/*
 * Ported from src/ConstEnums.h:371-378.
 *
 * Upstream enum EmitterType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class EmitterType {
    public static final int EMITTER_CIRCLE = 0;
    public static final int EMITTER_BOX = 1;
    public static final int EMITTER_BOX_PATH = 2;
    public static final int EMITTER_CIRCLE_PATH = 3;
    public static final int EMITTER_CIRCLE_EVEN_SPACING = 4;

    private EmitterType() {
    }
}
