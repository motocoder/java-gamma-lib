package llc.berserkr.gammalib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import llc.berserkr.gammalib.android.AudioBufferLoader;
import llc.berserkr.gammalib.android.SoundEncodingUtil;
import llc.berserkr.gammalib.android.SoundRecorder;
import llc.berserkr.gammalib.jni.Gamma;
import llc.berserkr.gammalib.util.StreamUtil;

public class GammaTest {

    private static final Logger logger = LoggerFactory.getLogger(GammaTest.class);

    @Rule
    public GrantPermissionRule permissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    @Test
    public void gammaInitTest() {

        final Gamma gamma = new Gamma();
        gamma.initialize();
    }

    @Test
    public void gammaProcessTest() throws IOException {

        final Gamma gamma = new Gamma();
        gamma.initialize();

        final AudioBufferLoader bufferLoader =
            new AudioBufferLoader(
                MediaRecorder.AudioSource.MIC,
                96_000, // Sample rate
                AudioFormat.CHANNEL_IN_MONO, // Channel configuration
                AudioFormat.ENCODING_PCM_24BIT_PACKED
            );

        final SoundRecorder soundRecorder =
            new SoundRecorder(
                bufferLoader,
                bufferLoader.getMinBufferSize()
            );

        //kill recorder after 5 seconds.
        scheduledExecutorService.schedule(() -> soundRecorder.stopRecording(), 5000, TimeUnit.MILLISECONDS);

        final byte [] recorded;

        try(final InputStream is = soundRecorder.startRecording()) {

            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            StreamUtil.copyTo(is, output);

            recorded = output.toByteArray();

            assertNotNull(recorded);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final float[] normalized = SoundEncodingUtil.unpack24BitToFloat(recorded);

        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;

        for(final float normalizedSample : normalized) {

            if(normalizedSample < min) {
                min = normalizedSample;
            }

            if(normalizedSample > max) {
                max = normalizedSample;
            }

        }

        logger.debug("normalized max " + max);
        logger.debug("normalized min " + min);

        final float processed = gamma.maxVolumeNormalize(normalized);
        final float processed2 = gamma.maxVolumePCM24Bytes(recorded);

        logger.debug("peak dB was " + String.format(Locale.US, "%.2f", processed));

        logger.debug("2peak dB was " + String.format(Locale.US, "%.2f", processed2));

    }

    @Test
    public void gammaDetectSilence() throws IOException {

        final Gamma gamma = new Gamma();
        gamma.initialize();

        final AudioBufferLoader bufferLoader =
            new AudioBufferLoader(
                MediaRecorder.AudioSource.MIC,
                96_000, // Sample rate
                AudioFormat.CHANNEL_IN_MONO, // Channel configuration
                AudioFormat.ENCODING_PCM_24BIT_PACKED
            );

        final SoundRecorder soundRecorder =
            new SoundRecorder(
                bufferLoader,
                bufferLoader.getMinBufferSize()
            );

        //kill recorder after 5 seconds.
        scheduledExecutorService.schedule(() -> soundRecorder.stopRecording(), 5000, TimeUnit.MILLISECONDS);

        final byte [] recorded;

        try(final InputStream is = soundRecorder.startRecording()) {

            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            StreamUtil.copyTo(is, output);

            recorded = output.toByteArray();

            assertNotNull(recorded);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final float[] normalized = SoundEncodingUtil.unpack24BitToFloat(recorded);

        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;

        for(final float normalizedSample : normalized) {

            if(normalizedSample < min) {
                min = normalizedSample;
            }

            if(normalizedSample > max) {
                max = normalizedSample;
            }

        }

        logger.debug("total samples " + normalized.length);
        logger.debug("normalized max " + max);
        logger.debug("normalized min " + min);

        assertTrue(gamma.detectSilence(normalized, 0.1F, 100000));

    }

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Test
    public void gammaDetectNoise() throws IOException {

        final Gamma gamma = new Gamma();
        gamma.initialize();

        final AudioBufferLoader bufferLoader =
            new AudioBufferLoader(
                MediaRecorder.AudioSource.MIC,
                96_000, // Sample rate
                AudioFormat.CHANNEL_IN_MONO, // Channel configuration
                AudioFormat.ENCODING_PCM_24BIT_PACKED
            );

        final SoundRecorder soundRecorder =
            new SoundRecorder(
                bufferLoader,
                bufferLoader.getMinBufferSize()
            );

        final byte [] recorded;

        //kill recorder after 5 seconds.
        scheduledExecutorService.schedule(() -> soundRecorder.stopRecording(), 5000, TimeUnit.MILLISECONDS);

        try(final InputStream is = soundRecorder.startRecording()) {

            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            StreamUtil.copyTo(is, output);

            recorded = output.toByteArray();

            assertNotNull(recorded);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final float[] normalized = SoundEncodingUtil.unpack24BitToFloat(recorded);

        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;

        for(final float normalizedSample : normalized) {

            if(normalizedSample < min) {
                min = normalizedSample;
            }

            if(normalizedSample > max) {
                max = normalizedSample;
            }

        }

        logger.debug("total samples " + normalized.length);
        logger.debug("normalized max " + max);
        logger.debug("normalized min " + min);

        assertTrue(gamma.detectNoise(normalized, 0.2F));

    }

    @Test
    public void gammaLowPassFilter() throws IOException, InterruptedException {

        final Gamma gamma = new Gamma();
        gamma.initialize();

        final AudioBufferLoader bufferLoader =
            new AudioBufferLoader(
                MediaRecorder.AudioSource.MIC,
                96_000, // Sample rate
                AudioFormat.CHANNEL_IN_MONO, // Channel configuration
                AudioFormat.ENCODING_PCM_24BIT_PACKED
            );

        final SoundRecorder soundRecorder =
            new SoundRecorder(
                bufferLoader,
                bufferLoader.getMinBufferSize()
            );

        final byte [] recorded;

        try(final InputStream is = soundRecorder.startRecording()) {

            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            StreamUtil.copyTo(is, output);

            recorded = output.toByteArray();

            assertNotNull(recorded);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final float[] normalized = SoundEncodingUtil.unpack24BitToFloat(recorded);

        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;

        for(final float normalizedSample : normalized) {

            if(normalizedSample < min) {
                min = normalizedSample;
            }

            if(normalizedSample > max) {
                max = normalizedSample;
            }

        }

        logger.debug("total samples " + normalized.length);
        logger.debug("normalized max " + max);
        logger.debug("normalized min " + min);

        final float [] result = gamma.lowPassFilter(normalized, 1000.0F, 96_000.0F);

        final byte[] readBytes = SoundEncodingUtil.packFloatTo24Bit(result);

        // Configure AudioTrack for the same format as your recording
        final AudioTrack audioTrack = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_24BIT_PACKED)
                .setSampleRate(96_000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO) // Note: OUT_MONO for playback
                .build())
            .setBufferSizeInBytes(readBytes.length)
            .setTransferMode(AudioTrack.MODE_STATIC) // Static is good for short buffers
            .build();

        // Load the data and play
        audioTrack.write(readBytes, 0, readBytes.length);
        audioTrack.play();

        // Wait for it to finish
        Thread.sleep(5000);
        audioTrack.stop();
        audioTrack.release();

    }

}
