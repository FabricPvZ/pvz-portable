/*
 * Shared headless fixture for every suite that builds a {@link LawnApp}.
 *
 * {@code LawnApp}'s constructor does no platform/GL/filesystem work, but everything downstream
 * (boards, widgets, entity ctors) reads the services {@code LawnApp::Init()} and the loading thread
 * create: the profile manager, the effect system, music/foley facades, the foley/reanim/particle/
 * trail definition tables and the loaded image/font resources. A unit test that calls
 * {@code new LawnApp()} and then builds any of those hits a null dereference.
 *
 * Tests call {@link #initialize} right after constructing the app; the resource tables are filled
 * with 1x1 stub images and stub bitmap fonts (see {@link #bindStubImages}/{@link #bindStubFonts}),
 * so no retail assets are needed. Suites that assert real pixel/glyph content stay gated on
 * {@link cn.enaium.fabricpvz.portable.pvzp.TestAssets} instead.
 */
package cn.enaium.fabricpvz.portable.test;

import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpFoley;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleDefinitions;
import cn.enaium.fabricpvz.portable.pvzp.fx.TrailDefinitions;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.ImageFont;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.sound.DummyMusicInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

public final class HeadlessTestApp {

    private HeadlessTestApp() {
    }

    /** Temp folder the headless fixtures keep their definition caches in; created on first use. */
    private static String sHeadlessAppDataFolder;

    private static synchronized String headlessAppDataFolder() {
        if (sHeadlessAppDataFolder == null) {
            try {
                sHeadlessAppDataFolder =
                        java.nio.file.Files.createTempDirectory("pvz-headless-appdata").toString();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot create the headless app-data folder", e);
            }
        }
        return sHeadlessAppDataFolder;
    }

    /**
     * Gives a headless {@link LawnApp} everything {@code LawnApp::Init()} and the loading thread
     * would normally create: a profile, the effect system, the audio facades and the process-wide
     * definition tables, plus stub images/fonts for every resource id. Tests build their app with
     * this so game code can run without a window, an audio device or the retail assets.
     */
    public static void initialize(LawnApp theApp) {
        // These fixtures boot with a partial, synthetic resource set: an installed resource-group
        // extractor would try to bind resources a synthetic pak does not contain and the load would
        // fail. Tests that boot the real game call Main.installHooks() themselves; keeping this null
        // also makes test order irrelevant (the field is static).
        cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager.gExtractResourcesByName = null;

        // The definition engine caches compiled definitions under the app-data folder, and a headless
        // app never sets one: without this it would be the process' working directory, i.e. the project,
        // where the dump tasks and the real-asset tests leave a cache64/ behind. A later asset-less test
        // then reads those caches and gets definitions its own resource layer cannot complete
        // (../verification/VERIFICATION.md §48.2). One temporary folder per process keeps every headless run
        // hermetic; tests that want their own folder still set one and restore this one.
        cn.enaium.fabricpvz.portable.sexy.Common.setAppDataFolder(headlessAppDataFolder());

        LawnApp.gLawnApp = theApp;
        if (theApp.mZenGarden == null) {
            // LoadingThreadProc creates the garden in the real game (LawnApp.cpp:1685); headless runs
            // skip that thread, and Board::DisposeBoard / the zen branches dereference it.
            theApp.mZenGarden = new cn.enaium.fabricpvz.portable.lawn.ZenGarden();
        }
        if (theApp.mProfileMgr == null) {
            theApp.mProfileMgr = new cn.enaium.fabricpvz.portable.lawn.system.ProfileMgr();
        }
        if (theApp.mPlayerInfo == null) {
            theApp.mPlayerInfo = theApp.mProfileMgr.addProfile("HeadlessTest");
        }
        if (theApp.mEffectSystem == null) {
            theApp.mEffectSystem = new cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem();
            theApp.mEffectSystem.effectSystemInitialize();
        }
        if (theApp.mReanimatorCache == null) {
            // LawnApp::Init creates it (LawnApp.cpp:1178-1179): the cached seed/mower/zombie frames
            // the seed packets, the almanac and the cursor preview draw from.
            theApp.mReanimatorCache = new cn.enaium.fabricpvz.portable.lawn.system.ReanimatorCache();
            theApp.mReanimatorCache.reanimatorCacheInitialize();
        }
        if (theApp.mMusicInterface == null) {
            // Headless runs must not open an audio device; the dummy interface records calls only.
            // Music captures the interface in its constructor, so it has to be installed first.
            theApp.mMusicInterface = new DummyMusicInterface();
        }
        if (theApp.mSoundManager == null) {
            theApp.mSoundManager = new StubSoundManager();
        }
        if (theApp.mMusic == null) {
            theApp.mMusic = new cn.enaium.fabricpvz.portable.lawn.system.Music();
        }
        if (theApp.mSoundSystem == null) {
            theApp.mSoundSystem = new PvzpFoley();
        }
        // The loading thread performs these two global initialisations in the real game.
        PvzpFoley.pvzpFoleyInitialize(PvzpFoley.gLawnFoleyParamArray, PvzpFoley.gLawnFoleyParamArray.length);
        Reanimator.reanimatorLoadDefinitions(Reanimator.gLawnReanimationArray, ReanimationType.NUM_REANIMS);
        // Particle/trail definition tables are filled by the loading thread; without retail assets the
        // individual files fail to parse (logged) but the tables exist, so emission paths become no-ops
        // instead of throwing.
        PvzpParticleDefinitions.pvzpParticleLoadDefinitions(
                PvzpParticleDefinitions.gLawnParticleArray,
                PvzpParticleDefinitions.gLawnParticleArray.length);
        TrailDefinitions.trailLoadDefinitions(
                TrailDefinitions.gLawnTrailArray, TrailDefinitions.gLawnTrailArray.length);
        bindStubImages(theApp);
        bindStubFonts(theApp);
        // LawnApp::LoadingThreadProc sets the framework's fallback font from the loaded resources
        // (LawnApp.java:1440); widgets built headlessly rely on it (Dialogs, buttons, edit widgets).
        theApp.mDefaultFont = Resources.getFontById(Resources.FONT_PICO129_ID);
    }

    /**
     * Binds a stub image for every image resource id, so game code that draws or measures an image
     * (seed banks, packets, backgrounds, ...) works headlessly without the retail assets. Tests that
     * assert real pixel content stay asset-gated with {@link cn.enaium.fabricpvz.portable.pvzp.TestAssets}.
     */
    public static void bindStubImages(SexyAppBase theApp) {
        for (Resources.ResourceEntry anEntry : Resources.ENTRIES) {
            if (anEntry.type() == Resources.ResourceType.IMAGE) {
                MemoryImage aStub = new MemoryImage(theApp);
                aStub.create(1, 1);
                Resources.setImageById(anEntry.id(), aStub);
            }
        }
    }

    /**
     * Binds a stub bitmap font for every font resource id. {@code GameButton::SetFont}
     * (GameButton.cpp:128) and every measuring widget dereference the font, so a widget constructed
     * without the {@code LoadingFonts} group would otherwise fail with a null dereference.
     *
     * <p>The stub is an {@link ImageFont} built from a 1x1 image — the port's stand-in for upstream's
     * single-layer {@code ImageFont(Image*)} ctor, which measures as zero width but supports the full
     * {@link Font} surface (duplicate/drawStringEx/charWidth) without a descriptor file.
     */
    public static void bindStubFonts(SexyAppBase theApp) {
        for (Resources.ResourceEntry anEntry : Resources.ENTRIES) {
            if (anEntry.type() == Resources.ResourceType.FONT) {
                Resources.setFontById(anEntry.id(), newStubFont(theApp));
            }
        }
    }

    /** A fresh headless bitmap font: measures as zero width, never dereferences a null glyph sheet. */
    public static Font newStubFont(SexyAppBase theApp) {
        MemoryImage aGlyphSheet = new MemoryImage(theApp);
        aGlyphSheet.create(1, 8);
        return new ImageFont(aGlyphSheet);
    }

    /**
     * Number of track instances given to a reanimation that could not load .reanim data (see
     * {@link #stubLoadedTracks}). A by-name lookup that matches no track falls back to index 0
     * (Reanimator.cpp:964-972), and callers such as {@code Zombie.reanimReenableClipping} walk the
     * whole array, so every entry has to exist; 64 covers the largest retail zombie/plant
     * reanimation.
     */
    public static final int STUB_REANIM_TRACK_COUNT = 64;

    /**
     * Makes a reanimation that could not load its .reanim data look loaded.
     *
     * <p>{@code ReanimationInitialize} allocates one {@link ReanimatorTrackInstance} per definition
     * track, and only when the definition has tracks (Reanimator.cpp:397-421). Without retail assets
     * every definition parses to zero tracks, so {@code mTrackInstances} stays null — yet entity code
     * indexes it regardless: {@code GetTrackInstanceByName} (Reanimator.cpp:974-977) is reached from
     * {@code Zombie::EnableFuture} -> {@code SetImageOverride} (Reanimator.cpp:1135-1138), and
     * {@code Zombie::ReanimReenableClipping} walks the array (Zombie.cpp:997-1007). Tests that drive
     * entity setup headlessly call this so the reanimation carries the per-track state a loaded one
     * would; a by-name lookup that finds no track resolves to index 0 (Reanimator.cpp:964-972), so
     * the array must be non-empty and fully initialised.
     */
    public static void stubLoadedTracks(Reanimation theReanim) {
        if (theReanim.mTrackInstances != null) {
            return;
        }
        theReanim.mTrackInstances = new ReanimatorTrackInstance[STUB_REANIM_TRACK_COUNT];
        for (int aTrackIndex = 0; aTrackIndex < STUB_REANIM_TRACK_COUNT; aTrackIndex++) {
            theReanim.mTrackInstances[aTrackIndex] = new ReanimatorTrackInstance();
        }
    }

    /** Silent SoundManager double: headless tests must not open an audio device. */
    public static final class StubSoundManager implements SoundManager {
        private double mMasterVolume = 0.5525;

        @Override
        public boolean initialized() {
            return true;
        }

        @Override
        public boolean loadSound(long theSfxID, String theFilename) {
            return true;
        }

        @Override
        public long loadSound(String theFilename) {
            return 0;
        }

        @Override
        public void releaseSound(long theSfxID) {
        }

        @Override
        public void setVolume(double theVolume) {
        }

        @Override
        public boolean setBaseVolume(long theSfxID, double theBaseVolume) {
            return true;
        }

        @Override
        public boolean setBasePan(long theSfxID, int theBasePan) {
            return true;
        }

        @Override
        public SoundInstance getSoundInstance(long theSfxID) {
            return new StubSoundInstance();
        }

        @Override
        public void releaseSounds() {
        }

        @Override
        public void releaseChannels() {
        }

        @Override
        public double getMasterVolume() {
            return mMasterVolume;
        }

        @Override
        public void setMasterVolume(double theVolume) {
            mMasterVolume = theVolume;
        }

        @Override
        public void flush() {
        }

        @Override
        public void stopAllSounds() {
        }

        @Override
        public long getFreeSoundId() {
            return 0;
        }

        @Override
        public int getNumSounds() {
            return 0;
        }
    }

    /** Silent SoundInstance double. */
    public static final class StubSoundInstance implements SoundInstance {
        private double mVolume = 1.0;
        private boolean mPlaying;

        @Override
        public void release() {
        }

        @Override
        public void setBaseVolume(double theBaseVolume) {
        }

        @Override
        public void setBasePan(int theBasePan) {
        }

        @Override
        public void adjustPitch(double theNumSteps) {
        }

        @Override
        public void setVolume(double theVolume) {
            mVolume = theVolume;
        }

        @Override
        public void setPan(int thePosition) {
        }

        @Override
        public boolean play(boolean looping, boolean autoRelease) {
            mPlaying = true;
            return true;
        }

        @Override
        public void stop() {
            mPlaying = false;
        }

        @Override
        public boolean isPlaying() {
            return mPlaying;
        }

        @Override
        public boolean isReleased() {
            return false;
        }

        @Override
        public double getVolume() {
            return mVolume;
        }
    }
}
