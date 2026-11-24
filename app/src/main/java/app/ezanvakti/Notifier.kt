package app.ezanvakti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.SystemClock
import android.widget.RemoteViews
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
    const val ACTION_REFRESH = "app.ezanvakti.REFRESH_NOTIF"

    private const val ACCENT = 0xFF0B6B66.toInt()
    private const val ACCENT_TEXT = 0xFF2EB8A6.toInt()

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

    private fun onDismiss(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 3, Intent(ctx, AdhanReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Bildirim panelindeki özel tasarımlı kalıcı vakit kartı. */
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
        val base = SystemClock.elapsedRealtime() + (nx.timeMs - now).coerceAtLeast(0L)

        // Kapalı görünüm: sadece sıradaki vakit + büyük geri sayım
        val small = RemoteViews(ctx.packageName, R.layout.notif_collapsed)
        small.setTextViewText(R.id.n_next_name, names[nx.idx])
        small.setChronometerCountDown(R.id.n_timer, true)
        small.setChronometer(R.id.n_timer, base, null, true)

        // Açık görünüm: sıradaki vakit + kalan süre + 6 vaktin tablosu
        val big = RemoteViews(ctx.packageName, R.layout.notif_expanded)
        big.setTextViewText(R.id.e_next_name, "${names[nx.idx]} · ${f(nx.idx)}")
        big.setChronometerCountDown(R.id.e_timer, true)
        big.setChronometer(R.id.e_timer, base, null, true)

        val labelIds = intArrayOf(R.id.e_l0, R.id.e_l1, R.id.e_l2, R.id.e_l3, R.id.e_l4, R.id.e_l5)
        val timeIds = intArrayOf(R.id.e_t0, R.id.e_t1, R.id.e_t2, R.id.e_t3, R.id.e_t4, R.id.e_t5)
        for (i in 0 until 6) {
            big.setTextViewText(labelIds[i], if (i == nx.idx) "▸ ${names[i]}" else names[i])
            big.setTextViewText(timeIds[i], f(i))
            if (i == nx.idx) {
                big.setTextColor(labelIds[i], ACCENT_TEXT)
                big.setTextColor(timeIds[i], ACCENT_TEXT)
            }
        }

        return Notification.Builder(ctx, CH_TIMES)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${names[nx.idx]} · ${f(nx.idx)}")
            .setSubText(if (tomorrow) "${loc.name} · yarın" else loc.name)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setShowWhen(false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(ACCENT)
            .setContentIntent(openApp(ctx))
            .setDeleteIntent(onDismiss(ctx))
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
