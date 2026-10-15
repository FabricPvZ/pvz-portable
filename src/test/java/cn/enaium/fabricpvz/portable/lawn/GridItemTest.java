/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.GridItem.
 *
 * Covers the per-type lifecycle that this port actually implements (GridItem.cpp:95-644):
 * gravestone/crater/ladder are draw-only, brain + scary pot + portal + rake have update logic.
 * Zombies come from a StubBoard whose list holds StubZombie doubles.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubZombie;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;

class GridItemTest {

    private static final int GX = 3;
    private static final int GY = 2;

    private static GridItem newItem(StubBoard board, int theType, int theState) {
        GridItem item = new GridItem();
        item.mBoard = board;
        item.mGridItemType = theType;
        item.mGridItemState = theState;
        item.mGridX = GX;
        item.mGridY = GY;
        item.mPosX = board.gridToPixelX(GX, GY);
        item.mPosY = board.gridToPixelY(GX, GY);
        return item;
    }

    @Test
    void constructorMatchesUpstreamDefaults() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);

        GridItem item = new GridItem();

        // GridItem.cpp:23-63
        assertEquals(GridItemType.GRIDITEM_NONE, item.mGridItemType);
        assertEquals(GridItemState.GRIDITEM_STATE_NORMAL, item.mGridItemState);
        assertEquals(ZombieType.ZOMBIE_INVALID, item.mZombieType);
        assertEquals(SeedType.SEED_NONE, item.mSeedType);
        assertEquals(ScaryPotType.SCARYPOT_NONE, item.mScaryPotType);
        assertEquals(ReanimationID.REANIMATIONID_NULL, item.mGridItemReanimID);
        assertEquals(ParticleSystemID.PARTICLESYSTEMID_NULL, item.mGridItemParticleID);
        assertFalse(item.mDead);
        assertFalse(item.mHighlighted);
        assertEquals(0, item.mTransparentCounter);
        assertEquals(0, item.mSunCount);
        assertEquals(0, item.mMotionTrailCount);
        assertEquals(12, item.mMotionTrailFrames.length);
    }

    /**
     * Gravestone, crater, ladder, squirrel, zen tool and stinky are pure draw-state types in this
     * port: none of them advances any counter in Update (GridItem.cpp:559-589).
     */
    @Test
    void drawOnlyTypesHaveNoUpdateLogic() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);

        int[] drawOnlyTypes = {
            GridItemType.GRIDITEM_NONE,
            GridItemType.GRIDITEM_GRAVESTONE,
            GridItemType.GRIDITEM_CRATER,
            GridItemType.GRIDITEM_LADDER,
            GridItemType.GRIDITEM_SQUIRREL,
            GridItemType.GRIDITEM_ZEN_TOOL,
            GridItemType.GRIDITEM_STINKY
        };

        for (int type : drawOnlyTypes) {
            GridItem item = newItem(board, type, GridItemState.GRIDITEM_STATE_NORMAL);
            item.mGridItemCounter = 7;
            item.mTransparentCounter = 7;

            item.update();

            assertFalse(item.mDead, "type " + type + " must not die on update");
            assertEquals(7, item.mGridItemCounter, "type " + type + " must not touch its counter");
            assertEquals(7, item.mTransparentCounter, "type " + type + " must not touch transparency");
        }
    }

    @Test
    void squishedIzombieBrainCountsDownAndDies() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem item = newItem(board, GridItemType.GRIDITEM_IZOMBIE_BRAIN,
                GridItemState.GRIDITEM_STATE_BRAIN_SQUISHED);
        item.mGridItemCounter = 2;

        // GridItem.cpp:542-557
        item.update();
        assertFalse(item.mDead);
        assertEquals(1, item.mGridItemCounter);

        item.update();
        assertTrue(item.mDead);
    }

    @Test
    void izombieBrainTransparencyDecays() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem item = newItem(board, GridItemType.GRIDITEM_IZOMBIE_BRAIN,
                GridItemState.GRIDITEM_STATE_NORMAL);
        item.mTransparentCounter = 3;

        item.update();
        assertEquals(2, item.mTransparentCounter);

        item.update();
        item.update();
        assertEquals(0, item.mTransparentCounter);
        assertFalse(item.mDead, "a healthy brain is never removed by its own update");
    }

    @Test
    void scaryPotIsRevealedWhileTheShiftCheatIsHeld() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem pot = newItem(board, GridItemType.GRIDITEM_SCARY_POT,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION);

        app.mCheatKeys = true;
        app.mWidgetManager.mKeyDown[KeyCode.KEYCODE_SHIFT] = true;

        // GridItem.cpp:506-515
        for (int i = 0; i < 3; i++) {
            pot.update();
        }
        assertEquals(3, pot.mTransparentCounter);

        // clamped at 50
        for (int i = 0; i < 100; i++) {
            pot.update();
        }
        assertEquals(50, pot.mTransparentCounter);

        app.mWidgetManager.mKeyDown[KeyCode.KEYCODE_SHIFT] = false;
        pot.update();
        assertEquals(49, pot.mTransparentCounter);
    }

    @Test
    void scaryPotIsRevealedByAnAdjacentPlantern() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem pot = newItem(board, GridItemType.GRIDITEM_SCARY_POT,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION);

        Plant plantern = board.mPlants.dataArrayAlloc();
        plantern.mBoard = board;
        plantern.mSeedType = SeedType.SEED_PLANTERN;
        plantern.mPlantCol = GX + 1;
        plantern.mRow = GY - 1;

        // GridItem.cpp:518-537: a plantern within Chebyshev distance 1 reveals the pot
        pot.update();
        assertEquals(1, pot.mTransparentCounter);

        // ... and moving it away lets the pot fade again
        plantern.mPlantCol = GX + 2;
        pot.update();
        assertEquals(0, pot.mTransparentCounter);
    }

    @Test
    void deadPlanternsDoNotRevealScaryPots() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem pot = newItem(board, GridItemType.GRIDITEM_SCARY_POT,
                GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION);

        Plant plantern = board.mPlants.dataArrayAlloc();
        plantern.mBoard = board;
        plantern.mSeedType = SeedType.SEED_PLANTERN;
        plantern.mPlantCol = GX;
        plantern.mRow = GY;
        plantern.mDead = true;

        pot.update();
        assertEquals(0, pot.mTransparentCounter);
    }

    @Test
    void portalIsOnlyOpenForPortalTypesThatAreNotClosed() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);

        GridItem circle = newItem(board, GridItemType.GRIDITEM_PORTAL_CIRCLE,
                GridItemState.GRIDITEM_STATE_NORMAL);
        GridItem square = newItem(board, GridItemType.GRIDITEM_PORTAL_SQUARE,
                GridItemState.GRIDITEM_STATE_NORMAL);
        GridItem gravestone = newItem(board, GridItemType.GRIDITEM_GRAVESTONE,
                GridItemState.GRIDITEM_STATE_NORMAL);

        // GridItem.cpp:471-475
        assertTrue(circle.isOpenPortal());
        assertTrue(square.isOpenPortal());
        assertFalse(gravestone.isOpenPortal());

        circle.closePortal();
        assertEquals(GridItemState.GRIDITEM_STATE_PORTAL_CLOSED, circle.mGridItemState);
        assertFalse(circle.isOpenPortal());
    }

    @Test
    void rakeFindsOnlyLiveDamageableZombiesInItsRow() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem rake = newItem(board, GridItemType.GRIDITEM_RAKE,
                GridItemState.GRIDITEM_STATE_RAKE_WAITING);

        StubZombie otherRow = board.addStubZombie(GY + 1, (int) rake.mPosX, (int) rake.mPosY);
        assertNull(rake.rakeFindZombie(), "zombies in other rows are ignored");
        assertEquals(GY + 1, otherRow.mRow);

        int y = (int) rake.mPosY;
        StubZombie dying = board.addStubZombie(GY, (int) rake.mPosX, y);
        dying.mDeadOrDying = true;
        assertNull(rake.rakeFindZombie(), "dying zombies are ignored");

        dying.mDeadOrDying = false;
        dying.mDamageable = false;
        assertNull(rake.rakeFindZombie(), "zombies immune to ground damage are ignored");

        dying.mDamageable = true;
        assertEquals(dying, rake.rakeFindZombie());

        // a zombie far away does not overlap the rake rect Rect(mPosX, mPosY, 63, 80)
        dying.mStubRect = new Rect((int) rake.mPosX + 300, y, 44, 115);
        assertNull(rake.rakeFindZombie());
    }

    @Test
    void rakeTriggersWhenAZombieWalksIntoIt() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem rake = newItem(board, GridItemType.GRIDITEM_RAKE,
                GridItemState.GRIDITEM_STATE_RAKE_WAITING);
        rake.mGridItemReanimID = 1;
        board.addStubZombie(GY, (int) rake.mPosX, (int) rake.mPosY);

        // GridItem.cpp:612-625
        rake.updateRake();

        assertEquals(GridItemState.GRIDITEM_STATE_RAKE_TRIGGERED, rake.mGridItemState);
        assertEquals(200, rake.mGridItemCounter);
        assertEquals(20.0f, app.mReanim.mAnimRate);
        assertTrue(app.mFoleysPlayed.contains(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_SWING));
    }

    @Test
    void triggeredRakeDamagesOnItsTimedEventAndDiesAtZero() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem rake = newItem(board, GridItemType.GRIDITEM_RAKE,
                GridItemState.GRIDITEM_STATE_RAKE_TRIGGERED);
        rake.mGridItemReanimID = 1;
        rake.mGridItemCounter = 1;
        app.mReanim.mTimedEvent = true;
        StubZombie zombie = board.addStubZombie(GY, (int) rake.mPosX, (int) rake.mPosY);

        // GridItem.cpp:626-643
        rake.updateRake();

        assertEquals(1800, zombie.mLastDamage);
        assertEquals(0, zombie.mLastDamageFlags);
        assertEquals(0, rake.mGridItemCounter);
        assertTrue(rake.mDead);
        assertTrue(app.mFoleysPlayed.contains(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_BONK));
    }

    @Test
    void gridItemDieKillsTheAttachedReanimation() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        GridItem item = newItem(board, GridItemType.GRIDITEM_GRAVESTONE,
                GridItemState.GRIDITEM_STATE_NORMAL);
        item.mGridItemReanimID = 1;

        item.gridItemDie();

        assertTrue(item.mDead);
        assertEquals(1, app.mReanim.mDieCount);
        assertEquals(ReanimationID.REANIMATIONID_NULL, item.mGridItemReanimID);
    }
}
