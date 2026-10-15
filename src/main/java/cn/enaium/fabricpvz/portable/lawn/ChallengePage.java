/*
 * Ported from src/ConstEnums.h:142-149.
 *
 * Upstream enum ChallengePage (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ChallengePage {
    public static final int CHALLENGE_PAGE_SURVIVAL = 0;
    public static final int CHALLENGE_PAGE_CHALLENGE = 1;
    public static final int CHALLENGE_PAGE_LIMBO = 2;
    public static final int CHALLENGE_PAGE_PUZZLE = 3;
    public static final int MAX_CHALLANGE_PAGES = 4;

    private ChallengePage() {
    }
}
