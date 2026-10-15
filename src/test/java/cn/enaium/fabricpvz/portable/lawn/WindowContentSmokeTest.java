package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * Regression gate for "the window shows nothing": after the resources finish loading, the frame the
 * application draws into the window target must contain actual content instead of a single flat
 * colour. Every other test asserts logic or draws into offscreen targets, so this is the only place
 * where the final frame the user sees is checked.
 *
 * <p>Runs through SDL's dummy video driver with the software renderer (set by the Gradle test task);
 * {@code texture id 0} is that window surface. {@code ./gradlew dumpWindow} shows the same frames
 * from a real window for visual inspection.
 */
class WindowContentSmokeTest {

    @TempDir
    Path tmp;

    private static int[] readWindow(LawnApp app, int width, int height) {
        int[] pixels = new int[width * height];
        app.mRendererBackend.readTexturePixels(0, width, height, pixels);
        return pixels;
    }

    private static void writePng(Path file, int[] pixels, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static int distinctColours(int[] pixels) {
        Map<Integer, Integer> histogram = new LinkedHashMap<>();
        for (int pixel : pixels) {
            histogram.merge(pixel, 1, Integer::sum);
        }
        return histogram.size();
    }

    @Test
    void windowShowsContentAfterLoading() throws IOException, InterruptedException {
        Optional<Path> resourceRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(resourceRoot.isPresent(), "no real PvZ assets available");

        Path saveDir = Files.createDirectories(tmp.resolve("userdata"));
        Path dumpDir = Path.of(System.getProperty("pvz.window.dumpDir", "build/window-dump"));
        // The entry point installs the resource extractor (upstream main.cpp); do the same here.
        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        try {
            app.setArgs(new String[] {
                "pvz-portable", "-resdir", resourceRoot.get().toString(), "-savedir", saveDir.toString(),
            });
            app.init();
            app.startLoadingThread();

            long deadline = System.nanoTime() + 180_000_000_000L;
            while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
                app.updateFrames();
                Thread.sleep(2);
            }
            assertTrue(app.mLoadingThreadCompleted.get(), "resource loading must complete");

            int width = app.mWidth;
            int height = app.mHeight;

            // Give the title screen / menu time to run its state machine, then look at the window.
            int[] captures = {0, 60, 200, 400, 1200, 3000};
            int ticked = 0;
            int[] lastPixels = null;
            for (int frame : captures) {
                while (ticked < frame) {
                    app.updateFrames();
                    // The frame loop draws after updating; the title screen's state machine only
                    // advances once it has drawn (TitleScreen checks mDrawnYet).
                    app.mWidgetManager.drawScreen();
                    Thread.sleep(5);
                    ticked++;
                }
                lastPixels = readWindow(app, width, height);
                writePng(dumpDir.resolve("frame-" + frame + ".png"), lastPixels, width, height);
                String titleState = "";
                for (var widget : app.mWidgetManager.mWidgets) {
                    if (widget instanceof cn.enaium.fabricpvz.portable.lawn.widget.TitleScreen aTitle) {
                        titleState = " titleState=" + aTitle.mTitleState
                                + " counter=" + aTitle.mTitleStateCounter
                                + " age=" + aTitle.mTitleAge
                                + " drawnYet=" + aTitle.mDrawnYet;
                    }
                }
                System.out.println("[window] frame " + frame + " scene=" + app.mGameScene
                        + " widgets=" + app.mWidgetManager.mWidgets.size()
                        + " distinctColours=" + distinctColours(lastPixels) + titleState);
            }

            // The first frame after loading paints the title screen's background plus its logo, so a
            // single-colour window means drawing never reached the window target.
            assertTrue(distinctColours(lastPixels) > 1,
                    "the window must show more than one colour after loading; a flat window means the "
                            + "frame never reached the window target (see docs/VERIFICATION.md)");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
