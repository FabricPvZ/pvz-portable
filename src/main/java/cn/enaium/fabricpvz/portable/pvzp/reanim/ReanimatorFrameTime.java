package cn.enaium.fabricpvz.portable.pvzp.reanim;

/**
 * Ported from src/PvzpLib/Reanimator.h:138-144 (class ReanimatorFrameTime).
 *
 * <p>The current playback position of an animation: the fraction elapsed between the two
 * integer frames the animation sits between.
 */
public class ReanimatorFrameTime {
    public float mFraction;
    public int mAnimFrameBeforeInt;
    public int mAnimFrameAfterInt;
}
