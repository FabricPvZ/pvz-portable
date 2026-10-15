/*
 * Ported from src/Lawn/Plant.h:147-156.
 *
 * Upstream struct MagnetItem; the public fields keep their upstream names. Upstream zeroes the
 * array with memset, so every slot starts with mItemType == MAGNET_ITEM_NONE.
 */
package cn.enaium.fabricpvz.portable.lawn;

public class MagnetItem {
    public transient float mPosX;
    public transient float mPosY;
    public transient float mDestOffsetX;
    public transient float mDestOffsetY;
    public int mItemType;

    public MagnetItem() {
        mPosX = 0.0f;
        mPosY = 0.0f;
        mDestOffsetX = 0.0f;
        mDestOffsetY = 0.0f;
        mItemType = MagnetItemType.MAGNET_ITEM_NONE;
    }
}
