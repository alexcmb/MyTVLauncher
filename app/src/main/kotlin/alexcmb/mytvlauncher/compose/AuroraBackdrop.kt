package alexcmb.mytvlauncher.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** One blob of the aurora: where it circles, how far, how fast, how big, which colour. */
private data class Blob(
    val cx: Float,
    val cy: Float,
    val orbit: Float,
    val periodMs: Int,
    val radius: Float,
    val hueShift: Float,
    val phase: Float,
)

// Positions and sizes as fractions of the screen; hue shifts in degrees from the accent.
private val Blobs = listOf(
    Blob(cx = 0.20f, cy = 0.25f, orbit = 0.10f, periodMs = 62_000, radius = 0.55f, hueShift = 0f, phase = 0.0f),
    Blob(cx = 0.80f, cy = 0.30f, orbit = 0.12f, periodMs = 74_000, radius = 0.50f, hueShift = 40f, phase = 1.7f),
    Blob(cx = 0.65f, cy = 0.85f, orbit = 0.09f, periodMs = 56_000, radius = 0.45f, hueShift = -45f, phase = 3.1f),
    Blob(cx = 0.15f, cy = 0.90f, orbit = 0.08f, periodMs = 82_000, radius = 0.40f, hueShift = 80f, phase = 4.4f),
)

// The aurora is painted small and stretched to the screen: it is all soft gradients, so a
// 240×135 frame scaled up with filtering looks the same as a full-resolution one.
private const val FRAME_W = 240
private const val FRAME_H = 135

// The blobs take a minute or more to go round: a few pixels a frame at 2.5 frames a second,
// too little to see as steps on shapes this soft. Every frame costs a full-screen redraw on
// the TV (everything above repaints too), so the rate is what sets the CPU cost.
private const val FRAME_MS = 400L

/**
 * The "aurora" background: large soft blobs of colour drifting slowly behind the launcher,
 * in shades of [accent] (the accent itself and its neighbours on the colour wheel), so it
 * follows an "auto" accent as focus moves. [intensity] (0–1, the brightness setting) sets
 * how strongly they glow.
 *
 * Built for a modest TV. Drawing the gradients at full resolution every frame cost ~57ms a
 * frame and half a CPU on the test TV; instead a tiny frame is repainted 2.5 times a second,
 * only while the launcher is visible, and the screen just draws that one bitmap, scaled.
 */
@Composable
fun AuroraBackdrop(accent: Color, intensity: Float) {
    val colours by rememberUpdatedState(Blobs.map { shiftHue(accent, it.hueShift).toArgb() })
    val alpha by rememberUpdatedState(0.18f + 0.42f * intensity)
    val frame = remember { Bitmap.createBitmap(FRAME_W, FRAME_H, Bitmap.Config.ARGB_8888) }
    val image = remember(frame) { frame.asImageBitmap() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    // Bumped after each repaint; reading it in the draw phase redraws just this layer.
    var version by remember { mutableIntStateOf(0) }
    // Only while the launcher is on screen: behind a playing app it would keep burning CPU.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(frame, lifecycle) {
        val canvas = Canvas(frame)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                paintFrame(canvas, paint, SystemClock.uptimeMillis(), colours, alpha)
                version++
                delay(FRAME_MS)
            }
        }
    }
    Box(
        Modifier.fillMaxSize().drawBehind {
            version // a dependency: redraw when a new frame is painted
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(FRAME_W, FRAME_H),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low, // bilinear: smooth when stretched
            )
        }
    )
}

/** Paints one aurora frame at [now] into the small [canvas]. */
private fun paintFrame(canvas: Canvas, paint: Paint, now: Long, colours: List<Int>, alpha: Float) {
    canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
    Blobs.forEachIndexed { i, blob ->
        val angle = (now % blob.periodMs).toFloat() / blob.periodMs * 2f * Math.PI.toFloat() + blob.phase
        val x = (blob.cx + blob.orbit * cos(angle)) * FRAME_W
        val y = (blob.cy + blob.orbit * sin(angle)) * FRAME_H
        val radius = blob.radius * FRAME_W
        val colour = colours[i]
        paint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(withAlpha(colour, alpha), withAlpha(colour, alpha * 0.45f), 0),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, paint)
    }
}

private fun withAlpha(argb: Int, alpha: Float): Int =
    (argb and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

/** [colour] turned [degrees] round the colour wheel, saturation and brightness kept. */
private fun shiftHue(colour: Color, degrees: Float): Color {
    if (degrees == 0f) return colour
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colour.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
