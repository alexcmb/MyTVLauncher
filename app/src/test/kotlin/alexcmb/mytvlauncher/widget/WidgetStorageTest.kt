package alexcmb.mytvlauncher.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStorageTest {

    @Test
    fun `survives a round trip`() {
        val widgets = listOf(
            HostedWidget(12, 80),
            HostedWidget(13, 130),
        )
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `keeps the order the widgets were added in`() {
        val widgets = listOf(HostedWidget(9), HostedWidget(3), HostedWidget(7))
        assertEquals(listOf(9, 3, 7), WidgetStorage.decode(WidgetStorage.encode(widgets)).map { it.id })
    }

    @Test
    fun `reads nothing from nothing`() {
        assertEquals(emptyList<HostedWidget>(), WidgetStorage.decode(""))
        assertEquals("", WidgetStorage.encode(emptyList()))
    }

    @Test
    fun `falls back to full scale when the size is unreadable`() {
        assertEquals(listOf(HostedWidget(4, 100)), WidgetStorage.decode("4:ENORMOUS"))
        assertEquals(listOf(HostedWidget(4, 100)), WidgetStorage.decode("4"))
    }

    @Test
    fun `migrates the five legacy size names to their percents`() {
        assertEquals(
            listOf(60, 80, 100, 130, 160),
            WidgetStorage.decode("1:XSMALL,2:SMALL,3:MEDIUM,4:LARGE,5:XLARGE").map { it.scalePercent },
        )
    }

    @Test
    fun `clamps a stored percent to the slider's bounds`() {
        assertEquals(listOf(HostedWidget(4, 200)), WidgetStorage.decode("4:999"))
        assertEquals(listOf(HostedWidget(4, 50)), WidgetStorage.decode("4:5"))
    }

    @Test
    fun `drops an unreadable entry instead of the whole band`() {
        assertEquals(
            listOf(HostedWidget(4, 80), HostedWidget(6, 130)),
            WidgetStorage.decode("4:80,rubbish,6:130")
        )
    }

    @Test
    fun `round-trips the alignment`() {
        val widgets = listOf(
            HostedWidget(1, 80, WidgetAlignment.START),
            HostedWidget(2, 100, WidgetAlignment.CENTER),
            HostedWidget(3, 130, WidgetAlignment.END),
        )
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `falls back to start when the alignment is unreadable`() {
        assertEquals(
            listOf(HostedWidget(5, 130, WidgetAlignment.START)),
            WidgetStorage.decode("5:130:SIDEWAYS")
        )
    }

    @Test
    fun `round-trips every shape`() {
        val widgets = WidgetShape.entries.mapIndexed { i, shape ->
            HostedWidget(i, 100, WidgetAlignment.START, shape)
        }
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `falls back to a wide shape when the shape is unreadable`() {
        assertEquals(
            listOf(HostedWidget(7, 80, WidgetAlignment.START, WidgetShape.WIDE)),
            WidgetStorage.decode("7:80:START:TRIANGLE")
        )
    }

    @Test
    fun `round-trips the fit`() {
        val widgets = listOf(
            HostedWidget(1, 100, WidgetAlignment.START, WidgetShape.WIDE, WidgetFit.NATIVE),
            HostedWidget(2, 100, WidgetAlignment.END, WidgetShape.SQUARE, WidgetFit.FIT),
        )
        assertEquals(widgets, WidgetStorage.decode(WidgetStorage.encode(widgets)))
    }

    @Test
    fun `defaults to native fit for entries stored before it existed`() {
        assertEquals(
            listOf(HostedWidget(8, 100, WidgetAlignment.END, WidgetShape.SQUARE, WidgetFit.NATIVE)),
            WidgetStorage.decode("8:100:END:SQUARE")
        )
    }
}
