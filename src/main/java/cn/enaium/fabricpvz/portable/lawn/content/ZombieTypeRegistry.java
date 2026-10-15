package cn.enaium.fabricpvz.portable.lawn.content;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationID;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Registry of the zombies a mod registered.
 *
 * <p>The zombie counterpart of {@link PlantTypeRegistry}: upstream, everything the game knows about a
 * zombie lives in {@code Zombie::gZombieDefs} (Zombie.cpp:80) plus a very long {@code switch} in
 * {@code Zombie::ZombieInitialize} (Zombie.cpp:143). This registry supplies both halves for mod
 * content — the definition row and the per-type setup — and the game consults it from the sites that
 * read {@code gZombieDefs}:
 *
 * <ul>
 *   <li>{@code Zombie::GetZombieDefinition} — via {@link #definition(int)};</li>
 *   <li>{@code Zombie::ZombieInitialize} — via {@link #applyToZombie(Zombie)};</li>
 *   <li>the wave composer, {@code BoardWaves::PickZombieType} (Board.cpp:2399) and
 *       {@code BoardWaves::CanZombieSpawnOnLevel}, through {@link #isWaveEnabled(int)} and the
 *       definition row;</li>
 *   <li>{@code Zombie::Update} — via {@link #updateZombie(Zombie)}.</li>
 * </ul>
 *
 * <p>Id slots are allocated from {@link ZombieType#CUSTOM_ZOMBIE_FIRST} upwards in registration
 * order. Registration happens before the game main class is loaded, reads happen on the game thread,
 * and the two are separated by a volatile snapshot.
 */
public final class ZombieTypeRegistry {

    /** Number of zombie types available to mods. */
    public static final int CAPACITY = ZombieType.MAX_ZOMBIE_TYPES - ZombieType.CUSTOM_ZOMBIE_FIRST;

    private static final Object LOCK = new Object();
    private static volatile CustomZombieType[] table = new CustomZombieType[CAPACITY];
    private static volatile int firstFree = ZombieType.CUSTOM_ZOMBIE_FIRST;
    private static volatile int registered;

    private ZombieTypeRegistry() {
    }

    /**
     * Registers a zombie and assigns it a zombie type.
     *
     * @param type the definition, filled by the caller and treated as immutable from here on
     * @return the assigned zombie type, i.e. the value of {@link CustomZombieType#mZombieType}
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalArgumentException if the definition is incomplete
     * @throws IllegalStateException if no zombie type is free any more
     */
    public static int register(CustomZombieType type) {
        if (type == null) {
            throw new NullPointerException("type");
        }

        if (type.mName.isEmpty() && type.mStringKeyStem.isEmpty()) {
            throw new IllegalArgumentException("A custom zombie needs a name or a string-file key stem");
        }

        if (type.mBodyHealth <= 0) {
            throw new IllegalArgumentException("A custom zombie needs a positive body health, got "
                    + type.mBodyHealth);
        }

        synchronized (LOCK) {
            if (firstFree >= ZombieType.MAX_ZOMBIE_TYPES) {
                throw new IllegalStateException("No free zombie type: all " + CAPACITY
                        + " custom zombie types are registered");
            }

            int zombieType = firstFree;
            type.mZombieType = zombieType;
            type.mDefinition = new ZombieDefinition(
                    zombieType,
                    type.mBodyReanimationType,
                    type.mZombieValue,
                    type.mStartingLevel,
                    type.mFirstAllowedWave,
                    type.mPickWeight,
                    type.mStringKeyStem.isEmpty() ? ("CUSTOM_ZOMBIE_" + zombieType) : type.mStringKeyStem);

            CustomZombieType[] updated = table.clone();
            updated[zombieType - ZombieType.CUSTOM_ZOMBIE_FIRST] = type;
            table = updated;
            firstFree = zombieType + 1;
            registered++;
            return zombieType;
        }
    }

    /**
     * Unregisters a zombie, for example when a mod is disabled at runtime.
     *
     * @param zombieType the zombie type to drop
     * @return whether a zombie was registered under that type
     */
    public static boolean unregister(int zombieType) {
        synchronized (LOCK) {
            int index = indexOf(zombieType);
            if (index < 0 || table[index] == null) {
                return false;
            }

            CustomZombieType[] updated = table.clone();
            updated[index] = null;
            table = updated;
            registered--;
            return true;
        }
    }

    /**
     * @param zombieType a zombie type
     * @return the registered zombie, or {@code null} when the id is an upstream type or nobody
     *     registered it
     */
    public static CustomZombieType get(int zombieType) {
        int index = indexOf(zombieType);
        return index < 0 ? null : table[index];
    }

    /**
     * @param zombieType a zombie type
     * @return whether {@code zombieType} is a mod zombie type, whether or not something is registered
     *     under it
     */
    public static boolean isCustom(int zombieType) {
        return zombieType >= ZombieType.CUSTOM_ZOMBIE_FIRST && zombieType < ZombieType.MAX_ZOMBIE_TYPES;
    }

    /**
     * @return the registered zombies, in registration order
     */
    public static List<CustomZombieType> all() {
        List<CustomZombieType> types = new ArrayList<>(registered);
        for (CustomZombieType type : table) {
            if (type != null) {
                types.add(type);
            }
        }
        return List.copyOf(types);
    }

    /**
     * @return how many zombies are currently registered
     */
    public static int count() {
        return registered;
    }

    /**
     * Resolves the table row of a zombie type; this is what {@code Zombie::GetZombieDefinition}
     * returns for a mod zombie.
     *
     * @param zombieType a mod zombie type
     * @return the definition row
     * @throws IllegalArgumentException if nothing is registered under {@code zombieType}
     */
    public static ZombieDefinition definition(int zombieType) {
        CustomZombieType type = get(zombieType);
        if (type == null) {
            throw new IllegalArgumentException("No custom zombie is registered for zombie type " + zombieType);
        }
        return type.mDefinition;
    }

    /**
     * Whether a mod zombie takes part in wave composition. Called by
     * {@code BoardWaves::CanZombieSpawnOnLevel} (Board.cpp:2343).
     *
     * @param zombieType a zombie type
     * @return {@code false} for upstream types (they use {@code Challenge::gZombieAllowedLevels})
     */
    public static boolean isWaveEnabled(int zombieType) {
        CustomZombieType type = get(zombieType);
        return type != null && type.mWaveEnabled;
    }

    /**
     * @param zombieType a zombie type
     * @return the registered zombie's display name, or {@code null} when the id is not registered
     */
    public static String displayName(int zombieType) {
        CustomZombieType type = get(zombieType);
        return type == null || type.mName.isEmpty() ? null : type.mName;
    }

    /**
     * @param zombieType a zombie type
     * @return the registered zombie's almanac text, or {@code null} when the id is not registered
     */
    public static String almanacDescription(int zombieType) {
        CustomZombieType type = get(zombieType);
        if (type == null) {
            return null;
        }
        return type.mAlmanacDescription.isEmpty() ? type.mName : type.mAlmanacDescription;
    }

    /**
     * Applies the registered definition to a zombie that just finished the upstream per-type setup in
     * {@code Zombie::ZombieInitialize}: the plain reanimation layers, the optional attached head, the
     * health/armour numbers and the collision rectangles.
     *
     * <p>Called from {@code Zombie::ZombieInitialize} for mod zombie types only, so an unmodded game
     * never enters this method.
     *
     * @param zombie the zombie being initialised
     */
    public static void applyToZombie(Zombie zombie) {
        CustomZombieType type = get(zombie.mZombieType);
        if (type == null) {
            return;
        }

        zombie.loadPlainZombieReanim();
        attachHead(zombie, type.mHead);

        zombie.mBodyHealth = type.mBodyHealth;
        zombie.mHelmType = type.mHelmType;
        zombie.mHelmHealth = type.mHelmHealth;
        zombie.mShieldType = type.mShieldType;
        zombie.mShieldHealth = type.mShieldHealth;
        zombie.mFlyingHealth = type.mFlyingHealth;
        zombie.mScaleZombie = type.mScaleZombie;

        if (type.mZombieRect != null) {
            zombie.mZombieRect = copy(type.mZombieRect);
        }
        if (type.mZombieAttackRect != null) {
            zombie.mZombieAttackRect = copy(type.mZombieAttackRect);
        }
        if (type.mShieldHealth > 0) {
            zombie.attachShield();
        }

        if (type.mBehaviour != null) {
            type.mBehaviour.onSpawned(zombie);
        }
    }

    /**
     * Fires the behaviour tick of a zombie. Called by {@code Zombie::Update} for mod zombie types
     * only.
     *
     * @param zombie the zombie being updated
     */
    public static void updateZombie(Zombie zombie) {
        CustomZombieType type = get(zombie.mZombieType);
        if (type != null && type.mBehaviour != null) {
            type.mBehaviour.onUpdate(zombie);
        }
    }

    /**
     * Clears the registry; used by tests and by a loader that reloads mods in-process.
     */
    public static void reset() {
        synchronized (LOCK) {
            table = new CustomZombieType[CAPACITY];
            firstFree = ZombieType.CUSTOM_ZOMBIE_FIRST;
            registered = 0;
        }
    }

    /**
     * Ports the "another plant as a head" setup of the peashooter-head zombies
     * (Zombie.cpp:720-742) for any mod zombie that asked for one.
     */
    private static void attachHead(Zombie zombie, CustomZombieType.HeadAttachment head) {
        if (head == null || head.mReanimationType == ReanimationType.REANIM_NONE) {
            return;
        }
        if (zombie.mBodyReanimID == ReanimationID.REANIMATIONID_NULL || zombie.mApp == null) {
            return;
        }

        Reanimation aBodyReanim = zombie.mApp.reanimationGet(zombie.mBodyReanimID);
        if (aBodyReanim == null || aBodyReanim.mTrackInstances == null) {
            return;
        }

        if (!head.mHiddenLayer.isEmpty()) {
            zombie.reanimShowPrefix(head.mHiddenLayer, Reanimation.RENDER_GROUP_HIDDEN);
        }

        if (!aBodyReanim.trackExists(head.mBodyTrack)) {
            return;
        }

        if (!head.mHiddenTrack.isEmpty() && aBodyReanim.trackExists(head.mHiddenTrack)) {
            aBodyReanim.getTrackInstanceByName(head.mHiddenTrack).mImageOverride =
                    Resources.getImageById(ImageID.IMAGE_BLANK);
        }

        Reanimation aHeadReanim = zombie.mApp.addReanimation(0.0f, 0.0f, 0, head.mReanimationType);
        aHeadReanim.playReanim(head.mHeadTrack, ReanimLoopType.REANIM_LOOP, 0, 15.0f);
        zombie.mSpecialHeadReanimID = zombie.mApp.reanimationGetID(aHeadReanim);

        AttachEffect aAttachEffect = Attachment.attachReanim(
                aBodyReanim.getTrackInstanceByName(head.mBodyTrack).mAttachmentID, aHeadReanim, 0.0f, 0.0f);
        aBodyReanim.mFrameBasePose = 0;
        PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, head.mOffsetX, head.mOffsetY,
                head.mRotate, head.mScale, head.mScale);
    }

    private static Rect copy(Rect theRect) {
        return new Rect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }

    private static int indexOf(int zombieType) {
        if (zombieType < ZombieType.CUSTOM_ZOMBIE_FIRST || zombieType >= ZombieType.MAX_ZOMBIE_TYPES) {
            return -1;
        }
        return zombieType - ZombieType.CUSTOM_ZOMBIE_FIRST;
    }
}
