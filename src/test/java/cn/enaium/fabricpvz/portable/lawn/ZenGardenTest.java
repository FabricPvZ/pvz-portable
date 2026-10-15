/*
 * Headless tests for the ported ZenGarden (src/Lawn/ZenGarden.cpp).
 *
 * Covers the parts of the mini-game this class owns: the per-background pixel<->grid tables, the
 * plant-need state machine (water/fertilizer/bug spray/phonograph), watering/feeding/growth and the
 * coin payouts, the marigold production loop, the wheelbarrow flow, the sell-price table, the Zen
 * shop gates and the stinky-the-snail timers (including the unsigned wraparound arithmetic).
 *
 * The app is the {@link LawnEntityTestSupport.TestLawnApp} double, so reanimations/particles/audio
 * become recording stubs and no retail asset is needed. The clock is pinned to a fixed epoch so the
 * hour/day comparisons are deterministic.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

class ZenGardenTest {

    /** 2023-11-14 22:13:20 UTC - a pinned clock keeps the day/hour comparisons deterministic. */
    private static final long NOW = 1_700_000_000L;

    /**
     * A headless app with the real effect system/audio facades from
     * {@link cn.enaium.fabricpvz.portable.test.HeadlessTestApp} (coins and reanimations need the real holders)
     * that records the foley/sample calls the garden makes.
     */
    static class RecordingLawnApp extends LawnApp {
        final List<Integer> mFoleys = new ArrayList<>();
        final List<Long> mSamples = new ArrayList<>();

        @Override
        public void playFoley(int theFoleyType) {
            mFoleys.add(theFoleyType);
        }

        @Override
        public void playSample(long theSoundNum) {
            mSamples.add(theSoundNum);
        }
    }

    private RecordingLawnApp mApp;
    private Board mBoard;
    private ZenGarden mZen;

    @BeforeEach
    void setUp() {
        mApp = new RecordingLawnApp();
        mApp.mGameMode = GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN;
        mApp.mGameScene = GameScenes.SCENE_PLAYING;
        mApp.mAppRandSeed = 0x5EED;
        mApp.mPlayerInfo = new PlayerInfo();
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(mApp);
        // The seed bank measures these two images; give them their own placeholder bitmaps.
        Resources.setImageById(ImageID.IMAGE_SEEDBANK, new MemoryImage(mApp));
        Resources.setImageById(ImageID.IMAGE_SEEDS, new MemoryImage(mApp));

        mBoard = new Board(mApp);
        mBoard.mLevel = 1;
        mApp.mBoard = mBoard;
        mBoard.mBackground = BackgroundType.BACKGROUND_GREENHOUSE;
        mBoard.mChallenge = new Challenge(mBoard);

        mZen = new ZenGarden();
        mZen.mBoard = mBoard;
        mZen.mGardenType = GardenType.GARDEN_MAIN;
        mZen.mNowTime = NOW;
        mZen.mNowTM = mApp.getLocalTime(NOW);
        mApp.mZenGarden = mZen;
    }

    // -------------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------------

    /** Appends a fresh potted-plant record (PlayerInfo::mPottedPlant) and returns it. */
    private PottedPlant newPottedPlant(int theSeedType, int thePlantAge, int theWhichZenGarden) {
        int aIndex = mApp.mPlayerInfo.mNumPottedPlants;
        PottedPlant aPottedPlant = mApp.mPlayerInfo.mPottedPlant[aIndex];
        aPottedPlant.initializePottedPlant(theSeedType);
        aPottedPlant.mWhichZenGarden = theWhichZenGarden;
        aPottedPlant.mPlantAge = thePlantAge;
        mApp.mPlayerInfo.mNumPottedPlants++;
        return aPottedPlant;
    }

    private int pottedPlantIndex(PottedPlant thePottedPlant) {
        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            if (mApp.mPlayerInfo.mPottedPlant[i] == thePottedPlant) {
                return i;
            }
        }
        throw new IllegalStateException("potted plant not registered");
    }

    /** A live board plant bound to the given potted-plant record (no plantInitialize side effects). */
    private Plant newGardenPlant(PottedPlant thePottedPlant, int theSeedType) {
        Plant aPlant = mBoard.mPlants.dataArrayAlloc();
        aPlant.mBoard = mBoard;
        aPlant.mSeedType = theSeedType;
        aPlant.mPottedPlantIndex = pottedPlantIndex(thePottedPlant);
        aPlant.mX = 100;
        aPlant.mY = 200;
        aPlant.mPlantCol = 0;
        aPlant.mRow = 0;
        // A live body-reanimation id: setPlantAnimSpeed reads its mAnimRate.
        aPlant.mBodyReanimID = mApp.reanimationGetID(
                mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_FLOWER_POT));
        return aPlant;
    }

    private int countCoins(int theCoinType) {
        return mBoard.countCoinByType(theCoinType);
    }

    // -------------------------------------------------------------------------------------------
    // Grid model
    // -------------------------------------------------------------------------------------------

    /**
     * ZenGarden.cpp:1973-2037: the placement tables are per background (greenhouse 8x4, mushroom and
     * aquarium share the 8x1 grid) and both directions of the mapping use the same 80x85 cell.
     */
    @Test
    void pixelToGridMappingFollowsTheBackgroundTable() {
        assertEquals(32, mZen.getSpecialGridPlacements().length);
        assertEquals(73, mZen.gridToPixelX(0, 0));
        assertEquals(73, mZen.gridToPixelY(0, 0));
        assertEquals(691, mZen.gridToPixelX(7, 3));
        assertEquals(368, mZen.gridToPixelY(7, 3));
        assertEquals(0, mZen.pixelToGridX(73, 73));
        assertEquals(0, mZen.pixelToGridY(100, 100));
        assertEquals(7, mZen.pixelToGridX(700, 100));
        assertEquals(0, mZen.pixelToGridY(700, 100));
        assertEquals(-1, mZen.pixelToGridX(10, 10));
        assertEquals(-1, mZen.pixelToGridY(10, 10));
        assertEquals(-1, mZen.gridToPixelX(8, 0));
        assertEquals(-1, mZen.gridToPixelY(0, 4));

        mBoard.mBackground = BackgroundType.BACKGROUND_MUSHROOM_GARDEN;
        assertEquals(8, mZen.getSpecialGridPlacements().length);
        assertEquals(110, mZen.gridToPixelX(0, 0));
        assertEquals(441, mZen.gridToPixelY(0, 0));
        assertEquals(552, mZen.gridToPixelX(7, 0));
        assertEquals(0, mZen.pixelToGridX(150, 500));
        assertEquals(0, mZen.pixelToGridY(150, 500));
        assertEquals(-1, mZen.gridToPixelX(0, 1));

        mBoard.mBackground = BackgroundType.BACKGROUND_ZOMBIQUARIUM;
        assertEquals(622, mZen.gridToPixelX(3, 0));
        assertEquals(120, mZen.gridToPixelY(3, 0));
        assertEquals(7, mZen.pixelToGridX(510, 420));
        assertEquals(0, mZen.pixelToGridY(510, 420));

        // No garden background has a placement table: PVZP_ASSERT(false) upstream.
        mBoard.mBackground = BackgroundType.BACKGROUND_1_DAY;
        assertThrows(IllegalStateException.class, () -> mZen.pixelToGridX(73, 73));
    }

    // -------------------------------------------------------------------------------------------
    // Plant needs
    // -------------------------------------------------------------------------------------------

    /** ZenGarden.cpp:843-860: nocturnal plants in the main garden and wheelbarrow plants need nothing. */
    @Test
    void getPlantsNeedIsNoneForNocturnalMainGardenAndWheelbarrowPlants() {
        PottedPlant aNocturnal = newPottedPlant(SeedType.SEED_PUFFSHROOM, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        aNocturnal.mLastWateredTime = NOW - 1000;
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, mZen.getPlantsNeed(aNocturnal));

        PottedPlant aTransport = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_WHEELBARROW);
        aTransport.mLastWateredTime = NOW - 1000;
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, mZen.getPlantsNeed(aTransport));
    }

    /** ZenGarden.cpp:872-886: a plant that still owes feedings wants water once the hour is stale. */
    @Test
    void getPlantsNeedAsksForWaterUntilTheFeedingsArePayed() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SMALL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mFeedingsPerGrow = 4;
        aPottedPlant.mTimesFed = 2;
        aPottedPlant.mLastWateredTime = NOW - 100;
        assertEquals(PottedPlantNeed.PLANTNEED_WATER, mZen.getPlantsNeed(aPottedPlant));
        assertTrue(mZen.plantsNeedWater());
        assertTrue(mZen.plantCanBeWatered(newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER)));

        // Freshly watered (within 15s) and inside the feeding budget: nothing to do.
        aPottedPlant.mLastWateredTime = NOW - 5;
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, mZen.getPlantsNeed(aPottedPlant));

        // A plant without a potted-plant index can never be watered.
        Plant aLoosePlant = new Plant();
        aLoosePlant.mPottedPlantIndex = -1;
        assertFalse(mZen.plantCanBeWatered(aLoosePlant));
    }

    /** ZenGarden.cpp:866-870: a recent fertilization or a need fulfilled today short-circuits to none. */
    @Test
    void getPlantsNeedIsNoneWhileFertilizedOrFulfilledToday() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_MEDIUM,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mTimesFed = 9;
        aPottedPlant.mLastWateredTime = NOW - 100;

        aPottedPlant.mLastFertilizedTime = NOW - 30;
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, mZen.getPlantsNeed(aPottedPlant));
        assertTrue(mZen.wasPlantFertilizedInLastHour(aPottedPlant));

        aPottedPlant.mLastFertilizedTime = 0;
        aPottedPlant.mLastNeedFulfilledTime = NOW - 30;
        assertTrue(mZen.wasPlantNeedFulfilledToday(aPottedPlant));
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, mZen.getPlantsNeed(aPottedPlant));
    }

    /** ZenGarden.cpp:886-908: grown plants want fertilizer, then whatever need the plant cached. */
    @Test
    void getPlantsNeedGrowsIntoFertilizerAndTheStoredNeed() {
        PottedPlant aGrowing = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_MEDIUM,
                GardenType.GARDEN_MAIN);
        aGrowing.mFeedingsPerGrow = 3;
        aGrowing.mTimesFed = 3;
        aGrowing.mLastWateredTime = NOW - 100;
        assertEquals(PottedPlantNeed.PLANTNEED_FERTILIZER, mZen.getPlantsNeed(aGrowing));

        PottedPlant aFull = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        aFull.mFeedingsPerGrow = 3;
        aFull.mTimesFed = 3;
        aFull.mLastWateredTime = NOW - 100;
        aFull.mPlantNeed = PottedPlantNeed.PLANTNEED_BUGSPRAY;
        assertEquals(PottedPlantNeed.PLANTNEED_BUGSPRAY, mZen.getPlantsNeed(aFull));

        aFull.mPlantNeed = PottedPlantNeed.PLANTNEED_NONE;
        assertEquals(PottedPlantNeed.PLANTNEED_WATER, mZen.getPlantsNeed(aFull));
    }

    /** ZenGarden.cpp:886-903: aquatic plants only grow in the aquarium and store their own need. */
    @Test
    void getPlantsNeedHandlesAquaticPlantsPerGarden() {
        PottedPlant aSmall = newPottedPlant(SeedType.SEED_LILYPAD, PottedPlantAge.PLANTAGE_SMALL,
                GardenType.GARDEN_MAIN);
        aSmall.mLastWateredTime = NOW - 100;
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, mZen.getPlantsNeed(aSmall));

        aSmall.mWhichZenGarden = GardenType.GARDEN_AQUARIUM;
        assertEquals(PottedPlantNeed.PLANTNEED_FERTILIZER, mZen.getPlantsNeed(aSmall));

        PottedPlant aFull = newPottedPlant(SeedType.SEED_TANGLEKELP, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_AQUARIUM);
        aFull.mLastWateredTime = NOW - 100;
        aFull.mPlantNeed = PottedPlantNeed.PLANTNEED_PHONOGRAPH;
        assertEquals(PottedPlantNeed.PLANTNEED_PHONOGRAPH, mZen.getPlantsNeed(aFull));
    }

    /** ZenGarden.cpp:597-646: the aggregates that drive the watering can hint and the store advice. */
    @Test
    void needAggregatesCountWaterAndFertilizer() {
        PottedPlant aThirsty = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SMALL,
                GardenType.GARDEN_MAIN);
        aThirsty.mFeedingsPerGrow = 5;
        aThirsty.mLastWateredTime = NOW - 100;

        PottedPlant aHungry = newPottedPlant(SeedType.SEED_SUNFLOWER, PottedPlantAge.PLANTAGE_MEDIUM,
                GardenType.GARDEN_MAIN);
        aHungry.mFeedingsPerGrow = 1;
        aHungry.mTimesFed = 1;
        aHungry.mLastWateredTime = NOW - 100;

        PottedPlant aSprout = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SPROUT,
                GardenType.GARDEN_MAIN);
        aSprout.mLastWateredTime = NOW;

        assertTrue(mZen.plantsNeedWater());
        assertEquals(1, mZen.countPlantsNeedingFertilizer());
        assertFalse(mZen.allPlantsHaveBeenFertilized()); // aSprout is still a sprout

        // All three plants reach full age with the need fulfilled today (ZenGarden.cpp:866-870).
        aThirsty.mPlantAge = PottedPlantAge.PLANTAGE_FULL;
        aThirsty.mTimesFed = 5;
        aThirsty.mLastNeedFulfilledTime = NOW - 10;
        aHungry.mPlantAge = PottedPlantAge.PLANTAGE_FULL;
        aHungry.mLastNeedFulfilledTime = NOW - 10;
        aSprout.mPlantAge = PottedPlantAge.PLANTAGE_FULL;
        aSprout.mLastNeedFulfilledTime = NOW - 10;
        assertFalse(mZen.plantsNeedWater());
        assertEquals(0, mZen.countPlantsNeedingFertilizer());
        assertTrue(mZen.allPlantsHaveBeenFertilized());
    }

    /**
     * ZenGarden.cpp:800-836: only full plants whose watering is stale by a full day refresh their
     * need; aquatic plants roll a new need, the rest just reset their feeding counter.
     */
    @Test
    void refreshPlantNeedsOnlyTouchesFullPlantsWhoseWateringIsADayOld() {
        mApp.mPlayerInfo.mNumPottedPlants = 0;
        PottedPlant aAquatic = newPottedPlant(SeedType.SEED_LILYPAD, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_AQUARIUM);
        PottedPlant aTerrestrial = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        PottedPlant aSprout = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SPROUT,
                GardenType.GARDEN_MAIN);
        for (PottedPlant aPottedPlant : new PottedPlant[] { aAquatic, aTerrestrial, aSprout }) {
            aPottedPlant.mLastWateredTime = NOW - 100_000;
            aPottedPlant.mTimesFed = 7;
        }
        aTerrestrial.mPlantNeed = PottedPlantNeed.PLANTNEED_BUGSPRAY;

        assertTrue(mZen.plantShouldRefreshNeed(aAquatic));
        mZen.updatePlantNeeds();

        // updatePlantNeeds re-reads the clock (ZenGarden.cpp:828); the aquatic plant is re-watered now.
        assertEquals(mZen.mNowTime, aAquatic.mLastWateredTime);
        assertFalse(mZen.plantShouldRefreshNeed(aAquatic));
        assertTrue(aAquatic.mPlantNeed >= PottedPlantNeed.PLANTNEED_BUGSPRAY
                && aAquatic.mPlantNeed <= PottedPlantNeed.PLANTNEED_PHONOGRAPH);
        assertEquals(0, aTerrestrial.mTimesFed);
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, aTerrestrial.mPlantNeed);
        assertEquals(7, aSprout.mTimesFed);
        assertEquals(PottedPlantAge.PLANTAGE_SPROUT, aSprout.mPlantAge);
    }

    // -------------------------------------------------------------------------------------------
    // Watering / feeding / growth / coins
    // -------------------------------------------------------------------------------------------

    /** ZenGarden.cpp:648-673: watering counts a feeding, stamps the clock and drops a silver coin. */
    @Test
    void plantWateredFeedsThePlantAndDropsASilverCoin() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SMALL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mPlantNeed = PottedPlantNeed.PLANTNEED_NONE;
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER);

        mZen.plantWatered(aPlant);

        assertEquals(1, aPottedPlant.mTimesFed);
        assertTrue(aPottedPlant.mLastWateredTime >= NOW - 9 && aPottedPlant.mLastWateredTime <= NOW);
        assertEquals(1, countCoins(CoinType.COIN_SILVER));
        assertEquals(1, mApp.mFoleys.size());
    }

    /** ZenGarden.cpp:529-578: fertilizing grows the plant a stage and pays gold/diamond coins. */
    @Test
    void plantFertilizedGrowsThePlantAndPaysCoins() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_MEDIUM,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mPlantNeed = PottedPlantNeed.PLANTNEED_FERTILIZER;
        aPottedPlant.mTimesFed = 4;
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER);

        mZen.plantFertilized(aPlant);

        assertEquals(PottedPlantAge.PLANTAGE_FULL, aPottedPlant.mPlantAge);
        assertEquals(NOW, aPottedPlant.mLastFertilizedTime);
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, aPottedPlant.mPlantNeed);
        assertEquals(0, aPottedPlant.mTimesFed);
        assertEquals(100, aPlant.mStateCountdown);
        assertEquals(2, countCoins(CoinType.COIN_DIAMOND));
    }

    /** ZenGarden.cpp:566-575: a marigold that reaches full age pays a single diamond. */
    @Test
    void plantFertilizedPaysAMarigoldDiamond() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_MARIGOLD, PottedPlantAge.PLANTAGE_MEDIUM,
                GardenType.GARDEN_MAIN);
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_MARIGOLD);

        mZen.plantFertilized(aPlant);

        assertEquals(PottedPlantAge.PLANTAGE_FULL, aPottedPlant.mPlantAge);
        assertEquals(1, countCoins(CoinType.COIN_DIAMOND));
    }

    /** ZenGarden.cpp:580-595: fulfilling a need clears the need and pays gold (extra when exotic). */
    @Test
    void plantFulfillNeedPaysGoldCoins() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mPlantNeed = PottedPlantNeed.PLANTNEED_BUGSPRAY;
        aPottedPlant.mTimesFed = 3;
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER);

        mZen.plantFulfillNeed(aPlant);

        assertEquals(NOW, aPottedPlant.mLastNeedFulfilledTime);
        assertEquals(PottedPlantNeed.PLANTNEED_NONE, aPottedPlant.mPlantNeed);
        assertEquals(0, aPottedPlant.mTimesFed);
        assertEquals(1, countCoins(CoinType.COIN_GOLD));

        PottedPlant aAquatic = newPottedPlant(SeedType.SEED_LILYPAD, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_AQUARIUM);
        Plant anAquaticPlant = newGardenPlant(aAquatic, SeedType.SEED_LILYPAD);
        mZen.plantFulfillNeed(anAquaticPlant);
        assertEquals(4, countCoins(CoinType.COIN_GOLD));
    }

    /**
     * ZenGarden.cpp:2228-2252: the hourly production loop pays a coin when the launch counter runs
     * out, and chocolate makes the counter tick twice per update.
     */
    @Test
    void plantUpdateProductionPaysACoinAndChocolateDoublesTheRate() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_MARIGOLD, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mLastNeedFulfilledTime = NOW;
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_MARIGOLD);
        aPlant.mLaunchCounter = 1;

        mZen.plantUpdateProduction(aPlant);

        assertEquals(1, countCoins(CoinType.COIN_SILVER) + countCoins(CoinType.COIN_GOLD));
        assertTrue(aPlant.mLaunchCounter > 1000); // PlantSetLaunchCounter re-armed between 1800 and 15000

        aPlant.mLaunchCounter = 2;
        aPottedPlant.mLastChocolateTime = NOW;
        mZen.plantUpdateProduction(aPlant);

        assertEquals(2, countCoins(CoinType.COIN_SILVER) + countCoins(CoinType.COIN_GOLD));
        assertTrue(mZen.plantHighOnChocolate(aPottedPlant));
        assertEquals(0, mZen.plantGetMinutesSinceHappy(aPlant));

        aPottedPlant.mLastChocolateTime = NOW - 301;
        assertFalse(mZen.plantHighOnChocolate(aPottedPlant));
    }

    /** ZenGarden.cpp:1017-1023: chocolate stamps the clock and shortens the production cooldown. */
    @Test
    void feedChocolateToPlantStampsTheClock() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER);

        mZen.feedChocolateToPlant(aPlant);

        assertEquals(NOW, aPottedPlant.mLastChocolateTime);
        assertEquals(60, aPlant.mLaunchCounter);
        assertTrue(mApp.mEffectSystem.mParticleHolder.mParticleSystems.mSize > 0);
        assertFalse(mZen.plantCanHaveChocolate(aPlant)); // chocolate is already in effect
    }

    /** ZenGarden.cpp:310-314: a full plant only accepts chocolate with today's need fulfilled. */
    @Test
    void plantCanHaveChocolateRequiresAFullHappyPlant() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER);
        aPottedPlant.mLastNeedFulfilledTime = NOW - 10;

        assertTrue(mZen.plantCanHaveChocolate(aPlant));

        aPottedPlant.mPlantAge = PottedPlantAge.PLANTAGE_MEDIUM;
        assertFalse(mZen.plantCanHaveChocolate(aPlant));
    }

    /** ZenGarden.cpp:407-453: the sell price table per age and plant class. */
    @Test
    void getPlantSellPriceMatchesUpstream() {
        assertEquals(150, sellPrice(SeedType.SEED_MARIGOLD, PottedPlantAge.PLANTAGE_SPROUT));
        assertEquals(200, sellPrice(SeedType.SEED_MARIGOLD, PottedPlantAge.PLANTAGE_SMALL));
        assertEquals(250, sellPrice(SeedType.SEED_MARIGOLD, PottedPlantAge.PLANTAGE_MEDIUM));
        assertEquals(300, sellPrice(SeedType.SEED_MARIGOLD, PottedPlantAge.PLANTAGE_FULL));
        assertEquals(150, sellPrice(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SPROUT));
        assertEquals(300, sellPrice(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SMALL));
        assertEquals(500, sellPrice(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_MEDIUM));
        assertEquals(800, sellPrice(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL));
        assertEquals(1000, sellPrice(SeedType.SEED_LILYPAD, PottedPlantAge.PLANTAGE_FULL));
        assertEquals(1000, sellPrice(SeedType.SEED_PUFFSHROOM, PottedPlantAge.PLANTAGE_FULL));
    }

    private int sellPrice(int theSeedType, int thePlantAge) {
        mApp.mPlayerInfo.mNumPottedPlants = 0;
        PottedPlant aPottedPlant = newPottedPlant(theSeedType, thePlantAge, GardenType.GARDEN_MAIN);
        Plant aPlant = new Plant();
        aPlant.mSeedType = theSeedType;
        aPlant.mPottedPlantIndex = pottedPlantIndex(aPottedPlant);
        return mZen.getPlantSellPrice(aPlant);
    }

    /** ZenGarden.cpp:1190-1277: the potted-plant draw offsets used by the garden and the cursor. */
    @Test
    void pottedDrawOffsetsMatchTheUpstreamTable() {
        assertEquals(50.0f, mZen.plantPottedDrawHeightOffset(SeedType.SEED_GRAVEBUSTER, 1.0f));
        assertEquals(42.5f, mZen.plantPottedDrawHeightOffset(SeedType.SEED_GRAVEBUSTER, 0.5f));
        assertEquals(-10.0f, mZen.plantPottedDrawHeightOffset(SeedType.SEED_LILYPAD, 1.0f));
        assertEquals(3.0f, mZen.plantPottedDrawHeightOffset(SeedType.SEED_CABBAGEPULT, 1.0f));
        assertEquals(-13.0f, mZen.plantPottedDrawHeightOffset(SeedType.SEED_SPIKEWEED, 1.0f));
        assertEquals(0.0f, mZen.plantPottedDrawHeightOffset(SeedType.SEED_PLANTERN, 1.0f));
        assertEquals(0.0f, mZen.plantPottedDrawHeightOffset(SeedType.SEED_WALLNUT, 1.0f));

        PottedPlant aPotatoMine = new PottedPlant();
        aPotatoMine.initializePottedPlant(SeedType.SEED_POTATOMINE);
        aPotatoMine.mFacing = PottedPlant.FacingDirection.FACING_LEFT;
        assertEquals(-6, ZenGarden.zenPlantOffsetX(aPotatoMine));
        aPotatoMine.mFacing = PottedPlant.FacingDirection.FACING_RIGHT;
        assertEquals(0, ZenGarden.zenPlantOffsetX(aPotatoMine));
    }

    // -------------------------------------------------------------------------------------------
    // Placing / moving plants
    // -------------------------------------------------------------------------------------------

    /** ZenGarden.cpp:201-268: putting a sprout down creates the flower pot plus the plant itself. */
    @Test
    void placePottedPlantPutsThePotAndThePlant() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_MARIGOLD, PottedPlantAge.PLANTAGE_SPROUT,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mX = 2;
        aPottedPlant.mY = 1;
        aPottedPlant.mDrawVariation = DrawVariation.VARIATION_NORMAL;

        Plant aPlant = mZen.placePottedPlant(pottedPlantIndex(aPottedPlant));

        assertEquals(2, mBoard.mPlants.mSize);
        assertEquals(SeedType.SEED_SPROUT, aPlant.mSeedType);
        assertEquals(0, aPlant.mPottedPlantIndex);
        assertEquals(mZen.gridToPixelX(2, 1), aPlant.mX);
        assertEquals(mZen.gridToPixelY(2, 1), aPlant.mY);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0, aPlant.mY + 1), aPlant.mRenderOrder);
    }

    /** ZenGarden.cpp:205-212: the aquarium garden plants straight into the water, without a pot. */
    @Test
    void placePottedPlantSkipsThePotInTheAquarium() {
        mZen.mGardenType = GardenType.GARDEN_AQUARIUM;
        mBoard.mBackground = BackgroundType.BACKGROUND_ZOMBIQUARIUM;
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_LILYPAD, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_AQUARIUM);
        aPottedPlant.mDrawVariation = DrawVariation.VARIATION_NORMAL;

        Plant aPlant = mZen.placePottedPlant(pottedPlantIndex(aPottedPlant));

        assertEquals(1, mBoard.mPlants.mSize);
        assertEquals(SeedType.SEED_LILYPAD, aPlant.mSeedType);
    }

    /** ZenGarden.cpp:385-405: addPottedPlant copies the record, finds a spot and pays the effects. */
    @Test
    void addPottedPlantRegistersARecordAtAFreeSpot() {
        PottedPlant aSource = new PottedPlant();
        aSource.initializePottedPlant(SeedType.SEED_SUNFLOWER);

        mZen.addPottedPlant(aSource);

        assertEquals(1, mApp.mPlayerInfo.mNumPottedPlants);
        PottedPlant aStored = mApp.mPlayerInfo.mPottedPlant[0];
        assertEquals(SeedType.SEED_SUNFLOWER, aStored.mSeedType);
        assertEquals(aSource.mFeedingsPerGrow, aStored.mFeedingsPerGrow);
        assertEquals(aSource.mFacing, aStored.mFacing);
        assertEquals(aSource.mDrawVariation, aStored.mDrawVariation);
        assertEquals(GardenType.GARDEN_MAIN, aStored.mWhichZenGarden);
        assertEquals(0, aStored.mLastWateredTime);
        assertTrue(aStored.mX >= 0 && aStored.mX < ZenGarden.ZEN_MAX_GRIDSIZE_X);
        assertTrue(aStored.mY >= 0 && aStored.mY < ZenGarden.ZEN_MAX_GRIDSIZE_Y);
    }

    /** ZenGarden.cpp:348-383: occupied cells and the Crazy Dave corner are never picked again. */
    @Test
    void findOpenZenGardenSpotSkipsOccupiedCells() {
        mApp.mPlayerInfo.mNumPottedPlants = 0;
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mX = 0;
        aPottedPlant.mY = 0;

        int[] aSpot = new int[2];
        for (int i = 0; i < 64; i++) {
            mZen.findOpenZenGardenSpot(aSpot);
            assertFalse(aSpot[0] == 0 && aSpot[1] == 0);
        }

        mApp.mCrazyDaveMessageIndex = 2100;
        for (int i = 0; i < 64; i++) {
            mZen.findOpenZenGardenSpot(aSpot);
            assertTrue(aSpot[0] >= 2 && aSpot[1] >= 1);
        }
    }

    /** ZenGarden.cpp:1950-1971: the wheelbarrow round-trips the potted-plant state. */
    @Test
    void wheelbarrowMovesThePlantOutOfTheGarden() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mX = 3;
        aPottedPlant.mY = 2;
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER);
        assertNull(mZen.getPottedPlantInWheelbarrow());

        mZen.mouseDownWithEmptyWheelBarrow(aPlant);

        assertEquals(GardenType.GARDEN_WHEELBARROW, aPottedPlant.mWhichZenGarden);
        assertEquals(0, aPottedPlant.mX);
        assertEquals(0, aPottedPlant.mY);
        assertTrue(aPlant.mDead);
        assertEquals(aPottedPlant, mZen.getPottedPlantInWheelbarrow());
        assertFalse(mApp.mFoleys.isEmpty());
    }

    /** ZenGarden.cpp:1929-1933: the aquarium refuses terrestrial plants. */
    @Test
    void aquariumRejectsTerrestrialPlantsFromTheWheelbarrow() {
        mZen.mGardenType = GardenType.GARDEN_AQUARIUM;
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_WHEELBARROW);

        mZen.mouseDownWithFullWheelBarrow(mZen.gridToPixelX(0, 0), mZen.gridToPixelY(0, 0));

        assertEquals(GardenType.GARDEN_WHEELBARROW, aPottedPlant.mWhichZenGarden);
        assertEquals(0, mBoard.mPlants.mSize);
        assertEquals(0, mApp.mPlayerInfo.mPottedPlant[0].mX);
    }

    // -------------------------------------------------------------------------------------------
    // Garden bookkeeping
    // -------------------------------------------------------------------------------------------

    /** ZenGarden.cpp:321-346: 32 main-garden records fill the garden; loot needs a finished adventure. */
    @Test
    void isZenGardenFullAndLootGating() {
        mApp.mPlayerInfo.mFinishedAdventure = 1;
        assertFalse(mZen.isZenGardenFull(true)); // 0 dropped presents + 0 potted plants
        assertTrue(mZen.canDropPottedPlantLoot());

        for (int i = 0; i < ZenGarden.ZEN_MAX_GRIDSIZE_X * ZenGarden.ZEN_MAX_GRIDSIZE_Y; i++) {
            newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL, GardenType.GARDEN_MAIN);
        }

        assertTrue(mZen.isZenGardenFull(true));
        assertFalse(mZen.canDropPottedPlantLoot());

        mApp.mPlayerInfo.mFinishedAdventure = 0;
        assertFalse(mZen.canDropPottedPlantLoot());
    }

    /** ZenGarden.cpp:2410-2424: the random seed never rolls marigold or the flower pot. */
    @Test
    void pickRandomSeedTypeSkipsMarigoldAndFlowerPot() {
        for (int i = 0; i < 200; i++) {
            int aSeedType = ZenGarden.pickRandomSeedType();
            assertTrue(aSeedType >= 0 && aSeedType < 40);
            assertNotEquals(SeedType.SEED_MARIGOLD, aSeedType);
            assertNotEquals(SeedType.SEED_FLOWERPOT, aSeedType);
        }
    }

    /** ZenGarden.cpp:316-319: chocolate loot needs stinky and a stock below ten. */
    @Test
    void canDropChocolateNeedsStinkyAndStock() {
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = (int) NOW;
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] = PlayerInfo.PURCHASE_COUNT_OFFSET + 9;
        assertTrue(mZen.canDropChocolate());

        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] = PlayerInfo.PURCHASE_COUNT_OFFSET + 10;
        assertFalse(mZen.canDropChocolate());

        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = 0;
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] = PlayerInfo.PURCHASE_COUNT_OFFSET;
        assertFalse(mZen.canDropChocolate());
    }

    /** ZenGarden.cpp:2327-2363: stinky's timers are unsigned 32-bit deltas and wrap like upstream. */
    @Test
    void stinkyTimersUseUnsignedDeltas() {
        mZen.mNowTime = NOW;
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = (int) NOW;
        assertTrue(mZen.shouldStinkyBeAwake());

        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = (int) (NOW - 200);
        assertFalse(mZen.shouldStinkyBeAwake());

        mApp.mPlayerInfo.mLastStinkyChocolateTime = (int) NOW;
        assertTrue(mZen.isStinkyHighOnChocolate());
        assertTrue(mZen.shouldStinkyBeAwake()); // chocolate keeps stinky up

        mApp.mPlayerInfo.mLastStinkyChocolateTime = (int) (NOW - 3601);
        assertFalse(mZen.isStinkyHighOnChocolate());

        // Wrapped clock: 100 - 0xFFFFFF00 (mod 2^32) is 356, still "less than an hour ago".
        mZen.mNowTime = 0x100000000L + 100;
        mApp.mPlayerInfo.mLastStinkyChocolateTime = (int) 0xFFFFFF00L;
        assertTrue(mZen.isStinkyHighOnChocolate());
    }

    /** ZenGarden.cpp:1519-1523: resetting stinky's timers re-arms the sleep countdown. */
    @Test
    void resetStinkyTimersReArmsTheSleep() {
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = (int) NOW;
        mApp.mPlayerInfo.mLastStinkyChocolateTime = (int) NOW;

        mZen.resetStinkyTimers();

        assertEquals(2, mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL]);
        assertEquals(0, mApp.mPlayerInfo.mLastStinkyChocolateTime);
        assertFalse(mZen.shouldStinkyBeAwake());
    }

    /** ZenGarden.cpp:1279-1329: no stinky without the purchase, and the pet state queries gate on it. */
    @Test
    void stinkyIsAbsentWithoutThePurchase() {
        assertFalse(mZen.hasPurchasedStinky());
        mZen.addStinky();
        assertNull(mZen.getStinky());
        assertFalse(mZen.isStinkySleeping());

        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = (int) NOW;
        assertTrue(mZen.hasPurchasedStinky());
        mZen.addStinky();
        assertEquals(GridItemType.GRIDITEM_STINKY, mZen.getStinky().mGridItemType);
        assertEquals(1, mApp.mPlayerInfo.mHasSeenStinky);
    }

    /**
     * ZenGarden.cpp:910-1015 + :1025-1085 + :1723-1741: picking the fertilizer up on a needy plant
     * spawns the tool reanimation, and when that finishes it fertilizes the plant under it.
     */
    @Test
    void feedingToolFertilizesThePottedPlantUnderIt() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_MEDIUM,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mX = 2;
        aPottedPlant.mY = 1;
        aPottedPlant.mDrawVariation = DrawVariation.VARIATION_NORMAL;
        aPottedPlant.mLastWateredTime = NOW - 100;
        aPottedPlant.mFeedingsPerGrow = 3;
        Plant aPlant = mZen.placePottedPlant(pottedPlantIndex(aPottedPlant));
        aPlant.mHighlighted = true;

        // Pay the feedings so the plant graduates to the fertilizer need (ZenGarden.cpp:897-900).
        aPottedPlant.mTimesFed = aPottedPlant.mFeedingsPerGrow;
        assertEquals(PottedPlantNeed.PLANTNEED_FERTILIZER, mZen.getPlantsNeed(aPottedPlant));

        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = PlayerInfo.PURCHASE_COUNT_OFFSET + 3;
        mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_FERTILIZER;
        mZen.mouseDownWithFeedingTool(aPlant.mX, aPlant.mY, CursorType.CURSOR_TYPE_FERTILIZER);

        assertEquals(CursorType.CURSOR_TYPE_NORMAL, mBoard.mCursorObject.mCursorType);
        assertEquals(PlayerInfo.PURCHASE_COUNT_OFFSET + 2, mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER]);

        GridItem aTool = null;
        for (GridItem aGridItem : mBoard.mGridItems) {
            if (!aGridItem.mDead && aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                aTool = aGridItem;
            }
        }
        assertTrue(aTool != null);
        assertEquals(GridItemState.GRIDITEM_STATE_ZEN_TOOL_FERTILIZER, aTool.mGridItemState);

        // The tool anim finishing feeds the plant under it.
        mApp.reanimationGet(aTool.mGridItemReanimID).mLoopCount = 1;
        mZen.zenToolUpdate(aTool);

        assertEquals(PottedPlantAge.PLANTAGE_FULL, aPottedPlant.mPlantAge);
        assertEquals(2, countCoins(CoinType.COIN_DIAMOND));
        assertTrue(aTool.mDead);
    }

    /** ZenGarden.cpp:1723-1741: the tool reanim finishes and waters the plant under it. */
    @Test
    void wateringToolWatersThePlantUnderIt() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_SMALL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mX = 3;
        aPottedPlant.mY = 2;
        aPottedPlant.mDrawVariation = DrawVariation.VARIATION_NORMAL;
        aPottedPlant.mFeedingsPerGrow = 5;
        Plant aPlant = mZen.placePottedPlant(pottedPlantIndex(aPottedPlant));
        aPlant.mHighlighted = true;
        assertEquals(PottedPlantNeed.PLANTNEED_WATER, mZen.getPlantsNeed(aPottedPlant));

        mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_WATERING_CAN;
        mZen.mouseDownWithFeedingTool(aPlant.mX, aPlant.mY, CursorType.CURSOR_TYPE_WATERING_CAN);

        GridItem aTool = null;
        for (GridItem aGridItem : mBoard.mGridItems) {
            if (!aGridItem.mDead && aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                aTool = aGridItem;
            }
        }
        assertTrue(aTool != null);
        assertEquals(GridItemState.GRIDITEM_STATE_ZEN_TOOL_WATERING_CAN, aTool.mGridItemState);

        mApp.reanimationGet(aTool.mGridItemReanimID).mLoopCount = 1;
        mZen.zenToolUpdate(aTool);

        assertEquals(1, aPottedPlant.mTimesFed);
        assertEquals(1, countCoins(CoinType.COIN_SILVER));
        assertTrue(aTool.mDead);
    }

    /** ZenGarden.cpp:1560-1660: stinky falls asleep at the sleep line and wakes back up. */
    @Test
    void stinkyFallsAsleepAndWakesUp() {
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = (int) NOW;
        mApp.mPlayerInfo.mStinkyPosX = 200;
        mApp.mPlayerInfo.mStinkyPosY = 300;
        mZen.addStinky();
        GridItem aStinky = mZen.getStinky();
        assertTrue(aStinky != null);
        Reanimation aStinkyReanim = mApp.reanimationGet(aStinky.mGridItemReanimID);
        // Without retail .reanim data a real reanimation has no track instances; the by-name lookups
        // (Reanimation.cpp:974-977) still index them, so give it the standard stub array.
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.stubLoadedTracks(aStinkyReanim);

        // Expired purchase timer and no chocolate: stinky wants to sleep.
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = 0;

        // At the sleep line (STINKY_SLEEP_POS_Y = 461.0f, ZenGarden.cpp:44) walking left falls asleep.
        aStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_WALKING_LEFT;
        aStinky.mPosY = 462.0f;
        mZen.stinkyUpdate(aStinky);
        assertEquals(GridItemState.GRIDITEM_STINKY_FALLING_ASLEEP, aStinky.mGridItemState);

        // Finishing the fall swaps in the sleeping attachment (StinkyFinishFallingAsleep).
        aStinkyReanim.mLoopCount = 1;
        mZen.stinkyUpdate(aStinky);
        assertEquals(GridItemState.GRIDITEM_STINKY_SLEEPING, aStinky.mGridItemState);
        assertTrue(mZen.isStinkySleeping());
        assertEquals(0, mApp.mPlayerInfo.mHasWokenStinky); // the advice is shown the first time

        // A live purchase timer (wake-up food/chocolate) wakes it back up.
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = (int) NOW;
        aStinkyReanim.mLoopCount = 0;
        mZen.stinkyUpdate(aStinky);
        assertEquals(GridItemState.GRIDITEM_STINKY_WAKING_UP, aStinky.mGridItemState);
        assertEquals(1, mApp.mPlayerInfo.mHasWokenStinky);

        // The wake-up animation finishing returns stinky to walking.
        aStinkyReanim.mLoopCount = 1;
        mZen.stinkyUpdate(aStinky);
        assertEquals(GridItemState.GRIDITEM_STINKY_WALKING_LEFT, aStinky.mGridItemState);
    }

    /**
     * The garden's board is only bound by {@link ZenGarden#zenGardenInitLevel} ({@code
     * mBoard = mApp->mBoard}, ZenGarden.cpp:287). Killing a level right after the mode switch to the
     * zen garden reaches {@code LeaveGarden} through {@code Board::DisposeBoard} (Board.cpp:246-248)
     * with no bound board, so it must not dereference it.
     */
    @Test
    void leaveGardenIsSafeBeforeABoardIsBound() {
        newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL, GardenType.GARDEN_MAIN);
        ZenGarden aZen = new ZenGarden();
        assertNull(aZen.mBoard);
        mApp.mZenGarden = aZen;

        aZen.leaveGarden();

        assertEquals(1, mApp.mPlayerInfo.mNumPottedPlants); // the garden state is untouched
    }

    /** ZenGarden.cpp:2254-2287: a stale (future) timestamp resets the plant's timers. */
    @Test
    void pottedPlantUpdateResetsFutureTimestamps() {
        PottedPlant aPottedPlant = newPottedPlant(SeedType.SEED_PEASHOOTER, PottedPlantAge.PLANTAGE_FULL,
                GardenType.GARDEN_MAIN);
        aPottedPlant.mLastWateredTime = NOW + 500;
        aPottedPlant.mLastNeedFulfilledTime = NOW - 10;
        aPottedPlant.mLastFertilizedTime = NOW - 10;
        Plant aPlant = newGardenPlant(aPottedPlant, SeedType.SEED_PEASHOOTER);

        mZen.pottedPlantUpdate(aPlant);

        assertEquals(0, aPottedPlant.mLastWateredTime);
        assertEquals(0, aPottedPlant.mLastNeedFulfilledTime);
        assertEquals(0, aPottedPlant.mLastFertilizedTime);
        assertEquals(0, aPottedPlant.mLastChocolateTime);
    }
}
