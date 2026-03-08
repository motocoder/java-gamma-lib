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

    public static class ProcessResult {

    }
}
