package alexcmb.mytvlauncher.dynamic

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the sun is in the sky, from a time and a place — the NOAA solar position equations
 * (accurate to about a degree, plenty for picking a wallpaper). Offline: no service is asked.
 */
object SunPosition {

    data class Sky(val altitude: Double, val azimuth: Double)

    /** The sun at [utcMillis] seen from [latitude], [longitude] (degrees, east positive). */
    fun at(utcMillis: Long, latitude: Double, longitude: Double): Sky {
        val r = Math.PI / 180
        // Julian century from J2000.0.
        val jd = utcMillis / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(m * r) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m * r) * (0.019993 - 0.000101 * t) + sin(3 * m * r) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(omega * r)
        val obliq = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0 +
            0.00256 * cos(omega * r)
        val decl = asin(sin(obliq * r) * sin(lambda * r))
        val y = tan(obliq * r / 2).let { it * it }
        val eqTime = 4 / r * (y * sin(2 * l0 * r) - 2 * e * sin(m * r) +
            4 * e * y * sin(m * r) * cos(2 * l0 * r) - 0.5 * y * y * sin(4 * l0 * r) -
            1.25 * e * e * sin(2 * m * r)) // minutes
        val minutesUtc = (utcMillis.mod(86_400_000L)) / 60_000.0
        val trueSolarTime = (minutesUtc + eqTime + 4 * longitude).mod(1440.0)
        val hourAngle = (trueSolarTime / 4 - 180) * r
        val lat = latitude * r
        val altitude = asin(sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(hourAngle))
        val azimuth = atan2(sin(hourAngle), cos(hourAngle) * sin(lat) - tan(decl) * cos(lat))
        return Sky(altitude / r, (azimuth / r + 180).mod(360.0))
    }
}
