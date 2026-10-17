package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Table-driven verification of the ConstEnums.h enum ports.
 *
 * <p>Each table lists every upstream enumerator name with its expected value;
 * the test fails if a constant is renamed, dropped or changes value.
 */
class EnumTableTest {

    private static Map<String, Integer> table(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    // ZombieType (ConstEnums.h:1340-1379)
    private static final Map<String, Integer> ZOMBIETYPE_EXPECTED = table(
            "ZOMBIE_INVALID", -1,
            "ZOMBIE_NORMAL", 0,
            "ZOMBIE_FLAG", 1,
            "ZOMBIE_TRAFFIC_CONE", 2,
            "ZOMBIE_POLEVAULTER", 3,
            "ZOMBIE_PAIL", 4,
            "ZOMBIE_NEWSPAPER", 5,
            "ZOMBIE_DOOR", 6,
            "ZOMBIE_FOOTBALL", 7,
            "ZOMBIE_DANCER", 8,
            "ZOMBIE_BACKUP_DANCER", 9,
            "ZOMBIE_DUCKY_TUBE", 10,
            "ZOMBIE_SNORKEL", 11,
            "ZOMBIE_ZAMBONI", 12,
            "ZOMBIE_BOBSLED", 13,
            "ZOMBIE_DOLPHIN_RIDER", 14,
            "ZOMBIE_JACK_IN_THE_BOX", 15,
            "ZOMBIE_BALLOON", 16,
            "ZOMBIE_DIGGER", 17,
            "ZOMBIE_POGO", 18,
            "ZOMBIE_YETI", 19,
            "ZOMBIE_BUNGEE", 20,
            "ZOMBIE_LADDER", 21,
            "ZOMBIE_CATAPULT", 22,
            "ZOMBIE_GARGANTUAR", 23,
            "ZOMBIE_IMP", 24,
            "ZOMBIE_BOSS", 25,
            "ZOMBIE_PEA_HEAD", 26,
            "ZOMBIE_WALLNUT_HEAD", 27,
            "ZOMBIE_JALAPENO_HEAD", 28,
            "ZOMBIE_GATLING_HEAD", 29,
            "ZOMBIE_SQUASH_HEAD", 30,
            "ZOMBIE_TALLNUT_HEAD", 31,
            "ZOMBIE_REDEYE_GARGANTUAR", 32,
            "NUM_ZOMBIE_TYPES", 33,
            "ZOMBIE_CACHED_POLEVAULTER_WITH_POLE", 34,
            "NUM_CACHED_ZOMBIE_TYPES", 35,
            // The id space is wider than the retail types: MAX_ZOMBIE_TYPES is the width of every
            // per-zombie table (and of Board::mZombieAllowed upstream), and the rows above
            // NUM_ZOMBIE_TYPES are empty until a definition is written into them.
            "MAX_ZOMBIE_TYPES", 100
    );

    @Test
    void zombieTypeMatchesUpstream() throws Exception {
        assertTable(ZombieType.class, ZOMBIETYPE_EXPECTED);
    }

    // ZombiePhase (ConstEnums.h:1241-1339)
    private static final Map<String, Integer> ZOMBIEPHASE_EXPECTED = table(
            "PHASE_ZOMBIE_NORMAL", 0,
            "PHASE_ZOMBIE_DYING", 1,
            "PHASE_ZOMBIE_BURNED", 2,
            "PHASE_ZOMBIE_MOWERED", 3,
            "PHASE_BUNGEE_DIVING", 4,
            "PHASE_BUNGEE_DIVING_SCREAMING", 5,
            "PHASE_BUNGEE_AT_BOTTOM", 6,
            "PHASE_BUNGEE_GRABBING", 7,
            "PHASE_BUNGEE_RISING", 8,
            "PHASE_BUNGEE_HIT_OUCHY", 9,
            "PHASE_BUNGEE_CUTSCENE", 10,
            "PHASE_POLEVAULTER_PRE_VAULT", 11,
            "PHASE_POLEVAULTER_IN_VAULT", 12,
            "PHASE_POLEVAULTER_POST_VAULT", 13,
            "PHASE_RISING_FROM_GRAVE", 14,
            "PHASE_JACK_IN_THE_BOX_RUNNING", 15,
            "PHASE_JACK_IN_THE_BOX_POPPING", 16,
            "PHASE_BOBSLED_SLIDING", 17,
            "PHASE_BOBSLED_BOARDING", 18,
            "PHASE_BOBSLED_CRASHING", 19,
            "PHASE_POGO_BOUNCING", 20,
            "PHASE_POGO_HIGH_BOUNCE_1", 21,
            "PHASE_POGO_HIGH_BOUNCE_2", 22,
            "PHASE_POGO_HIGH_BOUNCE_3", 23,
            "PHASE_POGO_HIGH_BOUNCE_4", 24,
            "PHASE_POGO_HIGH_BOUNCE_5", 25,
            "PHASE_POGO_HIGH_BOUNCE_6", 26,
            "PHASE_POGO_FORWARD_BOUNCE_2", 27,
            "PHASE_POGO_FORWARD_BOUNCE_7", 28,
            "PHASE_NEWSPAPER_READING", 29,
            "PHASE_NEWSPAPER_MADDENING", 30,
            "PHASE_NEWSPAPER_MAD", 31,
            "PHASE_DIGGER_TUNNELING", 32,
            "PHASE_DIGGER_RISING", 33,
            "PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE", 34,
            "PHASE_DIGGER_RISE_WITHOUT_AXE", 35,
            "PHASE_DIGGER_STUNNED", 36,
            "PHASE_DIGGER_WALKING", 37,
            "PHASE_DIGGER_WALKING_WITHOUT_AXE", 38,
            "PHASE_DIGGER_CUTSCENE", 39,
            "PHASE_DANCER_DANCING_IN", 40,
            "PHASE_DANCER_SNAPPING_FINGERS", 41,
            "PHASE_DANCER_SNAPPING_FINGERS_WITH_LIGHT", 42,
            "PHASE_DANCER_SNAPPING_FINGERS_HOLD", 43,
            "PHASE_DANCER_DANCING_LEFT", 44,
            "PHASE_DANCER_WALK_TO_RAISE", 45,
            "PHASE_DANCER_RAISE_LEFT_1", 46,
            "PHASE_DANCER_RAISE_RIGHT_1", 47,
            "PHASE_DANCER_RAISE_LEFT_2", 48,
            "PHASE_DANCER_RAISE_RIGHT_2", 49,
            "PHASE_DANCER_RISING", 50,
            "PHASE_DOLPHIN_WALKING", 51,
            "PHASE_DOLPHIN_INTO_POOL", 52,
            "PHASE_DOLPHIN_RIDING", 53,
            "PHASE_DOLPHIN_IN_JUMP", 54,
            "PHASE_DOLPHIN_WALKING_IN_POOL", 55,
            "PHASE_DOLPHIN_WALKING_WITHOUT_DOLPHIN", 56,
            "PHASE_SNORKEL_WALKING", 57,
            "PHASE_SNORKEL_INTO_POOL", 58,
            "PHASE_SNORKEL_WALKING_IN_POOL", 59,
            "PHASE_SNORKEL_UP_TO_EAT", 60,
            "PHASE_SNORKEL_EATING_IN_POOL", 61,
            "PHASE_SNORKEL_DOWN_FROM_EAT", 62,
            "PHASE_ZOMBIQUARIUM_ACCEL", 63,
            "PHASE_ZOMBIQUARIUM_DRIFT", 64,
            "PHASE_ZOMBIQUARIUM_BACK_AND_FORTH", 65,
            "PHASE_ZOMBIQUARIUM_BITE", 66,
            "PHASE_CATAPULT_LAUNCHING", 67,
            "PHASE_CATAPULT_RELOADING", 68,
            "PHASE_GARGANTUAR_THROWING", 69,
            "PHASE_GARGANTUAR_SMASHING", 70,
            "PHASE_IMP_GETTING_THROWN", 71,
            "PHASE_IMP_LANDING", 72,
            "PHASE_BALLOON_FLYING", 73,
            "PHASE_BALLOON_POPPING", 74,
            "PHASE_BALLOON_WALKING", 75,
            "PHASE_LADDER_CARRYING", 76,
            "PHASE_LADDER_PLACING", 77,
            "PHASE_BOSS_ENTER", 78,
            "PHASE_BOSS_IDLE", 79,
            "PHASE_BOSS_SPAWNING", 80,
            "PHASE_BOSS_STOMPING", 81,
            "PHASE_BOSS_BUNGEES_ENTER", 82,
            "PHASE_BOSS_BUNGEES_DROP", 83,
            "PHASE_BOSS_BUNGEES_LEAVE", 84,
            "PHASE_BOSS_DROP_RV", 85,
            "PHASE_BOSS_HEAD_ENTER", 86,
            "PHASE_BOSS_HEAD_IDLE_BEFORE_SPIT", 87,
            "PHASE_BOSS_HEAD_IDLE_AFTER_SPIT", 88,
            "PHASE_BOSS_HEAD_SPIT", 89,
            "PHASE_BOSS_HEAD_LEAVE", 90,
            "PHASE_YETI_RUNNING", 91,
            "PHASE_SQUASH_PRE_LAUNCH", 92,
            "PHASE_SQUASH_RISING", 93,
            "PHASE_SQUASH_FALLING", 94,
            "PHASE_SQUASH_DONE_FALLING", 95
    );

    @Test
    void zombiePhaseMatchesUpstream() throws Exception {
        assertTable(ZombiePhase.class, ZOMBIEPHASE_EXPECTED);
    }

    // GridItemType (ConstEnums.h:499-514)
    private static final Map<String, Integer> GRIDITEMTYPE_EXPECTED = table(
            "GRIDITEM_NONE", 0,
            "GRIDITEM_GRAVESTONE", 1,
            "GRIDITEM_CRATER", 2,
            "GRIDITEM_LADDER", 3,
            "GRIDITEM_PORTAL_CIRCLE", 4,
            "GRIDITEM_PORTAL_SQUARE", 5,
            "GRIDITEM_BRAIN", 6,
            "GRIDITEM_SCARY_POT", 7,
            "GRIDITEM_SQUIRREL", 8,
            "GRIDITEM_ZEN_TOOL", 9,
            "GRIDITEM_STINKY", 10,
            "GRIDITEM_RAKE", 11,
            "GRIDITEM_IZOMBIE_BRAIN", 12
    );

    @Test
    void gridItemTypeMatchesUpstream() throws Exception {
        assertTable(GridItemType.class, GRIDITEMTYPE_EXPECTED);
    }

    // CoinType (ConstEnums.h:186-216)
    private static final Map<String, Integer> COINTYPE_EXPECTED = table(
            "COIN_NONE", 0,
            "COIN_SILVER", 1,
            "COIN_GOLD", 2,
            "COIN_DIAMOND", 3,
            "COIN_SUN", 4,
            "COIN_SMALLSUN", 5,
            "COIN_LARGESUN", 6,
            "COIN_FINAL_SEED_PACKET", 7,
            "COIN_TROPHY", 8,
            "COIN_SHOVEL", 9,
            "COIN_ALMANAC", 10,
            "COIN_CARKEYS", 11,
            "COIN_VASE", 12,
            "COIN_WATERING_CAN", 13,
            "COIN_TACO", 14,
            "COIN_NOTE", 15,
            "COIN_USABLE_SEED_PACKET", 16,
            "COIN_PRESENT_PLANT", 17,
            "COIN_AWARD_MONEY_BAG", 18,
            "COIN_AWARD_PRESENT", 19,
            "COIN_AWARD_BAG_DIAMOND", 20,
            "COIN_AWARD_SILVER_SUNFLOWER", 21,
            "COIN_AWARD_GOLD_SUNFLOWER", 22,
            "COIN_CHOCOLATE", 23,
            "COIN_AWARD_CHOCOLATE", 24,
            "COIN_PRESENT_MINIGAMES", 25,
            "COIN_PRESENT_PUZZLE_MODE", 26,
            "COIN_PRESENT_SURVIVAL_MODE", 27
    );

    @Test
    void coinTypeMatchesUpstream() throws Exception {
        assertTable(CoinType.class, COINTYPE_EXPECTED);
    }

    // ProjectileType (ConstEnums.h:800-817)
    private static final Map<String, Integer> PROJECTILETYPE_EXPECTED = table(
            "PROJECTILE_PEA", 0,
            "PROJECTILE_SNOWPEA", 1,
            "PROJECTILE_CABBAGE", 2,
            "PROJECTILE_MELON", 3,
            "PROJECTILE_PUFF", 4,
            "PROJECTILE_WINTERMELON", 5,
            "PROJECTILE_FIREBALL", 6,
            "PROJECTILE_STAR", 7,
            "PROJECTILE_SPIKE", 8,
            "PROJECTILE_BASKETBALL", 9,
            "PROJECTILE_KERNEL", 10,
            "PROJECTILE_COBBIG", 11,
            "PROJECTILE_BUTTER", 12,
            "PROJECTILE_ZOMBIE_PEA", 13,
            "NUM_PROJECTILES", 14
    );

    @Test
    void projectileTypeMatchesUpstream() throws Exception {
        assertTable(ProjectileType.class, PROJECTILETYPE_EXPECTED);
    }

    // ProjectileMotion (ConstEnums.h:787-799)
    private static final Map<String, Integer> PROJECTILEMOTION_EXPECTED = table(
            "MOTION_STRAIGHT", 0,
            "MOTION_LOBBED", 1,
            "MOTION_THREEPEATER", 2,
            "MOTION_BEE", 3,
            "MOTION_BEE_BACKWARDS", 4,
            "MOTION_PUFF", 5,
            "MOTION_BACKWARDS", 6,
            "MOTION_STAR", 7,
            "MOTION_FLOAT_OVER", 8,
            "MOTION_HOMING", 9
    );

    @Test
    void projectileMotionMatchesUpstream() throws Exception {
        assertTable(ProjectileMotion.class, PROJECTILEMOTION_EXPECTED);
    }

    // SeedType (ConstEnums.h:1035-1114)
    private static final Map<String, Integer> SEEDTYPE_EXPECTED = table(
            "SEED_PEASHOOTER", 0,
            "SEED_SUNFLOWER", 1,
            "SEED_CHERRYBOMB", 2,
            "SEED_WALLNUT", 3,
            "SEED_POTATOMINE", 4,
            "SEED_SNOWPEA", 5,
            "SEED_CHOMPER", 6,
            "SEED_REPEATER", 7,
            "SEED_PUFFSHROOM", 8,
            "SEED_SUNSHROOM", 9,
            "SEED_FUMESHROOM", 10,
            "SEED_GRAVEBUSTER", 11,
            "SEED_HYPNOSHROOM", 12,
            "SEED_SCAREDYSHROOM", 13,
            "SEED_ICESHROOM", 14,
            "SEED_DOOMSHROOM", 15,
            "SEED_LILYPAD", 16,
            "SEED_SQUASH", 17,
            "SEED_THREEPEATER", 18,
            "SEED_TANGLEKELP", 19,
            "SEED_JALAPENO", 20,
            "SEED_SPIKEWEED", 21,
            "SEED_TORCHWOOD", 22,
            "SEED_TALLNUT", 23,
            "SEED_SEASHROOM", 24,
            "SEED_PLANTERN", 25,
            "SEED_CACTUS", 26,
            "SEED_BLOVER", 27,
            "SEED_SPLITPEA", 28,
            "SEED_STARFRUIT", 29,
            "SEED_PUMPKINSHELL", 30,
            "SEED_MAGNETSHROOM", 31,
            "SEED_CABBAGEPULT", 32,
            "SEED_FLOWERPOT", 33,
            "SEED_KERNELPULT", 34,
            "SEED_INSTANT_COFFEE", 35,
            "SEED_GARLIC", 36,
            "SEED_UMBRELLA", 37,
            "SEED_MARIGOLD", 38,
            "SEED_MELONPULT", 39,
            "SEED_GATLINGPEA", 40,
            "SEED_TWINSUNFLOWER", 41,
            "SEED_GLOOMSHROOM", 42,
            "SEED_CATTAIL", 43,
            "SEED_WINTERMELON", 44,
            "SEED_GOLD_MAGNET", 45,
            "SEED_SPIKEROCK", 46,
            "SEED_COBCANNON", 47,
            "SEED_IMITATER", 48,
            "SEED_EXPLODE_O_NUT", 49,
            "SEED_GIANT_WALLNUT", 50,
            "SEED_SPROUT", 51,
            "SEED_LEFTPEATER", 52,
            "NUM_SEED_TYPES", 53,
            "SEED_BEGHOULED_BUTTON_SHUFFLE", 54,
            "SEED_BEGHOULED_BUTTON_CRATER", 55,
            "SEED_SLOT_MACHINE_SUN", 56,
            "SEED_SLOT_MACHINE_DIAMOND", 57,
            "SEED_ZOMBIQUARIUM_SNORKLE", 58,
            "SEED_ZOMBIQUARIUM_TROPHY", 59,
            "SEED_ZOMBIE_NORMAL", 60,
            "SEED_ZOMBIE_TRAFFIC_CONE", 61,
            "SEED_ZOMBIE_POLEVAULTER", 62,
            "SEED_ZOMBIE_PAIL", 63,
            "SEED_ZOMBIE_LADDER", 64,
            "SEED_ZOMBIE_DIGGER", 65,
            "SEED_ZOMBIE_BUNGEE", 66,
            "SEED_ZOMBIE_FOOTBALL", 67,
            "SEED_ZOMBIE_BALLOON", 68,
            "SEED_ZOMBIE_SCREEN_DOOR", 69,
            "SEED_ZOMBONI", 70,
            "SEED_ZOMBIE_POGO", 71,
            "SEED_ZOMBIE_DANCER", 72,
            "SEED_ZOMBIE_GARGANTUAR", 73,
            "SEED_ZOMBIE_IMP", 74,
            "NUM_SEEDS_IN_CHOOSER", 49,
            "SEED_NONE", -1,
            // The id space is wider than the retail seed types: MAX_SEED_TYPES is the width of every
            // per-seed table (Plant::gPlantDefs included), and the rows above the retail ones are
            // empty until a definition is written into them.
            "MAX_SEED_TYPES", 200
    );

    @Test
    void seedTypeMatchesUpstream() throws Exception {
        assertTable(SeedType.class, SEEDTYPE_EXPECTED);
    }

    // ReanimationType (ConstEnums.h:818-965)
    private static final Map<String, Integer> REANIMATIONTYPE_EXPECTED = table(
            "REANIM_NONE", -1,
            "REANIM_LOADBAR_SPROUT", 0,
            "REANIM_LOADBAR_ZOMBIEHEAD", 1,
            "REANIM_SODROLL", 2,
            "REANIM_FINAL_WAVE", 3,
            "REANIM_PEASHOOTER", 4,
            "REANIM_WALLNUT", 5,
            "REANIM_LILYPAD", 6,
            "REANIM_SUNFLOWER", 7,
            "REANIM_LAWNMOWER", 8,
            "REANIM_READYSETPLANT", 9,
            "REANIM_CHERRYBOMB", 10,
            "REANIM_SQUASH", 11,
            "REANIM_DOOMSHROOM", 12,
            "REANIM_SNOWPEA", 13,
            "REANIM_REPEATER", 14,
            "REANIM_SUNSHROOM", 15,
            "REANIM_TALLNUT", 16,
            "REANIM_FUMESHROOM", 17,
            "REANIM_PUFFSHROOM", 18,
            "REANIM_HYPNOSHROOM", 19,
            "REANIM_CHOMPER", 20,
            "REANIM_ZOMBIE", 21,
            "REANIM_SUN", 22,
            "REANIM_POTATOMINE", 23,
            "REANIM_SPIKEWEED", 24,
            "REANIM_SPIKEROCK", 25,
            "REANIM_THREEPEATER", 26,
            "REANIM_MARIGOLD", 27,
            "REANIM_ICESHROOM", 28,
            "REANIM_ZOMBIE_FOOTBALL", 29,
            "REANIM_ZOMBIE_NEWSPAPER", 30,
            "REANIM_ZOMBIE_ZAMBONI", 31,
            "REANIM_SPLASH", 32,
            "REANIM_JALAPENO", 33,
            "REANIM_JALAPENO_FIRE", 34,
            "REANIM_COIN_SILVER", 35,
            "REANIM_ZOMBIE_CHARRED", 36,
            "REANIM_ZOMBIE_CHARRED_IMP", 37,
            "REANIM_ZOMBIE_CHARRED_DIGGER", 38,
            "REANIM_ZOMBIE_CHARRED_ZAMBONI", 39,
            "REANIM_ZOMBIE_CHARRED_CATAPULT", 40,
            "REANIM_ZOMBIE_CHARRED_GARGANTUAR", 41,
            "REANIM_SCRAREYSHROOM", 42,
            "REANIM_PUMPKIN", 43,
            "REANIM_PLANTERN", 44,
            "REANIM_TORCHWOOD", 45,
            "REANIM_SPLITPEA", 46,
            "REANIM_SEASHROOM", 47,
            "REANIM_BLOVER", 48,
            "REANIM_FLOWER_POT", 49,
            "REANIM_CACTUS", 50,
            "REANIM_DANCER", 51,
            "REANIM_TANGLEKELP", 52,
            "REANIM_STARFRUIT", 53,
            "REANIM_POLEVAULTER", 54,
            "REANIM_BALLOON", 55,
            "REANIM_GARGANTUAR", 56,
            "REANIM_IMP", 57,
            "REANIM_DIGGER", 58,
            "REANIM_DIGGER_DIRT", 59,
            "REANIM_ZOMBIE_DOLPHINRIDER", 60,
            "REANIM_POGO", 61,
            "REANIM_BACKUP_DANCER", 62,
            "REANIM_BOBSLED", 63,
            "REANIM_JACKINTHEBOX", 64,
            "REANIM_SNORKEL", 65,
            "REANIM_BUNGEE", 66,
            "REANIM_CATAPULT", 67,
            "REANIM_LADDER", 68,
            "REANIM_PUFF", 69,
            "REANIM_SLEEPING", 70,
            "REANIM_GRAVE_BUSTER", 71,
            "REANIM_ZOMBIES_WON", 72,
            "REANIM_MAGNETSHROOM", 73,
            "REANIM_BOSS", 74,
            "REANIM_CABBAGEPULT", 75,
            "REANIM_KERNELPULT", 76,
            "REANIM_MELONPULT", 77,
            "REANIM_COFFEEBEAN", 78,
            "REANIM_UMBRELLALEAF", 79,
            "REANIM_GATLINGPEA", 80,
            "REANIM_CATTAIL", 81,
            "REANIM_GLOOMSHROOM", 82,
            "REANIM_BOSS_ICEBALL", 83,
            "REANIM_BOSS_FIREBALL", 84,
            "REANIM_COBCANNON", 85,
            "REANIM_GARLIC", 86,
            "REANIM_GOLD_MAGNET", 87,
            "REANIM_WINTER_MELON", 88,
            "REANIM_TWIN_SUNFLOWER", 89,
            "REANIM_POOL_CLEANER", 90,
            "REANIM_ROOF_CLEANER", 91,
            "REANIM_FIRE_PEA", 92,
            "REANIM_IMITATER", 93,
            "REANIM_YETI", 94,
            "REANIM_BOSS_DRIVER", 95,
            "REANIM_LAWN_MOWERED_ZOMBIE", 96,
            "REANIM_CRAZY_DAVE", 97,
            "REANIM_TEXT_FADE_ON", 98,
            "REANIM_HAMMER", 99,
            "REANIM_SLOT_MACHINE_HANDLE", 100,
            "REANIM_CREDITS_FOOTBALL", 101,
            "REANIM_CREDITS_JACKBOX", 102,
            "REANIM_SELECTOR_SCREEN", 103,
            "REANIM_PORTAL_CIRCLE", 104,
            "REANIM_PORTAL_SQUARE", 105,
            "REANIM_ZENGARDEN_SPROUT", 106,
            "REANIM_ZENGARDEN_WATERINGCAN", 107,
            "REANIM_ZENGARDEN_FERTILIZER", 108,
            "REANIM_ZENGARDEN_BUGSPRAY", 109,
            "REANIM_ZENGARDEN_PHONOGRAPH", 110,
            "REANIM_DIAMOND", 111,
            "REANIM_ZOMBIE_HAND", 112,
            "REANIM_STINKY", 113,
            "REANIM_RAKE", 114,
            "REANIM_RAIN_CIRCLE", 115,
            "REANIM_RAIN_SPLASH", 116,
            "REANIM_ZOMBIE_SURPRISE", 117,
            "REANIM_COIN_GOLD", 118,
            "REANIM_TREEOFWISDOM", 119,
            "REANIM_TREEOFWISDOM_CLOUDS", 120,
            "REANIM_TREEOFWISDOM_TREEFOOD", 121,
            "REANIM_CREDITS_MAIN", 122,
            "REANIM_CREDITS_MAIN2", 123,
            "REANIM_CREDITS_MAIN3", 124,
            "REANIM_ZOMBIE_CREDITS_DANCE", 125,
            "REANIM_CREDITS_STAGE", 126,
            "REANIM_CREDITS_BIGBRAIN", 127,
            "REANIM_CREDITS_FLOWER_PETALS", 128,
            "REANIM_CREDITS_INFANTRY", 129,
            "REANIM_CREDITS_THROAT", 130,
            "REANIM_CREDITS_CRAZYDAVE", 131,
            "REANIM_CREDITS_BOSSDANCE", 132,
            "REANIM_ZOMBIE_CREDITS_SCREEN_DOOR", 133,
            "REANIM_ZOMBIE_CREDITS_CONEHEAD", 134,
            "REANIM_CREDITS_ZOMBIEARMY1", 135,
            "REANIM_CREDITS_ZOMBIEARMY2", 136,
            "REANIM_CREDITS_TOMBSTONES", 137,
            "REANIM_CREDITS_SOLARPOWER", 138,
            "REANIM_CREDITS_ANYHOUR", 139,
            "REANIM_CREDITS_WEARETHEUNDEAD", 140,
            "REANIM_CREDITS_DISCOLIGHTS", 141,
            "REANIM_FLAG", 142,
            "REANIM_ZOMBATAR_HEAD", 143,
            "NUM_REANIMS", 144
    );

    @Test
    void reanimationTypeMatchesUpstream() throws Exception {
        assertTable(ReanimationType.class, REANIMATIONTYPE_EXPECTED);
    }

    // ParticleEffect (ConstEnums.h:624-734)
    private static final Map<String, Integer> PARTICLEEFFECT_EXPECTED = table(
            "PARTICLE_NONE", -1,
            "PARTICLE_MELONSPLASH", 0,
            "PARTICLE_WINTERMELON", 1,
            "PARTICLE_FUMECLOUD", 2,
            "PARTICLE_POPCORNSPLASH", 3,
            "PARTICLE_POWIE", 4,
            "PARTICLE_JACKEXPLODE", 5,
            "PARTICLE_ZOMBIE_HEAD", 6,
            "PARTICLE_ZOMBIE_ARM", 7,
            "PARTICLE_ZOMBIE_TRAFFIC_CONE", 8,
            "PARTICLE_ZOMBIE_PAIL", 9,
            "PARTICLE_ZOMBIE_HELMET", 10,
            "PARTICLE_ZOMBIE_FLAG", 11,
            "PARTICLE_ZOMBIE_DOOR", 12,
            "PARTICLE_ZOMBIE_NEWSPAPER", 13,
            "PARTICLE_ZOMBIE_HEADLIGHT", 14,
            "PARTICLE_POW", 15,
            "PARTICLE_ZOMBIE_POGO", 16,
            "PARTICLE_ZOMBIE_NEWSPAPER_HEAD", 17,
            "PARTICLE_ZOMBIE_BALLOON_HEAD", 18,
            "PARTICLE_SOD_ROLL", 19,
            "PARTICLE_GRAVE_STONE_RISE", 20,
            "PARTICLE_PLANTING", 21,
            "PARTICLE_PLANTING_POOL", 22,
            "PARTICLE_ZOMBIE_RISE", 23,
            "PARTICLE_GRAVE_BUSTER", 24,
            "PARTICLE_GRAVE_BUSTER_DIE", 25,
            "PARTICLE_POOL_SPLASH", 26,
            "PARTICLE_ICE_SPARKLE", 27,
            "PARTICLE_SEED_PACKET", 28,
            "PARTICLE_TALL_NUT_BLOCK", 29,
            "PARTICLE_DOOM", 30,
            "PARTICLE_DIGGER_RISE", 31,
            "PARTICLE_DIGGER_TUNNEL", 32,
            "PARTICLE_DANCER_RISE", 33,
            "PARTICLE_POOL_SPARKLY", 34,
            "PARTICLE_WALLNUT_EAT_SMALL", 35,
            "PARTICLE_WALLNUT_EAT_LARGE", 36,
            "PARTICLE_PEA_SPLAT", 37,
            "PARTICLE_BUTTER_SPLAT", 38,
            "PARTICLE_CABBAGE_SPLAT", 39,
            "PARTICLE_PUFF_SPLAT", 40,
            "PARTICLE_STAR_SPLAT", 41,
            "PARTICLE_ICE_TRAP", 42,
            "PARTICLE_SNOWPEA_SPLAT", 43,
            "PARTICLE_SNOWPEA_PUFF", 44,
            "PARTICLE_SNOWPEA_TRAIL", 45,
            "PARTICLE_LANTERN_SHINE", 46,
            "PARTICLE_SEED_PACKET_PICKUP", 47,
            "PARTICLE_POTATO_MINE", 48,
            "PARTICLE_POTATO_MINE_RISE", 49,
            "PARTICLE_PUFFSHROOM_TRAIL", 50,
            "PARTICLE_PUFFSHROOM_MUZZLE", 51,
            "PARTICLE_SEED_PACKET_FLASH", 52,
            "PARTICLE_WHACK_A_ZOMBIE_RISE", 53,
            "PARTICLE_ZOMBIE_LADDER", 54,
            "PARTICLE_UMBRELLA_REFLECT", 55,
            "PARTICLE_SEED_PACKET_PICK", 56,
            "PARTICLE_ICE_TRAP_ZOMBIE", 57,
            "PARTICLE_ICE_TRAP_RELEASE", 58,
            "PARTICLE_ZAMBONI_SMOKE", 59,
            "PARTICLE_GLOOMCLOUD", 60,
            "PARTICLE_ZOMBIE_POGO_HEAD", 61,
            "PARTICLE_ZAMBONI_TIRE", 62,
            "PARTICLE_ZAMBONI_EXPLOSION", 63,
            "PARTICLE_ZAMBONI_EXPLOSION2", 64,
            "PARTICLE_CATAPULT_EXPLOSION", 65,
            "PARTICLE_MOWER_CLOUD", 66,
            "PARTICLE_BOSS_ICE_BALL", 67,
            "PARTICLE_BLASTMARK", 68,
            "PARTICLE_COIN_PICKUP_ARROW", 69,
            "PARTICLE_PRESENT_PICKUP", 70,
            "PARTICLE_IMITATER_MORPH", 71,
            "PARTICLE_MOWERED_ZOMBIE_HEAD", 72,
            "PARTICLE_MOWERED_ZOMBIE_ARM", 73,
            "PARTICLE_ZOMBIE_HEAD_POOL", 74,
            "PARTICLE_ZOMBIE_BOSS_FIREBALL", 75,
            "PARTICLE_FIREBALL_DEATH", 76,
            "PARTICLE_ICEBALL_DEATH", 77,
            "PARTICLE_ICEBALL_TRAIL", 78,
            "PARTICLE_FIREBALL_TRAIL", 79,
            "PARTICLE_BOSS_EXPLOSION", 80,
            "PARTICLE_SCREEN_FLASH", 81,
            "PARTICLE_TROPHY_SPARKLE", 82,
            "PARTICLE_PORTAL_CIRCLE", 83,
            "PARTICLE_PORTAL_SQUARE", 84,
            "PARTICLE_POTTED_PLANT_GLOW", 85,
            "PARTICLE_POTTED_WATER_PLANT_GLOW", 86,
            "PARTICLE_POTTED_ZEN_GLOW", 87,
            "PARTICLE_MIND_CONTROL", 88,
            "PARTICLE_VASE_SHATTER", 89,
            "PARTICLE_VASE_SHATTER_LEAF", 90,
            "PARTICLE_VASE_SHATTER_ZOMBIE", 91,
            "PARTICLE_AWARD_PICKUP_ARROW", 92,
            "PARTICLE_ZOMBIE_SEAWEED", 93,
            "PARTICLE_ZOMBIE_MUSTACHE", 94,
            "PARTICLE_ZOMBIE_SUNGLASS", 95,
            "PARTICLE_ZOMBIE_PINATA", 96,
            "PARTICLE_DUST_SQUASH", 97,
            "PARTICLE_DUST_FOOT", 98,
            "PARTICLE_ZOMBIE_DAISIES", 99,
            "PARTICLE_CREDIT_STROBE", 100,
            "PARTICLE_CREDITS_RAYSWIPE", 101,
            "PARTICLE_CREDITS_ZOMBIEHEADWIPE", 102,
            "PARTICLE_STARBURST", 103,
            "PARTICLE_CREDITS_FOG", 104,
            "PARTICLE_PERSENT_PICK_UP_ARROW", 105,
            "NUM_PARTICLES", 106
    );

    @Test
    void particleEffectMatchesUpstream() throws Exception {
        assertTable(ParticleEffect.class, PARTICLEEFFECT_EXPECTED);
    }

    // EffectType (ConstEnums.h:363-370)
    private static final Map<String, Integer> EFFECTTYPE_EXPECTED = table(
            "EFFECT_PARTICLE", 0,
            "EFFECT_TRAIL", 1,
            "EFFECT_REANIM", 2,
            "EFFECT_ATTACHMENT", 3,
            "EFFECT_OTHER", 4
    );

    @Test
    void effectTypeMatchesUpstream() throws Exception {
        assertTable(EffectType.class, EFFECTTYPE_EXPECTED);
    }

    // ReanimLoopType (ConstEnums.h:966-974)
    private static final Map<String, Integer> REANIMLOOPTYPE_EXPECTED = table(
            "REANIM_LOOP", 0,
            "REANIM_LOOP_FULL_LAST_FRAME", 1,
            "REANIM_PLAY_ONCE", 2,
            "REANIM_PLAY_ONCE_AND_HOLD", 3,
            "REANIM_PLAY_ONCE_FULL_LAST_FRAME", 4,
            "REANIM_PLAY_ONCE_FULL_LAST_FRAME_AND_HOLD", 5
    );

    @Test
    void reanimLoopTypeMatchesUpstream() throws Exception {
        assertTable(ReanimLoopType.class, REANIMLOOPTYPE_EXPECTED);
    }

    // AttachmentID (ConstEnums.h:1382-1382)
    private static final Map<String, Integer> ATTACHMENTID_EXPECTED = table(
            "ATTACHMENTID_NULL", 0
    );

    @Test
    void attachmentIDMatchesUpstream() throws Exception {
        assertTable(AttachmentID.class, ATTACHMENTID_EXPECTED);
    }

    private static final Map<String, Integer> EXPECTED_CONSTANT_COUNTS = table(
            "AdviceType", 68,
            "AlmanacPage", 3,
            "AwardType", 5,
            "BackgroundType", 10,
            "BoardResult", 7,
            "BossPart", 5,
            "ChallengePage", 5,
            "ChallengeState", 15,
            "ChosenSeedState", 5,
            "CoinMotion", 7,
            "CoinType", 28,
            "CrazyDaveState", 7,
            "CursorType", 18,
            "DamageFlags", 6,
            "DamageRangeFlags", 8,
            "Dialogs", 54,
            "DebugTextMode", 5,
            "DrawStringJustification", 6,
            "DrawVariation", 18,
            "EffectType", 5,
            "EmitterType", 5,
            "GameMode", 74,
            "GameObjectType", 22,
            "GameScenes", 8,
            "GardenType", 4,
            "GridItemType", 13,
            "GridItemState", 30,
            "GridSquareType", 5,
            "HelmType", 10,
            "LawnMowerState", 4,
            "LawnMowerType", 5,
            "MessageStyle", 19,
            "MowerHeight", 4,
            "NotRecommend", 8,
            "ParticleEffect", 108,
            "PlantPriority", 10,
            "PlantingReason", 14,
            "PlantRowType", 4,
            "PottedPlantAge", 4,
            "PottedPlantNeed", 5,
            "ProjectileMotion", 10,
            "ProjectileType", 15,
            "ReanimationType", 146,
            "ReanimLoopType", 6,
            "RenderLayer", 17,
            "RenderObjectType", 25,
            "ScaryPotType", 4,
            "SeedChooserState", 2,
            "SeedType", 78,
            "ShieldType", 4,
            "StoreItem", 32,
            "StorePages", 5,
            "PvzpCurves", 14,
            "TrialType", 2,
            "TutorialState", 31,
            "UnlockingState", 3,
            "ZombieHeight", 11,
            "ZombiePhase", 96,
            "ZombieType", 38,
            "AttachmentID", 1,
            "CoinID", 1,
            "ParticleID", 1,
            "ParticleEmitterID", 1,
            "ParticleSystemID", 1,
            "PlantID", 1,
            "ReanimationID", 1,
            "ZombieID", 1
    );

    @Test
    void everyEnumClassHasExpectedConstantCountAndNoNameCollisions() throws Exception {
        for (Map.Entry<String, Integer> e : EXPECTED_CONSTANT_COUNTS.entrySet()) {
            Class<?> cls = Class.forName("cn.enaium.fabricpvz.portable.lawn." + e.getKey());
            List<String> names = new ArrayList<>();
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isPublic(f.getModifiers()) && f.getType() == int.class
                        && Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
                    names.add(f.getName());
                }
            }
            assertEquals(e.getValue().intValue(), names.size(),
                    cls.getSimpleName() + " constant count");
            assertEquals(names.size(), new TreeSet<>(names).size(),
                    cls.getSimpleName() + " has duplicate constant names");
        }
    }

    private static void assertTable(Class<?> cls, Map<String, Integer> expected) throws Exception {
        assertEquals(expected.size(), countIntConstants(cls),
                cls.getSimpleName() + " constant count");
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            Field f = cls.getField(e.getKey());
            assertEquals(e.getValue().intValue(), f.getInt(null),
                    cls.getSimpleName() + "." + e.getKey());
        }
    }

    private static int countIntConstants(Class<?> cls) {
        int count = 0;
        for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && f.getType() == int.class
                    && Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
                count++;
            }
        }
        return count;
    }
}
