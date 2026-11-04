package com.goldclient.client

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        c.save()
        c.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        c.scale(s, s)
        p.strokeWidth = 1.9f
        when (kind) {
            Icons.SWORDS -> {
                // espada que apunta arriba a la derecha (hoja rellena) + espada fina que la cruza con guarda en cruz
                val blade = Path().apply { moveTo(10f, 14f); lineTo(17.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 6.5f); lineTo(10f, 14f); close() }
                c.drawPath(blade, f); c.drawPath(blade, p)
                c.drawLine(7.5f, 12.5f, 11.5f, 16.5f, p)          // guarda
                c.drawLine(9.5f, 14.5f, 6f, 18f, p)                // empunadura
                c.drawLine(5f, 5f, 15.5f, 15.5f, p)                // espada fina
                c.drawLine(13f, 18f, 18f, 13f, p)                  // guarda en cruz
                c.drawLine(16.5f, 16.5f, 19f, 19f, p)
            }
            Icons.HUD -> {
                c.drawRoundRect(RectF(4f, 4f, 20f, 20f), 2.2f, 2.2f, p)
                c.drawLine(7.5f, 8.5f, 12.5f, 8.5f, p); c.drawLine(14.8f, 8.5f, 16.5f, 8.5f, p)
                c.drawLine(9f, 12f, 16.5f, 12f, p)
                c.drawLine(7.5f, 15.5f, 12.5f, 15.5f, p); c.drawLine(14.8f, 15.5f, 16.5f, 15.5f, p)
            }
            Icons.EYE -> {
                val eye = Path().apply { moveTo(1.8f, 12f); quadTo(12f, 2.5f, 22.2f, 12f); quadTo(12f, 21.5f, 1.8f, 12f); close() }
                f.alpha = 70; c.drawPath(eye, f); f.alpha = 255
                c.drawPath(eye, p)
                c.drawCircle(12f, 12f, 4f, p)
                c.drawCircle(12f, 12f, 1.8f, f)
            }
            Icons.CHEV -> {
                p.strokeWidth = 1.7f
                val a1 = Path().apply { moveTo(5.5f, 5f); lineTo(12.5f, 12f); lineTo(5.5f, 19f) }
                val a2 = Path().apply { moveTo(11.5f, 5f); lineTo(18.5f, 12f); lineTo(11.5f, 19f) }
                c.drawPath(a1, p); c.drawPath(a2, p)
            }
            Icons.MENU -> {
                p.strokeWidth = 2.1f
                c.drawLine(5f, 6.5f, 19f, 6.5f, p); c.drawLine(5f, 12f, 19f, 12f, p); c.drawLine(5f, 17.5f, 19f, 17.5f, p)
            }
            Icons.SHIRT -> {
                val sh = Path().apply {
                    moveTo(8.5f, 4.5f); lineTo(3.5f, 7.5f); lineTo(5.5f, 11f); lineTo(8f, 10f); lineTo(8f, 20f)
                    lineTo(16f, 20f); lineTo(16f, 10f); lineTo(18.5f, 11f); lineTo(20.5f, 7.5f); lineTo(15.5f, 4.5f)
                    quadTo(12f, 8f, 8.5f, 4.5f); close()
                }
                c.drawPath(sh, p)
            }
            Icons.SEARCH -> {
                c.drawCircle(10.5f, 10.5f, 6f, p); c.drawLine(15f, 15f, 20f, 20f, p)
            }
            Icons.FRIENDS -> {
                c.drawCircle(9f, 8f, 3f, p); c.drawArc(RectF(3f, 13.5f, 15f, 25f), 180f, 180f, false, p)
                c.drawArc(RectF(12.5f, 5f, 18.5f, 11f), 280f, 200f, false, p)
                c.drawArc(RectF(14f, 13.5f, 22f, 22f), 235f, 125f, false, p)
            }
            Icons.PERSON -> {
                c.drawCircle(12f, 8f, 3.6f, p)
                val body = Path().apply {
                    moveTo(5f, 20f); lineTo(5f, 18f); quadTo(5f, 14.5f, 9f, 14.5f); lineTo(15f, 14.5f)
                    quadTo(19f, 14.5f, 19f, 18f); lineTo(19f, 20f); close()
                }
                c.drawPath(body, p)
            }
            Icons.ROCKET -> {
                c.rotate(45f, 12f, 11.5f)                            // cohete vertical girado: apunta arriba a la derecha
                val body = Path().apply { moveTo(12f, 1.5f); quadTo(18f, 6f, 16.8f, 14f); lineTo(7.2f, 14f); quadTo(6f, 6f, 12f, 1.5f); close() }
                c.drawPath(body, p)
                c.drawCircle(12f, 8f, 1.7f, f)
                val l = Path().apply { moveTo(7.6f, 10.5f); lineTo(4.2f, 15.5f); lineTo(7.6f, 14.5f) }
                val r = Path().apply { moveTo(16.4f, 10.5f); lineTo(19.8f, 15.5f); lineTo(16.4f, 14.5f) }
                c.drawPath(l, p); c.drawPath(r, p)
                val fl = Path().apply { moveTo(10f, 16.5f); lineTo(12f, 20.5f); lineTo(14f, 16.5f) }
                c.drawPath(fl, p)
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

/** Lienzo con las medidas del diseno original (941 x 1672), escalado al ancho de la pantalla. */
class DesignLayout(ctx: Context) : ViewGroup(ctx) {
    private class Item(val v: View, val x: Float, val y: Float, val w: Float, val h: Float, val baselineY: Float?)
    private val items = ArrayList<Item>()
    private var s = 1f

    /** x, y, w, h en pixeles de diseno. */
    fun add(v: View, x: Float, y: Float, w: Float, h: Float) { items.add(Item(v, x, y, w, h, null)); addView(v) }
    /** Texto colocado por su linea base. */
    fun addText(v: TextView, x: Float, baselineY: Float, w: Float) { items.add(Item(v, x, 0f, w, 0f, baselineY)); addView(v) }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        s = w / Spec.DW
        for (it in items) {
            val cw = MeasureSpec.makeMeasureSpec((it.w * s).toInt(), MeasureSpec.EXACTLY)
            val ch = if (it.baselineY == null) MeasureSpec.makeMeasureSpec((it.h * s).toInt(), MeasureSpec.EXACTLY)
                     else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            it.v.measure(cw, ch)
        }
        setMeasuredDimension(w, (Spec.DH * s).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (it in items) {
            val left = (it.x * s).toInt()
            val top = if (it.baselineY == null) (it.y * s).toInt() else (it.baselineY * s - it.v.baseline).toInt()
            it.v.layout(left, top, left + it.v.measuredWidth, top + it.v.measuredHeight)
        }
    }
}

/** Interruptor de vidrio: pista que cambia a dorado y bolita blanca que se desliza. */
class GlassSwitch(
    ctx: Context, initial: Boolean,
    private val onColor: Int, private val offColor: Int,
    private val onChange: (Boolean) -> Unit
) : View(ctx) {
    private var on = initial
    private var pos = if (initial) 1f else 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    init {
        isClickable = true
        setOnClickListener {
            on = !on
            ValueAnimator.ofFloat(pos, if (on) 1f else 0f).apply {
                duration = 140
                addUpdateListener { pos = it.animatedValue as Float; invalidate() }
                start()
            }
            onChange(on)
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.argb(ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = h / 2f
        track.color = mix(offColor, onColor, pos)
        c.drawRoundRect(0f, 0f, w, h, r, r, track)
        val kr = r - h * 0.14f
        c.drawCircle(r + (w - 2f * r) * pos, r, kr, knob)
    }
}
