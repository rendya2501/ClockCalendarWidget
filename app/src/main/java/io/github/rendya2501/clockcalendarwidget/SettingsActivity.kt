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
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.SeekBar
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
        const val STATE_CLOCK_TAP = "state_clock_tap"
        const val STATE_CALENDAR_TAP = "state_calendar_tap"
    }

    private data class AppEntry(val label: String, val packageName: String)

    /**
     * 選択画面(都市・アプリ共通)の1行。
     * value が null のものは「既定」(都市なら「端末の設定に従う」)。
     * pinned の行は、一覧の外(検索欄の下)に固定して、スクロールや検索で動かないようにする。
     * searchKey は、画面に出ない検索用の文字(英語の都市名やパッケージ名など)。
     */
    private data class PickerItem(
        val value: String?,
        val title: String,
        val searchKey: String = "",
        val pinned: Boolean = false,
    )

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var appliedTheme = ThemeMode.SYSTEM   // この画面にいま適用しているテーマ

    private var selectedZoneId: String? = null   // null = 端末のタイムゾーンに従う
    private var zoneItems: List<PickerItem> = emptyList()
    private var clockTap: String? = null       // 時計の部分をタップしたとき。null = 既定、TapTarget.NONE = 何もしない、それ以外 = アプリ
    private var calendarTap: String? = null    // カレンダーの部分をタップしたとき(同上)
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
    private lateinit var clockRow: TextView
    private lateinit var calendarRow: TextView

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
        clockRow = requireViewById<TextView>(R.id.tap_clock_row)
        calendarRow = requireViewById<TextView>(R.id.tap_calendar_row)

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

        // ---- タップしたときの動き(既定 / 何もしない / アプリ) ----
        if (savedInstanceState != null) {
            clockTap = savedInstanceState.getString(STATE_CLOCK_TAP)
            calendarTap = savedInstanceState.getString(STATE_CALENDAR_TAP)
        } else {
            clockTap = installedOrDefault(config.clockApp)
            calendarTap = installedOrDefault(config.calendarApp)
        }
        updateTapRows()
        clockRow.setOnClickListener {
            showTapPicker(getString(R.string.tap_clock), getString(R.string.app_default_clock), clockTap) {
                clockTap = it
                updateTapRows()
            }
        }
        calendarRow.setOnClickListener {
            showTapPicker(getString(R.string.tap_calendar), getString(R.string.app_default_calendar), calendarTap) {
                calendarTap = it
                updateTapRows()
            }
        }

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
        outState.putString(STATE_CLOCK_TAP, clockTap)
        outState.putString(STATE_CALENDAR_TAP, calendarTap)
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
    private fun buildZoneItems(): List<PickerItem> {
        val collator = Collator.getInstance()
        val cities = ZoneCatalog.ids().map { id ->
            val zone = ZoneId.of(id)
            PickerItem(
                value = id,
                title = ZoneLabels.title(zone),
                // 画面の名前(端末の言語の都市名・都市コード)に加えて、英語の都市名とIDでも探せる
                searchKey = "${ZoneLabels.cityName(id)} $id".lowercase(),
            )
        }.sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) })
        return listOf(PickerItem(null, getString(R.string.follow_system_zone), pinned = true)) + cities
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
        showPicker(getString(R.string.section_zone), getString(R.string.picker_search_hint), zoneItems, selectedZoneId) {
            selectedZoneId = it
            updateZoneRow()
        }
    }

    /**
     * 都市・アプリで共通の選択画面。上に検索欄、その下に固定する行、その下に一覧、下にキャンセルボタン。
     * 選択中の行にはチェックが付く。
     * 固定する行(pinned。「既定」「何もしない」など)は一覧の外に置くので、スクロールしても、検索しても動かない。
     * 検索は、画面に見える名前に含まれる文字で探す。画面に出ないパッケージ名やIDは、3文字以上のときだけ、
     * 名前でヒットしたものの後ろに足す(「q」と打っただけで、名前に関係ないものが出ないように)。
     * @param selected いま選ばれている value(null = 既定)
     */
    private fun showPicker(
        title: String,
        searchHint: String,
        items: List<PickerItem>,
        selected: String?,
        onPicked: (String?) -> Unit,
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_zone_picker, null)
        val search = view.requireViewById<EditText>(R.id.picker_search)
        val pinnedGroup = view.requireViewById<LinearLayout>(R.id.picker_pinned_group)
        val pinnedBox = view.requireViewById<LinearLayout>(R.id.picker_pinned)
        val list = view.requireViewById<ListView>(R.id.picker_list)
        search.hint = searchHint
        list.choiceMode = ListView.CHOICE_MODE_SINGLE

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val pinned = items.filter { it.pinned }
        val scrolling = items.filter { !it.pinned }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val pinnedRowHeight = 40   // 固定する行の高さ(dp)。標準の約48dpより細くして、一覧に高さを回す

        // 固定する行
        pinnedGroup.visibility = if (pinned.isEmpty()) View.GONE else View.VISIBLE
        pinned.forEach { item ->
            val row = layoutInflater.inflate(android.R.layout.simple_list_item_checked, pinnedBox, false) as CheckedTextView
            row.text = item.title
            row.isChecked = item.value == selected
            row.minHeight = dp(pinnedRowHeight)
            row.minimumHeight = dp(pinnedRowHeight)
            row.setOnClickListener {
                onPicked(item.value)
                dialog.dismiss()
            }
            pinnedBox.addView(row)
        }

        // キーボードが出ている間は、固定する行を隠して、一覧に高さを回す(隠すのは、検索中は選ばないと割り切っているため)。
        // 一覧の高さは、固定する行があるときは、その分だけ縮める。
        var imeVisible = false
        fun applyLayout() {
            val listHeight = if (imeVisible) 144 else 320 - pinnedRowHeight * pinned.size
            list.layoutParams = list.layoutParams.apply { height = dp(listHeight) }
            pinnedGroup.visibility = if (pinned.isEmpty() || imeVisible) View.GONE else View.VISIBLE
        }
        applyLayout()
        dialog.setOnShowListener {
            val decor = dialog.window?.decorView ?: return@setOnShowListener
            decor.setOnApplyWindowInsetsListener { v, insets ->
                val visible = insets.isVisible(WindowInsets.Type.ime())
                if (visible != imeVisible) {
                    imeVisible = visible
                    applyLayout()
                }
                v.onApplyWindowInsets(insets)
            }
            decor.requestApplyInsets()
        }

        var shown: List<PickerItem> = scrolling

        fun render(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) {
                scrolling
            } else {
                val (byName, others) = scrolling.partition { it.title.lowercase().contains(q) }
                byName + if (q.length >= 3) others.filter { it.searchKey.contains(q) } else emptyList()
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_checked, shown.map { it.title })
            val checked = shown.indexOfFirst { it.value == selected }
            if (checked >= 0) {
                list.setItemChecked(checked, true)
                list.setSelection(checked)   // 選択中の行が見える位置までスクロール
            }
        }

        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = render(s?.toString().orEmpty())
        })
        list.setOnItemClickListener { _, _, position, _ ->
            onPicked(shown[position].value)
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

    /** 保存してあるアプリが、もう入っていなければ「既定」に戻す(「既定」「何もしない」はそのまま) */
    private fun installedOrDefault(saved: String?): String? =
        if (saved == null || saved == TapTarget.NONE || apps.any { it.packageName == saved }) saved else null

    /** 設定画面の「既定の時計アプリ ▼」などの行 */
    private fun updateTapRows() {
        clockRow.text = tapTitle(clockTap, getString(R.string.app_default_clock))
        calendarRow.text = tapTitle(calendarTap, getString(R.string.app_default_calendar))
    }

    private fun tapTitle(value: String?, defaultLabel: String): String = when (value) {
        null -> defaultLabel
        TapTarget.NONE -> getString(R.string.app_none)
        else -> apps.firstOrNull { it.packageName == value }?.label ?: defaultLabel
    }

    /** 「既定」「何もしない」を一番上に固定して、そのあとにアプリを並べた、検索できる一覧を出す */
    private fun showTapPicker(title: String, defaultLabel: String, selected: String?, onPicked: (String?) -> Unit) {
        val items = listOf(
            PickerItem(null, defaultLabel, pinned = true),
            PickerItem(TapTarget.NONE, getString(R.string.app_none), pinned = true),
        ) + apps.map { PickerItem(it.packageName, it.label, it.packageName.lowercase()) }
        showPicker(title, getString(R.string.picker_search_hint_app), items, selected, onPicked)
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
            clockApp = clockTap,
            calendarApp = calendarTap,
        )
        WidgetPrefs.save(this, appWidgetId, config)
        ClockCalendarWidget.update(this, AppWidgetManager.getInstance(this), appWidgetId)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
