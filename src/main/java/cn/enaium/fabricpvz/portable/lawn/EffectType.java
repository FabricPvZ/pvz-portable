/*
 * Ported from src/ConstEnums.h:363-370.
 *
 * Upstream enum EffectType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class EffectType {
    public static final int EFFECT_PARTICLE = 0;
    public static final int EFFECT_TRAIL = 1;
    public static final int EFFECT_REANIM = 2;
    public static final int EFFECT_ATTACHMENT = 3;
    public static final int EFFECT_OTHER = 4;

    private EffectType() {
    }
}
