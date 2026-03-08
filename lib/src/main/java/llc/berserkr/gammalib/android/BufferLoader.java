package llc.berserkr.gammalib.android;

import java.io.EOFException;

public interface BufferLoader {

    int read(byte [] buffer, int offset, int max) throws EOFException;

    void start();
    void stop();
}
