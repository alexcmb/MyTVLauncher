package alexcmb.mytvlauncher.source

import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** One external input the TV can switch to (HDMI, composite, …). */
data class TvSource(
    val id: String,
    val label: String,
    val type: Int,
    val state: Int = TvInputManager.INPUT_STATE_DISCONNECTED,
) {
    val isHdmi: Boolean get() = type == TvInputInfo.TYPE_HDMI

    /** A device is plugged in and powered on — the input worth showing first. */
    val isConnected: Boolean get() = state == TvInputManager.INPUT_STATE_CONNECTED

    /** Something is plugged in, even if it is currently on standby. */
    val isAvailable: Boolean get() = state != TvInputManager.INPUT_STATE_DISCONNECTED
}
