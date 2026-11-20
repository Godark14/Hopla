@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.hopla.R
import app.hopla.domain.Stats
import app.hopla.ui.components.ConnectedChoice
import app.hopla.ui.components.Headline
import app.hopla.ui.components.HoplaShapes
import app.hopla.ui.components.MorphingBlob
import app.hopla.ui.components.RopeBackdrop
import app.hopla.ui.components.StatTile
import app.hopla.ui.components.Stepper
import app.hopla.workout.Phase
import app.hopla.workout.WorkoutMode
import app.hopla.workout.WorkoutUiState
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(state: WorkoutUiState, vm: WorkoutViewModel, onClose: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Retour pendant une séance : on quitte l'écran, le comptage continue (service de premier plan).
    BackHandler(enabled = state.phase != Phase.Finished) { onClose() }

    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = when {
            state.phase == Phase.Countdown -> scheme.primary
            state.phase == Phase.Running && state.isRest -> scheme.tertiaryContainer
            state.phase == Phase.Finished -> scheme.primaryContainer
            else -> scheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "bg",
    )
    val motion = MaterialTheme.motionScheme

    Box(Modifier.fillMaxSize().background(bg)) {
        RopeBackdrop(Modifier.fillMaxSize())
        AnimatedContent(
            targetState = when (state.phase) {
                Phase.Running, Phase.Paused -> Phase.Running
                else -> state.phase
            },
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.05f))
            },
            label = "phase",
        ) { phase ->
            when (phase) {
                Phase.Setup -> SetupPane(state, vm, onClose)
                Phase.Countdown -> CountdownPane(state.countdown)
                Phase.Running, Phase.Paused -> RunningPane(state, vm)
                Phase.Finished -> FinishedPane(state, vm, onClose)
            }
        }
    }
}

// ---------------------------------------------------------------- Préparation

private enum class ModeKind(val label: String) { FREE("Libre"), TIMED("Chrono"), TARGET("Objectif"), INTERVALS("Tabata") }

private val WorkoutMode.kind: ModeKind
    get() = when (this) {
        WorkoutMode.Free -> ModeKind.FREE
        is WorkoutMode.Timed -> ModeKind.TIMED
        is WorkoutMode.Target -> ModeKind.TARGET
        is WorkoutMode.Intervals -> ModeKind.INTERVALS
    }

@Composable
private fun SetupPane(state: WorkoutUiState, vm: WorkoutViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    // La notification affiche le compteur écran verrouillé. Si elle est refusée, le comptage marche quand même.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    val launch = {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start()
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        FilledTonalIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(top = 8.dp)) {
            Icon(painterResource(R.drawable.ic_back), "Retour")
        }
        Spacer(Modifier.height(12.dp))
        Headline("Prêt·e ?", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Glisse ton téléphone dans une poche serrée ou un brassard, puis saute.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        ConnectedChoice(
            options = ModeKind.entries,
            selected = state.mode.kind,
            onSelect = { k ->
                vm.setMode(
                    when (k) {
                        ModeKind.FREE -> WorkoutMode.Free
                        ModeKind.TIMED -> WorkoutMode.Timed(60)
                        ModeKind.TARGET -> WorkoutMode.Target(500)
                        ModeKind.INTERVALS -> WorkoutMode.Intervals()
                    },
                )
            },
            label = { it.label },
        )
        Spacer(Modifier.height(16.dp))

        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                when (val m = state.mode) {
                    WorkoutMode.Free -> Text("Pas de limite : arrête quand tu veux.", style = MaterialTheme.typography.titleMedium)
                    is WorkoutMode.Timed -> Stepper(
                        "Durée",
                        if (m.seconds < 120) "${m.seconds}s" else "${m.seconds / 60}min",
                        onMinus = { vm.setMode(WorkoutMode.Timed((m.seconds - step(m.seconds)).coerceAtLeast(30))) },
                        onPlus = { vm.setMode(WorkoutMode.Timed((m.seconds + step(m.seconds + 1)).coerceAtMost(3600))) },
                    )
                    is WorkoutMode.Target -> Stepper(
                        "Sauts",
                        "${m.jumps}",
                        onMinus = { vm.setMode(WorkoutMode.Target((m.jumps - 100).coerceAtLeast(100))) },
                        onPlus = { vm.setMode(WorkoutMode.Target((m.jumps + 100).coerceAtMost(10_000))) },
                    )
                    is WorkoutMode.Intervals -> {
                        Stepper(
                            "Tours",
                            "${m.rounds}",
                            onMinus = { vm.setMode(m.copy(rounds = (m.rounds - 1).coerceAtLeast(2))) },
                            onPlus = { vm.setMode(m.copy(rounds = (m.rounds + 1).coerceAtMost(30))) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Stepper(
                            "Effort",
                            "${m.work}s",
                            onMinus = { vm.setMode(m.copy(work = (m.work - 5).coerceAtLeast(10))) },
                            onPlus = { vm.setMode(m.copy(work = (m.work + 5).coerceAtMost(120))) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Stepper(
                            "Repos",
                            "${m.rest}s",
                            onMinus = { vm.setMode(m.copy(rest = (m.rest - 5).coerceAtLeast(5))) },
                            onPlus = { vm.setMode(m.copy(rest = (m.rest + 5).coerceAtMost(120))) },
                        )
                    }
                }
            }
        }

        if (!state.sensorAvailable) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    "Aucun accéléromètre détecté sur cet appareil : le comptage automatique est impossible.",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Tu peux verrouiller ton téléphone : le comptage continue, avec une vibration tous les 100 sauts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = launch,
            enabled = state.sensorAvailable,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.ExtraLargeContainerHeight).padding(vertical = 16.dp),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraLargeContainerHeight),
        ) {
            Icon(painterResource(R.drawable.ic_rope), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight)))
            Spacer(Modifier.width(12.dp))
            Headline("C'est parti", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

private fun step(seconds: Int) = if (seconds <= 120) 30 else 60

// ---------------------------------------------------------------- 3, 2, 1

@Composable
private fun CountdownPane(n: Int) {
    val motion = MaterialTheme.motionScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MorphingBlob(
            shapes = HoplaShapes.celebrate,
            color = MaterialTheme.colorScheme.onPrimary,
            cycleMillis = 700,
            pulseKey = n,
            pulseScale = 1.2f,
            modifier = Modifier.size(320.dp),
        ) {
            AnimatedContent(
                targetState = n,
                transitionSpec = {
                    (scaleIn(motion.fastSpatialSpec(), initialScale = 2f) + fadeIn()) togetherWith
                        (scaleOut(motion.fastSpatialSpec(), targetScale = 0.3f) + fadeOut())
                },
                label = "count",
            ) { value ->
                Headline(
                    if (value > 0) "$value" else "Go",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.8f),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- En cours

@Composable
private fun RunningPane(state: WorkoutUiState, vm: WorkoutViewModel) {
    val paused = state.phase == Phase.Paused
    val onBg = if (state.isRest && !paused) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        val headline = when {
            paused -> "En pause"
            state.mode is WorkoutMode.Intervals -> if (state.isRest) "Respire" else "Saute !"
            else -> state.mode.label
        }
        Headline(headline, style = MaterialTheme.typography.headlineLarge, color = onBg, maxLines = 1)
        if (state.mode is WorkoutMode.Intervals) {
            Text(
                "Tour ${state.round}/${state.mode.rounds} • ${state.phaseRemainingSec}s",
                style = MaterialTheme.typography.titleMedium,
                color = onBg.copy(alpha = 0.75f),
            )
        }

        Spacer(Modifier.weight(1f))
        MorphingBlob(
            shapes = if (paused) HoplaShapes.calm else HoplaShapes.hero,
            color = if (state.isRest) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            animate = !paused,
            cycleMillis = 1800,
            pulseKey = state.pulse,
            modifier = Modifier.size(300.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Headline(
                    "${state.jumps}",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (state.isRest) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
                Text(
                    "SAUTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.isRest) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        state.progress?.let { p ->
            LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(4.dp))
            Text(remainingLabel(state), style = MaterialTheme.typography.labelLarge, color = onBg.copy(alpha = 0.75f))
        }
        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Temps", Stats.formatClock(state.elapsedMs / 1000), "min:s", R.drawable.ic_timer, Modifier.weight(1f))
            StatTile("Cal.", "${state.kcal.roundToInt()}", "kcal", R.drawable.ic_bolt, Modifier.weight(1f))
            StatTile("Rythme", "${state.cadence}", "sauts/min", R.drawable.ic_fire, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            Button(
                onClick = vm::togglePause,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1.4f).heightIn(min = ButtonDefaults.LargeContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
            ) {
                Icon(painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause), null)
                Spacer(Modifier.width(8.dp))
                Text(if (paused) "Reprendre" else "Pause", style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalButton(
                onClick = vm::finish,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.LargeContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
            ) {
                Icon(painterResource(R.drawable.ic_stop), null)
                Spacer(Modifier.width(8.dp))
                Text("Fin", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun remainingLabel(s: WorkoutUiState): String = when (val m = s.mode) {
    is WorkoutMode.Timed -> "encore ${Stats.formatClock(((m.seconds * 1000L - s.elapsedMs) / 1000).coerceAtLeast(0))}"
    is WorkoutMode.Target -> "encore ${(m.jumps - s.jumps).coerceAtLeast(0)} sauts"
    is WorkoutMode.Intervals -> "encore ${Stats.formatClock(((m.totalSec * 1000L - s.elapsedMs) / 1000).coerceAtLeast(0))}"
    WorkoutMode.Free -> ""
}

// ---------------------------------------------------------------- Bilan

@Composable
private fun FinishedPane(state: WorkoutUiState, vm: WorkoutViewModel, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Headline(
            if (state.jumps >= 100) "Bravo !" else "Bien joué",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
        Text(state.mode.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(16.dp))
        MorphingBlob(
            shapes = HoplaShapes.celebrate,
            color = MaterialTheme.colorScheme.primary,
            cycleMillis = 1400,
            pulseKey = state.saved,
            pulseScale = 1.15f,
            modifier = Modifier.size(260.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Headline("${state.jumps}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                Text("SAUTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Temps", Stats.formatClock(state.elapsedMs / 1000), "min:s", R.drawable.ic_timer, Modifier.weight(1f), container = MaterialTheme.colorScheme.surface)
            StatTile("Cal.", "${state.kcal.roundToInt()}", "kcal", R.drawable.ic_bolt, Modifier.weight(1f), container = MaterialTheme.colorScheme.surface)
            StatTile("Pic", "${state.peakCadence}", "sauts/min", R.drawable.ic_fire, Modifier.weight(1f), container = MaterialTheme.colorScheme.surface)
        }
        Spacer(Modifier.height(28.dp))

        AnimatedContent(targetState = state.saving to state.saved, label = "save") { (saving, saved) ->
            when {
                saving -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    ContainedLoadingIndicator(Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("On prévient la bande…", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                saved -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (state.published == false) "Enregistré — envoi à la bande dès que le réseau revient" else "Enregistré et partagé",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { vm.save(onClose) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.LargeContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
                    ) {
                        Icon(painterResource(R.drawable.ic_check), null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enregistrer et partager", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { vm.reset(); onClose() }) {
                        Text("Ne pas enregistrer", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
