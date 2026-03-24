#include <jni.h>
#include <string>
extern "C" {
    #include "berserkr.h"
}
#include "berserkr_plus.hpp"
#include <android/log.h>
#include "Analysis.h"
#include "Filter.h"
#include "Effects.h"
#include <cmath>
using namespace gam;

extern "C" JNIEXPORT void JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_initialize(
    JNIEnv* env,
    jobject /* this */
) {

    LOGI( "initializing gamma");

}
extern "C"
JNIEXPORT float JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_maxVolumeNormalize(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded
) {

    const jsize length = env->GetArrayLength(recorded);

    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Inspector inspector;

    for (int i = 0; i < length; ++i) {
        float sample = elements[i];
        inspector(sample);
    }

    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    const float linearPeak = inspector.peak();
    const float peakDB = (linearPeak > 0) ? 20.0f * std::log10(linearPeak) : -96.0f; //not sure if this should multiply by 10 or 20

    return peakDB;

}

extern "C"
JNIEXPORT jfloat JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_maxVolumePCM24Bytes(
    JNIEnv *env,
    jobject thiz,
    jbyteArray pcmByteArray
) {

    jsize byteLength = env->GetArrayLength(pcmByteArray);
    jbyte *bytes = env->GetByteArrayElements(pcmByteArray, nullptr);

    int sampleCount = byteLength / 3;
    std::vector<float> normalizedSamples(sampleCount);

    for (int i = 0; i < sampleCount; ++i) {
        int idx = i * 3;

        //Extract bytes (Little Endian)
        // We cast to unsigned to avoid unwanted sign extension during shifts
        uint8_t b0 = (uint8_t)bytes[idx];
        uint8_t b1 = (uint8_t)bytes[idx + 1];
        int8_t  b2 = (int8_t)bytes[idx + 2]; // Keep MSB signed for sign extension

        //Combine into a 32-bit signed integer (Sign Extension)
        // Shift MSB to the top of a 32-bit int, then shift back to maintain sign
        int32_t sample = (b2 << 16) | (b1 << 8) | b0;

        //Normalize to [-1.0, 1.0]
        // 2^23 is 8388608.0f
        normalizedSamples[i] = (float)sample / 8388608.0f;
    }

    env->ReleaseByteArrayElements(pcmByteArray, bytes, JNI_ABORT);

    gam::Inspector inspector;
    for (float s : normalizedSamples) {
        inspector(s);
    }

    const float linearPeak = inspector.peak();
    return (linearPeak > 0) ? 20.0f * std::log10(linearPeak) : -96.0f;

}

extern "C"
JNIEXPORT jboolean JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_detectSilence(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat threshold, // e.g., 0.001f
    jint count        // e.g., 1000 samples
) {

    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // Initialize Gamma Silence detector
    // It triggers true if 'count' samples in a row are below 'threshold'
    gam::SilenceDetect detector(count);
    bool silenceFound = false;

    for (int i = 0; i < length; ++i) {
        if (detector(elements[i], threshold)) {
            silenceFound = true;
            break; // Stop as soon as silence is detected
        }
    }

    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);
    return silenceFound;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_detectNoise(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat threshold // e.g., 0.01f
) {

    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // EnvFollow estimates the amplitude envelope.
    // The frequency (10.0f) determines how quickly it responds to changes.
    gam::EnvFollow<float> envFollow(10.0f);
    bool detected = false;

    for (int i = 0; i < length; ++i) {
        // If the envelope (smoothed amplitude) exceeds the threshold, sound is detected.
        if (envFollow(elements[i]) > threshold) {
            detected = true;
            break;
        }
    }

    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return (jboolean)detected;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_lowPassFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat cutoffFreq,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // 1. Create a local domain to set the sample rate for the filter
    gam::Domain domain(sampleRate);

    // 2. Initialize the Biquad filter as a LOW_PASS
    // Parameters: Frequency, Resonance (Q=0.707 is flat/Butterworth), Type
    gam::Biquad<float> lpFilter(cutoffFreq, 0.707 , gam::LOW_PASS);

    // Associate the filter with our local domain
    lpFilter.domain(domain);

    // 2. Iterate and process
    for (int i = 0; i < length; ++i) {
        // Elements are modified in place
        elements[i] = lpFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);

    env->SetFloatArrayRegion(result, 0, length, elements);

    // 3. Release and commit changes back to the Java array (0 means commit + free)
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_highPassFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat cutoffFreq,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // 1. Create a local domain to set the sample rate for the filter
    gam::Domain domain(sampleRate);

    // 2. Initialize the Biquad filter as a LOW_PASS
    // Parameters: Frequency, Resonance (Q=0.707 is flat/Butterworth), Type
    gam::Biquad<float> lpFilter(cutoffFreq, 0.707 , gam::HIGH_PASS);

    // Associate the filter with our local domain
    lpFilter.domain(domain);

    // 2. Iterate and process
    for (int i = 0; i < length; ++i) {
        // Elements are modified in place
        elements[i] = lpFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);

    env->SetFloatArrayRegion(result, 0, length, elements);

    // 3. Release and commit changes back to the Java array (0 means commit + free)
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

// --- Group 1A: Biquad filters without level parameter ---

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

    gam::Domain domain(sampleRate);

    // Initialize the Biquad filter as BAND_PASS
    // Parameters: Frequency, Resonance (Q), Type
    gam::Biquad<float> bpFilter(centerFrequency, resonance, gam::BAND_PASS);
    bpFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = bpFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_resonantFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat centerFrequency,
    jfloat resonance,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Initialize the Biquad filter as RESONANT
    // Like band-pass but with constant skirt gain — peak gain equals Q
    gam::Biquad<float> resFilter(centerFrequency, resonance, gam::RESONANT);
    resFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = resFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_bandRejectFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat centerFrequency,
    jfloat resonance,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Initialize the Biquad filter as BAND_REJECT (notch)
    // Removes frequencies near centerFrequency, passes everything else
    gam::Biquad<float> brFilter(centerFrequency, resonance, gam::BAND_REJECT);
    brFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = brFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_allPassBiquadFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat centerFrequency,
    jfloat resonance,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Initialize the Biquad filter as ALL_PASS
    // Passes all frequencies at equal amplitude but shifts their phase
    gam::Biquad<float> apFilter(centerFrequency, resonance, gam::ALL_PASS);
    apFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = apFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

// --- Group 1B: Biquad filters with level parameter ---

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_peakingFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency,
    jfloat resonance,
    jfloat level,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Initialize the Biquad filter as PEAKING (parametric EQ)
    // The level parameter controls boost/cut amount (linear amplitude)
    gam::Biquad<float> pkFilter(frequency, resonance, gam::PEAKING, level);
    pkFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = pkFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_lowShelfFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency,
    jfloat resonance,
    jfloat level,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Initialize the Biquad filter as LOW_SHELF
    // Boosts or cuts all frequencies below the shelf frequency
    gam::Biquad<float> lsFilter(frequency, resonance, gam::LOW_SHELF, level);
    lsFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = lsFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_highShelfFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency,
    jfloat resonance,
    jfloat level,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Initialize the Biquad filter as HIGH_SHELF
    // Boosts or cuts all frequencies above the shelf frequency
    gam::Biquad<float> hsFilter(frequency, resonance, gam::HIGH_SHELF, level);
    hsFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = hsFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

// --- Group 2: Standalone filter classes ---

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_onePoleLowPassFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat cutoffFrequency,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Single-pole IIR filter — 6 dB/octave slope, much gentler than Biquad
    gam::OnePole<float> opFilter(cutoffFrequency, gam::LOW_PASS);
    opFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = opFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_onePoleHighPassFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat cutoffFrequency,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Single-pole IIR high-pass filter — 6 dB/octave slope
    gam::OnePole<float> opFilter(cutoffFrequency, gam::HIGH_PASS);
    opFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = opFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_allPass1Filter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // First-order all-pass — shifts phase from 0 to -180°, with -90° at frequency
    gam::AllPass1<float> ap1Filter(frequency);
    ap1Filter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = ap1Filter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_allPass2Filter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency,
    jfloat bandwidth,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Second-order all-pass — shifts phase from 0 to -360°
    gam::AllPass2<float> ap2Filter(frequency, bandwidth);
    ap2Filter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = ap2Filter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_blockDCFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat bandwidth,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Removes DC offset (0 Hz component) — specialized high-pass with very low cutoff
    gam::BlockDC<float> dcFilter(bandwidth);
    dcFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = dcFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_blockNyquistFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat bandwidth,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Removes Nyquist frequency component — specialized low-pass at the top of the spectrum
    gam::BlockNyq<float> nyqFilter(bandwidth);
    nyqFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = nyqFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_notchFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency,
    jfloat bandwidth,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Two-zero notch filter — completely eliminates a specific frequency
    // Uses zeros only (no feedback poles), different from Biquad BAND_REJECT
    gam::Notch<float> ntchFilter(frequency, bandwidth);
    ntchFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = ntchFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_resonatorFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency,
    jfloat bandwidth,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Two-pole resonator — strongly amplifies frequencies near center frequency
    gam::Reson<float> resonFilter(frequency, bandwidth);
    resonFilter.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = resonFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

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

    // FIR moving average — no domain needed, operates purely on sample values
    // Cutoff frequency is approximately sampleRate / windowSize
    gam::MovingAvg<float> maFilter(windowSize);

    for (int i = 0; i < length; ++i) {
        elements[i] = maFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_differencerFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // Returns the difference between current and previous sample
    // Acts as a high-pass filter with a zero at DC — no parameters needed
    gam::Differencer<float> diffFilter;

    for (int i = 0; i < length; ++i) {
        elements[i] = diffFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_integratorFilter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat leakCoefficient
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    // Accumulates input values with a leak coefficient
    // Without leak, sums indefinitely; with leak, acts as a simple low-pass
    gam::Integrator<float> intFilter(leakCoefficient);

    for (int i = 0; i < length; ++i) {
        elements[i] = intFilter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

// --- Group 3: Effects ---

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

    gam::Domain domain(sampleRate);

    // Dual delay-line chorus driven by a quadrature sinusoid modulator
    // Parameters: delay interval, modulation depth, modulation frequency, feedforward, feedback
    gam::Chorus<float> chorus(delay, depth, modulationFrequency, feedforward, feedback);

    // Associate internal comb filters with our domain
    chorus.comb1.domain(domain);
    chorus.comb2.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = chorus(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_quantizerEffect(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat quantizationFrequency,
    jfloat amplitudeStep,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Bitcrusher/downsampler — reduces sample rate and/or bit depth
    gam::Quantizer<float> quantizer(quantizationFrequency, amplitudeStep);
    quantizer.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = quantizer(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_llc_berserkr_gammalib_jni_Gamma_biquad3Filter(
    JNIEnv *env,
    jobject thiz,
    jfloatArray recorded,
    jfloat frequency0,
    jfloat frequency1,
    jfloat frequency2,
    jfloat resonance,
    jfloat sampleRate
) {
    const jsize length = env->GetArrayLength(recorded);
    jfloat *elements = env->GetFloatArrayElements(recorded, nullptr);

    gam::Domain domain(sampleRate);

    // Three Biquad band-pass filters summed in parallel
    // Biquad3 is not a template — it uses Biquad<> (default float) internally
    gam::Biquad3 bq3Filter(frequency0, frequency1, frequency2, resonance, gam::BAND_PASS);

    // Each internal biquad needs its domain set individually
    bq3Filter.bq0.domain(domain);
    bq3Filter.bq1.domain(domain);
    bq3Filter.bq2.domain(domain);

    for (int i = 0; i < length; ++i) {
        elements[i] = bq3Filter(elements[i]);
    }

    jfloatArray result = env->NewFloatArray(length);
    env->SetFloatArrayRegion(result, 0, length, elements);
    env->ReleaseFloatArrayElements(recorded, elements, JNI_ABORT);

    return result;
}
