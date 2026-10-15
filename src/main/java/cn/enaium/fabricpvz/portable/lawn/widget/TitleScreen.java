/*
 * Ported from src/Lawn/Widget/TitleScreen.h:33-100 and TitleScreen.cpp:39-552.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.HyperlinkWidget;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/TitleScreen.h:48-100 and TitleScreen.cpp:39-552.
 *
 * <p>The boot/loading screen: shows the PopCap logo, drops the PvZ logo over the title art and
 * animates the grass loading bar loaded by the background loading thread. When loading completes the
 * button flips to {@code [CLICK_TO_START]} and any click advances to the game selector.
 */
public class TitleScreen extends Widget implements ButtonListener {

    // TitleScreen.h:51-54
    public static final int TitleScreen_Start = 0;
    public static final int TitleScreen_Register = 1;

    // TitleScreen.h:33-39 (enum TitleState)
    public static final int TITLESTATE_WAITING_FOR_FIRST_DRAW = 0;
    public static final int TITLESTATE_POPCAP_LOGO = 1;
    public static final int TITLESTATE_PARTNER_LOGO = 2;
    public static final int TITLESTATE_SCREEN = 3;

    public HyperlinkWidget mStartButton;
    public float mCurBarWidth;
    public float mTotalBarWidth;
    public float mBarVel;
    public float mBarStartProgress;
    public boolean mRegisterClicked;
    public boolean mLoadingThreadComplete;
    public int mTitleAge;
    public int mQuickLoadKey;
    public boolean mNeedRegister;
    public boolean mNeedShowRegisterBox;
    public boolean mDrawnYet;
    public boolean mNeedToInit;
    public float mPrevLoadingPercent;
    public int mTitleState;
    public int mTitleStateCounter;
    public int mTitleStateDuration;
    public boolean mDisplayPartnerLogo;
    public volatile boolean mLoaderScreenIsLoaded;
    public LawnApp mApp;

    // TitleScreen::TitleScreen (TitleScreen.cpp:39-67)
    public TitleScreen(LawnApp theApp) {
        mCurBarWidth = 0.0f;
        mTotalBarWidth = 314.0f;
        mBarVel = 0.2f;
        mBarStartProgress = 0.0f;
        mPrevLoadingPercent = 0.0f;
        mApp = theApp;
        mTitleAge = 0;
        mNeedRegister = false;
        mRegisterClicked = false;
        mNeedShowRegisterBox = false;
        mLoadingThreadComplete = false;
        mDrawnYet = false;
        mNeedToInit = true;
        mQuickLoadKey = KeyCode.KEYCODE_UNKNOWN;
        mDisplayPartnerLogo = mApp.getBoolean("DisplayPartnerLogo", false);
        mTitleState = TITLESTATE_WAITING_FOR_FIRST_DRAW;
        mTitleStateDuration = 0;
        mTitleStateCounter = 0;
        mLoaderScreenIsLoaded = false;

        mStartButton = new HyperlinkWidget(0, this);
        mStartButton.mColor = new Color(218, 184, 33);
        mStartButton.mOverColor = new Color(250, 90, 15);
        mStartButton.mUnderlineSize = 0;
        mStartButton.mDisabled = true;
        mStartButton.mVisible = false;
    }

    /** Ported from TitleScreen::DrawToPreload (TitleScreen.cpp:80-83). */
    public void drawToPreload(Graphics g) {
        if (mApp.getImage(Resources.IMAGE_PLANTSHADOW_ID) != null) {
            g.drawImageF(mApp.getImage(Resources.IMAGE_PLANTSHADOW_ID), 1000.0f, 0.0f);
        }
    }

    // TitleScreen::Draw (TitleScreen.cpp:85-210)
    @Override
    public void draw(Graphics g) {
        g.setLinearBlend(true);

        if (mTitleState == TITLESTATE_WAITING_FOR_FIRST_DRAW) {
            g.setColor(Color.Black);
            g.fillRect(0, 0, mWidth, mHeight);

            if (!mDrawnYet) {
                PvzpDebug.pvzpLogLn("First Draw Time: {} ms", System.currentTimeMillis() - mApp.mTimeLoaded);
                mDrawnYet = true;
            }

            return;
        }

        if (mTitleState == TITLESTATE_POPCAP_LOGO) {
            g.setColor(Color.Black);
            g.fillRect(0, 0, mWidth, mHeight);

            Image aPopcapLogo = mApp.getImage(Resources.IMAGE_POPCAP_LOGO_ID);
            int anAlpha = 255;
            if (mTitleStateCounter < mTitleStateDuration - 50) {
                if (!mDisplayPartnerLogo) {
                    anAlpha = PvzpCommon.pvzpAnimateCurve(50, 0, mTitleStateCounter, 255, 0, 1);
                }
            } else {
                anAlpha = PvzpCommon.pvzpAnimateCurve(mTitleStateDuration, mTitleStateDuration - 50,
                        mTitleStateCounter, 0, 255, 1);
            }
            g.setColorizeImages(true);
            g.setColor(new Color(255, 255, 255, anAlpha));
            g.drawImage(aPopcapLogo, (mWidth - aPopcapLogo.mWidth) / 2, (mHeight - aPopcapLogo.mHeight) / 2);
            g.setColorizeImages(false);

            return;
        }

        if (mTitleState == TITLESTATE_PARTNER_LOGO) {
            g.setColor(Color.Black);
            g.fillRect(0, 0, mWidth, mHeight);

            Image aPopcapLogo = mApp.getImage(Resources.IMAGE_POPCAP_LOGO_ID);
            Image aPartnerLogo = mApp.getImage(Resources.IMAGE_PARTNER_LOGO_ID);
            g.setColorizeImages(true);
            int anAlpha = 255;
            if (mTitleStateCounter >= mTitleStateDuration - 35) {
                anAlpha = PvzpCommon.pvzpAnimateCurve(mTitleStateDuration, mTitleStateDuration - 35,
                        mTitleStateCounter, 0, 255, 1);
                g.setColor(new Color(255, 255, 255, 255 - anAlpha));
                g.drawImage(aPopcapLogo, (mWidth - aPopcapLogo.mWidth) / 2, (mHeight - aPopcapLogo.mHeight) / 2);
            } else {
                anAlpha = PvzpCommon.pvzpAnimateCurve(35, 0, mTitleStateCounter, 255, 0, 1);
            }
            g.setColor(new Color(255, 255, 255, anAlpha));
            g.drawImage(aPartnerLogo, (mWidth - aPartnerLogo.mWidth) / 2, (mHeight - aPartnerLogo.mHeight) / 2);
            g.setColorizeImages(false);

            return;
        }

        if (!mLoaderScreenIsLoaded) {
            g.setColor(Color.Black);
            g.fillRect(0, 0, mWidth, mHeight);
            return;
        }

        Image aTitleScreen = mApp.getImage(Resources.IMAGE_TITLESCREEN_ID);
        Image aLoadBarDirt = mApp.getImage(Resources.IMAGE_LOADBAR_DIRT_ID);
        Image aLoadBarGrass = mApp.getImage(Resources.IMAGE_LOADBAR_GRASS_ID);
        Image aPvzLogo = mApp.getImage(Resources.IMAGE_PVZ_LOGO_ID);

        g.drawImage(aTitleScreen, 0, 0);
        if (mNeedToInit) {
            return;
        }

        int aLogoY;
        if (mTitleStateCounter > 60) {
            aLogoY = PvzpCommon.pvzpAnimateCurve(100, 60, mTitleStateCounter, -150, 10, 2);
        } else {
            aLogoY = PvzpCommon.pvzpAnimateCurve(60, 50, mTitleStateCounter, 10, 15, 9);
        }
        g.drawImage(aPvzLogo, mWidth / 2 - aPvzLogo.mWidth / 2, aLogoY);

        int aGrassX = mStartButton.mX;
        int aGrassY = mStartButton.mY - 17;

        g.drawImage(aLoadBarDirt, aGrassX, aGrassY + 18);

        if (mCurBarWidth >= mTotalBarWidth) {
            g.drawImage(aLoadBarGrass, aGrassX, aGrassY);

            if (mLoadingThreadComplete) {
                drawToPreload(g);
            }
        } else {
            Graphics aClipG = new Graphics(g);
            aClipG.clipRect(240, aGrassY, (int) mCurBarWidth, aLoadBarGrass.mHeight);
            aClipG.drawImage(aLoadBarGrass, aGrassX, aGrassY);

            Image aSodRollCap = mApp.getImage(Resources.IMAGE_REANIM_SODROLLCAP_ID);
            float aRollLen = mCurBarWidth * 0.94f;
            float aRotation = -aRollLen / 180 * (float) Math.PI * 2;
            float aScale = PvzpCommon.pvzpAnimateCurveFloatTime(0, mTotalBarWidth, mCurBarWidth, 1, 0.5f, 1);
            SexyMatrix3 aTransform = new SexyMatrix3();
            PvzpCommon.pvzpScaleRotateTransformMatrix(aTransform, aGrassX + 11.0f + aRollLen,
                    aGrassY - 3.0f - 35.0f * aScale + 35.0f, aRotation, aScale, aScale);
            Rect aSrcRect = new Rect(0, 0, aSodRollCap.mWidth, aSodRollCap.mHeight);
            PvzpCommon.pvzpBltMatrix(g, aSodRollCap, aTransform, g.mClipRect, Color.White, g.getDrawMode(), aSrcRect);
        }

        for (Reanimation aReanim : mApp.mEffectSystem.mReanimationHolder.mReanimations) {
            aReanim.draw(g);
        }
    }

    // TitleScreen::Update (TitleScreen.cpp:212-493)
    @Override
    public void update() {
        super.update();
        if (mApp.mShutdown.get()) {
            return;
        }

        markDirty();
        if (!mDrawnYet) {
            return;
        }

        if (mTitleState == TITLESTATE_WAITING_FOR_FIRST_DRAW) {
            mApp.mMusic.musicTitleScreenInit();
            mApp.startLoadingThread();

            mTitleState = TITLESTATE_POPCAP_LOGO;
            if (mDisplayPartnerLogo) {
                mTitleStateDuration = 150;
            } else {
                mTitleStateDuration = 200;
            }

            mTitleStateCounter = mTitleStateDuration;
        }

        if (mQuickLoadKey != KeyCode.KEYCODE_UNKNOWN && mTitleState != TITLESTATE_SCREEN) {
            mTitleState = TITLESTATE_SCREEN;
            mTitleStateDuration = 0;
            mTitleStateCounter = 100;
        }

        mTitleAge++;
        if (mTitleStateCounter > 0) {
            mTitleStateCounter--;
        }

        if (mTitleState == TITLESTATE_POPCAP_LOGO) {
            if (mTitleStateCounter == 0) {
                if (mDisplayPartnerLogo) {
                    mTitleState = TITLESTATE_SCREEN;
                    mTitleStateDuration = 200;
                    mTitleStateCounter = 200;
                } else {
                    mTitleState = TITLESTATE_SCREEN;
                    mTitleStateDuration = 100;
                    mTitleStateCounter = 100;
                }
            }

            return;
        } else if (mTitleState == TITLESTATE_PARTNER_LOGO) {
            if (mTitleStateCounter == 0) {
                mTitleState = TITLESTATE_SCREEN;
                mTitleStateDuration = 100;
                mTitleStateCounter = 100;
            }

            return;
        }

        if (!mLoaderScreenIsLoaded) {
            return;
        }

        float aCurrentProgress = (float) mApp.getLoadingThreadProgress();
        if (mNeedToInit) {
            mNeedToInit = false;

            mStartButton.mLabel = PvzpStringFile.translate("[LOADING]");
            mStartButton.setFont(mApp.getFont(Resources.FONT_BRIANNETOD16_ID));
            Image aLoadBarDirt = mApp.getImage(Resources.IMAGE_LOADBAR_DIRT_ID);
            mStartButton.resize((mWidth - aLoadBarDirt.mWidth) / 2, 650, (int) mTotalBarWidth, 50);
            mStartButton.mVisible = true;

            float aEstimatedTotalLoadTime;
            if (aCurrentProgress > 0.000001f) {
                aEstimatedTotalLoadTime = mTitleAge / aCurrentProgress;
            } else {
                aEstimatedTotalLoadTime = 3000;
            }

            float aLoadTime = aEstimatedTotalLoadTime * (1 - aCurrentProgress);
            aLoadTime = Math.clamp(aLoadTime, 100.0f, 3000.0f);
            mBarVel = mTotalBarWidth / aLoadTime;
            mBarStartProgress = Math.min(aCurrentProgress, 0.9f);
        }

        float aLoadingPercent = (aCurrentProgress - mBarStartProgress) / (1 - mBarStartProgress);

        int aButtonY;
        if (mTitleStateCounter > 10) {
            aButtonY = PvzpCommon.pvzpAnimateCurve(60, 10, mTitleStateCounter, 650, 534, 2);
        } else {
            aButtonY = PvzpCommon.pvzpAnimateCurve(10, 0, mTitleStateCounter, 534, 529, 9);
        }
        mStartButton.resize(mStartButton.mX, aButtonY, (int) mTotalBarWidth, mStartButton.mHeight);

        if (mTitleStateCounter > 0) {
            return;
        }

        mApp.mEffectSystem.update();

        float aPrevWidth = mCurBarWidth;
        mCurBarWidth += mBarVel;
        if (!mLoadingThreadComplete) {
            if (mCurBarWidth > mTotalBarWidth * 0.99f) {
                mCurBarWidth = mTotalBarWidth * 0.99f;
            }
        } else if (mCurBarWidth > mTotalBarWidth) {
            mStartButton.mLabel = PvzpStringFile.translate("[CLICK_TO_START]");
            mCurBarWidth = mTotalBarWidth;
        }

        if (aLoadingPercent > mPrevLoadingPercent + 0.01f || mLoadingThreadComplete) {
            float aBarWidth = PvzpCommon.pvzpAnimateCurveFloatTime(0, 1, aLoadingPercent, 0, mTotalBarWidth, 1);
            float aDiff = aBarWidth - mCurBarWidth;
            float aAcceleration = PvzpCommon.pvzpAnimateCurveFloatTime(0, 1, aLoadingPercent, 0.0001f, 0.00001f, 1);
            if (mLoadingThreadComplete) {
                aAcceleration = 0.0001f;
            }
            mBarVel += aDiff * Math.abs(aDiff) * aAcceleration;

            float aMinVelocity = PvzpCommon.pvzpAnimateCurveFloatTime(0, 1, aLoadingPercent, 0.2f, 0.01f, 1);
            float aMaxVelocity = 2;
            if (mApp.mCheatKeys) {
                aMinVelocity = 0;
                aMaxVelocity = 100;
            }

            if (mBarVel < aMinVelocity) {
                mBarVel = aMinVelocity;
            } else if (mBarVel > aMaxVelocity) {
                mBarVel = aMaxVelocity;
            }

            mPrevLoadingPercent = aLoadingPercent;
        }

        if (!mLoadingThreadComplete && (mApp.isInDemoMode() ? mApp.mLoaded : mApp.mLoadingThreadCompleted.get())) {
            mLoadingThreadComplete = true;
            mStartButton.setDisabled(false);

            if (mQuickLoadKey == KeyCode.KEYCODE_ASCIIEND) {
                mApp.fastLoad(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN);
            } else if (mQuickLoadKey == 0x4D) {
                mApp.loadingCompleted();
            } else if (mQuickLoadKey == 0x53) {
                mApp.loadingCompleted();
                mApp.killGameSelector();
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
            } else if (mQuickLoadKey == 0x43) {
                mApp.loadingCompleted();
                mApp.killGameSelector();
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
            } else if (mQuickLoadKey == 0x55) {
                mApp.loadingCompleted();
                mApp.killGameSelector();
                mApp.preNewGame(GameMode.GAMEMODE_UPSELL, false);
            } else if (mQuickLoadKey == 0x49) {
                mApp.loadingCompleted();
                mApp.killGameSelector();
                mApp.preNewGame(GameMode.GAMEMODE_INTRO, false);
            } else if (mQuickLoadKey == 0x50) {
                mApp.loadingCompleted();
                mApp.killGameSelector();
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
            } else if (mQuickLoadKey == 0x52) {
                mApp.loadingCompleted();
                mApp.killGameSelector();
                mApp.showCreditScreen();
            } else if (mApp.mCheatKeys && mApp.mPlayerInfo != null && mQuickLoadKey == 0x54) {
                mApp.fastLoad(GameMode.GAMEMODE_ADVENTURE);
            } else {
                mStartButton.setVisible(true);
            }
        }

        float[] aTriggerPoint = {
            mTotalBarWidth * 0.11f,
            mTotalBarWidth * 0.32f,
            mTotalBarWidth * 0.54f,
            mTotalBarWidth * 0.72f,
            mTotalBarWidth * 0.91f
        };

        for (int i = 0; i < aTriggerPoint.length; i++) {
            if (aPrevWidth < aTriggerPoint[i] && mCurBarWidth >= aTriggerPoint[i]) {
                int aReanimType = ReanimationType.REANIM_LOADBAR_SPROUT;
                if (i == 4) {
                    aReanimType = ReanimationType.REANIM_LOADBAR_ZOMBIEHEAD;
                }
                float aPosX = aTriggerPoint[i] + 225.0f;
                float aPosY = 511.0f;
                Reanimation aSproutReanim = mApp.addReanimation(aPosX, aPosY, 0, aReanimType);
                aSproutReanim.mAnimRate = 18.0f;
                aSproutReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;

                if (i == 1 || i == 3) {
                    aSproutReanim.overrideScale(-1.0f, 1.0f);
                } else if (i == 2) {
                    aSproutReanim.setPosition(aPosX, aPosY - 5.0f);
                    aSproutReanim.overrideScale(1.1f, 1.3f);
                } else if (i == 4) {
                    aSproutReanim.setPosition(aPosX - 20.0f, aPosY);
                }

                if (i == 4) {
                    mApp.playSample(Resources.SOUND_LOADINGBAR_FLOWER_ID);
                    mApp.playSample(Resources.SOUND_LOADINGBAR_ZOMBIE_ID);
                } else {
                    mApp.playSample(Resources.SOUND_LOADINGBAR_FLOWER_ID);
                }
            }
        }
    }

    // TitleScreen::Resize (TitleScreen.cpp:495-498)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);
    }

    // TitleScreen::AddedToManager (TitleScreen.cpp:500-504)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        theWidgetManager.addWidget(mStartButton);
    }

    // TitleScreen::RemovedFromManager (TitleScreen.cpp:506-510)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        theWidgetManager.removeWidget(mStartButton);
    }

    // TitleScreen::ButtonPress (TitleScreen.cpp:512-515)
    @Override
    public void buttonPress(int theId) {
        mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
    }

    // TitleScreen::ButtonDepress (TitleScreen.cpp:517-528)
    @Override
    public void buttonDepress(int theId) {
        switch (theId) {
        case TitleScreen_Start:
            mApp.loadingCompleted();
            break;

        case TitleScreen_Register:
            mRegisterClicked = true;
            break;

        default:
            break;
        }
    }

    @Override
    public void buttonDownTick(int theId) {
    }

    @Override
    public void buttonMouseEnter(int theId) {
    }

    @Override
    public void buttonMouseLeave(int theId) {
    }

    @Override
    public void buttonMouseMove(int theId, int theX, int theY) {
    }

    // TitleScreen::MouseDown (TitleScreen.cpp:531-538)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        if (mLoadingThreadComplete) {
            mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
            mApp.loadingCompleted();
        }
    }

    // TitleScreen::KeyDown (TitleScreen.cpp:540-552)
    @Override
    public void keyDown(int theKey) {
        if (mLoadingThreadComplete) {
            mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
            mApp.loadingCompleted();
        }

        if (mApp.mCheatKeys && mApp.mPlayerInfo != null) {
            mQuickLoadKey = theKey;
        }
    }

    /** Ported from TitleScreen::SetRegistered (declared TitleScreen.h:96, dead in this port). */
    public void setRegistered() {
    }

}
