/*
 * Ported from src/Lawn/Widget/SeedChooserScreen.h:40-54 (class ChosenSeed).
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

/**
 * A single seed entry of the seed chooser screen: its current screen position, the animation
 * window used while it flies between the chooser and the seed bank, and the selection state.
 *
 * <p>Field names and defaults mirror the upstream pod exactly. In C++ the whole array is
 * {@code memset} to zero before the chooser seeds are initialised, so every field not explicitly
 * set keeps its Java default here too (i.e. a fresh instance equals the zeroed pod).
 */
public class ChosenSeed {
    public int mX;
    public int mY;
    public int mTimeStartMotion;
    public int mTimeEndMotion;
    public int mStartX;
    public int mStartY;
    public int mEndX;
    public int mEndY;
    /** SeedType value. */
    public int mSeedType;
    /** ChosenSeedState value. */
    public int mSeedState;
    public int mSeedIndexInBank;
    public boolean mRefreshing;
    public int mRefreshCounter;
    /** SeedType value the imitater copies, or {@link cn.enaium.fabricpvz.portable.lawn.SeedType#SEED_NONE}. */
    public int mImitaterType;
    public boolean mCrazyDavePicked;
}
