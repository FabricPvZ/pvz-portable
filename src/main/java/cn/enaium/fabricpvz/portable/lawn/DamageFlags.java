/*
 * Ported from src/ConstEnums.h:248-256.
 *
 * Upstream enum DamageFlags (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class DamageFlags {
    public static final int DAMAGE_BYPASSES_SHIELD = 0;
    public static final int DAMAGE_HITS_SHIELD_AND_BODY = 1;
    public static final int DAMAGE_FREEZE = 2;
    public static final int DAMAGE_DOESNT_CAUSE_FLASH = 3;
    public static final int DAMAGE_DOESNT_LEAVE_BODY = 4;
    public static final int DAMAGE_SPIKE = 5;

    private DamageFlags() {
    }
}
