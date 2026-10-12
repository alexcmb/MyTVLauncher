package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.repository.AppSort
import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {

    private fun app(id: String, title: String = id) = Shortcut(id, title, null, null)

    private fun ids(list: List<Shortcut>) = list.map { it.id }

    // Most used first, as the groups arrive.
    private val byUsage = listOf(app("vlc", "VLC"), app("ecran", "Écran"), app("arte", "arte"), app("zen", "Zen"))

    @Test
    fun `usage keeps the most used first`() {
        assertEquals(listOf("vlc", "ecran", "arte", "zen"), ids(AppOrder.sort(byUsage, AppSort.USAGE, emptyList())))
    }

    @Test
    fun `alphabetical ignores case and accents`() {
        assertEquals(
            listOf("arte", "ecran", "vlc", "zen"),
            ids(AppOrder.sort(byUsage, AppSort.ALPHABETICAL, emptyList())),
        )
    }

    @Test
    fun `manual follows the saved order, unplaced apps after it by usage`() {
        assertEquals(
            listOf("zen", "arte", "vlc", "ecran"),
            ids(AppOrder.sort(byUsage, AppSort.MANUAL, listOf("zen", "gone", "arte"))),
        )
    }

    @Test
    fun `moving writes the whole grid in and leaves other categories alone`() {
        val manual = listOf("other1", "zen", "other2")
        val shown = listOf("zen", "vlc", "ecran")
        assertEquals(
            listOf("other1", "other2", "vlc", "zen", "ecran"),
            AppOrder.move(manual, shown, "zen", +1),
        )
        // Past the edge: nothing moves, but the grid is still recorded as shown.
        assertEquals(listOf("other1", "other2", "zen", "vlc", "ecran"), AppOrder.move(manual, shown, "zen", -1))
    }
}
