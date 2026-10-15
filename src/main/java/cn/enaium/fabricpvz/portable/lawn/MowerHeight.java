/*
 * Ported from src/ConstEnums.h:606-612.
 *
 * Upstream enum MowerHeight (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class MowerHeight {
    public static final int MOWER_HEIGHT_LAND = 0;
    public static final int MOWER_HEIGHT_DOWN_TO_POOL = 1;
    public static final int MOWER_HEIGHT_IN_POOL = 2;
    public static final int MOWER_HEIGHT_UP_TO_LAND = 3;

    private MowerHeight() {
    }
}
