/*
 * Tests the ZenGarden disposal wired into LawnApp.dispose() (Z5 hook requested by the ZenGarden
 * port): the garden must release its tracked delay-load resource groups before the resource manager
 * is torn down, matching the upstream ~LawnApp member-destruction ordering (LawnApp.cpp:212-229).
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless check for {@code LawnApp.dispose()}'s ZenGarden teardown. */
class LawnAppDisposeTest {

    @Test
    void disposeDisposesZenGardenBeforeResourceTeardown() {
        LawnApp app = new LawnApp();
        HeadlessTestApp.initialize(app);

        ZenGarden aZen = app.mZenGarden;
        assertNotNull(aZen); // HeadlessTestApp models the loading thread creating it (LawnApp.cpp:1684)
        aZen.mLoadedResourceNames.add("DelayLoad_MushroomGarden");

        app.dispose();

        assertNull(app.mZenGarden); // LawnApp.dispose() released it before mResourceManager teardown
        assertFalse(aZen.mLoadedResourceNames.contains("DelayLoad_MushroomGarden")); // ZenGarden.dispose()

        // Restore the process-wide definition tables the teardown above freed, so later test classes
        // in the same JVM see a fully initialised app.
        HeadlessTestApp.initialize(new LawnApp());
    }
}
