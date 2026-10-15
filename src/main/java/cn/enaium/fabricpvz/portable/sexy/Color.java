package cn.enaium.fabricpvz.portable.sexy;

import java.nio.ByteOrder;

/**
 * Ported from src/SexyAppFramework/graphics/Color.h:33 and src/SexyAppFramework/graphics/Color.cpp:26.
 *
 * <p>Faithful port of {@code Sexy::Color}. The public fields keep their upstream names and the
 * constructors preserve every upstream quirk, notably that the packed {@code Color(int)} form
 * promotes a zero alpha channel to 0xff.
 *
 * <p>PORT-NOTE: upstream's {@code int32_t&amp; operator[](int32_t)} returns a mutable reference
 * (with a shared static fallback for out-of-range indices); Java cannot return a reference, so
 * {@link #get(int)} / {@link #set(int, int)} reproduce the read/write behaviour instead.
 */
public class Color {
    public int mRed;
    public int mGreen;
    public int mBlue;
    public int mAlpha;

    public static Color Black = new Color(0, 0, 0);
    public static Color White = new Color(255, 255, 255);

    public Color() {
        mRed = 0;
        mGreen = 0;
        mBlue = 0;
        mAlpha = 255;
    }

    public Color(int theColor) {
        mRed = (theColor >> 16) & 0xFF;
        mGreen = (theColor >> 8) & 0xFF;
        mBlue = theColor & 0xFF;
        mAlpha = (theColor >> 24) & 0xFF;
        if (mAlpha == 0) {
            mAlpha = 0xff;
        }
    }

    public Color(int theColor, int theAlpha) {
        mRed = (theColor >> 16) & 0xFF;
        mGreen = (theColor >> 8) & 0xFF;
        mBlue = theColor & 0xFF;
        mAlpha = theAlpha;
    }

    public Color(int theRed, int theGreen, int theBlue) {
        mRed = theRed;
        mGreen = theGreen;
        mBlue = theBlue;
        mAlpha = 0xFF;
    }

    public Color(int theRed, int theGreen, int theBlue, int theAlpha) {
        mRed = theRed;
        mGreen = theGreen;
        mBlue = theBlue;
        mAlpha = theAlpha;
    }

    public Color(byte[] theElements) {
        // PORT-NOTE: upstream reads uchar elements; Java bytes are signed, so mask to 0..255.
        mRed = theElements[0] & 0xFF;
        mGreen = theElements[1] & 0xFF;
        mBlue = theElements[2] & 0xFF;
        mAlpha = 0xFF;
    }

    public Color(int[] theElements) {
        mRed = theElements[0];
        mGreen = theElements[1];
        mBlue = theElements[2];
        mAlpha = 0xFF;
    }

    public int getRed() {
        return mRed;
    }

    public int getGreen() {
        return mGreen;
    }

    public int getBlue() {
        return mBlue;
    }

    public int getAlpha() {
        return mAlpha;
    }

    // ToInt
    public int toInt() {
        return (mAlpha << 24)
                | (mRed << 16)
                | (mGreen << 8)
                | mBlue;
    }

    // ToGLColor
    public int toGLColor() {
        int aGLColor = (mAlpha << 24)
                | (mBlue << 16)
                | (mGreen << 8)
                | mRed;
        return toLE32(aGLColor);
    }

    // PORT-NOTE: ToLE32 is an identity on little-endian hosts and byte-swaps on big-endian ones.
    private static int toLE32(int v) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return v;
        }
        return Integer.reverseBytes(v);
    }

    // int32_t& operator[](int32_t)
    public int get(int theIdx) {
        switch (theIdx) {
            case 0: return mRed;
            case 1: return mGreen;
            case 2: return mBlue;
            case 3: return mAlpha;
            default: return 0;
        }
    }

    // int32_t& operator[](int32_t)
    public void set(int theIdx, int theValue) {
        switch (theIdx) {
            case 0: mRed = theValue; break;
            case 1: mGreen = theValue; break;
            case 2: mBlue = theValue; break;
            case 3: mAlpha = theValue; break;
            default: break; // upstream writes to a discarded static junk int
        }
    }

    // operator== (default)
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Color theColor)) {
            return false;
        }
        return mRed == theColor.mRed
                && mGreen == theColor.mGreen
                && mBlue == theColor.mBlue
                && mAlpha == theColor.mAlpha;
    }

    @Override
    public int hashCode() {
        int result = mRed;
        result = 31 * result + mGreen;
        result = 31 * result + mBlue;
        result = 31 * result + mAlpha;
        return result;
    }

    @Override
    public String toString() {
        return "Color(" + mRed + ", " + mGreen + ", " + mBlue + ", " + mAlpha + ")";
    }
}
