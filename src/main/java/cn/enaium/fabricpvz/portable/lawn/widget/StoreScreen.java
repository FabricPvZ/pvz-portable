/*
 * Ported from src/Lawn/Widget/StoreScreen.h:31-125 and src/Lawn/Widget/StoreScreen.cpp:48-1185.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.AchievementId;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CoinMotion;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.CrazyDaveState;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.DrawVariation;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.lawn.StoreItem;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.StorePages;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/StoreScreen.h:42 (implementation
 * src/Lawn/Widget/StoreScreen.cpp:48-1185).
 *
 * <p>Crazy Dave's shop: four pages of eight item slots ({@code gStoreItemSpots}), a coin bank funded
 * through {@link PlayerInfo}, page cycling with the hatch animation and the purchase flow. The
 * purchasable upgrades are the seed-slot upgrade ({@code STORE_ITEM_PACKET_UPGRADE}, price ladder
 * 75/500/2000/8000), the extra lawn mower, the rake/cleaners/first-aid and the eight shop plants;
 * zen-garden items write into {@code PlayerInfo.mPurchases} too.
 */
public class StoreScreen extends Dialog {

    // StoreScreen.h:31-32
    public static final int MAX_PAGE_SPOTS = 8;
    public static final int MAX_PURCHASES = 80;

    // StoreScreen.h:46-50
    public static final int StoreScreen_Back = 100;
    public static final int StoreScreen_Prev = 101;
    public static final int StoreScreen_Next = 102;

    // StoreScreen.cpp:48-56
    private static final int STORESCREEN_ITEMOFFSET_1_X = 422;
    private static final int STORESCREEN_ITEMOFFSET_1_Y = 206;
    private static final int STORESCREEN_ITEMOFFSET_2_X = 372;
    private static final int STORESCREEN_ITEMOFFSET_2_Y = 310;
    private static final int STORESCREEN_ITEMSIZE = 74;
    private static final int STORESCREEN_COINBANK_X = 650;
    private static final int STORESCREEN_COINBANK_Y = 559;
    private static final int STORESCREEN_PAGESTRING_X = 470;
    private static final int STORESCREEN_PAGESTRING_Y = 500;

    // StoreScreen.cpp:60-70 (gStoreItemSpots)
    private static final int[][] gStoreItemSpots = {
            {StoreItem.STORE_ITEM_PACKET_UPGRADE, StoreItem.STORE_ITEM_POOL_CLEANER,
                    StoreItem.STORE_ITEM_RAKE, StoreItem.STORE_ITEM_ROOF_CLEANER,
                    StoreItem.STORE_ITEM_PLANT_GATLINGPEA, StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER,
                    StoreItem.STORE_ITEM_PLANT_GLOOMSHROOM, StoreItem.STORE_ITEM_PLANT_CATTAIL},
            {StoreItem.STORE_ITEM_PLANT_SPIKEROCK, StoreItem.STORE_ITEM_PLANT_GOLD_MAGNET,
                    StoreItem.STORE_ITEM_PLANT_WINTERMELON, StoreItem.STORE_ITEM_PLANT_COBCANNON,
                    StoreItem.STORE_ITEM_PLANT_IMITATER, StoreItem.STORE_ITEM_FIRSTAID,
                    StoreItem.STORE_ITEM_INVALID, StoreItem.STORE_ITEM_INVALID},
            {StoreItem.STORE_ITEM_POTTED_MARIGOLD_1, StoreItem.STORE_ITEM_POTTED_MARIGOLD_2,
                    StoreItem.STORE_ITEM_POTTED_MARIGOLD_3, StoreItem.STORE_ITEM_GOLD_WATERINGCAN,
                    StoreItem.STORE_ITEM_FERTILIZER, StoreItem.STORE_ITEM_BUG_SPRAY,
                    StoreItem.STORE_ITEM_PHONOGRAPH, StoreItem.STORE_ITEM_GARDENING_GLOVE},
            {StoreItem.STORE_ITEM_MUSHROOM_GARDEN, StoreItem.STORE_ITEM_AQUARIUM_GARDEN,
                    StoreItem.STORE_ITEM_WHEEL_BARROW, StoreItem.STORE_ITEM_STINKY_THE_SNAIL,
                    StoreItem.STORE_ITEM_TREE_OF_WISDOM, StoreItem.STORE_ITEM_TREE_FOOD,
                    StoreItem.STORE_ITEM_INVALID, StoreItem.STORE_ITEM_INVALID}};

    // StoreScreen.h:55-91
    public LawnApp mApp;
    public NewLawnButton mBackButton;
    public NewLawnButton mPrevButton;
    public NewLawnButton mNextButton;
    public Widget mOverlayWidget;
    public int mStoreTime;
    public String mBubbleText = "";
    public int mBubbleCountDown;
    public boolean mBubbleClickToContinue;
    public int mAmbientSpeechCountDown;
    public int mPreviousAmbientSpeechIndex;
    public int mPage;
    public int mMouseOverItem;
    public int mHatchTimer;
    public boolean mHatchOpen;
    public int mShakeX;
    public int mShakeY;
    public int mStartDialog;
    public boolean mEasyBuyingCheat;
    public boolean mWaitForDialog;
    public PottedPlant mPottedPlantSpecs = new PottedPlant();
    public DataArray<Coin> mCoins;
    public boolean mDrawnOnce;
    public int mAddedAtUpdateCount; // tick snapshot for demo-mode preload timing
    public boolean mGoToTreeNow;
    public boolean mPurchasedFullVersion;
    public boolean mTrialLockedWhenStoreOpened;
    /**
     * The item the "Buy this item?" confirmation is currently about. Upstream keeps this in the
     * {@code theStoreItem} parameter across {@code Dialog::WaitForResult} (StoreScreen.cpp:1009-1020);
     * see the PORT-NOTE in {@link #purchaseItem}.
     */
    public int mConfirmingItem = StoreItem.STORE_ITEM_INVALID;
    /**
     * Whether the open confirmation sheet is the "visit the Tree of Wisdom now?" prompt that
     * follows its purchase (StoreScreen.cpp:1060-1068) rather than a "buy this item?" sheet. Both use
     * {@code Dialogs.DIALOG_STORE_PURCHASE}, exactly like upstream.
     */
    public boolean mConfirmingTreeVisit;

    /**
     * The transition a caller runs on the way out of the store. Upstream's callers sit <em>after</em>
     * {@code StoreScreen::WaitForResult(true)}, which returns only once {@code autoKill} has removed
     * the screen (Dialog.cpp:410-441); this port's {@link Dialog#waitForResult} does not pump
     * (sexy/Dialog.java:506), so such a caller registers its transition here and
     * {@link LawnApp#killStoreScreen()} runs it where the store actually closes.
     */
    @FunctionalInterface
    public interface StoreClosedListener {
        void storeClosed(StoreScreen theStore);
    }

    private StoreClosedListener mStoreClosedListener;

    // StoreScreen::StoreScreen (StoreScreen.cpp:82)
    public StoreScreen(LawnApp theApp) {
        super(null, null, Dialogs.DIALOG_STORE, true, theApp.getString("STORE", "Store"), "", "",
                BUTTONS_NONE);
        mApp = theApp;
        mClip = false;
        mStoreTime = 0;
        mBubbleCountDown = 0;
        mBubbleClickToContinue = false;
        mAmbientSpeechCountDown = 200;
        mPreviousAmbientSpeechIndex = -1;
        mPage = StorePages.STORE_PAGE_SLOT_UPGRADES;
        mMouseOverItem = StoreItem.STORE_ITEM_INVALID;
        mHatchTimer = 0;
        mShakeX = 0;
        mShakeY = 0;
        mStartDialog = -1;
        mHatchOpen = true;
        mEasyBuyingCheat = false;
        mWaitForDialog = false;
        mCoins = new DataArray<>(Coin::new, 1024, "coins");
        mLoadedResourceNames.add("DelayLoad_Store");
        for (String resource : mLoadedResourceNames) {
            Resources.extractResourcesByName(mApp.mResourceManager, resource);
        }
        // StoreScreen.cpp:102-103: the groups registered above are loaded right away, before any widget reads its
        // images - without this the screen drew null images for everything they provide.
        for (String aResource : mLoadedResourceNames) {
            ((cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(aResource);
        }

        resize(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        mPottedPlantSpecs.initializePottedPlant(SeedType.SEED_MARIGOLD);
        mPottedPlantSpecs.mDrawVariation = PvzpCommon.randRangeInt(
                DrawVariation.VARIATION_MARIGOLD_WHITE, DrawVariation.VARIATION_MARIGOLD_LIGHT_GREEN);

        mBackButton = new NewLawnButton(null, StoreScreen_Back, this);
        mBackButton.mDoFinger = true;
        mBackButton.setLabel("[STORE_MAIN_MENU_BUTTON]");
        Image aMenuImage = Resources.getImageById(ImageID.IMAGE_STORE_MAINMENUBUTTON);
        mBackButton.mButtonImage = aMenuImage;
        mBackButton.mOverImage = Resources.getImageById(ImageID.IMAGE_STORE_MAINMENUBUTTONHIGHLIGHT);
        mBackButton.mDownImage = Resources.getImageById(ImageID.IMAGE_STORE_MAINMENUBUTTONDOWN);
        mBackButton.setFont(Resources.getFontById(Resources.FONT_HOUSEOFTERROR20_ID));
        mBackButton.setLabelColor(new Color(98, 153, 235));
        mBackButton.setLabelHiliteColor(new Color(167, 192, 235));
        mBackButton.resize(366, 512, aMenuImage.getWidth(), aMenuImage.getHeight());
        mBackButton.mTextOffsetX = -7;
        mBackButton.mTextOffsetY = 1;
        mBackButton.mTextDownOffsetX = 2;
        mBackButton.mTextDownOffsetY = 1;

        mPrevButton = new NewLawnButton(null, StoreScreen_Prev, this);
        mPrevButton.mDoFinger = true;
        mPrevButton.setLabel("");
        Image aPrevImage = Resources.getImageById(ImageID.IMAGE_STORE_PREVBUTTON);
        mPrevButton.mButtonImage = aPrevImage;
        mPrevButton.mOverImage = Resources.getImageById(ImageID.IMAGE_STORE_PREVBUTTONHIGHLIGHT);
        mPrevButton.mDownImage = Resources.getImageById(ImageID.IMAGE_STORE_PREVBUTTONHIGHLIGHT);
        mPrevButton.setLabelColor(new Color(255, 240, 0));
        mPrevButton.setLabelHiliteColor(new Color(200, 200, 255));
        mPrevButton.resize(252, 402, aPrevImage.getWidth(), aPrevImage.getHeight());

        mNextButton = new NewLawnButton(null, StoreScreen_Next, this);
        mNextButton.mDoFinger = true;
        mNextButton.setLabel("");
        Image aNextImage = Resources.getImageById(ImageID.IMAGE_STORE_NEXTBUTTON);
        mNextButton.mButtonImage = aNextImage;
        mNextButton.mOverImage = Resources.getImageById(ImageID.IMAGE_STORE_NEXTBUTTONHIGHLIGHT);
        mNextButton.mDownImage = Resources.getImageById(ImageID.IMAGE_STORE_NEXTBUTTONHIGHLIGHT);
        mNextButton.setLabelColor(new Color(255, 240, 0));
        mNextButton.setLabelHiliteColor(new Color(200, 200, 255));
        mNextButton.resize(596, 402, aNextImage.getWidth(), aNextImage.getHeight());

        mOverlayWidget = new StoreScreenOverlay(this);
        mOverlayWidget.resize(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);

        if (!isPageShown(StorePages.STORE_PAGE_PLANT_UPGRADES)) {
            mPrevButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_STORE_PREVBUTTONDISABLED);
            mPrevButton.setDisabled(true);
            mNextButton.mDisabledImage = Resources.getImageById(ImageID.IMAGE_STORE_NEXTBUTTONDISABLED);
            mNextButton.setDisabled(true);
        }
        mDrawnOnce = false;
        mAddedAtUpdateCount = mApp.mUpdateCount;
        mGoToTreeNow = false;
        mPurchasedFullVersion = false;
        mTrialLockedWhenStoreOpened = mApp.isTrialStageLocked();

    }

    /**
     * Registers the transition upstream runs once its blocking store wait returned; see
     * {@link StoreClosedListener}.
     */
    public void onStoreClosed(StoreClosedListener theListener) {
        mStoreClosedListener = theListener;
    }

    /**
     * Runs and clears the pending {@link StoreClosedListener}. {@link LawnApp#killStoreScreen()}
     * calls this after the screen has been removed — the point where upstream's
     * {@code Dialog::WaitForResult(true)} returned to its caller (Dialog.cpp:410-441).
     */
    public void runStoreClosedListener() {
        StoreClosedListener aListener = mStoreClosedListener;
        mStoreClosedListener = null;
        if (aListener != null) {
            aListener.storeClosed(this);
        }
    }

    // StoreScreen::GetStoreItemType (StoreScreen.cpp:165)
    public int getStoreItemType(int theSpotIndex) {
        if (mPage < StorePages.NUM_STORE_PAGES && theSpotIndex < MAX_PAGE_SPOTS) {
            if (mPage == StorePages.STORE_PAGE_SLOT_UPGRADES && theSpotIndex == 6
                    && mApp.isTrialStageLocked()) {
                return StoreItem.STORE_ITEM_PVZ;
            }
            return gStoreItemSpots[mPage][theSpotIndex];
        }

        throw new IllegalArgumentException("StoreScreen::GetStoreItemType: bad spot " + theSpotIndex);
    }

    // StoreScreen::IsFullVersionOnly (StoreScreen.cpp:180)
    public boolean isFullVersionOnly(int theStoreItem) {
        if (!mApp.isTrialStageLocked()) {
            return false;
        }

        if (theStoreItem == StoreItem.STORE_ITEM_PACKET_UPGRADE
                && mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] >= 2) {
            return true;
        }

        return theStoreItem == StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER;
    }

    // StoreScreen::IsPottedPlant (StoreScreen.cpp:191)
    public static boolean isPottedPlant(int theStoreItem) {
        return theStoreItem == StoreItem.STORE_ITEM_POTTED_MARIGOLD_1
                || theStoreItem == StoreItem.STORE_ITEM_POTTED_MARIGOLD_2
                || theStoreItem == StoreItem.STORE_ITEM_POTTED_MARIGOLD_3;
    }

    // StoreScreen::IsComingSoon (StoreScreen.cpp:196)
    public boolean isComingSoon(int theStoreItem) {
        if (isFullVersionOnly(theStoreItem)) {
            return true;
        } else if (theStoreItem == StoreItem.STORE_ITEM_WHEEL_BARROW) {
            return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_MUSHROOM_GARDEN] == 0
                    && mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_AQUARIUM_GARDEN] == 0;
        } else if (isPottedPlant(theStoreItem)) {
            return !mApp.hasFinishedAdventure();
        } else if (theStoreItem == StoreItem.STORE_ITEM_TREE_FOOD) {
            return mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] == 0
                    || mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD]
                            < PlayerInfo.PURCHASE_COUNT_OFFSET;
        }
        return false;
    }

    // StoreScreen::IsItemSoldOut (StoreScreen.cpp:209)
    public boolean isItemSoldOut(int theStoreItem) {
        PlayerInfo aPlayer = mApp.mPlayerInfo;
        if (theStoreItem == StoreItem.STORE_ITEM_INVALID) {
            return false;
        } else if (theStoreItem == StoreItem.STORE_ITEM_PACKET_UPGRADE) {
            return aPlayer.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] >= 4;
        } else if (theStoreItem == StoreItem.STORE_ITEM_FERTILIZER
                || theStoreItem == StoreItem.STORE_ITEM_BUG_SPRAY) {
            return aPlayer.mPurchases[theStoreItem] > PlayerInfo.PURCHASE_COUNT_OFFSET + 15;
        } else if (theStoreItem == StoreItem.STORE_ITEM_TREE_FOOD) {
            return aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD]
                    >= PlayerInfo.PURCHASE_COUNT_OFFSET + 10;
        } else if (theStoreItem == StoreItem.STORE_ITEM_BONUS_LAWN_MOWER) {
            return aPlayer.mPurchases[StoreItem.STORE_ITEM_BONUS_LAWN_MOWER] >= 2;
        } else if (isPottedPlant(theStoreItem)) {
            return mApp.mZenGarden.isZenGardenFull(true)
                    || aPlayer.mPurchases[theStoreItem]
                            == LawnCommon.getCurrentDaysSince2000(mApp.getNowTime());
        } else {
            return aPlayer.mPurchases[theStoreItem] != 0;
        }
    }

    // StoreScreen::IsItemUnavailable (StoreScreen.cpp:229)
    public boolean isItemUnavailable(int theStoreItem) {
        if (mEasyBuyingCheat) {
            return false;
        }

        if (theStoreItem == StoreItem.STORE_ITEM_ROOF_CLEANER) {
            return mApp.isTrialStageLocked()
                    || (!mApp.hasFinishedAdventure() && mApp.mPlayerInfo.getLevel() < 42);
        }
        if (theStoreItem == StoreItem.STORE_ITEM_PLANT_GLOOMSHROOM) {
            return mApp.isTrialStageLocked()
                    || (!mApp.hasFinishedAdventure() && mApp.mPlayerInfo.getLevel() < 35);
        }
        if (theStoreItem == StoreItem.STORE_ITEM_PLANT_CATTAIL) {
            return mApp.isTrialStageLocked()
                    || (!mApp.hasFinishedAdventure() && mApp.mPlayerInfo.getLevel() < 35);
        }
        if (theStoreItem == StoreItem.STORE_ITEM_PLANT_SPIKEROCK) {
            return !mApp.hasFinishedAdventure() && mApp.mPlayerInfo.getLevel() < 41;
        }
        if (theStoreItem == StoreItem.STORE_ITEM_PLANT_GOLD_MAGNET) {
            return !mApp.hasFinishedAdventure() && mApp.mPlayerInfo.getLevel() < 41;
        }
        if (theStoreItem == StoreItem.STORE_ITEM_PLANT_WINTERMELON
                || theStoreItem == StoreItem.STORE_ITEM_PLANT_COBCANNON
                || theStoreItem == StoreItem.STORE_ITEM_PLANT_IMITATER
                || theStoreItem == StoreItem.STORE_ITEM_FIRSTAID) {
            return !mApp.hasFinishedAdventure();
        }
        return false;
    }

    // StoreScreen::GetStorePosition (StoreScreen.cpp:288)
    public static int[] getStorePosition(int theSpotIndex) {
        if (theSpotIndex <= 3) {
            return new int[] {STORESCREEN_ITEMOFFSET_1_X + STORESCREEN_ITEMSIZE * theSpotIndex,
                    STORESCREEN_ITEMOFFSET_1_Y};
        }
        return new int[] {STORESCREEN_ITEMOFFSET_2_X + STORESCREEN_ITEMSIZE * (theSpotIndex - 4),
                STORESCREEN_ITEMOFFSET_2_Y};
    }

    // StoreScreen::DrawItemIcon (StoreScreen.cpp:302)
    public void drawItemIcon(Graphics g, int theItemPosition, int theItemType,
            boolean theIsForHighlight) {
        if (theIsForHighlight) {
            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.setColor(new Color(255, 255, 255, 96));
            g.setColorizeImages(true);
        }

        int[] aPos = getStorePosition(theItemPosition);
        int aPosX = aPos[0];
        int aPosY = aPos[1];
        if (theItemType == StoreItem.STORE_ITEM_PACKET_UPGRADE) {
            g.setColor(new Color(255, 255, 255, 32));
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_PACKETUPGRADE), aPosX - 7, aPosY + 7);
            if (theIsForHighlight) {
                g.setDrawMode(Graphics.DRAWMODE_NORMAL);
                g.setColorizeImages(false);
            }

            String aSlotText = PvzpCommon.pvzpReplaceNumberString("[STORE_UPGRADE_SLOTS]", "{SLOTS}",
                    mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] + 7);
            Rect aRect = new Rect(aPosX, aPosY + 6, 55, 70);
            // STORE_USE_*_IMAGE_LABEL not checked: no localized image, always draw text.
            PvzpStringFile.pvzpDrawStringWrapped(g, aSlotText, aRect,
                    Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), Color.White,
                    DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
        } else if (theItemType == StoreItem.STORE_ITEM_POOL_CLEANER) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ICON_POOLCLEANER), aPosX + 1, aPosY + 7);
        } else if (theItemType == StoreItem.STORE_ITEM_RAKE) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ICON_RAKE), aPosX - 5, aPosY + 10);
        } else if (theItemType == StoreItem.STORE_ITEM_ROOF_CLEANER) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ICON_ROOFCLEANER), aPosX, aPosY + 28);
        } else if (theItemType == StoreItem.STORE_ITEM_PLANT_IMITATER) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_IMITATERSEED), aPosX, aPosY);
        } else if (theItemType == StoreItem.STORE_ITEM_MUSHROOM_GARDEN) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_MUSHROOMGARDENICON), aPosX - 8,
                    aPosY + 2);
        } else if (theItemType == StoreItem.STORE_ITEM_AQUARIUM_GARDEN) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_AQUARIUMGARDENICON), aPosX - 8,
                    aPosY + 2);
        } else if (theItemType == StoreItem.STORE_ITEM_TREE_OF_WISDOM) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_TREEOFWISDOMICON), aPosX - 8,
                    aPosY + 2);
        } else if (theItemType == StoreItem.STORE_ITEM_FIRSTAID) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_FIRSTAIDWALLNUTICON), aPosX - 1,
                    aPosY + 13);
        } else if (theItemType == StoreItem.STORE_ITEM_PVZ) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_PVZICON), aPosX, aPosY - 9);
        } else if (theItemType == StoreItem.STORE_ITEM_TREE_FOOD) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_TREEFOOD), aPosX - 8, aPosY - 2);
        } else if (theItemType == StoreItem.STORE_ITEM_STINKY_THE_SNAIL) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_REANIM_STINKY_TURN3), aPosX - 24,
                    aPosY + 14);
        } else if (theItemType == StoreItem.STORE_ITEM_GOLD_WATERINGCAN) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_WATERINGCANGOLD), aPosX - 14, aPosY - 4);
        } else if (theItemType == StoreItem.STORE_ITEM_FERTILIZER) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_FERTILIZER), aPosX - 11, aPosY - 2);
            PvzpCommon.pvzpDrawString(g, "x5", aPosX + 56, aPosY + 62,
                    Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), Color.White,
                    DrawStringJustification.DS_ALIGN_RIGHT);
        } else if (theItemType == StoreItem.STORE_ITEM_PHONOGRAPH) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_PHONOGRAPH), aPosX - 12, aPosY + 3);
        } else if (theItemType == StoreItem.STORE_ITEM_BUG_SPRAY) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_BUG_SPRAY), aPosX - 12, aPosY + 3);
            PvzpCommon.pvzpDrawString(g, "x5", aPosX + 56, aPosY + 62,
                    Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), Color.White,
                    DrawStringJustification.DS_ALIGN_RIGHT);
        } else if (theItemType == StoreItem.STORE_ITEM_GARDENING_GLOVE) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_GARDENGLOVE), aPosX - 12, aPosY + 3);
        } else if (theItemType == StoreItem.STORE_ITEM_WHEEL_BARROW) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_WHEELBARROW), aPosX - 12, aPosY + 3);
        } else if (isPottedPlant(theItemType)) {
            mApp.mZenGarden.drawPottedPlantIcon(g, aPosX, aPosY, mPottedPlantSpecs);
        } else {
            SeedPacket.drawSeedPacket(g, aPosX, aPosY, theItemType + 40, SeedType.SEED_NONE, 0.0f,
                    255, false, false);
        }

        g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        g.setColorizeImages(false);
    }

    // StoreScreen::DrawItem (StoreScreen.cpp:411)
    public void drawItem(Graphics g, int theItemPosition, int theItemType) {
        if (isItemUnavailable(theItemType)) {
            return;
        }

        drawItemIcon(g, theItemPosition, theItemType, false);

        int[] aPos = getStorePosition(theItemPosition);
        int aPosX = aPos[0];
        int aPosY = aPos[1];
        if (theItemType != StoreItem.STORE_ITEM_PVZ) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_PRICETAG), aPosX - 3, aPosY + 70);
            String aCostString = mApp.getMoneyString(getItemCost(theItemType));
            PvzpCommon.pvzpDrawString(g, aCostString, aPosX + 23, aPosY + 85,
                    Resources.getFontById(Resources.FONT_BRIANNETOD12_ID), Color.Black,
                    DrawStringJustification.DS_ALIGN_CENTER);
        }
        if (isComingSoon(theItemType)) {
            Rect aRect = new Rect(aPosX, aPosY, 60, 70);
            if (theItemType == StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER
                    || theItemType == StoreItem.STORE_ITEM_PACKET_UPGRADE) {
                aRect.mX -= 4;
            }
            // STORE_USE_*_IMAGE_LABEL not checked: no localized image, always draw text.
            PvzpStringFile.pvzpDrawStringWrapped(g, "[COMING_SOON]", aRect,
                    Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), new Color(255, 0, 0),
                    DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
        } else if (isItemSoldOut(theItemType)) {
            Rect aRect = new Rect(aPosX, aPosY, 50, 70);
            // STORE_USE_*_IMAGE_LABEL not checked: no localized image, always draw text.
            PvzpStringFile.pvzpDrawStringWrapped(g, "[SOLD_OUT]", aRect,
                    Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), new Color(255, 0, 0),
                    DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
        } else if (mMouseOverItem == theItemType) {
            if (theItemType >= 0 && theItemType <= 8) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_SEEDPACKETFLASH), aPosX, aPosY);
            } else {
                drawItemIcon(g, theItemPosition, theItemType, true);
            }
        }
    }

    // StoreScreen::Draw (StoreScreen.cpp:455)
    @Override
    public void draw(Graphics g) {
        g.setLinearBlend(true);
        mDrawnOnce = true;

        int aStoreSignPosY = PvzpCommon.pvzpAnimateCurve(50, 110, mStoreTime, -150, 0,
                PvzpCurves.CURVE_EASE_IN_OUT);
        if (mApp.isNight()) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_BACKGROUNDNIGHT), 0, 0);
        } else {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_BACKGROUND), 0, 0);
        }

        if (mHatchTimer == 0 && mHatchOpen) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_CAR), mShakeX + 196, mShakeY + 138);
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_HATCHBACKOPEN), mShakeX + 299,
                    mShakeY);
            if (mApp.isNight()) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_CAR_NIGHT), mShakeX + 688,
                        mShakeY + 193);
            }
        } else {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_CARCLOSED), mShakeX + 196,
                    mShakeY + 138);
            if (mApp.isNight()) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_CAR_NIGHT), mShakeX + 688,
                        mShakeY + 193);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_CARCLOSED_NIGHT), mShakeX + 337,
                        mShakeY + 187);
            }
        }
        g.drawImage(Resources.getImageById(ImageID.IMAGE_STORE_SIGN), 285, aStoreSignPosY);

        Graphics gCrazyDave = new Graphics(g);
        gCrazyDave.mTransX -= 42.0f;
        gCrazyDave.mTransY += 68.0f;
        mApp.drawCrazyDave(gCrazyDave);

        if (mHatchTimer == 0 && mHatchOpen) {
            for (int i = 0; i < MAX_PAGE_SPOTS; i++) {
                int aStoreItem = getStoreItemType(i);
                if (aStoreItem != StoreItem.STORE_ITEM_INVALID) {
                    drawItem(g, i, aStoreItem);
                }
            }
        }

        g.drawImage(Resources.getImageById(ImageID.IMAGE_COINBANK), STORESCREEN_COINBANK_X,
                STORESCREEN_COINBANK_Y);
        g.setColor(new Color(180, 255, 90));
        g.setFont(Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID));
        String aCoinLabel = mApp.getMoneyString(mApp.mPlayerInfo.mCoins);
        g.drawString(aCoinLabel,
                STORESCREEN_COINBANK_X + 116
                        - Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID)
                                .stringWidth(aCoinLabel),
                STORESCREEN_COINBANK_Y + 24);

        if (!mPrevButton.mDisabled) {
            int aNumPages = 0;
            for (int aPage = StorePages.STORE_PAGE_SLOT_UPGRADES; aPage < StorePages.NUM_STORE_PAGES;
                    aPage++) {
                if (isPageShown(aPage)) {
                    aNumPages++;
                }
            }

            String aPageString = PvzpCommon.pvzpReplaceNumberString(
                    PvzpCommon.pvzpReplaceNumberString("[STORE_PAGE]", "{PAGE}", mPage + 1),
                    "{NUM_PAGES}", aNumPages);
            PvzpCommon.pvzpDrawString(g, aPageString, STORESCREEN_PAGESTRING_X,
                    STORESCREEN_PAGESTRING_Y, Resources.getFontById(Resources.FONT_BRIANNETOD12_ID),
                    new Color(80, 80, 80), DrawStringJustification.DS_ALIGN_CENTER);
        }
    }

    // StoreScreen::DrawOverlay (StoreScreen.cpp:517)
    @Override
    public void drawOverlay(Graphics g) {
        for (Coin aCoin : mCoins) {
            if (!aCoin.mDead) {
                aCoin.draw(g);
            }
        }
    }

    // StoreScreen::SetBubbleText (StoreScreen.cpp:540)
    public void setBubbleText(int theCrazyDaveMessage, int theTime, boolean theClickToContinue) {
        mApp.crazyDaveTalkIndex(theCrazyDaveMessage);
        mBubbleCountDown = theTime;
        mBubbleClickToContinue = theClickToContinue;
    }

    // StoreScreen::UpdateMouse (StoreScreen.cpp:547)
    public void updateMouse() {
        mMouseOverItem = StoreItem.STORE_ITEM_INVALID;
        if (mStoreTime < 120 || mBubbleClickToContinue || mHatchTimer > 0 || mWaitForDialog) {
            return;
        }
        int aMouseX = mApp.mWidgetManager.mLastMouseX - mX;
        int aMouseY = mApp.mWidgetManager.mLastMouseY - mY;
        boolean aShowFinger = false;
        for (int aItemPos = 0; aItemPos < MAX_PAGE_SPOTS; aItemPos++) {
            int aItemType = getStoreItemType(aItemPos);
            if (aItemType != StoreItem.STORE_ITEM_INVALID && !isItemUnavailable(aItemType)) {
                int[] aItemPos2 = getStorePosition(aItemPos);
                if (new Rect(aItemPos2[0], aItemPos2[1], 50, 87).contains(aMouseX, aMouseY)) {
                    mMouseOverItem = aItemType;
                    int aMessageIndex = -1;
                    switch (aItemType) {
                        case StoreItem.STORE_ITEM_PLANT_GATLINGPEA:
                            aMessageIndex = 2000;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER:
                            aMessageIndex = 2001;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_GLOOMSHROOM:
                            aMessageIndex = 2002;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_CATTAIL:
                            aMessageIndex = 2003;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_WINTERMELON:
                            aMessageIndex = 2004;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_GOLD_MAGNET:
                            aMessageIndex = 2005;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_SPIKEROCK:
                            aMessageIndex = 2006;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_COBCANNON:
                            aMessageIndex = 2007;
                            break;
                        case StoreItem.STORE_ITEM_PLANT_IMITATER:
                            aMessageIndex = 2008;
                            break;
                        case StoreItem.STORE_ITEM_BONUS_LAWN_MOWER:
                            aMessageIndex = 2009;
                            break;
                        case StoreItem.STORE_ITEM_POTTED_MARIGOLD_1:
                        case StoreItem.STORE_ITEM_POTTED_MARIGOLD_2:
                        case StoreItem.STORE_ITEM_POTTED_MARIGOLD_3:
                            aMessageIndex = 2010;
                            break;
                        case StoreItem.STORE_ITEM_GOLD_WATERINGCAN:
                            aMessageIndex = 2019;
                            break;
                        case StoreItem.STORE_ITEM_FERTILIZER:
                            aMessageIndex = 2020;
                            break;
                        case StoreItem.STORE_ITEM_BUG_SPRAY:
                            aMessageIndex = 2022;
                            break;
                        case StoreItem.STORE_ITEM_PHONOGRAPH:
                            aMessageIndex = 2021;
                            break;
                        case StoreItem.STORE_ITEM_GARDENING_GLOVE:
                            aMessageIndex = 2023;
                            break;
                        case StoreItem.STORE_ITEM_MUSHROOM_GARDEN:
                            aMessageIndex = 2032;
                            break;
                        case StoreItem.STORE_ITEM_WHEEL_BARROW:
                            aMessageIndex = 2024;
                            break;
                        case StoreItem.STORE_ITEM_STINKY_THE_SNAIL:
                            aMessageIndex = 2025;
                            break;
                        case StoreItem.STORE_ITEM_PACKET_UPGRADE:
                            aMessageIndex = Math.max(2011, Math.min(2014,
                                    mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]
                                            + 2011));
                            break;
                        case StoreItem.STORE_ITEM_POOL_CLEANER:
                            aMessageIndex = 2026;
                            break;
                        case StoreItem.STORE_ITEM_ROOF_CLEANER:
                            aMessageIndex = 2027;
                            break;
                        case StoreItem.STORE_ITEM_RAKE:
                            aMessageIndex = 2028;
                            break;
                        case StoreItem.STORE_ITEM_AQUARIUM_GARDEN:
                            aMessageIndex = 2029;
                            break;
                        case StoreItem.STORE_ITEM_CHOCOLATE:
                            break;
                        case StoreItem.STORE_ITEM_TREE_OF_WISDOM:
                            aMessageIndex = 2030;
                            break;
                        case StoreItem.STORE_ITEM_TREE_FOOD:
                            aMessageIndex = 2031;
                            break;
                        case StoreItem.STORE_ITEM_FIRSTAID:
                            aMessageIndex = 2033;
                            break;
                        case StoreItem.STORE_ITEM_PVZ:
                            aMessageIndex = 2034;
                            break;
                        default:
                            throw new IllegalStateException("StoreScreen::UpdateMouse: bad item "
                                    + aItemType);
                    }
                    if (mApp.mCrazyDaveMessageIndex != aMessageIndex) {
                        setBubbleText(aMessageIndex, 100, false);
                    } else {
                        mBubbleCountDown = 100;
                    }
                    if (isFullVersionOnly(aItemType) || (!isItemSoldOut(aItemType)
                            && !isItemUnavailable(aItemType) && !isComingSoon(aItemType))) {
                        aShowFinger = true;
                    }
                    break;
                }
            }
        }

        mApp.setCursor(mBackButton.mIsOver || mPrevButton.mIsOver || mNextButton.mIsOver
                || aShowFinger ? SexyAppBase.CURSOR_HAND : SexyAppBase.CURSOR_POINTER);
    }

    // StoreScreen::StorePreload (StoreScreen.cpp:614)
    public void storePreload() {
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CRAZY_DAVE, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZENGARDEN_FERTILIZER, true);
        mApp.crazyDaveEnter();

        Plant.preloadPlantResources(SeedType.SEED_GARLIC);
        Plant.preloadPlantResources(SeedType.SEED_TWINSUNFLOWER);

        if (mApp.hasFinishedAdventure()) {
            Plant.preloadPlantResources(SeedType.SEED_GLOOMSHROOM);
            Plant.preloadPlantResources(SeedType.SEED_CATTAIL);
            Plant.preloadPlantResources(SeedType.SEED_WINTERMELON);
            Plant.preloadPlantResources(SeedType.SEED_GOLD_MAGNET);
            Plant.preloadPlantResources(SeedType.SEED_SPIKEROCK);
            Plant.preloadPlantResources(SeedType.SEED_COBCANNON);
            Plant.preloadPlantResources(SeedType.SEED_IMITATER);
        }
    }

    // StoreScreen::CanInteractWithButtons (StoreScreen.cpp:635)
    public boolean canInteractWithButtons() {
        return mStoreTime >= 120 && !mBubbleClickToContinue && mHatchTimer <= 0 && !mWaitForDialog;
    }

    // StoreScreen::Update (StoreScreen.cpp:640)
    @Override
    public void update() {
        mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME);
        mApp.updateCrazyDave();

        for (Coin aCoin : mCoins) {
            if (!aCoin.mDead) {
                aCoin.update();
            }
        }

        if (mWaitForDialog) {
            return;
        }

        if (mApp.mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_OFF) {
            // demo sessions preload by update tick instead of the frame-scheduled mDrawnOnce
            boolean aShouldPreload = mApp.isInDemoMode()
                    ? (mApp.mUpdateCount - mAddedAtUpdateCount >= 2) : mDrawnOnce;
            if (aShouldPreload) {
                storePreload();
            }
            return;
        }

        mStoreTime++;
        if (mApp.mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_OFF
                && mApp.mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_ENTERING) {
            if (mHatchTimer > 0) {
                mHatchTimer--;
                mBackButton.mX -= mShakeX;
                mBackButton.mY -= mShakeY;
                mPrevButton.mX -= mShakeX;
                mPrevButton.mY -= mShakeY;
                mNextButton.mX -= mShakeX;
                mNextButton.mY -= mShakeY;

                if (mHatchTimer == 0) {
                    enableButtons(true);
                    mShakeX = 0;
                    mShakeY = 0;
                } else {
                    mShakeX = 0;
                    if (mHatchTimer > 35) {
                        mShakeY = PvzpCommon.randRangeInt(1, 3);
                    } else {
                        mShakeY = 0;
                    }
                }

                mBackButton.mX += mShakeX;
                mBackButton.mY += mShakeY;
                mPrevButton.mX += mShakeX;
                mPrevButton.mY += mShakeY;
                mNextButton.mX += mShakeX;
                mNextButton.mY += mShakeY;
            } else if (mStartDialog != -1) {
                setBubbleText(mStartDialog, 0, true);
                mStartDialog = -1;
            } else if (!mBubbleClickToContinue) {
                if (mBubbleCountDown > 0) {
                    mBubbleCountDown--;
                    if (mBubbleCountDown == 0) {
                        if (mApp.mSoundSystem.isFoleyPlaying(FoleyType.FOLEY_CRAZY_DAVE_SHORT)
                                || mApp.mSoundSystem.isFoleyPlaying(FoleyType.FOLEY_CRAZY_DAVE_LONG)
                                || mApp.mSoundSystem
                                        .isFoleyPlaying(FoleyType.FOLEY_CRAZY_DAVE_EXTRA_LONG)) {
                            mBubbleCountDown = 1;
                        } else {
                            mApp.crazyDaveStopTalking();
                        }
                    }
                } else {
                    mAmbientSpeechCountDown--;
                    if (mAmbientSpeechCountDown <= 0) {
                        PvzpWeightedArray[] aPickArray = new PvzpWeightedArray[4];
                        for (int i = 0; i < 4; i++) {
                            int aMessage = 2015 + i;
                            aPickArray[i] = new PvzpWeightedArray();
                            aPickArray[i].mItem = aMessage;
                            if (mPreviousAmbientSpeechIndex == aMessage) {
                                aPickArray[i].mWeight = 0;
                            } else if (i == 3) {
                                aPickArray[i].mWeight = mApp.hasFinishedAdventure() ? 20 : 0;
                            } else {
                                aPickArray[i].mWeight = 100;
                            }
                        }

                        int aDaveMessage = (int) PvzpCommon.pvzpPickFromWeightedArray(aPickArray, 4);
                        mPreviousAmbientSpeechIndex = aDaveMessage;
                        setBubbleText(aDaveMessage, 800, false);
                        mAmbientSpeechCountDown = PvzpCommon.randRangeInt(500, 1000);
                    }
                }
            }
        }

        updateMouse();
        // store opened in trial mode and now unlocked: the player just purchased the full version
        if (canInteractWithButtons() && mTrialLockedWhenStoreOpened && !mApp.isTrialStageLocked()) {
            mPurchasedFullVersion = true;
            mResult = Dialog.ID_OK;
        } else {
            super.update();
            markDirty();
        }
    }

    // StoreScreen::AddedToManager (StoreScreen.cpp:794)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mBackButton);
        addWidget(mPrevButton);
        addWidget(mNextButton);
        addWidget(mOverlayWidget);
    }

    // StoreScreen::RemovedFromManager (StoreScreen.cpp:803)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mBackButton);
        removeWidget(mPrevButton);
        removeWidget(mNextButton);
        removeWidget(mOverlayWidget);
        mApp.crazyDaveDie();
    }

    // StoreScreen::ButtonPress (StoreScreen.cpp:812)
    @Override
    public void buttonPress(int theId) {
        if (theId != StoreScreen_Prev && theId != StoreScreen_Next) {
            mApp.playSample(SoundType.SOUND_BUTTONCLICK);
        }
    }

    // StoreScreen::IsPageShown (StoreScreen.cpp:818)
    public boolean isPageShown(int thePage) {
        // trial mode only shows the default page
        if (mApp.isTrialStageLocked()) {
            return thePage == StorePages.STORE_PAGE_SLOT_UPGRADES;
        }
        // finishing adventure unlocks all pages
        if (mApp.hasFinishedAdventure()) {
            return true;
        }
        // the plant upgrades page requires reaching adventure 5-2 (level 42)
        if (thePage == StorePages.STORE_PAGE_PLANT_UPGRADES) {
            return mApp.mPlayerInfo.mLevel >= 42;
        }
        // the zen garden page requires reaching adventure 5-5 (level 45)
        if (thePage == StorePages.STORE_PAGE_ZEN1) {
            return mApp.mPlayerInfo.mLevel >= 45;
        }
        // hide the Tree of Wisdom page until adventure is finished
        return thePage != StorePages.STORE_PAGE_ZEN2;
    }

    // StoreScreen::ButtonDepress (StoreScreen.cpp:832)
    @Override
    public void buttonDepress(int theId) {
        if (theId == StoreScreen_Back) {
            // PORT-NOTE: StoreScreen.cpp:832-834 only sets mResult; upstream's caller waits in
            // Dialog::WaitForResult(true) whose autoKill then removes the screen (Dialog.cpp:410-441).
            // This port's waitForResult does not pump (sexy/Dialog.java:506), so nothing would ever
            // close the store — the player could not leave it. Kill it here, the way autoKill would.
            mResult = 1000;
            mApp.killStoreScreen();
        } else if (theId == StoreScreen_Prev || theId == StoreScreen_Next) {
            mHatchTimer = 50;
            mApp.playSample(SoundType.SOUND_HATCHBACK_CLOSE);
            mBubbleCountDown = 0;
            mApp.crazyDaveStopTalking();
            enableButtons(false);
            do {
                if (theId == StoreScreen_Prev) {
                    mPage = mPage - 1;
                    if (mPage < StorePages.STORE_PAGE_SLOT_UPGRADES) {
                        mPage = StorePages.STORE_PAGE_ZEN2;
                    }
                } else {
                    mPage = mPage + 1;
                    if (mPage >= StorePages.NUM_STORE_PAGES) {
                        mPage = StorePages.STORE_PAGE_SLOT_UPGRADES;
                    }
                }
            } while (!isPageShown(mPage));
        }
    }

    // StoreScreen::KeyDown (StoreScreen.cpp:865)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            buttonDepress(StoreScreen_Back);
            return;
        }

        if (mBubbleClickToContinue
                && (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN)) {
            advanceCrazyDaveDialog();
            return;
        }

        super.keyDown(theKey);
    }

    // StoreScreen::GetItemCost (StoreScreen.cpp:882)
    public static int getItemCost(int theStoreItem) {
        if (theStoreItem == StoreItem.STORE_ITEM_BONUS_LAWN_MOWER) {
            return LawnApp.gLawnApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BONUS_LAWN_MOWER] != 0
                    ? 500 : 200;
        }
        switch (theStoreItem) {
            case StoreItem.STORE_ITEM_PLANT_GATLINGPEA:
                return 500;
            case StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER:
                return 500;
            case StoreItem.STORE_ITEM_PLANT_GLOOMSHROOM:
                return 750;
            case StoreItem.STORE_ITEM_PLANT_CATTAIL:
                return 1000;
            case StoreItem.STORE_ITEM_PLANT_WINTERMELON:
                return 1000;
            case StoreItem.STORE_ITEM_PLANT_GOLD_MAGNET:
                return 300;
            case StoreItem.STORE_ITEM_PLANT_SPIKEROCK:
                return 750;
            case StoreItem.STORE_ITEM_PLANT_COBCANNON:
                return 2000;
            case StoreItem.STORE_ITEM_PLANT_IMITATER:
                return 3000;
            case StoreItem.STORE_ITEM_POTTED_MARIGOLD_1:
                return 250;
            case StoreItem.STORE_ITEM_POTTED_MARIGOLD_2:
                return 250;
            case StoreItem.STORE_ITEM_POTTED_MARIGOLD_3:
                return 250;
            case StoreItem.STORE_ITEM_GOLD_WATERINGCAN:
                return 1000;
            case StoreItem.STORE_ITEM_FERTILIZER:
                return 75;
            case StoreItem.STORE_ITEM_BUG_SPRAY:
                return 100;
            case StoreItem.STORE_ITEM_PHONOGRAPH:
                return 1500;
            case StoreItem.STORE_ITEM_GARDENING_GLOVE:
                return 100;
            case StoreItem.STORE_ITEM_MUSHROOM_GARDEN:
                return 3000;
            case StoreItem.STORE_ITEM_WHEEL_BARROW:
                return 20;
            case StoreItem.STORE_ITEM_STINKY_THE_SNAIL:
                return 300;
            case StoreItem.STORE_ITEM_PACKET_UPGRADE: {
                int aPurchase = LawnApp.gLawnApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE];
                return aPurchase == 0 ? 75 : aPurchase == 1 ? 500 : aPurchase == 2 ? 2000 : 8000;
            }
            case StoreItem.STORE_ITEM_POOL_CLEANER:
                return 100;
            case StoreItem.STORE_ITEM_ROOF_CLEANER:
                return 300;
            case StoreItem.STORE_ITEM_RAKE:
                return 20;
            case StoreItem.STORE_ITEM_AQUARIUM_GARDEN:
                return 3000;
            case StoreItem.STORE_ITEM_TREE_OF_WISDOM:
                return 1000;
            case StoreItem.STORE_ITEM_TREE_FOOD:
                return 250;
            case StoreItem.STORE_ITEM_FIRSTAID:
                return 200;
            default:
                throw new IllegalStateException("StoreScreen::GetItemCost: bad item " + theStoreItem);
        }
    }

    // StoreScreen::CanAffordItem (StoreScreen.cpp:925)
    public boolean canAffordItem(int theStoreItem) {
        return mApp.mPlayerInfo.mCoins >= getItemCost(theStoreItem);
    }

    // StoreScreen::PurchaseItem (StoreScreen.cpp:928)
    public void purchaseItem(int theStoreItem) {
        mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        mBubbleCountDown = 0;
        mApp.crazyDaveStopTalking();
        if (!canAffordItem(theStoreItem)) {
            // the localization key names for this dialog are wrong
            Dialog aDialog = mApp.doDialog(Dialogs.DIALOG_NOT_ENOUGH_MONEY, true,
                    mApp.getString("NOT_ENOUGH_MONEY", "Not enough money"),
                    mApp.getString("CANNOT_AFFORD_ITEM",
                            "You can't afford this item yet. Earn more coins by killing zombies!"),
                    "[DIALOG_BUTTON_OK]", BUTTONS_FOOTER);
            mWaitForDialog = true;
            aDialog.waitForResult(true);
            mWaitForDialog = false;
        } else {
            // PORT-NOTE: StoreScreen.cpp:1009-1020 — upstream asks with Dialog::WaitForResult() and
            // applies the purchase right after it returns. That call pumps the message loop until a
            // button is depressed (Dialog.cpp:410-441); this port's waitForResult does not pump
            // (sexy/Dialog.java:506), so the result read here was always the "not answered yet"
            // sentinel (0x7FFFFFFF) and no store item could ever be bought. The item is remembered
            // and the purchase is applied when the answer arrives — LawnApp.buttonDepress routes the
            // confirm dialog's buttons to finishPurchaseConfirmation(), like every other dialog.
            mConfirmingItem = theStoreItem;
            mConfirmingTreeVisit = false;
            LawnDialog aComfirmDialog = (LawnDialog) mApp.doDialog(Dialogs.DIALOG_STORE_PURCHASE, true,
                    mApp.getString("BUY_ITEM_HEADER", "Buy this item?"),
                    mApp.getString("BUY_ITEM", "Are you sure you want to buy this item?"), "",
                    BUTTONS_YES_NO);
            aComfirmDialog.mLawnYesButton.setLabel("[DIALOG_BUTTON_YES]");
            aComfirmDialog.mLawnNoButton.setLabel("[DIALOG_BUTTON_NO]");

            // Upstream holds this true for the whole blocking wait; the confirm dialog is modal, so
            // the store does not update until the answer arrives either way.
            mWaitForDialog = true;
        }
    }

    /**
     * StoreScreen.cpp:1017-1019 — applies the confirmed purchase. Upstream runs this right after the
     * blocking {@code Dialog::WaitForResult()}; see the PORT-NOTE in {@link #purchaseItem}.
     */
    public void finishPurchaseConfirmation(boolean theConfirmed) {
        int anItem = mConfirmingItem;
        boolean aTreeVisit = mConfirmingTreeVisit;
        mConfirmingItem = StoreItem.STORE_ITEM_INVALID;
        mConfirmingTreeVisit = false;
        mWaitForDialog = false;

        if (aTreeVisit) {
            // StoreScreen.cpp:1066-1068 — "do you want to visit the Tree of Wisdom now?" Upstream
            // stores the answer in the *store's* own result, so the caller's outer
            // WaitForResult(true) stops pumping and its autoKill removes the screen (Dialog.cpp:410-441);
            // the caller then reads mGoToTreeNow. Nothing pumps here, so the store closes itself — the
            // same reason the Back button does (see buttonDepress).
            if (theConfirmed) {
                mGoToTreeNow = true;
                mResult = Dialog.ID_OK;
                mApp.killStoreScreen();
            }
            return;
        }

        if (theConfirmed && anItem != StoreItem.STORE_ITEM_INVALID) {
            applyPurchase(anItem);
        }
    }

    // PORT-NOTE: StoreScreen.cpp:928-1079 — the post-confirmation body of PurchaseItem is extracted
    // into this method so the profile mutation is reachable without going through the confirmation
    // dialog. Behaviour is unchanged apart from the extraction.
    public void applyPurchase(int theStoreItem) {
                mApp.mPlayerInfo.addCoins(-getItemCost(theStoreItem));
                if (theStoreItem == StoreItem.STORE_ITEM_PACKET_UPGRADE) {
                    mApp.mPlayerInfo.mPurchases[theStoreItem]++;
                    String aDialogLines = mApp.getFormattedString("NOW_YOU_CAN_CHOOSE_X_SEEDS",
                            "Now you can choose to take %d seeds with you per level!",
                            6 + mApp.mPlayerInfo.mPurchases[theStoreItem]);
                    Dialog aDialog = mApp.doDialog(Dialogs.DIALOG_UPGRADED, true,
                            mApp.getString("MORE_SLOTS", "More slots!"), aDialogLines,
                            "[DIALOG_BUTTON_OK]", BUTTONS_FOOTER);

                    mWaitForDialog = true;
                    aDialog.waitForResult(true);
                    mWaitForDialog = false;

                    if (mApp.mBoard != null) {
                        mApp.mBoard.mSeedBank.updateWidth();
                    }
                } else if (theStoreItem == StoreItem.STORE_ITEM_BONUS_LAWN_MOWER) {
                    mApp.mPlayerInfo.mPurchases[theStoreItem]++;
                } else if (theStoreItem == StoreItem.STORE_ITEM_RAKE) {
                    mApp.mPlayerInfo.mPurchases[theStoreItem] = 3;
                } else if (theStoreItem == StoreItem.STORE_ITEM_STINKY_THE_SNAIL) {
                    int aTime = (int) mApp.getNowTime();
                    if (aTime == 0) {
                        aTime = 1;
                    }
                    mApp.mPlayerInfo.mPurchases[theStoreItem] = aTime;
                } else if (theStoreItem == StoreItem.STORE_ITEM_FERTILIZER
                        || theStoreItem == StoreItem.STORE_ITEM_BUG_SPRAY) {
                    if (mApp.mPlayerInfo.mPurchases[theStoreItem] < PlayerInfo.PURCHASE_COUNT_OFFSET) {
                        mApp.mPlayerInfo.mPurchases[theStoreItem] = PlayerInfo.PURCHASE_COUNT_OFFSET;
                    }
                    mApp.mPlayerInfo.mPurchases[theStoreItem] += 5;
                } else if (theStoreItem == StoreItem.STORE_ITEM_TREE_FOOD) {
                    if (mApp.mPlayerInfo.mPurchases[theStoreItem] < PlayerInfo.PURCHASE_COUNT_OFFSET) {
                        mApp.mPlayerInfo.mPurchases[theStoreItem] = PlayerInfo.PURCHASE_COUNT_OFFSET;
                    }
                    mApp.mPlayerInfo.mPurchases[theStoreItem]++;
                } else if (theStoreItem == StoreItem.STORE_ITEM_TREE_OF_WISDOM) {
                    mApp.mPlayerInfo.mPurchases[theStoreItem] = 1;
                    mApp.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM
                            - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1] = 1;

                    LawnDialog aDialog = (LawnDialog) mApp.doDialog(Dialogs.DIALOG_STORE_PURCHASE, true,
                            "[VISIT_TREE_HEADER]", "[VISIT_TREE_BODY]", "", BUTTONS_YES_NO);
                    aDialog.mLawnYesButton.setLabel("[DIALOG_BUTTON_YES]");
                    aDialog.mLawnNoButton.setLabel("[DIALOG_BUTTON_NO]");

                    // PORT-NOTE: StoreScreen.cpp:1060-1068 — upstream reads the answer from the
                    // blocking Dialog::WaitForResult(); this port applies it in
                    // finishPurchaseConfirmation() (the same dialog id is the store's confirmation
                    // sheet, see purchaseItem), from LawnApp.buttonDepress.
                    mConfirmingItem = StoreItem.STORE_ITEM_INVALID;
                    mConfirmingTreeVisit = true;
                    mWaitForDialog = true;
                } else if (isPottedPlant(theStoreItem)) {
                    mApp.mZenGarden.addPottedPlant(mPottedPlantSpecs);
                    mPottedPlantSpecs.initializePottedPlant(SeedType.SEED_MARIGOLD);
                    mPottedPlantSpecs.mDrawVariation = PvzpCommon.randRangeInt(
                            DrawVariation.VARIATION_MARIGOLD_WHITE,
                            DrawVariation.VARIATION_MARIGOLD_LIGHT_GREEN);
                    mApp.mPlayerInfo.mPurchases[theStoreItem] =
                            LawnCommon.getCurrentDaysSince2000(mApp.getNowTime());
                } else {
                    if (theStoreItem < StoreItem.STORE_ITEM_PLANT_GATLINGPEA
                            || theStoreItem >= MAX_PURCHASES) {
                        throw new IllegalStateException("StoreScreen::PurchaseItem: bad item "
                                + theStoreItem);
                    }
                    mApp.mPlayerInfo.mPurchases[theStoreItem] = 1;
                }

                if (theStoreItem == StoreItem.STORE_ITEM_FIRSTAID) {
                    setBubbleText(3400, 800, false);
                }

                if (mApp.mSeedChooserScreen != null) {
                    mApp.mSeedChooserScreen.updateAfterPurchase();
                }

                // Only give the achievement if the player bought a plant and has all plants purchased
                boolean aGiveAchievement = theStoreItem >= StoreItem.STORE_ITEM_PLANT_GATLINGPEA
                        && theStoreItem <= StoreItem.STORE_ITEM_PLANT_IMITATER;
                if (aGiveAchievement) {
                    for (int aSeedType = SeedType.SEED_GATLINGPEA; aSeedType <= SeedType.SEED_IMITATER;
                            aSeedType++) {
                        if (!mApp.hasSeedType(aSeedType)) {
                            aGiveAchievement = false;
                        }
                    }
                }

                if (aGiveAchievement) {
                    ReportAchievement.giveAchievement(mApp, AchievementId.Morticulturalist, true);
                    setBubbleText(4000, 800, false);
                }

                mApp.writeCurrentUserConfig();
            }

    // StoreScreen::AdvanceCrazyDaveDialog (StoreScreen.cpp:1080)
    public void advanceCrazyDaveDialog() {
        if (!mBubbleClickToContinue) {
            return;
        }

        // "Hey neighbor! I've got some new things to sell!"
        if (mApp.mCrazyDaveMessageIndex == 3100) {
            mHatchTimer = 150;
            mHatchOpen = true;
            mApp.playSample(SoundType.SOUND_HATCHBACK_OPEN);
        }
        if (!mApp.advanceCrazyDaveText()) {
            mApp.crazyDaveStopTalking();
            mBubbleClickToContinue = false;
            mBubbleCountDown = 500;
            if (mHatchTimer == 0) {
                enableButtons(true);
            }
        } else {
            setBubbleText(mApp.mCrazyDaveMessageIndex, 0, true);
        }

        int aMessage = mApp.mCrazyDaveMessageIndex;
        if (aMessage == 303 || aMessage == 606 || aMessage == 2601) {
            mHatchTimer = 150;
            mHatchOpen = true;
            mApp.playSample(SoundType.SOUND_HATCHBACK_OPEN);
        } else if (aMessage == 603) {
            mApp.mPlayerInfo.mNeedsMagicTacoReward = 0;
            mApp.writeCurrentUserConfig();
            mApp.playSample(SoundType.SOUND_DIAMOND);
            Coin aCoin = mCoins.dataArrayAlloc();
            aCoin.coinInitialize(80, 520, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_FROM_PRESENT);
            aCoin.mVelX = 0;
            aCoin.mVelY = -5;
        } else if (aMessage == 902 || aMessage == 1002) {
            mApp.mPlayerInfo.addCoins(100);
        }
    }

    // StoreScreen::MouseDown (StoreScreen.cpp:1130)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        if (mBubbleClickToContinue) {
            advanceCrazyDaveDialog();
            return;
        }
        if (!canInteractWithButtons()) {
            return;
        }
        for (int aItemPos = 0; aItemPos < MAX_PAGE_SPOTS; aItemPos++) {
            int aItemType = getStoreItemType(aItemPos);
            if (aItemType == StoreItem.STORE_ITEM_INVALID) {
                continue;
            }
            int[] aItemPos2 = getStorePosition(aItemPos);
            if (new Rect(aItemPos2[0], aItemPos2[1], 50, 87).contains(x, y)) {
                if (isFullVersionOnly(aItemType)) {
                    mWaitForDialog = true;
                    mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "[GET_FULL_VERSION_TITLE]",
                            "[FULL_VERSION_TO_BUY]", "[DIALOG_BUTTON_OK]", "", BUTTONS_FOOTER);
                    mWaitForDialog = false;
                } else if (aItemType == StoreItem.STORE_ITEM_PVZ) {
                    mWaitForDialog = true;
                    mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "[BUY_PVZ_TITLE]", "[BUY_PVZ_BODY]",
                            "[GET_FULL_VERSION_YES_BUTTON]", "[GET_FULL_VERSION_NO_BUTTON]",
                            BUTTONS_YES_NO);
                    mWaitForDialog = false;
                } else if (!isItemSoldOut(aItemType) && !isItemUnavailable(aItemType)
                        && !isComingSoon(aItemType)) {
                    purchaseItem(aItemType);
                }
                break;
            }
        }
    }

    // StoreScreen::EnableButtons (StoreScreen.cpp:1166)
    public void enableButtons(boolean theEnable) {
        if (mEasyBuyingCheat || isPageShown(StorePages.STORE_PAGE_PLANT_UPGRADES) || !theEnable) {
            mNextButton.mMouseVisible = theEnable;
            mNextButton.setDisabled(!theEnable);
            mPrevButton.mMouseVisible = theEnable;
            mPrevButton.setDisabled(!theEnable);
        }
        mBackButton.mMouseVisible = theEnable;
        mBackButton.setDisabled(!theEnable);
    }

    // StoreScreen::SetupForIntro (StoreScreen.cpp:1179)
    public void setupForIntro(int theDialogIndex) {
        mStartDialog = theDialogIndex;
        mHatchOpen = false;
        mBackButton.mLabel = PvzpStringFile.translate("[STORE_NEXT_LEVEL_BUTTON]");
        enableButtons(false);
    }
}
