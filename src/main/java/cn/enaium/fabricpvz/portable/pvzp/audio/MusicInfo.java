package cn.enaium.fabricpvz.portable.pvzp.audio;

/**
 * Ported from {@code SDLMusicInfo} in src/SexyAppFramework/sound/SDLMusicInterface.h:40-55
 * (defaults from SDLMusicInterface.cpp:29-36) plus the per-frame integrator of
 * {@code SDLMusicInterface::Update()} (SDLMusicInterface.cpp:304-341).
 *
 * <p>The fields are the exact upstream registers: {@code mVolume} is the current stream volume in
 * {@code [0, mVolumeCap]}, {@code mVolumeAdd} the per-{@link #step()} increment (positive for
 * {@code FadeIn}, negative for {@code FadeOut}, exactly zero when no fade is running) and
 * {@code mVolumeCap} the ceiling installed by {@code SetSongMaxVolume}. {@code mStopOnFade} is the
 * upstream {@code noLoop}/{@code stopSong} flag: when the volume reaches zero during a fade the
 * stream is halted.
 *
 * <p>{@link #step()} is the pure transcription of the upstream update body, so the fade tables can
 * be asserted without an audio device; {@code OpenAlMusicInterface.update()} calls it once per frame
 * like {@code SexyAppBase::UpdateFrames} does.
 */
public final class MusicInfo {

    /** Ported from SDLMusicInterface.cpp:29-36. */
    public double mVolume = 0.0;
    public double mVolumeAdd = 0.0;
    public double mVolumeCap = 1.0;
    public boolean mStopOnFade = false;

    /** Outcome of one {@link #step()} call. */
    public enum StepResult {
        /** {@code mVolumeAdd == 0}: upstream does nothing at all for this song. */
        UNCHANGED,
        /** Volume changed: upstream calls {@code Mix_VolumeMusicStream(handle, (int)(mVolume*128))}. */
        APPLY,
        /** Volume reached zero and {@code mStopOnFade} is set: apply, then halt the stream. */
        APPLY_AND_HALT
    }

    /**
     * One iteration of the upstream {@code SDLMusicInterface::Update()} integrator
     * (SDLMusicInterface.cpp:313-336), verbatim including the clamp order.
     */
    public StepResult step() {
        if (mVolumeAdd == 0.0) {
            return StepResult.UNCHANGED;
        }

        mVolume += mVolumeAdd;

        if (mVolume > mVolumeCap) {
            mVolume = mVolumeCap;
            mVolumeAdd = 0.0;
        } else if (mVolume < 0.0) {
            mVolume = 0.0;
            mVolumeAdd = 0.0;

            if (mStopOnFade) {
                return StepResult.APPLY_AND_HALT;
            }
        }

        return StepResult.APPLY;
    }
}
