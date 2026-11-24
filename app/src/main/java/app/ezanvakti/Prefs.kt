package app.ezanvakti

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.time.ZoneId
import java.util.Locale

object Prefs {
    private val ASSET_KEYS = arrayOf("sabah", "gunes", "ogle", "ikindi", "aksam", "yatsi")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "wav", "aac", "opus")

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

    private fun normalize(s: String): String = s
        .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
        .replace('Ö', 'o').replace('ö', 'o')
        .replace('Ü', 'u').replace('ü', 'u')
        .replace('Ş', 's').replace('ş', 's')
        .replace('Ğ', 'g').replace('ğ', 'g')
        .replace('Ç', 'c').replace('ç', 'c')
        .lowercase(Locale.US)

    private fun isAudioFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.US) in AUDIO_EXTS

    /** assets/ezan/ (veya doğrudan assets/) içindeki gömülü ses dosyasını esnek şekilde bulur. */
    fun defaultAsset(c: Context, i: Int): String? {
        val dirs = listOf("ezan", "")
        for (dir in dirs) {
            val raw = try { c.assets.list(dir) } catch (e: Exception) { null } ?: continue
            val audioFiles = raw.filter { isAudioFile(it) }
            if (audioFiles.isEmpty()) continue

            val prefix = if (dir.isEmpty()) "" else "$dir/"
            val key = ASSET_KEYS.getOrNull(i)
            if (key != null) {
                val matched = audioFiles.firstOrNull {
                    normalize(it.substringBeforeLast('.')).contains(key)
                }
                if (matched != null) return "$prefix$matched"
            }
            if (i == 1) return null
            val fallback = audioFiles.firstOrNull {
                normalize(it.substringBeforeLast('.')).contains("varsayilan")
            } ?: audioFiles.firstOrNull()
            if (fallback != null) return "$prefix$fallback"
        }
        return null
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

    /** Ezan ses seviyesi (0..100). */
    fun volume(c: Context): Int = sp(c).getInt("volume", 80).coerceIn(0, 100)
    fun setVolume(c: Context, v: Int) = sp(c).edit().putInt("volume", v.coerceIn(0, 100)).apply()
}
