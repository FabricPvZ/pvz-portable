package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Table and lookup tests for {@link KeyCodes}/{@link KeyCode}, asserting the exact upstream table
 * contents (src/SexyAppFramework/misc/KeyCodes.cpp).
 */
class KeyCodesTest {

    @Test
    void tableContents() {
        assertEquals(87, KeyCodes.aKeyCodeArray.length);
        assertEquals(12, KeyCodes.MAX_KEYNAME_LEN);

        // spot-check the head, the aliases and the tail of the table
        assertEquals("UNKNOWN", KeyCodes.aKeyCodeArray[0].mKeyName);
        assertEquals(0x00, KeyCodes.aKeyCodeArray[0].mKeyCode);
        assertEquals("KANA", KeyCodes.aKeyCodeArray[14].mKeyName);
        assertEquals(0x15, KeyCodes.aKeyCodeArray[14].mKeyCode);
        assertEquals("HANGEUL", KeyCodes.aKeyCodeArray[15].mKeyName);
        assertEquals(0x15, KeyCodes.aKeyCodeArray[15].mKeyCode);
        assertEquals("HANGUL", KeyCodes.aKeyCodeArray[16].mKeyName);
        assertEquals(0x15, KeyCodes.aKeyCodeArray[16].mKeyCode);
        assertEquals("HANJA", KeyCodes.aKeyCodeArray[19].mKeyName);
        assertEquals(0x19, KeyCodes.aKeyCodeArray[19].mKeyCode);
        assertEquals("KANJI", KeyCodes.aKeyCodeArray[20].mKeyName);
        assertEquals(0x19, KeyCodes.aKeyCodeArray[20].mKeyCode);
        assertEquals("SCROLL", KeyCodes.aKeyCodeArray[86].mKeyName);
        assertEquals(0x91, KeyCodes.aKeyCodeArray[86].mKeyCode);

        Set<String> aNames = new HashSet<>();
        for (KeyCodes.KeyNameEntry anEntry : KeyCodes.aKeyCodeArray) {
            aNames.add(anEntry.mKeyName);
        }
        assertEquals(KeyCodes.aKeyCodeArray.length, aNames.size());
    }

    @Test
    void tableRoundTrips() {
        for (KeyCodes.KeyNameEntry anEntry : KeyCodes.aKeyCodeArray) {
            assertEquals(anEntry.mKeyCode, KeyCodes.getKeyCodeFromName(anEntry.mKeyName), anEntry.mKeyName);
            assertEquals(anEntry.mKeyCode,
                    KeyCodes.getKeyCodeFromName(KeyCodes.getKeyNameFromCode(anEntry.mKeyCode)),
                    anEntry.mKeyName);
        }
    }

    @Test
    void aliasedCodesResolveToFirstTableEntry() {
        // KeyCodes.cpp:179-181: GetKeyNameFromCode returns the FIRST entry whose code matches, so
        // every alias of 0x15 / 0x19 resolves to the earlier name (KANA/HANJA), not HANGEUL/KANJI.
        assertEquals("KANA", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_KANA));
        assertEquals("KANA", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_HANGEUL));
        assertEquals("KANA", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_HANGUL));
        assertEquals("HANJA", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_HANJA));
        assertEquals("HANJA", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_KANJI));

        assertEquals(KeyCode.KEYCODE_HANGUL, KeyCodes.getKeyCodeFromName("HANGUL"));
        assertEquals(KeyCode.KEYCODE_KANJI, KeyCodes.getKeyCodeFromName("KANJI"));
    }

    @Test
    void asciiSingleCharacterNames() {
        assertEquals(0x30, KeyCodes.getKeyCodeFromName("0"));
        assertEquals(0x41, KeyCodes.getKeyCodeFromName("A"));
        assertEquals(0x41, KeyCodes.getKeyCodeFromName("a"));
        assertEquals(0x5A, KeyCodes.getKeyCodeFromName("Z"));
        assertEquals(0x33, KeyCodes.getKeyCodeFromName("3"));
        // 0x33..0x60 map to +0x80 (KEYCODE_ASCIIBEGIN2 range)
        assertEquals(0x60 + 0x80, KeyCodes.getKeyCodeFromName("`"));
        assertEquals(0x5B + 0x80, KeyCodes.getKeyCodeFromName("["));

        assertEquals("A", KeyCodes.getKeyNameFromCode(0x41));
        assertEquals("0", KeyCodes.getKeyNameFromCode(0x30));
        assertEquals("Z", KeyCodes.getKeyNameFromCode(0x5A));
        assertEquals("3", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_ASCIIBEGIN2));
        assertEquals("[", KeyCodes.getKeyNameFromCode(0x5B + 0x80));
        assertEquals("`", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_ASCIIEND2));
    }

    @Test
    void lookupsAreCaseInsensitiveAndGuarded() {
        assertEquals(KeyCode.KEYCODE_NONCONVERT, KeyCodes.getKeyCodeFromName("nonconvert"));
        assertEquals(KeyCode.KEYCODE_NUMPAD5, KeyCodes.getKeyCodeFromName("NuMpAd5"));
        assertEquals(KeyCode.KEYCODE_F24, KeyCodes.getKeyCodeFromName("f24"));

        // names of 11+ characters are rejected before the lookup
        assertEquals(KeyCode.KEYCODE_UNKNOWN, KeyCodes.getKeyCodeFromName("ABCDEFGHIJK"));
        assertEquals(KeyCode.KEYCODE_UNKNOWN, KeyCodes.getKeyCodeFromName("NONCONVERTX"));
        assertEquals(KeyCode.KEYCODE_UNKNOWN, KeyCodes.getKeyCodeFromName(""));
        assertEquals(KeyCode.KEYCODE_UNKNOWN, KeyCodes.getKeyCodeFromName("NOT_A_KEY"));

        assertEquals("UNKNOWN", KeyCodes.getKeyNameFromCode(KeyCode.KEYCODE_UNKNOWN));
        assertEquals("UNKNOWN", KeyCodes.getKeyNameFromCode(-1));
        assertEquals("UNKNOWN", KeyCodes.getKeyNameFromCode(0xAB));
    }

    @Test
    void constants() {
        assertEquals(0x00, KeyCode.KEYCODE_UNKNOWN);
        assertEquals(0x03, KeyCode.KEYCODE_CANCEL);
        assertEquals(0x04, KeyCode.KEYCODE_MBUTTON);
        assertEquals(0x08, KeyCode.KEYCODE_BACK);
        assertEquals(0x0C, KeyCode.KEYCODE_CLEAR);
        assertEquals(0x15, KeyCode.KEYCODE_KANA);
        assertEquals(0x15, KeyCode.KEYCODE_HANGEUL);
        assertEquals(0x15, KeyCode.KEYCODE_HANGUL);
        assertEquals(0x19, KeyCode.KEYCODE_HANJA);
        assertEquals(0x19, KeyCode.KEYCODE_KANJI);
        assertEquals(0x30, KeyCode.KEYCODE_ASCIIBEGIN);
        assertEquals(0x5A, KeyCode.KEYCODE_ASCIIEND);
        assertEquals(0x70, KeyCode.KEYCODE_F1);
        assertEquals(0x87, KeyCode.KEYCODE_F24);
        assertEquals(0x90, KeyCode.KEYCODE_NUMLOCK);
        assertEquals(0x91, KeyCode.KEYCODE_SCROLL);
        assertEquals(0xB3, KeyCode.KEYCODE_ASCIIBEGIN2);
        assertEquals(0xE0, KeyCode.KEYCODE_ASCIIEND2);

        assertNotEquals(KeyCode.KEYCODE_F1, KeyCode.KEYCODE_F2);
        assertEquals(0x2D, KeyCode.KEYCODE_ASCIIEND2 - KeyCode.KEYCODE_ASCIIBEGIN2);
    }
}
