/*
 * Ported from src/Lawn/Zombie.cpp:9705-10627 (the Boss* family plus ApplyBossSmokeParticles).
 *
 * Boss slice of the Zombie port. Every method is static and takes the owning {@link Zombie} as its
 * first parameter; Zombie.java wires thin delegators (e.g. Zombie.updateBoss() ->
 * ZombieBoss.updateBoss(this)).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Common;

public final class ZombieBoss {

    private ZombieBoss() {
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.h:72-76 (PvzpPickFromArray).
     */
    private static int pickFromArray(int[] theArray, int theCount) {
        return theCount > 0 ? theArray[Common.rand(theCount)] : 0;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10475-10503 (Zombie::BossDie).
     */
    public static void die(Zombie z) {
        if (!z.isOnBoard())
            return;

        Reanimation aFireBallReanim = z.mApp.reanimationTryToGet(z.mBossFireBallReanimID);
        if (aFireBallReanim != null) {
            aFireBallReanim.reanimationDie();
            z.mBossFireBallReanimID = ReanimationID.REANIMATIONID_NULL;

            destroyIceballInRow(z);
            destroyFireball(z);
        }

        z.mApp.mMusic.fadeOut(200);

        for (Zombie aZombie : z.mBoard.mZombies) {
            if (aZombie.mDead)
                continue;
            if (aZombie != z && !aZombie.isDeadOrDying()) {
                aZombie.dieWithLoot();
            }
        }

        z.removeColdEffects();
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10505-10524 (Zombie::BossSetupReanim).
     */
    public static void setupReanim(Zombie z) {
        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        aBodyReanim.assignRenderGroupToPrefix("Boss_innerleg", Zombie.RENDER_GROUP_BOSS_BACK_LEG);
        aBodyReanim.assignRenderGroupToPrefix("Boss_outerleg", Zombie.RENDER_GROUP_BOSS_FRONT_LEG);
        aBodyReanim.assignRenderGroupToPrefix("Boss_body2", Zombie.RENDER_GROUP_BOSS_FRONT_LEG);
        aBodyReanim.assignRenderGroupToPrefix("Boss_innerarm", Zombie.RENDER_GROUP_BOSS_BACK_ARM);
        aBodyReanim.assignRenderGroupToPrefix("Boss_RV", Zombie.RENDER_GROUP_BOSS_BACK_ARM);

        Reanimation aHeadReanim = z.mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_BOSS_DRIVER);
        aHeadReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 18.0f);
        z.mSpecialHeadReanimID = z.mApp.reanimationGetID(aHeadReanim);

        ReanimatorTrackInstance aTrackInstance = aBodyReanim.getTrackInstanceByName("Boss_head2");
        AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 28.0f, -84.0f);
        aBodyReanim.mFrameBasePose = 0;
        aAttachEffect.mOffset.m00 = 1.2f;
        aAttachEffect.mOffset.m11 = 1.2f;
        aAttachEffect.mDontDrawIfParentHidden = true;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9705-9710 (Zombie::BossPlayIdle).
     */
    public static void playIdle(Zombie z) {
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_IDLE;
        z.mPhaseCounter = PvzpCommon.randRangeInt(100, 200);
        z.playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 6.0f);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9766-9791 (Zombie::BossRVLanding).
     */
    public static void rVLanding(Zombie z) {
        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead)
                continue;
            if (aPlant.mRow >= z.mTargetRow && aPlant.mRow <= z.mTargetRow + 1
                && aPlant.mPlantCol >= z.mTargetCol && aPlant.mPlantCol <= z.mTargetCol + 2) {
                aPlant.squish();
            }
        }

        z.mBoard.shakeBoard(1, 2);
        z.mApp.playSample(SoundType.SOUND_RVTHROW);

        z.mSummonCounter = 500;
        z.mBossHeadCounter = 5000;
        if (z.mBossMode >= 1) {
            z.mBossStompCounter = 4000;
        }
        if (z.mBossMode >= 2) {
            z.mBossBungeeCounter = 6500;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9751-9764 (Zombie::BossRVAttack).
     */
    public static void rVAttack(Zombie z) {
        z.removeColdEffects();
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_DROP_RV;
        // DO_FIX_BUGS adds a StageHas6Rows() pool-boss branch; upstream default is 0..3.
        z.mTargetRow = PvzpCommon.randRangeInt(0, 3);
        z.mTargetCol = PvzpCommon.randRangeInt(0, 2);

        z.playZombieReanim("anim_RV_1", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 16.0f);
        z.mApp.playFoley(FoleyType.FOLEY_HYDRAULIC_SHORT);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9910-9924 (Zombie::BossStompContact).
     */
    public static void stompContact(Zombie z) {
        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead)
                continue;
            if (aPlant.mRow >= z.mTargetRow && aPlant.mRow <= z.mTargetRow + 1 && aPlant.mPlantCol >= 5) {
                aPlant.squish();
            }
        }

        z.mBoard.shakeBoard(1, 4);
        z.mApp.playFoley(FoleyType.FOLEY_THUMP);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9967-9984 (Zombie::BossAreBungeesDone).
     */
    public static boolean areBungeesDone(Zombie z) {
        int aBungeesRemaining = 0;
        for (int i = 0; i < Zombie.NUM_BOSS_BUNGEES; i++) {
            Zombie aZombie = z.mBoard.zombieTryToGet(z.mFollowerZombieID[i]);
            if (aZombie != null) {
                if (aZombie.mZombiePhase == ZombiePhase.PHASE_BUNGEE_RISING) {
                    return true;
                }
                aBungeesRemaining++;
            }
        }

        return aBungeesRemaining == 0;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9938-9949 (Zombie::BossBungeeSpawn).
     */
    public static void bungeeSpawn(Zombie z) {
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_BUNGEES_DROP;

        for (int i = 0; i < Zombie.NUM_BOSS_BUNGEES; i++) {
            Zombie aZombie = z.mBoard.addZombieInRow(ZombieType.ZOMBIE_BUNGEE, 0, 0);
            aZombie.pickBungeeZombieTarget(z.mTargetCol + i);
            aZombie.mAltitude = aZombie.mPosY - 30.0f;
            z.mFollowerZombieID[i] = z.mBoard.zombieGetID(aZombie);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9793-9828 (Zombie::BossSpawnAttack).
     */
    public static void spawnAttack(Zombie z) {
        z.removeColdEffects();
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_SPAWNING;
        if (z.mBossMode == 0) {
            z.mSummonCounter = PvzpCommon.randRangeInt(450, 550);
        } else if (z.mBossMode == 1) {
            z.mSummonCounter = PvzpCommon.randRangeInt(350, 450);
        } else if (z.mBossMode == 2) {
            z.mSummonCounter = PvzpCommon.randRangeInt(150, 250);
        }

        z.mTargetRow = z.mBoard.pickRowForNewZombie(ZombieType.ZOMBIE_NORMAL);

        String aTrackName;
        switch (z.mTargetRow) {
            case 0:  aTrackName = "anim_spawn_1"; break;
            case 1:  aTrackName = "anim_spawn_2"; break;
            case 2:  aTrackName = "anim_spawn_3"; break;
            case 3:  aTrackName = "anim_spawn_4"; break;
            case 4:  aTrackName = "anim_spawn_5"; break;
            // DO_FIX_BUGS uses "anim_spawn_5" here; upstream default asserts.
            default: assert false; aTrackName = "anim_spawn_5"; break;
        }
        z.playZombieReanim(aTrackName, ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
        z.mApp.playFoley(FoleyType.FOLEY_HYDRAULIC_SHORT);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9926-9936 (Zombie::BossBungeeAttack).
     */
    public static void bungeeAttack(Zombie z) {
        z.removeColdEffects();
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_BUNGEES_ENTER;
        z.mBossBungeeCounter = PvzpCommon.randRangeInt(4000, 5000);
        z.mTargetCol = PvzpCommon.randRangeInt(0, 2);

        z.playZombieReanim("anim_bungee_1_enter", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
        z.mApp.playFoley(FoleyType.FOLEY_HYDRAULIC_SHORT);
        z.mApp.playFoley(FoleyType.FOLEY_BUNGEE_SCREAM);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9830-9859 (Zombie::BossSpawnContact).
     */
    public static void spawnContact(Zombie z) {
        int aZombieType;
        if (z.mZombieAge < 3500) {
            aZombieType = ZombieType.ZOMBIE_NORMAL;
        } else if (z.mZombieAge < 8000) {
            aZombieType = ZombieType.ZOMBIE_TRAFFIC_CONE;
        } else if (z.mZombieAge < 12500) {
            aZombieType = ZombieType.ZOMBIE_PAIL;
        } else {
            int aZombieTypeCount = Zombie.gBossZombieList.length;
            if (z.mTargetRow == 0) {
                assert Zombie.gBossZombieList[aZombieTypeCount - 1] == ZombieType.ZOMBIE_GARGANTUAR;
                aZombieTypeCount--;
            }

            aZombieType = pickFromArray(Zombie.gBossZombieList, aZombieTypeCount);
        }

        Zombie aZombie = z.mBoard.addZombieInRow(aZombieType, z.mTargetRow, 0);
        aZombie.mPosX = 600.0f;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9951-9965 (Zombie::BossBungeeLeave).
     */
    public static void bungeeLeave(Zombie z) {
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_BUNGEES_LEAVE;

        for (int i = 0; i < Zombie.NUM_BOSS_BUNGEES; i++) {
            Zombie aZombie = z.mBoard.zombieTryToGet(z.mFollowerZombieID[i]);
            if (aZombie != null && aZombie.mButteredCounter > 0) {
                aZombie.dieWithLoot();
            }
        }

        z.playZombieReanim("anim_bungee_1_leave", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 18.0f);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9861-9894 (Zombie::BossStompAttack).
     */
    public static void stompAttack(Zombie z) {
        z.removeColdEffects();
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_STOMPING;
        z.mBossStompCounter = PvzpCommon.randRangeInt(5500, 6500);

        int aRowsCount = 0;
        int[] aRowArray = new int[4];
        for (int i = 0; i < 4; i++) {
            if (canStompRow(z, i)) {
                aRowArray[aRowsCount] = i;
                aRowsCount++;
            }
        }

        if (aRowsCount == 0)
            return;

        z.mTargetRow = pickFromArray(aRowArray, aRowsCount);

        String aTrackName;
        switch (z.mTargetRow) {
            case 0:  aTrackName = "anim_stomp_1"; break;
            case 1:  aTrackName = "anim_stomp_2"; break;
            case 2:  aTrackName = "anim_stomp_3"; break;
            case 3:  aTrackName = "anim_stomp_4"; break;
            default: assert false; aTrackName = "anim_stomp_1"; break;
        }
        z.playZombieReanim(aTrackName, ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
        z.mApp.playFoley(FoleyType.FOLEY_HYDRAULIC_SHORT);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9896-9908 (Zombie::BossCanStompRow).
     */
    public static boolean canStompRow(Zombie z, int theRow) {
        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead)
                continue;
            if (!aPlant.notOnGround() && aPlant.mRow >= theRow && aPlant.mRow <= theRow + 1
                && aPlant.mPlantCol >= 5) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9986-9993 (Zombie::BossHeadAttack).
     */
    public static void headAttack(Zombie z) {
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_HEAD_ENTER;
        z.mBossHeadCounter = PvzpCommon.randRangeInt(4000, 5000);

        z.playZombieReanim("anim_head_enter", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
        z.mApp.playFoley(FoleyType.FOLEY_HYDRAULIC_SHORT);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10114-10139 (Zombie::BossHeadSpitContact).
     */
    public static void headSpitContact(Zombie z) {
        assert z.mApp.reanimationTryToGet(z.mBossFireBallReanimID) == null;

        float aPosY = z.mBoard.getPosYBasedOnRow(550.0f, z.mFireballRow) - 90.0f;
        Reanimation aFireBallReanim;
        if (z.mIsFireBall) {
            aFireBallReanim = z.mApp.addReanimation(455.0f, aPosY, z.mRenderOrder + 1, ReanimationType.REANIM_BOSS_FIREBALL);
            aFireBallReanim.playReanim("anim_form", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 16.0f);
            aFireBallReanim.mIsAttachment = true;
            aFireBallReanim.assignRenderGroupToTrack("additive", Zombie.RENDER_GROUP_BOSS_FIREBALL_ADDITIVE);
            aFireBallReanim.assignRenderGroupToTrack("superglow", Zombie.RENDER_GROUP_BOSS_FIREBALL_ADDITIVE);
        } else {
            aFireBallReanim = z.mApp.addReanimation(455.0f, aPosY, z.mRenderOrder + 1, ReanimationType.REANIM_BOSS_ICEBALL);
            aFireBallReanim.playReanim("anim_form", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 16.0f);
            aFireBallReanim.mIsAttachment = true;
            aFireBallReanim.assignRenderGroupToTrack("ice_highlight", Zombie.RENDER_GROUP_BOSS_FIREBALL_ADDITIVE);
        }

        z.mBossFireBallReanimID = z.mApp.reanimationGetID(aFireBallReanim);
        z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID).playReanim("anim_laugh", ReanimLoopType.REANIM_LOOP, 20, 18.0f);
        z.mApp.playFoley(FoleyType.FOLEY_HYDRAULIC_SHORT);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9995-10042 (Zombie::BossHeadSpit).
     */
    public static void headSpit(Zombie z) {
        Reanimation aFireBallReanim = z.mApp.reanimationTryToGet(z.mBossFireBallReanimID);
        if (aFireBallReanim != null) {
            aFireBallReanim.reanimationDie();
            z.mBossFireBallReanimID = ReanimationID.REANIMATIONID_NULL;
        }

        z.mZombiePhase = ZombiePhase.PHASE_BOSS_HEAD_SPIT;
        // DO_FIX_BUGS adds a StageHas6Rows() pool-boss branch; upstream default is 0..4.
        z.mFireballRow = PvzpCommon.randRangeInt(0, 4);
        z.mIsFireBall = PvzpCommon.randRangeInt(0, 1) == 0;

        String aTrackName;
        switch (z.mFireballRow) {
            case 0:  aTrackName = "anim_head_attack_1"; break;
            case 1:  aTrackName = "anim_head_attack_2"; break;
            case 2:  aTrackName = "anim_head_attack_3"; break;
            case 3:  aTrackName = "anim_head_attack_4"; break;
            case 4:  aTrackName = "anim_head_attack_5"; break;
            // DO_FIX_BUGS uses "anim_head_attack_5"; upstream default asserts.
            default: assert false; aTrackName = "anim_head_attack_5"; break;
        }
        z.playZombieReanim(aTrackName, ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);

        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        if (z.mIsFireBall) {
            aBodyReanim.setImageOverride("Boss_eyeglow_red", null);
            aBodyReanim.setImageOverride("Boss_mouthglow_red", null);
        } else {
            aBodyReanim.setImageOverride("Boss_eyeglow_red", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_EYEGLOW_BLUE));
            aBodyReanim.setImageOverride("Boss_mouthglow_red", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_MOUTHGLOW_BLUE));
        }

        Reanimation aHeadReanim = z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID);
        aHeadReanim.playReanim("anim_drive", ReanimLoopType.REANIM_LOOP, 20, 36.0f);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10084-10112 (Zombie::BossHeadSpitEffect).
     */
    public static void headSpitEffect(Zombie z) {
        if (z.mIsFireBall) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            int aTrackIndex = aBodyReanim.findTrackIndex("Boss_jaw");
            ReanimatorTransform aTransform = new ReanimatorTransform();
            aBodyReanim.getCurrentTransform(aTrackIndex, aTransform);
            float aFlamePosX = z.mPosX + aTransform.mTransX + 100.0f;
            float aFlamePosY = z.mPosY + aTransform.mTransY + 50.0f;
            z.mApp.addPvzpParticle(aFlamePosX, aFlamePosY, z.mRenderOrder + 2, ParticleEffect.PARTICLE_ZOMBIE_BOSS_FIREBALL);
        } else {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            int aTrackIndex = aBodyReanim.findTrackIndex("Boss_jaw");
            ReanimatorTransform aTransform = new ReanimatorTransform();
            aBodyReanim.getCurrentTransform(aTrackIndex, aTransform);
            float aFlamePosX = z.mPosX + aTransform.mTransX + 100.0f;
            float aFlamePosY = z.mPosY + aTransform.mTransY + 50.0f;
            PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aFlamePosX, aFlamePosY, z.mRenderOrder + 2, ParticleEffect.PARTICLE_ZOMBIE_BOSS_FIREBALL);
            if (aParticle != null) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIE_BOSS_ICEBALL_PARTICLES));
            }
        }

        z.mApp.playFoley(FoleyType.FOLEY_BOSS_BOULDER_ATTACK);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10141-10206 (Zombie::UpdateBossFireball).
     */
    public static void updateFireball(Zombie z) {
        Reanimation aFireballReanim = z.mApp.reanimationTryToGet(z.mBossFireBallReanimID);
        if (aFireballReanim == null)
            return;

        float aSpeed = aFireballReanim.getTrackVelocity("_ground");
        aFireballReanim.mOverlayMatrix.m02 -= aSpeed;
        float aPosX = aFireballReanim.mOverlayMatrix.m02;
        float aPosY = z.mBoard.getPosYBasedOnRow(aPosX + 75.0f, z.mFireballRow) - 90.0f;
        aFireballReanim.mOverlayMatrix.m12 = aPosY;

        if (aPosX < -180.0f) {
            aFireballReanim.reanimationDie();
            z.mBossFireBallReanimID = ReanimationID.REANIMATIONID_NULL;
        }

        z.squishAllInSquare(z.mBoard.pixelToGridX((int) (aPosX + 75.0f), (int) aPosY), z.mFireballRow, ZombieAttackType.ATTACKTYPE_DRIVE_OVER);

        for (LawnMower aLawnMower : z.mBoard.mLawnMowers) {
            if (aLawnMower.mDead)
                continue;
            if (aLawnMower.mMowerState != LawnMowerState.MOWER_SQUISHED && aLawnMower.mRow == z.mFireballRow
                && aLawnMower.mPosX > aPosX && aLawnMower.mPosX < aPosX + 50.0f) {
                aLawnMower.squishMower();
            }
        }

        if (z.mIsFireBall) {
            if (aFireballReanim.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD && aFireballReanim.mLoopCount > 0) {
                aFireballReanim.playReanim("anim_role", ReanimLoopType.REANIM_LOOP, 0, 2.0f);
                aFireballReanim.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, z.mFireballRow, 0);
            }

            if (aFireballReanim.mLoopType == ReanimLoopType.REANIM_LOOP && Common.rand(10) == 0) {
                float aBallPosX = aPosX + 100.0f + PvzpCommon.randRangeFloat(0.0f, 20.0f);
                float aBallPosY = z.mBoard.getPosYBasedOnRow(aBallPosX - 40.0f, z.mFireballRow) + 90.0f + PvzpCommon.randRangeFloat(-50.0f, 0.0f);
                int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, z.mFireballRow, 6);
                z.mApp.addPvzpParticle(aBallPosX, aBallPosY, aRenderPosition, ParticleEffect.PARTICLE_FIREBALL_TRAIL);
            }
        } else {
            if (aFireballReanim.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD && aFireballReanim.mLoopCount > 0) {
                aFireballReanim.playReanim("anim_role", ReanimLoopType.REANIM_LOOP, 0, 2.0f);
                aFireballReanim.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, z.mFireballRow, 0);
            }

            if (aFireballReanim.mLoopType == ReanimLoopType.REANIM_LOOP && Common.rand(10) == 0) {
                float aBallPosX = aPosX + 100.0f + PvzpCommon.randRangeFloat(0.0f, 20.0f);
                float aBallPosY = z.mBoard.getPosYBasedOnRow(aBallPosX - 40.0f, z.mFireballRow) + 90.0f + PvzpCommon.randRangeFloat(-50.0f, 0.0f);
                int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, z.mFireballRow, 6);
                z.mApp.addPvzpParticle(aBallPosX, aBallPosY, aRenderPosition, ParticleEffect.PARTICLE_ICEBALL_TRAIL);
            }
        }

        aFireballReanim.update();
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10062-10082 (Zombie::BossDestroyFireball).
     */
    public static void destroyFireball(Zombie z) {
        Reanimation aFireBallReanim = z.mApp.reanimationTryToGet(z.mBossFireBallReanimID);
        if (aFireBallReanim != null && z.mIsFireBall) {
            float aPosX = aFireBallReanim.mOverlayMatrix.m02 + 80.0f;
            float aPosY = aFireBallReanim.mOverlayMatrix.m12 + 40.0f;
            for (int i = 0; i < 6; i++) {
                float aAngle = (float) (2 * Math.PI * i / 6 + Math.PI / 2);
                Reanimation aReanim = z.mApp.addReanimation(
                    aPosX + 60.0f * (float) Math.sin(aAngle),
                    aPosY + 60.0f * (float) Math.cos(aAngle),
                    400000, ReanimationType.REANIM_JALAPENO_FIRE);
                aReanim.mAnimTime = 0.2f;
                aReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME;
                aReanim.mAnimRate = PvzpCommon.randRangeFloat(20.0f, 25.0f);
            }

            aFireBallReanim.reanimationDie();
            z.mBossFireBallReanimID = ReanimationID.REANIMATIONID_NULL;
            z.mBoard.removeParticleByType(ParticleEffect.PARTICLE_FIREBALL_TRAIL);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10044-10060 (Zombie::BossDestroyIceballInRow).
     *
     * <p>The upstream {@code theRow} parameter was removed; the row check happens at the call site.
     */
    public static void destroyIceballInRow(Zombie z) {
        Reanimation aFireBallReanim = z.mApp.reanimationTryToGet(z.mBossFireBallReanimID);
        if (aFireBallReanim != null && !z.mIsFireBall) {
            float aPosX = aFireBallReanim.mOverlayMatrix.m02 + 80.0f;
            float aPosY = aFireBallReanim.mOverlayMatrix.m12 + 80.0f;
            z.mApp.addPvzpParticle(aPosX, aPosY, 400000, ParticleEffect.PARTICLE_ICEBALL_DEATH);

            aFireBallReanim.reanimationDie();
            z.mBossFireBallReanimID = ReanimationID.REANIMATIONID_NULL;
            z.mBoard.removeParticleByType(ParticleEffect.PARTICLE_ICEBALL_TRAIL);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10208-10218 (Zombie::BossStartDeath).
     */
    public static void startDeath(Zombie z) {
        z.mZombiePhase = ZombiePhase.PHASE_BOSS_HEAD_LEAVE;
        z.playZombieReanim("anim_head_leave", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);

        z.mApp.addPvzpParticle(700.0f, 150.0f, 400000, ParticleEffect.PARTICLE_BOSS_EXPLOSION);
        z.mApp.playSample(SoundType.SOUND_BOSSEXPLOSION);
        z.mApp.playFoley(FoleyType.FOLEY_GARGANTUDEATH);

        die(z);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10220-10473 (Zombie::UpdateBoss).
     */
    public static void updateBoss(Zombie z) {
        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (z.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.24f) || aBodyReanim.shouldTriggerTimedEvent(0.79f)) {
                z.mApp.playFoley(FoleyType.FOLEY_THUMP);
                z.mBoard.shakeBoard(1, 4);
            }
            return;
        }

        Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
        updateFireball(z);
        if (z.mIceTrapCounter == 0) {
            if (z.mSummonCounter > 0) {
                z.mSummonCounter--;
            }
            if (z.mBossBungeeCounter > 0) {
                z.mBossBungeeCounter--;
            }
            if (z.mBossStompCounter > 0) {
                z.mBossStompCounter--;
            }
            if (z.mBossHeadCounter > 0) {
                z.mBossHeadCounter--;
            }

            if (z.mChilledCounter > 0) {
                aHeadReanim.mAnimRate = 6.0f;
            } else if (aHeadReanim.mAnimRate == 0.0f) {
                aHeadReanim.mAnimRate = 12.0f;
            }
        } else {
            aHeadReanim.mAnimRate = 0.0f;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_ENTER) {
            playIdle(z);
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_IDLE) {
            if (z.mBodyHealth == 1) {
                z.playDeathAnim(0);
                return;
            }

            if (z.mPhaseCounter > 0)
                return;

            int aDamageIndex = z.getBodyDamageIndex();
            if (aDamageIndex != z.mBossMode) {
                z.mBossMode = aDamageIndex;
                if (z.mBossMode == 1) {  // on entering damage stage 1, immediately release bungees once
                    bungeeAttack(z);
                } else {  // on entering damage stage 2, immediately throw an RV once
                    rVAttack(z);
                }
            } else if (z.mBossStompCounter == 0) {
                stompAttack(z);
            } else if (z.mBossBungeeCounter == 0) {
                if (Common.rand(z.mApp.isAdventureMode() ? 4 : 2) == 0) {  // 1/2 chance to throw an RV (1/4 in adventure mode), otherwise release bungees
                    z.mBossBungeeCounter = PvzpCommon.randRangeInt(4000, 5000);
                    rVAttack(z);
                } else {
                    bungeeAttack(z);
                }
            } else if (z.mBossHeadCounter == 0) {
                headAttack(z);
            } else if (z.mSummonCounter == 0) {
                spawnAttack(z);
            } else {
                z.mPhaseCounter = PvzpCommon.randRangeInt(100, 200);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_SPAWNING) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.6f)) {
                spawnContact(z);
            }

            if (aBodyReanim.mLoopCount > 0) {
                playIdle(z);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_STOMPING) {
            float aTrigger = 0.5f;
            if (z.mTargetRow >= 2) {
                aTrigger = 0.55f;
            }
            if (aBodyReanim.shouldTriggerTimedEvent(aTrigger)) {
                stompContact(z);
            }

            if (aBodyReanim.mLoopCount > 0) {
                playIdle(z);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_BUNGEES_ENTER) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.4f)) {
                bungeeSpawn(z);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_BUNGEES_DROP) {
            if (areBungeesDone(z)) {
                bungeeLeave(z);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_BUNGEES_LEAVE) {
            if (aBodyReanim.mLoopCount > 0) {
                playIdle(z);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_DROP_RV) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.65f)) {
                rVLanding(z);
            }

            if (aBodyReanim.mLoopCount > 0) {
                playIdle(z);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_ENTER) {
            if (z.getBodyDamageIndex() == 2 && aBodyReanim.shouldTriggerTimedEvent(0.37f)) {
                applySmokeParticles(z, true);
            }

            if (aBodyReanim.shouldTriggerTimedEvent(0.55f)) {
                z.mApp.playFoley(FoleyType.FOLEY_HYDRAULIC);
            }

            if (aBodyReanim.mLoopCount > 0) {
                z.mZombiePhase = ZombiePhase.PHASE_BOSS_HEAD_IDLE_BEFORE_SPIT;
                z.playZombieReanim("anim_head_idle", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
                z.mPhaseCounter = 500;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_IDLE_BEFORE_SPIT) {
            if (z.mBodyHealth == 1) {
                startDeath(z);
            } else if (z.mPhaseCounter == 0) {
                headSpit(z);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_SPIT) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.37f)) {
                headSpitEffect(z);
            }

            if (aBodyReanim.shouldTriggerTimedEvent(0.42f)) {
                headSpitContact(z);
            }

            if (aBodyReanim.mLoopCount > 0) {
                aHeadReanim = z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID);
                aHeadReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 20, 18.0f);
                z.mZombiePhase = ZombiePhase.PHASE_BOSS_HEAD_IDLE_AFTER_SPIT;
                z.playZombieReanim("anim_head_idle", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
                z.mPhaseCounter = 300;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_IDLE_AFTER_SPIT) {
            if (z.mBodyHealth == 1) {
                startDeath(z);
            } else if (z.mPhaseCounter == 0) {
                z.mZombiePhase = ZombiePhase.PHASE_BOSS_HEAD_LEAVE;
                z.playZombieReanim("anim_head_leave", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 12.0f);
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_LEAVE) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.23f)) {
                z.mChilledCounter = 0;
                z.updateAnimSpeed();
            }

            if (aBodyReanim.shouldTriggerTimedEvent(0.48f) || aBodyReanim.shouldTriggerTimedEvent(0.8f)) {
                z.mApp.playFoley(FoleyType.FOLEY_THUMP);
            }

            if (aBodyReanim.mLoopCount > 0) {
                applySmokeParticles(z, false);
                playIdle(z);
            }
        } else {
            assert false;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10592-10626 (Zombie::ApplyBossSmokeParticles).
     */
    public static void applySmokeParticles(Zombie z, boolean theEnable) {
        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        ReanimatorTrackInstance aTrackInstance = aBodyReanim.getTrackInstanceByName("Boss_head");
        Attachment.attachmentDetachCrossFadeParticleType(aTrackInstance.mAttachmentID, ParticleEffect.PARTICLE_ZAMBONI_SMOKE, null);

        if (theEnable) {
            PvzpParticleSystem aParticle1 = z.mApp.addPvzpParticle(0.0f, 0.0f, 0, ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
            PvzpParticleSystem aParticle2 = z.mApp.addPvzpParticle(0.0f, 0.0f, 0, ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
            if (aParticle1 != null) {
                AttachEffect aAttachEffect = aBodyReanim.attachParticleToTrack("Boss_head", aParticle1, 120.0f, 30.0f);
                aAttachEffect.mDontDrawIfParentHidden = true;
                aAttachEffect.mDontPropogateColor = true;
            }
            if (aParticle2 != null) {
                AttachEffect aAttachEffect = aBodyReanim.attachParticleToTrack("Boss_head", aParticle2, 205.0f, 58.0f);
                aAttachEffect.mDontDrawIfParentHidden = true;
                aAttachEffect.mDontPropogateColor = true;
            }

            if (z.mBodyHealth < z.mBodyMaxHealth / Zombie.BOSS_FLASH_HEALTH_FRACTION) {
                PvzpParticleSystem aParticle3 = z.mApp.addPvzpParticle(0.0f, 0.0f, 0, ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
                if (aParticle3 != null) {
                    AttachEffect aAttachEffect = aBodyReanim.attachParticleToTrack("Boss_head", aParticle3, 193.0f, 27.0f);
                    aAttachEffect.mDontDrawIfParentHidden = true;
                    aAttachEffect.mDontPropogateColor = true;
                }
            }
        }
    }
}
