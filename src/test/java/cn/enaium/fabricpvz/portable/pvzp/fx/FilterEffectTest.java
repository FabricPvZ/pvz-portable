package cn.enaium.fabricpvz.portable.pvzp.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Pixel-level tests for the filter effects, ported from FilterEffect.cpp:106-192.
 */
class FilterEffectTest {

    private static MemoryImage imageWith(int... argb) {
        new SexyAppBase(); // MemoryImage() resolves SexyAppBase.current
        MemoryImage img = new MemoryImage();
        img.create(argb.length, 1);
        int[] bits = img.getBits();
        System.arraycopy(argb, 0, bits, 0, argb.length);
        img.bitsChanged();
        return img;
    }

    @Test
    void whiteForcesRgbAndPreservesAlpha() {
        MemoryImage img = imageWith(0x80112233, 0x00AABBCC);
        FilterEffect.filterEffectDoWhite(img);
        assertEquals(0x80FFFFFF, img.getBits()[0]);
        assertEquals(0x00FFFFFF, img.getBits()[1]);
    }

    @Test
    void washedOutRaisesLuminanceOfNeutralGrey() {
        // r=g=b=128 => saturation 0, so only the *1.8 luminance applies: 128/255*1.8*255 = 230.4 -> 230.
        MemoryImage img = imageWith(0xFF808080);
        FilterEffect.filterEffectDoWashedOut(img);
        int px = img.getBits()[0];
        assertEquals(0xFF, (px >>> 24) & 0xFF, "alpha untouched");
        int r = (px >> 16) & 0xFF;
        assertEquals(230, r);
        assertEquals(r, (px >> 8) & 0xFF);
        assertEquals(r, px & 0xFF);
    }

    @Test
    void lessWashedOutUsesItsOwnLuminanceFactor() {
        // 128/255*1.2*255 = 153.6 -> 153.
        MemoryImage img = imageWith(0xFF808080);
        FilterEffect.filterEffectDoLessWashedOut(img);
        assertEquals(153, (img.getBits()[0] >> 16) & 0xFF);
    }

    @Test
    void lumSatIsNearIdentityForConstantsOne() {
        MemoryImage img = imageWith(0xFF3366CC);
        int before = img.getBits()[0];
        FilterEffect.filterEffectDoLumSat(img, 1.0f, 1.0f);
        int after = img.getBits()[0];
        assertEquals(0xFF, (after >>> 24) & 0xFF, "alpha untouched");
        for (int shift : new int[] { 16, 8, 0 }) {
            assertEquals((before >> shift) & 0xFF, (after >> shift) & 0xFF, 1,
                    "lum=1, sat=1 must round-trip the channel to within one step");
        }
    }

    @Test
    void getImageMemoisesPerSourceAndDisposeClears() {
        MemoryImage src = imageWith(0xFF404040);
        Image a = FilterEffect.filterEffectGetImage(src, FilterEffect.FILTER_EFFECT_WHITE);
        Image b = FilterEffect.filterEffectGetImage(src, FilterEffect.FILTER_EFFECT_WHITE);
        assertSame(a, b, "the filtered image is memoised per source");

        FilterEffect.filterEffectDisposeForApp();
        Image c = FilterEffect.filterEffectGetImage(src, FilterEffect.FILTER_EFFECT_WHITE);
        assertNotSame(a, c, "disposing the cache forces a rebuild");
    }
}
