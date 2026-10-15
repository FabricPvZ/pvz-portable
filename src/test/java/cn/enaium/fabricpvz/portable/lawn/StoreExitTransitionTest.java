/*
 * The callers that run a transition on the way out of the store screen.
 *
 * Upstream every one of them sits *after* `StoreScreen::WaitForResult(true)`, which only returns once
 * `Dialog::WaitForResult`'s autoKill has removed the screen (Dialog.cpp:410-441); the port's
 * `waitForResult` does not pump (sexy/Dialog.java:506), so reading `mGoToTreeNow` /
 * `mPurchasedFullVersion` or restoring the music right there ran while the store was still open —
 * before the player could even answer it. The transitions are now registered on the screen
 * (`StoreScreen.onStoreClosed`) and run by `LawnApp.killStoreScreen`, the port's "the store closed"
 * point.
 *
 * Each test drives the same way the game does: the real `GameSelector`/`AwardScreen`/`SeedChooser`
 * entry point, the real store, and the real widget-manager click on its Back button.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.system.Music;
import cn.enaium.fabricpvz.portable.lawn.widget.AwardScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.GameSelector;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

class StoreExitTransitionTest {

    /**
     * A headless app that records the transitions the store's callers run on the way out. The store
     * itself is the real one: these tests only replace the modules that would otherwise start a
     * level, draw a screen or write the profile.
     */
    static final class RecordingLawnApp extends LawnApp {
        final List<Integer> mPreNewGames = new ArrayList<>();
        int mKillBoardCount;
        int mKillAwardScreenCount;
        int mShowGameSelectorCount;
        int mKillGameSelectorCount;
        final RecordingMusic mRecordingMusic = new RecordingMusic();

        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            // Without retail .reanim data every definition parses to zero tracks
            // (Reanimator.cpp:397-421) while the selector's SyncButtons indexes them by name
            // (Reanimator.cpp:974-977); the shared fixture fakes the loaded track table.
            HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }

        /**
         * Records the transition and switches to that mode's board, which is what
         * {@code LawnApp::PreNewGame} does synchronously through {@code NewGame} →
         * {@code MakeNewBoard} (LawnApp.java:564-651). The level itself is never started, but the
         * board has to exist: the upsell transition pokes its store button
         * (AwardScreen.cpp:545-546), and {@code Board::Board} only creates one for the upsell/zen
         * modes (Board.cpp:197-234).
         */
        @Override
        public void preNewGame(int theGameMode, boolean theLookForSavedGame) {
            mPreNewGames.add(theGameMode);
            mGameMode = theGameMode;
            mBoard = new Board(this);
        }

        @Override
        public void killBoard() {
            mKillBoardCount++;
        }

        @Override
        public void killAwardScreen() {
            mKillAwardScreenCount++;
        }

        @Override
        public void showGameSelector() {
            mShowGameSelectorCount++;
        }

        @Override
        public void killGameSelector() {
            mKillGameSelectorCount++;
        }

        @Override
        public boolean writeCurrentUserConfig() {
            return true;
        }
    }

    /** A {@link Music} that records the tunes the callers restore (it never opens an audio device). */
    static final class RecordingMusic extends Music {
        final List<Integer> mTunes = new ArrayList<>();

        @Override
        public void makeSureMusicIsPlaying(int theMusicTune) {
            mTunes.add(theMusicTune);
        }

        @Override
        public void stopAllMusic() {
        }

        @Override
        public void fadeOut(int theFadeOutDuration) {
        }

        @Override
        public void startGameMusic() {
        }

        @Override
        public void gameMusicPause(boolean thePause) {
        }
    }

    /** A headless app that records transitions and whose music calls are recorded, not played. */
    private static RecordingLawnApp newApp(int theGameMode) {
        RecordingLawnApp app = new RecordingLawnApp();
        HeadlessTestApp.initialize(app);
        app.mMusic = app.mRecordingMusic;
        app.mGameMode = theGameMode;
        app.mGameScene = GameScenes.SCENE_PLAYING;
        return app;
    }

    /** The selector, wired to the app the way {@code LawnApp::ShowGameSelector} wires it. */
    private static GameSelector newSelector(RecordingLawnApp app) {
        GameSelector aSelector = new GameSelector(app);
        app.mGameSelector = aSelector;
        return aSelector;
    }

    /** The store screen currently open, or null. */
    private static StoreScreen storeOf(LawnApp app) {
        return (StoreScreen) app.getDialog(Dialogs.DIALOG_STORE);
    }

    /** Depresses a dialog button the way the mouse does — hit test, mouse down, mouse up. */
    private static void clickButton(LawnApp app, Dialog theDialog, ButtonWidget theButton) {
        int aX = theDialog.mX + theButton.mX + theButton.mWidth / 2;
        int aY = theDialog.mY + theButton.mY + theButton.mHeight / 2;
        app.mWidgetManager.mouseDown(aX, aY, 1);
        app.mWidgetManager.mouseUp(aX, aY, 1);
    }

    /**
     * Lets the player leave a store that was opened with {@code SetupForIntro}
     * (StoreScreen.cpp:1179-1184): the intro holds every button disabled until Crazy Dave is through
     * ({@code AdvanceCrazyDaveDialog} → {@code EnableButtons(true)}, StoreScreen.cpp:1094-1105),
     * which is the state these tests need to click Back.
     */
    private static void letThePlayerLeave(StoreScreen theStore) {
        theStore.enableButtons(true);
    }

    // -----------------------------------------------------------------------------------------
    // GameSelector: the store on the adventure map (GameSelector.cpp:1229-1237)
    // -----------------------------------------------------------------------------------------

    @Test
    void theMenuStoreRestoresTheMenuMusicOnlyAfterTheStoreCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        GameSelector aSelector = newSelector(app);
        // The selector's own constructor starts the map music (GameSelector.cpp:399); only the
        // store's exit is under test here.
        app.mRecordingMusic.mTunes.clear();

        aSelector.buttonDepress(GameSelector.GameSelector_Store);

        StoreScreen aStore = storeOf(app);
        assertNotNull(aStore, "the map's shop button opens the store");
        assertTrue(app.mRecordingMusic.mTunes.isEmpty(),
                "GameSelector.cpp:1235 restores the music after the wait returned, not while the store is up");

        clickButton(app, aStore, aStore.mBackButton);

        assertNull(storeOf(app), "Back closes the store");
        assertEquals(List.of(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME), app.mRecordingMusic.mTunes,
                "the map's music comes back once the store is gone");
        assertEquals(List.of(), app.mPreNewGames, "no level is started");
    }

    @Test
    void theMenuStoreHandsOverToTheTreeOfWisdomOnlyAfterTheStoreCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        app.mPlayerInfo.mCoins = 1500;
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] = 0;
        GameSelector aSelector = newSelector(app);
        app.mRecordingMusic.mTunes.clear();

        aSelector.buttonDepress(GameSelector.GameSelector_Store);
        StoreScreen aStore = storeOf(app);
        giveCrazyDave(app);
        aStore.purchaseItem(StoreItem.STORE_ITEM_TREE_OF_WISDOM);

        // StoreScreen.cpp:1014 — the "buy this item?" sheet, then :1060-1068 — "visit now?".
        ((LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE)).buttonDepress(Dialog.ID_YES);
        assertNotNull(storeOf(app),
                "StoreScreen.cpp:1066-1068 — buying the tree keeps the store up until the visit answer");
        ((LawnDialog) app.getDialog(Dialogs.DIALOG_STORE_PURCHASE)).buttonDepress(Dialog.ID_YES);

        assertNull(storeOf(app), "the tree answer closes the store (upstream's outer autoKill)");
        assertTrue(aStore.mGoToTreeNow, "mGoToTreeNow is set on the store");
        assertEquals(List.of(GameMode.GAMEMODE_TREE_OF_WISDOM), app.mPreNewGames,
                "the map hands over to the tree only after the store closed");
        assertEquals(1, app.mKillGameSelectorCount);
        assertTrue(app.mRecordingMusic.mTunes.isEmpty(), "the tree trip does not restore the map music");
    }

    @Test
    void theMapsStoreIntroStartsTheLevelOnlyAfterTheStoreCloses() {
        // GameSelector.cpp:778-781 — the magic-taco reward store in the "starting game" countdown.
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        app.mPlayerInfo.mNeedsMagicTacoReward = 1;
        GameSelector aSelector = newSelector(app);
        aSelector.mLevel = 35;
        aSelector.mStartingGame = true;
        aSelector.mStartingGameCounter = 450;

        aSelector.update();

        StoreScreen aStore = storeOf(app);
        assertNotNull(aStore, "the taco reward shop opens");
        assertEquals(601, aStore.mStartDialog, "GameSelector.cpp:779 — SetupForIntro(601)");
        assertEquals(List.of(), app.mPreNewGames, "the level waits for the store to close");

        letThePlayerLeave(aStore);
        clickButton(app, aStore, aStore.mBackButton);

        assertEquals(List.of(GameMode.GAMEMODE_ADVENTURE), app.mPreNewGames,
                "GameSelector.cpp:781 — PreNewGame(adventure) after the wait");
    }

    // -----------------------------------------------------------------------------------------
    // AwardScreen: the level-25/35/42 intro stores (AwardScreen.cpp:526-559)
    // -----------------------------------------------------------------------------------------

    /** The reward screen of a level, wired like {@code LawnApp::ShowAwardScreen}. */
    private static AwardScreen newAward(RecordingLawnApp app, int theLevel) {
        app.mPlayerInfo.mLevel = theLevel;
        AwardScreen aAward = new AwardScreen(app, AwardType.AWARD_FORLEVEL, false);
        app.mAwardScreen = aAward;
        return aAward;
    }

    @Test
    void theLevelTwentyFiveAwardLeavesThroughItsStoreAndThenStartsTheNextLevel() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        AwardScreen aAward = newAward(app, 25);

        aAward.startButtonPressed();

        StoreScreen aStore = storeOf(app);
        assertNotNull(aStore, "AwardScreen.cpp:527 — the level-25 store");
        assertEquals(301, aStore.mStartDialog, "AwardScreen.cpp:528 — SetupForIntro(301)");
        assertEquals(List.of(), app.mPreNewGames,
                "AwardScreen.cpp:530 waits for the store; the level may not start under it");
        assertEquals(0, app.mKillAwardScreenCount);

        letThePlayerLeave(aStore);
        clickButton(app, aStore, aStore.mBackButton);

        assertEquals(1, app.mKillAwardScreenCount);
        assertEquals(List.of(GameMode.GAMEMODE_ADVENTURE), app.mPreNewGames,
                "AwardScreen.cpp:568-570 — the fall-through after the store");
        assertEquals(0, app.mShowGameSelectorCount);
    }

    @Test
    void theLevelTwentyFiveAwardShowsTheSelectorWhenTheFullVersionWasBought() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        AwardScreen aAward = newAward(app, 25);

        aAward.startButtonPressed();
        StoreScreen aStore = storeOf(app);
        // The store raises this when the player buys the full version (StoreScreen.cpp:783); upstream
        // reads it after the blocking wait, so the branch has to be taken when the store closes.
        aStore.mPurchasedFullVersion = true;

        letThePlayerLeave(aStore);
        clickButton(app, aStore, aStore.mBackButton);

        assertEquals(1, app.mKillAwardScreenCount);
        assertEquals(1, app.mShowGameSelectorCount, "AwardScreen.cpp:533-536");
        assertEquals(List.of(), app.mPreNewGames, "the store purchase replaces the level start");
    }

    @Test
    void theLevelTwentyFiveAwardGoesToTheUpsellWhenTheTrialStageLocksTheRest() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        app.mDebugTrialLocked = true;
        Board aBoard = new Board(app);
        aBoard.mLevel = 25;
        app.mPlayerInfo.mHasSeenUpsell = 0;
        AwardScreen aAward = newAward(app, 25);

        aAward.startButtonPressed();
        StoreScreen aStore = storeOf(app);
        assertNotNull(aStore);
        assertEquals(List.of(), app.mPreNewGames, "the upsell waits for the store to close");
        assertNull(aBoard.mStoreButton,
                "Board.cpp:197-218 gives an adventure level no store button, so the upsell may only"
                        + " poke the one of the board PreNewGame builds");

        letThePlayerLeave(aStore);
        clickButton(app, aStore, aStore.mBackButton);

        assertEquals(List.of(GameMode.GAMEMODE_UPSELL), app.mPreNewGames, "AwardScreen.cpp:540-543");
        assertEquals(1, app.mKillAwardScreenCount);
        assertEquals(GameMode.GAMEMODE_UPSELL, app.mGameMode);
        assertTrue(app.mBoard.mStoreButton.mBtnNoDraw,
                "AwardScreen.cpp:545-546 — the upsell board's store button is hidden");
        assertEquals(1, app.mPlayerInfo.mHasSeenUpsell);
    }

    @Test
    void theLevelThirtyFiveAndFortyTwoAwardsStartTheNextLevelOnlyAfterTheirStoreCloses() {
        int[] aLevels = {35, 42};
        int[] aIntroDialogs = {601, 3100};

        for (int i = 0; i < aLevels.length; i++) {
            RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
            AwardScreen aAward = newAward(app, aLevels[i]);

            aAward.startButtonPressed();

            StoreScreen aStore = storeOf(app);
            assertNotNull(aStore, "AwardScreen.cpp:551/557 — the level-" + aLevels[i] + " store");
            assertEquals(aIntroDialogs[i], aStore.mStartDialog,
                    "AwardScreen.cpp:552/558 — SetupForIntro for level " + aLevels[i]);
            assertEquals(List.of(), app.mPreNewGames,
                    "level " + aLevels[i] + " must not start while its store is up");

            letThePlayerLeave(aStore);
            clickButton(app, aStore, aStore.mBackButton);

            assertEquals(List.of(GameMode.GAMEMODE_ADVENTURE), app.mPreNewGames,
                    "level " + aLevels[i] + " continues after the store closed");
        }
    }

    // -----------------------------------------------------------------------------------------
    // The seed chooser's shop button (SeedChooserScreen.cpp:740-748)
    // -----------------------------------------------------------------------------------------

    /** The chooser of a fresh headless board, wired like {@code LawnApp::ShowSeedChooserScreen}. */
    private static SeedChooserScreen newChooser(RecordingLawnApp app) {
        SeedChooserScreen aChooser = new SeedChooserScreen();
        app.mSeedChooserScreen = aChooser;
        return aChooser;
    }

    @Test
    void theChoosersShopRestoresTheChooserMusicOnlyAfterTheStoreCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        Board aBoard = new Board(app);
        aBoard.mLevel = 20;
        SeedChooserScreen aChooser = newChooser(app);
        app.mRecordingMusic.mTunes.clear();

        aChooser.buttonDepress(aChooser.mStoreButton.mId);

        StoreScreen aStore = storeOf(app);
        assertNotNull(aStore, "the chooser's shop button opens the store");
        assertEquals(PvzpStringFile.translate("[STORE_BACK_TO_GAME]"), aStore.mBackButton.mLabel,
                "SeedChooserScreen.cpp:741");
        assertTrue(app.mRecordingMusic.mTunes.isEmpty(),
                "SeedChooserScreen.cpp:747 restores the chooser music after the wait");

        clickButton(app, aStore, aStore.mBackButton);

        assertEquals(List.of(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS), app.mRecordingMusic.mTunes,
                "picking your seeds resumes once the store is gone");
    }

    @Test
    void theChoosersShopGoesToTheTreeOfWisdomOnlyAfterTheStoreCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        Board aBoard = new Board(app);
        aBoard.mLevel = 20;
        SeedChooserScreen aChooser = newChooser(app);
        app.mRecordingMusic.mTunes.clear();

        aChooser.buttonDepress(aChooser.mStoreButton.mId);
        StoreScreen aStore = storeOf(app);
        // What the tree's "visit now?" answer sets on the store (StoreScreen.cpp:1066-1068); the
        // purchase chain that raises it is covered by the map's test above and by StoreScreenTest.
        aStore.mGoToTreeNow = true;

        clickButton(app, aStore, aStore.mBackButton);

        assertEquals(1, app.mKillBoardCount, "SeedChooserScreen.cpp:743-745 — the chooser's board goes");
        assertEquals(List.of(GameMode.GAMEMODE_TREE_OF_WISDOM), app.mPreNewGames);
        assertTrue(app.mRecordingMusic.mTunes.isEmpty(), "the tree trip does not restore the chooser music");
    }

    // -----------------------------------------------------------------------------------------
    // ZenGarden::OpenStore (ZenGarden.cpp:2365-2396)
    // -----------------------------------------------------------------------------------------

    @Test
    void theZenGardenStoreRunsItsReEntryOnlyAfterTheStoreCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN);
        Board aBoard = new Board(app);
        aBoard.mLevel = 1;
        aBoard.mTutorialState = TutorialState.TUTORIAL_ZEN_GARDEN_VISIT_STORE;
        ZenGarden aGarden = new ZenGarden();
        aGarden.mBoard = aBoard;
        aGarden.mNowTime = 0;
        app.mZenGarden = aGarden;
        app.mRecordingMusic.mTunes.clear();

        aGarden.openStore();

        StoreScreen aStore = storeOf(app);
        assertNotNull(aStore, "the garden's shop opens");
        assertEquals(StorePages.STORE_PAGE_ZEN1, aStore.mPage, "ZenGarden.cpp:2374");
        assertEquals(0, aGarden.mNowTime, "ZenGarden.cpp:2381 re-reads the clock after the wait");
        assertEquals(TutorialState.TUTORIAL_ZEN_GARDEN_VISIT_STORE, aBoard.mTutorialState,
                "the tutorial waits for the store");
        assertTrue(app.mRecordingMusic.mTunes.isEmpty());

        letThePlayerLeave(aStore);
        clickButton(app, aStore, aStore.mBackButton);

        assertTrue(aGarden.mNowTime != 0, "the garden clock is re-read once the store is gone");
        assertEquals(List.of(MusicTune.MUSIC_TUNE_ZEN_GARDEN), app.mRecordingMusic.mTunes,
                "ZenGarden.cpp:2386 — the garden music resumes");
        assertEquals(TutorialState.TUTORIAL_ZEN_GARDEN_FERTILIZE_PLANTS, aBoard.mTutorialState,
                "ZenGarden.cpp:2387-2391 — the advice and the next tutorial step");
    }

    // -----------------------------------------------------------------------------------------
    // AwardScreen / the almanac buttons: the same shape with `DoAlmanacDialog()->WaitForResult`
    // (AwardScreen.cpp:523-525, GameSelector.cpp:1242-1243, SeedChooserScreen.cpp:735-736, :1029-1030)
    // -----------------------------------------------------------------------------------------

    @Test
    void theLevelFifteenAwardWaitsForItsAlmanac() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        AwardScreen aAward = newAward(app, 15);

        aAward.startButtonPressed();

        assertNotNull(app.getDialog(Dialogs.DIALOG_ALMANAC),
                "AwardScreen.cpp:523 — the almanac shown before the level-15 reward");
        assertEquals(List.of(), app.mPreNewGames, "the level waits for the almanac");
        assertEquals(0, app.mKillAwardScreenCount,
                "starting the level here would close the almanac before the player read it");

        app.killAlmanacDialog();

        assertEquals(1, app.mKillAwardScreenCount);
        assertEquals(List.of(GameMode.GAMEMODE_ADVENTURE), app.mPreNewGames,
                "AwardScreen.cpp:568-570 — the tail runs after the almanac");
    }

    @Test
    void theMapsAlmanacButtonRestoresTheMapMusicOnlyAfterTheAlmanacCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        GameSelector aSelector = newSelector(app);
        app.mRecordingMusic.mTunes.clear();

        aSelector.buttonDepress(GameSelector.GameSelector_Almanac);

        assertNotNull(app.getDialog(Dialogs.DIALOG_ALMANAC), "the map's almanac opens");
        // The almanac plays the seed-chooser tune while it is up (AlmanacDialog.java:157-159); the
        // map's own tune is the one that has to wait for the close (GameSelector.cpp:1243).
        app.mRecordingMusic.mTunes.clear();

        app.killAlmanacDialog();

        assertEquals(List.of(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME), app.mRecordingMusic.mTunes);
    }

    @Test
    void theChoosersAlmanacButtonRestoresTheChooserMusicOnlyAfterTheAlmanacCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE);
        Board aBoard = new Board(app);
        aBoard.mLevel = 20;
        SeedChooserScreen aChooser = newChooser(app);

        aChooser.buttonDepress(aChooser.mAlmanacButton.mId);

        assertNotNull(app.getDialog(Dialogs.DIALOG_ALMANAC), "the chooser's almanac opens");
        app.mRecordingMusic.mTunes.clear();

        app.killAlmanacDialog();

        assertEquals(List.of(MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS), app.mRecordingMusic.mTunes,
                "SeedChooserScreen.cpp:736 — the chooser music is restored after the close");
    }

    // -----------------------------------------------------------------------------------------
    // Challenge::TreeOfWisdomOpenStore (Challenge.cpp:5277-5285)
    // -----------------------------------------------------------------------------------------

    @Test
    void theTreeOfWisdomsStoreRestoresTheGardenMusicOnlyAfterTheStoreCloses() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_TREE_OF_WISDOM);
        Board aBoard = new Board(app);
        aBoard.mLevel = 1;
        ChallengeTreeOfWisdom aTree = new ChallengeTreeOfWisdom(aBoard.mChallenge);
        app.mRecordingMusic.mTunes.clear();

        aTree.treeOfWisdomOpenStore();

        StoreScreen aStore = storeOf(app);
        assertNotNull(aStore, "the tree's shop opens");
        assertEquals(StorePages.STORE_PAGE_ZEN2, aStore.mPage, "Challenge.cpp:5282");
        assertEquals(PvzpStringFile.translate("[STORE_BACK_TO_GAME]"), aStore.mBackButton.mLabel,
                "Challenge.cpp:5281");
        assertTrue(app.mRecordingMusic.mTunes.isEmpty(),
                "Challenge.cpp:5284 restores the music after the wait returned");

        clickButton(app, aStore, aStore.mBackButton);

        assertEquals(List.of(MusicTune.MUSIC_TUNE_ZEN_GARDEN), app.mRecordingMusic.mTunes,
                "the garden music comes back once the store is gone");
    }

    /**
     * A live Crazy Dave reanimation, so the purchase path's {@code CrazyDaveStopTalking}
     * (LawnApp.cpp:2782-2811) has a reanimation for its {@code SetImageOverride("Dave_mouths", null)}.
     */
    private static void giveCrazyDave(LawnApp app) {
        Reanimation aDave = app.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_CRAZY_DAVE);
        app.mCrazyDaveReanimID = app.reanimationGetID(aDave);
        app.mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
    }
}
