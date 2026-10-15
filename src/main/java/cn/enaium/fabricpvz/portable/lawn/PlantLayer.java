/*
 * Ported from src/Lawn/Plant.h:100-108.
 *
 * Upstream enum PLANT_LAYER (int32_t); represented as public static final int constants.
 * Declared upstream but not referenced by Plant.cpp (legacy).
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantLayer {
    public static final int PLANT_LAYER_BELOW = -1;
    public static final int PLANT_LAYER_MAIN = 0;
    public static final int PLANT_LAYER_REANIM = 1;
    public static final int PLANT_LAYER_REANIM_HEAD = 2;
    public static final int PLANT_LAYER_REANIM_BLINK = 3;
    public static final int PLANT_LAYER_ON_TOP = 4;
    public static final int NUM_PLANT_LAYERS = 5;

    private PlantLayer() {
    }
}
