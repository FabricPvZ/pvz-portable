package cn.enaium.fabricpvz.portable.sexy.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;

/**
 * Behavior tests for {@link DescParser}, asserting the tokenizer/structure rules of
 * src/SexyAppFramework/misc/DescParser.cpp.
 */
class DescParserTest {

    /** Records every command, like {@code FontData} does upstream. */
    private static final class RecordingDescParser extends DescParser {

        final List<String> mCommands = new ArrayList<>();
        final List<ListDataElement> mParams = new ArrayList<>();

        @Override
        public boolean handleCommand(ListDataElement theParams) {
            mCommands.add(((SingleDataElement) theParams.mElementVector.get(0)).mString);
            mParams.add(theParams);
            return true;
        }

        List<Object> plainParams(int theIndex) {
            return toPlain(mParams.get(theIndex));
        }
    }

    private static List<Object> toPlain(ListDataElement theList) {
        List<Object> aResult = new ArrayList<>();
        for (DataElement anElement : theList.mElementVector) {
            if (anElement.mIsList) {
                aResult.add(toPlain((ListDataElement) anElement));
            } else {
                aResult.add(((SingleDataElement) anElement).mString);
            }
        }
        return aResult;
    }

    private static ListDataElement list(String theText) {
        ListDataElement aList = new ListDataElement();
        if (!new RecordingDescParser().parseToList(theText, aList, false, null)) {
            throw new IllegalArgumentException("unparsable test input: " + theText);
        }
        return aList;
    }

    private static List<String> stringValues(DescParser theParser, String theText) {
        List<String> aResult = new ArrayList<>();
        assertTrue(theParser.dataToStringVector(new SingleDataElement(theText), aResult),
                theParser.mError);
        return aResult;
    }

    // ------------------------------------------------------------------
    // Tokenization
    // ------------------------------------------------------------------

    @Test
    void separatesOnSpacesTabsNewlinesAndCommas() {
        ListDataElement aList = list("a b\tc\nd,e");

        assertEquals(List.of("a", "b", "c", "d", "e"), toPlain(aList));
    }

    @Test
    void quotedStringsStayOneTokenAndKeepTheirQuotes() {
        ListDataElement aList = list("\"hello world\" 'a b' plain");

        assertEquals(List.of("\"hello world\"", "'a b'", "plain"), toPlain(aList));
    }

    @Test
    void quotedStringsMayContainCommas() {
        ListDataElement aList = list("\"a,b\" c");

        assertEquals(List.of("\"a,b\"", "c"), toPlain(aList));
    }

    @Test
    void backslashEscapesTheNextCharacter() {
        // The backslash itself is dropped, the escaped character is taken literally (DescParser.cpp:340).
        ListDataElement aList = list("a\\,b c");

        assertEquals(List.of("a,b", "c"), toPlain(aList));
    }

    @Test
    void nestedParenthesizedLists() {
        ListDataElement aList = list("(a (b c) d)");

        assertEquals(1, aList.mElementVector.size());
        assertEquals(List.of("a", List.of("b", "c"), "d"), toPlain((ListDataElement) aList.mElementVector.get(0)));
    }

    @Test
    void emptyInputYieldsNoElements() {
        assertEquals(List.of(), toPlain(list("   ")));
    }

    @Test
    void singleElementListHasNoNesting() {
        assertEquals(List.of("1"), toPlain(list("1")));
    }

    // ------------------------------------------------------------------
    // Tokenization errors
    // ------------------------------------------------------------------

    @Test
    void unterminatedListIsAnError() {
        RecordingDescParser aParser = new RecordingDescParser();
        ListDataElement aList = new ListDataElement();

        assertFalse(aParser.parseToList("(a b", aList, true, null));
        assertEquals("Unterminated List", aParser.mError);
    }

    @Test
    void unexpectedListEndIsAnError() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertFalse(aParser.parseToList("a)", new ListDataElement(), false, null));
        assertEquals("Unexpected List End", aParser.mError);
    }

    @Test
    void unexpectedListStartIsAnError() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertFalse(aParser.parseToList("(a(b))", new ListDataElement(), true, null));
        assertEquals("Unexpected List Start", aParser.mError);
    }

    @Test
    void unterminatedQuotesAreErrors() {
        RecordingDescParser aParser = new RecordingDescParser();
        assertFalse(aParser.parseToList("\"abc", new ListDataElement(), false, null));
        assertEquals("Unterminated Double Quotes", aParser.mError);

        RecordingDescParser aSecondParser = new RecordingDescParser();
        assertFalse(aSecondParser.parseToList("'abc", new ListDataElement(), false, null));
        assertEquals("Unterminated Single Quotes", aSecondParser.mError);
    }

    @Test
    void missingCommandIsAnError() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertFalse(aParser.parseDescriptorLine("(a b)"));
        assertEquals("Missing Command", aParser.mError);
    }

    // ------------------------------------------------------------------
    // Conversion helpers
    // ------------------------------------------------------------------

    @Test
    void unquoteRemovesOuterQuotesAndDoubles() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertEquals("abc", aParser.unquote("\"abc\""));
        assertEquals("abc", aParser.unquote("'abc'"));
        assertEquals("a\"b", aParser.unquote("\"a\"\"b\""));
        assertEquals("plain", aParser.unquote("plain"));
        assertEquals("", aParser.unquote(""));
    }

    @Test
    void isImmediateMatchesLeadingCharacters() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertTrue(aParser.isImmediate("5"));
        assertTrue(aParser.isImmediate("-2"));
        assertTrue(aParser.isImmediate("+3"));
        assertTrue(aParser.isImmediate("0x1F"));
        assertTrue(aParser.isImmediate("'q'"));
        assertTrue(aParser.isImmediate("\"q\""));
        assertFalse(aParser.isImmediate("abc"));
        assertFalse(aParser.isImmediate(""));
    }

    @Test
    void dataElementToStringRendersNestedLists() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertEquals("(1, (2, 3))", aParser.dataElementToString(list("1 (2 3)")));
        assertEquals("7", aParser.dataElementToString(new SingleDataElement("7")));
    }

    @Test
    void dataToStringUnquotesAndFailsOnLists() {
        RecordingDescParser aParser = new RecordingDescParser();
        String[] aString = {""};

        assertTrue(aParser.dataToString(new SingleDataElement("\"x\""), aString));
        assertEquals("x", aString[0]);

        assertFalse(aParser.dataToString(list("1"), aString));
        assertEquals("", aString[0]);
    }

    @Test
    void dataToIntAcceptsNegativeHexAndOctal() {
        RecordingDescParser aParser = new RecordingDescParser();
        int[] anInt = {0};

        assertTrue(aParser.dataToInt(new SingleDataElement("-2"), anInt));
        assertEquals(-2, anInt[0]);

        assertTrue(aParser.dataToInt(new SingleDataElement("0x1F"), anInt));
        assertEquals(31, anInt[0]);

        assertTrue(aParser.dataToInt(new SingleDataElement("0755"), anInt));
        assertEquals(493, anInt[0]);

        assertTrue(aParser.dataToInt(new SingleDataElement("\"17\""), anInt));
        assertEquals(17, anInt[0]);

        assertFalse(aParser.dataToInt(new SingleDataElement("1.5"), anInt));
        assertEquals(0, anInt[0]);
    }

    @Test
    void dataToIntVectorFollowsUpstreamNumberRules() {
        RecordingDescParser aParser = new RecordingDescParser();
        List<Integer> anInts = new ArrayList<>();

        assertTrue(aParser.dataToIntVector(list("-2 0x1F 0755"), anInts), aParser.mError);
        assertEquals(List.of(-2, 31, 493), anInts);

        // Upstream clears only once, so a failing element leaves the already-converted prefix behind.
        assertFalse(aParser.dataToIntVector(list("1 \"1.5\""), anInts));
        assertEquals(List.of(1), anInts);
    }

    @Test
    void dataToDoubleVectorParsesFloats() {
        RecordingDescParser aParser = new RecordingDescParser();
        List<Double> aDoubles = new ArrayList<>();

        assertTrue(aParser.dataToDoubleVector(list("1.5 0.25 200.0"), aDoubles), aParser.mError);
        assertEquals(List.of(1.5, 0.25, 200.0), aDoubles);
    }

    // ------------------------------------------------------------------
    // Defines (dereferencing)
    // ------------------------------------------------------------------

    @Test
    void dereferenceIsCaseInsensitive() {
        RecordingDescParser aParser = new RecordingDescParser();
        SingleDataElement aDefine = new SingleDataElement("\"value\"");
        aParser.mDefineMap.put("MYDEF", aDefine);

        assertSame(aDefine, aParser.dereference("mydef"));
        assertSame(aDefine, aParser.dereference("MyDef"));
        assertNull(aParser.dereference("other"));
    }

    @Test
    void getValuesUnquotesImmediateAndDereferences() {
        RecordingDescParser aParser = new RecordingDescParser();
        aParser.mDefineMap.put("DEF", new SingleDataElement("\"deref\""));

        ListDataElement aValues = new ListDataElement();

        assertTrue(aParser.getValues(list("DEF 5 \"quoted\""), aValues), aParser.mError);
        // A dereferenced define is copied verbatim (quotes included); only literal tokens are
        // unquoted here.
        assertEquals(List.of("\"deref\"", "5", "quoted"), toPlain(aValues));
    }

    @Test
    void getValuesReportsUnknownNames() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertFalse(aParser.getValues(list("missing"), new ListDataElement()));
        assertEquals("Unable to Dereference \"missing\"", aParser.mError);
    }

    @Test
    void dataToStringVectorDereferencesLists() {
        RecordingDescParser aParser = new RecordingDescParser();
        aParser.mDefineMap.put("RANGE", list("1 2 3"));

        assertEquals(List.of("1", "2", "3"), stringValues(aParser, "RANGE"));

        List<String> aVector = new ArrayList<>();
        assertTrue(aParser.dataToStringVector(list("\"a\" \"b\""), aVector));
        assertEquals(List.of("a", "b"), aVector);

        List<String> aNested = new ArrayList<>();
        assertFalse(aParser.dataToStringVector(list("a (b)"), aNested));
        assertEquals(List.of(), aNested);
    }

    @Test
    void dataToStringVectorReportsMissingDefine() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertFalse(aParser.dataToStringVector(new SingleDataElement("nope"), new ArrayList<>()));
        assertEquals("Unable to Dereference \"nope\"", aParser.mError);
    }

    @Test
    void dataToListDereferencesAndDeepCopies() {
        RecordingDescParser aParser = new RecordingDescParser();
        aParser.mDefineMap.put("GRID", list("1 (2 3)"));

        ListDataElement aValues = new ListDataElement();
        assertTrue(aParser.dataToList(new SingleDataElement("grid"), aValues));

        assertEquals(List.of("1", List.of("2", "3")), toPlain(aValues));

        // Deep copy: mutating the define afterwards must not change the extracted list.
        aParser.mDefineMap.put("GRID", list("9"));
        assertEquals(List.of("1", List.of("2", "3")), toPlain(aValues));

        assertFalse(aParser.dataToList(new SingleDataElement("1"), aValues));
    }

    // ------------------------------------------------------------------
    // Command dispatch
    // ------------------------------------------------------------------

    @Test
    void parseDescriptorLineDispatchesTheCommand() {
        RecordingDescParser aParser = new RecordingDescParser();

        assertTrue(aParser.parseDescriptorLine("Define FOO 5"));
        assertEquals(List.of("Define"), aParser.mCommands);
        assertEquals(List.of("Define", "FOO", "5"), aParser.plainParams(0));

        assertTrue(aParser.parseDescriptorLine("   "));
        assertEquals(List.of("Define"), aParser.mCommands);
    }

    @Test
    void handleCommandFailureAbortsTheLine() {
        DescParser aParser = new DescParser() {
            @Override
            public boolean handleCommand(ListDataElement theParams) {
                return false;
            }
        };

        assertFalse(aParser.parseDescriptorLine("Cmd"));
    }

    // ------------------------------------------------------------------
    // LoadDescriptor
    // ------------------------------------------------------------------

    @Test
    void loadDescriptorSplitsOnSemicolonsAndSkipsComments(@TempDir Path theDir) throws Exception {
        new SexyAppBase();

        Path aFile = theDir.resolve("test.desc");
        Files.writeString(aFile, "# a comment\nDefine FOO 5 ;\nCmd 'a b' 2 ;\n", StandardCharsets.UTF_8);

        RecordingDescParser aParser = new RecordingDescParser();

        assertTrue(aParser.loadDescriptor(aFile.toString()), aParser.mError);
        assertEquals(List.of("Define", "Cmd"), aParser.mCommands);
        assertEquals(List.of("Define", "FOO", "5"), aParser.plainParams(0));
        // parseToList keeps the quotes; unquoting is the command's business.
        assertEquals(List.of("Cmd", "'a b'", "2"), aParser.plainParams(1));
        assertEquals("", aParser.mError);
        assertEquals(0, aParser.mCurrentLineNum);
        assertEquals("", aParser.mCurrentLine);
    }

    @Test
    void loadDescriptorWithoutSemicolonsReadsOneStatement(@TempDir Path theDir) throws Exception {
        new SexyAppBase();

        Path aFile = theDir.resolve("one.desc");
        Files.writeString(aFile, "A 1\nB 2\n", StandardCharsets.UTF_8);

        RecordingDescParser aParser = new RecordingDescParser();

        assertTrue(aParser.loadDescriptor(aFile.toString()), aParser.mError);
        assertEquals(List.of("A"), aParser.mCommands);
        assertEquals(List.of("A", "1", "B", "2"), aParser.plainParams(0));
    }

    @Test
    void noIndentModeStartsANewStatementPerLine(@TempDir Path theDir) throws Exception {
        new SexyAppBase();

        Path aFile = theDir.resolve("indent.desc");
        Files.writeString(aFile, "A 1\nB 2\n", StandardCharsets.UTF_8);

        RecordingDescParser aParser = new RecordingDescParser();
        aParser.mCmdSep |= DescParser.CMDSEP_NO_INDENT;

        assertTrue(aParser.loadDescriptor(aFile.toString()), aParser.mError);
        assertEquals(List.of("A", "B"), aParser.mCommands);
        assertEquals(List.of("A", "1"), aParser.plainParams(0));
        assertEquals(List.of("B", "2"), aParser.plainParams(1));
    }

    @Test
    void indentedContinuationLinesStayInTheStatement(@TempDir Path theDir) throws Exception {
        new SexyAppBase();

        Path aFile = theDir.resolve("continuation.desc");
        Files.writeString(aFile, "A 1\n  2\nB 3\n", StandardCharsets.UTF_8);

        RecordingDescParser aParser = new RecordingDescParser();
        aParser.mCmdSep |= DescParser.CMDSEP_NO_INDENT;

        assertTrue(aParser.loadDescriptor(aFile.toString()), aParser.mError);
        assertEquals(List.of("A", "B"), aParser.mCommands);
        assertEquals(List.of("A", "1", "2"), aParser.plainParams(0));
        assertEquals(List.of("B", "3"), aParser.plainParams(1));
    }

    @Test
    void loadDescriptorPropagatesParseErrors(@TempDir Path theDir) throws Exception {
        new SexyAppBase();

        Path aFile = theDir.resolve("broken.desc");
        Files.writeString(aFile, "Cmd \"unterminated ;\n", StandardCharsets.UTF_8);

        RecordingDescParser aParser = new RecordingDescParser();

        assertFalse(aParser.loadDescriptor(aFile.toString()));
        assertEquals("Unterminated Double Quotes", aParser.mError);
        assertEquals(0, aParser.mCommands.size());
    }

    @Test
    void loadDescriptorMissingFileIsReported() {
        new SexyAppBase();

        RecordingDescParser aParser = new RecordingDescParser();

        assertFalse(aParser.loadDescriptor("no/such/descriptor.desc"));
        assertEquals("Failed to open file", aParser.mError);
        assertEquals(0, aParser.mCurrentLineNum);
    }

    @Test
    void recordingParserIsUsableForLineParsing() {
        RecordingDescParser aParser = new RecordingDescParser();
        assertNotNull(aParser.mDefineMap);
        assertTrue(aParser.mDefineMap.isEmpty());
        assertEquals(DescParser.CMDSEP_SEMICOLON, aParser.mCmdSep);
    }
}
