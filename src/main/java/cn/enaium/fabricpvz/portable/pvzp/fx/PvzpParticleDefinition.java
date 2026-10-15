package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:184 (class PvzpParticleDefinition).
 *
 * <p>{@code mEmitterDefs} is the Java {@link DefinitionArrayDef} replacement for upstream's
 * {@code PvzpEmitterDefinition* + mEmitterDefCount} pair; the typed array is
 * {@code PvzpEmitterDefinition[]}.
 */
public class PvzpParticleDefinition {

    public DefinitionArrayDef mEmitterDefs = new DefinitionArrayDef(new PvzpEmitterDefinition[0]);

    /** Ported from src/PvzpLib/Definition.cpp:205 ({@code PvzpParticleDefinitionConstructor}). */
    public static PvzpParticleDefinition pvzpParticleDefinitionConstructor() {
        return new PvzpParticleDefinition();
    }
}
