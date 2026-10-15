/*
 * Ported from src/Lawn/System/ProfileMgr.h:26-56 (class ProfileMgr) and
 * src/Lawn/System/ProfileMgr.cpp:26-205.
 *
 * The profile index backed by `userdata/users.dat` (version 14, gProfileVersion):
 *   u32 version; u16 profileCount; then per profile: string mName (u16 length + bytes),
 *   u32 mUseSeq, u32 mId                       (docs/research/03-save.md §5)
 *
 * Upstream the profiles live in `std::map<std::string, PlayerInfo, Sexy::StringLessNoCase>`, i.e.
 * keyed by name with a case-insensitive, name-ordered comparison; a TreeMap with
 * String.CASE_INSENSITIVE_ORDER reproduces that (iteration order = the on-disk record order). At
 * most 200 profiles are kept and the one with the smallest mUseSeq is dropped (ProfileMgr.h:44,
 * .cpp:166-177).
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import java.util.Map;
import java.util.TreeMap;

import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.DataReader;
import cn.enaium.fabricpvz.portable.sexy.DataReaderException;
import cn.enaium.fabricpvz.portable.sexy.DataSync;
import cn.enaium.fabricpvz.portable.sexy.DataWriter;

public class ProfileMgr {

    /** Ported from src/Lawn/System/ProfileMgr.cpp:25 (`static int gProfileVersion = 14;`). */
    private static int gProfileVersion = 14;

    protected TreeMap<String, PlayerInfo> mProfileMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    protected long mNextProfileId;
    protected long mNextProfileUseSeq;

    /** Ported from src/Lawn/System/ProfileMgr.h:34 ({@code ProfileMgr() { Clear(); }}). */
    public ProfileMgr() {
        clear();
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:26-34. */
    public PlayerInfo getAnyProfile() {
        if (mProfileMap.isEmpty()) {
            return null;
        }

        PlayerInfo aPlayerInfo = mProfileMap.firstEntry().getValue();
        aPlayerInfo.loadDetails();
        aPlayerInfo.mUseSeq = (int) mNextProfileUseSeq++;
        return aPlayerInfo;
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:36-41. */
    public void clear() {
        mProfileMap.clear();
        mNextProfileId = 1L;
        mNextProfileUseSeq = 1L;
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:43-90. */
    protected void syncState(DataSync theSync) {
        DataReader aReader = theSync.getReader();
        DataWriter aWriter = theSync.getWriter();

        int aVersion = gProfileVersion;
        aVersion = theSync.syncUInt32(aVersion);
        theSync.setVersion(aVersion);
        if (aVersion == gProfileVersion) {
            if (aReader != null) {
                mProfileMap.clear();

                long aMaxProfileId = 0;
                long aMaxUseSeq = 0;
                for (int aProfileCount = aReader.readUInt16(); aProfileCount > 0; aProfileCount--) {
                    PlayerInfo aProfile = new PlayerInfo();
                    aProfile.syncSummary(theSync);

                    if (Integer.toUnsignedLong(aProfile.mId) > aMaxProfileId) {
                        aMaxProfileId = Integer.toUnsignedLong(aProfile.mId);
                    }
                    if (Integer.toUnsignedLong(aProfile.mUseSeq) > aMaxUseSeq) {
                        aMaxUseSeq = Integer.toUnsignedLong(aProfile.mUseSeq);
                    }

                    mProfileMap.put(aProfile.mName, aProfile);
                }

                mNextProfileId = aMaxProfileId + 1;
                mNextProfileUseSeq = aMaxUseSeq + 1;
            } else {
                aWriter.writeUInt16(mProfileMap.size());

                for (PlayerInfo aProfile : mProfileMap.values()) {
                    aProfile.syncSummary(theSync);
                }
            }
        }
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:92-109. */
    public void load() {
        try {
            String aFileName = Common.getAppDataPath("userdata/users.dat");
            DataReader aReader = new DataReader();
            if (aReader.openFile(aFileName)) {
                DataSync aSync = new DataSync(aReader);
                syncState(aSync);
            }
        } catch (DataReaderException e) {
            clear();
        }
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:111-121. */
    public void save() {
        DataWriter aWriter = new DataWriter();
        aWriter.openMemory(0x20);
        DataSync aSync = new DataSync(aWriter);
        syncState(aSync);

        Common.mkDir(Common.getAppDataPath("userdata"));
        String aFileName = Common.getAppDataPath("userdata/users.dat");
        aWriter.writeToFile(aFileName);
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:123-127 (the {@code ProfileMap::iterator} overload). */
    protected void deleteProfileEntry(String theName) {
        PlayerInfo aProfile = mProfileMap.get(theName);
        if (aProfile == null) {
            return;
        }
        aProfile.deleteUserFiles();
        mProfileMap.remove(theName);
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:129-137. */
    public boolean deleteProfile(String theName) {
        if (!mProfileMap.containsKey(theName)) {
            return false;
        }

        deleteProfileEntry(theName);
        return true;
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:139-164. */
    public boolean renameProfile(String theOldName, String theNewName) {
        PlayerInfo anOldProfile = mProfileMap.get(theOldName);
        if (anOldProfile == null) {
            return false;
        }

        // rename in place when the name only differs in case; otherwise move the profile
        if (theOldName.equalsIgnoreCase(theNewName)) {
            anOldProfile.mName = theNewName;
        } else {
            // emplace fails when the new name already exists
            if (mProfileMap.containsKey(theNewName)) {
                return false;
            }
            mProfileMap.put(theNewName, anOldProfile);
            mProfileMap.remove(theOldName);
            anOldProfile.mName = theNewName;
        }
        return true;
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:166-177. */
    protected void deleteOldestProfile() {
        if (mProfileMap.isEmpty()) {
            return;
        }

        // find the profile with the smallest mUseSeq
        Map.Entry<String, PlayerInfo> anOldest = null;
        for (Map.Entry<String, PlayerInfo> anEntry : mProfileMap.entrySet()) {
            if (anOldest == null
                    || Integer.compareUnsigned(anEntry.getValue().mUseSeq, anOldest.getValue().mUseSeq) < 0) {
                anOldest = anEntry;
            }
        }
        deleteProfileEntry(anOldest.getKey());
    }

    /** Ported from src/Lawn/System/ProfileMgr.h:41 */
    protected void deleteOldProfiles() {
        while (mProfileMap.size() > 200) {
            deleteOldestProfile();
        }
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:179-191. */
    public PlayerInfo getProfile(String theName) {
        PlayerInfo aProfile = mProfileMap.get(theName);
        if (aProfile != null) {
            aProfile.loadDetails();
            aProfile.mUseSeq = (int) mNextProfileUseSeq++;
            return aProfile;
        }
        return null;
    }

    /** Ported from src/Lawn/System/ProfileMgr.cpp:193-204. */
    public PlayerInfo addProfile(String theName) {
        if (mProfileMap.containsKey(theName)) {
            return null;
        }

        PlayerInfo aProfile = new PlayerInfo();
        mProfileMap.put(theName, aProfile);
        aProfile.mName = theName;
        aProfile.mId = (int) mNextProfileId++;
        aProfile.mUseSeq = (int) mNextProfileUseSeq++;
        deleteOldProfiles();
        return aProfile;
    }

    /** Ported from src/Lawn/System/ProfileMgr.h:51. */
    public int getNumProfiles() {
        return mProfileMap.size();
    }

    /** Ported from src/Lawn/System/ProfileMgr.h:54 ({@code ProfileMap& GetProfileMap()}). */
    public TreeMap<String, PlayerInfo> getProfileMap() {
        return mProfileMap;
    }
}
