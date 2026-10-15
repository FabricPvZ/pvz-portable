/*
 * Ported from src/Lawn/Widget/CreditScreen.h:1-159 and src/Lawn/Widget/CreditScreen.cpp:1-1783.
 *
 * <p>The end-of-adventure credits movie. Three reanim sequences (MAIN / MAIN2 / MAIN3) play in
 * order while the {@link #gCreditsTiming} karaoke table drives the singing sunflower and the flying
 * brain, then the scrolling final-credits text ({@code CREDITS_END}) loops behind the replay /
 * main-menu buttons. Music is {@code MUSIC_TUNE_CREDITS_ZOMBIES_ON_YOUR_LAWN}, re-seeked in
 * {@link #jumpToFrame} via the pre-computed karaoke offset tables.
 *
 * <p>The upstream translation unit declares several helper types at file scope. Because the whole
 * widget is ported into this one Java file they are nested here with their upstream names:
 * {@link CreditsPhase}, {@link CreditLayer}, {@link CreditWordType}, {@link CreditBrainType},
 * {@link CreditsTiming} and {@link CreditsOverlay}. Public fields keep the upstream {@code m}
 * prefix and methods are camelCase of the upstream names.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.Optional;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.MusicFile;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.lawn.ParticleSystemID;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationID;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.RenderLayer;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorDefinition;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrack;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.PerfTimer;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.TriVertex;

/**
 * Ported from src/Lawn/Widget/CreditScreen.h:74-143 and src/Lawn/Widget/CreditScreen.cpp:326-1783.
 *
 * <p>The credits widget: karaoke movie playback, the final scrolling credits and the pause / replay
 * menu.
 */
public class CreditScreen extends Widget implements ButtonListener {

    // CreditScreen.h:79-83 (enum { Credits_Button_Replay, Credits_Button_MainMenu })
    public static final int Credits_Button_Replay = 0;
    public static final int Credits_Button_MainMenu = 1;

    /** CreditScreen.cpp:40 ({@code CREDIT_SCREEN_ANIM_RATE}). */
    public static final float CREDIT_SCREEN_ANIM_RATE = 0.3f;

    // Layout of mMainMenuButton (CreditScreen.cpp:350-352).
    public static final int MAIN_MENU_BUTTON_X = 298;
    public static final int MAIN_MENU_BUTTON_Y = 554;
    public static final int MAIN_MENU_BUTTON_WIDTH = 209;
    public static final int MAIN_MENU_BUTTON_HEIGHT = 46;

    // Layout of mReplayButton (CreditScreen.cpp:354-363).
    public static final int REPLAY_BUTTON_X = 10;
    public static final int REPLAY_BUTTON_Y = 530;
    public static final int REPLAY_BUTTON_WIDTH = 125;
    public static final int REPLAY_BUTTON_HEIGHT = 65;
    public static final int REPLAY_BUTTON_TEXT_OFFSET_X = 33;
    public static final int REPLAY_BUTTON_TEXT_OFFSET_Y = -5;

    /** Ported from src/Lawn/Widget/CreditScreen.h:32-38 (enum {@code CreditsPhase}). */
    public static final class CreditsPhase {
        public static final int CREDITS_MAIN1 = 0;
        public static final int CREDITS_MAIN2 = 1;
        public static final int CREDITS_MAIN3 = 2;
        public static final int CREDITS_END = 3;

        private CreditsPhase() {
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.h:40-45 (enum {@code CreditLayer}). */
    public static final class CreditLayer {
        public static final int CREDIT_LAYER_BACKGROUND = 0;
        public static final int CREDIT_LAYER_ZOMBIE = 1;
        public static final int CREDIT_LAYER_TOP = 2;

        private CreditLayer() {
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.h:47-54 (enum {@code CreditWordType}). */
    public static final class CreditWordType {
        public static final int WORD_AA = 0;
        public static final int WORD_EE = 1;
        public static final int WORD_AW = 2;
        public static final int WORD_OH = 3;
        public static final int WORD_OFF = 4;

        private CreditWordType() {
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.h:56-64 (enum {@code CreditBrainType}). */
    public static final class CreditBrainType {
        public static final int BRAIN_FLY_ON = 0;
        public static final int BRAIN_FAST_ON = 1;
        public static final int BRAIN_NEXT_WORD = 2;
        public static final int BRAIN_FAST_OFF = 3;
        public static final int BRAIN_FLY_OFF = 4;
        public static final int BRAIN_OFF = 5;

        private CreditBrainType() {
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.h:68-74 (class {@code CreditsTiming}). */
    public static class CreditsTiming {
        public float mFrame;
        public int mWordType;
        public int mWordX;
        public int mBrainType;

        public CreditsTiming(float theFrame, int theWordType, int theWordX, int theBrainType) {
            mFrame = theFrame;
            mWordType = theWordType;
            mWordX = theWordX;
            mBrainType = theBrainType;
        }
    }

    /**
     * Ported from src/Lawn/Widget/CreditScreen.cpp:42-311 ({@code gCreditsTiming}): 268 karaoke rows,
     * frames strictly increasing from 128.5 to 1033.0, shared by all three movie phases through the
     * constant per-phase offset added in {@link #getTiming}.
     */
    public static final CreditsTiming[] gCreditsTiming = {
        new CreditsTiming(128.5f, CreditWordType.WORD_AW, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(133.0f, CreditWordType.WORD_OH, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(136.5f, CreditWordType.WORD_EE, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(140.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(141.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(143.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(145.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(149.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(153.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(155.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(159.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(163.0f, CreditWordType.WORD_AW, 619, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(171.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(172.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(173.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(175.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(177.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(181.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(185.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(187.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(191.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(193.0f, CreditWordType.WORD_AW, 619, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(199.0f, CreditWordType.WORD_AA, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(203.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(205.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FLY_ON),
        new CreditsTiming(207.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(209.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(213.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(217.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(219.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(223.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(227.0f, CreditWordType.WORD_AW, 619, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(231.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(234.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(235.0f, CreditWordType.WORD_EE, 150, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(237.0f, CreditWordType.WORD_OH, 220, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(239.0f, CreditWordType.WORD_AW, 307, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(241.0f, CreditWordType.WORD_AW, 390, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(245.0f, CreditWordType.WORD_EE, 452, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(249.0f, CreditWordType.WORD_AW, 512, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(251.0f, CreditWordType.WORD_AW, 573, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(255.0f, CreditWordType.WORD_AW, 630, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(257.0f, CreditWordType.WORD_AW, 656, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(261.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(262.0f, CreditWordType.WORD_AA, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(266.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(266.5f, CreditWordType.WORD_AW, 96, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(268.5f, CreditWordType.WORD_OH, 154, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(270.5f, CreditWordType.WORD_OH, 244, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(272.5f, CreditWordType.WORD_AW, 329, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(276.5f, CreditWordType.WORD_AW, 419, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(279.5f, CreditWordType.WORD_AW, 506, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(281.5f, CreditWordType.WORD_AW, 597, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(284.5f, CreditWordType.WORD_AW, 671, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(286.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(287.0f, CreditWordType.WORD_OH, 48, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(288.0f, CreditWordType.WORD_AW, 125, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(290.0f, CreditWordType.WORD_OH, 193, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(291.0f, CreditWordType.WORD_EE, 254, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(294.5f, CreditWordType.WORD_AW, 318, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(295.0f, CreditWordType.WORD_AW, 375, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(296.0f, CreditWordType.WORD_AW, 438, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(297.0f, CreditWordType.WORD_AW, 480, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(299.0f, CreditWordType.WORD_AW, 556, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(301.0f, CreditWordType.WORD_AW, 619, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(303.0f, CreditWordType.WORD_AW, 675, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(305.0f, CreditWordType.WORD_AW, 744, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(307.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(309.5f, CreditWordType.WORD_OFF, 207, CreditBrainType.BRAIN_FLY_ON),
        new CreditsTiming(310.5f, CreditWordType.WORD_OFF, 287, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(311.5f, CreditWordType.WORD_OFF, 365, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(313.5f, CreditWordType.WORD_OFF, 435, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(315.5f, CreditWordType.WORD_OFF, 518, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(317.5f, CreditWordType.WORD_OFF, 603, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(318.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FAST_OFF),
        new CreditsTiming(319.5f, CreditWordType.WORD_OFF, 198, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(320.5f, CreditWordType.WORD_OFF, 264, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(322.5f, CreditWordType.WORD_OFF, 335, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(323.5f, CreditWordType.WORD_OFF, 411, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(324.5f, CreditWordType.WORD_OFF, 474, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(326.5f, CreditWordType.WORD_OFF, 527, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(328.5f, CreditWordType.WORD_OFF, 595, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(332.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(337.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(339.5f, CreditWordType.WORD_AW, 190, CreditBrainType.BRAIN_FLY_ON),
        new CreditsTiming(340.5f, CreditWordType.WORD_AW, 260, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(342.5f, CreditWordType.WORD_AA, 314, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(344.5f, CreditWordType.WORD_AW, 364, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(347.5f, CreditWordType.WORD_AW, 426, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(349.5f, CreditWordType.WORD_OH, 474, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(350.5f, CreditWordType.WORD_AW, 538, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(352.5f, CreditWordType.WORD_EE, 606, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(353.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FAST_OFF),
        new CreditsTiming(354.5f, CreditWordType.WORD_EE, 187, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(356.5f, CreditWordType.WORD_AW, 242, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(358.5f, CreditWordType.WORD_OH, 280, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(359.5f, CreditWordType.WORD_AW, 340, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(360.5f, CreditWordType.WORD_AW, 394, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(361.5f, CreditWordType.WORD_EE, 439, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(363.5f, CreditWordType.WORD_AW, 500, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(364.5f, CreditWordType.WORD_AW, 550, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(366.5f, CreditWordType.WORD_EE, 606, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(369.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(371.5f, CreditWordType.WORD_OFF, 200, CreditBrainType.BRAIN_FLY_ON),
        new CreditsTiming(372.5f, CreditWordType.WORD_OH, 258, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(374.5f, CreditWordType.WORD_OFF, 332, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(376.5f, CreditWordType.WORD_OFF, 416, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(378.5f, CreditWordType.WORD_OFF, 494, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(380.5f, CreditWordType.WORD_OFF, 576, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(381.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FAST_OFF),
        new CreditsTiming(382.5f, CreditWordType.WORD_OFF, 255, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(384.5f, CreditWordType.WORD_OFF, 322, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(386.5f, CreditWordType.WORD_OFF, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(388.5f, CreditWordType.WORD_OFF, 474, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(390.5f, CreditWordType.WORD_OFF, 533, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(394.5f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(522.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(523.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(525.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(527.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(531.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(535.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(537.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(541.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(545.0f, CreditWordType.WORD_AW, 619, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(549.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(554.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(555.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(557.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(559.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(563.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(567.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(569.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(573.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(575.0f, CreditWordType.WORD_AW, 619, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(581.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(582.0f, CreditWordType.WORD_AA, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(586.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(587.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(589.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(591.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(595.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(599.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(601.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(605.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(609.0f, CreditWordType.WORD_AW, 619, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(613.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(616.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(617.0f, CreditWordType.WORD_EE, 150, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(619.0f, CreditWordType.WORD_OH, 220, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(621.0f, CreditWordType.WORD_AW, 307, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(623.0f, CreditWordType.WORD_AW, 390, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(627.0f, CreditWordType.WORD_EE, 452, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(631.0f, CreditWordType.WORD_AW, 512, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(633.0f, CreditWordType.WORD_AW, 573, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(637.0f, CreditWordType.WORD_AW, 630, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(639.0f, CreditWordType.WORD_AW, 656, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(643.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(644.0f, CreditWordType.WORD_AA, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(648.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(649.0f, CreditWordType.WORD_AA, 196, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(651.0f, CreditWordType.WORD_EE, 247, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(653.0f, CreditWordType.WORD_AW, 299, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(655.0f, CreditWordType.WORD_AW, 371, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(658.0f, CreditWordType.WORD_OH, 443, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(659.0f, CreditWordType.WORD_EE, 475, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(661.0f, CreditWordType.WORD_AW, 512, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(662.0f, CreditWordType.WORD_AA, 544, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(664.0f, CreditWordType.WORD_OH, 573, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(667.0f, CreditWordType.WORD_AA, 610, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(669.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FAST_OFF),
        new CreditsTiming(670.0f, CreditWordType.WORD_OFF, 48, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(671.0f, CreditWordType.WORD_OFF, 110, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(673.0f, CreditWordType.WORD_OFF, 185, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(674.0f, CreditWordType.WORD_OFF, 262, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(676.0f, CreditWordType.WORD_OFF, 317, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(677.0f, CreditWordType.WORD_OFF, 357, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(678.0f, CreditWordType.WORD_OFF, 417, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(679.0f, CreditWordType.WORD_OFF, 491, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(682.0f, CreditWordType.WORD_OFF, 558, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(685.0f, CreditWordType.WORD_OFF, 628, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(687.0f, CreditWordType.WORD_OFF, 720, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(689.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(690.0f, CreditWordType.WORD_OFF, 172, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(692.0f, CreditWordType.WORD_OFF, 263, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(694.0f, CreditWordType.WORD_OFF, 346, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(696.0f, CreditWordType.WORD_OFF, 423, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(698.0f, CreditWordType.WORD_OFF, 480, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(700.0f, CreditWordType.WORD_OFF, 536, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(702.0f, CreditWordType.WORD_OFF, 583, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(705.0f, CreditWordType.WORD_OFF, 633, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(708.0f, CreditWordType.WORD_OFF, 668, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(712.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(719.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(720.0f, CreditWordType.WORD_OFF, 182, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(722.0f, CreditWordType.WORD_OFF, 267, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(724.0f, CreditWordType.WORD_OFF, 331, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(726.0f, CreditWordType.WORD_OFF, 371, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(729.0f, CreditWordType.WORD_OFF, 434, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(731.0f, CreditWordType.WORD_OFF, 486, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(732.0f, CreditWordType.WORD_OFF, 562, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(734.0f, CreditWordType.WORD_OFF, 617, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(735.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FAST_OFF),
        new CreditsTiming(736.0f, CreditWordType.WORD_AW, 148, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(738.0f, CreditWordType.WORD_AW, 211, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(740.0f, CreditWordType.WORD_EE, 298, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(742.0f, CreditWordType.WORD_OH, 367, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(744.0f, CreditWordType.WORD_AW, 440, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(746.0f, CreditWordType.WORD_OH, 506, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(747.0f, CreditWordType.WORD_AW, 533, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(748.0f, CreditWordType.WORD_AW, 601, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(749.0f, CreditWordType.WORD_AW, 645, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(750.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FAST_OFF),
        new CreditsTiming(753.0f, CreditWordType.WORD_OFF, 123, CreditBrainType.BRAIN_FLY_ON),
        new CreditsTiming(755.0f, CreditWordType.WORD_OFF, 195, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(757.0f, CreditWordType.WORD_OFF, 255, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(759.0f, CreditWordType.WORD_OFF, 312, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(761.0f, CreditWordType.WORD_OFF, 378, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(763.0f, CreditWordType.WORD_OFF, 443, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(765.0f, CreditWordType.WORD_OFF, 516, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(767.0f, CreditWordType.WORD_OFF, 563, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(770.0f, CreditWordType.WORD_OFF, 588, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(773.0f, CreditWordType.WORD_OFF, 657, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(777.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(907.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(908.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(910.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(912.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(916.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(920.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(922.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(926.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(930.0f, CreditWordType.WORD_AW, 616, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(934.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(939.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(940.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(942.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(944.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(948.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(952.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(954.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(958.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(960.0f, CreditWordType.WORD_AW, 616, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(966.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(967.0f, CreditWordType.WORD_AA, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(971.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(972.0f, CreditWordType.WORD_AW, 214, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(974.0f, CreditWordType.WORD_AW, 297, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(976.0f, CreditWordType.WORD_AW, 348, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(980.0f, CreditWordType.WORD_EE, 400, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(984.0f, CreditWordType.WORD_AW, 455, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(986.0f, CreditWordType.WORD_OH, 523, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(990.0f, CreditWordType.WORD_AW, 593, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(994.0f, CreditWordType.WORD_AW, 616, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(998.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(1001.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(1002.0f, CreditWordType.WORD_EE, 150, CreditBrainType.BRAIN_FAST_ON),
        new CreditsTiming(1004.0f, CreditWordType.WORD_OH, 220, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1006.0f, CreditWordType.WORD_AW, 307, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1008.0f, CreditWordType.WORD_AW, 390, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1012.0f, CreditWordType.WORD_EE, 452, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1016.0f, CreditWordType.WORD_AW, 512, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1018.0f, CreditWordType.WORD_AW, 573, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1022.0f, CreditWordType.WORD_AW, 630, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1024.0f, CreditWordType.WORD_AW, 656, CreditBrainType.BRAIN_NEXT_WORD),
        new CreditsTiming(1028.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_FLY_OFF),
        new CreditsTiming(1029.0f, CreditWordType.WORD_AA, 0, CreditBrainType.BRAIN_OFF),
        new CreditsTiming(1033.0f, CreditWordType.WORD_OFF, 0, CreditBrainType.BRAIN_OFF),
    };

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:312 ({@code gCreditsTimingCount = LENGTH(gCreditsTiming)}). */
    public static final int gCreditsTimingCount = 268;

    /**
     * Java replacement for the three {@code CreditsTiming**}/{@code float*} out-parameters of
     * {@code CreditScreen::GetTiming} (CreditScreen.cpp:519-570). {@code mBeforeTiming} /
     * {@code mAfterTiming} are {@code null} at the table ends.
     */
    public static final class CreditsTimingResult {
        public CreditsTiming mBeforeTiming;
        public CreditsTiming mAfterTiming;
        public float mFraction;
    }

    // CreditScreen.h:86-113
    public GameButton mCloseButton;
    public LawnApp mApp;
    public int mCreditsPhase;
    public int mCreditsPhaseCounter;
    public int mCreditsReanimID;
    public int mFogParticleID;
    public int mBlinkCountdown;
    public LawnStoneButton mMainMenuButton;
    public NewLawnButton mReplayButton;
    public Widget mOverlayWidget;
    public boolean mDrawBrain;
    public float mBrainPosX;
    public float mBrainPosY;
    public int mUpdateCount;
    public int mDrawCount;
    public PerfTimer mTimerSinceStart;
    public boolean mDontSync;
    public boolean mCreditsPaused;
    public double mOriginalMusicVolume;
    public boolean mPreloaded;
    public int mLastDrawCount;

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:326-378 (CreditScreen::CreditScreen). */
    public CreditScreen(LawnApp theApp) {
        mApp = theApp;
        mClip = false;
        mCreditsReanimID = ReanimationID.REANIMATIONID_NULL;
        mFogParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        mUpdateCount = 0;
        mDrawCount = 0;
        mLastDrawCount = 0;
        mBlinkCountdown = 700;
        mDontSync = false;
        mCreditsPaused = false;
        mPreloaded = false;
        mTimerSinceStart = new PerfTimer();

        mApp.mEffectSystem.effectSystemFreeAll();
        mApp.mMusic.stopAllMusic();
        mLoadedResourceNames.add("DelayLoad_Credits");
        ((PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources("DelayLoad_Credits");

        mBrainPosX = 0.0f;
        mBrainPosY = 0.0f;
        mCreditsPhase = CreditsPhase.CREDITS_MAIN1;
        mCreditsPhaseCounter = 0;
        mDrawBrain = false;

        mMainMenuButton = GameButton.makeButton(Credits_Button_MainMenu, this, "[CREDITS_MAIN_MENU_BUTTON]");
        mMainMenuButton.resize(MAIN_MENU_BUTTON_X, MAIN_MENU_BUTTON_Y, MAIN_MENU_BUTTON_WIDTH, MAIN_MENU_BUTTON_HEIGHT);
        mMainMenuButton.setVisible(false);

        mReplayButton = GameButton.makeNewButton(Credits_Button_Replay, this, "[CREDITS_REPLAY_BUTTON]",
                mApp.getFont(Resources.FONT_HOUSEOFTERROR16_ID),
                mApp.getImage(Resources.IMAGE_CREDITS_PLAYBUTTON_ID), null, null);
        mReplayButton.mTextDownOffsetX = 1;
        mReplayButton.mTextDownOffsetY = 1;
        mReplayButton.setLabelColor(new Color(255, 255, 255));
        mReplayButton.setLabelHiliteColor(new Color(213, 159, 43));
        mReplayButton.resize(REPLAY_BUTTON_X, REPLAY_BUTTON_Y, REPLAY_BUTTON_WIDTH, REPLAY_BUTTON_HEIGHT);
        mReplayButton.setVisible(false);
        mReplayButton.mTextOffsetX = REPLAY_BUTTON_TEXT_OFFSET_X;
        mReplayButton.mTextOffsetY = REPLAY_BUTTON_TEXT_OFFSET_Y;

        mOverlayWidget = new CreditsOverlay(this);
        mOverlayWidget.resize(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);

        mOriginalMusicVolume = mApp.mMusicVolume;
        if (mApp.mMusicVolume < 0.1) {
            mApp.setMusicVolume(0.85);
        }
    }

    /**
     * Ported from src/Lawn/Widget/CreditScreen.cpp:368-371 (CreditScreen::~CreditScreen); restores
     * the music volume forced up by the constructor and releases the tracked resources.
     */
    @Override
    public void dispose() {
        mApp.setMusicVolume(mOriginalMusicVolume);
        super.dispose();
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:380-386 (CreditScreen::AddedToManager). */
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mMainMenuButton);
        addWidget(mReplayButton);
        addWidget(mOverlayWidget);
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:388-394 (CreditScreen::RemovedFromManager). */
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mMainMenuButton);
        removeWidget(mReplayButton);
        removeWidget(mOverlayWidget);
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:396-517 (CreditScreen::PreLoadCredits). */
    public void preLoadCredits() {
        mPreloaded = true;
        mLoadedResourceNames.add("DelayLoad_Background1");
        mLoadedResourceNames.add("DelayLoad_Background2");
        mLoadedResourceNames.add("DelayLoad_Background3");
        mLoadedResourceNames.add("DelayLoad_Background4");
        mLoadedResourceNames.add("DelayLoad_Background5");
        mLoadedResourceNames.add("DelayLoad_Background6");

        for (String resource : mLoadedResourceNames) {
            ((PvzpResourceManager) mApp.mResourceManager).pvzpLoadResources(resource);
        }

        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_MAIN, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_MAIN);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_MAIN2, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_MAIN2);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_MAIN3, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_MAIN3);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CREDITS_DANCE, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_ZOMBIE_CREDITS_DANCE);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_BIGBRAIN, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_BIGBRAIN);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_FLOWER_PETALS, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_FLOWER_PETALS);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_INFANTRY, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_INFANTRY);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_STAGE, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_STAGE);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_THROAT, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_THROAT);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SUNFLOWER, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_SUNFLOWER);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SUN, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_SUN);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_CRAZYDAVE, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_CRAZYDAVE);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_ZOMBIE);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_DIGGER, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_DIGGER);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_FOOTBALL, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_ZOMBIE_FOOTBALL);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CREDITS_CONEHEAD, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_ZOMBIE_CREDITS_CONEHEAD);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CREDITS_SCREEN_DOOR, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_ZOMBIE_CREDITS_SCREEN_DOOR);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_ZOMBIEARMY1, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_ZOMBIEARMY1);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_ZOMBIEARMY2, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_ZOMBIEARMY2);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_TOMBSTONES, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_TOMBSTONES);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_SOLARPOWER, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_SOLARPOWER);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_ANYHOUR, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_ANYHOUR);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_WEARETHEUNDEAD, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_WEARETHEUNDEAD);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_DISCOLIGHTS, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_DISCOLIGHTS);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_CREDITS_BOSSDANCE, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_CREDITS_BOSSDANCE);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_TALLNUT, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_TALLNUT);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SNOWPEA, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_SNOWPEA);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_PEASHOOTER, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_PEASHOOTER);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_REPEATER, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_REPEATER);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_KERNELPULT, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_KERNELPULT);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_WALLNUT, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_WALLNUT);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_FUMESHROOM, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_FUMESHROOM);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_PUFFSHROOM, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_PUFFSHROOM);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SUNSHROOM, true);
        Reanimator.reanimationPreload(ReanimationType.REANIM_SUNSHROOM);

        // Patch the words fonts in the loaded definitions (CreditScreen.cpp:466-515).
        Font aBlackFont = Resources.getFontById(Resources.FONT_BRIANNETOD32BLACK_ID);
        ReanimatorDefinition aMain2Def = Reanimator.gReanimatorDefArray[ReanimationType.REANIM_CREDITS_MAIN2];
        ReanimatorTrack[] aMain2Tracks = (ReanimatorTrack[]) aMain2Def.mTracks.mArrayData;
        for (int aTrackIndex = 0; aTrackIndex < aMain2Tracks.length; aTrackIndex++) {
            ReanimatorTrack aTrack = aMain2Tracks[aTrackIndex];
            ReanimatorTransform[] aTransforms = (ReanimatorTransform[]) aTrack.mTransforms.mArrayData;
            for (int aTransIndex = 0; aTransIndex < aTransforms.length; aTransIndex++) {
                ReanimatorTransform aTrans = aTransforms[aTransIndex];
                if (aTransIndex < 124 && (aTrack.mName.equals("Words") || aTrack.mName.equals("Words2"))) {
                    aTrans.mFont = aBlackFont;
                }
            }
        }
        ReanimatorDefinition aMain3Def = Reanimator.gReanimatorDefArray[ReanimationType.REANIM_CREDITS_MAIN3];
        ReanimatorTrack[] aMain3Tracks = (ReanimatorTrack[]) aMain3Def.mTracks.mArrayData;
        for (int aTrackIndex = 0; aTrackIndex < aMain3Tracks.length; aTrackIndex++) {
            ReanimatorTrack aTrack = aMain3Tracks[aTrackIndex];
            ReanimatorTransform[] aTransforms = (ReanimatorTransform[]) aTrack.mTransforms.mArrayData;
            for (int aTransIndex = 0; aTransIndex < aTransforms.length; aTransIndex++) {
                ReanimatorTransform aTrans = aTransforms[aTransIndex];
                if (aTransIndex < 124 && (aTrack.mName.equals("Words") || aTrack.mName.equals("Words2"))) {
                    aTrans.mFont = aBlackFont;
                }
            }
        }
        ReanimatorDefinition aUndeadDef = Reanimator.gReanimatorDefArray[ReanimationType.REANIM_CREDITS_WEARETHEUNDEAD];
        ReanimatorTrack[] aUndeadTracks = (ReanimatorTrack[]) aUndeadDef.mTracks.mArrayData;
        for (int aTrackIndex = 0; aTrackIndex < aUndeadTracks.length; aTrackIndex++) {
            ReanimatorTrack aTrack = aUndeadTracks[aTrackIndex];
            ReanimatorTransform[] aTransforms = (ReanimatorTransform[]) aTrack.mTransforms.mArrayData;
            for (int aTransIndex = 0; aTransIndex < aTransforms.length; aTransIndex++) {
                ReanimatorTransform aTrans = aTransforms[aTransIndex];
                if (aTrans.mFont != null) {
                    aTrans.mFont = aBlackFont;
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Widget/CreditScreen.cpp:519-570 (CreditScreen::GetTiming): adds the
     * per-phase frame offset (MAIN1 +2, MAIN2 +400, MAIN3 +785) and returns the bracketing karaoke
     * rows plus the linear fraction between them. CREDITS_END has no timing.
     */
    public CreditsTimingResult getTiming() {
        CreditsTimingResult aResult = new CreditsTimingResult();
        Reanimation aCreditsReanim = mApp.reanimationGet(mCreditsReanimID);
        // == mDefinition.mTracks.tracks->mTransforms.count * mAnimTime - 1 (CreditScreen.cpp:521)
        float aFrameCount = aCreditsReanim.mFrameCount * aCreditsReanim.mAnimTime - 1.0f;
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN1) {
            aFrameCount += 2.0f;
        } else if (mCreditsPhase == CreditsPhase.CREDITS_MAIN2) {
            aFrameCount += 400.0f;
        } else if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3) {
            aFrameCount += 785.0f;
        } else {
            aResult.mBeforeTiming = null;
            aResult.mAfterTiming = null;
            aResult.mFraction = 0.0f;
            return aResult;
        }

        if (aFrameCount < gCreditsTiming[0].mFrame) {
            aResult.mBeforeTiming = null;
            aResult.mAfterTiming = gCreditsTiming[0];
            aResult.mFraction = aFrameCount / gCreditsTiming[0].mFrame;
        } else {
            for (int i = 0; i < gCreditsTimingCount - 1; i++) {
                CreditsTiming aTiming1 = gCreditsTiming[i];
                CreditsTiming aTiming2 = gCreditsTiming[i + 1];
                // upstream PVZP_ASSERT(aTiming1->mFrame < aTiming2->mFrame) (CreditScreen.cpp:555)

                if (aTiming2.mFrame > aFrameCount) {
                    aResult.mBeforeTiming = aTiming1;
                    aResult.mAfterTiming = aTiming2;
                    aResult.mFraction = (aFrameCount - aTiming1.mFrame) / (aTiming2.mFrame - aTiming1.mFrame);
                    return aResult;
                }
            }

            aResult.mBeforeTiming = gCreditsTiming[gCreditsTimingCount - 1];
            aResult.mAfterTiming = null;
            aResult.mFraction = 0.0f;
        }
        return aResult;
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:572-618 (CreditScreen::PlayReanim). */
    public Reanimation playReanim(int aIndex) {
        Reanimation aCreditsReanim = mApp.reanimationTryToGet(mCreditsReanimID);
        if (aCreditsReanim != null) {
            aCreditsReanim.reanimationDie();
        }

        if (aIndex == 1) {
            aCreditsReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_CREDITS_MAIN);
            aCreditsReanim.assignRenderGroupToPrefix("Background", 1);
            aCreditsReanim.assignRenderGroupToPrefix("attacher__Zombie", 2);
            aCreditsReanim.assignRenderGroupToPrefix("Words", 3);
            aCreditsReanim.assignRenderGroupToPrefix("SpotFront", 3);
        } else if (aIndex == 2) {
            aCreditsReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_CREDITS_MAIN2);
            aCreditsReanim.assignRenderGroupToPrefix("Background", 1);
            aCreditsReanim.assignRenderGroupToPrefix("attacher__Zombie", 2);
            aCreditsReanim.assignRenderGroupToPrefix("Words", 3);
            aCreditsReanim.assignRenderGroupToPrefix("SpotFront", 3);
            aCreditsReanim.assignRenderGroupToPrefix("attacher__undead", 2);
        } else if (aIndex == 3) {
            aCreditsReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_CREDITS_MAIN3);
            aCreditsReanim.assignRenderGroupToPrefix("Background", 1);
            aCreditsReanim.assignRenderGroupToPrefix("attacher__Zombie", 2);
            aCreditsReanim.assignRenderGroupToPrefix("attacher__DiscoLights", 2);
            aCreditsReanim.assignRenderGroupToPrefix("Words", 3);
            aCreditsReanim.assignRenderGroupToPrefix("attacher__cattail", 3);
            aCreditsReanim.assignRenderGroupToPrefix("SpotFront", 3);
            aCreditsReanim.assignRenderGroupToPrefix("attacher__undead", 2);
        } else {
            // upstream PVZP_ASSERT(false) (CreditScreen.cpp:609)
            return null;
        }

        aCreditsReanim.mIsAttachment = true;
        aCreditsReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        mCreditsReanimID = mApp.reanimationGetID(aCreditsReanim);
        return aCreditsReanim;
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:620-654 (free function {@code DrawDisco}). */
    public static void drawDisco(Graphics g, float aCenterX, float aCenterY, float theTime) {
        if (!SexyAppBase.current.is3DAccelerated()) {
            return;
        }

        float x1 = (float) (Math.cos(theTime) * 600.0f);
        float y1 = (float) (Math.sin(theTime) * 200.0f);
        float x2 = (float) (Math.cos(theTime + GameConstants.PI / 2) * 600.0f);
        float y2 = (float) (Math.sin(theTime + GameConstants.PI / 2) * 200.0f);
        float x3 = (float) (Math.cos(theTime + GameConstants.PI) * 600.0f);
        float y3 = (float) (Math.sin(theTime + GameConstants.PI) * 200.0f);
        float x4 = (float) (Math.cos(theTime + GameConstants.PI + GameConstants.PI / 2) * 600.0f);
        float y4 = (float) (Math.sin(theTime + GameConstants.PI + GameConstants.PI / 2) * 200.0f);

        TriVertex[][] aVerts = new TriVertex[2][3];
        int aColor = new Color(255, 255, 255, 128).toInt();
        aVerts[0][0] = new TriVertex();
        aVerts[0][0].x = x1 + aCenterX;
        aVerts[0][0].y = y1 + aCenterY;
        aVerts[0][0].u = 0.0f;
        aVerts[0][0].v = 0.0f;
        aVerts[0][0].color = aColor;
        aVerts[0][1] = new TriVertex();
        aVerts[0][1].x = x2 + aCenterX;
        aVerts[0][1].y = y2 + aCenterY;
        aVerts[0][1].u = 1.0f;
        aVerts[0][1].v = 0.0f;
        aVerts[0][1].color = aColor;
        aVerts[0][2] = new TriVertex();
        aVerts[0][2].x = x4 + aCenterX;
        aVerts[0][2].y = y4 + aCenterY;
        aVerts[0][2].u = 0.0f;
        aVerts[0][2].v = 1.0f;
        aVerts[0][2].color = aColor;
        aVerts[1][0] = new TriVertex();
        aVerts[1][0].x = x4 + aCenterX;
        aVerts[1][0].y = y4 + aCenterY;
        aVerts[1][0].u = 0.0f;
        aVerts[1][0].v = 1.0f;
        aVerts[1][0].color = aColor;
        aVerts[1][1] = new TriVertex();
        aVerts[1][1].x = x2 + aCenterX;
        aVerts[1][1].y = y2 + aCenterY;
        aVerts[1][1].u = 1.0f;
        aVerts[1][1].v = 0.0f;
        aVerts[1][1].color = aColor;
        aVerts[1][2] = new TriVertex();
        aVerts[1][2].x = x3 + aCenterX;
        aVerts[1][2].y = y3 + aCenterY;
        aVerts[1][2].u = 1.0f;
        aVerts[1][2].v = 1.0f;
        aVerts[1][2].color = aColor;

        g.mDestImage.bltTrianglesTex(Resources.getImageById(Resources.IMAGE_REANIM_CREDITS_DISCOLIGHTS_ID),
                aVerts, 2, new Rect(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT),
                Color.White, g.getDrawMode(), 0.0f, 0.0f, g.getLinearBlend());
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:656-720 (CreditScreen::DrawFogEffect). */
    public void drawFogEffect(Graphics g, float theTime) {
        Reanimation aCreditsReanim = mApp.reanimationGet(mCreditsReanimID);
        Image aFogImage = mApp.is3DAccelerated()
                ? mApp.getImage(Resources.IMAGE_FOG_ID)
                : mApp.getImage(Resources.IMAGE_FOG_SOFTWARE_ID);
        int aFadeAmount = (int) (theTime * 255.0f);

        for (int x = 0; x < 14; x++) {
            for (int y = 0; y < 7; y++) {
                // fog shape for this cell
                int aCelLook = x + (x + 17) * y;
                int aCelCol = aCelLook % 8;
                float aPosX = x * 80 - 15.0f;
                float aPosY = y * 85 + 200.0f;
                // aAnimTime is the MV playback time in seconds, driving the cycling fog color
                float aAnimTime = aCreditsReanim.mFrameCount * aCreditsReanim.mAnimTime
                        / (aCreditsReanim.mAnimRate * Reanimator.SECONDS_PER_UPDATE);
                float aTime = (float) (aAnimTime * GameConstants.PI * 2);
                // initial phase tied to the column and row
                float aPhaseX = (float) (6 * GameConstants.PI * x / 14);
                float aPhaseY = (float) (6 * GameConstants.PI * y / 7);
                float aMotion = 13 + 4 * (float) Math.sin(aTime / 900 + aPhaseY)
                        + 8 * (float) Math.sin(aTime / 500 + aPhaseX);

                int aColorVariant = (int) (255 - (aCelLook % 20) * 1.5f - aMotion * 1.5f);
                int aLightnessVariant = (int) (255 - (aCelLook % 20) - aMotion);
                if (!mApp.is3DAccelerated()) {
                    aPosX += 10;
                    aPosY += 23;
                    aCelCol = aCelLook % mApp.getImage(Resources.IMAGE_FOG_SOFTWARE_ID).mNumCols;
                    aColorVariant = 255;
                    aLightnessVariant = 255;
                }

                g.setColorizeImages(true);
                g.setColor(new Color(aColorVariant, aColorVariant, aLightnessVariant, aFadeAmount));
                g.drawImageCel(aFogImage, (int) aPosX, (int) aPosY, aCelCol, 0);
                g.setColorizeImages(false);
            }
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:722-733 (CreditScreen::DrawOverlay). */
    @Override
    public void drawOverlay(Graphics g) {
        if (mCreditsPhase == CreditsPhase.CREDITS_END) {
            int aFadeAlpha = PvzpCommon.pvzpAnimateCurve(50, 100, mCreditsPhaseCounter, 255, 0,
                    PvzpCurves.CURVE_LINEAR);
            if (aFadeAlpha > 0) {
                g.setColor(new Color(0, 0, 0, aFadeAlpha));
                g.fillRect(0, 0, mWidth, mHeight);
            }
        }
    }

    /**
     * Ported from src/Lawn/Widget/CreditScreen.cpp:735-803 (free function {@code DrawCreditsContent}).
     * Walks the {@code [CREDITS_ROLES3..11]} / {@code [CREDITS_NAMES3..11]} sections, skipping the
     * missing ones, and returns the measured content height.
     */
    private static int drawCreditsContent(Graphics g, int theYPos, boolean theDraw) {
        Font aTitleFont = Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID);
        Font aCreditsFont = Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID);
        int aLineHeight = aCreditsFont.getLineSpacing();

        final int ROLES_RIGHT_X = GameConstants.BOARD_WIDTH / 2 - 15;
        final int NAMES_LEFT_X = GameConstants.BOARD_WIDTH / 2 + 15;

        int aYPos = theYPos;
        int aStartY = aYPos;

        // Title
        if (theDraw && aYPos > -aTitleFont.getLineSpacing()
                && aYPos < GameConstants.BOARD_HEIGHT + aTitleFont.getLineSpacing()) {
            PvzpCommon.pvzpDrawString(g, "[CREDITS_GAMENAME]", GameConstants.BOARD_WIDTH / 2, aYPos,
                    aTitleFont, Color.White, DrawStringJustification.DS_ALIGN_CENTER);
        }
        aYPos += aTitleFont.getLineSpacing() + 20;

        for (int aSection = 3; aSection <= 11; aSection++) {
            String aRolesKey = "[CREDITS_ROLES" + aSection + "]";
            Optional<String> aRolesLookup = PvzpStringFile.tryTranslate(aRolesKey);
            if (aRolesLookup.isEmpty()) {
                continue;
            }

            String aRoles = aRolesLookup.get();

            // ^ prefix: spacer or centered header
            if (!aRoles.isEmpty() && aRoles.charAt(0) == '^') {
                if (aRoles.length() > 1) {
                    if (theDraw && aYPos > -aLineHeight && aYPos < GameConstants.BOARD_HEIGHT + aLineHeight) {
                        PvzpCommon.pvzpDrawString(g, aRoles.substring(1), GameConstants.BOARD_WIDTH / 2, aYPos,
                                aCreditsFont, Color.White, DrawStringJustification.DS_ALIGN_CENTER);
                    }
                }
                aYPos += aLineHeight + 10;
                continue;
            }

            String aNamesKey = "[CREDITS_NAMES" + aSection + "]";
            String aNames = PvzpStringFile.tryTranslate(aNamesKey).orElse("");

            // Split roles and names by newline, draw side by side
            int aRolePos = 0;
            int aNamePos = 0;
            while (aRolePos < aRoles.length() || aNamePos < aNames.length()) {
                boolean aVisible = theDraw && aYPos > -aLineHeight && aYPos < GameConstants.BOARD_HEIGHT + aLineHeight;
                if (aRolePos < aRoles.length()) {
                    int aRoleEnd = aRoles.indexOf('\n', aRolePos);
                    if (aRoleEnd == -1) {
                        aRoleEnd = aRoles.length();
                    }
                    if (aVisible && aRoleEnd > aRolePos) {
                        PvzpCommon.pvzpDrawString(g, aRoles.substring(aRolePos, aRoleEnd), ROLES_RIGHT_X, aYPos,
                                aCreditsFont, Color.White, DrawStringJustification.DS_ALIGN_RIGHT);
                    }
                    aRolePos = aRoleEnd + 1;
                }
                if (aNamePos < aNames.length()) {
                    int aNameEnd = aNames.indexOf('\n', aNamePos);
                    if (aNameEnd == -1) {
                        aNameEnd = aNames.length();
                    }
                    if (aVisible && aNameEnd > aNamePos) {
                        PvzpCommon.pvzpDrawString(g, aNames.substring(aNamePos, aNameEnd), NAMES_LEFT_X, aYPos,
                                aCreditsFont, Color.White, DrawStringJustification.DS_ALIGN_LEFT);
                    }
                    aNamePos = aNameEnd + 1;
                }
                aYPos += aLineHeight;
            }
            aYPos += 10;
        }
        return aYPos - aStartY;
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:805-813 (CreditScreen::DrawFinalCredits). */
    public void drawFinalCredits(Graphics g) {
        // Measure total content height (no draw)
        int aContentHeight = drawCreditsContent(null, 0, false);
        int aTotalCycle = aContentHeight + GameConstants.BOARD_HEIGHT;  // full loop: content scrolls entirely through screen
        int aScrollOffset = (int) (mCreditsPhaseCounter * CREDIT_SCREEN_ANIM_RATE) % aTotalCycle;

        drawCreditsContent(g, GameConstants.BOARD_HEIGHT - aScrollOffset, true);
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:815-821 (free function {@code DrawReanimToPreload}). */
    public static void drawReanimToPreload(Graphics g, int theReanimType) {
        Reanimation aReanim = new Reanimation();
        aReanim.mAnimRate = CREDIT_SCREEN_ANIM_RATE;
        aReanim.reanimationInitializeType(0.0f, 0.0f, theReanimType);
        aReanim.draw(g);
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:823-1068 (CreditScreen::Draw). */
    @Override
    public void draw(Graphics g) {
        g.setLinearBlend(true);

        if (!mPreloaded) {
            g.setColor(Color.Black);
            g.fillRect(0, 0, mWidth, mHeight);
            mDrawCount = 1;
            return;
        }

        mDrawCount++;
        if (mDrawCount == 2) {
            g.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND1_ID), 0, 0);
            g.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND2_ID), 0, 0);
            g.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND3_ID), 0, 0);
            g.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND4_ID), 0, 0);
            g.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND5_ID), 0, 0);
            drawReanimToPreload(g, ReanimationType.REANIM_CREDITS_BOSSDANCE);
            drawReanimToPreload(g, ReanimationType.REANIM_CREDITS_CRAZYDAVE);
            drawReanimToPreload(g, ReanimationType.REANIM_ZOMBIE_CREDITS_DANCE);
            drawReanimToPreload(g, ReanimationType.REANIM_CREDITS_STAGE);
            drawReanimToPreload(g, ReanimationType.REANIM_SUNFLOWER);
        }
        g.setColor(Color.Black);
        g.fillRect(0, 0, mWidth, mHeight);

        Reanimation aCreditsReanim = mApp.reanimationGet(mCreditsReanimID);
        float aFrameFactor = 1.0f / (aCreditsReanim.mFrameCount - 1);
        int aBackground1Index = aCreditsReanim.findTrackIndex("Background");
        ReanimatorTransform aTransformBackground1 = new ReanimatorTransform();
        aCreditsReanim.getCurrentTransform(aBackground1Index, aTransformBackground1);
        int aBackground2Index = aCreditsReanim.findTrackIndex("Background2");
        ReanimatorTransform aTransformBackground2 = new ReanimatorTransform();
        aCreditsReanim.getCurrentTransform(aBackground2Index, aTransformBackground2);
        Graphics aBackground2G = new Graphics(g);

        boolean aDrawClippedBackgrounds1 = false;
        boolean aDrawClippedBackgrounds2 = false;
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN2 && aFrameFactor * 125.0f > aCreditsReanim.mAnimTime) {
            aDrawClippedBackgrounds1 = true;
        }
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3 && aFrameFactor * 125.0f > aCreditsReanim.mAnimTime) {
            aDrawClippedBackgrounds2 = true;
        }

        if (mCreditsPhase == CreditsPhase.CREDITS_END) {
            drawFinalCredits(g);
        }
        if (aDrawClippedBackgrounds1) {
            int aBackground3Index = aCreditsReanim.findTrackIndex("Background3");
            ReanimatorTransform aTransformBackground3 = new ReanimatorTransform();
            aCreditsReanim.getCurrentTransform(aBackground3Index, aTransformBackground3);
            int aBackground4Index = aCreditsReanim.findTrackIndex("Background4");
            ReanimatorTransform aTransformBackground4 = new ReanimatorTransform();
            aCreditsReanim.getCurrentTransform(aBackground4Index, aTransformBackground4);

            if (aTransformBackground2.mFrame != -1.0f) {
                aBackground2G.clipRect((int) aTransformBackground2.mTransX, (int) aTransformBackground2.mTransY,
                        aTransformBackground2.mImage.mWidth - 1, aTransformBackground2.mImage.mHeight - 1);
                aBackground2G.drawImageF(mApp.getImage(Resources.IMAGE_BACKGROUND1_ID),
                        aTransformBackground2.mTransX - GameConstants.BOARD_WIDTH / 2,
                        aTransformBackground2.mTransY - GameConstants.BOARD_HEIGHT / 2);
                aBackground2G.clearClipRect();
            }
            if (aTransformBackground3.mFrame != -1.0f) {
                Graphics aBackground3G = new Graphics(g);
                aBackground3G.clipRect((int) aTransformBackground3.mTransX, (int) aTransformBackground3.mTransY,
                        aTransformBackground3.mImage.mWidth - 1, aTransformBackground3.mImage.mHeight - 1);
                aBackground3G.drawImageF(mApp.getImage(Resources.IMAGE_BACKGROUND1_ID),
                        aTransformBackground3.mTransX - GameConstants.BOARD_WIDTH / 2,
                        aTransformBackground3.mTransY - GameConstants.BOARD_HEIGHT / 2);
            }
            if (aTransformBackground4.mFrame != -1.0f) {
                Graphics aBackground4G = new Graphics(g);
                aBackground4G.clipRect((int) aTransformBackground4.mTransX, (int) aTransformBackground4.mTransY,
                        aTransformBackground4.mImage.mWidth - 1, aTransformBackground4.mImage.mHeight - 1);
                aBackground4G.drawImageF(mApp.getImage(Resources.IMAGE_BACKGROUND2_ID),
                        aTransformBackground4.mTransX - GameConstants.BOARD_WIDTH / 2,
                        aTransformBackground4.mTransY - GameConstants.BOARD_HEIGHT / 2);
            }
        }
        if (aDrawClippedBackgrounds2) {
            aBackground1Index = aCreditsReanim.findTrackIndex("Background");
            aCreditsReanim.getCurrentTransform(aBackground1Index, aTransformBackground1);
            int aBackground3Index = aCreditsReanim.findTrackIndex("Background3");
            ReanimatorTransform aTransformBackground3 = new ReanimatorTransform();
            aCreditsReanim.getCurrentTransform(aBackground3Index, aTransformBackground3);
            int aBackground4Index = aCreditsReanim.findTrackIndex("Background4");
            ReanimatorTransform aTransformBackground4 = new ReanimatorTransform();
            aCreditsReanim.getCurrentTransform(aBackground4Index, aTransformBackground4);

            if (aTransformBackground1.mFrame != -1.0f) {
                Graphics aBackground1G = new Graphics(g);
                aBackground1G.clipRect((int) aTransformBackground1.mTransX, (int) aTransformBackground1.mTransY,
                        aTransformBackground1.mImage.mWidth - 1, aTransformBackground1.mImage.mHeight - 1);
                // Digger zombie says "I like your tricycle" at this position
                aBackground1G.drawImageF(mApp.getImage(Resources.IMAGE_BACKGROUND1_ID),
                        aTransformBackground1.mTransX - GameConstants.BOARD_WIDTH / 10,
                        aTransformBackground1.mTransY - GameConstants.BOARD_HEIGHT * 0.6f);
            }
            if (aTransformBackground3.mFrame != -1.0f) {
                Graphics aBackground3G = new Graphics(g);
                aBackground3G.mTransX += aTransformBackground3.mTransX - 20.0f;
                aBackground3G.mTransY += aTransformBackground3.mTransY - 260.0f;
                aBackground3G.clipRect(20, 260, aTransformBackground3.mImage.mWidth - 1,
                        aTransformBackground3.mImage.mHeight - 1);
                aBackground3G.drawImageF(mApp.getImage(Resources.IMAGE_BACKGROUND3_ID), -220.0f, 0.0f);
                aBackground3G.drawImageF(mApp.getImage(Resources.IMAGE_POOL_ID), 34.0f, 278.0f);
            }
            if (aTransformBackground4.mFrame != -1.0f) {
                Graphics aBackground4G = new Graphics(g);
                aBackground4G.clipRect((int) aTransformBackground4.mTransX, (int) aTransformBackground4.mTransY,
                        aTransformBackground4.mImage.mWidth - 1, aTransformBackground4.mImage.mHeight - 1);
                aBackground4G.drawImageF(mApp.getImage(Resources.IMAGE_BACKGROUND2_ID),
                        aTransformBackground4.mTransX - GameConstants.BOARD_WIDTH / 2,
                        aTransformBackground4.mTransY - GameConstants.BOARD_HEIGHT / 2);
            }
        }
        aCreditsReanim.drawRenderGroup(g, 1);

        boolean aDrawPool = false;
        boolean aDrawNightPool = false;
        boolean aDrawDoorBottom = false;
        boolean aDrawChimney = false;
        boolean aDrawDiscoLights = false;
        boolean aDrawFog = false;
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN1) {
            if (aCreditsReanim.mAnimTime > aFrameFactor * 203.0f && aCreditsReanim.mAnimTime < aFrameFactor * 268.0f) {
                aDrawPool = true;
            }
            if (aCreditsReanim.mAnimTime > aFrameFactor * 305.0f && aCreditsReanim.mAnimTime < aFrameFactor * 339.0f) {
                aDrawPool = true;
                aDrawDoorBottom = true;
            }
        }
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN2) {
            if (aCreditsReanim.mAnimTime > aFrameFactor * 187.0f && aCreditsReanim.mAnimTime < aFrameFactor * 249.0f) {
                aDrawPool = true;
                aDrawNightPool = true;
                if (aTransformBackground1.mFrame != -1.0f || aTransformBackground2.mFrame != -1.0f) {
                    aDrawDiscoLights = true;
                }
            }
            if (aCreditsReanim.mAnimTime > aFrameFactor * 123.0f && aCreditsReanim.mAnimTime < aFrameFactor * 189.0f) {
                if (aTransformBackground1.mFrame != -1.0f) {
                    aDrawDiscoLights = true;
                }
            }
            if (aCreditsReanim.mAnimTime > aFrameFactor * 189.0f && aCreditsReanim.mAnimTime < aFrameFactor * 249.0f) {
                if (aTransformBackground2.mFrame != -1.0f) {
                    aDrawDiscoLights = true;
                    aDrawFog = true;
                }
            }
        }
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3) {
            if (aCreditsReanim.mAnimTime > aFrameFactor * 123.0f && aCreditsReanim.mAnimTime < aFrameFactor * 218.0f) {
                aDrawChimney = true;
            }
            if (aCreditsReanim.mAnimTime > aFrameFactor * 217.0f) {
                if (aTransformBackground1.mFrame != -1.0f) {
                    aDrawDiscoLights = true;
                }
            }
        }
        if (aTransformBackground2.mFrame == -1.0f) {
            //aDrawPool = false;
            //aDrawNightPool = false;
            aDrawDoorBottom = false;
            aDrawChimney = false;
        } else {
            aBackground2G.mTransX += aTransformBackground2.mTransX + 220.0f;
            if (aDrawPool || aDrawNightPool) {
                mApp.mPoolEffect.poolEffectDraw(aBackground2G, aDrawNightPool);
            }
        }

        if (aDrawDoorBottom) {
            aBackground2G.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND3_GAMEOVER_INTERIOR_OVERLAY_ID), -171, 241);
        }
        aCreditsReanim.draw(g);

        if (aDrawDoorBottom) {
            g.clipRect(48, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        }
        if (aDrawDiscoLights) {
            float aDiscoTime = aCreditsReanim.mFrameCount * aCreditsReanim.mAnimTime / aCreditsReanim.mAnimRate;
            drawDisco(g, 600.0f, 450.0f, aDiscoTime);
            drawDisco(g, 200.0f, 450.0f, aDiscoTime);
        }
        if (aDrawFog) {
            aBackground2G.drawImage(mApp.getImage(Resources.IMAGE_REANIM_CREDITS_FOGMACHINE_ID), 600, 200);
        }
        aCreditsReanim.drawRenderGroup(g, 2);

        if (aDrawDoorBottom) {
            g.clearClipRect();
            aBackground2G.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND3_GAMEOVER_MASK_ID), -172, 234);
        }
        if (aDrawChimney) {
            aBackground2G.drawImage(mApp.getImage(Resources.IMAGE_BACKGROUND5_GAMEOVER_MASK_ID), -220, 81);
        }
        aCreditsReanim.drawRenderGroup(g, 3);

        for (PvzpParticleSystem aParticle : mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            if (!aParticle.mIsAttachment && !aParticle.mDead) {
                aParticle.draw(g);
            }
        }

        if (aDrawFog) {
            float aPercent = PvzpCommon.pvzpAnimateCurveFloatTime(aFrameFactor * 189.0f, aFrameFactor * 249.0f,
                    aCreditsReanim.mAnimTime, 0.0f, 1.0f, PvzpCurves.CURVE_LINEAR);
            drawFogEffect(aBackground2G, aPercent);
        }
        aCreditsReanim.drawRenderGroup(g, 3);

        if (mDrawBrain) {
            g.drawImageF(mApp.getImage(Resources.IMAGE_BRAIN_ID), mBrainPosX, mBrainPosY);
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1070-1089 (CreditScreen::FindSubReanim). */
    public static Reanimation findSubReanim(Reanimation theReanim, int theReanimType) {
        if (theReanim.mReanimationType == theReanimType) {
            return theReanim;
        }

        for (int i = 0; i < theReanim.mTrackInstances.length; i++) {
            Reanimation aReanimation = Attachment.findReanimAttachment(theReanim.mTrackInstances[i].mAttachmentID);
            if (aReanimation != null) {
                Reanimation aSubReanim = findSubReanim(aReanimation, theReanimType);
                if (aSubReanim != null) {
                    return aSubReanim;
                }
            }
        }

        return null;
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1091-1118 (CreditScreen::UpdateBlink). */
    public void updateBlink() {
        mBlinkCountdown--;
        if (mBlinkCountdown > 0) {
            return;
        }

        mBlinkCountdown = 700;
        Reanimation aCreditsReanim = mApp.reanimationGet(mCreditsReanimID);
        Reanimation aSunFlowerReanim = findSubReanim(aCreditsReanim, ReanimationType.REANIM_SUNFLOWER);
        if (aSunFlowerReanim != null) {
            if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3) {
                float aFrameFactor = 1.0f / (aCreditsReanim.mFrameCount - 1);
                if (aCreditsReanim.mAnimTime > aFrameFactor * 200.0f) {
                    return;
                }
            }

            Reanimation aBlinkReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_SUNFLOWER);
            aBlinkReanim.setFramesForLayer("anim_blink");
            aBlinkReanim.mAnimRate = 15.0f;
            aBlinkReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME;
            aBlinkReanim.attachToAnotherReanimation(aSunFlowerReanim, "anim_idle");
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1120-1169 (CreditScreen::Update). */
    @Override
    public void update() {
        super.update();
        if (!mCreditsPaused && !mMainMenuButton.mIsOver && !mReplayButton.mIsOver) {
            mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }
        if (mCreditsPaused || (!mApp.isInDemoMode() && mDrawCount == 0)) {
            return;
        }

        mUpdateCount++;
        if (mUpdateCount == 1) {
            preLoadCredits();
            playReanim(1);
            mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_CREDITS_ZOMBIES_ON_YOUR_LAWN);
            mApp.clearUpdateBacklog(false);
            mTimerSinceStart.start();
        } else if (mDontSync || mCreditsPhase == CreditsPhase.CREDITS_END) {
            updateMovie();
        } else if (mUpdateCount > 1) {
            if (mApp.isInDemoMode()) { // demo sessions advance the movie strictly one frame per update tick
                updateMovie();
            } else {
                Reanimation aCreditsReanim = mApp.reanimationGet(mCreditsReanimID);
                int aDurationSinceStart = (int) mTimerSinceStart.getDuration();
                int aDurationReanimation = (int) (aCreditsReanim.mFrameCount * aCreditsReanim.mAnimTime
                        / aCreditsReanim.mAnimRate * 1000.0f);
                if (mCreditsPhase == CreditsPhase.CREDITS_MAIN2) {
                    aDurationReanimation += 57142;
                } else if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3) {
                    aDurationReanimation += 112000;
                }

                int aUnsyncedDuration = aDurationSinceStart - aDurationReanimation;
                int aUnsyncedFrames = (aUnsyncedDuration + 5) / 10;
                if (aUnsyncedFrames < 0) {
                    PvzpDebug.pvzpLogLn("Movie playing too fast {} frames", 1 - aUnsyncedFrames);
                } else if (aUnsyncedFrames > 2) {
                    PvzpDebug.pvzpLogLn("Movie playing too slow {} frames", aUnsyncedFrames - 1);
                }

                if (aUnsyncedDuration > 10000) {
                    jumpToFrame(mCreditsPhase + 1, 0.0f);
                    aUnsyncedFrames = 0;
                }
                while (aUnsyncedFrames > 0) {
                    updateMovie();
                    aUnsyncedFrames--;
                }
            }
        }

        mLastDrawCount = mDrawCount;
        markDirty();
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1171-1499 (CreditScreen::UpdateMovie). */
    public void updateMovie() {
        updateBlink();

        Reanimation aCreditsReanim = mApp.reanimationGet(mCreditsReanimID);
        float aFrameFactor = 1.0f / (aCreditsReanim.mFrameCount - 1);

        aCreditsReanim.update();
        mApp.mEffectSystem.update();
        mApp.mPoolEffect.poolEffectUpdate();
        turnOffTongues(aCreditsReanim, 0);

        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN1 && aCreditsReanim.mLoopCount > 0) {
            aCreditsReanim = playReanim(2);
            mCreditsPhase = CreditsPhase.CREDITS_MAIN2;
        } else if (mCreditsPhase == CreditsPhase.CREDITS_MAIN2 && aCreditsReanim.mLoopCount > 0) {
            aCreditsReanim = playReanim(3);
            mCreditsPhase = CreditsPhase.CREDITS_MAIN3;
        } else if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3 && aCreditsReanim.mLoopCount > 0) {
            mCreditsPhase = CreditsPhase.CREDITS_END;
        } else if (mCreditsPhase == CreditsPhase.CREDITS_END) {
            mCreditsPhaseCounter++;
            if (mCreditsPhaseCounter == 50) {
                mMainMenuButton.setVisible(true);
                mReplayButton.setVisible(true);
            }
        }

        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN1) {
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 128.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 130.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 132.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 134.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 136.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 138.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 140.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 142.0f)) {
                mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2, GameConstants.BOARD_HEIGHT / 2,
                        RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_CREDIT_STROBE);
            }
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 136.5f)) {
                mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2, GameConstants.BOARD_HEIGHT / 2,
                        RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_CREDITS_RAYSWIPE);
            }
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 330.0f)) {
                mApp.playFoley(FoleyType.FOLEY_SCREAM);
            }
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 336.0f)) {
                mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_SCREAM);
            }
        }
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN2 || mCreditsPhase == CreditsPhase.CREDITS_MAIN3) {
            ReanimatorTrackInstance aTrackWords1 = aCreditsReanim.getTrackInstanceByName("Words");
            ReanimatorTrackInstance aTrackWords2 = aCreditsReanim.getTrackInstanceByName("Words2");
            if (aCreditsReanim.mAnimTime < aFrameFactor * 124.0f) {
                aTrackWords1.mTrackColor = Color.Black;
                aTrackWords2.mTrackColor = Color.Black;
            } else {
                aTrackWords1.mTrackColor = Color.White;
                aTrackWords2.mTrackColor = Color.White;
            }

            Reanimation aUndeadReanim = findSubReanim(aCreditsReanim, ReanimationType.REANIM_CREDITS_WEARETHEUNDEAD);
            if (aUndeadReanim != null) {
                ReanimatorTrackInstance aTrackWordsUndead1 = aUndeadReanim.getTrackInstanceByName("bubbletext1");
                ReanimatorTrackInstance aTrackWordsUndead2 = aUndeadReanim.getTrackInstanceByName("bubbletext2");
                ReanimatorTrackInstance aTrackWordsUndead3 = aUndeadReanim.getTrackInstanceByName("bubbletext3");
                aTrackWordsUndead1.mTrackColor = Color.Black;
                aTrackWordsUndead2.mTrackColor = Color.Black;
                aTrackWordsUndead3.mTrackColor = Color.Black;

                ReanimatorTrackInstance aTrackWordsShake = aUndeadReanim.getTrackInstanceByName("ShakyText");
                if (aCreditsReanim.mAnimTime > aFrameFactor * 112.0f) {
                    aTrackWordsShake.mShakeOverride = 2.0f;
                } else {
                    aTrackWordsShake.mShakeOverride = 0.0f;
                }
            }

            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 120.0f)) {
                mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2, GameConstants.BOARD_HEIGHT / 2,
                        RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_CREDITS_ZOMBIEHEADWIPE);
            }
        }
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN2) {
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 111.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 115.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 119.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 121.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 123.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 125.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 127.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 131.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 135.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 139.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 143.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 147.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 151.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 155.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 159.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 163.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 167.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 171.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 175.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 179.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 183.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 187.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 191.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 195.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 199.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 203.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 207.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 211.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 215.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 219.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 223.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 227.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 231.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 235.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 239.5f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 243.5f)) {
                mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2, GameConstants.BOARD_HEIGHT / 2,
                        RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_CREDIT_STROBE);
            }
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 332.75f)) {
                mApp.addPvzpParticle(678.0f, 352.0f, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_MELONSPLASH);
            }
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 336.0f)) {
                for (PvzpParticleSystem aParticle : mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
                    if (aParticle.mEffectType == ParticleEffect.PARTICLE_MELONSPLASH) {
                        aParticle.particleSystemDie();
                    }
                }
            }
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 342.0f)) {
                mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2, GameConstants.BOARD_HEIGHT / 2,
                        RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_CREDIT_STROBE);
            }

            int aBackground2Index = aCreditsReanim.findTrackIndex("Background2");
            ReanimatorTransform aTransformBackground2 = new ReanimatorTransform();
            aCreditsReanim.getCurrentTransform(aBackground2Index, aTransformBackground2);

            float aFogPosX = aTransformBackground2.mTransX + 856.0f;
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 188.0f)) {
                PvzpParticleSystem aFogParticle = mApp.addPvzpParticle(aFogPosX, 230.0f,
                        RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_CREDITS_FOG);
                mFogParticleID = mApp.particleGetID(aFogParticle);
            }
            PvzpParticleSystem aFogParticle = mApp.particleTryToGet(mFogParticleID);
            if (aFogParticle != null) {
                if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 248.0f)) {
                    aFogParticle.particleSystemDie();
                    mFogParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
                } else {
                    aFogParticle.systemMove(aFogPosX, 230.0f);
                }
            }
        }
        if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3) {
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 65.0f)) {
                mApp.playFoley(FoleyType.FOLEY_DOLPHIN_APPEARS);
            }
            if (aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 111.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 115.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 119.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 121.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 123.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 219.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 223.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 227.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 231.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 235.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 239.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 243.0f)
                    || aCreditsReanim.shouldTriggerTimedEvent(aFrameFactor * 247.0f)) {
                mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2, GameConstants.BOARD_HEIGHT / 2,
                        RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_CREDIT_STROBE);
            }
        }

        CreditsTimingResult aTiming = getTiming();
        CreditsTiming aBeforeTiming = aTiming.mBeforeTiming;
        CreditsTiming aAfterTiming = aTiming.mAfterTiming;
        float aFraction = aTiming.mFraction;

        int aSunflowerFrame = 0;
        mDrawBrain = false;
        if (aBeforeTiming != null && aAfterTiming != null) {
            if (aAfterTiming.mBrainType == CreditBrainType.BRAIN_FLY_ON) {
                mBrainPosX = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction, -50.0f,
                        aAfterTiming.mWordX - 15.0f, PvzpCurves.CURVE_EASE_IN_OUT);
                mBrainPosY = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction, 505.0f, 485.0f,
                        PvzpCurves.CURVE_BOUNCE_FAST_MIDDLE);
                mDrawBrain = true;
            } else if (aAfterTiming.mBrainType == CreditBrainType.BRAIN_FAST_ON) {
                mBrainPosX = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction,
                        aAfterTiming.mWordX - 50.0f, aAfterTiming.mWordX - 15.0f, PvzpCurves.CURVE_EASE_IN_OUT);
                mBrainPosY = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction, 485.0f, 505.0f,
                        PvzpCurves.CURVE_EASE_IN_OUT);
                mDrawBrain = true;
            } else if (aAfterTiming.mBrainType == CreditBrainType.BRAIN_FLY_OFF) {
                mBrainPosX = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction,
                        aBeforeTiming.mWordX - 15.0f, 850.0f, PvzpCurves.CURVE_EASE_IN_OUT);
                mBrainPosY = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction, 505.0f, 485.0f,
                        PvzpCurves.CURVE_BOUNCE_FAST_MIDDLE);
                mDrawBrain = true;
            } else if (aAfterTiming.mBrainType == CreditBrainType.BRAIN_FAST_OFF) {
                mBrainPosX = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction,
                        aBeforeTiming.mWordX - 15.0f, aBeforeTiming.mWordX + 25.0f, PvzpCurves.CURVE_EASE_IN_OUT);
                mBrainPosY = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction, 505.0f, 485.0f,
                        PvzpCurves.CURVE_EASE_IN_OUT);
                mDrawBrain = true;
            } else if (aBeforeTiming.mBrainType == CreditBrainType.BRAIN_FLY_ON
                    || aBeforeTiming.mBrainType == CreditBrainType.BRAIN_FAST_ON
                    || aBeforeTiming.mBrainType == CreditBrainType.BRAIN_NEXT_WORD) {
                mBrainPosX = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction,
                        aBeforeTiming.mWordX - 15.0f, aAfterTiming.mWordX - 15.0f, PvzpCurves.CURVE_EASE_IN_OUT);
                mBrainPosY = PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 1.0f, aFraction, 505.0f, 485.0f,
                        PvzpCurves.CURVE_BOUNCE_FAST_MIDDLE);
                mDrawBrain = true;
            }

            float aFramesForWord = aAfterTiming.mFrame - aBeforeTiming.mFrame;
            float aFramesTillEnd = (1.0f - aFraction) * aFramesForWord;
            if (aBeforeTiming.mWordType != CreditWordType.WORD_OFF && aFramesForWord * aFraction < 0.2f) {
                aSunflowerFrame = 1;
            } else if (aBeforeTiming.mWordType != CreditWordType.WORD_OFF && aFramesTillEnd < 0.4f) {
                aSunflowerFrame = 1;
            } else if (aBeforeTiming.mWordType == CreditWordType.WORD_AA) {
                aSunflowerFrame = 2;
            } else if (aBeforeTiming.mWordType == CreditWordType.WORD_EE) {
                aSunflowerFrame = 3;
            } else if (aBeforeTiming.mWordType == CreditWordType.WORD_AW) {
                aSunflowerFrame = 4;
            } else if (aBeforeTiming.mWordType == CreditWordType.WORD_OH) {
                aSunflowerFrame = 5;
            }
        }

        Reanimation aSunFlowerReanim = findSubReanim(aCreditsReanim, ReanimationType.REANIM_SUNFLOWER);
        if (aSunFlowerReanim != null) {
            if (mCreditsPhase == CreditsPhase.CREDITS_MAIN3 && aCreditsReanim.mAnimTime > aFrameFactor * 255.0f) {
                aSunFlowerReanim.setImageOverride("anim_idle", mApp.getImage(Resources.IMAGE_REANIM_SUNFLOWER_HEAD_WINK_ID));
                Reanimation aStageReanim = findSubReanim(aCreditsReanim, ReanimationType.REANIM_CREDITS_STAGE);
                aSunFlowerReanim.mAnimTime -= aSunFlowerReanim.mAnimRate * Reanimator.SECONDS_PER_UPDATE
                        / aSunFlowerReanim.mFrameCount;
                aStageReanim.mAnimTime -= aStageReanim.mAnimRate * Reanimator.SECONDS_PER_UPDATE
                        / aStageReanim.mFrameCount;
            } else {
                switch (aSunflowerFrame) {
                case 1:
                    aSunFlowerReanim.setImageOverride("anim_idle", mApp.getImage(Resources.IMAGE_REANIM_SUNFLOWER_HEAD_SING1_ID));
                    break;
                case 2:
                    aSunFlowerReanim.setImageOverride("anim_idle", mApp.getImage(Resources.IMAGE_REANIM_SUNFLOWER_HEAD_SING2_ID));
                    break;
                case 3:
                    aSunFlowerReanim.setImageOverride("anim_idle", mApp.getImage(Resources.IMAGE_REANIM_SUNFLOWER_HEAD_SING3_ID));
                    break;
                case 4:
                    aSunFlowerReanim.setImageOverride("anim_idle", mApp.getImage(Resources.IMAGE_REANIM_SUNFLOWER_HEAD_SING4_ID));
                    break;
                case 5:
                    aSunFlowerReanim.setImageOverride("anim_idle", mApp.getImage(Resources.IMAGE_REANIM_SUNFLOWER_HEAD_SING5_ID));
                    break;
                default:
                    aSunFlowerReanim.setImageOverride("anim_idle", null);
                    break;
                }
            }
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1501-1518 (CreditScreen::TurnOffTongues). */
    public void turnOffTongues(Reanimation theReanim, int aParentTrack) {
        ReanimatorTrack[] aTracks = (ReanimatorTrack[]) theReanim.mDefinition.mTracks.mArrayData;
        for (int aTrackIndex = 0; aTrackIndex < theReanim.mTrackInstances.length; aTrackIndex++) {
            ReanimatorTrackInstance aTrackInstance = theReanim.mTrackInstances[aTrackIndex];
            if (theReanim.mReanimationType == ReanimationType.REANIM_ZOMBIE_CREDITS_DANCE
                    && aParentTrack % 4 != 1
                    && aTracks[aTrackIndex].mName.equalsIgnoreCase("anim_tongue")) {
                aTrackInstance.mRenderGroup = Reanimation.RENDER_GROUP_HIDDEN;
            }

            Reanimation aAttachedReanim = Attachment.findReanimAttachment(aTrackInstance.mAttachmentID);
            if (aAttachedReanim != null) {
                turnOffTongues(aAttachedReanim, aTrackIndex);
            }
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1520-1639 (CreditScreen::JumpToFrame). */
    public void jumpToFrame(int thePhase, float theFrame) {
        mMainMenuButton.setVisible(false);
        mReplayButton.setVisible(false);
        mCreditsPhaseCounter = 0;
        mApp.mEffectSystem.effectSystemFreeAll();

        Reanimation aReanim;
        if (thePhase == CreditsPhase.CREDITS_END) {
            aReanim = playReanim(3);
        } else {
            aReanim = playReanim(thePhase + 1);
        }

        float aFrameFactor = 1.0f / (aReanim.mFrameCount - 1);
        int aMusicOffset = (int) (theFrame * 12142.0f);
        int aJumpMilliseconds = (int) (theFrame * 1000.0f / 7.0f);
        if (thePhase == CreditsPhase.CREDITS_MAIN1) {
            if (theFrame >= 368.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 368.0f) + 4634474.0f);
            } else if (theFrame >= 340.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 340.0f) + 4280738.0f);
            } else if (theFrame >= 304.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 304.0f) + 3825710.0f);
            } else if (theFrame >= 272.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 272.0f) + 3421764.0f);
            } else if (theFrame >= 144.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 144.0f) + 1805688.0f);
            } else if (theFrame >= 128.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 128.0f) + 1603662.0f);
            }
        } else if (thePhase == CreditsPhase.CREDITS_MAIN2) {
            if (theFrame >= 320.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 320.0f) + 9069118.0f);
                aJumpMilliseconds += 57142;
            } else if (theFrame >= 248.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 248.0f) + 8159850.0f);
                aJumpMilliseconds += 57142;
            } else if (theFrame >= 188.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 188.0f) + 7401454.0f);
                aJumpMilliseconds += 57142;
            } else if (theFrame >= 124.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 124.0f) + 6593548.0f);
                aJumpMilliseconds += 57142;
            } else {
                aMusicOffset = (int) (12142.0f * theFrame + 5026370.0f);
                aJumpMilliseconds += 57142;
            }
        } else if (thePhase == CreditsPhase.CREDITS_MAIN3) {
            if (theFrame >= 240.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 240.0f) + 12897822.0f);
                aJumpMilliseconds += 112000;
            } else if (theFrame >= 216.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 216.0f) + 12594510.0f);
                aJumpMilliseconds += 112000;
            } else if (theFrame >= 124.0f) {
                aMusicOffset = (int) (12142.0f * (theFrame - 124.0f) + 11434414.0f);
                aJumpMilliseconds += 112000;
            } else {
                aMusicOffset = (int) (12142.0f * theFrame + 9864866.0);
                aJumpMilliseconds += 112000;
            }
        } else if (thePhase == CreditsPhase.CREDITS_END) {
            aMusicOffset = 14047138;
            aJumpMilliseconds += 159142;
        }

        mApp.mMusic.playFromOffset(MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN, aMusicOffset - 900, 1.0f);
        if (thePhase == CreditsPhase.CREDITS_END) {
            aReanim.mAnimTime = 1.0f;
        } else {
            aReanim.mAnimTime = aFrameFactor * theFrame;
        }

        mCreditsPhase = thePhase;
        if (mApp.isInDemoMode()) { // keep the demo tick clock in sync with the jump target
            mUpdateCount = 1 + (int) (aJumpMilliseconds / 10.0);
        } else {
            mTimerSinceStart.setStartTime(aJumpMilliseconds);
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1641-1718 (CreditScreen::KeyChar). */
    @Override
    public void keyChar(char theChar) {
        if (mCreditsPaused || !mApp.mDebugKeysEnabled) {
            return;
        }

        if (theChar == '1') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN1, 0.0f);
        } else if (theChar == '2') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN1, 128.0f);
        } else if (theChar == '3') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN1, 144.0f);
        } else if (theChar == '4') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN1, 272.0f);
        } else if (theChar == '5') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN1, 304.0f);
        } else if (theChar == '6') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN1, 340.0f);
        } else if (theChar == '7') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN1, 368.0f);
        } else if (theChar == 'q') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN2, 0.0f);
        } else if (theChar == 'w') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN2, 124.0f);
        } else if (theChar == 'e') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN2, 188.0f);
        } else if (theChar == 'r') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN2, 248.0f);
        } else if (theChar == 't') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN2, 320.0f);
        } else if (theChar == 'a') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN3, 0.0f);
        } else if (theChar == 's') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN3, 124.0f);
        } else if (theChar == 'd') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN3, 216.0f);
        } else if (theChar == 'f') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN3, 240.0f);
        } else if (theChar == 'g') {
            jumpToFrame(CreditsPhase.CREDITS_MAIN3, 324.0f);
        } else if (theChar == 'n') {
            mDontSync = !mDontSync;
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1720-1747 (CreditScreen::PauseCredits). */
    public void pauseCredits() {
        if (mCreditsPaused) {
            return;
        }

        mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_SCREAM);
        mApp.playSample(SoundType.SOUND_PAUSE);
        mCreditsPaused = true;
        int aDurationOnPause = mApp.isInDemoMode() ? (mUpdateCount - 1) * 10 : (int) mTimerSinceStart.getDuration();
        mApp.mMusic.gameMusicPause(true);

        mApp.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "[CREDITS_PAUSE_HEADER]", "[CREDITS_PAUSE_BODY]",
                "[CREDITS_RESUME_BUTTON]", "[MAIN_MENU_BUTTON]", Dialog.BUTTONS_YES_NO,
                theYes -> {
                    if (!theYes) {
                        mApp.killCreditScreen();
                        mApp.doBackToMain();
                    }

                    mCreditsPaused = false;
                    mApp.mMusic.gameMusicPause(false);
                    if (!mApp.isInDemoMode()) { // the demo tick clock already froze during the pause
                        mTimerSinceStart.setStartTime(aDurationOnPause);
                    }
                });
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1749-1755 (CreditScreen::KeyDown). */
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN || theKey == KeyCode.KEYCODE_ESCAPE) {
            pauseCredits();
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1757-1767 (CreditScreen::ButtonPress). */
    @Override
    public void buttonPress(int theId) {
        if (theId == Credits_Button_MainMenu) {
            mApp.playSample(SoundType.SOUND_GRAVEBUTTON);
        } else if (theId == Credits_Button_Replay) {
            mApp.playSample(SoundType.SOUND_TAP);
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1769-1780 (CreditScreen::ButtonDepress). */
    @Override
    public void buttonDepress(int theId) {
        if (theId == Credits_Button_MainMenu) {
            mApp.killCreditScreen();
            mApp.doBackToMain();
        } else if (theId == Credits_Button_Replay) {
            mApp.killCreditScreen();
            mApp.showCreditScreen();
        }
    }

    /** Ported from src/Lawn/Widget/CreditScreen.cpp:1782 (CreditScreen::MouseUp). */
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
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

    /**
     * Ported from src/Lawn/Widget/CreditScreen.h:145-158 and src/Lawn/Widget/CreditScreen.cpp:314-324.
     *
     * <p>The transparent widget layered above the movie that forwards {@code Draw} to the parent's
     * {@link CreditScreen#drawOverlay}.
     */
    public static class CreditsOverlay extends Widget {
        public CreditScreen mParent;

        public CreditsOverlay(CreditScreen theCreditScreen) {
            mParent = theCreditScreen;
            mMouseVisible = false;
            mHasAlpha = true;
        }

        @Override
        public void draw(Graphics g) {
            mParent.drawOverlay(g);
        }
    }
}
