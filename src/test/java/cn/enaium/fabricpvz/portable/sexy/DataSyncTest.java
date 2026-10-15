package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Byte-level verification of the ported save-file primitives (docs/research/03-save.md §2). */
class DataSyncTest {

    private static final byte[] GOLDEN_BLOB = new byte[] {
        // writeUInt8(0x12)
        0x12,
        // writeUInt16(0x3456)
        0x56, 0x34,
        // writeUInt32(0x789ABCDE)
        (byte) 0xDE, (byte) 0xBC, (byte) 0x9A, 0x78,
        // writeUInt64(0x0102030405060708)
        0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
        // writeBool(true)
        0x01,
        // writeFloat(1.0f) -> 0x3F800000
        0x00, 0x00, (byte) 0x80, 0x3F,
        // writeDouble(2.0) -> 0x4000000000000000
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x40,
        // writeString("hi") -> uint16 length then raw bytes
        0x02, 0x00, 'h', 'i'
    };

    @Test
    void writesHandComputedLittleEndianBytes() {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        aWriter.writeUInt8(0x12);
        aWriter.writeUInt16(0x3456);
        aWriter.writeUInt32(0x789ABCDE);
        aWriter.writeUInt64(0x0102030405060708L);
        aWriter.writeBool(true);
        aWriter.writeFloat(1.0f);
        aWriter.writeDouble(2.0);
        aWriter.writeString("hi");

        assertArrayEquals(GOLDEN_BLOB, aWriter.getDataPtr());
    }

    @Test
    void readsHandComputedLittleEndianBytes() {
        DataReader aReader = new DataReader();
        aReader.openMemory(GOLDEN_BLOB);

        assertEquals(0x12, aReader.readUInt8());
        assertEquals(0x3456, aReader.readUInt16());
        assertEquals(0x789ABCDE, aReader.readUInt32());
        assertEquals(0x0102030405060708L, aReader.readUInt64());
        assertTrue(aReader.readBool());
        assertEquals(1.0f, aReader.readFloat());
        assertEquals(2.0, aReader.readDouble());
        assertEquals("hi", aReader.readString());
    }

    @Test
    void roundTripsEveryPrimitive() {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        aWriter.writeUInt8(0xAB);
        aWriter.writeUInt16(0xFFFE);
        aWriter.writeUInt32(0x80000000);
        aWriter.writeUInt64(-1L);
        aWriter.writeBool(false);
        aWriter.writeFloat(-3.5f);
        aWriter.writeDouble(Math.PI);
        aWriter.writeString("Ünïcödé \u00e9");

        DataReader aReader = new DataReader();
        aReader.openMemory(aWriter.getDataPtr());
        assertEquals(0xAB, aReader.readUInt8());
        assertEquals(0xFFFE, aReader.readUInt16());
        assertEquals(0x80000000, aReader.readUInt32());
        assertEquals(-1L, aReader.readUInt64());
        assertFalse(aReader.readBool());
        assertEquals(-3.5f, aReader.readFloat());
        assertEquals(Math.PI, aReader.readDouble());
        assertEquals("Ünïcödé \u00e9", aReader.readString());
    }

    @Test
    void stringLengthPrefixIsLittleEndianUInt16() {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        aWriter.writeString("abc");
        byte[] aBytes = aWriter.getDataPtr();
        assertArrayEquals(new byte[] {0x03, 0x00, 'a', 'b', 'c'}, aBytes);

        DataReader aReader = new DataReader();
        aReader.openMemory(aBytes);
        assertEquals("abc", aReader.readString());
    }

    @Test
    void writerTruncatesStringLengthToUInt16LikeUpstream() {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        String aLong = "x".repeat(70000);
        aWriter.writeString(aLong);
        byte[] aBytes = aWriter.getDataPtr();
        int aStoredLength = (aBytes[0] & 0xFF) | ((aBytes[1] & 0xFF) << 8);
        assertEquals(0xFFFF, aStoredLength);
        assertEquals(2 + 0xFFFF, aBytes.length);
    }

    @Test
    void readerThrowsWhenReadingPastEnd() {
        DataReader aReader = new DataReader();
        aReader.openMemory(new byte[] {0x01});
        aReader.readUInt8();
        assertThrows(DataReaderException.class, aReader::readUInt8);
    }

    @Test
    void syncMirrorsTheStreamDirection() {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        DataSync aWriteSync = new DataSync(aWriter);
        assertEquals(7, aWriteSync.syncUInt32(7));
        assertEquals("name", aWriteSync.syncString("name"));
        assertTrue(aWriteSync.syncBool(true));

        DataReader aReader = new DataReader();
        aReader.openMemory(aWriter.getDataPtr());
        DataSync aReadSync = new DataSync(aReader);
        assertEquals(7, aReadSync.syncUInt32(0));
        assertEquals("name", aReadSync.syncString(""));
        assertTrue(aReadSync.syncBool(false));
    }

    @Test
    void goldenBlobMatchesConstructedRecord() {
        // A tiny v4-style record: magic bytes, version, profile name, coins, a flag.
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        aWriter.writeBytes("PVZP_SAVE4".getBytes(StandardCharsets.ISO_8859_1));
        aWriter.writeUInt32(1);
        aWriter.writeString("Player");
        aWriter.writeUInt32(1234);
        aWriter.writeBool(true);

        byte[] expected = new byte[] {
            'P', 'V', 'Z', 'P', '_', 'S', 'A', 'V', 'E', '4',
            0x01, 0x00, 0x00, 0x00,
            0x06, 0x00, 'P', 'l', 'a', 'y', 'e', 'r',
            (byte) 0xD2, 0x04, 0x00, 0x00,
            0x01
        };
        assertArrayEquals(expected, aWriter.getDataPtr());
    }
}
