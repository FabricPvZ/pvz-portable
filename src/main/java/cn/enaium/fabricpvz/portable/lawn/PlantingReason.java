/*
 * Ported from src/ConstEnums.h:748-764.
 *
 * Upstream enum PlantingReason (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantingReason {
    public static final int PLANTING_OK = 0;
    public static final int PLANTING_NOT_HERE = 1;
    public static final int PLANTING_ONLY_ON_GRAVES = 2;
    public static final int PLANTING_ONLY_IN_POOL = 3;
    public static final int PLANTING_ONLY_ON_GROUND = 4;
    public static final int PLANTING_NEEDS_POT = 5;
    public static final int PLANTING_NOT_ON_ART = 6;
    public static final int PLANTING_NOT_PASSED_LINE = 7;
    public static final int PLANTING_NEEDS_UPGRADE = 8;
    public static final int PLANTING_NOT_ON_GRAVE = 9;
    public static final int PLANTING_NOT_ON_CRATER = 10;
    public static final int PLANTING_NOT_ON_WATER = 11;
    public static final int PLANTING_NEEDS_GROUND = 12;
    public static final int PLANTING_NEEDS_SLEEPING = 13;

    private PlantingReason() {
    }
}
