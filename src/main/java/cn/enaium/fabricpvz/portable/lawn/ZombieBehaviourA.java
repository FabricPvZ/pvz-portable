/*
 * Ported from src/Lawn/Zombie.cpp:1064-2809 (the ZombieBehaviourA slice).
 *
 * Per-type zombie state machines: bungee, pogo, catapult, flyer, newspaper, pole vaulter,
 * dolphin rider, snorkel, jack-in-the-box, gargantuar, imp, the peashooter/gatling/squash heads,
 * bobsled and digger.
 *
 * All methods are static helpers taking the owning {@link Zombie} as first parameter (the
 * delegation pattern documented in local://zombie-port-contract.md).  Upstream builds with
 * DO_FIX_BUGS enabled, so the DO_FIX_BUGS branches are the ones ported here.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Zombie behaviour state machines (bungee/pogo/catapult/flyer/newspaper/polevaulter/dolphin/
 * snorkel/jack-in-the-box/gargantuar/imp/heads/bobsled/digger).
 */
public final class ZombieBehaviourA {

    private ZombieBehaviourA() {
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1064-1084 (Zombie::CountBungeesTargetingSunFlowers).
     */
    public static int countBungeesTargetingSunFlowers(Zombie z) {
        int aCount = 0;

        for (Zombie aZombie : z.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying() && aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE && aZombie.mTargetCol != -1) {
                Plant aPlant = z.mBoard.getTopPlantAt(aZombie.mTargetCol, aZombie.mRow, PlantPriority.TOPPLANT_BUNGEE_ORDER);
                if (aPlant != null && aPlant.makesSun()) {
                    aCount++;
                }
            }
        }

        return aCount;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1085-1147 (Zombie::PickBungeeZombieTarget).
     */
    public static void pickBungeeZombieTarget(Zombie z, int theColumn) {
        boolean aAllowSunFlowerTarget = true;
        if (countBungeesTargetingSunFlowers(z) == z.mBoard.countSunFlowers() - 1) {
            aAllowSunFlowerTarget = false;
        }

        PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[Board.MAX_GRID_SIZE_X * Board.MAX_GRID_SIZE_Y];
        int aPickCount = 0;

        for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
            if (theColumn == -1 || theColumn == x) {
                for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                    int aWeight = 1;
                    if (z.mBoard.getGraveStoneAt(x, y) != null || z.mBoard.mGridSquareType[x][y] == GridSquareType.GRIDSQUARE_DIRT) {
                        continue;
                    }

                    Plant aPlant = z.mBoard.getTopPlantAt(x, y, PlantPriority.TOPPLANT_BUNGEE_ORDER);
                    if (aPlant != null) {
                        if (!aAllowSunFlowerTarget && aPlant.makesSun()) {
                            continue;
                        }

                        if (aPlant.mSeedType == SeedType.SEED_GRAVEBUSTER || aPlant.mSeedType == SeedType.SEED_COBCANNON) {
                            continue;
                        }

                        aWeight = 10000;
                    }

                    if (!z.mBoard.bungeeIsTargetingCell(x, y)) {
                        aPicks[aPickCount] = new PvzpWeightedGridArray();
                        aPicks[aPickCount].mX = x;
                        aPicks[aPickCount].mY = y;
                        aPicks[aPickCount].mWeight = aWeight;
                        aPickCount++;
                    }
                }
            }
        }

        if (aPickCount == 0) {
            z.dieNoLoot();
            return;
        }

        PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPickCount);
        z.mTargetCol = aGrid.mX;
        z.setRow(aGrid.mY);
        z.mPosX = z.mBoard.gridToPixelX(z.mTargetCol, z.mRow);
        z.mPosY = z.getPosYBasedOnRow(z.mRow);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1148-1164 (Zombie::BungeeDropZombie).
     */
    public static void bungeeDropZombie(Zombie z, Zombie theDroppedZombie, int theGridX, int theGridY) {
        z.mTargetCol = theGridX;
        z.setRow(theGridY);
        z.mPosX = z.mBoard.gridToPixelX(z.mTargetCol, z.mRow);
        z.mPosY = z.getPosYBasedOnRow(z.mRow);
        z.playZombieReanim("anim_raise", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 36.0f);
        z.mRelatedZombieID = z.mBoard.zombieGetID(theDroppedZombie);

        theDroppedZombie.mPosX = z.mPosX - 15.0f;
        theDroppedZombie.setRow(theGridY);
        theDroppedZombie.mPosY = z.getPosYBasedOnRow(theGridY);
        theDroppedZombie.mZombieHeight = ZombieHeight.HEIGHT_GETTING_BUNGEE_DROPPED;
        theDroppedZombie.playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
        theDroppedZombie.mRenderOrder = z.mRenderOrder + 1;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1229-1246 (Zombie::BungeeStealTarget).
     */
    public static void bungeeStealTarget(Zombie z) {
        z.playZombieReanim("anim_grab", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);

        Plant aPlant = z.mBoard.getTopPlantAt(z.mTargetCol, z.mRow, PlantPriority.TOPPLANT_BUNGEE_ORDER);
        if (aPlant != null && !aPlant.notOnGround()) {
            // PVZP_ASSERT(aPlant->mSeedType != SeedType::SEED_GRAVEBUSTER);

            if (aPlant.mSeedType != SeedType.SEED_COBCANNON && aPlant.mSeedType != SeedType.SEED_GRAVEBUSTER) {
                z.mTargetPlantID = z.mBoard.mPlants.dataArrayGetID(aPlant);
                aPlant.mOnBungeeState = PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE;
                z.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PROJECTILE, z.mRow, 0);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1247-1286 (Zombie::BungeeLiftTarget).
     */
    public static void bungeeLiftTarget(Zombie z) {
        z.playZombieReanim("anim_raise", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 36.0f);

        Plant aPlant = z.mBoard.mPlants.dataArrayTryToGet(z.mTargetPlantID);
        if (aPlant == null) {
            return;
        }

        // DO_FIX_BUGS: fixes the IZ bungee sun-farming bug
        for (Zombie aZombie : z.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE && aZombie != z && aZombie.mTargetPlantID == z.mTargetPlantID) {
                aZombie.mTargetPlantID = PlantID.PLANTID_NULL;
            }
        }

        aPlant.mOnBungeeState = PlantOnBungeeState.RISING_WITH_BUNGEE;
        z.mApp.playFoley(FoleyType.FOLEY_FLOOP);

        Reanimation aPlantReanim = z.mApp.reanimationTryToGet(aPlant.mBodyReanimID);
        if (aPlantReanim != null) {
            aPlantReanim.mAnimRate = 0.1f;
        }

        if (aPlant.mSeedType == SeedType.SEED_CATTAIL && z.mBoard.getTopPlantAt(z.mTargetCol, z.mRow, PlantPriority.TOPPLANT_ONLY_PUMPKIN) != null) {
            z.mBoard.newPlant(z.mTargetCol, z.mRow, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
        }

        if (z.mApp.isIZombieLevel()) {
            z.mBoard.mChallenge.iZombiePlantDropRemainingSun(aPlant);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1287-1335 (Zombie::BungeeLanding).
     */
    public static void bungeeLanding(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_DIVING && z.mAltitude < 1500.0f && !z.mApp.isFinalBossLevel()) {
            z.mApp.playFoley(FoleyType.FOLEY_BUNGEE_SCREAM);
            z.mZombiePhase = ZombiePhase.PHASE_BUNGEE_DIVING_SCREAMING;
        }

        if (z.mAltitude > 40.0f) {
            return;
        }

        Plant aPlant = z.mBoard.findUmbrellaPlant(z.mTargetCol, z.mRow);
        if (aPlant != null) {
            z.mApp.playSample(SoundType.SOUND_BOING);
            z.mApp.playFoley(FoleyType.FOLEY_UMBRELLA);

            aPlant.doSpecial();

            z.mZombiePhase = ZombiePhase.PHASE_BUNGEE_RISING;
            z.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 1);
            z.mHitUmbrella = true;

            return;
        }

        if (z.mAltitude > 0.0f) {
            return;
        }

        z.mAltitude = 0.0f;
        Zombie aZombie = z.mBoard.zombieTryToGet(z.mRelatedZombieID);
        if (aZombie != null) {
            aZombie.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
            aZombie.startWalkAnim(0);

            z.mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
            z.mZombiePhase = ZombiePhase.PHASE_BUNGEE_RISING;
            z.playZombieReanim("anim_raise", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 36.0f);
        } else {
            z.mZombiePhase = ZombiePhase.PHASE_BUNGEE_AT_BOTTOM;
            z.mPhaseCounter = 300;
            z.playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 5, 24.0f);
            z.mApp.reanimationGet(z.mBodyReanimID).mAnimTime = 0.5f;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1336-1396 (Zombie::UpdateZombieBungee).
     */
    public static void updateZombieBungee(Zombie z) {
        if (z.isDeadOrDying() || z.isImmobilizied()) {
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_DIVING || z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_DIVING_SCREAMING) {
            float aOldAltitude = z.mAltitude;
            z.mAltitude -= 8.0f;
            if (z.mAltitude <= Zombie.BUNGEE_ZOMBIE_HEIGHT - 404.0f && aOldAltitude > Zombie.BUNGEE_ZOMBIE_HEIGHT - 404.0f
                    && z.mRelatedZombieID == ZombieID.ZOMBIEID_NULL) {
                z.mApp.playFoley(FoleyType.FOLEY_GRASSSTEP); // sound of the target marker hitting the ground
            }

            bungeeLanding(z);
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_AT_BOTTOM) {
            if (z.mPhaseCounter <= 0) {
                bungeeStealTarget(z);
                z.mZombiePhase = ZombiePhase.PHASE_BUNGEE_GRABBING;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_GRABBING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                bungeeLiftTarget(z);
                z.mZombiePhase = ZombiePhase.PHASE_BUNGEE_RISING;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_HIT_OUCHY) {
            if (z.mPhaseCounter <= 0) {
                z.dieWithLoot();
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_RISING) {
            z.mAltitude += 8.0f;
            if (z.mAltitude >= 600.0f) {
                z.dieNoLoot();
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_CUTSCENE) {
            z.mAltitude = PvzpCommon.pvzpAnimateCurve(200, 0, z.mPhaseCounter, 40, 0, PvzpCurves.CURVE_SIN_WAVE);
            if (z.mPhaseCounter <= 0) {
                z.mPhaseCounter = 200;
            }
        }

        z.mX = (int) z.mPosX;
        z.mY = (int) z.mPosY;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1397-1424 (Zombie::PogoBreak).
     */
    public static void pogoBreak(Zombie z, int theDamageFlags) {
        if (!z.mHasObject) {
            return;
        }

        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
            float[] aPos = new float[2];
            z.getTrackPosition("Zombie_pogo_stick", aPos);
            PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1] + 30.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZOMBIE_POGO);
            z.overrideParticleScale(aParticle);
        }

        // PVZP_ASSERT(mZombiePhase != PHASE_ZOMBIE_DYING && mZombiePhase != PHASE_ZOMBIE_BURNED && !mDead);

        z.mZombieHeight = ZombieHeight.HEIGHT_FALLING;
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        z.startWalkAnim(0);
        z.mZombieRect = new Rect(36, 17, 42, 115);
        z.mZombieAttackRect = new Rect(20, 17, 50, 115);
        z.mShieldHealth = 0;
        z.mShieldType = ShieldType.SHIELDTYPE_NONE;
        z.mHasObject = false;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1430-1521 (Zombie::UpdateZombiePogo).
     */
    public static void updateZombiePogo(Zombie z) {
        if (z.isDeadOrDying() || z.isImmobilizied() || !z.isBouncingPogo() || z.mZombieHeight == ZombieHeight.HEIGHT_IN_TO_CHIMNEY) {
            return;
        }

        float aHeight = 40.0f;
        if (z.mZombiePhase >= ZombiePhase.PHASE_POGO_HIGH_BOUNCE_1 && z.mZombiePhase <= ZombiePhase.PHASE_POGO_HIGH_BOUNCE_6) {
            aHeight = 50.0f + 20.0f * (z.mZombiePhase - ZombiePhase.PHASE_POGO_HIGH_BOUNCE_1);
        } else if (z.mZombiePhase == ZombiePhase.PHASE_POGO_FORWARD_BOUNCE_2) {
            aHeight = 90.0f;
        } else if (z.mZombiePhase == ZombiePhase.PHASE_POGO_FORWARD_BOUNCE_7) {
            aHeight = 170.0f;
        }
        z.mAltitude = PvzpCommon.pvzpAnimateCurveFloat(Zombie.POGO_BOUNCE_TIME, 0, z.mPhaseCounter, 9.0f, aHeight + 9.0f,
                PvzpCurves.CURVE_BOUNCE_SLOW_MIDDLE);
        z.mFrame = Math.max(0, Math.min(3, (int) (3 - z.mAltitude / 3)));

        if (z.mPhaseCounter == 7) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            aBodyReanim.mAnimTime = 0.0f;
            aBodyReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        }
        if (z.isOnBoard() && z.mPhaseCounter == 5) {
            z.mApp.playFoley(FoleyType.FOLEY_POGO_ZOMBIE);
        }

        if (z.mZombieHeight == ZombieHeight.HEIGHT_UP_TO_HIGH_GROUND) {
            z.mAltitude += GameConstants.HIGH_GROUND_HEIGHT;
            z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
        } else if (z.mZombieHeight == ZombieHeight.HEIGHT_DOWN_OFF_HIGH_GROUND) {
            z.mOnHighGround = false;
            z.mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
        } else if (z.mOnHighGround) {
            z.mAltitude += GameConstants.HIGH_GROUND_HEIGHT;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_POGO_FORWARD_BOUNCE_2 && z.mPhaseCounter == 70) {
            Plant aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_VAULT);
            if (aPlant != null && aPlant.mSeedType == SeedType.SEED_TALLNUT) {
                z.mApp.playFoley(FoleyType.FOLEY_BONK);
                z.mApp.addPvzpParticle(aPlant.mX + 60, aPlant.mY - 20, z.mRenderOrder + 1,
                        ParticleEffect.PARTICLE_TALL_NUT_BLOCK);

                z.mShieldType = ShieldType.SHIELDTYPE_NONE;
                pogoBreak(z, 0);
                return;
            }
        }

        if (z.mPhaseCounter != 0) {
            return;
        }

        Plant aPlant = null;
        if (z.isOnBoard()) {
            aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_VAULT);
        }
        if (aPlant == null) {
            z.mZombiePhase = ZombiePhase.PHASE_POGO_BOUNCING;

            z.pickRandomSpeed();
            z.mPhaseCounter = Zombie.POGO_BOUNCE_TIME;
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_POGO_HIGH_BOUNCE_1) {
            z.mZombiePhase = ZombiePhase.PHASE_POGO_FORWARD_BOUNCE_2;
            z.mVelX = (z.mX - aPlant.mX + 60) / (float) Zombie.POGO_BOUNCE_TIME;
            z.mPhaseCounter = Zombie.POGO_BOUNCE_TIME;
        } else {
            z.mZombiePhase = ZombiePhase.PHASE_POGO_HIGH_BOUNCE_1;
            z.mVelX = 0.0f;
            z.mPhaseCounter = Zombie.POGO_BOUNCE_TIME;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1522-1553 (Zombie::ZombieCatapultFire).
     */
    public static void zombieCatapultFire(Zombie z, Plant thePlant) {
        float aOriginX = z.mPosX + 113.0f;
        float aOriginY = z.mPosY - 44.0f;
        int aTargetX;
        int aTargetY;
        if (thePlant != null) {
            aTargetX = thePlant.mX;
            aTargetY = thePlant.mY;
        } else {
            aTargetX = (int) (z.mPosX - 300.0f);
            aTargetY = 0;
        }

        z.mApp.playFoley(FoleyType.FOLEY_BASKETBALL);

        Projectile aProjectile = z.mBoard.addProjectile((int) aOriginX, (int) aOriginY, z.mRenderOrder, z.mRow,
                ProjectileType.PROJECTILE_BASKETBALL);
        float aRangeX = aOriginX - aTargetX - 20.0f;
        float aRangeY = aTargetY - aOriginY;
        if (aRangeX < 40.0f) {
            aRangeX = 40.0f;
        }
        aProjectile.mMotionType = ProjectileMotion.MOTION_LOBBED;
        aProjectile.mVelX = -aRangeX / 120.0f;
        aProjectile.mVelY = 0.0f;
        aProjectile.mVelZ = aRangeY / 120.0f - 7.0f;
        aProjectile.mAccZ = 0.115f;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1554-1573 (Zombie::FindCatapultTarget).
     */
    public static Plant findCatapultTarget(Zombie z) {
        Plant aTarget = null;

        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow == z.mRow && z.mX >= aPlant.mX + 100 && !aPlant.notOnGround() && !aPlant.isSpiky()) {
                if (aTarget == null || aPlant.mPlantCol < aTarget.mPlantCol) {
                    aTarget = z.mBoard.getTopPlantAt(aPlant.mPlantCol, aPlant.mRow, PlantPriority.TOPPLANT_CATAPULT_ORDER);
                }
            }
        }

        return aTarget;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1574-1626 (Zombie::UpdateZombieCatapult).
     */
    public static void updateZombieCatapult(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_NORMAL) {
            if (z.mPosX <= 650 && findCatapultTarget(z) != null && z.mSummonCounter > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_CATAPULT_LAUNCHING;
                z.mPhaseCounter = 300;
                z.playZombieReanim("anim_shoot", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_CATAPULT_LAUNCHING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.545f)) {
                Plant aPlant = findCatapultTarget(z);
                zombieCatapultFire(z, aPlant);
            }
            if (aBodyReanim.mLoopCount > 0) {
                z.mSummonCounter--;
                if (z.mSummonCounter == 4) {
                    z.reanimShowTrack("Zombie_catapult_basketball", Reanimation.RENDER_GROUP_HIDDEN);
                } else if (z.mSummonCounter == 3) {
                    z.reanimShowTrack("Zombie_catapult_basketball2", Reanimation.RENDER_GROUP_HIDDEN);
                } else if (z.mSummonCounter == 2) {
                    z.reanimShowTrack("Zombie_catapult_basketball3", Reanimation.RENDER_GROUP_HIDDEN);
                } else if (z.mSummonCounter == 1) {
                    z.reanimShowTrack("Zombie_catapult_basketball4", Reanimation.RENDER_GROUP_HIDDEN);
                }

                if (z.mSummonCounter == 0) {
                    z.playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 20, 6.0f);
                    z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
                } else {
                    z.playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 20, 12.0f);
                    z.mZombiePhase = ZombiePhase.PHASE_CATAPULT_RELOADING;
                }
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_CATAPULT_RELOADING && z.mPhaseCounter == 0) {
            Plant aPlant = findCatapultTarget(z);
            if (aPlant != null) {
                z.mZombiePhase = ZombiePhase.PHASE_CATAPULT_LAUNCHING;
                z.mPhaseCounter = 300;
                z.playZombieReanim("anim_shoot", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
            } else {
                z.playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 20, 6.0f);
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1642-1660 (Zombie::LandFlyer).
     */
    public static void landFlyer(Zombie z, int theDamageFlags) {
        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY) && z.mZombiePhase == ZombiePhase.PHASE_BALLOON_FLYING) {
            z.mApp.playSample(SoundType.SOUND_BALLOON_POP);
            z.mZombiePhase = ZombiePhase.PHASE_BALLOON_POPPING;
            z.playZombieReanim("anim_pop", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
        }

        if (z.mBoard.mPlantRow[z.mRow] == PlantRowType.PLANTROW_POOL) {
            z.dieWithLoot();
        } else {
            z.mZombieHeight = ZombieHeight.HEIGHT_FALLING;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1661-1687 (Zombie::UpdateZombieFlyer).
     */
    public static void updateZombieFlyer(Zombie z) {
        if (z.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY && z.mPosX < 720.0f) {
            z.mAltitude -= 0.1f;
            if (z.mAltitude < -35.0f) {
                landFlyer(z, 0);
            }
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_BALLOON_POPPING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_BALLOON_WALKING;
                z.startWalkAnim(0);
            }
        }

        if (z.mApp.isIZombieLevel() && z.mZombiePhase == ZombiePhase.PHASE_BALLOON_FLYING
                && z.mBoard.mChallenge.iZombieGetBrainTarget(z) != null) {
            landFlyer(z, 0);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1688-1706 (Zombie::UpdateZombieNewspaper).
     */
    public static void updateZombieNewspaper(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MADDENING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_NEWSPAPER_MAD;
                if (z.mBoard.countZombiesOnScreen() <= 10 && z.mHasHead) {
                    z.mApp.playFoley(FoleyType.FOLEY_NEWSPAPER_RARRGH);
                }

                z.startWalkAnim(20);
                aBodyReanim.setImageOverride("anim_head1", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_PAPER_MADHEAD));
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1707-1796 (Zombie::UpdateZombiePolevaulter).
     */
    public static void updateZombiePolevaulter(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT && z.mHasHead
                && z.mZombieHeight == ZombieHeight.HEIGHT_ZOMBIE_NORMAL) {
            Plant aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_VAULT);
            if (aPlant != null) {
                if (z.mBoard.getLadderAt(aPlant.mPlantCol, aPlant.mRow) != null) {
                    float aPlantX = z.mBoard.gridToPixelX(aPlant.mPlantCol, aPlant.mRow) + 40;
                    if (aPlantX > z.mPosX && z.mZombieHeight == ZombieHeight.HEIGHT_ZOMBIE_NORMAL
                            && z.mUseLadderCol != aPlant.mPlantCol) {
                        z.mZombieHeight = ZombieHeight.HEIGHT_UP_LADDER;
                        z.mUseLadderCol = aPlant.mPlantCol;
                    }
                    return;
                }

                z.mZombiePhase = ZombiePhase.PHASE_POLEVAULTER_IN_VAULT;
                z.playZombieReanim("anim_jump", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);

                Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
                float aAnimDuration = aBodyReanim.mFrameCount / aBodyReanim.mAnimRate * 100.0f;
                int aJumpDistance = z.mX - aPlant.mX - 80;
                if (z.mApp.isWallnutBowlingLevel()) {
                    aJumpDistance = 0;
                }
                z.mVelX = aJumpDistance / aAnimDuration;
                z.mHasObject = false;
            }

            if (z.mApp.isIZombieLevel() && z.mBoard.mChallenge.iZombieGetBrainTarget(z) != null) {
                z.mZombiePhase = ZombiePhase.PHASE_POLEVAULTER_POST_VAULT;
                z.startWalkAnim(0);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);

            boolean aJumpEnds = false;
            if (aBodyReanim.mAnimTime > 0.6f && aBodyReanim.mAnimTime <= 0.7f) {
                Plant aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_VAULT);
                if (aPlant != null && aPlant.mSeedType == SeedType.SEED_TALLNUT) {
                    z.mApp.playFoley(FoleyType.FOLEY_BONK);
                    aJumpEnds = true;
                    z.mApp.addPvzpParticle(aPlant.mX + 60, aPlant.mY - 20, z.mRenderOrder + 1,
                            ParticleEffect.PARTICLE_TALL_NUT_BLOCK);

                    z.mZombieHeight = ZombieHeight.HEIGHT_FALLING;
                    z.mPosX = aPlant.mX;
                    z.mPosY -= 30.0f;
                }
            }

            if (aBodyReanim.mLoopCount > 0) {
                aJumpEnds = true;
                z.mPosX -= 150.0f;
            }
            if (aBodyReanim.shouldTriggerTimedEvent(0.2f)) {
                z.mApp.playFoley(FoleyType.FOLEY_GRASSSTEP);
            }
            if (aBodyReanim.shouldTriggerTimedEvent(0.4f)) {
                z.mApp.playFoley(FoleyType.FOLEY_POLEVAULT);
            }

            if (aJumpEnds) {
                z.mX = (int) z.mPosX;
                z.mZombiePhase = ZombiePhase.PHASE_POLEVAULTER_POST_VAULT;
                z.mZombieAttackRect = new Rect(50, 0, 20, 115);

                z.startWalkAnim(0);
            } else {
                float aOldPosX = z.mPosX;
                z.mPosX -= 150.0f * aBodyReanim.mAnimTime;
                z.mPosY = z.getPosYBasedOnRow(z.mRow);
                z.mPosX = aOldPosX;
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1812-1932 (Zombie::UpdateZombieDolphinRider).
     */
    public static void updateZombieDolphinRider(Zombie z) {
        if (z.isTangleKelpTarget()) {
            return;
        }

        boolean aBackwards = z.isWalkingBackwards();
        if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING && !aBackwards) {
            if (z.mX > 700 && z.mX <= 720) {
                z.mZombiePhase = ZombiePhase.PHASE_DOLPHIN_INTO_POOL;
                z.playZombieReanim("anim_jumpinpool", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 16.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.56f)) {
                Reanimation aSplashReanim = z.mApp.addReanimation(z.mX - 83, z.mY + 73, z.mRenderOrder + 1,
                        ReanimationType.REANIM_SPLASH);
                aSplashReanim.overrideScale(1.2f, 0.8f);
                z.mApp.addPvzpParticle(z.mX - 46, z.mY + 115, z.mRenderOrder + 1, ParticleEffect.PARTICLE_PLANTING_POOL);
                z.mApp.playFoley(FoleyType.FOLEY_ZOMBIE_ENTERING_WATER);
            }

            if (aBodyReanim.mLoopCount > 0) {
                z.mPosX -= 70.0f;
                z.mZombiePhase = ZombiePhase.PHASE_DOLPHIN_RIDING;
                z.mInPool = true;
                z.mZombieAttackRect = new Rect(-29, 0, 70, 115);
                z.playZombieReanim("anim_ride", ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME, 0, 12.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING) {
            if (z.mX <= 10) {
                z.mAltitude = -40.0f;
                z.mZombieHeight = ZombieHeight.HEIGHT_OUT_OF_POOL;
                z.mZombiePhase = ZombiePhase.PHASE_DOLPHIN_WALKING;

                z.poolSplash(false);
                z.playZombieReanim("anim_walkdolphin", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
                z.pickRandomSpeed();
                return;
            }

            if (z.mHasHead && !z.isTangleKelpTarget()) {
                Plant aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_VAULT);
                if (aPlant != null) {
                    z.mApp.playFoley(FoleyType.FOLEY_DOLPHIN_BEFORE_JUMPING);
                    z.mApp.playFoley(FoleyType.FOLEY_PLANT_WATER);

                    z.mVelX = 0.5f;
                    z.mZombiePhase = ZombiePhase.PHASE_DOLPHIN_IN_JUMP;
                    z.mPhaseCounter = Zombie.DOLPHIN_JUMP_TIME;
                    z.playZombieReanim("anim_dolphinjump", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 10.0f);
                }
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            z.mAltitude = PvzpCommon.pvzpAnimateCurveFloat(Zombie.DOLPHIN_JUMP_TIME, 0, z.mPhaseCounter, 0.0f, 10.0f,
                    PvzpCurves.CURVE_LINEAR);

            boolean aJumpEnds = false;
            if (aBodyReanim.shouldTriggerTimedEvent(0.3f)) {
                Plant aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_VAULT);
                if (aPlant != null && aPlant.mSeedType == SeedType.SEED_TALLNUT) {
                    z.mApp.playFoley(FoleyType.FOLEY_BONK);
                    aJumpEnds = true;
                    z.mApp.addPvzpParticle(aPlant.mX + 60, aPlant.mY - 20, z.mRenderOrder + 1,
                            ParticleEffect.PARTICLE_TALL_NUT_BLOCK);

                    z.mZombieHeight = ZombieHeight.HEIGHT_FALLING;
                    z.mPosX = aPlant.mX + 25.0f;
                    z.mAltitude = 30.0f;
                }
            } else if (aBodyReanim.shouldTriggerTimedEvent(0.49f)) {
                Reanimation aSplashReanim = z.mApp.addReanimation(z.mX - 63, z.mY + 73, z.mRenderOrder + 1,
                        ReanimationType.REANIM_SPLASH);
                aSplashReanim.overrideScale(1.2f, 0.8f);
                z.mApp.addPvzpParticle(z.mX - 26, z.mY + 115, z.mRenderOrder + 1, ParticleEffect.PARTICLE_PLANTING_POOL);
                z.mApp.playFoley(FoleyType.FOLEY_ZOMBIE_ENTERING_WATER);
                z.mVelX = 0.0f;
            } else if (aBodyReanim.mLoopCount > 0) {
                aJumpEnds = true;
                z.mPosX -= 94.0f;
                z.mAltitude = 0.0f;
            }

            if (aJumpEnds) {
                z.mZombieAttackRect = new Rect(30, 0, 30, 115);
                z.mZombieRect = new Rect(20, 0, 42, 115);
                z.mZombiePhase = ZombiePhase.PHASE_DOLPHIN_WALKING_IN_POOL;
                z.startWalkAnim(0);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING_IN_POOL) {
            if ((z.mX <= 10 && !aBackwards) || (z.mX > 680 && aBackwards)) {
                z.mAltitude = -40.0f;
                z.mZombieHeight = ZombieHeight.HEIGHT_OUT_OF_POOL;
                z.mZombiePhase = ZombiePhase.PHASE_DOLPHIN_WALKING_WITHOUT_DOLPHIN;

                z.poolSplash(false);
                z.playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
                z.pickRandomSpeed();
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1933-2030 (Zombie::UpdateZombieSnorkel).
     */
    public static void updateZombieSnorkel(Zombie z) {
        boolean aBackwards = z.isWalkingBackwards();
        if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING && !aBackwards) {
            if (z.mX > 700 && z.mX <= 720) {
                z.mVelX = 0.2f;
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_INTO_POOL;
                z.playZombieReanim("anim_jumpinpool", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 16.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            z.mAltitude = PvzpCommon.pvzpAnimateCurveFloat(0, 1000, (int) (aBodyReanim.mAnimTime * 1000), 0.0f, 10.0f,
                    PvzpCurves.CURVE_LINEAR);

            if (aBodyReanim.shouldTriggerTimedEvent(0.83f)) {
                Reanimation aSplashReanim = z.mApp.addReanimation(z.mX - 47, z.mY + 73, z.mRenderOrder + 1,
                        ReanimationType.REANIM_SPLASH);
                aSplashReanim.overrideScale(1.2f, 0.8f);
                z.mApp.addPvzpParticle(z.mX - 10, z.mY + 115, z.mRenderOrder + 1, ParticleEffect.PARTICLE_PLANTING_POOL);
                z.mApp.playFoley(FoleyType.FOLEY_ZOMBIE_ENTERING_WATER);
            }

            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL;
                z.mInPool = true;
                z.playZombieReanim("anim_swim", ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME, 0, 12.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL) {
            if (!z.mHasHead) {
                z.takeDamage(1800, 9);
            } else if (z.mX <= 25 && !aBackwards) {
                z.mAltitude = -90.0f;
                z.mPosX -= 15.0f;
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_WALKING;
                z.mZombieHeight = ZombieHeight.HEIGHT_OUT_OF_POOL;

                z.poolSplash(false);
                z.startWalkAnim(0);
            } else if (z.mX > 640 && aBackwards) {
                z.mAltitude = -90.0f;
                z.mPosX += 15.0f;
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_WALKING;
                z.mZombieHeight = ZombieHeight.HEIGHT_OUT_OF_POOL;

                z.poolSplash(false);
                z.startWalkAnim(0);
            } else if (z.mIsEating) {
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_UP_TO_EAT;
                z.playZombieReanim("anim_uptoeat", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_UP_TO_EAT) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (!z.mIsEating) {
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_DOWN_FROM_EAT;
                z.playZombieReanim("anim_uptoeat", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, -24.0f);
            } else if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_EATING_IN_POOL;
                z.playZombieReanim("anim_eat", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_EATING_IN_POOL) {
            if (!z.mIsEating) {
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_DOWN_FROM_EAT;
                z.playZombieReanim("anim_uptoeat", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, -24.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_DOWN_FROM_EAT) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL;
                z.playZombieReanim("anim_swim", ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME, 0, 0.0f);
                z.pickRandomSpeed();
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2031-2079 (Zombie::UpdateZombieJackInTheBox).
     */
    public static void updateZombieJackInTheBox(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_RUNNING) {
            if (z.mPhaseCounter <= 0 && z.mHasHead) {
                z.mPhaseCounter = 110;
                z.mZombiePhase = ZombiePhase.PHASE_JACK_IN_THE_BOX_POPPING;

                z.stopZombieSound();
                z.mApp.playSample(SoundType.SOUND_BOING);
                z.playZombieReanim("anim_pop", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 28.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_POPPING) {
            if (z.mPhaseCounter == 80) {
                z.mApp.playFoley(FoleyType.FOLEY_JACK_SURPRISE);
            }

            if (z.mPhaseCounter <= 0) {
                z.mApp.playFoley(FoleyType.FOLEY_EXPLOSION);

                int aPosX = z.mX + z.mWidth / 2;
                int aPosY = z.mY + z.mHeight / 2;
                if (z.mMindControlled) {
                    z.mBoard.killAllZombiesInRadius(z.mRow, aPosX, aPosY, Zombie.JACK_IN_THE_BOX_ZOMBIE_RADIUS, 1, true, 127);
                } else {
                    z.mBoard.killAllZombiesInRadius(z.mRow, aPosX, aPosY, Zombie.JACK_IN_THE_BOX_ZOMBIE_RADIUS, 1, true, 255);
                    z.mBoard.killAllPlantsInRadius(aPosX, aPosY, Zombie.JACK_IN_THE_BOX_PLANT_RADIUS);
                }

                z.mApp.addPvzpParticle(aPosX, aPosY, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0),
                        ParticleEffect.PARTICLE_JACKEXPLODE);
                z.mBoard.shakeBoard(4, -6);
                z.dieNoLoot();

                if (z.mApp.isScaryPotterLevel()) {
                    z.mBoard.mChallenge.scaryPotterJackExplode(aPosX, aPosY);
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2080-2287 (Zombie::UpdateZombieGargantuar).
     */
    public static void updateZombieGargantuar(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_GARGANTUAR_SMASHING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.64f)) {
                // DO_FIX_BUGS: hypnotized gargantuars smash zombies
                if (z.mMindControlled) {
                    Zombie aZombie = z.findZombieTarget();
                    if (aZombie != null) {
                        int aDamage = z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR ? 1000 : 500;
                        aZombie.takeDamage(aDamage, 0);
                    }
                } else {
                    Plant aPlant = z.findPlantTarget(ZombieAttackType.ATTACKTYPE_CHEW);
                    if (aPlant != null) {
                        if (aPlant.mSeedType == SeedType.SEED_SPIKEROCK) {
                            z.takeDamage(20, 32);
                            aPlant.spikeRockTakeDamage();
                            if (aPlant.mPlantHealth <= 0) {
                                z.squishAllInSquare(aPlant.mPlantCol, aPlant.mRow, ZombieAttackType.ATTACKTYPE_CHEW);
                            }
                        } else {
                            z.squishAllInSquare(aPlant.mPlantCol, aPlant.mRow, ZombieAttackType.ATTACKTYPE_CHEW);
                        }
                    }

                    if (z.mApp.isScaryPotterLevel()) {
                        int aGridX = z.mBoard.pixelToGridX((int) z.mPosX, (int) z.mPosY);
                        GridItem aScaryPot = z.mBoard.getScaryPotAt(aGridX, z.mRow);
                        if (aScaryPot != null) {
                            z.mBoard.mChallenge.scaryPotterOpenPot(aScaryPot);
                        }
                    }

                    if (z.mApp.isIZombieLevel()) {
                        GridItem aBrain = z.mBoard.mChallenge.iZombieGetBrainTarget(z);
                        if (aBrain != null) {
                            z.mBoard.mChallenge.iZombieSquishBrain(aBrain);
                        }
                    }
                }

                z.mApp.playFoley(FoleyType.FOLEY_THUMP);
                z.mBoard.shakeBoard(0, 3);
            }

            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
                z.startWalkAnim(20);
            }

            return;
        }

        float aThrowingDistance = z.mPosX - 360.0f;
        if (z.mZombiePhase == ZombiePhase.PHASE_GARGANTUAR_THROWING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.74f)) {
                z.mHasObject = false;
                z.reanimShowPrefix("Zombie_imp", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowTrack("Zombie_gargantuar_whiterope", Reanimation.RENDER_GROUP_HIDDEN);
                z.mApp.playFoley(FoleyType.FOLEY_SWING);

                Zombie aZombieImp = z.mBoard.addZombie(ZombieType.ZOMBIE_IMP, z.mFromWave);
                if (aZombieImp == null) {
                    return;
                }

                float aMinThrowDistance = 40.0f;
                if (z.mBoard.stageHasRoof()) {
                    aThrowingDistance -= 180.0f;
                    aMinThrowDistance = -140.0f;
                }
                if (aThrowingDistance < aMinThrowDistance) {
                    aThrowingDistance = aMinThrowDistance;
                } else if (aThrowingDistance > 140.0f) {
                    aThrowingDistance -= PvzpCommon.randRangeFloat(0.0f, 100.0f);
                }

                aZombieImp.mPosX = z.mPosX - 133.0f;
                aZombieImp.mPosY = z.getPosYBasedOnRow(z.mRow);
                aZombieImp.setRow(z.mRow);
                aZombieImp.mVariant = false;
                aZombieImp.mAltitude = 88.0f;
                aZombieImp.mRenderOrder = z.mRenderOrder + 1;
                aZombieImp.mZombiePhase = ZombiePhase.PHASE_IMP_GETTING_THROWN;
                // DO_FIX_BUGS
                aZombieImp.mScaleZombie = z.mScaleZombie;
                aZombieImp.mBodyHealth *= z.mScaleZombie * z.mScaleZombie;
                aZombieImp.mBodyMaxHealth *= z.mScaleZombie * z.mScaleZombie;

                if (z.mMindControlled) {
                    aZombieImp.mPosX = z.mPosX + z.mWidth;
                    aZombieImp.startMindControlled();
                    aZombieImp.mVelX = -3.0f;
                } else {
                    aZombieImp.mVelX = 3.0f;
                }
                aZombieImp.mChilledCounter = z.mChilledCounter;
                aZombieImp.mVelZ = 0.5f * (aThrowingDistance / aZombieImp.mVelX) * Zombie.THOWN_ZOMBIE_GRAVITY;
                aZombieImp.playZombieReanim("anim_thrown", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 18.0f);
                aZombieImp.updateReanim();
                z.mApp.playFoley(FoleyType.FOLEY_IMP);
            }

            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
                z.startWalkAnim(20);
            }

            return;
        }

        if (z.isImmobilizied() || !z.mHasHead) {
            return;
        }

        if (z.mHasObject && z.mBodyHealth < z.mBodyMaxHealth / 2 && aThrowingDistance > 40.0f) {
            z.mZombiePhase = ZombiePhase.PHASE_GARGANTUAR_THROWING;
            z.playZombieReanim("anim_throw", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
            return;
        }

        // DO_FIX_BUGS
        boolean doSmash = false;
        if (z.mMindControlled) {
            doSmash = z.findZombieTarget() != null;
        } else if (z.findPlantTarget(ZombieAttackType.ATTACKTYPE_CHEW) != null) {
            doSmash = true;
        } else if (z.mApp.isScaryPotterLevel()) {
            int aGridX = z.mBoard.pixelToGridX((int) z.mPosX, (int) z.mPosY);
            if (z.mBoard.getScaryPotAt(aGridX, z.mRow) != null) {
                doSmash = true;
            }
        } else if (z.mApp.isIZombieLevel()) {
            if (z.mBoard.mChallenge.iZombieGetBrainTarget(z) != null) {
                doSmash = true;
            }
        }

        if (doSmash) {
            z.mZombiePhase = ZombiePhase.PHASE_GARGANTUAR_SMASHING;
            z.mApp.playFoley(FoleyType.FOLEY_LOW_GROAN);
            z.playZombieReanim("anim_smash", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 16.0f);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2288-2316 (Zombie::UpdateZombieImp).
     */
    public static void updateZombieImp(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_IMP_GETTING_THROWN) {
            z.mVelZ -= Zombie.THOWN_ZOMBIE_GRAVITY;
            z.mAltitude += z.mVelZ;
            z.mPosX -= z.mVelX;

            float aDiffY = z.getPosYBasedOnRow(z.mRow) - z.mPosY;
            z.mPosY += aDiffY;
            z.mAltitude += aDiffY;
            if (z.mAltitude <= 0.0f) {
                z.mAltitude = 0.0f;
                z.mZombiePhase = ZombiePhase.PHASE_IMP_LANDING;
                z.playZombieReanim("anim_land", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_IMP_LANDING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
                z.startWalkAnim(0);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2317-2360 (Zombie::UpdateZombiePeaHead).
     */
    public static void updateZombiePeaHead(Zombie z) {
        if (!z.mHasHead) {
            return;
        }

        if (z.mPhaseCounter == 35) {
            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.playReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 35.0f);
        } else if (z.mPhaseCounter == 0) {
            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.playReanim("anim_head_idle", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 15.0f);
            z.mApp.playFoley(FoleyType.FOLEY_THROW);

            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            int aTrackIndex = aBodyReanim.findTrackIndex("anim_head1");
            ReanimatorTransform aTransform = new ReanimatorTransform();
            aBodyReanim.getCurrentTransform(aTrackIndex, aTransform);

            float aOriginX = z.mPosX + aTransform.mTransX - 9.0f;
            float aOriginY = z.mPosY + aTransform.mTransY + 6.0f - z.mAltitude;
            // DO_FIX_BUGS: hypnotized fires a friendly pea instead
            if (z.mMindControlled) {
                aOriginX += 90.0f * z.mScaleZombie;
                Projectile aProjectile = z.mBoard.addProjectile((int) aOriginX, (int) aOriginY, z.mRenderOrder, z.mRow,
                        ProjectileType.PROJECTILE_PEA);
                aProjectile.mDamageRangeFlags = 1;
            } else {
                Projectile aProjectile = z.mBoard.addProjectile((int) aOriginX, (int) aOriginY, z.mRenderOrder, z.mRow,
                        ProjectileType.PROJECTILE_ZOMBIE_PEA);
                aProjectile.mMotionType = ProjectileMotion.MOTION_BACKWARDS;
            }

            z.mPhaseCounter = 150;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2361-2390 (Zombie::BurnRow). Only used by the DO_FIX_BUGS
     * jalapeno zombie fix.
     */
    public static void burnRow(Zombie z, int theRow) {
        for (Zombie aZombie : z.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if ((aZombie.mZombieType == ZombieType.ZOMBIE_BOSS || aZombie.mRow == theRow) && aZombie.effectedByDamage(127)) {
                aZombie.removeColdEffects();
                aZombie.applyBurn();
            }
        }

        for (GridItem aGridItem : z.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridY == theRow && aGridItem.mGridItemType == GridItemType.GRIDITEM_LADDER) {
                aGridItem.gridItemDie();
            }
        }

        Zombie aBossZombie = z.mBoard.getBossZombie();
        if (aBossZombie != null && aBossZombie.mFireballRow == theRow) {
            aBossZombie.bossDestroyIceballInRow();
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2391-2438 (Zombie::UpdateZombieJalapenoHead).
     */
    public static void updateZombieJalapenoHead(Zombie z) {
        if (!z.mHasHead) {
            return;
        }

        if (z.mPhaseCounter == 0) {
            z.mApp.playFoley(FoleyType.FOLEY_JALAPENO_IGNITE);
            z.mApp.playFoley(FoleyType.FOLEY_JUICY);
            z.mBoard.doFwoosh(z.mRow);
            z.mBoard.shakeBoard(3, -4);

            // DO_FIX_BUGS
            if (z.mMindControlled) {
                burnRow(z, z.mRow);
            } else {
                for (Plant aPlant : z.mBoard.mPlants) {
                    if (aPlant.mDead) {
                        continue;
                    }
                    // Rect aPlantRect = aPlant->GetPlantRect();
                    if (aPlant.mRow == z.mRow && !aPlant.notOnGround()) {
                        z.mBoard.mPlantsEaten++;
                        aPlant.die(Plant.PLANT_REMOVED_EXPLODED);
                    }
                }
            }
            z.dieNoLoot();
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2439-2484 (Zombie::UpdateZombieGatlingHead).
     */
    public static void updateZombieGatlingHead(Zombie z) {
        if (!z.mHasHead) {
            return;
        }

        if (z.mPhaseCounter == 100) {
            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.playReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 38.0f);
        } else if (z.mPhaseCounter == 18 || z.mPhaseCounter == 35 || z.mPhaseCounter == 51 || z.mPhaseCounter == 68) {
            z.mApp.playFoley(FoleyType.FOLEY_THROW);

            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            int aTrackIndex = aBodyReanim.findTrackIndex("anim_head1");
            ReanimatorTransform aTransform = new ReanimatorTransform();
            aBodyReanim.getCurrentTransform(aTrackIndex, aTransform);

            float aOriginX = z.mPosX + aTransform.mTransX - 9.0f;
            float aOriginY = z.mPosY + aTransform.mTransY + 6.0f;
            // DO_FIX_BUGS: hypnotized fires a friendly pea instead
            if (z.mMindControlled) {
                aOriginX += 90.0f * z.mScaleZombie;
                Projectile aProjectile = z.mBoard.addProjectile((int) aOriginX, (int) aOriginY, z.mRenderOrder, z.mRow,
                        ProjectileType.PROJECTILE_PEA);
                aProjectile.mDamageRangeFlags = 1;
            } else {
                Projectile aProjectile = z.mBoard.addProjectile((int) aOriginX, (int) aOriginY, z.mRenderOrder, z.mRow,
                        ProjectileType.PROJECTILE_ZOMBIE_PEA);
                aProjectile.mMotionType = ProjectileMotion.MOTION_BACKWARDS;
            }
        } else if (z.mPhaseCounter == 0) {
            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.playReanim("anim_head_idle", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 15.0f);
            z.mPhaseCounter = 150;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2485-2611 (Zombie::UpdateZombieSquashHead).
     */
    public static void updateZombieSquashHead(Zombie z) {
        if (z.mHasHead && z.mIsEating && z.mZombiePhase == ZombiePhase.PHASE_SQUASH_PRE_LAUNCH) {
            z.stopEating();
            z.playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 20, 12.0f);
            z.mHasHead = false;

            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.playReanim("anim_jumpup", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
            aHeadReanim.mRenderOrder = z.mRenderOrder + 1;
            aHeadReanim.setPosition(z.mPosX + 6.0f, z.mPosY - 21.0f);

            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            ReanimatorTrackInstance aTrackInstance = aBodyReanim.getTrackInstanceByName("anim_head1");
            Attachment.attachmentDetach(aTrackInstance.mAttachmentID);
            aHeadReanim.overrideScale(0.75f, 0.75f);
            aHeadReanim.mOverlayMatrix.m10 = 0.0f;

            z.mZombiePhase = ZombiePhase.PHASE_SQUASH_RISING;
            z.mPhaseCounter = 95;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_SQUASH_RISING) {
            int aDestX = z.mBoard.gridToPixelX(z.mBoard.pixelToGridXKeepOnBoard(z.mX, z.mY), z.mRow);
            // DO_FIX_BUGS
            if (z.mMindControlled) {
                Zombie aZombie = z.findZombieTarget();
                if (aZombie != null) {
                    aDestX = (int) aZombie.zombieTargetLeadX(0.0f);
                } else {
                    aDestX += 90.0f * z.mScaleZombie;
                }
            }
            int aPosX = PvzpCommon.pvzpAnimateCurve(50, 20, z.mPhaseCounter, 0, (int) (aDestX - z.mPosX),
                    PvzpCurves.CURVE_EASE_IN_OUT);
            int aPosY = PvzpCommon.pvzpAnimateCurve(50, 20, z.mPhaseCounter, 0, -20, PvzpCurves.CURVE_EASE_IN_OUT);

            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.setPosition(z.mPosX + aPosX + 6.0f, z.mPosY + aPosY - 21.0f);

            if (z.mPhaseCounter == 0) {
                aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
                aHeadReanim.playReanim("anim_jumpdown", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 60.0f);
                z.mZombiePhase = ZombiePhase.PHASE_SQUASH_FALLING;
                z.mPhaseCounter = 10;
            }
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_SQUASH_FALLING) {
            int aPosY = PvzpCommon.pvzpAnimateCurve(10, 0, z.mPhaseCounter, -20, 74, PvzpCurves.CURVE_LINEAR);
            int aDestX = z.mBoard.gridToPixelX(z.mBoard.pixelToGridXKeepOnBoard(z.mX, z.mY), z.mRow);
            // DO_FIX_BUGS
            if (z.mMindControlled) {
                Zombie aZombie = z.findZombieTarget();
                if (aZombie != null) {
                    aDestX = (int) aZombie.zombieTargetLeadX(0.0f);
                } else {
                    aDestX += 90.0f * z.mScaleZombie;
                }
            }

            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.setPosition(z.mPosX + 6.0f + aDestX - z.mPosX, z.mPosY - 21.0f + aPosY);

            if (z.mPhaseCounter == 2) {
                // DO_FIX_BUGS: hypnotized squashes zombies instead
                if (z.mMindControlled) {
                    Rect aAttackRect = new Rect(aDestX - 73, (int) z.mPosY + 4, 65, 90); // rect values not verified, TBD

                    for (Zombie aZombie : z.mBoard.mZombies) {
                        if (aZombie.mDead) {
                            continue;
                        }
                        if ((aZombie.mRow == z.mRow || aZombie.mZombieType == ZombieType.ZOMBIE_BOSS)
                                && aZombie.effectedByDamage(13)) {
                            Rect aZombieRect = aZombie.getZombieRect();
                            if (Board.getRectOverlap(aAttackRect, aZombieRect) > (aZombie.mZombieType == ZombieType.ZOMBIE_FOOTBALL ? -20 : 0)) {
                                aZombie.takeDamage(1800, 18);
                            }
                        }
                    }
                } else {
                    z.squishAllInSquare(z.mBoard.pixelToGridXKeepOnBoard(z.mX, z.mY), z.mRow, ZombieAttackType.ATTACKTYPE_CHEW);
                }
            }

            if (z.mPhaseCounter == 0) {
                z.mZombiePhase = ZombiePhase.PHASE_SQUASH_DONE_FALLING;
                z.mPhaseCounter = 100;

                z.mBoard.shakeBoard(1, 4);
                z.mApp.playFoley(FoleyType.FOLEY_THUMP);
            }
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_SQUASH_DONE_FALLING && z.mPhaseCounter == 0) {
            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.reanimationDie();
            z.mSpecialHeadReanimID = ReanimationID.REANIMATIONID_NULL;

            z.takeDamage(1800, 9);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2612-2639 (Zombie::BobsledCrash).
     */
    public static void bobsledCrash(Zombie z) {
        z.mAltitude = 0.0f;
        z.mZombieRect = new Rect(36, 0, 42, 115);
        z.mZombiePhase = ZombiePhase.PHASE_BOBSLED_CRASHING;
        z.mPhaseCounter = Zombie.BOBSLED_CRASH_TIME;
        z.startWalkAnim(0);

        Reanimation aLeaderReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        for (int i = 0; i < Zombie.NUM_BOBSLED_FOLLOWERS; i++) {
            Zombie aFollowerZombie = z.mBoard.zombieGet(z.mFollowerZombieID[i]);
            aFollowerZombie.mZombiePhase = ZombiePhase.PHASE_BOBSLED_CRASHING;
            aFollowerZombie.mPhaseCounter = Zombie.BOBSLED_CRASH_TIME;
            aFollowerZombie.mPosY = z.getPosYBasedOnRow(z.mRow);
            aFollowerZombie.mAltitude = 0.0f;
            aFollowerZombie.startWalkAnim(0);

            Reanimation aFollowerReanim = z.mApp.reanimationGet(aFollowerZombie.mBodyReanimID);
            if (aFollowerReanim != null) {
                aFollowerZombie.mVelX = z.mVelX;
                aFollowerReanim.mAnimTime = PvzpCommon.randRangeFloat(0.0f, 1.0f);
                aFollowerReanim.mAnimRate = aLeaderReanim.mAnimRate;
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2640-2694 (Zombie::UpdateZombieBobsled).
     */
    public static void updateZombieBobsled(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_CRASHING) {
            if (z.mPhaseCounter == 0) {
                z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
                if (z.getBobsledPosition() == 0) {
                    for (int i = 0; i < Zombie.NUM_BOBSLED_FOLLOWERS; i++) {
                        Zombie aZombie = z.mBoard.zombieGet(z.mFollowerZombieID[i]);
                        aZombie.mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
                        z.mFollowerZombieID[i] = ZombieID.ZOMBIEID_NULL;
                        aZombie.pickRandomSpeed();
                    }
                    z.pickRandomSpeed();
                }
            }
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_SLIDING) {
            if (z.mPhaseCounter == 0) {
                z.mZombiePhase = ZombiePhase.PHASE_BOBSLED_BOARDING;
                z.playZombieReanim("anim_jump", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 20.0f);
            }
        } else {
            if (z.mZombiePhase != ZombiePhase.PHASE_BOBSLED_BOARDING) {
                return;
            }

            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            int aCounter = (int) (aBodyReanim.mAnimTime * 50.0f);
            int aPosition = z.getBobsledPosition();
            if (aPosition == 1 || aPosition == 3) {
                z.mAltitude = PvzpCommon.pvzpAnimateCurveFloat(0, 50, aCounter, 8.0f, 18.0f, PvzpCurves.CURVE_LINEAR);
            } else {
                z.mAltitude = PvzpCommon.pvzpAnimateCurveFloat(0, 50, aCounter, -9.0f, 18.0f, PvzpCurves.CURVE_LINEAR);
            }
        }

        z.mBoard.mIceTimer[z.mRow] = Math.max(500, z.mBoard.mIceTimer[z.mRow]);
        if (z.mPosX + 10.0f < z.mBoard.mIceMinX[z.mRow] && z.getBobsledPosition() == 0) {
            z.takeDamage(6, 8);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2695-2711 (Zombie::DiggerLoseAxe).
     */
    public static void diggerLoseAxe(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            z.mZombiePhase = ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE;
            z.mPhaseCounter = 200;
            z.setAnimRate(0.0f);
            z.updateAnimSpeed();
            Attachment.attachmentDetachCrossFadeParticleType(z.mAttachmentID, ParticleEffect.PARTICLE_DIGGER_TUNNEL, null);
            z.stopZombieSound();
        }

        z.mHasObject = false;
        z.reanimShowTrack("Zombie_digger_pickaxe", Reanimation.RENDER_GROUP_HIDDEN);
        z.reanimShowTrack("Zombie_digger_dirt", Reanimation.RENDER_GROUP_HIDDEN);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2712-2809 (Zombie::UpdateZombieDigger).
     */
    public static void updateZombieDigger(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            if (z.mPosX < 10.0f) {
                z.mAltitude = -120.0f;
                z.mZombiePhase = ZombiePhase.PHASE_DIGGER_RISING;
                z.mPhaseCounter = 130;
                z.playZombieReanim("anim_drill", ReanimLoopType.REANIM_LOOP, 0, 20.0f);

                z.mApp.playFoley(FoleyType.FOLEY_DIRT_RISE);
                z.mApp.playFoley(FoleyType.FOLEY_WAKEUP);
                Attachment.attachmentDetachCrossFadeParticleType(z.mAttachmentID, ParticleEffect.PARTICLE_DIGGER_TUNNEL, null);
                z.stopZombieSound();

                z.mApp.addPvzpParticle(z.mPosX + 60.0f, z.mPosY + 118.0f, z.mRenderOrder + 1,
                        ParticleEffect.PARTICLE_DIGGER_RISE);
                Reanimation aDirtReanim = z.mApp.addReanimation(z.mPosX + 13.0f, z.mPosY + 97.0f, z.mRenderOrder + 1,
                        ReanimationType.REANIM_DIGGER_DIRT);
                aDirtReanim.mAnimRate = 24.0f;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING) {
            if (z.mPhaseCounter > 40) {
                z.mAltitude = PvzpCommon.pvzpAnimateCurve(130, 40, z.mPhaseCounter, -120, 20, PvzpCurves.CURVE_EASE_OUT);
            } else {
                z.mAltitude = PvzpCommon.pvzpAnimateCurve(30, 0, z.mPhaseCounter, 20, 0, PvzpCurves.CURVE_EASE_IN);
            }

            if (z.mPhaseCounter == 30) {
                z.playZombieReanim("anim_landing", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 12.0f);
            }

            if (z.mPhaseCounter == 0) {
                z.mAltitude = 0.0f;
                z.mZombiePhase = ZombiePhase.PHASE_DIGGER_STUNNED;
                z.playZombieReanim("anim_dizzy", ReanimLoopType.REANIM_LOOP, 10, 12.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE) {
            if (z.mPhaseCounter == 150) {
                z.addAttachedReanim(23, 93, ReanimationType.REANIM_ZOMBIE_SURPRISE);
            }

            if (z.mPhaseCounter == 0) {
                z.mAltitude = -120.0f;
                z.mZombiePhase = ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE;
                z.mPhaseCounter = 130;
                z.playZombieReanim("anim_landing", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 0.0f);

                z.mApp.playFoley(FoleyType.FOLEY_DIRT_RISE);
                z.mApp.addPvzpParticle(z.mPosX + 60.0f, z.mPosY + 118.0f, z.mRenderOrder + 1,
                        ParticleEffect.PARTICLE_DIGGER_RISE);
                Reanimation aDirtReanim = z.mApp.addReanimation(z.mPosX + 13.0f, z.mPosY + 97.0f, z.mRenderOrder + 1,
                        ReanimationType.REANIM_DIGGER_DIRT);
                aDirtReanim.mAnimRate = 24.0f;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE) {
            if (z.mPhaseCounter > 40) {
                z.mAltitude = PvzpCommon.pvzpAnimateCurve(130, 40, z.mPhaseCounter, -120, 20, PvzpCurves.CURVE_EASE_OUT);
            } else {
                z.mAltitude = PvzpCommon.pvzpAnimateCurve(30, 0, z.mPhaseCounter, 20, 0, PvzpCurves.CURVE_EASE_IN);
            }

            if (z.mPhaseCounter == 30) {
                z.playZombieReanim("anim_landing", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
            }

            if (z.mPhaseCounter == 0) {
                z.mAltitude = 0.0f;
                z.mZombiePhase = ZombiePhase.PHASE_DIGGER_WALKING_WITHOUT_AXE;
                z.startWalkAnim(20);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_STUNNED) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.mLoopCount > 1) {
                z.mZombiePhase = ZombiePhase.PHASE_DIGGER_WALKING;
                z.startWalkAnim(20);
            }
        }
    }
}
