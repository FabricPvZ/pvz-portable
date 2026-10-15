/*
 * Ported from src/PvzpLib/Definition.h:112-117 (class DefLoadResPath).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/Definition.h:112-117 (class DefLoadResPath).
 *
 * <p>Maps an image prefix to the directory holding its images; the table used by
 * {@code DefinitionLoadImage} (Definition.cpp:172, 317-346) is
 * {@link Definition#gDefLoadResPaths}: {@code "IMAGE_"} maps to {@code ""} and {@code "particles/"},
 * {@code "IMAGE_REANIM_"} to {@code "reanim/"} and {@code "images/"}.
 */
public final class DefLoadResPath {
    /** Upstream {@code mPrefix} (Definition.h:115): image prefix, e.g. {@code "IMAGE_"}. */
    public final String mPrefix;

    /** Upstream {@code mDirectory} (Definition.h:116): directory for images with this prefix. */
    public final String mDirectory;

    public DefLoadResPath(String thePrefix, String theDirectory) {
        mPrefix = thePrefix;
        mDirectory = theDirectory;
    }
}
