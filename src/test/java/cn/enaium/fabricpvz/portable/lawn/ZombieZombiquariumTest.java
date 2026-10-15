/*
 * Focused headless tests for the Zombiquarium zombie AI now implemented in ZombieBehaviourB:
 *   Zombie::ZombiquariumFindClosestBrain (src/Lawn/Zombie.cpp:3079-3131)
 *   Zombie::UpdateZombiquarium            (src/Lawn/Zombie.cpp:3132-3261)
 *
 * Every asserted value is quoted from those lines.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Common;

class ZombieZombiquariumTest {

    private static TestLawnApp newApp() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Common.sRand(0x5EED);
        return app;
    }

    /** A hand-configured aquarium zombie (a loaded-looking body reanim, no asset work). */
    private static Zombie bareZombie(Board board) {
        Zombie z = new Zombie();
        z.mApp = board.mApp;
        z.mBoard = board;
        z.mZombieType = ZombieType.ZOMBIE_NORMAL;
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_DRIFT;
        z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIQUARIUM;
        z.mBodyReanimID = 1; // the app double resolves every non-null id to its recording reanim
        z.mBodyHealth = 100;
        z.mBodyMaxHealth = 300;
        z.mPosX = 200.0f;
        z.mPosY = 200.0f;
        z.mZombieAge = 1; // never a multiple of 100, so the periodic damage tick stays out of the way
        return z;
    }

    private static GridItem addBrain(Board board, float thePosX, float thePosY) {
        GridItem brain = board.mGridItems.dataArrayAlloc();
        brain.mGridItemType = GridItemType.GRIDITEM_BRAIN;
        brain.mGridItemCounter = 20; // Zombie.cpp:3088 requires >= 15
        brain.mPosX = thePosX;
        brain.mPosY = thePosY;
        return brain;
    }

    @Test
    void findClosestBrainEatsANearbyBrainAndHeals() {
        TestLawnApp app = newApp();
        Board board = LawnEntityTestSupport.newBoard(app);
        Zombie z = bareZombie(board);
        GridItem brain = addBrain(board, z.mPosX + 50.0f, z.mPosY + 40.0f); // within the 50px bite radius

        boolean found = ZombieBehaviourB.zombiquariumFindClosestBrain(z);

        assertFalse(found);
        assertTrue(brain.mDead); // Zombie.cpp:3107 aBrainClosest->GridItemDie()
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_SLURP)); // Zombie.cpp:3108
        assertEquals(300, z.mBodyHealth); // Zombie.cpp:3110-3111: +200, clamped to mBodyMaxHealth
        assertEquals(ZombiePhase.PHASE_ZOMBIQUARIUM_BITE, z.mZombiePhase); // Zombie.cpp:3114
        assertEquals(200, z.mPhaseCounter); // Zombie.cpp:3115
        assertEquals("anim_aquarium_bite", app.mReanim.mLastTrack); // Zombie.cpp:3113
    }

    @Test
    void findClosestBrainAcceleratesTowardADistantBrain() {
        TestLawnApp app = newApp();
        Board board = LawnEntityTestSupport.newBoard(app);
        Zombie z = bareZombie(board);
        GridItem brain = addBrain(board, z.mPosX + 200.0f, z.mPosY);

        boolean found = ZombieBehaviourB.zombiquariumFindClosestBrain(z);

        assertTrue(found); // Zombie.cpp:3130
        assertFalse(brain.mDead);
        assertEquals(ZombiePhase.PHASE_ZOMBIQUARIUM_ACCEL, z.mZombiePhase); // Zombie.cpp:3129
        assertTrue(z.mVelZ >= 0.0f); // Zombie.cpp:3123-3127 (atan2 folded into [0, 2pi))
    }

    @Test
    void findClosestBrainBailsWhenHealthyOrAwardDropped() {
        TestLawnApp app = newApp();
        Board board = LawnEntityTestSupport.newBoard(app);
        Zombie z = bareZombie(board);
        z.mBodyHealth = 151; // Zombie.cpp:3081: mBodyHealth > 150

        assertFalse(ZombieBehaviourB.zombiquariumFindClosestBrain(z));

        z.mBodyHealth = 100;
        board.mLevelAwardSpawned = true; // Zombie.cpp:3081: HasLevelAwardDropped()
        assertFalse(ZombieBehaviourB.zombiquariumFindClosestBrain(z));
    }

    @Test
    void updateZombiquariumSwitchesFromBiteToDriftAfterOneLoop() {
        TestLawnApp app = newApp();
        Board board = LawnEntityTestSupport.newBoard(app);
        Zombie z = bareZombie(board);
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_BITE;
        app.mReanim.mLoopCount = 1; // Zombie.cpp:3139: aBodyReanim->mLoopCount > 0

        ZombieBehaviourB.updateZombiquarium(z);

        assertEquals(ZombiePhase.PHASE_ZOMBIQUARIUM_DRIFT, z.mZombiePhase); // Zombie.cpp:3143
        assertEquals(100, z.mPhaseCounter); // Zombie.cpp:3144
        assertEquals("anim_aquarium_swim", app.mReanim.mLastTrack); // Zombie.cpp:3141
    }
}
