package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.AnimType;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.SharedImageRef;

/** Verifies the manifest parser and the id maps against the upstream {@code ResourceManager}. */
class ResourceManagerTest {

    private static final String MANIFEST = """
        <?xml version="1.0"?>
        <ResourceManifest>
        <Resources id="TestGroup">
          <SetDefaults path="images" idprefix="IMAGE_" />
          <Image id="BLANK" path="blank" />
          <Image path="titlescreen" a8r8g8b8="" />
          <Image id="SPRITE" path="sprite" rows="2" cols="3" anim="pingpong" framedelay="5" begindelay="1" enddelay="2"
                 perframedelay="1,2,3" framemap="0,1,2" alphaimage="alpha" alphacolor="80FF00" variant="v1"
                 nobits2d="" minsubdivide="" ddsurface="" a4r4g4b4="" noalpha="" />
          <Image id="PROGRAM_IMG" path="!program" />
          <SetDefaults path="sounds" idprefix="SOUND_" />
          <Sound id="CLICK" path="click" volume="0.5" pan="-10" />
          <Sound path="dup" />
          <SetDefaults path="data" idprefix="FONT_" />
          <Font id="BRIANNE" path="BrianneTod16.txt" tags="a, b" />
          <Font id="REF" path="!ref:FONT_BRIANNE" />
        </Resources>
        <Resources id="second">
          <SetDefaults path="images" idprefix="S2_" />
          <Image path="thing" nopal="" />
        </Resources>
        </ResourceManifest>
        """;

    @TempDir
    Path tmp;

    private PvzpResourceManager parse(String theManifest) throws IOException {
        Path aFile = tmp.resolve("resources.xml");
        Files.writeString(aFile, theManifest, StandardCharsets.UTF_8);
        PvzpResourceManager aManager = new PvzpResourceManager(null);
        assertTrue(aManager.parseResourcesFile(aFile.toString()), () -> "parse failed: " + aManager.getErrorText());
        assertFalse(aManager.hadError(), aManager::getErrorText);
        return aManager;
    }

    @Test
    void parsesAllAttributesIdsAndGroups() throws IOException {
        PvzpResourceManager aManager = parse(MANIFEST);

        assertEquals(5, aManager.mImageMap.size());
        assertEquals(2, aManager.mSoundMap.size());
        assertEquals(2, aManager.mFontMap.size());
        assertEquals(9, aManager.getNumResources(""));
        assertEquals(3, aManager.getNumImages("TestGroup"));
        assertEquals(2, aManager.getNumSounds("TestGroup"));
        assertEquals(2, aManager.getNumFonts("TestGroup"));
        assertEquals(1, aManager.getNumImages("second"));
        assertEquals(5, aManager.getNumImages(""));
        // Group names are case-insensitive in the group index (upstream ResGroupMap StringLessNoCase);
        // per-resource counting compares the stored group name exactly, as upstream does.
        assertEquals(8, aManager.mResGroupMap.get("testgroup").size());

        ResourceManager.ImageRes aBlank = (ResourceManager.ImageRes) aManager.mImageMap.get("IMAGE_BLANK");
        assertNotNull(aBlank);
        assertEquals("images/blank", aBlank.mPath);
        assertEquals("TestGroup", aBlank.mResGroup);
        assertFalse(aBlank.mFromProgram);

        // Id derived from the file name (no extension, case preserved) with the sticky id prefix.
        ResourceManager.ImageRes aTitleScreen =
                (ResourceManager.ImageRes) aManager.mImageMap.get("IMAGE_titlescreen");
        assertNotNull(aTitleScreen);
        assertEquals("images/titlescreen", aTitleScreen.mPath);
        assertTrue(aTitleScreen.mA8R8G8B8);

        ResourceManager.ImageRes aSprite = (ResourceManager.ImageRes) aManager.mImageMap.get("IMAGE_SPRITE");
        assertNotNull(aSprite);
        assertEquals(2, aSprite.mRows);
        assertEquals(3, aSprite.mCols);
        assertEquals(AnimType.AnimType_PingPong, aSprite.mAnimInfo.mAnimType);
        assertEquals(5, aSprite.mAnimInfo.mFrameDelay);
        // compute() expands a ping-pong animation to 2n-2 cels (n = max(rows, cols) = 3 -> 4).
        assertEquals(4, aSprite.mAnimInfo.mNumCels);
        assertEquals(1, aSprite.mAnimInfo.mPerFrameDelay[0]);
        assertEquals(3, aSprite.mAnimInfo.mPerFrameDelay[2]);
        assertEquals(0, aSprite.mAnimInfo.mFrameMap[0]);
        assertEquals("images/alpha", aSprite.mAlphaImage);
        assertEquals(0x80FF00, aSprite.mAlphaColor);
        assertEquals("v1", aSprite.mVariant);
        assertTrue(aSprite.mPalletize);
        assertTrue(aSprite.mA4R4G4B4);
        assertTrue(aSprite.mDDSurface);
        assertTrue(aSprite.mPurgeBits);
        assertTrue(aSprite.mMinimizeSubdivisions);
        assertFalse(aSprite.mAutoFindAlpha);

        ResourceManager.ImageRes aProgram =
                (ResourceManager.ImageRes) aManager.mImageMap.get("IMAGE_PROGRAM_IMG");
        assertNotNull(aProgram);
        assertTrue(aProgram.mFromProgram);
        assertEquals("!program", aProgram.mPath);

        ResourceManager.SoundRes aClick = (ResourceManager.SoundRes) aManager.mSoundMap.get("SOUND_CLICK");
        assertNotNull(aClick);
        assertEquals("sounds/click", aClick.mPath);
        assertEquals(0.5, aClick.mVolume);
        assertEquals(-10, aClick.mPanning);

        ResourceManager.FontRes aFont = (ResourceManager.FontRes) aManager.mFontMap.get("FONT_BRIANNE");
        assertNotNull(aFont);
        assertEquals("data/BrianneTod16.txt", aFont.mPath);
        assertEquals("a, b", aFont.mTags);
        assertFalse(aFont.mSysFont);

        ResourceManager.FontRes aRef = (ResourceManager.FontRes) aManager.mFontMap.get("FONT_REF");
        assertNotNull(aRef);
        assertEquals("!ref:FONT_BRIANNE", aRef.mPath);
        assertFalse(aRef.mSysFont);

        assertNotNull(aManager.mImageMap.get("S2_thing"));
        assertEquals("images/thing", aManager.mImageMap.get("S2_thing").mPath);
        assertFalse(((ResourceManager.ImageRes) aManager.mImageMap.get("S2_thing")).mPalletize);
    }

    @Test
    void gettersReturnMissingSentinelValues() throws IOException {
        PvzpResourceManager aManager = parse(MANIFEST);

        assertTrue(aManager.getImage("IMAGE_NOPE").isNull());
        // An existing but not-yet-loaded image also yields an empty handle (upstream default SharedImageRef).
        assertTrue(aManager.getImage("IMAGE_BLANK").isNull());
        assertEquals(-1, aManager.getSound("SOUND_NOPE"));
        assertEquals(-1, aManager.getSound("SOUND_CLICK"));
        assertNull(aManager.getFont("FONT_NOPE"));
        assertNull(aManager.getFont("FONT_BRIANNE"));

        // !program resources cannot be loaded from disk.
        assertNull(aManager.loadImage("IMAGE_PROGRAM_IMG"));
        assertNull(aManager.loadImage("IMAGE_NOPE"));
        assertNull(aManager.loadFont("FONT_NOPE"));

        Map<String, String> aAttributes = aManager.getImageAttributes("IMAGE_SPRITE");
        assertEquals("2", aAttributes.get("rows"));
        assertEquals("3", aAttributes.get("cols"));
        assertEquals("pingpong", aAttributes.get("anim"));
        assertTrue(aManager.getImageAttributes("IMAGE_NOPE").isEmpty());
    }

    @Test
    void throwingAccessorsReportTheId() throws IOException {
        // fail() latches the first error (like upstream), so each accessor gets a fresh manager.
        PvzpResourceManager anImageManager = parse(MANIFEST);
        ResourceManagerException aImageError =
                assertThrows(ResourceManagerException.class, () -> anImageManager.getImageThrow("IMAGE_NOPE"));
        assertEquals("Image resource not found: IMAGE_NOPE", aImageError.getMessage());

        PvzpResourceManager aSoundManager = parse(MANIFEST);
        ResourceManagerException aSoundError =
                assertThrows(ResourceManagerException.class, () -> aSoundManager.getSoundThrow("SOUND_NOPE"));
        assertEquals("Sound resource not found: SOUND_NOPE", aSoundError.getMessage());

        PvzpResourceManager aFontManager = parse(MANIFEST);
        ResourceManagerException aFontError =
                assertThrows(ResourceManagerException.class, () -> aFontManager.getFontThrow("FONT_NOPE"));
        assertEquals("Font resource not found: FONT_NOPE", aFontError.getMessage());
    }

    @Test
    void duplicateIdFails() throws IOException {
        String aManifest = """
            <?xml version="1.0"?>
            <ResourceManifest>
            <Resources id="G">
              <SetDefaults path="images" idprefix="IMAGE_" />
              <Image id="DUP" path="a" />
              <Image id="DUP" path="b" />
            </Resources>
            </ResourceManifest>
            """;
        Path aFile = tmp.resolve("dup.xml");
        Files.writeString(aFile, aManifest, StandardCharsets.UTF_8);

        ResourceManager aManager = new ResourceManager(null);
        assertFalse(aManager.parseResourcesFile(aFile.toString()));
        assertTrue(aManager.hadError());
        assertTrue(aManager.getErrorText().contains("Resource already defined."), aManager.getErrorText());
        assertEquals(1, aManager.mImageMap.size());
    }

    @Test
    void missingFileReportsTheExactErrorText() {
        ResourceManager aManager = new ResourceManager(null);
        String aMissing = tmp.resolve("no-such-manifest.xml").toString();
        assertFalse(aManager.parseResourcesFile(aMissing));
        assertTrue(aManager.getErrorText().startsWith("Resource file not found: " + aMissing),
                aManager.getErrorText());
    }

    @Test
    void systemFontWithoutSizeFails() throws IOException {
        String aManifest = """
            <?xml version="1.0"?>
            <ResourceManifest>
            <Resources id="G">
              <SetDefaults path="fonts" idprefix="FONT_" />
              <Font path="!sys:Arial" />
            </Resources>
            </ResourceManifest>
            """;
        Path aFile = tmp.resolve("sysfont.xml");
        Files.writeString(aFile, aManifest, StandardCharsets.UTF_8);

        ResourceManager aManager = new ResourceManager(null);
        assertFalse(aManager.parseResourcesFile(aFile.toString()));
        assertTrue(aManager.getErrorText().contains("SysFont needs point size"), aManager.getErrorText());
    }

    @Test
    void invalidAnimationTypeFails() throws IOException {
        String aManifest = """
            <?xml version="1.0"?>
            <ResourceManifest>
            <Resources id="G">
              <SetDefaults path="images" idprefix="IMAGE_" />
              <Image path="x" anim="bogus" />
            </Resources>
            </ResourceManifest>
            """;
        Path aFile = tmp.resolve("badanim.xml");
        Files.writeString(aFile, aManifest, StandardCharsets.UTF_8);

        ResourceManager aManager = new ResourceManager(null);
        assertFalse(aManager.parseResourcesFile(aFile.toString()));
        assertTrue(aManager.getErrorText().contains("Invalid animation type."), aManager.getErrorText());
    }

    @Test
    void replaceImageAndReverseLookup() throws IOException {
        PvzpResourceManager aManager = parse(MANIFEST);

        Image anImage = new Image();
        assertTrue(aManager.replaceImage("IMAGE_BLANK", anImage));
        assertEquals(anImage, aManager.getImage("IMAGE_BLANK").get());

        String[] aPath = new String[1];
        assertTrue(aManager.findImagePath(anImage, aPath));
        assertEquals("IMAGE_BLANK", aPath[0]);

        Image anUnknown = new Image();
        assertFalse(aManager.findImagePath(anUnknown, aPath));

        assertFalse(aManager.replaceImage("IMAGE_NOPE", anImage));
    }

    @Test
    void deleteResourcesClearsLoadedState() throws IOException {
        PvzpResourceManager aManager = parse(MANIFEST);
        aManager.mLoadedGroups.add("second");

        aManager.deleteResources("second");
        assertFalse(aManager.isGroupLoaded("second"));
    }

    @Test
    void sharedImageRefDefaultsToNull() {
        SharedImageRef aRef = new SharedImageRef();
        assertTrue(aRef.isNull());
        assertNull(aRef.get());
    }
}
