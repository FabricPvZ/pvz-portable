package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/SexyMatrix.h:33 and src/SexyAppFramework/misc/SexyMatrix.cpp:27.
 *
 * <p>Faithful port of {@code Sexy::SexyMatrix3}. Upstream stores the 3x3 elements in an
 * anonymous union that exposes both {@code float m[3][3]} and the scalar names
 * {@code m00..m22}; Java has no aliasing, so the scalars are the storage and the array
 * access is provided through {@link #get(int, int)} / {@link #set(int, int, float)}.
 *
 * <p>PORT-NOTE: upstream's default constructor leaves the elements uninitialized (C++);
 * Java zero-fills them, which matches upstream's {@code ZeroMatrix()} state.
 */
public class SexyMatrix3 {
    public float m00;
    public float m01;
    public float m02;
    public float m10;
    public float m11;
    public float m12;
    public float m20;
    public float m21;
    public float m22;

    public SexyMatrix3() {
    }

    /** Replaces upstream {@code m[i][j]} read access. */
    public float get(int i, int j) {
        switch (i) {
            case 0:
                switch (j) {
                    case 0: return m00;
                    case 1: return m01;
                    default: return m02;
                }
            case 1:
                switch (j) {
                    case 0: return m10;
                    case 1: return m11;
                    default: return m12;
                }
            default:
                switch (j) {
                    case 0: return m20;
                    case 1: return m21;
                    default: return m22;
                }
        }
    }

    /** Replaces upstream {@code m[i][j]} write access. */
    public void set(int i, int j, float value) {
        switch (i) {
            case 0:
                switch (j) {
                    case 0: m00 = value; break;
                    case 1: m01 = value; break;
                    default: m02 = value; break;
                }
                break;
            case 1:
                switch (j) {
                    case 0: m10 = value; break;
                    case 1: m11 = value; break;
                    default: m12 = value; break;
                }
                break;
            default:
                switch (j) {
                    case 0: m20 = value; break;
                    case 1: m21 = value; break;
                    default: m22 = value; break;
                }
                break;
        }
    }

    // ZeroMatrix
    public void zeroMatrix() {
        m00 = m01 = m02 =
        m10 = m11 = m12 =
        m20 = m21 = m22 = 0;
    }

    // LoadIdentity
    public void loadIdentity() {
        m01 = m02 = m10 = m12 = m20 = m21 = 0;
        m00 = m11 = m22 = 1;
    }

    // operator*(const SexyMatrix3&)
    public SexyMatrix3 times(SexyMatrix3 theMat) {
        SexyMatrix3 aResult = new SexyMatrix3();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float x = 0;
                for (int k = 0; k < 3; k++) {
                    x += get(i, k) * theMat.get(k, j);
                }
                aResult.set(i, j, x);
            }
        }

        return aResult;
    }

    // operator*(const SexyVector2&)
    public SexyVector2 times(SexyVector2 theVec) {
        return new SexyVector2(
                m00 * theVec.x + m01 * theVec.y + m02,
                m10 * theVec.x + m11 * theVec.y + m12);
    }

    // operator*(const SexyVector3&)
    public SexyVector3 times(SexyVector3 theVec) {
        return new SexyVector3(
                m00 * theVec.x + m01 * theVec.y + m02 * theVec.z,
                m10 * theVec.x + m11 * theVec.y + m12 * theVec.z,
                m20 * theVec.x + m21 * theVec.y + m22 * theVec.z);
    }

    // operator*=(const SexyMatrix3&)
    public SexyMatrix3 timesAssign(SexyMatrix3 theMat) {
        return assign(times(theMat));
    }

    /** Replaces upstream's compiler-generated {@code operator=(const SexyMatrix3&)}. */
    protected SexyMatrix3 assign(SexyMatrix3 theMat) {
        m00 = theMat.m00;
        m01 = theMat.m01;
        m02 = theMat.m02;
        m10 = theMat.m10;
        m11 = theMat.m11;
        m12 = theMat.m12;
        m20 = theMat.m20;
        m21 = theMat.m21;
        m22 = theMat.m22;
        return this;
    }
}
