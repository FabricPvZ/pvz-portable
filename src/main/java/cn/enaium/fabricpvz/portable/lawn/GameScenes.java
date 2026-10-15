/*
 * Ported from src/ConstEnums.h:481-491.
 *
 * Upstream enum GameScenes (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class GameScenes {
    public static final int SCENE_LOADING = 0;
    public static final int SCENE_MENU = 1;
    public static final int SCENE_LEVEL_INTRO = 2;
    public static final int SCENE_PLAYING = 3;
    public static final int SCENE_ZOMBIES_WON = 4;
    public static final int SCENE_AWARD = 5;
    public static final int SCENE_CREDIT = 6;
    public static final int SCENE_CHALLENGE = 7;

    private GameScenes() {
    }
}
