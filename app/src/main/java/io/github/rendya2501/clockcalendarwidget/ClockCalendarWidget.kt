package io.github.rendya2501.clockcalendarwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Icon
import android.provider.AlarmClock
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

        // 背景の色。res/drawable/widget_bg.xml の #0A0A0A と合わせること
        private const val BG_RED = 0x0A
        private const val BG_GREEN = 0x0A
        private const val BG_BLUE = 0x0A

        /** 1つのウィジェットを描き直す(設定画面からも呼ぶ) */
        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val config = WidgetPrefs.load(context, id)
            // 左右の入れ替えは、部品の並びだけが違う2つのレイアウトを使い分ける(idは同じ)
            val layout = if (config.swapPositions) R.layout.widget_calendar_clock else R.layout.widget_clock_calendar
            val views = RemoteViews(context.packageName, layout)

            // 文字盤とカレンダーはCanvasで描いた画像
            views.setIcon(
                R.id.analog_clock, "setDial",
                Icon.createWithBitmap(ClockFace.createDial(context, config.label))
            )
            views.setImageViewBitmap(
                R.id.calendar_image,
                CalendarRenderer.render(LocalDate.now(config.zone), config.mondayStart)
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
                ColorStateList.valueOf(Color.argb(alpha, BG_RED, BG_GREEN, BG_BLUE))
            )

            // 針のタイムゾーン。null なら端末の設定に従う
            // (設定を「端末に従う」へ戻したときに前の値が残らないよう、nullでも必ず指定する)
            views.setString(R.id.analog_clock, "setTimeZone", config.timeZoneId)

            // 時計の部分は時計アプリ、カレンダーの部分はカレンダーアプリを起動する
            val clockPi = launchPendingIntent(context, id * 2, config.clockApp, ::defaultClockIntent)
            val calendarPi = launchPendingIntent(context, id * 2 + 1, config.calendarApp, ::defaultCalendarIntent)
            views.setOnClickPendingIntent(R.id.root, clockPi)
            views.setOnClickPendingIntent(R.id.analog_clock, clockPi)
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
