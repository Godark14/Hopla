@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import app.hopla.data.AuthState
import app.hopla.domain.Stats
import app.hopla.ui.auth.CreateProfileScreen
import app.hopla.ui.auth.SignInScreen
import app.hopla.ui.auth.SplashScreen
import app.hopla.ui.components.HoplaShapes
import app.hopla.ui.components.MorphingBlob
import app.hopla.workout.Phase
import app.hopla.workout.WorkoutUiState
import app.hopla.workout.isLive
import app.hopla.AppContainer
import app.hopla.R
import app.hopla.ui.AthleteViewModel
import app.hopla.ui.FriendsViewModel
import app.hopla.ui.HomeViewModel
import app.hopla.ui.ProfileViewModel
import app.hopla.ui.friends.AthleteScreen
import app.hopla.ui.friends.FriendsScreen
import app.hopla.ui.home.HomeScreen
import app.hopla.ui.profile.ProfileScreen
import app.hopla.workout.WorkoutMode
import app.hopla.ui.workout.WorkoutScreen
import app.hopla.ui.workout.WorkoutViewModel

sealed interface Screen {
    data object Home : Screen
    data object Friends : Screen
    data object Profile : Screen
    data class Workout(val mode: WorkoutMode) : Screen
    data class AthleteDetail(val id: String) : Screen
}

private val tabs = listOf(Screen.Home, Screen.Friends, Screen.Profile)

/** Navigation volontairement simple : une pile d'écrans en mémoire. */
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    fun go(screen: Screen) {
        stack.add(screen)
    }

    fun tab(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

@Composable
fun HoplaRoot(container: AppContainer) {
    val authState by container.auth.state.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = authState,
        contentKey = { it::class }, // pas de ré-animation quand seul le profil change
        transitionSpec = { fadeIn(motion.slowEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        label = "auth",
    ) { target ->
        when (val a = target) {
            AuthState.Loading -> SplashScreen()
            AuthState.SignedOut -> SignInScreen(container.auth)
            is AuthState.NeedsProfile -> CreateProfileScreen(container.auth, a.email)
            AuthState.Offline, is AuthState.SignedIn -> MainShell(container)
        }
    }
}

@Composable
private fun MainShell(container: AppContainer) {
    val nav = remember { Navigator() }
    val factory = remember(container) {
        viewModelFactory {
            initializer { HomeViewModel(container) }
            initializer { FriendsViewModel(container) }
            initializer { ProfileViewModel(container) }
            initializer { WorkoutViewModel(container) }
        }
    }
    val homeVm: HomeViewModel = viewModel(factory = factory)
    val friendsVm: FriendsViewModel = viewModel(factory = factory)
    val profileVm: ProfileViewModel = viewModel(factory = factory)
    val workoutVm: WorkoutViewModel = viewModel(factory = factory)

    BackHandler(enabled = nav.stack.size > 1 && nav.current !is Screen.Workout) { nav.back() }

    val workout by workoutVm.state.collectAsStateWithLifecycle()
    // Au lancement (ex. depuis la notification) : on revient sur la séance en cours.
    LaunchedEffect(Unit) {
        val s = workoutVm.state.value
        if (s.phase.isLive && nav.current !is Screen.Workout) nav.go(Screen.Workout(s.mode))
    }
    // Séance terminée hors de l'écran (chrono écoulé écran éteint…) : on affiche le bilan à enregistrer.
    LaunchedEffect(workout.phase) {
        if (workout.phase == Phase.Finished && nav.current !is Screen.Workout) nav.go(Screen.Workout(workout.mode))
    }

    val motion = MaterialTheme.motionScheme
    val current = nav.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            contentKey = { it.toString() },
            label = "screens",
        ) { screen ->
            when (screen) {
                Screen.Home -> {
                    val state by homeVm.state.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { homeVm.refresh() }
                    HomeScreen(
                        state = state,
                        onStart = { nav.go(Screen.Workout(it)) },
                        onOpenAthlete = { nav.go(Screen.AthleteDetail(it)) },
                        onOpenFriends = { nav.tab(Screen.Friends) },
                    )
                }
                Screen.Friends -> {
                    val state by friendsVm.state.collectAsStateWithLifecycle()
                    FriendsScreen(
                        state = state,
                        onTab = friendsVm::selectTab,
                        onQuery = friendsVm::search,
                        onToggleFollow = friendsVm::toggleFollow,
                        onOpen = { nav.go(Screen.AthleteDetail(it)) },
                        onRefresh = friendsVm::refresh,
                    )
                }
                Screen.Profile -> {
                    val settings by profileVm.settings.collectAsStateWithLifecycle()
                    val profileError by profileVm.profileError.collectAsStateWithLifecycle()
                    ProfileScreen(
                        settings = settings,
                        isRemote = profileVm.isRemote,
                        profileError = profileError,
                        onUpdate = profileVm::update,
                        onIdentity = profileVm::updateIdentity,
                        onSignOut = profileVm::signOut,
                    )
                }
                is Screen.Workout -> {
                    LaunchedEffect(screen) { workoutVm.prepare(screen.mode) }
                    val state by workoutVm.state.collectAsStateWithLifecycle()
                    WorkoutScreen(state = state, vm = workoutVm, onClose = { nav.back() })
                }
                is Screen.AthleteDetail -> {
                    val vm: AthleteViewModel = viewModel(
                        key = "athlete-${screen.id}",
                        factory = viewModelFactory { initializer { AthleteViewModel(container, screen.id) } },
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    AthleteScreen(
                        state = state,
                        onBack = { nav.back() },
                        onToggleFollow = vm::toggleFollow,
                        onChallenge = { nav.go(Screen.Workout(WorkoutMode.Timed(60))) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = current !is Screen.Workout && workout.phase.isLive,
            enter = slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(),
            exit = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
        ) {
            LiveBanner(workout) { nav.go(Screen.Workout(workout.mode)) }
        }

        AnimatedVisibility(
            visible = current in tabs,
            enter = slideInVertically(motion.defaultSpatialSpec()) { it * 2 } + fadeIn(),
            exit = slideOutVertically(motion.fastSpatialSpec()) { it * 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { nav.go(Screen.Workout(WorkoutMode.Free)) }) {
                        Icon(painterResource(R.drawable.ic_rope), "Commencer à sauter")
                    }
                },
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                ToolbarTab(current == Screen.Home, R.drawable.ic_home, "Accueil") { nav.tab(Screen.Home) }
                ToolbarTab(current == Screen.Friends, R.drawable.ic_group, "La bande") { nav.tab(Screen.Friends) }
                ToolbarTab(current == Screen.Profile, R.drawable.ic_tune, "Toi") { nav.tab(Screen.Profile) }
            }
        }
    }
}

@Composable
private fun LiveBanner(s: WorkoutUiState, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MorphingBlob(HoplaShapes.hero, MaterialTheme.colorScheme.primary, Modifier.size(32.dp), animate = s.phase == Phase.Running, pulseKey = s.pulse)
            Spacer(Modifier.width(10.dp))
            Text(
                (if (s.phase == Phase.Paused) "En pause • " else "") + "${s.jumps} sauts • ${Stats.formatClock(s.elapsedMs / 1000)}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun ToolbarTab(selected: Boolean, icon: Int, label: String, onClick: () -> Unit) {
    FilledIconToggleButton(checked = selected, onCheckedChange = { onClick() }) {
        Icon(painterResource(icon), contentDescription = label)
    }
}
