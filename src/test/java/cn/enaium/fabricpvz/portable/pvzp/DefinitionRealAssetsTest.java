package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Assumption-gated checks that drive the real game assets through {@link Definition}: a
 * {@code particles/*.xml} emitter file and a {@code reanim/*.reanim} file are loaded from
 * {@code main.pak} twice (first compile + cache write, then the compiled-cache read) and the parsed
 * counts are compared against values extracted from the raw file text.
 *
 * <p>The maps here are test-local copies of the upstream {@code Definition.cpp} tables for the members
 * those files actually use; they exist so the format layer is exercised on real data without depending
 * on the particle/reanim runtime maps. Skipped when no retail assets are available.
 */
class DefinitionRealAssetsTest {

    /** Test-local particle emitter (upstream {@code PvzpEmitterDefinition} subset). */
    public static class TestEmitter {
        public String mName = "";
        public Image mImage;
        public int mParticleFlags;
        public FloatParameterTrack mSystemDuration = new FloatParameterTrack();
        public FloatParameterTrack mSpawnMinActive = new FloatParameterTrack();
        public FloatParameterTrack mSpawnMaxLaunched = new FloatParameterTrack();
        public FloatParameterTrack mParticleDuration = new FloatParameterTrack();
        public FloatParameterTrack mParticleScale = new FloatParameterTrack();
        public FloatParameterTrack mParticleAlpha = new FloatParameterTrack();
        public FloatParameterTrack mParticleBrightness = new FloatParameterTrack();
        public FloatParameterTrack mParticleSpinSpeed = new FloatParameterTrack();
        public FloatParameterTrack mParticleSpinAngle = new FloatParameterTrack();
        public FloatParameterTrack mParticleRed = new FloatParameterTrack();
        public FloatParameterTrack mParticleGreen = new FloatParameterTrack();
        public FloatParameterTrack mParticleBlue = new FloatParameterTrack();

        public TestEmitter() {
        }
    }

    /** Test-local particle definition (upstream {@code PvzpParticleDefinition}). */
    public static class TestParticleDefinition {
        public DefinitionArrayDef mEmitterDefs = new DefinitionArrayDef(new TestEmitter[0]);

        public TestParticleDefinition() {
        }
    }

    /** Test-local reanim transform (upstream {@code ReanimatorTransform}, placeholders included). */
    public static class TestTransform {
        public float mTransX = -10000.0f;
        public float mTransY = -10000.0f;
        public float mSkewX = -10000.0f;
        public float mSkewY = -10000.0f;
        public float mScaleX = -10000.0f;
        public float mScaleY = -10000.0f;
        public float mFrame = -10000.0f;
        public float mAlpha = -10000.0f;
        public Image mImage;
        public Font mFont;
        public String mText = "";

        public TestTransform() {
        }
    }

    /** Test-local reanim track (upstream {@code ReanimatorTrack}). */
    public static class TestTrack {
        public String mName = "";
        public DefinitionArrayDef mTransforms = new DefinitionArrayDef(new TestTransform[0]);

        public TestTrack() {
        }
    }

    /** Test-local reanim definition (upstream {@code ReanimatorDefinition}). */
    public static class TestReanimDefinition {
        public DefinitionArrayDef mTracks = new DefinitionArrayDef(new TestTrack[0]);
        public float mFPS = 12.0f;

        public TestReanimDefinition() {
        }
    }

    static final DefMap EMITTER_MAP = new DefMapBuilder()
            .field("Name", "mName", DefFieldType.DT_STRING)
            .field("Image", "mImage", DefFieldType.DT_IMAGE)
            .field("ParticleFlags", "mParticleFlags", DefFieldType.DT_FLAGS, Definition.gParticleFlagSymbols)
            .field("SystemDuration", "mSystemDuration", DefFieldType.DT_TRACK_FLOAT)
            .field("SpawnMinActive", "mSpawnMinActive", DefFieldType.DT_TRACK_FLOAT)
            .field("SpawnMaxLaunched", "mSpawnMaxLaunched", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleDuration", "mParticleDuration", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleScale", "mParticleScale", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleAlpha", "mParticleAlpha", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleBrightness", "mParticleBrightness", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleSpinSpeed", "mParticleSpinSpeed", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleSpinAngle", "mParticleSpinAngle", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleRed", "mParticleRed", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleGreen", "mParticleGreen", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleBlue", "mParticleBlue", DefFieldType.DT_TRACK_FLOAT)
            .build(TestEmitter::new);

    static final DefMap PARTICLE_MAP = new DefMapBuilder()
            .field("Emitter", "mEmitterDefs", DefFieldType.DT_ARRAY, EMITTER_MAP)
            .build(TestParticleDefinition::new);

    static final DefMap TRANSFORM_MAP = new DefMapBuilder()
            .field("x", "mTransX", DefFieldType.DT_FLOAT)
            .field("y", "mTransY", DefFieldType.DT_FLOAT)
            .field("kx", "mSkewX", DefFieldType.DT_FLOAT)
            .field("ky", "mSkewY", DefFieldType.DT_FLOAT)
            .field("sx", "mScaleX", DefFieldType.DT_FLOAT)
            .field("sy", "mScaleY", DefFieldType.DT_FLOAT)
            .field("f", "mFrame", DefFieldType.DT_FLOAT)
            .field("a", "mAlpha", DefFieldType.DT_FLOAT)
            .field("i", "mImage", DefFieldType.DT_IMAGE)
            .field("font", "mFont", DefFieldType.DT_FONT)
            .field("text", "mText", DefFieldType.DT_STRING)
            .build(TestTransform::new);

    static final DefMap TRACK_MAP = new DefMapBuilder()
            .field("name", "mName", DefFieldType.DT_STRING)
            .field("t", "mTransforms", DefFieldType.DT_ARRAY, TRANSFORM_MAP)
            .build(TestTrack::new);

    static final DefMap REANIM_MAP = new DefMapBuilder()
            .field("track", "mTracks", DefFieldType.DT_ARRAY, TRACK_MAP)
            .field("fps", "mFPS", DefFieldType.DT_FLOAT)
            .build(TestReanimDefinition::new);

    private String mOriginalAppDataFolder;
    private boolean mPakMounted;
    private String mFixtureText;
    private String mReanimText;

    @BeforeEach
    void rememberFolder() {
        mOriginalAppDataFolder = Common.getAppDataFolder();
    }

    @AfterEach
    void restoreGlobals() throws IOException {
        Common.setAppDataFolder(mOriginalAppDataFolder);
        Definition.gDefResourceResolver = null;
        if (mPakMounted) {
            PakInterface.getInstance().reset();
            mPakMounted = false;
        }
    }

    @Test
    void parsesRealParticleEmitterFileThroughTheCompiledCache(@TempDir Path theDir) throws IOException {
        mountRealPak(theDir);
        String aText = mFixtureText;
        Common.setAppDataFolder(theDir.resolve("appdata").toString());
        installDummyResolver();

        TestParticleDefinition aCompiled = new TestParticleDefinition();
        assertTrue(Definition.loadXML("particles/Award.xml", PARTICLE_MAP, aCompiled),
                "the real emitter file must load");

        int anExpectedEmitters = countOccurrences(aText, "<Emitter>");
        assertEquals(anExpectedEmitters, aCompiled.mEmitterDefs.mArrayCount, "every <Emitter> is parsed");
        assertTrue(anExpectedEmitters >= 10, "the fixture should stay non-trivial: " + anExpectedEmitters);
        assertEquals(TestEmitter.class, aCompiled.mEmitterDefs.mArrayData.getClass().getComponentType());

        TestEmitter aFirst = (TestEmitter) aCompiled.mEmitterDefs.mArrayData[0];
        assertEquals(firstElementText(aText, "Name"), aFirst.mName, "the first emitter keeps its name");
        assertNotNull(aFirst.mImage);
        assertEquals("IMAGE_AWARDRAYS2", aFirst.mImage.mFilePath, "the image resolves by name");
        assertEquals(0, aFirst.mParticleFlags & (1 << 0), "RandomLaunchSpin is 0 in this emitter");
        assertTrue((aFirst.mParticleFlags & (1 << 8)) != 0, "Additive is 1 in this emitter");
        assertEquals(1, aFirst.mParticleDuration.mCountNodes, "ParticleDuration is a single value");
        assertEquals(1500.0f, aFirst.mParticleDuration.mNodes[0].mLowValue);
        assertEquals(1500.0f, aFirst.mParticleDuration.mNodes[0].mHighValue);
        assertEquals(3, aFirst.mSpawnMinActive.mCountNodes, "SpawnMinActive is '0,2 1,2 4,21'");
        assertEquals(0.21f, aFirst.mSpawnMinActive.mNodes[2].mTime, 1e-6f);

        // second load: the compiled cache written above must reproduce the same definition
        TestParticleDefinition aCached = new TestParticleDefinition();
        assertTrue(Definition.loadXML("particles/Award.xml", PARTICLE_MAP, aCached), "the cache must load");
        assertEquals(aCompiled.mEmitterDefs.mArrayCount, aCached.mEmitterDefs.mArrayCount);
        TestEmitter aFirstCached = (TestEmitter) aCached.mEmitterDefs.mArrayData[0];
        assertEquals(aFirst.mName, aFirstCached.mName);
        assertEquals(aFirst.mParticleFlags, aFirstCached.mParticleFlags);
        assertEquals(aFirst.mSpawnMinActive.mCountNodes, aFirstCached.mSpawnMinActive.mCountNodes);
        assertEquals(aFirst.mSpawnMinActive.mNodes[2].mTime, aFirstCached.mSpawnMinActive.mNodes[2].mTime, 1e-6f);
        assertNotNull(aFirstCached.mImage);
        assertEquals("IMAGE_AWARDRAYS2", aFirstCached.mImage.mFilePath);
    }

    @Test
    void parsesRealReanimFileThroughTheCompiledCache(@TempDir Path theDir) throws IOException {
        mountRealPak(theDir);
        String aText = mReanimText;
        Common.setAppDataFolder(theDir.resolve("appdata").toString());
        installDummyResolver();

        TestReanimDefinition aCompiled = new TestReanimDefinition();
        assertTrue(Definition.loadXML("reanim/PeaShooter.reanim", REANIM_MAP, aCompiled), "the real reanim must load");

        int anExpectedTracks = countOccurrences(aText, "<track>");
        assertEquals(anExpectedTracks, aCompiled.mTracks.mArrayCount, "every <track> is parsed");
        assertTrue(anExpectedTracks >= 20, "the fixture should stay non-trivial: " + anExpectedTracks);
        assertEquals(12.0f, aCompiled.mFPS, "the file says <fps>12</fps>");

        TestTrack aFirst = (TestTrack) aCompiled.mTracks.mArrayData[0];
        assertEquals(firstElementText(aText, "name"), aFirst.mName);
        int anExpectedTransforms = countOccurrences(firstBlock(aText, "track"), "<t>");
        assertEquals(anExpectedTransforms, aFirst.mTransforms.mArrayCount, "every <t> of the first track is parsed");
        assertTrue(anExpectedTransforms > 10, "the first track should have many frames: " + anExpectedTransforms);

        TestTransform aFirstTransform = (TestTransform) aFirst.mTransforms.mArrayData[0];
        assertEquals(-1.0f, aFirstTransform.mFrame, "the first frame says <f>-1</f>");
        TestTransform anEmptyTransform = (TestTransform) aFirst.mTransforms.mArrayData[1];
        assertEquals(-10000.0f, anEmptyTransform.mFrame, "an empty <t></t> keeps the upstream placeholder");
        assertEquals("", anEmptyTransform.mText);
        assertEquals(-10000.0f, anEmptyTransform.mTransX, "every unset float keeps the placeholder");
        assertTrue(Definition.getSize(REANIM_MAP, aCompiled) > 10000, "the parsed definition is non-trivial");

        TestReanimDefinition aCached = new TestReanimDefinition();
        assertTrue(Definition.loadXML("reanim/PeaShooter.reanim", REANIM_MAP, aCached), "the cache must load");
        assertEquals(aCompiled.mTracks.mArrayCount, aCached.mTracks.mArrayCount);
        TestTrack aFirstCached = (TestTrack) aCached.mTracks.mArrayData[0];
        assertEquals(aFirst.mName, aFirstCached.mName);
        assertEquals(aFirst.mTransforms.mArrayCount, aFirstCached.mTransforms.mArrayCount);
        TestTransform aFirstCachedTransform = (TestTransform) aFirstCached.mTransforms.mArrayData[0];
        assertEquals(aFirstTransform.mFrame, aFirstCachedTransform.mFrame, "the first frame survives the cache");
        TestTransform anEmptyCachedTransform = (TestTransform) aFirstCached.mTransforms.mArrayData[1];
        assertEquals(anEmptyTransform.mFrame, anEmptyCachedTransform.mFrame, "the empty frame survives the cache");
        assertEquals(anEmptyTransform.mText, anEmptyCachedTransform.mText);
        assertEquals(-1.0f, aFirstCachedTransform.mFrame, "the cache keeps the explicit <f>-1</f>");
        assertEquals(-10000.0f, anEmptyCachedTransform.mFrame, "the cache keeps the placeholder");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Mounts the retail {@code main.pak} on the framework singleton and reads the two fixtures. */
    private void mountRealPak(Path theDir) throws IOException {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "no real PvZ assets available");
        Optional<Path> aPakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(aPakPath.isPresent(), "no main.pak in the resource root");

        PakInterface aPak = PakInterface.getInstance();
        aPak.setResourceFolder(aRoot.get().toString());
        assertTrue(aPak.addPakFile(aPakPath.get()), "the retail main.pak must mount");
        mPakMounted = true;

        byte[] anEmitterFile = aPak.readResource("particles/Award.xml");
        Assumptions.assumeTrue(anEmitterFile != null, "particles/Award.xml is missing from the pak");
        mFixtureText = new String(anEmitterFile, StandardCharsets.UTF_8);

        byte[] aReanimFile = aPak.readResource("reanim/PeaShooter.reanim");
        Assumptions.assumeTrue(aReanimFile != null, "reanim/PeaShooter.reanim is missing from the pak");
        mReanimText = new String(aReanimFile, StandardCharsets.UTF_8);
    }

    /**
     * Installs a resolver that fabricates an image/font named after the requested resource, so the
     * definition layer can bind the {@code <Image>}/{@code <i>}/{@code <font>} members without a
     * resource manager. The name is kept on {@code mFilePath}, which is what the cache stores.
     */
    private static void installDummyResolver() {
        Definition.gDefResourceResolver = new Definition.DefinitionResourceResolver() {
            @Override
            public Image loadImage(String theName) {
                Image anImage = new Image();
                anImage.mFilePath = theName;
                return anImage;
            }

            @Override
            public Font loadFont(String theName) {
                return new Font() {
                    @Override
                    public int stringWidth(String theString) {
                        return 0;
                    }

                    @Override
                    public int charWidth(int theChar) {
                        return 0;
                    }

                    @Override
                    public int charWidthKern(int theChar, int thePrevChar) {
                        return 0;
                    }

                    @Override
                    public void drawString(cn.enaium.fabricpvz.portable.sexy.graphics.Graphics g, int theX, int theY, String theString,
                                           cn.enaium.fabricpvz.portable.sexy.Color theColor, cn.enaium.fabricpvz.portable.sexy.Rect theClipRect) {
                    }

                    @Override
                    public Font duplicate() {
                        return this;
                    }
                };
            }
        };
    }

    private static int countOccurrences(String theText, String theNeedle) {
        int aCount = 0;
        int anIndex = 0;
        while ((anIndex = theText.indexOf(theNeedle, anIndex)) >= 0) {
            aCount++;
            anIndex += theNeedle.length();
        }
        return aCount;
    }

    private static String firstElementText(String theText, String theTag) {
        int aStart = theText.indexOf("<" + theTag + ">");
        if (aStart < 0) {
            return null;
        }
        int anEnd = theText.indexOf("</" + theTag + ">", aStart);
        return theText.substring(aStart + theTag.length() + 2, anEnd);
    }

    private static String firstBlock(String theText, String theTag) {
        int aStart = theText.indexOf("<" + theTag + ">");
        int anEnd = theText.indexOf("</" + theTag + ">", aStart);
        return theText.substring(aStart, anEnd);
    }
}
