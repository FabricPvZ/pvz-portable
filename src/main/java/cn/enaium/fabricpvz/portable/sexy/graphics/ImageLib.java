package cn.enaium.fabricpvz.portable.sexy.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Ported from src/SexyAppFramework/imagelib/ImageLib.h:25 and src/SexyAppFramework/imagelib/ImageLib.cpp.
 *
 * <p>Extension-keyed image decoder registry ({@code png}, {@code jpg}, {@code gif}, {@code tga};
 * upstream {@code kImageExts}, ImageLib.cpp:1125-1131). Decoding resolves bytes through
 * {@link PakInterface} and produces {@link Image} pixels as {@code 0xAARRGGBB} words.
 *
 * <p>PORT-NOTE: upstream links libpng/libjpeg and carries two self-contained GIF/TGA readers.
 * This port uses {@code javax.imageio.ImageIO} for PNG/JPEG/GIF and keeps a hand-written
 * uncompressed 32-bit TGA reader; the writers exist for debugging/screenshots, mirroring upstream
 * {@code WriteJPEGImage}/{@code WritePNGImage}/{@code WriteTGAImage}. Alpha handling matches upstream:
 * PNG keeps its alpha (palettized PNG is expanded to RGBA), while JPEG and GIF are forced opaque
 * ({@code 0xFF}) because upstream's GIF decoder builds its palette with {@code 0xFF000000}
 * (ImageLib.cpp:425/446).
 */
public final class ImageLib {

    /** Ported from src/SexyAppFramework/imagelib/ImageLib.h:33-46 (implementation ImageLib.cpp:45-72). */
    public static final class Image {
        public int mWidth;
        public int mHeight;
        /** Pixel words {@code 0xAARRGGBB}, length {@code mWidth * mHeight}. */
        public int[] mBits;

        public Image(int theWidth, int theHeight, int[] theBits) {
            this.mWidth = theWidth;
            this.mHeight = theHeight;
            this.mBits = theBits;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }

        public int[] getBits() {
            return mBits;
        }
    }

    /** Upstream ImageLib.cpp:1121. */
    public static int gAlphaComposeColor = 0xFFFFFF;
    /** Upstream ImageLib.cpp:1122. */
    public static boolean gAutoLoadAlpha = true;
    /** Upstream ImageLib.cpp:1123 (declared for API compatibility; no JPEG2000 decoder exists). */
    public static boolean gIgnoreJPEG2000Alpha = true;

    /** Upstream {@code kImageExts}, ImageLib.cpp:1125-1131, in registry order. */
    private static final String[] EXTENSIONS = {".png", ".jpg", ".gif", ".tga"};

    private ImageLib() {
    }

    /** Upstream ImageLib.h:59 default argument: {@code lookForAlphaImage = true}. */
    public static Image getImage(String theFilename) {
        return getImage(theFilename, true);
    }

    /** Ported from ImageLib.cpp:1254-1311. */
    public static Image getImage(String theFilename, boolean lookForAlphaImage) {
        PakInterface pak = PakInterface.getInstance();
        if (!gAutoLoadAlpha) {
            lookForAlphaImage = false;
        }

        int lastSlash = theFilename.lastIndexOf('/');
        int lastDot = theFilename.lastIndexOf('.');

        String ext = "";
        String base;
        if (lastDot != -1 && (lastSlash == -1 || lastDot > lastSlash)) {
            ext = theFilename.substring(lastDot);
            base = theFilename.substring(0, lastDot);
        } else {
            base = theFilename;
        }

        Image anImage = tryLoadByExt(pak, base, ext);

        Image anAlphaImage = null;
        if (lookForAlphaImage) {
            int slashEnd = lastSlash != -1 ? lastSlash + 1 : 0;
            String alphaPath1 = theFilename.substring(0, slashEnd) + "_" + theFilename.substring(slashEnd);
            if (fastFileExists(pak, alphaPath1)) {
                anAlphaImage = getImage(alphaPath1, false);
            }

            if (anAlphaImage == null) {
                String alphaPath2 = theFilename + "_";
                if (fastFileExists(pak, alphaPath2)) {
                    anAlphaImage = getImage(alphaPath2, false);
                }
            }
        }

        if (anAlphaImage != null) {
            if (anImage != null) {
                composeAlpha(anImage, anAlphaImage);
            } else {
                anImage = anAlphaImage;
                applyAlphaAsImage(anImage, gAlphaComposeColor);
            }
        }

        return anImage;
    }

    /** Upstream {@code TryLoadByExt}, ImageLib.cpp:1218-1229. */
    private static Image tryLoadByExt(PakInterface pak, String theBaseName, String theExt) {
        for (String knownExt : EXTENSIONS) {
            if (theExt.isEmpty() || theExt.equalsIgnoreCase(knownExt)) {
                String fileName = theBaseName + knownExt;
                byte[] bytes = readResource(pak, fileName);
                if (bytes != null) {
                    Image image = decode(fileName, bytes);
                    if (image != null) {
                        return image;
                    }
                }
            }
        }
        return null;
    }

    private static Image decode(String fileName, byte[] bytes) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tga")) {
            return decodeTGA(bytes);
        }
        boolean forceOpaque = lower.endsWith(".jpg") || lower.endsWith(".gif");
        return decodeViaImageIO(bytes, forceOpaque);
    }

    private static Image decodeViaImageIO(byte[] bytes, boolean forceOpaque) {
        BufferedImage buffered;
        try {
            buffered = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
        if (buffered == null) {
            return null;
        }

        int width = buffered.getWidth();
        int height = buffered.getHeight();
        int[] bits = buffered.getRGB(0, 0, width, height, null, 0, width);
        if (forceOpaque) {
            for (int i = 0; i < bits.length; i++) {
                bits[i] |= 0xFF000000;
            }
        }
        return new Image(width, height, bits);
    }

    /** Ported from ImageLib.cpp:182-264: uncompressed 32-bit BGRA, top-left origin only. */
    private static Image decodeTGA(byte[] bytes) {
        if (bytes.length < 18) {
            return null;
        }

        // The 18-byte header is read field by field; like upstream, the optional image-ID field
        // and colour-map entries are not skipped.
        int bitCount = bytes[16] & 0xFF;
        int imageDescriptor = bytes[17] & 0xFF;
        if (bitCount != 32 || imageDescriptor != (8 | (1 << 5))) {
            return null;
        }

        int width = le16(bytes, 12);
        int height = le16(bytes, 14);
        int pixelCount = width * height;
        int[] bits = new int[pixelCount];

        int pos = 18;
        for (int i = 0; i < pixelCount; i++) {
            if (pos + 4 > bytes.length) {
                break; // truncated file: remaining pixels stay 0x00000000 (upstream zero-init)
            }
            int b = bytes[pos] & 0xFF;
            int g = bytes[pos + 1] & 0xFF;
            int r = bytes[pos + 2] & 0xFF;
            int a = bytes[pos + 3] & 0xFF;
            bits[i] = (a << 24) | (r << 16) | (g << 8) | b;
            pos += 4;
        }
        return new Image(width, height, bits);
    }

    /** Upstream {@code ComposeAlpha}, ImageLib.cpp:1231-1243. */
    private static void composeAlpha(Image theImage, Image theAlphaImage) {
        if (theImage.mWidth != theAlphaImage.mWidth || theImage.mHeight != theAlphaImage.mHeight) {
            return;
        }

        int size = theImage.mWidth * theImage.mHeight;
        for (int i = 0; i < size; i++) {
            theImage.mBits[i] = (theImage.mBits[i] & 0x00FFFFFF) | ((theAlphaImage.mBits[i] & 0xFF) << 24);
        }
    }

    /** Upstream {@code ApplyAlphaAsImage}, ImageLib.cpp:1245-1252. */
    private static void applyAlphaAsImage(Image theImage, int theBaseColor) {
        int size = theImage.mWidth * theImage.mHeight;
        for (int i = 0; i < size; i++) {
            theImage.mBits[i] = theBaseColor | ((theImage.mBits[i] & 0xFF) << 24);
        }
    }

    /** Upstream {@code FastFileExists}, ImageLib.cpp:1191-1216 (extension probing for bare names). */
    private static boolean fastFileExists(PakInterface pak, String thePath) {
        if (thePath.isEmpty()) {
            return false;
        }
        if (hasExtension(thePath)) {
            return pakExists(pak, thePath);
        }
        for (String ext : EXTENSIONS) {
            if (pakExists(pak, thePath + ext)) {
                return true;
            }
        }
        return false;
    }

    /** Mirror of {@code std::filesystem::path::has_extension}. */
    private static boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.lastIndexOf('.') > 0;
    }

    /** Upstream {@code CheckSinglePath}, ImageLib.cpp:1152-1189. */
    private static boolean pakExists(PakInterface pak, String path) {
        try {
            return pak.exists(path);
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] readResource(PakInterface pak, String fileName) {
        try {
            return pak.readResource(fileName);
        } catch (IOException e) {
            return null;
        }
    }

    private static int le16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /** Upstream ImageLib.cpp:797-869 (writes RGBA, matching the reader's 0xAARRGGBB order). */
    public static boolean writePNGImage(String theFileName, Image theImage) {
        if (theImage == null) {
            return false;
        }
        BufferedImage buffered = toBufferedImage(theImage, BufferedImage.TYPE_INT_ARGB);
        try {
            return ImageIO.write(buffered, "png", Path.of(theFileName).toFile());
        } catch (IOException e) {
            return false;
        }
    }

    /** Upstream ImageLib.cpp:722-795 (RGB only, quality 80, alpha dropped). */
    public static boolean writeJPEGImage(String theFileName, Image theImage) {
        if (theImage == null) {
            return false;
        }
        BufferedImage buffered = toBufferedImage(theImage, BufferedImage.TYPE_INT_RGB);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return false;
        }
        ImageWriter writer = writers.next();
        try (OutputStream out = Files.newOutputStream(Path.of(theFileName));
                ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            if (imageOut == null) {
                return false;
            }
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.8f);
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(buffered, null, null), param);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            writer.dispose();
        }
    }

    /** Upstream ImageLib.cpp:872-931 (uncompressed 32-bit BGRA, top-left origin). */
    public static boolean writeTGAImage(String theFileName, Image theImage) {
        if (theImage == null) {
            return false;
        }
        int width = theImage.mWidth;
        int height = theImage.mHeight;

        ByteArrayOutputStream out = new ByteArrayOutputStream(18 + width * height * 4);
        out.write(0); // id length
        out.write(0); // colour map type
        out.write(2); // image type: uncompressed true-colour
        writeLe16(out, 0); // first entry index
        writeLe16(out, 0); // colour map length
        out.write(0); // colour map entry size
        writeLe16(out, 0); // x origin
        writeLe16(out, 0); // y origin
        writeLe16(out, width);
        writeLe16(out, height);
        out.write(32); // bit count
        out.write(8 | (1 << 5)); // descriptor: 8 alpha bits | top-left origin

        for (int i = 0; i < width * height; i++) {
            int word = theImage.mBits[i];
            out.write(word & 0xFF); // blue
            out.write((word >> 8) & 0xFF); // green
            out.write((word >> 16) & 0xFF); // red
            out.write((word >> 24) & 0xFF); // alpha
        }

        try {
            Files.write(Path.of(theFileName), out.toByteArray());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static BufferedImage toBufferedImage(Image theImage, int type) {
        int width = theImage.mWidth;
        int height = theImage.mHeight;
        BufferedImage buffered = new BufferedImage(width, height, type);
        buffered.setRGB(0, 0, width, height, theImage.mBits, 0, width);
        return buffered;
    }

    private static void writeLe16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
