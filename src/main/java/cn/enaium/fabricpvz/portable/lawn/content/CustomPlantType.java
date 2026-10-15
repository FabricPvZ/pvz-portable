package cn.enaium.fabricpvz.portable.lawn.content;

import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;

/**
 * Definition of a plant registered by a mod.
 *
 * <p>This is deliberately a plain data holder with public fields, like
 * {@link PlantDefinition} and {@link cn.enaium.fabricpvz.portable.lawn.ZombieDefinition}: the mod
 * facing builders live in {@code api-plant}, which fills an instance and hands it to
 * {@link PlantTypeRegistry#register(CustomPlantType)}.
 *
 * <p>The game reads these fields from exactly the sites where it reads the upstream per-seed tables,
 * so a registered plant behaves like a built-in one:
 *
 * <ul>
 *   <li>{@link #mSeedCost}, {@link #mRefreshTime}, {@link #mSubClass} and {@link #mLaunchRate} are
 *       copied into a {@link PlantDefinition} row returned by
 *       {@code Plant::GetPlantDefinition} (Plant.cpp:4966);</li>
 *   <li>{@link #mReanimationType} makes the plant draw like any reanimation-backed plant, including
 *       its seed packet art and its almanac picture
 *       ({@code ReanimatorCache::DrawCachedPlant});</li>
 *   <li>{@link #mPacketImageId} overrides that packet art with a fixed image/cel (for plants with no
 *       reanimation of their own);</li>
 *   <li>{@link #mName}, {@link #mTooltip} and {@link #mAlmanacDescription} replace the
 *       {@code LawnStrings} lookups, so a mod does not have to patch {@code main.pak};</li>
 *   <li>the {@code mAquatic}/{@code mFungus}/... flags feed
 *       {@code Plant::IsAquatic}/{@code IsFungus}/{@code IsNocturnal}/{@code IsFlying}/{@code IsUpgrade}
 *       (Plant.cpp:5080-5134);</li>
 *   <li>{@link #mPlantHealth} replaces the {@code 300} default of {@code Plant::PlantInitialize}
 *       (Plant.cpp:120 area);</li>
 *   <li>{@link #mBehaviour} supplies the planting/collision/update hooks.</li>
 * </ul>
 *
 * <p>Fields are read when the plant is created and whenever the game consults the definition tables,
 * so a registered type must be treated as immutable.
 */
public class CustomPlantType {

    /** Value of {@link #mPacketImageId} meaning "derive the packet art from the plant reanimation". */
    public static final int PACKET_IMAGE_FROM_REANIM = 0;

    /**
     * Seed type this plant was registered under. Assigned by
     * {@link PlantTypeRegistry#register(CustomPlantType)} from {@link SeedType#CUSTOM_SEED_FIRST}
     * upwards; never set it by hand.
     */
    public int mSeedType = SeedType.SEED_NONE;

    /** Display name shown on the seed packet and in the almanac. */
    public String mName = "";

    /** Seed packet tooltip; falls back to {@link #mName} when empty. */
    public String mTooltip = "";

    /** Almanac body text; falls back to {@link #mTooltip} when empty. */
    public String mAlmanacDescription = "";

    /**
     * String-file key stem, e.g. {@code "PEASHOOTER"}. Only used when {@link #mName} is empty, in
     * which case the name is read from the game's string file exactly like an upstream plant.
     */
    public String mStringKeyStem = "";

    /** Sun cost of one seed packet. */
    public int mSeedCost;

    /** Recharge time in centiseconds, see {@code PlantDefinition#mRefreshTime}. */
    public int mRefreshTime = 750;

    /** {@code PlantSubClass} value driving which upstream ability set the plant runs. */
    public int mSubClass = PlantSubClass.SUBCLASS_NORMAL;

    /** Tick interval between the plant's launches, see {@code PlantDefinition#mLaunchRate}. */
    public int mLaunchRate;

    /** {@code ReanimationType} of the plant; {@link ReanimationType#REANIM_NONE} for a static one. */
    public int mReanimationType = ReanimationType.REANIM_NONE;

    /** Fixed packet art image id; {@link #PACKET_IMAGE_FROM_REANIM} keeps the reanimation-derived art. */
    public int mPacketImageId = PACKET_IMAGE_FROM_REANIM;

    /** Cel index used together with {@link #mPacketImageId}. */
    public int mPacketCelIndex;

    /** Maximum health of the plant, used instead of the upstream {@code 300} default. */
    public int mPlantHealth = 300;

    /** See {@code Plant::IsAquatic}. */
    public boolean mAquatic;
    /** See {@code Plant::IsFungus}. */
    public boolean mFungus;
    /** See {@code Plant::IsNocturnal}. */
    public boolean mNocturnal;
    /** See {@code Plant::IsFlying}. */
    public boolean mFlying;
    /** See {@code Plant::IsUpgrade} (selects the upgrade seed-packet background). */
    public boolean mUpgrade;

    /** Whether the seed may appear in the seed chooser and in the almanac plant index. */
    public boolean mChooserVisible = true;

    /** Planting, collision and update hooks; {@code null} means "no behaviour". */
    public PlantBehaviour mBehaviour;

    /** The index-aligned table row handed out by {@code Plant::GetPlantDefinition}. */
    public PlantDefinition mDefinition;

    /** The reanimation type the plant draws with, {@link ReanimationType#REANIM_NONE} when static. */
    public int reanimationType() {
        return mReanimationType;
    }
}
