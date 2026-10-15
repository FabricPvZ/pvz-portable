package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;

/**
 * Ported from src/SexyAppFramework/SexyAppBase.cpp:3583-4008 (image helpers) and :4102-4136 / :3994-4008 where applicable.
 *
 * <p>Package-private pixel plumbing behind the public {@code SexyAppBase} image-helpers
 * ({@code CreateCrossfadeImage}, {@code ColorizeImage}, {@code CreateColorizedImage},
 * {@code CopyImage}, {@code MirrorImage}, {@code FlipImage}, {@code RotateImageHue} and the
 * {@code HSLToRGB}/{@code RGBToHSL} converters). Every branch, magic constant, shift, clamp and
 * {@code mBitsChangedCount} bookkeeping mirrors upstream 1:1; only the C++/Java representation of
 * the bit buffers differs (see below).
 *
 * <p>Java deviations (forced by the frozen {@code MemoryImage}/{@code GLImage} API):
 * <ul>
 *   <li>{@code image->GetBits()} / {@code mBits.get()} becomes {@link MemoryImage#getBits()}; the
 *       buffer is a plain {@code int[]} of {@code w*h+1} words ({@code 0xAARRGGBB}) whose extra
 *       slot holds the {@code MEMORYCHECK_ID} canary, so only the first {@code w*h} words are ever
 *       written.</li>
 *   <li>{@code BitsChanged()} becomes {@link MemoryImage#bitsChanged()}.</li>
 *   <li>Upstream's palettized branches ({@code mColorTable}/{@code mColorIndices}) are dropped: the
 *       Java port has no CPU palette path (palettes are deliberately not ported; every data path is
 *       straight ARGB in {@code mBits}), so {@code ColorizeImage}/{@code CreateColorizedImage}
 *       always operate over {@code width*height} pixels of {@code getBits()} exactly as upstream
 *       does when {@code mColorTable == nullptr}.</li>
 *   <li>Upstream's {@code PrecacheAdditive}/{@code PrecacheAlpha}/{@code PrecacheNative}
 *       (SexyAppBase.cpp:3994-4008) are not ported: their only callees
 *       ({@code GetRLAdditiveData}/{@code GetRLAlphaData}/{@code GetNativeAlphaData}) exist solely to
 *       build the DirectDraw-era software blit tables, which the port does not have (the renderer
 *       backend uploads straight from the bits). They have zero callers repo-wide.</li>
 * </ul>
 */
final class SexyImageOps {

    private SexyImageOps() {
    }

    /** Renderer backend of {@code theApp}, or {@code null} (upstream passes {@code mGLInterface.get()}). */
    private static RendererBackend backend(SexyAppBase theApp) {
        return theApp == null ? null : theApp.mRendererBackend;
    }

    // SexyAppBase::CreateCrossfadeImage (SexyAppBase.cpp:3583)
    static GLImage createCrossfadeImage(SexyAppBase theApp, Image theImage1, Rect theRect1,
                                        Image theImage2, Rect theRect2, double theFadeFactor) {
        if (!(theImage1 instanceof MemoryImage)) {
            return null;
        }

        if (!(theImage2 instanceof MemoryImage)) {
            return null;
        }

        MemoryImage aMemoryImage1 = (MemoryImage) theImage1;
        MemoryImage aMemoryImage2 = (MemoryImage) theImage2;

        if ((theRect1.mX < 0) || (theRect1.mY < 0) ||
                (theRect1.mX + theRect1.mWidth > theImage1.getWidth()) ||
                (theRect1.mY + theRect1.mHeight > theImage1.getHeight())) {
            // DBG_ASSERTE("Crossfade Rect1 out of bounds");
            return null;
        }

        if ((theRect2.mX < 0) || (theRect2.mY < 0) ||
                (theRect2.mX + theRect2.mWidth > theImage2.getWidth()) ||
                (theRect2.mY + theRect2.mHeight > theImage2.getHeight())) {
            // DBG_ASSERTE("Crossfade Rect2 out of bounds");
            return null;
        }

        int aWidth = theRect1.mWidth;
        int aHeight = theRect1.mHeight;

        GLImage anImage = new GLImage(backend(theApp));
        anImage.create(aWidth, aHeight);
        // This image is written into through getBits(), so it must be allocated as a drawable
        // (render-target) texture rather than an immutable static one.
        anImage.mRenderTarget = true;

        int[] aDestBits = anImage.getBits();
        int[] aSrcBits1 = aMemoryImage1.getBits();
        int[] aSrcBits2 = aMemoryImage2.getBits();

        int aSrc1Width = aMemoryImage1.getWidth();
        int aSrc2Width = aMemoryImage2.getWidth();
        int aMult = (int) (theFadeFactor * 256);
        int aOMM = (256 - aMult);

        for (int y = 0; y < aHeight; y++) {
            int s1 = (y + theRect1.mY) * aSrc1Width + theRect1.mX;
            int s2 = (y + theRect2.mY) * aSrc2Width + theRect2.mX;
            int d = y * aWidth;

            for (int x = 0; x < aWidth; x++) {
                int p1 = aSrcBits1[s1++];
                int p2 = aSrcBits2[s2++];

                aDestBits[d++] =
                        ((((p1 & 0x000000FF) * aOMM + (p2 & 0x000000FF) * aMult) >>> 8) & 0x000000FF) |
                        ((((p1 & 0x0000FF00) * aOMM + (p2 & 0x0000FF00) * aMult) >>> 8) & 0x0000FF00) |
                        ((((p1 & 0x00FF0000) * aOMM + (p2 & 0x00FF0000) * aMult) >>> 8) & 0x00FF0000) |
                        ((((p1 >>> 24) * aOMM + (p2 >>> 24) * aMult) << 16) & 0xFF000000);
            }
        }

        anImage.bitsChanged();

        return anImage;
    }

    // SexyAppBase::ColorizeImage (SexyAppBase.cpp:3649)
    static void colorizeImage(Image theImage, Color theColor) {
        if (!(theImage instanceof MemoryImage)) {
            return;
        }

        MemoryImage aSrcMemoryImage = (MemoryImage) theImage;

        // Upstream branches on mColorTable; the port has no palette, so always use the ARGB bits.
        int[] aBits = aSrcMemoryImage.getBits();
        int aNumColors = theImage.getWidth() * theImage.getHeight();

        if ((theColor.mAlpha <= 255) && (theColor.mRed <= 255) &&
                (theColor.mGreen <= 255) && (theColor.mBlue <= 255)) {
            for (int i = 0; i < aNumColors; i++) {
                int aColor = aBits[i];

                aBits[i] =
                        ((((aColor & 0xFF000000) >>> 8) * theColor.mAlpha) & 0xFF000000) |
                        ((((aColor & 0x00FF0000) * theColor.mRed) >>> 8) & 0x00FF0000) |
                        ((((aColor & 0x0000FF00) * theColor.mGreen) >>> 8) & 0x0000FF00) |
                        ((((aColor & 0x000000FF) * theColor.mBlue) >>> 8) & 0x000000FF);
            }
        } else {
            for (int i = 0; i < aNumColors; i++) {
                int aColor = aBits[i];

                int aAlpha = ((aColor >>> 24) * theColor.mAlpha) / 255;
                int aRed = (((aColor >>> 16) & 0xFF) * theColor.mRed) / 255;
                int aGreen = (((aColor >>> 8) & 0xFF) * theColor.mGreen) / 255;
                int aBlue = ((aColor & 0xFF) * theColor.mBlue) / 255;

                aAlpha = Math.min(aAlpha, 255);
                aRed = Math.min(aRed, 255);
                aGreen = Math.min(aGreen, 255);
                aBlue = Math.min(aBlue, 255);

                aBits[i] = (aAlpha << 24) | (aRed << 16) | (aGreen << 8) | (aBlue);
            }
        }

        aSrcMemoryImage.bitsChanged();
    }

    // SexyAppBase::CreateColorizedImage (SexyAppBase.cpp:3707)
    static GLImage createColorizedImage(SexyAppBase theApp, Image theImage, Color theColor) {
        if (!(theImage instanceof MemoryImage)) {
            return null;
        }

        MemoryImage aSrcMemoryImage = (MemoryImage) theImage;

        GLImage anImage = new GLImage(backend(theApp));

        anImage.create(theImage.getWidth(), theImage.getHeight());
        // Drawable target: the colourised bits are written into this image.
        anImage.mRenderTarget = true;

        // Upstream branches on mColorTable; the port has no palette, so always use the ARGB bits.
        int[] aSrcBits = aSrcMemoryImage.getBits();
        int[] aDestBits = anImage.getBits();
        int aNumColors = theImage.getWidth() * theImage.getHeight();

        if ((theColor.mAlpha <= 255) && (theColor.mRed <= 255) &&
                (theColor.mGreen <= 255) && (theColor.mBlue <= 255)) {
            for (int i = 0; i < aNumColors; i++) {
                int aColor = aSrcBits[i];

                aDestBits[i] =
                        ((((aColor & 0xFF000000) >>> 8) * theColor.mAlpha) & 0xFF000000) |
                        ((((aColor & 0x00FF0000) * theColor.mRed) >>> 8) & 0x00FF0000) |
                        ((((aColor & 0x0000FF00) * theColor.mGreen) >>> 8) & 0x0000FF00) |
                        ((((aColor & 0x000000FF) * theColor.mBlue) >>> 8) & 0x000000FF);
            }
        } else {
            for (int i = 0; i < aNumColors; i++) {
                int aColor = aSrcBits[i];

                int aAlpha = ((aColor >>> 24) * theColor.mAlpha) / 255;
                int aRed = (((aColor >>> 16) & 0xFF) * theColor.mRed) / 255;
                int aGreen = (((aColor >>> 8) & 0xFF) * theColor.mGreen) / 255;
                int aBlue = ((aColor & 0xFF) * theColor.mBlue) / 255;

                aAlpha = Math.min(aAlpha, 255);
                aRed = Math.min(aRed, 255);
                aGreen = Math.min(aGreen, 255);
                aBlue = Math.min(aBlue, 255);

                aDestBits[i] = (aAlpha << 24) | (aRed << 16) | (aGreen << 8) | (aBlue);
            }
        }

        anImage.bitsChanged();

        return anImage;
    }

    // SexyAppBase::CopyImage (SexyAppBase.cpp:3778, the Rect overload)
    static GLImage copyImage(SexyAppBase theApp, Image theImage, Rect theRect) {
        GLImage anImage = new GLImage(backend(theApp));

        anImage.create(theRect.mWidth, theRect.mHeight);
        // Graphics draws into this image, so it must be a render target.
        anImage.mRenderTarget = true;

        Graphics g = new Graphics(anImage);
        g.drawImage(theImage, -theRect.mX, -theRect.mY);

        anImage.copyAttributes(theImage);

        return anImage;
    }

    // SexyAppBase::CopyImage (SexyAppBase.cpp:3792, the default overload)
    static GLImage copyImage(SexyAppBase theApp, Image theImage) {
        return copyImage(theApp, theImage, new Rect(0, 0, theImage.getWidth(), theImage.getHeight()));
    }

    // SexyAppBase::MirrorImage (SexyAppBase.cpp:3797)
    static void mirrorImage(Image theImage) {
        if (!(theImage instanceof MemoryImage)) {
            return;
        }

        MemoryImage aSrcMemoryImage = (MemoryImage) theImage;

        int[] aSrcBits = aSrcMemoryImage.getBits();

        int aPhysSrcWidth = aSrcMemoryImage.mWidth;
        for (int y = 0; y < aSrcMemoryImage.mHeight; y++) {
            int aLeftBits = y * aPhysSrcWidth;
            int aRightBits = aLeftBits + (aPhysSrcWidth - 1);

            for (int x = 0; x < (aPhysSrcWidth >> 1); x++) {
                int aSwap = aSrcBits[aLeftBits];

                aSrcBits[aLeftBits++] = aSrcBits[aRightBits];
                aSrcBits[aRightBits--] = aSwap;
            }
        }

        aSrcMemoryImage.bitsChanged();
    }

    // SexyAppBase::FlipImage (SexyAppBase.cpp:3821)
    static void flipImage(Image theImage) {
        if (!(theImage instanceof MemoryImage)) {
            return;
        }

        MemoryImage aSrcMemoryImage = (MemoryImage) theImage;

        int[] aSrcBits = aSrcMemoryImage.getBits();

        int aPhysSrcHeight = aSrcMemoryImage.mHeight;
        int aPhysSrcWidth = aSrcMemoryImage.mWidth;
        for (int x = 0; x < aPhysSrcWidth; x++) {
            int aTopBits = x;
            int aBottomBits = aTopBits + (aPhysSrcWidth * (aPhysSrcHeight - 1));

            for (int y = 0; y < (aPhysSrcHeight >> 1); y++) {
                int aSwap = aSrcBits[aTopBits];

                aSrcBits[aTopBits] = aSrcBits[aBottomBits];
                aTopBits += aPhysSrcWidth;
                aSrcBits[aBottomBits] = aSwap;
                aBottomBits -= aPhysSrcWidth;
            }
        }

        aSrcMemoryImage.bitsChanged();
    }

    // SexyAppBase::RotateImageHue (SexyAppBase.cpp:3848)
    static void rotateImageHue(MemoryImage theImage, int theDelta) {
        while (theDelta < 0) {
            theDelta += 256;
        }

        int aSize = theImage.mWidth * theImage.mHeight;
        int[] aPtr = theImage.getBits();
        for (int i = 0; i < aSize; i++) {
            int aPixel = aPtr[i];
            int alpha = aPixel & 0xFF000000;
            int r = (aPixel >>> 16) & 0xFF;
            int g = (aPixel >>> 8) & 0xFF;
            int b = aPixel & 0xFF;

            int maxval = Math.max(r, Math.max(g, b));
            int minval = Math.min(r, Math.min(g, b));
            int h = 0;
            int s = 0;
            int l = (minval + maxval) / 2;
            int delta = maxval - minval;

            if (delta != 0) {
                s = (delta * 256) / ((l <= 128) ? (minval + maxval) : (512 - maxval - minval));

                if (r == maxval) {
                    h = (g == minval ? 1280 + (((maxval - b) * 256) / delta) : 256 - (((maxval - g) * 256) / delta));
                } else if (g == maxval) {
                    h = (b == minval ? 256 + (((maxval - r) * 256) / delta) : 768 - (((maxval - b) * 256) / delta));
                } else {
                    h = (r == minval ? 768 + (((maxval - g) * 256) / delta) : 1280 - (((maxval - r) * 256) / delta));
                }

                h /= 6;
            }

            h += theDelta;
            if (h >= 256) {
                h -= 256;
            }

            int vInt = (l < 128) ? (l * (255 + s)) / 255 : (l + s - (l * s) / 255);

            double v = vInt;

            int y = (int) (2 * l - v);

            int aColorDiv = (6 * h) / 256;
            int x = (int) (y + (v - y) * ((h - (aColorDiv * 256 / 6)) * 6) / 255);
            x = Math.min(x, 255);

            int z = (int) (v - (v - y) * ((h - (aColorDiv * 256 / 6)) * 6) / 255);
            z = Math.max(z, 0);

            switch (aColorDiv) {
                case 0: r = (int) v; g = x; b = y; break;
                case 1: r = z; g = (int) v; b = y; break;
                case 2: r = y; g = (int) v; b = x; break;
                case 3: r = y; g = z; b = (int) v; break;
                case 4: r = x; g = y; b = (int) v; break;
                case 5: r = (int) v; g = y; b = z; break;
                default: r = (int) v; g = x; b = y; break;
            }

            aPtr[i] = alpha | (r << 16) | (g << 8) | (b);
        }

        theImage.bitsChanged();
    }

    // SexyAppBase::HSLToRGB (SexyAppBase.cpp:3918, scalar)
    static int hslToRgb(int h, int s, int l) {
        int r;
        int g;
        int b;

        int vInt = (l < 128) ? (l * (255 + s)) / 255 : (l + s - (l * s) / 255);

        double v = vInt;

        int y = (int) (2 * l - v);

        int aColorDiv = (6 * h) / 256;
        int x = (int) (y + (v - y) * ((h - (aColorDiv * 256 / 6)) * 6) / 255);
        x = Math.min(x, 255);

        int z = (int) (v - (v - y) * ((h - (aColorDiv * 256 / 6)) * 6) / 255);
        z = Math.max(z, 0);

        switch (aColorDiv) {
            case 0: r = (int) v; g = x; b = y; break;
            case 1: r = z; g = (int) v; b = y; break;
            case 2: r = y; g = (int) v; b = x; break;
            case 3: r = y; g = z; b = (int) v; break;
            case 4: r = x; g = y; b = (int) v; break;
            case 5: r = (int) v; g = y; b = z; break;
            default: r = (int) v; g = x; b = y; break;
        }

        return 0xFF000000 | (r << 16) | (g << 8) | (b);
    }

    // SexyAppBase::RGBToHSL (SexyAppBase.cpp:3950, scalar)
    static int rgbToHsl(int r, int g, int b) {
        int maxval = Math.max(r, Math.max(g, b));
        int minval = Math.min(r, Math.min(g, b));
        int hue = 0;
        int saturation = 0;
        int luminosity = (minval + maxval) / 2;
        int delta = maxval - minval;

        if (delta != 0) {
            saturation = (delta * 256) / ((luminosity <= 128) ? (minval + maxval) : (512 - maxval - minval));

            if (r == maxval) {
                hue = (g == minval ? 1280 + (((maxval - b) * 256) / delta) : 256 - (((maxval - g) * 256) / delta));
            } else if (g == maxval) {
                hue = (b == minval ? 256 + (((maxval - r) * 256) / delta) : 768 - (((maxval - b) * 256) / delta));
            } else {
                hue = (r == minval ? 768 + (((maxval - g) * 256) / delta) : 1280 - (((maxval - r) * 256) / delta));
            }

            hue /= 6;
        }

        return 0xFF000000 | (hue) | (saturation << 8) | (luminosity << 16);
    }

    // SexyAppBase::HSLToRGB (SexyAppBase.cpp:3976, array)
    static void hslToRgb(int[] theSource, int[] theDest, int theSize) {
        for (int i = 0; i < theSize; i++) {
            int src = theSource[i];
            theDest[i] = (src & 0xFF000000) | (hslToRgb(src & 0xFF, (src >>> 8) & 0xFF, (src >>> 16) & 0xFF) & 0x00FFFFFF);
        }
    }

    // SexyAppBase::RGBToHSL (SexyAppBase.cpp:3985, array)
    static void rgbToHsl(int[] theSource, int[] theDest, int theSize) {
        for (int i = 0; i < theSize; i++) {
            int src = theSource[i];
            theDest[i] = (src & 0xFF000000) | (rgbToHsl((src >>> 16) & 0xFF, (src >>> 8) & 0xFF, src & 0xFF) & 0x00FFFFFF);
        }
    }
}
