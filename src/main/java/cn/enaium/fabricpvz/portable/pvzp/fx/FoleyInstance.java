package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;

/**
 * Ported from src/PvzpLib/PvzpFoley.h:184 (class FoleyInstance).
 *
 * <p>{@code mStartTime} is the upstream {@code uint mStartTime} (the app update counter at play
 * time); it is a {@code long} here so it composes with any width of {@code SexyAppBase.mUpdateCount}.
 */
public class FoleyInstance {

    public SoundInstance mInstance;
    public int mRefCount;
    public boolean mPaused;
    public long mStartTime;
    public int mPauseOffset;

    /** Ported from src/PvzpLib/PvzpFoley.cpp:135. */
    public FoleyInstance() {
        mInstance = null;
        mRefCount = 0;
        mPaused = false;
        mStartTime = 0;
        mPauseOffset = 0;
    }
}
