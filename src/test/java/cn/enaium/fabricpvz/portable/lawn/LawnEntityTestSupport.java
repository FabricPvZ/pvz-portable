/*
 * Headless fixtures for the GridItem / LawnMower / SeedPacket / SeedBank / CursorObject tests.
 *
 * The entity ctors call into LawnApp for reanimations, particles and audio; retail art is not
 * available in a unit test, so {@link TestLawnApp} short-circuits those services with recording
 * test doubles ({@link StubReanimation}) and {@link StubZombie} stands in for the (much heavier)
 * Zombie entity. Everything else — grid maths, planting rules, state machines — runs against the
 * real {@link Board}.
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.util.ArrayList;
import java.util.List;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorDefinition;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

final class LawnEntityTestSupport {

    private LawnEntityTestSupport() {
    }

    // ---------------------------------------------------------------------------------------
    // App double
    // ---------------------------------------------------------------------------------------

    /** A {@link LawnApp} whose effect/audio services are recording test doubles. */
    static class TestLawnApp extends LawnApp {

        final StubReanimation mReanim = new StubReanimation();
        final PvzpParticleSystem mParticle = new PvzpParticleSystem();
        int mAddedReanimations;
        int mRemovedReanimationID = ReanimationID.REANIMATIONID_NULL;
        int mAddedParticles;
        final List<Integer> mFoleysPlayed = new ArrayList<>();
        final List<Long> mSamplesPlayed = new ArrayList<>();

        /**
         * The real {@code LawnApp::AddReanimation} allocates out of the global holder, so any port
         * code that later looks the new reanimation up there (e.g. {@code Attachment.attachReanim}
         * from {@code Coin.coinInitialize}, or the EffectSystem update) succeeds. The double
         * reproduces that by installing the shared stub as the holder's factory.
         */
        private cn.enaium.fabricpvz.portable.pvzp.DataArray<Reanimation> mStubReanims;
        private cn.enaium.fabricpvz.portable.pvzp.DataArray<PvzpParticleSystem> mStubParticles;

        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            mAddedReanimations++;
            mReanim.mAnimRate = 0.0f;
            if (mStubReanims == null) {
                mStubReanims = new cn.enaium.fabricpvz.portable.pvzp.DataArray<>(() -> mReanim, 1024, "stubreanims");
                cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem.gEffectSystem.mReanimationHolder.mReanimations = mStubReanims;
            }
            if (mStubReanims.mSize == 0) {
                // The shared stub occupies one live slot; a free-all (Board ctor) empties the array, so
                // re-register lazily. Registering it once keeps DataArrayFreeAll duplicate-free.
                mStubReanims.dataArrayAlloc();
            }
            return mReanim;
        }

        @Override
        public int reanimationGetID(Reanimation theReanimation) {
            return 1;
        }

        @Override
        public Reanimation reanimationGet(int theReanimationID) {
            return theReanimationID == ReanimationID.REANIMATIONID_NULL ? null : mReanim;
        }

        @Override
        public Reanimation reanimationTryToGet(int theReanimationID) {
            return theReanimationID == ReanimationID.REANIMATIONID_NULL ? null : mReanim;
        }

        @Override
        public void removeReanimation(int theReanimationID) {
            mRemovedReanimationID = theReanimationID;
        }

        @Override
        public PvzpParticleSystem addPvzpParticle(float theX, float theY, int theRenderOrder, int theEffect) {
            mAddedParticles++;
            if (mStubParticles == null) {
                mStubParticles = new cn.enaium.fabricpvz.portable.pvzp.DataArray<>(() -> mParticle, 1024, "stubparticles");
                cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems = mStubParticles;
            }
            if (mStubParticles.mSize == 0) {
                mStubParticles.dataArrayAlloc();
            }
            return mParticle;
        }

        @Override
        public int particleGetID(PvzpParticleSystem theParticle) {
            return mAddedParticles;
        }

        @Override
        public PvzpParticleSystem particleTryToGet(int theParticleID) {
            return null;
        }

        @Override
        public void playFoley(int theFoleyType) {
            mFoleysPlayed.add(theFoleyType);
        }

        @Override
        public void playSample(long theSoundNum) {
            mSamplesPlayed.add(theSoundNum);
        }
    }

    /**
     * Records everything entity code does to a reanimation (no retail .reanim data needed).
     *
     * <p>The stub stands in for a <em>loaded</em> reanimation. {@code ReanimationInitialize}
     * allocates one {@link ReanimatorTrackInstance} per definition track (Reanimator.cpp:397-421),
     * so entity code may assume {@code mTrackInstances} is non-null: {@code GetTrackInstanceByName}
     * indexes it (Reanimator.cpp:974-977) and {@code Zombie.reanimReenableClipping} walks the array
     * (Zombie.cpp:997-1007). With no .reanim file to parse, the stub fakes the loaded state with an
     * empty definition plus a fixed track array via {@link #stubLoadedTracks}; {@code update} and
     * {@code draw} stay no-ops.
     */
    static class StubReanimation extends Reanimation {

        String mLastTrack;
        int mLastLoopType;
        float mLastAnimRate;
        float mSetX;
        float mSetY;
        float mScaleX = 1.0f;
        float mScaleY = 1.0f;
        int mUpdateCount;
        int mDieCount;
        boolean mTimedEvent;

        StubReanimation() {
            // An empty definition is what a .reanim that fails to parse loads as; Reanimation's own
            // ctor leaves mDefinition null, which the by-name lookups (Reanimator.tracksOf) cannot
            // tolerate.
            mDefinition = new ReanimatorDefinition();
            cn.enaium.fabricpvz.portable.test.HeadlessTestApp.stubLoadedTracks(this);
        }

        @Override
        public void update() {
            mUpdateCount++;
        }

        @Override
        public void draw(Graphics g) {
        }

        @Override
        public void playReanim(String theTrackName, int theLoopType, int theBlendTime, float theAnimRate) {
            mLastTrack = theTrackName;
            mLastLoopType = theLoopType;
            mLastAnimRate = theAnimRate;
        }

        @Override
        public void setFramesForLayer(String theTrackName) {
            mLastTrack = theTrackName;
        }

        @Override
        public void overrideScale(float theScaleX, float theScaleY) {
            mScaleX = theScaleX;
            mScaleY = theScaleY;
        }

        @Override
        public void setPosition(float theX, float theY) {
            mSetX = theX;
            mSetY = theY;
        }

        @Override
        public boolean shouldTriggerTimedEvent(float theEventTime) {
            return mTimedEvent;
        }

        @Override
        public void reanimationDie() {
            mDieCount++;
        }
    }

    /** A minimal Zombie with the collision/damage surface the mower and rake touch. */
    static class StubZombie extends Zombie {

        Rect mStubRect;
        boolean mDamageable = true;
        boolean mBobsledTeamWithSled;
        boolean mTangleKelpTarget;
        boolean mDeadOrDying;
        boolean mMowedDown;
        boolean mDiedWithLoot;
        int mLastDamage;
        int mLastDamageFlags;

        StubZombie(Board theBoard, int theRow, int theX, int theY) {
            mApp = theBoard.mApp;
            mBoard = theBoard;
            mRow = theRow;
            mX = theX;
            mY = theY;
            mWidth = 44;
            mHeight = 115;
            mZombieType = ZombieType.ZOMBIE_NORMAL;
            mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
            mHasHead = true;
            mStubRect = new Rect(theX, theY, mWidth, mHeight);
        }

        @Override
        public Rect getZombieRect() {
            return mStubRect;
        }

        @Override
        public boolean effectedByDamage(int theDamageRangeFlags) {
            return mDamageable;
        }

        @Override
        public boolean isDeadOrDying() {
            return mDeadOrDying;
        }

        @Override
        public boolean isBobsledTeamWithSled() {
            return mBobsledTeamWithSled;
        }

        @Override
        public boolean isTangleKelpTarget() {
            return mTangleKelpTarget;
        }

        @Override
        public void mowDown() {
            mMowedDown = true;
            mDead = true;
        }

        @Override
        public void dieWithLoot() {
            mDiedWithLoot = true;
            mDead = true;
        }

        @Override
        public void takeDamage(int theDamage, int theDamageFlags) {
            mLastDamage = theDamage;
            mLastDamageFlags = theDamageFlags;
        }
    }

    /**
     * A {@link Board} whose zombie container is filled with {@link StubZombie} doubles, so entity
     * tests can drive collisions without Zombie's animation/effect dependencies.
     */
    static class StubBoard extends Board {

        StubBoard(TestLawnApp theApp) {
            super(theApp);
            // The pool factory runs before a caller fills a zombie in, and StubZombie's ctor reads
            // the board it is created for — so hand it this board rather than null.
            mZombies = new cn.enaium.fabricpvz.portable.pvzp.DataArray<Zombie>(() -> new StubZombie(this, 0, 0, 0), 32, "stubzombies");
        }

        StubZombie addStubZombie(int theRow, int theX, int theY) {
            StubZombie zombie = (StubZombie) mZombies.dataArrayAlloc();
            zombie.mApp = mApp;
            zombie.mBoard = this;
            zombie.mRow = theRow;
            zombie.mX = theX;
            zombie.mY = theY;
            zombie.mWidth = 44;
            zombie.mHeight = 115;
            zombie.mZombieType = ZombieType.ZOMBIE_NORMAL;
            zombie.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
            zombie.mHasHead = true;
            zombie.mStubRect = new Rect(theX, theY, 44, 115);
            return zombie;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Factories
    // ---------------------------------------------------------------------------------------

    /**
     * A headless adventure app: the {@link TestLawnApp} double plus everything
     * {@link #initializeHeadlessApp(LawnApp)} installs (effect system, audio facades, the shared
     * foley/reanim/particle/trail tables and the stub images). Building the board through this
     * factory therefore does not depend on some other test having initialised those process-wide
     * tables first.
     */
    static TestLawnApp newApp() {
        TestLawnApp app = new TestLawnApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mGameScene = GameScenes.SCENE_PLAYING;
        app.mAppRandSeed = 0x5EED;
        app.mPlayerInfo = new PlayerInfo();
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);
        // The Board ctor builds a SeedBank, whose upstream ctor measures IMAGE_SEEDBANK/IMAGE_SEEDS;
        // both keep their own placeholder bitmaps instead of the generic 1x1 stub image.
        Resources.setImageById(ImageID.IMAGE_SEEDBANK, new MemoryImage(app));
        Resources.setImageById(ImageID.IMAGE_SEEDS, new MemoryImage(app));
        return app;
    }

    /** A fresh Board (classic 5-lane front lawn by default, level 1) for the given app. */
    static Board newBoard(TestLawnApp theApp) {
        Board board = new Board(theApp);
        board.mLevel = 1;
        board.mMainCounter = 5;
        board.mSunMoney = 150;
        return board;
    }

    /** The standard app + board pair used by the entity tests. */
    static Board newBoard() {
        return newBoard(newApp());
    }

    /** App + {@link StubBoard} (stub zombie list) pair. */
    static StubBoard newStubBoard(TestLawnApp theApp) {
        StubBoard board = new StubBoard(theApp);
        board.mLevel = 1;
        board.mMainCounter = 5;
        board.mSunMoney = 150;
        return board;
    }

}
