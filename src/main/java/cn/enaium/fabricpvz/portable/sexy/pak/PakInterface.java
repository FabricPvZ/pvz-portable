package cn.enaium.fabricpvz.portable.sexy.pak;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/paklib/PakInterface.cpp and PakInterface.h.
 *
 * <p>Mounts .pak containers (e.g. {@code main.pak}) and resolves resource names to
 * pak members. Files that are not inside a pak fall back to the file system,
 * looked up relative to the configured resource folder (upstream {@code Sexy::GetResourceFolder()}).
 *
 * <p>Container format (all little-endian, whole file XOR-obfuscated with 0xF7):
 * <pre>
 *   uint32 magic       == 0xBAC04AC0
 *   uint32 version     == 0 (must be 0)
 *   repeat:
 *     uint8  flags      (0x80 => end of table)
 *     uint8  nameWidth
 *     char[nameWidth] name   ('\\' is normalized to '/')
 *     int32  srcSize         (size of the member data)
 *     int64  fileTime
 *   data blob (member contents in table order; header size is added to every member offset)
 * </pre>
 */
public final class PakInterface {

    public static final int PAK_MAGIC = 0xBAC04AC0;
    private static final int FILEFLAGS_END = 0x80;
    private static final int MAX_NAME_LENGTH = 255;

    private static PakInterface sInstance;

    private final List<PakSource> mPakCollectionList = new ArrayList<>();
    private final Map<String, PakRecord> mPakRecordMap = new LinkedHashMap<>();

    /** Upstream Sexy::GetResourceFolder(): directory containing main.pak / properties. */
    private String mResourceFolder = "";

    public PakInterface() {
    }

    /** Upstream global {@code gPakInterface}. */
    public static synchronized PakInterface getInstance() {
        if (sInstance == null) {
            sInstance = new PakInterface();
        }
        return sInstance;
    }

    public void setResourceFolder(String resourceFolder) {
        this.mResourceFolder = resourceFolder == null ? "" : resourceFolder;
    }

    public String getResourceFolder() {
        return mResourceFolder;
    }

    /** Upstream PakInterface::NormalizePakPath */
    public String normalizePakPath(String fileName) {
        String path = fileName.replace('\\', '/');

        if (isPathRooted(path)) {
            String resourceFolder = getResourceFolder().replace('\\', '/');
            if (!resourceFolder.isEmpty()) {
                String folder = resourceFolder.endsWith("/") ? resourceFolder : resourceFolder + "/";
                if (path.startsWith(folder)) {
                    path = path.substring(folder.length());
                } else if (path.equals(resourceFolder)) {
                    path = "";
                }
            }
        }

        path = lexicalNormal(path);
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        return path.toUpperCase(Locale.ROOT);
    }

    private static boolean isPathRooted(String path) {
        if (path.isEmpty()) {
            return false;
        }
        if (path.charAt(0) == '/' || path.charAt(0) == '\\') {
            return true;
        }
        return path.length() >= 2
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':';
    }

    /** Equivalent of std::filesystem::path::lexically_normal for '/'-separated paths. */
    private static String lexicalNormal(String path) {
        if (path.isEmpty()) {
            return "";
        }
        boolean rooted = path.charAt(0) == '/';
        String[] parts = path.split("/");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!out.isEmpty() && !out.get(out.size() - 1).equals("..")) {
                    out.remove(out.size() - 1);
                } else if (!rooted) {
                    out.add("..");
                }
                continue;
            }
            out.add(part);
        }
        String joined = String.join("/", out);
        return rooted ? "/" + joined : joined;
    }

    /** Upstream PakInterface::AddPakFile */
    public boolean addPakFile(Path fileName) throws IOException {
        Path resolved = PakSource.resolveCaseInsensitive(fileName);
        if (resolved == null) {
            return false;
        }
        PakSource collection = PakSource.open(resolved);
        mPakCollectionList.add(collection);

        String pakKey = normalizePakPath(fileName.toString());
        PakRecord selfRecord = new PakRecord();
        selfRecord.mCollection = collection;
        selfRecord.mFileName = pakKey;
        selfRecord.mStartPos = 0;
        selfRecord.mSize = (int) collection.size();
        mPakRecordMap.put(pakKey, selfRecord);

        try (PakFileHandle handle = open(fileName.toString(), "rb")) {
            if (handle == null) {
                removeCollection(collection, pakKey);
                return false;
            }

            byte[] header = new byte[4];
            if (readFully(handle, header, 4) < 4) {
                removeCollection(collection, pakKey);
                return false;
            }
            int magic = readLE32(header, 0);
            if (magic != PAK_MAGIC) {
                removeCollection(collection, pakKey);
                return false;
            }
            if (readFully(handle, header, 4) < 4) {
                removeCollection(collection, pakKey);
                return false;
            }
            int version = readLE32(header, 0);
            if (version > 0) {
                removeCollection(collection, pakKey);
                return false;
            }

            List<PakRecord> newRecords = new ArrayList<>();
            int pos = 0;
            byte[] one = new byte[1];
            for (; ; ) {
                int flagsRead = handle.read(one, 0, 1);
                if (flagsRead == 0) {
                    break;
                }
                int flags = one[0] & 0xFF;
                if ((flags & FILEFLAGS_END) != 0) {
                    break;
                }

                int nameWidth = handle.read(one, 0, 1) == 1 ? one[0] & 0xFF : 0;
                if (nameWidth > MAX_NAME_LENGTH) {
                    removeCollection(collection, pakKey);
                    return false;
                }
                byte[] nameBytes = new byte[nameWidth];
                readFully(handle, nameBytes, nameWidth);
                String rawName = new String(nameBytes, StandardCharsets.ISO_8859_1).replace('\\', '/');

                byte[] sizes = new byte[12];
                readFully(handle, sizes, 12);
                int srcSize = readLE32(sizes, 0);
                long fileTime = readLE64(sizes, 4);

                PakRecord record = new PakRecord();
                record.mCollection = collection;
                record.mFileName = normalizePakPath(rawName);
                record.mStartPos = pos;
                record.mSize = srcSize;
                record.mFileTime = fileTime;
                newRecords.add(record);

                pos += srcSize;
            }

            int headerEnd = handle.tell();
            for (PakRecord record : newRecords) {
                record.mStartPos += headerEnd;
                mPakRecordMap.put(record.mFileName, record);
            }

            if (handle.isPakMember()) {
                // The self record for the pak file itself keeps start 0 / full size (upstream behavior).
                selfRecord.mStartPos = 0;
                selfRecord.mSize = (int) collection.size();
                mPakRecordMap.put(pakKey, selfRecord);
            }
            return true;
        } catch (IOException e) {
            removeCollection(collection, pakKey);
            throw e;
        }
    }

    private void removeCollection(PakSource collection, String pakKey) throws IOException {
        mPakCollectionList.remove(collection);
        mPakRecordMap.remove(pakKey);
        collection.close();
    }

    private static int readFully(PakFileHandle handle, byte[] dest, int len) throws IOException {
        int done = 0;
        while (done < len) {
            int n = handle.read(dest, done, len - done);
            if (n <= 0) {
                break;
            }
            done += n;
        }
        return done;
    }

    private static int readLE32(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static long readLE64(byte[] data, int offset) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    /**
     * Upstream PakInterface::FOpen. Returns {@code null} when the resource cannot be resolved.
     */
    public PakFileHandle open(String fileName, String access) throws IOException {
        if (isReadAccess(access)) {
            String key = normalizePakPath(fileName);
            PakRecord record = mPakRecordMap.get(key);
            if (record != null) {
                return new PakFileHandle(record);
            }
        }

        String resourceBase = getResourceFolder();
        Path filePath = Path.of(fileName);
        Path candidate;
        if (!resourceBase.isEmpty() && !filePath.isAbsolute()) {
            candidate = Path.of(resourceBase).resolve(fileName);
        } else {
            candidate = filePath;
        }

        Path resolved = PakSource.resolveCaseInsensitive(candidate);
        if (resolved == null) {
            return null;
        }
        return new PakFileHandle(new RandomAccessFile(resolved.toFile(), "r"));
    }

    private static boolean isReadAccess(String access) {
        return "r".equalsIgnoreCase(access) || "rb".equalsIgnoreCase(access) || "rt".equalsIgnoreCase(access);
    }

    /** Reads a whole resource (pak member or file) into memory; {@code null} when missing. */
    public byte[] readResource(String fileName) throws IOException {
        try (PakFileHandle handle = open(fileName, "rb")) {
            if (handle == null) {
                return null;
            }
            return handle.readAllBytes();
        }
    }

    /** Reads a whole resource, or throws with the upstream-style "file not found" semantics. */
    public byte[] requireResource(String fileName) throws IOException {
        byte[] data = readResource(fileName);
        if (data == null) {
            throw new IOException("Unable to find file: " + fileName);
        }
        return data;
    }

    public boolean exists(String fileName) throws IOException {
        String key = normalizePakPath(fileName);
        if (mPakRecordMap.containsKey(key)) {
            return true;
        }
        String resourceBase = getResourceFolder();
        Path candidate = resourceBase.isEmpty() || Path.of(fileName).isAbsolute()
                ? Path.of(fileName)
                : Path.of(resourceBase).resolve(fileName);
        return Files.exists(candidate) || PakSource.resolveCaseInsensitive(candidate) != null;
    }

    /** All packed resource names (upper-case, '/'-separated). */
    public List<String> resourceNames() {
        return new ArrayList<>(mPakRecordMap.keySet());
    }

    public int resourceCount() {
        return mPakRecordMap.size();
    }

    public List<PakSource> mountedSources() {
        return List.copyOf(mPakCollectionList);
    }

    public void reset() throws IOException {
        for (PakSource source : mPakCollectionList) {
            source.close();
        }
        mPakCollectionList.clear();
        mPakRecordMap.clear();
    }
}
