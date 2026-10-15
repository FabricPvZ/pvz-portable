package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.lawn.AttachmentID;
import cn.enaium.fabricpvz.portable.lawn.EffectType;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.fx.Trail;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/PvzpLib/Attachment.h:31-120 (Attachment plus the attach/detach free functions)
 * and src/PvzpLib/Attachment.cpp (whole file).
 *
 * <p>Attach-to-track plumbing: one {@link Attachment} owns up to
 * {@link #MAX_EFFECTS_PER_ATTACHMENT} effects (particles, trails, sub-reanimations and nested
 * attachments). Every lookup goes through the {@link EffectSystem} holders, exactly like upstream.
 *
 * <p>PORT-NOTEs:
 * <ul>
 *   <li>C++ {@code AttachmentID&} out-parameters become {@link AttachmentIDRef} arguments: the
 *       functions can allocate a new attachment and write its id
 *       ({@link #createEffectAttachment}) or null it out when it is gone
 *       ({@link #attachmentDie}/{@link #attachmentDetach}).</li>
 *   <li>Upstream's fixed C array of value-type {@link AttachEffect}s becomes an array of objects;
 *       removal shifts the references (upstream {@code memmove}s the elements).</li>
 *   <li>{@link #attachmentDetachCrossFadeParticleType} compares the particle system's
 *       {@code ParticleEffect} (upstream compares its {@code mParticleDef} with
 *       {@code &gParticleDefArray[effect]}; a system's definition is its effect type by
 *       construction, PvzpParticle.cpp:1273-1275).</li>
 * </ul>
 */
public class Attachment {
    /** Upstream {@code MAX_EFFECTS_PER_ATTACHMENT} (Attachment.h:32). */
    public static final int MAX_EFFECTS_PER_ATTACHMENT = 16;

    public AttachEffect[] mEffectArray;
    public int mNumEffects;
    public boolean mDead;

    /** Ported from src/PvzpLib/Attachment.cpp:86-90 (Attachment::Attachment). */
    public Attachment() {
        mEffectArray = new AttachEffect[MAX_EFFECTS_PER_ATTACHMENT];
        for (int i = 0; i < MAX_EFFECTS_PER_ATTACHMENT; i++) {
            mEffectArray[i] = new AttachEffect();
        }
        mNumEffects = 0;
        mDead = false;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:22-84 (PruneDeadEffects). */
    private static void pruneDeadEffects(Attachment theAttachment) {
        for (int i = 0; i < theAttachment.mNumEffects;) {
            AttachEffect aAttachEffect = theAttachment.mEffectArray[i];
            boolean aStillAlive = false;
            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    aStillAlive = aParticleSystem != null && !aParticleSystem.mDead;
                    break;
                }
                case EffectType.EFFECT_TRAIL: {
                    Trail aTrail = EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayTryToGet(aAttachEffect.mEffectID);
                    aStillAlive = aTrail != null && !aTrail.mDead;
                    break;
                }
                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    aStillAlive = aReanimation != null && !aReanimation.mDead;
                    break;
                }
                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    aStillAlive = aAttachment != null && !aAttachment.mDead;
                    break;
                }
                case EffectType.EFFECT_OTHER:
                    aStillAlive = true;
                    break;
                default:
                    break;
            }

            if (!aStillAlive) {
                int aNumEffectsRemaining = theAttachment.mNumEffects - i - 1;
                if (aNumEffectsRemaining > 0) {
                    System.arraycopy(theAttachment.mEffectArray, i + 1, theAttachment.mEffectArray, i, aNumEffectsRemaining);
                }
                theAttachment.mNumEffects--;
                continue;
            }

            i++;
        }

        if (theAttachment.mNumEffects == 0) {
            theAttachment.mDead = true;
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:97-173 (Attachment::Update). */
    public void update() {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            boolean isEmpty = true;
            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null && !aParticleSystem.mDead) {
                        aParticleSystem.update();
                        isEmpty = false;
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL: {
                    Trail aTrail = EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aTrail != null && !aTrail.mDead) {
                        aTrail.update();
                        isEmpty = false;
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null && !aReanimation.mDead) {
                        aReanimation.update();
                        isEmpty = false;
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.update();
                        isEmpty = false;
                    }
                    break;
                }

                default:
                    break;
            }

            if (isEmpty) {
                int aNumEffectsRemaining = mNumEffects - i - 1;
                if (aNumEffectsRemaining > 0) {
                    System.arraycopy(mEffectArray, i + 1, mEffectArray, i, aNumEffectsRemaining);
                    i--;
                }
                mNumEffects--;
            }
        }

        if (mNumEffects == 0) {
            mDead = true;
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:175-238 (Attachment::SetPosition). */
    public void setPosition(SexyVector2 thePosition) {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            SexyVector2 aNewPos = aAttachEffect.mOffset.times(thePosition);

            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null) {
                        aParticleSystem.systemMove(aNewPos.x, aNewPos.y);
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL: {
                    Trail aTrail = EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aTrail != null) {
                        aTrail.addPoint(aNewPos.x, aNewPos.y);
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null) {
                        aReanimation.setPosition(aNewPos.x, aNewPos.y);
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.setPosition(aNewPos);
                    }
                    break;
                }

                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:240-287 (Attachment::OverrideColor). */
    public void overrideColor(Color theColor) {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null) {
                        aParticleSystem.overrideColor("", theColor);
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null) {
                        aReanimation.mColorOverride = theColor;
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.overrideColor(theColor);
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL:
                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:289-350 (Attachment::PropogateColor). */
    public void propogateColor(Color theColor, boolean theEnableAdditiveColor, Color theAdditiveColor, boolean theEnableOverlayColor, Color theOverlayColor) {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            if (aAttachEffect.mDontPropogateColor) {
                continue;
            }

            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null) {
                        aParticleSystem.overrideColor(null, theColor);
                        aParticleSystem.overrideExtraAdditiveDraw(null, theEnableAdditiveColor);
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null) {
                        aReanimation.mColorOverride = theColor;
                        aReanimation.mExtraAdditiveColor = theAdditiveColor;
                        aReanimation.mEnableExtraAdditiveDraw = theEnableAdditiveColor;
                        aReanimation.mExtraOverlayColor = theOverlayColor;
                        aReanimation.mEnableExtraOverlayDraw = theEnableOverlayColor;
                        aReanimation.propogateColorToAttachments();
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.propogateColor(theColor, theEnableAdditiveColor, theAdditiveColor, theEnableOverlayColor, theOverlayColor);
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL:
                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:352-397 (Attachment::OverrideScale). */
    public void overrideScale(float theScale) {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null) {
                        aParticleSystem.overrideScale(null, theScale);
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null) {
                        aReanimation.overrideScale(theScale, theScale);
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.overrideScale(theScale);
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL:
                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:399-416 (Attachment::CrossFade). */
    public void crossFade(String theCrossFadeName) {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            if (aAttachEffect.mEffectType == EffectType.EFFECT_PARTICLE) {
                PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                if (aParticleSystem != null) {
                    aParticleSystem.crossFade(theCrossFadeName);
                }
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:418-487 (Attachment::SetMatrix). */
    public void setMatrix(SexyTransform2D theMatrix) {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            SexyTransform2D aPosition = new SexyTransform2D(theMatrix.times(aAttachEffect.mOffset));

            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null) {
                        aParticleSystem.systemMove(aPosition.m02, aPosition.m12);
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL: {
                    Trail aTrail = EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aTrail != null) {
                        aTrail.mTrailCenter = new SexyVector2(aPosition.m02, aPosition.m12);
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null) {
                        aReanimation.mOverlayMatrix = aPosition;
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.setMatrix(aPosition);
                    }
                    break;
                }

                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:489-554 (Attachment::Draw). */
    public void draw(Graphics g, boolean theParentHidden) {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            if (theParentHidden && aAttachEffect.mDontDrawIfParentHidden) {
                continue;
            }

            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null) {
                        aParticleSystem.draw(g);
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL: {
                    Trail aTrail = EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aTrail != null) {
                        aTrail.draw(g);
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null) {
                        aReanimation.drawAllRenderGroups(g);
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.draw(g, theParentHidden);
                    }
                    break;
                }

                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:524-588 (Attachment::Detach). */
    public void detach() {
        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aParticleSystem != null) {
                        aParticleSystem.mIsAttachment = false;
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL: {
                    Trail aTrail = EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aTrail != null) {
                        aTrail.mIsAttachment = false;
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aReanimation != null) {
                        aReanimation.mIsAttachment = false;
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID);
                    if (aAttachment != null) {
                        aAttachment.detach();
                    }
                    break;
                }

                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }

            aAttachEffect.mEffectID = 0;
        }

        mNumEffects = 0;
        mDead = true;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:590-681 (Attachment::AttachmentDie). */
    public void attachmentDie() {
        if (mNumEffects == 0) {
            mDead = true;
            return;
        }

        if (EffectSystem.gEffectSystem == null) {
            mNumEffects = 0;
            mDead = true;
            return;
        }

        for (int i = 0; i < mNumEffects; i++) {
            AttachEffect aAttachEffect = mEffectArray[i];
            switch (aAttachEffect.mEffectType) {
                case EffectType.EFFECT_PARTICLE: {
                    PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder != null
                            ? EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID) : null;
                    if (aParticleSystem != null) {
                        aParticleSystem.particleSystemDie();
                    }
                    break;
                }

                case EffectType.EFFECT_TRAIL: {
                    Trail aTrail = EffectSystem.gEffectSystem.mTrailHolder != null
                            ? EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayTryToGet(aAttachEffect.mEffectID) : null;
                    if (aTrail != null) {
                        aTrail.mDead = true;
                    }
                    break;
                }

                case EffectType.EFFECT_REANIM: {
                    Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder != null
                            ? EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID) : null;
                    if (aReanimation != null) {
                        aReanimation.reanimationDie();
                    }
                    break;
                }

                case EffectType.EFFECT_ATTACHMENT: {
                    Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder != null
                            ? EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(aAttachEffect.mEffectID) : null;
                    if (aAttachment != null) {
                        aAttachment.attachmentDie();
                    }
                    break;
                }

                case EffectType.EFFECT_OTHER:
                    break;
                default:
                    break;
            }

            aAttachEffect.mEffectID = 0;
        }

        mNumEffects = 0;
        mDead = true;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:720-742 (AttachmentUpdateAndSetMatrix). */
    public static void attachmentUpdateAndSetMatrix(AttachmentIDRef theAttachmentID, SexyTransform2D theMatrix) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment != null) {
            aAttachment.update();
            aAttachment.setMatrix(theMatrix);
        } else {
            theAttachmentID.value = AttachmentID.ATTACHMENTID_NULL;
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:744-764 (AttachmentUpdateAndMove). */
    public static void attachmentUpdateAndMove(AttachmentIDRef theAttachmentID, float theX, float theY) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment != null) {
            aAttachment.update();
            aAttachment.setPosition(new SexyVector2(theX, theY));
        } else {
            theAttachmentID.value = AttachmentID.ATTACHMENTID_NULL;
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:766-780 (AttachmentOverrideColor). */
    public static void attachmentOverrideColor(AttachmentIDRef theAttachmentID, Color theColor) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment != null) {
            aAttachment.overrideColor(theColor);
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:782-796 (AttachmentOverrideScale). */
    public static void attachmentOverrideScale(AttachmentIDRef theAttachmentID, float theScale) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment != null) {
            aAttachment.overrideScale(theScale);
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:798-824 (AttachmentReanimTypeDie). */
    public static void attachmentReanimTypeDie(AttachmentIDRef theAttachmentID, int theReanimType) {
        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment == null) {
            return;
        }

        for (int i = 0; i < aAttachment.mNumEffects; i++) {
            AttachEffect aAttachEffect = aAttachment.mEffectArray[i];
            if (aAttachEffect.mEffectType == EffectType.EFFECT_REANIM) {
                Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                if (aReanimation != null && aReanimation.mReanimationType == theReanimType) {
                    aReanimation.reanimationDie();
                }
            }
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:826-888 (AttachmentDetachCrossFadeParticleType). */
    public static void attachmentDetachCrossFadeParticleType(AttachmentIDRef theAttachmentID, int theParticleEffect, String theCrossFadeName) {
        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment == null) {
            return;
        }

        for (int i = 0; i < aAttachment.mNumEffects;) {
            AttachEffect aAttachEffect = aAttachment.mEffectArray[i];
            if (aAttachEffect.mEffectType != EffectType.EFFECT_PARTICLE) {
                i++;
                continue;
            }

            PvzpParticleSystem aParticleSystem = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(aAttachEffect.mEffectID);
            if (aParticleSystem != null && aParticleSystem.mEffectType == theParticleEffect) {
                if (theCrossFadeName != null) {
                    aParticleSystem.mIsAttachment = false;
                    aParticleSystem.crossFade(theCrossFadeName);
                } else {
                    aParticleSystem.particleSystemDie();
                }

                // Remove the effect entry immediately so the parent attachment ownership remains consistent.
                int aNumEffectsRemaining = aAttachment.mNumEffects - i - 1;
                if (aNumEffectsRemaining > 0) {
                    System.arraycopy(aAttachment.mEffectArray, i + 1, aAttachment.mEffectArray, i, aNumEffectsRemaining);
                }
                aAttachment.mNumEffects--;
                continue;
            }

            i++;
        }

        if (aAttachment.mNumEffects == 0) {
            aAttachment.mDead = true;
            theAttachmentID.value = AttachmentID.ATTACHMENTID_NULL;
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:890-909 (AttachmentPropogateColor). */
    public static void attachmentPropogateColor(AttachmentIDRef theAttachmentID, Color theColor, boolean theEnableAdditiveColor, Color theAdditiveColor, boolean theEnableOverlayColor, Color theOverlayColor) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment != null) {
            aAttachment.propogateColor(theColor, theEnableAdditiveColor, theAdditiveColor, theEnableOverlayColor, theOverlayColor);
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:911-926 (AttachmentCrossFade). */
    public static void attachmentCrossFade(AttachmentIDRef theAttachmentID, String theCrossFadeName) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment != null) {
            aAttachment.crossFade(theCrossFadeName);
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:928-944 (AttachmentDraw). */
    public static void attachmentDraw(AttachmentIDRef theAttachmentID, Graphics g, boolean theParentHidden) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment != null) {
            aAttachment.draw(g, theParentHidden);
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:946-965 (AttachmentDie). */
    public static void attachmentDie(AttachmentIDRef theAttachmentID) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        theAttachmentID.value = AttachmentID.ATTACHMENTID_NULL;
        if (aAttachment != null) {
            aAttachment.attachmentDie();
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:967-986 (AttachmentDetach). */
    public static void attachmentDetach(AttachmentIDRef theAttachmentID) {
        if (theAttachmentID.value == AttachmentID.ATTACHMENTID_NULL) {
            return;
        }

        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        theAttachmentID.value = AttachmentID.ATTACHMENTID_NULL;
        if (aAttachment != null) {
            aAttachment.detach();
        }
    }

    /** Ported from src/PvzpLib/Attachment.cpp:988-1010 (FindReanimAttachment). */
    public static Reanimation findReanimAttachment(AttachmentIDRef theAttachmentID) {
        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment == null) {
            return null;
        }

        for (int i = 0; i < aAttachment.mNumEffects; i++) {
            AttachEffect aAttachEffect = aAttachment.mEffectArray[i];
            if (aAttachEffect.mEffectType == EffectType.EFFECT_REANIM) {
                Reanimation aReanimation = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(aAttachEffect.mEffectID);
                if (aReanimation != null) {
                    return aReanimation;
                }
            }
        }

        return null;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:1012-1025 (FindFirstAttachment). */
    public static AttachEffect findFirstAttachment(AttachmentIDRef theAttachmentID) {
        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment == null) {
            return null;
        }

        return aAttachment.mNumEffects == 0 ? null : aAttachment.mEffectArray[0];
    }

    /** Ported from src/PvzpLib/Attachment.cpp:1027-1047 (CreateEffectAttachment). */
    public static AttachEffect createEffectAttachment(AttachmentIDRef theAttachmentID, int theEffectType, int theDataID, float theOffsetX, float theOffsetY) {
        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        if (aAttachment == null || aAttachment.mDead) {
            aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.allocAttachment();
            theAttachmentID.value = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayGetID(aAttachment);
        }

        AttachEffect aAttachEffect = aAttachment.mEffectArray[aAttachment.mNumEffects];
        aAttachEffect.mEffectType = theEffectType;
        aAttachEffect.mEffectID = theDataID;
        aAttachEffect.mDontDrawIfParentHidden = false;
        aAttachEffect.mOffset.loadIdentity();
        aAttachEffect.mOffset.m02 = theOffsetX;
        aAttachEffect.mOffset.m12 = theOffsetY;
        aAttachment.mNumEffects++;
        return aAttachEffect;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:1049-1059 (AttachReanim). */
    public static AttachEffect attachReanim(AttachmentIDRef theAttachmentID, Reanimation theReanimation, float theOffsetX, float theOffsetY) {
        int aReanimId = EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayGetID(theReanimation);
        AttachEffect aAttachEffect = createEffectAttachment(theAttachmentID, EffectType.EFFECT_REANIM, aReanimId, theOffsetX, theOffsetY);

        theReanimation.mIsAttachment = true;

        return aAttachEffect;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:1061-1073 (AttachParticle). */
    public static AttachEffect attachParticle(AttachmentIDRef theAttachmentID, PvzpParticleSystem theParticleSystem, float theOffsetX, float theOffsetY) {
        if (theParticleSystem == null) {
            return null;
        }

        int aParticleId = EffectSystem.gEffectSystem.mParticleHolder.mParticleSystems.dataArrayGetID(theParticleSystem);
        AttachEffect aAttachEffect = createEffectAttachment(theAttachmentID, EffectType.EFFECT_PARTICLE, aParticleId, theOffsetX, theOffsetY);

        theParticleSystem.mIsAttachment = true;

        return aAttachEffect;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:1075-1087 (AttachTrail). */
    public static AttachEffect attachTrail(AttachmentIDRef theAttachmentID, Trail theTrail, float theOffsetX, float theOffsetY) {
        int aTrailId = EffectSystem.gEffectSystem.mTrailHolder.mTrails.dataArrayGetID(theTrail);
        AttachEffect aAttachEffect = createEffectAttachment(theAttachmentID, EffectType.EFFECT_TRAIL, aTrailId, theOffsetX, theOffsetY);

        theTrail.mIsAttachment = true;

        return aAttachEffect;
    }

    /** Ported from src/PvzpLib/Attachment.cpp:1089-1093 (IsFullOfAttachments). */
    public static boolean isFullOfAttachments(AttachmentIDRef theAttachmentID) {
        Attachment aAttachment = EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
        return aAttachment != null && aAttachment.mNumEffects >= MAX_EFFECTS_PER_ATTACHMENT;
    }

    /** Exposed for {@link AttachmentHolder#allocAttachment()}, which prunes dead effects. */
    static void pruneDead(Attachment theAttachment) {
        pruneDeadEffects(theAttachment);
    }
}
