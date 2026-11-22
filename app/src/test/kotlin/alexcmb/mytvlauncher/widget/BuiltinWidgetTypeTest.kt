package alexcmb.mytvlauncher.widget

import alexcmb.mytvlauncher.widget.builtin.BuiltinWidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinWidgetTypeTest {

    @Test
    fun `identifies builtin widget ids correctly`() {
        assertTrue(BuiltinWidgetType.isBuiltin(-101))
        assertTrue(BuiltinWidgetType.isBuiltin(-102))
        assertTrue(BuiltinWidgetType.isBuiltin(-103))
        assertEquals(false, BuiltinWidgetType.isBuiltin(1))
        assertEquals(false, BuiltinWidgetType.isBuiltin(-1))
    }

    @Test
    fun `resolves type from id`() {
        assertEquals(BuiltinWidgetType.WEATHER, BuiltinWidgetType.fromId(-101))
        assertEquals(BuiltinWidgetType.SYSTEM, BuiltinWidgetType.fromId(-102))
        assertEquals(BuiltinWidgetType.SOLAR, BuiltinWidgetType.fromId(-103))
        assertEquals(null, BuiltinWidgetType.fromId(42))
    }

    @Test
    fun `builtin widgets survive storage encode and decode`() {
        val builtinWidgets = listOf(
            HostedWidget(BuiltinWidgetType.WEATHER.id, 100, WidgetAlignment.START, WidgetShape.WIDE),
            HostedWidget(BuiltinWidgetType.SYSTEM.id, 90, WidgetAlignment.CENTER, WidgetShape.PANORAMIC),
            HostedWidget(BuiltinWidgetType.SOLAR.id, 110, WidgetAlignment.END, WidgetShape.STANDARD),
        )
        val encoded = WidgetStorage.encode(builtinWidgets)
        val decoded = WidgetStorage.decode(encoded)
        assertEquals(builtinWidgets, decoded)
        decoded.forEach {
            assertNotNull(BuiltinWidgetType.fromId(it.id))
        }
    }
}
