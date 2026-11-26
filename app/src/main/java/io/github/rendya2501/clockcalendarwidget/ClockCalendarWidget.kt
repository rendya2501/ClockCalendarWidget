package io.github.rendya2501.clockcalendarwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import kotlin.math.roundToInt

class ClockCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    // ウィジェットを削除したら、そのウィジェットの設定も消す
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.delete(context, appWidgetIds)
    }

    // 日付・時刻・タイムゾーンが変わったらカレンダーを描き直す
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, ClockCalendarWidget::class.java)
                )
                onUpdate(context, manager, ids)
            }
        }
    }

    companion object {

        /** テーマに合わせた色。「端末に従う」は、更新した時点の端末のダーク/ライト設定で決める */
        private fun paletteFor(context: Context, theme: ThemeMode): Palette {
            val dark = when (theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> {
                    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    night == Configuration.UI_MODE_NIGHT_YES
                }
            }
            return if (dark) Palette.DARK else Palette.LIGHT
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        /** 1つのウィジェットを描き直す(設定画面からも呼ぶ) */
        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val config = WidgetPrefs.load(context, id)
            val palette = paletteFor(context, config.theme)
            // 左右の入れ替えは、部品の並びだけが違う2つのレイアウトを使い分ける(idは同じ)
            val layout = if (config.swapPositions) R.layout.widget_calendar_clock else R.layout.widget_clock_calendar
            val views = RemoteViews(context.packageName, layout)

            // 文字盤とカレンダーはCanvasで描いた画像
            views.setIcon(
                R.id.analog_clock, "setDial",
                Icon.createWithBitmap(ClockFace.createDial(context, config.label, palette.dialRing))
            )
            views.setImageViewBitmap(
                R.id.calendar_image,
                CalendarRenderer.render(LocalDate.now(config.zone), config.mondayStart, palette)
            )

            // 秒針。オフのときは外す(オンのときも毎回指定して、オフからオンへ戻せるようにする)
            val secondHand = if (config.showSecondHand) {
                Icon.createWithResource(context, R.drawable.hand_second)
            } else {
                null
            }
            views.setIcon(R.id.analog_clock, "setSecondHand", secondHand)

            // 背景の濃さ。背景の形(角丸)はそのままに、色の透明度だけを変える
            val alpha = (255 * config.backgroundPercent / 100f).roundToInt()
            views.setColorStateList(
                R.id.root, "setBackgroundTintList",
                ColorStateList.valueOf(withAlpha(palette.background, alpha))
            )

            // デジタル時計(文字盤の中)。表示のオン/オフと、針と同じタイムゾーン
            views.setViewVisibility(R.id.digital_area, if (config.showDigital) View.VISIBLE else View.GONE)
            views.setString(R.id.digital_clock, "setTimeZone", config.timeZoneId)

            // デジタル時計の位置と大きさは、レイアウト側で文字盤に対する比率として決めてある
            // (ウィジェットの大きさを推定して計算する必要はない)

            // 針のタイムゾーン。null なら端末の設定に従う
            // (設定を「端末に従う」へ戻したときに前の値が残らないよう、nullでも必ず指定する)
            views.setString(R.id.analog_clock, "setTimeZone", config.timeZoneId)

            // 時計の部分は時計アプリ、カレンダーの部分はカレンダーアプリを起動する
            val clockPi = launchPendingIntent(context, id * 2, config.clockApp, ::defaultClockIntent)
            val calendarPi = launchPendingIntent(context, id * 2 + 1, config.calendarApp, ::defaultCalendarIntent)
            views.setOnClickPendingIntent(R.id.root, clockPi)
            views.setOnClickPendingIntent(R.id.analog_clock, clockPi)
            views.setOnClickPendingIntent(R.id.digital_area, clockPi)
            views.setOnClickPendingIntent(R.id.calendar_image, calendarPi)

            manager.updateAppWidget(id, views)
        }

        /** 設定で選ばれたアプリを起動するPendingIntent。未設定(または削除済み)なら既定のアプリ */
        private fun launchPendingIntent(
            context: Context,
            requestCode: Int,
            chosenPackage: String?,
            defaultIntent: (Context) -> Intent,
        ): PendingIntent {
            val chosen = chosenPackage?.let { context.packageManager.getLaunchIntentForPackage(it) }
            val intent = (chosen ?: defaultIntent(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /**
         * 端末の時計アプリを探し、アイコンをタップしたときと同じ起動用Intentを作る。
         * 見つからなければ標準の「アラームを表示」Intentにフォールバックする。
         */
        @Suppress("DEPRECATION")
        private fun defaultClockIntent(context: Context): Intent {
            val pm = context.packageManager
            val probe = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            val pkg = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            return pkg?.let { pm.getLaunchIntentForPackage(it) } ?: probe
        }

        /** 端末の既定のカレンダーアプリ(Googleカレンダーなど)を起動するIntentを作る */
        @Suppress("DEPRECATION")
        private fun defaultCalendarIntent(context: Context): Intent {
            val pm = context.packageManager
            val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            val pkg = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            return pkg?.let { pm.getLaunchIntentForPackage(it) } ?: probe
        }
    }
}
