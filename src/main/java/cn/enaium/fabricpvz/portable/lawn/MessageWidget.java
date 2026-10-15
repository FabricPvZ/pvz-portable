/*
 * Ported from src/Lawn/MessageWidget.h:24-71 and src/Lawn/MessageWidget.cpp:34-521.
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.nio.charset.StandardCharsets;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/MessageWidget.h:24-71 (implementation
 * src/Lawn/MessageWidget.cpp:34-521).
 *
 * <p>The on-board advice/announcement text widget owned by {@link Board} ({@code mAdvice}). It is
 * upstream a plain class (not a {@code Widget}); {@code Board} calls {@code Update}/{@code Draw}
 * from its own pipeline. A message has a {@link MessageStyle} that selects the position, colour,
 * pulsing alpha and font; "stay" styles never count down. When a reanimated style is active the text
 * is one {@link Reanimation} per UTF-8 code point.
 */
public class MessageWidget {

    // MessageWidget.h:29-30
    public static final int MAX_MESSAGE_LENGTH = 128;
    public static final int MAX_REANIM_LINES = 5;

    // MessageWidget.h:34-48
    public LawnApp mApp;
    public String mLabel = "";
    public int mDisplayTime;
    public int mDuration;
    public int mMessageStyle;
    public int[] mTextReanimID = new int[MAX_MESSAGE_LENGTH];
    public int[] mTextReanimByteOffset = new int[MAX_MESSAGE_LENGTH];
    public int mTextReanimCount;
    public int mReanimType;
    public int mSlideOffTime;
    public String mLabelNext = "";
    public int mMessageStyleNext;

    // MessageWidget::MessageWidget (MessageWidget.cpp:34)
    public MessageWidget(LawnApp theApp) {
        mApp = theApp;
        mDuration = 0;
        mDisplayTime = 0;
        mLabel = "";
        mMessageStyle = MessageStyle.MESSAGE_STYLE_OFF;
        mLabelNext = "";
        mMessageStyleNext = MessageStyle.MESSAGE_STYLE_OFF;
        mSlideOffTime = 100;
        mReanimType = ReanimationType.REANIM_NONE;
        mTextReanimCount = 0;
        for (int i = 0; i < MAX_MESSAGE_LENGTH; i++) {
            mTextReanimID[i] = ReanimationID.REANIMATIONID_NULL;
            mTextReanimByteOffset[i] = 0;
        }
    }

    // MessageWidget::ClearReanim (MessageWidget.cpp:53)
    public void clearReanim() {
        for (int i = 0; i < MAX_MESSAGE_LENGTH; i++) {
            Reanimation aReanim = mApp.reanimationTryToGet(mTextReanimID[i]);
            if (aReanim != null) {
                aReanim.reanimationDie();
                mTextReanimID[i] = ReanimationID.REANIMATIONID_NULL;
            }
        }
    }

    // MessageWidget::ClearLabel (MessageWidget.cpp:66)
    public void clearLabel() {
        if (mReanimType != ReanimationType.REANIM_NONE) {
            mDuration = Math.min(mDuration, 100 + mSlideOffTime + 1);
        } else {
            mDuration = 0;
        }
    }

    /** MessageWidget.cpp:74 (TruncateLabel). Enforces the buffer/line limits in one place. */
    private static String truncateLabel(String theLabel) {
        byte[] aBytes = theLabel.getBytes(StandardCharsets.UTF_8);
        int aBytePos = 0;
        int aLineCount = 1;
        while (aBytePos < aBytes.length) {
            int[] aNext = {aBytePos};
            int aChar = Common.utf8DecodeNext(aBytes, aNext);
            if (aChar < 0) {
                return new String(aBytes, 0, aBytePos, StandardCharsets.UTF_8); // drop from invalid byte on
            }
            if (aNext[0] > MAX_MESSAGE_LENGTH - 1
                    || (aChar == '\n' && aLineCount == MAX_REANIM_LINES)) {
                return new String(aBytes, 0, aBytePos, StandardCharsets.UTF_8);
            }
            if (aChar == '\n') {
                aLineCount++;
            }
            aBytePos = aNext[0];
        }
        return theLabel;
    }

    // MessageWidget::SetLabel (MessageWidget.cpp:104)
    public void setLabel(String theNewLabel, int theMessageStyle) {
        String aLabel = truncateLabel(PvzpStringFile.translate(theNewLabel));

        if (mReanimType != ReanimationType.REANIM_NONE && mDuration > 0) {
            mMessageStyleNext = theMessageStyle;
            mLabelNext = aLabel;
            clearLabel();
        } else {
            clearReanim();
            mLabel = aLabel;
            mMessageStyle = theMessageStyle;
            mReanimType = ReanimationType.REANIM_NONE;

            switch (theMessageStyle) {
                case MessageStyle.MESSAGE_STYLE_HINT_LONG:
                case MessageStyle.MESSAGE_STYLE_BIG_MIDDLE:
                case MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG:
                case MessageStyle.MESSAGE_STYLE_HINT_TALL_LONG:
                    mDuration = 1500;
                    break;

                case MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE:
                    mDuration = 500;
                    break;

                case MessageStyle.MESSAGE_STYLE_HINT_FAST:
                case MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST:
                case MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST:
                case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1:
                case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2:
                case MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER:
                    mDuration = 500;
                    break;

                case MessageStyle.MESSAGE_STYLE_HINT_STAY:
                case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY:
                case MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER_STAY:
                    mDuration = 10000;
                    break;

                case MessageStyle.MESSAGE_STYLE_HOUSE_NAME:
                    mDuration = 250;
                    break;

                case MessageStyle.MESSAGE_STYLE_HUGE_WAVE:
                    mDuration = 750;
                    mReanimType = ReanimationType.REANIM_TEXT_FADE_ON;
                    break;

                case MessageStyle.MESSAGE_STYLE_SLOT_MACHINE:
                    mDuration = 750;
                    break;

                case MessageStyle.MESSAGE_STYLE_ACHIEVEMENT:
                    mDuration = 250;
                    break;

                default:
                    throw new IllegalStateException("MessageWidget::SetLabel: unhandled style "
                            + theMessageStyle);
            }

            if (mReanimType != ReanimationType.REANIM_NONE) {
                layoutReanimText();
            }
            mDisplayTime = mDuration;
        }
    }

    // MessageWidget::LayoutReanimText (MessageWidget.cpp:180)
    public void layoutReanimText() {
        float aMaxWidth = 0;
        int aCurLine = 0;
        int aCurPos = 0;
        Font aFont = getFont();
        byte[] aLabelBytes = mLabel.getBytes(StandardCharsets.UTF_8);
        int aLabelLen = aLabelBytes.length;
        mSlideOffTime = aLabelLen + 100;

        float[] aLineWidth = new float[MAX_REANIM_LINES];
        for (int aPos = 0; aPos <= aLabelLen; aPos++) {
            if (aPos == aLabelLen || aLabelBytes[aPos] == '\n') {
                int aLen = aPos - aCurPos;
                int aOff = aCurPos;
                aCurPos = aPos + 1;
                String aLine = new String(aLabelBytes, aOff, aLen, StandardCharsets.UTF_8);

                aLineWidth[aCurLine] = aFont.stringWidth(aLine);
                aMaxWidth = Math.max(aMaxWidth, aLineWidth[aCurLine]);
                aCurLine++;
            }
        }

        aCurLine = 0;
        float aCurPosY = 0.0f;
        float aCurPosX = -aLineWidth[0] * 0.5f;
        // Iterate by code point so each reanimated glyph corresponds to one UTF-8 character.
        int aCharIdx = 0;
        int aBytePos = 0;
        while (aBytePos < aLabelLen) {
            int aCharStart = aBytePos;
            int[] aNext = {aBytePos};
            int aChar = Common.utf8DecodeNext(aLabelBytes, aNext);
            if (aChar < 0) {
                break;
            }
            aBytePos = aNext[0];

            Reanimation aReanimText = mApp.addReanimation(aCurPosX, aCurPosY, 0, mReanimType);
            aReanimText.mIsAttachment = true;
            aReanimText.playReanim("anim_enter", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 0.0f);
            mTextReanimID[aCharIdx] = mApp.reanimationGetID(aReanimText);
            mTextReanimByteOffset[aCharIdx] = aCharStart;

            aCurPosX += aFont.charWidth(aChar);
            if (aChar == '\n') {
                aCurLine++;
                aCurPosX = -aLineWidth[aCurLine] * 0.5f;
                aCurPosY += aFont.getLineSpacing();
            }
            aCharIdx++;
        }
        mTextReanimCount = aCharIdx;
    }

    // MessageWidget::Update (MessageWidget.cpp:238)
    public void update() {
        if (mApp.mBoard == null || mApp.mBoard.mPaused) {
            return;
        }

        // count down the remaining time and switch to the next message
        if (mDuration < 10000 && mDuration > 0) {
            mDuration--;
            if (mDuration == 0) {
                mMessageStyle = MessageStyle.MESSAGE_STYLE_OFF;
                if (mMessageStyleNext != MessageStyle.MESSAGE_STYLE_OFF) {
                    setLabel(mLabelNext, mMessageStyleNext);
                    mMessageStyleNext = MessageStyle.MESSAGE_STYLE_OFF;
                }
            }
        }

        // Iterate reanimated glyphs by code-point index.
        for (int aCharIdx = 0; aCharIdx < mTextReanimCount; aCharIdx++) {
            Reanimation aTextReanim = mApp.reanimationTryToGet(mTextReanimID[aCharIdx]);
            if (aTextReanim == null) {
                break;
            }

            int aTextSpeed = mReanimType == ReanimationType.REANIM_TEXT_FADE_ON ? 100 : 1;
            if (mDuration > mSlideOffTime) {
                if (mReanimType == ReanimationType.REANIM_TEXT_FADE_ON) {
                    aTextReanim.mAnimRate = 60.0f;
                } else {
                    aTextReanim.mAnimRate = PvzpCommon.pvzpAnimateCurveFloat(0, 50,
                            (mDisplayTime - mDuration) * aTextSpeed - aCharIdx, 0.0f, 40.0f,
                            PvzpCurves.CURVE_LINEAR);
                }
            } else {
                if (mDuration == mSlideOffTime) {
                    aTextReanim.playReanim("anim_leave", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0,
                            0.0f);
                }
                aTextReanim.mAnimRate = PvzpCommon.pvzpAnimateCurveFloat(0, 50,
                        (mSlideOffTime - mDuration) * aTextSpeed - aCharIdx, 0.0f, 40.0f,
                        PvzpCurves.CURVE_LINEAR);
            }

            aTextReanim.update();
        }
    }

    // MessageWidget::DrawReanimatedText (MessageWidget.cpp:292)
    public void drawReanimatedText(Graphics g, Font theFont, Color theColor, float thePosY) {
        byte[] aLabelBytes = mLabel.getBytes(StandardCharsets.UTF_8);
        for (int aCharIdx = 0; aCharIdx < mTextReanimCount; aCharIdx++) {
            Reanimation aTextReanim = mApp.reanimationTryToGet(mTextReanimID[aCharIdx]);
            if (aTextReanim == null) {
                break;
            }

            ReanimatorTransform aTransform = new ReanimatorTransform();
            aTextReanim.getCurrentTransform(2, aTransform);

            int anAlpha = PvzpCommon.floatRoundToInt(theColor.mAlpha * aTransform.mAlpha);
            anAlpha = Math.max(0, Math.min(255, anAlpha));
            if (anAlpha <= 0) {
                break;
            }
            Color aFinalColor = new Color(theColor.mRed, theColor.mGreen, theColor.mBlue, anAlpha);

            aTransform.mTransX += aTextReanim.mOverlayMatrix.m02;
            aTransform.mTransY += aTextReanim.mOverlayMatrix.m12 + thePosY - GameConstants.BOARD_HEIGHT / 2;
            if (mReanimType == ReanimationType.REANIM_TEXT_FADE_ON
                    && mDisplayTime - mDuration < mSlideOffTime) {
                float aStretch = 1.0f - aTextReanim.mAnimTime;
                aTransform.mTransX += aStretch * aTextReanim.mOverlayMatrix.m02;
            }

            SexyMatrix3 aMatrix = new SexyMatrix3();
            Reanimation.matrixFromTransform(aTransform, aMatrix);
            int aByteStart = mTextReanimByteOffset[aCharIdx];
            int aByteEnd = (aCharIdx + 1 < mTextReanimCount)
                    ? mTextReanimByteOffset[aCharIdx + 1] : aLabelBytes.length;
            String aLetter = new String(aLabelBytes, aByteStart, aByteEnd - aByteStart,
                    StandardCharsets.UTF_8);
            PvzpCommon.pvzpDrawStringMatrix(g, theFont, aMatrix, aLetter, aFinalColor);
        }
    }

    // MessageWidget::GetFont (MessageWidget.cpp:330)
    public Font getFont() {
        switch (mMessageStyle) {
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1:
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY:
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2:
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER:
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER_STAY:
            case MessageStyle.MESSAGE_STYLE_HINT_LONG:
            case MessageStyle.MESSAGE_STYLE_HINT_FAST:
            case MessageStyle.MESSAGE_STYLE_HINT_STAY:
            case MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST:
            case MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE:
            case MessageStyle.MESSAGE_STYLE_HINT_TALL_LONG:
            case MessageStyle.MESSAGE_STYLE_BIG_MIDDLE:
            case MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST:
            case MessageStyle.MESSAGE_STYLE_HOUSE_NAME:
            case MessageStyle.MESSAGE_STYLE_HUGE_WAVE:
            case MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG:
            case MessageStyle.MESSAGE_STYLE_ACHIEVEMENT:
                return Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID);

            case MessageStyle.MESSAGE_STYLE_SLOT_MACHINE:
                return Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID);
            case MessageStyle.MESSAGE_STYLE_OFF:
                break;
            default:
                break;
        }

        // src/Lawn/MessageWidget.cpp:356-358 — the switch above handles every MessageStyle, so
        // upstream's trailing `PVZP_ASSERT(false); unreachable();` is genuinely unreachable. It is
        // reproduced as a defensive assertion-style throw rather than an unimplemented feature.
        throw new IllegalStateException("MessageWidget::GetFont: unhandled style " + mMessageStyle);
    }

    // MessageWidget::Draw (MessageWidget.cpp:363)
    public void draw(Graphics g) {
        if (mDuration <= 0) {
            return;
        }

        Font aFont = getFont();
        Font aOutlineFont = null;
        int aPosX = GameConstants.BOARD_WIDTH / 2;
        int aPosY = 596;
        int aTextOffsetY = 0;
        int aRectHeight = 0;
        int aMinAlpha = 255;
        Color aColor = new Color(250, 250, 0, 255);
        Color aOutlineColor = new Color(0, 0, 0, 255);
        boolean aFadeOut = false;
        if (aFont == Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID)) {
            aOutlineFont = Resources.getFontById(Resources.FONT_CONTINUUMBOLD14OUTLINE_ID);
        }

        switch (mMessageStyle) {
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1:
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY:
                aPosY = 400;
                aRectHeight = 110;
                aTextOffsetY = -4;
                aColor = new Color(253, 245, 173);
                aMinAlpha = 192;
                break;

            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2:
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER:
            case MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER_STAY:
            case MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST:
            case MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE:
            case MessageStyle.MESSAGE_STYLE_HINT_TALL_LONG:
            case MessageStyle.MESSAGE_STYLE_ACHIEVEMENT:
                aPosY = 476;
                aRectHeight = 100;
                aTextOffsetY = -4;
                aColor = new Color(253, 245, 173);
                aMinAlpha = 192;
                break;

            case MessageStyle.MESSAGE_STYLE_HINT_LONG:
            case MessageStyle.MESSAGE_STYLE_HINT_FAST:
            case MessageStyle.MESSAGE_STYLE_HINT_STAY:
                aPosY = 527;
                aRectHeight = 55;
                aTextOffsetY = -4;
                aColor = new Color(253, 245, 173);
                aMinAlpha = 192;
                break;

            case MessageStyle.MESSAGE_STYLE_BIG_MIDDLE:
            case MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST:
                aPosY = 300;
                aRectHeight = 110;
                aColor = new Color(253, 245, 173);
                aMinAlpha = 192;
                break;

            case MessageStyle.MESSAGE_STYLE_HOUSE_NAME:
                aPosY = 550;
                aColor = new Color(255, 255, 255, 255);
                aFadeOut = true;
                break;

            case MessageStyle.MESSAGE_STYLE_HUGE_WAVE:
                aPosY = 330;
                aColor = new Color(255, 0, 0);
                break;

            case MessageStyle.MESSAGE_STYLE_SLOT_MACHINE:
                aPosY = 93;
                aPosX = 340;
                aMinAlpha = 64;
                break;

            case MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG:
                aPosY = 514;
                aRectHeight = 55;
                aTextOffsetY = -4;
                aColor = new Color(253, 245, 173);
                aMinAlpha = 192;
                break;

            default:
                throw new IllegalStateException("MessageWidget::Draw: unhandled style "
                        + mMessageStyle);
        }

        if (mReanimType != ReanimationType.REANIM_NONE) {
            if (aFont == Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID)) {
                drawReanimatedText(g, Resources.getFontById(Resources.FONT_CONTINUUMBOLD14OUTLINE_ID),
                        Color.Black, aPosY);
            }
            drawReanimatedText(g, aFont, aColor, aPosY);
        } else {
            if (aMinAlpha != 255) {
                aColor.mAlpha = PvzpCommon.pvzpAnimateCurve(75, 0, mApp.mBoard.mMainCounter % 75,
                        aMinAlpha, 255, PvzpCurves.CURVE_BOUNCE_SLOW_MIDDLE);
                aOutlineColor.mAlpha = aColor.mAlpha;
            }
            if (aFadeOut) {
                aColor.mAlpha = Math.max(0, Math.min(255, mDuration * 15));
                aOutlineColor.mAlpha = aColor.mAlpha;
            }

            if (aRectHeight > 0) {
                aOutlineColor = new Color(0, 0, 0, 128);
                Rect aRect = new Rect(0, aPosY, GameConstants.BOARD_WIDTH, aRectHeight);
                g.setColor(aOutlineColor);
                g.fillRect(aRect);

                aRect.mY += aTextOffsetY;
                PvzpStringFile.pvzpDrawStringWrapped(g, mLabel, aRect, aFont, aColor,
                        DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
            } else {
                Rect aRect = new Rect(aPosX - mApp.mBoard.mX - GameConstants.BOARD_WIDTH / 2,
                        aPosY - aFont.mAscent, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
                if (aOutlineFont != null) {
                    PvzpStringFile.pvzpDrawStringWrapped(g, mLabel, aRect, aOutlineFont, aOutlineColor,
                            DrawStringJustification.DS_ALIGN_CENTER);
                }
                PvzpStringFile.pvzpDrawStringWrapped(g, mLabel, aRect, aFont, aColor,
                        DrawStringJustification.DS_ALIGN_CENTER);
            }

            if (mMessageStyle == MessageStyle.MESSAGE_STYLE_HOUSE_NAME) {
                String aSubStr = "";
                if (mApp.isSurvivalMode() && mApp.mBoard.mChallenge.mSurvivalStage > 0) {
                    int aFlags = mApp.mBoard.getNumWavesPerSurvivalStage()
                            * mApp.mBoard.mChallenge.mSurvivalStage
                            / mApp.mBoard.getNumWavesPerFlag();
                    String aFlagStr = LawnApp.pluralize(aFlags, "[ONE_FLAG]", "[COUNT_FLAGS]");
                    aSubStr = PvzpCommon.pvzpReplaceString("[FLAGS_COMPLETED]", "{FLAGS}", aFlagStr);
                }

                if (aSubStr.length() > 0) {
                    PvzpCommon.pvzpDrawString(g, aSubStr,
                            GameConstants.BOARD_WIDTH / 2 - mApp.mBoard.mX, aPosY + 26,
                            Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID),
                            new Color(224, 187, 62, aColor.mAlpha),
                            DrawStringJustification.DS_ALIGN_CENTER);
                }
            }
        }
    }
}
