/*
 * Ported from src/Lawn/LawnCommon.h:43-55 and src/Lawn/LawnCommon.cpp:78-133.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.EditListener;
import cn.enaium.fabricpvz.portable.sexy.EditWidget;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;

/**
 * Ported from src/Lawn/LawnCommon.h:43-55, impl src/Lawn/LawnCommon.cpp:78-133.
 *
 * <p>{@link EditWidget} variant used by the lawn dialogs (new-user and cheat dialogs): it forwards
 * ESC to its dialog and can upper-case the first typed letter. The first-letter auto-capitalisation
 * is ASCII-only and locale-independent ({@code AutoCapChar}, LawnCommon.cpp:98-108).
 */
public class LawnEditWidget extends EditWidget {

    /** Ported from src/Lawn/LawnCommon.h:47. */
    public Dialog mDialog;

    /** Ported from src/Lawn/LawnCommon.h:48. */
    public boolean mAutoCapFirstLetter;

    /** Ported from src/Lawn/LawnCommon.cpp:78-83 (LawnEditWidget::LawnEditWidget). */
    public LawnEditWidget(int theId, EditListener theListener, Dialog theDialog) {
        super(theId, theListener);
        mDialog = theDialog;
        mAutoCapFirstLetter = true;
    }

    /** Ported from src/Lawn/LawnCommon.cpp:89-96 (LawnEditWidget::KeyDown). */
    @Override
    public void keyDown(int theKey) {
        super.keyDown(theKey);
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            mDialog.keyDown(KeyCode.KEYCODE_ESCAPE);
        }
    }

    /** Ported from src/Lawn/LawnCommon.cpp:110-116 (LawnEditWidget::KeyChar). */
    @Override
    public void keyChar(char theChar) {
        if (mAutoCapFirstLetter && autoCapChar(theChar)) {
            theChar = Character.toUpperCase(theChar);
            mAutoCapFirstLetter = false;
        }

        super.keyChar(theChar);
    }

    /** Ported from src/Lawn/LawnCommon.cpp:118-133 (LawnEditWidget::KeyText). */
    @Override
    public void keyText(String theText) {
        if (!mAutoCapFirstLetter || theText == null) {
            super.keyText(theText);
            return;
        }

        StringBuilder aText = new StringBuilder(theText);
        for (int i = 0; i < aText.length(); i++) {
            char aCh = aText.charAt(i);
            if (autoCapChar(aCh)) {
                aText.setCharAt(i, Character.toUpperCase(aCh));
                mAutoCapFirstLetter = false;
                break;
            }
        }

        super.keyText(aText.toString());
    }

    /**
     * Ported from src/Lawn/LawnCommon.cpp:98-108 (AutoCapChar): true when the character is an ASCII
     * letter (upper-case letters also clear the flag, as upstream does).
     */
    private static boolean autoCapChar(char theChar) {
        return (theChar >= 'a' && theChar <= 'z') || (theChar >= 'A' && theChar <= 'Z');
    }
}
