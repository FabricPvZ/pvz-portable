/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.ChallengeScaryPotter (Challenge.cpp:2060-2083,
 * 3456-3532, 3534-3980).
 *
 * Builds a real Board in each Scary Potter game mode through the entity test doubles
 * ({@link LawnEntityTestSupport}) and asserts the mode-specific objects/transitions the port
 * produces: the pot layout, the pot-type rewrites, the mallet/open-pot flow and the puzzle
 * stage-clear reset.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

class ChallengeScaryPotterTest {

    private static Board newBoard(TestLawnApp theApp, int theGameMode) {
        theApp.mGameMode = theGameMode;
        return LawnEntityTestSupport.newBoard(theApp);
    }

    private static GridItem addPot(Board theBoard, int theScaryPotType, int theZombieType, int theSeedType,
            int theState, int theGridX, int theGridY, int theSunCount) {
        GridItem aPot = theBoard.mGridItems.dataArrayAlloc();
        aPot.mGridItemType = GridItemType.GRIDITEM_SCARY_POT;
        aPot.mGridItemState = theState;
        aPot.mGridX = theGridX;
        aPot.mGridY = theGridY;
        aPot.mScaryPotType = theScaryPotType;
        aPot.mZombieType = theZombieType;
        aPot.mSeedType = theSeedType;
        aPot.mSunCount = theSunCount;
        aPot.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, theGridY, 0);
        return aPot;
    }

    private static int countCoinsOfType(Board theBoard, int theCoinType) {
        int aCount = 0;
        for (Coin aCoin : theBoard.mCoins) {
            if (!aCoin.mDead && aCoin.mType == theCoinType) {
                aCount++;
            }
        }
        return aCount;
    }

    // -------------------------------------------------------------------------------------
    // Populate / place / rewrite
    // -------------------------------------------------------------------------------------

    @Test
    void populateScaryPotterOnePlacesTheUpstreamLayout() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        sp.scaryPotterPopulate();

        // 5 peashooter + 5 snowpea + 5 squash + 6 normal + 3 pail + 1 jack-in-the-box = 25.
        assertEquals(25, sp.scaryPotterCountPots(), "ScaryPotterCountPots must see every spawned pot");
        assertEquals(25, board.mChallenge.mScaryPotterPots, "mScaryPotterPots must cache the count");

        int aLeafCount = 0;
        int aJackCount = 0;
        for (GridItem aPot : board.mGridItems) {
            if (aPot.mDead || aPot.mGridItemType != GridItemType.GRIDITEM_SCARY_POT) {
                continue;
            }
            // ScaryPotterDontPlaceInCol(0..3) seals the first four columns.
            assertTrue(aPot.mGridX >= 4, "no pot may sit in a column reserved by ScaryPotterDontPlaceInCol");
            assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, aPot.mGridY, 0), aPot.mRenderOrder);
            assertTrue(aPot.mGridItemState == GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION
                    || aPot.mGridItemState == GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF);
            if (aPot.mGridItemState == GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF) {
                aLeafCount++;
            }
            if (aPot.mScaryPotType == ScaryPotType.SCARYPOT_ZOMBIE
                    && aPot.mZombieType == ZombieType.ZOMBIE_JACK_IN_THE_BOX) {
                aJackCount++;
            }
        }
        assertEquals(2, aLeafCount, "ScaryPotterChangePotType(LEAF, 2) must rewrite exactly two seed pots");
        assertEquals(1, aJackCount, "the mode must contain one jack-in-the-box zombie pot");
    }

    @Test
    void placePotZeroesThePickedCellAndFillsThePotFields() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        PvzpWeightedGridArray[] aGrid = { new PvzpWeightedGridArray(4, 2, 1) };
        sp.scaryPotterPlacePot(ScaryPotType.SCARYPOT_SUN, ZombieType.ZOMBIE_INVALID, SeedType.SEED_NONE, 1, aGrid, 1);

        GridItem aPot = firstPot(board);
        assertNotNull(aPot);
        assertEquals(GridItemType.GRIDITEM_SCARY_POT, aPot.mGridItemType);
        assertEquals(GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, aPot.mGridItemState);
        assertEquals(4, aPot.mGridX);
        assertEquals(2, aPot.mGridY);
        assertEquals(ScaryPotType.SCARYPOT_SUN, aPot.mScaryPotType);
        assertTrue(aPot.mSunCount >= 1 && aPot.mSunCount <= 3, "Rand(3) + 1 sun must land in 1..3");
        assertEquals(0, aGrid[0].mWeight, "the picked cell must be consumed");
    }

    @Test
    void dontPlaceInColOnlyZeroesTheMatchingColumn() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        PvzpWeightedGridArray[] aGrid = {
            new PvzpWeightedGridArray(0, 0, 1),
            new PvzpWeightedGridArray(1, 0, 1),
            new PvzpWeightedGridArray(2, 0, 1),
        };
        sp.scaryPotterDontPlaceInCol(1, aGrid, aGrid.length);

        assertEquals(1, aGrid[0].mWeight);
        assertEquals(0, aGrid[1].mWeight);
        assertEquals(1, aGrid[2].mWeight);
    }

    @Test
    void fillColumnWithPlantSealsTheColumnAndPlantsEveryRow() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        PvzpWeightedGridArray[] aGrid = {
            new PvzpWeightedGridArray(4, 0, 1),
            new PvzpWeightedGridArray(4, 3, 1),
            new PvzpWeightedGridArray(5, 0, 1),
        };
        sp.scaryPotterFillColumnWithPlant(4, SeedType.SEED_POTATOMINE, aGrid, aGrid.length);

        assertEquals(0, aGrid[0].mWeight);
        assertEquals(0, aGrid[1].mWeight);
        assertEquals(1, aGrid[2].mWeight, "other columns must be untouched");

        int aPlanted = 0;
        for (Plant aPlant : board.mPlants) {
            if (aPlant.mDead || aPlant.mPlantCol != 4) {
                continue;
            }
            assertTrue(aPlant.mRow >= 0 && aPlant.mRow < Board.MAX_GRID_SIZE_Y - 1);
            assertEquals(SeedType.SEED_POTATOMINE, aPlant.mSeedType);
            assertEquals(10, aPlant.mStateCountdown, "potato mines are armed with a 10 frame countdown");
            aPlanted++;
        }
        assertEquals(Board.MAX_GRID_SIZE_Y - 1, aPlanted);
    }

    @Test
    void changePotTypeRewritesOnlyEligiblePots() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aSeed1 = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 4, 1, 0);
        GridItem aSeed2 = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 5, 1, 0);
        GridItem aGargantuar = addPot(board, ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_GARGANTUAR,
                SeedType.SEED_NONE, GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 6, 1, 0);
        GridItem aNormal = addPot(board, ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 7, 1, 0);

        sp.scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 10);
        assertEquals(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, aSeed1.mGridItemState);
        assertEquals(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, aSeed2.mGridItemState);
        assertEquals(GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, aGargantuar.mGridItemState);

        sp.scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_ZOMBIE, 10);
        assertEquals(GridItemState.GRIDITEM_STATE_SCARY_POT_ZOMBIE, aGargantuar.mGridItemState);
        assertEquals(GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, aNormal.mGridItemState,
                "only gargantuar zombie pots are eligible for the zombie-face rewrite");
    }

    // -------------------------------------------------------------------------------------
    // Completion / opening
    // -------------------------------------------------------------------------------------

    @Test
    void isCompletedNeedsNoPotsAndNoZombies() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        assertEquals(1, sp.scaryPotterIsCompleted(), "an empty board with no zombies is complete");

        GridItem aPot = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 4, 1, 0);
        assertEquals(0, sp.scaryPotterIsCompleted(), "an intact pot blocks completion");

        aPot.mDead = true;
        assertEquals(1, sp.scaryPotterIsCompleted());
    }

    @Test
    void openSeedPotDropsTheUsableSeedPacket() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aPot = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 4, 2, 0);
        addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 8, 5, 0);

        sp.scaryPotterOpenPot(aPot);

        assertTrue(aPot.mDead, "opening a pot must kill the grid item");
        Coin aCoin = lastCoinOfType(board, CoinType.COIN_USABLE_SEED_PACKET);
        assertNotNull(aCoin, "a seed pot must drop a usable seed packet coin");
        assertEquals(SeedType.SEED_PEASHOOTER, aCoin.mUsableSeedType);
        assertTrue(app.mSamplesPlayed.contains((long) Resources.SOUND_BONK_ID));
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_VASE_BREAKING));
        assertTrue(app.mAddedParticles >= 1, "the shatter particle must spawn");
    }

    @Test
    void openSunPotDropsEveryAccumulatedSun() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aPot = addPot(board, ScaryPotType.SCARYPOT_SUN, ZombieType.ZOMBIE_INVALID, SeedType.SEED_NONE,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 4, 2, 2);
        addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 8, 5, 0);

        assertEquals(0, countCoinsOfType(board, CoinType.COIN_SUN));
        sp.scaryPotterOpenPot(aPot);

        assertEquals(2, countCoinsOfType(board, CoinType.COIN_SUN),
                "ScaryPotterCountSunInPot suns must each become a coin");
        assertTrue(aPot.mDead);
    }

    @Test
    void openZombiePotReleasesTheZombieAtThePotPixel() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aPot = addPot(board, ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 5, 3, 0);
        addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 8, 5, 0);

        sp.scaryPotterOpenPot(aPot);

        Zombie aZombie = null;
        for (Zombie z : board.mZombies) {
            if (!z.mDead) {
                aZombie = z;
            }
        }
        assertNotNull(aZombie, "a zombie pot must release its zombie");
        assertEquals(ZombieType.ZOMBIE_NORMAL, aZombie.mZombieType);
        assertEquals(board.gridToPixelX(5, 3), aZombie.mPosX, 0.0f);
    }

    @Test
    void jackExplodeOpensPotInRangeOnly() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aNear = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 4, 2, 0);
        GridItem aFar = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 8, 5, 0);

        sp.scaryPotterJackExplode(board.gridToPixelX(4, 2), board.gridToPixelY(4, 2));

        assertTrue(aNear.mDead, "a pot within the 1x1 blast range must open");
        assertFalse(aFar.mDead, "a pot far from the blast must survive");
    }

    // -------------------------------------------------------------------------------------
    // Mallet flow
    // -------------------------------------------------------------------------------------

    @Test
    void malletPotOpensTheTargetOnTheNextUpdate() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aPot = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 3, 2, 0);
        addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 8, 5, 0);

        sp.scaryPotterMalletPot(aPot);

        assertEquals(ChallengeState.STATECHALLENGE_SCARY_POTTER_MALLETING, board.mChallenge.mChallengeState);
        assertEquals(3, board.mChallenge.mChallengeGridX);
        assertEquals(2, board.mChallenge.mChallengeGridY);
        assertEquals(1, app.mAddedReanimations, "the mallet reanimation must be added");
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_SWING));

        // The stub boasts one completed loop, which is the trigger ScaryPotterUpdate waits for.
        app.mReanim.mLoopCount = 1;
        sp.scaryPotterUpdate();

        assertTrue(aPot.mDead, "the mallet cycle must open the pot");
        assertEquals(ChallengeState.STATECHALLENGE_NORMAL, board.mChallenge.mChallengeState);
        assertEquals(ReanimationID.REANIMATIONID_NULL, board.mChallenge.mReanimChallenge);
        assertEquals(0, board.mChallenge.mChallengeGridX);
        assertEquals(0, board.mChallenge.mChallengeGridY);
    }

    @Test
    void updateIsInertOutsideTheMalletState() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aPot = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 4, 2, 0);
        board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;

        sp.scaryPotterUpdate();

        assertFalse(aPot.mDead);
        assertEquals(ChallengeState.STATECHALLENGE_NORMAL, board.mChallenge.mChallengeState);
    }

    // -------------------------------------------------------------------------------------
    // Award stage + stage clear
    // -------------------------------------------------------------------------------------

    @Test
    void puzzleIsAwardStageUsesThePerModeGoal() {
        TestLawnApp adventureApp = LawnEntityTestSupport.newApp();
        Board adventureBoard = newBoard(adventureApp, GameMode.GAMEMODE_ADVENTURE);
        ChallengeScaryPotter adventure = new ChallengeScaryPotter(adventureBoard.mChallenge);
        assertEquals(0, adventure.puzzleIsAwardStage(), "adventure mode never awards puzzle coins");

        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_ENDLESS);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);
        board.mChallenge.mSurvivalStage = 5;
        assertEquals(0, sp.puzzleIsAwardStage());
        board.mChallenge.mSurvivalStage = 10;
        assertEquals(1, sp.puzzleIsAwardStage(), "Scary Potter endless awards every 10th stage");
        board.mChallenge.mSurvivalStage = 20;
        assertEquals(1, sp.puzzleIsAwardStage());

        TestLawnApp izApp = LawnEntityTestSupport.newApp();
        Board izBoard = newBoard(izApp, GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS);
        ChallengeScaryPotter iz = new ChallengeScaryPotter(izBoard.mChallenge);
        izBoard.mChallenge.mSurvivalStage = 3;
        assertEquals(1, iz.puzzleIsAwardStage(), "I,Zombie endless awards every 3rd stage");
        izBoard.mChallenge.mSurvivalStage = 4;
        assertEquals(0, iz.puzzleIsAwardStage());
    }

    @Test
    void puzzleNextStageClearResetsTheBoardAndAdvancesTheStage() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        GridItem aPot = addPot(board, ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION, 4, 2, 0);
        Coin aSeedPacket = board.addCoin(100, 100, CoinType.COIN_USABLE_SEED_PACKET, CoinMotion.COIN_MOTION_COIN);
        Coin aSun = board.addCoin(120, 100, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
        Zombie aZombie = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);

        board.mNextSurvivalStageCounter = 500;
        board.mProgressMeterWidth = 400;
        board.mLevelAwardSpawned = true;

        sp.puzzleNextStageClear();

        assertEquals(1, board.mChallenge.mSurvivalStage);
        assertEquals(0, board.mNextSurvivalStageCounter);
        assertEquals(0, board.mProgressMeterWidth);
        assertFalse(board.mLevelAwardSpawned);
        assertTrue(aPot.mDead, "every grid item must be cleared");
        assertTrue(aZombie.mDead, "every on-board zombie must be removed");
        assertTrue(aSeedPacket.mDead, "usable seed packets are culled on stage clear");
        assertFalse(aSun.mDead, "other coins are auto-collected instead of culled");
        assertTrue(app.mSamplesPlayed.contains((long) Resources.SOUND_HUGE_WAVE_ID));
        assertTrue(app.mAddedParticles >= 1);
    }

    // -------------------------------------------------------------------------------------
    // Art overlay
    // -------------------------------------------------------------------------------------

    @Test
    void drawArtChallengeRunsOutsideArtLayoutsAndRestoresColorize() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        ChallengeScaryPotter sp = new ChallengeScaryPotter(board.mChallenge);

        Graphics g = new Graphics();
        sp.drawArtChallenge(g);

        assertFalse(g.getColorizeImages(), "DrawArtChallenge must restore the colorize flag");
    }

    // -------------------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------------------

    private static GridItem firstPot(Board theBoard) {
        for (GridItem aPot : theBoard.mGridItems) {
            if (!aPot.mDead && aPot.mGridItemType == GridItemType.GRIDITEM_SCARY_POT) {
                return aPot;
            }
        }
        return null;
    }

    private static Coin lastCoinOfType(Board theBoard, int theCoinType) {
        Coin aFound = null;
        for (Coin aCoin : theBoard.mCoins) {
            if (!aCoin.mDead && aCoin.mType == theCoinType) {
                aFound = aCoin;
            }
        }
        return aFound;
    }
}
