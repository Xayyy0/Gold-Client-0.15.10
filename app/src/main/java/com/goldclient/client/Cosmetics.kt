package com.goldclient.client

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Cosmeticos por textura: pinta capas sobre la skin de 64x64 (sombrero = capa de la cabeza, capa y alas = capa de la espalda).
 * La skin se manda al servidor al entrar, asi que otros jugadores tambien la ven.
 */
object Cosmetics {
    private val p = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private fun r(c: Canvas, x: Int, y: Int, w: Int, h: Int, color: Int) {
        p.color = color; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }
    private fun col(s: String) = Color.parseColor(s)

    fun compose(base: Bitmap, eq: Map<String, String>): Bitmap {
        val out = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(base, 0f, 0f, null)
        cape(c, eq["Capes"] ?: "None")
        wings(c, eq["Wings"] ?: "None")
        hat(c, eq["Hat"] ?: "None")
        return out
    }

    // capa de la cabeza ("hat"): cara superior (40,0) y laterales (32,8) (40,8) (48,8) (56,8)
    private fun hat(c: Canvas, name: String) {
        val gold = col("#F5B700"); val light = col("#FFE680")
        when (name) {
            "Crown" -> {
                for (x in intArrayOf(32, 40, 48, 56)) r(c, x, 8, 8, 2, gold)
                for (x in intArrayOf(32, 40, 48, 56)) { r(c, x, 8, 1, 1, gold); r(c, x + 2, 7, 1, 1, gold) }
                r(c, 40, 0, 8, 1, gold); r(c, 40, 7, 8, 1, gold); r(c, 40, 0, 1, 8, gold); r(c, 47, 0, 1, 8, gold)
                r(c, 43, 8, 2, 1, col("#D32F2F"))
            }
            "Halo" -> {
                r(c, 40, 0, 8, 1, light); r(c, 40, 7, 8, 1, light); r(c, 40, 0, 1, 8, light); r(c, 47, 0, 1, 8, light)
                for (x in intArrayOf(32, 40, 48, 56)) r(c, x, 8, 8, 1, light)
            }
            "Ears" -> {
                val d = col("#2B2B2B"); val pink = col("#E58FA6")
                r(c, 40, 0, 2, 2, d); r(c, 46, 0, 2, 2, d)
                r(c, 40, 8, 2, 1, d); r(c, 46, 8, 2, 1, d); r(c, 56, 8, 2, 1, d); r(c, 62, 8, 2, 1, d)
                r(c, 40, 0, 1, 1, pink); r(c, 47, 0, 1, 1, pink)
            }
        }
    }

    // espalda de la chaqueta (capa 2 del cuerpo): (32,36) de 8x12
    private fun cape(c: Canvas, name: String) {
        val x = 32; val y = 36
        fun body(fill: Int, border: Int) { r(c, x, y, 8, 12, border); r(c, x + 1, y + 1, 6, 10, fill) }
        when (name) {
            "Classic" -> body(col("#B02020"), col("#701010"))
            "Royal" -> { body(col("#5B2A86"), col("#F5B700")); r(c, x + 3, y + 4, 2, 2, col("#F5B700")); r(c, x + 3, y + 7, 2, 1, col("#F5B700")) }
            "Dragon" -> { body(col("#1F5A2E"), col("#0F2E18")); for (i in 0 until 5) r(c, x + 1 + (i % 2) * 2, y + 2 + i * 2, 2, 1, col("#B02020")) }
            "Samurai" -> { body(col("#EDEDED"), col("#B0B0B0")); r(c, x + 2, y + 5, 4, 2, col("#C62828")); r(c, x + 3, y + 4, 2, 4, col("#C62828")) }
            "Phoenix" -> {
                r(c, x, y, 8, 12, col("#7A2E10"))
                for (i in 0 until 10) {
                    val t = i / 9f
                    val rr = (255 + (232 - 255) * t).toInt(); val gg = (213 + (80 - 213) * t).toInt(); val bb = (74 + (28 - 74) * t).toInt()
                    r(c, x + 1, y + 1 + i, 6, 1, Color.rgb(rr, gg, bb))
                }
            }
        }
    }

    // alas pintadas sobre la espalda (encima de la capa): cada ala = (x inicial, ancho) por fila, dejando 2 columnas centrales libres
    private fun wings(c: Canvas, name: String) {
        val y0 = 37
        val rows = when (name) {
            "Angel" -> arrayOf(intArrayOf(34, 1), intArrayOf(33, 2), intArrayOf(32, 3), intArrayOf(32, 3),
                               intArrayOf(32, 2), intArrayOf(32, 2), intArrayOf(32, 1), intArrayOf(32, 1))
            "Dragon" -> arrayOf(intArrayOf(34, 1), intArrayOf(32, 3), intArrayOf(33, 2), intArrayOf(32, 3),
                                intArrayOf(33, 1), intArrayOf(32, 2), intArrayOf(33, 1), intArrayOf(32, 1))
            else -> return
        }
        val fill = if (name == "Angel") col("#F4F4F4") else col("#7A0F0F")
        val tip = if (name == "Angel") col("#CFCFCF") else col("#200808")
        for (i in rows.indices) {
            val xl = rows[i][0]; val w = rows[i][1]
            val xr = 72 - xl - w                       // espejo
            r(c, xl, y0 + i, w, 1, fill); r(c, xr, y0 + i, w, 1, fill)
            r(c, xl, y0 + i, 1, 1, tip); r(c, xr + w - 1, y0 + i, 1, 1, tip)
        }
    }

    /** Vista de frente o de espalda del personaje (16x32 px) escalada con pixeles nitidos. */
    fun view(skin: Bitmap, back: Boolean, scale: Int = 10): Bitmap {
        val o = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888)
        val c = Canvas(o)
        fun part(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
            c.drawBitmap(skin, Rect(sx, sy, sx + w, sy + h), Rect(dx, dy, dx + w, dy + h), null)
        }
        if (!back) {
            part(4, 20, 4, 12, 4, 20); part(20, 52, 4, 12, 8, 20)
            part(4, 36, 4, 12, 4, 20); part(4, 52, 4, 12, 8, 20)
            part(20, 20, 8, 12, 4, 8); part(20, 36, 8, 12, 4, 8)
            part(44, 20, 4, 12, 0, 8); part(36, 52, 4, 12, 12, 8)
            part(44, 36, 4, 12, 0, 8); part(52, 52, 4, 12, 12, 8)
            part(8, 8, 8, 8, 4, 0); part(40, 8, 8, 8, 4, 0)
        } else {
            part(28, 52, 4, 12, 4, 20); part(12, 20, 4, 12, 8, 20)
            part(12, 52, 4, 12, 4, 20); part(12, 36, 4, 12, 8, 20)
            part(32, 20, 8, 12, 4, 8); part(32, 36, 8, 12, 4, 8)
            part(44, 52, 4, 12, 0, 8); part(52, 20, 4, 12, 12, 8)
            part(60, 52, 4, 12, 0, 8); part(52, 36, 4, 12, 12, 8)
            part(24, 8, 8, 8, 4, 0); part(56, 8, 8, 8, 4, 0)
        }
        return Bitmap.createScaledBitmap(o, 16 * scale, 32 * scale, false)
    }
}
