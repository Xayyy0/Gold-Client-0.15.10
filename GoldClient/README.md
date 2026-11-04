# Gold Client
Launcher + menu dentro del juego para Minecraft PE 0.15.10 (APK de 64 bits con libemu32).
Instalas este APK junto a tu Minecraft; Minecraft NO se modifica. El launcher abre el juego dentro de su propio proceso
(GameActivity) usando los assets, clases y libemu32 del Minecraft instalado, y dibuja encima el menu Gold (boton "G").

Requisitos: Minecraft PE (com.mojang.minecraftpe) instalado, telefono arm64, permiso "Acceso a todos los archivos" para Gold Client.
Compilar: Actions > build-gold-client > Run workflow. Logs en el telefono: adb logcat -s GCORE
