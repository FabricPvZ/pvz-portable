/*
 * Ported from src/Lawn/Zombie.h:41-55.
 *
 * Upstream enum ZombieParts (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ZombieParts {
    public static final int PART_BODY = 0;
    public static final int PART_HEAD = 1;
    public static final int PART_HEAD_EATING = 2;
    public static final int PART_TONGUE = 3;
    public static final int PART_ARM = 4;
    public static final int PART_HAIR = 5;
    public static final int PART_HEAD_YUCKY = 6;
    public static final int PART_ARM_PICKAXE = 7;
    public static final int PART_ARM_POLEVAULT = 8;
    public static final int PART_ARM_LEASH = 9;
    public static final int PART_ARM_FLAG = 10;
    public static final int PART_POGO = 11;
    public static final int PART_DIGGER = 12;

    private ZombieParts() {
    }
}
