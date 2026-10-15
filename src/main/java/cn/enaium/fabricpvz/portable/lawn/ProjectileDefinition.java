/*
 * Ported from src/Lawn/Projectile.h:36-44 and src/Lawn/Projectile.cpp:38-53.
 *
 * Upstream struct ProjectileDefinition (the table element type for
 * gProjectileDefinition[]); fields keep their upstream names.
 */
package cn.enaium.fabricpvz.portable.lawn;

public class ProjectileDefinition {
    public int mProjectileType;
    public int mImageRow;
    public int mDamage;

    public ProjectileDefinition(int theProjectileType, int theImageRow, int theDamage) {
        mProjectileType = theProjectileType;
        mImageRow = theImageRow;
        mDamage = theDamage;
    }
}
