package llc.berserkr.gammalib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import llc.berserkr.gammalib.android.AudioBufferLoader;
import llc.berserkr.gammalib.util.MP3ToPCMConverter;
import llc.berserkr.gammalib.util.SoundEncodingUtil;
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

//    @Test //TODO this doesn't work (Ai generated stuff)
//    public void createPCMTest() throws IOException, InterruptedException {
//
//        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
//
//        // 2. Setup paths for conversion in the app's cache directory
//        File cacheDir = context.getCacheDir();
//        File mp3File = new File(cacheDir, "temp.mp3");
//        File pcmFile = new File(cacheDir, "retro_westerwald.pcm");
//        File convertedMp3File = new File(cacheDir, "retro_westerwald_converted.mp3");
//
//        // Copy asset to a file so MediaExtractor can use a path (some APIs require it)
//        try (InputStream is = context.getAssets().open("retro_westerwald.mp3");
//             FileOutputStream os = new FileOutputStream(mp3File)) {
//            StreamUtil.copyTo(is, os);
//        }
//
//        // 3. Perform conversions
//        MP3ToPCMConverter.convertTo24PCM(mp3File.getPath(), pcmFile.getPath());
//        MP3ToPCMConverter.convert24PCMToMP3(pcmFile.getPath(), convertedMp3File.getPath(), 96_000, 1);
//
//        logger.info("PCM conversion complete at: " + pcmFile.getAbsolutePath());
//        logger.info("Converted MP3 complete at: " + convertedMp3File.getAbsolutePath());
//
//        {
//            // 1. Play original MP3 from assets using MediaPlayer
//            final MediaPlayer mediaPlayer = new MediaPlayer();
//            try (AssetFileDescriptor afd = context.getAssets().openFd("retro_westerwald.mp3")) {
//                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
//                mediaPlayer.prepare();
//                mediaPlayer.start();
//                logger.info("Playing original MP3...");
//                Thread.sleep(3000); // Play for 3 seconds
//                mediaPlayer.stop();
//                mediaPlayer.release();
//            }
//        }
//
//        {
//            // 1. Play converted MP3 from file using MediaPlayer
//            final MediaPlayer mediaPlayer = new MediaPlayer();
//            mediaPlayer.setDataSource(convertedMp3File.getPath());
//            mediaPlayer.prepare();
//            mediaPlayer.start();
//            logger.info("Playing original MP3...");
//            Thread.sleep(3000); // Play for 3 seconds
//            mediaPlayer.stop();
//            mediaPlayer.release();
//
//        }
//    }

    private static final String PCM_FILE_NAME = "retro_westerwald_filtered.pcm";

    // PCM format parameters (must match your file)
    private static final int SAMPLE_RATE = 96_000; // Hz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO; // or CHANNEL_OUT_STEREO
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    @Test
    public void test() throws IOException, InterruptedException {

        final Context context = InstrumentationRegistry.getInstrumentation().getContext();

        // 2. Setup paths for conversion in the app's cache directory
        final File cacheDir = context.getCacheDir();

        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        // Copy asset to a file so MediaExtractor can use a path (some APIs require it)
        try (InputStream is = context.getAssets().open("retro_westerwald_filtered.pcm")) {
            StreamUtil.copyTo(is, os);
        }

        final byte [] read = os.toByteArray();

        final float[] normalized = SoundEncodingUtil.pcm16ToFloat(read);

        final Gamma gamma = new Gamma();
        gamma.initialize();
        final float [] filtered = gamma.highPassFilter(normalized, 1000.0F, 96_000.0F);

        final byte [] filteredPCM = SoundEncodingUtil.packFloatTo16Bit(filtered);

        Thread playThread = new Thread(() -> {
            AssetManager assetManager = context.getAssets();
            try (InputStream inputStream = new ByteArrayInputStream(filteredPCM)) {

                int minBufferSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT
                );

                AudioTrack audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AUDIO_FORMAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(CHANNEL_CONFIG)
                                .build())
                        .setBufferSizeInBytes(minBufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                byte[] buffer = new byte[minBufferSize];
                int bytesRead;

                audioTrack.play();

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    audioTrack.write(buffer, 0, bytesRead);
                }

                audioTrack.stop();
                audioTrack.release();

            } catch (IOException e) {
                logger.error("Error playing PCM file", e);
            }
        });

        playThread.start();

        Thread.sleep(30_000);

    }

    // --- Test helpers ---

    private static final float TEST_SAMPLE_RATE = 96_000.0f;
    private static final float TEST_DURATION = 0.1f; // 100ms — enough for filter to settle

    /**
     * Generates a normalized sine wave.
     * @param frequency  frequency in Hz
     * @param sampleRate sample rate in Hz
     * @param duration   duration in seconds
     * @return normalized float array of samples
     */
    private float[] generateSineWave(float frequency, float sampleRate, float duration) {
        int sampleCount = (int)(sampleRate * duration);
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = (float) Math.sin(2.0 * Math.PI * frequency * i / sampleRate);
        }
        return samples;
    }

    /**
     * Mixes two or more signals by adding them element-wise.
     * All input arrays must be the same length.
     * @param signals two or more float arrays of equal length
     * @return float array containing the summed signal
     */
    private float[] mixSignals(float[]... signals) {
        int length = signals[0].length;
        float[] mixed = new float[length];
        for (float[] signal : signals) {
            for (int i = 0; i < length; i++) {
                mixed[i] += signal[i];
            }
        }
        return mixed;
    }

    /**
     * Computes RMS (root-mean-square) energy of a signal.
     */
    private float computeRMS(float[] samples) {
        float sumSquares = 0;
        for (float sample : samples) {
            sumSquares += sample * sample;
        }
        return (float) Math.sqrt(sumSquares / samples.length);
    }

    /**
     * Computes the arithmetic mean of a signal.
     */
    private float computeMean(float[] samples) {
        float sum = 0;
        for (float sample : samples) {
            sum += sample;
        }
        return sum / samples.length;
    }

    /**
     * Saves a float[] signal to a binary file for offline analysis via MediaStore.
     * Format: raw 32-bit IEEE 754 floats, little-endian, no header.
     *
     * Files are written to the device's Downloads/test-signals/ directory using the
     * MediaStore API, which works on all API levels without special permissions.
     * Files persist after the test APK is uninstalled.
     *
     * Pull with:
     *   adb pull /storage/emulated/0/Download/test-signals/ ./test-signals/
     *
     * If file saving fails, the test continues — signal export is best-effort
     * and does not affect test assertions.
     *
     * @param samples  the signal data
     * @param filename the output filename (e.g., "bandPassFilter_input.raw")
     */
    private void saveSignal(float[] samples, String filename) {
        try {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

            ByteBuffer buffer = ByteBuffer.allocate(samples.length * 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (float sample : samples) {
                buffer.putFloat(sample);
            }
            byte[] data = buffer.array();

            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
            contentValues.put(MediaStore.Downloads.RELATIVE_PATH, "Download/test-signals");

            // Delete any existing file with the same name to avoid duplicates
            context.getContentResolver().delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.DISPLAY_NAME + "=? AND " +
                    MediaStore.Downloads.RELATIVE_PATH + "=?",
                new String[]{filename, "Download/test-signals/"}
            );

            Uri uri = context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
            );

            if (uri != null) {
                try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        outputStream.write(data);
                    }
                }
                logger.debug("Saved signal via MediaStore: " + filename);
            }
        } catch (Exception e) {
            logger.warn("Failed to save signal file " + filename + ": " + e.getMessage());
        }
    }

    // --- Group 1A: Biquad filter tests without level ---

    @Test
    public void bandPassFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] mid = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(5000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, mid, high);

        // Compute input metrics BEFORE JNI call (JNI may modify the input array in-place)
        float inputRMS = computeRMS(input);
        saveSignal(input, "bandPassFilter_input.raw");

        float[] output = gamma.bandPassFilter(input, 1000f, 2.0f, TEST_SAMPLE_RATE);

        saveSignal(output, "bandPassFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Band-pass should reduce overall energy", outputRMS < inputRMS);
        assertTrue("Band-pass should preserve passband energy", outputRMS > 0.0f);
    }

    @Test
    public void resonantFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] mid = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(5000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, mid, high);

        saveSignal(input, "resonantFilter_input.raw");

        float[] output = gamma.resonantFilter(input, 1000f, 5.0f, TEST_SAMPLE_RATE);

        saveSignal(output, "resonantFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Resonant filter should produce non-zero output", outputRMS > 0.0f);
    }

    @Test
    public void bandRejectFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] mid = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(5000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, mid, high);

        float inputRMS = computeRMS(input);
        saveSignal(input, "bandRejectFilter_input.raw");

        float[] output = gamma.bandRejectFilter(input, 1000f, 2.0f, TEST_SAMPLE_RATE);

        saveSignal(output, "bandRejectFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Band-reject should reduce energy", outputRMS < inputRMS);
        assertTrue("Band-reject should preserve non-rejected frequencies", outputRMS > 0.0f);
    }

    @Test
    public void allPassBiquadFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] input = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);

        float inputRMS = computeRMS(input);
        saveSignal(input, "allPassBiquadFilter_input.raw");

        float[] output = gamma.allPassBiquadFilter(input, 1000f, 0.707f, TEST_SAMPLE_RATE);

        saveSignal(output, "allPassBiquadFilter_output.raw");
        float outputRMS = computeRMS(output);

        float tolerance = inputRMS * 0.05f;
        assertTrue("All-pass should preserve amplitude (within 5%)",
            Math.abs(outputRMS - inputRMS) < tolerance);
    }

    // --- Group 1B: Biquad filter tests with level ---

    @Test
    public void peakingFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] mid = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, mid);

        float inputRMS = computeRMS(input);
        saveSignal(input, "peakingFilter_input.raw");

        // level=2.0 boosts the 1000Hz band
        float[] output = gamma.peakingFilter(input, 1000f, 1.0f, 2.0f, TEST_SAMPLE_RATE);

        saveSignal(output, "peakingFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Peaking filter with boost should increase RMS", outputRMS > inputRMS);
    }

    @Test
    public void lowShelfFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(5000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, high);

        float inputRMS = computeRMS(input);
        saveSignal(input, "lowShelfFilter_input.raw");

        // level=0.25 cuts frequencies below 1000Hz
        float[] output = gamma.lowShelfFilter(input, 1000f, 0.707f, 0.25f, TEST_SAMPLE_RATE);

        saveSignal(output, "lowShelfFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Low-shelf cut should reduce energy", outputRMS < inputRMS);
    }

    @Test
    public void highShelfFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(5000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, high);

        float inputRMS = computeRMS(input);
        saveSignal(input, "highShelfFilter_input.raw");

        // level=0.25 cuts frequencies above 1000Hz
        float[] output = gamma.highShelfFilter(input, 1000f, 0.707f, 0.25f, TEST_SAMPLE_RATE);

        saveSignal(output, "highShelfFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("High-shelf cut should reduce energy", outputRMS < inputRMS);
    }

    // --- Group 2: Standalone filter tests ---

    @Test
    public void onePoleLowPassFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(10000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, high);

        float inputRMS = computeRMS(input);
        saveSignal(input, "onePoleLowPassFilter_input.raw");

        float[] output = gamma.onePoleLowPassFilter(input, 500f, TEST_SAMPLE_RATE);

        saveSignal(output, "onePoleLowPassFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("OnePole low-pass should reduce energy", outputRMS < inputRMS);
        assertTrue("OnePole low-pass should preserve low frequencies", outputRMS > 0.0f);
    }

    @Test
    public void onePoleHighPassFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(10000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, high);

        float inputRMS = computeRMS(input);
        saveSignal(input, "onePoleHighPassFilter_input.raw");

        float[] output = gamma.onePoleHighPassFilter(input, 5000f, TEST_SAMPLE_RATE);

        saveSignal(output, "onePoleHighPassFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("OnePole high-pass should reduce energy", outputRMS < inputRMS);
        assertTrue("OnePole high-pass should preserve high frequencies", outputRMS > 0.0f);
    }

    @Test
    public void allPass1FilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] input = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);

        float inputRMS = computeRMS(input);
        saveSignal(input, "allPass1Filter_input.raw");

        float[] output = gamma.allPass1Filter(input, 1000f, TEST_SAMPLE_RATE);

        saveSignal(output, "allPass1Filter_output.raw");
        float outputRMS = computeRMS(output);

        float tolerance = inputRMS * 0.05f;
        assertTrue("AllPass1 should preserve amplitude (within 5%)",
            Math.abs(outputRMS - inputRMS) < tolerance);
    }

    @Test
    public void allPass2FilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] input = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);

        float inputRMS = computeRMS(input);
        saveSignal(input, "allPass2Filter_input.raw");

        float[] output = gamma.allPass2Filter(input, 1000f, 100f, TEST_SAMPLE_RATE);

        saveSignal(output, "allPass2Filter_output.raw");
        float outputRMS = computeRMS(output);

        float tolerance = inputRMS * 0.05f;
        assertTrue("AllPass2 should preserve amplitude (within 5%)",
            Math.abs(outputRMS - inputRMS) < tolerance);
    }

    @Test
    public void blockDCFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        // Sine wave with a DC offset of 0.5
        float[] sine = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = new float[sine.length];
        for (int i = 0; i < sine.length; i++) {
            input[i] = sine[i] + 0.5f;
        }

        float inputMean = computeMean(input);
        saveSignal(input, "blockDCFilter_input.raw");

        float[] output = gamma.blockDCFilter(input, 35f, TEST_SAMPLE_RATE);

        saveSignal(output, "blockDCFilter_output.raw");
        float outputMean = computeMean(output);

        assertTrue("Input should have DC offset", Math.abs(inputMean) > 0.3f);
        assertTrue("BlockDC should remove DC offset", Math.abs(outputMean) < 0.1f);
    }

    @Test
    public void blockNyquistFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        // Pure Nyquist signal (alternating +1/-1 samples) — dominant component at Nyquist
        int sampleCount = (int)(TEST_SAMPLE_RATE * TEST_DURATION);
        float[] input = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            input[i] = (i % 2 == 0) ? 1.0f : -1.0f;
        }

        float inputRMS = computeRMS(input);
        saveSignal(input, "blockNyquistFilter_input.raw");

        float[] output = gamma.blockNyquistFilter(input, 35f, TEST_SAMPLE_RATE);

        saveSignal(output, "blockNyquistFilter_output.raw");
        float outputRMS = computeRMS(output);

        // BlockNyquist should significantly attenuate the pure Nyquist signal
        assertTrue("BlockNyquist should reduce Nyquist energy", outputRMS < inputRMS);
    }

    @Test
    public void notchFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(500f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] mid = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(2000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, mid, high);

        float inputRMS = computeRMS(input);
        saveSignal(input, "notchFilter_input.raw");

        float[] output = gamma.notchFilter(input, 1000f, 100f, TEST_SAMPLE_RATE);

        saveSignal(output, "notchFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Notch should reduce energy", outputRMS < inputRMS);
        assertTrue("Notch should preserve non-notched frequencies", outputRMS > 0.0f);
    }

    @Test
    public void resonatorFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        // Mix of many frequencies to give the resonator something to amplify
        float[] f1 = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] f2 = generateSineWave(500f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] f3 = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] f4 = generateSineWave(2000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] f5 = generateSineWave(5000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(f1, f2, f3, f4, f5);

        saveSignal(input, "resonatorFilter_input.raw");

        float[] output = gamma.resonatorFilter(input, 1000f, 50f, TEST_SAMPLE_RATE);

        saveSignal(output, "resonatorFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Resonator should produce non-zero output", outputRMS > 0.0f);
    }

    @Test
    public void movingAverageFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] low = generateSineWave(100f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] high = generateSineWave(10000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(low, high);

        float inputRMS = computeRMS(input);
        saveSignal(input, "movingAverageFilter_input.raw");

        float[] output = gamma.movingAverageFilter(input, 10);

        saveSignal(output, "movingAverageFilter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Moving average should reduce energy", outputRMS < inputRMS);
    }

    @Test
    public void differencerFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        // Constant DC signal — difference should be zero after first sample
        int sampleCount = (int)(TEST_SAMPLE_RATE * TEST_DURATION);
        float[] input = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            input[i] = 0.5f;
        }

        saveSignal(input, "differencerFilter_input.raw");

        float[] output = gamma.differencerFilter(input);

        saveSignal(output, "differencerFilter_output.raw");

        // All samples after the first should be approximately zero
        for (int i = 1; i < output.length; i++) {
            assertTrue("Differencer of constant signal should be zero at sample " + i,
                Math.abs(output[i]) < 0.001f);
        }

        // Secondary check: sine wave input should produce non-zero output
        float[] sineInput = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] sineOutput = gamma.differencerFilter(sineInput);
        float sineOutputRMS = computeRMS(sineOutput);
        assertTrue("Differencer of sine wave should produce non-zero output", sineOutputRMS > 0.0f);
    }

    @Test
    public void integratorFilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        // Constant small value for short duration
        int sampleCount = 1000;
        float[] input = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            input[i] = 0.001f;
        }

        saveSignal(input, "integratorFilter_input.raw");

        float[] output = gamma.integratorFilter(input, 0.999f);

        saveSignal(output, "integratorFilter_output.raw");

        // Output should ramp up — last sample greater than first
        assertTrue("Integrator output should ramp up",
            output[output.length - 1] > output[0]);

        // Leak prevents unbounded growth — output should stay bounded
        for (float sample : output) {
            assertTrue("Integrator output should be bounded", sample < 2.0f);
        }
    }

    // --- Group 3: Effect tests ---

    @Test
    public void chorusEffectTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] input = generateSineWave(440f, TEST_SAMPLE_RATE, 1.0f);

        saveSignal(input, "chorusEffect_input.raw");

        float[] output = gamma.chorusEffect(input, 0.0021f, 0.002f, 1.0f, 0.9f, 0.1f, TEST_SAMPLE_RATE);

        saveSignal(output, "chorusEffect_output.raw");

        float outputRMS = computeRMS(output);

        // Chorus should produce output with similar energy
        assertTrue("Chorus output should have energy", outputRMS > 0.0f);

        // Output should not be identical to input — compare output against a fresh copy
        float[] reference = generateSineWave(440f, TEST_SAMPLE_RATE, 1.0f);
        boolean identical = true;
        for (int i = 0; i < Math.min(reference.length, output.length); i++) {
            if (Math.abs(reference[i] - output[i]) > 0.0001f) {
                identical = false;
                break;
            }
        }
        assertTrue("Chorus output should differ from input", !identical);
    }

    @Test
    public void quantizerEffectTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] input = generateSineWave(440f, TEST_SAMPLE_RATE, 1.0f);

        // Count unique values from a fresh reference (JNI may modify input)
        float[] reference = generateSineWave(440f, TEST_SAMPLE_RATE, 1.0f);
        Set<Float> inputValues = new HashSet<>();
        for (float sample : reference) {
            inputValues.add(sample);
        }

        saveSignal(input, "quantizerEffect_input.raw");

        float[] output = gamma.quantizerEffect(input, 8000f, 0.1f, TEST_SAMPLE_RATE);

        saveSignal(output, "quantizerEffect_output.raw");

        Set<Float> outputValues = new HashSet<>();
        for (float sample : output) {
            outputValues.add(sample);
        }

        assertTrue("Quantizer should reduce number of unique values",
            outputValues.size() < inputValues.size());
        assertTrue("Quantizer output should be non-zero", computeRMS(output) > 0.0f);
    }

    @Test
    public void biquad3FilterTest() throws IOException {
        final Gamma gamma = new Gamma();
        gamma.initialize();

        float[] f200 = generateSineWave(200f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] f1000 = generateSineWave(1000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] f3000 = generateSineWave(3000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] f8000 = generateSineWave(8000f, TEST_SAMPLE_RATE, TEST_DURATION);
        float[] input = mixSignals(f200, f1000, f3000, f8000);

        float inputRMS = computeRMS(input);
        saveSignal(input, "biquad3Filter_input.raw");

        float[] output = gamma.biquad3Filter(input, 1000f, 3000f, 5000f, 4.0f, TEST_SAMPLE_RATE);

        saveSignal(output, "biquad3Filter_output.raw");
        float outputRMS = computeRMS(output);

        assertTrue("Biquad3 should reduce energy from non-passband frequencies", outputRMS < inputRMS);
        assertTrue("Biquad3 should preserve passband energy", outputRMS > 0.0f);
    }

}
