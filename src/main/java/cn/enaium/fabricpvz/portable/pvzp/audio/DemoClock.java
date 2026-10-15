package cn.enaium.fabricpvz.portable.pvzp.audio;

/**
 * Ported from src/SexyAppFramework/SexyAppBase.h:401 ({@code IsInDemoMode()}) and the
 * {@code mUpdateCount} tick it pairs with (src/SexyAppFramework/sound/SDLSoundInstance.cpp:206-273).
 *
 * <p>Upstream derives sound playback state from the deterministic game tick while recording or
 * replaying a demo, because the audio clock is not reproducible across machines: a voice reports
 * "playing" while the tick counter has not passed the tick computed from the sample duration.
 *
 * <p>The demo/replay system is not part of this slice, so the sound manager takes this clock as an
 * optional dependency and defaults to {@link #NEVER}, which reproduces the normal (non-demo) path.
 */
public interface DemoClock {

    /** Ported from src/SexyAppFramework/SexyAppBase.h:401. */
    boolean isInDemoMode();

    /** Ported from {@code SexyAppBase::mUpdateCount} (0.1 s units). */
    int updateCount();

    /** Not in demo mode: the upstream default when neither a recording nor a playback buffer exists. */
    DemoClock NEVER = new DemoClock() {
        @Override
        public boolean isInDemoMode() {
            return false;
        }

        @Override
        public int updateCount() {
            return 0;
        }
    };
}
