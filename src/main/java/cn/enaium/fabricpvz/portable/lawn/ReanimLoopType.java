/*
 * Ported from src/ConstEnums.h:966-974.
 *
 * Upstream enum ReanimLoopType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ReanimLoopType {
    public static final int REANIM_LOOP = 0;
    public static final int REANIM_LOOP_FULL_LAST_FRAME = 1;
    public static final int REANIM_PLAY_ONCE = 2;
    public static final int REANIM_PLAY_ONCE_AND_HOLD = 3;
    public static final int REANIM_PLAY_ONCE_FULL_LAST_FRAME = 4;
    public static final int REANIM_PLAY_ONCE_FULL_LAST_FRAME_AND_HOLD = 5;

    private ReanimLoopType() {
    }
}
