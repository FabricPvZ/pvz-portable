package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/graphics/ImageFont.h:58 (class FontLayer) and
 * src/SexyAppFramework/graphics/ImageFont.cpp:103 (FontLayer::FontLayer(FontData*)) /
 * :120 (FontLayer::FontLayer(const FontLayer&amp;)) / :150 (FontLayer::GetCharData).
 *
 * <p>One renderable layer of an {@link ImageFont} (a font may stack several, e.g. an outline layer
 * plus the main layer). The public fields keep their upstream names.
 *
 * <p>Java deviations: {@code mImage} is the port's {@link SharedImageRef} handle (nullable image);
 * {@code mCharDataMap}
 * is a {@link LinkedHashMap} keyed by {@code Integer} code points (upstream {@code std::map}); the
 * unused {@code std::map<std::string,std::string> mExtendedInfo} is a {@link Map}. Upstream did not
 * initialise {@code mUseAlphaCorrection} in either constructor (self-documented UB); it defaults to
 * {@code false} here.
 */
public class FontLayer {

    public FontData mFontData;
    public Map<String, String> mExtendedInfo;
    public String mLayerName;
    public List<String> mRequiredTags;
    public List<String> mExcludedTags;
    public Map<Integer, CharData> mCharDataMap;
    public Color mColorMult;
    public Color mColorAdd;
    public SharedImageRef mImage;
    public int mDrawMode;
    public Point mOffset;
    public int mSpacing;
    public int mMinPointSize;
    public int mMaxPointSize;
    public int mPointSize;
    public int mAscent;
    public int mAscentPadding; // How much space is above the avg uppercase char
    public int mHeight;
    public int mDefaultHeight; // Max height of font character image rects
    public int mLineSpacingOffset; // This plus height should get added between lines
    public int mBaseOrder;
    public boolean mUseAlphaCorrection;

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:103 (FontLayer::FontLayer(FontData*)). */
    public FontLayer(FontData theFontData) {
        mFontData = theFontData;
        mExtendedInfo = new LinkedHashMap<>();
        mLayerName = "";
        mRequiredTags = new ArrayList<>();
        mExcludedTags = new ArrayList<>();
        mCharDataMap = new LinkedHashMap<>();
        mColorMult = new Color(255, 255, 255, 255);
        mColorAdd = new Color(0, 0, 0, 0);
        mImage = new SharedImageRef();
        mDrawMode = -1;
        mOffset = new Point(0, 0);
        mSpacing = 0;
        mPointSize = 0;
        mAscent = 0;
        mAscentPadding = 0;
        mMinPointSize = -1;
        mMaxPointSize = -1;
        mHeight = 0;
        mDefaultHeight = 0;
        mLineSpacingOffset = 0;
        mBaseOrder = 0;
        mUseAlphaCorrection = false;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:120 (FontLayer::FontLayer(const FontLayer&amp;)). */
    public FontLayer(FontLayer theFontLayer) {
        mFontData = theFontLayer.mFontData;
        mExtendedInfo = new LinkedHashMap<>(theFontLayer.mExtendedInfo);
        mLayerName = theFontLayer.mLayerName;
        mRequiredTags = new ArrayList<>(theFontLayer.mRequiredTags);
        mExcludedTags = new ArrayList<>(theFontLayer.mExcludedTags);
        mCharDataMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, CharData> anEntry : theFontLayer.mCharDataMap.entrySet()) {
            mCharDataMap.put(anEntry.getKey(), copyCharData(anEntry.getValue()));
        }
        mColorMult = new Color(theFontLayer.mColorMult.mRed, theFontLayer.mColorMult.mGreen, theFontLayer.mColorMult.mBlue, theFontLayer.mColorMult.mAlpha);
        mColorAdd = new Color(theFontLayer.mColorAdd.mRed, theFontLayer.mColorAdd.mGreen, theFontLayer.mColorAdd.mBlue, theFontLayer.mColorAdd.mAlpha);
        mImage = theFontLayer.mImage;
        mDrawMode = theFontLayer.mDrawMode;
        mOffset = new Point(theFontLayer.mOffset.mX, theFontLayer.mOffset.mY);
        mSpacing = theFontLayer.mSpacing;
        mMinPointSize = theFontLayer.mMinPointSize;
        mMaxPointSize = theFontLayer.mMaxPointSize;
        mPointSize = theFontLayer.mPointSize;
        mAscent = theFontLayer.mAscent;
        mAscentPadding = theFontLayer.mAscentPadding;
        mHeight = theFontLayer.mHeight;
        mDefaultHeight = theFontLayer.mDefaultHeight;
        mLineSpacingOffset = theFontLayer.mLineSpacingOffset;
        mBaseOrder = theFontLayer.mBaseOrder;
        // @Minerscale mUseAlphaCorrection wasn't initialised in the copy constructor causing UB
        mUseAlphaCorrection = theFontLayer.mUseAlphaCorrection;
    }

    private static CharData copyCharData(CharData theCharData) {
        CharData aCopy = new CharData();
        aCopy.mImageRect = new Rect(theCharData.mImageRect.mX, theCharData.mImageRect.mY, theCharData.mImageRect.mWidth, theCharData.mImageRect.mHeight);
        aCopy.mOffset = new Point(theCharData.mOffset.mX, theCharData.mOffset.mY);
        aCopy.mKerningOffsets = new java.util.HashMap<>(theCharData.mKerningOffsets);
        aCopy.mWidth = theCharData.mWidth;
        aCopy.mOrder = theCharData.mOrder;
        return aCopy;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:150 (FontLayer::GetCharData). */
    public CharData getCharData(int theChar) {
        CharData aCharData = mCharDataMap.get(theChar);
        if (aCharData == null) {
            aCharData = new CharData();
            mCharDataMap.put(theChar, aCharData);
        }
        return aCharData;
    }
}
