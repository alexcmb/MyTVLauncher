package alexcmb.mytvlauncher.source

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/**
 * Lists the TV's external inputs and switches to them. Switching just opens the input's
 * passthrough channel; the system's live-TV app takes it from there. No permission needed —
 * querying the inputs and opening a passthrough channel are both open to any app.
 */
class TvSourceController(private val context: Context) {
    private val tvInputManager =
        context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    /** Notified when an input appears or disappears, so the UI can re-read the list. */
    var onChanged: (() -> Unit)? = null

    private val callback = object : TvInputManager.TvInputCallback() {
        override fun onInputAdded(inputId: String) { onChanged?.invoke() }
        override fun onInputRemoved(inputId: String) { onChanged?.invoke() }
        override fun onInputStateChanged(inputId: String, state: Int) { onChanged?.invoke() }
    }

    fun startListening() = tvInputManager?.registerCallback(callback, android.os.Handler(context.mainLooper))

    fun stopListening() = tvInputManager?.unregisterCallback(callback)

    /** The physical inputs (HDMI, AV, …), HDMI first then the rest, each by its own order. */
    fun sources(): List<TvSource> {
        val tvm = tvInputManager ?: return emptyList()
        return tvm.tvInputList
            .filter { it.isPassthroughInput && it.type != TvInputInfo.TYPE_TUNER }
            .map { TvSource(it.id, it.loadLabel(context)?.toString().orEmpty().ifBlank { it.id }, it.type) }
            .sortedWith(compareByDescending<TvSource> { it.isHdmi }.thenBy { it.label })
    }

    fun hasSources(): Boolean = sources().isNotEmpty()

    /** Switches the TV to the given input by opening its passthrough channel. */
    fun open(source: TvSource) {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(source.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open input ${source.id}", e)
        }
    }

    private companion object {
        const val TAG = "TvSourceController"
    }
}
