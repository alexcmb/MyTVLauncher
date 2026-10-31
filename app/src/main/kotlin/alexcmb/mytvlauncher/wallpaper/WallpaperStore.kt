package alexcmb.mytvlauncher.wallpaper

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.dynamic.DynamicSchedule
import alexcmb.mytvlauncher.dynamic.DynamicWallpaperImporter
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.SettingsRepository
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The wallpaper library on the device: the list (in the settings), the pictures extracted
 * from dynamic files, and the gallery's thumbnails. The blocking calls ([add], [thumbnail])
 * belong off the main thread.
 */
class WallpaperStore(private val context: Context, private val settings: SettingsRepository) {

    sealed interface AddResult {
        data class Added(val wallpaper: Wallpaper) : AddResult
        data object Failed : AddResult
    }

    private val importer = DynamicWallpaperImporter(context)
    private val thumbs get() = File(context.filesDir, "thumbs")

    /** Where a dynamic wallpaper's pictures were extracted. */
    fun folder(id: String) = File(context.filesDir, "dynamic/$id")

    fun frame(id: String, index: Int) = DynamicWallpaperImporter.frame(folder(id), index)

    /** The library, carried over from the one-per-kind settings the first time. */
    fun library(): List<Wallpaper> {
        settings.wallpapers()?.let { return WallpaperLibrary.decode(it) }
        return migrate()
    }

    private fun save(library: List<Wallpaper>) = settings.setWallpapers(WallpaperLibrary.encode(library))

    /**
     * Adds the document at [uri]. A video is kept as a link to the file; an image is looked
     * into for a dynamic schedule — if it has one its pictures are extracted, else it's kept as
     * a still. Blocking.
     */
    fun add(uri: Uri): AddResult {
        val type = context.contentResolver.getType(uri).orEmpty()
        val name = WallpaperLibrary.displayName(queryName(uri))
        val id = WallpaperLibrary.newId()
        val wallpaper = if (type.startsWith("video/")) {
            keepAccess(uri)
            Wallpaper(id, WallpaperKind.VIDEO, name ?: context.getString(R.string.background_video), uri = uri.toString())
        } else {
            when (val result = importer.import(uri, folder(id))) {
                is DynamicWallpaperImporter.Result.Done ->
                    Wallpaper(id, WallpaperKind.DYNAMIC, name ?: context.getString(R.string.background_dynamic), schedule = DynamicSchedule.encode(result.schedule))
                // An ordinary picture (or a HEIC this Android can't take apart): a still.
                DynamicWallpaperImporter.Result.NotDynamic, DynamicWallpaperImporter.Result.Unsupported -> {
                    keepAccess(uri)
                    Wallpaper(id, WallpaperKind.STILL, name ?: context.getString(R.string.background_wallpaper), uri = uri.toString())
                }
                DynamicWallpaperImporter.Result.Failed -> return AddResult.Failed
            }
        }
        save(library() + wallpaper)
        return AddResult.Added(wallpaper)
    }

    /** Forgets [wallpaper]: its entry, its extracted pictures, thumbnails and file access. */
    fun remove(wallpaper: Wallpaper) {
        save(library().filterNot { it.id == wallpaper.id })
        if (settings.activeWallpaper() == wallpaper.id) settings.setActiveWallpaper(null)
        folder(wallpaper.id).deleteRecursively()
        thumbs.listFiles { f -> f.name.startsWith(wallpaper.id + "-") }?.forEach { it.delete() }
        wallpaper.uri?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    /**
     * A small picture of [wallpaper] for its card — for a dynamic one, of its picture [index]
     * — made once and kept. Null if the file can't be read any more. Blocking.
     */
    fun thumbnail(wallpaper: Wallpaper, index: Int = 0): Bitmap? {
        val file = File(thumbs, "${wallpaper.id}-$index.jpg")
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it }
        val source = runCatching {
            when (wallpaper.kind) {
                WallpaperKind.VIDEO -> videoFrame(Uri.parse(wallpaper.uri))
                WallpaperKind.STILL -> decodeSampled(Uri.parse(wallpaper.uri))
                WallpaperKind.DYNAMIC -> BitmapFactory.decodeFile(
                    frame(wallpaper.id, index).path,
                    BitmapFactory.Options().apply { inSampleSize = 2 },
                )
            }
        }.onFailure { Log.w(TAG, "No thumbnail for a wallpaper", it) }.getOrNull() ?: return null
        val thumb = cover(source, THUMB_W, THUMB_H)
        if (thumb !== source) source.recycle()
        runCatching {
            thumbs.mkdirs()
            file.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        return thumb
    }

    private fun videoFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // A second in, past any fade from black at the start.
            retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } finally {
            retriever.release()
        }
    }

    /**
     * A still wallpaper's picture, to draw full screen: no wider than 2560 pixels (a 4K or
     * larger photo is decoded at a half, a quarter…). Null if it can't be read. Blocking.
     */
    fun still(wallpaper: Wallpaper): Bitmap? = runCatching {
        val uri = Uri.parse(wallpaper.uri ?: return null)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth > 0 && bounds.outWidth / sample > 2560) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    private fun decodeSampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_W * 2) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun keepAccess(uri: Uri) {
        // Read access that survives reboots; a provider that doesn't offer it still works for now.
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /**
     * Before the library: at most one image, one video and one dynamic wallpaper, the latter's
     * pictures straight in files/dynamic. They become the library's first entries — the one on
     * screen stays on screen — and the old settings are cleared.
     */
    private fun migrate(): List<Wallpaper> {
        val (image, video, dynamic) = settings.legacyWallpapers()
        val library = mutableListOf<Wallpaper>()
        val style = settings.background()
        fun adopt(wallpaper: Wallpaper, shownAs: BackgroundStyle) {
            library += wallpaper
            if (style == shownAs) settings.setActiveWallpaper(wallpaper.id)
        }
        dynamic?.let { schedule ->
            val id = WallpaperLibrary.newId()
            val legacy = File(context.filesDir, "dynamic")
            val moving = File(context.filesDir, "dynamic.moving")
            val moved = legacy.renameTo(moving) && legacy.mkdirs() && moving.renameTo(folder(id))
            if (moved) adopt(Wallpaper(id, WallpaperKind.DYNAMIC, context.getString(R.string.background_dynamic), schedule = schedule), BackgroundStyle.DYNAMIC)
        }
        video?.let { uri ->
            val name = WallpaperLibrary.displayName(queryName(Uri.parse(uri))) ?: context.getString(R.string.background_video)
            adopt(Wallpaper(WallpaperLibrary.newId(), WallpaperKind.VIDEO, name, uri = uri), BackgroundStyle.VIDEO)
        }
        image?.let { uri ->
            val name = WallpaperLibrary.displayName(queryName(Uri.parse(uri))) ?: context.getString(R.string.background_wallpaper)
            adopt(Wallpaper(WallpaperLibrary.newId(), WallpaperKind.STILL, name, uri = uri), BackgroundStyle.WALLPAPER)
        }
        save(library)
        settings.clearLegacyWallpapers()
        return library
    }

    /** [source] scaled to cover [w]×[h], cropped around its centre. */
    private fun cover(source: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / source.width, h.toFloat() / source.height)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val sw = (source.width * scale).roundToInt()
        val sh = (source.height * scale).roundToInt()
        val dst = Rect((w - sw) / 2, (h - sh) / 2, (w - sw) / 2 + sw, (h - sh) / 2 + sh)
        Canvas(out).drawBitmap(source, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private companion object {
        const val TAG = "WallpaperStore"
        const val THUMB_W = 384
        const val THUMB_H = 216
    }
}
