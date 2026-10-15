package cn.enaium.fabricpvz.portable.pvzp.audio;

import org.lwjgl.openal.AL10;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Ported from src/SexyAppFramework/sound/SDLSoundInstance.cpp:25-275 (and SDLSoundInstance.h:53-94
 * for the member layout).
 *
 * <p>One OpenAL source of the manager's 32-voice pool, replacing one SDL_mixer channel. The voice is
 * owned by the manager and handed out by {@code OpenAlSoundManager.getSoundInstance(id)}, exactly
 * like the upstream {@code SDLSoundInstance} is stored in {@code mPlayingSounds[channel]}.
 *
 * <p>Load-bearing upstream behaviours reproduced here:
 * <ul>
 *   <li>{@code play()} always restarts (it calls {@code stop()} first), sets
 *       {@code mHasPlayed}/{@code mAutoRelease} <em>before</em> the buffer check, and ignores
 *       {@code autoRelease} unless {@link #isReleased()} is polled by the manager.</li>
 *   <li>{@code stop()} clears {@code mAutoRelease}; {@code stopAllSounds()} therefore saves and
 *       restores it (SDLSoundManager.cpp:436-447).</li>
 *   <li>{@code isReleased()} is the voice-GC predicate: an auto-release voice releases itself once it
 *       stopped playing.</li>
 *   <li>{@code adjustPitch()} sets a linear playback-rate multiplier
 *       {@code pow(2^(1/12), steps)} (upstream {@code AdjustPitch}), mapped to {@code AL_PITCH}; the
 *       value is read live, like the upstream mixer effect callback.</li>
 *   <li>Pan is stored but never applied: upstream {@code RehupPan()} is an empty body
 *       (SDLSoundInstance.cpp:65-67) and no caller in {@code src/Lawn/} ever passes a pan.</li>
 * </ul>
 *
 * <p>PORT-NOTE: the gain register lives on the shared per-sound buffer
 * ({@code OpenAlSoundManager.SoundBuffer.mChunkVolume}, upstream {@code Mix_Chunk::volume}), not on
 * the voice, so {@code RehupVolume()} affects every voice currently playing that same sound — the
 * upstream behaviour, since {@code Mix_VolumeChunk} writes the chunk. The mixer's global
 * {@code /128} factor is carried by the OpenAL listener gain
 * ({@code OpenAlDevice.setMasterGain}); {@code AL_GAIN} of a source is therefore
 * {@code chunkVolume / 128}.
 */
public final class OpenAlSoundInstance implements SoundInstance {

    private final OpenAlSoundManager mSoundManagerP;
    private final OpenAlSoundManager.SoundBuffer mSoundBuffer;

    /** OpenAL source name; the reserved channel slot handed in by the manager. */
    private final int mSource;

    /** Upstream {@code mReservedChannel} (SDLSoundInstance.cpp:230-231). */
    final int mReservedChannel;

    boolean mAutoRelease;
    boolean mHasPlayed;
    boolean mReleased;

    /** Upstream mixer channel; equals the reserved channel while playing, else -1. */
    int mChannel;

    int mBasePan;
    double mBaseVolume;

    int mPan;
    double mVolume;
    float mPitch;

    int mDemoEndUpdateCount;
    boolean mDemoLooping;

    /**
     * Upstream {@code SDLSoundInstance::SDLSoundInstance} (SDLSoundInstance.cpp:25-56): fields are
     * initialized to their defaults and {@code RehupVolume()} runs with the chunk already attached.
     */
    OpenAlSoundInstance(OpenAlSoundManager theSoundManager, OpenAlSoundManager.SoundBuffer theSourceSound,
            int theReservedChannel) {
        mSoundManagerP = theSoundManager;
        mSoundBuffer = theSourceSound;
        mReservedChannel = theReservedChannel;
        mReleased = false;
        mAutoRelease = false;
        mHasPlayed = false;
        mChannel = -1;

        mBaseVolume = 1.0;
        mBasePan = 0;

        mVolume = 1.0;
        mPan = 0;
        mPitch = 1.0f;

        mDemoEndUpdateCount = 0;
        mDemoLooping = false;

        mSource = AL10.alGenSources();
        AL10.alSourcef(mSource, AL10.AL_PITCH, mPitch);
        rehupVolume();
    }

    /** Upstream {@code SDLSoundInstance::~SDLSoundInstance()} → {@code Stop()} plus the source teardown. */
    void close() {
        stop();
        mReleased = true;
        if (mSource != 0) {
            AL10.alSourcei(mSource, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(mSource);
        }
    }

    /** Upstream {@code SDLSoundInstance::RehupVolume()} (SDLSoundInstance.cpp:60-63). */
    void rehupVolume() {
        int previous = mSoundBuffer.mChunkVolume;
        int next = AudioGain.chunkVolume(mBaseVolume, mVolume, mSoundManagerP.mMasterVolume, previous);
        if (next != previous) {
            mSoundBuffer.mChunkVolume = next;
            mSoundManagerP.rehupChunkVolume(mSoundBuffer);
        }
    }

    /** True when this voice plays the given shared sound buffer. */
    boolean usesBuffer(OpenAlSoundManager.SoundBuffer theBuffer) {
        return mSoundBuffer == theBuffer;
    }

    /** Writes the shared chunk register value to this voice's OpenAL gain. */
    void applyGain() {
        AL10.alSourcef(mSource, AL10.AL_GAIN, AudioGain.sourceGain(mSoundBuffer.mChunkVolume));
    }

    @Override
    public void release() {
        stop();
        mReleased = true;
    }

    @Override
    public void setBaseVolume(double theBaseVolume) {
        mBaseVolume = theBaseVolume;
        rehupVolume();
    }

    @Override
    public void setBasePan(int theBasePan) {
        mBasePan = theBasePan;
        // Upstream RehupPan() is empty (SDLSoundInstance.cpp:65-67); pan has no audible effect.
    }

    @Override
    public void adjustPitch(double theNumSteps) {
        mPitch = AudioGain.pitchFromSemitones(theNumSteps);
        // The upstream pitch effect reads the live value on every audio callback.
        AL10.alSourcef(mSource, AL10.AL_PITCH, mPitch);
    }

    @Override
    public void setVolume(double theVolume) {
        mVolume = theVolume;
        rehupVolume();
    }

    @Override
    public void setPan(int thePosition) {
        mPan = thePosition;
        // Upstream RehupPan() is empty (SDLSoundInstance.cpp:65-67); pan has no audible effect.
    }

    @Override
    public boolean play(boolean looping, boolean autoRelease) {
        stop();

        mHasPlayed = true;
        mAutoRelease = autoRelease;

        if (mSoundManagerP.isInDemoMode()) {
            // Demo sessions derive the playing state from game ticks, not the audio clock.
            mDemoLooping = looping;
            mDemoEndUpdateCount = mSoundManagerP.demoUpdateCount()
                    + (int) (durationMs() / (10.0 * mPitch)) + 1;
        }

        if (!hasBuffer()) {
            return false;
        }

        mChannel = mReservedChannel;
        AL10.alSourcei(mSource, AL10.AL_BUFFER, mSoundBuffer.mBuffer);
        AL10.alSourcei(mSource, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSourcef(mSource, AL10.AL_PITCH, mPitch);
        applyGain();
        AL10.alSourcePlay(mSource);
        return true;
    }

    @Override
    public void stop() {
        mDemoEndUpdateCount = 0;
        if (mChannel >= 0 && mChannel < SoundManager.MAX_CHANNELS) {
            AL10.alSourceStop(mSource);
            AL10.alSourcei(mSource, AL10.AL_BUFFER, 0);
            mChannel = -1;
        }
        mAutoRelease = false;
    }

    @Override
    public boolean isPlaying() {
        if (mSoundManagerP.isInDemoMode()) {
            if (!hasBuffer() || !mHasPlayed || mDemoEndUpdateCount == 0) {
                return false;
            }
            if (mDemoLooping) {
                return true;
            }
            return mSoundManagerP.demoUpdateCount() < mDemoEndUpdateCount;
        }
        if (!hasBuffer() || !mHasPlayed || mChannel < 0 || mChannel >= SoundManager.MAX_CHANNELS) {
            return false;
        }
        return AL10.alGetSourcei(mSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    @Override
    public boolean isReleased() {
        if (!mReleased && mAutoRelease && mHasPlayed && !isPlaying()) {
            release();
        }
        return mReleased;
    }

    @Override
    public double getVolume() {
        return mVolume;
    }

    /** Upstream {@code GetChunkDurationMS} (SDLSoundInstance.cpp:76-82) for this buffer's own format. */
    private int durationMs() {
        if (!hasBuffer() || mSoundBuffer.mSampleRate <= 0) {
            return 0;
        }
        return mSoundBuffer.mFrames * 1000 / mSoundBuffer.mSampleRate;
    }

    private boolean hasBuffer() {
        return mSoundBuffer != null && !mSoundBuffer.mReleased && mSoundBuffer.mBuffer != 0;
    }
}
