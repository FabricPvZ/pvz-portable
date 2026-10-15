package cn.enaium.fabricpvz.portable.lawn.content;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Registry of the plants a mod registered.
 *
 * <p>Upstream, everything the game knows about a plant lives in {@code Plant::gPlantDefs}
 * (Plant.cpp:48), a table whose index <em>is</em> the {@code SeedType} and whose rows are baked in at
 * compile time. This registry is the mod extension of that table: ids from
 * {@link SeedType#CUSTOM_SEED_FIRST} upwards map to a {@link CustomPlantType}, and the game consults
 * the registry from exactly the sites that read {@code gPlantDefs}:
 *
 * <ul>
 *   <li>{@code Plant::GetPlantDefinition} (Plant.cpp:4966) — via {@link #definition(int)};</li>
 *   <li>the {@code Plant::IsAquatic}/{@code IsFungus}/{@code IsNocturnal}/{@code IsFlying}/
 *       {@code IsUpgrade} predicates (Plant.cpp:5080-5134);</li>
 *   <li>{@code Plant::GetNameString}/{@code GetToolTip} (Plant.cpp:5039-5055);</li>
 *   <li>{@code Plant::PlantInitialize} (Plant.cpp:111) — via {@link #applyToPlant(Plant)};</li>
 *   <li>{@code Plant::Update} (Plant.cpp:2856) — via {@link #updatePlant(Plant)};</li>
 *   <li>{@code Plant::GetPlantRect}/{@code GetPlantAttackRect} (Plant.cpp:5136-5190).</li>
 * </ul>
 *
 * <p>Id slots are allocated in registration order and never reused after {@link #unregister(int)},
 * so a seed type handed to a mod keeps pointing at the same plant for the whole process lifetime.
 *
 * <p>Registration happens while the loader runs the mods' entry points, i.e. before the game main
 * class is even loaded; reads happen on the game thread. They are separated by a volatile snapshot
 * of the table, so a read never blocks and never observes a half-registered type.
 */
public final class PlantTypeRegistry {

    /** Number of seed types available to mods. */
    public static final int CAPACITY = SeedType.MAX_SEED_TYPES - SeedType.CUSTOM_SEED_FIRST;

    private static final Object LOCK = new Object();
    private static volatile CustomPlantType[] table = new CustomPlantType[CAPACITY];
    private static volatile int firstFree = SeedType.CUSTOM_SEED_FIRST;
    private static volatile int registered;

    private PlantTypeRegistry() {
    }

    /**
     * Registers a plant and assigns it a seed type.
     *
     * @param type the definition, filled by the caller and treated as immutable from here on
     * @return the assigned seed type, i.e. the value of {@link CustomPlantType#mSeedType}
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalArgumentException if the definition is incomplete
     * @throws IllegalStateException if no seed type is free any more
     */
    public static int register(CustomPlantType type) {
        if (type == null) {
            throw new NullPointerException("type");
        }

        if (type.mName.isEmpty() && type.mStringKeyStem.isEmpty()) {
            throw new IllegalArgumentException("A custom plant needs a name or a string-file key stem");
        }

        if (type.mReanimationType != ReanimationType.REANIM_NONE && type.mReanimationType < 0) {
            throw new IllegalArgumentException("Invalid reanimation type " + type.mReanimationType);
        }

        synchronized (LOCK) {
            if (firstFree >= SeedType.MAX_SEED_TYPES) {
                throw new IllegalStateException("No free seed type: all " + CAPACITY
                        + " custom seed types are registered");
            }

            int seedType = firstFree;
            type.mSeedType = seedType;
            type.mDefinition = new PlantDefinition(
                    seedType,
                    null,
                    type.mReanimationType,
                    type.mSeedType,
                    type.mSeedCost,
                    type.mRefreshTime,
                    type.mSubClass,
                    type.mLaunchRate,
                    type.mStringKeyStem.isEmpty() ? ("CUSTOM_PLANT_" + seedType) : type.mStringKeyStem);

            CustomPlantType[] updated = table.clone();
            updated[seedType - SeedType.CUSTOM_SEED_FIRST] = type;
            table = updated;
            firstFree = seedType + 1;
            registered++;
            return seedType;
        }
    }

    /**
     * Unregisters a plant, for example when a mod is disabled at runtime.
     *
     * <p>Already placed plants of that type keep working through the {@link CustomPlantType} instance
     * they were created from; only future lookups stop resolving the id.
     *
     * @param seedType the seed type to drop
     * @return whether a plant was registered under that seed type
     */
    public static boolean unregister(int seedType) {
        synchronized (LOCK) {
            int index = indexOf(seedType);
            if (index < 0 || table[index] == null) {
                return false;
            }

            CustomPlantType[] updated = table.clone();
            updated[index] = null;
            table = updated;
            registered--;
            return true;
        }
    }

    /**
     * @param seedType a seed type
     * @return the registered plant, or {@code null} when the id is an upstream seed or nobody
     *     registered it
     */
    public static CustomPlantType get(int seedType) {
        int index = indexOf(seedType);
        return index < 0 ? null : table[index];
    }

    /**
     * @param seedType a seed type
     * @return whether {@code seedType} is a mod seed type, whether or not something is registered
     *     under it
     */
    public static boolean isCustom(int seedType) {
        return seedType >= SeedType.CUSTOM_SEED_FIRST && seedType < SeedType.MAX_SEED_TYPES;
    }

    /**
     * @return the registered plants, in registration order
     */
    public static List<CustomPlantType> all() {
        List<CustomPlantType> types = new ArrayList<>(registered);
        for (CustomPlantType type : table) {
            if (type != null) {
                types.add(type);
            }
        }
        return List.copyOf(types);
    }

    /**
     * @return how many plants are currently registered
     */
    public static int count() {
        return registered;
    }

    /**
     * Resolves the table row of a seed type; this is what {@code Plant::GetPlantDefinition} returns
     * for a mod plant.
     *
     * @param seedType a mod seed type
     * @return the definition row
     * @throws IllegalArgumentException if nothing is registered under {@code seedType}
     */
    public static PlantDefinition definition(int seedType) {
        CustomPlantType type = get(seedType);
        if (type == null) {
            throw new IllegalArgumentException("No custom plant is registered for seed type " + seedType);
        }
        return type.mDefinition;
    }

    /**
     * @param seedType a seed type
     * @return the registered plant's display name, or {@code null} when the id is not registered
     */
    public static String displayName(int seedType) {
        CustomPlantType type = get(seedType);
        return type == null || type.mName.isEmpty() ? null : type.mName;
    }

    /**
     * @param seedType a seed type
     * @return the registered plant's tooltip, or {@code null} when the id is not registered or the
     *     definition has none
     */
    public static String tooltip(int seedType) {
        CustomPlantType type = get(seedType);
        if (type == null) {
            return null;
        }
        return type.mTooltip.isEmpty() ? displayName(seedType) : type.mTooltip;
    }

    /**
     * @param seedType a seed type
     * @return the registered plant's almanac text, or {@code null} when the id is not registered
     */
    public static String almanacDescription(int seedType) {
        CustomPlantType type = get(seedType);
        if (type == null) {
            return null;
        }
        if (!type.mAlmanacDescription.isEmpty()) {
            return type.mAlmanacDescription;
        }
        return tooltip(seedType);
    }

    /**
     * Applies the registered definition to a plant that has just been initialised: the health
     * override, the render scale of the definition and the behaviour's {@code onPlanted} callback.
     *
     * <p>Called by {@code Plant::PlantInitialize} for mod seed types only, so an unmodded game never
     * enters this method.
     *
     * @param plant the plant that was just initialised
     */
    public static void applyToPlant(Plant plant) {
        CustomPlantType type = get(plant.mSeedType);
        if (type == null) {
            return;
        }

        plant.mPlantHealth = type.mPlantHealth;
        plant.mPlantMaxHealth = type.mPlantHealth;
        if (type.mBehaviour != null) {
            type.mBehaviour.onPlanted(plant);
        }
    }

    /**
     * Fires the behaviour tick of a plant. Called by {@code Plant::Update} for mod seed types only.
     *
     * @param plant the plant being updated
     */
    public static void updatePlant(Plant plant) {
        CustomPlantType type = get(plant.mSeedType);
        if (type != null && type.mBehaviour != null) {
            type.mBehaviour.onUpdate(plant);
        }
    }

    /**
     * @param plant a plant
     * @return the collision rectangle the behaviour wants, or {@code null} to keep the upstream one
     */
    public static Rect plantRect(Plant plant) {
        CustomPlantType type = get(plant.mSeedType);
        return type == null || type.mBehaviour == null ? null : type.mBehaviour.plantRect(plant);
    }

    /**
     * @param plant a plant
     * @param thePlantWeapon {@code PlantWeapon} in use
     * @return the attack rectangle the behaviour wants, or {@code null} to keep the upstream one
     */
    public static Rect plantAttackRect(Plant plant, int thePlantWeapon) {
        CustomPlantType type = get(plant.mSeedType);
        return type == null || type.mBehaviour == null ? null : type.mBehaviour.plantAttackRect(plant, thePlantWeapon);
    }

    /**
     * Clears the registry; used by tests and by a loader that reloads mods in-process.
     */
    public static void reset() {
        synchronized (LOCK) {
            table = new CustomPlantType[CAPACITY];
            firstFree = SeedType.CUSTOM_SEED_FIRST;
            registered = 0;
        }
    }

    private static int indexOf(int seedType) {
        if (seedType < SeedType.CUSTOM_SEED_FIRST || seedType >= SeedType.MAX_SEED_TYPES) {
            return -1;
        }
        return seedType - SeedType.CUSTOM_SEED_FIRST;
    }
}
