package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:260 (class ParticleRenderParams).
 *
 * <p>The 8 "is set" flags and the interpolated values passed between {@code GetRenderParams},
 * {@code RenderParticle} and the cross-fade blend. Public field names are upstream.
 */
public class ParticleRenderParams {

    public boolean mRedIsSet;
    public boolean mGreenIsSet;
    public boolean mBlueIsSet;
    public boolean mAlphaIsSet;
    public boolean mParticleScaleIsSet;
    public boolean mParticleStretchIsSet;
    public boolean mSpinPositionIsSet;
    public boolean mPositionIsSet;
    public float mRed;
    public float mGreen;
    public float mBlue;
    public float mAlpha;
    public float mParticleScale;
    public float mParticleStretch;
    public float mSpinPosition;
    public float mPosX;
    public float mPosY;
}
