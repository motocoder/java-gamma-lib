cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_spatial)

add_library(gamma_examples_spatial STATIC
    binauralBeat.cpp
    dist.cpp
    echo.cpp
    pan.cpp
    reverb.cpp
    room.cpp
)

target_compile_definitions(gamma_examples_spatial PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_spatial PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_spatial PUBLIC gamma)
