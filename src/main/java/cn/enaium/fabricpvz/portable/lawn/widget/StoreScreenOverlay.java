/*
 * Ported from src/Lawn/Widget/StoreScreen.h:112-125 and src/Lawn/Widget/StoreScreen.cpp:72-80.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Widget/StoreScreen.h:112-125 (implementation
 * src/Lawn/Widget/StoreScreen.cpp:72-80).
 *
 * <p>An invisible full-screen widget whose only job is to draw {@link StoreScreen}'s coin overlay on
 * top of the dialog chrome.
 */
public class StoreScreenOverlay extends Widget {

    // StoreScreen.h:114
    public StoreScreen mParent;

    // StoreScreenOverlay::StoreScreenOverlay (StoreScreen.cpp:72)
    public StoreScreenOverlay(StoreScreen theParent) {
        mParent = theParent;
        mMouseVisible = false;
        mHasAlpha = true;
    }

    // StoreScreenOverlay::Draw (StoreScreen.cpp:78)
    @Override
    public void draw(Graphics g) {
        mParent.drawOverlay(g);
    }
}
