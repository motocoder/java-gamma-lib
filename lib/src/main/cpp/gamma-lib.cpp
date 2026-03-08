#include <jni.h>
#include <string>
extern "C" {
    #include "berserkr.h"
}
#include "berserkr_plus.hpp"
#include <android/log.h>
#include "Analysis.h"
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
