@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.floor
import kotlin.math.min

/** Familles de formes Material 3 Expressive utilisées dans l'app. */
object HoplaShapes {
    val hero: List<RoundedPolygon> by lazy {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst)
    }
    val celebrate: List<RoundedPolygon> by lazy {
        listOf(MaterialShapes.Burst, MaterialShapes.Flower, MaterialShapes.SoftBoom, MaterialShapes.Clover8Leaf)
    }
    val calm: List<RoundedPolygon> by lazy {
        listOf(MaterialShapes.Circle, MaterialShapes.Cookie4Sided, MaterialShapes.Pill, MaterialShapes.Oval)
    }
    /** Une forme par athlète, pour des avatars reconnaissables. */
    val avatars: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Gem,
            MaterialShapes.Puffy, MaterialShapes.Pentagon, MaterialShapes.Flower, MaterialShapes.Ghostish,
            MaterialShapes.Cookie6Sided, MaterialShapes.Arch, MaterialShapes.SoftBurst, MaterialShapes.Bun,
        )
    }
    val cards: List<RoundedPolygon> by lazy {
        listOf(MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie7Sided, MaterialShapes.Burst, MaterialShapes.Flower)
    }
}

/**
 * Forme organique qui passe en boucle d'un polygone Material à l'autre (Morph),
 * tourne doucement, et "pulse" avec un ressort expressif à chaque changement de [pulseKey].
 */
@Composable
fun MorphingBlob(
    shapes: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    cycleMillis: Int = 2600,
    rotationMillis: Int = 18_000,
    pulseKey: Any? = null,
    pulseScale: Float = 1.08f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "blob")
    val n = shapes.size
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = n.toFloat(),
        animationSpec = infiniteRepeatable(tween(cycleMillis * n, easing = LinearEasing), RepeatMode.Restart),
        label = "morph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(rotationMillis, easing = LinearEasing)),
        label = "rotation",
    )
    val morphs = remember(shapes) { List(n) { i -> Morph(shapes[i], shapes[(i + 1) % n]) } }

    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey != null) {
            scale.snapTo(pulseScale)
            scale.animateTo(1f, spring)
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = if (animate) rotation else 0f
                }
                .drawWithCache {
                    val androidPath = android.graphics.Path()
                    val matrix = android.graphics.Matrix()
                    onDrawBehind {
                        val p = if (animate) progress else 0f
                        val index = floor(p).toInt().coerceIn(0, n - 1)
                        val frac = easeInOut(p - index)
                        androidPath.rewind()
                        morphs[index].toPath(frac, androidPath)
                        // Les MaterialShapes sont normalisées dans [0,1] : on les met à l'échelle.
                        val s = min(size.width, size.height)
                        matrix.reset()
                        matrix.setScale(s, s)
                        matrix.postTranslate((size.width - s) / 2f, (size.height - s) / 2f)
                        androidPath.transform(matrix)
                        drawPath(androidPath.asComposePath(), color)
                    }
                },
        )
        content()
    }
}

private fun easeInOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) 4 * x * x * x else 1 - ((-2 * x + 2).let { it * it * it }) / 2
}
