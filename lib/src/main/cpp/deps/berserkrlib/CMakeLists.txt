# Define a library for raymoblib
add_library(berserkrlib STATIC berserkr_common.c berserkr_common.cpp)

target_link_options(berserkrlib PRIVATE "-Wl,-z,max-page-size=16384")

# Include headers directory for android_native_app_glue.c
include_directories(${ANDROID_NDK}/sources/android/native_app_glue/)

# Add android_native_app_glue.c to the source files
list(APPEND SOURCES ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c)

# Define compiler macros for the library
target_compile_definitions(berserkrlib PRIVATE PLATFORM_ANDROID)

# Include raylib header files
target_include_directories(berserkrlib PUBLIC include)

# Link required libraries to raylib
target_link_libraries(berserkrlib)
