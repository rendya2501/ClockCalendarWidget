package io.github.rendya2501.clockcalendarwidget

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.ZoneId

/** ウィジェット1つぶんの設定。null は「既定」を表す */
data class WidgetConfig(
    val timeZoneId: String? = null,   // null = 端末のタイムゾーンに従う
    val showLabel: Boolean = true,    // 文字盤に都市コード(TYOなど)を表示するか
    val showSecondHand: Boolean = true,  // 秒針を表示するか
    val mondayStart: Boolean = false,    // カレンダーを月曜始まりにするか(false = 日曜始まり)
    val swapPositions: Boolean = false,  // 時計とカレンダーの左右を入れ替えるか(true = カレンダーが左)
    val backgroundPercent: Int = 100,    // 背景の濃さ(0〜100%)
    val clockApp: String? = null,     // 時計部分をタップしたときのアプリ(パッケージ名)。null = 既定の時計アプリ
    val calendarApp: String? = null,  // カレンダー部分をタップしたときのアプリ。null = 既定のカレンダーアプリ
) {
    /** 実際に使うタイムゾーン */
    val zone: ZoneId
        get() = timeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    /** 文字盤に描くラベル(都市コード)。空文字なら非表示 */
    val label: String
        get() = if (showLabel) ZoneLabels.codeFor(zone) else ""
}

/** 設定をウィジェットごと(appWidgetIdごと)にSharedPreferencesへ保存する */
object WidgetPrefs {
    private const val FILE = "widget_settings"
    private const val TZ = "tz"
    private const val SHOW_LABEL = "show_label"
    private const val SECOND_HAND = "second_hand"
    private const val MONDAY_START = "monday_start"
    private const val SWAP = "swap"
    private const val BG_PERCENT = "bg_percent"
    private const val CLOCK_APP = "clock_app"
    private const val CALENDAR_APP = "calendar_app"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(name: String, id: Int) = "${name}_$id"

    fun load(context: Context, id: Int): WidgetConfig {
        val p = prefs(context)
        return WidgetConfig(
            timeZoneId = p.getString(key(TZ, id), null),
            showLabel = p.getBoolean(key(SHOW_LABEL, id), true),
            showSecondHand = p.getBoolean(key(SECOND_HAND, id), true),
            mondayStart = p.getBoolean(key(MONDAY_START, id), false),
            swapPositions = p.getBoolean(key(SWAP, id), false),
            backgroundPercent = p.getInt(key(BG_PERCENT, id), 100).coerceIn(0, 100),
            clockApp = p.getString(key(CLOCK_APP, id), null),
            calendarApp = p.getString(key(CALENDAR_APP, id), null),
        )
    }

    fun save(context: Context, id: Int, config: WidgetConfig) {
        prefs(context).edit()
            .putOrRemove(key(TZ, id), config.timeZoneId)
            .putBoolean(key(SHOW_LABEL, id), config.showLabel)
            .putBoolean(key(SECOND_HAND, id), config.showSecondHand)
            .putBoolean(key(MONDAY_START, id), config.mondayStart)
            .putBoolean(key(SWAP, id), config.swapPositions)
            .putInt(key(BG_PERCENT, id), config.backgroundPercent.coerceIn(0, 100))
            .putOrRemove(key(CLOCK_APP, id), config.clockApp)
            .putOrRemove(key(CALENDAR_APP, id), config.calendarApp)
            .apply()
    }

    fun delete(context: Context, ids: IntArray) {
        val editor = prefs(context).edit()
        for (id in ids) {
            listOf(TZ, SHOW_LABEL, SECOND_HAND, MONDAY_START, SWAP, BG_PERCENT, CLOCK_APP, CALENDAR_APP).forEach { editor.remove(key(it, id)) }
        }
        editor.apply()
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) =
        if (value == null) remove(key) else putString(key, value)
}

/** タイムゾーン → 文字盤の3文字ラベル(空港コード風)。表にない都市は都市名の頭3文字 */
object ZoneLabels {
    private val CODES = mapOf(
        "Tokyo" to "TYO", "Osaka" to "OSA", "Seoul" to "SEL", "Shanghai" to "SHA",
        "Hong_Kong" to "HKG", "Taipei" to "TPE", "Singapore" to "SIN", "Bangkok" to "BKK",
        "Jakarta" to "JKT", "Manila" to "MNL", "Kuala_Lumpur" to "KUL", "Ho_Chi_Minh" to "SGN",
        "Kolkata" to "CCU", "Calcutta" to "CCU", "Karachi" to "KHI", "Dhaka" to "DAC", "Dubai" to "DXB",
        "Riyadh" to "RUH", "Tehran" to "THR", "Istanbul" to "IST",
        "London" to "LON", "Dublin" to "DUB", "Lisbon" to "LIS", "Paris" to "PAR",
        "Madrid" to "MAD", "Berlin" to "BER", "Rome" to "ROM", "Amsterdam" to "AMS",
        "Brussels" to "BRU", "Zurich" to "ZRH", "Vienna" to "VIE", "Prague" to "PRG",
        "Warsaw" to "WAW", "Stockholm" to "STO", "Oslo" to "OSL", "Copenhagen" to "CPH",
        "Helsinki" to "HEL", "Athens" to "ATH", "Moscow" to "MOW", "Kyiv" to "IEV",
        "Cairo" to "CAI", "Lagos" to "LOS", "Nairobi" to "NBO", "Johannesburg" to "JNB",
        "New_York" to "NYC", "Chicago" to "CHI", "Denver" to "DEN", "Los_Angeles" to "LAX",
        "Anchorage" to "ANC", "Honolulu" to "HNL", "Toronto" to "YTO", "Vancouver" to "YVR",
        "Mexico_City" to "MEX", "Sao_Paulo" to "SAO", "Buenos_Aires" to "BUE",
        "Sydney" to "SYD", "Melbourne" to "MEL", "Auckland" to "AKL",
    )

    /** タイムゾーンIDから都市名を取り出す(Asia/Ho_Chi_Minh → Ho Chi Minh) */
    fun cityName(zoneId: String): String =
        if (zoneId == "UTC") "UTC" else zoneId.substringAfterLast('/').replace('_', ' ')

    /** 一覧や設定画面に出す名前。「Tokyo (TYO)」の形 */
    fun title(zone: ZoneId): String {
        val city = cityName(zone.id)
        val code = codeFor(zone)
        return if (city == code) city else "$city ($code)"
    }

    fun codeFor(zone: ZoneId): String {
        val id = zone.id
        if (id == "UTC" || id == "GMT" || id == "Z") return "UTC"
        val city = id.substringAfterLast('/')
        return CODES[city] ?: city.replace("_", "").take(3).uppercase()
    }
}

/** 設定画面の都市一覧に出すタイムゾーンID */
object ZoneCatalog {
    private val REGIONS =
        Regex("^(Africa|America|Antarctica|Arctic|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.+")

    // 旧名・別名。一覧に出すと同じ都市が二重に見えたり、見慣れない名前が混ざったりするので省く
    private val LEGACY = setOf(
        "Africa/Asmera", "Africa/Timbuktu",
        "America/Argentina/ComodRivadavia", "America/Atka", "America/Catamarca", "America/Cordoba",
        "America/Ensenada", "America/Fort_Wayne", "America/Godthab", "America/Jujuy", "America/Knox_IN",
        "America/Mendoza", "America/Montreal", "America/Porto_Acre", "America/Rosario",
        "America/Santa_Isabel", "America/Shiprock", "America/Virgin",
        "Antarctica/South_Pole",
        "Asia/Ashkhabad", "Asia/Calcutta", "Asia/Chongqing", "Asia/Chungking", "Asia/Dacca", "Asia/Harbin",
        "Asia/Istanbul", "Asia/Kashgar", "Asia/Katmandu", "Asia/Macao", "Asia/Rangoon", "Asia/Saigon",
        "Asia/Tel_Aviv", "Asia/Thimbu", "Asia/Ujung_Pandang", "Asia/Ulan_Bator",
        "Atlantic/Faeroe", "Atlantic/Jan_Mayen",
        "Australia/ACT", "Australia/Canberra", "Australia/LHI", "Australia/NSW", "Australia/North",
        "Australia/Queensland", "Australia/South", "Australia/Tasmania", "Australia/Victoria",
        "Australia/West", "Australia/Yancowinna",
        "Europe/Belfast", "Europe/Kiev", "Europe/Tiraspol",
        "Pacific/Ponape", "Pacific/Samoa", "Pacific/Truk", "Pacific/Yap",
    )

    /** 都市名の順に並べた選択肢。同じ都市名のIDは1つにまとめる */
    fun ids(): List<String> =
        ZoneId.getAvailableZoneIds()
            .filter { (it == "UTC" || REGIONS.matches(it)) && it !in LEGACY }
            .sorted()
            .distinctBy { ZoneLabels.cityName(it) }
            .sortedBy { ZoneLabels.cityName(it).lowercase() }
}

/** カレンダーの週の並び(日曜始まり/月曜始まり) */
object WeekLayout {
    /** 曜日の見出し */
    fun labels(mondayStart: Boolean): List<String> =
        if (mondayStart) listOf("M", "T", "W", "T", "F", "S", "S")
        else listOf("S", "M", "T", "W", "T", "F", "S")

    /** 表の左上の日付(その月の1日を含む週の、最初の日) */
    fun gridStart(first: LocalDate, mondayStart: Boolean): LocalDate {
        val offset = if (mondayStart) first.dayOfWeek.value - 1 else first.dayOfWeek.value % 7
        return first.minusDays(offset.toLong())
    }

    /** 土日の列か(列は左から0〜6) */
    fun isWeekend(col: Int, mondayStart: Boolean): Boolean =
        if (mondayStart) col >= 5 else col == 0 || col == 6
}
