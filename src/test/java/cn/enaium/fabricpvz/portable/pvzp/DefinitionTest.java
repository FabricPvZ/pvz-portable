package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.parsers.XMLParser;

/**
 * Tests for the definition/format layer ({@link Definition}): the XML descriptor readers, the float
 * track grammar, the schema hash and the compiled definition cache (header, zlib payload, struct image
 * and pointer records) including its invalidation and corruption paths.
 *
 * <p>The cache assertions rebuild the payload independently from the documented layout, so they verify
 * the format byte for byte rather than only round-tripping the engine through itself.
 */
class DefinitionTest {

    /** Pointee of the array field: a definition class with a scalar, a float and a string member. */
    public static class TestItem {
        public int mValue;
        public float mRatio;
        public String mTag = "";

        public TestItem() {
        }
    }

    /** Definition class covering every field type the engine handles. */
    public static class TestDefinition {
        public int mCount;
        public float mScale;
        public String mName = "";
        public int mType;
        public int mFlags;
        public DefinitionArrayDef mItems = new DefinitionArrayDef(new TestItem[0]);
        public FloatParameterTrack mTrack = new FloatParameterTrack();
        public Image mImage;

        public TestDefinition() {
        }
    }

    private static final DefSymbol[] TYPE_SYMBOLS = {
            new DefSymbol(3, "Three"),
            new DefSymbol(7, "Seven"),
            new DefSymbol(-1, null)
    };

    private static final DefSymbol[] FLAG_SYMBOLS = {
            new DefSymbol(0, "Alpha"),
            new DefSymbol(1, "Beta"),
            new DefSymbol(-1, null)
    };

    static final DefMap ITEM_MAP = new DefMapBuilder()
            .field("value", "mValue", DefFieldType.DT_INT)
            .field("ratio", "mRatio", DefFieldType.DT_FLOAT)
            .field("tag", "mTag", DefFieldType.DT_STRING)
            .build(TestItem::new);

    static final DefMap TEST_MAP = new DefMapBuilder()
            .field("count", "mCount", DefFieldType.DT_INT)
            .field("scale", "mScale", DefFieldType.DT_FLOAT)
            .field("name", "mName", DefFieldType.DT_STRING)
            .field("type", "mType", DefFieldType.DT_ENUM, TYPE_SYMBOLS)
            .field("flags", "mFlags", DefFieldType.DT_FLAGS, FLAG_SYMBOLS)
            .field("item", "mItems", DefFieldType.DT_ARRAY, ITEM_MAP)
            .field("track", "mTrack", DefFieldType.DT_TRACK_FLOAT)
            .field("image", "mImage", DefFieldType.DT_IMAGE)
            .build(TestDefinition::new);

    private static final String XML =
            "<count>42</count>"
                    + "<scale>2.5</scale>"
                    + "<name>Test</name>"
                    + "<type>Seven</type>"
                    + "<Alpha>1</Alpha>"
                    + "<Beta>0</Beta>"
                    + "<item><value>1</value><ratio>0.5</ratio><tag>a</tag></item>"
                    + "<item><value>2</value><ratio>1.5</ratio><tag>b</tag></item>"
                    + "<track>0 1,50 [2 EaseOut 4],100</track>"
                    + "<image></image>";

    private String mOriginalAppDataFolder;
    private String mOriginalResourceFolder;

    /** Scratch dir for the descriptor files (the engine reads XML through XMLParser.openFile). */
    @TempDir
    static Path sDescriptorDir;

    @BeforeEach
    void rememberFolders() {
        mOriginalAppDataFolder = Common.getAppDataFolder();
        mOriginalResourceFolder = Common.getResourceFolder();
    }

    @AfterEach
    void restoreGlobals() {
        Common.setAppDataFolder(mOriginalAppDataFolder);
        Common.setResourceFolder(mOriginalResourceFolder);
        Definition.gDefResourceResolver = null;
        Definition.gRequireCompiledUpToDate = false;
    }

    // ------------------------------------------------------------------
    // XML descriptor parsing
    // ------------------------------------------------------------------

    @Test
    void parsesSyntheticDescriptorIntoTheDefinition() {
        TestDefinition aDef = parse(XML);

        assertEquals(42, aDef.mCount);
        assertEquals(2.5f, aDef.mScale);
        assertEquals("Test", aDef.mName);
        assertEquals(7, aDef.mType, "the enum symbol 'Seven' is 7");
        assertEquals(1, aDef.mFlags, "only the Alpha bit is set");
        assertNull(aDef.mImage, "an empty <image/> binds no image");

        assertSame(TestItem.class, aDef.mItems.mArrayData.getClass().getComponentType());
        assertEquals(2, aDef.mItems.mArrayCount);
        TestItem aFirst = (TestItem) aDef.mItems.mArrayData[0];
        TestItem aSecond = (TestItem) aDef.mItems.mArrayData[1];
        assertEquals(1, aFirst.mValue);
        assertEquals(0.5f, aFirst.mRatio);
        assertEquals("a", aFirst.mTag);
        assertEquals(2, aSecond.mValue);
        assertEquals(1.5f, aSecond.mRatio);
        assertEquals("b", aSecond.mTag);
    }

    @Test
    void elementNamesAreMatchedCaseInsensitively() {
        TestDefinition aDef = parse("<COUNT>5</COUNT><Type>seven</Type>");

        assertEquals(5, aDef.mCount);
        assertEquals(7, aDef.mType);
    }

    @Test
    void unknownElementFailsTheLoad() {
        try {
            Path aFile = sDescriptorDir.resolve("defs/Bogus.xml");
            Files.createDirectories(aFile.getParent());
            Files.writeString(aFile, "<bogus>1</bogus>", StandardCharsets.UTF_8);
            XMLParser aParser = new XMLParser();
            try {
                assertTrue(aParser.openFile(aFile.toString()));
                assertFalse(Definition.loadMap(aParser, TEST_MAP, new TestDefinition()));
            } finally {
                aParser.close();
            }
        } catch (IOException anException) {
            throw new IllegalStateException(anException);
        }
    }

    @Test
    void floatTrackGrammarProducesUpstreamNodes() {
        FloatParameterTrack aTrack = parse(XML).mTrack;

        assertEquals(3, aTrack.mCountNodes);
        // "0 1,50 [2 EaseOut 4],100": the untimed nodes are spread between the timestamps
        assertNode(aTrack.mNodes[0], 0.0f, 0.0f, 0.0f, PvzpCurves.CURVE_LINEAR, PvzpCurves.CURVE_LINEAR);
        assertNode(aTrack.mNodes[1], 0.5f, 1.0f, 1.0f, PvzpCurves.CURVE_LINEAR, PvzpCurves.CURVE_LINEAR);
        assertNode(aTrack.mNodes[2], 1.0f, 2.0f, 4.0f, PvzpCurves.CURVE_LINEAR, PvzpCurves.CURVE_EASE_OUT);

        assertTrue(Definition.floatTrackIsSet(aTrack));
        assertEquals(0.5f, Definition.floatTrackEvaluate(aTrack, 0.25f, 0.0f), 1e-6f);
        assertEquals(0.0f, Definition.floatTrackEvaluate(aTrack, -1.0f, 0.0f), 1e-6f);
        assertEquals(0.0f, Definition.floatTrackEvaluateFromLastTime(aTrack, -1.0f, 0.0f), 1e-6f);
        assertFalse(Definition.floatTrackIsConstantZero(aTrack));
    }

    @Test
    void floatTrackHelpersMatchUpstreamDefaults() {
        FloatParameterTrack anEmpty = new FloatParameterTrack();
        assertEquals(0.0f, Definition.floatTrackEvaluate(anEmpty, 1.0f, 0.0f));
        assertTrue(Definition.floatTrackIsConstantZero(anEmpty));
        assertFalse(Definition.floatTrackIsSet(anEmpty));

        Definition.floatTrackSetDefault(anEmpty, 3.5f);
        assertEquals(1, anEmpty.mCountNodes);
        assertEquals(3.5f, anEmpty.mNodes[0].mLowValue);
        assertEquals(3.5f, anEmpty.mNodes[0].mHighValue);
        assertEquals(PvzpCurves.CURVE_CONSTANT, anEmpty.mNodes[0].mCurveType);
        assertFalse(Definition.floatTrackIsSet(anEmpty), "a constant track is not 'set'");
        assertFalse(Definition.floatTrackIsConstantZero(anEmpty), "3.5 is not zero");

        FloatParameterTrack aZero = new FloatParameterTrack();
        Definition.floatTrackSetDefault(aZero, 0.0f);
        assertEquals(0, aZero.mCountNodes);
        assertTrue(Definition.floatTrackIsConstantZero(aZero));
    }

    private static void assertNode(FloatParameterTrackNode theNode, float theTime, float theLow, float theHigh,
                                   int theCurve, int theDistribution) {
        assertEquals(theTime, theNode.mTime, 1e-6f, "node time");
        assertEquals(theLow, theNode.mLowValue, 1e-6f, "node low value");
        assertEquals(theHigh, theNode.mHighValue, 1e-6f, "node high value");
        assertEquals(theCurve, theNode.mCurveType, "node curve");
        assertEquals(theDistribution, theNode.mDistribution, "node distribution");
    }

    /**
     * Writes the descriptor to a scratch file and parses it through {@code XMLParser.openFile}, i.e.
     * the same path the game uses ({@code XMLParser.setStringSource} is a peer-owned string source and
     * cannot be used here).
     */
    private static TestDefinition parse(String theXml) {
        try {
            Path aFile = sDescriptorDir.resolve("defs/TestDef.xml");
            Files.createDirectories(aFile.getParent());
            Files.writeString(aFile, theXml, StandardCharsets.UTF_8);
            XMLParser aParser = new XMLParser();
            try {
                assertTrue(aParser.openFile(aFile.toString()), "the scratch descriptor must open");
                TestDefinition aDef = new TestDefinition();
                assertTrue(Definition.loadMap(aParser, TEST_MAP, aDef), "the synthetic descriptor must parse");
                return aDef;
            } finally {
                aParser.close();
            }
        } catch (IOException anException) {
            throw new IllegalStateException(anException);
        }
    }

    // ------------------------------------------------------------------
    // Schema hash
    // ------------------------------------------------------------------

    @Test
    void schemaHashMatchesAnIndependentCrc32Walk() {
        assertEquals(expectedHash(TEST_MAP), Definition.calcHash(TEST_MAP));
        assertEquals(Definition.calcHash(TEST_MAP), Definition.calcHash(TEST_MAP), "stable across calls");

        DefMap aDifferentOffset = new DefMapBuilder()
                .field("count", "mCount", DefFieldType.DT_INT)
                .field("scale", "mScale", DefFieldType.DT_FLOAT)
                .field("name", "mName", DefFieldType.DT_STRING)
                .field("type", "mType", DefFieldType.DT_ENUM, TYPE_SYMBOLS)
                .field("flags", "mFlags", DefFieldType.DT_FLAGS, FLAG_SYMBOLS)
                .field("item", "mItems", DefFieldType.DT_ARRAY, ITEM_MAP)
                .field("track", "mTrack", DefFieldType.DT_TRACK_FLOAT)
                .build(TestDefinition::new);
        assertNotEquals(Definition.calcHash(TEST_MAP), Definition.calcHash(aDifferentOffset),
                "a map without the image field has a different schema hash");

        DefSymbol[] aRenamedSymbols = {new DefSymbol(7, "Sept"), new DefSymbol(-1, null)};
        DefMap aRenamed = new DefMapBuilder()
                .field("count", "mCount", DefFieldType.DT_INT)
                .field("scale", "mScale", DefFieldType.DT_FLOAT)
                .field("name", "mName", DefFieldType.DT_STRING)
                .field("type", "mType", DefFieldType.DT_ENUM, aRenamedSymbols)
                .build(TestDefinition::new);
        DefMap aBase = new DefMapBuilder()
                .field("count", "mCount", DefFieldType.DT_INT)
                .field("scale", "mScale", DefFieldType.DT_FLOAT)
                .field("name", "mName", DefFieldType.DT_STRING)
                .field("type", "mType", DefFieldType.DT_ENUM, new DefSymbol[] {new DefSymbol(7, "Seven"), new DefSymbol(-1, null)})
                .build(TestDefinition::new);
        assertNotEquals(Definition.calcHash(aBase), Definition.calcHash(aRenamed), "symbol names are hashed");
    }

    // ------------------------------------------------------------------
    // Compiled cache
    // ------------------------------------------------------------------

    @Test
    void compiledCacheIsWrittenByteForByte(@TempDir Path theDir) {
        String anXmlPath = writeXml(theDir, XML);
        TestDefinition aDef = new TestDefinition();
        assertTrue(Definition.loadXML(anXmlPath, TEST_MAP, aDef));

        byte[] aCompressed = readCacheFile(anXmlPath);
        ByteBuffer aHeader = ByteBuffer.wrap(aCompressed).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(Definition.COMPRESSION_COOKIE, aHeader.getInt(), "cookie 0xDEADFED4");
        int anUncompressedSize = aHeader.getInt();
        assertEquals(Definition.getSize(TEST_MAP, aDef) + 4, anUncompressedSize, "declared payload size");
        assertEquals(aCompressed.length - 8, zlibPayloadLength(aCompressed), "the header is exactly 8 bytes");

        byte[] aPayload = inflate(aCompressed, anUncompressedSize);
        assertArrayEquals(buildExpectedPayload(aDef, ITEM_MAP.mDefSize, false), aPayload,
                "payload = schema hash || struct image || pointer records");
    }

    @Test
    void compiledCacheRoundTripRestoresEveryMember(@TempDir Path theDir) {
        String anXmlPath = writeXml(theDir, XML);
        TestDefinition aCompiled = new TestDefinition();
        assertTrue(Definition.loadXML(anXmlPath, TEST_MAP, aCompiled));
        assertTrue(Definition.isCompiled(anXmlPath), "the cache is newer than the source");

        TestDefinition aReloaded = new TestDefinition();
        assertTrue(Definition.readCompiledFile(Definition.getCompiledFilePathFromXMLFilePath(anXmlPath), TEST_MAP,
                aReloaded));

        assertEquals(aCompiled.mCount, aReloaded.mCount);
        assertEquals(aCompiled.mScale, aReloaded.mScale);
        assertEquals(aCompiled.mName, aReloaded.mName);
        assertEquals(aCompiled.mType, aReloaded.mType);
        assertEquals(aCompiled.mFlags, aReloaded.mFlags);
        assertEquals(2, aReloaded.mItems.mArrayCount);
        assertEquals(1, ((TestItem) aReloaded.mItems.mArrayData[0]).mValue);
        assertEquals(0.5f, ((TestItem) aReloaded.mItems.mArrayData[0]).mRatio);
        assertEquals("a", ((TestItem) aReloaded.mItems.mArrayData[0]).mTag);
        assertEquals(2, ((TestItem) aReloaded.mItems.mArrayData[1]).mValue);
        assertEquals(1.5f, ((TestItem) aReloaded.mItems.mArrayData[1]).mRatio);
        assertEquals("b", ((TestItem) aReloaded.mItems.mArrayData[1]).mTag);
        assertEquals(3, aReloaded.mTrack.mCountNodes);
        assertNode(aReloaded.mTrack.mNodes[2], 1.0f, 2.0f, 4.0f, PvzpCurves.CURVE_LINEAR, PvzpCurves.CURVE_EASE_OUT);
        assertNull(aReloaded.mImage);

        // the cache path of loadXML must produce the same result
        TestDefinition aCached = new TestDefinition();
        assertTrue(Definition.loadXML(anXmlPath, TEST_MAP, aCached));
        assertEquals(aCompiled.mTrack.mNodes.length, aCached.mTrack.mNodes.length);
        assertEquals(aCompiled.mItems.mArrayCount, aCached.mItems.mArrayCount);
    }

    @Test
    void cacheIsInvalidatedWhenTheSourceChanges(@TempDir Path theDir) throws IOException {
        String anXmlPath = writeXml(theDir, XML);
        TestDefinition aDef = new TestDefinition();
        assertTrue(Definition.loadXML(anXmlPath, TEST_MAP, aDef));
        assertEquals(2.5f, aDef.mScale);
        assertTrue(Definition.isCompiled(anXmlPath));

        writeXml(theDir, XML.replace("<scale>2.5</scale>", "<scale>9.5</scale>"));
        Files.setLastModifiedTime(Path.of(anXmlPath), FileTime.fromMillis(System.currentTimeMillis() + 10_000));
        assertFalse(Definition.isCompiled(anXmlPath), "a newer source invalidates the cache");

        // with the debug-build policy the loader recompiles instead of trusting the stale cache
        Definition.gRequireCompiledUpToDate = true;
        TestDefinition aReloaded = new TestDefinition();
        assertTrue(Definition.loadXML(anXmlPath, TEST_MAP, aReloaded));
        assertEquals(9.5f, aReloaded.mScale, "the changed source wins");
        // the recompiled cache is newer than the source once the source stops being "from the future"
        Files.setLastModifiedTime(Path.of(anXmlPath), FileTime.fromMillis(System.currentTimeMillis() - 60_000));
        assertTrue(Definition.isCompiled(anXmlPath), "recompiling refreshes the cache");

        // the release-build policy trusts the compiled file (upstream's default behaviour)
        Definition.gRequireCompiledUpToDate = false;
        TestDefinition aStale = new TestDefinition();
        assertTrue(Definition.loadXML(anXmlPath, TEST_MAP, aStale));
        assertEquals(9.5f, aStale.mScale, "the cache was refreshed by the recompile above");
    }

    @Test
    void corruptedCacheIsRejectedWithoutThrowing(@TempDir Path theDir) throws IOException {
        String anXmlPath = writeXml(theDir, XML);
        TestDefinition aDef = new TestDefinition();
        assertTrue(Definition.loadXML(anXmlPath, TEST_MAP, aDef));

        String aCompiledPath = Definition.getCompiledFilePathFromXMLFilePath(anXmlPath);
        Path aCacheFile = Path.of(Definition.getCompiledCacheFullPath(aCompiledPath));
        byte[] aGood = Files.readAllBytes(aCacheFile);

        // too small to hold the CompressedDefinitionHeader
        byte[] aTiny = {1, 2, 3};
        assertNull(Definition.uncompressCompiledBuffer(aTiny, aCompiledPath));
        Files.write(aCacheFile, aTiny);
        assertFalse(Definition.readCompiledFile(aCompiledPath, TEST_MAP, new TestDefinition()));

        // wrong cookie
        byte[] aBadCookie = aGood.clone();
        aBadCookie[0] ^= 0xFF;
        assertNull(Definition.uncompressCompiledBuffer(aBadCookie, aCompiledPath));
        Files.write(aCacheFile, aBadCookie);
        assertFalse(Definition.readCompiledFile(aCompiledPath, TEST_MAP, new TestDefinition()));

        // undecodable zlib payload
        byte[] aGarbage = aGood.clone();
        for (int i = 8; i < aGarbage.length; i++) {
            aGarbage[i] = (byte) 0xFF;
        }
        assertNull(Definition.uncompressCompiledBuffer(aGarbage, aCompiledPath));

        // schema hash mismatch
        int anUncompressedSize = ByteBuffer.wrap(aGood).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
        byte[] aPayload = inflate(aGood, anUncompressedSize);
        byte[] aWrongHash = aPayload.clone();
        aWrongHash[0] ^= 0xFF;
        Files.write(aCacheFile, Definition.compressCompiledBuffer(aWrongHash, aWrongHash.length));
        assertFalse(Definition.readCompiledFile(aCompiledPath, TEST_MAP, new TestDefinition()));

        // truncated payload (records missing): must fail, not throw
        byte[] aTruncated = java.util.Arrays.copyOf(aPayload, 4 + TEST_MAP.mDefSize);
        Files.write(aCacheFile, Definition.compressCompiledBuffer(aTruncated, aTruncated.length));
        assertFalse(Definition.readCompiledFile(aCompiledPath, TEST_MAP, new TestDefinition()));

        // array record whose element definition size does not match the pointee map
        TestDefinition anOther = parse(XML);
        byte[] aWrongArray = buildExpectedPayload(anOther, ITEM_MAP.mDefSize + 4, false);
        Files.write(aCacheFile, Definition.compressCompiledBuffer(aWrongArray, aWrongArray.length));
        assertFalse(Definition.readCompiledFile(aCompiledPath, TEST_MAP, new TestDefinition()));

        // a good cache still loads after all of that
        Files.write(aCacheFile, aGood);
        assertTrue(Definition.readCompiledFile(aCompiledPath, TEST_MAP, new TestDefinition()));
    }

    @Test
    void compressedBufferKeepsTheDocumentedHeader(@TempDir Path theDir) {
        byte[] aPayload = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] aCompressed = Definition.compressCompiledBuffer(aPayload, aPayload.length);

        ByteBuffer aHeader = ByteBuffer.wrap(aCompressed).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(Definition.COMPRESSION_COOKIE, aHeader.getInt());
        assertEquals(aPayload.length, aHeader.getInt());
        assertArrayEquals(aPayload, inflate(aCompressed, aPayload.length));
    }

    @Test
    void freeMapResetsPointerMembers() {
        TestDefinition aDef = parse(XML);
        Definition.freeMap(TEST_MAP, aDef);

        assertEquals("", aDef.mName);
        assertEquals(0, aDef.mItems.mArrayCount);
        assertEquals(0, aDef.mItems.mArrayData.length);
        assertEquals(0, aDef.mTrack.mCountNodes);
        assertNull(aDef.mTrack.mNodes);
        assertNull(aDef.mImage);
        assertEquals(42, aDef.mCount, "scalars are not touched by the free pass");
    }

    @Test
    void builderLaysOutMembersWithTheLp64Rules() {
        DefField aCount = TEST_MAP.findField("count");
        DefField aName = TEST_MAP.findField("name");
        DefField anItems = TEST_MAP.findField("item");
        DefField anImage = TEST_MAP.findField("image");

        assertEquals(0, aCount.mFieldOffset);
        assertEquals(8, aName.mFieldOffset, "a pointer member is 8-byte aligned");
        assertEquals(DefFieldType.DT_ARRAY, anItems.mFieldType);
        assertEquals(DefFieldType.DT_IMAGE, anImage.mFieldType);
        assertEquals(16, DefFieldType.sizeOf(DefFieldType.DT_ARRAY), "pointer + count, padded to 8");
        assertEquals(8, DefFieldType.sizeOf(DefFieldType.DT_STRING));
        assertEquals(4, DefFieldType.sizeOf(DefFieldType.DT_INT));
        assertEquals(0, TEST_MAP.mDefSize % 8, "the struct image size is 8-byte aligned");
        assertEquals(20, FloatParameterTrackNode.SIZE);
        assertEquals("mCount", aCount.mMemberName);
        assertNotNull(TEST_MAP.findField("COUNT"), "field lookup is case-insensitive");
        assertNull(TEST_MAP.findField("missing"));
        assertEquals(8, TEST_MAP.fieldCount());
        assertEquals(ITEM_MAP.mDefSize, ITEM_MAP.findField("tag").mFieldOffset + 8,
                "the item map ends after the string pointer slot");
    }

    // ------------------------------------------------------------------
    // Independent format reconstruction
    // ------------------------------------------------------------------

    private static String writeXml(Path theDir, String theXml) {
        try {
            Path aDir = theDir.resolve("particles");
            Files.createDirectories(aDir);
            Path anXml = aDir.resolve("TestDef.xml");
            Files.writeString(anXml, theXml, StandardCharsets.UTF_8);
            Common.setAppDataFolder(theDir.resolve("appdata").toString());
            return anXml.toString();
        } catch (Exception anException) {
            throw new IllegalStateException(anException);
        }
    }

    private static byte[] readCacheFile(String theXmlPath) {
        try {
            String aCompiledPath = Definition.getCompiledFilePathFromXMLFilePath(theXmlPath);
            return Files.readAllBytes(Path.of(Definition.getCompiledCacheFullPath(aCompiledPath)));
        } catch (Exception anException) {
            throw new IllegalStateException(anException);
        }
    }

    /** Rebuilds the expected payload from the documented layout instead of asking the engine. */
    private static byte[] buildExpectedPayload(TestDefinition theDef, int theArrayDefSize, boolean theDropRecords) {
        int aSize = Definition.getSize(TEST_MAP, theDef) + 4;
        ByteBuffer aPayload = ByteBuffer.allocate(aSize).order(ByteOrder.LITTLE_ENDIAN);
        aPayload.putInt(expectedHash(TEST_MAP));

        byte[] aStructImage = new byte[TEST_MAP.mDefSize];
        ByteBuffer anImage = ByteBuffer.wrap(aStructImage).order(ByteOrder.LITTLE_ENDIAN);
        anImage.putInt(TEST_MAP.findField("count").mFieldOffset, theDef.mCount);
        anImage.putFloat(TEST_MAP.findField("scale").mFieldOffset, theDef.mScale);
        anImage.putInt(TEST_MAP.findField("type").mFieldOffset, theDef.mType);
        anImage.putInt(TEST_MAP.findField("flags").mFieldOffset, theDef.mFlags);
        anImage.putInt(TEST_MAP.findField("item").mFieldOffset + 8, theDef.mItems.mArrayCount);
        anImage.putInt(TEST_MAP.findField("track").mFieldOffset + 8, theDef.mTrack.mCountNodes);
        aPayload.put(aStructImage);

        if (theDropRecords) {
            return aPayload.array();
        }

        // records, in DefMap field order: name, item[], track, image
        putString(aPayload, theDef.mName);
        aPayload.putInt(theArrayDefSize);
        for (int i = 0; i < theDef.mItems.mArrayCount; i++) {
            TestItem anItem = (TestItem) theDef.mItems.mArrayData[i];
            ByteBuffer anItemImage = ByteBuffer.allocate(ITEM_MAP.mDefSize).order(ByteOrder.LITTLE_ENDIAN);
            anItemImage.putInt(ITEM_MAP.findField("value").mFieldOffset, anItem.mValue);
            anItemImage.putFloat(ITEM_MAP.findField("ratio").mFieldOffset, anItem.mRatio);
            aPayload.put(anItemImage.array());
        }
        for (int i = 0; i < theDef.mItems.mArrayCount; i++) {
            TestItem anItem = (TestItem) theDef.mItems.mArrayData[i];
            putString(aPayload, anItem.mTag);
        }
        aPayload.putInt(theDef.mTrack.mCountNodes);
        for (int i = 0; i < theDef.mTrack.mCountNodes; i++) {
            FloatParameterTrackNode aNode = theDef.mTrack.mNodes[i];
            aPayload.putFloat(aNode.mTime);
            aPayload.putFloat(aNode.mLowValue);
            aPayload.putFloat(aNode.mHighValue);
            aPayload.putInt(aNode.mCurveType);
            aPayload.putInt(aNode.mDistribution);
        }
        putString(aPayload, theDef.mImage == null || theDef.mImage.mFilePath == null ? "" : theDef.mImage.mFilePath);

        assertEquals(aSize, aPayload.position(), "the independent reconstruction must fill the payload exactly");
        return aPayload.array();
    }

    private static void putString(ByteBuffer thePayload, String theValue) {
        byte[] aBytes = theValue.getBytes(StandardCharsets.UTF_8);
        thePayload.putInt(aBytes.length);
        thePayload.put(aBytes);
    }

    /**
     * The cache format version seeds the walk: it is mixed into every schema hash so a cache written by
     * another build is rejected and recompiled (bumped to 2 after the cache writer stamped the background
     * image's name into every empty image slot - docs/VERIFICATION.md §12).
     */
    private static int expectedHash(DefMap theDefMap) {
        return expectedHashDefMap(Definition.CACHE_FORMAT_VERSION, theDefMap, new ArrayList<>());
    }

    /** Mirrors {@code DefinitionCalcHashDefMap}: CRC-32 of the map layout, recursing into array maps. */
    private static int expectedHashDefMap(int theHash, DefMap theDefMap, List<DefMap> theSeen) {
        for (DefMap aMap : theSeen) {
            if (aMap == theDefMap) {
                return theHash;
            }
        }
        theSeen.add(theDefMap);

        int aHash = crc32(theHash, littleEndian(theDefMap.mDefSize));
        for (int i = 0; i < theDefMap.fieldCount(); i++) {
            DefField aField = theDefMap.mMapFields[i];
            aHash = crc32(aHash, littleEndian(aField.mFieldType));
            aHash = crc32(aHash, littleEndian(aField.mFieldOffset));
            if (aField.mFieldType == DefFieldType.DT_ENUM || aField.mFieldType == DefFieldType.DT_FLAGS) {
                for (DefSymbol aSymbol : aField.symbols()) {
                    if (aSymbol == null || aSymbol.mSymbolName == null) {
                        break;
                    }
                    aHash = crc32(aHash, aSymbol.mSymbolName.getBytes(StandardCharsets.UTF_8));
                    aHash = crc32(aHash, littleEndian(aSymbol.mSymbolValue));
                }
            } else if (aField.mFieldType == DefFieldType.DT_ARRAY) {
                aHash = expectedHashDefMap(aHash, aField.arrayDefMap(), theSeen);
            }
        }
        return aHash;
    }

    private static byte[] littleEndian(int theValue) {
        return new byte[] {(byte) theValue, (byte) (theValue >>> 8), (byte) (theValue >>> 16), (byte) (theValue >>> 24)};
    }

    private static int crc32(int theCrc, byte[] theData) {
        int aCrc = ~theCrc;
        for (byte aByte : theData) {
            aCrc = (aCrc >>> 8) ^ CRC_TABLE[(aCrc ^ aByte) & 0xFF];
        }
        return ~aCrc;
    }

    private static final int[] CRC_TABLE = makeCrcTable();

    private static int[] makeCrcTable() {
        int[] aTable = new int[256];
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
            }
            aTable[n] = c;
        }
        return aTable;
    }

    private static byte[] inflate(byte[] theCompressed, int theExpectedSize) {
        byte[] aResult = new byte[theExpectedSize];
        Inflater anInflater = new Inflater();
        try {
            anInflater.setInput(theCompressed, 8, theCompressed.length - 8);
            int aTotal = anInflater.inflate(aResult);
            assertEquals(theExpectedSize, aTotal, "the payload must inflate to its declared size");
            return aResult;
        } catch (Exception anException) {
            throw new IllegalStateException(anException);
        } finally {
            anInflater.end();
        }
    }

    private static int zlibPayloadLength(byte[] theCompressed) {
        Inflater anInflater = new Inflater();
        try {
            anInflater.setInput(theCompressed, 8, theCompressed.length - 8);
            byte[] aScratch = new byte[16];
            while (anInflater.inflate(aScratch) > 0) {
                // drain
            }
            return anInflater.getBytesRead() == 0 ? 0 : (int) anInflater.getBytesRead();
        } catch (Exception anException) {
            throw new IllegalStateException(anException);
        } finally {
            anInflater.end();
        }
    }
}
