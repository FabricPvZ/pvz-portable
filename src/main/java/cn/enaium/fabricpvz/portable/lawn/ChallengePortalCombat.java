package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Ported from src/Lawn/Challenge.cpp:2870-3062, 3064-3094, 3204-3233
 * ({@code Challenge::PortalStart}, {@code Challenge::UpdatePortal}, {@code Challenge::GetPortalAt},
 * {@code Challenge::MoveAPortal}, {@code Challenge::UpdatePortalCombat},
 * {@code Challenge::CanTargetZombieWithPortals}).
 */
public class ChallengePortalCombat {
    /** Upstream {@code MAX_PORTALS} (src/Lawn/Challenge.cpp:63). */
    private static final int MAX_PORTALS = 4;

    /** Upstream {@code MAX_PICK_GRID_SIZE} (src/Lawn/Challenge.h:35). */
    private static final int MAX_PICK_GRID_SIZE = 50;

    public final Challenge c;

    public ChallengePortalCombat(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:2870-2906 ({@code Challenge::PortalStart}). */
    public void portalStart() {
        c.mChallengeStateCounter = 9000;

        GridItem aPortal = c.mBoard.mGridItems.dataArrayAlloc();
        aPortal.mGridItemType = GridItemType.GRIDITEM_PORTAL_SQUARE;
        aPortal.mGridX = 2;
        aPortal.mGridY = 0;
        aPortal.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, aPortal.mGridY, 0);
        aPortal.openPortal();

        aPortal = c.mBoard.mGridItems.dataArrayAlloc();
        aPortal.mGridItemType = GridItemType.GRIDITEM_PORTAL_SQUARE;
        aPortal.mGridX = 9;
        aPortal.mGridY = 1;
        aPortal.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, aPortal.mGridY, 0);
        aPortal.openPortal();

        aPortal = c.mBoard.mGridItems.dataArrayAlloc();
        aPortal.mGridItemType = GridItemType.GRIDITEM_PORTAL_CIRCLE;
        aPortal.mGridX = 9;
        aPortal.mGridY = 3;
        aPortal.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, aPortal.mGridY, 0);
        aPortal.openPortal();

        aPortal = c.mBoard.mGridItems.dataArrayAlloc();
        aPortal.mGridItemType = GridItemType.GRIDITEM_PORTAL_CIRCLE;
        aPortal.mGridX = 2;
        aPortal.mGridY = 4;
        aPortal.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, aPortal.mGridY, 0);
        aPortal.openPortal();

        c.mBoard.mZombieCountDown = 200;
        c.mBoard.mZombieCountDownStart = c.mBoard.mZombieCountDown;
        c.mConveyorBeltCounter = 200;
    }

    /** Ported from src/Lawn/Challenge.cpp:2908-2984 ({@code Challenge::UpdatePortal}). */
    public void updatePortal(GridItem thePortal) {
        GridItem anOtherPortal = c.getOtherPortal(thePortal);
        if (anOtherPortal == null) {
            return;
        }

        for (Zombie aZombie : c.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mRow == thePortal.mGridY && aZombie.mLastPortalX != thePortal.mGridX) {
                Rect aZombieRect = aZombie.getZombieRect();
                int aZombieX = aZombieRect.mX + aZombieRect.mWidth / 2;
                int aPortalX = thePortal.mGridX * 80 + 25;
                if (Math.abs(aZombieX - aPortalX) <= 45) {
                    int aDiffX = aZombieX - aZombie.mX;
                    if (aZombie.isWalkingBackwards()) {
                        aDiffX -= 60;
                    }
                    aZombie.mX = anOtherPortal.mGridX * 80 - aDiffX;
                    aZombie.mPosX = aZombie.mX;

                    aZombie.setRow(anOtherPortal.mGridY);
                    aZombie.mY = (int) aZombie.getPosYBasedOnRow(anOtherPortal.mGridY);
                    aZombie.mPosY = aZombie.mY;

                    aZombie.mLastPortalX = anOtherPortal.mGridX;
                }
            }
        }

        for (Projectile aProjectile : c.mBoard.mProjectiles) {
            if (aProjectile.mDead) {
                continue;
            }
            if (aProjectile.mMotionType == ProjectileMotion.MOTION_STRAIGHT
                && aProjectile.mRow == thePortal.mGridY
                && aProjectile.mLastPortalX != thePortal.mGridX) {
                Rect aProjectileRect = aProjectile.getProjectileRect();
                int aProjectileX = aProjectileRect.mX + aProjectileRect.mWidth / 2;
                int aPortalX = thePortal.mGridX * 80 + 55;
                if (Math.abs(aProjectileX - aPortalX) <= 40) {
                    int aDeltaY = (anOtherPortal.mGridY - thePortal.mGridY) * 100;
                    aProjectile.mX += anOtherPortal.mGridX * 80 - aProjectileX + 60;
                    aProjectile.mPosX = aProjectile.mX;

                    aProjectile.mRow = anOtherPortal.mGridY;
                    aProjectile.mY += aDeltaY;
                    aProjectile.mPosY = aProjectile.mY;
                    aProjectile.mShadowY += aDeltaY;

                    aProjectile.mLastPortalX = anOtherPortal.mGridX;
                    aProjectile.mRenderOrder =
                        Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PROJECTILE, aProjectile.mRow, 0);
                }
            }
        }

        for (LawnMower aLawnMower : c.mBoard.mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            if (aLawnMower.mMowerState == LawnMowerState.MOWER_TRIGGERED
                && aLawnMower.mRow == thePortal.mGridY
                && aLawnMower.mLastPortalX != thePortal.mGridX) {
                int aMowerX = (int) (aLawnMower.mPosX + 45);
                int aPortalX = thePortal.mGridX * 80 + 25;
                if (Math.abs(aMowerX - aPortalX) <= 20) {
                    aLawnMower.mPosX = anOtherPortal.mGridX * 80 + 25;
                    aLawnMower.mRow = anOtherPortal.mGridY;
                    aLawnMower.mPosY = (anOtherPortal.mGridY - thePortal.mGridY) * 100;
                    aLawnMower.mLastPortalX = anOtherPortal.mGridX;
                    aLawnMower.mRenderOrder =
                        Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, aLawnMower.mRow, 0);
                    aLawnMower.update();
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3000-3012 ({@code Challenge::GetPortalAt}). */
    public GridItem getPortalAt(int theGridX, int theGridY) {
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridX == theGridX && aGridItem.mGridY == theGridY && aGridItem.isOpenPortal()) {
                return aGridItem;
            }
        }
        return null;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3014-3062 ({@code Challenge::MoveAPortal}).
     *
     * <p>Upstream stores the grid item pointer directly in {@code PvzpWeightedArray::mItem}; the port
     * stores the entry index and keeps the items in a parallel array. The upstream {@code PVZP_ASSERT}
     * invariants (at most {@code MAX_PORTALS} open portals, at most {@code MAX_PICK_GRID_SIZE} grid
     * candidates, at least one of each) are reproduced as {@link IllegalStateException}.
     */
    public void moveAPortal() {
        GridItem[] aPortals = new GridItem[MAX_PORTALS];
        PvzpWeightedArray[] aPickArray = new PvzpWeightedArray[MAX_PORTALS];
        int aNumpicks = 0;

        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.isOpenPortal()) {
                if (aNumpicks >= MAX_PORTALS) {
                    throw new IllegalStateException("Challenge.moveAPortal: more than MAX_PORTALS open portals");
                }
                aPickArray[aNumpicks] = new PvzpWeightedArray(aNumpicks, 1);
                aPortals[aNumpicks] = aGridItem;
                aNumpicks++;
            }
        }

        if (aNumpicks == 0) {
            throw new IllegalStateException("Challenge.moveAPortal: no open portals");
        }
        int aPicked = (int) PvzpCommon.pvzpPickFromWeightedArray(aPickArray, aNumpicks);
        GridItem aPortal = aPortals[aPicked];
        GridItem aOtherPortal = c.getOtherPortal(aPortal);
        if (aOtherPortal == null) {
            throw new IllegalStateException("Challenge.moveAPortal: portal without a pair");
        }

        PvzpWeightedGridArray[] aGridArray = new PvzpWeightedGridArray[MAX_PICK_GRID_SIZE];
        int aGridArrayCount = 0;
        for (int aGridX = 0; aGridX < 10; aGridX++) {
            for (int aGridY = 0; aGridY < 5; aGridY++) {
                if (getPortalAt(aGridX, aGridY) == null
                    && aOtherPortal.mGridX != aGridX && aOtherPortal.mGridY != aGridY) {
                    if (aGridArrayCount >= MAX_PICK_GRID_SIZE) {
                        throw new IllegalStateException("Challenge.moveAPortal: more than MAX_PICK_GRID_SIZE picks");
                    }
                    aGridArray[aGridArrayCount] = new PvzpWeightedGridArray(aGridX, aGridY, 1);
                    aGridArrayCount++;
                }
            }
        }

        PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aGridArray, aGridArrayCount);
        GridItem aNewPortal = c.mBoard.mGridItems.dataArrayAlloc();
        aNewPortal.mGridItemType = aPortal.mGridItemType;
        aNewPortal.mGridX = aGrid.mX;
        aNewPortal.mGridY = aGrid.mY;
        aNewPortal.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, aNewPortal.mGridY, 0);
        aNewPortal.openPortal();
        aPortal.closePortal();
    }

    /** Ported from src/Lawn/Challenge.cpp:3064-3094 ({@code Challenge::UpdatePortalCombat}). */
    public void updatePortalCombat() {
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.isOpenPortal()) {
                updatePortal(aGridItem);
            }
        }

        if (c.mBoard.hasLevelAwardDropped()) {
            c.mBoard.clearAdvice(AdviceType.ADVICE_PORTAL_RELOCATING);
        } else {
            c.mChallengeStateCounter--;
            if (c.mChallengeStateCounter == 500) {
                c.mBoard.displayAdviceAgain("[ADVICE_PORTAL_RELOCATING]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                    AdviceType.ADVICE_PORTAL_RELOCATING);
            } else if (c.mChallengeStateCounter <= 0) {
                c.mBoard.clearAdvice(AdviceType.ADVICE_PORTAL_RELOCATING);
                c.mChallengeStateCounter = 6000;
                moveAPortal();
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3204-3233 ({@code Challenge::CanTargetZombieWithPortals}).
     *
     * <p>Upstream returns {@code bool}; the port keeps the stub's {@code int} signature and returns
     * 1/0. It follows up to three portals to the right of the plant, checking the zombie against the
     * horizontal band between each portal pair.
     */
    public int canTargetZombieWithPortals(Plant thePlant, Zombie theZombie) {
        int aGridX = thePlant.mPlantCol;
        int aGridY = thePlant.mRow;

        for (int i = 0; i < 3; i++) {
            GridItem aPortal = c.getPortalToRight(aGridX, aGridY);
            if (aGridY == theZombie.mRow) {
                int aRangeLeft = aGridX * 80;
                int aRangeRight = aPortal != null ? aPortal.mGridX * 80 : 800;
                if (theZombie.mX > aRangeLeft && theZombie.mX < aRangeRight) {
                    return 1;
                }
            }

            if (aPortal != null) {
                GridItem aOtherPortal = c.getOtherPortal(aPortal);
                if (aOtherPortal != null) {
                    aGridX = aOtherPortal.mGridX;
                    aGridY = aOtherPortal.mGridY;
                    continue;
                }
            }
            break;
        }
        return 0;
    }
}
