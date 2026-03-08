package llc.berserkr.gammalib;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;

import llc.berserkr.gammalib.util.MP3ToPCMConverter;
import llc.berserkr.gammalib.util.SoundEncodingUtil;
import llc.berserkr.gammalib.util.StreamUtil;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class GammaUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

//    @Test
//    public void loadMP3Data() throws IOException {
//        // Local unit tests usually run from the module directory
//        final File file = new File("src/test/assets/retro_westerwald.mp3");
//        final File output = new File("data/test/retro_westerwald_filtered.mp3");
//
//        if (!output.exists()) {
//            output.createNewFile();
//        }
//
//        // Fallback if running from the project root directory
//        if (!file.exists()) {
//            fail("retro_westerwald.mp3 not found");
//        }
//
//        assertTrue("Test asset file not found at " + file.getAbsolutePath(), file.exists());
//
//        byte[] mp3Bytes = Files.readAllBytes(file.toPath());
//
//        assertNotNull("MP3 data should not be null", mp3Bytes);
//        assertTrue("MP3 data should not be empty", mp3Bytes.length > 0);
//
//        try(final InputStream inputStream = new ByteArrayInputStream(mp3Bytes)) {
//            try (final FileOutputStream outputStream = new FileOutputStream(output, false)) {
//                StreamUtil.copyTo(inputStream, outputStream);
//
//                outputStream.flush();
//            }
//        }
//
//    }


}
