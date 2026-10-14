package alexcmb.mytvlauncher.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Brings a panel in: it fades up from 94% scale, over 200ms. Plays when the element first
 * appears and again whenever [key] changes — a menu passes its title, so each level it opens
 * pops in instead of swapping in place.
 */
@Composable
fun Modifier.popIn(key: Any? = Unit): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(durationMillis = 200, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        scaleX = 0.94f + 0.06f * p
        scaleY = 0.94f + 0.06f * p
    }
}

/** Fades an element in once, when it first appears (a scrim behind a panel). */
@Composable
fun Modifier.fadeInOnce(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 160)) }
    return graphicsLayer { alpha = progress.value }
}
