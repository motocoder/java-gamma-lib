package llc.berserkr.gammalib.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SoundEncodingUtil {

    private static final Logger logger = LoggerFactory.getLogger(SoundEncodingUtil.class);

    public float pcm32ToFloat(int sample) {
        return sample / 2147483648.0f;
    }

    public static byte[] pack32BitTo24Bit(int[] samples) {
        if (samples == null) {
            throw new IllegalArgumentException("Samples array cannot be null.");
        }

        byte[] packedData = new byte[samples.length * 3];

        for (int i = 0; i < samples.length; i++) {
            int sample = samples[i];

            // Clamp to 24-bit signed range
            if (sample > 8388607) sample = 8388607;
            if (sample < -8388608) sample = -8388608;

            // Little-endian: LSB first
            packedData[i * 3]     = (byte) (sample & 0xFF);         // LSB
            packedData[i * 3 + 1] = (byte) ((sample >> 8) & 0xFF);  // Middle byte
            packedData[i * 3 + 2] = (byte) ((sample >> 16) & 0xFF); // MSB (sign bit included)
        }

        return packedData;
    }

    /**
     * Converts normalized float samples [-1.0, 1.0] to 16-bit PCM byte array (Little-Endian).
     *
     * @param samples Normalized float samples.
     * @return 16-bit PCM byte array.
     */
    public static byte[] packFloatTo16Bit(float[] samples) {
        if (samples == null) {
            throw new IllegalArgumentException("Samples array cannot be null.");
        }

        byte[] packedData = new byte[samples.length * 2];
        ByteBuffer buffer = ByteBuffer.wrap(packedData).order(ByteOrder.LITTLE_ENDIAN);

        for (float sample : samples) {
            // Clamp to [-1.0, 1.0] to prevent overflow
            if (sample > 1.0f) sample = 1.0f;
            if (sample < -1.0f) sample = -1.0f;

            // Scale to 16-bit signed range (-32768 to 32767)
            short shortSample = (short) (sample * 32767.0f);
            buffer.putShort(shortSample);
        }

        return packedData;
    }

    public static float[] unpack24BitToFloat(byte[] packedData) {
        if (packedData == null) {
            throw new IllegalArgumentException("Data must be non-null and length multiple of 3 bytes.");
        }

        if (packedData.length % 3 != 0) {
            logger.warn("Data should be length multiple of 3 bytes.");
        }

        int sampleCount = packedData.length / 3;
        float[] floatSamples = new float[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int byteIndex = i * 3;

            // Little-endian: LSB first
            int b0 = packedData[byteIndex] & 0xFF;
            int b1 = packedData[byteIndex + 1] & 0xFF;
            int b2 = packedData[byteIndex + 2]; // signed

            // Sign-extend to 32-bit
            int sample = (b2 << 16) | (b1 << 8) | b0;

            // Normalize: divide by max positive value (8388608.0f)
            // Note: 24-bit signed PCM range is -8388608 to 8388607
            floatSamples[i] = sample / 8388608.0f;
        }

        return floatSamples;
    }



    public static float[] pcm24ToFloat(byte[] pcmBytes) {
        if (pcmBytes == null || pcmBytes.length % 3 != 0) {
            throw new IllegalArgumentException("PCM byte array must be non-null and length multiple of 4.");
        }

        int sampleCount = pcmBytes.length / 3;
        float[] floatSamples = new float[sampleCount];

        // Wrap bytes in a ByteBuffer for endian-safe reading
        ByteBuffer buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < sampleCount; i++) {
            int sample = buffer.getInt(); // signed 32-bit PCM
            // Normalize: divide by max positive value for signed 32-bit
            floatSamples[i] = sample / 2147483648.0f; // 2^31
        }

        return floatSamples;
    }


    public static float[] pcm16ToFloat(byte[] pcmBytes) {
        if (pcmBytes == null || pcmBytes.length % 2 != 0) {
            throw new IllegalArgumentException("PCM byte array must be non-null and length multiple of 2.");
        }

        int sampleCount = pcmBytes.length / 2;
        float[] floatSamples = new float[sampleCount];

        // Wrap bytes in a ByteBuffer for endian-safe reading
        ByteBuffer buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < sampleCount; i++) {
            short sample = buffer.getShort();
            // Normalize: divide by max positive value for signed 16-bit
            floatSamples[i] = sample / 32768.0f;
        }

        return floatSamples;
    }


    public static byte[] packFloatTo24Bit(float[] samples) {
        if (samples == null) {
            throw new IllegalArgumentException("Samples array cannot be null.");
        }

        byte[] packedData = new byte[samples.length * 3];

        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i];

            // Clamp to [-1.0, 1.0]
            if (sample > 1.0f) sample = 1.0f;
            if (sample < -1.0f) sample = -1.0f;

            // Convert back to 24-bit range
            int intSample = (int) (sample * 8388607.0f);

            // Little-endian: LSB first
            packedData[i * 3]     = (byte) (intSample & 0xFF);         // LSB
            packedData[i * 3 + 1] = (byte) ((intSample >> 8) & 0xFF);  // Middle byte
            packedData[i * 3 + 2] = (byte) ((intSample >> 16) & 0xFF); // MSB (sign bit included)
        }
        return packedData;
    }

}
