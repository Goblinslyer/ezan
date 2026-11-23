package app.ezanvakti

import java.time.ZoneId

data class City(val name: String, val lat: Double, val lng: Double, val tz: String)

data class Loc(val name: String, val lat: Double, val lng: Double, val zone: ZoneId)

object Cities {
    val ALL = listOf(
        City("İstanbul", 41.0082, 28.9784, "Europe/Istanbul"),
        City("Ankara", 39.9334, 32.8597, "Europe/Istanbul"),
        City("İzmir", 38.4237, 27.1428, "Europe/Istanbul"),
        City("Bursa", 40.1826, 29.0665, "Europe/Istanbul"),
        City("Antalya", 36.8969, 30.7133, "Europe/Istanbul"),
        City("Konya", 37.8746, 32.4932, "Europe/Istanbul"),
        City("Adana", 37.0, 35.3213, "Europe/Istanbul"),
        City("Gaziantep", 37.0662, 37.3833, "Europe/Istanbul"),
        City("Kayseri", 38.7312, 35.4787, "Europe/Istanbul"),
        City("Samsun", 41.2867, 36.33, "Europe/Istanbul"),
        City("Trabzon", 41.0027, 39.7168, "Europe/Istanbul"),
        City("Erzurum", 39.9055, 41.2658, "Europe/Istanbul"),
        City("Diyarbakır", 37.9144, 40.2306, "Europe/Istanbul"),
        City("Van", 38.4891, 43.4089, "Europe/Istanbul"),
        City("Amsterdam", 52.3676, 4.9041, "Europe/Amsterdam"),
        City("Rotterdam", 51.9244, 4.4777, "Europe/Amsterdam"),
        City("Den Haag", 52.0705, 4.3007, "Europe/Amsterdam"),
        City("Utrecht", 52.0907, 5.1214, "Europe/Amsterdam"),
        City("Brüksel", 50.8503, 4.3517, "Europe/Brussels"),
        City("Berlin", 52.52, 13.405, "Europe/Berlin"),
        City("Köln", 50.9375, 6.9603, "Europe/Berlin"),
        City("Frankfurt", 50.1109, 8.6821, "Europe/Berlin"),
        City("Paris", 48.8566, 2.3522, "Europe/Paris"),
        City("Viyana", 48.2082, 16.3738, "Europe/Vienna"),
        City("Londra", 51.5072, -0.1276, "Europe/London"),
        City("Mekke", 21.4225, 39.8262, "Asia/Riyadh")
    )

    /** Listenin sonundaki "Özel koordinat" seçeneğinin sırası. */
    val CUSTOM_INDEX get() = ALL.size
}
