package alexcmb.mytvlauncher.compose

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The accent colour, provided at the top of the tree so every surface reads the same one. */
val LocalAccent = staticCompositionLocalOf { Color(0xFF3D5AFE) }
