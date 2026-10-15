package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Rect;

import java.util.HashMap;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/graphics/ImageFont.h:131 (class ActiveFontLayer) and
 * src/SexyAppFramework/graphics/ImageFont.cpp:1108 (ActiveFontLayer::ActiveFontLayer) /
 * :1113 (copy ctor).
 *
 * <p>An active (selected + point-size-resolved) view of a {@link FontLayer}: the base layer, the
 * image actually drawn (the base image at {@code mScale == 1} / matching point size, otherwise a
 * scaled copy), and the per-character source rectangles into that image.
 *
 * <p>Java deviation: {@code std::unique_ptr<Image> mOwnedScaledImage} becomes a plain nullable field;
 * the copy constructor shares the reference instead of deep-copying via the app (documented in the
 * port notes). This is only exercised by the rare scaled-font path.
 */
public class ActiveFontLayer {

    public FontLayer mBaseFontLayer;
    public Image mScaledImage;
    public Image mOwnedScaledImage;
    public Map<Integer, Rect> mScaledCharImageRects;

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1108 (ActiveFontLayer::ActiveFontLayer). */
    public ActiveFontLayer() {
        mBaseFontLayer = null;
        mScaledImage = null;
        mOwnedScaledImage = null;
        mScaledCharImageRects = new HashMap<>();
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1113 (ActiveFontLayer copy ctor). */
    public ActiveFontLayer(ActiveFontLayer theActiveFontLayer) {
        mBaseFontLayer = theActiveFontLayer.mBaseFontLayer;
        mScaledImage = theActiveFontLayer.mScaledImage;
        mOwnedScaledImage = theActiveFontLayer.mOwnedScaledImage;
        mScaledCharImageRects = new HashMap<>(theActiveFontLayer.mScaledCharImageRects);
    }
}
