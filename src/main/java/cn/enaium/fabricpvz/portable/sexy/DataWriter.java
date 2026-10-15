package cn.enaium.fabricpvz.portable.sexy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Ported from src/Lawn/System/DataSync.h:53-91 and src/Lawn/System/DataSync.cpp:383-546.
 *
 * <p>Little-endian primitive writer paired with {@link DataReader}. A writer is backed either by a
 * real file ({@link #openFile}) or by an in-memory buffer ({@link #openMemory}); the in-memory form
 * is what save code uses to build a buffer and then hand it to {@link #writeToFile}.
 *
 * <p>All multi-byte values are written little-endian (upstream {@code ToLE16/32/64},
 * Common.h:449-452). Strings are a {@code uint16} byte length followed by the raw ISO-8859-1 bytes;
 * lengths above 65535 are truncated exactly as upstream does.
 */
public class DataWriter {

    /** Upstream default memory reserve: {@code OpenMemory(uint32_t theReserveAmount = 0x20)}. */
    public static final int DEFAULT_RESERVE = 0x20;

    protected OutputStream mFile;
    protected ByteArrayOutputStream mData;
    protected int mFilePos;

    public DataWriter() {
        mFile = null;
        mData = null;
        mFilePos = 0;
    }

    /** Ported from DataSync.cpp:396-400. Opens (creates/truncates) a file for writing. */
    public boolean openFile(String theFileName) {
        close();
        try {
            Path path = Path.of(theFileName);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            mFile = Files.newOutputStream(
                    path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            mFile = null;
        }
        mFilePos = 0;
        return mFile != null;
    }

    /** Ported from DataSync.cpp:402-409. */
    public void close() {
        if (mFile != null) {
            try {
                mFile.close();
            } catch (IOException ignored) {
                // Nothing else can be done on a close failure.
            }
            mFile = null;
        }
    }

    /** Ported from DataSync.cpp:411-420. */
    public void openMemory() {
        openMemory(DEFAULT_RESERVE);
    }

    /** Ported from DataSync.cpp:411-420. */
    public void openMemory(int theReserveAmount) {
        close();
        mData = new ByteArrayOutputStream(Math.max(theReserveAmount, 32));
        mFilePos = 0;
    }

    /** Ported from DataSync.cpp:422-432. */
    public void writeBytes(byte[] theData) {
        writeBytes(theData, 0, theData.length);
    }

    /** Ported from DataSync.cpp:422-432. */
    public void writeBytes(byte[] theData, int theOffset, int theDataLen) {
        if (mFile != null) {
            try {
                mFile.write(theData, theOffset, theDataLen);
                mFilePos += theDataLen;
            } catch (IOException e) {
                throw new DataReaderException("Failed to write " + theDataLen + " bytes", e);
            }
        } else if (mData != null) {
            mData.write(theData, theOffset, theDataLen);
        } else {
            throw new IllegalStateException("DataWriter is not open");
        }
    }

    /** Ported from DataSync.cpp:486-490. */
    public void writeUInt32(int theUInt32) {
        writeBytes(new byte[] {
            (byte) theUInt32,
            (byte) (theUInt32 >>> 8),
            (byte) (theUInt32 >>> 16),
            (byte) (theUInt32 >>> 24)
        });
    }

    /** Ported from DataSync.cpp:492-496. */
    public void writeUInt64(long theUInt64) {
        byte[] aBuf = new byte[8];
        for (int i = 0; i < 8; i++) {
            aBuf[i] = (byte) (theUInt64 >>> (8 * i));
        }
        writeBytes(aBuf);
    }

    /** Ported from DataSync.cpp:498-502. */
    public void writeUInt16(int theUInt16) {
        writeBytes(new byte[] {(byte) theUInt16, (byte) (theUInt16 >>> 8)});
    }

    /** Ported from DataSync.cpp:504-507. */
    public void writeUInt8(int theUInt8) {
        writeBytes(new byte[] {(byte) theUInt8});
    }

    /** Ported from DataSync.cpp:509-512. Upstream writes the single byte of a C++ {@code bool}. */
    public void writeBool(boolean theBool) {
        writeUInt8(theBool ? 1 : 0);
    }

    /** Ported from DataSync.cpp:514-519. */
    public void writeFloat(float theFloat) {
        writeUInt32(Float.floatToRawIntBits(theFloat));
    }

    /** Ported from DataSync.cpp:521-526. */
    public void writeDouble(double theDouble) {
        writeUInt64(Double.doubleToRawLongBits(theDouble));
    }

    /** Ported from DataSync.cpp:528-533. {@code uint16} byte length then raw bytes. */
    public void writeString(String theStr) {
        byte[] aBytes = theStr.getBytes(StandardCharsets.ISO_8859_1);
        int aStrLen = Math.min(aBytes.length, 0xFFFF);
        writeUInt16(aStrLen);
        writeBytes(aBytes, 0, aStrLen);
    }

    /** Ported from DataSync.h:77 (inline). Flushes the in-memory buffer to a file. */
    public boolean writeToFile(String theFileName) {
        if (mData == null) {
            return false;
        }
        try {
            Path path = Path.of(theFileName);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, mData.toByteArray());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Ported from DataSync.h:87 (inline). */
    public int getPos() {
        if (mData != null) {
            return mData.size();
        }
        return mFilePos;
    }

    /** Ported from DataSync.h:88. The accumulated in-memory buffer. */
    public byte[] getDataPtr() {
        return mData == null ? new byte[0] : mData.toByteArray();
    }

    /** Ported from DataSync.h:89. */
    public int getDataLen() {
        return mData == null ? 0 : mData.size();
    }
}
