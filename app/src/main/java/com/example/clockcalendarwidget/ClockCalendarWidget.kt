package com.example.clockcalendarwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.provider.AlarmClock
import android.widget.RemoteViews

class ClockCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
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

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_clock_calendar)

        // 文字盤とカレンダーはCanvasで描いた画像
        views.setIcon(R.id.analog_clock, "setDial", Icon.createWithBitmap(ClockFace.createDial(context)))
        views.setImageViewBitmap(R.id.calendar_image, CalendarRenderer.render())

        // ウィジェットのどこをタップしても、端末の時計アプリを起動する
        val clockPi = clockPendingIntent(context)
        views.setOnClickPendingIntent(R.id.root, clockPi)
        views.setOnClickPendingIntent(R.id.analog_clock, clockPi)
        views.setOnClickPendingIntent(R.id.calendar_image, clockPi)

        manager.updateAppWidget(id, views)
    }

    /**
     * 端末の時計アプリを探し、アイコンをタップしたときと同じ起動用Intentを作る。
     * 見つからなければ標準の「アラームを表示」Intentにフォールバックする。
     */
    @Suppress("DEPRECATION")
    private fun clockPendingIntent(context: Context): PendingIntent {
        val pm = context.packageManager
        val probe = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val clockPackage = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val intent = clockPackage?.let { pm.getLaunchIntentForPackage(it) } ?: probe
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
