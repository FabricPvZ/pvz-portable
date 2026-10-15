package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:115 (class ParticleField).
 *
 * <p>One physical field of a particle (or of the emitter); public field names are upstream.
 */
public class ParticleField {

    public int mFieldType = ParticleFieldType.FIELD_INVALID;

    public FloatParameterTrack mX = new FloatParameterTrack();

    public FloatParameterTrack mY = new FloatParameterTrack();
}
