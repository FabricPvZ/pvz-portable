package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.lawn.AttachmentID;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.pvzp.fx.FilterEffect;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/PvzpLib/Reanimator.h:187-268 (class Reanimation) and
 * src/PvzpLib/Reanimator.cpp:29 (NO_BASE_POSE), :310-421 (construction/initialisation),
 * :423-523 (Update), :525-644 (BlendTransform/GetCurrentTransform/GetTransformAtTime/MatrixFromTransform/
 * ReanimBltMatrix), :646-826 (DrawTrack), :847-962 (GetTrackMatrix/DrawRenderGroup/Draw/DrawAllRenderGroups),
 * :964-1059 (FindTrackIndex/GetTrackInstanceByName/AttachToAnotherReanimation/GetTrackBasePoseMatrix/
 * GetAttachmentOverlayMatrix/GetFramesForLayer), :1061-1150 (SetFramesForLayer/StartBlend/ReanimationDie/
 * SetShakeOverride/SetPosition/OverrideScale/image overrides/SetTruncateDisappearingFrames),
 * :1238-1306 (GetTrackVelocity/IsTrackShowing/ShowOnlyTrack/AssignRenderGroupToTrack/AssignRenderGroupToPrefix,
 * PropogateColorToAttachments/ShouldTriggerTimedEvent), :1308-1318 (PlayReanim), :1320-1489 (attacher tracks)
 * and :1491-1508 (FindSubReanim).
 *
 * <p>The animation player. <b>Drawing contract</b>
 * (../verification/research/02-graphics-audio.md §8 and
 * ../verification/research/04-reanim-ui.md §1.3/§6.2): every blit goes through
 * {@code Graphics}/{@code Image} with an explicit colour, source rect and draw mode;
 * {@code cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.pvzpBltMatrix} is the shared matrix blit.
 *
 * <p>PORT-NOTEs for this class:
 * <ul>
 *   <li>The upstream software fast path in {@code ReanimBltMatrix} (Reanimator.cpp:612-644) is not
 *       ported: the Java renderer always takes the matrix path, as recommended by
 *       ../verification/research/04-reanim-ui.md §6.2.</li>
 *   <li>Atlas handles are real {@code Image} references looked up through
 *       {@link ReanimAtlas#getEncodedReanimAtlas(Image)} instead of upstream's fake small pointers,
 *       so the {@code (uintptr_t)image <= 1000} guards do not exist here.</li>
 *   <li>{@code PvzpTriangleGroup} batching is not used (the particle/effect agent's triangle group
 *       belongs to particles); each track blits directly, which preserves the upstream draw order.</li>
 * </ul>
 */
public class Reanimation {
    /** Upstream {@code RENDER_GROUP_HIDDEN} (Reanimator.h:115). */
    public static final int RENDER_GROUP_HIDDEN = -1;
    /** Upstream {@code RENDER_GROUP_NORMAL} (Reanimator.h:116). */
    public static final int RENDER_GROUP_NORMAL = 0;
    /** Upstream {@code RENDER_GROUP_MAX} (Reanimator.h:117). */
    public static final int RENDER_GROUP_MAX = 17;
    /** Upstream {@code NO_BASE_POSE} (Reanimator.cpp:29). */
    public static final int NO_BASE_POSE = -2;

    private static final float FLT_EPSILON = 1.1920929E-7f;

    public int mReanimationType;
    public float mAnimTime;
    public float mAnimRate;
    public ReanimatorDefinition mDefinition;
    public int mLoopType;
    public boolean mDead;
    public int mFrameStart;
    public int mFrameCount;
    public int mFrameBasePose;
    public SexyTransform2D mOverlayMatrix;
    public Color mColorOverride;
    public ReanimatorTrackInstance[] mTrackInstances;
    public int mLoopCount;
    public ReanimationHolder mReanimationHolder;
    public boolean mIsAttachment;
    public int mRenderOrder;
    public Color mExtraAdditiveColor;
    public boolean mEnableExtraAdditiveDraw;
    public Color mExtraOverlayColor;
    public boolean mEnableExtraOverlayDraw;
    public float mLastFrameTime;
    public int mFilterEffect;

    /** Ported from src/PvzpLib/Reanimator.cpp:310-340 (Reanimation::Reanimation). */
    public Reanimation() {
        mAnimTime = 0;
        mAnimRate = 12.0f;
        mDefinition = null;
        mLoopType = ReanimLoopType.REANIM_PLAY_ONCE;
        mLastFrameTime = -1.0f;
        mDead = false;
        mFrameStart = 0;
        mFrameCount = 0;
        mFrameBasePose = -1;
        mOverlayMatrix = new SexyTransform2D();
        mColorOverride = new Color(255, 255, 255, 255);  // Color::White copied by value
        mExtraAdditiveColor = new Color(255, 255, 255, 255);
        mEnableExtraAdditiveDraw = false;
        mExtraOverlayColor = new Color(255, 255, 255, 255);
        mEnableExtraOverlayDraw = false;
        mLoopCount = 0;
        mIsAttachment = false;
        mRenderOrder = 0;
        mReanimationHolder = null;
        mTrackInstances = null;
        mFilterEffect = FilterEffect.FILTER_EFFECT_NONE;
        mReanimationType = ReanimationType.REANIM_NONE;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:341-350 (ReanimationDelete). */
    public void reanimationDelete() {
        mTrackInstances = null;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:352-358 (ReanimationInitializeType). */
    public void reanimationInitializeType(float theX, float theY, int theReanimType) {
        Reanimator.reanimatorEnsureDefinitionLoaded(theReanimType, false);
        mReanimationType = theReanimType;
        reanimationInitialize(theX, theY, Reanimator.gReanimatorDefArray[theReanimType]);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:397-421 (ReanimationInitialize). */
    public void reanimationInitialize(float theX, float theY, ReanimatorDefinition theDefinition) {
        Reanimator.reanimationCreateAtlas(theDefinition, mReanimationType);
        mDead = false;
        setPosition(theX, theY);
        mDefinition = theDefinition;
        mAnimRate = theDefinition.mFPS;
        mLastFrameTime = -1.0f;

        ReanimatorTrack[] aTracks = Reanimator.tracksOf(theDefinition);
        if (aTracks.length != 0) {
            mFrameCount = Reanimator.transformsOf(aTracks[0]).length;
            mTrackInstances = new ReanimatorTrackInstance[aTracks.length];
            for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
                mTrackInstances[aTrackIndex] = new ReanimatorTrackInstance();
            }
        } else {
            mFrameCount = 0;
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:423-523 (Reanimation::Update). */
    public void update() {
        if (mFrameCount == 0 || mDead) {
            return;
        }

        mLastFrameTime = mAnimTime;  // save the previous loop position
        mAnimTime += Reanimator.SECONDS_PER_UPDATE * mAnimRate / mFrameCount;  // advance the loop position

        if (mAnimRate > 0) {
            switch (mLoopType) {
                case ReanimLoopType.REANIM_LOOP:
                case ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME:
                    while (mAnimTime >= 1.0f) {
                        mLoopCount++;
                        mAnimTime -= 1.0f;
                    }
                    break;
                case ReanimLoopType.REANIM_PLAY_ONCE:
                case ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME:
                    if (mAnimTime >= 1.0f) {
                        mLoopCount = 1;
                        mAnimTime = 1.0f;
                        mDead = true;
                    }
                    break;
                case ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD:
                case ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME_AND_HOLD:
                    if (mAnimTime >= 1.0f) {
                        mLoopCount = 1;
                        mAnimTime = 1.0f;
                    }
                    break;
                default:
                    break;
            }
        } else {
            switch (mLoopType) {
                case ReanimLoopType.REANIM_LOOP:
                case ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME:
                    while (mAnimTime < 0.0f) {
                        mLoopCount++;
                        mAnimTime += 1.0f;
                    }
                    break;
                case ReanimLoopType.REANIM_PLAY_ONCE:
                case ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME:
                    if (mAnimTime < 0.0f) {
                        mLoopCount = 1;
                        mAnimTime = 0.0f;
                        mDead = true;
                    }
                    break;
                case ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD:
                case ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME_AND_HOLD:
                    if (mAnimTime < 0.0f) {
                        mLoopCount = 1;
                        mAnimTime = 0.0f;
                    }
                    break;
                default:
                    break;
            }
        }

        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
            ReanimatorTrackInstance aTrack = mTrackInstances[aTrackIndex];
            if (aTrack.mBlendCounter > 0) {
                aTrack.mBlendCounter--;
            }

            if (aTrack.mShakeOverride != 0.0f) {
                aTrack.mShakeX = PvzpCommon.randRangeFloat(-aTrack.mShakeOverride, aTrack.mShakeOverride);
                aTrack.mShakeY = PvzpCommon.randRangeFloat(-aTrack.mShakeOverride, aTrack.mShakeOverride);
            }

            if (aTracks[aTrackIndex].mName.regionMatches(true, 0, "attacher__", 0, 10)) {
                updateAttacherTrack(aTrackIndex);
            }

            if (aTrack.mAttachmentID.value != AttachmentID.ATTACHMENTID_NULL) {
                SexyTransform2D aOverlayMatrix = new SexyTransform2D();
                getAttachmentOverlayMatrix(aTrackIndex, aOverlayMatrix);
                Attachment.attachmentUpdateAndSetMatrix(aTrack.mAttachmentID, aOverlayMatrix);
            }
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:553-569 (GetCurrentTransform). */
    public void getCurrentTransform(int theTrackIndex, ReanimatorTransform theTransformCurrent, ReanimatorFrameTime theFrameTime) {
        ReanimatorFrameTime aFrameTime = theFrameTime;
        if (aFrameTime == null) {
            aFrameTime = new ReanimatorFrameTime();
            getFrameTime(aFrameTime);
        }
        getTransformAtTime(theTrackIndex, theTransformCurrent, aFrameTime);  // base transform interpolated between the two frames

        ReanimatorTrackInstance aTrack = mTrackInstances[theTrackIndex];
        if (PvzpCommon.floatRoundToInt(theTransformCurrent.mFrame) >= 0 && aTrack.mBlendCounter > 0) {
            float aBlendFactor = aTrack.mBlendCounter / (float) aTrack.mBlendTime;
            Reanimator.blendTransform(theTransformCurrent, theTransformCurrent, aTrack.mBlendTransform, aBlendFactor);  // blend with the recorded source transform
        }
    }

    /** Overload of upstream's default {@code theFrameTime = nullptr} argument. */
    public void getCurrentTransform(int theTrackIndex, ReanimatorTransform theTransformCurrent) {
        getCurrentTransform(theTrackIndex, theTransformCurrent, null);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:571-594 (GetTransformAtTime). */
    public void getTransformAtTime(int theTrackIndex, ReanimatorTransform theTransform, ReanimatorFrameTime theFrameTime) {
        ReanimatorTrack aTrack = Reanimator.tracksOf(mDefinition)[theTrackIndex];
        ReanimatorTransform[] aTransforms = Reanimator.transformsOf(aTrack);
        ReanimatorTransform aTransBefore = aTransforms[theFrameTime.mAnimFrameBeforeInt];  // previous frame's transform definition
        ReanimatorTransform aTransAfter = aTransforms[theFrameTime.mAnimFrameAfterInt];  // next frame's transform definition

        theTransform.mTransX = PvzpCommon.floatLerp(aTransBefore.mTransX, aTransAfter.mTransX, theFrameTime.mFraction);
        theTransform.mTransY = PvzpCommon.floatLerp(aTransBefore.mTransY, aTransAfter.mTransY, theFrameTime.mFraction);
        theTransform.mSkewX = PvzpCommon.floatLerp(aTransBefore.mSkewX, aTransAfter.mSkewX, theFrameTime.mFraction);
        theTransform.mSkewY = PvzpCommon.floatLerp(aTransBefore.mSkewY, aTransAfter.mSkewY, theFrameTime.mFraction);
        theTransform.mScaleX = PvzpCommon.floatLerp(aTransBefore.mScaleX, aTransAfter.mScaleX, theFrameTime.mFraction);
        theTransform.mScaleY = PvzpCommon.floatLerp(aTransBefore.mScaleY, aTransAfter.mScaleY, theFrameTime.mFraction);
        theTransform.mAlpha = PvzpCommon.floatLerp(aTransBefore.mAlpha, aTransAfter.mAlpha, theFrameTime.mFraction);
        theTransform.mImage = aTransBefore.mImage;
        theTransform.mFont = aTransBefore.mFont;
        theTransform.mText = aTransBefore.mText;

        if (aTransBefore.mFrame != -1.0f && aTransAfter.mFrame == -1.0f && theFrameTime.mFraction > 0.0f
                && mTrackInstances[theTrackIndex].mTruncateDisappearingFrames) {
            theTransform.mFrame = -1.0f;  // cut the transition to a blank frame when the track truncates disappearing frames
        } else {
            theTransform.mFrame = aTransBefore.mFrame;
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:596-610 (MatrixFromTransform). */
    public static void matrixFromTransform(ReanimatorTransform theTransform, SexyMatrix3 theMatrix) {
        float aSkewX = -PvzpCommon.DEG_TO_RAD * theTransform.mSkewX;
        float aSkewY = -PvzpCommon.DEG_TO_RAD * theTransform.mSkewY;

        theMatrix.m00 = (float) Math.cos(aSkewX) * theTransform.mScaleX;
        theMatrix.m10 = (float) -Math.sin(aSkewX) * theTransform.mScaleX;
        theMatrix.m20 = 0.0f;
        theMatrix.m01 = (float) Math.sin(aSkewY) * theTransform.mScaleY;
        theMatrix.m11 = (float) Math.cos(aSkewY) * theTransform.mScaleY;
        theMatrix.m21 = 0.0f;
        theMatrix.m02 = theTransform.mTransX;
        theMatrix.m12 = theTransform.mTransY;
        theMatrix.m22 = 1.0f;
    }

    /**
     * Ported from src/PvzpLib/Reanimator.cpp:612-644 (ReanimBltMatrix).
     *
     * <p>PORT-NOTE: only the matrix path is kept; the upstream software fast path for
     * {@code !Is3DAccelerated() && REANIM_FAST_DRAW_IN_SW_MODE && no skew && positive scale && white}
     * is an optimisation the Java/OpenGL port does not need (../verification/research/04-reanim-ui.md §6.2), and
     * {@code PvzpBltMatrix} itself is shared with the rest of the port.
     */
    public void reanimBltMatrix(Graphics g, Image theImage, SexyMatrix3 theTransform, Rect theClipRect, Color theColor, int theDrawMode, Rect theSrcRect) {
        PvzpCommon.pvzpBltMatrix(g, theImage, theTransform, theClipRect, theColor, theDrawMode, theSrcRect);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:646-826 (DrawTrack). */
    public boolean drawTrack(Graphics g, int theTrackIndex, int theRenderGroup, ReanimatorFrameTime theFrameTime) {
        // Reused: a busy board draws thousands of tracks per frame and this ran once per track.
        ReanimatorTransform aTransform = mScratchTransform;
        ReanimatorTrackInstance aTrackInstance = mTrackInstances[theTrackIndex];
        getCurrentTransform(theTrackIndex, aTransform, theFrameTime);
        int aImageFrame = PvzpCommon.floatRoundToInt(aTransform.mFrame);  // cel index within the image
        if (aImageFrame < 0) {
            return false;  // no image to draw
        }

        Color aColor = new Color(aTrackInstance.mTrackColor.mRed, aTrackInstance.mTrackColor.mGreen, aTrackInstance.mTrackColor.mBlue, aTrackInstance.mTrackColor.mAlpha);
        if (!aTrackInstance.mIgnoreColorOverride) {
            aColor = PvzpCommon.colorsMultiply(aColor, mColorOverride);
        }
        if (g.getColorizeImages()) {
            aColor = PvzpCommon.colorsMultiply(aColor, g.getColor());
        }
        int aImageAlpha = Math.clamp(PvzpCommon.floatRoundToInt(aTransform.mAlpha * aColor.mAlpha), 0, 255);
        if (aImageAlpha <= 0) {
            return false;
        }
        aColor.mAlpha = aImageAlpha;

        Color aExtraAdditiveColor = null;
        if (mEnableExtraAdditiveDraw) {
            aExtraAdditiveColor = new Color(mExtraAdditiveColor.mRed, mExtraAdditiveColor.mGreen, mExtraAdditiveColor.mBlue, mExtraAdditiveColor.mAlpha);
            aExtraAdditiveColor.mAlpha = PvzpCommon.colorComponentMultiply(mExtraAdditiveColor.mAlpha, aImageAlpha);
        }
        Color aExtraOverlayColor = null;
        if (mEnableExtraOverlayDraw) {
            aExtraOverlayColor = new Color(mExtraOverlayColor.mRed, mExtraOverlayColor.mGreen, mExtraOverlayColor.mBlue, mExtraOverlayColor.mAlpha);
            aExtraOverlayColor.mAlpha = PvzpCommon.colorComponentMultiply(mExtraOverlayColor.mAlpha, aImageAlpha);
        }

        Rect aClipRect = g.getClipRect();
        if (aTrackInstance.mIgnoreClipRect) {
            aClipRect = new Rect(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        }

        Image aImage = aTransform.mImage;
        ReanimAtlasImage aAtlasImage = null;
        // Extract pivot info from the original track image before any override / cleanup logic.
        Image aPivotImage = aImage;
        ReanimAtlasImage aPivotAtlasImage = null;
        if (mDefinition.mReanimAtlas != null && aPivotImage != null) {
            aPivotAtlasImage = mDefinition.mReanimAtlas.getEncodedReanimAtlas(aPivotImage);
        }

        if (mDefinition.mReanimAtlas != null && aImage != null) {
            aAtlasImage = mDefinition.mReanimAtlas.getEncodedReanimAtlas(aImage);  // decode atlas handle from transform image
            if (aTrackInstance.mImageOverride != null) {
                aImage = aTrackInstance.mImageOverride;
                aAtlasImage = null;
            }
        } else if (aTrackInstance.mImageOverride != null) {
            aImage = aTrackInstance.mImageOverride;
        }

        SexyTransform2D aMatrix = new SexyTransform2D();
        boolean aFullScreen = false;
        if (aPivotAtlasImage != null) {
            aMatrix.translate(aPivotAtlasImage.mWidth * 0.5f, aPivotAtlasImage.mHeight * 0.5f);
        } else if (aPivotImage != null) {
            aMatrix.translate(aPivotImage.getCelWidth() * 0.5f, aPivotImage.getCelHeight() * 0.5f);
        } else if (aImage != null) {
            aMatrix.translate(aImage.getCelWidth() * 0.5f, aImage.getCelHeight() * 0.5f);
        } else if (aTransform.mFont != null && !aTransform.mText.isEmpty()) {
            int aWidth = aTransform.mFont.stringWidth(aTransform.mText);
            aMatrix.translate(-aWidth * 0.5f, aTransform.mFont.mAscent);
        } else {
            if (!Reanimator.tracksOf(mDefinition)[theTrackIndex].mName.equalsIgnoreCase("fullscreen")) {
                return false;  // nothing to draw
            }
            aFullScreen = true;  // a screen-sized rect is filled later
        }

        SexyTransform2D aTransformMatrix = new SexyTransform2D();
        matrixFromTransform(aTransform, aTransformMatrix);
        aMatrix.assign(aTransformMatrix.times(aMatrix));  // apply the track transform
        aMatrix.assign(mOverlayMatrix.times(aMatrix));  // apply the overlay matrix
        aMatrix.translate(aTrackInstance.mShakeX + g.mTransX, aTrackInstance.mShakeY + g.mTransY);  // apply track shake and g's translation

        if (aAtlasImage != null) {
            Rect aSrcRect = new Rect(aAtlasImage.mX, aAtlasImage.mY, aAtlasImage.mWidth, aAtlasImage.mHeight);
            Image aAtlasMemoryImage = mDefinition.mReanimAtlas.mMemoryImage;
            if (mFilterEffect != FilterEffect.FILTER_EFFECT_NONE) {
                aAtlasMemoryImage = FilterEffect.filterEffectGetImage(aAtlasMemoryImage, mFilterEffect);
            }
            reanimBltMatrix(g, aAtlasMemoryImage, aMatrix, aClipRect, aColor, g.getDrawMode(), aSrcRect);
            if (mEnableExtraAdditiveDraw && !aTrackInstance.mIgnoreExtraAdditiveColor) {
                reanimBltMatrix(g, aAtlasMemoryImage, aMatrix, aClipRect, aExtraAdditiveColor, Graphics.DRAWMODE_ADDITIVE, aSrcRect);
            }
            if (mEnableExtraOverlayDraw) {
                reanimBltMatrix(g, FilterEffect.filterEffectGetImage(aAtlasMemoryImage, FilterEffect.FILTER_EFFECT_WHITE), aMatrix, aClipRect, aExtraOverlayColor, Graphics.DRAWMODE_NORMAL, aSrcRect);
            }
        } else if (aImage != null) {
            if (mFilterEffect != FilterEffect.FILTER_EFFECT_NONE) {
                aImage = FilterEffect.filterEffectGetImage(aImage, mFilterEffect);
            }

            while (aImageFrame >= aImage.mNumCols) {
                aImageFrame -= aImage.mNumCols;
            }

            int aCelWidth = aImage.getCelWidth();
            Rect aSrcRect = new Rect(aImageFrame * aCelWidth, 0, aCelWidth, aImage.getCelHeight());
            reanimBltMatrix(g, aImage, aMatrix, aClipRect, aColor, g.getDrawMode(), aSrcRect);
            if (mEnableExtraAdditiveDraw) {
                reanimBltMatrix(g, aImage, aMatrix, aClipRect, aExtraAdditiveColor, Graphics.DRAWMODE_ADDITIVE, aSrcRect);
            }
            if (mEnableExtraOverlayDraw) {
                Image aOverlayImage = FilterEffect.filterEffectGetImage(aImage, FilterEffect.FILTER_EFFECT_WHITE);
                reanimBltMatrix(g, aOverlayImage, aMatrix, aClipRect, aExtraOverlayColor, Graphics.DRAWMODE_NORMAL, aSrcRect);
            }
        } else if (aTransform.mFont != null && !aTransform.mText.isEmpty()) {
            PvzpCommon.pvzpDrawStringMatrix(g, aTransform.mFont, aMatrix, aTransform.mText, aColor);
            if (mEnableExtraAdditiveDraw) {
                int aOldMode = g.getDrawMode();
                g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
                PvzpCommon.pvzpDrawStringMatrix(g, aTransform.mFont, aMatrix, aTransform.mText, aExtraAdditiveColor);
                g.setDrawMode(aOldMode);
            }
        } else if (aFullScreen) {
            Color aOldColor = g.getColor();
            g.setColor(aColor);
            g.fillRect((int) -g.mTransX, (int) -g.mTransY, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
            g.setColor(aOldColor);
        }
        return true;
    }

    /** Overload of upstream's default {@code theFrameTime = nullptr} argument. */
    public boolean drawTrack(Graphics g, int theTrackIndex, int theRenderGroup) {
        return drawTrack(g, theTrackIndex, theRenderGroup, null);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:808-826 (GetCurrentTrackImage). */
    public Image getCurrentTrackImage(String theTrackName) {
        int aTrackIndex = findTrackIndex(theTrackName);
        ReanimatorTrackInstance aTrackInstance = mTrackInstances[aTrackIndex];
        if (aTrackInstance.mImageOverride != null) {
            return aTrackInstance.mImageOverride;
        }

        ReanimatorTransform aTransform = new ReanimatorTransform();
        getCurrentTransform(aTrackIndex, aTransform);

        Image aImage = aTransform.mImage;
        if (mDefinition.mReanimAtlas != null && aImage != null && mDefinition.mReanimAtlas.getEncodedReanimAtlas(aImage) != null) {
            aImage = null;  // atlas-packed frames have no stable source image
        }
        return aImage;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:847-902 (GetTrackMatrix). */
    public void getTrackMatrix(int theTrackIndex, SexyTransform2D theMatrix) {
        ReanimatorTrackInstance aTrackInstance = mTrackInstances[theTrackIndex];
        ReanimatorTransform aTransform = new ReanimatorTransform();
        getCurrentTransform(theTrackIndex, aTransform);
        int aImageFrame = PvzpCommon.floatRoundToInt(aTransform.mFrame);
        Image aImage = aTransform.mImage;
        ReanimAtlasImage aAtlasImage = null;
        // Extract pivot info from the original track image before any override / cleanup logic.
        Image aPivotImage = aImage;
        ReanimAtlasImage aPivotAtlasImage = null;
        if (mDefinition.mReanimAtlas != null && aPivotImage != null) {
            aPivotAtlasImage = mDefinition.mReanimAtlas.getEncodedReanimAtlas(aPivotImage);
        }

        if (mDefinition.mReanimAtlas != null && aImage != null) {
            aAtlasImage = mDefinition.mReanimAtlas.getEncodedReanimAtlas(aImage);  // decode atlas handle from transform image
        }
        if (aTrackInstance.mImageOverride != null) {
            aImage = aTrackInstance.mImageOverride;
            aAtlasImage = null;
        }

        theMatrix.loadIdentity();
        if (aPivotAtlasImage != null && aImageFrame >= 0) {
            theMatrix.translate(aPivotAtlasImage.mWidth * 0.5f, aPivotAtlasImage.mHeight * 0.5f);
        } else if (aPivotImage != null && aImageFrame >= 0) {
            theMatrix.translate(aPivotImage.getCelWidth() * 0.5f, aPivotImage.getCelHeight() * 0.5f);
        } else if (aImage != null && aImageFrame >= 0) {
            theMatrix.translate(aImage.getCelWidth() * 0.5f, aImage.getCelHeight() * 0.5f);
        } else if (aTransform.mFont != null && !aTransform.mText.isEmpty()) {
            theMatrix.translate(0.0f, aTransform.mFont.mAscent);
        }

        SexyTransform2D aTransformMatrix = new SexyTransform2D();
        matrixFromTransform(aTransform, aTransformMatrix);
        theMatrix.assign(aTransformMatrix.times(theMatrix));  // apply the track transform
        theMatrix.assign(mOverlayMatrix.times(theMatrix));  // apply the overlay matrix
        theMatrix.translate(aTrackInstance.mShakeX - 0.5f, aTrackInstance.mShakeY - 0.5f);  // apply track shake
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:904-925 (GetFrameTime). */
    public void getFrameTime(ReanimatorFrameTime theFrameTime) {
        int aFrameCount;
        if (mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME
                || mLoopType == ReanimLoopType.REANIM_LOOP_FULL_LAST_FRAME
                || mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME_AND_HOLD) {
            aFrameCount = mFrameCount;
        } else {
            aFrameCount = mFrameCount - 1;
        }
        float aAnimPosition = mFrameStart + mAnimTime * aFrameCount;
        float aAnimFrameBefore = (float) Math.floor(aAnimPosition);
        theFrameTime.mFraction = aAnimPosition - aAnimFrameBefore;
        theFrameTime.mAnimFrameBeforeInt = PvzpCommon.floatRoundToInt(aAnimFrameBefore);
        if (theFrameTime.mAnimFrameBeforeInt >= mFrameStart + mFrameCount - 1) {  // on the last frame
            theFrameTime.mAnimFrameBeforeInt = mFrameStart + mFrameCount - 1;
            theFrameTime.mAnimFrameAfterInt = theFrameTime.mAnimFrameBeforeInt;
        } else {
            theFrameTime.mAnimFrameAfterInt = theFrameTime.mAnimFrameBeforeInt + 1;
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:927-951 (DrawRenderGroup). */
    public void drawRenderGroup(Graphics g, int theRenderGroup) {
        if (mDead) {
            return;
        }
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        if (aTracks.length == 0) {
            return;
        }

        ReanimatorFrameTime aFrameTime = new ReanimatorFrameTime();
        getFrameTime(aFrameTime);
        for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
            ReanimatorTrackInstance aTrackInstance = mTrackInstances[aTrackIndex];
            if (aTrackInstance.mRenderGroup == theRenderGroup) {
                boolean aTrackDrawn = drawTrack(g, aTrackIndex, theRenderGroup, aFrameTime);
                if (aTrackInstance.mAttachmentID.value != AttachmentID.ATTACHMENTID_NULL) {
                    Attachment.attachmentDraw(aTrackInstance.mAttachmentID, g, !aTrackDrawn);
                }
            }
        }
    }

    /** Scratch transform for {@link #drawTrack}: drawing is single threaded per instance. */
    private final ReanimatorTransform mScratchTransform = new ReanimatorTransform();

    /** Ported from src/PvzpLib/Reanimator.cpp:953-956 (Draw). */
    public void draw(Graphics g) {
        drawRenderGroup(g, RENDER_GROUP_NORMAL);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:958-962 (DrawAllRenderGroups). */
    public void drawAllRenderGroups(Graphics g) {
        for (int aRenderGroup = RENDER_GROUP_NORMAL; aRenderGroup <= RENDER_GROUP_MAX; aRenderGroup++) {
            drawRenderGroup(g, aRenderGroup);
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:964-972 (FindTrackIndex). */
    public int findTrackIndex(String theTrackName) {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
            if (aTracks[aTrackIndex].mName.equalsIgnoreCase(theTrackName)) {
                return aTrackIndex;
            }
        }

        PvzpDebug.pvzpLogLn("Can't find track '{}'", theTrackName);
        return 0;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:974-977 (GetTrackInstanceByName). */
    public ReanimatorTrackInstance getTrackInstanceByName(String theTrackName) {
        return mTrackInstances[findTrackIndex(theTrackName)];
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:979-987 (AttachToAnotherReanimation). */
    public void attachToAnotherReanimation(Reanimation theAttachReanim, String theTrackName) {
        if (Reanimator.tracksOf(theAttachReanim.mDefinition).length <= 0) {
            return;
        }

        if (theAttachReanim.mFrameBasePose == -1) {
            theAttachReanim.mFrameBasePose = theAttachReanim.mFrameStart;  // use the current animation's start frame as the base pose
        }
        Attachment.attachReanim(theAttachReanim.getTrackInstanceByName(theTrackName).mAttachmentID, this, 0.0f, 0.0f);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:989-994 (SetBasePoseFromAnim). */
    public void setBasePoseFromAnim(String theTrackName) {
        int[] aFrameStartCount = new int[2];
        getFramesForLayer(theTrackName, aFrameStartCount);
        mFrameBasePose = aFrameStartCount[0];  // use the track animation's start frame as the base pose
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:996-1009 (GetTrackBasePoseMatrix). */
    public void getTrackBasePoseMatrix(int theTrackIndex, SexyTransform2D theBasePosMatrix) {
        if (mFrameBasePose == NO_BASE_POSE) {
            theBasePosMatrix.loadIdentity();
            return;
        }

        int aBasePos = mFrameBasePose == -1 ? mFrameStart : mFrameBasePose;
        ReanimatorFrameTime aStartTime = new ReanimatorFrameTime();
        aStartTime.mFraction = 0.0f;
        aStartTime.mAnimFrameBeforeInt = aBasePos;
        aStartTime.mAnimFrameAfterInt = aBasePos + 1;
        ReanimatorTransform aTransformStart = new ReanimatorTransform();
        getTransformAtTime(theTrackIndex, aTransformStart, aStartTime);
        matrixFromTransform(aTransformStart, theBasePosMatrix);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1011-1019 (AttachParticleToTrack). */
    public AttachEffect attachParticleToTrack(String theTrackName, PvzpParticleSystem theParticleSystem, float thePosX, float thePosY) {
        int aTrackIndex = findTrackIndex(theTrackName);
        ReanimatorTrackInstance aTrackInstance = mTrackInstances[aTrackIndex];
        SexyTransform2D aBasePoseMatrix = new SexyTransform2D();
        getTrackBasePoseMatrix(aTrackIndex, aBasePoseMatrix);
        SexyVector2 aPosition = aBasePoseMatrix.times(new SexyVector2(thePosX, thePosY));
        return Attachment.attachParticle(aTrackInstance.mAttachmentID, theParticleSystem, aPosition.x, aPosition.y);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1021-1034 (GetAttachmentOverlayMatrix). */
    public void getAttachmentOverlayMatrix(int theTrackIndex, SexyTransform2D theOverlayMatrix) {
        ReanimatorTransform aTransform = new ReanimatorTransform();
        getCurrentTransform(theTrackIndex, aTransform);  // blended transform without overrides
        SexyTransform2D aTransformMatrix = new SexyTransform2D();
        matrixFromTransform(aTransform, aTransformMatrix);
        aTransformMatrix.assign(mOverlayMatrix.times(aTransformMatrix));  // apply the overlay matrix

        SexyTransform2D aBasePoseMatrix = new SexyTransform2D();
        getTrackBasePoseMatrix(theTrackIndex, aBasePoseMatrix);
        SexyTransform2D aBasePoseMatrixInv = new SexyTransform2D();
        PvzpCommon.sexyMatrix3Inverse(aBasePoseMatrix, aBasePoseMatrixInv);
        theOverlayMatrix.assign(aTransformMatrix.times(aBasePoseMatrixInv));
    }

    /**
     * Ported from src/PvzpLib/Reanimator.cpp:1036-1059 (GetFramesForLayer).
     *
     * <p>Java replacement for upstream's two reference out-parameters: {@code theFrameStartCount[0]}
     * receives {@code theFrameStart}, {@code theFrameStartCount[1]} receives {@code theFrameCount}.
     */
    public void getFramesForLayer(String theTrackName, int[] theFrameStartCount) {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        if (aTracks.length == 0) {
            theFrameStartCount[0] = 0;
            theFrameStartCount[1] = 0;
            return;
        }

        int aTrackIndex = findTrackIndex(theTrackName);
        ReanimatorTransform[] aTransforms = Reanimator.transformsOf(aTracks[aTrackIndex]);
        int theFrameStart = 0;
        int theFrameCount = 1;
        for (int i = 0; i < aTransforms.length; i++) {
            if (aTransforms[i].mFrame >= 0.0f) {
                theFrameStart = i;  // first non-blank frame
                break;
            }
        }
        for (int j = theFrameStart; j < aTransforms.length; j++) {
            if (aTransforms[j].mFrame >= 0.0f) {
                theFrameCount = j - theFrameStart + 1;  // span from start to the last non-blank frame
            }
        }
        theFrameStartCount[0] = theFrameStart;
        theFrameStartCount[1] = theFrameCount;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1061-1069 (SetFramesForLayer). */
    public void setFramesForLayer(String theTrackName) {
        if (mAnimRate >= 0) {
            mAnimTime = 0.0f;
        } else {
            mAnimTime = 0.9999999f;
        }
        mLastFrameTime = -1.0f;
        int[] aFrameStartCount = new int[2];
        getFramesForLayer(theTrackName, aFrameStartCount);
        mFrameStart = aFrameStartCount[0];
        mFrameCount = aFrameStartCount[1];
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1071-1077 (TrackExists). */
    public boolean trackExists(String theTrackName) {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
            if (aTracks[aTrackIndex].mName.equalsIgnoreCase(theTrackName)) {
                return true;
            }
        }
        return false;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1079-1096 (StartBlend). */
    public void startBlend(int theBlendTime) {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
            ReanimatorTransform aTransform = new ReanimatorTransform();
            getCurrentTransform(aTrackIndex, aTransform);
            if (PvzpCommon.floatRoundToInt(aTransform.mFrame) >= 0) {  // not on a blank frame
                ReanimatorTrackInstance aTrackInstance = mTrackInstances[aTrackIndex];
                aTrackInstance.mBlendTransform.assign(aTransform);  // record the current transform as the blend source
                aTrackInstance.mBlendTime = theBlendTime;
                aTrackInstance.mBlendCounter = theBlendTime;
                aTrackInstance.mBlendTransform.mFont = null;
                aTrackInstance.mBlendTransform.mText = "";
                aTrackInstance.mBlendTransform.mImage = null;
            }
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1098-1111 (ReanimationDie). */
    public void reanimationDie() {
        if (!mDead) {
            mDead = true;
            if (mDefinition == null) {
                return;
            }
            ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
            for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
                Attachment.attachmentDie(mTrackInstances[aTrackIndex].mAttachmentID);
            }
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1113-1116 (SetShakeOverride). */
    public void setShakeOverride(String theTrackName, float theShakeAmount) {
        getTrackInstanceByName(theTrackName).mShakeOverride = theShakeAmount;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1118-1122 (SetPosition). */
    public void setPosition(float theX, float theY) {
        mOverlayMatrix.m02 = theX;
        mOverlayMatrix.m12 = theY;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1124-1128 (OverrideScale). */
    public void overrideScale(float theScaleX, float theScaleY) {
        mOverlayMatrix.m00 = theScaleX;
        mOverlayMatrix.m11 = theScaleY;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1130-1133 (GetImageOverride). */
    public Image getImageOverride(String theTrackName) {
        return getTrackInstanceByName(theTrackName).mImageOverride;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1135-1138 (SetImageOverride). */
    public void setImageOverride(String theTrackName, Image theImage) {
        getTrackInstanceByName(theTrackName).mImageOverride = theImage;
    }

    /** Overload of upstream's default {@code theTrackName = nullptr} argument (all tracks). */
    public void setTruncateDisappearingFrames(boolean theTruncateDisappearingFrames) {
        setTruncateDisappearingFrames(null, theTruncateDisappearingFrames);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1140-1150 (SetTruncateDisappearingFrames). */
    public void setTruncateDisappearingFrames(String theTrackName, boolean theTruncateDisappearingFrames) {
        if (theTrackName == null) {
            ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
            for (int aTrackIndex = 0; aTrackIndex < aTracks.length; aTrackIndex++) {
                mTrackInstances[aTrackIndex].mTruncateDisappearingFrames = theTruncateDisappearingFrames;
            }
        } else {
            getTrackInstanceByName(theTrackName).mTruncateDisappearingFrames = theTruncateDisappearingFrames;
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1238-1248 (GetTrackVelocity). */
    public float getTrackVelocity(String theTrackName) {
        ReanimatorFrameTime aFrameTime = new ReanimatorFrameTime();
        getFrameTime(aFrameTime);
        int aTrackIndex = findTrackIndex(theTrackName);

        ReanimatorTrack aTrack = Reanimator.tracksOf(mDefinition)[aTrackIndex];
        ReanimatorTransform[] aTransforms = Reanimator.transformsOf(aTrack);
        float aDis = aTransforms[aFrameTime.mAnimFrameAfterInt].mTransX - aTransforms[aFrameTime.mAnimFrameBeforeInt].mTransX;
        return aDis * Reanimator.SECONDS_PER_UPDATE * mAnimRate;  // velocity = x displacement between frames * frame duration * anim rate
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1250-1258 (IsTrackShowing). */
    public boolean isTrackShowing(String theTrackName) {
        ReanimatorFrameTime aFrameTime = new ReanimatorFrameTime();
        getFrameTime(aFrameTime);
        int aTrackIndex = findTrackIndex(theTrackName);

        return Reanimator.transformsOf(Reanimator.tracksOf(mDefinition)[aTrackIndex])[aFrameTime.mAnimFrameAfterInt].mFrame >= 0.0f;  // whether the next integer frame has an image
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1260-1266 (ShowOnlyTrack). */
    public void showOnlyTrack(String theTrackName) {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int i = 0; i < aTracks.length; i++) {
            mTrackInstances[i].mRenderGroup = aTracks[i].mName.equalsIgnoreCase(theTrackName) ? RENDER_GROUP_NORMAL : RENDER_GROUP_HIDDEN;
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1268-1277 (AssignRenderGroupToTrack). */
    public void assignRenderGroupToTrack(String theTrackName, int theRenderGroup) {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int i = 0; i < aTracks.length; i++) {
            if (aTracks[i].mName.equalsIgnoreCase(theTrackName)) {
                mTrackInstances[i].mRenderGroup = theRenderGroup;  // only the first exact match
                return;
            }
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1279-1287 (AssignRenderGroupToPrefix). */
    public void assignRenderGroupToPrefix(String theTrackName, int theRenderGroup) {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        int aPrefixLength = theTrackName.length();
        for (int i = 0; i < aTracks.length; i++) {
            String aName = aTracks[i].mName;
            if (aName.length() >= aPrefixLength && aName.regionMatches(true, 0, theTrackName, 0, aPrefixLength)) {  // the name must be at least as long as the prefix
                mTrackInstances[i].mRenderGroup = theRenderGroup;
            }
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1289-1295 (PropogateColorToAttachments). */
    public void propogateColorToAttachments() {
        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int i = 0; i < aTracks.length; i++) {
            Attachment.attachmentPropogateColor(
                    mTrackInstances[i].mAttachmentID,
                    mColorOverride,
                    mEnableExtraAdditiveDraw,
                    mExtraAdditiveColor,
                    mEnableExtraOverlayDraw,
                    mExtraOverlayColor);
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1297-1306 (ShouldTriggerTimedEvent). */
    public boolean shouldTriggerTimedEvent(float theEventTime) {
        if (mFrameCount == 0 || mLastFrameTime <= 0.0f || mAnimRate <= 0.0f) {  // no animation, reverse playback, or not playing
            return false;
        }

        if (mAnimTime >= mLastFrameTime) {  // normal case: trigger range is [mLastFrameTime, mAnimTime]
            return theEventTime >= mLastFrameTime && theEventTime < mAnimTime;
        }
        // wrapped into the next loop: trigger range is [0, mAnimTime] ∪ [mLastFrameTime, 1]
        return theEventTime >= mLastFrameTime || theEventTime < mAnimTime;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1308-1318 (PlayReanim). */
    public void playReanim(String theTrackName, int theLoopType, int theBlendTime, float theAnimRate) {
        if (theBlendTime > 0) {
            startBlend(theBlendTime);
        }
        if (theAnimRate != 0.0f) {  // a rate of 0 keeps the current anim rate
            mAnimRate = theAnimRate;
        }

        mLoopType = theLoopType;
        mLoopCount = 0;
        setFramesForLayer(theTrackName);
    }

    /**
     * Ported from src/PvzpLib/Reanimator.cpp:1320-1369 (ParseAttacherTrack).
     *
     * <p>PORT-NOTE: upstream's {@code sscanf(code, "%f", &animRate)} becomes
     * {@code Float.parseFloat(code.trim())} (a tag is either a plain float, {@code hold} or
     * {@code once}, so the two agree on every shipped file).
     */
    public static void parseAttacherTrack(ReanimatorTransform theTransform, AttacherInfo theAttacherInfo) {
        theAttacherInfo.mReanimName = "";
        theAttacherInfo.mTrackName = "";
        theAttacherInfo.mAnimRate = 12.0f;
        theAttacherInfo.mLoopType = ReanimLoopType.REANIM_LOOP;
        if (theTransform.mFrame == -1.0f) {  // blank frame
            return;
        }

        // attacher track name format: attacher__REANIMNAME__TRACKNAME[TAG1][TAG2]...
        String aText = theTransform.mText;
        int aReanimNamePos = aText.indexOf("__");  // the "__" before the reanim name
        if (aReanimNamePos < 0) {
            return;
        }
        int aTagsPos = aText.indexOf('[', aReanimNamePos + 2);  // the "[" starting the tags, after the reanim name
        int aTrackNamePos = aText.indexOf("__", aReanimNamePos + 2);  // the "__" before the track name, after the reanim name
        if (aTagsPos >= 0 && aTrackNamePos >= 0 && aTagsPos < aTrackNamePos) {  // a "__" after "[" makes the string invalid
            return;
        }

        if (aTrackNamePos >= 0) {  // track name defined
            theAttacherInfo.mReanimName = aText.substring(aReanimNamePos + 2, aTrackNamePos);  // between the two "__" (REANIMNAME)
            if (aTagsPos >= 0) {  // tags defined
                theAttacherInfo.mTrackName = aText.substring(aTrackNamePos + 2, aTagsPos);  // up to the "["
            } else {
                theAttacherInfo.mTrackName = aText.substring(aTrackNamePos + 2);  // to the end of the string
            }
        } else if (aTagsPos >= 0) {  // no track name, but tags defined
            theAttacherInfo.mReanimName = aText.substring(aReanimNamePos + 2, aTagsPos);  // between "__" and "["
        } else {  // only the reanim name is defined
            theAttacherInfo.mReanimName = aText.substring(aReanimNamePos + 2);  // from after "__" to the end of the string
        }

        int aTagPos = aTagsPos;
        while (aTagPos >= 0) {  // read each tag
            int aTagEndPos = aText.indexOf(']', aTagPos + 1);
            if (aTagEndPos < 0) {  // no closing "]"
                break;
            }

            String aCode = aText.substring(aTagPos + 1, aTagEndPos);  // text inside the brackets
            boolean aIsRate = false;
            try {
                theAttacherInfo.mAnimRate = Float.parseFloat(aCode.trim());  // if the text parses as a float, it is the anim rate
                aIsRate = true;
            } catch (NumberFormatException e) {
                aIsRate = false;
            }
            if (!aIsRate) {
                if (aCode.equals("hold")) {
                    theAttacherInfo.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
                } else if (aCode.equals("once")) {
                    theAttacherInfo.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE;
                }
            }

            aTagPos = aText.indexOf('[', aTagEndPos + 1);  // find the next tag's "["
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1371-1421 (AttacherSynchWalkSpeed). */
    public void attacherSynchWalkSpeed(int theTrackIndex, Reanimation theAttachReanim, AttacherInfo theAttacherInfo) {
        ReanimatorTransform[] aTransforms = Reanimator.transformsOf(Reanimator.tracksOf(mDefinition)[theTrackIndex]);
        ReanimatorFrameTime aFrameTime = new ReanimatorFrameTime();
        getFrameTime(aFrameTime);

        int aPlaceHolderFrameStart = aFrameTime.mAnimFrameBeforeInt;
        while (aPlaceHolderFrameStart > mFrameStart
                && aTransforms[aPlaceHolderFrameStart - 1].mText.equals(aTransforms[aPlaceHolderFrameStart].mText)) {
            aPlaceHolderFrameStart--;  // first frame of the current segment
        }
        int aPlaceHolderFrameEnd = aFrameTime.mAnimFrameBeforeInt;
        while (aPlaceHolderFrameEnd < mFrameStart + mFrameCount - 1
                && aTransforms[aPlaceHolderFrameEnd + 1].mText.equals(aTransforms[aPlaceHolderFrameEnd].mText)) {
            aPlaceHolderFrameEnd++;  // last frame of the current segment
        }
        int aPlaceHolderFrameCount = aPlaceHolderFrameEnd - aPlaceHolderFrameStart;
        ReanimatorTransform aPlaceHolderStartTrans = aTransforms[aPlaceHolderFrameStart];
        ReanimatorTransform aPlaceHolderEndTrans = aTransforms[aPlaceHolderFrameEnd];
        if (PvzpCommon.floatApproxEqual(mAnimRate, 0.0f)) {
            theAttachReanim.mAnimRate = 0.0f;
            return;
        }
        float aPlaceHolderDistance = -(aPlaceHolderEndTrans.mTransX - aPlaceHolderStartTrans.mTransX);  // placeholder track's displacement in this segment
        float aPlaceHolderSeconds = aPlaceHolderFrameCount / mAnimRate;  // placeholder track's duration in this segment
        if (PvzpCommon.floatApproxEqual(aPlaceHolderSeconds, 0.0f)) {  // the segment has no frames
            theAttachReanim.mAnimRate = 0.0f;
            return;
        }

        int aGroundTrackIndex = theAttachReanim.findTrackIndex("_ground");
        ReanimatorTransform[] aGroundTransforms = Reanimator.transformsOf(Reanimator.tracksOf(theAttachReanim.mDefinition)[aGroundTrackIndex]);
        ReanimatorTransform aTransformGuyStart = aGroundTransforms[theAttachReanim.mFrameStart];
        ReanimatorTransform aTransformGuyEnd = aGroundTransforms[theAttachReanim.mFrameStart + theAttachReanim.mFrameCount - 1];
        float aGuyDistance = aTransformGuyEnd.mTransX - aTransformGuyStart.mTransX;  // attached anim's displacement over one full cycle
        if (aGuyDistance < FLT_EPSILON || aPlaceHolderDistance < FLT_EPSILON) {  // zero displacement: the attached anim cannot move
            theAttachReanim.mAnimRate = 0.0f;
            return;
        }

        float aLoops = aPlaceHolderDistance / aGuyDistance;  // cycles needed = placeholder displacement / cycle displacement
        ReanimatorTransform aTransformGuyCurrent = new ReanimatorTransform();
        theAttachReanim.getCurrentTransform(aGroundTrackIndex, aTransformGuyCurrent);
        AttachEffect aAttachEffect = Attachment.findFirstAttachment(mTrackInstances[theTrackIndex].mAttachmentID);
        if (aAttachEffect != null) {
            float aGuyCurrentDistance = aTransformGuyCurrent.mTransX - aTransformGuyStart.mTransX;  // attached anim's current displacement within its cycle
            float aGuyExpectedDistance = aGuyDistance * theAttachReanim.mAnimTime;  // expected displacement from the uniformly moving placeholder
            aAttachEffect.mOffset.m02 = aGuyExpectedDistance - aGuyCurrentDistance;  // correct the x offset to keep the attached anim in sync with the placeholder
        }
        theAttachReanim.mAnimRate = aLoops * theAttachReanim.mFrameCount / aPlaceHolderSeconds;  // rate = frames to play / available time
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1423-1482 (UpdateAttacherTrack). */
    public void updateAttacherTrack(int theTrackIndex) {
        ReanimatorTrackInstance aTrackInstance = mTrackInstances[theTrackIndex];
        ReanimatorTransform aTransform = new ReanimatorTransform();
        getCurrentTransform(theTrackIndex, aTransform);
        AttacherInfo aAttacherInfo = new AttacherInfo();
        parseAttacherTrack(aTransform, aAttacherInfo);

        int aReanimationType = ReanimationType.REANIM_NONE;
        if (!aAttacherInfo.mReanimName.isEmpty()) {
            String aReanimFileName = "reanim/" + aAttacherInfo.mReanimName + ".reanim";
            for (int i = 0; i < Reanimator.gReanimationParamArraySize; i++) {  // find the reanim type for this file name
                ReanimationParams aParams = Reanimator.gReanimationParamArray[i];
                if (aReanimFileName.equalsIgnoreCase(aParams.mReanimFileName)) {
                    aReanimationType = aParams.mReanimationType;
                    break;
                }
            }
        }
        if (aReanimationType == ReanimationType.REANIM_NONE) {  // no name set or no matching reanim found
            Attachment.attachmentDie(aTrackInstance.mAttachmentID);
            return;
        }

        Reanimation aAttachReanim = Attachment.findReanimAttachment(aTrackInstance.mAttachmentID);
        if (aAttachReanim == null || aAttachReanim.mReanimationType != aReanimationType) {
            Attachment.attachmentDie(aTrackInstance.mAttachmentID);
            aAttachReanim = EffectSystem.gEffectSystem.mReanimationHolder.allocReanimation(0.0f, 0.0f, 0, aReanimationType);
            aAttachReanim.mLoopType = aAttacherInfo.mLoopType;
            aAttachReanim.mAnimRate = aAttacherInfo.mAnimRate;
            Attachment.attachReanim(aTrackInstance.mAttachmentID, aAttachReanim, 0.0f, 0.0f);
            mFrameBasePose = NO_BASE_POSE;  // with an attachment set, this reanim has no base pose frame
        }

        if (!aAttacherInfo.mTrackName.isEmpty()) {
            int[] aAnimFrameStartCount = new int[2];
            aAttachReanim.getFramesForLayer(aAttacherInfo.mTrackName, aAnimFrameStartCount);
            if (aAttachReanim.mFrameStart != aAnimFrameStartCount[0] || aAttachReanim.mFrameCount != aAnimFrameStartCount[1]) {
                aAttachReanim.startBlend(20);
                aAttachReanim.setFramesForLayer(aAttacherInfo.mTrackName);
            }

            if (aAttachReanim.mAnimRate == 12.0f && aAttacherInfo.mTrackName.equals("anim_walk") && aAttachReanim.trackExists("_ground")) {
                attacherSynchWalkSpeed(theTrackIndex, aAttachReanim, aAttacherInfo);
            } else {
                aAttachReanim.mAnimRate = aAttacherInfo.mAnimRate;
            }
            aAttachReanim.mLoopType = aAttacherInfo.mLoopType;
        }

        Color aColor = PvzpCommon.colorsMultiply(mColorOverride, aTrackInstance.mTrackColor);
        aColor.mAlpha = Math.clamp(PvzpCommon.floatRoundToInt(aTransform.mAlpha * aColor.mAlpha), 0, 255);
        Attachment.attachmentPropogateColor(aTrackInstance.mAttachmentID, aColor, mEnableExtraAdditiveDraw, mExtraAdditiveColor, mEnableExtraOverlayDraw, mExtraOverlayColor);
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1484-1489 (IsAnimPlaying). */
    public boolean isAnimPlaying(String theTrackName) {
        int[] aFrameStartCount = new int[2];
        getFramesForLayer(theTrackName, aFrameStartCount);
        return mFrameStart == aFrameStartCount[0] && mFrameCount == aFrameStartCount[1];
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1491-1508 (FindSubReanim). */
    public Reanimation findSubReanim(int theReanimType) {
        if (mReanimationType == theReanimType) {
            return this;
        }

        ReanimatorTrack[] aTracks = Reanimator.tracksOf(mDefinition);
        for (int i = 0; i < aTracks.length; i++) {
            Reanimation aReanimation = Attachment.findReanimAttachment(mTrackInstances[i].mAttachmentID);
            if (aReanimation != null) {
                Reanimation aSubReanim = aReanimation.findSubReanim(theReanimType);
                if (aSubReanim != null) {
                    return aSubReanim;
                }
            }
        }

        return null;
    }
}
