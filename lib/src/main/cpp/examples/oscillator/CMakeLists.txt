cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_oscillator)

add_library(gamma_examples_oscillator STATIC
    accum.cpp
    buzz.cpp
    chirplet.cpp
    dwo.cpp
    lfoAudio.cpp
    lfoMod.cpp
    plotOsc.cpp
    sine.cpp
    sineDamped.cpp
    sweep.cpp
    tableOsc.cpp
)

target_compile_definitions(gamma_examples_oscillator PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_oscillator PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_oscillator PUBLIC gamma)
