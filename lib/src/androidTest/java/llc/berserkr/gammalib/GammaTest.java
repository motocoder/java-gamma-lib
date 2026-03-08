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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
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

}
