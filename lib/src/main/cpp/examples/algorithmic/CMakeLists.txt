cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_algorithmic)

add_library(gamma_examples_algorithmic STATIC
    chirpTank.cpp
    luster.cpp
    noteFuncs.cpp
    notesFromPinkNoise.cpp
    notesFromSine.cpp
    PhiX174.cpp
    sawCluster.cpp
)

target_compile_definitions(gamma_examples_algorithmic PRIVATE PLATFORM_ANDROID)

target_link_options(gamma_examples_algorithmic PRIVATE "-Wl,-z,max-page-size=16384")

# All Gamma headers are pulled in transitively via gamma's PUBLIC include dirs.
target_link_libraries(gamma_examples_algorithmic PUBLIC gamma)
