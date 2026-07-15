package alexcmb.mytvlauncher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutGroupTest {

    private fun shortcut(id: String, openCount: Int): Shortcut =
        Shortcut(id, id, null, null).also {
            it.category = "Apps"
            it.openCount = openCount
        }

    /** Builds a group the way ShortcutGrouping does: seed with one, then add the rest. */
    private fun groupOf(vararg openCounts: Int): ShortcutGroup =
        ShortcutGroup("Apps", mutableListOf(shortcut("seed", openCounts.first()))).also { group ->
            openCounts.drop(1).forEachIndexed { i, count -> group.add(shortcut("s$i", count)) }
        }

    private fun ShortcutGroup.counts(): List<Int> = shortcutList.map { it.openCount }

    @Test
    fun `keeps a lone shortcut`() {
        assertEquals(listOf(7), groupOf(7).counts())
    }

    @Test
    fun `puts a more used app first`() {
        val group = groupOf(5)
        group.add(shortcut("new", 9))
        assertEquals(listOf(9, 5), group.counts())
    }

    @Test
    fun `puts a less used app last`() {
        val group = groupOf(5)
        group.add(shortcut("new", 1))
        assertEquals(listOf(5, 1), group.counts())
    }

    @Test
    fun `puts a middling app in the middle`() {
        val group = groupOf(10, 8, 6, 4)
        group.add(shortcut("new", 9))
        assertEquals(listOf(10, 9, 8, 6, 4), group.counts())
    }

    @Test
    fun `stays sorted whatever the insertion order`() {
        assertEquals(listOf(9, 7, 5, 3, 1), groupOf(5, 1, 9, 3, 7).counts())
    }

    @Test
    fun `handles equal open counts`() {
        assertEquals(listOf(4, 4, 4), groupOf(4, 4, 4).counts())
    }

    @Test
    fun `handles never opened apps`() {
        assertEquals(listOf(2, 0, 0), groupOf(0, 2, 0).counts())
    }

    @Test
    fun `totals the open counts of its shortcuts`() {
        assertEquals(15, groupOf(5, 4, 6).openCount)
    }
}
