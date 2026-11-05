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
        val sources = ArrayList<String>()
        sources.add(ai.sourceDir)
        ai.splitSourceDirs?.let { sources.addAll(it) }

        if (out.exists() && prefs.getLong("emu_stamp", -1L) == stamp) { syncGuest(ctx, sources, stamp); return null }
        for (p in sources) {
            if (extract(p, out)) { prefs.edit().putLong("emu_stamp", stamp).apply(); syncGuest(ctx, sources, stamp); return null }
        }
        return "Este Minecraft no trae libemu32 (arm64-v8a). Necesitas el APK de 64 bits con emulador."
    }

    /**
     * Experimento: copia las 3 librerias del juego de 32 bits a <filesDir>/jogo32 (copia privada del launcher; el Minecraft
     * instalado no se toca). Solo si el modo de prueba esta activado; apagado, borra la carpeta y todo queda como siempre.
     */
    fun guestStage(ctx: Context) = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE).getBoolean("guest_stage", false)
    fun setGuestStage(ctx: Context, on: Boolean) = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE).edit().putBoolean("guest_stage", on).apply()

    private fun syncGuest(ctx: Context, sources: List<String>, stamp: Long) {
        val prefs = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE)
        val dir = File(ctx.filesDir, "jogo32")
        if (!guestStage(ctx)) { if (dir.exists()) dir.deleteRecursively(); prefs.edit().remove("guest_stamp").apply(); return }
        if (dir.exists() && prefs.getLong("guest_stamp", -1L) == stamp) return
        dir.deleteRecursively(); dir.mkdirs()
        for (p in sources) {
            try {
                ZipFile(p).use { z ->
                    for (n in listOf("libminecraftpe.so", "libfmod.so", "libgnustl_shared.so")) {
                        val e = z.getEntry("assets/jogo32/$n") ?: continue
                        z.getInputStream(e).use { i -> FileOutputStream(File(dir, n)).use { o -> i.copyTo(o) } }
                    }
                }
            } catch (_: Exception) {}
        }
        prefs.edit().putLong("guest_stamp", stamp).apply()
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
