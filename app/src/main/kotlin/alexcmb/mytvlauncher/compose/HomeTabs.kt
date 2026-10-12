package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup
import alexcmb.mytvlauncher.source.TvSource

/** A tab of the home screen. [key] identifies it in the settings (order, hidden, startup). */
sealed interface HomeTab {
    val title: String
    val key: String
}

/** A tab backed by a list of apps (the hub is the first of these). */
data class AppsTab(
    override val title: String,
    val shortcuts: List<Shortcut>,
    override val key: String = title,
) : HomeTab

/** A tab backed by the TV's external inputs. */
data class SourcesTab(
    override val title: String,
    val sources: List<TvSource>,
    override val key: String = HomeTabs.SOURCES_KEY,
) : HomeTab

/** Turns the categories (and any TV inputs) into the home screen's tabs; kept pure to test. */
object HomeTabs {
    // Category tabs are keyed by their name; these can't clash since "#" never starts one.
    const val HOME_KEY = "#home"
    const val SOURCES_KEY = "#sources"

    /**
     * The first tab gathers every app, most used first, so the ones you actually open are one
     * press away; it is always there and always first. By default a Sources tab follows when
     * the TV has inputs, then a tab per category in the order the groups arrive. [order] (tab
     * keys) rearranges the tabs after the hub — tabs it doesn't name, like a new category, keep
     * their default place after the ordered ones — and [hidden] removes tabs.
     */
    fun from(
        groups: List<ShortcutGroup>,
        allLabel: String,
        sources: List<TvSource> = emptyList(),
        sourcesLabel: String = "",
        order: List<String> = emptyList(),
        hidden: Set<String> = emptySet(),
    ): List<HomeTab> {
        val all = groups.flatMap { it.shortcutList }
        if (all.isEmpty()) return emptyList()
        val rest = buildList {
            if (sources.isNotEmpty()) add(SourcesTab(sourcesLabel, sources))
            groups.forEach { add(AppsTab(it.category, it.shortcutList)) }
        }
        val ranked = rest.withIndex().sortedBy { (index, tab) ->
            order.indexOf(tab.key).takeIf { it >= 0 } ?: (order.size + index)
        }.map { it.value }
        return listOf(AppsTab(allLabel, all.sortedByDescending { it.openCount }, HOME_KEY)) +
            ranked.filterNot { it.key in hidden }
    }

    /**
     * Saved tab keys after the category [from] was renamed [to]: its place in the order (or
     * its hidden / startup state) carries over. Renaming onto an existing category merges
     * the two, so a duplicate key is dropped and the first place kept.
     */
    fun renameKey(keys: List<String>, from: String, to: String): List<String> =
        keys.map { if (it == from) to else it }.distinct()

    /**
     * The new [order] after moving [key] one place left (delta -1) or right (+1) among [keys],
     * the current order of the movable tabs. Moving past either end changes nothing.
     */
    fun move(keys: List<String>, key: String, delta: Int): List<String> {
        val from = keys.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in keys.indices) return keys
        return keys.toMutableList().apply { add(to, removeAt(from)) }
    }
}
