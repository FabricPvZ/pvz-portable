/*
 * Headless tests for {@link ChallengePortalCombat} (the Portal Combat mini-game state machine of
 * src/Lawn/Challenge.cpp:2870-3094, 3204-3233).
 *
 * Uses the shared entity fixture ({@link LawnEntityTestSupport}) so a real {@link Board} plus the
 * recording {@link LawnEntityTestSupport.TestLawnApp} stand in for the app: the portal reanimations
 * and the foley/particle calls become recorded test doubles, while the grid maths, the row/column
 * rules and the teleport arithmetic run against the ported production code. No GL, window or retail
 * assets are required.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubZombie;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;

class ChallengePortalCombatTest {

    // Challenge::PortalStart (Challenge.cpp:2870-2906): square (2,0) pairs with square (9,1),
    // circle (9,3) pairs with circle (2,4).
    private static final int SQUARE_A_X = 2;
    private static final int SQUARE_A_Y = 0;
    private static final int SQUARE_B_X = 9;
    private static final int SQUARE_B_Y = 1;
    private static final int CIRCLE_A_X = 9;
    private static final int CIRCLE_A_Y = 3;
    private static final int CIRCLE_B_X = 2;
    private static final int CIRCLE_B_Y = 4;

    /** App + board + {@link Challenge} + the helper under test, optionally with its portals spawned. */
    private static final class Fixture {
        final TestLawnApp app;
        final StubBoard board;
        final Challenge challenge;
        final ChallengePortalCombat portals;
        final int reanimationsBefore;
        /** Grid items the board already owned before {@code PortalStart} ran. */
        final List<GridItem> baseline = new ArrayList<>();
        final List<GridItem> spawned = new ArrayList<>();

        Fixture() {
            app = LawnEntityTestSupport.newApp();
            board = LawnEntityTestSupport.newStubBoard(app);
            challenge = new Challenge(board);
            portals = new ChallengePortalCombat(challenge);
            reanimationsBefore = app.mAddedReanimations;
            for (GridItem aGridItem : board.mGridItems) {
                baseline.add(aGridItem);
            }
        }

        /** Runs {@code Challenge::PortalStart} and records the four grid items it allocated. */
        Fixture start() {
            portals.portalStart();
            for (GridItem aGridItem : board.mGridItems) {
                if (!baseline.contains(aGridItem)) {
                    spawned.add(aGridItem);
                }
            }
            return this;
        }

        /** Live grid items the mini-game allocated on top of the board's own. */
        int portalGridItemCount() {
            int aCount = 0;
            for (GridItem aGridItem : board.mGridItems) {
                if (!baseline.contains(aGridItem)) {
                    aCount++;
                }
            }
            return aCount;
        }
    }

    private static void assertPortal(GridItem thePortal, int theType, int theGridX, int theGridY) {
        assertEquals(theType, thePortal.mGridItemType, "portal type");
        assertEquals(theGridX, thePortal.mGridX, "portal column");
        assertEquals(theGridY, thePortal.mGridY, "portal row");
    }

    // -----------------------------------------------------------------------------------------
    // PortalStart
    // -----------------------------------------------------------------------------------------

    @Test
    void portalStartSpawnsTheFourUpstreamPortals() {
        Fixture f = new Fixture().start();

        assertEquals(4, f.spawned.size(), "PortalStart allocates exactly four grid items");
        assertPortal(f.spawned.get(0), GridItemType.GRIDITEM_PORTAL_SQUARE, SQUARE_A_X, SQUARE_A_Y);
        assertPortal(f.spawned.get(1), GridItemType.GRIDITEM_PORTAL_SQUARE, SQUARE_B_X, SQUARE_B_Y);
        assertPortal(f.spawned.get(2), GridItemType.GRIDITEM_PORTAL_CIRCLE, CIRCLE_A_X, CIRCLE_A_Y);
        assertPortal(f.spawned.get(3), GridItemType.GRIDITEM_PORTAL_CIRCLE, CIRCLE_B_X, CIRCLE_B_Y);

        for (GridItem aPortal : f.spawned) {
            assertTrue(aPortal.isOpenPortal(), "every spawned portal starts open");
            assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, aPortal.mGridY, 0),
                    aPortal.mRenderOrder, "portals render on the particle layer");
        }
    }

    @Test
    void portalStartResetsThePortalCountdownAndTheFirstWave() {
        Fixture f = new Fixture().start();

        assertEquals(9000, f.challenge.mChallengeStateCounter);
        assertEquals(200, f.challenge.mConveyorBeltCounter);
        assertEquals(200, f.board.mZombieCountDown);
        assertEquals(200, f.board.mZombieCountDownStart);

        assertEquals(4, f.app.mAddedReanimations - f.reanimationsBefore,
                "GridItem::OpenPortal gives each portal its own reanimation");
        assertEquals(4, f.app.mFoleysPlayed.size(), "GridItem::OpenPortal plays the portal foley");
        assertEquals(FoleyType.FOLEY_PORTAL, f.app.mFoleysPlayed.get(0));
    }

    // -----------------------------------------------------------------------------------------
    // GetPortalAt / GetOtherPortal
    // -----------------------------------------------------------------------------------------

    @Test
    void getPortalAtResolvesOnlyLiveOpenPortalsInTheCell() {
        Fixture f = new Fixture().start();

        assertSame(f.spawned.get(0), f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y));
        assertSame(f.spawned.get(2), f.portals.getPortalAt(CIRCLE_A_X, CIRCLE_A_Y));
        assertNull(f.portals.getPortalAt(4, 2), "an empty cell has no portal");
        assertNull(f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y + 1), "the row must match");

        f.spawned.get(0).closePortal();
        assertNull(f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y), "a closed portal is not reported");
        assertSame(f.spawned.get(1), f.challenge.getOtherPortal(f.spawned.get(0)),
                "GetOtherPortal pairs by type and liveness, so the closed square still has its partner");
    }

    @Test
    void getOtherPortalReturnsTheOtherPortalOfTheSameType() {
        Fixture f = new Fixture().start();

        GridItem aSquare = f.spawned.get(0);
        GridItem bSquare = f.spawned.get(1);
        GridItem aCircle = f.spawned.get(2);
        GridItem bCircle = f.spawned.get(3);

        assertSame(bSquare, f.challenge.getOtherPortal(aSquare));
        assertSame(aSquare, f.challenge.getOtherPortal(bSquare));
        assertSame(bCircle, f.challenge.getOtherPortal(aCircle));
        assertSame(aCircle, f.challenge.getOtherPortal(bCircle));
    }

    // -----------------------------------------------------------------------------------------
    // UpdatePortal - zombies
    // -----------------------------------------------------------------------------------------

    @Test
    void updatePortalTeleportsAZombieThroughThePairedPortal() {
        Fixture f = new Fixture().start();
        GridItem aPortal = f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y);

        // The portal centre is 2*80+25 = 185; the stub rect is (x, y, 44, 115) so x=163 centres it.
        StubZombie aZombie = f.board.addStubZombie(SQUARE_A_Y, 163, 10);

        f.portals.updatePortal(aPortal);

        // Challenge.cpp:2926-2932: mX = otherGridX*80 - (185 - 163) = 720 - 22.
        assertEquals(698, aZombie.mX);
        assertEquals(698.0f, aZombie.mPosX);
        assertEquals(SQUARE_B_Y, aZombie.mRow);
        assertEquals(SQUARE_B_X, aZombie.mLastPortalX);
        assertEquals((int) aZombie.getPosYBasedOnRow(SQUARE_B_Y), aZombie.mY);
        assertEquals(aZombie.mY, aZombie.mPosY);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ZOMBIE, SQUARE_B_Y, 4), aZombie.mRenderOrder,
                "SetRow re-renders the zombie in its new row");
    }

    @Test
    void updatePortalOffsetsBackwardsWalkingZombies() {
        Fixture f = new Fixture().start();
        GridItem aPortal = f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y);

        StubZombie aZombie = f.board.addStubZombie(SQUARE_A_Y, 163, 10);
        aZombie.mMindControlled = true; // Zombie::IsWalkingBackwards (Zombie.cpp:5074-5076)

        f.portals.updatePortal(aPortal);

        // Challenge.cpp:2925-2926: aDiffX = 22 - 60, so mX = 720 - (-38).
        assertEquals(758, aZombie.mX);
        assertEquals(758.0f, aZombie.mPosX);
    }

    @Test
    void updatePortalSkipsZombiesAlreadyTeleportedOrOutOfRange() {
        Fixture f = new Fixture().start();
        GridItem aPortal = f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y);

        StubZombie aTeleported = f.board.addStubZombie(SQUARE_A_Y, 163, 10);
        aTeleported.mLastPortalX = SQUARE_A_X;
        StubZombie anOutOfRange = f.board.addStubZombie(SQUARE_A_Y, 60, 10); // centre 82: 103 from 185
        StubZombie aWrongRow = f.board.addStubZombie(SQUARE_A_Y + 1, 163, 10);

        f.portals.updatePortal(aPortal);

        assertEquals(163, aTeleported.mX, "mLastPortalX == the portal column means it was just teleported");
        assertEquals(SQUARE_A_X, aTeleported.mLastPortalX);
        assertEquals(60, anOutOfRange.mX, "a zombie further than 45px from the portal centre is untouched");
        assertEquals(163, aWrongRow.mX, "a zombie in another row is untouched");
        assertEquals(SQUARE_A_Y + 1, aWrongRow.mRow);
    }

    @Test
    void updatePortalDoesNothingWithoutAPairedPortal() {
        Fixture f = new Fixture().start();
        GridItem aPortal = f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y);
        f.spawned.get(1).mDead = true; // its square partner disappears

        StubZombie aZombie = f.board.addStubZombie(SQUARE_A_Y, 163, 10);

        f.portals.updatePortal(aPortal);

        assertEquals(163, aZombie.mX);
    }

    // -----------------------------------------------------------------------------------------
    // UpdatePortal - projectiles
    // -----------------------------------------------------------------------------------------

    @Test
    void updatePortalTeleportsStraightProjectilesThroughThePairedPortal() {
        Fixture f = new Fixture().start();
        GridItem aCircle = f.portals.getPortalAt(CIRCLE_A_X, CIRCLE_A_Y);

        Projectile aProjectile = f.board.mProjectiles.dataArrayAlloc();
        aProjectile.mApp = f.app;
        aProjectile.mBoard = f.board;
        aProjectile.mProjectileType = ProjectileType.PROJECTILE_STAR; // generic 20x20 hit rect
        aProjectile.mMotionType = ProjectileMotion.MOTION_STRAIGHT;
        aProjectile.mWidth = 20;
        aProjectile.mHeight = 20;
        aProjectile.mRow = CIRCLE_A_Y;
        aProjectile.mX = 765; // centre 775 == 9*80+55
        aProjectile.mY = 300;
        aProjectile.mPosX = 765.0f;
        aProjectile.mPosY = 300.0f;
        aProjectile.mShadowY = 400.0f;
        aProjectile.mDead = false;
        aProjectile.mLastPortalX = -1;

        f.portals.updatePortal(aCircle);

        // Challenge.cpp:2949-2961: mX += 2*80 - 775 + 60; row and Y shift by (4-3)*100.
        assertEquals(210, aProjectile.mX);
        assertEquals(210.0f, aProjectile.mPosX);
        assertEquals(CIRCLE_B_Y, aProjectile.mRow);
        assertEquals(400, aProjectile.mY);
        assertEquals(400.0f, aProjectile.mPosY);
        assertEquals(500.0f, aProjectile.mShadowY);
        assertEquals(CIRCLE_B_X, aProjectile.mLastPortalX);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PROJECTILE, CIRCLE_B_Y, 0),
                aProjectile.mRenderOrder);
    }

    @Test
    void updatePortalLeavesNonStraightProjectilesAlone() {
        Fixture f = new Fixture().start();
        GridItem aCircle = f.portals.getPortalAt(CIRCLE_A_X, CIRCLE_A_Y);

        Projectile aProjectile = f.board.mProjectiles.dataArrayAlloc();
        aProjectile.mApp = f.app;
        aProjectile.mBoard = f.board;
        aProjectile.mProjectileType = ProjectileType.PROJECTILE_STAR;
        aProjectile.mMotionType = ProjectileMotion.MOTION_LOBBED;
        aProjectile.mWidth = 20;
        aProjectile.mHeight = 20;
        aProjectile.mRow = CIRCLE_A_Y;
        aProjectile.mX = 765;
        aProjectile.mY = 300;
        aProjectile.mPosX = 765.0f;
        aProjectile.mPosY = 300.0f;
        aProjectile.mShadowY = 400.0f;
        aProjectile.mLastPortalX = -1;

        f.portals.updatePortal(aCircle);

        assertEquals(765, aProjectile.mX);
        assertEquals(CIRCLE_A_Y, aProjectile.mRow);
    }

    // -----------------------------------------------------------------------------------------
    // UpdatePortal - lawn mowers
    // -----------------------------------------------------------------------------------------

    @Test
    void updatePortalTeleportsATriggeredMowerAndRunsItsUpdate() {
        Fixture f = new Fixture().start();
        GridItem aPortal = f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y);

        LawnMower aMower = f.board.mLawnMowers.dataArrayAlloc();
        aMower.mApp = f.app;
        aMower.mBoard = f.board;
        aMower.mRow = SQUARE_A_Y;
        aMower.mMowerState = LawnMowerState.MOWER_TRIGGERED;
        aMower.mMowerType = LawnMowerType.LAWNMOWER_LAWN;
        aMower.mMowerHeight = MowerHeight.MOWER_HEIGHT_LAND;
        aMower.mPosX = 145.0f; // (int)(145+45) = 190, 5px from the portal centre
        aMower.mPosY = f.board.getPosYBasedOnRow(aMower.mPosX + 40.0f, SQUARE_A_Y) + 23.0f;
        aMower.mLastPortalX = -1;
        aMower.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, SQUARE_A_Y, 0);
        aMower.mReanimID = 1;

        f.portals.updatePortal(aPortal);

        // Challenge.cpp:2974-2979 sets mPosX = 9*80+25, the new row and mLastPortalX, then Update()
        // advances the triggered mower by its 3.33 speed.
        assertEquals(SQUARE_B_Y, aMower.mRow);
        assertEquals(SQUARE_B_X, aMower.mLastPortalX);
        assertEquals(745.0f + 3.33f, aMower.mPosX, 0.01f);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, SQUARE_B_Y, 0), aMower.mRenderOrder);
        assertEquals(f.board.getPosYBasedOnRow(aMower.mPosX + 40.0f, SQUARE_B_Y) + 23.0f, aMower.mPosY, 0.01f);
    }

    @Test
    void updatePortalLeavesAnIdleMowerAlone() {
        Fixture f = new Fixture().start();
        GridItem aPortal = f.portals.getPortalAt(SQUARE_A_X, SQUARE_A_Y);

        LawnMower aMower = f.board.mLawnMowers.dataArrayAlloc();
        aMower.mApp = f.app;
        aMower.mBoard = f.board;
        aMower.mRow = SQUARE_A_Y;
        aMower.mMowerState = LawnMowerState.MOWER_READY;
        aMower.mPosX = 145.0f;
        aMower.mLastPortalX = -1;

        f.portals.updatePortal(aPortal);

        assertEquals(145.0f, aMower.mPosX);
        assertEquals(SQUARE_A_Y, aMower.mRow);
        assertEquals(-1, aMower.mLastPortalX);
    }

    // -----------------------------------------------------------------------------------------
    // MoveAPortal
    // -----------------------------------------------------------------------------------------

    @Test
    void moveAPortalRelocatesOnePortalOntoAFreeCell() {
        Fixture f = new Fixture().start();
        List<GridItem> before = new ArrayList<>(f.spawned);

        f.portals.moveAPortal();

        List<GridItem> after = new ArrayList<>();
        for (GridItem aGridItem : f.board.mGridItems) {
            after.add(aGridItem);
        }
        assertEquals(5, after.size(), "the relocated portal is a newly allocated grid item");

        GridItem aNewPortal = null;
        for (GridItem aGridItem : after) {
            if (!before.contains(aGridItem)) {
                aNewPortal = aGridItem;
            }
        }
        assertNotNull(aNewPortal, "MoveAPortal allocates one new grid item");

        int anOpen = 0;
        int aClosed = 0;
        GridItem aClosedPortal = null;
        for (GridItem aGridItem : after) {
            if (aGridItem.isOpenPortal()) {
                anOpen++;
            } else {
                aClosed++;
                aClosedPortal = aGridItem;
            }
        }
        assertEquals(4, anOpen, "the relocated portal is replaced one for one");
        assertEquals(1, aClosed, "the picked portal is closed");
        assertTrue(aNewPortal.isOpenPortal());
        assertEquals(aClosedPortal.mGridItemType, aNewPortal.mGridItemType, "the new portal keeps the type");
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, aNewPortal.mGridY, 0),
                aNewPortal.mRenderOrder);
        assertEquals(GridItemState.GRIDITEM_STATE_NORMAL, aNewPortal.mGridItemState);

        // Challenge.cpp:3039 excludes the whole row and column of the surviving pair.
        GridItem aPair = f.challenge.getOtherPortal(aClosedPortal);
        assertNotNull(aPair);
        assertTrue(aNewPortal.mGridX != aPair.mGridX && aNewPortal.mGridY != aPair.mGridY,
                "the new cell leaves the pair's row and column");
        assertNull(f.portals.getPortalAt(aClosedPortal.mGridX, aClosedPortal.mGridY),
                "the closed cell no longer resolves to a portal");
    }

    @Test
    void moveAPortalAlwaysLeavesThePairOpen() {
        Fixture f = new Fixture().start();

        for (int i = 0; i < 20; i++) {
            f.portals.moveAPortal();

            int anOpen = 0;
            for (GridItem aGridItem : f.board.mGridItems) {
                if (aGridItem.isOpenPortal()) {
                    anOpen++;
                }
            }
            assertEquals(4, anOpen, "relocation " + i + " keeps one open portal per pair");
        }
    }

    // -----------------------------------------------------------------------------------------
    // UpdatePortalCombat
    // -----------------------------------------------------------------------------------------

    @Test
    void updatePortalCombatCountsDownAndRelocatesOnExpiry() {
        Fixture f = new Fixture().start();

        f.portals.updatePortalCombat();
        assertEquals(8999, f.challenge.mChallengeStateCounter, "the portal timer counts down every tick");

        f.challenge.mChallengeStateCounter = 501;
        f.portals.updatePortalCombat();
        assertEquals(500, f.challenge.mChallengeStateCounter);
        assertEquals(AdviceType.ADVICE_PORTAL_RELOCATING, f.board.mHelpIndex, "the relocation hint is shown");
        assertEquals(4, f.portalGridItemCount(), "the hint frame does not relocate anything");

        f.challenge.mChallengeStateCounter = 1;
        f.portals.updatePortalCombat();
        assertEquals(6000, f.challenge.mChallengeStateCounter, "relocation restarts the timer");
        assertEquals(5, f.portalGridItemCount(), "a portal was relocated");
    }

    @Test
    void updatePortalCombatFreezesTheCountdownOnceTheLevelAwardDropped() {
        Fixture f = new Fixture().start();
        f.challenge.mChallengeStateCounter = 1;
        f.board.mLevelAwardSpawned = true;

        f.portals.updatePortalCombat();

        assertEquals(1, f.challenge.mChallengeStateCounter, "no countdown after the level award");
        assertEquals(4, f.portalGridItemCount(), "no relocation after the level award");
    }

    // -----------------------------------------------------------------------------------------
    // CanTargetZombieWithPortals
    // -----------------------------------------------------------------------------------------

    @Test
    void canTargetZombieWithPortalsFollowsThePortalChain() {
        Fixture f = new Fixture().start();

        Plant aPlant = new Plant();
        aPlant.mPlantCol = 0;
        aPlant.mRow = SQUARE_B_Y; // row 1, in front of the right square portal (9,1)

        // Same row, before the first portal to the right: the band is [0, 9*80).
        assertEquals(1, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(SQUARE_B_Y, 500, 10)));

        // Past that portal the chain hops through its pair (2,0) and only reaches column 2*80.
        assertEquals(0, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(SQUARE_B_Y, 750, 10)));

        // A zombie on row 0 is reachable through the hop: it sits between column 2 and the board edge.
        assertEquals(1, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(SQUARE_A_Y, 500, 10)));

        // A row with no portal chain is out of reach.
        assertEquals(0, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(2, 500, 10)));

        // A zombie in the plant's own column is never in the band [col*80, col*80).
        assertEquals(0, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(SQUARE_B_Y, 0, 10)));
    }

    @Test
    void canTargetZombieWithPortalsTreatsAnUnpairedPortalAsTheBoardEdge() {
        Fixture f = new Fixture().start();
        f.spawned.get(1).mDead = true; // the right square portal (9,1) loses its pair

        Plant aPlant = new Plant();
        aPlant.mPlantCol = 0;
        aPlant.mRow = SQUARE_B_Y;

        // GetPortalToRight skips dead grid items, so the row bounds are the plant column and the
        // board edge (Challenge.cpp:3215): both zombies are reachable.
        assertEquals(1, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(SQUARE_B_Y, 500, 10)));
        assertEquals(1, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(SQUARE_B_Y, 750, 10)));

        // Another row has no portal at all, so nothing there is reachable.
        assertEquals(0, f.portals.canTargetZombieWithPortals(aPlant, f.board.addStubZombie(SQUARE_A_Y, 500, 10)));
    }
}
