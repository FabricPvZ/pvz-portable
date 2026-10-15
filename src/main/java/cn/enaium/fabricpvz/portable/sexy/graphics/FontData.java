package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.parsers.DataElement;
import cn.enaium.fabricpvz.portable.sexy.parsers.DescParser;
import cn.enaium.fabricpvz.portable.sexy.parsers.ListDataElement;
import cn.enaium.fabricpvz.portable.sexy.parsers.SingleDataElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/graphics/ImageFont.h:96 (class FontData : public DescParser) and
 * src/SexyAppFramework/graphics/ImageFont.cpp:160-1020 (FontData::DataToLayer, GetColorFromDataElement,
 * HandleCommand, Load, LoadLegacy).
 *
 * <p>Interprets the PopCap font descriptor ({@code data/*.txt}) command language and builds the
 * {@link FontLayer}s, per-character data and character map of an {@link ImageFont}. Layer names and
 * commands are compared case-insensitively (upstream {@code strcasecmp}).
 *
 * <p>Java deviations: {@code char32_t} keys are {@code Integer} code points in a
 * {@link LinkedHashMap}; {@code std::atomic mRefCount} is a plain {@code int} (GC owns the lifetime);
 * the error path records the message ({@link #getErrorText()}) instead of opening a UI popup.
 */
public class FontData extends DescParser {

    public boolean mInitialized;
    public int mRefCount;
    public SexyAppBase mApp;

    public int mDefaultPointSize;
    public Map<Integer, Integer> mCharMap;
    public List<FontLayer> mFontLayerList;
    public Map<String, FontLayer> mFontLayerMap;

    public String mSourceFile;
    public String mFontErrorHeader;

    private String mErrorText = "";

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:160 (FontData::FontData). */
    public FontData() {
        mInitialized = false;
        mApp = null;
        mRefCount = 0;
        mDefaultPointSize = 0;
        mCharMap = new LinkedHashMap<>();
        mFontLayerList = new ArrayList<>();
        mFontLayerMap = new LinkedHashMap<>();
        mSourceFile = "";
        mFontErrorHeader = "";
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:172 (FontData::Ref). */
    public void ref() {
        mRefCount++;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:177 (FontData::DeRef). */
    public void deRef() {
        // Java: the FontData is garbage-collected once no ImageFont references it.
        mRefCount--;
    }

    /** The last descriptor error message (Java convenience for ResourceManager.doLoadFont). */
    public String getErrorText() {
        return mErrorText;
    }

    /**
     * Ported from src/SexyAppFramework/graphics/ImageFont.cpp:185 (FontData::Error). Upstream also
     * pops up a dialog; the port records {@link #getErrorText()} instead.
     */
    @Override
    public boolean error(String theError) {
        String anErrorString = mFontErrorHeader + theError;

        if (mCurrentLine != null && mCurrentLine.length() > 0) {
            anErrorString += " on Line " + mCurrentLineNum + ":\r\n\r\n" + mCurrentLine;
        }

        mErrorText = anErrorString;
        if (mApp != null) {
            mApp.popup(anErrorString);
        }
        return super.error(theError);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:199 (FontData::DataToLayer). */
    public boolean dataToLayer(DataElement theSource, FontLayer[] theFontLayer) {
        theFontLayer[0] = null;

        if (theSource.mIsList) {
            return false;
        }

        String aLayerName = Common.stringToUpper(((SingleDataElement) theSource).mString);

        FontLayer aLayer = mFontLayerMap.get(aLayerName);
        if (aLayer == null) {
            error("Undefined Layer");
            return false;
        }

        theFontLayer[0] = aLayer;
        return true;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:223 (FontData::GetColorFromDataElement). */
    public boolean getColorFromDataElement(DataElement theElement, Color[] theColor) {
        if (theElement.mIsList) {
            List<Double> aFactorVector = new ArrayList<>();
            if (!dataToDoubleVector(theElement, aFactorVector) || (aFactorVector.size() != 4)) {
                return false;
            }

            theColor[0] = new Color(
                    (int) (aFactorVector.get(0) * 255),
                    (int) (aFactorVector.get(1) * 255),
                    (int) (aFactorVector.get(2) * 255),
                    (int) (aFactorVector.get(3) * 255));
            return true;
        }

        int[] aColor = new int[1];
        if (!dataToInt(theElement, aColor)) {
            return false;
        }

        theColor[0] = new Color(aColor[0]);
        return true;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:250 (UTF8CharToUTF32Char). */
    private static int utf8CharToUTF32Char(String theString) {
        if (theString.codePointCount(0, theString.length()) != 1) {
            return 0; // Invalid character
        }
        return theString.codePointAt(0);
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:261 (UTF8PairToUTF32Pair). */
    private static boolean utf8PairToUTF32Pair(String theString, int[] firstChar, int[] secondChar) {
        if (theString.codePointCount(0, theString.length()) != 2) {
            return false;
        }
        firstChar[0] = theString.codePointAt(0);
        secondChar[0] = theString.codePointAt(Character.charCount(firstChar[0]));
        return true;
    }

    private static String singleString(DataElement theElement) {
        return ((SingleDataElement) theElement).mString;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:269 (FontData::HandleCommand). */
    @Override
    public boolean handleCommand(ListDataElement theParams) {
        String aCmd = singleString(theParams.mElementVector.get(0));

        boolean invalidNumParams = false;
        boolean invalidParamFormat = false;
        boolean literalError = false;
        boolean sizeMismatch = false;

        if (aCmd.equalsIgnoreCase("Define")) {
            if (theParams.mElementVector.size() == 3) {
                if (!theParams.mElementVector.get(1).mIsList) {
                    String aDefineName = Common.stringToUpper(singleString(theParams.mElementVector.get(1)));

                    if (!isImmediate(aDefineName)) {
                        mDefineMap.remove(aDefineName);

                        if (theParams.mElementVector.get(2).mIsList) {
                            ListDataElement aValues = new ListDataElement();
                            if (!getValues((ListDataElement) theParams.mElementVector.get(2), aValues)) {
                                return false;
                            }
                            mDefineMap.put(aDefineName, aValues);
                        } else {
                            DataElement aDefParam = theParams.mElementVector.get(2);
                            DataElement aDerefVal = dereference(singleString(aDefParam));
                            if (aDerefVal != null) {
                                mDefineMap.put(aDefineName, aDerefVal.duplicate());
                            } else {
                                mDefineMap.put(aDefineName, aDefParam.duplicate());
                            }
                        }
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("CreateHorzSpanRectList")) {
            if (theParams.mElementVector.size() == 4) {
                List<Integer> aRectIntVector = new ArrayList<>();
                List<Integer> aWidthsVector = new ArrayList<>();

                if ((!theParams.mElementVector.get(1).mIsList)
                        && dataToIntVector(theParams.mElementVector.get(2), aRectIntVector)
                        && (aRectIntVector.size() == 4)
                        && dataToIntVector(theParams.mElementVector.get(3), aWidthsVector)) {
                    String aDefineName = Common.stringToUpper(singleString(theParams.mElementVector.get(1)));

                    int aXPos = 0;
                    ListDataElement aRectList = new ListDataElement();

                    for (int aWidthNum = 0; aWidthNum < aWidthsVector.size(); aWidthNum++) {
                        ListDataElement aRectElement = new ListDataElement();
                        aRectList.mElementVector.add(aRectElement);
                        aRectElement.mElementVector.add(new SingleDataElement(String.valueOf(aRectIntVector.get(0) + aXPos)));
                        aRectElement.mElementVector.add(new SingleDataElement(String.valueOf(aRectIntVector.get(1))));
                        aRectElement.mElementVector.add(new SingleDataElement(String.valueOf(aWidthsVector.get(aWidthNum))));
                        aRectElement.mElementVector.add(new SingleDataElement(String.valueOf(aRectIntVector.get(3))));
                        aXPos += aWidthsVector.get(aWidthNum);
                    }

                    mDefineMap.remove(aDefineName);
                    mDefineMap.put(aDefineName, aRectList);
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("SetDefaultPointSize")) {
            if (theParams.mElementVector.size() == 2) {
                if (!theParams.mElementVector.get(1).mIsList) {
                    Integer aPointSize = Common.stringToInt(singleString(theParams.mElementVector.get(1)));
                    if (aPointSize != null) {
                        mDefaultPointSize = aPointSize;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("SetCharMap")) {
            if (theParams.mElementVector.size() == 3) {
                List<String> aFromVector = new ArrayList<>();
                List<String> aToVector = new ArrayList<>();

                if (dataToStringVector(theParams.mElementVector.get(1), aFromVector)
                        && dataToStringVector(theParams.mElementVector.get(2), aToVector)) {
                    if (aFromVector.size() == aToVector.size()) {
                        for (int aMapIdx = 0; aMapIdx < aFromVector.size(); aMapIdx++) {
                            if ((aFromVector.get(aMapIdx).length() == 1) && (aToVector.get(aMapIdx).length() == 1)) {
                                mCharMap.put((int) aFromVector.get(aMapIdx).charAt(0), (int) aToVector.get(aMapIdx).charAt(0));
                            } else {
                                invalidParamFormat = true;
                            }
                        }
                    } else {
                        sizeMismatch = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("CreateLayer")) {
            if (theParams.mElementVector.size() == 2) {
                if (!theParams.mElementVector.get(1).mIsList) {
                    String aLayerName = Common.stringToUpper(singleString(theParams.mElementVector.get(1)));

                    FontLayer aFontLayer = new FontLayer(this);
                    mFontLayerList.add(aFontLayer);

                    if (mFontLayerMap.putIfAbsent(aLayerName, aFontLayer) != null) {
                        error("Layer Already Exists");
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("CreateLayerFrom")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aSourceLayer = new FontLayer[1];

                if ((!theParams.mElementVector.get(1).mIsList) && dataToLayer(theParams.mElementVector.get(2), aSourceLayer)) {
                    String aLayerName = Common.stringToUpper(singleString(theParams.mElementVector.get(1)));

                    FontLayer aFontLayer = new FontLayer(aSourceLayer[0]);
                    mFontLayerList.add(aFontLayer);

                    if (mFontLayerMap.putIfAbsent(aLayerName, aFontLayer) != null) {
                        error("Layer Already Exists");
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerRequireTags")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                List<String> aStringVector = new ArrayList<>();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToStringVector(theParams.mElementVector.get(2), aStringVector)) {
                    for (String aTag : aStringVector) {
                        aLayer[0].mRequiredTags.add(Common.stringToUpper(aTag));
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerExcludeTags")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                List<String> aStringVector = new ArrayList<>();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToStringVector(theParams.mElementVector.get(2), aStringVector)) {
                    for (String aTag : aStringVector) {
                        aLayer[0].mExcludedTags.add(Common.stringToUpper(aTag));
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerPointRange")) {
            if (theParams.mElementVector.size() == 4) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && !theParams.mElementVector.get(2).mIsList
                        && !theParams.mElementVector.get(3).mIsList) {
                    Integer aMinPointSize = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    Integer aMaxPointSize = Common.stringToInt(singleString(theParams.mElementVector.get(3)));

                    if (aMinPointSize != null && aMaxPointSize != null) {
                        aLayer[0].mMinPointSize = aMinPointSize;
                        aLayer[0].mMaxPointSize = aMaxPointSize;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetPointSize")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && !theParams.mElementVector.get(2).mIsList) {
                    Integer aPointSize = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (aPointSize != null) {
                        aLayer[0].mPointSize = aPointSize;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetHeight")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && !theParams.mElementVector.get(2).mIsList) {
                    Integer aHeight = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (aHeight != null) {
                        aLayer[0].mHeight = aHeight;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetImage")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                String[] aFileNameString = new String[1];

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToString(theParams.mElementVector.get(2), aFileNameString)) {
                    String aFileName = Common.getPathFrom(aFileNameString[0], Common.getFileDir(mSourceFile));

                    boolean[] isNew = {false};
                    SharedImageRef anImage = mApp.getSharedImage(aFileName, "", isNew);

                    if (anImage != null && anImage.isNotNull()) {
                        aLayer[0].mImage = anImage;
                    } else {
                        error("Failed to Load Image");
                        return false;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetDrawMode")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer) && (!theParams.mElementVector.get(2).mIsList)) {
                    Integer anDrawMode = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (anDrawMode != null && anDrawMode >= 0 && anDrawMode <= 1) {
                        aLayer[0].mDrawMode = anDrawMode;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetColorMult")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer)) {
                    Color[] aColor = new Color[1];
                    if (!getColorFromDataElement(theParams.mElementVector.get(2), aColor)) {
                        invalidParamFormat = true;
                    } else {
                        aLayer[0].mColorMult = aColor[0];
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetColorAdd")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer)) {
                    Color[] aColor = new Color[1];
                    if (!getColorFromDataElement(theParams.mElementVector.get(2), aColor)) {
                        invalidParamFormat = true;
                    } else {
                        aLayer[0].mColorAdd = aColor[0];
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetAscent")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer) && (!theParams.mElementVector.get(2).mIsList)) {
                    Integer anAscent = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (anAscent != null) {
                        aLayer[0].mAscent = anAscent;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetAscentPadding")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer) && (!theParams.mElementVector.get(2).mIsList)) {
                    Integer anAscent = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (anAscent != null) {
                        aLayer[0].mAscentPadding = anAscent;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetLineSpacingOffset")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer) && (!theParams.mElementVector.get(2).mIsList)) {
                    Integer anAscent = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (anAscent != null) {
                        aLayer[0].mLineSpacingOffset = anAscent;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetOffset")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                List<Integer> anOffset = new ArrayList<>();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToIntVector(theParams.mElementVector.get(2), anOffset)
                        && (anOffset.size() == 2)) {
                    aLayer[0].mOffset.mX = anOffset.get(0);
                    aLayer[0].mOffset.mY = anOffset.get(1);
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetCharWidths")) {
            if (theParams.mElementVector.size() == 4) {
                FontLayer[] aLayer = new FontLayer[1];
                List<String> aCharsVector = new ArrayList<>();
                List<Integer> aCharWidthsVector = new ArrayList<>();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToStringVector(theParams.mElementVector.get(2), aCharsVector)
                        && dataToIntVector(theParams.mElementVector.get(3), aCharWidthsVector)) {
                    if (aCharsVector.size() == aCharWidthsVector.size()) {
                        for (int i = 0; i < aCharsVector.size(); i++) {
                            int firstChar = utf8CharToUTF32Char(aCharsVector.get(i));
                            aLayer[0].getCharData(firstChar).mWidth = aCharWidthsVector.get(i);
                        }
                    } else {
                        sizeMismatch = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetSpacing")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && (!theParams.mElementVector.get(2).mIsList)) {
                    Integer aSpacing = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (aSpacing != null) {
                        aLayer[0].mSpacing = aSpacing;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetImageMap")) {
            if (theParams.mElementVector.size() == 4) {
                FontLayer[] aLayer = new FontLayer[1];
                List<String> aCharsVector = new ArrayList<>();
                ListDataElement aRectList = new ListDataElement();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToStringVector(theParams.mElementVector.get(2), aCharsVector)
                        && dataToList(theParams.mElementVector.get(3), aRectList)) {
                    if (aCharsVector.size() == aRectList.mElementVector.size()) {
                        if (aLayer[0].mImage != null && aLayer[0].mImage.isNotNull() && aLayer[0].mImage.get() != null) {
                            Image anImage = aLayer[0].mImage.get();
                            int anImageWidth = anImage.getWidth();
                            int anImageHeight = anImage.getHeight();

                            for (int i = 0; i < aCharsVector.size(); i++) {
                                List<Integer> aRectElement = new ArrayList<>();
                                int firstChar = utf8CharToUTF32Char(aCharsVector.get(i));

                                if (dataToIntVector(aRectList.mElementVector.get(i), aRectElement) && (aRectElement.size() == 4)) {
                                    Rect aRect = new Rect(aRectElement.get(0), aRectElement.get(1), aRectElement.get(2), aRectElement.get(3));

                                    if ((aRect.mX < 0) || (aRect.mY < 0)
                                            || (aRect.mX + aRect.mWidth > anImageWidth) || (aRect.mY + aRect.mHeight > anImageHeight)) {
                                        error("Image rectangle out of bounds " + aRect.mX + " " + aRect.mY + " " + aRect.mWidth + " " + aRect.mHeight + " - " + anImageWidth + " " + anImageHeight);
                                        return false;
                                    }

                                    aLayer[0].getCharData(firstChar).mImageRect = aRect;
                                } else {
                                    invalidParamFormat = true;
                                }
                            }

                            aLayer[0].mDefaultHeight = 0;
                            for (CharData aCharData : aLayer[0].mCharDataMap.values()) {
                                aLayer[0].mDefaultHeight = Math.max(aLayer[0].mDefaultHeight, aCharData.mImageRect.mHeight + aCharData.mOffset.mY);
                            }
                        } else {
                            error("Layer image not set");
                            return false;
                        }
                    } else {
                        sizeMismatch = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetCharOffsets")) {
            if (theParams.mElementVector.size() == 4) {
                FontLayer[] aLayer = new FontLayer[1];
                List<String> aCharsVector = new ArrayList<>();
                ListDataElement aRectList = new ListDataElement();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToStringVector(theParams.mElementVector.get(2), aCharsVector)
                        && dataToList(theParams.mElementVector.get(3), aRectList)) {
                    if (aCharsVector.size() == aRectList.mElementVector.size()) {
                        for (int i = 0; i < aCharsVector.size(); i++) {
                            List<Integer> aRectElement = new ArrayList<>();
                            int firstChar = utf8CharToUTF32Char(aCharsVector.get(i));

                            if (dataToIntVector(aRectList.mElementVector.get(i), aRectElement) && (aRectElement.size() == 2)) {
                                aLayer[0].getCharData(firstChar).mOffset = new Point(aRectElement.get(0), aRectElement.get(1));
                            } else {
                                invalidParamFormat = true;
                            }
                        }
                    } else {
                        sizeMismatch = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetKerningPairs")) {
            if (theParams.mElementVector.size() == 4) {
                FontLayer[] aLayer = new FontLayer[1];
                List<String> aPairsVector = new ArrayList<>();
                List<Integer> anOffsetsVector = new ArrayList<>();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToStringVector(theParams.mElementVector.get(2), aPairsVector)
                        && dataToIntVector(theParams.mElementVector.get(3), anOffsetsVector)) {
                    if (aPairsVector.size() == anOffsetsVector.size()) {
                        for (int i = 0; i < aPairsVector.size(); i++) {
                            int[] firstChar = new int[1];
                            int[] secondChar = new int[1];
                            if (utf8PairToUTF32Pair(aPairsVector.get(i), firstChar, secondChar)) {
                                aLayer[0].getCharData(firstChar[0]).mKerningOffsets.put(secondChar[0], anOffsetsVector.get(i));
                            } else {
                                invalidParamFormat = true;
                            }
                        }
                    } else {
                        sizeMismatch = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetBaseOrder")) {
            if (theParams.mElementVector.size() == 3) {
                FontLayer[] aLayer = new FontLayer[1];
                if (dataToLayer(theParams.mElementVector.get(1), aLayer) && (!theParams.mElementVector.get(2).mIsList)) {
                    Integer aBaseOrder = Common.stringToInt(singleString(theParams.mElementVector.get(2)));
                    if (aBaseOrder != null) {
                        aLayer[0].mBaseOrder = aBaseOrder;
                    } else {
                        invalidParamFormat = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetCharOrders")) {
            if (theParams.mElementVector.size() == 4) {
                FontLayer[] aLayer = new FontLayer[1];
                List<String> aCharsVector = new ArrayList<>();
                List<Integer> aCharOrdersVector = new ArrayList<>();

                if (dataToLayer(theParams.mElementVector.get(1), aLayer)
                        && dataToStringVector(theParams.mElementVector.get(2), aCharsVector)
                        && dataToIntVector(theParams.mElementVector.get(3), aCharOrdersVector)) {
                    if (aCharsVector.size() == aCharOrdersVector.size()) {
                        for (int i = 0; i < aCharsVector.size(); i++) {
                            if (aCharsVector.get(i).length() == 1) {
                                aLayer[0].getCharData((int) aCharsVector.get(i).charAt(0)).mOrder = aCharOrdersVector.get(i);
                            } else {
                                invalidParamFormat = true;
                            }
                        }
                    } else {
                        sizeMismatch = true;
                    }
                } else {
                    invalidParamFormat = true;
                }
            } else {
                invalidNumParams = true;
            }
        } else if (aCmd.equalsIgnoreCase("LayerSetExInfo")) {
            // no-op
        } else {
            error("Unknown Command");
            return false;
        }

        if (invalidNumParams) {
            error("Invalid Number of Parameters");
            return false;
        }

        if (invalidParamFormat) {
            error("Invalid Paramater Type");
            return false;
        }

        if (literalError) {
            error("Undefined Value");
            return false;
        }

        if (sizeMismatch) {
            error("List Size Mismatch");
            return false;
        }

        return true;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1020 (FontData::Load). */
    public boolean load(SexyAppBase theSexyApp, String theFontDescFileName) {
        if (mInitialized) {
            return false;
        }

        mApp = theSexyApp;
        mCurrentLine = "";

        mFontErrorHeader = "Font Descriptor Error in " + theFontDescFileName + "\r\n";

        mSourceFile = theFontDescFileName;

        mInitialized = loadDescriptor(theFontDescFileName);

        return true;
    }

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:1041 (FontData::LoadLegacy). */
    public boolean loadLegacy(Image theFontImage, String theFontDescFileName) {
        if (mInitialized) {
            return false;
        }

        FontLayer aFontLayer = new FontLayer(this);
        mFontLayerList.add(aFontLayer);
        mFontLayerMap.put("", aFontLayer);

        aFontLayer.mImage = new SharedImageRef(theFontImage);
        aFontLayer.mDefaultHeight = theFontImage.getHeight();
        aFontLayer.mAscent = theFontImage.getHeight();

        String aFileContent;
        try {
            // Use GetResourcePath to handle resource folder prefix
            String aFilePath = Common.getResourcePath(theFontDescFileName);
            aFileContent = java.nio.file.Files.readString(java.nio.file.Path.of(aFilePath));
        } catch (java.io.IOException e) {
            return false;
        }

        mSourceFile = theFontDescFileName;

        java.util.StringTokenizer aStream = new java.util.StringTokenizer(aFileContent);
        if (!aStream.hasMoreTokens()) {
            return false;
        }
        Integer aSpaceWidth = Common.stringToInt(aStream.nextToken());
        if (!aStream.hasMoreTokens()) {
            return false;
        }
        Integer anAscent = Common.stringToInt(aStream.nextToken());
        if (aSpaceWidth == null || anAscent == null) {
            return false;
        }
        aFontLayer.getCharData(' ').mWidth = aSpaceWidth;
        aFontLayer.mAscent = anAscent;

        int aCharPos = 0;
        while (aStream.hasMoreTokens()) {
            String aCharToken = aStream.nextToken();
            if (!aStream.hasMoreTokens()) {
                break;
            }
            Integer aWidth = Common.stringToInt(aStream.nextToken());
            if (aWidth == null || aCharToken.isEmpty()) {
                break;
            }
            char aChar = aCharToken.charAt(0);
            if (aChar == 0) {
                break;
            }

            aFontLayer.getCharData(aChar).mImageRect = new Rect(aCharPos, 0, aWidth, theFontImage.getHeight());
            aFontLayer.getCharData(aChar).mWidth = aWidth;

            aCharPos += aWidth;
        }

        for (char c = 'A'; c <= 'Z'; c++) {
            if ((aFontLayer.getCharData(c).mWidth == 0) && (aFontLayer.getCharData((char) (c - 'A' + 'a')).mWidth != 0)) {
                mCharMap.put((int) c, (int) (c - 'A' + 'a'));
            }
        }

        for (char c = 'a'; c <= 'z'; c++) {
            if ((aFontLayer.getCharData(c).mWidth == 0) && (aFontLayer.getCharData((char) (c - 'a' + 'A')).mWidth != 0)) {
                mCharMap.put((int) c, (int) (c - 'a' + 'A'));
            }
        }

        mInitialized = true;

        return true;
    }
}
