package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;

/**
 * Ported from src/SexyAppFramework/sound/SDLSoundManager.cpp:84-244 (the hand-written {@code .au}
 * reader that assembles 16-bit signed PCM) and
 * src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_wav.c:1386-1445 (PCM encoding/bit-depth
 * selection): RIFF/WAVE PCM decoder producing {@link PcmSound} data at the file's own sample rate
 * and channel count.
 *
 * <p>Replaces the SDL_mixer-X WAV codec ({@code music_wav.c}) for the load path. Upstream converts
 * every decoded chunk to 16-bit signed PCM and lets {@code SDL_BuildAudioCVT} resample it to the
 * mixer spec (44100 Hz stereo) — see {@code SDLSoundManager::LoadSound}
 * (src/SexyAppFramework/sound/SDLSoundManager.cpp:247-310) and the PCM assembly in
 * {@code SDLSoundManager::LoadAUSound} (src/SexyAppFramework/sound/SDLSoundManager.cpp:84-244),
 * whose 8-bit path is fed straight into {@code AUDIO_S16SYS} conversion. OpenAL performs that
 * conversion per buffer/source, so this decoder only widens the samples:
 * <ul>
 *   <li>{@code WAVE_FORMAT_PCM} (tag 1) is the only accepted encoding, matching the PCM branch of
 *       {@code music_wav.c:1386-1405}.</li>
 *   <li>8-bit PCM is unsigned ({@code AUDIO_U8}, {@code music_wav.c:1431-1434}) and becomes
 *       {@code (v - 128) << 8}; 16-bit PCM is already signed little-endian
 *       ({@code AUDIO_S16}, {@code music_wav.c:1439-1443}).</li>
 * </ul>
 *
 * <p>PORT-NOTE: the RIFF container parser is Java-specific; upstream delegates it to
 * {@code SDL_LoadWAV_RW}/{@code WAV_Load} ({@code music_wav.c:1340-1520}). This port has no SDL RWops,
 * so the chunk walk is reimplemented here: {@code RIFF} + little-endian uint32 chunk size +
 * {@code WAVE}, then {@code fmt }/{@code data} located in any order with every other chunk
 * ({@code LIST}, {@code fact}, {@code JUNK}, ...) skipped, payload sizes padded to even lengths.
 */
public final class WavDecoder {

    /** {@code WAVE_FORMAT_PCM}. */
    private static final int FORMAT_PCM = 0x0001;

    /** Sanity bounds for the header fields (absurd values fail instead of allocating). */
    private static final int MAX_SAMPLE_RATE = 384_000;

    private static final int MAX_CHANNELS = 8;

    private static final int RIFF_HEADER_SIZE = 12;

    private static final int CHUNK_HEADER_SIZE = 8;

    private static final int FMT_MIN_SIZE = 16;

    private WavDecoder() {
    }

    /** Decodes the complete WAV file into interleaved 16-bit signed PCM. */
    public static PcmSound decode(byte[] data) throws IOException {
        if (data == null) {
            throw new IOException("WAV data is null");
        }
        if (data.length < RIFF_HEADER_SIZE) {
            throw new IOException("Truncated RIFF header: " + data.length + " bytes");
        }
        if (!isAscii(data, 0, "RIFF")) {
            throw new IOException("Not a RIFF file (missing RIFF signature)");
        }
        // The RIFF size field counts everything after the size field itself (file size - 8).
        long riffSize = readUint32Le(data, 4);
        if (riffSize + CHUNK_HEADER_SIZE > data.length) {
            throw new IOException("RIFF chunk size " + riffSize + " exceeds available data "
                    + (data.length - CHUNK_HEADER_SIZE) + " bytes");
        }
        if (!isAscii(data, 8, "WAVE")) {
            throw new IOException("RIFF file is not of WAVE form");
        }

        int formatTag = -1;
        int channels = -1;
        int sampleRate = -1;
        int blockAlign = -1;
        int bitsPerSample = -1;
        int dataOffset = -1;
        int dataSize = -1;

        int pos = RIFF_HEADER_SIZE;
        while (pos + CHUNK_HEADER_SIZE <= data.length) {
            long chunkSize = readUint32Le(data, pos + 4);
            if (pos + CHUNK_HEADER_SIZE + chunkSize > data.length) {
                throw new IOException("Chunk '" + chunkId(data, pos) + "' of size " + chunkSize
                        + " exceeds the remaining " + (data.length - pos - CHUNK_HEADER_SIZE) + " bytes");
            }
            int payload = pos + CHUNK_HEADER_SIZE;
            if (isAscii(data, pos, "fmt ")) {
                if (chunkSize < FMT_MIN_SIZE) {
                    throw new IOException("fmt chunk too small: " + chunkSize + " bytes");
                }
                formatTag = readUint16Le(data, payload);
                channels = readUint16Le(data, payload + 2);
                sampleRate = (int) readUint32Le(data, payload + 4);
                blockAlign = readUint16Le(data, payload + 12);
                bitsPerSample = readUint16Le(data, payload + 14);
            } else if (isAscii(data, pos, "data")) {
                dataOffset = payload;
                dataSize = (int) chunkSize;
            }
            if (formatTag >= 0 && dataOffset >= 0) {
                break;
            }
            pos = payload + (int) chunkSize + ((int) chunkSize & 1);
        }

        if (formatTag < 0) {
            throw new IOException("WAV file has no fmt chunk");
        }
        if (dataOffset < 0) {
            throw new IOException("WAV file has no data chunk");
        }
        if (formatTag != FORMAT_PCM) {
            throw new IOException("Unsupported WAV format tag 0x" + Integer.toHexString(formatTag)
                    + " (only PCM/0x1 is supported)");
        }
        if (sampleRate <= 0 || sampleRate > MAX_SAMPLE_RATE) {
            throw new IOException("Invalid WAV sample rate: " + sampleRate);
        }
        if (channels <= 0 || channels > MAX_CHANNELS) {
            throw new IOException("Invalid WAV channel count: " + channels);
        }
        if (bitsPerSample != 8 && bitsPerSample != 16) {
            throw new IOException("Unsupported PCM bit depth: " + bitsPerSample);
        }
        int frameBytes = channels * (bitsPerSample / 8);
        if (blockAlign != frameBytes) {
            throw new IOException("WAV nBlockAlign " + blockAlign + " does not match channels*bytesPerSample "
                    + frameBytes);
        }
        if (dataSize == 0) {
            throw new IOException("WAV data chunk is empty");
        }
        if (dataSize % frameBytes != 0) {
            throw new IOException("WAV data chunk of " + dataSize + " bytes is not a whole number of "
                    + frameBytes + "-byte frames");
        }

        int sampleCount = dataSize / (bitsPerSample / 8);
        short[] samples = new short[sampleCount];
        if (bitsPerSample == 16) {
            for (int i = 0; i < sampleCount; i++) {
                int p = dataOffset + i * 2;
                samples[i] = (short) ((data[p] & 0xFF) | (data[p + 1] << 8));
            }
        } else {
            for (int i = 0; i < sampleCount; i++) {
                samples[i] = (short) (((data[dataOffset + i] & 0xFF) - 128) << 8);
            }
        }
        return new PcmSound(sampleRate, channels, samples);
    }

    /** Opens an in-memory streaming view of {@link #decode(byte[])} (rewindable, never blocks). */
    public static AudioStream openStream(byte[] data) throws IOException {
        return new WavStream(decode(data));
    }

    private static int readUint16Le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readUint32Le(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static String chunkId(byte[] data, int offset) {
        StringBuilder id = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            int c = data[offset + i] & 0xFF;
            id.append(c >= 0x20 && c < 0x7F ? (char) c : '?');
        }
        return id.toString();
    }

    private static boolean isAscii(byte[] data, int offset, String magic) {
        if (offset + magic.length() > data.length) {
            return false;
        }
        for (int i = 0; i < magic.length(); i++) {
            if ((data[offset + i] & 0xFF) != magic.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Array-backed {@link AudioStream} over an already decoded sound; {@link #rewind()} resets to
     * frame 0 and {@link #close()} is a no-op because no native resources are held.
     */
    private static final class WavStream implements AudioStream {

        private final PcmSound mSound;
        private int mFrame;
        private boolean mLooping;

        private WavStream(PcmSound sound) {
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
