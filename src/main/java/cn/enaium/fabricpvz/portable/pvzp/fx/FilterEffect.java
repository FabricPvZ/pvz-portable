package cn.enaium.fabricpvz.portable.pvzp.fx;

import java.util.IdentityHashMap;
import java.util.Map;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Ported from src/PvzpLib/FilterEffect.h:24 (enum FilterEffect) and
 * src/PvzpLib/FilterEffect.cpp.
 *
 * <p>Luminance/saturation ("washed out") and white-flash recolours of an image, memoised per source
 * image in one identity map per effect (upstream {@code std::map<Image*, unique_ptr<Image>>}). The
 * alpha-edge sanding step reuses {@link PvzpCommon#fixPixelsOnAlphaEdgeForBlending}.
 */
public final class FilterEffect {

    public static final int FILTER_EFFECT_NONE = -1;
    public static final int FILTER_EFFECT_WASHED_OUT = 0;
    public static final int FILTER_EFFECT_LESS_WASHED_OUT = 1;
    public static final int FILTER_EFFECT_WHITE = 2;
    public static final int NUM_FILTER_EFFECTS = 3;

    @SuppressWarnings("unchecked")
    private static final Map<Image, Image>[] gFilterMap = new Map[NUM_FILTER_EFFECTS];

    static {
        for (int i = 0; i < NUM_FILTER_EFFECTS; i++) {
            gFilterMap[i] = new IdentityHashMap<>();
        }
    }

    private FilterEffect() {
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:92 (empty upstream body). */
    public static void filterEffectInitForApp() {
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:96. */
    public static void filterEffectDisposeForApp() {
        for (int i = 0; i < NUM_FILTER_EFFECTS; i++) {
            gFilterMap[i].clear();
        }
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:29 ({@code RGB_to_HSL}). */
    private static void rgbToHsl(float r, float g, float b, float[] hsl) {
        float maxval = Math.max(r, g);
        maxval = Math.max(maxval, b);
        float minval = Math.min(r, g);
        minval = Math.min(minval, b);

        float l = (minval + maxval) / 2;
        float h = 0.0f;
        float s = 0.0f;
        if (l <= 0.0f) {
            hsl[0] = h;
            hsl[1] = s;
            hsl[2] = l;
            return;
        }

        float delta = maxval - minval;
        s = delta;
        if (s <= 0.0f) {
            hsl[0] = h;
            hsl[1] = s;
            hsl[2] = l;
            return;
        }
        s /= ((l <= 0.5f) ? (minval + maxval) : (2.0f - minval - maxval));

        float r2 = (maxval - r) / delta;
        float g2 = (maxval - g) / delta;
        float b2 = (maxval - b) / delta;
        if (maxval == r) {
            h = ((g == minval) ? (5.0f + b2) : (1.0f - g2));
        } else if (maxval == g) {
            h = ((b == minval) ? (1.0f + r2) : (3.0f - b2));
        } else {
            h = ((r == minval) ? (3.0f + g2) : (5.0f - r2));
        }
        h /= 6.0f;

        hsl[0] = h;
        hsl[1] = s;
        hsl[2] = l;
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:60 ({@code HSL_to_RGB}). */
    private static void hslToRgb(float h, float sl, float l, float[] rgb) {
        float v = (l <= 0.5f) ? (l * (1.0f + sl)) : (l + sl - l * sl);
        if (v <= 0.0f) {
            rgb[0] = 0.0f;
            rgb[1] = 0.0f;
            rgb[2] = 0.0f;
            return;
        }

        float y = 2 * l - v;
        float sv = (v - y) / v;
        h *= 6.0f;
        int sextant = clamp((int) h, 0, 5);
        float vsf = v * sv * (h - sextant);
        float x = y + vsf;
        float z = v - vsf;

        float r;
        float g;
        float b;
        switch (sextant) {
            case 0: r = v; g = x; b = y; break;
            case 1: r = z; g = v; b = y; break;
            case 2: r = y; g = v; b = x; break;
            case 3: r = y; g = z; b = v; break;
            case 4: r = x; g = y; b = v; break;
            default: r = v; g = y; b = z; break;
        }
        rgb[0] = r;
        rgb[1] = g;
        rgb[2] = b;
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:106. */
    public static void filterEffectDoLumSat(MemoryImage theImage, float theLum, float theSat) {
        int[] ptr = theImage.mBits;
        float[] hsl = new float[3];
        float[] rgb = new float[3];
        for (int y = 0; y < theImage.mHeight; y++) {
            for (int x = 0; x < theImage.mWidth; x++) {
                int aPixel = ptr[y * theImage.mWidth + x];
                float b = (aPixel & 255) / 255.0f;
                float g = ((aPixel >> 8) & 255) / 255.0f;
                float r = ((aPixel >> 16) & 255) / 255.0f;
                int a = (aPixel >> 24) & 255;

                rgbToHsl(r, g, b, hsl);
                hsl[1] *= theSat;
                hsl[2] *= theLum;
                hslToRgb(hsl[0], hsl[1], hsl[2], rgb);

                int aR = clamp((int) (rgb[0] * 255), 0, 255);
                int aG = clamp((int) (rgb[1] * 255), 0, 255);
                int aB = clamp((int) (rgb[2] * 255), 0, 255);
                ptr[y * theImage.mWidth + x] = (a << 24) | (aR << 16) | (aG << 8) | aB;
            }
        }
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:130. */
    public static void filterEffectDoWashedOut(MemoryImage theImage) {
        filterEffectDoLumSat(theImage, 1.8f, 0.2f);
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:135. */
    public static void filterEffectDoLessWashedOut(MemoryImage theImage) {
        filterEffectDoLumSat(theImage, 1.2f, 0.3f);
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:140. */
    public static void filterEffectDoWhite(MemoryImage theImage) {
        int[] ptr = theImage.mBits;
        for (int y = 0; y < theImage.mHeight; y++) {
            for (int x = 0; x < theImage.mWidth; x++) {
                ptr[y * theImage.mWidth + x] |= 0x00FFFFFF;
            }
        }
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:148. */
    public static MemoryImage filterEffectCreateImage(Image theImage, int theFilterEffect) {
        MemoryImage aImage = new MemoryImage();
        aImage.create(theImage.mWidth, theImage.mHeight);
        aImage.mHasTrans = true;
        aImage.mHasAlpha = true;

        Graphics aMemoryGraphics = new Graphics(aImage);
        aMemoryGraphics.drawImage(theImage, 0, 0);
        PvzpCommon.fixPixelsOnAlphaEdgeForBlending(aImage);

        switch (theFilterEffect) {
            case FILTER_EFFECT_WASHED_OUT: filterEffectDoWashedOut(aImage); break;
            case FILTER_EFFECT_LESS_WASHED_OUT: filterEffectDoLessWashedOut(aImage); break;
            case FILTER_EFFECT_WHITE: filterEffectDoWhite(aImage); break;
            default: break;
        }

        aImage.mBitsChangedCount++;
        aImage.mNumCols = theImage.mNumCols;
        aImage.mNumRows = theImage.mNumRows;
        return aImage;
    }

    /** Ported from src/PvzpLib/FilterEffect.cpp:180. */
    public static Image filterEffectGetImage(Image theImage, int theFilterEffect) {
        Map<Image, Image> aFilterMap = gFilterMap[theFilterEffect];
        Image aCached = aFilterMap.get(theImage);
        if (aCached != null) {
            return aCached;
        }

        MemoryImage aImage = filterEffectCreateImage(theImage, theFilterEffect);
        aFilterMap.put(theImage, aImage);
        return aImage;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
