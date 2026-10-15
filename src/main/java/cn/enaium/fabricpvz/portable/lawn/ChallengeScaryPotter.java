package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.sexy.Color;

import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Challenge.cpp:2060-2083, 3456-3520, 3504-3532, 3534-3755, 3757-3763,
 * 3765-3828, 3830-3866, 3868-3924, 3926-3940, 3942-3980
 * (Challenge::DrawArtChallenge, ScaryPotterDontPlaceInCol, ScaryPotterFillColumnWithPlant,
 * ScaryPotterPlacePot, ScaryPotterChangePotType, ScaryPotterPopulate, ScaryPotterStart,
 * ScaryPotterIsCompleted, ScaryPotterUpdate, ScaryPotterMalletPot, ScaryPotterCountPots,
 * PuzzleIsAwardStage, PuzzlePhaseComplete, ScaryPotterCountSunInPot, ScaryPotterOpenPot,
 * ScaryPotterJackExplode, PuzzleNextStageClear).
 */
public class ChallengeScaryPotter {
    /** Upstream {@code MAX_SCARY_POTS} (src/Lawn/Challenge.cpp:65). */
    private static final int MAX_SCARY_POTS = 54;

    public final Challenge c;

    public ChallengeScaryPotter(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:2060-2083 ({@code Challenge::DrawArtChallenge}). */
    public void drawArtChallenge(Graphics g) {
        g.setColorizeImages(true);
        g.setColor(new Color(255, 255, 255, 100));

        for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
            for (int aCol = 0; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
                int aSeedType = c.getArtChallengeSeed(aCol, aRow);
                if (aSeedType != SeedType.SEED_NONE
                        && c.mBoard.getTopPlantAt(aCol, aRow, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION) == null) {
                    Plant.drawSeedType(g, aSeedType, SeedType.SEED_NONE, DrawVariation.VARIATION_NORMAL,
                            c.mBoard.gridToPixelX(aCol, aRow), c.mBoard.gridToPixelY(aCol, aRow));
                }
            }
        }

        if (c.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT) {
            // assets removed, perhaps add back?
        }

        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/Challenge.cpp:3456-3465 ({@code Challenge::ScaryPotterDontPlaceInCol}). */
    public void scaryPotterDontPlaceInCol(int theCol, PvzpWeightedGridArray[] theGridArray, int theGridArrayCount) {
        for (int i = 0; i < theGridArrayCount; i++) {
            if (theGridArray[i].mX == theCol) {
                theGridArray[i].mWeight = 0;
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3467-3479 ({@code Challenge::ScaryPotterFillColumnWithPlant}). */
    public void scaryPotterFillColumnWithPlant(int theCol, int theSeedType, PvzpWeightedGridArray[] theGridArray,
            int theGridArrayCount) {
        scaryPotterDontPlaceInCol(theCol, theGridArray, theGridArrayCount);

        for (int i = 0; i < Board.MAX_GRID_SIZE_Y - 1; i++) {
            Plant aPlant = c.mBoard.newPlant(theCol, i, theSeedType, SeedType.SEED_NONE);
            if (theSeedType == SeedType.SEED_POTATOMINE) {
                aPlant.mStateCountdown = 10;
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3481-3502 ({@code Challenge::ScaryPotterPlacePot}).
     *
     * <p>{@code theScaryPotType} is a {@link ScaryPotType} value, {@code theZombieType} a
     * {@link ZombieType} value and {@code theSeedType} a {@link SeedType} value.
     */
    public void scaryPotterPlacePot(int theScaryPotType, int theZombieType, int theSeedType, int theCount,
            PvzpWeightedGridArray[] theGridArray, int theGridArrayCount) {
        int aPotType = theScaryPotType;
        while (theCount > 0) {
            PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(theGridArray, theGridArrayCount);

            GridItem aScaryPot = c.mBoard.mGridItems.dataArrayAlloc();
            aScaryPot.mGridItemType = GridItemType.GRIDITEM_SCARY_POT;
            aScaryPot.mGridItemState = GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION;
            aScaryPot.mGridX = aGrid.mX;
            aScaryPot.mGridY = aGrid.mY;
            aGrid.mWeight = 0;
            aScaryPot.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, aGrid.mY, 0);
            aScaryPot.mZombieType = theZombieType;
            aScaryPot.mSeedType = theSeedType;
            aScaryPot.mScaryPotType = aPotType;
            if (aPotType == ScaryPotType.SCARYPOT_SUN) {
                aScaryPot.mSunCount = PvzpCommon.randRangeInt(0, 2) + 1;
            }
            --theCount;
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3504-3532 ({@code Challenge::ScaryPotterChangePotType});
     * {@code thePotType} is a {@link GridItemState} value.
     */
    public void scaryPotterChangePotType(int thePotType, int theCount) {
        PvzpWeightedArray[] aPotArray = new PvzpWeightedArray[MAX_SCARY_POTS];
        GridItem[] aPotItems = new GridItem[MAX_SCARY_POTS];
        int aPotCount = 0;

        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SCARY_POT) {
                if ((thePotType == GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF
                        && aGridItem.mScaryPotType == ScaryPotType.SCARYPOT_SEED)
                        || (thePotType == GridItemState.GRIDITEM_STATE_SCARY_POT_ZOMBIE
                                && aGridItem.mZombieType == ZombieType.ZOMBIE_GARGANTUAR)) {
                    aPotArray[aPotCount] = new PvzpWeightedArray(aPotCount, 1);
                    aPotItems[aPotCount] = aGridItem;
                    aPotCount++;
                }
            }
        }
        theCount = Math.min(theCount, aPotCount);

        for (int i = 0; i < theCount; i++) {
            PvzpWeightedArray aScaryPotArray = PvzpCommon.pvzpPickArrayItemFromWeightedArray(aPotArray, aPotCount);
            if (aScaryPotArray == null) {
                break;
            }
            aScaryPotArray.mWeight = 0;
            aPotItems[(int) aScaryPotArray.mItem].mGridItemState = thePotType;
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3534-3755 ({@code Challenge::ScaryPotterPopulate}). */
    public void scaryPotterPopulate() {
        int aGridArrayCount = 0;
        PvzpWeightedGridArray[] aGridArray = new PvzpWeightedGridArray[MAX_SCARY_POTS];
        for (int aGridX = 0; aGridX < Board.MAX_GRID_SIZE_X; aGridX++) {
            for (int aGridY = 0; aGridY < Board.MAX_GRID_SIZE_Y - 1; aGridY++) {
                PvzpWeightedGridArray aGrid = new PvzpWeightedGridArray();
                aGrid.mX = aGridX;
                aGrid.mY = aGridY;
                aGrid.mWeight = 1;
                aGridArray[aGridArrayCount] = aGrid;
                aGridArrayCount++;
            }
        }

        if (c.mApp.isAdventureMode() && c.mBoard.mLevel == 35) {
            switch (c.mSurvivalStage) {
            case 0:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(2, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(3, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(4, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                break;
            case 1:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(2, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(3, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_FOOTBALL, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case 2:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(2, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_HYPNOSHROOM, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_DANCER, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 3);
                break;
            default:
                break;
            }
        } else {
            switch (c.mApp.mGameMode) {
            case GameMode.GAMEMODE_SCARY_POTTER_1:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(2, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_2:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(2, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(8, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 7, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_WALLNUT, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_POTATOMINE, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_3:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_HYPNOSHROOM, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_WALLNUT, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 8, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_DANCER, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_4:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PUFFSHROOM, 11, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_HYPNOSHROOM, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 8, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 7, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_FOOTBALL, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_5:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PUMPKINSHELL, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_HYPNOSHROOM, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_MAGNETSHROOM, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_FOOTBALL, SeedType.SEED_NONE, 3, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_6:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 7, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_TALLNUT, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_THREEPEATER, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_TORCHWOOD, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 7, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_POLEVAULTER, SeedType.SEED_NONE, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_FOOTBALL, SeedType.SEED_NONE, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_7:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SPIKEWEED, 13, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_WALLNUT, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 10, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_8:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PUFFSHROOM, 7, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_TALLNUT, 3, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 8, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 4, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_POGO, SeedType.SEED_NONE, 4, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_9:
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_THREEPEATER, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_POTATOMINE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_WALLNUT, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PLANTERN, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 8, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_GARGANTUAR, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);
                break;
            case GameMode.GAMEMODE_SCARY_POTTER_ENDLESS: {
                int aNumExtraGargantuars = Math.max(0, Math.min(8, c.mSurvivalStage / 10));
                scaryPotterDontPlaceInCol(0, aGridArray, aGridArrayCount);
                scaryPotterDontPlaceInCol(1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_LEFTPEATER, 6, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SNOWPEA, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PEASHOOTER, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_THREEPEATER, 2, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_SQUASH, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_POTATOMINE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_WALLNUT, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SEED, ZombieType.ZOMBIE_INVALID, SeedType.SEED_PLANTERN, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_SUN, ZombieType.ZOMBIE_INVALID, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_NORMAL, SeedType.SEED_NONE, 8 - aNumExtraGargantuars, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_PAIL, SeedType.SEED_NONE, 5, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_JACK_IN_THE_BOX, SeedType.SEED_NONE, 1, aGridArray, aGridArrayCount);
                scaryPotterPlacePot(ScaryPotType.SCARYPOT_ZOMBIE, ZombieType.ZOMBIE_GARGANTUAR, SeedType.SEED_NONE, 1 + aNumExtraGargantuars, aGridArray, aGridArrayCount);
                scaryPotterChangePotType(GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF, 2);

                if (c.mSurvivalStage == 15) {
                    ReportAchievement.giveAchievement(c.mApp, AchievementId.ChinaShop, true);
                }

                break;
            }
            default:
                break;
            }
        }

        c.mScaryPotterPots = scaryPotterCountPots();
    }

    /** Ported from src/Lawn/Challenge.cpp:3757-3763 ({@code Challenge::ScaryPotterStart}). */
    public void scaryPotterStart() {
        if (c.mApp.isAdventureMode()) {
            c.mBoard.displayAdvice("[ADVICE_USE_SHOVEL_ON_POTS]", MessageStyle.MESSAGE_STYLE_HINT_STAY,
                    AdviceType.ADVICE_USE_SHOVEL_ON_POTS);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3802-3813 ({@code Challenge::ScaryPotterIsCompleted}). */
    public int scaryPotterIsCompleted() {
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SCARY_POT) {
                return 0;
            }
        }

        return c.mBoard.areEnemyZombiesOnScreen() ? 0 : 1;
    }

    /** Ported from src/Lawn/Challenge.cpp:3780-3800 ({@code Challenge::ScaryPotterUpdate}). */
    public void scaryPotterUpdate() {
        if (c.mChallengeState == ChallengeState.STATECHALLENGE_SCARY_POTTER_MALLETING) {
            Reanimation aMalletReanim = c.mApp.reanimationGet(c.mReanimChallenge);
            if (aMalletReanim.mLoopCount > 0) {
                GridItem aScaryPot = c.mBoard.getGridItemAt(GridItemType.GRIDITEM_SCARY_POT, c.mChallengeGridX,
                        c.mChallengeGridY);
                if (aScaryPot != null) {
                    scaryPotterOpenPot(aScaryPot);
                }

                c.mChallengeGridX = 0;
                c.mChallengeGridY = 0;
                aMalletReanim.reanimationDie();
                c.mReanimChallenge = ReanimationID.REANIMATIONID_NULL;
                c.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3802-3813, 3815-3828 ({@code Challenge::ScaryPotterMalletPot}). */
    public void scaryPotterMalletPot(GridItem theScaryPot) {
        c.mChallengeGridX = theScaryPot.mGridX;
        c.mChallengeGridY = theScaryPot.mGridY;
        int aXPos = c.mBoard.gridToPixelX(theScaryPot.mGridX, theScaryPot.mGridY);
        int aYPos = c.mBoard.gridToPixelY(theScaryPot.mGridX, theScaryPot.mGridY);
        Reanimation aMalletReanim = c.mApp.addReanimation(aXPos, aYPos, RenderLayer.RENDER_LAYER_TOP,
                ReanimationType.REANIM_HAMMER);
        aMalletReanim.playReanim("anim_pot_open", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 40.0f);
        c.mReanimChallenge = c.mApp.reanimationGetID(aMalletReanim);
        c.mChallengeState = ChallengeState.STATECHALLENGE_SCARY_POTTER_MALLETING;
        c.mApp.playFoley(FoleyType.FOLEY_SWING);
    }

    /** Ported from src/Lawn/Challenge.cpp:3815-3828 ({@code Challenge::ScaryPotterCountPots}). */
    public int scaryPotterCountPots() {
        int aCount = 0;
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SCARY_POT) {
                aCount++;
            }
        }
        return aCount;
    }

    /** Ported from src/Lawn/Challenge.cpp:3830-3837 ({@code Challenge::PuzzleIsAwardStage}). */
    public int puzzleIsAwardStage() {
        if (c.mApp.isAdventureMode()) {
            return 0;
        }

        int aGoal = c.mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS ? 3
                : c.mApp.mGameMode == GameMode.GAMEMODE_SCARY_POTTER_ENDLESS ? 10 : 1;
        return c.mSurvivalStage % aGoal == 0 ? 1 : 0;
    }

    /** Ported from src/Lawn/Challenge.cpp:3839-3866 ({@code Challenge::PuzzlePhaseComplete}). */
    public void puzzlePhaseComplete(int theGridX, int theGridY) {
        if (puzzleIsAwardStage() != 0) {
            int aCoinType;
            int aHit = PvzpCommon.randRangeInt(0, 99);
            if (aHit < 15) {
                aCoinType = c.mApp.mZenGarden.canDropPottedPlantLoot() ? CoinType.COIN_AWARD_PRESENT
                        : CoinType.COIN_AWARD_MONEY_BAG;
            } else if (aHit < 30) {
                aCoinType = c.mApp.mZenGarden.canDropChocolate() ? CoinType.COIN_AWARD_CHOCOLATE
                        : CoinType.COIN_AWARD_MONEY_BAG;
            } else {
                aCoinType = CoinType.COIN_AWARD_BAG_DIAMOND;
            }

            int aPosX = c.mBoard.gridToPixelX(theGridX, theGridY) + 40;
            int aPosY = c.mBoard.gridToPixelY(theGridX, theGridY) + 40;
            c.mBoard.addCoin(aPosX, aPosY, aCoinType, CoinMotion.COIN_MOTION_COIN);
        } else {
            c.mBoard.fadeOutLevel();
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3868-3871 ({@code Challenge::ScaryPotterCountSunInPot}). */
    public int scaryPotterCountSunInPot(GridItem theScaryPot) {
        return theScaryPot.mSunCount;
    }

    /** Ported from src/Lawn/Challenge.cpp:3873-3924 ({@code Challenge::ScaryPotterOpenPot}). */
    public void scaryPotterOpenPot(GridItem theScaryPot) {
        int aXPos = c.mBoard.gridToPixelX(theScaryPot.mGridX, theScaryPot.mGridY);
        int aYPos = c.mBoard.gridToPixelY(theScaryPot.mGridX, theScaryPot.mGridY);
        switch (theScaryPot.mScaryPotType) {
        case ScaryPotType.SCARYPOT_SEED:
            c.mBoard.addCoin(aXPos + 20, aYPos, CoinType.COIN_USABLE_SEED_PACKET, CoinMotion.COIN_MOTION_FROM_PLANT).mUsableSeedType = theScaryPot.mSeedType;
            break;
        case ScaryPotType.SCARYPOT_ZOMBIE:
            c.mBoard.addZombieInRow(theScaryPot.mZombieType, theScaryPot.mGridY, 0).mPosX = aXPos;
            break;
        case ScaryPotType.SCARYPOT_SUN: {
            int aSunCount = scaryPotterCountSunInPot(theScaryPot);
            for (int i = 0; i < aSunCount; i++) {
                c.mBoard.addCoin(aXPos + 15 * i, aYPos, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
            }
            break;
        }
        default:
            break;
        }

        theScaryPot.gridItemDie();

        if (c.mBoard.mHelpIndex == AdviceType.ADVICE_USE_SHOVEL_ON_POTS) {
            c.mBoard.displayAdvice("[ADVICE_DESTROY_POTS_TO_FINISH_LEVEL]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                    AdviceType.ADVICE_DESTORY_POTS_TO_FINISIH_LEVEL);
        }
        if (scaryPotterIsCompleted() != 0) {
            if (c.mApp.isScaryPotterLevel() && !c.mBoard.isFinalScaryPotterStage()) {
                puzzlePhaseComplete(theScaryPot.mGridX, theScaryPot.mGridY);
            } else {
                c.spawnLevelAward(theScaryPot.mGridX, theScaryPot.mGridY);
            }
        }

        c.mApp.playSample(Resources.SOUND_BONK_ID);
        c.mApp.playFoley(FoleyType.FOLEY_VASE_BREAKING);
        int anEffect = theScaryPot.mGridItemState == GridItemState.GRIDITEM_STATE_SCARY_POT_LEAF ? ParticleEffect.PARTICLE_VASE_SHATTER_LEAF
                : theScaryPot.mGridItemState == GridItemState.GRIDITEM_STATE_SCARY_POT_ZOMBIE ? ParticleEffect.PARTICLE_VASE_SHATTER_ZOMBIE
                        : ParticleEffect.PARTICLE_VASE_SHATTER;
        c.mApp.addPvzpParticle(aXPos + 20, aYPos, RenderLayer.RENDER_LAYER_GROUND, anEffect);
    }

    /** Ported from src/Lawn/Challenge.cpp:3926-3940 ({@code Challenge::ScaryPotterJackExplode}). */
    public void scaryPotterJackExplode(int thePosX, int thePosY) {
        int aGridX = c.mBoard.pixelToGridX(thePosX, thePosY);
        int aGridY = c.mBoard.pixelToGridY(thePosX, thePosY);

        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SCARY_POT
                    && LawnCommon.gridInRange(aGridItem.mGridX, aGridItem.mGridY, aGridX, aGridY, 1, 1)) {
                scaryPotterOpenPot(aGridItem);
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3942-3980 ({@code Challenge::PuzzleNextStageClear}). */
    public void puzzleNextStageClear() {
        c.mApp.playSample(Resources.SOUND_HUGE_WAVE_ID);
        c.mBoard.mNextSurvivalStageCounter = 0;
        c.mBoard.mProgressMeterWidth = 0;

        for (Zombie aZombie : c.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.isOnBoard()) {
                aZombie.dieNoLoot();
            }
        }

        for (Plant aPlant : c.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.isOnBoard()) {
                aPlant.die();
            }
        }
        c.mBoard.refreshSeedPacketFromCursor();

        for (Coin aCoin : c.mBoard.mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (aCoin.mType == CoinType.COIN_USABLE_SEED_PACKET) {
                aCoin.die();
            } else {
                aCoin.tryAutoCollectAfterLevelAward();
            }
        }

        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            aGridItem.gridItemDie();
        }

        c.mSurvivalStage++;
        c.mBoard.clearAdviceImmediately();
        c.mBoard.mLevelAwardSpawned = false;
        c.mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2.0f, GameConstants.BOARD_HEIGHT / 2.0f,
                RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_SCREEN_FLASH);
    }
}
