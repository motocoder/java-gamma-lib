# gammalib — Agent Instructions

## Overview

Java/Android bindings for the [Gamma](https://github.com/LancePutnam/Gamma) C++ audio DSP
library, exposed to Java/Kotlin via JNI. Provides real-time audio analysis (peak/silence/noise
detection), a large filter and effects library (low/high/band-pass, shelving, notch, chorus,
bitcrusher, etc.), spectral processing, PCM format conversion, MP3⇄PCM conversion (via a
vendored LAME encoder), live audio capture, and a higher-level `voice` package
(`llc.berserkr.gammalib.voice.VoiceProcessor`) that wraps the raw `Gamma` JNI calls into a
walkie-talkie-oriented API (voice-band filtering, presence/boominess EQ, radio/tinny effects,
PCM16 convenience overloads).

Two Gradle modules:

- **`lib`** — the Android library, published as `llc.berserkr:gammalib`.
- **`app`** — a placeholder demo Android app.

Key facts (verified against `lib/build.gradle.kts`, `gradle/libs.versions.toml`):

| Component | Version |
|---|---|
| Android min SDK | 24 (Android 7.0) |
| Android compile SDK | 36 |
| Android Gradle Plugin | 9.0.1 |
| NDK CMake (`externalNativeBuild`) | 4.2.1 |
| C++ standard (native code) | C++11 |
| Java source/target compatibility | 18 |

## Commands

Windows:

```bat
gradlew.bat build
gradlew.bat :lib:assembleDebug
gradlew.bat :lib:assembleRelease
gradlew.bat :lib:test
gradlew.bat :lib:test --tests "llc.berserkr.gammalib.GammaUnitTest"
gradlew.bat :lib:connectedAndroidTest
gradlew.bat clean
```

POSIX (macOS/Linux):

```sh
./gradlew build
./gradlew :lib:assembleDebug
./gradlew :lib:assembleRelease
./gradlew :lib:test
./gradlew :lib:test --tests "llc.berserkr.gammalib.GammaUnitTest"
./gradlew :lib:connectedAndroidTest      # requires a connected Android device/emulator
./gradlew clean
```

CI verify (both platforms build the same target; use the platform's wrapper script):

```
gradlew.bat :lib:assembleRelease --no-daemon      # Windows
./gradlew :lib:assembleRelease --no-daemon        # POSIX
```

Notes:

- The first build downloads `lame-3.100.tar.gz` (~600 KB) from SourceForge at CMake configure
  time to compile the vendored LAME MP3 encoder; subsequent builds use the CMake download cache.
- `:lib:connectedAndroidTest` requires a connected device/emulator and the `RECORD_AUDIO`
  permission (granted automatically via `GrantPermissionRule`). Instrumented tests also pull in
  `com.alphacephei:vosk-android` + `net.java.dev.jna:jna` (see `lib/build.gradle.kts`
  `androidTestImplementation`), used by `VoiceProcessorAudibleTest`.
- Tests that produce audio output files save them to `Download/test-signals/` on the device; pull
  with `adb pull /storage/emulated/0/Download/test-signals/ ./test-signals/`.

## Architecture

### Native layer (C/C++)

- `lib/src/main/cpp/gamma-lib.cpp` — JNI bridge implementing all native methods declared in
  `Gamma.java`. Each JNI function converts between Java arrays and C++ types, applies Gamma DSP
  operations, and returns results.
- `lib/src/main/cpp/deps/gamma/` — vendored copy of the Gamma DSP library (DFT, FFT, filters,
  oscillators, etc.).
- `lib/src/main/cpp/deps/berserkrlib/` — small helper library (`berserkr.h` / `berserkr_plus.hpp`)
  used alongside Gamma.
- `lib/src/main/cpp/deps/lame/` — Android `config.h` for the LAME MP3 encoder (fetched at CMake
  configure time — see Commands).
- CMake builds the native code into `libgamma_lib.so`, linked against `gamma` and `berserkrlib`
  static libraries. The `PLATFORM_ANDROID` preprocessor macro is defined in native builds.

### Java layer (`lib/src/main/java/llc/berserkr/gammalib/`)

- **`jni.Gamma`** — the JNI entry point. Loads `gamma_lib`. Declares native methods:
  `initialize()`, `maxVolumeNormalize()`, `maxVolumePCM24Bytes()`, `detectSilence()`,
  `detectNoise()`, plus the full filter/effects library (low/high/band-pass, shelving, notch,
  resonator, chorus, quantizer, biquad3, etc. — see the class for the full method list).
- **`android`** — Android audio capture: `BufferLoader` interface, `AudioBufferLoader` (wraps
  `AudioRecord`), `SoundRecorder` (returns an `InputStream` of live audio data).
- **`util`** — PCM encoding utilities: `SoundEncodingUtil` (conversions between 16-bit/24-bit/float
  PCM, all little-endian), `MP3ToPCMConverter` (MP3⇄PCM via `MediaCodec` + LAME JNI),
  `StreamUtil` (stream copy/digest helpers).
- **`voice.VoiceProcessor`** — higher-level, walkie-talkie-oriented API layered entirely on top of
  `Gamma` + `SoundEncodingUtil` (no additional native code). Every method has a normalized
  `float[]` form and a PCM16 `byte[]` form (via the nested `VoiceProcessor.PCM16` converter).
  Covers voice-activity detection (`isSilent`, `hasNoise`, `peakLevelDb`), clarity filters
  (`applyVoiceBandPass`, `removeRumble`, `blockDC`, `removeFrequency`, `smooth`), EQ
  (`boostPresence`, `reduceBoominess`, `addAir`, `eq`), effects (`radioEffect`, `tinnyEffect`,
  `formantFilter`, `chorus`), and direct-access filters (`lowPass`, `highPass`, `bandPass`,
  `bandReject`).

### Data flow

```
AudioRecord (live mic)
        │  raw 24-bit PCM bytes — 96 kHz, mono, little-endian
        ▼
SoundEncodingUtil.unpack24BitToFloat()
        │  normalized float[] in [-1.0, 1.0]
        ▼
Gamma native methods (filter / analyze / effect)   ── or VoiceProcessor's higher-level wrappers
        │  processed float[]
        ▼
SoundEncodingUtil.packFloatTo24Bit()
        │  24-bit PCM bytes
        ▼
MP3ToPCMConverter.pcm24At96kToMp3()   ← optional
        │  MP3 bytes
        ▼
  AudioTrack / file output
```

Full project structure, JNI method reference tables, and usage examples are in `README.md`.

## Project rules

- Match the existing coding style in a file being edited; if unsure, ask rather than guess.
- Audio format conventions are load-bearing: little-endian byte order throughout; 24-bit PCM uses
  a 3-byte packed format with sign extension on the MSB.
- Logging uses SLF4J in Java code and `__android_log_print` macros in native code.
- Maven/PyPI artifacts (`gammalib` itself and its dependencies) are consumed via the workspace's
  release coordinates; in-session snapshot publishing goes only to the session-local repository
  (see the rulebase below) — never to Artifactory.
- Git workflow (branching, commits, pushes, versioning) is governed by the workspace session
  rulebase: `server-config/agent-central/docs/sessions.md` at the workspace root.
