/*
 * Ported from src/Lawn/Plant.h:324-337.
 *
 * Upstream struct PlantDefinition; the public fields keep their upstream names. The table
 * gPlantDefs is index-aligned with SeedType (Plant.cpp:48-106, asserted by GetPlantDefinition,
 * Plant.cpp:4966-4972).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

public class PlantDefinition {
    public int mSeedType;
    /** Upstream {@code Image**}; null in every shipped row (the cel-sheet path is legacy). */
    public Image[] mPlantImage;
    public int mReanimationType;
    public int mPacketIndex;
    public int mSeedCost;
    public int mRefreshTime;
    public int mSubClass;
    public int mLaunchRate;
    /** String-file key stem, e.g. {@code "PEASHOOTER"} → {@code [PEASHOOTER]} / {@code [PEASHOOTER_TOOLTIP]}. */
    public String mPlantName;

    public PlantDefinition(
            int theSeedType,
            Image[] thePlantImage,
            int theReanimationType,
            int thePacketIndex,
            int theSeedCost,
            int theRefreshTime,
            int theSubClass,
            int theLaunchRate,
            String thePlantName) {
        mSeedType = theSeedType;
        mPlantImage = thePlantImage;
        mReanimationType = theReanimationType;
        mPacketIndex = thePacketIndex;
        mSeedCost = theSeedCost;
        mRefreshTime = theRefreshTime;
        mSubClass = theSubClass;
        mLaunchRate = theLaunchRate;
        mPlantName = thePlantName;
    }
}
