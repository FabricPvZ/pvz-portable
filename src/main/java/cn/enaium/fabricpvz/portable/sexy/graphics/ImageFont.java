package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.parsers.DataElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/graphics/ImageFont.h:163 (class ImageFont : public _Font) and
 * src/SexyAppFramework/graphics/ImageFont.cpp:1108-1705.
 *
 * <p>Bitmap font implementation. Fonts are described by PopCap descriptor files
 * ({@code data/*.txt}) that name a glyph-sheet image per layer and the per-character source
 * rectangles ({@link FontData}); they are parsed by {@code FontData}'s descriptor interpreter. A
 * layer may also scale a base image for a point size / {@code mScale} (rarely used).
 *
 * <p>Draws happen by blitting the (possibly pre-scaled) layer image's glyph rectangles through
 * {@link Graphics} with the current tint ({@code colorizeImages}), with per-layer color
 * {@code clamp(color*mult/255 + add, 255)} and the {@code baseOrder + order} bucket sort — no color
 * is baked into the uploaded texture.
 *
 * <p>Java deviations: the {@code RenderCommand} pool is per-call heap allocation;
 * {@code char32_t} becomes {@code int} code points; {@code palletize()} is dropped (the port has no
 * CPU palette path; layer images are {@link SharedImageRef} handles).
 */
public class ImageFont extends Font {

    private static final int POOL_SIZE = 4096;

    public FontData mFontData;
    public int mPointSize;
    public List<String> mTagVector;

    public boolean mActiveListValid;
    public List<ActiveFontLayer> mActiveLayerList;
    public double mScale;
    public boolean mForceScaledImagesWhite;

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1133 (ImageFont::ImageFont(SexyAppBase*, const std::string&)). */
    public ImageFont(SexyAppBase theSexyApp, String theFontDescFileName) {
        mScale = 1.0;
        mFontData = new FontData();
        mFontData.ref();
        mFontData.load(theSexyApp, theFontDescFileName);
        mPointSize = mFontData.mDefaultPointSize;
        mTagVector = new ArrayList<>();
        mActiveLayerList = new ArrayList<>();
        generateActiveFontLayers();
        mActiveListValid = true;
        mForceScaledImagesWhite = false;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1145 (ImageFont::ImageFont(Image*)). */
    public ImageFont(Image theFontImage) {
        mScale = 1.0;
        mFontData = new FontData();
        mFontData.ref();
        mFontData.mInitialized = true;
        mPointSize = mFontData.mDefaultPointSize;
        mTagVector = new ArrayList<>();
        mActiveLayerList = new ArrayList<>();
        mActiveListValid = false;
        mForceScaledImagesWhite = false;

        FontLayer aFontLayer = new FontLayer(mFontData);
        mFontData.mFontLayerList.add(aFontLayer);
        mFontData.mFontLayerMap.put("", aFontLayer);

        aFontLayer.mImage = new SharedImageRef(theFontImage);
        aFontLayer.mDefaultHeight = theFontImage.getHeight();
        aFontLayer.mAscent = theFontImage.getHeight();
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1166 (ImageFont copy ctor). */
    public ImageFont(ImageFont theImageFont) {
        super(theImageFont);
        mFontData = theImageFont.mFontData;
        mPointSize = theImageFont.mPointSize;
        mTagVector = new ArrayList<>(theImageFont.mTagVector);
        mActiveListValid = theImageFont.mActiveListValid;
        mActiveLayerList = new ArrayList<>();
        for (ActiveFontLayer anActiveFontLayer : theImageFont.mActiveLayerList) {
            mActiveLayerList.add(new ActiveFontLayer(anActiveFontLayer));
        }
        mScale = theImageFont.mScale;
        mForceScaledImagesWhite = theImageFont.mForceScaledImagesWhite;
        mFontData.ref();
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1179 (ImageFont::ImageFont(Image*, const std::string&amp;) — deprecated legacy). */
    public ImageFont(Image theFontImage, String theFontDescFileName) {
        mScale = 1.0;
        mFontData = new FontData();
        mFontData.ref();
        mFontData.loadLegacy(theFontImage, theFontDescFileName);
        mPointSize = mFontData.mDefaultPointSize;
        mTagVector = new ArrayList<>();
        mActiveLayerList = new ArrayList<>();
        generateActiveFontLayers();
        mActiveListValid = true;
        mForceScaledImagesWhite = false;
    }

    /** Java convenience for {@code ResourceManager.doLoadFont}. */
    public boolean isInitialized() {
        return mFontData != null && mFontData.mInitialized;
    }

    /** Java convenience for {@code ResourceManager.doLoadFont}: the descriptor error text. */
    public String getErrorText() {
        return mFontData == null ? "" : mFontData.getErrorText();
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1196 (ImageFont::GenerateActiveFontLayers). */
    public void generateActiveFontLayers() {
        if (!mFontData.mInitialized) {
            return;
        }

        mActiveLayerList.clear();

        mAscent = 0;
        mAscentPadding = 0;
        mHeight = 0;
        mLineSpacingOffset = 0;

        boolean firstLayer = true;

        for (FontLayer aFontLayer : mFontData.mFontLayerList) {
            if ((mPointSize >= aFontLayer.mMinPointSize)
                    && ((mPointSize <= aFontLayer.mMaxPointSize) || (aFontLayer.mMaxPointSize == -1))) {
                boolean active = true;

                // Make sure all required tags are included
                for (String aTag : aFontLayer.mRequiredTags) {
                    if (!mTagVector.contains(aTag)) {
                        active = false;
                    }
                }

                // Make sure no excluded tags are included
                for (String aTag : mTagVector) {
                    if (aFontLayer.mExcludedTags.contains(aTag)) {
                        active = false;
                    }
                }

                if (active) {
                    ActiveFontLayer anActiveFontLayer = new ActiveFontLayer();
                    mActiveLayerList.add(anActiveFontLayer);

                    anActiveFontLayer.mBaseFontLayer = aFontLayer;

                    double aLayerPointSize = 1;
                    double aPointSize = mScale;

                    if ((mScale == 1.0) && ((aFontLayer.mPointSize == 0) || (mPointSize == aFontLayer.mPointSize))) {
                        anActiveFontLayer.mScaledImage = (aFontLayer.mImage != null && aFontLayer.mImage.isNotNull())
                                ? aFontLayer.mImage.get() : null;

                        for (Map.Entry<Integer, CharData> anItr : aFontLayer.mCharDataMap.entrySet()) {
                            anActiveFontLayer.mScaledCharImageRects.put(anItr.getKey(), anItr.getValue().mImageRect);
                        }
                    } else {
                        if (aFontLayer.mPointSize != 0) {
                            aLayerPointSize = aFontLayer.mPointSize;
                            aPointSize = mPointSize * mScale;
                        }

                        // Resize font elements
                        MemoryImage aMemoryImage = new MemoryImage(mFontData.mApp);
                        anActiveFontLayer.mOwnedScaledImage = aMemoryImage;

                        int aCurX = 0;
                        int aMaxHeight = 0;

                        for (Map.Entry<Integer, CharData> anItr : aFontLayer.mCharDataMap.entrySet()) {
                            Rect anOrigRect = anItr.getValue().mImageRect;

                            Rect aScaledRect = new Rect(aCurX, 0,
                                    (int) ((anOrigRect.mWidth * aPointSize) / aLayerPointSize),
                                    (int) ((anOrigRect.mHeight * aPointSize) / aLayerPointSize));

                            anActiveFontLayer.mScaledCharImageRects.put(anItr.getKey(), aScaledRect);

                            aMaxHeight = Math.max(aMaxHeight, aScaledRect.mHeight);

                            aCurX += aScaledRect.mWidth;
                        }

                        anActiveFontLayer.mScaledImage = aMemoryImage;

                        // Create the image now
                        aMemoryImage.create(aCurX, aMaxHeight);

                        Graphics g = new Graphics(aMemoryImage);

                        for (Map.Entry<Integer, CharData> anItr : aFontLayer.mCharDataMap.entrySet()) {
                            if (aFontLayer.mImage != null && aFontLayer.mImage.isNotNull()) {
                                g.drawImage(aFontLayer.mImage.get(), anActiveFontLayer.mScaledCharImageRects.get(anItr.getKey()), anItr.getValue().mImageRect);
                            }
                        }

                        if (mForceScaledImagesWhite) {
                            int[] aBits = aMemoryImage.getBits();
                            int aCount = aMemoryImage.mWidth * aMemoryImage.mHeight;

                            for (int i = 0; i < aCount; i++) {
                                aBits[i] = aBits[i] | 0x00FFFFFF;
                            }
                            aMemoryImage.bitsChanged();
                        }
                    }

                    int aLayerAscent = (int) ((aFontLayer.mAscent * aPointSize) / aLayerPointSize);
                    mAscent = Math.max(mAscent, aLayerAscent);

                    if (aFontLayer.mHeight != 0) {
                        int aLayerHeight = (int) ((aFontLayer.mHeight * aPointSize) / aLayerPointSize);
                        mHeight = Math.max(mHeight, aLayerHeight);
                    } else {
                        int aLayerHeight = (int) ((aFontLayer.mDefaultHeight * aPointSize) / aLayerPointSize);
                        mHeight = Math.max(mHeight, aLayerHeight);
                    }

                    int anAscentPadding = (int) ((aFontLayer.mAscentPadding * aPointSize) / aLayerPointSize);
                    if (firstLayer || anAscentPadding < mAscentPadding) {
                        mAscentPadding = anAscentPadding;
                    }

                    int aLineSpacingOffset = (int) ((aFontLayer.mLineSpacingOffset * aPointSize) / aLayerPointSize);
                    if (firstLayer || aLineSpacingOffset > mLineSpacingOffset) {
                        mLineSpacingOffset = aLineSpacingOffset;
                    }

                    firstLayer = false;
                }
            }
        }
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1344 (ImageFont::StringWidth). */
    @Override
    public int stringWidth(String theString) {
        int aWidth = 0;
        int aPrevChar = 0;
        for (int i = 0; i < theString.length(); ) {
            int aChar = theString.codePointAt(i);
            i += Character.charCount(aChar);
            aWidth += charWidthKern(aChar, aPrevChar);
            aPrevChar = aChar;
        }

        return aWidth;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1359 (ImageFont::CharWidthKern). */
    @Override
    public int charWidthKern(int theChar, int thePrevChar) {
        prepare();

        int aMaxXPos = 0;
        double aPointSize = mPointSize * mScale;

        theChar = getMappedChar(theChar);
        if (thePrevChar != 0) {
            thePrevChar = getMappedChar(thePrevChar);
        }

        for (ActiveFontLayer anActiveFontLayer : mActiveLayerList) {
            int aLayerXPos = 0;

            int aCharWidth;
            int aSpacing;

            int aLayerPointSize = anActiveFontLayer.mBaseFontLayer.mPointSize;

            CharData aCharData = anActiveFontLayer.mBaseFontLayer.getCharData(theChar);
            CharData aPrevCharData = thePrevChar != 0 ? anActiveFontLayer.mBaseFontLayer.getCharData(thePrevChar) : null;

            if (aLayerPointSize == 0) {
                aCharWidth = (int) (aCharData.mWidth * mScale);

                if (thePrevChar != 0) {
                    aSpacing = (int) ((anActiveFontLayer.mBaseFontLayer.mSpacing
                            + aPrevCharData.kerningOffset(theChar)) * mScale);
                } else {
                    aSpacing = 0;
                }
            } else {
                aCharWidth = (int) (aCharData.mWidth * aPointSize / aLayerPointSize);

                if (thePrevChar != 0) {
                    aSpacing = (int) ((anActiveFontLayer.mBaseFontLayer.mSpacing
                            + aPrevCharData.kerningOffset(theChar)) * aPointSize / aLayerPointSize);
                } else {
                    aSpacing = 0;
                }
            }

            aLayerXPos += aCharWidth + aSpacing;

            aMaxXPos = Math.max(aMaxXPos, aLayerXPos);
        }

        return aMaxXPos;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1420 (ImageFont::CharWidth). */
    @Override
    public int charWidth(int theChar) {
        return charWidthKern(theChar, 0);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1431 (ImageFont::DrawStringEx). */
    public void drawStringEx(Graphics g, int theX, int theY, String theString, Color theColor, List<Rect> theDrawnAreas, int[] theWidth) {
        RenderCommand[] aRenderHead = new RenderCommand[256];
        RenderCommand[] aRenderTail = new RenderCommand[256];

        if (theDrawnAreas != null) {
            theDrawnAreas.clear();
        }

        if (!mFontData.mInitialized) {
            if (theWidth != null) {
                theWidth[0] = 0;
            }
            return;
        }

        prepare();

        boolean colorizeImages = g.getColorizeImages();
        g.setColorizeImages(true);

        int aCurXPos = theX;
        int aCurPoolIdx = 0;

        int[] aDecodeOffset = {0};
        int aCurRawChar = 0;
        int aNextRawChar = 0;
        boolean aHasCur = theString.length() > 0;
        if (aHasCur) {
            aCurRawChar = theString.codePointAt(0);
            aDecodeOffset[0] = Character.charCount(aCurRawChar);
        }

        while (aHasCur) {
            boolean aHasNext = aDecodeOffset[0] < theString.length();
            if (aHasNext) {
                aNextRawChar = theString.codePointAt(aDecodeOffset[0]);
            }

            int aChar = getMappedChar(aCurRawChar);
            int aNextChar = aHasNext ? getMappedChar(aNextRawChar) : 0;

            int aMaxXPos = aCurXPos;

            int aLayerOrderOffset = 0; // To ensure the order of font rendering
            for (ActiveFontLayer anActiveFontLayer : mActiveLayerList) {
                int aLayerXPos = aCurXPos;

                int anImageX;
                int anImageY;
                int aCharWidth;
                int aSpacing;

                int aLayerPointSize = anActiveFontLayer.mBaseFontLayer.mPointSize;

                double aScale = mScale;
                if (aLayerPointSize != 0) {
                    aScale *= (float) mPointSize / (float) aLayerPointSize;
                }

                CharData aCharData = anActiveFontLayer.mBaseFontLayer.getCharData(aChar);

                if (aScale == 1.0) {
                    anImageX = aLayerXPos + anActiveFontLayer.mBaseFontLayer.mOffset.mX + aCharData.mOffset.mX;
                    anImageY = theY - (anActiveFontLayer.mBaseFontLayer.mAscent - anActiveFontLayer.mBaseFontLayer.mOffset.mY - aCharData.mOffset.mY);
                    aCharWidth = aCharData.mWidth;

                    if (aNextChar != 0) {
                        aSpacing = anActiveFontLayer.mBaseFontLayer.mSpacing + aCharData.kerningOffset(aNextChar);
                    } else {
                        aSpacing = 0;
                    }
                } else {
                    anImageX = aLayerXPos + (int) ((anActiveFontLayer.mBaseFontLayer.mOffset.mX + aCharData.mOffset.mX) * aScale);
                    anImageY = theY - (int) ((anActiveFontLayer.mBaseFontLayer.mAscent - anActiveFontLayer.mBaseFontLayer.mOffset.mY - aCharData.mOffset.mY) * aScale);
                    aCharWidth = (int) (aCharData.mWidth * aScale);

                    if (aNextChar != 0) {
                        aSpacing = (int) ((anActiveFontLayer.mBaseFontLayer.mSpacing + aCharData.kerningOffset(aNextChar)) * aScale);
                    } else {
                        aSpacing = 0;
                    }
                }

                Color aColor = new Color();
                aColor.mRed = Math.min((theColor.mRed * anActiveFontLayer.mBaseFontLayer.mColorMult.mRed / 255) + anActiveFontLayer.mBaseFontLayer.mColorAdd.mRed, 255);
                aColor.mGreen = Math.min((theColor.mGreen * anActiveFontLayer.mBaseFontLayer.mColorMult.mGreen / 255) + anActiveFontLayer.mBaseFontLayer.mColorAdd.mGreen, 255);
                aColor.mBlue = Math.min((theColor.mBlue * anActiveFontLayer.mBaseFontLayer.mColorMult.mBlue / 255) + anActiveFontLayer.mBaseFontLayer.mColorAdd.mBlue, 255);
                aColor.mAlpha = Math.min((theColor.mAlpha * anActiveFontLayer.mBaseFontLayer.mColorMult.mAlpha / 255) + anActiveFontLayer.mBaseFontLayer.mColorAdd.mAlpha, 255);

                int anOrder = aLayerOrderOffset + anActiveFontLayer.mBaseFontLayer.mBaseOrder + aCharData.mOrder;

                if (aCurPoolIdx >= POOL_SIZE) {
                    break;
                }

                Rect aScaledCharRect = anActiveFontLayer.mScaledCharImageRects.get(aChar);
                if (aScaledCharRect == null) {
                    aScaledCharRect = new Rect();
                }

                RenderCommand aRenderCommand = new RenderCommand();
                aCurPoolIdx++;

                aRenderCommand.mImage = anActiveFontLayer.mScaledImage;
                aRenderCommand.mColor = aColor;
                aRenderCommand.mDest[0] = anImageX;
                aRenderCommand.mDest[1] = anImageY;
                aRenderCommand.mSrc[0] = aScaledCharRect.mX;
                aRenderCommand.mSrc[1] = aScaledCharRect.mY;
                aRenderCommand.mSrc[2] = aScaledCharRect.mWidth;
                aRenderCommand.mSrc[3] = aScaledCharRect.mHeight;
                aRenderCommand.mMode = anActiveFontLayer.mBaseFontLayer.mDrawMode;
                aRenderCommand.mNext = null;

                int anOrderIdx = Math.max(0, Math.min(255, anOrder + 128));

                if (aRenderTail[anOrderIdx] == null) {
                    aRenderTail[anOrderIdx] = aRenderCommand;
                    aRenderHead[anOrderIdx] = aRenderCommand;
                } else {
                    aRenderTail[anOrderIdx].mNext = aRenderCommand;
                    aRenderTail[anOrderIdx] = aRenderCommand;
                }

                if (theDrawnAreas != null) {
                    theDrawnAreas.add(new Rect(anImageX, anImageY, aScaledCharRect.mWidth, aScaledCharRect.mHeight));
                }

                aLayerXPos += aCharWidth + aSpacing;

                aMaxXPos = Math.max(aMaxXPos, aLayerXPos);

                aLayerOrderOffset++;
            }

            aCurXPos = aMaxXPos;

            aCurRawChar = aNextRawChar;
            aHasCur = aHasNext;
            if (aHasNext) {
                aDecodeOffset[0] += Character.charCount(aNextRawChar);
            }
        }

        if (theWidth != null) {
            theWidth[0] = aCurXPos - theX;
        }

        Color anOrigColor = g.getColor();

        for (int aPoolIdx = 0; aPoolIdx < 256; aPoolIdx++) {
            RenderCommand aRenderCommand = aRenderHead[aPoolIdx];

            while (aRenderCommand != null) {
                int anOldDrawMode = g.getDrawMode();
                if (aRenderCommand.mMode != -1) {
                    g.setDrawMode(aRenderCommand.mMode);
                }
                g.setColor(aRenderCommand.mColor);
                if (aRenderCommand.mImage != null) {
                    g.drawImage(aRenderCommand.mImage, aRenderCommand.mDest[0], aRenderCommand.mDest[1],
                            new Rect(aRenderCommand.mSrc[0], aRenderCommand.mSrc[1], aRenderCommand.mSrc[2], aRenderCommand.mSrc[3]));
                }
                g.setDrawMode(anOldDrawMode);

                aRenderCommand = aRenderCommand.mNext;
            }
        }

        g.setColor(anOrigColor);

        g.setColorizeImages(colorizeImages);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1616 (ImageFont::DrawString). */
    @Override
    public void drawString(Graphics g, int theX, int theY, String theString, Color theColor, Rect theClipRect) {
        drawStringEx(g, theX, theY, theString, theColor, null, null);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1621 (ImageFont::Duplicate). */
    @Override
    public Font duplicate() {
        return new ImageFont(this);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1626 (ImageFont::SetPointSize). */
    public void setPointSize(int thePointSize) {
        mPointSize = thePointSize;
        mActiveListValid = false;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1632 (ImageFont::SetScale). */
    public void setScale(double theScale) {
        mScale = theScale;
        mActiveListValid = false;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1638 (ImageFont::GetPointSize). */
    public int getPointSize() {
        return mPointSize;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1643 (ImageFont::GetDefaultPointSize). */
    public int getDefaultPointSize() {
        return mFontData.mDefaultPointSize;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1648 (ImageFont::AddTag). */
    public boolean addTag(String theTagName) {
        if (hasTag(theTagName)) {
            return false;
        }

        String aTagName = Common.stringToUpper(theTagName);
        mTagVector.add(aTagName);
        mActiveListValid = false;
        return true;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1657 (ImageFont::RemoveTag). */
    public boolean removeTag(String theTagName) {
        String aTagName = Common.stringToUpper(theTagName);
        if (!mTagVector.remove(aTagName)) {
            return false;
        }
        mActiveListValid = false;
        return true;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1669 (ImageFont::HasTag). */
    public boolean hasTag(String theTagName) {
        return mTagVector.contains(theTagName);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1675 (ImageFont::GetDefine). */
    public String getDefine(String theName) {
        DataElement aDataElement = mFontData.dereference(theName);

        if (aDataElement == null) {
            return "";
        }

        return mFontData.dataElementToString(aDataElement);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1688 (ImageFont::Prepare). */
    public void prepare() {
        if (!mActiveListValid) {
            generateActiveFontLayers();
            mActiveListValid = true;
        }
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1697 (ImageFont::GetMappedChar). */
    public int getMappedChar(int theChar) {
        Integer aMappedChar = mFontData.mCharMap.get(theChar);
        if (aMappedChar != null) {
            return aMappedChar;
        }
        return theChar;
    }
}
