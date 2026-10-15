package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Streaming MO3 source backed by one live libopenmpt module, rendered straight into the mixer
 * format (44100 Hz stereo 16-bit signed PCM).
 *
 * <p>Ported from src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_openmpt.c:
 * <ul>
 *   <li>{@link #read} mirrors {@code OPENMPT_GetSome} (lines 268-340) — it renders up to the
 *       requested frame count and returns 0 once the song is finished.</li>
 *   <li>{@link #setLooping} mirrors the codec's loop handling: {@code music_openmpt.c:225}
 *       ({@code openmpt_module_set_repeat_count(file, -1)}) for looping, repeat count 0 for
 *       one-shot playback.</li>
 *   <li>{@link #seekOrder}/{@link #currentOrder} mirror {@code OPENMPT_Jump} (348-354) and
 *       {@code OPENMPT_GetOrder} (356-364): low 16 bits = order, high 16 bits = row.</li>
 *   <li>{@link #setChannelVolume}/{@link #getChannelVolume} mirror
 *       {@code OPENMPT_SetChannelVolume} (395-400):
 *       {@code set_channel_volume(ext, channel, volume / 128.0)}.</li>
 * </ul>
 *
 * <p>libopenmpt is an optional native dependency (see {@link Mo3Decoder}); without it no
 * {@code Mo3Stream} can be constructed and music reports unavailable while sound effects keep
 * working.
 *
 * <p>PORT-NOTE: upstream renders with {@code openmpt_module_read_stereo} into 16-bit samples; this
 * port renders {@code openmpt_module_read_interleaved_float_stereo} and converts with
 * {@link Mo3Decoder#toPcm16(float)} so both the whole-module and streaming paths share one
 * saturating conversion. PORT-NOTE: the module is released with
 * {@code openmpt_module_ext_destroy} (libopenmpt_ext.h:82), and the ext interactive interface is
 * queried once per stream to resolve the two channel-volume function pointers.
 */
public final class Mo3Stream implements AudioStream {

    private final OpenMptLibrary.Module module;
    private final Arena arena = Arena.ofShared();
    private MemorySegment buffer;
    private int bufferFrames;
    private boolean closed;

    Mo3Stream(OpenMptLibrary.Module module) {
        this.module = module;
        module.setRepeatCount(0);
    }

    @Override
    public int channels() {
        return Mo3Decoder.CHANNELS;
    }

    @Override
    public int sampleRate() {
        return Mo3Decoder.RENDER_RATE;
    }

    @Override
    public int read(short[] dst, int maxFrames) {
        if (closed || maxFrames <= 0) {
            return 0;
        }
        int samples = maxFrames * channels();
        if (dst.length < samples) {
            throw new IllegalArgumentException("dst holds " + dst.length
                    + " samples, need " + samples + " for " + maxFrames + " frames");
        }
        ensureBuffer(maxFrames);
        int frames = module.readStereo(Mo3Decoder.RENDER_RATE, maxFrames, buffer);
        if (frames <= 0) {
            return 0;
        }
        int produced = frames * channels();
        for (int i = 0; i < produced; i++) {
            dst[i] = Mo3Decoder.toPcm16(buffer.getAtIndex(ValueLayout.JAVA_FLOAT, i));
        }
        return frames;
    }

    @Override
    public void setLooping(boolean looping) {
        if (closed) {
            return;
        }
        module.setRepeatCount(looping ? -1 : 0);
    }

    @Override
    public void rewind() {
        if (closed) {
            return;
        }
        module.setPositionOrderRow(0, 0);
    }

    @Override
    public boolean seekOrder(int pack) {
        if (closed) {
            return false;
        }
        int order = pack & 0xFFFF;
        int row = (pack >>> 16) & 0xFFFF;
        module.setPositionOrderRow(order, row);
        return true;
    }

    @Override
    public int currentOrder() {
        if (closed) {
            return -1;
        }
        int order = module.currentOrder();
        int row = module.currentRow();
        return ((row & 0xFFFF) << 16) | (order & 0xFFFF);
    }

    @Override
    public boolean setChannelVolume(int channel, double volume) {
        if (closed || channel < 0 || channel >= module.numChannels()) {
            return false;
        }
        return module.setChannelVolume(channel, volume / AudioGain.MIX_MAX_VOLUME);
    }

    /** Extended-interface round-trip companion of {@link #setChannelVolume}; 0.0 when out of range. */
    public double getChannelVolume(int channel) {
        if (closed || channel < 0 || channel >= module.numChannels()) {
            return 0.0;
        }
        return module.getChannelVolume(channel);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        module.close();
        arena.close();
    }

    private void ensureBuffer(int frames) {
        if (buffer != null && frames <= bufferFrames) {
            return;
        }
        int capacity = Math.max(frames, bufferFrames * 2);
        buffer = arena.allocate((long) capacity * channels() * Float.BYTES);
        bufferFrames = capacity;
    }
}
