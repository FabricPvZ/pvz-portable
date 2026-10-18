package cn.enaium.fabricpvz.portable.pvzp;

import java.util.Map;
import java.util.function.Supplier;

import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.PerfTimer;
import cn.enaium.fabricpvz.portable.sexy.ResourceManager;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.SharedImageRef;

/**
 * Ported from src/PvzpLib/PvzpCommon.h:85-96 and src/PvzpLib/PvzpCommon.cpp:1006-1180, plus
 * src/LawnApp.cpp:117 (LawnApp installs this subclass instead of the base {@code ResourceManager}).
 *
 * <p>Adds the game's group loader ({@link #pvzpLoadResources}), the per-resource step that also
 * marks loaded images for "sanding" ({@link #pvzpLoadNextResource}), the path lookups used when
 * writing save data ({@link #findImagePath}/{@link #findFontPath}) and a way to register images that
 * live outside the manifest ({@link #addImageToMap}).
 *
 * <p>The framework's {@code gExtractResourcesByName} function pointer becomes the
 * {@link #gExtractResourcesByName} static field, and the game's {@code LawnGetCurrentLevelName()}
 * log context becomes {@link #gGetCurrentLevelName}.
 */
public class PvzpResourceManager extends ResourceManager {

    /**
     * Ported from src/PvzpLib/PvzpCommon.h:45 and PvzpCommon.cpp:653-656
     * ({@code PvzpMarkImageForSanding}). Bit set on {@link MemoryImage#mRenderFlags}; the draw path
     * later consumes it in {@code PvzpSandImageIfNeeded}.
     */
    public static final int RENDERIMAGEFLAG_SANDING = 0x1000;

    /** Upstream {@code gExtractResourcesByName} (Resources.h:1905); installed by the game layer. */
    public static ResourceExtractor gExtractResourcesByName;

    /** Upstream {@code LawnGetCurrentLevelName()} (LawnApp.cpp:3139); installed by the game layer. */
    public static Supplier<String> gGetCurrentLevelName = () -> "Before App";

    /** Ported from PvzpCommon.cpp:1009-1041. */
    public PvzpResourceManager(SexyAppBase theApp) {
        super(theApp);
    }

    /** Ported from PvzpCommon.cpp:1009-1041. */
    public boolean pvzpLoadResources(String theGroup) {
        if (isGroupLoaded(theGroup)) {
            return true;
        }

        PerfTimer aTimer = new PerfTimer();
        aTimer.start();

        startLoadResources(theGroup);
        while (mApp != null && !mApp.mShutdown.get() && pvzpLoadNextResource()) {
        }
        if (mApp != null && mApp.mShutdown.get()) {
            return false;
        }

        if (hadError()) {
            if (mApp != null) {
                mApp.showResourceError(true);
            }
            return false;
        }

        if (gExtractResourcesByName != null && !gExtractResourcesByName.extract(this, theGroup)) {
            if (mApp != null) {
                mApp.showResourceError(true);
            }
            return false;
        }

        mLoadedGroups.add(theGroup);

        int aDuration = (int) Math.max(aTimer.getDuration(), 0.0);
        if (aDuration > 20) {
            // PORT-NOTE: a load-time trace (one line per resource group, only when it was slow), so
            // it is DEBUG like the rest of the loading traces; the errors of this method
            // (showResourceError) stay on the game's INFO/ERROR channel.
            Common.logDebugLn("LOADED: '{}' {} ms on {}", theGroup, aDuration, gGetCurrentLevelName.get());
        }

        return true;
    }

    /** Ported from PvzpCommon.cpp:1065-1125. Loads one resource, applying the sanding mark. */
    public boolean pvzpLoadNextResource() {
        while (mCurResGroupListItr != mCurResGroupList.size()) {
            BaseRes aRes = mCurResGroupList.get(mCurResGroupListItr);
            if (aRes.mFromProgram) {
                mCurResGroupListItr++;
                continue;
            }

            switch (aRes.mType) {
                case RES_TYPE_IMAGE: {
                    ImageRes anImageRes = (ImageRes) aRes;
                    if (anImageRes.mImage.get() != null) {
                        mCurResGroupListItr++;
                        continue;
                    }
                    break;
                }

                case RES_TYPE_SOUND: {
                    SoundRes aSoundRes = (SoundRes) aRes;
                    if (aSoundRes.mSoundId != -1) {
                        mCurResGroupListItr++;
                        continue;
                    }
                    break;
                }

                case RES_TYPE_FONT: {
                    FontRes aFontRes = (FontRes) aRes;
                    if (aFontRes.mFont != null) {
                        mCurResGroupListItr++;
                        continue;
                    }
                    break;
                }

                default:
                    break;
            }

            if (!loadNextResource()) {
                break;
            }

            if (aRes.mType == ResType.RES_TYPE_IMAGE) {
                ImageRes anImageRes = (ImageRes) aRes;
                Image aImage = anImageRes.mImage.get();
                if (aImage != null) {
                    markImageForSanding(aImage);
                }
            }

            return true;
        }

        return false;
    }

    /** Ported from PvzpCommon.cpp:1050-1057. */
    /**
     * Installs the {@link Definition.DefinitionResourceResolver} used by the reanim/particle
     * definition loader.
     *
     * <p>Upstream reached the {@code gSexyAppBase} global directly from
     * {@code DefinitionLoadImage} (Definition.cpp:332-361); the Java port routes those four calls
     * through this resolver so the definition layer stays free of app dependencies. Without it the
     * 1561 reanim image ids that are not listed in {@code properties/resources.xml} cannot resolve
     * through the {@code reanim/} + {@code images/} fallback tables.
     */
    public void installDefinitionResourceResolver() {
        Definition.gDefResourceResolver = new Definition.DefinitionResourceResolver() {
            @Override
            public Image loadImage(String theName) {
                SharedImageRef aRef = mApp == null ? null : mApp.mResourceManager.loadImage(theName);
                return aRef == null || aRef.isNull() ? null : aRef.get();
            }

            @Override
            public Image getSharedImage(String thePathToTry) {
                if (mApp == null) {
                    return null;
                }
                boolean[] aIsNew = new boolean[1];
                SharedImageRef aRef = mApp.getSharedImage(thePathToTry, "", aIsNew);
                return aRef == null || aRef.isNull() ? null : aRef.get();
            }

            @Override
            public void addImageToMap(Image theImage, String theName) {
                PvzpResourceManager.this.addImageToMap(new SharedImageRef(theImage), theName);
            }

            @Override
            public Font loadFont(String theName) {
                return mApp == null ? null : mApp.mResourceManager.loadFont(theName);
            }

            @Override
            public String findImagePath(Image theImage) {
                String[] aPath = new String[1];
                return PvzpResourceManager.this.findImagePath(theImage, aPath) ? aPath[0] : "";
            }

            @Override
            public String findFontPath(Font theFont) {
                String[] aPath = new String[1];
                return PvzpResourceManager.this.findFontPath(theFont, aPath) ? aPath[0] : "";
            }
        };
    }

    public void addImageToMap(SharedImageRef theImage, String thePath) {
        assert mImageMap.get(thePath) == null;

        ImageRes aImageRes = new ImageRes();
        aImageRes.mImage = theImage;
        aImageRes.mPath = thePath;
        mImageMap.put(thePath, aImageRes);
    }

    /** Ported from PvzpCommon.cpp:1143-1156. Writes the font's manifest id into {@code thePath[0]}. */
    public boolean findFontPath(Font theFont, String[] thePath) {
        if (theFont == null) {
            // Same trap as findImagePath: a null member would match the first font entry that holds no
            // font, writing that entry's name into the cache for a slot that has no font at all.
            return false;
        }
        for (Map.Entry<String, BaseRes> anItr : mFontMap.entrySet()) {
            FontRes aFontRes = (FontRes) anItr.getValue();
            if (aFontRes.mFont != null && aFontRes.mFont == theFont) {
                thePath[0] = anItr.getKey();
                return true;
            }
        }
        return false;
    }

    /** Ported from PvzpCommon.cpp:1158-1171. Writes the image's manifest id into {@code thePath[0]}. */
    public boolean findImagePath(Image theImage, String[] thePath) {
        if (theImage == null) {
            // A null member must not match an entry whose image reference is empty: that is what wrote
            // the background image's name into every empty cached image slot of every reanimation
            // definition, which then reloaded as the background picture (see ../verification/VERIFICATION.md §12).
            return false;
        }
        for (Map.Entry<String, BaseRes> anItr : mImageMap.entrySet()) {
            ImageRes aImageRes = (ImageRes) anItr.getValue();
            if (aImageRes.mImage.get() != null && aImageRes.mImage.get() == theImage) {
                thePath[0] = anItr.getKey();
                return true;
            }
        }
        return false;
    }

    /** Upstream {@code PvzpMarkImageForSanding} (PvzpCommon.cpp:653-656). */
    private static void markImageForSanding(Image theImage) {
        if (theImage instanceof MemoryImage aMemoryImage) {
            aMemoryImage.mRenderFlags |= RENDERIMAGEFLAG_SANDING;
        }
    }
}
