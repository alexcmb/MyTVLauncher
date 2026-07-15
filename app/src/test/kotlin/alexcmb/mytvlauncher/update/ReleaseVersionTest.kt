package alexcmb.mytvlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun `maps a dotted version to a code`() {
        assertEquals(20260714L, ReleaseVersion.codeFromName("2026.7.14"))
    }

    @Test
    fun `strips a leading v so raw tag names work`() {
        assertEquals(20260714L, ReleaseVersion.codeFromName("v2026.7.14"))
    }

    @Test
    fun `treats missing parts as zero`() {
        assertEquals(20260700L, ReleaseVersion.codeFromName("2026.7"))
        assertEquals(20260000L, ReleaseVersion.codeFromName("2026"))
    }

    @Test
    fun `returns zero for a non numeric tag rather than throwing`() {
        assertEquals(0L, ReleaseVersion.codeFromName("nightly"))
        assertEquals(0L, ReleaseVersion.codeFromName(""))
    }

    @Test
    fun `orders a newer release above an older one`() {
        assertTrue(
            ReleaseVersion.codeFromName("2026.7.15") > ReleaseVersion.codeFromName("2026.7.14")
        )
        assertTrue(
            ReleaseVersion.codeFromName("2026.8.1") > ReleaseVersion.codeFromName("2026.7.31")
        )
        assertTrue(
            ReleaseVersion.codeFromName("2027.1.1") > ReleaseVersion.codeFromName("2026.12.31")
        )
    }

    @Test
    fun `beats the version code shipped by a local build`() {
        // Local builds default to 20210913; any tagged release must win.
        assertTrue(ReleaseVersion.codeFromName("2026.7.14") > 20210913L)
    }
}
