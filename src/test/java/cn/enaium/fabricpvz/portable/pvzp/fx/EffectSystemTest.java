package cn.enaium.fabricpvz.portable.pvzp.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.EmitterType;
import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrackNode;

/**
 * EffectSystem holder wiring / delete-queue state transitions, ported from EffectSystem.cpp:23-115.
 */
class EffectSystemTest {

    private static FloatParameterTrack constant(float v) {
        FloatParameterTrack t = new FloatParameterTrack();
        Definition.floatTrackSetDefault(t, v);
        return t;
    }

    private static FloatParameterTrack linearConstant(float v) {
        return new FloatParameterTrack(new FloatParameterTrackNode[] {
                new FloatParameterTrackNode(0.0f, v, v, PvzpCurves.CURVE_LINEAR, PvzpCurves.CURVE_LINEAR) });
    }

    private static PvzpParticleDefinition emptyEmitterDefinition() {
        PvzpEmitterDefinition e = new PvzpEmitterDefinition();
        e.mEmitterType = EmitterType.EMITTER_BOX;
        e.mName = "Empty";
        e.mSpawnRate = constant(0.0f);
        e.mSpawnMinActive = constant(-1.0f);
        e.mSpawnMaxActive = constant(-1.0f);
        e.mSystemDuration = linearConstant(2.0f);
        PvzpParticleDefinition d = new PvzpParticleDefinition();
        d.mEmitterDefs = new DefinitionArrayDef(new PvzpEmitterDefinition[] { e });
        return d;
    }

    @Test
    void initializeUpdateDeleteQueueAndDispose() {
        EffectSystem saved = EffectSystem.gEffectSystem;
        EffectSystem effects = new EffectSystem();
        try {
            effects.effectSystemInitialize();
            assertSame(effects, EffectSystem.gEffectSystem);
            assertNotNull(effects.mParticleHolder);
            assertNotNull(effects.mTrailHolder);
            assertNotNull(effects.mReanimationHolder);
            assertNotNull(effects.mAttachmentHolder);
            assertEquals(1024, effects.mParticleHolder.mParticles.mMaxSize);
            assertEquals(1024, effects.mTrailHolder.mTrails.mMaxSize);
            assertEquals(1024, effects.mReanimationHolder.mReanimations.mMaxSize);
            assertEquals(1024, effects.mAttachmentHolder.mAttachments.mMaxSize);

            PvzpParticleSystem system = effects.mParticleHolder.allocParticleSystemFromDef(0.0f, 0.0f, 0, emptyEmitterDefinition(), ParticleEffect.PARTICLE_POW);
            assertNotNull(system);
            assertEquals(1, effects.mParticleHolder.mParticleSystems.mSize);

            for (int i = 0; i < 3; i++) {
                effects.update();
            }
            assertTrue(system.mDead, "the emitter's 2-frame duration elapses");

            effects.processDeleteQueue();
            assertEquals(0, effects.mParticleHolder.mParticleSystems.mSize, "dead systems are freed by the delete queue");
        } finally {
            effects.effectSystemDispose();
            assertNull(EffectSystem.gEffectSystem);
            EffectSystem.gEffectSystem = saved;
        }
    }

    /**
     * Freeing a dead system must return its emitters to the pool. Upstream recycles a slot through
     * {@code DataArray::Free -> DataArrayResetItemAt -> std::destroy_at} (DataArray.h:86-94,
     * :197-202), which runs {@code ~PvzpParticleSystem()} ({@code ParticleSystemDie()},
     * PvzpParticle.cpp:245-249), and that destructor is the only path that ever hands an emitter slot
     * back (PvzpParticle.cpp:313-322). Without it a level retains one emitter per dead effect: the
     * 1024-entry {@code mEmitters} pool fills, {@code AllocParticleSystemFromDef} starts returning
     * null (PvzpParticle.cpp:1253-1262) and every later effect silently stops appearing — the spore
     * cloud that renders at the start of a level and not after a while.
     */
    @Test
    void freeingDeadSystemsReturnsTheirEmitters() {
        EffectSystem saved = EffectSystem.gEffectSystem;
        EffectSystem effects = new EffectSystem();
        try {
            effects.effectSystemInitialize();

            for (int i = 0; i < 1200; i++) {
                PvzpParticleSystem system = effects.mParticleHolder.allocParticleSystemFromDef(
                        0.0f, 0.0f, 0, emptyEmitterDefinition(), ParticleEffect.PARTICLE_POW);
                assertNotNull(system, "effect " + i + " still spawns: the emitter pool must be recycled");
                for (int aFrame = 0; aFrame < 3; aFrame++) {
                    effects.update();
                }
                assertTrue(system.mDead, "the emitter's 2-frame duration elapses");
                effects.processDeleteQueue();
            }

            assertEquals(0, effects.mParticleHolder.mParticleSystems.mSize, "every dead system is freed");
            assertEquals(0, effects.mParticleHolder.mEmitters.mSize,
                    "and the emitters they owned come back: otherwise the pool leaks one per effect");
        } finally {
            effects.effectSystemDispose();
            EffectSystem.gEffectSystem = saved;
        }
    }

    @Test
    void attachmentFlagSkipsIndependentUpdate() {
        EffectSystem effects = new EffectSystem();
        try {
            effects.effectSystemInitialize();
            PvzpParticleSystem system = effects.mParticleHolder.allocParticleSystemFromDef(0.0f, 0.0f, 0, emptyEmitterDefinition(), ParticleEffect.PARTICLE_POW);
            system.mIsAttachment = true;

            PvzpParticleEmitter emitter = null;
            for (PvzpParticleEmitter e : effects.mParticleHolder.mEmitters) {
                emitter = e;
                break;
            }
            assertNotNull(emitter);
            int ageAtBirth = emitter.mSystemAge;

            effects.update();
            effects.update();
            assertEquals(ageAtBirth, emitter.mSystemAge, "an attachment-owned system is not updated by EffectSystem.update");
        } finally {
            effects.effectSystemDispose();
        }
    }
}
