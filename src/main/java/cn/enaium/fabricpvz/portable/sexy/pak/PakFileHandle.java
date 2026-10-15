package cn.enaium.fabricpvz.portable.sexy.pak;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Ported from src/SexyAppFramework/paklib/PakInterface.h (struct PFILE) and the
 * corresponding PakInterface::F* methods.
 *
 * <p>FILE*-like handle that either reads from a pak member ({@code mRecord != null})
 * or from a real file on disk ({@code mFileHandle != null}), mirroring upstream's
 * FOpen/FClose/FSeek/FTell/FRead/FGetC/UnGetC/FGetS/FEof.
 *
 * <p>Upstream quirks preserved on purpose:
 * <ul>
 *   <li>{@code FSeek} with SEEK_END computes {@code size - offset} (not {@code size + offset}).</li>
 *   <li>{@code FGetC}/{@code FGetS} silently drop '\r' characters and stop {@code FGetS} at '\n'.</li>
 *   <li>{@code UnGetC} rewinds one byte unconditionally (upstream comment says it is only valid
 *       when pushing back the same char).</li>
 * </ul>
 */
public final class PakFileHandle implements AutoCloseable {

    public static final int SEEK_SET = 0;
    public static final int SEEK_CUR = 1;
    public static final int SEEK_END = 2;

    /** Upstream PFILE::mRecord */
    public PakRecord mRecord;

    /** Upstream PFILE::mPos */
    public int mPos;

    /** Upstream PFILE::mFP */
    private RandomAccessFile mFileHandle;

    private final byte[] mSingle = new byte[1];
    private boolean mClosed;

    PakFileHandle(PakRecord record) {
        this.mRecord = record;
    }

    PakFileHandle(RandomAccessFile file) {
        this.mFileHandle = file;
    }

    public boolean isPakMember() {
        return mRecord != null;
    }

    /** Upstream PakInterface::FSeek */
    public int seek(long offset, int origin) throws IOException {
        if (mRecord != null) {
            long pos = switch (origin) {
                case SEEK_SET -> offset;
                case SEEK_END -> mRecord.mSize - offset;
                case SEEK_CUR -> mPos + offset;
                default -> mPos;
            };
            mPos = (int) Math.clamp(pos, 0, mRecord.mSize);
            return 0;
        }
        mFileHandle.seek(offset);
        return 0;
    }

    /** Upstream PakInterface::FTell */
    public int tell() throws IOException {
        if (mRecord != null) {
            return mPos;
        }
        return (int) mFileHandle.getFilePointer();
    }

    /** Upstream PakInterface::FRead (element-wise count semantics are applied by the caller). */
    public int read(byte[] dest, int offset, int len) throws IOException {
        if (mRecord != null) {
            int read = mRecord.readBytes(mPos, dest, offset, len);
            mPos += read;
            return read;
        }
        return mFileHandle.read(dest, offset, len);
    }

    /** Number of bytes left in the handle (only meaningful for pak members; upstream FEof). */
    public boolean eof() throws IOException {
        if (mRecord != null) {
            return mPos >= mRecord.mSize;
        }
        return mFileHandle.getFilePointer() >= mFileHandle.length();
    }

    public int size() throws IOException {
        if (mRecord != null) {
            return mRecord.mSize;
        }
        return (int) mFileHandle.length();
    }

    /** Upstream PakInterface::FGetC (skips '\r'; returns -1 at EOF). */
    public int getc() throws IOException {
        if (mRecord != null) {
            for (; ; ) {
                if (mPos >= mRecord.mSize) {
                    return -1;
                }
                if (read(mSingle, 0, 1) == 0) {
                    return -1;
                }
                if (mSingle[0] != '\r') {
                    return mSingle[0] & 0xFF;
                }
            }
        }
        return mFileHandle.read();
    }

    /** Upstream PakInterface::UnGetC */
    public int ungetc(int ch) throws IOException {
        if (mRecord != null) {
            mPos = Math.max(mPos - 1, 0);
            return ch;
        }
        long pos = mFileHandle.getFilePointer();
        if (pos > 0) {
            mFileHandle.seek(pos - 1);
        }
        return ch;
    }

    /** Reads one line like upstream FGetS: at most size-1 chars, '\r' dropped, '\n' terminates. */
    public String gets(int maxLength) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < maxLength - 1) {
            if (eof()) {
                return sb.isEmpty() ? null : sb.toString();
            }
            int ch = readRawByte();
            if (ch < 0) {
                break;
            }
            if (ch != '\r') {
                sb.append((char) ch);
            }
            if (ch == '\n') {
                break;
            }
        }
        return sb.toString();
    }

    private int readRawByte() throws IOException {
        if (mRecord != null) {
            if (read(mSingle, 0, 1) == 0) {
                return -1;
            }
            return mSingle[0] & 0xFF;
        }
        return mFileHandle.read();
    }

    /** Reads the whole member/file contents. */
    public byte[] readAllBytes() throws IOException {
        if (mRecord != null) {
            byte[] out = new byte[mRecord.mSize - mPos];
            int read = read(out, 0, out.length);
            if (read != out.length) {
                throw new EOFException("short read from pak member " + mRecord.mFileName);
            }
            return out;
        }
        byte[] out = new byte[Math.max(0, size() - mPos)];
        int read = 0;
        while (read < out.length) {
            int n = read(out, read, out.length - read);
            if (n <= 0) {
                break;
            }
            read += n;
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        if (mClosed) {
            return;
        }
        mClosed = true;
        if (mFileHandle != null) {
            mFileHandle.close();
        }
    }
}
