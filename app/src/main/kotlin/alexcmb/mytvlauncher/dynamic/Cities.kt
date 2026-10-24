package alexcmb.mytvlauncher.dynamic

/**
 * Places to choose from for sun-driven wallpapers: the sun's path depends on where the TV is,
 * and a TV knows neither its position nor (without asking a service) its town. A fixed list
 * keeps it offline; a nearby city is plenty — a degree or two moves sunrise by minutes.
 */
data class City(val name: String, val latitude: Double, val longitude: Double)

object Cities {
    val ALL = listOf(
        City("Paris", 48.86, 2.35),
        City("Lille", 50.63, 3.06),
        City("Strasbourg", 48.57, 7.75),
        City("Rennes", 48.11, -1.68),
        City("Brest", 48.39, -4.49),
        City("Nantes", 47.22, -1.55),
        City("Tours", 47.39, 0.69),
        City("Dijon", 47.32, 5.04),
        City("Lyon", 45.76, 4.84),
        City("Clermont-Ferrand", 45.78, 3.08),
        City("Bordeaux", 44.84, -0.58),
        City("Toulouse", 43.60, 1.44),
        City("Montpellier", 43.61, 3.88),
        City("Marseille", 43.30, 5.37),
        City("Nice", 43.70, 7.27),
        City("Ajaccio", 41.92, 8.74),
        City("Bruxelles", 50.85, 4.35),
        City("Genève", 46.20, 6.14),
        City("Luxembourg", 49.61, 6.13),
        City("Montréal", 45.50, -73.57),
        City("Londres", 51.51, -0.13),
        City("Madrid", 40.42, -3.70),
        City("Rome", 41.90, 12.50),
        City("Berlin", 52.52, 13.40),
        City("New York", 40.71, -74.01),
        City("Tokyo", 35.68, 139.69),
    )

    val DEFAULT = ALL.first()

    fun named(name: String?): City = ALL.firstOrNull { it.name == name } ?: DEFAULT
}
