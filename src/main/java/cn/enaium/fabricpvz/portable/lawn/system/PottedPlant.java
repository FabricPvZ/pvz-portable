/*
 * Ported from src/Lawn/System/PlayerInfo.h:36-67 and src/Lawn/System/PlayerInfo.cpp:314-329.
 *
 * Upstream value type PottedPlant; field names match upstream, the nested FacingDirection
 * enum becomes an int-constant holder.
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import cn.enaium.fabricpvz.portable.lawn.DrawVariation;
import cn.enaium.fabricpvz.portable.lawn.GardenType;
import cn.enaium.fabricpvz.portable.lawn.PottedPlantAge;
import cn.enaium.fabricpvz.portable.lawn.PottedPlantNeed;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.DataSync;

public class PottedPlant {

    /** Ported from src/Lawn/System/PlayerInfo.h:41-45 (enum FacingDirection). */
    public static final class FacingDirection {
        public static final int FACING_RIGHT = 0;
        public static final int FACING_LEFT = 1;

        private FacingDirection() {
        }
    }

    public int mSeedType;
    public int mWhichZenGarden;
    public int mX;
    public int mY;
    public int mFacing;

    /** Ported from PlayerInfo.h:43 ({@code uint32_t mPadding1}) - explicit alignment, unused, kept in the file. */
    public int mPadding1;
    public long mLastWateredTime;
    public int mDrawVariation;
    public int mPlantAge;
    public int mTimesFed;
    public int mFeedingsPerGrow;
    public int mPlantNeed;

    /** Ported from PlayerInfo.h:56 ({@code uint32_t mPadding2}) - explicit alignment, unused, kept in the file. */
    public int mPadding2;
    public long mLastNeedFulfilledTime;
    public long mLastFertilizedTime;
    public long mLastChocolateTime;
    public long[] mFutureAttribute = new long[1];

    public PottedPlant() {
    }

    /** Ported from src/Lawn/System/PlayerInfo.cpp:314-329 (PottedPlant::InitializePottedPlant). */
    public void initializePottedPlant(int theSeedType) {
        mSeedType = 0;
        mWhichZenGarden = 0;
        mX = 0;
        mY = 0;
        mFacing = 0;
        mLastWateredTime = 0;
        mDrawVariation = 0;
        mPlantAge = 0;
        mTimesFed = 0;
        mFeedingsPerGrow = 0;
        mPlantNeed = 0;
        mLastNeedFulfilledTime = 0;
        mLastFertilizedTime = 0;
        mLastChocolateTime = 0;

        mSeedType = theSeedType;
        mDrawVariation = DrawVariation.VARIATION_NORMAL;
        mLastWateredTime = 0;
        mFacing = PvzpCommon.randRangeInt(FacingDirection.FACING_RIGHT, FacingDirection.FACING_LEFT);
        mPlantAge = PottedPlantAge.PLANTAGE_SPROUT;
        mTimesFed = 0;
        mWhichZenGarden = GardenType.GARDEN_MAIN;
        mFeedingsPerGrow = PvzpCommon.randRangeInt(3, 5);
        mPlantNeed = PottedPlantNeed.PLANTNEED_NONE;
        mLastNeedFulfilledTime = 0;
        mLastFertilizedTime = 0;
        mLastChocolateTime = 0;
    }

    /**
     * Ported from src/Lawn/System/PlayerInfo.cpp:36-58 ({@code PottedPlantFromLE}) and
     * PlayerInfo.cpp:124-131.
     *
     * <p>Upstream copies the native {@code sizeof(PottedPlant)} bytes - including the two explicit
     * alignment padding dwords - and byte-swaps them on big-endian hosts ({@code PottedPlantToLE} on
     * write, {@code PottedPlantFromLE} after the read). Java has no raw struct, so the same 88 bytes
     * (0x58) are written/read field by field in little-endian order; the padding dwords are part of
     * the `user{id}.dat` record (../verification/research/03-save.md §4.2).
     */
    public void sync(DataSync theSync) {
        mSeedType = theSync.syncUInt32(mSeedType);
        mWhichZenGarden = theSync.syncUInt32(mWhichZenGarden);
        mX = theSync.syncUInt32(mX);
        mY = theSync.syncUInt32(mY);
        mFacing = theSync.syncUInt32(mFacing);
        mPadding1 = theSync.syncUInt32(mPadding1);
        mLastWateredTime = theSync.syncUInt64(mLastWateredTime);
        mDrawVariation = theSync.syncUInt32(mDrawVariation);
        mPlantAge = theSync.syncUInt32(mPlantAge);
        mTimesFed = theSync.syncUInt32(mTimesFed);
        mFeedingsPerGrow = theSync.syncUInt32(mFeedingsPerGrow);
        mPlantNeed = theSync.syncUInt32(mPlantNeed);
        mPadding2 = theSync.syncUInt32(mPadding2);
        mLastNeedFulfilledTime = theSync.syncUInt64(mLastNeedFulfilledTime);
        mLastFertilizedTime = theSync.syncUInt64(mLastFertilizedTime);
        mLastChocolateTime = theSync.syncUInt64(mLastChocolateTime);
        mFutureAttribute[0] = theSync.syncUInt64(mFutureAttribute[0]);
    }

    /**
     * Ported from src/Lawn/System/SaveGame.cpp:606-624 ({@code SyncPottedPlantPortable}); field order
     * is upstream-exact (enum/int fields ride on the i32 codec, the timestamps on the i64 codec) and
     * the two padding dwords are omitted, so this form is 80 bytes on the wire (../verification/research/03-save.md
     * §4.2). Used by the `.v4` board format ({@code Coin.mPottedPlantSpec}, zen-garden grid items).
     */
    public void syncPortable(DataSync theSync) {
        mSeedType = theSync.syncUInt32(mSeedType);
        mWhichZenGarden = theSync.syncUInt32(mWhichZenGarden);
        mX = theSync.syncUInt32(mX);
        mY = theSync.syncUInt32(mY);
        mFacing = theSync.syncUInt32(mFacing);
        mLastWateredTime = theSync.syncUInt64(mLastWateredTime);
        mDrawVariation = theSync.syncUInt32(mDrawVariation);
        mPlantAge = theSync.syncUInt32(mPlantAge);
        mTimesFed = theSync.syncUInt32(mTimesFed);
        mFeedingsPerGrow = theSync.syncUInt32(mFeedingsPerGrow);
        mPlantNeed = theSync.syncUInt32(mPlantNeed);
        mLastNeedFulfilledTime = theSync.syncUInt64(mLastNeedFulfilledTime);
        mLastFertilizedTime = theSync.syncUInt64(mLastFertilizedTime);
        mLastChocolateTime = theSync.syncUInt64(mLastChocolateTime);
        mFutureAttribute[0] = theSync.syncUInt64(mFutureAttribute[0]);
    }
}
