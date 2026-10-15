package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Resources.ResourceEntry;
import cn.enaium.fabricpvz.portable.lawn.Resources.ResourceType;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Validates the mechanically ported resource table in {@link Resources}.
 *
 * <p>The always-on checks verify the table is internally consistent with the 879
 * {@code *_ID} constants and that the {@link ImageID}/{@link SoundType} constant
 * classes cover every image/sound id. The real-asset check (skipped when no game
 * copy is present) parses {@code properties/resources.xml} out of {@code main.pak},
 * replays the {@code SetDefaults}/{@code Image|Sound|Font} manifest semantics and
 * asserts that every id maps to a manifest entry of the same kind whose file
 * resolves as a pak member or a loose resource file, with the canonical
 * 691 images / 168 sounds / 20 fonts split.
 */
class ResourcesTableTest {

    private static final int EXPECTED_IMAGES = 691;
    private static final int EXPECTED_SOUNDS = 168;
    private static final int EXPECTED_FONTS = 20;
    private static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".gif", ".tga"};

    /** Every {@code *_ID} constant must be mapped exactly once and agree with the table. */
    @Test
    void constantsAreUniqueAndFullyMapped() {
        Map<String, Integer> constants = intConstants();
        assertEquals(Resources.RESOURCE_ID_MAX, constants.size(),
                "one *_ID constant per resource id");
        assertEquals(Resources.RESOURCE_ID_MAX, Resources.ENTRIES.length);

        Set<Integer> seenValues = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        for (Map.Entry<String, Integer> constant : constants.entrySet()) {
            String fieldName = constant.getKey();
            int value = constant.getValue();
            String name = fieldName.substring(0, fieldName.length() - "_ID".length());

            assertTrue(seenValues.add(value), "duplicate id value " + value + " at " + fieldName);
            assertTrue(seenNames.add(name), "duplicate name " + name);

            ResourceEntry entry = Resources.ENTRIES[value];
            assertEquals(value, entry.id(), "ENTRIES must be indexed by id");
            assertEquals(name, entry.name(), "name for " + fieldName);
            assertNotNull(entry.type(), "type for " + fieldName);
            assertNotNull(entry.group(), "group for " + fieldName);
            assertTrue(Resources.getGroupEntries(entry.group()).contains(entry),
                    "group table missing " + fieldName);

            assertEquals(value, Resources.getIdByName(name), "reverse lookup for " + name);
            assertEquals(name, Resources.getName(value));
            assertEquals(entry.type(), Resources.getType(value));
            assertEquals(entry.group(), Resources.getGroup(value));
        }

        for (int id = 0; id < Resources.RESOURCE_ID_MAX; id++) {
            assertEquals(id, Resources.ENTRIES[id].id());
        }
        int mapped = 0;
        for (List<ResourceEntry> group : Resources.GROUPS.values()) {
            mapped += group.size();
        }
        assertEquals(Resources.RESOURCE_ID_MAX, mapped, "every id belongs to exactly one group");
    }

    /** Type counts of the table itself must already be the canonical 691/168/20. */
    @Test
    void tableHasCanonicalTypeCounts() {
        assertEquals(EXPECTED_IMAGES, Resources.getNumResources(ResourceType.IMAGE));
        assertEquals(EXPECTED_SOUNDS, Resources.getNumResources(ResourceType.SOUND));
        assertEquals(EXPECTED_FONTS, Resources.getNumResources(ResourceType.FONT));
    }

    /** The {@link ImageID} / {@link SoundType} constant classes cover every table id of their kind. */
    @Test
    void imageAndSoundConstantClassesAgreeWithTable() {
        Map<String, Integer> images = intConstantsOf(ImageID.class);
        Map<String, Integer> sounds = intConstantsOf(SoundType.class);
        assertEquals(EXPECTED_IMAGES, images.size(), "ImageID constant count");
        assertEquals(EXPECTED_SOUNDS, sounds.size(), "SoundType constant count");

        Set<Integer> imageValues = new HashSet<>();
        for (Map.Entry<String, Integer> constant : images.entrySet()) {
            assertTrue(imageValues.add(constant.getValue()),
                    "duplicate ImageID value " + constant.getValue());
            assertEquals(constant.getKey(), Resources.getName(constant.getValue()),
                    "ImageID." + constant.getKey() + " name");
            assertEquals(ResourceType.IMAGE, Resources.getType(constant.getValue()),
                    "ImageID." + constant.getKey() + " kind");
        }

        Set<Integer> soundValues = new HashSet<>();
        for (Map.Entry<String, Integer> constant : sounds.entrySet()) {
            assertTrue(soundValues.add(constant.getValue()),
                    "duplicate SoundType value " + constant.getValue());
            assertEquals(constant.getKey(), Resources.getName(constant.getValue()),
                    "SoundType." + constant.getKey() + " name");
            assertEquals(ResourceType.SOUND, Resources.getType(constant.getValue()),
                    "SoundType." + constant.getKey() + " kind");
        }
    }

    /** Cross-check the whole table against the real manifest in {@code main.pak}. */
    @Test
    void everyIdResolvesAgainstRealManifest() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");

        PakInterface pakInterface = new PakInterface();
        pakInterface.setResourceFolder(root.get().toString());
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        if (pakPath.isPresent()) {
            assertTrue(pakInterface.addPakFile(pakPath.get()), "real main.pak must mount");
        }

        byte[] manifestBytes = pakInterface.readResource("properties/resources.xml");
        if (manifestBytes == null) {
            Optional<Path> loose = TestAssets.resolve("properties/resources.xml");
            Assumptions.assumeTrue(loose.isPresent(), "no properties/resources.xml available");
            manifestBytes = Files.readAllBytes(loose.get());
        }
        Assumptions.assumeTrue(manifestBytes != null && manifestBytes.length > 0,
                "empty resource manifest");

        Map<String, ManifestEntry> manifest = parseManifest(manifestBytes);

        assertEquals(EXPECTED_IMAGES,
                manifest.values().stream().filter(e -> e.type == ResourceType.IMAGE).count());
        assertEquals(EXPECTED_SOUNDS,
                manifest.values().stream().filter(e -> e.type == ResourceType.SOUND).count());
        assertEquals(EXPECTED_FONTS,
                manifest.values().stream().filter(e -> e.type == ResourceType.FONT).count());

        for (ResourceEntry entry : Resources.ENTRIES) {
            ManifestEntry manifestEntry = manifest.get(entry.name());
            assertNotNull(manifestEntry, "no manifest entry for " + entry.name());
            assertEquals(entry.type(), manifestEntry.type, "kind of " + entry.name());
            assertTrue(resolves(pakInterface, manifestEntry),
                    () -> "unresolved resource " + entry.name() + " -> " + manifestEntry.path);
        }

        for (String image : intConstantsOf(ImageID.class).keySet()) {
            assertManifestResolves(pakInterface, manifest, image, ResourceType.IMAGE);
        }
        for (String sound : intConstantsOf(SoundType.class).keySet()) {
            assertManifestResolves(pakInterface, manifest, sound, ResourceType.SOUND);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void assertManifestResolves(PakInterface pakInterface,
            Map<String, ManifestEntry> manifest, String name, ResourceType expectedType)
            throws IOException {
        ManifestEntry manifestEntry = manifest.get(name);
        assertNotNull(manifestEntry, "no manifest entry for " + name);
        assertEquals(expectedType, manifestEntry.type, "kind of " + name);
        assertTrue(resolves(pakInterface, manifestEntry),
                () -> "unresolved resource " + name + " -> " + manifestEntry.path);
    }

    private static Map<String, Integer> intConstants() {
        return intConstantsOf(Resources.class, "_ID");
    }

    private static Map<String, Integer> intConstantsOf(Class<?> owner) {
        return intConstantsOf(owner, null);
    }

    private static Map<String, Integer> intConstantsOf(Class<?> owner, String suffix) {
        Map<String, Integer> constants = new LinkedHashMap<>();
        for (Field field : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;
            }
            if (suffix != null && !field.getName().endsWith(suffix)) {
                continue;
            }
            try {
                constants.put(field.getName(), field.getInt(null));
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        return constants;
    }

    private record ManifestEntry(String group, ResourceType type, String path) {
    }

    private static Map<String, ManifestEntry> parseManifest(byte[] bytes) throws IOException {
        DocumentBuilder builder;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            builder = factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new IOException("XML parser unavailable", e);
        }

        Document document;
        try {
            document = builder.parse(new ByteArrayInputStream(stripBom(bytes)));
        } catch (Exception e) {
            throw new IOException("bad resources.xml", e);
        }

        Element root = document.getDocumentElement();
        assertEquals("ResourceManifest", root.getTagName(), "resources.xml root tag");

        Map<String, ManifestEntry> manifest = new LinkedHashMap<>();
        NodeList groups = root.getChildNodes();
        for (int i = 0; i < groups.getLength(); i++) {
            Node node = groups.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element groupElement = (Element) node;
            assertEquals("Resources", groupElement.getTagName());
            String group = groupElement.getAttribute("id");

            String defaultPath = "";
            String defaultPrefix = "";
            NodeList items = groupElement.getChildNodes();
            for (int j = 0; j < items.getLength(); j++) {
                Node item = items.item(j);
                if (item.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) item;
                switch (element.getTagName()) {
                    case "SetDefaults" -> {
                        String path = element.getAttribute("path");
                        defaultPath = path.isEmpty() ? "" : stripTrailingSlash(path) + "/";
                        defaultPrefix = element.getAttribute("idprefix");
                    }
                    case "Image", "Sound", "Font" -> {
                        String id = element.getAttribute("id");
                        String path = element.getAttribute("path");
                        String key = defaultPrefix + id;
                        String full = path.startsWith("!") ? path : defaultPath + path;
                        ResourceType type = switch (element.getTagName()) {
                            case "Image" -> ResourceType.IMAGE;
                            case "Sound" -> ResourceType.SOUND;
                            default -> ResourceType.FONT;
                        };
                        manifest.put(key, new ManifestEntry(group, type, full));
                    }
                    default -> throw new AssertionError("unexpected element " + element.getTagName());
                }
            }
        }
        return manifest;
    }

    private static boolean resolves(PakInterface pakInterface, ManifestEntry entry)
            throws IOException {
        String path = entry.path;
        if (path.startsWith("!")) {
            // mFromProgram resource: not backed by a file.
            return true;
        }
        switch (entry.type) {
            case SOUND -> {
                return exists(pakInterface, withExtension(path, ".ogg"));
            }
            case FONT -> {
                return exists(pakInterface, hasExtension(path) ? path : path + ".txt");
            }
            default -> {
                return imageResolves(pakInterface, path);
            }
        }
    }

    private static boolean imageResolves(PakInterface pakInterface, String path) throws IOException {
        if (imageBaseExists(pakInterface, path)) {
            return true;
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            String directory = path.substring(0, slash + 1);
            if (imageBaseExists(pakInterface, directory + "_" + path.substring(slash + 1))) {
                return true;
            }
        }
        // Alpha images may be probed as "<base>_" (ImageLib.cpp:1264-1274).
        return imageBaseExists(pakInterface, path + "_");
    }

    private static boolean imageBaseExists(PakInterface pakInterface, String base) throws IOException {
        if (hasImageExtension(base)) {
            return exists(pakInterface, base);
        }
        for (String extension : IMAGE_EXTENSIONS) {
            if (exists(pakInterface, base + extension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean exists(PakInterface pakInterface, String path) throws IOException {
        return pakInterface.exists(path);
    }

    private static boolean hasExtension(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.indexOf('.') >= 0;
    }

    private static boolean hasImageExtension(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String extension : IMAGE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String withExtension(String path, String extension) {
        return path.toLowerCase(Locale.ROOT).endsWith(extension) ? path : path + extension;
    }

    private static String stripTrailingSlash(String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    private static byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            byte[] trimmed = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
