package alexcmb.mytvlauncher.dynamic

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * When each image of a dynamic wallpaper shows. Read from the XMP of the HEIC file (the
 * apple_desktop:h24 or apple_desktop:solar property: a base64 binary plist), and turned into
 * "which two images, blended how much" for a given moment.
 */
sealed interface DynamicSchedule {
    /** The image count the schedule refers to (highest index + 1). */
    val imageCount: Int

    /** Each image at a time of day, [dayFraction] 0–1 (0 = midnight). */
    data class ByTime(val frames: List<TimeFrame>) : DynamicSchedule {
        override val imageCount get() = frames.maxOf { it.image } + 1
    }

    /** Each image at a position of the sun (degrees: altitude above the horizon, azimuth from north). */
    data class BySun(val frames: List<SunFrame>) : DynamicSchedule {
        override val imageCount get() = frames.maxOf { it.image } + 1
    }

    data class TimeFrame(val dayFraction: Double, val image: Int)
    data class SunFrame(val altitude: Double, val azimuth: Double, val image: Int)

    /** Show [from], with [to] laid over it at opacity [blend] (0–1). */
    data class Mix(val from: Int, val to: Int, val blend: Float)

    companion object {
        /**
         * The schedule found in a file's bytes, or null if it has none. Looks for the XMP
         * property either as an attribute (h24="…") or as an element (<…:h24>…</…:h24>).
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun find(fileBytes: ByteArray): DynamicSchedule? {
            // XMP is plain text inside the file; Latin-1 maps every byte to one char.
            val text = String(fileBytes, Charsets.ISO_8859_1)
            for (kind in listOf("solar", "h24")) {
                val base64 = Regex("""apple_desktop:$kind\s*=\s*"([A-Za-z0-9+/=\s]+)"""").find(text)?.groupValues?.get(1)
                    ?: Regex("""<apple_desktop:$kind>([A-Za-z0-9+/=\s]+)</apple_desktop:$kind>""").find(text)?.groupValues?.get(1)
                    ?: continue
                val plist = runCatching {
                    BinaryPlist.parse(Base64.Mime.decode(base64.trim()))
                }.getOrNull() as? Map<*, *> ?: continue
                fromPlist(kind, plist)?.let { return it }
            }
            return null
        }

        internal fun fromPlist(kind: String, plist: Map<*, *>): DynamicSchedule? = when (kind) {
            "h24" -> (plist["ti"] as? List<*>)?.mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val t = (m["t"] as? Number)?.toDouble() ?: return@mapNotNull null
                val i = (m["i"] as? Number)?.toInt() ?: return@mapNotNull null
                TimeFrame(t, i)
            }?.takeIf { it.isNotEmpty() }?.let { ByTime(it.sortedBy(TimeFrame::dayFraction)) }
            "solar" -> (plist["si"] as? List<*>)?.mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val a = (m["a"] as? Number)?.toDouble() ?: return@mapNotNull null
                val z = (m["z"] as? Number)?.toDouble() ?: return@mapNotNull null
                val i = (m["i"] as? Number)?.toInt() ?: return@mapNotNull null
                SunFrame(a, z, i)
            }?.takeIf { it.isNotEmpty() }?.let { BySun(it) }
            else -> null
        }

        /** Compact text for the settings: "time:0.25@0,0.5@1" or "sun:-20/90@0,40/180@1". */
        fun encode(schedule: DynamicSchedule): String = when (schedule) {
            is ByTime -> "time:" + schedule.frames.joinToString(",") { "${it.dayFraction}@${it.image}" }
            is BySun -> "sun:" + schedule.frames.joinToString(",") { "${it.altitude}/${it.azimuth}@${it.image}" }
        }

        fun decode(text: String): DynamicSchedule? = runCatching {
            val (kind, body) = text.split(":", limit = 2)
            val items = body.split(",").map { it.split("@") }
            when (kind) {
                "time" -> ByTime(items.map { (t, i) -> TimeFrame(t.toDouble(), i.toInt()) })
                "sun" -> BySun(items.map { (pos, i) ->
                    val (a, z) = pos.split("/")
                    SunFrame(a.toDouble(), z.toDouble(), i.toInt())
                })
                else -> null
            }
        }.getOrNull()

        /**
         * By time: the frames around [dayFraction] on the 24-hour clock (wrapping past
         * midnight), blended by how far between them it is.
         */
        fun mix(schedule: ByTime, dayFraction: Double): Mix {
            val f = schedule.frames
            if (f.size == 1) return Mix(f[0].image, f[0].image, 0f)
            val nextIndex = f.indexOfFirst { it.dayFraction > dayFraction }.let { if (it < 0) 0 else it }
            val prev = f[(nextIndex - 1 + f.size) % f.size]
            val next = f[nextIndex]
            val span = ((next.dayFraction - prev.dayFraction) + 1.0) % 1.0
            val into = ((dayFraction - prev.dayFraction) + 1.0) % 1.0
            val blend = if (span <= 0.0) 0.0 else (into / span).coerceIn(0.0, 1.0)
            return Mix(prev.image, next.image, blend.toFloat())
        }

        /**
         * By sun: the two frames whose sun position is nearest the real one (angular distance
         * on the sky), blended towards the nearer — so the picture follows dawn, noon and dusk
         * as they really fall where the TV is, through the seasons.
         */
        fun mix(schedule: BySun, altitude: Double, azimuth: Double): Mix {
            val ranked = schedule.frames.sortedBy { angle(it.altitude, it.azimuth, altitude, azimuth) }
            val a = ranked[0]
            val b = ranked.getOrNull(1) ?: return Mix(a.image, a.image, 0f)
            val da = angle(a.altitude, a.azimuth, altitude, azimuth)
            val db = angle(b.altitude, b.azimuth, altitude, azimuth)
            // Weight of b: 0 when the sun is on a's spot, 0.5 halfway between.
            val blend = if (da + db <= 1e-9) 0.0 else da / (da + db)
            return Mix(a.image, b.image, blend.toFloat())
        }

        /** Angle in degrees between two sky positions (altitude, azimuth). */
        internal fun angle(alt1: Double, az1: Double, alt2: Double, az2: Double): Double {
            val r = Math.PI / 180
            val c = sin(alt1 * r) * sin(alt2 * r) + cos(alt1 * r) * cos(alt2 * r) * cos((az1 - az2) * r)
            return acos(c.coerceIn(-1.0, 1.0)) / r
        }
    }
}
