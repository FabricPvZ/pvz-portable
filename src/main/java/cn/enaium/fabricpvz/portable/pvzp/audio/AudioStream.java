package cn.enaium.fabricpvz.portable.pvzp.audio;

/**
 * Streaming PCM source used for music playback: 16-bit signed interleaved samples produced on
 * demand, at the stream's own sample rate and channel count.
 *
 * <p>Ported from src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_openmpt.c:243-300
 * (codec rendering) and :348-400 (order jump, order query, channel volume).
 *
 * <p>Mirrors the parts of the SDL-Mixer-X codec interface that the port actually uses
 * (src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_openmpt.c):
 * <ul>
 *   <li>{@link #read} replaces {@code GetSome}/{@code GetAudio}; it returns 0 once the stream is
 *       exhausted.</li>
 *   <li>{@link #seekOrder} replaces {@code Mix_ModMusicStreamJumpToOrder}
 *       ({@code music_openmpt.c:355-364}: low 16 bits = order, high 16 bits = row).</li>
 *   <li>{@link #currentOrder} replaces {@code Mix_ModMusicStreamGetOrder}.</li>
 *   <li>{@link #setChannelVolume} replaces {@code Mix_ModMusicStreamSetChannelVolume}
 *       ({@code music_openmpt.c:395-400}: {@code set_channel_volume(ext, channel, volume / 128.0)}
 *       where {@code volume} is in {@code MIX_MAX_VOLUME} units).</li>
 * </ul>
 *
 * <p>Non-tracker streams (Ogg Vorbis, WAV) implement the order/channel methods as the upstream
 * no-ops they are for those codecs.
 */
public interface AudioStream extends AutoCloseable {

    /** Channel count of the samples produced by {@link #read} (1 or 2). */
    int channels();

    /** Sample rate of the samples produced by {@link #read}. */
    int sampleRate();

    /**
     * Reads up to {@code maxFrames} interleaved frames into {@code dst} (which must hold at least
     * {@code maxFrames * channels()} samples) and returns the number of frames written, or 0 when
     * the stream has ended.
     */
    int read(short[] dst, int maxFrames);

    /**
     * Enables or disables looping at the decoder level, mirroring the per-codec loop handling of
     * SDL-Mixer-X: the openmpt codec loops inside libopenmpt
     * ({@code music_openmpt.c:225}: {@code openmpt_module_set_repeat_count(file, -1)}), the
     * stb_vorbis codec seeks back to the loop start when the end is reached. With looping enabled
     * {@link #read} only returns 0 for a broken stream; with looping disabled it returns 0 at the end
     * of the song.
     */
    default void setLooping(boolean looping) {
    }

    /** Restarts the stream from the beginning (upstream loop handling). */
    default void rewind() {
    }

    /** Upstream {@code Mix_ModMusicStreamJumpToOrder}: true when the jump was applied. */
    default boolean seekOrder(int order) {
        return false;
    }

    /** Upstream {@code Mix_ModMusicStreamGetOrder}: order pack, or -1 when unsupported/unknown. */
    default int currentOrder() {
        return -1;
    }

    /** Upstream {@code Mix_ModMusicStreamSetChannelVolume}: {@code volume} is in 0..128 units. */
    default boolean setChannelVolume(int channel, double volume) {
        return false;
    }

    @Override
    default void close() {
    }
}
