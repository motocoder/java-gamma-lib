cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_voices)

add_library(gamma_examples_voices STATIC
    voicesBasic.cpp
    voicesComplicated.cpp
    voicesGrains.cpp
)

target_compile_definitions(gamma_examples_voices PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_voices PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_voices PUBLIC gamma)
