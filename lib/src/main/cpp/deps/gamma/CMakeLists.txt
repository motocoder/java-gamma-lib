cmake_minimum_required(VERSION 4.2.1)
project(gamma)

set(CMAKE_CXX_STANDARD 14)

add_library(gamma SHARED
    arr.cpp
    Conversion.cpp
    DFT.cpp
    Domain.cpp
    FFT_fftpack.cpp
    fftpack++1.cpp
    fftpack++2.cpp
    Print.cpp
    Recorder.cpp
    Scheduler.cpp
    scl.cpp
    SoundFile.cpp
    Timer.cpp
)

target_link_options(gamma PRIVATE "-Wl,-z,max-page-size=16384")

target_include_directories(gamma PUBLIC include)
target_link_libraries(gamma PUBLIC android log)
