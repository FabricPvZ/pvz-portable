/*
 * Ported from src/Lawn/Zombie.cpp (status/death/loot helpers).
 *
 * Static helpers called by Zombie.java delegators. See local://zombie-port-contract.md for the
 * owner map and the peer API.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;

public final class ZombieStatus {

    private ZombieStatus() {
    }

    /** Ported from src/Lawn/Zombie.cpp:3072-3078 (Zombie::DragUnder). */
    public static void dragUnder(Zombie z) {
        z.mZombieHeight = ZombieHeight.HEIGHT_DRAGGED_UNDER;
        z.stopEating();
        z.reanimReenableClipping();
    }

    /** Ported from src/Lawn/Zombie.cpp:3342-3348 (Zombie::OverrideParticleScale). */
    public static void overrideParticleScale(Zombie z, PvzpParticleSystem aParticle) {
        if (aParticle != null) {
            aParticle.overrideScale(null, z.mScaleZombie);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3350-3366 (Zombie::OverrideParticleColor). */
    public static void overrideParticleColor(Zombie z, PvzpParticleSystem aParticle) {
        if (aParticle != null) {
            if (z.mMindControlled) {
                aParticle.overrideColor(null, Zombie.ZOMBIE_MINDCONTROLLED_COLOR);
                aParticle.overrideExtraAdditiveDraw(null, true);
            } else if (z.mChilledCounter > 0 || z.mIceTrapCounter > 0) {
                aParticle.overrideColor(null, new cn.enaium.fabricpvz.portable.sexy.Color(75, 75, 255, 255));
                aParticle.overrideExtraAdditiveDraw(null, true);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3367-3384 (Zombie::DropFlag). */
    public static void dropFlag(Zombie z) {
        if (z.mZombieType != ZombieType.ZOMBIE_FLAG || !z.mHasObject) {
            return;
        }

        z.mApp.removeReanimation(z.mSpecialHeadReanimID);
        z.reanimShowPrefix("anim_innerarm", Reanimation.RENDER_GROUP_NORMAL);
        z.reanimShowTrack("Zombie_flaghand", Reanimation.RENDER_GROUP_HIDDEN);
        z.reanimShowTrack("Zombie_innerarm_screendoor", Reanimation.RENDER_GROUP_HIDDEN);
        z.mHasObject = false;

        float[] aFlagPos = new float[2];
        z.getTrackPosition("Zombie_flaghand", aFlagPos);
        PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aFlagPos[0] + 6.0f, aFlagPos[1] - 45.0f,
                z.mRenderOrder + 1, ParticleEffect.PARTICLE_ZOMBIE_FLAG);
        overrideParticleColor(z, aParticle);
        overrideParticleScale(z, aParticle);
    }

    /** Upstream {@code RENDER_GROUP_ZOMBATAR_HEAD} (src/Lawn/Zombie.cpp:53). */
    private static final int RENDER_GROUP_ZOMBATAR_HEAD = 3;

    /**
     * One entry of the {@code aRuntimeParts} table in Zombie::ApplyZombatarHead
     * (src/Lawn/Zombie.cpp:3415-3424): {@code {mPartSlot, mColorSlot, mMaxCount, mPrefix, mPage,
     * mRemapAccessory, mCompactTrackRange}}.
     */
    private static final class RuntimePart {
        final int mPartSlot;
        final int mColorSlot;
        final int mMaxCount;
        final String mPrefix;
        final Zombatar.ZombatarPage mPage;
        final boolean mRemapAccessory;
        final boolean mCompactTrackRange;

        RuntimePart(int thePartSlot, int theColorSlot, int theMaxCount, String thePrefix,
                Zombatar.ZombatarPage thePage, boolean theRemapAccessory, boolean theCompactTrackRange) {
            mPartSlot = thePartSlot;
            mColorSlot = theColorSlot;
            mMaxCount = theMaxCount;
            mPrefix = thePrefix;
            mPage = thePage;
            mRemapAccessory = theRemapAccessory;
            mCompactTrackRange = theCompactTrackRange;
        }
    }

    /** The {@code aRuntimeParts} table, in upstream order (src/Lawn/Zombie.cpp:3415-3424). */
    private static final RuntimePart[] RUNTIME_PARTS = {
        new RuntimePart(Zombatar.ZOMBATAR_SLOT_HATS, Zombatar.ZOMBATAR_SLOT_HATS_COLOR, 14, "hats_",
                Zombatar.ZombatarPage.HATS, false, false),
        new RuntimePart(Zombatar.ZOMBATAR_SLOT_HAIR, Zombatar.ZOMBATAR_SLOT_HAIR_COLOR, 16, "hair_",
                Zombatar.ZombatarPage.HAIR, false, false),
        new RuntimePart(Zombatar.ZOMBATAR_SLOT_TIDBITS, Zombatar.ZOMBATAR_SLOT_TIDBITS_COLOR, 14, "tidBits_",
                Zombatar.ZombatarPage.TIDBITS, false, false),
        new RuntimePart(Zombatar.ZOMBATAR_SLOT_EYEWEAR, Zombatar.ZOMBATAR_SLOT_EYEWEAR_COLOR, 16, "eyeWear_",
                Zombatar.ZombatarPage.EYEWEAR, false, false),
        new RuntimePart(Zombatar.ZOMBATAR_SLOT_ACCESSORY, Zombatar.ZOMBATAR_SLOT_ACCESSORY_COLOR, 15, "accessories_",
                Zombatar.ZombatarPage.ACCESSORY, true, false),
        new RuntimePart(Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR, Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR_COLOR, 25, "facialHair_",
                Zombatar.ZombatarPage.FACIAL_HAIR, false, true),
    };

    /** Ported from src/Lawn/Zombie.cpp:3385-3463 (Zombie::ApplyZombatarHead). */
    public static void applyZombatarHead(Zombie z, byte[] theRecord) {
        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        ReanimatorTrackInstance aTrackInstance = aBodyReanim.getTrackInstanceByName("anim_head1");
        aTrackInstance.mImageOverride = Resources.getImageById(ImageID.IMAGE_BLANK);
        aBodyReanim.assignRenderGroupToTrack("anim_head1", RENDER_GROUP_ZOMBATAR_HEAD);
        aBodyReanim.assignRenderGroupToPrefix("anim_head2", Reanimation.RENDER_GROUP_HIDDEN);
        aBodyReanim.assignRenderGroupToPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
        aBodyReanim.mFrameBasePose = 0;

        Reanimation aHeadReanim = z.mApp.reanimationTryToGet(z.mZombatarHeadReanimID);
        if (aHeadReanim == null) {
            aHeadReanim = z.mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_ZOMBATAR_HEAD);
            aHeadReanim.playReanim("anim_head_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            z.mZombatarHeadReanimID = z.mApp.reanimationGetID(aHeadReanim);
            AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 0.0f, 0.0f);
            PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, -20.0f, -1.0f, 0.2f, 1.0f, 1.0f);
        }

        aHeadReanim.assignRenderGroupToTrack("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
        aHeadReanim.assignRenderGroupToPrefix("hats_", Reanimation.RENDER_GROUP_HIDDEN);
        aHeadReanim.assignRenderGroupToPrefix("hair_", Reanimation.RENDER_GROUP_HIDDEN);
        aHeadReanim.assignRenderGroupToPrefix("facialHair_", Reanimation.RENDER_GROUP_HIDDEN);
        aHeadReanim.assignRenderGroupToPrefix("accessories_", Reanimation.RENDER_GROUP_HIDDEN);
        aHeadReanim.assignRenderGroupToPrefix("eyeWear_", Reanimation.RENDER_GROUP_HIDDEN);
        aHeadReanim.assignRenderGroupToPrefix("tidBits_", Reanimation.RENDER_GROUP_HIDDEN);

        for (RuntimePart aPart : RUNTIME_PARTS) {
            int aPartIndex = Zombatar.readSignedRecordSlot(theRecord, aPart.mPartSlot);
            if (aPartIndex < 0 || aPartIndex >= aPart.mMaxCount) {
                continue;
            }
            int aTrackIndex = aPartIndex;
            if (aPart.mCompactTrackRange && aTrackIndex > 16) {
                aTrackIndex -= aTrackIndex / 17;
            }
            if (aPart.mRemapAccessory) {
                aTrackIndex = Zombatar.remapAccessoryForRuntime(aTrackIndex);
            }
            String aTrackName = Zombatar.trackName(aPart.mPrefix, aTrackIndex);

            Zombatar.ZombatarPartLayout aLayout = Zombatar.getPartLayout(aPart.mPage, aPartIndex);
            int aDrawOrder = aLayout != null ? aLayout.mDrawOrder : 0;
            if (aHeadReanim.trackExists(aTrackName)) {
                aHeadReanim.assignRenderGroupToTrack(aTrackName, aDrawOrder);
                aHeadReanim.getTrackInstanceByName(aTrackName).mTrackColor =
                        Zombatar.getColor(Zombatar.readSignedRecordSlot(theRecord, aPart.mColorSlot));
            }

            // some parts exist only as a "_line" detail track without a base track
            String aLineTrackName = aTrackName + "_line";
            if (aHeadReanim.trackExists(aLineTrackName)) {
                aHeadReanim.assignRenderGroupToTrack(aLineTrackName, aDrawOrder + 1);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3465-3477 (Zombie::SetupZombatarFlagReanim). */
    public static void setupZombatarFlagReanim(Zombie z, int theRecordIndex) {
        if (theRecordIndex < 0) {
            return;
        }

        PlayerInfo aPlayerInfo = z.mApp.mPlayerInfo;
        if (aPlayerInfo == null || aPlayerInfo.mZombatarData.length == 0) {
            return;
        }

        byte[] aRecord = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
        System.arraycopy(aPlayerInfo.mZombatarData, theRecordIndex * PlayerInfo.ZOMBATAR_RECORD_SIZE, aRecord, 0,
                PlayerInfo.ZOMBATAR_RECORD_SIZE);
        applyZombatarHead(z, aRecord);
    }

    /** Ported from src/Lawn/Zombie.cpp:3478-3487 (Zombie::DropPole). */
    public static void dropPole(Zombie z) {
        if (z.mZombieType != ZombieType.ZOMBIE_POLEVAULTER) {
            return;
        }

        z.reanimShowPrefix("Zombie_polevaulter_innerarm", Reanimation.RENDER_GROUP_HIDDEN);
        z.reanimShowPrefix("Zombie_polevaulter_innerhand", Reanimation.RENDER_GROUP_HIDDEN);
        z.reanimShowPrefix("Zombie_polevaulter_pole", Reanimation.RENDER_GROUP_HIDDEN);
    }

    /** Ported from src/Lawn/Zombie.cpp:4295-4302 (Zombie::UpdateBurn). */
    public static void updateBurn(Zombie z) {
        z.mPhaseCounter--;
        if (z.mPhaseCounter == 0) {
            dieWithLoot(z);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:4688-4705 (Zombie::HasYuckyFaceImage). */
    public static boolean hasYuckyFaceImage(Zombie z) {
        if (z.mBoard.mFutureMode) {
            return false;
        }

        return z.mZombieType == ZombieType.ZOMBIE_NORMAL
                || z.mZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE
                || z.mZombieType == ZombieType.ZOMBIE_PAIL
                || z.mZombieType == ZombieType.ZOMBIE_FLAG
                || z.mZombieType == ZombieType.ZOMBIE_DOOR
                || z.mZombieType == ZombieType.ZOMBIE_DUCKY_TUBE
                || z.mZombieType == ZombieType.ZOMBIE_DANCER
                || z.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER
                || z.mZombieType == ZombieType.ZOMBIE_NEWSPAPER
                || z.mZombieType == ZombieType.ZOMBIE_POLEVAULTER;
    }

    /** Ported from src/Lawn/Zombie.cpp:4706-4733 (Zombie::ShowYuckyFace). */
    public static void showYuckyFace(Zombie z, boolean theShow) {
        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        if (hasYuckyFaceImage(z)) {
            if (theShow) {
                aBodyReanim.setImageOverride("anim_head1",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_GROSSOUT));
                aBodyReanim.assignRenderGroupToTrack("anim_head2", Reanimation.RENDER_GROUP_HIDDEN);
                aBodyReanim.assignRenderGroupToTrack("anim_head_jaw", Reanimation.RENDER_GROUP_HIDDEN);
                aBodyReanim.assignRenderGroupToTrack("anim_tongue", Reanimation.RENDER_GROUP_HIDDEN);
            } else if (z.mHasHead) {
                aBodyReanim.setImageOverride("anim_head1", null);
                aBodyReanim.assignRenderGroupToTrack("anim_head2", Reanimation.RENDER_GROUP_NORMAL);
                aBodyReanim.assignRenderGroupToTrack("anim_head_jaw", Reanimation.RENDER_GROUP_NORMAL);
                if (z.mVariant) {
                    aBodyReanim.assignRenderGroupToTrack("anim_tongue", Reanimation.RENDER_GROUP_NORMAL);
                }
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:4734-4822 (Zombie::UpdateYuckyFace). */
    public static void updateYuckyFace(Zombie z) {
        z.mYuckyFaceCounter++;
        if (z.mYuckyFaceCounter > 20 && z.mYuckyFaceCounter < 170 && !hasYuckyFaceImage(z)) {
            z.stopEating();
            z.mYuckyFaceCounter = 170;
            if (z.mBoard.countZombiesOnScreen() <= 5 && z.mHasHead) {
                z.mApp.playFoley(FoleyType.FOLEY_YUCK);
            } else if (z.mBoard.countZombiesOnScreen() <= 10 && z.mHasHead && Common.rand(2) == 0) {
                z.mApp.playFoley(FoleyType.FOLEY_YUCK);
            }
        }

        if (z.mYuckyFaceCounter > 270) {
            showYuckyFace(z, false);
            z.mYuckyFace = false;
            z.mYuckyFaceCounter = 0;
            return;
        }

        if (z.mYuckyFaceCounter == 70) {
            z.stopEating();
            showYuckyFace(z, true);
            if (z.mBoard.countZombiesOnScreen() <= 5 && z.mHasHead) {
                z.mApp.playFoley(FoleyType.FOLEY_YUCK);
            } else if (z.mBoard.countZombiesOnScreen() <= 10 && z.mHasHead && Common.rand(2) == 0) {
                z.mApp.playFoley(FoleyType.FOLEY_YUCK);
            }
        }
        if (z.mYuckyFaceCounter == 170) {
            z.startWalkAnim(20);

            boolean aCanGoUp = true;
            boolean aCanGoDown = true;
            boolean aIsPool = z.mBoard.mPlantRow[z.mRow] == PlantRowType.PLANTROW_POOL;
            if (!z.mBoard.rowCanHaveZombies(z.mRow - 1)) {
                aCanGoUp = false;
            } else if (z.mBoard.mPlantRow[z.mRow - 1] == PlantRowType.PLANTROW_POOL && !aIsPool) {
                aCanGoUp = false;
            } else if (z.mBoard.mPlantRow[z.mRow - 1] != PlantRowType.PLANTROW_POOL && aIsPool) {
                aCanGoUp = false;
            }
            if (!z.mBoard.rowCanHaveZombies(z.mRow + 1)) {
                aCanGoDown = false;
            } else if (z.mBoard.mPlantRow[z.mRow + 1] == PlantRowType.PLANTROW_POOL && !aIsPool) {
                aCanGoDown = false;
            } else if (z.mBoard.mPlantRow[z.mRow + 1] != PlantRowType.PLANTROW_POOL && aIsPool) {
                aCanGoDown = false;
            }

            if (aCanGoDown && !aCanGoUp) {
                z.setRow(z.mRow + 1);
            } else if (!aCanGoDown && aCanGoUp) {
                z.setRow(z.mRow - 1);
            } else if (aCanGoDown && aCanGoUp) {
                z.setRow(Common.rand(2) == 0 ? z.mRow + 1 : z.mRow - 1);
            } else {
                throw new AssertionError();
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6498-6533 (Zombie::ZamboniDeath). */
    public static void zamboniDeath(Zombie z, int theDamageFlags) {
        if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_SPIKE)) {
            z.mFlatTires = true;
            z.mApp.playFoley(FoleyType.FOLEY_TIRE_POP);
            z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_DYING;
            z.mApp.addPvzpParticle(z.mPosX + 29.0f, z.mPosY + 114.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZAMBONI_TIRE);
            z.mVelX = 0.0f;

            if (Common.rand(4) == 0 && z.mPosX < 600.0f) {
                z.playZombieReanim("anim_wheelie2", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 10.0f);
                z.mPhaseCounter = 280;
            } else {
                Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
                PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(0.0f, 0.0f, 0,
                        ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
                if (aParticle != null) {
                    aBodyReanim.attachParticleToTrack("zombie_zamboni_1", aParticle, 35.0f, 85.0f);
                }

                z.mPhaseCounter = 280;
                z.playZombieReanim("anim_wheelie1", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 12.0f);
            }
        } else {
            z.mApp.addPvzpParticle(z.mPosX + 80.0f, z.mPosY + 60.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION);
            dieWithLoot(z);
            z.mApp.playFoley(FoleyType.FOLEY_EXPLOSION);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6534-6554 (Zombie::CatapultDeath). */
    public static void catapultDeath(Zombie z, int theDamageFlags) {
        if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_SPIKE)) {
            z.mApp.playFoley(FoleyType.FOLEY_TIRE_POP);
            z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_DYING;
            z.mApp.addPvzpParticle(z.mPosX + 29.0f, z.mPosY + 114.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZAMBONI_TIRE);
            z.mVelX = 0.0f;

            z.addAttachedParticle(47, 77, ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
            z.mPhaseCounter = 280;
            z.playZombieReanim("anim_bounce", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 12.0f);
        } else {
            z.mApp.addPvzpParticle(z.mPosX + 80.0f, z.mPosY + 60.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_CATAPULT_EXPLOSION);
            dieWithLoot(z);
            z.mApp.playFoley(FoleyType.FOLEY_EXPLOSION);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6697-6714 (Zombie::ConvertToNormalZombie). */
    public static void convertToNormalZombie(Zombie z) {
        stopZombieSound(z);
        z.mPosY = z.getPosYBasedOnRow(z.mRow);
        z.mX = (int) z.mPosX;
        z.mY = (int) z.mPosY;

        z.mZombieType = ZombieType.ZOMBIE_NORMAL;
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        z.mZombieAttackRect = new Rect(50, 0, 20, 115);

        z.mAnimFrames = 12;
        z.mAnimTicksPerFrame = 12;
        z.mPhaseCounter = 0;

        z.pickRandomSpeed();
    }

    /** Ported from src/Lawn/Zombie.cpp:6984-7025 (Zombie::StartMindControlled). */
    public static void startMindControlled(Zombie z) {
        z.mApp.playSample(SoundType.SOUND_MINDCONTROLLED);
        z.mMindControlled = true;
        z.mLastPortalX = -1;

        if (z.mZombieType == ZombieType.ZOMBIE_DANCER) {
            for (int i = 0; i < Zombie.NUM_BACKUP_DANCERS; i++) {
                z.mFollowerZombieID[i] = ZombieID.ZOMBIEID_NULL;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
            Zombie aLeader = z.mBoard.zombieTryToGet(z.mRelatedZombieID);
            if (aLeader != null) {
                int aId = z.mBoard.zombieGetID(z);
                for (int i = 0; i < Zombie.NUM_BACKUP_DANCERS; i++) {
                    if (aLeader.mFollowerZombieID[i] == aId) {
                        aLeader.mFollowerZombieID[i] = ZombieID.ZOMBIEID_NULL;
                        break;
                    }
                }
            }

            z.mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
        } else {
            Zombie aZombie = z.mBoard.zombieTryToGet(z.mRelatedZombieID);
            if (aZombie != null) {
                aZombie.mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
                z.mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7137-7276 (Zombie::TrySpawnLevelAward). */
    public static boolean trySpawnLevelAward(Zombie z) {
        if (!z.isOnBoard() || z.mBoard.hasLevelAwardDropped() || z.mBoard.mLevelComplete || z.mDroppedLoot) {
            return false;
        }

        if (z.mApp.isFinalBossLevel()) {
            if (z.mZombieType != ZombieType.ZOMBIE_BOSS) {
                return false;
            }
        } else if (z.mApp.isScaryPotterLevel()) {
            if (z.mBoard.mChallenge.scaryPotterIsCompleted() == 0) {
                return false;
            }
        } else if (z.mApp.isContinuousChallenge() || z.mBoard.mCurrentWave < z.mBoard.mNumWaves
                || z.mBoard.areEnemyZombiesOnScreen()) {
            return false;
        }

        if (z.mApp.isWhackAZombieLevel() && z.mBoard.mZombieCountDown > 0) {
            return false;
        }

        z.mBoard.mLevelAwardSpawned = true;
        z.mApp.mBoardResult = BoardResult.BOARDRESULT_WON;

        Rect aZombieRect = z.getZombieRect();
        int aCenterX = aZombieRect.mX + aZombieRect.mWidth / 2;
        int aCenterY = aZombieRect.mY + aZombieRect.mHeight / 2;

        if (!z.mBoard.isSurvivalStageWithRepick()) {
            z.mBoard.removeAllZombies();
        }

        int aCoinType;
        if (z.mApp.isScaryPotterLevel() && !z.mBoard.isFinalScaryPotterStage()) {
            aCoinType = CoinType.COIN_NONE;
            z.mBoard.mChallenge.puzzlePhaseComplete(
                    z.mBoard.pixelToGridXKeepOnBoard((int) (z.mPosX + 75.0f), (int) z.mPosY), z.mRow);
        } else if (z.mApp.isAdventureMode() && z.mBoard.mLevel <= 50) {
            if (z.mBoard.mLevel == 9 || z.mBoard.mLevel == 19 || z.mBoard.mLevel == 29
                    || z.mBoard.mLevel == 39 || z.mBoard.mLevel == 49) {
                aCoinType = CoinType.COIN_NOTE;
            } else if (z.mBoard.mLevel == 50) {
                aCoinType = z.mApp.hasFinishedAdventure()
                        ? CoinType.COIN_AWARD_MONEY_BAG : CoinType.COIN_AWARD_SILVER_SUNFLOWER;
            } else if (z.mApp.hasFinishedAdventure()) {
                aCoinType = CoinType.COIN_AWARD_MONEY_BAG;
            } else if (z.mBoard.mLevel == 4) {
                aCoinType = CoinType.COIN_SHOVEL;
            } else if (z.mBoard.mLevel == 14) {
                aCoinType = CoinType.COIN_ALMANAC;
            } else if (z.mBoard.mLevel == 24) {
                aCoinType = CoinType.COIN_CARKEYS;
            } else if (z.mBoard.mLevel == 34) {
                aCoinType = CoinType.COIN_TACO;
            } else if (z.mBoard.mLevel == 44) {
                aCoinType = CoinType.COIN_WATERING_CAN;
            } else {
                aCoinType = CoinType.COIN_FINAL_SEED_PACKET;
            }
        } else if (z.mBoard.isSurvivalStageWithRepick()) {
            aCoinType = CoinType.COIN_NONE;
            z.mBoard.fadeOutLevel();
        } else if (z.mBoard.isLastStandStageWithRepick()) {
            aCoinType = CoinType.COIN_NONE;

            z.mBoard.fadeOutLevel();
            z.mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
            for (int i = 0; i < 10; i++) {
                z.mBoard.addCoin(aCenterX + i * 5, aCenterY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
            }
        } else if (!z.mApp.isAdventureMode()) {
            if (z.mApp.hasBeatenChallenge(z.mApp.mGameMode)) {
                aCoinType = CoinType.COIN_AWARD_MONEY_BAG;
            } else if (z.mApp.trophiesNeedForGoldSunflower() == 1) {
                aCoinType = CoinType.COIN_AWARD_GOLD_SUNFLOWER;
            } else {
                aCoinType = CoinType.COIN_TROPHY;
            }
        } else {
            aCoinType = CoinType.COIN_AWARD_MONEY_BAG;
        }

        int aCoinMotion = CoinMotion.COIN_MOTION_COIN;
        if (z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            aCoinMotion = CoinMotion.COIN_MOTION_FROM_BOSS;
        }

        if (aCoinType != CoinType.COIN_NONE) {
            z.mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
            z.mBoard.addCoin(aCenterX, aCenterY, aCoinType, aCoinMotion);
        }

        z.mDroppedLoot = true;
        return true;
    }

    /** Ported from src/Lawn/Zombie.cpp:7277-7319 (Zombie::DropLoot). */
    public static void dropLoot(Zombie z) {
        if (!z.isOnBoard()) {
            return;
        }

        cn.enaium.fabricpvz.portable.lawn.widget.AlmanacDialog.almanacPlayerDefeatedZombie(z.mZombieType);
        if (z.mZombieType == ZombieType.ZOMBIE_YETI) {
            z.mBoard.mKilledYeti = true;
        }

        trySpawnLevelAward(z);
        if (z.mDroppedLoot || z.mBoard.hasLevelAwardDropped() || !z.mBoard.canDropLoot()) {
            return;
        }

        z.mDroppedLoot = true;
        int aZombieValue = Zombie.getZombieDefinition(z.mZombieType).mZombieValue;
        if (z.mApp.isLittleTroubleLevel() && Common.rand(4) != 0) {
            return;
        }
        if (z.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM || z.mApp.isIZombieLevel()) {
            return;
        }

        Rect aZombieRect = z.getZombieRect();
        int aCenterX = aZombieRect.mX + aZombieRect.mWidth / 2;
        int aCenterY = aZombieRect.mY + aZombieRect.mHeight / 4;
        if (z.mZombieType == ZombieType.ZOMBIE_YETI) {
            z.mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
            z.mBoard.addCoin(aCenterX - 20, aCenterY, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
            z.mBoard.addCoin(aCenterX - 30, aCenterY, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
            z.mBoard.addCoin(aCenterX - 40, aCenterY, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
            z.mBoard.addCoin(aCenterX - 50, aCenterY, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
        } else {
            z.mBoard.dropLootPiece(aCenterX, aCenterY, aZombieValue);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7320-7325 (Zombie::DieWithLoot). */
    public static void dieWithLoot(Zombie z) {
        dieNoLoot(z);
        dropLoot(z);
    }

    /** Ported from src/Lawn/Zombie.cpp:7326-7354 (Zombie::BobsledDie). */
    public static void bobsledDie(Zombie z) {
        if (!z.isBobsledTeamWithSled() || !z.isOnBoard()) {
            return;
        }

        Zombie aLeaderZombie;
        if (z.mRelatedZombieID == ZombieID.ZOMBIEID_NULL) {
            aLeaderZombie = z;
        } else {
            aLeaderZombie = z.mBoard.zombieGet(z.mRelatedZombieID);
        }

        if (!aLeaderZombie.mDead) {
            dieNoLoot(aLeaderZombie);
        }
        for (int i = 0; i < Zombie.NUM_BOBSLED_FOLLOWERS; i++) {
            Zombie aZombie = z.mBoard.zombieGet(aLeaderZombie.mFollowerZombieID[i]);
            if (!aZombie.mDead) {
                dieNoLoot(aZombie);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7355-7376 (Zombie::BobsledBurn). */
    public static void bobsledBurn(Zombie z) {
        if (!z.isBobsledTeamWithSled()) {
            return;
        }

        Zombie aLeaderZombie;
        if (z.mRelatedZombieID == ZombieID.ZOMBIEID_NULL) {
            aLeaderZombie = z;
        } else {
            aLeaderZombie = z.mBoard.zombieGet(z.mRelatedZombieID);
        }

        applyBurn(aLeaderZombie);
        for (int i = 0; i < Zombie.NUM_BOBSLED_FOLLOWERS; i++) {
            dieNoLoot(z.mBoard.zombieGet(aLeaderZombie.mFollowerZombieID[i]));
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7377-7397 (Zombie::BungeeDropPlant). */
    public static void bungeeDropPlant(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_GRABBING) {
            Plant aPlant = z.mBoard.mPlants.dataArrayTryToGet(z.mTargetPlantID);
            if (aPlant != null) {
                if (aPlant.mOnBungeeState == PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
                    aPlant.mOnBungeeState = PlantOnBungeeState.NOT_ON_BUNGEE;
                } else if (aPlant.mOnBungeeState == PlantOnBungeeState.RISING_WITH_BUNGEE) {
                    aPlant.die();
                }

                z.mTargetPlantID = PlantID.PLANTID_NULL;
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7398-7418 (Zombie::BungeeDie). */
    public static void bungeeDie(Zombie z) {
        bungeeDropPlant(z);

        if (z.mBoard != null) {
            Plant aPlant = z.mBoard.mPlants.dataArrayTryToGet(z.mTargetPlantID);
            if (aPlant != null) {
                z.mBoard.mPlantsEaten++;
                aPlant.die();
            }
        }

        // PORT-NOTE: upstream already guards mBoard for the plant above (Zombie.cpp:7402, "null check
        // added for safety") but still dereferences it here (Zombie.cpp:7412), so the almanac's
        // board-less bungee preview (AlmanacDialog.cpp:164) crashes on the way out of the dialog.
        // Without a board there is no dropped zombie to kill.
        if (z.mBoard != null) {
            Zombie aZombie = z.mBoard.zombieTryToGet(z.mRelatedZombieID);
            if (aZombie != null && !aZombie.mDead) {
                dieNoLoot(aZombie);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7419-7443 (Zombie::DieNoLoot). */
    public static void dieNoLoot(Zombie z) {
        stopZombieSound(z);
        Attachment.attachmentDie(z.mAttachmentID);
        z.mApp.removeReanimation(z.mBodyReanimID);
        z.mApp.removeReanimation(z.mMoweredReanimID);
        z.mApp.removeReanimation(z.mSpecialHeadReanimID);
        z.mApp.removeReanimation(z.mZombatarHeadReanimID);

        z.mDead = true;
        LawnHooksRegistry.dispatcher().onZombieDeath(z.mBoard, z);
        trySpawnLevelAward(z);
        if (z.mZombieType == ZombieType.ZOMBIE_BOBSLED) {
            bobsledDie(z);
        }
        if (z.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
            bungeeDie(z);
        }
        if (z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            z.bossDie();
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7444-7459 (Zombie::PlayZombieAppearSound). */
    public static void playZombieAppearSound(Zombie z) {
        if (z.mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER) {
            z.mApp.playFoley(FoleyType.FOLEY_DOLPHIN_APPEARS);
        } else if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            z.mApp.playFoley(FoleyType.FOLEY_BALLOONINFLATE);
        } else if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
            z.mApp.playFoley(FoleyType.FOLEY_ZAMBONI);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7460-7476 (Zombie::StartZombieSound). */
    public static void startZombieSound(Zombie z) {
        if (z.mPlayingSong) {
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_RUNNING && z.mHasHead) {
            z.mApp.playFoley(FoleyType.FOLEY_JACKINTHEBOX);
            z.mPlayingSong = true;
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            z.mApp.playFoley(FoleyType.FOLEY_DIGGER);
            z.mPlayingSong = true;
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7477-7518 (Zombie::StopZombieSound). */
    public static void stopZombieSound(Zombie z) {
        if (z.mZombieType == ZombieType.ZOMBIE_DANCER || z.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
            boolean aStopSound = true;

            if (z.mBoard != null) {
                for (Zombie aZombie : z.mBoard.mZombies) {
                    if (aZombie.mDead) {
                        continue;
                    }
                    if (aZombie.mHasHead && !aZombie.isDeadOrDying() && aZombie.isOnBoard()
                            && (aZombie.mZombieType == ZombieType.ZOMBIE_DANCER
                                    || aZombie.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER)) {
                        aStopSound = false;
                        break;
                    }
                }
            }

            if (aStopSound) {
                z.mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_DANCER);
            }
        }

        if (z.mPlayingSong) {
            z.mPlayingSong = false;

            if (z.mZombieType == ZombieType.ZOMBIE_JACK_IN_THE_BOX) {
                z.mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_JACKINTHEBOX);
            } else if (z.mZombieType == ZombieType.ZOMBIE_DIGGER) {
                z.mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_DIGGER);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7519-7538 (Zombie::ApplyChill). */
    public static void applyChill(Zombie z, boolean theIsIceTrap) {
        if (!canBeChilled(z)) {
            return;
        }

        if (z.mChilledCounter == 0) {
            z.mApp.playFoley(FoleyType.FOLEY_FROZEN);
        }

        int aChillTime = 1000;
        if (theIsIceTrap) {
            aChillTime = 2000;
        }
        z.mChilledCounter = Math.max(aChillTime, z.mChilledCounter);

        z.updateAnimSpeed();
    }

    /** Ported from src/Lawn/Zombie.cpp:8032-8056 (Zombie::CanBeChilled). */
    public static boolean canBeChilled(Zombie z) {
        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI || z.isBobsledTeamWithSled()) {
            return false;
        }

        if (z.isDeadOrDying()) {
            return false;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE
                || z.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE
                || z.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING) {
            return false;
        }

        if (z.mMindControlled) {
            return false;
        }

        return z.mZombieType != ZombieType.ZOMBIE_BOSS
                || z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_IDLE_BEFORE_SPIT
                || z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_IDLE_AFTER_SPIT
                || z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_SPIT;
    }

    /** Ported from src/Lawn/Zombie.cpp:8058-8080 (Zombie::CanBeFrozen). */
    public static boolean canBeFrozen(Zombie z) {
        if (!canBeChilled(z)) {
            return false;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL
                || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP
                || z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL
                || z.isFlying()
                || z.mZombiePhase == ZombiePhase.PHASE_IMP_GETTING_THROWN
                || z.mZombiePhase == ZombiePhase.PHASE_IMP_LANDING
                || z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_CRASHING
                || z.mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_POPPING
                || z.mZombiePhase == ZombiePhase.PHASE_SQUASH_RISING
                || z.mZombiePhase == ZombiePhase.PHASE_SQUASH_FALLING
                || z.mZombiePhase == ZombiePhase.PHASE_SQUASH_DONE_FALLING
                || z.isBouncingPogo()) {
            return false;
        }

        return z.mZombieType != ZombieType.ZOMBIE_BUNGEE || z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_AT_BOTTOM;
    }

    /** Ported from src/Lawn/Zombie.cpp:8373-8384 (Zombie::RemoveIceTrap). */
    public static void removeIceTrap(Zombie z) {
        z.mIceTrapCounter = 0;
        if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            balloonPropellerHatSpin(z, true);
        }

        z.updateAnimSpeed();
        startZombieSound(z);
    }

    /** Ported from src/Lawn/Zombie.cpp:8385-8423 (Zombie::HitIceTrap). */
    public static void hitIceTrap(Zombie z) {
        boolean cold = false;
        if (z.mChilledCounter > 0 || z.mIceTrapCounter != 0) {
            cold = true;
        }

        applyChill(z, true);
        if (!canBeFrozen(z)) {
            return;
        }

        if (z.mInPool) {
            z.mIceTrapCounter = 300;
        } else if (cold) {
            z.mIceTrapCounter = PvzpCommon.randRangeInt(300, 400);
        } else {
            z.mIceTrapCounter = PvzpCommon.randRangeInt(400, 600);
        }

        stopZombieSound(z);
        if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            balloonPropellerHatSpin(z, false);
        }
        if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_SPIT) {
            z.mBoard.removeParticleByType(ParticleEffect.PARTICLE_ZOMBIE_BOSS_FIREBALL);
        }

        z.takeDamage(20, 1);
        z.updateAnimSpeed();
    }

    /** Ported from src/Lawn/Zombie.cpp:8459-8467 (Zombie::IsFireResistant). */
    public static boolean isFireResistant(Zombie z) {
        return z.mZombieType == ZombieType.ZOMBIE_CATAPULT
                || z.mZombieType == ZombieType.ZOMBIE_ZAMBONI
                || z.mShieldType == ShieldType.SHIELDTYPE_DOOR
                || z.mShieldType == ShieldType.SHIELDTYPE_LADDER;
    }

    /** Ported from src/Lawn/Zombie.cpp:8468-8485 (Zombie::BalloonPropellerHatSpin). */
    public static void balloonPropellerHatSpin(Zombie z, boolean theSpinning) {
        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        ReanimatorTrackInstance aHatTrackInstance = aBodyReanim.getTrackInstanceByName("hat");
        Reanimation aPropellerReanim = Attachment.findReanimAttachment(aHatTrackInstance.mAttachmentID);
        if (aPropellerReanim != null) {
            if (theSpinning) {
                aPropellerReanim.mAnimRate = aPropellerReanim.mDefinition.mFPS;
            } else {
                aPropellerReanim.mAnimRate = 0.0f;
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:8486-8516 (Zombie::RemoveButter). */
    public static void removeButter(Zombie z) {
        if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            balloonPropellerHatSpin(z, true);
        }

        if (Zombie.isZombotany(z.mZombieType)) {
            Reanimation aHeadReanim = z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID);
            if (aHeadReanim != null) {
                if (z.mZombieType == ZombieType.ZOMBIE_PEA_HEAD && aHeadReanim.isAnimPlaying("anim_shooting")) {
                    aHeadReanim.mAnimRate = 35.0f;
                } else if (z.mZombieType == ZombieType.ZOMBIE_GATLING_HEAD
                        && aHeadReanim.isAnimPlaying("anim_shooting")) {
                    aHeadReanim.mAnimRate = 38.0f;
                } else {
                    aHeadReanim.mAnimRate = 15.0f;
                }
            }
        }

        z.updateAnimSpeed();
        startZombieSound(z);
    }

    /** Ported from src/Lawn/Zombie.cpp:8517-8557 (Zombie::ApplyButter). */
    public static void applyButter(Zombie z) {
        if (!z.mHasHead || !canBeFrozen(z)) {
            return;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI || z.mZombieType == ZombieType.ZOMBIE_BOSS
                || z.isTangleKelpTarget() || z.isBobsledTeamWithSled() || z.isFlying()) {
            return;
        }

        z.mButteredCounter = 400;
        Zombie aZombie = z.mBoard.zombieTryToGet(z.mRelatedZombieID);
        if (aZombie != null) {
            aZombie.mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
            z.mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_POGO) {
            z.mAltitude = 0.0f;
            if (z.mOnHighGround) {
                z.mAltitude += GameConstants.HIGH_GROUND_HEIGHT;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            balloonPropellerHatSpin(z, false);
        } else if (Zombie.isZombotany(z.mZombieType)) {
            Reanimation aHeadReanim = z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID);
            if (aHeadReanim != null) {
                aHeadReanim.mAnimRate = 0.0f;
            }
        }

        z.updateAnimSpeed();
        stopZombieSound(z);
    }

    /** Ported from src/Lawn/Zombie.cpp:8558-8646 (Zombie::MowDown). */
    public static void mowDown(Zombie z) {
        if (z.mDead || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED || z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            return;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
            z.mApp.addPvzpParticle(z.mPosX + 80.0f, z.mPosY + 60.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_CATAPULT_EXPLOSION);
            z.mApp.playFoley(FoleyType.FOLEY_EXPLOSION);
            dieWithLoot(z);
            return;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
            z.mApp.addPvzpParticle(z.mPosX + 80.0f, z.mPosY + 60.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION);
            z.mApp.playFoley(FoleyType.FOLEY_EXPLOSION);
            dieWithLoot(z);
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING
                || z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                || z.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE
                || z.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING
                || z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL
                || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED
                || z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR
                || z.mZombieType == ZombieType.ZOMBIE_BUNGEE
                || z.mZombieType == ZombieType.ZOMBIE_DIGGER
                || z.mZombieType == ZombieType.ZOMBIE_IMP
                || z.mZombieType == ZombieType.ZOMBIE_YETI
                || z.mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER
                || z.isBobsledTeamWithSled()
                || z.isFlying()
                || z.mInPool) {
            Reanimation aPuffReanim = z.mApp.addReanimation(z.mPosX - 73.0f, z.mPosY - 56.0f,
                    z.mRenderOrder + 2, ReanimationType.REANIM_PUFF);
            aPuffReanim.setFramesForLayer("anim_puff");
            z.mApp.addPvzpParticle(z.mPosX + 110.0f, z.mPosY + 0.0f, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_MOWER_CLOUD);

            if (z.mBoard.mPlantRow[z.mRow] != PlantRowType.PLANTROW_POOL) {
                z.dropHead(0);
                z.dropArm(0);
                z.dropHelm(0);
                z.dropShield(0);
            }

            dieWithLoot(z);
            return;
        }

        if (z.mIceTrapCounter > 0) {
            removeIceTrap(z);
        }
        z.mButteredCounter = Math.min(z.mButteredCounter, 0);

        z.dropShield(0);
        z.dropHelm(0);
        if (z.mZombieType == ZombieType.ZOMBIE_FLAG) {
            dropFlag(z);
        } else if (z.mZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
            dropPole(z);
        } else if (z.mZombieType == ZombieType.ZOMBIE_NEWSPAPER || z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            z.dropHead(0);
        } else if (z.mZombieType == ZombieType.ZOMBIE_POGO) {
            z.dropHead(0);
            z.mAltitude = 0.0f;
        }

        Reanimation aMoweredReanim = z.mApp.addReanimation(0.0f, 0.0f, z.mRenderOrder,
                ReanimationType.REANIM_LAWN_MOWERED_ZOMBIE);
        aMoweredReanim.mAnimRate = 8.0f;
        aMoweredReanim.mIsAttachment = false;
        aMoweredReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        z.mMoweredReanimID = z.mApp.reanimationGetID(aMoweredReanim);
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_MOWERED;
        dropLoot(z);
    }

    /** Ported from src/Lawn/Zombie.cpp:8647-8660 (Zombie::RemoveColdEffects). */
    public static void removeColdEffects(Zombie z) {
        if (z.mIceTrapCounter > 0) {
            removeIceTrap(z);
        }

        if (z.mChilledCounter > 0) {
            z.mChilledCounter = 0;
            z.updateAnimSpeed();
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:8661-8804 (Zombie::ApplyBurn). */
    public static void applyBurn(Zombie z) {
        if (z.mDead || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED) {
            return;
        }

        if (z.mBodyHealth >= 1800 || z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            z.takeDamage(1800, 18);
            return;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_SQUASH_HEAD && !z.mHasHead) {
            z.mApp.removeReanimation(z.mSpecialHeadReanimID);
            z.mSpecialHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        }

        if (z.mIceTrapCounter > 0) {
            removeIceTrap(z);
        }
        z.mButteredCounter = Math.min(z.mButteredCounter, 0);

        Attachment.attachmentDetachCrossFadeParticleType(z.mAttachmentID,
                ParticleEffect.PARTICLE_ZAMBONI_SMOKE, null);
        bungeeDropPlant(z);

        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING
                || z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                || z.mZombiePhase == ZombiePhase.PHASE_IMP_GETTING_THROWN
                || z.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE
                || z.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING
                || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL
                || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP
                || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING
                || z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE
                || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED
                || z.mInPool) {
            dieWithLoot(z);
        } else if (z.mZombieType == ZombieType.ZOMBIE_BUNGEE || z.mZombieType == ZombieType.ZOMBIE_YETI
                || Zombie.isZombotany(z.mZombieType) || z.isBobsledTeamWithSled() || z.isFlying()
                || !z.mHasHead) {
            z.setAnimRate(0.0f);
            Reanimation aHeadReanim = z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID);
            if (aHeadReanim != null) {
                aHeadReanim.mAnimRate = 0.0f;
            }

            z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_BURNED;
            z.mPhaseCounter = 300;
            z.mJustGotShotCounter = 0;
            dropLoot(z);

            if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
                balloonPropellerHatSpin(z, false);
            }
        } else {
            int aReanimType = ReanimationType.REANIM_ZOMBIE_CHARRED;
            float aCharredPosX = z.mPosX + 22.0f;
            float aCharredPosY = z.mPosY - 10.0f;
            if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
                aCharredPosY += 31.0f;
            }
            if (z.mZombieType == ZombieType.ZOMBIE_IMP) {
                aCharredPosX -= 6.0f;
                aReanimType = ReanimationType.REANIM_ZOMBIE_CHARRED_IMP;
            }
            if (z.mZombieType == ZombieType.ZOMBIE_DIGGER) {
                if (z.isWalkingBackwards()) {
                    aCharredPosX += 14.0f;
                }
                aReanimType = ReanimationType.REANIM_ZOMBIE_CHARRED_DIGGER;
            }
            if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                aReanimType = ReanimationType.REANIM_ZOMBIE_CHARRED_ZAMBONI;
                aCharredPosX += 61.0f;
                aCharredPosY -= 16.0f;
            }
            if (z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
                aReanimType = ReanimationType.REANIM_ZOMBIE_CHARRED_CATAPULT;
                aCharredPosX -= 36.0f;
                aCharredPosY -= 20.0f;
            }
            if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                    || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                aReanimType = ReanimationType.REANIM_ZOMBIE_CHARRED_GARGANTUAR;
                aCharredPosX -= 15.0f;
                aCharredPosY -= 10.0f;
            }

            Reanimation aCharredReanim = z.mApp.addReanimation(aCharredPosX, aCharredPosY, z.mRenderOrder,
                    aReanimType);
            aCharredReanim.mAnimRate *= PvzpCommon.randRangeFloat(0.9f, 1.1f);
            if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_WALKING_WITHOUT_AXE) {
                aCharredReanim.setFramesForLayer("anim_crumble_noaxe");
            } else if (z.mZombieType == ZombieType.ZOMBIE_DIGGER) {
                aCharredReanim.setFramesForLayer("anim_crumble");
            } else if ((z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                    || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) && !z.mHasObject) {
                aCharredReanim.setImageOverride("impblink", Resources.getImageById(ImageID.IMAGE_BLANK));
                aCharredReanim.setImageOverride("imphead", Resources.getImageById(ImageID.IMAGE_BLANK));
            }

            float aCharredScale = z.mScaleZombie;
            if (z.mZombieType == ZombieType.ZOMBIE_DANCER || z.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
                aCharredScale = 1.0f;
            }

            if (aCharredScale != 1.0f) {
                aCharredReanim.mOverlayMatrix.m00 = aCharredScale;
                aCharredReanim.mOverlayMatrix.m11 = aCharredScale;
                aCharredReanim.mOverlayMatrix.m02 += 20.0f - aCharredScale * 20.0f;
                aCharredReanim.mOverlayMatrix.m12 += 120.0f - aCharredScale * 120.0f;
                aCharredReanim.overrideScale(aCharredScale, aCharredScale);
            }

            if (z.isWalkingBackwards()) {
                aCharredReanim.overrideScale(-aCharredScale, aCharredScale);
                aCharredReanim.mOverlayMatrix.m02 += 60.0f * aCharredScale;
            }

            dieWithLoot(z);
        }

        if (z.mZombieType == ZombieType.ZOMBIE_BOBSLED) {
            bobsledBurn(z);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:8900-9030 (Zombie::PlayDeathAnim). */
    public static void playDeathAnim(Zombie z, int theDamageFlags) {
        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING
                || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED
                || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED) {
            return;
        }

        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (aBodyReanim == null || !aBodyReanim.trackExists("anim_death")) {
            dieNoLoot(z);
            return;
        }
        if (z.mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER
                && z.mZombiePhase != ZombiePhase.PHASE_DOLPHIN_WALKING_IN_POOL) {
            dieNoLoot(z);
            return;
        }
        if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL
                || z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING) {
            dieNoLoot(z);
            return;
        }

        if (z.mIceTrapCounter > 0) {
            z.addAttachedParticle(75, 106, ParticleEffect.PARTICLE_ICE_TRAP_RELEASE);
            z.mIceTrapCounter = 0;
        }
        z.mButteredCounter = Math.min(z.mButteredCounter, 0);
        if (z.mYuckyFace) {
            showYuckyFace(z, false);
            z.mYuckyFace = false;
            z.mYuckyFaceCounter = 0;
        }

        if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
            if (z.mZombieType != ZombieType.ZOMBIE_BOSS
                    && z.mZombieType != ZombieType.ZOMBIE_GARGANTUAR
                    && z.mZombieType != ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                dieNoLoot(z);
                return;
            }
        }

        if (z.mZombieType == ZombieType.ZOMBIE_POGO) {
            z.mAltitude = 0.0f;
        }

        Attachment.attachmentReanimTypeDie(z.mAttachmentID, ReanimationType.REANIM_ZOMBIE_SURPRISE);
        z.stopEating();

        if (z.mShieldType != ShieldType.SHIELDTYPE_NONE) {
            z.dropShield(1);
        }
        if (z.mZombieType == ZombieType.ZOMBIE_SQUASH_HEAD && !z.mHasHead) {
            z.mApp.removeReanimation(z.mSpecialHeadReanimID);
            z.mSpecialHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        }

        z.mVelX = 0.0f;
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_DYING;
        if (z.mZombieHeight == ZombieHeight.HEIGHT_ZOMBIQUARIUM) {
            z.playZombieReanim("anim_aquarium_death", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 14.0f);
            return;
        }
        if (z.mZombieHeight == ZombieHeight.HEIGHT_UP_LADDER) {
            z.mZombieHeight = ZombieHeight.HEIGHT_FALLING;
        }

        float aDeathAnimRate;
        if (z.mZombieType == ZombieType.ZOMBIE_FOOTBALL) {
            aDeathAnimRate = 24.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
            aDeathAnimRate = 14.0f;
            z.mApp.playFoley(FoleyType.FOLEY_GARGANTUDEATH);
        } else if (z.mZombieType == ZombieType.ZOMBIE_SNORKEL) {
            aDeathAnimRate = 14.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_DIGGER) {
            aDeathAnimRate = 18.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_YETI) {
            aDeathAnimRate = 14.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            aDeathAnimRate = 18.0f;

            z.bossDie();
            Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
            aHeadReanim.playReanim("anim_death", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, aDeathAnimRate);
        } else {
            aDeathAnimRate = PvzpCommon.randRangeFloat(24.0f, 30.0f);
        }

        String aDeathTrackName = "anim_death";
        int aDeathAnimHit = Common.rand(100);
        boolean aCanDoSuperLongDeath = z.mApp.hasFinishedAdventure() || z.mBoard.mLevel > 5;
        if (z.mInPool && aBodyReanim.trackExists("anim_waterdeath")) {
            aDeathTrackName = "anim_waterdeath";
            z.reanimIgnoreClipRect("Zombie_duckytube", false);
        } else if (aDeathAnimHit == 99 && aBodyReanim.trackExists("anim_superlongdeath") && aCanDoSuperLongDeath
                && z.mChilledCounter == 0 && z.mBoard.countZombiesOnScreen() <= 5) {
            aDeathAnimRate = 14.0f;
            aDeathTrackName = "anim_superlongdeath";
        } else if (aDeathAnimHit > 50 && aBodyReanim.trackExists("anim_death2")) {
            aDeathTrackName = "anim_death2";
        }

        z.playZombieReanim(aDeathTrackName, ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, aDeathAnimRate);
        z.reanimShowPrefix("anim_tongue", Reanimation.RENDER_GROUP_HIDDEN);
    }

    /** Ported from src/Lawn/Zombie.cpp:9031-9060 (Zombie::DoDaisies). */
    public static void doDaisies(Zombie z) {
        if (z.isWalkingBackwards()) {
            return;
        }

        if (z.mBoard.mPlantRow[z.mRow] == PlantRowType.PLANTROW_POOL) {
            return;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_BOBSLED || z.mZombieType == ZombieType.ZOMBIE_ZAMBONI
                || z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
            return;
        }

        if (z.mBoard.stageHasRoof()) {
            return;
        }

        float aOffsetX = 20.0f;
        float aOffsetY = 100.0f;
        if (z.mZombieType == ZombieType.ZOMBIE_FOOTBALL || z.mZombieType == ZombieType.ZOMBIE_DANCER
                || z.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
            aOffsetX += 160.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_POGO) {
            aOffsetY += 120.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            aOffsetY += 30.0f;
            aOffsetX += 110.0f;
        }
        if (z.mBoard.stageHasGraveStones()) {
            aOffsetY += 15.0f;
        }

        int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, z.mRow, 5);
        z.mApp.addPvzpParticle(z.mX + aOffsetX, z.mY + aOffsetY, aRenderPosition,
                ParticleEffect.PARTICLE_ZOMBIE_DAISIES);
    }

    /** Ported from src/Lawn/Zombie.cpp:9069-9285 (Zombie::UpdateDeath). */
    public static void updateDeath(Zombie z) {
        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (aBodyReanim == null) {
            dieNoLoot(z);
            return;
        }

        if (z.mZombieHeight == ZombieHeight.HEIGHT_FALLING) {
            z.updateZombieFalling();
        }
        if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.89f)) {
                z.mBoard.shakeBoard(0, 3);
            } else if (aBodyReanim.shouldTriggerTimedEvent(0.98f)) {
                z.mBoard.shakeBoard(0, 1);
            }
        }

        if (!z.mInPool) {
            float aFallTime;
            switch (z.mZombieType) {
                case ZombieType.ZOMBIE_SNORKEL:
                case ZombieType.ZOMBIE_ZAMBONI:
                case ZombieType.ZOMBIE_DOLPHIN_RIDER:
                case ZombieType.ZOMBIE_BUNGEE:
                case ZombieType.ZOMBIE_CATAPULT:
                case ZombieType.ZOMBIE_IMP:
                case ZombieType.ZOMBIE_BOSS:
                    aFallTime = -1.0f;
                    break;

                case ZombieType.ZOMBIE_NORMAL:
                case ZombieType.ZOMBIE_FLAG:
                case ZombieType.ZOMBIE_TRAFFIC_CONE:
                case ZombieType.ZOMBIE_PAIL:
                case ZombieType.ZOMBIE_DOOR:
                case ZombieType.ZOMBIE_PEA_HEAD:
                case ZombieType.ZOMBIE_WALLNUT_HEAD:
                case ZombieType.ZOMBIE_TALLNUT_HEAD:
                case ZombieType.ZOMBIE_JALAPENO_HEAD:
                case ZombieType.ZOMBIE_GATLING_HEAD:
                case ZombieType.ZOMBIE_SQUASH_HEAD:
                case ZombieType.ZOMBIE_DUCKY_TUBE:
                    if (aBodyReanim.isAnimPlaying("anim_superlongdeath")) {
                        aFallTime = 0.788f;
                    } else if (aBodyReanim.isAnimPlaying("anim_death2")) {
                        aFallTime = 0.71f;
                    } else {
                        aFallTime = 0.77f;
                    }
                    break;

                case ZombieType.ZOMBIE_POLEVAULTER:
                    aFallTime = 0.68f;
                    break;

                case ZombieType.ZOMBIE_FOOTBALL:
                    aFallTime = 0.52f;
                    break;

                case ZombieType.ZOMBIE_NEWSPAPER:
                    aFallTime = 0.63f;
                    break;

                case ZombieType.ZOMBIE_DANCER:
                case ZombieType.ZOMBIE_BACKUP_DANCER:
                    aFallTime = 0.83f;
                    break;

                case ZombieType.ZOMBIE_BOBSLED:
                    aFallTime = 0.81f;
                    break;

                case ZombieType.ZOMBIE_JACK_IN_THE_BOX:
                    aFallTime = 0.64f;
                    break;

                case ZombieType.ZOMBIE_BALLOON:
                    aFallTime = 0.68f;
                    break;

                case ZombieType.ZOMBIE_DIGGER:
                    aFallTime = 0.85f;
                    break;

                case ZombieType.ZOMBIE_POGO:
                    aFallTime = 0.84f;
                    break;

                case ZombieType.ZOMBIE_YETI:
                    aFallTime = 0.68f;
                    break;

                case ZombieType.ZOMBIE_LADDER:
                    aFallTime = 0.62f;
                    break;

                case ZombieType.ZOMBIE_GARGANTUAR:
                case ZombieType.ZOMBIE_REDEYE_GARGANTUAR:
                    aFallTime = 0.86f;
                    break;

                default:
                    aFallTime = -1.0f;
                    break;
            }

            if (aFallTime > 0 && aBodyReanim.shouldTriggerTimedEvent(aFallTime)) {
                z.mApp.playFoley(FoleyType.FOLEY_ZOMBIE_FALLING);
                if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                        || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                    z.mApp.playFoley(FoleyType.FOLEY_THUMP);
                }

                if (z.mBoard.mDaisyMode) {
                    doDaisies(z);
                }
            }
        }

        if (z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.1f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.12f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.15f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.19f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.2f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.26f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.3f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.4f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.42f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.5f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.58f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.61f)
                    || aBodyReanim.shouldTriggerTimedEvent(0.71f)) {
                float aExplosionPosX = PvzpCommon.randRangeFloat(600.0f, 750.0f);
                float aExplosionPosY = PvzpCommon.randRangeFloat(50.0f, 300.0f);
                z.mApp.addPvzpParticle(aExplosionPosX, aExplosionPosY, RenderLayer.RENDER_LAYER_TOP,
                        ParticleEffect.PARTICLE_BOSS_EXPLOSION);
                z.mApp.playFoley(FoleyType.FOLEY_BOSS_EXPLOSION_SMALL);
            }

            Reanimation aHeadReanim = z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.93f)) {
                z.mBoard.shakeBoard(1, 2);
                z.mApp.playFoley(FoleyType.FOLEY_BOSS_EXPLOSION_SMALL);
                z.mApp.playFoley(FoleyType.FOLEY_THUMP);
            }

            if (aBodyReanim.shouldTriggerTimedEvent(0.99f)) {
                aHeadReanim.playReanim("anim_flag", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 30.0f);
            }

            if (aHeadReanim.isAnimPlaying("anim_flag") && aHeadReanim.mLoopCount > 0) {
                aHeadReanim.playReanim("anim_flag_loop", ReanimLoopType.REANIM_LOOP, 20, 17.0f);
            }

            if (aBodyReanim.mLoopCount > 0) {
                dropLoot(z);
            }
        }

        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI && z.mPhaseCounter > 0) {
            z.mPhaseCounter--;
            if (z.mPhaseCounter == 0) {
                aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
                if (aBodyReanim.isTrackShowing("anim_wheelie2")) {
                    z.mApp.addPvzpParticle(z.mPosX + 80.0f, z.mPosY + 60.0f, z.mRenderOrder + 1,
                            ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION2);
                } else {
                    z.mApp.addPvzpParticle(z.mPosX + 80.0f, z.mPosY + 60.0f, z.mRenderOrder + 1,
                            ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION);
                }

                dieWithLoot(z);
                z.mApp.playFoley(FoleyType.FOLEY_EXPLOSION);
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
            z.mPhaseCounter--;
            if (z.mPhaseCounter == 0) {
                z.mApp.addPvzpParticle(z.mPosX + 80.0f, z.mPosY + 60.0f, z.mRenderOrder + 1,
                        ParticleEffect.PARTICLE_CATAPULT_EXPLOSION);
                dieWithLoot(z);
                z.mApp.playFoley(FoleyType.FOLEY_EXPLOSION);
            }
        } else if (z.mZombieFade == -1 && aBodyReanim.mLoopCount > 0
                && z.mZombieType != ZombieType.ZOMBIE_BOSS) {
            z.mZombieFade = z.mInPool ? 10 : 100;
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:9286-9296 (Zombie::UpdateMowered). */
    public static void updateMowered(Zombie z) {
        Reanimation aMoweredReanim = z.mApp.reanimationTryToGet(z.mMoweredReanimID);
        if (aMoweredReanim == null || aMoweredReanim.mLoopCount > 0) {
            z.dropHead(0);
            z.dropArm(0);
            dieWithLoot(z);
        }
    }
}
