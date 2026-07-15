package alexcmb.mytvlauncher.browse

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup

/** Grouping/sorting logic, kept free of Android types so it can be unit-tested. */
object ShortcutGrouping {
    /** Groups shortcuts by category; groups come back ordered by total open count, descending. */
    fun groupAndSort(shortcuts: Collection<Shortcut>): List<ShortcutGroup> {
        val groupByCategory = HashMap<String, ShortcutGroup>()
        shortcuts.forEach { shortcut ->
            val group = groupByCategory[shortcut.category]
            if (group != null) {
                group.add(shortcut)
            } else {
                groupByCategory[shortcut.category] =
                    ShortcutGroup(shortcut.category, mutableListOf(shortcut))
            }
        }
        return groupByCategory.values.sortedByDescending { it.openCount }
    }
}
