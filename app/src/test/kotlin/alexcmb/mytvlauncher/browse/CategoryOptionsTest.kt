package alexcmb.mytvlauncher.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CategoryOptionsTest {

    private val defaults = listOf("Apps", "System")
    private val presets = listOf("Streaming", "Games", "Music", "Utilities")

    @Test
    fun `never offers the category the app is already in`() {
        val candidates = CategoryOptions.candidates("Games", emptyList(), defaults, presets)
        assertFalse(candidates.contains("Games"))
    }

    @Test
    fun `lists categories in use before defaults and presets`() {
        val candidates =
            CategoryOptions.candidates("Apps", listOf("Kids"), defaults, listOf("Streaming"))
        assertEquals(listOf("Kids", "System", "Streaming"), candidates)
    }

    @Test
    fun `does not repeat a preset that is already in use`() {
        val candidates =
            CategoryOptions.candidates("Apps", listOf("Games"), defaults, presets)
        assertEquals(1, candidates.count { it == "Games" })
    }

    @Test
    fun `does not repeat a default that is already in use`() {
        val candidates =
            CategoryOptions.candidates("Games", listOf("Apps", "System"), defaults, emptyList())
        assertEquals(listOf("Apps", "System"), candidates)
    }

    @Test
    fun `offers defaults and presets on a fresh install with no custom rows`() {
        val candidates =
            CategoryOptions.candidates("Apps", listOf("Apps"), defaults, presets)
        assertEquals(listOf("System", "Streaming", "Games", "Music", "Utilities"), candidates)
    }
}
