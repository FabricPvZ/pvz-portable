/*
 * Ported from src/ConstEnums.h:1023-1029.
 *
 * Upstream enum ScaryPotType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ScaryPotType {
    public static final int SCARYPOT_NONE = 0;
    public static final int SCARYPOT_SEED = 1;
    public static final int SCARYPOT_ZOMBIE = 2;
    public static final int SCARYPOT_SUN = 3;

    private ScaryPotType() {
    }
}
