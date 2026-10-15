/*
 * Ported from src/Lawn/System/PlayerInfo.h:69-123 (class PlayerInfo) and
 * src/Lawn/System/PlayerInfo.cpp:64-329.
 *
 * Per-profile adventure progress. `user{id}.dat` (version 12, gUserVersion) holds everything in
 * `SyncDetails` below; the three summary fields (mName/mUseSeq/mId) live in `users.dat` instead and
 * are serialized by `SyncSummary` (that is what ProfileMgr::SyncState writes per profile record).
 *
 * Byte layout: docs/research/03-save.md §4.3 (all integers little-endian; strings are a u16 byte
 * length followed by the raw bytes). Achievements are stored as 20 x u16 (0/1); mShownAchievements
 * is NOT persisted and is rebuilt equal to mEarnedAchievements on load; the 20-byte mini-game flag
 * block after the Zombatar records is derived from mChallengeRecords[i + 0x0F] on write and
 * discarded on read (PlayerInfo.cpp:159-167, :202-208).
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import java.nio.file.Files;
import java.nio.file.Path;

import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.DataReader;
import cn.enaium.fabricpvz.portable.sexy.DataReaderException;
import cn.enaium.fabricpvz.portable.sexy.DataSync;
import cn.enaium.fabricpvz.portable.sexy.DataWriter;

public class PlayerInfo {

    /** Ported from src/Lawn/System/PlayerInfo.h:25. */
    public static final int MAX_POTTED_PLANTS = 200;
    /** Ported from src/Lawn/System/PlayerInfo.h:26. */
    public static final int PURCHASE_COUNT_OFFSET = 1000;
    /** Ported from src/Lawn/System/PlayerInfo.h:27. */
    public static final int ZOMBATAR_RECORD_SIZE = 0x48;
    /** Ported from src/Lawn/System/PlayerInfo.h:28. */
    public static final int MAX_ZOMBATAR_HEADS = 100;

    /** Ported from src/Lawn/System/PlayerInfo.cpp:35 (`static int gUserVersion = 12;`). */
    private static int gUserVersion = 12;

    // --- users.dat summary record (PlayerInfo.h:72-74) ---------------------------------------
    public String mName = ""; // +0x00 (not in user{id}.dat)
    public int mUseSeq; // +0x1C (not in user{id}.dat)
    public int mId; // +0x20 (not in user{id}.dat)

    // --- user{id}.dat (PlayerInfo.h:75-118) --------------------------------------------------
    public int mLevel; // +0x24
    public int mCoins; // +0x28
    public int mFinishedAdventure; // +0x2C
    public int[] mChallengeRecords = new int[100]; // +0x30
    public int[] mPurchases = new int[80]; // +0x1C0
    public int mPlayTimeActivePlayer; // +0x300
    public int mPlayTimeInactivePlayer; // +0x304
    public int mHasUsedCheatKeys; // +0x308
    public int mHasWokenStinky; // +0x30C
    public int mDidntPurchasePacketUpgrade; // +0x310
    public int mLastStinkyChocolateTime; // +0x314
    public int mStinkyPosX; // +0x318
    public int mStinkyPosY; // +0x31C
    public int mHasUnlockedMinigames; // +0x320
    public int mHasUnlockedPuzzleMode; // +0x324
    public int mHasNewMiniGame; // +0x328
    public int mHasNewScaryPotter; // +0x32C
    public int mHasNewIZombie; // +0x330
    public int mHasNewSurvival; // +0x334
    public int mHasUnlockedSurvivalMode; // +0x338
    public int mNeedsMessageOnGameSelector; // +0x33C
    public int mNeedsMagicTacoReward; // +0x340
    public int mHasSeenStinky; // +0x344
    public int mHasSeenUpsell; // +0x348
    public int mPlaceHolderPlayerStats; // reserved, always 0
    public int mNumPottedPlants; // +0x350
    public PottedPlant[] mPottedPlant = new PottedPlant[MAX_POTTED_PLANTS]; // +0x358
    public boolean[] mEarnedAchievements = new boolean[20];
    public boolean[] mShownAchievements = new boolean[20];
    /** Upstream `unsigned char mZombatarAccepted` (PlayerInfo.h:115); written as a u8 (0/1). */
    public int mZombatarAccepted;
    public int mZombatarHeadCount;
    /** Raw `0x48`-byte Zombatar records; exactly `mZombatarHeadCount * ZOMBATAR_RECORD_SIZE` bytes. */
    public byte[] mZombatarData = new byte[0];
    /** Upstream `unsigned char mZombatarCreatedBefore` (PlayerInfo.h:121); written as a u8 (0/1). */
    public int mZombatarCreatedBefore;

    /** Ported from src/Lawn/System/PlayerInfo.cpp:64-67. */
    public PlayerInfo() {
        for (int i = 0; i < MAX_POTTED_PLANTS; i++) {
            mPottedPlant[i] = new PottedPlant();
        }
        reset();
    }

    /**
     * Ported from src/Lawn/System/PlayerInfo.cpp:69-74.
     *
     * <p>This is the per-profile record inside `users.dat` (ProfileMgr::SyncState); mName/mUseSeq/mId
     * are not part of `user{id}.dat`.
     */
    public void syncSummary(DataSync theSync) {
        mName = theSync.syncString(mName);
        mUseSeq = theSync.syncUInt32(mUseSeq);
        mId = theSync.syncUInt32(mId);
    }

    /**
     * Ported from src/Lawn/System/PlayerInfo.cpp:76-213.
     *
     * <p>Stream order is exactly docs/research/03-save.md §4.3: version u32, mLevel, mCoins,
     * mFinishedAdventure, 100 challenge records, 80 purchases, 21 scalars, the live PottedPlant blobs,
     * 20 achievement u16s, the Zombatar block, 20 derived mini-game flag bytes and the
     * mZombatarCreatedBefore byte.
     */
    public void syncDetails(DataSync theSync) {
        if (theSync.getReader() != null) {
            reset();
        }

        int aVersion = gUserVersion;
        aVersion = theSync.syncUInt32(aVersion);
        theSync.setVersion(aVersion);
        if (aVersion != gUserVersion) {
            return;
        }

        mLevel = theSync.syncUInt32(mLevel);
        mCoins = theSync.syncUInt32(mCoins);
        mFinishedAdventure = theSync.syncUInt32(mFinishedAdventure);
        for (int i = 0; i < 100; i++) {
            mChallengeRecords[i] = theSync.syncUInt32(mChallengeRecords[i]);
        }
        for (int i = 0; i < 80; i++) {
            mPurchases[i] = theSync.syncUInt32(mPurchases[i]);
        }
        mPlayTimeActivePlayer = theSync.syncUInt32(mPlayTimeActivePlayer);
        mPlayTimeInactivePlayer = theSync.syncUInt32(mPlayTimeInactivePlayer);
        mHasUsedCheatKeys = theSync.syncUInt32(mHasUsedCheatKeys);
        mHasWokenStinky = theSync.syncUInt32(mHasWokenStinky);
        mDidntPurchasePacketUpgrade = theSync.syncUInt32(mDidntPurchasePacketUpgrade);
        mLastStinkyChocolateTime = theSync.syncUInt32(mLastStinkyChocolateTime);
        mStinkyPosX = theSync.syncUInt32(mStinkyPosX);
        mStinkyPosY = theSync.syncUInt32(mStinkyPosY);
        mHasUnlockedMinigames = theSync.syncUInt32(mHasUnlockedMinigames);
        mHasUnlockedPuzzleMode = theSync.syncUInt32(mHasUnlockedPuzzleMode);
        mHasNewMiniGame = theSync.syncUInt32(mHasNewMiniGame);
        mHasNewScaryPotter = theSync.syncUInt32(mHasNewScaryPotter);
        mHasNewIZombie = theSync.syncUInt32(mHasNewIZombie);
        mHasNewSurvival = theSync.syncUInt32(mHasNewSurvival);
        mHasUnlockedSurvivalMode = theSync.syncUInt32(mHasUnlockedSurvivalMode);
        mNeedsMessageOnGameSelector = theSync.syncUInt32(mNeedsMessageOnGameSelector);
        mNeedsMagicTacoReward = theSync.syncUInt32(mNeedsMagicTacoReward);
        mHasSeenStinky = theSync.syncUInt32(mHasSeenStinky);
        mHasSeenUpsell = theSync.syncUInt32(mHasSeenUpsell);
        mPlaceHolderPlayerStats = theSync.syncUInt32(mPlaceHolderPlayerStats);
        mNumPottedPlants = theSync.syncUInt32(mNumPottedPlants);

        // PVZP_ASSERT(mNumPottedPlants <= MAX_POTTED_PLANTS) (PlayerInfo.cpp:124)
        for (int i = 0; i < mNumPottedPlants; i++) {
            mPottedPlant[i].sync(theSync);
        }

        // achievements are stored as 20 x 16-bit values (0/1) (PlayerInfo.cpp:133-143)
        for (int i = 0; i < 20; i++) {
            int aAchievementValue = mEarnedAchievements[i] ? 1 : 0;
            aAchievementValue = theSync.syncUInt16(aAchievementValue);
            if (theSync.getReader() != null) {
                mEarnedAchievements[i] = aAchievementValue != 0;
                mShownAchievements[i] = mEarnedAchievements[i];
            }
        }

        if (theSync.getReader() != null) {
            try {
                int aZombatarAccepted = 0;
                aZombatarAccepted = theSync.syncUInt8(aZombatarAccepted);
                mZombatarAccepted = aZombatarAccepted != 0 ? 1 : 0;

                int aZombatarHeadCount = 0;
                aZombatarHeadCount = theSync.syncUInt32(aZombatarHeadCount);
                if (Integer.toUnsignedLong(aZombatarHeadCount) > MAX_ZOMBATAR_HEADS) {
                    throw new DataReaderException();
                }

                mZombatarHeadCount = aZombatarHeadCount;
                mZombatarData = new byte[mZombatarHeadCount * ZOMBATAR_RECORD_SIZE];
                if (mZombatarData.length != 0) {
                    theSync.syncBytes(mZombatarData);
                }
                byte[] aMiniGameFlags = new byte[0x14]; // consumed and discarded: derived on save
                theSync.syncBytes(aMiniGameFlags);

                int aZombatarCreatedBefore = 0;
                aZombatarCreatedBefore = theSync.syncUInt8(aZombatarCreatedBefore);
                mZombatarCreatedBefore = aZombatarCreatedBefore != 0 ? 1 : 0;
            } catch (DataReaderException e) {
                mZombatarAccepted = 0;
                mZombatarHeadCount = 0;
                mZombatarData = new byte[0];
                mZombatarCreatedBefore = 0;
            }
            return;
        }

        int aZombatarAccepted = mZombatarAccepted != 0 ? 1 : 0;
        aZombatarAccepted = theSync.syncUInt8(aZombatarAccepted);
        mZombatarAccepted = aZombatarAccepted;

        mZombatarHeadCount = mZombatarData.length / ZOMBATAR_RECORD_SIZE;
        if (mZombatarHeadCount > MAX_ZOMBATAR_HEADS) {
            mZombatarHeadCount = MAX_ZOMBATAR_HEADS;
            mZombatarData = java.util.Arrays.copyOf(mZombatarData, mZombatarHeadCount * ZOMBATAR_RECORD_SIZE);
        }
        int aZombatarDataBytes = mZombatarHeadCount * ZOMBATAR_RECORD_SIZE;
        mZombatarHeadCount = theSync.syncUInt32(mZombatarHeadCount);
        if (aZombatarDataBytes > 0) {
            byte[] aData = java.util.Arrays.copyOf(mZombatarData, aZombatarDataBytes);
            theSync.syncBytes(aData);
        }
        {
            byte[] aMiniGameFlags = new byte[0x14];
            for (int i = 0; i < 20; i++) {
                aMiniGameFlags[i] = mChallengeRecords[i + 0x0F] > 0 ? (byte) 1 : (byte) 0;
            }
            theSync.syncBytes(aMiniGameFlags);
        }

        int aZombatarCreatedBefore = mZombatarCreatedBefore != 0 ? 1 : 0;
        aZombatarCreatedBefore = theSync.syncUInt8(aZombatarCreatedBefore);
        mZombatarCreatedBefore = aZombatarCreatedBefore;
    }

    /** Ported from src/Lawn/System/PlayerInfo.cpp:215-236. */
    public void loadDetails() {
        try {
            String aFileName = Common.getAppDataPath("userdata/user" + Integer.toUnsignedString(mId) + ".dat");
            DataReader aReader = new DataReader();
            if (!aReader.openFile(aFileName)) {
                return;
            }
            DataSync aSync = new DataSync(aReader);
            syncDetails(aSync);
        } catch (DataReaderException e) {
            // Ported from PlayerInfo.cpp:231-235 (`PvzpLogLn("Failed to player data, resetting it")`).
            // PORT-NOTE: ERROR, not the INFO upstream used: this discards the player's saved profile
            // and no other line reports it.
            Common.logErrorLn("Failed to player data, resetting it");
            reset();
        }
    }

    /** Ported from src/Lawn/System/PlayerInfo.cpp:238-248. */
    public void saveDetails() {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        DataSync aSync = new DataSync(aWriter);
        syncDetails(aSync);

        Common.mkDir(Common.getAppDataPath("userdata"));
        String aFileName = Common.getAppDataPath("userdata/user" + Integer.toUnsignedString(mId) + ".dat");
        aWriter.writeToFile(aFileName);
    }

    /** Ported from src/Lawn/System/PlayerInfo.cpp:250-262. */
    public void deleteUserFiles() {
        eraseFile(Common.getAppDataPath("userdata/user" + Integer.toUnsignedString(mId) + ".dat"));

        for (int i = 0; i < GameMode.NUM_GAME_MODES; i++) {
            eraseFile(LawnCommon.getSavedGameName(i, mId));
            eraseFile(LawnCommon.getLegacySavedGameName(i, mId));
        }
    }

    /** Ported from src/Lawn/System/PlayerInfo.cpp:264-299. */
    public void reset() {
        mLevel = 1;
        mCoins = 0;
        mFinishedAdventure = 0;
        java.util.Arrays.fill(mChallengeRecords, 0);
        java.util.Arrays.fill(mPurchases, 0);
        mPlayTimeActivePlayer = 0;
        mPlayTimeInactivePlayer = 0;
        mHasUsedCheatKeys = 0;
        mHasWokenStinky = 0;
        mDidntPurchasePacketUpgrade = 0;
        mLastStinkyChocolateTime = 0;
        mStinkyPosX = 0;
        mStinkyPosY = 0;
        mHasUnlockedMinigames = 0;
        mHasUnlockedPuzzleMode = 0;
        mHasNewMiniGame = 0;
        mHasNewScaryPotter = 0;
        mHasNewIZombie = 0;
        mHasNewSurvival = 0;
        mHasUnlockedSurvivalMode = 0;
        mNeedsMessageOnGameSelector = 0;
        mNeedsMagicTacoReward = 0;
        mHasSeenStinky = 0;
        mHasSeenUpsell = 0;
        mPlaceHolderPlayerStats = 0;
        for (int i = 0; i < MAX_POTTED_PLANTS; i++) {
            mPottedPlant[i] = new PottedPlant();
        }
        mNumPottedPlants = 0;
        java.util.Arrays.fill(mEarnedAchievements, false);
        java.util.Arrays.fill(mShownAchievements, false);
        mZombatarAccepted = 0;
        mZombatarHeadCount = 0;
        mZombatarData = new byte[0];
        mZombatarCreatedBefore = 0;
    }

    /** Ported from src/Lawn/System/PlayerInfo.cpp:301-305 (`std::clamp(mCoins, 0, 99999)`). */
    public void addCoins(int theAmount) {
        mCoins = Math.clamp(mCoins + theAmount, 0, 99999);
    }

    /** Ported from src/Lawn/System/PlayerInfo.h:120. */
    public int getLevel() {
        return mLevel;
    }

    /** Ported from src/Lawn/System/PlayerInfo.h:121. */
    public void setLevel(int theLevel) {
        mLevel = theLevel;
    }

    /** Ported from src/Lawn/System/PlayerInfo.cpp:307-312. */
    public void resetChallengeRecord(int theGameMode) {
        int aGameMode = theGameMode - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
        // PVZP_ASSERT(aGameMode >= 0 && aGameMode <= NUM_CHALLENGE_MODES) (ChallengeScreen.h:31)
        mChallengeRecords[aGameMode] = 0;
    }

    /** Java replacement for {@code gSexyAppBase->EraseFile(path)} (PlayerInfo.cpp:254, :258). */
    private static void eraseFile(String theFileName) {
        try {
            Files.deleteIfExists(Path.of(theFileName));
        } catch (java.io.IOException e) {
            // Upstream ignores the failure as well.
        }
    }
}
