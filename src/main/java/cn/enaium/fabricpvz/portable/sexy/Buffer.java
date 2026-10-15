package cn.enaium.fabricpvz.portable.sexy;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Ported from src/SexyAppFramework/misc/Buffer.h:36 and src/SexyAppFramework/misc/Buffer.cpp:96.
 *
 * <p>Faithful port of {@code Sexy::Buffer}: a bit-addressable byte vector with the framework's
 * web-safe encoding, CRC-32 and text-decoding helpers.
 *
 * <p>The upstream {@code ByteVector mData} is modelled by a growable {@code byte[]} plus an
 * explicit length ({@link #getDataLen()} / {@link #mDataBitSize}); the vector's byte size
 * (which upstream's {@code mData.size()} reports and {@link #getCRC32(int)} covers) is
 * available through {@link #asByteBuffer()} and {@link #getDataPtr()}.
 */
public class Buffer {
    private static final int POLYNOMIAL = 0x04c11db7;

    private static final String WEB_ENCODE_MAP = ".-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int[] WEB_DECODE_MAP = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, 0, -1, 1, 0, -1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, -1, -1,
        -1, -1, -1, -1, -1, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, -1, -1, -1, -1, -1, -1, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60,
        61, 62, 63, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };

    // Windows-1252 bytes 0x80-0x9F to Unicode codepoints (SDL_iconv mishandles this under Wine)
    private static final char[] WIN1252_TO_UNICODE = {
        0x20AC, 0x0081, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
        0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008D, 0x017D, 0x008F,
        0x0090, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
        0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x009D, 0x017E, 0x0178
    };

    private static final int[] CRC_TABLE = generateCrcTable();

    private byte[] mData = new byte[0];
    private int mDataLength;
    public int mDataBitSize;
    public int mReadBitPos;
    public int mWriteBitPos;

    public Buffer() {
        mDataBitSize = 0;
        mReadBitPos = 0;
        mWriteBitPos = 0;
    }

    // Generate the table of CRC remainders for all possible bytes.
    private static int[] generateCrcTable() {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int crcAccum = i << 24;
            for (int j = 0; j < 8; j++) {
                if ((crcAccum & 0x80000000) != 0) {
                    crcAccum = (crcAccum << 1) ^ POLYNOMIAL;
                } else {
                    crcAccum = (crcAccum << 1);
                }
            }
            table[i] = crcAccum;
        }
        return table;
    }

    // Update the CRC on the data block one byte at a time.
    private static int updateCRC(int crcAccum, byte[] dataBlkPtr, int dataBlkSize) {
        for (int j = 0; j < dataBlkSize; j++) {
            int i = ((crcAccum >>> 24) ^ dataBlkPtr[j]) & 0xff;
            crcAccum = (crcAccum << 8) ^ CRC_TABLE[i];
        }
        return crcAccum;
    }

    private void ensureCapacity(int needed) {
        if (needed > mData.length) {
            int newCap = Math.max(needed, Math.max(32, mData.length * 2));
            mData = Arrays.copyOf(mData, newCap);
        }
    }

    private void push(byte b) {
        ensureCapacity(mDataLength + 1);
        mData[mDataLength++] = b;
    }

    // ToWebString
    public String toWebString() {
        StringBuilder aString = new StringBuilder();
        int aSizeBits = mWriteBitPos;

        int anOldReadBitPos = mReadBitPos;
        mReadBitPos = 0;

        aString.append(String.format(java.util.Locale.ROOT, "%08X", aSizeBits));

        int aNumChars = (aSizeBits + 5) / 6;
        for (int aCharNum = 0; aCharNum < aNumChars; aCharNum++) {
            aString.append(WEB_ENCODE_MAP.charAt(readNumBits(6, false)));
        }

        mReadBitPos = anOldReadBitPos;

        return aString.toString();
    }

    private static String win1252ToUTF8(byte[] theData, int theLen) {
        StringBuilder aResult = new StringBuilder(theLen);
        for (int i = 0; i < theLen; ++i) {
            int aByte = theData[i] & 0xFF;
            char aCodepoint;
            if (aByte < 0x80) {
                aCodepoint = (char) aByte;
            } else if (aByte <= 0x9F) {
                aCodepoint = WIN1252_TO_UNICODE[aByte - 0x80];
            } else {
                aCodepoint = (char) aByte; // 0xA0-0xFF map directly to U+00A0-U+00FF
            }
            aResult.append(aCodepoint);
        }
        return aResult.toString();
    }

    private static boolean isValidUTF8(byte[] theData, int theLen) {
        int p = 0;
        int anEnd = theLen;
        while (p < anEnd) {
            int aFirst = theData[p] & 0xFF;
            int aSeqLen;
            if (aFirst < 0x80) {
                ++p;
                continue;
            } else if ((aFirst & 0xE0) == 0xC0 && aFirst >= 0xC2) {
                aSeqLen = 2;
            } else if ((aFirst & 0xF0) == 0xE0) {
                aSeqLen = 3;
            } else if ((aFirst & 0xF8) == 0xF0 && aFirst <= 0xF4) {
                aSeqLen = 4;
            } else {
                return false;
            }

            if (p + aSeqLen > anEnd) {
                return false;
            }
            for (int i = 1; i < aSeqLen; ++i) {
                if ((theData[p + i] & 0xC0) != 0x80) {
                    return false;
                }
            }

            // Reject overlong encodings and surrogates
            int b1 = theData[p + 1] & 0xFF;
            if (aSeqLen == 3 && aFirst == 0xE0 && b1 < 0xA0) {
                return false;
            }
            if (aSeqLen == 3 && aFirst == 0xED && b1 > 0x9F) {
                return false;
            }
            if (aSeqLen == 4 && aFirst == 0xF0 && b1 < 0x90) {
                return false;
            }
            if (aSeqLen == 4 && aFirst == 0xF4 && b1 > 0x8F) {
                return false;
            }
            p += aSeqLen;
        }
        return true;
    }

    /**
     * Ported from src/SexyAppFramework/misc/Buffer.cpp:181. Returns {@code null} where upstream
     * returns {@code false} (only possible when the UTF-16 iconv conversion fails).
     */
    public String toUTF8String() {
        byte[] aData = mData;
        int aLen = getDataLen();

        if (aLen >= 3 && aData[0] == (byte) 0xEF && aData[1] == (byte) 0xBB && aData[2] == (byte) 0xBF) {
            // UTF-8 BOM: strip it
            return new String(aData, 3, aLen - 3, StandardCharsets.UTF_8);
        }

        if (aLen >= 2 && aData[0] == (byte) 0xFF && aData[1] == (byte) 0xFE) {
            if ((aLen - 2) % 2 != 0) {
                return null;
            }
            return decodeUtf16(aData, 2, aLen - 2, StandardCharsets.UTF_16LE);
        } else if (aLen >= 2 && aData[0] == (byte) 0xFE && aData[1] == (byte) 0xFF) {
            if ((aLen - 2) % 2 != 0) {
                return null;
            }
            return decodeUtf16(aData, 2, aLen - 2, StandardCharsets.UTF_16BE);
        } else if (isValidUTF8(aData, aLen)) {
            return new String(aData, 0, aLen, StandardCharsets.UTF_8);
        } else {
            return win1252ToUTF8(aData, aLen);
        }
    }

    private static String decodeUtf16(byte[] theData, int off, int len, java.nio.charset.Charset charset) {
        var decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(theData, off, len)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    // FromWebString
    public void fromWebString(String theString) {
        clear();

        if (theString.length() < 4) {
            return;
        }

        int aSizeBits = 0;

        for (int aDigitNum = 0; aDigitNum < 8; aDigitNum++) {
            char aChar = theString.charAt(aDigitNum);
            int aVal = 0;

            if ((aChar >= '0') && (aChar <= '9')) {
                aVal = aChar - '0';
            } else if ((aChar >= 'A') && (aChar <= 'F')) {
                aVal = (aChar - 'A') + 10;
            } else if ((aChar >= 'a') && (aChar <= 'f')) {
                aVal = (aChar - 'a') + 10;
            }

            aSizeBits += (aVal << ((7 - aDigitNum) * 4));
        }

        int aCharIdx = 8;
        int aNumBitsLeft = aSizeBits;
        while (aNumBitsLeft > 0) {
            int aChar = theString.charAt(aCharIdx++);
            int aVal = WEB_DECODE_MAP[aChar & 0xFF];
            // PORT-NOTE: characters outside the alphabet yield -1 from the table (note upstream's
            // table also maps '+' to 0, which the literal above preserves), so WriteNumBits then
            // stores all-one bits exactly as C++ does.
            int aNumBits = Math.min(aNumBitsLeft, 6);
            writeNumBits(aVal, aNumBits);
            aNumBitsLeft -= aNumBits;
        }

        seekFront();
    }

    // SeekFront
    public void seekFront() {
        mReadBitPos = 0;
    }

    // Clear
    public void clear() {
        mReadBitPos = 0;
        mWriteBitPos = 0;
        mDataBitSize = 0;
        mDataLength = 0;
    }

    // WriteByte
    public void writeByte(byte theByte) {
        if (mWriteBitPos % 8 == 0) {
            push(theByte);
        } else {
            int anOfs = mWriteBitPos % 8;
            mData[mWriteBitPos / 8] = (byte) (mData[mWriteBitPos / 8] | ((theByte & 0xFF) << anOfs));
            push((byte) ((theByte & 0xFF) >> (8 - anOfs)));
        }

        mWriteBitPos += 8;
        mDataBitSize = Math.max(mDataBitSize, mWriteBitPos);
    }

    // WriteNumBits
    public void writeNumBits(int theNum, int theBits) {
        for (int aBitNum = 0; aBitNum < theBits; aBitNum++) {
            if (mWriteBitPos % 8 == 0) {
                push((byte) 0);
            }
            if ((theNum & (1 << aBitNum)) != 0) {
                mData[mWriteBitPos / 8] = (byte) (mData[mWriteBitPos / 8] | (1 << (mWriteBitPos % 8)));
            }
            mWriteBitPos++;
        }

        mDataBitSize = Math.max(mDataBitSize, mWriteBitPos);
    }

    // GetBitsRequired
    public static int getBitsRequired(int theNum, boolean isSigned) {
        if (theNum < 0) { // two's compliment stuff
            theNum = -theNum - 1;
        }

        int aNumBits = 0;
        while (theNum >= 1 << aNumBits) {
            aNumBits++;
        }

        if (isSigned) {
            aNumBits++;
        }

        return aNumBits;
    }

    // WriteBoolean
    public void writeBoolean(boolean theBool) {
        writeByte((byte) (theBool ? 1 : 0));
    }

    // WriteShort
    public void writeShort(short theShort) {
        writeByte((byte) theShort);
        writeByte((byte) (theShort >> 8));
    }

    // WriteUInt32
    public void writeUInt32(int theValue) {
        writeByte((byte) theValue);
        writeByte((byte) (theValue >>> 8));
        writeByte((byte) (theValue >>> 16));
        writeByte((byte) (theValue >>> 24));
    }

    // WriteInt32
    public void writeInt32(int theValue) {
        writeUInt32(theValue);
    }

    // WriteString
    public void writeString(String theString) {
        byte[] aBytes = theString.getBytes(StandardCharsets.ISO_8859_1);
        writeShort((short) aBytes.length);
        for (int i = 0; i < aBytes.length; i++) {
            writeByte(aBytes[i]);
        }
    }

    // WriteLine
    public void writeLine(String theString) {
        byte[] aBytes = (theString + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        writeBytes(aBytes, aBytes.length);
    }

    // WriteBuffer
    public void writeBuffer(byte[] theBuffer) {
        writeUInt32(theBuffer.length);
        for (byte aByte : theBuffer) {
            writeByte(aByte);
        }
    }

    // WriteBytes
    public void writeBytes(byte[] theByte, int theCount) {
        for (int i = 0; i < theCount; i++) {
            writeByte(theByte[i]);
        }
    }

    // SetData(const ByteVector&)
    public void setData(byte[] theBuffer) {
        mData = Arrays.copyOf(theBuffer, theBuffer.length);
        mDataLength = mData.length;
        mDataBitSize = mDataLength * 8;
    }

    // SetData(const uchar*, int)
    public void setData(byte[] thePtr, int theCount) {
        mData = Arrays.copyOf(thePtr, theCount);
        mDataLength = theCount;
        mDataBitSize = mDataLength * 8;
    }

    // ReadByte
    public byte readByte() {
        if ((mReadBitPos + 7) / 8 >= mDataLength) {
            return 0; // Underflow
        }

        if (mReadBitPos % 8 == 0) {
            byte b = mData[mReadBitPos / 8];
            mReadBitPos += 8;
            return b;
        } else {
            int anOfs = mReadBitPos % 8;

            byte b;

            b = (byte) ((mData[mReadBitPos / 8] & 0xFF) >> anOfs);
            b |= (byte) (mData[(mReadBitPos / 8) + 1] << (8 - anOfs));

            mReadBitPos += 8;

            return b;
        }
    }

    // ReadNumBits
    public int readNumBits(int theBits, boolean isSigned) {
        int aByteLength = mDataLength;

        int theNum = 0;
        boolean bset = false;
        for (int aBitNum = 0; aBitNum < theBits; aBitNum++) {
            int aBytePos = mReadBitPos / 8;

            if (aBytePos >= aByteLength) {
                break;
            }

            if ((bset = (mData[aBytePos] & (1 << (mReadBitPos % 8))) != 0)) {
                theNum |= 1 << aBitNum;
            }

            mReadBitPos++;
        }

        if (isSigned && bset) { // sign extend
            for (int aBitNum = theBits; aBitNum < 32; aBitNum++) {
                theNum |= 1 << aBitNum;
            }
        }

        return theNum;
    }

    // ReadBoolean
    public boolean readBoolean() {
        return readByte() != 0;
    }

    // ReadShort
    public short readShort() {
        // PORT-NOTE: upstream ReadByte returns uchar, so the first byte must be zero-extended here.
        short aShort = (short) (readByte() & 0xFF);
        aShort |= (short) (readByte() << 8);
        return aShort;
    }

    // ReadUInt32
    public int readUInt32() {
        int aValue = readByte() & 0xFF;
        aValue |= (readByte() & 0xFF) << 8;
        aValue |= (readByte() & 0xFF) << 16;
        aValue |= (readByte() & 0xFF) << 24;

        return aValue;
    }

    // ReadInt32
    public int readInt32() {
        return readUInt32();
    }

    // ReadString
    public String readString() {
        byte[] aBytes;

        int aLen = readShort();

        aBytes = new byte[Math.max(aLen, 0)];
        for (int i = 0; i < aLen; i++) {
            aBytes[i] = readByte();
        }

        return new String(aBytes, StandardCharsets.ISO_8859_1);
    }

    // ReadLine
    public String readLine() {
        StringBuilder aString = new StringBuilder();

        for (; ; ) {
            byte c = readByte();

            if ((c == 0) || (c == '\n')) {
                break;
            }

            if (c != '\r') {
                aString.append((char) (c & 0xFF));
            }
        }

        return aString.toString();
    }

    // ReadBytes
    public void readBytes(byte[] theData, int theLen) {
        for (int i = 0; i < theLen; i++) {
            theData[i] = readByte();
        }
    }

    // ReadBuffer
    public byte[] readBuffer() {
        int aLength = readUInt32();
        byte[] theByteVector = new byte[aLength];
        readBytes(theByteVector, aLength);
        return theByteVector;
    }

    // GetDataPtr
    public byte[] getDataPtr() {
        if (mDataLength == 0) {
            return null;
        }
        return Arrays.copyOf(mData, mDataLength);
    }

    // GetDataLen
    public int getDataLen() {
        return (mDataBitSize + 7) / 8; // Round up
    }

    // GetDataLenBits
    public int getDataLenBits() {
        return mDataBitSize;
    }

    // GetCRC32
    public int getCRC32(int theSeed) {
        int aCRC = theSeed;
        aCRC = updateCRC(aCRC, mData, mDataLength);
        return aCRC;
    }

    public int getCRC32() {
        return getCRC32(0);
    }

    /** A {@link ByteBuffer} view over the buffer's raw bytes ({@code mData.size()} bytes). */
    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(mData, 0, mDataLength);
    }

    // AtEnd
    public boolean atEnd() {
        //return mReadBitPos >= (int)mData.size()*8;
        return mReadBitPos >= mDataBitSize;
    }

    // PastEnd
    public boolean pastEnd() {
        return mReadBitPos > mDataBitSize;
    }
}
