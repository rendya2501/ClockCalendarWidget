package io.github.rendya2501.clockcalendarwidget

import android.content.Context
import android.content.SharedPreferences
import android.icu.text.TimeZoneNames
import android.icu.util.ULocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** ウィジェット1つぶんの設定。null は「既定」を表す */
data class WidgetConfig(
    val timeZoneId: String? = null,   // null = 端末のタイムゾーンに従う
    val showLabel: Boolean = true,    // 文字盤に都市コード(TYOなど)を表示するか
    val showSecondHand: Boolean = true,  // 秒針を表示するか
    val mondayStart: Boolean = false,    // カレンダーを月曜始まりにするか(false = 日曜始まり)
    val swapPositions: Boolean = false,  // 時計とカレンダーの左右を入れ替えるか(true = カレンダーが左)
    val theme: ThemeMode = ThemeMode.DARK,  // ダーク / ライト / 端末に従う
    val showAlarm: Boolean = true,       // 次のアラームがあるとき、ベルを出すか
    val showDigital: Boolean = true,     // 文字盤の中のデジタル時計を表示するか
    val backgroundPercent: Int = 100,    // 背景の濃さ(0〜100%)
    val clockApp: String? = null,     // 時計部分をタップしたときのアプリ(パッケージ名)。null = 既定の時計アプリ、TapTarget.NONE = 何もしない
    val calendarApp: String? = null,  // カレンダー部分をタップしたときのアプリ。null = 既定のカレンダーアプリ、TapTarget.NONE = 何もしない
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
    private const val THEME = "theme"
    private const val DIGITAL = "digital"
    private const val ALARM = "alarm"
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
            theme = p.getString(key(THEME, id), null)
                ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() } ?: ThemeMode.DARK,
            showDigital = p.getBoolean(key(DIGITAL, id), true),
            showAlarm = p.getBoolean(key(ALARM, id), true),
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
            .putString(key(THEME, id), config.theme.name)
            .putBoolean(key(DIGITAL, id), config.showDigital)
            .putBoolean(key(ALARM, id), config.showAlarm)
            .putInt(key(BG_PERCENT, id), config.backgroundPercent.coerceIn(0, 100))
            .putOrRemove(key(CLOCK_APP, id), config.clockApp)
            .putOrRemove(key(CALENDAR_APP, id), config.calendarApp)
            .apply()
    }

    fun delete(context: Context, ids: IntArray) {
        val editor = prefs(context).edit()
        for (id in ids) {
            listOf(TZ, SHOW_LABEL, SECOND_HAND, MONDAY_START, SWAP, THEME, DIGITAL, ALARM, BG_PERCENT, CLOCK_APP, CALENDAR_APP).forEach { editor.remove(key(it, id)) }
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
        "Kolkata" to "CCU", "Calcutta" to "CCU", "Saigon" to "SGN", "Kiev" to "IEV", "Karachi" to "KHI", "Dhaka" to "DAC", "Dubai" to "DXB",
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

    // 端末の言語での都市名(Androidに入っているICUのデータ)
    private val names by lazy { TimeZoneNames.getInstance(ULocale.getDefault()) }

    /** 都市名。端末の言語で取れればそれ(東京)、取れなければIDから作った名前(Tokyo) */
    fun displayName(zoneId: String): String {
        if (zoneId == "UTC") return "UTC"
        val localized = runCatching { names.getExemplarLocationName(zoneId) }.getOrNull()
        return if (localized.isNullOrBlank()) cityName(zoneId) else localized
    }

    /** 一覧や設定画面に出す名前。「東京 (TYO)」の形 */
    fun title(zone: ZoneId): String {
        val city = displayName(zone.id)
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
    /**
     * 場所に結びついた標準のタイムゾーンID。別名・旧名は含まれない(Androidに入っているICUのデータ)。
     * ICUから取れなかったときは java.time の一覧で代用する。
     */
    fun ids(): List<String> {
        val icu = runCatching {
            android.icu.util.TimeZone.getAvailableIDs(
                android.icu.util.TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null
            )
        }.getOrNull().orEmpty()
        val base: Collection<String> = icu.ifEmpty {
            ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc/") }
        }
        return (base + "UTC")
            .filter { runCatching { ZoneId.of(it) }.isSuccess }
            .distinct()
            .sorted()
    }
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

/** テーマ。SYSTEM は端末のダーク/ライト設定に従う */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** 見た目の色(ARGB)。ダークは従来の見た目 */
data class Palette(
    val background: Int,     // ウィジェットの背景
    val accent: Int,         // カレンダーの見出しと、今日を示す丸
    val date: Int,           // 平日の日付
    val week: Int,           // 曜日の見出し
    val weekend: Int,        // 土日の日付
    val otherMonth: Int,     // 前月・翌月の日付
    val dialRing: Int?,      // 文字盤の外周の輪郭線(なしならnull)
) {
    companion object {
        val DARK = Palette(
            background = 0xFF0A0A0A.toInt(), accent = 0xFFE5383B.toInt(), date = 0xFFFFFFFF.toInt(),
            week = 0xFF9A9A9E.toInt(), weekend = 0xFF7A7A7E.toInt(), otherMonth = 0xFF48484C.toInt(),
            dialRing = null,
        )
        val LIGHT = Palette(
            background = 0xFFF4F4F5.toInt(), accent = 0xFFE5383B.toInt(), date = 0xFF1A1A1A.toInt(),
            week = 0xFF8A8A8E.toInt(), weekend = 0xFFA2A2A8.toInt(), otherMonth = 0xFFCFCFD3.toInt(),
            dialRing = 0xFFCFCFCF.toInt(),
        )
    }
}

/** 日付が変わる時刻(その地域の次の0時)を求める */
object MidnightClock {
    /**
     * now のあとに来る「その地域の0時」の、5秒後。
     * 夏時間などで0時がない日は、その日の最初の時刻になる。
     */
    fun nextAfter(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(5)
}

/** タップしたときの動きで、アプリ以外の選択肢 */
object TapTarget {
    /** 「何もしない」。パッケージ名には使えない文字(:)を含むので、アプリ名と重ならない */
    const val NONE = ":none:"
}
