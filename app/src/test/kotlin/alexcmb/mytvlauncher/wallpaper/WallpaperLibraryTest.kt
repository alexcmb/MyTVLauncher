package alexcmb.mytvlauncher.wallpaper

import alexcmb.mytvlauncher.dynamic.DynamicSchedule
import alexcmb.mytvlauncher.dynamic.DynamicSchedule.ByTime
import alexcmb.mytvlauncher.dynamic.DynamicSchedule.BySun
import alexcmb.mytvlauncher.dynamic.DynamicSchedule.SunFrame
import alexcmb.mytvlauncher.dynamic.DynamicSchedule.TimeFrame
import alexcmb.mytvlauncher.wallpaper.WallpaperLibrary.SunPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperLibraryTest {

    @Test
    fun `the library survives a round trip, odd names included`() {
        val library = listOf(
            Wallpaper("a1", WallpaperKind.VIDEO, "Plage\tde nuit\n100% été", uri = "content://x/video%2F12"),
            Wallpaper("b2", WallpaperKind.DYNAMIC, "Tokyo Street", schedule = "time:0.0@1,0.75@2"),
            Wallpaper("c3", WallpaperKind.STILL, "", uri = "content://x/image/3"),
        )
        assertEquals(library, WallpaperLibrary.decode(WallpaperLibrary.encode(library)))
    }

    @Test
    fun `nothing stored, or a broken line, gives no wallpaper`() {
        assertEquals(emptyList<Wallpaper>(), WallpaperLibrary.decode(null))
        assertEquals(emptyList<Wallpaper>(), WallpaperLibrary.decode(""))
        assertEquals(emptyList<Wallpaper>(), WallpaperLibrary.decode("only\tthree\tfields"))
        assertEquals(emptyList<Wallpaper>(), WallpaperLibrary.decode("id\tGIF\tname\t\t"))
    }

    @Test
    fun `a file name becomes a card title`() {
        assertEquals("Tokyo Street", WallpaperLibrary.displayName("Tokyo Street.heic"))
        assertEquals("ma video", WallpaperLibrary.displayName("ma_video.mp4"))
        assertNull(WallpaperLibrary.displayName(".mp4"))
        assertNull(WallpaperLibrary.displayName(null))
    }

    @Test
    fun `a four-picture day is shown morning to night`() {
        // Tokyo Street: night from midnight, day from 7:00, dusk at 18:00, evening at 20:00.
        val tokyo = ByTime(
            listOf(TimeFrame(0.0, 1), TimeFrame(7.0 / 24, 0), TimeFrame(0.75, 2), TimeFrame(20.0 / 24, 3))
        )
        val strip = WallpaperLibrary.previews(tokyo)
        assertEquals(listOf(0, 2, 3, 1), strip.map { it.image })
        assertEquals(listOf(420, 1080, 1200, 0), strip.map { it.minutes })
    }

    @Test
    fun `a long day is sampled down to four pictures across it`() {
        // 16 pictures, one every 90 minutes from midnight.
        val day = ByTime(List(16) { TimeFrame(it * 1.5 / 24, it) })
        val strip = WallpaperLibrary.previews(day)
        assertEquals(4, strip.size)
        // From the first picture after 4 a.m. to the last one before it.
        assertEquals(270, strip.first().minutes)
        assertEquals(180, strip.last().minutes)
        assertEquals(strip.size, strip.map { it.image }.distinct().size)
    }

    @Test
    fun `a sun file shows dawn, day, dusk and night`() {
        val sun = BySun(
            listOf(
                SunFrame(-50.0, 0.0, 0), // night
                SunFrame(2.0, 95.0, 1), // sunrise
                SunFrame(55.0, 175.0, 2), // noon
                SunFrame(3.0, 265.0, 3), // sunset
            )
        )
        val strip = WallpaperLibrary.previews(sun)
        assertEquals(listOf(1, 2, 3, 0), strip.map { it.image })
        assertEquals(listOf(SunPhase.DAWN, SunPhase.DAY, SunPhase.DUSK, SunPhase.NIGHT), strip.map { it.phase })
    }

    @Test
    fun `a picture shows once in the strip`() {
        val two: DynamicSchedule = ByTime(listOf(TimeFrame(0.3, 0), TimeFrame(0.8, 1), TimeFrame(0.9, 0)))
        val strip = WallpaperLibrary.previews(two)
        assertEquals(listOf(0, 1), strip.map { it.image })
        assertTrue(strip.all { it.phase == null })
    }
}
