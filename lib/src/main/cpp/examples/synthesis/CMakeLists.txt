cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_synthesis)

add_library(gamma_examples_synthesis STATIC
    additiveFourier.cpp
    amTransition.cpp
    beating.cpp
    chimes.cpp
    fmSimple.cpp
    fmTransition.cpp
    pluckedString.cpp
    pmFeedback.cpp
    subtractiveBassline.cpp
    subtractiveSing.cpp
    sync.cpp
)

target_compile_definitions(gamma_examples_synthesis PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_synthesis PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_synthesis PUBLIC gamma)
