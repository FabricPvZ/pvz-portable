package cn.enaium.fabricpvz.portable.contentmodfixture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import cn.enaium.fabricpvz.api.resource.ResourceOverrides;
import net.fabricmc.api.ModInitializer;

/**
 * Throwaway mod used by {@link FabricLoaderEndToEndTest} to prove that a mod's resource overrides
 * really are what the engine reads.
 *
 * <p>It is a real {@link ModInitializer}, loaded by Fabric Loader from a real {@code mods/}
 * directory, and it registers one override of each kind {@link ResourceOverrides} offers:
 *
 * <ul>
 *   <li>{@link ResourceOverrides#addBytes} for {@link #BYTES_RESOURCE}, from a constant this class
 *       carries (nothing has to be unpacked to disk for it);</li>
 *   <li>{@link ResourceOverrides#addFile} for {@link #FILE_RESOURCE}, from the file named by
 *       {@value #FILE_OVERRIDE_PROPERTY} (the test's temporary directory, so the fixture stays
 *       self-contained);</li>
 *   <li>{@link ResourceOverrides#addRoot} for {@link #ROOT_RESOURCE}, from the directory named by
 *       {@value #ROOT_OVERRIDE_PROPERTY}, which holds {@code images/Almanac.png} of its own.</li>
 * </ul>
 *
 * <p>{@link #CONTROL_RESOURCE} is deliberately <em>not</em> registered: the engine must still serve
 * it out of the retail {@code main.pak}, which is what makes the three reads above show an override
 * rather than a reader that answers with the file system for everything.
 *
 * <p>All six resources exist in the retail {@code main.pak} (see the test), so each override shadows
 * a pak member the game would otherwise read, and the test can compare the engine's answer with the
 * pak member it reads itself. The byte generators are public because the test writes the loose files
 * from the very same values, i.e. the expected content never has to be duplicated by hand.
 */
public final class ResourceOverrideMod implements ModInitializer {

    /** Mod id the test packs into the generated {@code fabric.mod.json}. */
    public static final String MOD_ID = "pvz-resource-override-mod";

    /** Pak path the {@code addBytes} registration replaces with {@link #lawnStringsBytes()}. */
    public static final String BYTES_RESOURCE = "properties/LawnStrings.txt";

    /** Pak path the {@code addFile} registration replaces with the file of the same property. */
    public static final String FILE_RESOURCE = "particles/pea_splats.png";

    /** Pak path only the registered root directory answers with a file of its own. */
    public static final String ROOT_RESOURCE = "images/Almanac.png";

    /** A retail resource no registration shadows: the engine must still read it from the pak. */
    public static final String CONTROL_RESOURCE = "reanim/PeaShooter.reanim";

    /** System property naming the loose file {@code addFile} registers (set by the test). */
    public static final String FILE_OVERRIDE_PROPERTY = "pvz.test.overrideFile";

    /** System property naming the loose root {@code addRoot} registers (set by the test). */
    public static final String ROOT_OVERRIDE_PROPERTY = "pvz.test.overrideRoot";

    /** The text a reader of the overlaid LawnStrings has to find; also identifiable, not retail. */
    public static final String LAWNSTRINGS_MARKER = "TestOverrideShooter";

    /** The overlaid LawnStrings text: a section heading and a name no retail file contains. */
    private static final String LAWNSTRINGS_TEXT = "[PEASHOOTER]\n" + LAWNSTRINGS_MARKER + "\n";

    /** Marker of the {@code addFile} content: identifies the bytes in a dump. */
    private static final String FILE_MARKER = "PvZ fixture addFile override for particles/pea_splats.png";

    /** Marker of the {@code addRoot} content: identifies the bytes in a dump. */
    private static final String ROOT_MARKER = "PvZ fixture addRoot override for images/Almanac.png";

    @Override
    public void onInitialize() {
        ResourceOverrides.addBytes(BYTES_RESOURCE, lawnStringsBytes());

        // The file and the root live in the test's temporary directory, which it names through the
        // system properties of the launcher: without them there is nothing to register, and the test
        // that proves them always sets both.
        Path file = propertyPath(FILE_OVERRIDE_PROPERTY);

        if (file != null) {
            ResourceOverrides.addFile(FILE_RESOURCE, file);
        }

        Path root = propertyPath(ROOT_OVERRIDE_PROPERTY);

        if (root != null) {
            ResourceOverrides.addRoot(root);
        }
    }

    /** The in-memory LawnStrings the {@code addBytes} registration serves. */
    public static byte[] lawnStringsBytes() {
        return LAWNSTRINGS_TEXT.getBytes(StandardCharsets.UTF_8);
    }

    /** The bytes the test writes for the {@code addFile} registration (a fake PNG, no art needed). */
    public static byte[] fileOverrideBytes() {
        return fakePng(FILE_MARKER);
    }

    /** The bytes the test writes to {@code images/Almanac.png} under the registered root. */
    public static byte[] rootImageBytes() {
        return fakePng(ROOT_MARKER);
    }

    /** A PNG-signature-prefixed, text-identifiable file: distinctive without shipping any art. */
    private static byte[] fakePng(String marker) {
        return ("\u0089PNG\r\n\u001A\n" + marker + "\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static Path propertyPath(String property) {
        String value = System.getProperty(property, "");
        return value.isBlank() ? null : Path.of(value);
    }
}
