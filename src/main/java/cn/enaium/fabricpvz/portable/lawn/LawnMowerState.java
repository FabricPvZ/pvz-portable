/*
 * Ported from src/ConstEnums.h:569-575.
 *
 * Upstream enum LawnMowerState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class LawnMowerState {
    public static final int MOWER_ROLLING_IN = 0;
    public static final int MOWER_READY = 1;
    public static final int MOWER_TRIGGERED = 2;
    public static final int MOWER_SQUISHED = 3;

    private LawnMowerState() {
    }
}
