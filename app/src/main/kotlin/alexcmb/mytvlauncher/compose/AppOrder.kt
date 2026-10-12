package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.repository.AppSort
import java.text.Collator

/** How a category grid is ordered; kept pure to test. */
object AppOrder {

    /**
     * [byUsage] (one category's apps, most used first) in the [sort] order. [manual] is the
     * user's saved order — one list for every category, since a category only cares about
     * the relative places of its own apps; an app not in it yet follows the placed ones, in
     * usage order.
     */
    fun sort(byUsage: List<Shortcut>, sort: AppSort, manual: List<String>): List<Shortcut> =
        when (sort) {
            AppSort.USAGE -> byUsage
            // Collator, not plain string order: accents and case sort the way people read
            // ("Écran" with the E's, not after "Z").
            AppSort.ALPHABETICAL -> byUsage.sortedWith(compareBy(Collator.getInstance()) { it.title })
            AppSort.MANUAL -> {
                val rank = manual.withIndex().associate { (index, id) -> id to index }
                // sortedBy is stable: unplaced apps keep their usage order among themselves.
                byUsage.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            }
        }

    /**
     * The saved order after moving [id] one place left/right within its grid, [shown] being
     * that grid's apps as currently displayed. The whole grid is written in, so apps never
     * placed before get a place too and the move does exactly what the user saw.
     */
    fun move(manual: List<String>, shown: List<String>, id: String, delta: Int): List<String> {
        val inGrid = shown.toSet()
        return manual.filterNot { it in inGrid } + HomeTabs.move(shown, id, delta)
    }
}
