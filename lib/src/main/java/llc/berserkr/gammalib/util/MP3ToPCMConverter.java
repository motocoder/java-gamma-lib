package llc.berserkr.gammalib.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MP3ToPCMConverter {

    private static final Logger logger = LoggerFactory.getLogger(MP3ToPCMConverter.class);

    private static final String TAG = "Mp3ToPcmConverter";

    /**
     * Converts an MP3 file to PCM 16-bit.
     *
     * @param mp3Path Path to the input MP3 file.
     * @param pcmPath Path to the output PCM file.
     * @throws IOException if file operations fail.
     */
    public static void convertTo16PCM(String mp3Path, String pcmPath) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(mp3Path);

        // Find the first audio track
        int audioTrackIndex = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                format = f;
                break;
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            throw new IOException("No audio track found in file.");
        }

        extractor.selectTrack(audioTrackIndex);

        String mime = format.getString(MediaFormat.KEY_MIME);
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        FileOutputStream pcmOutput = new FileOutputStream(new File(pcmPath));

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (!sawOutputEOS) {
            // Feed input
            if (!sawInputEOS) {
                int inputBufferId = codec.dequeueInputBuffer(10000);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = getInputBuffer(codec, inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);

                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferId, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(inputBufferId, 0, sampleSize,
                                presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }

            // Get decoded PCM output
            int outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputBufferId >= 0) {
                ByteBuffer outputBuffer = getOutputBuffer(codec, outputBufferId);

                byte[] chunk = new byte[bufferInfo.size];
                outputBuffer.get(chunk);
                outputBuffer.clear();

                if (chunk.length > 0) {
                    pcmOutput.write(chunk);
                }

                codec.releaseOutputBuffer(outputBufferId, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            }
        }

        pcmOutput.close();
        codec.stop();
        codec.release();
        extractor.release();

        logger.info("Conversion complete: " + pcmPath);
    }

    /**
     * Converts an MP3 file to PCM 24-bit.
     *
     * @param mp3Path Path to the input MP3 file.
     * @param pcmPath Path to the output PCM file.
     * @throws IOException if file operations fail.
     */
    public static void convertTo24PCM(String mp3Path, String pcmPath) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(mp3Path);

        int audioTrackIndex = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            if (f.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                audioTrackIndex = i;
                format = f;
                break;
            }
        }

        if (audioTrackIndex == -1) throw new IOException("No audio track found.");
        extractor.selectTrack(audioTrackIndex);

        MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();

        try (FileOutputStream pcmOutput = new FileOutputStream(pcmPath)) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false, sawOutputEOS = false;

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inputBufferId = codec.dequeueInputBuffer(10000);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            codec.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);

                    // Decode output is 16-bit PCM. Convert to float then pack to 24-bit.
                    short[] s16 = new short[bufferInfo.size / 2];
                    outputBuffer.asShortBuffer().get(s16);

                    float[] f32 = new float[s16.length];
                    for (int i = 0; i < s16.length; i++) f32[i] = s16[i] / 32768.0f;

                    byte[] pcm24 = SoundEncodingUtil.packFloatTo24Bit(f32);
                    pcmOutput.write(pcm24);

                    codec.releaseOutputBuffer(outputBufferId, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true;
                }
            }
        } finally {
            codec.stop();
            codec.release();
            extractor.release();
        }
    }

    /**
     * Converts a 24-bit PCM file to MP3 using MediaCodec.
     * Note: MediaCodec encoders usually expect 16-bit PCM. We'll downscale to 16-bit for the encoder.
     */
    public static void convert24PCMToMP3(String pcmPath, String mp3Path, int sampleRate, int channelCount) throws IOException {
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_MPEG, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);

        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        try (FileInputStream pcmInput = new FileInputStream(pcmPath);
             FileOutputStream mp3Output = new FileOutputStream(mp3Path)) {

            byte[] buffer24 = new byte[8192 * 3]; // Multiple of 3 for 24-bit
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false, sawOutputEOS = false;

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inputBufferId = encoder.dequeueInputBuffer(10000);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferId);
                        int bytesRead = pcmInput.read(buffer24);
                        if (bytesRead == -1) {
                            encoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            // Downscale 24-bit PCM to 16-bit for the encoder
                            int sampleCount = bytesRead / 3;
                            inputBuffer.clear();
                            for (int i = 0; i < sampleCount; i++) {
                                int b0 = buffer24[i * 3] & 0xFF;
                                int b1 = buffer24[i * 3 + 1] & 0xFF;
                                int b2 = buffer24[i * 3 + 2];
                                int sample24 = (b2 << 16) | (b1 << 8) | b0;
                                // Scale down to 16-bit
                                short sample16 = (short) (sample24 >> 8);
                                inputBuffer.putShort(sample16);
                            }
                            encoder.queueInputBuffer(inputBufferId, 0, sampleCount * 2, System.nanoTime() / 1000, 0);
                        }
                    }
                }

                int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                    byte[] outData = new byte[bufferInfo.size];
                    outputBuffer.get(outData);
                    mp3Output.write(outData);
                    encoder.releaseOutputBuffer(outputBufferId, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true;
                }
            }
        } finally {
            encoder.stop();
            encoder.release();
        }
    }


    // Helper for backward compatibility
    private static ByteBuffer getInputBuffer(MediaCodec codec, int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getInputBuffer(index);
        } else {
            return codec.getInputBuffers()[index];
        }
    }

    private static ByteBuffer getOutputBuffer(MediaCodec codec, int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getOutputBuffer(index);
        } else {
            return codec.getOutputBuffers()[index];
        }
    }
}
