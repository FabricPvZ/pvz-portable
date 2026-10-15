/*
 * Ported from src/ConstEnums.h:1221-1226.
 *
 * Upstream enum UnlockingState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class UnlockingState {
    public static final int UNLOCK_OFF = 0;
    public static final int UNLOCK_SHAKING = 1;
    public static final int UNLOCK_FADING = 2;

    private UnlockingState() {
    }
}
