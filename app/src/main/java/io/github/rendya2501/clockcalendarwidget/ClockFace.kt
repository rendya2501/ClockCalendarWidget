package io.github.rendya2501.clockcalendarwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** アナログ時計の文字盤(白い円+60本の目盛り+数字1〜12+ラベル)を描く */
object ClockFace {

    // 針のvector(hand_*.xml)の width/height(200dp) と必ず合わせること
    private const val DIAL_DP = 200f

    // ---- 見た目の調整用 ----
    private const val NUMBER_RADIUS = 0.72f      // 数字を置く位置(半径比)
    private const val NUMBER_SIZE = 0.12f        // 数字の大きさ(直径比)
    private const val LABEL_SIZE = 0.07f
    private const val LABEL_OFFSET = 0.40f       // 中心から上へ(半径比)

    private const val TICK_OUTER = 0.98f         // 目盛りの外側(半径比)
    private const val TICK_INNER = 0.94f         // 通常の目盛りの内側
    private const val TICK_INNER_BIG = 0.90f     // 5つごとの目盛りの内側
    private const val TICK_WIDTH = 0.005f        // 線の太さ(直径比)
    private const val TICK_WIDTH_BIG = 0.012f

    private val DIAL_COLOR = Color.WHITE
    private val NUMBER_COLOR = Color.parseColor("#111111")
    private val LABEL_COLOR = Color.parseColor("#9A9A9A")
    private val TICK_COLOR = Color.parseColor("#8A8A8A")
    private val TICK_COLOR_BIG = Color.parseColor("#111111")

    /** @param label 文字盤の上の方に描く小さい文字(例: TYO)。空文字なら描かない */
    fun createDial(context: Context, label: String): Bitmap {
        val metrics = context.resources.displayMetrics
        val size = (DIAL_DP * metrics.density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.density = metrics.densityDpi

        val canvas = Canvas(bitmap)
        val r = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 文字盤
        paint.color = DIAL_COLOR
        canvas.drawCircle(r, r, r, paint)

        // 目盛り(60本。5本ごとに太く長く)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val big = i % 5 == 0
            val angle = Math.toRadians(i * 6.0)
            val inner = r * (if (big) TICK_INNER_BIG else TICK_INNER)
            val outer = r * TICK_OUTER
            paint.color = if (big) TICK_COLOR_BIG else TICK_COLOR
            paint.strokeWidth = size * (if (big) TICK_WIDTH_BIG else TICK_WIDTH)
            canvas.drawLine(
                r + (inner * sin(angle)).toFloat(), r - (inner * cos(angle)).toFloat(),
                r + (outer * sin(angle)).toFloat(), r - (outer * cos(angle)).toFloat(),
                paint
            )
        }
        paint.style = Paint.Style.FILL

        // 数字
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER

        paint.color = NUMBER_COLOR
        paint.textSize = size * NUMBER_SIZE
        val numberShift = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        for (n in 1..12) {
            val angle = Math.toRadians(n * 30.0 - 90.0)
            val x = r + (r * NUMBER_RADIUS * cos(angle)).toFloat()
            val y = r + (r * NUMBER_RADIUS * sin(angle)).toFloat() + numberShift
            canvas.drawText(n.toString(), x, y, paint)
        }

        // ラベル
        if (label.isNotEmpty()) {
            paint.color = LABEL_COLOR
            paint.textSize = size * LABEL_SIZE
            val labelShift = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            canvas.drawText(label, r, r - r * LABEL_OFFSET + labelShift, paint)
        }
        return bitmap
    }
}
