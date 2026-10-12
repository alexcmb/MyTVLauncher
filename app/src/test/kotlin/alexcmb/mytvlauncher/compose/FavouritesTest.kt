package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.model.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritesTest {

    private val byUsage = listOf("a", "b", "c", "d", "e").map { Shortcut(it, it, null, null) }

    private fun ids(list: List<Shortcut>) = list.map { it.id }

    @Test
    fun `without pins the row is the most used apps`() {
        assertEquals(listOf("a", "b", "c"), ids(Favourites.pick(byUsage, emptyList(), 3)))
    }

    @Test
    fun `pins come first in their order, then the most used fill the row`() {
        assertEquals(listOf("d", "b", "a"), ids(Favourites.pick(byUsage, listOf("d", "b"), 3)))
    }

    @Test
    fun `pins always show even past the row size`() {
        assertEquals(listOf("e", "d", "c"), ids(Favourites.pick(byUsage, listOf("e", "d", "c"), 2)))
    }

    @Test
    fun `pins of apps that are gone or hidden are skipped, duplicates ignored`() {
        assertEquals(listOf("c", "a"), ids(Favourites.pick(byUsage, listOf("gone", "c", "c"), 2)))
    }
}
