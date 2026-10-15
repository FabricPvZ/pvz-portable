/*
 * Focused tests for cn.enaium.fabricpvz.portable.lawn.ChallengeBeghouled.
 *
 * Drives the ported Beghouled / Beghouled Twist bodies (Challenge.cpp:324-889, 1133-1376,
 * 1854-1886, 3235-3305) headlessly: match detection and the twist probe run on hand-built
 * {@link BeghouledBoardState}s, the plant-moving/removing/flashing paths run on a real
 * {@link Board} whose entities come from the shared headless fixtures.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

class ChallengeBeghouledTest {

    private static final int NORMAL = ChallengeState.STATECHALLENGE_NORMAL;
    private static final int MOVING = ChallengeState.STATECHALLENGE_BEGHOULED_MOVING;
    private static final int FALLING = ChallengeState.STATECHALLENGE_BEGHOULED_FALLING;
    private static final int NO_MATCHES = ChallengeState.STATECHALLENGE_BEGHOULED_NO_MATCHES;

    /** A headless board in the requested challenge mode, seeded for a deterministic fill. */
    private static Board newBoard(int theGameMode) {
        Common.sRand(0x5EED);
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = theGameMode;
        Board board = new Board(app);
        board.mLevel = 1;
        board.mMainCounter = 5;
        board.mSunMoney = 150;
        return board;
    }

    private static ChallengeBeghouled helper(Board theBoard) {
        return new ChallengeBeghouled(theBoard.mChallenge);
    }

    private static int livePlants(Board theBoard) {
        int aCount = 0;
        for (Plant aPlant : theBoard.mPlants) {
            if (!aPlant.mDead) {
                aCount++;
            }
        }
        return aCount;
    }

    private static int filledSquares(Board theBoard) {
        BeghouledBoardState aState = new BeghouledBoardState();
        theBoard.mChallenge.loadBeghouledBoardState(aState);
        int aCount = 0;
        for (int aX = 0; aX < Board.MAX_GRID_SIZE_X; aX++) {
            for (int aY = 0; aY < Board.MAX_GRID_SIZE_Y; aY++) {
                if (aState.mSeedType[aX][aY] != SeedType.SEED_NONE) {
                    aCount++;
                }
            }
        }
        return aCount;
    }

    // -----------------------------------------------------------------------------------------
    // Pure board-state logic
    // -----------------------------------------------------------------------------------------

    @Test
    void matchLengthsAndGetPlantAtMatchUpstream() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        BeghouledBoardState s = new BeghouledBoardState();
        s.mSeedType[0][2] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[1][2] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[2][2] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[4][0] = SeedType.SEED_WALLNUT;
        s.mSeedType[4][1] = SeedType.SEED_WALLNUT;
        s.mSeedType[4][2] = SeedType.SEED_WALLNUT;

        // A run is only reported from its first cell (Challenge.cpp:698-708).
        assertEquals(3, cb.beghouledHorizontalMatchLength(0, 2, s));
        assertEquals(0, cb.beghouledHorizontalMatchLength(1, 2, s));
        assertEquals(3, cb.beghouledVerticalMatchLength(4, 0, s));
        assertEquals(0, cb.beghouledVerticalMatchLength(4, 1, s));

        assertEquals(SeedType.SEED_PEASHOOTER, cb.beghouledGetPlantAt(0, 2, s));
        assertEquals(SeedType.SEED_NONE, cb.beghouledGetPlantAt(-1, 0, s));
        assertEquals(SeedType.SEED_NONE, cb.beghouledGetPlantAt(9, 0, s)); // > BEGHOULED_MAX_GRIDSIZEX
        assertEquals(SeedType.SEED_NONE, cb.beghouledGetPlantAt(0, 6, s)); // > BEGHOULED_MAX_GRIDSIZEY

        assertEquals(1, cb.beghouledBoardHasMatch(s));
        assertEquals(0, cb.beghouledBoardHasMatch(new BeghouledBoardState()));
    }

    @Test
    void twistValidMoveAndMoveCausesMatch() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        ChallengeBeghouled cb = helper(board);

        // A 2x2 whose quarter-turn drops the two peashooters into column 1, completing a vertical
        // run with (1,2). The probe must restore the state afterwards.
        BeghouledBoardState s = new BeghouledBoardState();
        s.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[1][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[0][1] = SeedType.SEED_WALLNUT;
        s.mSeedType[1][1] = SeedType.SEED_SNOWPEA;
        s.mSeedType[1][2] = SeedType.SEED_PEASHOOTER;

        assertEquals(1, cb.beghouledTwistValidMove(0, 0, s));
        assertEquals(1, cb.beghouledTwistMoveCausesMatch(0, 0, s));
        assertEquals(SeedType.SEED_PEASHOOTER, s.mSeedType[1][0]);
        assertEquals(SeedType.SEED_SNOWPEA, s.mSeedType[1][1]);

        assertEquals(0, cb.beghouledTwistValidMove(0, -1, s)); // the (x, -1) guard

        BeghouledBoardState hole = new BeghouledBoardState();
        hole.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        assertEquals(0, cb.beghouledTwistValidMove(0, 0, hole));
        assertEquals(0, cb.beghouledTwistMoveCausesMatch(0, 0, hole));
        assertEquals(0, cb.beghouledTwistValidMove(7, 4, s)); // outside the 7x4 twist area
    }

    @Test
    void twistSquareFromMouseMapsAndRejectsOutsideTheArea() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        ChallengeBeghouled cb = helper(board);

        int[] xy = new int[2];
        assertEquals(1, cb.beghouledTwistSquareFromMouse(
                board.gridToPixelX(2, 1) + 40, board.gridToPixelY(2, 1) + 40, xy));
        assertEquals(2, xy[0]);
        assertEquals(1, xy[1]);

        // grid x 7 is outside the x <= 6 twist area: the out-params are forced to -1.
        int[] outside = new int[2];
        assertEquals(0, cb.beghouledTwistSquareFromMouse(
                board.gridToPixelX(7, 0) + 40, board.gridToPixelY(7, 0) + 40, outside));
        assertEquals(-1, outside[0]);
        assertEquals(-1, outside[1]);
    }

    @Test
    void isValidMoveRejectsCratersAndEmptySources() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        BeghouledBoardState s = new BeghouledBoardState();
        s.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[1][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[2][0] = SeedType.SEED_WALLNUT;
        s.mSeedType[3][0] = SeedType.SEED_PEASHOOTER;

        // Swapping the wallnut into column 3 completes the peashooter run (Challenge.cpp:419-440).
        assertEquals(1, cb.beghouledIsValidMove(2, 0, 3, 0, s));
        assertEquals(SeedType.SEED_WALLNUT, s.mSeedType[2][0], "the probe restores the board");

        board.mChallenge.mBeghouledEated[3][0] = 1; // eaten square -> never a move target
        assertEquals(0, cb.beghouledIsValidMove(2, 0, 3, 0, s));
        assertEquals(0, cb.beghouledIsValidMove(0, 1, 0, 2, s)); // empty source
        assertEquals(0, cb.beghouledIsValidMove(-1, 0, 0, 0, s)); // out of bounds
    }

    // -----------------------------------------------------------------------------------------
    // Fill / pick / match detection
    // -----------------------------------------------------------------------------------------

    @Test
    void pickSeedFiltersSeedsThatWouldCompleteAMatch() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        BeghouledBoardState s = new BeghouledBoardState();
        s.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[1][0] = SeedType.SEED_PEASHOOTER;

        for (int i = 0; i < 64; i++) {
            int aPicked = cb.beghouledPickSeed(2, 0, s, 0);
            assertNotEquals(SeedType.SEED_PEASHOOTER, aPicked, "a matching seed must be filtered");
            assertTrue(aPicked == SeedType.SEED_PUFFSHROOM || aPicked == SeedType.SEED_STARFRUIT
                    || aPicked == SeedType.SEED_MAGNETSHROOM || aPicked == SeedType.SEED_SNOWPEA
                    || aPicked == SeedType.SEED_WALLNUT, "unexpected pick " + aPicked);
            assertEquals(SeedType.SEED_NONE, s.mSeedType[2][0], "the probe leaves the square empty");
        }

        // With the upgrades bought the base seeds are replaced (Challenge.cpp:750-770).
        board.mChallenge.mBeghouledPurcasedUpgrade[BeghouledUpgrade.BEGHOULED_UPGRADE_REPEATER] = 1;
        board.mChallenge.mBeghouledPurcasedUpgrade[BeghouledUpgrade.BEGHOULED_UPGRADE_FUMESHROOM] = 1;
        board.mChallenge.mBeghouledPurcasedUpgrade[BeghouledUpgrade.BEGHOULED_UPGRADE_TALLNUT] = 1;
        for (int i = 0; i < 64; i++) {
            int aPicked = cb.beghouledPickSeed(2, 0, s, 1);
            assertTrue(aPicked == SeedType.SEED_REPEATER || aPicked == SeedType.SEED_FUMESHROOM
                    || aPicked == SeedType.SEED_TALLNUT || aPicked == SeedType.SEED_STARFRUIT
                    || aPicked == SeedType.SEED_MAGNETSHROOM || aPicked == SeedType.SEED_SNOWPEA,
                    "unexpected upgraded pick " + aPicked);
        }
    }

    @Test
    void checkForPossibleMovesHonoursTheMode() {
        Board beghouled = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(beghouled);

        BeghouledBoardState s = new BeghouledBoardState();
        s.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[1][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[2][0] = SeedType.SEED_WALLNUT;
        s.mSeedType[3][0] = SeedType.SEED_PEASHOOTER;
        assertEquals(1, cb.beghouledCheckForPossibleMoves(s));
        assertEquals(0, cb.beghouledCheckForPossibleMoves(new BeghouledBoardState()));

        Board twist = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        ChallengeBeghouled tcb = helper(twist);

        BeghouledBoardState t = new BeghouledBoardState();
        t.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        t.mSeedType[1][0] = SeedType.SEED_PEASHOOTER;
        t.mSeedType[0][1] = SeedType.SEED_WALLNUT;
        t.mSeedType[1][1] = SeedType.SEED_SNOWPEA;
        t.mSeedType[1][2] = SeedType.SEED_PEASHOOTER;
        assertEquals(1, tcb.beghouledTwistMoveCausesMatch(0, 0, t));
        assertEquals(1, tcb.beghouledCheckForPossibleMoves(t));
        assertEquals(0, tcb.beghouledCheckForPossibleMoves(new BeghouledBoardState()));
    }

    @Test
    void fillHolesAndCreatePlantsPopulateAnEmptyBoard() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        BeghouledBoardState empty = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(empty);
        BeghouledBoardState filled = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(filled);

        cb.beghouledFillHoles(filled, 0);
        assertEquals(0, cb.beghouledBoardHasMatch(filled), "the fill never leaves a match");

        int aFilled = 0;
        for (int aX = 0; aX < Board.MAX_GRID_SIZE_X; aX++) {
            for (int aY = 0; aY < Board.MAX_GRID_SIZE_Y; aY++) {
                if (filled.mSeedType[aX][aY] != SeedType.SEED_NONE) {
                    aFilled++;
                }
            }
        }
        assertTrue(aFilled > 30);

        cb.beghouledCreatePlants(empty, filled);
        assertEquals(aFilled, livePlants(board), "one plant per newly filled square");
    }

    @Test
    void populateBoardFillsTheBoardFromEmpty() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        cb.beghouledPopulateBoard();

        assertEquals(filledSquares(board), livePlants(board));
        assertTrue(livePlants(board) > 30);
    }

    @Test
    void removeMatchesScoresAVerticalRunToo() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant v0 = board.newPlant(3, 0, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        Plant v1 = board.newPlant(3, 1, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        Plant v2 = board.newPlant(3, 2, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);

        BeghouledBoardState s = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(s);
        cb.beghouledRemoveMatches(s);

        assertTrue(v0.mDead);
        assertTrue(v1.mDead);
        assertTrue(v2.mDead);
        assertEquals(1, board.mChallenge.mChallengeScore, "only the run's first cell scores");
    }

    @Test
    void makeStartBoardFillsTheBoardWithoutLeavingAMatch() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        cb.beghouledMakeStartBoard();

        assertEquals(filledSquares(board), livePlants(board), "one plant per filled square");
        assertTrue(livePlants(board) > 30, "the start board fills the 8x5 grid");

        // The fill must not leave a match behind (Challenge.cpp:827).
        BeghouledBoardState s = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(s);
        assertEquals(0, cb.beghouledBoardHasMatch(s));
    }

    @Test
    void stuckStateEntersNoMatchesOnlyFromNormal() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        board.mChallenge.mChallengeState = MOVING;
        cb.beghouledCheckStuckState();
        assertEquals(MOVING, board.mChallenge.mChallengeState, "only NORMAL can get stuck");

        board.mChallenge.mChallengeState = NORMAL;
        cb.beghouledCheckStuckState(); // empty board -> no possible move
        assertEquals(NO_MATCHES, board.mChallenge.mChallengeState);
        assertEquals(500, board.mChallenge.mChallengeStateCounter);
        assertTrue(board.mHelpDisplayed[AdviceType.ADVICE_BEGHOULED_NO_MOVES]);
    }

    // -----------------------------------------------------------------------------------------
    // Plant movement / removal / scoring
    // -----------------------------------------------------------------------------------------

    @Test
    void removeMatchesKillsTheRunAndScores() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant p0 = board.newPlant(0, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant p1 = board.newPlant(1, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant p2 = board.newPlant(2, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);

        BeghouledBoardState s = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(s);
        cb.beghouledRemoveMatches(s);

        assertTrue(p0.mDead);
        assertTrue(p1.mDead);
        assertTrue(p2.mDead);
        assertEquals(1, board.mChallenge.mChallengeScore);
        assertEquals(1, board.mChallenge.mBeghouledMatchesThisMove);
        // The first match opens the four-packet upgrade bar (Challenge.cpp:616-640).
        assertEquals(4, board.mSeedBank.mNumPackets);
        assertEquals(SeedType.SEED_REPEATER, board.mSeedBank.mSeedPackets[0].mPacketType);
        assertEquals(SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE, board.mSeedBank.mSeedPackets[3].mPacketType);
    }

    @Test
    void scoreOpensTheBarAndWinsAtTheWinningScore() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        cb.beghouledScore(3, 2, 3, 1);
        assertEquals(1, board.mChallenge.mChallengeScore);
        assertEquals(1, board.mChallenge.mBeghouledMatchesThisMove);
        assertEquals(4, board.mSeedBank.mNumPackets);
        assertEquals(SeedType.SEED_FUMESHROOM, board.mSeedBank.mSeedPackets[1].mPacketType);
        assertEquals(SeedType.SEED_TALLNUT, board.mSeedBank.mSeedPackets[2].mPacketType);

        board.mChallenge.mChallengeScore = Challenge.BEGHOULED_WINNING_SCORE - 1;
        cb.beghouledScore(3, 2, 4, 0);
        assertEquals(Challenge.BEGHOULED_WINNING_SCORE, board.mChallenge.mChallengeScore);
        assertEquals(BoardResult.BOARDRESULT_WON, board.mApp.mBoardResult);
        assertTrue(board.mLevelAwardSpawned);
    }

    @Test
    void plantsFallIntoEmptySquaresButNotCraters() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant aPlant = board.newPlant(1, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        BeghouledBoardState s = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(s);

        cb.beghouledFallIntoSquare(1, 2, s);
        assertEquals(2, aPlant.mRow);
        assertEquals(SeedType.SEED_PEASHOOTER, s.mSeedType[1][2]);
        assertEquals(SeedType.SEED_NONE, s.mSeedType[1][0]);
        assertEquals(FALLING, board.mChallenge.mChallengeState);

        board.mChallenge.mBeghouledEated[1][3] = 1;
        BeghouledBoardState s2 = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(s2);
        cb.beghouledFallIntoSquare(1, 3, s2);
        assertEquals(2, aPlant.mRow, "a crater never receives a falling plant");

        // The whole-column pass leaves the same invariant: nothing sits above an empty square.
        BeghouledBoardState s3 = new BeghouledBoardState();
        board.mChallenge.loadBeghouledBoardState(s3);
        cb.beghouledMakePlantsFall(s3);
        assertEquals(SeedType.SEED_PEASHOOTER, s3.mSeedType[1][4]);
        assertEquals(SeedType.SEED_NONE, s3.mSeedType[1][2]);
    }

    @Test
    void updateBeghouledPlantMovesTowardItsGridSquare() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant aPlant = board.newPlant(2, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        int aTargetX = board.gridToPixelX(2, 1);
        int aTargetY = board.gridToPixelY(2, 1);
        aPlant.mX = aTargetX - 50;
        aPlant.mY = aTargetY - 50;

        // In the MOVING state the plant advances a fixed 3 px per tick (Challenge.cpp:1139).
        board.mChallenge.mChallengeState = MOVING;
        assertEquals(1, cb.updateBeghouledPlant(aPlant));
        assertEquals(aTargetX - 47, aPlant.mX);
        assertEquals(aTargetY - 47, aPlant.mY);

        aPlant.mX = aTargetX;
        aPlant.mY = aTargetY;
        assertEquals(0, cb.updateBeghouledPlant(aPlant), "a settled plant reports no movement");
    }

    @Test
    void dragStartAndDragUpdateRejectAnInvalidSwap() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        cb.beghouledDragStart(120, 140);
        assertEquals(1, board.mChallenge.mBeghouledMouseCapture);
        assertEquals(120, board.mChallenge.mBeghouledMouseDownX);
        assertEquals(140, board.mChallenge.mBeghouledMouseDownY);

        Plant aPlant = board.newPlant(2, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        int aDownX = board.gridToPixelX(2, 1) + 40;
        int aDownY = board.gridToPixelY(2, 1) + 40;
        board.mChallenge.mBeghouledMouseCapture = 1;
        board.mChallenge.mBeghouledMouseDownX = aDownX;
        board.mChallenge.mBeghouledMouseDownY = aDownY;
        int aBeforeX = aPlant.mX;
        TestLawnApp app = (TestLawnApp) board.mApp;
        app.mFoleysPlayed.clear();

        // Dragging right would swap with an empty square: no match, so the plant is nudged instead.
        cb.beghouledDragUpdate(aDownX + 20, aDownY);
        assertEquals(0, board.mChallenge.mBeghouledMouseCapture);
        assertEquals(aBeforeX + 30, aPlant.mX);
        assertEquals(2, aPlant.mPlantCol, "the rejected drag leaves the plant in its column");
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_FLOOP), "the rejected drag plays the floop");
    }

    @Test
    void twistMouseDownTurnsTheQuadIntoAMatch() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        ChallengeBeghouled cb = helper(board);

        Plant p0 = board.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant p1 = board.newPlant(1, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant p2 = board.newPlant(0, 1, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        Plant p3 = board.newPlant(1, 1, SeedType.SEED_SNOWPEA, SeedType.SEED_NONE);
        board.newPlant(1, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE); // completes the run after the turn

        cb.beghouledTwistMouseDown(board.gridToPixelX(0, 0) + 40, board.gridToPixelY(0, 0) + 40);

        // The quarter turn is clockwise: (x+1,y), (x+1,y+1), (x,y+1), (x,y) (Challenge.cpp:396-404).
        assertEquals(1, p0.mPlantCol);
        assertEquals(0, p0.mRow);
        assertEquals(1, p1.mRow);
        assertEquals(0, p2.mRow);
        assertEquals(0, p3.mPlantCol);
        assertEquals(MOVING, board.mChallenge.mChallengeState);
        assertEquals(100, board.mChallenge.mChallengeStateCounter);
    }

    @Test
    void twistMouseDownNudgesTheQuadWhenItWouldNotMatch() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        ChallengeBeghouled cb = helper(board);
        TestLawnApp app = (TestLawnApp) board.mApp;
        app.mFoleysPlayed.clear();

        Plant p0 = board.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant p1 = board.newPlant(1, 0, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        Plant p2 = board.newPlant(0, 1, SeedType.SEED_SNOWPEA, SeedType.SEED_NONE);
        Plant p3 = board.newPlant(1, 1, SeedType.SEED_MAGNETSHROOM, SeedType.SEED_NONE);

        cb.beghouledTwistMouseDown(board.gridToPixelX(0, 0) + 40, board.gridToPixelY(0, 0) + 40);

        // No match -> the four plants twitch outward and the floop plays (Challenge.cpp:382-387).
        assertEquals(board.gridToPixelX(0, 0) + 20, p0.mX);
        assertEquals(board.gridToPixelY(1, 0) + 20, p1.mY);
        assertEquals(board.gridToPixelY(0, 1) - 20, p2.mY);
        assertEquals(board.gridToPixelX(1, 1) - 20, p3.mX);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_FLOOP));
        assertEquals(NORMAL, board.mChallenge.mChallengeState);
    }

    // -----------------------------------------------------------------------------------------
    // Flashing
    // -----------------------------------------------------------------------------------------

    @Test
    void flashPlantRedirectsTheMovingCellAndOnlyStartsOnce() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant aPlant = board.newPlant(3, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        cb.beghouledFlashPlant(2, 1, 2, 1, 3, 1); // the "from" cell flashes the "to" cell instead
        assertEquals(300, aPlant.mBeghouledFlashCountdown);

        cb.beghouledFlashPlant(2, 1, 2, 1, 3, 1);
        assertEquals(300, aPlant.mBeghouledFlashCountdown, "an active flash is not restarted");
    }

    @Test
    void flashFromBoardStateFlashesTheProbedSwap() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant q0 = board.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant q1 = board.newPlant(1, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        board.newPlant(2, 0, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        Plant q3 = board.newPlant(3, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);

        BeghouledBoardState s = new BeghouledBoardState();
        s.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[1][0] = SeedType.SEED_PEASHOOTER;
        s.mSeedType[2][0] = SeedType.SEED_WALLNUT;
        s.mSeedType[3][0] = SeedType.SEED_PEASHOOTER;

        assertEquals(1, cb.beghouledFlashFromBoardState(s, 2, 0, 3, 0));
        assertEquals(SeedType.SEED_WALLNUT, s.mSeedType[2][0], "the probe restores the board");
        assertEquals(300, q0.mBeghouledFlashCountdown);
        assertEquals(300, q1.mBeghouledFlashCountdown);
        assertEquals(300, q3.mBeghouledFlashCountdown, "the swapped-in cell flashes the other square");

        // A crater at either square cancels the flash (Challenge.cpp:1214-1216).
        board.mChallenge.mBeghouledEated[3][0] = 1;
        assertEquals(0, cb.beghouledFlashFromBoardState(s, 2, 0, 3, 0));
    }

    @Test
    void twistFlashMatchFlashesTheProbedQuad() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        ChallengeBeghouled cb = helper(board);

        Plant p0 = board.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant p1 = board.newPlant(1, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant p2 = board.newPlant(0, 1, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        Plant p3 = board.newPlant(1, 1, SeedType.SEED_SNOWPEA, SeedType.SEED_NONE);

        BeghouledBoardState t = new BeghouledBoardState();
        t.mSeedType[0][0] = SeedType.SEED_PEASHOOTER;
        t.mSeedType[1][0] = SeedType.SEED_PEASHOOTER;
        t.mSeedType[0][1] = SeedType.SEED_WALLNUT;
        t.mSeedType[1][1] = SeedType.SEED_SNOWPEA;
        t.mSeedType[1][2] = SeedType.SEED_PEASHOOTER;

        assertEquals(1, cb.beghouledTwistFlashMatch(t, 0, 0));
        assertEquals(300, p0.mBeghouledFlashCountdown);
        assertEquals(300, p1.mBeghouledFlashCountdown);
        assertEquals(300, p2.mBeghouledFlashCountdown);
        assertEquals(300, p3.mBeghouledFlashCountdown);

        assertEquals(0, cb.beghouledTwistFlashMatch(new BeghouledBoardState(), 0, 0));
    }

    @Test
    void flashAMatchFindsARealSwap() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant q0 = board.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        board.newPlant(1, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        board.newPlant(2, 0, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        Plant q3 = board.newPlant(3, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);

        cb.beghouledFlashAMatch();

        // Swapping columns 2/3 completes the run, so those two plants flash.
        assertEquals(300, q0.mBeghouledFlashCountdown);
        assertEquals(300, q3.mBeghouledFlashCountdown);
    }

    @Test
    void cancelMatchFlashingClampsOnlyLivePlants() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant aLive = board.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant aDead = board.newPlant(1, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        aLive.mEatenFlashCountdown = 100;
        aDead.mEatenFlashCountdown = 100;
        aDead.die();

        cb.beghouledCancelMatchFlashing();
        assertEquals(25, aLive.mEatenFlashCountdown);
        assertEquals(100, aDead.mEatenFlashCountdown, "dead plants are skipped");
    }

    // -----------------------------------------------------------------------------------------
    // Crater packet + state machine
    // -----------------------------------------------------------------------------------------

    @Test
    void craterPacketActivatesOnlyWithFivePacketsAndAnEatenSquare() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        assertEquals(0, cb.beghouledCanClearCrater());

        board.mSeedBank.mNumPackets = 4;
        board.mSeedBank.mSeedPackets[4].setPacketType(SeedType.SEED_BEGHOULED_BUTTON_CRATER, SeedType.SEED_NONE);
        board.mSeedBank.mSeedPackets[4].setActivate(true);
        board.mChallenge.mBeghouledEated[3][2] = 1;
        cb.beghouledUpdateCraters();
        assertTrue(board.mSeedBank.mSeedPackets[4].mActive, "not five packets -> the packet is left alone");

        board.mSeedBank.mNumPackets = 5;
        assertEquals(1, cb.beghouledCanClearCrater());
        cb.beghouledUpdateCraters();
        assertTrue(board.mSeedBank.mSeedPackets[4].mActive);

        cb.beghouledClearCrater(1);
        assertEquals(0, board.mChallenge.mBeghouledEated[3][2]);
        assertEquals(0, cb.beghouledCanClearCrater());
        cb.beghouledUpdateCraters();
        assertFalse(board.mSeedBank.mSeedPackets[4].mActive, "an empty board disables the crater packet");
    }

    @Test
    void zombieAtePlantMarksTheSquareAndUnlocksTheCrater() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        board.mSeedBank.mNumPackets = 4;
        Plant aPlant = board.newPlant(4, 3, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        cb.zombieAtePlant(aPlant);

        assertEquals(1, board.mChallenge.mBeghouledEated[4][3]);
        assertEquals(5, board.mSeedBank.mNumPackets);
        assertEquals(SeedType.SEED_BEGHOULED_BUTTON_CRATER, board.mSeedBank.mSeedPackets[4].mPacketType);
        // The only plant is now gone, so the stuck check flips the machine (Challenge.cpp:1863-1865).
        assertEquals(NO_MATCHES, board.mChallenge.mChallengeState);
        assertTrue(board.mSeedBank.mSeedPackets[4].mActive);

        // Other modes ignore the eat callback entirely.
        Board adventure = newBoard(GameMode.GAMEMODE_ADVENTURE);
        ChallengeBeghouled other = helper(adventure);
        Plant aPea = adventure.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        other.zombieAtePlant(aPea);
        assertEquals(0, adventure.mChallenge.mBeghouledEated[0][0]);
    }

    @Test
    void shuffleKillsTheBoardAndRestartsFalling() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        Plant aPlant = board.newPlant(1, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        board.mChallenge.mChallengeState = NORMAL;
        cb.beghouledShuffle();

        assertTrue(aPlant.mDead);
        assertEquals(FALLING, board.mChallenge.mChallengeState);
        assertEquals(100, board.mChallenge.mChallengeStateCounter);
    }

    @Test
    void packetClickBuysUpgradesShufflesAndClearsCraters() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);
        board.mSunMoney = 5000;

        // Upgrade packet: every base plant is replaced in place (Challenge.cpp:3250-3268).
        Plant aPea = board.newPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        SeedPacket aUpgradePacket = board.mSeedBank.mSeedPackets[0];
        aUpgradePacket.setPacketType(SeedType.SEED_REPEATER, SeedType.SEED_NONE);
        board.mSeedBank.mNumPackets = 1;
        cb.beghouledPacketClicked(aUpgradePacket);

        assertEquals(1, board.mChallenge.mBeghouledPurcasedUpgrade[BeghouledUpgrade.BEGHOULED_UPGRADE_REPEATER]);
        assertTrue(aPea.mDead);
        assertFalse(aUpgradePacket.mActive);
        int aRepeaters = 0;
        for (Plant aPlant : board.mPlants) {
            if (!aPlant.mDead && aPlant.mSeedType == SeedType.SEED_REPEATER) {
                aRepeaters++;
            }
        }
        assertEquals(1, aRepeaters, "the peashooter became a repeater");

        // Shuffle packet: fills the whole board away and drops into the falling state.
        SeedPacket aShufflePacket = board.mSeedBank.mSeedPackets[1];
        aShufflePacket.setPacketType(SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE, SeedType.SEED_NONE);
        board.mChallenge.mChallengeState = NORMAL;
        cb.beghouledPacketClicked(aShufflePacket);
        assertEquals(0, livePlants(board));
        assertEquals(FALLING, board.mChallenge.mChallengeState);

        // Crater packet: clears one eaten square and restarts falling.
        board.mChallenge.mChallengeState = NORMAL;
        board.mChallenge.mBeghouledEated[2][2] = 1;
        board.mSeedBank.mNumPackets = 5;
        SeedPacket aCraterPacket = board.mSeedBank.mSeedPackets[4];
        aCraterPacket.setPacketType(SeedType.SEED_BEGHOULED_BUTTON_CRATER, SeedType.SEED_NONE);
        cb.beghouledPacketClicked(aCraterPacket);
        assertEquals(0, board.mChallenge.mBeghouledEated[2][2]);
        assertEquals(FALLING, board.mChallenge.mChallengeState);
    }

    @Test
    void startFallingSetsTheStateCounterAndClearsTheNoMovesAdvice() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        board.mChallenge.mChallengeState = NO_MATCHES;
        board.mChallenge.mChallengeStateCounter = 500;
        board.mHelpIndex = AdviceType.ADVICE_BEGHOULED_NO_MOVES;

        cb.beghouledStartFalling(FALLING);

        assertEquals(FALLING, board.mChallenge.mChallengeState);
        assertEquals(100, board.mChallenge.mChallengeStateCounter);
        assertEquals(AdviceType.ADVICE_NONE, board.mHelpIndex, "the no-moves advice is dismissed");
    }

    @Test
    void drawBeghouledDrawsCratersAndTheTwistOverlay() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        board.mChallenge.mBeghouledEated[0][0] = 1;
        board.mChallenge.mBeghouledEated[Board.MAX_GRID_SIZE_X - 1][Board.MAX_GRID_SIZE_Y - 1] = 1;
        cb.drawBeghouled(new Graphics()); // the crater cels (stub art, no-op blit)

        Board twist = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        twist.mChallenge.mChallengeGridX = 1;
        twist.mChallenge.mChallengeGridY = 1;
        helper(twist).drawBeghouled(new Graphics()); // the rotating overlay quad
    }

    @Test
    void updateBeghouledPopulatesAnEmptyBoardAndStartsFalling() {
        Board board = newBoard(GameMode.GAMEMODE_CHALLENGE_BEGHOULED);
        ChallengeBeghouled cb = helper(board);

        board.mChallenge.mChallengeState = MOVING;
        board.mChallenge.mChallengeStateCounter = 10;

        assertEquals(0, livePlants(board));
        cb.updateBeghouled();

        // With nothing moving, the MOVING state resolves the board: matches, falls and a refill.
        assertEquals(FALLING, board.mChallenge.mChallengeState);
        assertEquals(filledSquares(board), livePlants(board), "one plant per filled square");
        assertTrue(livePlants(board) > 30, "the refill fills the 8x5 board");
    }
}
