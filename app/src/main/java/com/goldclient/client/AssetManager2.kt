package com.goldclient.client

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Verifica y extrae los assets empaquetados a /Android/data/com.goldclient.client/files/ */
object AssetManager2 {
    private const val VERSION_FILE = ".gold_assets_version"
    private const val ASSETS_VERSION = "1"

    fun workDir(ctx: Context): File =
        ctx.getExternalFilesDir(null) ?: File(ctx.filesDir, "ext").also { it.mkdirs() }

    fun isUpToDate(ctx: Context): Boolean {
        val f = File(workDir(ctx), VERSION_FILE)
        return f.exists() && f.readText().trim() == ASSETS_VERSION
    }

    /** Copia recursiva desde assets/cosmetics ; devuelve cantidad de archivos copiados. */
    fun extractAll(ctx: Context, onFile: (String, Int) -> Unit): Int {
        val out = workDir(ctx)
        var count = 0
        fun walk(path: String) {
            val list = ctx.assets.list(path) ?: return
            if (list.isEmpty()) {
                val dest = File(out, path)
                dest.parentFile?.mkdirs()
                ctx.assets.open(path).use { i -> FileOutputStream(dest).use { o -> i.copyTo(o) } }
                count++; onFile(path, count)
            } else list.forEach { walk("$path/$it") }
        }
        walk("cosmetics")
        File(out, VERSION_FILE).writeText(ASSETS_VERSION)
        return count
    }

    fun abi(): String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
}
