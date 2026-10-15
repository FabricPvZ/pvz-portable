package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/KeyCodes.cpp:33 (MAX_KEYNAME_LEN), :35 (KeyNameEntry),
 * :41 (aKeyCodeArray) and :132/:165 (the two lookup functions).
 *
 * <p>The upstream file-scope {@code aKeyCodeArray} is exposed as a public immutable table so the
 * name/code round-trips can be verified; upstream keeps it file-static.
 */
public final class KeyCodes {
    private KeyCodes() {
    }

    /** Ported from src/SexyAppFramework/misc/KeyCodes.cpp:33. Upstream {@code char mKeyName[MAX_KEYNAME_LEN]}. */
    public static final int MAX_KEYNAME_LEN = 12;

    /** Ported from src/SexyAppFramework/misc/KeyCodes.cpp:35. */
    public static final class KeyNameEntry {
        public final String mKeyName;
        public final int mKeyCode;

        public KeyNameEntry(String mKeyName, int mKeyCode) {
            this.mKeyName = mKeyName;
            this.mKeyCode = mKeyCode;
        }
    }

    /** Ported from src/SexyAppFramework/misc/KeyCodes.cpp:41 (exact table contents and order). */
    public static final KeyNameEntry[] aKeyCodeArray = {
            new KeyNameEntry("UNKNOWN", KeyCode.KEYCODE_UNKNOWN),
            new KeyNameEntry("LBUTTON", KeyCode.KEYCODE_LBUTTON),
            new KeyNameEntry("RBUTTON", KeyCode.KEYCODE_RBUTTON),
            new KeyNameEntry("CANCEL", KeyCode.KEYCODE_CANCEL),
            new KeyNameEntry("MBUTTON", KeyCode.KEYCODE_MBUTTON),
            new KeyNameEntry("BACK", KeyCode.KEYCODE_BACK),
            new KeyNameEntry("TAB", KeyCode.KEYCODE_TAB),
            new KeyNameEntry("CLEAR", KeyCode.KEYCODE_CLEAR),
            new KeyNameEntry("RETURN", KeyCode.KEYCODE_RETURN),
            new KeyNameEntry("SHIFT", KeyCode.KEYCODE_SHIFT),
            new KeyNameEntry("CONTROL", KeyCode.KEYCODE_CONTROL),
            new KeyNameEntry("MENU", KeyCode.KEYCODE_MENU),
            new KeyNameEntry("PAUSE", KeyCode.KEYCODE_PAUSE),
            new KeyNameEntry("CAPITAL", KeyCode.KEYCODE_CAPITAL),
            new KeyNameEntry("KANA", KeyCode.KEYCODE_KANA),
            new KeyNameEntry("HANGEUL", KeyCode.KEYCODE_HANGEUL),
            new KeyNameEntry("HANGUL", KeyCode.KEYCODE_HANGUL),
            new KeyNameEntry("JUNJA", KeyCode.KEYCODE_JUNJA),
            new KeyNameEntry("FINAL", KeyCode.KEYCODE_FINAL),
            new KeyNameEntry("HANJA", KeyCode.KEYCODE_HANJA),
            new KeyNameEntry("KANJI", KeyCode.KEYCODE_KANJI),
            new KeyNameEntry("ESCAPE", KeyCode.KEYCODE_ESCAPE),
            new KeyNameEntry("CONVERT", KeyCode.KEYCODE_CONVERT),
            new KeyNameEntry("NONCONVERT", KeyCode.KEYCODE_NONCONVERT),
            new KeyNameEntry("ACCEPT", KeyCode.KEYCODE_ACCEPT),
            new KeyNameEntry("MODECHANGE", KeyCode.KEYCODE_MODECHANGE),
            new KeyNameEntry("SPACE", KeyCode.KEYCODE_SPACE),
            new KeyNameEntry("PRIOR", KeyCode.KEYCODE_PRIOR),
            new KeyNameEntry("NEXT", KeyCode.KEYCODE_NEXT),
            new KeyNameEntry("END", KeyCode.KEYCODE_END),
            new KeyNameEntry("HOME", KeyCode.KEYCODE_HOME),
            new KeyNameEntry("LEFT", KeyCode.KEYCODE_LEFT),
            new KeyNameEntry("UP", KeyCode.KEYCODE_UP),
            new KeyNameEntry("RIGHT", KeyCode.KEYCODE_RIGHT),
            new KeyNameEntry("DOWN", KeyCode.KEYCODE_DOWN),
            new KeyNameEntry("SELECT", KeyCode.KEYCODE_SELECT),
            new KeyNameEntry("PRINT", KeyCode.KEYCODE_PRINT),
            new KeyNameEntry("EXECUTE", KeyCode.KEYCODE_EXECUTE),
            new KeyNameEntry("SNAPSHOT", KeyCode.KEYCODE_SNAPSHOT),
            new KeyNameEntry("INSERT", KeyCode.KEYCODE_INSERT),
            new KeyNameEntry("DELETE", KeyCode.KEYCODE_DELETE),
            new KeyNameEntry("HELP", KeyCode.KEYCODE_HELP),
            new KeyNameEntry("LWIN", KeyCode.KEYCODE_LWIN),
            new KeyNameEntry("RWIN", KeyCode.KEYCODE_RWIN),
            new KeyNameEntry("APPS", KeyCode.KEYCODE_APPS),
            new KeyNameEntry("NUMPAD0", KeyCode.KEYCODE_NUMPAD0),
            new KeyNameEntry("NUMPAD1", KeyCode.KEYCODE_NUMPAD1),
            new KeyNameEntry("NUMPAD2", KeyCode.KEYCODE_NUMPAD2),
            new KeyNameEntry("NUMPAD3", KeyCode.KEYCODE_NUMPAD3),
            new KeyNameEntry("NUMPAD4", KeyCode.KEYCODE_NUMPAD4),
            new KeyNameEntry("NUMPAD5", KeyCode.KEYCODE_NUMPAD5),
            new KeyNameEntry("NUMPAD6", KeyCode.KEYCODE_NUMPAD6),
            new KeyNameEntry("NUMPAD7", KeyCode.KEYCODE_NUMPAD7),
            new KeyNameEntry("NUMPAD8", KeyCode.KEYCODE_NUMPAD8),
            new KeyNameEntry("NUMPAD9", KeyCode.KEYCODE_NUMPAD9),
            new KeyNameEntry("MULTIPLY", KeyCode.KEYCODE_MULTIPLY),
            new KeyNameEntry("ADD", KeyCode.KEYCODE_ADD),
            new KeyNameEntry("SEPARATOR", KeyCode.KEYCODE_SEPARATOR),
            new KeyNameEntry("SUBTRACT", KeyCode.KEYCODE_SUBTRACT),
            new KeyNameEntry("DECIMAL", KeyCode.KEYCODE_DECIMAL),
            new KeyNameEntry("DIVIDE", KeyCode.KEYCODE_DIVIDE),
            new KeyNameEntry("F1", KeyCode.KEYCODE_F1),
            new KeyNameEntry("F2", KeyCode.KEYCODE_F2),
            new KeyNameEntry("F3", KeyCode.KEYCODE_F3),
            new KeyNameEntry("F4", KeyCode.KEYCODE_F4),
            new KeyNameEntry("F5", KeyCode.KEYCODE_F5),
            new KeyNameEntry("F6", KeyCode.KEYCODE_F6),
            new KeyNameEntry("F7", KeyCode.KEYCODE_F7),
            new KeyNameEntry("F8", KeyCode.KEYCODE_F8),
            new KeyNameEntry("F9", KeyCode.KEYCODE_F9),
            new KeyNameEntry("F10", KeyCode.KEYCODE_F10),
            new KeyNameEntry("F11", KeyCode.KEYCODE_F11),
            new KeyNameEntry("F12", KeyCode.KEYCODE_F12),
            new KeyNameEntry("F13", KeyCode.KEYCODE_F13),
            new KeyNameEntry("F14", KeyCode.KEYCODE_F14),
            new KeyNameEntry("F15", KeyCode.KEYCODE_F15),
            new KeyNameEntry("F16", KeyCode.KEYCODE_F16),
            new KeyNameEntry("F17", KeyCode.KEYCODE_F17),
            new KeyNameEntry("F18", KeyCode.KEYCODE_F18),
            new KeyNameEntry("F19", KeyCode.KEYCODE_F19),
            new KeyNameEntry("F20", KeyCode.KEYCODE_F20),
            new KeyNameEntry("F21", KeyCode.KEYCODE_F21),
            new KeyNameEntry("F22", KeyCode.KEYCODE_F22),
            new KeyNameEntry("F23", KeyCode.KEYCODE_F23),
            new KeyNameEntry("F24", KeyCode.KEYCODE_F24),
            new KeyNameEntry("NUMLOCK", KeyCode.KEYCODE_NUMLOCK),
            new KeyNameEntry("SCROLL", KeyCode.KEYCODE_SCROLL)
    };

    /**
     * Ported from src/SexyAppFramework/misc/KeyCodes.cpp:132.
     *
     * <p>The name is upper-cased ASCII-only (upstream {@code toupper} over bytes in the "C" locale)
     * and the single-character ASCII ranges are checked before the table, exactly like upstream. A
     * name of 11 or more characters is rejected outright (upstream {@code MAX_KEYNAME_LEN - 1}).
     */
    public static int getKeyCodeFromName(String theKeyName) {
        if (theKeyName.length() >= MAX_KEYNAME_LEN - 1) {
            return KeyCode.KEYCODE_UNKNOWN;
        }

        String aKeyName = Common.stringToUpper(theKeyName);

        if (theKeyName.length() == 1) {
            int aKeyNameChar = aKeyName.charAt(0) & 0xFFFF;

            if (aKeyNameChar >= KeyCode.KEYCODE_ASCIIBEGIN && aKeyNameChar <= KeyCode.KEYCODE_ASCIIEND) {
                return aKeyNameChar;
            }

            if (aKeyNameChar >= (KeyCode.KEYCODE_ASCIIBEGIN2 - 0x80)
                    && aKeyNameChar <= (KeyCode.KEYCODE_ASCIIEND2 - 0x80)) {
                return aKeyNameChar + 0x80;
            }
        }

        for (KeyNameEntry anEntry : aKeyCodeArray) {
            if (anEntry.mKeyName.equals(aKeyName)) {
                return anEntry.mKeyCode;
            }
        }

        return KeyCode.KEYCODE_UNKNOWN;
    }

    /**
     * Ported from src/SexyAppFramework/misc/KeyCodes.cpp:165.
     *
     * <p>Returns the first table name with the given code (so the aliases resolve to
     * {@code HANGEUL}/{@code HANJA}).
     */
    public static String getKeyNameFromCode(int theKeyCode) {
        if (theKeyCode >= KeyCode.KEYCODE_ASCIIBEGIN && theKeyCode <= KeyCode.KEYCODE_ASCIIEND) {
            return String.valueOf((char) theKeyCode);
        }

        if (theKeyCode >= KeyCode.KEYCODE_ASCIIBEGIN2 && theKeyCode <= KeyCode.KEYCODE_ASCIIEND2) {
            return String.valueOf((char) ((theKeyCode & 0xFF) - 0x80));
        }

        for (KeyNameEntry anEntry : aKeyCodeArray) {
            if (theKeyCode == anEntry.mKeyCode) {
                return anEntry.mKeyName;
            }
        }

        return "UNKNOWN";
    }
}
