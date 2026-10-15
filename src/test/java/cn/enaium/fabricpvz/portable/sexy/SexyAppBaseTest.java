package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import cn.enaium.fabricpvz.portable.sexy.Common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformBackend;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformEvent;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;
import cn.enaium.fabricpvz.portable.sexy.graphics.SharedImageRef;
import cn.enaium.fabricpvz.portable.sexy.hooks.AppHooks;
import cn.enaium.fabricpvz.portable.sexy.hooks.AppHooksRegistry;

/**
 * Behaviour tests for {@link SexyAppBase}: the headless constructor contract, the 100 Hz frame
 * pacer, the update state machine, the dirty-draw ordering and the AppHooks lifecycle notifications
 * (src/SexyAppFramework/SexyAppBase.cpp).
 */
class SexyAppBaseTest {

    /** Screen-target stand-in: the framework draws through {@link Graphics}/{@link Image} only. */
    static class TestImage extends Image {
        TestImage(int theWidth, int theHeight) {
            mWidth = theWidth;
            mHeight = theHeight;
        }
    }

    static class TestWidget extends Widget {
        int mDraws;
        int mMouseDowns;
        int mMouseUps;

        @Override
        public void draw(Graphics g) {
            mDraws++;
        }

        @Override
        public void mouseDown(int x, int y, int theClickCount) {
            mMouseDowns++;
        }

        @Override
        public void mouseUp(int x, int y, int theLastDownButtonId) {
            mMouseUps++;
        }
    }

    /** Records platform invocations and lets tests drive the monotonic clock. */
    static class FakePlatformBackend implements PlatformBackend {
        long mTicks;
        int mCalls;
        int mInitCalls;
        int mSleepCalls;
        Options mLastOptions;
        String mLastTitle;

        void advance(long theMillis) {
            mTicks += theMillis;
        }

        @Override
        public void init(Options options) {
            mCalls++;
            mInitCalls++;
            mLastOptions = options;
        }

        final java.util.ArrayDeque<PlatformEvent> mEvents = new java.util.ArrayDeque<>();

        @Override
        public boolean pollEvent(PlatformEvent out) {
            mCalls++;
            PlatformEvent anEvent = mEvents.poll();
            if (anEvent == null) {
                return false;
            }
            out.type = anEvent.type;
            out.x = anEvent.x;
            out.y = anEvent.y;
            out.dx = anEvent.dx;
            out.dy = anEvent.dy;
            out.button = anEvent.button;
            out.keyCode = anEvent.keyCode;
            out.width = anEvent.width;
            out.height = anEvent.height;
            return true;
        }

        @Override
        public void swapBuffers() {
            mCalls++;
        }

        @Override
        public long ticksMillis() {
            return mTicks;
        }

        @Override
        public void sleepMillis(long millis, int nanos) {
            mCalls++;
            mSleepCalls++;
        }

        @Override
        public void setTitle(String title) {
            mCalls++;
            mLastTitle = title;
        }

        @Override
        public int windowWidth() {
            return 640;
        }

        @Override
        public int windowHeight() {
            return 480;
        }

        @Override
        public int pixelWidth() {
            return 640;
        }

        @Override
        public int pixelHeight() {
            return 480;
        }

        @Override
        public void setClipboardText(String text) {
            mCalls++;
        }

        @Override
        public String getClipboardText() {
            mCalls++;
            return "";
        }

        @Override
        public void setCursor(CursorType cursor) {
            mCalls++;
        }

        @Override
        public void setCustomCursor(Image cursorImage, int hotX, int hotY) {
            mCalls++;
        }

        @Override
        public void hideCursor(boolean hide) {
            mCalls++;
        }

        @Override
        public void showMessageBox(String title, String message, boolean isError) {
            mCalls++;
        }

        @Override
        public void openURL(String url) {
            mCalls++;
        }

        @Override
        public void shutdown() {
            mCalls++;
        }
    }

    /**
     * Recording renderer double: a JDK dynamic proxy, so the test keeps working as the
     * {@link RendererBackend} interface grows (it is owned by the graphics slice).
     */
    static class FakeRendererBackend implements java.lang.reflect.InvocationHandler {
        int mPresents;
        int mWidth = 640;
        int mHeight = 480;
        int mLogicalWidth = 640;
        int mLogicalHeight = 480;
        int[] mPresentationRect;

        RendererBackend newProxy() {
            return (RendererBackend) java.lang.reflect.Proxy.newProxyInstance(
                    RendererBackend.class.getClassLoader(), new Class<?>[] {RendererBackend.class},
                    this);
        }

        @Override
        public Object invoke(Object theProxy, java.lang.reflect.Method theMethod, Object[] theArgs) {
            switch (theMethod.getName()) {
                case "present":
                    mPresents++;
                    return null;
                case "renderOutputWidth":
                    return mWidth;
                case "renderOutputHeight":
                    return mHeight;
                case "logicalWidth":
                    return mLogicalWidth;
                case "logicalHeight":
                    return mLogicalHeight;
                case "logicalPresentationRect":
                    return mPresentationRect == null
                            ? new int[] {0, 0, mWidth, mHeight} : mPresentationRect;
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
    }

    private SexyAppBase mApp;
    private FakePlatformBackend mPlatform;

    /** Builds a headless app that already has a screen target and a recording platform backend. */
    private SexyAppBase newApp() {
        mApp = new SexyAppBase();
        mPlatform = new FakePlatformBackend();
        mPlatform.mTicks = 1000; // SDL_GetTicks() is never 0 at startup; mLastTimeCheck == 0 means "unset"
        mApp.mPlatformBackend = mPlatform;
        mApp.mWidgetManager.mImage = new TestImage(100, 100);
        mApp.mLoaded = true;
        mApp.mAutoStartLoadingThread = false;
        mApp.mFrameTime = 10;
        mApp.mUpdateMultiplier = 1;
        return mApp;
    }

    @AfterEach
    void restoreGlobal() {
        SexyAppBase.current = null;
    }

    @Test
    void constructorIsHeadlessAndSetsTheGlobal() {
        SexyAppBase anApp = new SexyAppBase();

        assertSame(anApp, SexyAppBase.current);
        assertNotNull(anApp.mWidgetManager);
        assertSame(anApp, anApp.mWidgetManager.mApp);
        assertNotNull(anApp.mResourceManager);
        assertNull(anApp.mPlatformBackend, "no platform backend is created in the constructor");
        assertNull(anApp.mRendererBackend, "no renderer is created in the constructor");
        assertNull(anApp.mSoundManager, "no audio device is created in the constructor");
        assertNull(anApp.mMusicInterface);
        assertNull(anApp.mLoadingThread, "no loading thread is started in the constructor");
        assertFalse(anApp.mLoadingThreadStarted.get());
        assertNull(anApp.mWidgetManager.mImage, "no screen image is allocated in the constructor");
        assertNull(anApp.mWindow);
        assertFalse(anApp.mShutdown.get());

        // The 100 Hz defaults of the pacer.
        assertEquals(10, anApp.mFrameTime);
        assertEquals(1, anApp.mUpdateMultiplier);
    }

    @Test
    void initIsThePlatformEntryPointWhileTheConstructorAndHelpersAreNot() {
        SexyAppBase anApp = newApp();

        // Headless helpers must not touch the platform backend at all.
        anApp.setArgs(new String[] {"pvz", "-screensaver"});
        anApp.doParseCmdLine();
        anApp.setString("ID", "value");
        anApp.setBoolean("FLAG", true);
        anApp.setInteger("NUM", 7);
        anApp.setDouble("DBL", 0.5);
        assertEquals("value", anApp.getString("ID"));
        assertTrue(anApp.getBoolean("FLAG", false));
        assertEquals(7, anApp.getInteger("NUM", 0));
        assertEquals(0.5, anApp.getDouble("DBL", 0.0), 1e-9);
        assertEquals(0, anApp.getStringVector("MISSING").size());
        assertEquals(SexyAppBase.CURSOR_POINTER, anApp.getCursor());
        assertEquals(0, mPlatform.mCalls, "headless helpers must not call the platform");

        // init() is where the window is created.
        anApp.mRendererBackend = new FakeRendererBackend().newProxy();
        anApp.mNoSoundNeeded = true;
        anApp.mAutoStartLoadingThread = false;
        anApp.init();

        assertTrue(mPlatform.mInitCalls >= 1, "init() creates the window through PlatformBackend");
        assertNotNull(mPlatform.mLastOptions);
        assertEquals(anApp.mWidth, mPlatform.mLastOptions.width());
        assertEquals(anApp.mHeight, mPlatform.mLastOptions.height());
        assertEquals(anApp.mTitle, mPlatform.mLastTitle);
        assertTrue(anApp.mInitialized);
        assertFalse(anApp.mShutdown.get());
        // DummyMusicInterface for mNoSoundNeeded (upstream CreateMusicInterface).
        assertNotNull(anApp.mMusicInterface);
        assertNull(anApp.mSoundManager, "mNoSoundNeeded opens no audio device");
        assertNull(anApp.mAudioEngine, "mNoSoundNeeded opens no audio device");
        assertTrue(anApp.mMusicInterface instanceof cn.enaium.fabricpvz.portable.sexy.sound.DummyMusicInterface);
    }

    @Test
    void lifecycleHooksFireOnInitAndShutdown() {
        SexyAppBase anApp = newApp();
        anApp.mRendererBackend = new FakeRendererBackend().newProxy();
        anApp.mNoSoundNeeded = true;
        final int[] anInitialized = new int[1];
        final int[] anExiting = new int[1];
        AppHooks aHook = new AppHooks() {
            @Override
            public void onGameInitialized() {
                anInitialized[0]++;
            }

            @Override
            public void onGameExiting() {
                anExiting[0]++;
            }
        };
        AppHooksRegistry.add(aHook);
        try {
            anApp.init();
            assertEquals(1, anInitialized[0]);

            anApp.shutdown();
            assertEquals(1, anExiting[0]);
            assertTrue(anApp.mShutdown.get());
        } finally {
            AppHooksRegistry.remove(aHook);
        }
    }

    @Test
    void fixedUpdateRateIsOneHundredUpdatesPerSecond() {
        SexyAppBase anApp = newApp();
        anApp.clearUpdateBacklog(false);

        for (int i = 0; i < 5; i++) {
            mPlatform.advance(10);
            anApp.updateFTimeAcc();

            anApp.mUpdateAppState = SexyAppBase.UPDATESTATE_PROCESS_1;
            assertTrue(anApp.process(false), "the pacer must keep running");
            assertEquals(SexyAppBase.UPDATESTATE_PROCESS_2, anApp.mUpdateAppState);

            anApp.process(false); // drain the frame
        }

        assertEquals(5, anApp.mUpdateCount, "10 ms per logical update == 100 Hz");
        assertEquals(0.0, anApp.mUpdateFTimeAcc, 1e-9);
        assertEquals(5, anApp.mNonDrawCount);
    }

    @Test
    void updateAccumulatorClampsAtTwoHundredMilliseconds() {
        SexyAppBase anApp = newApp();
        anApp.clearUpdateBacklog(false);

        mPlatform.advance(1000);
        anApp.updateFTimeAcc();

        assertEquals(200.0, anApp.mUpdateFTimeAcc, 1e-9);
    }

    @Test
    void clearUpdateBacklogRelaxForASecondDecaysWithElapsedTime() {
        SexyAppBase anApp = newApp();

        // Build up a backlog, then clear it: only the time after the reset accumulates again.
        anApp.clearUpdateBacklog(false);
        mPlatform.advance(500);
        anApp.updateFTimeAcc();
        assertEquals(200.0, anApp.mUpdateFTimeAcc, 1e-9);

        anApp.clearUpdateBacklog(true);
        assertEquals(0.0, anApp.mUpdateFTimeAcc, 1e-9, "the backlog was cleared");
        assertEquals(1000, anApp.mRelaxUpdateBacklogCount);

        mPlatform.advance(30);
        anApp.updateFTimeAcc();
        assertEquals(970, anApp.mRelaxUpdateBacklogCount);
        assertEquals(30.0, anApp.mUpdateFTimeAcc, 1e-9, "only the new frame accumulated");
    }

    @Test
    void vsynchedPacingRunsTheExtraUpdatesFromThePendingAccumulator() {
        SexyAppBase anApp = newApp();
        anApp.mIsPhysWindowed = false;
        anApp.mVSyncUpdates = true;
        anApp.mSyncRefreshRate = 50; // anUpdatesPerUpdateF = 1000 / (10 * 50) = 2
        anApp.mLastDrawWasEmpty = false;
        anApp.mVSyncBroken = false;
        anApp.mHasPendingDraw = true;
        anApp.clearUpdateBacklog(false);

        mPlatform.advance(20); // aFrameFTime = (1000 / 50) / 1 = 20 ms
        anApp.updateFTimeAcc();

        anApp.mUpdateAppState = SexyAppBase.UPDATESTATE_PROCESS_1;
        anApp.process(false);
        assertEquals(1, anApp.mUpdateCount);

        anApp.process(false); // PROCESS_2 drains mPendingUpdatesAcc
        assertEquals(2, anApp.mUpdateCount, "a 50 Hz frame owes two 100 Hz updates");
        assertEquals(0.0, anApp.mUpdateFTimeAcc, 1e-9);
    }

    @Test
    void nonDrawCountForcesADrawInsteadOfAnUpdate() {
        SexyAppBase anApp = newApp();
        anApp.mNonDrawCount = 9;
        anApp.mHasPendingDraw = true;
        anApp.clearUpdateBacklog(false);
        TestWidget aWidget = new TestWidget();
        aWidget.resize(0, 0, 50, 50);
        anApp.mWidgetManager.addWidget(aWidget);
        aWidget.markDirty();

        anApp.mUpdateAppState = SexyAppBase.UPDATESTATE_PROCESS_1;
        anApp.process(false);

        assertEquals(0, anApp.mUpdateCount, "the update is skipped to make room for a draw");
        assertEquals(0, anApp.mNonDrawCount, "the non-draw counter is reset by the skipped update");
        assertEquals(1, anApp.mDrawCount);
        assertFalse(anApp.mLastDrawWasEmpty);
        assertEquals(1, aWidget.mDraws);
        assertFalse(aWidget.mDirty);
        assertFalse(anApp.mHasPendingDraw);
    }

    @Test
    void processDoesNotSleepWhenSleepingIsNotAllowed() {
        SexyAppBase anApp = newApp();
        anApp.mNonDrawCount = 9;
        anApp.mHasPendingDraw = false;
        anApp.clearUpdateBacklog(false);

        anApp.mUpdateAppState = SexyAppBase.UPDATESTATE_PROCESS_1;
        assertFalse(anApp.process(false), "nothing to do and sleeping is not allowed");

        assertEquals(0, anApp.mSleepCount);
        assertEquals(0, mPlatform.mSleepCalls);
        assertEquals(SexyAppBase.UPDATESTATE_PROCESS_DONE, anApp.mUpdateAppState);
    }

    @Test
    void processSleepsWhenAllowed() {
        SexyAppBase anApp = newApp();
        anApp.mNonDrawCount = 9;
        anApp.mHasPendingDraw = false;
        anApp.clearUpdateBacklog(false);

        anApp.mUpdateAppState = SexyAppBase.UPDATESTATE_PROCESS_1;
        assertTrue(anApp.process(true));

        assertEquals(1, anApp.mSleepCount);
        assertEquals(1, mPlatform.mSleepCalls, "the pacer sleeps the remaining frame time");
    }

    @Test
    void updateStateMachineWalksMessagesProcessDone() {
        SexyAppBase anApp = newApp();
        anApp.mUpdateAppState = SexyAppBase.UPDATESTATE_PROCESS_DONE;
        anApp.mNonDrawCount = 9;
        anApp.mHasPendingDraw = false;
        anApp.clearUpdateBacklog(false);
        boolean[] updated = new boolean[1];

        // PROCESS_DONE -> MESSAGES -> PROCESS_1 (no messages pending, so no update yet)
        assertTrue(anApp.updateAppStep(updated));
        assertEquals(SexyAppBase.UPDATESTATE_PROCESS_1, anApp.mUpdateAppState);
        assertFalse(updated[0]);
        assertEquals(0, anApp.mUpdateAppDepth);

        // PROCESS_1 -> nothing due -> PROCESS_DONE (and sleeps because allowSleep is true there)
        assertTrue(anApp.updateAppStep(updated));
        assertEquals(SexyAppBase.UPDATESTATE_PROCESS_DONE, anApp.mUpdateAppState);
        assertFalse(updated[0]);
        assertEquals(0, anApp.mUpdateCount);
        assertEquals(0, anApp.mUpdateAppDepth);
    }

    @Test
    void updateAppStepReportsWhetherAnUpdateHappened() {
        SexyAppBase anApp = newApp();
        anApp.clearUpdateBacklog(false);
        boolean[] updated = new boolean[1];

        // First step only pumps messages and moves to PROCESS_1.
        anApp.mUpdateAppState = SexyAppBase.UPDATESTATE_PROCESS_DONE;
        anApp.updateAppStep(updated);
        assertFalse(updated[0]);

        // Second step runs Process with a full frame of accumulated time -> real update.
        mPlatform.advance(10);
        anApp.updateFTimeAcc();
        assertTrue(anApp.updateAppStep(updated));
        assertEquals(1, anApp.mUpdateCount);
        assertTrue(updated[0]);
    }

    @Test
    void drawDirtyStuffDrawsOnlyWhenSomethingIsDirtyAndClearsTheFlags() {
        SexyAppBase anApp = newApp();
        TestWidget aWidget = new TestWidget();
        aWidget.resize(0, 0, 50, 50);
        anApp.mWidgetManager.addWidget(aWidget);
        aWidget.markDirty();

        assertTrue(anApp.drawDirtyStuff());
        assertEquals(1, anApp.mDrawCount);
        assertEquals(1, aWidget.mDraws);
        assertFalse(aWidget.mDirty, "MarkDirty -> draw -> clean ordering");
        assertFalse(anApp.mLastDrawWasEmpty);
        assertFalse(anApp.mHasPendingDraw);

        // Nothing dirty and no forced redraw: the frame is an empty draw.
        assertFalse(anApp.drawDirtyStuff());
        assertEquals(1, anApp.mDrawCount);
        assertEquals(1, aWidget.mDraws);
        assertTrue(anApp.mLastDrawWasEmpty);
    }

    @Test
    void drawDirtyStuffPresentsOnlyWhenTheWidgetManagerDrew() {
        SexyAppBase anApp = newApp();
        FakeRendererBackend aRenderer = new FakeRendererBackend();
        anApp.mRendererBackend = aRenderer.newProxy();
        TestWidget aWidget = new TestWidget();
        aWidget.resize(0, 0, 50, 50);
        anApp.mWidgetManager.addWidget(aWidget);
        aWidget.markDirty();

        assertTrue(anApp.drawDirtyStuff());
        assertEquals(1, aRenderer.mPresents);

        assertFalse(anApp.drawDirtyStuff());
        assertEquals(1, aRenderer.mPresents, "an empty frame must not present");
    }

    @Test
    void registryAccessIsInertWithoutARegistryKey() {
        SexyAppBase anApp = newApp();
        // The default key is the game's own (options have to persist); an explicitly empty key is what
        // upstream does when the program strings carry no RegistryKey entry.
        anApp.mRegKey = "";

        assertFalse(anApp.registryWriteInteger("MusicVolume", 50));
        int[] anInt = new int[1];
        assertFalse(anApp.registryReadInteger("MusicVolume", anInt));
        String[] aString = new String[1];
        assertFalse(anApp.registryReadString("Name", aString));
        assertFalse(anApp.registryEraseKey("Sub"));
        anApp.registryEraseValue("Value");

        // WriteToRegistry is equally inert while mRegKey is empty.
        anApp.writeToRegistry();
        assertEquals(0, mPlatform.mCalls);
    }

    @Test
    void fileHelpersRoundTripThroughTheFilesystemWithoutInit(@TempDir Path theDir) throws IOException {
        SexyAppBase anApp = newApp();
        Path aFile = theDir.resolve("props/default.xml");
        anApp.setString("PROP", "x");

        String aPath = aFile.toString();
        assertTrue(anApp.writeBytesToFile(aPath, "<ResourceManifest/>".getBytes("UTF-8"),
                "<ResourceManifest/>".getBytes("UTF-8").length));
        assertTrue(Files.exists(aFile));
        assertTrue(anApp.fileExists(aPath));

        String[] aString = new String[1];
        assertTrue(anApp.readUtf8StringFromFile(aPath, aString));
        assertEquals("<ResourceManifest/>", aString[0]);
        assertEquals("<ResourceManifest/>", anApp.readUtf8StringFromFile(aPath));

        Buffer aBuffer = new Buffer();
        assertTrue(anApp.readBufferFromFile(aPath, aBuffer));
        assertEquals(19, aBuffer.getDataLen());
        assertTrue(anApp.writeBufferToFile(theDir.resolve("out.bin").toString(), aBuffer));

        assertTrue(anApp.eraseFile(aPath));
        assertFalse(anApp.fileExists(aPath));
        assertNull(anApp.readUtf8StringFromFile(aPath));

        assertEquals(0, mPlatform.mCalls, "file helpers never touch the platform");
    }

    @Test
    void clipboardAndCursorRoutingGoesThroughThePlatformBackend() {
        SexyAppBase anApp = newApp();

        anApp.copyToClipboard("hello");
        assertEquals("", anApp.getClipboard());
        assertEquals(2, mPlatform.mCalls);

        anApp.setCursor(SexyAppBase.CURSOR_NONE);
        assertEquals(SexyAppBase.CURSOR_NONE, anApp.getCursor());
        assertTrue(mPlatform.mCalls >= 3);

        anApp.setCursor(SexyAppBase.CURSOR_HAND);
        assertEquals(SexyAppBase.CURSOR_HAND, anApp.getCursor());
    }

    @Test
    void demoModeUsesTheUpdateCountAsItsClock() {
        SexyAppBase anApp = newApp();
        anApp.mRecordingDemoBuffer = true;
        anApp.mDemoStartTime = 1_700_000_000L;
        anApp.mUpdateCount = 250;

        assertTrue(anApp.isInDemoMode());
        assertEquals(1_700_000_000L + 2, anApp.getNowTime(), "2.5 s of updates -> 2 whole seconds");

        anApp.mRecordingDemoBuffer = false;
        assertFalse(anApp.isInDemoMode());
        assertTrue(anApp.getNowTime() > 1_700_000_000L);
    }

    @Test
    void sharedImagesAreKeyedCaseInsensitivelyAndCleanedOnRequest() {
        SexyAppBase anApp = newApp();
        Image aFirst = new TestImage(4, 4);
        boolean[] isNew = new boolean[1];

        anApp.setSharedImage("Reanim/Head.png", "Variant", new SharedImageRef(aFirst));

        SharedImageRef aLookup = anApp.getSharedImage("REANIM/HEAD.PNG", "VARIANT", isNew);
        assertFalse(isNew[0], "the key is upper-cased, so this is the same entry");
        assertSame(aFirst, aLookup.get());

        // A leading '!' creates a blank GLImage instead of loading from disk.
        boolean[] isNew2 = new boolean[1];
        SharedImageRef aBlank = anApp.getSharedImage("!blank", "", isNew2);
        assertTrue(isNew2[0]);
        assertNotNull(aBlank.get());

        anApp.mCleanupSharedImages.set(true);
        anApp.cleanSharedImages();
        assertFalse(anApp.mCleanupSharedImages.get());
        assertEquals(0, anApp.mSharedImageMap.size());
        assertNull(aLookup.get(), "released handles no longer hold an image");

        anApp.cleanSharedImages(); // idempotent when no cleanup was requested
    }

    @Test
    void memoryImageRegistryTracksRegisteredImages() {
        SexyAppBase anApp = newApp();
        MemoryImage anImage = new MemoryImage(anApp);

        anApp.addMemoryImage(anImage);
        assertEquals(1, anApp.mMemoryImageSet.size());

        anApp.removeMemoryImage(anImage);
        assertEquals(0, anApp.mMemoryImageSet.size());
    }

    @Test
    void shaderFreeMiscHooksReturnTheUpstreamDefaults() {
        SexyAppBase anApp = newApp();

        assertTrue(anApp.is3DAccelerated());
        assertTrue(anApp.is3DAccelerationSupported());
        assertTrue(anApp.is3DAccelerationRecommended());
        assertTrue(anApp.appCanRestore());
        assertFalse(anApp.isScreenSaver());
        assertFalse(anApp.debugKeyDown((int) 'A'));
        assertEquals("0", anApp.getProductVersion("/x"));
        assertEquals("", anApp.notifyCrashHook());
        assertFalse(anApp.startTextInput(new StringBuilder()));
        assertEquals("", anApp.getClipboard()); // not in demo playback
    }

    /**
     * The stored screen mode has to reach the window creation itself: upstream asks for
     * SDL_WINDOW_FULLSCREEN_DESKTOP at SDL_CreateWindow (platform/default/Window.cpp:60), otherwise a
     * saved fullscreen setting only takes effect after the player toggles it once.
     */
    @Test
    void windowIsCreatedFullscreenForAStoredFullscreenMode(@TempDir Path theTempDir) throws IOException {
        String aPreviousFolder = Common.getAppDataFolder();
        Common.setAppDataFolder(Files.createDirectories(theTempDir.resolve("appdata")).toString());
        try {
        SexyAppBase aWindowed = newApp();
        aWindowed.mRendererBackend = new FakeRendererBackend().newProxy();
        aWindowed.mNoSoundNeeded = true;
        aWindowed.init();
        assertNotNull(mPlatform.mLastOptions);
        assertFalse(mPlatform.mLastOptions.fullscreen(), "the default windowed mode must not ask for fullscreen");

        SexyAppBase aFullscreen = newApp();
        aFullscreen.mRendererBackend = new FakeRendererBackend().newProxy();
        aFullscreen.mNoSoundNeeded = true;
        // What readFromRegistry() yields for a stored ScreenMode of 1
        // (SexyAppBase writes ScreenMode as mIsWindowed ? 0 : 1 and reads it back inverted).
        aFullscreen.mIsWindowed = false;
        aFullscreen.init();
        assertTrue(mPlatform.mLastOptions.fullscreen(),
                "a fullscreen screen mode must be requested when the window is created");
        } finally {
            Common.setAppDataFolder(aPreviousFolder);
        }
    }

    /**
     * Options have to survive a restart: everything the options dialog touches goes through
     * {@code registryWrite*} into the emulated registry, which only persists once
     * {@code ReadFromRegistry} pointed it at a file - an empty registry key skipped that and every
     * write was dropped (close fullscreen, restart, still fullscreen).
     */
    @Test
    void optionsPersistAcrossRestarts(@TempDir Path theTempDir) throws IOException {
        Path aDir = Files.createDirectories(theTempDir.resolve("appdata"));
        String aPreviousFolder = Common.getAppDataFolder();
        try {
            Common.setAppDataFolder(aDir.toString());

            SexyAppBase aFirst = newApp();
            aFirst.mRendererBackend = new FakeRendererBackend().newProxy();
            aFirst.mNoSoundNeeded = true;
            aFirst.init();
            aFirst.mIsWindowed = true;
            aFirst.writeToRegistry();
            aFirst.shutdown();

            Path aRegistry = aDir.resolve("registry.regemu");
            assertTrue(Files.exists(aRegistry), "the registry file must be written into the app data folder");

            SexyAppBase aSecond = newApp();
            aSecond.mRendererBackend = new FakeRendererBackend().newProxy();
            aSecond.mNoSoundNeeded = true;
            aSecond.init();
            assertTrue(aSecond.mIsWindowed,
                    "the screen mode written by the first run must be read back by the next one");
        } finally {
            Common.setAppDataFolder(aPreviousFolder);
        }
    }

    /**
     * A window resize must not change the logical resolution: the renderer keeps drawing the fixed
     * logical canvas and only the presentation rect moves (upstream Input.cpp:611-646 re-runs
     * WidgetManager::Resize with the new rect and leaves mWidth/mHeight alone). Screens opened later
     * are laid out for the logical canvas - following the window size would put them outside it.
     */
    @Test
    void aWindowResizeKeepsTheLogicalCanvasAndRemapsThePointer() {
        SexyAppBase anApp = newApp();
        anApp.mWidth = 800;
        anApp.mHeight = 600;
        FakeRendererBackend aRenderer = new FakeRendererBackend();
        aRenderer.mWidth = 1470;
        aRenderer.mHeight = 895;
        aRenderer.mLogicalWidth = 800;
        aRenderer.mLogicalHeight = 600;
        // 800x600 (4:3) pillarboxed into a 1470x895 window: scale 1.49, 139px bars each side
        aRenderer.mPresentationRect = new int[] {139, 0, 1193, 895};
        anApp.mRendererBackend = aRenderer.newProxy();
        anApp.mNoSoundNeeded = true;
        anApp.init();

        PlatformEvent aResize = new PlatformEvent();
        aResize.type = PlatformEvent.Type.WINDOW_RESIZE;
        aResize.width = 1470;
        aResize.height = 895;
        mPlatform.mEvents.add(aResize);
        anApp.processDeferredMessages(false);

        assertEquals(800, anApp.mWidth, "the logical resolution must not follow the window");
        assertEquals(600, anApp.mHeight);
        assertEquals(800, anApp.mWidgetManager.mWidth, "widgets live in the logical canvas");

        // The drawn banner centre in window coordinates: 139 + 396 * 1193/800, 559 * 895/600
        PlatformEvent aClick = new PlatformEvent();
        aClick.type = PlatformEvent.Type.MOUSE_MOTION;
        aClick.x = 139 + 396 * 1193 / 800;
        aClick.y = 559 * 895 / 600;
        mPlatform.mEvents.add(aClick);
        anApp.processDeferredMessages(false);

        assertTrue(Math.abs(anApp.mWidgetManager.mLastMouseX - 396) <= 2,
                "the drawn position must map back to the logical x, got " + anApp.mWidgetManager.mLastMouseX);
        assertTrue(Math.abs(anApp.mWidgetManager.mLastMouseY - 559) <= 2,
                "the drawn position must map back to the logical y, got " + anApp.mWidgetManager.mLastMouseY);
    }

    /**
     * Mouse events arrive in window coordinates; the widget manager has to remap them into the logical
     * space the widgets are laid out in (upstream resizes it with {@code mGLInterface->mPresentationRect}).
     * Without that remap the pointer drifts away from the art as soon as the window is scaled - which is
     * exactly what a fullscreen window is.
     */
    @Test
    void mouseEventsAreMappedFromTheWindowIntoTheLogicalSpace() {
        SexyAppBase anApp = newApp();
        anApp.mWidth = 800;
        anApp.mHeight = 600;
        FakeRendererBackend aRenderer = new FakeRendererBackend();
        aRenderer.mWidth = 1600;
        aRenderer.mHeight = 900;
        aRenderer.mLogicalWidth = 800;
        aRenderer.mLogicalHeight = 600;
        // 800x600 (4:3) letterboxed into a 1600x900 (16:9) window: scale 1.5, 200px pillarbox each side
        aRenderer.mPresentationRect = new int[] {200, 0, 1200, 900};
        anApp.mRendererBackend = aRenderer.newProxy();
        anApp.mNoSoundNeeded = true;
        anApp.init();

        PlatformEvent aMove = new PlatformEvent();
        aMove.type = PlatformEvent.Type.MOUSE_MOTION;
        aMove.x = 800; // window centre, inside the pillarboxed canvas
        aMove.y = 450;
        mPlatform.mEvents.add(aMove);
        anApp.processDeferredMessages(false);

        // (800 - 200) / 1.5 = 400, (450 - 0) / 1.5 = 300: the logical centre
        assertEquals(400, anApp.mWidgetManager.mLastMouseX, "the window centre must map to the logical centre");
        assertEquals(300, anApp.mWidgetManager.mLastMouseY);
    }

    /**
     * A click that arrives while the window is unfocused has to reach the widget tree. The only click
     * the OS and SDL may swallow is the one that activates the window (SDL drops it unless
     * {@code SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH} is set, see {@link
     * cn.enaium.fabricpvz.portable.pvzp.platform.sdl.SdlPlatformTest}), so once the backend hands a
     * press over, the framework must deliver it: dropping or deferring clicks while the window is
     * unfocused is what makes the first click after focusing the window do nothing and a second click
     * necessary for the press to register.
     */
    @Test
    void clickArrivingWhileTheWindowIsUnfocusedIsDeliveredToTheWidget() {
        SexyAppBase anApp = newApp();
        anApp.mRendererBackend = new FakeRendererBackend().newProxy();
        anApp.mNoSoundNeeded = true;
        anApp.init();
        TestWidget aWidget = new TestWidget();
        aWidget.resize(0, 0, 640, 480);
        anApp.mWidgetManager.addWidget(aWidget);

        // The user is in another application: the window loses focus, then the click on it arrives.
        PlatformEvent aFocusLost = new PlatformEvent();
        aFocusLost.type = PlatformEvent.Type.WINDOW_FOCUS_LOST;
        PlatformEvent aDown = new PlatformEvent();
        aDown.type = PlatformEvent.Type.MOUSE_DOWN;
        aDown.x = 10;
        aDown.y = 10;
        aDown.button = 1; // platform button id: 1 = left
        PlatformEvent aUp = new PlatformEvent();
        aUp.type = PlatformEvent.Type.MOUSE_UP;
        aUp.x = 10;
        aUp.y = 10;
        aUp.button = 1;
        mPlatform.mEvents.add(aFocusLost);
        mPlatform.mEvents.add(aDown);
        mPlatform.mEvents.add(aUp);

        anApp.processDeferredMessages(false);

        assertFalse(anApp.mActive, "the click arrives while the window is unfocused");
        assertEquals(1, aWidget.mMouseDowns, "the press reaches the widget");
        assertEquals(1, aWidget.mMouseUps, "the release reaches the widget");
    }
}
