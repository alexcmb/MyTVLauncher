package alexcmb.mytvlauncher.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingTest {

    private fun playing(
        isPlaying: Boolean = true,
        duration: Long = 200_000,
        position: Long = 60_000,
        speed: Float = 1f,
    ) = NowPlaying("t", null, null, isPlaying, duration, position, positionAtMs = 1_000, speed = speed)

    @Test
    fun `the position moves on with time while playing`() {
        assertEquals(70_000, playing().positionAt(11_000))
    }

    @Test
    fun `it follows the playback speed`() {
        assertEquals(80_000, playing(speed = 2f).positionAt(11_000))
    }

    @Test
    fun `paused, the position stays put`() {
        assertEquals(60_000, playing(isPlaying = false).positionAt(500_000))
    }

    @Test
    fun `it never runs past the end`() {
        assertEquals(200_000, playing().positionAt(10_000_000))
    }

    @Test
    fun `progress is a fraction of the duration, or nothing when it is unknown`() {
        assertEquals(0.35f, playing().progressAt(11_000)!!, 0.0001f)
        assertNull(playing(duration = 0).progressAt(11_000))
    }
}
