/*
 * Ported from src/Lawn/Zombie.h:60-69.
 *
 * Plain holder for the per-frame zombie draw offsets (Zombie::GetDrawPos out-param).
 */
package cn.enaium.fabricpvz.portable.lawn;

public class ZombieDrawPosition {
    public int mHeadX;
    public int mHeadY;
    public int mArmY;
    public float mBodyY;
    public float mImageOffsetX;
    public float mImageOffsetY;
    public float mClipHeight;
}
