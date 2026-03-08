package llc.berserkr.gammalib.util;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

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
    public static void convert(String mp3Path, String pcmPath) throws IOException {
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
