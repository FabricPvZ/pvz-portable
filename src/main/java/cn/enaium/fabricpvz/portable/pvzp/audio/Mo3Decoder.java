package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Un4seen MO3 tracker decoder backed by the optional native {@code libopenmpt} library.
 *
 * <p>Ported from
 * src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_openmpt.c — the whole-module render is
 * modelled on {@code OPENMPT_GetSome} (lines 268-340) and the file level duration on
 * {@code OPENMPT_GetDuration} reading {@code openmpt_module_get_duration_seconds}. The mixer format
 * targeted here is 44100 Hz stereo, i.e. {@code settings.mFrequency}/{@code settings.mChannels} as
 * used by the openmpt codec (mirrored by {@link PcmSound}: SDL_mixer-X converts every decoded chunk
 * to the live mixer spec).
 *
 * <p>The library is an optional native dependency: libopenmpt must be installed (Homebrew
 * {@code brew install libopenmpt} on macOS, {@code apt install libopenmpt0} on Debian/Ubuntu, or a
 * libopenmpt build in the library search path). When it is missing {@link #isAvailable()} is false
 * and every entry point throws an {@link IOException} mentioning libopenmpt; music playback then
 * reports unavailable while sound effects keep working (the WAV and Ogg decoders do not depend on
 * libopenmpt).
 *
 * <p>PORT-NOTE: the FFM binding, library discovery and module lifetime live in
 * {@link OpenMptLibrary}; upstream uses SDL's dlopen shim. PORT-NOTE: libopenmpt renders native
 * {@code float} PCM and this port converts it to the signed 16-bit samples the rest of the port
 * uses with a saturating {@code *32768} scale (upstream renders to 16-bit directly through
 * {@code openmpt_module_read_stereo}); the conversion is exposed as {@link #toPcm16(float)} so the
 * streaming decoder uses the exact same rule.
 */
public final class Mo3Decoder {

    /** Sample rate libopenmpt renders at, matching upstream {@code settings.mFrequency}. */
    public static final int RENDER_RATE = 44100;

    /** Channel count libopenmpt renders (stereo, matching upstream {@code settings.mChannels}). */
    public static final int CHANNELS = 2;

    private static final int CHUNK_FRAMES = 4096;

    private Mo3Decoder() {
    }

    /** True when libopenmpt is present and usable; false means MO3 music is unavailable. */
    public static boolean isAvailable() {
        return OpenMptLibrary.isAvailable();
    }

    /** Empty when libopenmpt is available; otherwise a description including "libopenmpt" and the searched paths. */
    public static String unavailableReason() {
        return OpenMptLibrary.unavailableReason();
    }

    /** Playback length of the module in seconds, read from libopenmpt without rendering any audio. */
    public static double durationSeconds(byte[] data) throws IOException {
        try (OpenMptLibrary.Module module = OpenMptLibrary.createModule(data)) {
            return module.durationSeconds();
        }
    }

    /** Decodes the whole module once (repeat count 0) to 44100 Hz stereo 16-bit PCM. */
    public static PcmSound decode(byte[] data) throws IOException {
        return decode(data, Integer.MAX_VALUE);
    }

    /** Decodes at most {@code maxFrames} frames per channel to 44100 Hz stereo 16-bit PCM. */
    public static PcmSound decode(byte[] data, int maxFrames) throws IOException {
        OpenMptLibrary.Module module = OpenMptLibrary.createModule(data);
        try {
            module.setRepeatCount(0);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate((long) CHUNK_FRAMES * CHANNELS * Float.BYTES);
                List<short[]> blocks = new ArrayList<>();
                long totalFrames = 0;
                while (totalFrames < maxFrames) {
                    int want = (int) Math.min(CHUNK_FRAMES, maxFrames - totalFrames);
                    int frames = module.readStereo(RENDER_RATE, want, buffer);
                    if (frames <= 0) {
                        break;
                    }
                    short[] block = new short[frames * CHANNELS];
                    for (int i = 0; i < block.length; i++) {
                        block[i] = toPcm16(buffer.getAtIndex(ValueLayout.JAVA_FLOAT, i));
                    }
                    blocks.add(block);
                    totalFrames += frames;
                }
                if (totalFrames == 0) {
                    throw new IOException("libopenmpt produced no audio frames for the module");
                }
                short[] samples = new short[(int) totalFrames * CHANNELS];
                int offset = 0;
                for (short[] block : blocks) {
                    System.arraycopy(block, 0, samples, offset, block.length);
                    offset += block.length;
                }
                return new PcmSound(RENDER_RATE, CHANNELS, samples);
            }
        } finally {
            module.close();
        }
    }

    /** Opens a streaming 44100 Hz stereo MO3 source; the caller closes it. */
    public static AudioStream openStream(byte[] data) throws IOException {
        OpenMptLibrary.Module module = OpenMptLibrary.createModule(data);
        try {
            return new Mo3Stream(module);
        } catch (RuntimeException e) {
            module.close();
            throw e;
        }
    }

    /** Concrete streaming type, exposed for the extended-interface (order/channel volume) checks. */
    public static Mo3Stream openMo3Stream(byte[] data) throws IOException {
        OpenMptLibrary.Module module = OpenMptLibrary.createModule(data);
        try {
            return new Mo3Stream(module);
        } catch (RuntimeException e) {
            module.close();
            throw e;
        }
    }

    /** Saturating native {@code float} -> signed 16-bit conversion shared by both decode paths. */
    static short toPcm16(float sample) {
        return (short) Math.clamp((long) (sample * 32768.0f), -32768L, 32767L);
    }
}
