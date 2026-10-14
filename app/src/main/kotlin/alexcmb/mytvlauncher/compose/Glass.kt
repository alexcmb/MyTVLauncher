package alexcmb.mytvlauncher.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The launcher's glass look, shared by the menus, the top bar and the hub's cards.
 *
 * Simulated, not real frosted glass: blurring what lies behind a surface needs Android 12's
 * RenderEffect, and the TVs this runs on are older (the test TV is Android 9). The illusion
 * comes from a translucent dark fill lightly tinted with the accent, a sheen fading down from
 * the top, and a 1dp edge that catches the light at the top more than at the bottom.
 */
private val Fill = Color(0xFF12141C)
private val Sheen = listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.03f))
private val Edge = listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.07f))

/**
 * Paints this element as a glass surface of [shape]. [opacity] is the dark fill's: higher
 * where text must stay crisp over a busy background (menus), lower for light chrome.
 */
@Composable
fun Modifier.glass(shape: Shape, opacity: Float = 0.55f): Modifier {
    val accent = LocalAccent.current
    return this
        .background(Fill.copy(alpha = opacity), shape)
        .background(accent.copy(alpha = 0.08f), shape)
        .background(Brush.verticalGradient(Sheen), shape)
        .border(1.dp, Brush.verticalGradient(Edge), shape)
}

/**
 * Just the light on the glass — sheen and edge, no fill — for surfaces that already have
 * their own picture underneath, such as an app's banner.
 */
fun Modifier.glassGloss(shape: Shape): Modifier = this
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)), shape)
    .border(1.dp, Brush.verticalGradient(Edge), shape)
