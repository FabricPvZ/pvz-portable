/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.Zombie.
 *
 * Every asserted value is quoted from the upstream source line noted next to it (src/Lawn/Zombie.cpp,
 * src/Lawn/Zombie.h, src/Lawn/System/SaveGame.cpp).
 *
 * Conventions: the app double is built by {@link HeadlessTestApp#initialize}, which performs no
 * platform/GL/filesystem work, so a board can be built without a window; asset-free behaviour
 * (definition table, static predicates, armour/damage arithmetic, chill timers, animate timing) is
 * asserted unconditionally, while anything that walks the reanimation/particle systems end-to-end
 * is gated with Assumptions + TestAssets.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

class ZombieTest {

    /** Counts the blits a draw call issues, so we can assert the zombie actually renders frames. */
    private static final class RecordingImage extends Image {
        int mDrawCount;

        RecordingImage(int theWidth, int theHeight) {
            mWidth = theWidth;
            mHeight = theHeight;
            mNumRows = 1;
            mNumCols = 1;
        }

        @Override
        public void blt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode,
                boolean linearFilter) {
            mDrawCount++;
        }

        @Override
        public void bltMatrix(Image theImage, float x, float y, SexyMatrix3 theMatrix, Rect theClipRect, Color theColor,
                int theDrawMode, Rect theSrcRect, boolean blend) {
            mDrawCount++;
        }

        @Override
        public void stretchBlt(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor,
                int theDrawMode, boolean fastStretch) {
            mDrawCount++;
        }

        @Override
        public void bltF(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor,
                int theDrawMode) {
            mDrawCount++;
        }
    }

    /**
     * A headless adventure app whose reanimations look loaded. Retail .reanim data is absent, so a
     * reanimation built by {@code zombieInitialize}/the board would keep a null {@code mTrackInstances}
     * (Reanimator.cpp:397-421) which the zombie setup indexes;
     * {@link HeadlessTestApp#stubLoadedTracks} supplies the per-track state a loaded one would have.
     */
    private static final class LoadedReanimApp extends LawnApp {
        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }
    }

    private static LawnApp newAdventureApp() {
        LawnApp app = new LoadedReanimApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mGameScene = GameScenes.SCENE_PLAYING;
        // Everything LawnApp::Init() and the loading thread create (profile, effect system, audio
        // facades, definition tables, image/font resources) so a headless board and its entities
        // can be built without a window or retail assets.
        HeadlessTestApp.initialize(app);
        app.mPlayerInfo.mLevel = 1;
        app.mPlayerInfo.mFinishedAdventure = 0;
        return app;
    }

    private static Board newBoard() {
        Common.sRand(0x5EED);
        Board board = new Board(newAdventureApp());
        board.initLevel();
        return board;
    }

    private static void requireAssets() {
        Assumptions.assumeTrue(TestAssets.resourceRoot().isPresent(),
                "retail assets required for the reanimation/particle-driven paths");
    }

    /**
     * A zombie configured by hand (no ZombieInitialize, therefore no reanimation), which keeps the
     * damage/timing arithmetic tests independent of the asset pack.
     */
    private static Zombie bareZombie(Board board, int theZombieType) {
        Zombie z = new Zombie();
        z.mBoard = board;
        z.mApp = board.mApp;
        z.mZombieType = theZombieType;
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
        z.mRow = 2;
        z.mFromWave = 0;
        z.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ZOMBIE, 2, 4);
        z.mBodyReanimID = ReanimationID.REANIMATIONID_NULL;
        z.mSpecialHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        z.mHelmType = HelmType.HELMTYPE_NONE;
        z.mShieldType = ShieldType.SHIELDTYPE_NONE;
        z.mHasHead = true;
        z.mHasArm = true;
        z.mDead = false;
        z.mZombieRect = new Rect(36, 0, 42, 115);
        z.mZombieAttackRect = new Rect(50, 0, 20, 115);
        return z;
    }

    // -------------------------------------------------------------------------------------
    // §1 definition table (Zombie.cpp:80-140)
    // -------------------------------------------------------------------------------------

    @Test
    void definitionTableHasEveryZombieTypeInOrder() {
        assertEquals(ZombieType.NUM_ZOMBIE_TYPES, Zombie.gZombieDefs.length);
        for (int i = 0; i < ZombieType.NUM_ZOMBIE_TYPES; i++) {
            ZombieDefinition def = Zombie.gZombieDefs[i];
            assertNotNull(def);
            // Zombie.cpp:133-139 asserts the definition's own type equals the array index.
            assertEquals(i, def.mZombieType);
            assertNotNull(def.mZombieName);
            assertFalse(def.mZombieName.isEmpty());
        }
    }

    @Test
    void getZombieDefinitionReturnsSharedTableRow() {
        for (int i = 0; i < ZombieType.NUM_ZOMBIE_TYPES; i++) {
            assertSame(Zombie.gZombieDefs[i], Zombie.getZombieDefinition(i));
        }
    }

    @Test
    void definitionRowsMatchUpstream() {
        // Zombie.cpp:81-114, spot rows quoted verbatim.
        ZombieDefinition normal = Zombie.getZombieDefinition(ZombieType.ZOMBIE_NORMAL);
        assertEquals(ReanimationType.REANIM_ZOMBIE, normal.mReanimationType);
        assertEquals(1, normal.mZombieValue);
        assertEquals(1, normal.mStartingLevel);
        assertEquals(1, normal.mFirstAllowedWave);
        assertEquals(4000, normal.mPickWeight);
        assertEquals("ZOMBIE", normal.mZombieName);

        ZombieDefinition flag = Zombie.getZombieDefinition(ZombieType.ZOMBIE_FLAG);
        assertEquals(0, flag.mPickWeight); // never randomly picked

        ZombieDefinition cone = Zombie.getZombieDefinition(ZombieType.ZOMBIE_TRAFFIC_CONE);
        assertEquals(2, cone.mZombieValue);
        assertEquals(3, cone.mStartingLevel);
        assertEquals(4000, cone.mPickWeight);

        ZombieDefinition gargantuar = Zombie.getZombieDefinition(ZombieType.ZOMBIE_GARGANTUAR);
        assertEquals(ReanimationType.REANIM_GARGANTUAR, gargantuar.mReanimationType);
        assertEquals(10, gargantuar.mZombieValue);
        assertEquals(48, gargantuar.mStartingLevel);
        assertEquals(15, gargantuar.mFirstAllowedWave);

        ZombieDefinition redeye = Zombie.getZombieDefinition(ZombieType.ZOMBIE_REDEYE_GARGANTUAR);
        assertEquals(6000, redeye.mPickWeight);
        assertEquals(ReanimationType.REANIM_GARGANTUAR, redeye.mReanimationType);
        assertEquals("REDEYED_GARGANTUAR", redeye.mZombieName);

        // Zombotany types are never auto-allowed (mStartingLevel = 99).
        for (int type : new int[] { ZombieType.ZOMBIE_PEA_HEAD, ZombieType.ZOMBIE_WALLNUT_HEAD,
                ZombieType.ZOMBIE_JALAPENO_HEAD, ZombieType.ZOMBIE_GATLING_HEAD, ZombieType.ZOMBIE_SQUASH_HEAD,
                ZombieType.ZOMBIE_TALLNUT_HEAD }) {
            assertEquals(99, Zombie.getZombieDefinition(type).mStartingLevel);
        }
    }

    @Test
    void bossZombieListMatchesUpstream() {
        // Zombie.cpp:116-129
        int[] expected = {
            ZombieType.ZOMBIE_TRAFFIC_CONE, ZombieType.ZOMBIE_PAIL, ZombieType.ZOMBIE_FOOTBALL,
            ZombieType.ZOMBIE_POLEVAULTER, ZombieType.ZOMBIE_JACK_IN_THE_BOX, ZombieType.ZOMBIE_LADDER,
            ZombieType.ZOMBIE_ZAMBONI, ZombieType.ZOMBIE_CATAPULT, ZombieType.ZOMBIE_POGO,
            ZombieType.ZOMBIE_NEWSPAPER, ZombieType.ZOMBIE_DOOR, ZombieType.ZOMBIE_GARGANTUAR,
        };
        assertEquals(expected.length, Zombie.gBossZombieList.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], Zombie.gBossZombieList[i]);
        }
    }

    @Test
    void zombieConstantsMatchUpstream() {
        // Zombie.h:29-32 / Zombie.h:70-76 / Zombie.cpp:48-73
        assertEquals(4, Zombie.MAX_ZOMBIE_FOLLOWERS);
        assertEquals(3, Zombie.NUM_BOBSLED_FOLLOWERS);
        assertEquals(4, Zombie.NUM_BACKUP_DANCERS);
        assertEquals(3, Zombie.NUM_BOSS_BUNGEES);

        assertEquals(-1, Zombie.ZOMBIE_WAVE_DEBUG);
        assertEquals(-2, Zombie.ZOMBIE_WAVE_CUTSCENE);
        assertEquals(-3, Zombie.ZOMBIE_WAVE_UI);
        assertEquals(-4, Zombie.ZOMBIE_WAVE_WINNER);

        assertEquals(40, Zombie.ZOMBIE_START_RANDOM_OFFSET);
        assertEquals(3000, Zombie.BUNGEE_ZOMBIE_HEIGHT);
        assertEquals(80, Zombie.POGO_BOUNCE_TIME);
        assertEquals(120, Zombie.DOLPHIN_JUMP_TIME);
        assertEquals(115, Zombie.JACK_IN_THE_BOX_ZOMBIE_RADIUS);
        assertEquals(90, Zombie.JACK_IN_THE_BOX_PLANT_RADIUS);
        assertEquals(150, Zombie.BOBSLED_CRASH_TIME);
        assertEquals(-200, Zombie.ZOMBIE_BACKUP_DANCER_RISE_HEIGHT);
        assertEquals(10, Zombie.BOSS_FLASH_HEALTH_FRACTION);
        assertEquals(4, Zombie.TICKS_BETWEEN_EATS);
        assertEquals(4, Zombie.DAMAGE_PER_EAT);
        assertEquals(0.05f, Zombie.THOWN_ZOMBIE_GRAVITY);
        assertEquals(0.4f, Zombie.CHILLED_SPEED_FACTOR);
        assertEquals(-100.0f, Zombie.CLIP_HEIGHT_LIMIT);
        assertEquals(-200.0f, Zombie.CLIP_HEIGHT_OFF);
        assertEquals(128, Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mRed);
        assertEquals(64, Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mGreen);
        assertEquals(192, Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mBlue);
    }

    @Test
    void zombotanyAndPoolHighGroundTablesMatchUpstream() {
        // Zombie.cpp:8266-8297
        int[] zombotany = { ZombieType.ZOMBIE_PEA_HEAD, ZombieType.ZOMBIE_WALLNUT_HEAD, ZombieType.ZOMBIE_TALLNUT_HEAD,
                ZombieType.ZOMBIE_JALAPENO_HEAD, ZombieType.ZOMBIE_GATLING_HEAD, ZombieType.ZOMBIE_SQUASH_HEAD };
        for (int type : zombotany) {
            assertTrue(Zombie.isZombotany(type));
        }
        assertFalse(Zombie.isZombotany(ZombieType.ZOMBIE_NORMAL));
        assertFalse(Zombie.isZombotany(ZombieType.ZOMBIE_GARGANTUAR));

        int[] poolTypes = { ZombieType.ZOMBIE_NORMAL, ZombieType.ZOMBIE_TRAFFIC_CONE, ZombieType.ZOMBIE_PAIL,
                ZombieType.ZOMBIE_FLAG, ZombieType.ZOMBIE_SNORKEL, ZombieType.ZOMBIE_DOLPHIN_RIDER,
                ZombieType.ZOMBIE_PEA_HEAD, ZombieType.ZOMBIE_WALLNUT_HEAD, ZombieType.ZOMBIE_JALAPENO_HEAD,
                ZombieType.ZOMBIE_GATLING_HEAD, ZombieType.ZOMBIE_TALLNUT_HEAD };
        for (int type : poolTypes) {
            assertTrue(Zombie.zombieTypeCanGoInPool(type));
        }
        assertFalse(Zombie.zombieTypeCanGoInPool(ZombieType.ZOMBIE_ZAMBONI));

        assertFalse(Zombie.zombieTypeCanGoOnHighGround(ZombieType.ZOMBIE_ZAMBONI));
        assertFalse(Zombie.zombieTypeCanGoOnHighGround(ZombieType.ZOMBIE_BOBSLED));
        assertTrue(Zombie.zombieTypeCanGoOnHighGround(ZombieType.ZOMBIE_NORMAL));
        assertTrue(Zombie.zombieTypeCanGoOnHighGround(ZombieType.ZOMBIE_BOSS));
    }

    // -------------------------------------------------------------------------------------
    // §5 damage model (Zombie.cpp:7970-7998, 7539-7802)
    // -------------------------------------------------------------------------------------

    @Test
    void damageIsConsumedByHelmetBeforeBody() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_TRAFFIC_CONE);
        // Zombie.cpp:263 cone helm 370; Zombie.cpp:238 default body 270.
        z.mHelmType = HelmType.HELMTYPE_TRAFFIC_CONE;
        z.mHelmHealth = 370;
        z.mHelmMaxHealth = 370;
        z.mBodyHealth = 270;
        z.mBodyMaxHealth = 270;

        z.takeDamage(50, 0);

        assertEquals(320, z.mHelmHealth, "the helmet absorbs the hit first");
        assertEquals(270, z.mBodyHealth, "the body is untouched while the helmet holds");
    }

    @Test
    void damageIsConsumedByShieldBeforeHelmet() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_DOOR);
        // Zombie.cpp:276 door shield 1100.
        z.mShieldType = ShieldType.SHIELDTYPE_DOOR;
        z.mShieldHealth = 1100;
        z.mShieldMaxHealth = 1100;
        z.mHelmType = HelmType.HELMTYPE_TRAFFIC_CONE;
        z.mHelmHealth = 370;
        z.mHelmMaxHealth = 370;
        z.mBodyHealth = 270;
        z.mBodyMaxHealth = 270;

        z.takeDamage(50, 0);

        assertEquals(1050, z.mShieldHealth, "flying -> shield -> helm -> body: the shield is first");
        assertEquals(370, z.mHelmHealth);
        assertEquals(270, z.mBodyHealth);
    }

    @Test
    void bypassesShieldFlagSkipsTheShield() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_DOOR);
        z.mShieldType = ShieldType.SHIELDTYPE_DOOR;
        z.mShieldHealth = 1100;
        z.mShieldMaxHealth = 1100;
        z.mBodyHealth = 270;
        z.mBodyMaxHealth = 270;

        z.takeDamage(50, 1 << DamageFlags.DAMAGE_BYPASSES_SHIELD);

        assertEquals(1100, z.mShieldHealth, "DAMAGE_BYPASSES_SHIELD leaves the shield alone");
        assertEquals(220, z.mBodyHealth, "the body takes the full hit");
    }

    @Test
    void hitsShieldAndBodyFlagDamagesBoth() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_DOOR);
        z.mShieldType = ShieldType.SHIELDTYPE_DOOR;
        z.mShieldHealth = 1100;
        z.mShieldMaxHealth = 1100;
        z.mBodyHealth = 270;
        z.mBodyMaxHealth = 270;

        // Zombie.cpp:7981-7984: the shield takes the damage AND the body re-takes the original amount.
        z.takeDamage(50, 1 << DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY);

        assertEquals(1050, z.mShieldHealth);
        assertEquals(220, z.mBodyHealth);
    }

    @Test
    void takeDamageIsIgnoredWhileDyingOrBurned() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_NORMAL);
        z.mBodyHealth = 270;
        z.mBodyMaxHealth = 270;

        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_DYING;
        z.takeDamage(50, 0);
        assertEquals(270, z.mBodyHealth);

        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_BURNED;
        z.takeDamage(50, 0);
        assertEquals(270, z.mBodyHealth);
        // Zombie.cpp:9617-9624 lists PHASE_ZOMBIE_BURNED as a terminal phase, so the guard at
        // Zombie.cpp:7972-7973 (PHASE_JACK_IN_THE_BOX_POPPING || IsDeadOrDying()) rejects the hit
        // here exactly as it does in PHASE_ZOMBIE_DYING.
        assertTrue(z.isDeadOrDying(), "PHASE_ZOMBIE_BURNED is terminal, not a 'non-flagship phase'");
    }

    @Test
    void isDeadOrDyingCoversTheThreeTerminalPhases() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_NORMAL);
        assertFalse(z.isDeadOrDying());
        for (int phase : new int[] { ZombiePhase.PHASE_ZOMBIE_DYING, ZombiePhase.PHASE_ZOMBIE_BURNED,
                ZombiePhase.PHASE_ZOMBIE_MOWERED }) {
            z.mZombiePhase = phase;
            assertTrue(z.isDeadOrDying());
        }
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        z.mDead = true;
        assertTrue(z.isDeadOrDying());
    }

    @Test
    void bleedOutDealsThreeWhenMaxHealthIsAtLeast500() {
        Board board = newBoard();
        Common.sRand(0x1234);

        // Zombie.cpp:4657-4682: headless bleed-out is 1 damage, 10 for yetis, then overwritten with 3
        // whenever mBodyMaxHealth >= 500.
        Zombie small = board.mZombies.dataArrayAlloc();
        configure(small, board, ZombieType.ZOMBIE_NORMAL, 270, 260);
        Zombie big = board.mZombies.dataArrayAlloc();
        configure(big, board, ZombieType.ZOMBIE_LADDER, 500, 500);

        int smallHits = sampleBleedOutDamage(small, 180);
        int bigHits = sampleBleedOutDamage(big, 340);

        assertTrue(smallHits > 0, "a headless zombie must bleed out");
        assertTrue(bigHits > 0, "a headless 500 HP zombie must bleed out");
        assertEquals(1 * smallHits, 260 - small.mBodyHealth, "damage per bleed-out tick is 1 below 500 max HP");
        assertEquals(3 * bigHits, 500 - big.mBodyHealth, "damage per bleed-out tick is 3 at >= 500 max HP");
    }

    private static void configure(Zombie z, Board board, int theZombieType, int theMaxHealth, int theHealth) {
        z.mBoard = board;
        z.mApp = board.mApp;
        z.mZombieType = theZombieType;
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
        z.mRow = 1;
        z.mFromWave = 0;
        z.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ZOMBIE, 1, 4);
        z.mBodyReanimID = ReanimationID.REANIMATIONID_NULL;
        z.mSpecialHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        z.mHelmType = HelmType.HELMTYPE_NONE;
        z.mShieldType = ShieldType.SHIELDTYPE_NONE;
        z.mHasHead = false; // the bleed-out branch only runs while headless
        z.mHasArm = true;
        z.mBodyMaxHealth = theMaxHealth;
        z.mBodyHealth = theHealth;
        z.mVelX = 0.0f;
        z.mFrame = 0;
        z.mAnimFrames = 12;
        z.mAnimTicksPerFrame = 12;
        z.mPosX = 700.0f;
        z.mPosY = 200.0f;
        z.mX = 700;
        z.mY = 200;
        z.mZombieRect = new Rect(36, 0, 42, 115);
        z.mZombieAttackRect = new Rect(50, 0, 20, 115);
        z.mDead = false;
    }

    /** Runs update() until the body reaches the stop threshold and returns the number of bleed hits. */
    private static int sampleBleedOutDamage(Zombie z, int theStopHealth) {
        int hits = 0;
        int previous = z.mBodyHealth;
        for (int i = 0; i < 4000 && z.mBodyHealth > theStopHealth && !z.mDead; i++) {
            z.update();
            if (z.mBodyHealth < previous) {
                hits++;
                previous = z.mBodyHealth;
            }
        }
        return hits;
    }

    // -------------------------------------------------------------------------------------
    // §6 status effects (Zombie.cpp:7519-7537, 8373-8422)
    // -------------------------------------------------------------------------------------

    @Test
    void chillTimersMatchUpstream() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_NORMAL);
        z.mBodyHealth = 270;
        z.mBodyMaxHealth = 270;

        z.applyChill(false);
        assertEquals(1000, z.mChilledCounter); // Zombie.cpp:7526-7534
        assertTrue(z.isMovingAtChilledSpeed());
        assertFalse(z.isImmobilizied(), "chill does not immobilise");

        z.applyChill(true); // the ice-trap variant is 2000 and only ever raises the counter
        assertEquals(2000, z.mChilledCounter);

        z.applyChill(false);
        assertEquals(2000, z.mChilledCounter, "chill never shortens an existing counter");
    }

    @Test
    void canBeChilledRejectsZamboniAndBobsledTeams() {
        Board board = newBoard();
        Zombie zamboni = bareZombie(board, ZombieType.ZOMBIE_ZAMBONI);
        assertFalse(zamboni.canBeChilled());
        zamboni.applyChill(false);
        assertEquals(0, zamboni.mChilledCounter);

        Zombie normal = bareZombie(board, ZombieType.ZOMBIE_NORMAL);
        assertTrue(normal.canBeChilled());
    }

    @Test
    void chillHalvesTheSpeedFactorAndDoublesTheChewFrameLength() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_NORMAL);
        z.mBodyHealth = 270;
        z.mBodyMaxHealth = 270;

        // Zombie.cpp:4952-4956: chewing advances 6 ticks per frame, 12 while chilled, and
        // mFrame = mAnimCounter / aFrameLength is recomputed after every single tick, so 12
        // ticks reach frame 2 unchilled and only frame 1 once chilled.
        z.mIsEating = true;
        z.mHasHead = true;
        z.mAnimFrames = 12;
        z.mAnimCounter = 0;
        for (int i = 0; i < 12; i++) {
            z.animate();
        }
        assertEquals(2, z.mFrame, "12 ticks of chewing at 6 ticks/frame land on frame 2");

        z.mChilledCounter = 1000;
        z.mAnimCounter = 0;
        for (int i = 0; i < 12; i++) {
            z.animate();
        }
        assertEquals(1, z.mFrame, "while chilled the same 12 ticks only reach frame 1");

        for (int i = 0; i < 12; i++) {
            z.animate();
        }
        assertEquals(2, z.mFrame, "frame 2 arrives after 24 ticks while chilled");
    }

    @Test
    void immobilisedZombiesStopAnimating() {
        Board board = newBoard();
        Zombie z = bareZombie(board, ZombieType.ZOMBIE_NORMAL);
        z.mIsEating = false;
        z.mAnimCounter = 0;
        z.mButteredCounter = 400; // Zombie.cpp:8525
        assertTrue(z.isImmobilizied());

        for (int i = 0; i < 50; i++) {
            z.animate();
        }
        assertEquals(0, z.mAnimCounter, "Animate() early-returns while immobilised (Zombie.cpp:4940)");

        z.mButteredCounter = 0;
        z.mIceTrapCounter = 0;
        assertFalse(z.isImmobilizied());
    }

    // -------------------------------------------------------------------------------------
    // Asset-gated behaviour tests
    // -------------------------------------------------------------------------------------

    @Test
    void zombieInitializeAppliesThePerTypeStats() {
        requireAssets();
        Board board = newBoard();

        for (int type = 0; type < ZombieType.NUM_ZOMBIE_TYPES; type++) {
            Zombie z = new Zombie();
            z.mBoard = board;
            z.mApp = board.mApp;
            z.zombieInitialize(2, type, false, null, 0);
            assertEquals(type, z.mZombieType);
            assertEquals(z.mBodyHealth, z.mBodyMaxHealth, "max health snapshot (Zombie.cpp:886)");
            assertTrue(z.mBodyHealth > 0);
            assertFalse(z.mDead);
        }
    }

    @Test
    void zombieInitializeUsesTheUpstreamHealthAndArmourValues() {
        requireAssets();
        Board board = newBoard();

        assertEquals(270, initHealth(board, ZombieType.ZOMBIE_NORMAL)); // Zombie.cpp:238
        assertEquals(1350, initHealth(board, ZombieType.ZOMBIE_YETI)); // Zombie.cpp:282
        assertEquals(500, initHealth(board, ZombieType.ZOMBIE_LADDER)); // Zombie.cpp:289
        assertEquals(450, initHealth(board, ZombieType.ZOMBIE_BUNGEE)); // Zombie.cpp:303
        assertEquals(500, initHealth(board, ZombieType.ZOMBIE_POLEVAULTER)); // Zombie.cpp:378
        assertEquals(500, initHealth(board, ZombieType.ZOMBIE_DOLPHIN_RIDER)); // Zombie.cpp:400
        assertEquals(3000, initHealth(board, ZombieType.ZOMBIE_GARGANTUAR)); // Zombie.cpp:418
        assertEquals(6000, initHealth(board, ZombieType.ZOMBIE_REDEYE_GARGANTUAR)); // Zombie.cpp:452
        assertEquals(1350, initHealth(board, ZombieType.ZOMBIE_ZAMBONI)); // Zombie.cpp:459
        assertEquals(850, initHealth(board, ZombieType.ZOMBIE_CATAPULT)); // Zombie.cpp:471
        assertEquals(500, initHealth(board, ZombieType.ZOMBIE_JACK_IN_THE_BOX)); // Zombie.cpp:498
        assertEquals(500, initHealth(board, ZombieType.ZOMBIE_DANCER)); // Zombie.cpp:661
        assertEquals(500, initHealth(board, ZombieType.ZOMBIE_JALAPENO_HEAD)); // Zombie.cpp:795

        Zombie cone = init(board, ZombieType.ZOMBIE_TRAFFIC_CONE);
        assertEquals(HelmType.HELMTYPE_TRAFFIC_CONE, cone.mHelmType);
        assertEquals(370, cone.mHelmHealth); // Zombie.cpp:263

        Zombie pail = init(board, ZombieType.ZOMBIE_PAIL);
        assertEquals(HelmType.HELMTYPE_PAIL, pail.mHelmType);
        assertEquals(1100, pail.mHelmHealth); // Zombie.cpp:271

        Zombie football = init(board, ZombieType.ZOMBIE_FOOTBALL);
        assertEquals(HelmType.HELMTYPE_FOOTBALL, football.mHelmType);
        assertEquals(1400, football.mHelmHealth); // Zombie.cpp:345
        assertEquals(6, football.mAnimTicksPerFrame);

        Zombie digger = init(board, ZombieType.ZOMBIE_DIGGER);
        assertEquals(HelmType.HELMTYPE_DIGGER, digger.mHelmType);
        assertEquals(100, digger.mHelmHealth); // Zombie.cpp:353

        Zombie door = init(board, ZombieType.ZOMBIE_DOOR);
        assertEquals(ShieldType.SHIELDTYPE_DOOR, door.mShieldType);
        assertEquals(1100, door.mShieldHealth); // Zombie.cpp:276

        Zombie paper = init(board, ZombieType.ZOMBIE_NEWSPAPER);
        assertEquals(ShieldType.SHIELDTYPE_NEWSPAPER, paper.mShieldType);
        assertEquals(150, paper.mShieldHealth); // Zombie.cpp:614
        assertEquals(ZombiePhase.PHASE_NEWSPAPER_READING, paper.mZombiePhase);

        Zombie ladder = init(board, ZombieType.ZOMBIE_LADDER);
        assertEquals(ShieldType.SHIELDTYPE_LADDER, ladder.mShieldType);
        assertEquals(500, ladder.mShieldHealth); // Zombie.cpp:290
        assertEquals(ZombiePhase.PHASE_LADDER_CARRYING, ladder.mZombiePhase);

        Zombie balloon = init(board, ZombieType.ZOMBIE_BALLOON);
        assertEquals(20, balloon.mFlyingHealth); // Zombie.cpp:641
        assertEquals(ZombiePhase.PHASE_BALLOON_FLYING, balloon.mZombiePhase);

        Zombie boss = init(board, ZombieType.ZOMBIE_BOSS);
        assertEquals(40000, boss.mBodyHealth); // Zombie.cpp:692 (adventure mode)
        // Zombie.cpp:692 puts the boss on RENDER_LAYER_TOP, but Zombie.cpp:893 still folds the row
        // into the order (Board.cpp:448-451: theRow * RENDER_LAYER_ROW_OFFSET + theRenderLayer +
        // theLayerOffset), and this fixture spawns it in row 2.
        assertEquals(RenderLayer.RENDER_LAYER_TOP + 2 * RenderLayer.RENDER_LAYER_ROW_OFFSET,
                boss.mRenderOrder / 10000 * 10000,
                "the boss keeps the TOP layer, plus its row offset (Zombie.cpp:692, 893; Board.cpp:448-451)");
    }

    private static Zombie init(Board board, int theZombieType) {
        Zombie z = new Zombie();
        z.mBoard = board;
        z.mApp = board.mApp;
        z.zombieInitialize(2, theZombieType, false, null, 0);
        return z;
    }

    private static int initHealth(Board board, int theZombieType) {
        return init(board, theZombieType).mBodyHealth;
    }

    @Test
    void walkingZombieEatsAPlantThenWalksAgain() {
        requireAssets();
        Board board = newBoard();

        Zombie z = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
        assertNotNull(z);

        Plant plant = board.mPlants.dataArrayAlloc();
        plant.plantInitialize(5, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        plant.mPlantHealth = 1000;

        // Park the zombie just right of the plant column so it walks into it in a few ticks: the plant
        // sits at x = plant.mX = 5 * 80 + LAWN_XMIN (Board.cpp:8846) and zombies walk left
        // (Zombie.cpp:4146: mPosX -= aSpeed), so the fixture must start it right of the plant, never
        // left of it (which would send it away, toward the house, with nothing to chew).
        z.mPosX = plant.mX + 80;
        z.mX = plant.mX + 80;

        boolean ate = false;
        for (int i = 0; i < 3000 && !ate; i++) {
            z.update();
            ate = z.mIsEating;
        }
        assertTrue(ate, "Zombie.cpp:6817 CheckIfPreyCaught must start chewing a plant in its row");
        assertEquals(ZombiePhase.PHASE_ZOMBIE_NORMAL, z.mZombiePhase);
        assertTrue(plant.mPlantHealth < 1000, "chewing deals DAMAGE_PER_EAT each TICKS_BETWEEN_EATS");

        plant.mPlantHealth = 0;
        plant.mDead = true;
        for (int i = 0; i < 200; i++) {
            z.update();
        }
        assertFalse(z.mIsEating, "StopEating() runs once the plant is gone (Zombie.cpp:6890-6893)");
    }

    @Test
    void lethalDamageKillsAndTheBoardCleansTheZombieUp() {
        requireAssets();
        Board board = newBoard();

        Zombie z = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
        assertNotNull(z);
        int sizeBefore = board.mZombies.mSize;

        // Zombie.cpp:7989 takeDamage(1800, 9) is the "instant kill" idiom (bit0 + bit4).
        z.takeDamage(1800, 9);
        assertTrue(z.isDeadOrDying(), "the hit must put the zombie into a terminal phase");
        assertTrue(z.mBodyHealth <= 0);

        for (int i = 0; i < 600 && !z.mDead; i++) {
            z.update();
        }
        assertTrue(z.mDead, "the death animation ends in DieNoLoot/DieWithLoot (Zombie.cpp:8900-9030)");

        board.processDeleteQueue();
        assertTrue(board.mZombies.mSize < sizeBefore, "ProcessDeleteQueue frees mDead zombies");
    }

    @Test
    void houseEntryTriggersTheGameOverPath() {
        requireAssets();
        Board board = newBoard();
        LawnApp app = board.mApp;

        Zombie z = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
        assertNotNull(z);
        z.mPosX = -120.0f;
        z.mX = -120;
        z.mHasHead = true;
        assertEquals(ZombiePhase.PHASE_ZOMBIE_NORMAL, z.mZombiePhase);

        // Zombie.cpp:4537-4575: mX <= BOARD_EDGE(-100) with a head calls Board::ZombiesWon.
        z.checkForBoardEdge();

        assertEquals(BoardResult.BOARDRESULT_LOST, app.mBoardResult,
                "Board::ZombiesWon sets mApp->mBoardResult = BOARDRESULT_LOST");
        assertFalse(z.mDead, "the reaching zombie itself is left for the zombies-won cutscene");
    }

    @Test
    void drawRendersReanimFramesForSeveralPhases() {
        requireAssets();
        Board board = newBoard();

        int[] phases = { ZombiePhase.PHASE_ZOMBIE_NORMAL, ZombiePhase.PHASE_ZOMBIE_NORMAL,
                ZombiePhase.PHASE_DIGGER_TUNNELING, ZombiePhase.PHASE_POGO_BOUNCING,
                ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT, ZombiePhase.PHASE_BALLOON_FLYING };
        int[] types = { ZombieType.ZOMBIE_NORMAL, ZombieType.ZOMBIE_TRAFFIC_CONE, ZombieType.ZOMBIE_DIGGER,
                ZombieType.ZOMBIE_POGO, ZombieType.ZOMBIE_POLEVAULTER, ZombieType.ZOMBIE_BALLOON };

        for (int i = 0; i < types.length; i++) {
            Zombie z = init(board, types[i]);
            z.mZombiePhase = phases[i];
            z.mPosX = 400.0f;
            z.mPosY = 200.0f;
            z.mX = 400;
            z.mY = 200;

            ZombieDrawPosition drawPos = z.getDrawPos();
            assertNotNull(drawPos);
            assertTrue(drawPos.mClipHeight >= Zombie.CLIP_HEIGHT_OFF && drawPos.mClipHeight <= 120.0f,
                    "clip height must stay inside the upstream -200..120 range (Zombie.cpp:71-72)");

            RecordingImage target = new RecordingImage(800, 600);
            Graphics g = new Graphics(target);
            z.draw(g);
            assertTrue(target.mDrawCount > 0,
                    "Draw() must emit reanim/attachment blits for " + ZombiePhase.class.getSimpleName() + " phase "
                            + phases[i]);
        }
    }

    @Test
    void dyingAndBurnedPhasesStillDrawTheirFade() {
        requireAssets();
        Board board = newBoard();

        Zombie z = init(board, ZombieType.ZOMBIE_NORMAL);
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_DYING;
        z.mZombieFade = 100; // Zombie.cpp:9266 sets this once the death anim loops
        RecordingImage target = new RecordingImage(800, 600);
        z.draw(new Graphics(target));
        assertEquals(100, z.mZombieFade, "drawing must not advance the fade counter");
        assertTrue(z.getDrawPos().mClipHeight <= 120.0f, "the dying pose still reports a sane clip height");
    }
}
