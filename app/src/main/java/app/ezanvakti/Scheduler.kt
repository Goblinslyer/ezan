package app.ezanvakti

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalDate

object Scheduler {
    const val EXTRA_IDX = "idx"
    const val EXTRA_TIME = "time"

    class Next(val idx: Int, val timeMs: Long, val date: LocalDate)

    /** nowMs'den sonraki ilk vakit (önce Diyanet verisine bakar, yoksa yedek hesaplamayı kullanır). */
    fun next(ctx: Context, nowMs: Long): Next {
        val loc = Prefs.location(ctx)
        val today = Instant.ofEpochMilli(nowMs).atZone(loc.zone).toLocalDate()
        for (off in 0..2) {
            val d = today.plusDays(off.toLong())
            val times = DiyanetApi.getTimes(ctx, d, loc.zone) ?: PrayerCalc.calc(d, loc.lat, loc.lng).times
            for (i in 0 until 6) {
                if (times[i] > nowMs) return Next(i, times[i], d)
            }
        }
        return Next(0, nowMs + 3_600_000L, today)
    }

    /** Bir sonraki vakit için alarmı kurar ve kalıcı bildirimi yeniler. */
    fun scheduleAll(ctx: Context) {
        val app = ctx.applicationContext
        val n = next(app, System.currentTimeMillis() + 1000L)
        val am = app.getSystemService(AlarmManager::class.java)
        val intent = Intent(app, AdhanReceiver::class.java)
            .putExtra(EXTRA_IDX, n.idx)
            .putExtra(EXTRA_TIME, n.timeMs)
        val pi = PendingIntent.getBroadcast(
            app, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.timeMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.timeMs, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.timeMs, pi)
        }
        Notifier.updateTimes(app)
    }

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
}
