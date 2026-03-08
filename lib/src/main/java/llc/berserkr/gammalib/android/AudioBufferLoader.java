package llc.berserkr.gammalib.android;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.annotation.RequiresPermission;

import java.io.EOFException;

public class AudioBufferLoader implements BufferLoader {

    private final int bufferSize;
    private final int channelConfig;
    private final int sampleRate;
    private final int audioFormat;
    private final int audioSource;
    private AudioRecord audioRecord;

    public AudioBufferLoader(int audioSource, int sampleRate, int channelConfig, int audioFormat) {

        this.audioSource = audioSource;
        this.sampleRate = sampleRate;
        this.channelConfig = channelConfig;
        this.audioFormat = audioFormat;

        bufferSize = AudioRecord.getMinBufferSize(
                sampleRate, channelConfig, audioFormat
//                96_000, // Sample rate
//            AudioFormat.CHANNEL_IN_MONO, // Channel configuration
//            AudioFormat.ENCODING_PCM_24BIT_PACKED // Audio format
        );

    }

    public int getMinBufferSize() {
        return bufferSize;
    }

    @Override
    public int read(byte[] buffer, int offset, int max) throws EOFException {

        if(audioRecord == null) {
            throw new IllegalStateException("you must start the audio buffer loader before reading");
        }
        return audioRecord.read(buffer, offset, max);
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Override
    public void start() {
        audioRecord = new AudioRecord(
                audioSource,
//            MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat,
//            96_000,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            bufferSize
        );

        audioRecord.startRecording();
    }

    @Override
    public void stop() {

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

    }
}
