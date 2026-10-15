/*
 * Ported from src/Lawn/Cutscene.h:1-116 and src/Lawn/CutScene.cpp:1-2325.
 *
 * The level-intro / ZOMBIES-WON / Intro-cinematic / Crazy-Dave-upsell state machine. Field and
 * method names follow the upstream declaration order; file-local timing constants keep the
 * upstream identifiers (CutScene.cpp:42-71).
 *
 * The GAMEMODE_UPSELL sequence (Crazy Dave's store-intro cutscene, CutScene.cpp:2067-2229) is
 * ported in full: the per-stage board tableaux (pool / fog / roof), the challenge-screen tableau and
 * the dialog-driven store/almanac hand-off.
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.content.CustomPlantType;
import cn.enaium.fabricpvz.portable.lawn.content.PlantTypeRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.widget.ChallengeScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.GameOverDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.PerfTimer;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;

public class CutScene {

    // CutScene.cpp:42-71
    private static final int TimePanRightStart = 1500;
    private static final int TimePanRightEnd = 3500;
    private static final int TimeEarlyDaveEnterStart = 2000;
    private static final int TimeEarlyDaveEnterEnd = 2750;
    // private static final int TimeEarlyDaveLeaveStart = 3250;
    private static final int TimeEarlyDaveLeaveEnd = 4000;
    private static final int TimeSeedChoserSlideOnStart = 4000;
    private static final int TimeSeedChoserSlideOnEnd = 4250;
    private static final int TimeSeedChoserSlideOffStart = 4500;
    private static final int TimeSeedChoserSlideOffEnd = 4750;
    private static final int TimeSeedBankOnStart = 4000;
    private static final int TimeSeedBankOnEnd = 4250;
    private static final int TimePanLeftStart = 4500;
    private static final int TimePanLeftEnd = 6000;
    private static final int TimeSeedBankRightStart = 4750;
    private static final int TimeSeedBankRightEnd = 6000;
    private static final int TimeRollSodStart = 6000;
    private static final int TimeRollSodEnd = 8000;
    private static final int TimeGraveStoneStart = 6000;
    private static final int TimeGraveStoneEnd = 7000;
    private static final int TimeReadySetPlantStart = 6000;
    private static final int TimeReadySetPlantEnd = 7830;
    private static final int TimeFogRollIn = 5950;
    // private static final int TimeCrazyDaveEnterStart = 6500;
    // private static final int TimeCrazyDaveEnterEnd = 7250;
    // private static final int TimeCrazyDaveLeaveStart = 7750;
    // private static final int TimeCrazyDaveLeaveEnd = 8500;
    private static final int TimeIntroEnd = 6000;
    private static final int LostTimePanRightStart = 1500;
    private static final int LostTimePanRightEnd = 3500;
    private static final int LostTimeBrainGraphicStart = 6000;
    private static final int LostTimeBrainGraphicShake = 7000;
    private static final int LostTimeBrainGraphicCancelShake = 8000;
    private static final int LostTimeBrainGraphicEnd = 11000;
    private static final int LostTimeEnd = 11000;
    private static final int TimeIntro_PresentsFadeIn = 1000;
    private static final int TimeIntro_LogoStart = 5500;
    private static final int TimeIntro_LogoEnd = 5900;
    private static final int TimeIntro_PanRightStart = 5890;
    private static final int TimeIntro_PanRightEnd = 11890;
    private static final int TimeIntro_FadeOut = 10890;
    private static final int TimeIntro_FadeOutEnd = 11890;
    private static final int TimeIntro_End = 13890;
    private static final int TimeLawnMowerDuration = 250;
    private static final int[] TimeLawnMowerStart = { 6300, 6250, 6200, 6150, 6100, 6050 };

    public LawnApp mApp;
    public Board mBoard;
    public int mCutsceneTime;
    public int mSodTime;
    public int mGraveStoneTime;
    public int mReadySetPlantTime;
    public int mFogTime;
    public int mBossTime;
    public int mCrazyDaveTime;
    public int mLawnMowerTime;
    public int mCrazyDaveDialogStart;
    public boolean mSeedChoosing;
    public int mZombiesWonReanimID;
    public boolean mPreloaded;
    public boolean mPlacedZombies;
    public boolean mPlacedLawnItems;
    public int mCrazyDaveCountDown;              // Countdown for Crazy Dave's upsell dialog
    public int mCrazyDaveLastTalkIndex;          // Index of Crazy Dave's upsell dialog lines
    /**
     * The Crazy Dave line ("How does that sound?", 1503/1553) whose seed-slot offer dialog is up, or
     * {@code -1}. Upstream keeps it in the {@code aMessageIndex} local across the blocking
     * {@code Dialog::WaitForResult} (CutScene.cpp:1611-1638); see the PORT-NOTE in
     * {@link #advanceCrazyDaveDialog}.
     */
    public int mPacketUpgradeOfferIndex = -1;
    public boolean mUpsellHideBoard;             // Whether to hide the board during upsell
    public ChallengeScreen mUpsellChallengeScreen; // The challenge screen inserted during upsell
    public boolean mPreUpdatingBoard;            // Whether the board is pre-updating during scene setup
    public final List<String> mLoadedResourceNames = new ArrayList<>();

    // CutScene.cpp:74-98
    public CutScene() {
        mApp = LawnApp.gLawnApp;
        mBoard = mApp.mBoard;
        mCutsceneTime = 0;
        mSodTime = 0;
        mFogTime = 0;
        mBossTime = 0;
        mCrazyDaveTime = 0;
        mGraveStoneTime = 0;
        mReadySetPlantTime = 0;
        mLawnMowerTime = 0;
        mCrazyDaveDialogStart = -1;
        mSeedChoosing = false;
        mZombiesWonReanimID = ReanimationID.REANIMATIONID_NULL;
        mPreloaded = false;
        mPlacedZombies = false;
        mPlacedLawnItems = false;
        mCrazyDaveCountDown = 0;
        mCrazyDaveLastTalkIndex = -1;
        mUpsellHideBoard = false;
        mUpsellChallengeScreen = null;
        mPreUpdatingBoard = false;
    }

    // CutScene.cpp:100-128 (~CutScene)
    public void dispose() {
        mApp.mMuteSoundsForCutscene = false;
        mApp.mResourceManager.releaseTrackedResources(mLoadedResourceNames);
    }

    // CutScene.cpp:130-184
    public void placeAZombie(int theZombieType, int theGridX, int theGridY) {
        boolean aPutOnDuckyTube = false;
        if (theZombieType == ZombieType.ZOMBIE_DUCKY_TUBE && mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2) {
            theZombieType = ZombieType.ZOMBIE_PEA_HEAD;
            aPutOnDuckyTube = true;
        }

        Zombie aZombie = mBoard.addZombieInRow(theZombieType, theGridY, -2);
        aZombie.mPosX = theGridX * 56 + 830;
        aZombie.mPosY = theGridY * 90 + 70;
        if (theGridX % 2 == 1) {
            aZombie.mPosY += 30.0f;
        }

        if (aPutOnDuckyTube) {
            mApp.reanimationGet(aZombie.mBodyReanimID).assignRenderGroupToTrack("Zombie_duckytube", Reanimation.RENDER_GROUP_NORMAL);
        }
        if (mBoard.stageHasRoof()) {
            aZombie.mPosY -= theGridY * 2 - theGridX * 7 + 30;  //7 * (5 - theGridX) - 2 * (5 - theGridY) + 5;
            aZombie.mPosX -= 5.0f;
        }
        if (theZombieType == ZombieType.ZOMBIE_ZAMBONI) {
            aZombie.mPosY -= 10.0f;
            aZombie.mPosX -= 30.0f;
        } else if (mApp.isLittleTroubleLevel()) {
            aZombie.mPosY += Common.rand(50) - 25;  //RandRangeInt(-25, 24);
            aZombie.mPosX += Common.rand(50) - 25;  //RandRangeInt(-25, 24);
        } else if (is2x2Zombie(theZombieType)) {
            aZombie.mPosX += Common.rand(15) - 20;  //RandRangeInt(-20, -6);
        } else if (theGridY == 4 && (mApp.canShowAlmanac() || mApp.canShowStore())) {
            aZombie.mPosX += Common.rand(15);  //RandRangeInt(0, 14);
        } else {
            aZombie.mPosY += Common.rand(15);  //RandRangeInt(0, 14);
            aZombie.mPosX += Common.rand(15);  //RandRangeInt(0, 14);
        }
        aZombie.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN, 0, (theGridX % 2) * 2 + theGridY * 4);

        if (theZombieType == ZombieType.ZOMBIE_BUNGEE) {
            aZombie.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, 0, 0);
            aZombie.mRow = 0;
            aZombie.mPosX = theGridX * 50.0f + 950.0f;
            aZombie.mPosY = 50.0f;
        } else if (theZombieType == ZombieType.ZOMBIE_BOBSLED) {
            aZombie.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN, 0, 1000);
            aZombie.mRow = 0;
            aZombie.mPosX = 1105.0f;
            aZombie.mPosY = 480.0f;
        }
    }

    // CutScene.cpp:186-249
    public boolean canZombieGoInGridSpot(int theZombieType, int theGridX, int theGridY, boolean[][] theZombieGrid) {
        if (theZombieGrid[theGridX][theGridY]) {
            return false;
        }

        if (is2x2Zombie(theZombieType)) {
            if (theGridX == 0 || theGridY == 0) {
                return false;
            }

            if (theZombieGrid[theGridX - 1][theGridY] || theZombieGrid[theGridX][theGridY - 1] || theZombieGrid[theGridX - 1][theGridY - 1]) {
                return false;
            }
        }

        if (theGridX == 4) {
            if (theGridY == 0) {
                return false;
            }
        } else {
            if (theZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                return false;
            }

            if (theGridX == 0 && mBoard.stageHasPool()) {
                return false;
            }
        }

        if (mBoard.stageHasRoof() && theGridX == 0 && theGridY == 0) {
            return false;
        }
        if (theGridX == 4 && mBoard.stageHasFog() && theZombieType == ZombieType.ZOMBIE_BALLOON) {
            return false;
        }

        if (is2x2Zombie(theZombieType) ||
                theZombieType == ZombieType.ZOMBIE_ZAMBONI ||
                theZombieType == ZombieType.ZOMBIE_BOBSLED ||
                theZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
            if (theGridX == 0) {
                return false;
            }
            if (theGridX == 1 && (mBoard.stageHasPool() || theGridY == 0)) {
                return false;
            }
        }

        return true;
    }

    // CutScene.cpp:251-290. The C++ out-parameters become int[1] sinks.
    public void findPlaceForStreetZombies(int theZombieType, boolean[][] theZombieGrid, int[] thePosX, int[] thePosY) {
        if (theZombieType == ZombieType.ZOMBIE_BUNGEE) {
            thePosX[0] = 0;
            thePosY[0] = 0;
            return;
        }

        int aPicksCount = 0;
        PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[25];
        for (int aGridX = 0; aGridX < 5; aGridX++) {
            for (int aGridY = 0; aGridY < 5; aGridY++) {
                if (canZombieGoInGridSpot(theZombieType, aGridX, aGridY, theZombieGrid)) {
                    aPicks[aPicksCount] = new PvzpWeightedGridArray();
                    aPicks[aPicksCount].mX = aGridX;
                    aPicks[aPicksCount].mY = aGridY;
                    aPicks[aPicksCount].mWeight = 1;
                    aPicksCount++;
                }
            }
        }

        if (aPicksCount == 0) {
            // PORT-NOTE: WARN, not the INFO upstream used: the street zombie is dropped in at the
            // corner instead of a chosen spot and nothing else reports it.
            Common.logWarnLn("No place for street zombie!!");
            thePosX[0] = 2;
            thePosY[0] = 2;
        } else {
            PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPicksCount);
            thePosX[0] = aGrid.mX;
            thePosY[0] = aGrid.mY;
        }
    }

    // CutScene.cpp:292-316
    public void findAndPlaceZombie(int theZombieType, boolean[][] theZombieGrid) {
        int[] aGridX = new int[1];
        int[] aGridY = new int[1];
        findPlaceForStreetZombies(theZombieType, theZombieGrid, aGridX, aGridY);

        if (theZombieType != ZombieType.ZOMBIE_BUNGEE) {
            theZombieGrid[aGridX[0]][aGridY[0]] = true;
        }
        if (is2x2Zombie(theZombieType)) {
            theZombieGrid[aGridX[0] - 1][aGridY[0]] = true;
            theZombieGrid[aGridX[0]][aGridY[0] - 1] = true;
            theZombieGrid[aGridX[0] - 1][aGridY[0] - 1] = true;
        }

        placeAZombie(theZombieType, aGridX[0], aGridY[0]);
        if (theZombieType == ZombieType.ZOMBIE_BUNGEE && mApp.isBungeeBlitzLevel()) {
            placeAZombie(ZombieType.ZOMBIE_BUNGEE, 1, aGridY[0]);
            placeAZombie(ZombieType.ZOMBIE_BUNGEE, 2, aGridY[0]);
        }
    }

    // CutScene.cpp:318-322
    public static boolean is2x2Zombie(int theZombieType) {
        return theZombieType == ZombieType.ZOMBIE_GARGANTUAR || theZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR;
    }

    // CutScene.cpp:324-488
    public void preloadResources() {
        if (mPreloaded) {
            return;
        }
        mPreloaded = true;

        mLoadedResourceNames.clear();

        PerfTimer aTimer = new PerfTimer();
        aTimer.start();

        for (int aWave = 0; aWave < mBoard.mNumWaves; aWave++) {
            for (int aZombieIndex = 0; aZombieIndex < Board.MAX_ZOMBIES_IN_WAVE; aZombieIndex++) {
                int aZombieType = mBoard.mZombiesInWave[aWave][aZombieIndex];
                if (aZombieType == ZombieType.ZOMBIE_INVALID) {
                    break;
                }
                Zombie.preloadZombieResources(aZombieType);
            }
        }

        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.NUM_SEED_TYPES; aSeedType++) {
            if (mApp.hasSeedType(aSeedType)) {
                Plant.preloadPlantResources(aSeedType);
            }
        }

        // Mod plants have no unlock state, so they are always preloaded.
        for (CustomPlantType aCustom : PlantTypeRegistry.all()) {
            Plant.preloadPlantResources(aCustom.mSeedType);
        }

        if (mApp.isFirstTimeAdventureMode() && mBoard.mLevel <= 50) {
            Plant.preloadPlantResources(LawnApp.getAwardSeedForLevel(mBoard.mLevel));
        }

        if (mCrazyDaveDialogStart != -1) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CRAZY_DAVE, true);
        }
        if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_RAKE] != 0) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_RAKE, true);
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            Plant.preloadPlantResources(SeedType.SEED_SPROUT);
            Plant.preloadPlantResources(SeedType.SEED_MARIGOLD);
        }

        if (mBoard.stageHasRoof()) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ROOF_CLEANER, true);
        } else {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_LAWNMOWER, true);
        }
        if (mBoard.stageHasPool()) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SPLASH, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_POOL_CLEANER, true);
        }

        if (mBoard.canDropLoot()) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_COIN_SILVER, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_COIN_GOLD, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_DIAMOND, true);
        }

        if (mSodTime > 0) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SODROLL, true);
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_PORTAL_CIRCLE, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_PORTAL_SQUARE, true);
        }
        if (mApp.isWhackAZombieLevel() || mApp.isScaryPotterLevel()) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_HAMMER, true);
        }
        if (mApp.isStormyNightLevel() || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_RAIN_CIRCLE, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_RAIN_SPLASH, true);
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZENGARDEN_WATERINGCAN, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZENGARDEN_FERTILIZER, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZENGARDEN_BUGSPRAY, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZENGARDEN_PHONOGRAPH, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_STINKY, true);
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZENGARDEN_FERTILIZER, true);
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            mLoadedResourceNames.add("DelayLoad_Background3");
            mLoadedResourceNames.add("DelayLoad_Background4");
            mLoadedResourceNames.add("DelayLoad_Background5");
            mLoadedResourceNames.add("DelayLoad_ChallengeScreen");
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_NORMAL);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_TRAFFIC_CONE);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_PAIL);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_ZAMBONI);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_POGO);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_BALLOON);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_CATAPULT);
            Plant.preloadPlantResources(SeedType.SEED_SQUASH);
            Plant.preloadPlantResources(SeedType.SEED_THREEPEATER);
            Plant.preloadPlantResources(SeedType.SEED_MAGNETSHROOM);
            Plant.preloadPlantResources(SeedType.SEED_LILYPAD);
            Plant.preloadPlantResources(SeedType.SEED_TORCHWOOD);
            Plant.preloadPlantResources(SeedType.SEED_SPIKEWEED);
            Plant.preloadPlantResources(SeedType.SEED_TANGLEKELP);
            Plant.preloadPlantResources(SeedType.SEED_SUNFLOWER);
            Plant.preloadPlantResources(SeedType.SEED_PEASHOOTER);
            Plant.preloadPlantResources(SeedType.SEED_SUNSHROOM);
            Plant.preloadPlantResources(SeedType.SEED_SUNSHROOM);  // sun-shroom is deliberately preloaded twice
            Plant.preloadPlantResources(SeedType.SEED_FLOWERPOT);
            Plant.preloadPlantResources(SeedType.SEED_PLANTERN);
            Plant.preloadPlantResources(SeedType.SEED_FUMESHROOM);
            Plant.preloadPlantResources(SeedType.SEED_CACTUS);
            Plant.preloadPlantResources(SeedType.SEED_PUFFSHROOM);
            Plant.preloadPlantResources(SeedType.SEED_SEASHROOM);
            Plant.preloadPlantResources(SeedType.SEED_CABBAGEPULT);
            Plant.preloadPlantResources(SeedType.SEED_WALLNUT);
            Plant.preloadPlantResources(SeedType.SEED_CHOMPER);
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_INTRO) {
            mLoadedResourceNames.add("DelayLoad_Background3");
            mLoadedResourceNames.add("DelayLoad_Credits");
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_NORMAL);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_TRAFFIC_CONE);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_PAIL);
            Zombie.preloadZombieResources(ZombieType.ZOMBIE_ZAMBONI);
            Plant.preloadPlantResources(SeedType.SEED_SUNFLOWER);
            Plant.preloadPlantResources(SeedType.SEED_PEASHOOTER);
            Plant.preloadPlantResources(SeedType.SEED_SQUASH);
            Plant.preloadPlantResources(SeedType.SEED_THREEPEATER);
            Plant.preloadPlantResources(SeedType.SEED_LILYPAD);
            Plant.preloadPlantResources(SeedType.SEED_TORCHWOOD);
            Plant.preloadPlantResources(SeedType.SEED_SPIKEWEED);
            Plant.preloadPlantResources(SeedType.SEED_TANGLEKELP);
        }

        for (String resource : mLoadedResourceNames) {
            ((PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(resource);
        }

        placeStreetZombies();

        mBoard.mPreloadTime = (int) Math.max(aTimer.getDuration(), 0.0);
        PvzpDebug.pvzpLogLn("preloading: {} ms", mBoard.mPreloadTime);
    }

    // CutScene.cpp:490-606
    public void placeStreetZombies() {
        if (mPlacedZombies) {
            return;
        }

        mPlacedZombies = true;
        if (mApp.isFinalBossLevel()) {
            return;
        }

        // Count previewable zombies of each type in the wave list
        int aTotalZombieCount = 0;
        int[] aZombieTypeCount = new int[ZombieType.MAX_ZOMBIE_TYPES];

        for (int aWave = 0; aWave < mBoard.mNumWaves; aWave++) {
            for (int aZombieIndex = 0; aZombieIndex < Board.MAX_ZOMBIES_IN_WAVE; aZombieIndex++) {
                int aZombieType = mBoard.mZombiesInWave[aWave][aZombieIndex];
                if (aZombieType == ZombieType.ZOMBIE_INVALID) {
                    break;
                }

                if (aZombieType == ZombieType.ZOMBIE_FLAG) {
                    continue;
                }
                if (aZombieType == ZombieType.ZOMBIE_YETI && !mApp.isStormyNightLevel()) {
                    continue;
                }
                if (aZombieType == ZombieType.ZOMBIE_BOBSLED && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA) {
                    continue;
                }

                ++aZombieTypeCount[aZombieType];
                ++aTotalZombieCount;
                if (aZombieType == ZombieType.ZOMBIE_BUNGEE || aZombieType == ZombieType.ZOMBIE_BOBSLED) {
                    aZombieTypeCount[aZombieType] = 1;  // bungee and bobsled zombies get at most 1 preview zombie
                }
            }
        }

        // In Last Stand, count at least 1 of every allowed zombie type except the yeti
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            for (int aZombieType = 0; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
                if (aZombieType != ZombieType.ZOMBIE_YETI && mBoard.mZombieAllowed[aZombieType]) {
                    aZombieTypeCount[aZombieType] = Math.max(aZombieTypeCount[aZombieType], 1);
                }
            }
        }
        if (mBoard.stageHasPool()) {
            aZombieTypeCount[ZombieType.ZOMBIE_DUCKY_TUBE] = 1;  // pool stages always preview a ducky tube zombie
        }

        boolean[][] aZombieGrid = new boolean[5][5];
        int aPreviewCapacity = 10;
        if (mApp.isLittleTroubleLevel()) {
            aPreviewCapacity = 15;
        } else if ((mApp.isStormyNightLevel() && mApp.isAdventureMode()) || mApp.isMiniBossLevel()) {
            aPreviewCapacity = 18;
        }

        // Place larger zombies first, then smaller ones
        for (int aZombieType = ZombieType.ZOMBIE_NORMAL; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            if (aZombieTypeCount[aZombieType] != 0 && (is2x2Zombie(aZombieType) || aZombieType == ZombieType.ZOMBIE_ZAMBONI)) {
                findAndPlaceZombie(aZombieType, aZombieGrid);
            }
        }
        for (int aZombieType = ZombieType.ZOMBIE_NORMAL; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            if (aZombieTypeCount[aZombieType] != 0 && !is2x2Zombie(aZombieType) && aZombieType != ZombieType.ZOMBIE_ZAMBONI) {
                int aZombieNumInWave = aZombieTypeCount[aZombieType];
                int aZombiePreviewNum = aZombieNumInWave * aPreviewCapacity / aTotalZombieCount;
                aZombiePreviewNum = Math.max(Math.min(aZombiePreviewNum, aZombieNumInWave), 1);
                for (int i = 0; i < aZombiePreviewNum; i++) {
                    findAndPlaceZombie(aZombieType, aZombieGrid);
                }
            }
        }
    }

    // CutScene.cpp:608-628
    public void placeLawnItems() {
        if (mPlacedLawnItems) {
            return;
        }
        mPlacedLawnItems = true;

        if (!isSurvivalRepick()) {
            mBoard.initLawnMowers();
            addFlowerPots();
        }

        if (!isSurvivalRepick()) {
            mBoard.placeRake();
        }
    }

    // CutScene.cpp:630-633
    public boolean isSurvivalRepick() {
        return mApp.isSurvivalMode() && mBoard.mChallenge.mSurvivalStage > 0 && mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO;
    }

    // CutScene.cpp:635-646
    public boolean isNonScrollingCutscene() {
        return
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE ||
                mApp.mGameMode == GameMode.GAMEMODE_UPSELL ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN ||
                mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM ||
                mApp.isScaryPotterLevel() ||
                mApp.isIZombieLevel() ||
                mApp.isWhackAZombieLevel() ||
                mApp.isShovelLevel() ||
                mApp.isSquirrelLevel() ||
                mApp.isWallnutBowlingLevel();
    }

    // CutScene.cpp:648-653
    public boolean isScrolledLeftAtStart() {
        if (mBoard.mChallenge.mSurvivalStage > 0 && mApp.isSurvivalMode()) {
            return false;  // later survival rounds start scrolled to the screen center
        }

        return !isNonScrollingCutscene();
    }

    // CutScene.cpp:655-662
    public boolean canGetPacketUpgrade() {
        int aCost = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);

        return
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] == 0 &&
                mApp.mPlayerInfo.mCoins >= aCost &&
                mApp.mPlayerInfo.mDidntPurchasePacketUpgrade < 2;
    }

    // CutScene.cpp:664-671
    public boolean canGetSecondPacketUpgrade() {
        int aCost = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);

        return
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] == 1 &&
                mApp.mPlayerInfo.mCoins >= aCost &&
                mApp.mPlayerInfo.mDidntPurchasePacketUpgrade < 2;
    }

    // CutScene.cpp:673-680
    public boolean canGetPacketUpgrade(int theUpgradeIndex) {
        int aCost = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);

        return
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] == theUpgradeIndex &&  // theUpgradeIndex is 0-based
                mApp.mPlayerInfo.mCoins >= aCost &&
                mApp.mPlayerInfo.mDidntPurchasePacketUpgrade < 2;
    }

    // CutScene.cpp:682-965
    public void startLevelIntro() {
        mCutsceneTime = 0;
        mBoard.mSeedBank.move(GameConstants.SEED_BANK_OFFSET_X, -Resources.getImageById(ImageID.IMAGE_SEEDBANK).getHeight());
        mBoard.mMenuButton.mBtnNoDraw = true;
        mApp.mSeedChooserScreen.mMouseVisible = false;
        mApp.mSeedChooserScreen.move(0, GameConstants.SEED_CHOOSER_OFFSET_Y);
        mApp.mSeedChooserScreen.mMenuButton.mBtnNoDraw = true;
        mBoard.mShowShovel = false;
        mBoard.mSeedBank.mCutSceneDarken = 255;
        mPlacedZombies = false;
        mPreloaded = false;
        mPlacedLawnItems = false;
        mApp.mWidgetManager.setFocus(mBoard);

        int aLevel = mBoard.mLevel;
        if (mApp.isFirstTimeAdventureMode() && (aLevel == 1 || aLevel == 2 || aLevel == 4)) {
            mSodTime = TimeRollSodEnd - TimeRollSodStart;
            mBoard.mSodPosition = 0;
        } else {
            mSodTime = 0;
            mBoard.mSodPosition = 1000;
        }

        mGraveStoneTime = 0;
        mBoard.mEnableGraveStones = false;
        if (mBoard.stageHasGraveStones()) {
            if (mApp.isAdventureMode() && mApp.isWhackAZombieLevel()) {
                mGraveStoneTime = 0;
            } else if (!isSurvivalRepick()) {
                mGraveStoneTime = TimeGraveStoneEnd - TimeGraveStoneStart;
            }
        }

        if (mApp.isFirstTimeAdventureMode() && aLevel <= 2) {
            mReadySetPlantTime = 0;
        } else if (mApp.isShovelLevel() ||
                mApp.isSquirrelLevel() ||
                mApp.isWallnutBowlingLevel() ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND ||
                mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM ||
                mApp.isIZombieLevel() ||
                mApp.isWhackAZombieLevel() ||
                mApp.isScaryPotterLevel()) {
            mReadySetPlantTime = 0;
        } else {
            mReadySetPlantTime = TimeReadySetPlantEnd - TimeReadySetPlantStart;
        }

        mLawnMowerTime = 0;
        if (!isSurvivalRepick()) {
            mLawnMowerTime = 550;
        }

        boolean isRestart = false;
        if (mBoard.mPrevBoardResult == BoardResult.BOARDRESULT_LOST || mBoard.mPrevBoardResult == BoardResult.BOARDRESULT_RESTART) {
            isRestart = true;
        }

        if (mApp.isFirstTimeAdventureMode() && aLevel == 11) {
            mCrazyDaveDialogStart = 201;
        } else if (mApp.isFirstTimeAdventureMode() && aLevel == 12) {
            mCrazyDaveDialogStart = 1401;
        } else if (mApp.isFirstTimeAdventureMode() && aLevel >= 13 && aLevel <= 24 && aLevel != 15 && aLevel != 20 && aLevel != 21 && canGetPacketUpgrade()) {
            mCrazyDaveDialogStart = 1501;
        } else if (mApp.isFirstTimeAdventureMode() && aLevel >= 16 && aLevel <= 24 && aLevel != 20 && aLevel != 21 && canGetSecondPacketUpgrade()) {
            mCrazyDaveDialogStart = 1551;
        } else if (mApp.isWallnutBowlingLevel() && mApp.isAdventureMode()) {
            if (mApp.isFirstTimeAdventureMode()) {
                mCrazyDaveDialogStart = 2400;
            } else {
                mCrazyDaveDialogStart = 2411;
                mBoard.mChallenge.mShowBowlingLine = 1;
            }
            mBoard.mShowShovel = true;
        } else if (mApp.isFirstTimeAdventureMode() && aLevel == 21) {
            mCrazyDaveDialogStart = 501;
        } else if (mApp.isWhackAZombieLevel() && mApp.isAdventureMode()) {
            mCrazyDaveDialogStart = 401;
        } else if (mApp.isLittleTroubleLevel() && mApp.isAdventureMode()) {
            mCrazyDaveDialogStart = 701;
        } else if (mApp.isFirstTimeAdventureMode() && aLevel == 31) {
            mCrazyDaveDialogStart = 801;
        } else if (mApp.isScaryPotterLevel() && mApp.isAdventureMode()) {
            mCrazyDaveDialogStart = 2500;
        } else if (mApp.isStormyNightLevel() && mApp.isAdventureMode()) {
            mCrazyDaveDialogStart = 1101;
        } else if (mApp.isFirstTimeAdventureMode() && aLevel == 41) {
            mCrazyDaveDialogStart = 1201;
        } else if (mApp.isBungeeBlitzLevel() && mApp.isAdventureMode()) {
            mCrazyDaveDialogStart = mApp.isFirstTimeAdventureMode() ? 1301 : 1304;
        } else if (!mApp.isFirstTimeAdventureMode() && aLevel == 1) {
            mCrazyDaveDialogStart = 1601;
        } else if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1) {
            mCrazyDaveDialogStart = 2200;
        } else if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            mCrazyDaveDialogStart = 3300;
            mUpsellHideBoard = true;
            mBoard.mMenuButton.mBtnNoDraw = false;
        } else if (mApp.mGameMode == GameMode.GAMEMODE_SCARY_POTTER_1 && !mApp.hasBeatenChallenge(GameMode.GAMEMODE_SCARY_POTTER_1)) {
            mCrazyDaveDialogStart = 3000;
        } else if (mApp.isFinalBossLevel() && mApp.isAdventureMode() && !isRestart) {
            mCrazyDaveDialogStart = 2300;
        } else if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] < PlayerInfo.PURCHASE_COUNT_OFFSET) {
                mCrazyDaveDialogStart = 3200;
                mBoard.mStoreButton.mBtnNoDraw = true;
            }
        }
        if (mCrazyDaveDialogStart != -1) {
            mCrazyDaveTime = TimeEarlyDaveLeaveEnd - TimePanRightStart;
            if (mApp.isFinalBossLevel() && mApp.isAdventureMode()) {
                mCrazyDaveTime += 4000;
            }
        }

        if (mBoard.stageHasFog()) {
            mFogTime = TimeFogRollIn - mSodTime - mLawnMowerTime - TimeReadySetPlantStart + 2000;
        } else {
            mFogTime = 0;
        }

        if (mApp.isFinalBossLevel()) {
            mBossTime = 4000;
        } else {
            mBossTime = 0;
        }

        if (isScrolledLeftAtStart()) {
            mBoard.move(220, 0);
        }
        if (isNonScrollingCutscene() && mCrazyDaveTime == 0) {
            cancelIntro();
            return;
        }

        if (mApp.isFinalBossLevel() || mApp.isScaryPotterLevel() || mApp.isWallnutBowlingLevel()) {
            preloadResources();
            placeLawnItems();
        }

        String aHouseMessage = "";
        if (mCrazyDaveTime <= 0 && mApp.mGameMode != GameMode.GAMEMODE_INTRO) {
            if (mApp.isSurvivalMode()) {
                aHouseMessage = mApp.getCurrentChallengeDef().mChallengeName;
            } else if (mApp.isAdventureMode()) {
                if (mBoard.mBackground == BackgroundType.BACKGROUND_1_DAY || mBoard.mBackground == BackgroundType.BACKGROUND_2_NIGHT) {
                    aHouseMessage = PvzpStringFile.translate("[PLAYERS_HOUSE]");
                } else if (mBoard.mBackground == BackgroundType.BACKGROUND_3_POOL || mBoard.mBackground == BackgroundType.BACKGROUND_4_FOG) {
                    aHouseMessage = PvzpStringFile.translate("[PLAYERS_BACKYARD]");
                } else if (mBoard.mBackground == BackgroundType.BACKGROUND_5_ROOF || mBoard.mBackground == BackgroundType.BACKGROUND_6_BOSS) {
                    aHouseMessage = PvzpStringFile.translate("[PLAYERS_ROOF]");
                }
            } else {
                aHouseMessage = mApp.getCurrentChallengeDef().mChallengeName;
            }
        }

        aHouseMessage = PvzpCommon.pvzpReplaceString(aHouseMessage, "{PLAYER}", mApp.mPlayerInfo.mName);
        if (!aHouseMessage.isEmpty()) {
            mBoard.displayAdvice(aHouseMessage, MessageStyle.MESSAGE_STYLE_HOUSE_NAME, AdviceType.ADVICE_NONE);
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_ZEN_GARDEN);
        } else if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            mApp.mMusic.stopAllMusic();
        } else if (mApp.mGameMode == GameMode.GAMEMODE_INTRO) {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES);
        } else if (mCrazyDaveTime > 0) {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME);
        } else if (mApp.isFinalBossLevel()) {
            mApp.mMusic.stopAllMusic();
        } else {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS);
        }
    }

    // CutScene.cpp:967-970
    public boolean isBeforePreloading() {
        return mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && !mPreloaded;
    }

    // CutScene.cpp:972-1057
    public void cancelIntro() {
        preloadResources();
        placeStreetZombies();
        if (mCutsceneTime < mCrazyDaveTime + TimePanRightEnd) {
            mCutsceneTime = TimeSeedChoserSlideOnEnd + mCrazyDaveTime - 20;
            if (!isNonScrollingCutscene()) {
                mBoard.move(mApp.mWidth - GameConstants.BOARD_IMAGE_WIDTH_OFFSET, 0);
            }
            if (mBoard.mAdvice.mMessageStyle == MessageStyle.MESSAGE_STYLE_HOUSE_NAME) {
                mBoard.clearAdvice(AdviceType.ADVICE_NONE);
            }

            if (mCrazyDaveDialogStart != -1) {
                if (mApp.mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_OFF) {
                    mApp.crazyDaveEnter();
                }
                mApp.mCrazyDaveMessageIndex = mCrazyDaveDialogStart;
            }
            while (mApp.mCrazyDaveMessageIndex != -1) {
                advanceCrazyDaveDialog(true);
            }

            if (mBoard.mLevel == 5) {
                for (Plant aPlant : mBoard.mPlants) {
                    if (aPlant.mDead) {
                        continue;
                    }
                    aPlant.die();
                }
                mBoard.mChallenge.mShowBowlingLine = 1;
            }
        }
        mApp.crazyDaveDie();

        if (mCutsceneTime > mCrazyDaveTime + TimePanLeftStart || !mBoard.chooseSeedsOnCurrentLevel()) {
            // Fast-forward the cutscene to the end of the level intro
            mCutsceneTime = TimeIntroEnd + mLawnMowerTime + mSodTime + mGraveStoneTime + mCrazyDaveTime + mFogTime + mBossTime + mReadySetPlantTime - 20;

            placeLawnItems();
            if (mApp.isStormyNightLevel()) {
                mBoard.mChallenge.mChallengeStateCounter = 0;
            }
            if (mApp.isFinalBossLevel()) {
                mBoard.mChallenge.playBossEnter();
            }
            if (!mApp.isChallengeWithoutSeedBank()) {
                mBoard.mSeedBank.move(GameConstants.SEED_BANK_OFFSET_X_END, 0);
            }

            mBoard.mEnableGraveStones = true;
            showShovel();

            if (mApp.isFinalBossLevel()) {
                mApp.mMusic.startGameMusic();
            }

            if (mBoard.mFogBlownCountDown > 0) {
                mBoard.mFogBlownCountDown = 0;
                mBoard.mFogOffset = 0;
            }

            if (mBoard.mTutorialState != TutorialState.TUTORIAL_ZEN_GARDEN_PICKUP_WATER) {
                mBoard.mMenuButton.mBtnNoDraw = false;
            }
            mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_DIGGER);
        }
    }

    // CutScene.cpp:1059-1073
    public void addGraveStoneParticles() {
        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                aGridItem.addGraveStoneParticles();
            }
        }
    }

    // CutScene.cpp:1075-1104
    public void addFlowerPots() {
        int aPotColumns = 0;
        if (mBoard.mLevel == 41) {
            aPotColumns = 5;
        } else if (mBoard.mLevel == 42) {
            aPotColumns = 4;
        } else if (mBoard.mLevel >= 43 && mBoard.mLevel <= 50) {
            aPotColumns = 3;
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            aPotColumns = 8;
        } else if (mBoard.stageHasRoof()) {
            aPotColumns = 3;
        }

        for (int x = 0; x < aPotColumns; x++) {
            for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                if (mBoard.canPlantAt(x, y, SeedType.SEED_FLOWERPOT) == PlantingReason.PLANTING_OK) {
                    mBoard.newPlant(x, y, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
                }
            }
        }
    }

    // CutScene.cpp:1106-1109
    public int calcPosition(int theTimeStart, int theTimeEnd, int thePositionStart, int thePositionEnd) {
        return PvzpCommon.pvzpAnimateCurve(theTimeStart, theTimeEnd, mCutsceneTime, thePositionStart, thePositionEnd, PvzpCurves.CURVE_EASE_IN_OUT);
    }

    // CutScene.cpp:1111-1370
    public void animateBoard() {
        int aTimePanRightStart = TimePanRightStart + mCrazyDaveTime;
        int aTimePanRightEnd = TimePanRightEnd + mCrazyDaveTime;
        int aTimePanLeftStart = TimePanLeftStart + mCrazyDaveTime;
        int aTimePanLeftEnd = TimePanLeftEnd + mCrazyDaveTime;

        // Crazy Dave animation
        if (mCrazyDaveTime > 0) {
            if (mCutsceneTime == TimeEarlyDaveEnterStart) {
                mApp.crazyDaveEnter();
                if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
                    Reanimation aDaveReanim = mApp.reanimationTryToGet(mApp.mCrazyDaveReanimID);
                    aDaveReanim.playReanim("anim_enterup", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 12);
                    aDaveReanim.setPosition(150, 70);
                }
            }

            if (mCutsceneTime == TimeEarlyDaveEnterEnd && mCrazyDaveDialogStart != -1 &&
                    mApp.mGameMode != GameMode.GAMEMODE_UPSELL) {
                mApp.crazyDaveTalkIndex(mCrazyDaveDialogStart);
                mCrazyDaveDialogStart = -1;
            }

            if (mCutsceneTime == TimeEarlyDaveLeaveEnd && isNonScrollingCutscene()) {
                mCutsceneTime = aTimePanLeftEnd;
            }
        }

        // Pan the board right
        int aBoardOffset = isScrolledLeftAtStart() ? GameConstants.BOARD_OFFSET : 0;
        if (mCutsceneTime <= aTimePanRightStart) {
            mBoard.move(aBoardOffset, 0);
        }
        if (mCutsceneTime > aTimePanRightStart && mCutsceneTime <= aTimePanRightEnd) {
            int aPanOffset = calcPosition(aTimePanRightStart, aTimePanRightEnd, -aBoardOffset, GameConstants.BOARD_IMAGE_WIDTH_OFFSET - mApp.mWidth);
            mBoard.move(-aPanOffset, 0);
        }

        // Seed chooser animation
        if (mBoard.chooseSeedsOnCurrentLevel()) {
            int aTimeSeedChoserSlideOnStart = TimeSeedChoserSlideOnStart + mCrazyDaveTime;
            int aTimeSeedChoserSlideOnEnd = TimeSeedChoserSlideOnEnd + mCrazyDaveTime;
            SeedChooserScreen aSeedChoser = mApp.mSeedChooserScreen;
            // Seed chooser slides on
            if (mCutsceneTime > aTimeSeedChoserSlideOnStart && mCutsceneTime <= aTimeSeedChoserSlideOnEnd) {
                aSeedChoser.move(0, calcPosition(aTimeSeedChoserSlideOnStart, aTimeSeedChoserSlideOnEnd, GameConstants.SEED_CHOOSER_OFFSET_Y, 0));
                aSeedChoser.mMenuButton.mY = calcPosition(aTimeSeedChoserSlideOnStart, aTimeSeedChoserSlideOnEnd, -50, -10);
                aSeedChoser.mMenuButton.mBtnNoDraw = false;
            }
            // Seed chooser slides off
            int aTimeSeedChoserSlideOffStart = TimeSeedChoserSlideOffStart + mCrazyDaveTime;
            int aTimeSeedChoserSlideOffEnd = TimeSeedChoserSlideOffEnd + mCrazyDaveTime;
            if (mCutsceneTime > aTimeSeedChoserSlideOffStart && mCutsceneTime <= aTimeSeedChoserSlideOffEnd) {
                aSeedChoser.move(0, calcPosition(aTimeSeedChoserSlideOffStart, aTimeSeedChoserSlideOffEnd, 0, GameConstants.SEED_CHOOSER_OFFSET_Y));
                aSeedChoser.mMenuButton.mDisabled = true;
            }
        }

        // Pan the board left
        if (mCutsceneTime > aTimePanLeftStart) {
            int aPanOffset = calcPosition(aTimePanLeftStart, aTimePanLeftEnd, GameConstants.BOARD_IMAGE_WIDTH_OFFSET - mApp.mWidth, 0);
            mBoard.move(-aPanOffset, 0);
        }

        // Seed bank animation
        int aTimePrepareEnd = 0;
        if (!mBoard.chooseSeedsOnCurrentLevel()) {
            aTimePrepareEnd = mBossTime + mFogTime + mGraveStoneTime + mSodTime - TimeSeedChoserSlideOnStart + TimePanLeftEnd;
        }
        int aTimeSeedBankOnStart = TimeSeedBankOnStart + aTimePrepareEnd + mCrazyDaveTime;
        int aTimeSeedBankOnEnd = TimeSeedBankOnEnd + aTimePrepareEnd + mCrazyDaveTime;
        if (!mApp.isChallengeWithoutSeedBank() && mCutsceneTime > aTimeSeedBankOnStart && mCutsceneTime <= aTimeSeedBankOnEnd) {
            int aSeedBankY = calcPosition(aTimeSeedBankOnStart, aTimeSeedBankOnEnd, -Resources.getImageById(ImageID.IMAGE_SEEDBANK).getHeight(), 0);
            mBoard.mSeedBank.move(GameConstants.SEED_BANK_OFFSET_X, aSeedBankY);
        }
        int aTimeSeedBankRightStart = TimeSeedBankRightStart + mCrazyDaveTime;
        int aTimeSeedBankRightEnd = TimeSeedBankRightEnd + mCrazyDaveTime;
        if (mCutsceneTime > aTimeSeedBankRightStart) {
            int aSeedBankX = calcPosition(aTimeSeedBankRightStart, aTimeSeedBankRightEnd, GameConstants.SEED_BANK_OFFSET_X, GameConstants.SEED_BANK_OFFSET_X_END);
            int aDarken = PvzpCommon.pvzpAnimateCurve(aTimeSeedBankRightStart, aTimeSeedBankRightEnd, mCutsceneTime, 255, 128, PvzpCurves.CURVE_EASE_OUT);
            mBoard.mSeedBank.mCutSceneDarken = aDarken;
            mBoard.mSeedBank.move(aSeedBankX, mBoard.mSeedBank.mY);
        }

        // Sod rolling on early adventure levels
        if (mSodTime > 0) {
            int aTimeRollSodStart = TimeRollSodStart + mCrazyDaveTime;
            int aTimeRollSodEnd = TimeRollSodEnd + mCrazyDaveTime;
            mBoard.mSodPosition = PvzpCommon.pvzpAnimateCurve(aTimeRollSodStart, aTimeRollSodEnd, mCutsceneTime, 0, 1000, PvzpCurves.CURVE_LINEAR);

            if (mCutsceneTime == aTimeRollSodStart) {
                mApp.playFoley(FoleyType.FOLEY_DIGGER);
                if (mBoard.mLevel == 1) {
                    mApp.addReanimation(0, 0, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0), ReanimationType.REANIM_SODROLL);
                    mApp.addPvzpParticle(35, 348, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 1), ParticleEffect.PARTICLE_SOD_ROLL);
                } else if (mBoard.mLevel == 2) {
                    mApp.addReanimation(0, -102, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0), ReanimationType.REANIM_SODROLL);
                    mApp.addReanimation(0, 111, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0), ReanimationType.REANIM_SODROLL);
                    mApp.addPvzpParticle(35, 246, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 1), ParticleEffect.PARTICLE_SOD_ROLL);
                    mApp.addPvzpParticle(35, 459, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 1), ParticleEffect.PARTICLE_SOD_ROLL);
                } else if (mBoard.mLevel == 4) {
                    mApp.addReanimation(-3, -198, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0), ReanimationType.REANIM_SODROLL);
                    mApp.addReanimation(-3, 203, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0), ReanimationType.REANIM_SODROLL);
                    mApp.addPvzpParticle(32, 150, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 1), ParticleEffect.PARTICLE_SOD_ROLL);
                    mApp.addPvzpParticle(32, 511, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 1), ParticleEffect.PARTICLE_SOD_ROLL);
                }
            }

            if (mCutsceneTime == aTimeRollSodEnd) {
                mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_DIGGER);
            }
        }

        // Grave stones appearing on night levels
        if (mGraveStoneTime > 0) {
            int aTimeGraveStoneStart = mSodTime + TimeGraveStoneStart + mCrazyDaveTime;
            if (mCutsceneTime == aTimeGraveStoneStart) {
                mBoard.mEnableGraveStones = true;
                addGraveStoneParticles();
            }
        }

        // Place lawn items when the board starts panning left
        if (mCutsceneTime == aTimePanLeftStart) {
            placeLawnItems();
        }

        // Lawn mowers rolling in
        if (!isSurvivalRepick()) {
            for (int aGridY = 0; aGridY < Board.MAX_GRID_SIZE_Y; aGridY++) {
                int aTimeLawnMowerStart = TimeLawnMowerStart[aGridY] + mSodTime + mGraveStoneTime + mCrazyDaveTime;
                if (mCutsceneTime > aTimeLawnMowerStart) {
                    LawnMower aLawnMower = mBoard.findLawnMowerInRow(aGridY);
                    if (aLawnMower != null) {
                        aLawnMower.mVisible = true;
                        aLawnMower.mPosX = calcPosition(aTimeLawnMowerStart, aTimeLawnMowerStart + TimeLawnMowerDuration, -80, -21);
                    }
                }
            }
        }

        // Fog rolling in
        if (mBoard.mFogBlownCountDown > 0) {
            int aTimeFogRollIn = TimeFogRollIn + mSodTime + mGraveStoneTime + mCrazyDaveTime;
            if (mCutsceneTime > aTimeFogRollIn) {
                if (mBoard.mFogBlownCountDown > 200) {
                    mBoard.mFogBlownCountDown = 200;
                }
                mBoard.mFogBlownCountDown--;
            }
        }

        // Storm flash
        if (mApp.isStormyNightLevel() && (mCutsceneTime == aTimePanRightEnd - 1000 || mCutsceneTime == aTimePanLeftEnd)) {
            mBoard.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_2;
            mBoard.mChallenge.mChallengeStateCounter = 310;
        }

        // Dr. Zomboss enters
        if (mBossTime > 0) {
            int aTimeBossEnter = TimeReadySetPlantStart + mLawnMowerTime + mCrazyDaveTime;
            if (mCutsceneTime == aTimeBossEnter) {
                mBoard.mChallenge.playBossEnter();
            }
        }

        // Boss level music
        if (mApp.isFinalBossLevel() && mCutsceneTime == aTimeSeedBankOnStart) {
            mApp.mMusic.startGameMusic();
        }

        // Ready Set Plant animation
        int aTimeReadySetPlant = TimeReadySetPlantStart + mLawnMowerTime + mSodTime + mGraveStoneTime + mCrazyDaveTime + mFogTime + mBossTime;
        if (mReadySetPlantTime > 0 && mCutsceneTime == aTimeReadySetPlant) {
            mApp.addReanimation(400, 324, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_SCREEN_FADE, 0, 0), ReanimationType.REANIM_READYSETPLANT);
            mApp.playSample(Resources.SOUND_READYSETPLANT_ID);
            if (!mApp.isFinalBossLevel()) {
                mApp.mMusic.fadeOut(150);
            }
        }
        if (mReadySetPlantTime == 0 && mCutsceneTime == aTimeReadySetPlant - 2000) {
            if (!mApp.isFinalBossLevel()) {
                mApp.mMusic.fadeOut(200);
            }
        }

        mApp.mSeedChooserScreen.mParent.bringToFront(mApp.mSeedChooserScreen);
    }

    // CutScene.cpp:1372-1397
    public void showShovel() {
        if (mApp.isWhackAZombieLevel() ||
                mApp.isWallnutBowlingLevel() ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN ||
                mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM ||
                mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM ||
                mApp.isIZombieLevel()) {
            return;
        }

        if (!mApp.isFirstTimeAdventureMode() || mBoard.mLevel > 4) {
            mBoard.mShowShovel = true;
        }
    }

    // CutScene.cpp:1399-1404
    public boolean isInShovelTutorial() {
        return
                mBoard.mTutorialState == TutorialState.TUTORIAL_SHOVEL_PICKUP ||
                mBoard.mTutorialState == TutorialState.TUTORIAL_SHOVEL_DIG ||
                mBoard.mTutorialState == TutorialState.TUTORIAL_SHOVEL_KEEP_DIGGING;
    }

    // CutScene.cpp:1406-1411
    public void startSeedChooser() {
        mApp.mSeedChooserScreen.mMouseVisible = true;
        mSeedChoosing = true;
        mApp.mWidgetManager.setFocus(mApp.mSeedChooserScreen);
    }

    // CutScene.cpp:1413-1419
    public void endSeedChooser() {
        mApp.mSeedChooserScreen.mMouseVisible = false;
        mSeedChoosing = false;
        mCutsceneTime = mCrazyDaveTime + TimeSeedChoserSlideOnEnd + 10;
        mApp.mWidgetManager.setFocus(mBoard);
    }

    // CutScene.cpp:1421-1424
    public boolean isShowingCrazyDave() {
        return mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && (mCrazyDaveTime > 0 && mCutsceneTime < TimePanRightEnd + mCrazyDaveTime);
    }

    // CutScene.cpp:1426-1500
    public void update() {
        if (mPreUpdatingBoard) {
            return;
        }

        if (isShowingCrazyDave() && (!mBoard.mPaused || mApp.mGameMode != GameMode.GAMEMODE_UPSELL)) {
            mApp.updateCrazyDave();
        }

        if (mBoard.mPaused) {
            return;
        }

        if (mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            mCutsceneTime += 10;
            updateZombiesWon();
            return;
        }

        if (mApp.mGameScene != GameScenes.SCENE_LEVEL_INTRO || mBoard.mBoardUpdateCounter <= 1) { // the first frame is drawn after the first update tick, so defer one tick deterministically
            return;
        }

        if (!mPreloaded) {
            preloadResources();
        }
        if (!mPlacedZombies) {
            placeStreetZombies();
        }
        if (isNonScrollingCutscene() || !mBoard.chooseSeedsOnCurrentLevel()) {
            placeLawnItems();
        }

        // Updates before seed choosing
        boolean aCutsceneTimeStop = false;
        if (mSeedChoosing || mApp.mCrazyDaveMessageIndex != -1 || isInShovelTutorial()) {
            aCutsceneTimeStop = true;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            updateUpsell();
            if (mApp.mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_OFF && mApp.mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_ENTERING) {
                aCutsceneTimeStop = true;
            }
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_INTRO) {
            mCutsceneTime += 10;
            updateIntro();
            return;
        }
        if (!aCutsceneTimeStop) {
            mCutsceneTime += 10;
            if (mCutsceneTime == TimeSeedChoserSlideOnEnd + mCrazyDaveTime && mBoard.chooseSeedsOnCurrentLevel()) {
                startSeedChooser();
            }
        }

        // Check whether the cutscene is over
        int aTimeStart = TimeIntroEnd + mLawnMowerTime + mSodTime + mGraveStoneTime + mCrazyDaveTime + mFogTime + mBossTime + mReadySetPlantTime;
        if (mCutsceneTime >= aTimeStart) {
            mBoard.removeCutsceneZombies();
            if (mBoard.mTutorialState != TutorialState.TUTORIAL_ZEN_GARDEN_PICKUP_WATER) {
                mBoard.mMenuButton.mBtnNoDraw = false;
            }

            showShovel();
            mApp.startPlaying();
            return;
        }

        animateBoard();
    }

    // CutScene.cpp:1502-1509
    public void startZombiesWon() {
        mCutsceneTime = 0;
        mBoard.mMenuButton.mBtnNoDraw = true;
        mBoard.mShowShovel = false;
        mApp.mMusic.stopAllMusic();
        mBoard.stopAllZombieSounds();
        mApp.playSample(Resources.SOUND_LOSEMUSIC_ID);
    }

    // CutScene.cpp:1511-1558
    public void updateZombiesWon() {
        if (mCutsceneTime > LostTimePanRightStart && mCutsceneTime <= LostTimePanRightEnd) {
            mBoard.move(calcPosition(LostTimePanRightStart, LostTimePanRightEnd, 0, GameConstants.BOARD_OFFSET), 0);
        }

        if (mCutsceneTime == LostTimeBrainGraphicStart - 400 || mCutsceneTime == LostTimeBrainGraphicStart - 900) {
            mApp.playFoley(FoleyType.FOLEY_CHOMP);
        }

        // Brain-eating animation and scream
        if (mCutsceneTime == LostTimeBrainGraphicStart) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIES_WON, true);
            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_SCREEN_FADE, 0, 0);
            Reanimation aReanimation = mApp.addReanimation(-GameConstants.BOARD_OFFSET, 0, aRenderPosition, ReanimationType.REANIM_ZOMBIES_WON);
            aReanimation.mAnimRate = 12.0f;
            aReanimation.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
            aReanimation.getTrackInstanceByName("fullscreen").mTrackColor = Color.Black;
            mZombiesWonReanimID = mApp.reanimationGetID(aReanimation);
            aReanimation.setFramesForLayer("ZombiesWon");
            mApp.playFoley(FoleyType.FOLEY_SCREAM);
        }

        if (mCutsceneTime == LostTimeBrainGraphicShake) {
            mApp.reanimationGet(mZombiesWonReanimID).setShakeOverride("ZombiesWon", 1.0f);
        }
        if (mCutsceneTime == LostTimeBrainGraphicCancelShake) {
            mApp.reanimationGet(mZombiesWonReanimID).setShakeOverride("ZombiesWon", 0.0f);
        }
        if (mCutsceneTime == LostTimeBrainGraphicEnd) {
            mApp.reanimationGet(mZombiesWonReanimID).setFramesForLayer("anim_screen");
        }

        if (mCutsceneTime == LostTimeEnd) {
            if (mApp.isSurvivalMode()) {
                int aFlagsCompleted = mBoard.getSurvivalFlagsCompleted();
                String aFlagsStr = LawnApp.pluralize(aFlagsCompleted, "[ONE_FLAG]", "[COUNT_FLAGS]");
                String aStr = PvzpCommon.pvzpReplaceString("[SURVIVAL_DEATH_MESSAGE]", "{FLAGS}", aFlagsStr);
                GameOverDialog aDialog = new GameOverDialog(aStr, true);
                mApp.addDialog(Dialogs.DIALOG_GAME_OVER, aDialog);
                mApp.mWidgetManager.setFocus(aDialog);
            } else {
                GameOverDialog aDialog = new GameOverDialog("", false);
                mApp.addDialog(Dialogs.DIALOG_GAME_OVER, aDialog);
                mApp.mWidgetManager.setFocus(aDialog);
            }
        }
    }

    // CutScene.cpp:1560-1564
    public boolean isCutSceneOver() {
        return mCutsceneTime >= LostTimeEnd;
    }

    // CutScene.cpp:1566-1571
    public void zombieWonClick() {
        if (isCutSceneOver() || mApp.mCheatKeys) {
            mApp.endLevel();
        }
    }

    // CutScene.cpp:1573-1690
    public void advanceCrazyDaveDialog(boolean theJustSkipping) {
        if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL || mApp.mCrazyDaveMessageIndex == -1) {
            return;
        }

        // "Pick up the shovel and start digging"
        if (mApp.mCrazyDaveMessageIndex == 2406 && !theJustSkipping) {
            mBoard.setTutorialState(TutorialState.TUTORIAL_SHOVEL_PICKUP);
            mApp.crazyDaveLeave();
            return;
        }
        // "This is your Tree of Wisdom; I'll give you some fertilizer to get started"
        if (mApp.mCrazyDaveMessageIndex == 3200) {
            mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;
            mBoard.mMenuButton.mBtnNoDraw = false;
            mBoard.mStoreButton.mBtnNoDraw = false;
        }

        // Advance Dave's dialog; if there is no next line, Dave leaves
        if (!mApp.advanceCrazyDaveText()) {
            mApp.crazyDaveLeave();
            if (mApp.isFinalBossLevel() && mApp.isAdventureMode()) {
                Reanimation aCrazyDaveReanim = mApp.reanimationTryToGet(mApp.mCrazyDaveReanimID);
                aCrazyDaveReanim.playReanim("anim_grab", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 18.0f);

                mApp.mMusic.fadeOut(50);
                if (!theJustSkipping) {
                    mApp.playSample(Resources.SOUND_BUNGEE_SCREAM_ID);
                }
            } else if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
                mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_ZEN_GARDEN);
            } else {
                if (mBoard.chooseSeedsOnCurrentLevel()) {
                    mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS);
                } else if (isNonScrollingCutscene()) {
                    mApp.mMusic.fadeOut(50);
                }
            }
            return;
        }

        int aMessageIndex = mApp.mCrazyDaveMessageIndex;
        // Now_Unused
        if (aMessageIndex == 107 || aMessageIndex == 2407) {
            mBoard.mChallenge.shovelAddWallnuts();
        }
        // "And it's not a shovel, it's a mallet" || "Let's go bowling!"
        if (aMessageIndex == 405 || aMessageIndex == 2411) {
            mBoard.mChallenge.mShowBowlingLine = 1;
        }
        // (seed slot pitch) "How does that sound?"
        if ((aMessageIndex == 1503 || aMessageIndex == 1553) && !theJustSkipping) {
            int aCost = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);
            int aNumPackets = mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE];
            String aBodyString = PvzpCommon.pvzpReplaceNumberString("[UPGRADE_DIALOG_BODY]", "{SLOTS}", aNumPackets + 7);
            String aAmountString = mApp.getMoneyString(aCost);
            // Ask whether to buy a seed slot upgrade
            Dialog aDialog = mApp.doDialog(Dialogs.DIALOG_PURCHASE_PACKET_SLOT, true, aAmountString, aBodyString, "", Dialog.BUTTONS_YES_NO);
            aDialog.mX += 120;
            aDialog.mY += 130;
            mBoard.showCoinBank(100);
            // PORT-NOTE: CutScene.cpp:1611-1638 — upstream answers the offer right after
            // Dialog::WaitForResult(), which pumps the message loop until a button is depressed
            // (Dialog.cpp:410-441). This port's waitForResult returns without pumping
            // (sexy/Dialog.java:506), so the result read here was always the "not answered yet"
            // sentinel (0x7FFFFFFF): the offer was treated as declined before the player could click
            // (mDidntPurchasePacketUpgrade++ and Dave's "no" line), and buying never happened. The
            // answer is applied by answerPacketUpgradeOffer(), which LawnApp.buttonDepress calls for
            // both buttons — like every other dialog in this port.
            mPacketUpgradeOfferIndex = aMessageIndex;
        }
        // "Of course it wasn't me, it was you!"
        if (aMessageIndex == 406) {
            mBoard.mEnableGraveStones = true;
            addGraveStoneParticles();
        }
    }

    /**
     * CutScene.cpp:1611-1638 — applies the player's answer to the seed-slot offer
     * ({@link #advanceCrazyDaveDialog} only shows the dialog; see the PORT-NOTE there). Upstream runs
     * this immediately after the blocking {@code Dialog::WaitForResult()}; the cost is read from the
     * store table again because the offer can sit on screen for arbitrarily long here.
     */
    public void answerPacketUpgradeOffer(boolean theBuy) {
        int aMessageIndex = mPacketUpgradeOfferIndex;
        mPacketUpgradeOfferIndex = -1;

        if (theBuy) {
            mApp.mPlayerInfo.addCoins(-StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE));
            mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]++;
            mApp.writeCurrentUserConfig();
            // SeedBank::UpdateWidth (SeedPacket.cpp:1165) recomputes mNumPackets from the profile,
            // which is what gives the player the 7th slot right away (Board.cpp:8562).
            mBoard.mSeedBank.updateWidth();

            if (aMessageIndex == 1503) {
                mApp.crazyDaveTalkIndex(1510);
            } else if (aMessageIndex == 1553) {
                mApp.crazyDaveTalkIndex(1560);
            }
        } else {
            mApp.mPlayerInfo.mDidntPurchasePacketUpgrade++;
            if (aMessageIndex == 1503) {
                mApp.crazyDaveTalkIndex(1520);
            } else if (aMessageIndex == 1553) {
                mApp.crazyDaveTalkIndex(1570);
            }
        }
    }

    // CutScene.cpp:1692-1707
    public void mouseDown(int theX, int theY) {
        if (mApp.mCheatKeys && mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            mCrazyDaveCountDown = Math.min(mCrazyDaveCountDown, 1);
        } else {
            if (isShowingCrazyDave()) {
                advanceCrazyDaveDialog(false);
            } else if (mApp.mCheatKeys) {
                cancelIntro();
            }
        }
    }

    // CutScene.cpp:1709-1745
    public void keyDown(int theKey) {
        if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            if (mApp.mCheatKeys && theKey == KeyCode.KEYCODE_ESCAPE) {
                mCrazyDaveLastTalkIndex = 3316; // "Enough to blow your mind to Mars and back!"
                mCrazyDaveCountDown = 1;
            } else if (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN || theKey == KeyCode.KEYCODE_ESCAPE) {
                mApp.crazyDaveStopSound();
                mApp.playSample(Resources.SOUND_PAUSE_ID);
                mApp.mMusic.gameMusicPause(true);

                mApp.lawnMessageBox(
                        Dialogs.DIALOG_MESSAGE,
                        "[UPSELL_PAUSE_HEADER]",
                        "[UPSELL_PAUSE_BODY]",
                        "[UPSELL_RESUME_BUTTON]",
                        "[MAIN_MENU_BUTTON]",
                        Dialog.BUTTONS_YES_NO,
                        theYes -> {
                            if (!theYes) {
                                mApp.killCreditScreen();
                                mApp.doBackToMain();
                            }

                            mApp.mMusic.gameMusicPause(false);
                        });
            }
        } else {
            if ((theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN) && isShowingCrazyDave()) {
                advanceCrazyDaveDialog(false);
            } else if (mApp.mCheatKeys && (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN || theKey == KeyCode.KEYCODE_ESCAPE)) {
                cancelIntro();
            }
        }
    }

    // CutScene.cpp:1747-1760
    public int parseDelayTimeFromMessage() {
        String aCrazyDaveText = mApp.getCrazyDaveText(mCrazyDaveLastTalkIndex);
        int anIndex = aCrazyDaveText.indexOf("{DELAY_");
        if (anIndex != -1) {
            String aDelayTimeStr = aCrazyDaveText.substring(anIndex + 7, aCrazyDaveText.indexOf("}"));
            mCrazyDaveCountDown = Integer.parseInt(aDelayTimeStr.trim());
            return mCrazyDaveCountDown;
        }
        return 100;
    }

    // CutScene.cpp:1762-1775
    public int parseTalkTimeFromMessage() {
        String aCrazyDaveText = mApp.getCrazyDaveText(mCrazyDaveLastTalkIndex);
        int anIndex = aCrazyDaveText.indexOf("{TIME_");
        if (anIndex != -1) {
            String aTalkTimeStr = aCrazyDaveText.substring(anIndex + 6, aCrazyDaveText.indexOf("}"));
            mCrazyDaveCountDown = Integer.parseInt(aTalkTimeStr.trim());
            return mCrazyDaveCountDown;
        }
        return 100;
    }

    // CutScene.cpp:1777-1805
    public void clearUpsellBoard() {
        for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
            mBoard.mIceTimer[i] = 0;
            mBoard.mIceMinX[i] = GameConstants.BOARD_WIDTH;
        }

        mBoard.mZombies.dataArrayFreeAll();
        mBoard.mPlants.dataArrayFreeAll();
        mBoard.mCoins.dataArrayFreeAll();
        mBoard.mProjectiles.dataArrayFreeAll();
        mBoard.mGridItems.dataArrayFreeAll();
        mBoard.mLawnMowers.dataArrayFreeAll();

        for (PvzpParticleSystem aParticle : mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            if (aParticle.mDead) {
                continue;
            }
            aParticle.particleSystemDie();
        }
        int aDaveReanimID = mApp.mCrazyDaveReanimID;
        int aBlinkReanimID = mApp.mCrazyDaveBlinkReanimID;
        for (Reanimation aReanim : mApp.mEffectSystem.mReanimationHolder.mReanimations) {
            if (aReanim.mDead) {
                continue;
            }
            int aReanimID = mApp.reanimationGetID(aReanim);
            if (aReanimID != aDaveReanimID && aReanimID != aBlinkReanimID) {
                aReanim.reanimationDie();
            }
        }
        mBoard.mPoolSparklyParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;

        mUpsellChallengeScreen = null;
    }

    // CutScene.cpp:1807-1814
    public void addUpsellZombie(int theZombieType, int thePixelX, int theGridY) {
        Zombie aZombie = mBoard.addZombieInRow(theZombieType, theGridY, 0);
        aZombie.mPosX = thePixelX;
        aZombie.mPosY = aZombie.getPosYBasedOnRow(theGridY);
        aZombie.setRow(theGridY);
        aZombie.mX = (int) aZombie.mPosX;
        aZombie.mY = (int) aZombie.mPosY;
    }

    // CutScene.cpp:1816-1882 (GAMEMODE_INTRO cinematic board setup)
    public void loadIntroBoard() {
        clearUpsellBoard();
        mApp.mMuteSoundsForCutscene = true;

        mBoard.newPlant(0, 1, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(0, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 3, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(0, 3, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 4, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 0, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 1, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 4, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 5, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 0, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 3, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(2, 3, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 4, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 5, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(3, 0, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(3, 4, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(4, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(4, 2, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(5, 1, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(5, 4, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(5, 5, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(6, 0, SeedType.SEED_SPIKEWEED, SeedType.SEED_NONE);
        mBoard.newPlant(6, 4, SeedType.SEED_SPIKEWEED, SeedType.SEED_NONE);
        mBoard.newPlant(7, 1, SeedType.SEED_SPIKEWEED, SeedType.SEED_NONE);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 460, 0);
        addUpsellZombie(ZombieType.ZOMBIE_FOOTBALL, 680, 0);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 730, 0);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 810, 0);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 670, 1);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 740, 1);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 880, 1);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 500, 2);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 680, 2);
        addUpsellZombie(ZombieType.ZOMBIE_PAIL, 604, 3);
        addUpsellZombie(ZombieType.ZOMBIE_SNORKEL, 880, 3);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 600, 4);
        addUpsellZombie(ZombieType.ZOMBIE_PAIL, 690, 4);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 780, 4);
        addUpsellZombie(ZombieType.ZOMBIE_CATAPULT, 730, 5);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 590, 5);

        mPreUpdatingBoard = true;
        for (int i = 0; i < 100; i++) {
            mBoard.update();
        }
        mPreUpdatingBoard = false;
    }

    // CutScene.cpp:1884-1940 (upsell tableau 1: the back-yard pool)
    public void loadUpsellBoardPool() {
        clearUpsellBoard();
        mApp.mMuteSoundsForCutscene = true;

        mBoard.newPlant(0, 1, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(0, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 3, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(0, 3, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 4, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 0, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 1, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 4, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 5, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 0, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 3, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(2, 3, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 4, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 5, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(3, 4, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(4, 0, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(4, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(4, 2, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(5, 1, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(5, 4, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(5, 5, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
        mBoard.newPlant(6, 0, SeedType.SEED_SPIKEWEED, SeedType.SEED_NONE);
        mBoard.newPlant(6, 3, SeedType.SEED_TANGLEKELP, SeedType.SEED_NONE);
        mBoard.newPlant(6, 4, SeedType.SEED_SPIKEWEED, SeedType.SEED_NONE);
        mBoard.newPlant(6, 5, SeedType.SEED_SQUASH, SeedType.SEED_NONE);
        mBoard.newPlant(7, 1, SeedType.SEED_SPIKEWEED, SeedType.SEED_NONE);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 460, 0);
        addUpsellZombie(ZombieType.ZOMBIE_ZAMBONI, 680, 0);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 670, 1);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 740, 1);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 500, 2);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 680, 2);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 604, 3);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 690, 4);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 740, 4);
        addUpsellZombie(ZombieType.ZOMBIE_PAIL, 730, 5);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 590, 5);

        mPreUpdatingBoard = true;
        for (int i = 0; i < 100; i++) {
            mBoard.update();
        }
        mPreUpdatingBoard = false;
        mApp.mMuteSoundsForCutscene = false;
    }

    // CutScene.cpp:1942-2012 (upsell tableau 2: the fog level)
    public void loadUpsellBoardFog() {
        clearUpsellBoard();
        mApp.mMuteSoundsForCutscene = true;

        mBoard.mBackground = BackgroundType.BACKGROUND_4_FOG;
        mBoard.loadBackgroundImages();

        mBoard.newPlant(0, 1, SeedType.SEED_SUNSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(0, 4, SeedType.SEED_SUNSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(1, 0, SeedType.SEED_SUNSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(1, 1, SeedType.SEED_SUNSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_CACTUS, SeedType.SEED_NONE);
        mBoard.newPlant(1, 4, SeedType.SEED_SUNSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(1, 5, SeedType.SEED_SUNSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(2, 0, SeedType.SEED_CACTUS, SeedType.SEED_NONE);
        mBoard.newPlant(2, 4, SeedType.SEED_CACTUS, SeedType.SEED_NONE);
        mBoard.newPlant(2, 5, SeedType.SEED_FUMESHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(3, 1, SeedType.SEED_FUMESHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(3, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(3, 3, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(3, 3, SeedType.SEED_CACTUS, SeedType.SEED_NONE);
        mBoard.newPlant(3, 5, SeedType.SEED_PUFFSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(4, 0, SeedType.SEED_PUFFSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(4, 1, SeedType.SEED_MAGNETSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(4, 2, SeedType.SEED_SEASHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(4, 5, SeedType.SEED_PUFFSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(5, 1, SeedType.SEED_PUFFSHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(5, 2, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        mBoard.newPlant(5, 2, SeedType.SEED_PLANTERN, SeedType.SEED_NONE);
        mBoard.newPlant(5, 3, SeedType.SEED_SEASHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(6, 2, SeedType.SEED_SEASHROOM, SeedType.SEED_NONE);
        mBoard.newPlant(6, 3, SeedType.SEED_SEASHROOM, SeedType.SEED_NONE);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 460, 0);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 680, 0);
        addUpsellZombie(ZombieType.ZOMBIE_BALLOON, 780, 0);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 670, 1);
        addUpsellZombie(ZombieType.ZOMBIE_BALLOON, 640, 1);
        addUpsellZombie(ZombieType.ZOMBIE_PAIL, 640, 2);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 780, 3);
        addUpsellZombie(ZombieType.ZOMBIE_BALLOON, 704, 4);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 690, 4);
        addUpsellZombie(ZombieType.ZOMBIE_PAIL, 590, 5);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 740, 5);

        mPreUpdatingBoard = true;
        for (int i = 0; i < 100; i++) {
            mBoard.update();
        }
        mPreUpdatingBoard = false;
        mApp.mMuteSoundsForCutscene = false;
    }

    // CutScene.cpp:2014-2018 (upsell tableau 3: the mini-games & puzzles challenge screen)
    public void loadUpsellChallengeScreen() {
        clearUpsellBoard();
        mUpsellChallengeScreen = new ChallengeScreen(mApp, ChallengePage.CHALLENGE_PAGE_CHALLENGE);
    }

    // CutScene.cpp:2020-2108 (upsell tableau 4: the roof level)
    public void loadUpsellBoardRoof() {
        clearUpsellBoard();
        mApp.mMuteSoundsForCutscene = true;

        mBoard.mBackground = BackgroundType.BACKGROUND_5_ROOF;
        mBoard.loadBackgroundImages();
        mBoard.mPlantRow[0] = PlantRowType.PLANTROW_NORMAL;
        mBoard.mPlantRow[1] = PlantRowType.PLANTROW_NORMAL;
        mBoard.mPlantRow[2] = PlantRowType.PLANTROW_NORMAL;
        mBoard.mPlantRow[3] = PlantRowType.PLANTROW_NORMAL;
        mBoard.mPlantRow[4] = PlantRowType.PLANTROW_NORMAL;
        mBoard.mPlantRow[5] = PlantRowType.PLANTROW_DIRT;
        for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
            for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                if (mBoard.mPlantRow[y] == PlantRowType.PLANTROW_DIRT) {
                    mBoard.mGridSquareType[x][y] = GridSquareType.GRIDSQUARE_DIRT;
                } else {
                    mBoard.mGridSquareType[x][y] = GridSquareType.GRIDSQUARE_GRASS;
                }
            }
        }

        mBoard.newPlant(0, 0, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(0, 0, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(0, 1, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(0, 1, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(0, 2, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(0, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 3, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(0, 3, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(0, 4, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(0, 4, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 0, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 0, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 1, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 1, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 2, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 3, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 3, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 4, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(1, 4, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 0, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 0, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 1, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 1, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 2, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 2, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 3, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 3, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(2, 4, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(2, 4, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(3, 1, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(3, 1, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(3, 2, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(3, 2, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(3, 3, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(3, 3, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mBoard.newPlant(3, 4, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(3, 4, SeedType.SEED_CABBAGEPULT, SeedType.SEED_NONE);
        mBoard.newPlant(4, 0, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(4, 0, SeedType.SEED_CHOMPER, SeedType.SEED_NONE);
        mBoard.newPlant(4, 1, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(4, 1, SeedType.SEED_CHOMPER, SeedType.SEED_NONE);
        mBoard.newPlant(4, 2, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(4, 2, SeedType.SEED_REPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(4, 3, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(5, 2, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(5, 2, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        mBoard.newPlant(5, 3, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(5, 3, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
        mBoard.newPlant(5, 4, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
        mBoard.newPlant(5, 4, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 460, 0);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 680, 0);
        addUpsellZombie(ZombieType.ZOMBIE_CATAPULT, 780, 1);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 670, 1);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 580, 0);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 540, 1);
        addUpsellZombie(ZombieType.ZOMBIE_PAIL, 500, 1);
        addUpsellZombie(ZombieType.ZOMBIE_PAIL, 640, 2);
        addUpsellZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, 780, 3);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 380, 3);
        addUpsellZombie(ZombieType.ZOMBIE_CATAPULT, 704, 4);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 690, 4);
        addUpsellZombie(ZombieType.ZOMBIE_NORMAL, 590, 4);

        mPreUpdatingBoard = true;
        for (int k = 0; k < 100; k++) {
            mBoard.update();
        }
        mPreUpdatingBoard = false;
        mApp.mMuteSoundsForCutscene = false;
    }

    // CutScene.cpp:2110-2209 (the dialog-driven upsell sequence)
    public void updateUpsell() {
        if (!mBoard.mMenuButton.mIsOver && !mBoard.mStoreButton.mIsOver) {
            mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }
        if (mApp.mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_OFF || mApp.mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_ENTERING) {
            return;
        }

        if (mCrazyDaveLastTalkIndex == -1) {
            mApp.crazyDaveTalkIndex(mCrazyDaveDialogStart);
            mCrazyDaveLastTalkIndex = mCrazyDaveDialogStart;
            mCrazyDaveDialogStart = -1;
            mCrazyDaveCountDown = parseTalkTimeFromMessage();
            return;
        }

        if (mCrazyDaveCountDown > 0) {
            mCrazyDaveCountDown--;
        }

        // "Uh, what are you waiting for?"
        if (mCrazyDaveLastTalkIndex == 3317) {
            if (mCrazyDaveCountDown == 0) {
                mBoard.mStoreButton.resize(510, 420, 210, 46);
                mBoard.mMenuButton.resize(510, 480, 210, 46);
                mBoard.mMenuButton.mBtnNoDraw = false;
                mBoard.mStoreButton.mBtnNoDraw = false;
            }
            return;
        }
        // "You want to take action?"
        if (mCrazyDaveLastTalkIndex == 3311 && mCrazyDaveCountDown == 90) {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON);
        }

        if (mCrazyDaveCountDown != 0) {
            return;
        }

        if (mApp.mCrazyDaveMessageIndex != -1) {
            mCrazyDaveCountDown = parseDelayTimeFromMessage();
            mApp.crazyDaveStopTalking();
            return;
        }

        mApp.crazyDaveTalkIndex(mCrazyDaveLastTalkIndex + 1);
        ++mCrazyDaveLastTalkIndex;
        mCrazyDaveCountDown = parseTalkTimeFromMessage();

        Reanimation aCrazyDaveReanim = mApp.reanimationTryToGet(mApp.mCrazyDaveReanimID);
        switch (mCrazyDaveLastTalkIndex) {
            case 3305: {  // "Like this!"
                Reanimation aReanimSquash = mApp.addReanimation(0, 0, 0, ReanimationType.REANIM_SQUASH);
                aReanimSquash.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
                AttachEffect anAttachEffect = Attachment.attachReanim(aCrazyDaveReanim.getTrackInstanceByName("Dave_handinghand").mAttachmentID, aReanimSquash, 92.0f, 387.0f);
                anAttachEffect.mOffset.m00 = 1.2f;
                anAttachEffect.mOffset.m11 = 1.2f;
                aCrazyDaveReanim.update();
                break;
            }

            case 3306: {  // "And this!"
                Reanimation aReanimThreepeater = mApp.addReanimation(0, 0, 0, ReanimationType.REANIM_THREEPEATER);
                aReanimThreepeater.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
                for (int i = 1; i < 4; i++) {
                    Reanimation aReanimHead = mApp.addReanimation(0, 0, 0, ReanimationType.REANIM_THREEPEATER);
                    aReanimHead.mLoopType = ReanimLoopType.REANIM_LOOP;
                    aReanimHead.mAnimRate = aReanimThreepeater.mAnimRate;
                    aReanimHead.setFramesForLayer("anim_head_idle" + i);
                    aReanimHead.attachToAnotherReanimation(aReanimThreepeater, "anim_head" + i);
                }
                AttachEffect anAttachEffect = Attachment.attachReanim(aCrazyDaveReanim.getTrackInstanceByName("Dave_body1").mAttachmentID, aReanimThreepeater, 0.0f, 0.0f);
                PvzpCommon.pvzpScaleRotateTransformMatrix(anAttachEffect.mOffset, -70.0f, 260.0f, 0.5f, 1.2f, 1.2f);
                aCrazyDaveReanim.update();
                aReanimThreepeater.update();
                break;
            }

            case 3307: {  // "Later, I'll add this too!"
                Reanimation aReanimMagnet = mApp.addReanimation(0, 0, 0, ReanimationType.REANIM_MAGNETSHROOM);
                aReanimMagnet.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
                PvzpCommon.pvzpScaleRotateTransformMatrix(aReanimMagnet.mOverlayMatrix, 0, 0, 0.3f, 1, 1);
                AttachEffect anAttachEffect = Attachment.attachReanim(aCrazyDaveReanim.getTrackInstanceByName("Dave_pot").mAttachmentID, aReanimMagnet, 25.0f, 49.0f);
                anAttachEffect.mOffset.m00 = 1.2f;
                anAttachEffect.mOffset.m11 = 1.2f;
                aCrazyDaveReanim.update();
                break;
            }

            case 3309: {  // "Because I'm cra-zy!!!!"
                Reanimation aThreepeater = aCrazyDaveReanim.findSubReanim(ReanimationType.REANIM_THREEPEATER);
                if (aThreepeater != null) {
                    aThreepeater.reanimationDie();
                }
                Reanimation aMagnet = aCrazyDaveReanim.findSubReanim(ReanimationType.REANIM_MAGNETSHROOM);
                if (aMagnet != null) {
                    aMagnet.reanimationDie();
                }
                break;
            }

            case 3312:  // "I'll give you more battles!"
                mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON);
                loadUpsellBoardPool();
                mApp.playSample(Resources.SOUND_FINALWAVE_ID);
                mUpsellHideBoard = false;
                break;

            case 3313:  // "25 more levels of battles!"
                loadUpsellBoardFog();
                mApp.playSample(Resources.SOUND_HUGE_WAVE_ID);
                mUpsellHideBoard = false;
                break;

            case 3314:  // "40 mini-games & puzzles!"
                loadUpsellChallengeScreen();
                mApp.playSample(Resources.SOUND_FINALWAVE_ID);
                mUpsellHideBoard = false;
                break;

            case 3315:  // "Terra cotta!!!"
                clearUpsellBoard();
                mApp.playSample(Resources.SOUND_FINALWAVE_ID);
                mUpsellHideBoard = true;
                mApp.addPvzpParticle(592, 240, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_SCREEN_FADE, 0, 0), ParticleEffect.PARTICLE_PERSENT_PICK_UP_ARROW);
                break;

            case 3316:  // "Enough to blow your mind to Mars and back!"
                loadUpsellBoardRoof();
                mApp.playSample(Resources.SOUND_HUGE_WAVE_ID);
                mUpsellHideBoard = false;
                break;

            case 3317:  // "Uh, what are you waiting for?"
                clearUpsellBoard();
                mBoard.mMenuButton.mBtnNoDraw = true;
                mUpsellHideBoard = true;
                break;

            default:
                break;
        }
    }

    // CutScene.cpp:2211-2229
    public void drawUpsell(Graphics g) {
        if (mCrazyDaveLastTalkIndex == 3315) {  // "Terra cotta!"
            Reanimation aReanim = new Reanimation();
            aReanim.reanimationInitializeType(565, 360, ReanimationType.REANIM_FLOWER_POT);
            aReanim.setFramesForLayer("anim_zengarden");
            aReanim.overrideScale(1.3f, 1.3f);
            aReanim.draw(g);
            mBoard.mMenuButton.draw(g);
            aReanim.reanimationDie();
        }

        if (mUpsellChallengeScreen != null) {
            mUpsellChallengeScreen.draw(g);
            mBoard.mMenuButton.draw(g);
        }
    }

    // CutScene.cpp:2231-2262
    public void updateIntro() {
        mBoard.move(-PvzpCommon.pvzpAnimateCurve(TimeIntro_PanRightStart, TimeIntro_PanRightEnd, mCutsceneTime, -100, 100, PvzpCurves.CURVE_LINEAR), 0);

        if (mCutsceneTime == 10) {
            loadIntroBoard();
        }
        if (mCutsceneTime == TimeIntro_FadeOut) {
            mApp.mMusic.fadeOut(250);
        }
        if (mCutsceneTime == TimeIntro_LogoEnd) {
            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0);
            mApp.addPvzpParticle(400, 300, aRenderPosition, ParticleEffect.PARTICLE_SCREEN_FLASH);

            mApp.mMuteSoundsForCutscene = false;
            mApp.playSample(Resources.SOUND_HUGE_WAVE_ID);
            mApp.mMuteSoundsForCutscene = true;
        }
        if (mCutsceneTime == TimeIntro_FadeOut - 200) {
            mApp.mMuteSoundsForCutscene = false;
            mApp.playSample(Resources.SOUND_SIREN_ID);
            mApp.mMuteSoundsForCutscene = true;
        }
        if (mCutsceneTime == TimeIntro_End) {
            mApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
        }
    }

    // CutScene.cpp:2264-2308
    public void drawIntro(Graphics g) {
        if (mCutsceneTime <= TimeIntro_PanRightStart || mCutsceneTime > TimeIntro_FadeOutEnd) {
            g.setColor(Color.Black);
            g.fillRect(-mBoard.mX, -mBoard.mY, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        }

        // Draw the "PopCap Games presents" text
        int aTimePanRightStart = TimeIntro_PanRightStart - TimeIntro_PresentsFadeIn;
        if (mCutsceneTime > TimeIntro_PresentsFadeIn && mCutsceneTime <= aTimePanRightStart) {
            int anAlpha = mCutsceneTime < aTimePanRightStart - 600
                    ? PvzpCommon.pvzpAnimateCurve(TimeIntro_PresentsFadeIn, TimeIntro_PresentsFadeIn + 300, mCutsceneTime, 0, 255, PvzpCurves.CURVE_LINEAR)
                    : PvzpCommon.pvzpAnimateCurve(aTimePanRightStart - 600, aTimePanRightStart - 300, mCutsceneTime, 255, 0, PvzpCurves.CURVE_LINEAR);

            PvzpCommon.pvzpDrawString(
                    g,
                    PvzpStringFile.translate("[INTRO_PRESENTS]"),
                    GameConstants.BOARD_WIDTH / 2 - mBoard.mX,
                    310 - mBoard.mY,
                    Resources.getFontById(Resources.FONT_BRIANNETOD32_ID),
                    new Color(255, 255, 255, anAlpha),
                    DrawStringJustification.DS_ALIGN_CENTER
            );
        }

        // Draw the "Plants Vs Zombies" logo
        if (mCutsceneTime > TimeIntro_LogoStart && mCutsceneTime <= TimeIntro_PanRightEnd) {
            float aScale = PvzpCommon.pvzpAnimateCurveFloat(TimeIntro_LogoStart, TimeIntro_LogoEnd, mCutsceneTime, 5, 1, PvzpCurves.CURVE_EASE_OUT);
            float aCenter = aScale * 0.5f;
            int aOffsetX = GameConstants.BOARD_WIDTH / 2 - mBoard.mX;
            int aOffsetY = GameConstants.BOARD_HEIGHT / 2 - mBoard.mY;
            Rect aRect = new Rect(
                    aOffsetX - (int) (GameConstants.BOARD_WIDTH * aCenter),
                    aOffsetY - (int) (75 * aScale),
                    (int) (GameConstants.BOARD_WIDTH * aScale),
                    (int) (150 * aScale));
            g.setColor(new Color(0, 0, 0, 128));
            g.fillRect(aRect);
            Image aImage = Resources.getImageById(ImageID.IMAGE_PVZ_LOGO);
            PvzpCommon.pvzpDrawImageScaledF(g, aImage, aOffsetX - aImage.getWidth() * aCenter, aOffsetY - aImage.getHeight() * aCenter, aScale, aScale);
        }

        if (mCutsceneTime > TimeIntro_FadeOut && mCutsceneTime <= TimeIntro_FadeOutEnd) {
            g.setColor(new Color(0, 0, 0, PvzpCommon.pvzpAnimateCurve(TimeIntro_FadeOut, TimeIntro_FadeOutEnd, mCutsceneTime, 0, 255, PvzpCurves.CURVE_LINEAR)));
            g.fillRect(-mBoard.mX, -mBoard.mY, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        }
    }

    // CutScene.cpp:2310-2313
    public boolean shouldRunUpsellBoard() {
        return (mApp.mGameMode == GameMode.GAMEMODE_UPSELL || mApp.mGameMode == GameMode.GAMEMODE_INTRO) && !mUpsellHideBoard;
    }

    // CutScene.cpp:2315-2318
    public boolean isAfterSeedChooser() {
        return mCutsceneTime > TimeSeedChoserSlideOffStart + mCrazyDaveTime;
    }

    // CutScene.cpp:2320-2323
    public boolean showZombieWalking() {
        return mCutsceneTime > LostTimePanRightStart;
    }
}
