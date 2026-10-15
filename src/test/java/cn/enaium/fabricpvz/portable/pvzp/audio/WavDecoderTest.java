package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Header/widening checks for {@link WavDecoder}: every case builds the RIFF/WAVE bytes in memory, so
 * these tests never need game assets.
 */
class WavDecoderTest {

    private static final short[] MONO_44K = {0, 1, -1, 12345, -12345, Short.MAX_VALUE, Short.MIN_VALUE, 256};

    private static final short[] STEREO_22K = {100, -100, 200, -200, 300, -300, 32767, -32768};

    @Test
    void decodes16BitMonoAt44100() throws IOException {
        PcmSound sound = WavDecoder.decode(wavFile(fmtChunk(1, 1, 44100, 2, 16), dataChunk(pcm16(MONO_44K))));

        assertEquals(44100, sound.sampleRate());
        assertEquals(1, sound.channels());
        assertEquals(MONO_44K.length, sound.frameCount());
        assertArrayEquals(MONO_44K, sound.samples());
    }

    @Test
    void decodes16BitStereoAt22050() throws IOException {
        PcmSound sound = WavDecoder.decode(wavFile(fmtChunk(1, 2, 22050, 4, 16), dataChunk(pcm16(STEREO_22K))));

        assertEquals(22050, sound.sampleRate());
        assertEquals(2, sound.channels());
        assertEquals(STEREO_22K.length / 2, sound.frameCount());
        assertArrayEquals(STEREO_22K, sound.samples());
    }

    @Test
    void decodes8BitUnsignedMonoWithBoundaryValues() throws IOException {
        byte[] pcm = {0x00, (byte) 0x80, (byte) 0xFF};
        PcmSound sound = WavDecoder.decode(wavFile(fmtChunk(1, 1, 8000, 1, 8), dataChunk(pcm)));

        assertEquals(8000, sound.sampleRate());
        assertEquals(1, sound.channels());
        assertArrayEquals(new short[]{-32768, 0, 32512}, sound.samples());
        assertEquals((short) ((0x00 - 128) << 8), sound.samples()[0]);
        assertEquals((short) ((0xFF - 128) << 8), sound.samples()[2]);
    }

    @Test
    void decodes8BitStereo() throws IOException {
        byte[] pcm = {0x00, (byte) 0xFF, (byte) 0x80, (byte) 0x90};
        PcmSound sound = WavDecoder.decode(wavFile(fmtChunk(1, 2, 11025, 2, 8), dataChunk(pcm)));

        assertEquals(11025, sound.sampleRate());
        assertEquals(2, sound.channels());
        assertEquals(2, sound.frameCount());
        assertArrayEquals(new short[]{-32768, 32512, 0, (short) ((0x90 - 128) << 8)}, sound.samples());
    }

    @Test
    void skipsUnknownChunksBeforeFmtAndAfterData() throws IOException {
        byte[] plain = wavFile(fmtChunk(1, 1, 44100, 2, 16), dataChunk(pcm16(MONO_44K)));
        byte[] padded = chunk("LIST", new byte[]{1, 2, 3, 4, 5});
        byte[] odd = chunk("JUNK", new byte[]{9});
        byte[] decorated = wavFile(padded, fmtChunk(1, 1, 44100, 2, 16), odd, dataChunk(pcm16(MONO_44K)),
                chunk("fact", new byte[]{0, 0, 0, 0}));

        PcmSound expected = WavDecoder.decode(plain);
        PcmSound actual = WavDecoder.decode(decorated);

        assertEquals(expected.sampleRate(), actual.sampleRate());
        assertEquals(expected.channels(), actual.channels());
        assertArrayEquals(expected.samples(), actual.samples());
    }

    @Test
    void decodesWhenDataChunkComesBeforeFmtChunk() throws IOException {
        byte[] dataFirst = wavFile(dataChunk(pcm16(MONO_44K)), fmtChunk(1, 1, 44100, 2, 16));

        assertArrayEquals(MONO_44K, WavDecoder.decode(dataFirst).samples());
    }

    @Test
    void failsOnTruncatedRiffHeader() {
        assertThrows(IOException.class, () -> WavDecoder.decode(new byte[]{'R', 'I', 'F', 'F'}));
        byte[] valid = wavFile(fmtChunk(1, 1, 44100, 2, 16), dataChunk(pcm16(MONO_44K)));
        assertThrows(IOException.class, () -> WavDecoder.decode(Arrays.copyOf(valid, 16)));
    }

    @Test
    void failsOnRiffSizeLargerThanTheBuffer() {
        byte[] valid = wavFile(fmtChunk(1, 1, 44100, 2, 16), dataChunk(pcm16(MONO_44K)));
        putUint32(valid, 4, valid.length);

        assertThrows(IOException.class, () -> WavDecoder.decode(valid));
    }

    @Test
    void failsWithoutFmtChunk() {
        byte[] noFmt = wavFile(dataChunk(pcm16(MONO_44K)));

        assertThrows(IOException.class, () -> WavDecoder.decode(noFmt));
    }

    @Test
    void failsWithoutDataChunk() {
        byte[] noData = wavFile(fmtChunk(1, 1, 44100, 2, 16));

        assertThrows(IOException.class, () -> WavDecoder.decode(noData));
    }

    @Test
    void failsOnIeeeFloatFormatTag() {
        byte[] floatFile = wavFile(fmtChunk(3, 1, 44100, 4, 16), dataChunk(new byte[16]));
        IOException error = assertThrows(IOException.class, () -> WavDecoder.decode(floatFile));

        assertTrue(error.getMessage().contains("3"), error.getMessage());
    }

    @Test
    void failsOn24BitPcm() {
        byte[] file24 = wavFile(fmtChunk(1, 1, 44100, 3, 24), dataChunk(new byte[12]));

        assertThrows(IOException.class, () -> WavDecoder.decode(file24));
    }

    @Test
    void failsOnBlockAlignMismatch() {
        byte[] mismatch = wavFile(fmtChunk(1, 2, 44100, 2, 16), dataChunk(pcm16(STEREO_22K)));

        assertThrows(IOException.class, () -> WavDecoder.decode(mismatch));
    }

    @Test
    void failsWhenDataChunkLengthExceedsTheBuffer() throws IOException {
        byte[] valid = wavFile(fmtChunk(1, 1, 44100, 2, 16), dataChunk(pcm16(MONO_44K)));
        // Rewrite the data chunk size field (last 8 + payload bytes) to claim more than it holds.
        int dataHeader = valid.length - 8 - MONO_44K.length * 2;
        putUint32(valid, dataHeader + 4, MONO_44K.length * 2 + 64);

        assertThrows(IOException.class, () -> WavDecoder.decode(valid));
    }

    @Test
    void failsWhenDataChunkIsNotAWholeNumberOfFrames() {
        byte[] oddFrames = wavFile(fmtChunk(1, 2, 44100, 4, 16), dataChunk(new byte[]{0, 0, 0, 0, 0, 0}));

        assertThrows(IOException.class, () -> WavDecoder.decode(oddFrames));
    }

    @Test
    void failsOnEmptyDataChunk() {
        byte[] empty = wavFile(fmtChunk(1, 1, 44100, 2, 16), dataChunk(new byte[0]));

        assertThrows(IOException.class, () -> WavDecoder.decode(empty));
    }

    @Test
    void failsOnAbsurdSampleRateOrChannelCount() {
        assertThrows(IOException.class, () -> WavDecoder.decode(
                wavFile(fmtChunk(1, 1, 0, 2, 16), dataChunk(pcm16(MONO_44K)))));
        assertThrows(IOException.class, () -> WavDecoder.decode(
                wavFile(fmtChunk(1, 1, 400_000, 2, 16), dataChunk(pcm16(MONO_44K)))));
        assertThrows(IOException.class, () -> WavDecoder.decode(
                wavFile(fmtChunk(1, 9, 44100, 18, 16), dataChunk(pcm16(MONO_44K)))));
        assertThrows(IOException.class, () -> WavDecoder.decode(
                wavFile(fmtChunk(1, 0, 44100, 0, 16), dataChunk(pcm16(MONO_44K)))));
    }

    @Test
    void openStreamStreamsFramesInStepsAndRewinds() throws IOException {
        PcmSound expected = WavDecoder.decode(wavFile(fmtChunk(1, 2, 22050, 4, 16), dataChunk(pcm16(STEREO_22K))));
        AudioStream stream = WavDecoder.openStream(wavFile(fmtChunk(1, 2, 22050, 4, 16), dataChunk(pcm16(STEREO_22K))));

        assertEquals(2, stream.channels());
        assertEquals(22050, stream.sampleRate());

        short[] dst = new short[3 * 2];
        short[] read = new short[expected.samples().length];
        int frames = 0;
        int step;
        while ((step = stream.read(dst, 3)) > 0) {
            System.arraycopy(dst, 0, read, frames * 2, step * 2);
            frames += step;
        }

        assertEquals(expected.frameCount(), frames);
        assertArrayEquals(expected.samples(), read);

        stream.rewind();
        assertEquals(2, stream.read(dst, 2));
        assertArrayEquals(new short[]{STEREO_22K[0], STEREO_22K[1], STEREO_22K[2], STEREO_22K[3]},
                Arrays.copyOf(dst, 4));
        assertEquals(2, stream.read(dst, 4));
        assertEquals(0, stream.read(dst, 4));

        stream.close();
        stream.close();
    }

    @Test
    void openStreamReadsWholeBufferInOneCallAndReturnsZeroAtTheEnd() throws IOException {
        byte[] bytes = wavFile(fmtChunk(1, 1, 44100, 2, 16), dataChunk(pcm16(MONO_44K)));
        AudioStream stream = WavDecoder.openStream(bytes);

        short[] dst = new short[MONO_44K.length + 8];
        assertEquals(MONO_44K.length, stream.read(dst, dst.length));
        assertEquals(0, stream.read(dst, dst.length));
        assertEquals(0, stream.read(dst, 0));
        assertFalse(Arrays.equals(dst, new short[dst.length]));
    }

    // --- RIFF/WAVE byte builders -----------------------------------------------------------------

    private static byte[] wavFile(byte[]... chunks) {
        int size = 12;
        for (byte[] chunk : chunks) {
            size += chunk.length;
        }
        byte[] file = new byte[size];
        ascii(file, 0, "RIFF");
        putUint32(file, 4, size - 8);
        ascii(file, 8, "WAVE");
        int offset = 12;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, file, offset, chunk.length);
            offset += chunk.length;
        }
        return file;
    }

    private static byte[] fmtChunk(int formatTag, int channels, int sampleRate, int blockAlign, int bits) {
        byte[] payload = new byte[16];
        putUint16(payload, 0, formatTag);
        putUint16(payload, 2, channels);
        putUint32(payload, 4, sampleRate);
        putUint32(payload, 8, sampleRate * blockAlign);
        putUint16(payload, 12, blockAlign);
        putUint16(payload, 14, bits);
        return chunk("fmt ", payload);
    }

    private static byte[] dataChunk(byte[] pcm) {
        return chunk("data", pcm);
    }

    private static byte[] chunk(String id, byte[] payload) {
        byte[] out = new byte[8 + payload.length + (payload.length & 1)];
        ascii(out, 0, id);
        putUint32(out, 4, payload.length);
        System.arraycopy(payload, 0, out, 8, payload.length);
        return out;
    }

    private static byte[] pcm16(short[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            out[i * 2] = (byte) (samples[i] & 0xFF);
            out[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        return out;
    }

    private static void ascii(byte[] target, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            target[offset + i] = (byte) ascii.charAt(i);
        }
    }

    private static void putUint16(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void putUint32(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }
}
