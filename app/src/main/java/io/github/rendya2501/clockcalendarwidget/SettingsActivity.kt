package io.github.rendya2501.clockcalendarwidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.text.Collator
import java.time.ZoneId

/**
 * ウィジェットを長押しして開く設定画面(Android 12以降の「再設定」)。
 * 都市(タイムゾーン) / 表示(テーマ・デジタル時計・都市コード・秒針・週の始まり・左右の並び・背景の濃さ) / タップしたときに開くアプリ を設定する。
 */
class SettingsActivity : Activity() {

    private companion object {
        const val STATE_ZONE = "state_zone"
        const val STATE_THEME = "state_theme"
    }

    private data class AppEntry(val label: String, val packageName: String)

    /** 都市の選択肢。id が null のものは「端末の設定に従う」 */
    private data class ZoneItem(val id: String?, val title: String, val searchKey: String)

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var appliedTheme = ThemeMode.SYSTEM   // この画面にいま適用しているテーマ

    private var selectedZoneId: String? = null   // null = 端末のタイムゾーンに従う
    private var zoneItems: List<ZoneItem> = emptyList()
    private var apps: List<AppEntry> = emptyList()

    private lateinit var zoneRow: TextView
    private lateinit var radioTheme: RadioGroup
    private lateinit var switchDigital: Switch
    private lateinit var switchAlarm: Switch
    private lateinit var switchLabel: Switch
    private lateinit var switchSecondHand: Switch
    private lateinit var switchMondayStart: Switch
    private lateinit var switchSwap: Switch
    private lateinit var backgroundLabel: TextView
    private lateinit var seekBackground: SeekBar
    private lateinit var spinnerClock: Spinner
    private lateinit var spinnerCalendar: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 設定画面の色も、ウィジェットのテーマ(ダーク/ライト/端末に従う)に合わせる。
        // テーマはウィンドウができる前に決める必要があるので、super.onCreate の前に適用する。
        // 画面を作り直した直後(テーマ切り替え・画面回転)は、選びかけの値を引き継ぐ。
        val savedTheme = savedInstanceState?.getString(STATE_THEME)
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
        appliedTheme = savedTheme
            ?: if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetPrefs.load(this, appWidgetId).theme
            else ThemeMode.SYSTEM
        setTheme(
            when (appliedTheme) {
                ThemeMode.DARK -> R.style.Theme_ClockCalendarWidget_Settings_Dark
                ThemeMode.LIGHT -> R.style.Theme_ClockCalendarWidget_Settings_Light
                ThemeMode.SYSTEM -> R.style.Theme_ClockCalendarWidget_Settings
            }
        )
        super.onCreate(savedInstanceState)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // 保存せずに戻ったときは「キャンセル」として返す
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContentView(R.layout.activity_settings)
        applyInsets()

        zoneRow = requireViewById<TextView>(R.id.zone_row)
        radioTheme = requireViewById<RadioGroup>(R.id.radio_theme)
        switchDigital = requireViewById<Switch>(R.id.switch_digital)
        switchAlarm = requireViewById<Switch>(R.id.switch_alarm)
        switchLabel = requireViewById<Switch>(R.id.switch_label)
        switchSecondHand = requireViewById<Switch>(R.id.switch_second_hand)
        switchMondayStart = requireViewById<Switch>(R.id.switch_monday_start)
        switchSwap = requireViewById<Switch>(R.id.switch_swap)
        backgroundLabel = requireViewById<TextView>(R.id.background_label)
        seekBackground = requireViewById<SeekBar>(R.id.seek_background)
        spinnerClock = requireViewById<Spinner>(R.id.spinner_clock)
        spinnerCalendar = requireViewById<Spinner>(R.id.spinner_calendar)

        zoneItems = buildZoneItems()
        apps = loadApps()

        val config = WidgetPrefs.load(this, appWidgetId)

        // ---- 都市(タイムゾーン) ----
        selectedZoneId = if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_ZONE)
        } else {
            config.timeZoneId?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
        }
        updateZoneRow()
        zoneRow.setOnClickListener { showZonePicker() }

        // ---- 表示 ----
        radioTheme.check(
            when (config.theme) {
                ThemeMode.DARK -> R.id.radio_theme_dark
                ThemeMode.LIGHT -> R.id.radio_theme_light
                ThemeMode.SYSTEM -> R.id.radio_theme_system
            }
        )
        // 作り直した直後は、Androidが選択状態を戻し終えてから見張りを始める(途中の状態で反応しないように)
        if (savedInstanceState == null) watchThemeChange()
        switchDigital.isChecked = config.showDigital
        switchAlarm.isChecked = config.showAlarm
        switchLabel.isChecked = config.showLabel
        switchSecondHand.isChecked = config.showSecondHand
        switchMondayStart.isChecked = config.mondayStart
        switchSwap.isChecked = config.swapPositions
        seekBackground.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
                updateBackgroundLabel(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        seekBackground.progress = config.backgroundPercent
        updateBackgroundLabel(config.backgroundPercent)

        // ---- タップしたときに開くアプリ ----
        setupAppSpinner(spinnerClock, getString(R.string.app_default_clock), config.clockApp)
        setupAppSpinner(spinnerCalendar, getString(R.string.app_default_calendar), config.calendarApp)

        requireViewById<Button>(R.id.button_save).setOnClickListener { save() }
    }

    /** テーマの選択が、いまの画面のテーマと違うものになったら、画面を作り直して色を変える */
    private fun watchThemeChange() {
        radioTheme.setOnCheckedChangeListener { _, _ ->
            if (selectedTheme() != appliedTheme) recreate()
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        watchThemeChange()
    }

    /** ラジオボタンで選ばれているテーマ */
    private fun selectedTheme(): ThemeMode = when (radioTheme.checkedRadioButtonId) {
        R.id.radio_theme_light -> ThemeMode.LIGHT
        R.id.radio_theme_system -> ThemeMode.SYSTEM
        else -> ThemeMode.DARK
    }

    // 画面が作り直されても、保存前の選択(都市・テーマ)が消えないようにする。
    // スイッチやスライダーなど、idのある部品の状態はAndroidが自動で戻す。
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ZONE, selectedZoneId)
        outState.putString(STATE_THEME, selectedTheme().name)
    }

    /** ステータスバー・ナビゲーションバー・キーボードに隠れないように余白を足す */
    private fun applyInsets() {
        val root = requireViewById<View>(R.id.settings_root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    // ---------------------------------------------------------------- 都市(タイムゾーン)

    /** 先頭に「端末の設定に従う」、その後ろに都市名順の「東京 (TYO)」を並べる */
    private fun buildZoneItems(): List<ZoneItem> {
        val collator = Collator.getInstance()
        val cities = ZoneCatalog.ids().map { id ->
            val zone = ZoneId.of(id)
            ZoneItem(
                id = id,
                title = ZoneLabels.title(zone),
                // 端末の言語の都市名・英語の都市名・都市コード・IDのどれでも検索できる
                searchKey = "${ZoneLabels.displayName(id)} ${ZoneLabels.cityName(id)} " +
                        "${ZoneLabels.codeFor(zone)} $id".lowercase(),
            )
        }.sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) })
        return listOf(ZoneItem(null, getString(R.string.follow_system_zone), "")) + cities
    }

    /** 設定画面の「東京 (TYO) ▼」の行 */
    private fun updateZoneRow() {
        val id = selectedZoneId
        zoneRow.text = if (id == null) {
            getString(R.string.zone_row_system, ZoneLabels.title(ZoneId.systemDefault()))
        } else {
            ZoneLabels.title(ZoneId.of(id))
        }
    }

    /** 検索欄つきの都市一覧を出す。選ぶとすぐ閉じる */
    private fun showZonePicker() {
        val view = layoutInflater.inflate(R.layout.dialog_zone_picker, null)
        val search = view.requireViewById<EditText>(R.id.picker_search)
        val list = view.requireViewById<ListView>(R.id.picker_list)
        list.choiceMode = ListView.CHOICE_MODE_SINGLE

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        var shown: List<ZoneItem> = zoneItems

        fun render(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) zoneItems else zoneItems.filter { it.id != null && it.searchKey.contains(q) }
            list.adapter = ArrayAdapter(
                this, android.R.layout.simple_list_item_checked, shown.map { it.title }
            )
            val checked = shown.indexOfFirst { it.id == selectedZoneId }
            if (checked >= 0) {
                list.setItemChecked(checked, true)
                list.setSelection(checked)   // 選択中の都市が見える位置までスクロール
            }
        }

        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = render(s?.toString().orEmpty())
        })
        list.setOnItemClickListener { _, _, position, _ ->
            selectedZoneId = shown[position].id
            updateZoneRow()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateBackgroundLabel(percent: Int) {
        backgroundLabel.text = getString(R.string.background_opacity, percent)
    }

    // ---------------------------------------------------------------- アプリ

    /** ホーム画面に出せる(ランチャーから起動できる)アプリの一覧 */
    @Suppress("DEPRECATION")
    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun setupAppSpinner(spinner: Spinner, defaultLabel: String, selectedPackage: String?) {
        val labels = listOf(defaultLabel) + apps.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val index = apps.indexOfFirst { it.packageName == selectedPackage }
        spinner.setSelection(if (index >= 0) index + 1 else 0)   // 0番目は「既定」
    }

    // ---------------------------------------------------------------- 保存

    private fun save() {
        val config = WidgetConfig(
            timeZoneId = selectedZoneId,
            theme = selectedTheme(),
            showDigital = switchDigital.isChecked,
            showAlarm = switchAlarm.isChecked,
            showLabel = switchLabel.isChecked,
            showSecondHand = switchSecondHand.isChecked,
            mondayStart = switchMondayStart.isChecked,
            swapPositions = switchSwap.isChecked,
            backgroundPercent = seekBackground.progress,
            clockApp = apps.getOrNull(spinnerClock.selectedItemPosition - 1)?.packageName,
            calendarApp = apps.getOrNull(spinnerCalendar.selectedItemPosition - 1)?.packageName,
        )
        WidgetPrefs.save(this, appWidgetId, config)
        ClockCalendarWidget.update(this, AppWidgetManager.getInstance(this), appWidgetId)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
