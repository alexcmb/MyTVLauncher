package alexcmb.mytvlauncher.wallpaper

import alexcmb.mytvlauncher.dynamic.DynamicSchedule
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/** What a wallpaper of the library is made of — the gallery's filters and badges. */
enum class WallpaperKind { VIDEO, DYNAMIC, STILL }

/**
 * One imported wallpaper. A video or an image is read from its document [uri] (with a lasting
 * read permission); a dynamic one was extracted at import and keeps only its [schedule]
 * (DynamicSchedule.encode) — its pictures are in the app's storage, under [id].
 */
data class Wallpaper(
    val id: String,
    val kind: WallpaperKind,
    val name: String,
    val uri: String? = null,
    val schedule: String? = null,
) {
    val dynamicSchedule: DynamicSchedule? get() = schedule?.let(DynamicSchedule::decode)
}

/** The library as stored in the settings, and the pure helpers around it (kept testable). */
object WallpaperLibrary {

    fun newId(): String = UUID.randomUUID().toString().take(8)

    /** One line per wallpaper, its fields URL-encoded and tab-separated (names can hold anything). */
    fun encode(library: List<Wallpaper>): String = library.joinToString("\n") { w ->
        listOf(w.id, w.kind.name, w.name, w.uri.orEmpty(), w.schedule.orEmpty())
            .joinToString("\t") { URLEncoder.encode(it, "UTF-8") }
    }

    fun decode(text: String?): List<Wallpaper> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split("\t").map { URLDecoder.decode(it, "UTF-8") }
        if (f.size < 5) return@mapNotNull null
        val kind = runCatching { WallpaperKind.valueOf(f[1]) }.getOrNull() ?: return@mapNotNull null
        Wallpaper(f[0], kind, f[2], f[3].ifEmpty { null }, f[4].ifEmpty { null })
    }

    /** A file name made into a card title: no extension, separators as spaces. */
    fun displayName(fileName: String?): String? = fileName
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')
        ?.trim()
        ?.ifEmpty { null }

    /** The sun's moments a by-sun strip is labelled with. */
    enum class SunPhase { DAWN, DAY, DUSK, NIGHT }

    /**
     * One picture of a gallery card's strip and what to label it with: the time of day it
     * starts showing ([minutes] after midnight, by time) or the moment of the sun ([phase]).
     */
    data class DayPreview(val image: Int, val minutes: Int? = null, val phase: SunPhase? = null)

    /**
     * Up to four of a dynamic wallpaper's pictures that tell its day apart, for the gallery
     * card's strip. By time: the pictures in the order the day goes through them from 4 a.m.
     * (so the strip runs from morning to night), evenly sampled. By sun: the picture nearest
     * each of four sun positions — morning horizon, high noon, evening horizon, deep night.
     * A picture shows once, so a file of two pictures gives a strip of two.
     */
    fun previews(schedule: DynamicSchedule, max: Int = 4): List<DayPreview> = when (schedule) {
        is DynamicSchedule.ByTime -> {
            val start = 4.0 / 24
            val ordered = schedule.frames
                .sortedBy { (it.dayFraction - start + 1.0) % 1.0 }
                .distinctBy { it.image }
                .map { DayPreview(it.image, minutes = Math.round(it.dayFraction * 1440).toInt() % 1440) }
            if (ordered.size <= max) ordered
            else List(max) { k -> ordered[Math.round(k * (ordered.size - 1) / (max - 1).toDouble()).toInt()] }
        }
        is DynamicSchedule.BySun -> listOf(
            SunPhase.DAWN to (5.0 to 90.0),
            SunPhase.DAY to (60.0 to 180.0),
            SunPhase.DUSK to (5.0 to 270.0),
            SunPhase.NIGHT to (-40.0 to 0.0),
        ).map { (phase, sky) ->
            val image = schedule.frames.minBy { DynamicSchedule.angle(sky.first, sky.second, it.altitude, it.azimuth) }.image
            DayPreview(image, phase = phase)
        }.distinctBy { it.image }.take(max)
    }
}
