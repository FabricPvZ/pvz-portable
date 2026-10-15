/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.CursorObject / CursorPreview.
 *
 * Covers the cursor-position contract (CursorObject.cpp:49-72), the shovel/tool draw dispatch
 * entry point and the planting-preview validity rules (CursorObject.cpp:238-342), which are the
 * parts that decide whether a cell may receive the plant currently held by the cursor.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;

class CursorPreviewTest {

    private static final int GX = 3;
    private static final int GY = 2;

    /** Puts the mouse in the middle of grid cell (GX, GY) with a plant held by the cursor. */
    private static void pointAtCell(TestLawnApp app, StubBoard board, int theGridX, int theGridY, int theSeedType) {
        app.mGameScene = GameScenes.SCENE_PLAYING;
        app.mWidgetManager.mMouseIn = true;
        app.mWidgetManager.mLastMouseX = board.gridToPixelX(theGridX, theGridY) + 40;
        app.mWidgetManager.mLastMouseY = board.gridToPixelY(theGridX, theGridY) + 50;
        board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_BANK;
        board.mCursorObject.mType = theSeedType;
        board.mCursorObject.mImitaterType = SeedType.SEED_NONE;
    }

    @Test
    void cursorObjectDefaults() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);

        CursorObject cursor = new CursorObject();

        // CursorObject.cpp:31-47
        assertEquals(SeedType.SEED_NONE, cursor.mType);
        assertEquals(SeedType.SEED_NONE, cursor.mImitaterType);
        assertEquals(-1, cursor.mSeedBankIndex);
        assertEquals(CursorType.CURSOR_TYPE_NORMAL, cursor.mCursorType);
        assertEquals(CoinID.COINID_NULL, cursor.mCoinID);
        assertEquals(PlantID.PLANTID_NULL, cursor.mGlovePlantID);
        assertEquals(PlantID.PLANTID_NULL, cursor.mDuplicatorPlantID);
        assertEquals(PlantID.PLANTID_NULL, cursor.mCobCannonPlantID);
        assertEquals(ReanimationID.REANIMATIONID_NULL, cursor.mReanimCursorID);
        assertEquals(0, cursor.mHammerDownCounter);
        assertEquals(80, cursor.mWidth);
        assertEquals(80, cursor.mHeight);
    }

    @Test
    void cursorIsHiddenOutsideGameplay() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        CursorObject cursor = new CursorObject();

        app.mGameScene = GameScenes.SCENE_LOADING;
        app.mWidgetManager.mMouseIn = true;
        cursor.update();
        assertFalse(cursor.mVisible);

        // CursorObject.cpp:49-57: while not playing the cursor is only shown in a shovel tutorial
        board.mTutorialState = TutorialState.TUTORIAL_SHOVEL_PICKUP;
        assertTrue(board.mCutScene.isInShovelTutorial());
        cursor.update();
        assertTrue(cursor.mVisible);
    }

    @Test
    void cursorIsHiddenWhenTheMouseIsOutsideTheWindow() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);
        CursorObject cursor = new CursorObject();

        app.mGameScene = GameScenes.SCENE_PLAYING;
        app.mWidgetManager.mMouseIn = false;
        cursor.update();

        assertFalse(cursor.mVisible);
    }

    @Test
    void cursorFollowsTheMouseWithUpstreamOffsets() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);
        CursorObject cursor = new CursorObject();

        app.mGameScene = GameScenes.SCENE_PLAYING;
        app.mWidgetManager.mMouseIn = true;
        app.mWidgetManager.mLastMouseX = 200;
        app.mWidgetManager.mLastMouseY = 300;

        cursor.update();

        // CursorObject.cpp:69-70: mX = mouseX - 25, mY = mouseY - 35
        assertTrue(cursor.mVisible);
        assertEquals(175, cursor.mX);
        assertEquals(265, cursor.mY);
    }

    @Test
    void cursorDieRemovesItsReanimation() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);
        CursorObject cursor = new CursorObject();
        cursor.mReanimCursorID = 7;

        cursor.die();

        assertEquals(7, app.mRemovedReanimationID);
        assertEquals(ReanimationID.REANIMATIONID_NULL, cursor.mReanimCursorID);
    }

    @Test
    void previewIsInvisibleWithoutAPlantInTheCursor() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        CursorPreview preview = new CursorPreview();

        pointAtCell(app, board, GX, GY, SeedType.SEED_SUNFLOWER);
        board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_NORMAL;

        preview.update();

        // CursorObject.cpp:238-253: a preview needs IsPlantInCursor()
        assertFalse(preview.mVisible);
    }

    @Test
    void previewIsVisibleOnAPlantableCell() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        CursorPreview preview = new CursorPreview();

        pointAtCell(app, board, GX, GY, SeedType.SEED_SUNFLOWER);

        // The board is the authority on plantability (Board.cpp:2688 CanPlantAt).
        assertEquals(PlantingReason.PLANTING_OK, board.canPlantAt(GX, GY, SeedType.SEED_SUNFLOWER));

        preview.update();

        assertTrue(preview.mVisible);
        assertEquals(GX, preview.mGridX);
        assertEquals(GY, preview.mGridY);
        // CursorObject.cpp:260-263: the preview snaps to the grid cell origin
        assertEquals(board.gridToPixelX(GX, GY), preview.mX);
        assertEquals(board.gridToPixelY(GX, GY), preview.mY);
    }

    @Test
    void previewIsHiddenOnACellThatAlreadyHoldsAPlant() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        CursorPreview preview = new CursorPreview();

        Plant occupant = board.mPlants.dataArrayAlloc();
        occupant.mBoard = board;
        occupant.mSeedType = SeedType.SEED_SUNFLOWER;
        occupant.mPlantCol = GX;
        occupant.mRow = GY;

        pointAtCell(app, board, GX, GY, SeedType.SEED_SUNFLOWER);

        assertEquals(PlantingReason.PLANTING_NOT_HERE, board.canPlantAt(GX, GY, SeedType.SEED_SUNFLOWER));

        preview.update();

        assertFalse(preview.mVisible);
    }

    @Test
    void previewIsHiddenOffTheLawn() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        CursorPreview preview = new CursorPreview();

        pointAtCell(app, board, GX, GY, SeedType.SEED_SUNFLOWER);
        app.mWidgetManager.mLastMouseX = 10; // left of LAWN_XMIN

        preview.update();

        assertFalse(preview.mVisible);
    }
}
