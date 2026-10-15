/*
 * Ported from src/Lawn/Plant.h:119-145.
 *
 * Upstream enum MagnetItemType (int32_t); represented as public static final int constants.
 * The damage-stage runs are consecutive so `MAGNET_ITEM_*_1 + damageIndex` indexes the stage.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class MagnetItemType {
    public static final int MAGNET_ITEM_NONE = 0;
    public static final int MAGNET_ITEM_PAIL_1 = 1;
    public static final int MAGNET_ITEM_PAIL_2 = 2;
    public static final int MAGNET_ITEM_PAIL_3 = 3;
    public static final int MAGNET_ITEM_FOOTBALL_HELMET_1 = 4;
    public static final int MAGNET_ITEM_FOOTBALL_HELMET_2 = 5;
    public static final int MAGNET_ITEM_FOOTBALL_HELMET_3 = 6;
    public static final int MAGNET_ITEM_DOOR_1 = 7;
    public static final int MAGNET_ITEM_DOOR_2 = 8;
    public static final int MAGNET_ITEM_DOOR_3 = 9;
    // MAGNET_ITEM_PROPELLER is commented out upstream.
    public static final int MAGNET_ITEM_POGO_1 = 10;
    public static final int MAGNET_ITEM_POGO_2 = 11;
    public static final int MAGNET_ITEM_POGO_3 = 12;
    public static final int MAGNET_ITEM_JACK_IN_THE_BOX = 13;
    public static final int MAGNET_ITEM_LADDER_1 = 14;
    public static final int MAGNET_ITEM_LADDER_2 = 15;
    public static final int MAGNET_ITEM_LADDER_3 = 16;
    public static final int MAGNET_ITEM_LADDER_PLACED = 17;
    public static final int MAGNET_ITEM_SILVER_COIN = 18;
    public static final int MAGNET_ITEM_GOLD_COIN = 19;
    public static final int MAGNET_ITEM_DIAMOND = 20;
    public static final int MAGNET_ITEM_PICK_AXE = 21;

    private MagnetItemType() {
    }
}
