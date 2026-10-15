/*
 * Ported from src/Lawn/System/TypingCheck.h:23-39 (class TypingCheck) and
 * src/Lawn/System/TypingCheck.cpp:22-69.
 *
 * The cheat-code matcher used by LawnApp: a phrase is stored as a byte string of KeyCode values
 * (each character lower-cased and resolved through KeyCodes::GetKeyCodeFromName), and the recent
 * keystrokes are matched against it; a full match clears the buffer and returns true.
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import cn.enaium.fabricpvz.portable.sexy.KeyCodes;

public class TypingCheck {

    protected String mPhrase = "";
    protected String mRecentTyping = "";

    /** Ported from src/Lawn/System/TypingCheck.h:33 ({@code TypingCheck() : mPhrase() { }}). */
    public TypingCheck() {
    }

    /** Ported from src/Lawn/System/TypingCheck.cpp:24-27. */
    public TypingCheck(String thePhrase) {
        setPhrase(thePhrase);
    }

    /** Ported from src/Lawn/System/TypingCheck.cpp:29-33. */
    public void setPhrase(String thePhrase) {
        for (int i = 0; i < thePhrase.length(); i++) {
            addChar(thePhrase.charAt(i));
        }
    }

    /**
     * Ported from src/Lawn/System/TypingCheck.cpp:35-38. Upstream appends the one-byte
     * {@code static_cast<char>(theKeyCode)}, so only the low 8 bits are stored.
     */
    public void addKeyCode(int theKeyCode) {
        mPhrase = mPhrase + (char) (theKeyCode & 0xFF);
    }

    /** Ported from src/Lawn/System/TypingCheck.cpp:40-46. */
    public void addChar(char theChar) {
        char aChar = Character.toLowerCase(theChar);
        addKeyCode(KeyCodes.getKeyCodeFromName(String.valueOf(aChar)));
    }

    /** Ported from src/Lawn/System/TypingCheck.cpp:48-56. */
    public boolean check() {
        if (mRecentTyping.equals(mPhrase)) {
            mRecentTyping = "";
            return true;
        }
        return false;
    }

    /** Ported from src/Lawn/System/TypingCheck.cpp:58-69. */
    public boolean check(int theKeyCode) {
        // Upstream: mRecentTyping.append(1, static_cast<char>(theKeyCode)); keep to one byte.
        char aChar = (char) (theKeyCode & 0xFF);
        mRecentTyping = mRecentTyping + aChar;
        int aLength = mPhrase.length();
        if (aLength == 0) {
            return false;
        }

        if (mRecentTyping.length() > aLength) {
            mRecentTyping = mRecentTyping.substring(1, 1 + aLength);
        }

        return check();
    }
}
