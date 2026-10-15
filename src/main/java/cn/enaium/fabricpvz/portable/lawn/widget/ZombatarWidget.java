/*
 * Ported from src/Lawn/Widget/ZombatarWidget.h:1-158 and src/Lawn/Widget/ZombatarWidget.cpp:1-1551.
 *
 * The full Zombatar editor screen: a slide-in screen owned by GameSelector that lets the player
 * build, browse, save and delete zombatar heads. It has a list state (portrait strip + delete),
 * a create state (9 category tabs, a paged part grid, a colour swatch palette for colour-capable
 * parts) and a confirm state with a slide transition, plus a live preview built from a real flag
 * Zombie whose head is a REANIM_ZOMBATAR_HEAD reanim driven by the draft record.
 *
 * Porting notes:
 *  - All child buttons are added with {@code addWidget} (WidgetContainer), i.e. they use the same
 *    dialog-local coordinates upstream passes to `NewLawnButton::Resize` (ZombatarWidget.cpp:274-332,
 *    :1247-1279); the widget itself is a top-level widget positioned at mX = 0 once the selector has
 *    slid it in (upstream `Resize(800, 0, BOARD_WIDTH, BOARD_HEIGHT)`).
 *  - `GraphicsStateGuard` clip scopes (clothes-cell skin tint, TOS body) are expressed with a child
 *    {@link Graphics} so the parent's clip/colour state does not leak.
 *  - The saved records are the raw 0x48-byte blocks in PlayerInfo.mZombatarData; encode/decode use
 *    the upstream slot helpers in {@link cn.enaium.fabricpvz.portable.lawn.Zombatar}.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.Zombatar;
import cn.enaium.fabricpvz.portable.lawn.Zombatar.ZombatarPage;
import cn.enaium.fabricpvz.portable.lawn.Zombatar.ZombatarPartLayout;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;

import static cn.enaium.fabricpvz.portable.lawn.Zombatar.NUM_ZOMBATAR_PAGES;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/ZombatarWidget.h:45-98 (declaration) / src/Lawn/Widget/ZombatarWidget.cpp:274
 * (constructor).
 */
public class ZombatarWidget extends Widget implements ButtonListener {

    // ZombatarWidget.h:36-43 — enum ZombatarWidgetState.
    public enum ZombatarWidgetState {
        LIST,
        CREATE,
        CONFIRM,
        TO_CONFIRM,
        FROM_CONFIRM
    }

    // ZombatarWidget.h:52-62 (button ids).
    public static final int ZOMBATAR_BTN_BACK = 300;
    public static final int ZOMBATAR_BTN_VIEW = 301;
    public static final int ZOMBATAR_BTN_FINISHED = 302;
    public static final int ZOMBATAR_BTN_NEW = 303;
    public static final int ZOMBATAR_BTN_CONFIRM_BACK = 304;
    public static final int ZOMBATAR_BTN_PREV_PORTRAIT = 305;
    public static final int ZOMBATAR_BTN_NEXT_PORTRAIT = 306;
    public static final int ZOMBATAR_BTN_PREV_PAGE = 307;
    public static final int ZOMBATAR_BTN_NEXT_PAGE = 308;

    // ZombatarWidget.cpp:27-33
    public static final int ZOMBATAR_COLOR_NONE = -1;
    public static final int ZOMBATAR_SKIN_COLOR_COUNT = 12;
    public static final int ZOMBATAR_PART_COLOR_COUNT = 18;
    public static final int ZOMBATAR_PART_COLOR_BASE = 12;
    public static final int ZOMBATAR_PART_COLOR_BASE_2 = 30;
    public static final int ZOMBATAR_PART_COLOR_NONE_1 = 29;
    public static final int ZOMBATAR_PART_COLOR_NONE_2 = 47;

    // ZombatarWidget.cpp:35-38
    public static final int ZOMBATAR_COLOR_MODE_SKIN = 0;
    public static final int ZOMBATAR_COLOR_MODE_1 = 1;
    public static final int ZOMBATAR_COLOR_MODE_2 = 2;
    public static final int ZOMBATAR_COLOR_MODE_NONE = 4;

    // ZombatarWidget.cpp:40-47
    public static final int ZOMBATAR_PANEL_X = 25;
    public static final int ZOMBATAR_PANEL_Y = 25;
    public static final int ZOMBATAR_PANEL_WIDTH = 560;
    public static final int ZOMBATAR_INNER_X = 152;
    public static final int ZOMBATAR_INNER_Y = 125;
    public static final int ZOMBATAR_COLORS_X = 221;
    public static final int ZOMBATAR_COLORS_Y = 335;
    public static final int ZOMBATAR_PREVIEW_X = 592;
    public static final int ZOMBATAR_PREVIEW_Y = 115;

    // ZombatarWidget.cpp:49-50
    public static final int ZOMBATAR_TABS_X = 58;
    public static final int ZOMBATAR_TABS_Y0 = 128;

    // ZombatarWidget.cpp:52-57
    public static final int ZOMBATAR_GRID_COLS = 6;
    public static final int ZOMBATAR_GRID_PAGE = 17;
    public static final int ZOMBATAR_GRID_GAP = -4;
    public static final int ZOMBATAR_GRID_BIAS_X = 50;
    public static final int ZOMBATAR_CELL_INSET = 9;
    public static final int ZOMBATAR_CELL_ZOMBIE_MARGIN = 10;

    // ZombatarWidget.cpp:58-62
    public static final int ZOMBATAR_ALIGN_TOP = 0x02;
    public static final int ZOMBATAR_ALIGN_BOTTOM = 0x04;
    public static final int ZOMBATAR_ALIGN_LEFT = 0x08;
    public static final int ZOMBATAR_ALIGN_RIGHT = 0x10;
    public static final Color ZOMBATAR_CELL_DIM_COLOR = new Color(0x80, 0x80, 0x80, 0x80);

    // ZombatarWidget.cpp:64-69
    public static final int ZOMBATAR_COLOR_COLS = 9;
    public static final int ZOMBATAR_COLOR_GAP = 4;
    public static final int ZOMBATAR_COLOR_X = 238;
    public static final int ZOMBATAR_COLOR_Y = 367;
    public static final int ZOMBATAR_COLOR_HINT_X = 240;
    public static final int ZOMBATAR_COLOR_HINT_Y = 380;

    // ZombatarWidget.cpp:71-73
    public static final int ZOMBATAR_START_TEXT_Y = 185;
    public static final int ZOMBATAR_START_TEXT_WIDTH = 500;
    public static final int ZOMBATAR_START_TEXT_HEIGHT = 100;

    // ZombatarWidget.cpp:75-86
    public static final int ZOMBATAR_BACK_X = 278;
    public static final int ZOMBATAR_BACK_Y = 528;
    public static final int ZOMBATAR_VIEW_X = 65;
    public static final int ZOMBATAR_VIEW_Y = 472;
    public static final int ZOMBATAR_FINISHED_X = 445;
    public static final int ZOMBATAR_FINISHED_Y = 472;
    public static final int ZOMBATAR_ACCEPT_X = 155;
    public static final int ZOMBATAR_CONFIRM_BACK_X = 385;
    public static final int ZOMBATAR_CONFIRM_BTN_Y = 345;
    public static final int ZOMBATAR_NEW_X = 195;
    public static final int ZOMBATAR_NEW_Y = 395;
    public static final int ZOMBATAR_PREV_PORTRAIT_X = 120;
    public static final int ZOMBATAR_NEXT_PORTRAIT_X = 467;
    public static final int ZOMBATAR_PORTRAIT_BTN_Y = 398;
    public static final int ZOMBATAR_PREV_PAGE_X = 175;
    public static final int ZOMBATAR_NEXT_PAGE_X = 497;
    public static final int ZOMBATAR_PAGE_BTN_Y = 372;

    // ZombatarWidget.cpp:88-94
    public static final int ZOMBATAR_LIST_PORTRAIT_Y = 175;
    public static final int ZOMBATAR_LIST_SPACING = 40;
    public static final int ZOMBATAR_LIST_CENTER_INSET = 30;
    public static final int ZOMBATAR_LIST_CLIP_X = 58;
    public static final int ZOMBATAR_LIST_CLIP_Y = 125;
    public static final int ZOMBATAR_LIST_CLIP_RIGHT = 63;
    public static final int ZOMBATAR_LIST_CLIP_HEIGHT = 437;
    public static final int ZOMBATAR_LIST_FILL_HEIGHT = 331;
    public static final int ZOMBATAR_LIST_BLANK_WIDTH = 179;
    public static final int ZOMBATAR_LIST_COUNTER_X = 221;
    public static final int ZOMBATAR_LIST_COUNTER_Y = 161;
    public static final int ZOMBATAR_LIST_DELETE_X = 351;
    public static final int ZOMBATAR_LIST_DELETE_Y = 161;
    public static final int ZOMBATAR_LIST_DELETE_RECT_X = 346;
    public static final int ZOMBATAR_LIST_DELETE_RECT_Y = 146;
    public static final int ZOMBATAR_LIST_DELETE_RECT_WIDTH = 60;
    public static final int ZOMBATAR_LIST_DELETE_RECT_HEIGHT = 20;

    // ZombatarWidget.cpp:96-102
    public static final int ZOMBATAR_AVATAR_GROUND_X = 600;
    public static final int ZOMBATAR_AVATAR_GROUND_Y = 300;
    public static final int ZOMBATAR_AVATAR_ZOMBIE_X = ZOMBATAR_AVATAR_GROUND_X + 40;
    public static final int ZOMBATAR_AVATAR_ZOMBIE_Y = ZOMBATAR_AVATAR_GROUND_Y + 50;
    public static final int ZOMBATAR_AVATAR_GROUND_CLIP_TOP = 10;
    public static final int ZOMBATAR_AVATAR_GROUND_CLIP_RIGHT = 40;
    public static final int ZOMBATAR_AVATAR_GROUND_CLIP_BOTTOM = 10;

    // ZombatarWidget.cpp:104-111
    public static final int ZOMBATAR_CONFIRM_HEADER_X = 305;
    public static final int ZOMBATAR_CONFIRM_HEADER_Y = 185;
    public static final int ZOMBATAR_CONFIRM_TEXT_X = 60;
    public static final int ZOMBATAR_CONFIRM_TEXT_Y = 225;
    public static final int ZOMBATAR_CONFIRM_TEXT_WIDTH = 500;
    public static final int ZOMBATAR_CONFIRM_TEXT_HEIGHT = 100;
    public static final int ZOMBATAR_CONFIRM_ACCEPT_LABEL_X = 195;
    public static final int ZOMBATAR_CONFIRM_BACK_LABEL_X = 435;
    public static final int ZOMBATAR_CONFIRM_LABEL_Y = 335;

    // ZombatarWidget.cpp:113-118
    public static final int ZOMBATAR_TRANSITION_TICKS = 90;
    public static final int ZOMBATAR_VEIL_X = 58;
    public static final int ZOMBATAR_VEIL_Y = 125;
    public static final int ZOMBATAR_VEIL_RIGHT_INSET = 63;
    public static final int ZOMBATAR_VEIL_HEIGHT = 331;
    public static final Color ZOMBATAR_PAGE_BTN_DISABLED_TINT = new Color(108, 109, 140, 40);

    // ZombatarWidget.cpp:272
    public static final int[] ZOMBATAR_ITEMS_PER_PAGE = {0, 16, 24, 14, 16, 12, 15, 14, 5};

    // ZombatarWidget.cpp:671-697 — per-page category tab images (normal / highlight / over), indexed
    // by ZombatarPage ordinal. The skin tab reuses its highlight for the hover image upstream.
    private static final int[][] CATEGORY_IMAGES = {
            {Resources.IMAGE_ZOMBATAR_SKIN_BUTTON_ID, Resources.IMAGE_ZOMBATAR_SKIN_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_SKIN_BUTTON_HIGHLIGHT_ID},
            {Resources.IMAGE_ZOMBATAR_HAIR_BUTTON_ID, Resources.IMAGE_ZOMBATAR_HAIR_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_HAIR_BUTTON_OVER_ID},
            {Resources.IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_ID,
                    Resources.IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_OVER_ID},
            {Resources.IMAGE_ZOMBATAR_TIDBITS_BUTTON_ID, Resources.IMAGE_ZOMBATAR_TIDBITS_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_TIDBITS_BUTTON_OVER_ID},
            {Resources.IMAGE_ZOMBATAR_EYEWEAR_BUTTON_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_EYEWEAR_BUTTON_OVER_ID},
            {Resources.IMAGE_ZOMBATAR_CLOTHES_BUTTON_ID, Resources.IMAGE_ZOMBATAR_CLOTHES_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_CLOTHES_BUTTON_OVER_ID},
            {Resources.IMAGE_ZOMBATAR_ACCESSORY_BUTTON_ID,
                    Resources.IMAGE_ZOMBATAR_ACCESSORY_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_ACCESSORY_BUTTON_OVER_ID},
            {Resources.IMAGE_ZOMBATAR_HATS_BUTTON_ID, Resources.IMAGE_ZOMBATAR_HATS_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_HATS_BUTTON_OVER_ID},
            {Resources.IMAGE_ZOMBATAR_BACKDROPS_BUTTON_ID,
                    Resources.IMAGE_ZOMBATAR_BACKDROPS_BUTTON_HIGHLIGHT_ID,
                    Resources.IMAGE_ZOMBATAR_BACKDROPS_BUTTON_OVER_ID}
    };

    // ZombatarWidget.cpp:699-709 — the five backdrop images (indices 0..4).
    private static final int[] BACKGROUND_IMAGES = {
            Resources.IMAGE_ZOMBATAR_BACKGROUND_CRAZYDAVE_ID,
            Resources.IMAGE_ZOMBATAR_BACKGROUND_MENU_ID,
            Resources.IMAGE_ZOMBATAR_BACKGROUND_MENU_DOS_ID,
            Resources.IMAGE_ZOMBATAR_BACKGROUND_ROOF_ID,
            Resources.IMAGE_ZOMBATAR_BACKGROUND_BLANK_ID
    };

    // ZombatarWidget.cpp:713-722 — per-page part images; the accessory table skips index 7 and ends
    // with _15/_16 (upstream id table order, Resources.h:840-853,896-897).
    private static final int[] CLOTHES_IMAGES = {Resources.IMAGE_ZOMBATAR_CLOTHES_1_ID,
            Resources.IMAGE_ZOMBATAR_CLOTHES_2_ID, Resources.IMAGE_ZOMBATAR_CLOTHES_3_ID,
            Resources.IMAGE_ZOMBATAR_CLOTHES_4_ID, Resources.IMAGE_ZOMBATAR_CLOTHES_5_ID,
            Resources.IMAGE_ZOMBATAR_CLOTHES_6_ID, Resources.IMAGE_ZOMBATAR_CLOTHES_7_ID,
            Resources.IMAGE_ZOMBATAR_CLOTHES_8_ID, Resources.IMAGE_ZOMBATAR_CLOTHES_9_ID,
            Resources.IMAGE_ZOMBATAR_CLOTHES_10_ID, Resources.IMAGE_ZOMBATAR_CLOTHES_11_ID,
            Resources.IMAGE_ZOMBATAR_CLOTHES_12_ID};
    private static final int[] HATS_IMAGES = {Resources.IMAGE_ZOMBATAR_HATS_1_ID,
            Resources.IMAGE_ZOMBATAR_HATS_2_ID, Resources.IMAGE_ZOMBATAR_HATS_3_ID,
            Resources.IMAGE_ZOMBATAR_HATS_4_ID, Resources.IMAGE_ZOMBATAR_HATS_5_ID,
            Resources.IMAGE_ZOMBATAR_HATS_6_ID, Resources.IMAGE_ZOMBATAR_HATS_7_ID,
            Resources.IMAGE_ZOMBATAR_HATS_8_ID, Resources.IMAGE_ZOMBATAR_HATS_9_ID,
            Resources.IMAGE_ZOMBATAR_HATS_10_ID, Resources.IMAGE_ZOMBATAR_HATS_11_ID,
            Resources.IMAGE_ZOMBATAR_HATS_12_ID, Resources.IMAGE_ZOMBATAR_HATS_13_ID,
            Resources.IMAGE_ZOMBATAR_HATS_14_ID};
    private static final int[] HAIR_IMAGES = {Resources.IMAGE_ZOMBATAR_HAIR_1_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_2_ID, Resources.IMAGE_ZOMBATAR_HAIR_3_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_4_ID, Resources.IMAGE_ZOMBATAR_HAIR_5_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_6_ID, Resources.IMAGE_ZOMBATAR_HAIR_7_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_8_ID, Resources.IMAGE_ZOMBATAR_HAIR_9_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_10_ID, Resources.IMAGE_ZOMBATAR_HAIR_11_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_12_ID, Resources.IMAGE_ZOMBATAR_HAIR_13_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_14_ID, Resources.IMAGE_ZOMBATAR_HAIR_15_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_16_ID};
    private static final int[] EYEWEAR_IMAGES = {Resources.IMAGE_ZOMBATAR_EYEWEAR_1_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_2_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_3_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_4_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_5_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_6_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_7_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_8_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_9_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_10_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_11_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_12_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_13_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_14_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_15_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_16_ID};
    private static final int[] FACIAL_IMAGES = {Resources.IMAGE_ZOMBATAR_FACIALHAIR_1_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_2_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_3_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_4_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_5_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_6_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_7_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_8_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_9_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_10_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_11_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_12_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_13_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_14_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_15_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_16_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_17_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_18_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_19_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_20_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_21_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_22_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_23_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_24_ID};
    private static final int[] TIDBITS_IMAGES = {Resources.IMAGE_ZOMBATAR_TIDBITS_1_ID,
            Resources.IMAGE_ZOMBATAR_TIDBITS_2_ID, Resources.IMAGE_ZOMBATAR_TIDBITS_3_ID,
            Resources.IMAGE_ZOMBATAR_TIDBITS_4_ID, Resources.IMAGE_ZOMBATAR_TIDBITS_5_ID,
            Resources.IMAGE_ZOMBATAR_TIDBITS_6_ID, Resources.IMAGE_ZOMBATAR_TIDBITS_7_ID,
            Resources.IMAGE_ZOMBATAR_TIDBITS_8_ID, Resources.IMAGE_ZOMBATAR_TIDBITS_9_ID,
            Resources.IMAGE_ZOMBATAR_TIDBITS_10_ID, Resources.IMAGE_ZOMBATAR_TIDBITS_11_ID,
            Resources.IMAGE_ZOMBATAR_TIDBITS_12_ID, Resources.IMAGE_ZOMBATAR_TIDBITS_13_ID,
            Resources.IMAGE_ZOMBATAR_TIDBITS_14_ID};
    private static final int[] ACCESSORY_IMAGES = {Resources.IMAGE_ZOMBATAR_ACCESSORY_1_ID,
            Resources.IMAGE_ZOMBATAR_ACCESSORY_2_ID, Resources.IMAGE_ZOMBATAR_ACCESSORY_3_ID,
            Resources.IMAGE_ZOMBATAR_ACCESSORY_4_ID, Resources.IMAGE_ZOMBATAR_ACCESSORY_5_ID,
            Resources.IMAGE_ZOMBATAR_ACCESSORY_6_ID, Resources.IMAGE_ZOMBATAR_ACCESSORY_8_ID,
            Resources.IMAGE_ZOMBATAR_ACCESSORY_9_ID, Resources.IMAGE_ZOMBATAR_ACCESSORY_10_ID,
            Resources.IMAGE_ZOMBATAR_ACCESSORY_11_ID, Resources.IMAGE_ZOMBATAR_ACCESSORY_12_ID,
            Resources.IMAGE_ZOMBATAR_ACCESSORY_13_ID, Resources.IMAGE_ZOMBATAR_ACCESSORY_14_ID,
            Resources.IMAGE_ZOMBATAR_ACCESSORY_15_ID, Resources.IMAGE_ZOMBATAR_ACCESSORY_16_ID};

    // ZombatarWidget.cpp:748-772 — part mask images; -1 marks "this part has no mask".
    private static final int[] HATS_MASK_IMAGES = {Resources.IMAGE_ZOMBATAR_HATS_1_MASK_ID, -1,
            Resources.IMAGE_ZOMBATAR_HATS_3_MASK_ID, -1, -1, Resources.IMAGE_ZOMBATAR_HATS_6_MASK_ID,
            Resources.IMAGE_ZOMBATAR_HATS_7_MASK_ID, Resources.IMAGE_ZOMBATAR_HATS_8_MASK_ID,
            Resources.IMAGE_ZOMBATAR_HATS_9_MASK_ID, -1, Resources.IMAGE_ZOMBATAR_HATS_11_MASK_ID, -1, -1, -1};
    private static final int[] HAIR_MASK_IMAGES = {Resources.IMAGE_ZOMBATAR_HAIR_1_MASK_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_2_MASK_ID, -1, -1, -1, -1, -1, -1, -1, -1,
            Resources.IMAGE_ZOMBATAR_HAIR_11_MASK_ID, Resources.IMAGE_ZOMBATAR_HAIR_12_MASK_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_13_MASK_ID, Resources.IMAGE_ZOMBATAR_HAIR_14_MASK_ID,
            Resources.IMAGE_ZOMBATAR_HAIR_15_MASK_ID, -1};
    private static final int[] EYEWEAR_MASK_IMAGES = {Resources.IMAGE_ZOMBATAR_EYEWEAR_1_MASK_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_2_MASK_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_3_MASK_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_4_MASK_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_5_MASK_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_6_MASK_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_7_MASK_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_8_MASK_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_9_MASK_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_10_MASK_ID, Resources.IMAGE_ZOMBATAR_EYEWEAR_11_MASK_ID,
            Resources.IMAGE_ZOMBATAR_EYEWEAR_12_MASK_ID, -1, -1, -1, -1};
    private static final int[] FACIAL_MASK_IMAGES = {Resources.IMAGE_ZOMBATAR_FACIALHAIR_1_MASK_ID, -1, -1,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_4_MASK_ID, -1, -1, -1,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_8_MASK_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_9_MASK_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_10_MASK_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_11_MASK_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_12_MASK_ID, -1, Resources.IMAGE_ZOMBATAR_FACIALHAIR_14_MASK_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_15_MASK_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_16_MASK_ID, -1,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_18_MASK_ID, -1, -1, Resources.IMAGE_ZOMBATAR_FACIALHAIR_21_MASK_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_22_MASK_ID, Resources.IMAGE_ZOMBATAR_FACIALHAIR_23_MASK_ID,
            Resources.IMAGE_ZOMBATAR_FACIALHAIR_24_MASK_ID};

    // ZombatarWidget.h:65-96 (public fields, upstream names)
    public GameSelector mGameSelector;
    public LawnApp mApp;
    public ZombatarWidgetState mState;
    public ZombatarPage mPage;
    public int mCurrentIndex;
    public int mSubPage;
    public int mMaxSubPages;
    public int mMouseX;
    public int mMouseY;
    public int mHoverGridCell;
    public int mHoverColorCell;
    public int mHoverTab;
    public boolean mDeleteHover;
    public int mTransitionTimer;
    public int[] mPart = new int[NUM_ZOMBATAR_PAGES];
    public int[] mColor = new int[NUM_ZOMBATAR_PAGES];

    public NewLawnButton mBackButton;
    public NewLawnButton mViewButton;
    public NewLawnButton mFinishedButton;
    public NewLawnButton mNewButton;
    public NewLawnButton mConfirmBackButton;
    public NewLawnButton mPrevPortraitButton;
    public NewLawnButton mNextPortraitButton;
    public NewLawnButton mPrevPageButton;
    public NewLawnButton mNextPageButton;

    public Zombie mPreviewZombie;

    private static final ZombatarPage[] PAGES = ZombatarPage.values();

    // ZombatarWidget::ZombatarWidget (ZombatarWidget.cpp:274-336)
    public ZombatarWidget(GameSelector theGameSelector) {
        mGameSelector = theGameSelector;
        mApp = theGameSelector.mApp;
        mState = ZombatarWidgetState.CREATE;
        mPage = ZombatarPage.SKIN;
        mCurrentIndex = 0;
        mSubPage = 0;
        mMaxSubPages = 0;
        mMouseX = -1;
        mMouseY = -1;
        mHoverGridCell = -1;
        mHoverColorCell = -1;
        mHoverTab = -1;
        mDeleteHover = false;
        mTransitionTimer = 0;
        mPreviewZombie = null;

        mBackButton = GameButton.makeNewButton(ZOMBATAR_BTN_BACK, this, "", null,
                Resources.getImageById(Resources.IMAGE_BLANK_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_MAINMENUBACK_HIGHLIGHT_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_MAINMENUBACK_HIGHLIGHT_ID));
        Image aBack = Resources.getImageById(Resources.IMAGE_ZOMBATAR_MAINMENUBACK_HIGHLIGHT_ID);
        int aBackW = aBack != null ? aBack.getWidth() : 98;
        int aBackH = aBack != null ? aBack.getHeight() : 26;
        mBackButton.resize(ZOMBATAR_BACK_X, ZOMBATAR_BACK_Y, aBackW, aBackH);

        mViewButton = GameButton.makeNewButton(ZOMBATAR_BTN_VIEW, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_VIEW_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_VIEW_BUTTON_HIGHLIGHT_ID), null);
        mViewButton.resize(ZOMBATAR_VIEW_X, ZOMBATAR_VIEW_Y, 115, 26);

        mFinishedButton = GameButton.makeNewButton(ZOMBATAR_BTN_FINISHED, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_FINISHED_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_FINISHED_BUTTON_HIGHLIGHT_ID), null);
        mFinishedButton.resize(ZOMBATAR_FINISHED_X, ZOMBATAR_FINISHED_Y, 103, 26);

        mNewButton = GameButton.makeNewButton(ZOMBATAR_BTN_NEW, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON_HIGHLIGHT_ID), null);
        mNewButton.resize(ZOMBATAR_NEW_X, ZOMBATAR_NEW_Y, 234, 39);

        mConfirmBackButton = GameButton.makeNewButton(ZOMBATAR_BTN_CONFIRM_BACK, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_BACK_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_BACK_BUTTON_HIGHLIGHT_ID), null);
        mConfirmBackButton.resize(ZOMBATAR_CONFIRM_BACK_X, ZOMBATAR_CONFIRM_BTN_Y, 98, 26);

        mPrevPortraitButton = GameButton.makeNewButton(ZOMBATAR_BTN_PREV_PORTRAIT, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_PREV_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_PREV_BUTTON_HIGHLIGHT_ID), null);
        mPrevPortraitButton.resize(ZOMBATAR_PREV_PORTRAIT_X, ZOMBATAR_PORTRAIT_BTN_Y, 33, 38);

        mNextPortraitButton = GameButton.makeNewButton(ZOMBATAR_BTN_NEXT_PORTRAIT, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_NEXT_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_NEXT_BUTTON_HIGHLIGHT_ID), null);
        mNextPortraitButton.resize(ZOMBATAR_NEXT_PORTRAIT_X, ZOMBATAR_PORTRAIT_BTN_Y, 33, 38);

        mPrevPageButton = GameButton.makeNewButton(ZOMBATAR_BTN_PREV_PAGE, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_PREV_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_PREV_BUTTON_HIGHLIGHT_ID), null);
        mPrevPageButton.resize(ZOMBATAR_PREV_PAGE_X, ZOMBATAR_PAGE_BTN_Y, 33, 38);

        mNextPageButton = GameButton.makeNewButton(ZOMBATAR_BTN_NEXT_PAGE, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_NEXT_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_NEXT_BUTTON_HIGHLIGHT_ID), null);
        mNextPageButton.resize(ZOMBATAR_NEXT_PAGE_X, ZOMBATAR_PAGE_BTN_Y, 33, 38);

        resize(800, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        resetDraft();
        updateButtonState();
    }

    // ZombatarWidget::~ZombatarWidget (ZombatarWidget.cpp:338-341)
    @Override
    public void dispose() {
        destroyPreviewZombie();
        super.dispose();
    }

    // ZombatarWidget::AddedToManager (ZombatarWidget.cpp:343-356)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mBackButton);
        addWidget(mViewButton);
        addWidget(mFinishedButton);
        addWidget(mNewButton);
        addWidget(mConfirmBackButton);
        addWidget(mPrevPortraitButton);
        addWidget(mNextPortraitButton);
        addWidget(mPrevPageButton);
        addWidget(mNextPageButton);
    }

    // ZombatarWidget::RemovedFromManager (ZombatarWidget.cpp:358-373)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        destroyPreviewZombie();
        removeWidget(mBackButton);
        removeWidget(mViewButton);
        removeWidget(mFinishedButton);
        removeWidget(mNewButton);
        removeWidget(mConfirmBackButton);
        removeWidget(mPrevPortraitButton);
        removeWidget(mNextPortraitButton);
        removeWidget(mPrevPageButton);
        removeWidget(mNextPageButton);
    }

    // ZombatarWidget::Update (ZombatarWidget.cpp:375-410)
    @Override
    public void update() {
        super.update();
        if (mX >= GameConstants.BOARD_WIDTH) {
            if (mPreviewZombie != null) {
                destroyPreviewZombie();
            }
        } else if (mPreviewZombie != null) {
            mPreviewZombie.update();
        } else if (mApp.mPlayerInfo != null) {
            createPreviewZombie();
        }

        if (mState == ZombatarWidgetState.TO_CONFIRM || mState == ZombatarWidgetState.FROM_CONFIRM) {
            mTransitionTimer--;
            boolean aToConfirm = mState == ZombatarWidgetState.TO_CONFIRM;
            int aFromX = aToConfirm ? ZOMBATAR_FINISHED_X : ZOMBATAR_ACCEPT_X;
            int aFromY = aToConfirm ? ZOMBATAR_FINISHED_Y : ZOMBATAR_CONFIRM_BTN_Y;
            int aToX = aToConfirm ? ZOMBATAR_ACCEPT_X : ZOMBATAR_FINISHED_X;
            int aToY = aToConfirm ? ZOMBATAR_CONFIRM_BTN_Y : ZOMBATAR_FINISHED_Y;
            Image aFinished = Resources.getImageById(Resources.IMAGE_ZOMBATAR_FINISHED_BUTTON_ID);
            int aBtnW = aFinished != null ? aFinished.getWidth() : 103;
            int aBtnH = aFinished != null ? aFinished.getHeight() : 26;
            mFinishedButton.resize(
                    PvzpCommon.pvzpAnimateCurve(ZOMBATAR_TRANSITION_TICKS, 0, mTransitionTimer,
                            aFromX, aToX, PvzpCurves.CURVE_LINEAR),
                    PvzpCommon.pvzpAnimateCurve(ZOMBATAR_TRANSITION_TICKS, 0, mTransitionTimer,
                            aFromY, aToY, PvzpCurves.CURVE_LINEAR),
                    aBtnW, aBtnH);
            if (mTransitionTimer <= 0) {
                mTransitionTimer = 0;
                changeState(aToConfirm ? ZombatarWidgetState.CONFIRM : ZombatarWidgetState.CREATE);
            }
        }

        markDirty();
    }

    // ZombatarWidget::Open (ZombatarWidget.cpp:412-443)
    public void open() {
        if (mApp.mPlayerInfo == null) {
            return;
        }

        mSubPage = 0;
        mMaxSubPages = 0;
        mPage = ZombatarPage.SKIN;
        mHoverGridCell = -1;
        mHoverColorCell = -1;
        mDeleteHover = false;
        mCurrentIndex = 0;
        clampCurrentIndex();

        if (getHeadCount() > 0) {
            changeState(ZombatarWidgetState.LIST);
            loadCurrentToDraft();
        } else {
            changeState(ZombatarWidgetState.CREATE);
            resetDraft();
        }

        mGameSelector.slideTo(-GameConstants.BOARD_WIDTH, 0);
        if (mWidgetManager != null) {
            mWidgetManager.bringToFront(this);
            mWidgetManager.setFocus(this);
        }
    }

    // ZombatarWidget::ResetDraft (ZombatarWidget.cpp:445-457)
    public void resetDraft() {
        for (int i = 0; i < NUM_ZOMBATAR_PAGES; i++) {
            mPart[i] = -1;
            mColor[i] = ZOMBATAR_COLOR_NONE;
        }

        mPart[ZombatarPage.SKIN.ordinal()] = 0;
        mColor[ZombatarPage.SKIN.ordinal()] = 0;
        mPart[ZombatarPage.BACKDROPS.ordinal()] = 4;
        mColor[ZombatarPage.BACKDROPS.ordinal()] = ZOMBATAR_PART_COLOR_NONE_2;
    }

    // ZombatarWidget::GetHeadCount (ZombatarWidget.cpp:461-465)
    public int getHeadCount() {
        if (mApp.mPlayerInfo == null) {
            return 0;
        }
        return mApp.mPlayerInfo.mZombatarData.length / PlayerInfo.ZOMBATAR_RECORD_SIZE;
    }

    // ZombatarWidget::CanSaveNewHead (ZombatarWidget.cpp:467-470)
    public boolean canSaveNewHead() {
        return getHeadCount() < PlayerInfo.MAX_ZOMBATAR_HEADS;
    }

    // ZombatarWidget::ShowMaxHeadsMessage (ZombatarWidget.cpp:472-476)
    public void showMaxHeadsMessage() {
        mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "Zombatar Limit Reached",
                "This profile already has the maximum number of saved Zombatars.",
                "[DIALOG_BUTTON_OK]", "", Dialog.BUTTONS_FOOTER);
    }

    // ZombatarWidget::ClampCurrentIndex (ZombatarWidget.cpp:478-489)
    public void clampCurrentIndex() {
        int aCount = getHeadCount();
        if (aCount <= 0) {
            mCurrentIndex = 0;
        } else {
            mCurrentIndex = Math.max(0, Math.min(mCurrentIndex, aCount - 1));
        }
    }

    // ZombatarWidget::DecodeRecord (ZombatarWidget.cpp:491-523)
    public void decodeRecord(byte[] theRecord, int[] thePart, int[] theColor) {
        for (int i = 0; i < NUM_ZOMBATAR_PAGES; i++) {
            thePart[i] = -1;
            theColor[i] = ZOMBATAR_COLOR_NONE;
        }

        thePart[ZombatarPage.SKIN.ordinal()] = 0;
        {
            int aSkinColor = Zombatar.readSignedRecordSlot(theRecord, Zombatar.ZOMBATAR_SLOT_SKIN_COLOR);
            theColor[ZombatarPage.SKIN.ordinal()] = aSkinColor < 0 ? 0 : Math.min(aSkinColor, 11);
        }

        for (int i = ZombatarPage.HAIR.ordinal(); i < NUM_ZOMBATAR_PAGES; i++) {
            ZombatarPage aPage = PAGES[i];
            int aItemCount = getTotalItemsForPage(aPage);
            if (aItemCount > 16) {
                aItemCount += aItemCount / 17;
            }
            int aPart = Zombatar.readSignedRecordSlot(theRecord, slotForPart(aPage));
            int aColor = Zombatar.readSignedRecordSlot(theRecord, slotForColor(aPage));

            if (aPage == ZombatarPage.BACKDROPS) {
                thePart[i] = Math.max(0, Math.min(aPart, aItemCount - 1));
            } else {
                thePart[i] = (aPart >= 0 && aPart < aItemCount) ? aPart : -1;
            }

            if (aColor < 0) {
                theColor[i] = ZOMBATAR_COLOR_NONE;
            } else {
                theColor[i] = Math.min(aColor, 47);
            }
        }
    }

    // ZombatarWidget::EncodeRecord (ZombatarWidget.cpp:525-537)
    public void encodeRecord(byte[] theRecord) {
        Arrays.fill(theRecord, (byte) 0);
        Zombatar.writeRecordSlot(theRecord, Zombatar.ZOMBATAR_SLOT_SKIN_PART, ZOMBATAR_COLOR_NONE);
        Zombatar.writeRecordSlot(theRecord, Zombatar.ZOMBATAR_SLOT_SKIN_COLOR,
                Math.max(0, Math.min(mColor[ZombatarPage.SKIN.ordinal()], 11)));

        for (int i = ZombatarPage.HAIR.ordinal(); i < NUM_ZOMBATAR_PAGES; i++) {
            ZombatarPage aPage = PAGES[i];
            Zombatar.writeRecordSlot(theRecord, slotForPart(aPage), mPart[i]);
            Zombatar.writeRecordSlot(theRecord, slotForColor(aPage), mColor[i]);
        }
    }

    // ZombatarWidget::LoadCurrentToDraft (ZombatarWidget.cpp:539-550)
    public void loadCurrentToDraft() {
        if (mApp.mPlayerInfo == null || getHeadCount() <= 0) {
            resetDraft();
            return;
        }

        clampCurrentIndex();
        byte[] aRecord = recordAt(mCurrentIndex);
        decodeRecord(aRecord, mPart, mColor);
    }

    // ZombatarWidget::SaveDraft (ZombatarWidget.cpp:552-570)
    public boolean saveDraft() {
        PlayerInfo aPlayerInfo = mApp.mPlayerInfo;
        if (aPlayerInfo == null || !canSaveNewHead()) {
            return false;
        }

        int anOffset = aPlayerInfo.mZombatarData.length;
        byte[] aNewData = Arrays.copyOf(aPlayerInfo.mZombatarData,
                anOffset + PlayerInfo.ZOMBATAR_RECORD_SIZE);
        byte[] aDraft = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
        encodeRecord(aDraft);
        System.arraycopy(aDraft, 0, aNewData, anOffset, PlayerInfo.ZOMBATAR_RECORD_SIZE);
        aPlayerInfo.mZombatarData = aNewData;
        aPlayerInfo.mZombatarHeadCount = getHeadCount();
        mCurrentIndex = anOffset / PlayerInfo.ZOMBATAR_RECORD_SIZE;

        aPlayerInfo.mZombatarCreatedBefore = 1;
        aPlayerInfo.saveDetails();
        resetDraft();
        mPage = ZombatarPage.SKIN;
        mSubPage = 0;
        return true;
    }

    // ZombatarWidget::DeleteCurrent (ZombatarWidget.cpp:572-602)
    public void deleteCurrent() {
        PlayerInfo aPlayerInfo = mApp.mPlayerInfo;
        int aCount = getHeadCount();
        if (aPlayerInfo == null || aCount <= 0) {
            return;
        }

        clampCurrentIndex();
        byte[] aData = aPlayerInfo.mZombatarData;
        int anOffset = mCurrentIndex * PlayerInfo.ZOMBATAR_RECORD_SIZE;
        int aTailOffset = anOffset + PlayerInfo.ZOMBATAR_RECORD_SIZE;
        int aTailBytes = aData.length - aTailOffset;
        if (aTailBytes > 0) {
            System.arraycopy(aData, aTailOffset, aData, anOffset, aTailBytes);
        }
        aPlayerInfo.mZombatarData = Arrays.copyOf(aData, aData.length - PlayerInfo.ZOMBATAR_RECORD_SIZE);
        aPlayerInfo.mZombatarHeadCount = getHeadCount();
        clampCurrentIndex();

        if (getHeadCount() > 0) {
            loadCurrentToDraft();
            changeState(ZombatarWidgetState.LIST);
        } else {
            resetDraft();
            changeState(ZombatarWidgetState.CREATE);
        }

        aPlayerInfo.saveDetails();
    }

    // ZombatarWidget.cpp:604-611
    public Rect getCategoryRect(int theIndex) {
        Image aImage = getCategoryImage(PAGES[theIndex], false, false);
        int aTabH = aImage != null ? aImage.getHeight() : 39;
        int aTabW = aImage != null ? aImage.getWidth() : 104;
        int aStep = Math.max(8, aTabH - 3);
        return new Rect(ZOMBATAR_TABS_X, ZOMBATAR_TABS_Y0 + theIndex * aStep, aTabW, aTabH);
    }

    // ZombatarWidget.cpp:613-618
    public Rect getItemHitRect(int theIndex) {
        Rect aRect = getItemRect(theIndex);
        return new Rect(aRect.mX + ZOMBATAR_CELL_INSET, aRect.mY + ZOMBATAR_CELL_INSET,
                aRect.mWidth - 2 * ZOMBATAR_CELL_INSET, aRect.mHeight - 2 * ZOMBATAR_CELL_INSET);
    }

    // ZombatarWidget.cpp:620-631
    public Rect getItemRect(int theIndex) {
        Image aCell = Resources.getImageById(Resources.IMAGE_ZOMBATAR_ACCESSORY_BG_ID);
        int aCellW = aCell != null ? aCell.getWidth() : 67;
        int aCellH = aCell != null ? aCell.getHeight() : 67;
        int aStepX = aCellW + ZOMBATAR_GRID_GAP;
        int aStepY = aCellH + 3 + ZOMBATAR_GRID_GAP;
        int aOriginX = ZOMBATAR_PANEL_X + (ZOMBATAR_PANEL_WIDTH - ZOMBATAR_GRID_COLS * aStepX) / 2
                + ZOMBATAR_GRID_BIAS_X;
        int aOriginY = ZOMBATAR_PANEL_Y + 2 * ZOMBATAR_GRID_GAP + 120;
        int aCol = theIndex % ZOMBATAR_GRID_COLS;
        int aRow = theIndex / ZOMBATAR_GRID_COLS;
        return new Rect(aOriginX + aCol * aStepX, aOriginY + aRow * aStepY, aCellW, aCellH);
    }

    // ZombatarWidget.cpp:633-642
    public Rect getColorRect(int theIndex) {
        Image aSwatch = Resources.getImageById(Resources.IMAGE_ZOMBATAR_COLORPICKER_ID);
        int aSwatchW = aSwatch != null ? aSwatch.getWidth() : 21;
        int aSwatchH = aSwatch != null ? aSwatch.getHeight() : 20;
        int aStepX = aSwatchW + ZOMBATAR_COLOR_GAP;
        int aStepY = aSwatchH + ZOMBATAR_COLOR_GAP;
        int aCol = theIndex % ZOMBATAR_COLOR_COLS;
        int aRow = theIndex / ZOMBATAR_COLOR_COLS;
        return new Rect(ZOMBATAR_COLOR_X + aCol * aStepX, ZOMBATAR_COLOR_Y + aRow * aStepY,
                aSwatchW, aSwatchH);
    }

    // ZombatarWidget.cpp:644-648
    public int getTotalItemsForPage(ZombatarPage thePage) {
        return ZOMBATAR_ITEMS_PER_PAGE[Math.max(0, Math.min(thePage.ordinal(), NUM_ZOMBATAR_PAGES - 1))];
    }

    // ZombatarWidget.cpp:650-654
    public int getSubPageItemCount() {
        int aTotal = getTotalItemsForPage(mPage);
        int aRemaining = aTotal - mSubPage * ZOMBATAR_GRID_PAGE;
        return Math.max(0, Math.min(aRemaining, ZOMBATAR_GRID_PAGE));
    }

    // ZombatarWidget.cpp:656-659
    public boolean pageAllowsNone() {
        return mPage != ZombatarPage.SKIN && mPage != ZombatarPage.BACKDROPS;
    }

    // ZombatarWidget.cpp:661-669
    public boolean pageAllowsColors() {
        if (mPage == ZombatarPage.SKIN) {
            return true;
        }
        if (mPart[mPage.ordinal()] < 0) {
            return false;
        }
        int aMode = getPartColorMode(mPage, mPart[mPage.ordinal()]);
        return aMode == ZOMBATAR_COLOR_MODE_1 || aMode == ZOMBATAR_COLOR_MODE_2;
    }

    // ZombatarWidget.cpp:671-697
    public Image getCategoryImage(ZombatarPage thePage, boolean theSelected, boolean theOver) {
        int[] aCategory = CATEGORY_IMAGES[Math.max(0, Math.min(thePage.ordinal(), NUM_ZOMBATAR_PAGES - 1))];
        if (theSelected) {
            return Resources.getImageById(aCategory[1]);
        }
        Image aOver = Resources.getImageById(aCategory[2]);
        if (theOver && aOver != null) {
            return aOver;
        }
        return Resources.getImageById(aCategory[0]);
    }

    // ZombatarWidget.cpp:699-709
    public Image getBackgroundImage(int theIndex) {
        return Resources.getImageById(BACKGROUND_IMAGES[Math.max(0, Math.min(theIndex, 4))]);
    }

    // ZombatarWidget.cpp:711-744
    public Image getPartImage(ZombatarPage thePage, int theIndex) {
        if (theIndex < 0) {
            return null;
        }
        switch (thePage) {
            case CLOTHES:
                return theIndex < 12 ? Resources.getImageById(CLOTHES_IMAGES[theIndex]) : null;
            case HATS:
                return theIndex < 14 ? Resources.getImageById(HATS_IMAGES[theIndex]) : null;
            case HAIR:
                return theIndex < 16 ? Resources.getImageById(HAIR_IMAGES[theIndex]) : null;
            case EYEWEAR:
                return theIndex < 16 ? Resources.getImageById(EYEWEAR_IMAGES[theIndex]) : null;
            case FACIAL_HAIR: {
                int aIdx = theIndex;
                if (aIdx > 16) {
                    aIdx -= aIdx / 17;
                }
                return aIdx < 24 ? Resources.getImageById(FACIAL_IMAGES[aIdx]) : null;
            }
            case TIDBITS:
                return theIndex < 14 ? Resources.getImageById(TIDBITS_IMAGES[theIndex]) : null;
            case ACCESSORY:
                return theIndex < 15 ? Resources.getImageById(ACCESSORY_IMAGES[theIndex]) : null;
            default:
                return null;
        }
    }

    // ZombatarWidget.cpp:746-790
    public Image getPartMaskImage(ZombatarPage thePage, int theIndex) {
        if (theIndex < 0) {
            return null;
        }
        switch (thePage) {
            case HATS:
                return theIndex < 14 && HATS_MASK_IMAGES[theIndex] >= 0
                        ? Resources.getImageById(HATS_MASK_IMAGES[theIndex]) : null;
            case HAIR:
                return theIndex < 16 && HAIR_MASK_IMAGES[theIndex] >= 0
                        ? Resources.getImageById(HAIR_MASK_IMAGES[theIndex]) : null;
            case EYEWEAR:
                return theIndex < 16 && EYEWEAR_MASK_IMAGES[theIndex] >= 0
                        ? Resources.getImageById(EYEWEAR_MASK_IMAGES[theIndex]) : null;
            case FACIAL_HAIR: {
                int aIdx = theIndex;
                if (aIdx > 16) {
                    aIdx -= aIdx / 17;
                }
                return aIdx < 24 && FACIAL_MASK_IMAGES[aIdx] >= 0
                        ? Resources.getImageById(FACIAL_MASK_IMAGES[aIdx]) : null;
            }
            default:
                return null;
        }
    }

    // ZombatarWidget.cpp:792-804
    public void drawImageColorized(Graphics g, Image theImage, int theX, int theY, int theColorIndex) {
        if (theImage == null) {
            return;
        }

        g.setColorizeImages(true);
        g.setColor(Zombatar.getColor(theColorIndex));
        g.drawImage(theImage, theX, theY);
        g.setColorizeImages(false);
        g.setColor(Color.White);
    }

    // ZombatarWidget.cpp:806-835
    public void drawPartImage(Graphics g, ZombatarPage thePage, int theIndex, int theX, int theY,
            int theColorIndex) {
        Image aImage = getPartImage(thePage, theIndex);
        Image aMask = getPartMaskImage(thePage, theIndex);
        if (aImage == null) {
            return;
        }

        ZombatarPartLayout aLayout = Zombatar.getPartLayout(thePage, theIndex);
        int aPosX = theX + (aLayout != null ? aLayout.mOffsetX : 0);
        int aPosY = theY + (aLayout != null ? aLayout.mOffsetY : 0);

        if (getPartColorMode(thePage, theIndex) == ZOMBATAR_COLOR_MODE_NONE) {
            g.drawImage(aImage, aPosX, aPosY);
            return;
        }

        if (aMask != null) {
            int aColorOffX = aLayout != null ? aLayout.mColorOffsetX : 0;
            int aColorOffY = aLayout != null ? aLayout.mColorOffsetY : 0;
            drawImageColorized(g, aMask, aPosX + aColorOffX, aPosY + aColorOffY, theColorIndex);
            g.drawImage(aImage, aPosX, aPosY);
        } else {
            drawImageColorized(g, aImage, aPosX, aPosY, theColorIndex);
        }
    }

    // ZombatarWidget.cpp:837-878
    public void drawAvatar(Graphics g, int theX, int theY, byte[] theRecord) {
        int[] aPart = new int[NUM_ZOMBATAR_PAGES];
        int[] aColor = new int[NUM_ZOMBATAR_PAGES];
        decodeRecord(theRecord, aPart, aColor);

        Image aBackground = getBackgroundImage(aPart[ZombatarPage.BACKDROPS.ordinal()]);
        if (getPartColorMode(ZombatarPage.BACKDROPS, aPart[ZombatarPage.BACKDROPS.ordinal()])
                == ZOMBATAR_COLOR_MODE_2) {
            drawImageColorized(g, aBackground, theX, theY, aColor[ZombatarPage.BACKDROPS.ordinal()]);
        } else {
            g.drawImage(aBackground, theX, theY);
        }

        int aBlankX = theX;
        int aBlankY = theY;
        Image aBackgroundBlank = Resources.getImageById(Resources.IMAGE_ZOMBATAR_BACKGROUND_BLANK_ID);
        Image aZombieBlank = Resources.getImageById(Resources.IMAGE_ZOMBATAR_ZOMBIE_BLANK_ID);
        if (aBackgroundBlank != null && aZombieBlank != null) {
            aBlankX = theX + (aBackgroundBlank.getWidth() - aZombieBlank.getWidth());
            aBlankY = theY + (aBackgroundBlank.getHeight() - aZombieBlank.getHeight());
        }
        drawImageColorized(g, Resources.getImageById(Resources.IMAGE_ZOMBATAR_ZOMBIE_BLANK_SKIN_ID),
                aBlankX, aBlankY, aColor[ZombatarPage.SKIN.ordinal()]);
        g.drawImage(aZombieBlank, aBlankX, aBlankY);

        List<ZombatarDrawPart> aParts = new ArrayList<>();
        for (int i = ZombatarPage.HAIR.ordinal(); i < ZombatarPage.BACKDROPS.ordinal(); i++) {
            ZombatarPage aPage = PAGES[i];
            int aIndex = aPart[i];
            if (aIndex >= 0) {
                ZombatarPartLayout aLayout = Zombatar.getPartLayout(aPage, aIndex);
                ZombatarDrawPart aDrawPart = new ZombatarDrawPart();
                aDrawPart.mPage = aPage;
                aDrawPart.mIndex = aIndex;
                aDrawPart.mColor = aColor[i];
                aDrawPart.mDrawOrder = aLayout != null ? aLayout.mDrawOrder : 0;
                aParts.add(aDrawPart);
            }
        }
        aParts.sort((a, b) -> Integer.compare(a.mDrawOrder, b.mDrawOrder));
        for (ZombatarDrawPart aDrawPart : aParts) {
            drawPartImage(g, aDrawPart.mPage, aDrawPart.mIndex, theX, theY, aDrawPart.mColor);
        }
    }

    // ZombatarWidget.cpp:880-884
    public void drawDraftAvatar(Graphics g, int theX, int theY) {
        byte[] aRecord = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
        encodeRecord(aRecord);
        drawAvatar(g, theX, theY, aRecord);
    }

    // ZombatarWidget.cpp:886-905
    public void drawColorSwatches(Graphics g, int thePaletteBase, int theCount, int theSavedColor) {
        g.setColorizeImages(true);
        for (int i = 0; i < theCount; i++) {
            int aPalette = thePaletteBase + i;
            int aAlpha = 0x40;
            if (aPalette == theSavedColor) {
                aAlpha = 0xff;
            } else if (i == mHoverColorCell) {
                aAlpha = 0x80;
            }
            Color aColor = Zombatar.getColor(aPalette);
            g.setColor(new Color(aColor.mRed, aColor.mGreen, aColor.mBlue, aAlpha));
            Image aImage = (aPalette == ZOMBATAR_PART_COLOR_NONE_1 || aPalette == ZOMBATAR_PART_COLOR_NONE_2)
                    ? Resources.getImageById(Resources.IMAGE_ZOMBATAR_COLORPICKER_NONE_ID)
                    : Resources.getImageById(Resources.IMAGE_ZOMBATAR_COLORPICKER_ID);
            Rect aRect = getColorRect(i);
            g.drawImage(aImage, aRect.mX, aRect.mY);
        }
        g.setColorizeImages(false);
        g.setColor(Color.White);
    }

    // ZombatarWidget.cpp:907-921
    public void createPreviewZombie() {
        if (mPreviewZombie != null) {
            return;
        }

        mPreviewZombie = new Zombie();
        mPreviewZombie.mApp = mApp;
        mPreviewZombie.mBoard = null;
        mPreviewZombie.zombieInitialize(0, ZombieType.ZOMBIE_FLAG, false, null, Zombie.ZOMBIE_WAVE_UI);
        mPreviewZombie.mPosX = 0.0f;
        mPreviewZombie.mPosY = 0.0f;
        mPreviewZombie.mX = 0;
        mPreviewZombie.mY = 0;
    }

    // ZombatarWidget.cpp:923-930
    public void destroyPreviewZombie() {
        if (mPreviewZombie == null) {
            return;
        }

        mPreviewZombie.dieNoLoot();
        mPreviewZombie = null;
    }

    // ZombatarWidget.cpp:932-972
    public void drawAvatarBox(Graphics g) {
        if (mPreviewZombie == null) {
            return;
        }

        byte[] aRecord;
        if (mState == ZombatarWidgetState.LIST) {
            if (getHeadCount() <= 0) {
                return;
            }
            clampCurrentIndex();
            aRecord = recordAt(mCurrentIndex);
        } else {
            aRecord = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
            encodeRecord(aRecord);
        }

        mPreviewZombie.applyZombatarHead(aRecord);

        Image aGround = Resources.getImageById(Resources.IMAGE_ALMANAC_GROUNDDAY_ID);
        if (aGround != null) {
            g.setClipRect(ZOMBATAR_AVATAR_GROUND_X,
                    ZOMBATAR_AVATAR_GROUND_Y + ZOMBATAR_AVATAR_GROUND_CLIP_TOP,
                    aGround.getWidth() - ZOMBATAR_AVATAR_GROUND_CLIP_RIGHT,
                    aGround.getHeight() - ZOMBATAR_AVATAR_GROUND_CLIP_TOP
                            - ZOMBATAR_AVATAR_GROUND_CLIP_BOTTOM);
            g.drawImage(aGround, ZOMBATAR_AVATAR_GROUND_X, ZOMBATAR_AVATAR_GROUND_Y);
            g.clearClipRect();
        }

        mPreviewZombie.mPosX = ZOMBATAR_AVATAR_ZOMBIE_X;
        mPreviewZombie.mPosY = ZOMBATAR_AVATAR_ZOMBIE_Y;
        mPreviewZombie.mX = ZOMBATAR_AVATAR_ZOMBIE_X;
        mPreviewZombie.mY = ZOMBATAR_AVATAR_ZOMBIE_Y;

        Graphics aZombieGraphics = g.create();
        mPreviewZombie.beginDraw(aZombieGraphics);
        mPreviewZombie.draw(aZombieGraphics);
    }

    // ZombatarWidget::Draw (ZombatarWidget.cpp:974-991)
    @Override
    public void draw(Graphics g) {
        if (mApp.mPlayerInfo == null || mX >= GameConstants.BOARD_WIDTH) {
            return;
        }

        drawMainBackground(g);
        drawAvatarBox(g);
        if (mState != ZombatarWidgetState.LIST) {
            drawDraftAvatar(g, ZOMBATAR_PREVIEW_X, ZOMBATAR_PREVIEW_Y);
        }
        if (mState == ZombatarWidgetState.LIST) {
            drawList(g);
        } else if (mState == ZombatarWidgetState.CONFIRM) {
            drawConfirm(g);
        } else if (mState == ZombatarWidgetState.CREATE) {
            drawCreate(g);
        } else {
            drawTransition(g);
        }

        Image aWindow = Resources.getImageById(Resources.IMAGE_ZOMBATAR_DISPLAY_WINDOW_ID);
        if (aWindow != null) {
            g.drawImage(aWindow, 5, 0);
        }
    }

    // ZombatarWidget::DrawMainBackground (ZombatarWidget.cpp:993-996)
    public void drawMainBackground(Graphics g) {
        g.drawImage(Resources.getImageById(Resources.IMAGE_ZOMBATAR_MAIN_BG_ID), 0, 0);
    }

    // ZombatarWidget::DrawList (ZombatarWidget.cpp:998-1037)
    public void drawList(Graphics g) {
        g.drawImage(Resources.getImageById(Resources.IMAGE_ZOMBATAR_WIDGET_BG_ID),
                ZOMBATAR_PANEL_X, ZOMBATAR_PANEL_Y);

        int aCount = getHeadCount();
        clampCurrentIndex();
        if (aCount <= 0) {
            return;
        }

        Image aWidgetBg = Resources.getImageById(Resources.IMAGE_ZOMBATAR_WIDGET_BG_ID);
        Image aBackgroundBlank = Resources.getImageById(Resources.IMAGE_ZOMBATAR_BACKGROUND_BLANK_ID);
        int aPanelW = aWidgetBg != null ? aWidgetBg.getWidth() : ZOMBATAR_PANEL_WIDTH;
        int aBlankW = aBackgroundBlank != null ? aBackgroundBlank.getWidth() : ZOMBATAR_LIST_BLANK_WIDTH;
        // separate truncating divisions, do not merge
        int aPortraitX = ZOMBATAR_PANEL_X + (aPanelW - ZOMBATAR_LIST_CENTER_INSET) / 2
                + ZOMBATAR_LIST_SPACING / 2 - aBlankW / 2;

        g.clipRect(new Rect(ZOMBATAR_LIST_CLIP_X, ZOMBATAR_LIST_CLIP_Y,
                aPanelW - ZOMBATAR_LIST_CLIP_RIGHT, ZOMBATAR_LIST_CLIP_HEIGHT));
        g.setColor(Color.Black);
        g.fillRect(ZOMBATAR_LIST_CLIP_X, ZOMBATAR_LIST_CLIP_Y, aPanelW - ZOMBATAR_LIST_CLIP_RIGHT,
                ZOMBATAR_LIST_FILL_HEIGHT);
        for (int i = -1; i <= 1; i++) {
            int aIndex = mCurrentIndex + i;
            if (aIndex >= 0 && aIndex < aCount) {
                drawAvatar(g, aPortraitX + i * (ZOMBATAR_LIST_SPACING + aBlankW),
                        ZOMBATAR_LIST_PORTRAIT_Y, recordAt(aIndex));
            }
        }
        g.clearClipRect();
        g.setColor(Color.White);

        drawAvatar(g, ZOMBATAR_PREVIEW_X, ZOMBATAR_PREVIEW_Y, recordAt(mCurrentIndex));

        g.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        g.setColor(new Color(255, 255, 255));
        g.drawString(mCurrentIndex + 1 + " / " + aCount, ZOMBATAR_LIST_COUNTER_X, ZOMBATAR_LIST_COUNTER_Y);

        g.setFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT12_ID));
        g.setColor(mDeleteHover ? new Color(22, 253, 5) : new Color(255, 255, 255));
        g.drawString("Delete?", ZOMBATAR_LIST_DELETE_X, ZOMBATAR_LIST_DELETE_Y);
        g.setColor(Color.White);
    }

    // ZombatarWidget::DrawCreate (ZombatarWidget.cpp:1039-1176)
    public void drawCreate(Graphics g) {
        g.drawImage(Resources.getImageById(Resources.IMAGE_ZOMBATAR_WIDGET_BG_ID),
                ZOMBATAR_PANEL_X, ZOMBATAR_PANEL_Y);
        g.drawImage(Resources.getImageById(Resources.IMAGE_ZOMBATAR_WIDGET_INNER_BG_ID),
                ZOMBATAR_INNER_X, ZOMBATAR_INNER_Y);

        for (int i = 0; i < NUM_ZOMBATAR_PAGES; i++) {
            Rect aRect = getCategoryRect(i);
            g.drawImage(getCategoryImage(PAGES[i], i == mPage.ordinal(), i == mHoverTab), aRect.mX, aRect.mY);
        }

        if (mPage == ZombatarPage.SKIN) {
            Image aWidgetBg = Resources.getImageById(Resources.IMAGE_ZOMBATAR_WIDGET_BG_ID);
            int aPanelW = aWidgetBg != null ? aWidgetBg.getWidth() : ZOMBATAR_PANEL_WIDTH;
            PvzpStringFile.pvzpDrawStringWrapped(g, "[ZOMBATAR_START_TEXT]",
                    new Rect(ZOMBATAR_PANEL_X + aPanelW / 2 - 200, ZOMBATAR_START_TEXT_Y,
                            ZOMBATAR_START_TEXT_WIDTH, ZOMBATAR_START_TEXT_HEIGHT),
                    Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID),
                    new Color(254, 227, 0, 175), DrawStringJustification.DS_ALIGN_CENTER);
            g.setColor(Color.White);
        }

        int aItemCount = getSubPageItemCount();
        int aBaseIndex = mSubPage * ZOMBATAR_GRID_PAGE;
        for (int i = 0; i < aItemCount; i++) {
            Rect aRect = getItemRect(i);
            int aPartIndex = aBaseIndex + i;
            if (aPartIndex > 16) {
                aPartIndex += aPartIndex / 17;
            }
            boolean aSelected = mPart[mPage.ordinal()] == aPartIndex;
            boolean aHover = i == mHoverGridCell;
            boolean aDim = !aSelected && !aHover;

            if (aDim) {
                g.setColorizeImages(true);
                g.setColor(ZOMBATAR_CELL_DIM_COLOR);
            }
            g.drawImage(Resources.getImageById(aSelected ? Resources.IMAGE_ZOMBATAR_ACCESSORY_BG_HIGHLIGHT_ID
                            : Resources.IMAGE_ZOMBATAR_ACCESSORY_BG_ID), aRect.mX, aRect.mY);

            Image aZombieBlankSkin = Resources.getImageById(Resources.IMAGE_ZOMBATAR_ZOMBIE_BLANK_SKIN_ID);
            Image aZombieBlank = Resources.getImageById(Resources.IMAGE_ZOMBATAR_ZOMBIE_BLANK_ID);
            if (mPage == ZombatarPage.CLOTHES && aZombieBlankSkin != null && aZombieBlank != null) {
                // GraphicsStateGuard (ZombatarWidget.cpp:1064-1081): a child graphics carries the clip
                // so the dialog draw state is not disturbed.
                Graphics aClipG = g.create();
                aClipG.clipRect(new Rect(aRect.mX + ZOMBATAR_CELL_INSET, aRect.mY + ZOMBATAR_CELL_INSET,
                        aRect.mWidth - ZOMBATAR_CELL_INSET, aRect.mHeight - ZOMBATAR_CELL_INSET));
                Color aSkinColor = Zombatar.getColor(mColor[ZombatarPage.SKIN.ordinal()]);
                aSkinColor = new Color(aSkinColor.mRed, aSkinColor.mGreen, aSkinColor.mBlue,
                        aDim ? 0x80 : 0xff);
                aClipG.setColorizeImages(true);
                aClipG.setColor(aSkinColor);
                aClipG.drawImage(aZombieBlankSkin,
                        new Rect(aRect.mX - ZOMBATAR_CELL_ZOMBIE_MARGIN, aRect.mY - ZOMBATAR_CELL_ZOMBIE_MARGIN,
                                aRect.mWidth, aRect.mHeight),
                        new Rect(0, 0, aZombieBlankSkin.getWidth(), aZombieBlankSkin.getHeight()));
                aClipG.setColorizeImages(false);
                aClipG.drawImage(aZombieBlank,
                        new Rect(aRect.mX - ZOMBATAR_CELL_ZOMBIE_MARGIN, aRect.mY - ZOMBATAR_CELL_ZOMBIE_MARGIN,
                                aRect.mWidth, aRect.mHeight),
                        new Rect(0, 0, aZombieBlank.getWidth(), aZombieBlank.getHeight()));
                if (aDim) {
                    g.setColorizeImages(true);
                    g.setColor(ZOMBATAR_CELL_DIM_COLOR);
                }
            }

            Image aImage = (mPage == ZombatarPage.BACKDROPS) ? getBackgroundImage(aPartIndex)
                    : getPartImage(mPage, aPartIndex);
            if (aImage != null) {
                Rect aIconRect = fitIconRect(aImage, aRect, zombatarGridAlign(mPage, aPartIndex));
                Image aMask = getPartMaskImage(mPage, aPartIndex);
                if (aMask != null) {
                    ZombatarPartLayout aLayout = Zombatar.getPartLayout(mPage, aPartIndex);
                    float aScaleX = (float) aIconRect.mWidth / (float) aImage.getWidth();
                    float aScaleY = (float) aIconRect.mHeight / (float) aImage.getHeight();
                    int aMaskX = aIconRect.mX + (aLayout != null ? (int) (aLayout.mColorOffsetX * aScaleX) : 0);
                    int aMaskY = aIconRect.mY + (aLayout != null ? (int) (aLayout.mColorOffsetY * aScaleY) : 0);
                    g.drawImage(aMask,
                            new Rect(aMaskX, aMaskY, (int) (aMask.getWidth() * aScaleX),
                                    (int) (aMask.getHeight() * aScaleY)),
                            new Rect(0, 0, aMask.getWidth(), aMask.getHeight()));
                }
                g.drawImage(aImage, aIconRect, new Rect(0, 0, aImage.getWidth(), aImage.getHeight()));
            }

            if (aDim) {
                g.setColorizeImages(false);
                g.setColor(Color.White);
            }
        }

        if (pageAllowsNone()) {
            Rect aRect = getItemRect(aItemCount);
            boolean aSelected = mPart[mPage.ordinal()] < 0;
            boolean aHover = mHoverGridCell == aItemCount;
            boolean aDim = !aSelected && !aHover;
            if (aDim) {
                g.setColorizeImages(true);
                g.setColor(ZOMBATAR_CELL_DIM_COLOR);
            }
            g.drawImage(Resources.getImageById(aSelected ? Resources.IMAGE_ZOMBATAR_ACCESSORY_BG_HIGHLIGHT_ID
                    : Resources.IMAGE_ZOMBATAR_ACCESSORY_BG_ID), aRect.mX, aRect.mY);
            g.drawImage(Resources.getImageById(Resources.IMAGE_ZOMBATAR_ACCESSORY_BG_NONE_ID), aRect.mX, aRect.mY);
            if (aDim) {
                g.setColorizeImages(false);
                g.setColor(Color.White);
            }
        }

        g.drawImage(Resources.getImageById(Resources.IMAGE_ZOMBATAR_COLORS_BG_ID),
                ZOMBATAR_COLORS_X, ZOMBATAR_COLORS_Y);
        if (mPage == ZombatarPage.SKIN) {
            drawColorSwatches(g, 0, ZOMBATAR_SKIN_COLOR_COUNT, mColor[ZombatarPage.SKIN.ordinal()]);
        } else {
            int aMode = mPart[mPage.ordinal()] < 0 ? ZOMBATAR_COLOR_MODE_NONE
                    : getPartColorMode(mPage, mPart[mPage.ordinal()]);
            if (aMode == ZOMBATAR_COLOR_MODE_NONE) {
                String aKey = mPart[mPage.ordinal()] < 0 ? "[ZOMBATAR_COLOR_ITEM_NOT_CHOSEN]"
                        : "[ZOMBATAR_COLOR_NOT_APPLICABLE]";
                Image aColorsBg = Resources.getImageById(Resources.IMAGE_ZOMBATAR_COLORS_BG_ID);
                int aColorsW = aColorsBg != null ? aColorsBg.getWidth() : 261;
                int aColorsH = aColorsBg != null ? aColorsBg.getHeight() : 96;
                PvzpStringFile.pvzpDrawStringWrapped(g, aKey,
                        new Rect(ZOMBATAR_COLOR_HINT_X, ZOMBATAR_COLOR_HINT_Y, aColorsW - 40, aColorsH),
                        Resources.getFontById(Resources.FONT_BRIANNETOD12_ID), Color.White,
                        DrawStringJustification.DS_ALIGN_LEFT);
            } else {
                drawColorSwatches(g, zombatarColorBaseForMode(aMode), ZOMBATAR_PART_COLOR_COUNT,
                        mColor[mPage.ordinal()]);
            }
        }

        if (mMaxSubPages > 0) {
            g.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
            g.setColor(new Color(255, 255, 255));
            String aPage = mApp.getFormattedString("ZOMBATAR_PAGE", "Page %d / %d",
                    mSubPage + 1, mMaxSubPages + 1);
            g.drawString(aPage, 321, 441);
        }
    }

    // ZombatarWidget::DrawTransition (ZombatarWidget.cpp:1178-1200)
    public void drawTransition(Graphics g) {
        drawCreate(g);

        if (mMaxSubPages > 0) { // the page buttons are widgets but belong to the sheet, so draw them here
            g.setColorizeImages(true);
            g.setColor(ZOMBATAR_PAGE_BTN_DISABLED_TINT);
            Image aPrev = Resources.getImageById(Resources.IMAGE_ZOMBATAR_PREV_BUTTON_ID);
            if (aPrev != null) {
                g.drawImage(aPrev, ZOMBATAR_PREV_PAGE_X, ZOMBATAR_PAGE_BTN_Y);
            }
            Image aNext = Resources.getImageById(Resources.IMAGE_ZOMBATAR_NEXT_BUTTON_ID);
            if (aNext != null) {
                g.drawImage(aNext, ZOMBATAR_NEXT_PAGE_X, ZOMBATAR_PAGE_BTN_Y);
            }
            g.setColorizeImages(false);
        }

        Image aWidgetBg = Resources.getImageById(Resources.IMAGE_ZOMBATAR_WIDGET_BG_ID);
        int aPanelW = aWidgetBg != null ? aWidgetBg.getWidth() : ZOMBATAR_PANEL_WIDTH;
        int aAlpha = PvzpCommon.pvzpAnimateCurve(ZOMBATAR_TRANSITION_TICKS, 0, mTransitionTimer,
                mState == ZombatarWidgetState.TO_CONFIRM ? 0 : 255,
                mState == ZombatarWidgetState.TO_CONFIRM ? 255 : 0, PvzpCurves.CURVE_LINEAR);
        g.setColor(new Color(0, 0, 0, aAlpha));
        g.fillRect(ZOMBATAR_VEIL_X, ZOMBATAR_VEIL_Y, aPanelW - ZOMBATAR_VEIL_RIGHT_INSET, ZOMBATAR_VEIL_HEIGHT);
        g.setColor(Color.White);
    }

    // ZombatarWidget::DrawConfirm (ZombatarWidget.cpp:1202-1221)
    public void drawConfirm(Graphics g) {
        g.drawImage(Resources.getImageById(Resources.IMAGE_ZOMBATAR_WIDGET_BG_ID),
                ZOMBATAR_PANEL_X, ZOMBATAR_PANEL_Y);

        g.setFont(Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID));
        g.setColor(new Color(254, 227, 0));
        String aHeader = PvzpStringFile.translate("[ZOMBATAR_FINISHED_WARNING_HEADER]");
        g.drawString(aHeader,
                ZOMBATAR_CONFIRM_HEADER_X - Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID)
                        .stringWidth(aHeader) / 2,
                ZOMBATAR_CONFIRM_HEADER_Y);

        PvzpStringFile.pvzpDrawStringWrapped(g, "[ZOMBATAR_FINISHED_WARNING_TEXT]",
                new Rect(ZOMBATAR_CONFIRM_TEXT_X, ZOMBATAR_CONFIRM_TEXT_Y, ZOMBATAR_CONFIRM_TEXT_WIDTH,
                        ZOMBATAR_CONFIRM_TEXT_HEIGHT),
                Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID), Color.White,
                DrawStringJustification.DS_ALIGN_CENTER);

        g.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID));
        g.setColor(new Color(255, 255, 255));
        String aAccept = PvzpStringFile.translate("[ZOMBATAR_FINISHED_BUTTON_TEXT]");
        g.drawString(aAccept,
                ZOMBATAR_CONFIRM_ACCEPT_LABEL_X - Resources.getFontById(Resources.FONT_BRIANNETOD12_ID)
                        .stringWidth(aAccept) / 2,
                ZOMBATAR_CONFIRM_LABEL_Y);
        String aBack = PvzpStringFile.translate("[ZOMBATAR_BACK_BUTTON_TEXT]");
        g.drawString(aBack,
                ZOMBATAR_CONFIRM_BACK_LABEL_X - Resources.getFontById(Resources.FONT_BRIANNETOD12_ID)
                        .stringWidth(aBack) / 2,
                ZOMBATAR_CONFIRM_LABEL_Y);
    }

    // ZombatarWidget::ChangeState (ZombatarWidget.cpp:1223-1236)
    public void changeState(ZombatarWidgetState theState) {
        mState = theState;
        if (theState == ZombatarWidgetState.TO_CONFIRM || theState == ZombatarWidgetState.FROM_CONFIRM) {
            mTransitionTimer = ZOMBATAR_TRANSITION_TICKS; // hover cells intentionally stay frozen
        } else {
            mHoverGridCell = -1;
            mHoverColorCell = -1;
            mHoverTab = -1;
            mDeleteHover = false;
        }
        updateButtonState();
    }

    // ZombatarWidget::ChangePage (ZombatarWidget.cpp:1238-1245)
    public void changePage(ZombatarPage thePage) {
        mPage = thePage;
        mSubPage = 0;
        mHoverGridCell = -1;
        mHoverColorCell = -1;
        updateButtonState();
    }

    // ZombatarWidget::UpdateButtonState (ZombatarWidget.cpp:1247-1279)
    public void updateButtonState() {
        int aCount = getHeadCount();
        boolean aList = mState == ZombatarWidgetState.LIST;
        boolean aCreate = mState == ZombatarWidgetState.CREATE;
        boolean aConfirm = mState == ZombatarWidgetState.CONFIRM;
        boolean aTransition = mState == ZombatarWidgetState.TO_CONFIRM
                || mState == ZombatarWidgetState.FROM_CONFIRM;

        int aTotal = getTotalItemsForPage(mPage);
        mMaxSubPages = (aTotal > ZOMBATAR_GRID_PAGE) ? (aTotal - 1) / ZOMBATAR_GRID_PAGE : 0;
        mSubPage = Math.max(0, Math.min(mSubPage, mMaxSubPages));
        boolean aPaged = aCreate && mMaxSubPages > 0;

        mBackButton.setVisible(aCreate || aList || aConfirm || aTransition);
        mConfirmBackButton.setVisible(aConfirm);
        mViewButton.setVisible(aCreate && aCount > 0);
        mFinishedButton.setVisible(aCreate || aConfirm || aTransition);
        mNewButton.setVisible(aList);
        mPrevPortraitButton.setVisible(aList && aCount > 1 && mCurrentIndex > 0);
        mNextPortraitButton.setVisible(aList && aCount > 1 && mCurrentIndex + 1 < aCount);
        mPrevPageButton.setVisible(aPaged);
        mNextPageButton.setVisible(aPaged);

        mNewButton.mDisabled = !canSaveNewHead();
        mPrevPageButton.mDisabled = mSubPage <= 0;
        mNextPageButton.mDisabled = mSubPage >= mMaxSubPages;
        mFinishedButton.mDisabled = aCreate && !canSaveNewHead();

        if (aConfirm) {
            mFinishedButton.resize(ZOMBATAR_ACCEPT_X, ZOMBATAR_CONFIRM_BTN_Y, 103, 26);
        } else if (aCreate) {
            mFinishedButton.resize(ZOMBATAR_FINISHED_X, ZOMBATAR_FINISHED_Y, 103, 26);
        }
    }

    // ZombatarWidget::MouseMove (ZombatarWidget.cpp:1281-1331)
    @Override
    public void mouseMove(int x, int y) {
        mMouseX = x;
        mMouseY = y;
        mHoverGridCell = -1;
        mHoverColorCell = -1;
        mHoverTab = -1;

        if (mState == ZombatarWidgetState.LIST) {
            mDeleteHover = new Rect(ZOMBATAR_LIST_DELETE_RECT_X, ZOMBATAR_LIST_DELETE_RECT_Y,
                    ZOMBATAR_LIST_DELETE_RECT_WIDTH, ZOMBATAR_LIST_DELETE_RECT_HEIGHT).contains(x, y);
            return;
        }

        if (mState != ZombatarWidgetState.CREATE) {
            return;
        }

        for (int i = 0; i < NUM_ZOMBATAR_PAGES; i++) {
            if (getCategoryRect(i).contains(x, y)) {
                mHoverTab = i;
                break;
            }
        }

        int aItemCount = getSubPageItemCount();
        for (int i = 0; i < aItemCount; i++) {
            if (getItemHitRect(i).contains(x, y)) {
                mHoverGridCell = i;
                break;
            }
        }
        if (mHoverGridCell < 0 && pageAllowsNone() && getItemHitRect(aItemCount).contains(x, y)) {
            mHoverGridCell = aItemCount;
        }

        if (pageAllowsColors()) {
            int aColorCount = mPage == ZombatarPage.SKIN ? ZOMBATAR_SKIN_COLOR_COUNT : ZOMBATAR_PART_COLOR_COUNT;
            for (int i = 0; i < aColorCount; i++) {
                if (getColorRect(i).contains(x, y)) {
                    mHoverColorCell = i;
                    break;
                }
            }
        }
    }

    // ZombatarWidget::HandleGridClick (ZombatarWidget.cpp:1333-1361)
    public void handleGridClick(int theX, int theY) {
        if (mState != ZombatarWidgetState.CREATE) {
            return;
        }

        int aItemCount = getSubPageItemCount();
        int aBaseIndex = mSubPage * ZOMBATAR_GRID_PAGE;
        for (int i = 0; i < aItemCount; i++) {
            if (getItemHitRect(i).contains(theX, theY)) {
                int aPartIndex = aBaseIndex + i;
                if (aPartIndex > 16) {
                    aPartIndex += aPartIndex / 17;
                }
                mPart[mPage.ordinal()] = aPartIndex;
                if (getPartColorMode(mPage, aPartIndex) == ZOMBATAR_COLOR_MODE_NONE) {
                    mColor[mPage.ordinal()] = ZOMBATAR_COLOR_NONE;
                }
                updateButtonState();
                return;
            }
        }

        if (pageAllowsNone() && getItemHitRect(aItemCount).contains(theX, theY)) {
            mPart[mPage.ordinal()] = -1;
            mColor[mPage.ordinal()] = ZOMBATAR_COLOR_NONE;
            updateButtonState();
        }
    }

    // ZombatarWidget::HandleColorClick (ZombatarWidget.cpp:1363-1391)
    public void handleColorClick(int theX, int theY) {
        if (mState != ZombatarWidgetState.CREATE || !pageAllowsColors()) {
            return;
        }

        if (mPage == ZombatarPage.SKIN) {
            for (int i = 0; i < ZOMBATAR_SKIN_COLOR_COUNT; i++) {
                if (getColorRect(i).contains(theX, theY)) {
                    mColor[ZombatarPage.SKIN.ordinal()] = i;
                    return;
                }
            }
            return;
        }

        int aMode = getPartColorMode(mPage, mPart[mPage.ordinal()]);
        int aBase = zombatarColorBaseForMode(aMode);
        for (int i = 0; i < ZOMBATAR_PART_COLOR_COUNT; i++) {
            if (getColorRect(i).contains(theX, theY)) {
                mColor[mPage.ordinal()] = aBase + i;
                return;
            }
        }
    }

    // ZombatarWidget::MouseUp (ZombatarWidget.cpp:1393-1427)
    @Override
    public void mouseUp(int x, int y) {
        if (mApp.mPlayerInfo == null) {
            return;
        }

        if (mState == ZombatarWidgetState.CREATE) {
            for (int i = 0; i < NUM_ZOMBATAR_PAGES; i++) {
                if (getCategoryRect(i).contains(x, y)) {
                    changePage(PAGES[i]);
                    return;
                }
            }

            handleGridClick(x, y);
            handleColorClick(x, y);
        } else if (mState == ZombatarWidgetState.LIST) {
            if (getHeadCount() > 0 && new Rect(ZOMBATAR_LIST_DELETE_RECT_X, ZOMBATAR_LIST_DELETE_RECT_Y,
                    ZOMBATAR_LIST_DELETE_RECT_WIDTH, ZOMBATAR_LIST_DELETE_RECT_HEIGHT).contains(x, y)) {
                mApp.lawnMessageBox(Dialogs.DIALOG_ZOMBATAR_DELETE,
                        "[ZOMBATAR_DELETE_HEADER]", "[ZOMBATAR_DELETE_BODY]",
                        "[ZOMBATAR_DELETE_BUTTON]", "[DIALOG_BUTTON_NO]", Dialog.BUTTONS_YES_NO,
                        theYes -> {
                            if (theYes) {
                                deleteCurrent();
                            }
                        });
            }
        }
    }

    // ZombatarWidget::ButtonPress (ZombatarWidget.cpp:1429-1431) — empty, unlike the base.
    @Override
    public void buttonPress(int theId) {
    }

    // ZombatarWidget::ButtonDepress (ZombatarWidget.cpp:1433-1519)
    @Override
    public void buttonDepress(int theId) {
        if (mApp.mPlayerInfo == null) {
            return;
        }

        switch (theId) {
            case ZOMBATAR_BTN_BACK:
                backToSelector();
                break;

            case ZOMBATAR_BTN_CONFIRM_BACK:
                if (mState == ZombatarWidgetState.CONFIRM) {
                    changeState(ZombatarWidgetState.FROM_CONFIRM);
                }
                break;

            case ZOMBATAR_BTN_VIEW:
                resetDraft();
                changeState(ZombatarWidgetState.LIST);
                break;

            case ZOMBATAR_BTN_FINISHED:
                if (mState == ZombatarWidgetState.CREATE) {
                    if (canSaveNewHead()) {
                        changeState(ZombatarWidgetState.TO_CONFIRM);
                    } else {
                        showMaxHeadsMessage();
                    }
                } else if (mState == ZombatarWidgetState.CONFIRM) {
                    if (saveDraft()) {
                        changeState(ZombatarWidgetState.LIST);
                    }
                }
                break;

            case ZOMBATAR_BTN_NEW:
                if (canSaveNewHead()) {
                    resetDraft();
                    mPage = ZombatarPage.SKIN;
                    mSubPage = 0;
                    changeState(ZombatarWidgetState.CREATE);
                } else {
                    showMaxHeadsMessage();
                }
                break;

            case ZOMBATAR_BTN_PREV_PORTRAIT:
                if (mCurrentIndex > 0) {
                    mCurrentIndex--;
                    loadCurrentToDraft();
                    updateButtonState();
                }
                break;

            case ZOMBATAR_BTN_NEXT_PORTRAIT:
                if (mCurrentIndex + 1 < getHeadCount()) {
                    mCurrentIndex++;
                    loadCurrentToDraft();
                    updateButtonState();
                }
                break;

            case ZOMBATAR_BTN_PREV_PAGE:
                if (mSubPage > 0) {
                    mSubPage--;
                    mHoverGridCell = -1;
                    updateButtonState();
                }
                break;

            case ZOMBATAR_BTN_NEXT_PAGE:
                if (mSubPage < mMaxSubPages) {
                    mSubPage++;
                    mHoverGridCell = -1;
                    updateButtonState();
                }
                break;

            default:
                break;
        }
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

    // ZombatarWidget::KeyDown (ZombatarWidget.cpp:1521-1539)
    @Override
    public void keyDown(int theKey) {
        if (theKey != KeyCode.KEYCODE_ESCAPE) {
            return;
        }

        switch (mState) {
            case TO_CONFIRM:
                mState = ZombatarWidgetState.FROM_CONFIRM;
                mTransitionTimer = ZOMBATAR_TRANSITION_TICKS - mTransitionTimer;
                break;
            case CONFIRM:
                changeState(ZombatarWidgetState.FROM_CONFIRM);
                break;
            default:
                backToSelector();
                break;
        }
    }

    // ZombatarWidget::BackToSelector (ZombatarWidget.cpp:1541-1550)
    public void backToSelector() {
        mPage = ZombatarPage.SKIN;
        mSubPage = 0;
        changeState(getHeadCount() > 0 ? ZombatarWidgetState.LIST : ZombatarWidgetState.CREATE);
        resetDraft();
        mGameSelector.slideTo(0, 0);
        if (mWidgetManager != null) {
            mWidgetManager.setFocus(mGameSelector);
        }
    }

    // ------------------------------------------------------------------
    // File-local helpers

    // ZombatarWidget.cpp:120-164 (SlotForPart).
    private static int slotForPart(ZombatarPage thePage) {
        switch (thePage) {
            case SKIN: return Zombatar.ZOMBATAR_SLOT_SKIN_PART;
            case CLOTHES: return Zombatar.ZOMBATAR_SLOT_CLOTHES;
            case TIDBITS: return Zombatar.ZOMBATAR_SLOT_TIDBITS;
            case ACCESSORY: return Zombatar.ZOMBATAR_SLOT_ACCESSORY;
            case FACIAL_HAIR: return Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR;
            case HAIR: return Zombatar.ZOMBATAR_SLOT_HAIR;
            case EYEWEAR: return Zombatar.ZOMBATAR_SLOT_EYEWEAR;
            case HATS: return Zombatar.ZOMBATAR_SLOT_HATS;
            case BACKDROPS: return Zombatar.ZOMBATAR_SLOT_BACKGROUND;
            default: return Zombatar.ZOMBATAR_SLOT_SKIN_PART;
        }
    }

    // ZombatarWidget.cpp:166-183 (SlotForColor).
    private static int slotForColor(ZombatarPage thePage) {
        switch (thePage) {
            case SKIN: return Zombatar.ZOMBATAR_SLOT_SKIN_COLOR;
            case CLOTHES: return Zombatar.ZOMBATAR_SLOT_CLOTHES_COLOR;
            case TIDBITS: return Zombatar.ZOMBATAR_SLOT_TIDBITS_COLOR;
            case ACCESSORY: return Zombatar.ZOMBATAR_SLOT_ACCESSORY_COLOR;
            case FACIAL_HAIR: return Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR_COLOR;
            case HAIR: return Zombatar.ZOMBATAR_SLOT_HAIR_COLOR;
            case EYEWEAR: return Zombatar.ZOMBATAR_SLOT_EYEWEAR_COLOR;
            case HATS: return Zombatar.ZOMBATAR_SLOT_HATS_COLOR;
            case BACKDROPS: return Zombatar.ZOMBATAR_SLOT_BACKGROUND_COLOR;
            default: return Zombatar.ZOMBATAR_SLOT_SKIN_COLOR;
        }
    }

    // ZombatarWidget.cpp:185-214 (GetPartColorMode).
    private static int getPartColorMode(ZombatarPage thePage, int thePartIndex) {
        switch (thePage) {
            case SKIN: return ZOMBATAR_COLOR_MODE_SKIN;
            case CLOTHES: return ZOMBATAR_COLOR_MODE_NONE;
            case FACIAL_HAIR: return ZOMBATAR_COLOR_MODE_1;
            case HAIR: return thePartIndex == 2 ? ZOMBATAR_COLOR_MODE_NONE : ZOMBATAR_COLOR_MODE_1;
            case BACKDROPS: return thePartIndex == 4 ? ZOMBATAR_COLOR_MODE_2 : ZOMBATAR_COLOR_MODE_NONE;
            case TIDBITS:
                switch (thePartIndex) {
                    case 0: case 1: case 2: case 9: case 10: case 11: return ZOMBATAR_COLOR_MODE_2;
                    default: return ZOMBATAR_COLOR_MODE_NONE;
                }
            case EYEWEAR:
                return thePartIndex < 12 ? ZOMBATAR_COLOR_MODE_2 : ZOMBATAR_COLOR_MODE_NONE;
            case ACCESSORY:
                switch (thePartIndex) {
                    case 7: case 9: case 11: case 12: return ZOMBATAR_COLOR_MODE_2;
                    default: return ZOMBATAR_COLOR_MODE_NONE;
                }
            case HATS:
                return thePartIndex == 12 ? ZOMBATAR_COLOR_MODE_NONE : ZOMBATAR_COLOR_MODE_2;
            default: return ZOMBATAR_COLOR_MODE_NONE;
        }
    }

    // ZombatarWidget.cpp:216-226 (ZombatarGridAlign).
    private static int zombatarGridAlign(ZombatarPage thePage, int theIndex) {
        switch (thePage) {
            case CLOTHES:
                return theIndex == 5 ? 0 : ZOMBATAR_ALIGN_RIGHT | ZOMBATAR_ALIGN_BOTTOM;
            case FACIAL_HAIR:
                return (theIndex == 14 || theIndex == 21 || theIndex == 23) ? ZOMBATAR_ALIGN_BOTTOM : 0;
            case HAIR:
                return (theIndex == 11 || theIndex == 15) ? ZOMBATAR_ALIGN_TOP : 0;
            case HATS:
                return (theIndex >= 6 && theIndex <= 8) ? ZOMBATAR_ALIGN_TOP : 0;
            default:
                return 0;
        }
    }

    // ZombatarWidget.cpp:228-234 (struct ZombatarDrawPart).
    private static final class ZombatarDrawPart {
        ZombatarPage mPage;
        int mIndex;
        int mColor;
        int mDrawOrder;
    }

    // ZombatarWidget.cpp:236-239 (ZombatarColorBaseForMode).
    private static int zombatarColorBaseForMode(int theMode) {
        return theMode == ZOMBATAR_COLOR_MODE_1 ? ZOMBATAR_PART_COLOR_BASE : ZOMBATAR_PART_COLOR_BASE_2;
    }

    // ZombatarWidget.cpp:241-269 (FitIconRect).
    private static Rect fitIconRect(Image theImage, Rect theCell, int theAlign) {
        int aCellW = theCell.mWidth;
        int aCellH = theCell.mHeight;
        int aAvailW = aCellW - 2 * ZOMBATAR_CELL_INSET;
        int aAvailH = aCellH - 2 * ZOMBATAR_CELL_INSET;
        int aW = theImage.getWidth();
        int aH = theImage.getHeight();
        boolean aOvflW = (float) aW > (float) aAvailW;
        boolean aOvflH = (float) aH > (float) aAvailH;
        if (aOvflW && (!aOvflH || aW >= aH)) {
            aH = (int) (aH * ((float) aAvailW / (float) aW));
            aW = aAvailW;
        } else if (aOvflH) {
            aW = (int) (aW * ((float) aAvailH / (float) aH));
            aH = aAvailH;
        }
        int aX = theCell.mX + (aCellW - aW) / 2;
        int aY = theCell.mY + (aCellH - aH) / 2;
        if ((theAlign & ZOMBATAR_ALIGN_TOP) != 0) {
            aY = theCell.mY + ZOMBATAR_CELL_INSET;
        } else if ((theAlign & ZOMBATAR_ALIGN_BOTTOM) != 0) {
            aY = theCell.mY + aCellH - ZOMBATAR_CELL_INSET - aH;
        }
        if ((theAlign & ZOMBATAR_ALIGN_LEFT) != 0) {
            aX = theCell.mX + ZOMBATAR_CELL_INSET;
        } else if ((theAlign & ZOMBATAR_ALIGN_RIGHT) != 0) {
            aX = theCell.mX + aCellW - ZOMBATAR_CELL_INSET - aW;
        }
        return new Rect(aX, aY, aW, aH);
    }

    /** The 0x48-byte record of the {@code theIndex}-th saved head (Java array slice helper). */
    private byte[] recordAt(int theIndex) {
        byte[] aData = mApp.mPlayerInfo.mZombatarData;
        int anOffset = theIndex * PlayerInfo.ZOMBATAR_RECORD_SIZE;
        return Arrays.copyOfRange(aData, anOffset, anOffset + PlayerInfo.ZOMBATAR_RECORD_SIZE);
    }
}
