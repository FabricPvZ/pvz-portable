/*
 * Ported from src/Lawn/Widget/GameButton.h:97-102 and src/Lawn/Widget/GameButton.cpp:294-319.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.DialogButton;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/GameButton.h:97-102 (implementation
 * src/Lawn/Widget/GameButton.cpp:294-319).
 *
 * <p>A {@link DialogButton} that draws itself with the stone 3-slice art
 * ({@link GameButton#drawStoneButton}) instead of a component image.
 */
public class LawnStoneButton extends DialogButton {

    // LawnStoneButton::LawnStoneButton (GameButton.h:100)
    public LawnStoneButton(Image theComponentImage, int theId, ButtonListener theListener) {
        super(theComponentImage, theId, theListener);
    }

    // LawnStoneButton::SetLabel (GameButton.cpp:294)
    public void setLabel(String theLabel) {
        mLabel = PvzpStringFile.translate(theLabel);
    }

    // LawnStoneButton::Draw (GameButton.cpp:299)
    @Override
    public void draw(Graphics g) {
        if (mBtnNoDraw) {
            return;
        }

        boolean isDown = (mIsDown && mIsOver && !mDisabled) ^ mInverted;
        GameButton.drawStoneButton(g, 0, 0, mWidth, mHeight, isDown, mIsOver, mLabel);
    }
}
