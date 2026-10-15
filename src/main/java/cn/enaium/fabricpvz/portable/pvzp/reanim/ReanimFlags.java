package cn.enaium.fabricpvz.portable.pvzp.reanim;

/**
 * Ported from src/PvzpLib/Reanimator.h:54-58 (enum ReanimFlags).
 *
 * <p>Per-reanimation-type flags carried by {@link ReanimationParams#mReanimParamFlags}.
 */
public final class ReanimFlags {
    /** Skip the runtime atlas. */
    public static final int REANIM_NO_ATLAS = 0;
    /** Fast draw path when not 3D-accelerated. */
    public static final int REANIM_FAST_DRAW_IN_SW_MODE = 1;

    private ReanimFlags() {
    }
}
