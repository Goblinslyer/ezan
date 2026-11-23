package app.ezanvakti

import android.Manifest
import android.app.Activity
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
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

    private lateinit var countrySpinner: Spinner
    private lateinit var citySpinner: Spinner
    private lateinit var districtSpinner: Spinner
    private lateinit var sourceTv: TextView
    private lateinit var nextName: TextView
    private lateinit var countdown: TextView
    private lateinit var nextMeta: TextView
    private lateinit var dateTv: TextView
    private lateinit var todayBtn: Button
    private lateinit var statusTv: TextView
    private lateinit var exactBtn: Button

    private var countries: List<DiyanetApi.Item> = emptyList()
    private var cities: List<DiyanetApi.Item> = emptyList()
    private var districts: List<DiyanetApi.Item> = emptyList()

    private lateinit var loc: Loc
    private var viewDate: LocalDate? = null
    private var shownDate: LocalDate = LocalDate.now()

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

        loadCountries()
        refreshTimes()
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

    // ---------------------------------------------------------------- Diyanet konum yükleme

    private fun setSpinnerItems(spinner: Spinner, items: List<DiyanetApi.Item>, selectedId: String) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val idx = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        if (items.isNotEmpty()) spinner.setSelection(idx, false)
    }

    private fun loadCountries() {
        sourceTv.text = "Diyanet ülke listesi yükleniyor..."
        Thread {
            try {
                val list = DiyanetApi.fetchCountries()
                runOnUiThread {
                    countries = list
                    setSpinnerItems(countrySpinner, list, Prefs.countryId(this))
                    val sel = list.firstOrNull { it.id == Prefs.countryId(this) } ?: list.firstOrNull()
                    if (sel != null) loadCities(sel.id)
                }
            } catch (e: Exception) {
                runOnUiThread { sourceTv.text = "İnternet bağlantısı yok (kayıtlı vakitler kullanılıyor)" }
            }
        }.start()
    }

    private fun loadCities(countryId: String) {
        Thread {
            try {
                val list = DiyanetApi.fetchCities(countryId)
                runOnUiThread {
                    cities = list
                    setSpinnerItems(citySpinner, list, Prefs.cityId(this))
                    val sel = list.firstOrNull { it.id == Prefs.cityId(this) } ?: list.firstOrNull()
                    if (sel != null) loadDistricts(sel.id)
                }
            } catch (e: Exception) {
                runOnUiThread { sourceTv.text = "Şehir listesi alınamadı" }
            }
        }.start()
    }

    private fun loadDistricts(cityId: String) {
        Thread {
            try {
                val list = DiyanetApi.fetchDistricts(cityId)
                runOnUiThread {
                    districts = list
                    setSpinnerItems(districtSpinner, list, Prefs.districtId(this))
                    val sel = list.firstOrNull { it.id == Prefs.districtId(this) } ?: list.firstOrNull()
                    if (sel != null && Prefs.diyanetJson(this) == null) {
                        fetchTimesFor(sel.id, sel.name)
                    } else {
                        updateSourceLabel()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { sourceTv.text = "İlçe listesi alınamadı" }
            }
        }.start()
    }

    private fun fetchTimesFor(districtId: String, districtName: String) {
        val cId = (countrySpinner.selectedItem as? DiyanetApi.Item)?.id ?: Prefs.countryId(this)
        val sId = (citySpinner.selectedItem as? DiyanetApi.Item)?.id ?: Prefs.cityId(this)
        sourceTv.text = "Diyanet vakitleri indiriliyor ($districtName)..."
        Thread {
            val ok = DiyanetApi.fetchAndCacheTimes(this, districtId)
            runOnUiThread {
                if (ok) {
                    Prefs.setDiyanetLocation(this, cId, sId, districtId, districtName)
                    loc = Prefs.location(this)
                    viewDate = null
                    Scheduler.scheduleAll(this)
                    refreshTimes()
                    tick()
                    Toast.makeText(this, "$districtName vakitleri güncellendi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Vakitler indirilemedi, interneti kontrol edin", Toast.LENGTH_LONG).show()
                }
                updateSourceLabel()
            }
        }.start()
    }

    private fun updateSourceLabel() {
        val hasDiyanet = DiyanetApi.getTimes(this, LocalDate.now(loc.zone), loc.zone) != null
        sourceTv.text = if (hasDiyanet) {
            "Kaynak: Diyanet Resmî Takvimi (${Prefs.districtName(this)})"
        } else {
            "Diyanet verisi indirilmedi · 'Vakitleri güncelle' butonuna basın"
        }
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

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(cBg)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(20), dp(16), dp(32))
        scroll.addView(root)

        // Konum (Ülke / Şehir / İlçe)
        root.addView(tv("KONUM (DİYANET)", 12f, cMuted, true), lp())

        countrySpinner = Spinner(this)
        citySpinner = Spinner(this)
        districtSpinner = Spinner(this)

        countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val item = countries.getOrNull(pos) ?: return
                loadCities(item.id)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val item = cities.getOrNull(pos) ?: return
                loadDistricts(item.id)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        districtSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val item = districts.getOrNull(pos) ?: return
                if (item.id != Prefs.districtId(this@MainActivity) || Prefs.diyanetJson(this@MainActivity) == null) {
                    fetchTimesFor(item.id, item.name)
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        root.addView(countrySpinner, lp(4))
        root.addView(citySpinner, lp(4))
        root.addView(districtSpinner, lp(4))
        root.addView(button("Vakitleri Diyanet'ten güncelle") {
            val d = districtSpinner.selectedItem as? DiyanetApi.Item
            if (d != null) fetchTimesFor(d.id, d.name) else loadCountries()
        }, lp(4))

        sourceTv = tv("", 12f, cMuted)
        root.addView(sourceTv, lp(4))

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

        return scroll
    }

    // ---------------------------------------------------------------- durum

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
        val times = DiyanetApi.getTimes(this, date, loc.zone) ?: PrayerCalc.calc(date, loc.lat, loc.lng).times
        val isToday = date == today
        val now = System.currentTimeMillis()

        dateTv.text = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(Locale("tr", "TR")).format(date)
        todayBtn.visibility = if (isToday) View.GONE else View.VISIBLE

        var cur = -1
        if (isToday) {
            for (i in 0 until 6) if (times[i] in 1..now) cur = i
        }
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(loc.zone)
        for (i in 0 until 6) {
            timeTv[i].text = if (times[i] > 0) fmt.format(Instant.ofEpochMilli(times[i])) else "—"
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
        val nx = Scheduler.next(this, now)
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
