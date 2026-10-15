package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/PvzpStringFile.h:31
 *
 * <p>Formatting flags stored in {@link PvzpStringListFormat#mFormatFlags} and tested with
 * {@code PvzpCommon.testBit} (upstream {@code TestBit}).
 */
public final class PvzpStringFormatFlag {

    /** Upstream {@code PVZP_FORMAT_IGNORE_NEWLINES} (value 0). */
    public static final int PVZP_FORMAT_IGNORE_NEWLINES = 0;

    /** Upstream {@code PVZP_FORMAT_HIDE_UNTIL_MAGNETSHROOM} (value 1). */
    public static final int PVZP_FORMAT_HIDE_UNTIL_MAGNETSHROOM = 1;

    private PvzpStringFormatFlag() {
    }
}
