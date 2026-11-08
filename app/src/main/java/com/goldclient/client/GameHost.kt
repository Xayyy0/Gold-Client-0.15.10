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

    /** Prueba: cambia 16 bytes de una zona sin uso de la copia de libminecraftpe.so, SIN cambiar su tamano, para ver si el emulador la acepta modificada. */
    fun guestPatch(ctx: Context) = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE).getBoolean("guest_patch", false)
    fun setGuestPatch(ctx: Context, on: Boolean) = ctx.getSharedPreferences("gold_host", Context.MODE_PRIVATE).edit().putBoolean("guest_patch", on).apply()

    private const val MARK = "GOLD-PATCH-TEST\n"   // 16 bytes

    /** Posicion de una zona que no se carga al ejecutar (la seccion .comment u otra sin ALLOC) para un parche de IGUAL tamano, o -1. */
    private fun safeOffset(so: File): Long {
        try {
            java.io.RandomAccessFile(so, "r").use { f ->
                val hdr = ByteArray(0x34); f.readFully(hdr)
                if (hdr[0].toInt() != 0x7f || hdr[1].toInt() != 'E'.code) return -1
                val h = java.nio.ByteBuffer.wrap(hdr).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val shoff = h.getInt(0x20).toLong() and 0xffffffffL
                val shent = h.getShort(0x2E).toInt() and 0xffff
                val shnum = h.getShort(0x30).toInt() and 0xffff
                for (i in 0 until shnum) {
                    f.seek(shoff + i.toLong() * shent)
                    val sh = ByteArray(40); f.readFully(sh)
                    val b = java.nio.ByteBuffer.wrap(sh).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val type = b.getInt(4); val flags = b.getInt(8)
                    val off = b.getInt(16).toLong() and 0xffffffffL; val size = b.getInt(20).toLong() and 0xffffffffL
                    if (type == 1 && (flags and 2) == 0 && size >= 16) return off
                }
            }
        } catch (_: Exception) {}
        return -1
    }

    fun isPatched(ctx: Context): Boolean {
        val so = File(File(ctx.filesDir, "jogo32"), "libminecraftpe.so")
        if (!so.exists()) return false
        val off = safeOffset(so)
        if (off < 0) return false
        return try {
            java.io.RandomAccessFile(so, "r").use { f ->
                f.seek(off); val b = ByteArray(16); f.readFully(b); b.contentEquals(MARK.toByteArray())
            }
        } catch (_: Exception) { false }
    }

    /** Resumen legible del estado de la prueba (se muestra en la pantalla Log). */
    fun status(ctx: Context): String {
        val dir = File(ctx.filesDir, "jogo32")
        val so = File(dir, "libminecraftpe.so")
        if (!guestStage(ctx)) return "jogo32 apagado"
        if (!so.exists()) return "jogo32 ON · aun no hay libreria extraida: abre el juego una vez"
        val crc = File(dir, "libminecraftpe.so.crc").exists()
        return "jogo32 ON · libminecraftpe.so=${so.length()} bytes · crc=${if (crc) "si" else "no"} · parche pedido=${guestPatch(ctx)} · aplicado=${isPatched(ctx)}"
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
        if (!so.exists() || !File(dir, "libminecraftpe.so.crc").exists()) return
        val patched = isPatched(ctx)
        try {
            if (guestPatch(ctx) && !patched) {
                val off = safeOffset(so)
                if (off >= 0) java.io.RandomAccessFile(so, "rw").use { it.seek(off); it.write(MARK.toByteArray()) }
            } else if (!guestPatch(ctx) && patched) {
                so.delete(); File(dir, "libminecraftpe.so.crc").delete()      // el emulador la vuelve a extraer limpia
            }
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
