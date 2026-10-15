/*
 * Ported from src/Lawn/CursorObject.h:1-63 and src/Lawn/CursorObject.cpp:31-224.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/CursorObject.h:33-50 and src/Lawn/CursorObject.cpp:23-215.
 */
public class CursorObject extends GameObject {

    public int mSeedBankIndex;
    public int mType;
    public int mImitaterType;
    public int mCursorType;
    public int mCoinID;
    public int mGlovePlantID;
    public int mDuplicatorPlantID;
    public int mCobCannonPlantID;
    public int mHammerDownCounter;
    public int mReanimCursorID;

    /** Ported from src/Lawn/CursorObject.cpp:31-47. */
    public CursorObject() {
        mType = SeedType.SEED_NONE;
        mImitaterType = SeedType.SEED_NONE;
        mSeedBankIndex = -1;
        mX = 0;
        mY = 0;
        mCursorType = CursorType.CURSOR_TYPE_NORMAL;
        mCoinID = CoinID.COINID_NULL;
        mDuplicatorPlantID = PlantID.PLANTID_NULL;
        mCobCannonPlantID = PlantID.PLANTID_NULL;
        mGlovePlantID = PlantID.PLANTID_NULL;
        mReanimCursorID = ReanimationID.REANIMATIONID_NULL;
        mWidth = 80;
        mHeight = 80;
        mHammerDownCounter = 0;
    }

    /** Ported from src/Lawn/CursorObject.cpp:49-72 (CursorObject::Update). */
    public void update() {
        if (mApp.mGameScene != GameScenes.SCENE_PLAYING && !mBoard.mCutScene.isInShovelTutorial()) {
            mVisible = false;
            return;
        }

        if (!mApp.mWidgetManager.mMouseIn) {
            mVisible = false;
            return;
        }

        Reanimation aCursorReanim = mApp.reanimationTryToGet(mReanimCursorID);
        if (aCursorReanim != null) {
            aCursorReanim.update();
        }

        mVisible = true;
        mX = mApp.mWidgetManager.mLastMouseX - 25;
        mY = mApp.mWidgetManager.mLastMouseY - 35;
    }

    /** Ported from src/Lawn/CursorObject.cpp:74-78 (CursorObject::Die). */
    public void die() {
        mApp.removeReanimation(mReanimCursorID);
        mReanimCursorID = ReanimationID.REANIMATIONID_NULL;
    }

    /** Ported from src/Lawn/CursorObject.cpp:80-224 (CursorObject::Draw). */
    public void draw(Graphics g) {
        switch (mCursorType) {
            case CursorType.CURSOR_TYPE_SHOVEL -> g.drawImage(Resources.getImageById(ImageID.IMAGE_SHOVEL), 10, -30);

            case CursorType.CURSOR_TYPE_WATERING_CAN -> {
                if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_GOLD_WATERINGCAN] != 0) {
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_GOLDTOOLRETICLE), -62, -37);
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_WATERINGCANGOLD), -3, 12);
                } else {
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_WATERINGCAN), -3, 12);
                }
            }

            case CursorType.CURSOR_TYPE_FERTILIZER ->
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_FERTILIZER), -15, 0);

            case CursorType.CURSOR_TYPE_BUG_SPRAY ->
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_BUG_SPRAY), -9, -1);

            case CursorType.CURSOR_TYPE_PHONOGRAPH ->
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_PHONOGRAPH), -17, 10);

            case CursorType.CURSOR_TYPE_CHOCOLATE ->
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_CHOCOLATE), -2, -8);

            case CursorType.CURSOR_TYPE_GLOVE ->
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_GARDENGLOVE), -17, 15);

            case CursorType.CURSOR_TYPE_MONEY_SIGN ->
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_MONEYSIGN), -17, -10);

            case CursorType.CURSOR_TYPE_TREE_FOOD ->
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_TREEFOOD), -15, 0);

            case CursorType.CURSOR_TYPE_WHEEELBARROW -> {
                PottedPlant aPottedPlant = mApp.mZenGarden.getPottedPlantInWheelbarrow();
                if (aPottedPlant != null) {
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_WHEELBARROW), -20, -23);
                    if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
                        mApp.mZenGarden.drawPottedPlant(g, 10.0f, -35.0f, aPottedPlant, 0.6f, true);
                    } else if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_MEDIUM) {
                        mApp.mZenGarden.drawPottedPlant(g, 15.0f, -25.0f, aPottedPlant, 0.5f, true);
                    } else {
                        mApp.mZenGarden.drawPottedPlant(g, 21.0f, -15.0f, aPottedPlant, 0.4f, true);
                    }
                } else {
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_WHEELBARROW), -20, -30);
                }
            }

            case CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE -> {
                Plant aPlant = mBoard.mPlants.dataArrayGet(mGlovePlantID);
                PottedPlant aPottedPlant = mApp.mPlayerInfo.mPottedPlant[aPlant.mPottedPlantIndex];
                if (mBoard.mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                        || mBoard.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
                    mApp.mZenGarden.drawPottedPlant(g, -10.0f, -10.0f, aPottedPlant, 1.0f, false);
                } else {
                    mApp.mZenGarden.drawPottedPlant(g, -22.0f, -38.0f, aPottedPlant, 1.0f, true);
                }
            }

            case CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW -> {
                PottedPlant aPottedPlant = mApp.mZenGarden.getPottedPlantInWheelbarrow();
                if (mBoard.mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                        || mBoard.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
                    mApp.mZenGarden.drawPottedPlant(g, -10.0f, -10.0f, aPottedPlant, 1.0f, false);
                } else {
                    mApp.mZenGarden.drawPottedPlant(g, -22.0f, -38.0f, aPottedPlant, 1.0f, true);
                }
            }

            case CursorType.CURSOR_TYPE_PLANT_FROM_BANK,
                 CursorType.CURSOR_TYPE_PLANT_FROM_USABLE_COIN,
                 CursorType.CURSOR_TYPE_PLANT_FROM_DUPLICATOR -> {
                float aOffsetX = -10.0f;
                float aOffsetY = Plant.plantDrawHeightOffset(mBoard, null, mType, -1, -1) - 10.0f;
                if (Plant.isFlying(mType) || mType == SeedType.SEED_GRAVEBUSTER) {
                    aOffsetY += 30.0f;
                }
                aOffsetY -= 15.0f;
                if (mApp.isIZombieLevel()) {
                    aOffsetX -= 55.0f;
                    aOffsetY -= 70.0f;
                }

                Plant.drawSeedType(g, mType, mImitaterType, DrawVariation.VARIATION_NORMAL, aOffsetX, aOffsetY);
            }

            case CursorType.CURSOR_TYPE_HAMMER -> mApp.reanimationGet(mReanimCursorID).draw(g);

            case CursorType.CURSOR_TYPE_COBCANNON_TARGET -> {
                HitResult aHitResult = new HitResult();
                mBoard.mouseHitTest(mBoard.mPrevMouseX, mBoard.mPrevMouseY, aHitResult);
                if (aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_NONE && mBoard.mPrevMouseY >= 80) {
                    g.drawImageCel(Resources.getImageById(ImageID.IMAGE_COBCANNON_TARGET), -11, 7, 0);
                }
            }
            case CursorType.CURSOR_TYPE_NORMAL -> {
            }
            default -> {
            }
        }
    }
}
