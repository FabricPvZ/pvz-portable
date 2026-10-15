/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.LawnMower.
 *
 * Covers the initialisation contract (LawnMower.cpp:30-85), the READY -> TRIGGERED -> sweep state
 * machine (LawnMower.cpp:178-273) and the "kills everything in the row" behaviour that the mower
 * exists for. Zombies come from a StubBoard whose list holds StubZombie doubles, so no reanim or
 * animation data is required.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubZombie;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Rect;

class LawnMowerTest {

    private static final int ROW = 2;

    /** A mower placed at the "rolled in" position: mPosX = -21 (LawnMower.cpp:171-176). */
    private static LawnMower readyMower(StubBoard board) {
        LawnMower mower = new LawnMower();
        mower.lawnMowerInitialize(ROW);
        mower.mPosX = -21.0f;
        mower.mPosY = board.getPosYBasedOnRow(mower.mPosX + 40.0f, ROW) + 23.0f;
        return mower;
    }

    @Test
    void initialiseMatchesUpstreamContract() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = LawnEntityTestSupport.newBoard(app);

        LawnMower mower = new LawnMower();
        mower.lawnMowerInitialize(ROW);

        // LawnMower.cpp:30-85
        assertEquals(ROW, mower.mRow);
        assertEquals(-160.0f, mower.mPosX);
        assertEquals(-1, mower.mLastPortalX);
        assertEquals(LawnMowerState.MOWER_READY, mower.mMowerState);
        assertEquals(LawnMowerType.LAWNMOWER_LAWN, mower.mMowerType);
        assertTrue(mower.mVisible);
        assertFalse(mower.mDead);
        assertEquals(0, mower.mChompCounter);
        assertEquals(0, mower.mRollingInCounter);
        assertEquals(0, mower.mSquishedCounter);
        assertEquals(MowerHeight.MOWER_HEIGHT_LAND, mower.mMowerHeight);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, ROW, 0), mower.mRenderOrder);
        assertEquals(board.getPosYBasedOnRow(mower.mPosX + 40.0f, ROW) + 23.0f, mower.mPosY);
        assertEquals(1, app.mAddedReanimations);
        assertEquals(0.0f, app.mReanim.mAnimRate);
        assertEquals("anim_normal", app.mReanim.mLastTrack);
    }

    @Test
    void attackRectIsFiftyByEighty() {
        LawnMower mower = new LawnMower();
        mower.mPosX = -21.0f;
        mower.mPosY = 265.0f;

        // LawnMower.cpp:417-420
        assertEquals(new Rect(-21, 265, 50, 80), mower.getLawnMowerAttackRect());
    }

    @Test
    void rollingInCounterCurvesTowardsTheReadyPosition() {
        // LawnMower::Update reads mApp->mGameScene and mBoard (LawnMower.cpp:196-205), so the mower
        // needs the app/board LawnMowerInitialize always gives it (LawnMower.cpp:30-32).
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        mower.mMowerState = LawnMowerState.MOWER_ROLLING_IN;
        mower.mRollingInCounter = 0;
        mower.mPosX = -160.0f;

        // LawnMower.cpp:186-195
        mower.update();
        assertTrue(mower.mPosX > -160.0f, "the balancer mower must move right while rolling in");
        assertEquals(LawnMowerState.MOWER_ROLLING_IN, mower.mMowerState);

        for (int i = 0; i < 100; i++) {
            mower.update();
        }
        assertEquals(100, mower.mRollingInCounter);
        assertEquals(LawnMowerState.MOWER_READY, mower.mMowerState);
        assertEquals(-21.0f, mower.mPosX);
    }

    @Test
    void readyMowerMowsAnOverlappingZombieAndStartsSweeping() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mCurrentWave = 3;
        LawnMower mower = readyMower(board);
        StubZombie zombie = board.addStubZombie(ROW, -10, (int) mower.mPosY);

        mower.update();

        assertTrue(zombie.mMowedDown);
        assertEquals(LawnMowerState.MOWER_TRIGGERED, mower.mMowerState);
        // MowZombie sets 25 (LawnMower.cpp:148) and the same Update() tick then decrements the
        // counter for the sweep speed (LawnMower.cpp:248), so a ready mower that just mowed is 24.
        assertEquals(24, mower.mChompCounter);
        assertEquals(1, board.mTriggeredLawnMowers);
        assertEquals(3, board.mWaveRowGotLawnMowered[ROW]);
        assertEquals(70.0f, app.mReanim.mAnimRate);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_LAWNMOWER));
        assertTrue(mower.mPosX > -21.0f, "a triggered mower sweeps right");
        assertTrue(app.mReanim.mUpdateCount > 0);
    }

    @Test
    void triggerSweepKillsEveryZombieInTheRow() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        int y = (int) mower.mPosY;

        StubZombie first = board.addStubZombie(ROW, 0, y);
        StubZombie second = board.addStubZombie(ROW, 60, y);
        StubZombie third = board.addStubZombie(ROW, 120, y);
        StubZombie otherRow = board.addStubZombie(ROW + 1, 60, y);

        for (int i = 0; i < 400 && !mower.mDead; i++) {
            mower.update();
        }

        assertTrue(first.mMowedDown);
        assertTrue(second.mMowedDown);
        assertTrue(third.mMowedDown);
        assertFalse(otherRow.mMowedDown, "the mower only clears its own row");
        assertTrue(mower.mDead, "the sweep ends past WIDE_BOARD_WIDTH (LawnMower.cpp:250)");
    }

    @Test
    void mowerOnlyReactsToZombiesInItsRow() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        StubZombie otherRow = board.addStubZombie(ROW + 1, -10, (int) mower.mPosY);

        mower.update();

        assertFalse(otherRow.mMowedDown);
        assertEquals(LawnMowerState.MOWER_READY, mower.mMowerState);
        assertEquals(0, board.mTriggeredLawnMowers);
    }

    @Test
    void bungeeZombieWithoutAHeadCannotTriggerAReadyMower() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        StubZombie bungee = board.addStubZombie(ROW, -10, (int) mower.mPosY);
        bungee.mZombieType = ZombieType.ZOMBIE_BUNGEE;
        bungee.mHasHead = false;

        // LawnMower.cpp:207-211: a ready mower needs a headed, non-bungee zombie to trigger
        mower.update();

        assertFalse(bungee.mMowedDown);
        assertEquals(LawnMowerState.MOWER_READY, mower.mMowerState);
    }

    @Test
    void undamageableZombiesAreIgnored() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        StubZombie underground = board.addStubZombie(ROW, -10, (int) mower.mPosY);
        underground.mDamageable = false;

        mower.update();

        assertFalse(underground.mMowedDown);
        assertEquals(LawnMowerState.MOWER_READY, mower.mMowerState);
    }

    @Test
    void balloonZombiesNeedMoreThanTwentyPixelsOfOverlap() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);

        // Overlap with Rect(-21, y, 50, 80) is only 4px here: below the 20px balloon threshold.
        StubZombie balloon = board.addStubZombie(ROW, 25, (int) mower.mPosY);
        balloon.mZombieType = ZombieType.ZOMBIE_BALLOON;

        mower.update();
        assertFalse(balloon.mMowedDown);

        // Move it into the mower: the overlap now exceeds 20px.
        balloon.mX = 0;
        balloon.mStubRect = new Rect(0, (int) mower.mPosY, 44, 115);
        mower.update();
        assertTrue(balloon.mMowedDown);
    }

    @Test
    void squishedMowerDiesWhenTheCounterRunsOut() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        mower.mMowerState = LawnMowerState.MOWER_SQUISHED;
        mower.mSquishedCounter = 2;

        mower.update();
        assertFalse(mower.mDead);
        mower.update();

        // LawnMower.cpp:180-187
        assertTrue(mower.mDead);
        assertEquals(mower.mReanimID, app.mRemovedReanimationID);
    }

    @Test
    void triggeredMowerSweepsPastTheRightEdgeAndDies() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        mower.mMowerState = LawnMowerState.MOWER_TRIGGERED;
        mower.mPosX = GameConstants.WIDE_BOARD_WIDTH - 1.0f;

        // LawnMower.cpp:250-253
        mower.update();

        assertTrue(mower.mPosX > GameConstants.WIDE_BOARD_WIDTH);
        assertTrue(mower.mDead);
    }

    @Test
    void startMowerRecordsTheWaveAndPlaysTheLawnMowerFoley() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mCurrentWave = 4;
        board.mTriggeredLawnMowers = 0;
        LawnMower mower = readyMower(board);

        mower.startMower();

        assertEquals(LawnMowerState.MOWER_TRIGGERED, mower.mMowerState);
        assertEquals(4, board.mWaveRowGotLawnMowered[ROW]);
        assertEquals(1, board.mTriggeredLawnMowers);
        assertEquals(70.0f, app.mReanim.mAnimRate);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_LAWNMOWER));

        // LawnMower.cpp:382-386: re-starting a triggered mower is a no-op
        mower.startMower();
        assertEquals(1, board.mTriggeredLawnMowers);
    }

    @Test
    void poolMowerLootsInsteadOfMowingDown() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);
        mower.mMowerType = LawnMowerType.LAWNMOWER_POOL;

        StubZombie zombie = board.addStubZombie(ROW, 0, 0);
        mower.mowZombie(zombie);

        // LawnMower.cpp:143-176
        assertTrue(zombie.mDiedWithLoot);
        assertFalse(zombie.mMowedDown);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_SHOOP));
        assertEquals("anim_landsuck", app.mReanim.mLastTrack);
    }

    @Test
    void mowZombieChompCountersMatchUpstream() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);

        mower.mowZombie(board.addStubZombie(ROW, 0, 0));
        assertEquals(LawnMowerState.MOWER_TRIGGERED, mower.mMowerState);
        assertEquals(25, mower.mChompCounter);

        mower.mowZombie(board.addStubZombie(ROW, 0, 0));
        assertEquals(50, mower.mChompCounter);
    }

    @Test
    void dieSpawnsABonusMowerWhileTheyRemain() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mBonusLawnMowersRemaining = 1;
        LawnMower mower = readyMower(board);

        // LawnMower.cpp:369-380
        mower.die();

        assertTrue(mower.mDead);
        assertEquals(0, board.mBonusLawnMowersRemaining);
        assertEquals(1, board.mLawnMowers.mSize);
        // DataArrayAlloc hands out a (key << 16) | index handle, never the raw index (DataArray.h:158-178).
        int aBonusMowerID = board.mLawnMowers.dataArrayGetIDAt(0);
        assertEquals(LawnMowerState.MOWER_ROLLING_IN, board.mLawnMowers.dataArrayGet(aBonusMowerID).mMowerState);
    }

    @Test
    void squishMowerMatchesUpstreamCounter() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        LawnMower mower = readyMower(board);

        mower.squishMower();

        // LawnMower.cpp:406-415
        assertEquals(LawnMowerState.MOWER_SQUISHED, mower.mMowerState);
        assertEquals(500, mower.mSquishedCounter);
        assertEquals(-11.0f, app.mReanim.mSetX);
        assertEquals(65.0f, app.mReanim.mSetY);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_SQUISH));
    }
}
