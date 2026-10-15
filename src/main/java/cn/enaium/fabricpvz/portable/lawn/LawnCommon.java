/*
 * Ported from src/Lawn/LawnCommon.h:57-76 and src/Lawn/LawnCommon.cpp:25-181.
 *
 * Upstream is a set of free functions plus the LawnEditWidget class (ported separately in
 * LawnEditWidget.java); in Java the free functions live on this final static-utility class,
 * keeping the upstream names camelCased (API-CONTRACTS.md §6).
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import cn.enaium.fabricpvz.portable.sexy.Checkbox;
import cn.enaium.fabricpvz.portable.sexy.CheckboxListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.EditListener;
import cn.enaium.fabricpvz.portable.sexy.EditWidget;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/LawnCommon.h:57-76, impl src/Lawn/LawnCommon.cpp:25-181.
 *
 * <p>Range tests, tiled-image drawing, the lawn edit-box/checkbox factories and the save-file name
 * helpers. The colour scheme {@code gLawnEditWidgetColors} (LawnCommon.cpp:25-31) is applied by
 * {@link #createEditWidget(int, EditListener, Dialog)}.
 */
public final class LawnCommon {

    private LawnCommon() {
    }

    /**
     * Ported from src/Lawn/LawnCommon.cpp:37-45 (ModInRange): whether
     * {@code [theNumber - theRange, theNumber + theRange]} contains a multiple of {@code theMod}.
     */
    public static boolean modInRange(int theNumber, int theMod, int theRange) {
        int aRange = Math.abs(theRange);
        for (int i = theNumber - aRange; i <= theNumber + aRange; i++) {
            if (i % theMod == 0) {
                return true;
            }
        }
        return false;
    }

    /** Ported from src/Lawn/LawnCommon.h:68 ({@code theRange = 0} default argument). */
    public static boolean modInRange(int theNumber, int theMod) {
        return modInRange(theNumber, theMod, 0);
    }

    /**
     * Ported from src/Lawn/LawnCommon.cpp:48-51 (GridInRange): whether {@code (x1, y1)} is within
     * {@code (theRangeX, theRangeY)} of {@code (x2, y2)}.
     */
    public static boolean gridInRange(int x1, int y1, int x2, int y2, int theRangeX, int theRangeY) {
        return x1 >= x2 - theRangeX && x1 <= x2 + theRangeX
                && y1 >= y2 - theRangeY && y1 <= y2 + theRangeY;
    }

    /** Ported from src/Lawn/LawnCommon.h:69 ({@code theRangeX = theRangeY = 1} defaults). */
    public static boolean gridInRange(int x1, int y1, int x2, int y2) {
        return gridInRange(x1, y1, x2, y2, 1, 1);
    }

    /** Ported from src/Lawn/LawnCommon.cpp:53-62 (TileImageHorizontally). */
    public static void tileImageHorizontally(Graphics g, Image theImage, int theX, int theY, int theWidth) {
        while (theWidth > 0) {
            int aImageWidth = Math.min(theWidth, theImage.getWidth());
            g.drawImage(theImage, theX, theY, new Rect(0, 0, aImageWidth, theImage.getHeight()));
            theX += aImageWidth;
            theWidth -= aImageWidth;
        }
    }

    /** Ported from src/Lawn/LawnCommon.cpp:64-73 (TileImageVertically). */
    public static void tileImageVertically(Graphics g, Image theImage, int theX, int theY, int theHeight) {
        while (theHeight > 0) {
            int aImageHeight = Math.min(theHeight, theImage.getHeight());
            g.drawImage(theImage, theX, theY, new Rect(0, 0, theImage.getWidth(), aImageHeight));
            theY += aImageHeight;
            theHeight -= aImageHeight;
        }
    }

    /**
     * Ported from src/Lawn/LawnCommon.cpp:139-147 (CreateEditWidget): BrianneTod16 font, the lawn
     * edit colours and a blink delay of 14.
     */
    public static LawnEditWidget createEditWidget(int theId, EditListener theListener, Dialog theDialog) {
        LawnEditWidget aEditWidget = new LawnEditWidget(theId, theListener, theDialog);
        aEditWidget.setFont(Resources.getFontById(Resources.FONT_BRIANNETOD16_ID), null);
        aEditWidget.setColors(new EditWidget.EditWidgetColorScheme(
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, 0),
                new Color(240, 240, 255, 255),
                new Color(255, 255, 255, 255),
                new Color(0, 0, 0, 255)));
        aEditWidget.mBlinkDelay = 14;

        return aEditWidget;
    }

    /** Ported from src/Lawn/LawnCommon.cpp:149-152 (DrawEditBox). */
    public static void drawEditBox(Graphics g, EditWidget theWidget) {
        Rect aDest = new Rect(theWidget.mX - 8, theWidget.mY - 4, theWidget.mWidth + 16, theWidget.mHeight + 8);
        g.drawImageBox(aDest, Resources.getImageById(Resources.IMAGE_EDITBOX_ID));
    }

    /** Ported from src/Lawn/LawnCommon.cpp:154-163 (MakeNewCheckbox). */
    public static Checkbox makeNewCheckbox(int theId, CheckboxListener theListener, boolean theDefault) {
        Checkbox aCheckbox = new Checkbox(
                Resources.getImageById(Resources.IMAGE_OPTIONS_CHECKBOX0_ID),
                Resources.getImageById(Resources.IMAGE_OPTIONS_CHECKBOX1_ID),
                theId,
                theListener);
        aCheckbox.mChecked = theDefault;
        aCheckbox.mHasAlpha = true;
        aCheckbox.mHasTransparencies = true;

        return aCheckbox;
    }

    /** Ported from src/Lawn/LawnCommon.cpp:165-168 (GetSavedGameName): {@code userdata/game{id}_{mode}.v4}. */
    public static String getSavedGameName(int theGameMode, int theProfileId) {
        return Common.getAppDataPath("userdata/game" + theProfileId + "_" + theGameMode + ".v4");
    }

    /** Ported from src/Lawn/LawnCommon.cpp:170-173 (GetLegacySavedGameName): {@code ...dat}. */
    public static String getLegacySavedGameName(int theGameMode, int theProfileId) {
        return Common.getAppDataPath("userdata/game" + theProfileId + "_" + theGameMode + ".dat");
    }

    /**
     * Ported from src/Lawn/LawnCommon.cpp:175-181 (GetCurrentDaysSince2000).
     *
     * <p>Upstream reads the broken-down local time of {@code theTime} ({@code tm_year}/{@code tm_yday});
     * Java derives the same fields from {@link LocalDateTime} in the system zone.
     */
    public static int getCurrentDaysSince2000(long theTime) {
        LocalDateTime aNow = LocalDateTime.ofInstant(Instant.ofEpochSecond(theTime), ZoneId.systemDefault());

        int dy = aNow.getYear() - 2000;
        int yday = aNow.getDayOfYear() - 1;
        return dy * 365 + (dy - 1) / 400 - (dy - 1) / 100 + (dy - 1) / 4 + yday + 1;
    }
}
