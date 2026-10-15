/*
 * Ported from src/ConstEnums.h:325-332.
 *
 * Upstream enum DebugTextMode (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class DebugTextMode {
    public static final int DEBUG_TEXT_NONE = 0;
    public static final int DEBUG_TEXT_ZOMBIE_SPAWN = 1;
    public static final int DEBUG_TEXT_MUSIC = 2;
    public static final int DEBUG_TEXT_MEMORY = 3;
    public static final int DEBUG_TEXT_COLLISION = 4;

    private DebugTextMode() {
    }
}
