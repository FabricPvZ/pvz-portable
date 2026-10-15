package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavior and on-disk-format tests for {@link RegEmu}
 * (src/SexyAppFramework/misc/RegEmu.cpp).
 */
class RegEmuTest {

    private static final String KEY = "SOFTWARE\\Test";

    /** Clears the process-wide registry (and its temp-dir file binding) after each test. */
    @AfterEach
    void clearRegistry() {
        RegEmu.setRegFile("");
    }

    private static long readU32LE(byte[] theData, int thePos) {
        return (theData[thePos] & 0xFFL)
                | ((theData[thePos + 1] & 0xFFL) << 8)
                | ((theData[thePos + 2] & 0xFFL) << 16)
                | ((theData[thePos + 3] & 0xFFL) << 24);
    }

    private static String readCString(byte[] theData, int thePos) {
        int anEnd = thePos;
        while (theData[anEnd] != 0) {
            anEnd++;
        }
        return new String(theData, thePos, anEnd - thePos, StandardCharsets.UTF_8);
    }

    @Test
    void missingFileClearsStore(@TempDir Path theTempDir) {
        Path aFile = theTempDir.resolve("registry.regemu");
        RegEmu.setRegFile(aFile.toString());

        assertFalse(Files.exists(aFile));
        assertFalse(RegEmu.registryRead(KEY, "Anything", new RegEmu.RegValue()));
        assertNull(RegEmu.registryReadInteger(KEY, "Anything"));
        assertNull(RegEmu.registryReadString(KEY, "Anything"));
        assertNull(RegEmu.registryReadBoolean(KEY, "Anything"));
        assertNull(RegEmu.registryReadData(KEY, "Anything"));
    }

    @Test
    void typedSetAndGet(@TempDir Path theTempDir) {
        RegEmu.setRegFile(theTempDir.resolve("registry.regemu").toString());

        assertTrue(RegEmu.registryWriteInteger(KEY, "MusicVolume", 80));
        assertTrue(RegEmu.registryWriteString(KEY, "CurUser", "Enaium"));
        assertTrue(RegEmu.registryWriteBoolean(KEY, "WaitForVSync", true));
        assertTrue(RegEmu.registryWriteBoolean(KEY, "CustomCursors", false));
        assertTrue(RegEmu.registryWriteData(KEY, "Blob", new byte[]{9, 8, 7}));

        assertEquals(Integer.valueOf(80), RegEmu.registryReadInteger(KEY, "MusicVolume"));
        assertEquals("Enaium", RegEmu.registryReadString(KEY, "CurUser"));
        assertEquals(Boolean.TRUE, RegEmu.registryReadBoolean(KEY, "WaitForVSync"));
        assertEquals(Boolean.FALSE, RegEmu.registryReadBoolean(KEY, "CustomCursors"));
        assertArrayEquals(new byte[]{9, 8, 7}, RegEmu.registryReadData(KEY, "Blob"));

        // raw read exposes the type tag
        RegEmu.RegValue aValue = new RegEmu.RegValue();
        assertTrue(RegEmu.registryRead(KEY, "MusicVolume", aValue));
        assertEquals(RegEmu.REGEMU_DWORD, aValue.mType);
        assertEquals(4, aValue.mLength);
        assertEquals(4, aValue.mValue.length);
    }

    @Test
    void wrongTypeReadsFail(@TempDir Path theTempDir) {
        RegEmu.setRegFile(theTempDir.resolve("registry.regemu").toString());

        RegEmu.registryWriteInteger(KEY, "N", 7);
        RegEmu.registryWriteString(KEY, "S", "x");

        // upstream RegistryReadString/Integer/Boolean reject mismatched type tags
        assertNull(RegEmu.registryReadString(KEY, "N"));
        assertNull(RegEmu.registryReadInteger(KEY, "S"));
        assertNull(RegEmu.registryReadBoolean(KEY, "S"));
        assertNull(RegEmu.registryReadData(KEY, "S"));
    }

    @Test
    void defaultsAndTypeConstants(@TempDir Path theTempDir) {
        RegEmu.setRegFile(theTempDir.resolve("registry.regemu").toString());

        // a missing key/value returns null, so callers fall back to their own default
        assertNull(RegEmu.registryReadInteger(KEY, "Missing"));
        assertNull(RegEmu.registryReadInteger("SOFTWARE\\None", "Missing"));
        assertFalse(RegEmu.registryRead("SOFTWARE\\None", "Missing", new RegEmu.RegValue()));

        assertEquals(0, RegEmu.REGEMU_NONE);
        assertEquals(1, RegEmu.REGEMU_SZ);
        assertEquals(2, RegEmu.REGEMU_EXPAND_SZ);
        assertEquals(3, RegEmu.REGEMU_BINARY);
        assertEquals(4, RegEmu.REGEMU_DWORD);
        assertEquals(4, RegEmu.REGEMU_DWORD_LITTLE_ENDIAN);
        assertEquals(5, RegEmu.REGEMU_DWORD_BIG_ENDIAN);
        assertEquals(7, RegEmu.REGEMU_MULTI_SZ);
        assertEquals(11, RegEmu.REGEMU_QWORD);
        assertEquals(11, RegEmu.REGEMU_QWORD_LITTLE_ENDIAN);
        assertEquals(1, RegEmu.REGEMU_VERSION);
    }

    @Test
    void saveAndReloadRoundTrip(@TempDir Path theTempDir) throws IOException {
        Path aFile = theTempDir.resolve("registry.regemu");
        RegEmu.setRegFile(aFile.toString());

        RegEmu.registryWriteInteger(KEY, "MusicVolume", -12345);
        RegEmu.registryWriteString(KEY, "CurUser", "Zombie\u00e9");
        RegEmu.registryWriteBoolean(KEY, "Muted", true);
        RegEmu.registryWriteData(KEY, "Raw", new byte[0]);
        assertTrue(Files.exists(aFile));
        assertTrue(Files.size(aFile) > 0);

        byte[] aFirst = Files.readAllBytes(aFile);

        // re-open the same file into a cleared store
        RegEmu.setRegFile(aFile.toString());
        assertEquals(Integer.valueOf(-12345), RegEmu.registryReadInteger(KEY, "MusicVolume"));
        assertEquals("Zombie\u00e9", RegEmu.registryReadString(KEY, "CurUser"));
        assertEquals(Boolean.TRUE, RegEmu.registryReadBoolean(KEY, "Muted"));
        assertArrayEquals(new byte[0], RegEmu.registryReadData(KEY, "Raw"));

        // resaving produces the identical byte stream
        RegEmu.registryWriteInteger(KEY, "MusicVolume", -12345);
        assertArrayEquals(aFirst, Files.readAllBytes(aFile));
    }

    @Test
    void onDiskFormat(@TempDir Path theTempDir) throws IOException {
        Path aFile = theTempDir.resolve("registry.regemu");
        RegEmu.setRegFile(aFile.toString());

        RegEmu.registryWriteInteger(KEY, "A", 1);
        RegEmu.registryWriteString(KEY, "B", "hi");

        byte[] aData = Files.readAllBytes(aFile);

        // "REGEMU" | version=1 uint16 | numKeys=1 uint32
        assertEquals("REGEMU", new String(aData, 0, 6, StandardCharsets.US_ASCII));
        assertEquals(0x01, aData[6] & 0xFF);
        assertEquals(0x00, aData[7] & 0xFF);
        assertEquals(1, readU32LE(aData, 8));

        // key: name-length (including the NUL) + name + NUL, then numValues
        assertEquals(14, readU32LE(aData, 12));
        assertEquals(KEY, readCString(aData, 16));
        assertEquals(0, aData[16 + 13]);
        assertEquals(2, readU32LE(aData, 30));

        // value "A": length 2, name + NUL, type DWORD, length 4, LE value 1
        assertEquals(2, readU32LE(aData, 34));
        assertEquals("A", readCString(aData, 38));
        assertEquals(0, aData[39]);
        assertEquals(RegEmu.REGEMU_DWORD, readU32LE(aData, 40));
        assertEquals(4, readU32LE(aData, 44));
        assertEquals(1, readU32LE(aData, 48));

        // value "B": type SZ, length 2, "hi"
        assertEquals(2, readU32LE(aData, 52));
        assertEquals("B", readCString(aData, 56));
        assertEquals(0, aData[57]);
        assertEquals(RegEmu.REGEMU_SZ, readU32LE(aData, 58));
        assertEquals(2, readU32LE(aData, 62));
        assertEquals("hi", new String(aData, 66, 2, StandardCharsets.UTF_8));

        assertEquals(68, aData.length);
    }

    @Test
    void stringLengthIsByteCount(@TempDir Path theTempDir) throws IOException {
        Path aFile = theTempDir.resolve("registry.regemu");
        RegEmu.setRegFile(aFile.toString());

        // the stored length is the UTF-8 byte count, not the Java char count
        RegEmu.registryWriteString(KEY, "S", "\u00e9");
        RegEmu.RegValue aValue = new RegEmu.RegValue();
        assertTrue(RegEmu.registryRead(KEY, "S", aValue));
        assertEquals(2, aValue.mLength);
        assertEquals(RegEmu.REGEMU_SZ, aValue.mType);
        assertArrayEquals(new byte[]{(byte) 0xC3, (byte) 0xA9}, aValue.mValue);
        assertEquals("\u00e9", RegEmu.registryReadString(KEY, "S"));

        byte[] aData = Files.readAllBytes(aFile);
        assertTrue(new String(aData, StandardCharsets.ISO_8859_1).contains("\u00c3\u00a9"));
    }

    @Test
    void eraseValueAndKey(@TempDir Path theTempDir) throws IOException {
        Path aFile = theTempDir.resolve("registry.regemu");
        RegEmu.setRegFile(aFile.toString());

        RegEmu.registryWriteInteger(KEY, "A", 1);
        RegEmu.registryWriteInteger(KEY, "B", 2);
        long aFullSize = Files.size(aFile);

        assertTrue(RegEmu.registryEraseValue(KEY, "A"));
        assertNull(RegEmu.registryReadInteger(KEY, "A"));
        assertEquals(Integer.valueOf(2), RegEmu.registryReadInteger(KEY, "B"));
        assertTrue(Files.size(aFile) < aFullSize);

        assertFalse(RegEmu.registryEraseValue(KEY, "A"));
        assertFalse(RegEmu.registryEraseValue("SOFTWARE\\None", "A"));

        assertTrue(RegEmu.registryEraseKey(KEY));
        assertNull(RegEmu.registryReadInteger(KEY, "B"));
        assertFalse(RegEmu.registryEraseKey(KEY));

        // the erased store is what gets persisted
        RegEmu.setRegFile(aFile.toString());
        assertNull(RegEmu.registryReadInteger(KEY, "B"));
    }

    @Test
    void invalidAndTruncatedFiles(@TempDir Path theTempDir) throws IOException {
        Path aFile = theTempDir.resolve("registry.regemu");

        Files.write(aFile, "NOTAREGEMU".getBytes(StandardCharsets.US_ASCII));
        RegEmu.setRegFile(aFile.toString());
        assertNull(RegEmu.registryReadInteger(KEY, "A"));

        // valid header, numKeys=1, then nothing: parsing stops early and reads still fail
        ByteArrayOutputStream aTruncated = new ByteArrayOutputStream();
        aTruncated.write("REGEMU".getBytes(StandardCharsets.US_ASCII));
        aTruncated.write(1);
        aTruncated.write(0);
        aTruncated.write(new byte[]{1, 0, 0, 0});
        Files.write(aFile, aTruncated.toByteArray());
        RegEmu.setRegFile(aFile.toString());
        assertNull(RegEmu.registryReadInteger(KEY, "A"));

        // the version field is written but never validated on load
        RegEmu.registryWriteInteger(KEY, "A", 42);
        byte[] aData = Files.readAllBytes(aFile);
        aData[6] = 99;
        Files.write(aFile, aData);
        RegEmu.setRegFile(aFile.toString());
        assertEquals(Integer.valueOf(42), RegEmu.registryReadInteger(KEY, "A"));
    }

    @Test
    void emptyStringAndZeroLengthValues(@TempDir Path theTempDir) {
        RegEmu.setRegFile(theTempDir.resolve("registry.regemu").toString());

        RegEmu.registryWriteString(KEY, "Empty", "");
        assertEquals("", RegEmu.registryReadString(KEY, "Empty"));

        RegEmu.registryWriteData(KEY, "None", new byte[0]);
        assertNotNull(RegEmu.registryReadData(KEY, "None"));
        assertEquals(0, RegEmu.registryReadData(KEY, "None").length);
    }
}
