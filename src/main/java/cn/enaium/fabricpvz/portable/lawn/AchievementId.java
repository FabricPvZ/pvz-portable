/*
 * Ported from src/Lawn/Widget/AchievementsScreen.h:33-55.
 *
 * Upstream enum AchievementId (int); represented as public static final int constants with the
 * upstream enumerator names (which happen to be PascalCase, not UPPER_SNAKE).
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class AchievementId {
    public static final int HomeSecurity = 0;
    public static final int NovelPeasPrize = 1;
    public static final int BetterOffDead = 2;
    public static final int ChinaShop = 3;
    public static final int Spudow = 4;
    public static final int Explodonator = 5;
    public static final int Morticulturalist = 6;
    public static final int DontPea = 7;
    public static final int RollSomeHeads = 8;
    public static final int Grounded = 9;
    public static final int Zombologist = 10;
    public static final int PennyPincher = 11;
    public static final int SunnyDays = 12;
    public static final int PopcornParty = 13;
    public static final int GoodMorning = 14;
    public static final int NoFungusAmongUs = 15;
    public static final int BeyondTheGrave = 16;
    public static final int Immortal = 17;
    public static final int ToweringWisdom = 18;
    public static final int MustacheMode = 19;

    /** AchievementsScreen.h:54 (MAX_ACHIEVEMENTS). */
    public static final int MAX_ACHIEVEMENTS = 20;

    private AchievementId() {
    }
}
