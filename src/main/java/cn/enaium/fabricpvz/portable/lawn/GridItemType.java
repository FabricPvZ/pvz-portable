/*
 * Ported from src/ConstEnums.h:499-514.
 *
 * Upstream enum GridItemType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class GridItemType {
    public static final int GRIDITEM_NONE = 0;
    public static final int GRIDITEM_GRAVESTONE = 1;
    public static final int GRIDITEM_CRATER = 2;
    public static final int GRIDITEM_LADDER = 3;
    public static final int GRIDITEM_PORTAL_CIRCLE = 4;
    public static final int GRIDITEM_PORTAL_SQUARE = 5;
    public static final int GRIDITEM_BRAIN = 6;
    public static final int GRIDITEM_SCARY_POT = 7;
    public static final int GRIDITEM_SQUIRREL = 8;
    public static final int GRIDITEM_ZEN_TOOL = 9;
    public static final int GRIDITEM_STINKY = 10;
    public static final int GRIDITEM_RAKE = 11;
    public static final int GRIDITEM_IZOMBIE_BRAIN = 12;

    private GridItemType() {
    }
}
