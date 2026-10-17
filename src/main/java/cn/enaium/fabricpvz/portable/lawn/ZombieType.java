/*
 * Ported from src/ConstEnums.h:1340-1379.
 *
 * Upstream enum ZombieType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ZombieType {
    public static final int ZOMBIE_INVALID = -1;
    public static final int ZOMBIE_NORMAL = 0;
    public static final int ZOMBIE_FLAG = 1;
    public static final int ZOMBIE_TRAFFIC_CONE = 2;
    public static final int ZOMBIE_POLEVAULTER = 3;
    public static final int ZOMBIE_PAIL = 4;
    public static final int ZOMBIE_NEWSPAPER = 5;
    public static final int ZOMBIE_DOOR = 6;
    public static final int ZOMBIE_FOOTBALL = 7;
    public static final int ZOMBIE_DANCER = 8;
    public static final int ZOMBIE_BACKUP_DANCER = 9;
    public static final int ZOMBIE_DUCKY_TUBE = 10;
    public static final int ZOMBIE_SNORKEL = 11;
    public static final int ZOMBIE_ZAMBONI = 12;
    public static final int ZOMBIE_BOBSLED = 13;
    public static final int ZOMBIE_DOLPHIN_RIDER = 14;
    public static final int ZOMBIE_JACK_IN_THE_BOX = 15;
    public static final int ZOMBIE_BALLOON = 16;
    public static final int ZOMBIE_DIGGER = 17;
    public static final int ZOMBIE_POGO = 18;
    public static final int ZOMBIE_YETI = 19;
    public static final int ZOMBIE_BUNGEE = 20;
    public static final int ZOMBIE_LADDER = 21;
    public static final int ZOMBIE_CATAPULT = 22;
    public static final int ZOMBIE_GARGANTUAR = 23;
    public static final int ZOMBIE_IMP = 24;
    public static final int ZOMBIE_BOSS = 25;
    public static final int ZOMBIE_PEA_HEAD = 26;
    public static final int ZOMBIE_WALLNUT_HEAD = 27;
    public static final int ZOMBIE_JALAPENO_HEAD = 28;
    public static final int ZOMBIE_GATLING_HEAD = 29;
    public static final int ZOMBIE_SQUASH_HEAD = 30;
    public static final int ZOMBIE_TALLNUT_HEAD = 31;
    public static final int ZOMBIE_REDEYE_GARGANTUAR = 32;
    public static final int NUM_ZOMBIE_TYPES = 33;
    public static final int ZOMBIE_CACHED_POLEVAULTER_WITH_POLE = 34;
    public static final int NUM_CACHED_ZOMBIE_TYPES = 35;

    /**
     * Size of the zombie-type id space, i.e. the width of every table a zombie type indexes.
     *
     * <p>Rows stay index-aligned: slot {@code n} holds the data of zombie type {@code n}. {@code 100}
     * is the width upstream already reserves for {@code Board::mZombieAllowed} (Board.h), so every
     * other per-zombie table is as wide as this constant. The zombie types the retail game ships are
     * the ones below {@link #NUM_ZOMBIE_TYPES} (plus the two cached pole-vaulter rows); a type whose
     * row in the definition table is still empty ({@code null}) is one nobody has defined, see
     * {@code Zombie#gZombieDefs}.
     */
    public static final int MAX_ZOMBIE_TYPES = 100;

    private ZombieType() {
    }
}
