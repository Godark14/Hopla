@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.friends

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hopla.R
import app.hopla.data.Athlete
import app.hopla.domain.Stats
import app.hopla.ui.FriendsTab
import app.hopla.ui.FriendsUiState
import app.hopla.ui.components.ConnectedChoice
import app.hopla.ui.components.ExpressiveLoading
import app.hopla.ui.components.Headline
import app.hopla.ui.components.ShapeAvatar

@Composable
fun FriendsScreen(
    state: FriendsUiState,
    onTab: (FriendsTab) -> Unit,
    onQuery: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Headline("La bande", Modifier.weight(1f), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    FilledIconButton(onClick = onRefresh, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_leaderboard), "Actualiser")
                    }
                }
                Text(
                    "${state.following.size} abonnements • classement de la semaine",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                ConnectedChoice(FriendsTab.entries, state.tab, onTab, { it.label })
            }
        }

        state.error?.let { msg ->
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text(msg, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }

        if (state.athletes == null && state.tab != FriendsTab.DISCOVER) {
            item { ExpressiveLoading("On réveille la bande…") }
            return@LazyColumn
        }

        when (state.tab) {
            FriendsTab.RANKING -> {
                val ranking = state.ranking
                item { Podium(ranking.take(3), onOpen) }
                itemsIndexed(ranking.drop(3), key = { _, a -> "r-" + a.id }) { i, a ->
                    RankRow(rank = i + 4, athlete = a, onOpen = onOpen)
                }
                if (ranking.size <= 1) {
                    item { EmptyHint("Suis des potes dans « Découvrir » pour lancer la compétition.") }
                }
            }
            FriendsTab.FOLLOWING -> {
                val list = state.followed
                if (list.isEmpty()) item { EmptyHint("Tu ne suis encore personne.") }
                itemsIndexed(list, key = { _, a -> "f-" + a.id }) { _, a ->
                    FollowedCard(a, onOpen)
                }
            }
            FriendsTab.DISCOVER -> {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        placeholder = { Text("Nom ou @pseudo") },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                if (state.results == null) item { ExpressiveLoading("On cherche…") }
                if (state.results?.isEmpty() == true) item { EmptyHint("Personne trouvé·e pour « ${state.query} ».") }
                itemsIndexed(state.discover, key = { _, a -> "d-" + a.id }) { _, a ->
                    DiscoverRow(
                        athlete = a,
                        following = a.id in state.following,
                        pending = a.id in state.pending,
                        onToggle = { onToggleFollow(a.id) },
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Podium(top: List<Athlete>, onOpen: (String) -> Unit) {
    if (top.isEmpty()) return
    // Ordre visuel : 2e – 1er – 3e
    val order = listOfNotNull(top.getOrNull(1)?.let { it to 2 }, top[0] to 1, top.getOrNull(2)?.let { it to 3 })
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { (a, rank) ->
            val target = when (rank) { 1 -> 170.dp; 2 -> 128.dp; else -> 100.dp }
            val h by animateDpAsState(target, MaterialTheme.motionScheme.slowSpatialSpec(), label = "podium")
            val (bg, fg) = when (rank) {
                1 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                2 -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
                else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            }
            Column(
                Modifier.weight(1f).clickable { onOpen(a.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShapeAvatar(a, if (rank == 1) 84.dp else 68.dp)
                Spacer(Modifier.height(6.dp))
                Text(if (a.isMe) "Toi" else a.name.substringBefore(" "), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(h)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                        .background(bg),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Headline("$rank", style = MaterialTheme.typography.displaySmall, color = fg, maxLines = 1)
                        Text(Stats.compact(a.weekTotal), style = MaterialTheme.typography.labelLarge, color = fg)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, athlete: Athlete, onOpen: (String) -> Unit) {
    Surface(
        onClick = { onOpen(athlete.id) },
        shape = MaterialTheme.shapes.large,
        color = if (athlete.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Headline("$rank", Modifier.width(44.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            ShapeAvatar(athlete, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (athlete.isMe) "Toi" else athlete.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("@${athlete.handle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Headline(Stats.compact(athlete.weekTotal), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

@Composable
private fun FollowedCard(a: Athlete, onOpen: (String) -> Unit) {
    Surface(
        onClick = { onOpen(a.id) },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeAvatar(a, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Stats.compact(a.weekTotal)} sauts • ${a.weekKcal} kcal • série ${a.streak} j",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                MiniBars(a.weekJumps)
            }
        }
    }
}

@Composable
private fun MiniBars(values: List<Int>) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            Box(
                Modifier
                    .width(10.dp)
                    .height((4 + 24f * v / max).dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (v > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

@Composable
private fun DiscoverRow(athlete: Athlete, following: Boolean, pending: Boolean, onToggle: () -> Unit, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(athlete.id) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeAvatar(athlete, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(athlete.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text("@${athlete.handle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FollowButton(following, pending, onToggle)
    }
}

/** Bouton "Suivre" : un ToggleButton expressif dont la forme se transforme une fois coché. */
@Composable
fun FollowButton(following: Boolean, pending: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    ToggleButton(
        checked = following,
        onCheckedChange = { if (!pending) onToggle() },
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        AnimatedContent(
            targetState = Triple(following, pending, Unit),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "follow",
        ) { (f, p, _) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    p -> LoadingIndicator(Modifier.size(20.dp))
                    f -> Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                    else -> Icon(painterResource(R.drawable.ic_person_add), null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (f) "Suivi·e" else "Suivre")
            }
        }
    }
}
