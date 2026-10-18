package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavior tests for {@link Common}, asserting upstream-derived expected values
 * (src/SexyAppFramework/Common.cpp).
 */
class CommonTest {

    /**
     * Restores the process-wide framework statics this test touches, so other suites in the same
     * JVM see the upstream defaults (empty folders, MTRand default seed 4357).
     */
    @AfterEach
    void restoreFrameworkState() {
        Common.setAppDataFolder("");
        Common.setResourceFolder("");
        Common.sRand(4357);
    }

    // ------------------------------------------------------------------
    // Case conversion / trims
    // ------------------------------------------------------------------

    @Test
    void stringToUpperLower() {
        assertEquals("HELLO, WORLD_123", Common.stringToUpper("Hello, world_123"));
        assertEquals("hello, world_123", Common.stringToLower("Hello, WORLD_123"));
        assertEquals("", Common.stringToUpper(""));
        assertEquals("", Common.stringToLower(""));
        // Common.cpp:148-163: std::toupper/std::tolower on an unsigned char in the "C" locale, so
        // only a-z / A-Z are folded and every byte >= 0x80 (café / CAFÉ) is passed through.
        assertEquals("CAF\u00e9", Common.stringToUpper("caf\u00e9"));
        assertEquals("caf\u00c9", Common.stringToLower("CAF\u00c9"));
        assertEquals("ALREADY", Common.upper("already"));
        assertEquals("already", Common.lower("ALREADY"));
    }

    @Test
    void inlineTrims() {
        assertEquals("abc", Common.inlineLTrim("  abc"));
        assertEquals("abc", Common.inlineRTrim("abc  "));
        assertEquals("abc", Common.inlineTrim("\t abc \n"));
        assertEquals("", Common.inlineLTrim("   "));
        assertEquals("", Common.inlineRTrim("   "));
        assertEquals("", Common.inlineTrim("   "));
        assertEquals("a b", Common.inlineTrim("a b"));

        assertEquals("a", Common.inlineTrim("..a..", "."));
        assertEquals("", Common.inlineRTrim("..", "."));
        assertEquals("", Common.inlineLTrim("..", "."));
        assertEquals("ba", Common.inlineLTrim("..ba", "."));
    }

    @Test
    void trimUnicode() {
        assertEquals("", Common.trim(""));
        assertEquals("", Common.trim("   "));
        assertEquals("abc", Common.trim("\t\r\n abc \n"));
        assertEquals("abc", Common.trim("abc"));
        assertEquals("a b", Common.trim("a b"));
        assertEquals("abc", Common.trim("\u3000abc\u3000"));
        assertEquals("abc", Common.trim("\u00a0abc\u00a0"));
        assertEquals("abc", Common.trim("\u2003abc\u202f"));
        assertEquals("abc", Common.trim("\u0085abc\u3000"));
    }

    // ------------------------------------------------------------------
    // Number parsing / formatting
    // ------------------------------------------------------------------

    @Test
    void stringToInt() {
        assertEquals(Integer.valueOf(123), Common.stringToInt("123"));
        assertEquals(Integer.valueOf(12), Common.stringToInt(" 12"));
        assertEquals(Integer.valueOf(26), Common.stringToInt("0x1A"));
        assertEquals(Integer.valueOf(8), Common.stringToInt("010"));
        assertEquals(Integer.valueOf(0), Common.stringToInt("0"));
        assertEquals(Integer.valueOf(0), Common.stringToInt("0x0"));
        assertEquals(Integer.valueOf(5), Common.stringToInt("+5"));
        assertEquals(Integer.valueOf(-5), Common.stringToInt("-5"));
        assertEquals(Integer.valueOf(-26), Common.stringToInt("-0X1a"));
        // strtol already exceeds int here; the upstream (int) truncation wraps
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), Common.stringToInt("2147483648"));
        // overflow saturates to LONG_MAX / LONG_MIN, then wraps on the (int) cast
        assertEquals(Integer.valueOf(-1), Common.stringToInt("9223372036854775808"));
        assertEquals(Integer.valueOf(0), Common.stringToInt("-9223372036854775809"));

        assertNull(Common.stringToInt(""));
        assertNull(Common.stringToInt("abc"));
        assertNull(Common.stringToInt("12x"));
        assertNull(Common.stringToInt("12 "));
        assertNull(Common.stringToInt("1.5"));
        assertNull(Common.stringToInt("0x"));
        assertNull(Common.stringToInt("-"));
        assertNull(Common.stringToInt("+"));
        assertNull(Common.stringToInt("08"));
        assertNull(Common.stringToInt("1 2"));
    }

    @Test
    void stringToDouble() {
        assertEquals(Double.valueOf(1.5), Common.stringToDouble("1.5"));
        assertEquals(Double.valueOf(0.5), Common.stringToDouble(".5"));
        assertEquals(Double.valueOf(1.0), Common.stringToDouble("1."));
        assertEquals(Double.valueOf(1000.0), Common.stringToDouble("1e3"));
        assertEquals(Double.valueOf(-0.0025), Common.stringToDouble("-2.5e-3"));
        assertEquals(Double.valueOf(12.0), Common.stringToDouble(" \t12"));
        assertEquals(Double.POSITIVE_INFINITY, Common.stringToDouble("inf").doubleValue());
        assertEquals(Double.POSITIVE_INFINITY, Common.stringToDouble("Infinity").doubleValue());
        assertEquals(Double.NEGATIVE_INFINITY, Common.stringToDouble("-Infinity").doubleValue());
        assertTrue(Double.isNaN(Common.stringToDouble("nan")));
        assertEquals(Double.valueOf(3.0), Common.stringToDouble("0x1.8p1"));
        assertEquals(Double.valueOf(1.5), Common.stringToDouble("0x1.8"));
        assertEquals(Double.valueOf(-3.0), Common.stringToDouble("-0x1.8p1"));
        assertEquals(Double.valueOf(6.0), Common.stringToDouble("0x3p1"));

        assertNull(Common.stringToDouble(""));
        assertNull(Common.stringToDouble("abc"));
        assertNull(Common.stringToDouble("1.5x"));
        assertNull(Common.stringToDouble("1.5 "));
        assertNull(Common.stringToDouble("."));
        assertNull(Common.stringToDouble("e3"));
        assertNull(Common.stringToDouble("1e"));
        assertNull(Common.stringToDouble("in"));
        assertNull(Common.stringToDouble("0x"));
    }

    @Test
    void commaSeperate() {
        assertEquals("0", Common.commaSeperate(0));
        assertEquals("5", Common.commaSeperate(5));
        assertEquals("999", Common.commaSeperate(999));
        assertEquals("1,000", Common.commaSeperate(1000));
        assertEquals("1,234,567", Common.commaSeperate(1234567));
        assertEquals("-1,234", Common.commaSeperate(-1234));
        assertEquals("-1", Common.commaSeperate(-1));
        assertEquals("2,147,483,647", Common.commaSeperate(Integer.MAX_VALUE));
        // INT_MIN wraps to its unsigned magnitude before formatting
        assertEquals("-2,147,483,648", Common.commaSeperate(Integer.MIN_VALUE));
    }

    // ------------------------------------------------------------------
    // Search helpers
    // ------------------------------------------------------------------

    @Test
    void strFindNoCase() {
        assertEquals(2, Common.strFindNoCase("Hello", "ll"));
        assertEquals(2, Common.strFindNoCase("Hello", "LL"));
        assertEquals(0, Common.strFindNoCase("abc", ""));
        assertEquals(-1, Common.strFindNoCase("", ""));
        assertEquals(-1, Common.strFindNoCase("Hello", "z"));
        assertEquals(0, Common.strFindNoCase("a", "A"));
        assertEquals(-1, Common.strFindNoCase("Abc", "abcd"));
    }

    @Test
    void strPrefixNoCase() {
        assertTrue(Common.strPrefixNoCase("Hello", "he"));
        assertTrue(Common.strPrefixNoCase("Hello", "heLLo"));
        assertTrue(Common.strPrefixNoCase("Hello", "Hello"));
        assertFalse(Common.strPrefixNoCase("Hi", "Hello"));
        assertFalse(Common.strPrefixNoCase("", "x"));
        assertTrue(Common.strPrefixNoCase("", ""));
        // maxLength caps the comparison; reaching it without a mismatch returns true
        assertTrue(Common.strPrefixNoCase("Hello", "help", 3));
        assertFalse(Common.strPrefixNoCase("Hello", "help", 4));
    }

    // ------------------------------------------------------------------
    // XML / defines
    // ------------------------------------------------------------------

    @Test
    void evaluate() {
        Map<String, String> aDefines = new HashMap<>();
        aDefines.put("X", "1");
        aDefines.put("Y", "2");

        assertEquals("a1b", Common.evaluate("a%X%b", aDefines));
        assertEquals("1-2", Common.evaluate("%X%-%Y%", aDefines));
        assertEquals("ab", Common.evaluate("a%Z%b", aDefines));
        assertEquals("abc", Common.evaluate("abc", aDefines));
        assertEquals("50%", Common.evaluate("50%", aDefines));
        assertEquals("", Common.evaluate("%%", aDefines));
    }

    @Test
    void xmlDecode() {
        assertEquals("<a>", Common.xmlDecodeString("&lt;a&gt;"));
        assertEquals("&", Common.xmlDecodeString("&amp;"));
        assertEquals(" ", Common.xmlDecodeString("&nbsp;"));
        assertEquals("\n", Common.xmlDecodeString("&cr;"));
        assertEquals("\"q\"", Common.xmlDecodeString("&quot;q&quot;"));
        assertEquals("'s'", Common.xmlDecodeString("&apos;s&apos;"));
        assertEquals("a<b", Common.xmlDecodeString("a&lt;b"));
        assertEquals("&", Common.xmlDecodeString("&foo;"));
        assertEquals("&lt", Common.xmlDecodeString("&lt"));
        assertEquals("plain text", Common.xmlDecodeString("plain text"));
    }

    @Test
    void xmlEncode() {
        assertEquals("&lt;a&gt;", Common.xmlEncodeString("<a>"));
        assertEquals("&amp;", Common.xmlEncodeString("&"));
        assertEquals("&quot;q&quot;", Common.xmlEncodeString("\"q\""));
        assertEquals("&apos;s&apos;", Common.xmlEncodeString("'s'"));
        assertEquals("a&cr;b", Common.xmlEncodeString("a\nb"));
        assertEquals("a &nbsp;b", Common.xmlEncodeString("a  b"));
        assertEquals("a &nbsp;&nbsp;b", Common.xmlEncodeString("a   b"));
        assertEquals(" &nbsp;a", Common.xmlEncodeString("  a"));
        assertEquals("plain", Common.xmlEncodeString("plain"));
    }

    // ------------------------------------------------------------------
    // Path helpers
    // ------------------------------------------------------------------

    @Test
    void getFileName() {
        assertEquals("file.txt", Common.getFileName("path/to/file.txt"));
        assertEquals("file", Common.getFileName("path/to/file.txt", true));
        assertEquals("", Common.getFileName("a/b/"));
        assertEquals("file", Common.getFileName("file"));
        assertEquals("", Common.getFileName("/"));
        assertEquals("", Common.getFileName(""));
        assertEquals("", Common.getFileName(null));
        assertEquals("archive.tar", Common.getFileName("archive.tar.gz", true));
        assertEquals(".bashrc", Common.getFileName(".bashrc", true));
        assertEquals("c", Common.getFileName("a/b/c.txt", true));
        assertEquals(".", Common.getFileName("a/b/."));
        assertEquals(".", Common.getFileName("a/b/.", true));
        assertEquals("..", Common.getFileName(".."));
        assertEquals("..", Common.getFileName("..", true));
    }

    @Test
    void getFileDir() {
        assertEquals("path/to", Common.getFileDir("path/to/file.txt"));
        assertEquals("path/to/", Common.getFileDir("path/to/file.txt", true));
        assertEquals("", Common.getFileDir("file.txt"));
        assertEquals("", Common.getFileDir("file.txt", true));
        assertEquals("a/b", Common.getFileDir("a/b/"));
        assertEquals("a/b/", Common.getFileDir("a/b/", true));
        assertEquals("a/", Common.getFileDir("a//b"));
        assertEquals("", Common.getFileDir(""));
        assertEquals("/", Common.getFileDir("/a"));
        // upstream C++ parent_path("/a") is "/" and the trailing '/' is appended unconditionally
        assertEquals("//", Common.getFileDir("/a", true));
    }

    @Test
    void removeTrailingSlash() {
        assertEquals("a/b", Common.removeTrailingSlash("a/b/"));
        assertEquals("a/b", Common.removeTrailingSlash("a/b"));
        assertEquals("", Common.removeTrailingSlash(""));
        assertEquals("/", Common.removeTrailingSlash("/"));
        assertEquals("a/b", Common.removeTrailingSlash("a//b//"));
        assertEquals("a/b", Common.removeTrailingSlash("a/./b"));
        assertEquals("b", Common.removeTrailingSlash("a/../b"));
    }

    @Test
    void isPathRooted() {
        assertTrue(Common.isPathRooted("/a"));
        assertTrue(Common.isPathRooted("/"));
        assertFalse(Common.isPathRooted("a/b"));
        assertFalse(Common.isPathRooted(""));
    }

    @Test
    void getPathFrom() {
        assertEquals("/a/b/c", Common.getPathFrom("b/c", "/a"));
        assertEquals("/a/x", Common.getPathFrom("../x", "/a/b"));
        assertEquals("/abs", Common.getPathFrom("/abs", "/a"));
        assertEquals("x/b", Common.getPathFrom("a/../b", "x"));

        String aFullPath = Common.getFullPath("x");
        assertTrue(Common.isPathRooted(aFullPath));
        assertTrue(aFullPath.endsWith("/x"));
    }

    /**
     * Defect A of ../verification/VERIFICATION.md §59: on Windows the framework's path helpers used to hand
     * back native separators ({@code data\BrianneTod32Outline}), while every consumer of those
     * strings — {@code ImageLib}'s {@code '_'} companion probe ({@code rfind('/')}), the pak member
     * table, {@code GetFileDir(..., withSlash)}'s appended {@code '/'} — only understands
     * {@code '/'}. The whole string side of {@code Common} therefore speaks portable paths on every
     * host: a Windows-style input is understood as such, and no output ever carries {@code '\'}.
     *
     * <p>Every expectation below holds on macOS/Linux too, which is what keeps the Windows behaviour
     * under test on machines that cannot run Windows.
     */
    @Test
    void pathHelpersSpeakPortablePathsOnEveryHost() {
        // The font-descriptor chain of PvZ: data/BrianneTod32.txt names the layer image
        // "BrianneTod32Outline", which FontData resolves against the descriptor's directory
        // (FontData loadLayerSetImage -> Common.getPathFrom(..., Common.getFileDir(mSourceFile))).
        assertEquals("data", Common.getFileDir("data\\BrianneTod32.txt"));
        assertEquals("data/", Common.getFileDir("data\\BrianneTod32.txt", true));
        assertEquals("BrianneTod32.txt", Common.getFileName("data\\BrianneTod32.txt"));
        assertEquals("BrianneTod32", Common.getFileName("data\\BrianneTod32.txt", true));
        assertEquals("data/BrianneTod32Outline", Common.getPathFrom("BrianneTod32Outline", "data"));
        assertEquals("data/BrianneTod32Outline", Common.getPathFrom("BrianneTod32Outline", "data\\"));

        // A native resource root, as it arrives from ResourceLocator on Windows.
        assertEquals("C:/Games/PvZ/main.pak", Common.getPathFrom("main.pak", "C:\\Games\\PvZ"));

        // Mixed separators normalize the same way in both directions, so the conversion is stable.
        assertEquals("a/b/c", Common.getPathFrom("b\\c", "a/"));
        assertEquals("a/b", Common.getFileDir("a/b\\c"));

        assertFalse(Common.getCurDir().contains("\\"));
        assertFalse(Common.getFullPath("x").contains("\\"));
    }

    @Test
    void fileSystemHelpers(@TempDir Path theTempDir) {
        Path aDir = theTempDir.resolve("a/b");
        Common.mkDir(aDir.toString());
        assertTrue(Files.isDirectory(aDir));
        assertTrue(Common.fileExists(aDir.toString()));
        assertFalse(Common.fileExists(theTempDir.resolve("nope").toString()));

        assertEquals("b", Common.getFileName(aDir.toString()));
        assertEquals(theTempDir.resolve("a").toString(), Common.getFileDir(aDir.toString()));

        assertTrue(Common.deltree(theTempDir.resolve("a").toString()));
        assertFalse(Common.fileExists(aDir.toString()));
        assertFalse(Common.deltree(theTempDir.resolve("a").toString()));
    }

    @Test
    void appDataAndResourceFolders() {
        Common.setAppDataFolder("/data/app");
        assertEquals("/data/app", Common.getAppDataFolder());
        assertEquals("/data/app/save", Common.getAppDataPath("save"));

        Common.setResourceFolder("/res/main");
        assertEquals("/res/main", Common.getResourceFolder());
        assertEquals("/res/main/images", Common.getResourcePath("images"));
    }

    // ------------------------------------------------------------------
    // Formatting / comparison
    // ------------------------------------------------------------------

    @Test
    void strFormat() {
        assertEquals("a1b2", Common.strFormat("a{}b{}", 1, 2));
        assertEquals("x", Common.strFormat("{}", "x"));
        assertEquals("{x}", Common.strFormat("{{x}}"));
        assertEquals("a-b", Common.strFormat("{0}-{1}", "a", "b"));
        assertEquals("b-a", Common.strFormat("{1}-{0}", "a", "b"));
        // missing arguments and real nulls render as "null", like String.valueOf
        assertEquals("null", Common.strFormat("{}"));
        assertEquals("null", Common.strFormat("{}", (Object) null));
        assertEquals("plain", Common.strFormat("plain"));
    }

    @Test
    void stringLessNoCase() {
        assertEquals(0, Common.strCaseCompare("abc", "ABC"));
        assertTrue(Common.strCaseCompare("abc", "abd") < 0);
        assertTrue(Common.strCaseCompare("b", "a") > 0);
        assertTrue(Common.strCaseCompare("ab", "abc") < 0);

        TreeMap<String, String> aMap = new TreeMap<>(Common.STRING_LESS_NO_CASE);
        aMap.put("Banana", "b");
        aMap.put("apple", "a");
        assertEquals("apple", aMap.firstKey());
        assertEquals("Banana", aMap.lastKey());

        TreeMap<String, String> aSameKey = new TreeMap<>(Common.STRING_LESS_NO_CASE);
        aSameKey.put("APPLE", "1");
        aSameKey.put("apple", "2");
        assertEquals(1, aSameKey.size());
        assertEquals("2", aSameKey.get("Apple"));

        Comparator<String> aComparator = Common.STRING_LESS_NO_CASE;
        assertEquals(0, aComparator.compare("Hello", "HELLO"));
    }

    @Test
    void endianHelpers() {
        assertEquals(0x7FFFFFFFL, Common.SEXY_RAND_MAX);

        assertEquals((short) 0x2211, Common.byteSwap16((short) 0x1122));
        assertEquals(0x44332211, Common.byteSwap32(0x11223344));
        assertEquals(0x8877665544332211L, Common.byteSwap64(0x1122334455667788L));
        assertEquals(0x11223344, Common.byteSwap32(Common.byteSwap32(0x11223344)));

        // fromBE is always the byte-swapped fromLE value, regardless of the host
        int aValue = 0x11223344;
        assertEquals(Common.byteSwap32(Common.fromLE32(aValue)), Common.fromBE32(aValue));
        assertEquals(Common.fromLE32(aValue), Common.toLE32(aValue));
        assertEquals(Common.fromBE32(aValue), Common.toBE32(aValue));
        assertEquals(Common.byteSwap16(Common.fromLE16((short) 0x1122)), Common.fromBE16((short) 0x1122));
        assertEquals(Common.byteSwap64(Common.fromLE64(0x1122334455667788L)),
                Common.fromBE64(0x1122334455667788L));
    }

    // ------------------------------------------------------------------
    // Unicode classification / UTF-8 helpers
    // ------------------------------------------------------------------

    @Test
    void unicodeClassification() {
        assertTrue(Common.isUnicodeSpace(' '));
        assertTrue(Common.isUnicodeSpace('\t'));
        assertTrue(Common.isUnicodeSpace('\r'));
        assertTrue(Common.isUnicodeSpace(0x00A0));
        assertTrue(Common.isUnicodeSpace(0x3000));
        assertTrue(Common.isUnicodeSpace(0x2028));
        assertFalse(Common.isUnicodeSpace('a'));
        assertFalse(Common.isUnicodeSpace(0x200B));
        assertFalse(Common.isUnicodeSpace(0x2000 - 1));

        assertTrue(Common.isOpeningPunctuation(0x300A));
        assertTrue(Common.isOpeningPunctuation(0xFF08));
        assertTrue(Common.isOpeningPunctuation(0x201C));
        assertFalse(Common.isOpeningPunctuation(0x3009));
        assertFalse(Common.isOpeningPunctuation('a'));

        assertTrue(Common.isClosingPunctuation(0x3002));
        assertTrue(Common.isClosingPunctuation(0xFF01));
        assertTrue(Common.isClosingPunctuation(0x2019));
        assertFalse(Common.isClosingPunctuation(0x300A));

        assertTrue(Common.isAutoBreakChar(0x4E2D));
        assertTrue(Common.isAutoBreakChar(0x3042));
        assertTrue(Common.isAutoBreakChar(0x2019));
        assertTrue(Common.isAutoBreakChar(0x1F600));
        assertFalse(Common.isAutoBreakChar('a'));
        assertFalse(Common.isAutoBreakChar(0x2000));
    }

    @Test
    void utf8Helpers() {
        byte[] aAscii = {'A'};
        byte[] aTwo = {(byte) 0xC3, (byte) 0xA9};                     // U+00E9
        byte[] aThree = {(byte) 0xE2, (byte) 0x82, (byte) 0xAC};      // U+20AC
        byte[] aFour = {(byte) 0xF0, (byte) 0x9D, (byte) 0x84, (byte) 0x9E}; // U+1D11E

        int[] anOffset = {0};
        assertEquals('A', Common.utf8DecodeNext(aAscii, anOffset));
        assertEquals(1, anOffset[0]);

        anOffset[0] = 0;
        assertEquals(0x00E9, Common.utf8DecodeNext(aTwo, anOffset));
        assertEquals(2, anOffset[0]);

        anOffset[0] = 0;
        assertEquals(0x20AC, Common.utf8DecodeNext(aThree, anOffset));
        assertEquals(3, anOffset[0]);

        anOffset[0] = 0;
        assertEquals(0x1D11E, Common.utf8DecodeNext(aFour, anOffset));
        assertEquals(4, anOffset[0]);

        // invalid lead byte, truncated sequence and bad continuation all fail in place
        anOffset[0] = 0;
        assertEquals(-1, Common.utf8DecodeNext(new byte[]{(byte) 0x80}, anOffset));
        assertEquals(0, anOffset[0]);
        anOffset[0] = 0;
        assertEquals(-1, Common.utf8DecodeNext(new byte[]{(byte) 0xC3}, anOffset));
        assertEquals(0, anOffset[0]);
        anOffset[0] = 0;
        assertEquals(-1, Common.utf8DecodeNext(new byte[]{(byte) 0xC3, 'A'}, anOffset));
        assertEquals(0, anOffset[0]);
        anOffset[0] = 0;
        assertEquals(-1, Common.utf8DecodeNext(aAscii, new int[]{1}));
        assertEquals(-1, Common.utf8DecodeNext(new byte[0], new int[]{0}));

        byte[] aMixed = "a\u00e9\u20ac\uD834\uDD1E".getBytes(StandardCharsets.UTF_8);
        assertEquals(4, Common.utf8CodePointCount(aMixed));
        assertEquals(0, Common.utf8ByteOffsetForCodePoint(aMixed, 0));
        assertEquals(1, Common.utf8ByteOffsetForCodePoint(aMixed, 1));
        assertEquals(3, Common.utf8ByteOffsetForCodePoint(aMixed, 2));
        assertEquals(6, Common.utf8ByteOffsetForCodePoint(aMixed, 3));
        assertEquals(aMixed.length, Common.utf8ByteOffsetForCodePoint(aMixed, 99));
        assertEquals(1, Common.utf8NextBoundary(aMixed, 0));
        assertEquals(3, Common.utf8NextBoundary(aMixed, 1));
        assertEquals(aMixed.length, Common.utf8NextBoundary(aMixed, aMixed.length));
        assertEquals(6, Common.utf8PrevBoundary(aMixed, aMixed.length));
        assertEquals(3, Common.utf8PrevBoundary(aMixed, 6));
        assertEquals(0, Common.utf8PrevBoundary(aMixed, 0));
        assertEquals(0x00E9, Common.utf8CodePointAt(aMixed, 1));
        assertEquals(0, Common.utf8CodePointAt(aMixed, 2));

        // invalid bytes behave like the upstream byte-wise helpers
        byte[] anInvalid = {(byte) 0xC3, 'A'};
        assertEquals(1, Common.utf8NextBoundary(anInvalid, 0));
        assertEquals(0, Common.utf8CodePointCount(anInvalid));
        assertEquals(anInvalid.length, Common.utf8ByteOffsetForCodePoint(anInvalid, 1));
        assertEquals(0, Common.utf8CodePointAt(anInvalid, 0));
    }

    // ------------------------------------------------------------------
    // Block memory helpers
    // ------------------------------------------------------------------

    @Test
    void sMemRAndSMemW() {
        ByteBuffer aSrc = ByteBuffer.allocate(8);
        aSrc.put(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        aSrc.flip();

        ByteBuffer aDst = ByteBuffer.allocate(8);
        Common.sMemR(aSrc, aDst, 4);
        assertEquals(4, aSrc.position());
        assertEquals(4, aDst.position());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, java.util.Arrays.copyOf(aDst.array(), 4));

        ByteBuffer aDst2 = ByteBuffer.allocate(8);
        Common.sMemW(aDst2, aSrc, 4);
        assertEquals(8, aSrc.position());
        assertArrayEquals(new byte[]{5, 6, 7, 8}, java.util.Arrays.copyOf(aDst2.array(), 4));
    }

    @Test
    void sMemRStrAndSMemWStr() {
        ByteBuffer aBuffer = ByteBuffer.allocate(64);
        Common.sMemWStr(aBuffer, "h\u00e9llo");
        aBuffer.flip();
        assertEquals("h\u00e9llo", Common.sMemRStr(aBuffer));
        assertEquals(0, aBuffer.remaining());
    }

    @Test
    void loggingDoesNotThrow() {
        Common.dispatchLogLn(Common.SexyLogPriority.Info, "");
        Common.dispatchLogLn(Common.SexyLogPriority.Info, "CommonTest message");
        Common.logInfoLn("value={}", 3);
        Common.logErrorLn("err {}", "x");
        assertNotNull(Common.SexyLogPriority.Info);
        assertEquals(Common.SexyLogPriority.Info, Common.SexyLogPriority.valueOf("Info"));
    }

    @Test
    void randFamily() {
        // reseeding makes the sequence reproducible
        Common.sRand(1234);
        int aFirst = Common.rand();
        int aSecond = Common.rand();
        Common.sRand(1234);
        assertEquals(aFirst, Common.rand());
        assertEquals(aSecond, Common.rand());

        Common.sRand(4357);
        int aValue = Common.rand();
        assertTrue(aValue >= 0 && aValue <= Common.SEXY_RAND_MAX);

        for (int i = 0; i < 100; i++) {
            int aBounded = Common.rand(10);
            assertTrue(aBounded >= 0 && aBounded < 10);
            float aFloat = Common.rand(1.0f);
            assertTrue(aFloat >= 0.0f && aFloat < 1.0f);
        }
    }

    @Test
    void miscConstants() {
        assertFalse(Common.gDebug);
        assertFalse(Common.allowAllAccess("anything"));
    }
}
