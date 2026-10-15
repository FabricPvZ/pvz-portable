package cn.enaium.fabricpvz.portable.lawn.system;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.AdviceType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ChallengeState;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CoinMotion;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.CursorType;
import cn.enaium.fabricpvz.portable.lawn.EmitterType;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.GridItem;
import cn.enaium.fabricpvz.portable.lawn.GridItemState;
import cn.enaium.fabricpvz.portable.lawn.GridItemType;
import cn.enaium.fabricpvz.portable.lawn.GridSquareType;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnMower;
import cn.enaium.fabricpvz.portable.lawn.LawnMowerState;
import cn.enaium.fabricpvz.portable.lawn.LawnMowerType;
import cn.enaium.fabricpvz.portable.lawn.MagnetItem;
import cn.enaium.fabricpvz.portable.lawn.MagnetItemType;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantRowType;
import cn.enaium.fabricpvz.portable.lawn.PlantState;
import cn.enaium.fabricpvz.portable.lawn.ReanimationID;
import cn.enaium.fabricpvz.portable.lawn.RenderLayer;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombiePhase;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.pvzp.PvzpListNode;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpEmitterDefinition;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleDefinition;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleDefinitions;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleEmitter;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleHolder;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Container-level checks of the {@code .v4} mid-level save ({@link SaveGame}) against
 * docs/research/03-save.md §6.1-§6.2: header/CRC, chunk framing order, per-chunk version
 * validation and legacy-format rejection.
 *
 * <p>Everything here works on fabricated in-memory byte arrays through {@link DataWriter}; no real
 * {@code Board} content is required.
 */
class SaveGameTest {

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /** Builds a chunk frame with an explicit version, mirroring {@code WriteChunkV4}. */
    private static byte[] rawChunk(int theChunkType, int theChunkVersion, byte[] theFieldBody) {
        ByteArrayOutputStream aChunkData = new ByteArrayOutputStream();
        writeU32LE(aChunkData, theChunkVersion);
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

    private static void writeU32LE(ByteArrayOutputStream theOut, int theValue) {
        theOut.write(theValue & 0xFF);
        theOut.write((theValue >>> 8) & 0xFF);
        theOut.write((theValue >>> 16) & 0xFF);
        theOut.write((theValue >>> 24) & 0xFF);
    }

    private static byte[] concat(byte[]... theParts) {
        ByteArrayOutputStream aOut = new ByteArrayOutputStream();
        for (byte[] aPart : theParts) {
            aOut.write(aPart, 0, aPart.length);
        }
        return aOut.toByteArray();
    }

    private static int readU32LE(byte[] theData, int theOffset) {
        return (theData[theOffset] & 0xFF)
                | ((theData[theOffset + 1] & 0xFF) << 8)
                | ((theData[theOffset + 2] & 0xFF) << 16)
                | ((theData[theOffset + 3] & 0xFF) << 24);
    }

    // ------------------------------------------------------------------------------------------
    // Header / CRC (docs §6.1)
    // ------------------------------------------------------------------------------------------

    @Test
    void headerLayoutAndCrcMatchTheSpec() {
        byte[] aPayload = new byte[] {1, 2, 3, 4, 5};
        byte[] aFile = SaveGame.encodeSaveFile(aPayload);

        assertEquals(24 + aPayload.length, aFile.length);
        assertArrayEquals(new byte[] {'P', 'V', 'Z', 'P', '_', 'S', 'A', 'V', 'E', '4', 0, 0},
                java.util.Arrays.copyOfRange(aFile, 0, 12));
        assertEquals(1, readU32LE(aFile, 12));
        assertEquals(aPayload.length, readU32LE(aFile, 16));

        CRC32 aCrc = new CRC32();
        aCrc.update(aPayload, 0, aPayload.length);
        assertEquals((int) aCrc.getValue(), readU32LE(aFile, 20));

        assertArrayEquals(aPayload, SaveGame.decodeSaveFilePayload(aFile));
    }

    @Test
    void decodeRejectsWrongVersion() {
        byte[] aFile = SaveGame.encodeSaveFile(new byte[] {9, 9, 9});
        aFile[12] = 2;
        assertNull(SaveGame.decodeSaveFilePayload(aFile));
    }

    @Test
    void decodeRejectsCrcMismatch() {
        byte[] aFile = SaveGame.encodeSaveFile(new byte[] {9, 9, 9});
        aFile[24] ^= 0x7F;
        assertNull(SaveGame.decodeSaveFilePayload(aFile));
    }

    @Test
    void decodeRejectsTruncatedAndOversizedPayloads() {
        assertNull(SaveGame.decodeSaveFilePayload(new byte[10]));
        byte[] aFile = SaveGame.encodeSaveFile(new byte[] {1, 2, 3, 4});
        assertNull(SaveGame.decodeSaveFilePayload(java.util.Arrays.copyOf(aFile, 20)));
    }

    /** Builds the 12-byte legacy {@code SaveFileHeader} with an explicit version/date. */
    private static byte[] legacyHeader(int theBuildVersion, int theBuildDate) {
        ByteArrayOutputStream aOut = new ByteArrayOutputStream();
        writeU32LE(aOut, SaveGame.SAVE_FILE_MAGIC_NUMBER);
        writeU32LE(aOut, theBuildVersion);
        writeU32LE(aOut, theBuildDate);
        return aOut.toByteArray();
    }

    /** The build stamp upstream ties legacy saves to: {@code crc32("Jul  2 201011:47:03")}. */
    private static int legacyBuildDate() {
        CRC32 aCrc = new CRC32();
        byte[] aStamp = SaveGame.FILE_COMPILE_TIME_STRING.getBytes(StandardCharsets.ISO_8859_1);
        aCrc.update(aStamp, 0, aStamp.length);
        return (int) aCrc.getValue();
    }

    @Test
    void legacyHeaderParsesMagicVersionAndBuildStamp() {
        byte[] aLegacy = legacyHeader(SaveGame.SAVE_FILE_VERSION, legacyBuildDate());
        SaveGame.LegacySaveHeader aHeader = SaveGame.parseLegacySaveHeader(aLegacy);

        assertNotNull(aHeader);
        assertEquals(SaveGame.SAVE_FILE_MAGIC_NUMBER, aHeader.mMagicNumber);
        assertEquals(SaveGame.SAVE_FILE_VERSION, aHeader.mBuildVersion);
        assertEquals(SaveGame.SAVE_FILE_DATE, aHeader.mBuildDate);
        assertTrue(aHeader.isRecognized(), aHeader.toString());
        assertTrue(SaveGame.isLegacySaveFile(aLegacy));
    }

    @Test
    void decodeRejectsLegacyFeeddeadMagicWithClearMessage() {
        byte[] aLegacy = legacyHeader(SaveGame.SAVE_FILE_VERSION, legacyBuildDate());

        SaveGame.LegacySaveFormatException aError = assertThrows(SaveGame.LegacySaveFormatException.class,
                () -> SaveGame.decodeSaveFilePayload(aLegacy));
        assertEquals(SaveGame.SAVE_FILE_MAGIC_NUMBER, aError.getLegacyHeader().mMagicNumber);

        String aMessage = aError.getMessage();
        assertTrue(aMessage.contains("FEEDDEAD"), aMessage);
        assertTrue(aMessage.contains(".v4"), aMessage);
        assertTrue(aMessage.contains("PVZP_SAVE4"), aMessage);
        // Names the upstream migration rule that produces a loadable file.
        assertTrue(aMessage.contains("LawnApp.cpp:396-410"), aMessage);
        assertTrue(aMessage.contains("raw"), aMessage);
        // The port leaves the undecodable .dat in place (LawnApp.preNewGame PORT-NOTE): the message must
        // tell the user the file survives, not that the game erases it.
        assertTrue(aMessage.contains("left on disk"), aMessage);
    }

    @Test
    void legacyDumpFromAnotherBuildIsNotASave() {
        // Upstream accepts a legacy file only when magic, build version AND build date all match
        // (SaveGame.cpp:2768-2772); otherwise LawnLoadGame returns false.
        byte[] aWrongVersion = legacyHeader(SaveGame.SAVE_FILE_VERSION + 1, legacyBuildDate());
        assertFalse(SaveGame.isLegacySaveFile(aWrongVersion));
        assertNull(SaveGame.decodeSaveFilePayload(aWrongVersion));

        byte[] aWrongDate = legacyHeader(SaveGame.SAVE_FILE_VERSION, legacyBuildDate() ^ 0x5A5A5A5A);
        assertFalse(SaveGame.isLegacySaveFile(aWrongDate));
        assertNull(SaveGame.decodeSaveFilePayload(aWrongDate));

        // A file that is too short to hold the legacy header is not a legacy save either.
        assertNull(SaveGame.parseLegacySaveHeader(new byte[] {(byte) 0xAD, (byte) 0xDE, (byte) 0xED, (byte) 0xFE}));
        assertFalse(SaveGame.isLegacySaveFile(new byte[] {(byte) 0xAD, (byte) 0xDE, (byte) 0xED, (byte) 0xFE}));
    }

    @Test
    void lawnLoadGameRejectsALegacyDumpInsteadOfThrowing() throws IOException {
        // A recognized legacy dump must not escape as an exception out of the entry point: upstream
        // Board::LoadGame simply fails and LawnApp::TryLoadGame falls through to KillBoard
        // (LawnApp.cpp:382-410). The board is not touched on this path, so it stays null here.
        Path aFile = Files.createTempFile("pvz-legacy", ".dat");
        try {
            Files.write(aFile, legacyHeader(SaveGame.SAVE_FILE_VERSION, legacyBuildDate()));
            assertFalse(SaveGame.lawnLoadGame(null, aFile.toAbsolutePath().toString()));
        } finally {
            Files.deleteIfExists(aFile);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Chunk framing / order (docs §6.2)
    // ------------------------------------------------------------------------------------------

    @Test
    void chunkOrderConstantIsTheTwentyContiguousIds() {
        assertEquals(20, SaveGame.SAVE4_CHUNK_ORDER.length);
        for (int i = 0; i < SaveGame.SAVE4_CHUNK_ORDER.length; i++) {
            assertEquals(i + 1, SaveGame.SAVE4_CHUNK_ORDER[i]);
        }
    }

    @Test
    void chunkFramesRoundTripInOrder() {
        ByteArrayOutputStream aPayload = new ByteArrayOutputStream();
        for (int i = 0; i < SaveGame.SAVE4_CHUNK_ORDER.length; i++) {
            int aType = SaveGame.SAVE4_CHUNK_ORDER[i];
            byte[] aBody = new byte[] {(byte) aType, (byte) (aType + 1)};
            byte[] aChunk = SaveGame.encodeChunk(aType, aBody);
            aPayload.write(aChunk, 0, aChunk.length);
        }

        SaveGame.ChunkFrame[] aFrames = SaveGame.parseChunkFrames(aPayload.toByteArray());
        assertEquals(20, aFrames.length);
        for (int i = 0; i < 20; i++) {
            assertEquals(SaveGame.SAVE4_CHUNK_ORDER[i], aFrames[i].mChunkType);
            assertEquals(1, aFrames[i].mChunkVersion);
            assertEquals(1, aFrames[i].mFieldId);
            assertArrayEquals(new byte[] {(byte) (i + 1), (byte) (i + 2)}, aFrames[i].mBody);
        }
    }

    @Test
    void encodeChunkLayoutIsTypeSizeVersionFieldLenBody() {
        byte[] aBody = new byte[] {0x11, 0x22, 0x33};
        byte[] aChunk = SaveGame.encodeChunk(SaveGame.SAVE4_CHUNK_ZOMBIES, aBody);

        assertEquals(SaveGame.SAVE4_CHUNK_ZOMBIES, readU32LE(aChunk, 0));
        assertEquals(12 + aBody.length, readU32LE(aChunk, 4));
        assertEquals(SaveGame.SAVE4_CHUNK_VERSION, readU32LE(aChunk, 8));
        assertEquals(1, readU32LE(aChunk, 12));
        assertEquals(aBody.length, readU32LE(aChunk, 16));
        assertArrayEquals(aBody, java.util.Arrays.copyOfRange(aChunk, 20, 20 + aBody.length));
    }

    @Test
    void chunkVersionValidationFailsOnUnknownVersion() {
        byte[] aPayload = rawChunk(SaveGame.SAVE4_CHUNK_BOARD_BASE, 2, new byte[] {0});
        IllegalArgumentException aError = assertThrows(IllegalArgumentException.class,
                () -> SaveGame.parseChunkFrames(aPayload));
        assertTrue(aError.getMessage().contains("version"), aError.getMessage());
    }

    @Test
    void chunkParserRejectsTruncatedFrames() {
        byte[] aPayload = concat(rawChunk(SaveGame.SAVE4_CHUNK_BOARD_BASE, 1, new byte[] {1, 2, 3, 4}));
        byte[] aTruncated = java.util.Arrays.copyOf(aPayload, aPayload.length - 2);
        assertThrows(IllegalArgumentException.class, () -> SaveGame.parseChunkFrames(aTruncated));
    }

    @Test
    void crc32MatchesJavaZlib() {
        byte[] aData = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
        CRC32 aCrc = new CRC32();
        aCrc.update(aData, 0, aData.length);
        assertEquals((int) aCrc.getValue(), SaveGame.crc32(aData));
    }

    // ------------------------------------------------------------------------------------------
    // A fabricated full payload with a header
    // ------------------------------------------------------------------------------------------

    @Test
    void fabricatedContainerRoundTripsThroughHeaderAndChunkParser() {
        byte[] aFakeBoardBaseBody = new byte[] {0x10, 0x00, 0x00, 0x00};  // e.g. an empty TLV blob
        byte[] aPayload = rawChunk(SaveGame.SAVE4_CHUNK_BOARD_BASE, 1, aFakeBoardBaseBody);
        byte[] aFile = SaveGame.encodeSaveFile(aPayload);

        assertNotNull(SaveGame.decodeSaveFilePayload(aFile));
        SaveGame.ChunkFrame[] aFrames = SaveGame.parseChunkFrames(SaveGame.decodeSaveFilePayload(aFile));
        assertEquals(1, aFrames.length);
        assertEquals(SaveGame.SAVE4_CHUNK_BOARD_BASE, aFrames[0].mChunkType);
        assertArrayEquals(aFakeBoardBaseBody, aFrames[0].mBody);
    }

    // ------------------------------------------------------------------------------------------
    // Round trip of a running level (docs/research/03-save.md §6, §8, §10)
    // ------------------------------------------------------------------------------------------

    /** The single live item of a one-entity array ({@link DataArray} iteration is live-slots-only). */
    private static <T> T onlyItem(DataArray<T> theArray) {
        T aFound = null;
        for (T anItem : theArray) {
            assertNull(aFound, "expected exactly one live item");
            aFound = anItem;
        }
        assertNotNull(aFound, "expected one live item");
        return aFound;
    }

    /** A headless adventure level 1 board, built the way {@code LawnApp::NewGame} does. */
    private static Board newLevel1() {
        LawnApp aApp = new LawnApp();
        aApp.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(aApp);
        aApp.mPlayerInfo.mLevel = 1;
        aApp.mPlayerInfo.mFinishedAdventure = 0;
        Common.sRand(0x5EED);
        Board aBoard = new Board(aApp);
        aBoard.initLevel();
        return aBoard;
    }

    /**
     * Fills a board with the state a "Save and Exit" mid-level save has to carry: board base
     * fields (SaveGame.cpp:1603-1715), one live entity of every chunk-backed array
     * (SaveGame.cpp:1752-1790) and the cursor/advice/seedbank/challenge/music singletons
     * (SaveGame.cpp:1863-1938).
     */
    private static LawnMower populateRunningLevel(Board theBoard) {
        theBoard.mSunMoney = 725;
        theBoard.mCurrentWave = 7;
        theBoard.mNumWaves = 20;
        theBoard.mMainCounter = 4242;
        theBoard.mBoardRandSeed = 0x1234ABCD;
        theBoard.mLevelComplete = true;
        theBoard.mBoardFadeOutCounter = 33;
        theBoard.mGameID = 0x0123456789ABCDEFL;
        theBoard.mTimeStopCounter = 17;
        theBoard.mMustacheMode = true;
        theBoard.mGridSquareType[3][4] = GridSquareType.GRIDSQUARE_POOL;
        theBoard.mGridCelLook[2][1] = 9;
        theBoard.mPlantRow[2] = PlantRowType.PLANTROW_POOL;
        theBoard.mZombiesInWave[2][5] = ZombieType.ZOMBIE_GARGANTUAR;
        theBoard.mFwooshID[1][3] = 0x99;
        theBoard.mHelpDisplayed[AdviceType.ADVICE_SEED_REFRESH] = true;

        Plant aPlant = theBoard.mPlants.dataArrayAlloc();
        aPlant.mBoard = theBoard;
        aPlant.mApp = theBoard.mApp;
        // PlantInitialize (Plant.cpp) allocates these; the asset-gated reanimation setup is not needed
        // to round trip them, and the sync functions read every one of them.
        aPlant.mPlantRect = new Rect(10, 20, 30, 40);
        aPlant.mPlantAttackRect = new Rect(50, 60, 70, 80);
        aPlant.mMagnetItems = new MagnetItem[Plant.MAX_MAGNET_ITEMS];
        for (int i = 0; i < Plant.MAX_MAGNET_ITEMS; i++) {
            aPlant.mMagnetItems[i] = new MagnetItem();
        }
        aPlant.mMagnetItems[0].mPosX = 12.5f;
        aPlant.mMagnetItems[0].mDestOffsetY = 4.25f;
        aPlant.mMagnetItems[0].mItemType = MagnetItemType.MAGNET_ITEM_FOOTBALL_HELMET_2;
        aPlant.mSeedType = SeedType.SEED_PEASHOOTER;
        aPlant.mPlantCol = 2;
        aPlant.mRow = 3;
        aPlant.mPlantHealth = 300;
        aPlant.mPlantMaxHealth = 300;
        aPlant.mState = PlantState.STATE_NOTREADY;
        aPlant.mBodyReanimID = ReanimationID.REANIMATIONID_NULL;
        aPlant.mHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        aPlant.mHighlighted = true;

        Zombie aZombie = theBoard.mZombies.dataArrayAlloc();
        aZombie.mBoard = theBoard;
        aZombie.mApp = theBoard.mApp;
        aZombie.mZombieType = ZombieType.ZOMBIE_NORMAL;
        aZombie.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        aZombie.mPosX = 612.5f;
        aZombie.mPosY = 341.25f;
        aZombie.mRow = 3;
        aZombie.mBodyHealth = 181;
        aZombie.mBodyMaxHealth = 270;
        aZombie.mFromWave = 6;
        aZombie.mHasHead = true;
        aZombie.mHasArm = true;
        aZombie.mBodyReanimID = ReanimationID.REANIMATIONID_NULL;

        Coin aCoin = theBoard.mCoins.dataArrayAlloc();
        aCoin.mBoard = theBoard;
        aCoin.mApp = theBoard.mApp;
        aCoin.mPosX = 120.5f;
        aCoin.mPosY = 260.25f;
        aCoin.mScale = 0.75f;
        aCoin.mType = CoinType.COIN_SUN;
        aCoin.mCoinMotion = CoinMotion.COIN_MOTION_FROM_SKY;
        aCoin.mUsableSeedType = SeedType.SEED_SUNSHROOM;
        aCoin.mPottedPlantSpec.mSeedType = SeedType.SEED_MARIGOLD;
        aCoin.mTimesDropped = 2;

        GridItem aItem = theBoard.mGridItems.dataArrayAlloc();
        aItem.mBoard = theBoard;
        aItem.mApp = theBoard.mApp;
        aItem.mGridItemType = GridItemType.GRIDITEM_GRAVESTONE;
        aItem.mGridItemState = GridItemState.GRIDITEM_STATE_NORMAL;
        aItem.mGridX = 5;
        aItem.mGridY = 1;
        aItem.mGridItemCounter = 88;
        aItem.mPosX = 440.5f;
        aItem.mMotionTrailCount = 2;
        aItem.mMotionTrailFrames[0].mPosX = 11.5f;

        // A mower with an explicit state; mLawnMowers also carries whatever initLevel placed
        // (Board.cpp:1500-1520), which the round trip has to preserve too.
        LawnMower aMower = theBoard.mLawnMowers.dataArrayAlloc();
        aMower.mBoard = theBoard;
        aMower.mApp = theBoard.mApp;
        aMower.mMowerState = LawnMowerState.MOWER_TRIGGERED;
        aMower.mMowerType = LawnMowerType.LAWNMOWER_POOL;
        aMower.mAltitude = 4.5f;
        aMower.mChompCounter = 3;
        aMower.mRow = 3;
        aMower.mPosX = 20.5f;

        theBoard.mCursorObject.mSeedBankIndex = 6;
        theBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_BANK;
        theBoard.mCursorObject.mImitaterType = SeedType.SEED_PEASHOOTER;
        theBoard.mAdvice.mDisplayTime = 250;
        theBoard.mAdvice.mDuration = 500;
        theBoard.mAdvice.mLabel = "hello";
        theBoard.mSeedBank.mNumPackets = 3;
        theBoard.mSeedBank.mConveyorBeltCounter = 12;
        theBoard.mSeedBank.mSeedPackets[3].mPacketType = SeedType.SEED_SUNFLOWER;
        theBoard.mSeedBank.mSeedPackets[3].mIndex = 3;
        theBoard.mSeedBank.mSeedPackets[3].mRefreshCounter = 41;
        theBoard.mChallenge.mChallengeScore = 9876;
        theBoard.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        theBoard.mChallenge.mSurvivalStage = 4;
        theBoard.mChallenge.mBeghouledEated[1][2] = 1;
        theBoard.mChallenge.mReanimClouds[2] = 0x77;
        theBoard.mApp.mMusic.mCurMusicTune = MusicTune.MUSIC_TUNE_DAY_GRASSWALK;
        theBoard.mApp.mMusic.mBaseBPM = 128.5f;
        theBoard.mApp.mMusic.mPaused = true;
        theBoard.mApp.mMusic.mMusicDisabled = false;
        return aMower;
    }

    @Test
    void v4RoundTripRestoresARunningLevel() throws IOException {
        Board aSaved = newLevel1();
        LawnMower aSavedMower = populateRunningLevel(aSaved);
        Path aFile = Files.createTempFile("pvz-roundtrip", ".v4");
        try {
            assertTrue(SaveGame.lawnSaveGame(aSaved, aFile.toString()));

            byte[] aBytes = Files.readAllBytes(aFile);
            assertArrayEquals(SaveGame.SAVE_FILE_MAGIC_V4, java.util.Arrays.copyOf(aBytes, 12));
            assertEquals(20, SaveGame.parseChunkFrames(SaveGame.decodeSaveFilePayload(aBytes)).length);

            // A fresh level: nothing the save carries is already in place, so every assertion below
            // proves the load restored it (and not that the value was there to begin with).
            Board aLoaded = newLevel1();
            aLoaded.mSunMoney = 5;
            aLoaded.mApp.mMusic.mMusicDisabled = true;
            assertTrue(aLoaded.loadGame(aFile.toString()),
                    "Board::LoadGame (Board.cpp:361) must load the .v4 container");

            // Board base (SaveGame.cpp:1603-1715).
            assertEquals(725, aLoaded.mSunMoney);
            assertEquals(7, aLoaded.mCurrentWave);
            assertEquals(20, aLoaded.mNumWaves);
            assertEquals(4242, aLoaded.mMainCounter);
            assertEquals(0x1234ABCD, aLoaded.mBoardRandSeed);
            assertTrue(aLoaded.mLevelComplete);
            assertEquals(33, aLoaded.mBoardFadeOutCounter);
            assertEquals(0x0123456789ABCDEFL, aLoaded.mGameID);
            assertEquals(17, aLoaded.mTimeStopCounter);
            assertTrue(aLoaded.mMustacheMode);
            assertEquals(GridSquareType.GRIDSQUARE_POOL, aLoaded.mGridSquareType[3][4]);
            assertEquals(9, aLoaded.mGridCelLook[2][1]);
            assertEquals(PlantRowType.PLANTROW_POOL, aLoaded.mPlantRow[2]);
            assertEquals(ZombieType.ZOMBIE_GARGANTUAR, aLoaded.mZombiesInWave[2][5]);
            assertEquals(0x99, aLoaded.mFwooshID[1][3]);
            assertTrue(aLoaded.mHelpDisplayed[AdviceType.ADVICE_SEED_REFRESH]);

            // Entities + their GameObject bases (SaveGame.cpp:535-1009).
            assertEquals(1, aLoaded.mPlants.mSize);
            Plant aPlant = onlyItem(aLoaded.mPlants);
            assertEquals(SeedType.SEED_PEASHOOTER, aPlant.mSeedType);
            assertEquals(2, aPlant.mPlantCol);
            assertEquals(3, aPlant.mRow);
            assertEquals(300, aPlant.mPlantHealth);
            assertTrue(aPlant.mHighlighted);
            assertEquals(10, aPlant.mPlantRect.mX);
            assertEquals(40, aPlant.mPlantRect.mHeight);
            assertEquals(70, aPlant.mPlantAttackRect.mWidth);
            assertEquals(12.5f, aPlant.mMagnetItems[0].mPosX);
            assertEquals(4.25f, aPlant.mMagnetItems[0].mDestOffsetY);
            assertEquals(MagnetItemType.MAGNET_ITEM_FOOTBALL_HELMET_2, aPlant.mMagnetItems[0].mItemType);
            assertSame(aLoaded.mApp, aPlant.mApp);
            assertSame(aLoaded, aPlant.mBoard);

            assertEquals(1, aLoaded.mZombies.mSize);
            Zombie aZombie = onlyItem(aLoaded.mZombies);
            assertEquals(ZombieType.ZOMBIE_NORMAL, aZombie.mZombieType);
            assertEquals(612.5f, aZombie.mPosX);
            assertEquals(341.25f, aZombie.mPosY);
            assertEquals(181, aZombie.mBodyHealth);
            assertEquals(6, aZombie.mFromWave);
            assertTrue(aZombie.mHasHead);
            assertSame(aLoaded.mApp, aZombie.mApp);
            assertSame(aLoaded, aZombie.mBoard);

            assertEquals(1, aLoaded.mCoins.mSize);
            Coin aCoin = onlyItem(aLoaded.mCoins);
            assertEquals(CoinType.COIN_SUN, aCoin.mType);
            assertEquals(0.75f, aCoin.mScale);
            assertEquals(SeedType.SEED_MARIGOLD, aCoin.mPottedPlantSpec.mSeedType);
            assertEquals(2, aCoin.mTimesDropped);
            assertSame(aLoaded.mApp, aCoin.mApp);
            assertSame(aLoaded, aCoin.mBoard);

            assertEquals(1, aLoaded.mGridItems.mSize);
            GridItem aItem = onlyItem(aLoaded.mGridItems);
            assertEquals(GridItemType.GRIDITEM_GRAVESTONE, aItem.mGridItemType);
            assertEquals(5, aItem.mGridX);
            assertEquals(88, aItem.mGridItemCounter);
            assertEquals(2, aItem.mMotionTrailCount);
            assertEquals(11.5f, aItem.mMotionTrailFrames[0].mPosX);
            assertSame(aLoaded.mApp, aItem.mApp);
            assertSame(aLoaded, aItem.mBoard);

            assertEquals(aSaved.mLawnMowers.mSize, aLoaded.mLawnMowers.mSize);
            // DataArray slot ids survive the round trip (SyncDataArrayObjectsTLV writes each id), so
            // the saved mower resolves by the id it had before the save.
            int aMowerId = aSaved.mLawnMowers.dataArrayGetID(aSavedMower);
            LawnMower aMower = aLoaded.mLawnMowers.dataArrayGet(aMowerId);
            assertEquals(LawnMowerState.MOWER_TRIGGERED, aMower.mMowerState);
            assertEquals(LawnMowerType.LAWNMOWER_POOL, aMower.mMowerType);
            assertEquals(4.5f, aMower.mAltitude);
            assertEquals(3, aMower.mChompCounter);
            assertEquals(3, aMower.mRow);
            assertSame(aLoaded.mApp, aMower.mApp);
            assertSame(aLoaded, aMower.mBoard);

            // Singletons (SaveGame.cpp:1863-1938).
            assertEquals(6, aLoaded.mCursorObject.mSeedBankIndex);
            assertEquals(CursorType.CURSOR_TYPE_PLANT_FROM_BANK, aLoaded.mCursorObject.mCursorType);
            assertEquals(SeedType.SEED_PEASHOOTER, aLoaded.mCursorObject.mImitaterType);
            assertEquals(250, aLoaded.mAdvice.mDisplayTime);
            assertEquals("hello", aLoaded.mAdvice.mLabel);
            assertEquals(3, aLoaded.mSeedBank.mNumPackets);
            assertEquals(12, aLoaded.mSeedBank.mConveyorBeltCounter);
            assertEquals(SeedType.SEED_SUNFLOWER, aLoaded.mSeedBank.mSeedPackets[3].mPacketType);
            assertEquals(3, aLoaded.mSeedBank.mSeedPackets[3].mIndex);
            assertEquals(41, aLoaded.mSeedBank.mSeedPackets[3].mRefreshCounter);
            assertEquals(9876, aLoaded.mChallenge.mChallengeScore);
            assertEquals(4, aLoaded.mChallenge.mSurvivalStage);
            assertEquals(1, aLoaded.mChallenge.mBeghouledEated[1][2]);
            assertEquals(0x77, aLoaded.mChallenge.mReanimClouds[2]);
            assertEquals(MusicTune.MUSIC_TUNE_DAY_GRASSWALK, aLoaded.mApp.mMusic.mCurMusicTune);
            assertEquals(128.5f, aLoaded.mApp.mMusic.mBaseBPM);
            assertTrue(aLoaded.mApp.mMusic.mPaused);
            // The saved mMusicDisabled bool is read and discarded (SaveGame.cpp:749-760): it is a
            // runtime audio-capability flag, so the loading process keeps its own value.
            assertTrue(aLoaded.mApp.mMusic.mMusicDisabled);

            // LawnLoadGame ends in SCENE_PLAYING (SaveGame.cpp:2788).
            assertEquals(GameScenes.SCENE_PLAYING, aLoaded.mApp.mGameScene);
        } finally {
            Files.deleteIfExists(aFile);
        }
    }

    /**
     * Writing the level that was just read back out must reproduce the file byte for byte, which is
     * only possible when every chunk reads and writes exactly the same fields in the same order
     * (docs/research/03-save.md §6). This walks all 20 chunks of a populated level through
     * read-then-write, so an asymmetric or missing field anywhere shows up here.
     */
    @Test
    void v4RewriteOfALoadedLevelIsByteIdentical() throws IOException {
        Board aSaved = newLevel1();
        populateRunningLevel(aSaved);
        Path aFile = Files.createTempFile("pvz-rewrite", ".v4");
        Path aRewritten = Files.createTempFile("pvz-rewrite", ".v4");
        try {
            assertTrue(SaveGame.lawnSaveGame(aSaved, aFile.toString()));

            // lawnLoadGame (not Board::LoadGame): Board::LoadGame resets the FPS stats afterwards
            // (Board.cpp:365), and those three fields are part of the board base chunk.
            Board aLoaded = newLevel1();
            assertTrue(SaveGame.lawnLoadGame(aLoaded, aFile.toString()));
            assertTrue(SaveGame.lawnSaveGame(aLoaded, aRewritten.toString()));

            assertArrayEquals(Files.readAllBytes(aFile), Files.readAllBytes(aRewritten));
        } finally {
            Files.deleteIfExists(aFile);
            Files.deleteIfExists(aRewritten);
        }
    }

    /**
     * A definition with two emitters, installed at the fume-cloud slot of the process-wide particle
     * definition table: {@code SyncParticleDefPortable} (SaveGame.cpp:1200-1225) stores a definition's
     * index into {@code gParticleDefArray}, so a saved particle system can only be restored when its
     * definition is in that table. Going through the table also keeps the test asset independent
     * (the headless fixture rebuilds the table per app either way).
     */
    private static PvzpParticleDefinition twoEmitterDef() {
        PvzpParticleDefinition aDef = new PvzpParticleDefinition();
        aDef.mEmitterDefs = new DefinitionArrayDef(new PvzpEmitterDefinition[] {
                probeEmitterDef("ProbeA"), probeEmitterDef("ProbeB") });
        return aDef;
    }

    /** Puts {@code theDef} at the fume-cloud slot of the table the sync resolves def indices in. */
    private static void installEmitterDef(PvzpParticleDefinition theDef) {
        PvzpParticleDefinitions.gParticleDefArray[ParticleEffect.PARTICLE_FUMECLOUD] = theDef;
    }

    /**
     * Puts the fume-cloud definition back after a test replaced it. The headless fixture rebuilds
     * {@code gParticleDefArray} for every app, so the backup is written into whatever table is current
     * (a different instance is the same parsed definition).
     */
    private static void restoreFumeCloudDef(PvzpParticleDefinition theBackup) {
        PvzpParticleDefinition[] aTable = PvzpParticleDefinitions.gParticleDefArray;
        if (aTable != null && theBackup != null) {
            aTable[ParticleEffect.PARTICLE_FUMECLOUD] = theBackup;
        }
    }

    /** One live emitter definition: a box emitter that emits a particle every frame for 100 frames. */
    private static PvzpEmitterDefinition probeEmitterDef(String theName) {
        PvzpEmitterDefinition aDef = new PvzpEmitterDefinition();
        aDef.mEmitterType = EmitterType.EMITTER_BOX;
        aDef.mName = theName;
        aDef.mImageFrames = 4;
        aDef.mParticleDuration = constantTrack(50.0f);
        aDef.mSpawnRate = constantTrack(100.0f);
        aDef.mSpawnMinActive = constantTrack(-1.0f);
        aDef.mSpawnMaxActive = constantTrack(-1.0f);
        aDef.mSpawnMaxLaunched = constantTrack(-1.0f);
        aDef.mLaunchSpeed = constantTrack(100.0f);
        aDef.mLaunchAngle = constantTrack(90.0f);
        aDef.mEmitterBoxX = constantTrack(0.0f);
        aDef.mEmitterBoxY = constantTrack(0.0f);
        aDef.mEmitterOffsetX = constantTrack(0.0f);
        aDef.mEmitterOffsetY = constantTrack(0.0f);
        aDef.mEmitterSkewX = constantTrack(0.0f);
        aDef.mEmitterSkewY = constantTrack(0.0f);
        aDef.mSystemDuration = constantTrack(100.0f);
        return aDef;
    }

    private static FloatParameterTrack constantTrack(float theValue) {
        FloatParameterTrack aTrack = new FloatParameterTrack();
        Definition.floatTrackSetDefault(aTrack, theValue);
        return aTrack;
    }

    /** A live particle system with both of the definition's emitters and spawned particles. */
    private static PvzpParticleSystem addLiveParticleSystem(Board theBoard, PvzpParticleDefinition theDef) {
        PvzpParticleSystem aSystem = theBoard.mApp.mEffectSystem.mParticleHolder.allocParticleSystemFromDef(
                120.0f, 200.0f, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, 0, 0),
                theDef, ParticleEffect.PARTICLE_FUMECLOUD);
        assertNotNull(aSystem, "the particle system must be created");
        for (int i = 0; i < 12; i++) {
            aSystem.update();
        }
        return aSystem;
    }

    /** The emitter ids a particle system's list carries, in list order. */
    private static int[] emitterNodeIds(PvzpParticleSystem theSystem) {
        int[] aIds = new int[theSystem.mEmitterList.mSize];
        int i = 0;
        for (PvzpListNode<Integer> aNode = theSystem.mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            aIds[i++] = aNode.mValue;
        }
        return aIds;
    }

    /**
     * A mid-level save whose board owns live particle emitters must load back and resume.
     *
     * <p>Chunks 8 and 9 ({@code SyncDataArrayIdsOnlyPortable}, SaveGame.cpp:1405-1431) carry only the id
     * table of the emitters and particles pools, and chunk 10 references those ids from the systems'
     * emitter lists ({@code mEmitters.DataArrayGet(aNode->mValue)}, SaveGame.cpp:1330). Upstream's array
     * holds an object in every slot (DataArray.h:52-58), so the ids the load restores resolve to it;
     * the port's slots start out empty, which used to make the load re-create every listed object under
     * a new id. For a pool the save leaves nearly full - what a long level's emitter pool looks like -
     * that re-creation overflowed the pool, the overflow was discarded as a malformed field
     * ({@code ApplyFieldWithSync}'s result is ignored, SaveGame.cpp:1101), and the system kept a list
     * node whose id had no slot: the first frame after the load then threw
     * {@code Failed: dataArrayGet(...) for emitters} (PvzpParticleSystem.update, PvzpParticleSystem.java:79).
     */
    @Test
    void v4RoundTripRestoresLiveParticleEmittersAndResumes() throws IOException {
        PvzpParticleDefinition aBackup = null;
        Path aFile = Files.createTempFile("pvz-emitters", ".v4");
        try {
            Board aSaved = newLevel1();
            aBackup = PvzpParticleDefinitions.gParticleDefArray[ParticleEffect.PARTICLE_FUMECLOUD];
            PvzpParticleDefinition aDef = twoEmitterDef();
            installEmitterDef(aDef);
            PvzpParticleHolder aSavedHolder = aSaved.mApp.mEffectSystem.mParticleHolder;
            PvzpParticleSystem aSystem = addLiveParticleSystem(aSaved, aDef);
            int[] aSavedEmitterIds = emitterNodeIds(aSystem);
            assertEquals(2, aSavedEmitterIds.length);
            assertTrue(aSavedHolder.mParticles.mSize > 0, "the cloud must have live particles");
            // A long level hands the emitter pool out and never takes it back, so a save can carry a
            // pool with no free slot left at all. That is the case the pre-fix load could not survive:
            // re-creating the listed emitters needed slots the pool did not have.
            while (aSavedHolder.mEmitters.mSize < aSavedHolder.mEmitters.mMaxSize) {
                aSavedHolder.mEmitters.dataArrayAlloc();
            }
            int aSavedSize = aSavedHolder.mEmitters.mSize;
            int aSavedMaxUsed = aSavedHolder.mEmitters.mMaxUsedCount;
            int aSavedNextKey = aSavedHolder.mEmitters.mNextKey;
            int aSavedFreeList = aSavedHolder.mEmitters.mFreeListHead;
            int aSavedParticles = aSavedHolder.mParticles.mSize;

            assertTrue(SaveGame.lawnSaveGame(aSaved, aFile.toString()));

            Board aLoaded = newLevel1();
            installEmitterDef(aDef);
            assertTrue(SaveGame.lawnLoadGame(aLoaded, aFile.toString()));
            PvzpParticleHolder aLoadedHolder = aLoaded.mApp.mEffectSystem.mParticleHolder;
            PvzpParticleSystem aLoadedSystem = onlyItem(aLoadedHolder.mParticleSystems);
            int[] aLoadedEmitterIds = emitterNodeIds(aLoadedSystem);
            int aLoadedParticles = aLoadedHolder.mParticles.mSize;

            // The frame the reported crash happened on: Board.Update -> EffectSystem.Update ->
            // PvzpParticleSystem.Update dereferences each emitter-list id (PvzpParticleSystem.java:79).
            // It runs before the state assertions, so a load that leaves an id without a slot shows up
            // as the reported IllegalStateException and not as a size mismatch.
            for (int aFrame = 0; aFrame < 5; aFrame++) {
                aLoaded.mApp.mEffectSystem.update();
                aLoaded.mApp.mEffectSystem.processDeleteQueue();
            }

            // The restored pools must be the saved ones: same slots, same ids, same free list, no
            // re-created objects.
            assertEquals(aSavedSize, aLoadedHolder.mEmitters.mSize);
            assertEquals(aSavedMaxUsed, aLoadedHolder.mEmitters.mMaxUsedCount);
            assertEquals(aSavedNextKey, aLoadedHolder.mEmitters.mNextKey);
            assertEquals(aSavedFreeList, aLoadedHolder.mEmitters.mFreeListHead);
            assertEquals(aSavedParticles, aLoadedParticles);

            assertSame(aDef, aLoadedSystem.mParticleDef, "the saved definition index must map back to the table");
            assertArrayEquals(aSavedEmitterIds, aLoadedEmitterIds, "the saved emitter ids must survive the load");
            int i = 0;
            for (PvzpListNode<Integer> aNode = aLoadedSystem.mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
                PvzpParticleEmitter aEmitter = aLoadedHolder.mEmitters.dataArrayTryToGet(aNode.mValue);
                assertNotNull(aEmitter, "restored emitter node " + i + " must resolve");
                assertNotNull(aEmitter.mEmitterDef, "restored emitter " + i + " must carry its definition");
                assertSame(aLoadedSystem, aEmitter.mParticleSystem);
                i++;
            }
        } finally {
            restoreFumeCloudDef(aBackup);
            Files.deleteIfExists(aFile);
        }
    }

    /**
     * A save that carries particle systems must still rewrite byte for byte, like every other level
     * state (see {@link #v4RewriteOfALoadedLevelIsByteIdentical}). Chunks 8/9 store the socket id table
     * verbatim, so a load that re-created the emitters and particles under fresh ids would not.
     */
    @Test
    void v4RewriteOfALoadedLevelWithParticlesIsByteIdentical() throws IOException {
        PvzpParticleDefinition aBackup = null;
        Path aFile = Files.createTempFile("pvz-rewrite-emitters", ".v4");
        Path aRewritten = Files.createTempFile("pvz-rewrite-emitters", ".v4");
        try {
            Board aSaved = newLevel1();
            aBackup = PvzpParticleDefinitions.gParticleDefArray[ParticleEffect.PARTICLE_FUMECLOUD];
            PvzpParticleDefinition aDef = twoEmitterDef();
            installEmitterDef(aDef);
            addLiveParticleSystem(aSaved, aDef);
            assertTrue(SaveGame.lawnSaveGame(aSaved, aFile.toString()));

            Board aLoaded = newLevel1();
            installEmitterDef(aDef);
            assertTrue(SaveGame.lawnLoadGame(aLoaded, aFile.toString()));
            assertTrue(SaveGame.lawnSaveGame(aLoaded, aRewritten.toString()));

            assertArrayEquals(Files.readAllBytes(aFile), Files.readAllBytes(aRewritten));
        } finally {
            restoreFumeCloudDef(aBackup);
            Files.deleteIfExists(aFile);
            Files.deleteIfExists(aRewritten);
        }
    }

    /**
     * A list node whose id the save does not carry names an object that does not exist (its slot was
     * freed before the save, so chunk 8 carries the slot's free-list link instead of the id). Upstream
     * dereferences the id with {@code DataArrayGet}, which has no liveness check outside debug builds
     * (DataArray.h:186-190), so the port drops the node - the same shape as GameSelector dropping a
     * stale reanimation id with {@code reanimationTryToGet} - instead of dereferencing it on the first
     * frame after the load.
     */
    @Test
    void loadDropsAnEmitterNodeWhoseIdTheSaveDoesNotCarry() throws IOException {
        PvzpParticleDefinition aBackup = null;
        Path aFile = Files.createTempFile("pvz-stale-emitter", ".v4");
        try {
            Board aSaved = newLevel1();
            aBackup = PvzpParticleDefinitions.gParticleDefArray[ParticleEffect.PARTICLE_FUMECLOUD];
            PvzpParticleDefinition aDef = twoEmitterDef();
            installEmitterDef(aDef);
            PvzpParticleHolder aHolder = aSaved.mApp.mEffectSystem.mParticleHolder;
            PvzpParticleSystem aSystem = addLiveParticleSystem(aSaved, aDef);
            // Free one of the emitters the list still names: its slot becomes a free-list link, so the
            // saved id table cannot carry that id any more. (The write drops the node.)
            PvzpParticleEmitter aFreed = aHolder.mEmitters.dataArrayGet(emitterNodeIds(aSystem)[0]);
            aHolder.mEmitters.dataArrayFree(aFreed);

            assertTrue(SaveGame.lawnSaveGame(aSaved, aFile.toString()));
            assertEquals(1, aSystem.mEmitterList.mSize,
                    "the write drops a node whose emitter is not in the pool");

            Board aLoaded = newLevel1();
            installEmitterDef(aDef);
            assertTrue(SaveGame.lawnLoadGame(aLoaded, aFile.toString()));
            PvzpParticleHolder aLoadedHolder = aLoaded.mApp.mEffectSystem.mParticleHolder;
            PvzpParticleSystem aLoadedSystem = onlyItem(aLoadedHolder.mParticleSystems);
            assertEquals(1, aLoadedSystem.mEmitterList.mSize,
                    "an id the save does not carry must be dropped, not re-created");
            for (PvzpListNode<Integer> aNode = aLoadedSystem.mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
                assertNotNull(aLoadedHolder.mEmitters.dataArrayTryToGet(aNode.mValue),
                        "every remaining emitter node must resolve");
            }

            for (int aFrame = 0; aFrame < 5; aFrame++) {
                aLoaded.mApp.mEffectSystem.update();
                aLoaded.mApp.mEffectSystem.processDeleteQueue();
            }
        } finally {
            restoreFumeCloudDef(aBackup);
            Files.deleteIfExists(aFile);
        }
    }

    @Test
    void loadRejectsAPayloadWhoseCrcDoesNotMatch() throws IOException {
        Board aSaved = newLevel1();
        populateRunningLevel(aSaved);
        Path aFile = Files.createTempFile("pvz-bad-crc", ".v4");
        try {
            assertTrue(SaveGame.lawnSaveGame(aSaved, aFile.toString()));
            byte[] aBytes = Files.readAllBytes(aFile);
            aBytes[SaveGame.SAVE_FILE_HEADER_V4_SIZE + 4] ^= 0x40;  // corrupt the payload, keep the header
            Files.write(aFile, aBytes);

            Board aLoaded = newLevel1();
            assertFalse(aLoaded.loadGame(aFile.toString()));
        } finally {
            Files.deleteIfExists(aFile);
        }
    }

    @Test
    void loadRejectsAChunkWithAnUnknownVersionWithoutThrowing() throws IOException {
        // ReadChunkV4 refuses a chunk whose version is not SAVE4_CHUNK_VERSION (SaveGame.cpp:2048-2053).
        byte[] aPayload = rawChunk(SaveGame.SAVE4_CHUNK_BOARD_BASE, SAVE4_BAD_VERSION, new byte[] {(byte) 0x00});
        byte[] aFile = SaveGame.encodeSaveFile(aPayload);
        Path aPath = Files.createTempFile("pvz-bad-chunk", ".v4");
        try {
            Files.write(aPath, aFile);
            assertFalse(SaveGame.lawnLoadGame(null, aPath.toString()));
        } finally {
            Files.deleteIfExists(aPath);
        }
    }

    private static final int SAVE4_BAD_VERSION = 2;
}
