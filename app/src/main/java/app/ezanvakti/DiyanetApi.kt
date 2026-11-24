package app.ezanvakti

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DiyanetApi {
    private const val BASE = "https://ezanvakti.emushaf.net"
    private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    data class Item(val id: String, val name: String) {
        override fun toString(): String = name
    }

    private fun List<Item>.sortTr(): List<Item> {
        val collator = Collator.getInstance(Locale("tr", "TR"))
        return sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    private fun getJson(path: String): String {
        val conn = (URL("$BASE$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                throw RuntimeException("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun fetchCountries(): List<Item> {
        val arr = JSONArray(getJson("/ulkeler"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Item(o.getString("UlkeID"), o.getString("UlkeAdi"))
        }.sortTr()
    }

    fun fetchCities(countryId: String): List<Item> {
        val arr = JSONArray(getJson("/sehirler/$countryId"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Item(o.getString("SehirID"), o.getString("SehirAdi"))
        }.sortTr()
    }

    fun fetchDistricts(cityId: String): List<Item> {
        val arr = JSONArray(getJson("/ilceler/$cityId"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Item(o.getString("IlceID"), o.getString("IlceAdi"))
        }.sortTr()
    }

    /** Seçili ilçe için 30 günlük resmî Diyanet vakitlerini indirir ve telefona kaydeder. */
    fun fetchAndCacheTimes(ctx: Context, districtId: String): Boolean {
        return try {
            val raw = getJson("/vakitler/$districtId")
            val arr = JSONArray(raw)
            if (arr.length() > 0) {
                Prefs.setDiyanetJson(ctx, raw)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Önbellekteki Diyanet verisinden istenen günün 6 vaktini milisaniye olarak döndürür. */
    fun getTimes(ctx: Context, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LongArray? {
        val raw = Prefs.diyanetJson(ctx) ?: return null
        val target = DATE_FMT.format(date)
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("MiladiTarihKisa") == target) {
                    val keys = arrayOf("Imsak", "Gunes", "Ogle", "Ikindi", "Aksam", "Yatsi")
                    return LongArray(6) { k ->
                        val hm = o.optString(keys[k], "")
                        parseTime(date, hm, zone)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(date: LocalDate, hm: String, zone: ZoneId): Long {
        return try {
            val parts = hm.trim().split(":")
            val lt = LocalTime.of(parts[0].toInt(), parts[1].toInt())
            date.atTime(lt).atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            -1L
        }
    }

    private var lastAutoFetch = 0L

    /** Eldeki Diyanet verisinin bitmesine 3 günden az kaldıysa arka planda yeni 30 günü indirir. */
    fun refreshIfNeeded(ctx: Context, onUpdated: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastAutoFetch < 6 * 60 * 60 * 1000L) return
        val checkDate = LocalDate.now(ZoneId.systemDefault()).plusDays(3)
        if (getTimes(ctx, checkDate) != null) return

        lastAutoFetch = now
        val app = ctx.applicationContext
        Thread {
            if (fetchAndCacheTimes(app, Prefs.districtId(app))) {
                onUpdated()
            }
        }.start()
    }
}
