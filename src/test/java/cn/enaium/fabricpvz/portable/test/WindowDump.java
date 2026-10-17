package cn.enaium.fabricpvz.portable.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Main;

/**
 * Boots the game with the real video driver, drives the frame loop and writes what the window shows
 * into {@code build/window-dump/} (run through {@code ./gradlew dumpWindow}), together with the
 * logical, canvas and drawable sizes each frame.
 *
 * <p>Two things matter for a dependable dump. The widget tree is drawn with
 * {@code WidgetManager.drawScreen()} and read back <em>before</em> {@code SDL_RenderPresent}: after a
 * present the GPU back-buffer is undefined, so reading it then is meaningless (SDL returns a blank
 * surface on Metal). And every read-back happens on the frame-loop thread: SDL's renderer is not
 * thread safe and reading from another thread aborts the process inside Metal's command encoder.
 *
 * <p>Each frame is written twice, because the two are different images on a high-density window:
 * {@code frame-NNN.png} is the canvas the game drew into ({@code readTexturePixels(0, ..)}, 800x600
 * logical pixels) and {@code window-NNN.png} is what the window actually shows ({@code
 * readPresentedPixels(..)}, the drawable size) with the canvas composited into it. On a 1:1 window
 * (drawable == canvas) both are the same image.
 */
public final class WindowDump {

    private WindowDump() {
    }

    public static void main(String[] args) throws Exception {
        Path dumpDir = Path.of("build", "window-dump").toAbsolutePath();
        Path saveDir = dumpDir.resolve("userdata");
        Files.createDirectories(saveDir);

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        try {
            app.setArgs(new String[] {"pvz-portable", "-savedir", saveDir.toString()});
            app.init();
            app.startLoadingThread();

            int frames = 30;
            for (int frame = 1; frame <= frames; frame++) {
                long frameEnd = System.nanoTime() + 500_000_000L;
                while (System.nanoTime() < frameEnd) {
                    // Same order as the main loop: input/window events, update, draw.
                    app.processDeferredMessages(false);
                    app.doUpdateFrames();
                    app.mWidgetManager.drawScreen();
                    Thread.sleep(5);
                }

                if (app.mRendererBackend == null) {
                    System.out.println("[dump] frame " + frame + " renderer not ready");
                    continue;
                }
                int width = app.mRendererBackend.renderOutputWidth();
                int height = app.mRendererBackend.renderOutputHeight();
                int[] pixels = new int[width * height];
                try {
                    app.mRendererBackend.readTexturePixels(0, width, height, pixels);
                } catch (Throwable e) {
                    System.out.println("[dump] frame " + frame + " readback failed: " + e);
                    continue;
                }
                writePng(dumpDir.resolve(String.format("frame-%03d.png", frame)), pixels, width, height);

                // What the window shows: the same moment in the frame, but read from the window drawable
                // (with the canvas composited into it) instead of from the canvas itself.
                int[] windowPixels = new int[0];
                int windowWidth = -1;
                int windowHeight = -1;
                if (app.mRendererBackend instanceof cn.enaium.fabricpvz.portable.pvzp.platform.sdl.SdlRenderer aSdl
                        && aSdl.presentedWidth() > 0 && aSdl.presentedHeight() > 0) {
                    windowWidth = aSdl.presentedWidth();
                    windowHeight = aSdl.presentedHeight();
                    windowPixels = new int[windowWidth * windowHeight];
                    aSdl.readPresentedPixels(windowWidth, windowHeight, windowPixels);
                    writePng(dumpDir.resolve(String.format("window-%03d.png", frame)), windowPixels,
                            windowWidth, windowHeight);
                }
                app.mRendererBackend.present();
                if (app.mRendererBackend instanceof cn.enaium.fabricpvz.portable.pvzp.platform.sdl.SdlRenderer aPresented
                        && !aPresented.lastPresentSucceeded()) {
                    // A present SDL refused is invisible everywhere else - the window simply keeps showing
                    // what it showed - so the harness that exists to say what the window shows has to
                    // refuse to pass. (The read-back above happens before the present on purpose: after a
                    // present the GPU back buffer is undefined and Metal hands back a blank surface.)
                    throw new IllegalStateException("SDL refused the present on frame " + frame);
                }

                System.out.println("[dump] frame " + frame
                        + " appLogical=" + app.mWidth + "x" + app.mHeight
                        + " canvas=" + width + "x" + height
                        + " window=" + windowWidth + "x" + windowHeight
                        + " present=" + java.util.Arrays.toString(app.mRendererBackend.logicalPresentationRect())
                        + " scene=" + sceneName(app.mGameScene)
                        + " widgets=" + app.mWidgetManager.mWidgets.size()
                        + " canvas_colours=" + describe(pixels)
                        + " window_colours=" + describe(windowPixels));
            }
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    private static void writePng(Path file, int[] pixels, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static String describe(int[] pixels) {
        Map<Integer, Integer> histogram = new LinkedHashMap<>();
        for (int pixel : pixels) {
            histogram.merge(pixel, 1, Integer::sum);
        }
        StringBuilder topColors = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            if (shown++ < 4) {
                topColors.append(String.format("#%08X=%d ", entry.getKey(), entry.getValue()));
            }
        }
        return "distinct=" + histogram.size() + " colours=" + topColors;
    }

    private static String sceneName(int theScene) {
        return switch (theScene) {
            case GameScenes.SCENE_LOADING -> "LOADING";
            case GameScenes.SCENE_MENU -> "MENU";
            case GameScenes.SCENE_LEVEL_INTRO -> "LEVEL_INTRO";
            case GameScenes.SCENE_PLAYING -> "PLAYING";
            case GameScenes.SCENE_ZOMBIES_WON -> "ZOMBIES_WON";
            case GameScenes.SCENE_AWARD -> "AWARD";
            case GameScenes.SCENE_CREDIT -> "CREDIT";
            case GameScenes.SCENE_CHALLENGE -> "CHALLENGE";
            default -> "?" + theScene;
        };
    }
}
