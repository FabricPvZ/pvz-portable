package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.sexy.SexyVector2;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:279 (class PvzpParticle).
 *
 * <p>One live particle. Public field names/types are upstream; {@code mCrossFadeParticleID} is the
 * upstream {@code ParticleID} (a bucket id from the particle {@code DataArray}, {@code 0} =
 * {@code PARTICLEID_NULL}).
 */
public class PvzpParticle {

    public static final int MAX_PARTICLE_FIELDS = 4;

    public PvzpParticleEmitter mParticleEmitter;
    public int mParticleDuration;
    public int mParticleAge;
    public float mParticleTimeValue;
    public float mParticleLastTimeValue;
    public float mAnimationTimeValue;
    public SexyVector2 mVelocity = new SexyVector2();
    public SexyVector2 mPosition = new SexyVector2();
    public int mImageFrame;
    public float mSpinPosition;
    public float mSpinVelocity;
    public int mCrossFadeParticleID = 0;
    public int mCrossFadeDuration;
    public float[] mParticleInterp = new float[ParticleTracks.NUM_PARTICLE_TRACKS];
    public float[][] mParticleFieldInterp = new float[MAX_PARTICLE_FIELDS][2];
}
