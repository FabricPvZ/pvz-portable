/*
 * Headless tests for the Challenge mini-game bodies (Challenge.cpp:324-5335), which this port
 * splits into the Challenge<Mode> helper classes and exposes through Challenge's public entry
 * points. Each test builds a Board in the relevant game mode and asserts the mode-specific state
 * transition / objects that the body is responsible for, so a regression that reintroduces a
 * throwing body (or a wrong branch) fails here.
 *
 * The Board is built through {@link LawnEntityTestSupport}, whose {@code TestLawnApp} short-circuits
 * the reanimation/particle/audio services; asset-free logic (grid snapshots, portal/pot/squirrel
 * placement, wave counters, state machines) runs against the real Board.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;

class ChallengeModesTest {

    private static Board board(TestLawnApp theApp, int theMode) {
        theApp.mGameMode = theMode;
        return LawnEntityTestSupport.newBoard(theApp);
    }

    private static int countGridItems(Board theBoard, int theType) {
        int aCount = 0;
        for (GridItem aItem : theBoard.mGridItems) {
            if (!aItem.mDead && aItem.mGridItemType == theType) {
                aCount++;
            }
        }
        return aCount;
    }

    // -------------------------------------------------------------------------------------------
    // Beghouled / Beghouled Twist (Challenge.cpp:324-1376, 1854-1886, 2085-2118, 3235-3305)
    // -------------------------------------------------------------------------------------------

    @Test
    void beghouledBoardStateQueries() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        Challenge ch = board.mChallenge;

        BeghouledBoardState state = new BeghouledBoardState();
        ch.loadBeghouledBoardState(state); // no plants -> every square SEED_NONE

        assertEquals(SeedType.SEED_NONE, ch.beghouledGetPlantAt(-1, 0, state));
        assertEquals(SeedType.SEED_NONE, ch.beghouledGetPlantAt(9, 0, state));
        assertEquals(0, ch.beghouledBoardHasMatch(state), "an empty board has no match");
        assertEquals(0, ch.beghouledHorizontalMatchLength(0, 0, state));
        assertEquals(0, ch.beghouledVerticalMatchLength(0, 0, state));
    }

    @Test
    void beghouledMakeStartBoardPlantsTheGrid() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        Challenge ch = board.mChallenge;

        ch.beghouledMakeStartBoard();

        assertTrue(board.mPlants.mSize > 0, "the Beghouled start board must populate the grid");
    }

    @Test
    void beghouledTwistValidMoveNeedsFourAdjacentPlants() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        Challenge ch = board.mChallenge;

        BeghouledBoardState state = new BeghouledBoardState();
        state.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        state.mSeedType[1][0] = SeedType.SEED_WALLNUT;
        state.mSeedType[0][1] = SeedType.SEED_WALLNUT;
        state.mSeedType[1][1] = SeedType.SEED_PEASHOOTER;

        assertEquals(1, ch.beghouledTwistValidMove(0, 0, state));
        assertEquals(0, ch.beghouledTwistValidMove(7, 0, state), "x > 6 is out of the twist grid");
        assertEquals(0, ch.beghouledTwistValidMove(0, 4, state), "y > 3 is out of the twist grid");
    }

    // -------------------------------------------------------------------------------------------
    // Portal Combat (Challenge.cpp:2870-3094)
    // -------------------------------------------------------------------------------------------

    @Test
    void portalStartCreatesFourOpenPortals() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT);
        Challenge ch = board.mChallenge;

        ch.portalStart();

        int aPortals = countGridItems(board, GridItemType.GRIDITEM_PORTAL_CIRCLE)
                + countGridItems(board, GridItemType.GRIDITEM_PORTAL_SQUARE);
        assertEquals(4, aPortals);
        assertEquals(9000, ch.mChallengeStateCounter);
        assertEquals(200, board.mZombieCountDown);
        assertEquals(200, ch.mConveyorBeltCounter);
    }

    // -------------------------------------------------------------------------------------------
    // Whack-a-Zombie (Challenge.cpp:2491-2532, 4905-4921)
    // -------------------------------------------------------------------------------------------

    @Test
    void whackAZombiePlaceGravesAddsRequestedGravestones() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE);
        Challenge ch = board.mChallenge;

        ch.whackAZombiePlaceGraves(3);

        assertEquals(3, countGridItems(board, GridItemType.GRIDITEM_GRAVESTONE));
    }

    // -------------------------------------------------------------------------------------------
    // Squirrel (Challenge.cpp:4624-4823)
    // -------------------------------------------------------------------------------------------

    @Test
    void squirrelStartPlacesSevenCatchableSquirrels() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_SQUIRREL);
        Challenge ch = board.mChallenge;

        ch.squirrelStart();

        // MAX_SQUIRRELS waiting squirrels plus one zombie squirrel (Challenge.cpp:4640-4682).
        assertEquals(8, countGridItems(board, GridItemType.GRIDITEM_SQUIRREL));
        assertEquals(Challenge.MAX_SQUIRRELS, ch.squirrelCountUncaught());
    }

    // -------------------------------------------------------------------------------------------
    // Zombiquarium (Challenge.cpp:3307-3313, 3381-3443)
    // -------------------------------------------------------------------------------------------

    @Test
    void zombiquariumSpawnSnorkleAddsAZombie() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM);
        Challenge ch = board.mChallenge;

        int aBefore = board.mZombies.mSize;
        Zombie aZombie = ch.zombiquariumSpawnSnorkle();

        assertNotNull(aZombie);
        assertEquals(ZombieType.ZOMBIE_SNORKEL, aZombie.mZombieType);
        assertEquals(aBefore + 1, board.mZombies.mSize);
    }

    // -------------------------------------------------------------------------------------------
    // Scary Potter (Challenge.cpp:3534-3763)
    // -------------------------------------------------------------------------------------------

    @Test
    void scaryPotterPopulateFillsTheBoardWithPots() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_SCARY_POTTER_1);
        Challenge ch = board.mChallenge;

        ch.scaryPotterPopulate();

        int aPots = countGridItems(board, GridItemType.GRIDITEM_SCARY_POT);
        assertTrue(aPots > 0, "Scary Potter must place pots");
        assertEquals(aPots, ch.mScaryPotterPots);
        assertEquals(aPots, ch.scaryPotterCountPots());
    }

    // -------------------------------------------------------------------------------------------
    // I,Zombie (Challenge.cpp:4175-4401)
    // -------------------------------------------------------------------------------------------

    @Test
    void iZombieInitLevelPlacesBrainsAndDefenders() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1);
        Challenge ch = board.mChallenge;

        ch.iZombieInitLevel();

        assertEquals(Challenge.I_ZOMBIE_WINNING_SCORE, countGridItems(board, GridItemType.GRIDITEM_IZOMBIE_BRAIN));
        assertEquals(0, ch.mChallengeScore);
        assertTrue(board.mPlants.mSize > 0, "the I,Zombie stage must place the defenders");
    }

    // -------------------------------------------------------------------------------------------
    // Last Stand (Challenge.cpp:4855-4903)
    // -------------------------------------------------------------------------------------------

    @Test
    void lastStandUpdateRevealsTheStoreButton() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_LAST_STAND);
        Challenge ch = board.mChallenge;

        assertNotNull(board.mStoreButton);
        assertTrue(board.mStoreButton.mBtnNoDraw);

        ch.lastStandUpdate();

        assertFalse(board.mStoreButton.mBtnNoDraw);
        assertFalse(board.mStoreButton.mDisabled);
        assertTrue(board.mStoreButton.mLabel.contains("START_ONSLAUGHT"),
                "label was " + board.mStoreButton.mLabel);
        assertEquals(300, board.mStoreButton.mX);
        assertEquals(559, board.mStoreButton.mY);
    }

    // -------------------------------------------------------------------------------------------
    // Weather (Challenge.cpp:1710-1760, 4825-4853)
    // -------------------------------------------------------------------------------------------

    @Test
    void stormyNightUpdateSchedulesTheNextFlash() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_STORMY_NIGHT);
        Challenge ch = board.mChallenge;
        app.mGameScene = GameScenes.SCENE_PLAYING;
        board.mPaused = false;
        ch.mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_1;
        ch.mChallengeStateCounter = 1;

        ch.updateStormyNight();

        assertTrue(ch.mChallengeStateCounter > Challenge.STORM_FLASH_TIME,
                "the next flash must be scheduled in the future");
        assertTrue(ch.mChallengeState >= ChallengeState.STATECHALLENGE_STORM_FLASH_1
                && ch.mChallengeState <= ChallengeState.STATECHALLENGE_STORM_FLASH_3);
    }

    @Test
    void updateRainSpawnsSplashesWhenTheCounterRunsOut() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS);
        Challenge ch = board.mChallenge;
        ch.mRainCounter = 0;

        int aBefore = app.mAddedReanimations;
        ch.updateRain();

        if (!board.mCutScene.isBeforePreloading()) {
            assertEquals(aBefore + 3, app.mAddedReanimations, "three rain splashes per cycle");
            assertTrue(ch.mRainCounter > 0);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Slot Machine (Challenge.cpp:916-943)
    // -------------------------------------------------------------------------------------------

    @Test
    void updateToolTipOnlyHandlesTheSlotMachineHandle() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_ADVENTURE);
        Challenge ch = board.mChallenge;

        assertEquals(0, ch.updateToolTip(0, 0, null), "non slot-machine levels have no tooltip");
    }

    // -------------------------------------------------------------------------------------------
    // Tree of Wisdom (Challenge.cpp:5020-5068)
    // -------------------------------------------------------------------------------------------

    @Test
    void treeOfWisdomInitBuildsTheTreeAndClouds() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = board(app, GameMode.GAMEMODE_TREE_OF_WISDOM);
        Challenge ch = board.mChallenge;

        int aBefore = app.mAddedReanimations;
        ch.treeOfWisdomInit();

        assertEquals(aBefore + 7, app.mAddedReanimations, "one tree plus six clouds");
        assertEquals(ChallengeState.STATECHALLENGE_TREE_WAITING_TO_BABBLE, ch.mChallengeState);
        assertTrue(ch.mChallengeStateCounter > 0);
    }
}
