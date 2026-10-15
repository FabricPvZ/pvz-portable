/*
 * Ported from src/ConstEnums.h:975-994.
 *
 * Upstream enum RenderLayer (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class RenderLayer {
    public static final int RENDER_LAYER_ROW_OFFSET = 10000;
    public static final int RENDER_LAYER_UI_BOTTOM = 100000;
    public static final int RENDER_LAYER_GROUND = 200000;
    public static final int RENDER_LAYER_LAWN = 300000;
    public static final int RENDER_LAYER_GRAVE_STONE = 301000;
    public static final int RENDER_LAYER_PLANT = 302000;
    public static final int RENDER_LAYER_ZOMBIE = 303000;
    public static final int RENDER_LAYER_BOSS = 304000;
    public static final int RENDER_LAYER_PROJECTILE = 305000;
    public static final int RENDER_LAYER_LAWN_MOWER = 306000;
    public static final int RENDER_LAYER_PARTICLE = 307000;
    public static final int RENDER_LAYER_TOP = 400000;
    public static final int RENDER_LAYER_FOG = 500000;
    public static final int RENDER_LAYER_COIN_BANK = 600000;
    public static final int RENDER_LAYER_UI_TOP = 700000;
    public static final int RENDER_LAYER_ABOVE_UI = 800000;
    public static final int RENDER_LAYER_SCREEN_FADE = 900000;

    private RenderLayer() {
    }
}
