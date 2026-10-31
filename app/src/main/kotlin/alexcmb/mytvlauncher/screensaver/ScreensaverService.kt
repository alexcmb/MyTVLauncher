package alexcmb.mytvlauncher.screensaver

import alexcmb.mytvlauncher.compose.Backdrop
import alexcmb.mytvlauncher.compose.DynamicWallpaper
import alexcmb.mytvlauncher.compose.LauncherTheme
import alexcmb.mytvlauncher.compose.LocalAccent
import alexcmb.mytvlauncher.compose.Screensaver
import alexcmb.mytvlauncher.dynamic.Cities
import alexcmb.mytvlauncher.media.NowPlaying
import alexcmb.mytvlauncher.media.NowPlayingController
import alexcmb.mytvlauncher.repository.SettingsRepository
import alexcmb.mytvlauncher.wallpaper.WallpaperKind
import alexcmb.mytvlauncher.wallpaper.WallpaperStore
import android.net.Uri
import android.service.dreams.DreamService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launcher's sleep mode as the TV's own screensaver (Settings → Device preferences →
 * Screen saver): the background chosen in the launcher, darkened, with the time and what's
 * playing bouncing around (see [Screensaver]). It starts after the TV's own idle delay, over
 * any app that doesn't keep the screen on; any key ends it.
 *
 * A DreamService isn't an activity, so it gives Compose the lifecycle an activity would:
 * resumed while dreaming, which also runs and pauses a video or aurora background with it.
 */
class ScreensaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val settings by lazy { SettingsRepository.getInstance(applicationContext) }
    private val nowPlayingController by lazy { NowPlayingController(this) }
    private var nowPlaying by mutableStateOf<NowPlaying?>(null)

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        nowPlayingController.onChanged = { nowPlaying = nowPlayingController.nowPlaying() }
        val store = WallpaperStore(this, settings)
        val style = settings.background()
        val active = store.library().firstOrNull { it.id == settings.activeWallpaper() }
        val city = Cities.named(settings.dynamicCity())
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScreensaverService)
            setViewTreeSavedStateRegistryOwner(this@ScreensaverService)
            setContent {
                // A still is decoded off the main thread; the veil shows over the plain base meanwhile.
                val image by produceState<ImageBitmap?>(null, active) {
                    value = active?.takeIf { it.kind == WallpaperKind.STILL }
                        ?.let { withContext(Dispatchers.IO) { store.still(it) } }?.asImageBitmap()
                }
                CompositionLocalProvider(LocalAccent provides Color(settings.accent().argb)) {
                    LauncherTheme {
                        Box(Modifier.fillMaxSize()) {
                            Backdrop(
                                style = style,
                                image = image,
                                video = active?.takeIf { it.kind == WallpaperKind.VIDEO }?.uri?.let(Uri::parse),
                                dynamic = active?.takeIf { it.kind == WallpaperKind.DYNAMIC }?.let { w ->
                                    w.dynamicSchedule?.let { DynamicWallpaper(it, { i -> store.frame(w.id, i) }, city, w.id) }
                                },
                                intensity = settings.backgroundIntensity() / 100f,
                                fit = settings.wallpaperFit(),
                            )
                            Screensaver(nowPlaying)
                        }
                    }
                }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        nowPlayingController.startListening()
        nowPlaying = nowPlayingController.nowPlaying()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        nowPlayingController.stopListening()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}
