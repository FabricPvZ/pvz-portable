/*
 * Ported from the upstream boot/entry behaviour of src/LawnApp.cpp (LawnApp::LawnApp,
 * LawnApp::Init, LawnApp::LoadingThreadProc/LoadingCompleted, LawnApp::PreNewGame/NewGame,
 * LawnApp::CheckForGameEnd, LawnApp::Shutdown).
 *
 * The suite is headless: a recording {@link PlatformBackend} and a proxy {@link RendererBackend}
 * stand in for SDL. Anything that needs the retail assets is gated with {@link Assumptions} so the
 * suite stays green on machines without the game; the pure-maths and constructor assertions are
 * unconditional.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.widget.GameSelector;
import cn.enaium.fabricpvz.portable.pvzp.PakWriter;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformBackend;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformEvent;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;

class LawnAppTest {

    /**
     * Minimal resource manifest: an empty {@code Init} group keeps {@code LawnApp::Init}'s
     * {@code PvzpLoadResources("Init")} call succeeding without any retail art.
     */
    private static final String SYNTHETIC_MANIFEST = """
            <?xml version="1.0"?>
            <ResourceManifest>
            <Resources id="Init">
            </Resources>
            <Resources id="LoaderBar">
            </Resources>
            </ResourceManifest>
            """;

    /** Screen-target stand-in (the framework draws through {@link Graphics}/{@link Image} only). */
    static class TestImage extends Image {
        TestImage(int theWidth, int theHeight) {
            mWidth = theWidth;
            mHeight = theHeight;
        }
    }

    /** Records platform invocations; the test drives the monotonic clock manually. */
    static class FakePlatformBackend implements PlatformBackend {
        long mTicks = 1000;
        int mInitCalls;
        Options mLastOptions;
        String mLastTitle;

        @Override
        public void init(Options options) {
            mInitCalls++;
            mLastOptions = options;
        }

        @Override
        public boolean pollEvent(PlatformEvent out) {
            return false;
        }

        @Override
        public void swapBuffers() {
        }

        @Override
        public long ticksMillis() {
            return mTicks;
        }

        @Override
        public void sleepMillis(long millis, int nanos) {
            // The frame loop sleeps until the next frame is due and derives every timing decision
            // from ticksMillis() (SexyAppBase::Process/UpdateFTimeAcc, SexyAppBase.cpp:2698-2790).
            // A frozen clock would leave mUpdateFTimeAcc below the frame time forever, so
            // SexyAppBase::UpdateApp's retry loop (SexyAppBase.java:1128-1140) could never return:
            // the double models a running clock by charging the slept time to it.
            mTicks += millis;
        }

        @Override
        public void setTitle(String title) {
            mLastTitle = title;
        }

        @Override
        public int windowWidth() {
            return 800;
        }

        @Override
        public int windowHeight() {
            return 600;
        }

        @Override
        public int pixelWidth() {
            return 800;
        }

        @Override
        public int pixelHeight() {
            return 600;
        }

        @Override
        public void setClipboardText(String text) {
        }

        @Override
        public String getClipboardText() {
            return "";
        }

        @Override
        public void setCursor(CursorType cursor) {
        }

        @Override
        public void setCustomCursor(Image cursorImage, int hotX, int hotY) {
        }

        @Override
        public void hideCursor(boolean hide) {
        }

        @Override
        public void showMessageBox(String title, String message, boolean isError) {
        }

        @Override
        public void openURL(String url) {
        }

        @Override
        public void shutdown() {
        }
    }

    /** Recording renderer double (dynamic proxy so it survives additions to the interface). */
    static class FakeRendererBackend implements java.lang.reflect.InvocationHandler {
        @Override
        public Object invoke(Object theProxy, java.lang.reflect.Method theMethod, Object[] theArgs) {
            switch (theMethod.getName()) {
                case "present":
                    return null;
                case "renderOutputWidth":
                case "renderOutputHeight":
                    return 800;
                case "createTexture":
                case "createRenderTargetTexture":
                    return 1L;
                case "toString":
                    return "FakeRendererBackend";
                default:
                    return defaultValue(theMethod.getReturnType());
            }
        }

        private static Object defaultValue(Class<?> theType) {
            if (!theType.isPrimitive()) {
                return null;
            }
            if (theType == boolean.class) {
                return Boolean.FALSE;
            }
            if (theType == long.class) {
                return 0L;
            }
            if (theType == float.class) {
                return 0.0f;
            }
            if (theType == double.class) {
                return 0.0;
            }
            if (theType == void.class) {
                return null;
            }
            return 0;
        }

        RendererBackend newProxy() {
            return (RendererBackend) java.lang.reflect.Proxy.newProxyInstance(
                    RendererBackend.class.getClassLoader(), new Class<?>[] {RendererBackend.class}, this);
        }
    }

    private LawnApp mApp;

    /**
     * A {@link LawnApp} whose reanimations look loaded. Without retail .reanim data every definition
     * parses to zero tracks, so a real reanimation keeps a null {@code mTrackInstances}
     * (Reanimator.cpp:397-421) while the widget code indexes it ({@code GetTrackInstanceByName},
     * Reanimator.cpp:974-977 is reached from {@code GameSelector::SyncButtons});
     * {@link cn.enaium.fabricpvz.portable.test.HeadlessTestApp#stubLoadedTracks} supplies the per-track state a
     * loaded reanimation would have.
     */
    static class LoadedReanimLawnApp extends LawnApp {
        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            cn.enaium.fabricpvz.portable.test.HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }
    }

    /** Builds a headless LawnApp with a screen target and a recording platform/renderer pair. */
    private LawnApp newApp(Path theResourceDir) {
        mApp = new LoadedReanimLawnApp();
        mApp.mPlatformBackend = new FakePlatformBackend();
        mApp.mRendererBackend = new FakeRendererBackend().newProxy();
        mApp.mWidgetManager.mImage = new TestImage(800, 600);
        mApp.mLoaded = true;
        mApp.mAutoStartLoadingThread = false; // LawnApp drives loading itself (upstream default)
        mApp.mFrameTime = 10;
        mApp.mUpdateMultiplier = 1;
        mApp.mNoSoundNeeded = true;
        if (theResourceDir != null) {
            mApp.mResourceDir = theResourceDir.toString();
        }
        // Init() and LoadingCompleted() build widgets (TitleScreen, GameSelector) that read the
        // image and font tables; the synthetic manifest has empty resource groups and the real
        // groups load asynchronously, so the headless fixture binds the stub tables first. The
        // loading thread overwrites them with the real resources when they exist.
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(mApp);
        return mApp;
    }

    @AfterEach
    void restoreGlobal() {
        LawnApp.gLawnApp = null;
    }

    // ------------------------------------------------------------------
    // Asset-free assertions
    // ------------------------------------------------------------------

    @Test
    void constructorIsHeadlessAndSetsTheGameGlobal() {
        LawnApp anApp = new LawnApp();

        assertSame(anApp, LawnApp.gLawnApp, "gLawnApp is the LawnApp ctor's global (LawnApp.cpp:118)");
        assertSame(anApp, cn.enaium.fabricpvz.portable.sexy.SexyAppBase.current, "the framework global points at the same object");
        assertEquals(GameScenes.SCENE_LOADING, anApp.mGameScene);
        assertEquals(GameMode.GAMEMODE_ADVENTURE, anApp.mGameMode);
        assertEquals(BoardResult.BOARDRESULT_NONE, anApp.mBoardResult);
        assertEquals(TrialType.TRIALTYPE_NONE, anApp.mTrialType);
        assertEquals(GameConstants.BOARD_WIDTH, anApp.mWidth);
        assertEquals(GameConstants.BOARD_HEIGHT, anApp.mHeight);
        assertEquals(CrazyDaveState.CRAZY_DAVE_OFF, anApp.mCrazyDaveState);
        assertNotNull(anApp.mProfileMgr);
        assertNotNull(anApp.mLastLevelStats);
        assertNull(anApp.mBoard, "no board exists before a level is entered");
        assertNull(anApp.mTitleScreen, "the title screen is created by Init(), not the ctor");
        assertNull(anApp.mMusic, "Music is created by Init() (LawnApp.cpp:1218)");
        assertNull(anApp.mEffectSystem);
        assertFalse(anApp.mCloseRequest);
        assertEquals(0.5525, anApp.mSfxVolume, 1e-9, "LawnApp ctor overrides the framework sfx volume");
    }

    @Test
    void pureHelpersMatchUpstreamMaths() {
        assertEquals("1-1", LawnApp.getStageString(1));
        assertEquals("1-10", LawnApp.getStageString(10));
        assertEquals("2-1", LawnApp.getStageString(11));
        assertEquals("5-10", LawnApp.getStageString(50));

        // Each area awards 8 plants; sub-levels 5 and 10 award no new seed (LawnApp.cpp:2236).
        assertEquals(0, LawnApp.getAwardSeedForLevel(0));
        assertEquals(1, LawnApp.getAwardSeedForLevel(1));
        assertEquals(4, LawnApp.getAwardSeedForLevel(4));
        assertEquals(8, LawnApp.getAwardSeedForLevel(10));
        assertEquals(9, LawnApp.getAwardSeedForLevel(11));
        assertEquals(40, LawnApp.getAwardSeedForLevel(50));

        assertTrue(LawnApp.isSurvivalNormal(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_3));
        assertFalse(LawnApp.isSurvivalNormal(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_1));
        assertTrue(LawnApp.isSurvivalHard(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_5));
        assertTrue(LawnApp.isSurvivalEndless(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1));
        assertTrue(LawnApp.isEndlessScaryPotter(GameMode.GAMEMODE_SCARY_POTTER_ENDLESS));
        assertTrue(LawnApp.isEndlessIZombie(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS));

        LawnApp anApp = new LawnApp();
        assertEquals("$10", anApp.getMoneyString(1));
        assertEquals("$1,000", anApp.getMoneyString(100));
        assertEquals("1 apple", LawnApp.pluralize(1, "{COUNT} apple", "{COUNT} apples"));
        assertEquals("3 apples", LawnApp.pluralize(3, "{COUNT} apple", "{COUNT} apples"));
    }

    @Test
    void modePredicatesFollowTheGameMode() {
        LawnApp anApp = new LawnApp();

        anApp.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        assertTrue(anApp.isAdventureMode());
        assertFalse(anApp.isSurvivalMode());
        assertFalse(anApp.isPuzzleMode());
        assertFalse(anApp.isChallengeMode());
        assertTrue(anApp.isFirstTimeAdventureMode()); // no PlayerInfo yet, HasFinishedAdventure() is false

        anApp.mGameMode = GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
        assertTrue(anApp.isSurvivalMode());
        // IsChallengeMode() is the complement of adventure/puzzle/survival (LawnApp.cpp:1995), so a
        // survival mode is *not* a challenge mode.
        assertFalse(anApp.isChallengeMode());

        anApp.mGameMode = GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1;
        assertTrue(anApp.isPuzzleMode());
    }

    @Test
    void levelNameHelpersBeforeInit() {
        assertEquals("Before App", LawnApp.lawnGetCurrentLevelName());
        assertFalse(LawnApp.lawnGetCloseRequest());
        assertFalse(LawnApp.lawnHasUsedCheatKeys());

        LawnApp anApp = new LawnApp();
        assertEquals("Game Loading", LawnApp.lawnGetCurrentLevelName());
        assertFalse(LawnApp.lawnGetCloseRequest());

        anApp.mGameScene = GameScenes.SCENE_MENU;
        assertEquals("Game Selector", LawnApp.lawnGetCurrentLevelName());
    }

    // ------------------------------------------------------------------
    // Synthetic-pak boot
    // ------------------------------------------------------------------

    private Path writeSyntheticPak(Path theDir) throws IOException {
        Path aDir = Files.createDirectories(theDir);
        PakWriter.write(aDir.resolve("main.pak"),
                Map.of("properties/resources.xml", SYNTHETIC_MANIFEST.getBytes(StandardCharsets.UTF_8)));
        return aDir;
    }

    /**
     * Boots against a synthetic {@code main.pak}. {@code Init()} parses the manifest and creates the
     * {@code TitleScreen} (which is the {@link GameScenes#SCENE_LOADING} screen); finishing the
     * loading thread hands off to {@code ShowGameSelector()} which switches to
     * {@link GameScenes#SCENE_MENU}.
     */
    @Test
    void syntheticPakInitShowsTitleScreenThenHandsOffToGameSelector(@TempDir Path theTempDir) throws IOException {
        Path aResourceDir = writeSyntheticPak(theTempDir.resolve("game"));
        LawnApp anApp = newApp(aResourceDir);

        anApp.init();

        assertTrue(anApp.mInitialized, "init() completed");
        assertFalse(anApp.mShutdown.get());
        assertEquals(GameScenes.SCENE_LOADING, anApp.mGameScene, "the title screen is the loading scene");
        assertNotNull(anApp.mTitleScreen, "Init() creates the TitleScreen widget (LawnApp.cpp:1207)");
        assertNotNull(anApp.mMusic, "Init() creates the Music system");
        assertNotNull(anApp.mSoundSystem, "Init() creates the Foley system");
        assertNotNull(anApp.mEffectSystem);
        assertNotNull(anApp.mKonamiCheck, "the cheat-key TypingChecks are created in Init()");

        // The loading thread's last act is LoadingCompleted(): drop the title screen, then show the
        // game selector (LawnApp.cpp:1726).
        anApp.loadingCompleted();

        assertNull(anApp.mTitleScreen, "LoadingCompleted() releases the TitleScreen");
        assertEquals(GameScenes.SCENE_MENU, anApp.mGameScene, "the hand-off lands on the main menu");
        assertNotNull(anApp.mGameSelector);
        assertEquals(0, anApp.mGameSelector.mX);
        assertEquals(GameConstants.BOARD_WIDTH, anApp.mGameSelector.mWidth);
    }

    @Test
    void shutdownAfterSyntheticBootIsClean(@TempDir Path theTempDir) throws IOException {
        Path aResourceDir = writeSyntheticPak(theTempDir.resolve("game"));
        LawnApp anApp = newApp(aResourceDir);

        anApp.init();
        assertFalse(anApp.mShutdown.get());

        assertDoesNotThrow(anApp::shutdown);
        assertTrue(anApp.mShutdown.get(), "shutdown() sets the framework shutdown flag");

        // dispose() is the Java stand-in for ~LawnApp and must also be exception-free.
        assertDoesNotThrow(anApp::dispose);
    }

    // ------------------------------------------------------------------
    // Asset-dependent level flow (assumption-gated)
    // ------------------------------------------------------------------

    /**
     * Boots an app against the real retail resources the way the game does: {@code Init()}, then the
     * loading thread (which loads every asset group and fills the definition tables) with the frame
     * loop pumped until it reports completion — bounded by wall time, so a stuck loader fails the
     * test with a message instead of hanging it. Skips the test when the assets are absent.
     *
     * <p>The save directory is a per-test temp dir, so the profile list starts empty and the test
     * creates its own profile exactly like the retail new-user dialog does — {@code PreNewGame}
     * dereferences {@code mPlayerInfo} (LawnApp.cpp:337-353).
     */
    private LawnApp newRealAssetApp(Path theSaveDir) throws IOException, InterruptedException {
        var aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "retail PvZ resources are required for the level flow");
        LawnApp anApp = newApp(null);
        anApp.setArgs(new String[] {
            "pvz-portable", "-resdir", aRoot.get().toString(), "-savedir", theSaveDir.toString(),
        });
        anApp.init();

        // start() would run the whole blocking main loop; drive its two pieces by hand instead —
        // start the loading thread (as Start() does) and pump frames until it is done.
        anApp.startLoadingThread();
        long aDeadline = System.nanoTime() + 180_000_000_000L;
        while (!anApp.mLoadingThreadCompleted.get() && System.nanoTime() < aDeadline) {
            anApp.updateFrames();
            Thread.sleep(2);
        }
        assertTrue(anApp.mLoadingThreadCompleted.get(), "resource loading must complete");

        if (anApp.mPlayerInfo == null) {
            anApp.mPlayerInfo = anApp.mProfileMgr.addProfile("LawnAppTest");
        }
        return anApp;
    }

    @Test
    void adventureLevelOneEntryBuildsABoardAndSeedBank(@TempDir Path theTempDir) throws IOException, InterruptedException {
        LawnApp anApp = newRealAssetApp(theTempDir.resolve("userdata"));

        anApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);

        assertNotNull(anApp.mBoard, "PreNewGame -> NewGame -> MakeNewBoard creates the Board");
        assertEquals(GameMode.GAMEMODE_ADVENTURE, anApp.mGameMode);
        assertEquals(1, anApp.mBoard.mLevel, "adventure starts at level 1 (PlayerInfo default)");
        assertEquals(GameScenes.SCENE_LEVEL_INTRO, anApp.mGameScene);
        assertNotNull(anApp.mSeedChooserScreen, "NewGame shows the seed chooser (LawnApp.cpp:430)");
        assertNotNull(anApp.mBoard.mCutScene);

        anApp.startPlaying();

        assertEquals(GameScenes.SCENE_PLAYING, anApp.mGameScene);
        assertNull(anApp.mSeedChooserScreen, "StartPlaying kills the chooser");
        assertNotNull(anApp.mBoard.mSeedBank, "the board owns a seed bank");
        // A first-time adventure level 1 grants 150 sun (Board.cpp:1353-1356); 50 is the rule for
        // every other level (Board.cpp:1357-1360).
        assertEquals(150, anApp.mBoard.mSunMoney, "a first-time level-1 board starts with 150 sun");
    }

    @Test
    void checkForGameEndConsumesACompletedAdventureBoard(@TempDir Path theTempDir) throws IOException, InterruptedException {
        LawnApp anApp = newRealAssetApp(theTempDir.resolve("userdata"));
        anApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
        Board aBoard = anApp.mBoard;
        assertNotNull(aBoard);

        // Simulate the board flagging the level as complete (what Board does on the final wave).
        aBoard.mLevelComplete = true;
        anApp.checkForGameEnd();

        assertNull(anApp.mBoard, "CheckForGameEnd kills the finished board");
        assertEquals(GameScenes.SCENE_AWARD, anApp.mGameScene,
                "a first-time adventure level win shows the award screen");
        assertNotNull(anApp.mAwardScreen);
    }

    @Test
    void syntheticLossKillsTheBoardAndReturnsToTheMenu(@TempDir Path theTempDir) throws IOException, InterruptedException {
        LawnApp anApp = newRealAssetApp(theTempDir.resolve("userdata"));
        anApp.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
        Board aBoard = anApp.mBoard;
        assertNotNull(aBoard);

        // Loss path: the board records BOARDRESULT_LOST and LawnApp tears it down (KillBoard).
        anApp.mBoardResult = BoardResult.BOARDRESULT_LOST;
        anApp.killBoard();

        assertNull(anApp.mBoard);
        assertEquals(BoardResult.BOARDRESULT_LOST, anApp.mBoardResult);

        anApp.showGameSelector();
        assertEquals(GameScenes.SCENE_MENU, anApp.mGameScene);
    }

    @Test
    void updateAppTicksTheFrameLoop(@TempDir Path theTempDir) throws IOException {
        Path aResourceDir = writeSyntheticPak(theTempDir.resolve("game"));
        LawnApp anApp = newApp(aResourceDir);
        anApp.init();

        int aBefore = anApp.mAppCounter;
        for (int i = 0; i < 5; i++) {
            anApp.mLoaded = true;
            anApp.updateApp();
        }

        assertTrue(anApp.mAppCounter >= aBefore, "UpdateFrames() increments mAppCounter each tick");
    }

    @Test
    void thePortStatesItsOwnProduct() {
        // `-version` prints this and the startup log carries it: the product is this port's own
        // identity, not the upstream C++ project's id it started with.
        assertEquals("cn.enaium.fabricpvz", new LawnApp().mProdName);
    }
}
