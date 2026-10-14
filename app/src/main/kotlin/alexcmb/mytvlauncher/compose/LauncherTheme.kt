package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography

/**
 * Outfit, the launcher's typeface: geometric and round, legible from the sofa. Bundled
 * (res/font, SIL Open Font License — see licenses/Outfit-OFL.txt) as one variable font; each
 * weight below is a setting of its weight axis. Android 8+ applies the axis; older systems
 * show the font at its default weight.
 */
@OptIn(ExperimentalTextApi::class)
val Outfit = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { weight ->
        Font(
            R.font.outfit,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
)

/** Every tv-material text style, in Outfit. */
private val OutfitTypography: Typography = Typography().run {
    fun TextStyle.outfit() = copy(fontFamily = Outfit)
    Typography(
        displayLarge = displayLarge.outfit(),
        displayMedium = displayMedium.outfit(),
        displaySmall = displaySmall.outfit(),
        headlineLarge = headlineLarge.outfit(),
        headlineMedium = headlineMedium.outfit(),
        headlineSmall = headlineSmall.outfit(),
        titleLarge = titleLarge.outfit(),
        titleMedium = titleMedium.outfit(),
        titleSmall = titleSmall.outfit(),
        bodyLarge = bodyLarge.outfit(),
        bodyMedium = bodyMedium.outfit(),
        bodySmall = bodySmall.outfit(),
        labelLarge = labelLarge.outfit(),
        labelMedium = labelMedium.outfit(),
        labelSmall = labelSmall.outfit(),
    )
}

/**
 * The launcher's theme: tv-material with Outfit throughout. Wraps the whole screen — the
 * home and the menus, prompts and sliders drawn over it — so every Text picks the font up.
 */
@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = OutfitTypography, content = content)
}
