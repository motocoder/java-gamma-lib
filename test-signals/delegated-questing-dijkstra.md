# Plan: JNI Bindings for All Gamma Filters and Compatible Effects

## Context

The project wraps the Gamma C++ DSP library for Android via JNI. Currently only `lowPassFilter` and `highPassFilter` (both using `gam::Biquad`) are bound. This plan adds JNI bindings for **all remaining Biquad filter types**, **all standalone filter classes** from `Filter.h`, and **compatible effects** from `Effects.h` that process input arrays. Existing lowPassFilter/highPassFilter are left unchanged (hardcoded Q=0.707).

---

## Filter Inventory — What Each Filter Does

### Group 1: Remaining Biquad Types

The `gam::Biquad` is a 2-pole/2-zero IIR filter (12 dB/octave slope). Already bound: LOW_PASS, HIGH_PASS.

| Filter | Purpose | Use Case |
|--------|---------|----------|
| **Band-Pass** | Passes a band of frequencies around a center frequency, attenuating everything above and below. | Isolating a vocal range, radio tuning simulation, frequency-selective processing. |
| **Resonant** | Like band-pass but with constant skirt gain — peak gain equals Q. Emphasizes the center frequency more aggressively. | Sound design resonance effects, formant synthesis, emphasizing specific partials. |
| **Band-Reject** (Notch) | Opposite of band-pass — removes a narrow band of frequencies while passing everything else. | Removing 60Hz mains hum, eliminating feedback frequencies, surgical frequency removal. |
| **All-Pass** | Passes all frequencies at equal amplitude but shifts their phase. Phase shift varies with frequency around the center point. | Phase-based effects (phasers, flangers when combined with delay), correcting phase alignment in multi-driver speakers. |
| **Peaking** | Boosts or cuts a band of frequencies by a specified amount (parametric EQ). Frequencies outside the band are unaffected. | Parametric equalization, tone shaping, boosting presence or cutting muddiness in a mix. |
| **Low-Shelf** | Boosts or cuts all frequencies below a shelf frequency by a specified amount. Frequencies above are unaffected. | Bass boost/cut, adjusting low-end warmth, matching speaker response curves. |
| **High-Shelf** | Boosts or cuts all frequencies above a shelf frequency by a specified amount. Frequencies below are unaffected. | Treble boost/cut (like a tone knob), de-essing, adding "air" or brightness. |

### Group 2: Standalone Filter Classes (from Filter.h)

| Filter | Purpose | Parameters | Use Case |
|--------|---------|------------|----------|
| **OnePole** | Simplest possible IIR filter — single pole gives 6 dB/octave slope. Supports LOW_PASS or HIGH_PASS. Much gentler roll-off than Biquad. | freq, type | Gentle smoothing, control signal filtering, simple tone shaping where steep cutoff isn't needed. |
| **AllPass1** | First-order all-pass filter. Shifts phase from 0 to -180° across the spectrum, with -90° at the specified frequency. | freq | Building blocks for phasers, correcting group delay, Schroeder reverb components. |
| **AllPass2** | Second-order all-pass filter. Shifts phase from 0 to -360° (2π), with the transition centered at the specified frequency and controlled by bandwidth. | freq, bandwidth | More pronounced phaser stages, multi-stage phase effects, delay-based effects. |
| **BlockDC** | Removes the DC offset (0 Hz component) from a signal. A specialized high-pass with very low cutoff. | bandwidth | Removing DC bias after asymmetric processing (rectification, waveshaping), preventing speaker cone displacement from DC offset. |
| **BlockNyq** | Removes the Nyquist frequency component. A specialized low-pass that cuts only the very top of the spectrum. | bandwidth | Cleaning up aliasing artifacts at Nyquist, removing folded frequencies after nonlinear processing. |
| **Notch** | Two-zero notch filter. Completely eliminates a specific frequency. Different implementation from Biquad BAND_REJECT — uses zeros only (no feedback poles). | freq, bandwidth | Removing a single known interference tone, eliminating hum at a precise frequency, surgical frequency removal without any resonance. |
| **Reson** | Two-pole resonator. Strongly amplifies frequencies at and near the center frequency. Opposite of Notch. | freq, bandwidth | Vowel/formant simulation, physical modeling (resonating body), spectral emphasis of specific frequencies. |
| **MovingAvg** | FIR (finite impulse response) low-pass filter using a rectangular window average over N samples. Cutoff ≈ sampleRate / N. | size (kernel width) | Smoothing noisy signals, simple noise reduction, computing running averages of audio amplitude. |
| **Differencer** | Returns the difference between the current and previous sample. Acts as a high-pass filter with a zero at DC. No parameters. | (none) | Edge detection in audio, converting position to velocity, emphasizing transients, pre-emphasis filtering. |
| **Integrator** | Accumulates input values with optional leak (decay). Without leak, sums indefinitely; with leak, acts as a simple low-pass. | leakCoefficient | Smoothing control signals, converting velocity to position, low-frequency envelope following, leaky accumulation. |

### Group 3: Compatible Effects (from Effects.h)

Effects that process an input float[] array and return a processed float[] array, same pattern as filters.

| Effect | Purpose | Parameters | Use Case |
|--------|---------|------------|----------|
| **Chorus** | Dual delay-line chorus driven by a quadrature sinusoid modulator. Creates a thickened, shimmering copy of the input by modulating two comb-filtered delay lines. | delay, depth, modFrequency, feedforward, feedback | Adding width and richness to vocals or instruments, creating doubling effects, thickening thin sounds. |
| **Quantizer** | Bitcrusher/downsampler. Reduces sample rate (sequence quantization) and/or bit depth (amplitude quantization) of the signal. | quantizationFrequency, amplitudeStep, sampleRate | Lo-fi effects, retro game audio aesthetics, creative distortion, reducing audio quality intentionally for effect. |
| **Biquad3** | Three Biquad filters summed in parallel, each at a different center frequency. Produces a multi-band filtered output. | frequency0, frequency1, frequency2, resonance, filterType, sampleRate | Multi-band emphasis, formant-like filtering, creating vowel shapes, spectral sculpting with three peaks. |

### Excluded from this plan

| Item | Reason |
|------|--------|
| **Hilbert** | Hardcoded for 44.1kHz sample rate (project uses 96kHz). Returns `Complex<Tv>` (two outputs), requiring a different JNI return pattern. |
| **FreqShift** | Uses Hilbert internally — inherits the 44.1kHz incompatibility. |
| **Burst** | Sound generator (no input array) — calls `operator()()` with no input to produce percussive noise. Different binding pattern. |
| **MonoSynth** | Sound generator (no input array) — synthesizes saw wave + filter sweep internally. |
| **Pluck** | Primarily a sound generator (Karplus-Strong). Has `operator()(float in)` but intended for excitation input, not array processing. |

---

## Files to Modify

| File | Change |
|------|--------|
| `lib/src/main/java/llc/berserkr/gammalib/jni/Gamma.java` | Add 19 new native method declarations with Javadoc |
| `lib/src/main/cpp/gamma-lib.cpp` | Add 19 new JNI implementations |
| `lib/src/androidTest/java/llc/berserkr/gammalib/GammaTest.java` | Add instrumented tests for all new filters |
| `lib/src/test/java/llc/berserkr/gammalib/GammaHostTest.java` | **New** — Host-native JNI tests (synthetic signals, no Android device needed) |
| `lib/src/main/cpp/CMakeLists.txt` | Add conditional host-native build (non-Android target) |
| `lib/src/main/cpp/deps/gamma/CMakeLists.txt` | Make `android`/`log` link conditional on `PLATFORM_ANDROID` |
| `lib/src/main/cpp/deps/berserkrlib/CMakeLists.txt` | Make Android-specific includes/defines conditional |
| `lib/build.gradle.kts` | Add host-native CMake build task for `./gradlew :lib:test` |
| `tools/plot_signals.py` | **New** — Bokeh script to plot input vs output waveforms from test signal exports |
| `CLAUDE.md` | Update Gamma class description and test instructions |

`Filter.h` is already included transitively via `Effects.h`, so no new Gamma source changes are needed.

---

## Method Signatures

### Group 1A: Biquad filters without level parameter

Java signature pattern: `(float[] normalizedSamples, float centerFrequency, float resonance, float sampleRate)` → `float[]`

- `bandPassFilter`
- `resonantFilter`
- `bandRejectFilter`
- `allPassBiquadFilter` (named to distinguish from AllPass1/AllPass2)

### Group 1B: Biquad filters with level parameter

Java signature pattern: `(float[] normalizedSamples, float frequency, float resonance, float level, float sampleRate)` → `float[]`

The `level` parameter is a **linear amplitude** (not dB). Gamma's Biquad documents it as "Amplitude level". Default is `1.0` (unity).

- `peakingFilter`
- `lowShelfFilter`
- `highShelfFilter`

### Group 2: Standalone filter classes

| Java Method | Parameters | Gamma Class |
|------------|------------|-------------|
| `onePoleLowPassFilter` | `(float[] normalizedSamples, float cutoffFrequency, float sampleRate)` | `OnePole(freq, LOW_PASS)` |
| `onePoleHighPassFilter` | `(float[] normalizedSamples, float cutoffFrequency, float sampleRate)` | `OnePole(freq, HIGH_PASS)` |
| `allPass1Filter` | `(float[] normalizedSamples, float frequency, float sampleRate)` | `AllPass1(freq)` |
| `allPass2Filter` | `(float[] normalizedSamples, float frequency, float bandwidth, float sampleRate)` | `AllPass2(freq, bandwidth)` |
| `blockDCFilter` | `(float[] normalizedSamples, float bandwidth, float sampleRate)` | `BlockDC(bandwidth)` |
| `blockNyquistFilter` | `(float[] normalizedSamples, float bandwidth, float sampleRate)` | `BlockNyq(bandwidth)` |
| `notchFilter` | `(float[] normalizedSamples, float frequency, float bandwidth, float sampleRate)` | `Notch(freq, bandwidth)` |
| `resonatorFilter` | `(float[] normalizedSamples, float frequency, float bandwidth, float sampleRate)` | `Reson(freq, bandwidth)` |
| `movingAverageFilter` | `(float[] normalizedSamples, int windowSize)` | `MovingAvg(size)` |
| `differencerFilter` | `(float[] normalizedSamples)` | `Differencer()` |
| `integratorFilter` | `(float[] normalizedSamples, float leakCoefficient)` | `Integrator(leakCoef)` |

Note: `movingAverageFilter`, `differencerFilter`, and `integratorFilter` do NOT need a `sampleRate` parameter because these classes don't inherit from Domain and operate purely on sample values.

### Group 3: Effects

| Java Method | Parameters | Gamma Class |
|------------|------------|-------------|
| `chorusEffect` | `(float[] normalizedSamples, float delay, float depth, float modulationFrequency, float feedforward, float feedback, float sampleRate)` | `Chorus(delay, depth, freq, ffd, fbk)` |
| `quantizerEffect` | `(float[] normalizedSamples, float quantizationFrequency, float amplitudeStep, float sampleRate)` | `Quantizer(freq, step)` |
| `biquad3Filter` | `(float[] normalizedSamples, float frequency0, float frequency1, float frequency2, float resonance, float sampleRate)` | `Biquad3(f0, f1, f2, q, BAND_PASS)` |

Note: Chorus uses internal Comb filters (domain-aware). Quantizer inherits from Domain. Biquad3's internal biquads each need their domain set individually.

---

## C++ Implementation Pattern

Every filter follows the same JNI pattern. Two examples:

### Domain-aware filter (e.g., bandPassFilter)

```cpp
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_bandPassFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat centerFrequency,
    jfloat resonance,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // 1. Create a local domain to set the sample rate for the filter
    gam::Domain domain(sampleRate);

    // 2. Initialize the Biquad filter as a BAND_PASS
    // Parameters: Frequency, Resonance (Q), Type
    gam::Biquad<float> bpFilter(centerFrequency, resonance, gam::BAND_PASS);

    // Associate the filter with our local domain
    bpFilter.domain(domain);

    // 3. Iterate and process
    for (int i = 0; i < length; ++i) {
        elements[i] = bpFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);

    // 4. Release input array without copying changes back
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}
```

### Non-domain filter (e.g., movingAverageFilter)

```cpp
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_movingAverageFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jint windowSize
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // 1. Initialize the moving average filter
    // Kernel size determines cutoff: cutoff ≈ sampleRate / windowSize
    gam::MovingAvg<float> maFilter(windowSize);

    // 2. Iterate and process
    for (int i = 0; i < length; ++i) {
        elements[i] = maFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);

    // 3. Release input array without copying changes back
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}
```

### Effect with internal domain components (e.g., chorusEffect)

```cpp
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_chorusEffect(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat delay,
    jfloat depth,
    jfloat modulationFrequency,
    jfloat feedforward,
    jfloat feedback,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // 1. Create a local domain to set the sample rate
    gam::Domain domain(sampleRate);

    // 2. Initialize the Chorus effect
    // Parameters: delay interval, modulation depth, modulation frequency, feedforward, feedback
    gam::Chorus<float> chorus(delay, depth, modulationFrequency, feedforward, feedback);

    // Associate internal comb filters with our domain
    chorus.comb1.domain(domain);
    chorus.comb2.domain(domain);

    // 3. Iterate and process
    for (int i = 0; i < length; ++i) {
        elements[i] = chorus(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);

    // 4. Release input array without copying changes back
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}
```

---

## Testing Strategy

### Test Flow — How It Works

**No audio files, no microphone, no file transfer to the device.** Test signals are generated programmatically in Java as `float[]` arrays — the same format the JNI filter methods already accept and return. Everything happens in memory.

The end-to-end flow for every filter test:

```
┌─────────────────────────────────────────────────────────────────┐
│  Java Test Method (runs on host JVM or Android device)          │
│                                                                 │
│  1. GENERATE: Create synthetic float[] signal in pure Java      │
│     ┌──────────────────────────────────────────────────────┐    │
│     │ float[] tone1 = generateSineWave(200f, 96000f, 0.1f) │    │
│     │ float[] tone2 = generateSineWave(1000f, 96000f, 0.1f)│    │
│     │ float[] input = mixSignals(tone1, tone2)              │    │
│     └──────────────────────────────────────────────────────┘    │
│                          │                                      │
│                          ▼                                      │
│  2. FILTER: Pass float[] directly into JNI native method        │
│     ┌──────────────────────────────────────────────────────┐    │
│     │ float[] output = gamma.bandPassFilter(               │    │
│     │     input, 1000f, 2.0f, 96000f                       │    │
│     │ );                                                    │    │
│     └──────────────────────────────────────────────────────┘    │
│          │                                                      │
│          │  (JNI boundary — enters C++ Gamma DSP code,          │
│          │   processes each sample through the filter,           │
│          │   returns new float[] with filtered samples)          │
│          │                                                      │
│          ▼                                                      │
│  3. ASSERT: Compare input vs output using RMS energy, mean,     │
│     or sample-level checks — all in pure Java math              │
│     ┌──────────────────────────────────────────────────────┐    │
│     │ float inputRMS = computeRMS(input);                   │    │
│     │ float outputRMS = computeRMS(output);                 │    │
│     │                                                       │    │
│     │ // Band-pass at 1000Hz should remove the 200Hz        │    │
│     │ // component, reducing overall energy                  │    │
│     │ assertTrue(outputRMS < inputRMS);                      │    │
│     │                                                       │    │
│     │ // But the 1000Hz component should survive             │    │
│     │ assertTrue(outputRMS > 0.0f);                          │    │
│     └──────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

Key points:
- `generateSineWave()` uses `Math.sin()` to fill a `float[]` — no audio hardware, no files
- `mixSignals()` adds two `float[]` arrays element-wise to create composite test signals
- The JNI method receives and returns `float[]` — same as production code paths
- Assertions use `computeRMS()` (root-mean-square energy) to measure whether frequencies were attenuated or preserved, without needing FFT analysis
- **Identical flow on both tiers** — only the native library differs (`.dylib` on macOS vs `.so` on Android)

### Two Test Tiers

This project uses **two tiers** of JNI filter tests:

1. **Host-native tests** (`./gradlew :lib:test`) — Compile `gamma-lib.cpp` and the Gamma library for macOS, load the resulting `libgamma_lib.dylib` in the host JVM, and run synthetic-signal tests without any Android device. These are the primary development-loop tests: fast, deterministic, no emulator needed.

2. **Instrumented tests** (`./gradlew :lib:connectedAndroidTest`) — Run on a connected Android device or emulator. Verify that the same JNI bindings work correctly in the actual Android runtime with the Android NDK-built `.so`. These are the integration/verification tier.

Both tiers share the same test helpers and synthetic signal strategy. The host-native tests exercise the exact same C++ filter code — only the JNI host (macOS JVM vs Android ART) differs.

### Host-Native Build Infrastructure

To enable host-native testing, the build system needs these changes:

#### 1. Make CMake builds platform-conditional

The existing CMakeLists.txt files hardcode Android dependencies (`android`, `log`, `android_native_app_glue`). These must be wrapped in `if(PLATFORM_ANDROID)` guards:

**`lib/src/main/cpp/CMakeLists.txt`** changes:
```cmake
# Only include Android-specific sources and libraries when building for Android
if(PLATFORM_ANDROID)
    include_directories(${ANDROID_NDK}/sources/android/native_app_glue/)
    list(APPEND SOURCES ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c)
    target_compile_definitions(gamma_lib PRIVATE PLATFORM_ANDROID)
    target_link_libraries(gamma_lib PUBLIC android log berserkrlib gamma)
else()
    # Host build: link only gamma and berserkrlib (no Android system libs)
    # Find JNI headers from the host JDK
    find_package(JNI REQUIRED)
    target_include_directories(gamma_lib PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(gamma_lib PUBLIC berserkrlib gamma)
endif()
```

**`lib/src/main/cpp/deps/gamma/CMakeLists.txt`** changes:
```cmake
# Android system libraries only needed on Android
if(PLATFORM_ANDROID)
    target_link_libraries(gamma PUBLIC android log)
endif()
```

**`lib/src/main/cpp/deps/berserkrlib/CMakeLists.txt`** changes:
```cmake
if(PLATFORM_ANDROID)
    include_directories(${ANDROID_NDK}/sources/android/native_app_glue/)
    list(APPEND SOURCES ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c)
    target_compile_definitions(berserkrlib PRIVATE PLATFORM_ANDROID)
endif()
```

#### 2. Guard Android-specific code in C++ sources

**`berserkr.h`** — wrap Android logging behind `#ifdef PLATFORM_ANDROID`:
```c
#ifdef PLATFORM_ANDROID
    #include <android/log.h>
    #include "android_native_app_glue.h"
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
    #include <cstdio>
    #define LOGI(...) fprintf(stdout, __VA_ARGS__)
    #define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif
```

**`gamma-lib.cpp`** — wrap `#include <android/log.h>` behind `#ifdef PLATFORM_ANDROID`.

#### 3. Add a Gradle task to build the host-native library

Add a custom task in `lib/build.gradle.kts` that:
1. Runs CMake targeting the host (no Android NDK toolchain, no `-DPLATFORM_ANDROID`)
2. Builds `libgamma_lib.dylib` (macOS) into a known output directory
3. Sets `java.library.path` for the `:lib:test` task so `System.loadLibrary("gamma_lib")` finds the host dylib

```kotlin
// In lib/build.gradle.kts
val hostNativeBuildDir = layout.buildDirectory.dir("host-native")

val buildHostNativeLib by tasks.registering(Exec::class) {
    val buildDir = hostNativeBuildDir.get().asFile
    val srcDir = file("src/main/cpp")

    doFirst {
        buildDir.mkdirs()
    }

    workingDir = buildDir
    commandLine("cmake", srcDir.absolutePath,
        "-DCMAKE_BUILD_TYPE=Debug",
        "-DPLATFORM_ANDROID=OFF"
    )
}

val compileHostNativeLib by tasks.registering(Exec::class) {
    dependsOn(buildHostNativeLib)
    workingDir = hostNativeBuildDir.get().asFile
    commandLine("cmake", "--build", ".", "--config", "Debug")
}

tasks.withType<Test>().configureEach {
    dependsOn(compileHostNativeLib)
    systemProperty("java.library.path", hostNativeBuildDir.get().asFile.absolutePath)
}
```

### Proposed Test Helpers (shared by both tiers)

#### 1. Add a test helper method to reduce boilerplate (instrumented tests only)

Extract the repeated "record 5 seconds from mic → convert to float[]" pattern into a shared helper in `GammaTest`:

```java
private float[] recordAndNormalize(int durationMilliseconds) throws IOException {
    // ... AudioBufferLoader setup, SoundRecorder, StreamUtil.copyTo, unpack ...
}
```

#### 2. Add a synthetic signal generator for deterministic tests

Create a helper method that generates a known sine wave at a specific frequency:

```java
/**
 * Generates a normalized sine wave.
 * @param frequency  frequency in Hz
 * @param sampleRate sample rate in Hz
 * @param duration   duration in seconds
 * @return normalized float array of samples
 */
private float[] generateSineWave(float frequency, float sampleRate, float duration) {
    int sampleCount = (int)(sampleRate * duration);
    float[] samples = new float[sampleCount];
    for (int i = 0; i < sampleCount; i++) {
        samples[i] = (float) Math.sin(2.0 * Math.PI * frequency * i / sampleRate);
    }
    return samples;
}
```

This allows mixing multiple sine waves to create composite test signals with known frequency content.

#### 3. Add a signal mixer for composite test signals

Combine multiple sine waves into a single `float[]` by element-wise addition:

```java
/**
 * Mixes two or more signals by adding them element-wise.
 * All input arrays must be the same length.
 * The result is NOT normalized — amplitudes simply sum.
 * @param signals two or more float arrays of equal length
 * @return float array containing the summed signal
 */
private float[] mixSignals(float[]... signals) {
    int length = signals[0].length;
    float[] mixed = new float[length];
    for (float[] signal : signals) {
        for (int i = 0; i < length; i++) {
            mixed[i] += signal[i];
        }
    }
    return mixed;
}
```

Example usage — create a 200Hz + 1000Hz composite signal for testing a band-pass filter:

```java
float[] low = generateSineWave(200f, 96000f, 0.1f);   // 200Hz component
float[] mid = generateSineWave(1000f, 96000f, 0.1f);  // 1000Hz component
float[] input = mixSignals(low, mid);                   // composite signal

float[] output = gamma.bandPassFilter(input, 1000f, 2.0f, 96000f);

// Band-pass at 1000Hz should remove the 200Hz component
assertTrue(computeRMS(output) < computeRMS(input));
// But the 1000Hz component should survive
assertTrue(computeRMS(output) > 0.0f);
```

#### 4. Add an RMS energy measurement helper

```java
/**
 * Computes RMS energy of a signal.
 */
private float computeRMS(float[] samples) {
    float sumSquares = 0;
    for (float sample : samples) {
        sumSquares += sample * sample;
    }
    return (float) Math.sqrt(sumSquares / samples.length);
}
```

#### 5. Add a signal export helper for offline validation

Every test saves its input and output `float[]` arrays to files so they can be pulled off the device (or read from the host filesystem) and plotted for visual validation.

On Android instrumented tests, files are written to the app's external files directory (accessible via `adb pull`). On host-native tests, files are written to a local `build/test-signals/` directory.

```java
/**
 * Saves a float[] signal to a binary file for offline analysis.
 * Format: raw 32-bit IEEE 754 floats, little-endian, no header.
 * @param samples the signal data
 * @param filename the output filename (e.g., "bandPassFilter_input.raw")
 */
private void saveSignal(float[] samples, String filename) throws IOException {
    File outputDir;
    if (isAndroidInstrumentedTest()) {
        // Instrumented: write to app's external files dir on the device
        outputDir = InstrumentationRegistry.getInstrumentation()
            .getTargetContext().getExternalFilesDir("test-signals");
    } else {
        // Host-native: write to build/test-signals/
        outputDir = new File("build/test-signals");
    }
    outputDir.mkdirs();

    File file = new File(outputDir, filename);
    try (FileOutputStream fos = new FileOutputStream(file)) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : samples) {
            buffer.putFloat(sample);
        }
        fos.write(buffer.array());
    }
}
```

Each test saves both its input and output:

```java
// Inside a test method, after filtering:
saveSignal(input, "bandPassFilter_input.raw");
saveSignal(output, "bandPassFilter_output.raw");
```

#### Pulling signal files from the device

After running instrumented tests:

```bash
# Pull all saved signal files from the device
adb pull /storage/emulated/0/Android/data/llc.berserkr.gammalib.test/files/test-signals/ ./test-signals/

# Or for a specific file
adb pull /storage/emulated/0/Android/data/llc.berserkr.gammalib.test/files/test-signals/bandPassFilter_input.raw ./test-signals/
```

For host-native tests, files are already at `lib/build/test-signals/` — no pulling needed.

#### 6. Bokeh visualization script

A Python script (`tools/plot_signals.py`) reads the exported `.raw` files and plots the input vs output waveforms side-by-side using Bokeh. This provides visual confirmation that filters are behaving correctly.

```python
#!/usr/bin/env python3
"""Plot input vs output signal waveforms from JNI filter tests.

Usage:
    python tools/plot_signals.py test-signals/
    python tools/plot_signals.py test-signals/bandPassFilter

Reads .raw files (32-bit float, little-endian) and generates an interactive
Bokeh HTML page with linked time-domain plots for each filter test.
"""
import sys
import struct
from pathlib import Path
from bokeh.plotting import figure, output_file, save
from bokeh.layouts import column
from bokeh.models import RangeTool


SAMPLE_RATE = 96000


def load_raw_signal(filepath: Path) -> list[float]:
    """Load a raw 32-bit float signal file."""
    data = filepath.read_bytes()
    sample_count = len(data) // 4
    return list(struct.unpack(f"<{sample_count}f", data))


def plot_filter_test(input_file: Path, output_file_path: Path,
                     filter_name: str) -> list:
    """Create Bokeh figures comparing input and output signals."""
    input_signal = load_raw_signal(input_file)
    output_signal = load_raw_signal(output_file_path)

    # Show first 2000 samples (~21ms at 96kHz) for detail
    display_samples = min(2000, len(input_signal))
    time_axis = [i / SAMPLE_RATE * 1000 for i in range(display_samples)]

    input_fig = figure(
        title=f"{filter_name} — Input Signal",
        x_axis_label="Time (ms)", y_axis_label="Amplitude",
        width=900, height=250
    )
    input_fig.line(time_axis, input_signal[:display_samples],
                   color="steelblue", legend_label="Input")

    output_fig = figure(
        title=f"{filter_name} — Filtered Output",
        x_axis_label="Time (ms)", y_axis_label="Amplitude",
        width=900, height=250,
        x_range=input_fig.x_range  # linked x-axis for synchronized panning
    )
    output_fig.line(time_axis, output_signal[:display_samples],
                    color="coral", legend_label="Output")

    return [input_fig, output_fig]


def main():
    signal_dir = Path(sys.argv[1])
    # Find all *_input.raw files, pair with corresponding *_output.raw
    input_files = sorted(signal_dir.glob("*_input.raw"))

    if not input_files:
        print(f"No *_input.raw files found in {signal_dir}")
        sys.exit(1)

    figures = []
    for input_file in input_files:
        filter_name = input_file.stem.replace("_input", "")
        output_file_path = input_file.with_name(f"{filter_name}_output.raw")

        if not output_file_path.exists():
            print(f"Warning: no output file for {filter_name}, skipping")
            continue

        figures.extend(
            plot_filter_test(input_file, output_file_path, filter_name)
        )

    html_path = signal_dir / "filter_plots.html"
    output_file(str(html_path), title="Gamma Filter Test Results")
    save(column(*figures))
    print(f"Saved interactive plot to {html_path}")


if __name__ == "__main__":
    main()
```

**Usage after running tests:**

```bash
# After instrumented tests — pull signals then plot
adb pull /storage/emulated/0/Android/data/llc.berserkr.gammalib.test/files/test-signals/ ./test-signals/
python tools/plot_signals.py test-signals/

# After host-native tests — plot directly
python tools/plot_signals.py lib/build/test-signals/

# Open the interactive HTML plot
open test-signals/filter_plots.html
```

The Bokeh output is an interactive HTML file with linked x-axes — zoom/pan on one plot and all plots follow, making it easy to compare input vs output at the same time scale.

### How to Test Each Filter

The core strategy: **generate a composite signal with known frequency content, apply the filter, measure whether the expected frequencies were attenuated or preserved.**

#### Biquad Band-Pass (`bandPassFilter`)
- **Input**: Mix of 200Hz + 1000Hz + 5000Hz sine waves
- **Filter**: Center=1000Hz, Q=2.0, sampleRate=96000
- **Expected**: 1000Hz component preserved, 200Hz and 5000Hz significantly attenuated
- **Assert**: RMS of filtered signal is lower than input (energy removed); filtered signal still has non-zero energy (the passband component survived)

#### Biquad Resonant (`resonantFilter`)
- **Input**: Mix of 200Hz + 1000Hz + 5000Hz sine waves
- **Filter**: Center=1000Hz, Q=5.0, sampleRate=96000
- **Expected**: Similar to band-pass but with stronger emphasis at center
- **Assert**: Same as band-pass; filtered output RMS concentrated around center frequency

#### Biquad Band-Reject (`bandRejectFilter`)
- **Input**: Mix of 200Hz + 1000Hz + 5000Hz sine waves
- **Filter**: Center=1000Hz, Q=2.0, sampleRate=96000
- **Expected**: 1000Hz component removed, 200Hz and 5000Hz preserved
- **Assert**: Filtered RMS is lower than input but still substantial (two of three components remain)

#### Biquad All-Pass (`allPassBiquadFilter`)
- **Input**: 1000Hz sine wave
- **Filter**: Center=1000Hz, Q=0.707, sampleRate=96000
- **Expected**: Amplitude unchanged, phase shifted
- **Assert**: Output RMS ≈ input RMS (within small tolerance). Signal is different sample-by-sample but same energy.

#### Biquad Peaking (`peakingFilter`)
- **Input**: Mix of 200Hz + 1000Hz sine waves, both equal amplitude
- **Filter**: Center=1000Hz, Q=1.0, level=2.0 (boost), sampleRate=96000
- **Expected**: 1000Hz component boosted, 200Hz unchanged
- **Assert**: Output RMS > input RMS (energy was added by boost)

#### Biquad Low-Shelf (`lowShelfFilter`)
- **Input**: Mix of 200Hz + 5000Hz sine waves
- **Filter**: ShelfFreq=1000Hz, Q=0.707, level=0.25 (cut), sampleRate=96000
- **Expected**: 200Hz (below shelf) attenuated, 5000Hz unchanged
- **Assert**: Output RMS < input RMS

#### Biquad High-Shelf (`highShelfFilter`)
- **Input**: Mix of 200Hz + 5000Hz sine waves
- **Filter**: ShelfFreq=1000Hz, Q=0.707, level=0.25 (cut), sampleRate=96000
- **Expected**: 5000Hz (above shelf) attenuated, 200Hz unchanged
- **Assert**: Output RMS < input RMS

#### OnePole Low-Pass (`onePoleLowPassFilter`)
- **Input**: Mix of 200Hz + 10000Hz sine waves
- **Filter**: Cutoff=500Hz, sampleRate=96000
- **Expected**: 200Hz mostly passes (below cutoff), 10000Hz attenuated (gentler slope than Biquad — 6 dB/oct)
- **Assert**: Output RMS < input RMS; output is non-zero

#### OnePole High-Pass (`onePoleHighPassFilter`)
- **Input**: Mix of 200Hz + 10000Hz sine waves
- **Filter**: Cutoff=5000Hz, sampleRate=96000
- **Expected**: 10000Hz passes, 200Hz attenuated
- **Assert**: Output RMS < input RMS; output is non-zero

#### AllPass1 (`allPass1Filter`)
- **Input**: 1000Hz sine wave
- **Filter**: Freq=1000Hz, sampleRate=96000
- **Expected**: Amplitude preserved, phase shifted
- **Assert**: Output RMS ≈ input RMS (within tolerance)

#### AllPass2 (`allPass2Filter`)
- **Input**: 1000Hz sine wave
- **Filter**: Freq=1000Hz, bandwidth=100, sampleRate=96000
- **Expected**: Amplitude preserved, phase shifted more steeply than AllPass1
- **Assert**: Output RMS ≈ input RMS (within tolerance)

#### BlockDC (`blockDCFilter`)
- **Input**: 1000Hz sine wave with a DC offset added (e.g., each sample += 0.5)
- **Filter**: Bandwidth=35, sampleRate=96000
- **Expected**: DC offset removed, sine wave preserved
- **Assert**: Mean of output ≈ 0.0 (DC removed); output RMS close to sine-only RMS

#### BlockNyquist (`blockNyquistFilter`)
- **Input**: Alternating +1/-1 samples (Nyquist frequency) mixed with a 1000Hz sine wave
- **Filter**: Bandwidth=35, sampleRate=96000
- **Expected**: Nyquist component removed, sine preserved
- **Assert**: Output RMS < input RMS; output not all zeros

#### Notch (`notchFilter`)
- **Input**: Mix of 500Hz + 1000Hz + 2000Hz sine waves
- **Filter**: Freq=1000Hz, bandwidth=100, sampleRate=96000
- **Expected**: 1000Hz removed, 500Hz and 2000Hz preserved
- **Assert**: Output RMS < input RMS but still substantial

#### Resonator (`resonatorFilter`)
- **Input**: White-noise-like signal (or mix of many frequencies)
- **Filter**: Freq=1000Hz, bandwidth=50, sampleRate=96000
- **Expected**: Strong amplification near 1000Hz
- **Assert**: Output has significant energy; output peak is near 1000Hz cycle length

#### Moving Average (`movingAverageFilter`)
- **Input**: Mix of 100Hz + 10000Hz sine waves
- **Filter**: WindowSize=10
- **Expected**: Low frequencies pass, high frequencies smoothed out
- **Assert**: Output RMS < input RMS (high-frequency energy removed)

#### Differencer (`differencerFilter`)
- **Input**: Constant DC signal (all samples = 0.5)
- **Filter**: (no params)
- **Expected**: Output is all zeros after first sample (difference of constant = 0)
- **Assert**: All samples after the first are ≈ 0.0
- **Secondary test**: Sine wave input — output should be non-zero (differences of varying signal)

#### Integrator (`integratorFilter`)
- **Input**: Constant small value (all samples = 0.001) for short duration
- **Filter**: leakCoefficient=0.999
- **Expected**: Output ramps up then asymptotically levels off due to leak
- **Assert**: Last output sample > first output sample; output doesn't exceed a bound (leak prevents unbounded growth)

#### Chorus (`chorusEffect`)
- **Input**: 440Hz sine wave (1 second at 96kHz)
- **Effect**: delay=0.0021, depth=0.002, modFreq=1.0, feedforward=0.9, feedback=0.1, sampleRate=96000
- **Expected**: Output has similar energy to input but with subtle pitch/phase modulation (thickened sound)
- **Assert**: Output RMS is within reasonable range of input RMS (not drastically different); output is not identical to input (samples differ due to modulation)

#### Quantizer (`quantizerEffect`)
- **Input**: 440Hz sine wave (1 second at 96kHz)
- **Effect**: quantizationFreq=8000, amplitudeStep=0.1, sampleRate=96000
- **Expected**: Output is a coarser, "stepped" version of the input — fewer distinct amplitude levels, lower effective sample rate
- **Assert**: Output has fewer unique amplitude values than input; output is non-zero

#### Biquad3 (`biquad3Filter`)
- **Input**: Mix of 200Hz + 1000Hz + 3000Hz + 8000Hz sine waves
- **Filter**: f0=1000Hz, f1=3000Hz, f2=5000Hz, Q=4.0, sampleRate=96000
- **Expected**: Frequencies near 1000Hz, 3000Hz, and 5000Hz emphasized; 200Hz and 8000Hz attenuated
- **Assert**: Output RMS < input RMS (most energy outside the three passbands is removed)

---

## How to Run Tests

### Tier 1: Host-Native Tests (no device needed, fast iteration)
```bash
# Run all host-native JNI tests (builds libgamma_lib.dylib for macOS, then runs JVM tests)
./gradlew :lib:test

# Run a single host-native test class
./gradlew :lib:test --tests "llc.berserkr.gammalib.GammaHostTest"

# Run a single host-native test method
./gradlew :lib:test --tests "llc.berserkr.gammalib.GammaHostTest.bandPassFilterTest"
```

The Gradle `:lib:test` task automatically triggers the host-native CMake build before running tests. The resulting `libgamma_lib.dylib` is placed on `java.library.path` so `System.loadLibrary("gamma_lib")` succeeds in the host JVM.

### Tier 2: Instrumented Tests (requires connected Android device or emulator)
```bash
# Run all lib instrumented tests
./gradlew :lib:connectedAndroidTest

# Run a single test class
./gradlew :lib:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=llc.berserkr.gammalib.GammaTest

# Run a single test method
./gradlew :lib:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=llc.berserkr.gammalib.GammaTest#gammaInitTest
```

### Verifying Connected Device
```bash
# Check that a device/emulator is connected
adb devices
```

### Viewing Test Results
```bash
# Test reports are generated at:
# lib/build/reports/tests/test/index.html               (host-native)
# lib/build/reports/androidTests/connected/index.html    (instrumented)
```

### Existing Tests (for verification before changes)

| Test Method | Location | What It Does | How to Verify |
|-------------|----------|-------------|---------------|
| `gammaInitTest` | Instrumented | Loads native lib, calls initialize() | Passes if no UnsatisfiedLinkError |
| `gammaProcessTest` | Instrumented | Records 5s from mic, computes peak dB | Check logcat for "peak dB was" output |
| `gammaDetectSilence` | Instrumented | Records 5s, asserts silence detected | Must be quiet during test |
| `gammaDetectNoise` | Instrumented | Records 5s, asserts noise detected | Must make noise during test |
| `gammaLowPassFilter` | Instrumented | Records audio, applies 1kHz low-pass, plays back | Listen for muffled output (highs removed) |
| `test` | Instrumented | Loads PCM asset, applies 1kHz high-pass, plays 30s | Listen for tinny output (lows removed) |

---

## Implementation Steps

### Phase 1: Host-Native Build Infrastructure
1. **Make CMake platform-conditional** — Update `CMakeLists.txt` (root, gamma, berserkrlib) to guard Android-specific includes, defines, and link libraries behind `if(PLATFORM_ANDROID)`. Add `find_package(JNI REQUIRED)` for host builds.
2. **Guard Android-specific C/C++ code** — Wrap `#include <android/log.h>`, `android_native_app_glue.h`, and `LOGI`/`LOGE` macros in `#ifdef PLATFORM_ANDROID` with `fprintf` fallbacks for host.
3. **Add Gradle host-native build task** — Add CMake configure+build tasks to `lib/build.gradle.kts` that compile `libgamma_lib.dylib` for macOS and set `java.library.path` for `:lib:test`.
4. **Verify host-native build** — Confirm `./gradlew :lib:test` compiles the native library and existing unit tests still pass.

### Phase 2: Java Native Declarations
5. **Add Group 1A native declarations** to `Gamma.java` (4 Biquad filters without level)
6. **Add Group 1B native declarations** to `Gamma.java` (3 Biquad filters with level)
7. **Add Group 2 native declarations** to `Gamma.java` (9 standalone filters)
8. **Add Group 3 native declarations** to `Gamma.java` (3 effects)

### Phase 3: C++ JNI Implementations
9. **Implement Group 1A JNI functions** in `gamma-lib.cpp`
10. **Implement Group 1B JNI functions** in `gamma-lib.cpp`
11. **Implement Group 2 JNI functions** in `gamma-lib.cpp`
12. **Implement Group 3 JNI functions** in `gamma-lib.cpp`

### Phase 4: Tests
13. **Add test helper methods** (generateSineWave, mixSignals, computeRMS, saveSignal) — shared by both test tiers
14. **Add host-native tests** in `GammaHostTest.java` — all 19 filters using synthetic signals, each test saves input/output `.raw` files, runs via `./gradlew :lib:test`
15. **Add instrumented tests** in `GammaTest.java` — all 19 filters using synthetic signals, each test saves input/output `.raw` files to device, runs via `./gradlew :lib:connectedAndroidTest`
16. **Add recordAndNormalize helper** to `GammaTest.java` (instrumented-only, for mic-based tests)

### Phase 5: Visualization & Documentation
17. **Add Bokeh plot script** (`tools/plot_signals.py`) — reads exported `.raw` signal files, generates interactive HTML with linked input vs output waveform plots
18. **Update CLAUDE.md** — Add new filter/effect methods, host-native test instructions, signal export and plotting workflow

---

## Critical Files

| File | Purpose |
|------|---------|
| `lib/src/main/java/llc/berserkr/gammalib/jni/Gamma.java` | Java native method declarations |
| `lib/src/main/cpp/gamma-lib.cpp` | JNI C++ implementations |
| `lib/src/main/cpp/CMakeLists.txt` | Root native build — needs platform-conditional logic |
| `lib/src/main/cpp/deps/gamma/CMakeLists.txt` | Gamma library build — needs platform-conditional linking |
| `lib/src/main/cpp/deps/berserkrlib/CMakeLists.txt` | Berserkrlib build — needs platform-conditional includes |
| `lib/src/main/cpp/deps/berserkrlib/include/berserkr.h` | Logging macros — needs `#ifdef PLATFORM_ANDROID` guards |
| `lib/src/main/cpp/deps/gamma/include/Filter.h` | Reference: all filter class definitions |
| `lib/src/main/cpp/deps/gamma/include/Effects.h` | Reference: Chorus, Quantizer, Biquad3 definitions |
| `lib/build.gradle.kts` | Gradle config — needs host-native CMake build task |
| `lib/src/test/java/llc/berserkr/gammalib/GammaHostTest.java` | **New** — Host-native JNI tests |
| `lib/src/androidTest/java/llc/berserkr/gammalib/GammaTest.java` | Instrumented tests |
| `CLAUDE.md` | Project documentation |

---

## Verification

1. **Host-native build**: `./gradlew :lib:test` — compiles `libgamma_lib.dylib` for macOS, runs all synthetic-signal JNI tests on the host JVM
2. **Android build**: `./gradlew :lib:assembleDebug` — confirms NDK native code compiles and JNI names match
3. **Existing instrumented tests**: `./gradlew :lib:connectedAndroidTest` — confirms no regressions
4. **New instrumented tests**: Same synthetic-signal tests run on-device, verifying Android runtime compatibility

## Summary Count

- **New Filter.h bindings**: 16 (7 Biquad types + 9 standalone filters)
- **New Effects.h bindings**: 3 (Chorus, Quantizer, Biquad3)
- **Build system changes**: 4 files (3 CMakeLists.txt + build.gradle.kts)
- **C++ platform guards**: 2 files (berserkr.h + gamma-lib.cpp)
- **Test helpers**: 5 (generateSineWave, mixSignals, computeRMS, saveSignal, recordAndNormalize)
- **Visualization**: 1 Python script (tools/plot_signals.py — Bokeh interactive HTML plots)
- **New host-native test methods**: 19 (in GammaHostTest.java)
- **New instrumented test methods**: 19 (in GammaTest.java)
- **Total new JNI functions**: 19
- **Total new test methods**: 38
