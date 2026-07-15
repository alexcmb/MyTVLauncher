package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup

/** A tab of the home screen and the apps behind it. */
data class HomeTab(val title: String, val shortcuts: List<Shortcut>)

/** Turns the categories into the home screen's tabs; kept pure so it can be tested. */
object HomeTabs {
    /**
     * The first tab gathers every app, most used first, so the ones you actually open are
     * one press away; the rest is a tab per category, in the order the groups arrive.
     */
    fun from(groups: List<ShortcutGroup>, allLabel: String): List<HomeTab> {
        val all = groups.flatMap { it.shortcutList }
        if (all.isEmpty()) return emptyList()
        return buildList {
            add(HomeTab(allLabel, all.sortedByDescending { it.openCount }))
            groups.forEach { add(HomeTab(it.category, it.shortcutList)) }
        }
    }
}
