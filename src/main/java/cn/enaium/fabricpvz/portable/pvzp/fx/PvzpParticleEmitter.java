package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpList;
import cn.enaium.fabricpvz.portable.pvzp.PvzpListNode;
import cn.enaium.fabricpvz.portable.lawn.EmitterType;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:307 (class PvzpParticleEmitter) and
 * src/PvzpLib/PvzpParticle.cpp.
 *
 * <p>The emitter is a particle system's working state for one {@link PvzpEmitterDefinition}: spawn
 * accumulation, lifetime state machine, per-emitter track interpolation and the draw pass. Every
 * tick/emission formula and the RNG draw order are preserved so a run from a fixed seed is
 * bit-identical to upstream (the emitter draws {@code Rand(1.0f)} for each system field and for all
 * ten system tracks on init, and each particle draws one interp per field plus all sixteen particle
 * tracks on birth — required for MT-stream parity).
 *
 * <p>PORT-NOTE: {@code FIELD_SHAKE} upstream seeds libc {@code rand()} with
 * {@code age * (uintptr_t)particle}, which is not reproducible. This port substitutes a documented
 * deterministic seed: {@code age * particleBucketId}, evaluated with a fresh {@link MTRand}
 * (e.g. {@link #shakeRandom}). All other RNG goes through the shared {@link MTRand} via
 * {@link Common#rand}.
 */
public class PvzpParticleEmitter {

    /** Ported from src/PvzpLib/PvzpParticle.h:36. */
    public static final int MAX_PARTICLES_SIZE = 900;
    public static final int MAX_PARTICLE_FIELDS = PvzpParticle.MAX_PARTICLE_FIELDS;

    public PvzpEmitterDefinition mEmitterDef;
    public PvzpParticleSystem mParticleSystem;
    public PvzpList<Integer> mParticleList = new PvzpList<>();
    public float mSpawnAccum;
    public SexyVector2 mSystemCenter = new SexyVector2();
    public int mParticlesSpawned;
    public int mSystemAge;
    public int mSystemDuration;
    public float mSystemTimeValue;
    public float mSystemLastTimeValue;
    public boolean mDead;
    public Color mColorOverride = new Color(Color.White.mRed, Color.White.mGreen, Color.White.mBlue, Color.White.mAlpha);
    public boolean mExtraAdditiveDrawOverride;
    public float mScaleOverride;
    public Image mImageOverride;
    public int mCrossFadeEmitterID;
    public int mEmitterCrossFadeCountDown;
    public int mFrameOverride;
    public float[] mTrackInterp = new float[ParticleSystemTracks.NUM_SYSTEM_TRACKS];
    public float[][] mSystemFieldInterp = new float[MAX_PARTICLE_FIELDS][2];

    private static final int PARTICLEID_NULL = 0;
    private static final int PARTICLEEMITTERID_NULL = 0;

    /** Ported from src/PvzpLib/PvzpParticle.cpp:276. */
    public void pvzpEmitterInitialize(float theX, float theY, PvzpParticleSystem theSystem, PvzpEmitterDefinition theEmitterDef) {
        mSpawnAccum = 0.0f;
        mParticlesSpawned = 0;
        mSystemTimeValue = -1.0f;
        mSystemLastTimeValue = -1.0f;
        mSystemAge = -1;
        mDead = false;
        mColorOverride = new Color(Color.White.mRed, Color.White.mGreen, Color.White.mBlue, Color.White.mAlpha);
        mSystemCenter.x = theX;
        mSystemCenter.y = theY;
        mFrameOverride = -1;
        mParticleSystem = theSystem;
        mScaleOverride = 1.0f;
        mExtraAdditiveDrawOverride = false;
        mImageOverride = null;
        mSystemDuration = 0;
        mEmitterDef = theEmitterDef;
        mParticleList.setAllocator(theSystem.mParticleHolder.mParticleListNodeAllocator);

        if (Definition.floatTrackIsSet(mEmitterDef.mSystemDuration)) {
            mSystemDuration = (int) Definition.floatTrackEvaluate(mEmitterDef.mSystemDuration, 0.0f, Common.rand(1.0f));
        } else {
            mSystemDuration = (int) Definition.floatTrackEvaluate(mEmitterDef.mParticleDuration, 0.0f, 1.0f);
        }
        mSystemDuration = Math.max(1, mSystemDuration);

        for (int i = 0; i < mEmitterDef.mSystemFields.mArrayCount; i++) {
            mSystemFieldInterp[i][0] = Common.rand(1.0f);
            mSystemFieldInterp[i][1] = Common.rand(1.0f);
        }
        for (int j = 0; j < 10; j++) {
            mTrackInterp[j] = Common.rand(1.0f);
        }

        update();
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:474. */
    public void updateParticleField(PvzpParticle theParticle, ParticleField theParticleField, float theParticleTimeValue, int theFieldIndex) {
        float aInterpX = theParticle.mParticleFieldInterp[theFieldIndex][0];
        float aInterpY = theParticle.mParticleFieldInterp[theFieldIndex][1];
        float x = Definition.floatTrackEvaluate(theParticleField.mX, theParticleTimeValue, aInterpX);
        float y = Definition.floatTrackEvaluate(theParticleField.mY, theParticleTimeValue, aInterpY);

        switch (theParticleField.mFieldType) {
            case ParticleFieldType.FIELD_INVALID:
                break;
            case ParticleFieldType.FIELD_FRICTION:
                theParticle.mVelocity.x *= 1 - x;
                theParticle.mVelocity.y *= 1 - y;
                break;
            case ParticleFieldType.FIELD_ACCELERATION:
                theParticle.mVelocity.x += 0.01f * x;
                theParticle.mVelocity.y += 0.01f * y;
                break;
            case ParticleFieldType.FIELD_ATTRACTOR: {
                float aDiffX = x - (theParticle.mPosition.x - mSystemCenter.x);
                float aDiffY = y - (theParticle.mPosition.y - mSystemCenter.y);
                theParticle.mVelocity.x += 0.01f * aDiffX;
                theParticle.mVelocity.y += 0.01f * aDiffY;
                break;
            }
            case ParticleFieldType.FIELD_MAX_VELOCITY:
                theParticle.mVelocity.x = Math.min(Math.max(theParticle.mVelocity.x, -x), x);
                theParticle.mVelocity.y = Math.min(Math.max(theParticle.mVelocity.y, -y), y);
                break;
            case ParticleFieldType.FIELD_VELOCITY:
                theParticle.mPosition.x += 0.01 * x;
                theParticle.mPosition.y += 0.01 * y;
                break;
            case ParticleFieldType.FIELD_POSITION: {
                float aLastX = Definition.floatTrackEvaluateFromLastTime(theParticleField.mX, theParticle.mParticleLastTimeValue, aInterpX);
                float aLastY = Definition.floatTrackEvaluateFromLastTime(theParticleField.mY, theParticle.mParticleLastTimeValue, aInterpY);
                theParticle.mPosition.x += x - aLastX;
                theParticle.mPosition.y += y - aLastY;
                break;
            }
            case ParticleFieldType.FIELD_GROUND_CONSTRAINT:
                if (theParticle.mPosition.y > mSystemCenter.y + y) {
                    theParticle.mPosition.y = mSystemCenter.y + y;
                    float aCollisionReflect = Definition.floatTrackEvaluate(
                            mEmitterDef.mCollisionReflect, theParticleTimeValue, theParticle.mParticleInterp[ParticleTracks.TRACK_PARTICLE_COLLISION_REFLECT]);
                    float aCollisionSpin = Definition.floatTrackEvaluate(
                            mEmitterDef.mCollisionSpin, theParticleTimeValue, theParticle.mParticleInterp[ParticleTracks.TRACK_PARTICLE_COLLISION_SPIN]) / 1000.0f;
                    theParticle.mSpinVelocity = theParticle.mVelocity.y * aCollisionSpin;
                    theParticle.mVelocity.x *= aCollisionReflect;
                    theParticle.mVelocity.y *= -aCollisionReflect;
                }
                break;
            case ParticleFieldType.FIELD_SHAKE: {
                float aLastX = Definition.floatTrackEvaluateFromLastTime(theParticleField.mX, theParticle.mParticleLastTimeValue, aInterpX);
                float aLastY = Definition.floatTrackEvaluateFromLastTime(theParticleField.mY, theParticle.mParticleLastTimeValue, aInterpY);
                int aLastRandSeed = theParticle.mParticleAge - 1;
                if (aLastRandSeed == -1) {
                    aLastRandSeed = theParticle.mParticleDuration - 1;
                }
                float aLastShake = shakeRandom(aLastRandSeed, theParticle);
                theParticle.mPosition.x -= aLastX * aLastShake;
                theParticle.mPosition.y -= aLastY * aLastShake;
                float aShake = shakeRandom(theParticle.mParticleAge, theParticle);
                theParticle.mPosition.x += x * aShake;
                theParticle.mPosition.y += y * aShake;
                break;
            }
            case ParticleFieldType.FIELD_CIRCLE: {
                SexyVector2 aToCenter = theParticle.mPosition.minus(mSystemCenter);
                SexyVector2 aMotion = aToCenter.perp().normalize();
                float aRadius = aToCenter.magnitude();
                aMotion.multiplyAssign(0.01f * (x + aRadius * y));
                theParticle.mPosition.addAssign(aMotion);
                break;
            }
            case ParticleFieldType.FIELD_AWAY: {
                SexyVector2 aToCenter = theParticle.mPosition.minus(mSystemCenter);
                SexyVector2 aMotion = aToCenter.normalize();
                float aRadius = aToCenter.magnitude();
                aMotion.multiplyAssign(0.01f * (x + aRadius * y));
                theParticle.mPosition.addAssign(aMotion);
                break;
            }
            default:
                break;
        }
    }

    /**
     * PORT-NOTE substitute for upstream's {@code srand(age * (uintptr_t)particle); rand()} in
     * {@code FIELD_SHAKE} (PvzpParticle.cpp:555-572). Deterministic per (age, particle bucket id).
     */
    private static float shakeRandom(int theAge, PvzpParticle theParticle) {
        int aBucketId = theParticle.mParticleEmitter.mParticleSystem.mParticleHolder.mParticles.dataArrayGetID(theParticle);
        MTRand aRand = new MTRand((long) theAge * (aBucketId & 0xFFFFFFFFL));
        return (float) ((double) aRand.nextNoAssert() / 0x7FFFFFFF * 2.0 - 1.0);
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:580. */
    public void updateSystemField(ParticleField theParticleField, float theParticleTimeValue, int theFieldIndex) {
        float aInterpX = mSystemFieldInterp[theFieldIndex][0];
        float aInterpY = mSystemFieldInterp[theFieldIndex][1];
        float x = Definition.floatTrackEvaluate(theParticleField.mX, theParticleTimeValue, aInterpX);
        float y = Definition.floatTrackEvaluate(theParticleField.mY, theParticleTimeValue, aInterpY);

        if (theParticleField.mFieldType == ParticleFieldType.FIELD_SYSTEM_POSITION) {
            float aLastX = Definition.floatTrackEvaluateFromLastTime(theParticleField.mX, mSystemLastTimeValue, aInterpX);
            float aLastY = Definition.floatTrackEvaluateFromLastTime(theParticleField.mY, mSystemLastTimeValue, aInterpY);
            mSystemCenter.x += x - aLastX;
            mSystemCenter.y += y - aLastY;
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:575. */
    public float systemTrackEvaluate(FloatParameterTrack theTrack, int theSystemTrack) {
        return Definition.floatTrackEvaluate(theTrack, mSystemTimeValue, mTrackInterp[theSystemTrack]);
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:469. */
    public static float particleTrackEvaluate(FloatParameterTrack theTrack, PvzpParticle theParticle, int theParticleTrack) {
        return Definition.floatTrackEvaluate(theTrack, theParticle.mParticleTimeValue, theParticle.mParticleInterp[theParticleTrack]);
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:604. */
    public boolean crossFadeParticleToName(PvzpParticle theParticle, String theEmitterName) {
        PvzpEmitterDefinition aDef = mParticleSystem.findEmitterDefByName(theEmitterName);
        if (aDef == null) {
            return false;
        }
        PvzpParticleHolder aHolder = mParticleSystem.mParticleHolder;
        if (aHolder.mEmitters.mSize == aHolder.mEmitters.mMaxSize) {
            return false;
        }

        PvzpParticleEmitter aEmitter = aHolder.mEmitters.dataArrayAlloc();
        aEmitter.pvzpEmitterInitialize(mSystemCenter.x, mSystemCenter.y, mParticleSystem, aDef);
        int aEmitterID = aHolder.mEmitters.dataArrayGetID(aEmitter);
        mParticleSystem.mEmitterList.addTail(aEmitterID);
        return crossFadeParticle(theParticle, aEmitter);
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:625. */
    public boolean updateParticle(PvzpParticle theParticle) {
        if (theParticle.mParticleAge >= theParticle.mParticleDuration) {
            if (PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_PARTICLE_LOOPS)) {
                theParticle.mParticleAge = 0;
            } else if (theParticle.mCrossFadeDuration > 0) {
                theParticle.mParticleAge = theParticle.mParticleDuration - 1;
            } else if (mEmitterDef.mOnDuration.isEmpty() || !crossFadeParticleToName(theParticle, mEmitterDef.mOnDuration)) {
                return false;
            }
        }
        if (theParticle.mCrossFadeParticleID != PARTICLEID_NULL
                && mParticleSystem.mParticleHolder.mParticles.dataArrayTryToGet(theParticle.mCrossFadeParticleID) == null) {
            return false;
        }

        theParticle.mParticleTimeValue = theParticle.mParticleAge / ((float) theParticle.mParticleDuration - 1);
        ParticleField[] aFields = (ParticleField[]) mEmitterDef.mParticleFields.mArrayData;
        for (int i = 0; i < mEmitterDef.mParticleFields.mArrayCount; i++) {
            updateParticleField(theParticle, aFields[i], theParticle.mParticleTimeValue, i);
        }
        theParticle.mPosition.addAssign(theParticle.mVelocity);
        float aSpinSpeed = particleTrackEvaluate(mEmitterDef.mParticleSpinSpeed, theParticle, ParticleTracks.TRACK_PARTICLE_SPIN_SPEED) * 0.01f;
        float aSpinAngle = particleTrackEvaluate(mEmitterDef.mParticleSpinAngle, theParticle, ParticleTracks.TRACK_PARTICLE_SPIN_ANGLE);
        float aLastSpinAngle = Definition.floatTrackEvaluateFromLastTime(
                mEmitterDef.mParticleSpinAngle, theParticle.mParticleLastTimeValue, theParticle.mParticleInterp[ParticleTracks.TRACK_PARTICLE_SPIN_ANGLE]);
        theParticle.mSpinPosition += degToRad(aSpinSpeed + aSpinAngle - aLastSpinAngle) + theParticle.mSpinVelocity;

        if (Definition.floatTrackIsSet(mEmitterDef.mAnimationRate)) {
            float aAnimTime = particleTrackEvaluate(mEmitterDef.mAnimationRate, theParticle, ParticleTracks.TRACK_PARTICLE_ANIMATION_RATE) * 0.01f;
            theParticle.mAnimationTimeValue += aAnimTime;
            while (theParticle.mAnimationTimeValue >= 1.0f) {
                theParticle.mAnimationTimeValue -= 1.0f;
            }
            while (theParticle.mAnimationTimeValue < 0.0f) {
                theParticle.mAnimationTimeValue += 1.0f;
            }
        }

        theParticle.mParticleAge++;
        theParticle.mParticleLastTimeValue = theParticle.mParticleTimeValue;
        return true;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:665. */
    public void updateSpawning() {
        PvzpParticleHolder aHolder = mParticleSystem.mParticleHolder;
        PvzpParticleEmitter aCrossFadeEmitter = aHolder.mEmitters.dataArrayTryToGet(mCrossFadeEmitterID);
        PvzpParticleEmitter aSpawningEmitter = aCrossFadeEmitter == null ? this : aCrossFadeEmitter;
        mSpawnAccum += aSpawningEmitter.systemTrackEvaluate(aSpawningEmitter.mEmitterDef.mSpawnRate, ParticleSystemTracks.TRACK_SPAWN_RATE) * 0.01f;
        int aSpawnCount = (int) mSpawnAccum;
        mSpawnAccum -= aSpawnCount;

        int aSpawnMinActive = (int) aSpawningEmitter.systemTrackEvaluate(aSpawningEmitter.mEmitterDef.mSpawnMinActive, ParticleSystemTracks.TRACK_SPAWN_MIN_ACTIVE);
        if (aSpawnMinActive >= 0 && aSpawnCount < aSpawnMinActive - mParticleList.mSize) {
            aSpawnCount = aSpawnMinActive - mParticleList.mSize;
        }
        int aSpawnMaxActive = (int) aSpawningEmitter.systemTrackEvaluate(aSpawningEmitter.mEmitterDef.mSpawnMaxActive, ParticleSystemTracks.TRACK_SPAWN_MAX_ACTIVE);
        if (aSpawnMaxActive >= 0 && aSpawnCount > aSpawnMaxActive - mParticleList.mSize) {
            aSpawnCount = aSpawnMaxActive - mParticleList.mSize;
        }
        if (Definition.floatTrackIsSet(aSpawningEmitter.mEmitterDef.mSpawnMaxLaunched)) {
            int aSpawnMaxLaunched = (int) aSpawningEmitter.systemTrackEvaluate(aSpawningEmitter.mEmitterDef.mSpawnMaxLaunched, ParticleSystemTracks.TRACK_SPAWN_MAX_LAUNCHED);
            if (aSpawnCount > aSpawnMaxLaunched - mParticlesSpawned) {
                aSpawnCount = aSpawnMaxLaunched - mParticlesSpawned;
            }
        }

        for (int i = 0; i < aSpawnCount; i++) {
            PvzpParticle aParticle = spawnParticle(i, aSpawnCount);
            if (aParticle != null && aCrossFadeEmitter != null) {
                crossFadeParticle(aParticle, aCrossFadeEmitter);
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:325. */
    public PvzpParticle spawnParticle(int theIndex, int theSpawnCount) {
        PvzpParticleHolder aHolder = mParticleSystem.mParticleHolder;
        if (aHolder.mParticles.mSize == aHolder.mParticles.mMaxSize) {
            return null;
        }

        PvzpParticle aParticle = aHolder.mParticles.dataArrayAlloc();
        for (int i = 0; i < mEmitterDef.mParticleFields.mArrayCount; i++) {
            aParticle.mParticleFieldInterp[i][0] = Common.rand(1.0f);
            aParticle.mParticleFieldInterp[i][1] = Common.rand(1.0f);
        }
        for (int i = 0; i < ParticleTracks.NUM_PARTICLE_TRACKS; i++) {
            aParticle.mParticleInterp[i] = Common.rand(1.0f);
        }

        float aParticleDurationInterp = Common.rand(1.0f);
        float aLaunchSpeedInterp = Common.rand(1.0f);
        float aEmitterOffsetXInterp = Common.rand(1.0f);
        float aEmitterOffsetYInterp = Common.rand(1.0f);
        aParticle.mParticleDuration = (int) Definition.floatTrackEvaluate(mEmitterDef.mParticleDuration, mSystemTimeValue, aParticleDurationInterp);
        aParticle.mParticleDuration = Math.max(1, aParticle.mParticleDuration);
        aParticle.mParticleAge = 0;
        aParticle.mParticleEmitter = this;
        aParticle.mParticleTimeValue = -1.0f;
        aParticle.mParticleLastTimeValue = -1.0f;
        if (PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_RANDOM_START_TIME)) {
            aParticle.mParticleAge = Common.rand(aParticle.mParticleDuration);
        }
        float aLaunchSpeed = Definition.floatTrackEvaluate(mEmitterDef.mLaunchSpeed, mSystemTimeValue, aLaunchSpeedInterp) * 0.01f;
        float aLaunchAngleInterp = Common.rand(1.0f);

        float aLaunchAngle;
        if (mEmitterDef.mEmitterType == EmitterType.EMITTER_CIRCLE_PATH) {
            aLaunchAngle = (float) (Definition.floatTrackEvaluate(mEmitterDef.mEmitterPath, mSystemTimeValue, mTrackInterp[ParticleSystemTracks.TRACK_EMITTER_PATH]) * 2 * GameConstants.PI);
            aLaunchAngle += degToRad(Definition.floatTrackEvaluate(mEmitterDef.mLaunchAngle, mSystemTimeValue, aLaunchAngleInterp));
        } else if (mEmitterDef.mEmitterType == EmitterType.EMITTER_CIRCLE_EVEN_SPACING) {
            aLaunchAngle = (float) (2 * GameConstants.PI * theIndex / theSpawnCount) + degToRad(Definition.floatTrackEvaluate(mEmitterDef.mLaunchAngle, mSystemTimeValue, aLaunchAngleInterp));
        } else if (Definition.floatTrackIsConstantZero(mEmitterDef.mLaunchAngle)) {
            aLaunchAngle = Common.rand((float) (2 * GameConstants.PI));
        } else {
            aLaunchAngle = degToRad(Definition.floatTrackEvaluate(mEmitterDef.mLaunchAngle, mSystemTimeValue, aLaunchAngleInterp));
        }

        float aPosX;
        float aPosY;
        switch (mEmitterDef.mEmitterType) {
            case EmitterType.EMITTER_CIRCLE:
            case EmitterType.EMITTER_CIRCLE_PATH:
            case EmitterType.EMITTER_CIRCLE_EVEN_SPACING: {
                float aEmitterRadiusInterp = Common.rand(1.0f);
                float aRadius = Definition.floatTrackEvaluate(mEmitterDef.mEmitterRadius, mSystemTimeValue, aEmitterRadiusInterp);
                aPosX = (float) Math.sin(aLaunchAngle) * aRadius;
                aPosY = (float) Math.cos(aLaunchAngle) * aRadius;
                break;
            }
            case EmitterType.EMITTER_BOX: {
                float aEmitterBoxXInterp = Common.rand(1.0f);
                float aEmitterBoxYInterp = Common.rand(1.0f);
                aPosX = Definition.floatTrackEvaluate(mEmitterDef.mEmitterBoxX, mSystemTimeValue, aEmitterBoxXInterp);
                aPosY = Definition.floatTrackEvaluate(mEmitterDef.mEmitterBoxY, mSystemTimeValue, aEmitterBoxYInterp);
                break;
            }
            case EmitterType.EMITTER_BOX_PATH: {
                float aEmitterPathPosition = Definition.floatTrackEvaluate(mEmitterDef.mEmitterPath, mSystemTimeValue, mTrackInterp[ParticleSystemTracks.TRACK_EMITTER_PATH]);
                float aMinX = Definition.floatTrackEvaluate(mEmitterDef.mEmitterBoxX, mSystemTimeValue, 0.0f);
                float aMaxX = Definition.floatTrackEvaluate(mEmitterDef.mEmitterBoxX, mSystemTimeValue, 1.0f);
                float aMinY = Definition.floatTrackEvaluate(mEmitterDef.mEmitterBoxY, mSystemTimeValue, 0.0f);
                float aMaxY = Definition.floatTrackEvaluate(mEmitterDef.mEmitterBoxY, mSystemTimeValue, 1.0f);
                float aDistanceX = aMaxX - aMinX;
                float aDistanceY = aMaxY - aMinY;
                float aPathPos = aEmitterPathPosition * (aDistanceY + aDistanceX + aDistanceY + aDistanceX);
                if (aPathPos < aDistanceY) {
                    aPosX = aMinX;
                    aPosY = aMinY + aPathPos;
                } else if (aPathPos < aDistanceY + aDistanceX) {
                    aPosX = aMinX + (aPathPos - aDistanceY);
                    aPosY = aMaxY;
                } else if (aPathPos < aDistanceY + aDistanceX + aDistanceY) {
                    aPosX = aMaxX;
                    aPosY = aMaxY - (aPathPos - aDistanceY - aDistanceX);
                } else {
                    aPosX = aMaxX - (aPathPos - aDistanceY - aDistanceX - aDistanceY);
                    aPosY = aMinY;
                }
                break;
            }
            default:
                aPosX = 0.0f;
                aPosY = 0.0f;
                break;
        }
        float aEmitterSkewXInterp = Common.rand(1.0f);
        float aEmitterSkewYInterp = Common.rand(1.0f);
        float aSkewX = Definition.floatTrackEvaluate(mEmitterDef.mEmitterSkewX, mSystemTimeValue, aEmitterSkewXInterp);
        float aSkewY = Definition.floatTrackEvaluate(mEmitterDef.mEmitterSkewY, mSystemTimeValue, aEmitterSkewYInterp);
        aParticle.mPosition.x = mSystemCenter.x + aPosX + aPosY * aSkewX;
        aParticle.mPosition.y = mSystemCenter.y + aPosY + aPosX * aSkewY;
        aParticle.mVelocity.x = (float) Math.sin(aLaunchAngle) * aLaunchSpeed;
        aParticle.mVelocity.y = (float) Math.cos(aLaunchAngle) * aLaunchSpeed;
        aParticle.mPosition.x += Definition.floatTrackEvaluate(mEmitterDef.mEmitterOffsetX, mSystemTimeValue, aEmitterOffsetXInterp);
        aParticle.mPosition.y += Definition.floatTrackEvaluate(mEmitterDef.mEmitterOffsetY, mSystemTimeValue, aEmitterOffsetYInterp);

        aParticle.mAnimationTimeValue = 0.0f;
        if (mEmitterDef.mAnimated != 0 || Definition.floatTrackIsSet(mEmitterDef.mAnimationRate)) {
            aParticle.mImageFrame = 0;
        } else {
            aParticle.mImageFrame = Common.rand(mEmitterDef.mImageFrames);
        }

        if (PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_RANDOM_LAUNCH_SPIN)) {
            aParticle.mSpinPosition = Common.rand((float) (2 * GameConstants.PI));
        } else if (PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_ALIGN_LAUNCH_SPIN)) {
            aParticle.mSpinPosition = aLaunchAngle;
        } else {
            aParticle.mSpinPosition = 0.0f;
        }
        aParticle.mSpinVelocity = 0.0f;
        aParticle.mCrossFadeDuration = 0;
        aParticle.mCrossFadeParticleID = PARTICLEID_NULL;

        int aParticleID = aHolder.mParticles.dataArrayGetID(aParticle);
        mParticleList.addHead(aParticleID);
        mParticlesSpawned++;
        updateParticle(aParticle);
        return aParticle;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:733. */
    public boolean crossFadeParticle(PvzpParticle theParticle, PvzpParticleEmitter theToEmitter) {
        if (theParticle.mCrossFadeDuration > 0) {
            return false;
        }
        if (!Definition.floatTrackIsSet(theToEmitter.mEmitterDef.mCrossFadeDuration)) {
            return false;
        }

        PvzpParticle aToParticle = theToEmitter.spawnParticle(0, 1);
        if (aToParticle == null) {
            return false;
        }
        if (mEmitterCrossFadeCountDown > 0) {
            theParticle.mCrossFadeDuration = mEmitterCrossFadeCountDown;
        } else {
            float aCrossFadeDurationInterp = Common.rand(1);
            int aCrossFadeDuration = (int) Definition.floatTrackEvaluate(theToEmitter.mEmitterDef.mCrossFadeDuration, mSystemTimeValue, aCrossFadeDurationInterp);
            theParticle.mCrossFadeDuration = Math.max(1, aCrossFadeDuration);
        }
        if (!Definition.floatTrackIsSet(theToEmitter.mEmitterDef.mParticleDuration)) {
            aToParticle.mParticleDuration = theParticle.mCrossFadeDuration;
        }
        aToParticle.mCrossFadeParticleID = mParticleSystem.mParticleHolder.mParticles.dataArrayGetID(theParticle);
        return true;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:764. */
    public void deleteParticle(PvzpParticle theParticle) {
        PvzpParticleHolder aHolder = mParticleSystem.mParticleHolder;
        PvzpParticle aCrossFadeParticle = aHolder.mParticles.dataArrayTryToGet(theParticle.mCrossFadeParticleID);
        if (aCrossFadeParticle != null) {
            aCrossFadeParticle.mParticleEmitter.deleteParticle(aCrossFadeParticle);
            theParticle.mCrossFadeParticleID = PARTICLEID_NULL;
        }

        int aParticleID = aHolder.mParticles.dataArrayGetID(theParticle);
        mParticleList.removeAt(mParticleList.find(aParticleID));
        aHolder.mParticles.dataArrayFree(theParticle);
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:694. */
    public void deleteNonCrossFading() {
        for (PvzpListNode<Integer> aNode = mParticleList.mHead; aNode != null; ) {
            PvzpListNode<Integer> aNext = aNode.mNext;
            PvzpParticle aParticle = mParticleSystem.mParticleHolder.mParticles.dataArrayGet(aNode.mValue);
            if (aParticle.mCrossFadeDuration <= 0) {
                deleteParticle(aParticle);
            }
            aNode = aNext;
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:706. */
    public void deleteAll() {
        while (mParticleList.mSize != 0) {
            int anId = mParticleList.removeHead();
            mParticleSystem.mParticleHolder.mParticles.dataArrayFree(mParticleSystem.mParticleHolder.mParticles.dataArrayGet(anId));
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:778. */
    public void update() {
        if (mDead) {
            return;
        }

        mSystemAge++;
        boolean aDie = false;
        if (mSystemAge >= mSystemDuration) {
            if (PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_SYSTEM_LOOPS)) {
                mSystemAge = 0;
            } else {
                mSystemAge = mSystemDuration - 1;
                aDie = true;
            }
        }

        if (mEmitterCrossFadeCountDown > 0) {
            mEmitterCrossFadeCountDown--;
            if (mEmitterCrossFadeCountDown == 0) {
                aDie = true;
            }
        }
        if (mCrossFadeEmitterID != PARTICLEEMITTERID_NULL) {
            PvzpParticleEmitter aCrossFadeEmitter = mParticleSystem.mParticleHolder.mEmitters.dataArrayTryToGet(mCrossFadeEmitterID);
            if (aCrossFadeEmitter == null || aCrossFadeEmitter.mDead) {
                aDie = true;
            }
        }

        mSystemTimeValue = mSystemAge / ((float) mSystemDuration - 1);
        ParticleField[] aSystemFields = (ParticleField[]) mEmitterDef.mSystemFields.mArrayData;
        for (int i = 0; i < mEmitterDef.mSystemFields.mArrayCount; i++) {
            updateSystemField(aSystemFields[i], mSystemTimeValue, i);
        }
        for (PvzpListNode<Integer> aNode = mParticleList.mHead; aNode != null; ) {
            PvzpListNode<Integer> aNext = aNode.mNext;
            PvzpParticle aParticle = mParticleSystem.mParticleHolder.mParticles.dataArrayGet(aNode.mValue);
            if (!updateParticle(aParticle)) {
                deleteParticle(aParticle);
            }
            aNode = aNext;
        }
        updateSpawning();

        if (aDie) {
            deleteNonCrossFading();
            if (mParticleList.mSize == 0) {
                mDead = true;
                return;
            }
        }
        mSystemLastTimeValue = mSystemTimeValue;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1067. */
    public void systemMove(float theX, float theY) {
        float aDeltaX = theX - mSystemCenter.x;
        float aDeltaY = theY - mSystemCenter.y;
        if (PvzpCommon.floatApproxEqual(aDeltaX, 0.0f) && PvzpCommon.floatApproxEqual(aDeltaY, 0.0f)) {
            return;
        }

        mSystemCenter.x = theX;
        mSystemCenter.y = theY;
        if (!PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_PARTICLES_DONT_FOLLOW)) {
            for (PvzpListNode<Integer> aNode = mParticleList.mHead; aNode != null; aNode = aNode.mNext) {
                PvzpParticle aParticle = mParticleSystem.mParticleHolder.mParticles.dataArrayGet(aNode.mValue);
                aParticle.mPosition.x += aDeltaX;
                aParticle.mPosition.y += aDeltaY;
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:834. */
    public static float crossFadeLerp(float theFrom, float theTo, boolean theFromIsSet, boolean theToIsSet, float theFraction) {
        if (!theFromIsSet) {
            return theTo;
        }
        if (!theToIsSet) {
            return theFrom;
        }
        return theFrom + (theTo - theFrom) * theFraction;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:843. */
    public static boolean getRenderParams(PvzpParticle theParticle, ParticleRenderParams theParams) {
        PvzpParticleEmitter aEmitter = theParticle.mParticleEmitter;
        PvzpEmitterDefinition aDef = aEmitter.mEmitterDef;

        theParams.mRedIsSet = false;
        theParams.mRedIsSet |= Definition.floatTrackIsSet(aDef.mSystemRed);
        theParams.mRedIsSet |= Definition.floatTrackIsSet(aDef.mParticleRed);
        theParams.mRedIsSet |= aEmitter.mColorOverride.mRed != 1.0f;
        theParams.mGreenIsSet = false;
        theParams.mGreenIsSet |= Definition.floatTrackIsSet(aDef.mSystemGreen);
        theParams.mGreenIsSet |= Definition.floatTrackIsSet(aDef.mParticleGreen);
        theParams.mGreenIsSet |= aEmitter.mColorOverride.mGreen != 1.0f;
        theParams.mBlueIsSet = false;
        theParams.mBlueIsSet |= Definition.floatTrackIsSet(aDef.mSystemBlue);
        theParams.mBlueIsSet |= Definition.floatTrackIsSet(aDef.mParticleBlue);
        theParams.mBlueIsSet |= aEmitter.mColorOverride.mBlue != 1.0f;
        theParams.mAlphaIsSet = false;
        theParams.mAlphaIsSet |= Definition.floatTrackIsSet(aDef.mSystemAlpha);
        theParams.mAlphaIsSet |= Definition.floatTrackIsSet(aDef.mParticleAlpha);
        theParams.mAlphaIsSet |= aEmitter.mColorOverride.mAlpha != 1.0f;
        theParams.mParticleScaleIsSet = false;
        theParams.mParticleScaleIsSet |= Definition.floatTrackIsSet(aDef.mParticleScale);
        theParams.mParticleScaleIsSet |= aEmitter.mScaleOverride != 1.0f;
        theParams.mParticleStretchIsSet = Definition.floatTrackIsSet(aDef.mParticleStretch);
        theParams.mSpinPositionIsSet = false;
        theParams.mSpinPositionIsSet |= Definition.floatTrackIsSet(aDef.mParticleSpinSpeed);
        theParams.mSpinPositionIsSet |= Definition.floatTrackIsSet(aDef.mParticleSpinAngle);
        theParams.mSpinPositionIsSet |= PvzpCommon.testBit(aDef.mParticleFlags, ParticleFlags.PARTICLE_RANDOM_LAUNCH_SPIN);
        theParams.mSpinPositionIsSet |= PvzpCommon.testBit(aDef.mParticleFlags, ParticleFlags.PARTICLE_ALIGN_LAUNCH_SPIN);
        theParams.mPositionIsSet = false;
        theParams.mPositionIsSet |= aDef.mParticleFields.mArrayCount > 0;
        theParams.mPositionIsSet |= Definition.floatTrackIsSet(aDef.mEmitterRadius);
        theParams.mPositionIsSet |= Definition.floatTrackIsSet(aDef.mEmitterOffsetX);
        theParams.mPositionIsSet |= Definition.floatTrackIsSet(aDef.mEmitterOffsetY);
        theParams.mPositionIsSet |= Definition.floatTrackIsSet(aDef.mEmitterBoxX);
        theParams.mPositionIsSet |= Definition.floatTrackIsSet(aDef.mEmitterBoxY);

        float aSystemRed = aEmitter.systemTrackEvaluate(aDef.mSystemRed, ParticleSystemTracks.TRACK_SYSTEM_RED);
        float aSystemGreen = aEmitter.systemTrackEvaluate(aDef.mSystemGreen, ParticleSystemTracks.TRACK_SYSTEM_GREEN);
        float aSystemBlue = aEmitter.systemTrackEvaluate(aDef.mSystemBlue, ParticleSystemTracks.TRACK_SYSTEM_BLUE);
        float aSystemAlpha = aEmitter.systemTrackEvaluate(aDef.mSystemAlpha, ParticleSystemTracks.TRACK_SYSTEM_ALPHA);
        float aSystemBrightness = aEmitter.systemTrackEvaluate(aDef.mSystemBrightness, ParticleSystemTracks.TRACK_SYSTEM_BRIGHTNESS);
        float aParticleRed = particleTrackEvaluate(aDef.mParticleRed, theParticle, ParticleTracks.TRACK_PARTICLE_RED);
        float aParticleGreen = particleTrackEvaluate(aDef.mParticleGreen, theParticle, ParticleTracks.TRACK_PARTICLE_GREEN);
        float aParticleBlue = particleTrackEvaluate(aDef.mParticleBlue, theParticle, ParticleTracks.TRACK_PARTICLE_BLUE);
        float aParticleAlpha = particleTrackEvaluate(aDef.mParticleAlpha, theParticle, ParticleTracks.TRACK_PARTICLE_ALPHA);
        float aParticleBrightness = particleTrackEvaluate(aDef.mParticleBrightness, theParticle, ParticleTracks.TRACK_PARTICLE_BRIGHTNESS);
        float aBrightness = aParticleBrightness * aSystemBrightness;
        theParams.mRed = aParticleRed * aSystemRed * aEmitter.mColorOverride.mRed * aBrightness;
        theParams.mGreen = aParticleGreen * aSystemGreen * aEmitter.mColorOverride.mGreen * aBrightness;
        theParams.mBlue = aParticleBlue * aSystemBlue * aEmitter.mColorOverride.mBlue * aBrightness;
        theParams.mAlpha = aParticleAlpha * aSystemAlpha * aEmitter.mColorOverride.mAlpha * aBrightness;
        theParams.mPosX = theParticle.mPosition.x;
        theParams.mPosY = theParticle.mPosition.y;
        float aParticleScale = particleTrackEvaluate(aDef.mParticleScale, theParticle, ParticleTracks.TRACK_PARTICLE_SCALE);
        theParams.mParticleStretch = particleTrackEvaluate(aDef.mParticleStretch, theParticle, ParticleTracks.TRACK_PARTICLE_STRETCH);
        theParams.mParticleScale = aParticleScale * aEmitter.mScaleOverride;
        theParams.mSpinPosition = theParticle.mSpinPosition;

        PvzpParticle aCrossFadeParticle = aEmitter.mParticleSystem.mParticleHolder.mParticles.dataArrayTryToGet(theParticle.mCrossFadeParticleID);
        if (aCrossFadeParticle != null) {
            ParticleRenderParams aCrossFadeParams = new ParticleRenderParams();
            if (getRenderParams(aCrossFadeParticle, aCrossFadeParams)) {
                float aFraction = theParticle.mParticleAge / ((float) aCrossFadeParticle.mCrossFadeDuration - 1);
                theParams.mRed = crossFadeLerp(aCrossFadeParams.mRed, theParams.mRed, aCrossFadeParams.mRedIsSet, theParams.mRedIsSet, aFraction);
                theParams.mGreen = crossFadeLerp(aCrossFadeParams.mGreen, theParams.mGreen, aCrossFadeParams.mGreenIsSet, theParams.mGreenIsSet, aFraction);
                theParams.mBlue = crossFadeLerp(aCrossFadeParams.mBlue, theParams.mBlue, aCrossFadeParams.mBlueIsSet, theParams.mBlueIsSet, aFraction);
                theParams.mAlpha = crossFadeLerp(aCrossFadeParams.mAlpha, theParams.mAlpha, aCrossFadeParams.mAlphaIsSet, theParams.mAlphaIsSet, aFraction);
                theParams.mParticleScale = crossFadeLerp(
                        aCrossFadeParams.mParticleScale, theParams.mParticleScale, aCrossFadeParams.mParticleScaleIsSet, theParams.mParticleScaleIsSet, aFraction);
                theParams.mParticleStretch = crossFadeLerp(
                        aCrossFadeParams.mParticleStretch, theParams.mParticleStretch, aCrossFadeParams.mParticleStretchIsSet, theParams.mParticleStretchIsSet, aFraction);
                theParams.mSpinPosition = crossFadeLerp(
                        aCrossFadeParams.mSpinPosition, theParams.mSpinPosition, aCrossFadeParams.mSpinPositionIsSet, theParams.mSpinPositionIsSet, aFraction);
                theParams.mPosX = crossFadeLerp(aCrossFadeParams.mPosX, theParams.mPosX, aCrossFadeParams.mPositionIsSet, theParams.mPositionIsSet, aFraction);
                theParams.mPosY = crossFadeLerp(aCrossFadeParams.mPosY, theParams.mPosY, aCrossFadeParams.mPositionIsSet, theParams.mPositionIsSet, aFraction);
                theParams.mRedIsSet |= aCrossFadeParams.mRedIsSet;
                theParams.mGreenIsSet |= aCrossFadeParams.mGreenIsSet;
                theParams.mBlueIsSet |= aCrossFadeParams.mBlueIsSet;
                theParams.mAlphaIsSet |= aCrossFadeParams.mAlphaIsSet;
                theParams.mParticleScaleIsSet |= aCrossFadeParams.mParticleScaleIsSet;
                theParams.mParticleStretchIsSet |= aCrossFadeParams.mParticleStretchIsSet;
                theParams.mSpinPositionIsSet |= aCrossFadeParams.mSpinPositionIsSet;
                theParams.mPositionIsSet |= aCrossFadeParams.mPositionIsSet;
            }
        }
        return true;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:939 ({@code RenderParticle}). */
    public static void renderParticle(Graphics g, PvzpParticle theParticle, Color theColor, ParticleRenderParams theParams, PvzpTriangleGroup theTriangleGroup) {
        PvzpParticleEmitter aEmitter = theParticle.mParticleEmitter;
        PvzpEmitterDefinition aEmitterDef = aEmitter.mEmitterDef;
        Image aImage = aEmitter.mImageOverride != null ? aEmitter.mImageOverride : aEmitterDef.mImage;
        if (aImage == null) {
            return;
        }

        int aCelWidth = aImage.getCelWidth();
        int aCelHeight = aImage.getCelHeight();
        int aFrame = aEmitter.mFrameOverride;
        if (aFrame == -1) {
            if (Definition.floatTrackIsSet(aEmitterDef.mAnimationRate)) {
                aFrame = clamp((int) (theParticle.mAnimationTimeValue * aEmitterDef.mImageFrames), 0, aEmitterDef.mImageFrames - 1);
            } else if (aEmitterDef.mAnimated != 0) {
                aFrame = clamp((int) (theParticle.mParticleTimeValue * aEmitterDef.mImageFrames), 0, aEmitterDef.mImageFrames - 1);
            } else {
                aFrame = theParticle.mImageFrame;
            }
        }
        aFrame += aEmitterDef.mImageCol;
        if (aFrame >= aImage.mNumCols) {
            aFrame = aImage.mNumCols - 1;
        }

        Rect aSrcRect = new Rect(aFrame * aCelWidth, Math.min(aEmitterDef.mImageRow, aImage.mNumRows - 1) * aCelHeight, aCelWidth, aCelHeight);
        float aClipTop = particleTrackEvaluate(aEmitterDef.mClipTop, theParticle, ParticleTracks.TRACK_PARTICLE_CLIP_TOP);
        float aClipBottom = particleTrackEvaluate(aEmitterDef.mClipBottom, theParticle, ParticleTracks.TRACK_PARTICLE_CLIP_BOTTOM);
        float aClipLeft = particleTrackEvaluate(aEmitterDef.mClipLeft, theParticle, ParticleTracks.TRACK_PARTICLE_CLIP_LEFT);
        float aClipRight = particleTrackEvaluate(aEmitterDef.mClipRight, theParticle, ParticleTracks.TRACK_PARTICLE_CLIP_RIGHT);
        theParams.mPosX += aClipLeft * aCelWidth;
        theParams.mPosY += aClipTop * aCelHeight;
        aSrcRect.mX += PvzpCommon.floatRoundToInt(aClipLeft * aCelWidth);
        aSrcRect.mY += PvzpCommon.floatRoundToInt(aClipTop * aCelHeight);
        aSrcRect.mWidth -= PvzpCommon.floatRoundToInt(aCelWidth * (aClipLeft + aClipRight));
        aSrcRect.mHeight -= PvzpCommon.floatRoundToInt(aCelHeight * (aClipBottom + aClipTop));

        if (PvzpCommon.testBit(aEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_ALIGN_TO_PIXELS)) {
            theParams.mPosX = PvzpCommon.floatRoundToInt(theParams.mPosX);
            theParams.mPosY = PvzpCommon.floatRoundToInt(theParams.mPosY);
        }
        int aDrawMode = g.mDrawMode;
        if (PvzpCommon.testBit(aEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_ADDITIVE)) {
            aDrawMode = Graphics.DRAWMODE_ADDITIVE;
        }
        if (PvzpCommon.testBit(aEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_FULLSCREEN)) {
            theTriangleGroup.drawGroup(g);
            Color anOldColor = g.getColor();
            int anOldDrawMode = g.getDrawMode();
            g.setColor(theColor);
            g.fillRect((int) -g.mTransX, (int) -g.mTransY, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
            g.setColor(anOldColor);
            g.setDrawMode(anOldDrawMode);
        } else {
            SexyMatrix3 aTransform = new SexyMatrix3();
            PvzpCommon.pvzpScaleRotateTransformMatrix(aTransform, theParams.mPosX, theParams.mPosY, theParams.mSpinPosition,
                    theParams.mParticleScale, theParams.mParticleStretch * theParams.mParticleScale);
            theTriangleGroup.addTriangle(g, aImage, aTransform, g.mClipRect, theColor, aDrawMode, aSrcRect);
            if (aEmitter.mExtraAdditiveDrawOverride) {
                theTriangleGroup.addTriangle(g, aImage, aTransform, g.mClipRect, theColor, Graphics.DRAWMODE_ADDITIVE, aSrcRect);
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1018. */
    public void drawParticle(Graphics g, PvzpParticle theParticle, PvzpTriangleGroup theTriangleGroup) {
        if (theParticle.mCrossFadeDuration > 0) {
            return;
        }

        ParticleRenderParams aParams = new ParticleRenderParams();
        if (getRenderParams(theParticle, aParams)) {
            Color aColor = new Color(
                    clamp(PvzpCommon.floatRoundToInt(aParams.mRed), 0, 255),
                    clamp(PvzpCommon.floatRoundToInt(aParams.mGreen), 0, 255),
                    clamp(PvzpCommon.floatRoundToInt(aParams.mBlue), 0, 255),
                    clamp(PvzpCommon.floatRoundToInt(aParams.mAlpha), 0, 255));
            if (aColor.mAlpha > 0) {
                aParams.mPosX += g.mTransX;
                aParams.mPosY += g.mTransY;

                PvzpParticle aParticle;
                if (mImageOverride != null || mEmitterDef.mImage != null) {
                    aParticle = theParticle;
                } else {
                    aParticle = mParticleSystem.mParticleHolder.mParticles.dataArrayTryToGet(theParticle.mCrossFadeParticleID);
                }
                if (aParticle != null) {
                    renderParticle(g, aParticle, aColor, aParams, theTriangleGroup);
                }
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1054. */
    public void draw(Graphics g) {
        boolean aHardWare = SexyAppBase.current.is3DAccelerated();
        if ((PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_SOFTWARE_ONLY) && aHardWare)
                || (PvzpCommon.testBit(mEmitterDef.mParticleFlags, ParticleFlags.PARTICLE_HARDWARE_ONLY) && !aHardWare)) {
            return;
        }

        PvzpTriangleGroup aTriangleGroup = new PvzpTriangleGroup();
        for (PvzpListNode<Integer> aNode = mParticleList.mHead; aNode != null; aNode = aNode.mNext) {
            drawParticle(g, mParticleSystem.mParticleHolder.mParticles.dataArrayGet(aNode.mValue), aTriangleGroup);
        }
        aTriangleGroup.drawGroup(g);
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1165. */
    public void crossFadeEmitter(PvzpParticleEmitter theToEmitter) {
        if (mEmitterCrossFadeCountDown > 0) {
            return;
        }
        if (!Definition.floatTrackIsSet(theToEmitter.mEmitterDef.mCrossFadeDuration)) {
            return;
        }

        float aCrossFadeDurationInterp = Common.rand(1.0f);
        mEmitterCrossFadeCountDown = (int) Definition.floatTrackEvaluate(theToEmitter.mEmitterDef.mCrossFadeDuration, mSystemTimeValue, aCrossFadeDurationInterp);
        mEmitterCrossFadeCountDown = Math.max(1, mEmitterCrossFadeCountDown);
        mCrossFadeEmitterID = mParticleSystem.mParticleHolder.mEmitters.dataArrayGetID(theToEmitter);
        if (!Definition.floatTrackIsSet(theToEmitter.mEmitterDef.mSystemDuration)) {
            theToEmitter.mSystemDuration = mEmitterCrossFadeCountDown;
        }

        for (PvzpListNode<Integer> aNode = mParticleList.mHead; aNode != null; aNode = aNode.mNext) {
            crossFadeParticle(mParticleSystem.mParticleHolder.mParticles.dataArrayGet(aNode.mValue), theToEmitter);
        }
    }

    private static float degToRad(float deg) {
        return deg * PvzpCommon.DEG_TO_RAD;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
