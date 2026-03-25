cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_effects)

add_library(gamma_examples_effects STATIC
    bitcrush.cpp
    celeste.cpp
    cheby.cpp
    echo.cpp
    flanger.cpp
    vibrato.cpp
)

target_compile_definitions(gamma_examples_effects PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_effects PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_effects PUBLIC gamma)
