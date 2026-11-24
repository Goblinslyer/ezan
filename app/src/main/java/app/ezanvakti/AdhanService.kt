package app.ezanvakti

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.io.File

/** Ezanı ön plan servisi olarak çalar, böylece uygulama kapalıyken de ses devam eder. */
class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finish() }
    private var screenReceiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                finish()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val idx = (intent?.getIntExtra(Scheduler.EXTRA_IDX, 0) ?: 0).coerceIn(0, 5)
        Notifier.ensureChannels(this)

        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat), "Durdur", stopPi
        ).build()
        val note = Notification.Builder(this, Notifier.CH_ADHAN)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${PrayerCalc.NAMES[idx]} ezanı")
            .setContentText("Çalıyor")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .addAction(stopAction)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifier.ID_ADHAN, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifier.ID_ADHAN, note)
        }
        registerScreenOff()
        startPlayback(idx)
        return START_NOT_STICKY
    }

    private fun registerScreenOff() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenOff() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            // yok say
        }
        screenReceiverRegistered = false
    }

    private fun startPlayback(idx: Int) {
        releasePlayer()
        handler.removeCallbacks(timeout)
        val attrs = AudioAttributes.Builder()
            .setUsage(if (Prefs.alarmStream(this)) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val am = getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
        focus = req
        am.requestAudioFocus(req)

        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            val path = Prefs.audioPath(this, idx)
            val asset = Prefs.defaultAsset(this, idx)
            when {
                path != null -> mp.setDataSource(path)
                asset != null -> {
                    try {
                        assets.openFd(asset).use { afd ->
                            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                    } catch (e: Exception) {
                        val tmp = File(cacheDir, "def_$idx")
                        assets.open(asset).use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                        mp.setDataSource(tmp.absolutePath)
                    }
                }
                else -> mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepareAsync()
            player = mp
            handler.postDelayed(timeout, 15 * 60 * 1000L)
        } catch (e: Exception) {
            finish()
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (e: Exception) {
            // hazır değilse yok say
        }
        player?.release()
        player = null
    }

    private fun finish() {
        handler.removeCallbacks(timeout)
        unregisterScreenOff()
        releasePlayer()
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        Notifier.updateTimes(this)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        unregisterScreenOff()
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.ezanvakti.STOP"
    }
}
