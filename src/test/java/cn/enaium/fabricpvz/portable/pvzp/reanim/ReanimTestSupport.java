package cn.enaium.fabricpvz.portable.pvzp.reanim;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Test-only helpers for the reanimation runtime tests: hand-built
 * {@link ReanimatorDefinition}s and a {@link Image} that records every matrix blit it receives.
 */
final class ReanimTestSupport {

    private ReanimTestSupport() {
    }

    /** A plain 1×1-cel image of the given size (upstream {@code Image} without render backing). */
    static Image image(int theWidth, int theHeight) {
        Image aImage = new Image();
        aImage.mWidth = theWidth;
        aImage.mHeight = theHeight;
        aImage.mNumCols = 1;
        aImage.mNumRows = 1;
        return aImage;
    }

    static ReanimatorTransform transform(float theTransX, float theTransY, float theSkewX, float theSkewY,
                                         float theScaleX, float theScaleY, float theFrame, float theAlpha, Image theImage) {
        ReanimatorTransform aTransform = new ReanimatorTransform();
        aTransform.mTransX = theTransX;
        aTransform.mTransY = theTransY;
        aTransform.mSkewX = theSkewX;
        aTransform.mSkewY = theSkewY;
        aTransform.mScaleX = theScaleX;
        aTransform.mScaleY = theScaleY;
        aTransform.mFrame = theFrame;
        aTransform.mAlpha = theAlpha;
        aTransform.mImage = theImage;
        aTransform.mFont = null;
        aTransform.mText = "";
        return aTransform;
    }

    static ReanimatorTrack track(String theName, ReanimatorTransform... theTransforms) {
        ReanimatorTrack aTrack = new ReanimatorTrack();
        aTrack.mName = theName;
        aTrack.mTransforms = new DefinitionArrayDef(theTransforms);
        return aTrack;
    }

    static ReanimatorDefinition definition(float theFPS, ReanimatorTrack... theTracks) {
        ReanimatorDefinition aDefinition = new ReanimatorDefinition();
        aDefinition.mTracks = new DefinitionArrayDef(theTracks);
        aDefinition.mFPS = theFPS;
        return aDefinition;
    }

    /**
     * Installs a one-entry type registry ({@code theType} at index {@code theType}) so tests can
     * drive {@code reanimationInitializeType} without touching any file on disk.
     */
    static void installSingleEntryRegistry(int theType, String theFileName, int theFlags) {
        Reanimator.gReanimationParamArraySize = theType + 1;
        Reanimator.gReanimationParamArray = new ReanimationParams[theType + 1];
        Reanimator.gReanimatorDefArray = new ReanimatorDefinition[theType + 1];
        Reanimator.gReanimatorDefCount = theType + 1;
        for (int i = 0; i <= theType; i++) {
            // theReanimationType must equal the index, like upstream's table contract.
            Reanimator.gReanimationParamArray[i] = new ReanimationParams(i, theFileName, theFlags);
            Reanimator.gReanimatorDefArray[i] = new ReanimatorDefinition();
        }
    }

    static void freeRegistry() {
        Reanimator.gReanimatorDefArray = null;
        Reanimator.gReanimatorDefCount = 0;
        Reanimator.gReanimationParamArray = null;
        Reanimator.gReanimationParamArraySize = 0;
    }

    /** One recorded {@code Image.bltMatrix} call. */
    static final class Blit {
        Image mImage;
        float m00;
        float m01;
        float m02;
        float m10;
        float m11;
        float m12;
        Color mColor;
        int mDrawMode;
        Rect mSrcRect;
    }

    /**
     * Records every {@code bltMatrix} the graphics layer issues (the destination image of
     * {@code PvzpCommon.pvzpBltMatrix}).
     */
    static final class RecordingImage extends Image {
        final List<Blit> mBlits = new ArrayList<>();

        RecordingImage(int theWidth, int theHeight) {
            mWidth = theWidth;
            mHeight = theHeight;
            mNumCols = 1;
            mNumRows = 1;
        }

        @Override
        public void bltMatrix(Image theImage, float x, float y, SexyMatrix3 theMatrix, Rect theClipRect,
                              Color theColor, int theDrawMode, Rect theSrcRect, boolean blend) {
            Blit aBlit = new Blit();
            aBlit.mImage = theImage;
            aBlit.m00 = theMatrix.m00;
            aBlit.m01 = theMatrix.m01;
            aBlit.m02 = theMatrix.m02;
            aBlit.m10 = theMatrix.m10;
            aBlit.m11 = theMatrix.m11;
            aBlit.m12 = theMatrix.m12;
            aBlit.mColor = new Color(theColor.mRed, theColor.mGreen, theColor.mBlue, theColor.mAlpha);
            aBlit.mDrawMode = theDrawMode;
            aBlit.mSrcRect = theSrcRect;
            mBlits.add(aBlit);
        }
    }
}
