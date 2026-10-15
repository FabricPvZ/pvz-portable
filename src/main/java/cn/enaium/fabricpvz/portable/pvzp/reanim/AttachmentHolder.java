package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.pvzp.DataArray;

/**
 * Ported from src/PvzpLib/Attachment.h:106-120 (class AttachmentHolder) and
 * src/PvzpLib/Attachment.cpp:684-718 (InitializeHolder/DisposeHolder/AllocAttachment).
 */
public class AttachmentHolder {
    public DataArray<Attachment> mAttachments;

    public AttachmentHolder() {
        mAttachments = new DataArray<>(Attachment::new);
    }

    public void initializeHolder() {
        mAttachments.dataArrayInitialize(1024, "attachments");
    }

    public void disposeHolder() {
        mAttachments.dataArrayDispose();
    }

    /** Ported from src/PvzpLib/Attachment.cpp:706-718 (AllocAttachment). */
    public Attachment allocAttachment() {
        if (mAttachments.mSize + 1 >= mAttachments.mMaxSize) {
            for (Attachment aAttachment : mAttachments) {
                Attachment.pruneDead(aAttachment);
                if (aAttachment.mDead) {
                    mAttachments.dataArrayFree(aAttachment);
                }
            }
        }

        return mAttachments.dataArrayAlloc();
    }
}
