# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 4.2.1)

# Set standards for C and C++
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 14)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project(gamma_lib LANGUAGES C CXX)

add_subdirectory(${CMAKE_SOURCE_DIR}/deps/gamma)
add_subdirectory(${CMAKE_SOURCE_DIR}/deps/berserkrlib)

## Fetch all source files for your project (recursively), excluding 'deps' source files
file(GLOB_RECURSE SOURCES "${CMAKE_SOURCE_DIR}/*.c" "${CMAKE_SOURCE_DIR}/*.cpp")
list(FILTER SOURCES EXCLUDE REGEX "${CMAKE_SOURCE_DIR}/deps/.*")

# Add headers directory for android_native_app_glue.c
include_directories(${ANDROID_NDK}/sources/android/native_app_glue/)

# Add android_native_app_glue.c to the source files
list(APPEND SOURCES ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c)

# Create a shared library with game source files
add_library(gamma_lib SHARED ${SOURCES})

# Define compiler macros for the library
target_compile_definitions(gamma_lib PRIVATE PLATFORM_ANDROID)

# Apply flags depending on the build type
if(CMAKE_BUILD_TYPE MATCHES "Debug")
    target_compile_definitions(gamma_lib PRIVATE _DEBUG DEBUG)
endif()

target_include_directories(gamma_lib PRIVATE "${CMAKE_SOURCE_DIR}/deps/gamma")
target_include_directories(gamma_lib PRIVATE "${CMAKE_SOURCE_DIR}/deps/berserkrlib")

target_link_options(gamma_lib PRIVATE "-Wl,-z,max-page-size=16384")

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(gamma_lib PUBLIC
        # List libraries link to the target library
        android
        log
        berserkrlib
        gamma
)