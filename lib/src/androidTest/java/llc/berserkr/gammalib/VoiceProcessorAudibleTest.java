package llc.berserkr.gammalib;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import llc.berserkr.gammalib.util.SoundEncodingUtil;
import llc.berserkr.gammalib.voice.VoiceProcessor;

/**
 * Audible voice processor tests with human pass/fail verification.
 * <p>
 * Each test:
 * 1. Synthesizes speech describing the test via TTS
 * 2. Applies the filter being tested to the TTS audio
 * 3. Plays the filtered audio through the device speaker
 * 4. Plays a beep to signal "ready for response"
 * 5. Records your spoken response ("pass" or "fail")
 * 6. Uses Vosk STT to convert response to text
 * 7. Asserts based on whether you said "pass" or "fail"
 * <p>
 * Run individual tests via Android Studio or:
 *   adb shell am instrument -w -e class llc.berserkr.gammalib.VoiceProcessorAudibleTest#highPass_min ...
 */
public class VoiceProcessorAudibleTest {

    private static final Logger logger = LoggerFactory.getLogger(VoiceProcessorAudibleTest.class);

    private static final String VOSK_MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
    private static final String VOSK_MODEL_DIR = "vosk-model-small-en-us-0.15";

    private static final int TTS_SAMPLE_RATE = 22050;
    private static final int RECORD_SAMPLE_RATE = 16000;
    private static final float BEEP_FREQUENCY = 1000f;
    private static final int BEEP_DURATION_MS = 300;
    private static final int POST_BEEP_DELAY_MS = 400;
    private static final int MAX_RECORD_DURATION_MS = 5000;
    private static final int SILENCE_THRESHOLD_SAMPLES = 8000; // 500ms at 16kHz
    private static final float SILENCE_AMPLITUDE = 0.02f;

    private static Context context;
    private static TextToSpeech tts;
    private static Model voskModel;
    private static boolean initialized = false;

    @Rule
    public GrantPermissionRule permissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    @BeforeClass
    public static void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Initialize TTS
        initTTS();

        // Download and load Vosk model
        initVosk();

        initialized = true;
    }

    @AfterClass
    public static void teardown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (voskModel != null) {
            voskModel.close();
            voskModel = null;
        }
    }

    // =========================================================================
    // HIGH PASS FILTER
    // =========================================================================

    @Test(timeout = 60000)
    public void highPass_min() throws Exception {
        runFilterTest(
            "Testing high pass filter at minimum. Cutoff 100 hertz.",
            (vp, samples) -> vp.highPass(samples, 100f)
        );
    }

    @Test(timeout = 60000)
    public void highPass_max() throws Exception {
        runFilterTest(
            "Testing high pass filter at maximum. Cutoff 2000 hertz.",
            (vp, samples) -> vp.highPass(samples, 2000f)
        );
    }

    // =========================================================================
    // LOW PASS FILTER
    // =========================================================================

    @Test(timeout = 60000)
    public void lowPass_min() throws Exception {
        runFilterTest(
            "Testing low pass filter at minimum. Cutoff 500 hertz.",
            (vp, samples) -> vp.lowPass(samples, 500f)
        );
    }

    @Test(timeout = 60000)
    public void lowPass_max() throws Exception {
        runFilterTest(
            "Testing low pass filter at maximum. Cutoff 4000 hertz.",
            (vp, samples) -> vp.lowPass(samples, 4000f)
        );
    }

    // =========================================================================
    // BAND PASS FILTER
    // =========================================================================

    @Test(timeout = 60000)
    public void bandPass_min() throws Exception {
        runFilterTest(
            "Testing band pass filter at minimum. Center 500 hertz.",
            (vp, samples) -> vp.bandPass(samples, 500f, 1.0f)
        );
    }

    @Test(timeout = 60000)
    public void bandPass_max() throws Exception {
        runFilterTest(
            "Testing band pass filter at maximum. Center 3000 hertz.",
            (vp, samples) -> vp.bandPass(samples, 3000f, 1.0f)
        );
    }

    // =========================================================================
    // BAND REJECT FILTER
    // =========================================================================

    @Test(timeout = 60000)
    public void bandReject_min() throws Exception {
        runFilterTest(
            "Testing band reject filter at minimum. Center 500 hertz.",
            (vp, samples) -> vp.bandReject(samples, 500f, 2.0f)
        );
    }

    @Test(timeout = 60000)
    public void bandReject_max() throws Exception {
        runFilterTest(
            "Testing band reject filter at maximum. Center 3000 hertz.",
            (vp, samples) -> vp.bandReject(samples, 3000f, 2.0f)
        );
    }

    // =========================================================================
    // VOICE BAND PASS (fixed params)
    // =========================================================================

    @Test(timeout = 60000)
    public void voiceBandPass() throws Exception {
        runFilterTest(
            "Testing voice band pass filter. 300 to 3400 hertz passband.",
            (vp, samples) -> vp.applyVoiceBandPass(samples)
        );
    }

    // =========================================================================
    // REMOVE RUMBLE
    // =========================================================================

    @Test(timeout = 60000)
    public void removeRumble_min() throws Exception {
        runFilterTest(
            "Testing remove rumble at minimum. Cutoff 80 hertz.",
            (vp, samples) -> vp.removeRumble(samples, 80f)
        );
    }

    @Test(timeout = 60000)
    public void removeRumble_max() throws Exception {
        runFilterTest(
            "Testing remove rumble at maximum. Cutoff 400 hertz.",
            (vp, samples) -> vp.removeRumble(samples, 400f)
        );
    }

    // =========================================================================
    // BLOCK DC
    // =========================================================================

    @Test(timeout = 60000)
    public void blockDC() throws Exception {
        runFilterTest(
            "Testing DC blocking filter.",
            (vp, samples) -> vp.blockDC(samples)
        );
    }

    // =========================================================================
    // REMOVE FREQUENCY (NOTCH)
    // =========================================================================

    @Test(timeout = 60000)
    public void removeFrequency_min() throws Exception {
        runFilterTest(
            "Testing notch filter at minimum. Removing 60 hertz hum.",
            (vp, samples) -> vp.removeFrequency(samples, 60f, 10f)
        );
    }

    @Test(timeout = 60000)
    public void removeFrequency_max() throws Exception {
        runFilterTest(
            "Testing notch filter at maximum. Removing 4000 hertz.",
            (vp, samples) -> vp.removeFrequency(samples, 4000f, 200f)
        );
    }

    // =========================================================================
    // SMOOTH (MOVING AVERAGE)
    // =========================================================================

    @Test(timeout = 60000)
    public void smooth_min() throws Exception {
        runFilterTest(
            "Testing smoothing at minimum. Window size 2.",
            (vp, samples) -> vp.smooth(samples, 2)
        );
    }

    @Test(timeout = 60000)
    public void smooth_max() throws Exception {
        runFilterTest(
            "Testing smoothing at maximum. Window size 10.",
            (vp, samples) -> vp.smooth(samples, 10)
        );
    }

    // =========================================================================
    // BOOST PRESENCE
    // =========================================================================

    @Test(timeout = 60000)
    public void boostPresence_min() throws Exception {
        runFilterTest(
            "Testing presence boost at minimum. Level 1.1.",
            (vp, samples) -> vp.boostPresence(samples, 1.1f)
        );
    }

    @Test(timeout = 60000)
    public void boostPresence_max() throws Exception {
        runFilterTest(
            "Testing presence boost at maximum. Level 2.0.",
            (vp, samples) -> vp.boostPresence(samples, 2.0f)
        );
    }

    // =========================================================================
    // REDUCE BOOMINESS
    // =========================================================================

    @Test(timeout = 60000)
    public void reduceBoominess_min() throws Exception {
        runFilterTest(
            "Testing boominess reduction at minimum. Level 0.8.",
            (vp, samples) -> vp.reduceBoominess(samples, 0.8f)
        );
    }

    @Test(timeout = 60000)
    public void reduceBoominess_max() throws Exception {
        runFilterTest(
            "Testing boominess reduction at maximum. Level 0.2.",
            (vp, samples) -> vp.reduceBoominess(samples, 0.2f)
        );
    }

    // =========================================================================
    // ADD AIR
    // =========================================================================

    @Test(timeout = 60000)
    public void addAir_min() throws Exception {
        runFilterTest(
            "Testing air boost at minimum. Level 1.1.",
            (vp, samples) -> vp.addAir(samples, 1.1f)
        );
    }

    @Test(timeout = 60000)
    public void addAir_max() throws Exception {
        runFilterTest(
            "Testing air boost at maximum. Level 2.0.",
            (vp, samples) -> vp.addAir(samples, 2.0f)
        );
    }

    // =========================================================================
    // PARAMETRIC EQ
    // =========================================================================

    @Test(timeout = 60000)
    public void eq_min() throws Exception {
        runFilterTest(
            "Testing parametric EQ at minimum. 500 hertz, boost 1.2.",
            (vp, samples) -> vp.eq(samples, 500f, 1.5f, 1.2f)
        );
    }

    @Test(timeout = 60000)
    public void eq_max() throws Exception {
        runFilterTest(
            "Testing parametric EQ at maximum. 3000 hertz, boost 2.5.",
            (vp, samples) -> vp.eq(samples, 3000f, 1.5f, 2.5f)
        );
    }

    // =========================================================================
    // RADIO EFFECT
    // =========================================================================

    @Test(timeout = 60000)
    public void radioEffect_min() throws Exception {
        runFilterTest(
            "Testing radio effect at minimum. Subtle lo-fi.",
            (vp, samples) -> vp.radioEffect(samples, 10000f, 0.01f)
        );
    }

    @Test(timeout = 60000)
    public void radioEffect_max() throws Exception {
        runFilterTest(
            "Testing radio effect at maximum. Heavy bit crush.",
            (vp, samples) -> vp.radioEffect(samples, 4000f, 0.05f)
        );
    }

    // =========================================================================
    // TINNY EFFECT
    // =========================================================================

    @Test(timeout = 60000)
    public void tinnyEffect_min() throws Exception {
        runFilterTest(
            "Testing tinny effect at minimum. 800 hertz, low resonance.",
            (vp, samples) -> vp.tinnyEffect(samples, 800f, 3.0f)
        );
    }

    @Test(timeout = 60000)
    public void tinnyEffect_max() throws Exception {
        runFilterTest(
            "Testing tinny effect at maximum. 2500 hertz, high resonance.",
            (vp, samples) -> vp.tinnyEffect(samples, 2500f, 10.0f)
        );
    }

    // =========================================================================
    // FORMANT FILTER
    // =========================================================================

    @Test(timeout = 60000)
    public void formantFilter_min() throws Exception {
        runFilterTest(
            "Testing formant filter at minimum. Standard voice formants.",
            (vp, samples) -> vp.formantFilter(samples, 500f, 1500f, 2500f, 3.0f)
        );
    }

    @Test(timeout = 60000)
    public void formantFilter_max() throws Exception {
        runFilterTest(
            "Testing formant filter at maximum. Exaggerated formants.",
            (vp, samples) -> vp.formantFilter(samples, 700f, 1200f, 3500f, 8.0f)
        );
    }

    // =========================================================================
    // CHORUS
    // =========================================================================

    @Test(timeout = 60000)
    public void chorus_min() throws Exception {
        runFilterTest(
            "Testing chorus at minimum. Subtle thickening.",
            (vp, samples) -> vp.chorus(samples, 0.005f, 0.001f, 0.3f, 0.5f, 0.2f)
        );
    }

    @Test(timeout = 60000)
    public void chorus_max() throws Exception {
        runFilterTest(
            "Testing chorus at maximum. Heavy modulation.",
            (vp, samples) -> vp.chorus(samples, 0.02f, 0.005f, 1.0f, 0.8f, 0.5f)
        );
    }

    // =========================================================================
    // CORE TEST RUNNER
    // =========================================================================

    /**
     * Runs a single audible filter test:
     * 1. Synthesizes the description as speech
     * 2. Applies the filter to the synthesized speech
     * 3. Plays the filtered audio
     * 4. Plays a beep
     * 5. Records user response
     * 6. Recognizes speech and asserts pass/fail
     */
    private void runFilterTest(String description, FilterFunction filter) throws Exception {
        assertTrue("Test infrastructure not initialized", initialized);

        logger.info("=== TEST: " + description + " ===");

        // Step 1: Synthesize description to PCM
        byte[] ttsPcm = synthesizeSpeech(description);
        assertNotNull("TTS synthesis failed", ttsPcm);
        logger.info("Synthesized " + ttsPcm.length + " bytes of speech");

        // Step 2: Apply filter
        VoiceProcessor vp = new VoiceProcessor(TTS_SAMPLE_RATE);
        float[] normalized = SoundEncodingUtil.pcm16ToFloat(ttsPcm);
        float[] filtered = filter.apply(vp, normalized);
        byte[] filteredPcm = SoundEncodingUtil.packFloatTo16Bit(filtered);

        // Step 3: Play filtered audio
        playPcm16(filteredPcm, TTS_SAMPLE_RATE);

        // Step 4: Short pause then beep
        Thread.sleep(200);
        playBeep();

        // Step 5: Delay after beep, then record response
        Thread.sleep(POST_BEEP_DELAY_MS);
        byte[] responsePcm = recordResponse();
        assertNotNull("Recording failed", responsePcm);
        logger.info("Recorded " + responsePcm.length + " bytes of response");

        // Step 6: Recognize and assert
        String recognized = recognizeSpeech(responsePcm);
        logger.info("Recognized: \"" + recognized + "\"");

        assertPassOrFail(recognized, description);
    }

    // =========================================================================
    // HELPER: TEXT-TO-SPEECH SYNTHESIS
    // =========================================================================

    /**
     * Synthesizes text to a WAV file, reads it back as PCM16 bytes.
     */
    private byte[] synthesizeSpeech(String text) throws Exception {
        File wavFile = new File(context.getCacheDir(), "tts_output.wav");
        if (wavFile.exists()) wavFile.delete();

        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                success[0] = true;
                latch.countDown();
            }
            @Override public void onError(String utteranceId) {
                latch.countDown();
            }
        });

        int result = tts.synthesizeToFile(text, null, wavFile, "tts_synth");
        if (result != TextToSpeech.SUCCESS) {
            throw new RuntimeException("TTS synthesizeToFile returned error: " + result);
        }

        assertTrue("TTS synthesis timed out", latch.await(15, TimeUnit.SECONDS));
        assertTrue("TTS synthesis failed", success[0]);

        return readWavAsPcm16(wavFile);
    }

    /**
     * Reads a WAV file and returns its audio data as PCM16 little-endian bytes.
     * Handles sample rate conversion if the WAV is not at TTS_SAMPLE_RATE.
     */
    private byte[] readWavAsPcm16(File wavFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(wavFile)) {
            byte[] header = new byte[44];
            int read = fis.read(header);
            if (read < 44) throw new IOException("WAV header too short");

            // Parse WAV header
            int channels = readShortLE(header, 22);
            int sampleRate = readIntLE(header, 24);
            int bitsPerSample = readShortLE(header, 34);

            // Read all audio data after header
            byte[] audioData = readAllBytes(fis);

            // Convert to mono PCM16 if needed
            byte[] pcm16;
            if (bitsPerSample == 16) {
                pcm16 = audioData;
            } else if (bitsPerSample == 8) {
                pcm16 = convert8bitTo16bit(audioData);
            } else {
                // Assume 16-bit
                pcm16 = audioData;
            }

            // If stereo, mix down to mono
            if (channels == 2) {
                pcm16 = stereoToMono(pcm16);
            }

            return pcm16;
        }
    }

    // =========================================================================
    // HELPER: AUDIO PLAYBACK
    // =========================================================================

    /**
     * Plays PCM16 mono audio through the device speaker and blocks until complete.
     */
    private void playPcm16(byte[] pcm16, int sampleRate) throws InterruptedException {
        int minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );

        AudioTrack track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(Math.max(minBuffer, pcm16.length))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build();

        track.write(pcm16, 0, pcm16.length);

        CountDownLatch latch = new CountDownLatch(1);
        track.setNotificationMarkerPosition(pcm16.length / 2); // 2 bytes per sample
        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override public void onMarkerReached(AudioTrack t) { latch.countDown(); }
            @Override public void onPeriodicNotification(AudioTrack t) {}
        });

        track.play();

        // Wait for playback to finish (calculate duration + buffer)
        long durationMs = (long) pcm16.length * 1000 / (sampleRate * 2);
        latch.await(durationMs + 1000, TimeUnit.MILLISECONDS);

        track.stop();
        track.release();
    }

    /**
     * Generates and plays a short beep tone to signal "ready for response".
     */
    private void playBeep() throws InterruptedException {
        int sampleCount = (TTS_SAMPLE_RATE * BEEP_DURATION_MS) / 1000;
        float[] beepSamples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            // Sine wave with fade-in/out envelope to avoid click
            float envelope = 1.0f;
            int fadeLen = sampleCount / 10;
            if (i < fadeLen) envelope = (float) i / fadeLen;
            else if (i > sampleCount - fadeLen) envelope = (float)(sampleCount - i) / fadeLen;
            beepSamples[i] = (float)(Math.sin(2.0 * Math.PI * BEEP_FREQUENCY * i / TTS_SAMPLE_RATE)) * envelope * 0.7f;
        }
        byte[] beepPcm = SoundEncodingUtil.packFloatTo16Bit(beepSamples);
        playPcm16(beepPcm, TTS_SAMPLE_RATE);
    }

    // =========================================================================
    // HELPER: AUDIO RECORDING WITH VOICE ACTIVITY DETECTION
    // =========================================================================

    /**
     * Records audio from the microphone with voice activity detection.
     * Waits for speech to start, then records until silence is detected
     * (or max duration is reached). Adds 1 second after silence detected.
     *
     * @return PCM16 mono 16kHz audio bytes
     */
    private byte[] recordResponse() throws Exception {
        int bufferSize = AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );

        AudioRecord recorder = new AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        );

        recorder.startRecording();

        byte[] buffer = new byte[bufferSize];
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        boolean speechStarted = false;
        int silenceCount = 0;
        long startTime = System.currentTimeMillis();
        long speechEndTime = 0;

        while (true) {
            long elapsed = System.currentTimeMillis() - startTime;

            // Hard timeout
            if (elapsed > MAX_RECORD_DURATION_MS) break;

            // If speech ended, record 1 more second then stop
            if (speechEndTime > 0 && (System.currentTimeMillis() - speechEndTime) > 1000) break;

            int bytesRead = recorder.read(buffer, 0, buffer.length);
            if (bytesRead <= 0) continue;

            baos.write(buffer, 0, bytesRead);

            // Check amplitude for VAD
            float amplitude = computeAmplitude(buffer, bytesRead);

            if (!speechStarted) {
                if (amplitude > SILENCE_AMPLITUDE) {
                    speechStarted = true;
                    silenceCount = 0;
                    logger.debug("Speech detected, amplitude: " + amplitude);
                }
            } else {
                if (amplitude < SILENCE_AMPLITUDE) {
                    silenceCount += bytesRead / 2; // samples
                    if (silenceCount >= SILENCE_THRESHOLD_SAMPLES && speechEndTime == 0) {
                        speechEndTime = System.currentTimeMillis();
                        logger.debug("Speech ended, recording tail...");
                    }
                } else {
                    silenceCount = 0;
                    speechEndTime = 0; // reset if speech resumes
                }
            }
        }

        recorder.stop();
        recorder.release();

        return baos.toByteArray();
    }

    /**
     * Computes the peak amplitude of a PCM16 buffer chunk.
     */
    private float computeAmplitude(byte[] buffer, int length) {
        float max = 0;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short)((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            float normalized = Math.abs(sample / 32768.0f);
            if (normalized > max) max = normalized;
        }
        return max;
    }

    // =========================================================================
    // HELPER: SPEECH RECOGNITION (VOSK)
    // =========================================================================

    /**
     * Runs Vosk speech recognition on PCM16 mono 16kHz audio.
     *
     * @return recognized text (lowercase)
     */
    private String recognizeSpeech(byte[] pcm16) throws Exception {
        Recognizer recognizer = new Recognizer(voskModel, RECORD_SAMPLE_RATE, "[\"pass\", \"fail\", \"[unk]\"]");

        int chunkSize = 4096;
        for (int offset = 0; offset < pcm16.length; offset += chunkSize) {
            int len = Math.min(chunkSize, pcm16.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(pcm16, offset, chunk, 0, len);
            recognizer.acceptWaveForm(chunk, len);
        }

        String finalResult = recognizer.getFinalResult();
        recognizer.close();

        // Parse JSON result: {"text": "..."}
        JSONObject json = new JSONObject(finalResult);
        return json.optString("text", "").toLowerCase(Locale.US).trim();
    }

    /**
     * Asserts that the recognized text contains "pass" or "fail".
     * Fails the test if "fail" is detected or if response is unrecognized.
     */
    private void assertPassOrFail(String recognized, String testDescription) {
        boolean hasPass = recognized.contains("pass");
        boolean hasFail = recognized.contains("fail");

        if (hasFail) {
            fail("User said FAIL for: " + testDescription + " (recognized: \"" + recognized + "\")");
        } else if (hasPass) {
            logger.info("PASSED: " + testDescription);
        } else {
            fail("Could not recognize pass/fail response for: " + testDescription
                + " (recognized: \"" + recognized + "\")");
        }
    }

    // =========================================================================
    // SETUP: TTS INITIALIZATION
    // =========================================================================

    private static void initTTS() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final int[] initStatus = {TextToSpeech.ERROR};

        tts = new TextToSpeech(context, status -> {
            initStatus[0] = status;
            latch.countDown();
        });

        assertTrue("TTS init timed out", latch.await(10, TimeUnit.SECONDS));
        if (initStatus[0] != TextToSpeech.SUCCESS) {
            throw new RuntimeException("TTS initialization failed with status: " + initStatus[0]);
        }

        tts.setLanguage(Locale.US);
        tts.setSpeechRate(0.9f); // Slightly slow for clarity
    }

    // =========================================================================
    // SETUP: VOSK MODEL DOWNLOAD
    // =========================================================================

    private static void initVosk() throws Exception {
        File modelDir = new File(context.getFilesDir(), VOSK_MODEL_DIR);

        if (!modelDir.exists() || !new File(modelDir, "conf/mfcc.conf").exists()) {
            logger.info("Downloading Vosk model...");
            downloadAndExtractModel(modelDir);
        } else {
            logger.info("Vosk model already present at: " + modelDir.getAbsolutePath());
        }

        voskModel = new Model(modelDir.getAbsolutePath());
        logger.info("Vosk model loaded successfully");
    }

    private static void downloadAndExtractModel(File targetDir) throws IOException {
        URL url = new URL(VOSK_MODEL_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        try (InputStream is = new BufferedInputStream(conn.getInputStream());
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                // Strip the top-level directory from zip paths
                String name = entry.getName();
                int slash = name.indexOf('/');
                if (slash < 0) continue;
                String relativePath = name.substring(slash + 1);
                if (relativePath.isEmpty()) continue;

                File outFile = new File(targetDir, relativePath);

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        } finally {
            conn.disconnect();
        }

        logger.info("Vosk model extracted to: " + targetDir.getAbsolutePath());
    }

    // =========================================================================
    // UTILITY: WAV / PCM CONVERSION
    // =========================================================================

    private static int readShortLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 3] & 0xFF) << 24);
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) > 0) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }

    private static byte[] convert8bitTo16bit(byte[] data8) {
        byte[] data16 = new byte[data8.length * 2];
        ByteBuffer buf = ByteBuffer.wrap(data16).order(ByteOrder.LITTLE_ENDIAN);
        for (byte b : data8) {
            // 8-bit WAV is unsigned, center at 128
            short sample = (short) (((b & 0xFF) - 128) * 256);
            buf.putShort(sample);
        }
        return data16;
    }

    private static byte[] stereoToMono(byte[] stereo16) {
        int sampleCount = stereo16.length / 4; // 2 channels * 2 bytes
        byte[] mono = new byte[sampleCount * 2];
        ByteBuffer in = ByteBuffer.wrap(stereo16).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            short left = in.getShort();
            short right = in.getShort();
            out.putShort((short) ((left + right) / 2));
        }
        return mono;
    }

    // =========================================================================
    // FUNCTIONAL INTERFACE
    // =========================================================================

    @FunctionalInterface
    private interface FilterFunction {
        float[] apply(VoiceProcessor vp, float[] samples);
    }
}
