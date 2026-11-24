package app.ezanvakti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Vakit girince alarm buraya düşer: ezanı başlatır ve bir sonraki alarmı kurar. */
class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Notifier.ACTION_REFRESH) {
            val pending = goAsync()
            Handler(Looper.getMainLooper()).postDelayed({
                Notifier.updateTimes(context.applicationContext)
                pending.finish()
            }, 400L)
            return
        }
        val idx = intent.getIntExtra(Scheduler.EXTRA_IDX, -1)
        val planned = intent.getLongExtra(Scheduler.EXTRA_TIME, 0L)
        val fresh = planned == 0L || System.currentTimeMillis() - planned < 5 * 60 * 1000L
        if (idx in 0..5 && fresh && Prefs.enabled(context, idx)) {
            try {
                context.startForegroundService(
                    Intent(context, AdhanService::class.java).putExtra(Scheduler.EXTRA_IDX, idx)
                )
            } catch (e: Exception) {
                Notifier.showAlert(context, idx)
            }
        }
        Scheduler.scheduleAll(context)
    }
}

/** Telefon yeniden başlayınca, uygulama güncellenince veya saat/saat dilimi değişince alarmları yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleAll(context)
    }
}
