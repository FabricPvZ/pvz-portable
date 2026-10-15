package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.lawn.AttachmentID;

/**
 * Mutable holder replacing the C++ {@code AttachmentID&} out-parameter of the attachment API
 * (src/PvzpLib/Attachment.h:87-104).
 *
 * <p>The upstream free functions both read and <em>write</em> the caller's attachment id: they
 * null it out when the attachment is gone ({@code AttachmentDie}/{@code AttachmentDetach}), update
 * it when a new attachment is allocated ({@code CreateEffectAttachment}), and
 * {@code AttachmentDetachCrossFadeParticleType} clears it when the attachment empties. Java has no
 * reference parameters, so callers (tracks and entities) own an {@code AttachmentIDRef} and pass it
 * to those functions; the initial value is {@link AttachmentID#ATTACHMENTID_NULL}.
 */
public final class AttachmentIDRef {
    public int value;

    public AttachmentIDRef() {
        value = AttachmentID.ATTACHMENTID_NULL;
    }

    public AttachmentIDRef(int theValue) {
        value = theValue;
    }
}
