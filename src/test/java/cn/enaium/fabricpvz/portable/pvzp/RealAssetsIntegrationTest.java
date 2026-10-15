package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.pak.PakFileHandle;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.sexy.pak.PakRecord;

/**
 * Integration checks against the real game data of a locally installed copy
 * (working directory or Steam). Skipped automatically when no assets are available:
 * pass {@code -Dpvz.test.resources=<dir>} to point at a copy explicitly.
 */
class RealAssetsIntegrationTest {

    @Test
    void mountsRealMainPakAndValidatesEveryRecord() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(pakPath.isPresent(), "no main.pak in the resource root");

        PakInterface pakInterface = new PakInterface();
        pakInterface.setResourceFolder(root.get().toString());
        assertTrue(pakInterface.addPakFile(pakPath.get()), "real main.pak must mount");

        int members = pakInterface.resourceCount() - 1; // minus the self record
        assertTrue(members > 2000, "unexpectedly small pak: " + members + " members");
        assertEquals(pakInterface.resourceNames().size(), pakInterface.resourceCount());
        assertTrue(pakInterface.resourceNames().contains("DATA/CONTINUUMBOLD14.TXT"));

        long pakSize = Files.size(pakPath.get());
        for (String name : pakInterface.resourceNames()) {
            PakRecord record = pakInterface.open(name, "rb").mRecord;
            assertNotNull(record);
            assertTrue(record.mStartPos >= 0, name + " start position");
            assertTrue(record.mStartPos + (long) record.mSize <= pakSize, name + " exceeds pak size");
        }

        byte[] fontText = pakInterface.requireResource("data/ContinuumBold14.txt");
        assertTrue(fontText.length > 0);
        assertTrue(new String(fontText, StandardCharsets.ISO_8859_1).contains("14"), "font txt should carry its size");

        try (PakFileHandle handle = pakInterface.open("DATA/CONTINUUMBOLD14.TXT", "rb")) {
            byte[] head = new byte[8];
            assertEquals(8, handle.read(head, 0, 8));
        }
    }

    @Test
    void readsLoosePropertyFilesFromRealInstall() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");

        PakInterface pakInterface = new PakInterface();
        pakInterface.setResourceFolder(root.get().toString());
        Optional<Path> tos = TestAssets.resolve("properties/ZombatarTOS.txt");
        Assumptions.assumeTrue(tos.isPresent(), "no properties/ZombatarTOS.txt");

        byte[] tos2 = pakInterface.requireResource("properties/ZombatarTOS.txt");
        assertTrue(tos2.length > 100);
        assertTrue(new String(tos2, StandardCharsets.UTF_8).toLowerCase().contains("zombatar"));
    }
}
