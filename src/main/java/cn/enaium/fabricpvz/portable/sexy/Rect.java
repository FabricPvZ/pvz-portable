package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Rect.h:36 (template TRect&lt;int32_t&gt;, typedef line 128).
 *
 * <p>Faithful port of {@code Sexy::TRect<int32_t>}: an axis-aligned integer rectangle.
 * The public fields keep their upstream names.
 */
public class Rect {
    public int mX;
    public int mY;
    public int mWidth;
    public int mHeight;

    public Rect(int theX, int theY, int theWidth, int theHeight) {
        mX = theX;
        mY = theY;
        mWidth = theWidth;
        mHeight = theHeight;
    }

    public Rect() {
        mX = 0;
        mY = 0;
        mWidth = 0;
        mHeight = 0;
    }

    // Intersects
    public boolean intersects(Rect theRect) {
        return !((theRect.mX + theRect.mWidth <= mX)
                || (theRect.mY + theRect.mHeight <= mY)
                || (theRect.mX >= mX + mWidth)
                || (theRect.mY >= mY + mHeight));
    }

    // Intersection
    public Rect intersection(Rect theRect) {
        int x1 = Math.max(mX, theRect.mX);
        int x2 = Math.min(mX + mWidth, theRect.mX + theRect.mWidth);
        int y1 = Math.max(mY, theRect.mY);
        int y2 = Math.min(mY + mHeight, theRect.mY + theRect.mHeight);
        if (((x2 - x1) < 0) || ((y2 - y1) < 0)) {
            return new Rect(0, 0, 0, 0);
        } else {
            return new Rect(x1, y1, x2 - x1, y2 - y1);
        }
    }

    // Union
    public Rect union(Rect theRect) {
        int x1 = Math.min(mX, theRect.mX);
        int x2 = Math.max(mX + mWidth, theRect.mX + theRect.mWidth);
        int y1 = Math.min(mY, theRect.mY);
        int y2 = Math.max(mY + mHeight, theRect.mY + theRect.mHeight);
        return new Rect(x1, y1, x2 - x1, y2 - y1);
    }

    // Contains(int, int)
    public boolean contains(int theX, int theY) {
        return ((theX >= mX) && (theX < mX + mWidth)
                && (theY >= mY) && (theY < mY + mHeight));
    }

    // Contains(TPoint<int>)
    public boolean contains(Point thePoint) {
        return ((thePoint.mX >= mX) && (thePoint.mX < mX + mWidth)
                && (thePoint.mY >= mY) && (thePoint.mY < mY + mHeight));
    }

    // Offset(int, int)
    public void offset(int theX, int theY) {
        mX += theX;
        mY += theY;
    }

    // Offset(TPoint<int>)
    public void offset(Point thePoint) {
        mX += thePoint.mX;
        mY += thePoint.mY;
    }

    // Inflate; upstream returns *this by value, so a copy of the mutated rectangle is returned.
    public Rect inflate(int theX, int theY) {
        mX -= theX;
        mWidth += theX * 2;
        mY -= theY;
        mHeight += theY * 2;

        return new Rect(mX, mY, mWidth, mHeight);
    }

    // operator== (default)
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rect r)) {
            return false;
        }
        return mX == r.mX && mY == r.mY && mWidth == r.mWidth && mHeight == r.mHeight;
    }

    @Override
    public int hashCode() {
        int result = mX;
        result = 31 * result + mY;
        result = 31 * result + mWidth;
        result = 31 * result + mHeight;
        return result;
    }

    @Override
    public String toString() {
        return "Rect(" + mX + ", " + mY + ", " + mWidth + ", " + mHeight + ")";
    }
}
