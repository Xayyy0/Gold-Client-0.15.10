// libgold32.so (armeabi-v7a): se ejecuta DENTRO del juego (codigo de 32 bits que corre el emulador).
// El launcher la deja en la carpeta privada jogo32 con el nombre libgnustl_unwind.so, el hueco opcional que el emulador intenta cargar.
// Sin STL ni excepciones: solo C y liblog.
#include <android/log.h>

#define TAG "GOLD32"

extern "C" {
__attribute__((visibility("default"))) int gold32_version(void) { return 1; }
}

__attribute__((constructor)) static void gold32_init(void) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "libgold32 cargada dentro del juego (guest ARM32, version %d)", gold32_version());
}
