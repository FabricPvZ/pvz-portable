package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Tests the content sniffer and the decoder dispatch of {@link AudioFormat}. */
class AudioFormatTest {

    @Test
    void recognizesTheThreeContainersTheRetailAssetsUse() {
        assertEquals(AudioFormat.WAV, AudioFormat.sniff(TestAudio.wavTone(22050, 1, 8)));
        assertEquals(AudioFormat.OGG, AudioFormat.sniff(oggHeader()));
        assertEquals(AudioFormat.MO3, AudioFormat.sniff(mo3Header()));
    }

    @Test
    void rejectsShortAndForeignPayloads() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.sniff(null));
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.sniff(new byte[0]));
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.sniff(new byte[11]));
        // A RIFF container that is not WAVE is not a WAV file we can decode.
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.sniff(ascii("RIFFxxxxAVI ")));
        // Almost-Ogg magic is not Ogg.
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.sniff(ascii("OggZ23456789012")));
    }

    @Test
    void unknownFormatCannotBeDecodedOrStreamed() {
        byte[] payload = ascii("not an audio container at all");
        assertThrows(IOException.class, () -> AudioFormat.UNKNOWN.decodeSound(payload));
        assertThrows(IOException.class, () -> AudioFormat.UNKNOWN.openStream(payload));
    }

    @Test
    void wavDispatchesToTheWavDecoderAndToTheStreamingView() throws IOException {
        byte[] payload = TestAudio.wavTone(22050, 1, 64);

        assertEquals(64, AudioFormat.WAV.decodeSound(payload).frameCount());

        AudioStream stream = AudioFormat.WAV.openStream(payload);
        try {
            assertNotNull(stream);
            assertEquals(1, stream.channels());
            short[] buffer = new short[64];
            assertEquals(64, stream.read(buffer, 64));
            assertEquals(0, stream.read(buffer, 64));
        } finally {
            stream.close();
        }
    }

    private static byte[] oggHeader() {
        byte[] data = new byte[16];
        byte[] magic = ascii("OggS");
        System.arraycopy(magic, 0, data, 0, magic.length);
        return data;
    }

    private static byte[] mo3Header() {
        byte[] data = new byte[16];
        byte[] magic = ascii("MO3");
        System.arraycopy(magic, 0, data, 0, magic.length);
        data[3] = 0x01;
        return data;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
