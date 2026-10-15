package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * OpenAL device/context lifetime, replacing SDL's audio subsystem plus SDL_mixer's device handling.
 *
 * <p>Ported from src/SexyAppFramework/sound/SDLSoundManager.cpp:27-66 (device open) and
 * :68-80 (teardown order).
 *
 * <p>Device parameters mirror {@code SDLSoundManager::SDLSoundManager()}
 * (src/SexyAppFramework/sound/SDLSoundManager.cpp:27-66):
 * {@code Mix_OpenAudio(44100, AUDIO_S16SYS, 2, 2048)} — 44100 Hz, signed 16-bit, stereo, 2048-frame
 * mixer buffer ({@code ALC10} has no chunk-size attribute, so {@link #BUFFER_FRAMES} is used as the
 * size of each queued music buffer instead; upstream mixer callback size is
 * {@code SDL_mixer.h:260-268}).
 *
 * <p>Failure to open a device is not fatal: {@link #available()} returns {@code false} and the
 * sound manager/music interface degrade to the upstream "mixer init failed" behaviour, exactly like
 * {@code mInitializedMixer == false} upstream.
 */
public final class OpenAlDevice implements AutoCloseable {

    /** Upstream {@code Mix_OpenAudio} frequency. */
    public static final int FREQUENCY = 44100;

    /** Upstream {@code Mix_OpenAudio} channel count. */
    public static final int CHANNELS = 2;

    /** Upstream {@code Mix_OpenAudio} chunk size, in sample frames. */
    public static final int BUFFER_FRAMES = 2048;

    private long mDevice = MemoryUtil.NULL;
    private long mContext = MemoryUtil.NULL;
    private ALCapabilities mCapabilities;
    private boolean mAvailable;
    private String mErrorMessage = "";

    /** Opens the default device and makes a context current; never throws. */
    public OpenAlDevice() {
        long device = MemoryUtil.NULL;
        long context = MemoryUtil.NULL;
        try {
            device = ALC10.alcOpenDevice((ByteBuffer) null);
            if (device == MemoryUtil.NULL) {
                mErrorMessage = "alcOpenDevice failed (no audio device available)";
                return;
            }

            // Only ALC_FREQUENCY is settable; the S16/stereo chunk format lives in the buffers.
            IntBuffer attributes = MemoryUtil.memAllocInt(3);
            try {
                attributes.put(ALC10.ALC_FREQUENCY).put(FREQUENCY).put(0);
                attributes.flip();
                context = ALC10.alcCreateContext(device, attributes);
            } finally {
                MemoryUtil.memFree(attributes);
            }
            if (context == MemoryUtil.NULL) {
                ALC10.alcCloseDevice(device);
                mErrorMessage = "alcCreateContext failed (ALC error " + ALC10.alcGetError(device) + ")";
                return;
            }
            if (!ALC10.alcMakeContextCurrent(context)) {
                ALC10.alcDestroyContext(context);
                ALC10.alcCloseDevice(device);
                mErrorMessage = "alcMakeContextCurrent failed (ALC error " + ALC10.alcGetError(device) + ")";
                return;
            }

            ALCCapabilities alcCapabilities = ALC.createCapabilities(device);
            mCapabilities = AL.createCapabilities(alcCapabilities);
            mDevice = device;
            mContext = context;
            mAvailable = true;

            // Upstream never enables positional audio (no Mix_SetPosition/Mix_SetDistance and an
            // empty RehupPan in SDLSoundInstance.cpp:65-67); all sources are relative.
            AL10.alDistanceModel(AL10.AL_NONE);
            setMasterGain(1.0f);
        } catch (Throwable t) {
            mErrorMessage = "OpenAL initialization failed: " + t;
            if (context != MemoryUtil.NULL) {
                ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
                ALC10.alcDestroyContext(context);
            }
            if (device != MemoryUtil.NULL) {
                ALC10.alcCloseDevice(device);
            }
        }
    }

    /** True when a device and a current context could be created. */
    public boolean available() {
        return mAvailable;
    }

    /** Human readable reason when {@link #available()} is false. */
    public String errorMessage() {
        return mErrorMessage;
    }

    /** The context's capabilities, needed to attach other threads (see {@link #attachThread}). */
    ALCapabilities capabilities() {
        return mCapabilities;
    }

    /** Makes AL calls legal on the calling thread (LWJGL keeps capabilities per thread). */
    public void attachThread() {
        if (mAvailable) {
            AL.setCurrentThread(mCapabilities);
        }
    }

    /**
     * Applies the SDL_mixer global gain ({@code Mix_MasterVolume}) to the listener, so it scales
     * every source (sound effects and music) like upstream's mixer-wide master volume.
     */
    public void setMasterGain(float gain) {
        if (mAvailable) {
            AL10.alListenerf(AL10.AL_GAIN, gain);
        }
    }

    /** OpenAL buffer format for a PCM channel count. */
    public static int alFormat(int channels) {
        return switch (channels) {
            case 1 -> AL10.AL_FORMAT_MONO16;
            case 2 -> AL10.AL_FORMAT_STEREO16;
            default -> throw new IllegalArgumentException("Unsupported channel count: " + channels);
        };
    }

    /** Copies {@code frames} interleaved frames out of a native buffer into a Java array. */
    static void copyFrames(ShortBuffer source, short[] dst, int frames, int channels) {
        source.clear();
        source.limit(frames * channels);
        source.get(dst, 0, frames * channels);
    }

    /** Byte size of one frame of 16-bit interleaved PCM. */
    static int frameBytes(int channels) {
        return channels * Short.BYTES;
    }

    @Override
    public void close() {
        if (mContext != MemoryUtil.NULL) {
            ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
            ALC10.alcDestroyContext(mContext);
            mContext = MemoryUtil.NULL;
        }
        if (mDevice != MemoryUtil.NULL) {
            ALC10.alcCloseDevice(mDevice);
            mDevice = MemoryUtil.NULL;
        }
        mAvailable = false;
    }
}
