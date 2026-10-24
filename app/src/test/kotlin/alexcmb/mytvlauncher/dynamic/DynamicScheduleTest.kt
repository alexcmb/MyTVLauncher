package alexcmb.mytvlauncher.dynamic

import alexcmb.mytvlauncher.dynamic.DynamicSchedule.ByTime
import alexcmb.mytvlauncher.dynamic.DynamicSchedule.BySun
import alexcmb.mytvlauncher.dynamic.DynamicSchedule.SunFrame
import alexcmb.mytvlauncher.dynamic.DynamicSchedule.TimeFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Calendar
import java.util.TimeZone

class DynamicScheduleTest {

    // --- a tiny binary plist writer, enough to build schedules like Apple's ---------------

    private fun plist(root: Any): ByteArray {
        val objects = mutableListOf<Any>()
        fun flatten(o: Any): Int {
            val index = objects.size
            objects += o
            when (o) {
                is Map<*, *> -> o.forEach { (k, v) -> flatten(k!!); flatten(v!!) }
                is List<*> -> o.forEach { flatten(it!!) }
            }
            return index
        }
        flatten(root)
        // Recompute child indices in the same order flatten visited them.
        val out = ByteArrayOutputStream().apply { write("bplist00".toByteArray()) }
        val offsets = IntArray(objects.size)
        var cursor = 0
        fun childIndices(o: Any): List<Int> {
            val list = mutableListOf<Int>()
            fun walk(x: Any): Int {
                cursor++
                val me = cursor - 1
                when (x) {
                    is Map<*, *> -> x.forEach { (k, v) -> walk(k!!); walk(v!!) }
                    is List<*> -> x.forEach { walk(it!!) }
                }
                return me
            }
            cursor = objects.indexOf(o) + 1
            when (o) {
                is Map<*, *> -> {
                    val keys = mutableListOf<Int>(); val values = mutableListOf<Int>()
                    o.forEach { (k, v) -> keys += walk(k!!); values += walk(v!!) }
                    list += keys; list += values
                }
                is List<*> -> o.forEach { list += walk(it!!) }
            }
            return list
        }
        objects.forEachIndexed { i, o ->
            offsets[i] = out.size()
            when (o) {
                is Map<*, *> -> { out.write(0xD0 or o.size); childIndices(o).forEach { out.write(it) } }
                is List<*> -> { out.write(0xA0 or o.size); childIndices(o).forEach { out.write(it) } }
                is String -> { out.write(0x50 or o.length); out.write(o.toByteArray()) }
                is Int -> { out.write(0x10); out.write(o) }
                is Double -> {
                    out.write(0x23)
                    val bits = java.lang.Double.doubleToLongBits(o)
                    for (s in 56 downTo 0 step 8) out.write(((bits shr s) and 0xFF).toInt())
                }
                else -> error("unsupported $o")
            }
        }
        val table = out.size()
        offsets.forEach { out.write((it shr 8) and 0xFF); out.write(it and 0xFF) }
        val trailer = ByteArray(32)
        trailer[6] = 2 // offset size
        trailer[7] = 1 // ref size
        fun putLong(at: Int, v: Long) { for (k in 0 until 8) trailer[at + k] = ((v shr (56 - 8 * k)) and 0xFF).toByte() }
        putLong(8, objects.size.toLong()); putLong(16, 0); putLong(24, table.toLong())
        out.write(trailer)
        return out.toByteArray()
    }

    private fun xmp(kind: String, root: Any) =
        ("<x:xmpmeta><rdf:Description apple_desktop:$kind=\"" +
            Base64.getEncoder().encodeToString(plist(root)) + "\"/></x:xmpmeta>").toByteArray()

    // --- parsing ---------------------------------------------------------------------------

    @Test
    fun `reads a by-time schedule from the XMP and sorts it`() {
        val root = mapOf("ti" to listOf(mapOf("i" to 1, "t" to 0.75), mapOf("i" to 0, "t" to 0.25)))
        val schedule = DynamicSchedule.find(byteArrayOf(1, 2, 3) + xmp("h24", root) + byteArrayOf(9))
        assertEquals(ByTime(listOf(TimeFrame(0.25, 0), TimeFrame(0.75, 1))), schedule)
    }

    @Test
    fun `reads a by-sun schedule`() {
        val root = mapOf("si" to listOf(mapOf("a" to -20.0, "z" to 0.0, "i" to 0), mapOf("a" to 40.0, "z" to 180.0, "i" to 1)))
        val schedule = DynamicSchedule.find(xmp("solar", root)) as BySun
        assertEquals(2, schedule.imageCount)
        assertEquals(SunFrame(40.0, 180.0, 1), schedule.frames[1])
    }

    @Test
    fun `a file without a schedule gives nothing`() {
        assertNull(DynamicSchedule.find("just an ordinary image".toByteArray()))
    }

    @Test
    fun `reads real dynamic HEIC files when some are at hand`() {
        // Local check only: wallpaper/ is git-ignored, so CI has none and skips this.
        val files = File("../wallpaper").listFiles { f -> f.extension.equals("heic", true) }.orEmpty()
        assumeTrue(files.isNotEmpty())
        files.forEach { f ->
            val schedule = DynamicSchedule.find(f.readBytes())
            assertTrue("${f.name}: no schedule", schedule != null)
            println("${f.name}: $schedule")
        }
    }

    @Test
    fun `a schedule survives being saved as text`() {
        val time = ByTime(listOf(TimeFrame(0.0, 1), TimeFrame(0.354, 0)))
        val sun = BySun(listOf(SunFrame(-20.5, 90.0, 0), SunFrame(41.0, 180.25, 1)))
        assertEquals(time, DynamicSchedule.decode(DynamicSchedule.encode(time)))
        assertEquals(sun, DynamicSchedule.decode(DynamicSchedule.encode(sun)))
        assertNull(DynamicSchedule.decode("garbage"))
    }

    // --- choosing the images -----------------------------------------------------------------

    private val day = ByTime(listOf(TimeFrame(0.25, 0), TimeFrame(0.5, 1), TimeFrame(0.875, 2)))

    @Test
    fun `by time it blends between the frames around now`() {
        val mix = DynamicSchedule.mix(day, 0.375) // 9:00, halfway from 6:00 to 12:00
        assertEquals(0, mix.from); assertEquals(1, mix.to); assertEquals(0.5f, mix.blend, 0.001f)
    }

    @Test
    fun `by time it wraps past midnight`() {
        val mix = DynamicSchedule.mix(day, 0.0625) // 1:30, halfway through the 21:00 to 6:00 night
        assertEquals(2, mix.from); assertEquals(0, mix.to); assertEquals(0.5f, mix.blend, 0.001f)
    }

    @Test
    fun `by sun it picks the two nearest positions, leaning to the nearer`() {
        val sun = BySun(listOf(SunFrame(-30.0, 0.0, 0), SunFrame(10.0, 100.0, 1), SunFrame(60.0, 180.0, 2)))
        val mix = DynamicSchedule.mix(sun, 50.0, 175.0)
        assertEquals(2, mix.from); assertEquals(1, mix.to)
        assertTrue(mix.blend < 0.5f)
    }

    // --- the sun ----------------------------------------------------------------------------------

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis

    @Test
    fun `sun at noon in Nantes at the equinox is due south, about 43 degrees up`() {
        // Nantes 47.22N 1.55W: solar noon at the March equinox is about 12:13 UTC.
        val sky = SunPosition.at(utc(2026, 3, 20, 12, 13), 47.22, -1.55)
        assertEquals(42.8, sky.altitude, 1.5)
        assertEquals(180.0, sky.azimuth, 3.0)
    }

    @Test
    fun `sun is well below the horizon at midnight in December`() {
        val sky = SunPosition.at(utc(2026, 12, 21, 0, 0), 47.22, -1.55)
        assertTrue(sky.altitude < -50)
    }
}
