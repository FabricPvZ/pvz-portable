package cn.enaium.fabricpvz.portable.sexy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import cn.enaium.fabricpvz.portable.sexy.pak.PakSource;

/**
 * Ported from src/Lawn/System/DataSync.h:28-45 and src/Lawn/System/DataSync.cpp:32-175.
 *
 * <p>Little-endian primitive reader used by every save file ({@code users.dat},
 * {@code user{id}.dat}, {@code game{id}_{mode}.v4}). A reader is backed either by a real file
 * ({@link #openFile}) or by a memory buffer ({@link #openMemory}).
 *
 * <p>All multi-byte values are read little-endian, independent of host endianness (upstream applies
 * {@code FromLE16/32/64}, Common.h:449-452). Strings are stored as a {@code uint16} byte-length
 * followed by that many raw bytes with no NUL terminator; the raw bytes are preserved 1:1 through
 * ISO-8859-1 so a read/write round-trip is byte-identical.
 *
 * <p>Upstream {@code DataReader::Rewind} only rewinds the memory cursor ({@code mDataPos}); the
 * same restriction is kept here.
 */
public class DataReader {

    protected RandomAccessFile mFile;
    protected byte[] mData;
    protected int mDataLen;
    protected int mDataPos;

    public DataReader() {
        mFile = null;
        mData = null;
        mDataLen = 0;
        mDataPos = 0;
    }

    /** Ported from DataSync.cpp:44-49. Opens a file for reading, returning false on failure. */
    public boolean openFile(String theFileName) {
        mDataPos = 0;
        close();
        try {
            // Upstream opens through fcaseopen, i.e. with every path component folded to the spelling
            // the file system holds; the pak interface owns that fold (PakSource.resolveCaseInsensitive),
            // so this does not carry a second, narrower copy of it.
            Path aResolved = PakSource.resolveCaseInsensitive(Path.of(theFileName));
            mFile = aResolved == null ? null : new RandomAccessFile(aResolved.toFile(), "r");
        } catch (IOException e) {
            mFile = null;
        }
        return mFile != null;
    }

    /** Ported from DataSync.cpp:51-67. Uses {@code theData} as the backing buffer. */
    public void openMemory(byte[] theData) {
        openMemory(theData, false);
    }

    /**
     * Ported from DataSync.cpp:51-67. {@code takeOwnership} is a C++ ownership flag with no Java
     * equivalent; it is accepted so ported call sites keep the upstream argument list.
     */
    public void openMemory(byte[] theData, boolean takeOwnership) {
        close();
        mData = theData;
        mDataLen = theData.length;
        mDataPos = 0;
    }

    /** Ported from DataSync.cpp:69-76. */
    public void close() {
        if (mFile != null) {
            try {
                mFile.close();
            } catch (IOException ignored) {
                // Closing a read-only handle cannot lose data.
            }
            mFile = null;
        }
    }

    /** Ported from DataSync.cpp:78-94. Reads exactly {@code theNumBytes} bytes into {@code theMem}. */
    public void readBytes(byte[] theMem) {
        readBytes(theMem, 0, theMem.length);
    }

    /** Ported from DataSync.cpp:78-94. Reads {@code theNumBytes} bytes at {@code theOffset}. */
    public void readBytes(byte[] theMem, int theOffset, int theNumBytes) {
        if (mData != null) {
            mDataPos += theNumBytes;
            if (mDataPos > mDataLen) {
                throw new DataReaderException();
            }
            System.arraycopy(mData, mDataPos - theNumBytes, theMem, theOffset, theNumBytes);
        } else if (mFile == null) {
            throw new DataReaderException();
        } else {
            try {
                mFile.readFully(theMem, theOffset, theNumBytes);
            } catch (IOException e) {
                throw new DataReaderException("Failed to read " + theNumBytes + " bytes", e);
            }
        }
    }

    /** Ported from DataSync.cpp:96-101. */
    public void rewind(int theNumBytes) {
        int aNumBytes = Math.min(theNumBytes, mDataPos);
        mDataPos -= aNumBytes;
    }

    /** Ported from DataSync.cpp:103-108. */
    public int readUInt16() {
        byte[] aBuf = new byte[2];
        readBytes(aBuf);
        return (aBuf[0] & 0xFF) | ((aBuf[1] & 0xFF) << 8);
    }

    /** Ported from DataSync.cpp:110-115. Returns the raw 32-bit two's-complement value. */
    public int readUInt32() {
        byte[] aBuf = new byte[4];
        readBytes(aBuf);
        return (aBuf[0] & 0xFF)
                | ((aBuf[1] & 0xFF) << 8)
                | ((aBuf[2] & 0xFF) << 16)
                | ((aBuf[3] & 0xFF) << 24);
    }

    /** Ported from DataSync.cpp:117-122. */
    public long readUInt64() {
        byte[] aBuf = new byte[8];
        readBytes(aBuf);
        long aValue = 0;
        for (int i = 7; i >= 0; i--) {
            aValue = (aValue << 8) | (aBuf[i] & 0xFFL);
        }
        return aValue;
    }

    /** Ported from DataSync.cpp:124-129. */
    public int readUInt8() {
        byte[] aBuf = new byte[1];
        readBytes(aBuf);
        return aBuf[0] & 0xFF;
    }

    /** Ported from DataSync.cpp:131-136. Upstream reads the single byte of a C++ {@code bool}. */
    public boolean readBool() {
        return readUInt8() != 0;
    }

    /** Ported from DataSync.cpp:138-147. */
    public float readFloat() {
        return Float.intBitsToFloat(readUInt32());
    }

    /** Ported from DataSync.cpp:149-158. */
    public double readDouble() {
        return Double.longBitsToDouble(readUInt64());
    }

    /** Ported from DataSync.cpp:160-166. {@code uint16} byte length then raw bytes. */
    public String readString() {
        int aStrLen = readUInt16();
        byte[] aBytes = new byte[aStrLen];
        readBytes(aBytes);
        return new String(aBytes, StandardCharsets.ISO_8859_1);
    }

    /** Current cursor position (memory buffer only; mirrors upstream {@code mDataPos}). */
    public int getDataPos() {
        return mDataPos;
    }
}
