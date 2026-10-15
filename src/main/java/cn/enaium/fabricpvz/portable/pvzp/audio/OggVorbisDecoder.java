package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

/**
 * Ported from src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_ogg_stb.c:465-622: Ogg Vorbis
 * decoder built on stb_vorbis, replacing that codec with the LWJGL binding of the same library.
 *
 * <p>Upstream feeds the whole file to {@code stb_vorbis_open_memory} and pulls float samples with
 * {@code stb_vorbis_get_samples_float_interleaved} before handing them to the mixer
 * (src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_ogg_stb.c:465-570); this port uses the
 * library's native 16-bit signed output instead, so no float conversion or mixer-format resampling
 * happens here. Channel count and sample rate are reported exactly as stb_vorbis sees them; OpenAL
 * converts per buffer/source like {@code SDL_BuildAudioCVT} does upstream
 * (src/SexyAppFramework/sound/SDLSoundManager.cpp:206-243).
 *
 * <p>{@link #openStream} mirrors the upstream loop handling of
 * {@code music_ogg_stb.c:590-621}: with looping enabled the stream seeks back to its start when the
 * end is reached instead of ending, which is the upstream "infinite play count" branch.
 *
 * <p>PORT-NOTE: upstream only loops between the {@code OGG_LOOPSTART}/{@code OGG_LOOPEND} comment
 * tags and otherwise restarts the whole stream ({@code music_ogg_stb.c:367-467}); the retail GOTY
 * assets carry no loop tags, so this stream always restarts at sample 0
 * ({@code stb_vorbis_seek_start}) — the tag branch collapses to {@code loop_start = 0}. The world's
 * copy of the file is held in an LWJGL-allocated direct buffer because stb_vorbis reads from native
 * memory and keeps that pointer until {@code stb_vorbis_close}.
 */
public final class OggVorbisDecoder {

    /** Initial frame estimate for {@link #decode}: 65536 frames (about 1.5 s at 44100 Hz). */
    private static final int INITIAL_FRAME_ESTIMATE = 1 << 16;

    /** Frames pulled per native call while decoding the whole stream. */
    private static final int DECODE_CHUNK_FRAMES = 4096;

    private OggVorbisDecoder() {
    }

    /** Decodes the complete Ogg Vorbis stream into interleaved 16-bit signed PCM. */
    public static PcmSound decode(byte[] data) throws IOException {
        ByteBuffer memory = toNativeMemory(data);
        long handle = MemoryUtil.NULL;
        try {
            int[] error = new int[1];
            handle = STBVorbis.stb_vorbis_open_memory(memory, error, null);
            if (handle == MemoryUtil.NULL) {
                throw new IOException("stb_vorbis could not open the Ogg Vorbis stream (error "
                        + error[0] + ": " + vorbisErrorName(error[0]) + ")");
            }
            int channels;
            int sampleRate;
            try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
                STBVorbis.stb_vorbis_get_info(handle, info);
                channels = info.channels();
                sampleRate = info.sample_rate();
            }

            short[] samples = readAll(handle, channels);

            int readError = STBVorbis.stb_vorbis_get_error(handle);
            if (readError != STBVorbis.VORBIS__no_error) {
                throw new IOException("stb_vorbis failed while decoding the Ogg Vorbis stream (error "
                        + readError + ": " + vorbisErrorName(readError) + ")");
            }
            if (samples.length == 0) {
                throw new IOException("stb_vorbis produced no samples for the Ogg Vorbis stream");
            }
            return new PcmSound(sampleRate, channels, samples);
        } finally {
            if (handle != MemoryUtil.NULL) {
                STBVorbis.stb_vorbis_close(handle);
            }
            MemoryUtil.memFree(memory);
        }
    }

    /** Opens a streaming decoder over {@code data} (rewindable, loopable, closes native state). */
    public static AudioStream openStream(byte[] data) throws IOException {
        return OggVorbisStream.open(data);
    }

    /** Reads every frame of {@code handle} into a growing interleaved sample array. */
    private static short[] readAll(long handle, int channels) {
        short[] samples = new short[INITIAL_FRAME_ESTIMATE * channels];
        int frames = 0;
        ShortBuffer chunk = MemoryUtil.memAllocShort(DECODE_CHUNK_FRAMES * channels);
        try {
            while (true) {
                chunk.clear();
                int read = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, chunk);
                if (read == 0) {
                    break;
                }
                int shorts = read * channels;
                samples = grow(samples, frames + read, channels);
                chunk.position(0).limit(shorts);
                chunk.get(samples, frames * channels, shorts);
                frames += read;
            }
        } finally {
            MemoryUtil.memFree(chunk);
        }
        if (frames * channels == samples.length) {
            return samples;
        }
        return Arrays.copyOf(samples, frames * channels);
    }

    /** Doubles {@code samples} until it can hold {@code framesNeeded} frames. */
    private static short[] grow(short[] samples, int framesNeeded, int channels) {
        int capacityFrames = samples.length / channels;
        if (framesNeeded <= capacityFrames) {
            return samples;
        }
        int newCapacityFrames = capacityFrames;
        while (newCapacityFrames < framesNeeded) {
            if (newCapacityFrames > Integer.MAX_VALUE / (2 * channels)) {
                newCapacityFrames = framesNeeded;
                break;
            }
            newCapacityFrames *= 2;
        }
        return Arrays.copyOf(samples, newCapacityFrames * channels);
    }

    /** Copies {@code data} into an LWJGL-allocated direct buffer (stb_vorbis needs native memory). */
    private static ByteBuffer toNativeMemory(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("Ogg Vorbis data is null or empty");
        }
        ByteBuffer memory = MemoryUtil.memAlloc(data.length);
        memory.put(data).flip();
        return memory;
    }

    /** {@code stb_vorbis} error code to a readable name (mirrors stb_vorbis.c's error enum). */
    private static String vorbisErrorName(int error) {
        return switch (error) {
            case STBVorbis.VORBIS__no_error -> "no error";
            case STBVorbis.VORBIS_need_more_data -> "need more data";
            case STBVorbis.VORBIS_invalid_api_mixing -> "invalid api mixing";
            case STBVorbis.VORBIS_outofmem -> "out of memory";
            case STBVorbis.VORBIS_feature_not_supported -> "feature not supported";
            case STBVorbis.VORBIS_too_many_channels -> "too many channels";
            case STBVorbis.VORBIS_file_open_failure -> "file open failure";
            case STBVorbis.VORBIS_seek_without_length -> "seek without length";
            case STBVorbis.VORBIS_unexpected_eof -> "unexpected end of file";
            case STBVorbis.VORBIS_seek_invalid -> "seek invalid";
            case STBVorbis.VORBIS_invalid_setup -> "invalid setup";
            case STBVorbis.VORBIS_invalid_stream -> "invalid stream";
            case STBVorbis.VORBIS_missing_capture_pattern -> "missing capture pattern";
            case STBVorbis.VORBIS_invalid_stream_structure_version -> "invalid stream structure version";
            case STBVorbis.VORBIS_continued_packet_flag_invalid -> "continued packet flag invalid";
            case STBVorbis.VORBIS_incorrect_stream_serial_number -> "incorrect stream serial number";
            case STBVorbis.VORBIS_invalid_first_page -> "invalid first page";
            case STBVorbis.VORBIS_bad_packet_type -> "bad packet type";
            case STBVorbis.VORBIS_cant_find_last_page -> "cannot find last page";
            case STBVorbis.VORBIS_seek_failed -> "seek failed";
            case STBVorbis.VORBIS_ogg_skeleton_not_supported -> "ogg skeleton not supported";
            default -> "unknown error";
        };
    }

    /**
     * Streaming Ogg Vorbis decoder over a native copy of the file; supports seeking back to the
     * start for seamless looping ({@link #setLooping}) and {@link #rewind()}.
     */
    public static final class OggVorbisStream implements AudioStream {

        private final ByteBuffer mData;
        private final long mHandle;
        private final int mChannels;
        private final int mSampleRate;
        private ShortBuffer mScratch;
        private boolean mLooping;
        private boolean mClosed;

        private OggVorbisStream(ByteBuffer data, long handle, int channels, int sampleRate) {
            mData = data;
            mHandle = handle;
            mChannels = channels;
            mSampleRate = sampleRate;
        }

        private static OggVorbisStream open(byte[] data) throws IOException {
            ByteBuffer memory = toNativeMemory(data);
            int[] error = new int[1];
            long handle = STBVorbis.stb_vorbis_open_memory(memory, error, null);
            if (handle == MemoryUtil.NULL) {
                MemoryUtil.memFree(memory);
                throw new IOException("stb_vorbis could not open the Ogg Vorbis stream (error "
                        + error[0] + ": " + vorbisErrorName(error[0]) + ")");
            }
            int channels;
            int sampleRate;
            try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
                STBVorbis.stb_vorbis_get_info(handle, info);
                channels = info.channels();
                sampleRate = info.sample_rate();
            }
            return new OggVorbisStream(memory, handle, channels, sampleRate);
        }

        @Override
        public int channels() {
            return mChannels;
        }

        @Override
        public int sampleRate() {
            return mSampleRate;
        }

        @Override
        public int read(short[] dst, int maxFrames) {
            if (mClosed || maxFrames <= 0) {
                return 0;
            }
            int written = 0;
            boolean endSeen = false;
            while (written < maxFrames) {
                ShortBuffer scratch = scratch((maxFrames - written) * mChannels);
                int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(mHandle, mChannels, scratch);
                if (frames == 0) {
                    // Upstream seeks back to the loop start instead of reporting the end
                    // (music_ogg_stb.c:590-621); a second empty read in a row means a broken stream.
                    if (!mLooping || endSeen || !STBVorbis.stb_vorbis_seek_start(mHandle)) {
                        break;
                    }
                    endSeen = true;
                    continue;
                }
                endSeen = false;
                int shorts = frames * mChannels;
                scratch.position(0).limit(shorts);
                scratch.get(dst, written * mChannels, shorts);
                written += frames;
            }
            return written;
        }

        @Override
        public void setLooping(boolean looping) {
            mLooping = looping;
        }

        @Override
        public void rewind() {
            if (!mClosed) {
                STBVorbis.stb_vorbis_seek_start(mHandle);
            }
        }

        @Override
        public void close() {
            if (mClosed) {
                return;
            }
            mClosed = true;
            STBVorbis.stb_vorbis_close(mHandle);
            MemoryUtil.memFree(mData);
            if (mScratch != null) {
                MemoryUtil.memFree(mScratch);
                mScratch = null;
            }
        }

        /** Direct scratch buffer holding exactly {@code shortsNeeded} shorts. */
        private ShortBuffer scratch(int shortsNeeded) {
            if (mScratch == null || mScratch.capacity() < shortsNeeded) {
                if (mScratch != null) {
                    MemoryUtil.memFree(mScratch);
                }
                mScratch = MemoryUtil.memAllocShort(shortsNeeded);
            }
            mScratch.clear();
            mScratch.limit(shortsNeeded);
            return mScratch;
        }
    }
}
