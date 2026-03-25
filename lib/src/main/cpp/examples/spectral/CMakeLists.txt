cmake_minimum_required(VERSION 4.2.1)
project(gamma_examples_spectral)

add_library(gamma_examples_spectral STATIC
    brickwall.cpp
    brickwallSweep.cpp
    CFFT.cpp
    convolve.cpp
    freeze.cpp
    magThresh.cpp
    peakDetection.cpp
    pitchShift.cpp
    plucking.cpp
    RFFT.cpp
    spectralOsc.cpp
    STFT.cpp
)

target_compile_definitions(gamma_examples_spectral PRIVATE PLATFORM_ANDROID)
target_link_options(gamma_examples_spectral PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gamma_examples_spectral PUBLIC gamma)
