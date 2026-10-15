/*
 * Ported from src/Lawn/Plant.h:30-34.
 *
 * Upstream enum PlantSubClass (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantSubClass {
    public static final int SUBCLASS_NORMAL = 0;
    public static final int SUBCLASS_SHOOTER = 1;

    private PlantSubClass() {
    }
}
