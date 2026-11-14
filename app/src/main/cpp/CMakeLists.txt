cmake_minimum_required(VERSION 3.18)
project(gcore CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # lado del telefono: overlay, hooks de EGL/GL y entrada
    add_library(gcore SHARED gcore.cpp)
    target_link_libraries(gcore android log EGL GLESv2 dl)
    target_link_options(gcore PRIVATE -Wl,-z,max-page-size=16384)
else()
    # lado del juego (32 bits): se empaqueta en el APK solo como carga util; el launcher la copia a jogo32
    add_library(gold32 SHARED gold32.cpp)
    target_compile_options(gold32 PRIVATE -fno-exceptions -fno-rtti)
    target_link_libraries(gold32 log)
    target_link_options(gold32 PRIVATE -nostdlib++ -Wl,-soname,libgnustl_unwind.so)
endif()
