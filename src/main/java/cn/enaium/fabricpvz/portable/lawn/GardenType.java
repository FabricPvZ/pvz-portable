/*
 * Ported from src/ConstEnums.h:492-498.
 *
 * Upstream enum GardenType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class GardenType {
    public static final int GARDEN_MAIN = 0;
    public static final int GARDEN_MUSHROOM = 1;
    public static final int GARDEN_WHEELBARROW = 2;
    public static final int GARDEN_AQUARIUM = 3;

    private GardenType() {
    }
}
