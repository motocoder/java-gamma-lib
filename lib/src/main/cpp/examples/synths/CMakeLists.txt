cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_synths)

add_library(gamma_examples_synths STATIC
    additive.cpp
    AM.cpp
    Echo.cpp
    FM.cpp
    OscEnv.cpp
    OscTrm.cpp
    PluckedString.cpp
    SineEnv.cpp
    Sub.cpp
    Vib.cpp
)

target_compile_definitions(gamma_examples_synths PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_synths PRIVATE "-Wl,-z,max-page-size=16384")

# examples.h lives in this directory alongside the sources.
target_include_directories(gamma_examples_synths PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(gamma_examples_synths PUBLIC gamma)
