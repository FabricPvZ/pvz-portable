package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/PvzpLib/Reanimator.h:146-163 (class ReanimatorTransform) and
 * src/PvzpLib/Reanimator.cpp:192-205 (ReanimatorTransform::ReanimatorTransform).
 *
 * <p>One animation frame of one track. Every numeric field starts at
 * {@link Reanimator#DEFAULT_FIELD_PLACEHOLDER} so the previous-value inheritance pass
 * ({@code ReanimationFillInMissingData}) can tell an unset element from a real 0/1 value.
 */
public class ReanimatorTransform {
    public float mTransX;
    public float mTransY;
    public float mSkewX;
    public float mSkewY;
    public float mScaleX;
    public float mScaleY;
    public float mFrame;
    public float mAlpha;
    public Image mImage;
    public Font mFont;
    public String mText;

    public ReanimatorTransform() {
        mTransX = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mTransY = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mSkewX = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mSkewY = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mScaleX = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mScaleY = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mFrame = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mAlpha = Reanimator.DEFAULT_FIELD_PLACEHOLDER;
        mImage = null;
        mFont = null;
        mText = "";
    }

    /** Replaces upstream's compiler-generated copy assignment ({@code *this = theOther}). */
    public ReanimatorTransform assign(ReanimatorTransform theOther) {
        mTransX = theOther.mTransX;
        mTransY = theOther.mTransY;
        mSkewX = theOther.mSkewX;
        mSkewY = theOther.mSkewY;
        mScaleX = theOther.mScaleX;
        mScaleY = theOther.mScaleY;
        mFrame = theOther.mFrame;
        mAlpha = theOther.mAlpha;
        mImage = theOther.mImage;
        mFont = theOther.mFont;
        mText = theOther.mText;
        return this;
    }
}
