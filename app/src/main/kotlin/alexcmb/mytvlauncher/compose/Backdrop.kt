package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.WallpaperFit
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap

internal val Background = Color(0xFF0E0E12)

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Mixes [tint] into [base] by [fraction]; result is always fully opaque. */
internal fun blendColor(base: Color, tint: Color, fraction: Float): Color = Color(
    red = base.red + (tint.red - base.red) * fraction,
    green = base.green + (tint.green - base.green) * fraction,
    blue = base.blue + (tint.blue - base.blue) * fraction,
    alpha = 1f,
)

/**
 * An imported dynamic wallpaper, as the home screen draws it: its schedule, where each
 * picture is, the city a sun schedule follows, and its library id (a new one per import).
 */
data class DynamicWallpaper(
    val schedule: alexcmb.mytvlauncher.dynamic.DynamicSchedule,
    val frame: (Int) -> java.io.File,
    val city: alexcmb.mytvlauncher.dynamic.City,
    val id: String,
)

/**
 * The background, behind everything: the home screen's, and the screensaver's. [intensity]
 * (0–1, the brightness setting) drives every style: it deepens the solid tint, strengthens the
 * gradient wash and brightens a picture. A picture is [dimmed] where cards cover it all (the
 * grid tabs). In the AMBIENT style, the [focused] app's banner is the art.
 */
@Composable
internal fun Backdrop(
    style: BackgroundStyle,
    image: ImageBitmap?,
    video: Uri?,
    dynamic: DynamicWallpaper?,
    intensity: Float,
    fit: WallpaperFit,
    focused: Shortcut? = null,
    dimmed: Boolean = false,
) {
    val accent = LocalAccent.current
    // A dark base always carries the accent hue, so even "solid" reads as coloured, not black.
    val base = if (style == BackgroundStyle.SOLID) blendColor(Background, accent, lerpF(0.04f, 0.24f, intensity)) else Background
    Box(Modifier.fillMaxSize().background(base)) {
        when (style) {
            BackgroundStyle.AMBIENT -> AmbientBackdrop(focused, intensity)
            BackgroundStyle.ACCENT_GRADIENT -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(accent.copy(alpha = lerpF(0.18f, 0.65f, intensity)), Background))
                )
            )
            BackgroundStyle.WALLPAPER -> if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = fit.contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
                MediaScrim(intensity, dimmed)
            }
            BackgroundStyle.AURORA -> AuroraBackdrop(accent, intensity)
            BackgroundStyle.VIDEO -> if (video != null) {
                VideoBackdrop(video, fit)
                MediaScrim(intensity, dimmed)
            }
            BackgroundStyle.DYNAMIC -> if (dynamic != null) {
                DynamicBackdrop(dynamic.schedule, dynamic.frame, dynamic.city, fit.contentScale, dynamic.id)
                MediaScrim(intensity, dimmed)
            }
            BackgroundStyle.SOLID -> Unit // the tinted base is the whole look
        }
    }
}

private val WallpaperFit.contentScale: ContentScale
    get() = when (this) {
        WallpaperFit.FILL -> ContentScale.Crop
        WallpaperFit.STRETCH -> ContentScale.FillBounds
        WallpaperFit.FIT -> ContentScale.Fit
        WallpaperFit.CENTER -> ContentScale.None
        WallpaperFit.SPAN -> ContentScale.FillWidth
    }

/**
 * The background's dimming behind an image, a video or a dynamic wallpaper. On the grid tabs
 * the whole picture is dimmed (their cards are all over it), the more so the lower the
 * brightness setting; the hub leaves it clear and only shades its bottom (see HubShade).
 */
@Composable
private fun MediaScrim(intensity: Float, dimmed: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (dimmed) lerpF(0.8f, 0.2f, intensity) else 0f,
        animationSpec = tween(300),
        label = "scrim",
    )
    Box(Modifier.fillMaxSize().background(Background.copy(alpha = alpha)))
}

/**
 * The focused app's banner as hero art in the top-right corner, fading into the background
 * towards the left (so the hero title over there stays clean) and towards the bottom (no hard
 * edge). Drawn smaller than full-screen so a low-resolution banner doesn't blow up into a blur.
 * [intensity] (the background-brightness setting) sets how strongly it shows. No blur:
 * RenderEffect is API 31+.
 */
@Composable
private fun AmbientBackdrop(focused: Shortcut?, intensity: Float) {
    Crossfade(targetState = focused?.banner, label = "backdrop") { banner ->
        if (banner != null) {
            val bitmap = remember(banner) { banner.toBitmap().asImageBitmap() }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(0.62f)
                        .fillMaxHeight(0.62f),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = lerpF(0.25f, 0.8f, intensity),
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Fade the art out into the plain background on its left and bottom edges.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0f to Background,
                                0.45f to Background.copy(alpha = 0f),
                            )
                        )
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.35f to Background.copy(alpha = 0f),
                                1f to Background,
                            )
                        )
                    )
                }
            }
        }
    }
}
