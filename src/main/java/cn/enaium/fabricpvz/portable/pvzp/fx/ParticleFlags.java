package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:42 (enum ParticleFlags).
 *
 * <p>Each enumerator is a bit index into {@code PvzpEmitterDefinition.mParticleFlags}
 * ({@code 1 &lt;&lt; value}). The names are verbatim upstream.
 */
public final class ParticleFlags {
    private ParticleFlags() {
    }

    public static final int PARTICLE_RANDOM_LAUNCH_SPIN = 0;
    public static final int PARTICLE_ALIGN_LAUNCH_SPIN = 1;
    public static final int PARTICLE_ALIGN_TO_PIXELS = 2;
    public static final int PARTICLE_SYSTEM_LOOPS = 3;
    public static final int PARTICLE_PARTICLE_LOOPS = 4;
    public static final int PARTICLE_PARTICLES_DONT_FOLLOW = 5;
    public static final int PARTICLE_RANDOM_START_TIME = 6;
    public static final int PARTICLE_DIE_IF_OVERLOADED = 7;
    public static final int PARTICLE_ADDITIVE = 8;
    public static final int PARTICLE_FULLSCREEN = 9;
    public static final int PARTICLE_SOFTWARE_ONLY = 10;
    public static final int PARTICLE_HARDWARE_ONLY = 11;
}
