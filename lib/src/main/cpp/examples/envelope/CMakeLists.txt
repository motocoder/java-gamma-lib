cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_envelope)

add_library(gamma_examples_envelope STATIC
    AD.cpp
    ADSR.cpp
    decay.cpp
    env.cpp
    plotEnv.cpp
    seg.cpp
    segExp.cpp
    segNoise.cpp
)

target_compile_definitions(gamma_examples_envelope PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_envelope PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_envelope PUBLIC gamma)
