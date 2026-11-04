package com.goldclient.client

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs

object Icons {
    const val SWORDS = 0; const val HUD = 1; const val EYE = 2; const val CHEV = 3; const val MENU = 4
    const val SHIRT = 5; const val SEARCH = 6; const val FRIENDS = 7; const val PERSON = 8; const val ROCKET = 9
}

/** Iconos de linea dibujados con Canvas (caja de 24x24). */
class IconDrawable(private val kind: Int, color: Int, private val sizePx: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        c.save()
        c.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        c.scale(s, s)
        p.strokeWidth = 1.7f
        when (kind) {
            Icons.SWORDS -> {
                c.drawLine(5f, 5f, 19f, 19f, p); c.drawLine(19f, 5f, 5f, 19f, p)
                c.drawLine(4f, 8f, 8f, 4f, p); c.drawLine(20f, 8f, 16f, 4f, p)
                c.drawLine(4f, 16f, 8f, 20f, p); c.drawLine(20f, 16f, 16f, 20f, p)
            }
            Icons.HUD -> {
                c.drawRoundRect(RectF(4f, 4f, 20f, 20f), 3f, 3f, p)
                c.drawRoundRect(RectF(7f, 7.5f, 11f, 11.5f), 0.8f, 0.8f, p)
                c.drawLine(13.5f, 8f, 17f, 8f, p); c.drawLine(13.5f, 11f, 17f, 11f, p)
                c.drawLine(7f, 15f, 17f, 15f, p)
            }
            Icons.EYE -> {
                c.drawOval(RectF(2.5f, 6.5f, 21.5f, 17.5f), p)
                c.drawCircle(12f, 12f, 3.2f, p)
            }
            Icons.CHEV -> {
                val a = Path().apply { moveTo(5f, 6f); lineTo(11f, 12f); lineTo(5f, 18f) }
                val d = Path().apply { moveTo(12f, 6f); lineTo(18f, 12f); lineTo(12f, 18f) }
                c.drawPath(a, p); c.drawPath(d, p)
            }
            Icons.MENU -> {
                c.drawLine(5f, 7f, 19f, 7f, p); c.drawLine(5f, 12f, 19f, 12f, p); c.drawLine(5f, 17f, 19f, 17f, p)
            }
            Icons.SHIRT -> {
                val sh = Path().apply {
                    moveTo(9f, 4f); lineTo(4f, 8f); lineTo(7f, 11f); lineTo(7f, 20f); lineTo(17f, 20f)
                    lineTo(17f, 11f); lineTo(20f, 8f); lineTo(15f, 4f); quadTo(12f, 7f, 9f, 4f); close()
                }
                c.drawPath(sh, p)
            }
            Icons.SEARCH -> {
                c.drawCircle(10.5f, 10.5f, 5.5f, p); c.drawLine(14.6f, 14.6f, 19.5f, 19.5f, p)
            }
            Icons.FRIENDS -> {
                c.drawCircle(9f, 8f, 3f, p); c.drawArc(RectF(3.5f, 13f, 14.5f, 25f), 180f, 180f, false, p)
                c.drawCircle(16.5f, 9f, 2.5f, p); c.drawArc(RectF(13f, 14f, 21f, 23f), 200f, 160f, false, p)
            }
            Icons.PERSON -> {
                c.drawCircle(12f, 8f, 3.6f, p); c.drawArc(RectF(5f, 14f, 19f, 27f), 180f, 180f, false, p)
            }
            Icons.ROCKET -> {
                val body = Path().apply {
                    moveTo(12f, 2.5f); quadTo(17f, 6f, 16.5f, 13f); lineTo(7.5f, 13f); quadTo(7f, 6f, 12f, 2.5f); close()
                }
                c.drawPath(body, p)
                c.drawCircle(12f, 8.5f, 1.6f, p)
                c.drawLine(7.5f, 11f, 4.5f, 15.5f, p); c.drawLine(4.5f, 15.5f, 7.5f, 14.5f, p)
                c.drawLine(16.5f, 11f, 19.5f, 15.5f, p); c.drawLine(19.5f, 15.5f, 16.5f, 14.5f, p)
                c.drawLine(12f, 15f, 12f, 20f, p)
            }
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
}

/**
 * Fila de fichas con efecto "lupa": la ficha bajo el dedo se agranda, se ilumina y las vecinas se apartan.
 * Al soltar sobre una ficha, se elige.
 */
class HoverRow(
    ctx: Context,
    private val density: Float,
    private val fill: Int, private val fillOn: Int,
    private val line: Int, private val lineOn: Int,
    private val onPick: (Int) -> Unit
) : LinearLayout(ctx) {

    private var focus = -1

    init { orientation = HORIZONTAL; clipChildren = false; clipToPadding = false }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setFocus(indexAt(e.x, e.y))
            }
            MotionEvent.ACTION_UP -> {
                val i = indexAt(e.x, e.y); setFocus(-1)
                if (i >= 0) onPick(i)
            }
            MotionEvent.ACTION_CANCEL -> setFocus(-1)
        }
        return true
    }

    private fun indexAt(x: Float, y: Float): Int {
        if (y < -40 * density || y > height + 40 * density) return -1
        var best = -1; var dist = Float.MAX_VALUE
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val d = abs(x - (c.left + c.right) / 2f)
            if (d < dist) { dist = d; best = i }
        }
        return best
    }

    private fun setFocus(i: Int) {
        if (i == focus) return
        focus = i
        for (j in 0 until childCount) {
            val c = getChildAt(j)
            val on = j == i
            val scale = if (i < 0) 1f else if (on) 1.22f else 0.96f
            val shift = if (i < 0 || on) 0f else (if (j < i) -1f else 1f) * c.width * 0.13f
            c.animate().scaleX(scale).scaleY(scale).translationX(shift)
                .translationZ(if (on) 8f * density else 0f).setDuration(140).start()
            (c.background as? GradientDrawable)?.let {
                it.setColor(if (on) fillOn else fill)
                it.setStroke((density).toInt().coerceAtLeast(1), if (on) lineOn else line)
            }
            c.alpha = if (i < 0 || on) 1f else 0.85f
        }
    }
}
