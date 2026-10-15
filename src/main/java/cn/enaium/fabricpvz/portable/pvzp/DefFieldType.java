/*
 * Ported from src/PvzpLib/Definition.h:25-37 (enum class DefFieldType : int).
 *
 * Upstream enum DefFieldType (int32); represented as public static final int constants with the
 * upstream enumerator names and values verbatim.
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/Definition.h:25-37 (enum class DefFieldType : int).
 *
 * <p>The storage type of one {@code _MemVar} of a definition class; each type is read differently by
 * {@link Definition}, exactly as upstream {@code DefFieldType} drives {@code DefinitionReadField} and
 * {@code DefMapWriteToCache}.
 */
public final class DefFieldType {
    public static final int DT_INVALID = 0;
    public static final int DT_INT = 1;
    public static final int DT_FLOAT = 2;
    public static final int DT_STRING = 3;
    public static final int DT_ENUM = 4;
    public static final int DT_VECTOR2 = 5;
    public static final int DT_ARRAY = 6;
    public static final int DT_TRACK_FLOAT = 7;
    public static final int DT_FLAGS = 8;
    public static final int DT_IMAGE = 9;
    public static final int DT_FONT = 10;

    private DefFieldType() {
    }

    /**
     * Ported from the LP64 layout of the upstream member types (see {@link DefField#mFieldOffset}).
     *
     * <p>Number of bytes one member of {@code theFieldType} occupies inside the flat compiled
     * struct image:
     * <ul>
     *   <li>{@code DT_INT}/{@code DT_FLOAT}/{@code DT_ENUM}/{@code DT_FLAGS}: 4 ({@code int}/{@code float});</li>
     *   <li>{@code DT_STRING}/{@code DT_IMAGE}/{@code DT_FONT}: 8 (a 64-bit pointer slot);</li>
     *   <li>{@code DT_VECTOR2}: 8 ({@code SexyVector2} = 2 floats);</li>
     *   <li>{@code DT_ARRAY}/{@code DT_TRACK_FLOAT}: 16 ({@code DefinitionArrayDef}/{@code FloatParameterTrack}
     *       = an 8-byte pointer followed by a 4-byte count, padded to 8-byte alignment);</li>
     *   <li>{@code DT_INVALID}: 0 (the upstream field-list terminator).</li>
     * </ul>
     */
    public static int sizeOf(int theFieldType) {
        switch (theFieldType) {
            case DT_INT:
            case DT_FLOAT:
            case DT_ENUM:
            case DT_FLAGS:
                return 4;
            case DT_STRING:
            case DT_IMAGE:
            case DT_FONT:
            case DT_VECTOR2:
                return 8;
            case DT_ARRAY:
            case DT_TRACK_FLOAT:
                return 16;
            default:
                return 0;
        }
    }

    /**
     * Ported from the LP64 alignment of the upstream member types. Pointer-bearing members
     * ({@code DT_STRING}/{@code DT_IMAGE}/{@code DT_FONT}/{@code DT_ARRAY}/{@code DT_TRACK_FLOAT}) are
     * 8-byte aligned, the rest 4-byte aligned.
     */
    public static int alignmentOf(int theFieldType) {
        switch (theFieldType) {
            case DT_STRING:
            case DT_IMAGE:
            case DT_FONT:
            case DT_ARRAY:
            case DT_TRACK_FLOAT:
                return 8;
            default:
                return 4;
        }
    }

    /** Diagnostic name of a field type (used by error messages). */
    public static String nameOf(int theFieldType) {
        switch (theFieldType) {
            case DT_INVALID:
                return "DT_INVALID";
            case DT_INT:
                return "DT_INT";
            case DT_FLOAT:
                return "DT_FLOAT";
            case DT_STRING:
                return "DT_STRING";
            case DT_ENUM:
                return "DT_ENUM";
            case DT_VECTOR2:
                return "DT_VECTOR2";
            case DT_ARRAY:
                return "DT_ARRAY";
            case DT_TRACK_FLOAT:
                return "DT_TRACK_FLOAT";
            case DT_FLAGS:
                return "DT_FLAGS";
            case DT_IMAGE:
                return "DT_IMAGE";
            case DT_FONT:
                return "DT_FONT";
            default:
                return "DT_" + theFieldType;
        }
    }
}
