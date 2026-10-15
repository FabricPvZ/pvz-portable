package cn.enaium.fabricpvz.portable.sexy.sound;

/**
 * Ported from src/SexyAppFramework/sound/SoundInstance.h:33 (class).
 *
 * <p>Abstract single voice of a sound-effect. This port is a lean reimplementation, matching the
 * upstream header: the stock PopCap members {@code mId}, {@code mVolumeAdd}, {@code mFadeVolume},
 * {@code mFadeRate}, {@code mIsReleaseVoiceConflict}, {@code mLastGetVolume} do not exist here,
 * and there is no per-voice fade.
 *
 * <p>Pan handling is inherited from the stock framework (the parameter is documented as
 * "hundredth dB from left to right"), but the upstream backends ignore it: both
 * {@code SDLSoundInstance::RehupPan()} and the pan branch of the OpenAL port are empty. Pan is
 * stored and has no audible effect, exactly like upstream.
 */
public interface SoundInstance {

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:37. */
    void release();

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:39. */
    void setBaseVolume(double theBaseVolume);

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:40. */
    void setBasePan(int theBasePan);

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:42. */
    void adjustPitch(double theNumSteps);

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:44. */
    void setVolume(double theVolume);

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:45. */
    void setPan(int thePosition);

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:47. */
    boolean play(boolean looping, boolean autoRelease);

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:48. */
    void stop();

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:49. */
    boolean isPlaying();

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:50. */
    boolean isReleased();

    /** Ported from src/SexyAppFramework/sound/SoundInstance.h:51. */
    double getVolume();
}
