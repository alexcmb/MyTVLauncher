package alexcmb.mytvlauncher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * A snapshot of whatever is playing right now, ready to show on the hub. The position is
 * as of [positionAtMs] (elapsedRealtime) and moves on at [speed] while playing — sessions
 * only report it on changes, not continuously; see [positionAt]. [durationMs] is 0 when the
 * app doesn't say (live streams, some players).
 */
data class NowPlaying(
    val title: String,
    val subtitle: String?,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val positionAtMs: Long = 0,
    val speed: Float = 1f,
) {
    /** Where playback is at [nowMs] (elapsedRealtime), kept within the duration. */
    fun positionAt(nowMs: Long): Long {
        val moved = if (isPlaying) ((nowMs - positionAtMs) * speed).toLong() else 0L
        val position = (positionMs + moved).coerceAtLeast(0)
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }

    /** How far through it is, 0–1, or null when the duration is unknown. */
    fun progressAt(nowMs: Long): Float? =
        if (durationMs > 0) positionAt(nowMs).toFloat() / durationMs else null
}

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

    /**
     * Prefer a session that's playing (or loading its next track); fall back to any with a
     * title. Sessions without one are skipped: a player that's open but empty (VLC on its
     * library, say) keeps an active session with blank metadata, and picking it would hide
     * the card while another app has a track paused.
     */
    private fun pick(controllers: List<MediaController>): MediaController? {
        val titled = controllers.filter { titleOf(it.metadata) != null }
        return titled.firstOrNull { it.playbackState?.state in LIVE_STATES } ?: titled.firstOrNull()
    }

    private fun titleOf(meta: MediaMetadata?): String? = meta?.let {
        (it.getText(MediaMetadata.METADATA_KEY_TITLE) ?: it.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
            ?.toString()?.trim()?.ifBlank { null }
    }

    fun nowPlaying(): NowPlaying? {
        val c = controller ?: return null
        val meta = c.metadata ?: return null
        val title = titleOf(meta) ?: return null
        val subtitle = (meta.getText(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
            ?.toString()?.trim()?.ifBlank { null }
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: artFromUri(meta)
        val state = c.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        return NowPlaying(
            title = title,
            subtitle = subtitle,
            art = art,
            isPlaying = playing,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            positionMs = state?.position?.coerceAtLeast(0) ?: 0,
            positionAtMs = state?.lastPositionUpdateTime ?: 0,
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    // Many players hand the cover over as a link (content:// or https://) rather than a bitmap.
    // It's fetched once, off the main thread, and kept; the UI is told when it arrives.
    private val artLoader = Executors.newSingleThreadExecutor()
    private var artUri: String? = null
    private var artBitmap: Bitmap? = null

    private fun artFromUri(meta: MediaMetadata): Bitmap? {
        val uri = ART_URI_KEYS.firstNotNullOfOrNull { meta.getString(it)?.takeIf(String::isNotBlank) }
            ?: return null
        if (uri == artUri) return artBitmap
        artUri = uri
        artBitmap = null
        artLoader.execute {
            val bitmap = loadBitmap(uri)
            handler.post {
                if (artUri == uri && bitmap != null) {
                    artBitmap = bitmap
                    onChanged?.invoke()
                }
            }
        }
        return null
    }

    /** A cover from a content:// or http(s):// link, scaled down to at most ~512px. */
    private fun loadBitmap(uri: String): Bitmap? = runCatching {
        val parsed = Uri.parse(uri)
        fun open() = when (parsed.scheme) {
            "http", "https" -> (URL(uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
            }.inputStream
            else -> context.contentResolver.openInputStream(parsed)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512) sample *= 2
        open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.onFailure { Log.w(TAG, "Cover not loaded", it) }.getOrNull()

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

    private companion object {
        /** Playing, or about to: buffering, connecting, skipping to another track. */
        val LIVE_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )
        const val TAG = "NowPlayingController"
        val ART_URI_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )
    }
}
