package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachmentHolder;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimationHolder;

/**
 * Ported from src/PvzpLib/EffectSystem.h:172 (class EffectSystem) and
 * src/PvzpLib/EffectSystem.cpp:23-115.
 *
 * <p>Owns the four effect holders (particles, trails, reanimations, attachments) and drives the
 * per-frame update and delete queue. The global {@code gEffectSystem} keeps its upstream name.
 *
 * <p>PORT-NOTE: the software triangle rasterisers ({@code PvzpDrawTriangleInc.inc},
 * {@code PvzpDrawTriangle.inc}, {@code Pvzp_SWTri_AddAllDrawTriFuncs}) and the
 * {@code PvzpTriangleGroup} clip reservoir live only to feed them; this port draws through the
 * framework triangle blit, so they are omitted (see {@link PvzpTriangleGroup}).
 */
public class EffectSystem {

    public static EffectSystem gEffectSystem;

    public PvzpParticleHolder mParticleHolder;
    public TrailHolder mTrailHolder;
    public ReanimationHolder mReanimationHolder;
    public AttachmentHolder mAttachmentHolder;

    /** Ported from src/PvzpLib/EffectSystem.cpp:39. */
    public void effectSystemInitialize() {
        gEffectSystem = this;
        mParticleHolder = new PvzpParticleHolder();
        mTrailHolder = new TrailHolder();
        mReanimationHolder = new ReanimationHolder();
        mAttachmentHolder = new AttachmentHolder();

        mParticleHolder.initializeHolder();
        mTrailHolder.initializeHolder();
        mReanimationHolder.initializeHolder();
        mAttachmentHolder.initializeHolder();
    }

    /** Ported from src/PvzpLib/EffectSystem.cpp:58. */
    public void effectSystemDispose() {
        mParticleHolder = null;
        mTrailHolder = null;
        mReanimationHolder = null;
        mAttachmentHolder = null;

        gEffectSystem = null;
    }

    /** Ported from src/PvzpLib/EffectSystem.cpp:67. */
    public void effectSystemFreeAll() {
        mParticleHolder.mParticleSystems.dataArrayFreeAll();
        mParticleHolder.mEmitters.dataArrayFreeAll();
        mParticleHolder.mParticles.dataArrayFreeAll();
        mParticleHolder.mEmitterListNodeAllocator.freeAll();
        mParticleHolder.mParticleListNodeAllocator.freeAll();

        mTrailHolder.mTrails.dataArrayFreeAll();
        mReanimationHolder.mReanimations.dataArrayFreeAll();
        mAttachmentHolder.mAttachments.dataArrayFreeAll();
    }

    /** Ported from src/PvzpLib/EffectSystem.cpp:79. */
    public void processDeleteQueue() {
        // Upstream recycles a slot through DataArray::Free -> DataArrayResetItemAt ->
        // std::destroy_at + std::construct_at (DataArray.h:86-94, :197-202), so freeing a dead
        // object runs its destructor: ~PvzpParticleSystem (PvzpParticle.cpp:245-249),
        // ~Reanimation (Reanimator.cpp:335-339) and ~Attachment (Attachment.cpp:99-102).
        // DataArray is generic here and a Java object has no destructor, so every teardown those
        // destructors perform has to run explicitly before the slot is recycled — most importantly
        // ParticleSystemDie(), the only path that returns a system's emitters to the 1024-entry
        // mEmitters pool (PvzpParticle.cpp:313-322). Without it a level leaks one emitter per
        // naturally-dead effect, the pool fills, and PvzpParticleHolder::AllocParticleSystemFromDef
        // starts returning nullptr (PvzpParticle.cpp:1253-1262) — every later effect silently fails
        // to spawn until the level is left (EffectSystemFreeAll resets the pools).
        for (PvzpParticleSystem aParticle : mParticleHolder.mParticleSystems) {
            if (aParticle.mDead) {
                aParticle.particleSystemDie();
                mParticleHolder.mParticleSystems.dataArrayFree(aParticle);
            }
        }

        for (Trail aTrail : mTrailHolder.mTrails) {
            if (aTrail.mDead) {
                mTrailHolder.mTrails.dataArrayFree(aTrail);
            }
        }

        for (Reanimation aReanim : mReanimationHolder.mReanimations) {
            if (aReanim.mDead) {
                aReanim.reanimationDie();
                aReanim.reanimationDelete();
                mReanimationHolder.mReanimations.dataArrayFree(aReanim);
            }
        }

        for (Attachment aAttachment : mAttachmentHolder.mAttachments) {
            if (aAttachment.mDead) {
                aAttachment.attachmentDie();
                mAttachmentHolder.mAttachments.dataArrayFree(aAttachment);
            }
        }
    }

    /** Ported from src/PvzpLib/EffectSystem.cpp:98. */
    public void update() {
        for (PvzpParticleSystem aParticle : mParticleHolder.mParticleSystems) {
            if (!aParticle.mIsAttachment) {
                aParticle.update();
            }
        }

        for (Trail aTrail : mTrailHolder.mTrails) {
            if (!aTrail.mIsAttachment) {
                aTrail.update();
            }
        }

        for (Reanimation aReanim : mReanimationHolder.mReanimations) {
            if (!aReanim.mIsAttachment) {
                aReanim.update();
            }
        }
    }
}
