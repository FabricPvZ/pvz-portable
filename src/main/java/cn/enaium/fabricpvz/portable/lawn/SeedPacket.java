/*
 * Ported from src/Lawn/SeedPacket.h:1-92 and src/Lawn/SeedPacket.cpp:23-1218.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FilterEffect;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/SeedPacket.h:29-67 and src/Lawn/SeedPacket.cpp:23-894.
 *
 * <p>The file-scope constants and the two free draw helpers of SeedPacket.cpp live here
 * (SLOT_MACHINE_TIME/CONVEYOR_SPEED and seedPacketDrawSeed/drawSeedPacket).
 */
public class SeedPacket extends GameObject {

    /** Ported from src/Lawn/SeedPacket.cpp:29 (SLOT_MACHINE_TIME). */
    public static final int SLOT_MACHINE_TIME = 400;
    /** Ported from src/Lawn/SeedPacket.cpp:30 (CONVEYOR_SPEED). */
    public static final int CONVEYOR_SPEED = 4;

    public int mRefreshCounter;
    public int mRefreshTime;
    public int mIndex;
    public int mOffsetX;
    public int mPacketType;
    public int mImitaterType;
    public int mSlotMachineCountDown;
    public int mSlotMachiningNextSeed;
    public float mSlotMachiningPosition;
    public boolean mActive;
    public boolean mRefreshing;
    public int mTimesUsed;

    /** Ported from src/Lawn/SeedPacket.cpp:38-56 (SeedPacket::SeedPacket). */
    public SeedPacket() {
        mSlotMachiningPosition = 0.0f;
        mPacketType = SeedType.SEED_NONE;
        mImitaterType = SeedType.SEED_NONE;
        mIndex = -1;
        mSlotMachiningNextSeed = SeedType.SEED_NONE;
        mWidth = 50;
        mHeight = 70;
        mRefreshCounter = 0;
        mRefreshTime = 0;
        mRefreshing = false;
        mActive = true;
        mOffsetX = 0;
        mSlotMachineCountDown = 0;
        mSlotMachiningNextSeed = SeedType.SEED_NONE;
        mTimesUsed = 0;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:57-101 (SeedPacket::PickNextSlotMachineSeed). */
    public void pickNextSlotMachineSeed() {
        int aPeasCount = mBoard.countPlantByType(SeedType.SEED_PEASHOOTER);

        int[] SLOT_SEED_TYPES = {
            SeedType.SEED_SUNFLOWER,
            SeedType.SEED_PEASHOOTER,
            SeedType.SEED_SNOWPEA,
            SeedType.SEED_WALLNUT,
            SeedType.SEED_SLOT_MACHINE_SUN,
            SeedType.SEED_SLOT_MACHINE_DIAMOND
        };

        int aSeedsCount = 0;
        PvzpWeightedArray[] aSeedWeightArray = new PvzpWeightedArray[SeedType.MAX_SEED_TYPES];
        for (int i = 0; i < SLOT_SEED_TYPES.length; i++) {
            int aSeedType = SLOT_SEED_TYPES[i];

            int aWeight = 100;
            if (aSeedType == SeedType.SEED_PEASHOOTER) {
                aWeight = PvzpCommon.pvzpAnimateCurve(0, 5, aPeasCount, 200, 100, PvzpCurves.CURVE_LINEAR);
            } else if (aSeedType == SeedType.SEED_SLOT_MACHINE_DIAMOND) {
                aWeight = 30;
            }

            if (mIndex == 2 && aSeedType != SeedType.SEED_SLOT_MACHINE_DIAMOND) {
                if (aSeedType == mBoard.mSeedBank.mSeedPackets[0].mSlotMachiningNextSeed
                        || aSeedType == mBoard.mSeedBank.mSeedPackets[1].mSlotMachiningNextSeed) {
                    aWeight += aWeight / 2;
                }
            }

            aSeedWeightArray[aSeedsCount] = new PvzpWeightedArray();
            aSeedWeightArray[aSeedsCount].mItem = aSeedType;
            aSeedWeightArray[aSeedsCount].mWeight = aWeight;
            aSeedsCount++;
        }

        mSlotMachiningNextSeed = (int) PvzpCommon.pvzpPickFromWeightedArray(aSeedWeightArray, aSeedsCount);
    }

    /** Ported from src/Lawn/SeedPacket.cpp:102-108 (SeedPacket::SlotMachineStart). */
    public void slotMachineStart() {
        mSlotMachineCountDown = 300;
        mSlotMachiningPosition = 0.0f;
        pickNextSlotMachineSeed();
    }

    /** Ported from src/Lawn/SeedPacket.cpp:109-133 (SeedPacket::FlashIfReady). */
    public void flashIfReady() {
        if (!canPickUp() || mApp.mEasyPlantingCheat) {
            return;
        }

        if (!mBoard.hasConveyorBeltSeedBank()) {
            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_UI_BOTTOM, 0, 2);
            mApp.addPvzpParticle(mX + mBoard.mSeedBank.mX, mY + mBoard.mSeedBank.mY, aRenderPosition,
                    ParticleEffect.PARTICLE_SEED_PACKET_FLASH);
        }

        if (mBoard.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_REFRESH_PEASHOOTER) {
            mBoard.setTutorialState(TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER);
        } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER
                && mPacketType == SeedType.SEED_SUNFLOWER) {
            mBoard.setTutorialState(TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER);
        } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_MORESUN_REFRESH_SUNFLOWER
                && mPacketType == SeedType.SEED_SUNFLOWER) {
            mBoard.setTutorialState(TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER);
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:134-139 (SeedPacket::Activate). */
    public void activate() {
        assert mPacketType != SeedType.SEED_NONE;
        mActive = true;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:140-147 (SeedPacket::Deactivate). */
    public void deactivate() {
        mActive = false;
        mRefreshCounter = 0;
        mRefreshTime = 0;
        mRefreshing = false;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:148-158 (SeedPacket::SetActivate). */
    public void setActivate(boolean theActive) {
        if (theActive) {
            activate();
        } else {
            deactivate();
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:160-209 (SeedPacket::Update). */
    public void update() {
        if (mApp.mGameScene != GameScenes.SCENE_PLAYING || mPacketType == SeedType.SEED_NONE) {
            return;
        }

        if (mBoard.mMainCounter == 0) {
            flashIfReady();
        }

        if (!mActive && mRefreshing) {
            mRefreshCounter++;
            if (mRefreshCounter > mRefreshTime) {
                mRefreshCounter = 0;
                mRefreshing = false;
                activate();
                flashIfReady();
            }
        }

        if (mSlotMachineCountDown > 0) {
            mSlotMachineCountDown--;
            float aFlipsPerSecont = PvzpCommon.pvzpAnimateCurveFloat(SLOT_MACHINE_TIME, 0, mSlotMachineCountDown,
                    6.0f, 2.0f, PvzpCurves.CURVE_LINEAR);
            mSlotMachiningPosition += aFlipsPerSecont * 0.01f;

            if (mSlotMachiningPosition >= 1.0f) {
                mPacketType = mSlotMachiningNextSeed;
                if (mSlotMachineCountDown == 0) {
                    activate();
                    mSlotMachiningPosition = 0.0f;
                } else {
                    mSlotMachiningPosition -= 1.0f;
                    pickNextSlotMachineSeed();
                }
            } else if (mSlotMachineCountDown == 0) {
                mSlotMachineCountDown = 1;
            }
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:211-284 (SeedPacketDrawSeed). */
    public static void seedPacketDrawSeed(Graphics g, float x, float y, int theSeedType, int theImitaterType,
            float theOffsetX, float theOffsetY, float theScale) {
        Image aImage = Resources.getImageById(ImageID.IMAGE_PACKET_PLANTS);
        int aSeedType = theSeedType;
        if (theSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
            aSeedType = theImitaterType;
            int aFilterEffect = FilterEffect.FILTER_EFFECT_WASHED_OUT;
            if (theImitaterType == SeedType.SEED_HYPNOSHROOM || theImitaterType == SeedType.SEED_SQUASH
                    || theImitaterType == SeedType.SEED_POTATOMINE
                    || theImitaterType == SeedType.SEED_GARLIC
                    || theImitaterType == SeedType.SEED_LILYPAD) {
                aFilterEffect = FilterEffect.FILTER_EFFECT_LESS_WASHED_OUT;
            }
            aImage = FilterEffect.filterEffectGetImage(aImage, aFilterEffect);
        }

        if (aSeedType == SeedType.SEED_POTATOMINE && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 0, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_CHOMPER && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 1, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_HYPNOSHROOM && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 2, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_TALLNUT && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 3, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_BLOVER && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 4, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_PUMPKINSHELL && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 5, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_TWINSUNFLOWER && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 6, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_COBCANNON && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 7, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_CABBAGEPULT && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 8, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_KERNELPULT && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 9, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_MELONPULT && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 10, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_WINTERMELON && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 11, 0, g.mScaleX, g.mScaleY);
        } else if (aSeedType == SeedType.SEED_SPIKEROCK && g.mScaleX <= 1.0f) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, x, y, 12, 0, g.mScaleX, g.mScaleY);
        } else {
            Graphics aSeedG = new Graphics(g);
            aSeedG.mScaleX = theScale * g.mScaleX;
            aSeedG.mScaleY = theScale * g.mScaleY;
            Plant.drawSeedType(aSeedG, theSeedType, theImitaterType, DrawVariation.VARIATION_NORMAL,
                    x + theOffsetX, y + theOffsetY);
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:286-604 (DrawSeedPacket). */
    public static void drawSeedPacket(Graphics g, float x, float y, int theSeedType, int theImitaterType,
            float thePercentDark, int theGrayness, boolean theDrawCost, boolean theUseCurrentCost) {
        int aSeedType = theSeedType;
        if (aSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
            aSeedType = theImitaterType;
        }

        if (theGrayness != 255) {
            g.setColor(new Color(theGrayness, theGrayness, theGrayness));
            g.setColorizeImages(true);
        } else if (thePercentDark > 0) {
            g.setColor(new Color(128, 128, 128, 255));
            g.setColorizeImages(true);
        }

        int aPacketBackground =
                theSeedType == SeedType.SEED_IMITATER ? 0
                : Plant.isUpgrade(aSeedType) ? 1
                : theSeedType == SeedType.SEED_BEGHOULED_BUTTON_CRATER ? 3
                : theSeedType == SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE ? 4
                : theSeedType == SeedType.SEED_SLOT_MACHINE_SUN ? 5
                : theSeedType == SeedType.SEED_SLOT_MACHINE_DIAMOND ? 6
                : theSeedType == SeedType.SEED_ZOMBIQUARIUM_SNORKLE ? 7
                : theSeedType == SeedType.SEED_ZOMBIQUARIUM_TROPHY ? 8 : 2;

        if (g.mScaleX > 1) {
            PvzpCommon.pvzpDrawImageCelScaledF(g, Resources.getImageById(ImageID.IMAGE_SEEDPACKET_LARGER),
                    x, y, 0, 0, g.mScaleX * 0.5f, g.mScaleY * 0.5f);
        } else {
            PvzpCommon.pvzpDrawImageCelScaledF(g, Resources.getImageById(ImageID.IMAGE_SEEDS),
                    x, y, aPacketBackground, 0, g.mScaleX, g.mScaleY);
        }

        float[] aPlacement = getPacketArtPlacement(theSeedType, theImitaterType);
        float aScale = aPlacement[2];
        boolean aDrawSeedInMiddle = !isPacketArtHidden(aSeedType);
        float aOffsetX = g.mScaleX * aPlacement[0];
        float aOffsetY = g.mScaleY * aPlacement[1];
        if (aDrawSeedInMiddle) {
            seedPacketDrawSeed(g, x, y, theSeedType, theImitaterType, aOffsetX, aOffsetY, aScale);
        }

        if (thePercentDark > 0.0f) {
            int aDarknessHeight = PvzpCommon.floatRoundToInt(68.0f * thePercentDark) + 2;
            Graphics aPlantG = new Graphics(g);
            aPlantG.setColor(new Color(64, 64, 64, 255));
            aPlantG.setColorizeImages(true);
            aPlantG.clipRect((int) x, (int) y, GameConstants.SEED_PACKET_WIDTH, aDarknessHeight);
            PvzpCommon.pvzpDrawImageCelScaledF(aPlantG, Resources.getImageById(ImageID.IMAGE_SEEDS),
                    x, y, aPacketBackground, 0, aPlantG.mScaleX, aPlantG.mScaleY);
            if (aDrawSeedInMiddle) {
                seedPacketDrawSeed(aPlantG, x, y, theSeedType, theImitaterType, aOffsetX, aOffsetY, aScale);
            }
        }

        if (theDrawCost) {
            String aCostStr;
            if (LawnApp.gLawnApp.mBoard != null && LawnApp.gLawnApp.mBoard.plantUsesAcceleratedPricing(aSeedType)) {
                if (theUseCurrentCost) {
                    aCostStr = Integer.toString(LawnApp.gLawnApp.mBoard.getCurrentPlantCost(aSeedType, theImitaterType));
                } else {
                    aCostStr = Plant.getCost(aSeedType, theImitaterType) + "+";
                }
            } else {
                aCostStr = Integer.toString(Plant.getCost(aSeedType, theImitaterType));
            }

            Font aTextFont = Resources.getFontById(Resources.FONT_PICO129_ID);
            int aTextOffsetX = 32 - aTextFont.stringWidth(aCostStr);
            int aTextOffsetY = aTextFont.getAscent() + 54;
            if (g.mScaleX == 1.0f && g.mScaleY == 1.0f) {
                PvzpCommon.pvzpDrawString(g, aCostStr, (int) (x + aTextOffsetX), (int) (y + aTextOffsetY),
                        aTextFont, Color.Black, DrawStringJustification.DS_ALIGN_LEFT);
            } else {
                SexyMatrix3 aMatrix = new SexyMatrix3();
                PvzpCommon.pvzpScaleTransformMatrix(aMatrix, aTextOffsetX * g.mScaleX + x,
                        aTextOffsetY * g.mScaleY + y, g.mScaleX, g.mScaleY);
                if (g.mScaleX > 1.8f) {
                    g.setLinearBlend(false);
                }
                PvzpCommon.pvzpDrawStringMatrix(g, aTextFont, aMatrix, aCostStr, Color.Black);
                g.setLinearBlend(true);
            }
        }

        g.setColorizeImages(false);
    }

    /**
     * The placement {@code drawSeedPacket} draws a seed type's plant art at, as
     * {@code {offsetX, offsetY, scale}}.
     *
     * <p>These are the per-seed numbers of {@code SeedPacket::DrawSeedPacket}
     * (SeedPacket.cpp:286-604) and the two corrections after them, for a graphics context at scale
     * {@code mScaleX == mScaleY == 1}: the peashooter and the repeater are {@code {5, 9, 0.5}}, the
     * gatling pea {@code {2, 9, 0.5}}, the threepeater {@code {5, 11, 0.5}}. The vertical offset
     * already includes the {@code + 1.0f} upstream adds, so the caller multiplies the two offsets by
     * {@code mScaleX} / {@code mScaleY} exactly as the inline code did.
     *
     * <p>PORT-NOTE: pure extraction of {@code drawSeedPacket}'s inline computation, so the packet is
     * still drawn pixel for pixel the same. It is public because a mod that renders its own packet
     * art from a retail seed's art ({@code GameArt.packetArt}) has to place the plant the way that
     * seed's own case does, or the icon sits at the wrong offset.
     *
     * @param theSeedType the seed type; an imitater is resolved through {@code theImitaterType}
     * @param theImitaterType the imitated seed type, {@code SeedType.SEED_NONE} for none
     * @return {@code {offsetX, offsetY, scale}}
     */
    public static float[] getPacketArtPlacement(int theSeedType, int theImitaterType) {
        int aSeedType = theSeedType;
        if (aSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
            aSeedType = theImitaterType;
        }

        float aScale = 0.5f;
        float aOffsetX = 5.0f;
        float aOffsetY = 8.0f;
        switch (aSeedType) {
            case SeedType.SEED_TALLNUT -> {
                aScale = 0.3f;
                aOffsetX = 12.0f;
                aOffsetY = 22.0f;
            }

            case SeedType.SEED_INSTANT_COFFEE -> {
                aScale = 0.55f;
                aOffsetX = 0;
                aOffsetY = 9.0f;
            }

            case SeedType.SEED_COBCANNON -> {
                aScale = 0.26f;
                aOffsetX = 6.0f;
                aOffsetY = 22.0f;
            }

            case SeedType.SEED_CACTUS -> {
                aOffsetX = 9.0f;
                aOffsetY = 13.0f;
            }

            case SeedType.SEED_POTATOMINE -> {
                aScale = 0.4f;
                aOffsetX = 8.0f;
                aOffsetY = 12.0f;
            }

            case SeedType.SEED_MAGNETSHROOM -> aOffsetY = 12.0f;

            case SeedType.SEED_FUMESHROOM,
                 SeedType.SEED_PUMPKINSHELL,
                 SeedType.SEED_CHOMPER,
                 SeedType.SEED_DOOMSHROOM,
                 SeedType.SEED_SQUASH,
                 SeedType.SEED_HYPNOSHROOM,
                 SeedType.SEED_SPIKEWEED,
                 SeedType.SEED_SPIKEROCK,
                 SeedType.SEED_PLANTERN,
                 SeedType.SEED_TORCHWOOD,
                 SeedType.SEED_TANGLEKELP -> {
                aScale = 0.4f;
                aOffsetX = 8.0f;
                aOffsetY = 12.0f;
            }

            case SeedType.SEED_TWINSUNFLOWER,
                 SeedType.SEED_GLOOMSHROOM -> {
                aScale = 0.45f;
                aOffsetX = 7.0f;
                aOffsetY = 14.0f;
            }

            case SeedType.SEED_CATTAIL -> {
                aScale = 0.45f;
                aOffsetX = 8.0f;
                aOffsetY = 13.0f;
            }

            case SeedType.SEED_UMBRELLA -> {
                aScale = 0.5f;
                aOffsetX = 5.0f;
                aOffsetY = 10.0f;
            }

            case SeedType.SEED_KERNELPULT -> {
                aScale = 0.4f;
                aOffsetX = 13.0f;
                aOffsetY = 14.0f;
            }

            case SeedType.SEED_CABBAGEPULT -> {
                aScale = 0.4f;
                aOffsetX = 15.0f;
                aOffsetY = 14.0f;
            }

            case SeedType.SEED_MELONPULT,
                 SeedType.SEED_WINTERMELON -> {
                aScale = 0.35f;
                aOffsetX = 18.0f;
                aOffsetY = 19.0f;
            }

            case SeedType.SEED_GRAVEBUSTER -> {
                aScale = 0.4f;
                aOffsetX = 10.0f;
                aOffsetY = 15.0f;
            }

            case SeedType.SEED_SPLITPEA -> {
                aScale = 0.45f;
                aOffsetX = 12.0f;
                aOffsetY = 12.0f;
            }

            case SeedType.SEED_BLOVER -> {
                aScale = 0.4f;
                aOffsetX = 8.0f;
                aOffsetY = 17.0f;
            }

            case SeedType.SEED_STARFRUIT -> {
                aScale = 0.5f;
                aOffsetX = 6.0f;
                aOffsetY = 8.0f;
            }

            case SeedType.SEED_THREEPEATER -> {
                aScale = 0.5f;
                aOffsetX = 5.0f;
                aOffsetY = 10.0f;
            }

            case SeedType.SEED_GATLINGPEA -> {
                aScale = 0.5f;
                aOffsetX = 2.0f;
                aOffsetY = 8.0f;
            }

            case SeedType.SEED_ZOMBIE_NORMAL,
                 SeedType.SEED_ZOMBIE_TRAFFIC_CONE,
                 SeedType.SEED_ZOMBIE_PAIL -> {
                aScale = 0.35f;
                aOffsetX = -3.0f;
                aOffsetY = -7.0f;
            }

            case SeedType.SEED_ZOMBIE_DANCER -> {
                aScale = 0.375f;
                aOffsetX = -19.0f;
                aOffsetY = -40.0f;
            }

            case SeedType.SEED_ZOMBIE_POLEVAULTER -> {
                aScale = 0.35f;
                aOffsetX = -8.0f;
                aOffsetY = -12.0f;
            }

            case SeedType.SEED_ZOMBIE_LADDER,
                 SeedType.SEED_ZOMBIE_DIGGER,
                 SeedType.SEED_ZOMBIE_SCREEN_DOOR,
                 SeedType.SEED_ZOMBIE_POGO -> {
                aScale = 0.35f;
                aOffsetX = -3.0f;
                aOffsetY = -10.0f;
            }

            case SeedType.SEED_ZOMBIE_BUNGEE -> {
                aScale = 0.3f;
                aOffsetX = 1.0f;
                aOffsetY = -1.0f;
            }

            case SeedType.SEED_ZOMBIE_FOOTBALL -> {
                aScale = 0.33f;
                aOffsetX = -7.0f;
                aOffsetY = -9.0f;
            }

            case SeedType.SEED_ZOMBIE_BALLOON -> {
                aScale = 0.35f;
                aOffsetX = -3.0f;
                aOffsetY = -5.0f;
            }

            case SeedType.SEED_ZOMBIE_IMP -> {
                aScale = 0.4f;
                aOffsetX = -12.0f;
                aOffsetY = -17.0f;
            }

            case SeedType.SEED_ZOMBONI -> {
                aScale = 0.23f;
                aOffsetX = 12.0f;
                aOffsetY = 3.0f;
            }

            case SeedType.SEED_ZOMBIE_GARGANTUAR -> {
                aScale = 0.23f;
                aOffsetX = 4.0f;
                aOffsetY = 3.0f;
            }

            default -> {
            }
        }
        if (LawnApp.gLawnApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BIG_TIME) {
            if (aSeedType == SeedType.SEED_WALLNUT || aSeedType == SeedType.SEED_SUNFLOWER
                    || aSeedType == SeedType.SEED_MARIGOLD) {
                aOffsetX = 16.0f;
                aOffsetY = 34.0f;
            }
        }
        if (aSeedType == SeedType.SEED_GIANT_WALLNUT) {
            aScale *= 0.75f;
            aOffsetX = 52.0f;
            aOffsetY = 58.0f;
        }

        return new float[] {aOffsetX, aOffsetY + 1.0f, aScale};
    }

    /**
     * Whether {@code drawSeedPacket} skips the plant art of a seed type: the Beghouled buttons, the
     * slot-machine reels and the Zombiquarium rewards draw a flat icon instead of a plant. Ported from
     * the {@code aDrawSeedInMiddle = false} cases of {@code SeedPacket::DrawSeedPacket}
     * (SeedPacket.cpp:286-604).
     *
     * @param theSeedType a seed type, an imitater already resolved
     * @return whether the packet draws no plant art in the middle
     */
    private static boolean isPacketArtHidden(int theSeedType) {
        return theSeedType == SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE
                || theSeedType == SeedType.SEED_BEGHOULED_BUTTON_CRATER
                || theSeedType == SeedType.SEED_SLOT_MACHINE_SUN
                || theSeedType == SeedType.SEED_SLOT_MACHINE_DIAMOND
                || theSeedType == SeedType.SEED_ZOMBIQUARIUM_SNORKLE
                || theSeedType == SeedType.SEED_ZOMBIQUARIUM_TROPHY;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:606-684 (SeedPacket::Draw). */
    public void draw(Graphics g) {
        float aPercentDark = 0.0f;
        if (!mActive) {
            if (mRefreshTime == 0) {
                aPercentDark = 1.0f;
            } else {
                aPercentDark = (float) (mRefreshTime - mRefreshCounter) / (float) mRefreshTime;
            }
        }

        if (mSlotMachineCountDown > 0) {
            int aOffsetY = PvzpCommon.floatRoundToInt(-mHeight * mSlotMachiningPosition);

            Graphics aClipG = new Graphics(g);
            aClipG.clipRect(0, 0, mWidth, mHeight);

            drawSeedPacket(aClipG, 0.0f, aOffsetY, mPacketType, SeedType.SEED_NONE, 0.0f, 128, false, false);
            drawSeedPacket(aClipG, 0.0f, mHeight + aOffsetY, mSlotMachiningNextSeed, SeedType.SEED_NONE, 0.0f, 128, false, false);
        } else {
            int aUseSeedType = mPacketType;
            if (mPacketType == SeedType.SEED_IMITATER && mImitaterType != SeedType.SEED_NONE) {
                aUseSeedType = mImitaterType;
            }

            boolean aDrawCost = true;
            if (mBoard.hasConveyorBeltSeedBank() || mApp.isSlotMachineLevel()) {
                aDrawCost = false;
            }
            int aCost = mBoard.getCurrentPlantCost(mPacketType, mImitaterType);

            int aGrayness = 255;
            if ((mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED && !mActive)
                    || (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST && !mActive)) {
                aGrayness = 64;
            } else if (mApp.mGameScene != GameScenes.SCENE_PLAYING) {
                aGrayness = mBoard.mSeedBank.mCutSceneDarken;
                aPercentDark = 0.0f;
            } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER
                    && mBoard.mTutorialTimer == -1 && mPacketType == SeedType.SEED_PEASHOOTER) {
                aGrayness = PvzpCommon.getFlashingColor(mBoard.mMainCounter, 75).mRed;
            } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER
                    && mPacketType == SeedType.SEED_SUNFLOWER) {
                aGrayness = PvzpCommon.getFlashingColor(mBoard.mMainCounter, 75).mRed;
            } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER
                    && mPacketType == SeedType.SEED_SUNFLOWER) {
                aGrayness = PvzpCommon.getFlashingColor(mBoard.mMainCounter, 75).mRed;
            } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_WHACK_A_ZOMBIE_PICK_SEED) {
                aGrayness = PvzpCommon.getFlashingColor(mBoard.mMainCounter, 75).mRed;
            } else if (mApp.mEasyPlantingCheat) {
                aPercentDark = 0.0f;
            } else if ((!mBoard.canTakeSunMoney(aCost) && aDrawCost) || aPercentDark > 1.0f
                    || !mBoard.plantingRequirementsMet(aUseSeedType)) {
                aGrayness = 128;
            }

            drawSeedPacket(g, mOffsetX, 0.0f, mPacketType, mImitaterType, aPercentDark, aGrayness, aDrawCost, true);
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:686-724 (SeedPacket::CanPickUp). */
    public boolean canPickUp() {
        if (mBoard.mPaused || mApp.mGameScene != GameScenes.SCENE_PLAYING || mPacketType == SeedType.SEED_NONE) {
            return false;
        }

        int aUseSeedType = mPacketType;
        if (mPacketType == SeedType.SEED_IMITATER && mImitaterType != SeedType.SEED_NONE) {
            aUseSeedType = mImitaterType;
        }

        if (mApp.isSlotMachineLevel()) {
            return false;
        }

        if (!mApp.mEasyPlantingCheat) {
            if (!mActive) {
                return false;
            }

            int aCost = mBoard.getCurrentPlantCost(mPacketType, mImitaterType);
            if (!mBoard.canTakeSunMoney(aCost) && !mBoard.hasConveyorBeltSeedBank()) {
                return false;
            }

            if (!mBoard.plantingRequirementsMet(aUseSeedType)) {
                return false;
            }
        }

        return true;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:726-870 (SeedPacket::MouseDown). */
    public void mouseDown(int x, int y, int theClickCount) {
        if (mBoard.mPaused || mApp.mGameScene != GameScenes.SCENE_PLAYING || mPacketType == SeedType.SEED_NONE) {
            return;
        }

        if (mApp.isSlotMachineLevel()) {
            // MessageWidget::IsBeingDisplayed() is the inline `mDuration != 0` (MessageWidget.h:65).
            if (mBoard.mAdvice.mDuration == 0) {
                mBoard.displayAdvice("[ADVICE_SLOT_MACHINE_PULL]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                        AdviceType.ADVICE_NONE);
            }
            mBoard.mChallenge.mSlotMachineRollCount = Math.min(mBoard.mChallenge.mSlotMachineRollCount, 2);
            return;
        }

        int aUseSeedType = mPacketType;
        if (mPacketType == SeedType.SEED_IMITATER && mImitaterType != SeedType.SEED_NONE) {
            aUseSeedType = mImitaterType;
        }

        if (!mApp.mEasyPlantingCheat) {
            if (!mActive) {
                mApp.playSample(SoundType.SOUND_BUZZER);
                if (mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 1
                        && mBoard.mHelpDisplayed[AdviceType.ADVICE_CLICK_ON_SUN]) {
                    mBoard.displayAdvice("[ADVICE_SEED_REFRESH]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1,
                            AdviceType.ADVICE_SEED_REFRESH);
                }
                return;
            }

            int aCost = mBoard.getCurrentPlantCost(mPacketType, mImitaterType);
            if (!mBoard.canTakeSunMoney(aCost) && !mBoard.hasConveyorBeltSeedBank()) {
                mApp.playSample(SoundType.SOUND_BUZZER);
                mBoard.mOutOfMoneyCounter = 70;
                if (mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 1
                        && mBoard.mHelpDisplayed[AdviceType.ADVICE_CLICK_ON_SUN]) {
                    mBoard.displayAdvice("[ADVICE_CANT_AFFORD_PLANT]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1,
                            AdviceType.ADVICE_CANT_AFFORD_PLANT);
                }
                return;
            }

            if (!mBoard.plantingRequirementsMet(aUseSeedType)) {
                mApp.playSample(SoundType.SOUND_BUZZER);
                switch (aUseSeedType) {
                    case SeedType.SEED_GATLINGPEA -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_REPEATER]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_REPEATER);
                    case SeedType.SEED_WINTERMELON -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_MELONPULT]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_MELONPULT);
                    case SeedType.SEED_TWINSUNFLOWER -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_SUNFLOWER]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_SUNFLOWER);
                    case SeedType.SEED_SPIKEROCK -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_SPIKEWEED]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_SPIKEWEED);
                    case SeedType.SEED_COBCANNON -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_KERNELPULT]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_KERNELPULT);
                    case SeedType.SEED_GOLD_MAGNET -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_MAGNETSHROOM]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_MAGNETSHROOM);
                    case SeedType.SEED_GLOOMSHROOM -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_FUMESHROOM]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_FUMESHROOM);
                    case SeedType.SEED_CATTAIL -> mBoard.displayAdvice("[ADVICE_PLANT_NEEDS_LILYPAD]",
                            MessageStyle.MESSAGE_STYLE_HINT_LONG, AdviceType.ADVICE_PLANT_NEEDS_LILYPAD);
                    default -> throw new IllegalStateException("Unexpected planting requirement " + aUseSeedType);
                }

                return;
            }
        }

        mBoard.clearAdvice(AdviceType.ADVICE_CANT_AFFORD_PLANT);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_REPEATER);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_MELONPULT);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_SUNFLOWER);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_KERNELPULT);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_SPIKEWEED);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_MAGNETSHROOM);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_FUMESHROOM);
        mBoard.clearAdvice(AdviceType.ADVICE_PLANT_NEEDS_LILYPAD);

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            mBoard.mChallenge.beghouledPacketClicked(this);
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            mBoard.mChallenge.zombiquariumPacketClicked(this);
        } else {
            mBoard.mCursorObject.mType = mPacketType;
            mBoard.mCursorObject.mImitaterType = mImitaterType;
            mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_BANK;
            mBoard.mCursorObject.mSeedBankIndex = mIndex;
            mApp.playSample(SoundType.SOUND_SEEDLIFT);

            if (mBoard.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER) {
                mBoard.setTutorialState(TutorialState.TUTORIAL_LEVEL_1_PLANT_PEASHOOTER);
            } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER) {
                if (mPacketType == SeedType.SEED_SUNFLOWER) {
                    mBoard.setTutorialState(TutorialState.TUTORIAL_LEVEL_2_PLANT_SUNFLOWER);
                } else {
                    mBoard.setTutorialState(TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER);
                }
            } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER) {
                if (mPacketType == SeedType.SEED_SUNFLOWER) {
                    mBoard.setTutorialState(TutorialState.TUTORIAL_MORESUN_PLANT_SUNFLOWER);
                } else {
                    mBoard.setTutorialState(TutorialState.TUTORIAL_MORESUN_REFRESH_SUNFLOWER);
                }
            } else if (mBoard.mTutorialState == TutorialState.TUTORIAL_WHACK_A_ZOMBIE_PICK_SEED
                    || mBoard.mTutorialState == TutorialState.TUTORIAL_WHACK_A_ZOMBIE_BEFORE_PICK_SEED) {
                mBoard.setTutorialState(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_COMPLETED);
            }

            deactivate();
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:872-896 (SeedPacket::WasPlanted). */
    public void wasPlanted() {
        assert mPacketType != SeedType.SEED_NONE;

        if (mBoard.hasConveyorBeltSeedBank()) {
            mBoard.mSeedBank.removeSeed(mIndex);
        } else if (mApp.isSlotMachineLevel()) {
            deactivate();
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                && mBoard.mChallenge.mChallengeState != ChallengeState.STATECHALLENGE_LAST_STAND_ONSLAUGHT) {
            mTimesUsed++;
            mActive = true;
            flashIfReady();
        } else {
            mTimesUsed++;
            mRefreshing = true;
            mRefreshTime = Plant.getRefreshTime(mPacketType, mImitaterType);
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:898-917 (SeedPacket::MouseHitTest). */
    public boolean mouseHitTest(int theX, int theY, HitResult theHitResult) {
        if (mSlotMachineCountDown > 0 || mPacketType == SeedType.SEED_NONE) {
            theHitResult.mObject = null;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
            return false;
        }

        if (theX >= mX + mOffsetX && theX < mX + mOffsetX + mWidth && theY >= mY && theY < mY + mHeight) {
            theHitResult.mObject = this;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_SEEDPACKET;
            return true;
        }

        theHitResult.mObject = null;
        theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
        return false;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1107-1145 (SeedPacket::SetPacketType). */
    public void setPacketType(int theSeedType, int theImitaterType) {
        mPacketType = theSeedType;
        mImitaterType = theImitaterType;
        mRefreshCounter = 0;
        mRefreshTime = 0;
        mRefreshing = false;
        mActive = true;

        int aUseSeedType = theSeedType;
        if (theSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
            aUseSeedType = theImitaterType;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                || mApp.isIZombieLevel() || mApp.isScaryPotterLevel() || mApp.isWhackAZombieLevel()
                || (mApp.isSurvivalMode() && mBoard.mChallenge.mSurvivalStage > 0)) {
            return;
        }

        if ((Plant.isUpgrade(aUseSeedType) && !LawnApp.gLawnApp.isSurvivalMode())
                || Plant.getRefreshTime(mPacketType, mImitaterType) == 5000) {
            mRefreshTime = 3500;
            mRefreshing = true;
            mActive = false;
        } else if (Plant.isUpgrade(aUseSeedType) && LawnApp.gLawnApp.isSurvivalMode()) {
            mRefreshTime = 8000;
            mRefreshing = true;
            mActive = false;
        } else if (Plant.getRefreshTime(mPacketType, mImitaterType) == 3000) {
            mRefreshTime = 2000;
            mRefreshing = true;
            mActive = false;
        }
    }
}
