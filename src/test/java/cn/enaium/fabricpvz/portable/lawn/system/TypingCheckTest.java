/*
 * Test for the TypingCheck cheat-code matcher (src/Lawn/System/TypingCheck.cpp). The phrases are the
 * ones LawnApp.cpp:1223-1242 installs on its members.
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.KeyCodes;

class TypingCheckTest {

    /** The KeyCode a real key-down event delivers for a lower-case letter (0x41..0x5A). */
    private static int letter(char theChar) {
        return KeyCodes.getKeyCodeFromName(String.valueOf(theChar));
    }

    /** A real key-down sends KEYCODE_SPACE (0x20), not the KEYCODE_UNKNOWN that addChar(' ') stores. */
    private static int keyFor(char theChar) {
        return theChar == ' ' ? KeyCode.KEYCODE_SPACE : letter(theChar);
    }

    private static boolean feed(TypingCheck theCheck, String theTyped) {
        boolean aResult = false;
        for (int i = 0; i < theTyped.length(); i++) {
            aResult = theCheck.check(keyFor(theTyped.charAt(i)));
        }
        return aResult;
    }

    @Test
    void upstreamCheatPhrasesMatchOnTheLastKey() {
        // LawnApp.cpp:1234-1242 (the space-separated variant is covered separately below).
        String[] aPhrases = {"mustache", "moustache", "trickedout", "future", "pinata", "dance", "daisies", "sukhbir"};

        for (String aPhrase : aPhrases) {
            TypingCheck aCheck = new TypingCheck(aPhrase);
            for (int i = 0; i < aPhrase.length(); i++) {
                boolean aMatched = aCheck.check(letter(aPhrase.charAt(i)));
                assertEquals(i == aPhrase.length() - 1, aMatched, aPhrase + " at index " + i);
            }

            // a full match clears the buffer, so the same sequence matches again
            assertTrue(feed(aCheck, aPhrase), aPhrase + " second pass");
        }
    }

    @Test
    void konamiSequenceMatches() {
        // LawnApp.cpp:1223-1233
        TypingCheck aCheck = new TypingCheck();
        aCheck.addKeyCode(KeyCode.KEYCODE_UP);
        aCheck.addKeyCode(KeyCode.KEYCODE_UP);
        aCheck.addKeyCode(KeyCode.KEYCODE_DOWN);
        aCheck.addKeyCode(KeyCode.KEYCODE_DOWN);
        aCheck.addKeyCode(KeyCode.KEYCODE_LEFT);
        aCheck.addKeyCode(KeyCode.KEYCODE_RIGHT);
        aCheck.addKeyCode(KeyCode.KEYCODE_LEFT);
        aCheck.addKeyCode(KeyCode.KEYCODE_RIGHT);
        aCheck.addChar('b');
        aCheck.addChar('a');

        assertEquals(10, aCheck.mPhrase.length());
        assertEquals(KeyCode.KEYCODE_UP, (int) aCheck.mPhrase.charAt(0));
        assertEquals(KeyCode.KEYCODE_DOWN, (int) aCheck.mPhrase.charAt(2));
        assertEquals(0x42, (int) aCheck.mPhrase.charAt(8)); // 'b' -> KEYCODE_B
        assertEquals(0x41, (int) aCheck.mPhrase.charAt(9)); // 'a' -> KEYCODE_A

        int[] aKeys = {
            KeyCode.KEYCODE_UP, KeyCode.KEYCODE_UP,
            KeyCode.KEYCODE_DOWN, KeyCode.KEYCODE_DOWN,
            KeyCode.KEYCODE_LEFT, KeyCode.KEYCODE_RIGHT,
            KeyCode.KEYCODE_LEFT, KeyCode.KEYCODE_RIGHT,
            0x42, 0x41
        };
        for (int i = 0; i < aKeys.length; i++) {
            assertEquals(i == aKeys.length - 1, aCheck.check(aKeys[i]), "konami index " + i);
        }
    }

    @Test
    void phraseStoresUpperCasedKeyCodes() {
        TypingCheck aCheck = new TypingCheck("Dance");
        assertEquals("DANCE", aCheck.mPhrase);
        assertEquals(0x44, (int) aCheck.mPhrase.charAt(0));
    }

    @Test
    void wrongSequenceResetsAndTheRollingWindowRecovers() {
        TypingCheck aCheck = new TypingCheck("dance");
        assertFalse(aCheck.check(letter('d')));
        assertFalse(aCheck.check(letter('x'))); // wrong key
        assertFalse(aCheck.check(letter('a')));
        assertFalse(aCheck.check(letter('n')));
        assertFalse(aCheck.check(letter('c')));
        assertFalse(aCheck.check(letter('e')));
        assertEquals(5, aCheck.mRecentTyping.length()); // trimmed to the phrase length

        // the buffer holds the last 5 keystrokes, so a fresh run of the phrase still matches
        assertTrue(feed(aCheck, "dance"));
    }

    @Test
    void emptyPhraseOnlyMatchesAnEmptyBuffer() {
        TypingCheck aCheck = new TypingCheck();
        assertEquals(0, aCheck.mPhrase.length());
        assertTrue(aCheck.check()); // upstream Check(): "" == "" -> true
        assertFalse(aCheck.check(letter('a'))); // Check(keyCode) returns false for an empty phrase
        assertFalse(aCheck.check()); // the stray keystroke is still in mRecentTyping
    }

    @Test
    void spaceResolvesToKeyCodeUnknownExactlyLikeUpstream() {
        // KeyCodes::GetKeyCodeFromName(" ") is KEYCODE_UNKNOWN (the name table uses "SPACE"), which is
        // why the upstream "tricked out" cheat is unreachable: a real space key-down sends 0x20.
        assertEquals(0x20, KeyCode.KEYCODE_SPACE);
        assertEquals(0, KeyCodes.getKeyCodeFromName(" "));

        TypingCheck aCheck = new TypingCheck("tricked out");
        assertEquals(11, aCheck.mPhrase.length());
        assertEquals(KeyCode.KEYCODE_UNKNOWN, (int) aCheck.mPhrase.charAt(7));
        assertFalse(feed(aCheck, "tricked out"));
    }
}
