package cn.enaium.fabricpvz.portable.sexy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import cn.enaium.fabricpvz.portable.sexy.graphics.AnimInfo;
import cn.enaium.fabricpvz.portable.sexy.graphics.AnimType;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.ImageFont;
import cn.enaium.fabricpvz.portable.sexy.graphics.ImageLib;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.SharedImageRef;
import cn.enaium.fabricpvz.portable.sexy.parsers.XMLElement;
import cn.enaium.fabricpvz.portable.sexy.parsers.XMLParser;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Ported from src/SexyAppFramework/misc/ResourceManager.h:51-229 and
 * src/SexyAppFramework/misc/ResourceManager.cpp:1-1168.
 *
 * <p>Parses the {@code ResourceManifest} XML (properties/resources.xml), builds id-keyed maps of
 * image/sound/font descriptors and loads them lazily group by group. Semantics preserved verbatim:
 * <ul>
 *   <li>manifest structure {@code <ResourceManifest><Resources id=…><SetDefaults …/><Image …/>…}
 *       with the error texts {@code "Unexpected element found."}, {@code "Invalid Section '<x>'"},
 *       {@code "Element Not Expected '<x>'"}, {@code "No id specified."}, {@code "No path specified."},
 *       {@code "Resource already defined."}, {@code "Resource file not found: <f>"} and
 *       {@code "Expecting ResourceManifest tag"};</li>
 *   <li>{@code SetDefaults path/idprefix} are sticky; a {@code path} beginning with {@code '!'}
 *       bypasses the default path, with {@code "!program"} marking program-supplied resources;</li>
 *   <li>resource ids default to {@code idprefix + file name without extension};</li>
 *   <li>group names are case-insensitive ({@code mResGroupMap}), ids are exact;</li>
 *   <li>the first error wins and is suffixed with {@code " on Line N"} / {@code " in File '<f>'"}.</li>
 * </ul>
 *
 * <p>Image accessors return {@link SharedImageRef} (../verification/research/01-framework-core.md §8.3); the
 * Java handle is nullable and reports {@link SharedImageRef#isNull()} when missing. {@code intptr_t}
 * sound ids become {@code long} ({@code -1} = missing).
 */
public class ResourceManager {

    /** Ported from ResourceManager.h:54-59. */
    public enum ResType {
        RES_TYPE_IMAGE,
        RES_TYPE_SOUND,
        RES_TYPE_FONT
    }

    /** Ported from ResourceManager.h:62-73. */
    public static class BaseRes {
        public ResType mType;
        public String mId = "";
        public String mResGroup = "";
        public String mPath = "";
        public Map<String, String> mXMLAttributes = new LinkedHashMap<>();
        public boolean mFromProgram;

        public BaseRes() {
        }

        public void deleteResource() {
        }
    }

    /** Ported from ResourceManager.h:75-95. */
    public static class ImageRes extends BaseRes {
        public SharedImageRef mImage = new SharedImageRef();
        public String mAlphaImage = "";
        public String mAlphaGridImage = "";
        public String mVariant = "";
        public boolean mAutoFindAlpha;
        public boolean mPalletize;
        public boolean mA4R4G4B4;
        public boolean mA8R8G8B8;
        public boolean mDDSurface;
        public boolean mPurgeBits;
        public boolean mMinimizeSubdivisions;
        public int mRows = 1;
        public int mCols = 1;
        public int mAlphaColor = 0xFFFFFF;
        public AnimInfo mAnimInfo = new AnimInfo();

        public ImageRes() {
            mType = ResType.RES_TYPE_IMAGE;
        }

        @Override
        public void deleteResource() {
            mImage.release();
        }
    }

    /** Ported from ResourceManager.h:97-105. */
    public static class SoundRes extends BaseRes {
        public long mSoundId = -1;
        public double mVolume = -1;
        public int mPanning;

        public SoundRes() {
            mType = ResType.RES_TYPE_SOUND;
        }

        @Override
        public void deleteResource() {
            if (mSoundId >= 0 && SexyAppBase.current != null) {
                SoundManager aSoundManager = SexyAppBase.current.getSoundManager();
                if (aSoundManager != null) {
                    aSoundManager.releaseSound(mSoundId);
                }
            }
            mSoundId = -1;
        }
    }

    /** Ported from ResourceManager.h:107-125. */
    public static class FontRes extends BaseRes {
        public Font mFont;
        public Image mImage;
        public String mImagePath = "";
        public String mTags = "";

        // For SysFonts
        public boolean mSysFont;
        public boolean mBold;
        public boolean mItalic;
        public boolean mUnderline;
        public boolean mShadow;
        public int mSize;

        public FontRes() {
            mType = ResType.RES_TYPE_FONT;
        }

        @Override
        public void deleteResource() {
            mFont = null;
            mImage = null;
        }
    }

    public Set<String> mLoadedGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public Map<String, BaseRes> mImageMap = new LinkedHashMap<>();
    public Map<String, BaseRes> mSoundMap = new LinkedHashMap<>();
    public Map<String, BaseRes> mFontMap = new LinkedHashMap<>();

    protected XMLParser mXMLParser;
    protected String mError = "";
    protected boolean mHasFailed;
    protected SexyAppBase mApp;
    protected String mCurResGroup = "";
    protected String mDefaultPath = "";
    protected String mDefaultIdPrefix = "";
    protected boolean mAllowMissingProgramResources;
    protected boolean mAllowAlreadyDefinedResources;
    protected boolean mHadAlreadyDefinedError;

    /** Group index; group names are case-insensitive (upstream {@code StringLessNoCase}). */
    public Map<String, List<BaseRes>> mResGroupMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    protected List<BaseRes> mCurResGroupList;
    protected int mCurResGroupListItr;

    /** Ported from ResourceManager.cpp:56-67. */
    public ResourceManager(SexyAppBase theApp) {
        mApp = theApp;
        mHasFailed = false;
        mAllowMissingProgramResources = false;
        mAllowAlreadyDefinedResources = false;
        mCurResGroupList = null;
    }

    /** Ported from ResourceManager.cpp:69-74. */
    public void dispose() {
        deleteMap(mImageMap);
        deleteMap(mSoundMap);
        deleteMap(mFontMap);
    }

    /** Ported from ResourceManager.cpp:76-79. */
    public boolean isGroupLoaded(String theGroup) {
        return mLoadedGroups.contains(theGroup);
    }

    /** Ported from ResourceManager.cpp:81-85. */
    protected void deleteMap(Map<String, BaseRes> theMap) {
        for (BaseRes aRes : theMap.values()) {
            aRes.deleteResource();
        }
    }

    /** Ported from ResourceManager.cpp:87-94. */
    protected void deleteResources(Map<String, BaseRes> theMap, String theGroup) {
        for (BaseRes aRes : theMap.values()) {
            if (theGroup.isEmpty() || aRes.mResGroup.equals(theGroup)) {
                aRes.deleteResource();
            }
        }
    }

    /** Ported from ResourceManager.cpp:96-102. */
    public void deleteResources(String theGroup) {
        deleteResources(mImageMap, theGroup);
        deleteResources(mSoundMap, theGroup);
        deleteResources(mFontMap, theGroup);
        mLoadedGroups.remove(theGroup);
    }

    /** Ported from ResourceManager.cpp:104-107. */
    public void deleteSound(String theName) {
        replaceSound(theName, -1);
    }

    /** Ported from ResourceManager.cpp:109-115. */
    public void releaseTrackedResources(List<String> theNames) {
        // Upstream only frees under LOW_MEMORY; the Java port relies on the garbage collector.
        theNames.clear();
    }

    /** Ported from ResourceManager.cpp:117-128. */
    public void deleteExtraImageBuffers(String theGroup) {
        for (BaseRes aBaseRes : mImageMap.values()) {
            if (theGroup.isEmpty() || aBaseRes.mResGroup.equals(theGroup)) {
                ImageRes aRes = (ImageRes) aBaseRes;
                Image anImage = aRes.mImage.get();
                if (anImage instanceof MemoryImage aMemoryImage) {
                    aMemoryImage.deleteExtraBuffers();
                }
            }
        }
    }

    /** Ported from ResourceManager.cpp:130-133. */
    public String getErrorText() {
        return mError;
    }

    /** Ported from ResourceManager.cpp:135-138. */
    public boolean hadError() {
        return mHasFailed;
    }

    /** Ported from ResourceManager.cpp:140-163. First error wins; appends line/file from the parser. */
    protected boolean fail(String theErrorText) {
        if (!mHasFailed) {
            mHasFailed = true;
            if (mXMLParser == null) {
                mError = theErrorText;
                return false;
            }

            int aLineNum = mXMLParser.getCurrentLineNum();
            mError = theErrorText;

            if (aLineNum > 0) {
                mError += " on Line " + aLineNum;
            }
            if (!mXMLParser.getFileName().isEmpty()) {
                mError += " in File '" + mXMLParser.getFileName() + "'";
            }
        }
        return false;
    }

    /** Ported from ResourceManager.cpp:165-204. */
    protected boolean parseCommonResource(XMLElement theElement, BaseRes theRes, Map<String, BaseRes> theMap) {
        mHadAlreadyDefinedError = false;

        String aPath = theElement.mAttributes.getOrDefault("path", "");
        if (aPath.isEmpty()) {
            return fail("No path specified.");
        }

        theRes.mXMLAttributes = new LinkedHashMap<>(theElement.mAttributes);
        theRes.mFromProgram = false;
        if (aPath.charAt(0) == '!') {
            theRes.mPath = aPath;
            if (aPath.equals("!program")) {
                theRes.mFromProgram = true;
            }
        } else {
            theRes.mPath = mDefaultPath + aPath;
        }

        String anId;
        String anAttrId = theElement.mAttributes.get("id");
        if (anAttrId == null) {
            anId = mDefaultIdPrefix + Common.getFileName(theRes.mPath, true);
        } else {
            anId = mDefaultIdPrefix + anAttrId;
        }

        theRes.mResGroup = mCurResGroup;
        theRes.mId = anId;

        if (theMap.containsKey(anId)) {
            mHadAlreadyDefinedError = true;
            return fail("Resource already defined.");
        }

        theMap.put(anId, theRes);
        mCurResGroupList.add(theRes);
        return true;
    }

    /** Ported from ResourceManager.cpp:206-245. */
    protected boolean parseSoundResource(XMLElement theElement) {
        SoundRes aRes = new SoundRes();
        BaseRes aNewRes = aRes;
        aRes.mSoundId = -1;
        aRes.mVolume = -1;
        aRes.mPanning = 0;

        if (!parseCommonResource(theElement, aRes, mSoundMap)) {
            if (mHadAlreadyDefinedError && mAllowAlreadyDefinedResources) {
                mError = "";
                mHasFailed = false;
                aRes = (SoundRes) mSoundMap.get(aNewRes.mId);
                aRes.mPath = aNewRes.mPath;
                aRes.mXMLAttributes = aNewRes.mXMLAttributes;
            } else {
                return false;
            }
        }

        String aVolume = theElement.mAttributes.get("volume");
        if (aVolume != null) {
            aRes.mVolume = parseDouble(aVolume, aRes.mVolume);
        }

        String aPan = theElement.mAttributes.get("pan");
        if (aPan != null) {
            aRes.mPanning = (int) parseLong(aPan, aRes.mPanning);
        }

        return true;
    }

    /** Ported from ResourceManager.cpp:247-261 ({@code ReadIntVector}). */
    private static List<Integer> readIntVector(String theVal) {
        List<Integer> aVector = new ArrayList<>();
        for (String aToken : theVal.split(",", -1)) {
            aVector.add((int) atoi(aToken));
        }
        return aVector;
    }

    /** Ported from ResourceManager.cpp:263-375. */
    protected boolean parseImageResource(XMLElement theElement) {
        ImageRes aRes = new ImageRes();
        BaseRes aNewRes = aRes;
        if (!parseCommonResource(theElement, aRes, mImageMap)) {
            if (mHadAlreadyDefinedError && mAllowAlreadyDefinedResources) {
                mError = "";
                mHasFailed = false;
                aRes = (ImageRes) mImageMap.get(aNewRes.mId);
                aRes.mPath = aNewRes.mPath;
                aRes.mXMLAttributes = aNewRes.mXMLAttributes;
            } else {
                return false;
            }
        }

        Map<String, String> anAttrs = theElement.mAttributes;
        aRes.mPalletize = !anAttrs.containsKey("nopal");
        aRes.mA4R4G4B4 = anAttrs.containsKey("a4r4g4b4");
        aRes.mDDSurface = anAttrs.containsKey("ddsurface");
        aRes.mPurgeBits = anAttrs.containsKey("nobits")
                || (is3DAccelerated() && anAttrs.containsKey("nobits3d"))
                || (!is3DAccelerated() && anAttrs.containsKey("nobits2d"));
        aRes.mA8R8G8B8 = anAttrs.containsKey("a8r8g8b8");
        aRes.mMinimizeSubdivisions = anAttrs.containsKey("minsubdivide");
        aRes.mAutoFindAlpha = !anAttrs.containsKey("noalpha");

        String anAlphaImage = anAttrs.get("alphaimage");
        if (anAlphaImage != null) {
            aRes.mAlphaImage = mDefaultPath + anAlphaImage;
        }

        aRes.mAlphaColor = 0xFFFFFF;
        String anAlphaColor = anAttrs.get("alphacolor");
        if (anAlphaColor != null) {
            aRes.mAlphaColor = (int) parseHex(anAlphaColor, aRes.mAlphaColor);
        }

        String aVariant = anAttrs.get("variant");
        if (aVariant != null) {
            aRes.mVariant = aVariant;
        }

        String anAlphaGrid = anAttrs.get("alphagrid");
        if (anAlphaGrid != null) {
            aRes.mAlphaGridImage = mDefaultPath + anAlphaGrid;
        }

        String aRows = anAttrs.get("rows");
        if (aRows != null) {
            aRes.mRows = (int) atoi(aRows);
        } else {
            aRes.mRows = 1;
        }

        String aCols = anAttrs.get("cols");
        if (aCols != null) {
            aRes.mCols = (int) atoi(aCols);
        } else {
            aRes.mCols = 1;
        }

        String anAnim = anAttrs.get("anim");
        AnimType anAnimType = AnimType.AnimType_None;
        if (anAnim != null) {
            if (anAnim.equalsIgnoreCase("none")) {
                anAnimType = AnimType.AnimType_None;
            } else if (anAnim.equalsIgnoreCase("once")) {
                anAnimType = AnimType.AnimType_Once;
            } else if (anAnim.equalsIgnoreCase("loop")) {
                anAnimType = AnimType.AnimType_Loop;
            } else if (anAnim.equalsIgnoreCase("pingpong")) {
                anAnimType = AnimType.AnimType_PingPong;
            } else {
                fail("Invalid animation type.");
                return false;
            }
        }
        aRes.mAnimInfo.mAnimType = anAnimType;
        if (anAnimType != AnimType.AnimType_None) {
            int aNumCels = Math.max(aRes.mRows, aRes.mCols);
            int aBeginDelay = 0;
            int anEndDelay = 0;

            String aFrameDelay = anAttrs.get("framedelay");
            if (aFrameDelay != null) {
                aRes.mAnimInfo.mFrameDelay = (int) atoi(aFrameDelay);
            }

            String aBeginDelayAttr = anAttrs.get("begindelay");
            if (aBeginDelayAttr != null) {
                aBeginDelay = (int) atoi(aBeginDelayAttr);
            }

            String anEndDelayAttr = anAttrs.get("enddelay");
            if (anEndDelayAttr != null) {
                anEndDelay = (int) atoi(anEndDelayAttr);
            }

            String aPerFrameDelay = anAttrs.get("perframedelay");
            if (aPerFrameDelay != null) {
                aRes.mAnimInfo.mPerFrameDelay = toIntArray(readIntVector(aPerFrameDelay));
            }

            String aFrameMap = anAttrs.get("framemap");
            if (aFrameMap != null) {
                aRes.mAnimInfo.mFrameMap = toIntArray(readIntVector(aFrameMap));
            }

            aRes.mAnimInfo.compute(aNumCels, aBeginDelay, anEndDelay);
        }

        return true;
    }

    private static int[] toIntArray(List<Integer> theList) {
        int[] aResult = new int[theList.size()];
        for (int i = 0; i < aResult.length; i++) {
            aResult[i] = theList.get(i);
        }
        return aResult;
    }

    /** Ported from ResourceManager.cpp:377-428. */
    protected boolean parseFontResource(XMLElement theElement) {
        FontRes aRes = new FontRes();
        BaseRes aNewRes = aRes;

        if (!parseCommonResource(theElement, aRes, mFontMap)) {
            if (mHadAlreadyDefinedError && mAllowAlreadyDefinedResources) {
                mError = "";
                mHasFailed = false;
                aRes = (FontRes) mFontMap.get(aNewRes.mId);
                aRes.mPath = aNewRes.mPath;
                aRes.mXMLAttributes = aNewRes.mXMLAttributes;
            } else {
                return false;
            }
        }

        String anImage = theElement.mAttributes.get("image");
        if (anImage != null) {
            aRes.mImagePath = anImage;
        }

        String aTags = theElement.mAttributes.get("tags");
        if (aTags != null) {
            aRes.mTags = aTags;
        }

        if (aRes.mPath.startsWith("!sys:")) {
            aRes.mSysFont = true;
            aRes.mPath = aRes.mPath.substring(5);

            String aSize = theElement.mAttributes.get("size");
            if (aSize == null) {
                return fail("SysFont needs point size");
            }

            aRes.mSize = (int) atoi(aSize);
            if (aRes.mSize <= 0) {
                return fail("SysFont needs point size");
            }

            aRes.mBold = theElement.mAttributes.containsKey("bold");
            aRes.mItalic = theElement.mAttributes.containsKey("italic");
            aRes.mShadow = theElement.mAttributes.containsKey("shadow");
            aRes.mUnderline = theElement.mAttributes.containsKey("underline");
        } else {
            aRes.mSysFont = false;
        }

        return true;
    }

    /** Ported from ResourceManager.cpp:430-442. */
    protected boolean parseSetDefaults(XMLElement theElement) {
        String aPath = theElement.mAttributes.get("path");
        if (aPath != null) {
            mDefaultPath = Common.removeTrailingSlash(aPath) + '/';
        }

        String anIdPrefix = theElement.mAttributes.get("idprefix");
        if (anIdPrefix != null) {
            mDefaultIdPrefix = Common.removeTrailingSlash(anIdPrefix);
        }

        return true;
    }

    /** Ported from ResourceManager.cpp:444-500. */
    protected boolean parseResources() {
        for (; ; ) {
            XMLElement aXMLElement = new XMLElement();
            if (!mXMLParser.nextElement(aXMLElement)) {
                return false;
            }

            if (aXMLElement.mType == XMLElement.TYPE_START) {
                if (aXMLElement.mValue.equals("Image")) {
                    if (!parseImageResource(aXMLElement)) {
                        return false;
                    }

                    if (!mXMLParser.nextElement(aXMLElement)) {
                        return false;
                    }

                    if (aXMLElement.mType != XMLElement.TYPE_END) {
                        return fail("Unexpected element found.");
                    }
                } else if (aXMLElement.mValue.equals("Sound")) {
                    if (!parseSoundResource(aXMLElement)) {
                        return false;
                    }

                    if (!mXMLParser.nextElement(aXMLElement)) {
                        return false;
                    }

                    if (aXMLElement.mType != XMLElement.TYPE_END) {
                        return fail("Unexpected element found.");
                    }
                } else if (aXMLElement.mValue.equals("Font")) {
                    if (!parseFontResource(aXMLElement)) {
                        return false;
                    }

                    if (!mXMLParser.nextElement(aXMLElement)) {
                        return false;
                    }

                    if (aXMLElement.mType != XMLElement.TYPE_END) {
                        return fail("Unexpected element found.");
                    }
                } else if (aXMLElement.mValue.equals("SetDefaults")) {
                    if (!parseSetDefaults(aXMLElement)) {
                        return false;
                    }

                    if (!mXMLParser.nextElement(aXMLElement)) {
                        return false;
                    }

                    if (aXMLElement.mType != XMLElement.TYPE_END) {
                        return fail("Unexpected element found.");
                    }
                } else {
                    fail("Invalid Section '" + aXMLElement.mValue + "'");
                    return false;
                }
            } else if (aXMLElement.mType == XMLElement.TYPE_ELEMENT) {
                fail("Element Not Expected '" + aXMLElement.mValue + "'");
                return false;
            } else if (aXMLElement.mType == XMLElement.TYPE_END) {
                return true;
            }
        }
    }

    /** Ported from ResourceManager.cpp:502-543. */
    protected boolean doParseResources() {
        if (!mXMLParser.hasFailed()) {
            for (; ; ) {
                XMLElement aXMLElement = new XMLElement();
                if (!mXMLParser.nextElement(aXMLElement)) {
                    break;
                }

                if (aXMLElement.mType == XMLElement.TYPE_START) {
                    if (aXMLElement.mValue.equals("Resources")) {
                        mCurResGroup = aXMLElement.mAttributes.getOrDefault("id", "");
                        mCurResGroupList = mResGroupMap.computeIfAbsent(mCurResGroup, k -> new ArrayList<>());

                        if (mCurResGroup.isEmpty()) {
                            fail("No id specified.");
                            break;
                        }

                        if (!parseResources()) {
                            break;
                        }
                    } else {
                        fail("Invalid Section '" + aXMLElement.mValue + "'");
                        break;
                    }
                } else if (aXMLElement.mType == XMLElement.TYPE_ELEMENT) {
                    fail("Element Not Expected '" + aXMLElement.mValue + "'");
                    break;
                }
            }
        }

        if (mXMLParser.hasFailed()) {
            fail(mXMLParser.getErrorText());
        }

        mXMLParser = null;

        return !mHasFailed;
    }

    /** Ported from ResourceManager.cpp:545-588. */
    public boolean parseResourcesFile(String theFilename) {
        mXMLParser = new XMLParser();
        if (!mXMLParser.openFile(theFilename)) {
            fail("Resource file not found: " + theFilename);
        }

        XMLElement aXMLElement = new XMLElement();
        while (!mXMLParser.hasFailed()) {
            if (!mXMLParser.nextElement(aXMLElement)) {
                fail(mXMLParser.getErrorText());
            }

            if (aXMLElement.mType == XMLElement.TYPE_START) {
                if (!aXMLElement.mValue.equals("ResourceManifest")) {
                    break;
                } else {
                    return doParseResources();
                }
            }
        }

        fail("Expecting ResourceManifest tag");

        return doParseResources();
    }

    /** Ported from ResourceManager.cpp:590-600. */
    public boolean reparseResourcesFile(String theFilename) {
        boolean oldDefined = mAllowAlreadyDefinedResources;
        mAllowAlreadyDefinedResources = true;

        boolean aResult = parseResourcesFile(theFilename);

        mAllowAlreadyDefinedResources = oldDefined;

        return aResult;
    }

    /** Ported from ResourceManager.cpp:602-641. */
    protected boolean loadAlphaGridImage(ImageRes theRes, GLImage theImage) {
        ImageLib.Image anAlphaImage = ImageLib.getImage(theRes.mAlphaGridImage, true);
        if (anAlphaImage == null) {
            return fail("Failed to load image: " + theRes.mAlphaGridImage);
        }

        int aNumRows = theRes.mRows;
        int aNumCols = theRes.mCols;

        int aCelWidth = theImage.mWidth / aNumCols;
        int aCelHeight = theImage.mHeight / aNumRows;

        if (anAlphaImage.mWidth != aCelWidth || anAlphaImage.mHeight != aCelHeight) {
            return fail("GridAlphaImage size mismatch between " + theRes.mPath + " and " + theRes.mAlphaGridImage);
        }

        int[] aMasterBits = theImage.getBits();
        int[] anAlphaBits = anAlphaImage.mBits;
        int aMasterIndex = 0;
        int anAlphaIndex = 0;
        for (int i = 0; i < aNumRows; i++) {
            int aMasterRowIndex = aMasterIndex;
            for (int j = 0; j < aNumCols; j++) {
                int aRowIndex = aMasterRowIndex;
                anAlphaIndex = 0;
                for (int y = 0; y < aCelHeight; y++) {
                    int aDestIndex = aRowIndex;
                    for (int x = 0; x < aCelWidth; x++) {
                        aMasterBits[aDestIndex] =
                                (aMasterBits[aDestIndex] & 0x00FFFFFF) | ((anAlphaBits[anAlphaIndex] & 0xFF) << 24);
                        anAlphaIndex++;
                        aDestIndex++;
                    }
                    aRowIndex += theImage.mWidth;
                }
                aMasterRowIndex += aCelWidth;
            }
            aMasterIndex += aCelHeight * theImage.mWidth;
        }

        theImage.bitsChanged();
        return true;
    }

    /** Ported from ResourceManager.cpp:643-676. */
    protected boolean loadAlphaImage(ImageRes theRes, GLImage theImage) {
        ImageLib.Image anAlphaImage = ImageLib.getImage(theRes.mAlphaImage, true);
        if (anAlphaImage == null) {
            return fail("Failed to load image: " + theRes.mAlphaImage);
        }

        if (anAlphaImage.mWidth != theImage.mWidth || anAlphaImage.mHeight != theImage.mHeight) {
            return fail("AlphaImage size mismatch between " + theRes.mPath + " and " + theRes.mAlphaImage);
        }

        int[] aBits1 = theImage.getBits();
        int[] aBits2 = anAlphaImage.mBits;
        int aSize = theImage.mWidth * theImage.mHeight;

        for (int i = 0; i < aSize; i++) {
            aBits1[i] = (aBits1[i] & 0x00FFFFFF) | ((aBits2[i] & 0xFF) << 24);
        }

        theImage.bitsChanged();
        return true;
    }

    /** Ported from ResourceManager.cpp:678-752. */
    protected boolean doLoadImage(ImageRes theRes) {
        boolean[] isNew = new boolean[1];
        ImageLib.gAlphaComposeColor = theRes.mAlphaColor;
        SharedImageRef aSharedImageRef = mApp.getSharedImage(theRes.mPath, theRes.mVariant, isNew);
        ImageLib.gAlphaComposeColor = 0xFFFFFF;

        GLImage aGLImage = aSharedImageRef == null || aSharedImageRef.get() == null
                ? null
                : (aSharedImageRef.get() instanceof GLImage aGl ? aGl : null);

        if (aGLImage == null) {
            return fail("Failed to load image: " + theRes.mPath);
        }

        if (isNew[0]) {
            if (!theRes.mAlphaImage.isEmpty()) {
                if (!loadAlphaImage(theRes, aGLImage)) {
                    return false;
                }
            }

            if (!theRes.mAlphaGridImage.isEmpty()) {
                if (!loadAlphaGridImage(theRes, aGLImage)) {
                    return false;
                }
            }
        }

        aGLImage.commitBits();
        theRes.mImage = aSharedImageRef;
        aGLImage.mPurgeBits = theRes.mPurgeBits;

        if (theRes.mDDSurface) {
            aGLImage.commitBits();

            if (!aGLImage.mHasAlpha) {
                aGLImage.mPurgeBits = true;
            }
        }

        if (theRes.mA4R4G4B4) {
            aGLImage.mRenderFlags |= MemoryImage.RenderImageFlag_UseA4R4G4B4;
        }

        if (theRes.mA8R8G8B8) {
            aGLImage.mRenderFlags |= MemoryImage.RenderImageFlag_UseA8R8G8B8;
        }

        if (theRes.mMinimizeSubdivisions) {
            aGLImage.mRenderFlags |= MemoryImage.RenderImageFlag_MinimizeNumSubdivisions;
        }

        if (theRes.mAnimInfo.mAnimType != AnimType.AnimType_None) {
            aGLImage.mAnimInfo = new AnimInfo(theRes.mAnimInfo);
        }

        aGLImage.mNumRows = theRes.mRows;
        aGLImage.mNumCols = theRes.mCols;

        if (aGLImage.mPurgeBits) {
            aGLImage.purgeBits();
        }

        resourceLoadedHook(theRes);
        return true;
    }

    /** Ported from ResourceManager.cpp:754-757. */
    public void deleteImage(String theName) {
        replaceImage(theName, null);
    }

    /** Ported from ResourceManager.cpp:759-776. */
    public SharedImageRef loadImage(String theName) {
        BaseRes aBaseRes = mImageMap.get(theName);
        if (aBaseRes == null) {
            return null;
        }

        ImageRes aRes = (ImageRes) aBaseRes;
        if (aRes.mImage.get() != null) {
            return aRes.mImage;
        }

        if (aRes.mFromProgram) {
            return null;
        }

        if (!doLoadImage(aRes)) {
            return null;
        }

        return aRes.mImage;
    }

    /** Ported from ResourceManager.cpp:778-801. */
    protected boolean doLoadSound(SoundRes theRes) {
        SoundManager aSoundManager = mApp.getSoundManager();
        long aSoundId = aSoundManager.getFreeSoundId();
        if (aSoundId < 0) {
            return fail("Out of free sound ids");
        }

        if (!aSoundManager.loadSound(aSoundId, theRes.mPath)) {
            return fail("Failed to load sound: " + theRes.mPath);
        }

        if (theRes.mVolume >= 0) {
            aSoundManager.setBaseVolume(aSoundId, theRes.mVolume);
        }

        if (theRes.mPanning != 0) {
            aSoundManager.setBasePan(aSoundId, theRes.mPanning);
        }

        theRes.mSoundId = aSoundId;

        resourceLoadedHook(theRes);
        return true;
    }

    /** Ported from ResourceManager.cpp:803-863. */
    protected boolean doLoadFont(FontRes theRes) {
        Font aFont = null;

        if (theRes.mSysFont) {
            // System fonts not supported.
        } else if (theRes.mImagePath.isEmpty()) {
            if (theRes.mPath.startsWith("!ref:")) {
                String aRefName = theRes.mPath.substring(5);
                Font aRefFont = getFont(aRefName);
                if (aRefFont == null) {
                    return fail("Ref font not found: " + aRefName);
                }

                aFont = aRefFont.duplicate();
            } else {
                aFont = new ImageFont(mApp, theRes.mPath);
            }
        } else {
            Image anImage = mApp.getImage(theRes.mImagePath);
            if (anImage == null) {
                return fail("Failed to load image: " + theRes.mImagePath);
            }

            theRes.mImage = anImage;
            aFont = new ImageFont(anImage, theRes.mPath);
        }

        if (aFont instanceof ImageFont anImageFont) {
            if (!anImageFont.isInitialized()) {
                return fail("Failed to load font: " + theRes.mPath);
            }

            if (!theRes.mTags.isEmpty()) {
                String aDelim = ", \r\n\t";
                int aPos = indexOfNot(theRes.mTags, aDelim, 0);
                while (aPos != -1) {
                    int aEnd = indexOfAny(theRes.mTags, aDelim, aPos);
                    anImageFont.addTag(aEnd == -1 ? theRes.mTags.substring(aPos) : theRes.mTags.substring(aPos, aEnd));
                    aPos = indexOfNot(theRes.mTags, aDelim, aEnd == -1 ? theRes.mTags.length() : aEnd);
                }
                anImageFont.prepare();
            }
        }

        theRes.mFont = aFont;

        resourceLoadedHook(theRes);
        return true;
    }

    /** Ported from ResourceManager.cpp:865-882. */
    public Font loadFont(String theName) {
        BaseRes aBaseRes = mFontMap.get(theName);
        if (aBaseRes == null) {
            return null;
        }

        FontRes aRes = (FontRes) aBaseRes;
        if (aRes.mFont != null) {
            return aRes.mFont;
        }

        if (aRes.mFromProgram) {
            return null;
        }

        if (!doLoadFont(aRes)) {
            return null;
        }

        return aRes.mFont;
    }

    /** Ported from ResourceManager.cpp:884-887. */
    public void deleteFont(String theName) {
        replaceFont(theName, null);
    }

    /** Ported from ResourceManager.cpp:889-936. Loads exactly one pending resource. */
    public boolean loadNextResource() {
        if (hadError()) {
            return false;
        }

        if (mCurResGroupList == null) {
            return false;
        }

        while (mCurResGroupListItr != mCurResGroupList.size()) {
            BaseRes aRes = mCurResGroupList.get(mCurResGroupListItr++);
            if (aRes.mFromProgram) {
                continue;
            }

            switch (aRes.mType) {
                case RES_TYPE_IMAGE: {
                    ImageRes anImageRes = (ImageRes) aRes;
                    if (anImageRes.mImage.get() != null) {
                        continue;
                    }

                    return doLoadImage(anImageRes);
                }

                case RES_TYPE_SOUND: {
                    SoundRes aSoundRes = (SoundRes) aRes;
                    if (aSoundRes.mSoundId != -1) {
                        continue;
                    }

                    return doLoadSound(aSoundRes);
                }

                case RES_TYPE_FONT: {
                    FontRes aFontRes = (FontRes) aRes;
                    if (aFontRes.mFont != null) {
                        continue;
                    }

                    return doLoadFont(aFontRes);
                }

                default:
                    break;
            }
        }

        return false;
    }

    /** Ported from ResourceManager.cpp:938-939. */
    public void resourceLoadedHook(BaseRes theRes) {
    }

    /** Ported from ResourceManager.cpp:941-948. */
    public void startLoadResources(String theGroup) {
        mError = "";
        mHasFailed = false;

        mCurResGroup = theGroup;
        mCurResGroupList = mResGroupMap.computeIfAbsent(theGroup, k -> new ArrayList<>());
        mCurResGroupListItr = 0;
    }

    /** Ported from ResourceManager.cpp:950-977. */
    public void dumpCurResGroup(StringBuilder theDestStr) {
        List<BaseRes> aResList = mResGroupMap.get(mCurResGroup);
        if (aResList == null) {
            aResList = new ArrayList<>();
        }
        theDestStr.append("About to dump ").append(aResList.size())
                .append(" elements from current res group name ").append(mCurResGroup).append("\r\n");

        for (int i = 0; i < aResList.size(); i++) {
            BaseRes br = aResList.get(i);
            theDestStr.append(br.mId).append(": ").append(br.mPath).append("\r\n");
            if (br.mFromProgram) {
                theDestStr.append("     res is from program\r\n");
            } else if (br.mType == ResType.RES_TYPE_IMAGE) {
                theDestStr.append("     res is an image\r\n");
            } else if (br.mType == ResType.RES_TYPE_SOUND) {
                theDestStr.append("     res is a sound\r\n");
            } else if (br.mType == ResType.RES_TYPE_FONT) {
                theDestStr.append("     res is a font\r\n");
            }

            if (i == mCurResGroupListItr) {
                theDestStr.append("iterator has reached mCurResGroupItr\r\n");
            }
        }

        theDestStr.append("Done dumping resources\r\n");
    }

    /** Ported from ResourceManager.cpp:979-995. */
    public boolean loadResources(String theGroup) {
        mError = "";
        mHasFailed = false;
        startLoadResources(theGroup);
        while (loadNextResource()) {
        }

        if (!hadError()) {
            mLoadedGroups.add(theGroup);
            return true;
        } else {
            return false;
        }
    }

    /** Ported from ResourceManager.cpp:997-1011. */
    protected int getNumResources(String theGroup, Map<String, BaseRes> theMap) {
        if (theGroup.isEmpty()) {
            return theMap.size();
        }

        int aCount = 0;
        for (BaseRes aRes : theMap.values()) {
            if (aRes.mResGroup.equals(theGroup) && !aRes.mFromProgram) {
                aCount++;
            }
        }

        return aCount;
    }

    /** Ported from ResourceManager.cpp:1013-1016. */
    public int getNumImages(String theGroup) {
        return getNumResources(theGroup, mImageMap);
    }

    /** Ported from ResourceManager.cpp:1018-1021. */
    public int getNumSounds(String theGroup) {
        return getNumResources(theGroup, mSoundMap);
    }

    /** Ported from ResourceManager.cpp:1023-1026. */
    public int getNumFonts(String theGroup) {
        return getNumResources(theGroup, mFontMap);
    }

    /** Ported from ResourceManager.cpp:1028-1031. */
    public int getNumResources(String theGroup) {
        return getNumImages(theGroup) + getNumSounds(theGroup) + getNumFonts(theGroup);
    }

    /** Ported from ResourceManager.cpp:1033-1041. Never {@code null}; {@code isNull()} when missing. */
    public SharedImageRef getImage(String theId) {
        BaseRes aRes = mImageMap.get(theId);
        if (aRes != null) {
            return ((ImageRes) aRes).mImage;
        }
        return new SharedImageRef();
    }

    /** Ported from ResourceManager.cpp:1043-1050. */
    public long getSound(String theId) {
        BaseRes aRes = mSoundMap.get(theId);
        if (aRes != null) {
            return ((SoundRes) aRes).mSoundId;
        }
        return -1;
    }

    /** Ported from ResourceManager.cpp:1052-1060. */
    public Font getFont(String theId) {
        BaseRes aRes = mFontMap.get(theId);
        if (aRes != null) {
            return ((FontRes) aRes).mFont;
        }
        return null;
    }

    /** Ported from ResourceManager.cpp:1062-1079. */
    public SharedImageRef getImageThrow(String theId) {
        BaseRes aBaseRes = mImageMap.get(theId);
        if (aBaseRes != null) {
            ImageRes aRes = (ImageRes) aBaseRes;
            if (aRes.mImage.get() != null) {
                return aRes.mImage;
            }

            if (mAllowMissingProgramResources && aRes.mFromProgram) {
                return new SharedImageRef();
            }
        }

        fail("Image resource not found: " + theId);
        throw new ResourceManagerException(getErrorText());
    }

    /** Ported from ResourceManager.cpp:1081-1098. */
    public long getSoundThrow(String theId) {
        BaseRes aBaseRes = mSoundMap.get(theId);
        if (aBaseRes != null) {
            SoundRes aRes = (SoundRes) aBaseRes;
            if (aRes.mSoundId != -1) {
                return aRes.mSoundId;
            }

            if (mAllowMissingProgramResources && aRes.mFromProgram) {
                return -1;
            }
        }

        fail("Sound resource not found: " + theId);
        throw new ResourceManagerException(getErrorText());
    }

    /** Ported from ResourceManager.cpp:1100-1117. */
    public Font getFontThrow(String theId) {
        BaseRes aBaseRes = mFontMap.get(theId);
        if (aBaseRes != null) {
            FontRes aRes = (FontRes) aBaseRes;
            if (aRes.mFont != null) {
                return aRes.mFont;
            }

            if (mAllowMissingProgramResources && aRes.mFromProgram) {
                return null;
            }
        }

        fail("Font resource not found: " + theId);
        throw new ResourceManagerException(getErrorText());
    }

    /** Ported from ResourceManager.cpp:1119-1122. */
    public void setAllowMissingProgramImages(boolean allow) {
        mAllowMissingProgramResources = allow;
    }

    /** Ported from ResourceManager.cpp:1124-1138. */
    public boolean replaceImage(String theId, Image theImage) {
        BaseRes aBaseRes = mImageMap.get(theId);
        if (aBaseRes != null) {
            aBaseRes.deleteResource();
            ImageRes aRes = (ImageRes) aBaseRes;
            aRes.mImage = new SharedImageRef();
            aRes.mImage.set(theImage);
            return true;
        }
        return false;
    }

    /** Ported from ResourceManager.cpp:1140-1150. */
    public boolean replaceSound(String theId, long theSound) {
        BaseRes aBaseRes = mSoundMap.get(theId);
        if (aBaseRes != null) {
            aBaseRes.deleteResource();
            ((SoundRes) aBaseRes).mSoundId = theSound;
            return true;
        }
        return false;
    }

    /** Ported from ResourceManager.cpp:1152-1162. */
    public boolean replaceFont(String theId, Font theFont) {
        BaseRes aBaseRes = mFontMap.get(theId);
        if (aBaseRes != null) {
            aBaseRes.deleteResource();
            ((FontRes) aBaseRes).mFont = theFont;
            return true;
        }
        return false;
    }

    /** Ported from ResourceManager.cpp:1164-1175. */
    public Map<String, String> getImageAttributes(String theId) {
        BaseRes aRes = mImageMap.get(theId);
        if (aRes != null) {
            return aRes.mXMLAttributes;
        }
        return Map.of();
    }

    /** Ported from ResourceManager.h:225. */
    public List<BaseRes> getCurResGroupList() {
        return mCurResGroupList;
    }

    /** Ported from ResourceManager.h:226. */
    public String getCurResGroup() {
        return mCurResGroup;
    }

    /** Upstream {@code mApp->Is3DAccelerated()}; null-safe so the manifest can be parsed headlessly. */
    protected boolean is3DAccelerated() {
        return mApp != null && mApp.is3DAccelerated();
    }

    private static long atoi(String theValue) {
        return parseLong(theValue, 0);
    }

    private static long parseLong(String theValue, long theDefault) {
        int i = 0;
        int len = theValue.length();
        while (i < len && Character.isWhitespace(theValue.charAt(i))) {
            i++;
        }
        int start = i;
        if (i < len && (theValue.charAt(i) == '+' || theValue.charAt(i) == '-')) {
            i++;
        }
        while (i < len && theValue.charAt(i) >= '0' && theValue.charAt(i) <= '9') {
            i++;
        }
        if (i == start || (i == start + 1 && (theValue.charAt(start) == '+' || theValue.charAt(start) == '-'))) {
            return theDefault;
        }
        try {
            return Long.parseLong(theValue.substring(start, i));
        } catch (NumberFormatException e) {
            return theDefault;
        }
    }

    private static double parseDouble(String theValue, double theDefault) {
        try {
            return Double.parseDouble(theValue.trim());
        } catch (NumberFormatException e) {
            return theDefault;
        }
    }

    private static long parseHex(String theValue, long theDefault) {
        try {
            return Long.parseUnsignedLong(theValue.trim(), 16);
        } catch (NumberFormatException e) {
            return theDefault;
        }
    }

    private static int indexOfNot(String theString, String theChars, int theFrom) {
        for (int i = theFrom; i < theString.length(); i++) {
            if (theChars.indexOf(theString.charAt(i)) == -1) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfAny(String theString, String theChars, int theFrom) {
        for (int i = theFrom; i < theString.length(); i++) {
            if (theChars.indexOf(theString.charAt(i)) != -1) {
                return i;
            }
        }
        return -1;
    }
}
