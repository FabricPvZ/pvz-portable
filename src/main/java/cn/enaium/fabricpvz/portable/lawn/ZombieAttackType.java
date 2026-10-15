/*
 * Ported from src/Lawn/Zombie.h:33-39.
 *
 * Upstream enum ZombieAttackType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ZombieAttackType {
    public static final int ATTACKTYPE_CHEW = 0;
    public static final int ATTACKTYPE_DRIVE_OVER = 1;
    public static final int ATTACKTYPE_VAULT = 2;
    public static final int ATTACKTYPE_LADDER = 3;

    private ZombieAttackType() {
    }
}
