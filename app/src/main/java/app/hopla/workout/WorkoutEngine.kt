package app.hopla.workout

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.hopla.data.JumpSession
import app.hopla.data.SessionRepository
import app.hopla.data.SettingsRepository
import app.hopla.data.SocialRepository
import app.hopla.domain.Calories
import app.hopla.sensor.JumpDetector
import app.hopla.sensor.JumpSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface WorkoutMode {
    val label: String

    data object Free : WorkoutMode { override val label = "Libre" }
    data class Timed(val seconds: Int) : WorkoutMode {
        override val label = if (seconds < 120) "$seconds s chrono" else "${seconds / 60} min chrono"
    }
    data class Target(val jumps: Int) : WorkoutMode { override val label = "Objectif $jumps" }
    data class Intervals(val work: Int = 20, val rest: Int = 10, val rounds: Int = 8) : WorkoutMode {
        override val label = "Tabata ×$rounds"
        val totalSec: Int get() = rounds * (work + rest) - rest
    }
}

enum class Phase { Setup, Countdown, Running, Paused, Finished }

val Phase.isLive: Boolean get() = this == Phase.Countdown || this == Phase.Running || this == Phase.Paused

data class WorkoutUiState(
    val mode: WorkoutMode = WorkoutMode.Free,
    val phase: Phase = Phase.Setup,
    val countdown: Int = 3,
    val jumps: Int = 0,
    val elapsedMs: Long = 0,
    val activeMs: Long = 0,
    val kcal: Double = 0.0,
    val cadence: Int = 0,
    val peakCadence: Int = 0,
    /** Incrémenté à chaque saut : clé de l'animation « pulse ». */
    val pulse: Int = 0,
    /** Paliers de 100 sauts. */
    val milestone: Int = 0,
    val isRest: Boolean = false,
    val round: Int = 1,
    val phaseRemainingSec: Int = 0,
    val sensorAvailable: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** null = pas encore tenté, true = publié, false = gardé en local (sera renvoyé plus tard). */
    val published: Boolean? = null,
) {
    val progress: Float?
        get() = when (val m = mode) {
            WorkoutMode.Free -> null
            is WorkoutMode.Timed -> (elapsedMs / (m.seconds * 1000f)).coerceIn(0f, 1f)
            is WorkoutMode.Target -> (jumps / m.jumps.toFloat()).coerceIn(0f, 1f)
            is WorkoutMode.Intervals -> (elapsedMs / (m.totalSec * 1000f)).coerceIn(0f, 1f)
        }
}

/**
 * Moteur de séance, à l'échelle du processus (pas d'une Activity) :
 * il continue de compter quand l'écran s'éteint, porté par [JumpTrackingService].
 */
class WorkoutEngine(
    private val context: Context,
    private val sensor: JumpSensor,
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val social: () -> SocialRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WorkoutUiState(sensorAvailable = sensor.isAvailable))
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    private val detector = JumpDetector()
    private var sensorJob: Job? = null
    private var tickJob: Job? = null
    private var countdownJob: Job? = null
    private val jumpTimes = ArrayDeque<Long>()
    @Volatile private var lastJumpAt = 0L
    private var startedAtWall = 0L
    private var weightKg = 70

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun prepare(mode: WorkoutMode) {
        if (_state.value.phase.isLive) return
        stopAll()
        _state.value = WorkoutUiState(mode = mode, sensorAvailable = sensor.isAvailable)
    }

    fun setMode(mode: WorkoutMode) {
        if (_state.value.phase == Phase.Setup) _state.update { it.copy(mode = mode) }
    }

    fun start() {
        if (_state.value.phase != Phase.Setup) return
        // Le service démarre tout de suite (l'app est au premier plan) et garde le CPU éveillé.
        JumpTrackingService.start(context)
        countdownJob = scope.launch {
            val s = settings.settings.first()
            weightKg = s.weightKg
            detector.sensitivity = s.sensitivity.factor
            detector.reset()
            synchronized(jumpTimes) { jumpTimes.clear() }
            for (n in 3 downTo 1) {
                _state.update { it.copy(phase = Phase.Countdown, countdown = n) }
                buzz(30)
                delay(1000)
            }
            startedAtWall = System.currentTimeMillis()
            _state.update { it.copy(phase = Phase.Running, countdown = 0) }
            buzz(120)
            startSensor()
            startTicker()
        }
    }

    fun togglePause() {
        when (_state.value.phase) {
            Phase.Running -> {
                sensorJob?.cancel()
                _state.update { it.copy(phase = Phase.Paused, cadence = 0) }
            }
            Phase.Paused -> {
                detector.reset()
                _state.update { it.copy(phase = Phase.Running) }
                startSensor()
            }
            else -> Unit
        }
    }

    fun finish() {
        if (!_state.value.phase.isLive) return
        stopAll()
        if (_state.value.jumps == 0 && _state.value.elapsedMs < 3000) {
            _state.update { it.copy(phase = Phase.Setup) }
            return
        }
        _state.update { it.copy(phase = Phase.Finished, cadence = 0) }
        buzzPattern(longArrayOf(0, 150, 100, 150, 100, 300))
    }

    /** Enregistre localement (toujours) puis tente l'envoi au backend. */
    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.phase != Phase.Finished || s.saving) return
        _state.update { it.copy(saving = true) }
        scope.launch {
            sessions.add(
                JumpSession(
                    id = UUID.randomUUID().toString(),
                    startedAt = startedAtWall,
                    durationSec = (s.elapsedMs / 1000).toInt(),
                    activeSec = (s.activeMs / 1000).toInt(),
                    jumps = s.jumps,
                    kcal = s.kcal,
                    mode = s.mode.label,
                    peakCadence = s.peakCadence,
                ),
            )
            val published = runCatching { social().syncPending() }.isSuccess
            _state.update { it.copy(saving = false, saved = true, published = published) }
            delay(900)
            onDone()
            reset()
        }
    }

    fun reset() {
        stopAll()
        _state.value = WorkoutUiState(mode = _state.value.mode, sensorAvailable = sensor.isAvailable)
    }

    private fun startSensor() {
        sensorJob?.cancel()
        sensorJob = scope.launch(Dispatchers.Default) {
            sensor.samples().collect { sample ->
                if (_state.value.phase != Phase.Running) return@collect
                if (detector.onSample(sample.x, sample.y, sample.z, sample.timestampNs)) onJump()
            }
        }
    }

    private fun onJump() {
        val now = SystemClock.elapsedRealtime()
        lastJumpAt = now
        synchronized(jumpTimes) { jumpTimes.addLast(now) }
        val before = _state.value.milestone
        _state.update { s ->
            val jumps = s.jumps + 1
            s.copy(jumps = jumps, pulse = s.pulse + 1, milestone = jumps / 100)
        }
        // Retour haptique tous les 100 sauts : utile quand le téléphone est dans la poche.
        if (_state.value.milestone > before) buzz(60)
        val mode = _state.value.mode
        if (mode is WorkoutMode.Target && _state.value.jumps >= mode.jumps) {
            scope.launch { finish() }
        }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            var lastRest: Boolean? = null
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                if (_state.value.phase != Phase.Running) continue

                val recentJumps = synchronized(jumpTimes) {
                    while (jumpTimes.isNotEmpty() && now - jumpTimes.first() > CADENCE_WINDOW_MS) jumpTimes.removeFirst()
                    jumpTimes.size
                }
                val elapsed = _state.value.elapsedMs + dt
                val window = minOf(CADENCE_WINDOW_MS, elapsed).coerceAtLeast(2000)
                val cadence = if (now - lastJumpAt > 2500) 0 else (recentJumps * 60_000L / window).toInt()
                val active = now - lastJumpAt <= ACTIVE_GRACE_MS
                val kcalDelta = Calories.kcal(if (active) cadence.coerceAtLeast(60) else 0, weightKg, dt / 1000.0)

                _state.update { s ->
                    var next = s.copy(
                        elapsedMs = elapsed,
                        activeMs = s.activeMs + if (active) dt else 0,
                        kcal = s.kcal + kcalDelta,
                        cadence = cadence,
                        peakCadence = maxOf(s.peakCadence, if (elapsed > 5000) cadence else 0),
                    )
                    val m = s.mode
                    if (m is WorkoutMode.Intervals) {
                        val sec = (elapsed / 1000).toInt()
                        val cycle = m.work + m.rest
                        val inCycle = sec % cycle
                        next = next.copy(
                            round = (sec / cycle + 1).coerceAtMost(m.rounds),
                            isRest = inCycle >= m.work,
                            phaseRemainingSec = if (inCycle < m.work) m.work - inCycle else cycle - inCycle,
                        )
                    }
                    next
                }

                // Tabata : vibration à chaque changement effort/repos (on n'a pas besoin de regarder l'écran)
                val rest = _state.value.isRest
                if (_state.value.mode is WorkoutMode.Intervals && lastRest != null && rest != lastRest) {
                    if (rest) buzz(250) else buzzPattern(longArrayOf(0, 80, 80, 80))
                }
                lastRest = rest

                val m = _state.value.mode
                val done = when (m) {
                    is WorkoutMode.Timed -> elapsed >= m.seconds * 1000L
                    is WorkoutMode.Intervals -> elapsed >= m.totalSec * 1000L
                    else -> false
                }
                if (done) finish()
            }
        }
    }

    private fun buzz(ms: Long) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun buzzPattern(pattern: LongArray) {
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private fun stopAll() {
        countdownJob?.cancel()
        sensorJob?.cancel()
        tickJob?.cancel()
    }

    private companion object {
        const val TICK_MS = 100L
        const val CADENCE_WINDOW_MS = 10_000L
        const val ACTIVE_GRACE_MS = 1_500L
    }
}
