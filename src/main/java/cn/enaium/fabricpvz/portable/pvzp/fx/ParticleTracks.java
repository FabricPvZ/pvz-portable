package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:219 (enum ParticleTracks).
 *
 * <p>Indices into {@code PvzpParticle.mParticleInterp}.
 */
public final class ParticleTracks {
    private ParticleTracks() {
    }

    public static final int TRACK_PARTICLE_RED = 0;
    public static final int TRACK_PARTICLE_GREEN = 1;
    public static final int TRACK_PARTICLE_BLUE = 2;
    public static final int TRACK_PARTICLE_ALPHA = 3;
    public static final int TRACK_PARTICLE_BRIGHTNESS = 4;
    public static final int TRACK_PARTICLE_SPIN_SPEED = 5;
    public static final int TRACK_PARTICLE_SPIN_ANGLE = 6;
    public static final int TRACK_PARTICLE_SCALE = 7;
    public static final int TRACK_PARTICLE_STRETCH = 8;
    public static final int TRACK_PARTICLE_COLLISION_REFLECT = 9;
    public static final int TRACK_PARTICLE_COLLISION_SPIN = 10;
    public static final int TRACK_PARTICLE_CLIP_TOP = 11;
    public static final int TRACK_PARTICLE_CLIP_BOTTOM = 12;
    public static final int TRACK_PARTICLE_CLIP_LEFT = 13;
    public static final int TRACK_PARTICLE_CLIP_RIGHT = 14;
    public static final int TRACK_PARTICLE_ANIMATION_RATE = 15;
    public static final int NUM_PARTICLE_TRACKS = 16;
}
