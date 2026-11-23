package app.ezanvakti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Notifier {
    const val CH_TIMES = "times"
    const val CH_ADHAN = "adhan"
    const val CH_ALERT = "alert"
    const val ID_TIMES = 1
    const val ID_ADHAN = 2
    const val ID_ALERT = 3

    private const val ACCENT = 0xFF0B6B66.toInt()

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val times = NotificationChannel(CH_TIMES, "Namaz vakitleri", NotificationManager.IMPORTANCE_LOW)
        times.description = "Seçili şehrin günlük vakitleri ve sıradaki vakte geri sayım"
        times.setShowBadge(false)
        nm.createNotificationChannel(times)

        val adhan = NotificationChannel(CH_ADHAN, "Ezan çalıyor", NotificationManager.IMPORTANCE_LOW)
        adhan.setSound(null, null)
        nm.createNotificationChannel(adhan)

        val alert = NotificationChannel(CH_ALERT, "Vakit uyarısı", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(alert)
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Bildirim panelindeki kalıcı vakit kartı. */
    fun buildTimes(ctx: Context): Notification {
        ensureChannels(ctx)
        val loc = Prefs.location(ctx)
        val now = System.currentTimeMillis()
      val nx = Scheduler.next(ctx, now)
        val times = DiyanetApi.getTimes(ctx, nx.date, loc.zone) ?: PrayerCalc.calc(nx.date, loc.lat, loc.lng).times
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(loc.zone)
        fun f(i: Int): String = if (times[i] > 0) fmt.format(Instant.ofEpochMilli(times[i])) else "—"
        val names = PrayerCalc.NAMES
        val tomorrow = nx.date.isAfter(LocalDate.now(loc.zone))
        val title = "Sıradaki: ${names[nx.idx]} ${f(nx.idx)}"
        val compact = (0 until 6).joinToString(" · ") { "${names[it]} ${f(it)}" }
        val big = StringBuilder()
        for (i in 0 until 6) {
            big.append(if (i == nx.idx) "▶ " else "   ").append(names[i]).append("   ").append(f(i))
            if (i < 5) big.append('\n')
        }

        return Notification.Builder(ctx, CH_TIMES)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(compact)
            .setSubText(if (tomorrow) "${loc.name} · yarın" else loc.name)
            .setStyle(Notification.BigTextStyle().bigText(big.toString()))
            .setWhen(nx.timeMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(ACCENT)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun updateTimes(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.notify(ID_TIMES, buildTimes(ctx))
        } catch (e: Exception) {
            // Bildirim izni yoksa sessizce geç.
        }
    }

    /** Ezan servisi başlatılamazsa en azından sesli bir uyarı göster. */
    fun showAlert(ctx: Context, idx: Int) {
        try {
            ensureChannels(ctx)
            val n = Notification.Builder(ctx, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("${PrayerCalc.NAMES[idx]} vakti girdi")
                .setContentText("Ezan çalınamadı. Uygulamayı açıp pil ve bildirim ayarlarını kontrol edin.")
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx))
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
        } catch (e: Exception) {
            // yok say
        }
    }
}
