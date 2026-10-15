package cn.enaium.fabricpvz.portable.pvzp.reanim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.AttachmentID;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;

/**
 * Attachment semantics against a live {@link EffectSystem}: attach/detach/re-parent, id
 * invalidation, per-entry index lookup and the effect-count limit.
 */
class AttachmentTest {

    @BeforeEach
    void setUp() {
        new EffectSystem().effectSystemInitialize();
    }

    @AfterEach
    void tearDown() {
        EffectSystem.gEffectSystem.effectSystemDispose();
    }

    private static Attachment attachmentOf(AttachmentIDRef theAttachmentID) {
        return EffectSystem.gEffectSystem.mAttachmentHolder.mAttachments.dataArrayTryToGet(theAttachmentID.value);
    }

    private static Reanimation allocReanim() {
        return EffectSystem.gEffectSystem.mReanimationHolder.mReanimations.dataArrayAlloc();
    }

    /** A pool-backed reanimation owning the single track {@code body}. */
    private static Reanimation allocTrackedReanim() {
        Reanimation aReanimation = allocReanim();
        aReanimation.mDefinition = ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("body",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, null)));
        aReanimation.mTrackInstances = new ReanimatorTrackInstance[] {new ReanimatorTrackInstance()};
        aReanimation.mFrameCount = 1;
        return aReanimation;
    }

    @Test
    void attachesAndDetachesAReanimation() {
        Reanimation aReanimation = allocReanim();
        AttachmentIDRef aRef = new AttachmentIDRef();

        AttachEffect anEffect = Attachment.attachReanim(aRef, aReanimation, 5.0f, 7.0f);
        assertNotEquals(AttachmentID.ATTACHMENTID_NULL, aRef.value);
        assertTrue(aReanimation.mIsAttachment);
        assertNotNull(attachmentOf(aRef));
        assertSame(aReanimation, Attachment.findReanimAttachment(aRef));
        assertSame(anEffect, Attachment.findFirstAttachment(aRef));
        assertEquals(5.0f, anEffect.mOffset.m02, 1e-6f);
        assertEquals(7.0f, anEffect.mOffset.m12, 1e-6f);
        assertFalse(Attachment.isFullOfAttachments(aRef));

        Attachment.attachmentDetach(aRef);
        assertEquals(AttachmentID.ATTACHMENTID_NULL, aRef.value);
        assertFalse(aReanimation.mIsAttachment);
        assertNull(Attachment.findReanimAttachment(aRef));
        assertNull(Attachment.findFirstAttachment(aRef));
    }

    @Test
    void attachmentDieKillsTheAttachedEffectsAndClearsTheId() {
        Reanimation aReanimation = allocReanim();
        aReanimation.mReanimationType = ReanimationType.REANIM_SUN;
        AttachmentIDRef aRef = new AttachmentIDRef();
        Attachment.attachReanim(aRef, aReanimation, 0.0f, 0.0f);

        Attachment.attachmentDie(aRef);
        assertEquals(AttachmentID.ATTACHMENTID_NULL, aRef.value);
        assertTrue(aReanimation.mDead);

        Reanimation aSecond = allocReanim();
        aSecond.mReanimationType = ReanimationType.REANIM_SUN;
        AttachmentIDRef aSecondRef = new AttachmentIDRef();
        Attachment.attachReanim(aSecondRef, aSecond, 0.0f, 0.0f);
        Attachment.attachmentReanimTypeDie(aSecondRef, ReanimationType.REANIM_SUN);
        assertTrue(aSecond.mDead);
        assertNotEquals(AttachmentID.ATTACHMENTID_NULL, aSecondRef.value);  // type-die keeps the attachment alive
        Attachment.attachmentReanimTypeDie(aSecondRef, ReanimationType.REANIM_ZOMBIE);
    }

    @Test
    void reparentsBetweenParentTracks() {
        Reanimation aParent = allocTrackedReanim();
        Reanimation aSecondParent = allocTrackedReanim();
        Reanimation aChild = allocReanim();

        aChild.attachToAnotherReanimation(aParent, "body");
        assertEquals(0, aParent.mFrameBasePose);  // upstream pins the base pose to the animation's start frame
        AttachmentIDRef aParentRef = aParent.getTrackInstanceByName("body").mAttachmentID;
        assertSame(aChild, Attachment.findReanimAttachment(aParentRef));
        assertTrue(aChild.mIsAttachment);

        // Re-parent: detach from the first parent, then attach into the second parent's track.
        Attachment.attachmentDetach(aParentRef);
        aChild.attachToAnotherReanimation(aSecondParent, "body");
        AttachmentIDRef aSecondParentRef = aSecondParent.getTrackInstanceByName("body").mAttachmentID;
        assertSame(aChild, Attachment.findReanimAttachment(aSecondParentRef));
        assertNull(Attachment.findReanimAttachment(aParentRef));
        assertTrue(aChild.mIsAttachment);
    }

    @Test
    void looksUpEffectsByTheirIndex() {
        AttachmentIDRef aRef = new AttachmentIDRef();
        Reanimation aFirst = allocReanim();
        AttachEffect aFirstEffect = Attachment.attachReanim(aRef, aFirst, 0.0f, 0.0f);
        Reanimation aSecond = allocReanim();
        Attachment.attachReanim(aRef, aSecond, 1.0f, 1.0f);

        Attachment aAttachment = attachmentOf(aRef);
        assertEquals(2, aAttachment.mNumEffects);
        assertSame(aFirstEffect, Attachment.findFirstAttachment(aRef));
        assertSame(aFirst, Attachment.findReanimAttachment(aRef));  // the first matching entry wins

        for (int i = 2; i < Attachment.MAX_EFFECTS_PER_ATTACHMENT; i++) {
            Attachment.attachReanim(aRef, allocReanim(), 0.0f, 0.0f);
        }
        assertEquals(Attachment.MAX_EFFECTS_PER_ATTACHMENT, aAttachment.mNumEffects);
        assertTrue(Attachment.isFullOfAttachments(aRef));
    }

    @Test
    void updateAndSetMatrixDrivesTheAttachedReanimation() {
        Reanimation aReanimation = allocReanim();
        AttachmentIDRef aRef = new AttachmentIDRef();
        Attachment.attachReanim(aRef, aReanimation, 0.0f, 0.0f);

        SexyTransform2D aMatrix = new SexyTransform2D();
        aMatrix.m02 = 33.0f;
        aMatrix.m12 = 44.0f;
        Attachment.attachmentUpdateAndSetMatrix(aRef, aMatrix);
        assertEquals(33.0f, aReanimation.mOverlayMatrix.m02, 1e-6f);
        assertEquals(44.0f, aReanimation.mOverlayMatrix.m12, 1e-6f);

        Attachment.attachmentUpdateAndMove(aRef, 11.0f, 22.0f);
        assertEquals(11.0f, aReanimation.mOverlayMatrix.m02, 1e-6f);
        assertEquals(22.0f, aReanimation.mOverlayMatrix.m12, 1e-6f);
    }

    @Test
    void invalidatesIdsWhoseAttachmentIsGone() {
        AttachmentIDRef aRef = new AttachmentIDRef(4711);
        Attachment.attachmentUpdateAndMove(aRef, 0.0f, 0.0f);
        assertEquals(AttachmentID.ATTACHMENTID_NULL, aRef.value);

        AttachmentIDRef aSecondRef = new AttachmentIDRef(4712);
        SexyTransform2D aMatrix = new SexyTransform2D();
        Attachment.attachmentUpdateAndSetMatrix(aSecondRef, aMatrix);
        assertEquals(AttachmentID.ATTACHMENTID_NULL, aSecondRef.value);
    }
}
