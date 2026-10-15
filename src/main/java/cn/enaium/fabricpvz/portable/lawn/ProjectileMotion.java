/*
 * Ported from src/ConstEnums.h:787-799.
 *
 * Upstream enum ProjectileMotion (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ProjectileMotion {
    public static final int MOTION_STRAIGHT = 0;
    public static final int MOTION_LOBBED = 1;
    public static final int MOTION_THREEPEATER = 2;
    public static final int MOTION_BEE = 3;
    public static final int MOTION_BEE_BACKWARDS = 4;
    public static final int MOTION_PUFF = 5;
    public static final int MOTION_BACKWARDS = 6;
    public static final int MOTION_STAR = 7;
    public static final int MOTION_FLOAT_OVER = 8;
    public static final int MOTION_HOMING = 9;

    private ProjectileMotion() {
    }
}
