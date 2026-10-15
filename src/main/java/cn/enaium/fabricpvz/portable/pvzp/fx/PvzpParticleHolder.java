package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpAllocator;
import cn.enaium.fabricpvz.portable.pvzp.PvzpList;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:242 (class PvzpParticleHolder) and
 * src/PvzpLib/PvzpParticle.cpp:1229-1275.
 *
 * <p>Owns the three fixed-capacity {@link DataArray} pools (systems, emitters, particles) plus the
 * two intrusive-list node allocators. All pools are sized 1024 ({@code DataArrayInitialize(1024)}).
 * Public field names are upstream.
 *
 * <p>PORT-NOTE: upstream sizes the node allocators with {@code sizeof(PvzpListNode<id>)};
 * {@link PvzpList#NODE_SIZE} is the port's equivalent constant used for both allocators.
 */
public class PvzpParticleHolder {

    public DataArray<PvzpParticleSystem> mParticleSystems = new DataArray<>(PvzpParticleSystem::new);
    public DataArray<PvzpParticleEmitter> mEmitters = new DataArray<>(PvzpParticleEmitter::new);
    public DataArray<PvzpParticle> mParticles = new DataArray<>(PvzpParticle::new);
    public PvzpAllocator mParticleListNodeAllocator = new PvzpAllocator();
    public PvzpAllocator mEmitterListNodeAllocator = new PvzpAllocator();

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1229. */
    public void initializeHolder() {
        mParticleSystems.dataArrayInitialize(1024, "particle systems");
        mEmitters.dataArrayInitialize(1024, "emitters");
        mParticles.dataArrayInitialize(1024, "particles");
        mParticleListNodeAllocator.initialize(1024, PvzpList.NODE_SIZE);
        mEmitterListNodeAllocator.initialize(1024, PvzpList.NODE_SIZE);
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1238. */
    public void disposeHolder() {
        mParticleSystems.dataArrayDispose();
        mEmitters.dataArrayDispose();
        mParticles.dataArrayDispose();
        mParticleListNodeAllocator.freeAll();
        mEmitterListNodeAllocator.freeAll();
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1247. */
    public boolean isOverLoaded() {
        return mParticleSystems.mSize > PvzpParticleEmitter.MAX_PARTICLES_SIZE
                || mEmitters.mSize > PvzpParticleEmitter.MAX_PARTICLES_SIZE
                || mParticles.mSize > PvzpParticleEmitter.MAX_PARTICLES_SIZE;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1252. */
    public PvzpParticleSystem allocParticleSystemFromDef(float theX, float theY, int theRenderOrder, PvzpParticleDefinition theDefinition, int theParticleEffect) {
        if (mParticleSystems.mSize == mParticleSystems.mMaxSize) {
            return null;
        }
        if (theDefinition.mEmitterDefs.mArrayCount + mEmitters.mSize > mEmitters.mMaxSize) {
            return null;
        }

        PvzpParticleSystem aPvzpParticle = mParticleSystems.dataArrayAlloc();
        aPvzpParticle.mParticleHolder = this;
        aPvzpParticle.pvzpParticleInitializeFromDef(theX, theY, theRenderOrder, theDefinition, theParticleEffect);
        return aPvzpParticle;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1271. */
    public PvzpParticleSystem allocParticleSystem(float theX, float theY, int theRenderOrder, int theParticleEffect) {
        return allocParticleSystemFromDef(theX, theY, theRenderOrder, PvzpParticleDefinitions.gParticleDefArray[theParticleEffect], theParticleEffect);
    }
}
