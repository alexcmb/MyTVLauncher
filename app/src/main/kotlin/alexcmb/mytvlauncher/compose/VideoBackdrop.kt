package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.repository.WallpaperFit
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A looping, silent video behind the launcher — the "Vidéo" background style.
 *
 * Played on a SurfaceView: the video lives on its own hardware surface under the launcher's
 * window, composited by the display hardware, so a new video frame doesn't make the launcher
 * redraw anything (drawing animation into the UI itself, as the aurora does, costs the CPU on
 * every frame). Decoding is done by the TV's hardware decoders through ExoPlayer, which also
 * loops without the gap MediaPlayer leaves between two passes. Measured on the test TV with a
 * 720p H.264 loop: ~18% CPU in the launcher (the player feeding the decoder), nothing while
 * an app is in front.
 *
 * Plays only while the launcher is on screen (STARTED): behind an app it pauses, so it never
 * competes with what's being watched. [fit] places the video like a wallpaper image.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoBackdrop(uri: Uri, fit: WallpaperFit) {
    val context = LocalContext.current
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    val player = remember(uri) {
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true)).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Some files the TV's decoder refuses (an H.264 level below the frame size,
                    // an unsupported codec): say so rather than leave a silent black screen.
                    Log.w("VideoBackdrop", "Cannot play the video background", error)
                    Toast.makeText(context, R.string.background_video_error, Toast.LENGTH_LONG).show()
                }
            })
            prepare()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            player.playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize().videoFit(videoSize, fit)) {
        AndroidView(
            factory = { SurfaceView(it) },
            update = { player.setVideoSurfaceView(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Sizes and centres the video's view in the screen for [fit], from the video's own size
 * (unknown until the first frame: it fills the screen until then). The surface isn't clipped
 * to the screen, so what overflows (FILL, SPAN) simply falls off its edges.
 */
private fun Modifier.videoFit(video: VideoSize, fit: WallpaperFit): Modifier = layout { measurable, constraints ->
    val screenW = constraints.maxWidth
    val screenH = constraints.maxHeight
    val aspect = if (video.width > 0 && video.height > 0) {
        video.width * video.pixelWidthHeightRatio / video.height
    } else {
        0f
    }
    val (w, h) = when {
        aspect == 0f || fit == WallpaperFit.STRETCH -> screenW to screenH
        fit == WallpaperFit.CENTER -> video.width to video.height
        fit == WallpaperFit.SPAN -> screenW to (screenW / aspect).roundToInt()
        else -> {
            val scale = if (fit == WallpaperFit.FILL) {
                max(screenW / aspect, screenH.toFloat()) / screenH
            } else {
                min(screenW / aspect, screenH.toFloat()) / screenH
            }
            val height = (screenH * scale).roundToInt()
            (height * aspect).roundToInt() to height
        }
    }
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(screenW, screenH) {
        placeable.place((screenW - w) / 2, (screenH - h) / 2)
    }
}
