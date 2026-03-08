package llc.berserkr.gammalib.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class StreamUtil {
    public static final int DEFAULT_BUFFER_SIZE = 10000;

    private StreamUtil() {
    }

    public static void copyTo(InputStream in, Set<OutputStream> outs, int bufferSize) throws IOException {
        if (in == null) {
            throw new NullPointerException("<StreamUtil><1>In cannot be null");
        } else if (outs == null) {
            throw new NullPointerException("<StreamUtil><2>Out cannot be null");
        } else if (outs.contains((Object)null)) {
            throw new NullPointerException("<StreamUtil><3>outs cannot contain null");
        } else {
            byte[] buffer = new byte[bufferSize];

            while(true) {
                int read = in.read(buffer);
                if (read <= 0) {
                    for(OutputStream out : outs) {
                        out.flush();
                    }

                    return;
                }

                for(OutputStream out : outs) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    public static void copyTo(InputStream in, OutputStream out, int bufferSize) throws IOException {
        Set<OutputStream> outs = new HashSet();
        outs.add(out);
        copyTo(in, outs, bufferSize);
    }

    public static void copyTo(InputStream is, OutputStream out) throws IOException {
        copyTo(is, out, 10000);
    }

    public static byte[] digest(InputStream in) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        DigestInputStream is = new DigestInputStream(in, md);

        try {
            byte[] buffer = new byte[1024];

            while(is.read(buffer) >= 0) {
            }
        } finally {
            is.close();
        }

        return is.getMessageDigest().digest();
    }
}

