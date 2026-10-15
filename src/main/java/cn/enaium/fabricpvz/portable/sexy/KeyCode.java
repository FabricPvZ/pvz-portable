package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/KeyCodes.h:34 (unscoped {@code enum KeyCode}).
 *
 * <p>The upstream enum is an unscoped C++ enum that aliases several enumerators to the same value
 * ({@code KANA}/{@code HANGEUL}/{@code HANGUL} = 0x15, {@code HANJA}/{@code KANJI} = 0x19), so it
 * cannot be modeled as a Java {@code enum}. Constants are therefore {@code public static final int}
 * with the upstream names and values, which also preserves the implicit {@code int} conversions the
 * C++ code relies on.
 *
 * <p>The value ranges 0x30..0x5A and 0xB3..0xE0 are the ASCII key ranges; the key-name tables live in
 * {@link KeyCodes}.
 */
public final class KeyCode {
    private KeyCode() {
    }

    public static final int KEYCODE_UNKNOWN = 0x00;
    public static final int KEYCODE_LBUTTON = 0x01;
    public static final int KEYCODE_RBUTTON = 0x02;
    public static final int KEYCODE_CANCEL = 0x03;
    public static final int KEYCODE_MBUTTON = 0x04;
    public static final int KEYCODE_BACK = 0x08;
    public static final int KEYCODE_TAB = 0x09;
    public static final int KEYCODE_CLEAR = 0x0C;
    public static final int KEYCODE_RETURN = 0x0D;
    public static final int KEYCODE_SHIFT = 0x10;
    public static final int KEYCODE_CONTROL = 0x11;
    public static final int KEYCODE_MENU = 0x12;
    public static final int KEYCODE_PAUSE = 0x13;
    public static final int KEYCODE_CAPITAL = 0x14;
    public static final int KEYCODE_KANA = 0x15;
    public static final int KEYCODE_HANGEUL = 0x15;
    public static final int KEYCODE_HANGUL = 0x15;
    public static final int KEYCODE_JUNJA = 0x17;
    public static final int KEYCODE_FINAL = 0x18;
    public static final int KEYCODE_HANJA = 0x19;
    public static final int KEYCODE_KANJI = 0x19;
    public static final int KEYCODE_ESCAPE = 0x1B;
    public static final int KEYCODE_CONVERT = 0x1C;
    public static final int KEYCODE_NONCONVERT = 0x1D;
    public static final int KEYCODE_ACCEPT = 0x1E;
    public static final int KEYCODE_MODECHANGE = 0x1F;
    public static final int KEYCODE_SPACE = 0x20;
    public static final int KEYCODE_PRIOR = 0x21;
    public static final int KEYCODE_NEXT = 0x22;
    public static final int KEYCODE_END = 0x23;
    public static final int KEYCODE_HOME = 0x24;
    public static final int KEYCODE_LEFT = 0x25;
    public static final int KEYCODE_UP = 0x26;
    public static final int KEYCODE_RIGHT = 0x27;
    public static final int KEYCODE_DOWN = 0x28;
    public static final int KEYCODE_SELECT = 0x29;
    public static final int KEYCODE_PRINT = 0x2A;
    public static final int KEYCODE_EXECUTE = 0x2B;
    public static final int KEYCODE_SNAPSHOT = 0x2C;
    public static final int KEYCODE_INSERT = 0x2D;
    public static final int KEYCODE_DELETE = 0x2E;
    public static final int KEYCODE_HELP = 0x2F;
    public static final int KEYCODE_ASCIIBEGIN = 0x30;
    public static final int KEYCODE_ASCIIEND = 0x5A;
    public static final int KEYCODE_LWIN = 0x5B;
    public static final int KEYCODE_RWIN = 0x5C;
    public static final int KEYCODE_APPS = 0x5D;
    public static final int KEYCODE_NUMPAD0 = 0x60;
    public static final int KEYCODE_NUMPAD1 = 0x61;
    public static final int KEYCODE_NUMPAD2 = 0x62;
    public static final int KEYCODE_NUMPAD3 = 0x63;
    public static final int KEYCODE_NUMPAD4 = 0x64;
    public static final int KEYCODE_NUMPAD5 = 0x65;
    public static final int KEYCODE_NUMPAD6 = 0x66;
    public static final int KEYCODE_NUMPAD7 = 0x67;
    public static final int KEYCODE_NUMPAD8 = 0x68;
    public static final int KEYCODE_NUMPAD9 = 0x69;
    public static final int KEYCODE_MULTIPLY = 0x6A;
    public static final int KEYCODE_ADD = 0x6B;
    public static final int KEYCODE_SEPARATOR = 0x6C;
    public static final int KEYCODE_SUBTRACT = 0x6D;
    public static final int KEYCODE_DECIMAL = 0x6E;
    public static final int KEYCODE_DIVIDE = 0x6F;
    public static final int KEYCODE_F1 = 0x70;
    public static final int KEYCODE_F2 = 0x71;
    public static final int KEYCODE_F3 = 0x72;
    public static final int KEYCODE_F4 = 0x73;
    public static final int KEYCODE_F5 = 0x74;
    public static final int KEYCODE_F6 = 0x75;
    public static final int KEYCODE_F7 = 0x76;
    public static final int KEYCODE_F8 = 0x77;
    public static final int KEYCODE_F9 = 0x78;
    public static final int KEYCODE_F10 = 0x79;
    public static final int KEYCODE_F11 = 0x7A;
    public static final int KEYCODE_F12 = 0x7B;
    public static final int KEYCODE_F13 = 0x7C;
    public static final int KEYCODE_F14 = 0x7D;
    public static final int KEYCODE_F15 = 0x7E;
    public static final int KEYCODE_F16 = 0x7F;
    public static final int KEYCODE_F17 = 0x80;
    public static final int KEYCODE_F18 = 0x81;
    public static final int KEYCODE_F19 = 0x82;
    public static final int KEYCODE_F20 = 0x83;
    public static final int KEYCODE_F21 = 0x84;
    public static final int KEYCODE_F22 = 0x85;
    public static final int KEYCODE_F23 = 0x86;
    public static final int KEYCODE_F24 = 0x87;
    public static final int KEYCODE_NUMLOCK = 0x90;
    public static final int KEYCODE_SCROLL = 0x91;
    /** Ported from KeyCodes.h:125 ({@code //ASCII + 0x80}). */
    public static final int KEYCODE_ASCIIBEGIN2 = 0xB3;
    public static final int KEYCODE_ASCIIEND2 = 0xE0;
}
