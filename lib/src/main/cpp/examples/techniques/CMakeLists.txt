cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_techniques)

add_library(gamma_examples_techniques STATIC
    additivePacket.cpp
    bakedTremolo.cpp
    celeste.cpp
    drift.cpp
    equalLoudness.cpp
    portamento.cpp
)

target_compile_definitions(gamma_examples_techniques PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_techniques PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_techniques PUBLIC gamma)
