@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.hopla.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hopla.R
import app.hopla.data.Athlete
import app.hopla.domain.Calories
import app.hopla.domain.Stats
import app.hopla.ui.HomeUiState
import app.hopla.ui.components.ExpressiveLoading
import app.hopla.ui.components.Headline
import app.hopla.ui.components.HoplaShapes
import app.hopla.ui.components.MorphingBlob
import app.hopla.ui.components.Pill
import app.hopla.ui.components.RopeBackdrop
import app.hopla.ui.components.SectionTitle
import app.hopla.ui.components.ShapeAvatar
import app.hopla.ui.components.StatTile
import app.hopla.ui.components.WeeklyBars
import app.hopla.workout.WorkoutMode

data class WorkoutPreset(val title: String, val pitch: String, val mode: WorkoutMode, val seconds: Int?)

val Presets = listOf(
    WorkoutPreset("Libre", "Sans limite, juste le rythme", WorkoutMode.Free, null),
    WorkoutPreset("1 min chrono", "Combien en 60 secondes ?", WorkoutMode.Timed(60), 60),
    WorkoutPreset("Tabata", "8 × 20 s à fond, 10 s pour souffler", WorkoutMode.Intervals(), WorkoutMode.Intervals().totalSec),
    WorkoutPreset("Objectif 500", "500 sauts, pas un de moins", WorkoutMode.Target(500), null),
    WorkoutPreset("Endurance", "10 minutes en continu", WorkoutMode.Timed(600), 600),
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStart: (WorkoutMode) -> Unit,
    onOpenAthlete: (String) -> Unit,
    onOpenFriends: () -> Unit,
) {
    val d = state.dashboard
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp).padding(top = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Headline("Hopla", Modifier.weight(1f), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    Pill(
                        text = if (d.streak > 0) "${d.streak} j" else "0 j",
                        icon = R.drawable.ic_fire,
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    "Salut ${state.settings.name}, on saute ?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { TodayHero(jumps = d.todayJumps, goal = state.settings.dailyGoal, kcal = d.todayKcal, minutes = d.todayMinutes, onStart = { onStart(WorkoutMode.Free) }) }

        item {
            SectionTitle("Séances")
            Spacer(Modifier.height(12.dp))
            PresetCarousel(weightKg = state.settings.weightKg, onStart = onStart)
        }

        item {
            SectionTitle("Ta semaine")
            Spacer(Modifier.height(12.dp))
            Surface(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Headline("Sauts", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall))
                        Spacer(Modifier.width(6.dp))
                        Text("Sem. dernière", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Headline(Stats.compact(d.weekTotal), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    WeeklyBars(d.weekJumps, d.lastWeekJumps, Modifier.fillMaxWidth().height(150.dp), todayIndex = d.todayIndex)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Cal.", "${d.weekKcal}", "kcal", R.drawable.ic_bolt, Modifier.weight(1f))
                StatTile("Temps", "${d.weekMinutes}", "minutes", R.drawable.ic_timer, Modifier.weight(1f))
                StatTile(
                    "Record", Stats.compact(d.bestSession), "sauts", R.drawable.ic_star, Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        item {
            SectionTitle("La bande") {
                TextButton(onClick = onOpenFriends) { Text("Tout voir") }
            }
            Spacer(Modifier.height(8.dp))
            val friends = state.friends
            when {
                friends == null -> ExpressiveLoading("On réveille la bande…")
                friends.isEmpty() -> Text(
                    "Suis tes potes pour voir leurs sauts ici.",
                    Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> FriendsStrip(friends, onOpenAthlete)
            }
        }
    }
}

@Composable
private fun TodayHero(jumps: Int, goal: Int, kcal: Int, minutes: Int, onStart: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = (jumps / goal.toFloat()).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "goal",
    )
    val shown by animateIntAsState(jumps, MaterialTheme.motionScheme.slowEffectsSpec(), label = "count")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            )
            MorphingBlob(
                shapes = HoplaShapes.hero,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(236.dp)
                    .clickable(onClick = onStart),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AUJOURD'HUI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Headline(
                        Stats.compact(shown),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                    Text("/ ${Stats.compact(goal)} sauts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("$kcal kcal", R.drawable.ic_bolt, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Pill("$minutes min", R.drawable.ic_timer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun PresetCarousel(weightKg: Int, onStart: (WorkoutMode) -> Unit) {
    val carouselState = rememberCarouselState { Presets.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 250.dp,
        itemSpacing = 10.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(300.dp),
    ) { i ->
        val preset = Presets[i]
        val scheme = MaterialTheme.colorScheme
        val (bg, fg) = when (i % 3) {
            0 -> scheme.primary to scheme.onPrimary
            1 -> scheme.tertiary to scheme.onTertiary
            else -> scheme.inverseSurface to scheme.inverseOnSurface
        }
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .background(bg)
                .clickable { onStart(preset.mode) },
        ) {
            RopeBackdrop(Modifier.fillMaxSize(), fg.copy(alpha = 0.22f))
            MorphingBlob(
                shapes = listOf(HoplaShapes.cards[i % HoplaShapes.cards.size], HoplaShapes.cards[(i + 2) % HoplaShapes.cards.size]),
                color = fg.copy(alpha = 0.16f),
                cycleMillis = 3400,
                modifier = Modifier.size(220.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-40).dp),
            )
            Column(Modifier.align(Alignment.TopStart).padding(20.dp)) {
                val meta = buildList {
                    preset.seconds?.let { add(if (it < 120) "$it s" else "${it / 60} min"); add("~${Calories.estimate(it, weightKg)} kcal") }
                    if (preset.mode is WorkoutMode.Target) add("~5 min")
                    if (preset.mode is WorkoutMode.Free) add("à ton rythme")
                }.joinToString(" • ")
                Text(meta, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.85f))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Headline(preset.title, style = MaterialTheme.typography.headlineLarge, color = fg)
                Text(preset.pitch, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f), maxLines = 2)
            }
        }
    }
}

@Composable
private fun FriendsStrip(friends: List<Athlete>, onOpen: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(friends, key = { it.id }) { a ->
            Column(
                Modifier.width(84.dp).clickable { onOpen(a.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShapeAvatar(a, 72.dp)
                Spacer(Modifier.height(6.dp))
                Text(a.name.substringBefore(" "), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    "${Stats.compact(a.todayJumps)} auj.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
