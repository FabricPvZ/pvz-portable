/*
 * Ported from src/Lawn/Widget/GameSelector.h:50-152 and src/Lawn/Widget/GameSelector.cpp:49-1388.
 *
 * The main menu selector: the animated sign board, the 13 hit-tested menu buttons, the level
 * picker overlay, the trophy, the zombie hand start-up animation and the profile synchronisation.
 *
 * Porting notes:
 *  - Public fields keep the upstream `m`-prefix and names; methods are camelCase of the upstream
 *    PascalCase names (API contract §6).
 *  - `std::unique_ptr<ToolTipWidget>` / `std::unique_ptr<AchievementsScreen>` become plain fields;
 *    the buttons are non-owning references owned by the widget container (`removeAllWidgets`).
 *  - `std::unique_ptr<ZombatarWidget>` becomes a plain field of the concrete {@link ZombatarWidget}
 *    type (it is added to the widget manager and slid with the selector exactly like upstream).
 *  - The pure unlock / first-time-visibility decision of `SyncProfile` (`.cpp:476-542`) is factored
 *    into the static {@link #computeSyncProfileState} so the progress-state table is testable
 *    without an app instance.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.Locale;

import cn.enaium.fabricpvz.portable.lawn.AchievementId;
import cn.enaium.fabricpvz.portable.lawn.AwardType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.lawn.ParticleSystemID;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.RenderLayer;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.StoreItem;
import cn.enaium.fabricpvz.portable.lawn.ToolTipWidget;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrack;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.DialogButton;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/GameSelector.h:50-133 and src/Lawn/Widget/GameSelector.cpp:63-1388.
 *
 * <p>The main menu ("Adventure / Minigames / Puzzle / Survival / ...") selector screen.
 */
public class GameSelector extends Widget implements ButtonListener {

    // GameSelector.h:42-47
    public static final int SELECTOR_OPEN = 0;
    public static final int SELECTOR_NEW_USER = 1;
    public static final int SELECTOR_SHOW_SIGN = 2;
    public static final int SELECTOR_IDLE = 3;

    // GameSelector.h:55-67 (private enum in upstream)
    public static final int GameSelector_Adventure = 100;
    public static final int GameSelector_Minigame = 101;
    public static final int GameSelector_Puzzle = 102;
    public static final int GameSelector_Options = 103;
    public static final int GameSelector_Help = 104;
    public static final int GameSelector_Quit = 105;
    public static final int GameSelector_ChangeUser = 106;
    public static final int GameSelector_Store = 107;
    public static final int GameSelector_Almanac = 108;
    public static final int GameSelector_ZenGarden = 109;
    public static final int GameSelector_Survival = 110;
    public static final int GameSelector_Zombatar = 111;
    public static final int GameSelector_Achievements = 112;

    // GameSelector.cpp:168 (mAchievementsButton->Resize(20, mApp->mHeight - PEDESTAL->mHeight - 35, ...))
    public static final int ACHIEVEMENTS_BUTTON_X = 20;
    public static final int ACHIEVEMENTS_BUTTON_BOTTOM_MARGIN = 35;
    // GameSelector.cpp:257 (mStoreButton->Resize(405, 484, ...))
    public static final int STORE_BUTTON_X = 405;
    public static final int STORE_BUTTON_Y = 484;
    // GameSelector.cpp:270 (mAlmanacButton->Resize(327, 428, ...))
    public static final int ALMANAC_BUTTON_X = 327;
    public static final int ALMANAC_BUTTON_Y = 428;

    /** GameSelector.cpp:49 (gFlowerCenter[3][2]). */
    public static final float[][] gFlowerCenter = {
        { 765.0f, 483.0f },
        { 663.0f, 455.0f },
        { 701.0f, 439.0f }
    };

    public LawnApp mApp;
    // Non-owning: owned by the widget container (removeAllWidgets(true)); do not delete.
    public NewLawnButton mAdventureButton;
    public NewLawnButton mMinigameButton;
    public NewLawnButton mPuzzleButton;
    public NewLawnButton mOptionsButton;
    public NewLawnButton mQuitButton;
    public NewLawnButton mHelpButton;
    public NewLawnButton mStoreButton;
    public NewLawnButton mAlmanacButton;
    public NewLawnButton mZenGardenButton;
    public NewLawnButton mSurvivalButton;
    public NewLawnButton mChangeUserButton;
    public NewLawnButton mZombatarButton; // +0xC0
    public NewLawnButton mAchievementsButton; // +0xC4
    public Widget mOverlayWidget;
    public boolean mStartingGame;
    public int mStartingGameCounter;
    public boolean mMinigamesLocked;
    public boolean mPuzzleLocked;
    public boolean mSurvivalLocked;
    public boolean mShowStartButton;
    public int mTrophyParticleID;
    public int mSelectorReanimID;
    public int[] mCloudReanimID = new int[6];
    public int[] mCloudCounter = new int[6];
    public int[] mFlowerReanimID = new int[3];
    public int mLeafReanimID;
    public int mHandReanimID;
    public int mLeafCounter;
    public int mSelectorState;
    public int mLevel;
    public boolean mLoading;
    public ToolTipWidget mToolTip;
    public boolean mHasTrophy;
    public boolean mUnlockSelectorCheat;
    public int mSlideCounter; // +0x154
    public int mStartX; // +0x158
    public int mStartY; // +0x15C
    public int mDestX; // +0x160
    public int mDestY; // +0x164
    /** +0x168. Upstream {@code std::unique_ptr<ZombatarWidget>}. */
    public ZombatarWidget mZombatarWidget;
    /** +0x16C. Upstream {@code std::unique_ptr<AchievementsWidget>}. */
    public AchievementsScreen mAchievementsWidget;

    /**
     * Result of {@link GameSelector#computeSyncProfileState}, mirroring the four fields that
     * {@code SyncProfile} assigns (GameSelector.cpp:497-529).
     */
    public static class SyncProfileState {
        public int mLevel;
        public boolean mShowStartButton;
        public boolean mMinigamesLocked;
        public boolean mPuzzleLocked;
        public boolean mSurvivalLocked;
    }

    /**
     * Pure unlock / first-time-visibility decision of {@code GameSelector::SyncProfile}
     * (GameSelector.cpp:497-529).
     *
     * @param level              {@code mPlayerInfo->GetLevel()} (ignored when {@code hasProfile} is false)
     * @param finishedAdventure  {@code mApp->HasFinishedAdventure()}
     * @param unlockedMinigames  {@code mPlayerInfo->mHasUnlockedMinigames != 0}
     * @param unlockedPuzzle     {@code mPlayerInfo->mHasUnlockedPuzzleMode != 0}
     * @param unlockedSurvival   {@code mPlayerInfo->mHasUnlockedSurvivalMode != 0}
     * @param trialLocked        {@code mApp->IsTrialStageLocked()}
     * @param hasProfile         {@code mApp->mPlayerInfo != null && !mApp->IsIceDemo()} — the guard on
     *                           the whole block at GameSelector.cpp:504
     */
    public static SyncProfileState computeSyncProfileState(int level, boolean finishedAdventure,
            boolean unlockedMinigames, boolean unlockedPuzzle, boolean unlockedSurvival, boolean trialLocked,
            boolean hasProfile) {
        SyncProfileState aState = new SyncProfileState();
        aState.mLevel = 1;
        if (hasProfile) {
            aState.mLevel = level;
        }
        aState.mShowStartButton = true;
        aState.mMinigamesLocked = true;
        aState.mPuzzleLocked = true;
        aState.mSurvivalLocked = true;
        if (hasProfile) {
            if (aState.mLevel >= 2) {
                aState.mShowStartButton = false;
            }

            if (finishedAdventure) {
                aState.mMinigamesLocked = false;
                aState.mSurvivalLocked = false;
                aState.mPuzzleLocked = false;
                aState.mShowStartButton = false;
            }

            if (unlockedMinigames) {
                aState.mMinigamesLocked = false;
            }
            if (unlockedPuzzle) {
                aState.mPuzzleLocked = false;
            }
            if (unlockedSurvival) {
                aState.mSurvivalLocked = false;
            }

            if (trialLocked) {
                aState.mPuzzleLocked = true;
                aState.mSurvivalLocked = true;
            }
        }
        return aState;
    }

    // GameSelector::GameSelector (GameSelector.cpp:63-366)
    public GameSelector(LawnApp theApp) {
        mLoadedResourceNames.add("DelayLoad_Zombatar");
        mLoadedResourceNames.add("DelayLoad_Almanac");

        for (String resource : mLoadedResourceNames) {
            ((PvzpResourceManager) theApp.mResourceManager).pvzpLoadResources(resource);
        }

        mApp = theApp;
        mLevel = 1;
        mLoading = false;
        mHasTrophy = false;
        mToolTip = new ToolTipWidget();

        mAdventureButton = GameButton.makeNewButton(
                GameSelector_Adventure,
                this,
                "",
                null,
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_BUTTON_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_HIGHLIGHT_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_HIGHLIGHT_ID));
        mAdventureButton.resize(0, 0, imageWidth(Resources.IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_BUTTON_ID), 125);
        mAdventureButton.mClip = false;
        mAdventureButton.mBtnNoDraw = true;
        mAdventureButton.mMouseVisible = false;
        mAdventureButton.mPolygonShape[0] = new SexyVector2(7.0f, 1.0f);
        mAdventureButton.mPolygonShape[1] = new SexyVector2(328.0f, 30.0f);
        mAdventureButton.mPolygonShape[2] = new SexyVector2(314.0f, 125.0f);
        mAdventureButton.mPolygonShape[3] = new SexyVector2(1.0f, 78.0f);
        mAdventureButton.mUsePolygonShape = true;

        mMinigameButton = GameButton.makeNewButton(
                GameSelector_Minigame,
                this,
                "",
                null,
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_BUTTON_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_HIGHLIGHT_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_HIGHLIGHT_ID));
        mMinigameButton.resize(0, 0, imageWidth(Resources.IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_BUTTON_ID), 130);
        mMinigameButton.mClip = false;
        mMinigameButton.mBtnNoDraw = true;
        mMinigameButton.mMouseVisible = false;
        mMinigameButton.mPolygonShape[0] = new SexyVector2(4.0f, 2.0f);
        mMinigameButton.mPolygonShape[1] = new SexyVector2(312.0f, 51.0f);
        mMinigameButton.mPolygonShape[2] = new SexyVector2(296.0f, 130.0f);
        mMinigameButton.mPolygonShape[3] = new SexyVector2(7.0f, 77.0f);
        mMinigameButton.mUsePolygonShape = true;

        mPuzzleButton = GameButton.makeNewButton(
                GameSelector_Puzzle,
                this,
                "",
                null,
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_BUTTON_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_HIGHLIGHT_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_HIGHLIGHT_ID));
        mPuzzleButton.resize(0, 0, imageWidth(Resources.IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_BUTTON_ID), 121);
        mPuzzleButton.mClip = false;
        mPuzzleButton.mBtnNoDraw = true;
        mPuzzleButton.mMouseVisible = false;
        mPuzzleButton.mPolygonShape[0] = new SexyVector2(2.0f, 0.0f);
        mPuzzleButton.mPolygonShape[1] = new SexyVector2(281.0f, 55.0f);
        mPuzzleButton.mPolygonShape[2] = new SexyVector2(268.0f, 121.0f);
        mPuzzleButton.mPolygonShape[3] = new SexyVector2(3.0f, 60.0f);
        mPuzzleButton.mUsePolygonShape = true;

        mSurvivalButton = GameButton.makeNewButton(
                GameSelector_Survival,
                this,
                "",
                null,
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_BUTTON_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_HIGHLIGHT_ID),
                image(Resources.IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_HIGHLIGHT_ID));
        mSurvivalButton.resize(0, 0, imageWidth(Resources.IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_BUTTON_ID), 124);
        mSurvivalButton.mClip = false;
        mSurvivalButton.mBtnNoDraw = true;
        mSurvivalButton.mMouseVisible = false;
        mSurvivalButton.mPolygonShape[0] = new SexyVector2(7.0f, 1.0f);
        mSurvivalButton.mPolygonShape[1] = new SexyVector2(267.0f, 62.0f);
        mSurvivalButton.mPolygonShape[2] = new SexyVector2(257.0f, 124.0f);
        mSurvivalButton.mPolygonShape[3] = new SexyVector2(7.0f, 57.0f);
        mSurvivalButton.mUsePolygonShape = true;

        mZombatarButton = GameButton.makeNewButton(
                GameSelector_Zombatar,
                this,
                "",
                null,
                image(Resources.IMAGE_BLANK_ID),
                image(Resources.IMAGE_BLANK_ID),
                image(Resources.IMAGE_BLANK_ID));
        mZombatarButton.resize(0, 0, imageWidth(Resources.IMAGE_REANIM_SELECTORSCREEN_WOODSIGN3_PRESS_ID),
                imageHeight(Resources.IMAGE_REANIM_SELECTORSCREEN_WOODSIGN3_PRESS_ID));
        mZombatarButton.mClip = false;
        mZombatarButton.mBtnNoDraw = true;
        mZombatarButton.mMouseVisible = false;

        mAchievementsButton = GameButton.makeNewButton(
                GameSelector_Achievements,
                this,
                "",
                null,
                image(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_ID),
                image(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_PRESS_ID),
                image(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_PRESS_ID));
        mAchievementsButton.resize(ACHIEVEMENTS_BUTTON_X,
                mApp.mHeight - imageHeight(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_ID)
                        - ACHIEVEMENTS_BUTTON_BOTTOM_MARGIN,
                imageWidth(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_ID),
                imageHeight(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_ID));
        mAchievementsButton.mClip = false;
        mAchievementsButton.mBtnNoDraw = mHasTrophy;
        mAchievementsButton.mMouseVisible = false;

        mZenGardenButton = GameButton.makeNewButton(
                GameSelector_ZenGarden,
                this,
                "",
                null,
                image(Resources.IMAGE_SELECTORSCREEN_ZENGARDEN_ID),
                image(Resources.IMAGE_SELECTORSCREEN_ZENGARDENHIGHLIGHT_ID),
                image(Resources.IMAGE_SELECTORSCREEN_ZENGARDENHIGHLIGHT_ID));
        mZenGardenButton.resize(0, 0, 130, 130);
        mZenGardenButton.mMouseVisible = false;
        mZenGardenButton.mClip = false;

        mOptionsButton = GameButton.makeNewButton(
                GameSelector_Options,
                this,
                "",
                null,
                image(Resources.IMAGE_SELECTORSCREEN_OPTIONS1_ID),
                image(Resources.IMAGE_SELECTORSCREEN_OPTIONS2_ID),
                image(Resources.IMAGE_SELECTORSCREEN_OPTIONS2_ID));
        mOptionsButton.resize(0, 0, imageWidth(Resources.IMAGE_SELECTORSCREEN_OPTIONS1_ID),
                imageHeight(Resources.IMAGE_SELECTORSCREEN_OPTIONS1_ID) + 23);
        mOptionsButton.mClip = false; // fixes drawing issues
        mOptionsButton.mBtnNoDraw = true;
        mOptionsButton.mMouseVisible = false;
        mOptionsButton.mButtonOffsetY = 15;

        mHelpButton = GameButton.makeNewButton(
                GameSelector_Help,
                this,
                "",
                null,
                image(Resources.IMAGE_SELECTORSCREEN_HELP1_ID),
                image(Resources.IMAGE_SELECTORSCREEN_HELP2_ID),
                image(Resources.IMAGE_SELECTORSCREEN_HELP2_ID));
        mHelpButton.resize(0, 0, imageWidth(Resources.IMAGE_SELECTORSCREEN_HELP1_ID),
                imageHeight(Resources.IMAGE_SELECTORSCREEN_HELP1_ID) + 33);
        mHelpButton.mClip = false; // fixes drawing issues
        mHelpButton.mBtnNoDraw = true;
        mHelpButton.mMouseVisible = false;
        mHelpButton.mButtonOffsetY = 30;

        mQuitButton = GameButton.makeNewButton(
                GameSelector_Quit,
                this,
                "",
                null,
                image(Resources.IMAGE_SELECTORSCREEN_QUIT1_ID),
                image(Resources.IMAGE_SELECTORSCREEN_QUIT2_ID),
                image(Resources.IMAGE_SELECTORSCREEN_QUIT2_ID));
        mQuitButton.resize(0, 0, imageWidth(Resources.IMAGE_SELECTORSCREEN_QUIT1_ID) + 10,
                imageHeight(Resources.IMAGE_SELECTORSCREEN_QUIT1_ID) + 10);
        mQuitButton.mClip = false; // fixes drawing issues
        mQuitButton.mBtnNoDraw = true;
        mQuitButton.mMouseVisible = false;
        mQuitButton.mButtonOffsetX = 5;
        mQuitButton.mButtonOffsetY = 5;

        mChangeUserButton = GameButton.makeNewButton(
                GameSelector_ChangeUser,
                this,
                "",
                null,
                image(Resources.IMAGE_BLANK_ID),
                image(Resources.IMAGE_BLANK_ID),
                image(Resources.IMAGE_BLANK_ID));
        mChangeUserButton.resize(0, 0, 250, 30);
        mChangeUserButton.mBtnNoDraw = true;
        mChangeUserButton.mMouseVisible = false;

        mOverlayWidget = new GameSelectorOverlay(this);
        mOverlayWidget.resize(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);

        mStoreButton = GameButton.makeNewButton(
                GameSelector_Store,
                this,
                "",
                null,
                image(Resources.IMAGE_SELECTORSCREEN_STORE_ID),
                image(Resources.IMAGE_SELECTORSCREEN_STOREHIGHLIGHT_ID),
                image(Resources.IMAGE_SELECTORSCREEN_STOREHIGHLIGHT_ID));
        mStoreButton.resize(STORE_BUTTON_X, STORE_BUTTON_Y, imageWidth(Resources.IMAGE_SELECTORSCREEN_STORE_ID),
                imageHeight(Resources.IMAGE_SELECTORSCREEN_STORE_ID));
        mStoreButton.mClip = false; // fixes drawing issues
        mStoreButton.mMouseVisible = false;

        mAlmanacButton = GameButton.makeNewButton(
                GameSelector_Almanac,
                this,
                "",
                null,
                image(Resources.IMAGE_SELECTORSCREEN_ALMANAC_ID),
                image(Resources.IMAGE_SELECTORSCREEN_ALMANACHIGHLIGHT_ID),
                image(Resources.IMAGE_SELECTORSCREEN_ALMANACHIGHLIGHT_ID));
        mAlmanacButton.resize(ALMANAC_BUTTON_X, ALMANAC_BUTTON_Y, imageWidth(Resources.IMAGE_SELECTORSCREEN_ALMANAC_ID),
                imageHeight(Resources.IMAGE_SELECTORSCREEN_ALMANAC_ID));
        mAlmanacButton.mClip = false; // fixes drawing issues
        mAlmanacButton.mMouseVisible = false;

        mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME);

        mStartingGame = false;
        mStartingGameCounter = 0;
        mMinigamesLocked = false;
        mPuzzleLocked = false;
        mSurvivalLocked = false;
        mUnlockSelectorCheat = false;
        mTrophyParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        mShowStartButton = false;

        Reanimation aSelectorReanim = mApp.addReanimation(0.5f, 0.5f, 0, ReanimationType.REANIM_SELECTOR_SCREEN);
        aSelectorReanim.playReanim("anim_open", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 30.0f);
        aSelectorReanim.assignRenderGroupToPrefix("flower", Reanimation.RENDER_GROUP_HIDDEN);
        aSelectorReanim.assignRenderGroupToPrefix("leaf", Reanimation.RENDER_GROUP_HIDDEN);
        aSelectorReanim.assignRenderGroupToTrack("SelectorScreen_BG", 1);
        mSelectorReanimID = mApp.reanimationGetID(aSelectorReanim);
        mSelectorState = SELECTOR_OPEN;
        int[] aFrameStartCount = new int[2];
        aSelectorReanim.getFramesForLayer("anim_sign", aFrameStartCount);
        aSelectorReanim.mFrameBasePose = aFrameStartCount[0] + aFrameStartCount[1] - 1;

        for (int i = 0; i < 6; i++) {
            Reanimation aCloudReanim = mApp.addReanimation(0.5f, 0.5f, 0, ReanimationType.REANIM_SELECTOR_SCREEN);
            String aAnimName = String.format(Locale.ROOT, "anim_cloud%d", i > 1 ? i + 2 : i + 1);
            aCloudReanim.playReanim(aAnimName, ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 0.0f);
            mCloudReanimID[i] = mApp.reanimationGetID(aCloudReanim);
            mCloudCounter[i] = PvzpCommon.randRangeInt(-6000, 2000);
            if (mCloudCounter[i] < 0) {
                aCloudReanim.mAnimTime = -mCloudCounter[i] / 6000.0f;
                aCloudReanim.mAnimRate = 0.5f;
                mCloudCounter[i] = 0;
            } else {
                aCloudReanim.mAnimRate = 0.0f;
            }
        }

        for (int i = 0; i < 3; i++) {
            Reanimation aFlowerReanim = mApp.addReanimation(0.5f, 0.5f, 0, ReanimationType.REANIM_SELECTOR_SCREEN);
            String aAnimName = String.format(Locale.ROOT, "anim_flower%d", i + 1);
            aFlowerReanim.playReanim(aAnimName, ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 0.0f);
            aFlowerReanim.mAnimRate = 0.0f;
            aFlowerReanim.attachToAnotherReanimation(aSelectorReanim, "SelectorScreen_BG_Right");
            aFlowerReanim.mIsAttachment = false;
            mFlowerReanimID[i] = mApp.reanimationGetID(aFlowerReanim);
        }

        Reanimation aLeafReanim = mApp.addReanimation(0.5f, 0.5f, 0, ReanimationType.REANIM_SELECTOR_SCREEN);
        aLeafReanim.playReanim("anim_grass", ReanimLoopType.REANIM_LOOP, 0, 6.0f);
        aLeafReanim.mAnimRate = 0.0f;
        mLeafReanimID = mApp.reanimationGetID(aLeafReanim);
        mLeafCounter = 50;

        syncProfile(false);
        mApp.playSample(Resources.SOUND_ROLL_IN_ID);

        mSlideCounter = 0;
        mStartX = 0;
        mStartY = 0;
        mDestX = 0;
        mDestY = 0;
        mZombatarWidget = new ZombatarWidget(this);
        mAchievementsWidget = new AchievementsScreen(mApp);
        mAchievementsWidget.move(0, mApp.mHeight);

        // Add as children in z-order (bottom to top).
        addWidget(mAchievementsButton);
        addWidget(mZombatarButton);
        addWidget(mChangeUserButton);
        addWidget(mSurvivalButton);
        addWidget(mZenGardenButton);
        addWidget(mPuzzleButton);
        addWidget(mMinigameButton);
        addWidget(mAdventureButton);
        addWidget(mOptionsButton);
        addWidget(mQuitButton);
        addWidget(mHelpButton);
        addWidget(mStoreButton);
        addWidget(mAlmanacButton);
        addWidget(mOverlayWidget);
    }

    /**
     * The selector's main reanimation, or {@code null} when it has already been released.
     *
     * <p>The menu keeps updating for a few frames after a level starts (it is still in the widget tree
     * during the level intro) while the level's start-up releases the effect-system reanimations, so its
     * handle can be stale. {@code reanimationGet} throws for a stale handle; skipping the animation is
     * what the player sees anyway. Found by the real-click playthrough (docs/VERIFICATION.md §14).
     */
    private Reanimation selectorReanim() {
        return mApp.reanimationTryToGet(mSelectorReanimID);
    }

    private Image image(int theId) {
        return mApp.getImage(theId);
    }

    // Sizing helpers: upstream dereferences the resource image directly; without retail assets the
    // lookups are null, so the size falls back to 0 (same tolerance as AchievementsScreen.cpp:66-77).
    private int imageWidth(int theId) {
        Image anImage = mApp.getImage(theId);
        return anImage == null ? 0 : anImage.mWidth;
    }

    private int imageHeight(int theId) {
        Image anImage = mApp.getImage(theId);
        return anImage == null ? 0 : anImage.mHeight;
    }

    // GameSelector::~GameSelector (GameSelector.cpp:368) — the base destructor runs afterwards.
    @Override
    public void dispose() {
        removeAllWidgets(true, false);
        super.dispose();
    }

    // GameSelector::SyncButtons (GameSelector.cpp:373-467)
    public void syncButtons() {
        boolean aAlmanacAvailable = mApp.canShowAlmanac() || mUnlockSelectorCheat;
        boolean aStoreOpen = mApp.canShowStore() || mUnlockSelectorCheat;
        boolean aZenGardenOpen = mApp.canShowZenGarden() || mUnlockSelectorCheat;

        mAlmanacButton.mDisabled = !aAlmanacAvailable;
        mAlmanacButton.mVisible = aAlmanacAvailable;
        mStoreButton.mDisabled = !aStoreOpen;
        mStoreButton.mVisible = aStoreOpen;
        mZombatarButton.mDisabled = false;
        mZombatarButton.mVisible = true;

        Reanimation aSelectorReanim = selectorReanim();
        if (aSelectorReanim == null) {
            return;
        }
        if (aAlmanacAvailable) {
            aSelectorReanim.assignRenderGroupToPrefix("almanac_key_shadow", Reanimation.RENDER_GROUP_NORMAL);
            if (aStoreOpen) {
                aSelectorReanim.setImageOverride("almanac_key_shadow", null);
            } else {
                aSelectorReanim.setImageOverride("almanac_key_shadow",
                        image(Resources.IMAGE_REANIM_SELECTORSCREEN_ALMANAC_SHADOW_ID));
            }
        } else if (aStoreOpen) {
            aSelectorReanim.assignRenderGroupToPrefix("almanac_key_shadow", Reanimation.RENDER_GROUP_NORMAL);
            aSelectorReanim.setImageOverride("almanac_key_shadow",
                    image(Resources.IMAGE_REANIM_SELECTORSCREEN_KEY_SHADOW_ID));
        } else {
            aSelectorReanim.assignRenderGroupToPrefix("almanac_key_shadow", Reanimation.RENDER_GROUP_HIDDEN);
        }

        mZenGardenButton.mDisabled = !aZenGardenOpen;
        mZenGardenButton.mVisible = aZenGardenOpen;

        // already assigned in the constructor; reassigning them here breaks the hover highlight
        if (mMinigamesLocked) {
            mMinigameButton.setBkgColor(new Color(128, 128, 128));
        } else {
            mMinigameButton.setBkgColor(Color.White);
        }
        if (mPuzzleLocked) {
            mPuzzleButton.setBkgColor(new Color(128, 128, 128));
        } else {
            mPuzzleButton.setBkgColor(Color.White);
        }
        if (mSurvivalLocked) {
            mSurvivalButton.setBkgColor(new Color(128, 128, 128));
        } else {
            mSurvivalButton.setBkgColor(Color.White);
        }

        ReanimatorTrackInstance aMinigameTrack = aSelectorReanim.getTrackInstanceByName("SelectorScreen_Survival_button");
        ReanimatorTrackInstance aPuzzleTrack = aSelectorReanim.getTrackInstanceByName("SelectorScreen_Challenges_button");
        ReanimatorTrackInstance aSurvivalTrack = aSelectorReanim.getTrackInstanceByName("SelectorScreen_ZenGarden_button");
        aMinigameTrack.mTrackColor = mMinigameButton.mColors.mBkg;
        aPuzzleTrack.mTrackColor = mPuzzleButton.mColors.mBkg;
        aSurvivalTrack.mTrackColor = mSurvivalButton.mColors.mBkg;

        if (mShowStartButton) {
            aSelectorReanim.assignRenderGroupToPrefix("SelectorScreen_Adventure_", Reanimation.RENDER_GROUP_HIDDEN);

            mAdventureButton.mButtonImage = image(Resources.IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_BUTTON_ID);
            mAdventureButton.mOverImage = image(Resources.IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_HIGHLIGHT_ID);
            mAdventureButton.mDownImage = image(Resources.IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_HIGHLIGHT_ID);
        } else {
            aSelectorReanim.assignRenderGroupToPrefix("SelectorScreen_StartAdventure_", Reanimation.RENDER_GROUP_HIDDEN);

            mAdventureButton.mButtonImage = image(Resources.IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_BUTTON_ID);
            mAdventureButton.mOverImage = image(Resources.IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_HIGHLIGHT_ID);
            mAdventureButton.mDownImage = image(Resources.IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_HIGHLIGHT_ID);
        }
    }

    // GameSelector::AddTrophySparkle (GameSelector.cpp:469-474)
    public void addTrophySparkle() {
        PvzpParticleSystem aTrophyParticle = mApp.addPvzpParticle(85.0f, 330.0f, RenderLayer.RENDER_LAYER_TOP,
                ParticleEffect.PARTICLE_TROPHY_SPARKLE);
        mTrophyParticleID = mApp.particleGetID(aTrophyParticle);
    }

    // GameSelector::SyncProfile (GameSelector.cpp:476-542)
    public void syncProfile(boolean theShowLoading) {
        if (theShowLoading) {
            mLoading = true;

            mApp.doDialog(Dialogs.DIALOG_MESSAGE, true, "Loading...", "", "", Dialog.BUTTONS_NONE);
            mApp.drawDirtyStuff();
            mApp.preloadForUser();
            mApp.killDialog(Dialogs.DIALOG_MESSAGE);

            mLoading = false;
        }

        PvzpParticleSystem aTrophyParticle = mApp.particleTryToGet(mTrophyParticleID);
        if (aTrophyParticle != null) {
            aTrophyParticle.particleSystemDie();
            mTrophyParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        }

        SyncProfileState aState = computeSyncProfileState(
                mApp.mPlayerInfo == null ? 1 : mApp.mPlayerInfo.getLevel(),
                mApp.hasFinishedAdventure(),
                mApp.mPlayerInfo != null && mApp.mPlayerInfo.mHasUnlockedMinigames != 0,
                mApp.mPlayerInfo != null && mApp.mPlayerInfo.mHasUnlockedPuzzleMode != 0,
                mApp.mPlayerInfo != null && mApp.mPlayerInfo.mHasUnlockedSurvivalMode != 0,
                mApp.isTrialStageLocked(),
                mApp.mPlayerInfo != null && !mApp.isIceDemo());
        mLevel = aState.mLevel;
        mShowStartButton = aState.mShowStartButton;
        mMinigamesLocked = aState.mMinigamesLocked;
        mPuzzleLocked = aState.mPuzzleLocked;
        mSurvivalLocked = aState.mSurvivalLocked;

        mHasTrophy = mApp.hasFinishedAdventure() && !mApp.isTrialStageLocked();
        if (mHasTrophy && mSelectorState != SELECTOR_OPEN) {
            addTrophySparkle();
        }

        syncButtons();
        AlmanacDialog.almanacInitForPlayer();
        Board.boardInitForPlayer();
        ReportAchievement.achievementInitForPlayer(mApp);
    }

    // GameSelector::Draw (GameSelector.cpp:544-595)
    @Override
    public void draw(Graphics g) {
        if (mApp.getDialog(Dialogs.DIALOG_STORE) != null || mApp.getDialog(Dialogs.DIALOG_ALMANAC) != null) {
            return;
        }

        g.setLinearBlend(true);
        Reanimation aSelectorReanim = selectorReanim();
        if (aSelectorReanim == null) {
            return;
        }
        aSelectorReanim.drawRenderGroup(g, 1); // "SelectorScreen_BG"
        for (int i = 0; i < 6; i++) {
            Reanimation aCloud = mApp.reanimationTryToGet(mCloudReanimID[i]);
        if (aCloud != null) {
            aCloud.draw(g);
        }
        }
        aSelectorReanim.drawRenderGroup(g, Reanimation.RENDER_GROUP_NORMAL);

        if (mSelectorState == SELECTOR_OPEN) {
            int aBGIdx = aSelectorReanim.findTrackIndex("SelectorScreen_BG_Right");
            ReanimatorTransform aTransform = new ReanimatorTransform();
            aSelectorReanim.getCurrentTransform(aBGIdx, aTransform);
            float aFractionalOffsetX = aTransform.mTransX % 1.0f;
            float aFractionalOffsetY = aTransform.mTransY % 1.0f;
            g.drawImageF(mOptionsButton.mButtonImage,
                    mOptionsButton.mX + mOptionsButton.mButtonOffsetX + aFractionalOffsetX,
                    mOptionsButton.mY + mOptionsButton.mButtonOffsetY + aFractionalOffsetY);
            g.drawImageF(mQuitButton.mButtonImage,
                    mQuitButton.mX + mQuitButton.mButtonOffsetX + aFractionalOffsetX,
                    mQuitButton.mY + mQuitButton.mButtonOffsetY + aFractionalOffsetY);
            g.drawImageF(mHelpButton.mButtonImage,
                    mHelpButton.mX + mHelpButton.mButtonOffsetX + aFractionalOffsetX,
                    mHelpButton.mY + mHelpButton.mButtonOffsetY + aFractionalOffsetY);
        }

        if (mApp.mPlayerInfo != null && !mApp.mPlayerInfo.mName.isEmpty()
                && mSelectorState != SELECTOR_OPEN && mSelectorState != SELECTOR_NEW_USER) {
            String aWelcomeStr = mApp.mPlayerInfo.mName + '!';

            int aSignIdx = aSelectorReanim.findTrackIndex("woodsign1");
            SexyTransform2D aOverlayMatrix = new SexyTransform2D(true);
            aSelectorReanim.getAttachmentOverlayMatrix(aSignIdx, aOverlayMatrix);
            float aStringWidth = mApp.getFont(Resources.FONT_BRIANNETOD16_ID).stringWidth(aWelcomeStr);
            SexyTransform2D aOffsetMatrix = new SexyTransform2D(true);
            // add the widget position so the text moves when sliding
            aOffsetMatrix.translate(170.5f - (int) (aStringWidth * 0.5f) + mX, 102.5f + mY);
            PvzpCommon.pvzpDrawStringMatrix(g, mApp.getFont(Resources.FONT_BRIANNETOD16_ID),
                    aOverlayMatrix.times(aOffsetMatrix), aWelcomeStr, new Color(255, 245, 200));
        }
    }

    // GameSelector::DrawOverlay (GameSelector.cpp:597-700)
    @Override
    public void drawOverlay(Graphics g) {
        g.setLinearBlend(true);
        if (mApp.mPlayerInfo == null) {
            return;
        }

        if (!mApp.isIceDemo() && !mShowStartButton) {
            int aOffsetX;
            int aOffsetY;
            if (mAdventureButton.mIsDown && mAdventureButton.mIsOver) {
                aOffsetX = 1;
                aOffsetY = 1;
            } else {
                aOffsetX = 0;
                aOffsetY = 0;
            }

            Reanimation aSelectorReanim = selectorReanim();
        if (aSelectorReanim == null) {
            return;
        }
            int aRightIdx = aSelectorReanim.findTrackIndex("SelectorScreen_BG_Right");
            ReanimatorTransform aTransform = new ReanimatorTransform();
            aSelectorReanim.getCurrentTransform(aRightIdx, aTransform);
            float aTransAreaX = aTransform.mTransX + aOffsetX;
            float aTransAreaY = aTransform.mTransY + aOffsetY;
            float aTransSubX = aTransAreaX;
            float aTransSubY = aTransAreaY;

            int aStage = Math.clamp((mLevel - 1) / 10 + 1, 1, 6);
            int aSub = mLevel - (aStage - 1) * 10;
            if (mApp.isTrialStageLocked() && (mLevel >= 25 || mApp.hasFinishedAdventure())) {
                aStage = 3;
                aSub = 4;
            } else {
                if (aStage == 1) {
                    aTransAreaY += 1.0f;
                } else if (aStage == 4) {
                    aTransAreaX -= 1.0f;
                }
                if (aSub == 3) {
                    aTransSubX -= 1.0f;
                }
            }

            g.setColorizeImages(true);
            g.setColor(mAdventureButton.mColors.mBkg);
            PvzpCommon.pvzpDrawImageCelF(g, image(Resources.IMAGE_SELECTORSCREEN_LEVELNUMBERS_ID),
                    aTransAreaX + 486.0f, aTransAreaY + 47.f, aStage, 0);
            if (aSub < 10) {
                PvzpCommon.pvzpDrawImageCelF(g, image(Resources.IMAGE_SELECTORSCREEN_LEVELNUMBERS_ID),
                        aTransSubX + 509.f, aTransSubY + 50.f, aSub, 0);
            } else if (aSub == 10) {
                PvzpCommon.pvzpDrawImageCelF(g, image(Resources.IMAGE_SELECTORSCREEN_LEVELNUMBERS_ID),
                        aTransSubX + 509.f, aTransSubY + 50.f, 1, 0);
                PvzpCommon.pvzpDrawImageCelF(g, image(Resources.IMAGE_SELECTORSCREEN_LEVELNUMBERS_ID),
                        aTransSubX + 518.f, aTransSubY + 51.f, 0, 0);
            }
            g.setColorizeImages(false);
        }

        if (mZenGardenButton.mVisible && mApp.mZenGarden.plantsNeedWater()) {
            g.drawImage(image(Resources.IMAGE_PLANTSPEECHBUBBLE_ID), mZenGardenButton.mX + 106, mZenGardenButton.mY + 36);
            g.drawImage(image(Resources.IMAGE_WATERDROP_ID), mZenGardenButton.mX + 123, mZenGardenButton.mY + 45);
        }

        Reanimation aHandReanim = mApp.reanimationTryToGet(mHandReanimID);
        if (aHandReanim != null) {
            g.setClipRect(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT - 40);
            aHandReanim.draw(g);
            g.clearClipRect();
        }
        Reanimation aLeaf = mApp.reanimationTryToGet(mLeafReanimID);
        if (aLeaf != null) {
            aLeaf.draw(g);
        }
        for (int i = 0; i < 3; i++) {
            Reanimation aFlower = mApp.reanimationTryToGet(mFlowerReanimID[i]);
            if (aFlower != null) {
                aFlower.draw(g);
            }
        }

        // @Minerscale: Trophy needs to draw in the DrawOverlay
        Reanimation aSelectorReanim = selectorReanim();
        if (aSelectorReanim == null) {
            return;
        }

        int aLeftIdx = aSelectorReanim.findTrackIndex("SelectorScreen_BG_Left");
        ReanimatorTransform aTransformLeft = new ReanimatorTransform();
        aSelectorReanim.getCurrentTransform(aLeftIdx, aTransformLeft);
        if (mHasTrophy) {
            if (mApp.earnedGoldTrophy()) {
                PvzpCommon.pvzpDrawImageCelF(g, image(Resources.IMAGE_SUNFLOWER_TROPHY_ID),
                        aTransformLeft.mTransX + 12.f, aTransformLeft.mTransY + 345.f, 1, 0);
            } else {
                PvzpCommon.pvzpDrawImageCelF(g, image(Resources.IMAGE_SUNFLOWER_TROPHY_ID),
                        aTransformLeft.mTransX + 12.f, aTransformLeft.mTransY + 345.f, 0, 0);
            }

            PvzpParticleSystem aTrophyParticle = mApp.particleTryToGet(mTrophyParticleID);
            if (aTrophyParticle != null) {
                aTrophyParticle.draw(g);
            }
        }

        mToolTip.draw(g);
    }

    // GameSelector::UpdateTooltip (GameSelector.cpp:702-734)
    public void updateTooltip() {
        if (!mApp.hasFinishedAdventure() || mApp.getDialog(Dialogs.DIALOG_MESSAGE) != null) {
            return;
        }

        if (mHasTrophy) {
            int aMouseX = mApp.mWidgetManager.mLastMouseX;
            int aMouseY = mApp.mWidgetManager.mLastMouseY;
            if (aMouseX >= 50 && aMouseX < 135 && aMouseY >= 275 && aMouseY < 500) {
                if (mApp.earnedGoldTrophy()) {
                    mToolTip.setLabel(mApp.pluralize(mApp.mPlayerInfo.mFinishedAdventure,
                            "[GOLD_SUNFLOWER_TOOLTIP]", "[GOLD_SUNFLOWER_TOOLTIP_PLURAL]"));
                    mToolTip.mX = 32;
                    mToolTip.mY = 510;
                    mToolTip.mVisible = true;
                } else {
                    mToolTip.setLabel("[SILVER_SUNFLOWER_TOOLTIP]");
                    mToolTip.mX = 20;
                    mToolTip.mY = 495;
                    mToolTip.mVisible = true;
                }

                return;
            }
        }

        mToolTip.mVisible = false;
        mToolTip.update();
    }

    // GameSelector::Update (GameSelector.cpp:736-897)
    @Override
    public void update() {
        super.update();
        markDirty();
        updateTooltip();

        if (mSlideCounter > 0) {
            int aNewX = PvzpCommon.pvzpAnimateCurve(75, 0, mSlideCounter, mStartX, mDestX, PvzpCurves.CURVE_EASE_IN_OUT);
            int aNewY = PvzpCommon.pvzpAnimateCurve(75, 0, mSlideCounter, mStartY, mDestY, PvzpCurves.CURVE_EASE_IN_OUT);
            move(aNewX, aNewY);

            mZombatarWidget.move(aNewX + GameConstants.BOARD_WIDTH, aNewY);
            mAchievementsWidget.mY = aNewY + mApp.mHeight;

            mSlideCounter--;
        }

        mApp.mZenGarden.updatePlantNeeds();

        PvzpParticleSystem aParticle = mApp.particleTryToGet(mTrophyParticleID);
        if (aParticle != null) {
            aParticle.update();
        }

        if (mStartingGame) {
            mStartingGameCounter++;
            if (mStartingGameCounter > 450) {
                mApp.killGameSelector();

                if (mApp.isIceDemo()) {
                    mApp.preNewGame(GameMode.GAMEMODE_CHALLENGE_ICE, false);
                    return;
                }
                if (mApp.isFirstTimeAdventureMode() && mLevel == 1 && !mApp.saveFileExists()) {
                    mApp.preNewGame(GameMode.GAMEMODE_INTRO, false);
                    return;
                }
                if (mApp.mPlayerInfo.mNeedsMagicTacoReward != 0 && mLevel == 35) {
                    StoreScreen aStore = mApp.showStoreScreen();
                    aStore.setupForIntro(601);
                    // PORT-NOTE: GameSelector.cpp:778-781 — upstream leaves the store through
                    // WaitForResult(true) (its autoKill removes the screen, Dialog.cpp:410-441) and
                    // only then starts the level; this port's wait does not pump (sexy/Dialog.java:506),
                    // so the transition is registered on the screen and runs when it closes.
                    aStore.onStoreClosed(store -> mApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, false));
                    return;
                }
                if (shouldDoZenTuturialBeforeAdventure()) {
                    mApp.preNewGame(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, false);
                    mApp.mZenGarden.setupForZenTutorial();
                    return;
                }
                mApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, true);
                return;
            }

            mAdventureButton.setBkgColor(mStartingGameCounter % 20 < 10
                    ? new Color(80, 80, 80) : Color.White);
            if (mStartingGameCounter == 125) {
                mApp.playSample(Resources.SOUND_EVILLAUGH_ID);
            }
        }

        Reanimation aSelectorReanim = selectorReanim();
        if (aSelectorReanim == null) {
            return;
        }
        switch (mSelectorState) {
        case SELECTOR_OPEN:
            if (mWidgetManager != null) {
                mWidgetManager.rehupMouse();
            }
            if (aSelectorReanim.mLoopCount > 0) {
                aSelectorReanim.assignRenderGroupToTrack("SelectorScreen_Adventure_button", Reanimation.RENDER_GROUP_HIDDEN);
                aSelectorReanim.assignRenderGroupToTrack("SelectorScreen_StartAdventure_button", Reanimation.RENDER_GROUP_HIDDEN);
                aSelectorReanim.assignRenderGroupToTrack("SelectorScreen_Survival_button", Reanimation.RENDER_GROUP_HIDDEN);
                aSelectorReanim.assignRenderGroupToTrack("SelectorScreen_Challenges_button", Reanimation.RENDER_GROUP_HIDDEN);
                aSelectorReanim.assignRenderGroupToTrack("SelectorScreen_ZenGarden_button", Reanimation.RENDER_GROUP_HIDDEN);
                mAdventureButton.mBtnNoDraw = false;
                mMinigameButton.mBtnNoDraw = false;
                mPuzzleButton.mBtnNoDraw = false;
                mSurvivalButton.mBtnNoDraw = false;
                mZenGardenButton.mBtnNoDraw = false;
                mHelpButton.mBtnNoDraw = false;
                mOptionsButton.mBtnNoDraw = false;
                mQuitButton.mBtnNoDraw = false;
                mZombatarButton.mBtnNoDraw = false;
                mAchievementsButton.mBtnNoDraw = false;
                mAdventureButton.mMouseVisible = true;
                mMinigameButton.mMouseVisible = true;
                mPuzzleButton.mMouseVisible = true;
                mSurvivalButton.mMouseVisible = true;
                mZenGardenButton.mMouseVisible = true;
                mHelpButton.mMouseVisible = true;
                mOptionsButton.mMouseVisible = true;
                mQuitButton.mMouseVisible = true;
                mStoreButton.mMouseVisible = true;
                mAlmanacButton.mMouseVisible = true;
                mChangeUserButton.mMouseVisible = true;
                mZombatarButton.mMouseVisible = true;
                mAchievementsButton.mMouseVisible = true;

                if (mApp.mPlayerInfo == null) {
                    mApp.doCreateUserDialog();
                    if (LawnApp.gIsPartnerBuild) {
                        addPreviewProfiles();
                    }

                    mSelectorState = SELECTOR_NEW_USER;
                } else {
                    aSelectorReanim.playReanim("anim_sign", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 30.0f);
                    mSelectorState = SELECTOR_IDLE;
                }

                if (mHasTrophy) {
                    addTrophySparkle();
                }

                if (mApp.mPlayerInfo != null && mApp.mPlayerInfo.mNeedsMessageOnGameSelector != 0) {
                    mApp.mPlayerInfo.mNeedsMessageOnGameSelector = 0;
                    mApp.writeCurrentUserConfig();
                    mApp.lawnMessageBox(
                            Dialogs.DIALOG_MESSAGE,
                            "[ADVENTURE_COMPLETE_HEADER]",
                            "[ADVENTURE_COMPLETE_BODY]",
                            "[DIALOG_BUTTON_OK]",
                            "",
                            Dialog.BUTTONS_FOOTER);
                }
            }
            break;
        case SELECTOR_NEW_USER:
            if (mApp.getDialog(Dialogs.DIALOG_CREATEUSER) == null) {
                aSelectorReanim.playReanim("anim_sign", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 30.0f);
                mSelectorState = SELECTOR_SHOW_SIGN;
            }
            break;
        case SELECTOR_SHOW_SIGN:
            if (aSelectorReanim.mLoopCount > 0) {
                mSelectorState = SELECTOR_IDLE;
            }
            break;
        case SELECTOR_IDLE:
            break;
        default:
            break;
        }

        for (int i = 0; i < 6; i++) {
            Reanimation aCloudReanim = mApp.reanimationGet(mCloudReanimID[i]);
            if (mCloudCounter[i] > 0) {
                if (--mCloudCounter[i] == 0) {
                    aCloudReanim.mLoopCount = 0;
                    aCloudReanim.mAnimTime = 0.0f;
                    aCloudReanim.mAnimRate = 0.5f;
                }
            } else if (aCloudReanim.mLoopCount > 0) {
                mCloudCounter[i] = PvzpCommon.randRangeInt(2000, 4000);
            }
        }
        aSelectorReanim.update();

        Reanimation aLeafReanim = mApp.reanimationGet(mLeafReanimID);
        aLeafReanim.update();
        int aLeafTrackIndex = aSelectorReanim.findTrackIndex("SelectorScreen_BG_Right");
        ReanimatorTransform aLeafTransform = new ReanimatorTransform();
        aSelectorReanim.getCurrentTransform(aLeafTrackIndex, aLeafTransform);
        aLeafReanim.setPosition(aLeafTransform.mTransX - 71.0f, aLeafTransform.mTransY - 41.0f);
        if (--mLeafCounter == 0) {
            float aRate = PvzpCommon.randRangeFloat(3.0f, 12.0f);
            aLeafReanim.playReanim("anim_grass", ReanimLoopType.REANIM_LOOP, 20, aRate);
            mLeafCounter = PvzpCommon.randRangeInt(200, 400);
        }

        for (int i = 0; i < 6; i++) {
            mApp.reanimationGet(mCloudReanimID[i]).update();
        }
        Reanimation aHandReanim = mApp.reanimationTryToGet(mHandReanimID);
        if (aHandReanim != null) {
            aHandReanim.update();
        }

        trackButton(mAdventureButton, mShowStartButton ? "SelectorScreen_StartAdventure_button" : "SelectorScreen_Adventure_button", 0.0f, 0.0f);
        trackButton(mMinigameButton, "SelectorScreen_Survival_button", 0.0f, 0.0f);
        trackButton(mPuzzleButton, "SelectorScreen_Challenges_button", 0.0f, 0.0f);
        trackButton(mSurvivalButton, "SelectorScreen_ZenGarden_button", 0.0f, 0.0f);
        trackButton(mZenGardenButton, "SelectorScreen_BG_Right", 100.0f, 360.0f);
        trackButton(mOptionsButton, "SelectorScreen_BG_Right", 494.0f, 434.0f);
        trackButton(mQuitButton, "SelectorScreen_BG_Right", 644.0f, 469.0f);
        trackButton(mHelpButton, "SelectorScreen_BG_Right", 576.0f, 458.0f);
        trackButton(mAlmanacButton, "SelectorScreen_BG_Right", 256.0f, 387.0f);
        trackButton(mStoreButton, "SelectorScreen_BG_Right", 334.0f, 441.0f);
        trackButton(mChangeUserButton, "woodsign2", 24.0f, 10.0f);
        trackButton(mZombatarButton, "woodsign3", 0.f, 0.f);
        trackButton(mAchievementsButton, "SelectorScreen_BG_Left", 20.f, 480.f);
        aSelectorReanim.setImageOverride("woodsign2",
                mChangeUserButton.mIsOver || mChangeUserButton.mIsDown
                        ? image(Resources.IMAGE_REANIM_SELECTORSCREEN_WOODSIGN2_PRESS_ID) : null);
        aSelectorReanim.setImageOverride("woodsign3",
                mZombatarButton.mIsOver || mZombatarButton.mIsDown
                        ? image(Resources.IMAGE_REANIM_SELECTORSCREEN_WOODSIGN3_PRESS_ID) : null);
    }

    // GameSelector::TrackButton (GameSelector.cpp:936-945)
    public void trackButton(DialogButton theButton, String theTrackName, float theOffsetX, float theOffsetY) {
        Reanimation aSelectorReanim = selectorReanim();
        if (aSelectorReanim == null) {
            return;
        }
        int aTrackIndex = aSelectorReanim.findTrackIndex(theTrackName);
        ReanimatorTransform aTransform = new ReanimatorTransform();
        aSelectorReanim.getCurrentTransform(aTrackIndex, aTransform);

        theButton.mX = (int) (aTransform.mTransX + theOffsetX);
        theButton.mY = (int) (aTransform.mTransY + theOffsetY);
    }

    // GameSelector::AddedToManager (GameSelector.cpp:947-953)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);

        theWidgetManager.addWidget(mZombatarWidget);
        theWidgetManager.addWidget(mAchievementsWidget);
    }

    // GameSelector::RemovedFromManager (GameSelector.cpp:955-961)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);

        theWidgetManager.removeWidget(mZombatarWidget);
        theWidgetManager.removeWidget(mAchievementsWidget);
    }

    // GameSelector::OrderInManagerChanged (GameSelector.cpp:963-967)
    @Override
    public void orderInManagerChanged() {
        mWidgetManager.putInfront(mAchievementsWidget, this);
        mWidgetManager.bringToFront(mZombatarWidget);
    }

    // GameSelector::KeyDown (GameSelector.cpp:969-1049)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            mApp.confirmQuit();
            return;
        }

        if (mApp.mKonamiCheck.check(theKey)) {
            mApp.playFoley(FoleyType.FOLEY_DROP);
            return;
        }
        if (mApp.mMustacheCheck.check(theKey) || mApp.mMoustacheCheck.check(theKey)) {
            mApp.playFoley(FoleyType.FOLEY_POLEVAULT);
            mApp.mMustacheMode = !mApp.mMustacheMode;
            ReportAchievement.giveAchievement(mApp, AchievementId.MustacheMode, false);
            return;
        }
        if (mApp.mSuperMowerCheck.check(theKey) || mApp.mSuperMowerCheck2.check(theKey)) {
            mApp.playFoley(FoleyType.FOLEY_ZAMBONI);
            mApp.mSuperMowerMode = !mApp.mSuperMowerMode;
            return;
        }
        if (mApp.mFutureCheck.check(theKey)) {
            mApp.playSample(Resources.SOUND_BOING_ID);
            mApp.mFutureMode = !mApp.mFutureMode;
            return;
        }
        if (mApp.mPinataCheck.check(theKey)) {
            if (mApp.canDoPinataMode()) {
                mApp.playFoley(FoleyType.FOLEY_JUICY);
                mApp.mPinataMode = !mApp.mPinataMode;
                return;
            } else {
                mApp.playSample(Resources.SOUND_BUZZER_ID);
                return;
            }
        }
        if (mApp.mDanceCheck.check(theKey)) {
            if (mApp.canDoDanceMode()) {
                mApp.playFoley(FoleyType.FOLEY_DANCER);
                mApp.mDanceMode = !mApp.mDanceMode;
                return;
            } else {
                mApp.playSample(Resources.SOUND_BUZZER_ID);
                return;
            }
        }
        if (mApp.mDaisyCheck.check(theKey)) {
            if (mApp.canDoDaisyMode()) {
                mApp.playSample(Resources.SOUND_LOADINGBAR_FLOWER_ID);
                mApp.mDaisyMode = !mApp.mDaisyMode;
                return;
            } else {
                mApp.playSample(Resources.SOUND_BUZZER_ID);
                return;
            }
        }
        if (mApp.mSukhbirCheck.check(theKey)) {
            mApp.playSample(Resources.SOUND_SUKHBIR_ID);
            mApp.mSukhbirMode = !mApp.mSukhbirMode;
            return;
        }
    }

    // GameSelector::KeyChar (GameSelector.cpp:1051-1088)
    @Override
    public void keyChar(char theChar) {
        if (mStartingGame) {
            return;
        }

        if ((LawnApp.gIsPartnerBuild || mApp.mDebugKeysEnabled) && theChar == 'u' && mApp.mPlayerInfo != null) {
            PvzpDebug.pvzpLogLn("Selector cheat key '{}'", theChar);

            mApp.mPlayerInfo.mFinishedAdventure = 2;
            mApp.mPlayerInfo.addCoins(50000);
            mApp.mPlayerInfo.mHasUsedCheatKeys = 1;
            mApp.mPlayerInfo.mHasUnlockedMinigames = 1;
            mApp.mPlayerInfo.mHasUnlockedPuzzleMode = 1;
            mApp.mPlayerInfo.mHasUnlockedSurvivalMode = 1;

            for (int i = 1; i <= 100; i++) {
                if (i != GameMode.GAMEMODE_TREE_OF_WISDOM && i != GameMode.GAMEMODE_SCARY_POTTER_ENDLESS
                        && i != GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS
                        && i != GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3) {
                    mApp.mPlayerInfo.mChallengeRecords[i - 1] = 20;
                }
            }
            syncProfile(true);

            mApp.eraseFile(LawnCommon.getSavedGameName(GameMode.GAMEMODE_ADVENTURE, mApp.mPlayerInfo.mId));
            mApp.eraseFile(LawnCommon.getLegacySavedGameName(GameMode.GAMEMODE_ADVENTURE, mApp.mPlayerInfo.mId));
        }

        if (mApp.mDebugKeysEnabled) {
            PvzpDebug.pvzpLogLn("Selector cheat key '{}'", theChar);
            if (theChar == 'c' || theChar == 'C') {
                mMinigamesLocked = false;
                mPuzzleLocked = false;
                mSurvivalLocked = false;
                syncButtons();
            }
        }
    }

    // GameSelector::MouseDown (GameSelector.cpp:1090-1104)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        for (int i = 0; i < 3; i++) {
            // Upstream's ReanimationGet returns the slot at the id's index, so a selector that was
            // never shown (or whose reanimations were already freed) yields a fresh, inert object and
            // the click is simply ignored. Java has no such fallback, and the throwing lookup turned a
            // click on a not-yet-shown map into a crash.
            Reanimation aFlowerReanim = mApp.reanimationTryToGet(mFlowerReanimID[i]);
            if (aFlowerReanim == null) {
                continue;
            }
            if (aFlowerReanim.mAnimRate <= 0.0f
                    && PvzpCommon.distance2D(x, y, gFlowerCenter[i][0], gFlowerCenter[i][1]) < 20.0f) {
                aFlowerReanim.mAnimRate = 24.0f;
                mApp.playFoley(FoleyType.FOLEY_LIMBS_POP);
            }
        }

        if (mApp.mCheatKeys && mStartingGame && mStartingGameCounter < 450) {
            mStartingGameCounter = 450;
        }
    }

    // GameSelector::ButtonMouseEnter (GameSelector.cpp:1106-1114)
    @Override
    public void buttonMouseEnter(int theId) {
        if ((theId == GameSelector_Minigame && mMinigamesLocked)
                || (theId == GameSelector_Puzzle && mPuzzleLocked)
                || (theId == GameSelector_Survival && mSurvivalLocked)) {
            return;
        }

        mApp.playFoley(FoleyType.FOLEY_BLEEP);
    }

    // GameSelector.h:129-131 (inline no-ops)
    @Override
    public void buttonDownTick(int theId) {
    }

    @Override
    public void buttonMouseLeave(int theId) {
    }

    @Override
    public void buttonMouseMove(int theId, int theX, int theY) {
    }

    // GameSelector::ButtonPress (GameSelector.cpp:1116-1124)
    @Override
    public void buttonPress(int theId) {
        if (theId == GameSelector_Adventure || theId == GameSelector_Minigame
                || theId == GameSelector_Puzzle || theId == GameSelector_Survival
                || theId == GameSelector_Zombatar) {
            mApp.playSample(Resources.SOUND_GRAVEBUTTON_ID);
        } else {
            mApp.playSample(Resources.SOUND_TAP_ID);
        }
    }

    // GameSelector::ClickedAdventure (GameSelector.cpp:1126-1169)
    public void clickedAdventure() {
        if (mApp.isTrialStageLocked() && (mLevel >= 25 || mApp.hasFinishedAdventure())) {
            // PORT-NOTE: GameSelector.cpp:1128-1140 — the trial build asks "replay from level 1?"
            // and only continues when it is answered Yes. Upstream reads the blocking
            // Dialog::WaitForResult() (Dialog.cpp:410-441); this port's wait does not pump
            // (sexy/Dialog.java:506), so the Yes half runs from the answer callback instead.
            mApp.lawnMessageBox(
                    Dialogs.DIALOG_MESSAGE,
                    "[REPLAY_LEVEL_HEADER]",
                    "[REPLAY_LEVEL_BODY]",
                    "[DIALOG_BUTTON_YES]",
                    "[DIALOG_BUTTON_NO]",
                    Dialog.BUTTONS_YES_NO,
                    theYes -> {
                        if (!theYes) {
                            return;
                        }

                        mApp.mPlayerInfo.mLevel = 24;
                        mApp.mPlayerInfo.mFinishedAdventure = 0;
                        mApp.eraseFile(LawnCommon.getSavedGameName(GameMode.GAMEMODE_ADVENTURE,
                                mApp.mPlayerInfo.mId));
                        mApp.eraseFile(LawnCommon.getLegacySavedGameName(GameMode.GAMEMODE_ADVENTURE,
                                mApp.mPlayerInfo.mId));
                        beginAdventure();
                    });
            return;
        }

        beginAdventure();
    }

    // GameSelector.cpp:1140-1169 (the tail of ClickedAdventure)
    private void beginAdventure() {
        mApp.mMusic.stopAllMusic();
        mApp.playSample(Resources.SOUND_LOSEMUSIC_ID);
        mStartingGame = true;
        mAdventureButton.setDisabled(true);
        mMinigameButton.setDisabled(true);
        mPuzzleButton.setDisabled(true);
        mOptionsButton.setDisabled(true);
        mQuitButton.setDisabled(true);
        mHelpButton.setDisabled(true);
        mChangeUserButton.setDisabled(true);
        mStoreButton.setDisabled(true);
        mAlmanacButton.setDisabled(true);
        mSurvivalButton.setDisabled(true);
        mZenGardenButton.setDisabled(true);
        mZombatarButton.setDisabled(true);
        mAchievementsButton.setDisabled(true);

        Reanimation aHandReanim = mApp.addReanimation(-70.0f, 10.0f, 0, ReanimationType.REANIM_ZOMBIE_HAND);
        aHandReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        mHandReanimID = mApp.reanimationGetID(aHandReanim);
        mApp.playFoley(FoleyType.FOLEY_DIRT_RISE);
        for (int i = 0; i < aHandReanim.mDefinition.mTracks.mArrayCount; i++) {
            ReanimatorTrack aTrack = (ReanimatorTrack) aHandReanim.mDefinition.mTracks.mArrayData[i];
            if (aTrack.mName.regionMatches(true, 0, "rock", 0, 4)) {
                aHandReanim.mTrackInstances[i].mIgnoreClipRect = true;
            }
        }
    }

    // GameSelector::ShouldDoZenTuturialBeforeAdventure (GameSelector.cpp:1171-1174)
    public boolean shouldDoZenTuturialBeforeAdventure() {
        return !mApp.hasFinishedAdventure() && mApp.mPlayerInfo.getLevel() == 45
                && mApp.mPlayerInfo.mNumPottedPlants == 0;
    }

    // GameSelector::ButtonDepress (GameSelector.cpp:1176-1258)
    @Override
    public void buttonDepress(int theId) {
        if (mSlideCounter > 0) {
            return;
        }

        if (theId == GameSelector_Minigame && mMinigamesLocked) {
            mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "[MODE_LOCKED]", "[MINIGAME_LOCKED_MESSAGE]",
                    "[DIALOG_BUTTON_OK]", "", Dialog.BUTTONS_FOOTER);
            return;
        }
        if (theId == GameSelector_Puzzle && mPuzzleLocked) {
            mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "[MODE_LOCKED]", "[PUZZLE_LOCKED_MESSAGE]",
                    "[DIALOG_BUTTON_OK]", "", Dialog.BUTTONS_FOOTER);
            return;
        }
        if (theId == GameSelector_Survival && mSurvivalLocked) {
            mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "[MODE_LOCKED]", "[SURVIVAL_LOCKED_MESSAGE]",
                    "[DIALOG_BUTTON_OK]", "", Dialog.BUTTONS_FOOTER);
            return;
        }

        switch (theId) {
        case GameSelector_Adventure:
            clickedAdventure();
            break;
        case GameSelector_Minigame:
            mApp.killGameSelector();
            mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
            break;
        case GameSelector_Puzzle:
            mApp.killGameSelector();
            mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
            break;
        case GameSelector_Survival:
            mApp.killGameSelector();
            mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
            break;
        case GameSelector_Quit:
            mApp.confirmQuit();
            break;
        case GameSelector_Help:
            mApp.killGameSelector();
            mApp.showAwardScreen(AwardType.AWARD_HELP_ZOMBIENOTE, false);
            break;
        case GameSelector_Options:
            mApp.doNewOptions(true);
            break;
        case GameSelector_ChangeUser:
            mApp.doUserDialog();
            break;
        case GameSelector_Store: {
            StoreScreen aStore = mApp.showStoreScreen();
            // PORT-NOTE: GameSelector.cpp:1229-1237 — upstream reads mGoToTreeNow after
            // WaitForResult(true) returned, i.e. after autoKill removed the screen (Dialog.cpp:410-441),
            // and restores the menu music on the way out. This port's wait does not pump
            // (sexy/Dialog.java:506), so both run from the screen's close path instead.
            aStore.onStoreClosed(store -> {
                if (store.mGoToTreeNow) {
                    mApp.killGameSelector();
                    mApp.preNewGame(GameMode.GAMEMODE_TREE_OF_WISDOM, false);
                } else {
                    mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME);
                }
            });

            break;
        }
        case GameSelector_Almanac:
            // PORT-NOTE: GameSelector.cpp:1242-1243 — upstream restores the map music after the
            // almanac's blocking WaitForResult(true) returned; this port's wait does not pump
            // (sexy/Dialog.java:506), so the restore runs when the almanac closes.
            mApp.doAlmanacDialog(SeedType.SEED_NONE, ZombieType.ZOMBIE_INVALID);
            mApp.onAlmanacClosed(() -> mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME));
            break;
        case GameSelector_ZenGarden:
            mApp.killGameSelector();
            mApp.preNewGame(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, false);
            if (shouldDoZenTuturialBeforeAdventure()) {
                mApp.mZenGarden.setupForZenTutorial();
            }
            break;
        case GameSelector_Zombatar:
            showZombatarScreen();
            break;
        case GameSelector_Achievements:
            showAchievementsScreen();
            break;
        default:
            break;
        }
    }

    // GameSelector::AddPreviewProfiles (GameSelector.cpp:1260-1360)
    public void addPreviewProfiles() {
        PlayerInfo aProfile;

        aProfile = mApp.mProfileMgr.addProfile("2 Night");
        if (aProfile != null) {
            aProfile.mLevel = 11;
            aProfile.saveDetails();
        }

        aProfile = mApp.mProfileMgr.addProfile("3 Pool");
        if (aProfile != null) {
            aProfile.mLevel = 21;
            aProfile.mHasUnlockedMinigames = 1;
            aProfile.mCoins = 400;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 1;
            aProfile.saveDetails();
        }

        aProfile = mApp.mProfileMgr.addProfile("4 Fog");
        if (aProfile != null) {
            aProfile.mLevel = 31;
            aProfile.mHasUnlockedMinigames = 1;
            aProfile.mHasUnlockedSurvivalMode = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 2;
            aProfile.mPurchases[StoreItem.STORE_ITEM_POOL_CLEANER] = 1;
            aProfile.mCoins = 400;
            aProfile.saveDetails();
        }

        aProfile = mApp.mProfileMgr.addProfile("5 Roof");
        if (aProfile != null) {
            aProfile.mLevel = 41;
            aProfile.mHasUnlockedMinigames = 1;
            aProfile.mHasUnlockedPuzzleMode = 1;
            aProfile.mHasUnlockedSurvivalMode = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 2;
            aProfile.mPurchases[StoreItem.STORE_ITEM_POOL_CLEANER] = 1;
            aProfile.mCoins = 500;
            aProfile.saveDetails();
        }

        aProfile = mApp.mProfileMgr.addProfile("Complete");
        if (aProfile != null) {
            aProfile.mLevel = 1;
            aProfile.mFinishedAdventure = 1;
            aProfile.mHasUnlockedMinigames = 1;
            aProfile.mHasUnlockedPuzzleMode = 1;
            aProfile.mHasUnlockedSurvivalMode = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 2;
            aProfile.mPurchases[StoreItem.STORE_ITEM_POOL_CLEANER] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_ROOF_CLEANER] = 1;
            aProfile.mCoins = 1000;
            aProfile.saveDetails();
        }

        aProfile = mApp.mProfileMgr.addProfile("Full Unlock");
        if (aProfile != null) {
            aProfile.mLevel = 1;
            aProfile.mFinishedAdventure = 2;
            aProfile.addCoins(50000);
            aProfile.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;
            aProfile.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;
            aProfile.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;
            aProfile.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] = PlayerInfo.PURCHASE_COUNT_OFFSET + 5;
            aProfile.mHasUnlockedMinigames = 1;
            aProfile.mHasUnlockedPuzzleMode = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_GLOOMSHROOM] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_CATTAIL] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_WINTERMELON] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_GOLD_MAGNET] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_SPIKEROCK] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_COBCANNON] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PLANT_IMITATER] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 3;
            aProfile.mPurchases[StoreItem.STORE_ITEM_POOL_CLEANER] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_ROOF_CLEANER] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_PHONOGRAPH] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_GARDENING_GLOVE] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_MUSHROOM_GARDEN] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_WHEEL_BARROW] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_AQUARIUM_GARDEN] = 1;
            aProfile.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] = 1;

            aProfile.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM - 1] = 1;
            for (int i = 1; i <= 100; i++) {
                if (i != GameMode.GAMEMODE_TREE_OF_WISDOM && i != GameMode.GAMEMODE_SCARY_POTTER_ENDLESS
                        && i != GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS
                        && i != GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3) {
                    aProfile.mChallengeRecords[i - 1] = 20;
                }
            }

            aProfile.saveDetails();
        }
    }

    // GameSelector::SlideTo (GameSelector.cpp:1362-1372)
    public void slideTo(int theX, int theY) {
        if (mSlideCounter > 0) {
            return;
        }

        mSlideCounter = 75;
        mDestX = theX;
        mDestY = theY;
        mStartX = mX;
        mStartY = mY;
    }

    // GameSelector::ShowZombatarScreen (GameSelector.cpp:1374-1382)
    public void showZombatarScreen() {
        if (mZombatarWidget == null) {
            return;
        }
        if (mApp.mPlayerInfo != null && mApp.mPlayerInfo.mZombatarAccepted == 0) {
            mApp.showZombatarTOS();
        } else {
            mZombatarWidget.open();
        }
    }

    // GameSelector::ShowAchievementsScreen (GameSelector.cpp:1384-1388)
    public void showAchievementsScreen() {
        slideTo(0, -mApp.mHeight);
        mWidgetManager.setFocus(mAchievementsWidget);
    }
}

/**
 * Ported from src/Lawn/Widget/GameSelector.h:137-149 and src/Lawn/Widget/GameSelector.cpp:51-61.
 *
 * <p>A transparent, mouse-less overlay widget added on top of the selector so the level picker,
 * trophy and hand render above every button. Upstream declares it as a second top-level class in
 * GameSelector.cpp.
 */
class GameSelectorOverlay extends Widget {

    public GameSelector mParent;

    // GameSelectorOverlay::GameSelectorOverlay (GameSelector.cpp:55-60)
    GameSelectorOverlay(GameSelector theGameSelector) {
        mParent = theGameSelector;
        mMouseVisible = false;
        mHasAlpha = true;
    }

    // GameSelectorOverlay::Draw (GameSelector.cpp:51-53)
    @Override
    public void draw(Graphics g) {
        mParent.drawOverlay(g);
    }
}
