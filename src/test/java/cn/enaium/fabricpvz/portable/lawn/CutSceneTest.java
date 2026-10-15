/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.CutScene (ported from src/Lawn/CutScene.cpp).
 *
 * Uses a stub LawnApp (per the project headless convention: LawnApp's constructor does no
 * platform/GL/filesystem work) plus the real Board/EffectSystem. The sequences that need retail
 * art (the seed-bank slide needs IMAGE_SEEDBANK's height) are seeded with a 1x1 MemoryImage in
 * Resources.gResources so the suite stays green without the game assets; anything that would need
 * a real .reanim tolerates the missing file exactly like upstream (log + continue).
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.system.Music;
import cn.enaium.fabricpvz.portable.lawn.system.PoolEffect;
import cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen;
import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorDefinition;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrack;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.WidgetContainer;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

class CutSceneTest {

    /** TimeIntroEnd + mLawnMowerTime(550) + mSodTime(2000) for first-time adventure level 1. */
    private static final int ADVENTURE_L1_INTRO_END = 6000 + 550 + 2000;

    @BeforeAll
    static void loadReanimatorDefinitions() {
        // Seed the (empty) definition array so ReanimatorEnsureDefinitionLoaded can run; with no
        // assets each load fails gracefully (PvzpErrorMessageBox only logs).
        Reanimator.reanimatorLoadDefinitions(Reanimator.gLawnReanimationArray, ReanimationType.NUM_REANIMS);
    }

    /** A Music that never touches the (null) music interface. */
    static class SilentMusic extends Music {
        /** Every tune MusicTune the cutscene asked for, in call order. */
        final List<Integer> tunes = new ArrayList<>();

        @Override
        public void makeSureMusicIsPlaying(int theMusicTune) {
            tunes.add(theMusicTune);
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

    /**
     * App double that allocates real reanimations in the effect-system holder like
     * {@code LawnApp::AddReanimation} does, so {@code Attachment::AttachReanim} can resolve them,
     * but hands each one the track table the upsell sequence asks for: without retail .reanim data
     * every shipped definition parses to zero tracks, which would silently skip
     * {@code AttachToAnotherReanimation} and the by-name track lookups.
     */
    static class StubLawnApp extends LawnApp {
        /** Frames per track in the synthetic reanimation definitions (see {@link #track(String)}). */
        private static final int UPS_FRAMES_PER_TRACK = 2;

        int startPlayingCount;
        int endLevelCount;
        int preNewGameCount;
        /** How often the purchase path recorded the profile (LawnApp::WriteCurrentUserConfig). */
        int writeCurrentUserConfigCount;
        final List<Integer> addedDialogs = new ArrayList<>();
        /** Reanimation types AddReanimation was asked for, in call order. */
        final List<Integer> addedReanimationTypes = new ArrayList<>();
        /** The reanimations AddReanimation handed back, index-aligned with the type list. */
        final List<Reanimation> addedReanims = new ArrayList<>();
        final SilentMusic silentMusic = new SilentMusic();
        boolean firstTimeAdventure;

        StubLawnApp() {
            super();
            // Everything LawnApp::Init() and the loading thread would create for a headless run:
            // profile, effect system, audio facades, the foley/reanim/particle/trail tables and the
            // stub images/fonts (the seed-bank slide and the seed-chooser buttons read both).
            cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(this);
            mWidgetManager = new WidgetManager(this);
            mMusic = silentMusic;
            // The pool/fog board update bumps the pool effect's counter (Board.cpp:5705-5709), but
            // PoolEffectInitialize() needs the retail 256x256 caustic bitmap, so install it bare.
            mPoolEffect = new PoolEffect();
            mPoolEffect.mApp = this;
            setFirstTimeAdventure(false);
        }

        /**
         * Scripted Crazy-Dave line body: every upsell line reports a 2-tick talk time, while the
         * missing {@code {DELAY_}} tag leaves {@code parseDelayTimeFromMessage} on its 100-tick
         * default (CutScene.cpp:1747-1775) — so a dialog step costs 2 + 100 ticks deterministically.
         */
        @Override
        public String getCrazyDaveText(int theMessageIndex) {
            return "{TIME_2}";
        }

        /**
         * The tracks the upsell sequence reaches for: Crazy Dave's handing/body/pot tracks
         * (CutScene.cpp:2138-2186), the threepeater's head attachment points and its head idle
         * layers (CutScene.cpp:2150-2160).
         */
        private static final String[] UPS_TRACK_NAMES = {
            "Dave_handinghand", "Dave_body1", "Dave_pot",
            "anim_head1", "anim_head2", "anim_head3",
            "anim_head_idle1", "anim_head_idle2", "anim_head_idle3",
        };

        /**
         * Every track carries two frames, like the shipped data, so the base-pose and blend lookups
         * ({@code mAnimFrameBeforeInt}/{@code AfterInt}) resolve.
         */
        private static ReanimatorDefinition upsellTracks() {
            ReanimatorDefinition aDefinition = new ReanimatorDefinition();
            ReanimatorTrack[] aTracks = new ReanimatorTrack[UPS_TRACK_NAMES.length];
            for (int i = 0; i < aTracks.length; i++) {
                aTracks[i] = track(UPS_TRACK_NAMES[i]);
            }
            aDefinition.mTracks = new DefinitionArrayDef(aTracks);
            return aDefinition;
        }

        private static ReanimatorTrack track(String theName) {
            ReanimatorTransform[] aTransforms = new ReanimatorTransform[UPS_FRAMES_PER_TRACK];
            for (int i = 0; i < aTransforms.length; i++) {
                aTransforms[i] = transform(i);
            }
            ReanimatorTrack aTrack = new ReanimatorTrack();
            aTrack.mName = theName;
            aTrack.mTransforms = new DefinitionArrayDef(aTransforms);
            return aTrack;
        }

        private static ReanimatorTransform transform(int theFrame) {
            ReanimatorTransform aTransform = new ReanimatorTransform();
            aTransform.mFrame = theFrame;
            aTransform.mScaleX = 1.0f;
            aTransform.mScaleY = 1.0f;
            aTransform.mAlpha = 1.0f;
            aTransform.mImage = null;
            aTransform.mFont = null;
            aTransform.mText = "";
            return aTransform;
        }

        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            addedReanimationTypes.add(theReanimationType);
            // Same holder allocation as LawnApp::AddReanimation (LawnApp.cpp:2305-2307)...
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            // ...plus the track table a loaded .reanim would have brought. ReanimationInitialize
            // derived the frame span and the track instances from whatever definition was installed
            // (Reanimator.cpp:397-421) and that ran against the empty headless one, so redo both.
            aReanim.mDefinition = upsellTracks();
            aReanim.mFrameStart = 0;
            aReanim.mFrameCount = UPS_FRAMES_PER_TRACK;
            aReanim.mTrackInstances = new ReanimatorTrackInstance[UPS_TRACK_NAMES.length];
            for (int i = 0; i < aReanim.mTrackInstances.length; i++) {
                aReanim.mTrackInstances[i] = new ReanimatorTrackInstance();
            }
            addedReanims.add(aReanim);
            return aReanim;
        }

        /**
         * Sets {@link #isFirstTimeAdventureMode()} together with the profile state it is derived
         * from upstream ({@code IsAdventureMode() && !HasFinishedAdventure()}, LawnApp.cpp:2450).
         * The seed-chooser's Crazy-Dave pick only has candidates among the seeds the profile owns
         * ({@link SeedChooserScreen#crazyDavePickSeeds} / {@code LawnApp::HasSeedType},
         * LawnApp.cpp:2269-2302), so the two have to agree.
         */
        final void setFirstTimeAdventure(boolean theFirstTime) {
            firstTimeAdventure = theFirstTime;
            mPlayerInfo.mLevel = theFirstTime ? 1 : 50;
            mPlayerInfo.mFinishedAdventure = theFirstTime ? 0 : 1;
        }

        @Override
        public void startPlaying() {
            startPlayingCount++;
            mGameScene = GameScenes.SCENE_PLAYING;
        }

        @Override
        public void endLevel() {
            endLevelCount++;
        }

        @Override
        public void preNewGame(int theGameMode, boolean theLookForSavedGame) {
            preNewGameCount++;
        }

        @Override
        public void addDialog(int theDialogId, Dialog theDialog) {
            addedDialogs.add(theDialogId);
        }

        @Override
        public boolean isFirstTimeAdventureMode() {
            return firstTimeAdventure;
        }

        @Override
        public boolean canShowAlmanac() {
            return false;
        }

        @Override
        public boolean canShowStore() {
            return false;
        }

        @Override
        public void playSample(long theSoundNum) {
        }

        @Override
        public void playFoley(int theFoleyType) {
        }

        @Override
        public void crazyDaveDie() {
        }

        @Override
        public boolean writeCurrentUserConfig() {
            // The headless fixture keeps the profile in memory (the purchase is asserted on it).
            writeCurrentUserConfigCount++;
            return true;
        }

        @Override
        public void updateCrazyDave() {
        }
    }

    private static StubLawnApp newApp(int theGameScene, int theGameMode) {
        StubLawnApp anApp = new StubLawnApp();
        anApp.mGameScene = theGameScene;
        anApp.mGameMode = theGameMode;
        return anApp;
    }

    private static void attachSeedChooser(StubLawnApp theApp) {
        theApp.mSeedChooserScreen = new SeedChooserScreen();
        theApp.mSeedChooserScreen.mParent = new WidgetContainer();
    }

    private static void tickUntil(CutScene theCutScene, BooleanSupplier theDone, int theCap) {
        int aTicks = 0;
        while (!theDone.getAsBoolean() && aTicks < theCap) {
            theCutScene.update();
            aTicks++;
        }
        assertTrue(aTicks < theCap, "cutscene did not complete within " + theCap + " ticks");
    }

    // ------------------------------------------------------------------
    // Adventure level-intro ("Ready! Set! Plant!") driven to completion.
    // ------------------------------------------------------------------
    @Test
    void adventureLevelIntroRunsToCompletionAndCallsStartPlayingOnce() {
        StubLawnApp anApp = newApp(GameScenes.SCENE_LEVEL_INTRO, GameMode.GAMEMODE_ADVENTURE);
        anApp.setFirstTimeAdventure(true);
        Board aBoard = new Board(anApp);
        attachSeedChooser(anApp);
        aBoard.mLevel = 1;
        aBoard.mNumWaves = 0;
        aBoard.mBoardUpdateCounter = 5;

        CutScene aCutScene = aBoard.mCutScene;
        aCutScene.startLevelIntro();

        // Per-mode timings (CutScene.cpp:700-800): level 1 is a first-time sod-roll level.
        assertEquals(2000, aCutScene.mSodTime);
        assertEquals(550, aCutScene.mLawnMowerTime);
        assertEquals(0, aCutScene.mReadySetPlantTime);
        assertEquals(0, aCutScene.mCrazyDaveTime);
        assertEquals(0, aCutScene.mBossTime);
        assertEquals(0, aCutScene.mFogTime);

        tickUntil(aCutScene, () -> anApp.startPlayingCount > 0, 2000);

        assertEquals(ADVENTURE_L1_INTRO_END, aCutScene.mCutsceneTime);
        assertEquals(1, anApp.startPlayingCount, "StartPlaying must fire exactly once");

        // The stub switches the scene, so further ticks are inert: still exactly one callback.
        for (int i = 0; i < 5; i++) {
            aCutScene.update();
        }
        assertEquals(1, anApp.startPlayingCount);
    }

    // ------------------------------------------------------------------
    // ZOMBIES-WON outro: fixed 10-tick cadence, one game-over dialog.
    // ------------------------------------------------------------------
    @Test
    void zombiesWonRunsToCompletionAndShowsOneGameOverDialog() {
        StubLawnApp anApp = newApp(GameScenes.SCENE_ZOMBIES_WON, GameMode.GAMEMODE_ADVENTURE);
        Board aBoard = new Board(anApp);
        attachSeedChooser(anApp);

        CutScene aCutScene = aBoard.mCutScene;
        aCutScene.startZombiesWon();

        assertEquals(0, aCutScene.mCutsceneTime);
        assertTrue(aBoard.mMenuButton.mBtnNoDraw);
        assertFalse(aBoard.mShowShovel);

        assertFalse(aCutScene.isCutSceneOver());
        // Clicking before the outro ends must not leave the level.
        aCutScene.zombieWonClick();
        assertEquals(0, anApp.endLevelCount);

        tickUntil(aCutScene, aCutScene::isCutSceneOver, 2000);

        assertEquals(11000, aCutScene.mCutsceneTime);
        assertTrue(aCutScene.isCutSceneOver());
        assertEquals(List.of(Dialogs.DIALOG_GAME_OVER), anApp.addedDialogs);

        for (int i = 0; i < 5; i++) {
            aCutScene.update();
        }
        assertEquals(1, anApp.addedDialogs.size(), "game-over dialog must be added exactly once");

        aCutScene.zombieWonClick();
        assertEquals(1, anApp.endLevelCount, "EndLevel fires once per click once the outro is over");
    }

    // ------------------------------------------------------------------
    // Skip-on-click: MouseDown/KeyDown with cheat keys fast-forwards the intro.
    // ------------------------------------------------------------------
    @Test
    void skipOnClickFastForwardsTheIntro() {
        StubLawnApp anApp = newApp(GameScenes.SCENE_LEVEL_INTRO, GameMode.GAMEMODE_ADVENTURE);
        anApp.setFirstTimeAdventure(false);
        anApp.mCheatKeys = true;
        Board aBoard = new Board(anApp);
        attachSeedChooser(anApp);
        aBoard.mLevel = 6;

        CutScene aCutScene = aBoard.mCutScene;
        aCutScene.startLevelIntro();
        aCutScene.mCutsceneTime = 1000;

        aCutScene.mouseDown(0, 0);

        // CancelIntro clamps to TimeSeedChoserSlideOnEnd - 20 and scrolls the board to the screen end.
        assertEquals(4230, aCutScene.mCutsceneTime);
        assertEquals(anApp.mWidth - GameConstants.BOARD_IMAGE_WIDTH_OFFSET, aBoard.mX);
        assertFalse(aCutScene.mSeedChoosing);
        assertEquals(0, anApp.startPlayingCount);

        // The keyboard path skips identically.
        CutScene aSecond = new Board(anApp).mCutScene;
        aSecond.startLevelIntro();
        aSecond.mCutsceneTime = 1000;
        aSecond.keyDown(KeyCode.KEYCODE_RETURN);
        assertEquals(4230, aSecond.mCutsceneTime);
    }

    // ------------------------------------------------------------------
    // Missing-reanim resilience: preloading unavailable reanims must not throw.
    // ------------------------------------------------------------------
    @Test
    void preloadResourcesToleratesMissingReanims() {
        StubLawnApp anApp = newApp(GameScenes.SCENE_LEVEL_INTRO, GameMode.GAMEMODE_ADVENTURE);
        Board aBoard = new Board(anApp);
        attachSeedChooser(anApp);
        aBoard.mNumWaves = 0;

        CutScene aCutScene = aBoard.mCutScene;
        aCutScene.mCrazyDaveDialogStart = 201;   // forces REANIM_CRAZY_DAVE
        aCutScene.mSodTime = 2000;               // forces REANIM_SODROLL

        assertFalse(aCutScene.mPreloaded);
        assertDoesNotThrow(aCutScene::preloadResources);
        assertTrue(aCutScene.mPreloaded);
    }

    // ------------------------------------------------------------------
    // GAMEMODE_UPSELL: Crazy Dave's store-intro cutscene (CutScene.cpp:682-965,
    // 1742-2229). The mode used to throw on entry; these tests drive the real sequence.
    // ------------------------------------------------------------------

    /** {@code TimeEarlyDaveLeaveEnd - TimePanRightStart} (CutScene.cpp:42-71). */
    private static final int UPSELL_CRAZY_DAVE_TIME = 4000 - 1500;

    private static StubLawnApp newUpsellApp() {
        // StartLevelIntro picks the upsell dialog (3300) and the hidden board (CutScene.cpp:820-826).
        return newApp(GameScenes.SCENE_LEVEL_INTRO, GameMode.GAMEMODE_UPSELL);
    }

    /** A started upsell cutscene whose board is a plain 5-lane lawn with a seed chooser child. */
    private static CutScene newUpsellCutScene(StubLawnApp theApp) {
        Board aBoard = new Board(theApp);
        attachSeedChooser(theApp);
        aBoard.mLevel = 0;
        aBoard.mNumWaves = 0;
        aBoard.mBoardUpdateCounter = 5;
        CutScene aCutScene = aBoard.mCutScene;
        aCutScene.startLevelIntro();
        // Dave enters in AnimateBoard (CutScene.cpp:1177-1184); create his reanimation the same way
        // LawnApp::CrazyDaveEnter does, then put him past the enter animation, which is the state
        // UpdateUpsell gates on (CutScene.cpp:2072-2076).
        theApp.crazyDaveEnter();
        theApp.mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
        return aCutScene;
    }

    /** Runs UpdateUpsell until Dave has spoken line {@code theIndex} (its case body has run). */
    private static void speakUntil(CutScene theCutScene, int theIndex) {
        int aTicks = 0;
        while (theCutScene.mCrazyDaveLastTalkIndex != theIndex && aTicks < 4000) {
            theCutScene.updateUpsell();
            aTicks++;
        }
        assertEquals(theIndex, theCutScene.mCrazyDaveLastTalkIndex,
                "Dave must reach upsell line " + theIndex);
    }

    /** The {@code theOccurrence}-th reanimation of {@code theReanimType} the app handed out. */
    private static Reanimation addedReanim(StubLawnApp theApp, int theReanimType, int theOccurrence) {
        int aSeen = 0;
        for (int i = 0; i < theApp.addedReanimationTypes.size(); i++) {
            if (theApp.addedReanimationTypes.get(i) == theReanimType && aSeen++ == theOccurrence) {
                return theApp.addedReanims.get(i);
            }
        }
        throw new AssertionError("no reanimation of type " + theReanimType + " #" + theOccurrence);
    }

    @Test
    void upsellIntroStartsDavesDialogAndHidesTheBoard() {
        StubLawnApp anApp = newUpsellApp();
        Board aBoard = new Board(anApp);
        attachSeedChooser(anApp);

        CutScene aCutScene = aBoard.mCutScene;
        assertDoesNotThrow(aCutScene::startLevelIntro);

        // CutScene.cpp:820-826: dialog 3300, board hidden, menu button live.
        assertEquals(3300, aCutScene.mCrazyDaveDialogStart);
        assertTrue(aCutScene.mUpsellHideBoard);
        assertFalse(aBoard.mMenuButton.mBtnNoDraw);
        assertEquals(UPSELL_CRAZY_DAVE_TIME, aCutScene.mCrazyDaveTime);
        // Upsell is a non-scrolling cutscene (CutScene.cpp:635-646), which is what keeps the board
        // off-screen behind Dave's dialog instead of panning in.
        assertTrue(aCutScene.isNonScrollingCutscene());
    }

    @Test
    void upsellUpdateDrivesDavesDialogAndDefersStartPlaying() {
        StubLawnApp anApp = newUpsellApp();
        CutScene aCutScene = newUpsellCutScene(anApp);
        // Keep the resource preload/placement out of this test: it is about the Update() hand-off.
        aCutScene.mPreloaded = true;
        aCutScene.mPlacedZombies = true;
        aCutScene.mPlacedLawnItems = true;

        // CutScene.cpp:1441-1445: the upsell branch runs UpdateUpsell and freezes the intro clock
        // while Dave is on screen, so StartPlaying must not fire yet.
        for (int i = 0; i < 200; i++) {
            aCutScene.update();
        }
        assertEquals(0, aCutScene.mCutsceneTime, "Dave's dialog gates the intro clock");
        assertEquals(0, anApp.startPlayingCount);
        assertTrue(aCutScene.mCrazyDaveLastTalkIndex >= 3300,
                "Update() must have driven the upsell dialog");

        // With Dave gone the same branch lets the intro clock advance again.
        anApp.mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_OFF;
        anApp.mCrazyDaveMessageIndex = -1;
        aCutScene.update();
        assertEquals(10, aCutScene.mCutsceneTime);
    }

    @Test
    void upsellDialogWalkBuildsEveryStoreTableau() {
        StubLawnApp anApp = newUpsellApp();
        CutScene aCutScene = newUpsellCutScene(anApp);
        Board aBoard = anApp.mBoard;

        // 3305 "Like this!": the squash prop is created and attached to Dave's handing hand.
        speakUntil(aCutScene, 3305);
        assertEquals(3305, anApp.mCrazyDaveMessageIndex);
        Reanimation aSquash = addedReanim(anApp, ReanimationType.REANIM_SQUASH, 0);
        assertEquals(1, anApp.addedReanimationTypes.stream()
                .filter(aType -> aType == ReanimationType.REANIM_SQUASH).count());
        assertTrue(aSquash.mIsAttachment, "AttachReanim must have run on Dave's handing hand");
        assertEquals(15.0f, aSquash.mAnimRate, "PlayReanim(\"anim_idle\", LOOP, 0, 15.0f)");
        assertEquals(ReanimLoopType.REANIM_LOOP, aSquash.mLoopType);

        // 3306 "And this!": threepeater plus three head sub-reanimations on Dave's body track.
        speakUntil(aCutScene, 3306);
        Reanimation aThreepeater = addedReanim(anApp, ReanimationType.REANIM_THREEPEATER, 0);
        assertTrue(aThreepeater.mIsAttachment, "the threepeater hangs off Dave_body1");
        assertEquals(4, anApp.addedReanimationTypes.stream()
                .filter(aType -> aType == ReanimationType.REANIM_THREEPEATER).count());
        for (int i = 1; i < 4; i++) {
            Reanimation aHead = addedReanim(anApp, ReanimationType.REANIM_THREEPEATER, i);
            assertTrue(aHead.mIsAttachment, "head " + i + " must attach to the threepeater");
            assertEquals(aThreepeater.mAnimRate, aHead.mAnimRate);
            assertEquals(ReanimLoopType.REANIM_LOOP, aHead.mLoopType);
            // AttachToAnotherReanimation("anim_head" + i) lands on that named track.
            assertTrue(aThreepeater.getTrackInstanceByName("anim_head" + i).mAttachmentID.value
                    != AttachmentID.ATTACHMENTID_NULL);
        }

        // 3307 "Later, I'll add this too!": the magnetshroom prop goes on Dave's pot track.
        speakUntil(aCutScene, 3307);
        Reanimation aMagnet = addedReanim(anApp, ReanimationType.REANIM_MAGNETSHROOM, 0);
        assertTrue(aMagnet.mIsAttachment, "the magnetshroom hangs off Dave_pot");
        assertEquals(1, anApp.addedReanimationTypes.stream()
                .filter(aType -> aType == ReanimationType.REANIM_MAGNETSHROOM).count());

        // 3309 "Because I'm cra-zy!!!!": both props are found on Dave and killed.
        speakUntil(aCutScene, 3309);
        assertTrue(aThreepeater.mDead, "FindSubReanim(REANIM_THREEPEATER)->ReanimationDie");
        assertTrue(aMagnet.mDead, "FindSubReanim(REANIM_MAGNETSHROOM)->ReanimationDie");

        // 3311 "You want to take action?": the loonboon music cue fires on the countdown-90 frame.
        speakUntil(aCutScene, 3311);
        int aTunesBefore = anApp.silentMusic.tunes.size();
        aCutScene.mCrazyDaveCountDown = 91;   // the tick decrements it to 90 first
        aCutScene.updateUpsell();
        assertEquals(90, aCutScene.mCrazyDaveCountDown);
        assertTrue(anApp.silentMusic.tunes.subList(aTunesBefore, anApp.silentMusic.tunes.size())
                .contains(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON));

        // 3312 "I'll give you more battles!": loonboon again, plus the pool tableau
        // (CutScene.cpp:1884-1902).
        int aTunesBefore3312 = anApp.silentMusic.tunes.size();
        speakUntil(aCutScene, 3312);
        assertTrue(anApp.silentMusic.tunes.subList(aTunesBefore3312, anApp.silentMusic.tunes.size())
                .contains(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON));
        assertEquals(30, aBoard.mPlants.mSize);
        assertEquals(11, aBoard.mZombies.mSize);
        assertFalse(aCutScene.mUpsellHideBoard);

        // 3313 "25 more levels of battles!": the fog tableau (CutScene.cpp:1942-2012).
        speakUntil(aCutScene, 3313);
        assertEquals(BackgroundType.BACKGROUND_4_FOG, aBoard.mBackground);
        assertEquals(26, aBoard.mPlants.mSize);
        assertEquals(11, aBoard.mZombies.mSize);
        assertFalse(aCutScene.mUpsellHideBoard);

        // 3314 "40 mini-games & puzzles!": the challenge-screen tableau (CutScene.cpp:2014-2018).
        speakUntil(aCutScene, 3314);
        assertNotNull(aCutScene.mUpsellChallengeScreen);
        assertEquals(ChallengePage.CHALLENGE_PAGE_CHALLENGE, aCutScene.mUpsellChallengeScreen.mPageIndex);
        assertEquals(0, aBoard.mPlants.mSize);
        assertEquals(0, aBoard.mZombies.mSize);
        assertFalse(aCutScene.mUpsellHideBoard);
        // ClearUpsellBoard also clears the 12 zombie ice timers (CutScene.cpp:1744-1749).
        assertEquals(GameConstants.BOARD_WIDTH, aBoard.mIceMinX[Board.MAX_GRID_SIZE_Y - 1]);

        // 3315 "Terra cotta!!!": board cleared and hidden for the terra-cotta part.
        speakUntil(aCutScene, 3315);
        assertTrue(aCutScene.mUpsellHideBoard);
        assertNull(aCutScene.mUpsellChallengeScreen);

        // 3316 "Enough to blow your mind to Mars and back!": the roof tableau.
        speakUntil(aCutScene, 3316);
        assertEquals(BackgroundType.BACKGROUND_5_ROOF, aBoard.mBackground);
        assertEquals(PlantRowType.PLANTROW_NORMAL, aBoard.mPlantRow[0]);
        assertEquals(PlantRowType.PLANTROW_NORMAL, aBoard.mPlantRow[4]);
        assertEquals(PlantRowType.PLANTROW_DIRT, aBoard.mPlantRow[5]);
        assertEquals(GridSquareType.GRIDSQUARE_GRASS, aBoard.mGridSquareType[0][0]);
        assertEquals(GridSquareType.GRIDSQUARE_DIRT, aBoard.mGridSquareType[Board.MAX_GRID_SIZE_X - 1][5]);
        assertEquals(51, aBoard.mPlants.mSize);
        assertEquals(13, aBoard.mZombies.mSize);
        assertFalse(aCutScene.mUpsellHideBoard);

        // 3317 "Uh, what are you waiting for?": board cleared, menu hidden, Dave waits.
        speakUntil(aCutScene, 3317);
        assertTrue(aCutScene.mUpsellHideBoard);
        assertTrue(aBoard.mMenuButton.mBtnNoDraw);
        assertEquals(0, aBoard.mPlants.mSize);
        assertEquals(0, aBoard.mZombies.mSize);
        // The store hand-off button is still hidden until Dave's last line finishes counting down.
        assertTrue(aBoard.mStoreButton.mBtnNoDraw);

        // One tick later the store hand-off buttons slide into place (CutScene.cpp:2085-2094).
        aCutScene.mCrazyDaveCountDown = 1;
        aCutScene.updateUpsell();
        assertEquals(0, aCutScene.mCrazyDaveCountDown);
        assertEquals(510, aBoard.mStoreButton.mX);
        assertEquals(420, aBoard.mStoreButton.mY);
        assertEquals(210, aBoard.mStoreButton.mWidth);
        assertEquals(46, aBoard.mStoreButton.mHeight);
        assertFalse(aBoard.mStoreButton.mBtnNoDraw);
        assertEquals(510, aBoard.mMenuButton.mX);
        assertEquals(480, aBoard.mMenuButton.mY);
        assertFalse(aBoard.mMenuButton.mBtnNoDraw);

        // Line 3317 parks the sequence: the buttons stay put on further ticks.
        aCutScene.mCrazyDaveCountDown = 0;
        aCutScene.updateUpsell();
        assertEquals(510, aBoard.mStoreButton.mX);
    }

    @Test
    void upsellDrawsTheChallengeScreenAndTheTerraCottaPot() {
        StubLawnApp anApp = newUpsellApp();
        CutScene aCutScene = newUpsellCutScene(anApp);
        Graphics aGraphics = new Graphics();

        // Before the tableaux start there is only the (hidden) board.
        assertDoesNotThrow(() -> aCutScene.drawUpsell(aGraphics));

        // 3314: the live challenge screen plus the menu button are drawn (CutScene.cpp:2224-2228).
        speakUntil(aCutScene, 3314);
        assertNotNull(aCutScene.mUpsellChallengeScreen);
        assertDoesNotThrow(() -> aCutScene.drawUpsell(aGraphics));

        // 3315 "Terra cotta!": the flower-pot prop plus the menu button (CutScene.cpp:2211-2222).
        speakUntil(aCutScene, 3315);
        assertNull(aCutScene.mUpsellChallengeScreen);
        assertDoesNotThrow(() -> aCutScene.drawUpsell(aGraphics));
        assertEquals(3315, aCutScene.mCrazyDaveLastTalkIndex);
    }

    @Test
    void clearUpsellBoardEmptiesEveryEntityList() {
        StubLawnApp anApp = newUpsellApp();
        CutScene aCutScene = newUpsellCutScene(anApp);
        Board aBoard = anApp.mBoard;

        speakUntil(aCutScene, 3312);
        assertTrue(aBoard.mPlants.mSize > 0);
        assertTrue(aBoard.mZombies.mSize > 0);

        aCutScene.clearUpsellBoard();

        assertEquals(0, aBoard.mPlants.mSize);
        assertEquals(0, aBoard.mZombies.mSize);
        assertEquals(0, aBoard.mCoins.mSize);
        assertEquals(0, aBoard.mProjectiles.mSize);
        assertEquals(0, aBoard.mGridItems.mSize);
        assertEquals(0, aBoard.mLawnMowers.mSize);
        assertEquals(0, aBoard.mIceTimer[0]);
        assertEquals(GameConstants.BOARD_WIDTH, aBoard.mIceMinX[0]);
        assertEquals(ParticleSystemID.PARTICLESYSTEMID_NULL, aBoard.mPoolSparklyParticleID);
        assertNull(aCutScene.mUpsellChallengeScreen);
    }

    // ------------------------------------------------------------------
    // The seed-slot offer: "How does that sound?" (CutScene.cpp:1611-1638)
    // ------------------------------------------------------------------

    @Test
    void confirmingTheSlotOfferBuysTheSeventhSeedSlot() {
        StubLawnApp anApp = newApp(GameScenes.SCENE_LEVEL_INTRO, GameMode.GAMEMODE_ADVENTURE);
        anApp.setFirstTimeAdventure(true);
        anApp.mPlayerInfo.mLevel = 14; // 13 levels won: 12 seeds unlocked, so a 7th slot fits
        anApp.mPlayerInfo.mCoins = 750;
        anApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 0;
        anApp.mPlayerInfo.mDidntPurchasePacketUpgrade = 0;
        // The headless fixture has no LawnStrings.txt: AdvanceCrazyDaveDialog only reaches the offer
        // when the line after the pitch resolves (LawnApp::AdvanceCrazyDaveText). The dialog appears
        // on the line the advance moves to — aMessageIndex is read after AdvanceCrazyDaveText
        // (CutScene.cpp:1592), so the click on Dave that shows it must start at 1502.
        anApp.setString("CRAZY_DAVE_1503", "How does that sound?");

        Board aBoard = new Board(anApp);
        attachSeedChooser(anApp);
        anApp.crazyDaveEnter();
        anApp.mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
        aBoard.mSeedBank.updateWidth();
        assertEquals(6, aBoard.mSeedBank.mNumPackets, "the bank starts with six slots");

        CutScene aCutScene = aBoard.mCutScene;
        anApp.mCrazyDaveMessageIndex = 1502; // the click that lands on "How does that sound?"
        aCutScene.advanceCrazyDaveDialog(false); // what a click on Dave reaches (CutScene.mouseDown)

        assertEquals(1503, anApp.mCrazyDaveMessageIndex);
        assertTrue(anApp.addedDialogs.contains(Dialogs.DIALOG_PURCHASE_PACKET_SLOT),
                "the offer has to be on screen");
        assertEquals(0, anApp.mPlayerInfo.mDidntPurchasePacketUpgrade,
                "the offer must not be answered before the player clicks");
        assertEquals(0, anApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);
        assertEquals(6, aBoard.mSeedBank.mNumPackets);

        // The player clicks "Yes": the dialog's button reaches LawnApp.buttonDepress.
        anApp.buttonDepress(2000 + Dialogs.DIALOG_PURCHASE_PACKET_SLOT);

        assertEquals(675, anApp.mPlayerInfo.mCoins, "the first slot upgrade costs 75 coins");
        assertEquals(1, anApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);
        assertEquals(1, anApp.writeCurrentUserConfigCount, "the purchase is recorded in the profile");
        assertEquals(7, aBoard.getNumSeedsInBank());
        assertEquals(7, aBoard.mSeedBank.mNumPackets, "the bought slot is usable right away");
        assertEquals(0, anApp.mPlayerInfo.mDidntPurchasePacketUpgrade, "the offer was not declined");
        assertEquals(1510, anApp.mCrazyDaveMessageIndex, "Dave's \"bought\" line");
    }

    @Test
    void decliningTheSlotOfferIsRemembered() {
        StubLawnApp anApp = newApp(GameScenes.SCENE_LEVEL_INTRO, GameMode.GAMEMODE_ADVENTURE);
        anApp.setFirstTimeAdventure(true);
        anApp.mPlayerInfo.mLevel = 14;
        anApp.mPlayerInfo.mCoins = 750;
        anApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 0;
        anApp.mPlayerInfo.mDidntPurchasePacketUpgrade = 0;
        anApp.setString("CRAZY_DAVE_1503", "How does that sound?");

        Board aBoard = new Board(anApp);
        attachSeedChooser(anApp);
        anApp.crazyDaveEnter();
        anApp.mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
        aBoard.mSeedBank.updateWidth();

        CutScene aCutScene = aBoard.mCutScene;
        anApp.mCrazyDaveMessageIndex = 1502;
        aCutScene.advanceCrazyDaveDialog(false);

        anApp.buttonDepress(3000 + Dialogs.DIALOG_PURCHASE_PACKET_SLOT);

        assertEquals(750, anApp.mPlayerInfo.mCoins, "declining buys nothing");
        assertEquals(0, anApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);
        assertEquals(6, aBoard.mSeedBank.mNumPackets);
        assertEquals(1, anApp.mPlayerInfo.mDidntPurchasePacketUpgrade, "the refusal is remembered");
        assertEquals(1520, anApp.mCrazyDaveMessageIndex, "Dave's \"suit yourself\" line");
    }

    @Test
    void upsellPacketUpgradeCostsComeFromTheStoreTable() {
        StubLawnApp anApp = newUpsellApp();
        CutScene aCutScene = new Board(anApp).mCutScene;

        // CutScene.cpp:655-680: the cost is StoreScreen::GetItemCost(STORE_ITEM_PACKET_UPGRADE).
        anApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 0;
        anApp.mPlayerInfo.mCoins = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);
        anApp.mPlayerInfo.mDidntPurchasePacketUpgrade = 0;
        assertTrue(aCutScene.canGetPacketUpgrade());

        anApp.mPlayerInfo.mCoins = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE) - 1;
        assertFalse(aCutScene.canGetPacketUpgrade());
        assertFalse(aCutScene.canGetSecondPacketUpgrade());

        anApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 1;
        anApp.mPlayerInfo.mCoins = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);
        assertFalse(aCutScene.canGetPacketUpgrade());
        assertTrue(aCutScene.canGetSecondPacketUpgrade());
        assertTrue(aCutScene.canGetPacketUpgrade(1));

        // Two refusals retire the offer (CutScene.cpp:661).
        anApp.mPlayerInfo.mDidntPurchasePacketUpgrade = 2;
        assertFalse(aCutScene.canGetSecondPacketUpgrade());
    }

    // ------------------------------------------------------------------
    // Pure helpers.
    // ------------------------------------------------------------------
    @Test
    void is2x2ZombieOnlyMatchesGargantuars() {
        assertTrue(CutScene.is2x2Zombie(ZombieType.ZOMBIE_GARGANTUAR));
        assertTrue(CutScene.is2x2Zombie(ZombieType.ZOMBIE_REDEYE_GARGANTUAR));
        assertFalse(CutScene.is2x2Zombie(ZombieType.ZOMBIE_NORMAL));
        assertFalse(CutScene.is2x2Zombie(ZombieType.ZOMBIE_ZAMBONI));
    }

    @Test
    void calcPositionFollowsTheEaseInOutCurve() {
        StubLawnApp anApp = newApp(GameScenes.SCENE_LEVEL_INTRO, GameMode.GAMEMODE_ADVENTURE);
        Board aBoard = new Board(anApp);
        CutScene aCutScene = aBoard.mCutScene;

        aCutScene.mCutsceneTime = 100;
        assertEquals(0, aCutScene.calcPosition(100, 200, 0, 30));
        aCutScene.mCutsceneTime = 200;
        assertEquals(30, aCutScene.calcPosition(100, 200, 0, 30));
    }
}
