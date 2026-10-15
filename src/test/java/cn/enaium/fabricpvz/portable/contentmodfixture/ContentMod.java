package cn.enaium.fabricpvz.portable.contentmodfixture;

import cn.enaium.fabricpvz.api.plant.PlantType;
import cn.enaium.fabricpvz.api.zombie.ZombieType;
import cn.enaium.fabricpvz.portable.lawn.HelmType;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.PlantingReason;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import net.fabricmc.api.ModInitializer;

/**
 * Throwaway mod used by {@link FabricLoaderEndToEndTest}: a real
 * {@link net.fabricmc.api.ModInitializer}, loaded by Fabric Loader from a real {@code mods/}
 * directory, that registers one plant and one zombie through the public API only.
 *
 * <p>It is compiled as part of the game's test sources so the registrations are checked by the
 * compiler, and packaged into a jar by the test itself — the loader never sees it on the class path,
 * exactly like a mod a player dropped into {@code mods/}.
 *
 * <p>The plant pins a planting rule (only the middle lane) and a per-type tick/placed callback; the
 * zombie uses a shipped reanimation with a bucket helm and takes part in wave composition. Both reuse
 * upstream reanimations, so the test needs no new art — it only needs the registration path to work.
 */
public final class ContentMod implements ModInitializer {

    /** Mod id the test packs into the generated {@code fabric.mod.json}. */
    public static final String MOD_ID = "pvz-content-fixture-mod";

    /** Lane the plant's planting rule allows. */
    public static final int ALLOWED_ROW = 2;

    public static PlantType peaPlus;
    public static ZombieType bucketRunner;

    public static int plantPlacedCallbacks;
    public static int plantTickCallbacks;
    public static int zombieSpawnedCallbacks;
    public static int zombieTickCallbacks;
    public static int plantingRuleCalls;

    @Override
    public void onInitialize() {
        peaPlus = PlantType.builder("fixture_peashooter_plus")
                .name("Fixture Pea Shooter+")
                .tooltip("A test plant that fires two peas.")
                .almanacDescription("Registered by the content fixture mod.")
                .cost(125)
                .refreshTime(750)
                .reanimation(ReanimationType.REANIM_REPEATER)
                .subClass(PlantSubClass.SUBCLASS_SHOOTER)
                .launchRate(150)
                .health(400)
                .plantingRule((board, gridX, gridY, reason) -> {
                    plantingRuleCalls++;
                    return gridY == ALLOWED_ROW ? reason : PlantingReason.PLANTING_NOT_HERE;
                })
                .onPlanted(plant -> plantPlacedCallbacks++)
                .onTick(plant -> plantTickCallbacks++)
                .register();

        bucketRunner = ZombieType.builder("fixture_bucket_runner")
                .name("Fixture Bucket Runner")
                .almanacDescription("Registered by the content fixture mod.")
                .bodyReanimation(ReanimationType.REANIM_ZOMBIE)
                .health(400)
                .helm(HelmType.HELMTYPE_PAIL, 1100)
                .wave(2, 1, 1, 2000)
                .canGoInPool(true)
                .onSpawned(zombie -> zombieSpawnedCallbacks++)
                .onTick(zombie -> zombieTickCallbacks++)
                .register();
    }

    /** @return the spawn wave constant the fixture uses for zombies that are not part of a wave. */
    public static int uiWaveConstant() {
        return Zombie.ZOMBIE_WAVE_UI;
    }
}
