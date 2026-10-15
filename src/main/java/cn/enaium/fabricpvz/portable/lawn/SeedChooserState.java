/*
 * Ported from src/ConstEnums.h:1030-1034.
 *
 * Upstream enum SeedChooserState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class SeedChooserState {
    public static final int CHOOSE_NORMAL = 0;
    public static final int CHOOSE_VIEW_LAWN = 1;

    private SeedChooserState() {
    }
}
