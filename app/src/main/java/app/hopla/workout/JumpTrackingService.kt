package app.hopla.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.hopla.HoplaApp
import app.hopla.MainActivity
import app.hopla.R
import app.hopla.domain.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Service de premier plan de type « health » : garde le processus et le CPU éveillés
 * pendant une séance pour que l'accéléromètre continue d'être lu écran éteint.
 *
 * - Type `health` : prérequis satisfait par la permission HIGH_SAMPLING_RATE_SENSORS
 *   déclarée dans le manifeste (aucune demande à l'utilisateur).
 * - Wake lock partiel : les capteurs « non-wakeup » comme l'accéléromètre continuent
 *   de livrer leurs données tant que le CPU est éveillé.
 * - Notification continue avec compteur en direct + actions Pause / Terminer.
 */
@OptIn(FlowPreview::class)
class JumpTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private val engine: WorkoutEngine get() = (application as HoplaApp).container.engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(engine.state.value), type)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hopla:jumping").apply {
            setReferenceCounted(false)
            acquire(MAX_SESSION_MS)
        }

        watchdog()

        // Notification rafraîchie au plus 1×/s (le système limite de toute façon le débit).
        scope.launch {
            var seenLive = false // le service démarre juste avant le décompte : on attend la 1re phase active
            engine.state
                .sample(1_000)
                .map { s -> Triple(s.phase, s.jumps, s.elapsedMs / 1000) to s }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (_, s) ->
                    if (s.phase.isLive) seenLive = true
                    if (!s.phase.isLive && seenLive) {
                        // Bilan en notification seulement si l'écran est éteint (sinon on le voit dans l'app)
                        val screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                        if (s.phase == Phase.Finished && !screenOn) postSummary(s)
                        stopSelf()
                    } else if (s.phase.isLive) {
                        notify(buildNotification(s))
                    }
                }
        }
    }

    private fun watchdog() {
        // Séance annulée pendant le décompte : on ne laisse pas un service orphelin tourner.
        scope.launch {
            kotlinx.coroutines.delay(8_000)
            if (!engine.state.value.phase.isLive) stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> engine.togglePause()
            ACTION_STOP -> engine.finish()
        }
        if (intent?.action != null) watchdog()
        // Si le système tue le service, on ne le relance pas : la séance en mémoire est perdue de toute façon.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notify(n: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, n) } // ignoré si la permission est refusée
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, JumpTrackingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(s: WorkoutUiState): Notification {
        val title = when (s.phase) {
            Phase.Countdown -> "Prêt·e… ${s.countdown}"
            Phase.Paused -> "En pause • ${s.jumps} sauts"
            else -> if (s.mode is WorkoutMode.Intervals) {
                (if (s.isRest) "Respire" else "Saute !") + " • ${s.jumps} sauts"
            } else {
                "${s.jumps} sauts"
            }
        }
        val text = buildString {
            append(Stats.formatClock(s.elapsedMs / 1000))
            append(" • ${s.kcal.roundToInt()} kcal")
            if (s.cadence > 0) append(" • ${s.cadence}/min")
            if (s.mode is WorkoutMode.Intervals) append(" • tour ${s.round}/${s.mode.rounds}")
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_rope)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(s.mode.label)
            .setColor(ContextCompat.getColor(this, R.color.ember))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // lisible sur l'écran de verrouillage
            .setContentIntent(openAppIntent())
        s.progress?.let { builder.setProgress(1000, (it * 1000).toInt(), false) }
        if (s.phase == Phase.Running || s.phase == Phase.Paused) {
            builder.addAction(
                if (s.phase == Phase.Paused) R.drawable.ic_play else R.drawable.ic_pause,
                if (s.phase == Phase.Paused) "Reprendre" else "Pause",
                actionIntent(ACTION_PAUSE, 1),
            )
            builder.addAction(R.drawable.ic_stop, "Terminer", actionIntent(ACTION_STOP, 2))
        }
        return builder.build()
    }

    /** Petite notification de bilan si la séance se termine pendant que l'écran est éteint. */
    private fun postSummary(s: WorkoutUiState) {
        val n = NotificationCompat.Builder(this, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_rope)
            .setContentTitle("Séance terminée : ${s.jumps} sauts")
            .setContentText("${Stats.formatClock(s.elapsedMs / 1000)} • ${s.kcal.roundToInt()} kcal — touche pour enregistrer")
            .setColor(ContextCompat.getColor(this, R.color.ember))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(SUMMARY_ID, n) }
    }

    companion object {
        const val CHANNEL_LIVE = "workout_live"
        const val CHANNEL_SUMMARY = "workout_summary"
        private const val NOTIF_ID = 42
        const val SUMMARY_ID = 43
        private const val ACTION_PAUSE = "app.hopla.PAUSE"
        private const val ACTION_STOP = "app.hopla.STOP"
        private const val MAX_SESSION_MS = 3L * 60 * 60 * 1000

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, JumpTrackingService::class.java))
        }

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LIVE, "Séance en cours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Compteur de sauts en direct pendant une séance"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SUMMARY, "Bilans de séance", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
