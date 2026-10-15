package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/SexyMatrix.h:78 and src/SexyAppFramework/misc/SexyMatrix.cpp:135.
 *
 * <p>Faithful port of {@code Sexy::Transform}: a lazily-composed transform that stays in a
 * simple translate/rotate/scale representation until it must become a full matrix.
 */
public class Transform {
    protected SexyTransform2D mMatrix = new SexyTransform2D(false);
    protected boolean mNeedCalcMatrix;

    public boolean mComplex;
    public boolean mHaveRot;
    public boolean mHaveScale;
    public float mTransX1;
    public float mTransY1;
    public float mTransX2;
    public float mTransY2;
    public float mScaleX;
    public float mScaleY;
    public float mRot;

    public Transform() {
        reset();
    }

    // Reset
    public void reset() {
        mNeedCalcMatrix = true;
        mComplex = false;
        mTransX1 = mTransY1 = 0;
        mTransX2 = mTransY2 = 0;
        mScaleX = mScaleY = 1;
        mRot = 0;
        mHaveRot = false;
        mHaveScale = false;
    }

    // Translate
    public void translate(float tx, float ty) {
        if (!mComplex) {
            mNeedCalcMatrix = true;
            if (mHaveRot || mHaveScale) {
                mTransX2 += tx;
                mTransY2 += ty;
            } else {
                mTransX1 += tx;
                mTransY1 += ty;
            }
        } else {
            mMatrix.translate(tx, ty);
        }
    }

    // RotateRad
    public void rotateRad(float rot) {
        if (!mComplex) {
            if (mHaveScale) {
                makeComplex();
                mMatrix.rotateRad(rot);
            } else {
                mNeedCalcMatrix = true;
                mHaveRot = true;
                mRot += rot;
            }
        } else {
            mMatrix.rotateRad(rot);
        }
    }

    // RotateDeg
    public void rotateDeg(float rot) {
        rotateRad(3.1415926535897932384626433832795028841971f * rot / 180.0f);
    }

    // Scale
    public void scale(float sx, float sy) {
        if (!mComplex) {
            if (mHaveRot || mTransX1 != 0 || mTransY1 != 0 || (sx < 0 && mScaleX * sx != -1) || sy < 0) {
                makeComplex();
                mMatrix.scale(sx, sy);
            } else {
                mNeedCalcMatrix = true;
                mHaveScale = true;
                mScaleX *= sx;
                mScaleY *= sy;
            }
        } else {
            mMatrix.scale(sx, sy);
        }
    }

    // MakeComplex
    protected void makeComplex() {
        if (!mComplex) {
            mComplex = true;
            calcMatrix();
        }
    }

    // CalcMatrix
    protected void calcMatrix() {
        if (mNeedCalcMatrix) {
            mNeedCalcMatrix = false;

            mMatrix.loadIdentity();
            mMatrix.m02 = mTransX1;
            mMatrix.m12 = mTransY1;
            mMatrix.m22 = 1;

            if (mHaveScale) {
                mMatrix.m00 = mScaleX;
                mMatrix.m11 = mScaleY;
            } else if (mHaveRot) {
                mMatrix.rotateRad(mRot);
            }

            if (mTransX2 != 0 || mTransY2 != 0) {
                mMatrix.translate(mTransX2, mTransY2);
            }
        }
    }

    // GetMatrix
    public SexyTransform2D getMatrix() {
        calcMatrix();
        return mMatrix;
    }
}
