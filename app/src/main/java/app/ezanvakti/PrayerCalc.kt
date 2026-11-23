package app.ezanvakti

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Diyanet yöntemi: İmsak 18°, Yatsı 17°, Diyanet ihtiyat payları
 * (Güneş -7, Öğle +5, İkindi +4, Akşam +7 dakika). İkindi için gölge katsayısı 1.
 */
object PrayerCalc {
    val NAMES = listOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")
    private val ADJ_MIN = intArrayOf(0, -7, 5, 4, 7, 0)
    private const val FAJR = 18.0
    private const val ISHA = 17.0
    private const val RISE = 0.833

    /** times: UTC milisaniye, hesaplanamayan vakit için -1. approx: yüksek enlem yaklaşığı kullanıldı. */
    class Result(val times: LongArray, val approx: Boolean)

    private class Sun(val decl: Double, val eqt: Double)

    private fun rad(d: Double) = Math.toRadians(d)
    private fun dsin(d: Double) = sin(rad(d))
    private fun dcos(d: Double) = cos(rad(d))
    private fun dtan(d: Double) = tan(rad(d))
    private fun dacos(x: Double) = Math.toDegrees(acos(x))
    private fun dasin(x: Double) = Math.toDegrees(asin(x))
    private fun datan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun dacot(x: Double) = Math.toDegrees(atan(1.0 / x))
    private fun fix(a: Double, b: Double) = a - b * floor(a / b)

    private fun sunPos(jd: Double): Sun {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(datan2(dcos(e) * dsin(l), dcos(l)) / 15.0, 24.0)
        var eqt = q / 15.0 - ra
        if (eqt > 12) eqt -= 24.0
        if (eqt < -12) eqt += 24.0
        return Sun(dasin(dsin(e) * dsin(l)), eqt)
    }

    fun calc(date: LocalDate, lat: Double, lng: Double): Result {
        val epochDay = date.toEpochDay()
        val jd0 = epochDay.toDouble() + 2440587.5
        val g = 12.0 - lng / 15.0

        fun ev(ccw: Boolean, angleFn: (Sun) -> Double): Double {
            var t = g
            for (i in 0 until 4) {
                val p = sunPos(jd0 + t / 24.0)
                val tr = 12.0 - lng / 15.0 - p.eqt
                val a = angleFn(p)
                val c = (-dsin(a) - dsin(p.decl) * dsin(lat)) / (dcos(p.decl) * dcos(lat))
                if (!(c >= -1.0 && c <= 1.0)) return Double.NaN
                val h = dacos(c) / 15.0
                t = if (ccw) tr - h else tr + h
            }
            return t
        }

        val noon = 12.0 - lng / 15.0 - sunPos(jd0 + g / 24.0).eqt
        var fajr = ev(true) { FAJR }
        val sunrise = ev(true) { RISE }
        val asr = ev(false) { p -> -dacot(1.0 + dtan(abs(lat - p.decl))) }
        val sunset = ev(false) { RISE }
        var isha = ev(false) { ISHA }

        var approx = false
        if (!sunrise.isNaN() && !sunset.isNaN()) {
            val night = sunrise + 24.0 - sunset
            if (fajr.isNaN()) { fajr = sunrise - night * FAJR / 60.0; approx = true }
            if (isha.isNaN()) { isha = sunset + night * ISHA / 60.0; approx = true }
        }

        val hrs = doubleArrayOf(fajr, sunrise, noon, asr, sunset, isha)
        val base = epochDay * 86_400_000L
        val out = LongArray(6) { i ->
            val h = hrs[i]
            if (h.isNaN()) {
                -1L
            } else {
                val ms = base.toDouble() + h * 3_600_000.0 + ADJ_MIN[i] * 60_000.0
                Math.round(ms / 60_000.0) * 60_000L
            }
        }
        return Result(out, approx)
    }
}
