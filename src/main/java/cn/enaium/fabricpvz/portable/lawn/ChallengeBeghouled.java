package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Challenge.cpp:324-889, 1133-1376, 1854-1886, 2085-2118, 3235-3305
 * ({@code Challenge::Beghouled*}, {@code Challenge::UpdateBeghouledPlant}, {@code Challenge::UpdateBeghouled},
 * {@code Challenge::ZombieAtePlant}, {@code Challenge::DrawBeghouled}).
 */
public class ChallengeBeghouled {
    public final Challenge c;

    public ChallengeBeghouled(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:324-334 ({@code Challenge::BeghouledTwistValidMove}). */
    public int beghouledTwistValidMove(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        if (theGridY == -1 || theGridX > 6 || theGridY > 3) {
            return 0;
        }

        return theBoardState.mSeedType[theGridX][theGridY] != SeedType.SEED_NONE
                && theBoardState.mSeedType[theGridX + 1][theGridY] != SeedType.SEED_NONE
                && theBoardState.mSeedType[theGridX][theGridY + 1] != SeedType.SEED_NONE
                && theBoardState.mSeedType[theGridX + 1][theGridY + 1] != SeedType.SEED_NONE ? 1 : 0;
    }

    /** Ported from src/Lawn/Challenge.cpp:336-359 ({@code Challenge::BeghouledTwistMoveCausesMatch}). */
    public int beghouledTwistMoveCausesMatch(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        if (beghouledTwistValidMove(theGridX, theGridY, theBoardState) == 0) {
            return 0;
        }

        int aSeed1 = theBoardState.mSeedType[theGridX][theGridY];
        int aSeed2 = theBoardState.mSeedType[theGridX + 1][theGridY];
        int aSeed3 = theBoardState.mSeedType[theGridX][theGridY + 1];
        int aSeed4 = theBoardState.mSeedType[theGridX + 1][theGridY + 1];

        theBoardState.mSeedType[theGridX + 1][theGridY] = aSeed1;
        theBoardState.mSeedType[theGridX + 1][theGridY + 1] = aSeed2;
        theBoardState.mSeedType[theGridX][theGridY + 1] = aSeed4;
        theBoardState.mSeedType[theGridX][theGridY] = aSeed3;

        int aHasMatch = beghouledBoardHasMatch(theBoardState);

        theBoardState.mSeedType[theGridX][theGridY] = aSeed1;
        theBoardState.mSeedType[theGridX + 1][theGridY] = aSeed2;
        theBoardState.mSeedType[theGridX][theGridY + 1] = aSeed3;
        theBoardState.mSeedType[theGridX + 1][theGridY + 1] = aSeed4;

        return aHasMatch;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:361-372 ({@code Challenge::BeghouledTwistSquareFromMouse}).
     *
     * <p>Upstream's out-parameters {@code int& theGridX, int& theGridY} become the two-element
     * {@code theGridXY} array ({@code [0] = gridX}, {@code [1] = gridY}).
     */
    public int beghouledTwistSquareFromMouse(int theX, int theY, int[] theGridXY) {
        int aGridX = c.mBoard.pixelToGridX(theX - 40, theY - 40);
        int aGridY = c.mBoard.pixelToGridY(theX - 40, theY - 40);
        if (aGridX == -1 || aGridY == -1 || aGridX > 6 || aGridY > 3) {
            theGridXY[0] = -1;
            theGridXY[1] = -1;
            return 0;
        }

        theGridXY[0] = aGridX;
        theGridXY[1] = aGridY;
        return 1;
    }

    /** Ported from src/Lawn/Challenge.cpp:374-410 ({@code Challenge::BeghouledTwistMouseDown}). */
    public void beghouledTwistMouseDown(int x, int y) {
        if (c.mBoard.hasLevelAwardDropped()) {
            return;
        }

        BeghouledBoardState aBoardState = new BeghouledBoardState();
        c.loadBeghouledBoardState(aBoardState);

        int[] aGrid = new int[2];
        if (beghouledTwistSquareFromMouse(x, y, aGrid) != 0 && beghouledTwistValidMove(aGrid[0], aGrid[1], aBoardState) != 0) {
            int aGridX = aGrid[0];
            int aGridY = aGrid[1];

            Plant aPlant1 = c.mBoard.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ANY);
            Plant aPlant2 = c.mBoard.getTopPlantAt(aGridX + 1, aGridY, PlantPriority.TOPPLANT_ANY);
            Plant aPlant3 = c.mBoard.getTopPlantAt(aGridX, aGridY + 1, PlantPriority.TOPPLANT_ANY);
            Plant aPlant4 = c.mBoard.getTopPlantAt(aGridX + 1, aGridY + 1, PlantPriority.TOPPLANT_ANY);
            if (beghouledTwistMoveCausesMatch(aGridX, aGridY, aBoardState) == 0) {
                aPlant1.mX = c.mBoard.gridToPixelX(aPlant1.mPlantCol, aPlant1.mRow) + 20;
                aPlant2.mY = c.mBoard.gridToPixelY(aPlant2.mPlantCol, aPlant2.mRow) + 20;
                aPlant3.mY = c.mBoard.gridToPixelY(aPlant3.mPlantCol, aPlant3.mRow) - 20;
                aPlant4.mX = c.mBoard.gridToPixelX(aPlant4.mPlantCol, aPlant4.mRow) - 20;
                c.mApp.playFoley(FoleyType.FOLEY_FLOOP);
            } else {
                aPlant1.mPlantCol++;
                aPlant1.mRenderOrder = aPlant1.calcRenderOrder();
                aPlant2.mRow++;
                aPlant2.mRenderOrder = aPlant2.calcRenderOrder();
                aPlant3.mRow--;
                aPlant3.mRenderOrder = aPlant3.calcRenderOrder();
                aPlant4.mPlantCol--;
                aPlant4.mRenderOrder = aPlant4.calcRenderOrder();
                beghouledStartFalling(ChallengeState.STATECHALLENGE_BEGHOULED_MOVING);
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:412-417 ({@code Challenge::BeghouledStartFalling}). */
    public void beghouledStartFalling(int theState) {
        c.mChallengeState = theState;
        c.mChallengeStateCounter = 100;
        c.mBoard.clearAdvice(AdviceType.ADVICE_BEGHOULED_NO_MOVES);
    }

    /** Ported from src/Lawn/Challenge.cpp:419-440 ({@code Challenge::BeghouledIsValidMove}). */
    public int beghouledIsValidMove(int theFromX, int theFromY, int theToX, int theToY, BeghouledBoardState theBoardState) {
        if (theFromX < 0 || theFromX > Challenge.BEGHOULED_MAX_GRIDSIZEX || theToX < 0 || theToX > Challenge.BEGHOULED_MAX_GRIDSIZEX
                || theFromY < 0 || theFromY > Challenge.BEGHOULED_MAX_GRIDSIZEY || theToY < 0 || theToY > Challenge.BEGHOULED_MAX_GRIDSIZEY
                || c.mBeghouledEated[theFromX][theFromY] != 0 || c.mBeghouledEated[theToX][theToY] != 0) {
            return 0;
        }

        int aSeedFrom = theBoardState.mSeedType[theFromX][theFromY];
        int aSeedTo = theBoardState.mSeedType[theToX][theToY];
        if (aSeedFrom == SeedType.SEED_NONE) {
            return 0;
        }

        theBoardState.mSeedType[theFromX][theFromY] = aSeedTo;
        theBoardState.mSeedType[theToX][theToY] = aSeedFrom;

        int aValid = beghouledBoardHasMatch(theBoardState);

        theBoardState.mSeedType[theFromX][theFromY] = aSeedFrom;
        theBoardState.mSeedType[theToX][theToY] = aSeedTo;

        return aValid;
    }

    /** Ported from src/Lawn/Challenge.cpp:442-501 ({@code Challenge::BeghouledDragUpdate}). */
    public void beghouledDragUpdate(int x, int y) {
        int aDeltaX = x - c.mBeghouledMouseDownX;
        int aDeltaY = y - c.mBeghouledMouseDownY;
        if (Math.abs(aDeltaX) >= 10 || Math.abs(aDeltaY) >= 10) {
            c.mBoard.clearAdvice(AdviceType.ADVICE_BEGHOULED_DRAG_TO_MATCH_3);
            c.mBeghouledMouseCapture = 0;

            int aGridXFrom = c.mBoard.pixelToGridX(c.mBeghouledMouseDownX, c.mBeghouledMouseDownY);
            int aGridYFrom = c.mBoard.pixelToGridY(c.mBeghouledMouseDownX, c.mBeghouledMouseDownY);
            int aGridXTo;
            int aGridYTo;
            if (Math.abs(aDeltaX) > Math.abs(aDeltaY)) {
                aGridXTo = aGridXFrom + (aDeltaX > 0 ? 1 : -1);
                aGridYTo = aGridYFrom;
            } else {
                aGridXTo = aGridXFrom;
                aGridYTo = aGridYFrom + (aDeltaY > 0 ? 1 : -1);
            }

            BeghouledBoardState aBoardState = new BeghouledBoardState();
            c.loadBeghouledBoardState(aBoardState);
            Plant aPlantFrom = c.mBoard.getTopPlantAt(aGridXFrom, aGridYFrom, PlantPriority.TOPPLANT_ANY);
            if (beghouledIsValidMove(aGridXFrom, aGridYFrom, aGridXTo, aGridYTo, aBoardState) == 0) {
                if (aPlantFrom != null) {
                    if (aGridXTo > aGridXFrom) {
                        aPlantFrom.mX += 30;
                    } else if (aGridXTo < aGridXFrom) {
                        aPlantFrom.mX -= 30;
                    } else if (aGridYTo > aGridYFrom) {
                        aPlantFrom.mY += 30;
                    } else if (aGridYTo < aGridYFrom) {
                        aPlantFrom.mY -= 30;
                    }

                    c.mApp.playFoley(FoleyType.FOLEY_FLOOP);
                }
            } else {
                Plant aPlantTo = c.mBoard.getTopPlantAt(aGridXTo, aGridYTo, PlantPriority.TOPPLANT_ANY);

                if (aPlantFrom != null) {
                    aPlantFrom.mPlantCol = aGridXTo;
                    aPlantFrom.mRow = aGridYTo;
                    aPlantFrom.mRenderOrder = aPlantFrom.calcRenderOrder();
                }

                if (aPlantTo != null) {
                    aPlantTo.mPlantCol = aGridXFrom;
                    aPlantTo.mRow = aGridYFrom;
                    aPlantTo.mRenderOrder = aPlantTo.calcRenderOrder();
                }

                beghouledStartFalling(ChallengeState.STATECHALLENGE_BEGHOULED_MOVING);
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:503-509 ({@code Challenge::BeghouledGetPlantAt}). */
    public int beghouledGetPlantAt(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        if (theGridX < 0 || theGridX > Challenge.BEGHOULED_MAX_GRIDSIZEX || theGridY < 0 || theGridY > Challenge.BEGHOULED_MAX_GRIDSIZEY) {
            return SeedType.SEED_NONE;
        }

        return theBoardState.mSeedType[theGridX][theGridY];
    }

    /** Ported from src/Lawn/Challenge.cpp:511-524 ({@code Challenge::BeghouledRemoveHorizontalMatch}). */
    public void beghouledRemoveHorizontalMatch(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        int aSeedType = beghouledGetPlantAt(theGridX, theGridY, theBoardState);
        do {
            Plant aPlant = c.mBoard.getTopPlantAt(theGridX, theGridY, PlantPriority.TOPPLANT_ANY);
            if (aPlant != null) {
                aPlant.die();
            }
            theGridX++;
        } while (beghouledGetPlantAt(theGridX, theGridY, theBoardState) == aSeedType);
    }

    /** Ported from src/Lawn/Challenge.cpp:526-538 ({@code Challenge::BeghouledRemoveVerticalMatch}). */
    public void beghouledRemoveVerticalMatch(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        int aSeedType = beghouledGetPlantAt(theGridX, theGridY, theBoardState);
        do {
            Plant aPlant = c.mBoard.getTopPlantAt(theGridX, theGridY, PlantPriority.TOPPLANT_ANY);
            if (aPlant != null) {
                aPlant.die();
            }
            theGridY++;
        } while (beghouledGetPlantAt(theGridX, theGridY, theBoardState) == aSeedType);
    }

    /** Ported from src/Lawn/Challenge.cpp:540-558 ({@code Challenge::BeghouledFallIntoSquare}). */
    public void beghouledFallIntoSquare(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        if (c.mBeghouledEated[theGridX][theGridY] != 0) {
            return;
        }

        for (int aGridY = theGridY - 1; aGridY >= 0; aGridY--) {
            Plant aPlant = c.mBoard.getTopPlantAt(theGridX, aGridY, PlantPriority.TOPPLANT_ANY);
            if (aPlant != null) {
                aPlant.mRow = theGridY;
                aPlant.mRenderOrder = aPlant.calcRenderOrder();
                theBoardState.mSeedType[theGridX][theGridY] = aPlant.mSeedType;
                theBoardState.mSeedType[theGridX][aGridY] = SeedType.SEED_NONE;
                beghouledStartFalling(ChallengeState.STATECHALLENGE_BEGHOULED_FALLING);
                break;
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:560-572 ({@code Challenge::BeghouledMakePlantsFall}). */
    public void beghouledMakePlantsFall(BeghouledBoardState theBoardState) {
        for (int aGridY = Challenge.BEGHOULED_MAX_GRIDSIZEY - 1; aGridY >= 0; aGridY--) {
            for (int aGridX = 0; aGridX < Challenge.BEGHOULED_MAX_GRIDSIZEX; aGridX++) {
                if (beghouledGetPlantAt(aGridX, aGridY, theBoardState) == SeedType.SEED_NONE) {
                    beghouledFallIntoSquare(aGridX, aGridY, theBoardState);
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:574-582 ({@code Challenge::BeghouledUpdateCraters}).
     *
     * <p>Upstream asserts packet 4 is {@code SEED_BEGHOULED_BUTTON_CRATER}; like the other ported
     * asserts this is a precondition that the callers ({@link Challenge#beghouledScore},
     * {@link #zombieAtePlant}) already guarantee, so it is not re-checked.
     */
    public void beghouledUpdateCraters() {
        if (c.mBoard.mSeedBank.mNumPackets != 5) {
            return;
        }

        SeedPacket aSeedPacket = c.mBoard.mSeedBank.mSeedPackets[4];
        aSeedPacket.setActivate(beghouledCanClearCrater() != 0);
    }

    /** Ported from src/Lawn/Challenge.cpp:584-604 ({@code Challenge::BeghouledClearCrater}). */
    public void beghouledClearCrater(int theCount) {
        c.mBoard.clearAdvice(AdviceType.ADVICE_BEGHOULED_USE_CRATER_1);
        c.mBoard.clearAdvice(AdviceType.ADVICE_BEGHOULED_USE_CRATER_2);

        for (int aGridX = 0; aGridX < Board.MAX_GRID_SIZE_X; aGridX++) {
            for (int aGridY = 0; aGridY < Challenge.BEGHOULED_MAX_GRIDSIZEY; aGridY++) {
                if (c.mBeghouledEated[aGridX][aGridY] != 0) {
                    c.mBeghouledEated[aGridX][aGridY] = 0;
                    if (--theCount == 0) {
                        beghouledUpdateCraters();
                        return;
                    }
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:606-673 ({@code Challenge::BeghouledScore}). */
    public void beghouledScore(int theGridX, int theGridY, int theNumPlants, int theIsHorizontal) {
        c.mApp.playFoley(FoleyType.FOLEY_ART_CHALLENGE);

        float aPosX = c.mBoard.gridToPixelX(theGridX, theGridY);
        float aPosY = c.mBoard.gridToPixelY(theGridX, theGridY);
        if (theIsHorizontal != 0) {
            aPosX += theNumPlants == 3 ? 80.0f : theNumPlants == 4 ? 120.0f : 160.0f;
        } else {
            aPosY += theNumPlants == 3 ? 80.0f : theNumPlants == 4 ? 120.0f : 160.0f;
        }

        c.mChallengeScore++;
        if (c.mBoard.mSeedBank.mNumPackets == 0) {
            c.mBoard.mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_REPEATER, SeedType.SEED_NONE);
            c.mBoard.mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_FUMESHROOM, SeedType.SEED_NONE);
            c.mBoard.mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_TALLNUT, SeedType.SEED_NONE);
            c.mBoard.mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE, SeedType.SEED_NONE);
            c.mBoard.mSeedBank.mNumPackets = 4;

            c.mBoard.displayAdvice("[ADVICE_BEGHOULED_SAVE_SUN]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_BEGHOULED_SAVE_SUN);

            if (beghouledCanClearCrater() != 0) {
                c.mBoard.mSeedBank.mSeedPackets[4].setPacketType(SeedType.SEED_BEGHOULED_BUTTON_CRATER, SeedType.SEED_NONE);
                c.mBoard.mSeedBank.mNumPackets = 5;
            }
        } else {
            if (c.mBoard.mAdvice.mDuration <= 0) {
                String aMsg = PvzpCommon.pvzpReplaceNumberString("[ADVICE_BEGHOULED_MATCH_3]", "{SCORE}", Challenge.BEGHOULED_WINNING_SCORE);
                c.mBoard.displayAdvice(aMsg, MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_BEGHOULED_MATCH_3);
            }
            if (c.mChallengeScore >= Challenge.BEGHOULED_WINNING_SCORE - 5) {
                c.mBoard.displayAdvice("[ADVICE_BEGHOULED_ALMOST_THERE]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_ALMOST_THERE);
            }
        }

        if (c.mChallengeScore >= Challenge.BEGHOULED_WINNING_SCORE) {
            c.mChallengeScore = Challenge.BEGHOULED_WINNING_SCORE;
            c.spawnLevelAward(theGridX, theGridY);
            c.mBoard.clearAdvice(AdviceType.ADVICE_NONE);
        } else {
            int aNumSuns = theNumPlants - 2 + c.mBeghouledMatchesThisMove;
            if (theNumPlants >= 5) {
                aNumSuns += 2;
            }
            aNumSuns = Math.max(1, Math.min(5, aNumSuns));

            for (int i = 0; i < aNumSuns; i++) {
                c.mBoard.addCoin((int) (aPosX + 20 * i - 10), (int) aPosY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
            }
        }

        c.mBeghouledMatchesThisMove++;
    }

    /** Ported from src/Lawn/Challenge.cpp:675-696 ({@code Challenge::BeghouledRemoveMatches}). */
    public void beghouledRemoveMatches(BeghouledBoardState theBoardState) {
        for (int aGridY = 0; aGridY < Challenge.BEGHOULED_MAX_GRIDSIZEY; aGridY++) {
            for (int aGridX = 0; aGridX < Challenge.BEGHOULED_MAX_GRIDSIZEX; aGridX++) {
                int aHorMatchLength = beghouledHorizontalMatchLength(aGridX, aGridY, theBoardState);
                if (aHorMatchLength >= 3) {
                    beghouledRemoveHorizontalMatch(aGridX, aGridY, theBoardState);
                    beghouledScore(aGridX, aGridY, aHorMatchLength, 1);
                }

                int aVerMatchLength = beghouledVerticalMatchLength(aGridX, aGridY, theBoardState);
                if (aVerMatchLength >= 3) {
                    beghouledRemoveVerticalMatch(aGridX, aGridY, theBoardState);
                    beghouledScore(aGridX, aGridY, aVerMatchLength, 0);
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:698-708 ({@code Challenge::BeghouledHorizontalMatchLength}). */
    public int beghouledHorizontalMatchLength(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        int aSeedType = beghouledGetPlantAt(theGridX, theGridY, theBoardState);
        if (aSeedType == SeedType.SEED_NONE || beghouledGetPlantAt(theGridX - 1, theGridY, theBoardState) == aSeedType) {
            return 0;
        }

        int aLength = 1;
        while (beghouledGetPlantAt(theGridX + aLength, theGridY, theBoardState) == aSeedType) {
            aLength++;
        }
        return aLength;
    }

    /** Ported from src/Lawn/Challenge.cpp:710-720 ({@code Challenge::BeghouledVerticalMatchLength}). */
    public int beghouledVerticalMatchLength(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        int aSeedType = beghouledGetPlantAt(theGridX, theGridY, theBoardState);
        if (aSeedType == SeedType.SEED_NONE || beghouledGetPlantAt(theGridX, theGridY - 1, theBoardState) == aSeedType) {
            return 0;
        }

        int aLength = 1;
        while (beghouledGetPlantAt(theGridX, theGridY + aLength, theBoardState) == aSeedType) {
            aLength++;
        }
        return aLength;
    }

    /** Ported from src/Lawn/Challenge.cpp:722-734 ({@code Challenge::BeghouledBoardHasMatch}). */
    public int beghouledBoardHasMatch(BeghouledBoardState theBoardState) {
        for (int aCol = 0; aCol < 8; aCol++) {
            for (int aRow = 0; aRow < 5; aRow++) {
                if (beghouledHorizontalMatchLength(aCol, aRow, theBoardState) >= 3
                        || beghouledVerticalMatchLength(aCol, aRow, theBoardState) >= 3) {
                    return 1;
                }
            }
        }
        return 0;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:736-781 ({@code Challenge::BeghouledPickSeed}).
     *
     * <p>Upstream asserts the target square is empty; like the other ported asserts this precondition
     * is guaranteed by {@link #beghouledFillHoles}, so it is not re-checked. Upstream's
     * {@code PvzpPickFromArray} on a non-positive count returns {@code T{}} (= {@code SEED_NONE}),
     * which the port's {@code null} maps back to here.
     */
    public int beghouledPickSeed(int theGridX, int theGridY, BeghouledBoardState theBoardState, int theAllowMatches) {
        int aCount = 0;
        Integer[] aPickArray = new Integer[6];

        for (int i = 0; i < 6; i++) {
            int aSeedType;
            switch (i) {
                case 0:
                    aSeedType = SeedType.SEED_PUFFSHROOM;
                    break;
                case 1:
                    aSeedType = SeedType.SEED_STARFRUIT;
                    break;
                case 2:
                    aSeedType = SeedType.SEED_MAGNETSHROOM;
                    break;
                case 3:
                    aSeedType = SeedType.SEED_SNOWPEA;
                    break;
                case 4:
                    aSeedType = SeedType.SEED_WALLNUT;
                    break;
                case 5:
                    aSeedType = SeedType.SEED_PEASHOOTER;
                    break;
                default:
                    throw new IllegalStateException("unreachable"); // upstream PVZP_ASSERT(false)
            }

            if (c.mBeghouledPurcasedUpgrade[BeghouledUpgrade.BEGHOULED_UPGRADE_REPEATER] != 0 && aSeedType == SeedType.SEED_PEASHOOTER) {
                aSeedType = SeedType.SEED_REPEATER;
            }
            if (c.mBeghouledPurcasedUpgrade[BeghouledUpgrade.BEGHOULED_UPGRADE_FUMESHROOM] != 0 && aSeedType == SeedType.SEED_PUFFSHROOM) {
                aSeedType = SeedType.SEED_FUMESHROOM;
            }
            if (c.mBeghouledPurcasedUpgrade[BeghouledUpgrade.BEGHOULED_UPGRADE_TALLNUT] != 0 && aSeedType == SeedType.SEED_WALLNUT) {
                aSeedType = SeedType.SEED_TALLNUT;
            }

            theBoardState.mSeedType[theGridX][theGridY] = aSeedType;

            if (theAllowMatches != 0 || beghouledBoardHasMatch(theBoardState) == 0) {
                aPickArray[aCount] = aSeedType;
                aCount++;
            }
        }

        theBoardState.mSeedType[theGridX][theGridY] = SeedType.SEED_NONE;
        Integer aPicked = PvzpCommon.pvzpPickFromArray(aPickArray, aCount);
        return aPicked == null ? SeedType.SEED_NONE : aPicked;
    }

    /** Ported from src/Lawn/Challenge.cpp:783-795 ({@code Challenge::BeghouledFillHoles}). */
    public void beghouledFillHoles(BeghouledBoardState theBoardState, int theAllowMatches) {
        for (int aCol = 0; aCol < Challenge.BEGHOULED_MAX_GRIDSIZEX; aCol++) {
            for (int aRow = 0; aRow < Challenge.BEGHOULED_MAX_GRIDSIZEY; aRow++) {
                if (theBoardState.mSeedType[aCol][aRow] == SeedType.SEED_NONE && c.mBeghouledEated[aCol][aRow] == 0) {
                    theBoardState.mSeedType[aCol][aRow] = beghouledPickSeed(aCol, aRow, theBoardState, theAllowMatches);
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:797-813 ({@code Challenge::BeghouledCreatePlants}). */
    public void beghouledCreatePlants(BeghouledBoardState theOldBoardState, BeghouledBoardState theNewBoardState) {
        for (int aCol = 0; aCol < Challenge.BEGHOULED_MAX_GRIDSIZEX; aCol++) {
            int aFallY = 80;
            for (int aRow = Challenge.BEGHOULED_MAX_GRIDSIZEY - 1; aRow >= 0; aRow--) {
                int aSeedType = theNewBoardState.mSeedType[aCol][aRow];
                if (theOldBoardState.mSeedType[aCol][aRow] == SeedType.SEED_NONE && aSeedType != SeedType.SEED_NONE) {
                    aFallY -= 100;
                    c.mBoard.newPlant(aCol, aRow, aSeedType, SeedType.SEED_NONE).mY = aFallY;
                    beghouledStartFalling(ChallengeState.STATECHALLENGE_BEGHOULED_FALLING);
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:815-830 ({@code Challenge::BeghouledMakeStartBoard}). */
    public void beghouledMakeStartBoard() {
        BeghouledBoardState aEmptyBoardState = new BeghouledBoardState();
        c.loadBeghouledBoardState(aEmptyBoardState);
        BeghouledBoardState aBoardState = new BeghouledBoardState();
        c.loadBeghouledBoardState(aBoardState);

        beghouledFillHoles(aBoardState, 0);

        if (beghouledBoardHasMatch(aBoardState) == 0) {
            beghouledCreatePlants(aEmptyBoardState, aBoardState);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:831-847 ({@code Challenge::BeghouledPopulateBoard}). */
    public void beghouledPopulateBoard() {
        BeghouledBoardState aEmptyBoardState = new BeghouledBoardState();
        BeghouledBoardState aBoardState = new BeghouledBoardState();
        c.loadBeghouledBoardState(aEmptyBoardState);
        int aAllowGeneratedCascades = beghouledBoardHasMatch(aEmptyBoardState);

        for (int i = 0; i < 2; i++) {
            c.loadBeghouledBoardState(aBoardState);
            beghouledFillHoles(aBoardState, aAllowGeneratedCascades);
            // Refill once if the fill leaves no possible moves
            if (beghouledCheckForPossibleMoves(aBoardState) != 0) {
                break;
            }
        }

        beghouledCreatePlants(aEmptyBoardState, aBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:849-879 ({@code Challenge::BeghouledCheckForPossibleMoves}). */
    public int beghouledCheckForPossibleMoves(BeghouledBoardState theBoardState) {
        int aGameMode = c.mApp.mGameMode;

        for (int aRow = 0; aRow < Challenge.BEGHOULED_MAX_GRIDSIZEY; aRow++) {
            for (int aCol = 0; aCol < Challenge.BEGHOULED_MAX_GRIDSIZEX; aCol++) {
                if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED) {
                    if (beghouledIsValidMove(aCol, aRow, aCol + 1, aRow, theBoardState) != 0
                            || beghouledIsValidMove(aCol, aRow, aCol, aRow + 1, theBoardState) != 0) {
                        return 1;
                    }
                } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
                    if (beghouledTwistMoveCausesMatch(aCol, aRow, theBoardState) != 0) {
                        return 1;
                    }
                } else {
                    return 0;
                }
            }
        }

        return 0;
    }

    /** Ported from src/Lawn/Challenge.cpp:881-889 ({@code Challenge::BeghouledDragStart}). */
    public void beghouledDragStart(int x, int y) {
        if (!c.mBoard.hasLevelAwardDropped()) {
            c.mBeghouledMouseCapture = 1;
            c.mBeghouledMouseDownX = x;
            c.mBeghouledMouseDownY = y;
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:1133-1173 ({@code Challenge::UpdateBeghouledPlant}). */
    public int updateBeghouledPlant(Plant thePlant) {
        int aMoving = 0;
        int aDiffX = c.mBoard.gridToPixelX(thePlant.mPlantCol, thePlant.mRow) - thePlant.mX;
        int aDiffY = c.mBoard.gridToPixelY(thePlant.mPlantCol, thePlant.mRow) - thePlant.mY;
        int aDelta = c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_MOVING ? 3
                : PvzpCommon.pvzpAnimateCurve(90, 30, c.mChallengeStateCounter, 1, 20, PvzpCurves.CURVE_EASE_IN);
        int aDeltaX = 0;
        int aDeltaY = 0;

        if (aDiffX > 0) {
            aDeltaX = Math.min(aDelta, aDiffX);
            thePlant.mX += aDeltaX;
            aMoving = 1;
        } else if (aDiffX < 0) {
            aDeltaX = -Math.min(aDelta, -aDiffX);
            thePlant.mX += aDeltaX;
            aMoving = 1;
        }
        if (aDiffY > 0) {
            aDeltaY = Math.min(aDelta, aDiffY);
            thePlant.mY += aDeltaY;
            aMoving = 1;
        } else if (aDiffY < 0) {
            aDeltaY = -Math.min(aDelta, -aDiffY);
            thePlant.mY += aDeltaY;
            aMoving = 1;
        }

        if (thePlant.mState == PlantState.STATE_MAGNETSHROOM_CHARGING || thePlant.mState == PlantState.STATE_MAGNETSHROOM_SUCKING) {
            thePlant.mMagnetItems[0].mPosX += aDeltaX;
            thePlant.mMagnetItems[0].mPosY += aDeltaY;
        }

        return aMoving;
    }

    /** Ported from src/Lawn/Challenge.cpp:1175-1191 ({@code Challenge::BeghouledFlashPlant}). */
    public void beghouledFlashPlant(int theFlashX, int theFlashY, int theFromX, int theFromY, int theToX, int theToY) {
        if (theFlashX == theFromX && theFlashY == theFromY) {
            theFlashX = theToX;
            theFlashY = theToY;
        } else if (theFlashX == theToX && theFlashY == theToY) {
            theFlashX = theFromX;
            theFlashY = theFromY;
        }

        Plant aFlashPlant = c.mBoard.getTopPlantAt(theFlashX, theFlashY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
        if (aFlashPlant != null && aFlashPlant.mBeghouledFlashCountdown == 0) {
            aFlashPlant.mBeghouledFlashCountdown = 300;
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:1193-1207 ({@code Challenge::BeghouledTwistFlashMatch}). */
    public int beghouledTwistFlashMatch(BeghouledBoardState theBoardState, int theGridX, int theGridY) {
        if (beghouledTwistMoveCausesMatch(theGridX, theGridY, theBoardState) == 0) {
            return 0;
        }

        for (int i = 0; i < 4; i++) {
            Plant aPlant = c.mBoard.getTopPlantAt(theGridX + (i % 2), theGridY + (i / 2), PlantPriority.TOPPLANT_ANY);
            if (aPlant != null && aPlant.mBeghouledFlashCountdown == 0) {
                aPlant.mBeghouledFlashCountdown = 300;
            }
        }
        return 1;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1209-1252 ({@code Challenge::BeghouledFlashFromBoardState}).
     *
     * <p>Upstream asserts the two squares are inside the Beghouled grid; like the other ported
     * asserts this precondition is guaranteed by {@link #beghouledFlashAMatch}, so it is not
     * re-checked.
     */
    public int beghouledFlashFromBoardState(BeghouledBoardState theBoardState, int theFromX, int theFromY, int theToX, int theToY) {
        if (c.mBeghouledEated[theFromX][theFromY] != 0 || c.mBeghouledEated[theToX][theToY] != 0) {
            return 0;
        }

        int aFromSeedType = theBoardState.mSeedType[theFromX][theFromY];
        int aToSeedType = theBoardState.mSeedType[theToX][theToY];
        theBoardState.mSeedType[theFromX][theFromY] = aToSeedType;
        theBoardState.mSeedType[theToX][theToY] = aFromSeedType;

        int aHasMatch = 0;
        for (int aRow = 0; aRow < Challenge.BEGHOULED_MAX_GRIDSIZEY; aRow++) {
            for (int aCol = 0; aCol < Challenge.BEGHOULED_MAX_GRIDSIZEX; aCol++) {
                if (beghouledHorizontalMatchLength(aCol, aRow, theBoardState) >= 3) {
                    for (int i = 0; i < 3; i++) {
                        beghouledFlashPlant(aCol + i, aRow, theFromX, theFromY, theToX, theToY);
                    }
                } else if (beghouledVerticalMatchLength(aCol, aRow, theBoardState) >= 3) {
                    for (int i = 0; i < 3; i++) {
                        beghouledFlashPlant(aCol, aRow + i, theFromX, theFromY, theToX, theToY);
                    }
                } else {
                    continue;
                }

                aHasMatch = 1;
                break;
            }
        }

        theBoardState.mSeedType[theFromX][theFromY] = aFromSeedType;
        theBoardState.mSeedType[theToX][theToY] = aToSeedType;
        return aHasMatch;
    }

    /** Ported from src/Lawn/Challenge.cpp:1254-1263 ({@code Challenge::BeghouledCancelMatchFlashing}). */
    public void beghouledCancelMatchFlashing() {
        for (Plant aPlant : c.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mEatenFlashCountdown >= 25) {
                aPlant.mEatenFlashCountdown = 25;
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:1265-1294 ({@code Challenge::BeghouledFlashAMatch}). */
    public void beghouledFlashAMatch() {
        BeghouledBoardState aBoardState = new BeghouledBoardState();
        c.loadBeghouledBoardState(aBoardState);

        int aGameMode = c.mApp.mGameMode;
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED) {
            for (int aRow = 0; aRow <= Challenge.BEGHOULED_MAX_GRIDSIZEY - 1; aRow++) {
                for (int aCol = 0; aCol <= Challenge.BEGHOULED_MAX_GRIDSIZEX - 1; aCol++) {
                    if ((aCol < Challenge.BEGHOULED_MAX_GRIDSIZEX - 1 && beghouledFlashFromBoardState(aBoardState, aCol, aRow, aCol + 1, aRow) != 0)
                            || (aRow < Challenge.BEGHOULED_MAX_GRIDSIZEY - 1 && beghouledFlashFromBoardState(aBoardState, aCol, aRow, aCol, aRow + 1) != 0)) {
                        return;
                    }
                }
            }
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            for (int aRow = 0; aRow <= Challenge.BEGHOULED_MAX_GRIDSIZEY - 1; aRow++) {
                for (int aCol = 0; aCol <= Challenge.BEGHOULED_MAX_GRIDSIZEX - 1; aCol++) {
                    if (beghouledTwistFlashMatch(aBoardState, aCol, aRow) != 0) {
                        return;
                    }
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:1296-1376 ({@code Challenge::UpdateBeghouled}). */
    public void updateBeghouled() {
        c.mBoard.mProgressMeterWidth = PvzpCommon.pvzpAnimateCurve(0, Challenge.BEGHOULED_WINNING_SCORE, c.mChallengeScore,
                0, Board.PROGRESS_METER_COUNTER, PvzpCurves.CURVE_LINEAR);

        int aMovingPlant = 0;
        for (Plant aPlant : c.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (updateBeghouledPlant(aPlant) != 0) {
                aMovingPlant = 1;
            }
        }

        if (c.mBoard.mSeedBank.mNumPackets > 4 && c.mBoard.mAdvice.mDuration <= 0
                && !c.mBoard.mHelpDisplayed[AdviceType.ADVICE_BEGHOULED_USE_CRATER_2]) {
            int aCost = c.mBoard.getCurrentPlantCost(SeedType.SEED_BEGHOULED_BUTTON_CRATER, SeedType.SEED_NONE);
            if (c.mBoard.canTakeSunMoney(aCost) && beghouledCanClearCrater() != 0 && !c.mBoard.hasLevelAwardDropped()) {
                c.mBoard.displayAdvice("[ADVICE_BEGHOULED_USE_CRATER_2]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_BEGHOULED_USE_CRATER_2);
            }
        }

        if (c.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST && c.mChallengeState == ChallengeState.STATECHALLENGE_NORMAL) {
            int[] aGrid = new int[2];
            if (beghouledTwistSquareFromMouse(c.mApp.mWidgetManager.mLastMouseX, c.mApp.mWidgetManager.mLastMouseY, aGrid) != 0) {
                BeghouledBoardState aBoardState = new BeghouledBoardState();
                c.loadBeghouledBoardState(aBoardState);
                if (beghouledTwistValidMove(aGrid[0], aGrid[1], aBoardState) == 0) {
                    aGrid[0] = -1;
                    aGrid[1] = -1;
                }
            }
            c.mChallengeGridX = aGrid[0];
            c.mChallengeGridY = aGrid[1];
        } else {
            c.mChallengeGridX = -1;
            c.mChallengeGridY = -1;
        }

        if (aMovingPlant == 0 && (c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_FALLING
                || c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_MOVING)) {
            c.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
            c.mChallengeStateCounter = 1500;

            BeghouledBoardState aBoardState = new BeghouledBoardState();
            c.loadBeghouledBoardState(aBoardState);
            beghouledRemoveMatches(aBoardState);
            c.loadBeghouledBoardState(aBoardState);
            beghouledMakePlantsFall(aBoardState);
            beghouledPopulateBoard();

            if (c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_FALLING) {
                return;
            }

            c.mChallengeStateCounter = 1500;
            c.mBeghouledMatchesThisMove = 0;
            beghouledCheckStuckState();
        }

        if (c.mChallengeStateCounter != 0) {
            c.mChallengeStateCounter--;
            if (c.mChallengeStateCounter <= 0 && !c.mBoard.hasLevelAwardDropped()) {
                if (c.mChallengeState == ChallengeState.STATECHALLENGE_NORMAL) {
                    beghouledFlashAMatch();
                    c.mChallengeStateCounter = 1500;
                } else if (c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_NO_MATCHES) {
                    c.mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2, GameConstants.BOARD_HEIGHT / 2,
                            RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_SCREEN_FLASH);
                    beghouledShuffle();
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:1854-1867 ({@code Challenge::BeghouledCheckStuckState}). */
    public void beghouledCheckStuckState() {
        if (c.mChallengeState != ChallengeState.STATECHALLENGE_NORMAL || c.mBoard.hasLevelAwardDropped()) {
            return;
        }

        BeghouledBoardState aBoardState = new BeghouledBoardState();
        c.loadBeghouledBoardState(aBoardState);
        if (beghouledCheckForPossibleMoves(aBoardState) == 0) {
            c.mChallengeState = ChallengeState.STATECHALLENGE_BEGHOULED_NO_MATCHES;
            c.mChallengeStateCounter = 500;
            c.mBoard.displayAdviceAgain("[ADVICE_BEGHOULED_NO_MOVES]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_BEGHOULED_NO_MOVES);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:1869-1886 ({@code Challenge::ZombieAtePlant}). */
    public void zombieAtePlant(Plant thePlant) {
        if (c.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_BEGHOULED && c.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            return;
        }

        c.mBeghouledEated[thePlant.mPlantCol][thePlant.mRow] = 1;

        if (c.mBoard.mSeedBank.mNumPackets == 4) {
            c.mBoard.mSeedBank.mNumPackets += 1;
            c.mBoard.mSeedBank.mSeedPackets[4].setPacketType(SeedType.SEED_BEGHOULED_BUTTON_CRATER, SeedType.SEED_NONE);
            c.mBoard.displayAdvice("[ADVICE_BEGHOULED_USE_CRATER_1]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_BEGHOULED_USE_CRATER_1);
        }

        beghouledCheckStuckState();
        beghouledUpdateCraters();
    }

    /** Ported from src/Lawn/Challenge.cpp:2085-2118 ({@code Challenge::DrawBeghouled}). */
    public void drawBeghouled(Graphics g) {
        for (int aGridY = 0; aGridY < Board.MAX_GRID_SIZE_Y; aGridY++) {
            for (int aGridX = 0; aGridX < Board.MAX_GRID_SIZE_X; aGridX++) {
                if (c.mBeghouledEated[aGridX][aGridY] != 0) {
                    g.drawImageCel(Resources.getImageById(ImageID.IMAGE_CRATER),
                            c.mBoard.gridToPixelX(aGridX, aGridY) - 8, c.mBoard.gridToPixelY(aGridX, aGridY) + 40, 1, 0);
                }
            }
        }

        if (c.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            HitResult aHitResult = new HitResult();
            c.mBoard.mouseHitTest(c.mApp.mWidgetManager.mLastMouseX, c.mApp.mWidgetManager.mLastMouseY, aHitResult);
            if (c.mChallengeGridX != -1 && c.mChallengeGridY != -1 && aHitResult.mObjectType != GameObjectType.OBJECT_TYPE_COIN) {
                float aPixelX = c.mBoard.gridToPixelX(c.mChallengeGridX, c.mChallengeGridY) + 80;
                float aPixelY = c.mBoard.gridToPixelY(c.mChallengeGridX, c.mChallengeGridY) + 100;

                // Rotation speed coefficient is 2*PI*0.001, which means one full rotation every 1000
                // frames. Modulus is to avoid large angle float precision cliffs.
                final int BEGHOULED_TWIST_ROTATION_PERIOD = 1000;
                float aRotation = -(c.mBoard.mMainCounter % BEGHOULED_TWIST_ROTATION_PERIOD) * (2 * (float) Math.PI * 0.001f);
                SexyTransform2D aTransform = new SexyTransform2D();
                PvzpCommon.pvzpScaleRotateTransformMatrix(aTransform, aPixelX, aPixelY, aRotation, 1, 1);

                Image aImageOverlay = Resources.getImageById(ImageID.IMAGE_BEGHOULED_TWIST_OVERLAY);
                Rect aSrcRect = new Rect(0, 0, aImageOverlay.mWidth, aImageOverlay.mHeight);
                PvzpCommon.pvzpBltMatrix(g, aImageOverlay, aTransform, g.mClipRect, new Color(255, 255, 255, 128),
                        g.getDrawMode(), aSrcRect);
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3235-3275 ({@code Challenge::BeghouledPacketClicked}). */
    public void beghouledPacketClicked(SeedPacket theSeedPacket) {
        int aPacketType = theSeedPacket.mPacketType;
        int aPacketCost = c.mBoard.getCurrentPlantCost(aPacketType, SeedType.SEED_NONE);
        if (!c.mBoard.canTakeSunMoney(aPacketCost)) {
            return;
        }

        int aUpgrade = aPacketType == SeedType.SEED_REPEATER ? 0
                : aPacketType == SeedType.SEED_FUMESHROOM ? 1
                : aPacketType == SeedType.SEED_TALLNUT ? 2 : -1;
        if (aPacketType == SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE) {
            if (c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_FALLING
                    || c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_MOVING) {
                return;
            }
            beghouledShuffle();
        } else if (aPacketType == SeedType.SEED_BEGHOULED_BUTTON_CRATER) {
            if (beghouledCanClearCrater() == 0 || c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_FALLING
                    || c.mChallengeState == ChallengeState.STATECHALLENGE_BEGHOULED_MOVING) {
                return;
            }
            beghouledClearCrater(1);
            beghouledStartFalling(ChallengeState.STATECHALLENGE_BEGHOULED_FALLING);
        } else if (aUpgrade != -1 && c.mBeghouledPurcasedUpgrade[aUpgrade] == 0) {
            c.mBeghouledPurcasedUpgrade[aUpgrade] = 1;
            final int[] gUpgradableSeedTypes = {SeedType.SEED_PEASHOOTER, SeedType.SEED_PUFFSHROOM, SeedType.SEED_WALLNUT};
            int aSeedPrimary = gUpgradableSeedTypes[aUpgrade];

            for (Plant aPlant : c.mBoard.mPlants) {
                if (aPlant.mDead) {
                    continue;
                }
                if (aPlant.mSeedType == aSeedPrimary) {
                    aPlant.die();
                    c.mBoard.addPlant(aPlant.mPlantCol, aPlant.mRow, aPacketType, SeedType.SEED_NONE);
                }
            }
            theSeedPacket.setActivate(false);
        }
        c.mBoard.takeSunMoney(aPacketCost);
    }

    /** Ported from src/Lawn/Challenge.cpp:3277-3289 ({@code Challenge::BeghouledShuffle}). */
    public void beghouledShuffle() {
        c.mBoard.clearAdvice(AdviceType.ADVICE_NONE);

        for (Plant aPlant : c.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            aPlant.die();
        }

        beghouledStartFalling(ChallengeState.STATECHALLENGE_BEGHOULED_FALLING);
    }

    /** Ported from src/Lawn/Challenge.cpp:3291-3305 ({@code Challenge::BeghouledCanClearCrater}). */
    public int beghouledCanClearCrater() {
        for (int aRow = 0; aRow < 5; aRow++) {
            for (int aCol = 0; aCol < 8; aCol++) {
                if (c.mBeghouledEated[aCol][aRow] != 0) {
                    return 1;
                }
            }
        }

        return 0;
    }
}
