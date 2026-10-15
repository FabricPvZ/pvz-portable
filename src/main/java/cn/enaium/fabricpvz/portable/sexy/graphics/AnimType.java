package cn.enaium.fabricpvz.portable.sexy.graphics;

/**
 * Ported from src/SexyAppFramework/graphics/Image.h:44.
 *
 * <p>Faithful port of {@code Sexy::AnimType}. The constant names are the upstream C++ names
 * verbatim ({@code AnimType_None} etc.) because game code and resource metadata compare against
 * them; the numeric order is part of the behaviour of {@link AnimInfo#compute(int, int, int)}.
 */
public enum AnimType {
    AnimType_None,
    AnimType_Once,
    AnimType_PingPong,
    AnimType_Loop
}
