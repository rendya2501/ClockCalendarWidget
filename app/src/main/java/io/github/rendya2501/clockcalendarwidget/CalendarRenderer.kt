package io.github.rendya2501.clockcalendarwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 月カレンダーを描く。
 * 常に6週(42マス)ぶんを描き、月の前後の空きには前月末・翌月初の日付を薄く表示する。
 * そのため月が変わっても見出し・曜日・日付の位置は動かない。
 */
object CalendarRenderer {

    // ---- 見た目の調整用(単位はビットマップのpx) ----
    private const val CELL_W = 60f
    private const val CELL_H = 52f
    private const val HEADER_H = 44f
    private const val WEEK_H = 52f
    private const val ROWS = 6

    private const val HEADER_X = 12f
    private const val HEADER_SIZE = 40f
    private const val WEEK_SIZE = 38f
    private const val DATE_SIZE = 35f
    private const val TODAY_RADIUS = 24f

    private val RED = Color.parseColor("#E5383B")
    private val WEEK_COLOR = Color.parseColor("#9A9A9E")
    private val WEEKEND_COLOR = Color.parseColor("#7A7A7E")
    private val OTHER_MONTH_COLOR = Color.parseColor("#48484C")   // 前月・翌月の日付
    private val DATE_COLOR = Color.WHITE

    fun render(today: LocalDate = LocalDate.now(), mondayStart: Boolean = false): Bitmap {
        val width = (CELL_W * 7).toInt()
        val height = (HEADER_H + WEEK_H + CELL_H * ROWS).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        // 見出し「2026/11/22 (Sun)」(今日の日付と曜日)
        paint.color = RED
        paint.textSize = HEADER_SIZE
        paint.textAlign = Paint.Align.LEFT
        val title = today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd (EEE)", Locale.ENGLISH))
        canvas.drawText(title, HEADER_X, baseline(paint, HEADER_H / 2f), paint)

        // 曜日
        paint.color = WEEK_COLOR
        paint.textSize = WEEK_SIZE
        paint.textAlign = Paint.Align.CENTER
        val weekY = baseline(paint, HEADER_H + WEEK_H / 2f)
        WeekLayout.labels(mondayStart).forEachIndexed { i, label ->
            canvas.drawText(label, CELL_W * (i + 0.5f), weekY, paint)
        }

        // 日付(その月の1日を含む週の最初の日から42日ぶん)
        paint.textSize = DATE_SIZE
        val gridStart = WeekLayout.gridStart(today.withDayOfMonth(1), mondayStart)
        for (i in 0 until ROWS * 7) {
            val date = gridStart.plusDays(i.toLong())
            val col = i % 7
            val row = i / 7
            val cx = CELL_W * (col + 0.5f)
            val cy = HEADER_H + WEEK_H + CELL_H * (row + 0.5f)
            val isToday = date == today
            val inMonth = date.monthValue == today.monthValue

            if (isToday) {
                paint.color = RED
                canvas.drawCircle(cx, cy, TODAY_RADIUS, paint)
            }
            paint.color = when {
                isToday -> Color.WHITE
                !inMonth -> OTHER_MONTH_COLOR
                WeekLayout.isWeekend(col, mondayStart) -> WEEKEND_COLOR
                else -> DATE_COLOR
            }
            canvas.drawText(date.dayOfMonth.toString(), cx, baseline(paint, cy), paint)
        }
        return bitmap
    }

    private fun baseline(paint: Paint, centerY: Float): Float {
        val fm = paint.fontMetrics
        return centerY - (fm.ascent + fm.descent) / 2f
    }
}
