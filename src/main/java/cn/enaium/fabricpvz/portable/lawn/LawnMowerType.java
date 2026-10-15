/*
 * Ported from src/ConstEnums.h:576-583.
 *
 * Upstream enum LawnMowerType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class LawnMowerType {
    public static final int LAWNMOWER_LAWN = 0;
    public static final int LAWNMOWER_POOL = 1;
    public static final int LAWNMOWER_ROOF = 2;
    public static final int LAWNMOWER_SUPER_MOWER = 3;
    public static final int NUM_MOWER_TYPES = 4;

    private LawnMowerType() {
    }
}
