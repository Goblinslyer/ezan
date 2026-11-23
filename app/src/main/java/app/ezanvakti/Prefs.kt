package app.ezanvakti

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.time.ZoneId
import kotlin.math.abs

object Prefs {
    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("ezan", Context.MODE_PRIVATE)

    private fun defaultCity(): Int {
        val id = ZoneId.systemDefault().id
        val i = Cities.ALL.indexOfFirst { it.tz == id }
        return if (i >= 0) i else 0
    }

    fun cityIndex(c: Context): Int = sp(c).getInt("city", defaultCity())
    fun setCityIndex(c: Context, i: Int) = sp(c).edit().putInt("city", i).apply()

    fun customLat(c: Context): String = sp(c).getString("clat", "52.3676") ?: ""
    fun customLng(c: Context): String = sp(c).getString("clng", "4.9041") ?: ""
    fun customTz(c: Context): String = sp(c).getString("ctz", "Europe/Amsterdam") ?: ""
    fun setCustom(c: Context, lat: String, lng: String, tz: String) =
        sp(c).edit().putString("clat", lat).putString("clng", lng).putString("ctz", tz).apply()

    fun location(c: Context): Loc {
        val idx = cityIndex(c)
        if (idx == Cities.CUSTOM_INDEX) {
            val lat = customLat(c).trim().toDoubleOrNull()
            val lng = customLng(c).trim().toDoubleOrNull()
            val zone = try { ZoneId.of(customTz(c).trim()) } catch (e: Exception) { ZoneId.systemDefault() }
            if (lat != null && lng != null && abs(lat) <= 90 && abs(lng) <= 180) {
                return Loc("Özel konum", lat, lng, zone)
            }
        }
        val city = Cities.ALL[if (idx in Cities.ALL.indices) idx else 0]
        return Loc(city.name, city.lat, city.lng, ZoneId.of(city.tz))
    }

    /** Güneş vakti için varsayılan kapalı (ezan okunmaz). */
    fun enabled(c: Context, i: Int): Boolean = sp(c).getBoolean("on_$i", i != 1)
    fun setEnabled(c: Context, i: Int, on: Boolean) = sp(c).edit().putBoolean("on_$i", on).apply()

    fun audioPath(c: Context, i: Int): String? =
        sp(c).getString("file_$i", null)?.takeIf { File(it).exists() }

    fun audioName(c: Context, i: Int): String? =
        if (audioPath(c, i) != null) sp(c).getString("name_$i", null) else null

    fun setAudio(c: Context, i: Int, path: String, name: String) =
        sp(c).edit().putString("file_$i", path).putString("name_$i", name).apply()

    fun clearAudio(c: Context, i: Int) {
        sp(c).getString("file_$i", null)?.let { File(it).delete() }
        sp(c).edit().remove("file_$i").remove("name_$i").apply()
    }

    /** true: alarm ses kanalı (sessiz modda da duyulur), false: medya kanalı. */
    fun alarmStream(c: Context): Boolean = sp(c).getBoolean("alarm_stream", true)
    fun setAlarmStream(c: Context, on: Boolean) = sp(c).edit().putBoolean("alarm_stream", on).apply()
}
