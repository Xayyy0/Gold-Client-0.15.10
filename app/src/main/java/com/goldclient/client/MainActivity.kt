package com.goldclient.client

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
        "PvP" to ("⚔" to listOf("KeyStrokes", "CPS Counter", "Reach Display", "Armor Status", "Potion Status", "Old Animations", "Crosshair")),
        "HUD" to ("▤" to listOf("FPS Counter", "Ping Counter", "Direction HUD")),
        "Visuals" to ("✦" to listOf("FullBright", "Smooth Zoom", "Motion Blur", "Particle Limiter")),
        "Player" to ("»" to listOf("Show Capes", "Show Hats", "Show Wings", "Show Trails")),
        "Misc" to ("≡" to listOf("FPS Uncap", "Fast Math", "Chunk Optimizer", "RAM Cleaner"))
    )
    private data class Item(val name: String, val price: Int)
    private val wardrobe = linkedMapOf(
        "Capes" to listOf(Item("None", 0), Item("Classic", 0), Item("Royal", 400), Item("Dragon", 400), Item("Samurai", 480), Item("Phoenix", 1100)),
        "Wings" to listOf(Item("None", 0), Item("Angel", 600), Item("Dragon", 800)),
        "Trails" to listOf(Item("None", 0), Item("Fire", 300), Item("Rainbow", 500), Item("Rings", 500)),
        "Hat" to listOf(Item("None", 0), Item("Halo", 350), Item("Crown", 700), Item("Ears", 250)),
        "Mace" to emptyList(), "Emotes" to emptyList(), "Badges" to emptyList()
    )

    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("gold", MODE_PRIVATE) }
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var btn: Button

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

    // ---------- estado guardado (también se vuelca a modules.cfg para libgcore) ----------
    private fun isOn(k: String) = prefs.getBoolean("m_$k", false)
    private fun setOn(k: String, v: Boolean) { prefs.edit().putBoolean("m_$k", v).apply(); writeCfg() }
    private fun owned(cat: String, n: String) = prefs.getBoolean("o_${cat}_$n", false)
    private fun equipped(cat: String) = prefs.getString("e_$cat", "None") ?: "None"
    private fun shards() = prefs.getInt("shards", 500)

    private fun writeCfg() = io.execute {
        try {
            val sb = StringBuilder()
            categories.values.forEach { it.second.forEach { m -> sb.append("module.$m=${if (isOn(m)) 1 else 0}\n") } }
            wardrobe.keys.forEach { sb.append("cosmetic.$it=${equipped(it)}\n") }
            File(AssetManager2.workDir(this), "modules.cfg").writeText(sb.toString())
        } catch (_: Exception) {}
    }

    // ---------- ciclo de vida ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox().apply { setBackgroundColor(BG); setPadding(dp(14), dp(10), dp(14), dp(10)) }
        content = FrameLayout(this)
        root.addView(content, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        logView = tv("", 11f, Color.parseColor("#6FCF97")).apply { typeface = Typeface.MONOSPACE; maxLines = 3 }
        status = tv("", 12f, MUTED)
        btn = Button(this).apply {
            text = "LAUNCH 0.15.10 CORE"; setTextColor(Color.parseColor("#1A1400")); setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(GOLD, Color.parseColor("#B38600")))
                .apply { cornerRadius = dp(12).toFloat() }
            setOnClickListener { launchSequence() }
        }
        root.addView(logView); root.addView(status)
        root.addView(btn, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), 0f, dp(2)))
        setContentView(root)
        showHome(); refreshStatus(); writeCfg()
    }

    private fun swap(v: View) { content.removeAllViews(); content.addView(v, FrameLayout.LayoutParams(-1, -1)) }

    // ---------- HOME ----------
    private fun showHome() {
        val v = vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
        v.addView(View(this), lp(1, 0, 1f))
        v.addView(tv("Gold", 40f, TXT, true).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.1f })
        v.addView(tv("PvP Client · Minecraft PE 0.15.10", 12f, MUTED).apply { setPadding(0, 0, 0, dp(22)) })

        val row = hbox()
        categories.forEach { (name, g) ->
            val tile = vbox().apply {
                gravity = Gravity.CENTER; background = box(GLASS, LINE, 16)
                addView(tv(g.first, 24f, GOLD)); addView(tv(name, 11f, TXT).apply { setPadding(0, dp(6), 0, 0) })
                setOnClickListener { showCategory(name) }
            }
            row.addView(tile, lp(0, dp(78), 1f, dp(3)))
        }
        v.addView(row, lp(-1, -2))

        val small = hbox().apply { gravity = Gravity.CENTER }
        small.addView(chip("Wardrobe") { showWardrobe() }, lp(-2, -2, 0f, dp(4)))
        small.addView(chip("Search") { toast("Search: próximamente") }, lp(-2, -2, 0f, dp(4)))
        small.addView(chip("Friends") { toast("Friends: próximamente") }, lp(-2, -2, 0f, dp(4)))
        small.addView(chip("Profiles") { toast("Profiles: próximamente") }, lp(-2, -2, 0f, dp(4)))
        small.addView(chip("Log") { showLog() }, lp(-2, -2, 0f, dp(4)))
        v.addView(small, lp(-2, -2).apply { topMargin = dp(18) })
        v.addView(View(this), lp(1, 0, 1.2f))
        v.addView(tv("Gold Client ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }} · Minecraft PE 0.15.10", 10f, MUTED))
        swap(v)
    }

    // ---------- visor de log (para diagnosticar sin adb) ----------
    private fun showLog() {
        val v = vbox()
        val top = hbox()
        top.addView(chip("‹ Back") { showHome() }, lp(-2, -2, 0f, dp(2)))
        val t = tv("Cargando...", 10f, Color.parseColor("#6FCF97")).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        top.addView(chip("Copiar") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("gold-log", t.text))
            toast("Log copiado")
        }, lp(-2, -2, 0f, dp(2)))
        v.addView(top)
        v.addView(ScrollView(this).apply { addView(t) }, lp(-1, 0, 1f).apply { topMargin = dp(8) })
        swap(v)
        io.execute {
            // solo nuestras lineas (GCORE) y errores graves: el resto del log del sistema es ruido
            val raw = try { Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-s", "GCORE:V", "AndroidRuntime:E", "DEBUG:V", "libc:F")).inputStream.bufferedReader().readText() }
                      catch (e: Exception) { "No pude leer el log: ${e.message}" }
            val out = raw.lines().filter { it.isNotBlank() && !it.startsWith("---") }.takeLast(150).joinToString("\n")
            runOnUiThread { t.text = if (out.isBlank()) "(sin lineas GCORE: el juego no llego a abrir)" else out }
        }
    }

    // ---------- lista de módulos ----------
    private fun showCategory(name: String) {
        val v = vbox()
        v.addView(chip("‹ Back") { showHome() }, lp(-2, -2, 0f, dp(2)))
        v.addView(tv(name, 22f, TXT, true).apply { setPadding(dp(4), dp(10), 0, dp(8)) })
        val list = vbox().apply { background = box(GLASS, LINE, 14); setPadding(dp(12), dp(6), dp(12), dp(6)) }
        categories[name]!!.second.forEach { m ->
            val r = hbox().apply { setPadding(0, dp(6), 0, dp(6)) }
            r.addView(tv(m, 15f), lp(0, -2, 1f))
            r.addView(Switch(this).apply {
                isChecked = isOn(m); setOnCheckedChangeListener { _, c -> setOn(m, c) }
            })
            list.addView(r)
        }
        v.addView(ScrollView(this).apply { addView(list) }, lp(-1, 0, 1f))
        swap(v)
    }

    // ---------- Wardrobe / Store / Rewards ----------
    private fun showWardrobe(tab: String = "Wardrobe", cat: String = "Capes") {
        val v = vbox()
        val top = hbox()
        top.addView(chip("‹ Back") { showHome() }, lp(-2, -2, 0f, dp(2)))
        listOf("Wardrobe", "Store", "Rewards").forEach { t ->
            top.addView(chip(t, t == tab) { showWardrobe(t, cat) }, lp(-2, -2, 0f, dp(2)))
        }
        top.addView(View(this), lp(0, 1, 1f))
        top.addView(tv("◆ ${shards()}", 13f, GOLD, true))
        v.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(top) })

        if (tab == "Rewards") {
            v.addView(tv("Rewards: próximamente", 15f, MUTED).apply { setPadding(0, dp(30), 0, 0) })
            swap(v); return
        }
        val body = hbox().apply { gravity = Gravity.TOP }
        val side = vbox().apply { background = box(GLASS, LINE, 12); setPadding(dp(6), dp(6), dp(6), dp(6)) }
        wardrobe.keys.forEach { c ->
            side.addView(tv(c, 13f, if (c == cat) GOLD else TXT).apply {
                setPadding(dp(10), dp(9), dp(14), dp(9))
                if (c == cat) background = box(GLASS_ON, GOLD, 8)
                setOnClickListener { showWardrobe(tab, c) }
            })
        }
        body.addView(side, lp(-2, -2, 0f, dp(2)))

        val grid = GridLayout(this).apply { columnCount = 2 }
        val items = wardrobe[cat]!!.filter { it.name == "None" || (tab == "Store") != owned(cat, it.name) || it.price == 0 }
        if (items.isEmpty()) grid.addView(tv("Próximamente", 14f, MUTED))
        items.forEach { it0 ->
            val have = it0.price == 0 || owned(cat, it0.name)
            val card = vbox().apply {
                gravity = Gravity.CENTER; background = box(GLASS, if (equipped(cat) == it0.name) GOLD else LINE, 12)
                setPadding(dp(8), dp(14), dp(8), dp(14))
                addView(tv(it0.name, 14f, TXT, true))
                addView(tv(if (equipped(cat) == it0.name) "Equipped" else if (have) "Owned" else "◆ ${it0.price}", 11f, if (have) GOLD else MUTED))
                setOnClickListener {
                    if (have) { prefs.edit().putString("e_$cat", it0.name).apply(); writeCfg() }
                    else if (shards() >= it0.price) {
                        prefs.edit().putInt("shards", shards() - it0.price).putBoolean("o_${cat}_${it0.name}", true).apply()
                    } else toast("Shards insuficientes")
                    showWardrobe(tab, cat)
                }
            }
            grid.addView(card, GridLayout.LayoutParams().apply { width = dp(120); height = -2; setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        body.addView(ScrollView(this).apply { addView(grid) }, lp(0, -1, 1f))
        v.addView(body, lp(-1, 0, 1f).apply { topMargin = dp(10) })
        v.addView(tv("Vista previa 3D con rotación y zoom: fase 4", 10f, MUTED))
        swap(v)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- lanzamiento ----------
    private fun mcInstalled() = packageManager.getLaunchIntentForPackage(MC_PKG) != null
    private fun refreshStatus() {
        status.text = (if (mcInstalled()) "Minecraft PE detectado" else "Minecraft PE NO instalado") +
            " · assets: " + (if (AssetManager2.isUpToDate(this)) "listos" else "pendientes") + " · ABI ${AssetManager2.abi()}"
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
