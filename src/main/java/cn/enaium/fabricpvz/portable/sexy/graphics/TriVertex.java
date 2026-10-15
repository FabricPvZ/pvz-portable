package cn.enaium.fabricpvz.portable.sexy.graphics;

/**
 * Ported from src/SexyAppFramework/graphics/TriVertex.h:33.
 *
 * <p>Faithful port of {@code Sexy::TriVertex}. The public fields keep their upstream names and
 * types; {@code color} is the upstream {@code uint32_t} ARGB value ({@code 0} means "use the
 * color specified in the function call").
 */
public class TriVertex {
    public float x;
    public float y;
    public float u;
    public float v;
    public int color; // ARGB (0 = use color specified in function call)

    public TriVertex() {
        color = 0;
    }

    public TriVertex(float theX, float theY) {
        x = theX;
        y = theY;
        color = 0;
    }

    public TriVertex(float theX, float theY, float theU, float theV) {
        x = theX;
        y = theY;
        u = theU;
        v = theV;
        color = 0;
    }

    public TriVertex(float theX, float theY, float theU, float theV, int theColor) {
        x = theX;
        y = theY;
        u = theU;
        v = theV;
        color = theColor;
    }
}
