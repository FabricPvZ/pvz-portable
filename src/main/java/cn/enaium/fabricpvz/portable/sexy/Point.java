package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Point.h:33 (template TPoint&lt;int&gt;, typedef line 74).
 *
 * <p>Faithful port of {@code Sexy::TPoint<int>}. The public fields keep their upstream names.
 */
public class Point {
    public int mX;
    public int mY;

    public Point() {
        mX = 0;
        mY = 0;
    }

    public Point(int theX, int theY) {
        mX = theX;
        mY = theY;
    }

    // operator+ (TPoint)
    public Point plus(Point p) {
        return new Point(mX + p.mX, mY + p.mY);
    }

    // operator- (TPoint)
    public Point minus(Point p) {
        return new Point(mX - p.mX, mY - p.mY);
    }

    // operator* (TPoint)
    public Point times(Point p) {
        return new Point(mX * p.mX, mY * p.mY);
    }

    // operator/ (TPoint)
    // PORT-NOTE: C++ integer division by zero is undefined; Java throws ArithmeticException.
    public Point divide(Point p) {
        return new Point(mX / p.mX, mY / p.mY);
    }

    // operator* (_T s)
    public Point times(int s) {
        return new Point(mX * s, mY * s);
    }

    // operator/ (_T s)
    // PORT-NOTE: C++ integer division by zero is undefined; Java throws ArithmeticException.
    public Point divide(int s) {
        return new Point(mX / s, mY / s);
    }

    // operator+=
    public Point addAssign(Point p) {
        mX += p.mX;
        mY += p.mY;
        return this;
    }

    // operator-=
    public Point subtractAssign(Point p) {
        mX -= p.mX;
        mY -= p.mY;
        return this;
    }

    // operator*=
    public Point multiplyAssign(Point p) {
        mX *= p.mX;
        mY *= p.mY;
        return this;
    }

    // operator/=
    // PORT-NOTE: C++ integer division by zero is undefined; Java throws ArithmeticException.
    public Point divideAssign(Point p) {
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
        if (!(o instanceof Point p)) {
            return false;
        }
        return mX == p.mX && mY == p.mY;
    }

    @Override
    public int hashCode() {
        return 31 * mX + mY;
    }

    @Override
    public String toString() {
        return "Point(" + mX + ", " + mY + ")";
    }
}
