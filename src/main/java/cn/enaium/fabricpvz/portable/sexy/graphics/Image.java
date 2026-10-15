package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;

/**
 * Ported from src/SexyAppFramework/graphics/Image.h:74 (implementation src/SexyAppFramework/graphics/Image.cpp:31-262).
 *
 * <p>Faithful port of {@code Sexy::Image}: the base image type. It carries the geometry and strip
 * bookkeeping used by every draw path, and declares the virtual blit/fill entry points that the
 * software ({@link MemoryImage}) and textured ({@link GLImage}) implementations override.
 *
 * <p>As in upstream, the image is described by a rectangle pair and a cel grid: an image with
 * {@code mNumCols}/{@code mNumRows} greater than one is a strip whose cels are addressed by
 * {@link #getCelRect(int)} / {@link #getCelRect(int, int)}; animation across cels is described by
 * {@link #mAnimInfo}.
 *
 * <p>PORT-NOTE: in C++ the base-class draw entry points have empty bodies and {@code PolyFill3D}
 * returns {@code false} (Image.cpp:224-262); the Java base class mirrors that exactly, so a plain
 * {@code Image} silently ignores drawing while {@link MemoryImage} performs it in software and
 * {@link GLImage} forwards it to the renderer backend. {@code operator=} is deleted upstream and is
 * simply not provided here.
 */
public class Image {
    /** Set to {@code true} by every blit that consumes this image (upstream {@code mDrawn}). */
    public boolean mDrawn;
    /** Upstream {@code std::string mFilePath}; {@code null} is upstream's empty string. */
    public String mFilePath;
    public int mWidth;
    public int mHeight;

    // for image strips
    public int mNumRows;
    public int mNumCols;

    // for animations
    public AnimInfo mAnimInfo;

    public Image() {
        mWidth = 0;
        mHeight = 0;

        mNumRows = 1;
        mNumCols = 1;

        mDrawn = false;
    }

    /**
     * Copy constructor (upstream {@code Image(const Image&)}): copies the geometry, the cel grid and
     * a clone of the animation info; {@code mDrawn} is reset and {@code mFilePath} is not copied,
     * exactly as upstream.
     */
    public Image(Image theImage) {
        mWidth = theImage.mWidth;
        mHeight = theImage.mHeight;
        mNumRows = theImage.mNumRows;
        mNumCols = theImage.mNumCols;

        mDrawn = false;
        if (theImage.mAnimInfo != null) {
            mAnimInfo = new AnimInfo(theImage.mAnimInfo);
        }
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    /** Returns the width of just 1 cel in a strip of images. */
    public int getCelWidth() {
        return mWidth / mNumCols;
    }

    /** Like {@link #getCelWidth()} but for vertical strips. */
    public int getCelHeight() {
        return mHeight / mNumRows;
    }

    /** Uses {@link #mAnimInfo} to return the appropriate cel to draw at the time. */
    public int getAnimCel(int theTime) {
        if (mAnimInfo == null) {
            return 0;
        } else {
            return mAnimInfo.getCel(theTime);
        }
    }

    // GetAnimCelRect
    public Rect getAnimCelRect(int theTime) {
        int aCel = getAnimCel(theTime);
        int aCelWidth = getCelWidth();
        int aCelHeight = getCelHeight();
        if (mNumCols > 1) {
            return new Rect(aCel * aCelWidth, 0, aCelWidth, mHeight);
        } else {
            return new Rect(0, aCel * aCelHeight, mWidth, aCelHeight);
        }
    }

    /** Gets the rectangle for the given cel at the specified row/col. */
    public Rect getCelRect(int theCel) {
        int h = getCelHeight();
        int w = getCelWidth();
        int x = (theCel % mNumCols) * w;
        int y = (theCel / mNumCols) * h;

        return new Rect(x, y, w, h);
    }

    /** Same as {@link #getCelRect(int)}, but for an image with both multiple rows and cols. */
    public Rect getCelRect(int theCol, int theRow) {
        int h = getCelHeight();
        int w = getCelWidth();
        int x = theCol * w;
        int y = theRow * h;

        return new Rect(x, y, w, h);
    }

    /** Copies {@code mNumCols}/{@code mNumRows}/{@code mAnimInfo} from another image. */
    public void copyAttributes(Image from) {
        mNumCols = from.mNumCols;
        mNumRows = from.mNumRows;
        mAnimInfo = null;
        if (from.mAnimInfo != null) {
            mAnimInfo = new AnimInfo(from.mAnimInfo);
        }
    }

    /**
     * Returns a {@link Graphics} bound to this image (upstream {@code new Graphics(this)}), used for
     * off-screen compositing.
     */
    public Graphics getGraphics() {
        return new Graphics(this);
    }

    /**
     * PORT-NOTE: replaces upstream's {@code GLImage::Check3D(Image*)} dynamic cast
     * ({@code GLImage.cpp:86-95}). Returns whether blitting this image takes the 3D/textured path;
     * {@link GLImage} overrides it with {@code true}.
     */
    public boolean is3D() {
        return false;
    }

    public boolean polyFill3D(Point[] theVertices, int theNumVertices, Rect theClipRect, Color theColor, int theDrawMode, int tx, int ty) {
        return false;
    }

    public void fillRect(Rect theRect, Color theColor, int theDrawMode) {
    }

    public void drawRect(Rect theRect, Color theColor, int theDrawMode) {
        fillRect(new Rect(theRect.mX, theRect.mY, theRect.mWidth + 1, 1), theColor, theDrawMode);
        fillRect(new Rect(theRect.mX, theRect.mY + theRect.mHeight, theRect.mWidth + 1, 1), theColor, theDrawMode);
        fillRect(new Rect(theRect.mX, theRect.mY + 1, 1, theRect.mHeight - 1), theColor, theDrawMode);
        fillRect(new Rect(theRect.mX + theRect.mWidth, theRect.mY + 1, 1, theRect.mHeight - 1), theColor, theDrawMode);
    }

    public void clearRect(Rect theRect) {
    }

    public void drawLine(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor, int theDrawMode) {
    }

    public void drawLineAA(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor, int theDrawMode) {
    }

    public void fillScanLines(Span[] theSpans, int theSpanCount, Color theColor, int theDrawMode) {
        for (int i = 0; i < theSpanCount; i++) {
            Span aSpan = theSpans[i];
            fillRect(new Rect(aSpan.mX, aSpan.mY, aSpan.mWidth, 1), theColor, theDrawMode);
        }
    }

    public void fillScanLinesWithCoverage(Span[] theSpans, int theSpanCount, Color theColor, int theDrawMode, byte[] theCoverage, int theCoverX, int theCoverY, int theCoverWidth, int theCoverHeight) {
    }

    public void blt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
    }

    public void bltF(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode) {
    }

    public void bltRotated(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, double theRot, float theRotCenterX, float theRotCenterY) {
    }

    public void stretchBlt(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
    }

    public void bltMatrix(Image theImage, float x, float y, SexyMatrix3 theMatrix, Rect theClipRect, Color theColor, int theDrawMode, Rect theSrcRect, boolean blend) {
    }

    public void bltTrianglesTex(Image theTexture, TriVertex[][] theVertices, int theNumTriangles, Rect theClipRect, Color theColor, int theDrawMode, float tx, float ty, boolean blend) {
    }

    public void bltMirror(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
    }

    public void stretchBltMirror(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
    }
}
