/*
 * Ported from src/ConstEnums.h:1035-1114.
 *
 * Upstream enum SeedType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class SeedType {
    public static final int SEED_PEASHOOTER = 0;
    public static final int SEED_SUNFLOWER = 1;
    public static final int SEED_CHERRYBOMB = 2;
    public static final int SEED_WALLNUT = 3;
    public static final int SEED_POTATOMINE = 4;
    public static final int SEED_SNOWPEA = 5;
    public static final int SEED_CHOMPER = 6;
    public static final int SEED_REPEATER = 7;
    public static final int SEED_PUFFSHROOM = 8;
    public static final int SEED_SUNSHROOM = 9;
    public static final int SEED_FUMESHROOM = 10;
    public static final int SEED_GRAVEBUSTER = 11;
    public static final int SEED_HYPNOSHROOM = 12;
    public static final int SEED_SCAREDYSHROOM = 13;
    public static final int SEED_ICESHROOM = 14;
    public static final int SEED_DOOMSHROOM = 15;
    public static final int SEED_LILYPAD = 16;
    public static final int SEED_SQUASH = 17;
    public static final int SEED_THREEPEATER = 18;
    public static final int SEED_TANGLEKELP = 19;
    public static final int SEED_JALAPENO = 20;
    public static final int SEED_SPIKEWEED = 21;
    public static final int SEED_TORCHWOOD = 22;
    public static final int SEED_TALLNUT = 23;
    public static final int SEED_SEASHROOM = 24;
    public static final int SEED_PLANTERN = 25;
    public static final int SEED_CACTUS = 26;
    public static final int SEED_BLOVER = 27;
    public static final int SEED_SPLITPEA = 28;
    public static final int SEED_STARFRUIT = 29;
    public static final int SEED_PUMPKINSHELL = 30;
    public static final int SEED_MAGNETSHROOM = 31;
    public static final int SEED_CABBAGEPULT = 32;
    public static final int SEED_FLOWERPOT = 33;
    public static final int SEED_KERNELPULT = 34;
    public static final int SEED_INSTANT_COFFEE = 35;
    public static final int SEED_GARLIC = 36;
    public static final int SEED_UMBRELLA = 37;
    public static final int SEED_MARIGOLD = 38;
    public static final int SEED_MELONPULT = 39;
    public static final int SEED_GATLINGPEA = 40;
    public static final int SEED_TWINSUNFLOWER = 41;
    public static final int SEED_GLOOMSHROOM = 42;
    public static final int SEED_CATTAIL = 43;
    public static final int SEED_WINTERMELON = 44;
    public static final int SEED_GOLD_MAGNET = 45;
    public static final int SEED_SPIKEROCK = 46;
    public static final int SEED_COBCANNON = 47;
    public static final int SEED_IMITATER = 48;
    public static final int SEED_EXPLODE_O_NUT = 49;
    public static final int SEED_GIANT_WALLNUT = 50;
    public static final int SEED_SPROUT = 51;
    public static final int SEED_LEFTPEATER = 52;
    public static final int NUM_SEED_TYPES = 53;
    public static final int SEED_BEGHOULED_BUTTON_SHUFFLE = 54;
    public static final int SEED_BEGHOULED_BUTTON_CRATER = 55;
    public static final int SEED_SLOT_MACHINE_SUN = 56;
    public static final int SEED_SLOT_MACHINE_DIAMOND = 57;
    public static final int SEED_ZOMBIQUARIUM_SNORKLE = 58;
    public static final int SEED_ZOMBIQUARIUM_TROPHY = 59;
    public static final int SEED_ZOMBIE_NORMAL = 60;
    public static final int SEED_ZOMBIE_TRAFFIC_CONE = 61;
    public static final int SEED_ZOMBIE_POLEVAULTER = 62;
    public static final int SEED_ZOMBIE_PAIL = 63;
    public static final int SEED_ZOMBIE_LADDER = 64;
    public static final int SEED_ZOMBIE_DIGGER = 65;
    public static final int SEED_ZOMBIE_BUNGEE = 66;
    public static final int SEED_ZOMBIE_FOOTBALL = 67;
    public static final int SEED_ZOMBIE_BALLOON = 68;
    public static final int SEED_ZOMBIE_SCREEN_DOOR = 69;
    public static final int SEED_ZOMBONI = 70;
    public static final int SEED_ZOMBIE_POGO = 71;
    public static final int SEED_ZOMBIE_DANCER = 72;
    public static final int SEED_ZOMBIE_GARGANTUAR = 73;
    public static final int SEED_ZOMBIE_IMP = 74;
    public static final int NUM_SEEDS_IN_CHOOSER = 49;
    public static final int SEED_NONE = -1;

    /**
     * First seed type mods may allocate.
     *
     * <p>Everything below it is upstream: {@code 0..NUM_SEED_TYPES-1} are the plants of the almanac
     * and {@code NUM_SEED_TYPES..74} the mini-game seeds (beghouled buttons, slot machine,
     * zombiquarium, I, Zombie). Ids from here on are handed out by
     * {@link cn.enaium.fabricpvz.portable.lawn.content.PlantTypeRegistry} and are never used by the
     * upstream tables or by {@code switch} statements, so mod content can never collide with them.
     */
    public static final int CUSTOM_SEED_FIRST = 100;

    /**
     * Size of every seed-type-indexed table (upstream rows plus the mod window).
     *
     * <p>Tables stay index-aligned with {@link #CUSTOM_SEED_FIRST}: slot {@code n} of a
     * {@code MAX_SEED_TYPES}-wide array holds the data of seed type {@code n}. With an empty
     * {@link cn.enaium.fabricpvz.portable.lawn.content.PlantTypeRegistry} the extra slots are simply
     * never read, so an unmodded game behaves exactly like upstream.
     */
    public static final int MAX_SEED_TYPES = 200;

    private SeedType() {
    }
}
