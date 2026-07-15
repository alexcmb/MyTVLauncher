package alexcmb.mytvlauncher.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStorageTest {

    @Test
    fun `survives a round trip`() {
        val widgets = listOf(
            HostedWidget(12, WidgetSize.SMALL),
            HostedWidget(13, WidgetSize.LARGE),
        )
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `keeps the order the widgets were added in`() {
        val widgets = listOf(
            HostedWidget(9, WidgetSize.MEDIUM),
            HostedWidget(3, WidgetSize.MEDIUM),
            HostedWidget(7, WidgetSize.MEDIUM),
        )
        assertEquals(listOf(9, 3, 7), WidgetStorage.decode(WidgetStorage.encode(widgets)).map { it.id })
    }

    @Test
    fun `reads nothing from nothing`() {
        assertEquals(emptyList<HostedWidget>(), WidgetStorage.decode(""))
        assertEquals("", WidgetStorage.encode(emptyList()))
    }

    @Test
    fun `falls back to a medium widget when the size is unreadable`() {
        assertEquals(
            listOf(HostedWidget(4, WidgetSize.MEDIUM)),
            WidgetStorage.decode("4:ENORMOUS")
        )
        assertEquals(listOf(HostedWidget(4, WidgetSize.MEDIUM)), WidgetStorage.decode("4"))
    }

    @Test
    fun `drops an unreadable entry instead of the whole band`() {
        assertEquals(
            listOf(HostedWidget(4, WidgetSize.SMALL), HostedWidget(6, WidgetSize.LARGE)),
            WidgetStorage.decode("4:SMALL,rubbish,6:LARGE")
        )
    }
}
