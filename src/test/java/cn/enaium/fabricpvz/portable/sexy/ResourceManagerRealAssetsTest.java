package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Assumption-gated integration test: parses the shipped {@code properties/resources.xml} out of the
 * local {@code main.pak} and checks the documented manifest counts and known id → pak member
 * resolution (docs/research/01-framework-core.md §4.5).
 */
class ResourceManagerRealAssetsTest {

    @AfterEach
    void unmountSingletonPak() throws IOException {
        PakInterface.getInstance().reset();
        PakInterface.getInstance().setResourceFolder("");
        Common.setResourceFolder("");
    }

    @Test
    void parsesShippedManifestAndResolvesMembers() throws IOException {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        assumeTrue(aRoot.isPresent(), "no real Plants vs. Zombies assets on this machine");
        Path aResourceRoot = aRoot.get();

        PakInterface aPak = PakInterface.getInstance();
        aPak.setResourceFolder(aResourceRoot.toString());
        Common.setResourceFolder(aResourceRoot.toString());

        Path aMainPak = TestAssets.resolve("main.pak").orElse(null);
        if (aMainPak != null) {
            assertTrue(aPak.addPakFile(aMainPak), "main.pak must mount");
        }

        ResourceManager aManager = new ResourceManager(null);
        assertTrue(aManager.parseResourcesFile("properties/resources.xml"), aManager::getErrorText);

        assertEquals(691, aManager.mImageMap.size());
        assertEquals(168, aManager.mSoundMap.size());
        assertEquals(20, aManager.mFontMap.size());
        assertEquals(30, aManager.mResGroupMap.size());

        assertEquals(691, aManager.getNumImages(""));
        assertEquals(168, aManager.getNumSounds(""));
        assertEquals(20, aManager.getNumFonts(""));
        assertEquals(879, aManager.getNumResources(""));

        // Known ids used by the game resolve to members that exist on disk/in the pak.
        assertImageMember(aPak, aManager, "IMAGE_TITLESCREEN", "images/TITLESCREEN.PNG");
        assertImageMember(aPak, aManager, "IMAGE_LOADBAR_DIRT", "images/LOADBAR_DIRT.PNG");
        assertSoundMember(aPak, aManager, "SOUND_BUTTONCLICK", "sounds/BUTTONCLICK.OGG");

        ResourceManager.FontRes aFont = (ResourceManager.FontRes) aManager.mFontMap.get("FONT_BRIANNETOD16");
        assertNotNull(aFont);
        assertEquals("data/BrianneTod16.txt", aFont.mPath);
        assertTrue(aPak.exists(aFont.mPath), aFont.mPath);
    }

    private static void assertImageMember(PakInterface thePak, ResourceManager theManager, String theId, String theMember)
            throws IOException {
        ResourceManager.ImageRes aRes = (ResourceManager.ImageRes) theManager.mImageMap.get(theId);
        assertNotNull(aRes, theId);
        assertEquals(theMember.substring(0, theMember.lastIndexOf('.')).toLowerCase(),
                aRes.mPath.toLowerCase(), theId);
        assertTrue(thePak.exists(theMember), theId + " -> " + theMember);
    }

    private static void assertSoundMember(PakInterface thePak, ResourceManager theManager, String theId, String theMember)
            throws IOException {
        ResourceManager.SoundRes aRes = (ResourceManager.SoundRes) theManager.mSoundMap.get(theId);
        assertNotNull(aRes, theId);
        assertTrue(thePak.exists(theMember), theId + " -> " + theMember);
    }
}
