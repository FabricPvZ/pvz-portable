/*
 * Ported from src/ConstEnums.h:1227-1240.
 *
 * Upstream enum ZombieHeight (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ZombieHeight {
    public static final int HEIGHT_ZOMBIE_NORMAL = 0;
    public static final int HEIGHT_IN_TO_POOL = 1;
    public static final int HEIGHT_OUT_OF_POOL = 2;
    public static final int HEIGHT_DRAGGED_UNDER = 3;
    public static final int HEIGHT_UP_TO_HIGH_GROUND = 4;
    public static final int HEIGHT_DOWN_OFF_HIGH_GROUND = 5;
    public static final int HEIGHT_UP_LADDER = 6;
    public static final int HEIGHT_FALLING = 7;
    public static final int HEIGHT_IN_TO_CHIMNEY = 8;
    public static final int HEIGHT_GETTING_BUNGEE_DROPPED = 9;
    public static final int HEIGHT_ZOMBIQUARIUM = 10;

    private ZombieHeight() {
    }
}
