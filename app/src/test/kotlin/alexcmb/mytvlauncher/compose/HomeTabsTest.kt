package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup
import alexcmb.mytvlauncher.source.TvSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabsTest {

    private fun shortcut(id: String, openCount: Int): Shortcut =
        Shortcut(id, id, null, null).also { it.openCount = openCount }

    private fun group(category: String, vararg shortcuts: Shortcut): ShortcutGroup =
        ShortcutGroup(category, shortcuts.toMutableList()).also { group ->
            group.shortcutList.forEach { it.category = category }
        }

    private fun HomeTab.ids() = (this as AppsTab).shortcuts.map { it.id }

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
        assertEquals(listOf("steam", "netflix", "retro"), tabs.first().ids())
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
        assertEquals(listOf("netflix", "plex"), tabs[1].ids())
        assertEquals(listOf("steam"), tabs[2].ids())
    }

    @Test
    fun `inserts a sources tab right after the all tab when the tv has inputs`() {
        val tabs = HomeTabs.from(
            listOf(group("Games", shortcut("steam", 9))),
            "All",
            listOf(TvSource("hdmi1", "HDMI 1", 9)),
            "Sources",
        )
        assertEquals(listOf("All", "Sources", "Games"), tabs.map { it.title })
        assertTrue(tabs[1] is SourcesTab)
        assertEquals(listOf("hdmi1"), (tabs[1] as SourcesTab).sources.map { it.id })
    }

    @Test
    fun `omits the sources tab when there are no inputs`() {
        val tabs = HomeTabs.from(listOf(group("Games", shortcut("steam", 9))), "All", emptyList(), "Sources")
        assertEquals(listOf("All", "Games"), tabs.map { it.title })
    }
}
