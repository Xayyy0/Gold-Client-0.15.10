package com.goldclient.client

import android.app.Activity
import android.app.NativeActivity
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import dalvik.system.DexClassLoader

/**
 * NativeActivity del launcher que ejecuta el Minecraft instalado SIN modificarlo:
 *  - los assets salen del APK de Minecraft (getAssets)
 *  - las clases com.emu32.* (teclado, skins, almacenamiento...) se cargan del mismo APK (getClassLoader)
 *  - libgcore (lib_name) carga libemu32 y dibuja el menu Gold encima.
 */
class GameActivity : NativeActivity() {

    private class GameLoader(dex: String, opt: String, lib: String?, parent: ClassLoader, private val emu: String)
        : DexClassLoader(dex, opt, lib, parent) {
        // si el codigo Java del juego pide System.loadLibrary("emu32"), usa la copia que preparo el launcher
        override fun findLibrary(name: String): String? = if (name == "emu32") emu else super.findLibrary(name)
    }

    private var mcCtx: Context? = null
    private var loader: ClassLoader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val ai = packageManager.getApplicationInfo(GameHost.MC_PKG, 0)
            mcCtx = createPackageContext(GameHost.MC_PKG, Context.CONTEXT_IGNORE_SECURITY)
            loader = GameLoader(ai.sourceDir, codeCacheDir.absolutePath, applicationInfo.nativeLibraryDir,
                super.getClassLoader(), GameHost.emuFile(this).absolutePath)
        } catch (e: Exception) {
            Log.e("GCORE", "No pude preparar Minecraft: ${e.message}")
        }
        super.onCreate(savedInstanceState)
    }

    override fun getAssets(): AssetManager = mcCtx?.assets ?: super.getAssets()
    override fun getClassLoader(): ClassLoader = loader ?: super.getClassLoader()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // igual que com.emu32.Atividade: el resultado de la galeria va a Skin.resultado
        try {
            val l = loader
            if (l != null) {
                val pedido = l.loadClass("com.emu32.Atividade").getField("PEDIDO_IMAGEM").getInt(null)
                if (requestCode == pedido) {
                    l.loadClass("com.emu32.Skin")
                        .getMethod("resultado", Activity::class.java, Int::class.javaPrimitiveType, Intent::class.java)
                        .invoke(null, this, resultCode, data)
                }
            }
        } catch (e: Exception) { Log.e("GCORE", "Skin.resultado fallo: ${e.message}") }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
