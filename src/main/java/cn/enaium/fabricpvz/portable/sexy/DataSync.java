package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/Lawn/System/DataSync.h:93-146 and src/Lawn/System/DataSync.cpp:168-381.
 *
 * <p>A bidirectional serialization helper: given a {@link DataReader} it reads values, given a
 * {@link DataWriter} it writes them; the same call site can therefore be used for both directions,
 * exactly like the upstream {@code DataSync}.
 *
 * <p>Java has no by-reference primitive arguments, so every {@code syncXxx} method takes the current
 * value and <em>returns</em> the synchronized one. When reading, the returned value is what came
 * from the stream; when writing, it is the argument unchanged. Ported call sites read as
 * {@code theField = theSync.syncUInt32(theField);}.
 *
 * <p>Wire format (little-endian, §2 of docs/research/03-save.md):
 * <pre>
 *   uint8  1 byte
 *   bool   1 byte (the C++ bool representation)
 *   uint16 2 bytes LE
 *   uint32 4 bytes LE
 *   uint64 8 bytes LE
 *   float  4 bytes LE (bit pattern written as uint32)
 *   double 8 bytes LE (bit pattern written as uint64)
 *   string uint16 byte length N, then N raw bytes
 * </pre>
 */
public class DataSync {

    protected DataReader mReader;
    protected DataWriter mWriter;
    protected int mVersion;

    /** Ported from DataSync.cpp:182-187. */
    public DataSync(DataReader theReader) {
        reset();
        mReader = theReader;
    }

    /** Ported from DataSync.cpp:189-194. */
    public DataSync(DataWriter theWriter) {
        reset();
        mWriter = theWriter;
    }

    public DataSync() {
        reset();
    }

    /** Ported from DataSync.cpp:200-204. */
    protected void reset() {
        mReader = null;
        mWriter = null;
    }

    public void setReader(DataReader theReader) {
        mReader = theReader;
    }

    public void setWriter(DataWriter theWriter) {
        mWriter = theWriter;
    }

    public DataReader getReader() {
        return mReader;
    }

    public DataWriter getWriter() {
        return mWriter;
    }

    /** Ported from DataSync.cpp:206-215. */
    public byte[] syncBytes(byte[] theData) {
        if (mReader != null) {
            mReader.readBytes(theData);
        } else {
            mWriter.writeBytes(theData);
        }
        return theData;
    }

    /** Ported from DataSync.cpp:206-215 with an explicit range (upstream {@code SyncBytes(data,len)}). */
    public byte[] syncBytes(byte[] theData, int theOffset, int theDataLen) {
        if (mReader != null) {
            mReader.readBytes(theData, theOffset, theDataLen);
        } else {
            mWriter.writeBytes(theData, theOffset, theDataLen);
        }
        return theData;
    }

    /** Ported from DataSync.cpp:217-228. */
    public long syncUInt64(long theNum) {
        if (mReader != null) {
            return mReader.readUInt64();
        }
        mWriter.writeUInt64(theNum);
        return theNum;
    }

    /** Ported from DataSync.cpp:230-241. */
    public int syncUInt32(int theNum) {
        if (mReader != null) {
            return mReader.readUInt32();
        }
        mWriter.writeUInt32(theNum);
        return theNum;
    }

    /** Ported from DataSync.cpp:243-318 (the char/short/long/unsigned overloads collapse to int). */
    public int syncUInt16(int theNum) {
        if (mReader != null) {
            return mReader.readUInt16();
        }
        mWriter.writeUInt16(theNum);
        return theNum;
    }

    /** Ported from DataSync.cpp:320-378 (the char/short/long/unsigned overloads collapse to int). */
    public int syncUInt8(int theChar) {
        if (mReader != null) {
            return mReader.readUInt8();
        }
        mWriter.writeUInt8(theChar);
        return theChar;
    }

    /** Ported from DataSync.cpp:380-389. */
    public boolean syncBool(boolean theBool) {
        if (mReader != null) {
            return mReader.readBool();
        }
        mWriter.writeBool(theBool);
        return theBool;
    }

    /** Ported from DataSync.cpp:391-400. */
    public float syncFloat(float theFloat) {
        if (mReader != null) {
            return mReader.readFloat();
        }
        mWriter.writeFloat(theFloat);
        return theFloat;
    }

    /** Ported from DataSync.cpp:402-411. */
    public double syncDouble(double theDouble) {
        if (mReader != null) {
            return mReader.readDouble();
        }
        mWriter.writeDouble(theDouble);
        return theDouble;
    }

    /** Ported from DataSync.cpp:413-422. */
    public String syncString(String theStr) {
        if (mReader != null) {
            return mReader.readString();
        }
        mWriter.writeString(theStr);
        return theStr;
    }

    /** Ported from DataSync.h:140. */
    public void setVersion(int theVersion) {
        mVersion = theVersion;
    }

    /** Ported from DataSync.h:141. */
    public int getVersion() {
        return mVersion;
    }
}
