package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachmentIDRef;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless tests for the ported {@link Projectile} (src/Lawn/Projectile.cpp).
 *
 * <p>Every {@link ProjectileType} in {@code gProjectileDefinition} is exercised at least once.
 * Motion maths is asserted against hand-computed float values; collision uses an in-memory
 * {@link StubZombie}; sound selection uses a recording {@link LawnApp} double. No game assets or
 * GL/window are required.
 */
class ProjectileTest {

    /** Records sound calls instead of touching a real SoundManager. */
    static class RecordingLawnApp extends LawnApp {
        final List<Integer> foleys = new ArrayList<>();
        final List<Long> samples = new ArrayList<>();

        @Override
        public void playFoley(int theFoleyType) {
            foleys.add(theFoleyType);
        }

        @Override
        public void playSample(long theSoundNum) {
            samples.add(theSoundNum);
        }
    }

    /** Minimal zombie with an explicit hit rect, so no reanim/draw state is needed. */
    static class StubZombie extends Zombie {
        Rect rect = new Rect(0, 0, 80, 100);
        boolean effected = true;
        boolean fireResistant = false;
        final List<int[]> damageTaken = new ArrayList<>();

        @Override
        public Rect getZombieRect() {
            return new Rect(rect.mX, rect.mY, rect.mWidth, rect.mHeight);
        }

        @Override
        public boolean effectedByDamage(int theDamageRangeFlags) {
            return effected;
        }

        @Override
        public boolean isFireResistant() {
            return fireResistant;
        }

        @Override
        public float zombieTargetLeadX(float theTime) {
            return rect.mX + rect.mWidth / 2.0f;
        }

        @Override
        public void takeDamage(int theDamage, int theDamageFlags) {
            damageTaken.add(new int[] {theDamage, theDamageFlags});
        }

        @Override
        public void removeColdEffects() {
        }

        @Override
        public void applyButter() {
        }

        @Override
        public cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem addAttachedParticle(int thePosX, int thePosY,
                int theEffect) {
            return null;
        }
    }

    private RecordingLawnApp mApp;
    private Board mBoard;

    @BeforeEach
    void setUp() {
        mApp = new RecordingLawnApp();
        HeadlessTestApp.initialize(mApp);
        mBoard = new Board(mApp);
    }

    private Projectile newProjectile(int theType) {
        Projectile aProjectile = new Projectile();
        aProjectile.mApp = mApp;
        aProjectile.mBoard = mBoard;
        aProjectile.mProjectileType = theType;
        aProjectile.mMotionType = ProjectileMotion.MOTION_STRAIGHT;
        aProjectile.mWidth = 40;
        aProjectile.mHeight = 40;
        aProjectile.mRow = 0;
        aProjectile.mPosX = 100.0f;
        aProjectile.mPosY = 200.0f;
        aProjectile.mShadowY = 400.0f;
        aProjectile.mX = 100;
        aProjectile.mY = 200;
        aProjectile.mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
        return aProjectile;
    }

    // ------------------------------------------------------------------ table

    @Test
    void definitionTableCoversEveryProjectileType() {
        assertEquals(ProjectileType.NUM_PROJECTILES, Projectile.gProjectileDefinition.length);
        for (int i = 0; i < ProjectileType.NUM_PROJECTILES; i++) {
            assertEquals(i, Projectile.gProjectileDefinition[i].mProjectileType);
            assertEquals(0, Projectile.gProjectileDefinition[i].mImageRow, "all cels use image row 0");
        }
    }

    @Test
    void definitionDamagesMatchUpstream() {
        assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_PEA].mDamage);
        assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_SNOWPEA].mDamage);
        assertEquals(40, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_CABBAGE].mDamage);
        assertEquals(80, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_MELON].mDamage);
        assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_PUFF].mDamage);
        assertEquals(80, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_WINTERMELON].mDamage);
        assertEquals(40, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_FIREBALL].mDamage);
        assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_STAR].mDamage);
        assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_SPIKE].mDamage);
        assertEquals(75, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_BASKETBALL].mDamage);
        assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_KERNEL].mDamage);
        assertEquals(300, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_COBBIG].mDamage);
        assertEquals(40, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_BUTTER].mDamage);
        assertEquals(20, Projectile.gProjectileDefinition[ProjectileType.PROJECTILE_ZOMBIE_PEA].mDamage);
    }

    // ----------------------------------------------------------------- motion

    /** STRAIGHT: every type advances +3.33 px/tick in X and leaves Y untouched. */
    @Test
    void straightMotionAdvancesEveryProjectileType() {
        for (int type = 0; type < ProjectileType.NUM_PROJECTILES; type++) {
            Projectile aProjectile = newProjectile(type);
            float aExpectedX = 100.0f;
            for (int tick = 0; tick < 10; tick++) {
                aProjectile.updateMotion();
                aExpectedX += 3.33f;
            }
            assertEquals(aExpectedX, aProjectile.mPosX, 1.0e-4f, "type " + type + " straight X");
            assertEquals(200.0f, aProjectile.mPosY, 1.0e-4f, "type " + type + " straight Y");
            assertEquals((int) aExpectedX, aProjectile.mX);
        }
    }

    @Test
    void backwardsMotionMovesLeft() {
        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PEA);
        aProjectile.mMotionType = ProjectileMotion.MOTION_BACKWARDS;
        aProjectile.updateMotion();
        assertEquals(100.0f - 3.33f, aProjectile.mPosX, 1.0e-4f);
    }

    @Test
    void beeMotionLiftsWhileYoungThenAdvances() {
        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PEA);
        aProjectile.mMotionType = ProjectileMotion.MOTION_BEE;
        aProjectile.mProjectileAge = 0;
        aProjectile.updateMotion();
        // age 0 < 60 -> mPosY -= 0.5, then mPosX += 3.33
        assertEquals(200.0f - 0.5f, aProjectile.mPosY, 1.0e-4f);
        assertEquals(103.33f, aProjectile.mPosX, 1.0e-4f);

        aProjectile.mProjectileAge = 60;
        aProjectile.updateMotion();
        assertEquals(200.0f - 0.5f, aProjectile.mPosY, 1.0e-4f, "no lift at age 60");
        assertEquals(106.66f, aProjectile.mPosX, 1.0e-4f);
    }

    @Test
    void beeBackwardsMotionMovesLeftAndLifts() {
        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PEA);
        aProjectile.mMotionType = ProjectileMotion.MOTION_BEE_BACKWARDS;
        aProjectile.mProjectileAge = 0;
        aProjectile.updateMotion();
        assertEquals(200.0f - 0.5f, aProjectile.mPosY, 1.0e-4f);
        assertEquals(100.0f - 3.33f, aProjectile.mPosX, 1.0e-4f);
    }

    @Test
    void threepeaterMotionDecaysVelY() {
        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PEA);
        aProjectile.mMotionType = ProjectileMotion.MOTION_THREEPEATER;
        aProjectile.mVelY = 1.0f;
        aProjectile.updateMotion();
        // mPosX += 3.33; mPosY += 1.0; mVelY *= 0.97; mShadowY += mVelY(new)
        assertEquals(103.33f, aProjectile.mPosX, 1.0e-4f);
        assertEquals(201.0f, aProjectile.mPosY, 1.0e-4f);
        assertEquals(0.97f, aProjectile.mVelY, 1.0e-4f);
        assertEquals(400.0f + 0.97f, aProjectile.mShadowY, 1.0e-4f);
    }

    @Test
    void starMotionIntegratesVelocity() {
        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_STAR);
        aProjectile.mMotionType = ProjectileMotion.MOTION_STAR;
        aProjectile.mVelX = 2.0f;
        aProjectile.mVelY = -1.5f;
        aProjectile.mShadowY = 1000.0f;
        aProjectile.updateMotion();
        assertEquals(102.0f, aProjectile.mPosX, 1.0e-4f);
        assertEquals(198.5f, aProjectile.mPosY, 1.0e-4f);
        // MOTION_STAR integrates mVelY into mShadowY from its own value (Projectile.cpp:672-674).
        assertEquals(1000.0f - 1.5f, aProjectile.mShadowY, 1.0e-4f);
    }

    @Test
    void floatOverMotionGlidesRight() {
        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PUFF);
        aProjectile.mMotionType = ProjectileMotion.MOTION_FLOAT_OVER;
        aProjectile.mVelZ = -0.1f;
        aProjectile.updateMotion();
        // mVelZ = min(-0.1 + 0.002, 0) = -0.098; mPosY += mVelZ; mPosX += 0.4
        assertEquals(100.4f, aProjectile.mPosX, 1.0e-4f);
        assertEquals(200.0f - 0.098f, aProjectile.mPosY, 1.0e-4f);
        assertEquals(-0.098f, aProjectile.mVelZ, 1.0e-5f);
    }

    @Test
    void lobbedMotionAppliesGravity() {
        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_CABBAGE);
        aProjectile.mMotionType = ProjectileMotion.MOTION_LOBBED;
        aProjectile.mAccZ = 0.1f;
        aProjectile.mVelX = 2.0f;
        aProjectile.mVelY = 0.0f;
        aProjectile.updateMotion();
        // mVelZ += 0.1; mPosX += 2; mPosY += 0; mPosZ += 0.1
        assertEquals(0.1f, aProjectile.mVelZ, 1.0e-5f);
        assertEquals(102.0f, aProjectile.mPosX, 1.0e-4f);
        assertEquals(0.1f, aProjectile.mPosZ, 1.0e-5f);
    }

    @Test
    void homingMotionSteersTowardTargetZombie() {
        mBoard.mZombies = new DataArray<Zombie>(StubZombie::new, 8, "stub");
        StubZombie aTarget = (StubZombie) mBoard.mZombies.dataArrayAlloc();
        aTarget.mRow = 0;
        aTarget.rect = new Rect(300, 150, 80, 100);

        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PEA);
        aProjectile.mMotionType = ProjectileMotion.MOTION_HOMING;
        aProjectile.mTargetZombieID = mBoard.mZombies.dataArrayGetID(aTarget);
        aProjectile.mProjectileAge = 10;
        aProjectile.mVelX = 1.0f;
        aProjectile.mVelY = 0.0f;
        aProjectile.updateMotion();

        // Steering normalizes to length 2.0, so the projectile moves but stays aimed at the target.
        assertTrue(aProjectile.mVelX > 0.0f);
        assertEquals(2.0f, (float) Math.hypot(aProjectile.mVelX, aProjectile.mVelY), 1.0e-4f);
    }

    // ------------------------------------------------------------- hit rects

    @Test
    void projectileRectMathsPerType() {
        assertRect(ProjectileType.PROJECTILE_PEA, 100, 200, 40, 40, new Rect(85, 200, 55, 40));
        assertRect(ProjectileType.PROJECTILE_SNOWPEA, 100, 200, 40, 40, new Rect(85, 200, 55, 40));
        assertRect(ProjectileType.PROJECTILE_ZOMBIE_PEA, 100, 200, 40, 40, new Rect(85, 200, 55, 40));
        assertRect(ProjectileType.PROJECTILE_COBBIG, 100, 200, 40, 40, new Rect(100 + 20 - 115, 200 + 20 - 115, 230, 230));
        assertRect(ProjectileType.PROJECTILE_MELON, 100, 200, 40, 40, new Rect(120, 200, 60, 40));
        assertRect(ProjectileType.PROJECTILE_WINTERMELON, 100, 200, 40, 40, new Rect(120, 200, 60, 40));
        assertRect(ProjectileType.PROJECTILE_FIREBALL, 100, 200, 40, 40, new Rect(100, 200, 30, 40));
        assertRect(ProjectileType.PROJECTILE_SPIKE, 100, 200, 40, 40, new Rect(75, 200, 65, 40));
        assertRect(ProjectileType.PROJECTILE_STAR, 100, 200, 40, 40, new Rect(100, 200, 40, 40));
    }

    private void assertRect(int theType, int theX, int theY, int theW, int theH, Rect theExpected) {
        Projectile aProjectile = newProjectile(theType);
        aProjectile.mX = theX;
        aProjectile.mY = theY;
        aProjectile.mWidth = theW;
        aProjectile.mHeight = theH;
        Rect aRect = aProjectile.getProjectileRect();
        assertEquals(theExpected.mX, aRect.mX, "type " + theType + " x");
        assertEquals(theExpected.mY, aRect.mY, "type " + theType + " y");
        assertEquals(theExpected.mWidth, aRect.mWidth, "type " + theType + " w");
        assertEquals(theExpected.mHeight, aRect.mHeight, "type " + theType + " h");
    }

    // ----------------------------------------------------------- damage flags

    @Test
    void damageFlagsPerTypeAndMotion() {
        int shield = 1 << DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY;
        int bypass = 1 << DamageFlags.DAMAGE_BYPASSES_SHIELD;
        int freeze = 1 << DamageFlags.DAMAGE_FREEZE;

        assertEquals(shield, newProjectile(ProjectileType.PROJECTILE_MELON).getDamageFlags(null));
        assertEquals(shield, newProjectile(ProjectileType.PROJECTILE_WINTERMELON).getDamageFlags(null)
            & ~freeze);
        assertTrue((newProjectile(ProjectileType.PROJECTILE_WINTERMELON).getDamageFlags(null) & freeze) != 0);
        assertTrue((newProjectile(ProjectileType.PROJECTILE_SNOWPEA).getDamageFlags(null) & freeze) != 0);

        Projectile aLobbed = newProjectile(ProjectileType.PROJECTILE_CABBAGE);
        aLobbed.mMotionType = ProjectileMotion.MOTION_LOBBED;
        assertEquals(bypass, aLobbed.getDamageFlags(null));

        Projectile aBackwards = newProjectile(ProjectileType.PROJECTILE_PEA);
        aBackwards.mMotionType = ProjectileMotion.MOTION_BACKWARDS;
        assertEquals(bypass, aBackwards.getDamageFlags(null));

        Projectile aStar = newProjectile(ProjectileType.PROJECTILE_STAR);
        aStar.mMotionType = ProjectileMotion.MOTION_STAR;
        aStar.mVelX = -1.0f;
        assertEquals(bypass, aStar.getDamageFlags(null));

        aStar.mVelX = 1.0f;
        assertEquals(0, aStar.getDamageFlags(null));

        // A fireball hitting a fire-resistant zombie is not splash damage.
        StubZombie aResistant = new StubZombie();
        aResistant.fireResistant = true;
        Projectile aFireball = newProjectile(ProjectileType.PROJECTILE_FIREBALL);
        assertTrue(aFireball.isSplashDamage(null));
        assertTrue(!aFireball.isSplashDamage(aResistant));
    }

    // -------------------------------------------------------------- collision

    @Test
    void findsStubZombieOnlyWhenRectsOverlapAndRowMatches() {
        mBoard.mZombies = new DataArray<Zombie>(StubZombie::new, 8, "stub");
        StubZombie aZombie = (StubZombie) mBoard.mZombies.dataArrayAlloc();
        aZombie.mRow = 0;
        aZombie.rect = new Rect(100, 200, 80, 100);

        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PEA);
        // PEA hit rect = (mX-15, mY, 55, 40) = (85,200,55,40); overlap with (100,200,80,100) = 40.
        assertSame(aZombie, aProjectile.findCollisionTarget());

        // Far away in the same row -> no overlap.
        aZombie.rect = new Rect(500, 200, 80, 100);
        assertNull(aProjectile.findCollisionTarget());

        // Overlapping but wrong row -> skipped (non-boss).
        aZombie.rect = new Rect(100, 200, 80, 100);
        aZombie.mRow = 2;
        assertNull(aProjectile.findCollisionTarget());

        // Dead zombies are ignored.
        aZombie.mRow = 0;
        aZombie.mDead = true;
        assertNull(aProjectile.findCollisionTarget());

        // Immune to the projectile's damage-range flags.
        aZombie.mDead = false;
        aZombie.effected = false;
        assertNull(aProjectile.findCollisionTarget());
    }

    @Test
    void bossZombieIgnoresRowForCollision() {
        mBoard.mZombies = new DataArray<Zombie>(StubZombie::new, 8, "stub");
        StubZombie aBoss = (StubZombie) mBoard.mZombies.dataArrayAlloc();
        aBoss.mZombieType = ZombieType.ZOMBIE_BOSS;
        aBoss.mRow = 3;
        aBoss.rect = new Rect(100, 200, 90, 120);

        Projectile aProjectile = newProjectile(ProjectileType.PROJECTILE_PEA);
        assertSame(aBoss, aProjectile.findCollisionTarget());
    }

    // ---------------------------------------------------------------- splash

    @Test
    void splashDamageSplitsBetweenTargetAndNeighbours() {
        mBoard.mZombies = new DataArray<Zombie>(StubZombie::new, 64, "stub");
        StubZombie aTarget = (StubZombie) mBoard.mZombies.dataArrayAlloc();
        aTarget.mRow = 0;
        aTarget.rect = new Rect(130, 200, 80, 100);

        // 3 neighbours in the same row inside the melon hit rect (120,200,60,40).
        for (int i = 0; i < 3; i++) {
            StubZombie aZombie = (StubZombie) mBoard.mZombies.dataArrayAlloc();
            aZombie.mRow = 0;
            aZombie.rect = new Rect(130, 200, 80, 100);
        }

        Projectile aMelon = newProjectile(ProjectileType.PROJECTILE_MELON);
        aMelon.mX = 100;
        aMelon.mY = 200;
        aMelon.doSplashDamage(aTarget);

        // damage 80 -> splash 26, below the 80*7 cap.
        assertEquals(1, aTarget.damageTaken.size());
        assertEquals(80, aTarget.damageTaken.get(0)[0]);
        int aShieldFlag = 1 << DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY;
        assertEquals(aShieldFlag, aTarget.damageTaken.get(0)[1]);
    }

    @Test
    void splashDamageIsCappedForLargeGroups() {
        mBoard.mZombies = new DataArray<Zombie>(StubZombie::new, 128, "stub");
        StubZombie aTarget = (StubZombie) mBoard.mZombies.dataArrayAlloc();
        aTarget.mRow = 0;
        aTarget.rect = new Rect(130, 200, 80, 100);

        for (int i = 0; i < 40; i++) {
            StubZombie aZombie = (StubZombie) mBoard.mZombies.dataArrayAlloc();
            aZombie.mRow = 0;
            aZombie.rect = new Rect(130, 200, 80, 100);
        }

        Projectile aMelon = newProjectile(ProjectileType.PROJECTILE_MELON);
        aMelon.mX = 100;
        aMelon.mY = 200;
        aMelon.doSplashDamage(aTarget);

        // aSplashDamageAmount = 26*40 = 1040 > 560 -> splashDamage = 80*560/(1040*3) = 14.
        assertEquals(80, aTarget.damageTaken.get(0)[0]);
        for (int i = 0; i < mBoard.mZombies.mSize; i++) {
            StubZombie aZombie = (StubZombie) mBoard.mZombies.dataArrayGetItemAt(i);
            if (aZombie == aTarget) {
                continue;
            }
            assertEquals(14, aZombie.damageTaken.get(0)[0]);
        }
    }

    // ------------------------------------------------------------ impact sound

    @Test
    void impactSoundSelectionMatchesUpstreamTable() {
        assertFoleys(ProjectileType.PROJECTILE_KERNEL, null, HelmType.HELMTYPE_NONE,
            new int[] {FoleyType.FOLEY_KERNEL_SPLAT});
        assertFoleys(ProjectileType.PROJECTILE_BUTTER, null, HelmType.HELMTYPE_NONE,
            new int[] {FoleyType.FOLEY_BUTTER});
        assertFoleys(ProjectileType.PROJECTILE_MELON, null, HelmType.HELMTYPE_NONE,
            new int[] {FoleyType.FOLEY_MELONIMPACT});
        assertFoleys(ProjectileType.PROJECTILE_WINTERMELON, null, HelmType.HELMTYPE_NONE,
            new int[] {FoleyType.FOLEY_MELONIMPACT});
        assertFoleys(ProjectileType.PROJECTILE_PEA, null, HelmType.HELMTYPE_NONE,
            new int[] {FoleyType.FOLEY_SPLAT});
        assertFoleys(ProjectileType.PROJECTILE_PEA, "stub", HelmType.HELMTYPE_PAIL,
            new int[] {FoleyType.FOLEY_SHIELD_HIT});
        assertFoleys(ProjectileType.PROJECTILE_PEA, "stub", HelmType.HELMTYPE_TRAFFIC_CONE,
            new int[] {FoleyType.FOLEY_PLASTIC_HIT, FoleyType.FOLEY_SPLAT});
        assertFoleys(ProjectileType.PROJECTILE_PEA, "stub", HelmType.HELMTYPE_FOOTBALL,
            new int[] {FoleyType.FOLEY_PLASTIC_HIT, FoleyType.FOLEY_SPLAT});
        assertFoleys(ProjectileType.PROJECTILE_FIREBALL, "stub", HelmType.HELMTYPE_NONE,
            new int[] {FoleyType.FOLEY_IGNITE});
    }

    private void assertFoleys(int theType, String theZombieKind, int theHelmType, int[] theExpected) {
        mApp.foleys.clear();
        StubZombie aZombie = null;
        if (theZombieKind != null) {
            aZombie = new StubZombie();
            aZombie.mHelmType = theHelmType;
        }
        Projectile aProjectile = newProjectile(theType);
        aProjectile.playImpactSound(aZombie);

        assertEquals(theExpected.length, mApp.foleys.size(), "foley count for type " + theType);
        for (int i = 0; i < theExpected.length; i++) {
            assertEquals(theExpected[i], (int) mApp.foleys.get(i), "foley " + i + " for type " + theType);
        }
    }

    // ---------------------------------------------------------------- helpers

    @Test
    void cantHitHighGroundPerType() {
        assertTrue(newProjectile(ProjectileType.PROJECTILE_PEA).cantHitHighGround());
        assertTrue(newProjectile(ProjectileType.PROJECTILE_SNOWPEA).cantHitHighGround());
        assertTrue(newProjectile(ProjectileType.PROJECTILE_STAR).cantHitHighGround());
        assertTrue(newProjectile(ProjectileType.PROJECTILE_PUFF).cantHitHighGround());
        assertTrue(newProjectile(ProjectileType.PROJECTILE_FIREBALL).cantHitHighGround());
        assertTrue(!newProjectile(ProjectileType.PROJECTILE_MELON).cantHitHighGround());

        Projectile aBackwards = newProjectile(ProjectileType.PROJECTILE_PEA);
        aBackwards.mMotionType = ProjectileMotion.MOTION_BACKWARDS;
        assertTrue(!aBackwards.cantHitHighGround());

        Projectile aOnHighGround = newProjectile(ProjectileType.PROJECTILE_PEA);
        aOnHighGround.mOnHighGround = true;
        assertTrue(!aOnHighGround.cantHitHighGround());
    }

    @Test
    void getProjectileDefResolvesTableEntry() {
        assertNotNull(newProjectile(ProjectileType.PROJECTILE_COBBIG).getProjectileDef());
        assertEquals(300, newProjectile(ProjectileType.PROJECTILE_COBBIG).getProjectileDef().mDamage);
    }
}
