/*
 * Ported from src/ConstEnums.h:217-226.
 *
 * Upstream enum CrazyDaveState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class CrazyDaveState {
    public static final int CRAZY_DAVE_OFF = 0;
    public static final int CRAZY_DAVE_ENTERING = 1;
    public static final int CRAZY_DAVE_LEAVING = 2;
    public static final int CRAZY_DAVE_IDLING = 3;
    public static final int CRAZY_DAVE_TALKING = 4;
    public static final int CRAZY_DAVE_HANDING_TALKING = 5;
    public static final int CRAZY_DAVE_HANDING_IDLING = 6;

    private CrazyDaveState() {
    }
}
