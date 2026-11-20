package app.hopla

import android.app.Application
import android.content.Context
import app.hopla.data.AuthRepository
import app.hopla.data.FakeSocialRepository
import app.hopla.data.SessionRepository
import app.hopla.data.SettingsRepository
import app.hopla.data.SocialRepository
import app.hopla.data.SupabaseSocialRepository
import app.hopla.data.buildSupabase
import app.hopla.sensor.JumpSensor
import app.hopla.workout.JumpTrackingService
import app.hopla.workout.WorkoutEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Conteneur de dépendances minimaliste, vivant à l'échelle du processus. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(context)
    val sessions = SessionRepository(context)
    val sensor = JumpSensor(context)

    /** null si aucune clé n'est configurée → mode démo hors ligne. */
    val supabase = BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank() }
        ?.let { buildSupabase(it, BuildConfig.SUPABASE_KEY) }

    val social: SocialRepository = supabase?.let { SupabaseSocialRepository(it, sessions) }
        ?: FakeSocialRepository(settings, sessions)

    val auth = AuthRepository(supabase, settings, sessions, appScope).apply {
        // À chaque connexion : on envoie les séances en attente et on récupère l'historique.
        onSignedIn = {
            (social as? SupabaseSocialRepository)?.let { repo ->
                runCatching { repo.syncPending() }
                runCatching { repo.pullMine() }
                runCatching { repo.refreshFollowing() }
            }
        }
    }

    val engine = WorkoutEngine(context, sensor, settings, sessions, { social }, appScope)
}

class HoplaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        JumpTrackingService.createChannels(this)
        container = AppContainer(this)
    }
}
