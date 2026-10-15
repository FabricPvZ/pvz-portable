package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;

/**
 * Ported from src/SexyAppFramework/graphics/ImageFont.h:149 (class RenderCommand).
 *
 * <p>A queued glyph draw produced by {@code ImageFont.DrawStringEx}: the (pre-scaled) layer image,
 * the destination x/y, the source rectangle, the per-layer draw mode and the final tint color. The
 * commands are bucketed by {@code clamp(order + 128, 0, 255)} so that layers/characters render in the
 * upstream order; {@link #mNext} links the bucket list.
 */
public class RenderCommand {

    public Image mImage;
    public int[] mDest = new int[2];
    public int[] mSrc = new int[4];
    public int mMode;
    public Color mColor;
    public boolean mUseAlphaCorrection;
    public RenderCommand mNext;

    public RenderCommand() {
        mMode = -1;
        mColor = new Color();
    }
}
