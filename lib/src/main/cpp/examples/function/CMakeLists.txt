cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_function)

add_library(gamma_examples_function STATIC
    analysis.cpp
    interpolation.cpp
    random.cpp
)

target_compile_definitions(gamma_examples_function PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_function PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_function PUBLIC gamma)
