package cn.enaium.fabricpvz.portable.pvzp.audio;

import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.MusicInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Facade over the port's audio back end: one {@link OpenAlDevice} shared by an
 * {@link OpenAlSoundManager} and an {@link OpenAlMusicInterface}.
 *
 * <p>Ported from src/SexyAppFramework/SexyAppBase.cpp:3338-3344, :3489-3496 (audio object
 * lifetime) and :1652-1660 (per-frame music update).
 *
 * <p>Upstream has no such object — {@code SexyAppBase} creates an {@code SDLSoundManager} and the
 * music interface returned by {@code CreateMusicInterface()} (src/SexyAppFramework/SexyAppBase.cpp:3338-3344,
 * :3489-3496) and calls {@code mMusicInterface->Update()} from the frame loop
 * (SexyAppBase.cpp:1659). The facade keeps that lifetime in one place so a single OpenAL context
 * carries both the sound effects and the music (the SoundManager's master volume is applied to the
 * device's listener gain, matching SDL_mixer's mixer-wide master volume).
 */
public final class AudioEngine implements AutoCloseable {

    private final OpenAlDevice mDevice;
    private final OpenAlSoundManager mSoundManager;
    private final OpenAlMusicInterface mMusicInterface;

    public AudioEngine() {
        this(PakInterface.getInstance());
    }

    public AudioEngine(PakInterface pakInterface) {
        mDevice = new OpenAlDevice();
        mSoundManager = new OpenAlSoundManager(pakInterface, mDevice);
        mMusicInterface = new OpenAlMusicInterface(pakInterface, mDevice);
    }

    /** The OpenAL device; {@link OpenAlDevice#available()} is false when no audio device opened. */
    public OpenAlDevice device() {
        return mDevice;
    }

    /** The sound-effect manager (upstream {@code SexyAppBase::mSoundManager}). */
    public SoundManager soundManager() {
        return mSoundManager;
    }

    /** The music interface (upstream {@code SexyAppBase::mMusicInterface}). */
    public MusicInterface musicInterface() {
        return mMusicInterface;
    }

    /** True when an audio device could be opened. */
    public boolean initialized() {
        return mDevice.available();
    }

    /**
     * Per-frame pump: drives the music fade integrator, exactly like
     * {@code SexyAppBase::UpdateFrames()} calling {@code mMusicInterface->Update()}
     * (src/SexyAppFramework/SexyAppBase.cpp:1659).
     */
    public void update() {
        mMusicInterface.update();
    }

    @Override
    public void close() {
        mMusicInterface.close();
        mSoundManager.close();
        mDevice.close();
    }
}
