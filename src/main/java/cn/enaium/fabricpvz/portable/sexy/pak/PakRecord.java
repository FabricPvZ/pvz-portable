package cn.enaium.fabricpvz.portable.sexy.pak;

/**
 * Ported from src/SexyAppFramework/paklib/PakInterface.h (class PakRecord).
 *
 * <p>Describes one resource file inside a pak: name, start position, size, timestamp.
 * Field names keep the upstream {@code m} prefix by convention of this port.
 */
public final class PakRecord {

    /** The pak this record belongs to. */
    public PakSource mCollection;

    /** Path inside the pak, upper-cased with '/' separators (see {@link PakInterface#normalizePakPath}). */
    public String mFileName;

    /** Upstream mFileTime (+0x20): timestamp stored in the pak entry. */
    public long mFileTime;

    /** Upstream mStartPos (+0x28): offset of the file data inside the (de-obfuscated) pak. */
    public int mStartPos;

    /** Upstream mSize (+0x2C): size in bytes. */
    public int mSize;

    /**
     * Reads a byte range of this member.
     *
     * @return the number of bytes actually available (clamped like upstream ReadRecordBytes).
     */
    public int readBytes(int position, byte[] dest, int destOffset, int len) {
        int available = Math.min(len, mSize - position);
        if (available <= 0) {
            return 0;
        }
        mCollection.readBytes((long) mStartPos + position, dest, destOffset, available);
        return available;
    }
}
