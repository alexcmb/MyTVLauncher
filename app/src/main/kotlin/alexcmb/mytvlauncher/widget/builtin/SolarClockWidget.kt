package alexcmb.mytvlauncher.widget.builtin

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.compose.glass
import alexcmb.mytvlauncher.dynamic.Cities
import alexcmb.mytvlauncher.dynamic.SunPosition
import alexcmb.mytvlauncher.repository.SettingsRepository
import alexcmb.mytvlauncher.widget.WidgetShape
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SolarEphemeris(
    val sunriseHourMin: String,
    val sunsetHourMin: String,
    val currentAltitude: Double,
    val isDay: Boolean,
    @StringRes val phaseRes: Int,
    val progress: Float, // 0f (sunrise) to 1f (sunset)
)

@Composable
fun SolarClockWidget(
    shape: WidgetShape = WidgetShape.WIDE,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.getInstance(context) }
    val cityName = remember { settings.dynamicCity() ?: Cities.DEFAULT.name }
    val city = remember(cityName) { Cities.named(cityName) }

    var now by remember { mutableStateOf(Date()) }
    var ephemeris by remember { mutableStateOf(computeSolarEphemeris(now.time, city.latitude, city.longitude)) }

    // Tick every second for the clock
    LaunchedEffect(city.name) {
        while (true) {
            delay(1000)
            val currentTime = Date()
            now = currentTime
            ephemeris = computeSolarEphemeris(currentTime.time, city.latitude, city.longitude)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE d MMMM", Locale.getDefault()) }
    val timeStr = timeFormat.format(now)
    val dateStr = dateFormat.format(now).replaceFirstChar { it.uppercase() }

    val shapeRadius = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .glass(shapeRadius, opacity = 0.5f)
            .padding(14.dp),
    ) {
        when (shape) {
            WidgetShape.SQUARE -> CompactSolarLayout(timeStr, dateStr, city.name, ephemeris)
            else -> WideSolarLayout(timeStr, dateStr, city.name, ephemeris)
        }
    }
}

@Composable
private fun WideSolarLayout(
    timeStr: String,
    dateStr: String,
    cityName: String,
    ephemeris: SolarEphemeris,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Column: Clock & Date
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = cityName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(ephemeris.phaseRes),
                        color = if (ephemeris.isDay) Color(0xFFFFD54F) else Color(0xFF90CAF9),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                text = timeStr,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
            )

            Text(
                text = dateStr,
                color = Color(0xFF9AA0B4),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(90.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.width(12.dp))

        // Right Column: Solar Arc & Sunrise/Sunset
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.widget_builtin_solar_title),
                    color = Color(0xFFD0D6E8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Alt: ${ephemeris.currentAltitude.toInt()}°",
                    color = Color(0xFF9AA0B4),
                    fontSize = 11.sp,
                )
            }

            // Visual Sun Arc
            SunArcView(
                progress = ephemeris.progress,
                isDay = ephemeris.isDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )

            // Sunrise and Sunset labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_weather_sun),
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "${stringResource(R.string.widget_solar_sunrise)} ${ephemeris.sunriseHourMin}",
                        color = Color(0xFFD0D6E8),
                        fontSize = 11.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_day_night),
                        contentDescription = null,
                        tint = Color(0xFF90CAF9),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "${stringResource(R.string.widget_solar_sunset)} ${ephemeris.sunsetHourMin}",
                        color = Color(0xFFD0D6E8),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSolarLayout(
    timeStr: String,
    dateStr: String,
    cityName: String,
    ephemeris: SolarEphemeris,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(cityName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(stringResource(ephemeris.phaseRes), color = Color(0xFFFFD54F), fontSize = 9.sp)
            }
        }

        Text(
            text = timeStr,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
        )

        SunArcView(
            progress = ephemeris.progress,
            isDay = ephemeris.isDay,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("↑ ${ephemeris.sunriseHourMin}", color = Color(0xFFFFB74D), fontSize = 10.sp)
            Text("↓ ${ephemeris.sunsetHourMin}", color = Color(0xFF90CAF9), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SunArcView(
    progress: Float,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.85f

        // Horizon line
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(0f, horizonY),
            end = Offset(w, horizonY),
            strokeWidth = 1.5f,
        )

        // Arc path
        val arcPath = Path().apply {
            moveTo(w * 0.1f, horizonY)
            quadraticBezierTo(w * 0.5f, -h * 0.1f, w * 0.9f, horizonY)
        }

        drawPath(
            path = arcPath,
            color = Color.White.copy(alpha = 0.35f),
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            ),
        )

        // Sun position along arc
        val t = progress.coerceIn(0f, 1f)
        val p0x = w * 0.1f
        val p0y = horizonY
        val p1x = w * 0.5f
        val p1y = -h * 0.1f
        val p2x = w * 0.9f
        val p2y = horizonY

        val sunX = (1 - t) * (1 - t) * p0x + 2 * (1 - t) * t * p1x + t * t * p2x
        val sunY = if (isDay) {
            (1 - t) * (1 - t) * p0y + 2 * (1 - t) * t * p1y + t * t * p2y
        } else {
            horizonY + 8f
        }

        // Sun glow & core
        val sunColor = if (isDay) Color(0xFFFFC107) else Color(0xFF90CAF9)
        drawCircle(
            color = sunColor.copy(alpha = 0.35f),
            radius = 10f,
            center = Offset(sunX, sunY),
        )
        drawCircle(
            color = sunColor,
            radius = 5f,
            center = Offset(sunX, sunY),
        )
    }
}

private fun computeSolarEphemeris(nowMillis: Long, lat: Double, lon: Double): SolarEphemeris {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val dayStart = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    var sunriseMillis = 0L
    var sunsetMillis = 0L

    // Sample across the 24 hours of today in 10-minute steps
    var prevAlt = -90.0
    for (m in 0..(24 * 60) step 10) {
        val t = dayStart + m * 60_000L
        val alt = SunPosition.at(t, lat, lon).altitude
        if (prevAlt < -0.833 && alt >= -0.833 && sunriseMillis == 0L) {
            sunriseMillis = t
        } else if (prevAlt >= -0.833 && alt < -0.833 && sunsetMillis == 0L && sunriseMillis != 0L) {
            sunsetMillis = t
        }
        prevAlt = alt
    }

    val currentSky = SunPosition.at(nowMillis, lat, lon)
    val isDay = currentSky.altitude > -0.833

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val sunriseStr = if (sunriseMillis > 0) timeFormat.format(Date(sunriseMillis)) else "06:00"
    val sunsetStr = if (sunsetMillis > 0) timeFormat.format(Date(sunsetMillis)) else "18:00"

    val progress = if (sunriseMillis > 0 && sunsetMillis > sunriseMillis) {
        if (nowMillis in sunriseMillis..sunsetMillis) {
            (nowMillis - sunriseMillis).toFloat() / (sunsetMillis - sunriseMillis).toFloat()
        } else if (nowMillis < sunriseMillis) {
            0f
        } else {
            1f
        }
    } else 0.5f

    val phase = when {
        currentSky.altitude > 0 -> R.string.phase_day
        currentSky.altitude > -6 -> R.string.phase_dusk
        currentSky.altitude > -12 -> R.string.phase_dawn
        else -> R.string.phase_night
    }

    return SolarEphemeris(
        sunriseHourMin = sunriseStr,
        sunsetHourMin = sunsetStr,
        currentAltitude = currentSky.altitude,
        isDay = isDay,
        phaseRes = phase,
        progress = progress,
    )
}
