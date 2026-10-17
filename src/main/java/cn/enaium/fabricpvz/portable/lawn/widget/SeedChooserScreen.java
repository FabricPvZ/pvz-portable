/*
 * Ported from src/Lawn/Widget/SeedChooserScreen.cpp (1144 lines) and
 * src/Lawn/Widget/SeedChooserScreen.h:40-138.
 *
 * Pre-level plant selection screen ("Choose your plants" / "Let's Rock"). Selection is
 * click-to-toggle (ClickedSeedInChooser / ClickedSeedInBank); there is no drag & drop — see
 * docs/research/04-reanim-ui.md §1 "IMPORTANT — divergences".
 *
 * <p>PORT-NOTE: upstream draws one grid of seed packets, sized for the retail seed types, so a seed
 * type above them has no square to sit in. The port pages the grid instead: page 0 is the upstream
 * grid, every page after it shows the seed types above the retail range that have a definition and
 * that the player may pick ({@link #rebuildPageSeeds}), and the two arrows the screen draws next to
 * "Let's Rock" switch between the pages. Nothing else about the screen changes - the imitater keeps
 * its own button, the bank keeps its slots, and a seed that is not on the page on screen is simply
 * not drawn (it is still in the bank if it was picked).
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.AdviceType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ChosenSeedState;
import cn.enaium.fabricpvz.portable.lawn.CursorType;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.MessageStyle;
import cn.enaium.fabricpvz.portable.lawn.NotRecommend;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedChooserState;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.ToolTipWidget;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.lawn.StoreItem;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.lawn.system.Music;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;

public class SeedChooserScreen extends Widget {
    private static final int SEED_CHOOSER_SCREEN_START = 100;
    private static final int SEED_CHOOSER_SCREEN_RANDOM = 101;
    private static final int SEED_CHOOSER_SCREEN_VIEW_LAWN = 102;
    private static final int SEED_CHOOSER_SCREEN_ALMANAC = 103;
    private static final int SEED_CHOOSER_SCREEN_MENU = 104;
    private static final int SEED_CHOOSER_SCREEN_STORE = 105;
    private static final int SEED_CHOOSER_SCREEN_IMITATER = 106;
    private static final int SEED_CHOOSER_SCREEN_PREV_PAGE = 107;
    private static final int SEED_CHOOSER_SCREEN_NEXT_PAGE = 108;

    /** Chooser grid geometry, SeedChooserScreen.cpp:284-306. */
    public static final int CHOOSER_COLUMNS = 8;
    public static final int CHOOSER_COLUMN_PITCH = 53;
    public static final int CHOOSER_X_ORIGIN = 22;
    public static final int CHOOSER_ROW_PITCH_7_ROWS = 70;
    public static final int CHOOSER_Y_ORIGIN_7_ROWS = 123;
    public static final int CHOOSER_ROW_PITCH_6_ROWS = 73;
    public static final int CHOOSER_Y_ORIGIN_6_ROWS = 128;

    /**
     * Seed types one page of the grid holds: {@link #CHOOSER_COLUMNS} columns of six rows. The
     * imitater sits on its own button rather than in the grid (SeedChooserScreen.cpp:284-292), so page
     * 0 holds the retail seed types {@code 0 .. NUM_SEEDS_IN_CHOOSER-1} in those squares.
     */
    public static final int CHOOSER_PAGE_SIZE = CHOOSER_COLUMNS * 6;

    /**
     * The page arrows' geometry: one arrow square on either side of "Let's Rock" (154..310 by
     * 545..587), each drawn as a glyph and hit-tested in {@code MouseDown} rather than being a
     * {@code GameButton} like the row around it - the arrows are the port's own, and an upstream-font
     * glyph needs no art of its own.
     */
    public static final int PAGE_BUTTON_WIDTH = 22;
    public static final int PAGE_BUTTON_HEIGHT = 30;
    public static final int PAGE_PREV_BUTTON_X = 128;
    public static final int PAGE_NEXT_BUTTON_X = 312;
    public static final int PAGE_BUTTON_Y = 552;

    /** Position of the "{@code page/total}" counter, on the free square right of the imitater button. */
    public static final int PAGE_COUNTER_X = 596;
    public static final int PAGE_COUNTER_Y = 536;

    /** Imitater button position, SeedChooserScreen.cpp:146. */
    public static final int IMITATER_BUTTON_X = 464;
    public static final int IMITATER_BUTTON_Y = 515;

    public GameButton mStartButton;
    public GameButton mRandomButton;
    public GameButton mViewLawnButton;
    public GameButton mStoreButton;
    public GameButton mAlmanacButton;
    public GameButton mMenuButton;
    public GameButton mImitaterButton;
    public ChosenSeed[] mChosenSeeds;
    public LawnApp mApp;
    public Board mBoard;
    public int mNumSeedsToChoose;
    public int mSeedChooserAge;
    public int mSeedsInFlight;
    public int mSeedsInBank;
    public ToolTipWidget mToolTip;
    public int mToolTipSeed;
    public int mLastMouseX;
    public int mLastMouseY;
    /** Page of the grid on screen; 0 is the retail page, see {@link #rebuildPageSeeds()}. */
    public int mPage;
    /** How many pages the grid has, at least 1. */
    public int mNumPages;
    /**
     * Seed types the page on screen shows, in grid order: slot {@code n} of the page is
     * {@code mPageSeeds.get(n)}, and its square is {@code getSeedPositionInChooser(n, ...)} (the
     * imitater's slot is its own button, SeedChooserScreen.cpp:284-292).
     */
    private final List<Integer> mPageSeeds = new ArrayList<>(CHOOSER_PAGE_SIZE + 1);
    /**
     * Slot of a seed type on the page on screen, or {@code -1} when that page does not show it: the
     * draw loops read it to leave the other pages alone, the hit test to keep their squares
     * unclickable, and {@link #getSeedPositionInChooser(int, ChosenSeed)} to place a seed on its own
     * page whatever page is on screen.
     */
    private final int[] mPageSlotOf = new int[SeedType.MAX_SEED_TYPES];
    /**
     * Slot of a seed type on the page that shows it, or {@code -1} when no page does: the page on
     * screen places the seeds it shows through {@link #mPageSlotOf}, and a seed of another page keeps
     * its own page's slot so a packet that comes back from the bank knows where it belongs.
     */
    private final int[] mSeedSlotOf = new int[SeedType.MAX_SEED_TYPES];
    /**
     * Seed types above the retail range that the pages after page 0 are made of, in id order: those
     * with a definition that the player may pick. Rebuilt with the pages, see
     * {@link #rebuildPageSeeds()}.
     */
    private final List<Integer> mExtraSeeds = new ArrayList<>();
    /** Scratch out-param for the position getters, so the draw/update paths allocate nothing. */
    private final Point mTempPoint = new Point();
    /** Scratch hit-test rect, mirroring upstream's stack-local {@code Rect} in SeedHitTest. */
    private final Rect mHitRect = new Rect(0, 0, 0, 0);
    /** SeedChooserState value. */
    public int mChooseState;
    public int mViewLawnTime;
    /**
     * The repick warnings the current "Let's Rock" press has still to ask, in upstream's order; see
     * {@link #onStartButton} and {@link #addRepickWarning}.
     */
    private final List<String> mPendingRepickWarnings = new ArrayList<>();

    // SeedChooserScreen.cpp:45
    public SeedChooserScreen() {
        mApp = (LawnApp) SexyAppBase.current;
        mBoard = mApp.mBoard;
        mClip = false;
        // mSeedChooserAge is deliberately not initialized here (:47)
        mSeedsInFlight = 0;
        mSeedsInBank = 0;
        mLastMouseX = -1;
        mLastMouseY = -1;
        mChooseState = SeedChooserState.CHOOSE_NORMAL;
        mViewLawnTime = 0;
        mToolTip = new ToolTipWidget();
        mToolTip.mMaxLinesWidth = mApp.getInteger("SEED_CHOOSER_SCREEN_TOOL_TIP_MAX_LINE_WIDTH", 0);
        mToolTipSeed = SeedType.SEED_NONE;

        mStartButton = new GameButton(SEED_CHOOSER_SCREEN_START);
        mStartButton.setLabel("[LETS_ROCK_BUTTON]"); // the localization key name is wrong
        mStartButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON);
        mStartButton.mOverImage = null;
        mStartButton.mDownImage = null;
        mStartButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_DISABLED);
        mStartButton.mOverOverlayImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_GLOW);
        mStartButton.setFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID));
        mStartButton.setLabelHiliteColor(Color.White);
        mStartButton.resize(154, 545, 156, 42);
        mStartButton.mTextOffsetY = -1;
        enableStartButton(false);

        mMenuButton = new GameButton(SEED_CHOOSER_SCREEN_MENU);
        mMenuButton.setLabel("[MENU_BUTTON]");
        mMenuButton.resize(681, -10, 117, 46);
        mMenuButton.mDrawStoneButton = true;

        mRandomButton = new GameButton(SEED_CHOOSER_SCREEN_RANDOM);
        mRandomButton.setLabel("(Debug Play)");
        mRandomButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_BLANK);
        mRandomButton.mOverImage = Resources.getImageById(ImageID.IMAGE_BLANK);
        mRandomButton.mDownImage = Resources.getImageById(ImageID.IMAGE_BLANK);
        mRandomButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        mRandomButton.setLabelColor(new Color(255, 240, 0));
        mRandomButton.setLabelHiliteColor(new Color(200, 200, 255));
        mRandomButton.resize(332, 546, 100, 30);
        if (!mApp.mCheatKeys) {
            mRandomButton.mBtnNoDraw = true;
            mRandomButton.mDisabled = true;
        }

        Color aBtnColor = new Color(42, 42, 90);
        Image aBtnImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON2);
        Image aOverImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON2_GLOW);
        int aImageWidth = aBtnImage.getWidth();
        int aImageHeight = aOverImage.getHeight();

        mViewLawnButton = new GameButton(SEED_CHOOSER_SCREEN_VIEW_LAWN);
        mViewLawnButton.setLabel("[VIEW_LAWN]");
        mViewLawnButton.mButtonImage = aBtnImage;
        mViewLawnButton.mOverImage = aOverImage;
        mViewLawnButton.mDownImage = null;
        mViewLawnButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        mViewLawnButton.setLabelColor(aBtnColor);
        mViewLawnButton.setLabelHiliteColor(aBtnColor);
        mViewLawnButton.resize(22, 561, aImageWidth, aImageHeight);
        mViewLawnButton.mParentWidget = this;
        mViewLawnButton.mTextOffsetY = 1;
        if (!mBoard.mCutScene.isSurvivalRepick()) {
            mViewLawnButton.mBtnNoDraw = true;
            mViewLawnButton.mDisabled = true;
        }

        mAlmanacButton = new GameButton(SEED_CHOOSER_SCREEN_ALMANAC);
        mAlmanacButton.setLabel("[ALMANAC_BUTTON]");
        mAlmanacButton.mButtonImage = aBtnImage;
        mAlmanacButton.mOverImage = aOverImage;
        mAlmanacButton.mDownImage = null;
        mAlmanacButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        mAlmanacButton.setLabelColor(aBtnColor);
        mAlmanacButton.setLabelHiliteColor(aBtnColor);
        mAlmanacButton.resize(560, 572, aImageWidth, aImageHeight);
        mAlmanacButton.mParentWidget = this;
        mAlmanacButton.mTextOffsetY = 1;

        mStoreButton = new GameButton(SEED_CHOOSER_SCREEN_STORE);
        mStoreButton.setLabel("[SHOP_BUTTON]");
        mStoreButton.mButtonImage = aBtnImage;
        mStoreButton.mOverImage = aOverImage;
        mStoreButton.mDownImage = null;
        mStoreButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        mStoreButton.setLabelColor(aBtnColor);
        mStoreButton.setLabelHiliteColor(aBtnColor);
        mStoreButton.resize(680, 572, aImageWidth, aImageHeight);
        mStoreButton.mParentWidget = this;
        mStoreButton.mTextOffsetY = 1;

        mImitaterButton = new GameButton(SEED_CHOOSER_SCREEN_IMITATER);
        mImitaterButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_IMITATERSEED);
        mImitaterButton.mOverImage = Resources.getImageById(ImageID.IMAGE_IMITATERSEED);
        mImitaterButton.mDownImage = Resources.getImageById(ImageID.IMAGE_IMITATERSEED);
        mImitaterButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_IMITATERSEEDDISABLED);
        mImitaterButton.resize(IMITATER_BUTTON_X, IMITATER_BUTTON_Y,
                Resources.getImageById(ImageID.IMAGE_IMITATERSEED).mWidth,
                Resources.getImageById(ImageID.IMAGE_IMITATERSEED).mHeight);
        mImitaterButton.mParentWidget = this;

        if (!mApp.canShowAlmanac()) {
            mAlmanacButton.mBtnNoDraw = true;
            mAlmanacButton.mDisabled = true;
        }
        if (!mApp.canShowStore()) {
            mStoreButton.mBtnNoDraw = true;
            mStoreButton.mDisabled = true;
        }

        mChosenSeeds = new ChosenSeed[SeedType.MAX_SEED_TYPES];
        for (int i = 0; i < SeedType.MAX_SEED_TYPES; i++) {
            mChosenSeeds[i] = new ChosenSeed();
        }
        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.NUM_SEEDS_IN_CHOOSER; aSeedType++) {
            ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
            aChosenSeed.mSeedType = aSeedType;
            getSeedPositionInChooser(aSeedType, aChosenSeed);
            aChosenSeed.mStartX = aChosenSeed.mX;
            aChosenSeed.mStartY = aChosenSeed.mY;
            aChosenSeed.mEndX = aChosenSeed.mX;
            aChosenSeed.mEndY = aChosenSeed.mY;
            aChosenSeed.mSeedState = aSeedType == SeedType.SEED_IMITATER
                    ? ChosenSeedState.SEED_PACKET_HIDDEN : ChosenSeedState.SEED_IN_CHOOSER;
            aChosenSeed.mSeedIndexInBank = 0;
            aChosenSeed.mRefreshCounter = 0;
            aChosenSeed.mRefreshing = false;
            aChosenSeed.mImitaterType = SeedType.SEED_NONE;
            aChosenSeed.mCrazyDavePicked = false;
        }
        if (mBoard.mCutScene.isSurvivalRepick()) {
            for (int anIdx = 0; anIdx < mBoard.mSeedBank.mNumPackets; anIdx++) {
                SeedPacket aSeedPacket = mBoard.mSeedBank.mSeedPackets[anIdx];
                ChosenSeed aChosenSeed = mChosenSeeds[aSeedPacket.mPacketType];
                aChosenSeed.mRefreshing = aSeedPacket.mRefreshing;
                aChosenSeed.mRefreshCounter = aSeedPacket.mRefreshCounter;
            }
            mBoard.mSeedBank.mNumPackets = 0;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_SEEING_STARS) {
            ChosenSeed aStarFruit = mChosenSeeds[SeedType.SEED_STARFRUIT];
            int aX = mBoard.getSeedPacketPositionX(0);
            aStarFruit.mX = aX;
            aStarFruit.mY = 8;
            aStarFruit.mStartX = aX;
            aStarFruit.mStartY = 8;
            aStarFruit.mEndX = aX;
            aStarFruit.mEndY = 8;
            aStarFruit.mSeedState = ChosenSeedState.SEED_IN_BANK;
            aStarFruit.mSeedIndexInBank = 0;
            mSeedsInBank++;
        }
        if (mApp.isAdventureMode() && !mApp.isFirstTimeAdventureMode()) {
            crazyDavePickSeeds();
        }
        updateImitaterButton();
        rebuildPageSeeds();
    }

    // SeedChooserScreen.cpp:207-223
    public static int pickFromWeightedArrayUsingSpecialRandSeed(PvzpWeightedArray[] theArray,
            int theCount, MTRand theLevelRNG) {
        int aTotalWeight = 0;
        for (int i = 0; i < theCount; i++) {
            aTotalWeight += theArray[i].mWeight;
        }
        assert aTotalWeight > 0;

        int aRndResult = (int) theLevelRNG.next(aTotalWeight);
        int aWeight = 0;
        for (int j = 0; j < theCount; j++) {
            aWeight += theArray[j].mWeight;
            if (aWeight > aRndResult) {
                return (int) theArray[j].mItem;
            }
        }
        throw new IllegalStateException("PickFromWeightedArrayUsingSpecialRandSeed");
    }

    // SeedChooserScreen.cpp:225-273
    public void crazyDavePickSeeds() {
        PvzpWeightedArray[] aSeedArray = new PvzpWeightedArray[SeedType.NUM_SEED_TYPES];
        for (int i = 0; i < SeedType.NUM_SEED_TYPES; i++) {
            aSeedArray[i] = new PvzpWeightedArray();
        }
        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.NUM_SEEDS_IN_CHOOSER; aSeedType++) {
            aSeedArray[aSeedType].mItem = aSeedType;
            if (!mApp.hasSeedType(aSeedType) || seedNotRecommendedToPick(aSeedType) != 0
                    || seedNotAllowedToPick(aSeedType) || Plant.isUpgrade(aSeedType)
                    || aSeedType == SeedType.SEED_IMITATER || aSeedType == SeedType.SEED_UMBRELLA
                    || aSeedType == SeedType.SEED_BLOVER) {
                aSeedArray[aSeedType].mWeight = 0;
            } else {
                aSeedArray[aSeedType].mWeight = 1;
            }
        }
        if (mBoard.mZombieAllowed[ZombieType.ZOMBIE_BUNGEE] || mBoard.mZombieAllowed[ZombieType.ZOMBIE_CATAPULT]) {
            aSeedArray[SeedType.SEED_UMBRELLA].mWeight = 1;
        }
        if (mBoard.mZombieAllowed[ZombieType.ZOMBIE_BALLOON] || mBoard.stageHasFog()) {
            aSeedArray[SeedType.SEED_BLOVER].mWeight = 1;
        }
        if (mBoard.stageHasRoof()) {
            aSeedArray[SeedType.SEED_TORCHWOOD].mWeight = 0;
        }

        MTRand aLevelRNG = new MTRand(mBoard.getLevelRandSeed());
        for (int i = 0; i < 3; i++) {
            int aPickedSeed = pickFromWeightedArrayUsingSpecialRandSeed(aSeedArray,
                    SeedType.NUM_SEEDS_IN_CHOOSER, aLevelRNG);
            aSeedArray[aPickedSeed].mWeight = 0;
            ChosenSeed aChosenSeed = mChosenSeeds[aPickedSeed];

            int aPosX = mBoard.getSeedPacketPositionX(i);
            aChosenSeed.mX = aPosX;
            aChosenSeed.mY = 8;
            aChosenSeed.mStartX = aPosX;
            aChosenSeed.mStartY = 8;
            aChosenSeed.mEndX = aPosX;
            aChosenSeed.mEndY = 8;
            aChosenSeed.mSeedState = ChosenSeedState.SEED_IN_BANK;
            aChosenSeed.mSeedIndexInBank = i;
            aChosenSeed.mCrazyDavePicked = true;
            mSeedsInBank++;
        }
    }

    // SeedChooserScreen.cpp:275-282
    public boolean has7Rows() {
        if (mApp.hasFinishedAdventure() || mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA] != 0) {
            return true;
        }
        for (int aSeedType = SeedType.SEED_TWINSUNFLOWER; aSeedType < SeedType.SEED_COBCANNON; aSeedType++) {
            if (aSeedType != SeedType.SEED_SPIKEROCK && mApp.hasSeedType(aSeedType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pure form of {@code Has7Rows} (SeedChooserScreen.cpp:275-282): a seven-row chooser grid is
     * used once adventure is finished, the Gatling Pea upgrade is owned, or any seed in
     * {@code [SEED_TWINSUNFLOWER, SEED_COBCANNON)} other than SEED_SPIKEROCK is owned.
     */
    public static boolean has7Rows(boolean theFinishedAdventure, boolean theHasGatlingPeaPurchase,
            java.util.function.IntPredicate theHasSeedType) {
        if (theFinishedAdventure || theHasGatlingPeaPurchase) {
            return true;
        }
        for (int aSeedType = SeedType.SEED_TWINSUNFLOWER; aSeedType < SeedType.SEED_COBCANNON; aSeedType++) {
            if (aSeedType != SeedType.SEED_SPIKEROCK && theHasSeedType.test(aSeedType)) {
                return true;
            }
        }
        return false;
    }

    // SeedChooserScreen.cpp:284-306
    public void getSeedPositionInChooser(int theSlot, ChosenSeed theChosenSeed) {
        Point aPos = mTempPoint;
        getSeedPositionInChooser(theSlot, aPos);
        theChosenSeed.mX = aPos.mX;
        theChosenSeed.mY = aPos.mY;
    }

    /**
     * Chooser grid position (SeedChooserScreen.cpp:294-305): 8 columns, x = col*53 + 22;
     * y = row*70 + 123 when the chooser has 7 rows, else row*73 + 128.
     */
    public static void getSeedPositionInChooser(int theIndex, boolean sevenRows, Point out) {
        int aRow = theIndex / CHOOSER_COLUMNS;
        int aCol = theIndex % CHOOSER_COLUMNS;

        out.mX = aCol * CHOOSER_COLUMN_PITCH + CHOOSER_X_ORIGIN;
        if (sevenRows) {
            out.mY = aRow * CHOOSER_ROW_PITCH_7_ROWS + CHOOSER_Y_ORIGIN_7_ROWS;
        } else {
            out.mY = aRow * CHOOSER_ROW_PITCH_6_ROWS + CHOOSER_Y_ORIGIN_6_ROWS;
        }
    }

    /**
     * Place of one slot of the page on screen (SeedChooserScreen.cpp:294-305): the grid square of the
     * slot, or the imitater's own button for the slot the imitater takes
     * (SeedChooserScreen.cpp:284-292).
     */
    public void getSeedPositionInChooser(int theSlot, Point out) {
        getSeedSlotPosition(theSlot, theSlot < mPageSeeds.size() ? mPageSeeds.get(theSlot) : SeedType.SEED_NONE, out);
    }

    /** The place of one slot of a page, told which seed type sits in it (the imitater has its own). */
    private void getSeedSlotPosition(int theSlot, int theSeedType, Point out) {
        if (theSeedType == SeedType.SEED_IMITATER) {
            out.mX = mImitaterButton.mX;
            out.mY = mImitaterButton.mY;
        } else {
            getSeedPositionInChooser(theSlot, has7Rows(), out);
        }
    }

    /**
     * Place of a seed type's square: the square of the page that shows it, whether or not that page is
     * the one on screen. A packet that is clicked in the bank flies back to the page it came from, so
     * the target is its own page's square and not the square of a slot of the page on screen.
     */
    private void seedPositionInChooser(int theSeedType, Point out) {
        int aSlot = mPageSlotOf[theSeedType] >= 0 ? mPageSlotOf[theSeedType] : mSeedSlotOf[theSeedType];
        if (aSlot < 0) {
            // No page shows the seed (it was unregistered between the click and the landing): leave it
            // where it is rather than moving it to a square of a page it is not on.
            ChosenSeed aChosenSeed = mChosenSeeds[theSeedType];
            out.mX = aChosenSeed.mX;
            out.mY = aChosenSeed.mY;
            return;
        }
        getSeedSlotPosition(aSlot, theSeedType, out);
    }

    /**
     * Rebuilds the pages and places the seeds of the page on screen.
     *
     * <p>Page 0 is the retail grid: seed types {@code 0 .. NUM_SEEDS_IN_CHOOSER-1}, all of them,
     * exactly the squares upstream fills (a seed the player has not unlocked yet draws as a
     * silhouette). Every page after it shows up to {@link #CHOOSER_PAGE_SIZE} seed types above the
     * retail range, in id order, and only those the player may pick: the chooser is where a player
     * picks plants, so a seed type the game has no definition for and one that is not unlocked yet are
     * on no page at all. The page on screen is clamped when the count shrinks (a seed type can go away
     * again while the game runs).
     */
    private void rebuildPageSeeds() {
        mExtraSeeds.clear();
        for (int aSeedType = SeedType.NUM_SEEDS_IN_CHOOSER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            if (Plant.getPlantDefinition(aSeedType) != null && mApp.hasSeedType(aSeedType)) {
                mExtraSeeds.add(aSeedType);
            }
        }

        mNumPages = 1 + (mExtraSeeds.size() + CHOOSER_PAGE_SIZE - 1) / CHOOSER_PAGE_SIZE;
        if (mPage >= mNumPages) {
            mPage = mNumPages - 1;
        }

        mPageSeeds.clear();
        if (mPage == 0) {
            for (int aSeedType = 0; aSeedType < SeedType.NUM_SEEDS_IN_CHOOSER; aSeedType++) {
                mPageSeeds.add(aSeedType);
            }
        } else {
            int aFrom = (mPage - 1) * CHOOSER_PAGE_SIZE;
            for (int i = aFrom; i < Math.min(aFrom + CHOOSER_PAGE_SIZE, mExtraSeeds.size()); i++) {
                mPageSeeds.add(mExtraSeeds.get(i));
            }
        }

        Arrays.fill(mPageSlotOf, -1);
        Arrays.fill(mSeedSlotOf, -1);
        for (int aSeedType = 0; aSeedType < SeedType.NUM_SEEDS_IN_CHOOSER; aSeedType++) {
            mSeedSlotOf[aSeedType] = aSeedType;
        }
        for (int i = 0; i < mExtraSeeds.size(); i++) {
            mSeedSlotOf[mExtraSeeds.get(i)] = i % CHOOSER_PAGE_SIZE;

            // A seed type that has a page joins the chooser here, whether or not that page is the one
            // on screen: it is the chooser's seed from now on (see Update, which animates every seed
            // the player has), and only its position waits for its page to come on screen.
            ChosenSeed aChosenSeed = mChosenSeeds[mExtraSeeds.get(i)];
            if (aChosenSeed.mSeedType != mExtraSeeds.get(i)) {
                aChosenSeed.mSeedType = mExtraSeeds.get(i);
                aChosenSeed.mSeedState = ChosenSeedState.SEED_IN_CHOOSER;
            }
        }
        for (int aSlot = 0; aSlot < mPageSeeds.size(); aSlot++) {
            mPageSlotOf[mPageSeeds.get(aSlot)] = aSlot;
        }

        for (int aSlot = 0; aSlot < mPageSeeds.size(); aSlot++) {
            ChosenSeed aChosenSeed = mChosenSeeds[mPageSeeds.get(aSlot)];
            int aSeedState = aChosenSeed.mSeedState;
            if (aSeedState == ChosenSeedState.SEED_IN_BANK || aSeedState == ChosenSeedState.SEED_FLYING_TO_BANK
                    || aSeedState == ChosenSeedState.SEED_FLYING_TO_CHOOSER) {
                // It is the bank's (or on its way there or back): the bank places it, not this page.
                continue;
            }

            getSeedPositionInChooser(aSlot, aChosenSeed);
            aChosenSeed.mStartX = aChosenSeed.mX;
            aChosenSeed.mStartY = aChosenSeed.mY;
            aChosenSeed.mEndX = aChosenSeed.mX;
            aChosenSeed.mEndY = aChosenSeed.mY;
        }
    }

    /**
     * Shows one page of the grid: the seeds of the new page are placed where it wants them, which is
     * also why the packs in flight land first - a landing packet computes its square from the page it
     * belongs to, and one halfway to the page that was on screen would jump.
     */
    public void switchPage(int thePage) {
        if (thePage < 0 || thePage >= mNumPages || thePage == mPage
                || mChooseState == SeedChooserState.CHOOSE_VIEW_LAWN) {
            return;
        }

        // A landing packet computes the square it belongs to from its own page, so the ones in flight
        // land before the pages move (the same thing MouseDown does before it reads the arrows).
        if (mSeedsInFlight > 0) {
            for (int aSeedType = 0; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
                landFlyingSeed(mChosenSeeds[aSeedType]);
            }
        }

        mPage = thePage;
        rebuildPageSeeds();
        removeToolTip();
        mApp.playSample(SoundType.SOUND_TAP);
    }

    /** Whether (x, y) is inside the page arrow at {@code theButtonX} (the arrows are not widgets). */
    public static boolean pageButtonHit(int theButtonX, int x, int y) {
        return x >= theButtonX && x < theButtonX + PAGE_BUTTON_WIDTH
                && y >= PAGE_BUTTON_Y && y < PAGE_BUTTON_Y + PAGE_BUTTON_HEIGHT;
    }

    /**
     * The two page arrows and the page counter, drawn next to "Let's Rock" while the grid has more than
     * one page. With a single page nothing is drawn and nothing is clickable, so a game whose seed
     * types all fit on page 0 has exactly the upstream screen.
     */
    private void drawPageButtons(Graphics g) {
        if (mNumPages <= 1) {
            return;
        }

        Font aFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID);
        drawPageArrow(g, aFont, PAGE_PREV_BUTTON_X, "<", mPage > 0);
        drawPageArrow(g, aFont, PAGE_NEXT_BUTTON_X, ">", mPage < mNumPages - 1);
        PvzpCommon.pvzpDrawString(g, (mPage + 1) + "/" + mNumPages, PAGE_COUNTER_X, PAGE_COUNTER_Y, aFont,
                Color.White, DrawStringJustification.DS_ALIGN_CENTER);
    }

    /** One page arrow: a glyph in the label colours of the row around it, brighter while hovered. */
    private void drawPageArrow(Graphics g, Font theFont, int theButtonX, String theGlyph, boolean theEnabled) {
        boolean aHovered = theEnabled && mMouseVisible && mChooseState != SeedChooserState.CHOOSE_VIEW_LAWN
                && pageButtonHit(theButtonX, mLastMouseX, mLastMouseY);
        Color aColor = !theEnabled ? new Color(64, 64, 64) : aHovered ? new Color(200, 200, 255) : Color.White;
        PvzpCommon.pvzpDrawString(g, theGlyph, theButtonX + PAGE_BUTTON_WIDTH / 2,
                PAGE_BUTTON_Y + (PAGE_BUTTON_HEIGHT + theFont.getAscent()) / 2 - 1, theFont, aColor,
                DrawStringJustification.DS_ALIGN_CENTER);
    }

    // SeedChooserScreen.cpp:308-312
    public void getSeedPositionInBank(int theIndex, Point out) {
        out.mX = mBoard.mSeedBank.mX - mX + mBoard.getSeedPacketPositionX(theIndex);
        out.mY = mBoard.mSeedBank.mY - mY + 8;
    }

    // SeedChooserScreen.cpp:314-322
    public int seedNotRecommendedToPick(int theSeedType) {
        int aRecFlags = mBoard.seedNotRecommendedForLevel(theSeedType);
        // Upstream SetBit(aRecFlags, NOT_RECOMMENDED_NOCTURNAL, false) (PvzpCommon.h:186)
        if (PvzpCommon.testBit(aRecFlags, NotRecommend.NOT_RECOMMENDED_NOCTURNAL)
                && pickedPlantType(SeedType.SEED_INSTANT_COFFEE)) {
            aRecFlags &= ~(1 << NotRecommend.NOT_RECOMMENDED_NOCTURNAL);
        }
        return aRecFlags;
    }

    // SeedChooserScreen.cpp:324-328
    public boolean seedNotAllowedToPick(int theSeedType) {
        return mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                && (theSeedType == SeedType.SEED_SUNFLOWER || theSeedType == SeedType.SEED_SUNSHROOM
                        || theSeedType == SeedType.SEED_TWINSUNFLOWER || theSeedType == SeedType.SEED_SEASHROOM
                        || theSeedType == SeedType.SEED_PUFFSHROOM);
    }

    // SeedChooserScreen.cpp:330-333
    public boolean seedNotAllowedDuringTrial(int theSeedType) {
        return mApp.isTrialStageLocked()
                && (theSeedType == SeedType.SEED_SQUASH || theSeedType == SeedType.SEED_THREEPEATER);
    }

    // SeedChooserScreen.cpp:335-431
    @Override
    public void draw(Graphics g) {
        if (mApp.getDialog(Dialogs.DIALOG_STORE) != null || mApp.getDialog(Dialogs.DIALOG_ALMANAC) != null) {
            return;
        }

        g.setLinearBlend(true);
        if (!mBoard.chooseSeedsOnCurrentLevel() || mBoard.mCutScene.isBeforePreloading()) {
            return;
        }

        g.drawImage(Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BACKGROUND), 0, 87);
        if (mApp.hasSeedType(SeedType.SEED_IMITATER)) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_IMITATERADDON), 459, 503);
        }
        // the localization key name is wrong
        PvzpCommon.pvzpDrawString(g, "[CHOOSE_YOUR_PLANTS]", 229, 110,
                Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID), Color.White,
                cn.enaium.fabricpvz.portable.lawn.DrawStringJustification.DS_ALIGN_CENTER);

        // The empty squares of the page: a seed that is in the bank (or on its way there or back) shows
        // as a dimmed packet in its square, a seed type the player has not unlocked yet as a
        // silhouette. Upstream draws 48 squares with seven rows and 40 with six
        // (SeedChooserScreen.cpp:340); every page after page 0 holds at most CHOOSER_PAGE_SIZE seeds,
        // all of them the player's.
        int aNumSquares = mPage == 0 ? (has7Rows() ? 48 : 40) : mPageSeeds.size();
        for (int aSlot = 0; aSlot < Math.min(aNumSquares, mPageSeeds.size()); aSlot++) {
            int aSeedType = mPageSeeds.get(aSlot);
            if (aSeedType == SeedType.SEED_IMITATER) {
                continue;
            }
            getSeedPositionInChooser(aSlot, mTempPoint);

            if (mApp.hasSeedType(aSeedType)) {
                ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
                if (aChosenSeed.mSeedState != ChosenSeedState.SEED_IN_CHOOSER) {
                    SeedPacket.drawSeedPacket(g, mTempPoint.mX, mTempPoint.mY, aSeedType, SeedType.SEED_NONE, 0, 55, true, false);
                }
            } else {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_SEEDPACKETSILHOUETTE), mTempPoint.mX, mTempPoint.mY);
            }
        }

        int aNumSeedsInBank = mBoard.mSeedBank.mNumPackets;
        for (int anIndex = 0; anIndex < aNumSeedsInBank; anIndex++) {
            if (findSeedInBank(anIndex) == SeedType.SEED_NONE) {
                getSeedPositionInBank(anIndex, mTempPoint);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_SEEDPACKETSILHOUETTE), mTempPoint.mX, mTempPoint.mY);
            }
        }

        // Every seed the player has: one in the bank draws at its bank square, one on the page on
        // screen at its chooser square, and one on another page not at all - its packet is drawn again
        // when that page comes on screen.
        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
            int aSeedState = aChosenSeed.mSeedState;
            if (aSeedState == ChosenSeedState.SEED_IN_CHOOSER && mPageSlotOf[aSeedType] < 0) {
                continue;
            }
            if (mApp.hasSeedType(aSeedType) && aSeedState != ChosenSeedState.SEED_FLYING_TO_BANK
                    && aSeedState != ChosenSeedState.SEED_FLYING_TO_CHOOSER
                    && aSeedState != ChosenSeedState.SEED_PACKET_HIDDEN
                    && (aSeedState == ChosenSeedState.SEED_IN_CHOOSER || mBoard.mCutScene.mSeedChoosing)) {
                boolean aGrayed = false;
                if (((seedNotRecommendedToPick(aSeedType) != 0 || seedNotAllowedToPick(aSeedType))
                        && aSeedState == ChosenSeedState.SEED_IN_CHOOSER) || seedNotAllowedDuringTrial(aSeedType)) {
                    aGrayed = true;
                }

                int aPosX = aChosenSeed.mX;
                int aPosY = aChosenSeed.mY;
                if (aSeedState == ChosenSeedState.SEED_IN_BANK) {
                    aPosX -= mX;
                    aPosY -= mY;
                }
                SeedPacket.drawSeedPacket(g, aPosX, aPosY, aChosenSeed.mSeedType, aChosenSeed.mImitaterType,
                        0, aGrayed ? 115 : 255, true, false);
            }
        }

        mImitaterButton.draw(g);
        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
            int aSeedState = aChosenSeed.mSeedState;
            if (mApp.hasSeedType(aSeedType) && (aSeedState == ChosenSeedState.SEED_FLYING_TO_BANK
                    || aSeedState == ChosenSeedState.SEED_FLYING_TO_CHOOSER)) {
                SeedPacket.drawSeedPacket(g, aChosenSeed.mX, aChosenSeed.mY, aChosenSeed.mSeedType,
                        aChosenSeed.mImitaterType, 0, 255, true, false);
            }
        }

        drawPageButtons(g);
        mStartButton.draw(g);
        mRandomButton.draw(g);
        mViewLawnButton.draw(g);
        mAlmanacButton.draw(g);
        mStoreButton.draw(g);
        Graphics aBoardFrameG = new Graphics(g);
        aBoardFrameG.mTransX -= mX;
        aBoardFrameG.mTransY -= mY;
        mMenuButton.draw(aBoardFrameG);
        mToolTip.draw(g);
    }

    // SeedChooserScreen.cpp:433-464
    public void updateViewLawn() {
        if (mChooseState != SeedChooserState.CHOOSE_VIEW_LAWN) {
            return;
        }
        mViewLawnTime++;
        if (mViewLawnTime == 100) {
            mBoard.displayAdviceAgain("[CLICK_TO_CONTINUE]", MessageStyle.MESSAGE_STYLE_HINT_STAY,
                    AdviceType.ADVICE_CLICK_TO_CONTINUE);
        } else if (mViewLawnTime == 251) {
            mViewLawnTime = 250;
        }

        int aBoardX = GameConstants.BOARD_IMAGE_WIDTH_OFFSET - mApp.mWidth;
        int aSeedChooserY = GameConstants.SEED_CHOOSER_OFFSET_Y
                - Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BACKGROUND).mHeight;
        if (mViewLawnTime <= 100) {
            mBoard.move(-PvzpCommon.pvzpAnimateCurve(0, 100, mViewLawnTime, aBoardX, 0,
                    cn.enaium.fabricpvz.portable.lawn.PvzpCurves.CURVE_EASE_IN_OUT), 0);
            move(0, PvzpCommon.pvzpAnimateCurve(0, 40, mViewLawnTime, aSeedChooserY,
                    GameConstants.SEED_CHOOSER_OFFSET_Y, cn.enaium.fabricpvz.portable.lawn.PvzpCurves.CURVE_EASE_IN_OUT));
        } else if (mViewLawnTime <= 250) {
            mBoard.move(0, 0);
            move(0, GameConstants.SEED_CHOOSER_OFFSET_Y);
        } else if (mViewLawnTime <= 350) {
            mBoard.clearAdvice(AdviceType.ADVICE_CLICK_TO_CONTINUE);
            mBoard.move(-PvzpCommon.pvzpAnimateCurve(250, 350, mViewLawnTime, 0, aBoardX,
                    cn.enaium.fabricpvz.portable.lawn.PvzpCurves.CURVE_EASE_IN_OUT), 0);
            move(0, PvzpCommon.pvzpAnimateCurve(310, 350, mViewLawnTime, GameConstants.SEED_CHOOSER_OFFSET_Y,
                    aSeedChooserY, cn.enaium.fabricpvz.portable.lawn.PvzpCurves.CURVE_EASE_IN_OUT));
        } else {
            mChooseState = SeedChooserState.CHOOSE_NORMAL;
            mViewLawnTime = 0;
            mMenuButton.mDisabled = false;
        }
    }

    /**
     * State half of {@code LandFlyingSeed} (SeedChooserScreen.cpp:466-492). Only lands a seed that
     * is currently flying; returns true when a seed landed (the caller then decrements
     * {@code mSeedsInFlight} and refreshes the imitater button when needed).
     */
    public static boolean landFlyingSeedState(ChosenSeed theChosenSeed) {
        if (theChosenSeed.mSeedState == ChosenSeedState.SEED_FLYING_TO_BANK) {
            theChosenSeed.mX = theChosenSeed.mEndX;
            theChosenSeed.mY = theChosenSeed.mEndY;
            theChosenSeed.mTimeStartMotion = 0;
            theChosenSeed.mTimeEndMotion = 0;
            theChosenSeed.mSeedState = ChosenSeedState.SEED_IN_BANK;
            return true;
        } else if (theChosenSeed.mSeedState == ChosenSeedState.SEED_FLYING_TO_CHOOSER) {
            theChosenSeed.mX = theChosenSeed.mEndX;
            theChosenSeed.mY = theChosenSeed.mEndY;
            theChosenSeed.mTimeStartMotion = 0;
            theChosenSeed.mTimeEndMotion = 0;
            theChosenSeed.mSeedState = ChosenSeedState.SEED_IN_CHOOSER;
            if (theChosenSeed.mSeedType == SeedType.SEED_IMITATER) {
                theChosenSeed.mSeedState = ChosenSeedState.SEED_PACKET_HIDDEN;
                theChosenSeed.mImitaterType = SeedType.SEED_NONE;
            }
            return true;
        }
        return false;
    }

    // SeedChooserScreen.cpp:466-492
    public void landFlyingSeed(ChosenSeed theChosenSeed) {
        boolean aImitaterLanding = theChosenSeed.mSeedState == ChosenSeedState.SEED_FLYING_TO_CHOOSER
                && theChosenSeed.mSeedType == SeedType.SEED_IMITATER;
        if (landFlyingSeedState(theChosenSeed)) {
            mSeedsInFlight--;
            if (aImitaterLanding) {
                updateImitaterButton();
            }
        }
    }

    // SeedChooserScreen.cpp:494-511
    public void updateCursor() {
        if (mApp.getDialogCount() != 0 || mBoard.mCutScene.isInShovelTutorial()
                || mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            return;
        }
        int aMouseSeedType = seedHitTest(mLastMouseX, mLastMouseY);
        if (aMouseSeedType != SeedType.SEED_NONE) {
            ChosenSeed aMouseChosenSeed = mChosenSeeds[aMouseSeedType];
            if (aMouseChosenSeed.mSeedState == ChosenSeedState.SEED_IN_BANK && aMouseChosenSeed.mCrazyDavePicked) {
                aMouseSeedType = SeedType.SEED_NONE;
            }
        }

        if (mMouseVisible && mChooseState != SeedChooserState.CHOOSE_VIEW_LAWN
                && ((aMouseSeedType != SeedType.SEED_NONE && !seedNotAllowedToPick(aMouseSeedType))
                        || mRandomButton.isMouseOver() || mViewLawnButton.isMouseOver() || mAlmanacButton.isMouseOver()
                        || mImitaterButton.isMouseOver() || mStoreButton.isMouseOver() || mMenuButton.isMouseOver()
                        || mStartButton.isMouseOver())) {
            mApp.setCursor(SexyAppBase.CURSOR_HAND);
        } else {
            mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }
    }

    // SeedChooserScreen.cpp:513-553
    @Override
    public void update() {
        super.update();

        mLastMouseX = mApp.mWidgetManager.mLastMouseX;
        mLastMouseY = mApp.mWidgetManager.mLastMouseY;

        mSeedChooserAge++;
        mToolTip.update();

        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            if (mApp.hasSeedType(aSeedType)) {
                ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
                if (aChosenSeed.mSeedState == ChosenSeedState.SEED_FLYING_TO_BANK
                        || aChosenSeed.mSeedState == ChosenSeedState.SEED_FLYING_TO_CHOOSER) {
                    int aTimeStart = aChosenSeed.mTimeStartMotion;
                    int aTimeEnd = aChosenSeed.mTimeEndMotion;
                    aChosenSeed.mX = PvzpCommon.pvzpAnimateCurve(aTimeStart, aTimeEnd, mSeedChooserAge,
                            aChosenSeed.mStartX, aChosenSeed.mEndX, cn.enaium.fabricpvz.portable.lawn.PvzpCurves.CURVE_EASE_IN_OUT);
                    aChosenSeed.mY = PvzpCommon.pvzpAnimateCurve(aTimeStart, aTimeEnd, mSeedChooserAge,
                            aChosenSeed.mStartY, aChosenSeed.mEndY, cn.enaium.fabricpvz.portable.lawn.PvzpCurves.CURVE_EASE_IN_OUT);
                    if (mSeedChooserAge >= aChosenSeed.mTimeEndMotion) {
                        landFlyingSeed(aChosenSeed);
                    }
                }
            }
        }

        showToolTip();
        mStartButton.update();
        mRandomButton.update();
        mViewLawnButton.update();
        mAlmanacButton.update();
        mImitaterButton.update();
        mStoreButton.update();
        mMenuButton.update();
        updateViewLawn();
        updateCursor();
        markDirty();
    }

    // SeedChooserScreen.cpp:555-565
    /**
     * Queues one repick warning of the current "Let's Rock" press. Upstream returns the box's answer
     * because {@code LawnMessageBox} blocks inside {@code Dialog::WaitForResult} (Dialog.cpp:410-441);
     * this port's boxes do not pump (sexy/Dialog.java:506), so {@link #onStartButton} collects the
     * messages the same conditions produce and asks them one at a time ({@link #showNextRepickWarning()}).
     */
    private void addRepickWarning(String theMessage) {
        mPendingRepickWarnings.add(theMessage);
    }

    // SeedChooserScreen.cpp:567-582
    public boolean flyersAreComming() {
        for (int aWave = 0; aWave < mBoard.mNumWaves; aWave++) {
            for (int anIndex = 0; anIndex < Board.MAX_ZOMBIES_IN_WAVE; anIndex++) {
                int aZombieType = mBoard.mZombiesInWave[aWave][anIndex];
                if (aZombieType == ZombieType.ZOMBIE_INVALID) {
                    break;
                }
                if (aZombieType == ZombieType.ZOMBIE_BALLOON) {
                    return true;
                }
            }
        }
        return false;
    }

    // SeedChooserScreen.cpp:584-597
    public boolean flyProtectionCurrentlyPlanted() {
        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == SeedType.SEED_CATTAIL || aPlant.mSeedType == SeedType.SEED_CACTUS) {
                return true;
            }
        }
        return false;
    }

    // SeedChooserScreen.cpp:599-607
    /** Queues this pair's upgrade warning, if the pick set asks for one; see {@link #addRepickWarning}. */
    public void checkSeedUpgrade(int theSeedTypeTo, int theSeedTypeFrom) {
        if (mApp.isSurvivalMode() || !pickedPlantType(theSeedTypeTo) || pickedPlantType(theSeedTypeFrom)) {
            return;
        }

        String aWarning = PvzpStringFile.translate("[SEED_CHOOSER_UPGRADE_WARNING]");
        aWarning = PvzpCommon.pvzpReplaceString(aWarning, "{UPGRADE_TO}", Plant.getNameString(theSeedTypeTo, SeedType.SEED_NONE));
        aWarning = PvzpCommon.pvzpReplaceString(aWarning, "{UPGRADE_FROM}", Plant.getNameString(theSeedTypeFrom, SeedType.SEED_NONE));
        addRepickWarning(aWarning);
    }

    // SeedChooserScreen.cpp:609-698
    /**
     * "Let's Rock": collects the repick warnings this pick set triggers, then asks them in upstream's
     * order, starting the level once the last one is accepted.
     *
     * <p>Upstream walks the same conditions inline and blocks in {@code Dialog::WaitForResult} on
     * every warning (SeedChooserScreen.cpp:611-697): the sequence resumes after the answered box and
     * abandons itself as soon as one is declined ({@code return}). This port's boxes do not block
     * (sexy/Dialog.java:506), so the conditions are collected up front and the answers drive
     * {@link #showNextRepickWarning()}. Collecting first is equivalent because every condition reads
     * only the picked plants, the level and the game mode, and the modal warning cannot change any of
     * them while it is up.
     */
    public void onStartButton() {
        mPendingRepickWarnings.clear();
        collectRepickWarnings();
        showNextRepickWarning();
    }

    /** The {@link #onStartButton} conditions, in SeedChooserScreen.cpp:611-697 order. */
    private void collectRepickWarnings() {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_SEEING_STARS && !pickedPlantType(SeedType.SEED_STARFRUIT)) {
            addRepickWarning("[SEED_CHOOSER_SEEING_STARS_WARNING]");
        }

        if (mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 11 && !pickedPlantType(SeedType.SEED_PUFFSHROOM)) {
            addRepickWarning("[SEED_CHOOSER_PUFFSHROOM_WARNING]");
        }
        if (!pickedPlantType(SeedType.SEED_SUNFLOWER) && !pickedPlantType(SeedType.SEED_TWINSUNFLOWER)
                && !pickedPlantType(SeedType.SEED_SUNSHROOM) && !mBoard.mCutScene.isSurvivalRepick()
                && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            addRepickWarning(mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 11
                    ? "[SEED_CHOOSER_NIGHT_SUN_WARNING]" : "[SEED_CHOOSER_SUN_WARNING]");
        }
        if (mBoard.stageHasPool() && !pickedPlantType(SeedType.SEED_LILYPAD) && !pickedPlantType(SeedType.SEED_SEASHROOM)
                && !pickedPlantType(SeedType.SEED_TANGLEKELP) && !mBoard.mCutScene.isSurvivalRepick()) {
            addRepickWarning(mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 21
                    ? "[SEED_CHOOSER_LILY_WARNING]" : "[SEED_CHOOSER_POOL_WARNING]");
        }
        if (mBoard.stageHasRoof() && !pickedPlantType(SeedType.SEED_FLOWERPOT)
                && mApp.hasSeedType(SeedType.SEED_FLOWERPOT)) {
            addRepickWarning("[SEED_CHOOSER_ROOF_WARNING]");
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT
                && !pickedPlantType(SeedType.SEED_WALLNUT)) {
            addRepickWarning("[SEED_CHOOSER_ART_WALLNUT_WARNING]");
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_SUNFLOWER
                && (!pickedPlantType(SeedType.SEED_STARFRUIT) || !pickedPlantType(SeedType.SEED_UMBRELLA)
                        || !pickedPlantType(SeedType.SEED_WALLNUT))) {
            addRepickWarning("[SEED_CHOOSER_ART_2_WARNING]");
        }

        if (flyersAreComming() && !flyProtectionCurrentlyPlanted() && !pickedPlantType(SeedType.SEED_CATTAIL)
                && !pickedPlantType(SeedType.SEED_CACTUS) && !pickedPlantType(SeedType.SEED_BLOVER)) {
            addRepickWarning("[SEED_CHOOSER_FLYER_WARNING]");
        }

        checkSeedUpgrade(SeedType.SEED_GATLINGPEA, SeedType.SEED_REPEATER);
        checkSeedUpgrade(SeedType.SEED_WINTERMELON, SeedType.SEED_MELONPULT);
        checkSeedUpgrade(SeedType.SEED_TWINSUNFLOWER, SeedType.SEED_SUNFLOWER);
        checkSeedUpgrade(SeedType.SEED_SPIKEROCK, SeedType.SEED_SPIKEWEED);
        checkSeedUpgrade(SeedType.SEED_COBCANNON, SeedType.SEED_KERNELPULT);
        checkSeedUpgrade(SeedType.SEED_GOLD_MAGNET, SeedType.SEED_MAGNETSHROOM);
        checkSeedUpgrade(SeedType.SEED_GLOOMSHROOM, SeedType.SEED_FUMESHROOM);
        checkSeedUpgrade(SeedType.SEED_CATTAIL, SeedType.SEED_LILYPAD);
    }

    /**
     * Shows the next queued warning and starts the level when none is left. "Let's Rock!" (the Yes
     * button) continues the sequence, "pick again" (the No button) abandons it — upstream's
     * {@code return}s in SeedChooserScreen.cpp:611-697.
     */
    private void showNextRepickWarning() {
        if (mPendingRepickWarnings.isEmpty()) {
            closeSeedChooser();
            return;
        }

        String aWarning = mPendingRepickWarnings.remove(0);
        mApp.lawnMessageBox(Dialogs.DIALOG_CHOOSER_WARNING, "[DIALOG_WARNING]", aWarning,
                "[DIALOG_BUTTON_YES]", "[REPICK_BUTTON]", Dialog.BUTTONS_YES_NO, theYes -> {
                    if (theYes) {
                        showNextRepickWarning();
                    } else {
                        mPendingRepickWarnings.clear();
                    }
                });
    }

    // SeedChooserScreen.cpp:700-720
    public void pickRandomSeeds() {
        for (int anIndex = mSeedsInBank; anIndex < mBoard.mSeedBank.mNumPackets; anIndex++) {
            int aSeedType;
            do {
                aSeedType = cn.enaium.fabricpvz.portable.sexy.Common.rand(mApp.getSeedsAvailable());
            } while (!mApp.hasSeedType(aSeedType) || aSeedType == SeedType.SEED_IMITATER
                    || mChosenSeeds[aSeedType].mSeedState != ChosenSeedState.SEED_IN_CHOOSER);
            ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
            aChosenSeed.mTimeStartMotion = 0;
            aChosenSeed.mTimeEndMotion = 0;
            aChosenSeed.mStartX = aChosenSeed.mX;
            aChosenSeed.mStartY = aChosenSeed.mY;
            Point aPos = new Point();
            getSeedPositionInBank(anIndex, aPos);
            aChosenSeed.mEndX = aPos.mX;
            aChosenSeed.mEndY = aPos.mY;
            aChosenSeed.mSeedState = ChosenSeedState.SEED_IN_BANK;
            aChosenSeed.mSeedIndexInBank = anIndex;
            mSeedsInBank++;
        }
        for (int aSeedFlying = SeedType.SEED_PEASHOOTER; aSeedFlying < SeedType.MAX_SEED_TYPES; aSeedFlying++) {
            landFlyingSeed(mChosenSeeds[aSeedFlying]);
        }
        closeSeedChooser();
    }

    // SeedChooserScreen.cpp:722-764
    public void buttonDepress(int theId) {
        if (mSeedsInFlight > 0 || mChooseState == SeedChooserState.CHOOSE_VIEW_LAWN || !mMouseVisible) {
            return;
        }

        if (theId == SEED_CHOOSER_SCREEN_VIEW_LAWN) {
            mChooseState = SeedChooserState.CHOOSE_VIEW_LAWN;
            mMenuButton.mDisabled = true;
            mViewLawnTime = 0;
        } else if (theId == SEED_CHOOSER_SCREEN_ALMANAC) {
            // PORT-NOTE: SeedChooserScreen.cpp:735-736 — upstream restores the chooser music after the
            // almanac's blocking WaitForResult(true) returned; this port's wait does not pump
            // (sexy/Dialog.java:506), so the restore runs when the almanac closes.
            mApp.doAlmanacDialog(SeedType.SEED_NONE, ZombieType.ZOMBIE_INVALID);
            mApp.onAlmanacClosed(() -> mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS));
        } else if (theId == SEED_CHOOSER_SCREEN_STORE) {
            StoreScreen aStore = mApp.showStoreScreen();
            aStore.mBackButton.setLabel("[STORE_BACK_TO_GAME]");
            // PORT-NOTE: SeedChooserScreen.cpp:740-748 — upstream reads mGoToTreeNow after
            // WaitForResult() returned, i.e. after autoKill removed the store (Dialog.cpp:410-441),
            // and restores the chooser music on the way out; this port's wait does not pump
            // (sexy/Dialog.java:506), so both run from the screen's close path.
            aStore.onStoreClosed(store -> {
                if (store.mGoToTreeNow) {
                    mApp.killBoard();
                    mApp.preNewGame(GameMode.GAMEMODE_TREE_OF_WISDOM, false);
                } else {
                    mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS);
                }
            });
        } else if (theId == SEED_CHOOSER_SCREEN_MENU) {
            mMenuButton.mIsOver = false;
            mMenuButton.mIsDown = false;
            updateCursor();
            mApp.doNewOptions(false);
        } else if (theId == SEED_CHOOSER_SCREEN_PREV_PAGE) {
            switchPage(mPage - 1);
        } else if (theId == SEED_CHOOSER_SCREEN_NEXT_PAGE) {
            switchPage(mPage + 1);
        } else if (mApp.getSeedsAvailable() >= mBoard.mSeedBank.mNumPackets) {
            if (theId == SEED_CHOOSER_SCREEN_START) {
                onStartButton();
            } else if (theId == SEED_CHOOSER_SCREEN_RANDOM) {
                pickRandomSeeds();
            }
        }
    }

    // SeedChooserScreen.cpp:766-778
    public int seedHitTest(int x, int y) {
        if (mMouseVisible) {
            for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
                ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
                if (!mApp.hasSeedType(aSeedType) || aChosenSeed.mSeedState == ChosenSeedState.SEED_PACKET_HIDDEN) {
                    continue;
                }
                // A seed of another page keeps the position of its own page: only a seed of the page on
                // screen (or one that is in the bank) has a square at these coordinates.
                if (aChosenSeed.mSeedState == ChosenSeedState.SEED_IN_CHOOSER && mPageSlotOf[aSeedType] < 0) {
                    continue;
                }
                mHitRect.mX = aChosenSeed.mX;
                mHitRect.mY = aChosenSeed.mY;
                mHitRect.mWidth = GameConstants.SEED_PACKET_WIDTH;
                mHitRect.mHeight = GameConstants.SEED_PACKET_HEIGHT;
                if (mHitRect.contains(x, y)) {
                    return aSeedType;
                }
            }
        }
        return SeedType.SEED_NONE;
    }

    // SeedChooserScreen.cpp:780-791
    public int findSeedInBank(int theIndexInBank) {
        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            if (mApp.hasSeedType(aSeedType)) {
                ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
                if (aChosenSeed.mSeedState == ChosenSeedState.SEED_IN_BANK
                        && aChosenSeed.mSeedIndexInBank == theIndexInBank) {
                    return aSeedType;
                }
            }
        }
        return SeedType.SEED_NONE;
    }

    // SeedChooserScreen.cpp:793-798
    public void enableStartButton(boolean theEnabled) {
        mStartButton.setDisabled(!theEnabled);
        if (theEnabled) {
            mStartButton.setLabelColor(Color.White);
        } else {
            mStartButton.setLabelColor(new Color(64, 64, 64));
        }
    }

    /** Upstream enable rule: "Let's Rock" is enabled exactly when the bank is full (:755, :755, :850, :830, :1142). */
    public static boolean startButtonEnabled(int theSeedsInBank, int theNumPackets) {
        return theSeedsInBank == theNumPackets;
    }

    /**
     * Chooser -> bank click transition (SeedChooserScreen.cpp:835-845). The caller checks
     * {@link #canMoveChooserSeedToBank}, fills mEndX/mEndY from {@code getSeedPositionInBank},
     * plays SOUND_TAP and enables the start button when the bank becomes full.
     */
    public static void clickSeedInChooser(ChosenSeed theChosenSeed, int theAge, int theSeedsInBank) {
        theChosenSeed.mTimeStartMotion = theAge;
        theChosenSeed.mTimeEndMotion = theAge + 25;
        theChosenSeed.mStartX = theChosenSeed.mX;
        theChosenSeed.mStartY = theChosenSeed.mY;
        theChosenSeed.mSeedState = ChosenSeedState.SEED_FLYING_TO_BANK;
        theChosenSeed.mSeedIndexInBank = theSeedsInBank;
    }

    /** SeedChooserScreen.cpp:834 guard — a chooser click is ignored once the bank is full. */
    public static boolean canMoveChooserSeedToBank(int theSeedsInBank, int theNumPackets) {
        return theSeedsInBank != theNumPackets;
    }

    /**
     * Bank -> chooser click transition for the clicked packet (SeedChooserScreen.cpp:826-830); the
     * packets after it are shifted down by one slot before this is called (:812-825).
     */
    public static void clickSeedInBank(ChosenSeed theChosenSeed, int theAge) {
        theChosenSeed.mTimeStartMotion = theAge;
        theChosenSeed.mTimeEndMotion = theAge + 25;
        theChosenSeed.mStartX = theChosenSeed.mX;
        theChosenSeed.mStartY = theChosenSeed.mY;
        theChosenSeed.mSeedState = ChosenSeedState.SEED_FLYING_TO_CHOOSER;
        theChosenSeed.mSeedIndexInBank = 0;
    }

    // SeedChooserScreen.cpp:800-830
    public void clickedSeedInBank(ChosenSeed theChosenSeed) {
        for (int anIndex = theChosenSeed.mSeedIndexInBank + 1; anIndex < mBoard.mSeedBank.mNumPackets; anIndex++) {
            int aSeedType = findSeedInBank(anIndex);
            if (aSeedType != SeedType.SEED_NONE) {
                ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
                aChosenSeed.mTimeStartMotion = mSeedChooserAge;
                aChosenSeed.mTimeEndMotion = mSeedChooserAge + 15;
                aChosenSeed.mStartX = aChosenSeed.mX;
                aChosenSeed.mStartY = aChosenSeed.mY;
                Point aPos = new Point();
                getSeedPositionInBank(anIndex - 1, aPos);
                aChosenSeed.mEndX = aPos.mX;
                aChosenSeed.mEndY = aPos.mY;
                aChosenSeed.mSeedState = ChosenSeedState.SEED_FLYING_TO_BANK;
                aChosenSeed.mSeedIndexInBank = anIndex - 1;
                mSeedsInFlight++;
            }
        }
        clickSeedInBank(theChosenSeed, mSeedChooserAge);
        Point aPos = new Point();
        // The seed's own page's square: the page on screen may be another one, and the packet still
        // belongs to the square it came from.
        seedPositionInChooser(theChosenSeed.mSeedType, aPos);
        theChosenSeed.mEndX = aPos.mX;
        theChosenSeed.mEndY = aPos.mY;
        mSeedsInBank--;
        mSeedsInFlight++;
        removeToolTip();
        enableStartButton(false);
        mApp.playSample(SoundType.SOUND_TAP);
    }

    // SeedChooserScreen.cpp:832-851
    public void clickedSeedInChooser(ChosenSeed theChosenSeed) {
        if (!canMoveChooserSeedToBank(mSeedsInBank, mBoard.mSeedBank.mNumPackets)) {
            return;
        }

        clickSeedInChooser(theChosenSeed, mSeedChooserAge, mSeedsInBank);
        Point aPos = new Point();
        getSeedPositionInBank(mSeedsInBank, aPos);
        theChosenSeed.mEndX = aPos.mX;
        theChosenSeed.mEndY = aPos.mY;
        mSeedsInFlight++;
        mSeedsInBank++;

        removeToolTip();
        mApp.playSample(SoundType.SOUND_TAP);
        if (startButtonEnabled(mSeedsInBank, mBoard.mSeedBank.mNumPackets)) {
            enableStartButton(true);
        }
    }

    // SeedChooserScreen.cpp:853-937
    public void showToolTip() {
        if (!mApp.mWidgetManager.mMouseIn || !mApp.mActive || mApp.getDialogCount() > 0
                || mChooseState == SeedChooserState.CHOOSE_VIEW_LAWN) {
            removeToolTip();
        } else if (mSeedsInFlight <= 0) {
            if (mImitaterButton.isMouseOver() && mMouseVisible) {
                mToolTip.setLabel(Plant.getToolTip(SeedType.SEED_IMITATER));
                mToolTip.setTitle(Plant.getNameString(SeedType.SEED_IMITATER, SeedType.SEED_NONE));
                mToolTip.setWarningText("");
                mToolTip.mX = (GameConstants.SEED_PACKET_WIDTH - mToolTip.mWidth) / 2 + mImitaterButton.mX;
                mToolTip.mY = mImitaterButton.mY - mToolTip.mHeight;
                mToolTip.mVisible = true;
            } else {
                int aSeedType = seedHitTest(mLastMouseX, mLastMouseY);
                if (aSeedType == SeedType.SEED_NONE) {
                    removeToolTip();
                } else if (aSeedType != mToolTipSeed) {
                    removeToolTip();
                    ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
                    int aRecFlags = seedNotRecommendedToPick(aSeedType);
                    if (seedNotAllowedToPick(aSeedType)) {
                        mToolTip.setWarningText("[NOT_ALLOWED_ON_THIS_LEVEL]");
                    } else if (seedNotAllowedDuringTrial(aSeedType)) {
                        mToolTip.setWarningText("[FULL_VERSION_ONLY]");
                    } else if (aChosenSeed.mSeedState == ChosenSeedState.SEED_IN_BANK && aChosenSeed.mCrazyDavePicked) {
                        mToolTip.setWarningText("[CRAZY_DAVE_WANTS]");
                    } else if (aRecFlags != 0) {
                        if (PvzpCommon.testBit(aRecFlags, NotRecommend.NOT_RECOMMENDED_NOCTURNAL)) {
                            mToolTip.setWarningText("[NOCTURNAL_WARNING]");
                        } else {
                            mToolTip.setWarningText("[NOT_RECOMMENDED_FOR_LEVEL]");
                        }
                    } else {
                        mToolTip.setWarningText("");
                    }

                    if (aSeedType == SeedType.SEED_IMITATER) {
                        mToolTip.setTitle(Plant.getNameString(aSeedType, aChosenSeed.mImitaterType));
                        mToolTip.setLabel(Plant.getToolTip(aChosenSeed.mImitaterType));
                    } else {
                        mToolTip.setTitle(Plant.getNameString(aSeedType, SeedType.SEED_NONE));
                        mToolTip.setLabel(Plant.getToolTip(aSeedType));
                    }

                    Point aSeedPos = mTempPoint;
                    if (aChosenSeed.mSeedState == ChosenSeedState.SEED_IN_BANK) {
                        getSeedPositionInBank(aChosenSeed.mSeedIndexInBank, aSeedPos);
                    } else {
                        seedPositionInChooser(aSeedType, aSeedPos);
                    }

                    mToolTip.mX = Math.max(0, Math.min(GameConstants.BOARD_WIDTH - mToolTip.mWidth,
                            (GameConstants.SEED_PACKET_WIDTH - mToolTip.mWidth) / 2 + aSeedPos.mX));
                    mToolTip.mY = aSeedPos.mY + 70;
                    mToolTip.mVisible = true;
                    mToolTipSeed = aSeedType;
                }
            }
        }
    }

    // SeedChooserScreen.cpp:940-944
    public void removeToolTip() {
        mToolTip.mVisible = false;
        mToolTipSeed = SeedType.SEED_NONE;
    }

    // SeedChooserScreen.cpp:946-949
    public void cancelLawnView() {
        if (mChooseState == SeedChooserState.CHOOSE_VIEW_LAWN && mViewLawnTime > 100 && mViewLawnTime <= 250) {
            mViewLawnTime = 251;
        }
    }

    // SeedChooserScreen.cpp:951-960
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
        if (theClickCount == 1) {
            if (mMenuButton.isMouseOver()) {
                buttonDepress(SEED_CHOOSER_SCREEN_MENU);
            } else if (mStartButton.isMouseOver()) {
                buttonDepress(SEED_CHOOSER_SCREEN_START);
            } else if (mAlmanacButton.isMouseOver()) {
                buttonDepress(SEED_CHOOSER_SCREEN_ALMANAC);
            } else if (mStoreButton.isMouseOver()) {
                buttonDepress(SEED_CHOOSER_SCREEN_STORE);
            }
        }
    }

    // SeedChooserScreen.cpp:962-974
    public void updateImitaterButton() {
        if (!mApp.hasSeedType(SeedType.SEED_IMITATER)) {
            mImitaterButton.mBtnNoDraw = true;
            mImitaterButton.mDisabled = true;
        } else {
            mImitaterButton.mBtnNoDraw = false;
            mImitaterButton.mDisabled =
                    mChosenSeeds[SeedType.SEED_IMITATER].mSeedState != ChosenSeedState.SEED_PACKET_HIDDEN;
        }
    }

    /** Upstream rule (SeedChooserScreen.cpp:971): the imitater button is disabled unless its seed is hidden in the chooser. */
    public static boolean imitaterButtonDisabled(int theImitaterSeedState) {
        return theImitaterSeedState != ChosenSeedState.SEED_PACKET_HIDDEN;
    }

    // SeedChooserScreen.cpp:976-1070
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        super.mouseDown(x, y, theClickCount);

        if (mSeedsInFlight > 0) {
            for (int i = 0; i < SeedType.MAX_SEED_TYPES; i++) {
                landFlyingSeed(mChosenSeeds[i]);
            }
        }

        if (mChooseState == SeedChooserState.CHOOSE_VIEW_LAWN) {
            cancelLawnView();
        } else if (mNumPages > 1 && pageButtonHit(PAGE_PREV_BUTTON_X, x, y)) {
            switchPage(mPage - 1);
        } else if (mNumPages > 1 && pageButtonHit(PAGE_NEXT_BUTTON_X, x, y)) {
            switchPage(mPage + 1);
        } else if (mRandomButton.isMouseOver()) {
            mApp.playSample(SoundType.SOUND_TAP);
            buttonDepress(SEED_CHOOSER_SCREEN_RANDOM);
        } else if (mViewLawnButton.isMouseOver()) {
            mApp.playSample(SoundType.SOUND_TAP);
            buttonDepress(SEED_CHOOSER_SCREEN_VIEW_LAWN);
        } else if (mMenuButton.isMouseOver()) {
            mApp.playSample(SoundType.SOUND_GRAVEBUTTON);
        } else if (mStartButton.isMouseOver() || mAlmanacButton.isMouseOver() || mStoreButton.isMouseOver()) {
            mApp.playSample(SoundType.SOUND_TAP);
        } else if (mImitaterButton.isMouseOver()) {
            if (mSeedsInBank != mBoard.mSeedBank.mNumPackets) {
                mApp.playSample(SoundType.SOUND_TAP);
                ImitaterDialog aDialog = new ImitaterDialog();
                mApp.addDialog(aDialog.mId, aDialog);
                aDialog.resize((mWidth - aDialog.mWidth) / 2, (mHeight - aDialog.mHeight) / 2,
                        aDialog.mWidth, aDialog.mHeight);
                mApp.mWidgetManager.setFocus(aDialog);
            }
        } else {
            if (!mBoard.mSeedBank.containsPoint(x, y) && !mAlmanacButton.isMouseOver()
                    && !mStoreButton.isMouseOver() && mApp.canShowAlmanac()) {
                Zombie aZombie = mBoard.zombieHitTest(x - mBoard.mX, y - mBoard.mY);
                if (aZombie != null && aZombie.mFromWave == Zombie.ZOMBIE_WAVE_CUTSCENE
                        && aZombie.mZombieType != ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                    mApp.playSample(SoundType.SOUND_TAP);
                    // PORT-NOTE: SeedChooserScreen.cpp:1029-1030 — same shape as the almanac button
                    // above: the chooser music is restored once the almanac is closed.
                    mApp.doAlmanacDialog(SeedType.SEED_NONE, aZombie.mZombieType);
                    mApp.onAlmanacClosed(
                            () -> mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS));
                    return;
                }
            }

            int aSeedType = seedHitTest(x, y);
            if (aSeedType != SeedType.SEED_NONE && !seedNotAllowedToPick(aSeedType)) {
                if (seedNotAllowedDuringTrial(aSeedType)) {
                    mApp.playSample(SoundType.SOUND_TAP);
                    mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "[GET_FULL_VERSION_TITLE]",
                            "[GET_FULL_VERSION_BODY]", "[GET_FULL_VERSION_YES_BUTTON]",
                            "[GET_FULL_VERSION_NO_BUTTON]", Dialog.BUTTONS_YES_NO,
                            theYes -> {
                                if (theYes) {
                                    mApp.doBackToMain();
                                }
                            });
                } else {
                    ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
                    if (aChosenSeed.mSeedState == ChosenSeedState.SEED_IN_BANK) {
                        if (aChosenSeed.mCrazyDavePicked) {
                            mApp.playSample(SoundType.SOUND_BUZZER);
                            mToolTip.flashWarning();
                        } else {
                            clickedSeedInBank(aChosenSeed);
                        }
                    } else if (aChosenSeed.mSeedState == ChosenSeedState.SEED_IN_CHOOSER) {
                        clickedSeedInChooser(aChosenSeed);
                    }
                }
            }
        }
    }

    // SeedChooserScreen.cpp:1072-1086
    public boolean pickedPlantType(int theSeedType) {
        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
            if (aChosenSeed.mSeedState == ChosenSeedState.SEED_IN_BANK) {
                if (aChosenSeed.mSeedType == theSeedType
                        || (aChosenSeed.mSeedType == SeedType.SEED_IMITATER && aChosenSeed.mImitaterType == theSeedType)) {
                    return true;
                }
            }
        }
        return false;
    }

    // SeedChooserScreen.cpp:1088-1106
    public void closeSeedChooser() {
        for (int anIndex = 0; anIndex < mBoard.mSeedBank.mNumPackets; anIndex++) {
            int aSeedType = findSeedInBank(anIndex);
            ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
            SeedPacket aSeedPacket = mBoard.mSeedBank.mSeedPackets[anIndex];
            aSeedPacket.setPacketType(aSeedType, aChosenSeed.mImitaterType);
            if (aChosenSeed.mRefreshing) {
                aSeedPacket.mRefreshCounter = aChosenSeed.mRefreshCounter;
                aSeedPacket.mRefreshTime = Plant.getRefreshTime(aSeedPacket.mPacketType, aSeedPacket.mImitaterType);
                aSeedPacket.mRefreshing = true;
                aSeedPacket.mActive = false;
            }
        }
        mBoard.mCutScene.endSeedChooser();
    }

    // SeedChooserScreen.cpp:1108-1120
    @Override
    public void keyDown(int theKey) {
        mBoard.doTypingCheck(theKey);

        if (mChooseState == SeedChooserState.CHOOSE_VIEW_LAWN
                && (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN || theKey == KeyCode.KEYCODE_ESCAPE)) {
            cancelLawnView();
        } else if (theKey == KeyCode.KEYCODE_ESCAPE) {
            buttonDepress(SEED_CHOOSER_SCREEN_MENU);
        }
    }

    // SeedChooserScreen.cpp:1122-1125
    @Override
    public void keyChar(char theChar) {
        mBoard.keyChar(theChar);
    }

    // SeedChooserScreen.cpp:1127-1143
    public void updateAfterPurchase() {
        // The purchase may have unlocked seed types, so the pages are rebuilt first: the loop below and
        // the squares of the page on screen then already know the new set.
        rebuildPageSeeds();

        for (int aSeedType = SeedType.SEED_PEASHOOTER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            ChosenSeed aChosenSeed = mChosenSeeds[aSeedType];
            if (aChosenSeed.mSeedState != ChosenSeedState.SEED_IN_BANK) {
                continue;
            }

            Point aPos = new Point();
            getSeedPositionInBank(aChosenSeed.mSeedIndexInBank, aPos);
            aChosenSeed.mX = aPos.mX;
            aChosenSeed.mY = aPos.mY;
            aChosenSeed.mStartX = aPos.mX;
            aChosenSeed.mStartY = aPos.mY;
            aChosenSeed.mEndX = aPos.mX;
            aChosenSeed.mEndY = aPos.mY;
        }
        enableStartButton(startButtonEnabled(mSeedsInBank, mBoard.mSeedBank.mNumPackets));
        updateImitaterButton();
    }
}
