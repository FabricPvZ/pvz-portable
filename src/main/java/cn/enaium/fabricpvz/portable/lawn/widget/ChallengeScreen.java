/*
 * Ported from src/Lawn/Widget/ChallengeScreen.h:26-105 and src/Lawn/Widget/ChallengeScreen.cpp:20-771.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.ToolTipWidget;
import cn.enaium.fabricpvz.portable.lawn.UnlockingState;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/ChallengeScreen.h:26-105 and src/Lawn/Widget/ChallengeScreen.cpp:20-771.
 *
 * <p>The mini-game / puzzle / survival picker: one page of 104x115 thumbnails at a time, four page
 * buttons (cheat-key gated), a back button and a tooltip for locked entries. The 72-entry
 * {@link #gChallengeDefs} table is index-aligned to {@code GameMode - GAMEMODE_SURVIVAL_NORMAL_STAGE_1}
 * (ChallengeScreen.cpp:39-112, {@code GetChallengeDefinition} at :216-222), so button id
 * {@code 200 + i} opens {@code GameMode(i + 1)}.
 *
 * <p>Every entry's enablement follows upstream's trophy/accomplishment lock rules through
 * {@link #isEntryEnabled(int, int)}; selecting any entry starts its mode.
 */
public class ChallengeScreen extends Widget implements ButtonListener {

    /** Ported from src/Lawn/Widget/ChallengeScreen.h:32 (`NUM_CHALLENGE_MODES = NUM_GAME_MODES - 1`). */
    public static final int NUM_CHALLENGE_MODES = GameMode.NUM_GAME_MODES - 1;

    // ChallengeScreen.h:34-38 (button-id enum)
    public static final int ChallengeScreen_Back = 100;
    public static final int ChallengeScreen_Mode = 200;
    public static final int ChallengeScreen_Page = 300;

    // ChallengeScreen.cpp:160-176 (thumbnail / page button geometry)
    public static final int CHALLENGE_BUTTON_WIDTH = 104;
    public static final int CHALLENGE_BUTTON_HEIGHT = 115;
    public static final int CHALLENGE_BUTTON_X = 38;
    public static final int CHALLENGE_BUTTON_COL_PITCH = 155;
    /** Survival page row layout: `125 + row * 145` (ChallengeScreen.cpp:170). */
    public static final int CHALLENGE_BUTTON_SURVIVAL_Y = 125;
    public static final int CHALLENGE_BUTTON_SURVIVAL_ROW_PITCH = 145;
    /** Mini-game / limbo / puzzle row layout: `93 + row * 119` (ChallengeScreen.cpp:168). */
    public static final int CHALLENGE_BUTTON_OTHER_Y = 93;
    public static final int CHALLENGE_BUTTON_OTHER_ROW_PITCH = 119;
    // ChallengeScreen.cpp:141-158 (page buttons)
    public static final int CHALLENGE_PAGE_BUTTON_X = 200;
    public static final int CHALLENGE_PAGE_BUTTON_PITCH = 100;
    public static final int CHALLENGE_PAGE_BUTTON_Y = 540;
    public static final int CHALLENGE_PAGE_BUTTON_WIDTH = 100;
    public static final int CHALLENGE_PAGE_BUTTON_HEIGHT = 75;
    // ChallengeScreen.cpp:129-139 (back button)
    public static final int CHALLENGE_BACK_BUTTON_X = 18;
    public static final int CHALLENGE_BACK_BUTTON_Y = 568;
    public static final int CHALLENGE_BACK_BUTTON_WIDTH = 111;
    public static final int CHALLENGE_BACK_BUTTON_HEIGHT = 26;

    /** Ported from src/Lawn/Widget/ChallengeScreen.cpp:39-112 (`gChallengeDefs`). */
    public static final ChallengeDefinition[] gChallengeDefs = {
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1, 0, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 0, "[SURVIVAL_DAY_NORMAL]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_2, 1, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 1, "[SURVIVAL_NIGHT_NORMAL]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_3, 2, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 2, "[SURVIVAL_POOL_NORMAL]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_4, 3, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 3, "[SURVIVAL_FOG_NORMAL]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_5, 4, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 4, "[SURVIVAL_ROOF_NORMAL]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_1, 5, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 0, "[SURVIVAL_DAY_HARD]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_2, 6, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 1, "[SURVIVAL_NIGHT_HARD]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_3, 7, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 2, "[SURVIVAL_POOL_HARD]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_4, 8, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 3, "[SURVIVAL_FOG_HARD]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_5, 9, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 4, "[SURVIVAL_ROOF_HARD]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 0, "[SURVIVAL_DAY_ENDLESS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_2, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 1, "[SURVIVAL_NIGHT_ENDLESS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3, 10, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 2, 2, "[SURVIVAL_POOL_ENDLESS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_4, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 2, "[SURVIVAL_FOG_ENDLESS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_5, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 3, "[SURVIVAL_ROOF_ENDLESS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS, 0, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 0, "[WAR_AND_PEAS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING, 6, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 1, "[WALL_NUT_BOWLING]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE, 2, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 2, "[SLOT_MACHINE]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS, 3, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 3, "[ITS_RAINING_SEEDS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BEGHOULED, 1, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 4, "[BEGHOULED]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL, 8, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 0, "[INVISIGHOUL]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SEEING_STARS, 5, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 1, "[SEEING_STARS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM, 7, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 2, "[ZOMBIQUARIUM]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST, 20, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 3, "[BEGHOULED_TWIST]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_LITTLE_TROUBLE, 12, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 4, "[LITTLE_TROUBLE]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, 15, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 0, "[PORTAL_COMBAT]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_COLUMN, 4, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 1, "[COLUMN_AS_YOU_SEE_EM]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA, 17, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 2, "[BOBSLED_BONANZA]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SPEED, 18, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 3, "[ZOMBIES_ON_SPEED]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE, 16, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 4, "[WHACK_A_ZOMBIE]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_LAST_STAND, 21, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 0, "[LAST_STAND]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2, 0, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 1, "[WAR_AND_PEAS_2]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING_2, 6, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 2, "[WALL_NUT_BOWLING_EXTREME]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_POGO_PARTY, 14, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 3, "[POGO_PARTY]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_FINAL_BOSS, 19, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 4, "[FINAL_BOSS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT, 0, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 0, "[ART_CHALLENGE_WALL_NUT]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY, 1, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 1, "[SUNNY_DAY]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_RESODDED, 2, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 2, "[UNSODDED]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BIG_TIME, 3, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 3, "[BIG_TIME]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_SUNFLOWER, 4, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 4, "[ART_CHALLENGE_SUNFLOWER]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, 5, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 0, "[AIR_RAID]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ICE, 6, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 1, "[ICE_LEVEL]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, 7, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 2, "[ZEN_GARDEN]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY, 8, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 3, "[HIGH_GRAVITY]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER, 11, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 4, "[GRAVE_DANGER]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SHOVEL, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 0, "[CAN_YOU_DIG_IT]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_STORMY_NIGHT, 13, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 1, "[DARK_STORMY_NIGHT]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BUNGEE_BLITZ, 9, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 2, "[BUNGEE_BLITZ]"),
        new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SQUIRREL, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 3, "Squirrel"),
        new ChallengeDefinition(GameMode.GAMEMODE_TREE_OF_WISDOM, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 4, "Tree of Wisdom"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_1, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 0, "[SCARY_POTTER_1]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_2, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 1, "[SCARY_POTTER_2]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_3, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 2, "[SCARY_POTTER_3]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_4, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 3, "[SCARY_POTTER_4]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_5, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 4, "[SCARY_POTTER_5]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_6, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 0, "[SCARY_POTTER_6]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_7, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 1, "[SCARY_POTTER_7]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_8, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 2, "[SCARY_POTTER_8]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_9, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 3, "[SCARY_POTTER_9]"),
        new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_ENDLESS, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 4, "[SCARY_POTTER_ENDLESS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 0, "[I_ZOMBIE_1]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 1, "[I_ZOMBIE_2]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 2, "[I_ZOMBIE_3]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 3, "[I_ZOMBIE_4]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 4, "[I_ZOMBIE_5]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 0, "[I_ZOMBIE_6]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 1, "[I_ZOMBIE_7]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 2, "[I_ZOMBIE_8]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 3, "[I_ZOMBIE_9]"),
        new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 4, "[I_ZOMBIE_ENDLESS]"),
        new ChallengeDefinition(GameMode.GAMEMODE_UPSELL, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 4, "Upsell"),
        new ChallengeDefinition(GameMode.GAMEMODE_INTRO, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 3, "Intro"),
    };

    // ChallengeScreen.h:50-58
    public NewLawnButton mBackButton;
    public ButtonWidget[] mPageButton = new ButtonWidget[ChallengePage.MAX_CHALLANGE_PAGES];
    public ButtonWidget[] mChallengeButtons = new ButtonWidget[NUM_CHALLENGE_MODES];
    public LawnApp mApp;
    public ToolTipWidget mToolTip;
    public int mPageIndex;
    public boolean mCheatEnableChallenges;
    public int mUnlockState;
    public int mUnlockStateCounter;
    public int mUnlockChallengeIndex;
    public float mLockShakeX;
    public float mLockShakeY;
    public boolean mLimboPageUnlocked;
    public int mClickCount;
    public int mLastClickUpdateCnt;

    /**
     * Ported from src/Lawn/Widget/ChallengeScreen.cpp:216-222 ({@code GetChallengeDefinition}).
     */
    public static ChallengeDefinition getChallengeDefinition(int theChallengeMode) {
        return gChallengeDefs[theChallengeMode];
    }

    /**
     * Pure enablement predicate used by the UI: an entry is enabled exactly when upstream's
     * trophy/accomplishment lock reports no pending requirement. Mirrors upstream
     * {@code mDisabled = MoreTrophiesNeeded(i) != 0} (ChallengeScreen.cpp:172-175).
     */
    public static boolean isEntryEnabled(int theDefIndex, int theMoreTrophiesNeeded) {
        return theMoreTrophiesNeeded == 0;
    }

    // ChallengeScreen.cpp:160-170 (thumbnail grid position)
    public static int challengeButtonX(int theCol) {
        return CHALLENGE_BUTTON_X + theCol * CHALLENGE_BUTTON_COL_PITCH;
    }

    // ChallengeScreen.cpp:166-170 (survival uses a taller row pitch than the other pages)
    public static int challengeButtonY(int thePage, int theRow) {
        if (thePage == ChallengePage.CHALLENGE_PAGE_SURVIVAL) {
            return CHALLENGE_BUTTON_SURVIVAL_Y + theRow * CHALLENGE_BUTTON_SURVIVAL_ROW_PITCH;
        }
        return CHALLENGE_BUTTON_OTHER_Y + theRow * CHALLENGE_BUTTON_OTHER_ROW_PITCH;
    }

    // ChallengeScreen::ChallengeScreen (ChallengeScreen.cpp:114-211)
    public ChallengeScreen(LawnApp theApp, int thePage) {
        mLockShakeX = 0;
        mLockShakeY = 0;
        mPageIndex = thePage;
        mApp = theApp;
        mClip = false;
        mCheatEnableChallenges = false;
        mUnlockState = UnlockingState.UNLOCK_OFF;
        mUnlockChallengeIndex = -1;
        mUnlockStateCounter = 0;
        mLimboPageUnlocked = false;
        mClickCount = 0;
        mLastClickUpdateCnt = 0;
        mLoadedResourceNames.add("DelayLoad_ChallengeScreen");
        Resources.extractResourcesByName(mApp.mResourceManager, "DelayLoad_ChallengeScreen");

        // ChallengeScreen.cpp:130-131: the groups registered above are loaded right away, before any widget reads its
        // images - without this the screen drew null images for everything they provide.
        for (String aResource : mLoadedResourceNames) {
            ((cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(aResource);
        }

        mBackButton = GameButton.makeNewButton(ChallengeScreen_Back, this, "[BACK_TO_MENU]", null,
                Resources.getImageById(Resources.IMAGE_SEEDCHOOSER_BUTTON2_ID),
                Resources.getImageById(Resources.IMAGE_SEEDCHOOSER_BUTTON2_GLOW_ID),
                Resources.getImageById(Resources.IMAGE_SEEDCHOOSER_BUTTON2_GLOW_ID));
        mBackButton.mTextDownOffsetX = 1;
        mBackButton.mTextDownOffsetY = 1;
        mBackButton.setLabelColor(new Color(42, 42, 90));
        mBackButton.setLabelHiliteColor(new Color(42, 42, 90));
        mBackButton.resize(CHALLENGE_BACK_BUTTON_X, CHALLENGE_BACK_BUTTON_Y,
                CHALLENGE_BACK_BUTTON_WIDTH, CHALLENGE_BACK_BUTTON_HEIGHT);

        for (int aPageIdx = ChallengePage.CHALLENGE_PAGE_SURVIVAL; aPageIdx < ChallengePage.MAX_CHALLANGE_PAGES; aPageIdx++) {
            mPageButton[aPageIdx] = new ButtonWidget(ChallengeScreen_Page + aPageIdx, this);
            ButtonWidget aPageButton = mPageButton[aPageIdx];
            aPageButton.mDoFinger = true;
            if (aPageIdx == ChallengePage.CHALLENGE_PAGE_LIMBO) {
                aPageButton.mLabel = mApp.getString("LIMBO_PAGE_BUTTON", "Limbo Page");
            } else {
                aPageButton.mLabel = PvzpStringFile.translate("[PAGE_X]")
                        .replace("{PAGE}", Integer.toString(aPageIdx));
            }
            aPageButton.mButtonImage = Resources.getImageById(Resources.IMAGE_BLANK_ID);
            aPageButton.mOverImage = Resources.getImageById(Resources.IMAGE_BLANK_ID);
            aPageButton.mDownImage = Resources.getImageById(Resources.IMAGE_BLANK_ID);
            aPageButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
            aPageButton.setLabelColor(new Color(255, 240, 0));
            aPageButton.setLabelHiliteColor(new Color(220, 220, 0));
            aPageButton.resize(CHALLENGE_PAGE_BUTTON_X + CHALLENGE_PAGE_BUTTON_PITCH * aPageIdx,
                    CHALLENGE_PAGE_BUTTON_Y, CHALLENGE_PAGE_BUTTON_WIDTH, CHALLENGE_PAGE_BUTTON_HEIGHT);
            if (!showPageButtons() || aPageIdx == ChallengePage.CHALLENGE_PAGE_SURVIVAL
                    || aPageIdx == ChallengePage.CHALLENGE_PAGE_PUZZLE) {
                aPageButton.mVisible = false;
            }
        }

        for (int aChallengeMode = 0; aChallengeMode < NUM_CHALLENGE_MODES; aChallengeMode++) {
            ChallengeDefinition aChlDef = getChallengeDefinition(aChallengeMode);
            mChallengeButtons[aChallengeMode] = new ButtonWidget(ChallengeScreen_Mode + aChallengeMode, this);
            ButtonWidget aChallengeButton = mChallengeButtons[aChallengeMode];
            aChallengeButton.mDoFinger = true;
            aChallengeButton.mFrameNoDraw = true;
            aChallengeButton.resize(challengeButtonX(aChlDef.mCol), challengeButtonY(aChlDef.mPage, aChlDef.mRow),
                    CHALLENGE_BUTTON_WIDTH, CHALLENGE_BUTTON_HEIGHT);
            if (!isEntryEnabled(aChallengeMode, moreTrophiesNeeded(aChallengeMode))) {
                aChallengeButton.mDoFinger = false;
                aChallengeButton.setDisabled(true);
            }
        }

        mToolTip = new ToolTipWidget();
        mToolTip.mCenter = true;
        mToolTip.mVisible = false;
        updateButtons();

        if (mApp.mGameMode != GameMode.GAMEMODE_UPSELL || mApp.mGameScene != GameScenes.SCENE_LEVEL_INTRO) {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS);
        }

        if (mPageIndex == ChallengePage.CHALLENGE_PAGE_SURVIVAL && mApp.mPlayerInfo.mHasNewSurvival != 0) {
            setUnlockChallengeIndex(mPageIndex, false);
            mApp.mPlayerInfo.mHasNewSurvival = 0;
        } else if (mPageIndex == ChallengePage.CHALLENGE_PAGE_CHALLENGE && mApp.mPlayerInfo.mHasNewMiniGame != 0) {
            setUnlockChallengeIndex(mPageIndex, false);
            mApp.mPlayerInfo.mHasNewMiniGame = 0;
        } else if (mPageIndex == ChallengePage.CHALLENGE_PAGE_PUZZLE) {
            if (mApp.mPlayerInfo.mHasNewScaryPotter != 0) {
                setUnlockChallengeIndex(mPageIndex, false);
                mApp.mPlayerInfo.mHasNewScaryPotter = 0;
            } else if (mApp.mPlayerInfo.mHasNewIZombie != 0) {
                setUnlockChallengeIndex(mPageIndex, true);
                mApp.mPlayerInfo.mHasNewIZombie = 0;
            }
        }

    }

    // ChallengeScreen::IsScaryPotterLevel (ChallengeScreen.cpp:224-228)
    public boolean isScaryPotterLevel(int theGameMode) {
        return theGameMode >= GameMode.GAMEMODE_SCARY_POTTER_1 && theGameMode <= GameMode.GAMEMODE_SCARY_POTTER_ENDLESS;
    }

    // ChallengeScreen::IsIZombieLevel (ChallengeScreen.cpp:230-233)
    public boolean isIZombieLevel(int theGameMode) {
        return theGameMode >= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1
                && theGameMode <= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS;
    }

    // ChallengeScreen::SetUnlockChallengeIndex (ChallengeScreen.cpp:235-254)
    public void setUnlockChallengeIndex(int thePage, boolean theIsIZombie) {
        mUnlockState = UnlockingState.UNLOCK_SHAKING;
        mUnlockStateCounter = 100;
        mUnlockChallengeIndex = 0;
        for (int aChallengeMode = 0; aChallengeMode < NUM_CHALLENGE_MODES; aChallengeMode++) {
            ChallengeDefinition aDef = getChallengeDefinition(aChallengeMode);
            if (aDef.mPage == thePage) {
                if (thePage != ChallengePage.CHALLENGE_PAGE_PUZZLE
                        || (!theIsIZombie && isScaryPotterLevel(aDef.mChallengeMode))
                        || (theIsIZombie && isIZombieLevel(aDef.mChallengeMode))) {
                    if (accomplishmentsNeeded(aChallengeMode) <= 0) {
                        mUnlockChallengeIndex = aChallengeMode;
                    }
                }
            }
        }
    }

    // ChallengeScreen::MoreTrophiesNeeded (ChallengeScreen.cpp:256-326)
    public int moreTrophiesNeeded(int theChallengeIndex) {
        ChallengeDefinition aDef = getChallengeDefinition(theChallengeIndex);
        if (mApp.mGameMode == GameMode.GAMEMODE_UPSELL && mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
            return aDef.mChallengeMode == GameMode.GAMEMODE_CHALLENGE_FINAL_BOSS ? 1 : 0;
        }

        if (mApp.isTrialStageLocked()) {
            if (mPageIndex == ChallengePage.CHALLENGE_PAGE_PUZZLE
                    && aDef.mChallengeMode >= GameMode.GAMEMODE_SCARY_POTTER_4) {
                return aDef.mChallengeMode == GameMode.GAMEMODE_SCARY_POTTER_4 ? 1 : 2;
            } else if (mPageIndex == ChallengePage.CHALLENGE_PAGE_CHALLENGE
                    && aDef.mChallengeMode >= GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS) {
                return aDef.mChallengeMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS ? 1 : 2;
            } else if (mPageIndex == ChallengePage.CHALLENGE_PAGE_SURVIVAL
                    && aDef.mChallengeMode >= GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_4) {
                return aDef.mChallengeMode == GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_4 ? 1 : 2;
            }
        }

        if (aDef.mPage == ChallengePage.CHALLENGE_PAGE_PUZZLE) {
            if (isScaryPotterLevel(aDef.mChallengeMode)) {
                int aLevelsCompleted = 0;
                for (ChallengeDefinition aSPDef : gChallengeDefs) {
                    if (isScaryPotterLevel(aSPDef.mChallengeMode) && mApp.hasBeatenChallenge(aSPDef.mChallengeMode)) {
                        aLevelsCompleted++;
                    }
                }

                if (aDef.mChallengeMode < GameMode.GAMEMODE_SCARY_POTTER_4 || mApp.hasFinishedAdventure()
                        || aLevelsCompleted < 3) {
                    return Math.clamp(aDef.mChallengeMode - GameMode.GAMEMODE_SCARY_POTTER_1 - aLevelsCompleted, 0, 9);
                }
                return aDef.mChallengeMode == GameMode.GAMEMODE_SCARY_POTTER_4 ? 1 : 2;
            } else if (isIZombieLevel(aDef.mChallengeMode)) {
                int aLevelsCompleted = 0;
                for (ChallengeDefinition aIZDef : gChallengeDefs) {
                    if (isIZombieLevel(aIZDef.mChallengeMode) && mApp.hasBeatenChallenge(aIZDef.mChallengeMode)) {
                        aLevelsCompleted++;
                    }
                }

                if (aDef.mChallengeMode < GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4 || mApp.hasFinishedAdventure()
                        || aLevelsCompleted < 3) {
                    return Math.clamp(aDef.mChallengeMode - GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 - aLevelsCompleted, 0, 9);
                }
                return aDef.mChallengeMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4 ? 1 : 2;
            }
        } else {
            int aIdxInPage = aDef.mRow * 5 + aDef.mCol;
            if ((aDef.mPage == ChallengePage.CHALLENGE_PAGE_CHALLENGE
                    || aDef.mPage == ChallengePage.CHALLENGE_PAGE_SURVIVAL) && !mApp.hasFinishedAdventure()) {
                return aIdxInPage < 3 ? 0 : aIdxInPage == 3 ? 1 : 2;
            }
            int aNumTrophies = mApp.getNumTrophies(aDef.mPage);
            if (aDef.mPage == ChallengePage.CHALLENGE_PAGE_LIMBO) {
                return 0;
            }
            if (LawnApp.isSurvivalEndless(aDef.mChallengeMode)) {
                return 10 - aNumTrophies;
            }
            if (aDef.mPage == ChallengePage.CHALLENGE_PAGE_SURVIVAL
                    || aDef.mPage == ChallengePage.CHALLENGE_PAGE_CHALLENGE) {
                aNumTrophies += 3;
            }

            return aIdxInPage >= aNumTrophies ? aIdxInPage - aNumTrophies + 1 : 0;
        }

        // Unreachable for the four known pages.
        return 0;
    }

    // ChallengeScreen::ShowPageButtons (ChallengeScreen.cpp:356-360)
    public boolean showPageButtons() {
        return mApp.mCheatKeys && mPageIndex != ChallengePage.CHALLENGE_PAGE_SURVIVAL
                && mPageIndex != ChallengePage.CHALLENGE_PAGE_PUZZLE;
    }

    // ChallengeScreen::UpdateButtons (ChallengeScreen.cpp:362-384)
    public void updateButtons() {
        for (int aChallengeMode = 0; aChallengeMode < NUM_CHALLENGE_MODES; aChallengeMode++) {
            mChallengeButtons[aChallengeMode].mVisible =
                    getChallengeDefinition(aChallengeMode).mPage == mPageIndex;
        }
        for (int aPage = 0; aPage < ChallengePage.MAX_CHALLANGE_PAGES; aPage++) {
            ButtonWidget aPageButton = mPageButton[aPage];

            if (mLimboPageUnlocked && aPage == ChallengePage.CHALLENGE_PAGE_LIMBO) {
                aPageButton.mVisible = true;
            }

            if (aPage == mPageIndex) {
                aPageButton.setLabelColor(new Color(64, 64, 64));
                aPageButton.mDisabled = true;
            } else {
                aPageButton.setLabelColor(new Color(255, 240, 0));
                aPageButton.mDisabled = false;
            }
        }
    }

    // ChallengeScreen::AccomplishmentsNeeded (ChallengeScreen.cpp:386-393)
    public int accomplishmentsNeeded(int theChallengeIndex) {
        int aTrophiesNeeded = moreTrophiesNeeded(theChallengeIndex);
        int aGameMode = getChallengeDefinition(theChallengeIndex).mChallengeMode;
        if (LawnApp.isSurvivalEndless(aGameMode) && aTrophiesNeeded <= 3
                && mApp.getNumTrophies(ChallengePage.CHALLENGE_PAGE_SURVIVAL) < 10
                && mApp.hasFinishedAdventure() && !mApp.isTrialStageLocked()) {
            aTrophiesNeeded = 1;
        }
        return mCheatEnableChallenges ? 0 : aTrophiesNeeded;
    }

    // ChallengeScreen::DrawButton (ChallengeScreen.cpp:395-566)
    public void drawButton(Graphics g, int theChallengeIndex) {
        ButtonWidget aChallengeButton = mChallengeButtons[theChallengeIndex];
        if (!aChallengeButton.mVisible) {
            return;
        }

        ChallengeDefinition aDef = getChallengeDefinition(theChallengeIndex);
        int aPosX = aChallengeButton.mX;
        int aPosY = aChallengeButton.mY;
        if (aChallengeButton.mIsDown) {
            aPosX++;
            aPosY++;
        }

        if (accomplishmentsNeeded(theChallengeIndex) <= 1) {
            // draw the minigame icon on the button
            if (aChallengeButton.mDisabled) {
                g.setColor(new Color(92, 92, 92));
                g.setColorizeImages(true);
            }
            if (theChallengeIndex == mUnlockChallengeIndex) {
                if (mUnlockState == UnlockingState.UNLOCK_SHAKING) {
                    g.setColor(new Color(92, 92, 92));
                } else if (mUnlockState == UnlockingState.UNLOCK_FADING) {
                    int aColor = PvzpCommon.pvzpAnimateCurve(50, 25, mUnlockStateCounter, 92, 255, PvzpCurves.CURVE_LINEAR);
                    g.setColor(new Color(aColor, aColor, aColor));
                }
                g.setColorizeImages(true);
            }

            if (mPageIndex == ChallengePage.CHALLENGE_PAGE_SURVIVAL) {
                g.drawImageCel(Resources.getImageById(Resources.IMAGE_SURVIVAL_THUMBNAILS_ID),
                        aPosX + 13, aPosY + 4, aDef.mChallengeIconIndex);
            } else {
                g.drawImageCel(Resources.getImageById(Resources.IMAGE_CHALLENGE_THUMBNAILS_ID),
                        aPosX + 13, aPosY + 4, aDef.mChallengeIconIndex);
            }

            // draw the button frame
            boolean aHighLight = aChallengeButton.mIsOver && theChallengeIndex != mUnlockChallengeIndex;
            g.setColorizeImages(false);
            g.drawImage(Resources.getImageById(aHighLight ? Resources.IMAGE_CHALLENGE_WINDOW_ID
                    : Resources.IMAGE_CHALLENGE_WINDOW_HIGHLIGHT_ID), aPosX - 6, aPosY - 2);

            // draw the challenge name
            Color aTextColor = aHighLight ? new Color(250, 40, 40) : new Color(42, 42, 90);
            String aName = PvzpStringFile.translate(aDef.mChallengeName);
            if (aChallengeButton.mDisabled
                    || (theChallengeIndex == mUnlockChallengeIndex && mUnlockState == UnlockingState.UNLOCK_SHAKING)) {
                aName = "?";
            }

            int aNameCharLen = aName.codePointCount(0, aName.length());
            int aAutoWrapNum = mApp.getInteger("CHALLENGE_SCREEN_BUTTON_AUTO_WRAP_NUM", 13);
            Font aFont = Resources.getFontById(Resources.FONT_BRIANNETOD12_ID);
            if (aNameCharLen < aAutoWrapNum) {
                PvzpCommon.pvzpDrawString(g, aName, aPosX + 52, aPosY + 96, aFont, aTextColor,
                        DrawStringJustification.DS_ALIGN_CENTER);
            } else {
                // Split at a space in the second half, else any space; if none, the whole name stays on one line.
                int aHalfPosChar = (mPageIndex == ChallengePage.CHALLENGE_PAGE_SURVIVAL && !aChallengeButton.mDisabled)
                        ? 7 : (aNameCharLen / 2 - 1);
                int aSplitPos = -1;
                int aFallbackSpacePos = -1;
                int aCharIdx = 0;
                for (int aOffset = 0; aOffset < aName.length(); aCharIdx++) {
                    int aCharStart = aOffset;
                    int aChar = aName.codePointAt(aOffset);
                    if (aChar == ' ') {
                        if (aCharIdx >= aHalfPosChar) {
                            aSplitPos = aCharStart;
                            break;
                        }
                        if (aFallbackSpacePos == -1) {
                            aFallbackSpacePos = aCharStart;
                        }
                    }
                    aOffset += Character.charCount(aChar);
                }
                if (aSplitPos == -1) {
                    aSplitPos = aFallbackSpacePos;
                }

                String aLine1;
                String aLine2;
                if (aSplitPos != -1) {
                    aLine1 = aName.substring(0, aSplitPos);
                    int aLine2Offset = aName.charAt(aSplitPos) == ' ' ? aSplitPos + 1 : aSplitPos;
                    aLine2 = aName.substring(aLine2Offset);
                } else {
                    aLine1 = aName;
                    aLine2 = "";
                }

                PvzpCommon.pvzpDrawString(g, aLine1, aPosX + 52, aPosY + 88, aFont, aTextColor,
                        DrawStringJustification.DS_ALIGN_CENTER);
                if (!aLine2.isEmpty()) {
                    PvzpCommon.pvzpDrawString(g, aLine2, aPosX + 52, aPosY + 102, aFont, aTextColor,
                            DrawStringJustification.DS_ALIGN_CENTER);
                }
            }

            // draw the lock or completion images and the best record text
            int aRecord = mApp.mPlayerInfo.mChallengeRecords[theChallengeIndex];
            if (theChallengeIndex == mUnlockChallengeIndex) {
                Image aLockImage = Resources.getImageById(Resources.IMAGE_LOCK_ID);
                if (mUnlockState == UnlockingState.UNLOCK_FADING) {
                    aLockImage = Resources.getImageById(Resources.IMAGE_LOCK_OPEN_ID);
                    g.setColor(new Color(255, 255, 255,
                            PvzpCommon.pvzpAnimateCurve(25, 0, mUnlockStateCounter, 255, 0, PvzpCurves.CURVE_LINEAR)));
                    g.setColorizeImages(true);
                }
                PvzpCommon.pvzpDrawImageScaledF(g, aLockImage, aPosX + 24 + mLockShakeX, aPosY + 9 + mLockShakeY,
                        0.7f, 0.7f);
                g.setColorizeImages(false);
            } else if (aRecord > 0) {
                if (mApp.hasBeatenChallenge(aDef.mChallengeMode)) {
                    g.drawImage(Resources.getImageById(Resources.IMAGE_MINIGAME_TROPHY_ID), aPosX - 6, aPosY - 2);
                } else if (LawnApp.isEndlessScaryPotter(aDef.mChallengeMode)
                        || LawnApp.isEndlessIZombie(aDef.mChallengeMode)) {
                    String aAchievement = LawnApp.pluralize(aRecord, "[ONE_FLAG]", "[COUNT_FLAGS]");
                    Font aFlagFont = Resources.getFontById(Resources.FONT_CONTINUUMBOLD14OUTLINE_ID);
                    PvzpCommon.pvzpDrawString(g, aAchievement, aPosX + 48, aPosY + 48, aFlagFont, Color.White,
                            DrawStringJustification.DS_ALIGN_CENTER);
                    PvzpCommon.pvzpDrawString(g, aAchievement, aPosX + 48, aPosY + 48,
                            Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID), new Color(255, 0, 0),
                            DrawStringJustification.DS_ALIGN_CENTER);
                } else if (LawnApp.isSurvivalEndless(aDef.mChallengeMode)) {
                    String aAchievement = PvzpStringFile.translate("[LONGEST_STREAK]")
                            .replace("{STREAK}", Integer.toString(aRecord));
                    Rect aRect = new Rect(aPosX, aPosY + 15, 96, 200);
                    drawStringWrapped(g, aAchievement, aRect, Resources.getFontById(Resources.FONT_CONTINUUMBOLD14OUTLINE_ID),
                            Color.White);
                    drawStringWrapped(g, aAchievement, aRect, Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID),
                            new Color(255, 0, 0));
                }
            } else if (aChallengeButton.mDisabled) {
                PvzpCommon.pvzpDrawImageScaledF(g, Resources.getImageById(Resources.IMAGE_LOCK_ID),
                        aPosX + 24, aPosY + 9, 0.7f, 0.7f);
            }
        } else {
            g.drawImage(Resources.getImageById(Resources.IMAGE_CHALLENGE_BLANK_ID), aPosX, aPosY);
        }
    }

    /** Upstream {@code PvzpDrawStringWrapped} (ChallengeScreen.cpp:551-555) with the active font/colour. */
    private void drawStringWrapped(Graphics g, String theText, Rect theRect, Font theFont, Color theColor) {
        g.setFont(theFont);
        g.setColor(theColor);
        g.writeWordWrapped(theRect, PvzpStringFile.translate(theText), -1,
                DrawStringJustification.DS_ALIGN_CENTER, null, -1, null);
    }

    // ChallengeScreen::Draw (ChallengeScreen.cpp:568-592)
    @Override
    public void draw(Graphics g) {
        g.setLinearBlend(true);
        g.drawImage(Resources.getImageById(Resources.IMAGE_CHALLENGE_BACKGROUND_ID), 0, 0);

        String aTitleString = mPageIndex == ChallengePage.CHALLENGE_PAGE_SURVIVAL ? "[PICK_AREA]"
                : mPageIndex == ChallengePage.CHALLENGE_PAGE_PUZZLE ? "[SCARY_POTTER]" : "[PICK_CHALLENGE]";
        PvzpCommon.pvzpDrawString(g, aTitleString, 400, 58, Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID),
                new Color(220, 220, 220), DrawStringJustification.DS_ALIGN_CENTER);

        int aTrophiesGot = mApp.getNumTrophies(mPageIndex);
        int aTrophiesTotal = mPageIndex == ChallengePage.CHALLENGE_PAGE_SURVIVAL ? 10
                : mPageIndex == ChallengePage.CHALLENGE_PAGE_CHALLENGE ? 20
                : mPageIndex == ChallengePage.CHALLENGE_PAGE_PUZZLE ? 18 : 0;
        if (aTrophiesTotal > 0) {
            String aTrophyString = aTrophiesGot + "/" + aTrophiesTotal;
            PvzpCommon.pvzpDrawString(g, aTrophyString, 739, 73, Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID),
                    new Color(255, 240, 0), DrawStringJustification.DS_ALIGN_CENTER);
        }
        PvzpCommon.pvzpDrawImageScaledF(g, Resources.getImageById(Resources.IMAGE_TROPHY_ID), 718, 26, 0.5f, 0.5f);

        for (int aChallengeMode = 0; aChallengeMode < NUM_CHALLENGE_MODES; aChallengeMode++) {
            drawButton(g, aChallengeMode);
        }

        mToolTip.draw(g);
    }

    // ChallengeScreen::Update (ChallengeScreen.cpp:594-622)
    @Override
    public void update() {
        super.update();
        updateToolTip();

        if (mUnlockStateCounter > 0) {
            mUnlockStateCounter--;
        }
        if (mUnlockState == UnlockingState.UNLOCK_SHAKING) {
            if (mUnlockStateCounter == 0) {
                mApp.playFoley(FoleyType.FOLEY_PAPER);
                mUnlockState = UnlockingState.UNLOCK_FADING;
                mUnlockStateCounter = 50;
                mLockShakeX = 0;
                mLockShakeY = 0;
            } else {
                mLockShakeX = PvzpCommon.randRangeFloat(-2, 2);
                mLockShakeY = PvzpCommon.randRangeFloat(-2, 2);
            }
        } else if (mUnlockState == UnlockingState.UNLOCK_FADING && mUnlockStateCounter == 0) {
            mUnlockState = UnlockingState.UNLOCK_OFF;
            mUnlockStateCounter = 0;
            mUnlockChallengeIndex = -1;
        }

        markDirty();
    }

    // ChallengeScreen::AddedToManager (ChallengeScreen.cpp:624-631)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mBackButton);
        for (ButtonWidget aButton : mPageButton) {
            addWidget(aButton);
        }
        for (ButtonWidget aButton : mChallengeButtons) {
            addWidget(aButton);
        }
    }

    // ChallengeScreen::RemovedFromManager (ChallengeScreen.cpp:633-640)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mBackButton);
        for (ButtonWidget aButton : mPageButton) {
            removeWidget(aButton);
        }
        for (ButtonWidget aButton : mChallengeButtons) {
            removeWidget(aButton);
        }
    }

    // ChallengeScreen::ButtonPress (ChallengeScreen.cpp:642-645)
    @Override
    public void buttonPress(int theId) {
        mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
    }

    @Override
    public void buttonDownTick(int theId) {
    }

    @Override
    public void buttonMouseEnter(int theId) {
    }

    @Override
    public void buttonMouseLeave(int theId) {
    }

    @Override
    public void buttonMouseMove(int theId, int theX, int theY) {
    }

    // ChallengeScreen::ButtonDepress (ChallengeScreen.cpp:646-667)
    @Override
    public void buttonDepress(int theId) {
        if (theId == ChallengeScreen_Back) {
            mApp.killChallengeScreen();
            mApp.doBackToMain();
            return;
        }

        int aChallengeMode = theId - ChallengeScreen_Mode;
        if (aChallengeMode >= 0 && aChallengeMode < NUM_CHALLENGE_MODES) {
            mApp.killChallengeScreen();
            mApp.preNewGame(aChallengeMode + 1, true);
            return;
        }

        int aPageIndex = theId - ChallengeScreen_Page;
        if (aPageIndex >= 0 && aPageIndex < ChallengePage.MAX_CHALLANGE_PAGES) {
            mPageIndex = aPageIndex;
            updateButtons();
        }
    }

    // ChallengeScreen::KeyDown (ChallengeScreen.cpp:669-675)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            buttonDepress(ChallengeScreen_Back);
        }
    }

    // ChallengeScreen::UpdateToolTip (ChallengeScreen.cpp:677-749)
    public void updateToolTip() {
        if (!mApp.mWidgetManager.mMouseIn || !mApp.mActive) {
            mToolTip.mVisible = false;
            return;
        }

        for (int aChallengeMode = 0; aChallengeMode < NUM_CHALLENGE_MODES; aChallengeMode++) {
            ChallengeDefinition aDef = getChallengeDefinition(aChallengeMode);
            ButtonWidget aChallengeButton = mChallengeButtons[aChallengeMode];
            if (aChallengeButton.mVisible && aChallengeButton.mDisabled
                    && aChallengeButton.contains(mApp.mWidgetManager.mLastMouseX, mApp.mWidgetManager.mLastMouseY)
                    && accomplishmentsNeeded(aChallengeMode) <= 1) {
                mToolTip.mX = aChallengeButton.mWidth / 2 + aChallengeButton.mX;
                mToolTip.mY = aChallengeButton.mY;

                if (moreTrophiesNeeded(aChallengeMode) > 0) {
                    String aLabel;
                    if (mPageIndex == ChallengePage.CHALLENGE_PAGE_PUZZLE) {
                        if (isScaryPotterLevel(aDef.mChallengeMode)) {
                            if (!mApp.hasFinishedAdventure() && aDef.mChallengeMode == GameMode.GAMEMODE_SCARY_POTTER_4) {
                                aLabel = "[FINISH_ADVENTURE_TOOLTIP]";
                            } else {
                                aLabel = "[ONE_MORE_SCARY_POTTER_TOOLTIP]";
                            }
                        } else if (isIZombieLevel(aDef.mChallengeMode)) {
                            if (!mApp.hasFinishedAdventure() && aDef.mChallengeMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4) {
                                aLabel = "[FINISH_ADVENTURE_TOOLTIP]";
                            } else {
                                aLabel = "[ONE_MORE_IZOMBIE_TOOLTIP]";
                            }
                        } else {
                            continue;
                        }
                    } else if (!mApp.hasFinishedAdventure() || mApp.isTrialStageLocked()) {
                        aLabel = "[FINISH_ADVENTURE_TOOLTIP]";
                    } else if (LawnApp.isSurvivalEndless(aDef.mChallengeMode)) {
                        aLabel = "[10_SURVIVAL_TOOLTIP]";
                    } else if (mPageIndex == ChallengePage.CHALLENGE_PAGE_SURVIVAL) {
                        aLabel = "[ONE_MORE_SURVIVAL_TOOLTIP]";
                    } else if (mPageIndex == ChallengePage.CHALLENGE_PAGE_CHALLENGE) {
                        aLabel = "[ONE_MORE_CHALLENGE_TOOLTIP]";
                    } else {
                        continue;
                    }

                    mToolTip.setLabel(aLabel);
                    mToolTip.mVisible = true;
                    return;
                }
            }
        }

        mToolTip.mVisible = false;
    }

    // ChallengeScreen::MouseDown (ChallengeScreen.cpp:751-771)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        super.mouseDown(x, y, theClickCount);

        if (mLimboPageUnlocked) {
            return;
        }

        final int MAX_GAP_TICKS = 20; // 200 ms at 100 update ticks/sec
        final int CLICKS_NEEDED = 5;

        int aNow = mApp.mUpdateCount;
        if (aNow - mLastClickUpdateCnt > MAX_GAP_TICKS) {
            mClickCount = 0;
        }
        mLastClickUpdateCnt = aNow;
        mClickCount++;
        if (mClickCount >= CLICKS_NEEDED) {
            mLimboPageUnlocked = true;
            updateButtons();
        }
    }
}
