/*
 * Ported from src/Lawn/Widget/AchievementsScreen.h:86-89 and AchievementsScreen.cpp:222-274.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.AdviceType;
import cn.enaium.fabricpvz.portable.lawn.AchievementId;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.MessageStyle;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedType;

/**
 * Ported from src/Lawn/Widget/AchievementsScreen.h:86-89 and AchievementsScreen.cpp:222-274.
 *
 * <p>Static achievement reporting helper ({@code ReportAchievement}). The achievement definitions
 * themselves live in {@link AchievementsScreen#gAchievementList}.
 */
public final class ReportAchievement {

    private ReportAchievement() {
    }

    /** Ported from src/Lawn/Widget/AchievementsScreen.cpp:222-242 (ReportAchievement::GiveAchievement). */
    public static void giveAchievement(LawnApp theApp, int theAchievement, boolean theForceGive) {
        if (theApp.mPlayerInfo == null) {
            return;
        }

        if (theApp.mPlayerInfo.mEarnedAchievements[theAchievement]) {
            return;
        }

        theApp.mPlayerInfo.mEarnedAchievements[theAchievement] = true;

        if (!theForceGive) {
            return;
        }

        String anAchievementName = theApp.getString(AchievementsScreen.gAchievementList[theAchievement].name,
                AchievementsScreen.gAchievementList[theAchievement].name);
        String aMessage = theApp.getString("%s Achievement!", "%s Achievement!").replace("%s", anAchievementName);

        if (theApp.mBoard != null) {
            theApp.mBoard.displayAdvice(aMessage, MessageStyle.MESSAGE_STYLE_ACHIEVEMENT, AdviceType.ADVICE_NONE);
            theApp.mPlayerInfo.mShownAchievements[theAchievement] = true;
            theApp.playSample(Resources.SOUND_ACHIEVEMENT_ID);
        }
    }

    /** Ported from src/Lawn/Widget/AchievementsScreen.cpp:244-274 (AchievementInitForPlayer). */
    public static void achievementInitForPlayer(LawnApp theApp) {
        if (theApp == null || theApp.mPlayerInfo == null) {
            return;
        }

        if (theApp.hasFinishedAdventure()) {
            giveAchievement(theApp, AchievementId.HomeSecurity, true);
        }

        if (theApp.earnedGoldTrophy()) {
            giveAchievement(theApp, AchievementId.NovelPeasPrize, true);
        }

        if (theApp.canSpawnYetis()) {
            giveAchievement(theApp, AchievementId.Zombologist, true);
        }

        int aTreeSize = theApp.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM
                - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1];
        if (aTreeSize >= 100) {
            giveAchievement(theApp, AchievementId.ToweringWisdom, true);
        }

        boolean aGiveAchievement = true;
        for (int aSeedType = SeedType.SEED_GATLINGPEA; aSeedType <= SeedType.SEED_IMITATER; aSeedType++) {
            if (!theApp.hasSeedType(aSeedType)) {
                aGiveAchievement = false;
            }
        }

        if (aGiveAchievement) {
            giveAchievement(theApp, AchievementId.Morticulturalist, true);
        }
    }
}
