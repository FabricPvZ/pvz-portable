package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Rect.h:36 (template TRect&lt;double&gt;, typedef line 129).
 *
 * <p>Faithful port of {@code Sexy::TRect<double>}: an axis-aligned double rectangle.
 * The public fields keep their upstream names.
 */
public class FRect {
    public double mX;
    public double mY;
    public double mWidth;
    public double mHeight;

    public FRect(double theX, double theY, double theWidth, double theHeight) {
        mX = theX;
        mY = theY;
        mWidth = theWidth;
        mHeight = theHeight;
    }

    public FRect() {
        mX = 0;
        mY = 0;
        mWidth = 0;
        mHeight = 0;
    }

    // Intersects
    public boolean intersects(FRect theRect) {
        return !((theRect.mX + theRect.mWidth <= mX)
                || (theRect.mY + theRect.mHeight <= mY)
                || (theRect.mX >= mX + mWidth)
                || (theRect.mY >= mY + mHeight));
    }

    // Intersection
    public FRect intersection(FRect theRect) {
        double x1 = Math.max(mX, theRect.mX);
        double x2 = Math.min(mX + mWidth, theRect.mX + theRect.mWidth);
        double y1 = Math.max(mY, theRect.mY);
        double y2 = Math.min(mY + mHeight, theRect.mY + theRect.mHeight);
        if (((x2 - x1) < 0) || ((y2 - y1) < 0)) {
            return new FRect(0, 0, 0, 0);
        } else {
            return new FRect(x1, y1, x2 - x1, y2 - y1);
        }
    }

    // Union
    public FRect union(FRect theRect) {
        double x1 = Math.min(mX, theRect.mX);
        double x2 = Math.max(mX + mWidth, theRect.mX + theRect.mWidth);
        double y1 = Math.min(mY, theRect.mY);
        double y2 = Math.max(mY + mHeight, theRect.mY + theRect.mHeight);
        return new FRect(x1, y1, x2 - x1, y2 - y1);
    }

    // Contains(double, double)
    public boolean contains(double theX, double theY) {
        return ((theX >= mX) && (theX < mX + mWidth)
                && (theY >= mY) && (theY < mY + mHeight));
    }

    // Contains(TPoint<double>)
    public boolean contains(FPoint thePoint) {
        return ((thePoint.mX >= mX) && (thePoint.mX < mX + mWidth)
                && (thePoint.mY >= mY) && (thePoint.mY < mY + mHeight));
    }

    // Offset(double, double)
    public void offset(double theX, double theY) {
        mX += theX;
        mY += theY;
    }

    // Offset(TPoint<double>)
    public void offset(FPoint thePoint) {
        mX += thePoint.mX;
        mY += thePoint.mY;
    }

    // Inflate; upstream returns *this by value, so a copy of the mutated rectangle is returned.
    public FRect inflate(double theX, double theY) {
        mX -= theX;
        mWidth += theX * 2;
        mY -= theY;
        mHeight += theY * 2;

        return new FRect(mX, mY, mWidth, mHeight);
    }

    // operator== (default)
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FRect r)) {
            return false;
        }
        return Double.compare(mX, r.mX) == 0
                && Double.compare(mY, r.mY) == 0
                && Double.compare(mWidth, r.mWidth) == 0
                && Double.compare(mHeight, r.mHeight) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(mX);
        result = 31 * result + Double.hashCode(mY);
        result = 31 * result + Double.hashCode(mWidth);
        result = 31 * result + Double.hashCode(mHeight);
        return result;
    }

    @Override
    public String toString() {
        return "FRect(" + mX + ", " + mY + ", " + mWidth + ", " + mHeight + ")";
    }
}
