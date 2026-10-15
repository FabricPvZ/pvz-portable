/*
 * Ported from src/Lawn/Projectile.h:46-108 and src/Lawn/Projectile.cpp.
 *
 * Field/member names and per-type behaviours follow the upstream source verbatim;
 * line references are kept on the non-obvious maths. Randomness flows through
 * cn.enaium.fabricpvz.portable.pvzp.PvzpCommon / cn.enaium.fabricpvz.portable.sexy.Common (MTRand).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachmentIDRef;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

public class Projectile extends GameObject {

    /** Ported from src/Lawn/Projectile.cpp:38-53 (gProjectileDefinition[]). */
    public static final ProjectileDefinition[] gProjectileDefinition = {
        new ProjectileDefinition(ProjectileType.PROJECTILE_PEA, 0, 20),
        new ProjectileDefinition(ProjectileType.PROJECTILE_SNOWPEA, 0, 20),
        new ProjectileDefinition(ProjectileType.PROJECTILE_CABBAGE, 0, 40),
        new ProjectileDefinition(ProjectileType.PROJECTILE_MELON, 0, 80),
        new ProjectileDefinition(ProjectileType.PROJECTILE_PUFF, 0, 20),
        new ProjectileDefinition(ProjectileType.PROJECTILE_WINTERMELON, 0, 80),
        new ProjectileDefinition(ProjectileType.PROJECTILE_FIREBALL, 0, 40),
        new ProjectileDefinition(ProjectileType.PROJECTILE_STAR, 0, 20),
        new ProjectileDefinition(ProjectileType.PROJECTILE_SPIKE, 0, 20),
        new ProjectileDefinition(ProjectileType.PROJECTILE_BASKETBALL, 0, 75),
        new ProjectileDefinition(ProjectileType.PROJECTILE_KERNEL, 0, 20),
        new ProjectileDefinition(ProjectileType.PROJECTILE_COBBIG, 0, 300),
        new ProjectileDefinition(ProjectileType.PROJECTILE_BUTTER, 0, 40),
        new ProjectileDefinition(ProjectileType.PROJECTILE_ZOMBIE_PEA, 0, 20)
    };

    // Ported from src/Lawn/Projectile.h:50-81.
    public int mFrame;
    public int mNumFrames;
    public int mAnimCounter;
    public float mPosX;
    public float mPosY;
    public float mPosZ;
    public float mVelX;
    public float mVelY;
    public float mVelZ;
    public float mAccZ;
    public float mShadowY;
    public boolean mDead;
    public int mAnimTicksPerFrame;
    public int mMotionType;
    public int mProjectileType;
    public int mProjectileAge;
    public int mClickBackoffCounter;
    public float mRotation;
    public float mRotationSpeed;
    public boolean mOnHighGround;
    public int mDamageRangeFlags;
    public int mHitTorchwoodGridX;
    public AttachmentIDRef mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
    public float mCobTargetX;
    public int mCobTargetRow;
    public int mTargetZombieID;
    public int mLastPortalX;

    /** Ported from src/Lawn/Projectile.cpp:55. */
    public Projectile() {
    }

    /** Ported from src/Lawn/Projectile.cpp:63-155. */
    public void projectileInitialize(int theX, int theY, int theRenderOrder, int theRow, int theProjectileType) {
        int aGridX = mBoard.pixelToGridXKeepOnBoard(theX, theY);
        mProjectileType = theProjectileType;
        mPosX = theX;
        mPosY = theY;
        mPosZ = 0.0f;
        mVelX = 0.0f;
        mVelY = 0.0f;
        mVelZ = 0.0f;
        mAccZ = 0.0f;
        mShadowY = mBoard.gridToPixelY(aGridX, theRow) + 67.0f;
        mHitTorchwoodGridX = -1;
        mMotionType = ProjectileMotion.MOTION_STRAIGHT;
        mFrame = 0;
        mNumFrames = 1;
        mRow = theRow;
        mCobTargetX = 0.0f;
        mDamageRangeFlags = 0;
        mDead = false;
        mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
        mCobTargetRow = 0;
        mTargetZombieID = ZombieID.ZOMBIEID_NULL;
        mOnHighGround = mBoard.mGridSquareType[aGridX][theRow] == GridSquareType.GRIDSQUARE_HIGH_GROUND;
        if (mBoard.stageHasRoof() && theX < 480) {
            mShadowY -= 12.0f;
        }
        mRenderOrder = theRenderOrder;
        mRotation = 0.0f;
        mRotationSpeed = 0.0f;
        mWidth = 40;
        mHeight = 40;
        mProjectileAge = 0;
        mClickBackoffCounter = 0;
        mAnimTicksPerFrame = 0;

        switch (mProjectileType) {
            case ProjectileType.PROJECTILE_CABBAGE:
            case ProjectileType.PROJECTILE_BUTTER:
                mRotation = (float) (-7 * GameConstants.PI / 25);  // DEG_TO_RAD(-50.4f)
                mRotationSpeed = PvzpCommon.randRangeFloat(-0.08f, -0.02f);
                break;
            case ProjectileType.PROJECTILE_MELON:
            case ProjectileType.PROJECTILE_WINTERMELON:
                mRotation = (float) (-2 * GameConstants.PI / 5);  // DEG_TO_RAD(-72.0f)
                mRotationSpeed = PvzpCommon.randRangeFloat(-0.08f, -0.02f);
                break;
            case ProjectileType.PROJECTILE_KERNEL:
                mRotation = 0.0f;
                mRotationSpeed = PvzpCommon.randRangeFloat(-0.2f, -0.08f);
                break;
            case ProjectileType.PROJECTILE_SNOWPEA: {
                PvzpParticleSystem aParticle = mApp.addPvzpParticle(mPosX + 8.0f, mPosY + 13.0f, 400000,
                    ParticleEffect.PARTICLE_SNOWPEA_TRAIL);
                Attachment.attachParticle(mAttachmentID, aParticle, 8.0f, 13.0f);
                break;
            }
            case ProjectileType.PROJECTILE_FIREBALL:
                break;  // upstream PVZP_ASSERT(false): never constructed directly
            case ProjectileType.PROJECTILE_COBBIG:
                mWidth = mApp.getImage(Resources.IMAGE_REANIM_COBCANNON_COB_ID).getWidth();
                mHeight = mApp.getImage(Resources.IMAGE_REANIM_COBCANNON_COB_ID).getHeight();
                mRotation = (float) (GameConstants.PI / 2);
                break;
            case ProjectileType.PROJECTILE_PUFF: {
                PvzpParticleSystem aParticle = mApp.addPvzpParticle(mPosX + 13.0f, mPosY + 13.0f, 400000,
                    ParticleEffect.PARTICLE_PUFFSHROOM_TRAIL);
                Attachment.attachParticle(mAttachmentID, aParticle, 13.0f, 13.0f);
                break;
            }
            case ProjectileType.PROJECTILE_BASKETBALL:
                mRotation = PvzpCommon.randRangeFloat(0.0f, (float) (2 * GameConstants.PI));
                mRotationSpeed = PvzpCommon.randRangeFloat(0.05f, 0.1f);
                break;
            case ProjectileType.PROJECTILE_STAR:
                mShadowY += 15.0f;
                mRotationSpeed = PvzpCommon.randRangeFloat(0.05f, 0.1f);
                if (Common.rand(2) == 0) {
                    mRotationSpeed = -mRotationSpeed;
                }
                break;
            default:
                break;
        }

        mAnimCounter = 0;
        mX = (int) mPosX;
        mY = (int) mPosY;
    }

    /** Ported from src/Lawn/Projectile.cpp:157-194. */
    public Plant findCollisionTargetPlant() {
        Rect aProjectileRect = getProjectileRect();

        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow != mRow) {
                continue;
            }

            if (mProjectileType == ProjectileType.PROJECTILE_ZOMBIE_PEA) {
                if (aPlant.mSeedType == SeedType.SEED_PUFFSHROOM
                    || aPlant.mSeedType == SeedType.SEED_SUNSHROOM
                    || aPlant.mSeedType == SeedType.SEED_POTATOMINE
                    || aPlant.mSeedType == SeedType.SEED_SPIKEWEED
                    || aPlant.mSeedType == SeedType.SEED_SPIKEROCK
                    || aPlant.mSeedType == SeedType.SEED_LILYPAD) {  // zombie peas cannot hit low plants
                    continue;
                }
            }

            Rect aPlantRect = aPlant.getPlantRect();
            if (Board.getRectOverlap(aProjectileRect, aPlantRect) > 8) {
                if (mProjectileType == ProjectileType.PROJECTILE_ZOMBIE_PEA) {
                    return mBoard.getTopPlantAt(aPlant.mPlantCol, aPlant.mRow, PlantPriority.TOPPLANT_EATING_ORDER);
                } else {
                    return mBoard.getTopPlantAt(aPlant.mPlantCol, aPlant.mRow, PlantPriority.TOPPLANT_CATAPULT_ORDER);
                }
            }
        }

        return null;
    }

    /** Ported from src/Lawn/Projectile.cpp:196-222. */
    public boolean peaAboutToHitTorchwood() {
        if (mMotionType != ProjectileMotion.MOTION_STRAIGHT) {
            return false;
        }

        if (mProjectileType != ProjectileType.PROJECTILE_PEA && mProjectileType != ProjectileType.PROJECTILE_SNOWPEA) {
            return false;
        }

        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == SeedType.SEED_TORCHWOOD && aPlant.mRow == mRow && !aPlant.notOnGround()
                && mHitTorchwoodGridX != aPlant.mPlantCol) {
                Rect aPlantAttackRect = aPlant.getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);
                Rect aProjectileRect = getProjectileRect();
                aProjectileRect.mX += 40;

                if (Board.getRectOverlap(aPlantAttackRect, aProjectileRect) > 10) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Ported from src/Lawn/Projectile.cpp:224-262. */
    public Zombie findCollisionTarget() {
        if (peaAboutToHitTorchwood()) {  // a pea about to hit a torchwood skips zombie collision
            return null;
        }

        Rect aProjectileRect = getProjectileRect();
        Zombie aBestZombie = null;
        int aMinX = 0;

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if ((aZombie.mZombieType == ZombieType.ZOMBIE_BOSS || aZombie.mRow == mRow)
                && aZombie.effectedByDamage(mDamageRangeFlags)) {
                if (aZombie.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL && mPosZ <= 45.0f) {
                    continue;
                }

                if (mProjectileType == ProjectileType.PROJECTILE_STAR && mProjectileAge < 25 && mVelX >= 0.0f
                    && aZombie.mZombieType == ZombieType.ZOMBIE_DIGGER) {
                    continue;
                }

                Rect aZombieRect = aZombie.getZombieRect();
                if (Board.getRectOverlap(aProjectileRect, aZombieRect) >= 0) {
                    if (aBestZombie == null || aZombie.mX < aMinX) {
                        aBestZombie = aZombie;
                        aMinX = aZombie.mX;
                    }
                }
            }
        }

        return aBestZombie;
    }

    /** Ported from src/Lawn/Projectile.cpp:264-335. */
    public void checkForCollision() {
        if (mMotionType == ProjectileMotion.MOTION_PUFF && mProjectileAge >= 75) {
            die();
            return;
        }

        if (mPosX > GameConstants.WIDE_BOARD_WIDTH || mPosX + mWidth < 0.0f) {
            die();
            return;
        }

        if (mMotionType == ProjectileMotion.MOTION_HOMING) {
            Zombie aZombie = mBoard.zombieTryToGet(mTargetZombieID);
            if (aZombie != null && aZombie.effectedByDamage(mDamageRangeFlags)) {
                Rect aProjectileRect = getProjectileRect();
                Rect aZombieRect = aZombie.getZombieRect();
                if (Board.getRectOverlap(aProjectileRect, aZombieRect) >= 0 && mPosY > aZombieRect.mY
                    && mPosY < aZombieRect.mY + aZombieRect.mHeight) {
                    doImpact(aZombie);
                }
            }
            return;
        }

        if (mProjectileType == ProjectileType.PROJECTILE_STAR && (mPosY > 600.0f || mPosY < 40.0f)) {
            die();
            return;
        }

        if ((mProjectileType == ProjectileType.PROJECTILE_PEA || mProjectileType == ProjectileType.PROJECTILE_STAR)
            && mShadowY - mPosY > 90.0f) {
            return;
        }

        if (mMotionType == ProjectileMotion.MOTION_FLOAT_OVER) {
            return;
        }

        if (mProjectileType == ProjectileType.PROJECTILE_ZOMBIE_PEA) {
            Plant aPlant = findCollisionTargetPlant();
            if (aPlant != null) {
                ProjectileDefinition aProjectileDef = getProjectileDef();
                aPlant.takeDamage(aProjectileDef.mDamage);
                aPlant.mEatenFlashCountdown = Math.max(aPlant.mEatenFlashCountdown, 25);

                mApp.playFoley(FoleyType.FOLEY_SPLAT);
                mApp.addPvzpParticle(mPosX - 3.0f, mPosY + 17.0f, mRenderOrder + 1, ParticleEffect.PARTICLE_PEA_SPLAT);
                die();
            }
            return;
        }

        Zombie aZombie = findCollisionTarget();
        if (aZombie != null) {
            if (aZombie.mOnHighGround && cantHitHighGround()) {
                return;
            }

            doImpact(aZombie);
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:337-349. */
    public boolean cantHitHighGround() {
        if (mMotionType == ProjectileMotion.MOTION_BACKWARDS || mMotionType == ProjectileMotion.MOTION_HOMING) {
            return false;
        }

        return (mProjectileType == ProjectileType.PROJECTILE_PEA
            || mProjectileType == ProjectileType.PROJECTILE_SNOWPEA
            || mProjectileType == ProjectileType.PROJECTILE_STAR
            || mProjectileType == ProjectileType.PROJECTILE_PUFF
            || mProjectileType == ProjectileType.PROJECTILE_FIREBALL) && !mOnHighGround;
    }

    /** Ported from src/Lawn/Projectile.cpp:351-388. */
    public void checkForHighGround() {
        float aShadowDelta = mShadowY - mPosY;

        if (mProjectileType == ProjectileType.PROJECTILE_PEA
            || mProjectileType == ProjectileType.PROJECTILE_SNOWPEA
            || mProjectileType == ProjectileType.PROJECTILE_FIREBALL
            || mProjectileType == ProjectileType.PROJECTILE_SPIKE
            || mProjectileType == ProjectileType.PROJECTILE_COBBIG) {
            if (aShadowDelta < 28.0f) {
                doImpact(null);
                return;
            }
        }

        if (mProjectileType == ProjectileType.PROJECTILE_PUFF && aShadowDelta < 0.0f) {
            doImpact(null);
            return;
        }

        if (mProjectileType == ProjectileType.PROJECTILE_STAR && aShadowDelta < 23.0f) {
            doImpact(null);
            return;
        }

        if (cantHitHighGround()) {
            int aGridX = mBoard.pixelToGridXKeepOnBoard((int) (mPosX + 30), (int) mPosY);
            if (mBoard.mGridSquareType[aGridX][mRow] == GridSquareType.GRIDSQUARE_HIGH_GROUND) {
                doImpact(null);
            }
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:390-399. */
    public boolean isSplashDamage(Zombie theZombie) {
        if (mProjectileType == ProjectileType.PROJECTILE_FIREBALL && theZombie != null && theZombie.isFireResistant()) {
            return false;
        }

        return mProjectileType == ProjectileType.PROJECTILE_MELON
            || mProjectileType == ProjectileType.PROJECTILE_WINTERMELON
            || mProjectileType == ProjectileType.PROJECTILE_FIREBALL;
    }

    /** Ported from src/Lawn/Projectile.cpp:401-424. */
    public int getDamageFlags(Zombie theZombie) {
        int[] aDamageFlags = new int[1];

        if (isSplashDamage(theZombie)) {
            PvzpCommon.setBit(aDamageFlags, DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY, true);
        } else if (mMotionType == ProjectileMotion.MOTION_LOBBED || mMotionType == ProjectileMotion.MOTION_BACKWARDS) {
            PvzpCommon.setBit(aDamageFlags, DamageFlags.DAMAGE_BYPASSES_SHIELD, true);
        } else if (mMotionType == ProjectileMotion.MOTION_STAR && mVelX < 0.0f) {
            PvzpCommon.setBit(aDamageFlags, DamageFlags.DAMAGE_BYPASSES_SHIELD, true);
        }

        if (mProjectileType == ProjectileType.PROJECTILE_SNOWPEA
            || mProjectileType == ProjectileType.PROJECTILE_WINTERMELON) {
            PvzpCommon.setBit(aDamageFlags, DamageFlags.DAMAGE_FREEZE, true);
        }

        return aDamageFlags[0];
    }

    /** Ported from src/Lawn/Projectile.cpp:426-458. */
    public boolean isZombieHitBySplash(Zombie theZombie) {
        Rect aProjectileRect = getProjectileRect();
        if (mProjectileType == ProjectileType.PROJECTILE_FIREBALL) {
            aProjectileRect.mWidth = 100;
        }

        int aRowDeviation = theZombie.mRow - mRow;
        Rect aZombieRect = theZombie.getZombieRect();
        if (theZombie.isFireResistant() && mProjectileType == ProjectileType.PROJECTILE_FIREBALL) {
            return false;
        }

        if (theZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
            aRowDeviation = 0;
        }
        if (mProjectileType == ProjectileType.PROJECTILE_FIREBALL) {
            if (aRowDeviation != 0) {
                return false;
            }
        } else if (aRowDeviation > 1 || aRowDeviation < -1) {
            return false;
        }

        return theZombie.effectedByDamage(mDamageRangeFlags)
            && Board.getRectOverlap(aProjectileRect, aZombieRect) >= 0;
    }

    /** Ported from src/Lawn/Projectile.cpp:460-507. */
    public void doSplashDamage(Zombie theZombie) {
        ProjectileDefinition aProjectileDef = getProjectileDef();

        int aZombiesGetSplashed = 0;
        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie != theZombie && isZombieHitBySplash(aZombie)) {
                aZombiesGetSplashed++;
            }
        }

        int aOriginalDamage = aProjectileDef.mDamage;
        int aSplashDamage = aProjectileDef.mDamage / 3;
        int aMaxSplashDamageAmount = aOriginalDamage * 7;
        if (mProjectileType == ProjectileType.PROJECTILE_FIREBALL) {
            aMaxSplashDamageAmount = aOriginalDamage;
        }
        int aSplashDamageAmount = aSplashDamage * aZombiesGetSplashed;
        if (aSplashDamageAmount > aMaxSplashDamageAmount) {
            // aSplashDamage *= aMaxSplashDamageAmount / aSplashDamage;
            aSplashDamage = aOriginalDamage * aMaxSplashDamageAmount / (aSplashDamageAmount * 3);
            aSplashDamage = Math.max(aSplashDamage, 1);
        }

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (isZombieHitBySplash(aZombie)) {
                int aDamageFlags = getDamageFlags(aZombie);
                if (aZombie == theZombie) {
                    aZombie.takeDamage(aOriginalDamage, aDamageFlags);
                } else {
                    aZombie.takeDamage(aSplashDamage, aDamageFlags);
                }
            }
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:509-638. */
    public void updateLobMotion() {
        if (mProjectileType == ProjectileType.PROJECTILE_COBBIG && mPosZ < -700.0f) {
            mVelZ = 8.0f;
            mRow = mCobTargetRow;
            mPosX = mCobTargetX;
            int aCobTargetCol = mBoard.pixelToGridXKeepOnBoard((int) mCobTargetX, 0);
            mPosY = mBoard.gridToPixelY(aCobTargetCol, mCobTargetRow);
            mShadowY = mPosY + 67.0f;
            mRotation = (float) (-GameConstants.PI / 2);
        }

        mVelZ += mAccZ;
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY) {
            mVelZ += mAccZ;
        }
        mPosX += mVelX;
        mPosY += mVelY;
        mPosZ += mVelZ;

        boolean isRising = mVelZ < 0.0f;
        if (isRising && (mProjectileType == ProjectileType.PROJECTILE_BASKETBALL
            || mProjectileType == ProjectileType.PROJECTILE_COBBIG)) {
            return;
        }
        if (mProjectileAge > 20) {
            if (isRising) {
                return;
            }

            float aMinCollisionZ = 0.0f;
            if (mProjectileType == ProjectileType.PROJECTILE_BUTTER) {
                aMinCollisionZ = -32.0f;
            } else if (mProjectileType == ProjectileType.PROJECTILE_BASKETBALL) {
                aMinCollisionZ = 60.0f;
            } else if (mProjectileType == ProjectileType.PROJECTILE_MELON
                || mProjectileType == ProjectileType.PROJECTILE_WINTERMELON) {
                aMinCollisionZ = -35.0f;
            } else if (mProjectileType == ProjectileType.PROJECTILE_CABBAGE
                || mProjectileType == ProjectileType.PROJECTILE_KERNEL) {
                aMinCollisionZ = -30.0f;
            } else if (mProjectileType == ProjectileType.PROJECTILE_COBBIG) {
                aMinCollisionZ = -60.0f;
            }
            if (mBoard.mPlantRow[mRow] == PlantRowType.PLANTROW_POOL) {
                aMinCollisionZ += 40.0f;
            }

            if (mPosZ <= aMinCollisionZ) {
                return;
            }
        }

        Plant aPlant = null;
        Zombie aZombie = null;
        if (mProjectileType == ProjectileType.PROJECTILE_BASKETBALL
            || mProjectileType == ProjectileType.PROJECTILE_ZOMBIE_PEA) {
            aPlant = findCollisionTargetPlant();
        } else {
            aZombie = findCollisionTarget();
        }

        float aGroundZ = 80.0f;
        if (mProjectileType == ProjectileType.PROJECTILE_COBBIG) {
            aGroundZ = -40.0f;
        }
        boolean hitGround = mPosZ > aGroundZ;
        if (aZombie == null && aPlant == null && !hitGround) {
            return;
        }

        if (aPlant != null) {
            Plant aUmbrellaPlant = mBoard.findUmbrellaPlant(aPlant.mPlantCol, aPlant.mRow);
            if (aUmbrellaPlant != null) {
                if (aUmbrellaPlant.mState == PlantState.STATE_UMBRELLA_REFLECTING) {
                    mApp.playFoley(FoleyType.FOLEY_SPLAT);
                    int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 1);
                    mApp.addPvzpParticle(mPosX + 20.0f, mPosY + 20.0f, aRenderPosition,
                        ParticleEffect.PARTICLE_UMBRELLA_REFLECT);
                    die();
                } else if (aUmbrellaPlant.mState != PlantState.STATE_UMBRELLA_TRIGGERED) {
                    mApp.playFoley(FoleyType.FOLEY_UMBRELLA);
                    aUmbrellaPlant.doSpecial();
                }
            } else {
                aPlant.takeDamage(getProjectileDef().mDamage);
                aPlant.mEatenFlashCountdown = Math.max(aPlant.mEatenFlashCountdown, 25);
                mApp.playFoley(FoleyType.FOLEY_SPLAT);
                die();
            }
        } else if (mProjectileType == ProjectileType.PROJECTILE_COBBIG) {
            int aBeforeGargantuarCount = mBoard.getLiveGargantuarCount();
            mBoard.killAllZombiesInRadius(mRow, (int) (mPosX + 80), (int) (mPosY + 40), 115, 1, true,
                mDamageRangeFlags);
            int aAfterGargantuarCount = mBoard.getLiveGargantuarCount();
            mBoard.mGargantuarsKillsByCornCob += aBeforeGargantuarCount - aAfterGargantuarCount;
            if (mBoard.mGargantuarsKillsByCornCob >= 2) {
                ReportAchievement.giveAchievement(mApp, AchievementId.PopcornParty, true);
            }

            doImpact(null);
        } else {
            doImpact(aZombie);
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:640-737. */
    public void updateNormalMotion() {
        if (mMotionType == ProjectileMotion.MOTION_BACKWARDS) {
            mPosX -= 3.33f;
        } else if (mMotionType == ProjectileMotion.MOTION_HOMING) {
            Zombie aZombie = mBoard.zombieTryToGet(mTargetZombieID);
            if (aZombie != null && aZombie.effectedByDamage(mDamageRangeFlags)) {
                Rect aZombieRect = aZombie.getZombieRect();
                SexyVector2 aTargetCenter = new SexyVector2(aZombie.zombieTargetLeadX(0.0f),
                    aZombieRect.mY + aZombieRect.mHeight / 2);
                SexyVector2 aProjectileCenter = new SexyVector2(mPosX + mWidth / 2, mPosY + mHeight / 2);
                SexyVector2 aToTarget = aTargetCenter.minus(aProjectileCenter).normalize();
                SexyVector2 aMotion = new SexyVector2(mVelX, mVelY);

                aMotion.addAssign(aToTarget.times(0.001f * mProjectileAge));
                aMotion = aMotion.normalize();
                aMotion.multiplyAssign(2.0f);

                mVelX = aMotion.x;
                mVelY = aMotion.y;
                mRotation = (float) -Math.atan2(mVelY, mVelX);
            }

            mPosY += mVelY;
            mPosX += mVelX;
            mShadowY += mVelY;
            mRow = mBoard.pixelToGridYKeepOnBoard((int) mPosX, (int) mPosY);
        } else if (mMotionType == ProjectileMotion.MOTION_STAR) {
            mPosY += mVelY;
            mPosX += mVelX;
            mShadowY += mVelY;

            if (mVelY != 0.0f) {
                mRow = mBoard.pixelToGridYKeepOnBoard((int) mPosX, (int) mPosY);
            }
        } else if (mMotionType == ProjectileMotion.MOTION_BEE) {
            if (mProjectileAge < 60) {
                mPosY -= 0.5f;
            }
            mPosX += 3.33f;
        } else if (mMotionType == ProjectileMotion.MOTION_FLOAT_OVER) {
            if (mVelZ < 0.0f) {
                mVelZ += 0.002f;
                mVelZ = Math.min(mVelZ, 0.0f);
                mPosY += mVelZ;
                mRotation = (float) (0.3f - 0.7f * mVelZ * GameConstants.PI * 0.25f);
            }
            mPosX += 0.4f;
        } else if (mMotionType == ProjectileMotion.MOTION_BEE_BACKWARDS) {
            if (mProjectileAge < 60) {
                mPosY -= 0.5f;
            }
            mPosX -= 3.33f;
        } else if (mMotionType == ProjectileMotion.MOTION_THREEPEATER) {
            mPosX += 3.33f;
            mPosY += mVelY;
            mVelY *= 0.97f;
            mShadowY += mVelY;
        } else {
            mPosX += 3.33f;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY) {
            if (mMotionType == ProjectileMotion.MOTION_FLOAT_OVER) {
                mVelZ += 0.004f;
            } else {
                mVelZ += 0.2f;
            }

            mPosY += mVelZ;
        }

        checkForCollision();
        checkForHighGround();
    }

    /** Ported from src/Lawn/Projectile.cpp:739-775. */
    public void updateMotion() {
        if (mAnimTicksPerFrame > 0) {
            mAnimCounter = (mAnimCounter + 1) % (mNumFrames * mAnimTicksPerFrame);
            mFrame = mAnimCounter / mAnimTicksPerFrame;
        }

        int aOldRow = mRow;
        float aOldY = mBoard.getPosYBasedOnRow(mPosX, mRow);
        if (mMotionType == ProjectileMotion.MOTION_LOBBED) {
            updateLobMotion();
        } else {
            updateNormalMotion();
        }

        float aSlopeHeightChange = mBoard.getPosYBasedOnRow(mPosX, aOldRow) - aOldY;
        if (mProjectileType == ProjectileType.PROJECTILE_COBBIG) {
            aSlopeHeightChange = 0.0f;  // Fix The Roof Offset Bug of Corn Cob
        }
        if (mMotionType == ProjectileMotion.MOTION_FLOAT_OVER) {
            mPosY += aSlopeHeightChange;
        }
        if (mMotionType == ProjectileMotion.MOTION_LOBBED) {
            mPosY += aSlopeHeightChange;
            mPosZ -= aSlopeHeightChange;
        }
        mShadowY += aSlopeHeightChange;
        mX = (int) mPosX;
        mY = (int) (mPosY + mPosZ);
    }

    /** Ported from src/Lawn/Projectile.cpp:777-821. */
    public void playImpactSound(Zombie theZombie) {
        boolean aPlayHelmSound = true;
        boolean aPlaySplatSound = true;
        if (mProjectileType == ProjectileType.PROJECTILE_KERNEL) {
            mApp.playFoley(FoleyType.FOLEY_KERNEL_SPLAT);
            aPlayHelmSound = false;
            aPlaySplatSound = false;
        } else if (mProjectileType == ProjectileType.PROJECTILE_BUTTER) {
            mApp.playFoley(FoleyType.FOLEY_BUTTER);
            aPlaySplatSound = false;
        } else if (mProjectileType == ProjectileType.PROJECTILE_FIREBALL && isSplashDamage(theZombie)) {
            mApp.playFoley(FoleyType.FOLEY_IGNITE);
            aPlayHelmSound = false;
            aPlaySplatSound = false;
        } else if (mProjectileType == ProjectileType.PROJECTILE_MELON
            || mProjectileType == ProjectileType.PROJECTILE_WINTERMELON) {
            mApp.playFoley(FoleyType.FOLEY_MELONIMPACT);
            aPlaySplatSound = false;
        }

        if (aPlayHelmSound && theZombie != null) {
            if (theZombie.mHelmType == HelmType.HELMTYPE_PAIL) {
                mApp.playFoley(FoleyType.FOLEY_SHIELD_HIT);
                aPlaySplatSound = false;
            } else if (theZombie.mHelmType == HelmType.HELMTYPE_TRAFFIC_CONE
                || theZombie.mHelmType == HelmType.HELMTYPE_DIGGER
                || theZombie.mHelmType == HelmType.HELMTYPE_FOOTBALL) {
                mApp.playFoley(FoleyType.FOLEY_PLASTIC_HIT);
            }
        }

        if (aPlaySplatSound) {
            mApp.playFoley(FoleyType.FOLEY_SPLAT);
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:823-938. */
    public void doImpact(Zombie theZombie) {
        playImpactSound(theZombie);

        if (isSplashDamage(theZombie)) {
            if (mProjectileType == ProjectileType.PROJECTILE_FIREBALL && theZombie != null) {
                theZombie.removeColdEffects();
            }

            doSplashDamage(theZombie);
        } else if (theZombie != null) {
            int aDamageFlags = getDamageFlags(theZombie);
            theZombie.takeDamage(getProjectileDef().mDamage, aDamageFlags);
        }

        float aLastPosX = mPosX - mVelX;
        float aLastPosY = mPosY + mPosZ - mVelY - mVelZ;
        int aEffect = ParticleEffect.PARTICLE_NONE;
        float aSplatPosX = mPosX + 12.0f;
        float aSplatPosY = mPosY + 12.0f;
        switch (mProjectileType) {
            case ProjectileType.PROJECTILE_MELON:
                mApp.addPvzpParticle(aLastPosX + 30.0f, aLastPosY + 30.0f, mRenderOrder + 1,
                    ParticleEffect.PARTICLE_MELONSPLASH);
                break;
            case ProjectileType.PROJECTILE_WINTERMELON:
                mApp.addPvzpParticle(aLastPosX + 30.0f, aLastPosY + 30.0f, mRenderOrder + 1,
                    ParticleEffect.PARTICLE_WINTERMELON);
                break;
            case ProjectileType.PROJECTILE_COBBIG: {
                int aRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, mCobTargetRow, 2);
                mApp.addPvzpParticle(mPosX + 80.0f, mPosY + 40.0f, aRenderOrder, ParticleEffect.PARTICLE_BLASTMARK);
                mApp.addPvzpParticle(mPosX + 80.0f, mPosY + 40.0f, mRenderOrder + 1,
                    ParticleEffect.PARTICLE_POPCORNSPLASH);
                mApp.playSample(Resources.SOUND_DOOMSHROOM_ID);
                mBoard.shakeBoard(3, -4);
                break;
            }
            case ProjectileType.PROJECTILE_PEA:
                aSplatPosX -= 15.0f;
                aEffect = ParticleEffect.PARTICLE_PEA_SPLAT;
                break;
            case ProjectileType.PROJECTILE_SNOWPEA:
                aSplatPosX -= 15.0f;
                aEffect = ParticleEffect.PARTICLE_SNOWPEA_SPLAT;
                break;
            case ProjectileType.PROJECTILE_FIREBALL: {
                if (isSplashDamage(theZombie)) {
                    Reanimation aFireReanim = mApp.addReanimation(mPosX + 38.0f, mPosY - 20.0f, mRenderOrder + 1,
                        ReanimationType.REANIM_JALAPENO_FIRE);
                    aFireReanim.mAnimTime = 0.25f;
                    aFireReanim.mAnimRate = 24.0f;
                    aFireReanim.overrideScale(0.7f, 0.4f);
                }
                break;
            }
            case ProjectileType.PROJECTILE_STAR:
                aEffect = ParticleEffect.PARTICLE_STAR_SPLAT;
                break;
            case ProjectileType.PROJECTILE_PUFF:
                aSplatPosX -= 20.0f;
                aEffect = ParticleEffect.PARTICLE_PUFF_SPLAT;
                break;
            case ProjectileType.PROJECTILE_CABBAGE:
                aSplatPosX = aLastPosX - 38.0f;
                aSplatPosY = aLastPosY + 23.0f;
                aEffect = ParticleEffect.PARTICLE_CABBAGE_SPLAT;
                break;
            case ProjectileType.PROJECTILE_BUTTER:
                aSplatPosX = aLastPosX - 20.0f;
                aSplatPosY = aLastPosY + 63.0f;
                aEffect = ParticleEffect.PARTICLE_BUTTER_SPLAT;

                if (theZombie != null) {
                    theZombie.applyButter();
                }
                break;
            default:
                break;
        }

        if (aEffect != ParticleEffect.PARTICLE_NONE) {
            if (theZombie != null) {
                float aPosX = aSplatPosX + 52.0f - theZombie.mX;
                float aPosY = aSplatPosY - theZombie.mY;
                if (theZombie.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL
                    || theZombie.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING_IN_POOL) {
                    aPosY += 60.0f;
                }
                if (mMotionType == ProjectileMotion.MOTION_BACKWARDS) {
                    aPosX -= 80.0f;
                } else if (mPosX > theZombie.mX + 40 && mMotionType != ProjectileMotion.MOTION_LOBBED) {
                    aPosX -= 60.0f;
                }

                aPosY = Math.min(Math.max(aPosY, 20.0f), 100.0f);
                theZombie.addAttachedParticle((int) aPosX, (int) aPosY, aEffect);
            } else {
                mApp.addPvzpParticle(aSplatPosX, aSplatPosY, mRenderOrder + 1, aEffect);
            }
        }

        die();
    }

    /** Ported from src/Lawn/Projectile.cpp:855-903. */
    public void update() {
        mProjectileAge++;
        if (mApp.mGameScene != GameScenes.SCENE_PLAYING && !mBoard.mCutScene.shouldRunUpsellBoard()) {
            return;
        }

        int aTime = 20;
        if (mProjectileType == ProjectileType.PROJECTILE_PEA
            || mProjectileType == ProjectileType.PROJECTILE_SNOWPEA
            || mProjectileType == ProjectileType.PROJECTILE_CABBAGE
            || mProjectileType == ProjectileType.PROJECTILE_MELON
            || mProjectileType == ProjectileType.PROJECTILE_WINTERMELON
            || mProjectileType == ProjectileType.PROJECTILE_KERNEL
            || mProjectileType == ProjectileType.PROJECTILE_BUTTER
            || mProjectileType == ProjectileType.PROJECTILE_COBBIG
            || mProjectileType == ProjectileType.PROJECTILE_ZOMBIE_PEA
            || mProjectileType == ProjectileType.PROJECTILE_SPIKE) {
            aTime = 0;
        }
        if (mProjectileAge > aTime) {
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PROJECTILE, mRow, 0);
        }

        if (mClickBackoffCounter > 0) {
            mClickBackoffCounter--;
        }
        mRotation += mRotationSpeed;

        updateMotion();
        Attachment.attachmentUpdateAndMove(mAttachmentID, mPosX, mPosY + mPosZ);
    }

    /** Ported from src/Lawn/Projectile.cpp:940-1000. */
    public void draw(Graphics g) {
        ProjectileDefinition aProjectileDef = getProjectileDef();

        Image aImage = null;
        float aScale = 1.0f;
        switch (mProjectileType) {
            case ProjectileType.PROJECTILE_COBBIG:
                aImage = mApp.getImage(Resources.IMAGE_REANIM_COBCANNON_COB_ID);
                aScale = 0.9f;
                break;
            case ProjectileType.PROJECTILE_PEA:
            case ProjectileType.PROJECTILE_ZOMBIE_PEA:
                aImage = mApp.getImage(Resources.IMAGE_PROJECTILEPEA_ID);
                break;
            case ProjectileType.PROJECTILE_SNOWPEA:
                aImage = mApp.getImage(Resources.IMAGE_PROJECTILESNOWPEA_ID);
                break;
            case ProjectileType.PROJECTILE_FIREBALL:
                aImage = null;
                break;
            case ProjectileType.PROJECTILE_SPIKE:
                aImage = mApp.getImage(Resources.IMAGE_PROJECTILECACTUS_ID);
                break;
            case ProjectileType.PROJECTILE_STAR:
                aImage = mApp.getImage(Resources.IMAGE_PROJECTILE_STAR_ID);
                break;
            case ProjectileType.PROJECTILE_PUFF:
                aImage = mApp.getImage(Resources.IMAGE_PUFFSHROOM_PUFF1_ID);
                aScale = PvzpCommon.pvzpAnimateCurveFloat(0, 30, mProjectileAge, 0.3f, 1.0f,
                    PvzpCurves.CURVE_LINEAR);
                break;
            case ProjectileType.PROJECTILE_BASKETBALL:
                aImage = mApp.getImage(Resources.IMAGE_REANIM_ZOMBIE_CATAPULT_BASKETBALL_ID);
                aScale = 1.1f;
                break;
            case ProjectileType.PROJECTILE_CABBAGE:
                aImage = mApp.getImage(Resources.IMAGE_REANIM_CABBAGEPULT_CABBAGE_ID);
                aScale = 1.0f;
                break;
            case ProjectileType.PROJECTILE_KERNEL:
                aImage = mApp.getImage(Resources.IMAGE_REANIM_CORNPULT_KERNAL_ID);
                aScale = 0.95f;
                break;
            case ProjectileType.PROJECTILE_BUTTER:
                aImage = mApp.getImage(Resources.IMAGE_REANIM_CORNPULT_BUTTER_ID);
                aScale = 0.8f;
                break;
            case ProjectileType.PROJECTILE_MELON:
                aImage = mApp.getImage(Resources.IMAGE_REANIM_MELONPULT_MELON_ID);
                aScale = 1.0f;
                break;
            case ProjectileType.PROJECTILE_WINTERMELON:
                aImage = mApp.getImage(Resources.IMAGE_REANIM_WINTERMELON_PROJECTILE_ID);
                aScale = 1.0f;
                break;
            default:
                break;  // upstream PVZP_ASSERT(false)
        }

        boolean aMirror = mMotionType == ProjectileMotion.MOTION_BEE_BACKWARDS;

        if (aImage != null) {
            int aCelWidth = aImage.getCelWidth();
            int aCelHeight = aImage.getCelHeight();
            Rect aSrcRect = new Rect(aCelWidth * mFrame, aCelHeight * aProjectileDef.mImageRow, aCelWidth,
                aCelHeight);
            if (PvzpCommon.floatApproxEqual(mRotation, 0.0f) && PvzpCommon.floatApproxEqual(aScale, 1.0f)) {
                Rect aDestRect = new Rect(0, 0, aCelWidth, aCelHeight);
                g.drawImageMirror(aImage, aDestRect, aSrcRect, aMirror);
            } else {
                float aOffsetX = mPosX + aCelWidth * 0.5f;
                float aOffsetY = mPosZ + mPosY + aCelHeight * 0.5f;
                SexyTransform2D aTransform = new SexyTransform2D();
                PvzpCommon.pvzpScaleRotateTransformMatrix(aTransform, aOffsetX + mBoard.mX, aOffsetY + mBoard.mY,
                    mRotation, aScale, aScale);
                PvzpCommon.pvzpBltMatrix(g, aImage, aTransform, g.getClipRect(), Color.White, g.getDrawMode(),
                    aSrcRect);
            }
        }

        if (mAttachmentID.value != AttachmentID.ATTACHMENTID_NULL) {
            Graphics theParticleGraphics = new Graphics(g);
            makeParentGraphicsFrame(theParticleGraphics);
            Attachment.attachmentDraw(mAttachmentID, theParticleGraphics, false);
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:1002-1090. */
    public void drawShadow(Graphics g) {
        int aCelCol = 0;
        float aScale = 1.0f;
        float aStretch = 1.0f;
        float aOffsetX = mPosX - mX;
        float aOffsetY = mPosY - mY;

        int aGridX = mBoard.pixelToGridXKeepOnBoard(mX, mY);
        boolean isHighGround = false;
        if (mBoard.mGridSquareType[aGridX][mRow] == GridSquareType.GRIDSQUARE_HIGH_GROUND) {
            isHighGround = true;
        }
        if (mOnHighGround && !isHighGround) {
            aOffsetY += GameConstants.HIGH_GROUND_HEIGHT;
        } else if (!mOnHighGround && isHighGround) {
            aOffsetY -= GameConstants.HIGH_GROUND_HEIGHT;
        }

        if (mBoard.stageIsNight()) {
            aCelCol = 1;
        }

        switch (mProjectileType) {
            case ProjectileType.PROJECTILE_PEA:
            case ProjectileType.PROJECTILE_ZOMBIE_PEA:
                aOffsetX += 3.0f;
                break;
            case ProjectileType.PROJECTILE_SNOWPEA:
                aOffsetX += -1.0f;
                aScale = 1.3f;
                break;
            case ProjectileType.PROJECTILE_STAR:
                aOffsetX += 7.0f;
                break;
            case ProjectileType.PROJECTILE_CABBAGE:
            case ProjectileType.PROJECTILE_KERNEL:
            case ProjectileType.PROJECTILE_BUTTER:
            case ProjectileType.PROJECTILE_MELON:
            case ProjectileType.PROJECTILE_WINTERMELON:
                aOffsetX += 3.0f;
                aOffsetY += 10.0f;
                aScale = 1.6f;
                break;
            case ProjectileType.PROJECTILE_PUFF:
                return;
            case ProjectileType.PROJECTILE_COBBIG:
                aScale = 1.0f;
                aStretch = 3.0f;
                aOffsetX += 57.0f;
                break;
            case ProjectileType.PROJECTILE_FIREBALL:
                aScale = 1.4f;
                break;
            default:
                break;
        }

        if (mMotionType == ProjectileMotion.MOTION_LOBBED) {
            float aHeight = Math.min(Math.max(-mPosZ, 0.0f), 200.0f);
            aScale *= 200.0f / (aHeight + 200.0f);
        }

        PvzpCommon.pvzpDrawImageCelScaledF(g, mApp.getImage(Resources.IMAGE_PEA_SHADOWS_ID), aOffsetX,
            (mShadowY - mPosY + aOffsetY), aCelCol, 0, aScale * aStretch, aScale);
    }

    /** Ported from src/Lawn/Projectile.cpp:1152-1165. */
    public void die() {
        mDead = true;

        if (mProjectileType == ProjectileType.PROJECTILE_PUFF
            || mProjectileType == ProjectileType.PROJECTILE_SNOWPEA) {
            Attachment.attachmentCrossFade(mAttachmentID, "FadeOut");
            Attachment.attachmentDetach(mAttachmentID);
        } else {
            Attachment.attachmentDie(mAttachmentID);
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:1167-1195. */
    public Rect getProjectileRect() {
        if (mProjectileType == ProjectileType.PROJECTILE_PEA
            || mProjectileType == ProjectileType.PROJECTILE_SNOWPEA
            || mProjectileType == ProjectileType.PROJECTILE_ZOMBIE_PEA) {
            return new Rect(mX - 15, mY, mWidth + 15, mHeight);
        } else if (mProjectileType == ProjectileType.PROJECTILE_COBBIG) {
            return new Rect(mX + mWidth / 2 - 115, mY + mHeight / 2 - 115, 230, 230);
        } else if (mProjectileType == ProjectileType.PROJECTILE_MELON
            || mProjectileType == ProjectileType.PROJECTILE_WINTERMELON) {
            return new Rect(mX + 20, mY, 60, mHeight);
        } else if (mProjectileType == ProjectileType.PROJECTILE_FIREBALL) {
            return new Rect(mX, mY, mWidth - 10, mHeight);
        } else if (mProjectileType == ProjectileType.PROJECTILE_SPIKE) {
            return new Rect(mX - 25, mY, mWidth + 25, mHeight);
        } else {
            return new Rect(mX, mY, mWidth, mHeight);
        }
    }

    /** Ported from src/Lawn/Projectile.cpp:1197-1219. */
    public void convertToFireball(int theGridX) {
        if (mHitTorchwoodGridX == theGridX) {
            return;
        }

        mProjectileType = ProjectileType.PROJECTILE_FIREBALL;
        mHitTorchwoodGridX = theGridX;
        mApp.playFoley(FoleyType.FOLEY_FIREPEA);

        float aOffsetX = -25.0f;
        float aOffsetY = -25.0f;
        Reanimation aFirePeaReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_FIRE_PEA);
        if (mMotionType == ProjectileMotion.MOTION_BACKWARDS) {
            aFirePeaReanim.overrideScale(-1.0f, 1.0f);
            aOffsetX += 80.0f;
        }

        aFirePeaReanim.setPosition(mPosX + aOffsetX, mPosY + aOffsetY);
        aFirePeaReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
        aFirePeaReanim.mAnimRate = PvzpCommon.randRangeFloat(50.0f, 80.0f);
        Attachment.attachReanim(mAttachmentID, aFirePeaReanim, aOffsetX, aOffsetY);
    }

    /** Ported from src/Lawn/Projectile.cpp:1221-1230. */
    public void convertToPea(int theGridX) {
        if (mHitTorchwoodGridX == theGridX) {
            return;
        }

        Attachment.attachmentDie(mAttachmentID);
        mProjectileType = ProjectileType.PROJECTILE_PEA;
        mHitTorchwoodGridX = theGridX;
        mApp.playFoley(FoleyType.FOLEY_THROW);
    }

    /** Ported from src/Lawn/Projectile.cpp:1232-1237. */
    public ProjectileDefinition getProjectileDef() {
        return gProjectileDefinition[mProjectileType];
    }
}
