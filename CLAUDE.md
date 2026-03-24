# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Coding Style

When writing code for this project, adhere to the existing coding style that is present. If unsure what to do, ask.

## Project Overview

Java/Android bindings for the [Gamma](https://github.com/LancePutnam/Gamma) C++ audio DSP library. The library exposes audio analysis and filtering functions (peak detection, silence/noise detection, low/high pass filters) via JNI to Android apps.

## Build System

Gradle (Kotlin DSL) with Android Gradle Plugin 9.0.1. Two modules: `lib` (Android library) and `app` (demo Android app).

```bash
# Build everything
./gradlew build

# Build only the library
./gradlew :lib:assembleDebug

# Run local unit tests (JVM-based, no device needed)
./gradlew :lib:test

# Run a single unit test class
./gradlew :lib:test --tests "llc.berserkr.gammalib.GammaUnitTest"

# Run instrumented tests (requires connected Android device/emulator)
./gradlew :lib:connectedAndroidTest

# Clean
./gradlew clean
```

## Architecture

### Native Layer (C/C++)

- `lib/src/main/cpp/gamma-lib.cpp` — JNI bridge implementing all native methods declared in `Gamma.java`. Each JNI function converts between Java arrays and C++ types, applies Gamma DSP operations, and returns results.
- `lib/src/main/cpp/deps/gamma/` — Vendored copy of the Gamma DSP library (DFT, FFT, filters, oscillators, etc.)
- `lib/src/main/cpp/deps/berserkrlib/` — Small helper library (`berserkr.h`/`berserkr_plus.hpp`) used alongside Gamma
- CMake builds the native code into `libgamma_lib.so`, linked against `gamma` and `berserkrlib` static libraries

### Java Layer

- **`llc.berserkr.gammalib.jni.Gamma`** — JNI entry point. Loads `gamma_lib` native library. Declares native methods: `initialize()`, `maxVolumeNormalize()`, `maxVolumePCM24Bytes()`, `detectSilence()`, `detectNoise()`, `lowPassFilter()`, `highPassFilter()`.
- **`llc.berserkr.gammalib.android`** — Android audio capture: `BufferLoader` interface, `AudioBufferLoader` (wraps `AudioRecord`), `SoundRecorder` (returns an `InputStream` of live audio data).
- **`llc.berserkr.gammalib.util`** — PCM encoding utilities: `SoundEncodingUtil` (conversions between 16-bit/24-bit/float PCM formats, all little-endian), `MP3ToPCMConverter` (MP3 to PCM via `MediaCodec`), `StreamUtil` (stream copy/digest helpers).

### Data Flow

Audio is captured as raw PCM bytes (typically 24-bit packed, 96kHz mono) → unpacked to normalized floats [-1.0, 1.0] via `SoundEncodingUtil` → processed through JNI native Gamma functions → results packed back to PCM bytes for playback.

## Key Details

- Java source compatibility: Java 18
- Min Android SDK: 24
- NDK CMake version: 4.2.1, C++11 standard for native code
- Audio format conventions: little-endian byte order throughout; 24-bit PCM uses 3-byte packed format with sign extension on the MSB
- The `PLATFORM_ANDROID` preprocessor macro is defined in native builds
- Logging uses SLF4J in Java code and `__android_log_print` macros in native code
- Instrumented tests require `RECORD_AUDIO` permission (granted via `GrantPermissionRule`)
