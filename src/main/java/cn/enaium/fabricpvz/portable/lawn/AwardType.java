/*
 * Ported from src/ConstEnums.h:103-110.
 *
 * Upstream enum AwardType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class AwardType {
    public static final int AWARD_FORLEVEL = 0;
    public static final int AWARD_CREDITS_ZOMBIENOTE = 1;
    public static final int AWARD_HELP_ZOMBIENOTE = 2;
    public static final int AWARD_ACHIEVEMENTONLY = 3;
    public static final int AWARD_PRECREDITS_ZOMBIENOTE = 4;

    private AwardType() {
    }
}
