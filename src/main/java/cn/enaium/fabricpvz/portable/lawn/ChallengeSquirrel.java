/*
 * Ported from src/Lawn/Challenge.cpp:4624-4823 (Challenge::SquirrelCountUncaught, SquirrelStart,
 * SquirrelChew, SquirrelPeek, SquirrelFound, SquirrelUpdateOne, SquirrelUpdate).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;

/**
 * Ported from src/Lawn/Challenge.cpp:4624-4823 (Challenge::SquirrelCountUncaught, SquirrelStart,
 * SquirrelChew, SquirrelPeek, SquirrelFound, SquirrelUpdateOne, SquirrelUpdate).
 *
 * <p>The "Squirrel" challenge (GAMEMODE_CHALLENGE_SQUIRREL): squirrels hide under grid cells and
 * must be caught by removing the plants sitting on top of them.
 */
public class ChallengeSquirrel {
    public final Challenge c;

    public ChallengeSquirrel(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:4624-4638 ({@code Challenge::SquirrelCountUncaught}). */
    public int squirrelCountUncaught() {
        int aCount = 0;
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SQUIRREL
                    && aGridItem.mGridItemState != GridItemState.GRIDITEM_STATE_SQUIRREL_CAUGHT
                    && aGridItem.mGridItemState != GridItemState.GRIDITEM_STATE_SQUIRREL_ZOMBIE) {
                aCount++;
            }
        }

        return aCount;
    }

    /** Ported from src/Lawn/Challenge.cpp:4640-4682 ({@code Challenge::SquirrelStart}). */
    public void squirrelStart() {
        PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[Board.MAX_GRID_SIZE_X * (Board.MAX_GRID_SIZE_Y - 1)];
        int aPicksCount = 0;
        for (int aCol = 0; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y - 1; aRow++) {
                aPicks[aPicksCount] = new PvzpWeightedGridArray(aCol, aRow, 1);
                aPicksCount++;
            }
        }

        for (int aCount = Challenge.MAX_SQUIRRELS; aCount > 0; aCount--) {
            PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPicksCount);
            aGrid.mWeight = 0;

            GridItem aSquirrel = c.mBoard.mGridItems.dataArrayAlloc();
            aSquirrel.mGridItemType = GridItemType.GRIDITEM_SQUIRREL;
            aSquirrel.mGridItemState = GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING;
            aSquirrel.mGridX = aGrid.mX;
            aSquirrel.mGridY = aGrid.mY;
            aSquirrel.mGridItemCounter = PvzpCommon.randRangeInt(100, 500);
            aSquirrel.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, aSquirrel.mGridY, 1);
        }

        for (int i = 0; i < aPicksCount; i++) {
            if (aPicks[i].mX < 4) {
                aPicks[i].mWeight = 0;
            }
        }

        PvzpWeightedGridArray aZombieGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPicksCount);
        GridItem aZombieSquirrel = c.mBoard.mGridItems.dataArrayAlloc();
        aZombieSquirrel.mGridItemType = GridItemType.GRIDITEM_SQUIRREL;
        aZombieSquirrel.mGridItemState = GridItemState.GRIDITEM_STATE_SQUIRREL_ZOMBIE;
        aZombieSquirrel.mGridX = aZombieGrid.mX;
        aZombieSquirrel.mGridY = aZombieGrid.mY;
        aZombieSquirrel.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, aZombieGrid.mY, 1);
    }

    /** Ported from src/Lawn/Challenge.cpp:4684-4698 ({@code Challenge::SquirrelChew}). */
    public void squirrelChew(GridItem theSquirrel) {
        theSquirrel.mGridItemCounter = PvzpCommon.randRangeInt(100, 500);

        Plant aPlant = c.mBoard.getTopPlantAt(theSquirrel.mGridX, theSquirrel.mGridY, PlantPriority.TOPPLANT_EATING_ORDER);
        if (aPlant != null) {
            float aPosX = c.mBoard.gridToPixelX(theSquirrel.mGridX, theSquirrel.mGridY);
            float aPosY = c.mBoard.gridToPixelY(theSquirrel.mGridX, theSquirrel.mGridY);
            c.mApp.addPvzpParticle(aPosX + 40, aPosY + 40, aPlant.mRenderOrder + 1, ParticleEffect.PARTICLE_WALLNUT_EAT_SMALL);

            if (aPlant.mEatenFlashCountdown <= 25) {
                aPlant.mEatenFlashCountdown = 25;
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:4700-4704 ({@code Challenge::SquirrelPeek}). */
    public void squirrelPeek(GridItem theSquirrel) {
        theSquirrel.mGridItemCounter = 50;
        theSquirrel.mGridItemState = GridItemState.GRIDITEM_STATE_SQUIRREL_PEEKING;
    }

    /** Ported from src/Lawn/Challenge.cpp:4706-4769 ({@code Challenge::SquirrelFound}). */
    public void squirrelFound(GridItem theSquirrel) {
        if (theSquirrel.mGridItemState == GridItemState.GRIDITEM_STATE_SQUIRREL_ZOMBIE) {
            Zombie aZombie = c.mBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, theSquirrel.mGridY, 0);
            aZombie.mPosX = c.mBoard.gridToPixelX(theSquirrel.mGridX, theSquirrel.mGridY);
            theSquirrel.gridItemDie();
            c.mBoard.displayAdvice("[ADVICE_SQUIRREL_ZOMBIE]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
        } else {
            final int[][] aNeighborOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[4];
            int aPickCount = 0;
            for (int[] aOffset : aNeighborOffsets) {
                int aGridX = theSquirrel.mGridX + aOffset[0];
                int aGridY = theSquirrel.mGridY + aOffset[1];
                if (c.mBoard.getGridItemAt(GridItemType.GRIDITEM_SQUIRREL, aGridX, aGridY) == null
                        && c.mBoard.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_EATING_ORDER) != null) {
                    aPicks[aPickCount] = new PvzpWeightedGridArray(aGridX, aGridY, 1);
                    aPickCount++;
                }
            }

            if (aPickCount > 0) {
                PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPickCount);

                if (aGrid.mX != theSquirrel.mGridX) {
                    theSquirrel.mGridItemState = aGrid.mX < theSquirrel.mGridX
                            ? GridItemState.GRIDITEM_STATE_SQUIRREL_RUNNING_LEFT
                            : GridItemState.GRIDITEM_STATE_SQUIRREL_RUNNING_RIGHT;
                } else {
                    theSquirrel.mGridItemState = aGrid.mY < theSquirrel.mGridY
                            ? GridItemState.GRIDITEM_STATE_SQUIRREL_RUNNING_UP
                            : GridItemState.GRIDITEM_STATE_SQUIRREL_RUNNING_DOWN;
                }

                theSquirrel.mGridItemCounter = 50;
                theSquirrel.mGridX = aGrid.mX;
                theSquirrel.mGridY = aGrid.mY;
                theSquirrel.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, aGrid.mY, 1);
            } else {
                theSquirrel.mGridItemState = GridItemState.GRIDITEM_STATE_SQUIRREL_CAUGHT;
                theSquirrel.mGridItemCounter = 100;

                int aSquirrelsRemaining = squirrelCountUncaught();
                if (aSquirrelsRemaining > 0) {
                    c.mBoard.displayAdvice(LawnApp.pluralize(aSquirrelsRemaining, "[ADVICE_SQUIRRELS_ONE_LEFT]", "[ADVICE_SQUIRRELS_LEFT]"),
                            MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
                } else {
                    c.mBoard.clearAdvice(AdviceType.ADVICE_NONE);
                    c.spawnLevelAward(theSquirrel.mGridX, theSquirrel.mGridY);
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:4771-4809 ({@code Challenge::SquirrelUpdateOne}). */
    public void squirrelUpdateOne(GridItem theSquirrel) {
        if (theSquirrel.mGridItemCounter > 0) {
            theSquirrel.mGridItemCounter--;
        }

        int aState = theSquirrel.mGridItemState;
        if (aState == GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING || aState == GridItemState.GRIDITEM_STATE_SQUIRREL_ZOMBIE) {
            // a squirrel is uncovered once the plant sharing its cell is removed
            if (c.mBoard.getTopPlantAt(theSquirrel.mGridX, theSquirrel.mGridY, PlantPriority.TOPPLANT_EATING_ORDER) == null) {
                squirrelFound(theSquirrel);
                aState = theSquirrel.mGridItemState;
            }
            if (theSquirrel.mGridItemCounter == 0) {
                if (PvzpCommon.randRangeInt(0, 1) != 0 && aState != GridItemState.GRIDITEM_STATE_SQUIRREL_ZOMBIE) {
                    squirrelPeek(theSquirrel);
                    aState = theSquirrel.mGridItemState;
                } else {
                    squirrelChew(theSquirrel);
                }
            }
        }

        if (aState >= GridItemState.GRIDITEM_STATE_SQUIRREL_PEEKING
                && aState <= GridItemState.GRIDITEM_STATE_SQUIRREL_RUNNING_RIGHT && theSquirrel.mGridItemCounter == 0) {
            theSquirrel.mGridItemState = GridItemState.GRIDITEM_STATE_SQUIRREL_WAITING;
            theSquirrel.mGridItemCounter = PvzpCommon.randRangeInt(100, 500);
        }
        if (theSquirrel.mGridItemState == GridItemState.GRIDITEM_STATE_SQUIRREL_CAUGHT && theSquirrel.mGridItemCounter == 0) {
            theSquirrel.gridItemDie();
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:4811-4823 ({@code Challenge::SquirrelUpdate}). */
    public void squirrelUpdate() {
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SQUIRREL) {
                squirrelUpdateOne(aGridItem);
            }
        }

        c.mChallengeScore = Challenge.MAX_SQUIRRELS - squirrelCountUncaught();
        c.mBoard.mProgressMeterWidth = PvzpCommon.pvzpAnimateCurve(0, Challenge.MAX_SQUIRRELS, c.mChallengeScore,
                0, Board.PROGRESS_METER_COUNTER, PvzpCurves.CURVE_LINEAR);
    }
}
