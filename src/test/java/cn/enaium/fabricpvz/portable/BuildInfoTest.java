package cn.enaium.fabricpvz.portable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The build identity the game reports: it is read from the resource the {@code buildInfo} task writes,
 * and the failure this guards against is the one the first version of the port had - a build number of
 * zero, an empty commit date and an empty version, all silently, because nothing ever injected them.
 */
class BuildInfoTest {

    @Test
    void statesTheBuildItWasBuiltFrom() {
        assertFalse(BuildInfo.version().isBlank(), "the version comes from the build");
        assertFalse(BuildInfo.commitDate().isBlank(), "the commit date comes from the build");
        assertTrue(BuildInfo.buildNumber() > 0, "the build number is the commit count the build saw");
    }
}
