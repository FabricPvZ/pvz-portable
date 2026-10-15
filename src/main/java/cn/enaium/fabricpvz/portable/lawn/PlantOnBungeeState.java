/*
 * Ported from src/Lawn/Plant.h:42-47.
 *
 * Upstream enum PlantOnBungeeState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantOnBungeeState {
    public static final int NOT_ON_BUNGEE = 0;
    public static final int GETTING_GRABBED_BY_BUNGEE = 1;
    public static final int RISING_WITH_BUNGEE = 2;

    private PlantOnBungeeState() {
    }
}
