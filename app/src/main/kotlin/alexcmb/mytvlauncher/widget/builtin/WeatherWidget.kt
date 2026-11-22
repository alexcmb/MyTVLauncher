package alexcmb.mytvlauncher.widget.builtin

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.compose.glass
import alexcmb.mytvlauncher.dynamic.City
import alexcmb.mytvlauncher.dynamic.Cities
import alexcmb.mytvlauncher.repository.SettingsRepository
import alexcmb.mytvlauncher.widget.WidgetShape
import android.content.Context
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WeatherDay(
    val dayName: String,
    val weatherCode: Int,
    val minTemp: Int,
    val maxTemp: Int,
)

data class WeatherData(
    val city: String,
    val currentTemp: Int,
    val weatherCode: Int,
    val minTemp: Int,
    val maxTemp: Int,
    val humidity: Int,
    val windSpeed: Int,
    val forecast: List<WeatherDay>,
)

@Composable
fun WeatherWidget(
    shape: WidgetShape = WidgetShape.WIDE,
    city: City = Cities.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var weather by remember(city.name) { mutableStateOf(getCachedOrFallbackWeather(context, city.name)) }

    LaunchedEffect(city) {
        val fetched = fetchWeather(context, city.name, city.latitude, city.longitude)
        if (fetched != null) {
            weather = fetched
        }
    }

    val shapeRadius = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .glass(shapeRadius, opacity = 0.5f)
            .padding(14.dp),
    ) {
        when (shape) {
            WidgetShape.SQUARE -> CompactWeatherLayout(weather)
            else -> WideWeatherLayout(weather, showForecast = shape != WidgetShape.STANDARD)
        }
    }
}

@Composable
private fun WideWeatherLayout(weather: WeatherData, showForecast: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Column: Current weather & conditions
        Column(
            modifier = Modifier
                .weight(if (showForecast) 1.15f else 1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // City & Condition header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = weather.city,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(weatherConditionLabel(weather.weatherCode)),
                        color = Color(0xFFD0D6E8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Big temperature + Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(weatherIcon(weather.weatherCode)),
                    contentDescription = null,
                    tint = weatherTint(weather.weatherCode),
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = "${weather.currentTemp}°",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Light,
                )
                Column(modifier = Modifier.padding(start = 2.dp)) {
                    Text(
                        text = "↑ ${weather.maxTemp}°",
                        color = Color(0xFFFF9E80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "↓ ${weather.minTemp}°",
                        color = Color(0xFF80D8FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${stringResource(R.string.widget_weather_humidity)} ${weather.humidity}%",
                    color = Color(0xFF9AA0B4),
                    fontSize = 11.sp,
                )
                Text(
                    text = "${stringResource(R.string.widget_weather_wind)} ${weather.windSpeed} km/h",
                    color = Color(0xFF9AA0B4),
                    fontSize = 11.sp,
                )
            }
        }

        // Right side: 3-day forecast
        if (showForecast && weather.forecast.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(90.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                weather.forecast.take(3).forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = day.dayName,
                            color = Color(0xFFD0D6E8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(55.dp),
                        )
                        Icon(
                            painter = painterResource(weatherIcon(day.weatherCode)),
                            contentDescription = null,
                            tint = weatherTint(day.weatherCode),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "${day.minTemp}° / ${day.maxTemp}°",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactWeatherLayout(weather: WeatherData) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = weather.city,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Icon(
            painter = painterResource(weatherIcon(weather.weatherCode)),
            contentDescription = null,
            tint = weatherTint(weather.weatherCode),
            modifier = Modifier.size(34.dp),
        )

        Text(
            text = "${weather.currentTemp}°",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
        )

        Text(
            text = "${weather.minTemp}° / ${weather.maxTemp}°",
            color = Color(0xFFD0D6E8),
            fontSize = 11.sp,
        )
    }
}

private fun weatherIcon(code: Int): Int = when (code) {
    0, 1 -> R.drawable.ic_weather_sun
    2, 3 -> R.drawable.ic_weather_cloud
    45, 48 -> R.drawable.ic_weather_cloud
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> R.drawable.ic_weather_rain
    71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
    95, 96, 99 -> R.drawable.ic_weather_rain
    else -> R.drawable.ic_weather_sun
}

private fun weatherTint(code: Int): Color = when (code) {
    0, 1 -> Color(0xFFFFC107)
    2, 3 -> Color(0xFFB0BEC5)
    45, 48 -> Color(0xFF90A4AE)
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFF4FC3F7)
    71, 73, 75, 77, 85, 86 -> Color(0xFFE1F5FE)
    95, 96, 99 -> Color(0xFFFFB74D)
    else -> Color(0xFFFFC107)
}

private fun weatherConditionLabel(code: Int): Int = when (code) {
    0, 1 -> R.string.widget_weather_condition_clear
    2, 3 -> R.string.widget_weather_condition_partly_cloudy
    45, 48 -> R.string.widget_weather_condition_fog
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> R.string.widget_weather_condition_rain
    71, 73, 75, 77, 85, 86 -> R.string.widget_weather_condition_snow
    95, 96, 99 -> R.string.widget_weather_condition_thunder
    else -> R.string.widget_weather_condition_clear
}

private const val PREFS_NAME = "builtin_weather_cache"

private fun getCachedOrFallbackWeather(context: Context, cityName: String): WeatherData {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val cached = prefs.getString("data_$cityName", null)
    if (cached != null) {
        try {
            val json = JSONObject(cached)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val days = mutableListOf<WeatherDay>()
            val dayTimes = daily.getJSONArray("time")
            val dayCodes = daily.getJSONArray("weather_code")
            val dayMax = daily.getJSONArray("temperature_2m_max")
            val dayMin = daily.getJSONArray("temperature_2m_min")
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

            for (i in 1 until minOf(dayTimes.length(), 4)) {
                val date = sdf.parse(dayTimes.getString(i))
                val dName = if (date != null) dayFormat.format(date).replaceFirstChar { it.uppercase() } else "J+$i"
                days.add(
                    WeatherDay(
                        dayName = dName,
                        weatherCode = dayCodes.getInt(i),
                        minTemp = dayMin.getDouble(i).toInt(),
                        maxTemp = dayMax.getDouble(i).toInt(),
                    )
                )
            }

            return WeatherData(
                city = cityName,
                currentTemp = current.getDouble("temperature_2m").toInt(),
                weatherCode = current.getInt("weather_code"),
                minTemp = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt(),
                maxTemp = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt(),
                humidity = current.optInt("relative_humidity_2m", 60),
                windSpeed = current.optDouble("wind_speed_10m", 12.0).toInt(),
                forecast = days,
            )
        } catch (_: Exception) {}
    }

    // Realistic fallback based on seasonal date
    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH)
    val isSummer = month in 5..8
    val temp = if (isSummer) 24 else 15
    return WeatherData(
        city = cityName,
        currentTemp = temp,
        weatherCode = 1,
        minTemp = temp - 5,
        maxTemp = temp + 4,
        humidity = 55,
        windSpeed = 14,
        forecast = listOf(
            WeatherDay("Dem.", 1, temp - 4, temp + 3),
            WeatherDay("J+2", 2, temp - 3, temp + 2),
            WeatherDay("J+3", 61, temp - 5, temp + 1),
        ),
    )
}

private suspend fun fetchWeather(
    context: Context,
    cityName: String,
    lat: Double,
    lon: Double,
): WeatherData? = withContext(Dispatchers.IO) {
    try {
        val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
        }
        if (connection.responseCode == 200) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("data_$cityName", responseText)
                .apply()
            return@withContext getCachedOrFallbackWeather(context, cityName)
        }
    } catch (_: Exception) {}
    null
}
