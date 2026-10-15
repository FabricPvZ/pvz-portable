package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Buffer}: bit-level read/write, the vector-backed storage accounting
 * ({@code GetDataLen}/{@code GetDataLenBits}), the web encoding round trip, CRC-32 and the
 * {@link ByteBuffer} accessor's bounds. Expected CRC and web-encoding values are computed from
 * upstream's table-generating algorithm.
 */
class BufferTest {

    @Test
    void byteRoundTripAndUnderflow() {
        Buffer b = new Buffer();
        b.writeByte((byte) 0xAB);
        b.writeByte((byte) 0xCD);

        assertEquals(2, b.getDataLen());
        assertEquals(16, b.getDataLenBits());

        b.seekFront();
        assertEquals((byte) 0xAB, b.readByte());
        assertEquals((byte) 0xCD, b.readByte());
        assertEquals(0, (int) b.readByte()); // Underflow
    }

    @Test
    void numBitsRoundTripSignedAndUnsigned() {
        Buffer b = new Buffer();
        b.writeNumBits(5, 3); // 101

        b.seekFront();
        assertEquals(5, b.readNumBits(3, false));

        b.seekFront();
        assertEquals(-3, b.readNumBits(3, true)); // 101 sign-extended
    }

    @Test
    void getBitsRequired() {
        assertEquals(0, Buffer.getBitsRequired(0, false));
        assertEquals(1, Buffer.getBitsRequired(1, false));
        assertEquals(2, Buffer.getBitsRequired(2, false));
        assertEquals(8, Buffer.getBitsRequired(255, false));
        assertEquals(1, Buffer.getBitsRequired(-1, true));
        assertEquals(2, Buffer.getBitsRequired(-2, true));
        assertEquals(4, Buffer.getBitsRequired(5, true));
    }

    @Test
    void shortAndIntRoundTrip() {
        Buffer b = new Buffer();
        b.writeShort((short) 0x1234);
        b.writeShort((short) 0xABCD);
        b.writeUInt32(0xDEADBEEF);
        b.writeInt32(-1);

        b.seekFront();
        assertEquals((short) 0x1234, b.readShort());
        assertEquals((short) 0xABCD, b.readShort());
        assertEquals(0xDEADBEEF, b.readUInt32());
        assertEquals(-1, b.readInt32());
    }

    @Test
    void stringAndLineRoundTrip() {
        Buffer b = new Buffer();
        b.writeString("Hello");
        b.writeLine("AB");

        b.seekFront();
        assertEquals("Hello", b.readString());
        assertEquals("AB", b.readLine());
    }

    @Test
    void bufferRoundTrip() {
        Buffer b = new Buffer();
        b.writeBuffer(new byte[] {1, 2, 3, 4});

        b.seekFront();
        assertArrayEquals(new byte[] {1, 2, 3, 4}, b.readBuffer());
    }

    @Test
    void setDataReplacesStorage() {
        Buffer b = new Buffer();
        b.writeByte((byte) 9);

        b.setData(new byte[] {1, 2, 3});
        assertEquals(3, b.getDataLen());
        assertEquals(24, b.getDataLenBits());

        b.seekFront();
        assertEquals((byte) 1, b.readByte());
        assertEquals((byte) 2, b.readByte());
        assertEquals((byte) 3, b.readByte());
    }

    @Test
    void byteBufferAccessorBounds() {
        Buffer b = new Buffer();
        b.writeByte((byte) 0x0A);
        b.writeByte((byte) 0x0B);
        b.writeByte((byte) 0x0C);

        ByteBuffer bb = b.asByteBuffer();
        assertEquals(0, bb.position());
        assertEquals(3, bb.limit());
        assertEquals(3, bb.remaining());
        assertEquals((byte) 0x0C, bb.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> bb.get(3));
    }

    @Test
    void crc32UsesUpstreamTableAlgorithm() {
        Buffer b = new Buffer();
        b.setData("123456789".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals((int) 0x89A1897FL, b.getCRC32());

        Buffer empty = new Buffer();
        assertEquals(0, empty.getCRC32());

        Buffer seeded = new Buffer();
        seeded.setData("ABC".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals((int) 0xDE7ED2FDL, seeded.getCRC32(0x12345678));
    }

    @Test
    void webEncodingRoundTrip() {
        Buffer b = new Buffer();
        b.writeByte((byte) 0xAB);
        b.writeByte((byte) 0xCD);
        assertEquals("00000010fqA", b.toWebString());

        Buffer decoded = new Buffer();
        decoded.fromWebString("00000010fqA");
        assertEquals((byte) 0xAB, decoded.readByte());
        assertEquals((byte) 0xCD, decoded.readByte());
    }

    @Test
    void endMarkers() {
        Buffer b = new Buffer();
        b.writeNumBits(5, 3);
        assertEquals(1, b.getDataLen());
        assertEquals(3, b.getDataLenBits());

        b.seekFront();
        assertFalse(b.atEnd());

        b.readByte();
        assertTrue(b.atEnd());
        assertTrue(b.pastEnd());
    }

    @Test
    void dataPointerIsNullWhenEmpty() {
        Buffer b = new Buffer();
        assertNull(b.getDataPtr());
        b.writeByte((byte) 1);
        assertEquals(1, b.getDataPtr().length);
    }
}
