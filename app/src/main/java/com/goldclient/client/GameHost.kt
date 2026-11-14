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

    /** "gold32": instala libgold32.so (32 bits) en el hueco libgnustl_unwind.so de la carpeta privada jogo32 para que el emulador la cargue dentro del juego. */
    fun guestPatch(ctx: Context) = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE).getBoolean("guest_patch", false)
    fun setGuestPatch(ctx: Context, on: Boolean) = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE).edit().putBoolean("guest_patch", on).apply()

    private fun payloadFile(ctx: Context) = File(File(ctx.filesDir, "jogo32"), "libgnustl_unwind.so")
    fun isPatched(ctx: Context) = payloadFile(ctx).exists()

    /** Lee libgold32.so (armeabi-v7a) del propio APK del launcher. */
    private fun payloadBytes(ctx: Context): ByteArray? = try {
        ZipFile(ctx.applicationInfo.sourceDir).use { z -> z.getEntry("lib/armeabi-v7a/libgold32.so")?.let { e -> z.getInputStream(e).use { it.readBytes() } } }
    } catch (_: Exception) { null }

    /** Resumen legible del estado de la prueba (se muestra en la pantalla Log). */
    fun status(ctx: Context): String {
        val dir = File(ctx.filesDir, "jogo32")
        val so = File(dir, "libminecraftpe.so")
        if (!guestStage(ctx)) return "jogo32 apagado"
        if (!so.exists()) return "jogo32 ON · aun no hay libreria extraida: abre el juego una vez"
        val crc = File(dir, "libminecraftpe.so.crc").exists()
        return "jogo32 ON · libminecraftpe.so=${so.length()} bytes · crc=${if (crc) "si" else "no"} · gold32 pedido=${guestPatch(ctx)} · instalado=${isPatched(ctx)} · carga util en el APK=${payloadBytes(ctx)?.size ?: "NO"}"
    }

    /**
     * El emulador extrae el juego de 32 bits del APK a <filesDir>/jogo32 por su cuenta y deja un .crc junto a cada libreria;
     * si falta el .crc, vuelve a extraer y pisa lo que haya. Por eso aqui NO copiamos nada: esperamos a que exista el .crc
     * (despues de un primer arranque) y solo entonces aplicamos o quitamos el parche de prueba sin tocar el .crc.
     */
    private fun syncGuest(ctx: Context, sources: List<String>, stamp: Long) {
        val dir = File(ctx.filesDir, "jogo32")
        if (!guestStage(ctx)) { if (dir.exists()) dir.deleteRecursively(); return }
        val so = File(dir, "libminecraftpe.so")
        if (!so.exists() || !File(dir, "libminecraftpe.so.crc").exists()) return   // espera al primer arranque (el emulador crea sus archivos)
        val f = payloadFile(ctx)
        try {
            if (guestPatch(ctx)) {
                val bytes = payloadBytes(ctx) ?: return
                if (!f.exists() || f.length() != bytes.size.toLong()) { f.delete(); f.writeBytes(bytes) }
            } else if (f.exists()) f.delete()
        } catch (_: Exception) {}
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
