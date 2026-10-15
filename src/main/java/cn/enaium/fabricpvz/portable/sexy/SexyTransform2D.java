package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/SexyMatrix.h:58 and src/SexyAppFramework/misc/SexyMatrix.cpp:91.
 *
 * <p>Faithful port of {@code Sexy::SexyTransform2D}, the 2D affine transform specialization
 * of {@link SexyMatrix3}. All transforms pre-multiply ({@code *this = aMat * (*this)}), matching
 * upstream.
 */
public class SexyTransform2D extends SexyMatrix3 {
    public SexyTransform2D() {
        loadIdentity();
    }

    public SexyTransform2D(boolean loadIdentity) {
        if (loadIdentity) {
            loadIdentity();
        }
    }

    public SexyTransform2D(SexyMatrix3 theMatrix) {
        assign(theMatrix);
    }

    // operator=(const SexyMatrix3&)
    public SexyTransform2D assign(SexyMatrix3 theMat) {
        super.assign(theMat);
        return this;
    }

    // Translate
    public void translate(float tx, float ty) {
        SexyMatrix3 aMat = new SexyMatrix3();
        aMat.loadIdentity();
        aMat.m02 = tx;
        aMat.m12 = ty;
        aMat.m22 = 1;

        assign(aMat.times(this));
    }

    // RotateRad
    // PORT-NOTE: upstream uses sinf/cosf (float); Java promotes to double, then rounds back to float.
    public void rotateRad(float rot) {
        SexyMatrix3 aMat = new SexyMatrix3();
        aMat.loadIdentity();

        float sinRot = -(float) Math.sin(rot);
        float cosRot = (float) Math.cos(rot);

        aMat.m00 = cosRot;
        aMat.m01 = -sinRot;
        aMat.m10 = sinRot;
        aMat.m11 = cosRot;

        assign(aMat.times(this));
    }

    // RotateDeg
    public void rotateDeg(float rot) {
        rotateRad(3.1415926535897932384626433832795028841971f * rot / 180.0f);
    }

    // Scale
    public void scale(float sx, float sy) {
        SexyMatrix3 aMat = new SexyMatrix3();
        aMat.loadIdentity();
        aMat.m00 = sx;
        aMat.m11 = sy;

        assign(aMat.times(this));
    }
}
