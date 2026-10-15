package cn.enaium.fabricpvz.portable.pvzp.reanim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Behavioural tests of the reanimation runtime against hand-built definitions: frame selection by
 * the fixed 1/100 s tick, loop/once modes, blending, colour+alpha application, image overrides,
 * attach-point (track matrix) maths and render-group visibility.
 *
 * <p>The definitions are injected into the type registry at {@code REANIM_LOADBAR_SPROUT}, whose
 * shipped flag set carries {@code REANIM_NO_ATLAS}, so no atlas is built and the plain
 * {@code Image} test doubles are used directly.
 */
class ReanimatorTest {
    private static final int TEST_TYPE = ReanimationType.REANIM_LOADBAR_SPROUT;

    @BeforeAll
    static void installRegistry() {
        // A single-entry registry keeps the test deterministic: REANIM_LOADBAR_SPROUT (index 0)
        // carries REANIM_NO_ATLAS, so no atlas is ever built and nothing is read from disk.
        ReanimTestSupport.installSingleEntryRegistry(TEST_TYPE, "reanim/LoadBar_sprout.reanim", 1);
    }

    @AfterAll
    static void freeRegistry() {
        ReanimTestSupport.freeRegistry();
    }

    private static Reanimation initialize(ReanimatorDefinition theDefinition) {
        Reanimator.gReanimatorDefArray[TEST_TYPE] = theDefinition;
        Reanimation aReanimation = new Reanimation();
        aReanimation.reanimationInitializeType(0.0f, 0.0f, TEST_TYPE);
        return aReanimation;
    }

    /** Three visible frames at x = 0 / 10 / 20, 12 fps. */
    private static ReanimatorDefinition threeFrameDefinition(Image theImage) {
        return ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("anim_idle",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, theImage),
                ReanimTestSupport.transform(10.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, theImage),
                ReanimTestSupport.transform(20.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 1.0f, theImage)));
    }

    @Test
    void selectsFramesByElapsedTicks() {
        Reanimation aReanimation = initialize(threeFrameDefinition(ReanimTestSupport.image(40, 30)));
        assertEquals(3, aReanimation.mFrameCount);
        assertEquals(12.0f, aReanimation.mAnimRate, 1e-6f);
        aReanimation.mLoopType = ReanimLoopType.REANIM_LOOP;

        // 0.01 s per update * 12 fps / 3 frames = 0.04 of the loop per tick.
        for (int i = 0; i < 13; i++) {
            aReanimation.update();
        }
        assertEquals(0.52f, aReanimation.mAnimTime, 1e-4f);

        ReanimatorFrameTime aFrameTime = new ReanimatorFrameTime();
        aReanimation.getFrameTime(aFrameTime);
        assertEquals(1, aFrameTime.mAnimFrameBeforeInt);
        assertEquals(2, aFrameTime.mAnimFrameAfterInt);
        assertEquals(0.04f, aFrameTime.mFraction, 1e-3f);

        ReanimatorTransform aTransform = new ReanimatorTransform();
        aReanimation.getCurrentTransform(0, aTransform);
        assertEquals(10.4f, aTransform.mTransX, 1e-2f);  // lerp(10, 20, 0.04)
    }

    @Test
    void loopOnceAndHoldModesOwnThePlayhead() {
        ReanimatorDefinition aDefinition = threeFrameDefinition(ReanimTestSupport.image(40, 30));

        Reanimation aLooping = initialize(aDefinition);
        aLooping.mLoopType = ReanimLoopType.REANIM_LOOP;
        for (int i = 0; i < 30; i++) {
            aLooping.update();
        }
        assertFalse(aLooping.mDead);
        assertTrue(aLooping.mLoopCount >= 1);
        assertTrue(aLooping.mAnimTime < 1.0f);

        Reanimation aOnce = initialize(aDefinition);
        aOnce.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE;
        for (int i = 0; i < 30; i++) {
            aOnce.update();
        }
        assertTrue(aOnce.mDead);
        assertEquals(1, aOnce.mLoopCount);
        assertEquals(1.0f, aOnce.mAnimTime, 1e-6f);

        Reanimation aHold = initialize(aDefinition);
        aHold.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        for (int i = 0; i < 30; i++) {
            aHold.update();
        }
        assertFalse(aHold.mDead);
        assertEquals(1.0f, aHold.mAnimTime, 1e-6f);
        assertEquals(1, aHold.mLoopCount);
    }

    @Test
    void playReanimResetsTheLayerWindowAndRejectsZeroRate() {
        Image aImage = ReanimTestSupport.image(40, 30);
        ReanimatorDefinition aDefinition = ReanimTestSupport.definition(12.0f,
                ReanimTestSupport.track("anim_idle",
                        ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, -1.0f, 1.0f, null),
                        ReanimTestSupport.transform(3.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aImage),
                        ReanimTestSupport.transform(6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, aImage)),
                ReanimTestSupport.track("anim_walk",
                        ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aImage),
                        ReanimTestSupport.transform(3.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, aImage),
                        ReanimTestSupport.transform(6.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 1.0f, aImage)));
        Reanimation aReanimation = initialize(aDefinition);

        // anim_idle starts on a blank frame: the layer window is [1, 2].
        int[] aRange = new int[2];
        aReanimation.getFramesForLayer("anim_idle", aRange);
        assertEquals(1, aRange[0]);
        assertEquals(2, aRange[1]);

        aReanimation.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 20.0f);
        assertEquals(1, aReanimation.mFrameStart);
        assertEquals(2, aReanimation.mFrameCount);
        assertEquals(20.0f, aReanimation.mAnimRate, 1e-6f);
        assertEquals(0.0f, aReanimation.mAnimTime, 1e-6f);
        assertTrue(aReanimation.isAnimPlaying("anim_idle"));
        assertTrue(aReanimation.trackExists("ANIM_WALK"));  // case-insensitive, like upstream
        assertFalse(aReanimation.trackExists("anim_missing"));

        aReanimation.playReanim("anim_walk", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
        assertEquals(20.0f, aReanimation.mAnimRate, 1e-6f);  // a rate of 0 keeps the current rate
        assertEquals(3, aReanimation.mFrameCount);
    }

    @Test
    void blendsTowardTheRecordedTransform() {
        Reanimation aReanimation = initialize(threeFrameDefinition(ReanimTestSupport.image(40, 30)));
        aReanimation.mLoopType = ReanimLoopType.REANIM_LOOP;
        for (int i = 0; i < 13; i++) {
            aReanimation.update();
        }
        ReanimatorTrackInstance aTrackInstance = aReanimation.mTrackInstances[0];
        assertEquals(0, aTrackInstance.mBlendCounter);  // no blend in progress yet

        aReanimation.startBlend(10);
        assertEquals(10, aTrackInstance.mBlendTime);
        assertEquals(10, aTrackInstance.mBlendCounter);
        assertEquals(10.4f, aTrackInstance.mBlendTransform.mTransX, 1e-2f);  // snapshot of the current pose
        assertNull(aTrackInstance.mBlendTransform.mImage);  // image/font/text are not part of the blend

        aReanimation.update();  // counter 10 -> 9
        assertEquals(9, aTrackInstance.mBlendCounter);
        ReanimatorTransform aBlended = new ReanimatorTransform();
        aReanimation.getCurrentTransform(0, aBlended);
        // lerp(base pose 11.2, snapshot 10.4, 9/10)
        assertEquals(10.48f, aBlended.mTransX, 1e-2f);
    }

    @Test
    void appliesTrackColourAlphaAndAdditivePass() {
        Image aImage = ReanimTestSupport.image(40, 30);
        Reanimation aReanimation = initialize(ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("anim_idle",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.5f, aImage))));
        aReanimation.mColorOverride = new cn.enaium.fabricpvz.portable.sexy.Color(255, 255, 255, 128);
        aReanimation.mEnableExtraAdditiveDraw = true;
        aReanimation.mExtraAdditiveColor = new cn.enaium.fabricpvz.portable.sexy.Color(10, 20, 30, 200);
        aReanimation.mEnableExtraOverlayDraw = true;
        aReanimation.mExtraOverlayColor = new cn.enaium.fabricpvz.portable.sexy.Color(1, 2, 3, 100);

        ReanimTestSupport.RecordingImage aTarget = new ReanimTestSupport.RecordingImage(800, 600);
        Graphics aGraphics = new Graphics(aTarget);
        assertTrue(aReanimation.drawTrack(aGraphics, 0, Reanimation.RENDER_GROUP_NORMAL));

        assertEquals(3, aTarget.mBlits.size());  // base + additive + overlay
        ReanimTestSupport.Blit aBase = aTarget.mBlits.get(0);
        assertEquals(Graphics.DRAWMODE_NORMAL, aBase.mDrawMode);
        assertEquals(255, aBase.mColor.mRed);
        assertEquals(64, aBase.mColor.mAlpha);  // round(0.5 * 128)
        assertSame(aImage, aBase.mImage);
        assertEquals(40, aBase.mSrcRect.mWidth);  // cel 0 of a 1-column image
        assertEquals(0, aBase.mSrcRect.mX);

        ReanimTestSupport.Blit aAdditive = aTarget.mBlits.get(1);
        assertEquals(Graphics.DRAWMODE_ADDITIVE, aAdditive.mDrawMode);
        assertEquals(50, aAdditive.mColor.mAlpha);  // 200 * 64 / 255
        assertEquals(10, aAdditive.mColor.mRed);

        ReanimTestSupport.Blit aOverlay = aTarget.mBlits.get(2);
        assertEquals(Graphics.DRAWMODE_NORMAL, aOverlay.mDrawMode);
        assertEquals(25, aOverlay.mColor.mAlpha);  // 100 * 64 / 255
    }

    @Test
    void skipsBlankAndInvisibleTracks() {
        Image aImage = ReanimTestSupport.image(40, 30);
        Reanimation aBlank = initialize(ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("anim_idle",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, -1.0f, 1.0f, aImage))));
        ReanimTestSupport.RecordingImage aTarget = new ReanimTestSupport.RecordingImage(800, 600);
        assertFalse(aBlank.drawTrack(new Graphics(aTarget), 0, Reanimation.RENDER_GROUP_NORMAL));
        assertTrue(aTarget.mBlits.isEmpty());

        // Zero alpha of the effective colour skips the track as well.
        Reanimation aTransparent = initialize(ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("anim_idle",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, aImage))));
        assertFalse(aTransparent.drawTrack(new Graphics(aTarget), 0, Reanimation.RENDER_GROUP_NORMAL));
        assertTrue(aTarget.mBlits.isEmpty());
    }

    @Test
    void truncatesDisappearingFramesOnlyWhenAsked() {
        Image aImage = ReanimTestSupport.image(40, 30);
        ReanimatorDefinition aDefinition = ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("anim_idle",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aImage),
                ReanimTestSupport.transform(10.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, -1.0f, 1.0f, null),
                ReanimTestSupport.transform(20.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aImage)));

        Reanimation aTruncating = initialize(aDefinition);
        aTruncating.mLoopType = ReanimLoopType.REANIM_LOOP;
        aTruncating.update();  // fraction 0.08 between frame 0 and the blank frame 1
        ReanimatorTransform aTransform = new ReanimatorTransform();
        aTruncating.getCurrentTransform(0, aTransform);
        assertEquals(-1.0f, aTransform.mFrame, 1e-6f);

        Reanimation aKeeping = initialize(aDefinition);
        aKeeping.mLoopType = ReanimLoopType.REANIM_LOOP;
        aKeeping.setTruncateDisappearingFrames(false);
        aKeeping.update();
        aKeeping.getCurrentTransform(0, aTransform);
        assertEquals(0.0f, aTransform.mFrame, 1e-6f);
    }

    @Test
    void switchesImageOverridesPerTrack() {
        Image aFrameImage = ReanimTestSupport.image(40, 30);
        Image aOverride = ReanimTestSupport.image(60, 44);
        Reanimation aReanimation = initialize(ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("anim_idle",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aFrameImage))));

        assertSame(aFrameImage, aReanimation.getCurrentTrackImage("anim_idle"));
        assertNull(aReanimation.getImageOverride("anim_idle"));

        aReanimation.setImageOverride("anim_idle", aOverride);
        assertSame(aOverride, aReanimation.getImageOverride("anim_idle"));
        assertSame(aOverride, aReanimation.getCurrentTrackImage("anim_idle"));

        ReanimTestSupport.RecordingImage aTarget = new ReanimTestSupport.RecordingImage(800, 600);
        assertTrue(aReanimation.drawTrack(new Graphics(aTarget), 0, Reanimation.RENDER_GROUP_NORMAL));
        assertSame(aOverride, aTarget.mBlits.get(0).mImage);
        // the pivot still comes from the frame image's cel metrics, not the override's
        assertEquals(20.0f, aTarget.mBlits.get(0).m02, 1e-3f);

        aReanimation.setImageOverride("anim_idle", null);
        aTarget.mBlits.clear();
        assertTrue(aReanimation.drawTrack(new Graphics(aTarget), 0, Reanimation.RENDER_GROUP_NORMAL));
        assertSame(aFrameImage, aTarget.mBlits.get(0).mImage);
        assertNotSame(aOverride, aTarget.mBlits.get(0).mImage);
    }

    @Test
    void computesPivotedTrackAndAttachmentMatrices() {
        Image aImage = ReanimTestSupport.image(40, 30);
        // A parent rotated by 45 degrees (skewX = skewY = -45 -> +45 degree rotation in the matrix;
        // a single 90 degree skew would collapse the transform to a singular matrix).
        ReanimatorDefinition aDefinition = ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("body",
                ReanimTestSupport.transform(5.0f, 7.0f, -45.0f, -45.0f, 1.0f, 1.0f, 0.0f, 1.0f, aImage),
                ReanimTestSupport.transform(15.0f, 7.0f, -45.0f, -45.0f, 1.0f, 1.0f, 1.0f, 1.0f, aImage)));
        Reanimation aReanimation = initialize(aDefinition);
        aReanimation.setPosition(100.0f, 200.0f);

        SexyTransform2D aMatrix = new SexyTransform2D();
        aReanimation.getTrackMatrix(0, aMatrix);
        assertEquals(0.7071f, aMatrix.m00, 1e-3f);
        assertEquals(-0.7071f, aMatrix.m10, 1e-3f);
        assertEquals(0.7071f, aMatrix.m01, 1e-3f);
        assertEquals(0.7071f, aMatrix.m11, 1e-3f);
        // overlay(100,200) * rotate(45) * pivot(20,15), then the -0.5 shake offset
        assertEquals(129.25f, aMatrix.m02, 1e-2f);
        assertEquals(202.96f, aMatrix.m12, 1e-2f);

        // The attachment overlay is the movement since the base pose: 0.54 of the way from frame 0
        // to frame 1 (9 ticks * 0.06), i.e. +5.4 px in the parent's local space.
        aReanimation.setBasePoseFromAnim("body");
        assertEquals(0, aReanimation.mFrameBasePose);
        for (int i = 0; i < 9; i++) {
            aReanimation.update();
        }
        SexyTransform2D aOverlay = new SexyTransform2D();
        aReanimation.getAttachmentOverlayMatrix(0, aOverlay);
        assertEquals(105.4f, aOverlay.m02, 1e-2f);
        assertEquals(200.0f, aOverlay.m12, 1e-2f);
    }

    @Test
    void togglesRenderGroupsAndVisibility() {
        Image aImage = ReanimTestSupport.image(40, 30);
        ReanimatorDefinition aDefinition = ReanimTestSupport.definition(12.0f,
                ReanimTestSupport.track("body", ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aImage)),
                ReanimTestSupport.track("head", ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aImage)),
                ReanimTestSupport.track("head_glow", ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, aImage)));
        Reanimation aReanimation = initialize(aDefinition);

        aReanimation.showOnlyTrack("body");
        assertEquals(Reanimation.RENDER_GROUP_NORMAL, aReanimation.mTrackInstances[0].mRenderGroup);
        assertEquals(Reanimation.RENDER_GROUP_HIDDEN, aReanimation.mTrackInstances[1].mRenderGroup);
        assertEquals(Reanimation.RENDER_GROUP_HIDDEN, aReanimation.mTrackInstances[2].mRenderGroup);
        assertTrue(aReanimation.isTrackShowing("head"));  // the frame data still has an image

        ReanimTestSupport.RecordingImage aTarget = new ReanimTestSupport.RecordingImage(800, 600);
        aReanimation.drawAllRenderGroups(new Graphics(aTarget));
        assertEquals(1, aTarget.mBlits.size());  // hidden tracks are never played back

        aReanimation.assignRenderGroupToTrack("head", 5);
        aTarget.mBlits.clear();
        aReanimation.drawAllRenderGroups(new Graphics(aTarget));
        assertEquals(2, aTarget.mBlits.size());

        aReanimation.assignRenderGroupToPrefix("head", Reanimation.RENDER_GROUP_HIDDEN);
        aTarget.mBlits.clear();
        aReanimation.drawAllRenderGroups(new Graphics(aTarget));
        assertEquals(1, aTarget.mBlits.size());

        assertEquals(1, aReanimation.findTrackIndex("HEAD"));
        assertEquals(2, aReanimation.findTrackIndex("head_glow"));
    }
}
