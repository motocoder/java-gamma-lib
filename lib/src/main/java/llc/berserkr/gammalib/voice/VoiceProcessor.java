package llc.berserkr.gammalib.voice;

import llc.berserkr.gammalib.jni.Gamma;
import llc.berserkr.gammalib.util.SoundEncodingUtil;

/**
 * High-level voice audio processing API for walkie-talkie applications.
 * <p>
 * All methods are available in two forms:
 * <ul>
 *   <li>Normalized float[] [-1.0, 1.0] — for direct DSP pipeline integration</li>
 *   <li>PCM16 byte[] (little-endian signed 16-bit) — for direct stream integration</li>
 * </ul>
 * <p>
 * Typical usage in a recording pipeline:
 * <pre>
 *   VoiceProcessor vp = new VoiceProcessor(16000);
 *   byte[] pcmChunk = ...; // from AudioRecord
 *   if (!vp.isSilent(pcmChunk, -40f, 160)) {
 *       pcmChunk = vp.applyVoiceBandPass(pcmChunk);
 *       pcmChunk = vp.boostPresence(pcmChunk, 1.3f);
 *       // encode and send
 *   }
 * </pre>
 */
public class VoiceProcessor {

    private final Gamma gamma;
    private final float sampleRate;

    /**
     * @param sampleRate audio sample rate in Hz (e.g. 16000 for walkie-talkie)
     */
    public VoiceProcessor(float sampleRate) {
        this.gamma = new Gamma();
        this.sampleRate = sampleRate;
    }

    // =========================================================================
    // DETECTION / ANALYSIS
    // =========================================================================

    /**
     * Detects whether the audio buffer is silent.
     * Useful for voice activity detection — skip transmitting dead air.
     *
     * @param samples   normalized float samples
     * @param threshold amplitude threshold (linear, e.g. 0.01f)
     * @param consecutiveSamples number of consecutive samples below threshold to qualify as silence
     * @return true if silence detected
     */
    public boolean isSilent(float[] samples, float threshold, int consecutiveSamples) {
        return gamma.detectSilence(samples, threshold, consecutiveSamples);
    }

    /**
     * PCM16 overload.
     * @see #isSilent(float[], float, int)
     */
    public boolean isSilent(byte[] pcm16, float threshold, int consecutiveSamples) {
        return isSilent(PCM16.toNormalized(pcm16), threshold, consecutiveSamples);
    }

    /**
     * Detects noise using an envelope follower — ignores transient pops.
     * Returns true if sustained noise above threshold is present.
     *
     * @param samples   normalized float samples
     * @param threshold amplitude threshold (linear)
     * @return true if noise detected
     */
    public boolean hasNoise(float[] samples, float threshold) {
        return gamma.detectNoise(samples, threshold);
    }

    /**
     * PCM16 overload.
     * @see #hasNoise(float[], float)
     */
    public boolean hasNoise(byte[] pcm16, float threshold) {
        return hasNoise(PCM16.toNormalized(pcm16), threshold);
    }

    /**
     * Returns the peak amplitude in dB of the signal.
     * Useful for level metering or implementing AGC logic.
     *
     * @param samples normalized float samples
     * @return peak amplitude in dB (0 dB = full scale)
     */
    public float peakLevelDb(float[] samples) {
        return gamma.maxVolumeNormalize(samples);
    }

    /**
     * PCM16 overload.
     * @see #peakLevelDb(float[])
     */
    public float peakLevelDb(byte[] pcm16) {
        return peakLevelDb(PCM16.toNormalized(pcm16));
    }

    // =========================================================================
    // VOICE CLARITY FILTERS
    // =========================================================================

    /**
     * Band-pass filter restricting audio to voice frequencies (300-3400 Hz).
     * Mimics radio communications — removes rumble and hiss in one pass.
     *
     * @param samples normalized float samples
     * @return filtered samples
     */
    public float[] applyVoiceBandPass(float[] samples) {
        return gamma.bandPassFilter(samples, 1850f, 1.0f, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #applyVoiceBandPass(float[])
     */
    public byte[] applyVoiceBandPass(byte[] pcm16) {
        return PCM16.fromNormalized(applyVoiceBandPass(PCM16.toNormalized(pcm16)));
    }

    /**
     * High-pass filter to remove low-frequency rumble, wind, and handling noise.
     *
     * @param samples normalized float samples
     * @param cutoffHz cutoff frequency in Hz (typical: 150-300)
     * @return filtered samples
     */
    public float[] removeRumble(float[] samples, float cutoffHz) {
        return gamma.highPassFilter(samples, cutoffHz, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #removeRumble(float[], float)
     */
    public byte[] removeRumble(byte[] pcm16, float cutoffHz) {
        return PCM16.fromNormalized(removeRumble(PCM16.toNormalized(pcm16), cutoffHz));
    }

    /**
     * Removes DC offset from the signal, preventing clipping artifacts.
     *
     * @param samples normalized float samples
     * @return filtered samples with DC removed
     */
    public float[] blockDC(float[] samples) {
        return gamma.blockDCFilter(samples, 20f, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #blockDC(float[])
     */
    public byte[] blockDC(byte[] pcm16) {
        return PCM16.fromNormalized(blockDC(PCM16.toNormalized(pcm16)));
    }

    /**
     * Notch filter to remove a specific interference frequency (e.g. 50/60 Hz hum).
     *
     * @param samples   normalized float samples
     * @param frequency frequency to eliminate in Hz
     * @param bandwidth width of the notch in Hz
     * @return filtered samples
     */
    public float[] removeFrequency(float[] samples, float frequency, float bandwidth) {
        return gamma.notchFilter(samples, frequency, bandwidth, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #removeFrequency(float[], float, float)
     */
    public byte[] removeFrequency(byte[] pcm16, float frequency, float bandwidth) {
        return PCM16.fromNormalized(removeFrequency(PCM16.toNormalized(pcm16), frequency, bandwidth));
    }

    /**
     * Gentle smoothing using a moving average filter.
     * Reduces high-frequency harshness without aggressive filtering.
     *
     * @param samples    normalized float samples
     * @param windowSize averaging window (typical: 2-5 for subtle smoothing)
     * @return filtered samples
     */
    public float[] smooth(float[] samples, int windowSize) {
        return gamma.movingAverageFilter(samples, windowSize);
    }

    /**
     * PCM16 overload.
     * @see #smooth(float[], int)
     */
    public byte[] smooth(byte[] pcm16, int windowSize) {
        return PCM16.fromNormalized(smooth(PCM16.toNormalized(pcm16), windowSize));
    }

    // =========================================================================
    // EQUALIZATION
    // =========================================================================

    /**
     * Boosts presence/clarity in the 2-3 kHz range for speech intelligibility.
     *
     * @param samples normalized float samples
     * @param level   boost amount (1.0 = no change, 1.3 = moderate boost, 1.6 = strong boost)
     * @return equalized samples
     */
    public float[] boostPresence(float[] samples, float level) {
        return gamma.peakingFilter(samples, 2500f, 1.5f, level, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #boostPresence(float[], float)
     */
    public byte[] boostPresence(byte[] pcm16, float level) {
        return PCM16.fromNormalized(boostPresence(PCM16.toNormalized(pcm16), level));
    }

    /**
     * Reduces boominess from close-mic / proximity effect.
     *
     * @param samples normalized float samples
     * @param level   cut amount (1.0 = no change, 0.5 = moderate cut, 0.3 = strong cut)
     * @return equalized samples
     */
    public float[] reduceBoominess(float[] samples, float level) {
        return gamma.lowShelfFilter(samples, 300f, 0.7f, level, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #reduceBoominess(float[], float)
     */
    public byte[] reduceBoominess(byte[] pcm16, float level) {
        return PCM16.fromNormalized(reduceBoominess(PCM16.toNormalized(pcm16), level));
    }

    /**
     * Adds air/crispness to voice by boosting high frequencies.
     *
     * @param samples normalized float samples
     * @param level   boost amount (1.0 = no change, 1.3 = moderate, 1.5 = strong)
     * @return equalized samples
     */
    public float[] addAir(float[] samples, float level) {
        return gamma.highShelfFilter(samples, 4000f, 0.7f, level, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #addAir(float[], float)
     */
    public byte[] addAir(byte[] pcm16, float level) {
        return PCM16.fromNormalized(addAir(PCM16.toNormalized(pcm16), level));
    }

    /**
     * Parametric EQ — boost or cut any frequency band.
     *
     * @param samples   normalized float samples
     * @param frequency center frequency in Hz
     * @param q         resonance / Q (higher = narrower band)
     * @param level     linear level (1.0 = unity, &gt;1.0 = boost, &lt;1.0 = cut)
     * @return equalized samples
     */
    public float[] eq(float[] samples, float frequency, float q, float level) {
        return gamma.peakingFilter(samples, frequency, q, level, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #eq(float[], float, float, float)
     */
    public byte[] eq(byte[] pcm16, float frequency, float q, float level) {
        return PCM16.fromNormalized(eq(PCM16.toNormalized(pcm16), frequency, q, level));
    }

    // =========================================================================
    // VOICE EFFECTS
    // =========================================================================

    /**
     * Lo-fi radio effect — downsample and bit-crush for authentic walkie-talkie character.
     *
     * @param samples              normalized float samples
     * @param quantizationFreq     target effective sample rate in Hz (e.g. 8000 for lo-fi)
     * @param amplitudeStep        bit depth reduction (0 = none, 0.02 = subtle, 0.05 = heavy)
     * @return processed samples
     */
    public float[] radioEffect(float[] samples, float quantizationFreq, float amplitudeStep) {
        return gamma.quantizerEffect(samples, quantizationFreq, amplitudeStep, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #radioEffect(float[], float, float)
     */
    public byte[] radioEffect(byte[] pcm16, float quantizationFreq, float amplitudeStep) {
        return PCM16.fromNormalized(radioEffect(PCM16.toNormalized(pcm16), quantizationFreq, amplitudeStep));
    }

    /**
     * Tinny radio resonance — emphasizes a narrow frequency to sound like a cheap radio speaker.
     *
     * @param samples        normalized float samples
     * @param frequency      center frequency in Hz (typical: 1200-2000)
     * @param resonance      Q factor (higher = more tinny, typical: 5-10)
     * @return processed samples
     */
    public float[] tinnyEffect(float[] samples, float frequency, float resonance) {
        return gamma.resonantFilter(samples, frequency, resonance, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #tinnyEffect(float[], float, float)
     */
    public byte[] tinnyEffect(byte[] pcm16, float frequency, float resonance) {
        return PCM16.fromNormalized(tinnyEffect(PCM16.toNormalized(pcm16), frequency, resonance));
    }

    /**
     * Formant emphasis — boosts three frequency bands characteristic of human voice formants.
     * Can modify voice character or improve intelligibility.
     *
     * @param samples    normalized float samples
     * @param formant1   first formant frequency in Hz (typical: 500-900)
     * @param formant2   second formant frequency in Hz (typical: 1000-2500)
     * @param formant3   third formant frequency in Hz (typical: 2500-3500)
     * @param resonance  Q for all bands
     * @return processed samples
     */
    public float[] formantFilter(float[] samples, float formant1, float formant2, float formant3, float resonance) {
        return gamma.biquad3Filter(samples, formant1, formant2, formant3, resonance, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #formantFilter(float[], float, float, float, float)
     */
    public byte[] formantFilter(byte[] pcm16, float formant1, float formant2, float formant3, float resonance) {
        return PCM16.fromNormalized(formantFilter(PCM16.toNormalized(pcm16), formant1, formant2, formant3, resonance));
    }

    /**
     * Chorus effect — thickens voice with modulated delay lines.
     * Can be used for scrambled/encrypted voice aesthetic.
     *
     * @param samples             normalized float samples
     * @param delay               delay in seconds (typical: 0.005-0.02)
     * @param depth               modulation depth (typical: 0.001-0.005)
     * @param modulationFrequency LFO rate in Hz (typical: 0.3-1.0)
     * @param feedforward         feedforward mix (typical: 0.5-0.8)
     * @param feedback            feedback mix (typical: 0.2-0.5)
     * @return processed samples
     */
    public float[] chorus(float[] samples, float delay, float depth, float modulationFrequency, float feedforward, float feedback) {
        return gamma.chorusEffect(samples, delay, depth, modulationFrequency, feedforward, feedback, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #chorus(float[], float, float, float, float, float)
     */
    public byte[] chorus(byte[] pcm16, float delay, float depth, float modulationFrequency, float feedforward, float feedback) {
        return PCM16.fromNormalized(chorus(PCM16.toNormalized(pcm16), delay, depth, modulationFrequency, feedforward, feedback));
    }

    // =========================================================================
    // DIRECT ACCESS FILTERS (for advanced use)
    // =========================================================================

    /**
     * Low-pass filter with custom cutoff.
     *
     * @param samples  normalized float samples
     * @param cutoffHz cutoff frequency in Hz
     * @return filtered samples
     */
    public float[] lowPass(float[] samples, float cutoffHz) {
        return gamma.lowPassFilter(samples, cutoffHz, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #lowPass(float[], float)
     */
    public byte[] lowPass(byte[] pcm16, float cutoffHz) {
        return PCM16.fromNormalized(lowPass(PCM16.toNormalized(pcm16), cutoffHz));
    }

    /**
     * High-pass filter with custom cutoff.
     *
     * @param samples  normalized float samples
     * @param cutoffHz cutoff frequency in Hz
     * @return filtered samples
     */
    public float[] highPass(float[] samples, float cutoffHz) {
        return gamma.highPassFilter(samples, cutoffHz, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #highPass(float[], float)
     */
    public byte[] highPass(byte[] pcm16, float cutoffHz) {
        return PCM16.fromNormalized(highPass(PCM16.toNormalized(pcm16), cutoffHz));
    }

    /**
     * Band-pass filter with custom center frequency and Q.
     *
     * @param samples         normalized float samples
     * @param centerFrequency center frequency in Hz
     * @param resonance       Q factor
     * @return filtered samples
     */
    public float[] bandPass(float[] samples, float centerFrequency, float resonance) {
        return gamma.bandPassFilter(samples, centerFrequency, resonance, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #bandPass(float[], float, float)
     */
    public byte[] bandPass(byte[] pcm16, float centerFrequency, float resonance) {
        return PCM16.fromNormalized(bandPass(PCM16.toNormalized(pcm16), centerFrequency, resonance));
    }

    /**
     * Band-reject filter — removes frequencies around a center.
     *
     * @param samples         normalized float samples
     * @param centerFrequency center frequency in Hz
     * @param resonance       Q factor
     * @return filtered samples
     */
    public float[] bandReject(float[] samples, float centerFrequency, float resonance) {
        return gamma.bandRejectFilter(samples, centerFrequency, resonance, sampleRate);
    }

    /**
     * PCM16 overload.
     * @see #bandReject(float[], float, float)
     */
    public byte[] bandReject(byte[] pcm16, float centerFrequency, float resonance) {
        return PCM16.fromNormalized(bandReject(PCM16.toNormalized(pcm16), centerFrequency, resonance));
    }

    // =========================================================================
    // PCM16 CONVERSION UTILITIES
    // =========================================================================

    /**
     * Static utility class for converting between PCM16 byte arrays (the format used
     * by Android AudioRecord at 16-bit encoding) and normalized float arrays [-1.0, 1.0]
     * (the format used by gammalib's native DSP methods).
     * <p>
     * PCM16 format: signed 16-bit little-endian, 2 bytes per sample.
     */
    public static final class PCM16 {

        private PCM16() {}

        /**
         * Converts PCM16 little-endian byte array to normalized float array [-1.0, 1.0].
         *
         * @param pcm16Bytes raw PCM16 bytes from AudioRecord (must be even length)
         * @return normalized float array suitable for all gammalib processing methods
         * @throws IllegalArgumentException if input is null or odd length
         */
        public static float[] toNormalized(byte[] pcm16Bytes) {
            return SoundEncodingUtil.pcm16ToFloat(pcm16Bytes);
        }

        /**
         * Converts normalized float array [-1.0, 1.0] back to PCM16 little-endian byte array.
         *
         * @param normalized float samples in [-1.0, 1.0] range (clamped internally)
         * @return PCM16 byte array ready for AudioTrack or ADPCM encoding
         * @throws IllegalArgumentException if input is null
         */
        public static byte[] fromNormalized(float[] normalized) {
            return SoundEncodingUtil.packFloatTo16Bit(normalized);
        }

        /**
         * Converts a PCM16 byte array to normalized floats, suitable for passing
         * into the raw Gamma native methods directly.
         * <p>
         * This is an alias for {@link #toNormalized(byte[])} for readability at call sites
         * where the intent is to prepare data for Gamma JNI calls.
         *
         * @param pcm16Bytes raw PCM16 bytes
         * @return normalized float array
         */
        public static float[] forProcessing(byte[] pcm16Bytes) {
            return SoundEncodingUtil.pcm16ToFloat(pcm16Bytes);
        }

        /**
         * Returns the number of audio samples represented by the given PCM16 byte array.
         *
         * @param pcm16Bytes raw PCM16 bytes
         * @return number of samples (byte length / 2)
         */
        public static int sampleCount(byte[] pcm16Bytes) {
            if (pcm16Bytes == null) return 0;
            return pcm16Bytes.length / 2;
        }

        /**
         * Returns the byte count needed to represent the given number of samples in PCM16.
         *
         * @param sampleCount number of audio samples
         * @return byte count (sampleCount * 2)
         */
        public static int byteCount(int sampleCount) {
            return sampleCount * 2;
        }
    }
}
