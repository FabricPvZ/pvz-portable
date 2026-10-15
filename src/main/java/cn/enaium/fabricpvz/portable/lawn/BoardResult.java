/*
 * Ported from src/ConstEnums.h:124-133.
 *
 * Upstream enum BoardResult (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class BoardResult {
    public static final int BOARDRESULT_NONE = 0;
    public static final int BOARDRESULT_WON = 1;
    public static final int BOARDRESULT_LOST = 2;
    public static final int BOARDRESULT_RESTART = 3;
    public static final int BOARDRESULT_QUIT = 4;
    public static final int BOARDRESULT_QUIT_APP = 5;
    public static final int BOARDRESULT_CHEAT = 6;

    private BoardResult() {
    }
}
