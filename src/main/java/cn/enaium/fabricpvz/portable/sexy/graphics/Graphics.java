package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.Transform;

import java.util.Arrays;

/**
 * Ported from src/SexyAppFramework/graphics/Graphics.h:78 (class Graphics) and
 * src/SexyAppFramework/graphics/Graphics.cpp:46 (Graphics::Graphics(const Graphics&amp;), etc.).
 *
 * <p>{@code Graphics} is a state object plus a dispatch layer, not a rasterizer: it holds the
 * destination {@link Image}, the translation, the "in progress" scale, the clip rectangle, the
 * current color/font/draw mode and forwards every operation to the destination image's virtual blit
 * methods (upstream {@code Graphics.cpp} throughout). Because the only platform access happens
 * inside {@link Image} implementations, this class stays free of SDL/LWJGL imports.
 *
 * <p>Semantics preserved exactly: alpha-0 early-outs in {@code FillRect}/{@code DrawRect}, the
 * CPU-side rect intersection + source-rect shrinking, all {@code DrawImage} overloads, the scale
 * path ({@code mScaleOrigX + floor((x-mScaleOrigX)*mScaleX)}, src-rect overload), mirroring by
 * source-rect recomputation, rotation/center defaults, {@code DrawImageTransformHelper}'s
 * decomposition, cel math, the 9-slice {@code DrawImageBox}, {@code WriteString} {@code ^rrggbb^} /
 * {@code ^oldclr^} / {@code ^^} tags and {@code WriteWordWrapped}'s code-point-aware wrapping.
 *
 * <p>Java deviations:
 * <ul>
 *   <li>Nullable {@code int*} out-parameters become {@link IntRef} holders (or {@code null}).</li>
 *   <li>{@code std::string_view} becomes {@link String}; indices are UTF-16 char indices (ASCII
 *       color tags behave identically; code-point classification uses {@link Character}).</li>
 *   <li>Upstream has no rotation state on {@code Graphics}; rotation is expressed via
 *       {@link Transform} + {@link #drawImageMatrix} / {@link Image#bltMatrix}, which reanim and
 *       particles use. {@link #drawStringShaded} and {@link #drawStringWrapped} are thin
 *       convenience aliases required by the Java API contract.</li>
 * </ul>
 */
public class Graphics extends GraphicsState {

    /** Upstream {@code Graphics::DRAWMODE_NORMAL} (Graphics.h:82). */
    public static final int DRAWMODE_NORMAL = 0;
    /** Upstream {@code Graphics::DRAWMODE_ADDITIVE} (Graphics.h:83). */
    public static final int DRAWMODE_ADDITIVE = 1;

    private static final int MAX_TEMP_SPANS = 8192;

    /** Simple mutable {@code int} holder replacing C++ {@code int*} out-parameters. */
    public static final class IntRef {
        public int value;

        public IntRef() {
        }

        public IntRef(int theValue) {
            value = theValue;
        }
    }

    /** Ported from {@code struct Edge} (src/SexyAppFramework/graphics/Graphics.h:41). */
    public static final class Edge {
        public double mX;
        public double mDX;
        public int i;
        public double b;
    }

    public Edge[] mPFActiveEdgeList;
    public int mPFNumActiveEdges;
    public int mPFNumVertices;

    /** Upstream {@code GraphicsStateList mStateStack} (Graphics.h:95). */
    public java.util.List<GraphicsState> mStateStack = new java.util.ArrayList<>();

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:46 (Graphics::Graphics(const Graphics&amp;)). */
    public Graphics(Graphics theGraphics) {
        copyStateFrom(theGraphics);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:51 (Graphics::Graphics(Image*)). */
    public Graphics(Image theDestImage) {
        mTransX = 0;
        mTransY = 0;
        mScaleX = 1;
        mScaleY = 1;
        mScaleOrigX = 0;
        mScaleOrigY = 0;
        mFont = null;
        mDestImage = theDestImage;
        mDrawMode = DRAWMODE_NORMAL;
        mColorizeImages = false;
        mFastStretch = false;
        mWriteColoredString = true;
        mLinearBlend = true; // Default to linear filtering
        mColor = new Color();

        if (mDestImage == null) {
            mDestImage = mStaticImage;
            mIs3D = false;
        } else {
            mIs3D = theDestImage.is3D();
        }

        mClipRect = new Rect(0, 0, mDestImage.getWidth(), mDestImage.getHeight());
    }

    /** Java API contract convenience: {@code Graphics()} targets the static no-op image. */
    public Graphics() {
        this((Image) null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:82 (Graphics::PushState). */
    public void pushState() {
        GraphicsState aState = new GraphicsState();
        aState.copyStateFrom(this);
        mStateStack.add(aState);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:89 (Graphics::PopState). */
    public void popState() {
        if (!mStateStack.isEmpty()) {
            GraphicsState aState = mStateStack.remove(mStateStack.size() - 1);
            copyStateFrom(aState);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:99 (Graphics::Create). */
    public Graphics create() {
        return new Graphics(this);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:104 (Graphics::GetFont). */
    public Font getFont() {
        return mFont;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:109 (Graphics::SetFont). */
    public void setFont(Font theFont) {
        mFont = theFont;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:114 (Graphics::SetColor). */
    public void setColor(Color theColor) {
        mColor = theColor;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:119 (Graphics::GetColor). */
    public Color getColor() {
        return mColor;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:124 (Graphics::SetDrawMode). */
    public void setDrawMode(int theDrawMode) {
        mDrawMode = theDrawMode;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:129 (Graphics::GetDrawMode). */
    public int getDrawMode() {
        return mDrawMode;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:134 (Graphics::SetColorizeImages). */
    public void setColorizeImages(boolean colorizeImages) {
        mColorizeImages = colorizeImages;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:139 (Graphics::GetColorizeImages). */
    public boolean getColorizeImages() {
        return mColorizeImages;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:144 (Graphics::SetFastStretch). */
    public void setFastStretch(boolean fastStretch) {
        mFastStretch = fastStretch;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:149 (Graphics::GetFastStretch). */
    public boolean getFastStretch() {
        return mFastStretch;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:154 (Graphics::SetLinearBlend). */
    public void setLinearBlend(boolean linear) {
        mLinearBlend = linear;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:159 (Graphics::GetLinearBlend). */
    public boolean getLinearBlend() {
        return mLinearBlend;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:164 (Graphics::ClearRect). */
    public void clearRect(int theX, int theY, int theWidth, int theHeight) {
        Rect aDestRect = new Rect(theX + (int) mTransX, theY + (int) mTransY, theWidth, theHeight).intersection(mClipRect);
        mDestImage.clearRect(aDestRect);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:170 (Graphics::ClearRect(Rect)). */
    public void clearRect(Rect theRect) {
        clearRect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:175 (Graphics::FillRect). */
    public void fillRect(int theX, int theY, int theWidth, int theHeight) {
        if (mColor.mAlpha == 0) {
            return;
        }

        Rect aDestRect = new Rect(theX + (int) mTransX, theY + (int) mTransY, theWidth, theHeight).intersection(mClipRect);
        mDestImage.fillRect(aDestRect, mColor, mDrawMode);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:204 (Graphics::FillRect(Rect)). */
    public void fillRect(Rect theRect) {
        fillRect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:209 (Graphics::DrawRect). */
    public void drawRect(int theX, int theY, int theWidth, int theHeight) {
        if (mColor.mAlpha == 0) {
            return;
        }

        Rect aDestRect = new Rect(theX + (int) mTransX, theY + (int) mTransY, theWidth, theHeight);
        Rect aFullDestRect = new Rect(theX + (int) mTransX, theY + (int) mTransY, theWidth + 1, theHeight + 1);
        Rect aFullClippedRect = aFullDestRect.intersection(mClipRect);

        if (aFullDestRect.equals(aFullClippedRect)) {
            mDestImage.drawRect(aDestRect, mColor, mDrawMode);
        } else {
            fillRect(theX, theY, theWidth + 1, 1);
            fillRect(theX, theY + theHeight, theWidth + 1, 1);
            fillRect(theX, theY + 1, 1, theHeight - 1);
            fillRect(theX + theWidth, theY + 1, 1, theHeight - 1);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:232 (Graphics::DrawRect(Rect)). */
    public void drawRect(Rect theRect) {
        drawRect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }

    private void pfDelete(int i) {
        int j;
        for (j = 0; j < mPFNumActiveEdges && mPFActiveEdgeList[j].i != i; j++) {
            // find edge
        }
        if (j >= mPFNumActiveEdges) {
            return; // edge not in active list; happens at aMinY
        }

        mPFNumActiveEdges--;
        System.arraycopy(mPFActiveEdgeList, j + 1, mPFActiveEdgeList, j, mPFNumActiveEdges - j);
    }

    private void pfInsert(Point[] points, int i, int y) {
        int j = i < mPFNumVertices - 1 ? i + 1 : 0;
        Point p;
        Point q;
        if (points[i].mY < points[j].mY) {
            p = points[i];
            q = points[j];
        } else {
            p = points[j];
            q = points[i];
        }
        /* initialize x position at intersection of edge with scanline y */
        double dx = (q.mX - p.mX) / (double) (q.mY - p.mY);
        mPFActiveEdgeList[mPFNumActiveEdges].mDX = dx;
        mPFActiveEdgeList[mPFNumActiveEdges].mX = dx * (y + 0.5 - p.mY - mTransY) + p.mX + mTransX;
        mPFActiveEdgeList[mPFNumActiveEdges].i = i;
        mPFActiveEdgeList[mPFNumActiveEdges].b = p.mY - 1.0 / dx * p.mX;
        mPFNumActiveEdges++;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:280 (Graphics::PolyFill). */
    public void polyFill(Point[] theVertexList, int theNumVertices) {
        polyFill(theVertexList, theNumVertices, false);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:280 (Graphics::PolyFill). */
    public void polyFill(Point[] theVertexList, int theNumVertices, boolean convex) {
        if (convex && mDestImage.polyFill3D(theVertexList, theNumVertices, mClipRect, mColor, mDrawMode, (int) mTransX, (int) mTransY)) {
            return;
        }

        Span[] aSpans = new Span[MAX_TEMP_SPANS];
        int aSpanPos = 0;

        int k;
        int y0;
        int y1;
        int y;
        int i;
        int j;
        int xl;
        int xr;
        int[] ind; // list of vertex indices, sorted by points[ind[j]].mY

        int aMinX = mClipRect.mX;
        int aMaxX = mClipRect.mX + mClipRect.mWidth - 1;
        int aMinY = mClipRect.mY;
        int aMaxY = mClipRect.mY + mClipRect.mHeight - 1;

        mPFNumVertices = theNumVertices;

        if (mPFNumVertices <= 0) {
            return;
        }

        ind = new int[mPFNumVertices];
        Edge[] aEdgeList = new Edge[mPFNumVertices];
        for (int e = 0; e < aEdgeList.length; e++) {
            aEdgeList[e] = new Edge();
        }
        mPFActiveEdgeList = aEdgeList;

        /* create y-sorted array of indices ind[k] into vertex list */
        for (k = 0; k < mPFNumVertices; k++) {
            ind[k] = k;
        }
        sortIndicesByY(ind, theVertexList); // sort ind by points[ind[k]].mY

        mPFNumActiveEdges = 0; // start with empty active list
        k = 0; // ind[k] is next vertex to process
        y0 = Math.max(aMinY, (int) Math.ceil(theVertexList[ind[0]].mY - 0.5 + mTransY)); // ymin of polygon
        y1 = Math.min(aMaxY, (int) Math.floor(theVertexList[ind[mPFNumVertices - 1]].mY - 0.5 + mTransY)); // ymax of polygon

        for (y = y0; y <= y1; y++) {
            // step through scanlines; scanline y is at y+.5 in continuous coordinates
            for (; (k < mPFNumVertices) && (theVertexList[ind[k]].mY + mTransY <= y + 0.5); k++) {
                i = ind[k];
                j = i > 0 ? i - 1 : mPFNumVertices - 1; // vertex previous to i
                if (theVertexList[j].mY + mTransY <= y - 0.5) {
                    pfDelete(j);
                } else if (theVertexList[j].mY + mTransY > y + 0.5) {
                    pfInsert(theVertexList, j, y);
                }

                j = i < mPFNumVertices - 1 ? i + 1 : 0; // vertex next after i
                if (theVertexList[j].mY + mTransY <= y - 0.5) {
                    pfDelete(i);
                } else if (theVertexList[j].mY + mTransY > y + 0.5) {
                    pfInsert(theVertexList, i, y);
                }
            }

            sortActiveEdgesByX();

            for (j = 0; j < mPFNumActiveEdges; j += 2) {
                xl = (int) Math.ceil(mPFActiveEdgeList[j].mX - 0.5);
                xl = Math.max(xl, aMinX);
                xr = (int) Math.floor(mPFActiveEdgeList[j + 1].mX - 0.5);
                xr = Math.min(xr, aMaxX);

                if ((xl <= xr) && (aSpanPos < MAX_TEMP_SPANS)) {
                    Span aSpan = new Span();
                    aSpan.mY = y;
                    aSpan.mX = xl;
                    aSpan.mWidth = xr - xl + 1;
                    aSpans[aSpanPos++] = aSpan;
                }

                mPFActiveEdgeList[j].mX += mPFActiveEdgeList[j].mDX;
                mPFActiveEdgeList[j + 1].mX += mPFActiveEdgeList[j + 1].mDX;
            }
        }

        mDestImage.fillScanLines(aSpans, aSpanPos, mColor, mDrawMode);

        mPFActiveEdgeList = null;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:373 (Graphics::PolyFillAA). */
    public void polyFillAA(Point[] theVertexList, int theNumVertices) {
        polyFillAA(theVertexList, theNumVertices, false);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:373 (Graphics::PolyFillAA). */
    public void polyFillAA(Point[] theVertexList, int theNumVertices, boolean convex) {
        if (convex && mDestImage.polyFill3D(theVertexList, theNumVertices, mClipRect, mColor, mDrawMode, (int) mTransX, (int) mTransY)) {
            return;
        }

        int i;

        Span[] aSpans = new Span[MAX_TEMP_SPANS];
        int aSpanPos = 0;

        byte[] aCoverageBuffer;
        int aCoverWidth;
        int aCoverHeight;
        int aCoverLeft = 0;
        int aCoverRight = 0;
        int aCoverTop = 0;
        int aCoverBottom = 0;

        for (i = 0; i < theNumVertices; ++i) {
            Point aPt = theVertexList[i];
            if (i == 0) {
                aCoverLeft = aCoverRight = aPt.mX;
                aCoverTop = aCoverBottom = aPt.mY;
            } else {
                aCoverLeft = Math.min(aCoverLeft, aPt.mX);
                aCoverRight = Math.max(aCoverRight, aPt.mX);
                aCoverTop = Math.min(aCoverTop, aPt.mY);
                aCoverBottom = Math.max(aCoverBottom, aPt.mY);
            }
        }
        aCoverWidth = 256;
        aCoverHeight = 256;
        if ((aCoverRight - aCoverLeft + 1) > aCoverWidth || (aCoverBottom - aCoverTop + 1) > aCoverHeight) {
            aCoverWidth = aCoverRight - aCoverLeft + 1;
            aCoverHeight = aCoverBottom - aCoverTop + 1;
        }
        aCoverageBuffer = new byte[aCoverWidth * aCoverHeight];

        int k;
        int y0;
        int y1;
        int y;
        int j;
        int xl;
        int xr;

        int aMinX = mClipRect.mX;
        int aMaxX = mClipRect.mX + mClipRect.mWidth - 1;
        int aMinY = mClipRect.mY;
        int aMaxY = mClipRect.mY + mClipRect.mHeight - 1;

        mPFNumVertices = theNumVertices;

        if (mPFNumVertices <= 0) {
            return;
        }

        int[] ind = new int[mPFNumVertices];
        Edge[] aEdgeList = new Edge[mPFNumVertices];
        for (int e = 0; e < aEdgeList.length; e++) {
            aEdgeList[e] = new Edge();
        }
        mPFActiveEdgeList = aEdgeList;

        for (k = 0; k < mPFNumVertices; k++) {
            ind[k] = k;
        }
        sortIndicesByY(ind, theVertexList);

        mPFNumActiveEdges = 0;
        k = 0;
        y0 = Math.max(aMinY, (int) Math.ceil(theVertexList[ind[0]].mY - 0.5 + mTransY));
        y1 = Math.min(aMaxY, (int) Math.floor(theVertexList[ind[mPFNumVertices - 1]].mY - 0.5 + mTransY));

        for (y = y0; y <= y1; y++) {
            for (; (k < mPFNumVertices) && (theVertexList[ind[k]].mY + mTransY <= y + 0.5); k++) {
                i = ind[k];
                j = i > 0 ? i - 1 : mPFNumVertices - 1;
                if (theVertexList[j].mY + mTransY <= y - 0.5) {
                    pfDelete(j);
                } else if (theVertexList[j].mY + mTransY > y + 0.5) {
                    pfInsert(theVertexList, j, y);
                }

                j = i < mPFNumVertices - 1 ? i + 1 : 0;
                if (theVertexList[j].mY + mTransY <= y - 0.5) {
                    pfDelete(i);
                } else if (theVertexList[j].mY + mTransY > y + 0.5) {
                    pfInsert(theVertexList, i, y);
                }
            }

            sortActiveEdgesByX();

            for (j = 0; j < mPFNumActiveEdges; j += 2) {
                xl = (int) Math.ceil(mPFActiveEdgeList[j].mX - 0.5);
                int lErr = (int) (Math.abs((mPFActiveEdgeList[j].mX - 0.5) - xl) * 255);
                if (xl < aMinX) {
                    xl = aMinX;
                    lErr = 255;
                }
                xr = (int) Math.floor(mPFActiveEdgeList[j + 1].mX - 0.5);
                int rErr = (int) (Math.abs((mPFActiveEdgeList[j + 1].mX - 0.5) - xr) * 255);
                if (xr > aMaxX) {
                    xr = aMaxX;
                    rErr = 255;
                }

                if ((xl <= xr) && (aSpanPos < MAX_TEMP_SPANS)) {
                    Span aSpan = new Span();
                    aSpan.mY = y;
                    aSpan.mX = xl;
                    aSpan.mWidth = xr - xl + 1;
                    aSpans[aSpanPos++] = aSpan;

                    int coverRowBase = (y - aCoverTop) * aCoverWidth;
                    if (xr == xl) {
                        int cov = (aCoverageBuffer[coverRowBase + xl - aCoverLeft] & 0xFF) + ((lErr * rErr) >> 8);
                        aCoverageBuffer[coverRowBase + xl - aCoverLeft] = (byte) Math.min(255, cov);
                    } else {
                        if (Math.abs(mPFActiveEdgeList[j].mDX) > 1.0f) {
                            double m = 1.0 / mPFActiveEdgeList[j].mDX;
                            double b = mPFActiveEdgeList[j].b;
                            double c = Math.abs(mPFActiveEdgeList[j].mDX);
                            do {
                                double yEdge = m * xl + b;
                                lErr = Math.min(255, (int) (Math.abs(yEdge - y - .5) * 255));
                                int cov = (aCoverageBuffer[coverRowBase + xl - aCoverLeft] & 0xFF) + lErr;
                                aCoverageBuffer[coverRowBase + xl - aCoverLeft] = (byte) Math.min(255, cov);
                                xl++;
                                c -= 1.0;
                            } while (xl <= xr && c > 0);
                        } else {
                            int cov = (aCoverageBuffer[coverRowBase + xl - aCoverLeft] & 0xFF) + lErr;
                            aCoverageBuffer[coverRowBase + xl - aCoverLeft] = (byte) Math.min(255, cov);
                            xl++;
                        }

                        if (Math.abs(mPFActiveEdgeList[j + 1].mDX) > 1.0f) {
                            double m = 1.0 / mPFActiveEdgeList[j + 1].mDX;
                            double b = mPFActiveEdgeList[j + 1].b;
                            double c = Math.abs(mPFActiveEdgeList[j + 1].mDX);
                            do {
                                double yEdge = m * xr + b;
                                rErr = Math.min(255, (int) (Math.abs(yEdge - y - .5) * 255));
                                int cov = (aCoverageBuffer[coverRowBase + xr - aCoverLeft] & 0xFF) + rErr;
                                aCoverageBuffer[coverRowBase + xr - aCoverLeft] = (byte) Math.min(255, cov);
                                xr--;
                                c -= 1.0;
                            } while (xr >= xl && c > 0);
                        } else {
                            int cov = (aCoverageBuffer[coverRowBase + xr - aCoverLeft] & 0xFF) + rErr;
                            aCoverageBuffer[coverRowBase + xr - aCoverLeft] = (byte) Math.min(255, cov);
                            xr--;
                        }

                        if (xl <= xr) {
                            Arrays.fill(aCoverageBuffer, coverRowBase + xl - aCoverLeft, coverRowBase + xr - aCoverLeft + 1, (byte) 255);
                        }
                    }
                }

                mPFActiveEdgeList[j].mX += mPFActiveEdgeList[j].mDX;
                mPFActiveEdgeList[j + 1].mX += mPFActiveEdgeList[j + 1].mDX;
            }
        }

        mDestImage.fillScanLinesWithCoverage(aSpans, aSpanPos, mColor, mDrawMode, aCoverageBuffer,
                aCoverLeft, aCoverTop, aCoverWidth, aCoverHeight);

        mPFActiveEdgeList = null;
    }

    private static void sortIndicesByY(int[] ind, Point[] points) {
        // Mirrors PFCompareInd: points[a].mY <= points[b].mY ? -1 : 1. Insertion sort keeps it
        // deterministic for equal Y (stable by construction of the scanline algorithm's needs).
        for (int i = 1; i < ind.length; i++) {
            int v = ind[i];
            int j = i - 1;
            while (j >= 0 && points[ind[j]].mY > points[v].mY) {
                ind[j + 1] = ind[j];
                j--;
            }
            ind[j + 1] = v;
        }
    }

    private void sortActiveEdgesByX() {
        // Mirrors PFCompareActive: active[a].mX <= active[b].mX ? -1 : 1.
        for (int i = 1; i < mPFNumActiveEdges; i++) {
            Edge v = mPFActiveEdgeList[i];
            int j = i - 1;
            while (j >= 0 && mPFActiveEdgeList[j].mX > v.mX) {
                mPFActiveEdgeList[j + 1] = mPFActiveEdgeList[j];
                j--;
            }
            mPFActiveEdgeList[j + 1] = v;
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:561 (Graphics::DrawLineClipHelper). */
    private boolean drawLineClipHelper(double[] theStartX, double[] theStartY, double[] theEndX, double[] theEndY) {
        double aStartX = theStartX[0];
        double aStartY = theStartY[0];
        double aEndX = theEndX[0];
        double aEndY = theEndY[0];

        // Clip X
        if (aStartX > aEndX) {
            double t = aStartX; aStartX = aEndX; aEndX = t;
            t = aStartY; aStartY = aEndY; aEndY = t;
        }

        if (aStartX < mClipRect.mX) {
            if (aEndX < mClipRect.mX) {
                return false;
            }

            double aSlope = (aEndY - aStartY) / (aEndX - aStartX);
            aStartY += (mClipRect.mX - aStartX) * aSlope;
            aStartX = mClipRect.mX;
        }

        if (aEndX >= mClipRect.mX + mClipRect.mWidth) {
            if (aStartX >= mClipRect.mX + mClipRect.mWidth) {
                return false;
            }

            double aSlope = (aEndY - aStartY) / (aEndX - aStartX);
            aEndY += (mClipRect.mX + mClipRect.mWidth - 1 - aEndX) * aSlope;
            aEndX = mClipRect.mX + mClipRect.mWidth - 1;
        }

        // Clip Y
        if (aStartY > aEndY) {
            double t = aStartX; aStartX = aEndX; aEndX = t;
            t = aStartY; aStartY = aEndY; aEndY = t;
        }

        if (aStartY < mClipRect.mY) {
            if (aEndY < mClipRect.mY) {
                return false;
            }

            double aSlope = (aEndX - aStartX) / (aEndY - aStartY);
            aStartX += (mClipRect.mY - aStartY) * aSlope;
            aStartY = mClipRect.mY;
        }

        if (aEndY >= mClipRect.mY + mClipRect.mHeight) {
            if (aStartY >= mClipRect.mY + mClipRect.mHeight) {
                return false;
            }

            double aSlope = (aEndX - aStartX) / (aEndY - aStartY);
            aEndX += (mClipRect.mY + mClipRect.mHeight - 1 - aEndY) * aSlope;
            aEndY = mClipRect.mY + mClipRect.mHeight - 1;
        }

        theStartX[0] = aStartX;
        theStartY[0] = aStartY;
        theEndX[0] = aEndX;
        theEndY[0] = aEndY;

        return true;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:632 (Graphics::DrawLine). */
    public void drawLine(int theStartX, int theStartY, int theEndX, int theEndY) {
        double[] aStartX = {theStartX + mTransX};
        double[] aStartY = {theStartY + mTransY};
        double[] aEndX = {theEndX + mTransX};
        double[] aEndY = {theEndY + mTransY};

        if (!drawLineClipHelper(aStartX, aStartY, aEndX, aEndY)) {
            return;
        }

        mDestImage.drawLine(aStartX[0], aStartY[0], aEndX[0], aEndY[0], mColor, mDrawMode);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:645 (Graphics::DrawLineAA). */
    public void drawLineAA(int theStartX, int theStartY, int theEndX, int theEndY) {
        double[] aStartX = {theStartX + mTransX};
        double[] aStartY = {theStartY + mTransY};
        double[] aEndX = {theEndX + mTransX};
        double[] aEndY = {theEndY + mTransY};

        if (!drawLineClipHelper(aStartX, aStartY, aEndX, aEndY)) {
            return;
        }

        mDestImage.drawLineAA(aStartX[0], aStartY[0], aEndX[0], aEndY[0], mColor, mDrawMode);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:658 (Graphics::DrawString). */
    public void drawString(String theString, int theX, int theY) {
        if (mFont != null) {
            mFont.drawString(this, theX, theY, theString, mColor, mClipRect);
        }
    }

    /** Java API contract convenience: draws with an explicit color without leaving it set. */
    public void drawString(String theString, int theX, int theY, Color theColor) {
        Color anOrigColor = mColor;
        setColor(theColor);
        drawString(theString, theX, theY);
        setColor(anOrigColor);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:664 (Graphics::DrawImage(Image*, int, int)). */
    public void drawImage(Image theImage, int theX, int theY) {
        if (mScaleX != 1 || mScaleY != 1) {
            drawImage(theImage, theX, theY, new Rect(0, 0, theImage.mWidth, theImage.mHeight));
            return;
        }

        theX += (int) mTransX;
        theY += (int) mTransY;

        Rect aDestRect = new Rect(theX, theY, theImage.getWidth(), theImage.getHeight()).intersection(mClipRect);
        Rect aSrcRect = new Rect(aDestRect.mX - theX, aDestRect.mY - theY, aDestRect.mWidth, aDestRect.mHeight);

        if ((aSrcRect.mWidth > 0) && (aSrcRect.mHeight > 0)) {
            mDestImage.blt(theImage, aDestRect.mX, aDestRect.mY, aSrcRect, mColorizeImages ? mColor : Color.White, mDrawMode, mLinearBlend);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:682 (Graphics::DrawImage(Image*, int, int, Rect)). */
    public void drawImage(Image theImage, int theX, int theY, Rect theSrcRect) {
        if ((theSrcRect.mX + theSrcRect.mWidth > theImage.getWidth())
                || (theSrcRect.mY + theSrcRect.mHeight > theImage.getHeight())) {
            return;
        }

        theX += (int) mTransX;
        theY += (int) mTransY;

        if (mScaleX != 1 || mScaleY != 1) {
            Rect aDestRect = new Rect((int) (mScaleOrigX + Math.floor((theX - mScaleOrigX) * mScaleX)),
                    (int) (mScaleOrigY + Math.floor((theY - mScaleOrigY) * mScaleY)),
                    (int) Math.ceil(theSrcRect.mWidth * mScaleX),
                    (int) Math.ceil(theSrcRect.mHeight * mScaleY));
            mDestImage.stretchBlt(theImage, aDestRect, theSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, mFastStretch);
            return;
        }

        Rect aDestRect = new Rect(theX, theY, theSrcRect.mWidth, theSrcRect.mHeight).intersection(mClipRect);
        Rect aSrcRect = new Rect(theSrcRect.mX + aDestRect.mX - theX, theSrcRect.mY + aDestRect.mY - theY, aDestRect.mWidth, aDestRect.mHeight);

        if ((aSrcRect.mWidth > 0) && (aSrcRect.mHeight > 0)) {
            mDestImage.blt(theImage, aDestRect.mX, aDestRect.mY, aSrcRect, mColorizeImages ? mColor : Color.White, mDrawMode, mLinearBlend);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:708 (Graphics::DrawImageMirror(Image*, int, int, bool)). */
    public void drawImageMirror(Image theImage, int theX, int theY) {
        drawImageMirror(theImage, theX, theY, new Rect(0, 0, theImage.mWidth, theImage.mHeight), true);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:708 (Graphics::DrawImageMirror(Image*, int, int, bool)). */
    public void drawImageMirror(Image theImage, int theX, int theY, boolean mirror) {
        drawImageMirror(theImage, theX, theY, new Rect(0, 0, theImage.mWidth, theImage.mHeight), mirror);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:713 (Graphics::DrawImageMirror(Image*, int, int, Rect, bool)); {@code mirror} defaults to true. */
    public void drawImageMirror(Image theImage, int theX, int theY, Rect theSrcRect) {
        drawImageMirror(theImage, theX, theY, theSrcRect, true);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:713 (Graphics::DrawImageMirror(Image*, int, int, Rect, bool)). */
    public void drawImageMirror(Image theImage, int theX, int theY, Rect theSrcRect, boolean mirror) {
        if (!mirror) {
            drawImage(theImage, theX, theY, theSrcRect);
            return;
        }

        theX += (int) mTransX;
        theY += (int) mTransY;

        if ((theSrcRect.mX + theSrcRect.mWidth > theImage.getWidth())
                || (theSrcRect.mY + theSrcRect.mHeight > theImage.getHeight())) {
            return;
        }

        Rect aDestRect = new Rect(theX, theY, theSrcRect.mWidth, theSrcRect.mHeight).intersection(mClipRect);

        int aTotalClip = theSrcRect.mWidth - aDestRect.mWidth;
        int aLeftClip = aDestRect.mX - theX;
        int aRightClip = aTotalClip - aLeftClip;

        Rect aSrcRect = new Rect(theSrcRect.mX + aRightClip, theSrcRect.mY + aDestRect.mY - theY, aDestRect.mWidth, aDestRect.mHeight);

        if ((aSrcRect.mWidth > 0) && (aSrcRect.mHeight > 0)) {
            mDestImage.bltMirror(theImage, aDestRect.mX, aDestRect.mY, aSrcRect, mColorizeImages ? mColor : Color.White, mDrawMode, mLinearBlend);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:745 (Graphics::DrawImageMirror(Image*, Rect, Rect, bool)); {@code mirror} defaults to true. */
    public void drawImageMirror(Image theImage, Rect theDestRect, Rect theSrcRect) {
        drawImageMirror(theImage, theDestRect, theSrcRect, true);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:745 (Graphics::DrawImageMirror(Image*, Rect, Rect, bool)). */
    public void drawImageMirror(Image theImage, Rect theDestRect, Rect theSrcRect, boolean mirror) {
        if (!mirror) {
            drawImage(theImage, theDestRect, theSrcRect);
            return;
        }

        Rect aDestRect = new Rect(theDestRect.mX + (int) mTransX, theDestRect.mY + (int) mTransY, theDestRect.mWidth, theDestRect.mHeight);

        mDestImage.stretchBltMirror(theImage, aDestRect, theSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, mFastStretch);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:756 (Graphics::DrawImage(Image*, int, int, int, int)). */
    public void drawImage(Image theImage, int theX, int theY, int theStretchedWidth, int theStretchedHeight) {
        Rect aDestRect = new Rect(theX + (int) mTransX, theY + (int) mTransY, theStretchedWidth, theStretchedHeight);
        Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);

        mDestImage.stretchBlt(theImage, aDestRect, aSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, mFastStretch);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:765 (Graphics::DrawImage(Image*, Rect, Rect)). */
    public void drawImage(Image theImage, Rect theDestRect, Rect theSrcRect) {
        Rect aDestRect = new Rect(theDestRect.mX + (int) mTransX, theDestRect.mY + (int) mTransY, theDestRect.mWidth, theDestRect.mHeight);

        mDestImage.stretchBlt(theImage, aDestRect, theSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, mFastStretch);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:773 (Graphics::DrawImageF(Image*, float, float)). */
    public void drawImageF(Image theImage, float theX, float theY) {
        theX += mTransX;
        theY += mTransY;

        Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);
        mDestImage.bltF(theImage, theX, theY, aSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:783 (Graphics::DrawImageF(Image*, float, float, Rect)). */
    public void drawImageF(Image theImage, float theX, float theY, Rect theSrcRect) {
        theX += mTransX;
        theY += mTransY;

        mDestImage.bltF(theImage, theX, theY, theSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:792 (Graphics::DrawImageRotated(Image*, int, int, double, const Rect*)). */
    public void drawImageRotated(Image theImage, int theX, int theY, double theRot) {
        drawImageRotated(theImage, theX, theY, theRot, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:792 (Graphics::DrawImageRotated(Image*, int, int, double, const Rect*)). */
    public void drawImageRotated(Image theImage, int theX, int theY, double theRot, Rect theSrcRect) {
        if (theSrcRect == null) {
            int aRotCenterX = theImage.getWidth() / 2;
            int aRotCenterY = theImage.getHeight() / 2;
            drawImageRotatedF(theImage, theX, theY, theRot, aRotCenterX, aRotCenterY, null);
        } else {
            int aRotCenterX = theSrcRect.mWidth / 2;
            int aRotCenterY = theSrcRect.mHeight / 2;
            drawImageRotatedF(theImage, theX, theY, theRot, aRotCenterX, aRotCenterY, theSrcRect);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:809 (Graphics::DrawImageRotatedF(Image*, float, float, double, const Rect*)). */
    public void drawImageRotatedF(Image theImage, float theX, float theY, double theRot) {
        drawImageRotatedF(theImage, theX, theY, theRot, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:809 (Graphics::DrawImageRotatedF(Image*, float, float, double, const Rect*)). */
    public void drawImageRotatedF(Image theImage, float theX, float theY, double theRot, Rect theSrcRect) {
        if (theSrcRect == null) {
            float aRotCenterX = theImage.getWidth() / 2.0f;
            float aRotCenterY = theImage.getHeight() / 2.0f;
            drawImageRotatedF(theImage, theX, theY, theRot, aRotCenterX, aRotCenterY, null);
        } else {
            float aRotCenterX = theSrcRect.mWidth / 2.0f;
            float aRotCenterY = theSrcRect.mHeight / 2.0f;
            drawImageRotatedF(theImage, theX, theY, theRot, aRotCenterX, aRotCenterY, theSrcRect);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:820 (Graphics::DrawImageRotated(Image*, int, int, double, int, int, const Rect*)). */
    public void drawImageRotated(Image theImage, int theX, int theY, double theRot, int theRotCenterX, int theRotCenterY) {
        drawImageRotated(theImage, theX, theY, theRot, theRotCenterX, theRotCenterY, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:820 (Graphics::DrawImageRotated(Image*, int, int, double, int, int, const Rect*)). */
    public void drawImageRotated(Image theImage, int theX, int theY, double theRot, int theRotCenterX, int theRotCenterY, Rect theSrcRect) {
        drawImageRotatedF(theImage, theX, theY, theRot, theRotCenterX, theRotCenterY, theSrcRect);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:825 (Graphics::DrawImageRotatedF(Image*, float, float, double, float, float, const Rect*)). */
    public void drawImageRotatedF(Image theImage, float theX, float theY, double theRot, float theRotCenterX, float theRotCenterY) {
        drawImageRotatedF(theImage, theX, theY, theRot, theRotCenterX, theRotCenterY, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:825 (Graphics::DrawImageRotatedF(Image*, float, float, double, float, float, const Rect*)). */
    public void drawImageRotatedF(Image theImage, float theX, float theY, double theRot, float theRotCenterX, float theRotCenterY, Rect theSrcRect) {
        theX += mTransX;
        theY += mTransY;

        if (theSrcRect == null) {
            Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);
            mDestImage.bltRotated(theImage, theX, theY, aSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, theRot, theRotCenterX, theRotCenterY);
        } else {
            mDestImage.bltRotated(theImage, theX, theY, theSrcRect, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, theRot, theRotCenterX, theRotCenterY);
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:838 (Graphics::DrawImageMatrix(Image*, const SexyMatrix3&, float, float)). */
    public void drawImageMatrix(Image theImage, SexyMatrix3 theMatrix) {
        drawImageMatrix(theImage, theMatrix, 0, 0);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:838 (Graphics::DrawImageMatrix(Image*, const SexyMatrix3&, float, float)). */
    public void drawImageMatrix(Image theImage, SexyMatrix3 theMatrix, float x, float y) {
        Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);
        mDestImage.bltMatrix(theImage, x + mTransX, y + mTransY, theMatrix, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, aSrcRect, mLinearBlend);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:844 (Graphics::DrawImageMatrix(Image*, const SexyMatrix3&, const Rect&, float, float)). */
    public void drawImageMatrix(Image theImage, SexyMatrix3 theMatrix, Rect theSrcRect) {
        drawImageMatrix(theImage, theMatrix, theSrcRect, 0, 0);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:844 (Graphics::DrawImageMatrix(Image*, const SexyMatrix3&, const Rect&, float, float)). */
    public void drawImageMatrix(Image theImage, SexyMatrix3 theMatrix, Rect theSrcRect, float x, float y) {
        mDestImage.bltMatrix(theImage, x + mTransX, y + mTransY, theMatrix, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, theSrcRect, mLinearBlend);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:858 (Graphics::DrawImageTransformHelper). */
    private void drawImageTransformHelper(Image theImage, Transform theTransform, Rect theSrcRect, float x, float y, boolean useFloat) {
        if (theTransform.mComplex || (mDestImage.is3D() && useFloat)) {
            drawImageMatrix(theImage, theTransform.getMatrix(), theSrcRect, x, y);
            return;
        }

        // Translate into appropriate graphics call
        float w2 = theSrcRect.mWidth / 2.0f;
        float h2 = theSrcRect.mHeight / 2.0f;

        if (theTransform.mHaveRot) {
            float rx = w2 - theTransform.mTransX1;
            float ry = h2 - theTransform.mTransY1;

            x = x + theTransform.mTransX2 - rx + 0.5f;
            y = y + theTransform.mTransY2 - ry + 0.5f;

            if (useFloat) {
                drawImageRotatedF(theImage, x, y, theTransform.mRot, rx, ry, theSrcRect);
            } else {
                drawImageRotated(theImage, (int) x, (int) y, theTransform.mRot, (int) rx, (int) ry, theSrcRect);
            }
        } else if (theTransform.mHaveScale) {
            boolean mirror = false;
            if (theTransform.mScaleX == -1) {
                if (theTransform.mScaleY == 1) {
                    x = x + theTransform.mTransX1 + theTransform.mTransX2 - w2 + 0.5f;
                    y = y + theTransform.mTransY1 + theTransform.mTransY2 - h2 + 0.5f;
                    drawImageMirror(theImage, (int) x, (int) y, theSrcRect);
                    return;
                }
                mirror = true;
            }

            float sw = w2 * theTransform.mScaleX;
            float sh = h2 * theTransform.mScaleY;

            x = x + theTransform.mTransX2 - sw;
            y = y + theTransform.mTransY2 - sh;

            Rect aDestRect = new Rect((int) x, (int) y, (int) (sw * 2), (int) (sh * 2));
            drawImageMirror(theImage, aDestRect, theSrcRect, mirror);
        } else {
            x = x + theTransform.mTransX1 + theTransform.mTransX2 - w2 + 0.5f;
            y = y + theTransform.mTransY1 + theTransform.mTransY2 - h2 + 0.5f;

            if (useFloat) {
                drawImageF(theImage, x, y, theSrcRect);
            } else {
                drawImage(theImage, (int) x, (int) y, theSrcRect);
            }
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:888 (Graphics::DrawImageTransform). */
    public void drawImageTransform(Image theImage, Transform theTransform) {
        drawImageTransformHelper(theImage, theTransform, new Rect(0, 0, theImage.mWidth, theImage.mHeight), 0, 0, false);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:893 (Graphics::DrawImageTransformF). */
    public void drawImageTransformF(Image theImage, Transform theTransform) {
        drawImageTransformHelper(theImage, theTransform, new Rect(0, 0, theImage.mWidth, theImage.mHeight), 0, 0, true);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:898 (Graphics::DrawImageTransform). */
    public void drawImageTransform(Image theImage, Transform theTransform, float x, float y) {
        drawImageTransformHelper(theImage, theTransform, new Rect(0, 0, theImage.mWidth, theImage.mHeight), x, y, false);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:898 (Graphics::DrawImageTransform). */
    public void drawImageTransform(Image theImage, Transform theTransform, Rect theSrcRect, float x, float y) {
        drawImageTransformHelper(theImage, theTransform, theSrcRect, x, y, false);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:903 (Graphics::DrawImageTransformF). */
    public void drawImageTransformF(Image theImage, Transform theTransform, float x, float y) {
        drawImageTransformHelper(theImage, theTransform, new Rect(0, 0, theImage.mWidth, theImage.mHeight), x, y, true);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:903 (Graphics::DrawImageTransformF). */
    public void drawImageTransformF(Image theImage, Transform theTransform, Rect theSrcRect, float x, float y) {
        drawImageTransformHelper(theImage, theTransform, theSrcRect, x, y, true);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:908 (Graphics::DrawTriangleTex). */
    public void drawTriangleTex(Image theTexture, TriVertex v1, TriVertex v2, TriVertex v3) {
        TriVertex[][] v = {{v1, v2, v3}};
        mDestImage.bltTrianglesTex(theTexture, v, 1, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, mTransX, mTransY, mLinearBlend);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:914 (Graphics::DrawTrianglesTex). */
    public void drawTrianglesTex(Image theTexture, TriVertex[][] theVertices, int theNumTriangles) {
        mDestImage.bltTrianglesTex(theTexture, theVertices, theNumTriangles, mClipRect, mColorizeImages ? mColor : Color.White, mDrawMode, mTransX, mTransY, mLinearBlend);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:919 (Graphics::ClearClipRect). */
    public void clearClipRect() {
        mClipRect = new Rect(0, 0, mDestImage.getWidth(), mDestImage.getHeight());
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:924 (Graphics::SetClipRect). */
    public void setClipRect(int theX, int theY, int theWidth, int theHeight) {
        mClipRect = new Rect(0, 0, mDestImage.getWidth(), mDestImage.getHeight())
                .intersection(new Rect(theX + (int) mTransX, theY + (int) mTransY, theWidth, theHeight));
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:930 (Graphics::SetClipRect(Rect)). */
    public void setClipRect(Rect theRect) {
        setClipRect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }

    /** Java API contract: the current clip rectangle. */
    public Rect getClipRect() {
        return mClipRect;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:935 (Graphics::ClipRect). */
    public void clipRect(int theX, int theY, int theWidth, int theHeight) {
        mClipRect = mClipRect.intersection(new Rect(theX + (int) mTransX, theY + (int) mTransY, theWidth, theHeight));
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:940 (Graphics::ClipRect(Rect)). */
    public void clipRect(Rect theRect) {
        clipRect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:945 (Graphics::Translate). */
    public void translate(int theTransX, int theTransY) {
        mTransX += theTransX;
        mTransY += theTransY;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:950 (Graphics::TranslateF). */
    public void translateF(float theTransX, float theTransY) {
        mTransX += theTransX;
        mTransY += theTransY;
    }

    /** Java API contract: the current X translation. */
    public float getTransX() {
        return mTransX;
    }

    /** Java API contract: the current Y translation. */
    public float getTransY() {
        return mTransY;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:955 (Graphics::SetScale). */
    public void setScale(float theScaleX, float theScaleY, float theOrigX, float theOrigY) {
        mScaleX = theScaleX;
        mScaleY = theScaleY;
        mScaleOrigX = theOrigX + mTransX;
        mScaleOrigY = theOrigY + mTransY;
    }

    /**
     * Java API contract: draws {@code theString} with a one-pixel drop shadow in
     * {@code theShadowColor} followed by the string itself in the current color.
     */
    public void drawStringShaded(String theString, int theX, int theY, Color theShadowColor) {
        Color anOrigColor = mColor;
        setColor(theShadowColor);
        drawString(theString, theX + 1, theY + 1);
        setColor(anOrigColor);
        drawString(theString, theX, theY);
    }

    /** Java API contract: black drop shadow variant of {@link #drawStringShaded(String, int, int, Color)}. */
    public void drawStringShaded(String theString, int theX, int theY) {
        drawStringShaded(theString, theX, theY, Color.Black);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:961 (Graphics::StringWidth). */
    public int stringWidth(String theString) {
        return mFont.stringWidth(theString);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:966 (Graphics::DrawImageBox). */
    public void drawImageBox(Rect theDest, Image theComponentImage) {
        drawImageBox(new Rect(0, 0, theComponentImage.mWidth, theComponentImage.mHeight), theDest, theComponentImage);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:971 (Graphics::DrawImageBox). */
    public void drawImageBox(Rect theSrc, Rect theDest, Image theComponentImage) {
        if (theSrc.mWidth <= 0 || theSrc.mHeight <= 0) {
            return;
        }

        int cw = theSrc.mWidth / 3;
        int ch = theSrc.mHeight / 3;
        int cx = theSrc.mX;
        int cy = theSrc.mY;
        int cmw = theSrc.mWidth - cw * 2;
        int cmh = theSrc.mHeight - ch * 2;

        // Draw 4 corners
        drawImage(theComponentImage, theDest.mX, theDest.mY, new Rect(cx, cy, cw, ch));
        drawImage(theComponentImage, theDest.mX + theDest.mWidth - cw, theDest.mY, new Rect(cx + cw + cmw, cy, cw, ch));
        drawImage(theComponentImage, theDest.mX, theDest.mY + theDest.mHeight - ch, new Rect(cx, cy + ch + cmh, cw, ch));
        drawImage(theComponentImage, theDest.mX + theDest.mWidth - cw, theDest.mY + theDest.mHeight - ch, new Rect(cx + cw + cmw, cy + ch + cmh, cw, ch));

        // Draw top and bottom
        Graphics aVertClip = new Graphics(this);
        aVertClip.clipRect(theDest.mX + cw, theDest.mY, theDest.mWidth - cw * 2, theDest.mHeight);
        int aCol;
        int aRow;
        for (aCol = 0; aCol < (theDest.mWidth - cw * 2 + cmw - 1) / cmw; aCol++) {
            aVertClip.drawImage(theComponentImage, theDest.mX + cw + aCol * cmw, theDest.mY, new Rect(cx + cw, cy, cmw, ch));
            aVertClip.drawImage(theComponentImage, theDest.mX + cw + aCol * cmw, theDest.mY + theDest.mHeight - ch, new Rect(cx + cw, cy + ch + cmh, cmw, ch));
        }

        // Draw sides
        Graphics aHorzClip = new Graphics(this);
        aHorzClip.clipRect(theDest.mX, theDest.mY + ch, theDest.mWidth, theDest.mHeight - ch * 2);
        for (aRow = 0; aRow < (theDest.mHeight - ch * 2 + cmh - 1) / cmh; aRow++) {
            aHorzClip.drawImage(theComponentImage, theDest.mX, theDest.mY + ch + aRow * cmh, new Rect(cx, cy + ch, cw, cmh));
            aHorzClip.drawImage(theComponentImage, theDest.mX + theDest.mWidth - cw, theDest.mY + ch + aRow * cmh, new Rect(cx + cw + cmw, cy + ch, cw, cmh));
        }

        // Draw middle
        Graphics aMidClip = new Graphics(this);
        aMidClip.clipRect(theDest.mX + cw, theDest.mY + ch, theDest.mWidth - cw * 2, theDest.mHeight - ch * 2);
        for (aCol = 0; aCol < (theDest.mWidth - cw * 2 + cmw - 1) / cmw; aCol++) {
            for (aRow = 0; aRow < (theDest.mHeight - ch * 2 + cmh - 1) / cmh; aRow++) {
                aMidClip.drawImage(theComponentImage, theDest.mX + cw + aCol * cmw, theDest.mY + ch + aRow * cmh, new Rect(cx + cw, cy + ch, cmw, cmh));
            }
        }
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1021 (Graphics::DrawImageCel). */
    public void drawImageCel(Image theImageStrip, int theX, int theY, int theCel) {
        drawImageCel(theImageStrip, theX, theY, theCel % theImageStrip.mNumCols, theCel / theImageStrip.mNumCols);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1026 (Graphics::DrawImageCel). */
    public void drawImageCel(Image theImageStrip, Rect theDestRect, int theCel) {
        drawImageCel(theImageStrip, theDestRect, theCel % theImageStrip.mNumCols, theCel / theImageStrip.mNumCols);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1031 (Graphics::DrawImageCel). */
    public void drawImageCel(Image theImageStrip, int theX, int theY, int theCelCol, int theCelRow) {
        if (theCelRow < 0 || theCelCol < 0 || theCelRow >= theImageStrip.mNumRows || theCelCol >= theImageStrip.mNumCols) {
            return;
        }

        int aCelWidth = theImageStrip.mWidth / theImageStrip.mNumCols;
        int aCelHeight = theImageStrip.mHeight / theImageStrip.mNumRows;
        Rect aSrcRect = new Rect(aCelWidth * theCelCol, aCelHeight * theCelRow, aCelWidth, aCelHeight);

        drawImage(theImageStrip, theX, theY, aSrcRect);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1074 (Graphics::DrawImageAnim). */
    public void drawImageAnim(Image theImageAnim, int theX, int theY, int theTime) {
        drawImageCel(theImageAnim, theX, theY, theImageAnim.getAnimCel(theTime));
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1079 (Graphics::DrawImageCel). */
    public void drawImageCel(Image theImageStrip, Rect theDestRect, int theCelCol, int theCelRow) {
        if (theCelRow < 0 || theCelCol < 0 || theCelRow >= theImageStrip.mNumRows || theCelCol >= theImageStrip.mNumCols) {
            return;
        }

        int aCelWidth = theImageStrip.mWidth / theImageStrip.mNumCols;
        int aCelHeight = theImageStrip.mHeight / theImageStrip.mNumRows;
        Rect aSrcRect = new Rect(aCelWidth * theCelCol, aCelHeight * theCelRow, aCelWidth, aCelHeight);

        drawImage(theImageStrip, theDestRect, aSrcRect);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1091 (Graphics::WriteString). */
    public int writeString(String theString, int theX, int theY) {
        return writeString(theString, theX, theY, -1, 0, true, 0, -1, -1);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1091 (Graphics::WriteString). */
    public int writeString(String theString, int theX, int theY, int theWidth, int theJustification,
                           boolean drawString, int theOffset, int theLength, int theOldColor) {
        if (theOldColor == -1) {
            theOldColor = mColor.toInt();
        }

        if (drawString) {
            switch (theJustification) {
                case 0:
                    theX += (theWidth - writeString(theString, theX, theY, theWidth, -1, false, theOffset, theLength, theOldColor)) / 2;
                    break;
                case 1:
                    theX += theWidth - writeString(theString, theX, theY, theWidth, -1, false, theOffset, theLength, theOldColor);
                    break;
                default:
                    break;
            }
        }

        int aLength;
        if (theLength < 0 || theOffset + theLength > theString.length()) {
            aLength = theString.length();
        } else {
            aLength = theOffset + theLength;
        }

        StringBuilder aString = new StringBuilder();
        int aXOffset = 0;

        for (int i = theOffset; i < aLength; i++) {
            if ((theString.charAt(i) == '^') && mWriteColoredString) {
                if (i + 1 < aLength && theString.charAt(i + 1) == '^') { // literal '^'
                    aString.append('^');
                    i++;
                } else if (i > aLength - 8) { // badly formatted color specification
                    break;
                } else { // change color instruction
                    int aColor = 0;
                    if (theString.charAt(i + 1) == 'o') {
                        if (theString.regionMatches(i + 1, "oldclr", 0, 6)) {
                            aColor = theOldColor;
                        }
                    } else {
                        for (int aDigitNum = 0; aDigitNum < 6; aDigitNum++) {
                            char aChar = theString.charAt(i + aDigitNum + 1);
                            int aVal = 0;

                            if ((aChar >= '0') && (aChar <= '9')) {
                                aVal = aChar - '0';
                            } else if ((aChar >= 'A') && (aChar <= 'F')) {
                                aVal = (aChar - 'A') + 10;
                            } else if ((aChar >= 'a') && (aChar <= 'f')) {
                                aVal = (aChar - 'a') + 10;
                            }

                            aColor += (aVal << ((5 - aDigitNum) * 4));
                        }
                    }

                    if (drawString) {
                        drawString(aString.toString(), theX + aXOffset, theY);
                        setColor(new Color((aColor >> 16) & 0xFF, (aColor >> 8) & 0xFF, (aColor) & 0xFF, getColor().mAlpha));
                    }

                    i += 7;

                    aXOffset += getFont().stringWidth(aString.toString());

                    aString.setLength(0);
                }
            } else {
                aString.append(theString.charAt(i));
            }
        }

        if (drawString) {
            drawString(aString.toString(), theX + aXOffset, theY);
        }

        aXOffset += getFont().stringWidth(aString.toString());

        return aXOffset;
    }

    private int writeWordWrappedHelper(String theString, int theX, int theY, int theWidth, int theJustification,
                                       boolean drawString, int theOffset, int theLength, int theOldColor, int theMaxChars) {
        if (theOffset + theLength > theMaxChars) {
            theLength = theMaxChars - theOffset;
            if (theLength <= 0) {
                return -1;
            }
        }

        return writeString(theString, theX, theY, theWidth, theJustification, drawString, theOffset, theLength, theOldColor);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1194 (Graphics::WriteWordWrapped). */
    public int writeWordWrapped(Rect theRect, String theLine) {
        return writeWordWrapped(theRect, theLine, -1, -1, (IntRef) null, -1, (IntRef) null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1194 (Graphics::WriteWordWrapped). */
    public int writeWordWrapped(Rect theRect, String theLine, int theLineSpacing, int theJustification,
                                IntRef theMaxWidth, int theMaxChars, IntRef theLastWidth) {
        Color anOrigColor = getColor();
        int anOrigColorInt = anOrigColor.toInt();
        if ((anOrigColorInt & 0xFF000000) == 0xFF000000) {
            anOrigColorInt &= ~0xFF000000;
        }

        if (theMaxChars < 0) {
            theMaxChars = theLine.length();
        }

        Font aFont = getFont();

        int aYOffset = aFont.getAscent() - aFont.getAscentPadding();

        if (theLineSpacing == -1) {
            theLineSpacing = aFont.getLineSpacing();
        }

        int aCurPos = 0;
        int aLineStartPos = 0;
        int aCurWidth = 0;
        int aCurChar = 0;
        int aPrevChar = 0;
        int aMaxWidth = 0;
        int anIndentX = 0;

        int aBreakDrawLen = -1;
        int aBreakResumePos = 0;
        boolean aBreakSkipSpaces = false;

        if (theLastWidth != null) {
            anIndentX = theLastWidth.value;
            aCurWidth = anIndentX;
        }

        while (aCurPos < theLine.length()) {
            int aCharStart = aCurPos;

            if (theLine.charAt(aCurPos) == '^' && mWriteColoredString) { // color modifier
                if (aCurPos + 1 < theLine.length()) {
                    if (theLine.charAt(aCurPos + 1) == '^') {
                        aCurPos++; // literal '^' -> just skip the extra '^'
                    } else {
                        aCurPos += 8;
                        continue; // skip the color specifier when calculating the width
                    }
                }
            }

            if (aCurPos >= theLine.length()) {
                break;
            }
            int aCurCharCode = theLine.codePointAt(aCurPos);
            aCurPos += Character.charCount(aCurCharCode);
            aCurChar = aCurCharCode;

            if (aCurChar == '\r') { // skip CR for CRLF/LF compatibility
                continue;
            }
            int aCharEnd = aCurPos;

            boolean aIsNewline = (aCurChar == '\n');
            boolean aIsSpace = !aIsNewline && (aCurChar == ' ');

            if (aIsSpace) {
                aBreakDrawLen = aCharStart - aLineStartPos;
                aBreakResumePos = aCharEnd;
                aBreakSkipSpaces = true;
            } else if (aIsNewline) {
                aCurWidth = theRect.mWidth + 1; // force word wrap
                aBreakDrawLen = aCharStart - aLineStartPos;
                aBreakResumePos = aCharEnd;
                aBreakSkipSpaces = false;
            }

            aCurWidth += aFont.charWidthKern(aCurChar, aPrevChar);

            if (!aIsSpace && !aIsNewline && Common.isAutoBreakChar(aCurChar)
                    && !Common.isClosingPunctuation(aCurChar)
                    && aCharStart > aLineStartPos
                    && !Common.isOpeningPunctuation(aPrevChar)) {
                aBreakDrawLen = aCharStart - aLineStartPos;
                aBreakResumePos = aCharStart;
                aBreakSkipSpaces = false;
            }
            aPrevChar = aCurChar;

            if (aCurWidth > theRect.mWidth) { // need to wrap
                int aWrittenWidth;
                if (aBreakDrawLen >= 0) {
                    int aPhysPos = theRect.mY + aYOffset + (int) mTransY;
                    if ((aPhysPos >= mClipRect.mY) && (aPhysPos < mClipRect.mY + mClipRect.mHeight + theLineSpacing)) {
                        writeWordWrappedHelper(theLine, theRect.mX + anIndentX, theRect.mY + aYOffset, theRect.mWidth,
                                theJustification, true, aLineStartPos, aBreakDrawLen, anOrigColorInt, theMaxChars);
                    }

                    aWrittenWidth = aCurWidth + anIndentX;

                    if (aWrittenWidth < 0) {
                        break;
                    }

                    aCurPos = aBreakResumePos;
                    if (aBreakSkipSpaces) {
                        while (aCurPos < theLine.length() && theLine.charAt(aCurPos) == ' ') {
                            aCurPos++;
                        }
                    }
                    aLineStartPos = aCurPos;
                } else {
                    int aDrawEnd = aCharStart;
                    if (aDrawEnd <= aLineStartPos) {
                        aDrawEnd = aCharEnd; // at least one char per line
                    }

                    aWrittenWidth = writeWordWrappedHelper(theLine, theRect.mX + anIndentX, theRect.mY + aYOffset, theRect.mWidth,
                            theJustification, true, aLineStartPos, aDrawEnd - aLineStartPos, anOrigColorInt, theMaxChars);

                    if (aWrittenWidth < 0) {
                        break;
                    }

                    if (theMaxWidth != null && aWrittenWidth > theMaxWidth.value) {
                        theMaxWidth.value = aWrittenWidth;
                    }
                    if (theLastWidth != null) {
                        theLastWidth.value = aWrittenWidth;
                    }

                    aCurPos = aDrawEnd;
                }

                aMaxWidth = Math.max(aMaxWidth, aWrittenWidth);

                aLineStartPos = aCurPos;
                aBreakDrawLen = -1;
                aCurWidth = 0;
                aPrevChar = 0;
                anIndentX = 0;
                aYOffset += theLineSpacing;
            }
        }

        if (aLineStartPos < theLine.length()) { // write the last piece
            int aWrittenWidth = writeWordWrappedHelper(theLine, theRect.mX + anIndentX, theRect.mY + aYOffset, theRect.mWidth,
                    theJustification, true, aLineStartPos, theLine.length() - aLineStartPos, anOrigColorInt, theMaxChars);

            if (aWrittenWidth >= 0) {
                aMaxWidth = Math.max(aMaxWidth, aWrittenWidth);

                if (theMaxWidth != null && aWrittenWidth > theMaxWidth.value) {
                    theMaxWidth.value = aWrittenWidth;
                }
                if (theLastWidth != null) {
                    theLastWidth.value = aWrittenWidth;
                }

                aYOffset += theLineSpacing;
            }
        } else if (aCurChar == '\n') {
            aYOffset += theLineSpacing;
            if (theLastWidth != null) {
                theLastWidth.value = 0;
            }
        }

        setColor(anOrigColor);

        if (theMaxWidth != null) {
            theMaxWidth.value = aMaxWidth;
        }

        // text block height = aYOffset - the extra trailing line spacing + the font descent
        return aYOffset + aFont.getDescent() - theLineSpacing;
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1376 (Graphics::DrawStringColor). */
    public int drawStringColor(String theLine, int theX, int theY) {
        return writeString(theLine, theX, theY, -1, -1, true, 0, -1, -1);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1376 (Graphics::DrawStringColor). */
    public int drawStringColor(String theLine, int theX, int theY, int theOldColor) {
        return writeString(theLine, theX, theY, -1, -1, true, 0, -1, theOldColor);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1380 (Graphics::DrawStringWordWrapped). */
    public int drawStringWordWrapped(String theLine, int theX, int theY) {
        return drawStringWordWrapped(theLine, theX, theY, 10000000, -1, -1, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1380 (Graphics::DrawStringWordWrapped). */
    public int drawStringWordWrapped(String theLine, int theX, int theY, int theWrapWidth) {
        return drawStringWordWrapped(theLine, theX, theY, theWrapWidth, -1, -1, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1380 (Graphics::DrawStringWordWrapped). */
    public int drawStringWordWrapped(String theLine, int theX, int theY, int theWrapWidth, int theLineSpacing, int theJustification, IntRef theMaxWidth) {
        int aYOffset = mFont.getAscent() - mFont.getAscentPadding();

        Rect aRect = new Rect(theX, theY - aYOffset, theWrapWidth, 0);
        return writeWordWrapped(aRect, theLine, theLineSpacing, theJustification, theMaxWidth, -1, null);
    }

    /** Java API contract: alias of {@link #drawStringWordWrapped(String, int, int, int, int, int, IntRef)}. */
    public int drawStringWrapped(String theLine, int theX, int theY, int theWrapWidth, int theLineSpacing, int theJustification, IntRef theMaxWidth) {
        return drawStringWordWrapped(theLine, theX, theY, theWrapWidth, theLineSpacing, theJustification, theMaxWidth);
    }

    /** Java API contract: alias of {@link #drawStringWordWrapped(String, int, int, int)}. */
    public int drawStringWrapped(String theLine, int theX, int theY, int theWrapWidth) {
        return drawStringWordWrapped(theLine, theX, theY, theWrapWidth);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1388 (Graphics::GetWordWrappedHeight). */
    public int getWordWrappedHeight(int theWidth, String theLine) {
        return getWordWrappedHeight(theWidth, theLine, -1, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:1388 (Graphics::GetWordWrappedHeight). */
    public int getWordWrappedHeight(int theWidth, String theLine, int theLineSpacing, IntRef theMaxWidth) {
        Graphics aTestG = new Graphics();
        aTestG.setFont(mFont);
        return aTestG.writeWordWrapped(new Rect(0, 0, theWidth, 0), theLine, theLineSpacing, -1, theMaxWidth, -1, null);
    }

    /** Ported from src/SexyAppFramework/graphics/Graphics.h:194 (Graphics::Is3D). */
    public boolean is3D() {
        return mIs3D;
    }
}
