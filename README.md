# gammalib

An Android library that exposes the [Gamma](https://github.com/LancePutnam/Gamma) C++ DSP library to Java/Kotlin via JNI. Provides real-time audio analysis, filtering, and effects — including spectral processing, envelope detection, and MP3/PCM conversion — for use in Android apps targeting API 24+.

---

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
  - [Gamma (JNI)](#gamma-jni)
  - [SoundEncodingUtil](#soundencodingutil)
  - [MP3ToPCMConverter](#mp3topcmconverter)
  - [Audio Capture](#audio-capture)
- [Data Flow](#data-flow)
- [Building](#building)
- [Testing](#testing)
- [Dependencies](#dependencies)
- [License](#license)

---

## Features

- **Audio analysis** — peak detection, silence/noise detection, RMS inspection
- **Filters** — low-pass, high-pass, band-pass, band-reject, notch, all-pass, peaking EQ, low/high shelf, one-pole, resonator, moving-average, differencer, integrator, DC/Nyquist blocking, Hilbert
- **Effects** — chorus, bitcrusher/quantizer, three-band parallel biquad
- **Spectral processing** — DFT, FFT, STFT, convolution, pitch shift, brick-wall, freeze, peak detection
- **PCM format conversion** — 16-bit ↔ 24-bit ↔ float, all little-endian
- **MP3 ↔ PCM conversion** — decode MP3 to 24-bit 96 kHz PCM; encode 24-bit 96 kHz PCM to MP3 via LAME
- **Live audio capture** — wraps `AudioRecord` and returns an `InputStream` of raw PCM bytes

---

## Requirements

| Component | Version |
|---|---|
| Android min SDK | 24 (Android 7.0) |
| Android compile SDK | 36 |
| Android Gradle Plugin | 9.0.1 |
| Gradle | 9.1.0 |
| NDK | 28.2.13676358 |
| CMake | 4.2.1 |
| Java | 18 |

---

## Project Structure

```
gammalib/
├── lib/                            # Android library module
│   └── src/
│       ├── main/
│       │   ├── cpp/
│       │   │   ├── gamma-lib.cpp           # JNI bridge — all native method implementations
│       │   │   ├── CMakeLists.txt
│       │   │   ├── deps/
│       │   │   │   ├── gamma/              # Vendored Gamma DSP library (LancePutnam/Gamma)
│       │   │   │   ├── berserkrlib/        # Helper utilities (berserkr.h / berserkr_plus.hpp)
│       │   │   │   └── lame/               # Android config.h for LAME MP3 encoder
│       │   │   └── examples/               # Gamma usage examples (compiled as static libs)
│       │   │       ├── algorithmic/
│       │   │       ├── analysis/
│       │   │       ├── curves/
│       │   │       ├── effects/
│       │   │       ├── envelope/
│       │   │       ├── filter/
│       │   │       ├── function/
│       │   │       ├── io/
│       │   │       ├── oscillator/
│       │   │       ├── source/
│       │   │       ├── spatial/
│       │   │       ├── spectral/
│       │   │       ├── synthesis/
│       │   │       ├── synths/
│       │   │       ├── techniques/
│       │   │       └── voices/
│       │   └── java/llc/berserkr/gammalib/
│       │       ├── jni/
│       │       │   └── Gamma.java          # JNI entry point — loads libgamma_lib.so
│       │       ├── android/
│       │       │   ├── BufferLoader.java       # Audio buffer interface
│       │       │   ├── AudioBufferLoader.java  # AudioRecord wrapper
│       │       │   └── SoundRecorder.java      # Returns InputStream of live PCM
│       │       └── util/
│       │           ├── SoundEncodingUtil.java  # PCM format conversions
│       │           ├── MP3ToPCMConverter.java  # MP3 ↔ PCM via MediaCodec / LAME JNI
│       │           └── StreamUtil.java         # Stream copy / digest helpers
│       ├── test/                   # JVM unit tests (no device required)
│       └── androidTest/            # Instrumented tests (requires device/emulator)
│           └── assets/
│               ├── retro_westerwald.mp3          # Test MP3 (~2.6 MB)
│               └── retro_westerwald_filtered.pcm # Test PCM (~20.6 MB, 16-bit 96 kHz mono)
└── app/                            # Placeholder app module
```

---

## Getting Started

### 1. Add the library to your project

Copy the `lib` module into your project and add it to `settings.gradle.kts`:

```kotlin
include(":lib")
```

Then depend on it from your app module:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":lib"))
}
```

### 2. Grant audio permissions (if capturing live audio)

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 3. Basic usage

```java
// Initialize the native library
Gamma gamma = new Gamma();
gamma.initialize();

// Inspect peak level from raw 24-bit PCM bytes
float peakDb = gamma.maxVolumePCM24Bytes(pcm24Bytes);

// Apply a high-pass filter
float[] samples = SoundEncodingUtil.unpack24BitToFloat(pcm24Bytes);
float[] filtered = gamma.highPassFilter(samples, 1000.0f, 96_000.0f);
byte[] result = SoundEncodingUtil.packFloatTo24Bit(filtered);
```

---

## API Reference

### Gamma (JNI)

`llc.berserkr.gammalib.jni.Gamma` loads `libgamma_lib.so` and exposes native methods. All filter methods accept normalized float samples in the range **[-1.0, 1.0]** and return a new normalized float array.

#### Core

| Method | Description |
|---|---|
| `void initialize()` | Must be called once before using any other method |
| `float maxVolumeNormalize(float[] recorded)` | Returns peak level in dB from normalized float samples |
| `float maxVolumePCM24Bytes(byte[] recorded)` | Returns peak level in dB directly from 24-bit PCM bytes |

#### Detection

| Method | Description |
|---|---|
| `boolean detectSilence(float[] data, float threshold, int sampleSize)` | Returns `true` if `sampleSize` consecutive samples are all below `threshold` |
| `boolean detectNoise(float[] data, float threshold)` | Returns `true` if the signal exceeds `threshold`, ignoring transient pops |

#### Basic Filters

| Method | Description |
|---|---|
| `float[] lowPassFilter(float[] samples, float cutoff, float sampleRate)` | Simple IIR low-pass |
| `float[] highPassFilter(float[] samples, float cutoff, float sampleRate)` | Simple IIR high-pass |

#### Biquad Filters (no level parameter)

| Method | Description |
|---|---|
| `float[] bandPassFilter(float[] samples, float centerFreq, float resonance, float sampleRate)` | 2-pole/2-zero band-pass (12 dB/oct) |
| `float[] resonantFilter(float[] samples, float centerFreq, float resonance, float sampleRate)` | Constant-skirt band-pass; peak gain equals Q |
| `float[] bandRejectFilter(float[] samples, float centerFreq, float resonance, float sampleRate)` | Band-reject / notch |
| `float[] allPassBiquadFilter(float[] samples, float centerFreq, float resonance, float sampleRate)` | Phase-shift allpass; amplitude preserved |

#### Biquad Filters (with level parameter)

`level` is a linear amplitude multiplier: `1.0` = unity, `> 1.0` = boost, `< 1.0` = cut.

| Method | Description |
|---|---|
| `float[] peakingFilter(float[] samples, float freq, float resonance, float level, float sampleRate)` | Parametric EQ band boost/cut |
| `float[] lowShelfFilter(float[] samples, float freq, float resonance, float level, float sampleRate)` | Boosts/cuts all frequencies below shelf frequency |
| `float[] highShelfFilter(float[] samples, float freq, float resonance, float level, float sampleRate)` | Boosts/cuts all frequencies above shelf frequency |

#### Standalone Filters

| Method | Description |
|---|---|
| `float[] onePoleLowPassFilter(float[] samples, float cutoff, float sampleRate)` | 6 dB/oct low-pass |
| `float[] onePoleHighPassFilter(float[] samples, float cutoff, float sampleRate)` | 6 dB/oct high-pass |
| `float[] allPass1Filter(float[] samples, float freq, float sampleRate)` | First-order allpass; −90° phase shift at `freq` |
| `float[] allPass2Filter(float[] samples, float freq, float bandwidth, float sampleRate)` | Second-order allpass; 0° to −360° phase sweep |
| `float[] blockDCFilter(float[] samples, float bandwidth, float sampleRate)` | Removes DC offset component |
| `float[] blockNyquistFilter(float[] samples, float bandwidth, float sampleRate)` | Removes Nyquist frequency component |
| `float[] notchFilter(float[] samples, float freq, float bandwidth, float sampleRate)` | Two-zero notch; eliminates a specific frequency |
| `float[] resonatorFilter(float[] samples, float freq, float bandwidth, float sampleRate)` | Two-pole resonator; amplifies center frequency |
| `float[] movingAverageFilter(float[] samples, int windowSize)` | FIR rectangular-window moving average |
| `float[] differencerFilter(float[] samples)` | Sample-to-sample difference; high-pass with zero at DC |
| `float[] integratorFilter(float[] samples, float leakCoefficient)` | Leaky integrator / simple low-pass accumulator |

#### Effects

| Method | Description |
|---|---|
| `float[] chorusEffect(float[] samples, float delay, float depth, float modFreq, float feedforward, float feedback, float sampleRate)` | Dual delay-line chorus with quadrature LFO modulation |
| `float[] quantizerEffect(float[] samples, float quantizationFreq, float amplitudeStep, float sampleRate)` | Bitcrusher: reduces sample rate and/or bit depth |
| `float[] biquad3Filter(float[] samples, float freq0, float freq1, float freq2, float resonance, float sampleRate)` | Three parallel band-pass biquads summed |

---

### SoundEncodingUtil

`llc.berserkr.gammalib.util.SoundEncodingUtil`

All methods use **little-endian** byte order throughout. 24-bit PCM uses a 3-byte packed format with sign extension on the MSB.

| Method | Description |
|---|---|
| `float[] unpack24BitToFloat(byte[] packedData)` | 24-bit PCM bytes → normalized floats [-1.0, 1.0] |
| `float[] pcm16ToFloat(byte[] pcmBytes)` | 16-bit PCM bytes → normalized floats |
| `float[] pcm24ToFloat(byte[] pcmBytes)` | 24-bit PCM bytes → normalized floats (via 32-bit intermediate) |
| `byte[] packFloatTo24Bit(float[] samples)` | Normalized floats → 24-bit PCM bytes |
| `byte[] packFloatTo16Bit(float[] samples)` | Normalized floats → 16-bit PCM bytes |
| `byte[] pack32BitTo24Bit(int[] samples)` | 32-bit int samples → 24-bit PCM bytes (range-clamped) |

---

### MP3ToPCMConverter

`llc.berserkr.gammalib.util.MP3ToPCMConverter`

#### MP3 → PCM

```java
// Decode an MP3 byte array to 24-bit PCM at 96 kHz (mono, little-endian).
// Resamples from the MP3's native rate via linear interpolation.
// Stereo is mixed down to mono by averaging channels.
byte[] pcm24 = MP3ToPCMConverter.mp3ToPcm24At96k(byte[] mp3Data);

// Decode an MP3 file path to a raw 16-bit PCM file.
MP3ToPCMConverter.convertTo16PCM(String mp3Path, String pcmPath);

// Decode an MP3 file path to a raw 24-bit PCM file.
MP3ToPCMConverter.convertTo24PCM(String mp3Path, String pcmPath);
```

#### PCM → MP3

```java
// Encode 24-bit 96 kHz mono PCM bytes to MP3 at 128 kbps via LAME (JNI).
// Decimates 96 kHz → 48 kHz (MP3 maximum) and truncates 24-bit → 16-bit.
byte[] mp3 = MP3ToPCMConverter.pcm24At96kToMp3(byte[] pcm24Data);
```

> **Note:** Android's `MediaCodec` does not support MP3 encoding. `pcm24At96kToMp3` uses [LAME](https://lame.sourceforge.io/) via JNI, which is downloaded from SourceForge and compiled as a static library at CMake configure time (first build only, ~600 KB download).

---

### Audio Capture

#### SoundRecorder

```java
AudioBufferLoader bufferLoader = new AudioBufferLoader(
    MediaRecorder.AudioSource.MIC,
    96_000,                              // sample rate Hz
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_24BIT_PACKED
);

SoundRecorder recorder = new SoundRecorder(
    bufferLoader,
    bufferLoader.getMinBufferSize()
);

// Returns a blocking InputStream of raw PCM bytes.
// Call stopRecording() from another thread to unblock it.
try (InputStream stream = recorder.startRecording()) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    StreamUtil.copyTo(stream, buffer);
    byte[] pcm24 = buffer.toByteArray();
}

recorder.stopRecording();
```

---

## Data Flow

```
AudioRecord (live mic)
        │  raw 24-bit PCM bytes — 96 kHz, mono, little-endian
        ▼
SoundEncodingUtil.unpack24BitToFloat()
        │  normalized float[] in [-1.0, 1.0]
        ▼
Gamma native methods (filter / analyze / effect)
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

---

## Building

```bash
# Build everything
./gradlew build

# Build only the library (debug)
./gradlew :lib:assembleDebug

# Build release AAR
./gradlew :lib:assembleRelease

# Clean
./gradlew clean
```

The first build downloads `lame-3.100.tar.gz` (~600 KB) from SourceForge at CMake configure time to compile the LAME MP3 encoder. Subsequent builds use the CMake download cache.

---

## Testing

### Local unit tests (no device required)

```bash
./gradlew :lib:test

# Run a specific class
./gradlew :lib:test --tests "llc.berserkr.gammalib.GammaUnitTest"
```

### Instrumented tests (requires connected device or emulator)

Instrumented tests use the audio assets in `lib/src/androidTest/assets/` and require the `RECORD_AUDIO` permission, which is granted automatically via `GrantPermissionRule`.

```bash
# Run all instrumented tests
./gradlew :lib:connectedAndroidTest

# Run individual tests
./gradlew :lib:connectedAndroidTest \
  --tests "llc.berserkr.gammalib.GammaTest#mp3ToPcm24At96kTest"

./gradlew :lib:connectedAndroidTest \
  --tests "llc.berserkr.gammalib.GammaTest#pcm24At96kToMp3Test"
```

Tests that produce audio output files save them to `Download/test-signals/` on the device. Pull them with:

```bash
adb pull /storage/emulated/0/Download/test-signals/ ./test-signals/
```

---

## Dependencies

### Runtime

| Dependency | Version | Purpose |
|---|---|---|
| [Gamma DSP](https://github.com/LancePutnam/Gamma) | vendored | C++ audio DSP — filters, oscillators, spectral processing |
| [LAME](https://lame.sourceforge.io/) | 3.100 (fetched at build time) | MP3 encoding via JNI |
| [SLF4J API](https://www.slf4j.org/) | 2.0.17 | Java logging facade |
| AndroidX AppCompat | — | Android support library |
| Android Material | — | Material Design components |

### Test

| Dependency | Purpose |
|---|---|
| JUnit 4 | Local unit tests |
| AndroidX Test — JUnit, Espresso, Rules | Instrumented tests |

---

## License

```
MIT License

Copyright (c) 2026 motocoder

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Third-party licenses

- **Gamma DSP** — see `lib/src/main/cpp/deps/gamma/` for original copyright and license
- **LAME** — LGPL 2.1; source fetched from SourceForge at build time
