package crazyboyfeng.justTvLauncher.model

/** The apps of one category, kept ordered by open count, most used first. */
class ShortcutGroup(val category: String, val shortcutList: MutableList<Shortcut>) {
    /** Total open count of the group, used to order categories against each other. */
    var openCount = shortcutList.sumOf { it.openCount }
        private set

    init {
        sortByUsage()
    }

    fun add(element: Shortcut) {
        openCount += element.openCount
        shortcutList.add(element)
        sortByUsage()
    }

    // Stable, so shortcuts with equal open counts keep their insertion order.
    private fun sortByUsage() = shortcutList.sortByDescending { it.openCount }
}
