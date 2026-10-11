package alexcmb.mytvlauncher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/** A snapshot of whatever is playing right now, ready to show on the hub. */
data class NowPlaying(
    val title: String,
    val subtitle: String?,
    val art: Bitmap?,
    val isPlaying: Boolean,
)

/**
 * Reads the TV's active media sessions (music, video, …) through [MediaSessionManager] and
 * surfaces the most relevant one, plus play/pause and "open the app". Listing other apps'
 * sessions needs notification-listener access, granted once — on a TV, over adb:
 *
 *   adb shell cmd notification allow_listener <pkg>/alexcmb.mytvlauncher.media.NowPlayingListenerService
 *
 * Without the grant the controller simply reports nothing (no crash), and the hub hides the card.
 */
class NowPlayingController(private val context: Context) {

    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent = ComponentName(context, NowPlayingListenerService::class.java)
    private val handler = Handler(context.mainLooper)

    /** Called whenever the now-playing state changes, so the UI can re-read it. */
    var onChanged: (() -> Unit)? = null

    /** The session we're currently showing; kept so play/pause and open act on the right one. */
    private var controller: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { rebind(it) }

    private val controllerCallback = object : MediaController.Callback() {
        // Our session paused/played: re-pick, in case another session is the one now playing.
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) { onChanged?.invoke() }
        override fun onSessionDestroyed() = refresh()
    }

    /** Whether the launcher may read other apps' media sessions. */
    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun startListening() {
        val manager = sessionManager ?: return
        if (!hasAccess()) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            rebind(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification access for media sessions", e)
        }
    }

    fun stopListening() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private fun refresh() {
        val manager = sessionManager ?: return
        if (!hasAccess()) return
        try {
            rebind(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification access for media sessions", e)
        }
    }

    /** Switch our callback to the best current session and tell the UI. */
    private fun rebind(controllers: List<MediaController>?) {
        val next = pick(controllers.orEmpty())
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = next
            controller?.registerCallback(controllerCallback, handler)
        }
        onChanged?.invoke()
    }

    /** Prefer a session that's actually playing; fall back to any with something loaded. */
    private fun pick(controllers: List<MediaController>): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null }

    fun nowPlaying(): NowPlaying? {
        val c = controller ?: return null
        val meta = c.metadata ?: return null
        val title = (meta.getText(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
            ?.toString()?.trim().orEmpty()
        if (title.isBlank()) return null
        val subtitle = (meta.getText(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
            ?.toString()?.trim()?.ifBlank { null }
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return NowPlaying(title, subtitle, art, playing)
    }

    /** OK on the card: pause if playing, resume if paused. */
    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    /** Long-press on the card: bring the playing app to the front. */
    fun openApp() {
        val c = controller ?: return
        try {
            val sessionActivity = c.sessionActivity
            if (sessionActivity != null) {
                sessionActivity.send()
            } else {
                val intent = context.packageManager.getLeanbackLaunchIntentForPackage(c.packageName)
                    ?: context.packageManager.getLaunchIntentForPackage(c.packageName)
                intent?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open media app ${c.packageName}", e)
        }
    }

    private companion object { const val TAG = "NowPlayingController" }
}
