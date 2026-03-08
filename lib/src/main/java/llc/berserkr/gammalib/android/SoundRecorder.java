package llc.berserkr.gammalib.android;

import android.Manifest;

import androidx.annotation.RequiresPermission;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class SoundRecorder {

    private final BufferLoader bufferLoader;
    private final int bufferSize;
    private boolean isRecording = false;

    public SoundRecorder(final BufferLoader bufferLoader, final int bufferSize) {
        this.bufferSize = bufferSize;
        this.bufferLoader = bufferLoader;
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public synchronized InputStream startRecording() {

        bufferLoader.start();
        isRecording = true;

        return new InputStream() {
            private final byte [] buffer = new byte [bufferSize];
            private volatile int bytesInBuffer = 0;
            private volatile int position = 0;
            private synchronized void updateData() throws EOFException {

                //shift left accounting for the current position.
                System.arraycopy(buffer, position, buffer, 0, buffer.length - position);

                bytesInBuffer -= position;
                position = 0;

                bytesInBuffer += bufferLoader.read(buffer, bytesInBuffer, bufferSize);

            }

            /**
             * Reads the next byte of data from the input stream.
             * @return The next byte as an int (0-255), or -1 if end of stream.
             */
            @Override
            public synchronized int read() throws IOException {

                try {
                    if (!isRecording) {
                        return -1;
                    }

                    while (bytesInBuffer == 0 || (bytesInBuffer - position) == 0) { //keeps reading until we get some data
                        updateData();
                        Thread.yield();
                    }

                    return buffer[position++] & 0xFF; // Ensure unsigned byte
                }
                catch (EOFException eof) {
                    return -1;
                }

            }

            /**
             * Reads up to len bytes into the given buffer.
             */
            @Override
            public synchronized int read(byte[] inputBuffer, int offset, int len) throws IOException {

                if(len == 0) {
                    throw new IllegalArgumentException("len should not be 0");
                }

                if (!isRecording) {
                    return -1;
                }

                try {
                    if (inputBuffer == null) {
                        throw new NullPointerException("Buffer cannot be null");
                    }
                    if (offset < 0 || len < 0 || len > inputBuffer.length - offset) {
                        throw new IndexOutOfBoundsException("Invalid offset/length");
                    }

                    while (bytesInBuffer == 0 || (bytesInBuffer - position) == 0) {
                        updateData();
                        Thread.yield();
                    }

                    //dont read more than the input buffer
                    final int bytesToRead = Math.min(len, bytesInBuffer - position);

                    System.arraycopy(buffer, position, inputBuffer, offset, bytesToRead);

                    position += bytesToRead;

                    return bytesToRead;
                }
                catch (EOFException eof) {
                    return -1;
                }
            }

        };
    }

    public synchronized void stopRecording() {

        isRecording = false;
        bufferLoader.stop();

    }

    public int getBufferSize() {
        return bufferSize;
    }
}
