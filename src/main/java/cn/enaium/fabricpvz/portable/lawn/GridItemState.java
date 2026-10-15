/*
 * Ported from src/ConstEnums.h:515-547.
 *
 * Upstream enum GridItemState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class GridItemState {
    public static final int GRIDITEM_STATE_NORMAL = 0;
    public static final int GRIDITEM_STATE_GRAVESTONE_SPECIAL = 1;
    public static final int GRIDITEM_STATE_PORTAL_CLOSED = 2;
    public static final int GRIDITEM_STATE_SCARY_POT_QUESTION = 3;
    public static final int GRIDITEM_STATE_SCARY_POT_LEAF = 4;
    public static final int GRIDITEM_STATE_SCARY_POT_ZOMBIE = 5;
    public static final int GRIDITEM_STATE_SQUIRREL_WAITING = 6;
    public static final int GRIDITEM_STATE_SQUIRREL_PEEKING = 7;
    public static final int GRIDITEM_STATE_SQUIRREL_RUNNING_UP = 8;
    public static final int GRIDITEM_STATE_SQUIRREL_RUNNING_DOWN = 9;
    public static final int GRIDITEM_STATE_SQUIRREL_RUNNING_LEFT = 10;
    public static final int GRIDITEM_STATE_SQUIRREL_RUNNING_RIGHT = 11;
    public static final int GRIDITEM_STATE_SQUIRREL_CAUGHT = 12;
    public static final int GRIDITEM_STATE_SQUIRREL_ZOMBIE = 13;
    public static final int GRIDITEM_STATE_ZEN_TOOL_WATERING_CAN = 14;
    public static final int GRIDITEM_STATE_ZEN_TOOL_FERTILIZER = 15;
    public static final int GRIDITEM_STATE_ZEN_TOOL_BUG_SPRAY = 16;
    public static final int GRIDITEM_STATE_ZEN_TOOL_PHONOGRAPH = 17;
    public static final int GRIDITEM_STATE_ZEN_TOOL_GOLD_WATERING_CAN = 18;
    public static final int GRIDITEM_STINKY_WALKING_LEFT = 19;
    public static final int GRIDITEM_STINKY_TURNING_LEFT = 20;
    public static final int GRIDITEM_STINKY_WALKING_RIGHT = 21;
    public static final int GRIDITEM_STINKY_TURNING_RIGHT = 22;
    public static final int GRIDITEM_STINKY_SLEEPING = 23;
    public static final int GRIDITEM_STINKY_FALLING_ASLEEP = 24;
    public static final int GRIDITEM_STINKY_WAKING_UP = 25;
    public static final int GRIDITEM_STATE_RAKE_ATTRACTING = 26;
    public static final int GRIDITEM_STATE_RAKE_WAITING = 27;
    public static final int GRIDITEM_STATE_RAKE_TRIGGERED = 28;
    public static final int GRIDITEM_STATE_BRAIN_SQUISHED = 29;

    private GridItemState() {
    }
}
