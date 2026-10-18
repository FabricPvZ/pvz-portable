/*
 * Golden test for the two profile save formats (../verification/research/03-save.md §4.3 and §5):
 *  - userdata/user{id}.dat  (PlayerInfo.syncDetails, version 12)
 *  - userdata/users.dat     (ProfileMgr.syncState, version 14)
 *
 * The byte expectations are hand-computed from the upstream layout description instead of being
 * produced by the production writer, so a regression in field order/size is caught.
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.sexy.Common;

class ProfileMgrTest {

    @TempDir
    Path mTempDir;

    @BeforeEach
    void setUp() {
        Common.setAppDataFolder(mTempDir.toString());
    }

    // ------------------------------------------------------------------ golden byte helpers

    private static byte[] le16(int theValue) {
        return new byte[] {(byte) theValue, (byte) (theValue >>> 8)};
    }

    private static byte[] le32(int theValue) {
        return new byte[] {
            (byte) theValue,
            (byte) (theValue >>> 8),
            (byte) (theValue >>> 16),
            (byte) (theValue >>> 24)
        };
    }

    private static byte[] le64(long theValue) {
        byte[] aBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            aBytes[i] = (byte) (theValue >>> (8 * i));
        }
        return aBytes;
    }

    private static byte[] string(String theString) {
        byte[] aBytes = theString.getBytes(StandardCharsets.ISO_8859_1);
        return concat(le16(aBytes.length), aBytes);
    }

    private static byte[] concat(byte[]... theParts) {
        int aLength = 0;
        for (byte[] aPart : theParts) {
            aLength += aPart.length;
        }
        byte[] aResult = new byte[aLength];
        int aPos = 0;
        for (byte[] aPart : theParts) {
            System.arraycopy(aPart, 0, aResult, aPos, aPart.length);
            aPos += aPart.length;
        }
        return aResult;
    }

    /** The distinctive PottedPlant #0 of the golden file (non-zero padding dwords included). */
    private static PottedPlant potZero() {
        PottedPlant aPot = new PottedPlant();
        aPot.mSeedType = 1;
        aPot.mWhichZenGarden = 2;
        aPot.mX = 3;
        aPot.mY = 4;
        aPot.mFacing = PottedPlant.FacingDirection.FACING_LEFT;
        aPot.mPadding1 = 0x11223344;
        aPot.mLastWateredTime = 0x0102030405060708L;
        aPot.mDrawVariation = 5;
        aPot.mPlantAge = 1;
        aPot.mTimesFed = 6;
        aPot.mFeedingsPerGrow = 4;
        aPot.mPlantNeed = 0;
        aPot.mPadding2 = 0x55667788;
        aPot.mLastNeedFulfilledTime = 0x1112131415161718L;
        aPot.mLastFertilizedTime = 0x2122232425262728L;
        aPot.mLastChocolateTime = 0x3132333435363738L;
        aPot.mFutureAttribute[0] = 0x4142434445464748L;
        return aPot;
    }

    private static byte[] potBytes(PottedPlant thePot) {
        return concat(
                le32(thePot.mSeedType),
                le32(thePot.mWhichZenGarden),
                le32(thePot.mX),
                le32(thePot.mY),
                le32(thePot.mFacing),
                le32(thePot.mPadding1),
                le64(thePot.mLastWateredTime),
                le32(thePot.mDrawVariation),
                le32(thePot.mPlantAge),
                le32(thePot.mTimesFed),
                le32(thePot.mFeedingsPerGrow),
                le32(thePot.mPlantNeed),
                le32(thePot.mPadding2),
                le64(thePot.mLastNeedFulfilledTime),
                le64(thePot.mLastFertilizedTime),
                le64(thePot.mLastChocolateTime),
                le64(thePot.mFutureAttribute[0]));
    }

    private static final int GOLDEN_HEAD_COUNT = 1;
    private static final int GOLDEN_FILE_SIZE = 205 * 4 + 2 * 88 + 20 * 2 + 1 + 4 + 0x48 + 20 + 1;

    /**
     * Hand-built `user{id}.dat` for version 12, per ../verification/research/03-save.md §4.3:
     * u32 version; u32 mLevel=7, mCoins=12345, mFinishedAdventure=1; 100 challenges (idx0=5, idx15=2);
     * 80 purchases (idx0=1000, idx1=7); 21 scalars = 101..121; 2 PottedPlants; 20 achievement u16s
     * (idx0/idx19 = 1); u8 zombatarAccepted=1; u32 headCount; headCount*0x48 bytes; 20 derived
     * mini-game flag bytes ([0]=1); u8 zombatarCreatedBefore=1.
     */
    private static byte[] goldenUserFile(int theVersion, int theDropTailBytes) {
        byte[] aZombatarData = new byte[GOLDEN_HEAD_COUNT * PlayerInfo.ZOMBATAR_RECORD_SIZE];
        Arrays.fill(aZombatarData, (byte) 0xAB);

        ByteArrayOutputStream aStream = new ByteArrayOutputStream();

        try {
            aStream.write(le32(theVersion));
            aStream.write(le32(7)); // mLevel
            aStream.write(le32(12345)); // mCoins
            aStream.write(le32(1)); // mFinishedAdventure
            for (int i = 0; i < 100; i++) {
                aStream.write(le32(i == 0 ? 5 : (i == 15 ? 2 : 0))); // mChallengeRecords
            }
            for (int i = 0; i < 80; i++) {
                aStream.write(le32(i == 0 ? 1000 : (i == 1 ? 7 : 0))); // mPurchases
            }
            for (int i = 0; i < 20; i++) {
                aStream.write(le32(101 + i)); // mPlayTimeActivePlayer .. mPlaceHolderPlayerStats
            }
            aStream.write(le32(2)); // mNumPottedPlants
            aStream.write(potBytes(potZero()));
            aStream.write(potBytes(new PottedPlant()));
            for (int i = 0; i < 20; i++) {
                aStream.write(le16(i == 0 || i == 19 ? 1 : 0)); // achievements (u16 0/1)
            }
            aStream.write(1); // mZombatarAccepted
            aStream.write(le32(GOLDEN_HEAD_COUNT)); // mZombatarHeadCount
            aStream.write(aZombatarData);
            for (int i = 0; i < 20; i++) {
                aStream.write(i == 0 ? 1 : 0); // mini-game flags derived from mChallengeRecords[i+15]
            }
            aStream.write(1); // mZombatarCreatedBefore
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        byte[] aFile = aStream.toByteArray();
        return Arrays.copyOf(aFile, aFile.length - theDropTailBytes);
    }

    private static PlayerInfo configuredPlayerInfo() {
        PlayerInfo aInfo = new PlayerInfo();
        aInfo.mId = 3;
        aInfo.mLevel = 7;
        aInfo.mCoins = 12345;
        aInfo.mFinishedAdventure = 1;
        aInfo.mChallengeRecords[0] = 5;
        aInfo.mChallengeRecords[15] = 2;
        aInfo.mPurchases[0] = 1000;
        aInfo.mPurchases[1] = 7;
        aInfo.mPlayTimeActivePlayer = 101;
        aInfo.mPlayTimeInactivePlayer = 102;
        aInfo.mHasUsedCheatKeys = 103;
        aInfo.mHasWokenStinky = 104;
        aInfo.mDidntPurchasePacketUpgrade = 105;
        aInfo.mLastStinkyChocolateTime = 106;
        aInfo.mStinkyPosX = 107;
        aInfo.mStinkyPosY = 108;
        aInfo.mHasUnlockedMinigames = 109;
        aInfo.mHasUnlockedPuzzleMode = 110;
        aInfo.mHasNewMiniGame = 111;
        aInfo.mHasNewScaryPotter = 112;
        aInfo.mHasNewIZombie = 113;
        aInfo.mHasNewSurvival = 114;
        aInfo.mHasUnlockedSurvivalMode = 115;
        aInfo.mNeedsMessageOnGameSelector = 116;
        aInfo.mNeedsMagicTacoReward = 117;
        aInfo.mHasSeenStinky = 118;
        aInfo.mHasSeenUpsell = 119;
        aInfo.mPlaceHolderPlayerStats = 120;
        aInfo.mNumPottedPlants = 2;
        aInfo.mPottedPlant[0] = potZero();
        aInfo.mPottedPlant[1] = new PottedPlant();
        aInfo.mEarnedAchievements[0] = true;
        aInfo.mEarnedAchievements[19] = true;
        aInfo.mZombatarAccepted = 1;
        aInfo.mZombatarData = new byte[GOLDEN_HEAD_COUNT * PlayerInfo.ZOMBATAR_RECORD_SIZE];
        Arrays.fill(aInfo.mZombatarData, (byte) 0xAB);
        aInfo.mZombatarCreatedBefore = 1;
        return aInfo;
    }

    // ------------------------------------------------------------------ user{id}.dat

    @Test
    void userFileLayoutMatchesUpstreamBytes() throws IOException {
        PlayerInfo aInfo = configuredPlayerInfo();
        aInfo.saveDetails();

        Path aPath = mTempDir.resolve("userdata").resolve("user3.dat");
        byte[] aWritten = Files.readAllBytes(aPath);
        byte[] aExpected = goldenUserFile(12, 0);

        assertEquals(GOLDEN_FILE_SIZE, aExpected.length);
        assertEquals(GOLDEN_FILE_SIZE, aWritten.length);
        assertArrayEquals(aExpected, aWritten);
    }

    @Test
    void writesUpstreamLayoutFileAndReadsItBackWithGoldenValues() throws IOException {
        // The file is written from the hand-computed upstream layout description, not by the port.
        Path aDir = mTempDir.resolve("userdata");
        Files.createDirectories(aDir);
        Files.write(aDir.resolve("user5.dat"), goldenUserFile(12, 0));

        PlayerInfo aInfo = new PlayerInfo();
        aInfo.mId = 5;
        aInfo.loadDetails();

        assertEquals(7, aInfo.mLevel);
        assertEquals(12345, aInfo.mCoins);
        assertEquals(1, aInfo.mFinishedAdventure);
        assertEquals(5, aInfo.mChallengeRecords[0]);
        assertEquals(2, aInfo.mChallengeRecords[15]);
        assertEquals(0, aInfo.mChallengeRecords[1]);
        assertEquals(1000, aInfo.mPurchases[0]);
        assertEquals(7, aInfo.mPurchases[1]);
        for (int i = 0; i < 20; i++) {
            assertEquals(101 + i, scalarAt(aInfo, i), "scalar " + i);
        }
        assertEquals(2, aInfo.mNumPottedPlants);
        assertEquals(0x11223344, aInfo.mPottedPlant[0].mPadding1);
        assertEquals(PottedPlant.FacingDirection.FACING_LEFT, aInfo.mPottedPlant[0].mFacing);
        assertEquals(0x0102030405060708L, aInfo.mPottedPlant[0].mLastWateredTime);
        assertEquals(0x4142434445464748L, aInfo.mPottedPlant[0].mFutureAttribute[0]);
        assertEquals(0x2122232425262728L, aInfo.mPottedPlant[0].mLastFertilizedTime);
        assertTrue(aInfo.mEarnedAchievements[0]);
        assertTrue(aInfo.mEarnedAchievements[19]);
        assertFalse(aInfo.mEarnedAchievements[1]);
        assertEquals(aInfo.mEarnedAchievements[0], aInfo.mShownAchievements[0]);
        assertEquals(aInfo.mEarnedAchievements[19], aInfo.mShownAchievements[19]);
        assertEquals(1, aInfo.mZombatarAccepted);
        assertEquals(GOLDEN_HEAD_COUNT, aInfo.mZombatarHeadCount);
        assertEquals(0x48, aInfo.mZombatarData.length);
        assertEquals((byte) 0xAB, aInfo.mZombatarData[0x47]);
        assertEquals(1, aInfo.mZombatarCreatedBefore);
    }

    @Test
    void roundTripsThroughTheProductionWriterAndReader() {
        PlayerInfo aWritten = configuredPlayerInfo();
        aWritten.saveDetails();

        PlayerInfo aRead = new PlayerInfo();
        aRead.mId = 3;
        aRead.loadDetails();

        assertEquals(7, aRead.mLevel);
        assertEquals(12345, aRead.mCoins);
        assertEquals(GOLDEN_HEAD_COUNT, aRead.mZombatarHeadCount);
        assertEquals(aWritten.mZombatarData.length, aRead.mZombatarData.length);
        assertArrayEquals(aWritten.mZombatarData, aRead.mZombatarData);
        for (int i = 0; i < 21; i++) {
            assertEquals(scalarAt(aWritten, i), scalarAt(aRead, i), "scalar " + i);
        }
        assertArrayEquals(aWritten.mChallengeRecords, aRead.mChallengeRecords);
        assertArrayEquals(aWritten.mPurchases, aRead.mPurchases);
        assertArrayEquals(aWritten.mEarnedAchievements, aRead.mEarnedAchievements);
        assertEquals(aWritten.mPottedPlant[0].mPadding2, aRead.mPottedPlant[0].mPadding2);
    }

    @Test
    void rejectsTruncatedFileGracefully() throws IOException {
        Path aDir = mTempDir.resolve("userdata");
        Files.createDirectories(aDir);
        // valid through the 20 achievement u16s, then cut inside the Zombatar block
        Files.write(aDir.resolve("user6.dat"), goldenUserFile(12, 4));

        PlayerInfo aInfo = new PlayerInfo();
        aInfo.mId = 6;
        aInfo.loadDetails();

        // profile kept, Zombatar fields zeroed (PlayerInfo.cpp:170-185)
        assertEquals(7, aInfo.mLevel);
        assertEquals(12345, aInfo.mCoins);
        assertEquals(0, aInfo.mZombatarAccepted);
        assertEquals(0, aInfo.mZombatarHeadCount);
        assertEquals(0, aInfo.mZombatarData.length);
        assertEquals(0, aInfo.mZombatarCreatedBefore);
    }

    @Test
    void rejectsFileTruncatedInsideTheScalars() throws IOException {
        Path aDir = mTempDir.resolve("userdata");
        Files.createDirectories(aDir);
        byte[] aFile = Arrays.copyOf(goldenUserFile(12, 0), 4 + 3 * 4 + 100 * 4 + 10);
        Files.write(aDir.resolve("user7.dat"), aFile);

        PlayerInfo aInfo = new PlayerInfo();
        aInfo.mId = 7;
        aInfo.mLevel = 42;
        aInfo.loadDetails();

        assertEquals(1, aInfo.mLevel); // Reset() defaults
        assertEquals(0, aInfo.mCoins);
    }

    @Test
    void rejectsUnknownVersionAndLeavesDefaults() throws IOException {
        Path aDir = mTempDir.resolve("userdata");
        Files.createDirectories(aDir);
        Files.write(aDir.resolve("user8.dat"), goldenUserFile(13, 0));

        PlayerInfo aInfo = new PlayerInfo();
        aInfo.mId = 8;
        aInfo.loadDetails();

        assertEquals(1, aInfo.mLevel);
        assertEquals(0, aInfo.mCoins);
        assertEquals(0, aInfo.mFinishedAdventure);
    }

    @Test
    void missingUserFileLeavesDefaults() {
        PlayerInfo aInfo = new PlayerInfo();
        aInfo.mId = 9;
        aInfo.loadDetails();

        assertEquals(1, aInfo.mLevel);
    }

    @Test
    void addCoinsClampsToTheUpstreamRange() {
        PlayerInfo aInfo = new PlayerInfo();
        aInfo.addCoins(500);
        assertEquals(500, aInfo.mCoins);
        aInfo.addCoins(-1000);
        assertEquals(0, aInfo.mCoins);
        aInfo.mCoins = 99998;
        aInfo.addCoins(100);
        assertEquals(99999, aInfo.mCoins);
    }

    @Test
    void resetChallengeRecordUsesTheSurvivalStageOneOffset() {
        PlayerInfo aInfo = new PlayerInfo();
        aInfo.mChallengeRecords[0] = 9;
        aInfo.resetChallengeRecord(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1);
        assertEquals(0, aInfo.mChallengeRecords[0]);
    }

    private static int scalarAt(PlayerInfo theInfo, int theIndex) {
        return switch (theIndex) {
            case 0 -> theInfo.mPlayTimeActivePlayer;
            case 1 -> theInfo.mPlayTimeInactivePlayer;
            case 2 -> theInfo.mHasUsedCheatKeys;
            case 3 -> theInfo.mHasWokenStinky;
            case 4 -> theInfo.mDidntPurchasePacketUpgrade;
            case 5 -> theInfo.mLastStinkyChocolateTime;
            case 6 -> theInfo.mStinkyPosX;
            case 7 -> theInfo.mStinkyPosY;
            case 8 -> theInfo.mHasUnlockedMinigames;
            case 9 -> theInfo.mHasUnlockedPuzzleMode;
            case 10 -> theInfo.mHasNewMiniGame;
            case 11 -> theInfo.mHasNewScaryPotter;
            case 12 -> theInfo.mHasNewIZombie;
            case 13 -> theInfo.mHasNewSurvival;
            case 14 -> theInfo.mHasUnlockedSurvivalMode;
            case 15 -> theInfo.mNeedsMessageOnGameSelector;
            case 16 -> theInfo.mNeedsMagicTacoReward;
            case 17 -> theInfo.mHasSeenStinky;
            case 18 -> theInfo.mHasSeenUpsell;
            case 19 -> theInfo.mPlaceHolderPlayerStats;
            case 20 -> theInfo.mNumPottedPlants;
            default -> throw new IllegalArgumentException();
        };
    }

    // ------------------------------------------------------------------ users.dat

    private static byte[] goldenUsersFile() {
        return concat(
                le32(14),
                le16(2),
                string("Alice"),
                le32(1),
                le32(1),
                string("Bob"),
                le32(2),
                le32(2));
    }

    @Test
    void usersDatLayoutMatchesUpstreamBytes() throws IOException {
        ProfileMgr aMgr = new ProfileMgr();
        aMgr.addProfile("Alice");
        aMgr.addProfile("Bob");
        aMgr.save();

        byte[] aWritten = Files.readAllBytes(mTempDir.resolve("userdata").resolve("users.dat"));
        assertArrayEquals(goldenUsersFile(), aWritten);
    }

    @Test
    void loadsUpstreamLayoutUsersDat() throws IOException {
        Path aDir = mTempDir.resolve("userdata");
        Files.createDirectories(aDir);
        Files.write(aDir.resolve("users.dat"), goldenUsersFile());

        ProfileMgr aMgr = new ProfileMgr();
        aMgr.load();

        assertEquals(2, aMgr.getNumProfiles());
        PlayerInfo aBob = aMgr.getProfile("bob"); // case-insensitive lookup
        assertNotNull(aBob);
        assertEquals(2, aBob.mId);
        assertEquals("Bob", aBob.mName);
        PlayerInfo aAny = aMgr.getAnyProfile();
        assertNotNull(aAny);
        assertEquals("Alice", aAny.mName);
        assertEquals(3, aBob.mUseSeq);
        assertEquals(4, aAny.mUseSeq);
    }

    @Test
    void truncatedUsersDatIsRejected() throws IOException {
        Path aDir = mTempDir.resolve("userdata");
        Files.createDirectories(aDir);
        Files.write(aDir.resolve("users.dat"), concat(le32(14), le16(2)));

        ProfileMgr aMgr = new ProfileMgr();
        aMgr.load();

        assertEquals(0, aMgr.getNumProfiles());
    }

    @Test
    void unknownUsersDatVersionIsRejected() throws IOException {
        Path aDir = mTempDir.resolve("userdata");
        Files.createDirectories(aDir);
        Files.write(aDir.resolve("users.dat"), concat(le32(13), le16(1), string("X"), le32(1), le32(1)));

        ProfileMgr aMgr = new ProfileMgr();
        aMgr.load();

        assertEquals(0, aMgr.getNumProfiles());
    }

    @Test
    void missingUsersDatIsNotAnError() {
        ProfileMgr aMgr = new ProfileMgr();
        aMgr.load();
        assertEquals(0, aMgr.getNumProfiles());
    }

    @Test
    void renameDeleteAndCaseInsensitiveSemantics() {
        ProfileMgr aMgr = new ProfileMgr();
        aMgr.addProfile("Alice");
        aMgr.addProfile("Bob");

        assertTrue(aMgr.renameProfile("Alice", "ALICE"));
        assertEquals("ALICE", aMgr.getProfile("alice").mName);
        assertEquals(2, aMgr.getNumProfiles());

        assertFalse(aMgr.renameProfile("ALICE", "Bob")); // name already taken
        assertTrue(aMgr.renameProfile("Bob", "Carol"));
        assertNull(aMgr.getProfile("Bob"));
        assertNotNull(aMgr.getProfile("Carol"));

        assertTrue(aMgr.deleteProfile("carol"));
        assertFalse(aMgr.deleteProfile("carol"));
        assertEquals(1, aMgr.getNumProfiles());
    }

    @Test
    void keepsAtMostTwoHundredProfilesDroppingTheOldest() {
        ProfileMgr aMgr = new ProfileMgr();
        for (int i = 0; i < 201; i++) {
            assertNotNull(aMgr.addProfile("P" + i));
        }

        assertEquals(200, aMgr.getNumProfiles());
        assertNull(aMgr.getProfile("P0"));
        assertNotNull(aMgr.getProfile("P200"));
    }
}
