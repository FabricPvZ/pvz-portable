/*
 * Ported from src/ConstEnums.h:1157-1164.
 *
 * Upstream enum StorePages (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class StorePages {
    public static final int STORE_PAGE_SLOT_UPGRADES = 0;
    public static final int STORE_PAGE_PLANT_UPGRADES = 1;
    public static final int STORE_PAGE_ZEN1 = 2;
    public static final int STORE_PAGE_ZEN2 = 3;
    public static final int NUM_STORE_PAGES = 4;

    private StorePages() {
    }
}
