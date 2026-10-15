package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Decoding checks for {@link OggVorbisDecoder} against the real {@code .ogg} assets. The frame
 * counts are cross-checked against {@code stb_vorbis} itself, so the tests verify the port's
 * streaming loop rather than trusting hard-coded numbers. Skipped without real assets.
 */
class OggVorbisDecoderTest {

    @Test
    void decodesFloopOggWithStbVorbisReportedFrameCount() throws IOException {
        byte[] ogg = soundAsset("sounds/floop.ogg");

        int reportedFrames = stbVorbisFrameCount(ogg);
        PcmSound sound = OggVorbisDecoder.decode(ogg);

        assertEquals(1, sound.channels());
        assertEquals(11025, sound.sampleRate());
        assertEquals(reportedFrames, sound.frameCount());
        assertEquals(1320, sound.frameCount());
        assertTrue(sound.hasNonSilentSamples(), "floop.ogg must not decode to silence");
    }

    @Test
    void decodesExplosionOggWithStbVorbisReportedFrameCount() throws IOException {
        byte[] ogg = soundAsset("sounds/explosion.ogg");

        int reportedFrames = stbVorbisFrameCount(ogg);
        PcmSound sound = OggVorbisDecoder.decode(ogg);

        assertEquals(2, sound.channels());
        assertEquals(44100, sound.sampleRate());
        assertEquals(reportedFrames, sound.frameCount());
        assertEquals(114404, sound.frameCount());
        assertTrue(sound.hasNonSilentSamples(), "explosion.ogg must not decode to silence");
    }

    @Test
    void failsOnTruncatedPayload() throws IOException {
        byte[] ogg = soundAsset("sounds/floop.ogg");
        byte[] half = Arrays.copyOf(ogg, ogg.length / 2);

        assertThrows(IOException.class, () -> OggVorbisDecoder.decode(half));
        assertThrows(IOException.class, () -> OggVorbisDecoder.openStream(half));
    }

    @Test
    void failsOnEmptyPayload() {
        assertThrows(IOException.class, () -> OggVorbisDecoder.decode(new byte[0]));
        assertThrows(IOException.class, () -> OggVorbisDecoder.decode(null));
    }

    @Test
    void openStreamReadsInSmallChunksMatchingDecode() throws IOException {
        byte[] ogg = soundAsset("sounds/explosion.ogg");
        PcmSound decoded = OggVorbisDecoder.decode(ogg);

        AudioStream stream = OggVorbisDecoder.openStream(ogg);
        try {
            assertEquals(decoded.channels(), stream.channels());
            assertEquals(decoded.sampleRate(), stream.sampleRate());

            short[] read = readFrames(stream, 512, decoded.frameCount());

            assertEquals(decoded.frameCount(), read.length / stream.channels());
            assertArrayEquals(decoded.samples(), read);
            assertEquals(0, stream.read(new short[512 * stream.channels()], 512));

            stream.rewind();
            short[] first = new short[64 * stream.channels()];
            assertEquals(64, stream.read(first, 64));
            assertArrayEquals(Arrays.copyOf(decoded.samples(), first.length), first);
        } finally {
            stream.close();
            stream.close();
        }
    }

    @Test
    void loopingStreamWrapsAroundTheEnd() throws IOException {
        byte[] ogg = soundAsset("sounds/floop.ogg");
        PcmSound decoded = OggVorbisDecoder.decode(ogg);
        int frames = decoded.frameCount();
        int extra = 64;

        AudioStream stream = OggVorbisDecoder.openStream(ogg);
        try {
            stream.setLooping(true);
            short[] dst = new short[(frames + extra) * stream.channels()];
            assertEquals(frames + extra, stream.read(dst, frames + extra));
            assertArrayEquals(decoded.samples(), Arrays.copyOf(dst, frames));
            assertArrayEquals(Arrays.copyOf(decoded.samples(), extra * stream.channels()),
                    Arrays.copyOfRange(dst, frames, dst.length));

            // A looped stream never reports the end.
            short[] more = new short[32 * stream.channels()];
            assertEquals(32, stream.read(more, 32));

            stream.setLooping(false);
            stream.rewind();
            assertEquals(frames, stream.read(new short[frames * stream.channels()], frames + 1));
        } finally {
            stream.close();
        }
    }

    /** Reads exactly {@code totalFrames} frames in {@code chunkFrames} steps (0 means broken). */
    private static short[] readFrames(AudioStream stream, int chunkFrames, int totalFrames) {
        int channels = stream.channels();
        short[] out = new short[totalFrames * channels];
        short[] chunk = new short[chunkFrames * channels];
        int frames = 0;
        while (frames < totalFrames) {
            int step = Math.min(chunkFrames, totalFrames - frames);
            int read = stream.read(chunk, step);
            assertEquals(step, read, "short read at frame " + frames);
            System.arraycopy(chunk, 0, out, frames * channels, read * channels);
            frames += read;
        }
        return out;
    }

    /** {@code stb_vorbis}'s own frame count for {@code ogg} (frames per channel). */
    private static int stbVorbisFrameCount(byte[] ogg) {
        ByteBuffer memory = MemoryUtil.memAlloc(ogg.length);
        try {
            memory.put(ogg).flip();
            int[] error = new int[1];
            long handle = STBVorbis.stb_vorbis_open_memory(memory, error, null);
            assertNotEquals(MemoryUtil.NULL, handle, "stb_vorbis_open_memory error " + error[0]);
            try {
                return STBVorbis.stb_vorbis_stream_length_in_samples(handle);
            } finally {
                STBVorbis.stb_vorbis_close(handle);
            }
        } finally {
            MemoryUtil.memFree(memory);
        }
    }

    /** Mounts the real {@code main.pak} and returns the named sound (case-insensitive). */
    private static byte[] soundAsset(String name) throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(pakPath.isPresent(), "no main.pak in the resource root");

        PakInterface pak = new PakInterface();
        pak.setResourceFolder(root.get().toString());
        pak.addPakFile(pakPath.get());
        return pak.requireResource(name);
    }
}
