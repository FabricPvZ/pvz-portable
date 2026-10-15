package cn.enaium.fabricpvz.portable.sexy.sound;

/**
 * Ported from src/SexyAppFramework/sound/SoundManager.h:38 (class) and
 * src/SexyAppFramework/sound/SoundManager.h:35-36 (constants).
 *
 * <p>Abstract sound-effect manager. Faithful port of the upstream interface, including the exact
 * method set; C++ {@code intptr_t} sound ids become {@code long}.
 *
 * <p>Naming trap reproduced from upstream: {@link #setVolume(double)} sets the <b>SFX bus</b> gain
 * (upstream {@code SDLSoundManager::mMasterVolume}, driven by {@code SexyAppBase::mSfxVolume}),
 * while {@link #setMasterVolume(double)} drives the <b>SDL_mixer global gain</b>
 * ({@code Mix_MasterVolume}, upstream {@code SDLSoundManager.cpp:425-428}). Both are applied on top
 * of each other; they are two independent knobs.
 *
 * <p>Java has no default arguments and no reference-returning overloads, so every C++ overload is
 * present explicitly and callers must pass the upstream defaults themselves.
 */
public interface SoundManager {

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:35. */
    int MAX_SOURCE_SOUNDS = 256;

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:36. */
    int MAX_CHANNELS = 32;

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:43. */
    boolean initialized();

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:45. */
    boolean loadSound(long theSfxID, String theFilename);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:46. */
    long loadSound(String theFilename);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:47. */
    void releaseSound(long theSfxID);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:49. */
    void setVolume(double theVolume);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:50. */
    boolean setBaseVolume(long theSfxID, double theBaseVolume);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:51. */
    boolean setBasePan(long theSfxID, int theBasePan);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:53. */
    SoundInstance getSoundInstance(long theSfxID);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:55. */
    void releaseSounds();

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:56. */
    void releaseChannels();

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:58. */
    double getMasterVolume();

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:59. */
    void setMasterVolume(double theVolume);

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:61. */
    void flush();

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:62. */
    void stopAllSounds();

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:63. */
    long getFreeSoundId();

    /** Ported from src/SexyAppFramework/sound/SoundManager.h:64. */
    int getNumSounds();
}
