package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Color}: ARGB packing/unpacking, the zero-alpha promotion in the packed
 * constructor, ordered channel access, and the GL-color byte order. The "multiply" surface
 * mentioned by the port spec does not exist upstream (Color.h exposes no multiplication),
 * so the alpha and channel operations that Color does define are covered instead.
 */
class ColorTest {

    @Test
    void packedConstructorUnpacksArgb() {
        Color c = new Color(0x12345678);
        // Color.h:46-50: mRed=(theColor>>16)&0xFF, mGreen=(theColor>>8)&0xFF, mBlue=theColor&0xFF,
        // mAlpha=(theColor>>24)&0xFF — the top byte is alpha, so red is 0x34, not 0x12.
        assertEquals(0x34, c.mRed);
        assertEquals(0x56, c.mGreen);
        assertEquals(0x78, c.mBlue);
        assertEquals(0x12, c.mAlpha);
    }

    @Test
    void packedConstructorPromotesZeroAlpha() {
        Color c = new Color(0x00123456);
        assertEquals(0x12, c.mRed);
        assertEquals(0x34, c.mGreen);
        assertEquals(0x56, c.mBlue);
        assertEquals(0xFF, c.mAlpha);
        assertEquals((int) 0xFF123456L, c.toInt());
    }

    @Test
    void packedConstructorWithExplicitAlpha() {
        Color c = new Color(0x12345678, 0x9A);
        // Color.h:55-59: the two-argument packed form shifts red/green/blue out of the colour word
        // exactly like Color(int) and only replaces the alpha.
        assertEquals(0x34, c.mRed);
        assertEquals(0x56, c.mGreen);
        assertEquals(0x78, c.mBlue);
        assertEquals(0x9A, c.mAlpha);
        assertEquals(0x00, new Color(0x123456, 0x00).mAlpha);
    }

    @Test
    void componentConstructors() {
        Color rgb = new Color(10, 20, 30);
        assertEquals(10, rgb.getRed());
        assertEquals(20, rgb.getGreen());
        assertEquals(30, rgb.getBlue());
        assertEquals(0xFF, rgb.getAlpha());

        Color rgba = new Color(10, 20, 30, 40);
        assertEquals(40, rgba.mAlpha);

        Color def = new Color();
        assertEquals(0, def.mRed);
        assertEquals(0, def.mGreen);
        assertEquals(0, def.mBlue);
        assertEquals(255, def.mAlpha);
    }

    @Test
    void elementArrayConstructorsMaskSignedBytes() {
        Color fromBytes = new Color(new byte[] {(byte) 0xFF, (byte) 0x80, (byte) 0x00});
        assertEquals(255, fromBytes.mRed);
        assertEquals(128, fromBytes.mGreen);
        assertEquals(0, fromBytes.mBlue);
        assertEquals(255, fromBytes.mAlpha);

        Color fromInts = new Color(new int[] {1, 2, 3});
        assertEquals(1, fromInts.mRed);
        assertEquals(2, fromInts.mGreen);
        assertEquals(3, fromInts.mBlue);
        assertEquals(255, fromInts.mAlpha);
    }

    @Test
    void toIntRoundTripsArgb() {
        assertEquals((int) 0xA1B2C3D4L, new Color(0xA1B2C3D4).toInt());
        // alpha<<24 | red<<16 | green<<8 | blue
        assertEquals((int) 0x0D0A0B0CL, new Color(0x0A, 0x0B, 0x0C, 0x0D).toInt());
    }

    @Test
    void toGLColorUsesAbgrByteOrder() {
        Color c = new Color(0x12, 0x34, 0x56, 0x78);
        int expected = 0x78563412;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            expected = Integer.reverseBytes(expected);
        }
        assertEquals(expected, c.toGLColor());
    }

    @Test
    void indexedAccess() {
        Color c = new Color(1, 2, 3, 4);
        assertEquals(1, c.get(0));
        assertEquals(2, c.get(1));
        assertEquals(3, c.get(2));
        assertEquals(4, c.get(3));
        assertEquals(0, c.get(4));

        c.set(0, 11);
        c.set(3, 44);
        assertEquals(11, c.mRed);
        assertEquals(44, c.mAlpha);

        c.set(9, 123);   // out of range write is discarded
        assertEquals(11, c.mRed);
        assertEquals(44, c.mAlpha);
    }

    @Test
    void valueSemanticsAndStatics() {
        assertEquals(new Color(1, 2, 3, 4), new Color(1, 2, 3, 4));
        assertNotEquals(new Color(1, 2, 3, 4), new Color(1, 2, 3, 5));
        assertEquals(new Color(1, 2, 3, 4).hashCode(), new Color(1, 2, 3, 4).hashCode());

        assertEquals(new Color(0, 0, 0), Color.Black);
        assertEquals(255, Color.Black.mAlpha);
        assertEquals(new Color(255, 255, 255), Color.White);
        assertEquals(255, Color.White.mAlpha);
    }
}
