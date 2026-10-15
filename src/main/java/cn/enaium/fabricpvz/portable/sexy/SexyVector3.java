package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/SexyVector.h:70.
 *
 * <p>Faithful port of {@code Sexy::SexyVector3}. The public fields keep their upstream names.
 */
public class SexyVector3 {
    public float x;
    public float y;
    public float z;

    public SexyVector3() {
        x = 0;
        y = 0;
        z = 0;
    }

    public SexyVector3(float theX, float theY, float theZ) {
        x = theX;
        y = theY;
        z = theZ;
    }

    // Dot
    public float dot(SexyVector3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    // Cross
    public SexyVector3 cross(SexyVector3 v) {
        return new SexyVector3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
    }

    // operator+
    public SexyVector3 plus(SexyVector3 v) {
        return new SexyVector3(x + v.x, y + v.y, z + v.z);
    }

    // operator-
    public SexyVector3 minus(SexyVector3 v) {
        return new SexyVector3(x - v.x, y - v.y, z - v.z);
    }

    // operator*(float)
    public SexyVector3 times(float t) {
        return new SexyVector3(t * x, t * y, t * z);
    }

    // operator/(float)
    public SexyVector3 divide(float t) {
        return new SexyVector3(x / t, y / t, z / t);
    }

    // operator== (default)
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SexyVector3 v)) {
            return false;
        }
        return Float.compare(x, v.x) == 0 && Float.compare(y, v.y) == 0 && Float.compare(z, v.z) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        result = 31 * result + Float.hashCode(z);
        return result;
    }

    // Magnitude
    public float magnitude() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    // Normalize
    public SexyVector3 normalize() {
        float aMag = magnitude();
        return aMag != 0 ? this.divide(aMag) : this;
    }

    @Override
    public String toString() {
        return "SexyVector3(" + x + ", " + y + ", " + z + ")";
    }
}
