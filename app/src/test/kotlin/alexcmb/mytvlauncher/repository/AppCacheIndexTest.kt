package alexcmb.mytvlauncher.repository

import alexcmb.mytvlauncher.repository.AppCacheIndex.Art
import alexcmb.mytvlauncher.repository.AppCacheIndex.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCacheIndexTest {

    private val apps = listOf(
        Line("com.spotify.tv.android", "Spotify", "a@/data/app/x/base.apk@1730000000000", false, Art.BANNER),
        Line("org.example.odd", "Name\twith\ttabs\n100% odd", "b@/data/app/y/base.apk@1", true, Art.ICON),
        Line("com.android.tv.settings", "Paramètres", "c@/system/app/z.apk@0", true, Art.NONE),
    )

    @Test
    fun `the index survives a round trip, odd names included`() {
        assertEquals(apps, AppCacheIndex.decode(AppCacheIndex.encode("fr-FR", apps), "fr-FR"))
    }

    @Test
    fun `an empty list is still a cache`() {
        assertEquals(emptyList<Line>(), AppCacheIndex.decode(AppCacheIndex.encode("fr-FR", emptyList()), "fr-FR"))
    }

    @Test
    fun `names read in another language are not used`() {
        assertNull(AppCacheIndex.decode(AppCacheIndex.encode("fr-FR", apps), "en-US"))
    }

    @Test
    fun `nothing saved, or another format, gives no cache`() {
        assertNull(AppCacheIndex.decode(null, "fr-FR"))
        assertNull(AppCacheIndex.decode("", "fr-FR"))
        assertNull(AppCacheIndex.decode("0\tfr-FR\nid\ttitle\tstamp\tfalse\tBANNER", "fr-FR"))
    }

    @Test
    fun `a broken line is skipped, the rest kept`() {
        val text = AppCacheIndex.encode("fr-FR", apps.take(1)) +
            "\nonly\tthree\tfields" +
            "\nid\ttitle\tstamp\tfalse\tGIF" +
            "\n\ttitle\tstamp\tfalse\tICON"
        assertEquals(apps.take(1), AppCacheIndex.decode(text, "fr-FR"))
    }
}
