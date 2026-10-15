/*
 * Ported from src/ConstEnums.h:735-747.
 *
 * Upstream enum PlantPriority (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantPriority {
    public static final int TOPPLANT_EATING_ORDER = 0;
    public static final int TOPPLANT_DIGGING_ORDER = 1;
    public static final int TOPPLANT_BUNGEE_ORDER = 2;
    public static final int TOPPLANT_CATAPULT_ORDER = 3;
    public static final int TOPPLANT_ZEN_TOOL_ORDER = 4;
    public static final int TOPPLANT_ANY = 5;
    public static final int TOPPLANT_ONLY_NORMAL_POSITION = 6;
    public static final int TOPPLANT_ONLY_FLYING = 7;
    public static final int TOPPLANT_ONLY_PUMPKIN = 8;
    public static final int TOPPLANT_ONLY_UNDER_PLANT = 9;

    private PlantPriority() {
    }
}
