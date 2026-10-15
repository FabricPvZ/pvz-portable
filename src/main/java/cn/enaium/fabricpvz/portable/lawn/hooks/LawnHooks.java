package cn.enaium.fabricpvz.portable.lawn.hooks;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Extension hooks fired by the game at the sites documented in
 * {@code docs/research/03-board-core.md §7.4}.
 *
 * <p>This is the game-side half of the modding API: {@code fabric-api} registers an
 * implementation that forwards every call to its Fabric-style {@code Event} objects, so mods see a
 * Fabric-like API while the game itself stays free of any dependency on the mod loader.
 *
 * <p>All methods have no-op defaults: an unmodified game behaves exactly like upstream. Methods
 * returning a value receive the current value and return the (possibly modified) value used by the
 * game, mirroring how a Fabric listener can rewrite damage amounts or planting reasons.
 */
public interface LawnHooks {

    /** End of {@code Board::InitLevel} (Board.cpp:1323). */
    default void onLevelStart(Board board, int level, int gameMode, int background) {
    }

    /** {@code Board::FadeOutLevel} / {@code LawnApp::CheckForGameEnd}. */
    default void onLevelEnd(Board board, int result) {
    }

    /**
     * {@code Board::SpawnZombieWave} (Board.cpp:4895).
     *
     * @return true to cancel the wave spawn (Fabric-style cancellable event)
     */
    default boolean onWaveStart(Board board, int waveIndex, int[] zombieTypes) {
        return false;
    }

    /** {@code Board::AddZombieInRow} (Board.cpp:2609); return true to cancel the spawn. */
    default boolean onZombieSpawn(Board board, int zombieType, int row, boolean fromWave) {
        return false;
    }

    /** {@code Zombie::Die}/{@code DieNoLoot}. */
    default void onZombieDeath(Board board, Zombie zombie) {
    }

    /** {@code Zombie::TakeDamage}; returns the damage amount to apply. */
    default int onZombieDamage(Board board, Zombie zombie, int amount, int damageFlags) {
        return amount;
    }

    /** {@code Board::AddPlant} (Board.cpp:2071). */
    default void onPlantPlaced(Board board, Plant plant, int gridX, int gridY) {
    }

    /** {@code Plant::Die}. */
    default void onPlantRemoved(Board board, Plant plant, int cause) {
    }

    /** Plant damage entry point; returns the damage amount to apply. */
    default int onPlantDamage(Board board, Plant plant, int amount) {
        return amount;
    }

    /** {@code Board::AddSunMoney} (Board.cpp:8433); returns the value actually credited. */
    default int onSunCollected(Board board, int amount, int source) {
        return amount;
    }

    /** {@code Board::CanPlantAt} (Board.cpp:2688); returns the (possibly overridden) planting reason. */
    default int onPlantingValidated(Board board, int gridX, int gridY, int seedType, int reason) {
        return reason;
    }

    /** {@code Board::UpdateGame} right after {@code mMainCounter++} (Board.cpp:5667). */
    default void onTick(Board board, int tick) {
    }

    /** {@code Board::Draw} before the object passes (Board.cpp:7438). */
    default void onPreDraw(Board board, Graphics graphics) {
    }

    /** {@code Board::Draw} after the object passes. */
    default void onPostDraw(Board board, Graphics graphics) {
    }
}
