/*
 * Ported from src/PvzpLib/Definition.cpp:37-171 (the field tables plus the LP64 layout of the
 * upstream definition structs; the C++ tables use offsetof(), which Java must compute).
 */
package cn.enaium.fabricpvz.portable.pvzp;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from src/PvzpLib/Definition.cpp:37-171 (the {@code gXxxDefFields}/{@code gXxxDefMap}
 * tables). Upstream lays a definition class out with {@code offsetof(T, member)} and
 * {@code sizeof(T)}; Java has neither, so this builder places the fields sequentially using the LP64
 * sizes and alignments of the upstream member types (see {@link DefFieldType#sizeOf(int)} and
 * {@link DefFieldType#alignmentOf(int)}) and derives {@link DefMap#mDefSize} from the result.
 *
 * <p>The offsets it produces are the ones {@link Definition} writes into / reads from the flat struct
 * image of a compiled cache entry and the ones hashed into the schema hash, so a map must either be
 * built here or declare offsets with exactly these rules.
 *
 * <p>Usage:
 * <pre>{@code
 * public static final DefMap gReanimatorDefMap = new DefMapBuilder()
 *         .field("track", "mTracks", DefFieldType.DT_ARRAY, gReanimatorTrackDefMap)
 *         .field("fps", "mFPS", DefFieldType.DT_FLOAT)
 *         .build(ReanimatorDefinition::new);
 * }</pre>
 */
public final class DefMapBuilder {
    private final List<DefField> mFields = new ArrayList<>();
    private int mOffset;

    public DefMapBuilder field(String theFieldName, String theMemberName, int theFieldType) {
        return field(theFieldName, theMemberName, theFieldType, null);
    }

    /**
     * Appends one member. {@code theExtraData} is the pointee {@link DefMap} for
     * {@code DT_ARRAY}/{@code DT_TRACK_FLOAT} fields and the {@link DefSymbol} array for
     * {@code DT_ENUM}/{@code DT_FLAGS} fields (upstream Definition.h:61-63).
     */
    public DefMapBuilder field(String theFieldName, String theMemberName, int theFieldType, Object theExtraData) {
        int anAlignment = DefFieldType.alignmentOf(theFieldType);
        mOffset = align(mOffset, anAlignment);
        mFields.add(new DefField(theFieldName, theMemberName, mOffset, theFieldType, theExtraData));
        mOffset += DefFieldType.sizeOf(theFieldType);
        return this;
    }

    /** Offset one past the last declared member (already aligned), i.e. {@code sizeof(_DefClass)}. */
    public int defSize() {
        return align(mOffset, 8);
    }

    public DefField[] fields() {
        return mFields.toArray(new DefField[0]);
    }

    /** Builds the map; {@code theConstructorFunc} is the definition class' default constructor. */
    public DefMap build(DefConstructor theConstructorFunc) {
        return new DefMap(fields(), defSize(), theConstructorFunc);
    }

    private static int align(int theOffset, int theAlignment) {
        return (theOffset + theAlignment - 1) / theAlignment * theAlignment;
    }
}
