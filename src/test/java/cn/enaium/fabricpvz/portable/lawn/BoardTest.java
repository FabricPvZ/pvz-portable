/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.Board.
 *
 * Ported from the upstream contracts in src/Lawn/Board.cpp (grid math, planting rules, waves,
 * sun economy, render order, mower/game-over transitions) — every asserted value is quoted from
 * the upstream source line noted next to it.
 *
 * Conventions (per the main agent): a bare `LawnApp` subclass performs no platform/GL/filesystem
 * work, so a board can be built without a window; asset-dependent assertions are gated with
 * Assumptions + TestAssets, asset-free mathematics are asserted unconditionally.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Common;

class BoardTest {

    /**
     * A headless adventure app whose reanimations look loaded.
     *
     * <p>The reanimations stay real and registered with the global effect system, which
     * {@code Attachment::AttachReanim} resolves through (Attachment.cpp:1049-1059). Retail .reanim
     * data is absent here, though, so every definition parses to zero tracks and a real reanimation
     * keeps a null {@code mTrackInstances} (Reanimator.cpp:397-421) — which the zombie reanim setup
     * indexes ({@code Zombie::EnableFuture} -> {@code SetImageOverride}, Reanimator.cpp:1135-1138).
     * {@link cn.enaium.fabricpvz.portable.test.HeadlessTestApp#stubLoadedTracks} supplies the per-track state a
     * loaded reanimation would have.
     */
    private static final class LoadedReanimApp extends LawnApp {

        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            cn.enaium.fabricpvz.portable.test.HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }
    }

    /** A fresh headless adventure app: level 1, first play-through. */
    private static LawnApp newAdventureApp() {
        LawnApp app = new LoadedReanimApp();
        LawnApp.gLawnApp = app;
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        // A freshly constructed app has no profile yet: the retail flow resolves one from the
        // profile manager during init(), so tests create one the same way the new-user dialog does.
        app.mPlayerInfo = app.mProfileMgr.addProfile("BoardTest");
        app.mPlayerInfo.mLevel = 1;
        app.mPlayerInfo.mFinishedAdventure = 0;
        app.mAppRandSeed = 0x5EED;
        // Everything LawnApp.init() would create (profile, effect system, music/foley, images).
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);
        return app;
    }

    private static Board newLevel1() {
        Common.sRand(0x5EED);
        Board board = new Board(newAdventureApp());
        board.initLevel();
        return board;
    }

    // -------------------------------------------------------------------------------------
    // §2.2 grid <-> pixel model
    // -------------------------------------------------------------------------------------

    @Test
    void gridToPixelMatchesUpstreamCellSize() {
        Board board = newLevel1();

        // Board.cpp:8846 GridToPixelX = gridX * 80 + LAWN_XMIN(40)
        assertEquals(40, board.gridToPixelX(0, 0));
        assertEquals(120, board.gridToPixelX(1, 2));
        assertEquals(360, board.gridToPixelX(4, 3));
        assertEquals(680, board.gridToPixelX(8, 5));

        // Board.cpp:8879-8916 GridToPixelY = gridY * 100 + LAWN_YMIN(80) on a day lawn
        assertEquals(80, board.gridToPixelY(0, 0));
        assertEquals(180, board.gridToPixelY(0, 1));
        assertEquals(480, board.gridToPixelY(4, 4));
        assertEquals(580, board.gridToPixelY(8, 5));
    }

    @Test
    void pixelToGridMatchesUpstreamAndClamps() {
        Board board = newLevel1();

        // Board.cpp:8782-8804
        assertEquals(-1, board.pixelToGridX(39, 100));
        assertEquals(0, board.pixelToGridX(40, 100));
        assertEquals(0, board.pixelToGridX(119, 100));
        assertEquals(1, board.pixelToGridX(120, 100));
        assertEquals(8, board.pixelToGridX(760, 100));
        assertEquals(8, board.pixelToGridX(4000, 100)); // clamped to MAX_GRID_SIZE_X - 1

        // Board.cpp:8806-8838
        assertEquals(-1, board.pixelToGridY(100, 79));
        assertEquals(0, board.pixelToGridY(100, 80));
        assertEquals(1, board.pixelToGridY(100, 180));
        assertEquals(4, board.pixelToGridY(100, 580)); // clamped to MAX_GRID_SIZE_Y - 2 on a 5-row lawn
        assertEquals(-1, board.pixelToGridY(10, 580)); // left of the lawn

        // Board.cpp:8800, 8840: the keep-on-board Y variant clamps X to 80 first, so
        // PixelToGridY(80, 200) -> (200 - LAWN_YMIN(80)) / 100 = 1
        assertEquals(0, board.pixelToGridXKeepOnBoard(-500, 200));
        assertEquals(1, board.pixelToGridYKeepOnBoard(-500, 200));
        assertEquals(1, board.pixelToGridYKeepOnBoard(200, 180));
    }

    @Test
    void pixelToGridYMatchesRoofAndPoolVariants() {
        Board board = newLevel1();

        // Board.cpp:8806-8838: pool/fog use 85px rows and 6 rows total
        board.mBackground = BackgroundType.BACKGROUND_3_POOL;
        board.mPlantRow[2] = PlantRowType.PLANTROW_POOL;
        board.mPlantRow[3] = PlantRowType.PLANTROW_POOL;
        assertEquals(0, board.pixelToGridY(100, 80));
        assertEquals(1, board.pixelToGridY(100, 165));
        assertEquals(5, board.pixelToGridY(100, 505)); // (505-80)/85 = 5 -> row 5 allowed on pool
        assertEquals(165, board.gridToPixelY(0, 1));

        // Board.cpp:8879-8905: roof rows are 85px with slope (5-gridX)*20 for gridX < 5, minus 10
        board.mBackground = BackgroundType.BACKGROUND_5_ROOF;
        assertEquals(170, board.gridToPixelY(0, 0)); // 0*85 + (5-0)*20 + 80 - 10
        assertEquals(70, board.gridToPixelY(5, 0)); // 0*85 + 0 + 80 - 10
        assertEquals(155, board.gridToPixelY(5, 1)); // 1*85 + 0 + 80 - 10
        assertTrue(board.stageHasRoof());
        assertFalse(board.stageHasPool());
    }

    @Test
    void stageClassifiersMatchUpstream() {
        Board board = newLevel1();
        assertEquals(BackgroundType.BACKGROUND_1_DAY, board.mBackground);
        assertFalse(board.stageIsNight());
        assertFalse(board.stageHasPool());
        assertFalse(board.stageHasRoof());
        assertFalse(board.stageHas6Rows());
        assertFalse(board.stageHasFog());
        assertFalse(board.stageHasGraveStones()); // day background
        assertTrue(board.stageIsDayWithoutPool());
        assertTrue(board.stageHasZombieWalkInFromRight());
    }

    @Test
    void level1GridSquareTypesMatchPickBackground() {
        Board board = newLevel1();

        // Board.cpp:988-1043: adventure level 1 is dirt on rows 0,1,3,4 and normal on row 2
        for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
            assertEquals(GridSquareType.GRIDSQUARE_DIRT, board.mGridSquareType[x][0], "row 0");
            assertEquals(GridSquareType.GRIDSQUARE_DIRT, board.mGridSquareType[x][1], "row 1");
            assertEquals(GridSquareType.GRIDSQUARE_GRASS, board.mGridSquareType[x][2], "row 2");
            assertEquals(GridSquareType.GRIDSQUARE_DIRT, board.mGridSquareType[x][3], "row 3");
            assertEquals(GridSquareType.GRIDSQUARE_DIRT, board.mGridSquareType[x][4], "row 4");
            assertEquals(GridSquareType.GRIDSQUARE_DIRT, board.mGridSquareType[x][5], "row 5");
        }
    }

    // -------------------------------------------------------------------------------------
    // §2.3 planting rules
    // -------------------------------------------------------------------------------------

    @Test
    void canPlantAtTableMatchesUpstreamRules() {
        Board board = newLevel1();

        // out of bounds
        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(-1, 2, SeedType.SEED_PEASHOOTER));
        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(9, 2, SeedType.SEED_PEASHOOTER));
        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(0, 6, SeedType.SEED_PEASHOOTER));
        // dirt is not plantable (Board.cpp:2757-2761)
        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(0, 0, SeedType.SEED_PEASHOOTER));
        // ...but the one grass row is (Board.cpp:2890-2905)
        assertEquals(PlantingReason.PLANTING_OK, board.canPlantAt(0, 2, SeedType.SEED_PEASHOOTER));
        // pool-only plants need a pool square (Board.cpp:2762-2770)
        assertEquals(PlantingReason.PLANTING_ONLY_IN_POOL, board.canPlantAt(0, 2, SeedType.SEED_LILYPAD));
        // grave buster needs a gravestone (Board.cpp:2710-2718)
        assertEquals(PlantingReason.PLANTING_ONLY_ON_GRAVES, board.canPlantAt(0, 2, SeedType.SEED_GRAVEBUSTER));
        // spikeweed needs dry ground (Board.cpp:2775-2781)
        assertEquals(PlantingReason.PLANTING_OK, board.canPlantAt(0, 2, SeedType.SEED_SPIKEWEED));
        // flower pot and pumpkin shell go into an empty cell (Board.cpp:2791-2794, 2799)
        assertEquals(PlantingReason.PLANTING_OK, board.canPlantAt(0, 2, SeedType.SEED_FLOWERPOT));
        assertEquals(PlantingReason.PLANTING_OK, board.canPlantAt(0, 2, SeedType.SEED_PUMPKINSHELL));
        // upgrades need their base plant (Board.cpp:2859-2888)
        assertEquals(PlantingReason.PLANTING_NEEDS_UPGRADE, board.canPlantAt(0, 2, SeedType.SEED_GATLINGPEA));
        assertEquals(PlantingReason.PLANTING_NEEDS_UPGRADE, board.canPlantAt(0, 2, SeedType.SEED_COBCANNON));

        // after planting a peashooter the cell is occupied; a repeater is *not* an upgrade of a
        // peashooter (Plant::IsUpgradableTo, Plant.cpp:2602-2637: only repeater->gatlingpea,
        // melonpult->wintermelon, sunflower->twinsunflower, ...), so CanPlantAt takes the
        // occupied-normal-plant arm and returns PLANTING_NOT_HERE (Board.cpp:2861-2883)
        board.addPlant(0, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(0, 2, SeedType.SEED_PEASHOOTER));
        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(0, 2, SeedType.SEED_REPEATER));
        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(0, 2, SeedType.SEED_FLOWERPOT));
        assertEquals(1, board.countPlantByType(SeedType.SEED_PEASHOOTER));
    }

    @Test
    void plantingRequirementsMetTracksBasePlants() {
        Board board = newLevel1();
        assertFalse(board.plantingRequirementsMet(SeedType.SEED_GATLINGPEA));
        assertFalse(board.plantingRequirementsMet(SeedType.SEED_TWINSUNFLOWER));
        assertFalse(board.plantingRequirementsMet(SeedType.SEED_WINTERMELON));
        assertTrue(board.plantingRequirementsMet(SeedType.SEED_PEASHOOTER));
        board.addPlant(0, 2, SeedType.SEED_REPEATER, SeedType.SEED_NONE);
        assertTrue(board.plantingRequirementsMet(SeedType.SEED_GATLINGPEA));
        board.addPlant(1, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        assertTrue(board.plantingRequirementsMet(SeedType.SEED_TWINSUNFLOWER));
    }

    @Test
    void currentPlantCostAndSunCharging() {
        Board board = newLevel1();
        assertEquals(150, board.mSunMoney); // Board.cpp:1350-1355 (first-time adventure level 1)

        // Board.cpp:9376-9384 / Plant::GetCost
        assertEquals(100, board.getCurrentPlantCost(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE));
        assertEquals(50, board.getCurrentPlantCost(SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE));

        // Board.cpp:8471-8487
        assertTrue(board.canTakeSunMoney(150));
        assertTrue(board.takeSunMoney(100));
        assertEquals(50, board.mSunMoney);
        assertFalse(board.takeSunMoney(100));
        assertEquals(70, board.mOutOfMoneyCounter, "out-of-money counter is armed on failure");
        assertEquals(50, board.mSunMoney);
    }

    @Test
    void addSunMoneyClampsAt9990() {
        Board board = newLevel1();
        board.addSunMoney(9999);
        assertEquals(9990, board.mSunMoney); // Board.cpp:8433-8438
        board.addSunMoney(50);
        assertEquals(9990, board.mSunMoney);
    }

    // -------------------------------------------------------------------------------------
    // §2.4 sun economy sequence
    // -------------------------------------------------------------------------------------

    @Test
    void skySunCountdownSequenceFollowsUpstreamFormula() {
        Board board = newLevel1();

        // Board.cpp:1370-1374: initial countdown is RandRangeInt(425, 700) on a day stage
        assertTrue(board.mSunCountDown >= 425 && board.mSunCountDown <= 700,
                "initial sky-sun countdown out of the upstream range: " + board.mSunCountDown);

        int previous = board.mSunCountDown;
        int suns = 0;
        for (int tick = 0; tick < 40000 && suns < 3; tick++) {
            java.util.Set<Coin> before = new java.util.HashSet<>();
            for (Coin c : board.mCoins) {
                before.add(c);
            }
            board.updateSunSpawning();
            Coin aSun = null;
            for (Coin c : board.mCoins) {
                if (!before.contains(c)) {
                    aSun = c;
                }
            }
            if (aSun != null) {
                suns++;
                assertEquals(CoinType.COIN_SUN, aSun.mType);        // Board.cpp:5197-5210
                assertEquals(60, (int) aSun.mPosY);
                assertTrue(aSun.mPosX >= 100 && aSun.mPosX < 650, "sky sun x out of RandRangeInt(100,649)");

                // Board.cpp:5197-5200: min(950, 425 + n*10) + Rand(275), n = mNumSunsFallen
                int n = board.mNumSunsFallen;
                int base = Math.min(950, 425 + n * 10);
                assertTrue(board.mSunCountDown >= base && board.mSunCountDown <= base + 274,
                        "countdown after sun #" + n + " out of range: " + board.mSunCountDown);
                previous = board.mSunCountDown;
            } else {
                assertTrue(board.mSunCountDown <= previous);
                previous = board.mSunCountDown;
            }
        }
        assertEquals(3, suns, "three sky suns must fall within 4000 seconds of level time");
    }

    @Test
    void sunInFlightCountsTowardsPurchases() {
        Board board = newLevel1();
        board.mSunMoney = 0;
        Coin aSun = board.addCoin(200, 60, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
        aSun.mIsBeingCollected = true;
        assertEquals(aSun.getSunValue(), board.countSunBeingCollected());
        assertTrue(board.canTakeSunMoney(aSun.getSunValue()));
        assertFalse(board.canTakeSunMoney(aSun.getSunValue() + 1));
        board.addSunMoney(aSun.getSunValue());
        assertEquals(aSun.getSunValue(), board.mSunMoney);
    }

    // -------------------------------------------------------------------------------------
    // §3 wave system for adventure level 1
    // -------------------------------------------------------------------------------------

    @Test
    void level1WaveCompositionMatchesUpstreamTables() {
        Board board = newLevel1();

        // Challenge.gZombieWaves[0] == 4 (Challenge.h:259-265)
        assertEquals(4, board.mNumWaves);

        // wave 0-2: aZombiePoints = aWave/3 + 1 == 1 -> one ZOMBIE_NORMAL
        for (int wave = 0; wave < 3; wave++) {
            assertEquals(1, board.numberZombiesInWave(wave), "wave " + wave);
            assertEquals(ZombieType.ZOMBIE_NORMAL, board.mZombiesInWave[wave][0]);
            assertEquals(ZombieType.ZOMBIE_INVALID, board.mZombiesInWave[wave][1]);
        }

        // final wave: aZombiePoints = aWave/3 + 1 == 2 (Board.cpp:646). It is not a flag wave
        // (Board.cpp:525-533), level 1 has no introduced zombie (Board.cpp:2353-2357), and
        // PutInMissingZombies can only add ZOMBIE_NORMAL: ZOMBIE_FLAG fails CanZombieSpawnOnLevel
        // because its mPickWeight is 0 (Zombie.cpp:81, Board.cpp:2336-2351). One point is spent
        // there (Board.cpp:722-726 -> 560-568) and the remaining point by the while loop
        // (Board.cpp:762-766), so both zombies are ZOMBIE_NORMAL.
        int finalWave = board.mNumWaves - 1;
        assertEquals(2, board.numberZombiesInWave(finalWave));
        assertEquals(ZombieType.ZOMBIE_NORMAL, board.mZombiesInWave[finalWave][0]);
        assertEquals(ZombieType.ZOMBIE_NORMAL, board.mZombiesInWave[finalWave][1]);
        assertEquals(ZombieType.ZOMBIE_INVALID, board.mZombiesInWave[finalWave][2]);

        // ZOMBIE_TRAFFIC_CONE starts on level 3 (Zombie.cpp:80-115) -> never on level 1
        assertFalse(Board.canZombieSpawnOnLevel(ZombieType.ZOMBIE_TRAFFIC_CONE, 1));
        assertTrue(Board.canZombieSpawnOnLevel(ZombieType.ZOMBIE_NORMAL, 1));
        // ZOMBIE_FLAG is only ever placed by the flag-wave branch of PickZombieWaves
        // (Board.cpp:656-663); CanZombieSpawnOnLevel rejects it on every level because its
        // mPickWeight is 0 (Zombie.cpp:81, Board.cpp:2336-2351)
        assertFalse(Board.canZombieSpawnOnLevel(ZombieType.ZOMBIE_FLAG, 1));
        assertFalse(Board.canZombieSpawnOnLevel(ZombieType.ZOMBIE_PAIL, 1));

        // level 1 first-time adventure never has flag waves (Board.cpp:525-533)
        for (int wave = 0; wave < board.mNumWaves; wave++) {
            assertFalse(board.isFlagWave(wave));
        }
        assertEquals(4, board.getNumWavesPerFlag()); // mNumWaves < 10 -> mNumWaves
    }

    @Test
    void zombieSpawningWalksTheWaveTableAndRows() {
        Board board = newLevel1();
        board.startLevel();
        // The wave machinery lives behind UpdateGame's game-scene gate (Board.cpp:5651-5652); the
        // level-intro cut scene would normally flip the scene to SCENE_PLAYING, and this test drives
        // UpdateGame directly, so it sets the finished-intro state itself.
        board.mApp.mGameScene = GameScenes.SCENE_PLAYING;
        // The first tick of a first-time level 1 arms the pick-up-peashooter tutorial
        // (Board.cpp:5676-5680), and UpdateZombieSpawning returns early while that tutorial is
        // unfinished (Board.cpp:5249-5253). A player advances it by planting; the test just marks it
        // finished, which is all Board::SetTutorialState sets (Board.cpp:5528-5629).
        board.updateGame();
        board.setTutorialState(TutorialState.TUTORIAL_OFF);

        int expectedTotal = board.numberZombiesInWave(0) + board.numberZombiesInWave(1)
                + board.numberZombiesInWave(2) + board.numberZombiesInWave(3);
        // waves 0-2 hold one ZOMBIE_NORMAL each and the final wave holds two (Board.cpp:648,
        // 722-726, 762-766); ZOMBIE_FLAG never spawns on a first-time level 1 because
        // CanZombieSpawnOnLevel rejects every zero-weight type (Zombie.cpp:81, Board.cpp:2336-2351)
        assertEquals(5, expectedTotal);

        int spawned = 0;
        for (int tick = 0; tick < 200000 && spawned < expectedTotal; tick++) {
            java.util.Set<Zombie> before = new java.util.HashSet<>();
            for (Zombie z : board.mZombies) {
                before.add(z);
            }
            board.updateGame();
            // SpawnZombieWave adds every zombie of the wave in one tick (Board.cpp:4921-4939), so
            // each new zombie is checked, not each spawning tick.
            for (Zombie aZombie : board.mZombies) {
                if (before.contains(aZombie)) {
                    continue;
                }
                spawned++;
                assertTrue(aZombie.mRow >= 0 && aZombie.mRow < Board.MAX_GRID_SIZE_Y, "zombie row out of range");
                assertTrue(board.rowCanHaveZombies(aZombie.mRow), "zombie spawned on a dirt row");
                assertTrue(aZombie.mZombieType == ZombieType.ZOMBIE_NORMAL || aZombie.mZombieType == ZombieType.ZOMBIE_FLAG,
                        "level 1 only spawns normal and flag zombies, got " + aZombie.mZombieType);
                // A first-time level 1 has a single plantable row (Board.cpp:998-1010), so an
                // unopposed zombie walks into the house and wins (Board.cpp:5049-5100), which stops
                // UpdateGame and with it the wave machinery this test walks. The test therefore
                // removes each zombie as it spawns instead of playing the level.
                aZombie.mDead = true;
            }
        }
        assertEquals(expectedTotal, spawned, "every zombie of every level 1 wave must spawn");
        assertEquals(4, board.mTotalSpawnedWaves);
        assertEquals(4, board.mCurrentWave);
        assertTrue(board.mRiseFromGraveCounter == 200, "last wave arms the rising-from-grave counter");
    }

    // -------------------------------------------------------------------------------------
    // §1.5 / §1.6 entity arrays and the delete queue
    // -------------------------------------------------------------------------------------

    @Test
    void objectListsAddAndProcessDeleteQueue() {
        Board board = newLevel1();

        Plant aPlant = board.addPlant(0, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Zombie aZombie = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
        Projectile aProjectile = board.addProjectile(100, 100, 0, 2, ProjectileType.PROJECTILE_PEA);
        Coin aCoin = board.addCoin(100, 100, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
        assertNotNull(aPlant);
        assertNotNull(aZombie);
        assertNotNull(aProjectile);
        assertNotNull(aCoin);
        assertEquals(1, board.mPlants.mSize);
        assertEquals(1, board.mZombies.mSize);
        assertEquals(1, board.mProjectiles.mSize);
        assertEquals(1, board.mCoins.mSize);

        // nothing is freed while only marked dead (Board.cpp:8489-8544)
        aPlant.mDead = true;
        board.processDeleteQueue();
        assertEquals(0, board.mPlants.mSize);
        assertEquals(1, board.mZombies.mSize);
        aZombie.mDead = true;
        aCoin.mDead = true;
        board.processDeleteQueue();
        assertEquals(0, board.mZombies.mSize);
        assertEquals(0, board.mCoins.mSize);
        assertEquals(1, board.mProjectiles.mSize);
        assertSame(aProjectile, board.mProjectiles.dataArrayGet(board.mProjectiles.dataArrayGetID(aProjectile)));
    }

    @Test
    void zombieIdsRoundTrip() {
        Board board = newLevel1();
        Zombie aZombie = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
        int id = board.zombieGetID(aZombie);
        assertSame(aZombie, board.zombieGet(id));
        assertSame(aZombie, board.zombieTryToGet(id));
        assertNull(board.zombieTryToGet(9999));
        assertEquals(ZombieID.ZOMBIEID_NULL, board.zombieGetID(null));
    }

    // -------------------------------------------------------------------------------------
    // §1.7 render order
    // -------------------------------------------------------------------------------------

    @Test
    void makeRenderOrderMatchesUpstreamLayers() {
        assertEquals(10000, RenderLayer.RENDER_LAYER_ROW_OFFSET);
        assertEquals(0 * 10000 + 302000 + 0, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0, 0));
        assertEquals(2 * 10000 + 302000 + 3, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 2, 3));
        assertEquals(5 * 10000 + 303000 + 0, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ZOMBIE, 5, 0));
        assertEquals(100000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_UI_BOTTOM, 0, 0));
        assertEquals(200000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, 0, 0));
        assertEquals(301000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, 0, 0));
        assertEquals(306000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, 0, 0));
        assertEquals(307000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, 0, 0));
        assertEquals(400000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0));
        assertEquals(500000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_FOG, 0, 0));
        assertEquals(600000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_COIN_BANK, 0, 0));
        assertEquals(700000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_UI_TOP, 0, 0));
        assertEquals(800000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0));
        assertEquals(900000, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_SCREEN_FADE, 0, 0));
    }

    @Test
    void entityRenderOrdersFollowTheirLayers() {
        Board board = newLevel1();

        Plant aPlant = board.addPlant(2, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        // Plant::CalcRenderOrder (Plant.cpp:503-529) = MakeRenderOrder(PLANT_LAYER, mRow,
        // anOrder*5 - mX + 800) with PLANT_ORDER_NORMAL == 1 (Plant.h:113-120) and
        // mX == GridToPixelX(2, 2) == 200 -> 2*10000 + 302000 + (5 - 200 + 800) = 322605
        int aPlantLayerOffset = PlantOrder.PLANT_ORDER_NORMAL * 5 - board.gridToPixelX(2, 2) + 800;
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 2, aPlantLayerOffset), aPlant.mRenderOrder);

        Zombie aZombie = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 3, 0);
        // Zombie::ZombieInitialize defaults to RENDER_LAYER_ZOMBIE with an offset of 4
        // (Zombie.cpp:241-242) and settles the order at Zombie.cpp:893
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ZOMBIE, 3, 4), aZombie.mRenderOrder);

        GridItem aCrater = board.addACrater(4, 2);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, 2, 1), aCrater.mRenderOrder);

        GridItem aLadder = board.addALadder(5, 2);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 2, 800), aLadder.mRenderOrder);

        // the mower sits on the lawn-mower layer of its row; mowers are created by the level-intro
        // cut scene (CutScene.cpp:613), which a headless board never plays
        board.initLawnMowers();
        LawnMower aMower = board.findLawnMowerInRow(2);
        assertNotNull(aMower);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, 2, 0), aMower.mRenderOrder);

        // grids: the ice layer is GROUND + row + 2 (Board.cpp:5816)
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, 2, 2), board.getIceZPos(2));
    }

    // -------------------------------------------------------------------------------------
    // §3 lawn mowers, loss and level completion
    // -------------------------------------------------------------------------------------

    @Test
    void level1OnlyGetsAMowerOnTheGrassRow() {
        Board board = newLevel1();
        board.initLawnMowers();

        // Board.cpp:1588-1610: one mower per row whose mPlantRow != PLANTROW_DIRT
        assertEquals(1, board.mLawnMowers.mSize);
        assertEquals(1, board.countUntriggerLawnMowers());
        LawnMower aMower = board.findLawnMowerInRow(2);
        assertNotNull(aMower);
        assertEquals(2, aMower.mRow);
        assertNull(board.findLawnMowerInRow(0));
        assertEquals(LawnMowerType.LAWNMOWER_LAWN, aMower.mMowerType);
    }

    @Test
    void zombieReachingTheHouseEndsTheGame() {
        Board board = newLevel1();
        board.startLevel();
        Zombie aZombie = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
        assertNotNull(aZombie);

        board.zombiesWon(aZombie); // Board.cpp:5049-5120

        assertEquals(BoardResult.BOARDRESULT_LOST, board.mApp.mBoardResult);
        assertEquals(GameScenes.SCENE_ZOMBIES_WON, board.mApp.mGameScene);
        // ZombiesWon walks the zombie into the house (Board.cpp:5097-5100), which marks it as the
        // winner (Zombie::WalkIntoHouse sets mFromWave = ZOMBIE_WAVE_WINNER, Zombie.cpp:9634-9637);
        // GetWinningZombie returns exactly that zombie (Board.cpp:9449-9460)
        assertSame(aZombie, board.getWinningZombie());
    }

    @Test
    void editingTheLastWaveFadesOutTheLevel() {
        Board board = newLevel1();
        board.startLevel();

        // Board.cpp:1808-1916: FadeOutLevel arms the fade counter and the loot mowers
        board.fadeOutLevel();
        assertTrue(board.mBoardFadeOutCounter >= 500, "fade out counter must be armed");
        assertTrue(board.hasLevelAwardDropped(), "level award drops are armed by the fade out");

        for (int i = 0; i < 700 && !board.mLevelComplete; i++) {
            board.updateLevelEndSequence();
        }
        assertTrue(board.mLevelComplete, "the level must complete once the fade-out counter expires");
    }

    @Test
    void updateCounterPipelineAdvancesAsUpstream() {
        Board board = newLevel1();
        board.startLevel();
        board.mApp.mGameScene = GameScenes.SCENE_PLAYING;

        board.mTimeStopCounter = 0;
        for (int i = 0; i < 25; i++) {
            board.update();
        }

        // Board.cpp:5693-5715: mBoardUpdateCounter advances on every update, mMainCounter only in
        // SCENE_PLAYING, mEffectCounter only when not time-stopped (Board.cpp:5717-5723).
        assertEquals(25, board.mBoardUpdateCounter);
        assertEquals(25, board.mMainCounter);
        assertEquals(25, board.mEffectCounter);

        board.mTimeStopCounter = 10;
        board.update();
        assertEquals(26, board.mBoardUpdateCounter);
        // Board::Update returns at the time-stop gate (Board.cpp:5768-5769) *before* UpdateGame(),
        // which is where mMainCounter++ lives (Board.cpp:5654), so a time stop leaves it unchanged.
        assertEquals(25, board.mMainCounter, "mMainCounter does not advance while time-stopped");
        assertEquals(25, board.mEffectCounter, "mEffectCounter stops during a time stop");
    }

    // -------------------------------------------------------------------------------------
    // asset-dependent smoke test
    // -------------------------------------------------------------------------------------

    @Test
    void startLevelForAdventureLevel1RunsHeadless() {
        Assumptions.assumeTrue(TestAssets.resourceRoot().isPresent(), "retail assets are required");
        Board board = newLevel1();
        board.startLevel();
        for (int i = 0; i < 100; i++) {
            board.update();
        }
        assertEquals(100, board.mBoardUpdateCounter);
    }
}
