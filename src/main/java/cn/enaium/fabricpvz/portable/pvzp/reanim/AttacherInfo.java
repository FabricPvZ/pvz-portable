package cn.enaium.fabricpvz.portable.pvzp.reanim;

/**
 * Ported from src/PvzpLib/Attachment.h:52-62 (class AttacherInfo).
 *
 * <p>Parsed descriptor of an {@code attacher__*} track's text, see
 * {@link Reanimation#parseAttacherTrack}.
 */
public class AttacherInfo {
    public String mReanimName;
    public String mTrackName;
    public float mAnimRate;
    public int mLoopType;
}
