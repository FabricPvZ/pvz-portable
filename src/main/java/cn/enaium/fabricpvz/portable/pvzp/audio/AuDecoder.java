package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;

/**
 * Ported from src/SexyAppFramework/sound/SDLSoundManager.cpp:84-244
 * ({@code SDLSoundManager::LoadAUSound}, the hand-written Sun/NeXT {@code .au} reader that upstream
 * runs as the <em>last</em> sound probe: {@code .wav}/{@code .mp3}/{@code .ogg} first, then
 * {@code theFilename + ".au"}, SDLSoundManager.cpp:281-305).
 *
 * <p>Reads the six mandatory big-endian header words — magic {@code ".snd"} ({@code 0x2e736e64}),
 * {@code dataOffset}, {@code dataSize}, {@code encoding}, {@code sampleRate}, {@code channels} —
 * seeks to {@code dataOffset} and expands the payload to 16-bit signed PCM.
 *
 * <p>Only {@code encoding = 1} (8-bit ITU-T G.711 µ-law) is decoded, which is what the retail Steam
 * assets use ({@code sounds/diamond.au}, {@code sounds/tapglass.au}: 8012 Hz mono, dataOffset 28).
 * Upstream additionally has a (broken: it copies 8-bit data straight into an {@code AUDIO_S16SYS}
 * buffer) branch for {@code encoding = 2}; every other encoding is rejected there as well.
 *
 * <p>PORT-NOTE: upstream assembles the samples with a hand-rolled branch chain
 * (SDLSoundManager.cpp:196-232) that approximates the G.711 expansion: it differs from the ITU table
 * by up to 4 LSB at every segment boundary and decodes the single most-positive code
 * ({@code 0x80}) as {@code +1020} instead of {@code +32124}. The port uses the standard expansion
 * ({@link #decodeUlawByte}) so the audio matches every other µ-law decoder and the expected samples
 * can be checked against the ITU table instead of against this implementation.
 *
 * <p>PORT-NOTE: the header/seek/trim handling is Java-specific (upstream uses {@code p_fopen}/
 * {@code p_fseek}): {@code dataSize} may be {@code 0} or {@code 0xFFFFFFFF} ("up to the end of the
 * file" — the standard's way of saying "unknown"), a declared size that runs past the end of the
 * buffer is an error rather than a silently truncated sound, and a payload that is not a whole
 * number of frames is trimmed to the last complete frame (upstream does the same through
 * {@code wavecvt.len = aDestSize & ~(samplesize - 1)}, SDLSoundManager.cpp:237). Every rejection is
 * an {@link IOException}, including a payload shorter than one frame, so callers can treat a
 * malformed file exactly like an unsupported one (log and skip) instead of crashing on it.
 */
public final class AuDecoder {

    /** {@code ".snd"} — the first header word of a Sun/NeXT AU container, big-endian. */
    private static final int MAGIC = 0x2E736E64;

    /** AU encoding 1: 8-bit ITU-T G.711 µ-law (the only encoding this decoder accepts). */
    private static final int ENCODING_ULAW = 1;

    /** The six mandatory big-endian header words, in bytes. */
    private static final int HEADER_SIZE = 24;

    /** The {@code dataSize} word of {@code 0} / {@code 0xFFFFFFFF} means "unknown, use the rest". */
    private static final long DATA_SIZE_UNKNOWN = 0xFFFFFFFFL;

    /** Sanity bounds for the header fields (absurd values fail instead of allocating). */
    private static final int MAX_SAMPLE_RATE = 384_000;

    private static final int MAX_CHANNELS = 8;

    /** G.711 sign bit (of the complemented code word) and the expansion bias. */
    private static final int SIGN_BIT = 0x80;

    private static final int SEGMENT_SHIFT = 4;

    private static final int QUANT_MASK = 0x0F;

    private static final int SEGMENT_MASK = 0x70;

    private static final int BIAS = 0x84;

    private AuDecoder() {
    }

    /** Decodes the complete AU file into interleaved 16-bit signed PCM. */
    public static PcmSound decode(byte[] data) throws IOException {
        if (data == null) {
            throw new IOException("AU data is null");
        }
        if (data.length < HEADER_SIZE) {
            throw new IOException("Truncated AU header: " + data.length + " bytes");
        }
        if (readUint32Be(data, 0) != MAGIC) {
            throw new IOException("Not an AU file (missing \".snd\" signature)");
        }

        long dataOffset = readUint32Be(data, 4);
        long dataSize = readUint32Be(data, 8);
        long encoding = readUint32Be(data, 12);
        long sampleRate = readUint32Be(data, 16);
        long channels = readUint32Be(data, 20);

        if (encoding != ENCODING_ULAW) {
            throw new IOException("Unsupported AU encoding " + encoding + " (only 1/µ-law is supported)");
        }
        if (sampleRate <= 0 || sampleRate > MAX_SAMPLE_RATE) {
            throw new IOException("Invalid AU sample rate: " + sampleRate);
        }
        if (channels <= 0 || channels > MAX_CHANNELS) {
            throw new IOException("Invalid AU channel count: " + channels);
        }
        if (dataOffset < HEADER_SIZE || dataOffset > data.length) {
            throw new IOException("AU data offset " + dataOffset + " is outside the "
                    + data.length + "-byte payload");
        }

        int available = data.length - (int) dataOffset;
        if (dataSize != 0 && dataSize != DATA_SIZE_UNKNOWN) {
            if (dataSize > available) {
                throw new IOException("AU payload of " + dataSize + " bytes exceeds the "
                        + available + " bytes after the data offset");
            }
            available = (int) dataSize;
        }

        int sampleCount = available - available % (int) channels;
        if (sampleCount == 0) {
            throw new IOException("AU payload of " + available + " bytes is shorter than one "
                    + channels + "-channel frame");
        }
        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = decodeUlawByte(data[(int) dataOffset + i]);
        }
        return new PcmSound((int) sampleRate, (int) channels, samples);
    }

    /** Opens an in-memory streaming view of {@link #decode(byte[])} (rewindable, never blocks). */
    public static AudioStream openStream(byte[] data) throws IOException {
        return new AuStream(decode(data));
    }

    /**
     * Expands one ITU-T G.711 µ-law code word to a signed 16-bit sample (the standard
     * {@code ulaw2linear} expansion of ITU-T G.711 §3.3): the code word is complemented, split into
     * sign/segment/quantum, the quantum is scaled by the segment and the bias removed.
     *
     * <p>The result spans the full 16-bit range: {@code 0x80} decodes to {@code +32124} (the largest
     * magnitude), {@code 0x00} to {@code -32124} and both zero codes ({@code 0xFF} and {@code 0x7F})
     * to {@code 0}.
     */
    static short decodeUlawByte(byte value) {
        int code = (~value) & 0xFF;
        int sample = ((code & QUANT_MASK) << 3) + BIAS;
        sample <<= (code & SEGMENT_MASK) >>> SEGMENT_SHIFT;
        return (short) ((code & SIGN_BIT) != 0 ? BIAS - sample : sample - BIAS);
    }

    private static long readUint32Be(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24)
                | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8)
                | (data[offset + 3] & 0xFFL);
    }

    /**
     * Array-backed {@link AudioStream} over an already decoded sound; {@link #rewind()} resets to
     * frame 0 and {@link #close()} is a no-op because no native resources are held. Mirrors
     * {@code WavDecoder.WavStream} (upstream feeds both containers through the same
     * {@code SDL_BuildAudioCVT}/{@code Mix_Chunk} path).
     */
    private static final class AuStream implements AudioStream {

        private final PcmSound mSound;
        private int mFrame;
        private boolean mLooping;

        private AuStream(PcmSound sound) {
            mSound = sound;
        }

        @Override
        public int channels() {
            return mSound.channels();
        }

        @Override
        public int sampleRate() {
            return mSound.sampleRate();
        }

        @Override
        public int read(short[] dst, int maxFrames) {
            if (maxFrames <= 0) {
                return 0;
            }
            int channels = mSound.channels();
            int total = mSound.frameCount();
            int frames = 0;
            while (frames < maxFrames) {
                if (mFrame >= total) {
                    if (!mLooping) {
                        break;
                    }
                    mFrame = 0;
                }
                int chunk = Math.min(maxFrames - frames, total - mFrame);
                System.arraycopy(mSound.samples(), mFrame * channels, dst, frames * channels,
                        chunk * channels);
                frames += chunk;
                mFrame += chunk;
            }
            return frames;
        }

        @Override
        public void setLooping(boolean looping) {
            mLooping = looping;
        }

        @Override
        public void rewind() {
            mFrame = 0;
        }
    }
}
