package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.pvzp.DefField;
import cn.enaium.fabricpvz.portable.pvzp.DefFieldType;
import cn.enaium.fabricpvz.portable.pvzp.DefMap;

/**
 * Ported from src/PvzpLib/Definition.cpp:142-163 (gReanimatorTransformDefMap, gReanimatorTrackDefMap,
 * gReanimatorDefMap) and src/PvzpLib/Definition.cpp:242-274 (the three constructor functions).
 *
 * <p>The {@code DefMap} schemas the generic {@code cn.enaium.fabricpvz.portable.pvzp.Definition} loader uses for
 * {@code .reanim} files. Field names are the exact XML element names, member names are the Java
 * fields of {@link ReanimatorTransform}/{@link ReanimatorTrack}/{@link ReanimatorDefinition}.
 *
 * <p>Offsets are the C++ LP64 flat-image layout the compiled cache records (4 bytes per
 * int/float/enum slot, 8 per pointer slot, 16 per array slot: pointer + int32 count + padding);
 * they only feed the cache schema hash and the compiled struct image.
 */
public final class ReanimatorFormats {
    /** Ported from src/PvzpLib/Definition.cpp:142-156 (gReanimatorTransformDefFields). */
    public static final DefMap gReanimatorTransformDefMap = new DefMap(
            new DefField[] {
                new DefField("x", "mTransX", 0, DefFieldType.DT_FLOAT, null),
                new DefField("y", "mTransY", 4, DefFieldType.DT_FLOAT, null),
                new DefField("kx", "mSkewX", 8, DefFieldType.DT_FLOAT, null),
                new DefField("ky", "mSkewY", 12, DefFieldType.DT_FLOAT, null),
                new DefField("sx", "mScaleX", 16, DefFieldType.DT_FLOAT, null),
                new DefField("sy", "mScaleY", 20, DefFieldType.DT_FLOAT, null),
                new DefField("f", "mFrame", 24, DefFieldType.DT_FLOAT, null),
                new DefField("a", "mAlpha", 28, DefFieldType.DT_FLOAT, null),
                new DefField("i", "mImage", 32, DefFieldType.DT_IMAGE, null),
                new DefField("font", "mFont", 40, DefFieldType.DT_FONT, null),
                new DefField("text", "mText", 48, DefFieldType.DT_STRING, null),
            },
            56,
            ReanimatorTransform::new);

    /** Ported from src/PvzpLib/Definition.cpp:158-163 (gReanimatorTrackDefFields). */
    public static final DefMap gReanimatorTrackDefMap = new DefMap(
            new DefField[] {
                new DefField("name", "mName", 0, DefFieldType.DT_STRING, null),
                new DefField("t", "mTransforms", 8, DefFieldType.DT_ARRAY, gReanimatorTransformDefMap),
            },
            24,
            ReanimatorTrack::new);

    /** Ported from src/PvzpLib/Definition.cpp:165-170 (gReanimatorDefFields). */
    public static final DefMap gReanimatorDefMap = new DefMap(
            new DefField[] {
                new DefField("track", "mTracks", 0, DefFieldType.DT_ARRAY, gReanimatorTrackDefMap),
                new DefField("fps", "mFPS", 16, DefFieldType.DT_FLOAT, null),
            },
            20,
            ReanimatorDefinition::new);

    private ReanimatorFormats() {
    }
}
