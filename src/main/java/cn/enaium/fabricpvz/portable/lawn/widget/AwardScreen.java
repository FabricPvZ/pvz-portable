/*
 * Ported from src/Lawn/Widget/AwardScreen.h:34-84 and src/Lawn/Widget/AwardScreen.cpp:1-657.
 *
 * The "you got a new plant / found a note / got a trophy" screen shown after an adventure level or
 * a challenge, and the achievement-unlock overlay it can host. Upstream it is a plain Sexy::Widget
 * whose three GameButtons are drawn inside its own Draw() and hit-tested manually (no
 * ButtonListener).
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.AchievementId;
import cn.enaium.fabricpvz.portable.lawn.AwardType;
import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/AwardScreen.h:34-84, AwardScreen.cpp:32-657.
 *
 * <p>Hosts the award moment: the per-award background/art, the start/menu/continue buttons, and the
 * achievement slide-in animation when constructed with {@code theShowingAchievements}.
 */
public class AwardScreen extends Widget {

    /** Ported from src/Lawn/Widget/AwardScreen.h:36-40 (AwardScreen_Start / AwardScreen_Menu). */
    public static final int AwardScreen_Start = 100;
    public static final int AwardScreen_Menu = 101;

    /**
     * Ported from the default button layout (AwardScreen.cpp:147,155,175).
     *
     * <p>{@code mStartButton} is moved by the credits variant and the paper-note case in the
     * constructor; these are the rectangles as first resized. {@code mContinueButton} shares the
     * start button's id upstream (AwardScreen.cpp:155).
     */
    public static final Rect START_BUTTON_RECT = new Rect(324, 500, 156, 42);
    public static final Rect CONTINUE_BUTTON_RECT = new Rect(324, 515, 156, 42);
    public static final Rect MENU_BUTTON_RECT = new Rect(677, 16, 111, 26);

    /** Ported from src/Lawn/Widget/AwardScreen.h:29-36 (AchievementScreenItem). */
    public static class AchievementScreenItem {
        public int mId;
        public int mStartAnimTime;
        public int mEndAnimTime;
        public int mDestY;
        public int mStartY;
        public int mY;
    }

    // AwardScreen.h:43-55
    public GameButton mStartButton;
    public GameButton mMenuButton;
    public LawnApp mApp;
    public int mFadeInCounter;
    public int mAwardType;
    public GameButton mContinueButton;
    public boolean mShowStartButtonAfterAchievements;
    public boolean mShowMenuButtonAfterAchievements;
    public int mAchievementAnimTime;
    public boolean mShowingAchievements;
    public final List<AchievementScreenItem> mAchievementItems = new ArrayList<>();

    // AwardScreen::AwardScreen (AwardScreen.cpp:32-253)
    public AwardScreen(LawnApp theApp, int theAwardType, boolean theShowingAchievements) {
        mApp = theApp;
        mClip = false;
        mFadeInCounter = 180;
        mAchievementAnimTime = 0;
        mAwardType = theAwardType;
        mShowingAchievements = theShowingAchievements;

        mLoadedResourceNames.add("DelayLoad_AwardScreen");

        if (!theShowingAchievements) {
            mShowingAchievements = false;
        } else {
            mLoadedResourceNames.add("DelayLoad_ChallengeScreen");

            for (int i = 0; i < AchievementId.MAX_ACHIEVEMENTS; i++) {
                if (mApp.mPlayerInfo.mEarnedAchievements[i] && !mApp.mPlayerInfo.mShownAchievements[i]) {
                    mApp.mPlayerInfo.mShownAchievements[i] = true;

                    AchievementScreenItem aAchievementItem = new AchievementScreenItem();
                    aAchievementItem.mId = i;
                    aAchievementItem.mStartAnimTime = 100 * mAchievementItems.size() + 150;
                    aAchievementItem.mEndAnimTime = aAchievementItem.mStartAnimTime + 100;
                    aAchievementItem.mStartY = 750;
                    aAchievementItem.mY = aAchievementItem.mStartY;
                    mAchievementItems.add(aAchievementItem);
                }
            }

            if (!mAchievementItems.isEmpty()) {
                int aDestY = 284 - ((76 * mAchievementItems.size()) / 2);

                for (AchievementScreenItem aAchievementItem : mAchievementItems) {
                    aAchievementItem.mDestY = aDestY;
                    aDestY += 76;
                }
            } else {
                mShowingAchievements = false;
            }

            mApp.writeCurrentUserConfig();
        }

        int aLevel = mApp.mPlayerInfo.getLevel();
        if (mAwardType == AwardType.AWARD_CREDITS_ZOMBIENOTE) {
            mLoadedResourceNames.add("DelayLoad_Background6");
            mLoadedResourceNames.add("DelayLoad_ZombieNote");
            mLoadedResourceNames.add("DelayLoad_Credits");
        } else if (mAwardType == AwardType.AWARD_HELP_ZOMBIENOTE) {
            mLoadedResourceNames.add("DelayLoad_Background1");
            mLoadedResourceNames.add("DelayLoad_ZombieNote");
            mLoadedResourceNames.add("DelayLoad_ZombieNoteHelp");
        } else if (mApp.isAdventureMode()) {
            if (aLevel == 10) {
                mLoadedResourceNames.add("DelayLoad_Background1");
                mLoadedResourceNames.add("DelayLoad_ZombieNote");
                mLoadedResourceNames.add("DelayLoad_ZombieNote1");
            } else if (aLevel == 20) {
                mLoadedResourceNames.add("DelayLoad_Background2");
                mLoadedResourceNames.add("DelayLoad_ZombieNote");
                mLoadedResourceNames.add("DelayLoad_ZombieNote2");
            } else if (aLevel == 30) {
                mLoadedResourceNames.add("DelayLoad_Background1");
                mLoadedResourceNames.add("DelayLoad_ZombieNote");
                mLoadedResourceNames.add("DelayLoad_ZombieNote3");
            } else if (aLevel == 40) {
                mLoadedResourceNames.add("DelayLoad_Background2");
                mLoadedResourceNames.add("DelayLoad_ZombieNote");
                mLoadedResourceNames.add("DelayLoad_ZombieNote4");
            } else if (aLevel == 50) {
                mLoadedResourceNames.add("DelayLoad_Background1");
                mLoadedResourceNames.add("DelayLoad_ZombieNote");
                mLoadedResourceNames.add("DelayLoad_ZombieFinalNote");
            }
        }

        for (String resource : mLoadedResourceNames) {
            Resources.extractResourcesByName(mApp.mResourceManager, resource);
        }

        // AwardScreen.cpp:134-135: the groups registered above are loaded right away, before any widget reads its
        // images - without this the screen drew null images for everything they provide.
        for (String aResource : mLoadedResourceNames) {
            ((cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(aResource);
        }

        mStartButton = new GameButton(AwardScreen_Start);
        mStartButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON);
        mStartButton.mOverImage = null;
        mStartButton.mDownImage = null;
        mStartButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_DISABLED);
        mStartButton.mOverOverlayImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_GLOW);
        mStartButton.setFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID));
        mStartButton.setLabelColor(new Color(213, 159, 43));
        mStartButton.setLabelHiliteColor(new Color(213, 159, 43));
        mStartButton.resize(START_BUTTON_RECT.mX, START_BUTTON_RECT.mY, START_BUTTON_RECT.mWidth, START_BUTTON_RECT.mHeight);
        mStartButton.mTextOffsetY = -1;

        mContinueButton = new GameButton(AwardScreen_Start);
        mContinueButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON);
        mContinueButton.mOverImage = null;
        mContinueButton.mDownImage = null;
        mContinueButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_DISABLED);
        mContinueButton.mOverOverlayImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_GLOW);
        mContinueButton.setFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID));
        mContinueButton.setLabelColor(new Color(213, 159, 43));
        mContinueButton.setLabelHiliteColor(new Color(213, 159, 43));
        mContinueButton.resize(CONTINUE_BUTTON_RECT.mX, CONTINUE_BUTTON_RECT.mY, CONTINUE_BUTTON_RECT.mWidth,
                CONTINUE_BUTTON_RECT.mHeight);
        mContinueButton.mParentWidget = this;
        mContinueButton.mTextOffsetY = -1;
        mContinueButton.mBtnNoDraw = true;
        mContinueButton.mDisabled = true;

        mMenuButton = new GameButton(AwardScreen_Menu);
        mMenuButton.setLabel("[AWARD_MAIN_MENU_BUTTON]");
        mMenuButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON2);
        mMenuButton.mOverImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON2_GLOW);
        mMenuButton.mDownImage = null;
        mMenuButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        mMenuButton.setLabelColor(new Color(42, 42, 90));
        mMenuButton.setLabelHiliteColor(new Color(42, 42, 90));
        mMenuButton.mParentWidget = this;
        mMenuButton.resize(MENU_BUTTON_RECT.mX, MENU_BUTTON_RECT.mY, MENU_BUTTON_RECT.mWidth, MENU_BUTTON_RECT.mHeight);
        mMenuButton.mTextOffsetY = 1;
        if (!mApp.hasFinishedAdventure() && aLevel <= 3) {
            mMenuButton.mBtnNoDraw = true;
            mMenuButton.mDisabled = true;
        }

        mStartButton.setLabel(resolveStartButtonLabel(mAwardType, aLevel, mApp.isAdventureMode(),
                mApp.hasFinishedAdventure()));

        if (mAwardType == AwardType.AWARD_CREDITS_ZOMBIENOTE) {
            mStartButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_CREDITS_PLAYBUTTON);
            mStartButton.mOverImage = null;
            mStartButton.mDownImage = null;
            mStartButton.mDisabledImage = null;
            mStartButton.mOverOverlayImage = null;
            mStartButton.setFont(Resources.getFontById(Resources.FONT_HOUSEOFTERROR20_ID));
            mStartButton.setLabelColor(new Color(255, 255, 255));
            mStartButton.setLabelHiliteColor(new Color(213, 159, 43));
            mStartButton.resize(325, 505, 190, 73);
            mStartButton.mTextOffsetX = 33;
            mStartButton.mTextOffsetY = -2;
            mStartButton.mButtonOffsetX = -2;
            mStartButton.mButtonOffsetY = 8;
            mStartButton.mParentWidget = this;
        } else if (mAwardType == AwardType.AWARD_HELP_ZOMBIENOTE) {
            mMenuButton.mBtnNoDraw = true;
            mMenuButton.mDisabled = true;
        } else if (!mApp.isAdventureMode()) {
            mMenuButton.mBtnNoDraw = true;
            mMenuButton.mDisabled = true;
        } else if (aLevel == 1 && mApp.hasFinishedAdventure()) {
            ReportAchievement.giveAchievement(mApp, AchievementId.HomeSecurity, false);
            mMenuButton.mBtnNoDraw = true;
            mMenuButton.mDisabled = true;
        }

        if (mApp.isAdventureMode() && mApp.earnedGoldTrophy()) {
            ReportAchievement.giveAchievement(mApp, AchievementId.NovelPeasPrize, false);
        }

        if (mApp.isFirstTimeAdventureMode() && aLevel == 25 && mApp.isTrialStageLocked()
                && mApp.mPlayerInfo.mHasSeenUpsell == 0) {
            mMenuButton.mBtnNoDraw = true;
            mMenuButton.mDisabled = true;
        }

        if (mShowingAchievements) {
            mShowStartButtonAfterAchievements = !mStartButton.mBtnNoDraw;
            mShowMenuButtonAfterAchievements = !mMenuButton.mBtnNoDraw;
            mStartButton.mBtnNoDraw = true;
            mStartButton.mDisabled = true;
            mMenuButton.mBtnNoDraw = true;
            mMenuButton.mDisabled = true;
            mContinueButton.setLabel("[CONTINUE_BUTTON]");
        }

        if (isPaperNote()) {
            mApp.mMusic.stopAllMusic();
            mStartButton.mY += 20;
            mApp.playFoley(FoleyType.FOLEY_PAPER);
        } else {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_ZEN_GARDEN);
        }

    }

    /**
     * Ported from AwardScreen.cpp:184-243 (the per-award/level label table).
     *
     * <p>Pure helper: returns the {@code mStartButton} label key for the given award/level. The
     * {@code !adventureMode} branch precedes the level-15 almanac branch, exactly as upstream.
     */
    public static String resolveStartButtonLabel(int awardType, int level, boolean adventureMode,
            boolean finishedAdventure) {
        if (awardType == AwardType.AWARD_CREDITS_ZOMBIENOTE) {
            return "[ROLL_CREDITS]";
        } else if (awardType == AwardType.AWARD_HELP_ZOMBIENOTE) {
            return "[MAIN_MENU_BUTTON]";
        } else if (!adventureMode) {
            return "[MAIN_MENU_BUTTON]";
        } else if (level == 1 && finishedAdventure) {
            return "[CONTINUE_BUTTON]";
        } else if (level == 15) {
            return "[VIEW_ALMANAC_BUTTON]";
        } else if (level == 25 || level == 35 || level == 45) {
            return "[CONTINUE_BUTTON]";
        } else {
            return "[NEXT_LEVEL_BUTTON]";
        }
    }

    // AwardScreen::IsPaperNote (AwardScreen.cpp:257-265)
    public boolean isPaperNote() {
        return isPaperNote(mAwardType, mApp.mPlayerInfo.getLevel(), mApp.isAdventureMode());
    }

    /**
     * Ported from AwardScreen.cpp:257-265. The credits/help zombie notes are always paper; an
     * adventure-mode level ending in 10/20/30/40/50 shows a note instead of a seed.
     */
    public static boolean isPaperNote(int awardType, int level, boolean adventureMode) {
        if (awardType == AwardType.AWARD_CREDITS_ZOMBIENOTE || awardType == AwardType.AWARD_HELP_ZOMBIENOTE) {
            return true;
        }

        return adventureMode && (level == 10 || level == 20 || level == 30 || level == 40 || level == 50);
    }

    // AwardScreen::DrawBottom (AwardScreen.cpp:268-274)
    public static void drawBottom(Graphics g, String theTitle, String theAward, String theMessage) {
        g.drawImage(Resources.getImageById(ImageID.IMAGE_AWARDSCREEN_BACK), 0, 0);
        PvzpCommon.pvzpDrawString(g, theTitle, GameConstants.BOARD_WIDTH / 2, 58,
                Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID), new Color(213, 159, 43),
                DrawStringJustification.DS_ALIGN_CENTER);
        PvzpCommon.pvzpDrawString(g, theAward, GameConstants.BOARD_WIDTH / 2, 326,
                Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID), Color.White,
                DrawStringJustification.DS_ALIGN_CENTER);
        PvzpStringFile.pvzpDrawStringWrapped(g, theMessage, new Rect(285, 360, 230, 90),
                Resources.getFontById(Resources.FONT_BRIANNETOD16_ID), new Color(40, 50, 90),
                DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
    }

    // AwardScreen::DrawAwardSeed (AwardScreen.cpp:277-290)
    public void drawAwardSeed(Graphics g) {
        int aSeedType = LawnApp.getAwardSeedForLevel(mApp.mPlayerInfo.getLevel() - 1);
        String aAward = Plant.getNameString(aSeedType, SeedType.SEED_NONE);
        String aMessage;
        if (mApp.isTrialStageLocked() && aSeedType >= SeedType.SEED_SQUASH && aSeedType != SeedType.SEED_TANGLEKELP) {
            aMessage = "[AVAILABLE_IN_FULL_VERSION]";
        } else {
            aMessage = Plant.getToolTip(aSeedType);
        }
        drawBottom(g, "[NEW_PLANT]", aAward, aMessage);

        g.setScale(2, 2, 350, 129);
        SeedPacket.drawSeedPacket(g, 350, 129, aSeedType, SeedType.SEED_NONE, 0, 255, true, false);
        g.setScale(1, 1, 0, 0);
    }

    // AwardScreen::Draw (AwardScreen.cpp:292-423)
    @Override
    public void draw(Graphics g) {
        g.setLinearBlend(true);

        int aLevel = mApp.mPlayerInfo.getLevel();
        if (mShowingAchievements) {
            drawAchievements(g);
        } else if (mAwardType == AwardType.AWARD_CREDITS_ZOMBIENOTE) {
            g.setColor(new Color(125, 200, 255, 255));
            g.setColorizeImages(true);
            g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND6BOSS), -900, -400, 2800, 1200);
            g.setColorizeImages(false);
            g.setColor(new Color(0, 0, 0, 64));
            g.fillRect(0, 525, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE), 75, 60);
            g.drawImage(Resources.getImageById(ImageID.IMAGE_CREDITS_ZOMBIENOTE), 149, 103, 475, 325);
        } else if (mAwardType == AwardType.AWARD_HELP_ZOMBIENOTE) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1), -700, -300, 2800, 1200);
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE), 80, 80);
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE_HELP), 131, 132);
        } else if (mAwardType != AwardType.AWARD_ACHIEVEMENTONLY) {
            if (!mApp.isAdventureMode()) {
                if (mApp.earnedGoldTrophy()) {
                    drawBottom(g, "[BEAT_GAME_MESSAGE1]", "[GOLD_SUNFLOWER_TROPHY]", "[BEAT_GAME_MESSAGE2]");
                    PvzpCommon.pvzpDrawImageCelCenterScaledF(g,
                            Resources.getImageById(ImageID.IMAGE_SUNFLOWER_TROPHY), 325, 65, 1, 0.6f, 0.6f);
                } else {
                    String aMsgChar;
                    if (mApp.isSurvivalMode()) {
                        int aNumTrophies = mApp.getNumTrophies(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
                        aMsgChar = aNumTrophies <= 7 ? "[YOU_UNLOCKED_A_SURVIVAL]"
                                : aNumTrophies == 10 ? "[YOU_UNLOCKED_ENDLESS_SURVIVAL]"
                                        : "[EARN_MORE_TROPHIES_FOR_ENDLESS_SURVIVAL]";
                    } else if (mApp.isScaryPotterLevel()) {
                        aMsgChar = "[UNLOCKED_VASEBREAKER_LEVEL]";
                    } else if (mApp.isPuzzleMode()) {
                        aMsgChar = "[UNLOCKED_I_ZOMBIE_LEVEL]";
                    } else {
                        aMsgChar = mApp.getNumTrophies(ChallengePage.CHALLENGE_PAGE_CHALLENGE) <= 17
                                ? "[CHALLENGE_UNLOCKED]" : "[GET_MORE_TROPHIES]";
                    }

                    drawBottom(g, "[GOT_TROPHY]", "[TROPHY]", aMsgChar);
                    Image aTrophyImage = Resources.getImageById(ImageID.IMAGE_TROPHY_HI_RES);
                    g.drawImage(aTrophyImage, GameConstants.BOARD_WIDTH / 2 - aTrophyImage.getWidth() / 2, 137);
                }
            } else if (aLevel == 5) {
                drawBottom(g, "[GOT_SHOVEL]", "[SHOVEL]", "[SHOVEL_DESCRIPTION]");
                Image aShovelImage = Resources.getImageById(ImageID.IMAGE_SHOVEL_HI_RES);
                g.drawImage(aShovelImage, GameConstants.BOARD_WIDTH / 2 - aShovelImage.getWidth() / 2, 137);
            } else if (aLevel == 10) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1), -700, -300, 2800, 1200);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE), 80, 80);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE1), 131, 132);
                PvzpCommon.pvzpDrawString(g, "[FOUND_NOTE]", GameConstants.BOARD_WIDTH / 2, 70,
                        Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID), new Color(255, 200, 0, 255),
                        DrawStringJustification.DS_ALIGN_CENTER);
            } else if (aLevel == 15) {
                drawBottom(g, "[FOUND_SUBURBAN_ALMANAC]", "[SUBURBAN_ALMANAC]", "[SUBURBAN_ALMANAC_DESCRIPTION]");
                Image anAlmanacImage =
                        Resources.getImageById(ImageID.IMAGE_ALMANAC);
                g.drawImage(anAlmanacImage, GameConstants.BOARD_WIDTH / 2 - anAlmanacImage.getWidth() / 2, 160);
            } else if (aLevel == 20) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND2), -700, -300, 2800, 1200);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE), 80, 80);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE2), 133, 127);
                PvzpCommon.pvzpDrawString(g, "[FOUND_NOTE]", GameConstants.BOARD_WIDTH / 2, 70,
                        Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID), new Color(255, 200, 0, 255),
                        DrawStringJustification.DS_ALIGN_CENTER);
            } else if (aLevel == 25) {
                drawBottom(g, "[FOUND_KEYS]", "[KEYS]", "[KEYS_DESCRIPTION]");
                Image aKeysImage = Resources.getImageById(ImageID.IMAGE_CARKEYS);
                g.drawImage(aKeysImage, GameConstants.BOARD_WIDTH / 2 - aKeysImage.getWidth() / 2, 160);
            } else if (aLevel == 30) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1), -700, -300, 2800, 1200);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE), 80, 80);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE3), 120, 117);
                PvzpCommon.pvzpDrawString(g, "[FOUND_NOTE]", GameConstants.BOARD_WIDTH / 2, 70,
                        Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID), new Color(255, 200, 0, 255),
                        DrawStringJustification.DS_ALIGN_CENTER);
            } else if (aLevel == 35) {
                drawBottom(g, "[FOUND_TACO]", "[TACO]", "[TACO_DESCRIPTION]");
                Image aTacoImage = Resources.getImageById(ImageID.IMAGE_TACO);
                g.drawImage(aTacoImage, GameConstants.BOARD_WIDTH / 2 - aTacoImage.getWidth() / 2, 160);
            } else if (aLevel == 40) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND2), -700, -300, 2800, 1200);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE), 80, 80);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE4), 102, 117);
                PvzpCommon.pvzpDrawString(g, "[FOUND_NOTE]", GameConstants.BOARD_WIDTH / 2, 70,
                        Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID), new Color(255, 200, 0, 255),
                        DrawStringJustification.DS_ALIGN_CENTER);
            } else if (aLevel == 45) {
                drawBottom(g, "[FOUND_WATERING_CAN]", "[WATERING_CAN]", "[WATERING_CAN_DESCRIPTION]");
                Image aCanImage = Resources.getImageById(ImageID.IMAGE_WATERINGCAN);
                g.drawImage(aCanImage, GameConstants.BOARD_WIDTH / 2 - aCanImage.getWidth() / 2, 160);
            } else if (aLevel == 50) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1), -700, -300, 2800, 1200);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_NOTE), 80, 80);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZOMBIE_FINAL_NOTE), 114, 138);
                PvzpCommon.pvzpDrawString(g, "[FOUND_NOTE]", GameConstants.BOARD_WIDTH / 2, 70,
                        Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID), new Color(255, 200, 0, 255),
                        DrawStringJustification.DS_ALIGN_CENTER);
            } else if (aLevel == 1 && mApp.hasFinishedAdventure()) {
                drawBottom(g, "[WIN_MESSAGE1]", "[SILVER_SUNFLOWER_TROPHY]", "[WIN_MESSAGE2]");
                PvzpCommon.pvzpDrawImageCelCenterScaledF(g,
                        Resources.getImageById(ImageID.IMAGE_SUNFLOWER_TROPHY), 325, 65, 0, 0.7f, 0.7f);
            } else {
                drawAwardSeed(g);
            }
        }

        mStartButton.draw(g);
        mMenuButton.draw(g);
        mContinueButton.draw(g);

        int aFadeInAlpha = PvzpCommon.pvzpAnimateCurve(180, 0, mFadeInCounter, 255, 0, PvzpCurves.CURVE_LINEAR);
        g.setColor(isPaperNote() ? new Color(0, 0, 0, aFadeInAlpha) : new Color(255, 255, 255, aFadeInAlpha));
        g.fillRect(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
    }

    // AwardScreen::Update (AwardScreen.cpp:425-456)
    @Override
    public void update() {
        super.update();
        if (mShowingAchievements) {
            mAchievementAnimTime++;

            for (int i = 0; i < mAchievementItems.size(); i++) {
                AchievementScreenItem anItem = mAchievementItems.get(i);
                if (mAchievementAnimTime >= anItem.mStartAnimTime) {
                    anItem.mY = PvzpCommon.pvzpAnimateCurve(anItem.mStartAnimTime, anItem.mEndAnimTime,
                            mAchievementAnimTime, anItem.mStartY, anItem.mDestY, PvzpCurves.CURVE_EASE_IN_OUT);
                }

                AchievementScreenItem aLastItem = mAchievementItems.get(mAchievementItems.size() - 1);
                if (aLastItem.mY == aLastItem.mDestY) {
                    mContinueButton.mBtnNoDraw = false;
                    mContinueButton.mDisabled = false;
                }
            }
        }

        if (mApp.getDialogCount() > 0) {
            return;
        }
        mStartButton.update();
        mMenuButton.update();
        mContinueButton.update();
        mApp.setCursor(mStartButton.isMouseOver() || mMenuButton.isMouseOver() || mContinueButton.isMouseOver()
                ? SexyAppBase.CURSOR_HAND : SexyAppBase.CURSOR_POINTER);
        markDirty();
        if (mFadeInCounter > 0) {
            mFadeInCounter--;
        }
    }

    // AwardScreen::KeyDown (AwardScreen.cpp:458-475)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN) {
            startButtonPressed();
            return;
        }

        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            if (!mMenuButton.mDisabled && !mMenuButton.mBtnNoDraw) {
                mApp.killAwardScreen();
                mApp.showGameSelector();
            } else {
                startButtonPressed();
            }
        }
    }

    // AwardScreen::StartButtonPressed (AwardScreen.cpp:477-574)
    public void startButtonPressed() {
        if (mApp.getDialog(Dialogs.DIALOG_STORE) != null) {
            return;
        }

        if (mAwardType == AwardType.AWARD_CREDITS_ZOMBIENOTE) {
            mApp.killAwardScreen();
            mApp.showCreditScreen();
        } else if (mAwardType == AwardType.AWARD_HELP_ZOMBIENOTE) {
            mApp.killAwardScreen();
            mApp.showGameSelector();
        } else if (mApp.isSurvivalMode()) {
            mApp.killAwardScreen();
            mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
        } else if (mApp.isPuzzleMode()) {
            mApp.killAwardScreen();
            mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
        } else if (mApp.isChallengeMode()) {
            mApp.killAwardScreen();
            mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
        } else {
            int aLevel = mApp.mPlayerInfo.getLevel();
            if (aLevel == 1) {
                mApp.killAwardScreen();
                if (mApp.hasFinishedAdventure()) {
                    mApp.showAwardScreen(AwardType.AWARD_CREDITS_ZOMBIENOTE, false);
                } else {
                    mApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
                }
            } else {
                if (aLevel == 15) {
                    // PORT-NOTE: AwardScreen.cpp:523-525 — upstream waits for the almanac it shows at
                    // level 15 (`DoAlmanacDialog()->WaitForResult()`) and only then runs the tail
                    // below; this port's wait does not pump (sexy/Dialog.java:506), so the tail is
                    // registered here and runs when the almanac closes. Running it on the spot used to
                    // start the next level under the almanac, which then closed itself unread.
                    mApp.doAlmanacDialog(SeedType.SEED_NONE, ZombieType.ZOMBIE_INVALID);
                    mApp.onAlmanacClosed(this::continueAdventureAfterStore);
                    return;
                } else if (aLevel == 25) {
                    StoreScreen aStore = mApp.showStoreScreen();
                    aStore.setupForIntro(301);
                    // PORT-NOTE: AwardScreen.cpp:526-546 — upstream reads mPurchasedFullVersion and
                    // checks the trial lock after WaitForResult(true) returned, i.e. after autoKill
                    // removed the store (Dialog.cpp:410-441); this port's wait does not pump
                    // (sexy/Dialog.java:506), so the whole tail runs from the store's close path.
                    aStore.onStoreClosed(store -> {
                        if (store.mPurchasedFullVersion) {
                            mApp.killAwardScreen();
                            mApp.showGameSelector();
                            return;
                        }
                        if (mApp.isTrialStageLocked()) {
                            mApp.killAwardScreen();
                            mApp.preNewGame(GameMode.GAMEMODE_UPSELL, false);
                            if (mApp.mPlayerInfo.mHasSeenUpsell == 0) {
                                mApp.mBoard.mStoreButton.mBtnNoDraw = true;
                                mApp.mPlayerInfo.mHasSeenUpsell = 1;
                            }
                            return;
                        }
                        continueAdventureAfterStore();
                    });
                    return;
                } else if (aLevel == 35) {
                    StoreScreen aStore = mApp.showStoreScreen();
                    aStore.setupForIntro(601);
                    aStore.onStoreClosed(store -> continueAdventureAfterStore());
                    return;
                } else if (aLevel == 42) {
                    StoreScreen aStore = mApp.showStoreScreen();
                    aStore.setupForIntro(3100);
                    aStore.onStoreClosed(store -> continueAdventureAfterStore());
                    return;
                } else if (aLevel == 45) {
                    mApp.killAwardScreen();
                    mApp.preNewGame(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, false);
                    mApp.mZenGarden.setupForZenTutorial();
                    return;
                }

                continueAdventureAfterStore();
            }
        }
    }

    /**
     * AwardScreen.cpp:568-572 — the reward screen's tail for levels 25/35/42, which upstream reaches
     * after the store screen closed (the {@code WaitForResult(true)} at :529/:553/:559). The port
     * runs it from {@link StoreScreen#onStoreClosed} for those levels, and directly for the levels
     * without a store (extracted only so both paths are one body).
     */
    private void continueAdventureAfterStore() {
        mApp.killAwardScreen();
        mApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
    }

    // AwardScreen::MouseDown (AwardScreen.cpp:576-583)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        if (theClickCount == 1) {
            if (mStartButton.isMouseOver() || mMenuButton.isMouseOver() || mContinueButton.isMouseOver()) {
                mApp.playSample(Resources.SOUND_TAP_ID);
            }
        }
    }

    // AwardScreen::MouseUp (AwardScreen.cpp:585-598)
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
        if (theClickCount == 1) {
            if (mStartButton.isMouseOver()) {
                startButtonPressed();
            }
            if (mContinueButton.isMouseOver()) {
                achievementsContinuePressed();
            }
            if (mMenuButton.isMouseOver()) {
                mApp.killAwardScreen();
                mApp.showGameSelector();
            }
        }
    }

    // AwardScreen::DrawAchievements (AwardScreen.cpp:600-630)
    public void drawAchievements(Graphics g) {
        g.setColorizeImages(true);
        g.setColor(new Color(255, 255, 255));
        g.fillRect(0, 0, mWidth, mHeight);
        g.setColorizeImages(false);

        g.drawImage(Resources.getImageById(ImageID.IMAGE_CHALLENGE_BACKGROUND), 0, 0);

        PvzpCommon.pvzpDrawString(g, mApp.getString("ACHIEVEMENTS_TITLE", "ACHIEVEMENTS"),
                GameConstants.BOARD_WIDTH / 2, 58, Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID),
                new Color(220, 220, 220), DrawStringJustification.DS_ALIGN_CENTER);

        for (AchievementScreenItem anItem : mAchievementItems) {
            String aAchievementName = mApp.getString(AchievementsScreen.gAchievementList[anItem.mId].name,
                    AchievementsScreen.gAchievementList[anItem.mId].name);
            String aAchievementDesc = mApp.getString(AchievementsScreen.gAchievementList[anItem.mId].description,
                    AchievementsScreen.gAchievementList[anItem.mId].description);
            aAchievementName = aAchievementName + " Earned!";

            Rect aSrcRect = new Rect(70 * (anItem.mId % 7), 70 * (anItem.mId / 7), 70, 70);
            Rect aDestRect = new Rect(220, anItem.mY + 10, 70, 70);
            Rect aTextRect = new Rect(300, anItem.mY + 20, 300, 60);

            g.drawImage(Resources.getImageById(ImageID.IMAGE_ACHEESEMENTS_ICONS), aDestRect, aSrcRect);

            PvzpCommon.pvzpDrawString(g, aAchievementName, 450, anItem.mY + 25,
                    Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID), new Color(224, 187, 98),
                    DrawStringJustification.DS_ALIGN_CENTER);
            PvzpStringFile.pvzpDrawStringWrapped(g, aAchievementDesc, aTextRect,
                    Resources.getFontById(Resources.FONT_DWARVENTODCRAFT12_ID), Color.White,
                    DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
        }
    }

    // AwardScreen::AchievementsContinuePressed (AwardScreen.cpp:632-657)
    public void achievementsContinuePressed() {
        if (mAwardType == AwardType.AWARD_ACHIEVEMENTONLY) {
            mApp.killAwardScreen();
            if (mApp.isAdventureMode()) {
                mApp.preNewGame(mApp.mGameMode, false);
            } else if (mApp.isSurvivalMode()) {
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
            } else if (mApp.isPuzzleMode()) {
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
            } else {
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
            }
        } else {
            mStartButton.mBtnNoDraw = !mShowStartButtonAfterAchievements;
            mStartButton.mDisabled = !mShowStartButtonAfterAchievements;
            mMenuButton.mBtnNoDraw = !mShowMenuButtonAfterAchievements;
            mMenuButton.mDisabled = !mShowMenuButtonAfterAchievements;
            mContinueButton.mDisabled = true;
            mContinueButton.mBtnNoDraw = true;
            mShowingAchievements = false;
            int level = mApp.mPlayerInfo.getLevel();
            if (mApp.isAdventureMode() && level == 1 && mApp.hasFinishedAdventure()) {
                mApp.killAwardScreen();
                mApp.showAwardScreen(AwardType.AWARD_CREDITS_ZOMBIENOTE, false);
            }
        }
    }

    // AwardScreen::AddedToManager / RemovedFromManager (AwardScreen.h:63-64)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
    }

    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
    }
}
