package com.goldclient.client

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** Prepara lo que necesita GameActivity a partir del Minecraft ya instalado (que no se modifica). */
object GameHost {
    const val MC_PKG = "com.mojang.minecraftpe"
    private const val EMU_ENTRY = "lib/arm64-v8a/libemu32.so"

    fun emuFile(ctx: Context) = File(File(ctx.filesDir, "gold"), "libemu32.so")

    /** Devuelve null si todo esta listo, o el mensaje de error para mostrar. */
    fun prepare(ctx: Context): String? {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return "Este telefono no es de 64 bits (arm64-v8a)"
        val pm = ctx.packageManager
        val ai = try { pm.getApplicationInfo(MC_PKG, 0) } catch (e: Exception) { return "Minecraft PE no esta instalado" }
        val stamp = try { pm.getPackageInfo(MC_PKG, 0).lastUpdateTime } catch (e: Exception) { 0L }
        val prefs = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE)
        val out = emuFile(ctx)
        if (out.exists() && prefs.getLong("emu_stamp", -1L) == stamp) return null

        val sources = ArrayList<String>()
        sources.add(ai.sourceDir)
        ai.splitSourceDirs?.let { sources.addAll(it) }
        for (p in sources) {
            if (extract(p, out)) { prefs.edit().putLong("emu_stamp", stamp).apply(); return null }
        }
        return "Este Minecraft no trae libemu32 (arm64-v8a). Necesitas el APK de 64 bits con emulador."
    }

    private fun extract(zipPath: String, out: File): Boolean {
        return try {
            ZipFile(zipPath).use { z ->
                val e = z.getEntry(EMU_ENTRY) ?: return false
                out.parentFile?.mkdirs()
                if (out.exists()) { out.setWritable(true); out.delete() }
                z.getInputStream(e).use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } }
                out.setReadOnly()
                true
            }
        } catch (e: Exception) { false }
    }
}
