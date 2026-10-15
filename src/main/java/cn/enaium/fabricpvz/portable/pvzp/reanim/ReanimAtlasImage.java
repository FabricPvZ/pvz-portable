package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/PvzpLib/ReanimAtlas.h:38-52 (class ReanimAtlasImage).
 *
 * <p>Placement record of one source image inside a {@link ReanimAtlas}: the destination rectangle
 * plus the original image it was copied from.
 */
public class ReanimAtlasImage {
    public int mX;
    public int mY;
    public int mWidth;
    public int mHeight;
    public Image mOriginalImage;

    public ReanimAtlasImage() {
        mX = 0;
        mY = 0;
        mWidth = 0;
        mHeight = 0;
        mOriginalImage = null;
    }
}
