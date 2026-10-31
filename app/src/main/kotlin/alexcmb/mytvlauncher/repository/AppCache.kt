package alexcmb.mytvlauncher.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The app list as it was last read from the PackageManager — names, artwork — kept on disk so
 * the next start shows it at once, instead of waiting on every app's files (tens of seconds on
 * a TV that has just booted). The real list is read behind it and replaces it.
 */
class AppCache(private val context: Context) {

    /**
     * One app as the launcher shows it. [stamp] tells whether its package changed since (an
     * update moves and rewrites the APK); [icon] is only kept when there is no [banner].
     */
    class Entry(
        val id: String,
        val title: String,
        val stamp: String,
        val system: Boolean,
        val icon: Drawable?,
        val banner: Drawable?,
    ) {
        val line get() = AppCacheIndex.Line(id, title, stamp, system, art)
        private val art get() = when {
            banner != null -> AppCacheIndex.Art.BANNER
            icon != null -> AppCacheIndex.Art.ICON
            else -> AppCacheIndex.Art.NONE
        }
    }

    private val dir = File(context.filesDir, "apps")
    private val index = File(dir, "index")

    /** What the last [write] saved, or null when there is none, or it was for another language. */
    fun read(locale: String): List<Entry>? = runCatching {
        val lines = lines(locale) ?: return null
        lines.map { line ->
            val art = if (line.art == AppCacheIndex.Art.NONE) null else image(line.id)
            // A picture that can't be read back: no stamp, so the next load reads the app again.
            val stamp = if (art == null && line.art != AppCacheIndex.Art.NONE) "" else line.stamp
            Entry(
                line.id, line.title, stamp, line.system,
                icon = art.takeIf { line.art == AppCacheIndex.Art.ICON },
                banner = art.takeIf { line.art == AppCacheIndex.Art.BANNER },
            )
        }
    }.onFailure { Log.w(TAG, "App cache unreadable", it) }.getOrNull()

    /**
     * Saves [entries] for the next start. Only the pictures of apps that changed are written
     * again, and those of apps that are gone are dropped. Blocking.
     */
    fun write(locale: String, entries: List<Entry>) {
        runCatching {
            dir.mkdirs()
            val before = lines(locale).orEmpty().associateBy { it.id }
            entries.forEach { entry ->
                val art = entry.banner ?: entry.icon ?: return@forEach
                if (before[entry.id] == entry.line && file(entry.id).exists()) return@forEach
                file(entry.id).outputStream().use { art.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val kept = entries.mapTo(HashSet()) { file(it.id).name } + index.name
            dir.listFiles()?.filter { it.name !in kept }?.forEach { it.delete() }
            // Written aside and moved in place, so a start never reads half an index.
            val next = File(dir, "index.new")
            next.writeText(AppCacheIndex.encode(locale, entries.map { it.line }))
            next.renameTo(index)
        }.onFailure { Log.w(TAG, "App cache not saved", it) }
    }

    private fun lines(locale: String) = AppCacheIndex.decode(index.takeIf { it.exists() }?.readText(), locale)

    private fun file(id: String) = File(dir, "$id.png")

    private fun image(id: String): Drawable? =
        BitmapFactory.decodeFile(file(id).path)?.let { BitmapDrawable(context.resources, it) }

    private companion object {
        const val TAG = "AppCache"
    }
}

/** The cache's index, and its pure (testable) encoding. */
object AppCacheIndex {

    /** Which of its pictures an app is shown with. */
    enum class Art { BANNER, ICON, NONE }

    data class Line(val id: String, val title: String, val stamp: String, val system: Boolean, val art: Art)

    /** Bumped whenever the format changes: an older index is then just ignored. */
    private const val VERSION = "1"

    /**
     * A header (version, then the [locale] the names were read in), then one line per app,
     * its fields URL-encoded and tab-separated (an app's name can hold anything).
     */
    fun encode(locale: String, lines: List<Line>): String =
        (listOf(listOf(VERSION, locale)) + lines.map { listOf(it.id, it.title, it.stamp, it.system.toString(), it.art.name) })
            .joinToString("\n") { fields -> fields.joinToString("\t") { URLEncoder.encode(it, "UTF-8") } }

    /** The apps saved by [encode]; null if there are none, or they were for another format or language. */
    fun decode(text: String?, locale: String): List<Line>? {
        val rows = text.orEmpty().lines().filter { it.isNotEmpty() }
            .map { line -> line.split("\t").map { URLDecoder.decode(it, "UTF-8") } }
        if (rows.firstOrNull() != listOf(VERSION, locale)) return null
        return rows.drop(1).mapNotNull { f ->
            if (f.size != 5 || f[0].isEmpty()) return@mapNotNull null
            val art = runCatching { Art.valueOf(f[4]) }.getOrNull() ?: return@mapNotNull null
            Line(f[0], f[1], f[2], f[3].toBoolean(), art)
        }
    }
}
