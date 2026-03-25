cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_filter)

add_library(gamma_examples_filter STATIC
    biquad.cpp
    blockDC.cpp
    comb.cpp
    delay.cpp
    freqResponses.cpp
    HilbertFilter.cpp
    onePole.cpp
)

target_compile_definitions(gamma_examples_filter PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_filter PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_filter PUBLIC gamma)
