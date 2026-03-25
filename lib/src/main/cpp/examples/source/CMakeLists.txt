cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_source)

add_library(gamma_examples_source STATIC
    noise.cpp
    plotNoise.cpp
    samplePlayer.cpp
    samplePlayer2.cpp
)

target_compile_definitions(gamma_examples_source PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_source PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_source PUBLIC gamma)
