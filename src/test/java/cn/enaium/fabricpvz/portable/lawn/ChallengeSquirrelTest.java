package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;

/**
 * Focused headless tests for {@link ChallengeSquirrel} — the "Squirrel" challenge state machine
 * ported from src/Lawn/Challenge.cpp:4624-4823.
 *
 * <p>Builds a real {@link Board} through the shared {@link LawnEntityTestSupport} fixture (no retail
 * art needed) and drives the squirrel helpers directly, asserting the upstream state transitions and
 * the {@code mChallengeScore}/{@code mProgressMeterWidth} bookkeeping.
 */
class ChallengeSquirrelTest {

    private static Board newBoard() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        return LawnEntityTestSupport.newBoard(app);
    }

    private static ChallengeSquirrel newChallenge(Board theBoard) {
        return new ChallengeSquirrel(theBoard.mChallenge);
    }

    private static GridItem addSquirrel(Board theBoard, int theState, int theGridX, int theGridY) {
        GridItem item = theBoard.mGridItems.dataArrayAlloc();
        item.mGridItemType = GridItemType.GRIDITEM_SQUIRREL;
        item.mGridItemState = theState;
        item.mGridX = theGridX;
        item.mGridY = theGridY;
        item.mGridItemCounter = 0;
        return item;
    }

    @Test
    void countUncaughtIgnoresCaughtZombieAndNonSquirrelItems() {
        Board board = newBoard();
        addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING, 0, 0);
        addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_PEEKING, 1, 0);
        addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_RUNNING_LEFT, 2, 0);
        addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_CAUGHT, 3, 0);
        addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_ZOMBIE, 4, 0);

        GridItem aGrave = board.mGridItems.dataArrayAlloc();
        aGrave.mGridItemType = GridItemType.GRIDITEM_GRAVESTONE;
        aGrave.mGridX = 5;
        aGrave.mGridY = 0;

        assertEquals(3, newChallenge(board).squirrelCountUncaught());
    }

    @Test
    void startCreatesSevenWaitingSquirrelsPlusOneZombieSquirrel() {
        Board board = newBoard();
        ChallengeSquirrel squirrel = newChallenge(board);

        squirrel.squirrelStart();

        int waiting = 0;
        int zombie = 0;
        int total = 0;
        for (GridItem item : board.mGridItems) {
            if (item.mGridItemType != GridItemType.GRIDITEM_SQUIRREL) {
                continue;
            }
            total++;
            if (item.mGridItemState == GridItemState.GRIDITEM_STATE_SQUIRREL_ZOMBIE) {
                zombie++;
                assertTrue(item.mGridX >= 4, "the zombie squirrel is placed at or east of column 4");
            } else if (item.mGridItemState == GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING) {
                waiting++;
                assertTrue(item.mGridItemCounter >= 100 && item.mGridItemCounter <= 500,
                        "waiting squirrel counter is RandRangeInt(100, 500)");
            }
        }

        assertEquals(Challenge.MAX_SQUIRRELS, waiting);
        assertEquals(1, zombie);
        assertEquals(Challenge.MAX_SQUIRRELS + 1, total);
        assertEquals(Challenge.MAX_SQUIRRELS, squirrel.squirrelCountUncaught());
    }

    @Test
    void peekSetsCounterAndState() {
        Board board = newBoard();
        GridItem item = addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING, 2, 2);

        newChallenge(board).squirrelPeek(item);

        assertEquals(GridItemState.GRIDITEM_STATE_SQUIRREL_PEEKING, item.mGridItemState);
        assertEquals(50, item.mGridItemCounter);
    }

    @Test
    void chewWithoutPlantOnlyResetsCounter() {
        Board board = newBoard();
        GridItem item = addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING, 2, 2);

        newChallenge(board).squirrelChew(item);

        assertEquals(GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING, item.mGridItemState);
        assertTrue(item.mGridItemCounter >= 100 && item.mGridItemCounter <= 500);
    }

    @Test
    void foundWithNoPlantNeighborsCaughtAndAdvisesWhileOthersRemain() {
        Board board = newBoard();
        ChallengeSquirrel squirrel = newChallenge(board);
        GridItem first = addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING, 2, 2);
        addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING, 8, 4);

        squirrel.squirrelFound(first);

        assertEquals(GridItemState.GRIDITEM_STATE_SQUIRREL_CAUGHT, first.mGridItemState);
        assertEquals(100, first.mGridItemCounter);
        assertEquals(1, squirrel.squirrelCountUncaught());
    }

    @Test
    void updateScoresAndRetiresCaughtSquirrel() {
        Board board = newBoard();
        ChallengeSquirrel squirrel = newChallenge(board);
        GridItem caught = addSquirrel(board, GridItemState.GRIDITEM_STATE_SQUIRREL_CAUGHT, 3, 3);
        caught.mGridItemCounter = 1;

        squirrel.squirrelUpdate();

        assertTrue(caught.mDead, "a caught squirrel with an expired counter is removed");
        assertEquals(0, squirrel.squirrelCountUncaught());
        assertEquals(Challenge.MAX_SQUIRRELS, board.mChallenge.mChallengeScore);
        assertEquals(Board.PROGRESS_METER_COUNTER, board.mProgressMeterWidth);
    }
}
