package cn.enaium.fabricpvz.portable.pvzp.reanim;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.PerfTimer;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Ported from src/PvzpLib/ReanimAtlas.h:56-72 (class ReanimAtlas) and
 * src/PvzpLib/ReanimAtlas.cpp (whole file).
 *
 * <p>The runtime atlas packer: every image referenced by a definition with a single cel
 * ({@code mNumCols == 1 && mNumRows == 1}) and a size of at most 254×254 is packed into one
 * power-of-two-wide memory image, and the definition's transforms keep pointing at their source
 * images.
 *
 * <p>PORT-NOTE: upstream overwrites {@code ReanimatorTransform::mImage} with the fake handle
 * {@code (Image*)(index + 1)} and decodes it again in {@code GetEncodedReanimAtlas} via the
 * {@code <= 1000} pointer test (Reanimator.cpp:700-722, ReanimAtlas.cpp:35-44). Java has no such
 * pointer encoding, so the original {@link Image} references are kept and the same
 * index lookup is served from an identity map; {@link #getEncodedReanimAtlas(Image)} returns the
 * same {@link ReanimAtlasImage} the upstream decode would (and {@code null} for images that are not
 * in the atlas), so the draw path behaves identically.
 */
public class ReanimAtlas {
    public List<ReanimAtlasImage> mImageArray;
    public MemoryImage mMemoryImage;

    private final Map<Image, ReanimAtlasImage> mEncodedImages;

    public ReanimAtlas() {
        mImageArray = new ArrayList<>();
        mEncodedImages = new IdentityHashMap<>();
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:35-44 (GetEncodedReanimAtlas). */
    public ReanimAtlasImage getEncodedReanimAtlas(Image theImage) {
        if (theImage == null) {
            return null;
        }
        return mEncodedImages.get(theImage);
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:46-64 (ReanimAtlasMakeBlankMemoryImage). */
    public static MemoryImage reanimAtlasMakeBlankMemoryImage(int theWidth, int theHeight) {
        MemoryImage aImage = new MemoryImage();
        aImage.create(theWidth, theHeight);  // zeroed bits, trans + alpha (upstream memset + mHasTrans/mHasAlpha)
        return aImage;
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:66-72 (sSortByNonIncreasingHeight). */
    private static int compareByNonIncreasingHeight(ReanimAtlasImage image1, ReanimAtlasImage image2) {
        if (image1.mHeight != image2.mHeight) {
            return Integer.compare(image2.mHeight, image1.mHeight);
        } else if (image1.mWidth != image2.mWidth) {
            return Integer.compare(image2.mWidth, image1.mWidth);
        }
        // Stable tiebreaker on the original image's allocation order (upstream compares the
        // pointers; identityHashCode keeps the sort deterministic for a given run).
        return Integer.compare(System.identityHashCode(image2.mOriginalImage), System.identityHashCode(image1.mOriginalImage));
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:74-79 (GetClosestPowerOf2Above). */
    private static int getClosestPowerOf2Above(int theNum) {
        int aPower2 = 1;
        while (aPower2 < theNum) {
            aPower2 <<= 1;
        }

        return aPower2;
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:81-95 (PickAtlasWidth). */
    public int pickAtlasWidth() {
        int totalArea = 0;
        int aMaxWidth = 0;
        for (ReanimAtlasImage aImage : mImageArray) {
            totalArea += aImage.mWidth * aImage.mHeight;
            aMaxWidth = Math.max(aMaxWidth, aImage.mWidth + 2);
        }

        int aWidth = PvzpCommon.floatRoundToInt((float) Math.sqrt(totalArea));  // side length assuming a square region
        return getClosestPowerOf2Above(Math.min(Math.max(aWidth, aMaxWidth), 2048));  // max of side length and widest image, capped at 2048, rounded up to a power of 2
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:97-110 (ImageFits). */
    public boolean imageFits(int theImageCount, Rect rectTest, int theMaxWidth) {
        if (rectTest.mX + rectTest.mWidth > theMaxWidth) {
            return false;
        }

        for (int i = 0; i < theImageCount; i++) {  // check the rect against the first theImageCount placed images
            ReanimAtlasImage aImage = mImageArray.get(i);
            if (new Rect(aImage.mX, aImage.mY, aImage.mWidth, aImage.mHeight).inflate(1, 1).intersects(rectTest)) {  // an image occupies its rect inflated by 1 pixel
                return false;
            }
        }
        return true;
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:112-145 (ImageFindPlaceOnSide). */
    public boolean imageFindPlaceOnSide(ReanimAtlasImage theAtlasImageToPlace, int theImageCount, int theMaxWidth, boolean theToRight) {
        Rect rectTest = new Rect(0, 0, theAtlasImageToPlace.mWidth + 2, theAtlasImageToPlace.mHeight + 2);

        for (int i = 0; i < theImageCount; i++) {
            ReanimAtlasImage aImage = mImageArray.get(i);
            if (theToRight) {  // to the right of the placed image
                rectTest.mX = aImage.mX + aImage.mWidth + 1;
                rectTest.mY = aImage.mY;
            } else {  // below the placed image
                rectTest.mX = aImage.mX;
                rectTest.mY = aImage.mY + aImage.mHeight + 1;
            }

            if (imageFits(theImageCount, rectTest, theMaxWidth)) {
                theAtlasImageToPlace.mX = rectTest.mX;
                theAtlasImageToPlace.mY = rectTest.mY;
                if (theToRight) {
                    theAtlasImageToPlace.mX += 1;
                } else {
                    theAtlasImageToPlace.mY += 1;
                }

                return true;
            }
        }

        return false;
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:147-152 (ImageFindPlace). */
    public boolean imageFindPlace(ReanimAtlasImage theAtlasImageToPlace, int theImageCount, int theMaxWidth) {
        return imageFindPlaceOnSide(theAtlasImageToPlace, theImageCount, theMaxWidth, true)
                || imageFindPlaceOnSide(theAtlasImageToPlace, theImageCount, theMaxWidth, false);  // try placing to the right, then below
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:154-169 (PlaceAtlasImage). */
    public boolean placeAtlasImage(ReanimAtlasImage theAtlasImageToPlace, int theImageCount, int theMaxWidth) {
        if (theImageCount == 0) {
            theAtlasImageToPlace.mX = 1;
            theAtlasImageToPlace.mY = 1;
            return true;
        }

        if (imageFindPlace(theAtlasImageToPlace, theImageCount, theMaxWidth)) {
            return true;
        }

        return false;
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:171-185 (ArrangeImages). */
    public void arrangeImages(int[] theAtlasWidthHeight) {
        mImageArray.sort(ReanimAtlas::compareByNonIncreasingHeight);
        theAtlasWidthHeight[0] = pickAtlasWidth();
        int theAtlasHeight = 0;

        for (int i = 0; i < mImageArray.size(); i++) {
            ReanimAtlasImage aImage = mImageArray.get(i);
            placeAtlasImage(aImage, i, theAtlasWidthHeight[0]);

            // computed once here so the max expression does not evaluate it twice
            int aImageHeight = getClosestPowerOf2Above(aImage.mY + aImage.mHeight);
            theAtlasHeight = Math.max(aImageHeight, theAtlasHeight);
        }
        theAtlasWidthHeight[1] = theAtlasHeight;
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:187-197 (AddImage). */
    public void addImage(Image theImage) {
        if (theImage.mNumCols == 1 && theImage.mNumRows == 1) {
            ReanimAtlasImage aImage = new ReanimAtlasImage();
            aImage.mHeight = theImage.mHeight;
            aImage.mWidth = theImage.mWidth;
            aImage.mOriginalImage = theImage;
            mImageArray.add(aImage);
        }
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:199-207 (FindImage). */
    public int findImage(Image theImage) {
        for (int i = 0; i < mImageArray.size(); i++) {
            if (mImageArray.get(i).mOriginalImage == theImage) {
                return i;
            }
        }

        return -1;
    }

    /** Ported from src/PvzpLib/ReanimAtlas.cpp:209-251 (ReanimAtlasCreate). */
    public void reanimAtlasCreate(ReanimatorDefinition theReanimDef) {
        PerfTimer aTimer = new PerfTimer();
        aTimer.start();

        for (ReanimatorTrack aTrack : Reanimator.tracksOf(theReanimDef)) {
            for (ReanimatorTransform aKey : Reanimator.transformsOf(aTrack)) {
                Image aImage = aKey.mImage;
                if (aImage != null && aImage.mWidth <= 254 && aImage.mHeight <= 254 && findImage(aImage) < 0) {
                    addImage(aImage);  // add it now; its atlas position is decided later
                }
            }
        }

        int[] aAtlasSize = new int[2];
        arrangeImages(aAtlasSize);
        for (ReanimAtlasImage aImage : mImageArray) {
            mEncodedImages.put(aImage.mOriginalImage, aImage);
        }

        mMemoryImage = reanimAtlasMakeBlankMemoryImage(aAtlasSize[0], aAtlasSize[1]);
        Graphics aMemoryGraphics = new Graphics(mMemoryImage);
        for (ReanimAtlasImage aImage : mImageArray) {
            aMemoryGraphics.drawImage(aImage.mOriginalImage, aImage.mX, aImage.mY);
        }
        PvzpCommon.fixPixelsOnAlphaEdgeForBlending(mMemoryImage);  // set transparent pixels to the average color of their neighbors
    }
}
