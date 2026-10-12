package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut

/** The hub's favourites row; kept pure to test. */
object Favourites {
    /**
     * The apps the user pinned, in their order, then the most used of the rest until the row
     * holds [fill] cards. Pins always show, even past [fill]; a pin whose app is gone (removed,
     * hidden) is skipped. [byUsage] is every visible app, most used first.
     */
    fun pick(byUsage: List<Shortcut>, pinned: List<String>, fill: Int): List<Shortcut> {
        val byId = byUsage.associateBy { it.id }
        val pins = pinned.distinct().mapNotNull { byId[it] }
        val pinnedIds = pins.mapTo(HashSet()) { it.id }
        val rest = byUsage.filterNot { it.id in pinnedIds }.take((fill - pins.size).coerceAtLeast(0))
        return pins + rest
    }
}
