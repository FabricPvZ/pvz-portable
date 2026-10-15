/*
 * Ported from src/ConstEnums.h:176-185.
 *
 * Upstream enum CoinMotion (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class CoinMotion {
    public static final int COIN_MOTION_FROM_SKY = 0;
    public static final int COIN_MOTION_FROM_SKY_SLOW = 1;
    public static final int COIN_MOTION_FROM_PLANT = 2;
    public static final int COIN_MOTION_COIN = 3;
    public static final int COIN_MOTION_LAWNMOWER_COIN = 4;
    public static final int COIN_MOTION_FROM_PRESENT = 5;
    public static final int COIN_MOTION_FROM_BOSS = 6;

    private CoinMotion() {
    }
}
