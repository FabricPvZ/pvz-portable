/*
 * Ported from src/ConstEnums.h:613-623.
 *
 * Upstream enum NotRecommend (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class NotRecommend {
    public static final int NOT_RECOMMENDED_NOCTURNAL = 0;
    public static final int NOT_RECOMMENDED_NEEDS_POOL = 1;
    public static final int NOT_RECOMMENDED_NEEDS_GRAVES = 2;
    public static final int NOT_RECOMMENDED_NEEDS_FOG = 3;
    public static final int NOT_RECOMMENDED_NEEDS_ROOF = 4;
    public static final int NOT_RECOMMENDED_ON_ROOF = 5;
    public static final int NOT_RECOMMENDED_FOR_CHALLENGE = 6;
    public static final int NOT_RECOMMENDED_AT_NIGHT = 7;

    private NotRecommend() {
    }
}
