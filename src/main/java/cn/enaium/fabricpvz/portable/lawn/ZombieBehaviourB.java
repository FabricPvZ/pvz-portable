/*
 * Ported from src/Lawn/Zombie.cpp (helpers): dancers, rising-from-grave, pool/high-ground,
 * falling, zamboni/yeti/ladder climbing, chimney and house entry.
 *
 * Static helpers called by Zombie.java delegators. See local://zombie-port-contract.md for the
 * owner map and the peer API.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;

public final class ZombieBehaviourB {

    private ZombieBehaviourB() {
    }

    /** Ported from src/Lawn/Zombie.cpp:2810-2845 (Zombie::SummonBackupDancer). */
    public static int summonBackupDancer(Zombie z, int theRow, int thePosX) {
        if (!z.mBoard.rowCanHaveZombieType(theRow, ZombieType.ZOMBIE_BACKUP_DANCER)) {
            return ZombieID.ZOMBIEID_NULL;
        }

        Zombie aZombie = z.mBoard.addZombie(ZombieType.ZOMBIE_BACKUP_DANCER, z.mFromWave);
        if (aZombie == null) {
            return ZombieID.ZOMBIEID_NULL;
        }

        aZombie.mPosX = thePosX;
        aZombie.mPosY = z.getPosYBasedOnRow(theRow);
        aZombie.setRow(theRow);
        aZombie.mX = (int) aZombie.mPosX;
        aZombie.mY = (int) aZombie.mPosY;

        aZombie.mAltitude = Zombie.ZOMBIE_BACKUP_DANCER_RISE_HEIGHT;
        aZombie.mZombiePhase = ZombiePhase.PHASE_DANCER_RISING;
        aZombie.mPhaseCounter = 150;
        aZombie.mRelatedZombieID = z.mBoard.zombieGetID(z);

        aZombie.setAnimRate(0.0f);
        aZombie.mMindControlled = z.mMindControlled;

        int aParticleX = (int) aZombie.mPosX + 60;
        int aParticleY = (int) aZombie.mPosY + 110;
        if (isOnHighGround(aZombie)) {
            aParticleY -= GameConstants.HIGH_GROUND_HEIGHT;
        }
        int aRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, theRow, 0);
        z.mApp.addPvzpParticle(aParticleX, aParticleY, aRenderOrder, ParticleEffect.PARTICLE_DANCER_RISE);
        z.mApp.playFoley(FoleyType.FOLEY_GRAVESTONE_RUMBLE);

        return z.mBoard.zombieGetID(aZombie);
    }

    /** Ported from src/Lawn/Zombie.cpp:2846-2869 (Zombie::SummonBackupDancers). */
    public static void summonBackupDancers(Zombie z) {
        if (!z.mHasHead) {
            return;
        }

        for (int i = 0; i < Zombie.NUM_BACKUP_DANCERS; i++) {
            if (z.mBoard.zombieTryToGet(z.mFollowerZombieID[i]) == null) {
                int aRow = 0;
                int aPosX = 0;
                switch (i) {
                    case 0 -> {
                        aRow = z.mRow - 1;
                        aPosX = (int) z.mPosX;
                    }
                    case 1 -> {
                        aRow = z.mRow + 1;
                        aPosX = (int) z.mPosX;
                    }
                    case 2 -> {
                        aRow = z.mRow;
                        aPosX = (int) (z.mPosX - 100);
                    }
                    case 3 -> {
                        aRow = z.mRow;
                        aPosX = (int) (z.mPosX + 100);
                    }
                    default -> throw new IllegalStateException("Unexpected backup dancer index " + i);
                }

                z.mFollowerZombieID[i] = summonBackupDancer(z, aRow, aPosX);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:2870-2892 (Zombie::NeedsMoreBackupDancers). */
    public static boolean needsMoreBackupDancers(Zombie z) {
        for (int i = 0; i < Zombie.NUM_BACKUP_DANCERS; i++) {
            if (z.mBoard.zombieTryToGet(z.mFollowerZombieID[i]) == null) {
                if (i == 0 && !z.mBoard.rowCanHaveZombieType(z.mRow - 1, ZombieType.ZOMBIE_BACKUP_DANCER)) {
                    continue;
                }

                if (i == 1 && !z.mBoard.rowCanHaveZombieType(z.mRow + 1, ZombieType.ZOMBIE_BACKUP_DANCER)) {
                    continue;
                }

                return true;
            }
        }

        return false;
    }

    /** Ported from src/Lawn/Zombie.cpp:2907-2951 (Zombie::UpdateZombieBackupDancer). */
    public static void updateZombieBackupDancer(Zombie z) {
        if (z.mIsEating) {
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING) {
            z.mAltitude = PvzpCommon.pvzpAnimateCurve(150, 0, z.mPhaseCounter,
                    Zombie.ZOMBIE_BACKUP_DANCER_RISE_HEIGHT, 0, PvzpCurves.CURVE_LINEAR);

            if (z.mPhaseCounter != 0) {
                return;
            }

            if (isOnHighGround(z)) {
                z.mAltitude = GameConstants.HIGH_GROUND_HEIGHT;
            }
        }

        int aDancerPhase = getDancerPhase(z);
        if (aDancerPhase != z.mZombiePhase) {
            switch (aDancerPhase) {
                case ZombiePhase.PHASE_DANCER_DANCING_LEFT -> {
                    z.mZombiePhase = aDancerPhase;
                    z.playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 10, 0.0f);
                }
                case ZombiePhase.PHASE_DANCER_WALK_TO_RAISE -> {
                    z.mZombiePhase = aDancerPhase;
                    z.playZombieReanim("anim_armraise", ReanimLoopType.REANIM_LOOP, 10, 18.0f);
                    z.mApp.reanimationTryToGet(z.mBodyReanimID).mAnimTime = 0.6f;
                }
                case ZombiePhase.PHASE_DANCER_RAISE_LEFT_1, ZombiePhase.PHASE_DANCER_RAISE_LEFT_2 -> {
                    z.mZombiePhase = aDancerPhase;
                    z.playZombieReanim("anim_armraise", ReanimLoopType.REANIM_LOOP, 10, 18.0f);
                }
                default -> {
                }
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:2952-3041 (Zombie::UpdateZombieDancer). */
    public static void updateZombieDancer(Zombie z) {
        if (z.mIsEating) {
            return;
        }

        if (z.mSummonCounter > 0) {
            z.mSummonCounter--;
            if (z.mSummonCounter == 0) {
                if (getDancerFrame(z) == 12 && z.mHasHead && z.mPosX < 700.0f) {
                    z.mZombiePhase = ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_WITH_LIGHT;
                    z.playZombieReanim("anim_point", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
                } else {
                    z.mSummonCounter = 1;
                }
            }
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_DANCING_IN) {
            if (z.mHasHead && z.mPhaseCounter == 0) {
                z.mZombiePhase = ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS;
                z.playZombieReanim("anim_point", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
                z.pickRandomSpeed();
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS
                || z.mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_WITH_LIGHT) {
            Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS
                        && z.mBoard.countZombiesOnScreen() <= 15) {
                    z.mApp.playFoley(FoleyType.FOLEY_DANCER);
                }

                summonBackupDancers(z);
                z.mZombiePhase = ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_HOLD;
                z.mPhaseCounter = 200;
            }
        } else {
            if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_HOLD) {
                if (z.mPhaseCounter != 0) {
                    return;
                }

                z.mZombiePhase = ZombiePhase.PHASE_DANCER_DANCING_LEFT;
                z.playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 20, 0.0f);
            }

            int aDancerPhase = getDancerPhase(z);
            if (aDancerPhase != z.mZombiePhase) {
                switch (aDancerPhase) {
                    case ZombiePhase.PHASE_DANCER_DANCING_LEFT -> {
                        z.mZombiePhase = aDancerPhase;
                        z.playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 10, 0.0f);
                    }
                    case ZombiePhase.PHASE_DANCER_WALK_TO_RAISE -> {
                        z.mZombiePhase = aDancerPhase;
                        z.playZombieReanim("anim_armraise", ReanimLoopType.REANIM_LOOP, 10, 18.0f);
                        z.mApp.reanimationTryToGet(z.mBodyReanimID).mAnimTime = 0.6f;
                    }
                    case ZombiePhase.PHASE_DANCER_RAISE_LEFT_1, ZombiePhase.PHASE_DANCER_RAISE_LEFT_2 -> {
                        z.mZombiePhase = aDancerPhase;
                        z.playZombieReanim("anim_armraise", ReanimLoopType.REANIM_LOOP, 10, 18.0f);
                    }
                    default -> {
                    }
                }
            }

            if (z.mHasHead && z.mSummonCounter == 0 && needsMoreBackupDancers(z)) {
                z.mSummonCounter = 100;
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3042-3071 (Zombie::UpdateZombieRiseFromGrave). */
    public static void updateZombieRiseFromGrave(Zombie z) {
        if (z.mInPool) {
            z.mAltitude = PvzpCommon.pvzpAnimateCurve(50, 0, z.mPhaseCounter, -150, -40,
                    PvzpCurves.CURVE_LINEAR) * z.mScaleZombie;
        } else {
            z.mAltitude = PvzpCommon.pvzpAnimateCurve(50, 0, z.mPhaseCounter, -200, 0,
                    PvzpCurves.CURVE_LINEAR);
        }

        if (z.mPhaseCounter == 0) {
            z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;

            if (isOnHighGround(z)) {
                z.mAltitude = GameConstants.HIGH_GROUND_HEIGHT;
            }

            if (z.mInPool) {
                z.reanimIgnoreClipRect("Zombie_duckytube", true);
                z.reanimIgnoreClipRect("Zombie_whitewater", true);
                z.reanimIgnoreClipRect("Zombie_outerarm_hand", true);
                z.reanimIgnoreClipRect("Zombie_innerarm3", true);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:3079-3131 (Zombie::ZombiquariumFindClosestBrain).
     */
    public static boolean zombiquariumFindClosestBrain(Zombie z) {
        if (z.mBoard.hasLevelAwardDropped() || z.mBodyHealth > 150) {
            return false;
        }

        GridItem aBrainClosest = null;
        float aDistanceClosest = 0.0f;
        for (GridItem aGridItem : z.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_BRAIN && aGridItem.mGridItemCounter >= 15) {
                float aDistance = PvzpCommon.distance2D(aGridItem.mPosX + 15.0f, aGridItem.mPosY + 15.0f,
                        z.mPosX + 50.0f, z.mPosY + 40.0f);
                if (aBrainClosest == null || aDistance < aDistanceClosest) {
                    aDistanceClosest = aDistance;
                    aBrainClosest = aGridItem;
                }
            }
        }

        if (aBrainClosest != null) {
            if (aDistanceClosest < 50.0f) {
                aBrainClosest.gridItemDie();
                z.mApp.playFoley(FoleyType.FOLEY_SLURP);

                z.mBodyHealth += 200;
                z.mBodyHealth = Math.min(z.mBodyHealth, z.mBodyMaxHealth);

                z.playZombieReanim("anim_aquarium_bite", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 24.0f);
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_BITE;
                z.mPhaseCounter = 200;
                return false;
            }

            float aRangeY = aBrainClosest.mPosY + 15.0f - (z.mPosY + 40.0f);
            float aRangeX = aBrainClosest.mPosX + 15.0f - (z.mPosX + 50.0f);
            z.mVelZ = (float) Math.atan2(aRangeY, aRangeX);
            if (z.mVelZ < 0.0f) {
                z.mVelZ += (float) (Math.PI * 2);
            }

            z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_ACCEL;
            return true;
        }

        return false;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:3132-3261 (Zombie::UpdateZombiquarium).
     */
    public static void updateZombiquarium(Zombie z) {
        if (z.isDeadOrDying()) {
            return;
        }

        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIQUARIUM_BITE) {
            if (aBodyReanim.mLoopCount > 0) {
                float aAnimRate = PvzpCommon.randRangeFloat(8.0f, 10.0f);
                z.playZombieReanim("anim_aquarium_swim", ReanimLoopType.REANIM_LOOP, 20, aAnimRate);

                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_DRIFT;
                z.mPhaseCounter = 100;
            }
        } else if (!zombiquariumFindClosestBrain(z) && z.mPhaseCounter == 0) {
            int aPhaseHit = Common.rand(7);
            if (aPhaseHit <= 4) {
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_ACCEL;
                z.mVelZ = PvzpCommon.randRangeFloat(0.0f, (float) (Math.PI * 2));
                z.mPhaseCounter = PvzpCommon.randRangeInt(300, 1000);
                aBodyReanim.mAnimRate = PvzpCommon.randRangeFloat(15.0f, 20.0f);
            }
            // upstream's PHASE_ZOMBIQUARIUM_DRIFT branch for aPhaseHit == 4 is commented out
            else if (aPhaseHit == 5) {
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_BACK_AND_FORTH;
                z.mVelZ = 0.0f;
                z.mPhaseCounter = PvzpCommon.randRangeInt(300, 1000);
                aBodyReanim.mAnimRate = PvzpCommon.randRangeFloat(15.0f, 20.0f);
            } else {
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_BACK_AND_FORTH;
                z.mVelZ = (float) Math.PI;
                z.mPhaseCounter = PvzpCommon.randRangeInt(300, 1000);
                aBodyReanim.mAnimRate = PvzpCommon.randRangeFloat(15.0f, 20.0f);
            }
        }

        float aVelX = (float) Math.cos(z.mVelZ);
        float aVelY = (float) Math.sin(z.mVelZ);
        boolean aIsOutOfBounds = false;
        if (z.mPosX < 0.0f && aVelX < 0.0f) {
            aIsOutOfBounds = true;
        } else if (z.mPosX > 680.0f && aVelX > 0.0f) {
            aIsOutOfBounds = true;
        } else if (z.mPosY < 100.0f && aVelY < 0.0f) {
            aIsOutOfBounds = true;
        } else if (z.mPosY > 400.0f && aVelY > 0.0f) {
            aIsOutOfBounds = true;
        }

        float aMaxSpeed = 0.5f;
        if (aIsOutOfBounds) {
            aMaxSpeed = z.mVelX * 0.3f;
            z.mPhaseCounter = Math.min(100, z.mPhaseCounter);
        } else if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIQUARIUM_ACCEL) {
            aMaxSpeed = 0.5f;
        } else if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIQUARIUM_BACK_AND_FORTH) {
            if (z.mPosX < 200.0f && aVelX < 0.0f) {
                z.mVelZ = 0.0f;
            }

            if (z.mPosX > 550.0f && aVelX > 0.0f) {
                z.mVelZ = (float) Math.PI;
            }

            aMaxSpeed = 0.3f;
        } else if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIQUARIUM_DRIFT
                || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIQUARIUM_BITE) {
            aMaxSpeed = 0.05f;
        }

        z.mVelX = Math.min(aMaxSpeed, z.mVelX + 0.01f);
        aVelX *= z.mVelX;
        aVelY *= z.mVelX;
        z.mPosX += aVelX;
        z.mPosY += aVelY;

        if (!z.mBoard.hasLevelAwardDropped()) {
            if (z.mSummonCounter > 0) {
                z.mSummonCounter--;
                if (z.mSummonCounter == 0) {
                    z.mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
                    z.mBoard.addCoin(z.mX + 50, z.mY + 40, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
                    z.mSummonCounter = PvzpCommon.randRangeInt(1000, 1500);
                }
            }

            if (z.mZombieAge % 100 == 0) {
                z.takeDamage(10, 8);
                if (z.isDeadOrDying()) {
                    z.mApp.playSample(Resources.SOUND_ZOMBAQUARIUM_DIE_ID);
                }
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3262-3295 (Zombie::UpdateZombiePool). */
    public static void updateZombiePool(Zombie z) {
        if (z.mZombieHeight == ZombieHeight.HEIGHT_OUT_OF_POOL) {
            z.mAltitude++;
            if (z.mZombieType == ZombieType.ZOMBIE_SNORKEL) {
                z.mAltitude++;
            }

            if (z.mAltitude >= 0.0f) {
                z.mAltitude = 0.0f;
                z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
                z.mInPool = false;
            }
        } else if (z.mZombieHeight == ZombieHeight.HEIGHT_IN_TO_POOL) {
            z.mAltitude--;
            int aDepth = (int) (-40 * z.mScaleZombie);
            if (z.mAltitude <= aDepth) {
                z.mAltitude = aDepth;
                z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
                z.startWalkAnim(0);
            }
        } else if (z.mZombieHeight == ZombieHeight.HEIGHT_DRAGGED_UNDER) {
            z.mAltitude--;
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3296-3321 (Zombie::UpdateZombieHighGround). */
    public static void updateZombieHighGround(Zombie z) {
        if (z.mZombieType == ZombieType.ZOMBIE_POGO) {
            return;
        }

        if (z.mZombieHeight == ZombieHeight.HEIGHT_UP_TO_HIGH_GROUND) {
            z.mAltitude++;
            if (z.mAltitude >= GameConstants.HIGH_GROUND_HEIGHT) {
                z.mAltitude = GameConstants.HIGH_GROUND_HEIGHT;
                z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
            }
        } else if (z.mZombieHeight == ZombieHeight.HEIGHT_DOWN_OFF_HIGH_GROUND) {
            z.mAltitude--;
            if (z.mAltitude <= 0.0f) {
                z.mAltitude = 0.0f;
                z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
                z.mOnHighGround = false;
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3322-3341 (Zombie::UpdateZombieFalling). */
    public static void updateZombieFalling(Zombie z) {
        z.mAltitude--;
        if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT) {
            z.mAltitude--;
        }

        int aGroundHeight = 0;
        if (isOnHighGround(z)) {
            aGroundHeight = GameConstants.HIGH_GROUND_HEIGHT;
        }
        if (z.mAltitude <= aGroundHeight) {
            z.mAltitude = aGroundHeight;
            z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:4023-4053 (Zombie::UpdateZamboni). */
    public static void updateZamboni(Zombie z) {
        if (z.mPosX > 400.0f && !z.mFlatTires) {
            z.mVelX = PvzpCommon.pvzpAnimateCurveFloat(700, 300, (int) z.mPosX, 0.25f, 0.05f,
                    PvzpCurves.CURVE_LINEAR);
        } else if (z.mFlatTires && z.mVelX > 0.0005f) {
            z.mVelX -= 0.0005f;
        }

        int anIceX = (int) (z.mPosX + 118);
        if (z.mBoard.stageHasRoof()) {
            anIceX = Math.max(anIceX, 500);
        } else {
            anIceX = Math.max(anIceX, 25);
        }
        z.mBoard.mIceMinX[z.mRow] = Math.min(z.mBoard.mIceMinX[z.mRow], anIceX);
        if (anIceX < 800) {
            z.mBoard.mIceTimer[z.mRow] = 3000;
            if (z.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA) {
                z.mBoard.mIceTimer[z.mRow] = Integer.MAX_VALUE;
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:4054-4066 (Zombie::UpdateYeti). */
    public static void updateYeti(Zombie z) {
        if (z.mMindControlled || !z.mHasHead || z.isDeadOrDying()) {
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_NORMAL && z.mPhaseCounter == 0) {
            z.mZombiePhase = ZombiePhase.PHASE_YETI_RUNNING;
            z.mHasObject = false;
            z.pickRandomSpeed();
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:4067-4103 (Zombie::UpdateLadder). */
    public static void updateLadder(Zombie z) {
        if (z.mMindControlled || !z.mHasHead || z.isDeadOrDying()) {
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_LADDER_CARRYING
                && z.mZombieHeight == ZombieHeight.HEIGHT_ZOMBIE_NORMAL) {
            if (z.findPlantTarget(ZombieAttackType.ATTACKTYPE_LADDER) != null) {
                z.stopEating();
                z.mZombiePhase = ZombiePhase.PHASE_LADDER_PLACING;
                z.playZombieReanim("anim_placeladder", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 24.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_LADDER_PLACING) {
            Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                Plant aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_LADDER);
                if (aPlant != null) {
                    z.mBoard.addALadder(aPlant.mPlantCol, aPlant.mRow);
                    z.mApp.playSample(SoundType.SOUND_LADDER_ZOMBIE);
                    z.mZombieHeight = ZombieHeight.HEIGHT_UP_LADDER;
                    z.mUseLadderCol = aPlant.mPlantCol;
                    z.detachShield();
                } else {
                    z.mZombiePhase = ZombiePhase.PHASE_LADDER_CARRYING;
                    z.startWalkAnim(0);
                }
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:4397-4425 (Zombie::UpdateClimbingLadder). */
    public static void updateClimbingLadder(Zombie z) {
        float aDistOffGround = z.mAltitude;
        if (z.mOnHighGround) {
            aDistOffGround -= GameConstants.HIGH_GROUND_HEIGHT;
        }
        int aLadderOriginX = z.mBoard.pixelToGridXKeepOnBoard(
                (int) (z.mX + 5 + aDistOffGround * 0.5f), z.mY);
        if (z.mBoard.getLadderAt(aLadderOriginX, z.mRow) == null) {
            z.mZombieHeight = ZombieHeight.HEIGHT_FALLING;
            return;
        }

        z.mAltitude += 0.8f;
        if (z.mVelX < 0.5f) {
            z.mPosX -= 0.5f;
        }

        float aTargetHeight = 90.0f;
        if (z.mOnHighGround) {
            aTargetHeight += GameConstants.HIGH_GROUND_HEIGHT;
        }
        if (z.mAltitude >= aTargetHeight) {
            z.mZombieHeight = ZombieHeight.HEIGHT_FALLING;
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6160-6186 (Zombie::GetDancerFrame). */
    public static int getDancerFrame(Zombie z) {
        if (z.mFromWave == Zombie.ZOMBIE_WAVE_UI || z.isImmobilizied()) {
            return 0;
        }

        int aFrameLength = 20;
        int aFramesCount = 23;
        if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_DANCING_IN) {
            aFramesCount = 11;
            aFrameLength = 10;
        }

        return (int) (z.mApp.mAppCounter % (aFrameLength * aFramesCount) / aFrameLength);
    }

    /** Ported from src/Lawn/Zombie.cpp:6187-6197 (Zombie::GetDancerPhase). */
    public static int getDancerPhase(Zombie z) {
        int aFrame = getDancerFrame(z);

        return aFrame <= 11 ? ZombiePhase.PHASE_DANCER_DANCING_LEFT
                : aFrame <= 12 ? ZombiePhase.PHASE_DANCER_WALK_TO_RAISE
                : aFrame <= 18 ? ZombiePhase.PHASE_DANCER_RAISE_LEFT_1
                : ZombiePhase.PHASE_DANCER_RAISE_LEFT_2;
    }

    /** Ported from src/Lawn/Zombie.cpp:8184-8265 (Zombie::RiseFromGrave). */
    public static void riseFromGrave(Zombie z, int theCol, int theRow) {
        z.mPosX = z.mBoard.gridToPixelX(theCol, z.mRow) - 25;
        z.mPosY = z.getPosYBasedOnRow(theRow);
        z.setRow(theRow);
        z.mX = (int) z.mPosX;
        z.mY = (int) z.mPosY;
        z.mAltitude = Zombie.CLIP_HEIGHT_OFF;
        z.mZombiePhase = ZombiePhase.PHASE_RISING_FROM_GRAVE;
        z.mPhaseCounter = 150;

        if (z.mBoard.stageHasPool()) {
            z.mAltitude = -150.0f;
            z.mInPool = true;
            z.mPhaseCounter = 50;
            z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;

            z.startWalkAnim(0);
            z.reanimIgnoreClipRect("Zombie_duckytube", false);
            z.reanimIgnoreClipRect("Zombie_whitewater", false);
            z.reanimIgnoreClipRect("Zombie_outerarm_hand", false);
            z.reanimIgnoreClipRect("Zombie_innerarm3", false);

            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(0.0f, 0.0f, 0,
                    ParticleEffect.PARTICLE_ZOMBIE_SEAWEED);
            z.overrideParticleScale(aParticle);

            if (z.mZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE && aParticle != null) {
                aBodyReanim.attachParticleToTrack("anim_cone", aParticle, 37.0f, 20.0f);
            } else if (z.mZombieType == ZombieType.ZOMBIE_PAIL && aParticle != null) {
                aBodyReanim.attachParticleToTrack("anim_bucket", aParticle, 37.0f, 20.0f);
            } else if (aParticle != null) {
                aBodyReanim.attachParticleToTrack("anim_head1", aParticle, 30.0f, 20.0f);
            }

            PvzpParticleSystem aParticle2 = z.mApp.addPvzpParticle(0.0f, 0.0f, 0,
                    ParticleEffect.PARTICLE_ZOMBIE_SEAWEED);
            if (aParticle2 != null) {
                z.overrideParticleScale(aParticle2);
                aBodyReanim.attachParticleToTrack("Zombie_outerarm_upper", aParticle2, 5.0f, 5.0f);
            }

            PvzpParticleSystem aParticle3 = z.mApp.addPvzpParticle(0.0f, 0.0f, 0,
                    ParticleEffect.PARTICLE_ZOMBIE_SEAWEED);
            if (aParticle3 != null) {
                z.overrideParticleScale(aParticle3);
                aBodyReanim.attachParticleToTrack("Zombie_duckytube", aParticle3, 77.0f, 20.0f);
            }

            poolSplash(z, false);
        } else {
            int aParticleX = (int) (z.mPosX + 60);
            int aParticleY = (int) (z.mPosY + 110);
            if (isOnHighGround(z)) {
                aParticleY -= GameConstants.HIGH_GROUND_HEIGHT;
            }

            int aRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, theRow, 0);
            if (z.mApp.isWhackAZombieLevel()) {
                z.mApp.playFoley(FoleyType.FOLEY_DIRT_RISE);
                z.mApp.addPvzpParticle(aParticleX, aParticleY, aRenderOrder,
                        ParticleEffect.PARTICLE_WHACK_A_ZOMBIE_RISE);
            } else {
                z.mApp.playFoley(FoleyType.FOLEY_GRAVESTONE_RUMBLE);
                z.mApp.addPvzpParticle(aParticleX, aParticleY, aRenderOrder,
                        ParticleEffect.PARTICLE_ZOMBIE_RISE);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:4219-4242 (Zombie::IsStandingOnSpikeweed). */
    public static Plant isStandingOnSpikeweed(Zombie z) {
        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI || z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
            return null;
        }

        Rect aZombieRect = z.getZombieRect();

        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow == z.mRow && aPlant.isSpiky() && !aPlant.notOnGround()
                    && (!z.mOnHighGround || aPlant.isOnHighGround())) {
                Rect aPlantAttackRect = aPlant.getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);
                if (Board.getRectOverlap(aPlantAttackRect, aZombieRect) > 0) {
                    return aPlant;
                }
            }
        }

        return null;
    }

    /** Ported from src/Lawn/Zombie.cpp:9626-9632 (Zombie::UpdateZombieChimney). */
    public static void updateZombieChimney(Zombie z) {
        if (z.mBoard.mBackground == BackgroundType.BACKGROUND_5_ROOF
                || z.mBoard.mBackground == BackgroundType.BACKGROUND_6_BOSS) {
            z.mAltitude = PvzpCommon.pvzpAnimateCurve(4000, 5000, z.mBoard.mCutScene.mCutsceneTime,
                    200, 0, PvzpCurves.CURVE_EASE_IN);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:9634-9704 (Zombie::WalkIntoHouse). */
    public static void walkIntoHouse(Zombie z) {
        Attachment.attachmentDetachCrossFadeParticleType(z.mAttachmentID,
                ParticleEffect.PARTICLE_ZAMBONI_SMOKE, null);
        z.mFromWave = Zombie.ZOMBIE_WAVE_WINNER;
        z.reanimReenableClipping();

        if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT) {
            z.mZombiePhase = ZombiePhase.PHASE_POLEVAULTER_POST_VAULT;
            z.startWalkAnim(0);
        }

        if (z.mBoard.mBackground == BackgroundType.BACKGROUND_1_DAY
                || z.mBoard.mBackground == BackgroundType.BACKGROUND_2_NIGHT
                || z.mBoard.mBackground == BackgroundType.BACKGROUND_3_POOL
                || z.mBoard.mBackground == BackgroundType.BACKGROUND_4_FOG) {
            z.mPosY = 290.0f;
            z.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ZOMBIE, 2, 0);

            if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                    || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                z.mPosY += 30.0f;
            } else if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT) {
                z.mPosX += 35.0f;
            } else if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                z.mPosY += 15.0f;
            }

            if (z.mBoard.stageHasPool()) {
                if (z.mZombieType == ZombieType.ZOMBIE_FOOTBALL) {
                    z.mPosX -= 10.0f;
                } else {
                    z.mPosX -= 80.0f;
                }
            }
        } else if (z.mBoard.mBackground == BackgroundType.BACKGROUND_5_ROOF
                || z.mBoard.mBackground == BackgroundType.BACKGROUND_6_BOSS) {
            z.mPosX = -180.0f;
            z.mPosY = 250.0f;
            z.mZombieHeight = ZombieHeight.HEIGHT_IN_TO_CHIMNEY;
            z.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, 0, 2);

            if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                    || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                z.mPosY += 5.0f;
            } else if (z.mZombieType == ZombieType.ZOMBIE_FOOTBALL) {
                z.mPosX -= 14.0f;
            } else if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                z.mPosX -= 28.0f;
            }

            Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
            if (aBodyReanim != null && aBodyReanim.trackExists("anim_idle")
                    && z.mZombieType != ZombieType.ZOMBIE_POLEVAULTER) {
                z.playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6897-6919 (Zombie::PoolSplash). */
    public static void poolSplash(Zombie z, boolean theInToPoolSound) {
        float aOffsetX = 23.0f;
        float aOffsetY = 78.0f;
        if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL) {
            aOffsetX -= 37.0f;
            aOffsetY -= -8.0f;
        }

        z.mApp.addReanimation(z.mX + aOffsetX, z.mY + aOffsetY, z.mRenderOrder + 1,
                ReanimationType.REANIM_SPLASH).overrideScale(0.8f, 0.8f);
        z.mApp.addPvzpParticle(z.mX + aOffsetX + 37.0f, z.mY + aOffsetY + 42.0f,
                z.mRenderOrder + 1, ParticleEffect.PARTICLE_PLANTING_POOL);

        if (theInToPoolSound) {
            z.mApp.playFoley(FoleyType.FOLEY_ZOMBIESPLASH);
        } else {
            z.mApp.playFoley(FoleyType.FOLEY_PLANT_WATER);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6920-6961 (Zombie::CheckForPool). */
    public static void checkForPool(Zombie z) {
        if (!Zombie.zombieTypeCanGoInPool(z.mZombieType) || z.isFlying()) {
            return;
        }
        if (z.mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER || z.mZombieType == ZombieType.ZOMBIE_SNORKEL) {
            return;
        }
        if (z.mZombieHeight == ZombieHeight.HEIGHT_IN_TO_POOL || z.mZombieHeight == ZombieHeight.HEIGHT_OUT_OF_POOL) {
            return;
        }

        boolean aIsPoolSquare =
                z.mBoard.isPoolSquare(z.mBoard.pixelToGridX(z.mX + 75, z.mY), z.mRow)
                && z.mBoard.isPoolSquare(z.mBoard.pixelToGridX(z.mX + 45, z.mY), z.mRow)
                && z.mX < 680;

        if (!z.mInPool && aIsPoolSquare) {
            if (z.mBoard.mIceTrapCounter > 0) {
                z.mIceTrapCounter = z.mBoard.mIceTrapCounter;
                z.applyChill(true);
            } else {
                z.mZombieHeight = ZombieHeight.HEIGHT_IN_TO_POOL;
                z.mInPool = true;
                poolSplash(z, true);
            }
        } else if (z.mInPool && !aIsPoolSquare) {
            z.mZombieHeight = ZombieHeight.HEIGHT_OUT_OF_POOL;
            z.startWalkAnim(0);
            poolSplash(z, false);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6962-6966 (Zombie::IsOnHighGround). */
    public static boolean isOnHighGround(Zombie z) {
        return z.isOnBoard() && z.mBoard.mGridSquareType[
                z.mBoard.pixelToGridXKeepOnBoard(z.mX + 75, z.mY)][z.mRow] == GridSquareType.GRIDSQUARE_HIGH_GROUND;
    }

    /** Ported from src/Lawn/Zombie.cpp:6967-6983 (Zombie::CheckForHighGround). */
    public static void checkForHighGround(Zombie z) {
        if (z.mZombieHeight != ZombieHeight.HEIGHT_ZOMBIE_NORMAL || z.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
            return;
        }

        boolean aIsHighGround = isOnHighGround(z);
        if (!z.mOnHighGround && aIsHighGround) {
            z.mZombieHeight = ZombieHeight.HEIGHT_UP_TO_HIGH_GROUND;
            z.mOnHighGround = true;
        } else if (z.mOnHighGround && !aIsHighGround) {
            z.mZombieHeight = ZombieHeight.HEIGHT_DOWN_OFF_HIGH_GROUND;
        }
    }
}
