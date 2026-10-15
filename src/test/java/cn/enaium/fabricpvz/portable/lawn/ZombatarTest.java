/*
 * Tests for {@link Zombatar} (ported from src/Lawn/System/Zombatar.h:22-140 and Zombatar.cpp:24-100).
 *
 * Asset-free and pure: the record slot IO, colour palette lookup, accessory remap, runtime track
 * naming and the per-page part layout tables. No LawnApp, no resources.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Zombatar.ZombatarPage;
import cn.enaium.fabricpvz.portable.lawn.Zombatar.ZombatarPartLayout;
import cn.enaium.fabricpvz.portable.sexy.Color;

class ZombatarTest {

    private static final int RECORD_SIZE = 0x48;

    @Test
    void slotConstantsMatchUpstreamEnum() {
        // Zombatar.h:32-40 — enum ZombatarRecordSlot.
        assertEquals(0, Zombatar.ZOMBATAR_SLOT_SKIN_PART);
        assertEquals(1, Zombatar.ZOMBATAR_SLOT_SKIN_COLOR);
        assertEquals(2, Zombatar.ZOMBATAR_SLOT_CLOTHES);
        assertEquals(3, Zombatar.ZOMBATAR_SLOT_CLOTHES_COLOR);
        assertEquals(4, Zombatar.ZOMBATAR_SLOT_TIDBITS);
        assertEquals(5, Zombatar.ZOMBATAR_SLOT_TIDBITS_COLOR);
        assertEquals(6, Zombatar.ZOMBATAR_SLOT_ACCESSORY);
        assertEquals(7, Zombatar.ZOMBATAR_SLOT_ACCESSORY_COLOR);
        assertEquals(8, Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR);
        assertEquals(9, Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR_COLOR);
        assertEquals(10, Zombatar.ZOMBATAR_SLOT_HAIR);
        assertEquals(11, Zombatar.ZOMBATAR_SLOT_HAIR_COLOR);
        assertEquals(12, Zombatar.ZOMBATAR_SLOT_EYEWEAR);
        assertEquals(13, Zombatar.ZOMBATAR_SLOT_EYEWEAR_COLOR);
        assertEquals(14, Zombatar.ZOMBATAR_SLOT_HATS);
        assertEquals(15, Zombatar.ZOMBATAR_SLOT_HATS_COLOR);
        assertEquals(16, Zombatar.ZOMBATAR_SLOT_BACKGROUND);
        assertEquals(17, Zombatar.ZOMBATAR_SLOT_BACKGROUND_COLOR);
    }

    @Test
    void writeThenReadRecordSlotRoundTripsLittleEndian() {
        byte[] aRecord = new byte[RECORD_SIZE];

        Zombatar.writeRecordSlot(aRecord, 0, 0x12345678);
        Zombatar.writeRecordSlot(aRecord, 17, 0x0BADF00D);

        // little-endian byte order at 4*slot (Zombatar.h:54-60)
        assertEquals(0x78, aRecord[0] & 0xFF);
        assertEquals(0x56, aRecord[1] & 0xFF);
        assertEquals(0x34, aRecord[2] & 0xFF);
        assertEquals(0x12, aRecord[3] & 0xFF);

        assertEquals(0x12345678, Zombatar.readRecordSlot(aRecord, 0));
        assertEquals(0x0BADF00D, Zombatar.readRecordSlot(aRecord, 17));
    }

    @Test
    void readSignedRecordSlotTreatsNegativeIntsAsMinusOne() {
        byte[] aRecord = new byte[RECORD_SIZE];

        Zombatar.writeRecordSlot(aRecord, 0, 7);
        assertEquals(7, Zombatar.readSignedRecordSlot(aRecord, 0));

        // -1 written back is 0xFFFFFFFF, which reads as the "empty" marker.
        Zombatar.writeRecordSlot(aRecord, 1, -1);
        assertEquals(0xFFFFFFFF, Zombatar.readRecordSlot(aRecord, 1));
        assertEquals(-1, Zombatar.readSignedRecordSlot(aRecord, 1));

        // 0x7FFFFFFF is the largest still-valid value (Zombatar.h:63-70).
        Zombatar.writeRecordSlot(aRecord, 2, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, Zombatar.readSignedRecordSlot(aRecord, 2));

        Zombatar.writeRecordSlot(aRecord, 3, 0x80000000);
        assertEquals(-1, Zombatar.readSignedRecordSlot(aRecord, 3));
    }

    @Test
    void getColorHandlesOutOfRangeIndices() {
        // Zombatar.h:91-96 — negative is white, large clamps to the final entry.
        assertEquals(255, Zombatar.getColor(-1).mRed);
        assertEquals(255, Zombatar.getColor(-1).mGreen);
        assertEquals(255, Zombatar.getColor(-1).mBlue);

        // gZombatarColors[0] = (134,147,122); [47] = (255,255,255).
        assertEquals(134, Zombatar.getColor(0).mRed);
        assertEquals(147, Zombatar.getColor(0).mGreen);
        assertEquals(122, Zombatar.getColor(0).mBlue);

        Color aLast = Zombatar.getColor(48);
        Color aLastEntry = Zombatar.gZombatarColors[Zombatar.gZombatarColors.length - 1];
        assertEquals(aLastEntry.mRed, aLast.mRed);
        assertEquals(aLastEntry.mGreen, aLast.mGreen);
        assertEquals(aLastEntry.mBlue, aLast.mBlue);
        assertSame(aLastEntry, aLast);
    }

    @Test
    void paletteHasFortyEightEntries() {
        // Zombatar.h:79-88 — gZombatarColors[48].
        assertEquals(48, Zombatar.gZombatarColors.length);
        // Spot-check a few palette anchors used by the editor.
        assertEquals(151, Zombatar.getColor(12).mRed);
        assertEquals(238, Zombatar.getColor(30).mRed);
        assertEquals(255, Zombatar.getColor(29).mRed);
    }

    @Test
    void remapAccessoryForRuntimeMatchesUpstreamTable() {
        // Zombatar.h:98-112.
        assertEquals(14, Zombatar.remapAccessoryForRuntime(5));
        assertEquals(5, Zombatar.remapAccessoryForRuntime(6));
        assertEquals(6, Zombatar.remapAccessoryForRuntime(7));
        assertEquals(12, Zombatar.remapAccessoryForRuntime(8));
        assertEquals(7, Zombatar.remapAccessoryForRuntime(9));
        assertEquals(9, Zombatar.remapAccessoryForRuntime(10));
        assertEquals(10, Zombatar.remapAccessoryForRuntime(11));
        assertEquals(11, Zombatar.remapAccessoryForRuntime(12));
        assertEquals(8, Zombatar.remapAccessoryForRuntime(14));
        // default: unchanged
        assertEquals(0, Zombatar.remapAccessoryForRuntime(0));
        assertEquals(13, Zombatar.remapAccessoryForRuntime(13));
    }

    @Test
    void trackNameFormatsTwoDigitIndex() {
        // Zombie.cpp:75-78, std::format("{}{:02d}").
        assertEquals("hats_03", Zombatar.trackName("hats_", 3));
        assertEquals("hair_12", Zombatar.trackName("hair_", 12));
        assertEquals("tidBits_00", Zombatar.trackName("tidBits_", 0));
        assertEquals("accessories_100", Zombatar.trackName("accessories_", 100));
    }

    @Test
    void pageEnumOrdinalsMatchUpstream() {
        assertEquals(0, ZombatarPage.SKIN.ordinal());
        assertEquals(1, ZombatarPage.HAIR.ordinal());
        assertEquals(2, ZombatarPage.FACIAL_HAIR.ordinal());
        assertEquals(3, ZombatarPage.TIDBITS.ordinal());
        assertEquals(4, ZombatarPage.EYEWEAR.ordinal());
        assertEquals(5, ZombatarPage.CLOTHES.ordinal());
        assertEquals(6, ZombatarPage.ACCESSORY.ordinal());
        assertEquals(7, ZombatarPage.HATS.ordinal());
        assertEquals(8, ZombatarPage.BACKDROPS.ordinal());
        assertEquals(9, Zombatar.NUM_ZOMBATAR_PAGES);
    }

    @Test
    void partLayoutTablesMatchUpstream() {
        // Zombatar.cpp:24-79.
        assertLayout(Zombatar.getPartLayout(ZombatarPage.CLOTHES, 0), 88, 110, 0, 0, 0);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.CLOTHES, 11), 79, 112, 0, 0, 0);

        assertLayout(Zombatar.getPartLayout(ZombatarPage.TIDBITS, 0), 28, 63, 18, 48, 2);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.TIDBITS, 11), 86, 91, 0, 6, 2);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.TIDBITS, 13), 113, 115, 0, 0, 2);

        assertLayout(Zombatar.getPartLayout(ZombatarPage.ACCESSORY, 0), 103, 110, 0, 0, 4);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.ACCESSORY, 14), 13, 40, 0, 0, 10);

        assertLayout(Zombatar.getPartLayout(ZombatarPage.HAIR, 0), 23, 0, 8, 1, 8);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.HAIR, 15), 26, 20, 0, 0, 8);

        assertLayout(Zombatar.getPartLayout(ZombatarPage.EYEWEAR, 0), 28, 73, 0, 0, 10);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.EYEWEAR, 4), 30, 75, -1, -1, 10);

        assertLayout(Zombatar.getPartLayout(ZombatarPage.HATS, 0), 28, 5, 2, 1, 12);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.HATS, 13), 23, 5, 0, 0, 12);
    }

    @Test
    void facialHairLayoutCompactsIndicesAboveSixteen() {
        // Zombatar.cpp:88-94: aIdx -= aIdx/17 for indices > 16.
        assertLayout(Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 0), 35, 107, 1, 0, 6);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 16), 43, 110, 0, 0, 6);
        // 17 -> 16, 18 -> 17, 24 -> 23
        assertLayout(Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 17), 43, 110, 0, 0, 6);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 18), 58, 96, 1, 3, 6);
        assertLayout(Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 24), 38, 108, 4, 2, 6);
        // Past the compacted range there is no layout.
        assertNull(Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 25));
        assertNull(Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 34));
    }

    @Test
    void partLayoutOutOfRangeAndColorOnlyPagesReturnNull() {
        assertNull(Zombatar.getPartLayout(ZombatarPage.CLOTHES, 12));
        assertNull(Zombatar.getPartLayout(ZombatarPage.TIDBITS, 14));
        assertNull(Zombatar.getPartLayout(ZombatarPage.ACCESSORY, 15));
        assertNull(Zombatar.getPartLayout(ZombatarPage.HAIR, 16));
        assertNull(Zombatar.getPartLayout(ZombatarPage.EYEWEAR, 16));
        assertNull(Zombatar.getPartLayout(ZombatarPage.HATS, 14));
        // SKIN and BACKDROPS have no layout table at all.
        assertNull(Zombatar.getPartLayout(ZombatarPage.SKIN, 0));
        assertNull(Zombatar.getPartLayout(ZombatarPage.BACKDROPS, 0));
    }

    @Test
    void layoutCarriesColorOffsetAndDrawOrder() {
        ZombatarPartLayout aLayout = Zombatar.getPartLayout(ZombatarPage.FACIAL_HAIR, 3);
        assertNotNull(aLayout);
        assertEquals(3, aLayout.mColorOffsetX);
        assertEquals(2, aLayout.mColorOffsetY);
        assertEquals(6, aLayout.mDrawOrder);

        ZombatarPartLayout aHats = Zombatar.getPartLayout(ZombatarPage.HATS, 8);
        assertNotNull(aHats);
        assertEquals(-1, aHats.mColorOffsetX);
        assertEquals(-2, aHats.mColorOffsetY);
        assertEquals(12, aHats.mDrawOrder);
    }

    private static void assertLayout(ZombatarPartLayout theLayout, int theOffsetX, int theOffsetY,
            int theColorOffsetX, int theColorOffsetY, int theDrawOrder) {
        assertNotNull(theLayout);
        assertEquals(theOffsetX, theLayout.mOffsetX);
        assertEquals(theOffsetY, theLayout.mOffsetY);
        assertEquals(theColorOffsetX, theLayout.mColorOffsetX);
        assertEquals(theColorOffsetY, theLayout.mColorOffsetY);
        assertEquals(theDrawOrder, theLayout.mDrawOrder);
    }
}
