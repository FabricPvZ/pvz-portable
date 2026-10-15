package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * MO3 (libopenmpt) decoder checks. The library is an optional native dependency, so the positive
 * cases are gated on both libopenmpt being installed and the real {@code main.pak} being available.
 */
class Mo3DecoderTest {

    @Test
    void missingLibraryReportsReasonAndFailsAsIOException() {
        if (Mo3Decoder.isAvailable()) {
            return; // exercised by the positive tests below
        }
        assertTrue(Mo3Decoder.unavailableReason().contains("libopenmpt"));

        IOException decodeFailure = assertThrows(IOException.class,
                () -> Mo3Decoder.decode(new byte[] {1, 2, 3}));
        assertTrue(decodeFailure.getMessage().contains("libopenmpt"));

        IOException streamFailure = assertThrows(IOException.class,
                () -> Mo3Decoder.openStream(new byte[] {1, 2, 3}));
        assertTrue(streamFailure.getMessage().contains("libopenmpt"));
    }

    @Test
    void decodesRealMainMusicToMixerFormat() throws IOException {
        Assumptions.assumeTrue(Mo3Decoder.isAvailable(), "libopenmpt is not installed");
        byte[] bytes = loadRealMainMusic();

        assertTrue(bytes.length > 0);
        assertEquals(AudioFormat.MO3, AudioFormat.sniff(bytes));

        double duration = Mo3Decoder.durationSeconds(bytes);
        assertTrue(duration > 0.0, "duration must be positive");
        assertEquals(189.8, duration, 5.0);

        PcmSound decoded = Mo3Decoder.decode(bytes, Mo3Decoder.RENDER_RATE * 2);
        assertEquals(2, decoded.channels());
        assertEquals(Mo3Decoder.RENDER_RATE, decoded.sampleRate());
        assertEquals(Mo3Decoder.RENDER_RATE * 2, decoded.frameCount());
        assertTrue(decoded.hasNonSilentSamples(), "decoded main music must not be silent");
    }

    @Test
    void streamsRealMainMusicWithOrderAndChannelControls() throws IOException {
        Assumptions.assumeTrue(Mo3Decoder.isAvailable(), "libopenmpt is not installed");
        byte[] bytes = loadRealMainMusic();

        Mo3Stream stream = Mo3Decoder.openMo3Stream(bytes);
        try {
            assertEquals(2, stream.channels());
            assertEquals(Mo3Decoder.RENDER_RATE, stream.sampleRate());

            assertTrue(stream.seekOrder(152));
            assertEquals(152, stream.currentOrder());

            assertTrue(stream.setChannelVolume(3, 64.0));
            assertEquals(0.5, stream.getChannelVolume(3), 0.1);

            short[] dst = new short[4096 * 2];
            int frames = stream.read(dst, 4096);
            assertTrue(frames > 0, "stream must produce frames");
            assertTrue(hasNonZero(dst, frames * 2), "stream must not be silent");
        } finally {
            stream.close();
            stream.close(); // close is idempotent
        }
    }

    @Test
    void malformedModuleFailsWithIOExceptionNotLinkError() {
        Assumptions.assumeTrue(Mo3Decoder.isAvailable(), "libopenmpt is not installed");

        IOException failure = assertThrows(IOException.class,
                () -> Mo3Decoder.decode(new byte[] {'M', 'O', '3', 1, 0, 0, 0, 0}));
        assertTrue(failure.getMessage().contains("libopenmpt"));
    }

    private static boolean hasNonZero(short[] samples, int count) {
        for (int i = 0; i < count; i++) {
            if (samples[i] != 0) {
                return true;
            }
        }
        return false;
    }

    private static byte[] loadRealMainMusic() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(pakPath.isPresent(), "no main.pak in the resource root");

        PakInterface pak = new PakInterface();
        pak.setResourceFolder(root.get().toString());
        assertTrue(pak.addPakFile(pakPath.get()), "real main.pak must mount");
        return pak.requireResource("sounds/mainmusic.mo3");
    }
}
