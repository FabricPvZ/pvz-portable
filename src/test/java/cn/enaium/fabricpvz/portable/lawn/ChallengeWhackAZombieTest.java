/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.ChallengeWhackAZombie, the Whack-a-Zombie mini-game
 * helper ported from src/Lawn/Challenge.cpp:945-991 (the mallet swing), 2491-2532 (grave raising)
 * and 4905-4921 (the tutorial state machine).
 *
 * Every fixture is a bare LawnApp double; no retail assets are needed.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;

class ChallengeWhackAZombieTest {

    /** A {@link LawnEntityTestSupport.StubZombie} that records the Helm damage it is dealt. */
    private static final class RecordingZombie extends LawnEntityTestSupport.StubZombie {

        int mHelmDamageTaken = -1;

        RecordingZombie(Board theBoard, int theRow, int theX, int theY) {
            super(theBoard, theRow, theX, theY);
        }

        @Override
        public int takeHelmDamage(int theDamage, int theDamageFlags) {
            mHelmDamageTaken = theDamage;
            return 0;
        }
    }

    // ---------------------------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------------------------

    /**
     * A whack-a-zombie board: the mode the interaction is gated on (so
     * {@code Challenge.clearCursor} keeps the hammer cursor), a hammer reanimation id that resolves
     * through the stub app and a zombie list of {@link RecordingZombie}s.
     */
    private static StubBoard newWhackBoard() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE;
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mCursorObject.mReanimCursorID = 1;
        board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_HAMMER;
        board.mZombies = new DataArray<Zombie>(() -> new RecordingZombie(board, 0, 0, 0), 32, "testzombies");
        return board;
    }

    private static RecordingZombie addZombie(StubBoard theBoard, int theRow, int theX, int theY) {
        RecordingZombie aZombie = (RecordingZombie) theBoard.mZombies.dataArrayAlloc();
        aZombie.mApp = theBoard.mApp;
        aZombie.mBoard = theBoard;
        aZombie.mRow = theRow;
        aZombie.mX = theX;
        aZombie.mY = theY;
        aZombie.mWidth = 44;
        aZombie.mHeight = 115;
        aZombie.mZombieType = ZombieType.ZOMBIE_NORMAL;
        aZombie.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        aZombie.mHasHead = true;
        aZombie.mStubRect = new Rect(theX, theY, 44, 115);
        return aZombie;
    }

    private static ChallengeWhackAZombie helper(Board theBoard) {
        return new ChallengeWhackAZombie(theBoard.mChallenge);
    }

    private static TestLawnApp app(Board theBoard) {
        return (TestLawnApp) theBoard.mApp;
    }

    private static int gravestoneCount(Board theBoard) {
        int aCount = 0;
        for (GridItem aGridItem : theBoard.mGridItems) {
            if (!aGridItem.mDead && aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                aCount++;
            }
        }
        return aCount;
    }

    /** The first live gravestone (grid-item handles are opaque, so tests must not index by literal). */
    private static GridItem firstGraveStone(Board theBoard) {
        for (GridItem aGridItem : theBoard.mGridItems) {
            if (!aGridItem.mDead && aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                return aGridItem;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // MouseDownWhackAZombie (Challenge.cpp:945-991)
    // ---------------------------------------------------------------------------------------

    @Test
    void swingResetsTheHammerReanimationAndPlaysTheSwingFoley() {
        StubBoard board = newWhackBoard();
        app(board).mReanim.mAnimTime = 0.0f;

        // No zombie anywhere near the click: only the swing animation/audio fires.
        helper(board).mouseDownWhackAZombie(100, 100);

        assertEquals(0.2f, app(board).mReanim.mAnimTime, 1e-6f,
                "Challenge.cpp:947 sets the cursor reanimation time to 0.2");
        assertEquals(List.of(FoleyType.FOLEY_SWING), app(board).mFoleysPlayed);
        assertEquals(0, app(board).mAddedParticles);
    }

    @Test
    void bareZombieInTheSwingCircleDiesWithLootAndTheCursorClears() {
        StubBoard board = newWhackBoard();
        RecordingZombie aZombie = addZombie(board, 2, 100, 100);

        // Circle is (theX, theY - 20, 45) (Challenge.cpp:960); the zombie rect starts at (100,100).
        helper(board).mouseDownWhackAZombie(100, 100);

        assertTrue(aZombie.mDead, "DieWithLoot marks the zombie dead");
        assertTrue(aZombie.mDiedWithLoot);
        assertEquals(List.of(FoleyType.FOLEY_SWING, FoleyType.FOLEY_BONK), app(board).mFoleysPlayed);
        assertEquals(1, app(board).mAddedParticles, "PARTICLE_POW is emitted at (theX - 3, theY + 9)");
        // Board::ClearCursor then Challenge::ClearCursor; in whack-a-zombie the mode re-arms the
        // hammer cursor (Challenge.cpp:1128-1130).
        assertEquals(CursorType.CURSOR_TYPE_HAMMER, board.mCursorObject.mCursorType);
    }

    @Test
    void zombieOutsideTheSwingCircleIsNotHit() {
        StubBoard board = newWhackBoard();
        RecordingZombie aZombie = addZombie(board, 4, 700, 500);

        helper(board).mouseDownWhackAZombie(100, 100);

        assertFalse(aZombie.mDead);
        assertEquals(List.of(FoleyType.FOLEY_SWING), app(board).mFoleysPlayed);
        assertEquals(0, app(board).mAddedParticles);
    }

    @Test
    void pailHelmAbsorbsTheBlowWithTheShieldHitFoley() {
        StubBoard board = newWhackBoard();
        RecordingZombie aZombie = addZombie(board, 2, 100, 100);
        aZombie.mHelmType = HelmType.HELMTYPE_PAIL;

        helper(board).mouseDownWhackAZombie(100, 100);

        assertEquals(900, aZombie.mHelmDamageTaken, "Challenge.cpp:978 TakeHelmDamage(900, 0)");
        assertEquals(List.of(FoleyType.FOLEY_SWING, FoleyType.FOLEY_SHIELD_HIT), app(board).mFoleysPlayed);
        assertFalse(aZombie.mDead, "a helmed zombie survives the mallet");
        assertEquals(0, app(board).mAddedParticles);
        assertEquals(CursorType.CURSOR_TYPE_HAMMER, board.mCursorObject.mCursorType, "the cursor is not cleared");
    }

    @Test
    void coneHelmUsesThePlasticHitFoley() {
        StubBoard board = newWhackBoard();
        RecordingZombie aZombie = addZombie(board, 2, 100, 100);
        aZombie.mHelmType = HelmType.HELMTYPE_TRAFFIC_CONE;

        helper(board).mouseDownWhackAZombie(100, 100);

        assertEquals(900, aZombie.mHelmDamageTaken);
        assertEquals(List.of(FoleyType.FOLEY_SWING, FoleyType.FOLEY_PLASTIC_HIT), app(board).mFoleysPlayed);
    }

    @Test
    void topmostZombieByRenderOrderTakesTheBlow() {
        StubBoard board = newWhackBoard();
        RecordingZombie aLower = addZombie(board, 2, 100, 100);
        RecordingZombie aHigher = addZombie(board, 2, 120, 100);
        aLower.mRenderOrder = 10;
        aHigher.mRenderOrder = 20;

        helper(board).mouseDownWhackAZombie(100, 100);

        assertTrue(aHigher.mDead, "the greater mRenderOrder wins (Challenge.cpp:963)");
        assertFalse(aLower.mDead);
    }

    @Test
    void dyingZombieIsSkipped() {
        StubBoard board = newWhackBoard();
        RecordingZombie aZombie = addZombie(board, 2, 100, 100);
        aZombie.mDeadOrDying = true;

        helper(board).mouseDownWhackAZombie(100, 100);

        assertFalse(aZombie.mDead, "IsDeadOrDying zombies are ignored, not killed");
        assertEquals(0, app(board).mAddedParticles);
    }

    // ---------------------------------------------------------------------------------------
    // WhackAZombiePlaceGraves (Challenge.cpp:2491-2532)
    // ---------------------------------------------------------------------------------------

    @Test
    void placeGravesRaisesTheRequestedNumberOfGravestonesOutsideTheFirstColumns() {
        Board board = LawnEntityTestSupport.newBoard();
        assertEquals(0, gravestoneCount(board));

        helper(board).whackAZombiePlaceGraves(3);

        assertEquals(3, gravestoneCount(board), "the first 3 of the 36 grass cells above column 2");
        assertEquals(3, board.getGraveStonesCount());
        for (GridItem aGridItem : board.mGridItems) {
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                assertTrue(aGridItem.mGridX >= 3, "Challenge.cpp:2495 starts the column scan at 3");
            }
        }
    }

    @Test
    void placeGravesIsClampedToTheAvailableCells() {
        Board board = LawnEntityTestSupport.newBoard();
        // Only one cell qualifies on this board.
        for (int aCol = 0; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
                board.mGridSquareType[aCol][aRow] = GridSquareType.GRIDSQUARE_NONE;
            }
        }
        board.mGridSquareType[6][1] = GridSquareType.GRIDSQUARE_GRASS;

        helper(board).whackAZombiePlaceGraves(5);

        assertEquals(1, gravestoneCount(board), "std::min(theGraveCount, aPickCount) clamps to 1");
        GridItem aGrave = firstGraveStone(board);
        assertEquals(6, aGrave.mGridX);
        assertEquals(1, aGrave.mGridY);
    }

    @Test
    void placeGravesKillsThePlantsItBuries() {
        Board board = LawnEntityTestSupport.newBoard();
        for (int aCol = 0; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
                board.mGridSquareType[aCol][aRow] = GridSquareType.GRIDSQUARE_NONE;
            }
        }
        board.mGridSquareType[5][2] = GridSquareType.GRIDSQUARE_GRASS;

        Common.sRand(0x5EED);
        Plant aPlant = board.addPlant(5, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        assertFalse(aPlant.mDead);

        helper(board).whackAZombiePlaceGraves(1);

        assertTrue(aPlant.mDead, "a plant on the picked cell dies before the grave stone lands");
        assertEquals(1, gravestoneCount(board));
        GridItem aGrave = firstGraveStone(board);
        assertEquals(5, aGrave.mGridX);
        assertEquals(2, aGrave.mGridY);
    }

    @Test
    void noGrassCellsMeansNoGraves() {
        Board board = LawnEntityTestSupport.newBoard();
        for (int aCol = 0; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
                board.mGridSquareType[aCol][aRow] = GridSquareType.GRIDSQUARE_POOL;
            }
        }

        helper(board).whackAZombiePlaceGraves(4);

        assertEquals(0, gravestoneCount(board));
    }

    // ---------------------------------------------------------------------------------------
    // WhackAZombieUpdate (Challenge.cpp:4905-4921)
    // ---------------------------------------------------------------------------------------

    @Test
    void tutorialWalksOffBeforePickSeedToPickSeedToCompleted() {
        Board board = LawnEntityTestSupport.newBoard();
        board.mTutorialState = TutorialState.TUTORIAL_OFF;
        board.mSunMoney = 50;
        ChallengeWhackAZombie aHelper = helper(board);

        aHelper.whackAZombieUpdate();
        assertEquals(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_BEFORE_PICK_SEED, board.mTutorialState);
        assertEquals(1500, board.mTutorialTimer, "Challenge.cpp:4909 arms the 1500-tick timer");

        aHelper.whackAZombieUpdate();
        assertEquals(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_BEFORE_PICK_SEED, board.mTutorialState,
                "the state holds until the timer runs out");

        board.mTutorialTimer = 0;
        aHelper.whackAZombieUpdate();
        assertEquals(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_PICK_SEED, board.mTutorialState);
        assertEquals(400, board.mTutorialTimer, "Challenge.cpp:4914 arms the 400-tick timer");

        board.mTutorialTimer = 0;
        aHelper.whackAZombieUpdate();
        assertEquals(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_COMPLETED, board.mTutorialState,
                "Challenge.cpp:4918 completes the tutorial");
    }

    @Test
    void tutorialDoesNotStartWithoutSun() {
        Board board = LawnEntityTestSupport.newBoard();
        board.mTutorialState = TutorialState.TUTORIAL_OFF;
        board.mSunMoney = 0;

        helper(board).whackAZombieUpdate();

        assertEquals(TutorialState.TUTORIAL_OFF, board.mTutorialState);
    }
}
