/*
 * Ported from src/Lawn/Plant.h:110-117.
 *
 * Upstream enum PLANT_ORDER (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantOrder {
    public static final int PLANT_ORDER_LILYPAD = 0;
    public static final int PLANT_ORDER_NORMAL = 1;
    public static final int PLANT_ORDER_PUMPKIN = 2;
    public static final int PLANT_ORDER_FLYER = 3;
    public static final int PLANT_ORDER_CHERRYBOMB = 4;

    private PlantOrder() {
    }
}
