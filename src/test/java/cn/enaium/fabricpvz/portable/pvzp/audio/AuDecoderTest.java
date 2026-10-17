package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link AuDecoder} against the Sun/NeXT container layout and the ITU-T G.711 µ-law table.
 *
 * <p>Expected samples are written out by hand from the standard expansion
 * ({@code u = ~b; t = ((u & 0xF) << 3) + 0x84; t <<= (u & 0x70) >> 4; sample = u & 0x80 ? 0x84 - t :
 * t - 0x84}), not read back from the decoder.
 */
class AuDecoderTest {

    /**
     * The µ-law payload every fixture here wraps: both zero codes, the two largest magnitudes and a
     * spread of segment/quantum combinations, including three consecutive segment boundaries.
     */
    private static final byte[] ULAW = {
        (byte) 0xFF, 0x7F, (byte) 0xFE, 0x7E, (byte) 0x80, 0x00, (byte) 0xF5, (byte) 0xEB, 0x3F, 0x28,
    };

    /**
     * ITU-T G.711 expansions of {@link #ULAW}: the two max-magnitude codes {@code ±32124}, the two
     * zero codes, and (0xF5, 0xEB) / (0x3F, 0x28) as a pair of quantum steps inside segment 0 and
     * segment 5/4.
     */
    private static final short[] EXPECTED = {
        0, 0, 8, -8, 32124, -32124, 80, 196, -1980, -5884,
    };

    @Test
    void decodesASteamStyleUlawFile() throws IOException {
        byte[] au = TestAudio.auUlaw(8012, 1, ULAW);

        PcmSound sound = AuDecoder.decode(au);

        assertEquals(8012, sound.sampleRate());
        assertEquals(1, sound.channels());
        assertEquals(ULAW.length, sound.frameCount());
        assertArrayEquals(EXPECTED, sound.samples());
        assertTrue(sound.hasNonSilentSamples());
    }

    @Test
    void decodesStereoInterleavedSamples() throws IOException {
        byte[] ulaw = {(byte) 0x80, 0x00, (byte) 0xFE, 0x7E, (byte) 0xF5, (byte) 0xEB};
        byte[] au = TestAudio.auUlaw(22050, 2, ulaw);

        PcmSound sound = AuDecoder.decode(au);

        assertEquals(22050, sound.sampleRate());
        assertEquals(2, sound.channels());
        assertEquals(3, sound.frameCount());
        // Interleaved: frame 0 = (max positive, max negative), frame 1 = (+8, -8), ...
        assertArrayEquals(new short[] {32124, -32124, 8, -8, 80, 196}, sound.samples());
    }

    @Test
    void unknownDataSizeMeansEverythingUpToTheEndOfTheFile() throws IOException {
        byte[] au = TestAudio.auUlaw(8000, 1, ULAW, 0xFFFFFFFFL);

        PcmSound sound = AuDecoder.decode(au);

        assertEquals(ULAW.length, sound.frameCount());
        assertArrayEquals(EXPECTED, sound.samples());
    }

    @Test
    void aZeroDataSizeAlsoMeansEverythingUpToTheEndOfTheFile() throws IOException {
        byte[] au = TestAudio.auUlaw(8000, 1, ULAW, 0L);

        assertEquals(ULAW.length, AuDecoder.decode(au).frameCount());
    }

    @Test
    void anIncompleteTrailingFrameIsDropped() throws IOException {
        byte[] ulaw = {(byte) 0xF5, (byte) 0xEB, (byte) 0xFE};
        byte[] au = TestAudio.auUlaw(8000, 2, ulaw);

        PcmSound sound = AuDecoder.decode(au);

        assertEquals(1, sound.frameCount(), "3 µ-law bytes are 1.5 stereo frames");
        assertArrayEquals(new short[] {80, 196}, sound.samples());
    }

    @Test
    void rejectsNonUlawAndMalformedContainers() {
        // encoding 2 (8-bit linear) and encoding 27 (A-law), neither of which the port decodes.
        assertThrows(IOException.class, () -> AuDecoder.decode(withEncoding(8000, 1, ULAW, 2)));
        assertThrows(IOException.class, () -> AuDecoder.decode(withEncoding(8000, 1, ULAW, 27)));

        // No magic, truncated header, absurd header fields.
        byte[] notAu = "RIFFxxxxWAVE".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(IOException.class, () -> AuDecoder.decode(notAu));
        assertThrows(IOException.class, () -> AuDecoder.decode(new byte[23]));
        assertThrows(IOException.class, () -> AuDecoder.decode(null));
        assertThrows(IOException.class, () -> AuDecoder.decode(TestAudio.auUlaw(0, 1, ULAW)));
        assertThrows(IOException.class, () -> AuDecoder.decode(TestAudio.auUlaw(8000, 0, ULAW)));

        // dataOffset/dataSize pointing outside the payload.
        byte[] beyondEnd = TestAudio.auUlaw(8000, 1, ULAW);
        beyondEnd[7] = (byte) 0xFF; // dataOffset = 0x000000FF
        assertThrows(IOException.class, () -> AuDecoder.decode(beyondEnd));

        byte[] shortPayload = TestAudio.auUlaw(8000, 1, ULAW, ULAW.length + 1);
        assertThrows(IOException.class, () -> AuDecoder.decode(shortPayload));

        // A payload that is not even one whole frame is rejected as an IOException (never as an
        // unchecked failure that would escape the loading thread's IOException handling).
        assertThrows(IOException.class, () -> AuDecoder.decode(TestAudio.auUlaw(8000, 2, new byte[] {0x01})));
        assertThrows(IOException.class,
                () -> AuDecoder.decode(TestAudio.auUlaw(8000, 1, new byte[0])));
    }

    @Test
    void theStreamingViewReadsEveryFrameOnce() throws IOException {
        byte[] au = TestAudio.auUlaw(8012, 1, ULAW);

        AudioStream stream = AuDecoder.openStream(au);
        try {
            assertEquals(1, stream.channels());
            assertEquals(8012, stream.sampleRate());

            short[] buffer = new short[4];
            assertEquals(4, stream.read(buffer, 4));
            assertArrayEquals(Arrays.copyOfRange(EXPECTED, 0, 4), buffer);
            assertEquals(4, stream.read(buffer, 4));
            assertArrayEquals(Arrays.copyOfRange(EXPECTED, 4, 8), buffer);
            assertEquals(2, stream.read(buffer, 4));
            assertArrayEquals(Arrays.copyOfRange(EXPECTED, 8, 10), Arrays.copyOfRange(buffer, 0, 2));
            assertEquals(0, stream.read(buffer, 4), "the stream ends after the last frame");

            stream.rewind();
            assertEquals(4, stream.read(buffer, 4));

            stream.rewind();
            stream.setLooping(true);
            short[] all = new short[EXPECTED.length * 2];
            assertEquals(all.length, stream.read(all, all.length), "looping streams wrap around");
            assertArrayEquals(EXPECTED, Arrays.copyOfRange(all, 0, EXPECTED.length));
            assertArrayEquals(EXPECTED, Arrays.copyOfRange(all, EXPECTED.length, all.length));
        } finally {
            stream.close();
        }
    }

    /**
     * Structural check that does not depend on the expansion formula: the µ-law code space is
     * ordered, so magnitude decreases monotonically from {@code 0x80} (max positive) to {@code 0x7F}
     * (zero) through {@code 0xFF} and back down to {@code 0x00} (max negative).
     */
    @Test
    void theCodeSpaceIsMonotonicAroundTheFullScaleExtremes() {
        assertEquals(32124, AuDecoder.decodeUlawByte((byte) 0x80), "most positive code");
        assertEquals(-32124, AuDecoder.decodeUlawByte((byte) 0x00), "most negative code");
        assertEquals(0, AuDecoder.decodeUlawByte((byte) 0xFF));
        assertEquals(0, AuDecoder.decodeUlawByte((byte) 0x7F));

        int previous = Integer.MAX_VALUE;
        for (int b = 0x80; b <= 0xFF; b++) {
            int sample = AuDecoder.decodeUlawByte((byte) b);
            assertTrue(sample <= previous, "0x" + Integer.toHexString(b) + " must not exceed the previous code");
            previous = sample;
        }
        assertEquals(0, previous);

        previous = Integer.MIN_VALUE;
        for (int b = 0x00; b <= 0x7F; b++) {
            int sample = AuDecoder.decodeUlawByte((byte) b);
            assertTrue(sample >= previous, "0x" + Integer.toHexString(b) + " must not fall below the previous code");
            previous = sample;
        }
        assertEquals(0, previous);
    }

    /** The fixture with a different big-endian {@code encoding} header word (byte offset 12). */
    private static byte[] withEncoding(int sampleRate, int channels, byte[] ulaw, int encoding) {
        byte[] au = TestAudio.auUlaw(sampleRate, channels, ulaw);
        au[12] = (byte) (encoding >>> 24);
        au[13] = (byte) (encoding >>> 16);
        au[14] = (byte) (encoding >>> 8);
        au[15] = (byte) encoding;
        return au;
    }
}
