package app.hopla.data

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

fun buildSupabase(url: String, key: String): SupabaseClient = createSupabaseClient(url, key) {
    install(Auth)      // session persistée automatiquement sur Android
    install(Postgrest)
}

// ------------------------------------------------------------------ DTO

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val handle: String,
    @SerialName("shape_index") val shapeIndex: Int = 0,
    @SerialName("color_index") val colorIndex: Int = 0,
)

@Serializable
private data class NewProfileDto(val id: String, val name: String, val handle: String)

@Serializable
private data class OverviewDto(
    val id: String,
    val name: String,
    val handle: String,
    @SerialName("shape_index") val shapeIndex: Int,
    @SerialName("color_index") val colorIndex: Int,
    @SerialName("today_jumps") val todayJumps: Int,
    @SerialName("week_jumps") val weekJumps: List<Int>,
    @SerialName("last_week_jumps") val lastWeekJumps: List<Int>,
    @SerialName("week_kcal") val weekKcal: Int,
    @SerialName("week_minutes") val weekMinutes: Int,
    @SerialName("best_session") val bestSession: Int,
    val streak: Int,
)

@Serializable
private data class SessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("duration_sec") val durationSec: Int,
    @SerialName("active_sec") val activeSec: Int,
    val jumps: Int,
    val kcal: Double,
    val mode: String,
    @SerialName("peak_cadence") val peakCadence: Int,
)

@Serializable
private data class FollowDto(@SerialName("followee_id") val followeeId: String)

private fun JumpSession.toDto(userId: String) = SessionDto(
    id = id, userId = userId, startedAt = Instant.ofEpochMilli(startedAt).toString(),
    durationSec = durationSec, activeSec = activeSec, jumps = jumps,
    kcal = Math.round(kcal * 100) / 100.0, mode = mode.take(40), peakCadence = peakCadence.coerceIn(0, 400),
)

private fun SessionDto.toModel() = JumpSession(
    id = id, startedAt = OffsetDateTime.parse(startedAt).toInstant().toEpochMilli(),
    durationSec = durationSec, activeSec = activeSec, jumps = jumps, kcal = kcal,
    mode = mode, peakCadence = peakCadence, synced = true,
)

// ------------------------------------------------------------------ Auth

sealed interface AuthState {
    data object Loading : AuthState
    /** Pas de clés Supabase : l'app tourne en démo locale. */
    data object Offline : AuthState
    data object SignedOut : AuthState
    data class NeedsProfile(val email: String?) : AuthState
    data class SignedIn(val profile: ProfileDto) : AuthState
}

class AuthRepository(
    private val client: SupabaseClient?,
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(if (client == null) AuthState.Offline else AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Appelé quand une session devient active (sync des séances, abonnements…). */
    var onSignedIn: (suspend (ProfileDto) -> Unit)? = null

    init {
        client?.let { c ->
            scope.launch {
                c.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Initializing -> _state.value = AuthState.Loading
                        is SessionStatus.NotAuthenticated -> _state.value = AuthState.SignedOut
                        is SessionStatus.RefreshFailure -> {
                            // Pas de réseau au rafraîchissement : on garde l'état courant (mode hors ligne).
                            if (_state.value is AuthState.Loading) loadProfile(c, fallback = true)
                        }
                        is SessionStatus.Authenticated -> loadProfile(c, fallback = true)
                    }
                }
            }
        }
    }

    private suspend fun loadProfile(c: SupabaseClient, fallback: Boolean) {
        val user = c.auth.currentUserOrNull() ?: return
        val profile = runCatching {
            c.from("profiles").select { filter { eq("id", user.id) } }.decodeSingleOrNull<ProfileDto>()
        }
        profile.onSuccess { p ->
            if (p == null) {
                _state.value = AuthState.NeedsProfile(user.email)
            } else {
                settings.update { it.copy(name = p.name, handle = p.handle) }
                _state.value = AuthState.SignedIn(p)
                scope.launch { runCatching { onSignedIn?.invoke(p) } }
            }
        }.onFailure { e ->
            Log.w(TAG, "Lecture du profil impossible", e)
            if (fallback && e !is PostgrestRestException) {
                // Vrai problème réseau : on continue avec le profil en cache.
                val s = settings.settings.first()
                _state.value = AuthState.SignedIn(ProfileDto(user.id, s.name, s.handle))
            } else {
                // Le serveur a répondu une erreur : on ne fait pas semblant d'être connecté.
                _state.value = AuthState.NeedsProfile(user.email)
            }
        }
    }

    suspend fun sendCode(email: String): Result<Unit> = runCatching {
        val address = email.trim()
        requireNotNull(client).auth.signInWith(OTP) {
            this.email = address
        }
    }.mapError()

    suspend fun verifyCode(email: String, code: String): Result<Unit> = runCatching {
        requireNotNull(client).auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email.trim(), token = code.trim())
        Unit
    }.mapError()

    suspend fun createProfile(name: String, handle: String): Result<Unit> = runCatching {
        val c = requireNotNull(client)
        val uid = requireNotNull(c.auth.currentUserOrNull()).id
        val body = Json.encodeToJsonElement(NewProfileDto.serializer(), NewProfileDto(uid, name.trim(), handle.trim().lowercase()))
        c.from("profiles").insert(JsonArray(listOf(body)))
        loadProfile(c, fallback = false)
    }.mapError()

    suspend fun updateProfile(name: String, handle: String): Result<Unit> = runCatching {
        val c = requireNotNull(client)
        val uid = requireNotNull(c.auth.currentUserOrNull()).id
        c.from("profiles").update({
            set("name", name.trim())
            set("handle", handle.trim().lowercase())
        }) { filter { eq("id", uid) } }
        Unit
    }.mapError()

    suspend fun signOut() {
        client?.auth?.signOut()
        sessions.clear()
        settings.update { UserSettings(palette = it.palette, titleStyle = it.titleStyle, darkMode = it.darkMode, sensitivity = it.sensitivity, weightKg = it.weightKg) }
    }

    private fun <T> Result<T>.mapError(): Result<T> = recoverCatching { e -> throw friendly(e) }

    private fun friendly(it: Throwable): Throwable {  // it = erreur brute
        Log.w(TAG, "Erreur Supabase", it)
        val msg = when {
            it is PostgrestRestException && it.code == "42501" ->
                "Accès refusé par le serveur (42501) : exécute la migration SQL à jour."
            it is PostgrestRestException && it.code == "23505" -> "Ce pseudo est déjà pris."
            it is PostgrestRestException && it.code == "23514" -> "Pseudo : 3 à 20 caractères, lettres, chiffres, . ou _"
            it.message?.contains("otp", ignoreCase = true) == true ||
                it.message?.contains("token", ignoreCase = true) == true -> "Code invalide ou expiré."
            it.message?.contains("rate", ignoreCase = true) == true -> "Trop d'essais, réessaie dans une minute."
            it is IllegalArgumentException -> "Backend non configuré."
            it is PostgrestRestException -> "Erreur serveur (${it.code}) : ${it.message?.take(120)}"
            else -> "Connexion impossible. Vérifie ton réseau."
        }
        return Exception(msg, it)
    }

    private companion object {
        const val TAG = "Hopla"
    }
}

// ------------------------------------------------------------------ Social

class SupabaseSocialRepository(
    private val client: SupabaseClient,
    private val sessions: SessionRepository,
) : SocialRepository {

    override val isRemote = true
    private val zone: String get() = ZoneId.systemDefault().id
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    override val following: Flow<Set<String>> = _following.asStateFlow()

    private val myId: String? get() = client.auth.currentUserOrNull()?.id

    private suspend fun overview(ids: List<String>? = null, search: String? = null, limit: Int = 50): List<Athlete> {
        val params = buildJsonObject {
            put("p_tz", zone)
            put("p_ids", ids?.let { list -> JsonArray(list.map { JsonPrimitive(it) }) } ?: JsonNull)
            put("p_search", search?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it.trim()) } ?: JsonNull)
            put("p_limit", limit)
        }
        val me = myId
        return client.postgrest.rpc("athletes_overview", params).decodeList<OverviewDto>().map { o ->
            Athlete(
                id = o.id, name = o.name, handle = o.handle, shapeIndex = o.shapeIndex, colorIndex = o.colorIndex,
                todayJumps = o.todayJumps, weekJumps = o.weekJumps, lastWeekJumps = o.lastWeekJumps,
                weekKcal = o.weekKcal, weekMinutes = o.weekMinutes, bestSession = o.bestSession, streak = o.streak,
                recent = emptyList(), isMe = o.id == me,
            )
        }
    }

    suspend fun refreshFollowing() {
        val me = myId ?: return
        _following.value = client.from("follows")
            .select { filter { eq("follower_id", me) } }
            .decodeList<FollowDto>().map { it.followeeId }.toSet()
    }

    override suspend fun circle(): List<Athlete> {
        runCatching { syncPending() }
        refreshFollowing()
        val me = myId ?: return emptyList()
        return overview(ids = (_following.value + me).toList(), limit = 200)
    }

    override suspend fun search(query: String): List<Athlete> =
        overview(search = query.removePrefix("@"), limit = 40).filterNot { it.isMe }

    override suspend fun athlete(id: String): Athlete? {
        val a = overview(ids = listOf(id)).firstOrNull() ?: return null
        val recent = client.from("sessions").select {
            filter { eq("user_id", id) }
            order("started_at", Order.DESCENDING)
            limit(5)
        }.decodeList<SessionDto>().map { s ->
            val ms = OffsetDateTime.parse(s.startedAt).toInstant().toEpochMilli()
            RecentActivity(s.mode, s.jumps, (s.durationSec / 60).coerceAtLeast(1), relativeTime(ms))
        }
        return a.copy(recent = recent)
    }

    override suspend fun setFollowing(id: String, follow: Boolean) {
        val me = myId ?: return
        if (follow) {
            client.from("follows").upsert(JsonArray(listOf(buildJsonObject {
                put("follower_id", me)
                put("followee_id", id)
            }))) { ignoreDuplicates = true }
            _following.value = _following.value + id
        } else {
            client.from("follows").delete { filter { eq("follower_id", me); eq("followee_id", id) } }
            _following.value = _following.value - id
        }
    }

    override suspend fun syncPending(): Int {
        val me = myId ?: return 0
        val pending = sessions.pending()
        if (pending.isEmpty()) return 0
        val body = JsonArray(pending.map { Json.encodeToJsonElement(SessionDto.serializer(), it.toDto(me)).jsonObject })
        client.from("sessions").upsert(body) { onConflict = "id" } // idempotent : rejouable sans doublon
        sessions.markSynced(pending.map { it.id })
        return pending.size
    }

    /** Récupère tes séances des 90 derniers jours (nouvel appareil, réinstallation). */
    suspend fun pullMine() {
        val me = myId ?: return
        val since = Instant.now().minusSeconds(90L * 24 * 3600).toString()
        val remote = client.from("sessions").select {
            filter {
                eq("user_id", me)
                gte("started_at", since)
            }
            order("started_at", Order.DESCENDING)
            limit(500)
        }.decodeList<SessionDto>()
        sessions.merge(remote.map { it.toModel() })
    }
}
