/*
 * Ported from src/ConstEnums.h:111-123.
 *
 * Upstream enum BackgroundType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class BackgroundType {
    public static final int BACKGROUND_1_DAY = 0;
    public static final int BACKGROUND_2_NIGHT = 1;
    public static final int BACKGROUND_3_POOL = 2;
    public static final int BACKGROUND_4_FOG = 3;
    public static final int BACKGROUND_5_ROOF = 4;
    public static final int BACKGROUND_6_BOSS = 5;
    public static final int BACKGROUND_MUSHROOM_GARDEN = 6;
    public static final int BACKGROUND_GREENHOUSE = 7;
    public static final int BACKGROUND_ZOMBIQUARIUM = 8;
    public static final int BACKGROUND_TREEOFWISDOM = 9;

    private BackgroundType() {
    }
}
