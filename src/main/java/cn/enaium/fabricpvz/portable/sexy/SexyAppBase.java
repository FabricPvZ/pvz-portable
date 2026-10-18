package cn.enaium.fabricpvz.portable.sexy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import cn.enaium.fabricpvz.portable.pvzp.audio.AudioEngine;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformBackend;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformEvent;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;
import cn.enaium.fabricpvz.portable.sexy.graphics.SharedImageRef;
import cn.enaium.fabricpvz.portable.sexy.hooks.AppHooksRegistry;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.sexy.parsers.PropertiesParser;
import cn.enaium.fabricpvz.portable.sexy.sound.DummyMusicInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.MusicInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Ported from src/SexyAppFramework/SexyAppBase.h:136 (implementation
 * src/SexyAppFramework/SexyAppBase.cpp).
 *
 * <p>The framework application object: lifecycle, the 100 Hz frame pacer, the update state machine,
 * the property maps, registry/file helpers, the shared-image map, the loading thread, cursors,
 * clipboard, dialogs and the ~40 overridable hooks. Public fields and the virtual-method names keep
 * their upstream spelling (camelCased where upstream used PascalCase).
 *
 * <p>Constructor contract ({@code ../verification/research/01-framework-core.md} §8.2): the constructor only
 * initialises fields and sub-objects. No window, no renderer/audio device, no filesystem access and
 * no thread is started here; all platform work happens in {@link #init()} / {@link #start()}.
 * {@link #current} (the {@code gSexyAppBase} equivalent) is assigned here, as upstream does, and is
 * never read by the constructor.
 */
public class SexyAppBase implements ButtonListener, DialogListener {
    /** Upstream {@code gSexyAppBase} (SexyAppBase.h:627). */
    public static SexyAppBase current;

    // Cursor numbers (SexyAppBase.h:52-68)
    public static final int CURSOR_POINTER = 0;
    public static final int CURSOR_HAND = 1;
    public static final int CURSOR_DRAGGING = 2;
    public static final int CURSOR_TEXT = 3;
    public static final int CURSOR_CIRCLE_SLASH = 4;
    public static final int CURSOR_SIZEALL = 5;
    public static final int CURSOR_SIZENESW = 6;
    public static final int CURSOR_SIZENS = 7;
    public static final int CURSOR_SIZENWSE = 8;
    public static final int CURSOR_SIZEWE = 9;
    public static final int CURSOR_WAIT = 10;
    public static final int CURSOR_NONE = 11;
    public static final int CURSOR_CUSTOM = 12;
    public static final int NUM_CURSORS = 13;

    // Demo command ids (SexyAppBase.h:71-100)
    public static final int DEMO_MOUSE_POSITION = 0;
    public static final int DEMO_ACTIVATE_APP = 1;
    public static final int DEMO_SIZE = 2;
    public static final int DEMO_KEY_DOWN = 3;
    public static final int DEMO_KEY_UP = 4;
    public static final int DEMO_KEY_CHAR = 5;
    public static final int DEMO_CLOSE = 6;
    public static final int DEMO_MOUSE_ENTER = 7;
    public static final int DEMO_MOUSE_EXIT = 8;
    public static final int DEMO_LOADING_COMPLETE = 9;
    public static final int DEMO_REGISTRY_GETSUBKEYS = 10;
    public static final int DEMO_REGISTRY_READ = 11;
    public static final int DEMO_REGISTRY_WRITE = 12;
    public static final int DEMO_REGISTRY_ERASE = 13;
    public static final int DEMO_FILE_EXISTS = 14;
    public static final int DEMO_FILE_READ = 15;
    public static final int DEMO_FILE_WRITE = 16;
    public static final int DEMO_HTTP_RESULT = 17;
    public static final int DEMO_SYNC = 18;
    public static final int DEMO_ASSERT_STRING_EQUAL = 19;
    public static final int DEMO_ASSERT_INT_EQUAL = 20;
    public static final int DEMO_MOUSE_WHEEL = 21;
    public static final int DEMO_HANDLE_COMPLETE = 22;
    public static final int DEMO_VIDEO_DATA = 23;
    public static final int DEMO_KEY_TEXT = 24;
    public static final int DEMO_IDLE = 31;

    // FPS modes (SexyAppBase.h:102-107)
    public static final int FPS_ShowFPS = 0;
    public static final int FPS_ShowCoords = 1;
    public static final int Num_FPS_Types = 2;

    // Update state machine (SexyAppBase.h:109-115)
    public static final int UPDATESTATE_MESSAGES = 0;
    public static final int UPDATESTATE_PROCESS_1 = 1;
    public static final int UPDATESTATE_PROCESS_2 = 2;
    public static final int UPDATESTATE_PROCESS_DONE = 3;

    /** SexyAppBase.cpp:85. */
    public static final int DEMO_FILE_ID = 0x42BEEF78;
    /** SexyAppBase.cpp:86 — v6: text input is recorded as UTF-8 (DEMO_KEY_TEXT). */
    public static final int DEMO_VERSION = 6;

    /** Upstream {@code WidgetSafeDeleteInfo} (SexyAppBase.h:43). */
    public static class WidgetSafeDeleteInfo {
        public int mUpdateAppDepth;
        public Widget mWidget;
    }

    /** Upstream {@code DemoMarker = std::pair<std::string, uint32_t>} (SexyAppBase.h:319). */
    public static class DemoMarker {
        public String first;
        public int second;

        public DemoMarker() {
        }

        public DemoMarker(String theString, int theUpdateCount) {
            first = theString;
            second = theUpdateCount;
        }
    }

    public Object mWindow;
    public Object mContext;
    public Object mSurface; // for EGL

    public int mRandSeed;

    public String mCompanyName = "";
    public String mFullCompanyName = "";
    public String mProdName = "";
    public String mTitle = "";
    /**
     * Registry key the emulated registry stores its values under (upstream
     * {@code SexyAppBase::mRegKey}, assigned in {@code ReadFromRegistry} from the program string
     * {@code RegistryKey}).
     *
     * <p>The retail string tables carry no {@code RegistryKey} entry, so the fallback matters: with an
     * empty key {@link #readFromRegistry()} returns early, {@link RegEmu#setRegFile} is never called and
     * every {@code registryWrite*} silently drops its value - screen mode, volumes and mute state never
     * survived a restart. The value matches the key the port's existing {@code registry.regemu} files
     * were written with, so old profiles keep working.
     */
    public String mRegKey = "PopCap\\PlantsVsZombies";
    public String mResourceDir = "";
    public String mCustomSaveDir = "";

    public int mRelaxUpdateBacklogCount;
    public int mPreferredX;
    public int mPreferredY;
    public int mWidth;
    public int mHeight;
    public int mFullscreenBits;
    public double mMusicVolume;
    public double mSfxVolume;
    public double mDemoMusicVolume;
    public double mDemoSfxVolume;
    public boolean mNoSoundNeeded;
    public boolean mWantFMod;
    public boolean mCmdLineParsed;
    public int mArgc;
    public String[] mArgv;
    public boolean mSkipSignatureChecks;
    public boolean mStandardWordWrap;
    public boolean mbAllowExtendedChars;

    public boolean mOnlyAllowOneCopyToRun;
    public int mNotifyGameMessage;
    public final int[] mAdd8BitMaxTable = new int[512];
    public WidgetManager mWidgetManager;
    public final Map<Integer, Dialog> mDialogMap = new LinkedHashMap<>();
    public final List<Dialog> mDialogList = new ArrayList<>();
    public long mPrimaryThreadId;
    public Thread mLoadingThread;
    public boolean mSEHOccured;
    public final AtomicBoolean mShutdown = new AtomicBoolean(false);
    public boolean mExitToTop;
    public boolean mIsWindowed;
    public boolean mIsPhysWindowed;
    public boolean mFullScreenWindow;
    public boolean mForceFullscreen;
    public boolean mForceWindowed;
    public boolean mInitialized;
    public boolean mProcessInTimer;
    public int mTimeLoaded;
    public boolean mIsScreenSaver;
    public boolean mAllowMonitorPowersave;
    public boolean mNoDefer;
    public boolean mFullScreenPageFlip;
    public boolean mTabletPC;
    /** Must outlive the renderer: {@code ~GLImage -> removeMemoryImage()}. */
    public final Set<MemoryImage> mMemoryImageSet = new LinkedHashSet<>();
    public RendererBackend mRendererBackend;
    public PlatformBackend mPlatformBackend;
    public boolean mAlphaDisabled;
    public MusicInterface mMusicInterface;
    public SoundManager mSoundManager;
    /** Audio facade owning the device, mixer and music stream (upstream SDL audio init). */
    public AudioEngine mAudioEngine;
    public boolean mReadFromRegistry;
    public String mRegisterLink = "";
    public String mProductVersion = "";
    public final Image[] mCursorImages = new Image[NUM_CURSORS];
    public boolean mIsOpeningURL;
    public boolean mShutdownOnURLOpen;
    public String mOpeningURL = "";
    public int mOpeningURLTime;
    public int mLastTimerTime;
    public int mLastBigDelayTime;
    public double mUnmutedMusicVolume;
    public double mUnmutedSfxVolume;
    public int mMuteCount;
    public int mAutoMuteCount;
    public boolean mDemoMute;
    public boolean mMuteOnLostFocus;
    public final Map<String, SharedImageRef> mSharedImageMap = new HashMap<>();
    public final AtomicBoolean mCleanupSharedImages = new AtomicBoolean(false);

    public int mNonDrawCount;
    public int mFrameTime;

    public boolean mIsDrawing;
    public boolean mLastDrawWasEmpty;
    public boolean mHasPendingDraw;
    public double mPendingUpdatesAcc;
    public double mUpdateFTimeAcc;
    public long mLastTimeCheck;
    public long mLastTime;
    public int mLastUserInputTick;

    public int mSleepCount;
    public int mDrawCount;
    public int mUpdateCount;
    public int mStartTick;
    public int mUpdateAppState;
    public int mUpdateAppDepth;
    public double mUpdateMultiplier;
    public boolean mPaused;
    public int mFastForwardToUpdateNum;
    public boolean mFastForwardToMarker;
    public boolean mFastForwardStep;
    public int mLastDrawTick;
    public int mNextDrawTick;
    public int mStepMode; // 0 = off, 1 = step, 2 = waiting for step

    public int mCursorNum;
    public Image mCustomCursorImage;
    public int mCustomCursorImageNum;
    /** app-injected fallback for widgets without an explicit font (upstream {@code std::atomic<_Font*>}). */
    public volatile Font mDefaultFont;
    public final List<WidgetSafeDeleteInfo> mSafeDeleteList = new ArrayList<>();
    public boolean mMouseIn;
    public boolean mRunning;
    public boolean mActive;
    public boolean mMinimized;
    public boolean mPhysMinimized;
    public boolean mIsDisabled;
    public boolean mHasFocus;
    public long mDrawTime;
    public boolean mShowFPS;
    public int mShowFPSMode;
    public int mScreenBltTime;
    public boolean mAutoStartLoadingThread;
    public final AtomicBoolean mLoadingThreadStarted = new AtomicBoolean(false);
    public final AtomicBoolean mLoadingThreadCompleted = new AtomicBoolean(false);
    public boolean mLoaded;
    public boolean mYieldMainThread;
    public final AtomicBoolean mLoadingFailed = new AtomicBoolean(false);
    public boolean mCursorThreadRunning;
    public boolean mSysCursor;
    public boolean mCustomCursorsEnabled;
    public boolean mCustomCursorDirty;
    public boolean mLastShutdownWasGraceful;
    public boolean mIsWideWindow;
    public boolean mWriteToSexyCache;
    public boolean mSexyCacheBuffers;

    public final AtomicInteger mNumLoadingThreadTasks = new AtomicInteger(0);
    public final AtomicInteger mCompletedLoadingThreadTasks = new AtomicInteger(0);

    public boolean mRecordingDemoBuffer;
    public boolean mPlayingDemoBuffer;
    public boolean mManualShutdown;
    public String mDemoPrefix = "";
    public String mDemoFileName = "";
    public boolean mHasCustomDemoFile;
    public int mDemoRecordFileLimit;
    public int mDemoPlayIndex;
    public final Buffer mDemoBuffer = new Buffer();
    public int mLastDemoMouseX;
    public int mLastDemoMouseY;
    public int mLastDemoUpdateCnt;
    public long mDemoStartTime;
    public int mDemoTimeZoneOffset;
    public boolean mDemoNeedsCommand;
    public boolean mDemoIsShortCmd;
    public int mDemoCmdNum;
    public int mDemoCmdBitPos;
    public int mDemoCmdUpdateCnt;
    public int mDemoQueuedSince;
    public boolean mDemoCommandQueued;
    public boolean mDemoLoadingComplete;
    public final List<DemoMarker> mDemoMarkerList = new ArrayList<>();

    public boolean mDebugKeysEnabled;
    public boolean mEnableMaximizeButton;
    public boolean mCtrlDown;
    public boolean mAltDown;
    public boolean mAllowAltEnter;

    public int mSyncRefreshRate;
    public boolean mVSyncUpdates;
    public boolean mVSyncBroken;
    public int mVSyncBrokenCount;
    public int mVSyncBrokenTestStartTick;
    public int mVSyncBrokenTestUpdates;
    public boolean mWaitForVSync;
    public boolean mSoftVSyncWait;
    public boolean mUserChanged3DSetting;
    public boolean mAutoEnable3D;
    public boolean mTest3D;
    public int mMinVidMemory3D;
    public int mRecommendedVidMemory3D;

    public boolean mWidescreenAware;
    public Rect mScreenBounds;
    public boolean mEnableWindowAspect;
    public Ratio mWindowAspect;

    public final Map<String, String> mStringProperties = new HashMap<>();
    public final Map<String, Boolean> mBoolProperties = new HashMap<>();
    public final Map<String, Integer> mIntProperties = new HashMap<>();
    public final Map<String, Double> mDoubleProperties = new HashMap<>();
    public final Map<String, List<String>> mStringVectorProperties = new HashMap<>();
    public ResourceManager mResourceManager;

    // SexyAppBase::SexyAppBase (SexyAppBase.cpp:163)
    public SexyAppBase() {
        current = this;

        mNotifyGameMessage = 0;
        mOnlyAllowOneCopyToRun = true;
        mResourceDir = "";

        mNoDefer = false;
        mFullScreenPageFlip = true; // should we page flip in fullscreen?
        mTimeLoaded = 0;
        mSEHOccured = false;
        mProdName = "Product";
        mTitle = "SexyApp";
        mShutdown.set(false);
        mExitToTop = false;
        mWidth = 640;
        mHeight = 480;
        mFullscreenBits = 16;
        mIsWindowed = true;
        mIsPhysWindowed = true;
        mFullScreenWindow = false;
        mPreferredX = -1;
        mPreferredY = -1;
        mIsScreenSaver = false;
        mAllowMonitorPowersave = true;
        mFrameTime = 10;
        mNonDrawCount = 0;
        mDrawCount = 0;
        mSleepCount = 0;
        mUpdateCount = 0;
        mStartTick = 0;
        mUpdateAppState = 0;
        mUpdateAppDepth = 0;
        mPendingUpdatesAcc = 0.0;
        mUpdateFTimeAcc = 0.0;
        mHasPendingDraw = true;
        mIsDrawing = false;
        mLastDrawWasEmpty = false;
        mLastTimeCheck = 0;
        mUpdateMultiplier = 1;
        mPaused = false;
        mFastForwardToUpdateNum = 0;
        mFastForwardToMarker = false;
        mFastForwardStep = false;
        mCustomCursorImage = null;
        mCustomCursorImageNum = -1;
        mCursorNum = CURSOR_POINTER;
        mMouseIn = false;
        mRunning = false;
        mActive = true;
        mProcessInTimer = false;
        mMinimized = false;
        mPhysMinimized = false;
        mIsDisabled = false;
        mLoaded = false;
        mYieldMainThread = false;
        mLoadingFailed.set(false);
        mLoadingThreadStarted.set(false);
        mAutoStartLoadingThread = true;
        mLoadingThreadCompleted.set(false);
        mCursorThreadRunning = false;
        mNumLoadingThreadTasks.set(0);
        mCompletedLoadingThreadTasks.set(0);
        mLastDrawTick = 0;
        mNextDrawTick = 0;
        mSysCursor = true;
        mForceFullscreen = false;
        mForceWindowed = false;
        mHasFocus = true;
        mCustomCursorsEnabled = false;
        mCustomCursorDirty = false;
        mIsOpeningURL = false;
        mInitialized = false;
        mLastShutdownWasGraceful = true;
        mReadFromRegistry = false;
        mCmdLineParsed = false;
        mSkipSignatureChecks = false;
        mCtrlDown = false;
        mAltDown = false;
        mAllowAltEnter = true;
        mStepMode = 0;
        mCleanupSharedImages.set(false);
        mStandardWordWrap = true;
        mbAllowExtendedChars = true;
        mEnableMaximizeButton = false;
        mWriteToSexyCache = true;
        mSexyCacheBuffers = false;

        mMusicVolume = 0.85;
        mSfxVolume = 0.85;
        mDemoMusicVolume = 0.0;
        mDemoSfxVolume = 0.0;
        mMuteCount = 0;
        mAutoMuteCount = 0;
        mDemoMute = false;
        mMuteOnLostFocus = false;
        mShowFPS = false;
        mShowFPSMode = FPS_ShowFPS;
        mDrawTime = 0;
        mScreenBltTime = 0;
        mAlphaDisabled = false;
        mDebugKeysEnabled = false;
        mNoSoundNeeded = false;
        mWantFMod = false;

        mSyncRefreshRate = 100;
        mVSyncUpdates = false;
        mVSyncBroken = false;
        mVSyncBrokenCount = 0;
        mVSyncBrokenTestStartTick = 0;
        mVSyncBrokenTestUpdates = 0;
        mWaitForVSync = false;
        mSoftVSyncWait = true;
        mUserChanged3DSetting = false;
        mAutoEnable3D = false;
        mArgc = 0;
        mArgv = null;
        mTest3D = false;
        mMinVidMemory3D = 6;
        mRecommendedVidMemory3D = 14;
        mRelaxUpdateBacklogCount = 0;
        mWidescreenAware = false;
        mEnableWindowAspect = false;
        mWindowAspect = new Ratio(4, 3);
        mIsWideWindow = false;

        mWindow = null;
        mContext = null;
        mSurface = null;

        for (int i = 0; i < NUM_CURSORS; i++) {
            mCursorImages[i] = null;
        }

        for (int i = 0; i < 256; i++) {
            mAdd8BitMaxTable[i] = i;
        }

        for (int i = 256; i < 512; i++) {
            mAdd8BitMaxTable[i] = 255;
        }

        // Set default strings.  Init could read in overrides from partner.xml
        setString("DIALOG_BUTTON_OK", "OK");
        setString("DIALOG_BUTTON_CANCEL", "CANCEL");

        setString("UPDATE_CHECK_TITLE", "Update Check");
        setString("UPDATE_CHECK_BODY",
                "Checking if there are any updates available for this product ...");

        setString("UP_TO_DATE_TITLE", "Up to Date");
        setString("UP_TO_DATE_BODY",
                "There are no updates available for this product at this time.");
        setString("NEW_VERSION_TITLE", "New Version");
        setString("NEW_VERSION_BODY",
                "There is an update available for this product.  Would you like to visit the web site to download it?");

        mDemoPrefix = "sexyapp";
        mDemoFileName = mDemoPrefix + ".dmo";
        mHasCustomDemoFile = false;
        mDemoRecordFileLimit = 0;
        mDemoPlayIndex = 0;
        mPlayingDemoBuffer = false;
        mManualShutdown = false;
        mRecordingDemoBuffer = false;
        mLastDemoMouseX = 0;
        mLastDemoMouseY = 0;
        mLastDemoUpdateCnt = 0;
        mDemoStartTime = 0;
        mDemoTimeZoneOffset = 0;
        mDemoNeedsCommand = true;
        mDemoLoadingComplete = false;
        mDemoCmdNum = 0;
        mDemoCmdBitPos = 0;
        mDemoCmdUpdateCnt = 0;
        mDemoQueuedSince = 0;
        mDemoCommandQueued = false;

        mWidgetManager = new WidgetManager(this);
        mResourceManager = new ResourceManager(this);

        mPrimaryThreadId = Thread.currentThread().threadId();

        mTabletPC = false;
    }

    /**
     * Ported from {@code SexyAppBase::~SexyAppBase} (SexyAppBase.cpp:403). Java has no destructors;
     * this is the explicit teardown the application driver calls once at the end of {@link #start()}
     * (upstream runs it when the object is destroyed).
     */
    public void dispose() {
        shutdown();
        waitForLoadingThread();

        for (Dialog aDialog : new ArrayList<>(mDialogMap.values())) {
            mWidgetManager.removeWidget(aDialog);
            aDialog.dispose();
        }
        mDialogMap.clear();
        mDialogList.clear();

        if (mAudioEngine != null) {
            mAudioEngine.close();
            mAudioEngine = null;
            mSoundManager = null;
            mMusicInterface = null;
        }

        // Java is garbage collected: dropping the map's strong references is the whole cleanup
        // (upstream deletes the GLImages whose SharedImage refcount reached 0).
        mSharedImageMap.clear();

        resetCustomCursorCache();

        current = null;

        writeDemoBuffer();
    }

    // SexyAppBase::ClearUpdateBacklog (SexyAppBase.cpp:447)
    public void clearUpdateBacklog(boolean relaxForASecond) {
        mLastTimeCheck = ticks();
        mUpdateFTimeAcc = 0.0;

        if (relaxForASecond) {
            mRelaxUpdateBacklogCount = 1000;
        }
    }

    // SexyAppBase::IsScreenSaver (SexyAppBase.cpp:456)
    public boolean isScreenSaver() {
        return mIsScreenSaver;
    }

    // SexyAppBase::AppCanRestore (SexyAppBase.cpp:461)
    public boolean appCanRestore() {
        return !mIsDisabled;
    }

    /** Upstream {@code SDL_GetTicks()} / {@code PlatformBackend.ticksMillis()}. */
    public int ticks() {
        if (mPlatformBackend != null) {
            return (int) mPlatformBackend.ticksMillis();
        }
        return (int) (System.nanoTime() / 1_000_000L);
    }

    // SexyAppBase::IsInDemoMode (SexyAppBase.h:373)
    public boolean isInDemoMode() {
        return mRecordingDemoBuffer || mPlayingDemoBuffer;
    }

    // SexyAppBase::GetNowTime (SexyAppBase.h:377)
    public long getNowTime() {
        if (isInDemoMode()) {
            return mDemoStartTime + mUpdateCount / 100;
        }
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * SexyAppBase::GetLocalTime (SexyAppBase.h:409-425).
     *
     * <p>Demo-synced local time: {@code localtime} normally; during demo sessions the recorder's
     * timezone is applied via the UTC offset, and pre-epoch times are clamped to 1970-01-01. The
     * upstream {@code tm} breakdown is modelled as {@link LocalDateTime} in the same (or the
     * recorder's) zone; callers read the year/day-of-year fields, which map 1:1.
     */
    public LocalDateTime getLocalTime(long theTime) {
        if (isInDemoMode()) {
            long aShifted = theTime + mDemoTimeZoneOffset;
            if (aShifted < 0) {
                aShifted = 0;
            }
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(aShifted), java.time.ZoneOffset.UTC);
        }
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(theTime), java.time.ZoneId.systemDefault());
    }

    // SexyAppBase::IsOnPrimaryThread (SexyAppBase.h:369)
    public boolean isOnPrimaryThread() {
        return Thread.currentThread().threadId() == mPrimaryThreadId;
    }

    // SexyAppBase::GetLoadingThreadProgress (SexyAppBase.cpp:997)
    public double getLoadingThreadProgress() {
        if (mLoaded) {
            return 1.0;
        }
        if (!mLoadingThreadStarted.get()) {
            return 0.0;
        }
        if (mNumLoadingThreadTasks.get() == 0) {
            return 0.0;
        }
        return Math.min(mCompletedLoadingThreadTasks.get() / (double) mNumLoadingThreadTasks.get(), 1.0);
    }

    // SexyAppBase::StartLoadingThread (SexyAppBase.cpp:2319)
    public void startLoadingThread() {
        if (!mLoadingThreadStarted.get()) {
            mYieldMainThread = true;
            mLoadingThreadStarted.set(true);
            // keep joinable: the thread is joined by WaitForLoadingThread()
            // Daemon so the JVM can exit like the C++ process does when the main loop ends; upstream's
            // loading thread is non-detached only because the process tears everything down anyway.
            mLoadingThread = Thread.ofPlatform().daemon(true).name("LoadingThread")
                    .unstarted(this::loadingThreadProcStub);
            mLoadingThread.start();
        }
    }

    // SexyAppBase::LoadingThreadProcStub (SexyAppBase.cpp:2308)
    public void loadingThreadProcStub() {
        try {
            loadingThreadProc();
        } catch (Throwable aThrowable) {
            // Upstream's loading routine reports failures through mLoadingFailed; a Java exception must
            // not leave the thread dying silently (the main loop would wait for it forever).
            // PORT-NOTE: the stack trace, which used to be a bare aThrowable.printStackTrace() on
            // stderr - i.e. the one piece of the game's output that bypassed the log stack - is now
            // rendered by the logging stack together with the line it belongs to.
            Common.logErrorLn("Resource loading failed: " + aThrowable, aThrowable);
            mLoadingFailed.set(true);
        } finally {
            Common.logInfoLn("Resource Loading Time: {}", ticks() - mTimeLoaded);
            mLoadingThreadCompleted.set(true);
        }
    }

    // SexyAppBase::LoadingThreadProc (SexyAppBase.cpp:2300)
    public void loadingThreadProc() {
    }

    // SexyAppBase::LoadingThreadCompleted (SexyAppBase.cpp:2304)
    public void loadingThreadCompleted() {
    }

    // SexyAppBase::WaitForLoadingThread (SexyAppBase.cpp:978)
    public void waitForLoadingThread() {
        if ((mLoadingThread != null) && mLoadingThread.isAlive()) {
            try {
                mLoadingThread.join();
            } catch (InterruptedException anException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // SexyAppBase::UpdateFTimeAcc (SexyAppBase.cpp:2496)
    public void updateFTimeAcc() {
        int aCurTime = ticks();

        if (mLastTimeCheck != 0) {
            int aDeltaTime = (int) (aCurTime - mLastTimeCheck);

            mUpdateFTimeAcc = Math.min(mUpdateFTimeAcc + aDeltaTime, 200.0);

            if (mRelaxUpdateBacklogCount > 0) {
                mRelaxUpdateBacklogCount = Math.max(mRelaxUpdateBacklogCount - aDeltaTime, 0);
            }
        }

        mLastTimeCheck = aCurTime;
    }

    // SexyAppBase::UpdateFrames (SexyAppBase.cpp:1652)
    public void updateFrames() {
        mUpdateCount++;

        if (!mMinimized) {
            mWidgetManager.updateFrame();
        }

        if (mAudioEngine != null) {
            // The facade drives the music update (and only the engine owns the music stream).
            mAudioEngine.update();
        } else if (mMusicInterface != null) {
            mMusicInterface.update();
        }
        cleanSharedImages();
    }

    // SexyAppBase::DoUpdateFramesF (SexyAppBase.cpp:1663)
    public void doUpdateFramesF(float theFrac) {
        if (mVSyncUpdates && !mMinimized) {
            mWidgetManager.updateFrameF(theFrac);
        }
    }

    // SexyAppBase::DoUpdateFrames (SexyAppBase.cpp:1669)
    public boolean doUpdateFrames() {
        if (mPlayingDemoBuffer) {
            if (mLoadingThreadCompleted.get() && !mLoaded && mDemoLoadingComplete) {
                mLoaded = true;
                mYieldMainThread = false;
                loadingThreadCompleted();
            }

            // a queued command waits on game logic; let the tick advance so it can be claimed or
            // judged diverged
            if ((mLoaded == mDemoLoadingComplete)
                    && ((mUpdateCount != mLastDemoUpdateCnt) || mDemoCommandQueued)) {
                updateFrames();
                return true;
            }

            return false;
        } else {
            if (mLoadingThreadCompleted.get() && !mLoaded) {
                mLoaded = true;
                mYieldMainThread = false;
                loadingThreadCompleted();

                if (mRecordingDemoBuffer) {
                    writeDemoTimingBlock();
                    mDemoBuffer.writeNumBits(0, 1);
                    mDemoBuffer.writeNumBits(DEMO_LOADING_COMPLETE, 5);
                }
            }

            updateFrames();
            return true;
        }
    }

    // SexyAppBase::Redraw (SexyAppBase.cpp:1717)
    public void redraw(Rect theClipRect) {
        // Do mIsDrawing check because we could enter here at a bad time if any windows messages
        //  are processed during WidgetManager->Draw
        if (mIsDrawing || mShutdown.get()) {
            return;
        }

        if (mRendererBackend != null) {
            mRendererBackend.present();
        }
    }

    // SexyAppBase::DrawDirtyStuff (SexyAppBase.cpp:1775)
    public boolean drawDirtyStuff() {
        MTRand.setRandAllowed(false);
        try {
            if (mShowFPS) {
                // FPS/coords overlay drawing is not implemented in this port (upstream FPSDrawCoords
                // and CalculateDemoTimeLeft are empty stubs as well).
            }

            int aStartTime = ticks();

            mIsDrawing = true;
            boolean drewScreen = mWidgetManager.drawScreen();
            mIsDrawing = false;

            if ((drewScreen || (aStartTime - mLastDrawTick >= 1000) || mCustomCursorDirty)
                    && ((int) (aStartTime - mNextDrawTick) >= 0)) {
                mLastDrawWasEmpty = false;

                mDrawCount++;

                int aMidTime = ticks();

                mDrawTime += aMidTime - aStartTime;

                int aPreScreenBltTime = ticks();
                mLastDrawTick = aPreScreenBltTime;

                if (drewScreen) {
                    redraw(null);
                }

                // This is our one UpdateFTimeAcc if we are vsynched
                updateFTimeAcc();

                int aEndTime = ticks();

                mScreenBltTime = aEndTime - aPreScreenBltTime;

                if (mLoadingThreadStarted.get() && !mLoadingThreadCompleted.get()) {
                    int aTotalTime = aEndTime - aStartTime;

                    mNextDrawTick += 35 + Math.max(aTotalTime, 15);

                    if ((int) (aEndTime - mNextDrawTick) >= 0) {
                        mNextDrawTick = aEndTime;
                    }
                } else {
                    mNextDrawTick = aEndTime;
                }

                mHasPendingDraw = false;
                mCustomCursorDirty = false;

                return true;
            } else {
                mHasPendingDraw = false;
                mLastDrawWasEmpty = true;
                return false;
            }
        } finally {
            MTRand.setRandAllowed(true);
        }
    }

    // SexyAppBase::Process (SexyAppBase.cpp:2513)
    public boolean process(boolean allowSleep) {
        if (mLoadingFailed.get()) {
            shutdown();
        }

        boolean isVSynched = !mPlayingDemoBuffer && mVSyncUpdates && !mLastDrawWasEmpty && !mVSyncBroken
                && (!mIsPhysWindowed || (mIsPhysWindowed && mWaitForVSync && !mSoftVSyncWait));
        double aFrameFTime;
        double anUpdatesPerUpdateF;

        if (mVSyncUpdates) {
            aFrameFTime = (1000.0 / mSyncRefreshRate) / mUpdateMultiplier;
            anUpdatesPerUpdateF = 1000.0 / (mFrameTime * mSyncRefreshRate);
        } else {
            aFrameFTime = mFrameTime / mUpdateMultiplier;
            anUpdatesPerUpdateF = 1.0;
        }

        // Do we need to fast forward?
        if (mPlayingDemoBuffer) {
            if ((mUpdateCount < mFastForwardToUpdateNum) || mFastForwardToMarker) {
                if (!mDemoMute && !mFastForwardStep) {
                    mDemoMute = true;
                    mute(true);
                }

                int aTick = ticks();
                while ((mUpdateCount < mFastForwardToUpdateNum) || mFastForwardToMarker) {
                    clearUpdateBacklog(false);
                    int aLastUpdateCount = mUpdateCount;

                    // Actual updating code below

                    boolean hadRealUpdate = doUpdateFrames();

                    if (hadRealUpdate) {
                        mPendingUpdatesAcc += anUpdatesPerUpdateF;
                        mPendingUpdatesAcc -= 1.0;
                        processSafeDeleteList();

                        // Process any extra updates
                        while (mPendingUpdatesAcc >= 1.0) {
                            // These should just be IDLE commands we have to clear out
                            processDemo();

                            boolean hasRealUpdate = doUpdateFrames();

                            if (!hasRealUpdate) {
                                break;
                            }

                            processSafeDeleteList();
                            mPendingUpdatesAcc -= 1.0;
                        }

                        doUpdateFramesF((float) anUpdatesPerUpdateF);
                        processSafeDeleteList();
                    }

                    // If the update count doesn't change, its because we are
                    //  playing back a demo and need to read more
                    if (aLastUpdateCount == mUpdateCount) {
                        return true;
                    }

                    int aNewTick = ticks();
                    if ((aNewTick - aTick >= 1000) || mFastForwardStep) { // let the app draw some
                        mFastForwardStep = false;
                        aTick = ticks();
                        drawDirtyStuff();
                        return true;
                    }
                }
            }

            if (mDemoMute) {
                mDemoMute = false;
                if (mSoundManager != null) {
                    mSoundManager.stopAllSounds();
                }
                unmute(true);
            }
        }

        // Make sure we're not paused
        if (!mPaused && (mUpdateMultiplier > 0)) {
            int aStartTime = ticks();

            int aCumSleepTime = 0;

            // When we are VSynching, only calculate this FTimeAcc right after drawing

            if (!isVSynched) {
                updateFTimeAcc();
            }

            // mNonDrawCount is used to make sure we draw the screen at least
            // 10 times per second, even if it means we have to slow down
            // the updates to make it draw 10 times per second in "game time"

            boolean didUpdate = false;

            if (mUpdateAppState == UPDATESTATE_PROCESS_1) {
                if ((++mNonDrawCount < (int) Math.ceil(10 * mUpdateMultiplier)) || !mLoaded) {
                    boolean doUpdate = false;

                    if (isVSynched) {
                        // Synch'ed to vertical refresh, so update as soon as possible after draw
                        doUpdate = !mHasPendingDraw
                                || (mUpdateFTimeAcc >= (int) (aFrameFTime * 0.75));
                    } else if (mUpdateFTimeAcc >= aFrameFTime) {
                        doUpdate = true;
                    }

                    if (doUpdate) {
                        // Do VSyncBroken test.  This test fails if we're in fullscreen and
                        // "don't vsync" has been forced in Advanced settings up Display Properties
                        if (!mPlayingDemoBuffer && (mUpdateMultiplier == 1.0)) {
                            mVSyncBrokenTestUpdates++;
                            if (mVSyncBrokenTestUpdates >= (1000 + mFrameTime - 1) / mFrameTime) {
                                // It has to be running 33% fast to be "broken" (25% = 1/0.800)
                                if (aStartTime - mVSyncBrokenTestStartTick <= 800) {
                                    // The test has to fail 3 times in a row before we decide that
                                    //  vsync is broken overall
                                    mVSyncBrokenCount++;
                                    if (mVSyncBrokenCount >= 3) {
                                        mVSyncBroken = true;
                                    }
                                } else {
                                    mVSyncBrokenCount = 0;
                                }

                                mVSyncBrokenTestStartTick = aStartTime;
                                mVSyncBrokenTestUpdates = 0;
                            }
                        }

                        boolean hadRealUpdate = doUpdateFrames();
                        if (hadRealUpdate) {
                            mUpdateAppState = UPDATESTATE_PROCESS_2;
                        }

                        mHasPendingDraw = true;
                        didUpdate = true;
                    }
                }
            } else if (mUpdateAppState == UPDATESTATE_PROCESS_2) {
                mUpdateAppState = UPDATESTATE_PROCESS_DONE;

                mPendingUpdatesAcc += anUpdatesPerUpdateF;
                mPendingUpdatesAcc -= 1.0;
                processSafeDeleteList();

                // Process any extra updates
                while (mPendingUpdatesAcc >= 1.0) {
                    // These should just be IDLE commands we have to clear out
                    processDemo();

                    ++mNonDrawCount;
                    boolean hasRealUpdate = doUpdateFrames();

                    if (!hasRealUpdate) {
                        break;
                    }

                    processSafeDeleteList();
                    mPendingUpdatesAcc -= 1.0;
                }

                doUpdateFramesF((float) anUpdatesPerUpdateF);
                processSafeDeleteList();

                // Don't let mUpdateFTimeAcc dip below 0
                //  Subtract an extra 0.2ms, because sometimes refresh rates have some
                //  fractional component that gets truncated, and it's better to take off
                //  too much to keep our timing tending toward occuring right after
                //  redraws
                if (isVSynched) {
                    mUpdateFTimeAcc = Math.max(mUpdateFTimeAcc - aFrameFTime - 0.2, 0.0);
                } else {
                    mUpdateFTimeAcc -= aFrameFTime;
                }

                if (mRelaxUpdateBacklogCount > 0) {
                    mUpdateFTimeAcc = 0;
                }

                didUpdate = true;
            }

            if (!didUpdate) {
                mUpdateAppState = UPDATESTATE_PROCESS_DONE;

                mNonDrawCount = 0;

                if (mHasPendingDraw) {
                    drawDirtyStuff();
                } else {
                    // Let us take into account the time it took to draw dirty stuff
                    int aTimeToNextFrame = (int) (aFrameFTime - mUpdateFTimeAcc);
                    if (aTimeToNextFrame > 0) {
                        if (!allowSleep) {
                            return false;
                        }

                        // Wait till next processing cycle
                        ++mSleepCount;

                        if (mPlatformBackend != null) {
                            mPlatformBackend.sleepMillis(aTimeToNextFrame, 0);
                        }

                        aCumSleepTime += aTimeToNextFrame;
                    }
                }
            }

            if (mYieldMainThread) {
                // This is to make sure that the title screen doesn't take up any more than
                // 1/3 of the processor time

                int anEndTime = ticks();
                int anElapsedTime = (anEndTime - aStartTime) - aCumSleepTime;
                int aLoadingYieldSleepTime = Math.min(250, (anElapsedTime * 2) - aCumSleepTime);

                if (aLoadingYieldSleepTime >= 0) {
                    if (!allowSleep) {
                        return false;
                    }

                    if (mPlatformBackend != null) {
                        mPlatformBackend.sleepMillis(aLoadingYieldSleepTime, 0);
                    }
                }
            }
        }

        processSafeDeleteList();
        return true;
    }

    // SexyAppBase::DoMainLoop (SexyAppBase.cpp:2823)
    public void doMainLoop() {
        while (!mShutdown.get()) {
            if (mExitToTop) {
                mExitToTop = false;
            }
            updateApp();
        }
    }

    // SexyAppBase::UpdateAppStep (SexyAppBase.cpp:2837)
    public boolean updateAppStep(boolean[] updated) {
        if (updated != null) {
            updated[0] = false;
        }

        if (mExitToTop) {
            return false;
        }

        if (mUpdateAppState == UPDATESTATE_PROCESS_DONE) {
            mUpdateAppState = UPDATESTATE_MESSAGES;
        }

        mUpdateAppDepth++;

        // We update in two stages to avoid doing a Process if our loop termination
        //  condition has already been met by processing windows messages
        if (mUpdateAppState == UPDATESTATE_MESSAGES) {
            processDemo();
            if (!processDeferredMessages(true)) {
                mUpdateAppState = UPDATESTATE_PROCESS_1;
            }
        } else {
            // Process changes state by itself
            if (mStepMode != 0) {
                if (mStepMode == 2) {
                    if (mPlatformBackend != null) {
                        mPlatformBackend.sleepMillis(mFrameTime, 0);
                    }

                    mUpdateAppState = UPDATESTATE_PROCESS_DONE; // skip actual update until next step
                } else {
                    mStepMode = 2;
                    doUpdateFrames();
                    doUpdateFramesF(1.0f);
                    drawDirtyStuff();
                }
            } else {
                int anOldUpdateCnt = mUpdateCount;
                process(true);
                if (updated != null) {
                    updated[0] = mUpdateCount != anOldUpdateCnt;
                }
            }
        }

        mUpdateAppDepth--;

        return true;
    }

    // SexyAppBase::UpdateApp (SexyAppBase.cpp:2898)
    public boolean updateApp() {
        boolean[] updated = new boolean[1];
        for (;;) {
            if (!updateAppStep(updated)) {
                return false;
            }
            if (updated[0]) {
                return true;
            }
        }
    }

    // SexyAppBase::ProcessSafeDeleteList (SexyAppBase.cpp:2482)
    public void processSafeDeleteList() {
        MTRand.setRandAllowed(false);
        try {
            for (int anIdx = 0; anIdx < mSafeDeleteList.size();) {
                WidgetSafeDeleteInfo aWidgetSafeDeleteInfo = mSafeDeleteList.get(anIdx);
                if (mUpdateAppDepth <= aWidgetSafeDeleteInfo.mUpdateAppDepth) {
                    aWidgetSafeDeleteInfo.mWidget.dispose();
                    mSafeDeleteList.remove(anIdx);
                } else {
                    anIdx++;
                }
            }
        } finally {
            MTRand.setRandAllowed(true);
        }
    }

    // SexyAppBase::SafeDeleteWidget (SexyAppBase.cpp:1948)
    public void safeDeleteWidget(Widget theWidget) {
        WidgetSafeDeleteInfo aWidgetSafeDeleteInfo = new WidgetSafeDeleteInfo();
        aWidgetSafeDeleteInfo.mUpdateAppDepth = mUpdateAppDepth;
        aWidgetSafeDeleteInfo.mWidget = theWidget;
        mSafeDeleteList.add(aWidgetSafeDeleteInfo);
    }

    // SexyAppBase::RehupFocus (SexyAppBase.cpp:1962)
    protected void rehupFocus() {
        boolean wantHasFocus = mActive && !mMinimized;

        if (wantHasFocus != mHasFocus) {
            mHasFocus = wantHasFocus;

            if (mHasFocus) {
                if (mMuteOnLostFocus) {
                    unmute(true);
                }

                mWidgetManager.gotFocus();
                gotFocus();
            } else {
                if (mMuteOnLostFocus) {
                    mute(true);
                }

                mWidgetManager.lostFocus();
                lostFocus();

                mWidgetManager.doMouseUps();
            }
        }
    }

    // SexyAppBase::ClearKeysDown (SexyAppBase.cpp:1991)
    protected void clearKeysDown() {
        if (mWidgetManager != null) { // fix stuck alt-key problem
            for (int aKeyNum = 0; aKeyNum < 0xFF; aKeyNum++) {
                mWidgetManager.mKeyDown[aKeyNum] = false;
            }
        }
        mCtrlDown = false;
        mAltDown = false;
    }

    // ------------------------------------------------------------------
    // Demo recording / playback (SexyAppBase.cpp:466-802, :2002-2224)
    // ------------------------------------------------------------------

    // SexyAppBase::ReadDemoBuffer (SexyAppBase.cpp:466)
    public boolean readDemoBuffer(String[] theError) {
        theError[0] = "Invalid demo file.";

        byte[] aFileData;
        try {
            aFileData = Files.readAllBytes(Paths.get(mDemoFileName));
        } catch (IOException anException) {
            theError[0] = "Demo file not found: " + mDemoFileName;
            return false;
        }

        Buffer aFile = new Buffer();
        aFile.setData(aFileData, aFileData.length);

        int aFileID = aFile.readUInt32();
        if (aFileID != DEMO_FILE_ID) {
            theError[0] = "Invalid demo file.";
            return false;
        }

        int aVersion = aFile.readUInt32();
        if (aVersion != DEMO_VERSION) {
            theError[0] = "Incompatible demo file version.";
            return false;
        }

        mRandSeed = aFile.readUInt32();
        Common.sRand(mRandSeed);

        mDemoStartTime = readUInt64LE(aFile);
        mDemoTimeZoneOffset = aFile.readUInt32();

        // Recorded program version (empty in older files); unknown or mismatched versions only warn.
        int aStrLen = aFile.readShort() & 0xFFFF;
        byte[] aVersionBytes = new byte[aStrLen];
        aFile.readBytes(aVersionBytes, aStrLen);
        String aRecordedVersion = new String(aVersionBytes, java.nio.charset.StandardCharsets.UTF_8);
        if (aRecordedVersion.isEmpty()) {
            Common.logInfoLn("Demo has no program version tag; replay may diverge.");
        } else if (!mProductVersion.equals(aRecordedVersion)) {
            Common.logInfoLn(
                    "Demo was recorded with a different program version (recorded: {}, current: {}); replay may diverge.",
                    aRecordedVersion, mProductVersion);
        }

        int aBytesLeft = aFileData.length - (aFile.mReadBitPos / 8);

        // read marker list
        if (aVersion >= 2) {
            int aSize = aFile.readUInt32();
            aBytesLeft -= 4;

            if (aSize < 0 || aSize >= aBytesLeft) {
                theError[0] = "Invalid demo file.";
                return false;
            }

            byte[] aMarkerBytes = new byte[aSize];
            aFile.readBytes(aMarkerBytes, aSize);

            Buffer aMarkerBuffer = new Buffer();
            aMarkerBuffer.setData(aMarkerBytes, aSize);
            aMarkerBuffer.seekFront();

            int aNumItems = aMarkerBuffer.readUInt32();
            int i;
            for (i = 0; i < aNumItems && !aMarkerBuffer.atEnd(); i++) {
                DemoMarker aMarker = new DemoMarker();
                aMarker.first = aMarkerBuffer.readString();
                aMarker.second = aMarkerBuffer.readUInt32();
                mDemoMarkerList.add(aMarker);
            }

            if (i != aNumItems) {
                theError[0] = "Invalid demo file.";
                return false;
            }

            aBytesLeft -= aSize;
        }

        // Read demo commands
        aFile.readUInt32(); // unused by playback; consumed only to keep the stream aligned
        aBytesLeft -= 4;

        if (aBytesLeft <= 0) {
            theError[0] = "Invalid demo file.";
            return false;
        }

        byte[] aDemoBytes = new byte[aBytesLeft];
        aFile.readBytes(aDemoBytes, aBytesLeft);

        mDemoBuffer.setData(aDemoBytes, aBytesLeft);
        mDemoBuffer.seekFront();
        return true;
    }

    private static long readUInt64LE(Buffer theBuffer) {
        long aLow = theBuffer.readUInt32() & 0xFFFFFFFFL;
        long aHigh = theBuffer.readUInt32() & 0xFFFFFFFFL;
        return aLow | (aHigh << 32);
    }

    private static void writeUInt64LE(Buffer theBuffer, long theValue) {
        theBuffer.writeUInt32((int) (theValue & 0xFFFFFFFFL));
        theBuffer.writeUInt32((int) (theValue >>> 32));
    }

    // SexyAppBase::WriteDemoBuffer (SexyAppBase.cpp:630)
    public void writeDemoBuffer() {
        if (!mRecordingDemoBuffer) {
            return;
        }

        Buffer aFile = new Buffer();
        aFile.writeUInt32(DEMO_FILE_ID);
        aFile.writeUInt32(DEMO_VERSION);
        aFile.writeUInt32(mRandSeed);
        writeUInt64LE(aFile, mDemoStartTime);
        aFile.writeUInt32(mDemoTimeZoneOffset);

        byte[] aVersionBytes = mProductVersion.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        aFile.writeShort((short) aVersionBytes.length);
        aFile.writeBytes(aVersionBytes, aVersionBytes.length);

        Buffer aMarkerBuffer = new Buffer();
        aMarkerBuffer.writeUInt32(mDemoMarkerList.size());
        for (DemoMarker aMarker : mDemoMarkerList) {
            aMarkerBuffer.writeString(aMarker.first);
            aMarkerBuffer.writeUInt32(aMarker.second);
        }
        int aMarkerBufferSize = aMarkerBuffer.getDataLen();
        aFile.writeUInt32(aMarkerBufferSize);
        aFile.writeBytes(aMarkerBuffer.getDataPtr(), aMarkerBufferSize);

        aFile.writeUInt32(mUpdateCount);
        aFile.writeBytes(mDemoBuffer.getDataPtr(), mDemoBuffer.getDataLen());

        byte[] aBytes = java.util.Arrays.copyOf(aFile.getDataPtr(), aFile.getDataLen());
        boolean aWriteOk = writeBytesToFile(mDemoFileName, aBytes, aBytes.length);

        // Prune only files matching the automatic naming pattern; explicit targets never trigger
        // retention
        if (aWriteOk && (mDemoRecordFileLimit != 0) && !mHasCustomDemoFile
                && isStampedDemoFileName(mDemoPrefix, mDemoFileName)) {
            List<String> aDemoFiles = findDemoFiles(mDemoPrefix, true);
            for (int i = mDemoRecordFileLimit; i < aDemoFiles.size(); i++) {
                try {
                    Files.deleteIfExists(Paths.get(aDemoFiles.get(i)));
                } catch (IOException anException) {
                    Common.logErrorLn("Failed to delete demo file '{}': {}", aDemoFiles.get(i),
                            anException.getMessage());
                }
            }
        }
    }

    /** Matches the reserved automatic naming pattern: theDemoPrefix + "-YYYYMMDD-HHMMSS[-N].dmo" (SexyAppBase.cpp:598). */
    private static boolean isStampedDemoFileName(String theDemoPrefix, String theName) {
        int aStampLen = theDemoPrefix.length() + 16; // "-YYYYMMDD-HHMMSS"
        if ((theName.length() < aStampLen + 4) || !theName.startsWith(theDemoPrefix)
                || !theName.endsWith(".dmo")) {
            return false;
        }

        String aStamp = theName.substring(theDemoPrefix.length(), theDemoPrefix.length() + 16);
        if ((aStamp.charAt(0) != '-') || (aStamp.charAt(9) != '-')
                || !isAllDigits(aStamp.substring(1, 9)) || !isAllDigits(aStamp.substring(10, 16))) {
            return false;
        }

        String aSuffix = theName.substring(aStampLen, theName.length() - 4);
        return aSuffix.isEmpty() || ((aSuffix.charAt(0) == '-') && isAllDigits(aSuffix.substring(1)));
    }

    private static boolean isAllDigits(String theString) {
        if (theString.isEmpty()) {
            return false;
        }
        for (int i = 0; i < theString.length(); i++) {
            char aChar = theString.charAt(i);
            if (aChar < '0' || aChar > '9') {
                return false;
            }
        }
        return true;
    }

    // static FindDemoFiles (SexyAppBase.cpp:613)
    private static List<String> findDemoFiles(String theDemoPrefix, boolean theStampedOnly) {
        List<String> aFiles = new ArrayList<>();

        String aFilter = theDemoPrefix + '-';
        try (var anEntries = Files.newDirectoryStream(Paths.get("."))) {
            for (Path anEntry : anEntries) {
                if (!Files.isRegularFile(anEntry)) {
                    continue;
                }
                String aName = anEntry.getFileName().toString();
                if (aName.startsWith(aFilter) && aName.endsWith(".dmo")
                        && (!theStampedOnly || isStampedDemoFileName(theDemoPrefix, aName))) {
                    aFiles.add(aName);
                }
            }
        } catch (IOException anException) {
            Common.logErrorLn("Failed to enumerate demo files: {}", anException.getMessage());
        }

        int aStampLen = theDemoPrefix.length() + 16; // "-YYYYMMDD-HHMMSS"
        // stamp, then longer (suffixed) name, then lexicographic, descending
        aFiles.sort((theA, theB) -> {
            String aKeyA = theA.substring(0, Math.min(aStampLen, theA.length()));
            String aKeyB = theB.substring(0, Math.min(aStampLen, theB.length()));
            int aCompare = aKeyA.compareTo(aKeyB);
            if (aCompare != 0) {
                return -aCompare;
            }
            aCompare = Integer.compare(theA.length(), theB.length());
            if (aCompare != 0) {
                return -aCompare;
            }
            return -theA.compareTo(theB);
        });
        return aFiles;
    }

    // static GetTimestampedDemoFileName (SexyAppBase.cpp:3182)
    private static String getTimestampedDemoFileName(String theDemoPrefix) {
        LocalDateTime aNow = LocalDateTime.now();

        String aBaseName = String.format("%s-%04d%02d%02d-%02d%02d%02d", theDemoPrefix, aNow.getYear(),
                aNow.getMonthValue(), aNow.getDayOfMonth(), aNow.getHour(), aNow.getMinute(),
                aNow.getSecond());
        String aName = aBaseName + ".dmo";
        String aSuffixPrefix = aBaseName + '-';
        for (String aFileName : findDemoFiles(theDemoPrefix, true)) {
            if (aFileName.equals(aName)) {
                return aBaseName + "-2.dmo";
            }
            if (aFileName.startsWith(aSuffixPrefix)) {
                int aNumber = stringToIntOr(aFileName.substring(aSuffixPrefix.length()), 0);
                return aBaseName + "-" + (aNumber + 1) + ".dmo";
            }
        }

        return aName;
    }

    // SexyAppBase::DemoSyncBuffer (SexyAppBase.cpp:689)
    public void demoSyncBuffer(Buffer theBuffer) {
        if (mPlayingDemoBuffer) {
            if (mManualShutdown) {
                return;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            int aLen = mDemoBuffer.readUInt32();

            theBuffer.clear();
            for (int i = 0; i < aLen; i++) {
                theBuffer.writeByte(mDemoBuffer.readByte());
            }
        } else if (mRecordingDemoBuffer) {
            writeDemoTimingBlock();
            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_SYNC, 5);
            mDemoBuffer.writeUInt32(theBuffer.getDataLen());
            mDemoBuffer.writeBytes(theBuffer.getDataPtr(), theBuffer.getDataLen());
        }
    }

    // SexyAppBase::DemoSyncString (SexyAppBase.cpp:718)
    public void demoSyncString(String[] theString) {
        Buffer aBuffer = new Buffer();
        aBuffer.writeString(theString[0]);
        demoSyncBuffer(aBuffer);
        theString[0] = aBuffer.readString();
    }

    // SexyAppBase::DemoSyncInt (SexyAppBase.cpp:726)
    public void demoSyncInt(int[] theInt) {
        Buffer aBuffer = new Buffer();
        aBuffer.writeInt32(theInt[0]);
        demoSyncBuffer(aBuffer);
        theInt[0] = aBuffer.readInt32();
    }

    // SexyAppBase::DemoSyncBool (SexyAppBase.cpp:734)
    public void demoSyncBool(boolean[] theBool) {
        Buffer aBuffer = new Buffer();
        aBuffer.writeBoolean(theBool[0]);
        demoSyncBuffer(aBuffer);
        theBool[0] = aBuffer.readBoolean();
    }

    // SexyAppBase::DemoAssertStringEqual (SexyAppBase.cpp:742)
    public void demoAssertStringEqual(String theString) {
        if (mPlayingDemoBuffer) {
            if (mManualShutdown) {
                return;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            mDemoBuffer.readString();
        } else if (mRecordingDemoBuffer) {
            writeDemoTimingBlock();
            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_ASSERT_STRING_EQUAL, 5);
            mDemoBuffer.writeString(theString);
        }
    }

    // SexyAppBase::DemoAddMarker (SexyAppBase.cpp:767)
    public void demoAddMarker(String theString) {
        if (mPlayingDemoBuffer) {
            mFastForwardToMarker = false;
        } else if (mRecordingDemoBuffer) {
            mDemoMarkerList.add(new DemoMarker(theString, mUpdateCount));
        }
    }

    // SexyAppBase::DemoAssertIntEqual (SexyAppBase.cpp:779)
    public void demoAssertIntEqual(int theInt) {
        if (mPlayingDemoBuffer) {
            if (mManualShutdown) {
                return;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            mDemoBuffer.readInt32(); // upstream only uses it in an assertion
        } else if (mRecordingDemoBuffer) {
            writeDemoTimingBlock();
            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_ASSERT_INT_EQUAL, 5);
            mDemoBuffer.writeInt32(theInt);
        }
    }

    // SexyAppBase::WriteDemoTimingBlock (SexyAppBase.cpp:2002)
    protected void writeDemoTimingBlock() {
        while (mUpdateCount - mLastDemoUpdateCnt > 15) {
            mDemoBuffer.writeNumBits(15, 4);
            mLastDemoUpdateCnt += 15;

            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_IDLE, 5);
        }

        mDemoBuffer.writeNumBits(mUpdateCount - mLastDemoUpdateCnt, 4);
        mLastDemoUpdateCnt = mUpdateCount;
    }

    // SexyAppBase::PrepareDemoCommand (SexyAppBase.cpp:2025)
    protected boolean prepareDemoCommand(boolean required) {
        if (mDemoNeedsCommand) {
            mDemoCmdBitPos = mDemoBuffer.mReadBitPos;
            mDemoCmdUpdateCnt = mLastDemoUpdateCnt;
            if (required) { // a game-logic call site claimed the queued command
                mDemoCommandQueued = false;
            }

            mLastDemoUpdateCnt += mDemoBuffer.readNumBits(4, false);

            mDemoIsShortCmd = mDemoBuffer.readNumBits(1, false) == 1;

            if (mDemoIsShortCmd) {
                mDemoCmdNum = mDemoBuffer.readNumBits(1, false);
            } else {
                mDemoCmdNum = mDemoBuffer.readNumBits(5, false);
            }

            mDemoNeedsCommand = false;
        }

        return mUpdateCount >= mLastDemoUpdateCnt;
    }

    // SexyAppBase::ProcessDemo (SexyAppBase.cpp:2051)
    protected void processDemo() {
        if (!mPlayingDemoBuffer) {
            return;
        }

        if (mDemoBuffer.atEnd()) { // a recording that didn't end with DEMO_CLOSE: treat stream end as end of demo
            shutdown();
            return;
        }

        while (!mShutdown.get() && (mUpdateCount >= mLastDemoUpdateCnt) && !mDemoBuffer.atEnd()) {
            if (!prepareDemoCommand(false)) {
                continue;
            }

            mDemoNeedsCommand = true;

            if (mDemoIsShortCmd) {
                switch (mDemoCmdNum) {
                    case 0 -> {
                        int aDeltaX = mDemoBuffer.readNumBits(6, true);
                        int aDeltaY = mDemoBuffer.readNumBits(6, true);
                        mLastDemoMouseX += aDeltaX;
                        mLastDemoMouseY += aDeltaY;

                        mWidgetManager.mouseMove(mLastDemoMouseX, mLastDemoMouseY);
                    }
                    case 1 -> {
                        boolean down = mDemoBuffer.readNumBits(1, false) != 0;
                        int aBtnCount = mDemoBuffer.readNumBits(3, true);

                        if (down) {
                            mWidgetManager.mouseDown(mLastDemoMouseX, mLastDemoMouseY, aBtnCount);
                        } else {
                            mWidgetManager.mouseUp(mLastDemoMouseX, mLastDemoMouseY, aBtnCount);
                        }
                    }
                    default -> {
                    }
                }
            } else {
                switch (mDemoCmdNum) {
                    case DEMO_MOUSE_POSITION -> {
                        mLastDemoMouseX = mDemoBuffer.readNumBits(12, false);
                        mLastDemoMouseY = mDemoBuffer.readNumBits(12, false);

                        mWidgetManager.mouseMove(mLastDemoMouseX, mLastDemoMouseY);
                    }
                    case DEMO_ACTIVATE_APP -> {
                        mActive = mDemoBuffer.readNumBits(1, false) != 0;

                        rehupFocus();

                        if (mActive && !mIsWindowed) {
                            mWidgetManager.markAllDirty();
                        }

                        if (mIsOpeningURL && !mActive) {
                            urlOpenSucceeded(mOpeningURL);
                        }
                    }
                    case DEMO_SIZE -> {
                        boolean isMinimized = mDemoBuffer.readBoolean();

                        if (!mShutdown.get() && (isMinimized != mMinimized)) {
                            mMinimized = isMinimized;

                            // We don't want any sounds (or music) playing while its minimized
                            if (mMinimized) {
                                mute(true);
                            } else {
                                unmute(true);
                                mWidgetManager.markAllDirty();
                            }
                        }

                        rehupFocus();
                    }
                    case DEMO_MOUSE_WHEEL -> {
                        int aScroll = mDemoBuffer.readNumBits(8, true);
                        mWidgetManager.mouseWheel(aScroll);
                    }
                    case DEMO_KEY_DOWN -> {
                        int aKeyCode = mDemoBuffer.readNumBits(8, false);
                        mWidgetManager.keyDown(aKeyCode);
                    }
                    case DEMO_KEY_UP -> {
                        int aKeyCode = mDemoBuffer.readNumBits(8, false);
                        mWidgetManager.keyUp(aKeyCode);
                    }
                    case DEMO_KEY_CHAR -> {
                        int sizeMult = mDemoBuffer.readNumBits(1, false) + 1; // 1 single, 2 double
                        char aChar = (char) mDemoBuffer.readNumBits(8 * sizeMult, false);
                        mWidgetManager.keyChar(aChar);
                    }
                    case DEMO_KEY_TEXT -> {
                        String aText = mDemoBuffer.readString();
                        if (!aText.isEmpty()) {
                            mWidgetManager.keyText(aText);
                        }
                    }
                    case DEMO_CLOSE -> shutdown();
                    case DEMO_MOUSE_ENTER -> {
                        mMouseIn = true;
                        enforceCursor();
                    }
                    case DEMO_MOUSE_EXIT -> {
                        mWidgetManager.mouseExit(mLastDemoMouseX, mLastDemoMouseY);
                        mMouseIn = false;
                        enforceCursor();
                    }
                    case DEMO_LOADING_COMPLETE -> mDemoLoadingComplete = true;
                    case DEMO_VIDEO_DATA -> {
                        mIsWindowed = mDemoBuffer.readBoolean();
                        mSyncRefreshRate = mDemoBuffer.readByte();
                    }
                    case DEMO_IDLE -> {
                    }
                    case DEMO_REGISTRY_GETSUBKEYS, DEMO_REGISTRY_READ, DEMO_REGISTRY_WRITE,
                            DEMO_REGISTRY_ERASE, DEMO_FILE_EXISTS, DEMO_FILE_READ, DEMO_FILE_WRITE,
                            DEMO_SYNC, DEMO_ASSERT_STRING_EQUAL, DEMO_ASSERT_INT_EQUAL -> {
                        if (mDemoCommandQueued && mUpdateCount != mDemoQueuedSince) {
                            // queued across a tick with no claim: the replay has diverged
                            shutdown();
                            return;
                        }
                        mDemoQueuedSince = mUpdateCount;
                        mDemoCommandQueued = true;
                        mDemoBuffer.mReadBitPos = mDemoCmdBitPos; // leave queued for the game-logic call site
                        mLastDemoUpdateCnt = mDemoCmdUpdateCnt;
                        mDemoNeedsCommand = true;
                        return;
                    }
                    default -> {
                    }
                }
            }
        }
    }

    // SexyAppBase::DemoSyncRefreshRate (SexyAppBase.cpp:4138)
    public void demoSyncRefreshRate() {
        if (mRendererBackend == null) {
            return;
        }

        mSyncRefreshRate = 60; // upstream copies GLInterface::mRefreshRate (60 Hz presentation)

        if (mRecordingDemoBuffer) {
            writeDemoTimingBlock();
            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_VIDEO_DATA, 5);
            mDemoBuffer.writeBoolean(mIsWindowed);
            mDemoBuffer.writeByte((byte) mSyncRefreshRate);
        }
    }

    // ------------------------------------------------------------------
    // Registry (SexyAppBase.cpp:1008-1365)
    // ------------------------------------------------------------------

    // SexyAppBase::RegistryWrite (SexyAppBase.cpp:1008)
    protected boolean registryWrite(String theValueName, int theType, byte[] theValue, int theLength) {
        if (mRegKey.isEmpty()) {
            return false;
        }

        if (mPlayingDemoBuffer && isOnPrimaryThread()) {
            if (mManualShutdown) {
                return true;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            return mDemoBuffer.readNumBits(1, false) != 0;
        }

        String aKeyName = Common.removeTrailingSlash("SOFTWARE\\" + mRegKey);
        String aValueName;

        int aSlashPos = theValueName.lastIndexOf('\\');
        if (aSlashPos != -1) {
            aKeyName += "\\" + theValueName.substring(0, aSlashPos);
            aValueName = theValueName.substring(aSlashPos + 1);
        } else {
            aValueName = theValueName;
        }

        if (!RegEmu.registryWrite(aKeyName, aValueName, theType, theValue, theLength)) {
            if (mRecordingDemoBuffer && isOnPrimaryThread()) {
                writeDemoTimingBlock();
                mDemoBuffer.writeNumBits(0, 1);
                mDemoBuffer.writeNumBits(DEMO_REGISTRY_WRITE, 5);
                mDemoBuffer.writeNumBits(0, 1); // failure
            }

            return false;
        }

        if (mRecordingDemoBuffer && isOnPrimaryThread()) {
            writeDemoTimingBlock();
            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_REGISTRY_WRITE, 5);
            mDemoBuffer.writeNumBits(1, 1); // success
        }

        return true;
    }

    // SexyAppBase::RegistryWriteString (SexyAppBase.cpp:1065)
    public boolean registryWriteString(String theValueName, String theString) {
        byte[] aBytes = theString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return registryWrite(theValueName, RegEmu.REGEMU_SZ, aBytes, aBytes.length);
    }

    // SexyAppBase::RegistryWriteInteger (SexyAppBase.cpp:1070)
    public boolean registryWriteInteger(String theValueName, int theValue) {
        return registryWrite(theValueName, RegEmu.REGEMU_DWORD, toLE32(theValue), 4);
    }

    // SexyAppBase::RegistryWriteBoolean (SexyAppBase.cpp:1075)
    public boolean registryWriteBoolean(String theValueName, boolean theValue) {
        return registryWrite(theValueName, RegEmu.REGEMU_DWORD, toLE32(theValue ? 1 : 0), 4);
    }

    // SexyAppBase::RegistryWriteData (SexyAppBase.cpp:1081)
    public boolean registryWriteData(String theValueName, byte[] theValue, int theLength) {
        return registryWrite(theValueName, RegEmu.REGEMU_BINARY, theValue, theLength);
    }

    // SexyAppBase::WriteToRegistry (SexyAppBase.cpp:1086)
    public void writeToRegistry() {
        registryWriteInteger("MusicVolume", (int) (mMusicVolume * 100));
        registryWriteInteger("SfxVolume", (int) (mSfxVolume * 100));
        registryWriteInteger("Muted", (mMuteCount - mAutoMuteCount > 0) ? 1 : 0);
        registryWriteInteger("ScreenMode", mIsWindowed ? 0 : 1);
        registryWriteInteger("PreferredX", mPreferredX);
        registryWriteInteger("PreferredY", mPreferredY);
        registryWriteInteger("CustomCursors", mCustomCursorsEnabled ? 1 : 0);
        registryWriteInteger("InProgress", 0);
        registryWriteBoolean("WaitForVSync", mWaitForVSync);
    }

    private static byte[] toLE32(int theValue) {
        return new byte[] {(byte) theValue, (byte) (theValue >> 8), (byte) (theValue >> 16),
                (byte) (theValue >> 24)};
    }

    /** {@code std::from_chars} equivalent: leading integer, {@code theDefault} when unparsable. */
    private static int stringToIntOr(String theString, int theDefault) {
        Integer aValue = Common.stringToInt(theString);
        return aValue != null ? aValue : theDefault;
    }

    private static int fromLE32(byte[] theValue, int theOffset) {
        return (theValue[theOffset] & 0xFF) | ((theValue[theOffset + 1] & 0xFF) << 8)
                | ((theValue[theOffset + 2] & 0xFF) << 16) | ((theValue[theOffset + 3] & 0xFF) << 24);
    }

    // SexyAppBase::RegistryEraseKey (SexyAppBase.cpp:1099)
    public boolean registryEraseKey(String theKeyName) {
        if (mRegKey.isEmpty()) {
            return false;
        }

        if (mPlayingDemoBuffer && isOnPrimaryThread()) {
            if (mManualShutdown) {
                return true;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            return mDemoBuffer.readNumBits(1, false) != 0;
        }

        String aKeyName = Common.removeTrailingSlash("SOFTWARE\\" + mRegKey) + "\\" + theKeyName;

        if (!RegEmu.registryEraseKey(aKeyName)) {
            if (mRecordingDemoBuffer && isOnPrimaryThread()) {
                writeDemoTimingBlock();
                mDemoBuffer.writeNumBits(0, 1);
                mDemoBuffer.writeNumBits(DEMO_REGISTRY_ERASE, 5);
                mDemoBuffer.writeNumBits(0, 1); // failure
            }

            return false;
        }

        if (mRecordingDemoBuffer && isOnPrimaryThread()) {
            writeDemoTimingBlock();
            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_REGISTRY_ERASE, 5);
            mDemoBuffer.writeNumBits(1, 1); // success
        }

        return true;
    }

    // SexyAppBase::RegistryEraseValue (SexyAppBase.cpp:1145)
    public void registryEraseValue(String theValueName) {
        if (mRegKey.isEmpty()) {
            return;
        }

        String aKeyName = Common.removeTrailingSlash("SOFTWARE\\" + mRegKey);
        String aValueName;

        int aSlashPos = theValueName.lastIndexOf('\\');
        if (aSlashPos != -1) {
            aKeyName += "\\" + theValueName.substring(0, aSlashPos);
            aValueName = theValueName.substring(aSlashPos + 1);
        } else {
            aValueName = theValueName;
        }

        RegEmu.registryEraseValue(aKeyName, aValueName);
    }

    // SexyAppBase::RegistryRead (SexyAppBase.cpp:1168)
    protected boolean registryRead(String theValueName, int[] theType, byte[] theValue, int[] theLength) {
        if (mRegKey.isEmpty()) {
            return false;
        }

        if (mPlayingDemoBuffer && isOnPrimaryThread()) {
            if (mManualShutdown) {
                return false;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            boolean success = mDemoBuffer.readNumBits(1, false) != 0;
            if (!success) {
                return false;
            }

            theType[0] = mDemoBuffer.readUInt32();

            int aLen = mDemoBuffer.readUInt32();

            if (theLength[0] >= aLen) {
                theLength[0] = aLen;
                if ((theType[0] == RegEmu.REGEMU_DWORD) && (aLen == 4)) {
                    int aValue = mDemoBuffer.readUInt32();
                    System.arraycopy(toLE32(aValue), 0, theValue, 0, 4);
                } else {
                    mDemoBuffer.readBytes(theValue, aLen);
                }
                return true;
            } else {
                for (int i = 0; i < aLen; i++) {
                    mDemoBuffer.readByte();
                }
                return false;
            }
        } else {
            String aKeyName = Common.removeTrailingSlash("SOFTWARE\\" + mRegKey);
            String aValueName;

            int aSlashPos = theValueName.lastIndexOf('\\');
            if (aSlashPos != -1) {
                aKeyName += "\\" + theValueName.substring(0, aSlashPos);
                aValueName = theValueName.substring(aSlashPos + 1);
            } else {
                aValueName = theValueName;
            }

            RegEmu.RegValue aRegValue = new RegEmu.RegValue();
            if (RegEmu.registryRead(aKeyName, aValueName, aRegValue)) {
                theType[0] = aRegValue.mType;
                int aLen = Math.min(theLength[0], aRegValue.mLength);
                if (aRegValue.mValue != null) {
                    System.arraycopy(aRegValue.mValue, 0, theValue, 0, aLen);
                }
                theLength[0] = aRegValue.mLength;

                if (mRecordingDemoBuffer && isOnPrimaryThread()) {
                    writeDemoTimingBlock();
                    mDemoBuffer.writeNumBits(0, 1);
                    mDemoBuffer.writeNumBits(DEMO_REGISTRY_READ, 5);
                    mDemoBuffer.writeNumBits(1, 1); // success
                    mDemoBuffer.writeUInt32(theType[0]);
                    mDemoBuffer.writeUInt32(theLength[0]);
                    if ((theType[0] == RegEmu.REGEMU_DWORD) && (theLength[0] == 4)) {
                        mDemoBuffer.writeUInt32(fromLE32(theValue, 0));
                    } else {
                        mDemoBuffer.writeBytes(theValue, theLength[0]);
                    }
                }

                return true;
            }

            if (mRecordingDemoBuffer && isOnPrimaryThread()) {
                writeDemoTimingBlock();
                mDemoBuffer.writeNumBits(0, 1);
                mDemoBuffer.writeNumBits(DEMO_REGISTRY_READ, 5);
                mDemoBuffer.writeNumBits(0, 1); // failure
            }

            return false;
        }
    }

    // SexyAppBase::RegistryReadString (SexyAppBase.cpp:1268)
    public boolean registryReadString(String theKey, String[] theString) {
        byte[] aStr = new byte[1024];

        int[] aType = new int[1];
        int[] aLen = new int[] {aStr.length - 1};
        if (!registryRead(theKey, aType, aStr, aLen)) {
            return false;
        }

        if (aType[0] != RegEmu.REGEMU_SZ) {
            return false;
        }

        theString[0] = new String(aStr, 0, aLen[0], java.nio.charset.StandardCharsets.UTF_8);
        return true;
    }

    // SexyAppBase::RegistryReadInteger (SexyAppBase.cpp:1286)
    public boolean registryReadInteger(String theKey, int[] theValue) {
        byte[] aLong = new byte[4];

        int[] aType = new int[1];
        int[] aLen = new int[] {4};
        if (!registryRead(theKey, aType, aLong, aLen)) {
            return false;
        }

        if (aType[0] != RegEmu.REGEMU_DWORD) {
            return false;
        }

        theValue[0] = fromLE32(aLong, 0);
        return true;
    }

    // SexyAppBase::RegistryReadBoolean (SexyAppBase.cpp:1301)
    public boolean registryReadBoolean(String theKey, boolean[] theValue) {
        int[] aValue = new int[1];
        if (!registryReadInteger(theKey, aValue)) {
            return false;
        }

        theValue[0] = aValue[0] != 0;
        return true;
    }

    // SexyAppBase::RegistryReadData (SexyAppBase.cpp:1311)
    public boolean registryReadData(String theKey, byte[] theValue, int[] theLength) {
        int[] aType = new int[1];
        if (!registryRead(theKey, aType, theValue, theLength)) {
            return false;
        }

        return aType[0] == RegEmu.REGEMU_BINARY;
    }

    // SexyAppBase::ReadFromRegistry (SexyAppBase.cpp:1323)
    public void readFromRegistry() {
        mReadFromRegistry = true;
        mRegKey = getString("RegistryKey", mRegKey);

        if (mRegKey.isEmpty()) {
            return;
        }

        RegEmu.setRegFile(Common.getAppDataPath("registry.regemu"));

        int[] anInt = new int[1];
        if (registryReadInteger("MusicVolume", anInt)) {
            mMusicVolume = anInt[0] / 100.0;
        }

        if (registryReadInteger("SfxVolume", anInt)) {
            mSfxVolume = anInt[0] / 100.0;
        }

        if (registryReadInteger("Muted", anInt)) {
            mMuteCount = anInt[0];
        }

        // The read must happen on every platform to keep the demo command stream in sync
        if (registryReadInteger("ScreenMode", anInt)) {
            mIsWindowed = anInt[0] == 0;
        }

        if (registryReadInteger("PreferredX", anInt)) {
            mPreferredX = anInt[0];
        }
        if (registryReadInteger("PreferredY", anInt)) {
            mPreferredY = anInt[0];
        }

        if (registryReadInteger("CustomCursors", anInt)) {
            enableCustomCursors(anInt[0] != 0);
        }

        boolean[] aBool = new boolean[1];
        registryReadBoolean("WaitForVSync", aBool);
        mWaitForVSync = aBool[0];

        if (registryReadInteger("InProgress", anInt)) {
            mLastShutdownWasGraceful = anInt[0] == 0;
        }

        if (!isScreenSaver()) {
            registryWriteInteger("InProgress", 1);
        }
    }

    // ------------------------------------------------------------------
    // File helpers (SexyAppBase.cpp:1366-1541)
    // ------------------------------------------------------------------

    // SexyAppBase::WriteBytesToFile (SexyAppBase.cpp:1366)
    public boolean writeBytesToFile(String theFileName, byte[] theData, int theDataLen) {
        if (mPlayingDemoBuffer && isOnPrimaryThread()) {
            if (mManualShutdown) {
                return true;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            return mDemoBuffer.readNumBits(1, false) != 0;
        }

        try {
            Path aPath = Paths.get(theFileName);
            Path aDir = aPath.getParent();
            if (aDir != null) {
                Files.createDirectories(aDir);
            }
            Files.write(aPath, java.util.Arrays.copyOf(theData, theDataLen));
        } catch (IOException anException) {
            Common.logErrorLn("Failed to write file '{}': {}", theFileName, anException.getMessage());

            if (mRecordingDemoBuffer && isOnPrimaryThread()) {
                writeDemoTimingBlock();
                mDemoBuffer.writeNumBits(0, 1);
                mDemoBuffer.writeNumBits(DEMO_FILE_WRITE, 5);
                mDemoBuffer.writeNumBits(0, 1); // failure
            }

            return false;
        }

        if (mRecordingDemoBuffer && isOnPrimaryThread()) {
            writeDemoTimingBlock();
            mDemoBuffer.writeNumBits(0, 1);
            mDemoBuffer.writeNumBits(DEMO_FILE_WRITE, 5);
            mDemoBuffer.writeNumBits(1, 1); // success
        }

        return true;
    }

    // SexyAppBase::WriteBufferToFile (SexyAppBase.cpp:1416)
    public boolean writeBufferToFile(String theFileName, Buffer theBuffer) {
        return writeBytesToFile(theFileName, theBuffer.getDataPtr(), theBuffer.getDataLen());
    }

    // SexyAppBase::ReadBufferFromFile (SexyAppBase.cpp:1422)
    public boolean readBufferFromFile(String theFileName, Buffer theBuffer) {
        return readBufferFromFile(theFileName, theBuffer, false);
    }

    public boolean readBufferFromFile(String theFileName, Buffer theBuffer, boolean dontWriteToDemo) {
        if (mPlayingDemoBuffer && !dontWriteToDemo && isOnPrimaryThread()) {
            if (mManualShutdown) {
                return false;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            boolean success = mDemoBuffer.readNumBits(1, false) != 0;
            if (!success) {
                return false;
            }

            int aLen = mDemoBuffer.readUInt32();

            theBuffer.clear();
            for (int i = 0; i < aLen; i++) {
                theBuffer.writeByte(mDemoBuffer.readByte());
            }

            return true;
        } else {
            byte[] aData;
            try {
                aData = PakInterface.getInstance().readResource(theFileName);
            } catch (IOException anException) {
                aData = null;
            }

            if (aData == null) {
                if (mRecordingDemoBuffer && !dontWriteToDemo && isOnPrimaryThread()) {
                    writeDemoTimingBlock();
                    mDemoBuffer.writeNumBits(0, 1);
                    mDemoBuffer.writeNumBits(DEMO_FILE_READ, 5);
                    mDemoBuffer.writeNumBits(0, 1); // failure
                }

                return false;
            }

            theBuffer.clear();
            theBuffer.setData(aData, aData.length);

            if (mRecordingDemoBuffer && !dontWriteToDemo && isOnPrimaryThread()) {
                writeDemoTimingBlock();
                mDemoBuffer.writeNumBits(0, 1);
                mDemoBuffer.writeNumBits(DEMO_FILE_READ, 5);
                mDemoBuffer.writeNumBits(1, 1); // success
                mDemoBuffer.writeUInt32(aData.length);
                mDemoBuffer.writeBytes(aData, aData.length);
            }

            return true;
        }
    }

    // SexyAppBase::ReadUTF8StringFromFile (SexyAppBase.cpp:1490)
    public boolean readUtf8StringFromFile(String theFileName, String[] theString) {
        Buffer aBuffer = new Buffer();
        if (!readBufferFromFile(theFileName, aBuffer)) {
            return false;
        }

        theString[0] = aBuffer.toUTF8String();
        return true;
    }

    /** Convenience form of {@link #readUtf8StringFromFile(String, String[])}; {@code null} on failure. */
    public String readUtf8StringFromFile(String theFileName) {
        String[] aString = new String[1];
        if (!readUtf8StringFromFile(theFileName, aString)) {
            return null;
        }
        return aString[0];
    }

    // SexyAppBase::FileExists (SexyAppBase.cpp:1499)
    public boolean fileExists(String theFileName) {
        if (mPlayingDemoBuffer && isOnPrimaryThread()) {
            if (mManualShutdown) {
                return true;
            }

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            return mDemoBuffer.readNumBits(1, false) != 0;
        } else {
            boolean aFileExists;
            try {
                aFileExists = PakInterface.getInstance().exists(theFileName);
            } catch (IOException anException) {
                aFileExists = false;
            }

            if (mRecordingDemoBuffer && isOnPrimaryThread()) {
                writeDemoTimingBlock();
                mDemoBuffer.writeNumBits(0, 1);
                mDemoBuffer.writeNumBits(DEMO_FILE_EXISTS, 5);
                mDemoBuffer.writeNumBits(aFileExists ? 1 : 0, 1);
            }

            return aFileExists;
        }
    }

    // SexyAppBase::EraseFile (SexyAppBase.cpp:1535)
    public boolean eraseFile(String theFileName) {
        if (mPlayingDemoBuffer) {
            return true;
        }

        try {
            return Files.deleteIfExists(Paths.get(theFileName));
        } catch (IOException anException) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Dialogs (SexyAppBase.cpp:804-937)
    // ------------------------------------------------------------------

    // SexyAppBase::NewDialog (SexyAppBase.cpp:804)
    public Dialog newDialog(int theDialogId, boolean isModal, String theDialogHeader,
            String theDialogLines, String theDialogFooter, int theButtonMode) {
        return new Dialog(null, null, theDialogId, isModal, theDialogHeader, theDialogLines,
                theDialogFooter, theButtonMode);
    }

    // SexyAppBase::DoDialog (SexyAppBase.cpp:810)
    public Dialog doDialog(int theDialogId, boolean isModal, String theDialogHeader,
            String theDialogLines, String theDialogFooter, int theButtonMode) {
        killDialog(theDialogId, true, true);

        Dialog aDialog = newDialog(theDialogId, isModal, theDialogHeader, theDialogLines,
                theDialogFooter, theButtonMode);

        addDialog(theDialogId, aDialog);

        return aDialog;
    }

    // SexyAppBase::GetDialog (SexyAppBase.cpp:822)
    public Dialog getDialog(int theDialogId) {
        return mDialogMap.get(theDialogId);
    }

    // SexyAppBase::KillDialog (SexyAppBase.cpp:832)
    public boolean killDialog(int theDialogId, boolean removeWidget, boolean deleteWidget) {
        Dialog aDialog = mDialogMap.get(theDialogId);

        if (aDialog != null) {
            // set the result to something else so DoMainLoop knows that the dialog is gone
            // in case nobody else sets mResult
            if (aDialog.mResult == 0x7FFFFFFF) {
                aDialog.mResult = 0;
            }

            mDialogList.remove(aDialog);
            mDialogMap.remove(theDialogId);

            if (removeWidget || deleteWidget) {
                mWidgetManager.removeWidget(aDialog);
            }

            if (aDialog.isModal()) {
                modalClose();
                mWidgetManager.removeBaseModal(aDialog);
            }

            if (deleteWidget) {
                safeDeleteWidget(aDialog);
            }

            return true;
        }

        return false;
    }

    // SexyAppBase::KillDialog (SexyAppBase.cpp:873)
    public boolean killDialog(int theDialogId) {
        return killDialog(theDialogId, true, true);
    }

    // SexyAppBase::KillDialog (SexyAppBase.cpp:878)
    public boolean killDialog(Dialog theDialog) {
        return killDialog(theDialog.mId);
    }

    // SexyAppBase::GetDialogCount (SexyAppBase.cpp:883)
    public int getDialogCount() {
        return mDialogMap.size();
    }

    // SexyAppBase::AddDialog (SexyAppBase.cpp:888)
    public void addDialog(int theDialogId, Dialog theDialog) {
        killDialog(theDialogId, true, true);

        if (theDialog.mWidth == 0) {
            // Set the dialog position ourselves
            int aWidth = mWidth / 2;
            theDialog.resize((mWidth - aWidth) / 2, mHeight / 5, aWidth,
                    theDialog.getPreferredHeight(aWidth));
        }

        mDialogMap.put(theDialogId, theDialog);
        mDialogList.add(theDialog);

        mWidgetManager.addWidget(theDialog);
        if (theDialog.isModal()) {
            mWidgetManager.addBaseModal(theDialog);
            modalOpen();
        }
    }

    // SexyAppBase::AddDialog (SexyAppBase.cpp:910)
    public void addDialog(Dialog theDialog) {
        addDialog(theDialog.mId, theDialog);
    }

    // SexyAppBase::ModalOpen (SexyAppBase.cpp:915)
    public void modalOpen() {
    }

    // SexyAppBase::ModalClose (SexyAppBase.cpp:919)
    public void modalClose() {
    }

    // SexyAppBase::DialogButtonPress (SexyAppBase.cpp:923)
    @Override
    public void dialogButtonPress(int theDialogId, int theButtonId) {
        if (theButtonId == Dialog.ID_YES) {
            buttonPress(2000 + theDialogId);
        } else if (theButtonId == Dialog.ID_NO) {
            buttonPress(3000 + theDialogId);
        }
    }

    // SexyAppBase::DialogButtonDepress (SexyAppBase.cpp:931)
    @Override
    public void dialogButtonDepress(int theDialogId, int theButtonId) {
        if (theButtonId == Dialog.ID_YES) {
            buttonDepress(2000 + theDialogId);
        } else if (theButtonId == Dialog.ID_NO) {
            buttonDepress(3000 + theDialogId);
        }
    }

    // SexyAppBase inherits ButtonListener; the base implementation is empty (upstream concrete
    // subclasses such as LawnApp override these).
    @Override
    public void buttonPress(int theId) {
    }

    @Override
    public void buttonDepress(int theId) {
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

    // ------------------------------------------------------------------
    // Properties (SexyAppBase.cpp:2980-3200)
    // ------------------------------------------------------------------

    // SexyAppBase::LoadProperties (SexyAppBase.cpp:2980)
    public boolean loadProperties(String theFileName, boolean required, boolean checkSig) {
        Buffer aBuffer = new Buffer();
        if (!readBufferFromFile(theFileName, aBuffer)) {
            if (!required) {
                return true;
            } else {
                popup(getString("UNABLE_OPEN_PROPERTIES", "Unable to open properties file ")
                        + theFileName);
                return false;
            }
        }

        PropertiesParser aPropertiesParser = new PropertiesParser(this);

        // Load required language-file properties
        if (!aPropertiesParser.parsePropertiesBuffer(aBuffer)) {
            popup(aPropertiesParser.getErrorText());
            return false;
        } else {
            return true;
        }
    }

    // SexyAppBase::LoadProperties (SexyAppBase.cpp:3009)
    public boolean loadProperties() {
        // Load required language-file properties
        return loadProperties("properties/default.xml", true, false);
    }

    // SexyAppBase::LoadResourceManifest (SexyAppBase.cpp:3015)
    public void loadResourceManifest() {
        if ((mResourceManager != null) && !mResourceManager.parseResourcesFile("properties/resources.xml")) {
            showResourceError(true);
        }
    }

    // SexyAppBase::ShowResourceError (SexyAppBase.cpp:3021)
    public void showResourceError(boolean doExit) {
        String aError = (mResourceManager != null) ? mResourceManager.getErrorText() : "";

        String aMessage = "";
        if ((aError != null) && !aError.isEmpty()) {
            aMessage = aError + "\n\n";
        }
        aMessage += "Please place main.pak and the properties/ folder into:\n"
                + Common.getResourceFolder();

        popup(aMessage);
        if (doExit) {
            doExit(1);
        }
    }

    // SexyAppBase::GetBoolean (SexyAppBase.cpp:3053)
    public boolean getBoolean(String theId) {
        Boolean aValue = mBoolProperties.get(theId);
        return aValue != null ? aValue : false;
    }

    // SexyAppBase::GetBoolean (SexyAppBase.cpp:3064)
    public boolean getBoolean(String theId, boolean theDefault) {
        Boolean aValue = mBoolProperties.get(theId);
        return aValue != null ? aValue : theDefault;
    }

    // SexyAppBase::GetInteger (SexyAppBase.cpp:3074)
    public int getInteger(String theId) {
        Integer aValue = mIntProperties.get(theId);
        return aValue != null ? aValue : 0;
    }

    // SexyAppBase::GetInteger (SexyAppBase.cpp:3085)
    public int getInteger(String theId, int theDefault) {
        Integer aValue = mIntProperties.get(theId);
        return aValue != null ? aValue : theDefault;
    }

    // SexyAppBase::GetDouble (SexyAppBase.cpp:3095)
    public double getDouble(String theId) {
        Double aValue = mDoubleProperties.get(theId);
        return aValue != null ? aValue : 0.0;
    }

    // SexyAppBase::GetDouble (SexyAppBase.cpp:3106)
    public double getDouble(String theId, double theDefault) {
        Double aValue = mDoubleProperties.get(theId);
        return aValue != null ? aValue : theDefault;
    }

    // SexyAppBase::GetString (SexyAppBase.cpp:3116)
    public String getString(String theId) {
        String aValue = mStringProperties.get(theId);
        return aValue != null ? aValue : "";
    }

    // SexyAppBase::GetString (SexyAppBase.cpp:3127)
    public String getString(String theId, String theDefault) {
        String aValue = mStringProperties.get(theId);
        return aValue != null ? aValue : theDefault;
    }

    // SexyAppBase::GetStringVector (SexyAppBase.cpp:3137)
    public List<String> getStringVector(String theId) {
        List<String> aValue = mStringVectorProperties.get(theId);
        return aValue != null ? aValue : new ArrayList<>();
    }

    // SexyAppBase::SetString (SexyAppBase.cpp:3148)
    public void setString(String theId, String theValue) {
        mStringProperties.put(theId, theValue);
    }

    // SexyAppBase::SetBoolean (SexyAppBase.cpp:3156)
    public void setBoolean(String theId, boolean theValue) {
        mBoolProperties.put(theId, theValue);
    }

    // SexyAppBase::SetInteger (SexyAppBase.cpp:3163)
    public void setInteger(String theId, int theValue) {
        mIntProperties.put(theId, theValue);
    }

    // SexyAppBase::SetDouble (SexyAppBase.cpp:3170)
    public void setDouble(String theId, double theValue) {
        mDoubleProperties.put(theId, theValue);
    }

    // ------------------------------------------------------------------
    // Command line (SexyAppBase.cpp:3206-3320)
    // ------------------------------------------------------------------

    private static boolean paramTakesValue(String theParamName) {
        return switch (theParamName) {
            case "-play", "-playnum", "-record", "-recnum", "-resdir", "-savedir" -> true;
            default -> false;
        };
    }

    // SexyAppBase::DoParseCmdLine (SexyAppBase.cpp:3206)
    public void doParseCmdLine() {
        if (mArgv != null) {
            for (int i = 1; i < mArgc; i++) {
                String aParam = mArgv[i];
                String aValue = "";

                int anEqualsPos = aParam.indexOf('=');
                if (anEqualsPos != -1) {
                    aValue = aParam.substring(anEqualsPos + 1);
                    aParam = aParam.substring(0, anEqualsPos);
                } else if ((i + 1 < mArgc) && !mArgv[i + 1].startsWith("-") && paramTakesValue(aParam)) {
                    aValue = mArgv[++i];
                }

                handleCmdLineParam(aParam, aValue);
            }
        }

        // Resolve the demo file only after all params are parsed, so explicit files win regardless
        // of order
        if (mPlayingDemoBuffer && !mHasCustomDemoFile) {
            List<String> aDemoFiles = findDemoFiles(mDemoPrefix, false);
            if (aDemoFiles.isEmpty()) {
                popup("No demo recordings found");
                doExit(1);
                return;
            }
            mDemoFileName = aDemoFiles.get(Math.min(mDemoPlayIndex, aDemoFiles.size() - 1));
        } else if (mRecordingDemoBuffer && !mHasCustomDemoFile) {
            mDemoFileName = getTimestampedDemoFileName(mDemoPrefix);
        }

        mCmdLineParsed = true;
    }

    // SexyAppBase::SetArgs (SexyAppBase.cpp:3248)
    public void setArgs(String[] theArgs) {
        mArgc = theArgs.length;
        mArgv = theArgs;
    }

    // SexyAppBase::HandleCmdLineParam (SexyAppBase.cpp:3254)
    public void handleCmdLineParam(String theParamName, String theParamValue) {
        switch (theParamName) {
            case "-play", "-playnum" -> {
                mHasCustomDemoFile = false; // each occurrence fully redefines the request: last one wins
                mDemoPlayIndex = 0;
                if (theParamName.equals("-play") && !theParamValue.isEmpty()) {
                    mDemoFileName = theParamValue;
                    mHasCustomDemoFile = true;
                } else if (theParamName.equals("-playnum")) {
                    int aNum = stringToIntOr(theParamValue, 0);
                    mDemoPlayIndex = Math.max(aNum, 1) - 1;
                }
                mPlayingDemoBuffer = true;
                mRecordingDemoBuffer = false;
            }
            case "-record", "-recnum" -> {
                if (theParamName.equals("-recnum")) { // keep only the first N recordings
                    int aNum = stringToIntOr(theParamValue, 0);
                    if (aNum <= 0) {
                        aNum = 5;
                    }
                    mDemoRecordFileLimit = aNum;
                } else {
                    mHasCustomDemoFile = false;
                    if (!theParamValue.isEmpty()) {
                        mDemoFileName = theParamValue;
                        mHasCustomDemoFile = true;
                    }
                }
                mRecordingDemoBuffer = true;
                mPlayingDemoBuffer = false;
            }
            case "-crash" -> throw new NullPointerException("Crash requested by -crash");
            case "-screensaver" -> mIsScreenSaver = true;
            case "-resdir" -> mResourceDir = theParamValue;
            case "-savedir" -> mCustomSaveDir = theParamValue;
            default -> {
                popup(getString("INVALID_COMMANDLINE_PARAM", "Invalid command line parameter: ")
                        + theParamName);
                doExit(1);
            }
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle (SexyAppBase.cpp:1567-1660, :2910-2980, :3361-3510)
    // ------------------------------------------------------------------

    // SexyAppBase::InitHook (SexyAppBase.cpp:3350)
    public void initHook() {
    }

    // SexyAppBase::ShutdownHook (SexyAppBase.cpp:1567)
    public void shutdownHook() {
    }

    // SexyAppBase::PreTerminate (SexyAppBase.cpp:2928)
    public void preTerminate() {
    }

    // SexyAppBase::PreDisplayHook (SexyAppBase.cpp:3321)
    public void preDisplayHook() {
    }

    // SexyAppBase::PreGLInterfaceInitHook (SexyAppBase.cpp:3325)
    public void preGLInterfaceInitHook() {
    }

    // SexyAppBase::PostGLInterfaceInitHook (SexyAppBase.cpp:3329)
    public void postGLInterfaceInitHook() {
    }

    // SexyAppBase::ChangeDirHook (SexyAppBase.cpp:3333)
    public boolean changeDirHook(String theIntendedPath) {
        return false;
    }

    // SexyAppBase::InitPropertiesHook (SexyAppBase.cpp:3346)
    public void initPropertiesHook() {
    }

    // SexyAppBase::CreateMusicInterface (SexyAppBase.cpp:3338)
    public MusicInterface createMusicInterface() {
        if (mNoSoundNeeded) {
            return new DummyMusicInterface();
        }
        if (mAudioEngine != null) {
            return mAudioEngine.musicInterface();
        }
        // No device facade was created (a host may have assigned mMusicInterface itself).
        return mMusicInterface;
    }

    /**
     * Upstream creates an {@code SDLSoundManager} in {@code Init()} (SexyAppBase.cpp:3494). The
     * Java port opens the device through the {@code AudioEngine} facade
     * ({@link #mAudioEngine}), which also owns the music stream and the per-frame update.
     *
     * <p>{@code mNoSoundNeeded} skips device creation entirely, and a host may pre-assign
     * {@link #mSoundManager} (tests use an in-memory double); a {@code null} result means "no audio
     * backend", and every sound call is then a no-op through the null checks, exactly like
     * {@code PlaySample} with no sound manager upstream.
     */
    protected SoundManager createSoundManager() {
        if (mNoSoundNeeded) {
            return null;
        }

        if (mAudioEngine == null) {
            // Reused across init() calls: re-creating the engine would make a second ALC device
            // current and orphan the first one (upstream reuses mSoundManager likewise).
            mAudioEngine = new AudioEngine(PakInterface.getInstance());
        }
        return mAudioEngine.soundManager();
    }

    // SexyAppBase::Shutdown (SexyAppBase.cpp:1571)
    public void shutdown() {
        AppHooksRegistry.dispatcher().onGameExiting();

        if (!isOnPrimaryThread()) {
            mLoadingFailed.set(true);
        } else if (!mShutdown.get()) {
            if (mRecordingDemoBuffer) { // every quit path converges here; close the stream before any shutdown IO
                writeDemoTimingBlock();
                mDemoBuffer.writeNumBits(0, 1);
                mDemoBuffer.writeNumBits(DEMO_CLOSE, 5);
            }

            mExitToTop = true;
            mShutdown.set(true);
            shutdownHook();

            if (mPlayingDemoBuffer) {
                // if the music/sfx volume is 0, then it means that in playback someone pressed the
                // "S" key to mute sounds (or that the sound volume was set to 0 in the first
                // place). Out of politeness, return the system sound volume to what it last was
                // in the game.
                setMusicVolume(mDemoMusicVolume);
                setSfxVolume(mDemoSfxVolume);
            }

            if (mMusicInterface != null) {
                mMusicInterface.stopAllMusic();
            }

            restoreScreenResolution();

            if (mReadFromRegistry) {
                writeToRegistry();
            }
        }
    }

    // SexyAppBase::RestoreScreenResolution (SexyAppBase.cpp:1624)
    public void restoreScreenResolution() {
        // Screen resolution restoration not needed
    }

    // SexyAppBase::DoExit (SexyAppBase.cpp:1629)
    public void doExit(int theCode) {
        restoreScreenResolution();
        shutdown();
        // The C++ port calls exit(theCode) on desktop. The Java port deliberately does not
        // terminate the JVM (the framework also runs inside a mod host): shutdown() ends the main
        // loop and the launcher is responsible for process teardown.
        Common.logInfoLn("Exiting with code {}", theCode);
    }

    // SexyAppBase::Start (SexyAppBase.cpp:2946)
    public void start() {
        if (mShutdown.get()) {
            return;
        }

        startCursorThread();

        if (mAutoStartLoadingThread) {
            startLoadingThread();
        }

        int aStartTime = ticks();

        mRunning = true;
        mStartTick = aStartTime;
        mLastTime = aStartTime;
        mLastUserInputTick = aStartTime;
        mLastTimerTime = aStartTime;

        doMainLoop();

        processSafeDeleteList();

        mRunning = false;

        waitForLoadingThread();

        logPerfStats();

        preTerminate();

        writeToRegistry();
    }

    // SexyAppBase::LogPerfStats (SexyAppBase.cpp:2932)
    public void logPerfStats() {
        Common.logInfoLn("Seconds       = {}", (ticks() - mStartTick) / 1000.0);
        Common.logInfoLn("Sleep Count   = {}", mSleepCount);
        Common.logInfoLn("Update Count  = {}", mUpdateCount);
        Common.logInfoLn("Draw Count    = {}", mDrawCount);
        Common.logInfoLn("Draw Time     = {}", mDrawTime);
        Common.logInfoLn("Screen Blt    = {}", mScreenBltTime);
        if (mDrawTime + mScreenBltTime > 0) {
            Common.logInfoLn("Avg FPS       = {}",
                    (mDrawCount * 1000L) / (mDrawTime + mScreenBltTime));
        }
    }

    /**
     * Resolves the resource directory (where {@code main.pak} and {@code properties/} live).
     *
     * <p>Upstream only used the executable directory plus the {@code -resdir} override
     * (SexyAppBase.cpp:204-214, 3306-3308); the Java port additionally honours {@code PVZ_RES_DIR},
     * the current working directory and a locally installed Steam copy of the game
     * ({@link cn.enaium.fabricpvz.portable.pvzp.ResourceLocator}), so retail assets are picked up without copying.
     */
    protected String resolveResourceDirectory() {
        if (mResourceDir != null && !mResourceDir.isEmpty()) {
            return mResourceDir;
        }
        cn.enaium.fabricpvz.portable.pvzp.ResourceLocator.Discovery aDiscovery =
                cn.enaium.fabricpvz.portable.pvzp.ResourceLocator.discover(new String[0]);
        if (aDiscovery.resourceRoot() != null) {
            Common.logInfoLn("Using game resources from {} ({})", aDiscovery.resourceRoot(), aDiscovery.source());
            return aDiscovery.resourceRoot().toString();
        }
        // Nothing found: say what was searched (and what this JVM is), because the failure the game
        // reports next - a missing main.pak - is the same however the search went wrong.
        Common.logErrorLn("{}", cn.enaium.fabricpvz.portable.pvzp.ResourceLocator.diagnose(aDiscovery));
        return mResourceDir == null ? "" : mResourceDir;
    }

    // SexyAppBase::Init (SexyAppBase.cpp:3361)
    public void init() {
        mPrimaryThreadId = Thread.currentThread().threadId();

        if (mShutdown.get()) {
            return;
        }

        // Set resource directory (for main.pak, properties/, etc.)
        String aResourceDir = resolveResourceDirectory();
        if (!changeDirHook(aResourceDir)) {
            Common.setResourceFolder(aResourceDir);
            // The pak interface resolves loose files (images, properties, ...) relative to the same
            // resource folder; without this it would look in the process working directory instead.
            cn.enaium.fabricpvz.portable.sexy.pak.PakInterface.getInstance().setResourceFolder(aResourceDir);
        }

        try {
            PakInterface.getInstance().addPakFile(Paths.get(Common.getResourcePath("main.pak")));
        } catch (IOException anException) {
            Common.logErrorLn("Failed to open main.pak: {}", anException.getMessage());
        }

        // Set up demo recording stuff
        if (mPlayingDemoBuffer) { // must load before any demo-synced ops
            String[] anError = new String[1];
            if (!readDemoBuffer(anError)) {
                mPlayingDemoBuffer = false;
                popup(anError[0]);
                doExit(1);
                return;
            }
        }

        initPropertiesHook();

        // The app-data folder (upstream SDL_GetPrefPath) is provided by Common's platform defaults;
        // -savedir and the resource-folder fallback stay exactly as upstream.
        if (!mCmdLineParsed) {
            mCmdLineParsed = true;
        }

        if (isScreenSaver()) {
            mOnlyAllowOneCopyToRun = false;
        }

        // Handle custom save directory from -savedir parameter
        if (!mCustomSaveDir.isEmpty()) {
            Common.setAppDataFolder(mCustomSaveDir);
        } else if (Common.getAppDataFolder().isEmpty()) {
            Common.setAppDataFolder(Common.getResourcePath("savedata"));
        }

        readFromRegistry();

        if (!mPlayingDemoBuffer) { // demo playback restores mRandSeed from the demo file
            mRandSeed = ticks();
            Common.sRand(mRandSeed);

            if (mRecordingDemoBuffer) {
                long aNow = System.currentTimeMillis() / 1000L;
                mDemoStartTime = aNow; // synthetic clock base; mUpdateCount is still 0 here
                // local minus UTC; pure arithmetic, exact around DST
                mDemoTimeZoneOffset = java.time.ZoneId.systemDefault().getRules()
                        .getOffset(java.time.Instant.ofEpochSecond(aNow)).getTotalSeconds();
            }
        }

        Common.sRand(ticks());

        mIsWideWindow = false; // upstream: sizeof(char) > 1, which is false on every target

        // Let app do something before showing window, or switching to fullscreen mode
        // NOTE: Moved call to PreDisplayHook above mIsWindowed and GetSystemsMetrics
        // checks because the checks below use values that could change in PreDisplayHook.
        // PreDisplayHook must call mWidgetManager->Resize if it changes mWidth or mHeight.
        preDisplayHook();

        mWidgetManager.resize(logicalCanvasBounds(), logicalPresentationRect());
        mWidgetManager.ensureScreenImage(mWidth, mHeight);

        makeWindow();

        if (mRendererBackend == null) {
            Common.logErrorLn("FATAL: Failed to create OpenGL interface.");
            mShutdown.set(true);
            return;
        }

        if (mPlayingDemoBuffer) {
            // Get video data

            prepareDemoCommand(true);
            mDemoNeedsCommand = true;

            mIsWindowed = mDemoBuffer.readBoolean();
            mSyncRefreshRate = mDemoBuffer.readByte();
        }

        if (mSoundManager == null) {
            mSoundManager = createSoundManager();
        }

        setSfxVolume(mSfxVolume);

        mMusicInterface = createMusicInterface();

        setMusicVolume(mMusicVolume);

        if (isScreenSaver()) {
            setCursor(CURSOR_NONE);
        }

        initHook();

        initInput();

        mInitialized = true;

        AppHooksRegistry.dispatcher().onGameInitialized();
    }

    // SexyAppBase::MakeWindow (platform/default/Window.cpp:42)
    public void makeWindow() {
        if (mPlatformBackend == null) {
            // Default host wiring: SDL3 through the LWJGL bindings. Tests/embedders may inject their
            // own backend (or leave it null for fully headless runs).
            mPlatformBackend = cn.enaium.fabricpvz.portable.pvzp.platform.PlatformFactory.createPlatform();
        }
        if (mPlatformBackend != null) {
            mPlatformBackend.init(new PlatformBackend.Options(mWidth, mHeight, mTitle,
                    !mFullScreenWindow, false, !mIsWindowed));
            mPlatformBackend.setTitle(mTitle);
            mIsPhysWindowed = mIsWindowed;
        }
        if (mRendererBackend == null && mPlatformBackend != null) {
            // The renderer needs the window (created above) and is bound to the logical screen size;
            // PlatformFactory falls back to SDL's software renderer when no GPU driver is available.
            mRendererBackend = cn.enaium.fabricpvz.portable.pvzp.platform.PlatformFactory.createRenderer(
                    mPlatformBackend, mWidth, mHeight);
        }

        boolean isActive = mActive;
        mActive = mPlatformBackend != null;

        mPhysMinimized = false;
        if (mMinimized) {
            if (mMuteOnLostFocus) {
                unmute(true);
            }

            mMinimized = false;
            isActive = mActive; // set this here so we don't call RehupFocus again.
            rehupFocus();
        }

        if (isActive != mActive) {
            rehupFocus();
        }

        reInitImages();

        if (mWidgetManager.mImage == null) {
            mWidgetManager.ensureScreenImage(mWidth, mHeight);
        }
        mWidgetManager.markAllDirty();

        if (mRendererBackend != null) {
            mScreenBounds = logicalCanvasBounds();
            mWidgetManager.resize(mScreenBounds, logicalPresentationRect());
        }
    }

    // SexyAppBase::InitGLInterface (SexyAppBase.cpp:2910)
    public int initGLInterface() {
        preGLInterfaceInitHook();
        deleteNativeImageData();
        int aResult = (mRendererBackend != null) ? 1 : 0;
        demoSyncRefreshRate();
        if (aResult != 0) {
            mScreenBounds = logicalCanvasBounds();
            mWidgetManager.resize(mScreenBounds, logicalPresentationRect());
            postGLInterfaceInitHook();
        }
        return aResult;
    }

    // SexyAppBase::SwitchScreenMode (SexyAppBase.cpp:2346)
    public void switchScreenMode(boolean wantWindowed, boolean is3d, boolean force) {
        if (mForceFullscreen) {
            wantWindowed = false;
        }

        if ((mIsWindowed == wantWindowed) && !force) {
            set3DAcclerated(is3d, true);
            return;
        }

        // Set 3d acceleration preference
        set3DAcclerated(is3d, false);

        mIsWindowed = wantWindowed;

        if (mPlatformBackend != null && mInitialized) {
            // The window already exists: switch its mode instead of initialising the platform again
            // (upstream does the same inside Window::MakeWindow). Re-initialising threw
            // "platform already initialized" whenever fullscreen was toggled off again.
            mPlatformBackend.setFullscreen(!wantWindowed);
            mIsPhysWindowed = wantWindowed;
            if (mWidgetManager != null) {
                // The window changed size, so the logical canvas sits elsewhere on screen: refresh the
                // pointer mapping right away instead of waiting for SDL's resize event.
                mWidgetManager.resize(logicalCanvasBounds(), logicalPresentationRect());
                mWidgetManager.markAllDirty();
            }
        } else {
            makeWindow();
        }

        mLastTime = ticks();
    }

    // SexyAppBase::SwitchScreenMode (SexyAppBase.cpp:2392)
    public void switchScreenMode(boolean wantWindowed) {
        switchScreenMode(wantWindowed, is3DAccelerated(), false);
    }

    // SexyAppBase::SwitchScreenMode (SexyAppBase.cpp:2397)
    public void switchScreenMode() {
        switchScreenMode(mIsWindowed, is3DAccelerated(), true);
    }

    // SexyAppBase::SetAlphaDisabled (SexyAppBase.cpp:2402)
    public void setAlphaDisabled(boolean isDisabled) {
        if (mAlphaDisabled != isDisabled) {
            mAlphaDisabled = isDisabled;
            // Upstream also switches GLInterface to video-only drawing; the renderer backend owns
            // that switch in this port, so only the framework-side invalidation remains here.
            mWidgetManager.ensureScreenImage(mWidth, mHeight);
            mWidgetManager.markAllDirty();
        }
    }

    // SexyAppBase::DeleteNativeImageData (SexyAppBase.cpp:2248)
    public void deleteNativeImageData() {
        for (MemoryImage anImage : memoryImageSnapshot()) {
            anImage.delete3DBuffers();
        }
    }

    // SexyAppBase::DeleteExtraImageData (SexyAppBase.cpp:2265)
    public void deleteExtraImageData() {
        for (MemoryImage anImage : memoryImageSnapshot()) {
            anImage.deleteExtraBuffers();
        }
    }

    // SexyAppBase::ReInitImages (SexyAppBase.cpp:2282)
    public void reInitImages() {
        for (MemoryImage anImage : memoryImageSnapshot()) {
            anImage.reInit();
        }
    }

    private List<MemoryImage> memoryImageSnapshot() {
        synchronized (mMemoryImageSet) {
            return new ArrayList<>(mMemoryImageSet);
        }
    }

    // SexyAppBase::SEHOccured (SexyAppBase.cpp:1543)
    public void sehOccured() {
        setMusicVolume(0);
        mSEHOccured = true;
        enforceCursor();
    }

    // SexyAppBase::GetGameSEHInfo (SexyAppBase.cpp:1550)
    public String getGameSEHInfo() {
        StringBuilder anInfoString = new StringBuilder();

        anInfoString.append("\r\n").append('\n');
        anInfoString.append("Product: ").append(mProdName).append("\r\n");
        anInfoString.append("Version: ").append(mProductVersion).append("\r\n");
        anInfoString.append("Time: ").append(System.currentTimeMillis() / 1000L).append("\r\n");
        anInfoString.append("Random Seed: ").append(mRandSeed).append("\r\n");

        return anInfoString.toString();
    }

    // SexyAppBase::GetSEHWebParams (SexyAppBase.cpp:1565)
    public void getSEHWebParams(Map<String, String> theDefinesMap) {
    }

    // SexyAppBase::GetProductVersion (SexyAppBase.cpp:973)
    public String getProductVersion(String thePath) {
        return "0";
    }

    // SexyAppBase::HandleNotifyGameMessage (SexyAppBase.cpp:1957)
    public void handleNotifyGameMessage(int theType) {
    }

    // SexyAppBase::HandleGameAlreadyRunning (SexyAppBase.cpp:3510)
    public void handleGameAlreadyRunning() {
        if (mOnlyAllowOneCopyToRun) {
            doExit(0);
        }
    }

    // SexyAppBase::DebugKeyDown (SexyAppBase.cpp:2229)
    public boolean debugKeyDown(int theKey) {
        return false;
    }

    // SexyAppBase::CloseRequestAsync (SexyAppBase.cpp:2234)
    public void closeRequestAsync() {
    }

    // SexyAppBase::Done3dTesting (SexyAppBase.cpp:2238)
    public void done3dTesting() {
    }

    // SexyAppBase::NotifyCrashHook (SexyAppBase.cpp:2243)
    public String notifyCrashHook() {
        return "";
    }

    // SexyAppBase::ShowMemoryUsage (SexyAppBase.cpp:2224)
    public void showMemoryUsage() {
    }

    // SexyAppBase::Is3DAccelerated (SexyAppBase.h:504)
    public boolean is3DAccelerated() {
        return true;
    }

    // SexyAppBase::Is3DAccelerationSupported (SexyAppBase.cpp:4128)
    public boolean is3DAccelerationSupported() {
        return true;
    }

    // SexyAppBase::Is3DAccelerationRecommended (SexyAppBase.cpp:4133)
    public boolean is3DAccelerationRecommended() {
        return true;
    }

    // SexyAppBase::Set3DAcclerated (SexyAppBase.cpp:4153)
    public void set3DAcclerated(boolean is3D, boolean reinit) {
        // 3D acceleration toggle not implemented
    }

    public void set3DAcclerated(boolean is3D) {
        set3DAcclerated(is3D, true);
    }

    // SexyAppBase::LogScreenSaverError (SexyAppBase.cpp:1882)
    public void logScreenSaverError(String theError) {
    }

    // SexyAppBase::BeginPopup (SexyAppBase.cpp:1887)
    public void beginPopup() {
    }

    // SexyAppBase::EndPopup (SexyAppBase.cpp:1892)
    public void endPopup() {
        if (!mIsPhysWindowed) {
            mNoDefer = false;
        }

        clearUpdateBacklog(false);
        clearKeysDown();

        if (mWidgetManager.mDownButtons != 0) {
            mWidgetManager.doMouseUps();
        }
    }

    // SexyAppBase::MsgBox (SexyAppBase.cpp:1906)
    public int msgBox(String theText, String theTitle, int theFlags) {
        beginPopup();
        Common.logInfoLn("{}\n===\n{}", theTitle, theText);

        if ((mPlatformBackend != null) && isOnPrimaryThread()) {
            mPlatformBackend.showMessageBox(theTitle, theText, false);
        }

        endPopup();

        return 0;
    }

    public int msgBox(String theText, String theTitle) {
        return msgBox(theText, theTitle, 0);
    }

    public int msgBox(String theText) {
        return msgBox(theText, "Message", 0);
    }

    // SexyAppBase::Popup (SexyAppBase.cpp:1922)
    public void popup(String theString) {
        if (isScreenSaver()) {
            logScreenSaverError(theString);
            return;
        }

        beginPopup();
        if (!mShutdown.get()) {
            Common.logInfoLn("FATAL ERROR\n===\n{}", theString);

            if ((mPlatformBackend != null) && isOnPrimaryThread()) {
                mPlatformBackend.showMessageBox("FATAL ERROR", theString, true);
            }
        }

        endPopup();
    }

    // SexyAppBase::GotFocus (SexyAppBase.cpp:939)
    public void gotFocus() {
    }

    // SexyAppBase::LostFocus (SexyAppBase.cpp:943)
    public void lostFocus() {
    }

    // SexyAppBase::URLOpenFailed (SexyAppBase.cpp:947)
    public void urlOpenFailed(String theURL) {
        mIsOpeningURL = false;
    }

    // SexyAppBase::URLOpenSucceeded (SexyAppBase.cpp:952)
    public void urlOpenSucceeded(String theURL) {
        mIsOpeningURL = false;

        if (mShutdownOnURLOpen) {
            shutdown();
        }
    }

    // SexyAppBase::OpenURL (SexyAppBase.cpp:960)
    public boolean openURL(String theURL, boolean shutdownOnOpen) {
        if (!mIsOpeningURL || !theURL.equals(mOpeningURL)) {
            mShutdownOnURLOpen = shutdownOnOpen;
            mIsOpeningURL = true;
            mOpeningURL = theURL;
            mOpeningURLTime = ticks();
        }

        if (mPlatformBackend != null) {
            mPlatformBackend.openURL(theURL);
        }

        return true;
    }

    // ------------------------------------------------------------------
    // Clipboard, cursors (SexyAppBase.cpp:2425-2480, :3518-3564)
    // ------------------------------------------------------------------

    // SexyAppBase::CopyToClipboard (SexyAppBase.cpp:3518)
    public void copyToClipboard(String theString) {
        if (mPlayingDemoBuffer) {
            return;
        }

        if (mPlatformBackend != null) {
            mPlatformBackend.setClipboardText(theString);
        }
    }

    // SexyAppBase::GetClipboard (SexyAppBase.cpp:3526)
    public String getClipboard() {
        if (mPlayingDemoBuffer) {
            return "";
        }

        if (mPlatformBackend == null) {
            return "";
        }

        String aText = mPlatformBackend.getClipboardText();
        if (aText == null) {
            return "";
        }

        return aText;
    }

    // SexyAppBase::SetCursor (SexyAppBase.cpp:3540)
    public void setCursor(int theCursorNum) {
        mCursorNum = theCursorNum;
        enforceCursor();
    }

    // SexyAppBase::GetCursor (SexyAppBase.cpp:3546)
    public int getCursor() {
        return mCursorNum;
    }

    // SexyAppBase::EnableCustomCursors (SexyAppBase.cpp:3551)
    public void enableCustomCursors(boolean enabled) {
        if (mCustomCursorsEnabled == enabled) {
            enforceCursor();
            return;
        }

        mCustomCursorsEnabled = enabled;
        if (!enabled) {
            resetCustomCursorCache();
        }

        enforceCursor();
    }

    // SexyAppBase::SetCursorImage (SexyAppBase.cpp:984)
    public void setCursorImage(int theCursorNum, Image theImage) {
        if ((theCursorNum >= 0) && (theCursorNum < NUM_CURSORS)) {
            if ((mCustomCursorImageNum == theCursorNum) && (mCursorImages[theCursorNum] != theImage)) {
                resetCustomCursorCache();
            }

            mCursorImages[theCursorNum] = theImage;
            enforceCursor();
        }
    }

    // SexyAppBase::ResetCustomCursorCache (SexyAppBase.cpp:2413)
    public void resetCustomCursorCache() {
        mCustomCursorImage = null;
        mCustomCursorImageNum = -1;
    }

    // SexyAppBase::EnforceCursor (SexyAppBase.cpp:2425)
    public void enforceCursor() {
        int aCursorNum = mSEHOccured ? CURSOR_POINTER : mCursorNum;
        if (aCursorNum < 0 || aCursorNum >= NUM_CURSORS) {
            aCursorNum = CURSOR_POINTER;
        }

        if (mPlatformBackend == null) {
            return;
        }

        if (aCursorNum == CURSOR_NONE) {
            mPlatformBackend.hideCursor(true);
            return;
        }

        boolean aCustom = false;

        if (mCustomCursorsEnabled && (mCursorImages[aCursorNum] != null)
                && (mCursorImages[aCursorNum] instanceof MemoryImage)) {
            Image aCursorImage = mCursorImages[aCursorNum];
            if ((mCustomCursorImage == aCursorImage) && (mCustomCursorImageNum == aCursorNum)) {
                aCustom = true;
            } else {
                resetCustomCursorCache();

                mCustomCursorImage = aCursorImage;
                mCustomCursorImageNum = aCursorNum;
                aCustom = true;
            }
        }

        if (aCustom) {
            mPlatformBackend.setCustomCursor(mCustomCursorImage, 0, 0);
        } else {
            mPlatformBackend.setCursor(cursorNumToSystemCursor(aCursorNum));
        }

        mPlatformBackend.hideCursor(false);
    }

    /** Upstream {@code CursorNumToSystemCursor} (platform/default/Window.cpp). */
    private static PlatformBackend.CursorType cursorNumToSystemCursor(int theCursorNum) {
        return switch (theCursorNum) {
            case CURSOR_HAND -> PlatformBackend.CursorType.HAND;
            case CURSOR_TEXT -> PlatformBackend.CursorType.IBEAM;
            case CURSOR_CIRCLE_SLASH -> PlatformBackend.CursorType.NOT_ALLOWED;
            case CURSOR_SIZENS -> PlatformBackend.CursorType.VRESIZE;
            case CURSOR_SIZEWE -> PlatformBackend.CursorType.HRESIZE;
            default -> PlatformBackend.CursorType.ARROW;
        };
    }

    // SexyAppBase::StartCursorThread (SexyAppBase.cpp:2341)
    public void startCursorThread() {
        // Cursor thread not implemented
    }

    // SexyAppBase::CursorThreadProc (SexyAppBase.cpp:2332)
    public void cursorThreadProc() {
        // Cursor thread not implemented
    }

    // ------------------------------------------------------------------
    // Images (SexyAppBase.cpp:3566-3582, :3994-4008, :4102-4136, :4158-4252)
    // ------------------------------------------------------------------

    // SexyAppBase::GetImage (SexyAppBase.cpp:3566)
    public Image getImage(String theFileName, boolean commitBits) {
        // Upstream loads the file through ImageLib (which probes extensions and the `_name`/`name_`
        // alpha companions) and wraps the pixels into a GLImage carrying CPU bits; ImageLib is the
        // file-level loader, while ResourceManager loads *manifest ids*.
        cn.enaium.fabricpvz.portable.sexy.graphics.ImageLib.Image aLoadedImage =
                cn.enaium.fabricpvz.portable.sexy.graphics.ImageLib.getImage(theFileName, true);
        if (aLoadedImage == null) {
            return null;
        }

        GLImage anImage = new GLImage(mRendererBackend);
        anImage.setBits(aLoadedImage.mBits, aLoadedImage.mWidth, aLoadedImage.mHeight, commitBits);
        anImage.mFilePath = theFileName;
        return anImage;
    }

    public Image getImage(String theFileName) {
        return getImage(theFileName, true);
    }

    // SexyAppBase::SetSharedImage (SexyAppBase.cpp:4158)
    public void setSharedImage(String theFileName, String theVariant, SharedImageRef theImage) {
        String aKey = sharedImageKey(theFileName, theVariant);

        mSharedImageMap.put(aKey, theImage);
    }

    // SexyAppBase::GetSharedImage (SexyAppBase.cpp:4186)
    public SharedImageRef getSharedImage(String theFileName, String theVariant, boolean[] isNew) {
        String aKey = sharedImageKey(theFileName, theVariant);

        SharedImageRef aSharedImageRef = mSharedImageMap.get(aKey);
        boolean aNew = (aSharedImageRef == null);
        if (isNew != null) {
            isNew[0] = aNew;
        }

        if (aNew) {
            aSharedImageRef = new SharedImageRef();
            // Leading '!' means create a blank image rather than loading from file
            if (!theFileName.isEmpty() && (theFileName.charAt(0) == '!')) {
                GLImage aBlankImage =
                        (mRendererBackend != null) ? new GLImage(mRendererBackend) : new GLImage();
                // Blank shared images exist to be drawn into (ImageLib composite targets), so the
                // texture must be created as a render target rather than an immutable static one.
                aBlankImage.mRenderTarget = true;
                aSharedImageRef.set(aBlankImage);
            } else {
                aSharedImageRef.set(getImage(theFileName, false));
            }
            mSharedImageMap.put(aKey, aSharedImageRef);
        }

        return aSharedImageRef;
    }

    public SharedImageRef getSharedImage(String theFileName, String theVariant) {
        return getSharedImage(theFileName, theVariant, null);
    }

    public SharedImageRef getSharedImage(String theFileName) {
        return getSharedImage(theFileName, "", null);
    }

    private static String sharedImageKey(String theFileName, String theVariant) {
        return Common.stringToUpper(theFileName) + "\n" + Common.stringToUpper(theVariant);
    }

    // SexyAppBase::CleanSharedImages (SexyAppBase.cpp:4218)
    public void cleanSharedImages() {
        if (mCleanupSharedImages.get()) {
            // Upstream deletes the SharedImages whose reference count reached 0. The Java
            // SharedImageRef has no refcount (the GC owns lifetime), so the cleanup request
            // releases every handle the map holds and drops the entries; callers re-fetch through
            // GetSharedImage.
            for (SharedImageRef aRef : mSharedImageMap.values()) {
                if (aRef != null) {
                    aRef.release();
                }
            }
            mSharedImageMap.clear();

            mCleanupSharedImages.set(false);
        }
    }

    // SexyAppBase::AddMemoryImage (SexyAppBase.cpp:4102)
    public void addMemoryImage(MemoryImage theMemoryImage) {
        synchronized (mMemoryImageSet) {
            mMemoryImageSet.add(theMemoryImage);
        }
    }

    // SexyAppBase::RemoveMemoryImage (SexyAppBase.cpp:4108)
    public void removeMemoryImage(MemoryImage theMemoryImage) {
        synchronized (mMemoryImageSet) {
            mMemoryImageSet.remove(theMemoryImage);
        }

        remove3DData(theMemoryImage);
    }

    // SexyAppBase::Remove3DData (SexyAppBase.cpp:4121)
    public void remove3DData(MemoryImage theMemoryImage) {
        // Upstream drops the GLInterface 3D buffers of the image.
    }

    // SexyAppBase::CreateCrossfadeImage (SexyAppBase.cpp:3583)
    public GLImage createCrossfadeImage(Image theImage1, Rect theRect1, Image theImage2,
            Rect theRect2, double theFadeFactor) {
        return SexyImageOps.createCrossfadeImage(this, theImage1, theRect1, theImage2, theRect2,
                theFadeFactor);
    }

    // SexyAppBase::ColorizeImage (SexyAppBase.cpp:3649)
    public void colorizeImage(Image theImage, Color theColor) {
        SexyImageOps.colorizeImage(theImage, theColor);
    }

    // SexyAppBase::CreateColorizedImage (SexyAppBase.cpp:3707)
    public GLImage createColorizedImage(Image theImage, Color theColor) {
        return SexyImageOps.createColorizedImage(this, theImage, theColor);
    }

    // SexyAppBase::CopyImage (SexyAppBase.cpp:3778)
    public GLImage copyImage(Image theImage, Rect theRect) {
        return SexyImageOps.copyImage(this, theImage, theRect);
    }

    // SexyAppBase::CopyImage (SexyAppBase.cpp:3792)
    public GLImage copyImage(Image theImage) {
        return SexyImageOps.copyImage(this, theImage);
    }

    // SexyAppBase::MirrorImage (SexyAppBase.cpp:3797)
    public void mirrorImage(Image theImage) {
        SexyImageOps.mirrorImage(theImage);
    }

    // SexyAppBase::FlipImage (SexyAppBase.cpp:3821)
    public void flipImage(Image theImage) {
        SexyImageOps.flipImage(theImage);
    }

    // SexyAppBase::RotateImageHue (SexyAppBase.cpp:3848)
    public void rotateImageHue(MemoryImage theImage, int theDelta) {
        SexyImageOps.rotateImageHue(theImage, theDelta);
    }

    // SexyAppBase::HSLToRGB (SexyAppBase.cpp:3918)
    public int hslToRgb(int h, int s, int l) {
        return SexyImageOps.hslToRgb(h, s, l);
    }

    // SexyAppBase::RGBToHSL (SexyAppBase.cpp:3950)
    public int rgbToHsl(int r, int g, int b) {
        return SexyImageOps.rgbToHsl(r, g, b);
    }

    // SexyAppBase::HSLToRGB (SexyAppBase.cpp:3976)
    public void hslToRgb(int[] theSource, int[] theDest, int theSize) {
        SexyImageOps.hslToRgb(theSource, theDest, theSize);
    }

    // SexyAppBase::RGBToHSL (SexyAppBase.cpp:3985)
    public void rgbToHsl(int[] theSource, int[] theDest, int theSize) {
        SexyImageOps.rgbToHsl(theSource, theDest, theSize);
    }

    // NOTE: upstream PrecacheAdditive/PrecacheAlpha/PrecacheNative (SexyAppBase.cpp:3994-4008) are
    // not ported: their only callees (MemoryImage::GetRLAdditiveData/GetRLAlphaData/
    // GetNativeAlphaData) build the DirectDraw-era software blit tables that this GL-first port
    // dropped, and no game code calls them.

    // ------------------------------------------------------------------
    // Audio (SexyAppBase.cpp:4010-4100)
    // ------------------------------------------------------------------

    // SexyAppBase::PlaySample (SexyAppBase.cpp:4010)
    public void playSample(long theSoundNum) {
        if (mSoundManager == null) {
            return;
        }

        SoundInstance aSoundInstance = mSoundManager.getSoundInstance(theSoundNum);
        if (aSoundInstance != null) {
            aSoundInstance.play(false, true);
        }
    }

    // SexyAppBase::PlaySample (SexyAppBase.cpp:4023)
    public void playSample(long theSoundNum, int thePan) {
        if (mSoundManager == null) {
            return;
        }

        SoundInstance aSoundInstance = mSoundManager.getSoundInstance(theSoundNum);
        if (aSoundInstance != null) {
            aSoundInstance.setPan(thePan);
            aSoundInstance.play(false, true);
        }
    }

    // SexyAppBase::IsMuted (SexyAppBase.cpp:4036)
    public boolean isMuted() {
        return mMuteCount > 0;
    }

    // SexyAppBase::Mute (SexyAppBase.cpp:4041)
    public void mute(boolean autoMute) {
        mMuteCount++;
        if (autoMute) {
            mAutoMuteCount++;
        }

        setMusicVolume(mMusicVolume);
        setSfxVolume(mSfxVolume);
    }

    public void mute() {
        mute(false);
    }

    // SexyAppBase::Unmute (SexyAppBase.cpp:4051)
    public void unmute(boolean autoMute) {
        if (mMuteCount > 0) {
            mMuteCount--;
            if (autoMute) {
                mAutoMuteCount--;
            }
        }

        setMusicVolume(mMusicVolume);
        setSfxVolume(mSfxVolume);
    }

    public void unmute() {
        unmute(false);
    }

    // SexyAppBase::GetMusicVolume (SexyAppBase.cpp:4065)
    public double getMusicVolume() {
        return mMusicVolume;
    }

    // SexyAppBase::SetMusicVolume (SexyAppBase.cpp:4070)
    public void setMusicVolume(double theVolume) {
        mMusicVolume = theVolume;

        if (mMusicInterface != null) {
            mMusicInterface.setVolume((mMuteCount > 0) ? 0.0 : mMusicVolume);
        }
    }

    // SexyAppBase::GetSfxVolume (SexyAppBase.cpp:4078)
    public double getSfxVolume() {
        return mSfxVolume;
    }

    // SexyAppBase::SetSfxVolume (SexyAppBase.cpp:4083)
    public void setSfxVolume(double theVolume) {
        mSfxVolume = theVolume;

        if (mSoundManager != null) {
            mSoundManager.setVolume((mMuteCount > 0) ? 0.0 : mSfxVolume);
        }
    }

    // SexyAppBase::GetMasterVolume (SexyAppBase.cpp:4091)
    public double getMasterVolume() {
        if (mSoundManager == null) {
            return mSfxVolume;
        }
        return mSoundManager.getMasterVolume();
    }

    // SexyAppBase::SetMasterVolume (SexyAppBase.cpp:4096)
    public void setMasterVolume(double theMasterVolume) {
        mSfxVolume = theMasterVolume;
        if (mSoundManager != null) {
            mSoundManager.setMasterVolume(mSfxVolume);
        }
    }

    /** Upstream {@code SexyAppBase::SoundManager} accessor used by the platform/resource layer. */
    public SoundManager getSoundManager() {
        return mSoundManager;
    }

    // ------------------------------------------------------------------
    // Platform input hooks (platform/default/Input.cpp:333, :540)
    // ------------------------------------------------------------------

    // SexyAppBase::InitInput (platform/default/Input.cpp:333)
    public void initInput() {
        // Input is delivered through PlatformBackend.pollEvent(); nothing to set up here.
    }

    // SexyAppBase::ProcessDeferredMessages (platform/default/Input.cpp:540)
    /**
     * Drains pending platform events into the widget tree, exactly as the main loop does.
     *
     * <p>Public so host loops (tools, tests) can drive {@code doUpdateFrames}/{@code drawDirtyStuff}
     * themselves and still see input and window events; upstream keeps this inside
     * {@code SexyAppBase::Start}.
     */
    public boolean processDeferredMessages(boolean singleMessage) {
        if (mPlatformBackend == null) {
            return false;
        }

        PlatformEvent anEvent = new PlatformEvent();
        while (mPlatformBackend.pollEvent(anEvent)) {
            dispatchPlatformEvent(anEvent);
            if (singleMessage) {
                break;
            }
        }

        return false;
    }

    /**
     * The window-space rectangle the logical canvas is drawn into (upstream
     * {@code GLInterface::mPresentationRect}): mouse events arrive in window coordinates and
     * {@link WidgetManager#remapMouse} has to translate them back into the logical space the widgets
     * are laid out in. Falling back to the logical rect keeps the mapping the identity when no
     * renderer is present (headless runs) or when the window already has the logical size.
     */
    private Rect logicalPresentationRect() {
        if (mRendererBackend == null) {
            return new Rect(0, 0, mWidth, mHeight);
        }
        int[] aRect = mRendererBackend.logicalPresentationRect();
        if (aRect == null || aRect.length < 4 || aRect[2] <= 0 || aRect[3] <= 0) {
            return new Rect(0, 0, mWidth, mHeight);
        }
        return new Rect(aRect[0], aRect[1], aRect[2], aRect[3]);
    }

    /**
     * The coordinate space the widgets live in: the renderer's logical canvas (the screen image is
     * {@code mWidth x mHeight} at boot), or the app size when there is no renderer.
     */
    private Rect logicalCanvasBounds() {
        if (mRendererBackend == null || mRendererBackend.logicalWidth() <= 0
                || mRendererBackend.logicalHeight() <= 0) {
            return new Rect(0, 0, mWidth, mHeight);
        }
        return new Rect(0, 0, mRendererBackend.logicalWidth(), mRendererBackend.logicalHeight());
    }

    private void dispatchPlatformEvent(PlatformEvent theEvent) {
        switch (theEvent.type) {
            case MOUSE_MOTION -> {
                int[] aX = {theEvent.x};
                int[] aY = {theEvent.y};
                mWidgetManager.remapMouse(aX, aY);
                mWidgetManager.mouseMove(aX[0], aY[0]);
            }
            case MOUSE_DOWN -> {
                mLastUserInputTick = mLastTimerTime;
                int[] aX = {theEvent.x};
                int[] aY = {theEvent.y};
                mWidgetManager.remapMouse(aX, aY);
                mWidgetManager.mouseDown(aX[0], aY[0], platformButtonToClickCount(theEvent.button));
            }
            case MOUSE_UP -> {
                mLastUserInputTick = mLastTimerTime;
                int[] aX = {theEvent.x};
                int[] aY = {theEvent.y};
                mWidgetManager.remapMouse(aX, aY);
                mWidgetManager.mouseUp(aX[0], aY[0], platformButtonToClickCount(theEvent.button));
            }
            case MOUSE_WHEEL -> {
                mLastUserInputTick = mLastTimerTime;
                mWidgetManager.mouseWheel(theEvent.dy);
            }
            case KEY_DOWN -> {
                mLastUserInputTick = mLastTimerTime;

                if (mAllowAltEnter && !theEvent.repeat
                        && (theEvent.keyCode == KeyCode.KEYCODE_RETURN)
                        && mAltDown) {
                    switchScreenMode(!mIsWindowed);
                    break;
                }

                mWidgetManager.keyDown(theEvent.keyCode);
            }
            case KEY_UP -> {
                mLastUserInputTick = mLastTimerTime;
                mWidgetManager.keyUp(theEvent.keyCode);
            }
            case TEXT_INPUT -> {
                mLastUserInputTick = mLastTimerTime;
                if (theEvent.text != null) {
                    mWidgetManager.keyText(theEvent.text);
                }
            }
            case WINDOW_FOCUS_GAINED -> {
                mActive = true;
                rehupFocus();
            }
            case MOUSE_LEAVE -> {
                // The pointer left the window: a press that started inside and ended outside never
                // delivers its release, which left the widget manager holding a button down and turned
                // every following click into a drag (clicks appeared dead until a full click pair arrived).
                mWidgetManager.doMouseUps();
                mWidgetManager.mMouseIn = false;
            }
            case WINDOW_FOCUS_LOST -> {
                mActive = false;
                rehupFocus();
            }
            case WINDOW_RESIZE -> {
                // The window size does not change the game's logical resolution (upstream keeps the GL
                // interface at the game size and centres it - Input.cpp:611-646 only re-runs
                // WidgetManager::Resize with the new presentation rect). Following the window size here
                // would lay every later screen out for a canvas the renderer has already scaled.
                mWidgetManager.resize(logicalCanvasBounds(), logicalPresentationRect());
            }
            case WINDOW_CLOSE, QUIT -> shutdown();
            case DROP_FILE -> handleDroppedFile(theEvent.fileName);
        }
    }

    /** Platform mouse button id (1 left, 2 middle, 3 right) → framework click count. */
    private static int platformButtonToClickCount(int theButton) {
        return switch (theButton) {
            case 1 -> 1;
            case 3 -> 3; // right
            case 2 -> 3; // middle — upstream's middle encoding is the click count 3
            default -> 1;
        };
    }

    /** Upstream {@code SDL_DROPFILE} handling lives in the platform layer; the base is a no-op. */
    protected void handleDroppedFile(String theFileName) {
    }

    // SexyAppBase::StartTextInput (SexyAppBase.h:544)
    public boolean startTextInput(StringBuilder theInput) {
        // Soft-keyboard input is a platform capability; this base returns false exactly like the
        // desktop upstream build (the Switch backend is the only one that returns true).
        return false;
    }

    // SexyAppBase::StopTextInput (SexyAppBase.h:545)
    public void stopTextInput() {
    }

    // SexyAppBase::SetTextInputRect (SexyAppBase.h:546)
    public void setTextInputRect(Rect theRect) {
    }
}
