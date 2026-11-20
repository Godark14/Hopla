@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hopla.R
import app.hopla.data.Athlete
import app.hopla.domain.Stats
import app.hopla.ui.theme.LocalTitleTheme
import app.hopla.ui.theme.avatarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Titre d'affiche : applique la casse du thème de titres choisi. */
@Composable
fun Headline(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
) {
    val upper = LocalTitleTheme.current.id.uppercase
    Text(
        text = if (upper) text.uppercase() else text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Lignes courbes en fond, comme une corde en mouvement. */
@Composable
fun RopeBackdrop(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.5.dp.toPx())
        listOf(0.15f, 0.42f, 0.7f).forEachIndexed { i, k ->
            val path = Path().apply {
                moveTo(-w * 0.1f, h * k)
                cubicTo(w * 0.3f, h * (k - 0.35f + i * 0.1f), w * 0.7f, h * (k + 0.45f), w * 1.1f, h * (k - 0.1f))
            }
            drawPath(path, color, style = stroke)
        }
    }
}

/** Avatar découpé dans une forme Material 3 Expressive propre à chaque athlète. */
@Composable
fun ShapeAvatar(athlete: Athlete, size: Dp, modifier: Modifier = Modifier) {
    val (bg, fg) = MaterialTheme.colorScheme.avatarColors(athlete.colorIndex)
    val shape = HoplaShapes.avatars[athlete.shapeIndex % HoplaShapes.avatars.size].toShape()
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            athlete.initials,
            color = fg,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * (size.value / 64f)),
        )
    }
}

/** Histogramme hebdomadaire bicolore (semaine dernière en fond, cette semaine devant). */
@Composable
fun WeeklyBars(
    thisWeek: List<Int>,
    lastWeek: List<Int>,
    modifier: Modifier = Modifier,
    todayIndex: Int = -1,
    barColor: Color = MaterialTheme.colorScheme.primary,
    ghostColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val max = (thisWeek + lastWeek).maxOrNull()?.coerceAtLeast(1) ?: 1
    val anims = remember { List(7) { Animatable(0f) } }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(thisWeek) {
        anims.forEachIndexed { i, a ->
            launch {
                delay(i * 45L)
                a.animateTo(thisWeek.getOrElse(i) { 0 } / max.toFloat(), spring)
            }
        }
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val slot = size.width / 7f
            val barW = slot * 0.36f
            val r = CornerRadius(barW / 2f)
            for (i in 0 until 7) {
                val cx = slot * i + slot / 2f
                val ghostH = (lastWeek.getOrElse(i) { 0 } / max.toFloat()) * size.height
                drawRoundRect(ghostColor, Offset(cx - barW * 0.85f, size.height - ghostH), Size(barW, ghostH.coerceAtLeast(barW)), r)
                val h = (anims[i].value * size.height).coerceAtLeast(barW)
                drawRoundRect(
                    if (i == todayIndex) barColor else barColor.copy(alpha = 0.78f),
                    Offset(cx - barW * 0.25f, size.height - h),
                    Size(barW, h),
                    r,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Stats.dayLabels.forEachIndexed { i, d ->
                Text(
                    d,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == todayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Tuile de stat : gros chiffre en police d'affiche. */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String,
    icon: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
                }
                Spacer(Modifier.width(8.dp))
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(10.dp))
            Headline(value, style = MaterialTheme.typography.headlineMedium, color = accent, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
        }
    }
}

/** Groupe de boutons connectés (Material 3 Expressive) pour choisir une option. */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                if (option == selected) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Sélecteur +/- avec boutons qui changent de forme à l'appui. */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = onMinus, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_remove), "Moins")
        }
        Headline(
            value,
            Modifier.padding(horizontal = 12.dp).widthIn(min = 72.dp),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
        FilledTonalIconButton(onClick = onPlus, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_add), "Plus")
        }
    }
}

/** Indicateur de chargement expressif avec une petite phrase. */
@Composable
fun ExpressiveLoading(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pastille arrondie avec icône (série, tags…). */
@Composable
fun Pill(text: String, icon: Int, container: Color, content: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = content)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Headline(text, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        action?.invoke()
    }
}
