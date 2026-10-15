package cn.enaium.fabricpvz.portable.pvzp.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.EmitterType;
import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrackNode;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;

/**
 * Deterministic emission/lifetime/recycling tests for the particle runtime, using a hand-built
 * definition and the injected {@link Common} MTRand (never {@code java.util.Random}).
 *
 * <p>The scenario: a Box emitter at (100,200) that launches along +X at 1 unit/frame, one particle
 * in total ({@code SpawnMaxLaunched = 1}), 5-frame lifetime. Every position is hand-computed from
 * the upstream formulas (PvzpParticle.cpp:325-467, :625-663): the particle advances one frame at
 * birth and once per emitter update, so after {@code k} updates it sits at {@code center + (k+1)*v}.
 */
class ParticleTest {

    private static final float CX = 100.0f;
    private static final float CY = 200.0f;

    private static FloatParameterTrack constant(float v) {
        FloatParameterTrack t = new FloatParameterTrack();
        Definition.floatTrackSetDefault(t, v);
        return t;
    }

    /** A single LINEAR node (so {@code FloatTrackIsSet} is true) holding a constant value. */
    private static FloatParameterTrack linearConstant(float v) {
        return new FloatParameterTrack(new FloatParameterTrackNode[] {
                new FloatParameterTrackNode(0.0f, v, v, PvzpCurves.CURVE_LINEAR, PvzpCurves.CURVE_LINEAR) });
    }

    private static PvzpParticleDefinition buildDefinition() {
        PvzpEmitterDefinition e = new PvzpEmitterDefinition();
        e.mEmitterType = EmitterType.EMITTER_BOX;
        e.mName = "TestEmitter";
        e.mImageFrames = 4;
        e.mParticleDuration = constant(5.0f);
        e.mSpawnRate = constant(100.0f);       // 1 particle per frame
        e.mSpawnMinActive = constant(-1.0f);   // post-load default: no lower bound
        e.mSpawnMaxActive = constant(-1.0f);   // post-load default: no upper bound
        e.mSpawnMaxLaunched = linearConstant(1.0f); // exactly one particle for the whole run
        e.mLaunchSpeed = constant(100.0f);     // * 0.01 => 1 pixel/frame
        e.mLaunchAngle = linearConstant(90.0f); // not constant-zero, so a fixed 90 deg (=> +X)
        e.mEmitterBoxX = constant(0.0f);
        e.mEmitterBoxY = constant(0.0f);
        e.mEmitterOffsetX = constant(0.0f);
        e.mEmitterOffsetY = constant(0.0f);
        e.mEmitterSkewX = constant(0.0f);
        e.mEmitterSkewY = constant(0.0f);
        e.mSystemDuration = constant(5.0f);

        PvzpParticleDefinition d = new PvzpParticleDefinition();
        d.mEmitterDefs = new DefinitionArrayDef(new PvzpEmitterDefinition[] { e });
        return d;
    }

    private static PvzpParticle firstParticle(PvzpParticleHolder holder) {
        for (PvzpParticle p : holder.mParticles) {
            return p;
        }
        return null;
    }

    @Test
    void emitAndTickProduceHandComputedPositionsAndLifetimes() {
        Common.sRand(1234L);
        PvzpParticleHolder holder = new PvzpParticleHolder();
        holder.initializeHolder();

        PvzpParticleSystem system = holder.allocParticleSystemFromDef(CX, CY, 0, buildDefinition(), ParticleEffect.PARTICLE_POW);
        assertNotNull(system);
        assertEquals(1, holder.mParticles.mSize, "one particle spawns on the emitter's initial update");

        PvzpParticle p = firstParticle(holder);
        assertNotNull(p);
        assertEquals(5, p.mParticleDuration);
        assertEquals(1, p.mParticleAge, "the particle advances one frame at birth (SpawnParticle calls UpdateParticle)");
        assertEquals(CX + 1.0f, p.mPosition.x, 0.001f, "birth position = center + 1*velocity");
        assertEquals(CY, p.mPosition.y, 0.01f);
        assertTrue(p.mImageFrame >= 0 && p.mImageFrame < 4, "fixed-frame particle picks a random frame in [0,ImageFrames)");

        // Four more updates: position = center + (k+1)*v, age = k+1.
        for (int k = 1; k <= 4; k++) {
            system.update();
            p = firstParticle(holder);
            assertNotNull(p, "particle must still be alive after " + k + " update(s)");
            assertEquals(CX + (k + 1), p.mPosition.x, 0.001f);
            assertEquals(k + 1, p.mParticleAge);
        }

        // Fifth update: age reaches the duration with no loops/cross-fade => the particle is deleted.
        system.update();
        assertEquals(0, holder.mParticles.mSize, "dead particle is recycled back into the DataArray pool");
    }

    @Test
    void sameSeedReproducesPositionsAndFrameIndices() {
        float[] first = runScenario();
        float[] second = runScenario();
        assertEquals(first[0], second[0], 0.0f, "frame index must be identical for a fixed seed");
        assertEquals(first[1], second[1], 0.0f, "x position must be identical for a fixed seed");
        assertEquals(first[2], second[2], 0.0f, "y position must be identical for a fixed seed");
    }

    private static float[] runScenario() {
        Common.sRand(99L);
        PvzpParticleHolder holder = new PvzpParticleHolder();
        holder.initializeHolder();
        PvzpParticleSystem system = holder.allocParticleSystemFromDef(CX, CY, 0, buildDefinition(), ParticleEffect.PARTICLE_POW);
        PvzpParticle p = firstParticle(holder);
        float frame = p.mImageFrame;
        system.update();
        system.update();
        p = firstParticle(holder);
        SexyVector2 pos = p.mPosition;
        return new float[] { frame, pos.x, pos.y };
    }

    @Test
    void deadEmitterIsFreedByTheHolderPool() {
        Common.sRand(7L);
        PvzpParticleHolder holder = new PvzpParticleHolder();
        holder.initializeHolder();
        PvzpParticleSystem system = holder.allocParticleSystemFromDef(CX, CY, 0, buildDefinition(), ParticleEffect.PARTICLE_POW);

        for (int i = 0; i < 6; i++) {
            system.update();
        }
        assertTrue(system.mDead, "the emitter dies when its system duration elapses");

        // A second system can be allocated from the recycled pools.
        PvzpParticleSystem second = holder.allocParticleSystemFromDef(0.0f, 0.0f, 0, buildDefinition(), ParticleEffect.PARTICLE_POW);
        assertNotNull(second);
        assertEquals(1, holder.mParticles.mSize);
    }
}
