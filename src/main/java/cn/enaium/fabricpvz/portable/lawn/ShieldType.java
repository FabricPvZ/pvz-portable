/*
 * Ported from src/ConstEnums.h:1115-1121.
 *
 * Upstream enum ShieldType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ShieldType {
    public static final int SHIELDTYPE_NONE = 0;
    public static final int SHIELDTYPE_DOOR = 1;
    public static final int SHIELDTYPE_NEWSPAPER = 2;
    public static final int SHIELDTYPE_LADDER = 3;

    private ShieldType() {
    }
}
