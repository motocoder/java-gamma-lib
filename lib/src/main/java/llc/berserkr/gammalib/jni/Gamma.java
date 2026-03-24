package llc.berserkr.gammalib.jni;

public class Gamma {

    static {
        System.loadLibrary("gamma_lib");
    }

    public native void initialize();

    public native float maxVolumeNormalize(float[] recorded);

    public native float maxVolumePCM24Bytes(byte[] recorded);

    /**
     *
     * @param data - normalized [-1.0, 1.0] array of floats
     * @param threshhold - threshold to be silent below
     * @param sampleSize - consecutive samples to be below threshhold
     * @return - true if silence is detected
     */
    public native boolean detectSilence(float[] data, float threshhold, int sampleSize);

    /**
     * Detects noise in a float array ignores small pops and crap
     *
     * @param data - normalized [-1.0, 1.0] array of floats
     * @param threshhold - threshold to be silent below
     * @return
     */
    public native boolean detectNoise(float[] data, float threshhold);

    public native float [] lowPassFilter(float[] normalized, float cutoff, float sampleRate);

    public native float[] highPassFilter(float[] normalized, float v, float v1);

    // --- Group 1A: Biquad filters without level parameter ---

    /**
     * Band-pass filter — passes frequencies near centerFrequency, attenuates everything else.
     * Uses a 2-pole/2-zero Biquad IIR filter (12 dB/octave slope).
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param centerFrequency  center frequency in Hz
     * @param resonance        resonance (Q) amount — higher values narrow the passband
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] bandPassFilter(float[] normalizedSamples, float centerFrequency, float resonance, float sampleRate);

    /**
     * Resonant band-pass filter — like band-pass but with constant skirt gain (peak gain equals Q).
     * Emphasizes the center frequency more aggressively than standard band-pass.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param centerFrequency  center frequency in Hz
     * @param resonance        resonance (Q) amount
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] resonantFilter(float[] normalizedSamples, float centerFrequency, float resonance, float sampleRate);

    /**
     * Band-reject (notch) filter — removes frequencies near centerFrequency, passes everything else.
     * Uses a 2-pole/2-zero Biquad IIR filter.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param centerFrequency  center frequency in Hz to reject
     * @param resonance        resonance (Q) amount — higher values narrow the rejected band
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] bandRejectFilter(float[] normalizedSamples, float centerFrequency, float resonance, float sampleRate);

    /**
     * All-pass Biquad filter — passes all frequencies at equal amplitude but shifts their phase.
     * Useful for phase-based effects (phasers, flangers).
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param centerFrequency  center frequency in Hz where phase shift is -180°
     * @param resonance        resonance (Q) amount
     * @param sampleRate       sample rate in Hz
     * @return filtered float array (same amplitude, shifted phase)
     */
    public native float[] allPassBiquadFilter(float[] normalizedSamples, float centerFrequency, float resonance, float sampleRate);

    // --- Group 1B: Biquad filters with level parameter ---

    /**
     * Peaking (parametric EQ) filter — boosts or cuts a band of frequencies by a specified amount.
     * Frequencies outside the band are unaffected.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency        center frequency in Hz
     * @param resonance        resonance (Q) amount
     * @param level            linear amplitude level (1.0 = unity, >1.0 = boost, <1.0 = cut)
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] peakingFilter(float[] normalizedSamples, float frequency, float resonance, float level, float sampleRate);

    /**
     * Low-shelf filter — boosts or cuts all frequencies below the shelf frequency.
     * Frequencies above the shelf frequency are unaffected.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency        shelf frequency in Hz
     * @param resonance        resonance (Q) amount
     * @param level            linear amplitude level (1.0 = unity, >1.0 = boost, <1.0 = cut)
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] lowShelfFilter(float[] normalizedSamples, float frequency, float resonance, float level, float sampleRate);

    /**
     * High-shelf filter — boosts or cuts all frequencies above the shelf frequency.
     * Frequencies below the shelf frequency are unaffected.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency        shelf frequency in Hz
     * @param resonance        resonance (Q) amount
     * @param level            linear amplitude level (1.0 = unity, >1.0 = boost, <1.0 = cut)
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] highShelfFilter(float[] normalizedSamples, float frequency, float resonance, float level, float sampleRate);

    // --- Group 2: Standalone filter classes ---

    /**
     * One-pole low-pass filter — simplest IIR filter with 6 dB/octave slope.
     * Much gentler roll-off than Biquad.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param cutoffFrequency  cutoff frequency in Hz
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] onePoleLowPassFilter(float[] normalizedSamples, float cutoffFrequency, float sampleRate);

    /**
     * One-pole high-pass filter — simplest IIR high-pass with 6 dB/octave slope.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param cutoffFrequency  cutoff frequency in Hz
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] onePoleHighPassFilter(float[] normalizedSamples, float cutoffFrequency, float sampleRate);

    /**
     * First-order all-pass filter — shifts phase from 0 to -180° across the spectrum,
     * with -90° at the specified frequency. Amplitude is preserved.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency        frequency in Hz where phase shift is -90°
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] allPass1Filter(float[] normalizedSamples, float frequency, float sampleRate);

    /**
     * Second-order all-pass filter — shifts phase from 0 to -360° across the spectrum.
     * More pronounced phase shift than AllPass1.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency        center frequency in Hz
     * @param bandwidth        bandwidth of phase transition in Hz
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] allPass2Filter(float[] normalizedSamples, float frequency, float bandwidth, float sampleRate);

    /**
     * Removes the DC offset (0 Hz component) from a signal.
     * A specialized high-pass filter with very low cutoff.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param bandwidth        bandwidth of the DC-blocking pole
     * @param sampleRate       sample rate in Hz
     * @return filtered float array with DC offset removed
     */
    public native float[] blockDCFilter(float[] normalizedSamples, float bandwidth, float sampleRate);

    /**
     * Removes the Nyquist frequency component from a signal.
     * A specialized low-pass that cuts only the very top of the spectrum.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param bandwidth        bandwidth of the Nyquist-blocking pole
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] blockNyquistFilter(float[] normalizedSamples, float bandwidth, float sampleRate);

    /**
     * Two-zero notch filter — completely eliminates a specific frequency.
     * Uses zeros only (no feedback poles), different from Biquad band-reject.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency        frequency in Hz to eliminate
     * @param bandwidth        bandwidth of the notch in Hz
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] notchFilter(float[] normalizedSamples, float frequency, float bandwidth, float sampleRate);

    /**
     * Two-pole resonator — strongly amplifies frequencies at and near the center frequency.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency        center frequency in Hz
     * @param bandwidth        bandwidth of resonance in Hz
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] resonatorFilter(float[] normalizedSamples, float frequency, float bandwidth, float sampleRate);

    /**
     * FIR moving average low-pass filter using a rectangular window.
     * Cutoff frequency is approximately sampleRate / windowSize.
     * Does not require a sample rate parameter — operates purely on sample values.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param windowSize       number of samples in the averaging window
     * @return filtered float array
     */
    public native float[] movingAverageFilter(float[] normalizedSamples, int windowSize);

    /**
     * Returns the difference between current and previous sample.
     * Acts as a high-pass filter with a zero at DC.
     * Does not require any parameters beyond the input signal.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @return filtered float array of sample-to-sample differences
     */
    public native float[] differencerFilter(float[] normalizedSamples);

    /**
     * Accumulates input values with a leak coefficient.
     * Without leak (1.0), sums indefinitely; with leak (<1.0), acts as a simple low-pass.
     * Does not require a sample rate parameter — operates purely on sample values.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param leakCoefficient  leak coefficient in [0, 1) — controls how quickly accumulated value decays
     * @return filtered float array
     */
    public native float[] integratorFilter(float[] normalizedSamples, float leakCoefficient);

    // --- Group 3: Effects ---

    /**
     * Dual delay-line chorus effect — creates a thickened, shimmering sound
     * by modulating two comb-filtered delay lines with a quadrature sinusoid.
     *
     * @param normalizedSamples    normalized [-1.0, 1.0] array of floats
     * @param delay                delay interval in seconds
     * @param depth                depth of delay-line modulation
     * @param modulationFrequency  frequency of modulation in Hz
     * @param feedforward          feedforward amount
     * @param feedback             feedback amount
     * @param sampleRate           sample rate in Hz
     * @return processed float array
     */
    public native float[] chorusEffect(float[] normalizedSamples, float delay, float depth, float modulationFrequency, float feedforward, float feedback, float sampleRate);

    /**
     * Bitcrusher/downsampler effect — reduces sample rate and/or bit depth of the signal.
     *
     * @param normalizedSamples      normalized [-1.0, 1.0] array of floats
     * @param quantizationFrequency  frequency of sequence quantization in Hz
     * @param amplitudeStep          step amount of amplitude quantization (0 = no quantization)
     * @param sampleRate             sample rate in Hz
     * @return processed float array
     */
    public native float[] quantizerEffect(float[] normalizedSamples, float quantizationFrequency, float amplitudeStep, float sampleRate);

    /**
     * Three Biquad band-pass filters summed in parallel, each at a different center frequency.
     * Produces a multi-band filtered output useful for formant-like filtering.
     *
     * @param normalizedSamples normalized [-1.0, 1.0] array of floats
     * @param frequency0       center frequency of first filter in Hz
     * @param frequency1       center frequency of second filter in Hz
     * @param frequency2       center frequency of third filter in Hz
     * @param resonance        resonance (Q) for all three filters
     * @param sampleRate       sample rate in Hz
     * @return filtered float array
     */
    public native float[] biquad3Filter(float[] normalizedSamples, float frequency0, float frequency1, float frequency2, float resonance, float sampleRate);

    public static class ProcessResult {

    }
}
