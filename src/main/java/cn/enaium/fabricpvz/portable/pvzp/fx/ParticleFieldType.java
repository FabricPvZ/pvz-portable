package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:56 (enum ParticleFieldType).
 *
 * <p>Values are verbatim upstream (the values checked in {@code UpdateParticleField}).
 */
public final class ParticleFieldType {
    private ParticleFieldType() {
    }

    public static final int FIELD_INVALID = 0;
    public static final int FIELD_FRICTION = 1;
    public static final int FIELD_ACCELERATION = 2;
    public static final int FIELD_ATTRACTOR = 3;
    public static final int FIELD_MAX_VELOCITY = 4;
    public static final int FIELD_VELOCITY = 5;
    public static final int FIELD_POSITION = 6;
    public static final int FIELD_SYSTEM_POSITION = 7;
    public static final int FIELD_GROUND_CONSTRAINT = 8;
    public static final int FIELD_SHAKE = 9;
    public static final int FIELD_CIRCLE = 10;
    public static final int FIELD_AWAY = 11;
    public static final int PARTICLE_FIELD_COUNT = 12;
}
