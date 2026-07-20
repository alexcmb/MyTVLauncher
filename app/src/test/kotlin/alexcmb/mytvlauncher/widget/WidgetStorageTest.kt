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

    @Test
    fun `round-trips the alignment`() {
        val widgets = listOf(
            HostedWidget(1, WidgetSize.SMALL, WidgetAlignment.START),
            HostedWidget(2, WidgetSize.MEDIUM, WidgetAlignment.CENTER),
            HostedWidget(3, WidgetSize.LARGE, WidgetAlignment.END),
        )
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `defaults to start alignment for entries stored before it existed`() {
        assertEquals(
            listOf(HostedWidget(4, WidgetSize.SMALL, WidgetAlignment.START)),
            WidgetStorage.decode("4:SMALL")
        )
    }

    @Test
    fun `falls back to start when the alignment is unreadable`() {
        assertEquals(
            listOf(HostedWidget(5, WidgetSize.LARGE, WidgetAlignment.START)),
            WidgetStorage.decode("5:LARGE:SIDEWAYS")
        )
    }

    @Test
    fun `round-trips every shape`() {
        val widgets = WidgetShape.entries.mapIndexed { i, shape ->
            HostedWidget(i, WidgetSize.MEDIUM, WidgetAlignment.START, shape)
        }
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `defaults to a wide shape for entries stored before it existed`() {
        assertEquals(
            listOf(HostedWidget(6, WidgetSize.MEDIUM, WidgetAlignment.END, WidgetShape.WIDE)),
            WidgetStorage.decode("6:MEDIUM:END")
        )
    }

    @Test
    fun `falls back to a wide shape when the shape is unreadable`() {
        assertEquals(
            listOf(HostedWidget(7, WidgetSize.SMALL, WidgetAlignment.START, WidgetShape.WIDE)),
            WidgetStorage.decode("7:SMALL:START:TRIANGLE")
        )
    }

    @Test
    fun `round-trips the fit`() {
        val widgets = listOf(
            HostedWidget(1, WidgetSize.MEDIUM, WidgetAlignment.START, WidgetShape.WIDE, WidgetFit.NATIVE),
            HostedWidget(2, WidgetSize.MEDIUM, WidgetAlignment.END, WidgetShape.SQUARE, WidgetFit.FIT),
        )
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `defaults to native fit for entries stored before it existed`() {
        assertEquals(
            listOf(HostedWidget(8, WidgetSize.MEDIUM, WidgetAlignment.END, WidgetShape.SQUARE, WidgetFit.NATIVE)),
            WidgetStorage.decode("8:MEDIUM:END:SQUARE")
        )
    }
}
