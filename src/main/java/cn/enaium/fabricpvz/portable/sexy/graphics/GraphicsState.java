package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Ported from src/SexyAppFramework/graphics/Graphics.h:51 (class GraphicsState).
 *
 * <p>Holds every piece of drawing state that {@link Graphics} carries: the destination image, the
 * destination translation, the "in progress" scale, the clip rectangle, the current color, font,
 * draw mode and the assorted flags. The public fields keep their upstream names.
 *
 * <p>Upstream also declares {@code static Image GraphicsState::mStaticImage;} (defined at
 * src/SexyAppFramework/graphics/Graphics.cpp:31) which is used as a no-op draw target when a
 * {@code Graphics} is constructed without a destination image. It is reproduced here as
 * {@link #mStaticImage}.
 */
public class GraphicsState {

    /** Upstream {@code GraphicsState::mStaticImage} (Graphics.cpp:31). */
    public static final Image mStaticImage = new Image() {
    };

    public Image mDestImage;
    public float mTransX;
    public float mTransY;
    public float mScaleX;
    public float mScaleY;
    public float mScaleOrigX;
    public float mScaleOrigY;
    public Rect mClipRect;
    public Color mColor;
    public Font mFont;
    public int mDrawMode;
    public boolean mColorizeImages;
    public boolean mFastStretch;
    public boolean mWriteColoredString;
    public boolean mLinearBlend;
    public boolean mIs3D;

    /** Ported from src/SexyAppFramework/graphics/Graphics.cpp:33 (GraphicsState::CopyStateFrom). */
    public void copyStateFrom(GraphicsState theState) {
        mDestImage = theState.mDestImage;
        mTransX = theState.mTransX;
        mTransY = theState.mTransY;
        mClipRect = theState.mClipRect;
        mFont = theState.mFont;
        mColor = theState.mColor;
        mDrawMode = theState.mDrawMode;
        mColorizeImages = theState.mColorizeImages;
        mFastStretch = theState.mFastStretch;
        mWriteColoredString = theState.mWriteColoredString;
        mLinearBlend = theState.mLinearBlend;
        mScaleX = theState.mScaleX;
        mScaleY = theState.mScaleY;
        mScaleOrigX = theState.mScaleOrigX;
        mScaleOrigY = theState.mScaleOrigY;
        mIs3D = theState.mIs3D;
    }
}
