package cn.enaium.fabricpvz.portable.pvzp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates the real Plants vs. Zombies assets for integration tests.
 *
 * <p>Assets may be supplied with {@code -Dpvz.test.resources=<dir>}; otherwise the standard
 * {@link ResourceLocator} search runs (working directory, executable directory, local Steam
 * installation). Tests that need real assets must be gated with
 * {@code Assumptions.assumeTrue(...)} so the suite stays green on machines without the game.
 */
public final class TestAssets {

    private TestAssets() {
    }

    public static Optional<Path> resourceRoot() {
        String override = System.getProperty("pvz.test.resources", "");
        if (!override.isBlank()) {
            Path path = Path.of(override);
            return ResourceLocator.isValidResourceRoot(path) ? Optional.of(path) : Optional.empty();
        }
        return Optional.ofNullable(ResourceLocator.discover(new String[0]).resourceRoot());
    }

    /** Resolves a resource relative to the real resource root, case-insensitively. */
    public static Optional<Path> resolve(String relative) throws IOException {
        Optional<Path> root = resourceRoot();
        if (root.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cn.enaium.fabricpvz.portable.sexy.pak.PakSource.resolveCaseInsensitive(root.get().resolve(relative)));
    }
}
