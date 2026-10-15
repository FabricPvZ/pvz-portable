/*
 * Ported from src/Lawn/ZenGarden.h:44-153 and src/Lawn/ZenGarden.cpp:44-2461.
 *
 * The Zen Garden mini-game: the potted-plant model lives in PlayerInfo (PottedPlant[]), this class
 * owns the garden grid (PixelToGridX/Y + GridToPixelX/Y over the per-background placement tables),
 * the plant needs (water/fertilizer/bug spray/phonograph), the wheelbarrow/glove/money-sign tool
 * flows, the tree-of-wisdom/garden progression, the Zen tutorial state machine and the
 * stinky-the-snail pet (walk/sleep/chocolate state machine).
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleEmitter;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

public class ZenGarden {

    /**
     * The plant (and its price) the "sell this plant?" prompt is about, or -1. Upstream keeps both in
     * locals across the blocking {@code Dialog::WaitForResult} (ZenGarden.cpp:487-527); see the
     * PORT-NOTE in {@link #mouseDownWithMoneySign}.
     */
    public int mPendingSellPlantID = -1;
    public int mPendingSellPrice;

    /** Ported from src/Lawn/ZenGarden.h:32. */
    public static final int ZEN_MAX_GRIDSIZE_X = 8;

    /** Ported from src/Lawn/ZenGarden.h:33. */
    public static final int ZEN_MAX_GRIDSIZE_Y = 4;

    /** Ported from src/Lawn/ZenGarden.cpp:44 ({@code STINKY_SLEEP_POS_Y}). */
    private static final float STINKY_SLEEP_POS_Y = 461.0f;

    /** Ported from src/Lawn/ZenGarden.h:43-50 (class SpecialGridPlacement). */
    public static final class SpecialGridPlacement {
        public int mPixelX;
        public int mPixelY;
        public int mGridX;
        public int mGridY;

        public SpecialGridPlacement(int thePixelX, int thePixelY, int theGridX, int theGridY) {
            mPixelX = thePixelX;
            mPixelY = thePixelY;
            mGridX = theGridX;
            mGridY = theGridY;
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:46-80 ({@code gGreenhouseGridPlacement}). */
    private static final SpecialGridPlacement[] gGreenhouseGridPlacement = {
            new SpecialGridPlacement(73, 73, 0, 0),
            new SpecialGridPlacement(155, 71, 1, 0),
            new SpecialGridPlacement(239, 68, 2, 0),
            new SpecialGridPlacement(321, 73, 3, 0),
            new SpecialGridPlacement(406, 71, 4, 0),
            new SpecialGridPlacement(484, 67, 5, 0),
            new SpecialGridPlacement(566, 70, 6, 0),
            new SpecialGridPlacement(648, 72, 7, 0),
            new SpecialGridPlacement(67, 168, 0, 1),
            new SpecialGridPlacement(150, 165, 1, 1),
            new SpecialGridPlacement(232, 170, 2, 1),
            new SpecialGridPlacement(314, 175, 3, 1),
            new SpecialGridPlacement(416, 173, 4, 1),
            new SpecialGridPlacement(497, 170, 5, 1),
            new SpecialGridPlacement(578, 164, 6, 1),
            new SpecialGridPlacement(660, 168, 7, 1),
            new SpecialGridPlacement(41, 268, 0, 2),
            new SpecialGridPlacement(130, 266, 1, 2),
            new SpecialGridPlacement(219, 260, 2, 2),
            new SpecialGridPlacement(310, 266, 3, 2),
            new SpecialGridPlacement(416, 267, 4, 2),
            new SpecialGridPlacement(504, 261, 5, 2),
            new SpecialGridPlacement(594, 265, 6, 2),
            new SpecialGridPlacement(684, 269, 7, 2),
            new SpecialGridPlacement(37, 371, 0, 3),
            new SpecialGridPlacement(124, 369, 1, 3),
            new SpecialGridPlacement(211, 368, 2, 3),
            new SpecialGridPlacement(302, 369, 3, 3),
            new SpecialGridPlacement(425, 375, 4, 3),
            new SpecialGridPlacement(512, 368, 5, 3),
            new SpecialGridPlacement(602, 365, 6, 3),
            new SpecialGridPlacement(691, 368, 7, 3),
    };

    /** Ported from src/Lawn/ZenGarden.cpp:82-91 ({@code gMushroomGridPlacement}). */
    private static final SpecialGridPlacement[] gMushroomGridPlacement = {
            new SpecialGridPlacement(110, 441, 0, 0),
            new SpecialGridPlacement(237, 360, 1, 0),
            new SpecialGridPlacement(298, 458, 2, 0),
            new SpecialGridPlacement(355, 296, 3, 0),
            new SpecialGridPlacement(387, 203, 4, 0),
            new SpecialGridPlacement(460, 385, 5, 0),
            new SpecialGridPlacement(486, 478, 6, 0),
            new SpecialGridPlacement(552, 283, 7, 0),
    };

    /** Ported from src/Lawn/ZenGarden.cpp:93-102 ({@code gAquariumGridPlacement}). */
    private static final SpecialGridPlacement[] gAquariumGridPlacement = {
            new SpecialGridPlacement(113, 185, 0, 0),
            new SpecialGridPlacement(306, 120, 1, 0),
            new SpecialGridPlacement(356, 270, 2, 0),
            new SpecialGridPlacement(622, 120, 3, 0),
            new SpecialGridPlacement(669, 270, 4, 0),
            new SpecialGridPlacement(122, 355, 5, 0),
            new SpecialGridPlacement(365, 458, 6, 0),
            new SpecialGridPlacement(504, 417, 7, 0),
    };

    /** Ported from src/Lawn/ZenGarden.h:60. */
    public LawnApp mApp;

    /** Ported from src/Lawn/ZenGarden.h:61. */
    public Board mBoard;

    /** Ported from src/Lawn/ZenGarden.h:62 ({@link GardenType} value). */
    public int mGardenType;

    /** Ported from src/Lawn/ZenGarden.h:63. */
    public final List<String> mLoadedResourceNames = new ArrayList<>();

    /** Ported from src/Lawn/ZenGarden.h:64 ({@code time_t}, cached per-frame). */
    public long mNowTime;

    /**
     * Ported from src/Lawn/ZenGarden.h:65 ({@code tm}, cached per-frame).
     *
     * <p>Upstream keeps the broken-down local time and only reads {@code tm_year}/{@code tm_yday}
     * (ZenGarden.cpp:774-798); {@link LocalDateTime} is the Java equivalent of that {@code tm}
     * (both are the local wall-clock breakdown), and its year/day-of-year map 1:1 onto those fields.
     */
    public LocalDateTime mNowTM;

    /** Ported from src/Lawn/ZenGarden.cpp:104-111 (ZenGarden::ZenGarden). */
    public ZenGarden() {
        mApp = LawnApp.gLawnApp;
        mBoard = null;
        mGardenType = GardenType.GARDEN_MAIN;
        // Constructed on the loading thread: GetNowTime() reads main-thread state, refreshed before any use.
        mNowTime = System.currentTimeMillis() / 1000L;
        mNowTM = mApp != null ? mApp.getLocalTime(mNowTime) : LocalDateTime.now();
    }

    /**
     * Ported from src/Lawn/ZenGarden.cpp:113-116 (~ZenGarden).
     *
     * <p>Java has no destructor; upstream only released the delay-loaded resource groups here, so
     * {@link LawnApp} calls this when the garden is torn down. The tracked-resource list is cleared
     * to mirror the value state left behind by the destructor.
     */
    public void dispose() {
        if (mApp != null && mApp.mResourceManager != null) {
            mApp.mResourceManager.releaseTrackedResources(mLoadedResourceNames);
        }
        mLoadedResourceNames.clear();
    }

    /** Upstream {@code PVZP_ASSERT}; the ported files raise {@link IllegalStateException} instead. */
    private static void zenAssert(boolean theCondition, String theMessage) {
        if (!theCondition) {
            throw new IllegalStateException("PVZP_ASSERT failed: " + theMessage);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:118-121 (ZenGarden::DrawPottedPlantIcon). */
    public void drawPottedPlantIcon(Graphics g, float x, float y, PottedPlant thePottedPlant) {
        drawPottedPlant(g, x, y, thePottedPlant, 0.7f, true);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:123-192 (ZenGarden::DrawPottedPlant). */
    public void drawPottedPlant(Graphics g, float x, float y, PottedPlant thePottedPlant, float theScale,
            boolean theDrawPot) {
        Graphics aPottedPlantG = new Graphics(g);
        aPottedPlantG.mScaleX = theScale;
        aPottedPlantG.mScaleY = theScale;

        int aPlantVariation = DrawVariation.VARIATION_NORMAL;
        int aSeedType = thePottedPlant.mSeedType;
        if (thePottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SPROUT) {
            aSeedType = SeedType.SEED_SPROUT;
            if (thePottedPlant.mSeedType != SeedType.SEED_MARIGOLD) {
                aPlantVariation = DrawVariation.VARIATION_SPROUT_NO_FLOWER;
            }
        } else if ((aSeedType == SeedType.SEED_TANGLEKELP || aSeedType == SeedType.SEED_SEASHROOM)
                && thePottedPlant.mWhichZenGarden == GardenType.GARDEN_AQUARIUM) {
            aPlantVariation = DrawVariation.VARIATION_AQUARIUM;
        } else {
            aPlantVariation = thePottedPlant.mDrawVariation;
        }

        float aOffsetX = 0.0f;
        float aOffsetY = Plant.plantDrawHeightOffset(mBoard, null, aSeedType, -1, -1);
        if (theDrawPot) {
            float aPotOffsetY = theScale * 0.0f - 0.0f + 0.0f;
            aPotOffsetY += Plant.plantDrawHeightOffset(mBoard, null, SeedType.SEED_FLOWERPOT, -1, -1);

            int aPotVariation2 = DrawVariation.VARIATION_ZEN_GARDEN;
            if (Plant.isAquatic(aSeedType)) {
                aPotVariation2 = DrawVariation.VARIATION_ZEN_GARDEN_WATER;
            }

            Plant.drawSeedType(aPottedPlantG, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE, aPotVariation2, x,
                    y + aPotOffsetY * theScale);
        }

        if (thePottedPlant.mFacing == PottedPlant.FacingDirection.FACING_LEFT) {
            aPottedPlantG.mScaleX = -theScale;
            aOffsetX += 80.0f * theScale;
        }

        if (thePottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
            aOffsetX += 20.0f * aPottedPlantG.mScaleX;
            aOffsetY += 40.0f * aPottedPlantG.mScaleY;
            aPottedPlantG.mScaleX *= 0.5f;
            aPottedPlantG.mScaleY *= 0.5f;
        } else if (thePottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_MEDIUM) {
            aOffsetX += 10.0f * aPottedPlantG.mScaleX;
            aOffsetY += 20.0f * aPottedPlantG.mScaleY;
            aPottedPlantG.mScaleX *= 0.75f;
            aPottedPlantG.mScaleY *= 0.75f;
        }

        if (theDrawPot) {
            aOffsetY += Plant.plantFlowerPotHeightOffset(aSeedType, theScale);
        }
        aOffsetY += plantPottedDrawHeightOffset(aSeedType, aPottedPlantG.mScaleY);

        Plant.drawSeedType(aPottedPlantG, aSeedType, SeedType.SEED_NONE, aPlantVariation, x + aOffsetX, y + aOffsetY);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:194-199 (ZenGarden::PlantSetLaunchCounter). */
    public void plantSetLaunchCounter(Plant thePlant) {
        int aTime = plantGetMinutesSinceHappy(thePlant);
        int aCounterMax = PvzpCommon.pvzpAnimateCurve(5, 30, aTime, 3000, 15000, PvzpCurves.CURVE_LINEAR);
        thePlant.mLaunchCounter = PvzpCommon.randRangeInt(1800, aCounterMax);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:201-268 (ZenGarden::PlacePottedPlant). */
    public Plant placePottedPlant(int thePottedPlantIndex) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePottedPlantIndex);
        int aSeedType = aPottedPlant.mSeedType;
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SPROUT) {
            aSeedType = SeedType.SEED_SPROUT;
        }

        boolean needPot = true;
        if (mGardenType == GardenType.GARDEN_MUSHROOM && !Plant.isAquatic(aSeedType)) {
            needPot = false;
        } else if (mGardenType == GardenType.GARDEN_AQUARIUM) {
            needPot = false;
        }

        if (needPot) {
            Plant aPot = mBoard.newPlant(aPottedPlant.mX, aPottedPlant.mY, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
            aPot.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0, aPot.mY);
            aPot.mStateCountdown = 0;

            Reanimation aPotReanim = mApp.reanimationGet(aPot.mBodyReanimID);
            if (Plant.isAquatic(aSeedType)) {
                aPotReanim.setFramesForLayer("anim_waterplants");
            } else {
                aPotReanim.setFramesForLayer("anim_zengarden");
            }
        }

        Plant aPlant = mBoard.newPlant(aPottedPlant.mX, aPottedPlant.mY, aSeedType, SeedType.SEED_NONE);
        aPlant.mPottedPlantIndex = thePottedPlantIndex;
        aPlant.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0, aPlant.mY + 1);
        aPlant.mStateCountdown = 0;

        Reanimation aPlantReanim = mApp.reanimationTryToGet(aPlant.mBodyReanimID);
        if (aPlantReanim != null) {
            if (aSeedType == SeedType.SEED_SPROUT) {
                if (aPottedPlant.mSeedType != SeedType.SEED_MARIGOLD) {
                    aPlantReanim.setFramesForLayer("anim_idle_noflower");
                }
            } else if ((aSeedType == SeedType.SEED_TANGLEKELP || aSeedType == SeedType.SEED_SEASHROOM)
                    && mGardenType == GardenType.GARDEN_AQUARIUM) {
                aPlantReanim.setFramesForLayer("anim_idle_aquarium");
            } else if (aPottedPlant.mDrawVariation != DrawVariation.VARIATION_NORMAL) {
                mApp.mReanimatorCache.updateReanimationForVariation(aPlantReanim, aPottedPlant.mDrawVariation);
            }

            aPlant.updateReanim();
            aPlantReanim.update();
        }

        plantSetLaunchCounter(aPlant);
        updatePlantEffectState(aPlant);
        return aPlant;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:270-278 (ZenGarden::RemovePottedPlant). */
    public void removePottedPlant(Plant thePlant) {
        thePlant.die();
        Plant aPot = mBoard.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow, PlantPriority.TOPPLANT_ONLY_UNDER_PLANT);
        if (aPot != null) {
            aPot.die();
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:280-284 (ZenGarden::PottedPlantFromIndex). */
    public PottedPlant pottedPlantFromIndex(int thePottedPlantIndex) {
        zenAssert(thePottedPlantIndex >= 0
                && thePottedPlantIndex < mApp.mPlayerInfo.mNumPottedPlants, "potted-plant index out of range");
        return mApp.mPlayerInfo.mPottedPlant[thePottedPlantIndex];
    }

    /**
     * Java-only helper for the upstream pointer arithmetic that maps a {@code PottedPlant*} back to
     * its index in {@code mPlayerInfo->mPottedPlant} (ZenGarden.cpp:1935-1937, :309).
     */
    private int pottedPlantIndex(PottedPlant thePottedPlant) {
        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            if (mApp.mPlayerInfo.mPottedPlant[i] == thePottedPlant) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Java-only helper for the upstream struct assignment {@code *aPottedPlant = *thePottedPlant;}
     * (ZenGarden.cpp:388): PottedPlant is a Java reference type, so the fields are copied value-wise.
     */
    private static void copyPottedPlant(PottedPlant theDest, PottedPlant theSource) {
        theDest.mSeedType = theSource.mSeedType;
        theDest.mWhichZenGarden = theSource.mWhichZenGarden;
        theDest.mX = theSource.mX;
        theDest.mY = theSource.mY;
        theDest.mFacing = theSource.mFacing;
        theDest.mPadding1 = theSource.mPadding1;
        theDest.mLastWateredTime = theSource.mLastWateredTime;
        theDest.mDrawVariation = theSource.mDrawVariation;
        theDest.mPlantAge = theSource.mPlantAge;
        theDest.mTimesFed = theSource.mTimesFed;
        theDest.mFeedingsPerGrow = theSource.mFeedingsPerGrow;
        theDest.mPlantNeed = theSource.mPlantNeed;
        theDest.mPadding2 = theSource.mPadding2;
        theDest.mLastNeedFulfilledTime = theSource.mLastNeedFulfilledTime;
        theDest.mLastFertilizedTime = theSource.mLastFertilizedTime;
        theDest.mLastChocolateTime = theSource.mLastChocolateTime;
        theDest.mFutureAttribute[0] = theSource.mFutureAttribute[0];
    }

    /** Ported from src/Lawn/ZenGarden.cpp:286-304 (ZenGarden::ZenGardenInitLevel). */
    public void zenGardenInitLevel() {
        mBoard = mApp.mBoard;
        mNowTime = mApp.getNowTime();
        mNowTM = mApp.getLocalTime(mNowTime);

        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(i);
            if (aPottedPlant.mWhichZenGarden == mGardenType) {
                placePottedPlant(i);
            }
        }

        mBoard.mChallenge.mChallengeStateCounter = 3000;
        addStinky();
        mApp.mMusic.startGameMusic();
    }

    /** Ported from src/Lawn/ZenGarden.cpp:306-308 (ZenGarden::ZenGardenStart) - empty upstream. */
    public void zenGardenStart() {
    }

    /** Ported from src/Lawn/ZenGarden.cpp:310-314 (ZenGarden::PlantCanHaveChocolate). */
    public boolean plantCanHaveChocolate(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        return aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_FULL && wasPlantNeedFulfilledToday(aPottedPlant)
                && !plantHighOnChocolate(aPottedPlant);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:316-319 (ZenGarden::CanDropChocolate). */
    public boolean canDropChocolate() {
        return hasPurchasedStinky()
                && mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] < PlayerInfo.PURCHASE_COUNT_OFFSET + 10;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:321-341 (ZenGarden::IsZenGardenFull). */
    public boolean isZenGardenFull(boolean theIncludeDroppedPresents) {
        int aNumDroppedPresents = 0;
        if (mBoard != null && theIncludeDroppedPresents) {
            aNumDroppedPresents += mBoard.countCoinByType(CoinType.COIN_AWARD_PRESENT);
            aNumDroppedPresents += mBoard.countCoinByType(CoinType.COIN_PRESENT_PLANT);
        }

        int aNumPottedPlantsInGarden = 0;
        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(i);
            if (aPottedPlant.mWhichZenGarden == GardenType.GARDEN_MAIN) {
                aNumPottedPlantsInGarden++;
            }
        }

        return aNumDroppedPresents + aNumPottedPlantsInGarden >= ZEN_MAX_GRIDSIZE_X * ZEN_MAX_GRIDSIZE_Y;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:343-346 (ZenGarden::CanDropPottedPlantLoot). */
    public boolean canDropPottedPlantLoot() {
        return mApp.hasFinishedAdventure() && !isZenGardenFull(true);
    }

    /**
     * Ported from src/Lawn/ZenGarden.cpp:348-383 (ZenGarden::FindOpenZenGardenSpot).
     *
     * <p>Upstream takes two {@code int&} out-parameters; Java passes a two-element array where
     * {@code theSpot[0]} receives {@code theSpotX} and {@code theSpot[1]} receives {@code theSpotY}.
     */
    public void findOpenZenGardenSpot(int[] theSpot) {
        PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[ZEN_MAX_GRIDSIZE_X * ZEN_MAX_GRIDSIZE_Y];
        int aPickCount = 0;

        for (int x = 0; x < ZEN_MAX_GRIDSIZE_X; x++) {
            for (int y = 0; y < ZEN_MAX_GRIDSIZE_Y; y++) {
                boolean aSkipPlantPick = false;

                if (mApp.mCrazyDaveMessageIndex != -1 && (x < 2 || y < 1)) {
                    aSkipPlantPick = true; // skip spots covered by Crazy Dave
                }

                if (!aSkipPlantPick) {
                    for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
                        PottedPlant aPottedPlant = pottedPlantFromIndex(i);
                        if (aPottedPlant.mWhichZenGarden == GardenType.GARDEN_MAIN && aPottedPlant.mX == x
                                && aPottedPlant.mY == y) {
                            aSkipPlantPick = true;
                            break;
                        }
                    }
                }

                if (!aSkipPlantPick) {
                    aPicks[aPickCount] = new PvzpWeightedGridArray(x, y, 1);
                    aPickCount++;
                }
            }
        }

        PvzpWeightedGridArray aSpot = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPickCount);
        theSpot[0] = aSpot.mX;
        theSpot[1] = aSpot.mY;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:385-405 (ZenGarden::AddPottedPlant). */
    public void addPottedPlant(PottedPlant thePottedPlant) {
        zenAssert(mApp.mPlayerInfo.mNumPottedPlants < PlayerInfo.MAX_POTTED_PLANTS, "potted-plant list full");

        int aPottedPlantIndex = mApp.mPlayerInfo.mNumPottedPlants;
        PottedPlant aPottedPlant = mApp.mPlayerInfo.mPottedPlant[aPottedPlantIndex];
        copyPottedPlant(aPottedPlant, thePottedPlant);
        aPottedPlant.mWhichZenGarden = GardenType.GARDEN_MAIN;
        aPottedPlant.mLastWateredTime = 0;
        int[] aSpot = new int[2];
        findOpenZenGardenSpot(aSpot);
        aPottedPlant.mX = aSpot[0];
        aPottedPlant.mY = aSpot[1];
        mApp.mPlayerInfo.mNumPottedPlants++;

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN && mBoard != null
                && aPottedPlant.mWhichZenGarden == mGardenType) {
            Plant aPlant = placePottedPlant(aPottedPlantIndex);
            if (mApp.getDialog(Dialogs.DIALOG_STORE) == null) {
                mBoard.doPlantingEffects(aPottedPlant.mX, aPottedPlant.mY, aPlant);
            }
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:407-453 (ZenGarden::GetPlantSellPrice). */
    public int getPlantSellPrice(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        if (aPottedPlant.mSeedType == SeedType.SEED_MARIGOLD) {
            if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SPROUT) {
                return 150;
            }
            if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
                return 200;
            }
            if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_MEDIUM) {
                return 250;
            }
            if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_FULL) {
                return 300;
            }
            zenAssert(false, "unexpected marigold plant age");
        }
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SPROUT) {
            return 150;
        }
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
            return 300;
        }
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_MEDIUM) {
            return 500;
        }
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_FULL) {
            if (Plant.isNocturnal(aPottedPlant.mSeedType) || Plant.isAquatic(aPottedPlant.mSeedType)) {
                return 1000;
            }
            return 800;
        }
        throw new IllegalStateException("unreachable");
    }

    /** Ported from src/Lawn/ZenGarden.cpp:455-527 (ZenGarden::MouseDownWithMoneySign). */
    public void mouseDownWithMoneySign(Plant thePlant) {
        mBoard.clearCursor();

        String aHeader = PvzpStringFile.translate("[ZEN_SELL_HEADER]");
        String aLines = PvzpStringFile.translate("[ZEN_SELL_LINES]");
        int aPrice = getPlantSellPrice(thePlant);
        if (mApp.mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_OFF) {
            mApp.crazyDaveEnter();
        }

        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        String aMessageText = mApp.getCrazyDaveText(1700);
        aMessageText = PvzpCommon.pvzpReplaceNumberString(aMessageText, "{SELL_PRICE}", aPrice);

        String aPlantName;
        if (thePlant.mSeedType == SeedType.SEED_SPROUT && aPottedPlant.mSeedType == SeedType.SEED_MARIGOLD) {
            aPlantName = PvzpStringFile.translate("[MARIGOLD_SPROUT]");
        } else {
            aPlantName = Plant.getNameString(thePlant.mSeedType, thePlant.mImitaterType);
        }
        aMessageText = PvzpCommon.pvzpReplaceString(aMessageText, "{PLANT_TYPE}", aPlantName);

        mApp.crazyDaveTalkMessage(aMessageText);
        Reanimation aCrazyDaveReanim = mApp.reanimationGet(mApp.mCrazyDaveReanimID);
        aCrazyDaveReanim.playReanim("anim_blahblah", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);

        int aPlantID = mBoard.mPlants.dataArrayGetID(thePlant);
        Dialog aDialog = mApp.doDialog(Dialogs.DIALOG_ZEN_SELL, true, aHeader, aLines, "", Dialog.BUTTONS_YES_NO);
        aDialog.mX += 120;
        aDialog.mY += 60;
        mBoard.showCoinBank();
        // PORT-NOTE: ZenGarden.cpp:487-491 — upstream answers the sale with Dialog::WaitForResult(),
        // which pumps the message loop until a button is depressed (Dialog.cpp:410-441). This port's
        // waitForResult returns without pumping (sexy/Dialog.java:506), so the result read here was
        // always the "not answered yet" sentinel and the plant was never sold. The answer arrives in
        // finishSellPlant(), which LawnApp.buttonDepress calls for the prompt's buttons.
        mPendingSellPlantID = aPlantID;
        mPendingSellPrice = aPrice;
    }

    /**
     * ZenGarden.cpp:487-527 — applies the player's answer to the "sell this plant?" prompt (upstream
     * runs it right after the blocking {@code Dialog::WaitForResult()}; see the PORT-NOTE in
     * {@link #mouseDownWithMoneySign}).
     */
    public void finishSellPlant(boolean theConfirmed) {
        int aPlantID = mPendingSellPlantID;
        int aPrice = mPendingSellPrice;
        mPendingSellPlantID = -1;
        mPendingSellPrice = 0;

        mApp.crazyDaveLeave();

        if (theConfirmed) {
            Plant aSellPlant = mBoard.mPlants.dataArrayTryToGet(aPlantID);
            if (aSellPlant == null || aSellPlant.mDead) { // the plant may have been replaced while the prompt was up
                return;
            }

            PottedPlant aPottedPlant = pottedPlantFromIndex(aSellPlant.mPottedPlantIndex);
            mApp.mPlayerInfo.addCoins(aPrice);
            mBoard.mCoinsCollected += aPrice;

            int aNumPlantsAfterThis = mApp.mPlayerInfo.mNumPottedPlants - aSellPlant.mPottedPlantIndex - 1;
            if (aNumPlantsAfterThis > 0) {
                // Upstream memmove of the struct array (ZenGarden.cpp:493): shift the records down.
                System.arraycopy(mApp.mPlayerInfo.mPottedPlant, aSellPlant.mPottedPlantIndex + 1,
                        mApp.mPlayerInfo.mPottedPlant, aSellPlant.mPottedPlantIndex, aNumPlantsAfterThis);
                // Upstream leaves a trailing duplicate record; Java copies it so the slot is not aliased
                // with the record that just moved down into it.
                PottedPlant aLast = mApp.mPlayerInfo.mPottedPlant[mApp.mPlayerInfo.mNumPottedPlants - 1];
                copyPottedPlant(aLast, mApp.mPlayerInfo.mPottedPlant[mApp.mPlayerInfo.mNumPottedPlants - 2]);

                for (Plant aUpdatePlant : mBoard.mPlants) {
                    if (aUpdatePlant.mDead) {
                        continue;
                    }
                    if (aUpdatePlant.mPottedPlantIndex > aSellPlant.mPottedPlantIndex) {
                        aUpdatePlant.mPottedPlantIndex--;
                    }
                }
            }

            mApp.mPlayerInfo.mNumPottedPlants--;
            mApp.playFoley(FoleyType.FOLEY_USE_SHOVEL);
            // mApp.PlaySample(SOUND_PLANT2);
            removePottedPlant(aSellPlant);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:529-578 (ZenGarden::PlantFertilized). */
    public void plantFertilized(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        aPottedPlant.mLastFertilizedTime = mNowTime;
        aPottedPlant.mPlantAge = aPottedPlant.mPlantAge + 1;
        aPottedPlant.mPlantNeed = PottedPlantNeed.PLANTNEED_NONE;
        aPottedPlant.mTimesFed = 0;

        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
            int aOldPlantID = mBoard.mPlants.dataArrayGetID(thePlant);
            removePottedPlant(thePlant);
            Plant aNewPlant = placePottedPlant(thePlant.mPottedPlantIndex);
            if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE
                    && mBoard.mCursorObject.mGlovePlantID == aOldPlantID) {
                // keep the glove holding the replacement plant
                mBoard.mCursorObject.mGlovePlantID = mBoard.mPlants.dataArrayGetID(aNewPlant);
            }
            mApp.playSample(SoundType.SOUND_LOADINGBAR_FLOWER);
        } else {
            thePlant.mStateCountdown = 100;
            mApp.playFoley(FoleyType.FOLEY_PLANTGROW);
        }

        mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
            mBoard.addCoin(thePlant.mX + 40, thePlant.mY, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);
        } else if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_MEDIUM) {
            mBoard.addCoin(thePlant.mX + 30, thePlant.mY, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);
            mBoard.addCoin(thePlant.mX + 50, thePlant.mY, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);
        } else if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_FULL) {
            if (aPottedPlant.mSeedType == SeedType.SEED_MARIGOLD) {
                mBoard.addCoin(thePlant.mX + 40, thePlant.mY, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
            } else {
                mBoard.addCoin(thePlant.mX + 10, thePlant.mY, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
                mBoard.addCoin(thePlant.mX + 70, thePlant.mY, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
            }
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:580-595 (ZenGarden::PlantFulfillNeed). */
    public void plantFulfillNeed(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        aPottedPlant.mLastNeedFulfilledTime = mNowTime;
        aPottedPlant.mPlantNeed = PottedPlantNeed.PLANTNEED_NONE;
        aPottedPlant.mTimesFed = 0;

        mApp.playFoley(FoleyType.FOLEY_PRIZE);
        mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
        mBoard.addCoin(thePlant.mX + 40, thePlant.mY, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);
        if (Plant.isNocturnal(thePlant.mSeedType) || Plant.isAquatic(thePlant.mSeedType)) {
            mBoard.addCoin(thePlant.mX + 10, thePlant.mY, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);
            mBoard.addCoin(thePlant.mX + 70, thePlant.mY, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:597-608 (ZenGarden::PlantsNeedWater). */
    public boolean plantsNeedWater() {
        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(i);
            if (mApp.mZenGarden.getPlantsNeed(aPottedPlant) == PottedPlantNeed.PLANTNEED_WATER) {
                return true;
            }
        }
        return false;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:610-619 (ZenGarden::PlantCanBeWatered). */
    public boolean plantCanBeWatered(Plant thePlant) {
        if (thePlant.mPottedPlantIndex == -1) {
            return false;
        }

        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        return mApp.mZenGarden.getPlantsNeed(aPottedPlant) == PottedPlantNeed.PLANTNEED_WATER;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:621-633 (ZenGarden::CountPlantsNeedingFertilizer). */
    public int countPlantsNeedingFertilizer() {
        int aCount = 0;
        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(i);
            if (mApp.mZenGarden.getPlantsNeed(aPottedPlant) == PottedPlantNeed.PLANTNEED_FERTILIZER) {
                aCount++;
            }
        }
        return aCount;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:635-646 (ZenGarden::AllPlantsHaveBeenFertilized). */
    public boolean allPlantsHaveBeenFertilized() {
        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(i);
            if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SPROUT) {
                return false;
            }
        }
        return true;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:648-673 (ZenGarden::PlantWatered). */
    public void plantWatered(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        aPottedPlant.mTimesFed++;
        int aTimeSpan = PvzpCommon.randRangeInt(0, 8);
        if (mBoard.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_WATER_PLANT
                || mBoard.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_KEEP_WATERING) {
            aTimeSpan = 9;
        }
        aPottedPlant.mLastWateredTime = mNowTime - aTimeSpan;

        mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
        mBoard.addCoin(thePlant.mX + 40, thePlant.mY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_FULL
                && aPottedPlant.mPlantNeed == PottedPlantNeed.PLANTNEED_NONE) {
            aPottedPlant.mPlantNeed = PvzpCommon.randRangeInt(PottedPlantNeed.PLANTNEED_BUGSPRAY,
                    PottedPlantNeed.PLANTNEED_PHONOGRAPH);
        }

        if (mBoard.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_WATER_PLANT) {
            mBoard.mTutorialState = TutorialState.TUTORIAL_ZEN_GARDEN_KEEP_WATERING;
            mBoard.displayAdvice("[ADVICE_ZEN_GARDEN_KEEP_WATERING]", MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG,
                    AdviceType.ADVICE_NONE);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:675-737 (ZenGarden::UpdatePlantEffectState). */
    public void updatePlantEffectState(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        int aOriginalState = thePlant.mState;
        int aPlantNeed = getPlantsNeed(aPottedPlant);
        if (aPlantNeed == PottedPlantNeed.PLANTNEED_WATER) {
            thePlant.mState = PlantState.STATE_NOTREADY;
        } else if (aPlantNeed == PottedPlantNeed.PLANTNEED_NONE) {
            if (wasPlantNeedFulfilledToday(aPottedPlant)) {
                thePlant.mState = PlantState.STATE_ZEN_GARDEN_HAPPY;
            } else if (thePlant.mIsAsleep) {
                thePlant.mState = PlantState.STATE_NOTREADY;
            } else {
                thePlant.mState = PlantState.STATE_ZEN_GARDEN_WATERED;
            }
        } else {
            thePlant.mState = PlantState.STATE_ZEN_GARDEN_NEEDY;
        }
        if (aOriginalState == thePlant.mState) {
            return;
        }

        Plant aFlowerPot = mBoard.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow, PlantPriority.TOPPLANT_ONLY_UNDER_PLANT);
        if (aFlowerPot != null && !Plant.isAquatic(thePlant.mSeedType)) {
            Reanimation aPotReanim = mApp.reanimationGet(aFlowerPot.mBodyReanimID);
            if (thePlant.mState == PlantState.STATE_ZEN_GARDEN_WATERED
                    || thePlant.mState == PlantState.STATE_ZEN_GARDEN_NEEDY
                    || thePlant.mState == PlantState.STATE_ZEN_GARDEN_HAPPY) {
                aPotReanim.setImageOverride("Pot_top", Resources.getImageById(ImageID.IMAGE_REANIM_POT_TOP_DARK));
            } else {
                aPotReanim.setImageOverride("Pot_top", null);
            }
        }
        if (aOriginalState == PlantState.STATE_ZEN_GARDEN_HAPPY) {
            removeHappyEffect(thePlant);
        }

        if (thePlant.mState == PlantState.STATE_ZEN_GARDEN_HAPPY) {
            thePlant.setSleeping(false);
            addHappyEffect(thePlant);
        } else if (Plant.isNocturnal(thePlant.mSeedType) && !mBoard.stageIsNight()) {
            thePlant.setSleeping(true);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:739-754 (ZenGarden::AddHappyEffect). */
    public void addHappyEffect(Plant thePlant) {
        Plant aFlowerPot = mBoard.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow, PlantPriority.TOPPLANT_ONLY_UNDER_PLANT);
        if (aFlowerPot == null) {
            thePlant.addAttachedParticle(thePlant.mX + 40, thePlant.mY + 60, thePlant.mRenderOrder - 1,
                    ParticleEffect.PARTICLE_POTTED_ZEN_GLOW);
        } else if (Plant.isAquatic(thePlant.mSeedType)) {
            aFlowerPot.addAttachedParticle(aFlowerPot.mX + 40, aFlowerPot.mY + 61, aFlowerPot.mRenderOrder - 1,
                    ParticleEffect.PARTICLE_POTTED_WATER_PLANT_GLOW);
        } else {
            aFlowerPot.addAttachedParticle(aFlowerPot.mX + 40, aFlowerPot.mY + 63, aFlowerPot.mRenderOrder - 1,
                    ParticleEffect.PARTICLE_POTTED_PLANT_GLOW);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:756-772 (ZenGarden::RemoveHappyEffect). */
    public void removeHappyEffect(Plant thePlant) {
        Plant aFlowerPot = mBoard.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow, PlantPriority.TOPPLANT_ONLY_UNDER_PLANT);
        PvzpParticleSystem aParticleSystem;
        if (aFlowerPot != null) {
            aParticleSystem = mApp.particleTryToGet(aFlowerPot.mParticleID);
        } else {
            aParticleSystem = mApp.particleTryToGet(thePlant.mParticleID);
        }
        if (aParticleSystem != null) {
            aParticleSystem.particleSystemDie();
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:774-785 (ZenGarden::WasPlantNeedFulfilledToday). */
    public boolean wasPlantNeedFulfilledToday(PottedPlant thePottedPlant) {
        if (mNowTime - thePottedPlant.mLastNeedFulfilledTime < 3600) {
            return true;
        }

        LocalDateTime aLastNeedFulfilledTM = mApp.getLocalTime(thePottedPlant.mLastNeedFulfilledTime);
        return mNowTM.getYear() <= aLastNeedFulfilledTM.getYear()
                && mNowTM.getDayOfYear() <= aLastNeedFulfilledTM.getDayOfYear();
    }

    /** Ported from src/Lawn/ZenGarden.cpp:787-798 (ZenGarden::PlantShouldRefreshNeed). */
    public boolean plantShouldRefreshNeed(PottedPlant thePottedPlant) {
        if (mNowTime - thePottedPlant.mLastWateredTime < 3600) {
            return false;
        }

        LocalDateTime aLastWateredTM = mApp.getLocalTime(thePottedPlant.mLastWateredTime);
        return mNowTM.getYear() > aLastWateredTM.getYear()
                || mNowTM.getDayOfYear() > aLastWateredTM.getDayOfYear();
    }

    /** Ported from src/Lawn/ZenGarden.cpp:800-819 (ZenGarden::RefreshPlantNeeds). */
    public void refreshPlantNeeds(PottedPlant thePottedPlant) {
        if (thePottedPlant.mPlantAge != PottedPlantAge.PLANTAGE_FULL || !plantShouldRefreshNeed(thePottedPlant)) {
            return;
        }

        if (Plant.isAquatic(thePottedPlant.mSeedType)) {
            thePottedPlant.mLastWateredTime = mNowTime;
            thePottedPlant.mPlantNeed = PvzpCommon.randRangeInt(PottedPlantNeed.PLANTNEED_BUGSPRAY,
                    PottedPlantNeed.PLANTNEED_PHONOGRAPH);
        } else {
            thePottedPlant.mTimesFed = 0;
            thePottedPlant.mPlantNeed = PottedPlantNeed.PLANTNEED_NONE;
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:821-836 (ZenGarden::UpdatePlantNeeds). */
    public void updatePlantNeeds() {
        if (mApp.mPlayerInfo == null) {
            return;
        }

        mNowTime = mApp.getNowTime(); // the cached clock is stale on the game selector
        mNowTM = mApp.getLocalTime(mNowTime);

        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(i);
            refreshPlantNeeds(aPottedPlant);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:838-841 (ZenGarden::WasPlantFertilizedInLastHour). */
    public boolean wasPlantFertilizedInLastHour(PottedPlant thePottedPlant) {
        return mNowTime - thePottedPlant.mLastFertilizedTime < 3600;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:843-908 (ZenGarden::GetPlantsNeed). */
    public int getPlantsNeed(PottedPlant thePottedPlant) {
        if (thePottedPlant.mPlantAge != PottedPlantAge.PLANTAGE_SPROUT
                && Plant.isNocturnal(thePottedPlant.mSeedType)
                && thePottedPlant.mWhichZenGarden == GardenType.GARDEN_MAIN) {
            return PottedPlantNeed.PLANTNEED_NONE;
        }
        if (thePottedPlant.mWhichZenGarden == GardenType.GARDEN_WHEELBARROW) {
            return PottedPlantNeed.PLANTNEED_NONE;
        }

        boolean aTooLongSinceWatering = mNowTime - thePottedPlant.mLastWateredTime > 15;
        boolean aTooShortSinceWatering = mNowTime - thePottedPlant.mLastWateredTime < 3;

        if (wasPlantFertilizedInLastHour(thePottedPlant) || wasPlantNeedFulfilledToday(thePottedPlant)) {
            return PottedPlantNeed.PLANTNEED_NONE;
        }
        if (Plant.isAquatic(thePottedPlant.mSeedType)
                && thePottedPlant.mPlantAge != PottedPlantAge.PLANTAGE_SPROUT) {
            if (thePottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_FULL) {
                if (plantShouldRefreshNeed(thePottedPlant)) {
                    return PottedPlantNeed.PLANTNEED_NONE;
                }
                return thePottedPlant.mPlantNeed;
            } else {
                if (thePottedPlant.mWhichZenGarden != GardenType.GARDEN_AQUARIUM) {
                    return PottedPlantNeed.PLANTNEED_NONE;
                }
                return PottedPlantNeed.PLANTNEED_FERTILIZER;
            }
        }
        if (!aTooLongSinceWatering) {
            return PottedPlantNeed.PLANTNEED_NONE;
        }
        if (thePottedPlant.mTimesFed < thePottedPlant.mFeedingsPerGrow) {
            return PottedPlantNeed.PLANTNEED_WATER;
        }
        if (aTooShortSinceWatering) {
            return PottedPlantNeed.PLANTNEED_NONE;
        }
        if (thePottedPlant.mPlantAge != PottedPlantAge.PLANTAGE_FULL) {
            return PottedPlantNeed.PLANTNEED_FERTILIZER;
        }
        if (plantShouldRefreshNeed(thePottedPlant)) {
            return PottedPlantNeed.PLANTNEED_NONE;
        }
        if (thePottedPlant.mPlantNeed != PottedPlantNeed.PLANTNEED_NONE) {
            return thePottedPlant.mPlantNeed;
        }
        return PottedPlantNeed.PLANTNEED_WATER;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:910-1015 (ZenGarden::MouseDownWithFeedingTool). */
    public void mouseDownWithFeedingTool(int x, int y, int theCursorType) {
        Plant aPlantToFeed = null;
        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mHighlighted && aPlant.mPottedPlantIndex != -1) {
                aPlantToFeed = aPlant;
                break;
            }
        }

        if (theCursorType == CursorType.CURSOR_TYPE_CHOCOLATE) {
            zenAssert(mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE]
                    > PlayerInfo.PURCHASE_COUNT_OFFSET, "no chocolate in the inventory");

            GridItem aStinky = getStinky();
            if (aStinky != null && aStinky.mHighlighted) {
                wakeStinky();
                mApp.addPvzpParticle(aStinky.mPosX + 40.0f, aStinky.mPosY + 40.0f, aStinky.mRenderOrder + 1,
                        ParticleEffect.PARTICLE_PRESENT_PICKUP);
                mApp.mPlayerInfo.mLastStinkyChocolateTime = (int) (mNowTime & 0xFFFFFFFFL);
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE]--;

                mApp.playFoley(FoleyType.FOLEY_WAKEUP);
                mApp.playSample(SoundType.SOUND_MINDCONTROLLED);
            }

            if (aPlantToFeed != null) {
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE]--;
                feedChocolateToPlant(aPlantToFeed);
                mApp.playFoley(FoleyType.FOLEY_WAKEUP);
            }
        }

        if (aPlantToFeed != null) {
            GridItem aZenTool = mBoard.mGridItems.dataArrayAlloc();
            aZenTool.mGridItemType = GridItemType.GRIDITEM_ZEN_TOOL;
            aZenTool.mGridX = aPlantToFeed.mPlantCol;
            aZenTool.mGridY = aPlantToFeed.mRow;
            aZenTool.mPosX = aPlantToFeed.mX + 40;
            aZenTool.mPosY = aPlantToFeed.mY + 40;
            aZenTool.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);

            if (theCursorType == CursorType.CURSOR_TYPE_WATERING_CAN) {
                if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_GOLD_WATERINGCAN] != 0) {
                    aZenTool.mPosX = x;
                    aZenTool.mPosY = y;
                    Reanimation aWateringCanReanim = mApp.addReanimation(x, y, 0,
                            ReanimationType.REANIM_ZENGARDEN_WATERINGCAN);
                    aWateringCanReanim.playReanim("anim_water_area", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 8.0f);
                    aZenTool.mGridItemReanimID = mApp.reanimationGetID(aWateringCanReanim);
                    aZenTool.mGridItemState = GridItemState.GRIDITEM_STATE_ZEN_TOOL_GOLD_WATERING_CAN;
                    mApp.playFoley(FoleyType.FOLEY_WATERING);
                } else {
                    Reanimation aWateringCanReanim = mApp.addReanimation(aPlantToFeed.mX + 32, aPlantToFeed.mY, 0,
                            ReanimationType.REANIM_ZENGARDEN_WATERINGCAN);
                    aWateringCanReanim.playReanim("anim_water", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 0.0f);
                    aZenTool.mGridItemReanimID = mApp.reanimationGetID(aWateringCanReanim);
                    aZenTool.mGridItemState = GridItemState.GRIDITEM_STATE_ZEN_TOOL_WATERING_CAN;
                    mApp.playFoley(FoleyType.FOLEY_WATERING);
                }
            } else if (theCursorType == CursorType.CURSOR_TYPE_FERTILIZER) {
                Reanimation aFertilizerReanim = mApp.addReanimation(aPlantToFeed.mX, aPlantToFeed.mY, 0,
                        ReanimationType.REANIM_ZENGARDEN_FERTILIZER);
                aFertilizerReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
                aZenTool.mGridItemReanimID = mApp.reanimationGetID(aFertilizerReanim);
                aZenTool.mGridItemState = GridItemState.GRIDITEM_STATE_ZEN_TOOL_FERTILIZER;
                mApp.playFoley(FoleyType.FOLEY_FERTILIZER);

                zenAssert(mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER]
                        > PlayerInfo.PURCHASE_COUNT_OFFSET, "no fertilizer in the inventory");
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER]--;
            } else if (theCursorType == CursorType.CURSOR_TYPE_BUG_SPRAY) {
                Reanimation aBugSprayReanim = mApp.addReanimation(aPlantToFeed.mX + 54, aPlantToFeed.mY, 0,
                        ReanimationType.REANIM_ZENGARDEN_BUGSPRAY);
                aBugSprayReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
                aZenTool.mGridItemReanimID = mApp.reanimationGetID(aBugSprayReanim);
                aZenTool.mGridItemState = GridItemState.GRIDITEM_STATE_ZEN_TOOL_BUG_SPRAY;
                mApp.playFoley(FoleyType.FOLEY_BUGSPRAY);

                zenAssert(mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY]
                        > PlayerInfo.PURCHASE_COUNT_OFFSET, "no bug spray in the inventory");
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY]--;
            } else if (theCursorType == CursorType.CURSOR_TYPE_PHONOGRAPH) {
                Reanimation aPhonographReanim = mApp.addReanimation(aPlantToFeed.mX + 20, aPlantToFeed.mY + 34, 0,
                        ReanimationType.REANIM_ZENGARDEN_PHONOGRAPH);
                aPhonographReanim.mAnimRate = 20.0f;
                aPhonographReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
                aZenTool.mGridItemReanimID = mApp.reanimationGetID(aPhonographReanim);
                aZenTool.mGridItemState = GridItemState.GRIDITEM_STATE_ZEN_TOOL_PHONOGRAPH;
                mApp.playFoley(FoleyType.FOLEY_PHONOGRAPH);
            }
        }

        mBoard.clearCursor();
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1017-1023 (ZenGarden::FeedChocolateToPlant). */
    public void feedChocolateToPlant(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        aPottedPlant.mLastChocolateTime = mNowTime;
        thePlant.mLaunchCounter = 60;
        mApp.addPvzpParticle(thePlant.mX + 40.0f, thePlant.mY + 40.0f, thePlant.mRenderOrder + 1,
                ParticleEffect.PARTICLE_PRESENT_PICKUP);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1025-1085 (ZenGarden::DoFeedingTool). */
    public void doFeedingTool(int x, int y, int theToolType) {
        if (theToolType == GridItemState.GRIDITEM_STATE_ZEN_TOOL_GOLD_WATERING_CAN) {
            for (Plant aPlant : mBoard.mPlants) {
                if (aPlant.mDead || aPlant.mPottedPlantIndex == -1) {
                    continue;
                }
                if (mBoard.isPlantInGoldWateringCanRange(x, y, aPlant)) {
                    PottedPlant aPottedPlant = pottedPlantFromIndex(aPlant.mPottedPlantIndex);
                    if (getPlantsNeed(aPottedPlant) == PottedPlantNeed.PLANTNEED_WATER) {
                        plantWatered(aPlant);
                    }
                }
            }
            return;
        }

        int aGridX = pixelToGridX(x, y);
        int aGridY = pixelToGridY(x, y);
        Plant aPlant = mBoard.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ZEN_TOOL_ORDER);
        if (aPlant != null && aPlant.mPottedPlantIndex != -1) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(aPlant.mPottedPlantIndex);
            int aNeed = getPlantsNeed(aPottedPlant);
            if (aNeed == PottedPlantNeed.PLANTNEED_WATER
                    && theToolType == GridItemState.GRIDITEM_STATE_ZEN_TOOL_WATERING_CAN) {
                plantWatered(aPlant);
            } else if (aNeed == PottedPlantNeed.PLANTNEED_FERTILIZER
                    && theToolType == GridItemState.GRIDITEM_STATE_ZEN_TOOL_FERTILIZER) {
                plantFertilized(aPlant);
            } else if (aNeed == PottedPlantNeed.PLANTNEED_BUGSPRAY
                    && theToolType == GridItemState.GRIDITEM_STATE_ZEN_TOOL_BUG_SPRAY) {
                plantFulfillNeed(aPlant);
            } else if (aNeed == PottedPlantNeed.PLANTNEED_PHONOGRAPH
                    && theToolType == GridItemState.GRIDITEM_STATE_ZEN_TOOL_PHONOGRAPH) {
                plantFulfillNeed(aPlant);
            }

            if (mBoard.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_FERTILIZE_PLANTS
                    && theToolType == GridItemState.GRIDITEM_STATE_ZEN_TOOL_FERTILIZER) {
                if (allPlantsHaveBeenFertilized()) {
                    mApp.mBoard.mTutorialState = TutorialState.TUTORIAL_ZEN_GARDEN_COMPLETED;
                    mApp.mBoard.displayAdvice("[ADVICE_ZEN_GARDEN_CONTINUE_ADVENTURE]",
                            MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST, AdviceType.ADVICE_NONE);
                    mBoard.mMenuButton.mDisabled = false;
                    mBoard.mMenuButton.mBtnNoDraw = false;
                } else if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER]
                        == PlayerInfo.PURCHASE_COUNT_OFFSET) {
                    mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;
                    mApp.mBoard.displayAdvice("[ADVICE_ZEN_GARDEN_NEED_MORE_FERTILIZER]",
                            MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST, AdviceType.ADVICE_NONE);
                }
            }
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1087-1132 (ZenGarden::MouseDownWithTool). */
    public void mouseDownWithTool(int x, int y, int theCursorType) {
        if (theCursorType == CursorType.CURSOR_TYPE_WHEEELBARROW && getPottedPlantInWheelbarrow() != null) {
            mouseDownWithFullWheelBarrow(x, y);
            mBoard.clearCursor();
            return;
        }

        if (theCursorType == CursorType.CURSOR_TYPE_WATERING_CAN
                || theCursorType == CursorType.CURSOR_TYPE_FERTILIZER
                || theCursorType == CursorType.CURSOR_TYPE_BUG_SPRAY
                || theCursorType == CursorType.CURSOR_TYPE_PHONOGRAPH
                || theCursorType == CursorType.CURSOR_TYPE_CHOCOLATE) {
            mouseDownWithFeedingTool(x, y, theCursorType);
            return;
        }

        Plant aPlant = mBoard.toolHitTest(x, y);
        if (aPlant == null || aPlant.mPottedPlantIndex == -1) {
            mApp.playFoley(FoleyType.FOLEY_DROP);
            mBoard.clearCursor();
            return;
        }

        if (theCursorType == CursorType.CURSOR_TYPE_MONEY_SIGN) {
            mouseDownWithMoneySign(aPlant);
        } else if (theCursorType == CursorType.CURSOR_TYPE_WHEEELBARROW) {
            mouseDownWithEmptyWheelBarrow(aPlant);
            mBoard.clearCursor();
        } else if (theCursorType == CursorType.CURSOR_TYPE_GLOVE) {
            mBoard.mCursorObject.mType = aPlant.mSeedType;
            mBoard.mCursorObject.mImitaterType = aPlant.mImitaterType;
            mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE;
            mBoard.mCursorObject.mGlovePlantID = mBoard.mPlants.dataArrayGetID(aPlant);
            // mBoard.mIgnoreMouseUp = true;
            mApp.playSample(SoundType.SOUND_TAP);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1134-1188 (ZenGarden::MovePlant). */
    public void movePlant(Plant thePlant, int theGridX, int theGridY) {
        if (mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            return;
        }

        int aPosX = mBoard.gridToPixelX(theGridX, theGridY);
        int aPosY = mBoard.gridToPixelY(theGridX, theGridY);
        zenAssert(mBoard.getTopPlantAt(theGridX, theGridY, PlantPriority.TOPPLANT_ANY) == null,
                "zen garden spot occupied");

        // boolean aIsSleeping = thePlant.mIsAsleep; // unused
        thePlant.setSleeping(false);
        Plant aTopPlantAtGrid = mBoard.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow,
                PlantPriority.TOPPLANT_ONLY_UNDER_PLANT);
        if (aTopPlantAtGrid != null) {
            aTopPlantAtGrid.mX = aPosX;
            aTopPlantAtGrid.mY = aPosY;
            aTopPlantAtGrid.mPlantCol = theGridX;
            aTopPlantAtGrid.mRow = theGridY;
            aTopPlantAtGrid.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0, aPosY);
        }
        float aDeltaX = aPosX - thePlant.mX;
        float aDeltaY = aPosY - thePlant.mY;
        thePlant.mX = aPosX;
        thePlant.mY = aPosY;
        thePlant.mPlantCol = theGridX;
        thePlant.mRow = theGridY;
        thePlant.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0, aPosY + 1);

        PvzpParticleSystem aParticle = mApp.particleTryToGet(thePlant.mParticleID);
        if (aParticle != null && aParticle.mEmitterList.mSize != 0) {
            PvzpParticleEmitter aEmitter = aParticle.mParticleHolder.mEmitters
                    .dataArrayGet(aParticle.mEmitterList.getHead().mValue);
            aParticle.systemMove(aEmitter.mSystemCenter.x + aDeltaX, aEmitter.mSystemCenter.y + aDeltaY);
        }

        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        aPottedPlant.mX = theGridX;
        aPottedPlant.mY = theGridY;
        if (thePlant.mState == PlantState.STATE_ZEN_GARDEN_HAPPY) {
            removeHappyEffect(thePlant);
            addHappyEffect(thePlant);
        }

        if (aTopPlantAtGrid != null) {
            mBoard.doPlantingEffects(theGridX, theGridY, aTopPlantAtGrid);
        } else {
            mBoard.doPlantingEffects(theGridX, theGridY, thePlant);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1190-1267 (ZenGarden::PlantPottedDrawHeightOffset). */
    public float plantPottedDrawHeightOffset(int theSeedType, float theScale) {
        float aScaleOffsetFix = 0.0f;
        float aHeightOffset = 0.0f;
        switch (theSeedType) {
            case SeedType.SEED_GRAVEBUSTER:
                aHeightOffset += 50.0f;
                aScaleOffsetFix += 15.0f;
                break;
            case SeedType.SEED_PUFFSHROOM:
                aHeightOffset += 10.0f;
                aScaleOffsetFix += 24.0f;
                break;
            case SeedType.SEED_SUNSHROOM:
                aHeightOffset += 10.0f;
                aScaleOffsetFix += 17.0f;
                break;
            case SeedType.SEED_SCAREDYSHROOM:
                aHeightOffset += 5.0f;
                aScaleOffsetFix += 5.0f;
                break;
            case SeedType.SEED_TANGLEKELP:
                aHeightOffset -= 18.0f;
                aScaleOffsetFix += 20.0f;
                break;
            case SeedType.SEED_SEASHROOM:
                aHeightOffset -= 20.0f;
                aScaleOffsetFix += 15.0f;
                break;
            case SeedType.SEED_LILYPAD:
                aHeightOffset -= 10.0f;
                aScaleOffsetFix += 30.0f;
                break;
            case SeedType.SEED_HYPNOSHROOM:
            case SeedType.SEED_MARIGOLD:
            case SeedType.SEED_PEASHOOTER:
            case SeedType.SEED_REPEATER:
            case SeedType.SEED_LEFTPEATER:
            case SeedType.SEED_SNOWPEA:
            case SeedType.SEED_THREEPEATER:
            case SeedType.SEED_SUNFLOWER:
                aScaleOffsetFix += 10.0f;
                break;
            case SeedType.SEED_STARFRUIT:
                aHeightOffset += 10.0f;
                aScaleOffsetFix += 24.0f;
                break;
            case SeedType.SEED_CABBAGEPULT:
            case SeedType.SEED_MELONPULT:
                aScaleOffsetFix += 10.0f;
                aHeightOffset += 3.0f;
                break;
            case SeedType.SEED_POTATOMINE:
                aScaleOffsetFix += 5.0f;
                break;
            case SeedType.SEED_TORCHWOOD:
                aScaleOffsetFix += 3.0f;
                break;
            case SeedType.SEED_SPIKEWEED:
                aScaleOffsetFix += 10.0f;
                aHeightOffset -= 13.0f;
                break;
            case SeedType.SEED_BLOVER:
                aScaleOffsetFix += 10.0f;
                break;
            case SeedType.SEED_PUMPKINSHELL:
                aScaleOffsetFix += 20.0f;
                break;
            case SeedType.SEED_PLANTERN:
                aScaleOffsetFix -= 1.0f;
                break;
            default:
                break;
        }

        return aHeightOffset + (aScaleOffsetFix * theScale - aScaleOffsetFix);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1269-1277 (ZenGarden::ZenPlantOffsetX). */
    public static float zenPlantOffsetX(PottedPlant thePottedPlant) {
        int aOffsetX = 0;
        if (thePottedPlant.mFacing == PottedPlant.FacingDirection.FACING_LEFT
                && thePottedPlant.mSeedType == SeedType.SEED_POTATOMINE) {
            aOffsetX -= 6;
        }
        return aOffsetX;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1279-1282 (ZenGarden::HasPurchasedStinky). */
    public boolean hasPurchasedStinky() {
        return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] != 0;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1284-1329 (ZenGarden::AddStinky). */
    public void addStinky() {
        if (!hasPurchasedStinky() || mGardenType != GardenType.GARDEN_MAIN) {
            return;
        }

        if (mApp.mPlayerInfo.mHasSeenStinky == 0) {
            mApp.mPlayerInfo.mHasSeenStinky = 1;
            int aTime = (int) (mNowTime & 0xFFFFFFFFL);
            if (aTime == 0) {
                aTime = 1;
            }
            mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = aTime;
        }

        GridItem aStinky = mBoard.mGridItems.dataArrayAlloc();
        aStinky.mGridItemType = GridItemType.GRIDITEM_STINKY;
        aStinky.mPosX = mApp.mPlayerInfo.mStinkyPosX;
        aStinky.mPosY = mApp.mPlayerInfo.mStinkyPosY;
        aStinky.mGoalX = aStinky.mPosX;
        aStinky.mGoalY = aStinky.mPosY;
        Reanimation aReanimStinky = mApp.addReanimation(aStinky.mPosX, aStinky.mPosY, 0, ReanimationType.REANIM_STINKY);
        aReanimStinky.overrideScale(0.8f, 0.8f);
        aStinky.mGridItemReanimID = mApp.reanimationGetID(aReanimStinky);

        if (mApp.mPlayerInfo.mStinkyPosX == 0) {
            stinkyPickGoal(aStinky);
            aStinky.mPosX = aStinky.mGoalX;
            aStinky.mPosY = aStinky.mGoalY;
        }

        if (shouldStinkyBeAwake()) {
            aReanimStinky.playReanim("anim_crawl", ReanimLoopType.REANIM_LOOP, 0, 6.0f);
            aStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_WALKING_LEFT;
        } else {
            aStinky.mPosY = STINKY_SLEEP_POS_Y;
            stinkyFinishFallingAsleep(aStinky, 0);
        }

        aStinky.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0, (int) (aStinky.mPosY - 30.0f));
        aReanimStinky.setPosition(aStinky.mPosX, aStinky.mPosY);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1331-1426 (ZenGarden::StinkyPickGoal). */
    public void stinkyPickGoal(GridItem theStinky) {
        float aCurDistToGoal = PvzpCommon.distance2D(theStinky.mGoalX, theStinky.mGoalY, theStinky.mPosX, theStinky.mPosY);

        Coin aBestCoin = null;
        float aCurWeight = 0.0f;
        for (Coin aCoin : mBoard.mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (!aCoin.mIsBeingCollected && aCoin.mPosY == aCoin.mGroundY) {
                float aWeight = PvzpCommon.distance2D(aCoin.mPosX, aCoin.mPosY + 30.0f, theStinky.mPosX, theStinky.mPosY);
                if (aCoin.mType == CoinType.COIN_GOLD) {
                    aWeight -= 40.0f;
                } else if (aCoin.mType == CoinType.COIN_DIAMOND) {
                    aWeight -= 80.0f;
                }

                float aDistFromLastGoal = PvzpCommon.distance2D(aCoin.mPosX, aCoin.mPosY + 30.0f, theStinky.mGoalX,
                        theStinky.mGoalY);
                if (aDistFromLastGoal < 5.0f) {
                    aWeight -= 20.0f;
                    aWeight += PvzpCommon.pvzpAnimateCurve(3000, 6000, aCoin.mDisappearCounter, 0, -40,
                            PvzpCurves.CURVE_LINEAR);
                }

                if (aBestCoin == null || aWeight < aCurWeight) {
                    aBestCoin = aCoin;
                    aCurWeight = aWeight;
                }
            }
        }

        if (aBestCoin != null) {
            theStinky.mGoalX = aBestCoin.mPosX;
            theStinky.mGoalY = aBestCoin.mPosY + 30.0f;
        } else {
            if (aCurDistToGoal > 10.0f) {
                return;
            }

            PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[Board.MAX_GRID_SIZE_X * Board.MAX_GRID_SIZE_Y];
            int aPickCount = 0;

            SpecialGridPlacement[] aSpecialGrids = getSpecialGridPlacements();
            zenAssert(aSpecialGrids.length < Board.MAX_GRID_SIZE_X * Board.MAX_GRID_SIZE_Y,
                    "special grid table larger than the pick list");

            for (SpecialGridPlacement aGrid : aSpecialGrids) {
                Plant aPlant = mBoard.getTopPlantAt(aGrid.mGridX, aGrid.mGridY, PlantPriority.TOPPLANT_ANY);
                aPicks[aPickCount] = new PvzpWeightedGridArray(aGrid.mPixelX + 15, aGrid.mPixelY + 80, 0);

                if (aPlant != null) {
                    // Upstream computes this in float and truncates on the int assignment.
                    aPicks[aPickCount].mWeight = (int) (2000 - Math.abs(aPicks[aPickCount].mY - theStinky.mPosY));
                } else {
                    aPicks[aPickCount].mWeight = 1;
                }

                aPickCount++;
            }

            PvzpWeightedGridArray aTarget = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPickCount);
            theStinky.mGoalX = aTarget.mX;
            theStinky.mGoalY = aTarget.mY;
        }

        theStinky.mGridItemCounter = 100;
        if (theStinky.mGoalX < theStinky.mPosX
                && theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT) {
            Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
            theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_TURNING_LEFT;
            aStinkyReanim.playReanim("turn", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 6.0f);
            theStinky.mMotionTrailCount = 0;
        } else if (theStinky.mGoalX > theStinky.mPosX
                && theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_LEFT) {
            Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
            theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_TURNING_RIGHT;
            aStinkyReanim.playReanim("turn", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 6.0f);
            theStinky.mMotionTrailCount = 0;
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1428-1438 (ZenGarden::StinkyWakeUp). */
    public void stinkyWakeUp(GridItem theStinky) {
        Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
        aStinkyReanim.playReanim("anim_out", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 6.0f);
        theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_WAKING_UP;

        Reanimation aSleepingReanim = Attachment
                .findReanimAttachment(aStinkyReanim.getTrackInstanceByName("shell").mAttachmentID);
        aSleepingReanim.reanimationDie();

        LawnApp.gLawnApp.mPlayerInfo.mHasWokenStinky = 1;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1440-1445 (ZenGarden::StinkyStartFallingAsleep). */
    public void stinkyStartFallingAsleep(GridItem theStinky) {
        Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
        aStinkyReanim.playReanim("anim_in", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 6.0f);
        theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_FALLING_ASLEEP;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1447-1463 (ZenGarden::StinkyFinishFallingAsleep). */
    public void stinkyFinishFallingAsleep(GridItem theStinky, int theBlendTime) {
        Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
        aStinkyReanim.playReanim("anim_out", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, theBlendTime, 0.0f);
        aStinkyReanim.mAnimRate = 0.0f;

        Reanimation aSleepingReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_SLEEPING);
        aSleepingReanim.mAnimRate = 3.0f;
        aSleepingReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
        Attachment.attachReanim(aStinkyReanim.getTrackInstanceByName("shell").mAttachmentID, aSleepingReanim, 34.0f,
                39.0f);

        theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_SLEEPING;
        if (LawnApp.gLawnApp.mPlayerInfo.mHasWokenStinky == 0) {
            mApp.mBoard.displayAdvice("[ADVICE_STINKY_SLEEPING]", MessageStyle.MESSAGE_STYLE_HINT_LONG,
                    AdviceType.ADVICE_STINKY_SLEEPING);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1465-1492 (ZenGarden::UpdateStinkyMotionTrail). */
    public void updateStinkyMotionTrail(GridItem theStinky, boolean theStinkyHighOnChocolate) {
        Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
        if (!theStinkyHighOnChocolate) {
            theStinky.mMotionTrailCount = 0;
            return;
        }
        if (theStinky.mGridItemState != GridItemState.GRIDITEM_STINKY_WALKING_RIGHT
                && theStinky.mGridItemState != GridItemState.GRIDITEM_STINKY_WALKING_LEFT) {
            theStinky.mMotionTrailCount = 0;
            return;
        }

        if (theStinky.mMotionTrailCount == GridItem.NUM_MOTION_TRAIL_FRAMES) {
            theStinky.mMotionTrailCount--;
        }
        if (theStinky.mMotionTrailCount > 0) {
            System.arraycopy(theStinky.mMotionTrailFrames, 0, theStinky.mMotionTrailFrames, 1,
                    theStinky.mMotionTrailCount);
        }

        theStinky.mMotionTrailFrames[0].mPosX = theStinky.mPosX;
        theStinky.mMotionTrailFrames[0].mPosY = theStinky.mPosY;
        theStinky.mMotionTrailFrames[0].mAnimTime = aStinkyReanim.mAnimTime;
        theStinky.mMotionTrailCount++;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1494-1517 (ZenGarden::StinkyAnimRateUpdate). */
    public void stinkyAnimRateUpdate(GridItem theStinky) {
        Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
        if (isStinkyHighOnChocolate()) {
            if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_LEFT
                    || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT
                    || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_TURNING_RIGHT
                    || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_TURNING_LEFT) {
                aStinkyReanim.mAnimRate = 12.0f;
            }
        } else {
            if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_LEFT
                    || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT
                    || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_TURNING_RIGHT
                    || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_TURNING_LEFT) {
                aStinkyReanim.mAnimRate = 6.0f;
            }
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1519-1523 (ZenGarden::ResetStinkyTimers). */
    public void resetStinkyTimers() {
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = 2;
        mApp.mPlayerInfo.mLastStinkyChocolateTime = 0;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1525-1721 (ZenGarden::StinkyUpdate). */
    public void stinkyUpdate(GridItem theStinky) {
        Reanimation aStinkyReanim = mApp.reanimationGet(theStinky.mGridItemReanimID);
        boolean aStinkyHighOnChocolate = isStinkyHighOnChocolate();
        updateStinkyMotionTrail(theStinky, aStinkyHighOnChocolate);

        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_FALLING_ASLEEP) {
            if (aStinkyReanim.mLoopCount > 0) {
                stinkyFinishFallingAsleep(theStinky, 20);
            }
            return;
        }

        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_SLEEPING) {
            Reanimation aSleepingReanim = Attachment
                    .findReanimAttachment(aStinkyReanim.getTrackInstanceByName("shell").mAttachmentID);
            zenAssert(aSleepingReanim != null, "sleeping attachment missing");

            if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_CHOCOLATE) {
                aSleepingReanim.assignRenderGroupToPrefix("z", Reanimation.RENDER_GROUP_HIDDEN);
            } else {
                aSleepingReanim.assignRenderGroupToPrefix("z", Reanimation.RENDER_GROUP_NORMAL);
            }

            if (shouldStinkyBeAwake()) {
                stinkyWakeUp(theStinky);
            }
            return;
        }

        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WAKING_UP) {
            if (aStinkyReanim.mLoopCount > 0) {
                theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_WALKING_LEFT;
                aStinkyReanim.playReanim("anim_crawl", ReanimLoopType.REANIM_LOOP, 10, 6.0f);
                stinkyPickGoal(theStinky);
            }
            return;
        }

        if (!shouldStinkyBeAwake()) {
            if (theStinky.mPosY < STINKY_SLEEP_POS_Y) {
                if (theStinky.mGoalY != STINKY_SLEEP_POS_Y) {
                    theStinky.mGoalY = STINKY_SLEEP_POS_Y + 10.0f;
                }
            } else {
                if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_LEFT) {
                    stinkyStartFallingAsleep(theStinky);
                    return;
                } else if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT) {
                    theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_TURNING_LEFT;
                    aStinkyReanim.playReanim("turn", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 6.0f);
                    theStinky.mMotionTrailCount = 0;
                    theStinky.mGoalX = theStinky.mPosX;
                    theStinky.mGoalY = theStinky.mPosY;
                    return;
                }
            }
        }

        if (theStinky.mGridItemCounter > 0) {
            theStinky.mGridItemCounter--;
        }

        for (Coin aCoin : mBoard.mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (!aCoin.mIsBeingCollected && PvzpCommon.distance2D(aCoin.mPosX, aCoin.mPosY + 30.0f, theStinky.mPosX,
                    theStinky.mPosY) < 20.0f) {
                aCoin.playCollectSound();
                aCoin.collect();
            }
        }

        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_LEFT
                || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT) {
            if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_CHOCOLATE && !isStinkyHighOnChocolate()) {
                if (!aStinkyReanim.isAnimPlaying("anim_idle")) {
                    aStinkyReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 10, 6.0f);
                }
            } else if (!aStinkyReanim.isAnimPlaying("anim_crawl")) {
                aStinkyReanim.playReanim("anim_crawl", ReanimLoopType.REANIM_LOOP, 10, 6.0f);
            }
        }

        float aDeltaX = theStinky.mPosX - theStinky.mGoalX;
        float aDeltaY = theStinky.mPosY - theStinky.mGoalY;
        float aSpeedY = 0.5f;
        float aSpeedX = aStinkyReanim.getTrackVelocity("_ground") * 15.0f;
        if (aStinkyHighOnChocolate) {
            aSpeedY = 1.0f;
            aSpeedX = Math.max(aSpeedX, 0.5f);
        } else if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_CHOCOLATE) {
            aSpeedY = 0.0f;
            aSpeedX = 0.0f;
        }
        aSpeedY *= PvzpCommon.pvzpAnimateCurveFloatTime(20.0f, 5.0f, Math.abs(aDeltaY), 1.0f, 0.2f,
                PvzpCurves.CURVE_LINEAR);
        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_LEFT) {
            theStinky.mPosX -= aSpeedX;
            if (theStinky.mPosX < theStinky.mGoalX) {
                theStinky.mPosX = theStinky.mGoalX;
            }
        } else if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT) {
            theStinky.mPosX += aSpeedX;
            if (theStinky.mPosX > theStinky.mGoalX) {
                theStinky.mPosX = theStinky.mGoalX;
            }
        }

        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_LEFT
                || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT) {
            if (Math.abs(aDeltaY) < aSpeedY) {
                theStinky.mPosY = theStinky.mGoalY;
            } else if (aDeltaY > 0.0f) {
                theStinky.mPosY -= aSpeedY;
            } else {
                theStinky.mPosY += aSpeedY;
            }

            if (Math.abs(aDeltaX) < 5.0f && Math.abs(aDeltaY) < 5.0f) {
                stinkyPickGoal(theStinky);
            } else if (theStinky.mGridItemCounter == 0) {
                stinkyPickGoal(theStinky);
            }
        }

        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_TURNING_LEFT) {
            if (aStinkyReanim.mLoopCount > 0) {
                theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_WALKING_LEFT;
                aStinkyReanim.playReanim("anim_crawl", ReanimLoopType.REANIM_LOOP, 10, 6.0f);
            }
        } else if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_TURNING_RIGHT) {
            if (aStinkyReanim.mLoopCount > 0) {
                theStinky.mGridItemState = GridItemState.GRIDITEM_STINKY_WALKING_RIGHT;
                aStinkyReanim.playReanim("anim_crawl", ReanimLoopType.REANIM_LOOP, 10, 6.0f);
            }
        }

        stinkyAnimRateUpdate(theStinky);
        if (theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_WALKING_RIGHT
                || theStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_TURNING_LEFT) {
            aStinkyReanim.overrideScale(-0.8f, 0.8f);
            aStinkyReanim.setPosition(theStinky.mPosX + 69.0f, theStinky.mPosY);
        } else {
            aStinkyReanim.overrideScale(0.8f, 0.8f);
            aStinkyReanim.setPosition(theStinky.mPosX, theStinky.mPosY);
        }

        theStinky.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, 0,
                (int) (theStinky.mPosY - 30.0f));
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1723-1741 (ZenGarden::ZenToolUpdate). */
    public void zenToolUpdate(GridItem theZenTool) {
        Reanimation aToolReanim = mApp.reanimationTryToGet(theZenTool.mGridItemReanimID);
        if (aToolReanim == null) {
            return;
        }

        int aPlayTime = 1;
        if (theZenTool.mGridItemState == GridItemState.GRIDITEM_STATE_ZEN_TOOL_PHONOGRAPH) {
            aPlayTime = 2;
        }
        if (aToolReanim.mLoopCount >= aPlayTime) {
            doFeedingTool((int) theZenTool.mPosX, (int) theZenTool.mPosY, theZenTool.mGridItemState);
            theZenTool.gridItemDie();
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1743-1808 (ZenGarden::ZenGardenUpdate). */
    public void zenGardenUpdate() {
        if (mApp.getDialog(Dialogs.DIALOG_STORE) != null) {
            return;
        }

        // Cache the current time and its broken-down form once per frame to avoid repeated calls.
        mNowTime = mApp.getNowTime();
        mNowTM = mApp.getLocalTime(mNowTime);

        mApp.updateCrazyDave();
        if (mBoard.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_NORMAL) {
            mBoard.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
            mBoard.mChallenge.mChallengeStateCounter = 3000;
        } else if (mApp.mBoard.mTutorialState == TutorialState.TUTORIAL_OFF) {
            if (mBoard.mChallenge.mChallengeStateCounter > 0) {
                mBoard.mChallenge.mChallengeStateCounter--;
            }
            if (mBoard.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_NORMAL
                    && mBoard.mChallenge.mChallengeStateCounter == 0) {
                mBoard.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_ZEN_FADING;
                mBoard.mChallenge.mChallengeStateCounter = 50;
            }
        }

        updatePlantNeeds();
        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mPottedPlantIndex != -1) {
                pottedPlantUpdate(aPlant);
            }
        }
        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                zenToolUpdate(aGridItem);
            } else if (aGridItem.mGridItemType == GridItemType.GRIDITEM_STINKY) {
                stinkyUpdate(aGridItem);
            }
        }

        if (mBoard.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_KEEP_WATERING
                && countPlantsNeedingFertilizer() > 0) {
            mBoard.displayAdvice("[ADVICE_ZEN_GARDEN_VISIT_STORE]", MessageStyle.MESSAGE_STYLE_HINT_TALL_LONG,
                    AdviceType.ADVICE_NONE);
            mBoard.mTutorialState = TutorialState.TUTORIAL_ZEN_GARDEN_VISIT_STORE;
            mBoard.mStoreButton.mDisabled = false;
            mBoard.mStoreButton.mBtnNoDraw = false;
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1810-1822 (ZenGarden::GetStinky). */
    public GridItem getStinky() {
        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_STINKY) {
                return aGridItem;
            }
        }
        return null;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1824-1921 (ZenGarden::GotoNextGarden). */
    public void gotoNextGarden() {
        leaveGarden();
        mBoard.clearAdvice(AdviceType.ADVICE_NONE);
        mApp.crazyDaveDie();
        mBoard.mPlants.dataArrayFreeAll();
        mBoard.mCoins.dataArrayFreeAll();
        mApp.mEffectSystem.effectSystemFreeAll();

        boolean aGoToTree = false;
        if (mGardenType == GardenType.GARDEN_MAIN) {
            if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_MUSHROOM_GARDEN] != 0) {
                mGardenType = GardenType.GARDEN_MUSHROOM;
                mBoard.mBackground = BackgroundType.BACKGROUND_MUSHROOM_GARDEN;
            } else if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_AQUARIUM_GARDEN] != 0) {
                mGardenType = GardenType.GARDEN_AQUARIUM;
                mBoard.mBackground = BackgroundType.BACKGROUND_ZOMBIQUARIUM;
            } else if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] != 0) {
                aGoToTree = true;
            }
        } else if (mGardenType == GardenType.GARDEN_MUSHROOM) {
            if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_AQUARIUM_GARDEN] != 0) {
                mGardenType = GardenType.GARDEN_AQUARIUM;
                mBoard.mBackground = BackgroundType.BACKGROUND_ZOMBIQUARIUM;
            } else if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] != 0) {
                aGoToTree = true;
            } else {
                mGardenType = GardenType.GARDEN_MAIN;
                mBoard.mBackground = BackgroundType.BACKGROUND_GREENHOUSE;
            }
        } else if (mGardenType == GardenType.GARDEN_AQUARIUM) {
            if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] != 0) {
                aGoToTree = true;
            } else {
                mGardenType = GardenType.GARDEN_MAIN;
                mBoard.mBackground = BackgroundType.BACKGROUND_GREENHOUSE;
            }
        }
        if (aGoToTree) {
            mApp.mResourceManager.releaseTrackedResources(mLoadedResourceNames);
            mApp.killBoard();
            mApp.preNewGame(GameMode.GAMEMODE_TREE_OF_WISDOM, false);
            return;
        }

        mApp.mResourceManager.releaseTrackedResources(mLoadedResourceNames);

        if (mBoard.mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN) {
            mLoadedResourceNames.add("DelayLoad_MushroomGarden");
        } else if (mBoard.mBackground == BackgroundType.BACKGROUND_GREENHOUSE) {
            mLoadedResourceNames.add("DelayLoad_GreenHouseGarden");
            mLoadedResourceNames.add("DelayLoad_GreenHouseOverlay");
        } else if (mBoard.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
            mLoadedResourceNames.add("DelayLoad_Zombiquarium");
            mLoadedResourceNames.add("DelayLoad_GreenHouseOverlay");
        } else {
            zenAssert(false, "unexpected zen garden background");
        }

        for (String resource : mLoadedResourceNames) {
            ((PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(resource);
        }

        if (mBoard.mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                || mBoard.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
            if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_WHEEL_BARROW] == 0) {
                mBoard.displayAdvice("[ADVICE_NEED_WHEELBARROW]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                        AdviceType.ADVICE_NEED_WHEELBARROW);
            }
        }

        zenGardenInitLevel();
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1923-1948 (ZenGarden::MouseDownWithFullWheelBarrow). */
    public void mouseDownWithFullWheelBarrow(int x, int y) {
        PottedPlant aPottedPlant = getPottedPlantInWheelbarrow();
        zenAssert(aPottedPlant != null, "wheelbarrow is empty");

        if (mApp.mZenGarden.mGardenType == GardenType.GARDEN_AQUARIUM && !Plant.isAquatic(aPottedPlant.mSeedType)) {
            mBoard.displayAdvice("[ZEN_ONLY_AQUATIC_PLANTS]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                    AdviceType.ADVICE_NONE);
            return;
        }

        int aGridX = mBoard.pixelToGridX(x, y);
        int aGridY = mBoard.pixelToGridY(x, y);
        if (aGridX == -1 || aGridY == -1
                || mBoard.canPlantAt(aGridX, aGridY, aPottedPlant.mSeedType) != PlantingReason.PLANTING_OK) {
            return;
        }

        aPottedPlant.mWhichZenGarden = mGardenType;
        aPottedPlant.mX = aGridX;
        aPottedPlant.mY = aGridY;
        int aPottedPlantIndex = pottedPlantIndex(aPottedPlant);
        Plant aPlant = placePottedPlant(aPottedPlantIndex);
        mBoard.doPlantingEffects(aPottedPlant.mX, aPottedPlant.mY, aPlant);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1950-1958 (ZenGarden::MouseDownWithEmptyWheelBarrow). */
    public void mouseDownWithEmptyWheelBarrow(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        removePottedPlant(thePlant);
        aPottedPlant.mWhichZenGarden = GardenType.GARDEN_WHEELBARROW;
        aPottedPlant.mX = 0;
        aPottedPlant.mY = 0;
        mApp.playFoley(FoleyType.FOLEY_PLANT);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1960-1971 (ZenGarden::GetPottedPlantInWheelbarrow). */
    public PottedPlant getPottedPlantInWheelbarrow() {
        for (int i = 0; i < mApp.mPlayerInfo.mNumPottedPlants; i++) {
            PottedPlant aPottedPlant = pottedPlantFromIndex(i);
            if (aPottedPlant.mWhichZenGarden == GardenType.GARDEN_WHEELBARROW) {
                return aPottedPlant;
            }
        }
        return null;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1973-1989 (ZenGarden::GetSpecialGridPlacements). */
    public SpecialGridPlacement[] getSpecialGridPlacements() {
        if (mBoard.mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN) {
            return gMushroomGridPlacement;
        }
        if (mBoard.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
            return gAquariumGridPlacement;
        }
        if (mBoard.mBackground == BackgroundType.BACKGROUND_GREENHOUSE) {
            return gGreenhouseGridPlacement;
        }
        zenAssert(false, "unexpected zen garden background");
        return new SpecialGridPlacement[0];
    }

    /** Ported from src/Lawn/ZenGarden.cpp:1991-2001 (ZenGarden::PixelToGridX). */
    public int pixelToGridX(int theX, int theY) {
        for (SpecialGridPlacement aGrid : getSpecialGridPlacements()) {
            if (theX >= aGrid.mPixelX && theX <= aGrid.mPixelX + 80 && theY >= aGrid.mPixelY
                    && theY <= aGrid.mPixelY + 85) {
                return aGrid.mGridX;
            }
        }
        return -1;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2003-2013 (ZenGarden::PixelToGridY). */
    public int pixelToGridY(int theX, int theY) {
        for (SpecialGridPlacement aGrid : getSpecialGridPlacements()) {
            if (theX >= aGrid.mPixelX && theX <= aGrid.mPixelX + 80 && theY >= aGrid.mPixelY
                    && theY <= aGrid.mPixelY + 85) {
                return aGrid.mGridY;
            }
        }
        return -1;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2015-2025 (ZenGarden::GridToPixelX). */
    public int gridToPixelX(int theGridX, int theGridY) {
        for (SpecialGridPlacement aGrid : getSpecialGridPlacements()) {
            if (theGridX == aGrid.mGridX && theGridY == aGrid.mGridY) {
                return aGrid.mPixelX;
            }
        }
        return -1;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2027-2037 (ZenGarden::GridToPixelY). */
    public int gridToPixelY(int theGridX, int theGridY) {
        for (SpecialGridPlacement aGrid : getSpecialGridPlacements()) {
            if (theGridX == aGrid.mGridX && theGridY == aGrid.mGridY) {
                return aGrid.mPixelY;
            }
        }
        return -1;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2039-2058 (ZenGarden::DrawBackdrop). */
    public void drawBackdrop(Graphics g) {
        if (mGardenType != GardenType.GARDEN_AQUARIUM) {
            return;
        }

        if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW
                || mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WHEEELBARROW
                || mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE) {
            for (SpecialGridPlacement aGrid : getSpecialGridPlacements()) {
                if (mBoard.getTopPlantAt(aGrid.mGridX, aGrid.mGridY, PlantPriority.TOPPLANT_ZEN_TOOL_ORDER) == null) {
                    PvzpCommon.pvzpDrawImageCelScaled(g, Resources.getImageById(ImageID.IMAGE_PLANTSHADOW),
                            aGrid.mPixelX - 35, aGrid.mPixelY + 33, 0, 0, 1.7f, 1.7f);
                }
            }
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2060-2067 (ZenGarden::ShowTutorialArrowOnWateringCan). */
    public void showTutorialArrowOnWateringCan() {
        Rect aButtonRect = mBoard.getShovelButtonRect();
        mBoard.getZenButtonRect(GameObjectType.OBJECT_TYPE_WATERING_CAN, aButtonRect);
        mBoard.tutorialArrowShow(aButtonRect.mX + 10, aButtonRect.mY + 10);
        mBoard.displayAdvice("[ADVICE_ZEN_GARDEN_PICK_UP_WATER]", MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG,
                AdviceType.ADVICE_NONE);
        mBoard.mTutorialState = TutorialState.TUTORIAL_ZEN_GARDEN_PICKUP_WATER;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2069-2100 (ZenGarden::AdvanceCrazyDaveDialog). */
    public void advanceCrazyDaveDialog() {
        if (mApp.mCrazyDaveMessageIndex == -1 || mApp.getDialog(Dialogs.DIALOG_STORE) != null
                || mApp.getDialog(Dialogs.DIALOG_ZEN_SELL) != null) {
            return;
        }

        if (mApp.mCrazyDaveMessageIndex == 2104) {
            showTutorialArrowOnWateringCan();
        }

        if (!mApp.advanceCrazyDaveText()) {
            mApp.crazyDaveLeave();
            return;
        }

        if (mApp.mCrazyDaveMessageIndex == 2102 && mApp.mPlayerInfo.mNumPottedPlants == 0) {
            for (int i = 0; i < 2; i++) {
                PottedPlant aPottedPlant = new PottedPlant();
                aPottedPlant.initializePottedPlant(SeedType.SEED_MARIGOLD);
                aPottedPlant.mDrawVariation = PvzpCommon.randRangeInt(DrawVariation.VARIATION_MARIGOLD_WHITE,
                        DrawVariation.VARIATION_MARIGOLD_LIGHT_GREEN);
                aPottedPlant.mFeedingsPerGrow = 3;
                mApp.mZenGarden.addPottedPlant(aPottedPlant);
            }
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2102-2161 (ZenGarden::MouseDownZenGarden). */
    public boolean mouseDownZenGarden(int x, int y, int theClickCount, HitResult theHitResult) {
        if (mBoard.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_ZEN_FADING) {
            mBoard.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        }
        mBoard.mChallenge.mChallengeStateCounter = 3000;

        if (theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_STINKY
                && mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL) {
            wakeStinky();
        } else if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_GLOVE) {
            if (mBoard.canUseGameObject(GameObjectType.OBJECT_TYPE_WHEELBARROW)) {
                Rect aButtonRect = mBoard.getShovelButtonRect();
                mBoard.getZenButtonRect(GameObjectType.OBJECT_TYPE_WHEELBARROW, aButtonRect);

                PottedPlant aPottedPlant = getPottedPlantInWheelbarrow();
                if (aButtonRect.contains(x, y) && aPottedPlant != null) {
                    mBoard.clearCursor();
                    mBoard.mCursorObject.mType = aPottedPlant.mSeedType;
                    mBoard.mCursorObject.mImitaterType = SeedType.SEED_NONE;
                    mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW;
                    return true;
                }
            }
        } else if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE) {
            if (mBoard.canUseGameObject(GameObjectType.OBJECT_TYPE_WHEELBARROW)) {
                Rect aButtonRect = mBoard.getShovelButtonRect();
                mBoard.getZenButtonRect(GameObjectType.OBJECT_TYPE_WHEELBARROW, aButtonRect);

                Plant aPlant = mBoard.mPlants.dataArrayTryToGet(mBoard.mCursorObject.mGlovePlantID);
                if (aPlant != null && aButtonRect.contains(x, y) && getPottedPlantInWheelbarrow() == null) {
                    mouseDownWithEmptyWheelBarrow(aPlant);
                    mBoard.clearCursor();
                    return true;
                }
            }
        } else if (theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_NONE
                && mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL
                && mGardenType == GardenType.GARDEN_AQUARIUM && theClickCount <= -1) {
            mApp.playSample(SoundType.SOUND_TAPGLASS);
        }

        if (mApp.mCrazyDaveMessageIndex != -1) {
            advanceCrazyDaveDialog();
            return true;
        }

        return false;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2163-2215 (ZenGarden::SetPlantAnimSpeed). */
    public void setPlantAnimSpeed(Plant thePlant) {
        Reanimation aBodyReanim = mApp.reanimationGet(thePlant.mBodyReanimID);
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        boolean aPlantHighOnChocolate = plantHighOnChocolate(aPottedPlant);
        boolean aPlantAtHighRate = aBodyReanim.mAnimRate >= 25.0f;
        if (aPlantAtHighRate == aPlantHighOnChocolate) {
            return;
        }

        float aTargetRate;
        if (thePlant.mSeedType == SeedType.SEED_PEASHOOTER || thePlant.mSeedType == SeedType.SEED_SNOWPEA
                || thePlant.mSeedType == SeedType.SEED_REPEATER || thePlant.mSeedType == SeedType.SEED_LEFTPEATER
                || thePlant.mSeedType == SeedType.SEED_GATLINGPEA || thePlant.mSeedType == SeedType.SEED_SPLITPEA
                || thePlant.mSeedType == SeedType.SEED_THREEPEATER || thePlant.mSeedType == SeedType.SEED_MARIGOLD) {
            aTargetRate = PvzpCommon.randRangeFloat(15.0f, 20.0f);
        } else if (thePlant.mSeedType == SeedType.SEED_POTATOMINE) {
            aTargetRate = 12.0f;
        } else {
            aTargetRate = PvzpCommon.randRangeFloat(10.0f, 15.0f);
        }

        if (aPlantHighOnChocolate) {
            aTargetRate *= 2.0f;
            aTargetRate = Math.max(25.0f, aTargetRate);
        }

        aBodyReanim.mAnimRate = aTargetRate;
        Reanimation aHeadReanim = mApp.reanimationTryToGet(thePlant.mHeadReanimID);
        Reanimation aHeadReanim2 = mApp.reanimationTryToGet(thePlant.mHeadReanimID2);
        Reanimation aHeadReanim3 = mApp.reanimationTryToGet(thePlant.mHeadReanimID3);
        if (aHeadReanim != null) {
            aHeadReanim.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim.mAnimTime = aBodyReanim.mAnimTime;
        }
        if (aHeadReanim2 != null) {
            aHeadReanim2.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim2.mAnimTime = aBodyReanim.mAnimTime;
        }
        if (aHeadReanim3 != null) {
            aHeadReanim3.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim3.mAnimTime = aBodyReanim.mAnimTime;
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2217-2226 (ZenGarden::PlantGetMinutesSinceHappy). */
    public int plantGetMinutesSinceHappy(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        int aMinutes = (int) ((mNowTime - aPottedPlant.mLastNeedFulfilledTime) / 60);
        if (plantHighOnChocolate(aPottedPlant)) {
            aMinutes = 0;
        }
        return aMinutes;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2228-2252 (ZenGarden::PlantUpdateProduction). */
    public void plantUpdateProduction(Plant thePlant) {
        thePlant.mLaunchCounter--;
        setPlantAnimSpeed(thePlant);
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        if (plantHighOnChocolate(aPottedPlant)) {
            thePlant.mLaunchCounter--;
        }

        if (thePlant.mLaunchCounter <= 0) {
            plantSetLaunchCounter(thePlant);
            mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);

            int aCoinHit = Common.rand(1000);
            aCoinHit += PvzpCommon.pvzpAnimateCurve(5, 30, plantGetMinutesSinceHappy(thePlant), 0, 80,
                    PvzpCurves.CURVE_LINEAR);
            int aCoinType = CoinType.COIN_SILVER;
            if (aCoinHit < 100) {
                aCoinType = CoinType.COIN_GOLD;
            }
            mBoard.addCoin(thePlant.mX, thePlant.mY, aCoinType, CoinMotion.COIN_MOTION_COIN);
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2254-2260 (ZenGarden::ResetPlantTimers). */
    public void resetPlantTimers(PottedPlant thePottedPlant) {
        thePottedPlant.mLastWateredTime = 0;
        thePottedPlant.mLastNeedFulfilledTime = 0;
        thePottedPlant.mLastFertilizedTime = 0;
        thePottedPlant.mLastChocolateTime = 0;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2262-2287 (ZenGarden::PottedPlantUpdate). */
    public void pottedPlantUpdate(Plant thePlant) {
        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        if (aPottedPlant.mLastWateredTime > mNowTime || aPottedPlant.mLastNeedFulfilledTime > mNowTime
                || aPottedPlant.mLastFertilizedTime > mNowTime || aPottedPlant.mLastChocolateTime > mNowTime) {
            resetPlantTimers(aPottedPlant);
        }

        if (thePlant.mIsAsleep) {
            return;
        }
        if (thePlant.mStateCountdown > 0) {
            thePlant.mStateCountdown--;
        }
        if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_FULL && wasPlantNeedFulfilledToday(aPottedPlant)) {
            plantUpdateProduction(thePlant);
        }
        updatePlantEffectState(thePlant);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2289-2325 (ZenGarden::DrawPlantOverlay). */
    public void drawPlantOverlay(Graphics g, Plant thePlant) {
        if (thePlant.mPottedPlantIndex == -1) {
            return;
        }

        PottedPlant aPottedPlant = pottedPlantFromIndex(thePlant.mPottedPlantIndex);
        int aPlantNeed = mApp.mZenGarden.getPlantsNeed(aPottedPlant);
        if (aPlantNeed == PottedPlantNeed.PLANTNEED_NONE) {
            return;
        }

        g.drawImage(Resources.getImageById(ImageID.IMAGE_PLANTSPEECHBUBBLE), 50, 0);
        switch (aPlantNeed) {
            case PottedPlantNeed.PLANTNEED_FERTILIZER:
                g.drawImageCel(Resources.getImageById(ImageID.IMAGE_ZEN_NEED_ICONS), 61, 7, 0, 0);
                break;

            case PottedPlantNeed.PLANTNEED_BUGSPRAY:
                g.drawImageCel(Resources.getImageById(ImageID.IMAGE_ZEN_NEED_ICONS), 61, 7, 1, 0);
                break;

            case PottedPlantNeed.PLANTNEED_PHONOGRAPH:
                g.drawImageCel(Resources.getImageById(ImageID.IMAGE_ZEN_NEED_ICONS), 60, 7, 2, 0);
                break;

            case PottedPlantNeed.PLANTNEED_WATER:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_WATERDROP), 67, 9);
                break;

            case PottedPlantNeed.PLANTNEED_NONE:
                break;

            default:
                break;
        }
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2327-2335 (ZenGarden::WakeStinky). */
    public void wakeStinky() {
        int aTime = (int) (mNowTime & 0xFFFFFFFFL);
        if (aTime == 0) {
            aTime = 1;
        }
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = aTime;
        mApp.playSample(SoundType.SOUND_TAP);
        mBoard.clearAdvice(AdviceType.ADVICE_STINKY_SLEEPING);
        LawnApp.gLawnApp.mPlayerInfo.mHasWokenStinky = 1;
    }

    /**
     * Ported from src/Lawn/ZenGarden.cpp:2337-2341 (ZenGarden::IsStinkyHighOnChocolate).
     *
     * <p>Unsigned arithmetic extends the limit to 2106 and handles wrap-around correctly so that
     * after 2106 is also OK.
     */
    public boolean isStinkyHighOnChocolate() {
        return unsignedTimeDiff(mNowTime, mApp.mPlayerInfo.mLastStinkyChocolateTime) < 3600;
    }

    /**
     * Java-only helper for the upstream {@code uint32_t} time differences
     * (ZenGarden.cpp:2339, :2361); the subtraction wraps modulo 2^32 exactly as upstream.
     */
    private static long unsignedTimeDiff(long theNowTime, int theEarlierTime) {
        return ((theNowTime & 0xFFFFFFFFL) - (theEarlierTime & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2343-2346 (ZenGarden::PlantHighOnChocolate). */
    public boolean plantHighOnChocolate(PottedPlant thePottedPlant) {
        return mNowTime - thePottedPlant.mLastChocolateTime < 300;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2348-2352 (ZenGarden::IsStinkySleeping). */
    public boolean isStinkySleeping() {
        GridItem aStinky = getStinky();
        return aStinky != null && aStinky.mGridItemState == GridItemState.GRIDITEM_STINKY_SLEEPING;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2354-2363 (ZenGarden::ShouldStinkyBeAwake). */
    public boolean shouldStinkyBeAwake() {
        if (isStinkyHighOnChocolate()) {
            return true;
        }
        // Unsigned arithmetic extends the limit to 2106 and handles wrap-around correctly.
        return unsignedTimeDiff(mNowTime,
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL]) < 180;
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2365-2396 (ZenGarden::OpenStore). */
    public void openStore() {
        leaveGarden();
        StoreScreen aStore = mApp.showStoreScreen();
        if (mBoard.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_VISIT_STORE) {
            aStore.setupForIntro(2600);
            mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;
        }
        aStore.mBackButton.setLabel("[STORE_BACK_TO_GAME]");
        aStore.mPage = StorePages.STORE_PAGE_ZEN1;
        // PORT-NOTE: ZenGarden.cpp:2376-2395 — upstream reads mGoToTreeNow and runs the whole garden
        // re-entry after WaitForResult(true) returned, i.e. after autoKill removed the store
        // (Dialog.cpp:410-441); this port's wait does not pump (sexy/Dialog.java:506), so the
        // re-entry runs from the screen's close path instead.
        aStore.onStoreClosed(store -> {
            if (store.mGoToTreeNow) {
                mApp.killBoard();
                mApp.preNewGame(GameMode.GAMEMODE_TREE_OF_WISDOM, false);
            } else {
                mNowTime = mApp.getNowTime();
                mNowTM = mApp.getLocalTime(mNowTime);

                mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_ZEN_GARDEN);
                if (mBoard.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_VISIT_STORE) {
                    mBoard.displayAdvice("[ADVICE_ZEN_GARDEN_FERTILIZE]", MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG,
                            AdviceType.ADVICE_NONE);
                    mBoard.mTutorialState = TutorialState.TUTORIAL_ZEN_GARDEN_FERTILIZE_PLANTS;
                }
                addStinky();
            }
        });
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2398-2408 (ZenGarden::SetupForZenTutorial). */
    public void setupForZenTutorial() {
        mBoard.mMenuButton.setLabel("[CONTINUE_BUTTON]");
        mBoard.mStoreButton.mDisabled = true;
        mBoard.mStoreButton.mBtnNoDraw = true;
        mBoard.mMenuButton.mDisabled = true;
        mBoard.mMenuButton.mBtnNoDraw = true;

        mApp.crazyDaveEnter();
        mApp.crazyDaveTalkIndex(2100);
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2410-2424 (ZenGarden::PickRandomSeedType). */
    public static int pickRandomSeedType() {
        int[] aSeedList = new int[40];
        int aSeedCount = 0;
        for (int i = 0; i < 40; i++) {
            int aSeedType = i;
            if (aSeedType != SeedType.SEED_MARIGOLD && aSeedType != SeedType.SEED_FLOWERPOT) {
                aSeedList[aSeedCount] = aSeedType;
                aSeedCount++;
            }
        }
        return aSeedList[Common.rand(aSeedCount)];
    }

    /** Ported from src/Lawn/ZenGarden.cpp:2426-2461 (ZenGarden::LeaveGarden). */
    public void leaveGarden() {
        if (mBoard == null) {
            // Java-only guard: Board::DisposeBoard tests the *new* game mode (Board.cpp:246-248) and
            // nulls the garden's board afterwards (:253), so killing a level right after the mode
            // switch to GAMEMODE_CHALLENGE_ZEN_GARDEN reaches LeaveGarden with an unbound board.
            // Upstream dereferences that null board here; the port keeps the state untouched instead
            // (there is no garden to tear down when the board was never bound).
            return;
        }

        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                doFeedingTool((int) aGridItem.mPosX, (int) aGridItem.mPosY, aGridItem.mGridItemState);
                aGridItem.gridItemDie();
            } else if (aGridItem.mGridItemType == GridItemType.GRIDITEM_STINKY) {
                mApp.mPlayerInfo.mStinkyPosX = (int) aGridItem.mPosX;
                mApp.mPlayerInfo.mStinkyPosY = (int) aGridItem.mPosY;
                aGridItem.gridItemDie();
            }
        }
        for (Coin aCoin : mBoard.mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (aCoin.mIsBeingCollected) {
                aCoin.scoreCoin();
            } else {
                aCoin.die();
            }
        }
    }

    /**
     * Ported from src/Lawn/ZenGarden.h:112 ({@code CanUseGameObject}).
     *
     * <p>Declared by the upstream header but never defined in ZenGarden.cpp (dead declaration: the
     * only {@code CanUseGameObject} implementation is {@code Board::CanUseGameObject}, which the
     * garden's call sites use through {@code mBoard}); this forwards there so the upstream member
     * exists with the semantics it is used with.
     */
    public boolean canUseGameObject(int theGameObjectType) {
        return mBoard.canUseGameObject(theGameObjectType);
    }
}
