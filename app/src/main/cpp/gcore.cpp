// Gold Client - libgcore.so (arm64-v8a)
// Lib de la GameActivity del launcher: carga libemu32.so del Minecraft instalado (sin modificarlo) y le pasa el control.
// Hooks por GOT-patch sobre los imports de libemu32.so (EGL + entrada tactil).
// Dibuja dentro del juego: contador de FPS, boton flotante "G" y el menu Gold (PvP/HUD/Visuals/Player/Misc).
#include <android/input.h>
#include <android/log.h>
#include <android/native_activity.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <string>
#include "font.h"

#define TAG "GCORE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ======================= Modulos =======================
struct ModDef { const char* name; int cat; };
static const char* CAT_NAMES[5] = {"PvP", "HUD", "Visuals", "Player", "Misc"};
static const ModDef MODS[] = {
    {"KeyStrokes", 0}, {"CPS Counter", 0}, {"Reach Display", 0}, {"Armor Status", 0}, {"Potion Status", 0}, {"Old Animations", 0},
    {"FPS Counter", 1}, {"Ping Counter", 1}, {"Direction HUD", 1},                       // 6,7,8
    {"FullBright", 2}, {"Smooth Zoom", 2}, {"Motion Blur", 2}, {"Particle Limiter", 2},
    {"Show Capes", 3}, {"Show Wings", 3}, {"Show Hats", 3}, {"Show Trails", 3},
    {"FPS Uncap", 4}, {"Fast Math", 4}, {"Chunk Optimizer", 4}, {"RAM Cleaner", 4},      // 17..
};
static const int NMOD = (int)(sizeof(MODS) / sizeof(MODS[0]));
static const int IDX_FPS = 6, IDX_UNCAP = 17;    // modulos ya conectados
static std::atomic<bool> g_on[32];

static std::atomic<bool>  g_open{false};
static std::atomic<int>   g_cat{0};
static std::atomic<int>   g_W{0}, g_H{0};
static std::atomic<float> g_fps{0.f};

// ======================= GOT patch =======================
struct HookDef { const char* name; void* repl; void** orig; int hits; };
struct DlCtx { const char* so; uintptr_t base; ElfW(Dyn)* dyn; };

static int phdrCb(struct dl_phdr_info* i, size_t, void* d) {
    auto* c = (DlCtx*)d;
    if (!i->dlpi_name || !strstr(i->dlpi_name, c->so)) return 0;
    c->base = i->dlpi_addr;
    for (int k = 0; k < i->dlpi_phnum; k++)
        if (i->dlpi_phdr[k].p_type == PT_DYNAMIC)
            c->dyn = (ElfW(Dyn)*)(i->dlpi_addr + i->dlpi_phdr[k].p_vaddr);
    return 1;
}

static bool patchGot(const char* so, HookDef* hooks, int n) {
    DlCtx c{so, 0, nullptr};
    dl_iterate_phdr(phdrCb, &c);
    if (!c.dyn) { LOGE("no encontre %s en memoria", so); return false; }
    auto fix = [&](uintptr_t p) { return p >= c.base ? p : c.base + p; };
    const ElfW(Sym)* symtab = nullptr; const char* strtab = nullptr;
    const ElfW(Rela)* jmprel = nullptr; size_t jmpsz = 0;
    const ElfW(Rela)* rela = nullptr;   size_t relasz = 0;
    for (ElfW(Dyn)* d = c.dyn; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
            case DT_SYMTAB:   symtab = (const ElfW(Sym)*)fix(d->d_un.d_ptr); break;
            case DT_STRTAB:   strtab = (const char*)fix(d->d_un.d_ptr); break;
            case DT_JMPREL:   jmprel = (const ElfW(Rela)*)fix(d->d_un.d_ptr); break;
            case DT_PLTRELSZ: jmpsz = d->d_un.d_val; break;
            case DT_RELA:     rela = (const ElfW(Rela)*)fix(d->d_un.d_ptr); break;
            case DT_RELASZ:   relasz = d->d_un.d_val; break;
        }
    }
    if (!symtab || !strtab) { LOGE("tablas dinamicas invalidas"); return false; }
    long pg = sysconf(_SC_PAGESIZE);
    auto scan = [&](const ElfW(Rela)* r, size_t bytes) {
        if (!r) return;
        for (size_t k = 0; k < bytes / sizeof(ElfW(Rela)); k++) {
            unsigned type = ELF64_R_TYPE(r[k].r_info);
            if (type != 1025 && type != 1026) continue;           // GLOB_DAT / JUMP_SLOT
            const char* nm = strtab + symtab[ELF64_R_SYM(r[k].r_info)].st_name;
            for (int h = 0; h < n; h++) {
                if (strcmp(nm, hooks[h].name) != 0) continue;
                void** slot = (void**)(c.base + r[k].r_offset);
                uintptr_t page = (uintptr_t)slot & ~(uintptr_t)(pg - 1);
                if (mprotect((void*)page, pg, PROT_READ | PROT_WRITE) != 0) { LOGE("mprotect fallo (%s)", nm); continue; }
                if (!*hooks[h].orig) *hooks[h].orig = *slot;
                *slot = hooks[h].repl;
                hooks[h].hits++;
            }
        }
    };
    scan(jmprel, jmpsz);
    scan(rela, relasz);
    for (int h = 0; h < n; h++) LOGI("hook %s: %d entradas GOT", hooks[h].name, hooks[h].hits);
    return true;
}

// ======================= Dibujo (scissor + glClear, sin shaders) =======================
static void rect(int x, int y, int w, int h) { glScissor(x, y, w, h); glClear(GL_COLOR_BUFFER_BIT); }
static void color(float r, float g, float b) { glClearColor(r, g, b, 1.f); }
// coordenadas "TL": origen arriba-izquierda (igual que los toques)
static void rectTL(int x, int y, int w, int h) { if (w > 0 && h > 0) rect(x, g_H - y - h, w, h); }

static void text(const char* s, int x, int y, int sc) {
    for (; *s; s++) {
        unsigned char ch = (unsigned char)*s;
        if (ch >= 32 && ch <= 126) {
            const uint8_t* gl = FONT[ch - 32];
            for (int row = 0; row < FONT_H; row++) {
                uint8_t m = gl[row]; int col = 0;
                while (col < FONT_W) {
                    if (m & (1 << (FONT_W - 1 - col))) {
                        int st = col;
                        while (col < FONT_W && (m & (1 << (FONT_W - 1 - col)))) col++;
                        rectTL(x + st * sc, y + row * sc, (col - st) * sc, sc);
                    } else col++;
                }
            }
        }
        x += FONT_W * sc;
    }
}
static int textW(const char* s, int sc) { return (int)strlen(s) * FONT_W * sc; }

// ----- contador de FPS (siete segmentos) -----
static void digit(int d, int x, int y, int s) {
    static const uint8_t M[10] = {0x3F,0x06,0x5B,0x4F,0x66,0x6D,0x7D,0x07,0x7F,0x6F};
    uint8_t m = M[d % 10];
    if (m & 1)  rectTL(x + s,   y,         2*s, s);
    if (m & 2)  rectTL(x + 3*s, y + s,     s, 2*s);
    if (m & 4)  rectTL(x + 3*s, y + 4*s,   s, 2*s);
    if (m & 8)  rectTL(x + s,   y + 6*s,   2*s, s);
    if (m & 16) rectTL(x,       y + 4*s,   s, 2*s);
    if (m & 32) rectTL(x,       y + s,     s, 2*s);
    if (m & 64) rectTL(x + s,   y + 3*s,   2*s, s);
}
static void drawFps(int s) {
    int v = (int)(g_fps.load() + 0.5f); if (v > 9999) v = 9999;
    int nd = v >= 1000 ? 4 : v >= 100 ? 3 : v >= 10 ? 2 : 1;
    int cell = 5 * s, x0 = 3 * s, y0 = 3 * s;
    color(0.f, 0.f, 0.f);
    rectTL(x0 - s, y0 - s, nd * cell + s, 9 * s);
    color(0.3f, 1.f, 0.4f);
    int div = 1; for (int i = 1; i < nd; i++) div *= 10;
    for (int i = 0; i < nd; i++, div /= 10) digit((v / div) % 10, x0 + i * cell, y0, s);
}

// ----- layout del boton y del menu -----
struct Lay { int sc, pad, bx, by, bs, px, py, pw, ph, tabY, tabH, tabW, listY, rowH; };
static Lay lay() {
    Lay l; int W = g_W, H = g_H;
    l.sc = std::max(1, H / 220); l.pad = 3 * l.sc;
    l.bs = 16 * l.sc; l.bx = W - l.bs - 3 * l.sc; l.by = 3 * l.sc;
    l.pw = W * 70 / 100; l.ph = H * 80 / 100; l.px = (W - l.pw) / 2; l.py = (H - l.ph) / 2;
    l.tabY = l.py + l.pad + 24 * l.sc + l.pad; l.tabH = 18 * l.sc;
    l.tabW = (l.pw - 2 * l.pad - 4 * l.pad) / 5;
    l.listY = l.tabY + l.tabH + l.pad; l.rowH = 18 * l.sc;
    return l;
}

static void drawButton(const Lay& l) {
    color(0.96f, 0.72f, 0.f);  rectTL(l.bx - l.sc, l.by - l.sc, l.bs + 2 * l.sc, l.bs + 2 * l.sc);
    color(0.07f, 0.07f, 0.09f); rectTL(l.bx, l.by, l.bs, l.bs);
    color(0.96f, 0.72f, 0.f);  text("G", l.bx + (l.bs - FONT_W * l.sc) / 2, l.by + (l.bs - FONT_H * l.sc) / 2, l.sc);
}

static void drawMenu(const Lay& l) {
    int sc = l.sc, pad = l.pad;
    color(0.96f, 0.72f, 0.f);   rectTL(l.px - sc, l.py - sc, l.pw + 2 * sc, l.ph + 2 * sc);
    color(0.07f, 0.07f, 0.09f); rectTL(l.px, l.py, l.pw, l.ph);
    color(0.96f, 0.72f, 0.f);   text("GOLD", l.px + pad, l.py + pad, 2 * sc);
    color(0.55f, 0.55f, 0.6f);  text("PvP Client 0.15.10", l.px + l.pw - pad - textW("PvP Client 0.15.10", sc), l.py + pad + 6 * sc, sc);

    for (int i = 0; i < 5; i++) {
        int x = l.px + pad + i * (l.tabW + pad); bool on = (i == g_cat);
        color(on ? 0.96f : 0.23f, on ? 0.72f : 0.23f, on ? 0.f : 0.27f); rectTL(x, l.tabY, l.tabW, l.tabH);
        color(on ? 0.16f : 0.12f, on ? 0.15f : 0.12f, on ? 0.10f : 0.14f); rectTL(x + sc, l.tabY + sc, l.tabW - 2 * sc, l.tabH - 2 * sc);
        if (on) color(0.96f, 0.72f, 0.f); else color(0.93f, 0.93f, 0.93f);
        text(CAT_NAMES[i], x + (l.tabW - textW(CAT_NAMES[i], sc)) / 2, l.tabY + (l.tabH - FONT_H * sc) / 2, sc);
    }
    int k = 0;
    for (int i = 0; i < NMOD; i++) {
        if (MODS[i].cat != g_cat) continue;
        int y = l.listY + k * l.rowH; k++;
        if (y + l.rowH > l.py + l.ph - pad) break;
        color(0.11f, 0.11f, 0.13f); rectTL(l.px + pad, y, l.pw - 2 * pad, l.rowH - sc);
        color(0.93f, 0.93f, 0.93f); text(MODS[i].name, l.px + 2 * pad, y + (l.rowH - FONT_H * sc) / 2, sc);
        int tw = 22 * sc, th = 10 * sc, tx = l.px + l.pw - 2 * pad - tw, ty = y + (l.rowH - sc - th) / 2;
        bool on = g_on[i];
        if (on) color(0.96f, 0.72f, 0.f); else color(0.27f, 0.27f, 0.3f);
        rectTL(tx, ty, tw, th);
        color(1.f, 1.f, 1.f); rectTL(on ? tx + tw - th + sc : tx + sc, ty + sc, th - 2 * sc, th - 2 * sc);
        if (i != IDX_FPS && i != IDX_UNCAP) { color(0.4f, 0.4f, 0.45f); text("soon", tx - textW("soon", sc) - pad, y + (l.rowH - FONT_H * sc) / 2, sc); }
    }
}

static void drawAll(EGLDisplay dpy, EGLSurface surf) {
    EGLint W = 0, H = 0;
    eglQuerySurface(dpy, surf, EGL_WIDTH, &W);
    eglQuerySurface(dpy, surf, EGL_HEIGHT, &H);
    if (W <= 0 || H <= 0) return;
    g_W = W; g_H = H;

    GLint fbo = 0, box[4] = {0}; GLfloat cc[4] = {0}; GLboolean cm[4] = {1,1,1,1};
    GLboolean scissorOn = glIsEnabled(GL_SCISSOR_TEST);
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbo);
    glGetIntegerv(GL_SCISSOR_BOX, box);
    glGetFloatv(GL_COLOR_CLEAR_VALUE, cc);
    glGetBooleanv(GL_COLOR_WRITEMASK, cm);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glEnable(GL_SCISSOR_TEST);

    Lay l = lay();
    if (g_on[IDX_FPS]) drawFps(l.sc);
    drawButton(l);
    if (g_open) drawMenu(l);

    glClearColor(cc[0], cc[1], cc[2], cc[3]);
    glColorMask(cm[0], cm[1], cm[2], cm[3]);
    glScissor(box[0], box[1], box[2], box[3]);
    if (!scissorOn) glDisable(GL_SCISSOR_TEST);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)fbo);
}

// ======================= Toques =======================
static bool inR(float x, float y, int rx, int ry, int rw, int rh) { return x >= rx && x < rx + rw && y >= ry && y < ry + rh; }

static bool onTouchDown(float x, float y) {      // true = el menu consume el toque
    if (g_W <= 0) return false;
    Lay l = lay();
    if (!g_open) {
        if (inR(x, y, l.bx, l.by, l.bs, l.bs)) { g_open = true; return true; }
        return false;
    }
    if (!inR(x, y, l.px, l.py, l.pw, l.ph)) { g_open = false; return true; }
    for (int i = 0; i < 5; i++)
        if (inR(x, y, l.px + l.pad + i * (l.tabW + l.pad), l.tabY, l.tabW, l.tabH)) { g_cat = i; return true; }
    int k = 0;
    for (int i = 0; i < NMOD; i++) {
        if (MODS[i].cat != g_cat) continue;
        int ry = l.listY + k * l.rowH; k++;
        if (ry + l.rowH > l.py + l.ph - l.pad) break;
        if (inR(x, y, l.px + l.pad, ry, l.pw - 2 * l.pad, l.rowH)) { g_on[i] = !g_on[i]; return true; }
    }
    return true;
}

static bool g_grab = false; static int32_t g_grabId = -1;
static bool handleEvent(AInputEvent* e) {
    if (AInputEvent_getType(e) != AINPUT_EVENT_TYPE_MOTION) return false;
    int32_t a = AMotionEvent_getAction(e);
    int act = a & AMOTION_EVENT_ACTION_MASK;
    int idx = (a & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK) >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;
    if (act == AMOTION_EVENT_ACTION_DOWN || act == AMOTION_EVENT_ACTION_POINTER_DOWN) {
        if (onTouchDown(AMotionEvent_getX(e, idx), AMotionEvent_getY(e, idx))) {
            g_grab = true; g_grabId = AMotionEvent_getPointerId(e, idx); return true;
        }
        return g_open;
    }
    if (g_grab) {
        if (act == AMOTION_EVENT_ACTION_UP || act == AMOTION_EVENT_ACTION_CANCEL ||
            (act == AMOTION_EVENT_ACTION_POINTER_UP && AMotionEvent_getPointerId(e, idx) == g_grabId)) g_grab = false;
        return true;
    }
    return g_open;   // con el menu abierto, el juego no recibe toques
}

static int32_t (*o_getEvent)(AInputQueue*, AInputEvent**) = nullptr;
static int32_t h_getEvent(AInputQueue* q, AInputEvent** out) {
    static bool first = true;
    if (first) { first = false; LOGI("primera lectura de eventos de entrada"); }
    for (;;) {
        int32_t r = o_getEvent(q, out);
        if (r < 0) return r;
        if (handleEvent(*out)) { AInputQueue_finishEvent(q, *out, 1); continue; }
        return r;
    }
}

// ======================= Hooks EGL =======================
static EGLBoolean (*o_swapInterval)(EGLDisplay, EGLint) = nullptr;
static EGLBoolean (*o_swapBuffers)(EGLDisplay, EGLSurface) = nullptr;

static EGLBoolean h_swapInterval(EGLDisplay d, EGLint interval) {
    return o_swapInterval(d, g_on[IDX_UNCAP] ? 0 : interval);
}
static EGLBoolean h_swapBuffers(EGLDisplay d, EGLSurface s) {
    using clk = std::chrono::steady_clock;
    static int frames = 0; static int logTick = 0;
    static clk::time_point t0 = clk::now();
    static bool firstSwap = true;
    if (firstSwap) { firstSwap = false; LOGI("primer eglSwapBuffers: el juego esta dibujando"); }
    frames++;
    float dt = std::chrono::duration<float>(clk::now() - t0).count();
    if (dt >= 0.5f) {
        g_fps = frames / dt; frames = 0; t0 = clk::now();
        if (++logTick % 4 == 0) LOGI("FPS %.1f", g_fps.load());
    }
    drawAll(d, s);
    return o_swapBuffers(d, s);
}


// ======================= Diagnostico =======================
static EGLSurface (*o_createWinSurf)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*) = nullptr;
static EGLSurface h_createWinSurf(EGLDisplay d, EGLConfig c, EGLNativeWindowType w, const EGLint* a) {
    EGLSurface r = o_createWinSurf(d, c, w, a);
    LOGI("eglCreateWindowSurface -> %p", r);
    return r;
}
static EGLBoolean (*o_makeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext) = nullptr;
static EGLBoolean h_makeCurrent(EGLDisplay d, EGLSurface dr, EGLSurface rd, EGLContext c) {
    static int n = 0;
    EGLBoolean r = o_makeCurrent(d, dr, rd, c);
    if (n++ < 6) LOGI("eglMakeCurrent(surf=%p ctx=%p) -> %u", dr, c, (unsigned)r);
    return r;
}
static void (*o_winCreated)(ANativeActivity*, ANativeWindow*) = nullptr;
static void h_winCreated(ANativeActivity* a, ANativeWindow* w) { LOGI("callback: ventana creada %p", w); if (o_winCreated) o_winCreated(a, w); }
static void (*o_inputCreated)(ANativeActivity*, AInputQueue*) = nullptr;
static void h_inputCreated(ANativeActivity* a, AInputQueue* q) { LOGI("callback: cola de entrada creada %p", q); if (o_inputCreated) o_inputCreated(a, q); }
static void (*o_resume)(ANativeActivity*) = nullptr;
static void h_resume(ANativeActivity* a) { LOGI("callback: onResume"); if (o_resume) o_resume(a); }
static void (*o_focus)(ANativeActivity*, int) = nullptr;
static void h_focus(ANativeActivity* a, int f) { LOGI("callback: foco=%d", f); if (o_focus) o_focus(a, f); }

// ======================= Entrada NativeActivity (proxy) =======================
extern "C" __attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity* activity, void* state, size_t size) {
    LOGI("libgcore cargada, abriendo libemu32.so");
    g_on[IDX_FPS] = true; g_on[IDX_UNCAP] = true;
    // El launcher copia libemu32.so del Minecraft instalado a <filesDir>/gold/ antes de abrir esta actividad.
    std::string emuPath = std::string(activity->internalDataPath) + "/gold/libemu32.so";
    void* emu = dlopen(emuPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!emu) { LOGE("dlopen %s fallo: %s", emuPath.c_str(), dlerror()); return; }
    LOGI("libemu32 cargada desde %s", emuPath.c_str());

    HookDef hooks[] = {
        {"eglSwapInterval",     (void*)h_swapInterval, (void**)&o_swapInterval, 0},
        {"eglSwapBuffers",      (void*)h_swapBuffers,  (void**)&o_swapBuffers,  0},
        {"AInputQueue_getEvent",(void*)h_getEvent,     (void**)&o_getEvent,     0},
        {"eglCreateWindowSurface",(void*)h_createWinSurf,(void**)&o_createWinSurf, 0},
        {"eglMakeCurrent",      (void*)h_makeCurrent,  (void**)&o_makeCurrent,  0},
    };
    patchGot("libemu32.so", hooks, 5);
    if (!o_swapBuffers) LOGE("eglSwapBuffers no enganchado: sin overlay");
    if (!o_getEvent)    LOGE("AInputQueue_getEvent no enganchado: el menu se ve pero no responde al toque");

    auto fn = (void (*)(ANativeActivity*, void*, size_t))dlsym(emu, "ANativeActivity_onCreate");
    if (!fn) { LOGE("libemu32 no exporta ANativeActivity_onCreate"); return; }
    LOGI("entregando control a libemu32 (ANativeActivity_onCreate)");
    fn(activity, state, size);
    LOGI("libemu32 devolvio el control");
    if (activity->callbacks) {
        auto* cb = activity->callbacks;
        LOGI("callbacks registrados: window=%d input=%d resume=%d focus=%d",
             cb->onNativeWindowCreated != nullptr, cb->onInputQueueCreated != nullptr,
             cb->onResume != nullptr, cb->onWindowFocusChanged != nullptr);
        o_winCreated = cb->onNativeWindowCreated;   if (o_winCreated)   cb->onNativeWindowCreated = h_winCreated;
        o_inputCreated = cb->onInputQueueCreated;   if (o_inputCreated) cb->onInputQueueCreated = h_inputCreated;
        o_resume = cb->onResume;                    if (o_resume)       cb->onResume = h_resume;
        o_focus = cb->onWindowFocusChanged;         if (o_focus)        cb->onWindowFocusChanged = h_focus;
    }
}
