package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpList;
import cn.enaium.fabricpvz.portable.pvzp.PvzpListNode;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:349 (class PvzpParticleSystem) and
 * src/PvzpLib/PvzpParticle.cpp.
 *
 * <p>Owns a list of live emitters ({@code ParticleEmitterID}s into
 * {@link PvzpParticleHolder#mEmitters}) and is the unit the game creates, updates, moves, overrides
 * and cross-fades. Public field names and the draw/update order are upstream.
 */
public class PvzpParticleSystem {

    public int mEffectType = cn.enaium.fabricpvz.portable.lawn.ParticleEffect.PARTICLE_NONE;
    public PvzpParticleDefinition mParticleDef;
    public PvzpParticleHolder mParticleHolder;
    public PvzpList<Integer> mEmitterList = new PvzpList<>();
    public boolean mDead;
    public boolean mIsAttachment;
    public int mRenderOrder;
    public boolean mDontUpdate;

    /** Ported from src/PvzpLib/PvzpParticle.cpp:236. */
    public PvzpParticleSystem() {
        mEffectType = cn.enaium.fabricpvz.portable.lawn.ParticleEffect.PARTICLE_NONE;
        mParticleDef = null;
        mParticleHolder = null;
        mDead = false;
        mDontUpdate = false;
        mIsAttachment = false;
        mRenderOrder = 0;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:251. */
    public void pvzpParticleInitializeFromDef(float theX, float theY, int theRenderOrder, PvzpParticleDefinition theDefinition, int theEffectType) {
        mEmitterList.setAllocator(mParticleHolder.mEmitterListNodeAllocator);
        mParticleDef = theDefinition;
        mEffectType = theEffectType;
        mRenderOrder = theRenderOrder;

        PvzpEmitterDefinition[] aEmitterDefs = (PvzpEmitterDefinition[]) theDefinition.mEmitterDefs.mArrayData;
        for (int i = 0; i < theDefinition.mEmitterDefs.mArrayCount; i++) {
            PvzpEmitterDefinition aDef = aEmitterDefs[i];
            if (!Definition.floatTrackIsSet(aDef.mCrossFadeDuration)) {
                if (PvzpCommon.testBit(aDef.mParticleFlags, ParticleFlags.PARTICLE_DIE_IF_OVERLOADED) && mParticleHolder.isOverLoaded()) {
                    particleSystemDie();
                    break;
                }
                PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayAlloc();
                aEmitter.pvzpEmitterInitialize(theX, theY, this, aDef);
                mEmitterList.addTail(mParticleHolder.mEmitters.dataArrayGetID(aEmitter));
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:313. */
    public void particleSystemDie() {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            aEmitter.deleteAll();
            mParticleHolder.mEmitters.dataArrayFree(aEmitter);
        }
        mEmitterList.removeAll();
        mDead = true;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:716. */
    public void update() {
        if (!mDontUpdate) {
            boolean aEmitterAlive = false;
            for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
                PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
                aEmitter.update();
                if ((Definition.floatTrackIsSet(aEmitter.mEmitterDef.mCrossFadeDuration) && aEmitter.mParticleList.mSize > 0) || !aEmitter.mDead) {
                    aEmitterAlive = true;
                }
            }
            if (!aEmitterAlive) {
                mDead = true;
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1048. */
    public void draw(Graphics g) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            mParticleHolder.mEmitters.dataArrayGet(aNode.mValue).draw(g);
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1058. */
    public void systemMove(float theX, float theY) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            mParticleHolder.mEmitters.dataArrayGet(aNode.mValue).systemMove(theX, theY);
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1093. */
    public void overrideColor(String theEmitterName, Color theColor) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            if (theEmitterName == null || theEmitterName.equalsIgnoreCase(aEmitter.mEmitterDef.mName)) {
                aEmitter.mColorOverride = theColor;
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1103. */
    public void overrideExtraAdditiveDraw(String theEmitterName, boolean theEnableExtraAdditiveDraw) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            if (theEmitterName == null || theEmitterName.equalsIgnoreCase(aEmitter.mEmitterDef.mName)) {
                aEmitter.mExtraAdditiveDrawOverride = theEnableExtraAdditiveDraw;
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1113. */
    public void overrideImage(String theEmitterName, Image theImage) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            if (theEmitterName == null || theEmitterName.equalsIgnoreCase(aEmitter.mEmitterDef.mName)) {
                aEmitter.mImageOverride = theImage;
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1123. */
    public void overrideFrame(String theEmitterName, int theFrame) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            if (theEmitterName == null || theEmitterName.equalsIgnoreCase(aEmitter.mEmitterDef.mName)) {
                aEmitter.mFrameOverride = theFrame;
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1133. */
    public void overrideScale(String theEmitterName, float theScale) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            if (theEmitterName == null || theEmitterName.equalsIgnoreCase(aEmitter.mEmitterDef.mName)) {
                aEmitter.mScaleOverride = theScale;
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1143. */
    public PvzpParticleEmitter findEmitterByName(String theEmitterName) {
        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            if (theEmitterName.equalsIgnoreCase(aEmitter.mEmitterDef.mName)) {
                return aEmitter;
            }
        }
        return null;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1154. */
    public PvzpEmitterDefinition findEmitterDefByName(String theEmitterName) {
        PvzpEmitterDefinition[] aEmitterDefs = (PvzpEmitterDefinition[]) mParticleDef.mEmitterDefs.mArrayData;
        for (int i = 0; i < mParticleDef.mEmitterDefs.mArrayCount; i++) {
            PvzpEmitterDefinition aEmitterDef = aEmitterDefs[i];
            if (theEmitterName.equalsIgnoreCase(aEmitterDef.mName)) {
                return aEmitterDef;
            }
        }
        return null;
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:1190. */
    public void crossFade(String theEmitterName) {
        PvzpEmitterDefinition aEmitterDef = findEmitterDefByName(theEmitterName);
        if (aEmitterDef == null) {
            return;
        }
        if (!Definition.floatTrackIsSet(aEmitterDef.mCrossFadeDuration)) {
            return;
        }
        if (mParticleHolder.mEmitters.mSize + mEmitterList.mSize > mParticleHolder.mEmitters.mMaxSize) {
            particleSystemDie();
            return;
        }

        for (PvzpListNode<Integer> aNode = mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            if (aEmitter.mEmitterDef != aEmitterDef) {
                PvzpParticleEmitter aCrossFadeEmitter = mParticleHolder.mEmitters.dataArrayAlloc();
                aCrossFadeEmitter.pvzpEmitterInitialize(aEmitter.mSystemCenter.x, aEmitter.mSystemCenter.y, this, aEmitterDef);
                int aCrossFadeEmitterID = mParticleHolder.mEmitters.dataArrayGetID(aCrossFadeEmitter);
                mEmitterList.addTail(aCrossFadeEmitterID);
                aEmitter.crossFadeEmitter(aCrossFadeEmitter);
            }
        }
    }
}
