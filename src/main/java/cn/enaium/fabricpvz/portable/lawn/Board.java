/*
 * Ported from src/Lawn/Board.h:1-511 and src/Lawn/Board.cpp:1-9499.
 *
 * The level/board layer: grid model, planting rules, entity arrays, the update pipeline, the
 * wave system, the sun economy, lawn mowers, the progress meter, win/lose transitions, mouse
 * routing and the render list.
 *
 * Porting notes:
 *  - Public fields keep the upstream `m`-prefix and names; methods are camelCase of the upstream
 *    PascalCase names (API contract §6).
 *  - Every upstream C++ enum is a plain `int` here (see the GameEnums constant classes).
 *  - `std::sort` on raw pointers (Board.cpp:5926) is replaced by the monotonic `mAllocId`
 *    (Board.h:64-85) so the render order is bit-identical to upstream allocation order.
 *  - The Zen Garden / Tree of Wisdom branches now call the upstream `ZenGarden`/`Challenge` APIs;
 *    genuinely unreachable `PVZP_ASSERT(false); unreachable();` tails are reproduced as defensive
 *    {@link IllegalStateException} (never a silent no-op where upstream aborts).
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.widget.GameButton;
import cn.enaium.fabricpvz.portable.lawn.widget.GameOverDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen;
import cn.enaium.fabricpvz.portable.lawn.system.SaveGame;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager;
import cn.enaium.fabricpvz.portable.pvzp.PvzpSmoothArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;

/**
 * Ported from src/Lawn/Board.h:98 (declaration) / src/Lawn/Board.cpp:72 (constructor).
 */
public class Board extends Widget implements ButtonListener {

    // Board.h:42-49
    public static final int MAX_GRID_SIZE_X = 9;
    public static final int MAX_GRID_SIZE_Y = 6;
    public static final int MAX_ZOMBIES_IN_WAVE = 50;
    public static final int MAX_ZOMBIE_WAVES = 100;
    public static final int MAX_GRAVE_STONES = MAX_GRID_SIZE_X * MAX_GRID_SIZE_Y;
    public static final int MAX_POOL_GRID_SIZE = 10;
    public static final int MAX_RENDER_ITEMS = 2048;
    public static final int PROGRESS_METER_COUNTER = 150;

    // Board.cpp:59-68 (file statics)
    private static final int ZOMBIE_COUNTDOWN_FIRST_WAVE = 1800;
    private static final int ZOMBIE_COUNTDOWN = 2500;
    private static final int ZOMBIE_COUNTDOWN_RANGE = 600;
    private static final int ZOMBIE_COUNTDOWN_BEFORE_FLAG = 4500;
    private static final int ZOMBIE_COUNTDOWN_BEFORE_REPICK = 5499;
    private static final int SUN_COUNTDOWN = 425;
    private static final int SUN_COUNTDOWN_RANGE = 275;
    private static final int SUN_COUNTDOWN_MAX = 950;
    private static final int FOG_BLOW_RETURN_TIME = 2000;
    private static final int FLAG_RAISE_TIME = 100;

    // Board.cpp:70 (extern bool gShownMoreSunTutorial)
    public static boolean gShownMoreSunTutorial = false;

    // ---------------------------------------------------------------------------------------
    // Board.h:98-245 — fields
    // ---------------------------------------------------------------------------------------

    public LawnApp mApp;
    public DataArray<Zombie> mZombies;
    public DataArray<Plant> mPlants;
    public DataArray<Projectile> mProjectiles;
    public DataArray<Coin> mCoins;
    public DataArray<LawnMower> mLawnMowers;
    public DataArray<GridItem> mGridItems;
    public CursorObject mCursorObject;
    public CursorPreview mCursorPreview;
    public MessageWidget mAdvice;
    public SeedBank mSeedBank;
    public GameButton mMenuButton;
    public GameButton mStoreButton;
    public boolean mIgnoreMouseUp;
    public ToolTipWidget mToolTip;
    public CutScene mCutScene;
    public Challenge mChallenge;
    public boolean mPaused;
    public int[][] mGridSquareType = new int[MAX_GRID_SIZE_X][MAX_GRID_SIZE_Y];
    public int[][] mGridCelLook = new int[MAX_GRID_SIZE_X][MAX_GRID_SIZE_Y];
    public int[][][] mGridCelOffset = new int[MAX_GRID_SIZE_X][MAX_GRID_SIZE_Y][2];
    public int[][] mGridCelFog = new int[MAX_GRID_SIZE_X][MAX_GRID_SIZE_Y + 1];
    public boolean mEnableGraveStones;
    public int mSpecialGraveStoneX;
    public int mSpecialGraveStoneY;
    public float mFogOffset;
    public int mFogBlownCountDown;
    public int[] mPlantRow = new int[MAX_GRID_SIZE_Y];
    public int[] mWaveRowGotLawnMowered = new int[MAX_GRID_SIZE_Y];
    public int mBonusLawnMowersRemaining;
    public int[] mIceMinX = new int[MAX_GRID_SIZE_Y];
    public int[] mIceTimer = new int[MAX_GRID_SIZE_Y];
    public int[] mIceParticleID = new int[MAX_GRID_SIZE_Y];
    /**
     * Upstream keeps this a <em>value</em> array ({@code PvzpSmoothArray mRowPickingArray[MAX_GRID_SIZE_Y]},
     * memset in Board.cpp:1361), so every slot is always a live object. {@code InitLevel} only fills in
     * the values, and a board restored by {@code SaveGame} never runs it at all - a null slot here made
     * the next save (pause -> back to main menu, the level-start autosave) throw
     * {@code NullPointerException: Cannot read field "mItem" because "aArray" is null}.
     */
    public PvzpSmoothArray[] mRowPickingArray = newSmoothRows();

    private static PvzpSmoothArray[] newSmoothRows() {
        PvzpSmoothArray[] aRows = new PvzpSmoothArray[MAX_GRID_SIZE_Y];
        for (int i = 0; i < aRows.length; i++) {
            aRows[i] = new PvzpSmoothArray();
        }
        return aRows;
    }
    public int[][] mZombiesInWave = new int[MAX_ZOMBIE_WAVES][MAX_ZOMBIES_IN_WAVE];
    public boolean[] mZombieAllowed = new boolean[100];
    public int mSunCountDown;
    public int mNumSunsFallen;
    public int mShakeCounter;
    public int mShakeAmountX;
    public int mShakeAmountY;
    public int mBackground;
    public int mLevel;
    public int mSodPosition;
    public int mPrevMouseX;
    public int mPrevMouseY;
    public int mSunMoney;
    public int mNumWaves;
    public int mMainCounter;
    public int mEffectCounter;
    public int mBoardUpdateCounter; // ticks since level start; unlike mMainCounter it also advances outside SCENE_PLAYING
    public int mDrawCount;
    public int mRiseFromGraveCounter;
    public int mOutOfMoneyCounter;
    public int mCurrentWave;
    public int mTotalSpawnedWaves;
    public int mTutorialState;
    public int mTutorialParticleID;
    public int mTutorialTimer;
    public int mLastBungeeWave;
    public int mZombieHealthToNextWave;
    public int mZombieHealthWaveStart;
    public int mZombieCountDown;
    public int mZombieCountDownStart;
    public int mHugeWaveCountDown;
    public boolean[] mHelpDisplayed = new boolean[AdviceType.NUM_ADVICE_TYPES];
    public int mHelpIndex;
    public boolean mFinalBossKilled;
    public boolean mShowShovel;
    public int mCoinBankFadeCount;
    public int mDebugTextMode;
    public boolean mLevelComplete;
    public int mBoardFadeOutCounter;
    public int mNextSurvivalStageCounter;
    public int mScoreNextMowerCounter;
    public boolean mLevelAwardSpawned;
    public int mProgressMeterWidth;
    public int mFlagRaiseCounter;
    public int mIceTrapCounter;
    public int mBoardRandSeed;
    public int mPoolSparklyParticleID;
    public int[][] mFwooshID = new int[MAX_GRID_SIZE_Y][12];
    public int mFwooshCountDown;
    public int mTimeStopCounter;
    public boolean mDroppedFirstCoin;
    public int mFinalWaveSoundCounter;
    public int mCobCannonCursorDelayCounter;
    public int mCobCannonMouseX;
    public int mCobCannonMouseY;
    public boolean mKilledYeti;
    public boolean mMustacheMode;
    public boolean mSuperMowerMode;
    public boolean mFutureMode;
    public boolean mPinataMode;
    public boolean mDanceMode;
    public boolean mDaisyMode;
    public boolean mSukhbirMode;
    public int mPrevBoardResult;
    public int mTriggeredLawnMowers;
    public int mPlayTimeActiveLevel;
    public int mPlayTimeInactiveLevel;
    public int mMaxSunPlants;
    public long mStartDrawTime;
    public long mIntervalDrawTime;
    public int mIntervalDrawCountStart;
    public float mMinFPS;
    public int mPreloadTime;
    public long mGameID;
    public int mGravesCleared;
    public int mPlantsEaten;
    public int mPlantsShoveled;
    public boolean mPeaShooterUsed; // +0x5784
    public boolean mCatapultPlantsUsed; // +0x5785
    public boolean mMushroomAndCoffeeBeansOnly; // +0x5790
    public boolean mMushroomsUsed; // +0x5791
    public int mLevelCoinsCollected; // +0x5788
    public int mGargantuarsKillsByCornCob; // +0x578C
    public int mCoinsCollected; // +0x57C8
    public int mDiamondsCollected; // +0x57CC
    public int mPottedPlantsCollected;
    public int mChocolateCollected;

    /** Last hover hit-test result (upstream Board.h:251 uses a stack local + member mHitResult). */
    public HitResult mHitResult = new HitResult();

    /**
     * Board.cpp:784-836 (LoadBackgroundImages) pushes the background resource names into this list;
     * upstream declares it outside Board.cpp (delayed-load support), so the port declares it here.
     */
    public final List<String> mLoadedResourceNames = new ArrayList<>();

    // ---------------------------------------------------------------------------------------
    // Constructor / disposal
    // ---------------------------------------------------------------------------------------

    // Board.cpp:72-245
    public Board(LawnApp theApp) {
        mApp = theApp;
        mApp.mBoard = this;

        mZombies = new DataArray<>(Zombie::new, 1024, "zombies");
        mPlants = new DataArray<>(Plant::new, 1024, "plants");
        mProjectiles = new DataArray<>(Projectile::new, 1024, "projectiles");
        mCoins = new DataArray<>(Coin::new, 1024, "coins");
        mLawnMowers = new DataArray<>(LawnMower::new, 32, "lawnmowers");
        mGridItems = new DataArray<>(GridItem::new, 128, "griditems");

        mApp.mEffectSystem.effectSystemFreeAll();
        mBoardRandSeed = mApp.mAppRandSeed;
        if (mApp.isSurvivalMode()) {
            mBoardRandSeed = Common.rand();
        }
        mCoinBankFadeCount = 0;
        mLevel = 0;
        mCursorObject = new CursorObject();
        mCursorPreview = new CursorPreview();
        mSeedBank = new SeedBank();
        mCutScene = new CutScene();
        mSpecialGraveStoneX = -1;
        mSpecialGraveStoneY = -1;
        for (int i = 0; i < MAX_GRID_SIZE_X; i++) {
            for (int j = 0; j < MAX_GRID_SIZE_Y; j++) {
                mGridSquareType[i][j] = GridSquareType.GRIDSQUARE_GRASS;
                mGridCelLook[i][j] = Common.rand(20);
                mGridCelOffset[i][j][0] = Common.rand(10) - 5;
                mGridCelOffset[i][j][1] = Common.rand(10) - 5;
            }

            for (int k = 0; k < MAX_GRID_SIZE_Y + 1; k++) {
                mGridCelFog[i][k] = 0;
            }
        }
        mFogOffset = 0.0f;
        mSunCountDown = 0;
        mShakeCounter = 0;
        mShakeAmountX = 0;
        mShakeAmountY = 0;
        mPaused = false;
        mLevelAwardSpawned = false;
        mFlagRaiseCounter = 0;
        mIceTrapCounter = 0;
        mLevelComplete = false;
        mBoardFadeOutCounter = -1;
        mNextSurvivalStageCounter = 0;
        mScoreNextMowerCounter = 0;
        mProgressMeterWidth = 0;
        mPoolSparklyParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        mFogBlownCountDown = 0;
        mFwooshCountDown = 0;
        mTimeStopCounter = 0;
        mCobCannonCursorDelayCounter = 0;
        mCobCannonMouseX = 0;
        mCobCannonMouseY = 0;
        mDroppedFirstCoin = false;
        mBonusLawnMowersRemaining = 0;
        mEnableGraveStones = false;
        mHelpIndex = AdviceType.ADVICE_NONE;
        mEffectCounter = 0;
        mDrawCount = 0;
        mRiseFromGraveCounter = 0;
        mFinalWaveSoundCounter = 0;
        mKilledYeti = false;
        mTriggeredLawnMowers = 0;
        mPlayTimeActiveLevel = 0;
        mPlayTimeInactiveLevel = 0;
        mMaxSunPlants = 0;
        mStartDrawTime = 0;
        mIntervalDrawTime = 0;
        mIntervalDrawCountStart = 0;
        mPreloadTime = 0;
        mGameID = mApp.getNowTime();
        mMinFPS = 1000.0f;
        mGravesCleared = 0;
        mPlantsEaten = 0;
        mPlantsShoveled = 0;
        mPeaShooterUsed = false;
        mCatapultPlantsUsed = false;
        mMushroomAndCoffeeBeansOnly = true;
        mMushroomsUsed = false;
        mLevelCoinsCollected = 0;
        mGargantuarsKillsByCornCob = 0;
        mCoinsCollected = 0;
        mDiamondsCollected = 0;
        mPottedPlantsCollected = 0;
        mChocolateCollected = 0;
        for (int y = 0; y < MAX_GRID_SIZE_Y; y++) {
            for (int x = 0; x < 12; x++) {
                mFwooshID[y][x] = ReanimationID.REANIMATIONID_NULL;
            }
        }
        mPrevMouseX = -1;
        mPrevMouseY = -1;
        mFinalBossKilled = false;
        mMustacheMode = mApp.mMustacheMode;
        mSuperMowerMode = mApp.mSuperMowerMode;
        mFutureMode = mApp.mFutureMode;
        mPinataMode = mApp.mPinataMode;
        mDanceMode = mApp.mDanceMode;
        mDaisyMode = mApp.mDaisyMode;
        mSukhbirMode = mApp.mSukhbirMode;
        mShowShovel = false;
        mToolTip = new ToolTipWidget();
        mAdvice = new MessageWidget(mApp);
        mBackground = BackgroundType.BACKGROUND_1_DAY;
        mMainCounter = 0;
        mBoardUpdateCounter = 0;
        mTutorialState = TutorialState.TUTORIAL_OFF;
        mTutorialTimer = -1;
        mTutorialParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        mChallenge = new Challenge(this);
        mClip = false;
        mDebugTextMode = DebugTextMode.DEBUG_TEXT_NONE;
        mMenuButton = new GameButton(0);
        mMenuButton.mDrawStoneButton = true;
        mStoreButton = null;
        mIgnoreMouseUp = false;

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN || mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            mMenuButton.setLabel("[MAIN_MENU_BUTTON]");
            mMenuButton.resize(628, -10, 163, 46);

            mStoreButton = new GameButton(1);
            mStoreButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_ZENSHOPBUTTON);
            mStoreButton.mOverImage = Resources.getImageById(ImageID.IMAGE_ZENSHOPBUTTON_HIGHLIGHT);
            mStoreButton.mDownImage = Resources.getImageById(ImageID.IMAGE_ZENSHOPBUTTON_HIGHLIGHT);
            mStoreButton.mParentWidget = this;
            mStoreButton.resize(678, 33, Resources.getImageById(ImageID.IMAGE_ZENSHOPBUTTON).getWidth(), 40);
        } else {
            mMenuButton.setLabel("[MENU_BUTTON]");
            mMenuButton.resize(681, -10, 117, 46);
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            mStoreButton = new GameButton(1);
            mStoreButton.mDrawStoneButton = true;
            mStoreButton.mBtnNoDraw = true;
            mStoreButton.mDisabled = true;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            mMenuButton.setLabel("[MAIN_MENU_BUTTON]");
            mMenuButton.resize(628, -10, 163, 46);

            mStoreButton = new GameButton(1);
            mStoreButton.mDrawStoneButton = true;
            mStoreButton.mBtnNoDraw = true;
            mStoreButton.setLabel("[GET_FULL_VERSION_BUTTON]");
        }
    }

    // Board.cpp:243
    public void disposeBoard() {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            mApp.mZenGarden.leaveGarden();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            mChallenge.treeOfWisdomLeave();
        }

        mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_RAIN);
        mApp.mZenGarden.mBoard = null;
        mApp.crazyDaveDie();
        mApp.mEffectSystem.effectSystemFreeAll();
    }

    /** Ported from src/Lawn/Board.cpp:246 (BoardInitForPlayer). */
    public static void boardInitForPlayer() {
        gShownMoreSunTutorial = false;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:258-315 — on-screen zombie counters
    // ---------------------------------------------------------------------------------------

    // Board.cpp:258
    public boolean areEnemyZombiesOnScreen() {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mHasHead && !aZombie.isDeadOrDying() && !aZombie.mMindControlled) {
                return true;
            }
        }
        return false;
    }

    // Board.cpp:272
    public int countZombiesOnScreen() {
        int aCount = 0;
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mHasHead && !aZombie.isDeadOrDying() && !aZombie.mMindControlled && aZombie.isOnBoard()) {
                aCount++;
            }
        }
        return aCount;
    }

    // Board.cpp:287
    public int getLiveGargantuarCount() {
        int aCount = 0;
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mHasHead && !aZombie.isDeadOrDying() && aZombie.isOnBoard()
                    && (aZombie.mZombieType == ZombieType.ZOMBIE_GARGANTUAR || aZombie.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR)) {
                aCount++;
            }
        }
        return aCount;
    }

    // Board.cpp:301
    public int countUntriggerLawnMowers() {
        int aCount = 0;
        for (LawnMower aLawnMower : mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            if (aLawnMower.mMowerState != LawnMowerState.MOWER_TRIGGERED && aLawnMower.mMowerState != LawnMowerState.MOWER_SQUISHED) {
                aCount++;
            }
        }
        return aCount;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:316-371 — save game plumbing (SaveGame.cpp is a different subsystem)
    // ---------------------------------------------------------------------------------------

    // Board.cpp:316
    public void tryToSaveGame() {
        String aFileName = LawnCommon.getSavedGameName(mApp.mGameMode, mApp.mPlayerInfo.mId);
        if (needSaveGame()) {
            if (mBoardFadeOutCounter > 0) {
                completeEndLevelSequenceForSaving();
                return;
            }
            // MkDir(GetAppDataPath("userdata")): lawnSaveGame creates the parent directory itself.
            mApp.mMusic.gameMusicPause(true);
            SaveGame.lawnSaveGame(this, aFileName);
            mApp.clearUpdateBacklog(false);
            survivalSaveScore();
        }
    }

    // Board.cpp:336
    public boolean needSaveGame() {
        return mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ICE
                && mApp.mGameMode != GameMode.GAMEMODE_UPSELL
                && mApp.mGameMode != GameMode.GAMEMODE_INTRO
                && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && mApp.mGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM
                && mApp.mGameScene == GameScenes.SCENE_PLAYING;
    }

    // Board.cpp:347
    public void saveGame(String theFileName) {
        SaveGame.lawnSaveGame(this, theFileName);
    }

    // Board.cpp:352
    public void resetFPSStats() {
        long aTickCount = System.nanoTime() / 1000000L;
        mStartDrawTime = aTickCount;
        mIntervalDrawTime = aTickCount;
        mDrawCount = 1;
        mIntervalDrawCountStart = 1;
    }

    // Board.cpp:361
    public boolean loadGame(String theFileName) {
        if (!SaveGame.lawnLoadGame(this, theFileName)) {
            return false;
        }
        loadBackgroundImages();
        mApp.clearUpdateBacklog(false);
        resetFPSStats();
        updateLayers();
        return true;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:373-446 — grid item queries
    // ---------------------------------------------------------------------------------------

    // Board.cpp:373
    public GridItem getGridItemAt(int theGridItemType, int theGridX, int theGridY) {
        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridX == theGridX && aGridItem.mGridY == theGridY && aGridItem.mGridItemType == theGridItemType) {
                return aGridItem;
            }
        }
        return null;
    }

    // Board.cpp:387
    public GridItem getRake() {
        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_RAKE) {
                return aGridItem;
            }
        }
        return null;
    }

    // Board.cpp:401
    public GridItem getCraterAt(int theGridX, int theGridY) {
        return getGridItemAt(GridItemType.GRIDITEM_CRATER, theGridX, theGridY);
    }

    // Board.cpp:406
    public GridItem getGraveStoneAt(int theGridX, int theGridY) {
        return getGridItemAt(GridItemType.GRIDITEM_GRAVESTONE, theGridX, theGridY);
    }

    // Board.cpp:411
    public GridItem getLadderAt(int theGridX, int theGridY) {
        return getGridItemAt(GridItemType.GRIDITEM_LADDER, theGridX, theGridY);
    }

    // Board.cpp:416
    public GridItem getScaryPotAt(int theGridX, int theGridY) {
        return getGridItemAt(GridItemType.GRIDITEM_SCARY_POT, theGridX, theGridY);
    }

    // Board.cpp:421
    public GridItem getZenToolAt(int theGridX, int theGridY) {
        return getGridItemAt(GridItemType.GRIDITEM_ZEN_TOOL, theGridX, theGridY);
    }

    // Board.cpp:426
    public boolean canAddGraveStoneAt(int theGridX, int theGridY) {
        if (mGridSquareType[theGridX][theGridY] != GridSquareType.GRIDSQUARE_GRASS
                && mGridSquareType[theGridX][theGridY] != GridSquareType.GRIDSQUARE_HIGH_GROUND) {
            return false;
        }

        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridX == theGridX && aGridItem.mGridY == theGridY) {
                if (aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE
                        || aGridItem.mGridItemType == GridItemType.GRIDITEM_CRATER
                        || aGridItem.mGridItemType == GridItemType.GRIDITEM_LADDER) {
                    return false;
                }
            }
        }
        return true;
    }

    // Board.cpp:448
    public static int makeRenderOrder(int theRenderLayer, int theRow, int theLayerOffset) {
        return theRow * RenderLayer.RENDER_LAYER_ROW_OFFSET + theRenderLayer + theLayerOffset;
    }

    // Board.cpp:453
    public GridItem addALadder(int theGridX, int theGridY) {
        GridItem aLadder = mGridItems.dataArrayAlloc();
        aLadder.mGridItemType = GridItemType.GRIDITEM_LADDER;
        aLadder.mRenderOrder = makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, theGridY, 800);
        aLadder.mGridX = theGridX;
        aLadder.mGridY = theGridY;
        return aLadder;
    }

    // Board.cpp:463
    public GridItem addACrater(int theGridX, int theGridY) {
        GridItem aCrater = mGridItems.dataArrayAlloc();
        aCrater.mGridItemType = GridItemType.GRIDITEM_CRATER;
        aCrater.mRenderOrder = makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, theGridY, 1);
        aCrater.mGridX = theGridX;
        aCrater.mGridY = theGridY;
        return aCrater;
    }

    // Board.cpp:473
    public GridItem addAGraveStone(int theGridX, int theGridY) {
        GridItem aGraveStone = mGridItems.dataArrayAlloc();
        aGraveStone.mGridItemType = GridItemType.GRIDITEM_GRAVESTONE;
        aGraveStone.mGridItemCounter = -Common.rand(50);
        aGraveStone.mRenderOrder = makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, theGridY, 3);
        aGraveStone.mGridX = theGridX;
        aGraveStone.mGridY = theGridY;
        return aGraveStone;
    }

    // Board.cpp:484 (delegates to BoardWaves)
    public void addGraveStones(int theGridX, int theCount, MTRand theLevelRNG) {
        BoardWaves.addGraveStones(this, theGridX, theCount, theLevelRNG);
    }

    // Board.cpp:520
    public int getNumWavesPerFlag() {
        return BoardWaves.getNumWavesPerFlag(this);
    }

    // Board.cpp:525
    public boolean isFlagWave(int theWaveNumber) {
        return BoardWaves.isFlagWave(this, theWaveNumber);
    }

    // Board.cpp:547
    public void putZombieInWave(int theZombieType, int theWaveNumber, ZombiePicker theZombiePicker) {
        BoardWaves.putZombieInWave(this, theZombieType, theWaveNumber, theZombiePicker);
    }

    // Board.cpp:560
    public void putInMissingZombies(int theWaveNumber, ZombiePicker theZombiePicker) {
        BoardWaves.putInMissingZombies(this, theWaveNumber, theZombiePicker);
    }

    // Board.cpp:571
    public void pickZombieWaves() {
        BoardWaves.pickZombieWaves(this);
    }

    // Board.cpp:770
    public int getLevelRandSeed() {
        return BoardWaves.getLevelRandSeed(this);
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:784-1220 — background & level setup (wave part in BoardWaves)
    // ---------------------------------------------------------------------------------------

    // Board.cpp:784
    public void loadBackgroundImages() {
        switch (mBackground) {
            case BackgroundType.BACKGROUND_1_DAY:
                mLoadedResourceNames.add("DelayLoad_Background1");
                if ((mApp.isAdventureMode() && mLevel <= 4) || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RESODDED) {
                    mLoadedResourceNames.add("DelayLoad_BackgroundUnsodded");
                }
                break;

            case BackgroundType.BACKGROUND_2_NIGHT:
                mLoadedResourceNames.add("DelayLoad_Background2");
                break;

            case BackgroundType.BACKGROUND_3_POOL:
                mLoadedResourceNames.add("DelayLoad_Background3");
                break;

            case BackgroundType.BACKGROUND_4_FOG:
                mLoadedResourceNames.add("DelayLoad_Background4");
                break;

            case BackgroundType.BACKGROUND_5_ROOF:
                mLoadedResourceNames.add("DelayLoad_Background5");
                break;

            case BackgroundType.BACKGROUND_6_BOSS:
                mLoadedResourceNames.add("DelayLoad_Background6");
                break;

            case BackgroundType.BACKGROUND_GREENHOUSE:
                mLoadedResourceNames.add("DelayLoad_GreenHouseGarden");
                mLoadedResourceNames.add("DelayLoad_GreenHouseOverlay");
                break;

            case BackgroundType.BACKGROUND_ZOMBIQUARIUM:
                mLoadedResourceNames.add("DelayLoad_Zombiquarium");
                mLoadedResourceNames.add("DelayLoad_GreenHouseOverlay");
                break;

            case BackgroundType.BACKGROUND_TREEOFWISDOM:
                // Board.cpp:821-823: no resource group here, only the tree's reanimation definition.
                Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_TREEOFWISDOM, true);
                break;

            default:
                break;
        }

        // Board.cpp:839-840: the groups registered above are loaded right away. Without this the level's
        // backdrop, graves and fog groups were registered but never loaded, so the board drew a null
        // backdrop (level 1-4 draw the unsodded background plus the sod row, which crashed on the null
        // image) and every other level lost its backdrop art.
        for (String aResource : mLoadedResourceNames) {
            ((PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(aResource);
        }
    }

    // Board.cpp:843
    public void pickBackground() {
        switch (mApp.mGameMode) {
            case GameMode.GAMEMODE_ADVENTURE:
                if (mLevel <= 1 * GameConstants.LEVELS_PER_AREA) {
                    mBackground = BackgroundType.BACKGROUND_1_DAY;
                } else if (mLevel <= 2 * GameConstants.LEVELS_PER_AREA) {
                    mBackground = BackgroundType.BACKGROUND_2_NIGHT;
                } else if (mLevel <= 3 * GameConstants.LEVELS_PER_AREA) {
                    mBackground = BackgroundType.BACKGROUND_3_POOL;
                } else if (mApp.isScaryPotterLevel()) {
                    mBackground = BackgroundType.BACKGROUND_2_NIGHT;
                } else if (mLevel <= 4 * GameConstants.LEVELS_PER_AREA) {
                    mBackground = BackgroundType.BACKGROUND_4_FOG;
                } else if (mLevel < GameConstants.FINAL_LEVEL) {
                    mBackground = BackgroundType.BACKGROUND_5_ROOF;
                } else if (mLevel == GameConstants.FINAL_LEVEL) {
                    mBackground = BackgroundType.BACKGROUND_6_BOSS;
                } else {
                    mBackground = BackgroundType.BACKGROUND_1_DAY;
                }
                break;

            case GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1:
            case GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_1:
            case GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1:
            case GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS:
            case GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING:
            case GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE:
            case GameMode.GAMEMODE_CHALLENGE_SEEING_STARS:
            case GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING_2:
            case GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT:
            case GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY:
            case GameMode.GAMEMODE_CHALLENGE_RESODDED:
            case GameMode.GAMEMODE_CHALLENGE_BIG_TIME:
            case GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_SUNFLOWER:
            case GameMode.GAMEMODE_CHALLENGE_ICE:
            case GameMode.GAMEMODE_CHALLENGE_SHOVEL:
            case GameMode.GAMEMODE_CHALLENGE_SQUIRREL:
                mBackground = BackgroundType.BACKGROUND_1_DAY;
                break;

            case GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_2:
            case GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_2:
            case GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_2:
            case GameMode.GAMEMODE_CHALLENGE_BEGHOULED:
            case GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST:
            case GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT:
            case GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE:
            case GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER:
            case GameMode.GAMEMODE_SCARY_POTTER_1:
            case GameMode.GAMEMODE_SCARY_POTTER_2:
            case GameMode.GAMEMODE_SCARY_POTTER_3:
            case GameMode.GAMEMODE_SCARY_POTTER_4:
            case GameMode.GAMEMODE_SCARY_POTTER_5:
            case GameMode.GAMEMODE_SCARY_POTTER_6:
            case GameMode.GAMEMODE_SCARY_POTTER_7:
            case GameMode.GAMEMODE_SCARY_POTTER_8:
            case GameMode.GAMEMODE_SCARY_POTTER_9:
            case GameMode.GAMEMODE_SCARY_POTTER_ENDLESS:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9:
            case GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS:
                mBackground = BackgroundType.BACKGROUND_2_NIGHT;
                break;

            case GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_3:
            case GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_3:
            case GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3:
            case GameMode.GAMEMODE_CHALLENGE_LITTLE_TROUBLE:
            case GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA:
            case GameMode.GAMEMODE_CHALLENGE_SPEED:
            case GameMode.GAMEMODE_CHALLENGE_LAST_STAND:
            case GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2:
            case GameMode.GAMEMODE_UPSELL:
            case GameMode.GAMEMODE_INTRO:
                mBackground = BackgroundType.BACKGROUND_3_POOL;
                break;

            case GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_4:
            case GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_4:
            case GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_4:
            case GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS:
            case GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL:
            case GameMode.GAMEMODE_CHALLENGE_AIR_RAID:
            case GameMode.GAMEMODE_CHALLENGE_STORMY_NIGHT:
                mBackground = BackgroundType.BACKGROUND_4_FOG;
                break;

            case GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_5:
            case GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_5:
            case GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_5:
            case GameMode.GAMEMODE_CHALLENGE_COLUMN:
            case GameMode.GAMEMODE_CHALLENGE_POGO_PARTY:
            case GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY:
            case GameMode.GAMEMODE_CHALLENGE_BUNGEE_BLITZ:
                mBackground = BackgroundType.BACKGROUND_5_ROOF;
                break;

            case GameMode.GAMEMODE_CHALLENGE_FINAL_BOSS:
                mBackground = BackgroundType.BACKGROUND_6_BOSS;
                break;

            case GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM:
                mBackground = BackgroundType.BACKGROUND_ZOMBIQUARIUM;
                break;

            case GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN:
                mBackground = BackgroundType.BACKGROUND_GREENHOUSE;
                break;

            case GameMode.GAMEMODE_TREE_OF_WISDOM:
                mBackground = BackgroundType.BACKGROUND_TREEOFWISDOM;
                break;

            default:
                // src/Lawn/Board.cpp:834-836 — upstream `default: PVZP_ASSERT(false); break;`, a
                // no-op in release builds; every GameMode is enumerated above.
                break;
        }
        loadBackgroundImages();

        if (mBackground == BackgroundType.BACKGROUND_1_DAY || mBackground == BackgroundType.BACKGROUND_GREENHOUSE || mBackground == BackgroundType.BACKGROUND_TREEOFWISDOM) {
            mPlantRow[0] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[1] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[2] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[3] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[4] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[5] = PlantRowType.PLANTROW_DIRT;

            if (mApp.isAdventureMode() && mApp.isFirstTimeAdventureMode()) {
                if (mLevel == 1) {
                    mPlantRow[0] = PlantRowType.PLANTROW_DIRT;
                    mPlantRow[1] = PlantRowType.PLANTROW_DIRT;
                    mPlantRow[3] = PlantRowType.PLANTROW_DIRT;
                    mPlantRow[4] = PlantRowType.PLANTROW_DIRT;
                } else if (mLevel == 2 || mLevel == 3) {
                    mPlantRow[0] = PlantRowType.PLANTROW_DIRT;
                    mPlantRow[4] = PlantRowType.PLANTROW_DIRT;
                }
            } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RESODDED) {
                mPlantRow[0] = PlantRowType.PLANTROW_DIRT;
                mPlantRow[4] = PlantRowType.PLANTROW_DIRT;
            }
        } else if (mBackground == BackgroundType.BACKGROUND_2_NIGHT) {
            mPlantRow[0] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[1] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[2] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[3] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[4] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[5] = PlantRowType.PLANTROW_DIRT;
        } else if (mBackground == BackgroundType.BACKGROUND_3_POOL || mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM || mBackground == BackgroundType.BACKGROUND_4_FOG) {
            mPlantRow[0] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[1] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[2] = PlantRowType.PLANTROW_POOL;
            mPlantRow[3] = PlantRowType.PLANTROW_POOL;
            mPlantRow[4] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[5] = PlantRowType.PLANTROW_NORMAL;
        } else if (mBackground == BackgroundType.BACKGROUND_5_ROOF || mBackground == BackgroundType.BACKGROUND_6_BOSS) {
            mPlantRow[0] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[1] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[2] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[3] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[4] = PlantRowType.PLANTROW_NORMAL;
            mPlantRow[5] = PlantRowType.PLANTROW_DIRT;
        } else {
            // src/Lawn/Board.cpp:1044-1047 — upstream `else { PVZP_ASSERT(false); }` (release no-op);
            // all BackgroundType values are handled above.
        }

        for (int x = 0; x < MAX_GRID_SIZE_X; x++) {
            for (int y = 0; y < MAX_GRID_SIZE_Y; y++) {
                if (mPlantRow[y] == PlantRowType.PLANTROW_DIRT) {
                    mGridSquareType[x][y] = GridSquareType.GRIDSQUARE_DIRT;
                } else if (mPlantRow[y] == PlantRowType.PLANTROW_POOL && x >= 0 && x <= 8) {
                    mGridSquareType[x][y] = GridSquareType.GRIDSQUARE_POOL;
                } else if (mPlantRow[y] == PlantRowType.PLANTROW_HIGH_GROUND && x >= 4 && x <= 8) {
                    mGridSquareType[x][y] = GridSquareType.GRIDSQUARE_HIGH_GROUND;
                }
            }
        }

        MTRand aLevelRNG = new MTRand(getLevelRandSeed());
        if (stageHasGraveStones()) {
            if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER) {
                addGraveStones(6, PvzpCommon.randRangeInt(1, 2), aLevelRNG);
                addGraveStones(7, PvzpCommon.randRangeInt(1, 3), aLevelRNG);
                addGraveStones(8, PvzpCommon.randRangeInt(2, 3), aLevelRNG);
            } else if (mApp.isWhackAZombieLevel()) {
                mChallenge.whackAZombiePlaceGraves(9);
            } else if (mBackground == BackgroundType.BACKGROUND_2_NIGHT) {
                if (mApp.isSurvivalNormal(mApp.mGameMode)) {
                    addGraveStones(5, 1, aLevelRNG);
                    addGraveStones(6, 1, aLevelRNG);
                    addGraveStones(7, 1, aLevelRNG);
                    addGraveStones(8, 2, aLevelRNG);
                } else if (!mApp.isAdventureMode()) {
                    addGraveStones(4, 1, aLevelRNG);
                    addGraveStones(5, 1, aLevelRNG);
                    addGraveStones(6, 2, aLevelRNG);
                    addGraveStones(7, 2, aLevelRNG);
                    addGraveStones(8, 3, aLevelRNG);
                } else if (mLevel == 11 || mLevel == 12 || mLevel == 13) {
                    addGraveStones(6, 1, aLevelRNG);
                    addGraveStones(7, 1, aLevelRNG);
                    addGraveStones(8, 2, aLevelRNG);
                } else if (mLevel == 14 || mLevel == 16) {
                    addGraveStones(5, 1, aLevelRNG);
                    addGraveStones(6, 1, aLevelRNG);
                    addGraveStones(7, 2, aLevelRNG);
                    addGraveStones(8, 3, aLevelRNG);
                } else if (mLevel == 17 || mLevel == 18 || mLevel == 19) {
                    addGraveStones(4, 1, aLevelRNG);
                    addGraveStones(5, 2, aLevelRNG);
                    addGraveStones(6, 2, aLevelRNG);
                    addGraveStones(7, 3, aLevelRNG);
                    addGraveStones(8, 3, aLevelRNG);
                } else if (mLevel >= 20) {
                    addGraveStones(3, 1, aLevelRNG);
                    addGraveStones(4, 2, aLevelRNG);
                    addGraveStones(5, 2, aLevelRNG);
                    addGraveStones(6, 2, aLevelRNG);
                    addGraveStones(7, 3, aLevelRNG);
                    addGraveStones(8, 3, aLevelRNG);
                } else {
                    // src/Lawn/Board.cpp:1128-1131 — upstream `else { PVZP_ASSERT(false); }` (release
                    // no-op); every adventure night level (11-13, 14/16, 17-19, >=20) is handled.
                }
            }
        }
        pickSpecialGraveStone();
    }

    // Board.cpp:1137
    public void initZombieWavesForLevel(int theForLevel) {
        BoardWaves.initZombieWavesForLevel(this, theForLevel);
    }

    // Board.cpp:1151
    public boolean isZombieWaveDistributionOk() {
        return BoardWaves.isZombieWaveDistributionOk(this);
    }

    // Board.cpp:1183
    public void initZombieWaves() {
        BoardWaves.initZombieWaves(this);
    }

    // Board.cpp:1249
    public void initSurvivalStage() {
        BoardWaves.initSurvivalStage(this);
    }

    // Board.cpp:1278 (utilities/GameButton coordinates)
    public Rect getShovelButtonRect() {
        Rect aRect = new Rect();
        Rect aShovelButtonRect = mChallenge.getShovelButtonRect();
        aRect.mX = aShovelButtonRect.mX;
        aRect.mY = aShovelButtonRect.mY;
        aRect.mWidth = Resources.getImageById(ImageID.IMAGE_SHOVELBANK).getWidth();
        aRect.mHeight = Resources.getImageById(ImageID.IMAGE_SHOVELBANK).getHeight();
        return aRect;
    }

    // Board.cpp:1288
    public void getZenButtonRect(int theObjectType, Rect theRect) {
        // theRect must be pre-initialized with the shovel button rect; this function only adjusts its X
        theRect.mX = 30;
        if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            return;
        }

        boolean aUsable = true;
        for (int anObject = GameObjectType.OBJECT_TYPE_WATERING_CAN; anObject <= GameObjectType.OBJECT_TYPE_WHEELBARROW; anObject++) {
            if (!canUseGameObject(anObject)) {
                aUsable = false;
                break;
            }
        }
        if (aUsable) {
            theRect.mX = 0;
        }

        int aShovelWidth = Resources.getImageById(ImageID.IMAGE_SHOVELBANK).getWidth();
        for (int anObject = GameObjectType.OBJECT_TYPE_WATERING_CAN; anObject < theObjectType; anObject++) {
            if (canUseGameObject(anObject)) {
                theRect.mX += aShovelWidth;
            }
        }
    }

    // Board.cpp:1323
    public void initLevel() {
        mMainCounter = 0;
        mBoardUpdateCounter = 0;
        mEnableGraveStones = false;
        mSodPosition = 0;
        mPrevBoardResult = mApp.mBoardResult;

        int aGameMode = mApp.mGameMode;
        if (aGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM && aGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            mApp.mMusic.stopAllMusic();
        }
        mLevel = mApp.isAdventureMode() ? mApp.mPlayerInfo.mLevel : 0;
        pickBackground();
        initZombieWaves();
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED || aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || mApp.isScaryPotterLevel() || mApp.isWhackAZombieLevel()) {
            mSunMoney = 0;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            mSunMoney = 5000;
        } else if (mApp.isIZombieLevel()) {
            mSunMoney = 150;
        } else if (mApp.isFirstTimeAdventureMode() && mLevel == 1) {
            mSunMoney = 150;
        } else {
            mSunMoney = 50;
        }

        for (int aRow = 0; aRow < MAX_GRID_SIZE_Y; aRow++) {
            mWaveRowGotLawnMowered[aRow] = -100;
            mIceMinX[aRow] = GameConstants.BOARD_ICE_START;
            mIceTimer[aRow] = 0;
            mIceParticleID[aRow] = ParticleSystemID.PARTICLESYSTEMID_NULL;
            mRowPickingArray[aRow].mItem = aRow;
        }
        mNumSunsFallen = 0;
        if (!stageIsNight()) {
            mSunCountDown = PvzpCommon.randRangeInt(425, 700);
        }
        Arrays.fill(mHelpDisplayed, false);
        mSeedBank.mNumPackets = getNumSeedsInBank();
        mSeedBank.updateWidth();
        for (int i = 0; i < GameConstants.SEEDBANK_MAX; i++) {
            SeedPacket aPacket = mSeedBank.mSeedPackets[i];
            aPacket.mIndex = i;
            aPacket.mX = getSeedPacketPositionX(i);
            aPacket.mY = 8;
            aPacket.mPacketType = SeedType.SEED_NONE;
        }
        if (mApp.isSlotMachineLevel()) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_SNOWPEA, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ICE) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_CHERRYBOMB, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_REPEATER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[4].setPacketType(SeedType.SEED_SNOWPEA, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[5].setPacketType(SeedType.SEED_CHOMPER, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS
                || mApp.isScaryPotterLevel() || mApp.isWhackAZombieLevel()
                || aGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            // Puzzle / mini-game seed banks (I,Zombie, Scary Potter, Whack-a-Zombie, Zombiquarium).
            lizardSeedBanks(aGameMode);
        } else if (!chooseSeedsOnCurrentLevel() && !hasConveyorBeltSeedBank()) {
            mSeedBank.mNumPackets = getNumSeedsInBank();
            for (int i = 0; i < mSeedBank.mNumPackets; i++) {
                mSeedBank.mSeedPackets[i].setPacketType(i, SeedType.SEED_NONE);
            }
        }
        markAllDirty();

        mPaused = false;
        mOutOfMoneyCounter = 0;
        if (stageHasFog()) {
            mFogBlownCountDown = 200;
            mFogOffset = 1065 - leftFogColumn() * 80;
        }
        mChallenge.initLevel();
        LawnHooksRegistry.dispatcher().onLevelStart(this, mLevel, mApp.mGameMode, mBackground);
    }

    /**
     * Board.cpp:1420-1526 — the mini-game / puzzle seed-bank packet tables (I,Zombie stages,
     * Scary Potter, Whack-a-Zombie, Zombiquarium). Kept in upstream order so the packet layout is
     * identical; those modes themselves are guarded where they are entered.
     */
    private void lizardSeedBanks(int aGameMode) {
        if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_FOOTBALL, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_SCREEN_DOOR, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_DIGGER, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_LADDER, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_BUNGEE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_ZOMBIE_BALLOON, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_POLEVAULTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_ZOMBIE_GARGANTUAR, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_POLEVAULTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_ZOMBIE_DANCER, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_IMP, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_TRAFFIC_CONE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_ZOMBIE_BUNGEE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[4].setPacketType(SeedType.SEED_ZOMBIE_DIGGER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[5].setPacketType(SeedType.SEED_ZOMBIE_LADDER, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_IMP, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_TRAFFIC_CONE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_POLEVAULTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[4].setPacketType(SeedType.SEED_ZOMBIE_BUNGEE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[5].setPacketType(SeedType.SEED_ZOMBIE_DIGGER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[6].setPacketType(SeedType.SEED_ZOMBIE_LADDER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[7].setPacketType(SeedType.SEED_ZOMBIE_FOOTBALL, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_IMP, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIE_TRAFFIC_CONE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_ZOMBIE_POLEVAULTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[3].setPacketType(SeedType.SEED_ZOMBIE_PAIL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[4].setPacketType(SeedType.SEED_ZOMBIE_BUNGEE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[5].setPacketType(SeedType.SEED_ZOMBIE_DIGGER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[6].setPacketType(SeedType.SEED_ZOMBIE_LADDER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[7].setPacketType(SeedType.SEED_ZOMBIE_FOOTBALL, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[8].setPacketType(SeedType.SEED_ZOMBIE_DANCER, SeedType.SEED_NONE);
        } else if (mApp.isScaryPotterLevel()) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_CHERRYBOMB, SeedType.SEED_NONE);
        } else if (mApp.isWhackAZombieLevel()) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_POTATOMINE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_GRAVEBUSTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(mApp.isAdventureMode() ? SeedType.SEED_CHERRYBOMB : SeedType.SEED_ICESHROOM, SeedType.SEED_NONE);
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIQUARIUM_SNORKLE, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_ZOMBIQUARIUM_TROPHY, SeedType.SEED_NONE);
        } else if (mApp.isSlotMachineLevel()) {
            mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
            mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_SNOWPEA, SeedType.SEED_NONE);
        } else {
            // Unreachable: initLevel only dispatches here for the puzzle / mini-game modes listed
            // above (Board.cpp:1420-1526); upstream has no default arm in this chain.
        }
    }

    // Board.cpp:1527
    public Reanimation createRakeReanim(float theRakeX, float theRakeY, int theRenderOrder) {
        Reanimation aReanim = mApp.addReanimation(theRakeX + 20, theRakeY, theRenderOrder, ReanimationType.REANIM_RAKE);
        aReanim.mAnimRate = 0;
        aReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        aReanim.mIsAttachment = true;
        return aReanim;
    }

    // Board.cpp:1536
    public void placeRake() {
        if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_RAKE] == 0) {
            return;
        }

        int aGridX = 7;
        if (mApp.isScaryPotterLevel()) {
            for (GridItem aGridItem : mGridItems) {
                if (aGridItem.mDead) {
                    continue;
                }
                if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SCARY_POT && aGridItem.mGridX <= aGridX && aGridItem.mGridX > 0) {
                    aGridX = aGridItem.mGridX - 1;
                }
            }
        } else {
            if (!stageHasZombieWalkInFromRight() || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                    || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA) {
                return;
            }
        }

        int aPickCount = 0;
        PvzpWeightedArray[] aPickArray = new PvzpWeightedArray[MAX_GRID_SIZE_Y];
        for (int aRow = 0; aRow < MAX_GRID_SIZE_Y; aRow++) {
            if (aRow != 5 && mPlantRow[aRow] == PlantRowType.PLANTROW_NORMAL) {
                aPickArray[aPickCount] = new PvzpWeightedArray();
                aPickArray[aPickCount].mWeight = 1;
                aPickArray[aPickCount].mItem = aRow;
                aPickCount++;
            }
        }
        if (aPickCount == 0) {
            return;
        }

        int aGridY = (int) PvzpCommon.pvzpPickFromWeightedArray(aPickArray, aPickCount);
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_RAKE]--;
        GridItem aRake = mGridItems.dataArrayAlloc();
        aRake.mGridItemType = GridItemType.GRIDITEM_RAKE;
        aRake.mGridX = aGridX;
        aRake.mGridY = aGridY;
        aRake.mPosX = gridToPixelX(aGridX, aGridY);
        aRake.mPosY = gridToPixelY(aGridX, aGridY);
        aRake.mRenderOrder = makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, aGridY, 9);
        aRake.mGridItemReanimID = mApp.reanimationGetID(createRakeReanim(aRake.mPosX, aRake.mPosY, 0));
        aRake.mGridItemState = GridItemState.GRIDITEM_STATE_RAKE_ATTRACTING;
    }

    // Board.cpp:1588
    public void initLawnMowers() {
        int aGameMode = mApp.mGameMode;
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED || aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || aGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN || aGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || aGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND || aGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM
                || mApp.isSquirrelLevel() || mApp.isIZombieLevel()
                || (stageHasRoof() && mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_ROOF_CLEANER] == 0)) {
            return;
        }

        for (int aRow = 0; aRow < MAX_GRID_SIZE_Y; aRow++) {
            if ((aGameMode == GameMode.GAMEMODE_CHALLENGE_RESODDED && aRow <= 4)
                    || (mApp.isAdventureMode() && mLevel == 35)
                    || (!mApp.isScaryPotterLevel() && mPlantRow[aRow] != PlantRowType.PLANTROW_DIRT)) {
                LawnMower aLawnMower = mLawnMowers.dataArrayAlloc();
                aLawnMower.lawnMowerInitialize(aRow);
                aLawnMower.mVisible = false;
            }
        }
    }

    // Board.cpp:1611
    public boolean chooseSeedsOnCurrentLevel() {
        if (mApp.isChallengeWithoutSeedBank() || hasConveyorBeltSeedBank()) {
            return false;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            return false;
        }

        if (mApp.isIZombieLevel() || mApp.isSlotMachineLevel()) {
            return false;
        }

        return !mApp.isFirstTimeAdventureMode() || mLevel > 7;
    }

    // Board.cpp:1628
    public void startLevel() {
        mCoinBankFadeCount = 0;
        mApp.mLastLevelStats.reset();
        mChallenge.startLevel();

        // the unsigned underflow below is intentional (Board.cpp:1637)
        int aSurvivalStage = mApp.mGameMode - GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1;
        if (aSurvivalStage >= 0 && aSurvivalStage <= 4) {
            if (getSurvivalFlagsCompleted() >= 20) {
                mApp.giveAchievement(AchievementId.Immortal, true);
            }
        }

        if (mApp.isSurvivalMode() && mChallenge.mSurvivalStage > 0) {
            freezeEffectsForCutscene(false);
            mApp.mSoundSystem.gamePause(false);
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || mApp.mGameMode == GameMode.GAMEMODE_UPSELL
                || mApp.mGameMode == GameMode.GAMEMODE_INTRO
                || mApp.isFinalBossLevel()) {
            return;
        }

        mApp.mMusic.startGameMusic();
    }

    // Board.cpp:1659
    public LawnMower getBottomLawnMower() {
        LawnMower aBottomMower = null;
        for (LawnMower aLawnMower : mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            if (aLawnMower.mMowerState == LawnMowerState.MOWER_TRIGGERED || aLawnMower.mMowerState == LawnMowerState.MOWER_SQUISHED) {
                continue;
            }

            if (aBottomMower == null || aBottomMower.mRow < aLawnMower.mRow) {
                aBottomMower = aLawnMower;
            }
        }
        return aBottomMower;
    }

    // Board.cpp:1677
    public void updateLevelEndSequence() {
        if (mNextSurvivalStageCounter > 0) {
            if (!isScaryPotterDaveTalking()) {
                mNextSurvivalStageCounter--;
                if (mApp.isAdventureMode() && mApp.isScaryPotterLevel() && mNextSurvivalStageCounter == 300) {
                    mApp.crazyDaveEnter();
                    mApp.crazyDaveTalkIndex(mChallenge.mSurvivalStage == 0 ? 2700 : 2800);
                    mChallenge.puzzleNextStageClear();
                    mNextSurvivalStageCounter = 100;
                }
            }

            if (mNextSurvivalStageCounter == 1 && mApp.isSurvivalMode()) {
                tryToSaveGame();
            }

            if (mNextSurvivalStageCounter == 0) {
                if (mApp.isScaryPotterLevel()) {
                    if (mApp.isAdventureMode()) {
                        return;
                    }

                    if (!isFinalScaryPotterStage()) {
                        mChallenge.puzzleNextStageClear();
                        mChallenge.scaryPotterPopulate();
                    }
                } else if (LawnApp.isEndlessIZombie(mApp.mGameMode)) {
                    mChallenge.puzzleNextStageClear();
                    mChallenge.iZombieInitLevel();
                } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
                    clearAdvice(AdviceType.ADVICE_NONE);
                } else {
                    mLevelComplete = true;
                    removeZombiesForRepick();
                }
                return;
            }
        }

        if (mBoardFadeOutCounter < 0) {
            return;
        }

        mBoardFadeOutCounter--;
        if (mBoardFadeOutCounter == 0) {
            mLevelComplete = true;
            LawnHooksRegistry.dispatcher().onLevelEnd(this, mApp.mBoardResult);
            return;
        }
        if (mBoardFadeOutCounter == 300) {
            if (!isSurvivalStageWithRepick() && !(mLevel == 9 || mLevel == 19 || mLevel == 29 || mLevel == 39 || mLevel == 49)) {
                mApp.playSample(Resources.SOUND_LIGHTFILL_ID);
            }
        }

        if (mScoreNextMowerCounter > 0) {
            mScoreNextMowerCounter--;
            if (mScoreNextMowerCounter != 0) {
                return;
            }
        }

        if (canDropLoot() && !isSurvivalStageWithRepick()) {
            mScoreNextMowerCounter = 40;
            LawnMower aLawnMower = getBottomLawnMower();
            if (aLawnMower != null) {
                addCoin((int) aLawnMower.mPosX + 40, (int) aLawnMower.mPosY + 40, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_LAWNMOWER_COIN);
                SoundInstance aSoundInstance = mApp.getSoundManager().getSoundInstance(Resources.getSoundById(Resources.SOUND_POINTS_ID));
                if (aSoundInstance != null) {
                    aSoundInstance.play(false, true);
                    float aPitch = clampInt(6 - countUntriggerLawnMowers(), 0, 6);
                    aSoundInstance.adjustPitch(aPitch);
                }
                aLawnMower.die();
            }
        }
    }

    // Board.cpp:1774
    public void completeEndLevelSequenceForSaving() {
        if (canDropLoot()) {
            for (LawnMower aLawnMower : mLawnMowers) {
                if (aLawnMower.mDead) {
                    continue;
                }
                if (aLawnMower.mMowerState != LawnMowerState.MOWER_TRIGGERED && aLawnMower.mMowerState != LawnMowerState.MOWER_SQUISHED) {
                    int aCoinValue = Coin.getCoinValue(CoinType.COIN_GOLD);
                    mApp.mPlayerInfo.addCoins(aCoinValue);
                    mCoinsCollected += aCoinValue;
                }
            }
        }

        for (Coin aCoin : mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (aCoin.mIsBeingCollected) {
                aCoin.scoreCoin();
            } else {
                aCoin.die();
            }
        }

        mApp.updatePlayerProfileForFinishingLevel();
    }

    // Board.cpp:1808
    public void fadeOutLevel() {
        if (mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            refreshSeedPacketFromCursor();
            mApp.mLastLevelStats.reset();
            mLevelComplete = true;
        }

        boolean aNeedSoundEffect = true;
        if (mApp.isScaryPotterLevel() && !isFinalScaryPotterStage()) {
            aNeedSoundEffect = false;
        } else if (isSurvivalStageWithRepick() || isLastStandStageWithRepick() || mApp.isEndlessIZombie(mApp.mGameMode)) {
            aNeedSoundEffect = false;
        }
        if (aNeedSoundEffect) {
            mApp.mMusic.stopAllMusic();
            if (mApp.isAdventureMode() && mLevel == 50) {
                mApp.playFoley(FoleyType.FOLEY_FINAL_FANFARE);
            } else if (mApp.trophiesNeedForGoldSunflower() == 1) {
                mApp.playFoley(FoleyType.FOLEY_FINAL_FANFARE);
            } else {
                mApp.playFoley(FoleyType.FOLEY_WINMUSIC);
            }
        }

        if (mApp.isScaryPotterLevel() && !isFinalScaryPotterStage()) {
            mNextSurvivalStageCounter = 500;
            if (mApp.isAdventureMode()) {
                clearAdvice(AdviceType.ADVICE_NONE);
            } else {
                mLevelAwardSpawned = true;
                String aStreakStr = mApp.isEndlessScaryPotter(mApp.mGameMode) ? "[ADVICE_MORE_SCARY_POTS]" : "[ADVICE_3_IN_A_ROW]";
                String aMessage = PvzpCommon.pvzpReplaceNumberString(aStreakStr, "{STREAK}", mChallenge.mSurvivalStage + 1);
                puzzleSaveStreak();
                clearAdvice(AdviceType.ADVICE_NONE);
                displayAdvice(aMessage, MessageStyle.MESSAGE_STYLE_BIG_MIDDLE, AdviceType.ADVICE_NONE);
            }
            return;
        }

        if (mApp.isEndlessIZombie(mApp.mGameMode)) {
            mNextSurvivalStageCounter = 500;
            String aMessage = PvzpCommon.pvzpReplaceNumberString("[ADVICE_MORE_IZOMBIE]", "{STREAK}", mChallenge.mSurvivalStage + 1);
            puzzleSaveStreak();
            clearAdvice(AdviceType.ADVICE_NONE);
            displayAdvice(aMessage, MessageStyle.MESSAGE_STYLE_BIG_MIDDLE, AdviceType.ADVICE_NONE);
            return;
        }

        if (isLastStandStageWithRepick()) {
            mNextSurvivalStageCounter = 500;
            mChallenge.lastStandCompletedStage();
            return;
        }

        if (!isSurvivalStageWithRepick()) {
            refreshSeedPacketFromCursor();
            mApp.mLastLevelStats.mUnusedLawnMowers = countUntriggerLawnMowers();

            mBoardFadeOutCounter = 600;
            if (mLevel == 9 || mLevel == 19 || mLevel == 29 || mLevel == 39 || mLevel == 49) {
                mBoardFadeOutCounter = 500;
            }

            if (canDropLoot()) {
                mScoreNextMowerCounter = 200;
            }

            for (Coin aCoin : mCoins) {
                if (aCoin.mDead) {
                    continue;
                }
                aCoin.tryAutoCollectAfterLevelAward();
            }
        } else {
            mNextSurvivalStageCounter = 500;
            displayAdvice("[ADVICE_MORE_ZOMBIES]", MessageStyle.MESSAGE_STYLE_BIG_MIDDLE, AdviceType.ADVICE_NONE);
            mApp.mMusic.fadeOut(500);
            mApp.playSample(Resources.SOUND_HUGE_WAVE_ID);
            for (int aRow = 0; aRow < MAX_GRID_SIZE_Y; aRow++) {
                mIceTimer[aRow] = mNextSurvivalStageCounter;
            }
        }

        mApp.setCursor(SexyAppBase.CURSOR_POINTER);
    }

    // Board.cpp:1918
    public void displayAdvice(String theAdvice, int theMessageStyle, int theHelpIndex) {
        if (theHelpIndex != AdviceType.ADVICE_NONE) {
            if (mHelpDisplayed[theHelpIndex]) {
                return;
            }

            mHelpDisplayed[theHelpIndex] = true;
        }

        mAdvice.setLabel(theAdvice, theMessageStyle);
        mHelpIndex = theHelpIndex;
    }

    // Board.cpp:1932
    public void displayAdviceAgain(String theAdvice, int theMessageStyle, int theHelpIndex) {
        if (theHelpIndex != AdviceType.ADVICE_NONE) {
            mHelpDisplayed[theHelpIndex] = false;
        }
        displayAdvice(theAdvice, theMessageStyle, theHelpIndex);
    }

    // Board.cpp:1941
    public void clearAdviceImmediately() {
        clearAdvice(AdviceType.ADVICE_NONE);
        mAdvice.mDuration = 0;
    }

    // Board.cpp:1947
    public void clearAdvice(int theHelpIndex) {
        if (theHelpIndex == AdviceType.ADVICE_NONE || theHelpIndex == mHelpIndex) {
            mAdvice.clearLabel();
            mHelpIndex = AdviceType.ADVICE_NONE;
        }
    }

    // Board.cpp:1956
    public Coin addCoin(int theX, int theY, int theCoinType, int theCoinMotion) {
        Coin aCoin = mCoins.dataArrayAlloc();
        aCoin.coinInitialize(theX, theY, theCoinType, theCoinMotion);
        if (mApp.isFirstTimeAdventureMode() && mLevel == 1) {
            displayAdvice("[ADVICE_CLICK_ON_SUN]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY, AdviceType.ADVICE_CLICK_ON_SUN);
        }
        return aCoin;
    }

    // Board.cpp:1967
    public boolean isPlantInCursor() {
        return mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_BANK
                || mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_USABLE_COIN
                || mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE
                || mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_DUPLICATOR
                || mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW;
    }

    // Board.cpp:1977
    public int getSeedTypeInCursor() {
        if (mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WHEEELBARROW) {
            PottedPlant aPottedPlant = mApp.mZenGarden.getPottedPlantInWheelbarrow();
            if (aPottedPlant != null) {
                return aPottedPlant.mSeedType;
            }
        }

        if (!isPlantInCursor()) {
            return SeedType.SEED_NONE;
        }
        return mCursorObject.mType == SeedType.SEED_IMITATER ? mCursorObject.mImitaterType : mCursorObject.mType;
    }

    // Board.cpp:1995
    public void refreshSeedPacketFromCursor() {
        if (mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_USABLE_COIN) {
            mCoins.dataArrayTryToGet(mCursorObject.mCoinID).droppedUsableSeed();
        } else if (mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_BANK) {
            mSeedBank.mSeedPackets[mCursorObject.mSeedBankIndex].activate();
        }
        clearCursor();
    }

    // Board.cpp:2009
    public boolean isPoolSquare(int theGridX, int theGridY) {
        if (theGridX >= 0 && theGridY >= 0) {
            return mGridSquareType[theGridX][theGridY] == GridSquareType.GRIDSQUARE_POOL;
        }
        return false;
    }

    // Board.cpp:2019
    public Plant newPlant(int theGridX, int theGridY, int theSeedType, int theImitaterType) {
        Plant aPlant = mPlants.dataArrayAlloc();
        aPlant.mIsOnBoard = true;
        aPlant.plantInitialize(theGridX, theGridY, theSeedType, theImitaterType);
        return aPlant;
    }

    // Board.cpp:2027
    public void doPlantingEffects(int theGridX, int theGridY, Plant thePlant) {
        int aXPos = gridToPixelX(theGridX, theGridY) + 41;
        int aYPos = gridToPixelY(theGridX, theGridY) + 74;
        if (thePlant != null) {
            if (thePlant.mSeedType == SeedType.SEED_LILYPAD) {
                aYPos += 15;
            } else if (thePlant.mSeedType == SeedType.SEED_FLOWERPOT) {
                aYPos += 30;
            }
        }

        if (mBackground == BackgroundType.BACKGROUND_GREENHOUSE) {
            mApp.playFoley(FoleyType.FOLEY_CERAMIC);
            return;
        }
        if (mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
            mApp.playFoley(FoleyType.FOLEY_PLANT_WATER);
            return;
        }
        if (Plant.isFlying(thePlant.mSeedType)) {
            mApp.playFoley(FoleyType.FOLEY_PLANT);
            return;
        }

        if (isPoolSquare(theGridX, theGridY)) {
            mApp.playFoley(FoleyType.FOLEY_PLANT_WATER);
            mApp.addPvzpParticle(aXPos, aYPos, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_PLANTING_POOL);
        } else {
            mApp.playFoley(FoleyType.FOLEY_PLANT);
            mApp.addPvzpParticle(aXPos, aYPos, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_PLANTING);
        }
    }

    // Board.cpp:2071
    public Plant addPlant(int theGridX, int theGridY, int theSeedType, int theImitaterType) {
        Plant aPlant = newPlant(theGridX, theGridY, theSeedType, theImitaterType);
        doPlantingEffects(theGridX, theGridY, aPlant);
        mChallenge.plantAdded(aPlant);

        int aSunPlantsCount = countPlantByType(SeedType.SEED_SUNSHROOM) + countPlantByType(SeedType.SEED_SUNFLOWER);
        if (aSunPlantsCount > mMaxSunPlants) {
            mMaxSunPlants = aSunPlantsCount;
        }

        if (theSeedType == SeedType.SEED_PEASHOOTER
                || theSeedType == SeedType.SEED_SNOWPEA
                || theSeedType == SeedType.SEED_REPEATER
                || theSeedType == SeedType.SEED_THREEPEATER
                || theSeedType == SeedType.SEED_SPLITPEA
                || theSeedType == SeedType.SEED_GATLINGPEA) {
            mPeaShooterUsed = true;
        }
        if (theSeedType == SeedType.SEED_CABBAGEPULT
                || theSeedType == SeedType.SEED_KERNELPULT
                || theSeedType == SeedType.SEED_MELONPULT
                || theSeedType == SeedType.SEED_WINTERMELON) {
            mCatapultPlantsUsed = true;
        }

        boolean aIsFungi = Plant.isFungus(theSeedType);
        if (!Plant.isFlying(theSeedType) && !aIsFungi) {
            mMushroomAndCoffeeBeansOnly = false;
        }
        if (aIsFungi) {
            mMushroomsUsed = true;
        }

        LawnHooksRegistry.dispatcher().onPlantPlaced(this, aPlant, theGridX, theGridY);
        return aPlant;
    }

    // Board.cpp:2111
    public Plant getPumpkinAt(int theGridX, int theGridY) {
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mPlantCol == theGridX && aPlant.mRow == theGridY && !aPlant.notOnGround() && aPlant.mSeedType == SeedType.SEED_PUMPKINSHELL) {
                return aPlant;
            }
        }
        return null;
    }

    // Board.cpp:2125
    public Plant getFlowerPotAt(int theGridX, int theGridY) {
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mPlantCol == theGridX && aPlant.mRow == theGridY && !aPlant.notOnGround() && aPlant.mSeedType == SeedType.SEED_FLOWERPOT) {
                return aPlant;
            }
        }
        return null;
    }

    // Board.cpp:2139
    public void getPlantsOnLawn(int theGridX, int theGridY, PlantsOnLawn thePlantOnLawn) {
        thePlantOnLawn.mUnderPlant = null;
        thePlantOnLawn.mPumpkinPlant = null;
        thePlantOnLawn.mFlyingPlant = null;
        thePlantOnLawn.mNormalPlant = null;

        if (theGridX < 0 || theGridX >= MAX_GRID_SIZE_X || theGridY < 0 || theGridY >= MAX_GRID_SIZE_Y) {
            return;
        }

        if (mApp.isWallnutBowlingLevel() && !mCutScene.isInShovelTutorial()) {
            return;
        }

        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow != theGridY) {
                continue;
            }
            int aSeedType = aPlant.mSeedType;
            if (aSeedType == SeedType.SEED_IMITATER && aPlant.mImitaterType != SeedType.SEED_NONE) {
                aSeedType = aPlant.mImitaterType;
            }

            if (aSeedType == SeedType.SEED_COBCANNON) {
                if (aPlant.mPlantCol < theGridX - 1 || aPlant.mPlantCol > theGridX) {
                    continue;
                }
            } else {
                if (aPlant.mPlantCol != theGridX) {
                    continue;
                }
            }
            if (aPlant.notOnGround()) {
                continue;
            }

            if (Plant.isFlying(aSeedType)) {
                thePlantOnLawn.mFlyingPlant = aPlant;
            } else if (aSeedType == SeedType.SEED_FLOWERPOT || (aSeedType == SeedType.SEED_LILYPAD && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN)) {
                thePlantOnLawn.mUnderPlant = aPlant;
            } else if (aSeedType == SeedType.SEED_PUMPKINSHELL) {
                thePlantOnLawn.mPumpkinPlant = aPlant;
            } else {
                thePlantOnLawn.mNormalPlant = aPlant;
            }
        }
    }

    // Board.cpp:2208
    public Plant getTopPlantAt(int theGridX, int theGridY, int thePriority) {
        if (theGridX < 0 || theGridX >= MAX_GRID_SIZE_X || theGridY < 0 || theGridY >= MAX_GRID_SIZE_Y) {
            return null;
        }

        if (mApp.isWallnutBowlingLevel() && !mCutScene.isInShovelTutorial()) {
            return null;
        }

        PlantsOnLawn aPlantOnLawn = new PlantsOnLawn();
        getPlantsOnLawn(theGridX, theGridY, aPlantOnLawn);

        switch (thePriority) {
            case PlantPriority.TOPPLANT_EATING_ORDER:
                if (aPlantOnLawn.mPumpkinPlant != null) {
                    return aPlantOnLawn.mPumpkinPlant;
                } else if (aPlantOnLawn.mNormalPlant != null) {
                    return aPlantOnLawn.mNormalPlant;
                } else {
                    return aPlantOnLawn.mUnderPlant;
                }
            case PlantPriority.TOPPLANT_DIGGING_ORDER:
                if (aPlantOnLawn.mNormalPlant != null) {
                    return aPlantOnLawn.mNormalPlant;
                } else {
                    return aPlantOnLawn.mUnderPlant;
                }
            case PlantPriority.TOPPLANT_BUNGEE_ORDER:
            case PlantPriority.TOPPLANT_CATAPULT_ORDER:
            case PlantPriority.TOPPLANT_ANY:
                if (aPlantOnLawn.mFlyingPlant != null) {
                    return aPlantOnLawn.mFlyingPlant;
                } else if (aPlantOnLawn.mNormalPlant != null) {
                    return aPlantOnLawn.mNormalPlant;
                } else if (aPlantOnLawn.mPumpkinPlant != null) {
                    return aPlantOnLawn.mPumpkinPlant;
                } else {
                    return aPlantOnLawn.mUnderPlant;
                }
            case PlantPriority.TOPPLANT_ZEN_TOOL_ORDER:
                if (aPlantOnLawn.mFlyingPlant != null) {
                    return aPlantOnLawn.mFlyingPlant;
                } else if (aPlantOnLawn.mPumpkinPlant != null) {
                    return aPlantOnLawn.mPumpkinPlant;
                } else if (aPlantOnLawn.mNormalPlant != null) {
                    return aPlantOnLawn.mNormalPlant;
                } else {
                    return aPlantOnLawn.mUnderPlant;
                }
            case PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION:
                return aPlantOnLawn.mNormalPlant;
            case PlantPriority.TOPPLANT_ONLY_FLYING:
                return aPlantOnLawn.mFlyingPlant;
            case PlantPriority.TOPPLANT_ONLY_PUMPKIN:
                return aPlantOnLawn.mPumpkinPlant;
            case PlantPriority.TOPPLANT_ONLY_UNDER_PLANT:
                return aPlantOnLawn.mUnderPlant;
            default:
                // src/Lawn/Board.cpp:2244-2246 — upstream `default: PVZP_ASSERT(false);` then
                // `unreachable();`; every PlantPriority is enumerated above.
                throw new IllegalStateException("plant priority " + thePriority + " is not handled");
        }
    }

    // Board.cpp:2249
    public int countSunFlowers() {
        int aCount = 0;
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.makesSun()) {
                aCount++;
            }
        }
        return aCount;
    }

    // Board.cpp:2264
    public int countPlantByType(int theSeedType) {
        int aCount = 0;
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == theSeedType) {
                aCount++;
            }
        }
        return aCount;
    }

    // Board.cpp:2279
    public int countEmptyPotsOrLilies(int theSeedType) {
        int aCount = 0;
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == theSeedType && getTopPlantAt(aPlant.mPlantCol, aPlant.mRow, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION) == null) {
                aCount++;
            }
        }
        return aCount;
    }

    // Board.cpp:2294
    public boolean isValidCobCannonSpotHelper(int theGridX, int theGridY) {
        PlantsOnLawn aPlantOnLawn = new PlantsOnLawn();
        getPlantsOnLawn(theGridX, theGridY, aPlantOnLawn);
        if (aPlantOnLawn.mPumpkinPlant != null) {
            return false;
        }

        if (aPlantOnLawn.mNormalPlant != null && aPlantOnLawn.mNormalPlant.mSeedType == SeedType.SEED_KERNELPULT) {
            return true;
        }

        return mApp.mEasyPlantingCheat && canPlantAt(theGridX, theGridY, SeedType.SEED_KERNELPULT) == PlantingReason.PLANTING_OK;
    }

    // Board.cpp:2307
    public boolean isValidCobCannonSpot(int theGridX, int theGridY) {
        if (!isValidCobCannonSpotHelper(theGridX, theGridY) || !isValidCobCannonSpotHelper(theGridX + 1, theGridY)) {
            return false;
        }

        return (getFlowerPotAt(theGridX, theGridY) != null) == (getFlowerPotAt(theGridX + 1, theGridY) != null);
    }

    // Board.cpp:2315
    public boolean hasValidCobCannonSpot() {
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == SeedType.SEED_KERNELPULT && isValidCobCannonSpot(aPlant.mPlantCol, aPlant.mRow)) {
                return true;
            }
        }
        return false;
    }

    // Board.cpp:2329
    public Projectile addProjectile(int theX, int theY, int theRenderOrder, int theRow, int theProjectileType) {
        Projectile aProjectile = mProjectiles.dataArrayAlloc();
        aProjectile.projectileInitialize(theX, theY, theRenderOrder, theRow, theProjectileType);
        return aProjectile;
    }

    // Board.cpp:2336
    public static boolean canZombieSpawnOnLevel(int theZombieType, int theLevel) {
        return BoardWaves.canZombieSpawnOnLevel(theZombieType, theLevel);
    }

    // Board.cpp:2353
    public int getIntroducedZombieType() {
        return BoardWaves.getIntroducedZombieType(this);
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:2688-2900 — planting rules
    // ---------------------------------------------------------------------------------------

    // Board.cpp:2688
    public int canPlantAt(int theGridX, int theGridY, int theSeedType) {
        if (theGridX < 0 || theGridX >= MAX_GRID_SIZE_X || theGridY < 0 || theGridY >= MAX_GRID_SIZE_Y) {
            return PlantingReason.PLANTING_NOT_HERE;
        }

        int aReason = mChallenge.canPlantAt(theGridX, theGridY, theSeedType);
        int aHookedReason = LawnHooksRegistry.dispatcher().onPlantingValidated(this, theGridX, theGridY, theSeedType, aReason);
        if (aHookedReason != aReason) {
            return aHookedReason;
        }
        if (aReason != PlantingReason.PLANTING_OK || Challenge.isZombieSeedType(theSeedType) != 0) {
            return aReason;
        }

        PlantsOnLawn aPlantOnLawn = new PlantsOnLawn();
        getPlantsOnLawn(theGridX, theGridY, aPlantOnLawn);
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            if (aPlantOnLawn.mUnderPlant != null || aPlantOnLawn.mPumpkinPlant != null
                    || aPlantOnLawn.mFlyingPlant != null || aPlantOnLawn.mNormalPlant != null) {
                return PlantingReason.PLANTING_NOT_HERE;
            }
            if (mApp.mZenGarden.mGardenType == GardenType.GARDEN_AQUARIUM && !Plant.isAquatic(theSeedType)) {
                return PlantingReason.PLANTING_NOT_ON_WATER;
            }

            return PlantingReason.PLANTING_OK;
        }

        boolean aHasGrave = getGraveStoneAt(theGridX, theGridY) != null;
        if (theSeedType == SeedType.SEED_GRAVEBUSTER) {
            if (aPlantOnLawn.mNormalPlant != null) {
                return PlantingReason.PLANTING_NOT_HERE;
            }

            return aHasGrave ? PlantingReason.PLANTING_OK : PlantingReason.PLANTING_ONLY_ON_GRAVES;
        }
        if (theSeedType == SeedType.SEED_INSTANT_COFFEE) {
            if (aPlantOnLawn.mFlyingPlant != null) {
                return PlantingReason.PLANTING_NOT_HERE;
            }

            if (aPlantOnLawn.mNormalPlant == null || !aPlantOnLawn.mNormalPlant.mIsAsleep || aPlantOnLawn.mNormalPlant.mWakeUpCounter > 0
                    || aPlantOnLawn.mNormalPlant.mOnBungeeState == PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
                return PlantingReason.PLANTING_NEEDS_SLEEPING;
            }

            return PlantingReason.PLANTING_OK;
        }
        if (aHasGrave) {
            return Plant.isFlying(theSeedType) ? PlantingReason.PLANTING_OK : PlantingReason.PLANTING_NOT_ON_GRAVE;
        }

        Plant aUnderPlant = aPlantOnLawn.mUnderPlant;
        boolean aHasLilypad;
        boolean aHasFlowerPot;
        if (aUnderPlant == null || aUnderPlant.mOnBungeeState == PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
            aHasLilypad = false;
            aHasFlowerPot = false;
        } else {
            aHasLilypad = aUnderPlant.mSeedType == SeedType.SEED_LILYPAD;
            aHasFlowerPot = aUnderPlant.mSeedType == SeedType.SEED_FLOWERPOT;
        }
        if (getCraterAt(theGridX, theGridY) != null) {
            return PlantingReason.PLANTING_NOT_ON_CRATER;
        }
        if (getScaryPotAt(theGridX, theGridY) != null || isIceAt(theGridX, theGridY)) {
            return PlantingReason.PLANTING_NOT_HERE;
        }
        int aGridSquare = mGridSquareType[theGridX][theGridY];
        if (aGridSquare == GridSquareType.GRIDSQUARE_DIRT || aGridSquare == GridSquareType.GRIDSQUARE_NONE) {
            return PlantingReason.PLANTING_NOT_HERE;
        }
        Plant aNormalPlant = aPlantOnLawn.mNormalPlant;
        if (theSeedType == SeedType.SEED_LILYPAD || theSeedType == SeedType.SEED_TANGLEKELP || theSeedType == SeedType.SEED_SEASHROOM) {
            if (!isPoolSquare(theGridX, theGridY)) {
                return PlantingReason.PLANTING_ONLY_IN_POOL;
            }

            return (aNormalPlant != null || aUnderPlant != null) ? PlantingReason.PLANTING_NOT_HERE : PlantingReason.PLANTING_OK;
        }
        if (Plant.isFlying(theSeedType)) {
            return aPlantOnLawn.mFlyingPlant != null ? PlantingReason.PLANTING_NOT_HERE : PlantingReason.PLANTING_OK;
        }
        if (theSeedType == SeedType.SEED_SPIKEWEED || theSeedType == SeedType.SEED_SPIKEROCK) {
            if (aGridSquare == GridSquareType.GRIDSQUARE_POOL || stageHasRoof() || aUnderPlant != null) {
                return PlantingReason.PLANTING_NEEDS_GROUND;
            }
        }
        // non-aquatic plants need a lily pad on water, but a pumpkin shell may go on a cattail
        Plant aPumpkinPlant = aPlantOnLawn.mPumpkinPlant;
        if (aGridSquare == GridSquareType.GRIDSQUARE_POOL && !aHasLilypad && theSeedType != SeedType.SEED_CATTAIL) {
            if (aNormalPlant == null || aNormalPlant.mSeedType != SeedType.SEED_CATTAIL || theSeedType != SeedType.SEED_PUMPKINSHELL) {
                return PlantingReason.PLANTING_NOT_ON_WATER;
            }
        }
        if (theSeedType == SeedType.SEED_FLOWERPOT) {
            return (aNormalPlant != null || aUnderPlant != null || aPumpkinPlant != null) ? PlantingReason.PLANTING_NOT_HERE : PlantingReason.PLANTING_OK;
        }
        if (stageHasRoof() && !aHasFlowerPot) {
            return PlantingReason.PLANTING_NEEDS_POT;
        }
        boolean aAidPurchased = mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FIRSTAID] > 0;
        if (theSeedType == SeedType.SEED_PUMPKINSHELL) {
            if (aNormalPlant != null && aNormalPlant.mSeedType == SeedType.SEED_COBCANNON) {
                return PlantingReason.PLANTING_NOT_HERE;
            }
            if (aPumpkinPlant == null) {
                return PlantingReason.PLANTING_OK;
            }
            // wall-nut first aid on a damaged pumpkin
            if (aAidPurchased && aPumpkinPlant.mPlantHealth < aPumpkinPlant.mPlantMaxHealth * 2 / 3
                    && aPumpkinPlant.mSeedType == SeedType.SEED_PUMPKINSHELL && aPumpkinPlant.mOnBungeeState != PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
                return PlantingReason.PLANTING_OK;
            }

            return PlantingReason.PLANTING_NOT_HERE;
        }
        if (aHasLilypad && theSeedType == SeedType.SEED_POTATOMINE) {
            return PlantingReason.PLANTING_ONLY_ON_GROUND;
        }

        if (aUnderPlant != null) {
            if (theSeedType == SeedType.SEED_CATTAIL) {
                if (aNormalPlant != null) {
                    return PlantingReason.PLANTING_NOT_HERE;
                }
                if (aUnderPlant.isUpgradableTo(theSeedType) && aUnderPlant.mOnBungeeState != PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
                    return PlantingReason.PLANTING_OK;
                }
                if (Plant.isUpgrade(theSeedType)) {
                    return PlantingReason.PLANTING_NEEDS_UPGRADE;
                }
            } else {
                if (aUnderPlant.mSeedType == SeedType.SEED_IMITATER) {
                    return PlantingReason.PLANTING_NOT_HERE;
                }
            }
        }

        if (aNormalPlant != null) {
            if (aNormalPlant.isUpgradableTo(theSeedType) && aNormalPlant.mOnBungeeState != PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
                return PlantingReason.PLANTING_OK;
            }
            if (Plant.isUpgrade(theSeedType)) {
                return PlantingReason.PLANTING_NEEDS_UPGRADE;
            }

            // wall-nut first aid
            if ((theSeedType == SeedType.SEED_WALLNUT || theSeedType == SeedType.SEED_TALLNUT) && aAidPurchased) {
                if (aNormalPlant.mPlantHealth < aNormalPlant.mPlantMaxHealth * 2 / 3
                        && aNormalPlant.mSeedType == theSeedType && aNormalPlant.mOnBungeeState != PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
                    return PlantingReason.PLANTING_OK;
                }
            }

            return PlantingReason.PLANTING_NOT_HERE;
        }

        // the easy planting cheat skips the upgrade requirement
        if (!mApp.mEasyPlantingCheat && Plant.isUpgrade(theSeedType)) {
            return PlantingReason.PLANTING_NEEDS_UPGRADE;
        }
        if (theSeedType == SeedType.SEED_COBCANNON && !isValidCobCannonSpot(theGridX, theGridY)) {
            return PlantingReason.PLANTING_NEEDS_UPGRADE;
        } else if (theSeedType == SeedType.SEED_CATTAIL && aGridSquare != GridSquareType.GRIDSQUARE_POOL) {
            return PlantingReason.PLANTING_NOT_HERE;
        }

        return PlantingReason.PLANTING_OK;
    }

    // Board.cpp:9276
    public boolean plantingRequirementsMet(int theSeedType) {
        switch (theSeedType) {
            case SeedType.SEED_GATLINGPEA:
                return countPlantByType(SeedType.SEED_REPEATER) > 0;
            case SeedType.SEED_TWINSUNFLOWER:
                return countPlantByType(SeedType.SEED_SUNFLOWER) > 0;
            case SeedType.SEED_GLOOMSHROOM:
                return countPlantByType(SeedType.SEED_FUMESHROOM) > 0;
            case SeedType.SEED_CATTAIL:
                return countEmptyPotsOrLilies(SeedType.SEED_LILYPAD) > 0;
            case SeedType.SEED_WINTERMELON:
                return countPlantByType(SeedType.SEED_MELONPULT) > 0;
            case SeedType.SEED_GOLD_MAGNET:
                return countPlantByType(SeedType.SEED_MAGNETSHROOM) > 0;
            case SeedType.SEED_SPIKEROCK:
                return countPlantByType(SeedType.SEED_SPIKEWEED) > 0;
            case SeedType.SEED_COBCANNON:
                return hasValidCobCannonSpot();
            default:
                return true;
        }
    }

    // Board.cpp:9371
    public boolean plantUsesAcceleratedPricing(int theSeedType) {
        return Plant.isUpgrade(theSeedType) && mApp.isSurvivalEndless(mApp.mGameMode);
    }

    // Board.cpp:9376
    public int getCurrentPlantCost(int theSeedType, int theImitaterType) {
        int aCost = Plant.getCost(theSeedType, theImitaterType);
        if (plantUsesAcceleratedPricing(theSeedType)) {
            aCost += countPlantByType(theSeedType) * 50;
        }
        return aCost;
    }

    // Board.cpp:8968
    public int seedNotRecommendedForLevel(int theSeedType) {
        int aNotRec = 0;
        if (Plant.isNocturnal(theSeedType) && !stageIsNight()) {
            aNotRec |= 1 << NotRecommend.NOT_RECOMMENDED_NOCTURNAL;
        }
        if (theSeedType == SeedType.SEED_INSTANT_COFFEE && stageIsNight()) {
            aNotRec |= 1 << NotRecommend.NOT_RECOMMENDED_AT_NIGHT;
        }
        if (theSeedType == SeedType.SEED_GRAVEBUSTER && !stageHasGraveStones()) {
            aNotRec |= 1 << NotRecommend.NOT_RECOMMENDED_NEEDS_GRAVES;
        }
        if (theSeedType == SeedType.SEED_PLANTERN && !stageHasFog()) {
            aNotRec |= 1 << NotRecommend.NOT_RECOMMENDED_NEEDS_FOG;
        }
        if (theSeedType == SeedType.SEED_FLOWERPOT && !stageHasRoof()) {
            aNotRec |= 1 << NotRecommend.NOT_RECOMMENDED_NEEDS_ROOF;
        }
        if (stageHasRoof() && (theSeedType == SeedType.SEED_SPIKEWEED || theSeedType == SeedType.SEED_SPIKEROCK)) {
            aNotRec |= 1 << NotRecommend.NOT_RECOMMENDED_ON_ROOF;
        }
        if (!stageHasPool() && Plant.isAquatic(theSeedType)) {
            aNotRec |= 1 << NotRecommend.NOT_RECOMMENDED_NEEDS_POOL;
        }
        return aNotRec;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:8433-8506 — sun economy
    // ---------------------------------------------------------------------------------------

    // Board.cpp:8433
    public void addSunMoney(int theAmount) {
        int aAmount = LawnHooksRegistry.dispatcher().onSunCollected(this, theAmount, 0);
        mSunMoney += aAmount;
        mSunMoney = Math.min(mSunMoney, 9990);
        if (mSunMoney >= 8000) {
            mApp.giveAchievement(AchievementId.SunnyDays, true);
        }
    }

    // Board.cpp:8441
    public int countSunBeingCollected() {
        int aCount = 0;
        for (Coin aCoin : mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (aCoin.mIsBeingCollected && aCoin.isSun()) {
                aCount += aCoin.getSunValue();
            }
        }
        return aCount;
    }

    // Board.cpp:8456
    public int countCoinsBeingCollected() {
        int aCount = 0;
        for (Coin aCoin : mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (aCoin.mIsBeingCollected && aCoin.isMoney()) {
                aCount += Coin.getCoinValue(aCoin.mType);
            }
        }
        return aCount;
    }

    // Board.cpp:8471
    public boolean takeSunMoney(int theAmount) {
        if (canTakeSunMoney(theAmount)) {
            mSunMoney -= theAmount;
            return true;
        }

        mApp.playSample(Resources.SOUND_BUZZER_ID);
        mOutOfMoneyCounter = 70;
        return false;
    }

    // Board.cpp:8484
    public boolean canTakeSunMoney(int theAmount) {
        return theAmount <= mSunMoney + countSunBeingCollected();
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:8489-8546 — the death/cleanup queue
    // ---------------------------------------------------------------------------------------

    // Board.cpp:8489
    public void processDeleteQueue() {
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                mPlants.dataArrayFree(aPlant);
            }
        }
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                mZombies.dataArrayFree(aZombie);
            }
        }
        for (Projectile aProjectile : mProjectiles) {
            if (aProjectile.mDead) {
                mProjectiles.dataArrayFree(aProjectile);
            }
        }
        for (Coin aCoin : mCoins) {
            if (aCoin.mDead) {
                mCoins.dataArrayFree(aCoin);
            }
        }
        for (LawnMower aLawnMower : mLawnMowers) {
            if (aLawnMower.mDead) {
                mLawnMowers.dataArrayFree(aLawnMower);
            }
        }
        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                mGridItems.dataArrayFree(aGridItem);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:8547-8736 — seed bank geometry and stage classifiers
    // ---------------------------------------------------------------------------------------

    // Board.cpp:8547
    public boolean hasConveyorBeltSeedBank() {
        return mApp.isFinalBossLevel()
                || mApp.isMiniBossLevel()
                || mApp.isShovelLevel()
                || mApp.isWallnutBowlingLevel()
                || mApp.isLittleTroubleLevel()
                || mApp.isStormyNightLevel()
                || mApp.isBungeeBlitzLevel()
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL;
    }

    // Board.cpp:8562
    public int getNumSeedsInBank() {
        if (mApp.isScaryPotterLevel()) {
            return 1;
        }
        if (mApp.isWhackAZombieLevel()) {
            return 3;
        }
        if (mApp.isChallengeWithoutSeedBank()) {
            return 0;
        }
        if (hasConveyorBeltSeedBank()) {
            return 10;
        }
        if (mApp.isSlotMachineLevel()) {
            return 3;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE) {
            return 6;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            return 0;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            return 2;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2
                || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3 || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4) {
            return 3;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5 || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6
                || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7) {
            return 4;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8) {
            return 6;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9) {
            return 8;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS) {
            return 9;
        }

        int aNumSeeds = mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] + 6;
        int aSeedsAvailable = mApp.getSeedsAvailable();
        return Math.min(aNumSeeds, aSeedsAvailable);
    }

    // Board.cpp:8624
    public boolean stageIsNight() {
        return mBackground == BackgroundType.BACKGROUND_2_NIGHT
                || mBackground == BackgroundType.BACKGROUND_4_FOG
                || mBackground == BackgroundType.BACKGROUND_6_BOSS
                || mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                || mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM;
    }

    // Board.cpp:8634
    public boolean stageHasGraveStones() {
        if (mApp.isWallnutBowlingLevel()
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_POGO_PARTY
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                || mApp.isIZombieLevel()
                || mApp.isScaryPotterLevel()) {
            return false;
        }

        return mBackground == BackgroundType.BACKGROUND_2_NIGHT;
    }

    // Board.cpp:8649
    public boolean stageHasRoof() {
        return mBackground == BackgroundType.BACKGROUND_5_ROOF || mBackground == BackgroundType.BACKGROUND_6_BOSS;
    }

    // Board.cpp:8654
    public boolean stageHasPool() {
        return mBackground == BackgroundType.BACKGROUND_3_POOL || mBackground == BackgroundType.BACKGROUND_4_FOG;
    }

    // Board.cpp:8659
    public boolean stageHas6Rows() {
        return mBackground == BackgroundType.BACKGROUND_3_POOL || mBackground == BackgroundType.BACKGROUND_4_FOG;
    }

    // Board.cpp:8664
    public boolean stageHasZombieWalkInFromRight() {
        if (mApp.isWhackAZombieLevel()
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM
                || mApp.isFinalBossLevel()
                || mApp.isIZombieLevel()
                || mApp.isSquirrelLevel()
                || mApp.isScaryPotterLevel()) {
            return false;
        }

        return true;
    }

    // Board.cpp:8680
    public boolean stageHasFog() {
        return !mApp.isStormyNightLevel() && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL
                && mBackground == BackgroundType.BACKGROUND_4_FOG;
    }

    // Board.cpp:8685
    public boolean stageIsDayWithoutPool() {
        return mBackground == BackgroundType.BACKGROUND_1_DAY;
    }

    // Board.cpp:8689
    public boolean stageIsDayWithPool() {
        return mBackground == BackgroundType.BACKGROUND_3_POOL;
    }

    // Board.cpp:8693
    public int leftFogColumn() {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_AIR_RAID) {
            return 6;
        }
        if (!mApp.isAdventureMode()) {
            return 5;
        }
        if (mLevel == 31) {
            return 6;
        }
        if (mLevel >= 32 && mLevel <= 36) {
            return 5;
        }
        if (mLevel >= 37 && mLevel <= 40) {
            return 4;
        }
        // src/Lawn/Board.cpp:8693-8702 — upstream `PVZP_ASSERT(false); unreachable();`: a fog stage
        // is always air raid (col 6), non-adventure (5), or adventure level 31-40.
        throw new IllegalStateException("leftFogColumn: unhandled level " + mLevel);
    }

    // Board.cpp:8705
    public int getSeedPacketPositionX(int theIndex) {
        if (mApp.isSlotMachineLevel()) {
            return theIndex * 59 + 247;
        }
        if (hasConveyorBeltSeedBank()) {
            return theIndex * 50 + 91;
        }

        if (mSeedBank.mNumPackets <= 7) {
            return theIndex * 59 + 85;
        } else if (mSeedBank.mNumPackets == 8) {
            return theIndex * 54 + 81;
        } else if (mSeedBank.mNumPackets == 9) {
            return theIndex * 52 + 80;
        } else {
            return theIndex * 51 + 79;
        }
    }

    // Board.cpp:8716
    public int getSeedBankExtraWidth() {
        int aNumPackets = mSeedBank.mNumPackets;
        return aNumPackets <= 6 ? 0 : aNumPackets == 7 ? 60 : aNumPackets == 8 ? 76 : aNumPackets == 9 ? 112 : 153;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:8722-8922 — grid <-> pixel math
    // ---------------------------------------------------------------------------------------

    // Board.cpp:8722
    public int offsetYForPlanting(int theY, int theSeedType) {
        if (Plant.isFlying(theSeedType) || theSeedType == SeedType.SEED_GRAVEBUSTER) {
            theY += 15;
        }
        if (theSeedType == SeedType.SEED_SPIKEWEED || theSeedType == SeedType.SEED_SPIKEROCK) {
            theY -= 15;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN && mBackground == BackgroundType.BACKGROUND_GREENHOUSE) {
            theY -= 25;
        }
        return theY;
    }

    // Board.cpp:8738
    public int plantingPixelToGridX(int theX, int theY, int theSeedType) {
        theY = offsetYForPlanting(theY, theSeedType);
        return pixelToGridX(theX, theY);
    }

    // Board.cpp:8744
    public int plantingPixelToGridY(int theX, int theY, int theSeedType) {
        theY = offsetYForPlanting(theY, theSeedType);

        int aGridY = pixelToGridY(theX, theY);
        if (theSeedType == SeedType.SEED_INSTANT_COFFEE) {
            int aGridX = pixelToGridX(theX, theY);

            Plant aPlant = getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
            if (aPlant != null && aPlant.mIsAsleep) {
                return aGridY;
            }

            int aGridYDown = pixelToGridY(theX, theY + 30);
            if (aGridYDown != aGridY) {
                Plant aPlantDown = getTopPlantAt(aGridX, aGridYDown, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
                if (aPlantDown != null && aPlantDown.mIsAsleep) {
                    return aGridYDown;
                }
            }

            int aGridYUp = pixelToGridY(theX, theY - 50);
            if (aGridYUp != aGridY) {
                Plant aPlantUp = getTopPlantAt(aGridX, aGridYUp, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
                if (aPlantUp != null && aPlantUp.mIsAsleep) {
                    return aGridYUp;
                }
            }
        }
        return aGridY;
    }

    // Board.cpp:8782
    public int pixelToGridX(int theX, int theY) {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && (mBackground == BackgroundType.BACKGROUND_GREENHOUSE
                || mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                || mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM)) {
            return mApp.mZenGarden.pixelToGridX(theX, theY);
        }

        if (theX < GameConstants.LAWN_XMIN) {
            return -1;
        }

        return clampInt((theX - GameConstants.LAWN_XMIN) / 80, 0, MAX_GRID_SIZE_X - 1);
    }

    // Board.cpp:8800
    public int pixelToGridXKeepOnBoard(int theX, int theY) {
        int aGridX = pixelToGridX(theX, theY);
        return Math.max(aGridX, 0);
    }

    // Board.cpp:8806
    public int pixelToGridY(int theX, int theY) {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && (mBackground == BackgroundType.BACKGROUND_GREENHOUSE
                || mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                || mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM)) {
            return mApp.mZenGarden.pixelToGridY(theX, theY);
        }

        int aGridX = pixelToGridX(theX, theY);
        if (aGridX == -1 || theY < GameConstants.LAWN_YMIN) {
            return -1;
        }

        if (stageHasRoof()) {
            if (aGridX < 5) {
                theY -= (4 - aGridX) * 20;
            }
            return clampInt((theY - GameConstants.LAWN_YMIN) / 85, 0, MAX_GRID_SIZE_Y - 2);
        } else if (stageHasPool()) {
            return clampInt((theY - GameConstants.LAWN_YMIN) / 85, 0, MAX_GRID_SIZE_Y - 1);
        } else {
            return clampInt((theY - GameConstants.LAWN_YMIN) / 100, 0, MAX_GRID_SIZE_Y - 2);
        }
    }

    // Board.cpp:8840
    public int pixelToGridYKeepOnBoard(int theX, int theY) {
        int aGridY = pixelToGridY(Math.max(theX, 80), theY);
        return Math.max(aGridY, 0);
    }

    // Board.cpp:8846
    public int gridToPixelX(int theGridX, int theGridY) {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && (mBackground == BackgroundType.BACKGROUND_GREENHOUSE
                || mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                || mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM)) {
            return mApp.mZenGarden.gridToPixelX(theGridX, theGridY);
        }

        return theGridX * 80 + GameConstants.LAWN_XMIN;
    }

    // Board.cpp:8863
    public float getPosYBasedOnRow(float thePosX, int theRow) {
        if (stageHasRoof()) {
            float aSlopeOffset = 0.0f;
            if (thePosX < 440.0f) {
                aSlopeOffset = (440.0f - thePosX) * 0.25f;
            }

            return gridToPixelY(8, theRow) + aSlopeOffset;
        }

        return gridToPixelY(0, theRow);
    }

    // Board.cpp:8879
    public int gridToPixelY(int theGridX, int theGridY) {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && (mBackground == BackgroundType.BACKGROUND_GREENHOUSE
                || mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                || mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM)) {
            return mApp.mZenGarden.gridToPixelY(theGridX, theGridY);
        }

        int aY;
        if (stageHasRoof()) {
            int aSlopeOffset;
            if (theGridX < 5) {
                aSlopeOffset = (5 - theGridX) * 20;
            } else {
                aSlopeOffset = 0;
            }
            aY = theGridY * 85 + aSlopeOffset + GameConstants.LAWN_YMIN - 10;
        } else if (stageHasPool()) {
            aY = theGridY * 85 + GameConstants.LAWN_YMIN;
        } else {
            aY = theGridY * 100 + GameConstants.LAWN_YMIN;
        }

        if (theGridX != -1 && mGridSquareType[theGridX][theGridY] == GridSquareType.GRIDSQUARE_HIGH_GROUND) {
            aY -= GameConstants.HIGH_GROUND_HEIGHT;
        }

        return aY;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:8924-8954, 8939, 8948 — zombie ids, overlap helpers, radius kill
    // ---------------------------------------------------------------------------------------

    // Board.cpp:8924
    public int zombieGetID(Zombie theZombie) {
        if (theZombie == null) {
            return ZombieID.ZOMBIEID_NULL;
        }
        return mZombies.dataArrayGetID(theZombie);
    }

    // Board.cpp:8929
    public Zombie zombieGet(int theZombieID) {
        return mZombies.dataArrayGet(theZombieID);
    }

    // Board.cpp:8934
    public Zombie zombieTryToGet(int theZombieID) {
        return mZombies.dataArrayTryToGet(theZombieID);
    }

    // Board.cpp:8939
    public static int getRectOverlap(Rect rect1, Rect rect2) {
        return Math.min(rect1.mX + rect1.mWidth, rect2.mX + rect2.mWidth) - Math.max(rect1.mX, rect2.mX);
    }

    // Board.cpp:8948
    public static boolean getCircleRectOverlap(int theCircleX, int theCircleY, int theRadius, Rect theRect) {
        int aNearX = clampInt(theCircleX, theRect.mX, theRect.mX + theRect.mWidth);
        int aNearY = clampInt(theCircleY, theRect.mY, theRect.mY + theRect.mHeight);
        int dx = theCircleX - aNearX;
        int dy = theCircleY - aNearY;
        return dx * dx + dy * dy <= theRadius * theRadius;
    }

    // Board.cpp:8954
    public void killAllPlantsInRadius(int theX, int theY, int theRadius) {
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (getCircleRectOverlap(theX, theY, theRadius, aPlant.getPlantRect())) {
                mPlantsEaten++;
                aPlant.die();
            }
        }
    }

    // Board.cpp:9002
    public int countCoinByType(int theCoinType) {
        int aCount = 0;

        for (Coin aCoin : mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (aCoin.mType == theCoinType) {
                aCount++;
            }
        }

        return aCount;
    }

    // Board.cpp:9019
    public int getGraveStoneCount() {
        int aCount = 0;

        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                aCount++;
            }
        }

        return aCount;
    }

    // Board.cpp:9036
    public void dropLootPiece(int thePosX, int thePosY, int theDropFactor) {
        if (mApp.isFirstTimeAdventureMode()) {
            if (mLevel == 22 && mCurrentWave > 5 && mApp.mPlayerInfo.mHasUnlockedMinigames == 0 && countCoinByType(CoinType.COIN_PRESENT_MINIGAMES) == 0) {
                mApp.playFoley(FoleyType.FOLEY_ART_CHALLENGE);
                addCoin(thePosX, thePosY, CoinType.COIN_PRESENT_MINIGAMES, CoinMotion.COIN_MOTION_COIN);
                return;
            }
            if (mLevel == 36 && mCurrentWave > 5 && mApp.mPlayerInfo.mHasUnlockedPuzzleMode == 0 && countCoinByType(CoinType.COIN_PRESENT_PUZZLE_MODE) == 0) {
                mApp.playFoley(FoleyType.FOLEY_ART_CHALLENGE);
                addCoin(thePosX, thePosY, CoinType.COIN_PRESENT_PUZZLE_MODE, CoinMotion.COIN_MOTION_COIN);
                return;
            }
        }

        int aDropHit = Common.rand(30000);
        if (mApp.isFirstTimeAdventureMode() && mLevel == 11 && !mDroppedFirstCoin && mCurrentWave > 5) {
            aDropHit = 1000;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            aDropHit *= 5;
        }

        if (mApp.isWhackAZombieLevel()) {
            int aSunChanceMin = 2500;
            int aSunChanceMax = mSunMoney > 500 ? 2800 : mSunMoney > 350 ? 3100 : mSunMoney > 200 ? 3700 : 5000;
            if (aDropHit >= aSunChanceMin * theDropFactor && aDropHit <= aSunChanceMax * theDropFactor) {
                mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
                addCoin(thePosX - 20, thePosY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
                addCoin(thePosX - 40, thePosY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
                addCoin(thePosX - 60, thePosY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
                return;
            }
        }

        if (mTotalSpawnedWaves > 70) {
            return;
        }

        int aPottedPlantChance;
        if (!mApp.mZenGarden.canDropPottedPlantLoot()) {
            aPottedPlantChance = 0;
        } else if (mApp.isAdventureMode() && !mApp.isFirstTimeAdventureMode()) {
            aPottedPlantChance = 24;
        } else {
            aPottedPlantChance = mApp.isSurvivalEndless(mApp.mGameMode) ? 3 : 12;
        }

        int aChocolateChance = aPottedPlantChance;
        if (mApp.mZenGarden.canDropChocolate()) {
            if (mApp.isAdventureMode() && !mApp.isFirstTimeAdventureMode()) {
                aChocolateChance = aPottedPlantChance + 72;
            } else {
                aChocolateChance = aPottedPlantChance + (mApp.isSurvivalEndless(mApp.mGameMode) ? 9 : 36);
            }
        }

        int aDiamondChance = aChocolateChance + 14;
        int aGoldChance = aChocolateChance + 250;
        int aSilverChance = aChocolateChance + 2500;

        int aCoinType;
        if (aDropHit < aPottedPlantChance * theDropFactor) {
            aCoinType = CoinType.COIN_PRESENT_PLANT;
        } else if (aDropHit < aChocolateChance * theDropFactor) {
            aCoinType = CoinType.COIN_CHOCOLATE;
        } else if (aDropHit < aDiamondChance * theDropFactor) {
            aCoinType = mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] < 1 ? CoinType.COIN_GOLD : CoinType.COIN_DIAMOND;
        } else if (aDropHit < aGoldChance * theDropFactor) {
            aCoinType = CoinType.COIN_GOLD;
        } else if (aDropHit < aSilverChance * theDropFactor) {
            aCoinType = CoinType.COIN_SILVER;
        } else {
            return;
        }

        if (mApp.isWallnutBowlingLevel() && Coin.isMoney(aCoinType)) {
            return;
        }

        if (mApp.isFirstTimeAdventureMode() && mLevel == 11) {
            int aMoney = Coin.getCoinValue(CoinType.COIN_GOLD) * mLawnMowers.mSize;
            int aCost = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);
            aMoney += mApp.mPlayerInfo.mCoins + countCoinsBeingCollected();
            if (Coin.getCoinValue(aCoinType) + aMoney >= aCost) {
                return;
            }
        }

        mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
        addCoin(thePosX - 40, thePosY, aCoinType, CoinMotion.COIN_MOTION_COIN);
        mDroppedFirstCoin = true;
    }

    // Board.cpp:9154
    public boolean canDropLoot() {
        return !mCutScene.shouldRunUpsellBoard() && (!mApp.isFirstTimeAdventureMode() || mLevel >= 11);
    }

    // Board.cpp:9159
    public boolean bungeeIsTargetingCell(int theGridX, int theGridY) {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying() && aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE
                    && aZombie.mRow == theGridY && aZombie.mTargetCol == theGridX) {
                return true;
            }
        }
        return false;
    }

    // Board.cpp:9173
    public Zombie getBossZombie() {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                return aZombie;
            }
        }
        return null;
    }

    // Board.cpp:9187
    public Plant findUmbrellaPlant(int theGridX, int theGridY) {
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == SeedType.SEED_UMBRELLA && !aPlant.notOnGround()
                    && LawnCommon.gridInRange(theGridX, theGridY, aPlant.mPlantCol, aPlant.mRow, 1, 1)) {
                return aPlant;
            }
        }
        return null;
    }

    // Board.cpp:9201
    public void doFwoosh(int theRow) {
        int aRenderOrder = makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, theRow, 1);
        for (int i = 0; i < 12; i++) {
            Reanimation aOriReanim = mApp.reanimationTryToGet(mFwooshID[theRow][i]);
            if (aOriReanim != null) {
                aOriReanim.reanimationDie();
            }

            float aPosX = 750.0f * i / 11.0f + 10.0f;
            float aPosY = getPosYBasedOnRow(aPosX + 10.0f, theRow) - 10.0f;
            Reanimation aFwoosh = mApp.addReanimation(aPosX, aPosY, aRenderOrder, ReanimationType.REANIM_JALAPENO_FIRE);
            aFwoosh.setFramesForLayer("anim_flame");
            aFwoosh.mLoopType = ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME;
            aFwoosh.mAnimRate *= PvzpCommon.randRangeFloat(0.7f, 1.3f);

            float aScale = PvzpCommon.randRangeFloat(0.9f, 1.1f);
            float aFlip = Common.rand(2) != 0 ? 1.0f : -1.0f;
            aFwoosh.overrideScale(aScale * aFlip, 1);

            mFwooshID[theRow][i] = mApp.reanimationGetID(aFwoosh);
        }
        mFwooshCountDown = 100;
    }

    // Board.cpp:9228
    public void updateFwoosh() {
        if (mFwooshCountDown == 0) {
            return;
        }

        int aFwooshRemaining = PvzpCommon.pvzpAnimateCurve(50, 0, --mFwooshCountDown, 12, 0, PvzpCurves.CURVE_LINEAR);
        for (int aRow = 0; aRow < MAX_GRID_SIZE_Y; aRow++) {
            for (int i = 0; i < 12 - aFwooshRemaining; i++) {
                Reanimation aFwoosh = mApp.reanimationTryToGet(mFwooshID[aRow][i]);
                if (aFwoosh != null) {
                    aFwoosh.setFramesForLayer("anim_done");
                    aFwoosh.mAnimRate = 15;
                    aFwoosh.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME;
                }
                mFwooshID[aRow][i] = ReanimationID.REANIMATIONID_NULL;
            }
        }
    }

    // Board.cpp:9250
    public void updateGridItems() {
        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (mEnableGraveStones && aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE && aGridItem.mGridItemCounter < 100) {
                aGridItem.mGridItemCounter++;
            }

            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_CRATER && mApp.mGameScene == GameScenes.SCENE_PLAYING) {
                if (aGridItem.mGridItemCounter > 0) {
                    aGridItem.mGridItemCounter--;
                }
                if (aGridItem.mGridItemCounter == 0) {
                    aGridItem.gridItemDie();
                }
            }
            aGridItem.update();
        }
    }

    // Board.cpp:9292
    public int killAllZombiesInRadius(int theRow, int theX, int theY, int theRadius, int theRowRange, boolean theBurn, int theDamageRangeFlags) {
        int aKilledZombies = 0;
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.effectedByDamage(theDamageRangeFlags)) {
                Rect aZombieRect = aZombie.getZombieRect();
                int aRowDist = aZombie.mRow - theRow;
                if (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                    aRowDist = 0;
                }

                if (aRowDist <= theRowRange && aRowDist >= -theRowRange && getCircleRectOverlap(theX, theY, theRadius, aZombieRect)) {
                    if (theBurn) {
                        aZombie.applyBurn();
                    } else {
                        aZombie.takeDamage(1800, DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY);
                    }

                    aKilledZombies++;
                }
            }
        }

        int aGridX = pixelToGridXKeepOnBoard(theX, theY);
        int aGridY = pixelToGridYKeepOnBoard(theX, theY);
        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_LADDER) {
                if (LawnCommon.gridInRange(aGridItem.mGridX, aGridItem.mGridY, aGridX, aGridY, theRowRange, theRowRange)) {
                    aGridItem.gridItemDie();
                }
            }
        }

        return aKilledZombies;
    }

    // Board.cpp:9358
    public void removeParticleByType(int theEffectType) {
        for (PvzpParticleSystem aParticle : mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            if (aParticle.mDead) {
                continue;
            }
            if (aParticle.mEffectType == theEffectType) {
                aParticle.particleSystemDie();
            }
        }
    }

    // Board.cpp:9386
    public boolean canUseGameObject(int theGameObject) {
        if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            return theGameObject == GameObjectType.OBJECT_TYPE_TREE_FOOD || theGameObject == GameObjectType.OBJECT_TYPE_NEXT_GARDEN;
        }
        if (mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            return false;
        }

        switch (theGameObject) {
            case GameObjectType.OBJECT_TYPE_WATERING_CAN:
                return true;
            case GameObjectType.OBJECT_TYPE_NEXT_GARDEN:
                return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_MUSHROOM_GARDEN] != 0
                        || mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_AQUARIUM_GARDEN] != 0
                        || mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] != 0;
            case GameObjectType.OBJECT_TYPE_FERTILIZER:
                return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] > 0;
            case GameObjectType.OBJECT_TYPE_BUG_SPRAY:
                return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY] > 0;
            case GameObjectType.OBJECT_TYPE_PHONOGRAPH:
                return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PHONOGRAPH] > 0;
            case GameObjectType.OBJECT_TYPE_CHOCOLATE:
                return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] > 0;
            case GameObjectType.OBJECT_TYPE_WHEELBARROW:
                return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_WHEEL_BARROW] > 0;
            case GameObjectType.OBJECT_TYPE_GLOVE:
                return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_GARDENING_GLOVE] > 0;
            case GameObjectType.OBJECT_TYPE_MONEY_SIGN:
                return mApp.hasFinishedAdventure();
            case GameObjectType.OBJECT_TYPE_TREE_FOOD:
                return false;
            default:
                // src/Lawn/Board.cpp:9420-9423 — upstream `default: PVZP_ASSERT(false);`
                // `unreachable();`; every GameObjectType is handled above.
                throw new IllegalStateException("canUseGameObject: unknown object " + theGameObject);
        }
    }

    // Board.cpp:9428
    public void shakeBoard(int theShakeAmountX, int theShakeAmountY) {
        mShakeCounter = 12;
        mShakeAmountX = theShakeAmountX;
        mShakeAmountY = theShakeAmountY;
    }

    // Board.cpp:9435
    public LawnMower findLawnMowerInRow(int theRow) {
        for (LawnMower aLawnMower : mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            if (aLawnMower.mRow == theRow) {
                return aLawnMower;
            }
        }
        return null;
    }

    // Board.cpp:9449
    public Zombie getWinningZombie() {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mFromWave == Zombie.ZOMBIE_WAVE_WINNER) {
                return aZombie;
            }
        }
        return null;
    }

    // Board.cpp:9463
    public int countZombieByType(int theZombieType) {
        int aCount = 0;

        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mZombieType == theZombieType) {
                aCount++;
            }
        }

        return aCount;
    }

    // Board.cpp:9342
    public int getNumWavesPerSurvivalStage() {
        return BoardWaves.getNumWavesPerSurvivalStage(this);
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:1222-2666 — cutscene freeze, object removal, ice
    // ---------------------------------------------------------------------------------------

    // Board.cpp:1222
    public void freezeEffectsForCutscene(boolean theFreeze) {
        for (PvzpParticleSystem aParticle : mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            if (aParticle.mDead) {
                continue;
            }
            if (aParticle.mEffectType == ParticleEffect.PARTICLE_GRAVE_BUSTER) {
                aParticle.mDontUpdate = theFreeze;
            } else if (aParticle.mEffectType == ParticleEffect.PARTICLE_POOL_SPARKLY && mIceTrapCounter == 0) {
                aParticle.mDontUpdate = theFreeze;
            }
        }

        for (Reanimation aReanim : mApp.mEffectSystem.mReanimationHolder.mReanimations) {
            if (aReanim.mDead) {
                continue;
            }
            if (aReanim.mReanimationType == ReanimationType.REANIM_SLEEPING) {
                aReanim.mAnimRate = theFreeze ? 0.0f : PvzpCommon.randRangeFloat(6, 8);
            }
        }
    }

    // Board.cpp:2640
    public void removeAllZombies() {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying()) {
                aZombie.dieNoLoot();
            }
        }
    }

    // Board.cpp:2653
    public void removeZombiesForRepick() {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying() && aZombie.mMindControlled && aZombie.mPosX > 720) {
                aZombie.dieNoLoot();
            }
        }
    }

    // Board.cpp:2666
    public void removeCutsceneZombies() {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mFromWave == Zombie.ZOMBIE_WAVE_CUTSCENE) {
                aZombie.dieNoLoot();
            }
        }
    }

    // Board.cpp:2679
    public boolean isIceAt(int theGridX, int theGridY) {
        if (mIceTimer[theGridY] == 0 || mIceMinX[theGridY] > 750) {
            return false;
        }

        return theGridX >= pixelToGridXKeepOnBoard(mIceMinX[theGridY] + 12, 0);
    }

    // Board.cpp:4643
    public void showCoinBank(int theDuration) {
        mCoinBankFadeCount = theDuration;
    }

    /** Java-only default-argument wrapper of Board.cpp:4643 ({@code theDuration = 1000}). */
    public void showCoinBank() {
        showCoinBank(1000);
    }

    // Board.cpp:4648
    public void pause(boolean thePause) {
        if (mPaused == thePause) {
            return;
        }

        mPaused = thePause;
        if (thePause && mApp.mPlayerInfo.mCoins > 0) {
            showCoinBank();
        }

        if (!thePause || mApp.mGameScene != GameScenes.SCENE_LEVEL_INTRO) {
            mApp.mSoundSystem.gamePause(thePause);
            mApp.mMusic.gameMusicPause(thePause);
        }

        if (thePause && needSaveGame()) {
            tryToSaveGame();
        }
    }

    // Board.cpp:7368
    public boolean isScaryPotterDaveTalking() {
        return mApp.isScaryPotterLevel() && mNextSurvivalStageCounter > 0
                && mApp.mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_OFF;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:4710-4960 — wave/level end state, on-screen hooks
    // ---------------------------------------------------------------------------------------

    // Board.cpp:5009
    public int getSurvivalFlagsCompleted() {
        int aWavesPerFlag = getNumWavesPerFlag();
        int aFlagsCompleted = mChallenge.mSurvivalStage * getNumWavesPerSurvivalStage() / aWavesPerFlag;
        int aCurrentWave = mCurrentWave;
        if (isFlagWave(aCurrentWave - 1) && mBoardFadeOutCounter < 0 && mNextSurvivalStageCounter == 0) {
            aCurrentWave -= 1;
        }
        return aCurrentWave / aWavesPerFlag + aFlagsCompleted;
    }

    // Board.cpp:5021
    public void survivalSaveScore() {
        if (!mApp.isSurvivalMode()) {
            return;
        }

        int aFlagsCompleted = getSurvivalFlagsCompleted();
        int aIndex = mApp.getCurrentChallengeIndex();
        int aFlagsRecord = mApp.mPlayerInfo.mChallengeRecords[aIndex];
        if (aFlagsCompleted > aFlagsRecord) {
            mApp.mPlayerInfo.mChallengeRecords[aIndex] = aFlagsCompleted;
            mApp.writeCurrentUserConfig();
        }
    }

    // Board.cpp:5035
    public void puzzleSaveStreak() {
        if (!mApp.isEndlessIZombie(mApp.mGameMode) && !mApp.isEndlessScaryPotter(mApp.mGameMode)) {
            return;
        }

        int aStreak = mChallenge.mSurvivalStage + 1;
        int aIndex = mApp.getCurrentChallengeIndex();
        int aRecord = mApp.mPlayerInfo.mChallengeRecords[aIndex];
        if (aStreak > aRecord) {
            mApp.mPlayerInfo.mChallengeRecords[aIndex] = aStreak;
            mApp.writeCurrentUserConfig();
        }
    }

    // Board.cpp:5049
    public void zombiesWon(Zombie theZombie) {
        if (mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            return;
        }

        clearAdvice(AdviceType.ADVICE_NONE);
        mApp.mBoardResult = BoardResult.BOARDRESULT_LOST;

        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie == theZombie) {
                continue;
            }

            if (aZombie.getZombieRect().mX < -50
                    || aZombie.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE
                    || aZombie.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING) {
                if ((aZombie.mZombieType == ZombieType.ZOMBIE_GARGANTUAR || aZombie.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR)
                        && aZombie.isDeadOrDying() && aZombie.mPosX < 140) {
                    aZombie.dieNoLoot();
                }
            }
        }
        survivalSaveScore();

        String aGameOverMsg;
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            aGameOverMsg = "[ZOMBIQUARIUM_DEATH_MESSAGE]";
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            String aFlagStr = LawnApp.pluralize(getSurvivalFlagsCompleted(), "[ONE_FLAG]", "[COUNT_FLAGS]");
            aGameOverMsg = PvzpCommon.pvzpReplaceString("[LAST_STAND_DEATH_MESSAGE]", "{FLAGS}", aFlagStr);
        } else if (mApp.isEndlessIZombie(mApp.mGameMode) || mApp.isEndlessScaryPotter(mApp.mGameMode)) {
            aGameOverMsg = PvzpCommon.pvzpReplaceNumberString("[ENDLESS_PUZZLE_DEATH_MESSAGE]", "{STREAK}", mChallenge.mSurvivalStage);
        } else if (mApp.isIZombieLevel()) {
            aGameOverMsg = "[I_ZOMBIE_DEATH_MESSAGE]";
        } else {
            mApp.mGameScene = GameScenes.SCENE_ZOMBIES_WON;
            if (theZombie != null) { // theZombie can be null when game over is triggered without a zombie
                theZombie.walkIntoHouse();
            }

            clearAdvice(AdviceType.ADVICE_NONE);
            mCutScene.startZombiesWon();
            freezeEffectsForCutscene(true);
            BoardInput.tutorialArrowRemove(this);
            BoardInput.updateCursor(this, null);
            return;
        }

        GameOverDialog aGameOverDialog = new GameOverDialog(aGameOverMsg, true);
        mApp.addDialog(Dialogs.DIALOG_GAME_OVER, aGameOverDialog);
        mApp.mWidgetManager.setFocus(aGameOverDialog);
        mApp.mMusic.stopAllMusic();
        stopAllZombieSounds();
        mApp.playSample(Resources.SOUND_LOSEMUSIC_ID);

        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIES_WON, true);
        Reanimation aReanim = mApp.addReanimation(-GameConstants.BOARD_OFFSET, 0,
                makeRenderOrder(RenderLayer.RENDER_LAYER_SCREEN_FADE, 0, 0), ReanimationType.REANIM_ZOMBIES_WON);
        aReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        aReanim.getTrackInstanceByName("fullscreen").mTrackColor = new Color(0, 0, 0, 255);
        aReanim.setFramesForLayer("anim_screen");
    }

    // Board.cpp:5126
    public boolean isFinalScaryPotterStage() {
        if (!mApp.isScaryPotterLevel()) {
            return false;
        }

        if (mApp.isAdventureMode()) {
            return mChallenge.mSurvivalStage == 2;
        }

        return !mApp.isEndlessScaryPotter(mApp.mGameMode);
    }

    // Board.cpp:5139
    public boolean isFinalSurvivalStage() {
        if (!mApp.isSurvivalMode()) {
            return false;
        }

        int aFlags = getNumWavesPerSurvivalStage() * (mChallenge.mSurvivalStage + 1) / getNumWavesPerFlag();
        if (mApp.isSurvivalNormal(mApp.mGameMode)) {
            return aFlags >= 5;
        }
        if (mApp.isSurvivalHard(mApp.mGameMode)) {
            return aFlags >= 10;
        }

        return false;
    }

    // Board.cpp:5157
    public boolean isLastStandFinalStage() {
        return mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND && mChallenge.mSurvivalStage == GameConstants.LAST_STAND_FLAGS - 1;
    }

    // Board.cpp:5162
    public boolean isSurvivalStageWithRepick() {
        return mApp.isSurvivalMode() && !isFinalSurvivalStage();
    }

    // Board.cpp:5167
    public boolean isLastStandStageWithRepick() {
        return mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND && !isLastStandFinalStage();
    }

    // Board.cpp:5172
    public boolean hasLevelAwardDropped() {
        return mLevelAwardSpawned || mNextSurvivalStageCounter > 0 || mBoardFadeOutCounter >= 0;
    }

    // Board.cpp:5177
    public void updateSunSpawning() {
        BoardWaves.updateSunSpawning(this);
    }

    // Board.cpp:5214
    public void nextWaveComing() {
        BoardWaves.nextWaveComing(this);
    }

    // Board.cpp:5235
    public void updateZombieSpawning() {
        BoardWaves.updateZombieSpawning(this);
    }

    // Board.cpp:4953
    public void updateGameObjects() {
        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            aPlant.update();
        }

        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            aZombie.update();
        }

        for (Projectile aProjectile : mProjectiles) {
            if (aProjectile.mDead) {
                continue;
            }
            aProjectile.update();
        }

        for (Coin aCoin : mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            aCoin.update();
        }

        for (LawnMower aLawnMower : mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            aLawnMower.update();
        }

        mCursorPreview.update();
        mCursorObject.update();

        for (int i = 0; i < mSeedBank.mNumPackets; i++) {
            mSeedBank.mSeedPackets[i].update();
        }
    }

    // Board.cpp:4999
    public void stopAllZombieSounds() {
        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            aZombie.stopZombieSound();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:5381-5480 — ice and the progress meter
    // ---------------------------------------------------------------------------------------

    // Board.cpp:5381
    public void updateIce() {
        for (int aRow = 0; aRow < MAX_GRID_SIZE_Y; aRow++) {
            if (mIceTimer[aRow] != 0) {
                mIceTimer[aRow]--;
                PvzpParticleSystem aParticleIce = mApp.particleTryToGet(mIceParticleID[aRow]);
                if (mIceTimer[aRow] == 0) {
                    mIceMinX[aRow] = GameConstants.BOARD_ICE_START;
                    if (aParticleIce != null) {
                        aParticleIce.particleSystemDie();
                    }
                } else {
                    float aPosX = mIceMinX[aRow];
                    float aPosY = gridToPixelY(8, aRow);
                    if (aParticleIce != null) {
                        aParticleIce.systemMove(aPosX, aPosY);
                    } else {
                        int aRenderPosition = makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, aRow, 3);
                        aParticleIce = mApp.addPvzpParticle(aPosX, aPosY, aRenderPosition, ParticleEffect.PARTICLE_ICE_SPARKLE);
                        mIceParticleID[aRow] = mApp.particleGetID(aParticleIce);
                    }
                }

                int anAlpha = clampInt(mIceTimer[aRow] / 10, 0, 255);
                aParticleIce.overrideColor(null, new Color(255, 255, 255, anAlpha));
            }
        }
    }

    // Board.cpp:5419
    public void updateProgressMeter() {
        if (mApp.isFinalBossLevel()) {
            Zombie aBoss = getBossZombie();
            if (aBoss != null && !aBoss.isDeadOrDying()) {
                mProgressMeterWidth = 150 * (aBoss.mBodyMaxHealth - aBoss.mBodyHealth) / aBoss.mBodyMaxHealth;
            } else {
                mProgressMeterWidth = 150;
            }
        } else if (mCurrentWave != 0) {
            if (mFlagRaiseCounter > 0) {
                mFlagRaiseCounter--;
            }

            int aTotalWidth = 150; // total meter length distributed across the waves
            int aNumWavesPerFlag = getNumWavesPerFlag();
            boolean aHasFlags = BoardRender.progressMeterHasFlags(this); // flag waves occupy extra meter length when flags are drawn
            if (aHasFlags) {
                aTotalWidth -= 12 * mNumWaves / aNumWavesPerFlag;
            }

            int aWaveLength = aTotalWidth / (mNumWaves - 1);
            int aCurrentWaveLength = (mCurrentWave - 1) * aTotalWidth / (mNumWaves - 1);
            int aNextWaveLength = mCurrentWave * aTotalWidth / (mNumWaves - 1);
            if (aHasFlags) {
                int anExtraLength = mCurrentWave / aNumWavesPerFlag * 12; // add back the flag length of completed flag waves
                aCurrentWaveLength += anExtraLength;
                aNextWaveLength += anExtraLength;
            }

            float aFraction = (mZombieCountDownStart - mZombieCountDown) / (float) mZombieCountDownStart;
            if (mZombieHealthToNextWave != -1) {
                int aHealthCurrent = totalZombiesHealthInWave(mCurrentWave - 1);
                // damage that must be dealt to this wave to trigger the next wave
                int aDamageTarget = mZombieHealthWaveStart - mZombieHealthToNextWave;
                if (aDamageTarget < 1) {
                    aDamageTarget = 1;
                }
                // health fraction = damage dealt to this wave / damage needed to trigger the next wave
                float aHealthFraction = (aDamageTarget - aHealthCurrent + mZombieHealthToNextWave) / (float) aDamageTarget;
                aFraction = Math.max(aHealthFraction, aFraction);
            }

            int aLength = clampInt(aCurrentWaveLength + PvzpCommon.floatRoundToInt((aNextWaveLength - aCurrentWaveLength) * aFraction), 1, 150);
            int aDelta = aLength - mProgressMeterWidth;
            // adjust every 20cs, or every 5cs when more than a wave length behind
            if ((aDelta > aWaveLength && (mMainCounter % 5 == 0)) || (aDelta > 0 && (mMainCounter % 20 == 0))) {
                mProgressMeterWidth++;
            }
        }
    }

    // Board.cpp:5481
    public void updateTutorial() {
        BoardInput.updateTutorial(this);
    }

    // Board.cpp:5528
    public void setTutorialState(int theTutorialState) {
        BoardInput.setTutorialState(this, theTutorialState);
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:5631-5806 — the update pipeline
    // ---------------------------------------------------------------------------------------

    // Board.cpp:5631
    public void updateGame() {
        updateGameObjects();
        if (stageHasFog() && mFogBlownCountDown > 0) {
            float aMaxFogOffset = 1065.0f - leftFogColumn() * 80.0f;
            if (mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
                mFogOffset = PvzpCommon.pvzpAnimateCurveFloat(200, 0, mFogBlownCountDown, aMaxFogOffset, 0, PvzpCurves.CURVE_EASE_OUT);
            } else if (mFogBlownCountDown < 2000) {
                mFogOffset = PvzpCommon.pvzpAnimateCurveFloat(2000, 0, mFogBlownCountDown, aMaxFogOffset, 0, PvzpCurves.CURVE_EASE_OUT);
            } else if (mFogOffset < aMaxFogOffset) {
                mFogOffset = PvzpCommon.pvzpAnimateCurveFloatTime(-5, aMaxFogOffset, mFogOffset * 1.1f, 0, aMaxFogOffset, PvzpCurves.CURVE_LINEAR);
            }
        }

        if (mApp.mGameScene != GameScenes.SCENE_PLAYING && !mCutScene.shouldRunUpsellBoard()) {
            return;
        }

        mMainCounter++;
        updateSunSpawning();
        updateZombieSpawning();
        updateIce();
        if (mIceTrapCounter > 0) {
            mIceTrapCounter--;
            if (mIceTrapCounter == 0) {
                PvzpParticleSystem aPoolSparklyParticle = mApp.particleTryToGet(mPoolSparklyParticleID);
                if (aPoolSparklyParticle != null) {
                    aPoolSparklyParticle.mDontUpdate = false;
                }
            }
        }

        if (mFogBlownCountDown > 0) {
            mFogBlownCountDown--;
        }

        if (mMainCounter == 1 && mApp.isFirstTimeAdventureMode()) {
            if (mLevel == 1) {
                setTutorialState(TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER);
            } else if (mLevel == 2) {
                setTutorialState(TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER);
                displayAdvice("[ADVICE_PLANT_SUNFLOWER1]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2, AdviceType.ADVICE_NONE);
                mTutorialTimer = 500;
            }
        }

        updateProgressMeter();
        LawnHooksRegistry.dispatcher().onTick(this, mMainCounter);
    }

    // Board.cpp:5693
    @Override
    public void update() {
        super.update();
        markDirty();

        mBoardUpdateCounter++;
        mCutScene.update();
        BoardInput.updateMousePosition(this);
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            mApp.mZenGarden.zenGardenUpdate();
        }
        if (isScaryPotterDaveTalking()) {
            mApp.updateCrazyDave();
        }

        if (mPaused) {
            mChallenge.update();
            mCursorPreview.mVisible = false;
            mCursorObject.mVisible = false;
            return;
        }

        boolean aDisabled = !BoardInput.canInteractWithBoardButtons(this) || mIgnoreMouseUp;
        if (!mMenuButton.mBtnNoDraw) {
            mMenuButton.mDisabled = aDisabled;
        }
        mMenuButton.update();
        if (mStoreButton != null) {
            mStoreButton.mDisabled = aDisabled;
            mStoreButton.update();
        }

        mApp.mEffectSystem.update();
        mAdvice.update();
        updateTutorial();

        if (mCobCannonCursorDelayCounter > 0) {
            mCobCannonCursorDelayCounter--;
        }
        if (mOutOfMoneyCounter > 0) {
            mOutOfMoneyCounter--;
        }
        if (mShakeCounter > 0) {
            mShakeCounter--;
            if (mShakeCounter == 0) {
                mX = 0;
                mY = 0;
            } else {
                if (Common.rand(3) == 0) {
                    mShakeAmountX = -mShakeAmountX;
                }
                mX = PvzpCommon.pvzpAnimateCurve(12, 0, mShakeCounter, 0, mShakeAmountX, PvzpCurves.CURVE_BOUNCE);
                mY = PvzpCommon.pvzpAnimateCurve(12, 0, mShakeCounter, 0, mShakeAmountY, PvzpCurves.CURVE_BOUNCE);
            }
        }
        if (mCoinBankFadeCount > 0 && mApp.getDialog(Dialogs.DIALOG_PURCHASE_PACKET_SLOT) == null) {
            mCoinBankFadeCount--;
        }
        updateLayers();

        if (mTimeStopCounter > 0) {
            return;
        }

        mEffectCounter++;
        if (stageHasPool() && mIceTrapCounter == 0 && mApp.mGameScene != GameScenes.SCENE_ZOMBIES_WON && !mCutScene.isSurvivalRepick()) {
            mApp.mPoolEffect.mPoolCounter++;
        }
        if (mBackground == BackgroundType.BACKGROUND_3_POOL && mPoolSparklyParticleID == ParticleSystemID.PARTICLESYSTEMID_NULL) {
            int aRenderPosition = makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, 2, 0);
            PvzpParticleSystem aPoolParticle = mApp.addPvzpParticle(450, 295, aRenderPosition, ParticleEffect.PARTICLE_POOL_SPARKLY);
            mPoolSparklyParticleID = mApp.particleGetID(aPoolParticle);
        }

        updateGridItems();
        updateFwoosh();
        updateGame();
        updateFog();
        mChallenge.update();
        updateLevelEndSequence();
        mPrevMouseX = mApp.mWidgetManager.mLastMouseX;
        mPrevMouseY = mApp.mWidgetManager.mLastMouseY;
    }

    // Board.cpp:5793
    public void updateLayers() {
        if (mWidgetManager != null) {
            mWidgetManager.markAllDirty();

            for (Widget aDialog : mApp.mDialogList) {
                mWidgetManager.bringToFront(aDialog);
                aDialog.markDirty();
            }
        }
    }

    // Board.cpp:5808
    public boolean rowCanHaveZombies(int theRow) {
        if (theRow < 0 || theRow >= MAX_GRID_SIZE_Y) {
            return false;
        }

        return (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RESODDED && theRow <= 4) || mPlantRow[theRow] != PlantRowType.PLANTROW_DIRT;
    }

    // Board.cpp:7224
    public void clearFogAroundPlant(Plant thePlant, int theSize) {
        int aFogFadeOutSpeed = 6;
        if (mFogBlownCountDown > 0 && mFogBlownCountDown < 2000) {
            aFogFadeOutSpeed = 2;
        } else if (mFogBlownCountDown > 0) {
            aFogFadeOutSpeed = 40;
        }

        int aLeft = leftFogColumn();
        int aFogOffsetX = (int) (mFogOffset + 50) / 100;
        int aStartX = thePlant.mPlantCol - theSize - aFogOffsetX;
        int aEndX = thePlant.mPlantCol + theSize - aFogOffsetX;
        aStartX = Math.max(aStartX, aLeft);
        aEndX = Math.min(aEndX, MAX_GRID_SIZE_X - 1);

        int aStartY = thePlant.mRow - theSize;
        int aEndY = thePlant.mRow + theSize;
        aStartY = Math.max(aStartY, 0);
        aEndY = Math.min(aEndY, MAX_GRID_SIZE_Y);

        for (int x = aStartX; x <= aEndX; x++) {
            for (int y = aStartY; y <= aEndY; y++) {
                int aDistX = Math.abs(x + aFogOffsetX - thePlant.mPlantCol);
                int aDistY = Math.abs(y - thePlant.mRow);
                if (theSize == 4) {
                    if (aDistX > 3 || aDistY > 2) {
                        continue;
                    }
                    if (aDistX + aDistY == 5) {
                        continue;
                    }
                } else if (aDistX + aDistY > theSize) {
                    continue;
                }

                mGridCelFog[x][y] = Math.max(mGridCelFog[x][y] - aFogFadeOutSpeed, 0);
            }
        }
    }

    // Board.cpp:7276
    public void updateFog() {
        if (!stageHasFog()) {
            return;
        }

        int aFogFadeInSpeed = 3;
        if (mFogBlownCountDown > 0 && mFogBlownCountDown < 2000) {
            aFogFadeInSpeed = 1;
        } else if (mFogBlownCountDown > 0) {
            aFogFadeInSpeed = 20;
        }

        int aLeft = leftFogColumn();
        for (int x = aLeft; x < MAX_GRID_SIZE_X; x++) {
            for (int y = 0; y < MAX_GRID_SIZE_Y + 1; y++) {
                int aFogMax = x == aLeft ? 200 : 255;
                mGridCelFog[x][y] = Math.min(mGridCelFog[x][y] + aFogFadeInSpeed, aFogMax);
            }
        }

        for (Plant aPlant : mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.notOnGround()) {
                continue;
            }

            if (aPlant.mSeedType == SeedType.SEED_PLANTERN) {
                clearFogAroundPlant(aPlant, 4);
            } else if (aPlant.mSeedType == SeedType.SEED_TORCHWOOD) {
                clearFogAroundPlant(aPlant, 1);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:7470-7546 — cheat mode toggles
    // ---------------------------------------------------------------------------------------

    // Board.cpp:7470
    public void setMustacheMode(boolean theEnableMustache) {
        mApp.playFoley(FoleyType.FOLEY_POLEVAULT);
        mMustacheMode = theEnableMustache;
        mApp.mMustacheMode = theEnableMustache;

        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            aZombie.enableMustache(theEnableMustache);
        }
    }

    // Board.cpp:7484
    public void setFutureMode(boolean theEnableFuture) {
        mApp.playSample(Resources.SOUND_BOING_ID);
        mFutureMode = theEnableFuture;
        mApp.mFutureMode = theEnableFuture;

        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            aZombie.enableFuture(theEnableFuture);
        }
    }

    // Board.cpp:7498
    public void setPinataMode(boolean theEnablePinata) {
        mApp.playFoley(FoleyType.FOLEY_JUICY);
        mPinataMode = theEnablePinata;
        mApp.mPinataMode = theEnablePinata;
    }

    // Board.cpp:7505
    public void setDanceMode(boolean theEnableDance) {
        mApp.playFoley(FoleyType.FOLEY_DANCER);
        mDanceMode = theEnableDance;
        mApp.mDanceMode = theEnableDance;

        for (Zombie aZombie : mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            aZombie.enableDance();
        }
    }

    // Board.cpp:7519
    public void setSuperMowerMode(boolean theEnableSuperMower) {
        mApp.playFoley(FoleyType.FOLEY_ZAMBONI);
        mSuperMowerMode = theEnableSuperMower;
        mApp.mSuperMowerMode = theEnableSuperMower;

        for (LawnMower aLawnMower : mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            aLawnMower.enableSuperMower(theEnableSuperMower);
        }
    }

    // Board.cpp:7533
    public void setDaisyMode(boolean theEnableDaisy) {
        mApp.playSample(Resources.SOUND_LOADINGBAR_FLOWER_ID);
        mDaisyMode = theEnableDaisy;
        mApp.mDaisyMode = theEnableDaisy;
    }

    // Board.cpp:7540
    public void setSukhbirMode(boolean theEnableSukhbir) {
        mApp.playSample(Resources.SOUND_SUKHBIR_ID);
        mSukhbirMode = theEnableSukhbir;
        mApp.mSukhbirMode = theEnableSukhbir;
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:2597-2643 — zombie allocation
    // ---------------------------------------------------------------------------------------

    // Board.cpp:2597
    public boolean canAddBobSled() {
        return BoardWaves.canAddBobSled(this);
    }

    // Board.cpp:2609
    public Zombie addZombieInRow(int theZombieType, int theRow, int theFromWave) {
        if (mZombies.mSize >= mZombies.mMaxSize - 1) {
            Common.logInfoLn("Too many zombies!!");
            return null;
        }

        if (LawnHooksRegistry.dispatcher().onZombieSpawn(this, theZombieType, theRow, theFromWave >= 0)) {
            return null;
        }

        if (theZombieType == ZombieType.ZOMBIE_YETI) {
            if (mApp.isAdventureMode() && mLevel == 40 && theFromWave >= 0) {
                mApp.giveAchievement(AchievementId.Zombologist, true);
            }
        }

        boolean aVariant = Common.rand(5) == 0;
        Zombie aZombie = mZombies.dataArrayAlloc();
        aZombie.zombieInitialize(theRow, theZombieType, aVariant, null, theFromWave);
        if (theZombieType == ZombieType.ZOMBIE_BOBSLED && aZombie.isOnBoard()) {
            for (int _i = 0; _i < 3; _i++) {
                mZombies.dataArrayAlloc().zombieInitialize(theRow, ZombieType.ZOMBIE_BOBSLED, false, aZombie, theFromWave);
            }
        }
        return aZombie;
    }

    // Board.cpp:2635
    public Zombie addZombie(int theZombieType, int theFromWave) {
        return addZombieInRow(theZombieType, pickRowForNewZombie(theZombieType), theFromWave);
    }

    // Board.cpp:2546
    public int pickRowForNewZombie(int theZombieType) {
        return BoardWaves.pickRowForNewZombie(this, theZombieType);
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:2399-2546, 4710-4950 — wave composition (BoardWaves)
    // ---------------------------------------------------------------------------------------

    // Board.cpp:2399
    public int pickZombieType(int theZombiePoints, int theWaveIndex, ZombiePicker theZombiePicker) {
        return BoardWaves.pickZombieType(this, theZombiePoints, theWaveIndex, theZombiePicker);
    }

    // Board.cpp:2371
    public int pickGraveRisingZombieType() {
        return BoardWaves.pickGraveRisingZombieType(this);
    }

    // Board.cpp:2491
    public boolean rowCanHaveZombieType(int theRow, int theZombieType) {
        return BoardWaves.rowCanHaveZombieType(this, theRow, theZombieType);
    }

    // Board.cpp:2486
    public static boolean isZombieTypePoolOnly(int theZombieType) {
        return BoardWaves.isZombieTypePoolOnly(theZombieType);
    }

    // Board.cpp:9496
    public static boolean isZombieTypeSpawnedOnly(int theZombieType) {
        return BoardWaves.isZombieTypeSpawnedOnly(theZombieType);
    }

    // Board.cpp:9480
    public int numberZombiesInWave(int theWaveIndex) {
        return BoardWaves.numberZombiesInWave(this, theWaveIndex);
    }

    // Board.cpp:4879
    public int totalZombiesHealthInWave(int theWaveIndex) {
        return BoardWaves.totalZombiesHealthInWave(this, theWaveIndex);
    }

    // Board.cpp:4895
    public void spawnZombieWave() {
        BoardWaves.spawnZombieWave(this);
    }

    // Board.cpp:4710
    public void spawnZombiesFromPool() {
        BoardWaves.spawnZombiesFromPool(this);
    }

    // Board.cpp:4794
    public void spawnZombiesFromSky() {
        BoardWaves.spawnZombiesFromSky(this);
    }

    // Board.cpp:4832
    public void spawnZombiesFromGraves() {
        BoardWaves.spawnZombiesFromGraves(this);
    }

    // Board.cpp:4765
    public void setupBungeeDrop(BungeeDropGrid theBungeeDropGrid) {
        BoardWaves.setupBungeeDrop(this, theBungeeDropGrid);
    }

    // Board.cpp:4782
    public void bungeeDropZombie(BungeeDropGrid theBungeeDropGrid, int theZombieType) {
        BoardWaves.bungeeDropZombie(this, theBungeeDropGrid, theZombieType);
    }

    // Board.cpp:4671
    public int getGraveStonesCount() {
        return BoardWaves.getGraveStonesCount(this);
    }

    // Board.cpp:4688
    public void pickSpecialGraveStone() {
        GridItem[] aPicks = new GridItem[MAX_GRAVE_STONES];
        int aPickCount = 0;
        for (GridItem aGridItem : mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                aPicks[aPickCount] = aGridItem;
                aPickCount++;
            }
        }

        if (aPickCount > 0) {
            PvzpCommon.pvzpPickFromArray(aPicks, aPickCount).mGridItemState = GridItemState.GRIDITEM_STATE_GRAVESTONE_SPECIAL;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:2902-4641, 7672-8431 — mouse / keyboard routing (BoardInput)
    // ---------------------------------------------------------------------------------------

    // Board.cpp:2902
    public void updateCursor(HitResult theHitResult) {
        BoardInput.updateCursor(this, theHitResult);
    }

    /** Java-only default-argument wrapper of Board.cpp:2902 ({@code theHitResult = nullptr}). */
    public void updateCursor() {
        updateCursor(null);
    }

    // Board.cpp:3003
    @Override
    public void mouseMove(int x, int y) {
        BoardInput.mouseMove(this, x, y);
    }

    // Board.cpp:3009
    @Override
    public void mouseDrag(int x, int y) {
        BoardInput.mouseDrag(this, x, y);
    }

    // Board.cpp:3015
    public Zombie zombieHitTest(int theMouseX, int theMouseY) {
        return BoardInput.zombieHitTest(this, theMouseX, theMouseY);
    }

    // Board.cpp:3039
    public boolean isPlantInGoldWateringCanRange(int theMouseX, int theMouseY, Plant thePlant) {
        return BoardInput.isPlantInGoldWateringCanRange(this, theMouseX, theMouseY, thePlant);
    }

    // Board.cpp:3062
    public void highlightPlantsForMouse(int theMouseX, int theMouseY, HitResult theHitResult) {
        BoardInput.highlightPlantsForMouse(this, theMouseX, theMouseY, theHitResult);
    }

    // Board.cpp:3099
    public void updateMousePosition() {
        BoardInput.updateMousePosition(this);
    }

    // Board.cpp:3195
    public void updateToolTip(HitResult theHitResult) {
        BoardInput.updateToolTip(this, theHitResult);
    }

    /** Java-only default-argument wrapper of Board.cpp:3195 ({@code theHitResult = nullptr}). */
    public void updateToolTip() {
        updateToolTip(null);
    }

    // Board.cpp:3564
    public void mouseDownCobcannonFire(int x, int y, int theClickCount) {
        BoardInput.mouseDownCobcannonFire(this, x, y, theClickCount);
    }

    // Board.cpp:3585
    public void mouseDownWithPlant(int x, int y, int theClickCount) {
        BoardInput.mouseDownWithPlant(this, x, y, theClickCount);
    }

    // Board.cpp:3952
    public Plant toolHitTestHelper(HitResult theHitResult) {
        return BoardInput.toolHitTestHelper(this, theHitResult);
    }

    // Board.cpp:3958
    public Plant toolHitTest(int theX, int theY) {
        return BoardInput.toolHitTest(this, theX, theY);
    }

    // Board.cpp:3969
    public void tutorialArrowShow(int theX, int theY) {
        BoardInput.tutorialArrowShow(this, theX, theY);
    }

    // Board.cpp:3976
    public void tutorialArrowRemove() {
        BoardInput.tutorialArrowRemove(this);
    }

    // Board.cpp:3982
    public void mouseDownWithTool(int x, int y, int theClickCount, int theCursorType) {
        BoardInput.mouseDownWithTool(this, x, y, theClickCount, theCursorType);
    }

    // Board.cpp:4026
    public Plant specialPlantHitTest(int x, int y) {
        return BoardInput.specialPlantHitTest(this, x, y);
    }

    // Board.cpp:4052
    public boolean mouseHitTestPlant(int x, int y, HitResult theHitResult) {
        return BoardInput.mouseHitTestPlant(this, x, y, theHitResult);
    }

    // Board.cpp:4108
    public boolean mouseHitTest(int x, int y, HitResult theHitResult) {
        return BoardInput.mouseHitTest(this, x, y, theHitResult);
    }

    // Board.cpp:4257
    public void pickUpTool(int theObjectType) {
        BoardInput.pickUpTool(this, theObjectType);
    }

    // Board.cpp:4363
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        BoardInput.mouseDown(this, x, y, theClickCount);
    }

    // Board.cpp:4497
    public void clearCursor() {
        BoardInput.clearCursor(this);
    }

    // Board.cpp:4561
    public boolean canInteractWithBoardButtons() {
        return BoardInput.canInteractWithBoardButtons(this);
    }

    // Board.cpp:4581
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
        BoardInput.mouseUp(this, x, y, theClickCount);
    }

    // Board.cpp:7547
    public void doTypingCheck(int theKey) {
        BoardInput.doTypingCheck(this, theKey);
    }

    // Board.cpp:7628
    @Override
    public void keyDown(int theKey) {
        BoardInput.keyDown(this, theKey);
    }

    // Board.cpp:7672
    @Override
    public void keyChar(char theChar) {
        BoardInput.keyChar(this, theChar);
    }

    // Board.cpp:7703 (Board.h:274 declares an empty override)
    @Override
    public void keyUp(int theKey) {
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:5808-7469 — draw pipeline (BoardRender)
    // ---------------------------------------------------------------------------------------

    // Board.cpp:7438
    @Override
    public void draw(Graphics g) {
        BoardRender.draw(this, g);
    }

    // Board.cpp:6074
    public void drawGameObjects(Graphics g) {
        BoardRender.drawGameObjects(this, g);
    }

    // Board.cpp:5850
    public void drawBackdrop(Graphics g) {
        BoardRender.drawBackdrop(this, g);
    }

    // Board.cpp:6632
    public void drawLevel(Graphics g) {
        BoardRender.drawLevel(this, g);
    }

    // Board.cpp:6855
    public void drawShovel(Graphics g) {
        BoardRender.drawShovel(this, g);
    }

    // Board.cpp:6883
    public void drawDebugText(Graphics g) {
        BoardRender.drawDebugText(this, g);
    }

    // Board.cpp:7026
    public void drawDebugObjectRects(Graphics g) {
        BoardRender.drawDebugObjectRects(this, g);
    }

    // Board.cpp:7094
    public void drawFadeOut(Graphics g) {
        BoardRender.drawFadeOut(this, g);
    }

    // Board.cpp:7111
    public void drawTopRightUI(Graphics g) {
        BoardRender.drawTopRightUI(this, g);
    }

    // Board.cpp:7147
    public void drawUIBottom(Graphics g) {
        BoardRender.drawUIBottom(this, g);
    }

    // Board.cpp:7197
    public void drawUICoinBank(Graphics g) {
        BoardRender.drawUICoinBank(this, g);
    }

    // Board.cpp:6527
    public void drawProgressMeter(Graphics g) {
        BoardRender.drawProgressMeter(this, g);
    }

    // Board.cpp:6488
    public boolean hasProgressMeter() {
        return BoardRender.hasProgressMeter(this);
    }

    // Board.cpp:6510
    public boolean progressMeterHasFlags() {
        return BoardRender.progressMeterHasFlags(this);
    }

    // Board.cpp:6606
    public void drawHouseDoorBottom(Graphics g) {
        BoardRender.drawHouseDoorBottom(this, g);
    }

    // Board.cpp:6618
    public void drawHouseDoorTop(Graphics g) {
        BoardRender.drawHouseDoorTop(this, g);
    }

    // Board.cpp:7320
    public void drawFog(Graphics g) {
        BoardRender.drawFog(this, g);
    }

    // Board.cpp:5821
    public void drawIce(Graphics g, int theGridY) {
        BoardRender.drawIce(this, g, theGridY);
    }

    // Board.cpp:5816
    public int getIceZPos(int theRow) {
        return BoardRender.getIceZPos(this, theRow);
    }

    // Board.cpp:5936
    public int addBossRenderItem(RenderItem[] theRenderList, int theCurRenderItem, Zombie theBossZombie) {
        return BoardRender.addBossRenderItem(this, theRenderList, theCurRenderItem, theBossZombie);
    }

    // Board.cpp:6680
    public void drawZenWheelBarrowButton(Graphics g, int theOffsetY) {
        BoardRender.drawZenWheelBarrowButton(this, g, theOffsetY);
    }

    // Board.cpp:6715
    public void drawZenButtons(Graphics g) {
        BoardRender.drawZenButtons(this, g);
    }

    // Board.cpp:7373
    public void drawUITop(Graphics g) {
        BoardRender.drawUITop(this, g);
    }

    // ---------------------------------------------------------------------------------------
    // Board.h:254-262 — ButtonListener (all empty upstream) and the "not found" inline helper
    // ---------------------------------------------------------------------------------------

    // Board.h:254
    @Override
    public void buttonPress(int theId) {
    }

    // Board.h:255
    @Override
    public void buttonDepress(int theId) {
    }

    // Board.h:256
    @Override
    public void buttonDownTick(int theId) {
    }

    // Board.h:257
    @Override
    public void buttonMouseEnter(int theId) {
    }

    // Board.h:258
    @Override
    public void buttonMouseLeave(int theId) {
    }

    // Board.h:259
    @Override
    public void buttonMouseMove(int theId, int theX, int theY) {
    }

    /**
     * Board.h:283 — upstream declares {@code inline bool MakeEasyZombieType() { /* not found *&#47;
     * return false; } }, i.e. a constant false.
     */
    public boolean makeEasyZombieType() {
        return false;
    }

    /** {@code std::clamp} for ints (upstream Board.cpp uses it throughout). */
    private static int clampInt(int theValue, int theMin, int theMax) {
        return theValue < theMin ? theMin : (theValue > theMax ? theMax : theValue);
    }

    // ---------------------------------------------------------------------------------------
    // Board.cpp:5926 — the render list comparator
    // ---------------------------------------------------------------------------------------

    /** Ported from src/Lawn/Board.cpp:5926 (RenderItemSortFunc): z first, then allocation order. */
    public static int compareRenderItems(RenderItem theItem1, RenderItem theItem2) {
        if (theItem1.mZPos == theItem2.mZPos) {
            return Long.compare(theItem1.mAllocId, theItem2.mAllocId);
        }
        return Integer.compare(theItem1.mZPos, theItem2.mZPos);
    }
}

/**
 * Ported from src/Lawn/Board.h:57-62 (class HitResult).
 *
 * <p>Upstream stores a {@code void*}; the Java port keeps an untyped reference (the concrete
 * pointer class is named by {@link #mObjectType}) plus the hit object type.
 */
class HitResult {
    public Object mObject;
    public int mObjectType;
}

/**
 * Ported from src/Lawn/Board.h:64-85 (class RenderItem).
 *
 * <p>The upstream union of payload pointers is flattened into one field per pointer type (Java has
 * no unions); {@link #mParticleSytem} keeps the upstream spelling and is typed {@code Object}
 * because the particle subsystem type is not part of this port's fixed surface.
 */
class RenderItem {
    public int mRenderObjectType;
    public int mZPos;
    /** Monotonic allocation id used as the deterministic replacement for the C++ pointer tie-break. */
    public long mAllocId;
    public Object mGameObject;
    public Plant mPlant;
    public Zombie mZombie;
    public Coin mCoin;
    public Projectile mProjectile;
    public CursorPreview mCursorPreview;
    public Object mParticleSytem;
    public Reanimation mReanimation;
    public GridItem mGridItem;
    public LawnMower mMower;
    public int mBossPart;
    public int mBoardGridY;
}

/** Ported from src/Lawn/Board.h:88-95 (struct ZombiePicker). */
class ZombiePicker {
    public int mZombieCount;
    public int mZombiePoints;
    public int[] mZombieTypeCount = new int[ZombieType.MAX_ZOMBIE_TYPES];
    public int[] mAllWavesZombieTypeCount = new int[ZombieType.MAX_ZOMBIE_TYPES];
}

/** Ported from src/Lawn/Board.h:118-123 (struct PlantsOnLawn). */
class PlantsOnLawn {
    public Plant mUnderPlant;
    public Plant mPumpkinPlant;
    public Plant mFlyingPlant;
    public Plant mNormalPlant;
}

/** Ported from src/Lawn/Board.h:125-130 (struct BungeeDropGrid). */
class BungeeDropGrid {
    public PvzpWeightedGridArray[] mGridArray = new PvzpWeightedGridArray[Board.MAX_GRID_SIZE_X * Board.MAX_GRID_SIZE_Y];
    public int mGridArrayCount;
}
