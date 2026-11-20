@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.friends

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.hopla.R
import app.hopla.domain.Stats
import app.hopla.ui.AthleteUiState
import app.hopla.ui.components.ExpressiveLoading
import app.hopla.ui.components.Headline
import app.hopla.ui.components.HoplaShapes
import app.hopla.ui.components.MorphingBlob
import app.hopla.ui.components.StatTile
import app.hopla.ui.components.WeeklyBars

@Composable
fun AthleteScreen(state: AthleteUiState, onBack: () -> Unit, onToggleFollow: () -> Unit, onChallenge: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(top = 8.dp)) {
            Icon(painterResource(R.drawable.ic_back), "Retour")
        }
        val a = state.athlete
        if (a == null) {
            if (state.error) {
                Text("Profil indisponible hors ligne.", Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ExpressiveLoading("Chargement du profil…")
            }
            return@Column
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MorphingBlob(
                shapes = listOf(HoplaShapes.avatars[a.shapeIndex % HoplaShapes.avatars.size]) + HoplaShapes.hero.take(2),
                color = MaterialTheme.colorScheme.primary,
                cycleMillis = 3000,
                modifier = Modifier.size(200.dp),
            ) {
                Headline(a.initials, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Headline(if (a.isMe) "Toi" else a.name, style = MaterialTheme.typography.displaySmall)
        Text("@${a.handle} • série de ${a.streak} j", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (!a.isMe) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FollowButton(state.following, state.pending, onToggleFollow)
                Button(
                    onClick = onChallenge,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_bolt), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Défi 1 min")
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Semaine", Stats.compact(a.weekTotal), "sauts", R.drawable.ic_rope, Modifier.weight(1f))
            StatTile("Cal.", "${a.weekKcal}", "kcal", R.drawable.ic_bolt, Modifier.weight(1f))
            StatTile(
                "Record", Stats.compact(a.bestSession), "sauts", R.drawable.ic_star, Modifier.weight(1f),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                accent = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(20.dp)) {
                Headline("Sauts", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                WeeklyBars(a.weekJumps, a.lastWeekJumps, Modifier.fillMaxWidth().height(140.dp), todayIndex = Stats.dashboard(emptyList()).todayIndex)
            }
        }
        Spacer(Modifier.height(20.dp))
        Headline("Activité", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (a.recent.isEmpty()) {
            Text("Pas encore de séance.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        a.recent.forEach { r ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = MaterialTheme.typography.titleMedium)
                        Text("${r.whenLabel} • ${r.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Headline("${r.jumps}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
