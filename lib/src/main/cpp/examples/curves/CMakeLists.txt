cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_curves)

add_library(gamma_examples_curves STATIC
    botanic.cpp
    Bowditch.cpp
    circle.cpp
    epicycloid.cpp
    hypocycloid.cpp
    spiral.cpp
)

target_compile_definitions(gamma_examples_curves PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_curves PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_curves PUBLIC gamma)
