package alexcmb.mytvlauncher.widget.builtin

import alexcmb.mytvlauncher.widget.WidgetShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BuiltinWidgetHost(
    type: BuiltinWidgetType,
    shape: WidgetShape = WidgetShape.WIDE,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (type) {
            BuiltinWidgetType.WEATHER -> WeatherWidget(shape = shape)
            BuiltinWidgetType.SYSTEM -> SystemMonitorWidget(shape = shape)
            BuiltinWidgetType.SOLAR -> SolarClockWidget(shape = shape)
        }
    }
}
