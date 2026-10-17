/*
 * Ported from src/Lawn/Widget/AlmanacDialog.cpp (693 lines) and
 * src/Lawn/Widget/AlmanacDialog.h:27-106.
 *
 * The suburban almanac: an index page plus fixed 2D grids of the owned plants and of the
 * encountered zombies. There is no scrollbar; entry stat lines are built from the plant/zombie
 * definition tables plus the LawnStrings keys (docs/research/04-reanim-ui.md §2).
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.AlmanacPage;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.CursorType;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.lawn.system.Music;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFormatFlag;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringListFormat;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;

public class AlmanacDialog extends LawnDialog {
    private static final int ALMANAC_BUTTON_CLOSE = 0;
    private static final int ALMANAC_BUTTON_PLANT = 1;
    private static final int ALMANAC_BUTTON_ZOMBIE = 2;
    private static final int ALMANAC_BUTTON_INDEX = 3;
    /**
     * The two page arrows. Upstream has no page control at all (AlmanacDialog.cpp:41-104), so the
     * port takes the ids after the upstream four; the buttons only exist while the content page on
     * screen has more than one page.
     */
    private static final int ALMANAC_BUTTON_PREV_PAGE = 4;
    private static final int ALMANAC_BUTTON_NEXT_PAGE = 5;

    /** AlmanacDialog.h:29-36. */
    public static final int NUM_ALMANAC_SEEDS = 49;
    public static final int NUM_ALMANAC_ZOMBIES = 26;
    public static final float ALMANAC_PLANT_POSITION_X = 578.0f;
    public static final float ALMANAC_PLANT_POSITION_Y = 140.0f;
    public static final float ALMANAC_ZOMBIE_POSITION_X = 559.0f;
    public static final float ALMANAC_ZOMBIE_POSITION_Y = 175.0f;
    public static final int ALMANAC_INDEXPLANT_POSITION_X = 167;
    public static final int ALMANAC_INDEXPLANT_POSITION_Y = 255;
    public static final float ALMANAC_INDEXZOMBIE_POSITION_X = 535.0f;
    public static final float ALMANAC_INDEXZOMBIE_POSITION_Y = 215.0f;

    /**
     * Slots of one page of the plant grid after the retail page, i.e. the retail grid's non-special
     * squares: seed types 0..47 (the imitater sits in its own 34x46 square, {@code getSeedPosition},
     * AlmanacDialog.cpp:522-531). A page of the retail grid itself holds all
     * {@link #NUM_ALMANAC_SEEDS} slots, the imitater's included.
     */
    public static final int ALMANAC_PLANT_PAGE_SIZE = NUM_ALMANAC_SEEDS - 1;

    /**
     * Slots of one page of the zombie grid after the retail page, i.e. the retail grid's non-special
     * squares: the 25 non-boss zombie types (the boss sits in its own square at (192,486),
     * {@code getZombiePosition}, AlmanacDialog.cpp:609-618). A page of the retail grid itself holds
     * all {@link #NUM_ALMANAC_ZOMBIES} slots, the boss's included.
     */
    public static final int ALMANAC_ZOMBIE_PAGE_SIZE = NUM_ALMANAC_ZOMBIES - 1;

    /**
     * Geometry of the two page arrows and of the "{@code page/total}" counter between them.
     *
     * <p>They sit in the bottom button row, in the free stretch between the index button
     * (32..196 by 567..593) and the close button (676..765 by 567..593): the row's other widgets and
     * both content grids (the plant grid's last row ends at y=552, the zombie grid's boss square at
     * y=562) are left alone. The arrows wear the button art {@code mPlantButton} wears, because the
     * port's page control is an addition and no art of its own exists.
     */
    public static final int ALMANAC_PAGE_BUTTON_WIDTH = 34;
    public static final int ALMANAC_PAGE_BUTTON_HEIGHT = 26;
    public static final int ALMANAC_PAGE_PREV_BUTTON_X = 250;
    public static final int ALMANAC_PAGE_NEXT_BUTTON_X = 340;
    public static final int ALMANAC_PAGE_BUTTON_Y = 567;
    /** Label colour of a page arrow that cannot be pressed, i.e. of an end of the page range. */
    private static final Color PAGE_ARROW_DISABLED_COLOR = new Color(64, 64, 64);

    public static final int ALMANAC_PAGE_COUNTER_X = 312;
    public static final int ALMANAC_PAGE_COUNTER_Y = 567;

    /** AlmanacDialog.h:79 — cleared by {@link #almanacInitForPlayer}. */
    public static boolean[] gZombieDefeated = new boolean[ZombieType.MAX_ZOMBIE_TYPES];

    public LawnApp mApp;
    public GameButton mCloseButton;
    public GameButton mIndexButton;
    public GameButton mPlantButton;
    public GameButton mZombieButton;
    /** Page arrows of the two content grids; see {@link #refreshPageButtons()}. */
    public GameButton mPrevPageButton;
    public GameButton mNextPageButton;
    /** AlmanacPage value. */
    public int mOpenPage;
    /** Page of the plant grid on screen; 0 is the retail grid, see {@link #rebuildPlantPage()}. */
    public int mPlantPage;
    /** How many pages the plant grid has, at least 1. */
    public int mPlantNumPages;
    /** Page of the zombie grid on screen; 0 is the retail grid, see {@link #rebuildZombiePage()}. */
    public int mZombiePage;
    /** How many pages the zombie grid has, at least 1. */
    public int mZombieNumPages;
    /** Upstream declares {@code Reanimation* mReanim[4]} but never assigns it (AlmanacDialog.h:63). */
    public Reanimation[] mReanim = new Reanimation[4];
    /** SeedType value. */
    public int mSelectedSeed;
    /** ZombieType value. */
    public int mSelectedZombie;
    public Plant mPlant;
    public Zombie mZombie;
    public Zombie[] mZombiePerfTest = new Zombie[400];

    private final Point mTempPoint = new Point();
    /** Scratch hit-test rect, mirroring upstream's stack-local {@code Rect} in Seed/ZombieHitTest. */
    private final Rect mHitRect = new Rect(0, 0, 0, 0);
    private final Rect mDescriptionRect = new Rect(485, 309, 258, 230);
    private final Rect mCostRect = new Rect(485, 520, 134, 50);
    private final Rect mRechargeRect = new Rect(600, 520, 139, 50);
    private final Rect mZombieTextRect = new Rect(484, 377, 258, 170);

    /**
     * Contents of the plant grid on screen, slot by slot: slot {@code n} holds
     * {@code mPlantPageSeeds.get(n)}, and its square is {@code getSeedSlotPosition(n, ...)}. Page 0
     * holds the retail seed types, so slot {@code n} is seed type {@code n} there; a page after it
     * holds the registered seed types above the retail grid, see {@link #rebuildPlantPage()}.
     */
    private final List<Integer> mPlantPageSeeds = new ArrayList<>(NUM_ALMANAC_SEEDS);
    /**
     * Contents of the zombie grid on screen, slot by slot; same shape as {@link #mPlantPageSeeds},
     * see {@link #rebuildZombiePage()}.
     */
    private final List<Integer> mZombiePageZombies = new ArrayList<>(NUM_ALMANAC_ZOMBIES);
    /**
     * Seed types the plant pages after page 0 are made of, in id order: those above the retail grid
     * with a definition that the player may pick. Rebuilt with the pages, see
     * {@link #rebuildPlantPage()}.
     */
    private final List<Integer> mExtraSeeds = new ArrayList<>();
    /**
     * Zombie types the zombie pages after page 0 are made of, in id order: those above the retail
     * zombie range with a definition. Rebuilt with the pages, see {@link #rebuildZombiePage()}.
     */
    private final List<Integer> mExtraZombies = new ArrayList<>();

    // AlmanacDialog.cpp:41-104
    public AlmanacDialog(LawnApp theApp) {
        super(theApp, Dialogs.DIALOG_ALMANAC, true, theApp.getString("ALMANAC_HEADER", "Almanac"),
                "", "", Dialog.BUTTONS_NONE);
        mApp = (LawnApp) SexyAppBase.current;
        mOpenPage = AlmanacPage.ALMANAC_PAGE_INDEX;
        mSelectedSeed = SeedType.SEED_PEASHOOTER;
        mSelectedZombie = ZombieType.ZOMBIE_NORMAL;
        mDrawStandardBack = false;
        mLoadedResourceNames.add("DelayLoad_Almanac");
        // AlmanacDialog.cpp:51-52: the groups registered above are loaded right away, before any widget reads its
        // images - without this the screen drew null images for everything they provide.
        for (String aResource : mLoadedResourceNames) {
            ((cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(aResource);
        }

        resize(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);

        Resources.extractResourcesByName(mApp.mResourceManager, "DelayLoad_Almanac");

        mCloseButton = new GameButton(ALMANAC_BUTTON_CLOSE);
        mCloseButton.setLabel("[CLOSE_BUTTON]");
        mCloseButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_ALMANAC_CLOSEBUTTON);
        mCloseButton.mOverImage = Resources.getImageById(ImageID.IMAGE_ALMANAC_CLOSEBUTTONHIGHLIGHT);
        mCloseButton.mDownImage = null;
        mCloseButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        Color aColor = new Color(42, 42, 90);
        mCloseButton.setLabelColor(aColor);
        mCloseButton.setLabelHiliteColor(aColor);
        mCloseButton.resize(676, 567, 89, 26);
        mCloseButton.mParentWidget = this;
        mCloseButton.mTextOffsetX = -8;
        mCloseButton.mTextOffsetY = 1;

        mIndexButton = new GameButton(ALMANAC_BUTTON_INDEX);
        mIndexButton.setLabel("[ALMANAC_INDEX]");
        mIndexButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_ALMANAC_INDEXBUTTON);
        mIndexButton.mOverImage = Resources.getImageById(ImageID.IMAGE_ALMANAC_INDEXBUTTONHIGHLIGHT);
        mIndexButton.mDownImage = null;
        mIndexButton.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        mIndexButton.setLabelColor(aColor);
        mIndexButton.setLabelHiliteColor(aColor);
        mIndexButton.resize(32, 567, 164, 26);
        mIndexButton.mParentWidget = this;
        mIndexButton.mTextOffsetX = 8;
        mIndexButton.mTextOffsetY = 1;

        mPlantButton = new GameButton(ALMANAC_BUTTON_PLANT);
        mPlantButton.setLabel("[VIEW_PLANTS]");
        mPlantButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON);
        mPlantButton.mOverImage = null;
        mPlantButton.mDownImage = null;
        mPlantButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_DISABLED);
        mPlantButton.mOverOverlayImage = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BUTTON_GLOW);
        mPlantButton.setFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID));
        mPlantButton.setLabelColor(Color.White);
        mPlantButton.setLabelHiliteColor(Color.White);
        mPlantButton.resize(130, 345, 156, 42);
        mPlantButton.mTextOffsetY = -1;
        mPlantButton.mParentWidget = this;

        mZombieButton = new GameButton(ALMANAC_BUTTON_ZOMBIE);
        mZombieButton.setLabel("[VIEW_ZOMBIES]");
        mZombieButton.resize(487, 345, 210, 48);
        mZombieButton.mDrawStoneButton = true;
        mZombieButton.mParentWidget = this;

        mPrevPageButton = makePageButton(ALMANAC_BUTTON_PREV_PAGE, "<", ALMANAC_PAGE_PREV_BUTTON_X);
        mNextPageButton = makePageButton(ALMANAC_BUTTON_NEXT_PAGE, ">", ALMANAC_PAGE_NEXT_BUTTON_X);

        // The pages are built once here so the dialog knows its grid before the index page hands over
        // to a content page; setPage() rebuilds them whenever one is entered.
        rebuildPlantPage();
        rebuildZombiePage();

        setPage(AlmanacPage.ALMANAC_PAGE_INDEX);
        if (mApp.mBoard == null || !mApp.mBoard.mPaused) {
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS);
        }

    }

    /**
     * One page arrow: a {@code GameButton} wearing the button art {@code mPlantButton} wears
     * ({@code IMAGE_SEEDCHOOSER_BUTTON} family) and a glyph label, since the port's page control has
     * no art and no LawnStrings key of its own.
     */
    /**
     * One page arrow: a glyph in the button's square, with no art of its own.
     *
     * <p>PORT-NOTE: the button art the rest of the row wears (the plant button's) is 156x42, and
     * {@code GameButton} draws a button image at its own size rather than at the widget's, so an arrow
     * wearing it painted a wide slab over the counter next to it. The arrows are the port's addition -
     * the same glyph-only control the seed chooser's page arrows are - and a blank image keeps the click
     * square and the label without painting anything behind them.
     */
    private GameButton makePageButton(int theId, String theLabel, int theX) {
        GameButton aButton = new GameButton(theId);
        aButton.setLabel(theLabel);
        aButton.mButtonImage = Resources.getImageById(ImageID.IMAGE_BLANK);
        aButton.mOverImage = null;
        aButton.mDownImage = null;
        aButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_BLANK);
        aButton.mOverOverlayImage = null;
        aButton.setFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID));
        aButton.setLabelColor(Color.White);
        aButton.setLabelHiliteColor(Color.White);
        aButton.resize(theX, ALMANAC_PAGE_BUTTON_Y, ALMANAC_PAGE_BUTTON_WIDTH, ALMANAC_PAGE_BUTTON_HEIGHT);
        aButton.mParentWidget = this;
        return aButton;
    }

    /**
     * Rebuilds the plant grid's pages: the seed types the pages after page 0 are made of, the number
     * of pages, and the slot map of the page on screen.
     *
     * <p>A seed type belongs to those pages when it has a definition and the player may pick it -
     * {@code LawnApp::HasSeedType} (LawnApp.cpp:2269), the same gate the seed chooser applies, because
     * the almanac lists what the player owns. The retail grid itself is page 0 and is not built from
     * this list: it stays exactly the upstream loop over seed types {@code 0..NUM_ALMANAC_SEEDS-1}.
     */
    public void rebuildPlantPage() {
        mExtraSeeds.clear();
        for (int aSeedType = SeedType.NUM_SEEDS_IN_CHOOSER; aSeedType < SeedType.MAX_SEED_TYPES; aSeedType++) {
            if (Plant.getPlantDefinition(aSeedType) != null && mApp.hasSeedType(aSeedType)) {
                mExtraSeeds.add(aSeedType);
            }
        }

        mPlantNumPages = 1 + (mExtraSeeds.size() + ALMANAC_PLANT_PAGE_SIZE - 1) / ALMANAC_PLANT_PAGE_SIZE;
        if (mPlantPage >= mPlantNumPages) {
            mPlantPage = mPlantNumPages - 1;
        }

        mPlantPageSeeds.clear();
        if (mPlantPage == 0) {
            for (int aSlot = 0; aSlot < NUM_ALMANAC_SEEDS; aSlot++) {
                mPlantPageSeeds.add(aSlot);
            }
        } else {
            int aFrom = (mPlantPage - 1) * ALMANAC_PLANT_PAGE_SIZE;
            for (int i = aFrom; i < Math.min(aFrom + ALMANAC_PLANT_PAGE_SIZE, mExtraSeeds.size()); i++) {
                mPlantPageSeeds.add(mExtraSeeds.get(i));
            }
        }

        ensureSelectedSeedIsOnAPage();
        refreshPageButtons();
    }

    /**
     * Rebuilds the zombie grid's pages, the zombie half of {@link #rebuildPlantPage()}.
     *
     * <p>A zombie type belongs to the pages after page 0 when it has a definition and lies above the
     * retail zombie range ({@link ZombieType#NUM_ZOMBIE_TYPES}): the almanac grid lists
     * {@code 0..NUM_ALMANAC_ZOMBIES-1} of that range, and the rows between the two (the "another plant
     * as a head" zombies) are retail internals upstream never listed either - counting them would give
     * an unmodded game a second page. There is no player-availability answer for a zombie type
     * ({@code LawnApp} has the seed-type one only), so a defined row is the whole gate.
     */
    public void rebuildZombiePage() {
        mExtraZombies.clear();
        for (int aZombieType = ZombieType.NUM_ZOMBIE_TYPES; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            if (Zombie.getZombieDefinition(aZombieType) != null) {
                mExtraZombies.add(aZombieType);
            }
        }

        mZombieNumPages = 1 + (mExtraZombies.size() + ALMANAC_ZOMBIE_PAGE_SIZE - 1) / ALMANAC_ZOMBIE_PAGE_SIZE;
        if (mZombiePage >= mZombieNumPages) {
            mZombiePage = mZombieNumPages - 1;
        }

        mZombiePageZombies.clear();
        if (mZombiePage == 0) {
            for (int aSlot = 0; aSlot < NUM_ALMANAC_ZOMBIES; aSlot++) {
                mZombiePageZombies.add(getZombieType(aSlot));
            }
        } else {
            int aFrom = (mZombiePage - 1) * ALMANAC_ZOMBIE_PAGE_SIZE;
            for (int i = aFrom; i < Math.min(aFrom + ALMANAC_ZOMBIE_PAGE_SIZE, mExtraZombies.size()); i++) {
                mZombiePageZombies.add(mExtraZombies.get(i));
            }
        }

        ensureSelectedZombieIsOnAPage();
        refreshPageButtons();
    }

    /**
     * Seed type of one slot of the plant grid on screen, or {@link SeedType#SEED_NONE} when the slot
     * is not on this page or the page has no content for it.
     */
    public int plantSlotSeedType(int theSlot) {
        return theSlot < 0 || theSlot >= mPlantPageSeeds.size()
                ? SeedType.SEED_NONE : mPlantPageSeeds.get(theSlot);
    }

    /**
     * Zombie type of one slot of the zombie grid on screen, or {@link ZombieType#ZOMBIE_INVALID} when
     * the slot is not on this page or the page has no content for it.
     */
    public int zombieSlotZombieType(int theSlot) {
        return theSlot < 0 || theSlot >= mZombiePageZombies.size()
                ? ZombieType.ZOMBIE_INVALID : mZombiePageZombies.get(theSlot);
    }

    /**
     * The square of one slot of the plant grid on screen: slot {@code n} is the retail square of seed
     * type {@code n} ({@code getSeedPosition}, AlmanacDialog.cpp:522-531). Page 0 puts seed type
     * {@code n} in slot {@code n}; a later page holds seed types above the retail grid in those same
     * squares, and never in the imitater's own slot (that is slot
     * {@link #ALMANAC_PLANT_PAGE_SIZE} of page 0 only).
     */
    public void getSeedSlotPosition(int theSlot, Point out) {
        getSeedPosition(theSlot, out);
    }

    /**
     * The square of one slot of the zombie grid on screen: slot {@code n} is the retail square of
     * zombie type {@code n} ({@code getZombiePosition}, AlmanacDialog.cpp:609-618), the boss's own
     * square being slot {@link #ALMANAC_ZOMBIE_PAGE_SIZE} of page 0 only.
     */
    public void getZombieSlotPosition(int theSlot, Point out) {
        getZombiePosition(theSlot, out);
    }

    /**
     * Switches the plant grid to another page; the port's page control, upstream's almanac being a
     * single fixed grid. A page that does not exist is ignored, so page 0 cannot be left when there is
     * nothing behind it.
     */
    public void switchPlantPage(int thePage) {
        if (thePage < 0 || thePage >= mPlantNumPages || thePage == mPlantPage) {
            return;
        }

        mPlantPage = thePage;
        rebuildPlantPage();
        setupPlant();
        mApp.playSample(SoundType.SOUND_TAP);
    }

    /** Switches the zombie grid to another page, see {@link #switchPlantPage(int)}. */
    public void switchZombiePage(int thePage) {
        if (thePage < 0 || thePage >= mZombieNumPages || thePage == mZombiePage) {
            return;
        }

        mZombiePage = thePage;
        rebuildZombiePage();
        setupZombie();
        mApp.playSample(SoundType.SOUND_TAP);
    }

    /**
     * Falls back to the first slot of the page on screen when the selected plant is on no page at all
     * (its seed type was unregistered, or lost its availability, while the dialog was open): the card
     * would otherwise describe an entry no grid of the almanac still holds. A plant of another page is
     * left selected, exactly like the chooser keeps a pick of another page.
     */
    private void ensureSelectedSeedIsOnAPage() {
        boolean aRetail = mSelectedSeed >= 0 && mSelectedSeed < NUM_ALMANAC_SEEDS && mApp.hasSeedType(mSelectedSeed);
        if (aRetail || mExtraSeeds.contains(mSelectedSeed)) {
            return;
        }

        for (int aSlot = 0; aSlot < mPlantPageSeeds.size(); aSlot++) {
            int aSeedType = mPlantPageSeeds.get(aSlot);
            if (mApp.hasSeedType(aSeedType)) {
                mSelectedSeed = aSeedType;
                return;
            }
        }
    }

    /** Zombie half of {@link #ensureSelectedSeedIsOnAPage()}. */
    private void ensureSelectedZombieIsOnAPage() {
        boolean aRetail = mSelectedZombie >= 0 && mSelectedZombie < NUM_ALMANAC_ZOMBIES;
        if (aRetail || mExtraZombies.contains(mSelectedZombie)) {
            return;
        }

        for (int aSlot = 0; aSlot < mZombiePageZombies.size(); aSlot++) {
            int aZombieType = mZombiePageZombies.get(aSlot);
            if (Zombie.getZombieDefinition(aZombieType) != null) {
                mSelectedZombie = aZombieType;
                return;
            }
        }
    }

    /** Page of the content grid on screen; cell-based, so only valid on the two content pages. */
    private int pageOnScreen() {
        return mOpenPage == AlmanacPage.ALMANAC_PAGE_ZOMBIES ? mZombiePage : mPlantPage;
    }

    /** Pages of the content grid on screen; see {@link #pageOnScreen()}. */
    private int numPagesOnScreen() {
        return mOpenPage == AlmanacPage.ALMANAC_PAGE_ZOMBIES ? mZombieNumPages : mPlantNumPages;
    }

    /**
     * Shows the two page arrows only while the content page on screen has more than one page, and
     * disables the one that would leave that range. The index page has none, and a single page draws
     * nothing at all - which is what keeps an unmodded almanac exactly upstream's.
     */
    private void refreshPageButtons() {
        boolean aContentPage = mOpenPage == AlmanacPage.ALMANAC_PAGE_PLANTS
                || mOpenPage == AlmanacPage.ALMANAC_PAGE_ZOMBIES;
        boolean aShown = aContentPage && numPagesOnScreen() > 1;

        mPrevPageButton.mBtnNoDraw = !aShown;
        mNextPageButton.mBtnNoDraw = !aShown;
        mPrevPageButton.mDisabled = aShown && pageOnScreen() <= 0;
        mNextPageButton.mDisabled = aShown && pageOnScreen() >= numPagesOnScreen() - 1;
        // The arrows carry no art (see makePageButton), so which of them is usable shows in the label
        // colour, exactly like the seed chooser's page arrows.
        mPrevPageButton.setLabelColor(mPrevPageButton.mDisabled ? PAGE_ARROW_DISABLED_COLOR : Color.White);
        mNextPageButton.setLabelColor(mNextPageButton.mDisabled ? PAGE_ARROW_DISABLED_COLOR : Color.White);
    }

    /** Turns the page arrow of the content page on screen, see {@link #buttonDepress(int)}. */
    private void switchContentPage(int theDelta) {
        if (mOpenPage == AlmanacPage.ALMANAC_PAGE_PLANTS) {
            switchPlantPage(mPlantPage + theDelta);
        } else if (mOpenPage == AlmanacPage.ALMANAC_PAGE_ZOMBIES) {
            switchZombiePage(mZombiePage + theDelta);
        }
    }

    // AlmanacDialog.cpp:106-122
    public void clearPlantsAndZombies() {
        if (mPlant != null) {
            mPlant.die();
            mPlant = null;
        }
        if (mZombie != null) {
            mZombie.dieNoLoot();
            mZombie = null;
        }
        for (int i = 0; i < mZombiePerfTest.length; i++) {
            if (mZombiePerfTest[i] != null) {
                mZombiePerfTest[i].dieNoLoot();
                mZombiePerfTest[i] = null;
            }
        }
    }

    // AlmanacDialog.cpp:124-128
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        clearPlantsAndZombies();
    }

    // AlmanacDialog.cpp:130-137
    public void setupPlant() {
        clearPlantsAndZombies();

        float aPosX = ALMANAC_PLANT_POSITION_X;
        float aPosY = ALMANAC_PLANT_POSITION_Y;
        if (mSelectedSeed == SeedType.SEED_TALLNUT) {
            aPosY += 18;
        } else if (mSelectedSeed == SeedType.SEED_COBCANNON) {
            aPosX -= 40;
        } else if (mSelectedSeed == SeedType.SEED_FLOWERPOT) {
            aPosY -= 20;
        } else if (mSelectedSeed == SeedType.SEED_INSTANT_COFFEE) {
            aPosY += 20;
        } else if (mSelectedSeed == SeedType.SEED_GRAVEBUSTER) {
            aPosY += 55;
        }

        mPlant = new Plant();
        mPlant.mBoard = null;
        mPlant.mIsOnBoard = false;
        mPlant.plantInitialize(0, 0, mSelectedSeed, SeedType.SEED_NONE);
        mPlant.mX = (int) aPosX;
        mPlant.mY = (int) aPosY;
    }

    // AlmanacDialog.cpp:139-157
    public void setupZombie() {
        clearPlantsAndZombies();

        mZombie = new Zombie();
        mZombie.mBoard = null;
        mZombie.zombieInitialize(0, mSelectedZombie, false, null, Zombie.ZOMBIE_WAVE_UI);
        mZombie.mPosX = ALMANAC_ZOMBIE_POSITION_X;
        mZombie.mPosY = ALMANAC_ZOMBIE_POSITION_Y;
    }

    // AlmanacDialog.cpp:159-168
    public void setPage(int thePage) {
        mOpenPage = thePage;
        clearPlantsAndZombies();

        if (mOpenPage == AlmanacPage.ALMANAC_PAGE_INDEX) {
            mPlant = new Plant();
            mPlant.mBoard = null;
            mPlant.mIsOnBoard = false;
            mPlant.plantInitialize(0, 0, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
            mPlant.mX = ALMANAC_INDEXPLANT_POSITION_X;
            mPlant.mY = ALMANAC_INDEXPLANT_POSITION_Y;

            mZombie = new Zombie();
            mZombie.mBoard = null;
            mZombie.zombieInitialize(0, ZombieType.ZOMBIE_NORMAL, false, null, Zombie.ZOMBIE_WAVE_UI);
            mZombie.mPosX = ALMANAC_INDEXZOMBIE_POSITION_X;
            mZombie.mPosY = ALMANAC_INDEXZOMBIE_POSITION_Y;
        } else if (mOpenPage == AlmanacPage.ALMANAC_PAGE_PLANTS) {
            // Rebuild before the preview: a page entered later has to see the definition tables as
            // they are now, and the fallback of an unreachable selection has to land on this page.
            rebuildPlantPage();
            setupPlant();
        } else if (mOpenPage == AlmanacPage.ALMANAC_PAGE_ZOMBIES) {
            rebuildZombiePage();
            setupZombie();
        } else {
            return;
        }

        mIndexButton.mBtnNoDraw = indexButtonHidden(mOpenPage);
        mPlantButton.mBtnNoDraw = plantButtonHidden(mOpenPage);
        mZombieButton.mBtnNoDraw = zombieButtonHidden(mOpenPage);
        refreshPageButtons();
    }

    /** AlmanacDialog.cpp:170-186 — the index button is hidden on the index page. */
    public static boolean indexButtonHidden(int thePage) {
        return thePage == AlmanacPage.ALMANAC_PAGE_INDEX;
    }

    /** AlmanacDialog.cpp:170-186 — the plant/zombie buttons are hidden on the two content pages. */
    public static boolean plantButtonHidden(int thePage) {
        return thePage != AlmanacPage.ALMANAC_PAGE_INDEX;
    }

    /** AlmanacDialog.cpp:170-186. */
    public static boolean zombieButtonHidden(int thePage) {
        return thePage != AlmanacPage.ALMANAC_PAGE_INDEX;
    }

    // AlmanacDialog.cpp:188-196
    public void showPlant(int theSeedType) {
        mSelectedSeed = theSeedType;
        setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);
    }

    // AlmanacDialog.cpp:198-202
    public void showZombie(int theZombieType) {
        mSelectedZombie = theZombieType;
        setPage(AlmanacPage.ALMANAC_PAGE_ZOMBIES);
    }

    // AlmanacDialog.cpp:204-234
    @Override
    public void update() {
        mCloseButton.update();
        mIndexButton.update();
        mPlantButton.update();
        mZombieButton.update();
        mPrevPageButton.update();
        mNextPageButton.update();
        if (mPlant != null) {
            mPlant.update();
        }
        if (mZombie != null) {
            mZombie.update();
        }
        for (int i = 0; i < mZombiePerfTest.length; i++) {
            if (mZombiePerfTest[i] != null) {
                mZombiePerfTest[i].update();
            }
        }

        int aMouseX = mApp.mWidgetManager.mLastMouseX;
        int aMouseY = mApp.mWidgetManager.mLastMouseY;
        if (seedHitTest(aMouseX, aMouseY) != SeedType.SEED_NONE
                || zombieHitTest(aMouseX, aMouseY) != ZombieType.ZOMBIE_INVALID
                || mCloseButton.isMouseOver() || mIndexButton.isMouseOver()
                || mPlantButton.isMouseOver() || mZombieButton.isMouseOver()
                || mPrevPageButton.isMouseOver() || mNextPageButton.isMouseOver()) {
            mApp.setCursor(SexyAppBase.CURSOR_HAND);
        } else {
            mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }

        mApp.mPoolEffect.poolEffectUpdate();
        markDirty();
    }

    // AlmanacDialog.cpp:236-239
    public static int getZombieType(int theIndex) {
        return theIndex < ZombieType.NUM_ZOMBIE_TYPES ? theIndex : ZombieType.ZOMBIE_INVALID;
    }

    // AlmanacDialog.cpp:241-256
    public void drawIndex(Graphics g) {
        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_INDEXBACK), 0, 0);
        PvzpCommon.pvzpDrawString(g, "[SUBURBAN_ALMANAC_INDEX]", GameConstants.BOARD_WIDTH / 2, 60,
                Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID), new Color(220, 220, 220),
                DrawStringJustification.DS_ALIGN_CENTER);

        if (mPlant != null) {
            Graphics aPlantGraphics = g.create();
            mPlant.beginDraw(aPlantGraphics);
            mPlant.draw(aPlantGraphics);
        }
        if (mZombie != null) {
            Graphics aZombieGraphics = g.create();
            mZombie.beginDraw(aZombieGraphics);
            mZombie.draw(aZombieGraphics);
        }
    }

    // AlmanacDialog.cpp:258-335
    public void drawPlants(Graphics g) {
        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_PLANTBACK), 0, 0);
        PvzpCommon.pvzpDrawString(g, "[SUBURBAN_ALMANAC_PLANTS]", GameConstants.BOARD_WIDTH / 2, 48,
                Resources.getFontById(Resources.FONT_HOUSEOFTERROR20_ID), new Color(213, 159, 43),
                DrawStringJustification.DS_ALIGN_CENTER);

        int aSeedMouseOn = seedHitTest(mApp.mWidgetManager.mLastMouseX, mApp.mWidgetManager.mLastMouseY);
        // Slot by slot over the page on screen (the port's paging): on page 0 slot n is seed type n, so
        // this draws exactly the upstream loop, and a later page draws the registered seed types above
        // the retail grid in the same squares.
        for (int aSlot = 0; aSlot < mPlantPageSeeds.size(); aSlot++) {
            int aSeedType = mPlantPageSeeds.get(aSlot);
            getSeedSlotPosition(aSlot, mTempPoint);
            if (mApp.hasSeedType(aSeedType)) {
                if (aSeedType == SeedType.SEED_IMITATER) {
                    if (aSeedType == aSeedMouseOn) {
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_IMITATER), mTempPoint.mX, mTempPoint.mY);
                    }
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_IMITATER), mTempPoint.mX, mTempPoint.mY);
                } else {
                    cn.enaium.fabricpvz.portable.lawn.SeedPacket.drawSeedPacket(g, mTempPoint.mX, mTempPoint.mY,
                            aSeedType, SeedType.SEED_NONE, 0, 255, true, false);
                    if (aSeedType == aSeedMouseOn) {
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_SEEDPACKETFLASH), mTempPoint.mX, mTempPoint.mY);
                    }
                }
            }
        }

        if (mSelectedSeed == SeedType.SEED_LILYPAD || mSelectedSeed == SeedType.SEED_TANGLEKELP
                || mSelectedSeed == SeedType.SEED_CATTAIL || mSelectedSeed == SeedType.SEED_SEASHROOM) {
            boolean aNight = mSelectedSeed == SeedType.SEED_SEASHROOM;
            g.drawImage(Resources.getImageById(aNight ? ImageID.IMAGE_ALMANAC_GROUNDNIGHTPOOL
                    : ImageID.IMAGE_ALMANAC_GROUNDPOOL), 521, 107);

            if (mApp.is3DAccelerated()) {
                g.setClipRect(475, 0, 397, 500);
                g.mTransY -= 145;
                mApp.mPoolEffect.poolEffectDraw(g, aNight);
                g.mTransY += 145;
                g.clearClipRect();
            }
        } else {
            g.drawImage(Resources.getImageById(
                    Plant.isNocturnal(mSelectedSeed) || mSelectedSeed == SeedType.SEED_GRAVEBUSTER
                            || mSelectedSeed == SeedType.SEED_PLANTERN ? ImageID.IMAGE_ALMANAC_GROUNDNIGHT
                            : mSelectedSeed == SeedType.SEED_FLOWERPOT ? ImageID.IMAGE_ALMANAC_GROUNDROOF
                            : ImageID.IMAGE_ALMANAC_GROUNDDAY), 521, 107);
        }

        if (mPlant != null) {
            Graphics aPlantGraphics = g.create();
            mPlant.beginDraw(aPlantGraphics);
            mPlant.draw(aPlantGraphics);
        }

        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_PLANTCARD), 459, 86);
        PlantDefinition aPlantDef = Plant.getPlantDefinition(mSelectedSeed);
        String aName = Plant.getNameString(mSelectedSeed, SeedType.SEED_NONE);
        PvzpCommon.pvzpDrawString(g, aName, 617, 288, Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID),
                Color.White, DrawStringJustification.DS_ALIGN_CENTER);
        PvzpStringFile.pvzpDrawStringWrapped(g, getPlantDescriptionText(mSelectedSeed, aPlantDef), mDescriptionRect,
                Resources.getFontById(Resources.FONT_BRIANNETOD12_ID), new Color(40, 50, 90),
                DrawStringJustification.DS_ALIGN_LEFT);

        if (mSelectedSeed != SeedType.SEED_IMITATER) {
            PvzpStringFile.pvzpDrawStringWrapped(g, getPlantCostString(aPlantDef), mCostRect,
                    Resources.getFontById(Resources.FONT_BRIANNETOD12_ID), Color.White,
                    DrawStringJustification.DS_ALIGN_LEFT);
            PvzpStringFile.pvzpDrawStringWrapped(g, getPlantRechargeString(aPlantDef), mRechargeRect,
                    Resources.getFontById(Resources.FONT_BRIANNETOD12_ID), new Color(40, 50, 90),
                    DrawStringJustification.DS_ALIGN_RIGHT);
        }
    }

    /**
     * LawnStrings key of a plant's almanac description, e.g. {@code [PEASHOOTER_DESCRIPTION]}
     * (AlmanacDialog.cpp:337; key stem from {@code gPlantDefs[].mPlantName}, Plant.cpp:48).
     */
    public static String getPlantDescriptionKey(PlantDefinition thePlantDefinition) {
        return "[" + thePlantDefinition.mPlantName + "_DESCRIPTION]";
    }

    /**
     * Cost stat line (AlmanacDialog.cpp:339-340): {@code {KEYWORD}{COST}:{STAT} <seedCost>} with
     * {@code {COST}} substituted for the {@code [COST]} LawnStrings key.
     */
    public static String getPlantCostString(PlantDefinition thePlantDefinition) {
        String aCostStr = "{KEYWORD}{COST}:{STAT} " + thePlantDefinition.mSeedCost;
        return PvzpCommon.pvzpReplaceString(aCostStr, "{COST}", "[COST]");
    }

    /**
     * Recharge stat line (AlmanacDialog.cpp:342-348): {@code {WAIT_TIME}} and {@code {WAIT_TIME_LENGTH}}
     * become the {@code [WAIT_TIME]} / {@code [WAIT_TIME_SHORT|LONG|VERY_LONG]} LawnStrings keys
     * chosen from {@code mRefreshTime} (750 / 3000 / anything else).
     */
    public static String getPlantRechargeString(PlantDefinition thePlantDefinition) {
        String aRechargeStr = PvzpCommon.pvzpReplaceString("{KEYWORD}{WAIT_TIME}: {STAT}{WAIT_TIME_LENGTH}",
                "{WAIT_TIME_LENGTH}",
                thePlantDefinition.mRefreshTime == 750 ? "[WAIT_TIME_SHORT]"
                        : thePlantDefinition.mRefreshTime == 3000 ? "[WAIT_TIME_LONG]" : "[WAIT_TIME_VERY_LONG]");
        return PvzpCommon.pvzpReplaceString(aRechargeStr, "{WAIT_TIME}", "[WAIT_TIME]");
    }

    /** LawnStrings key of a zombie's almanac description, e.g. {@code [ZOMBIE_DESCRIPTION]} (AlmanacDialog.cpp:381). */
    public static String getZombieDescriptionKey(ZombieDefinition theZombieDefinition) {
        return "[" + theZombieDefinition.mZombieName + "_DESCRIPTION]";
    }

    /** Zombie display name (AlmanacDialog.cpp:377): the {@code [<ZOMBIE_NAME>]} key, or {@code ???} while a silhouette. */
    public static String getZombieNameString(ZombieDefinition theZombieDefinition, boolean silhouette) {
        return silhouette ? "???" : "[" + theZombieDefinition.mZombieName + "]";
    }

    /**
     * Almanac description of a plant: the {@code [<PLANT_NAME>_DESCRIPTION]} LawnStrings key.
     */
    public static String getPlantDescriptionText(int theSeedType, PlantDefinition thePlantDefinition) {
        return getPlantDescriptionKey(thePlantDefinition);
    }

    /** Zombie display name (AlmanacDialog.cpp:377): the {@code [<ZOMBIE_NAME>]} key, or {@code ???} while a silhouette. */
    public static String getZombieNameText(int theZombieType, ZombieDefinition theZombieDefinition, boolean silhouette) {
        return getZombieNameString(theZombieDefinition, silhouette);
    }

    /**
     * Almanac description of a zombie: the translated {@code [<ZOMBIE_NAME>_DESCRIPTION]} LawnStrings
     * key.
     */
    public static String getZombieDescriptionText(int theZombieType, ZombieDefinition theZombieDefinition) {
        return PvzpStringFile.translate(getZombieDescriptionKey(theZombieDefinition));
    }

    // AlmanacDialog.cpp:356-495
    public void drawZombies(Graphics g) {
        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_ZOMBIEBACK), 0, 0);
        PvzpCommon.pvzpDrawString(g, "[SUBURBAN_ALMANAC_ZOMBIES]", GameConstants.BOARD_WIDTH / 2, 54,
                Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID), new Color(0, 196, 0),
                DrawStringJustification.DS_ALIGN_CENTER);

        int aZombieMouseOn = zombieHitTest(mApp.mWidgetManager.mLastMouseX, mApp.mWidgetManager.mLastMouseY);
        // Slot by slot over the page on screen, the zombie half of DrawPlants' loop. Page 0 keeps
        // upstream's encounter gate ({@code ZombieIsShown}); a page after it holds the defined zombies
        // above the retail range, for which there is no adventure progress to gate on.
        for (int aSlot = 0; aSlot < mZombiePageZombies.size(); aSlot++) {
            int aZombieType = mZombiePageZombies.get(aSlot);
            getZombieSlotPosition(aSlot, mTempPoint);
            if (aZombieType != ZombieType.ZOMBIE_INVALID) {
                if (mZombiePage == 0 && !zombieIsShown(aZombieType)) {
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_ZOMBIEBLANK), mTempPoint.mX, mTempPoint.mY);
                } else {
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_ZOMBIEWINDOW), mTempPoint.mX, mTempPoint.mY);
                    if (aZombieType == aZombieMouseOn) {
                        g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
                        g.setColor(new Color(255, 255, 255, 48));
                        g.setColorizeImages(true);
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_ZOMBIEWINDOW), mTempPoint.mX, mTempPoint.mY);
                        g.setDrawMode(Graphics.DRAWMODE_NORMAL);
                        g.setColorizeImages(false);
                    }

                    int aZombieTypeToDraw = aZombieType;
                    Graphics aZombieGraphics = g.create();
                    aZombieGraphics.setClipRect(mTempPoint.mX + 2, mTempPoint.mY + 2, 72, 72);
                    aZombieGraphics.translate(mTempPoint.mX + 1, mTempPoint.mY - 6);
                    aZombieGraphics.mScaleX = 0.5f;
                    aZombieGraphics.mScaleY = 0.5f;
                    if (aZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
                        aZombieGraphics.translateF(2, -3);
                        aZombieTypeToDraw = ZombieType.ZOMBIE_CACHED_POLEVAULTER_WITH_POLE;
                    } else if (aZombieType == ZombieType.ZOMBIE_FLAG) {
                        aZombieGraphics.translateF(2, 10);
                    } else if (aZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE) {
                        aZombieGraphics.translateF(0, 12);
                    } else if (aZombieType == ZombieType.ZOMBIE_PAIL) {
                        aZombieGraphics.translateF(0, 9);
                    } else if (aZombieType == ZombieType.ZOMBIE_FOOTBALL) {
                        aZombieGraphics.translateF(-15, -1);
                    } else if (aZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                        aZombieGraphics.translateF(0, 3);
                    } else if (aZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER) {
                        aZombieGraphics.translateF(-2, -10);
                    } else if (aZombieType == ZombieType.ZOMBIE_POGO) {
                        aZombieGraphics.translateF(0, -3);
                    } else if (aZombieType == ZombieType.ZOMBIE_GARGANTUAR) {
                        aZombieGraphics.translateF(15, 17);
                    } else if (aZombieType == ZombieType.ZOMBIE_IMP) {
                        aZombieGraphics.translateF(-8, -7);
                    } else if (aZombieType == ZombieType.ZOMBIE_BUNGEE) {
                        aZombieGraphics.translateF(-4, 3);
                    } else if (aZombieType == ZombieType.ZOMBIE_DANCER) {
                        aZombieGraphics.translateF(0, 15);
                    } else if (aZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
                        aZombieGraphics.translateF(-4, 20);
                    } else if (aZombieType == ZombieType.ZOMBIE_SNORKEL) {
                        aZombieGraphics.translateF(-10, 0);
                    } else if (aZombieType == ZombieType.ZOMBIE_YETI) {
                        aZombieGraphics.translateF(0, 4);
                    } else if (aZombieType == ZombieType.ZOMBIE_CATAPULT) {
                        aZombieGraphics.translateF(-24, -1);
                    } else if (aZombieType == ZombieType.ZOMBIE_BOBSLED) {
                        aZombieGraphics.translateF(0, -8);
                    } else if (aZombieType == ZombieType.ZOMBIE_LADDER) {
                        aZombieGraphics.translateF(0, -3);
                    }
                    if (zombieHasSilhouette(aZombieType)) {
                        aZombieGraphics.setColor(new Color(0, 0, 0, 40));
                        aZombieGraphics.setColorizeImages(true);
                    }
                    mApp.mReanimatorCache.drawCachedZombie(aZombieGraphics, 0, 0, aZombieTypeToDraw);
                    aZombieGraphics.setColorizeImages(false);

                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_ZOMBIEWINDOW2), mTempPoint.mX, mTempPoint.mY);
                    if (aZombieType == aZombieMouseOn) {
                        g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
                        g.setColor(new Color(255, 255, 255, 48));
                        g.setColorizeImages(true);
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_ZOMBIEWINDOW2), mTempPoint.mX, mTempPoint.mY);
                        g.setDrawMode(Graphics.DRAWMODE_NORMAL);
                        g.setColorizeImages(false);
                    }
                }
            }
        }

        g.drawImage(Resources.getImageById(mZombie.mZombieType == ZombieType.ZOMBIE_ZAMBONI
                || mZombie.mZombieType == ZombieType.ZOMBIE_BOBSLED ? ImageID.IMAGE_ALMANAC_GROUNDICE
                : ImageID.IMAGE_ALMANAC_GROUNDDAY), 518, 110);
        if (mZombie != null && !zombieHasSilhouette(mZombie.mZombieType)) {
            Graphics aZombieGraphics = g.create();
            mZombie.beginDraw(aZombieGraphics);
            aZombieGraphics.setClipRect(-42, -51, 197, 187);
            if (mZombie.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                aZombieGraphics.translateF(-30, 5);
            } else if (mZombie.mZombieType == ZombieType.ZOMBIE_GARGANTUAR) {
                aZombieGraphics.translateF(0, 40);
            } else if (mZombie.mZombieType == ZombieType.ZOMBIE_FOOTBALL) {
                aZombieGraphics.translateF(-10, 0);
            } else if (mZombie.mZombieType == ZombieType.ZOMBIE_BALLOON) {
                aZombieGraphics.translateF(0, -20);
            } else if (mZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
                aZombieGraphics.translateF(15, 0);
            } else if (mZombie.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
                aZombieGraphics.translateF(-10, 0);
            } else if (mZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                aZombieGraphics.translateF(-540, -175);
            }
            if (mZombie.mZombieType != ZombieType.ZOMBIE_BUNGEE && mZombie.mZombieType != ZombieType.ZOMBIE_BOSS
                    && mZombie.mZombieType != ZombieType.ZOMBIE_ZAMBONI
                    && mZombie.mZombieType != ZombieType.ZOMBIE_CATAPULT) {
                mZombie.drawShadow(aZombieGraphics);
            }
            mZombie.draw(aZombieGraphics);
        }
        g.drawImage(Resources.getImageById(ImageID.IMAGE_ALMANAC_ZOMBIECARD), 455, 78);

        ZombieDefinition aZombieDef = Zombie.getZombieDefinition(mSelectedZombie);
        boolean aSilhouette = zombieHasSilhouette(mSelectedZombie);
        PvzpCommon.pvzpDrawString(g, getZombieNameText(mSelectedZombie, aZombieDef, aSilhouette), 613, 362,
                Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID),
                new Color(190, 255, 235, 255), DrawStringJustification.DS_ALIGN_CENTER);

        String aDescription;
        int aAlign;
        if (zombieHasDescription(mSelectedZombie)) {
            aDescription = getZombieDescriptionText(mSelectedZombie, aZombieDef);
            aAlign = DrawStringJustification.DS_ALIGN_LEFT;
        } else {
            aDescription = "[NOT_ENCOUNTERED_YET]";
            aAlign = DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE;
        }
        for (PvzpStringListFormat aFormat : PvzpStringFile.gLawnStringFormats) {
            if (PvzpCommon.testBit(aFormat.mFormatFlags, PvzpStringFormatFlag.PVZP_FORMAT_HIDE_UNTIL_MAGNETSHROOM)) {
                if (mApp.hasSeedType(SeedType.SEED_MAGNETSHROOM)) {
                    aFormat.mNewColor.mAlpha = 255;
                    aFormat.mLineSpacingOffset = 0;
                } else {
                    aFormat.mNewColor.mAlpha = 0;
                    aFormat.mLineSpacingOffset = -17;
                }
            }
        }
        mZombieTextRect.mY = mSelectedZombie == ZombieType.ZOMBIE_ZAMBONI ? 372 : 377;
        PvzpStringFile.pvzpDrawStringWrapped(g, aDescription, mZombieTextRect,
                Resources.getFontById(Resources.FONT_BRIANNETOD12_ID), new Color(40, 50, 90), aAlign);
    }

    // AlmanacDialog.cpp:497-519
    @Override
    public void draw(Graphics g) {
        g.setLinearBlend(true);
        if (mOpenPage == AlmanacPage.ALMANAC_PAGE_INDEX) {
            drawIndex(g);
        } else if (mOpenPage == AlmanacPage.ALMANAC_PAGE_PLANTS) {
            drawPlants(g);
        } else if (mOpenPage == AlmanacPage.ALMANAC_PAGE_ZOMBIES) {
            drawZombies(g);
        }

        for (int i = 0; i < mZombiePerfTest.length; i++) {
            if (mZombiePerfTest[i] != null) {
                Graphics aTestGraphics = g.create();
                mZombiePerfTest[i].draw(aTestGraphics);
            }
        }

        mCloseButton.draw(g);
        mIndexButton.draw(g);
        mPlantButton.draw(g);
        mZombieButton.draw(g);
        mPrevPageButton.draw(g);
        mNextPageButton.draw(g);
        drawPageCounter(g);
    }

    /**
     * The "{@code page/total}" counter of the content grid on screen, in the gap the two arrows leave
     * (the arrows themselves are widgets and draw in {@link #draw}). Drawn only while that grid has
     * more than one page, so a single-page almanac stays exactly the upstream one.
     */
    private void drawPageCounter(Graphics g) {
        if (mOpenPage != AlmanacPage.ALMANAC_PAGE_PLANTS && mOpenPage != AlmanacPage.ALMANAC_PAGE_ZOMBIES) {
            return;
        }
        if (numPagesOnScreen() <= 1) {
            return;
        }

        Font aFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18YELLOW_ID);
        PvzpCommon.pvzpDrawString(g, (pageOnScreen() + 1) + "/" + numPagesOnScreen(), ALMANAC_PAGE_COUNTER_X,
                ALMANAC_PAGE_COUNTER_Y + (ALMANAC_PAGE_BUTTON_HEIGHT + aFont.getAscent()) / 2 - 1, aFont,
                Color.White, DrawStringJustification.DS_ALIGN_CENTER);
    }

    /**
     * The page arrows' press. Upstream routes the close/index/plant/zombie buttons through
     * {@code MouseUp} (AlmanacDialog.cpp:639-645) because {@code GameButton} is not a widget; the
     * arrows are the port's own and take the same path, through the dialog's button handler so their
     * ids arrive here like a framework button's would.
     */
    @Override
    public void buttonDepress(int theId) {
        if (theId == ALMANAC_BUTTON_PREV_PAGE) {
            switchContentPage(-1);
        } else if (theId == ALMANAC_BUTTON_NEXT_PAGE) {
            switchContentPage(1);
        } else {
            super.buttonDepress(theId);
        }
    }

    // AlmanacDialog.cpp:522-531
    public static void getSeedPosition(int theSeedType, Point out) {
        if (theSeedType == SeedType.SEED_IMITATER) {
            out.mX = 20;
            out.mY = 23;
        } else {
            out.mX = theSeedType % 8 * 52 + 26;
            out.mY = theSeedType / 8 * 78 + 92;
        }
    }

    // AlmanacDialog.cpp:533-549
    public int seedHitTest(int x, int y) {
        if (mMouseVisible && mOpenPage == AlmanacPage.ALMANAC_PAGE_PLANTS) {
            for (int aSlot = 0; aSlot < mPlantPageSeeds.size(); aSlot++) {
                int aSeedType = mPlantPageSeeds.get(aSlot);
                if (mApp.hasSeedType(aSeedType)) {
                    getSeedSlotPosition(aSlot, mTempPoint);
                    mHitRect.mX = mTempPoint.mX;
                    mHitRect.mY = mTempPoint.mY;
                    mHitRect.mWidth = aSeedType == SeedType.SEED_IMITATER ? 34 : GameConstants.SEED_PACKET_WIDTH;
                    mHitRect.mHeight = aSeedType == SeedType.SEED_IMITATER ? 46 : GameConstants.SEED_PACKET_HEIGHT;
                    if (mHitRect.contains(x, y)) {
                        return aSeedType;
                    }
                }
            }
        }
        return SeedType.SEED_NONE;
    }

    // AlmanacDialog.cpp:551-559
    public boolean zombieHasSilhouette(int theZombieType) {
        // only the Yeti can be a silhouette, and not once it can spawn
        if (theZombieType != ZombieType.ZOMBIE_YETI || mApp.canSpawnYetis()) {
            return false;
        }

        // the silhouette shows once adventure is finished or the Yeti's debut level is passed
        return mApp.hasFinishedAdventure()
                || mApp.mPlayerInfo.getLevel() > Zombie.getZombieDefinition(ZombieType.ZOMBIE_YETI).mStartingLevel;
    }

    // AlmanacDialog.cpp:561-585
    public boolean zombieIsShown(int theZombieType) {
        // trial mode only shows zombies up to the Snorkel Zombie
        if (mApp.isTrialStageLocked() && theZombieType > ZombieType.ZOMBIE_SNORKEL) {
            return false;
        }

        // the Yeti is shown once it can spawn, or earlier as a silhouette
        if (theZombieType == ZombieType.ZOMBIE_YETI) {
            return mApp.canSpawnYetis() || zombieHasSilhouette(ZombieType.ZOMBIE_YETI);
        }

        // zombies encountered in adventure mode
        if (theZombieType <= ZombieType.ZOMBIE_BOSS) {
            // once adventure is finished, every zombie is shown
            if (mApp.hasFinishedAdventure()) {
                return true;
            }

            int aLevel = mApp.mPlayerInfo.getLevel();
            int aStart = Zombie.getZombieDefinition(theZombieType).mStartingLevel;
            // must have reached the zombie's debut level; spawn-only zombies also require passing it
            // or defeating the zombie once
            return aStart <= aLevel && (aStart != aLevel || !Board.isZombieTypeSpawnedOnly(theZombieType)
                    || gZombieDefeated[theZombieType]);
        }

        return false;
    }

    // AlmanacDialog.cpp:587-607
    public boolean zombieHasDescription(int theZombieType) {
        int aLevel = mApp.mPlayerInfo.getLevel();
        int aStart = Zombie.getZombieDefinition(theZombieType).mStartingLevel;

        if (theZombieType == ZombieType.ZOMBIE_YETI) {
            // no description until the Yeti can spawn
            if (!mApp.canSpawnYetis()) {
                return false;
            }
            // from the third playthrough on, the description always shows
            if (mApp.mPlayerInfo.mFinishedAdventure >= 2) {
                return true;
            }
        } else if (mApp.hasFinishedAdventure()) {
            // other zombies always show their description once adventure is finished
            return true;
        }

        // otherwise require reaching the zombie's debut level, plus passing it or defeating the zombie
        return aStart <= aLevel && (aStart != aLevel || gZombieDefeated[theZombieType]);
    }

    // AlmanacDialog.cpp:609-618
    public static void getZombiePosition(int theZombieType, Point out) {
        if (theZombieType == ZombieType.ZOMBIE_BOSS) {
            out.mX = 192;
            out.mY = 486;
        } else {
            out.mX = theZombieType % 5 * 85 + 22;
            out.mY = theZombieType / 5 * 80 + 86;
        }
    }

    // AlmanacDialog.cpp:620-637
    public int zombieHitTest(int x, int y) {
        if (mMouseVisible && mOpenPage == AlmanacPage.ALMANAC_PAGE_ZOMBIES) {
            for (int aSlot = 0; aSlot < mZombiePageZombies.size(); aSlot++) {
                int aZombieType = mZombiePageZombies.get(aSlot);
                if (aZombieType != ZombieType.ZOMBIE_INVALID
                        && (mZombiePage != 0 || zombieIsShown(aZombieType))) {
                    getZombieSlotPosition(aSlot, mTempPoint);
                    mHitRect.mX = mTempPoint.mX;
                    mHitRect.mY = mTempPoint.mY;
                    mHitRect.mWidth = 76;
                    mHitRect.mHeight = 76;
                    if (mHitRect.contains(x, y)) {
                        return aZombieType;
                    }
                }
            }
        }
        return ZombieType.ZOMBIE_INVALID;
    }

    // AlmanacDialog.cpp:639-645
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
        if (mPlantButton.isMouseOver()) {
            setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);
        } else if (mZombieButton.isMouseOver()) {
            setPage(AlmanacPage.ALMANAC_PAGE_ZOMBIES);
        } else if (mCloseButton.isMouseOver()) {
            mApp.killAlmanacDialog();
        } else if (mIndexButton.isMouseOver()) {
            setPage(AlmanacPage.ALMANAC_PAGE_INDEX);
        } else if (mPrevPageButton.isMouseOver()) {
            buttonDepress(ALMANAC_BUTTON_PREV_PAGE);
        } else if (mNextPageButton.isMouseOver()) {
            buttonDepress(ALMANAC_BUTTON_NEXT_PAGE);
        }
    }

    // AlmanacDialog.cpp:647-668
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        if (mPlantButton.isMouseOver() || mCloseButton.isMouseOver() || mIndexButton.isMouseOver()) {
            mApp.playSample(SoundType.SOUND_TAP);
        }
        if (mZombieButton.isMouseOver()) {
            mApp.playSample(SoundType.SOUND_GRAVEBUTTON);
        }

        int aSeedType = seedHitTest(x, y);
        if (aSeedType != SeedType.SEED_NONE && aSeedType != mSelectedSeed) {
            mSelectedSeed = aSeedType;
            setupPlant();
            mApp.playSample(SoundType.SOUND_TAP);
        }
        int aZombieType = zombieHitTest(x, y);
        if (aZombieType != ZombieType.ZOMBIE_INVALID && aZombieType != mSelectedZombie) {
            mSelectedZombie = aZombieType;
            setupZombie();
            mApp.playSample(SoundType.SOUND_TAP);
        }
    }

    // AlmanacDialog.cpp:670-682
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            if (mOpenPage == AlmanacPage.ALMANAC_PAGE_INDEX) {
                mApp.killAlmanacDialog();
            } else {
                setPage(AlmanacPage.ALMANAC_PAGE_INDEX);
            }
            return;
        }

        super.keyDown(theKey);
    }

    // AlmanacDialog.cpp:684-688
    public static void almanacInitForPlayer() {
        for (int i = 0; i < ZombieType.NUM_ZOMBIE_TYPES; i++) {
            gZombieDefeated[i] = false;
        }
    }

    // AlmanacDialog.cpp:690-693
    public static void almanacPlayerDefeatedZombie(int theZombieType) {
        gZombieDefeated[theZombieType] = true;
    }
}
