package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Behavior tests for {@link PvzpStringFile}, asserting the parsing/lookup rules of
 * src/PvzpLib/PvzpStringFile.cpp.
 */
class PvzpStringFileTest {

    private SexyAppBase mApp;

    @BeforeEach
    void newApp() {
        // The bank lives in the app's string properties (upstream gSexyAppBase->mStringProperties).
        mApp = new SexyAppBase();
        // main() installs the built-in table once; do the same for the formatting tests.
        PvzpStringFile.pvzpStringListSetColors(PvzpStringFile.gLawnStringFormats,
                PvzpStringFile.gLawnStringFormatCount);
    }

    /** One-unit-wide font double: widths are string lengths, draws are recorded. */
    private static final class FixedFont extends Font {

        final List<String> mDrawn = new ArrayList<>();
        final List<Integer> mDrawnX = new ArrayList<>();
        final List<Integer> mDrawnY = new ArrayList<>();
        final List<Color> mDrawnColors = new ArrayList<>();

        FixedFont() {
            mAscent = 10;
            mAscentPadding = 2;
            mHeight = 12;
        }

        @Override
        public int stringWidth(String theString) {
            return theString.length();
        }

        @Override
        public int charWidth(int theChar) {
            return 1;
        }

        @Override
        public int charWidthKern(int theChar, int thePrevChar) {
            return 1;
        }

        @Override
        public void drawString(Graphics g, int theX, int theY, String theString, Color theColor,
                Rect theClipRect) {
            mDrawn.add(theString);
            mDrawnX.add(theX);
            mDrawnY.add(theY);
            mDrawnColors.add(theColor);
        }

        @Override
        public Font duplicate() {
            return this;
        }
    }

    private static Graphics fakeGraphics() {
        Graphics g = new Graphics();
        g.mTransY = 0;
        g.mClipRect = new Rect(0, 0, 1000, 1000);
        return g;
    }

    /** A realistic slice of Properties/LawnStrings.txt. */
    private static final String TABLE = """
            [LOADING]Loading...
            [CLICK_TO_START]Click to Start
            [NESTED_KEY]text with [BRACKETS] inside
            [MULTI_LINE]Line one\r
            Line two
            [UNICODE]Gr\u00fc\u00dfe
            """;

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    @Test
    void readItemsFillsTheAppStringBank() {
        assertTrue(PvzpStringFile.readItems(TABLE));

        assertEquals("Loading...", mApp.mStringProperties.get("LOADING"));
        assertEquals("Click to Start", mApp.mStringProperties.get("CLICK_TO_START"));
        assertEquals("Gr\u00fc\u00dfe", mApp.mStringProperties.get("UNICODE"));
    }

    @Test
    void valuesEndAtTheNextBracket() {
        assertTrue(PvzpStringFile.readItems(TABLE));

        // The value of NESTED_KEY stops at the '[' of the following name.
        assertEquals("text with", mApp.mStringProperties.get("NESTED_KEY"));
        assertEquals("inside", mApp.mStringProperties.get("BRACKETS"));
    }

    @Test
    void valuesAreTrimmedAndLoseReturnCharacters() {
        assertTrue(PvzpStringFile.readItems(TABLE));

        assertEquals("Line one\nLine two", mApp.mStringProperties.get("MULTI_LINE"));
    }

    @Test
    void keysAreUpperCasedOnRead() {
        assertTrue(PvzpStringFile.readItems("[loading]Loading...\n"));

        assertEquals("Loading...", mApp.mStringProperties.get("LOADING"));
        assertEquals("<Missing loading>", PvzpStringFile.find("loading"));
    }

    @Test
    void duplicateKeysKeepTheLastValue() {
        assertTrue(PvzpStringFile.readItems("[KEY]first\n[KEY]second\n"));

        assertEquals("second", mApp.mStringProperties.get("KEY"));
    }

    @Test
    void trailingWhitespaceTerminatesTheTable() {
        assertTrue(PvzpStringFile.readItems("[A]1\n[KEY]v\n\n   \t\n"));
        assertEquals("v", PvzpStringFile.find("KEY"));
    }

    @Test
    void emptyTableParses() {
        assertTrue(PvzpStringFile.readItems(""));
        assertTrue(PvzpStringFile.readItems("   \n\t "));
    }

    // ------------------------------------------------------------------
    // readName / readValue cursors
    // ------------------------------------------------------------------

    @Test
    void readNameAndValueWalkTheCursor() {
        String aText = "[KEY] v";
        int[] aPtr = {0};
        String[] aName = {""};
        String[] aValue = {""};

        assertTrue(PvzpStringFile.readName(aText, aPtr, aName));
        assertEquals("KEY", aName[0]);
        assertEquals(5, aPtr[0]); // one past ']'

        assertTrue(PvzpStringFile.readValue(aText, aPtr, aValue));
        assertEquals("v", aValue[0]);
        assertEquals(aText.length(), aPtr[0]);
    }

    @Test
    void readNameTerminatesOnWhitespaceOnlyRemainder() {
        int[] aPtr = {0};
        String[] aName = {"unset"};

        assertTrue(PvzpStringFile.readName("  \t\n", aPtr, aName));
        assertEquals("", aName[0]);
        assertFalse(PvzpStringFile.readName("garbage", new int[] {0}, aName));
    }

    @Test
    void readNameReportsMissingBracketAndShortName() {
        String[] aName = {""};

        assertFalse(PvzpStringFile.readName("[KEY x", new int[] {0}, aName));
        assertFalse(PvzpStringFile.readName("[]", new int[] {0}, aName));
        assertFalse(PvzpStringFile.readName("[   ]", new int[] {0}, aName));
    }

    @Test
    void readNameCursorArithmeticIsRelativeToTheCursor() {
        // PORT-NOTE: upstream does `thePtr += aCount + 2` relative to the cursor, not to the located
        // '[', so text before the first name shifts the cursor (PvzpStringFile.cpp:95). Real
        // LawnStrings.txt starts with '[' so this only shows up for hand-written input.
        String aText = "  [A] x";
        int[] aPtr = {0};
        String[] aName = {""};
        String[] aValue = {""};

        assertTrue(PvzpStringFile.readName(aText, aPtr, aName));
        assertEquals("A", aName[0]);
        assertEquals(3, aPtr[0]);

        assertTrue(PvzpStringFile.readValue(aText, aPtr, aValue));
        assertEquals("A] x", aValue[0]);
    }

    @Test
    void readValueWithoutAnotherBracketTakesTheRest() {
        int[] aPtr = {4};
        String[] aValue = {""};

        assertTrue(PvzpStringFile.readValue("[KB]tail \r\n", aPtr, aValue));
        assertEquals("tail", aValue[0]);
        assertEquals("[KB]tail \r\n".length(), aPtr[0]);
    }

    @Test
    void removeReturnCharsStripsOnlyCarriageReturns() {
        assertEquals("a\nb", PvzpStringFile.removeReturnChars("a\r\nb"));
        assertEquals("", PvzpStringFile.removeReturnChars("\r\r"));
        assertEquals("plain", PvzpStringFile.removeReturnChars("plain"));
    }

    // ------------------------------------------------------------------
    // Lookup / translation
    // ------------------------------------------------------------------

    @Test
    void findReturnsMissingPlaceholderForUnknownKeys() {
        assertTrue(PvzpStringFile.readItems(TABLE));

        assertEquals("Click to Start", PvzpStringFile.find("CLICK_TO_START"));
        assertEquals("<Missing NOPE>", PvzpStringFile.find("NOPE"));
    }

    @Test
    void translateReplacesBracketedKeysOnly() {
        assertTrue(PvzpStringFile.readItems(TABLE));

        assertEquals("Loading...", PvzpStringFile.translate("[LOADING]"));
        assertEquals("plain text", PvzpStringFile.translate("plain text"));
        assertEquals("<Missing MISSING_KEY>", PvzpStringFile.translate("[MISSING_KEY]"));
        // Length-3 requirement: "[A" is too short to be a key lookup.
        assertEquals("[A", PvzpStringFile.translate("[A"));
        assertEquals("", PvzpStringFile.translate(null));
    }

    @Test
    void tryTranslateAndExistsReportUnknownKeys() {
        assertTrue(PvzpStringFile.readItems(TABLE));

        assertEquals(Optional.of("inside"), PvzpStringFile.tryTranslate("[BRACKETS]"));
        assertEquals(Optional.empty(), PvzpStringFile.tryTranslate("[NOPE]"));
        assertEquals(Optional.empty(), PvzpStringFile.tryTranslate("plain"));

        assertTrue(PvzpStringFile.exists("[MULTI_LINE]"));
        assertFalse(PvzpStringFile.exists("[NOPE]"));
        assertFalse(PvzpStringFile.exists("plain"));
    }

    @Test
    void laterAppStringsOverrideTheLoadedTable() {
        // LawnApp loads LawnStrings.txt first and the localized properties/*.xml afterwards, writing
        // into the same map, so the override wins.
        assertTrue(PvzpStringFile.readItems("[LOADING]Loading...\n"));
        assertEquals("Loading...", PvzpStringFile.translate("[LOADING]"));

        mApp.setString("LOADING", "\u52a0\u8f7d\u4e2d...");
        assertEquals("\u52a0\u8f7d\u4e2d...", PvzpStringFile.translate("[LOADING]"));
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    @Test
    void readFileGoesThroughTheApp(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("LawnStrings.txt");
        Files.writeString(aFile, "[LOADING]Loading...\n[QUIT]Quit\n", StandardCharsets.UTF_8);

        assertTrue(PvzpStringFile.readFile(aFile.toString()));
        assertEquals("Quit", PvzpStringFile.find("QUIT"));
    }

    @Test
    void readFileHandlesUtf8BomAndCrlf(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("bom.txt");
        Files.write(aFile, ("\uFEFF[KEY]value\r\n[OTHER]two\r\n").getBytes(StandardCharsets.UTF_8));

        assertTrue(PvzpStringFile.readFile(aFile.toString()));
        assertEquals("value", PvzpStringFile.find("KEY"));
        assertEquals("two", PvzpStringFile.find("OTHER"));
    }

    @Test
    void missingFileFails(@TempDir Path theDir) {
        assertFalse(PvzpStringFile.readFile(theDir.resolve("nope.txt").toString()));
        assertFalse(PvzpStringFile.load(theDir.resolve("nope.txt").toString()));
        assertEquals("<Missing KEY>", PvzpStringFile.find("KEY"));
    }

    @Test
    void loadReturnsSuccessForAValidFile(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("strings.txt");
        Files.writeString(aFile, "[A]1\n", StandardCharsets.UTF_8);

        assertTrue(PvzpStringFile.load(aFile.toString()));
        assertEquals("1", PvzpStringFile.find("A"));
    }

    // ------------------------------------------------------------------
    // Format table
    // ------------------------------------------------------------------

    @Test
    void lawnFormatTableMatchesUpstream() {
        assertEquals(12, PvzpStringFile.gLawnStringFormatCount);
        assertEquals(12, PvzpStringFile.gLawnStringFormats.length);

        String[] aNames = {"NORMAL", "FLAVOR", "KEYWORD", "NOCTURNAL", "AQUATIC", "STAT", "METAL",
                "KEYMETAL", "SHORTLINE", "EXTRASHORTLINE", "CREDITS1", "CREDITS2"};
        for (int i = 0; i < aNames.length; i++) {
            assertEquals(aNames[i], PvzpStringFile.gLawnStringFormats[i].mFormatName);
            assertNull(PvzpStringFile.gLawnStringFormats[i].mNewFont);
        }

        PvzpStringListFormat aNormal = PvzpStringFile.gLawnStringFormats[0];
        assertEquals(new Color(40, 50, 90, 255), aNormal.mNewColor);
        assertEquals(0, aNormal.mLineSpacingOffset);
        assertEquals(0, aNormal.mFormatFlags);

        assertEquals(1, PvzpStringFile.gLawnStringFormats[1].mFormatFlags); // FLAVOR ignores newlines
        assertEquals(2, PvzpStringFile.gLawnStringFormats[6].mFormatFlags); // METAL hides until magnetshroom
        assertEquals(new Color(204, 36, 29, 255), PvzpStringFile.gLawnStringFormats[6].mNewColor);

        // The "line" entries keep the current colour (0,0,0,0 means "don't touch").
        assertEquals(new Color(0, 0, 0, 0), PvzpStringFile.gLawnStringFormats[8].mNewColor);
        assertEquals(-9, PvzpStringFile.gLawnStringFormats[8].mLineSpacingOffset);
        assertEquals(-14, PvzpStringFile.gLawnStringFormats[9].mLineSpacingOffset);
        assertEquals(3, PvzpStringFile.gLawnStringFormats[10].mLineSpacingOffset);
        assertEquals(2, PvzpStringFile.gLawnStringFormats[11].mLineSpacingOffset);
    }

    @Test
    void setColorsInstallsTheTable() {
        PvzpStringListFormat[] aCustom = {new PvzpStringListFormat("X", null, Color.White, 1, 0)};
        PvzpStringFile.pvzpStringListSetColors(aCustom, 1);

        assertSame(aCustom, PvzpStringFile.gPvzpStringFormats);
        assertEquals(1, PvzpStringFile.gPvzpStringFormatCount);

        PvzpStringFile.pvzpStringListSetColors(PvzpStringFile.gLawnStringFormats,
                PvzpStringFile.gLawnStringFormatCount);

        assertSame(PvzpStringFile.gLawnStringFormats, PvzpStringFile.gPvzpStringFormats);
        assertEquals(12, PvzpStringFile.gPvzpStringFormatCount);
    }

    @Test
    void writeStringSetFormatMatchesTheNamePrefix() {
        FixedFont aFont = new FixedFont();
        PvzpStringListFormat aFormat = new PvzpStringListFormat("", aFont, Color.White, 0, 0);

        PvzpStringFile.pvzpWriteStringSetFormat("NORMAL rest", aFormat);
        assertEquals(new Color(40, 50, 90, 255), aFormat.mNewColor);
        assertEquals(0, aFormat.mLineSpacingOffset);
        assertEquals(0, aFormat.mFormatFlags);

        // strncmp semantics: the entry name only has to be a prefix of the scanned format.
        PvzpStringFile.pvzpWriteStringSetFormat("SHORTLINE plus", aFormat);
        assertEquals(-9, aFormat.mLineSpacingOffset);
        // SHORTLINE's colour is (0,0,0,0), so the previous colour survives.
        assertEquals(new Color(40, 50, 90, 255), aFormat.mNewColor);

        PvzpStringFile.pvzpWriteStringSetFormat("METAL", aFormat);
        assertEquals(new Color(204, 36, 29, 255), aFormat.mNewColor);
        assertEquals(2, aFormat.mFormatFlags);

        PvzpStringFile.pvzpWriteStringSetFormat("NOPE", aFormat); // unknown: nothing changes
        assertEquals(new Color(204, 36, 29, 255), aFormat.mNewColor);
        assertEquals(2, aFormat.mFormatFlags);
    }

    @Test
    void formatCopyDoesNotShareTheColour() {
        // The almanac mutates mNewColor.mAlpha / mLineSpacingOffset on its own copy, like upstream
        // copies the struct by value; the shared table entry must stay intact.
        PvzpStringListFormat aCopy = new PvzpStringListFormat(PvzpStringFile.gLawnStringFormats[0]);
        aCopy.mNewColor.mAlpha = 0;
        aCopy.mLineSpacingOffset = -17;

        assertEquals(255, PvzpStringFile.gLawnStringFormats[0].mNewColor.mAlpha);
        assertEquals(0, PvzpStringFile.gLawnStringFormats[0].mLineSpacingOffset);
        assertEquals("NORMAL", aCopy.mFormatName);
    }

    @Test
    void charIsSpaceInFormatHonoursTheIgnoreNewlinesFlag() {
        PvzpStringListFormat aFormat = new PvzpStringListFormat("", null, Color.White, 0, 0);

        assertTrue(PvzpStringFile.charIsSpaceInFormat(' ', aFormat));
        assertFalse(PvzpStringFile.charIsSpaceInFormat('\n', aFormat));
        assertFalse(PvzpStringFile.charIsSpaceInFormat('x', aFormat));

        // PVZP_FORMAT_IGNORE_NEWLINES is the *bit index* (0): TestBit is num & (1 << idx)
        // (PvzpCommon.h:187), so the flag value must be 1 << PVZP_FORMAT_IGNORE_NEWLINES.
        aFormat.mFormatFlags = 1 << PvzpStringFormatFlag.PVZP_FORMAT_IGNORE_NEWLINES;
        assertTrue(PvzpStringFile.charIsSpaceInFormat('\n', aFormat));
        assertTrue(PvzpStringFile.charIsSpaceInFormat(' ', aFormat));
        assertFalse(PvzpStringFile.charIsSpaceInFormat('\t', aFormat));
    }

    // ------------------------------------------------------------------
    // Formatted drawing
    // ------------------------------------------------------------------

    @Test
    void writeStringSplitsOnFormatCodesAndAppliesColours() {
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();
        PvzpStringListFormat aFormat = new PvzpStringListFormat("", aFont, Color.White, 0, 0);

        int aWidth = PvzpStringFile.pvzpWriteString(g, "a{STAT}b", 10, 20, aFormat, 100,
                DrawStringJustification.DS_ALIGN_LEFT, true, 0, -1);

        assertEquals(2, aWidth);
        assertEquals(List.of("a", "b"), aFont.mDrawn);
        assertEquals(List.of(10, 11), aFont.mDrawnX);
        assertEquals(List.of(20, 20), aFont.mDrawnY);
        assertEquals(Color.White, aFont.mDrawnColors.get(0));
        assertEquals(new Color(204, 36, 29, 255), aFont.mDrawnColors.get(1));
    }

    @Test
    void writeStringAlignsUsingTheMeasuredWidth() {
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();
        PvzpStringListFormat aFormat = new PvzpStringListFormat("", aFont, Color.White, 0, 0);

        PvzpStringFile.pvzpWriteString(g, "abc", 0, 0, aFormat, 10,
                DrawStringJustification.DS_ALIGN_RIGHT, true, 0, -1);
        assertEquals(7, aFont.mDrawnX.get(0));

        aFont.mDrawn.clear();
        aFont.mDrawnX.clear();
        PvzpStringFile.pvzpWriteString(g, "abc", 0, 0, aFormat, 10,
                DrawStringJustification.DS_ALIGN_CENTER, true, 0, -1);
        assertEquals(3, aFont.mDrawnX.get(0)); // 7 / 2
    }

    @Test
    void writeStringOffsetAndLengthSelectTheSubstring() {
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();
        PvzpStringListFormat aFormat = new PvzpStringListFormat("", aFont, Color.White, 0, 0);

        assertEquals(4, PvzpStringFile.pvzpWriteString(g, "abcdef", 0, 0, aFormat, 100,
                DrawStringJustification.DS_ALIGN_LEFT, true, 2, -1));
        assertEquals(List.of("cdef"), aFont.mDrawn);

        aFont.mDrawn.clear();
        assertEquals(2, PvzpStringFile.pvzpWriteString(g, "abcdef", 0, 0, aFormat, 100,
                DrawStringJustification.DS_ALIGN_LEFT, true, 2, 2));
        assertEquals(List.of("cd"), aFont.mDrawn);

        aFont.mDrawn.clear();
        assertEquals(4, PvzpStringFile.pvzpWriteString(g, "abcdef", 0, 0, aFormat, 100,
                DrawStringJustification.DS_ALIGN_LEFT, true, 2, 100));
        assertEquals(List.of("cdef"), aFont.mDrawn);
    }

    @Test
    void writeStringTurnsNewlinesIntoSpacesWhenTheFlagIsSet() {
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();
        PvzpStringListFormat aFormat = new PvzpStringListFormat("", aFont, Color.White, 0,
                1 << PvzpStringFormatFlag.PVZP_FORMAT_IGNORE_NEWLINES);

        assertEquals(3, PvzpStringFile.pvzpWriteString(g, "a\nb", 0, 0, aFormat, 100,
                DrawStringJustification.DS_ALIGN_LEFT, true, 0, -1));
        assertEquals(List.of("a b"), aFont.mDrawn);
    }

    @Test
    void wordWrappedHelperClampsToMaxChars() {
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();
        PvzpStringListFormat aFormat = new PvzpStringListFormat("", aFont, Color.White, 0, 0);

        assertEquals(2, PvzpStringFile.pvzpWriteWordWrappedHelper(g, "abcdef", 0, 0, aFormat, 100,
                DrawStringJustification.DS_ALIGN_LEFT, true, 4, 5, 6));
        assertEquals(List.of("ef"), aFont.mDrawn);

        aFont.mDrawn.clear();
        assertEquals(-1, PvzpStringFile.pvzpWriteWordWrappedHelper(g, "abcdef", 0, 0, aFormat, 100,
                DrawStringJustification.DS_ALIGN_LEFT, true, 6, 1, 6));
        assertEquals(List.of(), aFont.mDrawn);
    }

    @Test
    void wrappedHelperWrapsAndReturnsTheHeight() {
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();

        int aHeight = PvzpStringFile.pvzpDrawStringWrappedHelper(g, "abcdef", new Rect(0, 0, 4, 100),
                aFont, Color.White, DrawStringJustification.DS_ALIGN_LEFT, true);

        assertEquals(22, aHeight);
        assertEquals(List.of("abcd", "ef"), aFont.mDrawn);
        assertEquals(List.of(0, 0), aFont.mDrawnX);
        assertEquals(List.of(8, 20), aFont.mDrawnY);
    }

    @Test
    void wrappedDrawStripsFormatCodesAndKeepsTheBaseColour() {
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();

        int aHeight = PvzpStringFile.pvzpDrawStringWrappedHelper(g, "a{STAT}b",
                new Rect(0, 0, 100, 100), aFont, Color.White, DrawStringJustification.DS_ALIGN_LEFT,
                true);

        assertEquals(10, aHeight);
        assertEquals(List.of("a", "b"), aFont.mDrawn);
        // The wrapper's own scan restores the base colour after the switch
        // (PvzpStringFile.cpp:347-349), but the text is drawn by PvzpWriteString, which re-applies
        // "{STAT}" inside the drawn substring and paints the rest in the format's colour
        // (PvzpStringFile.cpp:269-273).
        assertEquals(Color.White, aFont.mDrawnColors.get(0));
        assertEquals(new Color(204, 36, 29, 255), aFont.mDrawnColors.get(1));
    }

    @Test
    void wrappedDrawTranslatesWholeKeysOnly() {
        assertTrue(PvzpStringFile.readItems("[KEY]value\n"));
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();

        PvzpStringFile.pvzpDrawStringWrapped(g, "[KEY]", new Rect(0, 0, 100, 100), aFont, Color.White,
                DrawStringJustification.DS_ALIGN_LEFT);
        assertEquals(List.of("value"), aFont.mDrawn);

        // A bracketed key must be the whole string: "[KEY] tail" is looked up as "KEY] tai".
        aFont.mDrawn.clear();
        PvzpStringFile.pvzpDrawStringWrapped(g, "[KEY] tail", new Rect(0, 0, 100, 100), aFont,
                Color.White, DrawStringJustification.DS_ALIGN_LEFT);
        assertEquals(List.of("<Missing KEY] tai>"), aFont.mDrawn);
    }

    @Test
    void wrappedDrawVerticalMiddleCentresTheBlock() {
        assertTrue(PvzpStringFile.readItems("[KEY]value\n"));
        FixedFont aFont = new FixedFont();
        Graphics g = fakeGraphics();

        PvzpStringFile.pvzpDrawStringWrapped(g, "[KEY]", new Rect(0, 0, 100, 100), aFont, Color.White,
                DrawStringJustification.DS_ALIGN_LEFT);
        assertEquals(8, aFont.mDrawnY.get(0));

        aFont.mDrawn.clear();
        aFont.mDrawnY.clear();
        PvzpStringFile.pvzpDrawStringWrapped(g, "[KEY]", new Rect(0, 0, 100, 100), aFont, Color.White,
                DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);

        // Measured height is 10, so the text block starts (100 - 10) / 2 = 45 lower.
        assertEquals(1, aFont.mDrawn.size());
        assertEquals(53, aFont.mDrawnY.get(0));
    }
}
