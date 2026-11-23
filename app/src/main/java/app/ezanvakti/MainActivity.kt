package app.ezanvakti

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())

    private val dark by lazy {
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    private val cBg by lazy { if (dark) 0xFF0E1A1F.toInt() else 0xFFEDF1F2.toInt() }
    private val cSurface by lazy { if (dark) 0xFF15262C.toInt() else 0xFFFFFFFF.toInt() }
    private val cInk by lazy { if (dark) 0xFFE5EEF0.toInt() else 0xFF12262B.toInt() }
    private val cMuted by lazy { if (dark) 0xFF93A9AF.toInt() else 0xFF566B71.toInt() }
    private val cLine by lazy { if (dark) 0xFF27393F.toInt() else 0xFFD3DCDE.toInt() }
    private val cAccent by lazy { if (dark) 0xFF55C8BA.toInt() else 0xFF0B6B66.toInt() }
    private val cAccentSoft by lazy { if (dark) 0xFF17383A.toInt() else 0xFFD9ECEA.toInt() }

    private val makam = listOf(
        "Sabah ezanı · geleneksel öneri: Saba makamı",
        "Ezan okunmaz · isteğe bağlı kısa uyarı sesi",
        "Öğle ezanı · geleneksel öneri: Uşşak makamı",
        "İkindi ezanı · geleneksel öneri: Rast makamı",
        "Akşam ezanı · geleneksel öneri: Segâh makamı",
        "Yatsı ezanı · geleneksel öneri: Hicaz makamı"
    )

    private val cards = ArrayList<LinearLayout>()
    private val timeTv = ArrayList<TextView>()
    private val fileTv = ArrayList<TextView>()
    private val switches = ArrayList<Switch>()

    private lateinit var citySpinner: Spinner
    private lateinit var customPanel: LinearLayout
    private lateinit var latEt: EditText
    private lateinit var lngEt: EditText
    private lateinit var tzEt: EditText
    private lateinit var nextName: TextView
    private lateinit var countdown: TextView
    private lateinit var nextMeta: TextView
    private lateinit var dateTv: TextView
    private lateinit var todayBtn: Button
    private lateinit var approxTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var exactBtn: Button

    private lateinit var loc: Loc
    private var viewDate: LocalDate? = null
    private var shownDate: LocalDate = LocalDate.now()
    private var result: PrayerCalc.Result? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            ui.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        if (!dark) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        Notifier.ensureChannels(this)
        loc = Prefs.location(this)
        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        onLocationChanged()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshTimes()
        ui.post(ticker)
    }

    override fun onPause() {
        ui.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Scheduler.scheduleAll(this)
        updateStatus()
    }

    // ---------------------------------------------------------------- arayüz

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun lp(top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val bg = GradientDrawable()
        bg.setColor(cSurface)
        bg.cornerRadius = dp(12).toFloat()
        bg.setStroke(dp(1), cLine)
        c.background = bg
        return c
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun input(hint: String, type: Int, value: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        e.setText(value)
        e.setTextColor(cInk)
        e.setHintTextColor(cMuted)
        e.setSingleLine(true)
        return e
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(cBg)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(20), dp(16), dp(32))
        scroll.addView(root)

        // Konum
        root.addView(tv("KONUM", 12f, cMuted, true), lp())
        val names = ArrayList<String>(Cities.ALL.map { it.name })
        names.add("Özel koordinat…")
        citySpinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        citySpinner.adapter = adapter
        citySpinner.setSelection(Prefs.cityIndex(this).coerceIn(0, names.size - 1), false)
        citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos == Prefs.cityIndex(this@MainActivity)) return
                Prefs.setCityIndex(this@MainActivity, pos)
                viewDate = null
                onLocationChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(citySpinner, lp(4))

        customPanel = LinearLayout(this)
        customPanel.orientation = LinearLayout.VERTICAL
        val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        latEt = input("Enlem (örn. 52.3676)", decimal, Prefs.customLat(this))
        lngEt = input("Boylam (örn. 4.9041)", decimal, Prefs.customLng(this))
        tzEt = input("Saat dilimi (örn. Europe/Amsterdam)", InputType.TYPE_CLASS_TEXT, Prefs.customTz(this))
        customPanel.addView(latEt, lp())
        customPanel.addView(lngEt, lp())
        customPanel.addView(tzEt, lp())
        customPanel.addView(button("Uygula") {
            Prefs.setCustom(this, latEt.text.toString(), lngEt.text.toString(), tzEt.text.toString())
            viewDate = null
            onLocationChanged()
        }, lp(4))
        root.addView(customPanel, lp(4))

        // Sıradaki vakit
        val nowCard = LinearLayout(this)
        nowCard.orientation = LinearLayout.VERTICAL
        nowCard.setPadding(0, dp(20), 0, dp(8))
        nowCard.addView(tv("SIRADAKİ VAKİT", 12f, cMuted, true))
        nextName = tv("—", 36f, cInk, true)
        nowCard.addView(nextName)
        countdown = tv("00:00:00", 52f, cAccent, true)
        countdown.fontFeatureSettings = "tnum"
        nowCard.addView(countdown)
        nextMeta = tv("", 14f, cMuted)
        nowCard.addView(nextMeta)
        root.addView(nowCard, lp())

        // Tarih çubuğu
        val dateBar = LinearLayout(this)
        dateBar.orientation = LinearLayout.HORIZONTAL
        dateBar.gravity = Gravity.CENTER_VERTICAL
        dateBar.addView(button("‹") { shiftDay(-1) })
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.gravity = Gravity.CENTER_HORIZONTAL
        dateTv = tv("", 16f, cInk, true)
        dateTv.gravity = Gravity.CENTER
        mid.addView(dateTv)
        todayBtn = button("Bugüne dön") { viewDate = null; refreshTimes() }
        mid.addView(todayBtn)
        dateBar.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dateBar.addView(button("›") { shiftDay(1) })
        root.addView(dateBar, lp(8))

        approxTv = tv(
            "Bu konumda yaz aylarında güneş 17°-18° açıya inmiyor; İmsak ve Yatsı gecenin oranıyla yaklaşık hesaplandı.",
            12f, cMuted
        )
        root.addView(approxTv, lp(4))

        // Altı vakit
        for (i in 0 until 6) {
            val c = card()
            val top = LinearLayout(this)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            top.addView(tv(PrayerCalc.NAMES[i], 18f, cInk, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val t = tv("--:--", 26f, cInk, true)
            t.fontFeatureSettings = "tnum"
            timeTv.add(t)
            top.addView(t)
            c.addView(top)
            c.addView(tv(makam[i], 12f, cMuted), lp(2))
            val f = tv("", 13f, cMuted)
            fileTv.add(f)
            c.addView(f, lp(6))

            val ctl = LinearLayout(this)
            ctl.orientation = LinearLayout.HORIZONTAL
            ctl.gravity = Gravity.CENTER_VERTICAL
            val sw = Switch(this)
            sw.text = "Ezan çal"
            sw.setTextColor(cInk)
            sw.isChecked = Prefs.enabled(this, i)
            sw.setOnCheckedChangeListener { _, on -> Prefs.setEnabled(this, i, on) }
            switches.add(sw)
            ctl.addView(sw, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            ctl.addView(button("Ses seç") { pickAudio(i) })
            ctl.addView(button("Dene") { preview(i) })
            c.addView(ctl, lp(4))

            cards.add(c)
            root.addView(c, lp(10))
        }

        // Ayarlar ve durum
        root.addView(tv("AYARLAR", 12f, cMuted, true), lp(24))
        val streamSw = Switch(this)
        streamSw.text = "Alarm ses kanalından çal (sessiz modda da duyulur)"
        streamSw.setTextColor(cInk)
        streamSw.isChecked = Prefs.alarmStream(this)
        streamSw.setOnCheckedChangeListener { _, on -> Prefs.setAlarmStream(this, on) }
        root.addView(streamSw, lp(8))
        root.addView(button("Çalan ezanı durdur") {
            startService(Intent(this, AdhanService::class.java).setAction(AdhanService.ACTION_STOP))
        }, lp(8))

        statusTv = tv("", 14f, cInk)
        root.addView(statusTv, lp(16))
        exactBtn = button("Kesin alarm iznini aç") {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    openAppSettings()
                }
            }
        }
        root.addView(exactBtn, lp(4))
        root.addView(button("Bildirim izni ver") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                openAppSettings()
            }
        }, lp(4))
        root.addView(button("Pil kısıtlamasını kaldır") {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                openAppSettings()
            }
        }, lp(4))

        root.addView(
            tv(
                "Samsung: Ayarlar > Pil > Arka plan kullanım sınırları > Uyku modundaki uygulamalar listesinde " +
                    "Ezan Vakti olmasın, ve \"Hiç uyumayan uygulamalar\" listesine ekleyin. Aksi halde telefon uygulamayı " +
                    "kısıtlayıp ezanı geciktirebilir.",
                12f, cMuted
            ), lp(12)
        )
        root.addView(
            tv(
                "Hesaplama: İmsak 18°, Yatsı 17°; Diyanet ihtiyat payları (Güneş -7, Öğle +5, İkindi +4, Akşam +7 dk). " +
                    "Diyanet tablolarıyla 1-2 dakika ayrılabilir; ibadet için resmî takvimi esas alın.",
                12f, cMuted
            ), lp(10)
        )
        return scroll
    }

    // ---------------------------------------------------------------- durum

    private fun onLocationChanged() {
        loc = Prefs.location(this)
        customPanel.visibility = if (Prefs.cityIndex(this) == Cities.CUSTOM_INDEX) View.VISIBLE else View.GONE
        Scheduler.scheduleAll(this)
        refreshTimes()
        tick()
    }

    private fun shiftDay(n: Int) {
        val base = viewDate ?: LocalDate.now(loc.zone)
        viewDate = base.plusDays(n.toLong())
        refreshTimes()
    }

    private fun refreshTimes() {
        loc = Prefs.location(this)
        val today = LocalDate.now(loc.zone)
        val date = viewDate ?: today
        shownDate = today
        val r = PrayerCalc.calc(date, loc.lat, loc.lng)
        result = r
        val isToday = date == today
        val now = System.currentTimeMillis()

        dateTv.text = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(Locale("tr", "TR")).format(date)
        todayBtn.visibility = if (isToday) View.GONE else View.VISIBLE
        approxTv.visibility = if (r.approx) View.VISIBLE else View.GONE

        var cur = -1
        if (isToday) {
            for (i in 0 until 6) if (r.times[i] in 1..now) cur = i
        }
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(loc.zone)
        for (i in 0 until 6) {
            timeTv[i].text = if (r.times[i] > 0) fmt.format(Instant.ofEpochMilli(r.times[i])) else "—"
            val past = isToday && i < cur
            timeTv[i].setTextColor(if (past) cMuted else cInk)
            val bg = GradientDrawable()
            bg.setColor(if (isToday && i == cur) cAccentSoft else cSurface)
            bg.cornerRadius = dp(12).toFloat()
            bg.setStroke(dp(1), cLine)
            cards[i].background = bg
            val name = Prefs.audioName(this, i)
            fileTv[i].text = if (name != null) "Ses: $name" else "Ses dosyası seçilmedi"
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (LocalDate.now(loc.zone) != shownDate && viewDate == null) refreshTimes()
        val nx = Scheduler.next(loc, now)
        val s = ((nx.timeMs - now) / 1000L).coerceAtLeast(0L)
        nextName.text = PrayerCalc.NAMES[nx.idx]
        countdown.text = String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(loc.zone)
        nextMeta.text = "${fmt.format(Instant.ofEpochMilli(nx.timeMs))} · ${loc.name}"
    }

    private fun updateStatus() {
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val exact = Scheduler.canScheduleExact(this)
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        fun mark(ok: Boolean) = if (ok) "✓" else "✗"
        statusTv.text = "${mark(notif)} Bildirim izni\n${mark(exact)} Kesin alarm izni\n${mark(battery)} Pil kısıtlaması kaldırıldı"
        exactBtn.visibility = if (exact) View.GONE else View.VISIBLE
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    // ---------------------------------------------------------------- ses

    private fun pickAudio(i: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
        try {
            startActivityForResult(intent, REQ_AUDIO + i)
        } catch (e: Exception) {
            Toast.makeText(this, "Dosya seçici açılamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun preview(i: Int) {
        if (Prefs.audioPath(this, i) == null) {
            Toast.makeText(this, "Önce bu vakit için bir ses dosyası seçin", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, AdhanService::class.java).putExtra(Scheduler.EXTRA_IDX, i))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val i = requestCode - REQ_AUDIO
        val uri = data?.data
        if (resultCode != RESULT_OK || i !in 0..5 || uri == null) return

        val display = queryName(uri) ?: "ezan"
        val ext = display.substringAfterLast('.', "mp3").take(5)
        val dest = File(filesDir, "ezan_$i.$ext")
        Thread {
            try {
                Prefs.clearAudio(this, i)
                contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                Prefs.setAudio(this, i, dest.absolutePath, display)
                runOnUiThread { refreshTimes() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Dosya kopyalanamadı", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val REQ_AUDIO = 200
    }
}
