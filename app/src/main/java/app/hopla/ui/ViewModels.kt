package app.hopla.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hopla.AppContainer
import app.hopla.data.Athlete
import app.hopla.data.UserSettings
import app.hopla.domain.Dashboard
import app.hopla.domain.Stats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Message lisible : distingue un refus du serveur d'une vraie coupure réseau. */
fun describe(e: Throwable): String {
    android.util.Log.w("Hopla", "Erreur réseau/serveur", e)
    val rest = e as? io.github.jan.supabase.postgrest.exception.PostgrestRestException
    return when {
        rest?.code == "42501" -> "Accès refusé par Supabase (42501) : exécute la migration SQL à jour."
        rest != null -> "Erreur serveur (${rest.code}) : ${rest.message?.take(120)}"
        else -> "Pas de réseau : classement indisponible."
    }
}

// ---------- Accueil ----------

data class HomeUiState(
    val settings: UserSettings = UserSettings(),
    val dashboard: Dashboard = Dashboard(),
    val friends: List<Athlete>? = null, // null = chargement
    val offline: Boolean = false,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val circle = MutableStateFlow<List<Athlete>?>(null)
    private val offline = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        container.settings.settings,
        container.sessions.sessions.map { Stats.dashboard(it) },
        circle,
        offline,
    ) { settings, dashboard, all, off ->
        HomeUiState(
            settings = settings,
            dashboard = dashboard,
            friends = all?.filter { !it.isMe }?.sortedByDescending { it.todayJumps },
            offline = off,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        viewModelScope.launch {
            runCatching { container.social.circle() }
                .onSuccess { circle.value = it; offline.value = false }
                .onFailure { offline.value = true; if (circle.value == null) circle.value = emptyList() }
        }
    }
}

// ---------- Social ----------

enum class FriendsTab(val label: String) { RANKING("Classement"), FOLLOWING("Suivis"), DISCOVER("Découvrir") }

data class FriendsUiState(
    val tab: FriendsTab = FriendsTab.RANKING,
    val circle: List<Athlete>? = null,
    val results: List<Athlete>? = null,
    val following: Set<String> = emptySet(),
    val query: String = "",
    val pending: Set<String> = emptySet(),
    val error: String? = null,
) {
    /** Pour la compatibilité avec l'écran : null tant que le cercle charge. */
    val athletes: List<Athlete>? get() = circle
    val ranking: List<Athlete>
        get() = circle.orEmpty().filter { it.isMe || it.id in following }.sortedByDescending { it.weekTotal }
    val followed: List<Athlete>
        get() = circle.orEmpty().filter { !it.isMe && it.id in following }
    val discover: List<Athlete> get() = results.orEmpty()
}

class FriendsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            container.social.following.collect { f -> _state.update { it.copy(following = f) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(circle = null, error = null) }
            runCatching { container.social.circle() }
                .onSuccess { list -> _state.update { it.copy(circle = list) } }
                .onFailure { e -> _state.update { it.copy(circle = emptyList(), error = describe(e)) } }
        }
        search(_state.value.query, immediate = true)
    }

    fun selectTab(tab: FriendsTab) = _state.update { it.copy(tab = tab) }

    fun search(q: String) = search(q, immediate = false)

    private fun search(q: String, immediate: Boolean) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) delay(300) // debounce : une requête quand on arrête de taper
            _state.update { it.copy(results = null) }
            val list = runCatching { container.social.search(q) }.getOrDefault(emptyList())
            _state.update { it.copy(results = list) }
        }
    }

    fun toggleFollow(id: String) {
        val follow = id !in _state.value.following
        _state.update { it.copy(pending = it.pending + id) }
        viewModelScope.launch {
            val ok = runCatching { container.social.setFollowing(id, follow) }.isSuccess
            _state.update { it.copy(pending = it.pending - id, error = if (ok) null else "Action impossible hors ligne.") }
            // Le classement inclut désormais (ou plus) cette personne
            if (ok) runCatching { container.social.circle() }.onSuccess { list -> _state.update { it.copy(circle = list) } }
        }
    }
}

// ---------- Fiche athlète ----------

data class AthleteUiState(val athlete: Athlete? = null, val following: Boolean = false, val pending: Boolean = false, val error: Boolean = false)

class AthleteViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private val athlete = MutableStateFlow<Athlete?>(null)
    private val pending = MutableStateFlow(false)
    private val error = MutableStateFlow(false)

    val state: StateFlow<AthleteUiState> = combine(athlete, container.social.following, pending, error) { a, f, p, e ->
        AthleteUiState(a, id in f, p, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AthleteUiState())

    init {
        viewModelScope.launch {
            runCatching { container.social.athlete(id) }
                .onSuccess { athlete.value = it }
                .onFailure { error.value = true }
        }
    }

    fun toggleFollow() {
        val follow = !state.value.following
        pending.value = true
        viewModelScope.launch {
            runCatching { container.social.setFollowing(id, follow) }
            pending.value = false
        }
    }
}

// ---------- Profil & réglages ----------

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isRemote: Boolean = container.social.isRemote

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()
    private var profileJob: Job? = null

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    /** Nom / pseudo : sauvegarde locale immédiate, envoi au serveur après une courte pause de frappe. */
    fun updateIdentity(name: String, handle: String) {
        update { it.copy(name = name, handle = handle) }
        if (!isRemote) return
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            delay(900)
            if (name.isBlank() || handle.length < 3) return@launch
            _profileError.value = container.auth.updateProfile(name, handle).exceptionOrNull()?.message
        }
    }

    fun signOut() {
        viewModelScope.launch { container.auth.signOut() }
    }
}
