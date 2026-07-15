package alexcmb.mytvlauncher.browse

import alexcmb.mytvlauncher.model.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutGroupingTest {

    private fun shortcut(id: String, category: String, openCount: Int): Shortcut =
        Shortcut(id, id, null, null).also {
            it.category = category
            it.openCount = openCount
        }

    @Test
    fun `returns no groups for no shortcuts`() {
        assertEquals(emptyList<Any>(), ShortcutGrouping.groupAndSort(emptyList()))
    }

    @Test
    fun `groups shortcuts by category`() {
        val groups = ShortcutGrouping.groupAndSort(
            listOf(
                shortcut("a", "Apps", 1),
                shortcut("b", "System", 1),
                shortcut("c", "Apps", 1),
            )
        )
        assertEquals(2, groups.size)
        assertEquals(
            setOf("Apps", "System"),
            groups.map { it.category }.toSet()
        )
        assertEquals(2, groups.first { it.category == "Apps" }.shortcutList.size)
        assertEquals(1, groups.first { it.category == "System" }.shortcutList.size)
    }

    @Test
    fun `sums the open counts of a group`() {
        val groups = ShortcutGrouping.groupAndSort(
            listOf(
                shortcut("a", "Apps", 3),
                shortcut("b", "Apps", 4),
            )
        )
        assertEquals(7, groups.single().openCount)
    }

    @Test
    fun `orders groups by total open count descending`() {
        val groups = ShortcutGrouping.groupAndSort(
            listOf(
                shortcut("rare", "Rare", 1),
                shortcut("busy", "Busy", 10),
                shortcut("mid", "Mid", 5),
            )
        )
        assertEquals(listOf("Busy", "Mid", "Rare"), groups.map { it.category })
    }

    @Test
    fun `a many small apps category can outrank a single popular app`() {
        val groups = ShortcutGrouping.groupAndSort(
            listOf(
                shortcut("one", "Popular", 9),
                shortcut("a", "Many", 4),
                shortcut("b", "Many", 4),
                shortcut("c", "Many", 4),
            )
        )
        assertEquals(listOf("Many", "Popular"), groups.map { it.category })
    }
}
