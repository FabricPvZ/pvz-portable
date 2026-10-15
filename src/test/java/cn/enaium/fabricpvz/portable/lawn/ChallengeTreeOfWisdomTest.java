/*
 * Focused headless tests for {@link ChallengeTreeOfWisdom} (the Tree of Wisdom mini-game state
 * machine ported from src/Lawn/Challenge.cpp:4923-5335).
 *
 * Every asserted value is quoted from the upstream source noted next to it. The fixture is the
 * shared {@link LawnEntityTestSupport} headless app/board pair, whose effect/audio services are
 * recording test doubles (no retail assets needed), so the state transitions, the grid-item spawn
 * and the hit-test rectangles can be driven deterministically.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Common;

class ChallengeTreeOfWisdomTest {

    /** App + board in {@link GameMode#GAMEMODE_TREE_OF_WISDOM} with the given challenge record. */
    private static final class Fixture {
        final LawnEntityTestSupport.TestLawnApp app;
        final Board board;
        final ChallengeTreeOfWisdom tw;

        Fixture(int treeSize) {
            app = LawnEntityTestSupport.newApp();
            app.mGameMode = GameMode.GAMEMODE_TREE_OF_WISDOM;
            board = LawnEntityTestSupport.newBoard(app);
            // reanimationGet(REANIMATIONID_NULL) returns null, so the tree/cloud slots must name a
            // live id for the paths that read them back (Challenge.cpp:5040-5060, 5236-5252).
            board.mChallenge.mReanimChallenge = 1;
            for (int i = 0; i < 6; i++) {
                board.mChallenge.mReanimClouds[i] = 1;
            }
            setTreeSize(treeSize);
            tw = new ChallengeTreeOfWisdom(board.mChallenge);
        }

        void setTreeSize(int treeSize) {
            app.mPlayerInfo.mChallengeRecords[app.getCurrentChallengeIndex()] = treeSize;
        }

        int treeSize() {
            return app.mPlayerInfo.mChallengeRecords[app.getCurrentChallengeIndex()];
        }
    }

    // ---------------------------------------------------------------------------------------
    // TreeOfWisdomGetSize / HitTest / MouseOn
    // ---------------------------------------------------------------------------------------

    @Test
    void getSizeReadsTheCurrentChallengeRecord() {
        Fixture f = new Fixture(23);
        // Challenge.cpp:4930-4933 -> mChallengeRecords[mApp->GetCurrentChallengeIndex()].
        assertEquals(23, f.tw.treeOfWisdomGetSize());
        assertEquals(23, f.treeSize());
    }

    @Test
    void hitTestChoosesTheRectangleByTreeSize() {
        Fixture f = new Fixture(0);
        HitResult hit = new HitResult();

        // Challenge.cpp:5301 -> aTreeSize <= 1: Rect(310, 275, 175, 175).
        assertEquals(1, f.tw.treeOfWisdomHitTest(320, 280, hit));
        assertEquals(GameObjectType.OBJECT_TYPE_TREE_OF_WISDOM, hit.mObjectType);
        assertNull(hit.mObject);

        assertEquals(0, f.tw.treeOfWisdomHitTest(0, 0, hit));
        assertEquals(GameObjectType.OBJECT_TYPE_NONE, hit.mObjectType);
        assertNull(hit.mObject);

        // (350, 260) is outside the size-0 rect (y < 275) but inside the 7..11 rect
        // (290, 215, 205, 225) at size 8.
        f.setTreeSize(8);
        assertEquals(1, f.tw.treeOfWisdomHitTest(350, 260, hit));
        f.setTreeSize(0);
        assertEquals(0, f.tw.treeOfWisdomHitTest(350, 260, hit));

        // Challenge.cpp:5303 -> aTreeSize < 7: Rect(290, 255, 205, 195).
        f.setTreeSize(6);
        assertEquals(1, f.tw.treeOfWisdomHitTest(291, 256, hit));

        // Challenge.cpp:5305 -> aTreeSize >= 12: Rect(280, 155, 225, 305).
        f.setTreeSize(50);
        assertEquals(1, f.tw.treeOfWisdomHitTest(281, 156, hit));
        assertEquals(0, f.tw.treeOfWisdomHitTest(505, 460, hit));
    }

    @Test
    void mouseOnRequiresTreeFoodCursorOverTheTree() {
        Fixture f = new Fixture(5); // size 5 -> Rect(290, 255, 205, 195)
        f.board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_TREE_FOOD;
        assertEquals(1, f.tw.treeOfWisdomMouseOn(300, 260));

        f.board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_NORMAL;
        assertEquals(0, f.tw.treeOfWisdomMouseOn(300, 260));

        f.board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_TREE_FOOD;
        assertEquals(0, f.tw.treeOfWisdomMouseOn(0, 0));
    }

    // ---------------------------------------------------------------------------------------
    // Babble / give-wisdom talk-index selection
    // ---------------------------------------------------------------------------------------

    @Test
    void babbleSmallTreeUsesTheCannedLine() {
        Fixture f = new Fixture(1);
        Common.sRand(0x5EED);
        f.tw.treeOfWisdomBabble();
        // Challenge.cpp:5113-5119 -> state BABBLING, counter 400, size <= 1 -> talk index 600.
        assertEquals(ChallengeState.STATECHALLENGE_TREE_BABBLING, f.board.mChallenge.mChallengeState);
        assertEquals(400, f.board.mChallenge.mChallengeStateCounter);
        assertEquals(600, f.board.mChallenge.mTreeOfWisdomTalkIndex);
    }

    @Test
    void giveWisdomMapsSpecialSizesToSpecialLines() {
        Fixture f = new Fixture(100);
        f.tw.treeOfWisdomGiveWisdom();
        assertEquals(ChallengeState.STATECHALLENGE_TREE_GIVE_WISDOM, f.board.mChallenge.mChallengeState);
        assertEquals(1000, f.board.mChallenge.mChallengeStateCounter);
        assertEquals(800, f.board.mChallenge.mTreeOfWisdomTalkIndex);

        f.setTreeSize(500);
        f.tw.treeOfWisdomGiveWisdom();
        assertEquals(900, f.board.mChallenge.mTreeOfWisdomTalkIndex);

        f.setTreeSize(1000);
        f.tw.treeOfWisdomGiveWisdom();
        assertEquals(1000, f.board.mChallenge.mTreeOfWisdomTalkIndex);

        f.setTreeSize(1200);
        f.tw.treeOfWisdomGiveWisdom();
        assertEquals(1100, f.board.mChallenge.mTreeOfWisdomTalkIndex);

        // Challenge.cpp:5159-5161 -> otherwise clamp(aTreeSize - 1, 1, 49).
        f.setTreeSize(30);
        f.tw.treeOfWisdomGiveWisdom();
        assertEquals(29, f.board.mChallenge.mTreeOfWisdomTalkIndex);

        f.setTreeSize(0);
        f.tw.treeOfWisdomGiveWisdom();
        assertEquals(1, f.board.mChallenge.mTreeOfWisdomTalkIndex);
    }

    @Test
    void sayRepeatStaysInsideTheTreeSizeRange() {
        Fixture f = new Fixture(20);
        Common.sRand(0x5EED);
        f.tw.treeOfWisdomSayRepeat();
        // Challenge.cpp:5182-5184 -> RandRangeInt(2, clamp(aTreeSize, 3, 49)); counter 600.
        assertTrue(f.board.mChallenge.mTreeOfWisdomTalkIndex >= 2
                && f.board.mChallenge.mTreeOfWisdomTalkIndex <= 20);
        assertEquals(600, f.board.mChallenge.mChallengeStateCounter);
    }

    // ---------------------------------------------------------------------------------------
    // Init / grow / fertilize / tool / leave
    // ---------------------------------------------------------------------------------------

    @Test
    void initBuildsTreeAndSixCloudsAndWaitsToBabble() {
        Fixture f = new Fixture(5);
        int before = f.app.mAddedReanimations;
        Common.sRand(0x5EED);
        f.tw.treeOfWisdomInit();

        // Challenge.cpp:5036 + 5046 -> one tree reanimation plus six cloud reanimations.
        assertEquals(7, f.app.mAddedReanimations - before);
        assertEquals(1, f.board.mChallenge.mReanimChallenge);
        for (int i = 0; i < 6; i++) {
            assertEquals(1, f.board.mChallenge.mReanimClouds[i]);
            int counter = f.board.mChallenge.mCloudsCounter[i];
            assertTrue(counter >= 0 && counter <= 2000, "cloud counter " + counter);
        }
        // Challenge.cpp:5066-5067 -> WAITING_TO_BABBLE, RandRangeInt(700, 1500).
        assertEquals(ChallengeState.STATECHALLENGE_TREE_WAITING_TO_BABBLE, f.board.mChallenge.mChallengeState);
        int counter = f.board.mChallenge.mChallengeStateCounter;
        assertTrue(counter >= 700 && counter <= 1500, "state counter " + counter);
    }

    @Test
    void growIncrementsTheRecordAndTransitions() {
        Fixture f = new Fixture(0);
        int before = f.app.mAddedReanimations;
        f.tw.treeOfWisdomGrow();
        // Challenge.cpp:5072 -> the record is incremented first, so a size-0 tree becomes 1 and
        // 5078-5084 leaves the state NORMAL (aTreeSize == 1 is not > 1).
        assertEquals(1, f.treeSize());
        assertEquals(ChallengeState.STATECHALLENGE_NORMAL, f.board.mChallenge.mChallengeState);
        assertEquals("anim_grow1", f.app.mReanim.mLastTrack);
        assertTrue(f.app.mFoleysPlayed.contains(FoleyType.FOLEY_PLANTGROW));
        // Grow reuses the existing tree reanimation (no new allocation).
        assertEquals(0, f.app.mAddedReanimations - before);

        Fixture g = new Fixture(5);
        g.tw.treeOfWisdomGrow();
        assertEquals(6, g.treeSize());
        assertEquals("anim_grow6", g.app.mReanim.mLastTrack);
        assertEquals(ChallengeState.STATECHALLENGE_TREE_JUST_GREW, g.board.mChallenge.mChallengeState);
        assertEquals(120, g.board.mChallenge.mChallengeStateCounter);
    }

    @Test
    void fertilizeSpawnsTreeFoodAndConsumesAPurchase() {
        Fixture f = new Fixture(3);
        f.app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;

        f.tw.treeOfWisdomFertilize();

        GridItem item = null;
        for (GridItem aGridItem : f.board.mGridItems) {
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                item = aGridItem;
            }
        }
        assertNotNull(item);
        assertEquals(1, countZenTools(f.board));
        assertEquals(GridItemType.GRIDITEM_ZEN_TOOL, item.mGridItemType);
        assertEquals(GridItemState.GRIDITEM_STATE_ZEN_TOOL_FERTILIZER, item.mGridItemState);
        assertEquals(340.0f, item.mPosX);
        assertEquals(300.0f, item.mPosY);
        assertEquals(0, item.mGridX);
        assertEquals(0, item.mGridY);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0), item.mRenderOrder);
        assertEquals(PlayerInfo.PURCHASE_COUNT_OFFSET + 4,
                f.app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD]);
        assertEquals(ChallengeState.STATECHALLENGE_NORMAL, f.board.mChallenge.mChallengeState);
        assertTrue(f.app.mFoleysPlayed.contains(FoleyType.FOLEY_FERTILIZER));
        // Challenge.cpp:5107 -> the cursor is cleared after fertilising.
        assertEquals(CursorType.CURSOR_TYPE_NORMAL, f.board.mCursorObject.mCursorType);
    }

    @Test
    void toolUpdateGrowsWhenTheFertilizerAnimationLoops() {
        Fixture f = new Fixture(3);
        GridItem item = f.board.mGridItems.dataArrayAlloc();
        item.mGridItemType = GridItemType.GRIDITEM_ZEN_TOOL;
        item.mGridItemReanimID = 7;

        f.app.mReanim.mLoopCount = 1;
        f.tw.treeOfWisdomToolUpdate(item);
        // Challenge.cpp:5190-5194 -> LoopCount > 0 grows the tree and kills the tool.
        assertEquals(4, f.treeSize());
        assertTrue(item.mDead);

        GridItem item2 = f.board.mGridItems.dataArrayAlloc();
        item2.mGridItemType = GridItemType.GRIDITEM_ZEN_TOOL;
        item2.mGridItemReanimID = 7;
        f.app.mReanim.mLoopCount = 0;
        f.tw.treeOfWisdomToolUpdate(item2);
        assertEquals(4, f.treeSize());
        assertFalse(item2.mDead);
    }

    @Test
    void leaveGrowsForEveryLiveZenTool() {
        Fixture f = new Fixture(3);
        GridItem item = f.board.mGridItems.dataArrayAlloc();
        item.mGridItemType = GridItemType.GRIDITEM_ZEN_TOOL;

        GridItem other = f.board.mGridItems.dataArrayAlloc();
        other.mGridItemType = GridItemType.GRIDITEM_RAKE;

        f.tw.treeOfWisdomLeave();

        // Challenge.cpp:5260-5265 -> only GRIDITEM_ZEN_TOOL items grow + die.
        assertEquals(4, f.treeSize());
        assertTrue(item.mDead);
        assertFalse(other.mDead);
    }

    // ---------------------------------------------------------------------------------------
    // Update state machine + can-feed guard
    // ---------------------------------------------------------------------------------------

    @Test
    void updateCountsDownOnlyWhilePlaying() {
        Fixture f = new Fixture(2);
        f.board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_TREE_WAITING_TO_BABBLE;
        f.board.mChallenge.mChallengeStateCounter = 5;

        f.app.mGameScene = GameScenes.SCENE_PLAYING;
        f.tw.treeOfWisdomUpdate();
        assertEquals(4, f.board.mChallenge.mChallengeStateCounter);

        f.app.mGameScene = GameScenes.SCENE_LEVEL_INTRO;
        f.tw.treeOfWisdomUpdate();
        assertEquals(4, f.board.mChallenge.mChallengeStateCounter);
    }

    @Test
    void updatePromotesJustGrewAtCounterZero() {
        Fixture f = new Fixture(100);
        f.board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_TREE_JUST_GREW;
        f.board.mChallenge.mChallengeStateCounter = 0;

        f.tw.treeOfWisdomUpdate();

        // Challenge.cpp:5212-5214 -> JUST_GREW + counter 0 gives wisdom.
        assertEquals(ChallengeState.STATECHALLENGE_TREE_GIVE_WISDOM, f.board.mChallenge.mChallengeState);
        assertEquals(1000, f.board.mChallenge.mChallengeStateCounter);
        assertEquals(800, f.board.mChallenge.mTreeOfWisdomTalkIndex);
    }

    @Test
    void updateReturnsBabbelingToWaitingAtCounterZero() {
        Fixture f = new Fixture(20);
        f.board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_TREE_BABBLING;
        f.board.mChallenge.mChallengeStateCounter = 0;
        f.board.mChallenge.mTreeOfWisdomTalkIndex = 3; // not the repeat line (500)

        Common.sRand(0x5EED);
        f.tw.treeOfWisdomUpdate();

        // Challenge.cpp:5226-5230 -> default path waits again for RandRangeInt(700, 1000).
        assertEquals(ChallengeState.STATECHALLENGE_TREE_WAITING_TO_BABBLE, f.board.mChallenge.mChallengeState);
        int counter = f.board.mChallenge.mChallengeStateCounter;
        assertTrue(counter >= 700 && counter <= 1000, "state counter " + counter);
    }

    @Test
    void updateRepeatsWhenTheLastLineWasTheRepeatRequest() {
        Fixture f = new Fixture(20);
        f.board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_TREE_BABBLING;
        f.board.mChallenge.mChallengeStateCounter = 0;
        f.board.mChallenge.mTreeOfWisdomTalkIndex = 500;

        Common.sRand(0x5EED);
        f.tw.treeOfWisdomUpdate();

        // Challenge.cpp:5222-5224 -> talk index 500 re-runs SayRepeat (counter 600).
        assertEquals(600, f.board.mChallenge.mChallengeStateCounter);
    }

    @Test
    void canFeedIsBlockedByJustGrewOrALiveZenTool() {
        Fixture f = new Fixture(3);
        f.board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        assertEquals(1, f.tw.treeOfWisdomCanFeed());

        f.board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_TREE_JUST_GREW;
        assertEquals(0, f.tw.treeOfWisdomCanFeed());

        f.board.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        GridItem tool = f.board.mGridItems.dataArrayAlloc();
        tool.mGridItemType = GridItemType.GRIDITEM_ZEN_TOOL;
        assertEquals(0, f.tw.treeOfWisdomCanFeed());

        // A dead tool does not block feeding, nor does an unrelated grid item.
        tool.mDead = true;
        assertEquals(1, f.tw.treeOfWisdomCanFeed());
        GridItem rake = f.board.mGridItems.dataArrayAlloc();
        rake.mGridItemType = GridItemType.GRIDITEM_RAKE;
        assertEquals(1, f.tw.treeOfWisdomCanFeed());
    }

    private static int countZenTools(Board board) {
        int count = 0;
        for (GridItem aGridItem : board.mGridItems) {
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                count++;
            }
        }
        return count;
    }
}
