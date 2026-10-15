package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/SexyVector.h:33.
 *
 * <p>Faithful port of {@code Sexy::SexyVector2}. The public fields keep their upstream names.
 */
public class SexyVector2 {
    public float x;
    public float y;

    public SexyVector2() {
        x = 0;
        y = 0;
    }

    public SexyVector2(float theX, float theY) {
        x = theX;
        y = theY;
    }

    // Dot
    public float dot(SexyVector2 v) {
        return x * v.x + y * v.y;
    }

    // operator+
    public SexyVector2 plus(SexyVector2 v) {
        return new SexyVector2(x + v.x, y + v.y);
    }

    // operator-
    public SexyVector2 minus(SexyVector2 v) {
        return new SexyVector2(x - v.x, y - v.y);
    }

    // unary operator-
    public SexyVector2 negate() {
        return new SexyVector2(-x, -y);
    }

    // operator*(float)
    public SexyVector2 times(float t) {
        return new SexyVector2(t * x, t * y);
    }

    // operator/(float)
    public SexyVector2 divide(float t) {
        return new SexyVector2(x / t, y / t);
    }

    // operator+=
    public void addAssign(SexyVector2 v) {
        x += v.x;
        y += v.y;
    }

    // operator-=
    public void subtractAssign(SexyVector2 v) {
        x -= v.x;
        y -= v.y;
    }

    // operator*=
    public void multiplyAssign(float t) {
        x *= t;
        y *= t;
    }

    // operator/=
    public void divideAssign(float t) {
        x /= t;
        y /= t;
    }

    // operator== (default)
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SexyVector2 v)) {
            return false;
        }
        return Float.compare(x, v.x) == 0 && Float.compare(y, v.y) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Float.hashCode(x) + Float.hashCode(y);
    }

    // Magnitude
    public float magnitude() {
        return (float) Math.sqrt(x * x + y * y);
    }

    // MagnitudeSquared
    public float magnitudeSquared() {
        return x * x + y * y;
    }

    // Normalize
    public SexyVector2 normalize() {
        float aMag = magnitude();
        return aMag != 0 ? this.divide(aMag) : this;
    }

    // Perp
    public SexyVector2 perp() {
        return new SexyVector2(-y, x);
    }

    @Override
    public String toString() {
        return "SexyVector2(" + x + ", " + y + ")";
    }
}
