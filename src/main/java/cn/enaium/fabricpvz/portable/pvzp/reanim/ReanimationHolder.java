package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.pvzp.DataArray;

/**
 * Ported from src/PvzpLib/Reanimator.h:123-135 (class ReanimationHolder) and
 * src/PvzpLib/Reanimator.cpp:1155-1174 (InitializeHolder/AllocReanimation).
 *
 * <p>Fixed 1024-slot pool of {@link Reanimation} owned by
 * {@code cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem#mReanimationHolder}. Upstream also stores the
 * definitions in the holder; this port keeps the process-global definition array in
 * {@link Reanimator} (the researched layout for this repo), so the only state here is the pool.
 */
public class ReanimationHolder {
    public DataArray<Reanimation> mReanimations;

    public ReanimationHolder() {
        mReanimations = new DataArray<>(Reanimation::new);
    }

    public void initializeHolder() {
        mReanimations.dataArrayInitialize(1024, "reanims");
    }

    public void disposeHolder() {
        mReanimations.dataArrayDispose();
    }

    public Reanimation allocReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
        Reanimation aReanim = mReanimations.dataArrayAlloc();
        aReanim.mRenderOrder = theRenderOrder;
        aReanim.mReanimationHolder = this;
        aReanim.reanimationInitializeType(theX, theY, theReanimationType);
        return aReanim;
    }
}
