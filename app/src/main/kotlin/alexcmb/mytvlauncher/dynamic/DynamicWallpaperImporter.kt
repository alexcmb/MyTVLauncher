package alexcmb.mytvlauncher.dynamic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a dynamic HEIC (several pictures of one scene through the day, plus a schedule) into
 * what the launcher shows: each picture as a 1920×1080 JPEG in the app's own storage, and the
 * schedule. Done once, at import: the originals are often 5–6K, far too heavy to decode live
 * on a TV (about 60 MB of memory and seconds of work each).
 */
class DynamicWallpaperImporter(private val context: Context) {

    sealed interface Result {
        data class Done(val schedule: DynamicSchedule, val images: Int) : Result
        /** Android before 9 can't read the pictures of a HEIC. */
        data object Unsupported : Result
        /** An ordinary image, or a schedule we can't read. */
        data object NotDynamic : Result
        data object Failed : Result
    }

    /** Where the imported pictures live: dynamic/0.jpg, 1.jpg… */
    val folder: File get() = File(context.filesDir, "dynamic")

    fun frame(index: Int) = File(folder, "$index.jpg")

    /** Blocking (call it off the main thread). [onProgress] gets (done, total). */
    fun import(uri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Result.Unsupported
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.Failed
            val schedule = DynamicSchedule.find(bytes) ?: return Result.NotDynamic
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val count = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_COUNT)
                    ?.toIntOrNull() ?: 0
                if (count < schedule.imageCount) return Result.NotDynamic
                // A fresh folder written aside, swapped in only once complete: a failed
                // import leaves the current wallpaper untouched.
                val staging = File(context.filesDir, "dynamic.new").apply { deleteRecursively(); mkdirs() }
                val params = MediaMetadataRetriever.BitmapParams().apply {
                    preferredConfig = Bitmap.Config.RGB_565 // half the memory of a 6K ARGB frame
                }
                for (i in 0 until schedule.imageCount) {
                    val full = retriever.getImageAtIndex(i, params) ?: return Result.Failed
                    val scaled = cover(full, 1920, 1080)
                    if (scaled !== full) full.recycle()
                    File(staging, "$i.jpg").outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    scaled.recycle()
                    onProgress(i + 1, schedule.imageCount)
                }
                folder.deleteRecursively()
                if (!staging.renameTo(folder)) return Result.Failed
                Result.Done(schedule, schedule.imageCount)
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic wallpaper import failed", e)
            Result.Failed
        }
    }

    fun clear() {
        folder.deleteRecursively()
    }

    /** [source] scaled so it covers [w]×[h], cropped to that size around its centre. */
    private fun cover(source: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / source.width, h.toFloat() / source.height)
        if (scale >= 1f && source.width == w && source.height == h) return source
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val sw = (source.width * scale).roundToInt()
        val sh = (source.height * scale).roundToInt()
        val dst = android.graphics.Rect((w - sw) / 2, (h - sh) / 2, (w - sw) / 2 + sw, (h - sh) / 2 + sh)
        Canvas(out).drawBitmap(source, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private companion object {
        const val TAG = "DynamicWallpaper"
    }
}
