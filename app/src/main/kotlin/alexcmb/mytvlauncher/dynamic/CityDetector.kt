package alexcmb.mytvlauncher.dynamic

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Provides IP-based geolocation and city search via free public endpoints without API keys.
 */
object CityDetector {

    /**
     * Attempts to detect the user's location based on their public IP address.
     * Tries freeipapi.com first, then ipapi.co as a fallback.
     */
    fun detectByIp(): City? {
        // Primary: freeipapi.com
        try {
            val url = URL("https://freeipapi.com/api/json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "MyTVLauncher/1.0")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val cityName = json.optString("cityName", "").trim()
                val lat = json.optDouble("latitude", Double.NaN)
                val lon = json.optDouble("longitude", Double.NaN)
                if (cityName.isNotEmpty() && !lat.isNaN() && !lon.isNaN()) {
                    return City(cityName, lat, lon)
                }
            }
        } catch (_: Exception) {}

        // Fallback: ipapi.co
        try {
            val url = URL("https://ipapi.co/json/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "MyTVLauncher/1.0")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val cityName = json.optString("city", "").trim()
                val lat = json.optDouble("latitude", Double.NaN)
                val lon = json.optDouble("longitude", Double.NaN)
                if (cityName.isNotEmpty() && !lat.isNaN() && !lon.isNaN()) {
                    return City(cityName, lat, lon)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Searches cities/towns by name using Open-Meteo Geocoding API (free, no API key).
     */
    fun searchCity(query: String): List<City> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=fr")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "MyTVLauncher/1.0")
            }
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<City>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val name = item.optString("name", "").trim()
                val admin1 = item.optString("admin1", "").trim()
                val country = item.optString("country", "").trim()
                val lat = item.optDouble("latitude", Double.NaN)
                val lon = item.optDouble("longitude", Double.NaN)
                if (name.isNotEmpty() && !lat.isNaN() && !lon.isNaN()) {
                    val label = when {
                        admin1.isNotEmpty() && !admin1.equals(name, ignoreCase = true) -> "$name ($admin1)"
                        country.isNotEmpty() -> "$name ($country)"
                        else -> name
                    }
                    list.add(City(name = label, latitude = lat, longitude = lon))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
