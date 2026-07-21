package alexcmb.mytvlauncher.source

import android.media.tv.TvInputInfo

/** One external input the TV can switch to (HDMI, composite, …). */
data class TvSource(val id: String, val label: String, val type: Int) {
    val isHdmi: Boolean get() = type == TvInputInfo.TYPE_HDMI
}
