/*
 * Ported from src/Lawn/Challenge.h:252-257 (class ZombieAllowedLevels).
 */
package cn.enaium.fabricpvz.portable.lawn;

/**
 * Ported from src/Lawn/Challenge.h:252-257.
 *
 * <p>Upstream is a plain data holder: a zombie type plus a {@code NUM_LEVELS}-entry 0/1 availability
 * vector indexed by {@code level - 1}. Type and availability stay {@code int} (all upstream enums are
 * int-constant classes in this port, API-CONTRACTS.md §6) and the public fields keep their upstream
 * names.
 */
public class ZombieAllowedLevels {

    /** Upstream {@code mZombieType} ({@code ZombieType}); see {@link ZombieType}. */
    public int mZombieType;

    /** Upstream {@code mAllowedOnLevel[NUM_LEVELS]} ({@code int32_t}); index = {@code level - 1}. */
    public int[] mAllowedOnLevel = new int[GameConstants.NUM_LEVELS];

    public ZombieAllowedLevels() {
    }

    /**
     * Convenience constructor used to build {@link Challenge#gZombieAllowedLevels} from literals.
     *
     * @param theZombieType     the {@link ZombieType} constant (upstream {@code ZOMBIE_*})
     * @param theAllowedOnLevel {@code NUM_LEVELS} 0/1 values, copied into {@link #mAllowedOnLevel}
     */
    public ZombieAllowedLevels(int theZombieType, int[] theAllowedOnLevel) {
        mZombieType = theZombieType;
        System.arraycopy(theAllowedOnLevel, 0, mAllowedOnLevel, 0,
                Math.min(theAllowedOnLevel.length, mAllowedOnLevel.length));
    }
}
