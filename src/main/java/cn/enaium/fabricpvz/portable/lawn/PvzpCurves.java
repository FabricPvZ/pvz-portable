/*
 * Ported from src/ConstEnums.h:1165-1181.
 *
 * Upstream enum PvzpCurves (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PvzpCurves {
    public static final int CURVE_CONSTANT = 0;
    public static final int CURVE_LINEAR = 1;
    public static final int CURVE_EASE_IN = 2;
    public static final int CURVE_EASE_OUT = 3;
    public static final int CURVE_EASE_IN_OUT = 4;
    public static final int CURVE_EASE_IN_OUT_WEAK = 5;
    public static final int CURVE_FAST_IN_OUT = 6;
    public static final int CURVE_FAST_IN_OUT_WEAK = 7;
    public static final int CURVE_WEAK_FAST_IN_OUT = 8;
    public static final int CURVE_BOUNCE = 9;
    public static final int CURVE_BOUNCE_FAST_MIDDLE = 10;
    public static final int CURVE_BOUNCE_SLOW_MIDDLE = 11;
    public static final int CURVE_SIN_WAVE = 12;
    public static final int CURVE_EASE_SIN_WAVE = 13;

    private PvzpCurves() {
    }
}
