/*
 * Ported from src/Lawn/Widget/CheatDialog.h:27-45 and src/Lawn/Widget/CheatDialog.cpp:33-151.
 *
 * The cheat dialog: a level/challenge edit box seeded with the current stage string (or the
 * active game mode / "F" finished-adventure prefix). OK applies the parsed cheat and restarts the
 * game; Cancel closes. The dialog is opened by LawnApp.DoCheatDialog (LawnApp.cpp:1003) and closed
 * through LawnApp.FinishCheatDialog (LawnApp.cpp:1012).
 *
 * Porting notes:
 *  - Upstream parses the edit-box text with sscanf patterns ("c%d"/"C%d", "f%d-%d"/"F%d-%d",
 *    "f%d"/"F%d", "%d-%d", "%d"); Java reproduces the same prefix-anchored, tolerant parsing with
 *    regexes, including sscanf's trailing-garbage tolerance.
 *  - std::clamp(aChallengeIndex, 0, NUM_CHALLENGE_MODES) is kept verbatim (NUM_CHALLENGE_MODES is the
 *    inclusive upper bound, matching ChallengeScreen.h:31 / ChallengeScreen.java:46).
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.EditListener;
import cn.enaium.fabricpvz.portable.sexy.EditWidget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Widget/CheatDialog.h:31 (class declaration) / src/Lawn/Widget/CheatDialog.cpp:33
 * (constructor).
 */
public class CheatDialog extends LawnDialog implements EditListener {

    public LawnApp mApp; // CheatDialog.h:35
    public EditWidget mLevelEditWidget; // CheatDialog.h:36

    // sscanf prefix patterns, in the order the upstream branches test them.
    private static final Pattern CHALLENGE = Pattern.compile("^[cC]\\s*([+-]?\\d+)"); // CheatDialog.cpp:104
    private static final Pattern F_AREA_SUB = Pattern.compile("^[fF]\\s*([+-]?\\d+)\\s*-\\s*([+-]?\\d+)"); // :111
    private static final Pattern F_LEVEL = Pattern.compile("^[fF]\\s*([+-]?\\d+)"); // :117
    private static final Pattern AREA_SUB = Pattern.compile("^\\s*([+-]?\\d+)\\s*-\\s*([+-]?\\d+)"); // :122
    private static final Pattern LEVEL = Pattern.compile("^\\s*([+-]?\\d+)"); // :128

    // CheatDialog::CheatDialog (CheatDialog.cpp:33)
    public CheatDialog(LawnApp theApp) {
        super(theApp, Dialogs.DIALOG_CHEAT, true, "CHEAT", "Enter New Level:", "", Dialog.BUTTONS_OK_CANCEL);

        mApp = theApp;
        mVerticalCenterText = false;
        mLevelEditWidget = LawnCommon.createEditWidget(0, this, this);
        mLevelEditWidget.mMaxChars = 12;
        mLevelEditWidget.addWidthCheckFont(Resources.getFontById(Resources.FONT_BRIANNETOD12_ID), 220);

        String aCheatStr;
        if (mApp.mGameMode != GameMode.GAMEMODE_ADVENTURE) {
            aCheatStr = String.format("C%d", mApp.mGameMode);
        } else if (mApp.hasFinishedAdventure()) {
            aCheatStr = String.format("F%s", LawnApp.getStageString(mApp.mPlayerInfo.getLevel()));
        } else {
            aCheatStr = LawnApp.getStageString(mApp.mPlayerInfo.getLevel());
        }
        mLevelEditWidget.setText(aCheatStr, true);

        calcSize(110, 40);
    }

    // CheatDialog::GetPreferredHeight (CheatDialog.cpp:56)
    @Override
    public int getPreferredHeight(int theWidth) {
        return super.getPreferredHeight(theWidth);
    }

    // CheatDialog::Resize (CheatDialog.cpp:61)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);
        mLevelEditWidget.resize(mContentInsets.mLeft + 12, mHeight - 155,
                mWidth - mContentInsets.mLeft - mContentInsets.mRight - 24, 28);
    }

    // CheatDialog::AddedToManager (CheatDialog.cpp:67)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mLevelEditWidget);
        theWidgetManager.setFocus(mLevelEditWidget);
    }

    // CheatDialog::RemovedFromManager (CheatDialog.cpp:74)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mLevelEditWidget);
    }

    // CheatDialog::Draw (CheatDialog.cpp:80)
    @Override
    public void draw(Graphics g) {
        super.draw(g);
        LawnCommon.drawEditBox(g, mLevelEditWidget);
    }

    // CheatDialog::EditWidgetText (CheatDialog.cpp:86)
    @Override
    public void editWidgetText(int theId, String theString) {
        mApp.buttonDepress(mId + 2000);
    }

    // CheatDialog::AllowChar (CheatDialog.cpp:91)
    public boolean allowChar(int theId, char theChar) {
        return Character.isDigit(theChar) || theChar == '-' || theChar == 'c' || theChar == 'C'
                || theChar == 'f' || theChar == 'F';
    }

    // CheatDialog::ApplyCheat (CheatDialog.cpp:96)
    public boolean applyCheat() {
        String aText = mLevelEditWidget.mString;

        Matcher aChallenge = CHALLENGE.matcher(aText);
        if (aChallenge.lookingAt()) {
            int aChallengeIndex = Integer.parseInt(aChallenge.group(1));
            mApp.mGameMode = Math.max(0, Math.min(aChallengeIndex, ChallengeScreen.NUM_CHALLENGE_MODES));
            return true;
        }

        int aLevel = -1;
        int aFinishedAdventure = 0;
        int aArea;
        int aSubArea;
        Matcher aFAreaSub = F_AREA_SUB.matcher(aText);
        Matcher aFLevel = F_LEVEL.matcher(aText);
        Matcher aAreaSub = AREA_SUB.matcher(aText);
        if (aFAreaSub.lookingAt()) {
            aArea = Integer.parseInt(aFAreaSub.group(1));
            aSubArea = Integer.parseInt(aFAreaSub.group(2));
            aLevel = (aArea - 1) * GameConstants.LEVELS_PER_AREA + aSubArea;
            aFinishedAdventure = 1;
        } else if (aFLevel.lookingAt()) {
            aLevel = Integer.parseInt(aFLevel.group(1));
            aFinishedAdventure = 1;
        } else if (aAreaSub.lookingAt()) {
            aArea = Integer.parseInt(aAreaSub.group(1));
            aSubArea = Integer.parseInt(aAreaSub.group(2));
            aLevel = (aArea - 1) * GameConstants.LEVELS_PER_AREA + aSubArea;
        } else {
            Matcher aLevelMatch = LEVEL.matcher(aText);
            if (aLevelMatch.lookingAt()) {
                aLevel = Integer.parseInt(aLevelMatch.group(1));
            }
        }

        if (aLevel <= 0) {
            mApp.doDialog(
                    Dialogs.DIALOG_CHEATERROR,
                    true,
                    "Enter Level",
                    "Invalid Level. Do 'number' or 'area-subarea' or 'Cnumber' or 'Farea-subarea'.",
                    "[DIALOG_BUTTON_OK]",
                    Dialog.BUTTONS_FOOTER);
            return false;
        }

        mApp.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        mApp.mPlayerInfo.setLevel(aLevel);
        mApp.mPlayerInfo.mFinishedAdventure = aFinishedAdventure;
        mApp.writeCurrentUserConfig();
        return true;
    }
}
