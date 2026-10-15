package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:193 (class ParticleParams).
 *
 * <p>{@code mParticleEffect} is the upstream {@code ParticleEffect} enum value (Java {@code int}).
 */
public class ParticleParams {

    public int mParticleEffect;
    public String mParticleFileName;

    public ParticleParams() {
    }

    public ParticleParams(int theParticleEffect, String theParticleFileName) {
        mParticleEffect = theParticleEffect;
        mParticleFileName = theParticleFileName;
    }
}
