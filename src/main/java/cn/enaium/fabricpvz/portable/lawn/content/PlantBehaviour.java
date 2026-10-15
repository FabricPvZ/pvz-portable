package cn.enaium.fabricpvz.portable.lawn.content;

import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Behaviour callbacks of a mod-registered plant, called by the game from the same sites that drive
 * upstream plants.
 *
 * <p>This is the game-side half of the plant content extension point, exactly like
 * {@link cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks} is for gameplay events: the game knows
 * this interface only, {@code api-plant} implements it by fanning out to Fabric-style events, and
 * a mod therefore never has to touch a game class.
 *
 * <p>Every method has a default that preserves the upstream behaviour of an unmodified plant, so a
 * behaviour implementation only overrides what it actually needs. All callbacks run on the game
 * thread, inside the plant's own update pass.
 */
public interface PlantBehaviour {

    /**
     * Called once per plant tick while the plant is being updated, from {@code Plant::Update}
     * (Plant.cpp:2856).
     *
     * <p>{@code plant.mBoard} and {@code plant.mApp} are guaranteed to be set; the plant may be on
     * the board or in a conveyor/packet preview.
     *
     * @param plant the plant being updated
     */
    default void onUpdate(Plant plant) {
    }

    /**
     * Called right after the plant finished initialising on the board, from
     * {@code Plant::PlantInitialize} (Plant.cpp:111) through {@code Board::AddPlant}
     * (Board.cpp:2071).
     *
     * <p>This is the point where a behaviour may adjust state that depends on the board (for example
     * pick a target or a facing direction). The plant is already placed in the grid.
     *
     * @param plant the newly placed plant
     */
    default void onPlanted(Plant plant) {
    }

    /**
     * Collision rectangle of the plant, replacing {@code Plant::GetPlantRect} (Plant.cpp:5136).
     *
     * @param plant the plant being tested
     * @return the rectangle to use, or {@code null} to keep the upstream rectangle
     */
    default Rect plantRect(Plant plant) {
        return null;
    }

    /**
     * Attack rectangle of the plant, replacing {@code Plant::GetPlantAttackRect}
     * (Plant.cpp:5160).
     *
     * @param plant the plant being tested
     * @param thePlantWeapon {@code PlantWeapon} of the weapon being used
     * @return the rectangle to use, or {@code null} to keep the upstream rectangle
     */
    default Rect plantAttackRect(Plant plant, int thePlantWeapon) {
        return null;
    }
}
