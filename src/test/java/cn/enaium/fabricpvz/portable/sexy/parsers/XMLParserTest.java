package cn.enaium.fabricpvz.portable.sexy.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavior tests for {@link XMLParser}, asserting the acceptance/error rules of
 * src/SexyAppFramework/misc/XMLParser.cpp (line numbers refer to that file).
 */
class XMLParserTest {

    private static List<XMLElement> readAll(XMLParser theParser) {
        List<XMLElement> anElements = new ArrayList<>();
        for (; ; ) {
            XMLElement anElement = new XMLElement();
            if (!theParser.nextElement(anElement)) {
                return anElements;
            }
            anElements.add(anElement);
        }
    }

    private static XMLParser fromString(String theXml) {
        XMLParser aParser = new XMLParser();
        aParser.setStringSource(theXml);
        return aParser;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Test
    void stringSourceIsParsedInOrder() {
        // Regression: the push-back stack stores the source in reverse, so popping it must yield
        // the text front-to-back (upstream XMLParser.cpp:314-331).
        XMLParser aParser = fromString("<count>42</count>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(3, anElements.size());
        assertEquals(XMLElement.TYPE_START, anElements.get(0).mType);
        assertEquals("count", anElements.get(0).mValue);
        assertEquals(XMLElement.TYPE_ELEMENT, anElements.get(1).mType);
        assertEquals("42", anElements.get(1).mValue);
        assertEquals(XMLElement.TYPE_END, anElements.get(2).mType);
        assertFalse(aParser.hasFailed());
    }

    @Test
    void nestedElementsTrackSections() {
        XMLParser aParser = fromString("<root><child a=\"1\">text</child></root>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(5, anElements.size());

        assertEquals(XMLElement.TYPE_START, anElements.get(0).mType);
        assertEquals("root", anElements.get(0).mValue);
        assertEquals("", anElements.get(0).mSection);

        assertEquals(XMLElement.TYPE_START, anElements.get(1).mType);
        assertEquals("child", anElements.get(1).mValue);
        assertEquals("root", anElements.get(1).mSection);
        assertEquals("1", anElements.get(1).mAttributes.get("a"));

        assertEquals(XMLElement.TYPE_ELEMENT, anElements.get(2).mType);
        assertEquals("text", anElements.get(2).mValue);
        assertEquals("root/child", anElements.get(2).mSection);

        assertEquals(XMLElement.TYPE_END, anElements.get(3).mType);
        assertEquals("child", anElements.get(3).mValue);
        assertEquals("root/child", anElements.get(3).mSection);

        assertEquals(XMLElement.TYPE_END, anElements.get(4).mType);
        assertEquals("root", anElements.get(4).mValue);
        assertEquals("root", anElements.get(4).mSection);

        assertFalse(aParser.hasFailed());
        assertFalse(aParser.nextElement(new XMLElement()));
        assertFalse(aParser.hasFailed());
    }

    @Test
    void emptyElementIsFakedIntoStartAndEnd() {
        XMLParser aParser = fromString("<img path=\"x.png\" id=\"A\"/>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(2, anElements.size());
        assertEquals(XMLElement.TYPE_START, anElements.get(0).mType);
        assertEquals("img", anElements.get(0).mValue);
        assertEquals("x.png", anElements.get(0).mAttributes.get("path"));
        assertEquals("A", anElements.get(0).mAttributes.get("id"));
        // AddAttribute re-appends the iterator for the pending value AND for the '/'-stripped
        // rewrite (XMLParser.cpp:105-117), so "id" is listed twice.
        assertEquals(List.of("path", "id", "id"), anElements.get(0).mAttributeIteratorList);
        assertEquals(XMLElement.TYPE_END, anElements.get(1).mType);
        assertEquals("img", anElements.get(1).mValue);
    }

    @Test
    void spaceBeforeSlashIsIgnored() {
        XMLParser aParser = fromString("<img path=\"x.png\" />");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(2, anElements.size());
        assertEquals("img", anElements.get(0).mValue);
        assertEquals("x.png", anElements.get(0).mAttributes.get("path"));
        assertEquals(XMLElement.TYPE_END, anElements.get(1).mType);
    }

    @Test
    void duplicateAttributeKeepsLastValue() {
        XMLParser aParser = fromString("<a id=\"1\" id=\"2\"/>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals("2", anElements.get(0).mAttributes.get("id"));
        // Upstream re-appends the attribute iterator for the pending value, the '/'-stripped
        // rewrite and the slash-less value (XMLParser.cpp:105-117).
        assertEquals(3, anElements.get(0).mAttributeIteratorList.size());
    }

    @Test
    void doubleQuotedAttributeKeepsSpaces() {
        XMLParser aParser = fromString("<a path=\"a b/c.png\"/>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals("a b/c.png", anElements.get(0).mAttributes.get("path"));
    }

    @Test
    void textWhitespaceIsCollapsedAndNeverEmitsBlankElements() {
        XMLParser aParser = fromString("<a>  x   y  </a>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(3, anElements.size());
        assertEquals("x y", anElements.get(1).mValue);

        XMLParser aBlankParser = fromString("<a>   \n\t  </a>");
        List<XMLElement> aBlankElements = readAll(aBlankParser);
        assertEquals(2, aBlankElements.size());
        assertEquals(XMLElement.TYPE_START, aBlankElements.get(0).mType);
        assertEquals(XMLElement.TYPE_END, aBlankElements.get(1).mType);
    }

    // ------------------------------------------------------------------
    // Comments / instructions
    // ------------------------------------------------------------------

    @Test
    void commentsAreSkippedUnlessAllowed() {
        XMLParser aParser = fromString("<a><!-- note --></a>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(2, anElements.size());
        assertEquals(XMLElement.TYPE_START, anElements.get(0).mType);
        assertEquals(XMLElement.TYPE_END, anElements.get(1).mType);

        XMLParser anAllowedParser = fromString("<a><!-- note --></a>");
        anAllowedParser.allowComments(true);

        List<XMLElement> anAllowedElements = readAll(anAllowedParser);

        assertEquals(3, anAllowedElements.size());
        assertEquals(XMLElement.TYPE_COMMENT, anAllowedElements.get(1).mType);
        assertEquals("!--", anAllowedElements.get(1).mValue);
        // The comment body is copied verbatim up to the closing "-->", but the space that turns
        // "!--" into a comment is consumed as whitespace, so it never reaches mInstruction
        // (XMLParser.cpp:591-599).
        assertEquals("note ", anAllowedElements.get(1).mInstruction);
        assertEquals(XMLElement.TYPE_END, anAllowedElements.get(2).mType);
    }

    @Test
    void instructionElementSplitsTargetAndBody() {
        XMLParser aParser = fromString("<?xml version=\"1.0\"?><a/>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(3, anElements.size());
        assertEquals(XMLElement.TYPE_INSTRUCTION, anElements.get(0).mType);
        assertEquals("xml", anElements.get(0).mValue);
        assertEquals(" version=\"1.0\"", anElements.get(0).mInstruction);
        assertEquals("a", anElements.get(1).mValue);
        assertEquals(XMLElement.TYPE_END, anElements.get(2).mType);
    }

    // ------------------------------------------------------------------
    // Escapes / entities
    // ------------------------------------------------------------------

    @Test
    void xmlEntitiesAreDecoded() {
        XMLParser aParser = fromString(
                "<a>x&lt;y&amp;z&gt;q&quot;r&apos;s&nbsp;t&cr;u</a>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals("x<y&z>q\"r's t\nu", anElements.get(1).mValue);
    }

    @Test
    void unknownEntityDropsTheEntityText() {
        XMLParser aParser = fromString("<a>&foo;bar</a>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals("&bar", anElements.get(1).mValue);
    }

    @Test
    void entityInsideAttributeIsDecoded() {
        XMLParser aParser = fromString("<a path=\"a&amp;b.png\"/>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals("a&b.png", anElements.get(0).mAttributes.get("path"));
    }

    @Test
    void cdataIsNotSupportedAndIsReadAsATag() {
        // Upstream has no CDATA handling: "<![CDATA[x]]>" is scanned as a start tag named
        // "![CDATA[x]]" (the '>' at the end closes it).
        XMLParser aParser = fromString("<![CDATA[x]]>");

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(1, anElements.size());
        assertEquals(XMLElement.TYPE_START, anElements.get(0).mType);
        assertEquals("![CDATA[x]]", anElements.get(0).mValue);
    }

    // ------------------------------------------------------------------
    // Malformed input
    // ------------------------------------------------------------------

    @Test
    void unexpectedStartTagCharacter() {
        XMLParser aParser = fromString("<a<b>");

        assertFalse(aParser.nextElement(new XMLElement()));
        assertTrue(aParser.hasFailed());
        assertEquals("Unexpected '<'", aParser.getErrorText());
    }

    @Test
    void unexpectedEndTagCharacter() {
        XMLParser aParser = fromString("x>");

        assertFalse(aParser.nextElement(new XMLElement()));
        assertEquals("Unexpected '>'", aParser.getErrorText());
    }

    @Test
    void unexpectedEndWithoutStart() {
        XMLParser aParser = fromString("</a>");

        assertFalse(aParser.nextElement(new XMLElement()));
        assertEquals("Unexpected End", aParser.getErrorText());
    }

    @Test
    void mismatchedEndTagReportsBothNames() {
        XMLParser aParser = fromString("<a></b>");

        assertTrue(aParser.nextElement(new XMLElement()));
        assertFalse(aParser.nextElement(new XMLElement()));
        assertEquals("End 'b' Doesn't Match Start 'a'", aParser.getErrorText());
    }

    @Test
    void unexpectedEndOfFileWhileInsideTag() {
        XMLParser aParser = fromString("<a");

        assertFalse(aParser.nextElement(new XMLElement()));
        assertTrue(aParser.hasFailed());
        assertEquals("Unexpected End of File", aParser.getErrorText());
    }

    @Test
    void eofAfterACompleteElementIsNotAnError() {
        XMLParser aParser = fromString("<a/>");

        // "<a/>" is an empty element: "/>" fakes BOTH a start and an end segment
        // (XMLParser.cpp:505-545), so two elements come out of the single tag.
        assertTrue(aParser.nextElement(new XMLElement()));
        assertTrue(aParser.nextElement(new XMLElement()));
        assertFalse(aParser.nextElement(new XMLElement()));
        assertFalse(aParser.hasFailed());
        assertTrue(aParser.endOfFile());
    }

    @Test
    void illegalControlCharacterFails() {
        XMLParser aParser = fromString("<a>\u0001x</a>");

        assertTrue(aParser.nextElement(new XMLElement()));
        assertFalse(aParser.nextElement(new XMLElement()));
        assertEquals("Illegal Character", aParser.getErrorText());
    }

    // ------------------------------------------------------------------
    // Files, BOM, line numbers
    // ------------------------------------------------------------------

    @Test
    void lineNumbersCountNewlines() {
        XMLParser aParser = fromString("<a>\n<b>\n</c>");

        assertTrue(aParser.nextElement(new XMLElement()));
        assertTrue(aParser.nextElement(new XMLElement()));
        assertEquals(2, aParser.getCurrentLineNum());
        assertFalse(aParser.nextElement(new XMLElement()));
        assertEquals(3, aParser.getCurrentLineNum());
        assertEquals("End 'c' Doesn't Match Start 'b'", aParser.getErrorText());
    }

    @Test
    void missingFileFailsWithoutFileNameOrLine(@TempDir Path theDir) {
        XMLParser aParser = new XMLParser();

        assertFalse(aParser.openFile(theDir.resolve("missing.xml").toString()));
        assertTrue(aParser.hasFailed());
        assertEquals("Unable to open file " + theDir.resolve("missing.xml"), aParser.getErrorText());
        assertEquals(0, aParser.getCurrentLineNum());
        assertEquals("", aParser.getFileName());
    }

    @Test
    void utf8BomIsStrippedWhenReadingAFile(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("bom.xml");
        byte[] aBytes = ("\uFEFF<a id=\"K\">caf\u00e9 \u4e2d\u6587</a>")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(aFile, aBytes);

        XMLParser aParser = new XMLParser();
        assertTrue(aParser.openFile(aFile.toString()));
        assertEquals(aFile.toString(), aParser.getFileName());
        assertEquals(1, aParser.getCurrentLineNum());

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(3, anElements.size());
        assertEquals("a", anElements.get(0).mValue);
        assertEquals("K", anElements.get(0).mAttributes.get("id"));
        assertEquals("caf\u00e9 \u4e2d\u6587", anElements.get(1).mValue);
        assertFalse(aParser.hasFailed());
        aParser.close();
    }

    @Test
    void bomlessUtf8FileStillDecodesNonAsciiText(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("noBom.xml");
        Files.write(aFile, "<a>caf\u00e9 \u4e2d\u6587</a>".getBytes(StandardCharsets.UTF_8));

        XMLParser aParser = new XMLParser();
        assertTrue(aParser.openFile(aFile.toString()), aParser.getErrorText());

        List<XMLElement> anElements = readAll(aParser);

        assertEquals("caf\u00e9 \u4e2d\u6587", anElements.get(1).mValue);
        assertFalse(aParser.hasFailed());
        aParser.close();
    }

    @Test
    void crlfFileKeepsLineNumbers(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("crlf.xml");
        Files.writeString(aFile, "<a>\r\n</a>\r\n", StandardCharsets.US_ASCII);

        XMLParser aParser = new XMLParser();
        assertTrue(aParser.openFile(aFile.toString()));

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(2, anElements.size());
        assertFalse(aParser.hasFailed());
        aParser.close();
    }

    @Test
    void utf16FileWithBomIsDecoded(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("utf16.xml");
        byte[] aBody = "<a>h\u00e9</a>".getBytes(StandardCharsets.UTF_16LE);
        byte[] aBytes = new byte[aBody.length + 2];
        aBytes[0] = (byte) 0xFF;
        aBytes[1] = (byte) 0xFE;
        System.arraycopy(aBody, 0, aBytes, 2, aBody.length);
        Files.write(aFile, aBytes);

        XMLParser aParser = new XMLParser();
        assertTrue(aParser.openFile(aFile.toString()));

        List<XMLElement> anElements = readAll(aParser);

        assertEquals(3, anElements.size());
        assertEquals("a", anElements.get(0).mValue);
        assertEquals("h\u00e9", anElements.get(1).mValue);
        // The UTF-16 getters set *error = true before the (2-byte) read and only clear it after a
        // character is produced, so reaching EOF is reported as a failure (XMLParser.cpp:154-160,
        // 189-199): hasFailed() is set and nextElement() returns false.
        assertTrue(aParser.hasFailed());
        assertEquals("Illegal Character", aParser.getErrorText());
        aParser.close();
    }

    @Test
    void forcedEncodingOverridesDetection(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("forced.xml");
        Files.writeString(aFile, "<a>plain</a>", StandardCharsets.US_ASCII);

        XMLParser aParser = new XMLParser();
        aParser.setEncodingType(XMLParser.XMLEncodingType.ASCII);
        assertTrue(aParser.openFile(aFile.toString()));

        List<XMLElement> anElements = readAll(aParser);

        assertEquals("plain", anElements.get(1).mValue);
        assertFalse(aParser.hasFailed());
        aParser.close();
    }
}
