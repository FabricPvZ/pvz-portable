/*
 * Ported from src/Lawn/Zombie.h and src/Lawn/Zombie.cpp.
 *
 * The large per-concern clusters of Zombie.cpp are split into sibling helper classes so the port
 * stays reviewable; every method here either ports directly or is a one-line delegator:
 *   ZombieRender      - Draw/DrawReanim/GetDrawPos and the reanim sprite plumbing
 *   ZombieBoss        - the ZOMBIE_BOSS state machine (boss level only)
 *   ZombieStatus      - status effects, damage-side drops, death, loot, sounds
 *   ZombieBehaviourA  - per-type updaters (bungee..digger)
 *   ZombieBehaviourB  - dancers, rising-from-grave, pool/high-ground, zamboni/yeti/ladder
 *   ZombieCombat      - plant/zombie targeting and the armour/damage pipeline
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.util.Arrays;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachmentIDRef;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrack;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.DataSync;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

public class Zombie extends GameObject {

    /** Zombie.h:29-32 */
    public static final int MAX_ZOMBIE_FOLLOWERS = 4;
    public static final int NUM_BOBSLED_FOLLOWERS = 3;
    public static final int NUM_BACKUP_DANCERS = 4;
    public static final int NUM_BOSS_BUNGEES = 3;

    /** Zombie.h:70-76 */
    public static final int ZOMBIE_WAVE_DEBUG = -1;
    public static final int ZOMBIE_WAVE_CUTSCENE = -2;
    public static final int ZOMBIE_WAVE_UI = -3;
    public static final int ZOMBIE_WAVE_WINNER = -4;

    /** Zombie.cpp:48-73 */
    public static final int ZOMBIE_START_RANDOM_OFFSET = 40;
    public static final int BUNGEE_ZOMBIE_HEIGHT = 3000;
    public static final int RENDER_GROUP_SHIELD = 1;
    public static final int RENDER_GROUP_ARMS = 2;
    public static final int RENDER_GROUP_OVER_SHIELD = 3;
    public static final int RENDER_GROUP_ZOMBATAR_HEAD = 3;
    public static final int RENDER_GROUP_BOSS_BACK_LEG = 4;
    public static final int RENDER_GROUP_BOSS_FRONT_LEG = 5;
    public static final int RENDER_GROUP_BOSS_BACK_ARM = 6;
    public static final int RENDER_GROUP_BOSS_FIREBALL_ADDITIVE = 7;
    public static final int RENDER_GROUP_BOSS_FIREBALL_TOP = 8;
    public static final int ZOMBIE_LIMP_SPEED_FACTOR = 2;
    public static final int POGO_BOUNCE_TIME = 80;
    public static final int DOLPHIN_JUMP_TIME = 120;
    public static final int JACK_IN_THE_BOX_ZOMBIE_RADIUS = 115;
    public static final int JACK_IN_THE_BOX_PLANT_RADIUS = 90;
    public static final int BOBSLED_CRASH_TIME = 150;
    public static final int ZOMBIE_BACKUP_DANCER_RISE_HEIGHT = -200;
    public static final int BOSS_FLASH_HEALTH_FRACTION = 10;
    public static final int TICKS_BETWEEN_EATS = 4;
    public static final int DAMAGE_PER_EAT = TICKS_BETWEEN_EATS;
    public static final float THOWN_ZOMBIE_GRAVITY = 0.05f;
    public static final float CHILLED_SPEED_FACTOR = 0.4f;
    public static final float CLIP_HEIGHT_LIMIT = -100.0f;
    public static final float CLIP_HEIGHT_OFF = -200.0f;
    /** Zombie.cpp:73 */
    public static final Color ZOMBIE_MINDCONTROLLED_COLOR = new Color(128, 64, 192, 255);

    /**
     * Zombie.cpp:80-115 — the zombie definition table, index == ZombieType; upstream rows 0 to
     * {@link ZombieType#NUM_ZOMBIE_TYPES} (plus the cached pole-vaulter row) verbatim.
     *
     * <p>Same table, but as wide as the zombie-type id space ({@link ZombieType#MAX_ZOMBIE_TYPES}):
     * zombie type {@code n} always has its row {@code n}, the retail rows are filled in here, and
     * every row above them is empty ({@code null}) until a definition is written into it. A
     * {@code null} row is a zombie type nothing defines, which is what
     * {@link #getZombieDefinition(int)} answers for it and what the loops walking the table skip.
     * See {@code Plant#gPlantDefs} for the write-before-read contract of such a table.
     */
    public static final ZombieDefinition[] gZombieDefs = Arrays.copyOf(new ZombieDefinition[] {
        new ZombieDefinition(ZombieType.ZOMBIE_NORMAL, ReanimationType.REANIM_ZOMBIE, 1, 1, 1, 4000, "ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_FLAG, ReanimationType.REANIM_ZOMBIE, 1, 1, 1, 0, "FLAG_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_TRAFFIC_CONE, ReanimationType.REANIM_ZOMBIE, 2, 3, 1, 4000, "CONEHEAD_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_POLEVAULTER, ReanimationType.REANIM_POLEVAULTER, 2, 6, 5, 2000, "POLE_VAULTING_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_PAIL, ReanimationType.REANIM_ZOMBIE, 4, 8, 1, 3000, "BUCKETHEAD_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_NEWSPAPER, ReanimationType.REANIM_ZOMBIE_NEWSPAPER, 2, 11, 1, 1000, "NEWSPAPER_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_DOOR, ReanimationType.REANIM_ZOMBIE, 4, 13, 5, 3500, "SCREEN_DOOR_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_FOOTBALL, ReanimationType.REANIM_ZOMBIE_FOOTBALL, 7, 16, 5, 2000, "FOOTBALL_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_DANCER, ReanimationType.REANIM_DANCER, 5, 18, 5, 1000, "DANCING_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_BACKUP_DANCER, ReanimationType.REANIM_BACKUP_DANCER, 1, 18, 1, 0, "BACKUP_DANCER"),
        new ZombieDefinition(ZombieType.ZOMBIE_DUCKY_TUBE, ReanimationType.REANIM_ZOMBIE, 1, 21, 5, 0, "DUCKY_TUBE_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_SNORKEL, ReanimationType.REANIM_SNORKEL, 3, 23, 10, 2000, "SNORKEL_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_ZAMBONI, ReanimationType.REANIM_ZOMBIE_ZAMBONI, 7, 26, 10, 2000, "ZOMBONI"),
        new ZombieDefinition(ZombieType.ZOMBIE_BOBSLED, ReanimationType.REANIM_BOBSLED, 3, 26, 10, 2000, "ZOMBIE_BOBSLED_TEAM"),
        new ZombieDefinition(ZombieType.ZOMBIE_DOLPHIN_RIDER, ReanimationType.REANIM_ZOMBIE_DOLPHINRIDER, 3, 28, 10, 1500, "DOLPHIN_RIDER_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_JACK_IN_THE_BOX, ReanimationType.REANIM_JACKINTHEBOX, 3, 31, 10, 1000, "JACK_IN_THE_BOX_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_BALLOON, ReanimationType.REANIM_BALLOON, 2, 33, 10, 2000, "BALLOON_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_DIGGER, ReanimationType.REANIM_DIGGER, 4, 36, 10, 1000, "DIGGER_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_POGO, ReanimationType.REANIM_POGO, 4, 38, 10, 1000, "POGO_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_YETI, ReanimationType.REANIM_YETI, 4, 40, 1, 1, "ZOMBIE_YETI"),
        new ZombieDefinition(ZombieType.ZOMBIE_BUNGEE, ReanimationType.REANIM_BUNGEE, 3, 41, 10, 1000, "BUNGEE_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_LADDER, ReanimationType.REANIM_LADDER, 4, 43, 10, 1000, "LADDER_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_CATAPULT, ReanimationType.REANIM_CATAPULT, 5, 46, 10, 1500, "CATAPULT_ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_GARGANTUAR, ReanimationType.REANIM_GARGANTUAR, 10, 48, 15, 1500, "GARGANTUAR"),
        new ZombieDefinition(ZombieType.ZOMBIE_IMP, ReanimationType.REANIM_IMP, 10, 48, 1, 0, "IMP"),
        new ZombieDefinition(ZombieType.ZOMBIE_BOSS, ReanimationType.REANIM_BOSS, 10, 50, 1, 0, "BOSS"),
        new ZombieDefinition(ZombieType.ZOMBIE_PEA_HEAD, ReanimationType.REANIM_ZOMBIE, 1, 99, 1, 4000, "ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_WALLNUT_HEAD, ReanimationType.REANIM_ZOMBIE, 4, 99, 1, 3000, "ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_JALAPENO_HEAD, ReanimationType.REANIM_ZOMBIE, 3, 99, 10, 1000, "ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_GATLING_HEAD, ReanimationType.REANIM_ZOMBIE, 3, 99, 10, 2000, "ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_SQUASH_HEAD, ReanimationType.REANIM_ZOMBIE, 3, 99, 10, 2000, "ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_TALLNUT_HEAD, ReanimationType.REANIM_ZOMBIE, 4, 99, 10, 2000, "ZOMBIE"),
        new ZombieDefinition(ZombieType.ZOMBIE_REDEYE_GARGANTUAR, ReanimationType.REANIM_GARGANTUAR, 10, 48, 15, 6000, "REDEYED_GARGANTUAR"),
    }, ZombieType.MAX_ZOMBIE_TYPES);

    /** Zombie.cpp:116-129 */
    public static final int[] gBossZombieList = {
        ZombieType.ZOMBIE_TRAFFIC_CONE,
        ZombieType.ZOMBIE_PAIL,
        ZombieType.ZOMBIE_FOOTBALL,
        ZombieType.ZOMBIE_POLEVAULTER,
        ZombieType.ZOMBIE_JACK_IN_THE_BOX,
        ZombieType.ZOMBIE_LADDER,
        ZombieType.ZOMBIE_ZAMBONI,
        ZombieType.ZOMBIE_CATAPULT,
        ZombieType.ZOMBIE_POGO,
        ZombieType.ZOMBIE_NEWSPAPER,
        ZombieType.ZOMBIE_DOOR,
        ZombieType.ZOMBIE_GARGANTUAR,
    };

    /**
     * Zombie.cpp:131-140 — the definition of a zombie type, or {@code null} when the type has none.
     *
     * <p>A row that is still empty is a zombie type nobody defined, and an id outside the zombie-type
     * id space has no row at all; both answer {@code null}, so a caller can tell "this type has no
     * definition" from "this type is defined" without knowing which ids are in use.
     */
    public static ZombieDefinition getZombieDefinition(int theZombieType) {
        if (theZombieType < 0 || theZombieType >= gZombieDefs.length) {
            return null;
        }

        ZombieDefinition aDef = gZombieDefs[theZombieType];
        if (aDef == null) {
            return null;
        }

        if (aDef.mZombieType != theZombieType) {
            throw new IllegalStateException("gZombieDefs is not index-aligned at " + theZombieType);
        }
        return aDef;
    }

    // ------------------------------------------------------------------
    // Zombie.h:86-161
    // ------------------------------------------------------------------
    public int mZombieType;
    public int mZombiePhase;
    public float mPosX;
    public float mPosY;
    public float mVelX;
    public int mAnimCounter;
    public int mGroanCounter;
    public int mAnimTicksPerFrame;
    public int mAnimFrames;
    public int mFrame;
    public int mPrevFrame;
    public boolean mVariant;
    public boolean mIsEating;
    public int mJustGotShotCounter;
    public int mShieldJustGotShotCounter;
    public int mShieldRecoilCounter;
    public int mZombieAge;
    public int mZombieHeight;
    public int mPhaseCounter;
    public int mFromWave;
    public boolean mDroppedLoot;
    public int mZombieFade;
    public boolean mFlatTires;
    public int mUseLadderCol;
    public int mTargetCol;
    public float mAltitude;
    public boolean mHitUmbrella;
    public Rect mZombieRect = new Rect(36, 0, 42, 115);
    public Rect mZombieAttackRect = new Rect(50, 0, 20, 115);
    public int mChilledCounter;
    public int mButteredCounter;
    public int mIceTrapCounter;
    public boolean mMindControlled;
    public boolean mBlowingAway;
    public boolean mHasHead;
    public boolean mHasArm;
    public boolean mHasObject;
    public boolean mInPool;
    public boolean mOnHighGround;
    public boolean mYuckyFace;
    public int mYuckyFaceCounter;
    public int mHelmType;
    public int mBodyHealth;
    public int mBodyMaxHealth;
    public int mHelmHealth;
    public int mHelmMaxHealth;
    public int mShieldType;
    public int mShieldHealth;
    public int mShieldMaxHealth;
    public int mFlyingHealth;
    public int mFlyingMaxHealth;
    public boolean mDead;
    public int mRelatedZombieID;
    public int[] mFollowerZombieID = new int[MAX_ZOMBIE_FOLLOWERS];
    public boolean mPlayingSong;
    public int mParticleOffsetX;
    public int mParticleOffsetY;
    public AttachmentIDRef mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
    public int mSummonCounter;
    public int mBodyReanimID;
    public float mScaleZombie;
    public float mVelZ;
    public float mOriginalAnimRate;
    public int mTargetPlantID;
    public int mBossMode;
    public int mTargetRow;
    public int mBossBungeeCounter;
    public int mBossStompCounter;
    public int mBossHeadCounter;
    public int mBossFireBallReanimID;
    public int mSpecialHeadReanimID;
    public int mFireballRow;
    public boolean mIsFireBall;
    public int mMoweredReanimID;
    public int mZombatarHeadReanimID;
    public int mLastPortalX;

    public Zombie() {
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:143-905.
     */
    public void zombieInitialize(int theRow, int theType, boolean theVariant, Zombie theParentZombie, int theFromWave) {
        assert theType >= 0 && theType < ZombieType.MAX_ZOMBIE_TYPES;

        int aZombatarRecordIndex = -1;
        if (theType == ZombieType.ZOMBIE_FLAG && mBoard != null) {
            cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo aPlayerInfo = mApp.mPlayerInfo;
            if (aPlayerInfo != null && aPlayerInfo.mZombatarData.length != 0) {
                int aCount = aPlayerInfo.mZombatarData.length / cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo.ZOMBATAR_RECORD_SIZE;
                if (aCount > 0) {
                    aZombatarRecordIndex = Common.rand(aCount);
                }
            }
        }

        mFromWave = theFromWave;
        mRow = theRow;
        mPosX = 780 + Common.rand(ZOMBIE_START_RANDOM_OFFSET);
        mPosY = getPosYBasedOnRow(theRow);
        mVelX = 0.0f;
        mVelZ = 0.0f;
        mWidth = 120;
        mHeight = 120;
        mFrame = 0;
        mPrevFrame = 0;
        mZombieType = theType;
        mVariant = theVariant;
        mIsEating = false;
        mJustGotShotCounter = 0;
        mShieldJustGotShotCounter = 0;
        mShieldRecoilCounter = 0;
        mChilledCounter = 0;
        mIceTrapCounter = 0;
        mButteredCounter = 0;
        mMindControlled = false;
        mBlowingAway = false;
        mHasHead = true;
        mHasArm = true;
        mHasObject = false;
        mInPool = false;
        mOnHighGround = false;
        mHelmType = HelmType.HELMTYPE_NONE;
        mShieldType = ShieldType.SHIELDTYPE_NONE;
        mYuckyFace = false;
        mYuckyFaceCounter = 0;
        mAnimCounter = 0;
        mGroanCounter = PvzpCommon.randRangeInt(300, 400);
        mAnimTicksPerFrame = 12;
        mAnimFrames = 12;
        mZombieAge = 0;
        mTargetCol = -1;
        mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        mZombieHeight = ZombieHeight.HEIGHT_ZOMBIE_NORMAL;
        mPhaseCounter = 0;
        mHitUmbrella = false;
        mDroppedLoot = false;
        mRelatedZombieID = ZombieID.ZOMBIEID_NULL;
        mZombieRect = new Rect(36, 0, 42, 115);
        mZombieAttackRect = new Rect(50, 0, 20, 115);
        mPlayingSong = false;
        mZombieFade = -1;
        mFlatTires = false;
        mScaleZombie = 1.0f;
        mUseLadderCol = -1;
        mShieldHealth = 0;
        mHelmHealth = 0;
        mAltitude = 0.0f;
        mFlyingHealth = 0;
        mOriginalAnimRate = 0.0f;
        mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
        mSummonCounter = 0;
        mBossStompCounter = -1;
        mBossBungeeCounter = -1;
        mBossHeadCounter = -1;
        mBodyReanimID = ReanimationID.REANIMATIONID_NULL;
        mTargetPlantID = PlantID.PLANTID_NULL;
        mBossMode = 0;
        mBossFireBallReanimID = ReanimationID.REANIMATIONID_NULL;
        mSpecialHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        mTargetRow = -1;
        mFireballRow = -1;
        mIsFireBall = false;
        mMoweredReanimID = ReanimationID.REANIMATIONID_NULL;
        mZombatarHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        mLastPortalX = -1;
        for (int i = 0; i < MAX_ZOMBIE_FOLLOWERS; i++) {
            mFollowerZombieID[i] = ZombieID.ZOMBIEID_NULL;
        }
        if (mBoard != null && mBoard.isFlagWave(mFromWave)) {
            mPosX += 40.0f;
        }
        pickRandomSpeed();
        mBodyHealth = 270;

        ZombieDefinition aZombieDef = getZombieDefinition(mZombieType);
        int aRenderLayer = RenderLayer.RENDER_LAYER_ZOMBIE;
        int aRenderOffset = 4;
        if (aZombieDef.mReanimationType != ReanimationType.REANIM_NONE) {
            loadReanim(aZombieDef.mReanimationType);
        }

        switch (theType) {
        case ZombieType.ZOMBIE_NORMAL:
            loadPlainZombieReanim();
            break;

        case ZombieType.ZOMBIE_DUCKY_TUBE:
            loadPlainZombieReanim();
            break;

        case ZombieType.ZOMBIE_TRAFFIC_CONE:
            loadPlainZombieReanim();
            reanimShowPrefix("anim_cone", Reanimation.RENDER_GROUP_NORMAL);
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            mHelmType = HelmType.HELMTYPE_TRAFFIC_CONE;
            mHelmHealth = 370;
            break;

        case ZombieType.ZOMBIE_PAIL:
            loadPlainZombieReanim();
            reanimShowPrefix("anim_bucket", Reanimation.RENDER_GROUP_NORMAL);
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            mHelmType = HelmType.HELMTYPE_PAIL;
            mHelmHealth = 1100;
            break;

        case ZombieType.ZOMBIE_DOOR:
            mShieldType = ShieldType.SHIELDTYPE_DOOR;
            mShieldHealth = 1100;
            loadPlainZombieReanim();
            attachShield();
            break;

        case ZombieType.ZOMBIE_YETI:
            mBodyHealth = 1350;
            mPhaseCounter = PvzpCommon.randRangeInt(1500, 2000);
            mHasObject = true;
            mZombieAttackRect = new Rect(20, 0, 50, 115);
            break;

        case ZombieType.ZOMBIE_LADDER:
            mBodyHealth = 500;
            mShieldHealth = 500;
            mShieldType = ShieldType.SHIELDTYPE_LADDER;
            mZombieAttackRect = new Rect(10, 0, 50, 115);
            if (isOnBoard()) {
                mZombiePhase = ZombiePhase.PHASE_LADDER_CARRYING;
                startWalkAnim(0);
            }
            attachShield();
            break;

        case ZombieType.ZOMBIE_BUNGEE: {
            mBodyHealth = 450;
            mAnimFrames = 4;
            mAltitude = BUNGEE_ZOMBIE_HEIGHT + PvzpCommon.randRangeInt(0, 150);
            mVelX = 0.0f;

            if (isOnBoard()) {
                pickBungeeZombieTarget(-1);

                if (mDead) {
                    return;
                }

                mZombiePhase = ZombiePhase.PHASE_BUNGEE_DIVING;
            } else {
                mZombiePhase = ZombiePhase.PHASE_BUNGEE_CUTSCENE;
                mPhaseCounter = PvzpCommon.randRangeInt(0, 200);
            }

            playZombieReanim("anim_drop", ReanimLoopType.REANIM_LOOP, 0, 24.0f);
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            aBodyReanim.assignRenderGroupToPrefix("Zombie_bungi_rightarm_lower2", RENDER_GROUP_ARMS);
            aBodyReanim.assignRenderGroupToPrefix("Zombie_bungi_rightarm_hand2", RENDER_GROUP_ARMS);
            aBodyReanim.assignRenderGroupToPrefix("Zombie_bungi_leftarm_lower2", RENDER_GROUP_ARMS);
            aBodyReanim.assignRenderGroupToPrefix("Zombie_bungi_leftarm_hand2", RENDER_GROUP_ARMS);
            aBodyReanim.setTruncateDisappearingFrames(null, false);

            aRenderLayer = RenderLayer.RENDER_LAYER_GRAVE_STONE;
            aRenderOffset = 7;
            mZombieRect = new Rect(-20, 22, 110, 94);
            mZombieAttackRect = new Rect(0, 0, 0, 0);
            mVariant = false;
            break;
        }

        case ZombieType.ZOMBIE_FOOTBALL:
            mZombieRect = new Rect(50, 0, 57, 115);
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            mHelmType = HelmType.HELMTYPE_FOOTBALL;
            mHelmHealth = 1400;
            mAnimTicksPerFrame = 6;
            mVariant = false;
            break;

        case ZombieType.ZOMBIE_DIGGER: {
            mHelmType = HelmType.HELMTYPE_DIGGER;
            mHelmHealth = 100;
            mVariant = false;
            mHasObject = true;
            mZombieRect = new Rect(50, 0, 28, 115);

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            aBodyReanim.setTruncateDisappearingFrames(null, false);

            if (!isOnBoard()) {
                mZombiePhase = ZombiePhase.PHASE_DIGGER_CUTSCENE;
            } else {
                mZombiePhase = ZombiePhase.PHASE_DIGGER_TUNNELING;
                addAttachedParticle(60, 100, ParticleEffect.PARTICLE_DIGGER_TUNNEL);
                aRenderOffset = 7;
                playZombieReanim("anim_dig", ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME, 0, 12.0f);
                pickRandomSpeed();
            }
            break;
        }

        case ZombieType.ZOMBIE_POLEVAULTER:
            mBodyHealth = 500;
            mAnimTicksPerFrame = 6;
            mZombiePhase = ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT;
            mHasObject = true;
            mVariant = false;
            mPosX = GameConstants.WIDE_BOARD_WIDTH + 70 + Common.rand(10);
            if (isOnBoard()) {
                playZombieReanim("anim_run", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
                pickRandomSpeed();
            }
            if (mApp.isWallnutBowlingLevel()) {
                mZombieAttackRect = new Rect(-229, 0, 270, 115);
            } else {
                mZombieAttackRect = new Rect(-29, 0, 70, 115);
            }
            break;

        case ZombieType.ZOMBIE_DOLPHIN_RIDER:
            mBodyHealth = 500;
            mAnimTicksPerFrame = 6;
            mZombiePhase = ZombiePhase.PHASE_DOLPHIN_WALKING;
            mVariant = false;
            if (isOnBoard()) {
                playZombieReanim("anim_walkdolphin", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
                pickRandomSpeed();
            }
            setupWaterTrack("zombie_dolphinrider_whitewater");
            setupWaterTrack("zombie_dolphinrider_dolphininwater");
            break;

        case ZombieType.ZOMBIE_GARGANTUAR:
        case ZombieType.ZOMBIE_REDEYE_GARGANTUAR: {
            mWidth = 180;
            mHeight = 180;
            mBodyHealth = 3000;
            mAnimFrames = 24;
            mAnimTicksPerFrame = 8;
            mPosX = GameConstants.WIDE_BOARD_WIDTH + 45 + Common.rand(10);
            mZombieRect = new Rect(-17, -38, 125, 154);
            mZombieAttackRect = new Rect(-30, -38, 89, 154);
            mVariant = false;
            aRenderOffset = 8;
            mHasObject = true;

            int aPoleHit = Common.rand(100);
            int aPoleVariant;
            if (!isOnBoard() || mBoard.mLevel == 48) {
                aPoleVariant = 0;
            } else {
                aPoleVariant = aPoleHit < 10 ? 2 : aPoleHit < 35 ? 1 : 0;
            }

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (aPoleVariant == 2) {
                aBodyReanim.setImageOverride("Zombie_gargantuar_telephonepole", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_ZOMBIE));
            } else if (aPoleVariant == 1) {
                aBodyReanim.setImageOverride("Zombie_gargantuar_telephonepole", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_DUCKXING));
            }

            if (mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                aBodyReanim.setImageOverride("anim_head1", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD_REDEYE));
                mBodyHealth = 6000;
            }
            break;
        }

        case ZombieType.ZOMBIE_ZAMBONI:
            mBodyHealth = 1350;
            mAnimFrames = 2;
            mAnimTicksPerFrame = 8;
            mPosX = GameConstants.WIDE_BOARD_WIDTH + Common.rand(10);
            aRenderOffset = 8;
            playZombieReanim("anim_drive", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
            mZombieRect = new Rect(0, -13, 153, 140);
            mZombieAttackRect = new Rect(10, -13, 133, 140);
            mVariant = false;
            break;

        case ZombieType.ZOMBIE_CATAPULT:
            mBodyHealth = 850;
            mPosX = GameConstants.WIDE_BOARD_WIDTH + 25 + Common.rand(10);
            mSummonCounter = 20;
            if (isOnBoard()) {
                playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 0, 5.5f);
            } else {
                playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 8.0f);
            }
            mZombieRect = new Rect(0, -13, 153, 140);
            mZombieAttackRect = new Rect(10, -13, 133, 140);
            mVariant = false;
            break;

        case ZombieType.ZOMBIE_SNORKEL:
            mZombieRect = new Rect(12, 0, 62, 115);
            mZombieAttackRect = new Rect(-5, 0, 55, 115);
            setupWaterTrack("Zombie_snorkle_whitewater");
            setupWaterTrack("Zombie_snorkle_whitewater2");
            mVariant = false;
            mZombiePhase = ZombiePhase.PHASE_SNORKEL_WALKING;
            break;

        case ZombieType.ZOMBIE_JACK_IN_THE_BOX: {
            mBodyHealth = 500;
            mAnimTicksPerFrame = 6;

            int aDistance = 450 + Common.rand(300);
            if (Common.rand(20) == 0) { // chance of an early explosion
                aDistance /= 3;
            }
            mPhaseCounter = (int) (aDistance / mVelX) * ZOMBIE_LIMP_SPEED_FACTOR;
            mZombieAttackRect = new Rect(20, 0, 50, 115);

            if (mApp.isScaryPotterLevel()) {
                mPhaseCounter = 10;
            }
            if (isOnBoard()) {
                mZombiePhase = ZombiePhase.PHASE_JACK_IN_THE_BOX_RUNNING;
            }
            break;
        }

        case ZombieType.ZOMBIE_BOBSLED: {
            aRenderOffset = 3;

            if (theParentZombie != null) {
                int aPosition = 0;
                while (aPosition < NUM_BOBSLED_FOLLOWERS && theParentZombie.mFollowerZombieID[aPosition] != ZombieID.ZOMBIEID_NULL) {
                    aPosition++;
                }
                assert aPosition < 3;
                theParentZombie.mFollowerZombieID[aPosition] = mBoard.zombieGetID(this);
                mRelatedZombieID = mBoard.zombieGetID(theParentZombie);

                mPosX = theParentZombie.mPosX + (aPosition + 1) * 50;
                if (aPosition == 0) {
                    aRenderOffset = 1;
                    mAltitude = 9.0f;
                } else if (aPosition == 1) {
                    aRenderOffset = 2;
                    mAltitude = -7.0f;
                } else {
                    aRenderOffset = 0;
                    mAltitude = 9.0f;
                }
            } else {
                mPosX = GameConstants.WIDE_BOARD_WIDTH + 80;
                mZombieRect = new Rect(-50, 0, 275, 115);
                mHelmType = HelmType.HELMTYPE_BOBSLED;
                mHelmHealth = 300;
                mAltitude = -10.0f;
            }

            mVelX = 0.6f;
            mZombiePhase = ZombiePhase.PHASE_BOBSLED_SLIDING;
            mPhaseCounter = 500;
            mVariant = false;

            if (mFromWave == ZOMBIE_WAVE_CUTSCENE) {
                playZombieReanim("anim_jump", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 20.0f);
                mApp.reanimationGet(mBodyReanimID).mAnimTime = 1.0f;
                mAltitude = 18.0f;
            } else if (isOnBoard()) {
                playZombieReanim("anim_push", ReanimLoopType.REANIM_LOOP, 0, 30.0f);
            }
            break;
        }

        case ZombieType.ZOMBIE_FLAG: {
            mHasObject = true;
            loadPlainZombieReanim();

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            Reanimation aFlagReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_FLAG);
            aFlagReanim.playReanim("Zombie_flag", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            mSpecialHeadReanimID = mApp.reanimationGetID(aFlagReanim);
            var aTrackInstance = aBodyReanim.getTrackInstanceByName("Zombie_flaghand");
            Attachment.attachReanim(aTrackInstance.mAttachmentID, aFlagReanim, 0.0f, 0.0f);
            aBodyReanim.mFrameBasePose = 0;
            if (aZombatarRecordIndex >= 0) {
                setupZombatarFlagReanim(aZombatarRecordIndex);
            }

            mPosX = GameConstants.WIDE_BOARD_WIDTH;
            break;
        }

        case ZombieType.ZOMBIE_POGO:
            mVariant = false;
            mZombiePhase = ZombiePhase.PHASE_POGO_BOUNCING;
            mPhaseCounter = Common.rand(POGO_BOUNCE_TIME) + 1;
            mHasObject = true;
            mBodyHealth = 500;
            mZombieAttackRect = new Rect(10, 0, 30, 115);
            playZombieReanim("anim_pogo", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 40.0f);
            mApp.reanimationGet(mBodyReanimID).mAnimTime = 1.0f;
            break;

        case ZombieType.ZOMBIE_NEWSPAPER:
            mZombieAttackRect = new Rect(20, 0, 50, 115);
            mZombiePhase = ZombiePhase.PHASE_NEWSPAPER_READING;
            mShieldType = ShieldType.SHIELDTYPE_NEWSPAPER;
            mShieldHealth = 150;
            mVariant = false;
            attachShield();
            break;

        case ZombieType.ZOMBIE_BALLOON: {
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            aBodyReanim.setTruncateDisappearingFrames(null, false);

            if (isOnBoard()) {
                mAltitude = 25.0f;
                mZombiePhase = ZombiePhase.PHASE_BALLOON_FLYING;
                playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, aBodyReanim.mAnimRate);
            } else {
                setAnimRate(PvzpCommon.randRangeFloat(8.0f, 10.0f));
            }

            Reanimation aPropellerReanim = mApp.addReanimation(0.0f, 0.0f, 0, aZombieDef.mReanimationType);
            aPropellerReanim.setFramesForLayer("Propeller");
            aPropellerReanim.mLoopType = ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME;
            aPropellerReanim.attachToAnotherReanimation(aBodyReanim, "hat");

            mFlyingHealth = 20;
            mZombieRect = new Rect(36, 30, 42, 115);
            mZombieAttackRect = new Rect(20, 30, 50, 115);
            mVariant = false;
            break;
        }

        case ZombieType.ZOMBIE_DANCER:
            mScaleZombie = 0.8f;
            if (!isOnBoard()) {
                playZombieReanim("anim_armraise", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
            } else {
                mZombiePhase = ZombiePhase.PHASE_DANCER_DANCING_IN;
                mVelX = 0.5f;
                mPhaseCounter = 300 + Common.rand(12);
                playZombieReanim("anim_moonwalk", ReanimLoopType.REANIM_LOOP, 0, 24.0f);
            }
            mBodyHealth = 500;
            mVariant = false;
            break;

        case ZombieType.ZOMBIE_BACKUP_DANCER:
            mScaleZombie = 0.8f;
            if (!isOnBoard()) {
                playZombieReanim("anim_armraise", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
            }
            mZombiePhase = ZombiePhase.PHASE_DANCER_DANCING_LEFT;
            mVariant = false;
            break;

        case ZombieType.ZOMBIE_IMP:
            if (!isOnBoard()) {
                playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
            }
            if (mApp.isIZombieLevel()) {
                mBodyHealth = 70;
            }
            break;

        case ZombieType.ZOMBIE_BOSS:
            mPosX = 0.0f;
            mPosY = 0.0f;
            mZombieRect = new Rect(700, 80, 90, 430);
            mZombieAttackRect = new Rect(0, 0, 0, 0);
            aRenderLayer = RenderLayer.RENDER_LAYER_TOP;
            mBodyHealth = mApp.isAdventureMode() ? 40000 : 60000;
            if (isOnBoard()) {
                playZombieReanim("anim_enter", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 12.0f);
                mSummonCounter = 500;
                mBossHeadCounter = 5000;
                mZombiePhase = ZombiePhase.PHASE_BOSS_ENTER;
            } else {
                playZombieReanim("anim_head_idle", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
            }
            setupBossReanim();
            break;

        case ZombieType.ZOMBIE_PEA_HEAD: {
            loadPlainZombieReanim();
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("anim_head2", Reanimation.RENDER_GROUP_HIDDEN);

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (isOnBoard()) {
                aBodyReanim.setFramesForLayer("anim_walk2");
            }

            var aTrackInstance = aBodyReanim.getTrackInstanceByName("anim_head1");
            aTrackInstance.mImageOverride = Resources.getImageById(ImageID.IMAGE_BLANK);
            Reanimation aHeadReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_PEASHOOTER);
            aHeadReanim.playReanim("anim_head_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            mSpecialHeadReanimID = mApp.reanimationGetID(aHeadReanim);
            AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 0.0f, 0.0f);
            aBodyReanim.mFrameBasePose = 0;
            PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, 65.0f, -5.0f, 0.2f, -1.0f, 1.0f);

            mPhaseCounter = 150;
            mVariant = false;
            break;
        }

        case ZombieType.ZOMBIE_WALLNUT_HEAD: {
            loadPlainZombieReanim();
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("anim_head", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("Zombie_tie", Reanimation.RENDER_GROUP_HIDDEN);

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            var aTrackInstance = aBodyReanim.getTrackInstanceByName("Zombie_body");
            Reanimation aHeadReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_WALLNUT);
            aHeadReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            mSpecialHeadReanimID = mApp.reanimationGetID(aHeadReanim);
            AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 0.0f, 0.0f);
            aBodyReanim.mFrameBasePose = 0;
            PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, 50.0f, 0.0f, 0.2f, -0.8f, 0.8f);

            mHelmType = HelmType.HELMTYPE_WALLNUT;
            mHelmHealth = 1100;
            mVariant = false;
            break;
        }

        case ZombieType.ZOMBIE_TALLNUT_HEAD: {
            loadPlainZombieReanim();
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("anim_head", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("Zombie_tie", Reanimation.RENDER_GROUP_HIDDEN);

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            var aTrackInstance = aBodyReanim.getTrackInstanceByName("Zombie_body");
            Reanimation aHeadReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_TALLNUT);
            aHeadReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            mSpecialHeadReanimID = mApp.reanimationGetID(aHeadReanim);
            AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 0.0f, 0.0f);
            aBodyReanim.mFrameBasePose = 0;
            PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, 37.0f, 0.0f, 0.2f, -0.8f, 0.8f);

            mHelmType = HelmType.HELMTYPE_TALLNUT;
            mHelmHealth = 2200;
            mVariant = false;
            mPosX += 30.0f;
            break;
        }

        case ZombieType.ZOMBIE_JALAPENO_HEAD: {
            loadPlainZombieReanim();
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("anim_head", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("Zombie_tie", Reanimation.RENDER_GROUP_HIDDEN);

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            var aTrackInstance = aBodyReanim.getTrackInstanceByName("Zombie_body");
            Reanimation aHeadReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_JALAPENO);
            aHeadReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            mSpecialHeadReanimID = mApp.reanimationGetID(aHeadReanim);
            AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 0.0f, 0.0f);
            aBodyReanim.mFrameBasePose = 0;
            PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, 55.0f, -5.0f, 0.2f, -1.0f, 1.0f);

            mVariant = false;
            mBodyHealth = 500;
            int aDistance = 275 + Common.rand(175);
            mPhaseCounter = (int) (aDistance / mVelX) * ZOMBIE_LIMP_SPEED_FACTOR;
            break;
        }

        case ZombieType.ZOMBIE_GATLING_HEAD: {
            loadPlainZombieReanim();
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("anim_head2", Reanimation.RENDER_GROUP_HIDDEN);

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (isOnBoard()) {
                aBodyReanim.setFramesForLayer("anim_walk2");
            }

            var aTrackInstance = aBodyReanim.getTrackInstanceByName("anim_head1");
            aTrackInstance.mImageOverride = Resources.getImageById(ImageID.IMAGE_BLANK);
            Reanimation aHeadReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_GATLINGPEA);
            aHeadReanim.playReanim("anim_head_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            mSpecialHeadReanimID = mApp.reanimationGetID(aHeadReanim);
            AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 0.0f, 0.0f);
            aBodyReanim.mFrameBasePose = 0;
            PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, 65.0f, -5.0f, 0.2f, -1.0f, 1.0f);

            mPhaseCounter = 150;
            mVariant = false;
            break;
        }

        case ZombieType.ZOMBIE_SQUASH_HEAD: {
            loadPlainZombieReanim();
            reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
            reanimShowPrefix("anim_head2", Reanimation.RENDER_GROUP_HIDDEN);

            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (isOnBoard()) {
                aBodyReanim.setFramesForLayer("anim_walk2");
            }

            var aTrackInstance = aBodyReanim.getTrackInstanceByName("anim_head1");
            aTrackInstance.mImageOverride = Resources.getImageById(ImageID.IMAGE_BLANK);
            Reanimation aHeadReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_SQUASH);
            aHeadReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
            mSpecialHeadReanimID = mApp.reanimationGetID(aHeadReanim);
            AttachEffect aAttachEffect = Attachment.attachReanim(aTrackInstance.mAttachmentID, aHeadReanim, 0.0f, 0.0f);
            aBodyReanim.mFrameBasePose = 0;
            PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, 55.0f, -15.0f, 0.2f, -0.75f, 0.75f);

            mZombiePhase = ZombiePhase.PHASE_SQUASH_PRE_LAUNCH;
            mVariant = false;
            break;
        }

        case ZombieType.ZOMBIE_CACHED_POLEVAULTER_WITH_POLE:
        case ZombieType.NUM_ZOMBIE_TYPES:
        case ZombieType.NUM_CACHED_ZOMBIE_TYPES:
        case ZombieType.ZOMBIE_INVALID:
            break;
        }

        if (isOnBoard() && mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            float aAnimRate = PvzpCommon.randRangeFloat(8.0f, 10.0f);
            playZombieReanim("anim_aquarium_swim", ReanimLoopType.REANIM_LOOP, 0, aAnimRate);

            mZombieHeight = ZombieHeight.HEIGHT_ZOMBIQUARIUM;
            mZombiePhase = ZombiePhase.PHASE_ZOMBIQUARIUM_DRIFT;
            mPhaseCounter = 200;
            mBodyHealth = 200;
            mSummonCounter = PvzpCommon.randRangeInt(200, 400);
        }

        if (mApp.isLittleTroubleLevel() && (isOnBoard() || theFromWave == ZOMBIE_WAVE_CUTSCENE)) {
            mScaleZombie = 0.5f;
            mBodyHealth /= 4;
            mHelmHealth /= 4;
            mShieldHealth /= 4;
            mFlyingHealth /= 4;
        }

        updateAnimSpeed();
        if (mVariant) {
            reanimShowPrefix("anim_tongue", Reanimation.RENDER_GROUP_NORMAL);
        }

        mBodyMaxHealth = mBodyHealth;
        mHelmMaxHealth = mHelmHealth;
        mShieldMaxHealth = mShieldHealth;
        mFlyingMaxHealth = mFlyingHealth;
        mDead = false;
        mX = (int) mPosX;
        mY = (int) mPosY;
        mRenderOrder = mBoard.makeRenderOrder(aRenderLayer, mRow, aRenderOffset);
        if (mZombieHeight == ZombieHeight.HEIGHT_ZOMBIQUARIUM) {
            mBodyMaxHealth = 300;
        }

        if (isOnBoard()) {
            playZombieAppearSound();
            startZombieSound();
        }

        updateReanim();
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:908-926.
     */
    public static void setupDoorArms(Reanimation aReanim, boolean theShow) {
        int aArmGroup = Reanimation.RENDER_GROUP_NORMAL;
        int aDoorGroup = Reanimation.RENDER_GROUP_HIDDEN;
        if (theShow) {
            aArmGroup = Reanimation.RENDER_GROUP_HIDDEN;
            aDoorGroup = Reanimation.RENDER_GROUP_NORMAL;
        }

        aReanim.assignRenderGroupToPrefix("Zombie_outerarm_hand", aArmGroup);
        aReanim.assignRenderGroupToPrefix("Zombie_outerarm_lower", aArmGroup);
        aReanim.assignRenderGroupToPrefix("Zombie_outerarm_upper", aArmGroup);
        aReanim.assignRenderGroupToPrefix("anim_innerarm", aArmGroup);
        aReanim.assignRenderGroupToPrefix("Zombie_outerarm_screendoor", aDoorGroup);
        aReanim.assignRenderGroupToPrefix("Zombie_innerarm_screendoor", aDoorGroup);
        aReanim.assignRenderGroupToPrefix("Zombie_innerarm_screendoor_hand", aDoorGroup);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:927-967.
     */
    public static void setupReanimLayers(Reanimation aReanim, int theZombieType) {
        aReanim.assignRenderGroupToPrefix("anim_cone", Reanimation.RENDER_GROUP_HIDDEN);
        aReanim.assignRenderGroupToPrefix("anim_bucket", Reanimation.RENDER_GROUP_HIDDEN);
        aReanim.assignRenderGroupToPrefix("anim_screendoor", Reanimation.RENDER_GROUP_HIDDEN);
        aReanim.assignRenderGroupToPrefix("Zombie_flaghand", Reanimation.RENDER_GROUP_HIDDEN);
        aReanim.assignRenderGroupToPrefix("Zombie_duckytube", Reanimation.RENDER_GROUP_HIDDEN);
        aReanim.assignRenderGroupToPrefix("anim_tongue", Reanimation.RENDER_GROUP_HIDDEN);
        aReanim.assignRenderGroupToPrefix("Zombie_mustache", Reanimation.RENDER_GROUP_HIDDEN);
        setupDoorArms(aReanim, false);

        if (theZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE) {
            aReanim.assignRenderGroupToPrefix("anim_cone", Reanimation.RENDER_GROUP_NORMAL);
            aReanim.assignRenderGroupToPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
        } else if (theZombieType == ZombieType.ZOMBIE_PAIL) {
            aReanim.assignRenderGroupToPrefix("anim_bucket", Reanimation.RENDER_GROUP_NORMAL);
            aReanim.assignRenderGroupToPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
        } else if (theZombieType == ZombieType.ZOMBIE_DOOR) {
            setupDoorArms(aReanim, true);
        } else if (theZombieType == ZombieType.ZOMBIE_NEWSPAPER) {
            aReanim.assignRenderGroupToPrefix("Zombie_paper_paper", Reanimation.RENDER_GROUP_HIDDEN);
        } else if (theZombieType == ZombieType.ZOMBIE_FLAG) {
            aReanim.assignRenderGroupToPrefix("anim_innerarm", Reanimation.RENDER_GROUP_HIDDEN);
            aReanim.assignRenderGroupToTrack("Zombie_flaghand", Reanimation.RENDER_GROUP_NORMAL);
            aReanim.assignRenderGroupToTrack("Zombie_innerarm_screendoor", Reanimation.RENDER_GROUP_NORMAL);
        } else if (theZombieType == ZombieType.ZOMBIE_DUCKY_TUBE) {
            aReanim.assignRenderGroupToPrefix("Zombie_duckytube", Reanimation.RENDER_GROUP_NORMAL);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:968-981.
     */
    public void showDoorArms(boolean theShow) {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (aBodyReanim != null) {
            setupDoorArms(aBodyReanim, theShow);
            if (!mHasArm) {
                reanimShowPrefix("Zombie_outerarm_lower", Reanimation.RENDER_GROUP_HIDDEN);
                reanimShowPrefix("Zombie_outerarm_hand", Reanimation.RENDER_GROUP_HIDDEN);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:982-996.
     */
    public void reanimIgnoreClipRect(String theTrackName, boolean theIgnoreClipRect) {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        for (int i = 0; i < aBodyReanim.mDefinition.mTracks.mArrayCount; i++) {
            ReanimatorTrack aTrack = (ReanimatorTrack) aBodyReanim.mDefinition.mTracks.mArrayData[i];
            if (theTrackName.equalsIgnoreCase(aTrack.mName)) {
                aBodyReanim.mTrackInstances[i].mIgnoreClipRect = theIgnoreClipRect;
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:997-1008.
     */
    public void reanimReenableClipping() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        for (ReanimatorTrackInstance aTrackInstance : aBodyReanim.mTrackInstances) {
            aTrackInstance.mIgnoreClipRect = false;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1009-1033.
     */
    public void loadPlainZombieReanim() {
        mZombieAttackRect = new Rect(20, 0, 50, 115);
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        setupReanimLayers(aBodyReanim, mZombieType);
        if (mBoard != null) {
            enableMustache(mBoard.mMustacheMode);
            enableFuture(mBoard.mFutureMode);
        }

        if ((mBoard != null && mBoard.mPlantRow[mRow] == PlantRowType.PLANTROW_POOL && mFromWave != ZOMBIE_WAVE_CUTSCENE)
                || mZombieType == ZombieType.ZOMBIE_DUCKY_TUBE) {
            reanimShowPrefix("zombie_duckytube", Reanimation.RENDER_GROUP_NORMAL);
            reanimIgnoreClipRect("Zombie_duckytube", true);
            reanimIgnoreClipRect("Zombie_outerarm_hand", true);
            reanimIgnoreClipRect("Zombie_innerarm3", true);
            setupWaterTrack("Zombie_whitewater");
            setupWaterTrack("Zombie_whitewater2");
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1034-1063.
     */
    public Reanimation loadReanim(int theReanimationType) {
        Reanimation aBodyReanim = mApp.addReanimation(0.0f, 0.0f, 0, theReanimationType);
        mBodyReanimID = mApp.reanimationGetID(aBodyReanim);
        aBodyReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
        aBodyReanim.mIsAttachment = true;

        if (!isOnBoard()) {
            if (Common.rand(4) > 0 && aBodyReanim.trackExists("anim_idle2")) {
                float aRanimRate = PvzpCommon.randRangeFloat(12.0f, 24.0f);
                playZombieReanim("anim_idle2", ReanimLoopType.REANIM_LOOP, 0, aRanimRate);
            } else if (aBodyReanim.trackExists("anim_idle")) {
                float aRanimRate = PvzpCommon.randRangeFloat(12.0f, 18.0f);
                playZombieReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, aRanimRate);
            }

            aBodyReanim.mAnimTime = PvzpCommon.randRangeFloat(0.0f, 0.99f);
        } else {
            startWalkAnim(0);
        }

        return aBodyReanim;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:2893-2906.
     */
    public void playZombieReanim(String theTrackName, int theLoopType, int theBlendTime, float theAnimRate) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        aBodyReanim.playReanim(theTrackName, theLoopType, theBlendTime, theAnimRate);
        if (theAnimRate != 0.0f) {
            mOriginalAnimRate = theAnimRate;
        }
        updateAnimSpeed();
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8882-8890.
     */
    public void reanimShowPrefix(String theTrackPrefix, int theRenderGroup) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim != null) {
            aBodyReanim.assignRenderGroupToPrefix(theTrackPrefix, theRenderGroup);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8891-8899.
     */
    public void reanimShowTrack(String theTrackName, int theRenderGroup) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim != null) {
            aBodyReanim.assignRenderGroupToTrack(theTrackName, theRenderGroup);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8342-8358.
     */
    public PvzpParticleSystem addAttachedParticle(int thePosX, int thePosY, int theEffect) {
        if (mDead) {
            return null;
        }

        if (Attachment.isFullOfAttachments(mAttachmentID)) {
            return null;
        }

        PvzpParticleSystem aParticle = mApp.addPvzpParticle(mX + thePosX, mY + thePosY, 0, theEffect);
        if (aParticle != null) {
            Attachment.attachParticle(mAttachmentID, aParticle, thePosX, thePosY);
        }

        return aParticle;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8359-8372.
     */
    public Reanimation addAttachedReanim(int thePosX, int thePosY, int theReanimType) {
        if (mDead) {
            return null;
        }

        Reanimation aReanim = mApp.addReanimation(mX + thePosX, mY + thePosY, 0, theReanimType);
        if (aReanim != null) {
            Attachment.attachReanim(mAttachmentID, aReanim, thePosX, thePosY);
        }

        return aReanim;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9558-9574. {@code thePos} must hold at least two floats;
     * [0] receives the X, [1] the Y (upstream {@code float&,float&}).
     */
    public void getTrackPosition(String theTrackName, float[] thePos) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            thePos[0] = mPosX;
            thePos[1] = mPosY;
            return;
        }

        int aTrackIndex = aBodyReanim.findTrackIndex(theTrackName);
        SexyTransform2D aMatrix = new SexyTransform2D();
        aBodyReanim.getTrackMatrix(aTrackIndex, aMatrix);
        thePos[0] = aMatrix.m02 + mPosX;
        thePos[1] = aMatrix.m12 + mPosY;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10699-10706.
     */
    public void setupWaterTrack(String theTrackName) {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        ReanimatorTrackInstance aTrackInstance = aBodyReanim.getTrackInstanceByName(theTrackName);
        aTrackInstance.mIgnoreExtraAdditiveColor = true;
        aTrackInstance.mIgnoreColorOverride = true;
        aTrackInstance.mIgnoreClipRect = true;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10628-10656.
     */
    public void enableMustache(boolean theEnableMustache) {
        if (mFromWave == ZOMBIE_WAVE_UI) {
            return;
        }

        if (!mHasHead || isZombotany(mZombieType)) {
            return;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null || !aBodyReanim.trackExists("Zombie_mustache")) {
            return;
        }

        if (theEnableMustache) {
            aBodyReanim.assignRenderGroupToPrefix("Zombie_mustache", Reanimation.RENDER_GROUP_NORMAL);

            switch (PvzpCommon.randRangeInt(1, 3)) {
            case 1:
                aBodyReanim.setImageOverride("Zombie_mustache", null);
                break;
            case 2:
                aBodyReanim.setImageOverride("Zombie_mustache", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_MUSTACHE2));
                break;
            case 3:
                aBodyReanim.setImageOverride("Zombie_mustache", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_MUSTACHE3));
                break;
            default:
                break;
            }
        } else {
            aBodyReanim.assignRenderGroupToPrefix("Zombie_mustache", Reanimation.RENDER_GROUP_HIDDEN);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10657-10684.
     */
    public void enableFuture(boolean theEnableFuture) {
        if (mFromWave == ZOMBIE_WAVE_UI || isZombotany(mZombieType)) {
            return;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null || aBodyReanim.mReanimationType != ReanimationType.REANIM_ZOMBIE) {
            return;
        }

        if (theEnableFuture) {
            Image aImage = null;
            switch (mBoard.zombieGetID(this) % 4) {
            case 0:
                aImage = Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES1);
                break;
            case 1:
                aImage = Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES2);
                break;
            case 2:
                aImage = Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES3);
                break;
            case 3:
                aImage = Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES4);
                break;
            default:
                break;
            }
            aBodyReanim.setImageOverride("anim_head1", aImage);
        } else {
            aBodyReanim.setImageOverride("anim_head1", null);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10685-10698.
     */
    public void enableDance() {
        if (!isOnBoard()) {
            return;
        }

        if (zombieNotWalking() || isDeadOrDying()) {
            return;
        }

        if (mZombieType == ZombieType.ZOMBIE_NORMAL || mZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE
                || mZombieType == ZombieType.ZOMBIE_PAIL) {
            startWalkAnim(0);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:10542-10591.
     */
    public static void preloadZombieResources(int theZombieType) {
        ZombieDefinition aZombieDef = getZombieDefinition(theZombieType);
        if (aZombieDef.mReanimationType != ReanimationType.REANIM_NONE) {
            Reanimator.reanimatorEnsureDefinitionLoaded(aZombieDef.mReanimationType, true);
        }

        if (theZombieType == ZombieType.ZOMBIE_DIGGER) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_DIGGER_DIRT, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CHARRED_DIGGER, true);
        } else if (theZombieType == ZombieType.ZOMBIE_BOSS) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_BOSS_DRIVER, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_BOSS_FIREBALL, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_BOSS_ICEBALL, true);

            for (int aBossZombie : gBossZombieList) {
                ZombieDefinition aDef = getZombieDefinition(aBossZombie);
                Reanimator.reanimatorEnsureDefinitionLoaded(aDef.mReanimationType, true);
            }
        } else if (theZombieType == ZombieType.ZOMBIE_DANCER) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_BACKUP_DANCER, true);
        } else if (theZombieType == ZombieType.ZOMBIE_GARGANTUAR || theZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_IMP, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CHARRED_IMP, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CHARRED_GARGANTUAR, true);
        } else if (theZombieType == ZombieType.ZOMBIE_ZAMBONI) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_IMP, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CHARRED_ZAMBONI, true);
        } else if (theZombieType == ZombieType.ZOMBIE_CATAPULT) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CHARRED_CATAPULT, true);
        }

        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_PUFF, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CHARRED, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_LAWN_MOWERED_ZOMBIE, true);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8811-8838.
     */
    public void attachShield() {
        String aTrackName;
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (mShieldType == ShieldType.SHIELDTYPE_DOOR) {
            showDoorArms(true);
            reanimShowPrefix("Zombie_outerarm_screendoor", RENDER_GROUP_OVER_SHIELD);
            aTrackName = "anim_screendoor";
        } else if (mShieldType == ShieldType.SHIELDTYPE_NEWSPAPER) {
            reanimShowPrefix("Zombie_paper_hands", RENDER_GROUP_OVER_SHIELD);
            aTrackName = "Zombie_paper_paper";
        } else if (mShieldType == ShieldType.SHIELDTYPE_LADDER) {
            reanimShowPrefix("Zombie_outerarm", RENDER_GROUP_OVER_SHIELD);
            aTrackName = "Zombie_ladder_1";
        } else {
            aTrackName = null;
        }

        if (aTrackName != null) {
            aBodyReanim.assignRenderGroupToTrack(aTrackName, RENDER_GROUP_SHIELD);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8839-8879.
     */
    public void detachShield() {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim != null) {
            if (mShieldType == ShieldType.SHIELDTYPE_DOOR) {
                showDoorArms(false);
            } else if (mShieldType == ShieldType.SHIELDTYPE_NEWSPAPER) {
                reanimShowPrefix("Zombie_paper_hands", Reanimation.RENDER_GROUP_NORMAL);
            } else if (mShieldType == ShieldType.SHIELDTYPE_LADDER) {
                if (mHasArm) { // fixes lost arms regrowing after a ladder zombie places its ladder
                    reanimShowPrefix("Zombie_outerarm", Reanimation.RENDER_GROUP_NORMAL);
                }
                mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
                if (mIsEating) {
                    playZombieReanim("anim_eat", ReanimLoopType.REANIM_LOOP, 20, 0.0f);
                } else {
                    startWalkAnim(0);
                }
            }
        }

        mShieldType = ShieldType.SHIELDTYPE_NONE;
        mShieldHealth = 0;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4304-4396.
     */
    public void update() {
        assert !mDead;

        mZombieAge++;
        boolean doUpdate = false;
        if (mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && mZombieType == ZombieType.ZOMBIE_BOSS) {
            doUpdate = true;
        } else if (isOnBoard() && mBoard.mCutScene.shouldRunUpsellBoard()) {
            doUpdate = true;
        } else if (mApp.mGameScene == GameScenes.SCENE_PLAYING || !isOnBoard() || mFromWave == ZOMBIE_WAVE_WINNER) {
            doUpdate = true;
        }

        if (doUpdate) {
            if (mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED) {
                updateBurn();
            } else if (mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED) {
                updateMowered();
            } else if (mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING) {
                updateDeath();
                updateZombieWalking();
            } else {
                if (mPhaseCounter > 0 && !isImmobilizied()) {
                    mPhaseCounter--;
                }

                if (mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
                    if (mBoard.mCutScene.showZombieWalking()) {
                        updateZombieChimney();
                        updateZombieWalking();
                    }
                } else if (isOnBoard()) {
                    updatePlaying();
                }

                if (mZombieType == ZombieType.ZOMBIE_BUNGEE) {
                    updateZombieBungee();
                }
                if (mZombieType == ZombieType.ZOMBIE_POGO) {
                    updateZombiePogo();
                }

                animate();
            }

            mJustGotShotCounter--;
            if (mShieldJustGotShotCounter > 0) {
                mShieldJustGotShotCounter--;
            }
            if (mShieldRecoilCounter > 0) {
                mShieldRecoilCounter--;
            }
            if (mZombieFade > 0) {
                mZombieFade--;
                if (mZombieFade == 0) {
                    dieNoLoot();
                }
            }

            mX = (int) mPosX;
            mY = (int) mPosY;

            Attachment.attachmentUpdateAndMove(mAttachmentID, mPosX, mPosY);
            updateReanim();
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4576-4686.
     */
    public void updatePlaying() {
        assert mBodyHealth > 0 || mZombiePhase == ZombiePhase.PHASE_BOBSLED_CRASHING;

        mGroanCounter--;
        int aZombiesCount = mBoard.mZombies.mSize;
        if (mGroanCounter == 0 && Common.rand(aZombiesCount) == 0 && mHasHead && mZombieType != ZombieType.ZOMBIE_BOSS
                && !mBoard.hasLevelAwardDropped()) {
            float aPitch = 0.0f;
            if (mApp.isLittleTroubleLevel()) {
                aPitch = PvzpCommon.randRangeFloat(40.0f, 50.0f);
            }

            if (mZombieType == ZombieType.ZOMBIE_GARGANTUAR) {
                mApp.playFoley(FoleyType.FOLEY_LOW_GROAN);
            } else if (mVariant) {
                mApp.playFoleyPitch(FoleyType.FOLEY_BRAINS, aPitch);
            } else if (mApp.mSukhbirMode) {
                mApp.playFoleyPitch(FoleyType.FOLEY_SUKHBIR, aPitch);
            } else {
                mApp.playFoleyPitch(FoleyType.FOLEY_GROAN, aPitch);
            }

            mGroanCounter = Common.rand(1000) + 500;
        }

        if (mIceTrapCounter > 0) {
            mIceTrapCounter--;
            if (mIceTrapCounter == 0) {
                removeIceTrap();
                addAttachedParticle(75, 106, ParticleEffect.PARTICLE_ICE_TRAP_RELEASE);
            }
        }
        if (mChilledCounter > 0) {
            mChilledCounter--;
            if (mChilledCounter == 0) {
                updateAnimSpeed();
            }
        }
        if (mButteredCounter > 0) {
            mButteredCounter--;
            if (mButteredCounter == 0) {
                removeButter();
            }
        }

        if (mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE) {
            updateZombieRiseFromGrave();
            return;
        }

        if (!isImmobilizied()) {
            updateActions();
            updateZombiePosition();
            checkIfPreyCaught();
            checkForPool();
            checkForHighGround();
            checkForBoardEdge();
        }

        if (mZombieType == ZombieType.ZOMBIE_BOSS) {
            updateBoss();
        }

        if (!isDeadOrDying() && mFromWave != ZOMBIE_WAVE_WINNER) {
            boolean isDying = !mHasHead;
            if (mZombieType == ZombieType.ZOMBIE_ZAMBONI || mZombieType == ZombieType.ZOMBIE_CATAPULT) {
                if (mBodyHealth < 200) {
                    isDying = true;
                }
            }

            if (isDying) {
                int aDamage = 1;
                if (mZombieType == ZombieType.ZOMBIE_YETI) {
                    aDamage = 10;
                }
                if (mBodyMaxHealth >= 500) {
                    aDamage = 3;
                }

                if (Common.rand(5) == 0) {
                    takeDamage(aDamage, 9);
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4428-4536.
     */
    public void updateActions() {
        if (mZombieHeight == ZombieHeight.HEIGHT_UP_LADDER) {
            updateClimbingLadder();
        }
        if (mZombieHeight == ZombieHeight.HEIGHT_ZOMBIQUARIUM) {
            updateZombiquarium();
        }
        if (mZombieHeight == ZombieHeight.HEIGHT_OUT_OF_POOL || mZombieHeight == ZombieHeight.HEIGHT_IN_TO_POOL || mInPool) {
            updateZombiePool();
        }
        if (mZombieHeight == ZombieHeight.HEIGHT_UP_TO_HIGH_GROUND || mZombieHeight == ZombieHeight.HEIGHT_DOWN_OFF_HIGH_GROUND) {
            updateZombieHighGround();
        }
        if (mZombieHeight == ZombieHeight.HEIGHT_FALLING) {
            updateZombieFalling();
        }
        if (mZombieHeight == ZombieHeight.HEIGHT_IN_TO_CHIMNEY) {
            updateZombieChimney();
        }

        switch (mZombieType) {
        case ZombieType.ZOMBIE_POLEVAULTER:
            updateZombiePolevaulter();
            break;
        case ZombieType.ZOMBIE_CATAPULT:
            updateZombieCatapult();
            break;
        case ZombieType.ZOMBIE_DOLPHIN_RIDER:
            updateZombieDolphinRider();
            break;
        case ZombieType.ZOMBIE_SNORKEL:
            updateZombieSnorkel();
            break;
        case ZombieType.ZOMBIE_BALLOON:
            updateZombieFlyer();
            break;
        case ZombieType.ZOMBIE_NEWSPAPER:
            updateZombieNewspaper();
            break;
        case ZombieType.ZOMBIE_DIGGER:
            updateZombieDigger();
            break;
        case ZombieType.ZOMBIE_JACK_IN_THE_BOX:
            updateZombieJackInTheBox();
            break;
        case ZombieType.ZOMBIE_GARGANTUAR:
        case ZombieType.ZOMBIE_REDEYE_GARGANTUAR:
            updateZombieGargantuar();
            break;
        case ZombieType.ZOMBIE_BOBSLED:
            updateZombieBobsled();
            break;
        case ZombieType.ZOMBIE_ZAMBONI:
            updateZamboni();
            break;
        case ZombieType.ZOMBIE_LADDER:
            updateLadder();
            break;
        case ZombieType.ZOMBIE_YETI:
            updateYeti();
            break;
        case ZombieType.ZOMBIE_DANCER:
            updateZombieDancer();
            break;
        case ZombieType.ZOMBIE_BACKUP_DANCER:
            updateZombieBackupDancer();
            break;
        case ZombieType.ZOMBIE_IMP:
            updateZombieImp();
            break;
        case ZombieType.ZOMBIE_PEA_HEAD:
            updateZombiePeaHead();
            break;
        case ZombieType.ZOMBIE_JALAPENO_HEAD:
            updateZombieJalapenoHead();
            break;
        case ZombieType.ZOMBIE_GATLING_HEAD:
            updateZombieGatlingHead();
            break;
        case ZombieType.ZOMBIE_SQUASH_HEAD:
            updateZombieSquashHead();
            break;
        default:
            break;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4931-5020.
     */
    public void animate() {
        mPrevFrame = mFrame;
        if (mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_POPPING
                || mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MADDENING
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_STUNNED
                || isImmobilizied()) {
            return;
        }

        mAnimCounter++;
        if (mYuckyFace) {
            updateYuckyFace();
        }

        if (mIsEating && mHasHead) {
            int aFrameLength = 6;
            if (mChilledCounter > 0) {
                aFrameLength = 12;
            }
            if (mAnimCounter >= mAnimFrames * aFrameLength) {
                mAnimCounter = aFrameLength;
            }
            mFrame = mAnimCounter / aFrameLength;

            Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
            if (aBodyReanim != null) {
                float aLeftHandTime = 0.14f;
                float aRightHandTime = 0.68f;
                if (mZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
                    aLeftHandTime = 0.38f;
                    aRightHandTime = 0.8f;
                } else if (mZombieType == ZombieType.ZOMBIE_NEWSPAPER || mZombieType == ZombieType.ZOMBIE_LADDER) {
                    aLeftHandTime = 0.42f;
                    aRightHandTime = 0.42f;
                } else if (mZombieType == ZombieType.ZOMBIE_JACK_IN_THE_BOX) {
                    aLeftHandTime = 0.53f;
                    aRightHandTime = 0.53f;
                } else if (mZombieType == ZombieType.ZOMBIE_BOBSLED) {
                    aLeftHandTime = 0.33f;
                    aRightHandTime = 0.83f;
                } else if (mZombieType == ZombieType.ZOMBIE_IMP) {
                    aLeftHandTime = 0.33f;
                    aRightHandTime = 0.79f;
                }

                if (aBodyReanim.shouldTriggerTimedEvent(aLeftHandTime) || aBodyReanim.shouldTriggerTimedEvent(aRightHandTime)) {
                    animateChewSound();
                    animateChewEffect();
                }
            } else {
                if (mAnimCounter == 4 * aFrameLength) {
                    animateChewSound();
                }
                if (mAnimCounter == 7 * aFrameLength && !mMindControlled) {
                    animateChewEffect();
                }
            }
        } else {
            if (mAnimCounter >= mAnimFrames * mAnimTicksPerFrame) {
                mAnimCounter = 0;
            }
            mFrame = mAnimCounter / mAnimTicksPerFrame;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4823-4878.
     */
    public void animateChewSound() {
        if (mZombiePhase == ZombiePhase.PHASE_SNORKEL_UP_TO_EAT) {
            return;
        }

        Plant aPlant = findPlantTarget(ZombieAttackType.ATTACKTYPE_CHEW);
        if (aPlant != null) {
            if (aPlant.mSeedType == SeedType.SEED_HYPNOSHROOM && !aPlant.mIsAsleep) {
                mApp.playFoley(FoleyType.FOLEY_FLOOP);
                aPlant.die(Plant.PLANT_REMOVED_EATEN);

                startMindControlled();
                mApp.addPvzpParticle(mPosX + 60.0f, mPosY + 40.0f, mRenderOrder + 1, ParticleEffect.PARTICLE_MIND_CONTROL);
                trySpawnLevelAward();

                mVelX = 0.17f;
                mAnimTicksPerFrame = 18;
                updateAnimSpeed();
            } else if (aPlant.mSeedType == SeedType.SEED_GARLIC) {
                if (!mYuckyFace) {
                    mYuckyFace = true;
                    mYuckyFaceCounter = 0;
                    updateAnimSpeed();
                    mApp.playFoley(FoleyType.FOLEY_CHOMP);
                }
            } else {
                if (aPlant.mSeedType == SeedType.SEED_WALLNUT || aPlant.mSeedType == SeedType.SEED_TALLNUT
                        || aPlant.mSeedType == SeedType.SEED_PUMPKINSHELL) {
                    mApp.playFoley(FoleyType.FOLEY_CHOMP_SOFT);
                } else {
                    mApp.playFoley(FoleyType.FOLEY_CHOMP);
                }
            }
        } else {
            if (mMindControlled) {
                mApp.playFoley(FoleyType.FOLEY_CHOMP_SOFT);
            } else {
                mApp.playFoley(FoleyType.FOLEY_CHOMP);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4879-4930.
     */
    public void animateChewEffect() {
        if (mZombiePhase == ZombiePhase.PHASE_SNORKEL_UP_TO_EAT) {
            return;
        }

        if (mApp.isIZombieLevel()) {
            GridItem aBrain = mBoard.mChallenge.iZombieGetBrainTarget(this);
            if (aBrain != null) {
                aBrain.mTransparentCounter = Math.max(aBrain.mTransparentCounter, 25);
                return;
            }
        }

        Plant aPlant = findPlantTarget(ZombieAttackType.ATTACKTYPE_CHEW);
        if (aPlant != null) {
            if (aPlant.mSeedType == SeedType.SEED_WALLNUT || aPlant.mSeedType == SeedType.SEED_TALLNUT) {
                int aRenderOrder = mBoard.makeRenderOrder(RenderLayer.RENDER_LAYER_PROJECTILE, mRow, 0);
                ZombieDrawPosition aDrawPos = getDrawPos();

                float aPosX = mPosX + 37.0f;
                float aPosY = mPosY + 40.0f + aDrawPos.mBodyY;
                if (mZombieType == ZombieType.ZOMBIE_SNORKEL || mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER) {
                    aPosX -= 7.0f;
                    aPosY += 70.0f;
                } else if (isWalkingBackwards()) {
                    aPosX += 47.0f;
                } else if (mZombieType == ZombieType.ZOMBIE_BALLOON) {
                    aPosY += 47.0f;
                } else if (mZombieType == ZombieType.ZOMBIE_IMP) {
                    aPosX += 24.0f;
                    aPosY += 40.0f;
                }

                mApp.addPvzpParticle(aPosX, aPosY, aRenderOrder, ParticleEffect.PARTICLE_WALLNUT_EAT_SMALL);
            }

            aPlant.mEatenFlashCountdown = Math.max(aPlant.mEatenFlashCountdown, 25);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4251-4283.
     */
    public void updateZombiePosition() {
        if (mZombieType == ZombieType.ZOMBIE_BUNGEE || mZombieType == ZombieType.ZOMBIE_BOSS
                || mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE || mZombieHeight == ZombieHeight.HEIGHT_ZOMBIQUARIUM) {
            return;
        }

        updateZombieWalking();
        checkForZombieStep();

        if (mBlowingAway) {
            mPosX += 10.0f;
            if (mX > 850) {
                dieWithLoot();
                return;
            }
        }

        if (mZombieHeight == ZombieHeight.HEIGHT_ZOMBIE_NORMAL) {
            float aDesiredY = getPosYBasedOnRow(mRow);
            if (mPosY < aDesiredY) {
                mPosY += Math.min(aDesiredY - mPosY, 1.0f);
            } else if (mPosY > aDesiredY) {
                mPosY -= Math.min(mPosY - aDesiredY, 1.0f);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4104-4218.
     */
    public void updateZombieWalking() {
        if (zombieNotWalking()) {
            return;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim != null) {
            float aSpeed;
            if (isBouncingPogo() || mZombiePhase == ZombiePhase.PHASE_BALLOON_FLYING || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING
                    || mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL || mZombieType == ZombieType.ZOMBIE_CATAPULT) {
                aSpeed = mVelX;
                if (isMovingAtChilledSpeed()) {
                    aSpeed *= CHILLED_SPEED_FACTOR;
                }
            } else if (mZombieType == ZombieType.ZOMBIE_ZAMBONI || mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING
                    || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP || isBobsledTeamWithSled()
                    || mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT || mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL) {
                aSpeed = mVelX;
            } else if (aBodyReanim.trackExists("_ground")) {
                aSpeed = aBodyReanim.getTrackVelocity("_ground") * mScaleZombie;
            } else {
                aSpeed = mVelX;
                if (isMovingAtChilledSpeed()) {
                    aSpeed *= CHILLED_SPEED_FACTOR;
                }
            }

            if (isWalkingBackwards() || mZombiePhase == ZombiePhase.PHASE_DANCER_DANCING_IN) {
                mPosX += aSpeed;
            } else {
                mPosX -= aSpeed;
            }

            if (mZombieType == ZombieType.ZOMBIE_FOOTBALL && mFromWave != ZOMBIE_WAVE_WINNER) {
                if (aBodyReanim.shouldTriggerTimedEvent(0.03f)) {
                    mApp.addPvzpParticle(mX + 81, mY + 106, mRenderOrder - 1, ParticleEffect.PARTICLE_DUST_FOOT);
                }
                if (aBodyReanim.shouldTriggerTimedEvent(0.61f)) {
                    mApp.addPvzpParticle(mX + 87, mY + 110, mRenderOrder - 1, ParticleEffect.PARTICLE_DUST_FOOT);
                }
            }
            if (mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT) {
                if (aBodyReanim.shouldTriggerTimedEvent(0.16f)) {
                    mApp.addPvzpParticle(mX + 81, mY + 106, mRenderOrder - 1, ParticleEffect.PARTICLE_DUST_FOOT);
                }
                if (aBodyReanim.shouldTriggerTimedEvent(0.67f)) {
                    mApp.addPvzpParticle(mX + 87, mY + 110, mRenderOrder - 1, ParticleEffect.PARTICLE_DUST_FOOT);
                }
            }
        } else {
            boolean doWalk = false;
            if (mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                    || mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING
                    || mZombieType == ZombieType.ZOMBIE_DANCER
                    || mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER
                    || mZombieType == ZombieType.ZOMBIE_BOBSLED
                    || mZombieType == ZombieType.ZOMBIE_POGO
                    || mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER
                    || mZombieType == ZombieType.ZOMBIE_BALLOON) {
                doWalk = true;
            } else if (mZombieType == ZombieType.ZOMBIE_SNORKEL && mInPool) {
                doWalk = true;
            } else if (mFrame >= 0 && mFrame <= 2) {
                doWalk = true;
            } else if (mFrame >= 6 && mFrame <= 8) {
                doWalk = true;
            }

            if (doWalk) {
                float aSpeed = mVelX;
                if (isMovingAtChilledSpeed()) {
                    aSpeed *= CHILLED_SPEED_FACTOR;
                }

                if (isWalkingBackwards()) {
                    mPosX += aSpeed;
                } else {
                    mPosX -= aSpeed;
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4243-4250.
     */
    public void checkForZombieStep() {
        if ((mZombieType == ZombieType.ZOMBIE_ZAMBONI || mZombieType == ZombieType.ZOMBIE_CATAPULT) && !mFlatTires) {
            checkSquish(ZombieAttackType.ATTACKTYPE_DRIVE_OVER);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4537-4575.
     */
    public void checkForBoardEdge() {
        if (isWalkingBackwards() && mPosX > 850.0f) {
            dieNoLoot();
            return;
        }

        int aEdgeX = GameConstants.BOARD_EDGE;
        if (mZombieType == ZombieType.ZOMBIE_GARGANTUAR || mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR
                || mZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
            aEdgeX = -150;
        } else if (mZombieType == ZombieType.ZOMBIE_CATAPULT || mZombieType == ZombieType.ZOMBIE_FOOTBALL
                || mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
            aEdgeX = -175;
        } else if (mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER || mZombieType == ZombieType.ZOMBIE_DANCER
                || mZombieType == ZombieType.ZOMBIE_SNORKEL) {
            aEdgeX = -130;
        }

        if (mX <= aEdgeX && mHasHead) {
            if (mApp.isIZombieLevel()) {
                dieNoLoot();
            } else {
                mBoard.zombiesWon(this);
            }
        }
        if (mX <= aEdgeX + 70 && !mHasHead) {
            takeDamage(1800, 9);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8176-8183.
     */
    public void setRow(int theRow) {
        mRow = theRow;
        mRenderOrder = mBoard.makeRenderOrder(RenderLayer.RENDER_LAYER_ZOMBIE, mRow, 4);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:7999-8031.
     */
    public float getPosYBasedOnRow(int theRow) {
        if (!isOnBoard()) {
            return 0.0f;
        }

        if (isOnHighGround()) {
            if (mAltitude < GameConstants.HIGH_GROUND_HEIGHT) {
                mZombieHeight = ZombieHeight.HEIGHT_UP_TO_HIGH_GROUND;
            }
            mOnHighGround = true;
        }

        float aPosY = mBoard.getPosYBasedOnRow(mPosX + 40.0f, theRow) - 30.0f;
        if (mZombieType == ZombieType.ZOMBIE_BALLOON) {
            aPosY -= 30.0f;
        } else if (mZombieType == ZombieType.ZOMBIE_POGO) {
            aPosY -= 16.0f;
        }

        return aPosY;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8298-8317.
     */
    public Rect getZombieRect() {
        Rect aZombieRect = new Rect(mZombieRect.mX, mZombieRect.mY, mZombieRect.mWidth, mZombieRect.mHeight);
        if (isWalkingBackwards()) {
            aZombieRect.mX = mWidth - aZombieRect.mX - aZombieRect.mWidth;
        }

        ZombieDrawPosition aDrawPos = getDrawPos();
        aZombieRect.mX += mX;
        aZombieRect.mY += mY + (int) aDrawPos.mBodyY;
        if (aDrawPos.mClipHeight > CLIP_HEIGHT_LIMIT) {
            aZombieRect.mHeight -= (int) aDrawPos.mClipHeight;
            aZombieRect.mHeight = Math.max(aZombieRect.mHeight, 0);
        }

        return aZombieRect;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8318-8341.
     */
    public Rect getZombieAttackRect() {
        Rect aAttackRect = new Rect(mZombieAttackRect.mX, mZombieAttackRect.mY, mZombieAttackRect.mWidth, mZombieAttackRect.mHeight);
        if (mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP) {
            aAttackRect = new Rect(-40, 0, 100, 115);
        }

        if (isWalkingBackwards()) {
            aAttackRect.mX = mWidth - aAttackRect.mX - aAttackRect.mWidth;
        }

        ZombieDrawPosition aDrawPos = getDrawPos();
        aAttackRect.mX += mX;
        aAttackRect.mY += mY + (int) aDrawPos.mBodyY;
        if (aDrawPos.mClipHeight > CLIP_HEIGHT_LIMIT) {
            aAttackRect.mHeight -= (int) aDrawPos.mClipHeight;
        }

        return aAttackRect;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:5074-5103.
     */
    public boolean isWalkingBackwards() {
        if (mMindControlled) {
            return true;
        }

        if (mZombieHeight == ZombieHeight.HEIGHT_ZOMBIQUARIUM) {
            if (mVelZ < 1.5707964f || mVelZ > 4.712389f) {
                return true;
            }
        }

        if (mZombieType == ZombieType.ZOMBIE_DIGGER) {
            if (mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING || mZombiePhase == ZombiePhase.PHASE_DIGGER_STUNNED
                    || mZombiePhase == ZombiePhase.PHASE_DIGGER_WALKING) {
                return true;
            } else if (mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING || mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED
                    || mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED) {
                return mHasObject;
            }

            return false;
        }

        return mZombieType == ZombieType.ZOMBIE_YETI && !mHasObject;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:3954-4021.
     */
    public boolean zombieNotWalking() {
        if (mIsEating || isImmobilizied()) {
            return true;
        }

        if (mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_POPPING
                || mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MADDENING
                || mZombiePhase == ZombiePhase.PHASE_GARGANTUAR_THROWING
                || mZombiePhase == ZombiePhase.PHASE_GARGANTUAR_SMASHING
                || mZombiePhase == ZombiePhase.PHASE_CATAPULT_LAUNCHING
                || mZombiePhase == ZombiePhase.PHASE_CATAPULT_RELOADING
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_STUNNED
                || mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS
                || mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_WITH_LIGHT
                || mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_HOLD
                || mZombiePhase == ZombiePhase.PHASE_DANCER_RISING
                || mZombiePhase == ZombiePhase.PHASE_IMP_GETTING_THROWN
                || mZombiePhase == ZombiePhase.PHASE_IMP_LANDING
                || mZombiePhase == ZombiePhase.PHASE_LADDER_PLACING
                || mZombieHeight == ZombieHeight.HEIGHT_IN_TO_CHIMNEY
                || mZombieHeight == ZombieHeight.HEIGHT_GETTING_BUNGEE_DROPPED
                || mZombieHeight == ZombieHeight.HEIGHT_ZOMBIQUARIUM
                || mZombieType == ZombieType.ZOMBIE_BUNGEE
                || mZombieType == ZombieType.ZOMBIE_BOSS
                || mZombiePhase == ZombiePhase.PHASE_DANCER_RAISE_LEFT_1
                || mZombiePhase == ZombiePhase.PHASE_DANCER_WALK_TO_RAISE
                || mZombiePhase == ZombiePhase.PHASE_DANCER_RAISE_LEFT_2) {
            return true;
        }

        if (mZombieType == ZombieType.ZOMBIE_DANCER || mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
            Zombie aLeader;
            if (mZombieType == ZombieType.ZOMBIE_DANCER) {
                aLeader = this;
            } else {
                aLeader = mBoard.zombieTryToGet(mRelatedZombieID);
            }

            if (aLeader != null) {
                if (aLeader.isImmobilizied() || aLeader.mIsEating) {
                    return true;
                }

                for (int i = 0; i < NUM_BACKUP_DANCERS; i++) {
                    Zombie aDancer = mBoard.zombieTryToGet(aLeader.mFollowerZombieID[i]);
                    if (aDancer != null && (aDancer.isImmobilizied() || aDancer.mIsEating)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:3932-3951.
     */
    public float zombieTargetLeadX(float theTime) {
        float aSpeed = mVelX;
        if (mChilledCounter > 0) {
            aSpeed *= CHILLED_SPEED_FACTOR;
        }
        if (isWalkingBackwards()) {
            aSpeed = -aSpeed;
        }
        if (zombieNotWalking()) {
            aSpeed = 0.0f;
        }

        Rect aZombieRect = getZombieRect();
        float aCurrentPosX = aZombieRect.mX + aZombieRect.mWidth / 2;
        float aDisplacementX = aSpeed * theTime;
        return aCurrentPosX - aDisplacementX;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6589-6626.
     */
    public boolean isMovingAtChilledSpeed() {
        if (mChilledCounter > 0) {
            return true;
        }

        if (mZombieType == ZombieType.ZOMBIE_DANCER || mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
            Zombie aLeader;
            if (mZombieType == ZombieType.ZOMBIE_DANCER) {
                aLeader = this;
            } else {
                aLeader = mBoard.zombieTryToGet(mRelatedZombieID);
            }

            if (aLeader != null) {
                if (aLeader.mChilledCounter > 0) {
                    return true;
                }

                for (int i = 0; i < NUM_BACKUP_DANCERS; i++) {
                    Zombie aDancer = mBoard.zombieTryToGet(aLeader.mFollowerZombieID[i]);
                    if (aDancer != null && aDancer.mChilledCounter > 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6584-6588.
     */
    public boolean isImmobilizied() {
        return mIceTrapCounter > 0 || mButteredCounter > 0;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9617-9624.
     */
    public boolean isDeadOrDying() {
        return mDead
                || mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING
                || mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED
                || mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:4284-4294.
     */
    public boolean isOnBoard() {
        if (mFromWave == ZOMBIE_WAVE_CUTSCENE || mFromWave == ZOMBIE_WAVE_UI) {
            return false;
        }

        return mBoard != null;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9575-9579.
     */
    public boolean isFlying() {
        return mZombiePhase == ZombiePhase.PHASE_BALLOON_FLYING || mZombiePhase == ZombiePhase.PHASE_BALLOON_POPPING;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1425-1429.
     */
    public boolean isBouncingPogo() {
        return mZombiePhase >= ZombiePhase.PHASE_POGO_BOUNCING && mZombiePhase <= ZombiePhase.PHASE_POGO_FORWARD_BOUNCE_7;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9612-9616.
     */
    public boolean isBobsledTeamWithSled() {
        return getBobsledPosition() != -1;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:9580-9610.
     */
    public int getBobsledPosition() {
        if (mZombieType != ZombieType.ZOMBIE_BOBSLED) {
            return -1;
        }

        if (mRelatedZombieID == ZombieID.ZOMBIEID_NULL && mFollowerZombieID[0] == ZombieID.ZOMBIEID_NULL) {
            return -1;
        }

        if (mRelatedZombieID == ZombieID.ZOMBIEID_NULL) {
            return 0;
        }

        int anId = mBoard.zombieGetID(this);
        Zombie aLeaderZombie = mBoard.zombieGet(mRelatedZombieID);
        for (int i = 0; i < NUM_BOBSLED_FOLLOWERS; i++) {
            if (aLeaderZombie.mFollowerZombieID[i] == anId) {
                return i + 1;
            }
        }

        throw new IllegalStateException("Bobsled zombie is not in its leader's follower list");
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:3488-3500.
     */
    public boolean canLoseBodyParts() {
        return mZombieType != ZombieType.ZOMBIE_ZAMBONI
                && mZombieType != ZombieType.ZOMBIE_BUNGEE
                && mZombieType != ZombieType.ZOMBIE_CATAPULT
                && mZombieType != ZombieType.ZOMBIE_GARGANTUAR
                && mZombieType != ZombieType.ZOMBIE_REDEYE_GARGANTUAR
                && mZombieType != ZombieType.ZOMBIE_BOSS
                && mZombieHeight != ZombieHeight.HEIGHT_ZOMBIQUARIUM
                && !isFlying()
                && !isBobsledTeamWithSled();
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6627-6632.
     */
    public void setAnimRate(float theAnimRate) {
        mOriginalAnimRate = theAnimRate;
        applyAnimRate(theAnimRate);
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6633-6641.
     */
    public void applyAnimRate(float theAnimRate) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim != null) {
            aBodyReanim.mAnimRate = isMovingAtChilledSpeed() ? theAnimRate * 0.5f : theAnimRate;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6642-6696.
     */
    public void updateAnimSpeed() {
        if (!isOnBoard()) {
            return;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        if (isImmobilizied() || (mYuckyFace && mYuckyFaceCounter < 170)) {
            applyAnimRate(0.0f);
            return;
        }

        if (mZombiePhase == ZombiePhase.PHASE_SNORKEL_UP_TO_EAT || mZombiePhase == ZombiePhase.PHASE_SNORKEL_DOWN_FROM_EAT
                || isDeadOrDying()) {
            applyAnimRate(mOriginalAnimRate);
            return;
        }

        if (mIsEating) {
            if (mZombieType == ZombieType.ZOMBIE_POLEVAULTER || mZombieType == ZombieType.ZOMBIE_BALLOON
                    || mZombieType == ZombieType.ZOMBIE_IMP || mZombieType == ZombieType.ZOMBIE_DIGGER
                    || mZombieType == ZombieType.ZOMBIE_JACK_IN_THE_BOX || mZombieType == ZombieType.ZOMBIE_SNORKEL
                    || mZombieType == ZombieType.ZOMBIE_YETI) {
                applyAnimRate(20.0f);
            } else {
                applyAnimRate(36.0f);
            }
        } else {
            if (zombieNotWalking() || isBobsledTeamWithSled() || mZombieType == ZombieType.ZOMBIE_CATAPULT
                    || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING || mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL) {
                applyAnimRate(mOriginalAnimRate);
            } else if (aBodyReanim.trackExists("_ground")) {
                int aTrackIndex = aBodyReanim.findTrackIndex("_ground");
                ReanimatorTrack aTrack = (ReanimatorTrack) aBodyReanim.mDefinition.mTracks.mArrayData[aTrackIndex];
                int aLastFrame = aBodyReanim.mFrameStart + aBodyReanim.mFrameCount - 1;
                ReanimatorTransform aFirst = (ReanimatorTransform) aTrack.mTransforms.mArrayData[aBodyReanim.mFrameStart];
                ReanimatorTransform aLast = (ReanimatorTransform) aTrack.mTransforms.mArrayData[aLastFrame];
                float aDistance = aLast.mTransX - aFirst.mTransX;
                if (aDistance >= 1e-6f) {
                    float aOneOverSpeed = aBodyReanim.mFrameCount / aDistance;
                    float aAnimRate = mVelX * aOneOverSpeed * 47.0f / mScaleZombie;
                    applyAnimRate(aAnimRate);
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6747-6792.
     */
    public void startWalkAnim(int theBlendTime) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        pickRandomSpeed();
        if (mZombiePhase == ZombiePhase.PHASE_LADDER_CARRYING) {
            playZombieReanim("anim_ladderwalk", ReanimLoopType.REANIM_LOOP, theBlendTime, 0.0f);
        } else if (mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MAD) {
            playZombieReanim("anim_walk_nopaper", ReanimLoopType.REANIM_LOOP, theBlendTime, 0.0f);
        } else if (mInPool && mZombieHeight != ZombieHeight.HEIGHT_IN_TO_POOL && mZombieHeight != ZombieHeight.HEIGHT_OUT_OF_POOL
                && aBodyReanim.trackExists("anim_swim")) {
            playZombieReanim("anim_swim", ReanimLoopType.REANIM_LOOP, theBlendTime, 0.0f);
        } else if ((mZombieType == ZombieType.ZOMBIE_NORMAL || mZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE
                || mZombieType == ZombieType.ZOMBIE_PAIL) && mBoard.mDanceMode) {
            playZombieReanim("anim_dance", ReanimLoopType.REANIM_LOOP, theBlendTime, 0.0f);
        } else {
            int aWalkAnimVariant = Common.rand(2);
            if (mZombieType == ZombieType.ZOMBIE_PEA_HEAD) {
                aWalkAnimVariant = 0;
            }
            if (mZombieType == ZombieType.ZOMBIE_FLAG) {
                aWalkAnimVariant = 0;
            }

            if (aWalkAnimVariant == 0 && aBodyReanim.trackExists("anim_walk2")) {
                playZombieReanim("anim_walk2", ReanimLoopType.REANIM_LOOP, theBlendTime, 0.0f);
            } else if (aBodyReanim.trackExists("anim_walk")) {
                playZombieReanim("anim_walk", ReanimLoopType.REANIM_LOOP, theBlendTime, 0.0f);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6715-6746.
     */
    public void startEating() {
        if (mIsEating) {
            return;
        }

        mIsEating = true;

        if (mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            return;
        }

        if (mZombiePhase == ZombiePhase.PHASE_LADDER_CARRYING) {
            playZombieReanim("anim_laddereat", ReanimLoopType.REANIM_LOOP, 20, 0.0f);
        } else if (mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MAD) {
            playZombieReanim("anim_eat_nopaper", ReanimLoopType.REANIM_LOOP, 20, 0.0f);
        } else {
            if (mZombieType != ZombieType.ZOMBIE_SNORKEL) {
                playZombieReanim("anim_eat", ReanimLoopType.REANIM_LOOP, 20, 0.0f);
            }

            if (mShieldType == ShieldType.SHIELDTYPE_DOOR) {
                showDoorArms(false);
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6793-6816.
     */
    public void stopEating() {
        if (!mIsEating) {
            return;
        }

        mIsEating = false;
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);

        if (mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            return;
        }

        if (aBodyReanim != null && mZombieType != ZombieType.ZOMBIE_SNORKEL) {
            startWalkAnim(20);
        }

        if (mShieldType == ShieldType.SHIELDTYPE_DOOR) {
            showDoorArms(true);
        }

        updateAnimSpeed();
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:6817-6896.
     */
    public void checkIfPreyCaught() {
        if (mZombieType == ZombieType.ZOMBIE_BUNGEE
                || mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                || mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR
                || mZombieType == ZombieType.ZOMBIE_ZAMBONI
                || mZombieType == ZombieType.ZOMBIE_CATAPULT
                || mZombieType == ZombieType.ZOMBIE_BOSS
                || isBouncingPogo()
                || isBobsledTeamWithSled()
                || mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                || mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT
                || mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MADDENING
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE
                || mZombiePhase == ZombiePhase.PHASE_DIGGER_STUNNED
                || mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE
                || mZombiePhase == ZombiePhase.PHASE_IMP_GETTING_THROWN
                || mZombiePhase == ZombiePhase.PHASE_IMP_LANDING
                || mZombiePhase == ZombiePhase.PHASE_DANCER_RISING
                || mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS
                || mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_WITH_LIGHT
                || mZombiePhase == ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS_HOLD
                || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING
                || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING_WITHOUT_DOLPHIN
                || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL
                || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING
                || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP
                || mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL
                || mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING
                || mZombiePhase == ZombiePhase.PHASE_LADDER_PLACING
                || mZombieHeight == ZombieHeight.HEIGHT_GETTING_BUNGEE_DROPPED
                || mZombieHeight == ZombieHeight.HEIGHT_UP_LADDER
                || mZombieHeight == ZombieHeight.HEIGHT_IN_TO_POOL
                || mZombieHeight == ZombieHeight.HEIGHT_OUT_OF_POOL
                || isTangleKelpTarget()
                || mZombieHeight == ZombieHeight.HEIGHT_FALLING
                || !mHasHead
                || isFlying()) {
            return;
        }

        int aTicksBetweenEats = TICKS_BETWEEN_EATS;
        if (mChilledCounter > 0) {
            aTicksBetweenEats *= 2;
        }
        if (mZombieAge % aTicksBetweenEats != 0) {
            return;
        }

        Zombie aZombie = findZombieTarget();
        if (aZombie != null) {
            eatZombie(aZombie);
            return;
        }

        if (!mMindControlled) {
            Plant aPlant = findPlantTarget(ZombieAttackType.ATTACKTYPE_CHEW);
            if (aPlant != null) {
                eatPlant(aPlant);
                return;
            }
        }

        if (mApp.isIZombieLevel() && mBoard.mChallenge.iZombieEatBrain(this)) {
            return;
        }

        if (mIsEating) {
            stopEating();
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:7026-7126.
     */
    public void eatPlant(Plant thePlant) {
        if (mZombiePhase == ZombiePhase.PHASE_DANCER_DANCING_IN) {
            mPhaseCounter = 1;
            return;
        }

        if (mYuckyFace) {
            return;
        }

        if (mBoard.getLadderAt(thePlant.mPlantCol, thePlant.mRow) != null && mZombieType != ZombieType.ZOMBIE_DIGGER) {
            // digger zombies ignore ladders
            stopEating();

            if (mZombieHeight == ZombieHeight.HEIGHT_ZOMBIE_NORMAL && mUseLadderCol != thePlant.mPlantCol) {
                mZombieHeight = ZombieHeight.HEIGHT_UP_LADDER;
                mUseLadderCol = thePlant.mPlantCol;
            }

            return;
        }

        startEating();
        if (thePlant.mSeedType == SeedType.SEED_JALAPENO
                || thePlant.mSeedType == SeedType.SEED_CHERRYBOMB
                || thePlant.mSeedType == SeedType.SEED_DOOMSHROOM
                || thePlant.mSeedType == SeedType.SEED_ICESHROOM
                || thePlant.mSeedType == SeedType.SEED_HYPNOSHROOM
                || thePlant.mState == PlantState.STATE_FLOWERPOT_INVULNERABLE
                || thePlant.mState == PlantState.STATE_LILYPAD_INVULNERABLE
                || thePlant.mState == PlantState.STATE_SQUASH_LOOK
                || thePlant.mState == PlantState.STATE_SQUASH_PRE_LAUNCH) {
            if (!thePlant.mIsAsleep) {
                return;
            }
        }
        if (thePlant.mSeedType == SeedType.SEED_POTATOMINE && thePlant.mState != PlantState.STATE_NOTREADY) {
            return;
        }

        boolean triggered = false;
        if (thePlant.mSeedType == SeedType.SEED_BLOVER) {
            triggered = true;
        }
        if (thePlant.mSeedType == SeedType.SEED_ICESHROOM && !thePlant.mIsAsleep) {
            triggered = true;
        }
        if (triggered) {
            thePlant.doSpecial();
            return;
        }

        if (mChilledCounter > 0 && mZombieAge % 2 == 1) {
            return;
        }

        if (mApp.isIZombieLevel() && thePlant.mSeedType == SeedType.SEED_SUNFLOWER) {
            int aStageBeforeChew = thePlant.mPlantHealth / 40;
            int aStageAfterChew = (thePlant.mPlantHealth - DAMAGE_PER_EAT) / 40;
            if (aStageAfterChew < aStageBeforeChew || thePlant.mPlantHealth - DAMAGE_PER_EAT <= 0) {
                mBoard.addCoin(thePlant.mX, thePlant.mY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
            }
        }

        thePlant.takeDamage(DAMAGE_PER_EAT);
        thePlant.mRecentlyEatenCountdown = 50;
        if (mApp.isIZombieLevel() && mJustGotShotCounter < -500) {
            if (thePlant.mSeedType == SeedType.SEED_WALLNUT || thePlant.mSeedType == SeedType.SEED_TALLNUT
                    || thePlant.mSeedType == SeedType.SEED_PUMPKINSHELL) {
                thePlant.takeDamage(DAMAGE_PER_EAT);
            }
        }

        if (thePlant.mPlantHealth <= 0) {
            mApp.playSample(SoundType.SOUND_GULP);

            mBoard.mPlantsEaten++;
            thePlant.die(Plant.PLANT_REMOVED_EATEN);
            mBoard.mChallenge.zombieAtePlant(thePlant);

            if (mBoard.mLevel >= 2 && mBoard.mLevel <= 4 && mApp.isFirstTimeAdventureMode()) {
                if (thePlant.mPlantCol > 4 && mBoard.mPlants.mSize < 15 && thePlant.mSeedType == SeedType.SEED_PEASHOOTER) {
                    mBoard.displayAdvice("[ADVICE_PEASHOOTER_DIED]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                            AdviceType.ADVICE_PEASHOOTER_DIED);
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:7127-7136.
     */
    public void eatZombie(Zombie theZombie) {
        theZombie.takeDamage(DAMAGE_PER_EAT, 9);
        startEating();
        if (theZombie.mBodyHealth <= 0) {
            mApp.playSample(SoundType.SOUND_GULP);
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:1165-1228.
     */
    public void pickRandomSpeed() {
        if (mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL) {
            mVelX = 0.3f;
        } else if (mZombiePhase == ZombiePhase.PHASE_DIGGER_WALKING) {
            if (mApp.isIZombieLevel()) {
                mVelX = 0.23f;
            } else {
                mVelX = 0.12f;
            }
        } else if (mZombieType == ZombieType.ZOMBIE_IMP && mApp.isIZombieLevel()) {
            mVelX = 0.9f;
        } else if (mZombiePhase == ZombiePhase.PHASE_YETI_RUNNING) {
            mVelX = 0.8f;
        } else if (mZombieType == ZombieType.ZOMBIE_YETI) {
            mVelX = 0.4f;
        } else if (mZombieType == ZombieType.ZOMBIE_DANCER || mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER
                || mZombieType == ZombieType.ZOMBIE_POGO || mZombieType == ZombieType.ZOMBIE_FLAG) {
            mVelX = 0.45f;
        } else if (mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING || mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT
                || mZombieType == ZombieType.ZOMBIE_FOOTBALL || mZombieType == ZombieType.ZOMBIE_SNORKEL
                || mZombieType == ZombieType.ZOMBIE_JACK_IN_THE_BOX) {
            mVelX = PvzpCommon.randRangeFloat(0.66f, 0.68f);
        } else if (mZombiePhase == ZombiePhase.PHASE_LADDER_CARRYING || mZombieType == ZombieType.ZOMBIE_SQUASH_HEAD) {
            mVelX = PvzpCommon.randRangeFloat(0.79f, 0.81f);
        } else if (mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MAD || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING
                || mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING_WITHOUT_DOLPHIN) {
            mVelX = PvzpCommon.randRangeFloat(0.89f, 0.91f);
        } else {
            mVelX = PvzpCommon.randRangeFloat(0.23f, 0.37f);
            if (mVelX < 0.3f) {
                mAnimTicksPerFrame = 12;
            } else {
                mAnimTicksPerFrame = 15;
            }
        }

        updateAnimSpeed();
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8266-8275.
     */
    public static boolean isZombotany(int theZombieType) {
        return theZombieType == ZombieType.ZOMBIE_PEA_HEAD
                || theZombieType == ZombieType.ZOMBIE_WALLNUT_HEAD
                || theZombieType == ZombieType.ZOMBIE_TALLNUT_HEAD
                || theZombieType == ZombieType.ZOMBIE_JALAPENO_HEAD
                || theZombieType == ZombieType.ZOMBIE_GATLING_HEAD
                || theZombieType == ZombieType.ZOMBIE_SQUASH_HEAD;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8277-8292.
     */
    public static boolean zombieTypeCanGoInPool(int theZombieType) {
        return theZombieType == ZombieType.ZOMBIE_NORMAL
                || theZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE
                || theZombieType == ZombieType.ZOMBIE_PAIL
                || theZombieType == ZombieType.ZOMBIE_FLAG
                || theZombieType == ZombieType.ZOMBIE_SNORKEL
                || theZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER
                || theZombieType == ZombieType.ZOMBIE_PEA_HEAD
                || theZombieType == ZombieType.ZOMBIE_WALLNUT_HEAD
                || theZombieType == ZombieType.ZOMBIE_JALAPENO_HEAD
                || theZombieType == ZombieType.ZOMBIE_GATLING_HEAD
                || theZombieType == ZombieType.ZOMBIE_TALLNUT_HEAD;
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:8293-8297.
     */
    public static boolean zombieTypeCanGoOnHighGround(int theZombieType) {
        return theZombieType != ZombieType.ZOMBIE_ZAMBONI && theZombieType != ZombieType.ZOMBIE_BOBSLED;
    }

    // ------------------------------------------------------------------
    // Serialization (SaveGame.cpp:774-855 SyncZombieTailPortable)
    // ------------------------------------------------------------------

    /**
     * Ported from src/Lawn/System/SaveGame.cpp:774-855 ({@code SyncZombieTailPortable}), field order
     * verbatim. 32-bit integers go through {@link DataSync#syncUInt32(int)} (identical little-endian
     * wire bytes to the portable context's int32 sync), matching {@code GameObject.syncGameObjectPortable}.
     */
    public void syncZombieTailPortable(DataSync theSync) {
        mZombieType = theSync.syncUInt32(mZombieType);
        mZombiePhase = theSync.syncUInt32(mZombiePhase);
        mPosX = theSync.syncFloat(mPosX);
        mPosY = theSync.syncFloat(mPosY);
        mVelX = theSync.syncFloat(mVelX);
        mAnimCounter = theSync.syncUInt32(mAnimCounter);
        mGroanCounter = theSync.syncUInt32(mGroanCounter);
        mAnimTicksPerFrame = theSync.syncUInt32(mAnimTicksPerFrame);
        mAnimFrames = theSync.syncUInt32(mAnimFrames);
        mFrame = theSync.syncUInt32(mFrame);
        mPrevFrame = theSync.syncUInt32(mPrevFrame);
        mVariant = theSync.syncBool(mVariant);
        mIsEating = theSync.syncBool(mIsEating);
        mJustGotShotCounter = theSync.syncUInt32(mJustGotShotCounter);
        mShieldJustGotShotCounter = theSync.syncUInt32(mShieldJustGotShotCounter);
        mShieldRecoilCounter = theSync.syncUInt32(mShieldRecoilCounter);
        mZombieAge = theSync.syncUInt32(mZombieAge);
        mZombieHeight = theSync.syncUInt32(mZombieHeight);
        mPhaseCounter = theSync.syncUInt32(mPhaseCounter);
        mFromWave = theSync.syncUInt32(mFromWave);
        mDroppedLoot = theSync.syncBool(mDroppedLoot);
        mZombieFade = theSync.syncUInt32(mZombieFade);
        mFlatTires = theSync.syncBool(mFlatTires);
        mUseLadderCol = theSync.syncUInt32(mUseLadderCol);
        mTargetCol = theSync.syncUInt32(mTargetCol);
        mAltitude = theSync.syncFloat(mAltitude);
        mHitUmbrella = theSync.syncBool(mHitUmbrella);
        syncRect(theSync, mZombieRect);
        syncRect(theSync, mZombieAttackRect);
        mChilledCounter = theSync.syncUInt32(mChilledCounter);
        mButteredCounter = theSync.syncUInt32(mButteredCounter);
        mIceTrapCounter = theSync.syncUInt32(mIceTrapCounter);
        mMindControlled = theSync.syncBool(mMindControlled);
        mBlowingAway = theSync.syncBool(mBlowingAway);
        mHasHead = theSync.syncBool(mHasHead);
        mHasArm = theSync.syncBool(mHasArm);
        mHasObject = theSync.syncBool(mHasObject);
        mInPool = theSync.syncBool(mInPool);
        mOnHighGround = theSync.syncBool(mOnHighGround);
        mYuckyFace = theSync.syncBool(mYuckyFace);
        mYuckyFaceCounter = theSync.syncUInt32(mYuckyFaceCounter);
        mHelmType = theSync.syncUInt32(mHelmType);
        mBodyHealth = theSync.syncUInt32(mBodyHealth);
        mBodyMaxHealth = theSync.syncUInt32(mBodyMaxHealth);
        mHelmHealth = theSync.syncUInt32(mHelmHealth);
        mHelmMaxHealth = theSync.syncUInt32(mHelmMaxHealth);
        mShieldType = theSync.syncUInt32(mShieldType);
        mShieldHealth = theSync.syncUInt32(mShieldHealth);
        mShieldMaxHealth = theSync.syncUInt32(mShieldMaxHealth);
        mFlyingHealth = theSync.syncUInt32(mFlyingHealth);
        mFlyingMaxHealth = theSync.syncUInt32(mFlyingMaxHealth);
        mDead = theSync.syncBool(mDead);
        mRelatedZombieID = theSync.syncUInt32(mRelatedZombieID);
        for (int i = 0; i < MAX_ZOMBIE_FOLLOWERS; i++) {
            mFollowerZombieID[i] = theSync.syncUInt32(mFollowerZombieID[i]);
        }
        mPlayingSong = theSync.syncBool(mPlayingSong);
        mParticleOffsetX = theSync.syncUInt32(mParticleOffsetX);
        mParticleOffsetY = theSync.syncUInt32(mParticleOffsetY);
        mAttachmentID.value = theSync.syncUInt32(mAttachmentID.value);
        mSummonCounter = theSync.syncUInt32(mSummonCounter);
        mBodyReanimID = theSync.syncUInt32(mBodyReanimID);
        mScaleZombie = theSync.syncFloat(mScaleZombie);
        mVelZ = theSync.syncFloat(mVelZ);
        mOriginalAnimRate = theSync.syncFloat(mOriginalAnimRate);
        mTargetPlantID = theSync.syncUInt32(mTargetPlantID);
        mBossMode = theSync.syncUInt32(mBossMode);
        mTargetRow = theSync.syncUInt32(mTargetRow);
        mBossBungeeCounter = theSync.syncUInt32(mBossBungeeCounter);
        mBossStompCounter = theSync.syncUInt32(mBossStompCounter);
        mBossHeadCounter = theSync.syncUInt32(mBossHeadCounter);
        mBossFireBallReanimID = theSync.syncUInt32(mBossFireBallReanimID);
        mSpecialHeadReanimID = theSync.syncUInt32(mSpecialHeadReanimID);
        mFireballRow = theSync.syncUInt32(mFireballRow);
        mIsFireBall = theSync.syncBool(mIsFireBall);
        mMoweredReanimID = theSync.syncUInt32(mMoweredReanimID);
        mLastPortalX = theSync.syncUInt32(mLastPortalX);
        mZombatarHeadReanimID = theSync.syncUInt32(mZombatarHeadReanimID);
    }

    /** Upstream {@code SyncRectPortable}: x, y, width, height as int32. */
    private static void syncRect(DataSync theSync, Rect theRect) {
        theRect.mX = theSync.syncUInt32(theRect.mX);
        theRect.mY = theSync.syncUInt32(theRect.mY);
        theRect.mWidth = theSync.syncUInt32(theRect.mWidth);
        theRect.mHeight = theSync.syncUInt32(theRect.mHeight);
    }

    // ------------------------------------------------------------------
    // ZombieRender.java delegators (Zombie.cpp:5022-6290, 9365-9400, 9712-9750, 10526-10541)
    // ------------------------------------------------------------------
    /**
     * Ported from src/Lawn/Zombie.cpp:6290-6342.
     */
    public void draw(Graphics g) {
        if (mZombieHeight == ZombieHeight.HEIGHT_GETTING_BUNGEE_DROPPED) {
            return;
        }

        ZombieDrawPosition aDrawPos = getDrawPos();

        if (mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON && !setupDrawZombieWon(g)) {
            return;
        }

        if (mIceTrapCounter > 0) {
            drawIceTrap(g, aDrawPos, false);
        }
        if (mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL || mFromWave == ZOMBIE_WAVE_UI) {
            if (mBodyReanimID != ReanimationID.REANIMATIONID_NULL) {
                drawReanim(g, aDrawPos, Reanimation.RENDER_GROUP_NORMAL);
            }
            // else: upstream would DrawZombie() from the IMAGE_ZOMBIE parts atlas, which is dead code
            // (commented out at Zombie.cpp:6308) and has no ported asset.
        }
        if (mIceTrapCounter > 0) {
            drawIceTrap(g, aDrawPos, true);
        }
        if (mButteredCounter > 0) {
            drawButter(g, aDrawPos);
        }

        if (mAttachmentID.value != AttachmentID.ATTACHMENTID_NULL) {
            Graphics theParticleGraphics = new Graphics(g);
            makeParentGraphicsFrame(theParticleGraphics);
            theParticleGraphics.mTransY += aDrawPos.mBodyY;

            if (aDrawPos.mClipHeight > CLIP_HEIGHT_LIMIT) {
                float aDrawHeight = 120.0f - aDrawPos.mClipHeight + 21.0f;
                theParticleGraphics.clipRect(mX + (int) aDrawPos.mImageOffsetX - 400,
                        mY + (int) aDrawPos.mImageOffsetY - 28, 920, (int) aDrawHeight);
            }

            Attachment.attachmentDraw(mAttachmentID, theParticleGraphics, false);
        }

        g.clearClipRect();
    }

    public void updateReanim() {
        ZombieRender.updateReanim(this);
    }

    public ZombieDrawPosition getDrawPos() {
        return ZombieRender.getDrawPos(this);
    }

    public void drawBobsledReanim(Graphics g, ZombieDrawPosition theDrawPos, boolean theBeforeZombie) {
        ZombieRender.drawBobsledReanim(this, g, theDrawPos, theBeforeZombie);
    }

    public void drawBungeeReanim(Graphics g) {
        ZombieRender.drawBungeeReanim(this, g);
    }

    public void drawBungeeTarget(Graphics g) {
        ZombieRender.drawBungeeTarget(this, g);
    }

    public void drawDancerReanim(Graphics g) {
        ZombieRender.drawDancerReanim(this, g);
    }

    public void drawReanim(Graphics g, ZombieDrawPosition theDrawPos, int theBaseRenderGroup) {
        ZombieRender.drawReanim(this, g, theDrawPos, theBaseRenderGroup);
    }

    public void drawBungeeCord(Graphics g, int theOffsetX) {
        ZombieRender.drawBungeeCord(this, g, theOffsetX);
    }

    public void drawIceTrap(Graphics g, ZombieDrawPosition theDrawPos, boolean theFront) {
        ZombieRender.drawIceTrap(this, g, theDrawPos, theFront);
    }

    public void drawButter(Graphics g, ZombieDrawPosition theDrawPos) {
        ZombieRender.drawButter(this, g, theDrawPos);
    }

    public boolean hasShadow() {
        return ZombieRender.hasShadow(this);
    }

    public boolean setupDrawZombieWon(Graphics g) {
        return ZombieRender.setupDrawZombieWon(this, g);
    }

    public void drawShadow(Graphics g) {
        ZombieRender.drawShadow(this, g);
    }

    public void drawBossFireBall(Graphics g) {
        ZombieRender.drawBossFireBall(this, g);
    }

    public void drawBossBackArm(Graphics g, ZombieDrawPosition theDrawPos) {
        ZombieRender.drawBossBackArm(this, g, theDrawPos);
    }

    public void drawBossPart(Graphics g, int theBossPart) {
        ZombieRender.drawBossPart(this, g, theBossPart);
    }

    public void drawZombiePart(Graphics g, Image theImage, int theFrame, int theRow, ZombieDrawPosition theDrawPos) {
        ZombieRender.drawZombiePart(this, g, theImage, theFrame, theRow, theDrawPos);
    }

    // ------------------------------------------------------------------
    // ZombieBoss.java delegators (Zombie.cpp:9705-10541, 10592-10627)
    // ------------------------------------------------------------------
    public void updateBoss() {
        ZombieBoss.updateBoss(this);
    }

    public void setupBossReanim() {
        ZombieBoss.setupReanim(this);
    }

    public void bossPlayIdle() {
        ZombieBoss.playIdle(this);
    }

    public void bossRVLanding() {
        ZombieBoss.rVLanding(this);
    }

    public void bossRVAttack() {
        ZombieBoss.rVAttack(this);
    }

    public void bossStompContact() {
        ZombieBoss.stompContact(this);
    }

    public boolean bossAreBungeesDone() {
        return ZombieBoss.areBungeesDone(this);
    }

    public void bossBungeeSpawn() {
        ZombieBoss.bungeeSpawn(this);
    }

    public void bossSpawnAttack() {
        ZombieBoss.spawnAttack(this);
    }

    public void bossBungeeAttack() {
        ZombieBoss.bungeeAttack(this);
    }

    public void bossSpawnContact() {
        ZombieBoss.spawnContact(this);
    }

    public void bossBungeeLeave() {
        ZombieBoss.bungeeLeave(this);
    }

    public void bossStompAttack() {
        ZombieBoss.stompAttack(this);
    }

    public boolean bossCanStompRow(int theRow) {
        return ZombieBoss.canStompRow(this, theRow);
    }

    public void bossDie() {
        ZombieBoss.die(this);
    }

    public void bossHeadAttack() {
        ZombieBoss.headAttack(this);
    }

    public void bossHeadSpitContact() {
        ZombieBoss.headSpitContact(this);
    }

    public void bossHeadSpit() {
        ZombieBoss.headSpit(this);
    }

    public void bossHeadSpitEffect() {
        ZombieBoss.headSpitEffect(this);
    }

    public void updateBossFireball() {
        ZombieBoss.updateFireball(this);
    }

    public void bossDestroyFireball() {
        ZombieBoss.destroyFireball(this);
    }

    public void bossDestroyIceballInRow() {
        ZombieBoss.destroyIceballInRow(this);
    }

    public void bossStartDeath() {
        ZombieBoss.startDeath(this);
    }

    public void applyBossSmokeParticles(boolean theEnable) {
        ZombieBoss.applySmokeParticles(this, theEnable);
    }

    // ------------------------------------------------------------------
    // ZombieStatus.java delegators (Zombie.cpp:3072-3500, 4688-4822, 6984-7025, 7137-7538,
    // 8020-8080, 8373-8422, 8459-8804, 8900-9296)
    // ------------------------------------------------------------------
    public void applyChill(boolean theIsIceTrap) {
        ZombieStatus.applyChill(this, theIsIceTrap);
    }

    public boolean canBeChilled() {
        return ZombieStatus.canBeChilled(this);
    }

    public boolean canBeFrozen() {
        return ZombieStatus.canBeFrozen(this);
    }

    public void applyButter() {
        ZombieStatus.applyButter(this);
    }

    public void removeButter() {
        ZombieStatus.removeButter(this);
    }

    public void hitIceTrap() {
        ZombieStatus.hitIceTrap(this);
    }

    public void removeIceTrap() {
        ZombieStatus.removeIceTrap(this);
    }

    public void applyBurn() {
        ZombieStatus.applyBurn(this);
    }

    public void updateBurn() {
        ZombieStatus.updateBurn(this);
    }

    public void removeColdEffects() {
        ZombieStatus.removeColdEffects(this);
    }

    public void mowDown() {
        ZombieStatus.mowDown(this);
    }

    public void updateMowered() {
        ZombieStatus.updateMowered(this);
    }

    public void startMindControlled() {
        ZombieStatus.startMindControlled(this);
    }

    public void dragUnder() {
        ZombieStatus.dragUnder(this);
    }

    public void updateYuckyFace() {
        ZombieStatus.updateYuckyFace(this);
    }

    public void showYuckyFace(boolean theShow) {
        ZombieStatus.showYuckyFace(this, theShow);
    }

    public boolean hasYuckyFaceImage() {
        return ZombieStatus.hasYuckyFaceImage(this);
    }

    public boolean isFireResistant() {
        return ZombieStatus.isFireResistant(this);
    }

    public void playDeathAnim(int theDamageFlags) {
        ZombieStatus.playDeathAnim(this, theDamageFlags);
    }

    public void updateDeath() {
        ZombieStatus.updateDeath(this);
    }

    public void dieNoLoot() {
        ZombieStatus.dieNoLoot(this);
    }

    public void dieWithLoot() {
        ZombieStatus.dieWithLoot(this);
    }

    public void dropLoot() {
        ZombieStatus.dropLoot(this);
    }

    public boolean trySpawnLevelAward() {
        return ZombieStatus.trySpawnLevelAward(this);
    }

    public void bobsledDie() {
        ZombieStatus.bobsledDie(this);
    }

    public void bobsledBurn() {
        ZombieStatus.bobsledBurn(this);
    }

    public void bungeeDie() {
        ZombieStatus.bungeeDie(this);
    }

    public void bungeeDropPlant() {
        ZombieStatus.bungeeDropPlant(this);
    }

    public void zamboniDeath(int theDamageFlags) {
        ZombieStatus.zamboniDeath(this, theDamageFlags);
    }

    public void catapultDeath(int theDamageFlags) {
        ZombieStatus.catapultDeath(this, theDamageFlags);
    }

    public void doDaisies() {
        ZombieStatus.doDaisies(this);
    }

    public void dropFlag() {
        ZombieStatus.dropFlag(this);
    }

    public void setupZombatarFlagReanim(int theRecordIndex) {
        ZombieStatus.setupZombatarFlagReanim(this, theRecordIndex);
    }

    public void applyZombatarHead(byte[] theRecord) {
        ZombieStatus.applyZombatarHead(this, theRecord);
    }

    public void dropPole() {
        ZombieStatus.dropPole(this);
    }

    public void startZombieSound() {
        ZombieStatus.startZombieSound(this);
    }

    public void stopZombieSound() {
        ZombieStatus.stopZombieSound(this);
    }

    public void playZombieAppearSound() {
        ZombieStatus.playZombieAppearSound(this);
    }

    public void balloonPropellerHatSpin(boolean theSpinning) {
        ZombieStatus.balloonPropellerHatSpin(this, theSpinning);
    }

    public void overrideParticleColor(PvzpParticleSystem aParticle) {
        ZombieStatus.overrideParticleColor(this, aParticle);
    }

    public void overrideParticleScale(PvzpParticleSystem aParticle) {
        ZombieStatus.overrideParticleScale(this, aParticle);
    }

    public void convertToNormalZombie() {
        ZombieStatus.convertToNormalZombie(this);
    }

    // ------------------------------------------------------------------
    // ZombieBehaviourA.java delegators (Zombie.cpp:1064-2810)
    // ------------------------------------------------------------------
    public int countBungeesTargetingSunFlowers() {
        return ZombieBehaviourA.countBungeesTargetingSunFlowers(this);
    }

    public void pickBungeeZombieTarget(int theColumn) {
        ZombieBehaviourA.pickBungeeZombieTarget(this, theColumn);
    }

    public void bungeeDropZombie(Zombie theDroppedZombie, int theGridX, int theGridY) {
        ZombieBehaviourA.bungeeDropZombie(this, theDroppedZombie, theGridX, theGridY);
    }

    public void bungeeStealTarget() {
        ZombieBehaviourA.bungeeStealTarget(this);
    }

    public void bungeeLiftTarget() {
        ZombieBehaviourA.bungeeLiftTarget(this);
    }

    public void bungeeLanding() {
        ZombieBehaviourA.bungeeLanding(this);
    }

    public void updateZombieBungee() {
        ZombieBehaviourA.updateZombieBungee(this);
    }

    public void pogoBreak(int theDamageFlags) {
        ZombieBehaviourA.pogoBreak(this, theDamageFlags);
    }

    public void updateZombiePogo() {
        ZombieBehaviourA.updateZombiePogo(this);
    }

    public void zombieCatapultFire(Plant thePlant) {
        ZombieBehaviourA.zombieCatapultFire(this, thePlant);
    }

    public Plant findCatapultTarget() {
        return ZombieBehaviourA.findCatapultTarget(this);
    }

    public void updateZombieCatapult() {
        ZombieBehaviourA.updateZombieCatapult(this);
    }

    public void landFlyer(int theDamageFlags) {
        ZombieBehaviourA.landFlyer(this, theDamageFlags);
    }

    public void updateZombieFlyer() {
        ZombieBehaviourA.updateZombieFlyer(this);
    }

    public void updateZombieNewspaper() {
        ZombieBehaviourA.updateZombieNewspaper(this);
    }

    public void updateZombiePolevaulter() {
        ZombieBehaviourA.updateZombiePolevaulter(this);
    }

    public void updateZombieDolphinRider() {
        ZombieBehaviourA.updateZombieDolphinRider(this);
    }

    public void updateZombieSnorkel() {
        ZombieBehaviourA.updateZombieSnorkel(this);
    }

    public void updateZombieJackInTheBox() {
        ZombieBehaviourA.updateZombieJackInTheBox(this);
    }

    public void updateZombieGargantuar() {
        ZombieBehaviourA.updateZombieGargantuar(this);
    }

    public void updateZombieImp() {
        ZombieBehaviourA.updateZombieImp(this);
    }

    public void updateZombiePeaHead() {
        ZombieBehaviourA.updateZombiePeaHead(this);
    }

    public void burnRow(int theRow) {
        ZombieBehaviourA.burnRow(this, theRow);
    }

    public void updateZombieJalapenoHead() {
        ZombieBehaviourA.updateZombieJalapenoHead(this);
    }

    public void updateZombieGatlingHead() {
        ZombieBehaviourA.updateZombieGatlingHead(this);
    }

    public void updateZombieSquashHead() {
        ZombieBehaviourA.updateZombieSquashHead(this);
    }

    public void bobsledCrash() {
        ZombieBehaviourA.bobsledCrash(this);
    }

    public void updateZombieBobsled() {
        ZombieBehaviourA.updateZombieBobsled(this);
    }

    public void diggerLoseAxe() {
        ZombieBehaviourA.diggerLoseAxe(this);
    }

    public void updateZombieDigger() {
        ZombieBehaviourA.updateZombieDigger(this);
    }

    // ------------------------------------------------------------------
    // ZombieBehaviourB.java delegators (Zombie.cpp:2810-3500, 3954-4110, 6897-6983,
    // 8184-8265, 9273-9300, 9626-9704)
    // ------------------------------------------------------------------
    public int summonBackupDancer(int theRow, int thePosX) {
        return ZombieBehaviourB.summonBackupDancer(this, theRow, thePosX);
    }

    public void summonBackupDancers() {
        ZombieBehaviourB.summonBackupDancers(this);
    }

    public boolean needsMoreBackupDancers() {
        return ZombieBehaviourB.needsMoreBackupDancers(this);
    }

    public void updateZombieBackupDancer() {
        ZombieBehaviourB.updateZombieBackupDancer(this);
    }

    public void updateZombieDancer() {
        ZombieBehaviourB.updateZombieDancer(this);
    }

    public int getDancerFrame() {
        return ZombieBehaviourB.getDancerFrame(this);
    }

    public int getDancerPhase() {
        return ZombieBehaviourB.getDancerPhase(this);
    }

    public void updateZombieRiseFromGrave() {
        ZombieBehaviourB.updateZombieRiseFromGrave(this);
    }

    public void riseFromGrave(int theCol, int theRow) {
        ZombieBehaviourB.riseFromGrave(this, theCol, theRow);
    }

    public boolean zombiquariumFindClosestBrain() {
        return ZombieBehaviourB.zombiquariumFindClosestBrain(this);
    }

    public void updateZombiquarium() {
        ZombieBehaviourB.updateZombiquarium(this);
    }

    public void updateZombiePool() {
        ZombieBehaviourB.updateZombiePool(this);
    }

    public void poolSplash(boolean theInToPoolSound) {
        ZombieBehaviourB.poolSplash(this, theInToPoolSound);
    }

    public void checkForPool() {
        ZombieBehaviourB.checkForPool(this);
    }

    public void updateZombieHighGround() {
        ZombieBehaviourB.updateZombieHighGround(this);
    }

    public void checkForHighGround() {
        ZombieBehaviourB.checkForHighGround(this);
    }

    public boolean isOnHighGround() {
        return ZombieBehaviourB.isOnHighGround(this);
    }

    public void updateZombieFalling() {
        ZombieBehaviourB.updateZombieFalling(this);
    }

    public void updateZamboni() {
        ZombieBehaviourB.updateZamboni(this);
    }

    public void updateYeti() {
        ZombieBehaviourB.updateYeti(this);
    }

    public void updateLadder() {
        ZombieBehaviourB.updateLadder(this);
    }

    public void updateClimbingLadder() {
        ZombieBehaviourB.updateClimbingLadder(this);
    }

    public void updateZombieChimney() {
        ZombieBehaviourB.updateZombieChimney(this);
    }

    public void walkIntoHouse() {
        ZombieBehaviourB.walkIntoHouse(this);
    }

    public Plant isStandingOnSpikeweed() {
        return ZombieBehaviourB.isStandingOnSpikeweed(this);
    }

    // ------------------------------------------------------------------
    // ZombieCombat.java delegators (Zombie.cpp:3488-3931, 5797-5858, 6343-6583, 7539-8175,
    // 8424-8458)
    // ------------------------------------------------------------------
    public boolean canTargetPlant(Plant thePlant, int theAttackType) {
        return ZombieCombat.canTargetPlant(this, thePlant, theAttackType);
    }

    public Plant findPlantTarget(int theAttackType) {
        return ZombieCombat.findPlantTarget(this, theAttackType);
    }

    public Zombie findZombieTarget() {
        return ZombieCombat.findZombieTarget(this);
    }

    public boolean isTangleKelpTarget() {
        return ZombieCombat.isTangleKelpTarget(this);
    }

    public boolean isSquashTarget(Plant theExcept) {
        return ZombieCombat.isSquashTarget(this, theExcept);
    }

    public void squishAllInSquare(int theX, int theY, int theAttackType) {
        ZombieCombat.squishAllInSquare(this, theX, theY, theAttackType);
    }

    public void checkSquish(int theAttackType) {
        ZombieCombat.checkSquish(this, theAttackType);
    }

    public void takeDamage(int theDamage, int theDamageFlags) {
        ZombieCombat.takeDamage(this, theDamage, theDamageFlags);
    }

    public int takeFlyingDamage(int theDamage, int theDamageFlags) {
        return ZombieCombat.takeFlyingDamage(this, theDamage, theDamageFlags);
    }

    public int takeShieldDamage(int theDamage, int theDamageFlags) {
        return ZombieCombat.takeShieldDamage(this, theDamage, theDamageFlags);
    }

    public int takeHelmDamage(int theDamage, int theDamageFlags) {
        return ZombieCombat.takeHelmDamage(this, theDamage, theDamageFlags);
    }

    public void takeBodyDamage(int theDamage, int theDamageFlags) {
        ZombieCombat.takeBodyDamage(this, theDamage, theDamageFlags);
    }

    public void dropShield(int theDamageFlags) {
        ZombieCombat.dropShield(this, theDamageFlags);
    }

    public void dropHelm(int theDamageFlags) {
        ZombieCombat.dropHelm(this, theDamageFlags);
    }

    public void dropHead(int theDamageFlags) {
        ZombieCombat.dropHead(this, theDamageFlags);
    }

    public void dropArm(int theDamageFlags) {
        ZombieCombat.dropArm(this, theDamageFlags);
    }

    public void setupReanimForLostHead() {
        ZombieCombat.setupReanimForLostHead(this);
    }

    public void setupReanimForLostArm(int theDamageFlags) {
        ZombieCombat.setupReanimForLostArm(this, theDamageFlags);
    }

    public void updateDamageStates(int theDamageFlags) {
        ZombieCombat.updateDamageStates(this, theDamageFlags);
    }

    public int getHelmDamageIndex() {
        return ZombieCombat.getHelmDamageIndex(this);
    }

    public int getShieldDamageIndex() {
        return ZombieCombat.getShieldDamageIndex(this);
    }

    public int getBodyDamageIndex() {
        return ZombieCombat.getBodyDamageIndex(this);
    }

    public boolean effectedByDamage(int theDamageRangeFlags) {
        return ZombieCombat.effectedByDamage(this, theDamageRangeFlags);
    }

    // ===ZOMBIE_END===
}
