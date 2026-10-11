package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup
import alexcmb.mytvlauncher.source.TvSource

/** A tab of the home screen. */
sealed interface HomeTab {
    val title: String
}

/** A tab backed by a list of apps (the hub is the first of these). */
data class AppsTab(override val title: String, val shortcuts: List<Shortcut>) : HomeTab

/** A tab backed by the TV's external inputs. */
data class SourcesTab(override val title: String, val sources: List<TvSource>) : HomeTab

/** Turns the categories (and any TV inputs) into the home screen's tabs; kept pure to test. */
object HomeTabs {
    /**
     * The first tab gathers every app, most used first, so the ones you actually open are one
     * press away; a Sources tab follows when the TV has inputs; then a tab per category, in the
     * order the groups arrive.
     */
    fun from(
        groups: List<ShortcutGroup>,
        allLabel: String,
        sources: List<TvSource> = emptyList(),
        sourcesLabel: String = "",
    ): List<HomeTab> {
        val all = groups.flatMap { it.shortcutList }
        if (all.isEmpty()) return emptyList()
        return buildList {
            add(AppsTab(allLabel, all.sortedByDescending { it.openCount }))
            if (sources.isNotEmpty()) add(SourcesTab(sourcesLabel, sources))
            groups.forEach { add(AppsTab(it.category, it.shortcutList)) }
        }
    }
}
