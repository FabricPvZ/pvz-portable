/*
 * Ported from src/ConstEnums.h:257-267.
 *
 * Upstream enum DamageRangeFlags (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class DamageRangeFlags {
    public static final int DAMAGES_GROUND = 0;
    public static final int DAMAGES_FLYING = 1;
    public static final int DAMAGES_SUBMERGED = 2;
    public static final int DAMAGES_DOG = 3;
    public static final int DAMAGES_OFF_GROUND = 4;
    public static final int DAMAGES_DYING = 5;
    public static final int DAMAGES_UNDERGROUND = 6;
    public static final int DAMAGES_ONLY_MINDCONTROLLED = 7;

    private DamageRangeFlags() {
    }
}
