/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.CrazyDaveState;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.StoreItem;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless tests for {@link StoreScreen}: the item-cost table, availability rules and the purchase
 * effect table (insufficient funds / already owned / success paths mutating the profile).
 *
 * <p>Every app is initialised with {@link HeadlessTestApp} (profile, effect system, foley/music
 * doubles, a stub image/font for every resource id), so no test needs the retail store art and every
 * assertion runs unconditionally.
 */
class StoreScreenTest {

    /** Avoids filesystem writes from the purchase path (LawnApp.writeCurrentUserConfig). */
    private static final class TestLawnApp extends LawnApp {
        /** How often the purchase path recorded the profile (LawnApp::WriteCurrentUserConfig). */
        int mWriteConfigCount;

        @Override
        public boolean writeCurrentUserConfig() {
            mWriteConfigCount++;
            return true;
        }
    }

    /** A headless app: profile, effect system, foley/music doubles and stub images/fonts. */
    private static TestLawnApp newApp() {
        TestLawnApp app = new TestLawnApp();
        HeadlessTestApp.initialize(app);
        return app;
    }

    /**
     * Gives the app a live Crazy Dave reanimation so {@code LawnApp::CrazyDaveStopTalking}
     * (LawnApp.cpp:2782-2811) has a reanimation to override ({@code SetImageOverride("Dave_mouths",
     * nullptr)}) — the upstream call {@code StoreScreen::PurchaseItem} makes before showing the
     * "not enough money" dialog. Retail .reanim data parses to zero tracks, so the reanimation gets
     * the per-track state {@link HeadlessTestApp#stubLoadedTracks} supplies.
     */
    private static void giveCrazyDave(TestLawnApp app) {
        Reanimation aDave = app.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_CRAZY_DAVE);
        HeadlessTestApp.stubLoadedTracks(aDave);
        app.mCrazyDaveReanimID = app.reanimationGetID(aDave);
        app.mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
    }

    private static StoreScreen newStore(TestLawnApp app) {
        return new StoreScreen(app);
    }

    // -----------------------------------------------------------------------------------------
    // Asset-free static tables
    // -----------------------------------------------------------------------------------------

    @Test
    void storePositionTableMatchesUpstream() {
        assertArrayEquals(new int[] {422, 206}, StoreScreen.getStorePosition(0));
        assertArrayEquals(new int[] {644, 206}, StoreScreen.getStorePosition(3));
        assertArrayEquals(new int[] {372, 310}, StoreScreen.getStorePosition(4));
        assertArrayEquals(new int[] {594, 310}, StoreScreen.getStorePosition(7));
    }

    @Test
    void onlyMarigoldSlotsArePottedPlants() {
        assertTrue(StoreScreen.isPottedPlant(StoreItem.STORE_ITEM_POTTED_MARIGOLD_1));
        assertTrue(StoreScreen.isPottedPlant(StoreItem.STORE_ITEM_POTTED_MARIGOLD_3));
        assertFalse(StoreScreen.isPottedPlant(StoreItem.STORE_ITEM_TREE_OF_WISDOM));
        assertFalse(StoreScreen.isPottedPlant(StoreItem.STORE_ITEM_INVALID));
    }

    @Test
    void packetUpgradeCostLadder() {
        TestLawnApp app = newApp();
        int aPacket = StoreItem.STORE_ITEM_PACKET_UPGRADE;

        app.mPlayerInfo.mPurchases[aPacket] = 0;
        assertEquals(75, StoreScreen.getItemCost(aPacket));
        app.mPlayerInfo.mPurchases[aPacket] = 1;
        assertEquals(500, StoreScreen.getItemCost(aPacket));
        app.mPlayerInfo.mPurchases[aPacket] = 2;
        assertEquals(2000, StoreScreen.getItemCost(aPacket));
        app.mPlayerInfo.mPurchases[aPacket] = 3;
        assertEquals(8000, StoreScreen.getItemCost(aPacket));
    }

    @Test
    void bonusLawnMowerCostTwice() {
        TestLawnApp app = newApp();
        int aMower = StoreItem.STORE_ITEM_BONUS_LAWN_MOWER;

        app.mPlayerInfo.mPurchases[aMower] = 0;
        assertEquals(200, StoreScreen.getItemCost(aMower));
        app.mPlayerInfo.mPurchases[aMower] = 1;
        assertEquals(500, StoreScreen.getItemCost(aMower));
    }

    @Test
    void fixedItemCostsMatchTheSpecTable() {
        newApp();
        assertEquals(20, StoreScreen.getItemCost(StoreItem.STORE_ITEM_RAKE));
        assertEquals(75, StoreScreen.getItemCost(StoreItem.STORE_ITEM_FERTILIZER));
        assertEquals(100, StoreScreen.getItemCost(StoreItem.STORE_ITEM_POOL_CLEANER));
        assertEquals(100, StoreScreen.getItemCost(StoreItem.STORE_ITEM_GARDENING_GLOVE));
        assertEquals(100, StoreScreen.getItemCost(StoreItem.STORE_ITEM_BUG_SPRAY));
        assertEquals(200, StoreScreen.getItemCost(StoreItem.STORE_ITEM_FIRSTAID));
        assertEquals(250, StoreScreen.getItemCost(StoreItem.STORE_ITEM_TREE_FOOD));
        assertEquals(300, StoreScreen.getItemCost(StoreItem.STORE_ITEM_ROOF_CLEANER));
        assertEquals(300, StoreScreen.getItemCost(StoreItem.STORE_ITEM_STINKY_THE_SNAIL));
        assertEquals(500, StoreScreen.getItemCost(StoreItem.STORE_ITEM_PLANT_GATLINGPEA));
        assertEquals(750, StoreScreen.getItemCost(StoreItem.STORE_ITEM_PLANT_GLOOMSHROOM));
        assertEquals(1000, StoreScreen.getItemCost(StoreItem.STORE_ITEM_PLANT_CATTAIL));
        assertEquals(2000, StoreScreen.getItemCost(StoreItem.STORE_ITEM_PLANT_COBCANNON));
        assertEquals(3000, StoreScreen.getItemCost(StoreItem.STORE_ITEM_PLANT_IMITATER));
        assertEquals(3000, StoreScreen.getItemCost(StoreItem.STORE_ITEM_MUSHROOM_GARDEN));
    }

    // -----------------------------------------------------------------------------------------
    // Availability (needs a constructed screen for the instance rules)
    // -----------------------------------------------------------------------------------------

    @Test
    void soldOutRules() {
        TestLawnApp app = newApp();
        StoreScreen aStore = newStore(app);
        PlayerInfo aPlayer = app.mPlayerInfo;

        aPlayer.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 3;
        assertFalse(aStore.isItemSoldOut(StoreItem.STORE_ITEM_PACKET_UPGRADE));
        aPlayer.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 4;
        assertTrue(aStore.isItemSoldOut(StoreItem.STORE_ITEM_PACKET_UPGRADE));

        aPlayer.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA] = 1;
        assertTrue(aStore.isItemSoldOut(StoreItem.STORE_ITEM_PLANT_GATLINGPEA));
        aPlayer.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA] = 0;
        assertFalse(aStore.isItemSoldOut(StoreItem.STORE_ITEM_PLANT_GATLINGPEA));

        aPlayer.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] =
                PlayerInfo.PURCHASE_COUNT_OFFSET + 16;
        assertTrue(aStore.isItemSoldOut(StoreItem.STORE_ITEM_FERTILIZER));

        aPlayer.mPurchases[StoreItem.STORE_ITEM_BONUS_LAWN_MOWER] = 2;
        assertTrue(aStore.isItemSoldOut(StoreItem.STORE_ITEM_BONUS_LAWN_MOWER));

        assertFalse(aStore.isItemSoldOut(StoreItem.STORE_ITEM_INVALID));
    }

    @Test
    void trialLocksFullVersionItems() {
        TestLawnApp app = newApp();
        app.mDebugTrialLocked = true;
        StoreScreen aStore = newStore(app);

        assertTrue(aStore.isFullVersionOnly(StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER));
        assertTrue(aStore.isComingSoon(StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER));
        assertTrue(aStore.isPageShown(cn.enaium.fabricpvz.portable.lawn.StorePages.STORE_PAGE_SLOT_UPGRADES));
        assertFalse(aStore.isPageShown(cn.enaium.fabricpvz.portable.lawn.StorePages.STORE_PAGE_PLANT_UPGRADES));

        // packet upgrade becomes full-version-only after two purchases
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 2;
        assertTrue(aStore.isFullVersionOnly(StoreItem.STORE_ITEM_PACKET_UPGRADE));
    }

    @Test
    void affordabilityUsesTheCoinBalance() {
        TestLawnApp app = newApp();
        StoreScreen aStore = newStore(app);

        app.mPlayerInfo.mCoins = 19;
        assertFalse(aStore.canAffordItem(StoreItem.STORE_ITEM_RAKE), "rake costs 20");
        app.mPlayerInfo.mCoins = 20;
        assertTrue(aStore.canAffordItem(StoreItem.STORE_ITEM_RAKE));
    }

    // -----------------------------------------------------------------------------------------
    // Purchase effect table
    // -----------------------------------------------------------------------------------------

    @Test
    void insufficientFundsLeavesTheProfileUnchanged() {
        TestLawnApp app = newApp();
        giveCrazyDave(app);
        StoreScreen aStore = newStore(app);
        app.mPlayerInfo.mCoins = 0;
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_RAKE] = 0;

        aStore.purchaseItem(StoreItem.STORE_ITEM_RAKE);

        assertEquals(0, app.mPlayerInfo.mCoins);
        assertEquals(0, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_RAKE],
                "nothing is bought without enough coins");
    }

    @Test
    void alreadyOwnedItemIsNotBuyable() {
        TestLawnApp app = newApp();
        StoreScreen aStore = newStore(app);
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 4;

        assertTrue(aStore.isItemSoldOut(StoreItem.STORE_ITEM_PACKET_UPGRADE));
    }

    @Test
    void successfulUpgradePurchasesMutateTheProfile() {
        TestLawnApp app = newApp();
        StoreScreen aStore = newStore(app);
        PlayerInfo aPlayer = app.mPlayerInfo;

        // RAKE: cost 20, sets purchases to 3 (a fresh rake for the next three levels)
        aPlayer.mCoins = 1000;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_RAKE] = 0;
        aStore.applyPurchase(StoreItem.STORE_ITEM_RAKE);
        assertEquals(980, aPlayer.mCoins);
        assertEquals(3, aPlayer.mPurchases[StoreItem.STORE_ITEM_RAKE]);

        // BONUS_LAWN_MOWER: cost 200, purchases++
        aPlayer.mCoins = 1000;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_BONUS_LAWN_MOWER] = 0;
        aStore.applyPurchase(StoreItem.STORE_ITEM_BONUS_LAWN_MOWER);
        assertEquals(800, aPlayer.mCoins);
        assertEquals(1, aPlayer.mPurchases[StoreItem.STORE_ITEM_BONUS_LAWN_MOWER]);

        // PACKET_UPGRADE: cost 75, purchases++ (6+purchases seed slots)
        aPlayer.mCoins = 1000;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 0;
        aStore.applyPurchase(StoreItem.STORE_ITEM_PACKET_UPGRADE);
        assertEquals(925, aPlayer.mCoins);
        assertEquals(1, aPlayer.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);

        // 3rd packet upgrade costs 2000
        aPlayer.mCoins = 3000;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 2;
        aStore.applyPurchase(StoreItem.STORE_ITEM_PACKET_UPGRADE);
        assertEquals(1000, aPlayer.mCoins);
        assertEquals(3, aPlayer.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);

        // FERTILIZER: cost 75, +5 charges above PURCHASE_COUNT_OFFSET
        aPlayer.mCoins = 1000;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = 0;
        aStore.applyPurchase(StoreItem.STORE_ITEM_FERTILIZER);
        assertEquals(925, aPlayer.mCoins);
        assertEquals(PlayerInfo.PURCHASE_COUNT_OFFSET + 5,
                aPlayer.mPurchases[StoreItem.STORE_ITEM_FERTILIZER]);

        // TREE_FOOD: cost 250, +1 charge above PURCHASE_COUNT_OFFSET
        aPlayer.mCoins = 1000;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] = 0;
        aStore.applyPurchase(StoreItem.STORE_ITEM_TREE_FOOD);
        assertEquals(750, aPlayer.mCoins);
        assertEquals(PlayerInfo.PURCHASE_COUNT_OFFSET + 1,
                aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD]);

        // a plant: cost 500, purchases = 1 (adventure upgrade)
        aPlayer.mCoins = 1000;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA] = 0;
        aStore.applyPurchase(StoreItem.STORE_ITEM_PLANT_GATLINGPEA);
        assertEquals(500, aPlayer.mCoins);
        assertEquals(1, aPlayer.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA]);
    }

    @Test
    void treeOfWisdomPurchaseRecordsTheChallenge() {
        TestLawnApp app = newApp();
        StoreScreen aStore = newStore(app);
        PlayerInfo aPlayer = app.mPlayerInfo;
        aPlayer.mCoins = 1500;
        aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] = 0;

        aStore.applyPurchase(StoreItem.STORE_ITEM_TREE_OF_WISDOM);

        assertEquals(500, aPlayer.mCoins);
        assertEquals(1, aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM]);
        assertEquals(1, aPlayer.mChallengeRecords[cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_TREE_OF_WISDOM
                - cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1]);
    }

    @Test
    void stinkyPurchaseStoresTheCurrentDay() {
        TestLawnApp app = newApp();
        StoreScreen aStore = newStore(app);
        app.mPlayerInfo.mCoins = 1000;
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] = 0;

        aStore.applyPurchase(StoreItem.STORE_ITEM_STINKY_THE_SNAIL);

        assertEquals(700, app.mPlayerInfo.mCoins);
        assertTrue(app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_STINKY_THE_SNAIL] != 0,
                "the snail stores a purchase time (never 0)");
    }

    // -----------------------------------------------------------------------------------------
    // The "Buy this item?" confirmation (StoreScreen.cpp:1009-1020)
    // -----------------------------------------------------------------------------------------

    /**
     * The reported bug: confirming the seed-slot upgrade did nothing, because
     * {@code Dialog::WaitForResult} blocks upstream (Dialog.cpp:410-441) while this port's does not
     * (sexy/Dialog.java:506) — the result was read before the player could click, so no purchase was
     * ever applied. The answer is now applied when the dialog's button is depressed
     * (LawnApp.buttonDepress → {@link StoreScreen#finishPurchaseConfirmation}).
     */
    @Test
    void confirmingTheSlotUpgradeGivesTheSeventhSeedSlot() {
        TestLawnApp app = newApp();
        app.mGameMode = cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_ADVENTURE;
        app.mPlayerInfo.mLevel = 14; // 13 levels won: 12 seeds unlocked, so a 7th slot fits
        app.mPlayerInfo.mFinishedAdventure = 0;
        app.mPlayerInfo.mCoins = 750;
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 0;

        Board aBoard = new Board(app);
        aBoard.mSeedBank.updateWidth();
        assertEquals(6, aBoard.mSeedBank.mNumPackets, "the bank starts with six slots");

        // The board frees every reanimation when it is built (Board.cpp:238), so Dave comes after it.
        giveCrazyDave(app);

        StoreScreen aStore = app.showStoreScreen();
        aStore.purchaseItem(StoreItem.STORE_ITEM_PACKET_UPGRADE);

        LawnDialog aConfirm = (LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE);
        assertNotNull(aConfirm, "the store asks for confirmation before charging");
        assertEquals(750, app.mPlayerInfo.mCoins, "nothing is charged before the answer");
        assertEquals(6, aBoard.mSeedBank.mNumPackets, "nothing is applied before the answer");

        clickDialogButton(app, aConfirm, aConfirm.mLawnYesButton);

        assertEquals(675, app.mPlayerInfo.mCoins, "the first slot upgrade costs 75 coins");
        assertEquals(1, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);
        assertEquals(1, app.mWriteConfigCount, "the profile records the purchase on disk");
        assertEquals(7, aBoard.getNumSeedsInBank());
        assertEquals(7, aBoard.mSeedBank.mNumPackets, "the bought slot is usable right away");
        assertNull(app.getDialog(Dialogs.DIALOG_STORE_PURCHASE), "the confirmation is dismissed");
        assertFalse(aStore.mWaitForDialog);
    }

    @Test
    void decliningTheSlotUpgradeCostsNothing() {
        TestLawnApp app = newApp();
        app.mGameMode = cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_ADVENTURE;
        app.mPlayerInfo.mLevel = 14;
        app.mPlayerInfo.mFinishedAdventure = 0;
        app.mPlayerInfo.mCoins = 750;
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 0;

        Board aBoard = new Board(app);
        aBoard.mSeedBank.updateWidth();
        giveCrazyDave(app);

        StoreScreen aStore = app.showStoreScreen();
        aStore.purchaseItem(StoreItem.STORE_ITEM_PACKET_UPGRADE);
        LawnDialog aConfirm = (LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE);

        clickDialogButton(app, aConfirm, aConfirm.mLawnNoButton);

        assertEquals(750, app.mPlayerInfo.mCoins, "declining buys nothing");
        assertEquals(0, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);
        assertEquals(6, aBoard.mSeedBank.mNumPackets);
        assertNull(app.getDialog(Dialogs.DIALOG_STORE_PURCHASE));
        assertFalse(aStore.mWaitForDialog);
    }

    /**
     * The upgrade ladder: every purchase adds one slot — 6 → 7 → 8 → 9 → 10, and at
     * {@link GameConstants#SEEDBANK_MAX} the store stops offering it (StoreScreen.cpp:909-913 cost
     * steps, Board.cpp:8620 slot count, StoreScreen.cpp:215 sold out at four purchases).
     */
    @Test
    void eachSlotUpgradeStepAddsOneSlotUpToTheBankMaximum() {
        TestLawnApp app = null;
        Board aBoard = null;
        for (int aPurchase = 0; aPurchase < 4; aPurchase++) {
            app = newApp();
            app.mGameMode = cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_ADVENTURE;
            app.mPlayerInfo.mLevel = 20; // 16 seeds unlocked, so every step still fits
            app.mPlayerInfo.mFinishedAdventure = 0;
            app.mPlayerInfo.mCoins = 20000;
            app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = aPurchase;

            aBoard = new Board(app);
            aBoard.mSeedBank.updateWidth();
            assertEquals(6 + aPurchase, aBoard.mSeedBank.mNumPackets, "slots before step " + (aPurchase + 1));
            giveCrazyDave(app);

            StoreScreen aStore = app.showStoreScreen();
            assertTrue(aStore.canAffordItem(StoreItem.STORE_ITEM_PACKET_UPGRADE));
            int aCost = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);
            aStore.purchaseItem(StoreItem.STORE_ITEM_PACKET_UPGRADE);

            LawnDialog aConfirm = (LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE);
            clickDialogButton(app, aConfirm, aConfirm.mLawnYesButton);

            assertEquals(20000 - aCost, app.mPlayerInfo.mCoins, "cost of step " + (aPurchase + 1));
            assertEquals(aPurchase + 1, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);
            assertEquals(7 + aPurchase, aBoard.mSeedBank.mNumPackets,
                    "step " + (aPurchase + 1) + " grants the next slot");
        }

        // Four purchases is the end of the ladder: the bank is full and the item is sold out.
        assertEquals(10, GameConstants.SEEDBANK_MAX);
        assertEquals(4, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);
        assertEquals(GameConstants.SEEDBANK_MAX, aBoard.mSeedBank.mNumPackets);
        assertTrue(newStore(app).isItemSoldOut(StoreItem.STORE_ITEM_PACKET_UPGRADE));
    }

    /**
     * The player must be able to leave the store. Upstream relies on {@code WaitForResult(autoKill)}
     * to remove the screen (Dialog.cpp:410-441); this port's wait does not pump (sexy/Dialog.java:506),
     * so {@code StoreScreen.buttonDepress(StoreScreen_Back)} kills it itself.
     */
    @Test
    void theStoreScreenClosesWhenThePlayerLeaves() {
        TestLawnApp app = newApp();
        StoreScreen aStore = app.showStoreScreen();
        assertNotNull(app.getDialog(Dialogs.DIALOG_STORE), "the store is open");

        clickDialogButton(app, aStore, aStore.mBackButton);

        assertNull(app.getDialog(Dialogs.DIALOG_STORE), "Back must let the player leave the store");
        assertEquals(1000, aStore.mResult, "the caller sees the store's \"close\" result");
    }

    /**
     * The store's own answer can close the screen: choosing "yes, visit the Tree of Wisdom now"
     * (StoreScreen.cpp:1060-1068) stores {@code ID_OK} as the <em>store's</em> result, which upstream
     * makes the caller's outer {@code WaitForResult(true)} stop pumping and autoKill the screen
     * (Dialog.cpp:410-441). Nothing pumps here, so the answer closes the store itself — and the
     * caller's transition (registered with {@link StoreScreen#onStoreClosed}) has to see the flag.
     */
    @Test
    void acceptingTheTreeVisitClosesTheStoreWithTheFlagSet() {
        TestLawnApp app = newApp();
        app.mPlayerInfo.mCoins = 1500;
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] = 0;
        giveCrazyDave(app);

        StoreScreen aStore = app.showStoreScreen();
        boolean[] aTransitionSawTreeFlag = new boolean[1];
        aStore.onStoreClosed(store -> aTransitionSawTreeFlag[0] = store.mGoToTreeNow);

        aStore.purchaseItem(StoreItem.STORE_ITEM_TREE_OF_WISDOM);
        ((LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE)).buttonDepress(Dialog.ID_YES);

        // The visit prompt replaces the purchase prompt on the same dialog id (StoreScreen.cpp:1060-1064).
        LawnDialog aVisit = (LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE);
        assertNotNull(aVisit, "the visit prompt is up");
        assertEquals(1, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM],
                "buying the tree is applied before the visit prompt");
        assertNotNull(app.getDialog(Dialogs.DIALOG_STORE), "the store stays up until the visit is answered");
        assertFalse(aStore.mGoToTreeNow, "the flag is only set by a yes");

        aVisit.buttonDepress(Dialog.ID_YES);

        assertNull(app.getDialog(Dialogs.DIALOG_STORE), "the answer closes the store, the way autoKill does");
        assertTrue(aStore.mGoToTreeNow);
        assertEquals(Dialog.ID_OK, aStore.mResult,
                "StoreScreen.cpp:1033-1034 — the answer becomes the store's result");
        assertTrue(aTransitionSawTreeFlag[0], "the caller's transition sees the flag on the closed store");
    }

    /** Declining the Tree of Wisdom keeps the player shopping (upstream's inner wait returns ID_NO). */
    @Test
    void decliningTheTreeVisitLeavesTheStoreOpen() {
        TestLawnApp app = newApp();
        app.mPlayerInfo.mCoins = 1500;
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] = 0;
        giveCrazyDave(app);

        StoreScreen aStore = app.showStoreScreen();
        boolean[] aTransitionRan = new boolean[1];
        aStore.onStoreClosed(store -> aTransitionRan[0] = true);

        aStore.purchaseItem(StoreItem.STORE_ITEM_TREE_OF_WISDOM);
        ((LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE)).buttonDepress(Dialog.ID_YES);
        ((LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE)).buttonDepress(Dialog.ID_NO);

        assertNotNull(app.getDialog(Dialogs.DIALOG_STORE), "the store is still open");
        assertFalse(aStore.mGoToTreeNow);
        assertFalse(aStore.mWaitForDialog, "the store accepts input again");
        assertFalse(aTransitionRan[0], "nothing on the way out runs while the store is up");
    }

    /**
     * Upstream's callers sit after {@code StoreScreen::WaitForResult(true)}, so they run with the
     * screen already removed (Dialog.cpp:410-441). The port's close path has to deliver the same
     * ordering, or a caller that reads {@code mGoToTreeNow}/{@code mPurchasedFullVersion} would read
     * a store the player can still see.
     */
    @Test
    void theStoreTransitionRunsOnlyAfterTheScreenIsGone() {
        TestLawnApp app = newApp();
        StoreScreen aStore = app.showStoreScreen();
        boolean[] aStoreStillOpen = new boolean[1];
        aStore.onStoreClosed(store -> aStoreStillOpen[0] = app.getDialog(Dialogs.DIALOG_STORE) != null);

        clickDialogButton(app, aStore, aStore.mBackButton);

        assertNull(app.getDialog(Dialogs.DIALOG_STORE));
        assertFalse(aStoreStillOpen[0], "the transition ran with the store already removed");
    }

    /**
     * Depresses a dialog button the way the mouse does — hit test, mouse down, mouse up — so the
     * click travels {@code WidgetManager → LawnDialog → LawnApp.buttonDepress} like a real one.
     */
    private static void clickDialogButton(TestLawnApp app, cn.enaium.fabricpvz.portable.sexy.Dialog theDialog,
            cn.enaium.fabricpvz.portable.sexy.ButtonWidget theButton) {
        int aX = theDialog.mX + theButton.mX + theButton.mWidth / 2;
        int aY = theDialog.mY + theButton.mY + theButton.mHeight / 2;
        app.mWidgetManager.mouseDown(aX, aY, 1);
        app.mWidgetManager.mouseUp(aX, aY, 1);
    }
}
