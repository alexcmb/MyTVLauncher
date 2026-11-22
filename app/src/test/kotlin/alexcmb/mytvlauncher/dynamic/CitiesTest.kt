package alexcmb.mytvlauncher.dynamic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CitiesTest {

    @Test
    fun defaultCityIsParis() {
        assertEquals("Paris", Cities.DEFAULT.name)
    }

    @Test
    fun namedFindsPresetCityCaseInsensitively() {
        val city = Cities.named("lyon")
        assertEquals("Lyon", city.name)
        assertEquals(45.76, city.latitude, 0.01)
        assertEquals(4.84, city.longitude, 0.01)
    }

    @Test
    fun namedReturnsDefaultWhenUnknownAndNoFallback() {
        val city = Cities.named("UnknownCity")
        assertEquals(Cities.DEFAULT, city)
    }

    @Test
    fun namedReturnsCustomCoordinatesWhenProvided() {
        val custom = Cities.named("Besse-et-Saint-Anastaise", 45.51, 2.93)
        assertEquals("Besse-et-Saint-Anastaise", custom.name)
        assertEquals(45.51, custom.latitude, 0.01)
        assertEquals(2.93, custom.longitude, 0.01)
    }

    @Test
    fun namedHandlesNullSafely() {
        val city = Cities.named(null)
        assertEquals(Cities.DEFAULT, city)
    }
}
