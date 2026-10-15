/*
 * Ported from src/Lawn/System/Zombatar.h:1-140 and src/Lawn/System/Zombatar.cpp:1-100.
 *
 * The upstream header holds an enum (`ZombatarRecordSlot`), three little-endian record accessors
 * (`ZombatarReadRecordSlot` / `ZombatarReadSignedRecordSlot` / `ZombatarWriteRecordSlot`), the
 * colour palette (`gZombatarColors` + `ZombatarGetColor`), the accessory index remap
 * (`ZombatarRemapAccessoryForRuntime`), the page enum (`ZombatarPage`) and the part layout table
 * (`ZombatarPartLayout` + `GetPartLayout`).
 *
 * Upstream keeps these as free functions/inline helpers in the `Zombatar.h` header; the Java port
 * groups them as static members of one class, keeping the upstream names camelCased. The 18
 * `ZOMBATAR_SLOT_*` enumerators become `ZOMBATAR_SLOT_*` int constants of this class so callers
 * (ZombieStatus.applyZombatarHead, ZombatarWidget) can index the raw 0x48-byte record directly.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.sexy.Color;

/**
 * Ported from src/Lawn/System/Zombatar.h:22-140 (declarations) and src/Lawn/System/Zombatar.cpp:22-100
 * (part layout tables).
 *
 * <p>The zombatar record model: a raw 0x48-byte record is 18 little-endian int32 slots (see
 * {@link #ZOMBATAR_SLOT_SKIN_PART} … {@link #ZOMBATAR_SLOT_BACKGROUND_COLOR}); this class provides
 * the slot readers/writers, the shared colour palette and the per-page part placement those records
 * are rendered with.
 */
public final class Zombatar {

    private Zombatar() {
    }

    // Zombatar.h:22-40 — enum ZombatarRecordSlot.
    public static final int ZOMBATAR_SLOT_SKIN_PART = 0;
    public static final int ZOMBATAR_SLOT_SKIN_COLOR = 1;
    public static final int ZOMBATAR_SLOT_CLOTHES = 2;
    public static final int ZOMBATAR_SLOT_CLOTHES_COLOR = 3;
    public static final int ZOMBATAR_SLOT_TIDBITS = 4;
    public static final int ZOMBATAR_SLOT_TIDBITS_COLOR = 5;
    public static final int ZOMBATAR_SLOT_ACCESSORY = 6;
    public static final int ZOMBATAR_SLOT_ACCESSORY_COLOR = 7;
    public static final int ZOMBATAR_SLOT_FACIAL_HAIR = 8;
    public static final int ZOMBATAR_SLOT_FACIAL_HAIR_COLOR = 9;
    public static final int ZOMBATAR_SLOT_HAIR = 10;
    public static final int ZOMBATAR_SLOT_HAIR_COLOR = 11;
    public static final int ZOMBATAR_SLOT_EYEWEAR = 12;
    public static final int ZOMBATAR_SLOT_EYEWEAR_COLOR = 13;
    public static final int ZOMBATAR_SLOT_HATS = 14;
    public static final int ZOMBATAR_SLOT_HATS_COLOR = 15;
    public static final int ZOMBATAR_SLOT_BACKGROUND = 16;
    public static final int ZOMBATAR_SLOT_BACKGROUND_COLOR = 17;

    /**
     * Ported from src/Lawn/System/Zombatar.h:54-60 (ZombatarReadRecordSlot).
     *
     * <p>Reads the little-endian 32-bit value of {@code theSlot} as raw bits (upstream reads a
     * {@code uint32_t}; the Java {@code int} carries the same bits).
     */
    public static int readRecordSlot(byte[] theRecord, int theSlot) {
        int anOffset = theSlot * 4;
        return (theRecord[anOffset] & 0xFF)
                | ((theRecord[anOffset + 1] & 0xFF) << 8)
                | ((theRecord[anOffset + 2] & 0xFF) << 16)
                | ((theRecord[anOffset + 3] & 0xFF) << 24);
    }

    /**
     * Ported from src/Lawn/System/Zombatar.h:63-70 (ZombatarReadSignedRecordSlot).
     *
     * <p>Any value above {@code INT32_MAX} (i.e. a negative signed int) reads back as {@code -1},
     * which is the "slot empty" marker used by the editor and the runtime head composition.
     */
    public static int readSignedRecordSlot(byte[] theRecord, int theSlot) {
        int aValue = readRecordSlot(theRecord, theSlot);
        if (Integer.compareUnsigned(aValue, Integer.MAX_VALUE) > 0) {
            return -1;
        }
        return aValue;
    }

    /** Ported from src/Lawn/System/Zombatar.h:72-77 (ZombatarWriteRecordSlot). */
    public static void writeRecordSlot(byte[] theRecord, int theSlot, int theValue) {
        int anOffset = theSlot * 4;
        theRecord[anOffset] = (byte) theValue;
        theRecord[anOffset + 1] = (byte) (theValue >> 8);
        theRecord[anOffset + 2] = (byte) (theValue >> 16);
        theRecord[anOffset + 3] = (byte) (theValue >> 24);
    }

    // Zombatar.h:79-88 — constexpr Color gZombatarColors[] (48 entries).
    public static final Color[] gZombatarColors = {
            new Color(134, 147, 122), new Color(79, 135, 94), new Color(127, 135, 94),
            new Color(120, 130, 50), new Color(156, 163, 105), new Color(96, 151, 11),
            new Color(147, 184, 77), new Color(82, 143, 54), new Color(121, 168, 99),
            new Color(65, 156, 74), new Color(107, 178, 114), new Color(104, 121, 90),
            new Color(151, 33, 33), new Color(199, 53, 53), new Color(220, 112, 47),
            new Color(251, 251, 172), new Color(240, 210, 87), new Color(165, 126, 65),
            new Color(106, 72, 32), new Color(72, 35, 5), new Color(50, 56, 61),
            new Color(0, 0, 10), new Color(197, 239, 239), new Color(63, 109, 242),
            new Color(13, 202, 151), new Color(158, 183, 19), new Color(30, 210, 64),
            new Color(225, 65, 230), new Color(128, 47, 204), new Color(255, 255, 255),
            new Color(238, 19, 24), new Color(247, 89, 215), new Color(239, 198, 253),
            new Color(160, 56, 241), new Color(86, 74, 241), new Color(74, 160, 241),
            new Color(199, 244, 251), new Color(49, 238, 237), new Color(16, 194, 66),
            new Color(112, 192, 33), new Color(16, 145, 52), new Color(248, 247, 41),
            new Color(227, 180, 20), new Color(241, 115, 25), new Color(248, 247, 175),
            new Color(103, 85, 54), new Color(159, 17, 20), new Color(255, 255, 255)
    };

    /**
     * Ported from src/Lawn/System/Zombatar.h:91-96 (ZombatarGetColor).
     *
     * <p>Negative indices are "no color" (white); large indices clamp to the last palette entry.
     */
    public static Color getColor(int theIndex) {
        if (theIndex < 0) {
            return new Color(255, 255, 255);
        }
        return gZombatarColors[Math.min(theIndex, gZombatarColors.length - 1)];
    }

    /** Ported from src/Lawn/System/Zombatar.h:98-112 (ZombatarRemapAccessoryForRuntime). */
    public static int remapAccessoryForRuntime(int theIndex) {
        switch (theIndex) {
            case 5: return 14;
            case 6: return 5;
            case 7: return 6;
            case 8: return 12;
            case 9: return 7;
            case 10: return 9;
            case 11: return 10;
            case 12: return 11;
            case 14: return 8;
            default: return theIndex;
        }
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:75-78 (file-local {@code ZombatarTrackName}).
     *
     * <p>The runtime head reanim track name for a part prefix and index: {@code "{prefix}{index:02}"}.
     */
    public static String trackName(String thePrefix, int theIndex) {
        return String.format("%s%02d", thePrefix, theIndex);
    }

    // Zombatar.h:115-126 — enum ZombatarPage.
    public enum ZombatarPage {
        SKIN,
        HAIR,
        FACIAL_HAIR,
        TIDBITS,
        EYEWEAR,
        CLOTHES,
        ACCESSORY,
        HATS,
        BACKDROPS
    }

    /** Zombatar.h:126 — NUM_ZOMBATAR_PAGES. */
    public static final int NUM_ZOMBATAR_PAGES = 9;

    /** Zombatar.h:128-135 — struct ZombatarPartLayout. */
    public static final class ZombatarPartLayout {
        public int mOffsetX;
        public int mOffsetY;
        public int mColorOffsetX;
        public int mColorOffsetY;
        public int mDrawOrder;

        public ZombatarPartLayout(int theOffsetX, int theOffsetY, int theColorOffsetX,
                int theColorOffsetY, int theDrawOrder) {
            mOffsetX = theOffsetX;
            mOffsetY = theOffsetY;
            mColorOffsetX = theColorOffsetX;
            mColorOffsetY = theColorOffsetY;
            mDrawOrder = theDrawOrder;
        }
    }

    // Zombatar.cpp:24-29 — gClothesLayout[12].
    private static final ZombatarPartLayout[] gClothesLayout = {
            new ZombatarPartLayout(88, 110, 0, 0, 0), new ZombatarPartLayout(75, 100, 0, 0, 0),
            new ZombatarPartLayout(85, 112, 0, 0, 0), new ZombatarPartLayout(76, 110, 0, 0, 0),
            new ZombatarPartLayout(89, 115, 0, 0, 0), new ZombatarPartLayout(93, 110, 0, 0, 0),
            new ZombatarPartLayout(78, 105, 0, 0, 0), new ZombatarPartLayout(88, 110, 0, 0, 0),
            new ZombatarPartLayout(88, 102, 0, 0, 0), new ZombatarPartLayout(85, 110, 0, 0, 0),
            new ZombatarPartLayout(85, 110, 0, 0, 0), new ZombatarPartLayout(79, 112, 0, 0, 0)
    };

    // Zombatar.cpp:31-37 — gTidbitsLayout[14].
    private static final ZombatarPartLayout[] gTidbitsLayout = {
            new ZombatarPartLayout(28, 63, 18, 48, 2), new ZombatarPartLayout(28, 63, 0, 0, 2),
            new ZombatarPartLayout(46, 111, 0, 0, 2), new ZombatarPartLayout(31, 62, 0, 0, 2),
            new ZombatarPartLayout(31, 58, 0, 0, 2), new ZombatarPartLayout(28, 66, 0, 0, 2),
            new ZombatarPartLayout(28, 72, 0, 0, 2), new ZombatarPartLayout(33, 55, 0, 0, 2),
            new ZombatarPartLayout(21, 76, 0, 0, 2), new ZombatarPartLayout(36, 71, 0, 0, 2),
            new ZombatarPartLayout(36, 70, 0, 0, 2), new ZombatarPartLayout(86, 91, 0, 6, 2),
            new ZombatarPartLayout(88, 50, 0, 8, 2), new ZombatarPartLayout(113, 115, 0, 0, 2)
    };

    // Zombatar.cpp:39-46 — gAccessoryLayout[15].
    private static final ZombatarPartLayout[] gAccessoryLayout = {
            new ZombatarPartLayout(103, 110, 0, 0, 4), new ZombatarPartLayout(108, 110, 0, 0, 4),
            new ZombatarPartLayout(86, 113, 0, 0, 4), new ZombatarPartLayout(131, 95, 0, 0, 4),
            new ZombatarPartLayout(131, 100, 0, 0, 4), new ZombatarPartLayout(131, 100, 0, 0, 4),
            new ZombatarPartLayout(104, 111, 0, 0, 4), new ZombatarPartLayout(118, 65, 0, 0, 4),
            new ZombatarPartLayout(61, 118, 0, 0, 4), new ZombatarPartLayout(43, 100, 0, 0, 4),
            new ZombatarPartLayout(135, 92, 0, 0, 4), new ZombatarPartLayout(78, 130, 0, 0, 4),
            new ZombatarPartLayout(68, 145, 0, 0, 4), new ZombatarPartLayout(133, 70, 0, 0, 4),
            new ZombatarPartLayout(13, 40, 0, 0, 10)
    };

    // Zombatar.cpp:48-56 — gFacialHairLayout[24].
    private static final ZombatarPartLayout[] gFacialHairLayout = {
            new ZombatarPartLayout(35, 107, 1, 0, 6), new ZombatarPartLayout(51, 110, 0, 0, 6),
            new ZombatarPartLayout(45, 110, 0, 0, 6), new ZombatarPartLayout(38, 105, 3, 2, 6),
            new ZombatarPartLayout(69, 145, 0, 0, 6), new ZombatarPartLayout(48, 112, 0, 0, 6),
            new ZombatarPartLayout(13, 107, 0, 0, 6), new ZombatarPartLayout(45, 105, 1, 1, 6),
            new ZombatarPartLayout(41, 105, 1, 1, 6), new ZombatarPartLayout(44, 112, 1, 2, 6),
            new ZombatarPartLayout(43, 88, 1, 4, 6), new ZombatarPartLayout(28, 105, 8, 1, 6),
            new ZombatarPartLayout(45, 110, 0, 0, 6), new ZombatarPartLayout(18, 103, 1, 1, 6),
            new ZombatarPartLayout(63, 145, 2, 1, 6), new ZombatarPartLayout(63, 140, 1, 1, 6),
            new ZombatarPartLayout(43, 110, 0, 0, 6), new ZombatarPartLayout(58, 96, 1, 3, 6),
            new ZombatarPartLayout(46, 92, 0, 0, 6), new ZombatarPartLayout(114, 80, 0, 0, 6),
            new ZombatarPartLayout(118, 83, 1, 1, 6), new ZombatarPartLayout(13, 87, 3, 4, 6),
            new ZombatarPartLayout(58, 145, 1, 1, 6), new ZombatarPartLayout(38, 108, 4, 2, 6)
    };

    // Zombatar.cpp:58-64 — gHairLayout[16].
    private static final ZombatarPartLayout[] gHairLayout = {
            new ZombatarPartLayout(23, 0, 8, 1, 8), new ZombatarPartLayout(23, 25, 2, 3, 8),
            new ZombatarPartLayout(23, 30, 0, 0, 8), new ZombatarPartLayout(30, 15, 0, 0, 8),
            new ZombatarPartLayout(36, 37, 0, 0, 8), new ZombatarPartLayout(39, 13, 0, 0, 8),
            new ZombatarPartLayout(51, 22, 0, 0, 8), new ZombatarPartLayout(28, 15, 0, 0, 8),
            new ZombatarPartLayout(128, 55, 0, 0, 8), new ZombatarPartLayout(22, 32, 0, 0, 8),
            new ZombatarPartLayout(25, 19, 2, 2, 8), new ZombatarPartLayout(51, -5, 2, 2, 8),
            new ZombatarPartLayout(33, 13, 2, 2, 8), new ZombatarPartLayout(9, -2, 1, 5, 8),
            new ZombatarPartLayout(45, 4, 0, -1, 8), new ZombatarPartLayout(26, 20, 0, 0, 8)
    };

    // Zombatar.cpp:66-72 — gEyewearLayout[16].
    private static final ZombatarPartLayout[] gEyewearLayout = {
            new ZombatarPartLayout(28, 73, 0, 0, 10), new ZombatarPartLayout(31, 85, 0, -1, 10),
            new ZombatarPartLayout(28, 69, 0, 1, 10), new ZombatarPartLayout(28, 78, 0, -1, 10),
            new ZombatarPartLayout(30, 75, -1, -1, 10), new ZombatarPartLayout(30, 78, -1, -1, 10),
            new ZombatarPartLayout(50, 90, -1, -1, 10), new ZombatarPartLayout(32, 70, -1, -1, 10),
            new ZombatarPartLayout(36, 100, -1, -1, 10), new ZombatarPartLayout(31, 75, -1, -1, 10),
            new ZombatarPartLayout(31, 67, -1, -1, 10), new ZombatarPartLayout(38, 95, -1, -1, 10),
            new ZombatarPartLayout(30, 81, 0, 0, 10), new ZombatarPartLayout(35, 64, 0, 0, 10),
            new ZombatarPartLayout(42, 65, 0, 0, 10), new ZombatarPartLayout(35, 65, 0, 0, 10)
    };

    // Zombatar.cpp:74-79 — gHatsLayout[14].
    private static final ZombatarPartLayout[] gHatsLayout = {
            new ZombatarPartLayout(28, 5, 2, 1, 12), new ZombatarPartLayout(47, 12, 0, 0, 12),
            new ZombatarPartLayout(36, 20, 15, -1, 12), new ZombatarPartLayout(11, 10, 0, 0, 12),
            new ZombatarPartLayout(41, 16, 0, 0, 12), new ZombatarPartLayout(18, 3, 4, -2, 12),
            new ZombatarPartLayout(53, 17, 0, 15, 12), new ZombatarPartLayout(3, 0, 0, -2, 12),
            new ZombatarPartLayout(38, 0, -1, -2, 12), new ZombatarPartLayout(13, 45, 0, 0, 12),
            new ZombatarPartLayout(63, 8, 1, 14, 12), new ZombatarPartLayout(43, 15, 0, 0, 12),
            new ZombatarPartLayout(18, 0, 0, 0, 12), new ZombatarPartLayout(23, 5, 0, 0, 12)
    };

    /**
     * Ported from src/Lawn/System/Zombatar.cpp:81-100 (GetPartLayout).
     *
     * <p>Returns the layout for a page item, or {@code null} when the page has no layout table or
     * the index is out of range. Facial-hair indices above 16 are compacted by {@code aIdx -= aIdx/17}
     * because runtime track 17 does not exist.
     */
    public static ZombatarPartLayout getPartLayout(ZombatarPage thePage, int theIndex) {
        switch (thePage) {
            case CLOTHES:
                return theIndex < 12 ? gClothesLayout[theIndex] : null;
            case TIDBITS:
                return theIndex < 14 ? gTidbitsLayout[theIndex] : null;
            case ACCESSORY:
                return theIndex < 15 ? gAccessoryLayout[theIndex] : null;
            case FACIAL_HAIR: {
                int aIdx = theIndex;
                if (aIdx > 16) {
                    aIdx -= aIdx / 17;
                }
                return aIdx < 24 ? gFacialHairLayout[aIdx] : null;
            }
            case HAIR:
                return theIndex < 16 ? gHairLayout[theIndex] : null;
            case EYEWEAR:
                return theIndex < 16 ? gEyewearLayout[theIndex] : null;
            case HATS:
                return theIndex < 14 ? gHatsLayout[theIndex] : null;
            default:
                return null;
        }
    }
}
