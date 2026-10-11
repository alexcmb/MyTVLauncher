package alexcmb.mytvlauncher.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPlacementTest {

    @Test
    fun `successive adds fill start then centre then end`() {
        val taken = mutableListOf<WidgetAlignment>()
        val order = List(3) {
            val next = nextFreeAlignment(taken)
            taken += next
            next
        }
        assertEquals(
            listOf(WidgetAlignment.START, WidgetAlignment.CENTER, WidgetAlignment.END),
            order,
        )
    }

    @Test
    fun `fills the gap a removed widget leaves`() {
        // START and END are hosted; the freed middle zone is filled next.
        assertEquals(
            WidgetAlignment.CENTER,
            nextFreeAlignment(listOf(WidgetAlignment.START, WidgetAlignment.END)),
        )
    }

    @Test
    fun `falls back to start when every zone is taken`() {
        assertEquals(WidgetAlignment.START, nextFreeAlignment(WidgetAlignment.entries))
    }
}
