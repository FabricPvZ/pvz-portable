package cn.enaium.fabricpvz.portable.sexy.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.sexy.Buffer;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;

/**
 * Behavior tests for {@link PropertiesParser}, asserting the acceptance rules of
 * src/SexyAppFramework/misc/PropertiesParser.cpp.
 *
 * <p>The format really is XML ({@code <Properties><String id="…">…</String>…}), not {@code k=v}
 * lines: upstream routes everything through {@link XMLParser} and has no key/value branch.
 */
class PropertiesParserTest {

    private static Buffer buffer(String theXml) {
        Buffer aBuffer = new Buffer();
        aBuffer.setData(theXml.getBytes(StandardCharsets.UTF_8));
        return aBuffer;
    }

    private static Buffer buffer(byte[] theBytes) {
        Buffer aBuffer = new Buffer();
        aBuffer.setData(theBytes);
        return aBuffer;
    }

    // ------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------

    @Test
    void storesEveryPropertyType() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        String anXml = """
                <?xml version="1.0"?>
                <Properties>
                  <String id="DIALOG_BUTTON_OK">OK</String>
                  <Boolean id="FLAG">yes</Boolean>
                  <Integer id="NUM">0x2A</Integer>
                  <Double id="F">1.5</Double>
                  <StringArray id="LIST"><String>one</String><String>two</String></StringArray>
                </Properties>
                """;

        assertTrue(aParser.parsePropertiesBuffer(buffer(anXml)), aParser.getErrorText());

        assertEquals("OK", aApp.mStringProperties.get("DIALOG_BUTTON_OK"));
        assertEquals(Boolean.TRUE, aApp.mBoolProperties.get("FLAG"));
        assertEquals(42, aApp.mIntProperties.get("NUM")); // strtol base 0: hex accepted
        assertEquals(1.5, aApp.mDoubleProperties.get("F"));
        assertEquals(List.of("one", "two"), aApp.mStringVectorProperties.get("LIST"));
    }

    @Test
    void booleanSpellingsFollowUpstream() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        String anXml = """
                <Properties>
                  <Boolean id="A">1</Boolean>
                  <Boolean id="B">Yes</Boolean>
                  <Boolean id="C">on</Boolean>
                  <Boolean id="D">TRUE</Boolean>
                  <Boolean id="E">0</Boolean>
                  <Boolean id="F">no</Boolean>
                  <Boolean id="G">Off</Boolean>
                  <Boolean id="H">false</Boolean>
                </Properties>
                """;

        assertTrue(aParser.parsePropertiesBuffer(buffer(anXml)), aParser.getErrorText());

        for (String anId : List.of("A", "B", "C", "D")) {
            assertEquals(Boolean.TRUE, aApp.mBoolProperties.get(anId), anId);
        }
        for (String anId : List.of("E", "F", "G", "H")) {
            assertEquals(Boolean.FALSE, aApp.mBoolProperties.get(anId), anId);
        }
    }

    @Test
    void duplicateIdKeepsTheLastValue() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesBuffer(buffer("""
                <Properties>
                  <String id="DUP">first</String>
                  <String id="DUP">second</String>
                </Properties>
                """)));

        assertEquals("second", aApp.mStringProperties.get("DUP"));
    }

    @Test
    void whitespaceInElementTextIsCollapsedLikeUpstream() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesBuffer(buffer(
                "<Properties><String id=\"W\">  a   b  </String></Properties>")));

        assertEquals("a b", aApp.mStringProperties.get("W"));
    }

    @Test
    void unicodeValuesSurvive() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesBuffer(buffer(
                "<Properties><String id=\"U\">caf\u00e9 \u4e2d\u6587</String></Properties>")));

        assertEquals("caf\u00e9 \u4e2d\u6587", aApp.mStringProperties.get("U"));
    }

    @Test
    void quotedTextKeepsItsQuotes() {
        // XML text content keeps quote characters (only attribute scanning treats '"' specially).
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesBuffer(buffer(
                "<Properties><String id=\"Q\">\"quoted\"</String></Properties>")));

        assertEquals("\"quoted\"", aApp.mStringProperties.get("Q"));
    }

    @Test
    void commentsAndInstructionsAreIgnored() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesBuffer(buffer("""
                <?xml version="1.0"?>
                <Properties>
                  <!-- a comment -->
                  <String id="A">x</String>
                </Properties>
                """)));

        assertEquals("x", aApp.mStringProperties.get("A"));
    }

    @Test
    void emptyStringArrayYieldsEmptyList() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesBuffer(buffer(
                "<Properties><StringArray id=\"EMPTY\"></StringArray></Properties>")));

        assertEquals(List.of(), aApp.mStringVectorProperties.get("EMPTY"));
    }

    // ------------------------------------------------------------------
    // Errors
    // ------------------------------------------------------------------

    @Test
    void invalidBooleanValueIsReported() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer(
                "<Properties><Boolean id=\"B\">maybe</Boolean></Properties>")));
        assertEquals("Invalid Boolean Value: 'MAYBE' on Line 1", aParser.getErrorText());
    }

    @Test
    void invalidIntegerAndDoubleValuesAreReported() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer(
                "<Properties><Integer id=\"N\">12abc</Integer></Properties>")));
        assertEquals("Invalid Integer Value: '12abc' on Line 1", aParser.getErrorText());

        SexyAppBase aSecondApp = new SexyAppBase();
        PropertiesParser aSecondParser = new PropertiesParser(aSecondApp);
        assertFalse(aSecondParser.parsePropertiesBuffer(buffer(
                "<Properties><Double id=\"D\">1.2.3</Double></Properties>")));
        assertEquals("Invalid Double Value: '1.2.3' on Line 1", aSecondParser.getErrorText());
    }

    @Test
    void invalidSectionIsReported() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer(
                "<Properties><Bogus id=\"X\">1</Bogus></Properties>")));
        assertEquals("Invalid Section 'Bogus' on Line 1", aParser.getErrorText());
    }

    @Test
    void bareTextInsidePropertiesIsAnError() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer("<Properties>hello</Properties>")));
        assertEquals("Element Not Expected 'hello' on Line 1", aParser.getErrorText());
    }

    @Test
    void unexpectedRootSectionIsAnError() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer("<NotProperties></NotProperties>")));
        assertEquals("Invalid Section 'NotProperties' on Line 1", aParser.getErrorText());
    }

    @Test
    void truncatedDocumentReportsTheXmlParserFailure() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer(
                "<Properties><String id=\"A\"")));
        assertEquals("Unexpected End of File on Line 1", aParser.getErrorText());
        assertFalse(aApp.mStringProperties.containsKey("A"));
    }

    @Test
    void truncatedDocumentAfterCompleteTagsIsNotDetected() {
        // Upstream quirk: when EOF arrives while the scanner is between elements (TYPE_NONE, i.e.
        // after a complete end tag) the XML parser returns false WITHOUT failing
        // (XMLParser.cpp:389-390, 691-697), so ParseSingleElement's false propagates and
        // ParseProperties returns true with the already-stored value kept (PropertiesParser.cpp:262-267).
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesBuffer(buffer(
                "<Properties><String id=\"A\">x</String>")));
        assertEquals("", aParser.getErrorText());
        assertEquals("x", aApp.mStringProperties.get("A"));
    }

    @Test
    void unexpectedNestedSectionInsideStringIsReported() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer(
                "<Properties><String id=\"A\"><Nested/></String></Properties>")));
        assertEquals("Unexpected Section: 'Nested' on Line 1", aParser.getErrorText());
    }

    @Test
    void unterminatedStringArrayStringSectionIsReported() {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer(
                "<Properties><StringArray id=\"A\"><Int/></StringArray></Properties>")));
        assertEquals("Invalid Section 'Int' on Line 1", aParser.getErrorText());
    }

    @Test
    void undecodableBufferIsReported() {
        // A UTF-16LE BOM followed by an odd number of bytes: Buffer.toUTF8String() gives up.
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesBuffer(buffer(
                new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41})));
        assertEquals("Failed to convert properties buffer to UTF-8 string", aParser.getErrorText());
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    @Test
    void parseFileAppendsFileNameToErrors(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("default.xml");
        Files.writeString(aFile, """
                <Properties>
                  <Boolean id="B">maybe</Boolean>
                </Properties>
                """, StandardCharsets.UTF_8);

        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesFile(aFile.toString()));
        assertEquals("Invalid Boolean Value: 'MAYBE' on Line 2 in File '" + aFile + "'",
                aParser.getErrorText());
    }

    @Test
    void parseFileWithoutBomKeepsValues(@TempDir Path theDir) throws Exception {
        Path aFile = theDir.resolve("layout.xml");
        Files.writeString(aFile, "<Properties><Integer id=\"N\">-7</Integer></Properties>",
                StandardCharsets.US_ASCII);

        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertTrue(aParser.parsePropertiesFile(aFile.toString()), aParser.getErrorText());
        assertEquals(-7, aApp.mIntProperties.get("N"));
    }

    @Test
    void missingFileFailsWithoutFileNameSuffix(@TempDir Path theDir) {
        SexyAppBase aApp = new SexyAppBase();
        PropertiesParser aParser = new PropertiesParser(aApp);

        assertFalse(aParser.parsePropertiesFile(theDir.resolve("nope.xml").toString()));
        // openFile failed, so both the line number and the file name are unknown to the XML parser.
        assertEquals("Unable to open file " + theDir.resolve("nope.xml"), aParser.getErrorText());
    }
}
