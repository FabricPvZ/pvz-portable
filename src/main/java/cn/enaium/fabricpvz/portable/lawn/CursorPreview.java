/*
 * Ported from src/Lawn/CursorObject.h:52-63 and src/Lawn/CursorObject.cpp:227-342.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/CursorObject.h:52-62 and src/Lawn/CursorObject.cpp:217-343.
 */
public class CursorPreview extends GameObject {

    public int mGridX;
    public int mGridY;

    /** Ported from src/Lawn/CursorObject.cpp:227-236. */
    public CursorPreview() {
        mX = 0;
        mY = 0;
        mGridX = 0;
        mGridY = 0;
        mVisible = false;
        mWidth = 80;
        mHeight = 80;
    }

    /** Ported from src/Lawn/CursorObject.cpp:238-276 (CursorPreview::Update). */
    public void update() {
        if (mApp.mGameScene != GameScenes.SCENE_PLAYING && !mBoard.mCutScene.isInShovelTutorial()) {
            mVisible = false;
            return;
        }

        int aSeedType = mBoard.getSeedTypeInCursor();
        int aMouseX = mApp.mWidgetManager.mLastMouseX;
        int aMouseY = mApp.mWidgetManager.mLastMouseY;
        mGridX = mBoard.plantingPixelToGridX(aMouseX, aMouseY, aSeedType);
        mGridY = mBoard.plantingPixelToGridY(aMouseX, aMouseY, aSeedType);
        if (mGridX >= 0 && mGridX < Board.MAX_GRID_SIZE_X && mGridY >= 0 && mGridY <= Board.MAX_GRID_SIZE_Y) {
            boolean aShow = false;
            if (mBoard.isPlantInCursor() && mBoard.canPlantAt(mGridX, mGridY, aSeedType) == PlantingReason.PLANTING_OK) {
                aShow = true;
            } else if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WHEEELBARROW) {
                if (mApp.mZenGarden.getPottedPlantInWheelbarrow() != null
                        && mBoard.canPlantAt(mGridX, mGridY, aSeedType) == PlantingReason.PLANTING_OK) {
                    aShow = true;
                }
            }

            if (aShow) {
                mX = mBoard.gridToPixelX(mGridX, mGridY);
                mY = mBoard.gridToPixelY(mGridX, mGridY);
                mVisible = true;
                return;
            }
        }

        mVisible = false;
    }

    /** Ported from src/Lawn/CursorObject.cpp:278-342 (CursorPreview::Draw). */
    public void draw(Graphics g) {
        int aSeedType = mBoard.getSeedTypeInCursor();
        if (aSeedType == SeedType.SEED_NONE) {
            return;
        }

        g.setColorizeImages(true);
        g.setColor(new Color(255, 255, 255, 100));

        PottedPlant aPottedPlant = null;
        if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WHEEELBARROW
                || mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW) {
            aPottedPlant = mApp.mZenGarden.getPottedPlantInWheelbarrow();
        } else if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE) {
            aPottedPlant = mApp.mPlayerInfo.mPottedPlant[
                    mBoard.mPlants.dataArrayGet(mBoard.mCursorObject.mGlovePlantID).mPottedPlantIndex];
        }

        if (aPottedPlant != null) {
            boolean aDrawPot = true;
            if (mBoard.mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                    || mBoard.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
                aDrawPot = false;
            }
            mApp.mZenGarden.drawPottedPlant(g, 0.0f, 0.0f, aPottedPlant, 1.0f, aDrawPot);
        } else {
            float aOffsetX;
            float aOffsetY;
            if (mApp.isIZombieLevel()) {
                float aHeight = Plant.plantDrawHeightOffset(mBoard, null, aSeedType, mGridX, mGridY);
                if (aSeedType == SeedType.SEED_ZOMBIE_GARGANTUAR) {
                    aHeight -= 30.0f;
                }

                aOffsetY = aHeight - 78.0f;
                aOffsetX = -49.0f;
            } else {
                aOffsetY = Plant.plantDrawHeightOffset(mBoard, null, aSeedType, mGridX, mGridY);
                aOffsetX = 0.0f;
            }

            Plant.drawSeedType(g, mBoard.mCursorObject.mType, mBoard.mCursorObject.mImitaterType,
                    DrawVariation.VARIATION_NORMAL, aOffsetX, aOffsetY);
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                if (y != mGridY && mBoard.canPlantAt(mGridX, y, aSeedType) == PlantingReason.PLANTING_OK) {
                    float aOffsetY = 85.0f * (y - mGridY) + Plant.plantDrawHeightOffset(mBoard, null, aSeedType, mGridX, y);
                    Plant.drawSeedType(g, mBoard.mCursorObject.mType, mBoard.mCursorObject.mImitaterType,
                            DrawVariation.VARIATION_NORMAL, 0.0f, aOffsetY);
                }
            }
        }

        g.setColorizeImages(false);
    }
}
