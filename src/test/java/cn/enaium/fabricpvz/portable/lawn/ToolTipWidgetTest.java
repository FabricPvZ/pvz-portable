/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.ToolTipWidget.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * Headless tests for {@link ToolTipWidget}: size measurement, word wrapping, the title/label/warning
 * content set from a hovered object and the warning flash timer. Measuring text needs the retail
 * fonts, so the tests load the {@code LoadingFonts} group and are gated on the assets being present.
 */
class ToolTipWidgetTest {

    /** Minimal stand-in for whatever the cursor is hovering: the three localized strings. */
    private static final class StubHoveredObject {
        final String title;
        final String label;
        final String warning;

        StubHoveredObject(String theTitle, String theLabel, String theWarning) {
            title = theTitle;
            label = theLabel;
            warning = theWarning;
        }
    }

    private static LawnApp sFontApp;
    private static Path sSaveDir;

    /**
     * Boots the real assets (LawnApp::Init() plus the loading thread, which extracts the
     * {@code LoadingFonts} group into the resource table), so the size measurement and the word
     * wrapping below run on the real PICO129/TinyBold metrics: the headless stub font measures as
     * zero width, which would make every assertion in this suite vacuous.
     *
     * <p>The metrics come from the retail PopCap descriptors the manifest names — {@code <Font
     * id="PICO129" path="Pico129.txt"/>} and {@code <Font id="TINYBOLD" path="Pix118Bold.txt"/>}
     * (properties/resources.xml:39,51, under the {@code LoadingFonts} group's
     * {@code <SetDefaults path="data" idprefix="FONT_"/>}; ../verification/research/02-graphics-audio.md
     * §"Font resources"): each {@code data/&lt;name&gt;.txt} descriptor carries the per-character
     * source rects and {@code LayerSetPointSize} (Pico129: ascent 10, point size 9, \
     * ../verification/research/02-graphics-audio.md:1502), and {@code ImageFont::CharWidthKern} sums
     * {@code mWidth * mPointSize / layerPointSize} from those rects (ImageFont.cpp:1359-1409).
     *
     * <p>The boot is driven exactly like {@link GameBootSmokeTest} — {@code new LawnApp()},
     * {@code setArgs}, {@code init()}, then the loading thread — and the headless fixture is
     * deliberately NOT used first, because {@code HeadlessTestApp.initialize} runs the definition
     * tables ({@code Reanimator::LoadDefinitions}/{@code PvzpParticleLoadDefinitions}) before
     * {@code init()} has parsed {@code properties/resources.xml}. Those loaders register every
     * image they resolve into the app's own resource manager ({@code DefinitionLoadImage} ->
     * {@code PvzpAddImageToMap}, PvzpCommon.cpp:1045-1050 / Definition.cpp:332-346); once any
     * earlier app in the same JVM has mounted {@code main.pak} into the process-wide
     * {@link cn.enaium.fabricpvz.portable.sexy.pak.PakInterface}, the pre-{@code init()} resolution succeeds, so
     * {@code ParseCommonResource} reaches ids the manifest is about to define and fails with
     * "Resource already defined." (ResourceManager.cpp:206). {@code Init()} then never reaches
     * the loading thread, the stub fonts stay bound through {@code HeadlessTestApp.bindStubFonts}
     * and every measurement below collapses to zero width. With the fixture gone, {@code Init()}
     * parses the manifest and {@code LoadingThreadProc} is the authority: it loads and binds the
     * whole {@code LoadingFonts}/{@code LoadingImages} group (LawnApp.cpp:1614-1635) and sets
     * {@code mDefaultFont} from the real PICO129 (LawnApp.cpp:1672).
     */
    private static LawnApp appWithFonts() throws InterruptedException {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "no real PvZ assets available");

        if (sFontApp == null) {
            LawnApp anApp = new LawnApp();
            // -resdir/-savedir keep the boot off the working directory and out of the user's saves.
            anApp.setArgs(new String[] {
                    "pvz-portable", "-resdir", aRoot.get().toString(),
                    "-savedir", tempSaveDir().toString(),
            });
            anApp.init();

            // start() would run the whole blocking main loop; drive its two pieces by hand instead —
            // start the loading thread (as start() does) and pump frames until it is done.
            anApp.startLoadingThread();
            long aDeadline = System.nanoTime() + 180_000_000_000L;
            while (!anApp.mLoadingThreadCompleted.get() && System.nanoTime() < aDeadline) {
                anApp.updateFrames();
                Thread.sleep(2);
            }
            assertTrue(anApp.mLoadingThreadCompleted.get(), "resource loading must complete");

            if (anApp.mPlayerInfo == null) {
                anApp.mPlayerInfo = anApp.mProfileMgr.addProfile("ToolTipWidgetTest");
            }
            sFontApp = anApp;
        }

        // A zero-width font would silently turn the wrap/measuring assertions into no-ops, so the
        // numbers below must come from the retail descriptors the manifest binds: PICO129 is
        // data/Pico129.txt (ascent 10, point size 9, 'A' = 7px, space = 3px) and TINYBOLD is
        // data/Pix118Bold.txt (ascent 9, point size 8, space = 4px), measured by
        // ImageFont::CharWidthKern (ImageFont.cpp:1359-1409) out of the descriptor's WidthList.
        assertTrue(Resources.getFontById(Resources.FONT_PICO129_ID) != null,
                "LoadingFonts must bind FONT_PICO129");
        assertTrue(Resources.getFontById(Resources.FONT_TINYBOLD_ID) != null,
                "LoadingFonts must bind FONT_TINYBOLD");
        assertTrue(Resources.getFontById(Resources.FONT_PICO129_ID)
                .stringWidth("The quick brown fox jumps over the lazy dog") > 0,
                "the retail PICO129 font must measure text (the headless stub font is zero-width)");
        return sFontApp;
    }

    /** Save directory for the booted app, so it never touches the user's save data. */
    private static Path tempSaveDir() {
        if (sSaveDir == null) {
            try {
                sSaveDir = Files.createDirectories(
                        Files.createTempDirectory("tooltip-widget-test-userdir").resolve("userdata"));
            } catch (IOException anException) {
                throw new UncheckedIOException(anException);
            }
        }
        return sSaveDir;
    }

    private static void showFor(ToolTipWidget theToolTip, StubHoveredObject theHovered) {
        theToolTip.setTitle(theHovered.title);
        theToolTip.setLabel(theHovered.label);
        theToolTip.setWarningText(theHovered.warning);
    }

    @Test
    void defaultsMatchUpstream() {
        ToolTipWidget aToolTip = new ToolTipWidget();

        assertTrue(aToolTip.mVisible);
        assertFalse(aToolTip.mCenter);
        assertEquals(0, aToolTip.mX);
        assertEquals(0, aToolTip.mY);
        assertEquals(0, aToolTip.mMinLeft);
        assertEquals(GameConstants.BOARD_HEIGHT, aToolTip.mMaxBottom);
        assertEquals(0, aToolTip.mMaxLinesWidth);
        assertEquals(0, aToolTip.mWarningFlashCounter);
    }

    @Test
    void contentComesFromTheHoveredObjectAndIsMeasured() throws InterruptedException {
        appWithFonts();
        ToolTipWidget aToolTip = new ToolTipWidget();
        StubHoveredObject aHovered = new StubHoveredObject("Sunflower", "Helps you grow",
                "Not available yet");

        showFor(aToolTip, aHovered);

        assertEquals("Sunflower", aToolTip.mTitle);
        assertEquals("Helps you grow", aToolTip.mLabel);
        assertEquals("Not available yet", aToolTip.mWarningText);
        assertTrue(aToolTip.mWidth > 0, "CalculateSize produces a width");
        assertTrue(aToolTip.mHeight > 0, "CalculateSize produces a height");
        assertTrue(aToolTip.mWidth >= Resources.getFontById(Resources.FONT_TINYBOLD_ID)
                .stringWidth("Sunflower") + 10, "width covers the measured title");
    }

    @Test
    void longLabelsWrapIntoMultipleLines() throws InterruptedException {
        appWithFonts();
        ToolTipWidget aToolTip = new ToolTipWidget();
        aToolTip.mMaxLinesWidth = 90; // force a narrow wrap width
        aToolTip.setLabel("The quick brown fox jumps over the lazy dog and keeps on running");

        List<String> aLines = new ArrayList<>();
        aToolTip.getLines(aLines);

        assertTrue(aLines.size() > 1, "the long label wraps into several lines");
        for (String aLine : aLines) {
            assertFalse(aLine.isEmpty(), "wrapped lines are non-empty");
        }
        assertEquals("The quick brown fox jumps over the lazy dog and keeps on running",
                aToolTip.mLabel, "wrapping never mutates the source label");
    }

    @Test
    void explicitNewlinesForceLineBreaks() throws InterruptedException {
        appWithFonts();
        ToolTipWidget aToolTip = new ToolTipWidget();
        aToolTip.mGetsLinesWidth = 1000; // no automatic wrap
        aToolTip.mLabel = "alpha\nbeta\ngamma";

        List<String> aLines = new ArrayList<>();
        aToolTip.getLines(aLines);

        assertEquals(List.of("alpha", "beta", "gamma"), aLines);
    }

    @Test
    void warningFlashCountsDown() {
        ToolTipWidget aToolTip = new ToolTipWidget();
        assertEquals(0, aToolTip.mWarningFlashCounter);

        aToolTip.flashWarning();
        assertEquals(70, aToolTip.mWarningFlashCounter);

        aToolTip.update();
        assertEquals(69, aToolTip.mWarningFlashCounter);

        for (int i = 0; i < 69; i++) {
            aToolTip.update();
        }
        assertEquals(0, aToolTip.mWarningFlashCounter, "never goes negative");
    }

    @Test
    void setPositionStoresCoordinates() {
        ToolTipWidget aToolTip = new ToolTipWidget();
        aToolTip.setPosition(120, 340);
        assertEquals(120, aToolTip.mX);
        assertEquals(340, aToolTip.mY);
    }
}
