package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import org.lwjgl.sdl.SDLKeycode;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;

/**
 * Ported from src/SexyAppFramework/platform/default/Input.cpp:180-254 ({@code SDLKeyToKeyCode}).
 *
 * <p>Translates an SDL key code into the framework's Windows-VK-compatible {@link KeyCode} values,
 * which the game uses for hotkeys and cheat codes. Both the Dvorak-independent letter range and the
 * keypad/function-key mappings are preserved exactly.
 */
public final class SdlKeys {

    private SdlKeys() {
    }

    public static int toKeyCode(int sdlKey) {
        if (sdlKey >= SDLKeycode.SDLK_A && sdlKey <= SDLKeycode.SDLK_Z) {
            return 'A' + (sdlKey - SDLKeycode.SDLK_A);
        }
        if (sdlKey >= SDLKeycode.SDLK_0 && sdlKey <= SDLKeycode.SDLK_9) {
            return sdlKey;
        }
        return switch (sdlKey) {
            case SDLKeycode.SDLK_BACKSPACE -> KeyCode.KEYCODE_BACK;
            case SDLKeycode.SDLK_TAB -> KeyCode.KEYCODE_TAB;
            case SDLKeycode.SDLK_CLEAR -> KeyCode.KEYCODE_CLEAR;
            case SDLKeycode.SDLK_RETURN -> KeyCode.KEYCODE_RETURN;
            case SDLKeycode.SDLK_ESCAPE -> KeyCode.KEYCODE_ESCAPE;
            case SDLKeycode.SDLK_SPACE -> KeyCode.KEYCODE_SPACE;
            case SDLKeycode.SDLK_DELETE -> KeyCode.KEYCODE_DELETE;

            case SDLKeycode.SDLK_LEFT -> KeyCode.KEYCODE_LEFT;
            case SDLKeycode.SDLK_UP -> KeyCode.KEYCODE_UP;
            case SDLKeycode.SDLK_RIGHT -> KeyCode.KEYCODE_RIGHT;
            case SDLKeycode.SDLK_DOWN -> KeyCode.KEYCODE_DOWN;

            case SDLKeycode.SDLK_INSERT -> KeyCode.KEYCODE_INSERT;
            case SDLKeycode.SDLK_HOME -> KeyCode.KEYCODE_HOME;
            case SDLKeycode.SDLK_END -> KeyCode.KEYCODE_END;
            case SDLKeycode.SDLK_PAGEUP -> KeyCode.KEYCODE_PRIOR;
            case SDLKeycode.SDLK_PAGEDOWN -> KeyCode.KEYCODE_NEXT;

            case SDLKeycode.SDLK_LSHIFT, SDLKeycode.SDLK_RSHIFT -> KeyCode.KEYCODE_SHIFT;
            case SDLKeycode.SDLK_LCTRL, SDLKeycode.SDLK_RCTRL -> KeyCode.KEYCODE_CONTROL;
            case SDLKeycode.SDLK_LALT, SDLKeycode.SDLK_RALT -> KeyCode.KEYCODE_MENU;
            case SDLKeycode.SDLK_LGUI, SDLKeycode.SDLK_RGUI -> KeyCode.KEYCODE_LWIN;
            case SDLKeycode.SDLK_PAUSE -> KeyCode.KEYCODE_PAUSE;
            case SDLKeycode.SDLK_CAPSLOCK -> KeyCode.KEYCODE_CAPITAL;
            case SDLKeycode.SDLK_NUMLOCKCLEAR -> KeyCode.KEYCODE_NUMLOCK;
            case SDLKeycode.SDLK_SCROLLLOCK -> KeyCode.KEYCODE_SCROLL;

            case SDLKeycode.SDLK_KP_0 -> KeyCode.KEYCODE_NUMPAD0;
            case SDLKeycode.SDLK_KP_1 -> KeyCode.KEYCODE_NUMPAD1;
            case SDLKeycode.SDLK_KP_2 -> KeyCode.KEYCODE_NUMPAD2;
            case SDLKeycode.SDLK_KP_3 -> KeyCode.KEYCODE_NUMPAD3;
            case SDLKeycode.SDLK_KP_4 -> KeyCode.KEYCODE_NUMPAD4;
            case SDLKeycode.SDLK_KP_5 -> KeyCode.KEYCODE_NUMPAD5;
            case SDLKeycode.SDLK_KP_6 -> KeyCode.KEYCODE_NUMPAD6;
            case SDLKeycode.SDLK_KP_7 -> KeyCode.KEYCODE_NUMPAD7;
            case SDLKeycode.SDLK_KP_8 -> KeyCode.KEYCODE_NUMPAD8;
            case SDLKeycode.SDLK_KP_9 -> KeyCode.KEYCODE_NUMPAD9;
            case SDLKeycode.SDLK_KP_MULTIPLY -> KeyCode.KEYCODE_MULTIPLY;
            case SDLKeycode.SDLK_KP_PLUS -> KeyCode.KEYCODE_ADD;
            case SDLKeycode.SDLK_KP_MINUS -> KeyCode.KEYCODE_SUBTRACT;
            case SDLKeycode.SDLK_KP_PERIOD -> KeyCode.KEYCODE_DECIMAL;
            case SDLKeycode.SDLK_KP_DIVIDE -> KeyCode.KEYCODE_DIVIDE;
            case SDLKeycode.SDLK_KP_ENTER -> KeyCode.KEYCODE_RETURN;

            case SDLKeycode.SDLK_F1 -> KeyCode.KEYCODE_F1;
            case SDLKeycode.SDLK_F2 -> KeyCode.KEYCODE_F2;
            case SDLKeycode.SDLK_F3 -> KeyCode.KEYCODE_F3;
            case SDLKeycode.SDLK_F4 -> KeyCode.KEYCODE_F4;
            case SDLKeycode.SDLK_F5 -> KeyCode.KEYCODE_F5;
            case SDLKeycode.SDLK_F6 -> KeyCode.KEYCODE_F6;
            case SDLKeycode.SDLK_F7 -> KeyCode.KEYCODE_F7;
            case SDLKeycode.SDLK_F8 -> KeyCode.KEYCODE_F8;
            case SDLKeycode.SDLK_F9 -> KeyCode.KEYCODE_F9;
            case SDLKeycode.SDLK_F10 -> KeyCode.KEYCODE_F10;
            case SDLKeycode.SDLK_F11 -> KeyCode.KEYCODE_F11;
            case SDLKeycode.SDLK_F12 -> KeyCode.KEYCODE_F12;

            default -> KeyCode.KEYCODE_UNKNOWN;
        };
    }
}
