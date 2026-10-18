package cn.enaium.fabricpvz.portable.lawn.system;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import cn.enaium.fabricpvz.portable.lawn.AdviceType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Challenge;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CursorObject;
import cn.enaium.fabricpvz.portable.lawn.CursorPreview;
import cn.enaium.fabricpvz.portable.lawn.GameObject;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.GridItem;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnMower;
import cn.enaium.fabricpvz.portable.lawn.MessageWidget;
import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Projectile;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedBank;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.PvzpList;
import cn.enaium.fabricpvz.portable.pvzp.PvzpListNode;
import cn.enaium.fabricpvz.portable.pvzp.PvzpSmoothArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.ParticleSystemTracks;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpEmitterDefinition;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticle;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleDefinition;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleDefinitions;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleEmitter;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.fx.Trail;
import cn.enaium.fabricpvz.portable.pvzp.fx.TrailDefinition;
import cn.enaium.fabricpvz.portable.pvzp.fx.TrailDefinitions;
import cn.enaium.fabricpvz.portable.pvzp.fx.TrailType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorDefinition;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.DataReader;
import cn.enaium.fabricpvz.portable.sexy.DataReaderException;
import cn.enaium.fabricpvz.portable.sexy.DataSync;
import cn.enaium.fabricpvz.portable.sexy.DataWriter;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/System/SaveGame.cpp:49-2829 (the {@code .v4} mid-level board save).
 *
 * <p>This class owns the container format (header + CRC + chunk framing, {@code
 * ../verification/research/03-save.md} §6.1-§6.2) and the per-chunk portable sync functions that live in the
 * C++ {@code SaveGame.cpp} translation unit. Entity/board state is read through the public fields
 * of the ported classes; the per-object portable syncs are driven by a {@link DataSync} so the same
 * code reads and writes, exactly like the upstream {@code PortableSaveContext}
 * ({@code SaveGame.cpp:168-381}).
 *
 * <p><b>Legacy {@code 0xFEEDDEAD} {@code .dat} mid-level saves.</b> Upstream keeps a second,
 * pre-{@code .v4} container for migration: {@code LawnLoadGame} first tries {@code .v4} and then
 * decodes the legacy dump ({@code SaveGame.cpp:2758-2789}), and {@code LawnApp::TryLoadGame}
 * re-saves a successfully loaded legacy file as {@code .v4} and erases the {@code .dat}
 * ({@code LawnApp.cpp:396-410}). That legacy container is <em>not</em> a field sequence: it is a raw
 * image of the running C++ process (../verification/research/03-save.md §9: "This format embeds native struct
 * layout, padding, and {@code sizeof} and is <b>not portable</b>; do not reimplement it beyond a
 * best-effort migrator (or drop it - the port can reject legacy files)"), so it is by construction
 * not decodable into a Java object graph:
 *
 * <ul>
 *   <li>{@code SyncBoard} writes the whole {@code Board} struct verbatim
 *       ({@code theContext.SyncBytes(&theBoard->mPaused, sizeof(Board) - offset)}, SaveGame.cpp:2700);
 *       the trailing {@code CursorObject}/{@code CursorPreview}/{@code MessageWidget}/{@code SeedBank}/
 *       {@code Challenge}/{@code Music} blobs are raw {@code sizeof(T)} structs too
 *       (SaveGame.cpp:2736-2741).</li>
 *   <li>{@code SyncDataArray} copies {@code sizeof(T)} bytes per entity, including vtable/pointer
 *       padding, through {@code LegacyDataArrayItem<T>} ({@code alignas(T) unsigned char
 *       mItem[sizeof(T)]}, SaveGame.cpp:2661-2668, 2686-2690).</li>
 *   <li>{@code SyncParticleEmitter} derives the emitter definition index by pointer subtraction
 *       across two live objects (SaveGame.cpp:2585-2590), which is meaningless in another process or
 *       address layout.</li>
 * </ul>
 *
 * <p>Upstream's own README states the format is not portable (README.md, "Save data compatibility":
 * "{@code *.dat} files: <b>Legacy dump</b> from old versions. Contains raw memory dumps. <b>Do not
 * share this file</b> across platforms as it will cause crashes due to architecture differences."),
 * so this port implements the only well-defined parts of the legacy path: the container is
 * recognised with upstream's exact accept test (magic, build version and build date,
 * {@code SaveGame.cpp:2767-2775}), a recognised file is rejected with an actionable
 * {@link LegacySaveFormatException} pointing at {@code .v4} and at upstream's migration rule, and a
 * file that fails that test is reported as "not a save" exactly like upstream ({@code return false}).
 */
public final class SaveGame {

    private SaveGame() {
    }

    // ---------------------------------------------------------------------------------------
    // Constants (SaveGame.cpp:49-93)
    // ---------------------------------------------------------------------------------------

    /** Legacy {@code .dat} magic ({@code SAVE_FILE_MAGIC_NUMBER}, SaveGame.cpp:50). */
    public static final int SAVE_FILE_MAGIC_NUMBER = 0xFEEDDEAD;

    /** {@code FILE_COMPILE_TIME_STRING} (SaveGame.cpp:48): legacy saves are tied to this timestamp. */
    public static final String FILE_COMPILE_TIME_STRING = "Jul  2 201011:47:03";

    /** {@code SAVE_FILE_VERSION} (SaveGame.cpp:51). */
    public static final int SAVE_FILE_VERSION = 2;

    /** {@code SAVE_FILE_DATE = crc32(0, FILE_COMPILE_TIME_STRING, strlen(...))} (SaveGame.cpp:52). */
    public static final int SAVE_FILE_DATE = crc32(FILE_COMPILE_TIME_STRING.getBytes(StandardCharsets.ISO_8859_1));

    /** Size of the legacy {@code SaveFileHeader} (SaveGame.cpp:64-69): magic, build version, build date. */
    public static final int SAVE_FILE_HEADER_SIZE = 12;

    /** {@code SAVE_FILE_MAGIC_V4} (SaveGame.cpp:55): 12 bytes, {@code "PVZP_SAVE4"} NUL-padded. */
    public static final byte[] SAVE_FILE_MAGIC_V4 = {
        'P', 'V', 'Z', 'P', '_', 'S', 'A', 'V', 'E', '4', 0, 0
    };

    /** {@code SAVE_FILE_V4_VERSION} (SaveGame.cpp:56). */
    public static final int SAVE_FILE_V4_VERSION = 1;

    /** {@code SAVE4_CHUNK_VERSION} (SaveGame.cpp:95). */
    public static final int SAVE4_CHUNK_VERSION = 1;

    /** Size of {@code SaveFileHeaderV4} (SaveGame.cpp:58-64). */
    public static final int SAVE_FILE_HEADER_V4_SIZE = 24;

    public static final int SAVE4_CHUNK_BOARD_BASE = 1;
    public static final int SAVE4_CHUNK_ZOMBIES = 2;
    public static final int SAVE4_CHUNK_PLANTS = 3;
    public static final int SAVE4_CHUNK_PROJECTILES = 4;
    public static final int SAVE4_CHUNK_COINS = 5;
    public static final int SAVE4_CHUNK_MOWERS = 6;
    public static final int SAVE4_CHUNK_GRIDITEMS = 7;
    public static final int SAVE4_CHUNK_PARTICLE_EMITTERS = 8;
    public static final int SAVE4_CHUNK_PARTICLE_PARTICLES = 9;
    public static final int SAVE4_CHUNK_PARTICLE_SYSTEMS = 10;
    public static final int SAVE4_CHUNK_REANIMATIONS = 11;
    public static final int SAVE4_CHUNK_TRAILS = 12;
    public static final int SAVE4_CHUNK_ATTACHMENTS = 13;
    public static final int SAVE4_CHUNK_CURSOR = 14;
    public static final int SAVE4_CHUNK_CURSOR_PREVIEW = 15;
    public static final int SAVE4_CHUNK_ADVICE = 16;
    public static final int SAVE4_CHUNK_SEEDBANK = 17;
    public static final int SAVE4_CHUNK_SEEDPACKETS = 18;
    public static final int SAVE4_CHUNK_CHALLENGE = 19;
    public static final int SAVE4_CHUNK_MUSIC = 20;

    /** The fixed chunk emit order ({@code LawnSaveGame}, SaveGame.cpp:2793-2815). */
    public static final int[] SAVE4_CHUNK_ORDER = {
        SAVE4_CHUNK_BOARD_BASE,
        SAVE4_CHUNK_ZOMBIES,
        SAVE4_CHUNK_PLANTS,
        SAVE4_CHUNK_PROJECTILES,
        SAVE4_CHUNK_COINS,
        SAVE4_CHUNK_MOWERS,
        SAVE4_CHUNK_GRIDITEMS,
        SAVE4_CHUNK_PARTICLE_EMITTERS,
        SAVE4_CHUNK_PARTICLE_PARTICLES,
        SAVE4_CHUNK_PARTICLE_SYSTEMS,
        SAVE4_CHUNK_REANIMATIONS,
        SAVE4_CHUNK_TRAILS,
        SAVE4_CHUNK_ATTACHMENTS,
        SAVE4_CHUNK_CURSOR,
        SAVE4_CHUNK_CURSOR_PREVIEW,
        SAVE4_CHUNK_ADVICE,
        SAVE4_CHUNK_SEEDBANK,
        SAVE4_CHUNK_SEEDPACKETS,
        SAVE4_CHUNK_CHALLENGE,
        SAVE4_CHUNK_MUSIC
    };

    /** {@code PORTABLE_FIELD_TAIL} (SaveGame.cpp:546). */
    public static final int PORTABLE_FIELD_TAIL = 100;

    /** {@code DATA_ARRAY_KEY_MASK} (DataArray.h:34). */
    public static final int DATA_ARRAY_KEY_MASK = -65536;

    /** {@code BOARD_FIELD_COUNT} terminator (SaveGame.cpp:1595). */
    public static final int BOARD_FIELD_COUNT = 104;

    // ---------------------------------------------------------------------------------------
    // Small helpers (SaveGame.cpp:97-163, 1011-1114)
    // ---------------------------------------------------------------------------------------

    /** Functional interface mirroring the upstream context-bound sync lambda. */
    @FunctionalInterface
    private interface PortableSync {
        void sync(DataSync theSync);
    }

    /** Per-item sync used by the {@code DataArray} loops. */
    @FunctionalInterface
    private interface ItemSync<T> {
        void sync(DataSync theSync, T theItem);
    }

    /** Per-field callback for TLV blob iteration. */
    @FunctionalInterface
    private interface FieldSync {
        void field(int theFieldId, byte[] theData);
    }

    /** Thrown when the payload is structurally invalid (upstream {@code mFailed}). */
    private static final class SaveFormatException extends RuntimeException {
        SaveFormatException(String theMessage) {
            super(theMessage);
        }
    }

    private static boolean isReading(DataSync theSync) {
        return theSync.getReader() != null;
    }

    private static void writeU32LE(ByteArrayOutputStream theOut, int theValue) {
        theOut.write(theValue & 0xFF);
        theOut.write((theValue >>> 8) & 0xFF);
        theOut.write((theValue >>> 16) & 0xFF);
        theOut.write((theValue >>> 24) & 0xFF);
    }

    private static int readU32LE(byte[] theData, int theOffset) {
        return (theData[theOffset] & 0xFF)
                | ((theData[theOffset + 1] & 0xFF) << 8)
                | ((theData[theOffset + 2] & 0xFF) << 16)
                | ((theData[theOffset + 3] & 0xFF) << 24);
    }

    /** {@code crc32(0, data, len)} (SaveGame.cpp:2265, 2822). */
    public static int crc32(byte[] theData) {
        CRC32 aCrc = new CRC32();
        aCrc.update(theData, 0, theData.length);
        return (int) aCrc.getValue();
    }

    /** Runs {@code theSyncFn} against a fresh in-memory {@link DataWriter} and returns the bytes. */
    private static byte[] syncFieldBytes(PortableSync theSyncFn) {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        theSyncFn.sync(new DataSync(aWriter));
        return aWriter.getDataPtr();
    }

    /** {@code AppendFieldWithSync} (SaveGame.cpp:1011-1026): fieldId, fieldLen, fieldData. */
    private static void appendField(ByteArrayOutputStream theOut, int theFieldId, PortableSync theSyncFn) {
        byte[] aBody = syncFieldBytes(theSyncFn);
        writeU32LE(theOut, theFieldId);
        writeU32LE(theOut, aBody.length);
        theOut.write(aBody, 0, aBody.length);
    }

    /** {@code ApplyFieldWithSync} (SaveGame.cpp:1028-1036): applies a field body, ignoring failure. */
    private static boolean applyField(byte[] theData, PortableSync theSyncFn) {
        DataReader aReader = new DataReader();
        aReader.openMemory(theData, false);
        try {
            theSyncFn.sync(new DataSync(aReader));
            return true;
        } catch (RuntimeException e) {
            // Upstream PortableSaveContext records mFailed and the callers of ApplyFieldWithSync
            // discard the result, so a malformed optional field never aborts the load.
            return false;
        }
    }

    /** Iterates a TLV blob ({@code TLVReader}, SaveGame.cpp:120-163). */
    private static void readTlv(byte[] theBlob, FieldSync theCallback) {
        int aPos = 0;
        while (aPos + 8 <= theBlob.length) {
            int aFieldId = readU32LE(theBlob, aPos);
            int aFieldSize = readU32LE(theBlob, aPos + 4);
            aPos += 8;
            if (aPos + aFieldSize > theBlob.length) {
                break;
            }
            byte[] aFieldData = new byte[aFieldSize];
            System.arraycopy(theBlob, aPos, aFieldData, 0, aFieldSize);
            aPos += aFieldSize;
            theCallback.field(aFieldId, aFieldData);
        }
    }

    /** {@code WriteTLVBlob} (SaveGame.cpp:1054-1060). */
    private static void writeTlvBlob(DataSync theSync, byte[] theBlob) {
        theSync.syncUInt32(theBlob.length);
        if (theBlob.length > 0) {
            theSync.syncBytes(theBlob);
        }
    }

    /** {@code ReadTLVBlob} (SaveGame.cpp:1062-1074). */
    private static byte[] readTlvBlob(DataSync theSync) {
        int aSize = theSync.syncUInt32(0);
        if (aSize < 0) {
            throw new SaveFormatException("negative TLV blob size");
        }
        byte[] aBlob = new byte[aSize];
        if (aSize > 0) {
            theSync.syncBytes(aBlob);
        }
        return aBlob;
    }

    // ---------------------------------------------------------------------------------------
    // Primitive syncs (SaveGame.cpp:383-534)
    // ---------------------------------------------------------------------------------------

    private static void syncColor(DataSync theSync, Color theColor) {
        theColor.mRed = theSync.syncUInt32(theColor.mRed);
        theColor.mGreen = theSync.syncUInt32(theColor.mGreen);
        theColor.mBlue = theSync.syncUInt32(theColor.mBlue);
        theColor.mAlpha = theSync.syncUInt32(theColor.mAlpha);
    }

    private static void syncVector2(DataSync theSync, SexyVector2 theVector) {
        theVector.x = theSync.syncFloat(theVector.x);
        theVector.y = theSync.syncFloat(theVector.y);
    }

    private static void syncMatrix(DataSync theSync, SexyMatrix3 theMatrix) {
        theMatrix.m00 = theSync.syncFloat(theMatrix.m00);
        theMatrix.m01 = theSync.syncFloat(theMatrix.m01);
        theMatrix.m02 = theSync.syncFloat(theMatrix.m02);
        theMatrix.m10 = theSync.syncFloat(theMatrix.m10);
        theMatrix.m11 = theSync.syncFloat(theMatrix.m11);
        theMatrix.m12 = theSync.syncFloat(theMatrix.m12);
        theMatrix.m20 = theSync.syncFloat(theMatrix.m20);
        theMatrix.m21 = theSync.syncFloat(theMatrix.m21);
        theMatrix.m22 = theSync.syncFloat(theMatrix.m22);
    }

    /** {@code SyncRectPortable} (SaveGame.cpp:443-451). */
    private static void syncRect(DataSync theSync, Rect theRect) {
        theRect.mX = theSync.syncUInt32(theRect.mX);
        theRect.mY = theSync.syncUInt32(theRect.mY);
        theRect.mWidth = theSync.syncUInt32(theRect.mWidth);
        theRect.mHeight = theSync.syncUInt32(theRect.mHeight);
    }

    /** {@code SyncImagePortable} (SaveGame.cpp:465-493): resource id, RESOURCE_ID_MAX = null. */
    private static Image syncImage(DataSync theSync, Image theImage) {
        if (isReading(theSync)) {
            int aResId = theSync.syncUInt32(Resources.RESOURCE_ID_MAX);
            return aResId == Resources.RESOURCE_ID_MAX ? null : Resources.getImageById(aResId);
        }
        int aResId = theImage != null ? Resources.getIdByImage(theImage) : Resources.RESOURCE_ID_MAX;
        theSync.syncUInt32(aResId);
        return theImage;
    }

    // ---------------------------------------------------------------------------------------
    // DataArray framing (SaveGame.cpp:1384-1487)
    // ---------------------------------------------------------------------------------------

    private static <T> int syncDataArrayHeader(DataSync theSync, DataArray<T> theArray) {
        theArray.mFreeListHead = theSync.syncUInt32(theArray.mFreeListHead);
        theArray.mMaxUsedCount = theSync.syncUInt32(theArray.mMaxUsedCount);
        theArray.mSize = theSync.syncUInt32(theArray.mSize);
        theArray.mNextKey = theSync.syncUInt32(theArray.mNextKey);
        int aMaxSize = theArray.mMaxSize;
        aMaxSize = theSync.syncUInt32(aMaxSize);
        if (isReading(theSync) && aMaxSize != theArray.mMaxSize) {
            throw new SaveFormatException("DataArray max size mismatch: saved " + aMaxSize
                    + ", runtime " + theArray.mMaxSize + " (" + theArray.mName + ")");
        }
        return theArray.mMaxUsedCount;
    }

    /**
     * {@code SyncDataArrayIdsOnlyPortable} (SaveGame.cpp:1405-1431).
     *
     * <p>Upstream's array owns an object in every slot ({@code std::make_unique<DataArrayItem[]>},
     * DataArray.h:52-58), so an ids-only chunk is all a load needs: the ids the saved lists carry
     * resolve through {@code DataArrayGet} to those slot objects (SaveGame.cpp:1307, :1330). A Java
     * array starts empty instead, so the read has to construct the slot object for every restored id
     * - exactly what the TLV read paths do with {@code DataArrayResetItemAt} before applying a body.
     * Without that, nothing the save restored resolves: the loads that follow (the particle systems
     * that own the emitters) would have to re-create every list object under a new id, the restored
     * ids would stay unresolvable, and re-creating them can overflow a pool the save left nearly full
     * ({@code dataArrayAlloc} throws where upstream's fixed-capacity array simply has the slot).
     */
    private static <T> void syncDataArrayIdsOnly(DataSync theSync, DataArray<T> theArray) {
        int aMaxUsed = syncDataArrayHeader(theSync, theArray);
        for (int i = 0; i < aMaxUsed; i++) {
            if (isReading(theSync)) {
                theArray.dataArraySetIDAt(i, theSync.syncUInt32(0));
                theArray.dataArrayResetItemAt(i);
            } else {
                theSync.syncUInt32(theArray.dataArrayGetIDAt(i));
            }
        }
    }

    /**
     * {@code SyncDataArrayPortableTLV} with a single field-1 body per item
     * (SaveGame.cpp:1433-1487), used by particle systems, reanimations and trails.
     */
    private static <T> void syncDataArrayItemField1Tlv(DataSync theSync, DataArray<T> theArray, ItemSync<T> theItemSync) {
        int aMaxUsed = syncDataArrayHeader(theSync, theArray);
        for (int i = 0; i < aMaxUsed; i++) {
            if (isReading(theSync)) {
                theArray.dataArraySetIDAt(i, theSync.syncUInt32(0));
                int aItemSize = theSync.syncUInt32(0);
                if (aItemSize < 0) {
                    throw new SaveFormatException("negative item size");
                }
                byte[] aItemData = new byte[aItemSize];
                if (aItemSize > 0) {
                    theSync.syncBytes(aItemData);
                }
                T aItem = theArray.dataArrayResetItemAt(i);
                readTlv(aItemData, (aFieldId, aFieldData) -> {
                    if (aFieldId == 1) {
                        applyField(aFieldData, s -> theItemSync.sync(s, aItem));
                    }
                });
            } else {
                int aId = theArray.dataArrayGetIDAt(i);
                theSync.syncUInt32(aId);
                byte[] aItemData = new byte[0];
                if ((aId & DATA_ARRAY_KEY_MASK) != 0) {
                    T aItem = theArray.dataArrayGetItemAt(i);
                    ByteArrayOutputStream aBlob = new ByteArrayOutputStream();
                    appendField(aBlob, 1, s -> theItemSync.sync(s, aItem));
                    aItemData = aBlob.toByteArray();
                }
                theSync.syncUInt32(aItemData.length);
                if (aItemData.length > 0) {
                    theSync.syncBytes(aItemData);
                }
            }
        }
    }

    /**
     * {@code SyncDataArrayObjectsTLV} (SaveGame.cpp:1489-1521) for item types that derive from
     * {@link GameObject}: field 1 = GameObject body, field 100 = tail. The item-size wrapper is
     * present only for live slots.
     */
    private static <T extends GameObject> void syncDataArrayGameObjectsTlv(DataSync theSync, DataArray<T> theArray, ItemSync<T> theTailSync) {
        int aMaxUsed = syncDataArrayHeader(theSync, theArray);
        for (int i = 0; i < aMaxUsed; i++) {
            if (isReading(theSync)) {
                theArray.dataArraySetIDAt(i, theSync.syncUInt32(0));
                int aItemSize = theSync.syncUInt32(0);
                if (aItemSize < 0) {
                    throw new SaveFormatException("negative item size");
                }
                byte[] aItemData = new byte[aItemSize];
                if (aItemSize > 0) {
                    theSync.syncBytes(aItemData);
                }
                T aItem = theArray.dataArrayResetItemAt(i);
                readTlv(aItemData, (aFieldId, aFieldData) -> {
                    if (aFieldId == 1) {
                        applyField(aFieldData, aItem::syncGameObjectPortable);
                    } else if (aFieldId == PORTABLE_FIELD_TAIL) {
                        applyField(aFieldData, s -> theTailSync.sync(s, aItem));
                    }
                });
            } else {
                int aId = theArray.dataArrayGetIDAt(i);
                theSync.syncUInt32(aId);
                byte[] aItemData = new byte[0];
                if ((aId & DATA_ARRAY_KEY_MASK) != 0) {
                    T aItem = theArray.dataArrayGetItemAt(i);
                    ByteArrayOutputStream aBlob = new ByteArrayOutputStream();
                    appendField(aBlob, 1, aItem::syncGameObjectPortable);
                    appendField(aBlob, PORTABLE_FIELD_TAIL, s -> theTailSync.sync(s, aItem));
                    aItemData = aBlob.toByteArray();
                }
                theSync.syncUInt32(aItemData.length);
                if (aItemData.length > 0) {
                    theSync.syncBytes(aItemData);
                }
            }
        }
    }

    /**
     * {@code SyncDataArrayObjectsTLV} for item types that do not derive from {@link GameObject}
     * (LawnMower, GridItem, Attachment): only field 100 = tail.
     */
    private static <T> void syncDataArrayObjectsNoBaseTlv(DataSync theSync, DataArray<T> theArray, ItemSync<T> theTailSync) {
        int aMaxUsed = syncDataArrayHeader(theSync, theArray);
        for (int i = 0; i < aMaxUsed; i++) {
            if (isReading(theSync)) {
                theArray.dataArraySetIDAt(i, theSync.syncUInt32(0));
                int aItemSize = theSync.syncUInt32(0);
                if (aItemSize < 0) {
                    throw new SaveFormatException("negative item size");
                }
                byte[] aItemData = new byte[aItemSize];
                if (aItemSize > 0) {
                    theSync.syncBytes(aItemData);
                }
                T aItem = theArray.dataArrayResetItemAt(i);
                readTlv(aItemData, (aFieldId, aFieldData) -> {
                    if (aFieldId == PORTABLE_FIELD_TAIL) {
                        applyField(aFieldData, s -> theTailSync.sync(s, aItem));
                    }
                });
            } else {
                int aId = theArray.dataArrayGetIDAt(i);
                theSync.syncUInt32(aId);
                byte[] aItemData = new byte[0];
                if ((aId & DATA_ARRAY_KEY_MASK) != 0) {
                    T aItem = theArray.dataArrayGetItemAt(i);
                    ByteArrayOutputStream aBlob = new ByteArrayOutputStream();
                    appendField(aBlob, PORTABLE_FIELD_TAIL, s -> theTailSync.sync(s, aItem));
                    aItemData = aBlob.toByteArray();
                }
                theSync.syncUInt32(aItemData.length);
                if (aItemData.length > 0) {
                    theSync.syncBytes(aItemData);
                }
            }
        }
    }

    /** {@code SyncSingleObjectTLV} (SaveGame.cpp:1076-1114). */
    private static void syncSingleObjectTlv(DataSync theSync, boolean theHasGameObject, GameObject theObject, PortableSync theTailSync) {
        if (isReading(theSync)) {
            byte[] aBlob = readTlvBlob(theSync);
            readTlv(aBlob, (aFieldId, aFieldData) -> {
                if (aFieldId == 1 && theHasGameObject) {
                    applyField(aFieldData, theObject::syncGameObjectPortable);
                } else if (aFieldId == PORTABLE_FIELD_TAIL) {
                    applyField(aFieldData, theTailSync);
                }
            });
        } else {
            ByteArrayOutputStream aBlob = new ByteArrayOutputStream();
            if (theHasGameObject) {
                appendField(aBlob, 1, theObject::syncGameObjectPortable);
            }
            appendField(aBlob, PORTABLE_FIELD_TAIL, theTailSync);
            writeTlvBlob(theSync, aBlob.toByteArray());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Fixed-size string helper (upstream SyncBytes over char[] buffers)
    // ---------------------------------------------------------------------------------------

    private static String syncFixedString(DataSync theSync, String theValue, int theLength) {
        byte[] aBuf = new byte[theLength];
        if (isReading(theSync)) {
            theSync.syncBytes(aBuf);
            int aLen = 0;
            while (aLen < theLength && aBuf[aLen] != 0) {
                aLen++;
            }
            return new String(aBuf, 0, aLen, StandardCharsets.ISO_8859_1);
        }
        byte[] aBytes = theValue.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(aBytes, 0, aBuf, 0, Math.min(aBytes.length, theLength));
        theSync.syncBytes(aBuf);
        return theValue;
    }

    // ---------------------------------------------------------------------------------------
    // Entity tails (SaveGame.cpp:640-1009)
    // ---------------------------------------------------------------------------------------

    /** {@code SyncMagnetItemPortable} (SaveGame.cpp:640-648). */
    private static void syncMagnetItemPortable(DataSync theSync, cn.enaium.fabricpvz.portable.lawn.MagnetItem theItem) {
        theItem.mPosX = theSync.syncFloat(theItem.mPosX);
        theItem.mPosY = theSync.syncFloat(theItem.mPosY);
        theItem.mDestOffsetX = theSync.syncFloat(theItem.mDestOffsetX);
        theItem.mDestOffsetY = theSync.syncFloat(theItem.mDestOffsetY);
        theItem.mItemType = theSync.syncUInt32(theItem.mItemType);
    }

    /** {@code SyncAttachEffectPortable} (SaveGame.cpp:650-657). */
    private static void syncAttachEffectPortable(DataSync theSync, AttachEffect theEffect) {
        theEffect.mEffectID = theSync.syncUInt32(theEffect.mEffectID);
        theEffect.mEffectType = theSync.syncUInt32(theEffect.mEffectType);
        syncMatrix(theSync, theEffect.mOffset);
        theEffect.mDontDrawIfParentHidden = theSync.syncBool(theEffect.mDontDrawIfParentHidden);
        theEffect.mDontPropogateColor = theSync.syncBool(theEffect.mDontPropogateColor);
    }

    /** {@code SyncAttachmentTailPortable} (SaveGame.cpp:659-669). */
    private static void syncAttachmentTailPortable(DataSync theSync, Attachment theAttachment) {
        for (int i = 0; i < Attachment.MAX_EFFECTS_PER_ATTACHMENT; i++) {
            syncAttachEffectPortable(theSync, theAttachment.mEffectArray[i]);
        }
        theAttachment.mNumEffects = theSync.syncUInt32(theAttachment.mNumEffects);
        theAttachment.mDead = theSync.syncBool(theAttachment.mDead);
    }

    /** {@code SyncCursorObjectTailPortable} (SaveGame.cpp:671-682). */
    private static void syncCursorObjectTailPortable(DataSync theSync, CursorObject theObject) {
        theObject.mSeedBankIndex = theSync.syncUInt32(theObject.mSeedBankIndex);
        theObject.mType = theSync.syncUInt32(theObject.mType);
        theObject.mImitaterType = theSync.syncUInt32(theObject.mImitaterType);
        theObject.mCursorType = theSync.syncUInt32(theObject.mCursorType);
        theObject.mCoinID = theSync.syncUInt32(theObject.mCoinID);
        theObject.mGlovePlantID = theSync.syncUInt32(theObject.mGlovePlantID);
        theObject.mDuplicatorPlantID = theSync.syncUInt32(theObject.mDuplicatorPlantID);
        theObject.mCobCannonPlantID = theSync.syncUInt32(theObject.mCobCannonPlantID);
        theObject.mHammerDownCounter = theSync.syncUInt32(theObject.mHammerDownCounter);
        theObject.mReanimCursorID = theSync.syncUInt32(theObject.mReanimCursorID);
    }

    /** {@code SyncCursorPreviewTailPortable} (SaveGame.cpp:684-688). */
    private static void syncCursorPreviewTailPortable(DataSync theSync, CursorPreview thePreview) {
        thePreview.mGridX = theSync.syncUInt32(thePreview.mGridX);
        thePreview.mGridY = theSync.syncUInt32(thePreview.mGridY);
    }

    /** {@code SyncMessageWidgetTailPortable} (SaveGame.cpp:690-701). */
    private static void syncMessageWidgetTailPortable(DataSync theSync, MessageWidget theWidget) {
        theWidget.mLabel = syncFixedString(theSync, theWidget.mLabel, MessageWidget.MAX_MESSAGE_LENGTH);
        theWidget.mDisplayTime = theSync.syncUInt32(theWidget.mDisplayTime);
        theWidget.mDuration = theSync.syncUInt32(theWidget.mDuration);
        theWidget.mMessageStyle = theSync.syncUInt32(theWidget.mMessageStyle);
        for (int i = 0; i < MessageWidget.MAX_MESSAGE_LENGTH; i++) {
            theWidget.mTextReanimID[i] = theSync.syncUInt32(theWidget.mTextReanimID[i]);
        }
        theWidget.mReanimType = theSync.syncUInt32(theWidget.mReanimType);
        theWidget.mSlideOffTime = theSync.syncUInt32(theWidget.mSlideOffTime);
        theWidget.mLabelNext = syncFixedString(theSync, theWidget.mLabelNext, MessageWidget.MAX_MESSAGE_LENGTH);
        theWidget.mMessageStyleNext = theSync.syncUInt32(theWidget.mMessageStyleNext);
    }

    /** {@code SyncSeedBankTailPortable} (SaveGame.cpp:703-708). */
    private static void syncSeedBankTailPortable(DataSync theSync, SeedBank theSeedBank) {
        theSeedBank.mNumPackets = theSync.syncUInt32(theSeedBank.mNumPackets);
        theSeedBank.mCutSceneDarken = theSync.syncUInt32(theSeedBank.mCutSceneDarken);
        theSeedBank.mConveyorBeltCounter = theSync.syncUInt32(theSeedBank.mConveyorBeltCounter);
    }

    /** {@code SyncSeedPacketTailPortable} (SaveGame.cpp:698-713). */
    private static void syncSeedPacketTailPortable(DataSync theSync, SeedPacket thePacket) {
        thePacket.mRefreshCounter = theSync.syncUInt32(thePacket.mRefreshCounter);
        thePacket.mRefreshTime = theSync.syncUInt32(thePacket.mRefreshTime);
        thePacket.mIndex = theSync.syncUInt32(thePacket.mIndex);
        thePacket.mOffsetX = theSync.syncUInt32(thePacket.mOffsetX);
        thePacket.mPacketType = theSync.syncUInt32(thePacket.mPacketType);
        thePacket.mImitaterType = theSync.syncUInt32(thePacket.mImitaterType);
        thePacket.mSlotMachineCountDown = theSync.syncUInt32(thePacket.mSlotMachineCountDown);
        thePacket.mSlotMachiningNextSeed = theSync.syncUInt32(thePacket.mSlotMachiningNextSeed);
        thePacket.mSlotMachiningPosition = theSync.syncFloat(thePacket.mSlotMachiningPosition);
        thePacket.mActive = theSync.syncBool(thePacket.mActive);
        thePacket.mRefreshing = theSync.syncBool(thePacket.mRefreshing);
        thePacket.mTimesUsed = theSync.syncUInt32(thePacket.mTimesUsed);
    }

    /** {@code SyncChallengeTailPortable} (SaveGame.cpp:714-739). */
    public static void syncChallengeTailPortable(DataSync theSync, Challenge theChallenge) {
        theChallenge.mBeghouledMouseCapture = theSync.syncUInt32(theChallenge.mBeghouledMouseCapture);
        theChallenge.mBeghouledMouseDownX = theSync.syncUInt32(theChallenge.mBeghouledMouseDownX);
        theChallenge.mBeghouledMouseDownY = theSync.syncUInt32(theChallenge.mBeghouledMouseDownY);
        for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
            for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                theChallenge.mBeghouledEated[x][y] = theSync.syncUInt32(theChallenge.mBeghouledEated[x][y]);
            }
        }
        for (int i = 0; i < theChallenge.mBeghouledPurcasedUpgrade.length; i++) {
            theChallenge.mBeghouledPurcasedUpgrade[i] = theSync.syncUInt32(theChallenge.mBeghouledPurcasedUpgrade[i]);
        }
        theChallenge.mBeghouledMatchesThisMove = theSync.syncUInt32(theChallenge.mBeghouledMatchesThisMove);
        theChallenge.mChallengeState = theSync.syncUInt32(theChallenge.mChallengeState);
        theChallenge.mChallengeStateCounter = theSync.syncUInt32(theChallenge.mChallengeStateCounter);
        theChallenge.mConveyorBeltCounter = theSync.syncUInt32(theChallenge.mConveyorBeltCounter);
        theChallenge.mChallengeScore = theSync.syncUInt32(theChallenge.mChallengeScore);
        theChallenge.mShowBowlingLine = theSync.syncUInt32(theChallenge.mShowBowlingLine);
        theChallenge.mLastConveyorSeedType = theSync.syncUInt32(theChallenge.mLastConveyorSeedType);
        theChallenge.mSurvivalStage = theSync.syncUInt32(theChallenge.mSurvivalStage);
        theChallenge.mSlotMachineRollCount = theSync.syncUInt32(theChallenge.mSlotMachineRollCount);
        theChallenge.mReanimChallenge = theSync.syncUInt32(theChallenge.mReanimChallenge);
        for (int i = 0; i < 6; i++) {
            theChallenge.mReanimClouds[i] = theSync.syncUInt32(theChallenge.mReanimClouds[i]);
        }
        for (int i = 0; i < 6; i++) {
            theChallenge.mCloudsCounter[i] = theSync.syncUInt32(theChallenge.mCloudsCounter[i]);
        }
        theChallenge.mChallengeGridX = theSync.syncUInt32(theChallenge.mChallengeGridX);
        theChallenge.mChallengeGridY = theSync.syncUInt32(theChallenge.mChallengeGridY);
        theChallenge.mScaryPotterPots = theSync.syncUInt32(theChallenge.mScaryPotterPots);
        theChallenge.mRainCounter = theSync.syncUInt32(theChallenge.mRainCounter);
        theChallenge.mTreeOfWisdomTalkIndex = theSync.syncUInt32(theChallenge.mTreeOfWisdomTalkIndex);
    }

    /** {@code SyncMusicTailPortable} (SaveGame.cpp:740-771), including the mMusicDisabled asymmetry. */
    public static void syncMusicTailPortable(DataSync theSync, Music theMusic) {
        theMusic.mCurMusicTune = theSync.syncUInt32(theMusic.mCurMusicTune);
        theMusic.mCurMusicFileMain = theSync.syncUInt32(theMusic.mCurMusicFileMain);
        theMusic.mCurMusicFileDrums = theSync.syncUInt32(theMusic.mCurMusicFileDrums);
        theMusic.mCurMusicFileHihats = theSync.syncUInt32(theMusic.mCurMusicFileHihats);
        theMusic.mBurstOverride = theSync.syncUInt32(theMusic.mBurstOverride);
        theMusic.mBaseBPM = theSync.syncFloat(theMusic.mBaseBPM);
        theMusic.mBaseModSpeed = theSync.syncFloat(theMusic.mBaseModSpeed);
        theMusic.mMusicBurstState = theSync.syncUInt32(theMusic.mMusicBurstState);
        theMusic.mBurstStateCounter = theSync.syncUInt32(theMusic.mBurstStateCounter);
        theMusic.mMusicDrumsState = theSync.syncUInt32(theMusic.mMusicDrumsState);
        theMusic.mQueuedDrumTrackPackedOrder = theSync.syncUInt32(theMusic.mQueuedDrumTrackPackedOrder);
        theMusic.mDrumsStateCounter = theSync.syncUInt32(theMusic.mDrumsStateCounter);
        theMusic.mPauseOffset = theSync.syncUInt32(theMusic.mPauseOffset);
        theMusic.mPauseOffsetDrums = theSync.syncUInt32(theMusic.mPauseOffsetDrums);
        theMusic.mPaused = theSync.syncBool(theMusic.mPaused);
        // When loading, the saved bool is consumed and discarded: mMusicDisabled is a runtime
        // capability flag (set when audio assets fail to load) and must never come from a save.
        if (isReading(theSync)) {
            theSync.syncBool(false);
        } else {
            theSync.syncBool(theMusic.mMusicDisabled);
        }
        theMusic.mFadeOutCounter = theSync.syncUInt32(theMusic.mFadeOutCounter);
        theMusic.mFadeOutDuration = theSync.syncUInt32(theMusic.mFadeOutDuration);
    }

    /** {@code SyncZombieTailPortable} (SaveGame.cpp:774-853); delegates to the ported method. */
    private static void syncZombieTailPortable(DataSync theSync, Zombie theZombie) {
        theZombie.syncZombieTailPortable(theSync);
    }

    /** {@code SyncPlantTailPortable} (SaveGame.cpp:854-905). */
    private static void syncPlantTailPortable(DataSync theSync, Plant thePlant) {
        if (isReading(theSync)) {
            // C++ keeps these as inline values (`Rect mPlantRect; Rect mPlantAttackRect; MagnetItem
            // mMagnetItems[MAX_MAGNET_ITEMS];`, Plant.h:182-202), so a raw dump always has valid
            // storage; the port models them as references that PlantInitialize allocates. The read path
            // builds its item with `new Plant()` (DataArrayResetItemAt), so the storage has to be
            // created here or the fields after mLaunchRate would be silently dropped.
            if (thePlant.mPlantRect == null) {
                thePlant.mPlantRect = new Rect();
            }
            if (thePlant.mPlantAttackRect == null) {
                thePlant.mPlantAttackRect = new Rect();
            }
            if (thePlant.mMagnetItems == null) {
                thePlant.mMagnetItems = new cn.enaium.fabricpvz.portable.lawn.MagnetItem[Plant.MAX_MAGNET_ITEMS];
                for (int i = 0; i < Plant.MAX_MAGNET_ITEMS; i++) {
                    thePlant.mMagnetItems[i] = new cn.enaium.fabricpvz.portable.lawn.MagnetItem();
                }
            }
        }
        thePlant.mSeedType = theSync.syncUInt32(thePlant.mSeedType);
        thePlant.mPlantCol = theSync.syncUInt32(thePlant.mPlantCol);
        thePlant.mAnimCounter = theSync.syncUInt32(thePlant.mAnimCounter);
        thePlant.mFrame = theSync.syncUInt32(thePlant.mFrame);
        thePlant.mFrameLength = theSync.syncUInt32(thePlant.mFrameLength);
        thePlant.mNumFrames = theSync.syncUInt32(thePlant.mNumFrames);
        thePlant.mState = theSync.syncUInt32(thePlant.mState);
        thePlant.mPlantHealth = theSync.syncUInt32(thePlant.mPlantHealth);
        thePlant.mPlantMaxHealth = theSync.syncUInt32(thePlant.mPlantMaxHealth);
        thePlant.mSubclass = theSync.syncUInt32(thePlant.mSubclass);
        thePlant.mDisappearCountdown = theSync.syncUInt32(thePlant.mDisappearCountdown);
        thePlant.mDoSpecialCountdown = theSync.syncUInt32(thePlant.mDoSpecialCountdown);
        thePlant.mStateCountdown = theSync.syncUInt32(thePlant.mStateCountdown);
        thePlant.mLaunchCounter = theSync.syncUInt32(thePlant.mLaunchCounter);
        thePlant.mLaunchRate = theSync.syncUInt32(thePlant.mLaunchRate);
        syncRect(theSync, thePlant.mPlantRect);
        syncRect(theSync, thePlant.mPlantAttackRect);
        thePlant.mTargetX = theSync.syncUInt32(thePlant.mTargetX);
        thePlant.mTargetY = theSync.syncUInt32(thePlant.mTargetY);
        thePlant.mStartRow = theSync.syncUInt32(thePlant.mStartRow);
        thePlant.mParticleID = theSync.syncUInt32(thePlant.mParticleID);
        thePlant.mShootingCounter = theSync.syncUInt32(thePlant.mShootingCounter);
        thePlant.mBodyReanimID = theSync.syncUInt32(thePlant.mBodyReanimID);
        thePlant.mHeadReanimID = theSync.syncUInt32(thePlant.mHeadReanimID);
        thePlant.mHeadReanimID2 = theSync.syncUInt32(thePlant.mHeadReanimID2);
        thePlant.mHeadReanimID3 = theSync.syncUInt32(thePlant.mHeadReanimID3);
        thePlant.mBlinkReanimID = theSync.syncUInt32(thePlant.mBlinkReanimID);
        thePlant.mLightReanimID = theSync.syncUInt32(thePlant.mLightReanimID);
        thePlant.mSleepingReanimID = theSync.syncUInt32(thePlant.mSleepingReanimID);
        thePlant.mBlinkCountdown = theSync.syncUInt32(thePlant.mBlinkCountdown);
        thePlant.mRecentlyEatenCountdown = theSync.syncUInt32(thePlant.mRecentlyEatenCountdown);
        thePlant.mEatenFlashCountdown = theSync.syncUInt32(thePlant.mEatenFlashCountdown);
        thePlant.mBeghouledFlashCountdown = theSync.syncUInt32(thePlant.mBeghouledFlashCountdown);
        thePlant.mShakeOffsetX = theSync.syncFloat(thePlant.mShakeOffsetX);
        thePlant.mShakeOffsetY = theSync.syncFloat(thePlant.mShakeOffsetY);
        for (int i = 0; i < Plant.MAX_MAGNET_ITEMS; i++) {
            syncMagnetItemPortable(theSync, thePlant.mMagnetItems[i]);
        }
        thePlant.mTargetZombieID = theSync.syncUInt32(thePlant.mTargetZombieID);
        thePlant.mWakeUpCounter = theSync.syncUInt32(thePlant.mWakeUpCounter);
        thePlant.mOnBungeeState = theSync.syncUInt32(thePlant.mOnBungeeState);
        thePlant.mImitaterType = theSync.syncUInt32(thePlant.mImitaterType);
        thePlant.mPottedPlantIndex = theSync.syncUInt32(thePlant.mPottedPlantIndex);
        thePlant.mAnimPing = theSync.syncBool(thePlant.mAnimPing);
        thePlant.mDead = theSync.syncBool(thePlant.mDead);
        thePlant.mSquished = theSync.syncBool(thePlant.mSquished);
        thePlant.mIsAsleep = theSync.syncBool(thePlant.mIsAsleep);
        thePlant.mIsOnBoard = theSync.syncBool(thePlant.mIsOnBoard);
        thePlant.mHighlighted = theSync.syncBool(thePlant.mHighlighted);
    }

    /** {@code SyncProjectileTailPortable} (SaveGame.cpp:906-936). */
    private static void syncProjectileTailPortable(DataSync theSync, Projectile theProjectile) {
        theProjectile.mFrame = theSync.syncUInt32(theProjectile.mFrame);
        theProjectile.mNumFrames = theSync.syncUInt32(theProjectile.mNumFrames);
        theProjectile.mAnimCounter = theSync.syncUInt32(theProjectile.mAnimCounter);
        theProjectile.mPosX = theSync.syncFloat(theProjectile.mPosX);
        theProjectile.mPosY = theSync.syncFloat(theProjectile.mPosY);
        theProjectile.mPosZ = theSync.syncFloat(theProjectile.mPosZ);
        theProjectile.mVelX = theSync.syncFloat(theProjectile.mVelX);
        theProjectile.mVelY = theSync.syncFloat(theProjectile.mVelY);
        theProjectile.mVelZ = theSync.syncFloat(theProjectile.mVelZ);
        theProjectile.mAccZ = theSync.syncFloat(theProjectile.mAccZ);
        theProjectile.mShadowY = theSync.syncFloat(theProjectile.mShadowY);
        theProjectile.mDead = theSync.syncBool(theProjectile.mDead);
        theProjectile.mAnimTicksPerFrame = theSync.syncUInt32(theProjectile.mAnimTicksPerFrame);
        theProjectile.mMotionType = theSync.syncUInt32(theProjectile.mMotionType);
        theProjectile.mProjectileType = theSync.syncUInt32(theProjectile.mProjectileType);
        theProjectile.mProjectileAge = theSync.syncUInt32(theProjectile.mProjectileAge);
        theProjectile.mClickBackoffCounter = theSync.syncUInt32(theProjectile.mClickBackoffCounter);
        theProjectile.mRotation = theSync.syncFloat(theProjectile.mRotation);
        theProjectile.mRotationSpeed = theSync.syncFloat(theProjectile.mRotationSpeed);
        theProjectile.mOnHighGround = theSync.syncBool(theProjectile.mOnHighGround);
        theProjectile.mDamageRangeFlags = theSync.syncUInt32(theProjectile.mDamageRangeFlags);
        theProjectile.mHitTorchwoodGridX = theSync.syncUInt32(theProjectile.mHitTorchwoodGridX);
        theProjectile.mAttachmentID.value = theSync.syncUInt32(theProjectile.mAttachmentID.value);
        theProjectile.mCobTargetX = theSync.syncFloat(theProjectile.mCobTargetX);
        theProjectile.mCobTargetRow = theSync.syncUInt32(theProjectile.mCobTargetRow);
        theProjectile.mTargetZombieID = theSync.syncUInt32(theProjectile.mTargetZombieID);
        theProjectile.mLastPortalX = theSync.syncUInt32(theProjectile.mLastPortalX);
    }

    /** {@code SyncCoinTailPortable} (SaveGame.cpp:937-963). */
    private static void syncCoinTailPortable(DataSync theSync, Coin theCoin) {
        theCoin.mPosX = theSync.syncFloat(theCoin.mPosX);
        theCoin.mPosY = theSync.syncFloat(theCoin.mPosY);
        theCoin.mVelX = theSync.syncFloat(theCoin.mVelX);
        theCoin.mVelY = theSync.syncFloat(theCoin.mVelY);
        theCoin.mScale = theSync.syncFloat(theCoin.mScale);
        theCoin.mDead = theSync.syncBool(theCoin.mDead);
        theCoin.mFadeCount = theSync.syncUInt32(theCoin.mFadeCount);
        theCoin.mCollectX = theSync.syncFloat(theCoin.mCollectX);
        theCoin.mCollectY = theSync.syncFloat(theCoin.mCollectY);
        theCoin.mGroundY = theSync.syncUInt32(theCoin.mGroundY);
        theCoin.mCoinAge = theSync.syncUInt32(theCoin.mCoinAge);
        theCoin.mIsBeingCollected = theSync.syncBool(theCoin.mIsBeingCollected);
        theCoin.mDisappearCounter = theSync.syncUInt32(theCoin.mDisappearCounter);
        theCoin.mType = theSync.syncUInt32(theCoin.mType);
        theCoin.mCoinMotion = theSync.syncUInt32(theCoin.mCoinMotion);
        theCoin.mAttachmentID.value = theSync.syncUInt32(theCoin.mAttachmentID.value);
        theCoin.mCollectionDistance = theSync.syncFloat(theCoin.mCollectionDistance);
        theCoin.mUsableSeedType = theSync.syncUInt32(theCoin.mUsableSeedType);
        theCoin.mPottedPlantSpec.syncPortable(theSync);
        theCoin.mNeedsBouncyArrow = theSync.syncBool(theCoin.mNeedsBouncyArrow);
        theCoin.mHasBouncyArrow = theSync.syncBool(theCoin.mHasBouncyArrow);
        theCoin.mHitGround = theSync.syncBool(theCoin.mHitGround);
        theCoin.mTimesDropped = theSync.syncUInt32(theCoin.mTimesDropped);
    }

    /** {@code SyncLawnMowerTailPortable} (SaveGame.cpp:964-983). */
    private static void syncLawnMowerTailPortable(DataSync theSync, LawnMower theMower) {
        theMower.mPosX = theSync.syncFloat(theMower.mPosX);
        theMower.mPosY = theSync.syncFloat(theMower.mPosY);
        theMower.mRenderOrder = theSync.syncUInt32(theMower.mRenderOrder);
        theMower.mRow = theSync.syncUInt32(theMower.mRow);
        theMower.mAnimTicksPerFrame = theSync.syncUInt32(theMower.mAnimTicksPerFrame);
        theMower.mReanimID = theSync.syncUInt32(theMower.mReanimID);
        theMower.mChompCounter = theSync.syncUInt32(theMower.mChompCounter);
        theMower.mRollingInCounter = theSync.syncUInt32(theMower.mRollingInCounter);
        theMower.mSquishedCounter = theSync.syncUInt32(theMower.mSquishedCounter);
        theMower.mMowerState = theSync.syncUInt32(theMower.mMowerState);
        theMower.mDead = theSync.syncBool(theMower.mDead);
        theMower.mVisible = theSync.syncBool(theMower.mVisible);
        theMower.mMowerType = theSync.syncUInt32(theMower.mMowerType);
        theMower.mAltitude = theSync.syncFloat(theMower.mAltitude);
        theMower.mMowerHeight = theSync.syncUInt32(theMower.mMowerHeight);
        theMower.mLastPortalX = theSync.syncUInt32(theMower.mLastPortalX);
    }

    /** {@code SyncMotionTrailFramePortable} (SaveGame.cpp:623-628). */
    private static void syncMotionTrailFramePortable(DataSync theSync, GridItem.MotionTrailFrame theFrame) {
        theFrame.mPosX = theSync.syncFloat(theFrame.mPosX);
        theFrame.mPosY = theSync.syncFloat(theFrame.mPosY);
        theFrame.mAnimTime = theSync.syncFloat(theFrame.mAnimTime);
    }

    /** {@code SyncGridItemTailPortable} (SaveGame.cpp:984-1009). */
    private static void syncGridItemTailPortable(DataSync theSync, GridItem theItem) {
        theItem.mGridItemType = theSync.syncUInt32(theItem.mGridItemType);
        theItem.mGridItemState = theSync.syncUInt32(theItem.mGridItemState);
        theItem.mGridX = theSync.syncUInt32(theItem.mGridX);
        theItem.mGridY = theSync.syncUInt32(theItem.mGridY);
        theItem.mGridItemCounter = theSync.syncUInt32(theItem.mGridItemCounter);
        theItem.mRenderOrder = theSync.syncUInt32(theItem.mRenderOrder);
        theItem.mDead = theSync.syncBool(theItem.mDead);
        theItem.mPosX = theSync.syncFloat(theItem.mPosX);
        theItem.mPosY = theSync.syncFloat(theItem.mPosY);
        theItem.mGoalX = theSync.syncFloat(theItem.mGoalX);
        theItem.mGoalY = theSync.syncFloat(theItem.mGoalY);
        theItem.mGridItemReanimID = theSync.syncUInt32(theItem.mGridItemReanimID);
        theItem.mGridItemParticleID = theSync.syncUInt32(theItem.mGridItemParticleID);
        theItem.mZombieType = theSync.syncUInt32(theItem.mZombieType);
        theItem.mSeedType = theSync.syncUInt32(theItem.mSeedType);
        theItem.mScaryPotType = theSync.syncUInt32(theItem.mScaryPotType);
        theItem.mHighlighted = theSync.syncBool(theItem.mHighlighted);
        theItem.mTransparentCounter = theSync.syncUInt32(theItem.mTransparentCounter);
        theItem.mSunCount = theSync.syncUInt32(theItem.mSunCount);
        for (int i = 0; i < GridItem.NUM_MOTION_TRAIL_FRAMES; i++) {
            syncMotionTrailFramePortable(theSync, theItem.mMotionTrailFrames[i]);
        }
        theItem.mMotionTrailCount = theSync.syncUInt32(theItem.mMotionTrailCount);
    }

    // ---------------------------------------------------------------------------------------
    // Effect-system bodies (SaveGame.cpp:1153-1360)
    // ---------------------------------------------------------------------------------------

    private static ReanimatorDefinition syncReanimationDefPortable(DataSync theSync, ReanimatorDefinition theDefinition) {
        ReanimatorDefinition[] aDefs = Reanimator.gReanimatorDefArray;
        if (isReading(theSync)) {
            int aReanimType = theSync.syncUInt32(ReanimationType.REANIM_NONE);
            if (aReanimType == ReanimationType.REANIM_NONE) {
                return null;
            }
            if (aDefs == null || aReanimType < 0 || aReanimType >= ReanimationType.NUM_REANIMS) {
                throw new SaveFormatException("invalid reanimation type " + aReanimType);
            }
            Reanimator.reanimatorEnsureDefinitionLoaded(aReanimType, true);
            return aDefs[aReanimType];
        }
        int aReanimType = ReanimationType.REANIM_NONE;
        if (aDefs != null) {
            for (int i = 0; i < aDefs.length; i++) {
                if (aDefs[i] == theDefinition) {
                    aReanimType = i;
                    break;
                }
            }
        }
        theSync.syncUInt32(aReanimType);
        return theDefinition;
    }

    private static PvzpParticleDefinition syncParticleDefPortable(DataSync theSync, PvzpParticleDefinition theDefinition) {
        PvzpParticleDefinition[] aDefs = PvzpParticleDefinitions.gParticleDefArray;
        if (isReading(theSync)) {
            int aParticleType = theSync.syncUInt32(ParticleEffect.PARTICLE_NONE);
            if (aParticleType == ParticleEffect.PARTICLE_NONE) {
                return null;
            }
            if (aDefs == null || aParticleType < 0 || aParticleType >= ParticleEffect.NUM_PARTICLES) {
                throw new SaveFormatException("invalid particle type " + aParticleType);
            }
            return aDefs[aParticleType];
        }
        int aParticleType = ParticleEffect.PARTICLE_NONE;
        if (aDefs != null) {
            for (int i = 0; i < aDefs.length; i++) {
                if (aDefs[i] == theDefinition) {
                    aParticleType = i;
                    break;
                }
            }
        }
        theSync.syncUInt32(aParticleType);
        return theDefinition;
    }

    private static TrailDefinition syncTrailDefPortable(DataSync theSync, TrailDefinition theDefinition) {
        TrailDefinition[] aDefs = TrailDefinitions.gTrailDefArray;
        if (isReading(theSync)) {
            int aTrailType = theSync.syncUInt32(TrailType.TRAIL_NONE);
            if (aTrailType == TrailType.TRAIL_NONE) {
                return null;
            }
            if (aDefs == null || aTrailType < 0 || aTrailType >= TrailType.NUM_TRAILS) {
                throw new SaveFormatException("invalid trail type " + aTrailType);
            }
            return aDefs[aTrailType];
        }
        int aTrailType = TrailType.TRAIL_NONE;
        if (aDefs != null) {
            for (int i = 0; i < aDefs.length; i++) {
                if (aDefs[i] == theDefinition) {
                    aTrailType = i;
                    break;
                }
            }
        }
        theSync.syncUInt32(aTrailType);
        return theDefinition;
    }

    /** {@code SyncDataIDListPortable} (SaveGame.cpp:495-533). */
    private static void syncDataIdListPortable(DataSync theSync, PvzpList<Integer> theList, cn.enaium.fabricpvz.portable.pvzp.PvzpAllocator theAllocator) {
        if (isReading(theSync)) {
            theList.removeAll();
            theList.setAllocator(theAllocator);
            int aCount = theSync.syncUInt32(0);
            for (int i = 0; i < aCount; i++) {
                theList.addTail(theSync.syncUInt32(0));
            }
        } else {
            theSync.syncUInt32(theList.mSize);
            for (PvzpListNode<Integer> aNode = theList.mHead; aNode != null; aNode = aNode.mNext) {
                theSync.syncUInt32(aNode.mValue);
            }
        }
    }

    /** {@code SyncReanimTransformPortable} (SaveGame.cpp:1116-1129). */
    private static void syncReanimTransformPortable(DataSync theSync, ReanimatorTransform theTransform) {
        theTransform.mTransX = theSync.syncFloat(theTransform.mTransX);
        theTransform.mTransY = theSync.syncFloat(theTransform.mTransY);
        theTransform.mSkewX = theSync.syncFloat(theTransform.mSkewX);
        theTransform.mSkewY = theSync.syncFloat(theTransform.mSkewY);
        theTransform.mScaleX = theSync.syncFloat(theTransform.mScaleX);
        theTransform.mScaleY = theSync.syncFloat(theTransform.mScaleY);
        theTransform.mFrame = theSync.syncFloat(theTransform.mFrame);
        theTransform.mAlpha = theSync.syncFloat(theTransform.mAlpha);
        if (isReading(theSync)) {
            theTransform.mImage = null;
            theTransform.mFont = null;
            theTransform.mText = "";
        }
    }

    /** {@code SyncReanimTrackInstancePortable} (SaveGame.cpp:1131-1151). */
    private static void syncReanimTrackInstancePortable(DataSync theSync, ReanimatorTrackInstance theTrackInstance) {
        theTrackInstance.mBlendCounter = theSync.syncUInt32(theTrackInstance.mBlendCounter);
        theTrackInstance.mBlendTime = theSync.syncUInt32(theTrackInstance.mBlendTime);
        syncReanimTransformPortable(theSync, theTrackInstance.mBlendTransform);
        theTrackInstance.mShakeOverride = theSync.syncFloat(theTrackInstance.mShakeOverride);
        theTrackInstance.mShakeX = theSync.syncFloat(theTrackInstance.mShakeX);
        theTrackInstance.mShakeY = theSync.syncFloat(theTrackInstance.mShakeY);
        theTrackInstance.mAttachmentID.value = theSync.syncUInt32(theTrackInstance.mAttachmentID.value);
        theTrackInstance.mImageOverride = syncImage(theSync, theTrackInstance.mImageOverride);
        theTrackInstance.mRenderGroup = theSync.syncUInt32(theTrackInstance.mRenderGroup);
        syncColor(theSync, theTrackInstance.mTrackColor);
        theTrackInstance.mIgnoreClipRect = theSync.syncBool(theTrackInstance.mIgnoreClipRect);
        theTrackInstance.mTruncateDisappearingFrames = theSync.syncBool(theTrackInstance.mTruncateDisappearingFrames);
        theTrackInstance.mIgnoreColorOverride = theSync.syncBool(theTrackInstance.mIgnoreColorOverride);
        theTrackInstance.mIgnoreExtraAdditiveColor = theSync.syncBool(theTrackInstance.mIgnoreExtraAdditiveColor);
    }

    /** {@code SyncReanimationPortable} (SaveGame.cpp:1153-1242). */
    private static void syncReanimationPortable(DataSync theSync, Board theBoard, Reanimation theReanimation) {
        theReanimation.mDefinition = syncReanimationDefPortable(theSync, theReanimation.mDefinition);
        if (isReading(theSync)) {
            theReanimation.mReanimationHolder = theBoard.mApp.mEffectSystem.mReanimationHolder;
        }

        ReanimatorDefinition aDef = theReanimation.mDefinition;
        ReanimatorDefinition[] aDefs = Reanimator.gReanimatorDefArray;
        if (aDef == null && aDefs != null) {
            int aType = theReanimation.mReanimationType;
            if (aType >= 0 && aType < ReanimationType.NUM_REANIMS) {
                Reanimator.reanimatorEnsureDefinitionLoaded(aType, true);
                aDef = aDefs[aType];
                if (isReading(theSync)) {
                    theReanimation.mDefinition = aDef;
                }
            }
        }

        theReanimation.mReanimationType = theSync.syncUInt32(theReanimation.mReanimationType);
        theReanimation.mAnimTime = theSync.syncFloat(theReanimation.mAnimTime);
        theReanimation.mAnimRate = theSync.syncFloat(theReanimation.mAnimRate);
        theReanimation.mLoopType = theSync.syncUInt32(theReanimation.mLoopType);
        theReanimation.mDead = theSync.syncBool(theReanimation.mDead);
        theReanimation.mFrameStart = theSync.syncUInt32(theReanimation.mFrameStart);
        theReanimation.mFrameCount = theSync.syncUInt32(theReanimation.mFrameCount);
        theReanimation.mFrameBasePose = theSync.syncUInt32(theReanimation.mFrameBasePose);
        syncMatrix(theSync, theReanimation.mOverlayMatrix);
        syncColor(theSync, theReanimation.mColorOverride);
        theReanimation.mLoopCount = theSync.syncUInt32(theReanimation.mLoopCount);
        theReanimation.mIsAttachment = theSync.syncBool(theReanimation.mIsAttachment);
        theReanimation.mRenderOrder = theSync.syncUInt32(theReanimation.mRenderOrder);
        syncColor(theSync, theReanimation.mExtraAdditiveColor);
        theReanimation.mEnableExtraAdditiveDraw = theSync.syncBool(theReanimation.mEnableExtraAdditiveDraw);
        syncColor(theSync, theReanimation.mExtraOverlayColor);
        theReanimation.mEnableExtraOverlayDraw = theSync.syncBool(theReanimation.mEnableExtraOverlayDraw);
        theReanimation.mLastFrameTime = theSync.syncFloat(theReanimation.mLastFrameTime);
        theReanimation.mFilterEffect = theSync.syncUInt32(theReanimation.mFilterEffect);

        if (aDef != null && aDef.mTracks.mArrayCount != 0) {
            int aCount = aDef.mTracks.mArrayCount;
            if (isReading(theSync)) {
                theReanimation.mTrackInstances = new ReanimatorTrackInstance[aCount];
                for (int i = 0; i < aCount; i++) {
                    theReanimation.mTrackInstances[i] = new ReanimatorTrackInstance();
                }
                for (int i = 0; i < aCount; i++) {
                    syncReanimTrackInstancePortable(theSync, theReanimation.mTrackInstances[i]);
                }
            } else if (theReanimation.mTrackInstances != null && theReanimation.mTrackInstances.length == aCount) {
                for (int i = 0; i < aCount; i++) {
                    syncReanimTrackInstancePortable(theSync, theReanimation.mTrackInstances[i]);
                }
            } else {
                ReanimatorTrackInstance[] aTemp = new ReanimatorTrackInstance[aCount];
                for (int i = 0; i < aCount; i++) {
                    aTemp[i] = new ReanimatorTrackInstance();
                }
                for (int i = 0; i < aCount; i++) {
                    syncReanimTrackInstancePortable(theSync, aTemp[i]);
                }
            }
        }
    }

    /** {@code SyncParticlePortable} (SaveGame.cpp:1245-1266). */
    private static void syncParticlePortable(DataSync theSync, PvzpParticle theParticle) {
        theParticle.mParticleDuration = theSync.syncUInt32(theParticle.mParticleDuration);
        theParticle.mParticleAge = theSync.syncUInt32(theParticle.mParticleAge);
        theParticle.mParticleTimeValue = theSync.syncFloat(theParticle.mParticleTimeValue);
        theParticle.mParticleLastTimeValue = theSync.syncFloat(theParticle.mParticleLastTimeValue);
        theParticle.mAnimationTimeValue = theSync.syncFloat(theParticle.mAnimationTimeValue);
        syncVector2(theSync, theParticle.mVelocity);
        syncVector2(theSync, theParticle.mPosition);
        theParticle.mImageFrame = theSync.syncUInt32(theParticle.mImageFrame);
        theParticle.mSpinPosition = theSync.syncFloat(theParticle.mSpinPosition);
        theParticle.mSpinVelocity = theSync.syncFloat(theParticle.mSpinVelocity);
        theParticle.mCrossFadeParticleID = theSync.syncUInt32(theParticle.mCrossFadeParticleID);
        theParticle.mCrossFadeDuration = theSync.syncUInt32(theParticle.mCrossFadeDuration);
        for (int i = 0; i < theParticle.mParticleInterp.length; i++) {
            theParticle.mParticleInterp[i] = theSync.syncFloat(theParticle.mParticleInterp[i]);
        }
        for (int i = 0; i < theParticle.mParticleFieldInterp.length; i++) {
            for (int j = 0; j < theParticle.mParticleFieldInterp[i].length; j++) {
                theParticle.mParticleFieldInterp[i][j] = theSync.syncFloat(theParticle.mParticleFieldInterp[i][j]);
            }
        }
    }

    /** {@code SyncParticleEmitterPortable} (SaveGame.cpp:1268-1316). */
    private static void syncParticleEmitterPortable(DataSync theSync, PvzpParticleSystem theParticleSystem, PvzpParticleEmitter theParticleEmitter) {
        PvzpEmitterDefinition[] aDefs = null;
        if (theParticleSystem.mParticleDef != null && theParticleSystem.mParticleDef.mEmitterDefs != null) {
            aDefs = (PvzpEmitterDefinition[]) theParticleSystem.mParticleDef.mEmitterDefs.mArrayData;
        }
        if (isReading(theSync)) {
            int aEmitterDefIndex = theSync.syncUInt32(0);
            theParticleEmitter.mParticleSystem = theParticleSystem;
            if (aDefs != null && aEmitterDefIndex >= 0 && aEmitterDefIndex < aDefs.length) {
                theParticleEmitter.mEmitterDef = aDefs[aEmitterDefIndex];
            }
        } else {
            int aEmitterDefIndex = 0;
            if (aDefs != null) {
                for (int i = 0; i < aDefs.length; i++) {
                    if (aDefs[i] == theParticleEmitter.mEmitterDef) {
                        aEmitterDefIndex = i;
                        break;
                    }
                }
            }
            theSync.syncUInt32(aEmitterDefIndex);
        }

        syncDataIdListPortable(theSync, theParticleEmitter.mParticleList, theParticleSystem.mParticleHolder.mParticleListNodeAllocator);
        syncVector2(theSync, theParticleEmitter.mSystemCenter);
        syncColor(theSync, theParticleEmitter.mColorOverride);
        theParticleEmitter.mImageOverride = syncImage(theSync, theParticleEmitter.mImageOverride);
        theParticleEmitter.mSpawnAccum = theSync.syncFloat(theParticleEmitter.mSpawnAccum);
        theParticleEmitter.mParticlesSpawned = theSync.syncUInt32(theParticleEmitter.mParticlesSpawned);
        theParticleEmitter.mSystemAge = theSync.syncUInt32(theParticleEmitter.mSystemAge);
        theParticleEmitter.mSystemDuration = theSync.syncUInt32(theParticleEmitter.mSystemDuration);
        theParticleEmitter.mSystemTimeValue = theSync.syncFloat(theParticleEmitter.mSystemTimeValue);
        theParticleEmitter.mSystemLastTimeValue = theSync.syncFloat(theParticleEmitter.mSystemLastTimeValue);
        theParticleEmitter.mDead = theSync.syncBool(theParticleEmitter.mDead);
        theParticleEmitter.mExtraAdditiveDrawOverride = theSync.syncBool(theParticleEmitter.mExtraAdditiveDrawOverride);
        theParticleEmitter.mScaleOverride = theSync.syncFloat(theParticleEmitter.mScaleOverride);
        theParticleEmitter.mCrossFadeEmitterID = theSync.syncUInt32(theParticleEmitter.mCrossFadeEmitterID);
        theParticleEmitter.mEmitterCrossFadeCountDown = theSync.syncUInt32(theParticleEmitter.mEmitterCrossFadeCountDown);
        theParticleEmitter.mFrameOverride = theSync.syncUInt32(theParticleEmitter.mFrameOverride);
        for (int i = 0; i < ParticleSystemTracks.NUM_SYSTEM_TRACKS; i++) {
            theParticleEmitter.mTrackInterp[i] = theSync.syncFloat(theParticleEmitter.mTrackInterp[i]);
        }
        for (int i = 0; i < theParticleEmitter.mSystemFieldInterp.length; i++) {
            for (int j = 0; j < theParticleEmitter.mSystemFieldInterp[i].length; j++) {
                theParticleEmitter.mSystemFieldInterp[i][j] = theSync.syncFloat(theParticleEmitter.mSystemFieldInterp[i][j]);
            }
        }

        for (PvzpListNode<Integer> aNode = theParticleEmitter.mParticleList.mHead; aNode != null; ) {
            PvzpListNode<Integer> aNext = aNode.mNext;
            // Upstream resolves the id with mParticles.DataArrayGet (SaveGame.cpp:1307), which returns
            // the array's own slot object; the port's ids-only chunk reconstructs those slots
            // (syncDataArrayIdsOnly), so the saved id resolves here too. An id whose slot the save does
            // not carry names an object that does not exist - upstream's DataArrayGet has no liveness
            // check in release builds (DataArray.h:186-190), so a vanished particle is not a load
            // failure: drop its node, the way GameSelector drops a stale reanimation id
            // (reanimationTryToGet + skip).
            PvzpParticle aParticle = theParticleSystem.mParticleHolder.mParticles.dataArrayTryToGet(aNode.mValue);
            if (aParticle == null) {
                theParticleEmitter.mParticleList.removeAt(aNode);
            } else {
                if (isReading(theSync)) {
                    aParticle.mParticleEmitter = theParticleEmitter;
                }
                syncParticlePortable(theSync, aParticle);
            }
            aNode = aNext;
        }
    }

    /** {@code SyncParticleSystemPortable} (SaveGame.cpp:1319-1338). */
    private static void syncParticleSystemPortable(DataSync theSync, Board theBoard, PvzpParticleSystem theParticleSystem) {
        theParticleSystem.mParticleDef = syncParticleDefPortable(theSync, theParticleSystem.mParticleDef);
        if (isReading(theSync)) {
            theParticleSystem.mParticleHolder = theBoard.mApp.mEffectSystem.mParticleHolder;
        }
        syncDataIdListPortable(theSync, theParticleSystem.mEmitterList, theParticleSystem.mParticleHolder.mEmitterListNodeAllocator);
        for (PvzpListNode<Integer> aNode = theParticleSystem.mEmitterList.mHead; aNode != null; ) {
            PvzpListNode<Integer> aNext = aNode.mNext;
            // Same shape as the particle list above: upstream's mEmitters.DataArrayGet
            // (SaveGame.cpp:1330) returns the slot object for the id the save carries, and a node whose
            // id has no slot is a stale entry from a save that no longer carries it - dropped instead
            // of dereferenced forever on the first frame after the load.
            PvzpParticleEmitter aEmitter = theParticleSystem.mParticleHolder.mEmitters.dataArrayTryToGet(aNode.mValue);
            if (aEmitter == null) {
                theParticleSystem.mEmitterList.removeAt(aNode);
            } else {
                syncParticleEmitterPortable(theSync, theParticleSystem, aEmitter);
            }
            aNode = aNext;
        }
        theParticleSystem.mEffectType = theSync.syncUInt32(theParticleSystem.mEffectType);
        theParticleSystem.mDead = theSync.syncBool(theParticleSystem.mDead);
        theParticleSystem.mIsAttachment = theSync.syncBool(theParticleSystem.mIsAttachment);
        theParticleSystem.mRenderOrder = theSync.syncUInt32(theParticleSystem.mRenderOrder);
        theParticleSystem.mDontUpdate = theSync.syncBool(theParticleSystem.mDontUpdate);
    }

    /** {@code SyncTrailPortable} (SaveGame.cpp:1341-1360). */
    private static void syncTrailPortable(DataSync theSync, Board theBoard, Trail theTrail) {
        theTrail.mDefinition = syncTrailDefPortable(theSync, theTrail.mDefinition);
        if (isReading(theSync)) {
            theTrail.mTrailHolder = theBoard.mApp.mEffectSystem.mTrailHolder;
        }
        for (int i = 0; i < 20; i++) {
            syncVector2(theSync, theTrail.mTrailPoints[i].aPos);
        }
        theTrail.mNumTrailPoints = theSync.syncUInt32(theTrail.mNumTrailPoints);
        theTrail.mDead = theSync.syncBool(theTrail.mDead);
        theTrail.mRenderOrder = theSync.syncUInt32(theTrail.mRenderOrder);
        theTrail.mTrailAge = theSync.syncUInt32(theTrail.mTrailAge);
        theTrail.mTrailDuration = theSync.syncUInt32(theTrail.mTrailDuration);
        for (int i = 0; i < theTrail.mTrailInterp.length; i++) {
            theTrail.mTrailInterp[i] = theSync.syncFloat(theTrail.mTrailInterp[i]);
        }
        syncVector2(theSync, theTrail.mTrailCenter);
        theTrail.mIsAttachment = theSync.syncBool(theTrail.mIsAttachment);
        syncColor(theSync, theTrail.mColorOverride);
    }

    // ---------------------------------------------------------------------------------------
    // Board base (SaveGame.cpp:1488-1750)
    // ---------------------------------------------------------------------------------------

    /** The {@code gBoardBaseFields} table, in field-id order (ids are {@code index + 1}). */
    private static PortableSync[] boardBaseFields(Board b) {
        return new PortableSync[] {
            s -> b.mPaused = s.syncBool(b.mPaused),
            s -> {
                for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
                    for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                        b.mGridSquareType[x][y] = s.syncUInt32(b.mGridSquareType[x][y]);
                    }
                }
            },
            s -> {
                for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
                    for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                        b.mGridCelLook[x][y] = s.syncUInt32(b.mGridCelLook[x][y]);
                    }
                }
            },
            s -> {
                for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
                    for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                        for (int k = 0; k < 2; k++) {
                            b.mGridCelOffset[x][y][k] = s.syncUInt32(b.mGridCelOffset[x][y][k]);
                        }
                    }
                }
            },
            s -> {
                for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
                    for (int y = 0; y < Board.MAX_GRID_SIZE_Y + 1; y++) {
                        b.mGridCelFog[x][y] = s.syncUInt32(b.mGridCelFog[x][y]);
                    }
                }
            },
            s -> b.mEnableGraveStones = s.syncBool(b.mEnableGraveStones),
            s -> b.mSpecialGraveStoneX = s.syncUInt32(b.mSpecialGraveStoneX),
            s -> b.mSpecialGraveStoneY = s.syncUInt32(b.mSpecialGraveStoneY),
            s -> b.mFogOffset = s.syncFloat(b.mFogOffset),
            s -> b.mFogBlownCountDown = s.syncUInt32(b.mFogBlownCountDown),
            s -> {
                for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                    b.mPlantRow[i] = s.syncUInt32(b.mPlantRow[i]);
                }
            },
            s -> {
                for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                    b.mWaveRowGotLawnMowered[i] = s.syncUInt32(b.mWaveRowGotLawnMowered[i]);
                }
            },
            s -> b.mBonusLawnMowersRemaining = s.syncUInt32(b.mBonusLawnMowersRemaining),
            s -> {
                for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                    b.mIceMinX[i] = s.syncUInt32(b.mIceMinX[i]);
                }
            },
            s -> {
                for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                    b.mIceTimer[i] = s.syncUInt32(b.mIceTimer[i]);
                }
            },
            s -> {
                for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                    b.mIceParticleID[i] = s.syncUInt32(b.mIceParticleID[i]);
                }
            },
            s -> {
                for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                    PvzpSmoothArray aArray = b.mRowPickingArray[i];
                    aArray.mItem = s.syncUInt32(aArray.mItem);
                    aArray.mWeight = s.syncFloat(aArray.mWeight);
                    aArray.mLastPicked = s.syncFloat(aArray.mLastPicked);
                    aArray.mSecondLastPicked = s.syncFloat(aArray.mSecondLastPicked);
                }
            },
            s -> {
                for (int w = 0; w < Board.MAX_ZOMBIE_WAVES; w++) {
                    for (int i = 0; i < Board.MAX_ZOMBIES_IN_WAVE; i++) {
                        b.mZombiesInWave[w][i] = s.syncUInt32(b.mZombiesInWave[w][i]);
                    }
                }
            },
            s -> {
                for (int i = 0; i < 100; i++) {
                    b.mZombieAllowed[i] = s.syncBool(b.mZombieAllowed[i]);
                }
            },
            s -> b.mSunCountDown = s.syncUInt32(b.mSunCountDown),
            s -> b.mNumSunsFallen = s.syncUInt32(b.mNumSunsFallen),
            s -> b.mShakeCounter = s.syncUInt32(b.mShakeCounter),
            s -> b.mShakeAmountX = s.syncUInt32(b.mShakeAmountX),
            s -> b.mShakeAmountY = s.syncUInt32(b.mShakeAmountY),
            s -> b.mBackground = s.syncUInt32(b.mBackground),
            s -> b.mLevel = s.syncUInt32(b.mLevel),
            s -> b.mSodPosition = s.syncUInt32(b.mSodPosition),
            s -> b.mPrevMouseX = s.syncUInt32(b.mPrevMouseX),
            s -> b.mPrevMouseY = s.syncUInt32(b.mPrevMouseY),
            s -> b.mSunMoney = s.syncUInt32(b.mSunMoney),
            s -> b.mNumWaves = s.syncUInt32(b.mNumWaves),
            s -> b.mMainCounter = s.syncUInt32(b.mMainCounter),
            s -> b.mEffectCounter = s.syncUInt32(b.mEffectCounter),
            s -> b.mDrawCount = s.syncUInt32(b.mDrawCount),
            s -> b.mRiseFromGraveCounter = s.syncUInt32(b.mRiseFromGraveCounter),
            s -> b.mOutOfMoneyCounter = s.syncUInt32(b.mOutOfMoneyCounter),
            s -> b.mCurrentWave = s.syncUInt32(b.mCurrentWave),
            s -> b.mTotalSpawnedWaves = s.syncUInt32(b.mTotalSpawnedWaves),
            s -> b.mTutorialState = s.syncUInt32(b.mTutorialState),
            s -> b.mTutorialParticleID = s.syncUInt32(b.mTutorialParticleID),
            s -> b.mTutorialTimer = s.syncUInt32(b.mTutorialTimer),
            s -> b.mLastBungeeWave = s.syncUInt32(b.mLastBungeeWave),
            s -> b.mZombieHealthToNextWave = s.syncUInt32(b.mZombieHealthToNextWave),
            s -> b.mZombieHealthWaveStart = s.syncUInt32(b.mZombieHealthWaveStart),
            s -> b.mZombieCountDown = s.syncUInt32(b.mZombieCountDown),
            s -> b.mZombieCountDownStart = s.syncUInt32(b.mZombieCountDownStart),
            s -> b.mHugeWaveCountDown = s.syncUInt32(b.mHugeWaveCountDown),
            s -> {
                for (int i = 0; i < AdviceType.NUM_ADVICE_TYPES; i++) {
                    b.mHelpDisplayed[i] = s.syncBool(b.mHelpDisplayed[i]);
                }
            },
            s -> b.mHelpIndex = s.syncUInt32(b.mHelpIndex),
            s -> b.mFinalBossKilled = s.syncBool(b.mFinalBossKilled),
            s -> b.mShowShovel = s.syncBool(b.mShowShovel),
            s -> b.mCoinBankFadeCount = s.syncUInt32(b.mCoinBankFadeCount),
            s -> b.mDebugTextMode = s.syncUInt32(b.mDebugTextMode),
            s -> b.mLevelComplete = s.syncBool(b.mLevelComplete),
            s -> b.mBoardFadeOutCounter = s.syncUInt32(b.mBoardFadeOutCounter),
            s -> b.mNextSurvivalStageCounter = s.syncUInt32(b.mNextSurvivalStageCounter),
            s -> b.mScoreNextMowerCounter = s.syncUInt32(b.mScoreNextMowerCounter),
            s -> b.mLevelAwardSpawned = s.syncBool(b.mLevelAwardSpawned),
            s -> b.mProgressMeterWidth = s.syncUInt32(b.mProgressMeterWidth),
            s -> b.mFlagRaiseCounter = s.syncUInt32(b.mFlagRaiseCounter),
            s -> b.mIceTrapCounter = s.syncUInt32(b.mIceTrapCounter),
            s -> b.mBoardRandSeed = s.syncUInt32(b.mBoardRandSeed),
            s -> b.mPoolSparklyParticleID = s.syncUInt32(b.mPoolSparklyParticleID),
            s -> {
                for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                    for (int i = 0; i < 12; i++) {
                        b.mFwooshID[y][i] = s.syncUInt32(b.mFwooshID[y][i]);
                    }
                }
            },
            s -> b.mFwooshCountDown = s.syncUInt32(b.mFwooshCountDown),
            s -> b.mTimeStopCounter = s.syncUInt32(b.mTimeStopCounter),
            s -> b.mDroppedFirstCoin = s.syncBool(b.mDroppedFirstCoin),
            s -> b.mFinalWaveSoundCounter = s.syncUInt32(b.mFinalWaveSoundCounter),
            s -> b.mCobCannonCursorDelayCounter = s.syncUInt32(b.mCobCannonCursorDelayCounter),
            s -> b.mCobCannonMouseX = s.syncUInt32(b.mCobCannonMouseX),
            s -> b.mCobCannonMouseY = s.syncUInt32(b.mCobCannonMouseY),
            s -> b.mKilledYeti = s.syncBool(b.mKilledYeti),
            s -> b.mMustacheMode = s.syncBool(b.mMustacheMode),
            s -> b.mSuperMowerMode = s.syncBool(b.mSuperMowerMode),
            s -> b.mFutureMode = s.syncBool(b.mFutureMode),
            s -> b.mPinataMode = s.syncBool(b.mPinataMode),
            s -> b.mDanceMode = s.syncBool(b.mDanceMode),
            s -> b.mDaisyMode = s.syncBool(b.mDaisyMode),
            s -> b.mSukhbirMode = s.syncBool(b.mSukhbirMode),
            s -> b.mPrevBoardResult = s.syncUInt32(b.mPrevBoardResult),
            s -> b.mTriggeredLawnMowers = s.syncUInt32(b.mTriggeredLawnMowers),
            s -> b.mPlayTimeActiveLevel = s.syncUInt32(b.mPlayTimeActiveLevel),
            s -> b.mPlayTimeInactiveLevel = s.syncUInt32(b.mPlayTimeInactiveLevel),
            s -> b.mMaxSunPlants = s.syncUInt32(b.mMaxSunPlants),
            s -> b.mStartDrawTime = s.syncUInt64(b.mStartDrawTime),
            s -> b.mIntervalDrawTime = s.syncUInt64(b.mIntervalDrawTime),
            s -> b.mIntervalDrawCountStart = s.syncUInt32(b.mIntervalDrawCountStart),
            s -> b.mMinFPS = s.syncFloat(b.mMinFPS),
            s -> b.mPreloadTime = s.syncUInt32(b.mPreloadTime),
            s -> b.mGameID = s.syncUInt64(b.mGameID),
            s -> b.mGravesCleared = s.syncUInt32(b.mGravesCleared),
            s -> b.mPlantsEaten = s.syncUInt32(b.mPlantsEaten),
            s -> b.mPlantsShoveled = s.syncUInt32(b.mPlantsShoveled),
            s -> b.mPeaShooterUsed = s.syncBool(b.mPeaShooterUsed),
            s -> b.mCatapultPlantsUsed = s.syncBool(b.mCatapultPlantsUsed),
            s -> b.mMushroomAndCoffeeBeansOnly = s.syncBool(b.mMushroomAndCoffeeBeansOnly),
            s -> b.mMushroomsUsed = s.syncBool(b.mMushroomsUsed),
            s -> b.mLevelCoinsCollected = s.syncUInt32(b.mLevelCoinsCollected),
            s -> b.mGargantuarsKillsByCornCob = s.syncUInt32(b.mGargantuarsKillsByCornCob),
            s -> b.mCoinsCollected = s.syncUInt32(b.mCoinsCollected),
            s -> b.mDiamondsCollected = s.syncUInt32(b.mDiamondsCollected),
            s -> b.mPottedPlantsCollected = s.syncUInt32(b.mPottedPlantsCollected),
            s -> b.mChocolateCollected = s.syncUInt32(b.mChocolateCollected)
        };
    }

    /** {@code SyncBoardBasePortable} (SaveGame.cpp:1719-1750). */
    public static void syncBoardBasePortable(DataSync theSync, Board theBoard) {
        PortableSync[] aFields = boardBaseFields(theBoard);
        if (isReading(theSync)) {
            byte[] aBlob = readTlvBlob(theSync);
            readTlv(aBlob, (aFieldId, aFieldData) -> {
                if (aFieldId >= 1 && aFieldId <= aFields.length) {
                    PortableSync aField = aFields[aFieldId - 1];
                    applyField(aFieldData, aField);
                }
            });
        } else {
            ByteArrayOutputStream aBlob = new ByteArrayOutputStream();
            for (int i = 0; i < aFields.length; i++) {
                appendField(aBlob, i + 1, aFields[i]);
            }
            writeTlvBlob(theSync, aBlob.toByteArray());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Chunk sync fns (SaveGame.cpp:1752-1930)
    // ---------------------------------------------------------------------------------------

    public static void syncZombiesPortable(DataSync theSync, Board theBoard) {
        syncDataArrayGameObjectsTlv(theSync, theBoard.mZombies, SaveGame::syncZombieTailPortable);
    }

    public static void syncPlantsPortable(DataSync theSync, Board theBoard) {
        syncDataArrayGameObjectsTlv(theSync, theBoard.mPlants, SaveGame::syncPlantTailPortable);
    }

    public static void syncProjectilesPortable(DataSync theSync, Board theBoard) {
        syncDataArrayGameObjectsTlv(theSync, theBoard.mProjectiles, SaveGame::syncProjectileTailPortable);
    }

    public static void syncCoinsPortable(DataSync theSync, Board theBoard) {
        syncDataArrayGameObjectsTlv(theSync, theBoard.mCoins, SaveGame::syncCoinTailPortable);
    }

    public static void syncMowersPortable(DataSync theSync, Board theBoard) {
        syncDataArrayObjectsNoBaseTlv(theSync, theBoard.mLawnMowers, SaveGame::syncLawnMowerTailPortable);
    }

    public static void syncGridItemsPortable(DataSync theSync, Board theBoard) {
        syncDataArrayObjectsNoBaseTlv(theSync, theBoard.mGridItems, SaveGame::syncGridItemTailPortable);
    }

    public static void syncParticleEmittersPortable(DataSync theSync, Board theBoard) {
        syncDataArrayIdsOnly(theSync, theBoard.mApp.mEffectSystem.mParticleHolder.mEmitters);
    }

    public static void syncParticlesPortable(DataSync theSync, Board theBoard) {
        syncDataArrayIdsOnly(theSync, theBoard.mApp.mEffectSystem.mParticleHolder.mParticles);
    }

    public static void syncParticleSystemsPortable(DataSync theSync, Board theBoard) {
        syncDataArrayItemField1Tlv(theSync, theBoard.mApp.mEffectSystem.mParticleHolder.mParticleSystems,
                (s, item) -> syncParticleSystemPortable(s, theBoard, item));
    }

    public static void syncReanimationsPortable(DataSync theSync, Board theBoard) {
        syncDataArrayItemField1Tlv(theSync, theBoard.mApp.mEffectSystem.mReanimationHolder.mReanimations,
                (s, item) -> syncReanimationPortable(s, theBoard, item));
    }

    public static void syncTrailsPortable(DataSync theSync, Board theBoard) {
        syncDataArrayItemField1Tlv(theSync, theBoard.mApp.mEffectSystem.mTrailHolder.mTrails,
                (s, item) -> syncTrailPortable(s, theBoard, item));
    }

    public static void syncAttachmentsPortable(DataSync theSync, Board theBoard) {
        syncDataArrayObjectsNoBaseTlv(theSync, theBoard.mApp.mEffectSystem.mAttachmentHolder.mAttachments,
                SaveGame::syncAttachmentTailPortable);
    }

    public static void syncCursorPortable(DataSync theSync, Board theBoard) {
        syncSingleObjectTlv(theSync, true, theBoard.mCursorObject,
                s -> syncCursorObjectTailPortable(s, theBoard.mCursorObject));
    }

    public static void syncCursorPreviewPortable(DataSync theSync, Board theBoard) {
        syncSingleObjectTlv(theSync, true, theBoard.mCursorPreview,
                s -> syncCursorPreviewTailPortable(s, theBoard.mCursorPreview));
    }

    public static void syncAdvicePortable(DataSync theSync, Board theBoard) {
        syncSingleObjectTlv(theSync, false, null, s -> syncMessageWidgetTailPortable(s, theBoard.mAdvice));
    }

    public static void syncSeedBankPortable(DataSync theSync, Board theBoard) {
        syncSingleObjectTlv(theSync, true, theBoard.mSeedBank,
                s -> syncSeedBankTailPortable(s, theBoard.mSeedBank));
    }

    /** {@code SyncSeedPacketsPortable} (SaveGame.cpp:1883-1926). */
    public static void syncSeedPacketsPortable(DataSync theSync, Board theBoard) {
        int aCount = cn.enaium.fabricpvz.portable.lawn.GameConstants.SEEDBANK_MAX;
        aCount = theSync.syncUInt32(aCount);
        for (int i = 0; i < aCount && i < cn.enaium.fabricpvz.portable.lawn.GameConstants.SEEDBANK_MAX; i++) {
            SeedPacket aPacket = theBoard.mSeedBank.mSeedPackets[i];
            if (isReading(theSync)) {
                int aItemSize = theSync.syncUInt32(0);
                if (aItemSize < 0) {
                    throw new SaveFormatException("negative seed packet size");
                }
                byte[] aItemData = new byte[aItemSize];
                if (aItemSize > 0) {
                    theSync.syncBytes(aItemData);
                }
                readTlv(aItemData, (aFieldId, aFieldData) -> {
                    if (aFieldId == 1) {
                        applyField(aFieldData, aPacket::syncGameObjectPortable);
                    } else if (aFieldId == PORTABLE_FIELD_TAIL) {
                        applyField(aFieldData, s -> syncSeedPacketTailPortable(s, aPacket));
                    }
                });
            } else {
                ByteArrayOutputStream aBlob = new ByteArrayOutputStream();
                appendField(aBlob, 1, aPacket::syncGameObjectPortable);
                appendField(aBlob, PORTABLE_FIELD_TAIL, s -> syncSeedPacketTailPortable(s, aPacket));
                byte[] aItemData = aBlob.toByteArray();
                theSync.syncUInt32(aItemData.length);
                if (aItemData.length > 0) {
                    theSync.syncBytes(aItemData);
                }
            }
        }
    }

    public static void syncChallengePortable(DataSync theSync, Board theBoard) {
        syncSingleObjectTlv(theSync, false, null, s -> syncChallengeTailPortable(s, theBoard.mChallenge));
    }

    public static void syncMusicPortable(DataSync theSync, Board theBoard) {
        syncSingleObjectTlv(theSync, false, null, s -> syncMusicTailPortable(s, theBoard.mApp.mMusic));
    }

    // ---------------------------------------------------------------------------------------
    // Chunk dispatch / framing (SaveGame.cpp:1965-2080)
    // ---------------------------------------------------------------------------------------

    private static PortableSync chunkSyncFn(int theChunkType, Board theBoard) {
        switch (theChunkType) {
            case SAVE4_CHUNK_BOARD_BASE: return s -> syncBoardBasePortable(s, theBoard);
            case SAVE4_CHUNK_ZOMBIES: return s -> syncZombiesPortable(s, theBoard);
            case SAVE4_CHUNK_PLANTS: return s -> syncPlantsPortable(s, theBoard);
            case SAVE4_CHUNK_PROJECTILES: return s -> syncProjectilesPortable(s, theBoard);
            case SAVE4_CHUNK_COINS: return s -> syncCoinsPortable(s, theBoard);
            case SAVE4_CHUNK_MOWERS: return s -> syncMowersPortable(s, theBoard);
            case SAVE4_CHUNK_GRIDITEMS: return s -> syncGridItemsPortable(s, theBoard);
            case SAVE4_CHUNK_PARTICLE_EMITTERS: return s -> syncParticleEmittersPortable(s, theBoard);
            case SAVE4_CHUNK_PARTICLE_PARTICLES: return s -> syncParticlesPortable(s, theBoard);
            case SAVE4_CHUNK_PARTICLE_SYSTEMS: return s -> syncParticleSystemsPortable(s, theBoard);
            case SAVE4_CHUNK_REANIMATIONS: return s -> syncReanimationsPortable(s, theBoard);
            case SAVE4_CHUNK_TRAILS: return s -> syncTrailsPortable(s, theBoard);
            case SAVE4_CHUNK_ATTACHMENTS: return s -> syncAttachmentsPortable(s, theBoard);
            case SAVE4_CHUNK_CURSOR: return s -> syncCursorPortable(s, theBoard);
            case SAVE4_CHUNK_CURSOR_PREVIEW: return s -> syncCursorPreviewPortable(s, theBoard);
            case SAVE4_CHUNK_ADVICE: return s -> syncAdvicePortable(s, theBoard);
            case SAVE4_CHUNK_SEEDBANK: return s -> syncSeedBankPortable(s, theBoard);
            case SAVE4_CHUNK_SEEDPACKETS: return s -> syncSeedPacketsPortable(s, theBoard);
            case SAVE4_CHUNK_CHALLENGE: return s -> syncChallengePortable(s, theBoard);
            case SAVE4_CHUNK_MUSIC: return s -> syncMusicPortable(s, theBoard);
            default: return null;
        }
    }

    /** A parsed chunk frame (tests and {@link #parseChunkFrames}). */
    public static final class ChunkFrame {
        public final int mChunkType;
        public final int mChunkVersion;
        public final int mFieldId;
        public final byte[] mBody;

        ChunkFrame(int theChunkType, int theChunkVersion, int theFieldId, byte[] theBody) {
            mChunkType = theChunkType;
            mChunkVersion = theChunkVersion;
            mFieldId = theFieldId;
            mBody = theBody;
        }
    }

    /**
     * {@code AppendChunk} (SaveGame.cpp:113-118) + the {@code WriteChunkV4} field header
     * (SaveGame.cpp:2014-2039): {@code u32 chunkType, u32 chunkDataSize, [u32 version, u32
     * fieldId=1, u32 bodyLen, body]}.
     */
    public static byte[] encodeChunk(int theChunkType, byte[] theFieldBody) {
        ByteArrayOutputStream aChunkData = new ByteArrayOutputStream();
        writeU32LE(aChunkData, SAVE4_CHUNK_VERSION);
        writeU32LE(aChunkData, 1);
        writeU32LE(aChunkData, theFieldBody.length);
        aChunkData.write(theFieldBody, 0, theFieldBody.length);
        byte[] aChunk = aChunkData.toByteArray();

        ByteArrayOutputStream aFrame = new ByteArrayOutputStream();
        writeU32LE(aFrame, theChunkType);
        writeU32LE(aFrame, aChunk.length);
        aFrame.write(aChunk, 0, aChunk.length);
        return aFrame.toByteArray();
    }

    /**
     * Parses the chunk frames of a payload, validating the framing and every chunk version.
     *
     * @throws IllegalArgumentException when the payload is truncated or a chunk version is not
     *     {@link #SAVE4_CHUNK_VERSION}
     */
    public static ChunkFrame[] parseChunkFrames(byte[] thePayload) {
        List<ChunkFrame> aFrames = new ArrayList<>();
        int aPos = 0;
        while (aPos < thePayload.length) {
            if (aPos + 8 > thePayload.length) {
                throw new IllegalArgumentException("truncated chunk header at offset " + aPos);
            }
            int aChunkType = readU32LE(thePayload, aPos);
            int aChunkSize = readU32LE(thePayload, aPos + 4);
            aPos += 8;
            if (aChunkSize < 8 || aPos + aChunkSize > thePayload.length) {
                throw new IllegalArgumentException("invalid chunk size " + aChunkSize + " for chunk type " + aChunkType);
            }
            int aChunkVersion = readU32LE(thePayload, aPos);
            if (aChunkVersion != SAVE4_CHUNK_VERSION) {
                throw new IllegalArgumentException("unsupported chunk version " + aChunkVersion
                        + " for chunk type " + aChunkType);
            }
            int aFieldId = readU32LE(thePayload, aPos + 4);
            int aFieldLen = readU32LE(thePayload, aPos + 8);
            if (aFieldLen < 0 || 12 + aFieldLen > aChunkSize) {
                throw new IllegalArgumentException("invalid field length " + aFieldLen + " for chunk type " + aChunkType);
            }
            byte[] aBody = new byte[aFieldLen];
            System.arraycopy(thePayload, aPos + 12, aBody, 0, aFieldLen);
            aFrames.add(new ChunkFrame(aChunkType, aChunkVersion, aFieldId, aBody));
            aPos += aChunkSize;
        }
        return aFrames.toArray(new ChunkFrame[0]);
    }

    /** {@code WriteChunkV4} (SaveGame.cpp:2014-2039). Returns null for unknown chunk types. */
    private static byte[] writeChunkV4(int theChunkType, Board theBoard) {
        PortableSync aSyncFn = chunkSyncFn(theChunkType, theBoard);
        if (aSyncFn == null) {
            return null;
        }
        return encodeChunk(theChunkType, syncFieldBytes(aSyncFn));
    }

    /** {@code ReadChunkV4} (SaveGame.cpp:2041-2080). */
    private static void readChunkV4(int theChunkType, byte[] theChunkData, Board theBoard) {
        PortableSync aSyncFn = chunkSyncFn(theChunkType, theBoard);
        if (aSyncFn == null) {
            return;
        }
        if (theChunkData.length < 4) {
            throw new SaveFormatException("chunk " + theChunkType + " too small");
        }
        int aChunkVersion = readU32LE(theChunkData, 0);
        if (aChunkVersion != SAVE4_CHUNK_VERSION) {
            throw new SaveFormatException("unsupported chunk version " + aChunkVersion
                    + " for chunk " + theChunkType);
        }
        boolean aApplied = false;
        int aPos = 4;
        while (aPos + 8 <= theChunkData.length) {
            int aFieldId = readU32LE(theChunkData, aPos);
            int aFieldSize = readU32LE(theChunkData, aPos + 4);
            aPos += 8;
            if (aPos + aFieldSize > theChunkData.length) {
                break;
            }
            byte[] aFieldData = new byte[aFieldSize];
            System.arraycopy(theChunkData, aPos, aFieldData, 0, aFieldSize);
            aPos += aFieldSize;
            if (aFieldId == 1) {
                if (!applyField(aFieldData, aSyncFn)) {
                    throw new SaveFormatException("chunk " + theChunkType + " field 1 failed to apply");
                }
                aApplied = true;
            }
        }
        if (!aApplied) {
            throw new SaveFormatException("chunk " + theChunkType + " contains no field 1");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Legacy 0xFEEDDEAD container (SaveGame.cpp:48-69, 2758-2789)
    // ---------------------------------------------------------------------------------------

    /**
     * The 12-byte legacy {@code SaveFileHeader} (SaveGame.cpp:64-69), recognised with upstream's
     * exact accept test in {@link #isRecognized()} ({@code LawnLoadGame}, SaveGame.cpp:2767-2775).
     */
    public static final class LegacySaveHeader {
        public final int mMagicNumber;
        public final int mBuildVersion;
        public final int mBuildDate;

        LegacySaveHeader(int theMagicNumber, int theBuildVersion, int theBuildDate) {
            mMagicNumber = theMagicNumber;
            mBuildVersion = theBuildVersion;
            mBuildDate = theBuildDate;
        }

        /**
         * {@code aHeader.mMagicNumber != SAVE_FILE_MAGIC_NUMBER || aHeader.mBuildVersion !=
         * SAVE_FILE_VERSION || aHeader.mBuildDate != SAVE_FILE_DATE} (SaveGame.cpp:2768-2772): when any
         * of the three differs, upstream treats the file as "not a save" and {@code LawnLoadGame}
         * returns false.
         */
        public boolean isRecognized() {
            return mMagicNumber == SAVE_FILE_MAGIC_NUMBER
                    && mBuildVersion == SAVE_FILE_VERSION
                    && mBuildDate == SAVE_FILE_DATE;
        }

        @Override
        public String toString() {
            return "LegacySaveHeader[magic=0x" + String.format("%08X", mMagicNumber)
                    + ", buildVersion=" + mBuildVersion
                    + ", buildDate=0x" + String.format("%08X", mBuildDate) + "]";
        }
    }

    /**
     * Thrown when a file is a <em>recognised</em> legacy mid-level save. The legacy {@code .dat}
     * container is a raw C++ memory image and therefore cannot be decoded into this port's object
     * graph; see the class javadoc for the upstream evidence (SaveGame.cpp:2661-2690, 2700,
     * 2736-2741, 2585-2590 and README.md "Save data compatibility").
     */
    public static final class LegacySaveFormatException extends UnsupportedOperationException {
        private final LegacySaveHeader mHeader;

        LegacySaveFormatException(LegacySaveHeader theHeader) {
            super(legacyRejectionMessage(theHeader));
            mHeader = theHeader;
        }

        /** The recognised header of the rejected file. */
        public LegacySaveHeader getLegacyHeader() {
            return mHeader;
        }
    }

    /**
     * Parses the legacy header when {@code theFile} starts with the legacy magic
     * ({@code SaveFileHeader}, SaveGame.cpp:64-69).
     *
     * @return the header, or {@code null} when the file is too short or does not carry the legacy
     *     magic (i.e. it is a {@code .v4} file or not a save at all)
     */
    public static LegacySaveHeader parseLegacySaveHeader(byte[] theFile) {
        if (theFile == null || theFile.length < SAVE_FILE_HEADER_SIZE) {
            return null;
        }
        int aMagicNumber = readU32LE(theFile, 0);
        if (aMagicNumber != SAVE_FILE_MAGIC_NUMBER) {
            return null;
        }
        return new LegacySaveHeader(aMagicNumber, readU32LE(theFile, 4), readU32LE(theFile, 8));
    }

    /**
     * True when {@code theFile} is a legacy mid-level save that upstream itself would accept: legacy
     * magic, build version 2 and the build date of the {@code "Jul  2 201011:47:03"} build
     * (SaveGame.cpp:2768-2772). Such a file is a raw memory dump and is rejected by this port.
     */
    public static boolean isLegacySaveFile(byte[] theFile) {
        LegacySaveHeader aHeader = parseLegacySaveHeader(theFile);
        return aHeader != null && aHeader.isRecognized();
    }

    /** The actionable rejection text for a recognised legacy dump. */
    private static String legacyRejectionMessage(LegacySaveHeader theHeader) {
        String aStamp = theHeader != null ? theHeader.toString() : "LegacySaveHeader[unknown]";
        return "This is a legacy PvZ mid-level save (.dat container, magic 0xFEEDDEAD, " + aStamp + "), written by the "
                + "\"Jul  2 201011:47:03\" build as a raw C++ memory image. That image cannot be decoded by "
                + "this Java port: SaveGame.cpp dumps whole structs verbatim (SyncBytes(&theBoard->mPaused, "
                + "sizeof(Board) - offset), SaveGame.cpp:2700) and copies sizeof(T) bytes per entity "
                + "(LegacyDataArrayItem<T>, SaveGame.cpp:2661-2690), so the field offsets only exist inside "
                + "that process image. Upstream agrees (README.md, \"Save data compatibility\"): \"*.dat "
                + "files: Legacy dump from old versions. Contains raw memory dumps. Do not share this file "
                + "across platforms as it will cause crashes due to architecture differences.\"\n"
                + "To continue this level: open it with an upstream PvZ-Portable build, which migrates the "
                + "legacy dump to the portable .v4 format and erases the .dat after a successful load "
                + "(LawnApp.cpp:396-410: LawnSaveGame(...), then EraseFile(*.dat)), then load the resulting "
                + "'game<profile>_<mode>.v4' here - this port reads and writes only the PVZP_SAVE4 (.v4) "
                + "container. The .dat is left on disk untouched: the new-game path does not erase a legacy "
                + "save it could not decode (LawnApp.preNewGame PORT-NOTE, ../verification/VERIFICATION.md §7), so the "
                + "file stays available for the migration above.";
    }

    // ---------------------------------------------------------------------------------------
    // Container helpers (SaveGame.cpp:2244-2291, 2793-2829)
    // ---------------------------------------------------------------------------------------

    /** Wraps a payload in a {@code SaveFileHeaderV4} (SaveGame.cpp:2817-2829). */
    public static byte[] encodeSaveFile(byte[] thePayload) {
        ByteArrayOutputStream aOut = new ByteArrayOutputStream();
        aOut.write(SAVE_FILE_MAGIC_V4, 0, SAVE_FILE_MAGIC_V4.length);
        writeU32LE(aOut, SAVE_FILE_V4_VERSION);
        writeU32LE(aOut, thePayload.length);
        writeU32LE(aOut, crc32(thePayload));
        aOut.write(thePayload, 0, thePayload.length);
        return aOut.toByteArray();
    }

    /**
     * Validates a {@code .v4} file header and returns its payload (SaveGame.cpp:2244-2268).
     *
     * <p>Mirrors {@code LawnLoadGame}'s ordering (SaveGame.cpp:2758-2789): the {@code .v4} container is
     * tried first, and the legacy {@code 0xFEEDDEAD} dump is only considered when the file does not
     * carry the {@code .v4} magic. A file that carries the legacy magic but fails upstream's
     * build-version/build-date test is "not a save" ({@code return false}).
     *
     * @return the payload, or {@code null} when the file is not a valid {@code .v4} save (or is a
     *     legacy-looking file that upstream would also refuse)
     * @throws LegacySaveFormatException when the file is a recognised legacy {@code .dat} dump, which
     *     is a raw memory image and not portable to this object graph (see the class javadoc)
     */
    public static byte[] decodeSaveFilePayload(byte[] theFile) {
        // Detect the legacy header before the v4 size check: a legacy header is only 12 bytes, so a
        // recognized legacy file can legitimately be shorter than SaveFileHeaderV4 (24 bytes).
        LegacySaveHeader aLegacy = parseLegacySaveHeader(theFile);
        if (aLegacy != null) {
            if (!aLegacy.isRecognized()) {
                // SaveGame.cpp:2768-2772 -> LawnLoadGame returns false.
                return null;
            }
            throw new LegacySaveFormatException(aLegacy);
        }
        if (theFile.length < SAVE_FILE_HEADER_V4_SIZE) {
            return null;
        }
        boolean aMagicOk = true;
        for (int i = 0; i < SAVE_FILE_MAGIC_V4.length; i++) {
            if (theFile[i] != SAVE_FILE_MAGIC_V4[i]) {
                aMagicOk = false;
                break;
            }
        }
        if (!aMagicOk) {
            return null;
        }
        int aVersion = readU32LE(theFile, 12);
        if (aVersion != SAVE_FILE_V4_VERSION) {
            return null;
        }
        int aPayloadSize = readU32LE(theFile, 16);
        if (aPayloadSize < 0 || aPayloadSize > theFile.length - SAVE_FILE_HEADER_V4_SIZE) {
            return null;
        }
        int aPayloadCrc = readU32LE(theFile, 20);
        byte[] aPayload = new byte[aPayloadSize];
        System.arraycopy(theFile, SAVE_FILE_HEADER_V4_SIZE, aPayload, 0, aPayloadSize);
        if (crc32(aPayload) != aPayloadCrc) {
            return null;
        }
        return aPayload;
    }

    // ---------------------------------------------------------------------------------------
    // FixBoardAfterLoad (SaveGame.cpp:2082-2242)
    // ---------------------------------------------------------------------------------------

    /** {@code FixBoardAfterLoad} (SaveGame.cpp:2082-2242). */
    private static void fixBoardAfterLoad(Board theBoard) {
        for (Plant aPlant : theBoard.mPlants) {
            aPlant.mApp = theBoard.mApp;
            aPlant.mBoard = theBoard;
        }

        for (Zombie aZombie : theBoard.mZombies) {
            aZombie.mApp = theBoard.mApp;
            aZombie.mBoard = theBoard;

            switch (aZombie.mZombieType) {
                case ZombieType.ZOMBIE_GARGANTUAR:
                case ZombieType.ZOMBIE_REDEYE_GARGANTUAR: {
                    Reanimation aBodyReanim = theBoard.mApp.reanimationGet(aZombie.mBodyReanimID);
                    if (aBodyReanim != null) {
                        int aDamageIndex = aZombie.getBodyDamageIndex();
                        if (aDamageIndex >= 1) {
                            aBodyReanim.setImageOverride("Zombie_gargantua_body1", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_2_ID));
                            aBodyReanim.setImageOverride("Zombie_gargantuar_outerarm_lower", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_GARGANTUAR_OUTERARM_LOWER2_ID));
                        }
                        if (aDamageIndex >= 2) {
                            aBodyReanim.setImageOverride("Zombie_gargantua_body1", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_3_ID));
                            aBodyReanim.setImageOverride("Zombie_gargantuar_outerleg_foot", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_GARGANTUAR_FOOT2_ID));
                        }
                        if (aZombie.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                            if (aDamageIndex >= 2) {
                                aBodyReanim.setImageOverride("anim_head1", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_REDEYE_ID));
                            } else {
                                aBodyReanim.setImageOverride("anim_head1", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD_REDEYE_ID));
                            }
                        } else if (aDamageIndex >= 2) {
                            aBodyReanim.setImageOverride("anim_head1", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_ID));
                        }
                    }
                    break;
                }
                case ZombieType.ZOMBIE_ZAMBONI: {
                    Reanimation aBodyReanim = theBoard.mApp.reanimationGet(aZombie.mBodyReanimID);
                    if (aBodyReanim != null) {
                        int aDamageIndex = aZombie.getBodyDamageIndex();
                        if (aDamageIndex >= 1) {
                            aBodyReanim.setImageOverride("Zombie_zamboni_1", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE1_ID));
                            aBodyReanim.setImageOverride("Zombie_zamboni_2", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE1_ID));
                        }
                        if (aDamageIndex >= 2) {
                            aBodyReanim.setImageOverride("Zombie_zamboni_1", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE2_ID));
                            aBodyReanim.setImageOverride("Zombie_zamboni_2", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE2_ID));
                        }
                    }
                    break;
                }
                case ZombieType.ZOMBIE_CATAPULT: {
                    Reanimation aBodyReanim = theBoard.mApp.reanimationGet(aZombie.mBodyReanimID);
                    if (aBodyReanim != null) {
                        int aDamageIndex = aZombie.getBodyDamageIndex();
                        if (aDamageIndex >= 1) {
                            aBodyReanim.setImageOverride("Zombie_catapult_siding", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_CATAPULT_SIDING_DAMAGE_ID));
                        }
                    }
                    break;
                }
                case ZombieType.ZOMBIE_BOSS: {
                    Reanimation aBodyReanim = theBoard.mApp.reanimationGet(aZombie.mBodyReanimID);
                    if (aBodyReanim != null) {
                        int aDamageIndex = aZombie.getBodyDamageIndex();
                        if (aDamageIndex >= 1) {
                            aBodyReanim.setImageOverride("Boss_head", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE1_ID));
                            aBodyReanim.setImageOverride("Boss_jaw", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE1_ID));
                            aBodyReanim.setImageOverride("Boss_outerarm_hand", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE1_ID));
                            aBodyReanim.setImageOverride("Boss_outerarm_thumb2", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE1_ID));
                            aBodyReanim.setImageOverride("Boss_innerleg_foot", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE1_ID));
                        }
                        if (aDamageIndex >= 2) {
                            aBodyReanim.setImageOverride("Boss_head", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE2_ID));
                            aBodyReanim.setImageOverride("Boss_jaw", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE2_ID));
                            aBodyReanim.setImageOverride("Boss_outerarm_hand", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE2_ID));
                            aBodyReanim.setImageOverride("Boss_outerarm_thumb2", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE2_ID));
                            aBodyReanim.setImageOverride("Boss_outerleg_foot", Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE2_ID));
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }

        for (Projectile aProjectile : theBoard.mProjectiles) {
            aProjectile.mApp = theBoard.mApp;
            aProjectile.mBoard = theBoard;
        }
        for (Coin aCoin : theBoard.mCoins) {
            aCoin.mApp = theBoard.mApp;
            aCoin.mBoard = theBoard;
        }
        for (LawnMower aLawnMower : theBoard.mLawnMowers) {
            aLawnMower.mApp = theBoard.mApp;
            aLawnMower.mBoard = theBoard;
        }
        for (GridItem aGridItem : theBoard.mGridItems) {
            aGridItem.mApp = theBoard.mApp;
            aGridItem.mBoard = theBoard;
        }

        theBoard.mAdvice.mApp = theBoard.mApp;
        theBoard.mCursorObject.mApp = theBoard.mApp;
        theBoard.mCursorObject.mBoard = theBoard;
        theBoard.mCursorPreview.mApp = theBoard.mApp;
        theBoard.mCursorPreview.mBoard = theBoard;
        theBoard.mSeedBank.mApp = theBoard.mApp;
        theBoard.mSeedBank.mBoard = theBoard;
        for (int i = 0; i < cn.enaium.fabricpvz.portable.lawn.GameConstants.SEEDBANK_MAX; i++) {
            theBoard.mSeedBank.mSeedPackets[i].mApp = theBoard.mApp;
            theBoard.mSeedBank.mSeedPackets[i].mBoard = theBoard;
        }
        theBoard.mChallenge.mApp = theBoard.mApp;
        theBoard.mChallenge.mBoard = theBoard;
        theBoard.mApp.mMusic.mApp = theBoard.mApp;
        if (cn.enaium.fabricpvz.portable.sexy.SexyAppBase.current != null) {
            theBoard.mApp.mMusic.mMusicInterface = cn.enaium.fabricpvz.portable.sexy.SexyAppBase.current.mMusicInterface;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Entry points (SaveGame.cpp:2244-2291, 2758-2829)
    // ---------------------------------------------------------------------------------------

    /**
     * {@code LawnLoadGame} (SaveGame.cpp:2758-2790): tries the {@code .v4} container, then the legacy
     * dump.
     *
     * <p>A recognised legacy {@code .dat} is rejected: upstream would decode it by replaying the raw
     * C++ struct image, which has no meaning in this object graph (see the class javadoc). The
     * failure is reported with the actionable message of {@link LegacySaveFormatException} and the
     * load returns false, which is the control flow upstream takes when the dump cannot be decoded
     * ({@code LawnApp::TryLoadGame}, LawnApp.cpp:382-410, falls through to {@code KillBoard}).
     */
    public static boolean lawnLoadGame(Board theBoard, String theFileName) {
        byte[] aFile;
        try {
            aFile = Files.readAllBytes(Path.of(theFileName));
        } catch (IOException e) {
            return false;
        }

        byte[] aPayload;
        try {
            aPayload = decodeSaveFilePayload(aFile);
        } catch (LegacySaveFormatException e) {
            PvzpDebug.pvzpErrorMessageBox(e.getMessage(), "Save Game");
            return false;
        }
        if (aPayload == null) {
            return false;
        }

        try {
            ChunkFrame[] aFrames = parseChunkFrames(aPayload);
            boolean aBaseLoaded = false;
            for (ChunkFrame aFrame : aFrames) {
                readChunkV4(aFrame.mChunkType, encodeChunkData(aFrame), theBoard);
                if (aFrame.mChunkType == SAVE4_CHUNK_BOARD_BASE) {
                    aBaseLoaded = true;
                }
            }
            if (!aBaseLoaded) {
                return false;
            }
            fixBoardAfterLoad(theBoard);
            theBoard.mApp.mGameScene = GameScenes.SCENE_PLAYING;
            return true;
        } catch (DataReaderException | SaveFormatException | IllegalArgumentException e) {
            // Every structural failure of the container (bad chunk framing/version/length, a chunk
            // field that does not apply, a short read) is "not a loadable save": upstream
            // ReadChunkV4/ReadTLVBlob return false for the same inputs (SaveGame.cpp:2041-2080).
            return false;
        }
    }

    /** Rebuilds the raw chunk data ({@code u32 version, u32 fieldId, u32 len, body}) from a frame. */
    private static byte[] encodeChunkData(ChunkFrame theFrame) {
        ByteArrayOutputStream aChunkData = new ByteArrayOutputStream();
        writeU32LE(aChunkData, theFrame.mChunkVersion);
        writeU32LE(aChunkData, theFrame.mFieldId);
        writeU32LE(aChunkData, theFrame.mBody.length);
        aChunkData.write(theFrame.mBody, 0, theFrame.mBody.length);
        return aChunkData.toByteArray();
    }

    /** {@code LawnSaveGame} (SaveGame.cpp:2793-2829): always writes the {@code .v4} format. */
    public static boolean lawnSaveGame(Board theBoard, String theFileName) {
        ByteArrayOutputStream aPayload = new ByteArrayOutputStream();
        for (int aChunkType : SAVE4_CHUNK_ORDER) {
            byte[] aChunk = writeChunkV4(aChunkType, theBoard);
            if (aChunk != null) {
                aPayload.write(aChunk, 0, aChunk.length);
            }
        }
        byte[] aFile = encodeSaveFile(aPayload.toByteArray());
        try {
            Path aPath = Path.of(theFileName);
            if (aPath.getParent() != null) {
                Files.createDirectories(aPath.getParent());
            }
            Files.write(aPath, aFile);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
