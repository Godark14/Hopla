package app.hopla.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hopla")

private object Keys {
    val name = stringPreferencesKey("name")
    val handle = stringPreferencesKey("handle")
    val weight = intPreferencesKey("weight")
    val goal = intPreferencesKey("goal")
    val palette = stringPreferencesKey("palette")
    val titleStyle = stringPreferencesKey("title_style")
    val darkMode = stringPreferencesKey("dark_mode")
    val sensitivity = stringPreferencesKey("sensitivity")
    val following = stringSetPreferencesKey("following")
    val sessions = stringPreferencesKey("sessions_json")
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

class SettingsRepository(private val context: Context) {
    private val defaults = UserSettings()

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            name = p[Keys.name] ?: defaults.name,
            handle = p[Keys.handle] ?: defaults.handle,
            weightKg = p[Keys.weight] ?: defaults.weightKg,
            dailyGoal = p[Keys.goal] ?: defaults.dailyGoal,
            palette = p[Keys.palette].toEnum(defaults.palette),
            titleStyle = p[Keys.titleStyle].toEnum(defaults.titleStyle),
            darkMode = p[Keys.darkMode].toEnum(defaults.darkMode),
            sensitivity = p[Keys.sensitivity].toEnum(defaults.sensitivity),
            following = p[Keys.following] ?: defaults.following,
        )
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { p ->
            val current = UserSettings(
                name = p[Keys.name] ?: defaults.name,
                handle = p[Keys.handle] ?: defaults.handle,
                weightKg = p[Keys.weight] ?: defaults.weightKg,
                dailyGoal = p[Keys.goal] ?: defaults.dailyGoal,
                palette = p[Keys.palette].toEnum(defaults.palette),
                titleStyle = p[Keys.titleStyle].toEnum(defaults.titleStyle),
                darkMode = p[Keys.darkMode].toEnum(defaults.darkMode),
                sensitivity = p[Keys.sensitivity].toEnum(defaults.sensitivity),
                following = p[Keys.following] ?: defaults.following,
            )
            val next = transform(current)
            p[Keys.name] = next.name
            p[Keys.handle] = next.handle
            p[Keys.weight] = next.weightKg
            p[Keys.goal] = next.dailyGoal
            p[Keys.palette] = next.palette.name
            p[Keys.titleStyle] = next.titleStyle.name
            p[Keys.darkMode] = next.darkMode.name
            p[Keys.sensitivity] = next.sensitivity.name
            p[Keys.following] = next.following
        }
    }
}

class SessionRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(JumpSession.serializer())

    val sessions: Flow<List<JumpSession>> = context.dataStore.data.map { p ->
        p[Keys.sessions]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
    }

    private fun Preferences.read(): List<JumpSession> =
        this[Keys.sessions]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()

    private suspend fun mutate(transform: (List<JumpSession>) -> List<JumpSession>) {
        context.dataStore.edit { p ->
            // On garde les 500 dernières séances, largement assez pour les stats.
            p[Keys.sessions] = json.encodeToString(listSerializer, transform(p.read()).sortedByDescending { it.startedAt }.take(500))
        }
    }

    suspend fun add(session: JumpSession) = mutate { listOf(session) + it.filterNot { s -> s.id == session.id } }

    suspend fun pending(): List<JumpSession> = sessions.first().filterNot { it.synced }

    suspend fun markSynced(ids: Collection<String>) = mutate { list -> list.map { if (it.id in ids) it.copy(synced = true) else it } }

    /** Fusionne les séances du serveur (nouvel appareil, réinstallation…). */
    suspend fun merge(remote: List<JumpSession>) = mutate { local ->
        val known = local.associateBy { it.id }
        local + remote.filter { it.id !in known }.map { it.copy(synced = true) }
    }

    suspend fun clear() = mutate { emptyList() }
}
