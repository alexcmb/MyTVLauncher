package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabsTest {

    private fun shortcut(id: String, openCount: Int): Shortcut =
        Shortcut(id, id, null, null).also { it.openCount = openCount }

    private fun group(category: String, vararg shortcuts: Shortcut): ShortcutGroup =
        ShortcutGroup(category, shortcuts.toMutableList()).also { group ->
            group.shortcutList.forEach { it.category = category }
        }

    @Test
    fun `shows no tabs at all when there are no apps`() {
        assertEquals(emptyList<HomeTab>(), HomeTabs.from(emptyList(), "All"))
    }

    @Test
    fun `opens with an all tab, then one per category in order`() {
        val tabs = HomeTabs.from(
            listOf(
                group("Streaming", shortcut("netflix", 9)),
                group("Games", shortcut("steam", 2)),
            ),
            "All",
        )
        assertEquals(listOf("All", "Streaming", "Games"), tabs.map { it.title })
    }

    @Test
    fun `the all tab gathers every app, most used first`() {
        val tabs = HomeTabs.from(
            listOf(
                group("Streaming", shortcut("netflix", 4)),
                group("Games", shortcut("steam", 9), shortcut("retro", 1)),
            ),
            "All",
        )
        assertEquals(listOf("steam", "netflix", "retro"), tabs.first().shortcuts.map { it.id })
    }

    @Test
    fun `a category tab keeps only its own apps`() {
        val tabs = HomeTabs.from(
            listOf(
                group("Streaming", shortcut("netflix", 4), shortcut("plex", 2)),
                group("Games", shortcut("steam", 9)),
            ),
            "All",
        )
        assertEquals(listOf("netflix", "plex"), tabs[1].shortcuts.map { it.id })
        assertEquals(listOf("steam"), tabs[2].shortcuts.map { it.id })
    }
}
