package cn.enaium.fabricpvz.portable.sexy.graphics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.PakWriter;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/** Verifies the ported {@link ImageLib} decoders, alpha-companion rules and writers. */
class ImageLibTest {

    @TempDir
    Path tmp;

    @AfterEach
    void resetGlobalState() throws IOException {
        PakInterface.getInstance().reset();
        ImageLib.gAutoLoadAlpha = true;
        ImageLib.gAlphaComposeColor = 0xFFFFFF;
    }

    private PakInterface mount(Map<String, byte[]> members) throws IOException {
        PakInterface pak = PakInterface.getInstance();
        pak.setResourceFolder(tmp.toString());
        Path pakFile = PakWriter.write(tmp.resolve("images.pak"), members);
        assertTrue(pak.addPakFile(pakFile), "fixture pak must mount");
        return pak;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out), "no ImageIO writer for " + format);
        return out.toByteArray();
    }

    /**
     * Encodes a GIF that ImageIO will also read back losslessly.
     *
     * <p>Java's GIF writer defaults to the progressive mode, which sets the interlace flag in the
     * image descriptor and makes ImageIO's GIF reader mis-place rows of interlaced frames. A
     * non-interlaced GIF is the ordinary case that the port (and upstream's GetGIFImage,
     * ImageLib.cpp:498-660) decodes in raster order, so the fixture asks for
     * {@link ImageWriteParam#MODE_DISABLED} explicitly.
     */
    private static byte[] encodePlainGif(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream sink = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(sink);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage argb(int width, int height, int[] pixels) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    @Test
    void decodesPngFromPakWithExactArgb() throws IOException {
        int[] pixels = {
            0xFFFF0000, 0x8000FF00, 0xFF0000FF,
            0x00000000, 0xFFFFFFFF, 0x01020304,
        };
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("images/known.png", encode(argb(3, 2, pixels), "png"));
        mount(members);

        ImageLib.Image image = ImageLib.getImage("images/known.png");

        assertNotNull(image);
        assertEquals(3, image.getWidth());
        assertEquals(2, image.getHeight());
        assertArrayEquals(pixels, image.getBits(), "PNG round-trip must preserve RGBA exactly");
    }

    @Test
    void decodesGifAndForcesOpaqueAlpha() throws IOException {
        int[] pixels = {
            0xFF102030, 0xFF405060,
            0xFF70A0B0, 0xFFC0D0E0,
        };
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("images/known.gif", encodePlainGif(argb(2, 2, pixels)));
        mount(members);

        ImageLib.Image image = ImageLib.getImage("images/known.gif");

        assertNotNull(image);
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        for (int i = 0; i < pixels.length; i++) {
            assertEquals(0xFF000000, image.getBits()[i] & 0xFF000000, "GIF alpha must be forced opaque");
            assertEquals(pixels[i] & 0x00FFFFFF, image.getBits()[i] & 0x00FFFFFF, "GIF colour " + i);
        }
    }

    @Test
    void decodesJpegWithinTolerance() throws IOException {
        int rgb = 0x336699;
        BufferedImage source = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                source.setRGB(x, y, rgb);
            }
        }
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("images/solid.jpg", encode(source, "jpeg"));
        mount(members);

        ImageLib.Image image = ImageLib.getImage("images/solid.jpg");

        assertNotNull(image);
        assertEquals(4, image.getWidth());
        assertEquals(3, image.getHeight());
        int tolerance = 8;
        for (int pixel : image.getBits()) {
            assertEquals(0xFF000000, pixel & 0xFF000000, "JPEG alpha must be opaque");
            assertTrue(Math.abs(((pixel >> 16) & 0xFF) - 0x33) <= tolerance, "red");
            assertTrue(Math.abs(((pixel >> 8) & 0xFF) - 0x66) <= tolerance, "green");
            assertTrue(Math.abs((pixel & 0xFF) - 0x99) <= tolerance, "blue");
        }
    }

    @Test
    void decodesUncompressedTopLeftTgaAndRejects24Bit() throws IOException {
        int[] pixels = {
            0xFFFF0000, 0x8000FF00,
            0x00000000, 0x01020304,
        };
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("images/known.tga", tga32(2, 2, pixels));
        members.put("images/bad24.tga", tga24(2, 2));
        mount(members);

        ImageLib.Image image = ImageLib.getImage("images/known.tga");

        assertNotNull(image);
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        assertArrayEquals(pixels, image.getBits(), "TGA BGRA little-endian must decode to 0xAARRGGBB");

        assertNull(ImageLib.getImage("images/bad24.tga"), "24-bit TGA is not supported");
    }

    @Test
    void returnsNullForMissingUndecodableAndUnknownExtensions() throws IOException {
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("images/broken.png", "not an image".getBytes(StandardCharsets.ISO_8859_1));
        members.put("images/picture.bmp", new byte[] {1, 2, 3, 4});
        mount(members);

        assertNull(ImageLib.getImage("images/broken.png"), "undecodable PNG data");
        assertNull(ImageLib.getImage("images/picture.bmp"), "BMP is not a registered extension");
        assertNull(ImageLib.getImage("images/missing.png"), "missing file");
    }

    @Test
    void loadsRegisteredExtensionCaseInsensitively() throws IOException {
        int[] pixels = {0xFFAABBCC, 0xFF0F1E2D};
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("case/photo.png", encode(argb(2, 1, pixels), "png"));
        mount(members);

        ImageLib.Image image = ImageLib.getImage("case/photo.PNG");

        assertNotNull(image, "extension case must not matter");
        assertArrayEquals(pixels, image.getBits());
    }

    @Test
    void composesAlphaCompanionAndAppliesAlphaAsImage() throws IOException {
        int[] base = {0xFF112233, 0xFF445566};
        int[] alphaCompanion = {0x00000080, 0x00000040};
        int[] alphaOnly = {0x0000007F};

        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("img/base.png", encode(argb(2, 1, base), "png"));
        members.put("img/_base.png", encode(argb(2, 1, alphaCompanion), "png"));
        members.put("img/only_.png", encode(argb(1, 1, alphaOnly), "png"));
        mount(members);

        // alphaPath1: img/_base.png replaces the base alpha byte with the companion's low byte.
        ImageLib.Image composed = ImageLib.getImage("img/base.png");
        assertNotNull(composed);
        assertArrayEquals(new int[] {0x80112233, 0x40445566}, composed.getBits(), "ComposeAlpha low-byte rule");

        // gAutoLoadAlpha == false disables the probing for that call.
        ImageLib.gAutoLoadAlpha = false;
        ImageLib.Image untouched = ImageLib.getImage("img/base.png");
        assertNotNull(untouched);
        assertArrayEquals(base, untouched.getBits(), "probing disabled keeps the source alpha");
        ImageLib.gAutoLoadAlpha = true;

        // alphaPath2: img/only_.png has no base image, so ApplyAlphaAsImage paints gAlphaComposeColor.
        ImageLib.gAlphaComposeColor = 0x0000FF;
        ImageLib.Image applied = ImageLib.getImage("img/only");
        assertNotNull(applied);
        assertArrayEquals(new int[] {0x7F0000FF}, applied.getBits(), "ApplyAlphaAsImage uses gAlphaComposeColor");
    }

    @Test
    void writersRoundTripThroughGetImage() throws IOException {
        int[] pixels = {0xFFFF0000, 0x8000FF00, 0x00000000, 0xFF102030};
        ImageLib.Image image = new ImageLib.Image(2, 2, pixels.clone());
        PakInterface.getInstance().setResourceFolder(tmp.toString());

        assertTrue(ImageLib.writePNGImage(tmp.resolve("rt.png").toString(), image));
        assertArrayEquals(pixels, reload("rt.png").getBits());

        assertTrue(ImageLib.writeTGAImage(tmp.resolve("rt.tga").toString(), image));
        assertArrayEquals(pixels, reload("rt.tga").getBits());

        assertTrue(ImageLib.writeJPEGImage(tmp.resolve("rt.jpg").toString(), image));
        ImageLib.Image jpeg = reload("rt.jpg");
        assertEquals(2, jpeg.getWidth());
        assertEquals(2, jpeg.getHeight());
        assertEquals(0xFF000000, jpeg.getBits()[0] & 0xFF000000, "JPEG drops alpha");
    }

    @Test
    void decodesRealPakPngWithMatchingIhdrSize() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "real PvZ assets not available");

        PakInterface pak = PakInterface.getInstance();
        pak.setResourceFolder(root.get().toString());
        assertTrue(pak.addPakFile(root.get().resolve("main.pak")), "main.pak must mount");

        List<String> names = pak.resourceNames();
        String member = names.stream().filter(name -> name.endsWith(".PNG")).findFirst().orElse(null);
        Assumptions.assumeTrue(member != null, "main.pak contains no PNG image");

        byte[] bytes = pak.requireResource(member);
        int ihdrWidth = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
        int ihdrHeight = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);

        ImageLib.Image image = ImageLib.getImage(member);

        assertNotNull(image, "must decode " + member);
        assertEquals(ihdrWidth, image.getWidth(), "decoded width must match PNG IHDR");
        assertEquals(ihdrHeight, image.getHeight(), "decoded height must match PNG IHDR");
    }

    /**
     * The intro cutscene draws {@code IMAGE_PVZ_LOGO} over the lawn; the art is a JPEG
     * ({@code images/PvZ_Logo.jpg}) whose transparency lives in the {@code _}-suffixed companion
     * ({@code images/PvZ_Logo_.png}). If the companion is not composed, the logo is blitted as an
     * opaque black rectangle - which is how the intro looked before this test existed.
     */
    @Test
    void composesRealLogoAlphaCompanion() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "real PvZ assets not available");

        PakInterface pak = PakInterface.getInstance();
        pak.setResourceFolder(root.get().toString());
        assertTrue(pak.addPakFile(root.get().resolve("main.pak")), "main.pak must mount");

        List<String> names = pak.resourceNames();
        Assumptions.assumeTrue(names.stream().anyMatch(n -> n.equalsIgnoreCase("images/PvZ_Logo.jpg")),
                "main.pak has no PvZ_Logo.jpg");
        Assumptions.assumeTrue(names.stream().anyMatch(n -> n.equalsIgnoreCase("images/PvZ_Logo_.png")),
                "main.pak has no PvZ_Logo_ alpha companion");

        ImageLib.Image logo = ImageLib.getImage("images/PvZ_Logo");
        assertNotNull(logo, "must decode images/PvZ_Logo");
        assertEquals(700, logo.getWidth(), "images/PvZ_Logo.jpg is 700x116");
        assertEquals(116, logo.getHeight());

        int[] bits = logo.getBits();
        long transparent = 0;
        for (int pixel : bits) {
            if ((pixel >>> 24) == 0) {
                transparent++;
            }
        }
        long semi = 0;
        for (int pixel : bits) {
            int a = pixel >>> 24;
            if (a > 0 && a < 255) {
                semi++;
            }
        }
        System.out.println("[logo] pixels=" + bits.length + " transparent=" + transparent + " semi=" + semi
                + " opaque=" + (bits.length - transparent - semi) + " corner=" + Integer.toHexString(bits[0]));
        assertTrue(transparent > bits.length / 10,
                "the composed logo must keep the companion's transparency, but only " + transparent + " of "
                        + bits.length + " pixels are fully transparent (corner="
                        + Integer.toHexString(bits[0]) + ")");
    }

    private static ImageLib.Image reload(String resourceName) {
        ImageLib.Image image = ImageLib.getImage(resourceName);
        assertNotNull(image, "round-trip must decode " + resourceName);
        return image;
    }

    /** Builds an uncompressed 32-bit top-left-origin TGA byte array (BGRA little-endian). */
    private static byte[] tga32(int width, int height, int[] pixels) {
        byte[] data = new byte[18 + width * height * 4];
        writeTgaHeader(data, width, height, 32);
        int pos = 18;
        for (int pixel : pixels) {
            data[pos++] = (byte) (pixel & 0xFF);
            data[pos++] = (byte) ((pixel >> 8) & 0xFF);
            data[pos++] = (byte) ((pixel >> 16) & 0xFF);
            data[pos++] = (byte) ((pixel >> 24) & 0xFF);
        }
        return data;
    }

    /** Builds a 24-bit TGA header plus data, which the reader must reject. */
    private static byte[] tga24(int width, int height) {
        byte[] data = new byte[18 + width * height * 3];
        writeTgaHeader(data, width, height, 24);
        return data;
    }

    private static void writeTgaHeader(byte[] data, int width, int height, int bitCount) {
        data[2] = 2; // uncompressed true-colour
        data[12] = (byte) (width & 0xFF);
        data[13] = (byte) ((width >> 8) & 0xFF);
        data[14] = (byte) (height & 0xFF);
        data[15] = (byte) ((height >> 8) & 0xFF);
        data[16] = (byte) bitCount;
        data[17] = (byte) (8 | (1 << 5)); // 8 alpha bits | top-left origin
    }
}
