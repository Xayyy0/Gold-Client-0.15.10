package com.goldclient.client

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // ---------- paleta ----------
    private val BG = Color.parseColor("#0B0B0D")
    private val GLASS = Color.parseColor("#1A1A1F")
    private val GLASS_ON = Color.parseColor("#2A2A31")
    private val LINE = Color.parseColor("#3A3A42")
    private val GOLD = Color.parseColor("#F5B700")
    private val TXT = Color.parseColor("#EDEDED")
    private val MUTED = Color.parseColor("#8A8A94")

    // ---------- datos ----------
    private val categories = linkedMapOf(
        "PvP" to ("⚔" to listOf("KeyStrokes", "CPS Counter", "Reach Display", "Armor Status", "Potion Status", "Old Animations")),
        "HUD" to ("▤" to listOf("FPS Counter", "Ping Counter", "Direction HUD")),
        "Visuals" to ("✦" to listOf("FullBright", "Smooth Zoom", "Motion Blur", "Particle Limiter")),
        "Player" to ("»" to listOf("Show Capes", "Show Wings", "Show Hats", "Show Trails")),
        "Misc" to ("≡" to listOf("FPS Uncap", "Fast Math", "Chunk Optimizer", "RAM Cleaner"))
    )
    private data class Item(val name: String, val price: Int)
    private val wardrobe = linkedMapOf(
        "Capes" to listOf(Item("None", 0), Item("Classic", 0), Item("Royal", 400), Item("Dragon", 400), Item("Samurai", 480), Item("Phoenix", 1100)),
        "Wings" to listOf(Item("None", 0), Item("Angel", 600), Item("Dragon", 800)),
        "Trails" to emptyList<Item>(),
        "Hat" to listOf(Item("None", 0), Item("Halo", 350), Item("Crown", 700), Item("Ears", 250)),
        "Mace" to emptyList<Item>(), "Emotes" to emptyList<Item>(), "Badges" to emptyList<Item>()
    )

    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("gold", MODE_PRIVATE) }
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var btn: View

    // ---------- helpers de UI ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun box(fill: Int, stroke: Int = LINE, r: Int = 12) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(r).toFloat(); setStroke(dp(1), stroke)
    }
    private fun tv(text: String, size: Float = 14f, color: Int = TXT, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun hbox() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun chip(text: String, on: Boolean = false, click: (() -> Unit)? = null) = tv(text, 13f, if (on) GOLD else TXT).apply {
        setPadding(dp(14), dp(8), dp(14), dp(8)); background = box(if (on) GLASS_ON else GLASS, if (on) GOLD else LINE, 10)
        click?.let { setOnClickListener { _ -> it() } }
    }
    private fun lp(w: Int, h: Int, weight: Float = 0f, m: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(m, m, m, m) }

    // ---------- estado guardado ----------
    // Los interruptores se guardan en gold_modules.cfg (el mismo archivo que lee el menu dentro del juego).
    private fun cfgFile() = File(AssetManager2.workDir(this), "gold_modules.cfg")
    private fun loadCfg(): MutableMap<String, Boolean> {
        val m = linkedMapOf<String, Boolean>()
        try { cfgFile().forEachLine { l -> val i = l.indexOf('='); if (i > 0) m[l.substring(0, i)] = l.substring(i + 1).trim() != "0" } } catch (_: Exception) {}
        return m
    }
    private fun isOnIn(m: Map<String, Boolean>, k: String) = m[k] ?: (k == "FPS Counter" || k == "FPS Uncap")
    private fun setOn(k: String, v: Boolean) {
        val m = loadCfg(); m[k] = v
        try { cfgFile().writeText(m.entries.joinToString("") { "${it.key}=${if (it.value) 1 else 0}\n" }) } catch (_: Exception) {}
    }
    private fun owned(cat: String, n: String) = prefs.getBoolean("o_${cat}_$n", false)
    private fun equipped(cat: String) = prefs.getString("e_$cat", "None") ?: "None"
    private fun shards() = prefs.getInt("shards", 500)

    private fun writeCfg() = io.execute {
        try {
            val sb = StringBuilder()
            wardrobe.keys.forEach { sb.append("$it=${equipped(it)}\n") }
            File(AssetManager2.workDir(this), "gold_cosmetics.cfg").writeText(sb.toString())
        } catch (_: Exception) {}
    }

    // ---------- ciclo de vida ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ds = resources.displayMetrics.widthPixels / Spec.DW
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#040406")) }
        frame.addView(ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_XY; setImageDrawable(homeBackground()) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        content = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))

        logView = ptv("", 20f * ds, Color.parseColor("#6FCF97")).apply { typeface = Typeface.MONOSPACE; maxLines = 2 }
        status = ptv("", 20f * ds, Color.parseColor("#A8A8B3"))
        btn = FrameLayout(this).apply {
            background = boxPx(Color.parseColor("#B015151A"), Color.parseColor("#77FFFFFF"), 26f * ds, (1.5f * ds).toInt().coerceAtLeast(1))
            isClickable = true; isFocusable = true
            val ib = Spec.ICON.getValue("ic_launch")
            addView(iconView("ic_launch", ib, ds), FrameLayout.LayoutParams((ib[2] * ds).toInt(), (ib[3] * ds).toInt(), Gravity.START or Gravity.CENTER_VERTICAL)
                .apply { leftMargin = ((ib[0] - 44f) * ds).toInt() })
            addView(ptv("LAUNCH 0.15.10 CORE", 30f * ds, Color.parseColor("#E6E6EA")).apply { letterSpacing = 0.12f },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL)
                    .apply { leftMargin = ((330f - 44f) * ds).toInt() })
            setOnClickListener { launchSequence() }
        }
        setContentView(frame)
        showHome(); refreshStatus(); writeCfg()
    }

    private var onHome = true
    private fun swap(v: View, pad: Boolean = true) {
        onHome = !pad
        if (pad) v.setPadding(dp(14), dp(14), dp(14), dp(14))
        content.removeAllViews(); content.addView(v, FrameLayout.LayoutParams(-1, -1))
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() { if (!onHome) showHome() else super.onBackPressed() }

    private fun boxPx(fill: Int, stroke: Int, rPx: Float, strokePx: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = rPx; setStroke(strokePx, stroke)
    }
    private fun ptv(text: String, px: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_PX, px); setTextColor(color); includeFontPadding = false
        typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
    }
    private fun iconView(name: String, spec: FloatArray, ds: Float) = ImageView(this).apply {
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id != 0) setImageResource(id)
        scaleType = ImageView.ScaleType.FIT_XY
    }

    // ---------- HOME ----------
    private fun homeBackground(): Drawable {
        val id = resources.getIdentifier("bg_home", "drawable", packageName)
        if (id != 0) androidx.core.content.ContextCompat.getDrawable(this, id)?.let { return it }
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#050507"), Color.parseColor("#0C0A06"), Color.parseColor("#050507")))
        val glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = dp(380).toFloat()
            setGradientCenter(0.5f, 0.38f); colors = intArrayOf(Color.parseColor("#55F5B700"), Color.parseColor("#00000000"))
        }
        return LayerDrawable(arrayOf(base, glow))
    }

    private fun ver(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    private fun showHome() {
        val ds = resources.displayMetrics.widthPixels / Spec.DW
        val line = Color.parseColor("#55FFFFFF")
        val fill = Color.parseColor("#B01A1A1F")
        val strokePx = (1.5f * ds).toInt().coerceAtLeast(1)
        val d = DesignLayout(this)

        // titulo y subtitulo
        d.addText(ptv("Gold", 96f * ds, Color.WHITE, true).apply { setShadowLayer(14f * ds, 0f, 0f, Color.parseColor("#99000000")) }, 52f, 660f, 520f)
        d.addText(ptv("PvP Client · Minecraft PE 0.15.10", 22f * ds, Color.parseColor("#C9C9D2")), 55f, 703f, 800f)

        // las 5 fichas (44..902 x 784..941) con efecto de lupa
        val names = categories.keys.toList()
        val ids = listOf("ic_pvp", "ic_hud", "ic_visuals", "ic_player", "ic_misc")
        val half = (7.25f * ds).toInt()
        val row = HoverRow(this, resources.displayMetrics.density, fill, Color.parseColor("#CC55555D"), line, Color.parseColor("#DDFFFFFF")) { i ->
            showCategory(names[i])
        }
        names.forEachIndexed { i, name ->
            val ib = Spec.ICON.getValue(ids[i])
            val tile = FrameLayout(this).apply {
                background = boxPx(fill, line, 22f * ds, strokePx)
                addView(iconView(ids[i], ib, ds).apply { translationY = ((ib[1] + ib[3] / 2f) - 862.5f) * ds },
                    FrameLayout.LayoutParams((ib[2] * ds).toInt(), (ib[3] * ds).toInt(), Gravity.CENTER))
                addView(ptv(name, 22f * ds, Color.parseColor("#EDEDED")).apply { translationY = 40.5f * ds },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
            row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(half, 0, half, 0) })
        }
        d.add(row, 44f - 7.25f, 784f, 858f + 14.5f, 157f)

        // fila de botones: x, ancho, icono, inicio del texto
        data class Chip(val label: String, val x: Float, val w: Float, val icon: String, val textX: Float, val click: () -> Unit)
        val chips = listOf(
            Chip("Wardrobe", 52f, 211f, "ic_wardrobe", 131f) { showWardrobe() },
            Chip("Search", 279f, 178f, "ic_search", 354f) { toast("Search: próximamente") },
            Chip("Friends", 472f, 177f, "ic_friends", 546f) { toast("Friends: próximamente") },
            Chip("Profiles", 664f, 175f, "ic_profiles", 734f) { toast("Profiles: próximamente") }
        )
        for (c in chips) {
            val ib = Spec.ICON.getValue(c.icon)
            val chip = FrameLayout(this).apply {
                background = boxPx(fill, line, 24f * ds, strokePx)
                isClickable = true
                addView(iconView(c.icon, ib, ds).apply { translationY = ((ib[1] + ib[3] / 2f) - 1061.5f) * ds },
                    FrameLayout.LayoutParams((ib[2] * ds).toInt(), (ib[3] * ds).toInt(), Gravity.START or Gravity.CENTER_VERTICAL)
                        .apply { leftMargin = ((ib[0] - c.x) * ds).toInt() })
                addView(ptv(c.label, 22f * ds, Color.parseColor("#EDEDED")),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL)
                        .apply { leftMargin = ((c.textX - c.x) * ds).toInt() })
                setOnClickListener { c.click() }
            }
            d.add(chip, c.x, 1020f, c.w, 83f)
        }
        // pastilla alta de la derecha del mockup: aqui abre el Log
        d.add(View(this).apply {
            background = boxPx(fill, line, 24f * ds, strokePx); isClickable = true
            setOnClickListener { showLog() }
        }, 853f, 985f, 48f, 151f)

        // estado y boton de lanzar (44..901 x 1507..1603)
        (logView.parent as? ViewGroup)?.removeView(logView)
        (status.parent as? ViewGroup)?.removeView(status)
        (btn.parent as? ViewGroup)?.removeView(btn)
        d.addText(logView, 44f, 1455f, 857f)
        d.addText(status, 44f, 1492f, 857f)
        d.add(btn, 44f, 1507f, 857f, 96f)

        swap(d.also { it.clipChildren = false }, false)
        d.layoutParams = FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
    }

    // ---------- skin con cosmeticos ----------
    // carpeta donde el juego lee custom.png (confirmado en el Log: games/com.mojang/minecraftpe/custom.png)
    private fun skinDir(): File = File(android.os.Environment.getExternalStorageDirectory(), "games/com.mojang/minecraftpe")
    private fun baseSkin(): Bitmap {
        val f = File(AssetManager2.workDir(this), "gold_skin_base.png")
        if (!f.exists()) {
            val src: java.io.InputStream? = try {
                val cur = File(skinDir(), "custom.png")
                if (cur.exists()) cur.inputStream()
                else createPackageContext(GameHost.MC_PKG, android.content.Context.CONTEXT_IGNORE_SECURITY).assets.open("skins/Base/steve.png")
            } catch (_: Exception) { null }
            if (src != null) src.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        return BitmapFactory.decodeFile(f.path)?.copy(Bitmap.Config.ARGB_8888, false)
            ?: Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    }
    private fun previewPair(cat: String, sel: String?): Pair<Bitmap, Bitmap> {
        val eq = HashMap<String, String>()
        wardrobe.keys.forEach { eq[it] = equipped(it) }
        if (sel != null) eq[cat] = sel
        val skin = Cosmetics.compose(baseSkin(), eq)
        return Pair(Cosmetics.view(skin, false), Cosmetics.view(skin, true))
    }
    private fun applySkin() = io.execute {
        try {
            val eq = HashMap<String, String>()
            wardrobe.keys.forEach { eq[it] = equipped(it) }
            val out = Cosmetics.compose(baseSkin(), eq)
            val dir = skinDir(); dir.mkdirs()
            val f = File(dir, "custom.png")
            java.io.FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            runOnUiThread { toast("Skin guardada: ${f.path}") }
        } catch (e: Exception) { runOnUiThread { toast("No pude guardar la skin: ${e.message}") } }
    }

    // ---------- estilo de vidrio compartido por las pantallas ----------
    private val GOLDC = Color.parseColor("#F5B700")
    private fun dsc() = resources.displayMetrics.widthPixels / Spec.DW
    private fun panel(r: Float = 22f) = boxPx(Color.parseColor("#A8141418"), Color.parseColor("#55FFFFFF"), r * dsc(), (1.5f * dsc()).toInt().coerceAtLeast(1))
    private fun glassChip(text: String, on: Boolean = false, click: () -> Unit): TextView {
        val ds = dsc()
        return ptv(text, 24f * ds, if (on) GOLDC else Color.parseColor("#EDEDED")).apply {
            setPadding((22 * ds).toInt(), (13 * ds).toInt(), (22 * ds).toInt(), (13 * ds).toInt())
            background = boxPx(if (on) Color.parseColor("#CC2A2A31") else Color.parseColor("#B01A1A1F"),
                if (on) GOLDC else Color.parseColor("#55FFFFFF"), 22f * ds, (1.5f * ds).toInt().coerceAtLeast(1))
            isClickable = true
            setOnClickListener { click() }
        }
    }
    private fun topBar(right: View? = null): View {
        val h = hbox().apply { setPadding(0, dp(6), 0, dp(10)) }
        h.addView(glassChip("‹  Back") { showHome() })
        h.addView(View(this), lp(0, 1, 1f))
        right?.let { h.addView(it) }
        return h
    }
    private fun titleRow(title: String, iconName: String?): View {
        val ds = dsc()
        val r = hbox().apply { setPadding(dp(2), dp(4), 0, dp(14)) }
        if (iconName != null) {
            val ib = Spec.ICON.getValue(iconName)
            r.addView(iconView(iconName, ib, ds), lp((ib[2] * ds * 0.9f).toInt(), (ib[3] * ds * 0.9f).toInt()))
        }
        r.addView(ptv(title, 46f * ds, Color.WHITE, true).apply { setPadding(if (iconName != null) (18 * ds).toInt() else 0, 0, 0, 0) })
        return r
    }

    // ---------- visor de log (para diagnosticar sin adb) ----------
    private fun showLog() {
        val ds = dsc()
        val v = vbox()
        val t = ptv("Cargando...", 20f * ds, Color.parseColor("#6FCF97")).apply { setTextIsSelectable(true) }
        val right = hbox()
        right.addView(glassChip("Copiar") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("gold-log", t.text))
            toast("Log copiado")
        }, lp(-2, -2, 0f, dp(3)))
        val tests = hbox()
        tests.addView(glassChip("jogo32: ${if (GameHost.guestStage(this)) "ON" else "OFF"}", GameHost.guestStage(this)) {
            GameHost.setGuestStage(this, !GameHost.guestStage(this))
            toast("jogo32 " + (if (GameHost.guestStage(this)) "ON" else "OFF") + ": se aplica al pulsar LAUNCH")
            showLog()
        }, lp(-2, -2, 0f, dp(3)))
        tests.addView(glassChip("parche: ${if (GameHost.guestPatch(this)) "ON" else "OFF"}", GameHost.guestPatch(this)) {
            GameHost.setGuestPatch(this, !GameHost.guestPatch(this))
            toast("parche " + (if (GameHost.guestPatch(this)) "ON" else "OFF") + ": solo actua con jogo32 ON")
            showLog()
        }, lp(-2, -2, 0f, dp(3)))
        v.addView(topBar(right))
        v.addView(titleRow("Log", null))
        tests.addView(glassChip("Probar parche") {
            GameHost.setGuestStage(this, true); GameHost.setGuestPatch(this, true)
            toast("Parche de prueba: abriendo el juego...")
            launchSequence()
        }, lp(-2, -2, 0f, dp(3)))
        v.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tests) },
            lp(-1, -2).apply { bottomMargin = dp(6) })
        v.addView(ptv(GameHost.status(this), 20f * ds, Color.parseColor("#C9C9D2")).apply { setPadding(dp(2), 0, 0, dp(8)) })
        val box = vbox().apply { background = panel(); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        box.addView(t)
        v.addView(ScrollView(this).apply { addView(box) }, lp(-1, 0, 1f))
        swap(v)
        io.execute {
            val raw = try { Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-s", "GCORE:V", "AndroidRuntime:E", "DEBUG:V", "libc:F")).inputStream.bufferedReader().readText() }
                      catch (e: Exception) { "No pude leer el log: ${e.message}" }
            val out = raw.lines().filter { it.isNotBlank() && !it.startsWith("---") }.takeLast(220).joinToString("\n")
            runOnUiThread { t.text = if (out.isBlank()) "(sin lineas GCORE: el juego no llego a abrir)" else out }
        }
    }

    // ---------- lista de modulos ----------
    private fun showCategory(name: String) {
        val ds = dsc()
        val ids = mapOf("PvP" to "ic_pvp", "HUD" to "ic_hud", "Visuals" to "ic_visuals", "Player" to "ic_player", "Misc" to "ic_misc")
        val working = setOf("FPS Counter", "FPS Uncap", "CPS Counter", "KeyStrokes")
        val v = vbox()
        v.addView(topBar())
        v.addView(titleRow(name, ids[name]))
        val cfg = loadCfg()
        val list = vbox().apply { background = panel(); setPadding((16 * ds).toInt(), (10 * ds).toInt(), (16 * ds).toInt(), (10 * ds).toInt()) }
        categories[name]!!.second.forEach { m ->
            val row = hbox().apply {
                background = boxPx(Color.parseColor("#55202026"), Color.parseColor("#22FFFFFF"), 18f * ds, 1)
                setPadding((20 * ds).toInt(), (14 * ds).toInt(), (20 * ds).toInt(), (14 * ds).toInt())
            }
            row.addView(ptv(m, 26f * ds, Color.parseColor("#EDEDED")), lp(0, -2, 1f))
            val ok = m in working
            row.addView(ptv(if (ok) "activo" else "pronto", 18f * ds, if (ok) GOLDC else Color.parseColor("#7C7C86"))
                .apply { setPadding(0, 0, (16 * ds).toInt(), 0) })
            row.addView(GlassSwitch(this, isOnIn(cfg, m), GOLDC, Color.parseColor("#44444C")) { setOn(m, it) },
                lp((76 * ds).toInt(), (40 * ds).toInt()))
            list.addView(row, lp(-1, -2).apply { setMargins(0, (6 * ds).toInt(), 0, (6 * ds).toInt()) })
        }
        v.addView(ScrollView(this).apply { addView(list) }, lp(-1, 0, 1f))
        swap(v)
    }

    // ---------- Wardrobe / Store / Rewards ----------
    private fun showWardrobe(tab: String = "Wardrobe", cat: String = "Capes", sel: String? = null) {
        val ds = dsc()
        val v = vbox()
        v.addView(topBar(glassChip("◆ ${shards()}", true) {}))
        v.addView(titleRow("Wardrobe", "ic_wardrobe"))

        val tabs = hbox()
        listOf("Wardrobe", "Store", "Rewards").forEach { t -> tabs.addView(glassChip(t, t == tab) { showWardrobe(t, cat) }, lp(-2, -2, 0f, dp(3))) }
        v.addView(tabs)
        if (tab == "Rewards") {
            v.addView(ptv("Rewards: próximamente", 26f * ds, Color.parseColor("#9A9AA5")).apply { setPadding(0, dp(30), 0, 0) })
            swap(v); return
        }
        val cats = hbox()
        wardrobe.keys.forEach { c -> cats.addView(glassChip(c, c == cat) { showWardrobe(tab, c) }, lp(-2, -2, 0f, dp(3))) }
        v.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(cats) },
            lp(-1, -2).apply { topMargin = dp(8) })

        val items = wardrobe[cat]!!.filter { it.name == "None" || (tab == "Store") != owned(cat, it.name) || it.price == 0 }
        val chosen = items.firstOrNull { it.name == sel } ?: items.firstOrNull { it.name == equipped(cat) } ?: items.firstOrNull()

        // vista previa
        val prev = vbox().apply { background = panel(26f); gravity = Gravity.CENTER; setPadding(dp(14), dp(18), dp(14), dp(18)) }
        if (chosen == null) {
            prev.addView(ptv("Próximamente", 30f * ds, Color.parseColor("#9A9AA5")))
        } else {
            try {
                val (fr, bk) = previewPair(cat, chosen.name)
                val pr = hbox().apply { gravity = Gravity.CENTER }
                pr.addView(ImageView(this).apply { setImageBitmap(fr) }, lp(dp(84), dp(168), 0f, dp(6)))
                pr.addView(ImageView(this).apply { setImageBitmap(bk) }, lp(dp(84), dp(168), 0f, dp(6)))
                prev.addView(pr)
            } catch (_: Exception) {}
            val have = chosen.price == 0 || owned(cat, chosen.name)
            val isEq = equipped(cat) == chosen.name
            prev.addView(ptv(chosen.name, 46f * ds, Color.WHITE, true))
            prev.addView(ptv(if (isEq) "Equipped" else if (have) "Owned" else "◆ ${chosen.price}", 24f * ds, if (have) GOLDC else Color.parseColor("#9A9AA5"))
                .apply { setPadding(0, dp(4), 0, dp(10)) })
            prev.addView(glassChip(if (isEq) "Equipped" else if (have) "Equip" else "Buy  ◆ ${chosen.price}", !isEq) {
                if (have) { prefs.edit().putString("e_$cat", chosen.name).apply(); writeCfg(); applySkin() }
                else if (shards() >= chosen.price) prefs.edit().putInt("shards", shards() - chosen.price).putBoolean("o_${cat}_${chosen.name}", true).apply()
                else toast("Shards insuficientes")
                showWardrobe(tab, cat, chosen.name)
            })
            prev.addView(ptv("Frente y espalda · se aplica a tu skin personalizada", 18f * ds, Color.parseColor("#6E6E78")).apply { setPadding(0, dp(10), 0, 0) })
        }
        v.addView(prev, lp(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(10) })

        // cuadricula
        val grid = GridLayout(this).apply { columnCount = 2 }
        val cw = (resources.displayMetrics.widthPixels - dp(28) - dp(16)) / 2
        items.forEach { it0 ->
            val have = it0.price == 0 || owned(cat, it0.name)
            val selected = chosen?.name == it0.name
            val card = vbox().apply {
                gravity = Gravity.CENTER
                background = boxPx(if (selected) Color.parseColor("#CC2A2A31") else Color.parseColor("#B01A1A1F"),
                    if (selected) GOLDC else Color.parseColor("#55FFFFFF"), 20f * ds, (1.5f * ds).toInt().coerceAtLeast(1))
                setPadding(dp(8), dp(16), dp(8), dp(16))
                addView(ptv(it0.name, 26f * ds, Color.parseColor("#EDEDED"), true))
                addView(ptv(if (equipped(cat) == it0.name) "Equipped" else if (have) "Owned" else "◆ ${it0.price}", 20f * ds,
                    if (have) GOLDC else Color.parseColor("#8A8A94")).apply { setPadding(0, dp(4), 0, 0) })
                setOnClickListener { showWardrobe(tab, cat, it0.name) }
            }
            grid.addView(card, GridLayout.LayoutParams().apply { width = cw; height = -2; setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        v.addView(ScrollView(this).apply { addView(grid) }, lp(-1, 0, 1f))
        swap(v)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- lanzamiento ----------
    private fun mcInstalled() = packageManager.getLaunchIntentForPackage(MC_PKG) != null
    private fun refreshStatus() {
        status.text = (if (mcInstalled()) "Minecraft PE detectado" else "Minecraft PE NO instalado") +
            " · assets: " + (if (AssetManager2.isUpToDate(this)) "listos" else "pendientes") + " · ${ver()}"
    }
    private fun log(s: String) = runOnUiThread { logView.append(s + "\n") }

    private fun launchSequence() {
        if (!mcInstalled()) { toast("Instala Minecraft PE (com.mojang.minecraftpe)"); return }
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            toast("Activa 'Acceso a todos los archivos' para Gold Client")
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:$packageName")))
            return
        }
        btn.isEnabled = false
        io.execute {
            try {
                log("[1/3] Preparando Minecraft instalado...")
                val err = GameHost.prepare(this)
                if (err != null) { log("ERROR: $err"); runOnUiThread { toast(err) }; return@execute }
                writeCfg()
                log("[2/3] Configuracion lista")
                log("[3/3] Abriendo Gold Client...")
                try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() } catch (_: Exception) {}
                runOnUiThread { startActivity(Intent(this, GameActivity::class.java)) }
            } catch (e: Exception) { log("ERROR: ${e.message}") }
            finally { runOnUiThread { btn.isEnabled = true; refreshStatus() } }
        }
    }

    override fun onDestroy() { super.onDestroy(); io.shutdown() }
    companion object { const val MC_PKG = "com.mojang.minecraftpe" }
}
