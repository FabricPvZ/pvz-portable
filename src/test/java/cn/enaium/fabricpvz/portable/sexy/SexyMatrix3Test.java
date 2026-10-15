package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SexyMatrix3}, {@link SexyTransform2D} and {@link Transform}. All expected
 * values are hand-computed from upstream's pre-multiplying composition rules
 * ({@code *this = aMat * (*this)}).
 */
class SexyMatrix3Test {

    private static final float EPS = 1e-5f;

    private static SexyMatrix3 matrix(float... v) {
        SexyMatrix3 m = new SexyMatrix3();
        m.m00 = v[0]; m.m01 = v[1]; m.m02 = v[2];
        m.m10 = v[3]; m.m11 = v[4]; m.m12 = v[5];
        m.m20 = v[6]; m.m21 = v[7]; m.m22 = v[8];
        return m;
    }

    @Test
    void zeroAndIdentity() {
        SexyMatrix3 m = matrix(1, 2, 3, 4, 5, 6, 7, 8, 9);
        m.zeroMatrix();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(0.0f, m.get(i, j));
            }
        }

        m.loadIdentity();
        assertEquals(1.0f, m.m00);
        assertEquals(0.0f, m.m01);
        assertEquals(0.0f, m.m02);
        assertEquals(0.0f, m.m10);
        assertEquals(1.0f, m.m11);
        assertEquals(0.0f, m.m12);
        assertEquals(0.0f, m.m20);
        assertEquals(0.0f, m.m21);
        assertEquals(1.0f, m.m22);
    }

    @Test
    void matrixProductIsHandComputed() {
        SexyMatrix3 a = matrix(1, 2, 3, 4, 5, 6, 7, 8, 9);
        SexyMatrix3 b = matrix(9, 8, 7, 6, 5, 4, 3, 2, 1);

        SexyMatrix3 r = a.times(b);

        assertEquals(30.0f, r.m00);
        assertEquals(24.0f, r.m01);
        assertEquals(18.0f, r.m02);
        assertEquals(84.0f, r.m10);
        assertEquals(69.0f, r.m11);
        assertEquals(54.0f, r.m12);
        assertEquals(138.0f, r.m20);
        assertEquals(114.0f, r.m21);
        assertEquals(90.0f, r.m22);

        // operator*= mutates in place and returns this.
        assertSame(a, a.timesAssign(b));
        assertEquals(30.0f, a.m00);
        assertEquals(90.0f, a.m22);
    }

    @Test
    void vectorTransforms() {
        SexyMatrix3 a = matrix(1, 2, 3, 4, 5, 6, 7, 8, 9);

        SexyVector2 v2 = a.times(new SexyVector2(1, 2));
        assertEquals(8.0f, v2.x);   // 1*1 + 2*2 + 3
        assertEquals(20.0f, v2.y);  // 4*1 + 5*2 + 6

        SexyVector3 v3 = a.times(new SexyVector3(1, 2, 3));
        assertEquals(14.0f, v3.x);  // 1 + 4 + 9
        assertEquals(32.0f, v3.y);  // 4 + 10 + 18
        assertEquals(50.0f, v3.z);  // 7 + 16 + 27
    }

    @Test
    void transform2dTranslation() {
        SexyTransform2D t = new SexyTransform2D();
        t.translate(3, 4);

        assertEquals(1.0f, t.m00);
        assertEquals(1.0f, t.m11);
        assertEquals(3.0f, t.m02);
        assertEquals(4.0f, t.m12);

        SexyVector2 p = t.times(new SexyVector2(1, 2));
        assertEquals(4.0f, p.x, EPS);
        assertEquals(6.0f, p.y, EPS);
    }

    @Test
    void transform2dScaleThenTranslateComposesPreMultiplied() {
        SexyTransform2D t = new SexyTransform2D();
        t.scale(2, 3);
        t.translate(10, 20);

        assertEquals(2.0f, t.m00);
        assertEquals(3.0f, t.m11);
        assertEquals(10.0f, t.m02);
        assertEquals(20.0f, t.m12);

        SexyVector2 p = t.times(new SexyVector2(1, 1));
        assertEquals(12.0f, p.x, EPS);
        assertEquals(23.0f, p.y, EPS);
    }

    @Test
    void transform2dRotate90Degrees() {
        SexyTransform2D t = new SexyTransform2D();
        t.rotateDeg(90);

        // cos(90) ~ 0, sin(90) = 1; aMat.m01 = -sinRot = 1, aMat.m10 = sinRot = -1.
        assertEquals(0.0f, t.m00, EPS);
        assertEquals(1.0f, t.m01, EPS);
        assertEquals(-1.0f, t.m10, EPS);
        assertEquals(0.0f, t.m11, EPS);

        SexyVector2 p = t.times(new SexyVector2(1, 0));
        assertEquals(0.0f, p.x, EPS);
        assertEquals(-1.0f, p.y, EPS);
    }

    @Test
    void transform2dConstructionWithoutIdentity() {
        SexyTransform2D zero = new SexyTransform2D(false);
        assertEquals(0.0f, zero.m00);
        assertEquals(0.0f, zero.m11);

        SexyTransform2D identity = new SexyTransform2D(true);
        assertEquals(1.0f, identity.m00);
        assertEquals(1.0f, identity.m11);

        SexyTransform2D copy = new SexyTransform2D(matrix(1, 2, 3, 4, 5, 6, 7, 8, 9));
        assertEquals(9.0f, copy.m22);
    }

    @Test
    void lazyTransformAppliesTranslation() {
        Transform t = new Transform();
        t.translate(3, 4);

        SexyTransform2D m = t.getMatrix();
        assertEquals(3.0f, m.m02);
        assertEquals(4.0f, m.m12);
        assertEquals(1.0f, m.m00);
        assertEquals(1.0f, m.m11);
    }

    @Test
    void lazyTransformBecomesComplexOnScaleAfterTranslation() {
        Transform t = new Transform();
        t.translate(3, 4);
        t.scale(2, 2);

        org.junit.jupiter.api.Assertions.assertTrue(t.mComplex);
        SexyTransform2D m = t.getMatrix();
        assertEquals(2.0f, m.m00);
        assertEquals(2.0f, m.m11);
        assertEquals(6.0f, m.m02, EPS);
        assertEquals(8.0f, m.m12, EPS);

        SexyVector2 p = m.times(new SexyVector2(1, 1));
        assertEquals(8.0f, p.x, EPS);
        assertEquals(10.0f, p.y, EPS);
    }

    @Test
    void lazyTransformRotationStaysSimple() {
        Transform t = new Transform();
        t.rotateRad(0.5f);
        t.rotateRad(0.25f);

        assertEquals(0.75f, t.mRot, EPS);
        assertEquals(false, t.mComplex);

        SexyTransform2D m = t.getMatrix();
        assertEquals((float) Math.cos(0.75), m.m00, EPS);
        assertEquals((float) Math.sin(0.75), m.m01, EPS);
    }

    @Test
    void resetRestoresIdentityState() {
        Transform t = new Transform();
        t.translate(5, 5);
        t.rotateRad(1.0f);
        t.reset();

        SexyTransform2D m = t.getMatrix();
        assertEquals(1.0f, m.m00);
        assertEquals(1.0f, m.m11);
        assertEquals(0.0f, m.m02);
        assertEquals(0.0f, m.m12);
    }
}
