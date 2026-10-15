/*
 * Ported from src/ConstEnums.h:333-341.
 *
 * Upstream enum DrawStringJustification (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class DrawStringJustification {
    public static final int DS_ALIGN_LEFT = 0;
    public static final int DS_ALIGN_RIGHT = 1;
    public static final int DS_ALIGN_CENTER = 2;
    public static final int DS_ALIGN_LEFT_VERTICAL_MIDDLE = 3;
    public static final int DS_ALIGN_RIGHT_VERTICAL_MIDDLE = 4;
    public static final int DS_ALIGN_CENTER_VERTICAL_MIDDLE = 5;

    private DrawStringJustification() {
    }
}
