package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Point.h:33 (template TPoint&lt;double&gt;, typedef line 75).
 *
 * <p>Faithful port of {@code Sexy::TPoint<double>}. The public fields keep their upstream names.
 */
public class FPoint {
    public double mX;
    public double mY;

    public FPoint() {
        mX = 0;
        mY = 0;
    }

    public FPoint(double theX, double theY) {
        mX = theX;
        mY = theY;
    }

    // operator+ (TPoint)
    public FPoint plus(FPoint p) {
        return new FPoint(mX + p.mX, mY + p.mY);
    }

    // operator- (TPoint)
    public FPoint minus(FPoint p) {
        return new FPoint(mX - p.mX, mY - p.mY);
    }

    // operator* (TPoint)
    public FPoint times(FPoint p) {
        return new FPoint(mX * p.mX, mY * p.mY);
    }

    // operator/ (TPoint)
    public FPoint divide(FPoint p) {
        return new FPoint(mX / p.mX, mY / p.mY);
    }

    // operator* (_T s)
    public FPoint times(double s) {
        return new FPoint(mX * s, mY * s);
    }

    // operator/ (_T s)
    public FPoint divide(double s) {
        return new FPoint(mX / s, mY / s);
    }

    // operator+=
    public FPoint addAssign(FPoint p) {
        mX += p.mX;
        mY += p.mY;
        return this;
    }

    // operator-=
    public FPoint subtractAssign(FPoint p) {
        mX -= p.mX;
        mY -= p.mY;
        return this;
    }

    // operator*=
    public FPoint multiplyAssign(FPoint p) {
        mX *= p.mX;
        mY *= p.mY;
        return this;
    }

    // operator/=
    public FPoint divideAssign(FPoint p) {
        mX /= p.mX;
        mY /= p.mY;
        return this;
    }

    // operator== (default)
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FPoint p)) {
            return false;
        }
        return Double.compare(mX, p.mX) == 0 && Double.compare(mY, p.mY) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(mX) + Double.hashCode(mY);
    }

    @Override
    public String toString() {
        return "FPoint(" + mX + ", " + mY + ")";
    }
}
