package app.ezanvakti

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.time.ZoneId

object Prefs {
    private val ASSET_KEYS = arrayOf("sabah", "gunes", "ogle", "ikindi", "aksam", "yatsi")

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("ezan", Context.MODE_PRIVATE)

    fun countryId(c: Context): String = sp(c).getString("d_country", "2") ?: "2"
    fun cityId(c: Context): String = sp(c).getString("d_city", "539") ?: "539"
    fun districtId(c: Context): String = sp(c).getString("d_district", "9541") ?: "9541"
    fun districtName(c: Context): String = sp(c).getString("d_name", "İSTANBUL") ?: "İSTANBUL"

    fun setDiyanetLocation(c: Context, countryId: String, cityId: String, districtId: String, name: String) {
        sp(c).edit()
            .putString("d_country", countryId)
            .putString("d_city", cityId)
            .putString("d_district", districtId)
            .putString("d_name", name)
            .apply()
    }

    fun diyanetJson(c: Context): String? = sp(c).getString("d_json", null)
    fun setDiyanetJson(c: Context, json: String) = sp(c).edit().putString("d_json", json).apply()

    fun location(c: Context): Loc {
        return Loc(districtName(c), 41.0082, 28.9784, ZoneId.systemDefault())
    }

    /** Güneş vakti için varsayılan kapalı (ezan okunmaz). */
    fun enabled(c: Context, i: Int): Boolean = sp(c).getBoolean("on_$i", i != 1)
    fun setEnabled(c: Context, i: Int, on: Boolean) = sp(c).edit().putBoolean("on_$i", on).apply()

    fun audioPath(c: Context, i: Int): String? =
        sp(c).getString("file_$i", null)?.takeIf { File(it).exists() }

    fun audioName(c: Context, i: Int): String? =
        if (audioPath(c, i) != null) sp(c).getString("name_$i", null) else null

    /** assets/ezan/ içindeki gömülü varsayılan ses dosyasını bulur. */
    fun defaultAsset(c: Context, i: Int): String? {
        val list = try { c.assets.list("ezan") } catch (e: Exception) { null } ?: return null
        val key = ASSET_KEYS.getOrNull(i) ?: return null
        val exact = list.firstOrNull { it.substringBeforeLast('.').equals(key, ignoreCase = true) }
        if (exact != null) return "ezan/$exact"
        if (i == 1) return null
        val fallback = list.firstOrNull { it.substringBeforeLast('.').equals("varsayilan", ignoreCase = true) }
        return fallback?.let { "ezan/$it" }
    }

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
