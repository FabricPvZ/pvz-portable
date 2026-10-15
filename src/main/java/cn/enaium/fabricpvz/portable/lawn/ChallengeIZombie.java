package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FilterEffect;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Challenge.cpp:4026-4041, 4043-4085, 4087-4106, 4108-4114, 4116-4173,
 * 4175-4401, 4403-4406, 4408-4469, 4493-4503, 4505-4539, 4541-4561, 4563-4584, 4586-4601,
 * 4603-4613, 4615-4622 ({@code Challenge::IZombiePlaceZombie},
 * {@code Challenge::IZombieMouseDownWithZombie}, {@code Challenge::IZombieSetupPlant},
 * {@code Challenge::IZombiePlacePlantInSquare}, {@code Challenge::IZombiePlacePlants},
 * {@code Challenge::IZombieInitLevel}, {@code Challenge::IZombieStart},
 * {@code Challenge::IZombieUpdate}, {@code Challenge::IZombieSetPlantFilterEffect},
 * {@code Challenge::IZombieDrawPlant}, {@code Challenge::IZombieGetBrainTarget},
 * {@code Challenge::IZombieScoreBrain}, {@code Challenge::IZombieEatBrain},
 * {@code Challenge::IZombiePlantDropRemainingSun}, {@code Challenge::IZombieSquishBrain}).
 */
public class ChallengeIZombie {
    public final Challenge c;

    public ChallengeIZombie(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4026-4041 ({@code Challenge::IZombiePlaceZombie}).
     *
     * <p>A bungee zombie is placed straight on its target column (and given a grave-stone render
     * order so it draws above the plants), every other zombie is nudged 30 px left of the square.
     */
    public void iZombiePlaceZombie(int theZombieType, int theGridX, int theGridY) {
        Zombie aZombie = c.mBoard.addZombieInRow(theZombieType, theGridY, 0);
        if (theZombieType == ZombieType.ZOMBIE_BUNGEE) {
            aZombie.mTargetCol = theGridX;
            aZombie.setRow(theGridY);
            aZombie.mPosX = c.mBoard.gridToPixelX(theGridX, theGridY);
            aZombie.mPosY = aZombie.getPosYBasedOnRow(theGridY);
            aZombie.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, theGridY, 7);
        } else {
            aZombie.mPosX = c.mBoard.gridToPixelX(theGridX, theGridY) - 30.0f;
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4043-4085 ({@code Challenge::IZombieMouseDownWithZombie}).
     *
     * <p>Drops the cursor's zombie packet on the clicked square when it is right of the red line,
     * otherwise shows the matching hint. Falls through to the packet-drop feedback (refresh the
     * seed packet from the cursor + {@code FOLEY_DROP}) when no square was clicked.
     */
    public void iZombieMouseDownWithZombie(int theX, int theY, int theClickCount) {
        if (theClickCount >= 0) {
            int aSeedType = c.mBoard.mCursorObject.mType;
            int aGridX = c.mBoard.plantingPixelToGridX(theX, theY, aSeedType);
            int aGridY = c.mBoard.plantingPixelToGridY(theX, theY, aSeedType);
            if (aGridX != -1 && aGridY != -1 && theClickCount != 0) {
                if (c.canPlantAt(aGridX, aGridY, aSeedType) == PlantingReason.PLANTING_OK) {
                    if (c.mApp.mEasyPlantingCheat
                            || c.mBoard.takeSunMoney(c.mBoard.getCurrentPlantCost(aSeedType, SeedType.SEED_NONE))) {
                        c.mBoard.clearAdvice(AdviceType.ADVICE_I_ZOMBIE_LEFT_OF_LINE);
                        c.mBoard.clearAdvice(AdviceType.ADVICE_I_ZOMBIE_NOT_PASSED_LINE);
                        int aZombieType = Challenge.iZombieSeedTypeToZombieType(aSeedType);
                        iZombiePlaceZombie(aZombieType, aGridX, aGridY);

                        // Challenge.cpp:4069:
                        // PVZP_ASSERT(mBoard->mCursorObject->mSeedBankIndex >= 0
                        //             && mBoard->mCursorObject->mSeedBankIndex < mBoard->mSeedBank->mNumPackets);
                        int aSeedBankIndex = c.mBoard.mCursorObject.mSeedBankIndex;
                        if (aSeedBankIndex < 0 || aSeedBankIndex >= c.mBoard.mSeedBank.mNumPackets) {
                            throw new IllegalStateException("Challenge.IZombieMouseDownWithZombie: cursor seed bank "
                                    + "index " + aSeedBankIndex + " is out of range");
                        }
                        c.mBoard.mSeedBank.mSeedPackets[aSeedBankIndex].wasPlanted();
                        c.mApp.playFoley(FoleyType.FOLEY_PLANT);
                        c.mBoard.clearCursor();
                    }
                } else {
                    c.mBoard.clearAdvice(AdviceType.ADVICE_NONE);
                    if (aSeedType == SeedType.SEED_ZOMBIE_BUNGEE) {
                        c.mBoard.displayAdvice("[ADVICE_I_ZOMBIE_LEFT_OF_LINE]", MessageStyle.MESSAGE_STYLE_HINT_LONG,
                                AdviceType.ADVICE_I_ZOMBIE_LEFT_OF_LINE);
                    } else {
                        c.mBoard.displayAdvice("[ADVICE_I_ZOMBIE_NOT_PASSED_LINE]",
                                MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_I_ZOMBIE_NOT_PASSED_LINE);
                    }
                }

                return;
            }
        }
        c.mBoard.refreshSeedPacketFromCursor();
        c.mApp.playFoley(FoleyType.FOLEY_DROP);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4087-4106 ({@code Challenge::IZombieSetupPlant}).
     *
     * <p>Freezes every plant reanimation of the pre-placed defender; a potato mine is additionally
     * forced to its armed state.
     */
    public void iZombieSetupPlant(Plant thePlant) {
        Reanimation aBodyReanim = c.mApp.reanimationTryToGet(thePlant.mBodyReanimID);
        Reanimation aHeadReanim = c.mApp.reanimationTryToGet(thePlant.mHeadReanimID);
        Reanimation aHeadReanim2 = c.mApp.reanimationTryToGet(thePlant.mHeadReanimID2);
        Reanimation aHeadReanim3 = c.mApp.reanimationTryToGet(thePlant.mHeadReanimID3);
        if (aBodyReanim != null) {
            aBodyReanim.mAnimRate = 0;
        }
        if (aHeadReanim != null) {
            aHeadReanim.mAnimRate = 0;
        }
        if (aHeadReanim2 != null) {
            aHeadReanim2.mAnimRate = 0;
        }
        if (aHeadReanim3 != null) {
            aHeadReanim3.mAnimRate = 0;
        }

        if (thePlant.mSeedType == SeedType.SEED_POTATOMINE) {
            thePlant.playBodyReanim("anim_armed", ReanimLoopType.REANIM_LOOP, 0, 0);
            thePlant.mState = PlantState.STATE_POTATO_ARMED;
        }

        thePlant.mBlinkCountdown = 0;
        thePlant.updateReanim();
    }

    /** Ported from src/Lawn/Challenge.cpp:4108-4114 ({@code Challenge::IZombiePlacePlantInSquare}). */
    public void iZombiePlacePlantInSquare(int theSeedType, int theGridX, int theGridY) {
        if (c.mBoard.canPlantAt(theGridX, theGridY, theSeedType) == PlantingReason.PLANTING_OK) {
            iZombieSetupPlant(c.mBoard.newPlant(theGridX, theGridY, theSeedType, SeedType.SEED_NONE));
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4116-4173 ({@code Challenge::IZombiePlacePlants}).
     *
     * <p>Places {@code theCount} plants of {@code theSeedType} on a weighted random pick of the free
     * squares left of the red line. theGridY == -1 means every row; wall-nuts and torchwoods are
     * restricted to the rightmost three columns.
     */
    public void iZombiePlacePlants(int theSeedType, int theCount, int theGridY) {
        // Columns available for plants (the red line limit)
        int aColumns = 6;
        int aGameMode = c.mApp.mGameMode;
        if (aGameMode >= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 && aGameMode <= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5) {
            aColumns = 4;
        } else if (aGameMode != GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9) {
            aColumns = 5;
        }

        // Only spawn between rows aMinGridY and aMaxGridY
        int aMinGridY;
        int aMaxGridY;
        if (theGridY == -1) {  // theGridY == -1 means no row restriction
            aMinGridY = 0;
            aMaxGridY = 4;
        } else {
            aMinGridY = theGridY;
            aMaxGridY = theGridY;  // restrict planting to row theGridY only
        }

        int aGridArrayCount = 0;
        PvzpWeightedGridArray[] aGridArray = new PvzpWeightedGridArray[Board.MAX_GRID_SIZE_X * Board.MAX_GRID_SIZE_Y];
        // Collect candidate squares from rows aMinGridY to aMaxGridY
        for (int aRow = aMinGridY; aRow <= aMaxGridY; aRow++) {
            // In each row, collect the columns left of the red line
            for (int aCol = 0; aCol < aColumns; aCol++) {
                if (c.mBoard.canPlantAt(aCol, aRow, theSeedType) == PlantingReason.PLANTING_OK) {
                    // Wall-nuts and torchwoods only appear in the rightmost 3 columns
                    if ((theSeedType != SeedType.SEED_WALLNUT && theSeedType != SeedType.SEED_TORCHWOOD)
                            || aColumns - aCol <= 3) {
                        PvzpWeightedGridArray aGrid = new PvzpWeightedGridArray();
                        aGrid.mX = aCol;
                        aGrid.mY = aRow;
                        aGrid.mWeight = 1;
                        aGridArray[aGridArrayCount] = aGrid;
                        aGridArrayCount++;
                    }
                }
            }
        }

        theCount = Math.min(theCount, aGridArrayCount);

        for (int i = 0; i < theCount; i++) {
            PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aGridArray, aGridArrayCount);
            iZombiePlacePlantInSquare(theSeedType, aGrid.mX, aGrid.mY);
            aGrid.mWeight = 0;
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4175-4401 ({@code Challenge::IZombieInitLevel}).
     *
     * <p>Lays out the five brains in column 0, then fills the defenders for the active I,Zombie
     * stage (a fixed layout for stages 1-9, a randomly rolled formation for the endless stage).
     */
    public void iZombieInitLevel() {
        c.mChallengeScore = 0;
        for (int aRow = 0; aRow < Challenge.I_ZOMBIE_WINNING_SCORE; aRow++) {
            GridItem aBrain = c.mBoard.mGridItems.dataArrayAlloc();
            aBrain.mGridItemType = GridItemType.GRIDITEM_IZOMBIE_BRAIN;
            aBrain.mGridX = 0;
            aBrain.mGridY = aRow;
            aBrain.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, aRow, 0);
            aBrain.mGridItemCounter = 70;
            aBrain.mPosX = c.mBoard.gridToPixelX(0, aRow) - 40;
            aBrain.mPosY = c.mBoard.gridToPixelY(0, aRow) + 40;
        }

        switch (c.mApp.mGameMode) {
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1:
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 3, 2);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 3, 3);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 7, -1);
            iZombiePlacePlants(SeedType.SEED_SQUASH, 3, -1);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 6, -1);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 2, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2:
            iZombiePlacePlantInSquare(SeedType.SEED_SPIKEWEED, 3, 0);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 0);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 3, 3);
            iZombiePlacePlants(SeedType.SEED_SPIKEWEED, 1, 0);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 1, 0);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 2, 3);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 1, 3);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 4, -1);
            iZombiePlacePlants(SeedType.SEED_SPIKEWEED, 2, -1);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 2, -1);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 4, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3:
            iZombiePlacePlantInSquare(SeedType.SEED_POTATOMINE, 3, 0);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 0);
            iZombiePlacePlantInSquare(SeedType.SEED_POTATOMINE, 2, 2);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 4);
            iZombiePlacePlantInSquare(SeedType.SEED_TORCHWOOD, 3, 3);
            iZombiePlacePlants(SeedType.SEED_TORCHWOOD, 2, -1);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 5, -1);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 7, -1);
            iZombiePlacePlants(SeedType.SEED_SPLITPEA, 1, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4:
            iZombiePlacePlantInSquare(SeedType.SEED_WALLNUT, 3, 0);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 0);
            iZombiePlacePlantInSquare(SeedType.SEED_WALLNUT, 3, 1);
            iZombiePlacePlantInSquare(SeedType.SEED_WALLNUT, 3, 2);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 2);
            iZombiePlacePlantInSquare(SeedType.SEED_WALLNUT, 3, 3);
            iZombiePlacePlantInSquare(SeedType.SEED_WALLNUT, 3, 4);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 4);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 1, 0);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 1, 1);
            iZombiePlacePlants(SeedType.SEED_FUMESHROOM, 2, 2);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 1, 3);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 1, 4);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 2, -1);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 4, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5:
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 3, 2);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 3, 3);
            iZombiePlacePlants(SeedType.SEED_CACTUS, 1, 1);
            iZombiePlacePlants(SeedType.SEED_CACTUS, 1, 4);
            iZombiePlacePlants(SeedType.SEED_MAGNETSHROOM, 1, -1);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 5, -1);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 8, -1);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 2, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6:
            iZombiePlacePlantInSquare(SeedType.SEED_GARLIC, 4, 1);
            iZombiePlacePlantInSquare(SeedType.SEED_GARLIC, 4, 3);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 3, 1);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 3, 3);
            iZombiePlacePlants(SeedType.SEED_TORCHWOOD, 2, -1);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 2, -1);
            iZombiePlacePlants(SeedType.SEED_SPIKEWEED, 3, -1);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 1, -1);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 5, -1);
            iZombiePlacePlants(SeedType.SEED_SQUASH, 2, -1);
            iZombiePlacePlants(SeedType.SEED_KERNELPULT, 2, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7:
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 4, 2);
            iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 4, 4);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 6, -1);
            iZombiePlacePlants(SeedType.SEED_POTATOMINE, 9, -1);
            iZombiePlacePlants(SeedType.SEED_CHOMPER, 8, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8:
            iZombiePlacePlants(SeedType.SEED_WALLNUT, 3, -1);
            iZombiePlacePlants(SeedType.SEED_MAGNETSHROOM, 2, -1);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 8, -1);
            iZombiePlacePlants(SeedType.SEED_SQUASH, 2, -1);
            iZombiePlacePlants(SeedType.SEED_POTATOMINE, 2, -1);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 8, -1);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9:
            iZombiePlacePlantInSquare(SeedType.SEED_TALLNUT, 5, 1);
            iZombiePlacePlantInSquare(SeedType.SEED_TORCHWOOD, 5, 3);
            iZombiePlacePlants(SeedType.SEED_POTATOMINE, 4, 0);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 2, 0);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 2, 1);
            iZombiePlacePlants(SeedType.SEED_THREEPEATER, 1, 1);
            iZombiePlacePlants(SeedType.SEED_SNOWPEA, 1, 1);
            iZombiePlacePlants(SeedType.SEED_SPLITPEA, 1, 1);
            iZombiePlacePlants(SeedType.SEED_CHOMPER, 3, 2);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 2, 2);
            iZombiePlacePlants(SeedType.SEED_SQUASH, 1, 2);
            iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 3, 3);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 2, 3);
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 1, 4);
            iZombiePlacePlants(SeedType.SEED_FUMESHROOM, 1, 4);
            iZombiePlacePlants(SeedType.SEED_SCAREDYSHROOM, 1, 4);
            iZombiePlacePlants(SeedType.SEED_STARFRUIT, 1, 4);
            iZombiePlacePlants(SeedType.SEED_SPLITPEA, 1, 4);
            iZombiePlacePlants(SeedType.SEED_MAGNETSHROOM, 1, 4);
            break;
        case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS: {
            if (c.mSurvivalStage == 10) {
                ReportAchievement.giveAchievement(c.mApp, AchievementId.BetterOffDead, true);
            }

            int aFormationHit = PvzpCommon.randRangeInt(0, 4);

            int aPuffshroomCount = PvzpCommon.randRangeInt(
                    Math.max(2, Math.min(4, 2 + c.mSurvivalStage / 3)),
                    Math.max(2, Math.min(6, 3 + c.mSurvivalStage / 2)));
            if (c.mSurvivalStage == 0) {
                aPuffshroomCount = 0;
            } else if (c.mSurvivalStage == 1) {
                aPuffshroomCount = 1;
            } else if (c.mSurvivalStage >= 10) {
                aPuffshroomCount = PvzpCommon.randRangeInt(
                        Math.max(2, Math.min(5, 2 + c.mSurvivalStage / 3)),
                        Math.max(2, Math.min(7, 3 + c.mSurvivalStage / 2)));
            }
            int aSunflowerCount = 8 - aPuffshroomCount;
            iZombiePlacePlants(SeedType.SEED_SUNFLOWER, aSunflowerCount, -1);
            iZombiePlacePlants(SeedType.SEED_PUFFSHROOM, aPuffshroomCount, -1);

            if (aFormationHit == 0 && c.mSurvivalStage >= 1) {
                switch (PvzpCommon.randRangeInt(0, 4)) {
                case 0:  // Damage formation
                    iZombiePlacePlants(SeedType.SEED_SNOWPEA, 9, -1);
                    iZombiePlacePlants(SeedType.SEED_SPLITPEA, 4, -1);
                    iZombiePlacePlants(SeedType.SEED_REPEATER, 4, -1);
                    break;
                case 1:  // Explosive formation
                    iZombiePlacePlants(SeedType.SEED_POTATOMINE, 9, -1);
                    iZombiePlacePlants(SeedType.SEED_CHOMPER, 8, -1);
                    break;
                case 2:  // Tilted formation
                    iZombiePlacePlants(SeedType.SEED_SPIKEWEED, 9, -1);
                    iZombiePlacePlants(SeedType.SEED_STARFRUIT, 8, -1);
                    break;
                case 3:  // Piercing formation
                    iZombiePlacePlants(SeedType.SEED_FUMESHROOM, 9, -1);
                    iZombiePlacePlants(SeedType.SEED_MAGNETSHROOM, 8, -1);
                    break;
                default:  // Recovery formation
                    iZombiePlacePlants(SeedType.SEED_SCAREDYSHROOM, 12, -1);
                    iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 5, -1);
                }
            } else {
                switch (PvzpCommon.randRangeInt(0, 5)) {
                case 0:
                case 1:
                case 2:  // Mixed formation
                    iZombiePlacePlants(SeedType.SEED_WALLNUT, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_TORCHWOOD, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_POTATOMINE, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_CHOMPER, 2, -1);
                    iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_SPLITPEA, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_KERNELPULT, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_THREEPEATER, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_SNOWPEA, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_SQUASH, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_FUMESHROOM, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_UMBRELLA, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_STARFRUIT, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_MAGNETSHROOM, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_SPIKEWEED, 2, -1);
                    break;
                case 3:
                case 4:  // Control formation
                    iZombiePlacePlants(SeedType.SEED_TORCHWOOD, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_SPLITPEA, 3, -1);
                    iZombiePlacePlants(SeedType.SEED_REPEATER, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_KERNELPULT, 3, -1);
                    iZombiePlacePlants(SeedType.SEED_THREEPEATER, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_SNOWPEA, 3, -1);
                    iZombiePlacePlants(SeedType.SEED_UMBRELLA, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_MAGNETSHROOM, 1, -1);
                    iZombiePlacePlants(SeedType.SEED_SPIKEWEED, 3, -1);
                    break;
                default:  // Instant-kill formation
                    iZombiePlacePlants(SeedType.SEED_POTATOMINE, 4, -1);
                    iZombiePlacePlants(SeedType.SEED_CHOMPER, 3, -1);
                    iZombiePlacePlants(SeedType.SEED_SQUASH, 3, -1);
                    iZombiePlacePlants(SeedType.SEED_FUMESHROOM, 4, -1);
                    iZombiePlacePlants(SeedType.SEED_SPIKEWEED, 3, -1);
                    break;
                }
            }
            break;
        }
        default:
            // Challenge.cpp:4397: PVZP_ASSERT(false); the callers gate this on isIZombieLevel(),
            // which is exactly GAMEMODE_PUZZLE_I_ZOMBIE_1..GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS.
            throw new IllegalStateException("Challenge.IZombieInitLevel: unexpected game mode " + c.mApp.mGameMode);
        }

        c.mBoard.mBonusLawnMowersRemaining = 0;
    }

    /** Ported from src/Lawn/Challenge.cpp:4403-4406 ({@code Challenge::IZombieStart}). */
    public void iZombieStart() {
        c.mBoard.displayAdvice("[ADVICE_I_ZOMBIE_EAT_ALL_BRAINS]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                AdviceType.ADVICE_I_ZOMBIE_EAT_ALL_BRAINS);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4408-4469 ({@code Challenge::IZombieUpdate}).
     *
     * <p>Keeps the pre-placed defenders idle (re-randomising the speed of any zombie that has not
     * been shot for a while), then hands the level to the zombies once every zombie is gone, the sun
     * stock is under 50, nothing is left to drop and nothing is mid-animation.
     */
    public void iZombieUpdate() {
        int aSunMoney = c.mBoard.mSunMoney + c.mBoard.countSunBeingCollected();

        for (Zombie aZombie : c.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying() && aZombie.mZombiePhase != ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                    && !aZombie.mIsEating && aZombie.mJustGotShotCounter < -500) {
                aZombie.pickRandomSpeed();
            }
        }

        boolean anActive = false;
        for (Plant aPlant : c.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            int aState = aPlant.mState;
            if (aState == PlantState.STATE_SQUASH_FALLING || aState == PlantState.STATE_SQUASH_DONE_FALLING
                    || aState == PlantState.STATE_CHOMPER_BITING
                    || aState == PlantState.STATE_CHOMPER_BITING_GOT_ONE) {
                anActive = true;
            }
        }
        for (PvzpParticleSystem aParticle : c.mBoard.mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            if (aParticle.mDead) {
                continue;
            }
            if (aParticle.mEffectType == ParticleEffect.PARTICLE_POTATO_MINE) {
                anActive = true;
            }
        }

        boolean aHasAvailableSun = false;
        for (Coin aCoinForSun : c.mBoard.mCoins) {
            if (aCoinForSun.mDead) {
                continue;
            }
            if (aCoinForSun.isSun() && !aCoinForSun.mIsBeingCollected) {
                aHasAvailableSun = true;
                break;
            }
        }

        if (c.mBoard.mZombies.mSize == 0 && aSunMoney < 50 && !c.mBoard.hasLevelAwardDropped() && !anActive
                && !aHasAvailableSun) {
            for (Coin aCoin : c.mBoard.mCoins) {
                if (aCoin.mDead) {
                    continue;
                }
                if (aCoin.isMoney()) {
                    aCoin.die();
                }
            }

            c.mBoard.zombiesWon(null);
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4493-4503 ({@code Challenge::IZombieSetPlantFilterEffect}).
     *
     * <p>Applies the filter effect to every plant reanimation that currently exists.
     */
    public void iZombieSetPlantFilterEffect(Plant thePlant, int theFilterEffect) {
        Reanimation aBodyReanim = c.mApp.reanimationTryToGet(thePlant.mBodyReanimID);
        Reanimation aHeadReanim = c.mApp.reanimationTryToGet(thePlant.mHeadReanimID);
        Reanimation aHeadReanim2 = c.mApp.reanimationTryToGet(thePlant.mHeadReanimID2);
        Reanimation aHeadReanim3 = c.mApp.reanimationTryToGet(thePlant.mHeadReanimID3);
        if (aBodyReanim != null) {
            aBodyReanim.mFilterEffect = theFilterEffect;
        }
        if (aHeadReanim != null) {
            aHeadReanim.mFilterEffect = theFilterEffect;
        }
        if (aHeadReanim2 != null) {
            aHeadReanim2.mFilterEffect = theFilterEffect;
        }
        if (aHeadReanim3 != null) {
            aHeadReanim3.mFilterEffect = theFilterEffect;
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4505-4539 ({@code Challenge::IZombieDrawPlant}).
     *
     * <p>Draws the white-filtered plant four times with a decreasing offset to fake the soft "frozen
     * defender" outline of the I,Zombie pre-placed plants.
     */
    public void iZombieDrawPlant(Graphics g, Plant thePlant) {
        Reanimation aReanim = c.mApp.reanimationTryToGet(thePlant.mBodyReanimID);
        if (aReanim != null) {
            iZombieSetPlantFilterEffect(thePlant, FilterEffect.FILTER_EFFECT_WHITE);
            g.setColorizeImages(true);

            float aOffsetX = g.mTransX;
            float aOffsetY = g.mTransY;
            g.mTransX = aOffsetX + 4;
            g.mTransY = aOffsetY + 4;
            g.setColor(new Color(122, 86, 58));
            aReanim.drawRenderGroup(g, 0);

            g.mTransX = aOffsetX + 2;
            g.mTransY = aOffsetY + 2;
            g.setColor(new Color(171, 135, 107));
            aReanim.drawRenderGroup(g, 0);

            g.mTransX = aOffsetX - 2;
            g.mTransY = aOffsetY - 2;
            g.setColor(new Color(171, 135, 107));
            aReanim.drawRenderGroup(g, 0);

            g.mTransX = aOffsetX;
            g.mTransY = aOffsetY;
            g.setColor(new Color(255, 201, 160));
            iZombieSetPlantFilterEffect(thePlant, FilterEffect.FILTER_EFFECT_NONE);
            aReanim.drawRenderGroup(g, 0);

            iZombieSetPlantFilterEffect(thePlant, FilterEffect.FILTER_EFFECT_NONE);
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
            g.setColorizeImages(false);
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4541-4561 ({@code Challenge::IZombieGetBrainTarget}).
     *
     * <p>Returns the zombie's brain grid item, or {@code null} when the zombie cannot reach it
     * (bungee, walking backwards, not past the red line, or the brain is already squished).
     */
    public GridItem iZombieGetBrainTarget(Zombie theZombie) {
        if (theZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE || theZombie.isWalkingBackwards()) {
            return null;
        }

        Rect aZombieRect = theZombie.getZombieAttackRect();
        if (theZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT) {
            aZombieRect = new Rect(50 + theZombie.mX, 0, 20, 115);
        }
        if (theZombie.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            aZombieRect.mX += 25;
        }

        if (aZombieRect.mX > 20) {
            return null;
        }

        GridItem aBrain = c.mBoard.getGridItemAt(GridItemType.GRIDITEM_IZOMBIE_BRAIN, 0, theZombie.mRow);
        return (aBrain != null && aBrain.mGridItemState != GridItemState.GRIDITEM_STATE_BRAIN_SQUISHED) ? aBrain
                : null;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4563-4584 ({@code Challenge::IZombieScoreBrain}).
     *
     * <p>Bumps the score, updates the progress meter and either finishes the stage (endless) or
     * drops the level award; a loot piece falls from the brain unless the stage is now being awarded.
     */
    public void iZombieScoreBrain(GridItem theBrain) {
        c.mChallengeScore++;
        c.mBoard.mProgressMeterWidth = PvzpCommon.pvzpAnimateCurve(0, Challenge.I_ZOMBIE_WINNING_SCORE,
                c.mChallengeScore, 0, Board.PROGRESS_METER_COUNTER, PvzpCurves.CURVE_LINEAR);

        if (c.mChallengeScore == Challenge.I_ZOMBIE_WINNING_SCORE) {
            if (LawnApp.isEndlessIZombie(c.mApp.mGameMode)) {
                c.puzzlePhaseComplete(0, theBrain.mGridY);
            } else {
                c.spawnLevelAward(0, theBrain.mGridY);
            }
        }

        if (c.mChallengeScore != Challenge.I_ZOMBIE_WINNING_SCORE || c.puzzleIsAwardStage() == 0) {
            c.mBoard.dropLootPiece((int) (theBrain.mPosX + 40), (int) (theBrain.mPosY - 50), 12);
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4586-4601 ({@code Challenge::IZombieEatBrain}).
     *
     * <p>Upstream returns {@code int} 1/0 for "ate the brain"; the port returns {@code boolean} because
     * the only caller (Zombie.java:2460) uses it as a condition, matching the sibling
     * {@code Zombie.isZombotany} / {@code Board.isFlagWave} normalisation.
     */
    public boolean iZombieEatBrain(Zombie theZombie) {
        GridItem aBrain = iZombieGetBrainTarget(theZombie);
        if (aBrain == null) {
            return false;
        }

        theZombie.startEating();
        aBrain.mGridItemCounter--;
        if (aBrain.mGridItemCounter <= 0) {
            c.mApp.playSample(SoundType.SOUND_GULP);
            aBrain.gridItemDie();
            iZombieScoreBrain(aBrain);
        }
        return true;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4603-4613 ({@code Challenge::IZombiePlantDropRemainingSun}).
     *
     * <p>A sunflower that gets eaten coughs up the sun it still had left, one sun coin per 40 health.
     */
    public void iZombiePlantDropRemainingSun(Plant thePlant) {
        if (thePlant.mSeedType == SeedType.SEED_SUNFLOWER) {
            int aSunCount = thePlant.mPlantHealth / 40 + 1;
            for (int i = 0; i < aSunCount; i++) {
                c.mBoard.addCoin(thePlant.mX + 5 * i, thePlant.mY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4615-4622 ({@code Challenge::IZombieSquishBrain}).
     *
     * <p>Called when a Gargantuar (or an I,Zombie smash) crushes an already-eaten-to-zero brain.
     */
    public void iZombieSquishBrain(GridItem theBrain) {
        theBrain.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, theBrain.mGridY, 0);
        theBrain.mGridItemState = GridItemState.GRIDITEM_STATE_BRAIN_SQUISHED;
        theBrain.mGridItemCounter = 500;
        theBrain.mApp.playFoley(FoleyType.FOLEY_SQUISH);
        iZombieScoreBrain(theBrain);
    }
}
