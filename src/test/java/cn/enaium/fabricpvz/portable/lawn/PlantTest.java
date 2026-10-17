/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.Plant (+ its definition table and helper classes).
 *
 * The always-on group needs no game assets: it builds plants by hand and checks the data table,
 * the pure classification helpers and the geometry/render-order maths against values hand-derived
 * from src/Lawn/Plant.cpp. The behavioural group drives a real LawnApp/Board off the retail assets
 * (docs/research/03-plant.md) and is skipped when they are missing, so the suite stays green on
 * machines without the game.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

class PlantTest {

    // =======================================================================================
    // Group 1 — definition table and pure helpers (no assets required)
    // =======================================================================================

    /** Plant.cpp:48-106, transcribed independently: {packetIndex, seedCost, refreshTime, subClass, launchRate}. */
    private static final int[][] UPSTREAM_DEFS = {
        /* PEASHOOTER     */ { 0, 100, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* SUNFLOWER      */ { 1, 50, 750, PlantSubClass.SUBCLASS_NORMAL, 2500 },
        /* CHERRYBOMB     */ { 3, 150, 5000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* WALLNUT        */ { 2, 50, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* POTATOMINE     */ { 37, 25, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SNOWPEA        */ { 4, 175, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* CHOMPER        */ { 31, 150, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* REPEATER       */ { 5, 200, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* PUFFSHROOM     */ { 6, 0, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* SUNSHROOM      */ { 7, 25, 750, PlantSubClass.SUBCLASS_NORMAL, 2500 },
        /* FUMESHROOM     */ { 9, 75, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* GRAVEBUSTER    */ { 40, 75, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* HYPNOSHROOM    */ { 10, 75, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SCAREDYSHROOM  */ { 33, 25, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* ICESHROOM      */ { 36, 75, 5000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* DOOMSHROOM     */ { 20, 125, 5000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* LILYPAD        */ { 19, 25, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SQUASH         */ { 21, 50, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* THREEPEATER    */ { 12, 325, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* TANGLEKELP     */ { 17, 25, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* JALAPENO       */ { 11, 125, 5000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SPIKEWEED      */ { 22, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* TORCHWOOD      */ { 29, 175, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* TALLNUT        */ { 28, 125, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SEASHROOM      */ { 39, 0, 3000, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* PLANTERN       */ { 38, 25, 3000, PlantSubClass.SUBCLASS_NORMAL, 2500 },
        /* CACTUS         */ { 15, 125, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* BLOVER         */ { 18, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SPLITPEA       */ { 32, 125, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* STARFRUIT      */ { 30, 125, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* PUMPKINSHELL   */ { 25, 125, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* MAGNETSHROOM   */ { 35, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* CABBAGEPULT    */ { 13, 100, 750, PlantSubClass.SUBCLASS_SHOOTER, 300 },
        /* FLOWERPOT      */ { 33, 25, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* KERNELPULT     */ { 13, 100, 750, PlantSubClass.SUBCLASS_SHOOTER, 300 },
        /* INSTANT_COFFEE */ { 33, 75, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* GARLIC         */ { 8, 50, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* UMBRELLA       */ { 23, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* MARIGOLD       */ { 24, 50, 3000, PlantSubClass.SUBCLASS_NORMAL, 2500 },
        /* MELONPULT      */ { 14, 300, 750, PlantSubClass.SUBCLASS_SHOOTER, 300 },
        /* GATLINGPEA     */ { 5, 250, 5000, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* TWINSUNFLOWER  */ { 1, 150, 5000, PlantSubClass.SUBCLASS_NORMAL, 2500 },
        /* GLOOMSHROOM    */ { 27, 150, 5000, PlantSubClass.SUBCLASS_SHOOTER, 200 },
        /* CATTAIL        */ { 27, 225, 5000, PlantSubClass.SUBCLASS_SHOOTER, 150 },
        /* WINTERMELON    */ { 27, 200, 5000, PlantSubClass.SUBCLASS_SHOOTER, 300 },
        /* GOLD_MAGNET    */ { 27, 50, 5000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SPIKEROCK      */ { 27, 125, 5000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* COBCANNON      */ { 16, 500, 5000, PlantSubClass.SUBCLASS_NORMAL, 600 },
        /* IMITATER       */ { 33, 0, 750, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* EXPLODE_O_NUT  */ { 2, 0, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* GIANT_WALLNUT  */ { 2, 0, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* SPROUT         */ { 33, 0, 3000, PlantSubClass.SUBCLASS_NORMAL, 0 },
        /* LEFTPEATER     */ { 5, 200, 750, PlantSubClass.SUBCLASS_SHOOTER, 150 }
    };

    /** Plant.cpp:48-106 — the mPlantName stems, in SeedType order. */
    private static final String[] UPSTREAM_NAMES = {
        "PEASHOOTER", "SUNFLOWER", "CHERRY_BOMB", "WALL_NUT", "POTATO_MINE", "SNOW_PEA", "CHOMPER",
        "REPEATER", "PUFF_SHROOM", "SUN_SHROOM", "FUME_SHROOM", "GRAVE_BUSTER", "HYPNO_SHROOM",
        "SCAREDY_SHROOM", "ICE_SHROOM", "DOOM_SHROOM", "LILY_PAD", "SQUASH", "THREEPEATER",
        "TANGLE_KELP", "JALAPENO", "SPIKEWEED", "TORCHWOOD", "TALL_NUT", "SEA_SHROOM", "PLANTERN",
        "CACTUS", "BLOVER", "SPLIT_PEA", "STARFRUIT", "PUMPKIN", "MAGNET_SHROOM", "CABBAGE_PULT",
        "FLOWER_POT", "KERNEL_PULT", "COFFEE_BEAN", "GARLIC", "UMBRELLA_LEAF", "MARIGOLD",
        "MELON_PULT", "GATLING_PEA", "TWIN_SUNFLOWER", "GLOOM_SHROOM", "CATTAIL", "WINTER_MELON",
        "GOLD_MAGNET", "SPIKEROCK", "COB_CANNON", "IMITATER", "EXPLODE_O_NUT", "GIANT_WALLNUT",
        "SPROUT", "REPEATER"
    };

    /** Plant.cpp:48-106 — the mReanimationType of every row, in SeedType order. */
    private static final int[] UPSTREAM_REANIMS = {
        ReanimationType.REANIM_PEASHOOTER, ReanimationType.REANIM_SUNFLOWER, ReanimationType.REANIM_CHERRYBOMB,
        ReanimationType.REANIM_WALLNUT, ReanimationType.REANIM_POTATOMINE, ReanimationType.REANIM_SNOWPEA,
        ReanimationType.REANIM_CHOMPER, ReanimationType.REANIM_REPEATER, ReanimationType.REANIM_PUFFSHROOM,
        ReanimationType.REANIM_SUNSHROOM, ReanimationType.REANIM_FUMESHROOM, ReanimationType.REANIM_GRAVE_BUSTER,
        ReanimationType.REANIM_HYPNOSHROOM, ReanimationType.REANIM_SCRAREYSHROOM, ReanimationType.REANIM_ICESHROOM,
        ReanimationType.REANIM_DOOMSHROOM, ReanimationType.REANIM_LILYPAD, ReanimationType.REANIM_SQUASH,
        ReanimationType.REANIM_THREEPEATER, ReanimationType.REANIM_TANGLEKELP, ReanimationType.REANIM_JALAPENO,
        ReanimationType.REANIM_SPIKEWEED, ReanimationType.REANIM_TORCHWOOD, ReanimationType.REANIM_TALLNUT,
        ReanimationType.REANIM_SEASHROOM, ReanimationType.REANIM_PLANTERN, ReanimationType.REANIM_CACTUS,
        ReanimationType.REANIM_BLOVER, ReanimationType.REANIM_SPLITPEA, ReanimationType.REANIM_STARFRUIT,
        ReanimationType.REANIM_PUMPKIN, ReanimationType.REANIM_MAGNETSHROOM, ReanimationType.REANIM_CABBAGEPULT,
        ReanimationType.REANIM_FLOWER_POT, ReanimationType.REANIM_KERNELPULT, ReanimationType.REANIM_COFFEEBEAN,
        ReanimationType.REANIM_GARLIC, ReanimationType.REANIM_UMBRELLALEAF, ReanimationType.REANIM_MARIGOLD,
        ReanimationType.REANIM_MELONPULT, ReanimationType.REANIM_GATLINGPEA, ReanimationType.REANIM_TWIN_SUNFLOWER,
        ReanimationType.REANIM_GLOOMSHROOM, ReanimationType.REANIM_CATTAIL, ReanimationType.REANIM_WINTER_MELON,
        ReanimationType.REANIM_GOLD_MAGNET, ReanimationType.REANIM_SPIKEROCK, ReanimationType.REANIM_COBCANNON,
        ReanimationType.REANIM_IMITATER, ReanimationType.REANIM_WALLNUT, ReanimationType.REANIM_WALLNUT,
        ReanimationType.REANIM_ZENGARDEN_SPROUT, ReanimationType.REANIM_REPEATER
    };

    /** The 53 plant seed types (SeedType.SEED_PEASHOOTER .. SEED_LEFTPEATER). */
    private static final int NUM_PLANT_ROWS = 53;

    @Test
    void definitionTableIsCompleteAndIndexAligned() {
        assertEquals(SeedType.MAX_SEED_TYPES, Plant.gPlantDefs.length,
                "the table is as wide as the seed-type id space");

        for (int i = 0; i < NUM_PLANT_ROWS; i++) {
            PlantDefinition aDef = Plant.gPlantDefs[i];
            assertNotNull(aDef, "row " + i);
            assertEquals(i, aDef.mSeedType, "row " + i + " must be self-identifying (Plant.cpp:4966 asserts this)");
            assertNotNull(aDef.mPlantName, "row " + i);
            assertFalse(aDef.mPlantName.isEmpty(), "row " + i);
            assertNull(aDef.mPlantImage, "mPlantImage is null in every shipped row (03-plant.md §2.1)");
            assertTrue(aDef.mReanimationType != ReanimationType.REANIM_NONE, "row " + i + " has a body reanim");
            assertTrue(aDef.mSubClass == PlantSubClass.SUBCLASS_NORMAL || aDef.mSubClass == PlantSubClass.SUBCLASS_SHOOTER);
        }

        // Above the retail seed types the id space is free: a row nobody has written to is empty, and a
        // lookup of one answers null instead of a definition (Plant#getPlantDefinition).
        for (int i = NUM_PLANT_ROWS; i < Plant.gPlantDefs.length; i++) {
            assertNull(Plant.gPlantDefs[i], "row " + i + " is free until something registers it");
            assertNull(Plant.getPlantDefinition(i), "an empty row is not a definition");
        }
        assertNull(Plant.getPlantDefinition(SeedType.MAX_SEED_TYPES), "an id outside the id space has no row");
        assertNull(Plant.getPlantDefinition(SeedType.SEED_NONE), "no negative id has a row");
    }

    @Test
    void definitionTableMatchesUpstreamLiterals() {
        for (int i = 0; i < NUM_PLANT_ROWS; i++) {
            PlantDefinition aDef = Plant.gPlantDefs[i];
            int[] anExpected = UPSTREAM_DEFS[i];

            assertEquals(anExpected[0], aDef.mPacketIndex, aDef.mPlantName + " packetIndex");
            assertEquals(anExpected[1], aDef.mSeedCost, aDef.mPlantName + " seedCost");
            assertEquals(anExpected[2], aDef.mRefreshTime, aDef.mPlantName + " refreshTime");
            assertEquals(anExpected[3], aDef.mSubClass, aDef.mPlantName + " subClass");
            assertEquals(anExpected[4], aDef.mLaunchRate, aDef.mPlantName + " launchRate");
            assertEquals(UPSTREAM_NAMES[i], aDef.mPlantName, "row " + i + " name stem");
            assertEquals(UPSTREAM_REANIMS[i], aDef.mReanimationType, aDef.mPlantName + " reanimation");
        }

        // The one deliberate alias (03-plant.md §2.1).
        assertEquals(Plant.gPlantDefs[SeedType.SEED_REPEATER].mReanimationType,
                Plant.gPlantDefs[SeedType.SEED_LEFTPEATER].mReanimationType, "leftpeater reuses REANIM_REPEATER");
        assertEquals("REPEATER", Plant.gPlantDefs[SeedType.SEED_LEFTPEATER].mPlantName);
    }

    @Test
    void getPlantDefinitionReturnsTheIndexedRow() {
        for (int i = 0; i < NUM_PLANT_ROWS; i++) {
            assertSame(Plant.gPlantDefs[i], Plant.getPlantDefinition(i), "SeedType " + i);
        }
    }

    @Test
    void getCostAndRefreshTimeResolveTheImitaterAndZombieSeeds() {
        new LawnApp(); // Plant.getCost reads LawnApp.gLawnApp.mGameMode

        assertEquals(100, Plant.getCost(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE));
        assertEquals(200, Plant.getCost(SeedType.SEED_REPEATER, SeedType.SEED_NONE));
        assertEquals(500, Plant.getCost(SeedType.SEED_COBCANNON, SeedType.SEED_NONE));
        // An imitater costs what it imitates (Plant.cpp:5027-5035).
        assertEquals(175, Plant.getCost(SeedType.SEED_IMITATER, SeedType.SEED_SNOWPEA));
        assertEquals(0, Plant.getCost(SeedType.SEED_SLOT_MACHINE_SUN, SeedType.SEED_NONE));
        assertEquals(300, Plant.getCost(SeedType.SEED_ZOMBIE_GARGANTUAR, SeedType.SEED_NONE));

        assertEquals(750, Plant.getRefreshTime(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE));
        assertEquals(5000, Plant.getRefreshTime(SeedType.SEED_COBCANNON, SeedType.SEED_NONE));
        assertEquals(5000, Plant.getRefreshTime(SeedType.SEED_IMITATER, SeedType.SEED_COBCANNON));
        assertEquals(0, Plant.getRefreshTime(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE), "zombie seeds never recharge");
    }

    @Test
    void plantClassificationMatchesUpstream() {
        assertTrue(Plant.isNocturnal(SeedType.SEED_PUFFSHROOM));
        assertFalse(Plant.isNocturnal(SeedType.SEED_PEASHOOTER));
        assertTrue(Plant.isFungus(SeedType.SEED_SUNSHROOM));
        assertFalse(Plant.isFungus(SeedType.SEED_SUNFLOWER));
        assertTrue(Plant.isAquatic(SeedType.SEED_TANGLEKELP));
        assertFalse(Plant.isAquatic(SeedType.SEED_KERNELPULT));
        assertTrue(Plant.isFlying(SeedType.SEED_INSTANT_COFFEE));
        assertFalse(Plant.isFlying(SeedType.SEED_CATTAIL));
        assertTrue(Plant.isUpgrade(SeedType.SEED_GATLINGPEA));
        assertFalse(Plant.isUpgrade(SeedType.SEED_REPEATER));
    }

    @Test
    void damageRangeFlagsMatchUpstream() {
        assertEquals(1, makePlant(SeedType.SEED_PEASHOOTER).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(2, makePlant(SeedType.SEED_CACTUS).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(1, makePlant(SeedType.SEED_CACTUS).getDamageRangeFlags(PlantWeapon.WEAPON_SECONDARY));
        assertEquals(127, makePlant(SeedType.SEED_CHERRYBOMB).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(127, makePlant(SeedType.SEED_JALAPENO).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(127, makePlant(SeedType.SEED_DOOMSHROOM).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(13, makePlant(SeedType.SEED_MELONPULT).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(13, makePlant(SeedType.SEED_KERNELPULT).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(77, makePlant(SeedType.SEED_POTATOMINE).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(13, makePlant(SeedType.SEED_SQUASH).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(9, makePlant(SeedType.SEED_FUMESHROOM).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(9, makePlant(SeedType.SEED_GLOOMSHROOM).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(9, makePlant(SeedType.SEED_CHOMPER).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(11, makePlant(SeedType.SEED_CATTAIL).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(5, makePlant(SeedType.SEED_TANGLEKELP).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(17, makePlant(SeedType.SEED_GIANT_WALLNUT).getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY));
    }

    @Test
    void attackRectGeometryMatchesUpstream() {
        // Plant.cpp:5160-5190, with mX = 120, mY = 100, w = h = 80.
        assertEquals(new Rect(180, 100, 800, 80),
                makePlant(SeedType.SEED_PEASHOOTER).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY), "default shooter rect");
        assertEquals(new Rect(0, 100, 120, 80),
                makePlant(SeedType.SEED_LEFTPEATER).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY), "leftpeater fires left");
        assertEquals(new Rect(0, 100, 136, 80),
                makePlant(SeedType.SEED_SPLITPEA).getPlantAttackRect(PlantWeapon.WEAPON_SECONDARY), "split-pea back shot");
        assertEquals(new Rect(140, 100, 45, 80),
                makePlant(SeedType.SEED_SQUASH).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(200, 100, 40, 80),
                makePlant(SeedType.SEED_CHOMPER).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(140, 100, 30, 80),
                makePlant(SeedType.SEED_SPIKEWEED).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(120, 100, 55, 80),
                makePlant(SeedType.SEED_POTATOMINE).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(170, 100, 30, 80),
                makePlant(SeedType.SEED_TORCHWOOD).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(180, 100, 230, 80),
                makePlant(SeedType.SEED_PUFFSHROOM).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(180, 100, 340, 80),
                makePlant(SeedType.SEED_FUMESHROOM).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(40, 20, 240, 240),
                makePlant(SeedType.SEED_GLOOMSHROOM).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY));
        assertEquals(new Rect(-800, -600, 1600, 1200),
                makePlant(SeedType.SEED_CATTAIL).getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY), "cattail hits anywhere");

        // GetPlantRect (Plant.cpp:5136-5158).
        assertEquals(new Rect(130, 100, 80, 80), makePlant(SeedType.SEED_TALLNUT).getPlantRect());
        Plant aPumpkin = makePlant(SeedType.SEED_PUMPKINSHELL);
        aPumpkin.mWidth = 120;
        assertEquals(new Rect(120, 100, 100, 80), aPumpkin.getPlantRect());
        assertEquals(new Rect(120, 100, 140, 80), makePlant(SeedType.SEED_COBCANNON).getPlantRect());
        assertEquals(new Rect(130, 100, 60, 80), makePlant(SeedType.SEED_PEASHOOTER).getPlantRect());
    }

    @Test
    void flowerPotHeightOffsetsMatchUpstream() {
        // Plant.cpp:3485-3535: -5*scale + (scale*fix - fix).
        // Plant.cpp:3524-3534: LILYPAD keeps aScaleOffsetFix -= 16, so at half scale the fix term
        // (0.5*-16 - -16) adds 8 to -5*0.5.
        assertEquals(-5.0f, Plant.plantFlowerPotHeightOffset(SeedType.SEED_PEASHOOTER, 1.0f), 0.0f);
        assertEquals(-10.0f, Plant.plantFlowerPotHeightOffset(SeedType.SEED_CHOMPER, 1.0f), 0.0f);
        assertEquals(1.5f, Plant.plantFlowerPotHeightOffset(SeedType.SEED_PEASHOOTER, 0.5f), 0.0f,
                "-2.5 + (0.5*-8 - -8)");
        assertEquals(5.5f, Plant.plantFlowerPotHeightOffset(SeedType.SEED_LILYPAD, 0.5f), 0.0f,
                "-2.5 + (0.5*-16 - -16), the lily pad keeps its own scale fix");
    }

    @Test
    void calcRenderOrderSortsByLayerRowAndColumn() {
        // Plant.cpp:503-530 → Board.MakeRenderOrder(layer, row, order*5 - mX + 800).
        Plant aLilypad = makePlant(SeedType.SEED_LILYPAD);
        Plant aNormal = makePlant(SeedType.SEED_PEASHOOTER);
        Plant aPumpkin = makePlant(SeedType.SEED_PUMPKINSHELL);
        Plant aFlyer = makePlant(SeedType.SEED_INSTANT_COFFEE);

        assertTrue(aLilypad.calcRenderOrder() < aNormal.calcRenderOrder(), "lilypad/flowerpot draw below the plant");
        assertTrue(aNormal.calcRenderOrder() < aPumpkin.calcRenderOrder(), "the pumpkin shell draws after its contents");
        assertTrue(aPumpkin.calcRenderOrder() < aFlyer.calcRenderOrder(), "flying plants (coffee bean) draw on top");

        // Same category: larger x ⇒ smaller offset ⇒ drawn behind (03-plant.md §4).
        Plant aLeft = makePlant(SeedType.SEED_PEASHOOTER);
        Plant aRight = makePlant(SeedType.SEED_PEASHOOTER);
        aRight.mX = aLeft.mX + 200;
        assertTrue(aRight.calcRenderOrder() < aLeft.calcRenderOrder());

        // Row dominates the layer offset (10 000 per row).
        Plant aRow1 = makePlant(SeedType.SEED_PEASHOOTER);
        Plant aRow2 = makePlant(SeedType.SEED_PEASHOOTER);
        aRow2.mRow = aRow1.mRow + 1;
        assertTrue(aRow2.calcRenderOrder() > aRow1.calcRenderOrder());
    }

    @Test
    void upgradeChainsMatchUpstream() {
        assertTrue(makePlant(SeedType.SEED_REPEATER).isUpgradableTo(SeedType.SEED_GATLINGPEA));
        assertTrue(makePlant(SeedType.SEED_MELONPULT).isUpgradableTo(SeedType.SEED_WINTERMELON));
        assertTrue(makePlant(SeedType.SEED_SUNFLOWER).isUpgradableTo(SeedType.SEED_TWINSUNFLOWER));
        assertTrue(makePlant(SeedType.SEED_SPIKEWEED).isUpgradableTo(SeedType.SEED_SPIKEROCK));
        assertTrue(makePlant(SeedType.SEED_MAGNETSHROOM).isUpgradableTo(SeedType.SEED_GOLD_MAGNET));
        assertTrue(makePlant(SeedType.SEED_FUMESHROOM).isUpgradableTo(SeedType.SEED_GLOOMSHROOM));
        assertFalse(makePlant(SeedType.SEED_PEASHOOTER).isUpgradableTo(SeedType.SEED_GATLINGPEA));
    }

    // =======================================================================================
    // Group 2 — behaviour on a real Board (needs the retail assets)
    // =======================================================================================

    @Test
    void plantInitializeSetsTheDocumentedDefaults() {
        Board aBoard = headlessBoard();
        Common.sRand(0x5EED_0001L);

        Plant aPlant = aBoard.addPlant(3, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);

        assertEquals(3, aPlant.mPlantCol);
        assertEquals(2, aPlant.mRow);
        assertEquals(2, aPlant.mStartRow);
        assertEquals(300, aPlant.mPlantHealth);
        assertEquals(300, aPlant.mPlantMaxHealth);
        assertEquals(80, aPlant.mWidth);
        assertEquals(80, aPlant.mHeight);
        assertEquals(5, aPlant.mNumFrames);
        assertTrue(aPlant.mFrameLength >= 12 && aPlant.mFrameLength <= 18, "RandRangeInt(12, 18)");
        assertEquals(PlantState.STATE_NOTREADY, aPlant.mState);
        assertFalse(aPlant.mDead);
        assertFalse(aPlant.mSquished);
        assertTrue(aPlant.mIsOnBoard);
        assertEquals(PlantSubClass.SUBCLASS_SHOOTER, aPlant.mSubclass);
        assertEquals(150, aPlant.mLaunchRate);
        assertTrue(aPlant.mLaunchCounter >= 0 && aPlant.mLaunchCounter <= 150, "RandRangeInt(0, mLaunchRate)");
        assertEquals(0, aPlant.mShootingCounter);
        assertTrue(aPlant.mBodyReanimID != ReanimationID.REANIMATIONID_NULL);
        assertTrue(aPlant.mHeadReanimID != ReanimationID.REANIMATIONID_NULL, "pea plants get a head reanim");
        assertEquals(aBoard.gridToPixelX(3, 2), aPlant.mX);
        assertEquals(aBoard.gridToPixelY(3, 2), aPlant.mY);
    }

    @Test
    void sunflowerProducesOneSunCoinPerLaunchCycle() {
        Board aBoard = headlessBoard();
        Plant aSunflower = aBoard.addPlant(1, 1, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);

        // Drive the production timer to the tick before it fires (Plant.cpp:1018-1075).
        aSunflower.mLaunchCounter = 1;
        aSunflower.update();

        assertEquals(1, aBoard.mCoins.mSize, "one sun per production cycle");
        Coin aSun = aBoard.mCoins.dataArrayGetItemAt(0);
        assertEquals(CoinType.COIN_SUN, aSun.mType);
        assertEquals(CoinMotion.COIN_MOTION_FROM_PLANT, aSun.mCoinMotion);
        assertTrue(aSunflower.mLaunchCounter >= 2350 && aSunflower.mLaunchCounter <= 2500,
                "reset to RandRangeInt(mLaunchRate - 150, mLaunchRate), got " + aSunflower.mLaunchCounter);

        // 100 ticks early the plant only flashes, it does not produce yet (Plant.cpp:1028-1032).
        aSunflower.mLaunchCounter = 100;
        aSunflower.update();
        assertEquals(1, aBoard.mCoins.mSize);
        assertTrue(aSunflower.mEatenFlashCountdown > 0, "the ready-flash is what the player sees");
    }

    @Test
    void twinSunflowerProducesTwoSunCoins() {
        Board aBoard = headlessBoard();
        Plant aTwin = aBoard.addPlant(1, 2, SeedType.SEED_TWINSUNFLOWER, SeedType.SEED_NONE);

        aTwin.mLaunchCounter = 1;
        aTwin.update();

        assertEquals(2, aBoard.mCoins.mSize, "twin sunflower drops two suns (Plant.cpp:1058-1062)");
        for (Coin aCoin : aBoard.mCoins) {
            assertEquals(CoinType.COIN_SUN, aCoin.mType);
        }
    }

    @Test
    void peashooterSpawnsAProjectileAfterItsShootingAnimation() {
        Board aBoard = headlessBoard();
        Plant aPeashooter = aBoard.addPlant(0, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        spawnTargetZombie(aBoard, 1, aPeashooter.mX + 300);

        aPeashooter.mLaunchCounter = 1;
        aPeashooter.update();

        assertEquals(35, aPeashooter.mShootingCounter, "the pea head plays anim_shooting (Plant.cpp:757)");
        assertEquals(0, aBoard.mProjectiles.mSize, "the pea leaves the head only when the animation ends");

        assertEquals(34, ticksUntilProjectile(aBoard, aPeashooter, 60), "mShootingCounter 35 fires when it reaches 1");
        Projectile aPea = aBoard.mProjectiles.dataArrayGetItemAt(0);
        assertEquals(ProjectileType.PROJECTILE_PEA, aPea.mProjectileType);
        assertEquals(1, aPea.mDamageRangeFlags);
        assertEquals(1, aPea.mRow);
    }

    @Test
    void snowPeaSpawnsSnowPeaProjectiles() {
        Board aBoard = headlessBoard();
        Plant aSnowPea = aBoard.addPlant(0, 3, SeedType.SEED_SNOWPEA, SeedType.SEED_NONE);
        spawnTargetZombie(aBoard, 3, aSnowPea.mX + 300);

        aSnowPea.mLaunchCounter = 1;
        aSnowPea.update();
        assertTrue(ticksUntilProjectile(aBoard, aSnowPea, 60) >= 0);

        assertEquals(ProjectileType.PROJECTILE_SNOWPEA, aBoard.mProjectiles.dataArrayGetItemAt(0).mProjectileType);
    }

    @Test
    void repeaterFiresItsSecondPeaLateInTheCycle() {
        Board aBoard = headlessBoard();
        Plant aRepeater = aBoard.addPlant(0, 0, SeedType.SEED_REPEATER, SeedType.SEED_NONE);
        spawnTargetZombie(aBoard, 0, aRepeater.mX + 300);

        aRepeater.mLaunchCounter = 1;
        aRepeater.update();
        assertEquals(26, aRepeater.mShootingCounter, "repeater uses the fast head animation (Plant.cpp:759-762)");
        assertTrue(ticksUntilProjectile(aBoard, aRepeater, 60) >= 0);
        assertEquals(1, aBoard.mProjectiles.mSize);

        // The second shot is scheduled when the launch counter reaches 25 (Plant.cpp:985-990).
        aRepeater.mLaunchCounter = 26;
        aRepeater.update();
        assertEquals(25, aRepeater.mLaunchCounter, "the extra shot triggers at exactly 25");
        assertEquals(26, aRepeater.mShootingCounter, "and restarts the head animation");

        int aTicks = 0;
        while (aBoard.mProjectiles.mSize == 1 && aTicks < 60) {
            aRepeater.update();
            aTicks++;
        }
        assertEquals(2, aBoard.mProjectiles.mSize, "a repeater fires a second pea per cycle");
    }

    @Test
    void wallnutUsesItsHealthPoolAndDiesOnlyBelowZero() {
        Board aBoard = headlessBoard();
        Plant aWallnut = aBoard.addPlant(1, 1, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);

        assertEquals(4000, aWallnut.mPlantHealth);
        assertEquals(4000, aWallnut.mPlantMaxHealth);

        // Exactly zero HP survives until the next damage event: Plant::Update kills on `< 0`
        // (Plant.cpp:2862) while Zombie::EatPlant kills on `<= 0` (03-plant.md §6.1).
        aWallnut.takeDamage(4000);
        assertEquals(0, aWallnut.mPlantHealth);
        aWallnut.update();
        assertFalse(aWallnut.mDead, "a plant at exactly 0 HP is still alive");

        aWallnut.takeDamage(1);
        aWallnut.update();
        assertTrue(aWallnut.mDead);
    }

    @Test
    void cherryBombExplodesAfterAHundredTicks() {
        Board aBoard = headlessBoard();
        Plant aCherry = aBoard.addPlant(2, 2, SeedType.SEED_CHERRYBOMB, SeedType.SEED_NONE);

        assertEquals(100, aCherry.mDoSpecialCountdown, "armed for 100 ticks (Plant.cpp:320-336)");

        for (int i = 0; i < 99; i++) {
            aCherry.update();
        }
        assertFalse(aCherry.mDead, "still ticking before the fuse runs out");
        assertEquals(1, aCherry.mDoSpecialCountdown);

        aCherry.update();
        assertTrue(aCherry.mDead, "DoSpecial fires at countdown 0 and the cherry bomb dies");
    }

    @Test
    void jalapenoBurnsItsRowAndFreezesIt() {
        Board aBoard = headlessBoard();
        Plant aJalapeno = aBoard.addPlant(3, 3, SeedType.SEED_JALAPENO, SeedType.SEED_NONE);

        for (int i = 0; i < 100; i++) {
            aJalapeno.update();
        }

        assertTrue(aJalapeno.mDead, "DoSpecial kills the jalapeno");
        assertEquals(20, aBoard.mIceTimer[3], "BurnRow sets mIceTimer[row] = 20 (Plant.cpp:4324)");
        assertEquals(0, aBoard.mIceTimer[2], "only the burned row is frozen");
    }

    @Test
    void potatoMineNeedsFifteenHundredTicksToRise() {
        Board aBoard = headlessBoard();
        Plant aMine = aBoard.addPlant(2, 4, SeedType.SEED_POTATOMINE, SeedType.SEED_NONE);

        assertEquals(1500, aMine.mStateCountdown, "15 s arming time (Plant.cpp:344-347)");
        assertEquals(PlantState.STATE_NOTREADY, aMine.mState);

        for (int i = 0; i < 1499; i++) {
            aMine.update();
        }
        assertEquals(PlantState.STATE_NOTREADY, aMine.mState, "not armed before 1500 ticks");
        assertEquals(1, aMine.mStateCountdown);

        aMine.update();
        assertEquals(PlantState.STATE_POTATO_RISING, aMine.mState, "the rise animation starts at countdown 0");

        int aTicks = 0;
        while (aMine.mState != PlantState.STATE_POTATO_ARMED && aTicks < 600) {
            aMine.update();
            aTicks++;
        }
        assertEquals(PlantState.STATE_POTATO_ARMED, aMine.mState);
        assertTrue(aMine.mLightReanimID != ReanimationID.REANIMATIONID_NULL, "the glow reanim is attached when arming");
        assertTrue(aMine.mBlinkCountdown >= 400 && aMine.mBlinkCountdown <= 4400, "400 + Rand(4000)");
    }

    @Test
    void chomperBitesAndKillsTheZombieInFrontOfIt() {
        Board aBoard = headlessBoard();
        Plant aChomper = aBoard.addPlant(1, 1, SeedType.SEED_CHOMPER, SeedType.SEED_NONE);
        assertEquals(PlantState.STATE_READY, aChomper.mState, "chompers start ready (Plant.cpp:410)");
        Zombie aZombie = spawnTargetZombie(aBoard, 1, aChomper.mX + 80);

        aChomper.update();
        assertEquals(PlantState.STATE_CHOMPER_BITING, aChomper.mState);
        assertEquals(70, aChomper.mStateCountdown, "70-tick bite windup (Plant.cpp:1754-1758)");

        for (int i = 0; i < 70; i++) {
            aChomper.update();
        }
        assertTrue(aZombie.mDead, "the chomper swallows its victim (DieWithLoot)");
        assertEquals(PlantState.STATE_CHOMPER_BITING_GOT_ONE, aChomper.mState);
    }

    @Test
    void squashLaunchesAtTheClosestZombieAndDies() {
        Board aBoard = headlessBoard();
        Plant aSquash = aBoard.addPlant(2, 2, SeedType.SEED_SQUASH, SeedType.SEED_NONE);
        Zombie aZombie = spawnTargetZombie(aBoard, 2, aSquash.mX + 60);

        // Plant.cpp:128: a freshly added squash starts in STATE_NOTREADY; the very first update
        // already leaves it (Plant.cpp:1487-1495: STATE_NOTREADY -> STATE_SQUASH_LOOK), so the
        // recorded list below only contains post-update states.
        assertEquals(PlantState.STATE_NOTREADY, aSquash.mState, "a fresh squash starts not-ready");

        List<Integer> aStates = new ArrayList<>();
        for (int i = 0; i < 400 && !aSquash.mDead; i++) {
            aSquash.update();
            if (aStates.isEmpty() || aStates.get(aStates.size() - 1) != aSquash.mState) {
                aStates.add(aSquash.mState);
            }
        }

        assertTrue(aSquash.mDead, "the squash always dies after landing");
        assertTrue(aStates.contains(PlantState.STATE_SQUASH_LOOK), aStates.toString());
        assertTrue(aStates.contains(PlantState.STATE_SQUASH_PRE_LAUNCH), aStates.toString());
        assertTrue(aStates.contains(PlantState.STATE_SQUASH_RISING), aStates.toString());
        assertTrue(aStates.contains(PlantState.STATE_SQUASH_FALLING), aStates.toString());
        assertTrue(aStates.contains(PlantState.STATE_SQUASH_DONE_FALLING), aStates.toString());
        assertTrue(aZombie.mDead, "DoSquashDamage deals 1800 damage to every zombie in the cell");
    }

    @Test
    void seededRandomStreamDrivesTheLaunchCounterDeterministically() {
        Board aBoard = headlessBoard();

        Common.sRand(0xBEEF_0001L);
        Plant aFirst = aBoard.addPlant(0, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        int aFirstCounter = aFirst.mLaunchCounter;
        int aFirstFrameLength = aFirst.mFrameLength;
        aBoard.mPlants.dataArrayFree(aFirst);

        Common.sRand(0xBEEF_0001L);
        Plant aSecond = aBoard.addPlant(0, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);

        assertEquals(aFirstCounter, aSecond.mLaunchCounter, "the launch counter comes from the seeded MTRand");
        assertEquals(aFirstFrameLength, aSecond.mFrameLength, "mFrameLength = RandRangeInt(12, 18)");
    }

    // =======================================================================================
    // Harness
    // =======================================================================================

    /** A plant built by hand: enough state for the pure geometry/classification helpers. */
    private static Plant makePlant(int theSeedType) {
        new LawnApp(); // GameObject.mApp comes from LawnApp.gLawnApp
        Plant aPlant = new Plant();
        aPlant.mSeedType = theSeedType;
        aPlant.mX = 120;
        aPlant.mY = 100;
        aPlant.mRow = 1;
        aPlant.mPlantCol = 1;
        aPlant.mWidth = 80;
        aPlant.mHeight = 80;
        aPlant.mPlantHealth = 300;
        aPlant.mPlantMaxHealth = 300;
        return aPlant;
    }

    private static LawnApp sApp;
    private static Path sSaveDir;

    /**
     * A real {@link Board} with a playable level, built on the retail assets: {@code LawnApp::Init()}
     * plus the loading thread that binds the real image/font/reanim tables, then a fresh Adventure
     * board (the same boot {@link GameBootSmokeTest} and {@code LawnAppTest.newRealAssetApp} drive by
     * hand). Skips the calling test when the assets are missing; a real failure now propagates.
     */
    private static Board headlessBoard() {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "no real PvZ assets available");

        if (sApp == null) {
            LawnApp anApp = new LawnApp();
            HeadlessTestApp.initialize(anApp);
            // -resdir/-savedir keep the boot off the working directory and out of the user's saves.
            anApp.setArgs(new String[] {
                    "pvz-portable", "-resdir", aRoot.get().toString(),
                    "-savedir", tempSaveDir().toString(),
            });
            anApp.init();

            // start() would run the whole blocking main loop; drive its two pieces by hand instead —
            // start the loading thread (as start() does) and pump frames until it is done.
            anApp.startLoadingThread();
            long aDeadline = System.nanoTime() + 180_000_000_000L;
            while (!anApp.mLoadingThreadCompleted.get() && System.nanoTime() < aDeadline) {
                anApp.updateFrames();
                try {
                    Thread.sleep(2);
                } catch (InterruptedException anInterrupted) {
                    // Leave the bound below to fail the test with its message rather than hang.
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            assertTrue(anApp.mLoadingThreadCompleted.get(), "resource loading must complete");

            // A fresh save directory has no profiles (the retail game shows the new-user dialog here);
            // create one exactly like that dialog does and continue with it.
            if (anApp.mPlayerInfo == null) {
                anApp.mPlayerInfo = anApp.mProfileMgr.addProfile("PlantTest");
            }
            sApp = anApp;
        }

        // Every GameObject captures mApp/mBoard from the process-wide LawnApp.gLawnApp when it is
        // constructed (GameObject.java:70-72, ported from GameObject.cpp:30-32) — the SeedBank does
        // so in the Board ctor and Board::InitLevel then dereferences it (SeedBank.java:211,
        // ported from SeedPacket.cpp:1167). Upstream has exactly one app for the whole process
        // (main.cpp:97) and its ctor clears mBoard (LawnApp.cpp:120), but the pure-helper tests
        // above build throwaway LawnApps, each of which re-points the global at a board-less app.
        // Restore BOTH process-wide globals (GameObject reads gLawnApp; PvzpFoley reads
        // gSexyAppBase, PvzpFoley.cpp:284) so the board and everything spawned on it bind to the app
        // that owns it, exactly like HeadlessTestApp.initialize and GameBootSmokeTest do.
        LawnApp.gLawnApp = sApp;
        SexyAppBase.current = sApp;

        sApp.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        sApp.makeNewBoard();
        Board aBoard = sApp.mBoard;
        assertNotNull(aBoard, "makeNewBoard creates the board");
        aBoard.initLevel();
        sApp.mGameScene = GameScenes.SCENE_PLAYING;
        return aBoard;
    }

    /** Save directory for the booted app, so {@code HeadlessTestApp}/{@code LawnApp} never touch the user's data. */
    private static Path tempSaveDir() {
        if (sSaveDir == null) {
            try {
                sSaveDir = Files.createDirectories(
                        Files.createTempDirectory("plant-test-userdir").resolve("userdata"));
            } catch (IOException anException) {
                throw new UncheckedIOException(anException);
            }
        }
        return sSaveDir;
    }

    /** Places a plain zombie in {@code theRow} at pixel x. */
    private static Zombie spawnTargetZombie(Board theBoard, int theRow, int theX) {
        Zombie aZombie = theBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, theRow, 0);
        assertNotNull(aZombie, "the board must accept a normal zombie");
        aZombie.mPosX = theX;
        aZombie.mX = theX;
        return aZombie;
    }

    /** Ticks the plant until a projectile exists, returning the tick count (-1 when it never fires). */
    private static int ticksUntilProjectile(Board theBoard, Plant thePlant, int theMaxTicks) {
        for (int i = 0; i < theMaxTicks; i++) {
            if (theBoard.mProjectiles.mSize > 0) {
                return i;
            }
            thePlant.update();
        }
        return theBoard.mProjectiles.mSize > 0 ? theMaxTicks : -1;
    }
}
