/*
 * Ported from src/Lawn/Board.cpp:5816-7469 (the Board draw pipeline).
 *
 * Every method is static and takes the owning {@link Board} as its first parameter, so Board can
 * delegate its public draw surface (see Board.h:260-470) to this class. Entity draw calls go
 * through the concrete peer types (Plant/Zombie/Coin/...); the render list is the flat
 * {@link RenderItem} array of Board.cpp:6074, sorted by (mZPos, mAllocId) as the port's replacement
 * for the upstream raw-pointer tie-break (Board.cpp:5926, ../verification/research/03-board-core.md §1.7).
 *
 * Zen-garden branches now call the upstream {@link ZenGarden} APIs (plant overlays, the wheel-
 * barrow button and the zen tool buttons); every branch is implemented for real.
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.util.Arrays;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

final class BoardRender {

    private BoardRender() {
    }

    /** Millisecond clock replacing SDL_GetTicks() (Board.cpp:354, :7449). */
    private static long getTicks() {
        return System.nanoTime() / 1000L;
    }

    /** Ported from src/Lawn/Board.cpp:5816 (Board::GetIceZPos). */
    static int getIceZPos(Board b, int theRow) {
        return Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, theRow, 2);
    }

    /** Ported from src/Lawn/Board.cpp:5821 (Board::DrawIce). */
    static void drawIce(Board b, Graphics g, int theGridY) {
        int aPosY = b.gridToPixelY(8, theGridY) + 20;
        Image aIceImage = Resources.getImageById(ImageID.IMAGE_ICE);
        int aHeight = aIceImage.getHeight();
        int aWidth = aIceImage.getWidth();
        int anAlpha = Math.min(Math.max(255 * b.mIceTimer[theGridY] / 10, 0), 255);
        if (anAlpha < 255) {
            g.setColorizeImages(true);
            g.setColor(new Color(255, 255, 255, anAlpha));
        }

        int aBeginningX = b.mIceMinX[theGridY] + 13;
        int aDeltaX = 0;
        for (int aPosX = aBeginningX; aPosX < GameConstants.BOARD_WIDTH; aPosX += aDeltaX) {
            if (aPosX == aBeginningX) {
                aDeltaX = (GameConstants.BOARD_WIDTH - aBeginningX) % aWidth;
                if (aDeltaX == 0) {
                    aDeltaX = aWidth;
                }
            } else {
                aDeltaX = aWidth;
            }
            Rect aRepeatSrcRect = new Rect(aWidth - aDeltaX, 0, aDeltaX, aHeight);
            Rect aRepeatDstRect = new Rect(aPosX, aPosY, aDeltaX, aHeight);
            g.drawImage(aIceImage, aRepeatDstRect, aRepeatSrcRect);
        }
        g.drawImage(Resources.getImageById(ImageID.IMAGE_ICE_CAP), b.mIceMinX[theGridY], aPosY);
        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/Board.cpp:5850 (Board::DrawBackdrop). */
    static void drawBackdrop(Board b, Graphics g) {
        Image aBgImage = null;
        switch (b.mBackground) {
            case BackgroundType.BACKGROUND_1_DAY:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND1);
                break;
            case BackgroundType.BACKGROUND_2_NIGHT:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND2);
                break;
            case BackgroundType.BACKGROUND_3_POOL:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND3);
                break;
            case BackgroundType.BACKGROUND_4_FOG:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND4);
                break;
            case BackgroundType.BACKGROUND_5_ROOF:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND5);
                break;
            case BackgroundType.BACKGROUND_6_BOSS:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND6BOSS);
                break;
            case BackgroundType.BACKGROUND_MUSHROOM_GARDEN:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND_MUSHROOMGARDEN);
                break;
            case BackgroundType.BACKGROUND_GREENHOUSE:
                aBgImage = Resources.getImageById(ImageID.IMAGE_BACKGROUND_GREENHOUSE);
                break;
            case BackgroundType.BACKGROUND_ZOMBIQUARIUM:
                aBgImage = Resources.getImageById(ImageID.IMAGE_AQUARIUM1);
                break;
            case BackgroundType.BACKGROUND_TREEOFWISDOM:
                aBgImage = null;
                break;
            default:
                break;
        }

        if (b.mLevel == 1 && b.mApp.isFirstTimeAdventureMode()) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1UNSODDED), -GameConstants.BOARD_OFFSET, 0);
            Image aSod1Image = Resources.getImageById(ImageID.IMAGE_SOD1ROW);
            int aWidth = PvzpCommon.pvzpAnimateCurve(0, 1000, b.mSodPosition, 0, aSod1Image.getWidth(), PvzpCurves.CURVE_LINEAR);
            Rect aSrcRect = new Rect(0, 0, aWidth, aSod1Image.getHeight());
            g.drawImage(aSod1Image, 239 - GameConstants.BOARD_OFFSET, 265, aSrcRect);
        } else if (((b.mLevel == 2 || b.mLevel == 3) && b.mApp.isFirstTimeAdventureMode())
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RESODDED) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1UNSODDED), -GameConstants.BOARD_OFFSET, 0);
            Image aSod1Image = Resources.getImageById(ImageID.IMAGE_SOD1ROW);
            g.drawImage(aSod1Image, 239 - GameConstants.BOARD_OFFSET, 265);
            Image aSod3Image = Resources.getImageById(ImageID.IMAGE_SOD3ROW);
            int aWidth = PvzpCommon.pvzpAnimateCurve(0, 1000, b.mSodPosition, 0, aSod3Image.getWidth(), PvzpCurves.CURVE_LINEAR);
            Rect aSrcRect = new Rect(0, 0, aWidth, aSod3Image.getHeight());
            g.drawImage(aSod3Image, 235 - GameConstants.BOARD_OFFSET, 149, aSrcRect);
        } else if (b.mLevel == 4 && b.mApp.isFirstTimeAdventureMode()) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1UNSODDED), -GameConstants.BOARD_OFFSET, 0);
            g.drawImage(Resources.getImageById(ImageID.IMAGE_SOD3ROW), 235 - GameConstants.BOARD_OFFSET, 149);
            Image aBackground1 = Resources.getImageById(ImageID.IMAGE_BACKGROUND1);
            int aWidth = PvzpCommon.pvzpAnimateCurve(0, 1000, b.mSodPosition, 0, 773, PvzpCurves.CURVE_LINEAR);
            Rect aSrcRect = new Rect(232, 0, aWidth, aBackground1.getHeight());
            g.drawImage(aBackground1, 232 - GameConstants.BOARD_OFFSET, 0, aSrcRect);
        } else if (aBgImage != null) {
            if (b.mBackground == BackgroundType.BACKGROUND_MUSHROOM_GARDEN
                    || b.mBackground == BackgroundType.BACKGROUND_GREENHOUSE
                    || b.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
                g.drawImage(aBgImage, 0, 0);
            } else {
                g.drawImage(aBgImage, -GameConstants.BOARD_OFFSET, 0);
            }
        }

        if (b.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            drawHouseDoorBottom(b, g);
        }
        if (b.stageHasPool()) {
            b.mApp.mPoolEffect.poolEffectDraw(g, b.stageIsNight());
        }
        if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PLANT_PEASHOOTER) {
            Graphics aClipG = new Graphics(g);
            aClipG.setColorizeImages(true);
            aClipG.setColor(PvzpCommon.getFlashingColor(b.mMainCounter, 75));
            aClipG.drawImage(Resources.getImageById(ImageID.IMAGE_SOD1ROW), 239 - GameConstants.BOARD_OFFSET, 265);
            aClipG.setColorizeImages(false);
        }
        b.mChallenge.drawBackdrop(g);
        if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && b.stageHasGraveStones()) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_NIGHT_GRAVE_GRAPHIC), 1092, 40);
        }
    }

    /** Comparator replaced by {@link #compareRenderItems} (Board.cpp:5926, RenderItemSortFunc). */
    static int compareRenderItems(RenderItem theItem1, RenderItem theItem2) {
        if (theItem1.mZPos == theItem2.mZPos) {
            return Long.compare(theItem1.mAllocId, theItem2.mAllocId);
        }
        return Integer.compare(theItem1.mZPos, theItem2.mZPos);
    }

    /** Adds one object render item; returns the new list count (replaces the C++ `int&`). */
    private static int addObjectRenderItem(RenderItem[] theRenderList, int theCurRenderItem, int theType,
            int theZPos, long theAllocId) {
        RenderItem aItem = theRenderList[theCurRenderItem];
        if (aItem == null) {
            aItem = new RenderItem();
            theRenderList[theCurRenderItem] = aItem;
        }
        aItem.mRenderObjectType = theType;
        aItem.mZPos = theZPos;
        aItem.mAllocId = theAllocId;
        return theCurRenderItem + 1;
    }

    /** Ported from src/Lawn/Board.cpp:5936 (Board::AddBossRenderItem). */
    static int addBossRenderItem(Board b, RenderItem[] theRenderList, int theCurRenderItem, Zombie theBossZombie) {
        int aBackLegRow = 1;
        int aFrontLegRow = 3;
        int aBackArmRow = 4;
        if (theBossZombie.isDeadOrDying()) {
            aBackArmRow = 1;
        } else if (theBossZombie.mZombiePhase == ZombiePhase.PHASE_BOSS_STOMPING) {
            Reanimation aBossReanim = b.mApp.reanimationTryToGet(theBossZombie.mBodyReanimID);
            if (aBossReanim != null && aBossReanim.mAnimTime > 0.25f && aBossReanim.mAnimTime < 0.75f) {
                if (theBossZombie.mTargetRow == 1) {
                    aBackLegRow = 2;
                } else if (theBossZombie.mTargetRow == 3) {
                    aFrontLegRow = 4;
                }
            }
        }

        RenderItem aItem = theRenderList[theCurRenderItem];
        if (aItem == null) {
            aItem = new RenderItem();
            theRenderList[theCurRenderItem] = aItem;
        }
        aItem.mRenderObjectType = RenderObjectType.RENDER_ITEM_BOSS_PART;
        aItem.mZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_BOSS, aBackLegRow, 2);
        aItem.mAllocId = theBossZombie.mAllocId;
        aItem.mBossPart = BossPart.BOSS_PART_BACK_LEG;
        theCurRenderItem++;

        aItem = theRenderList[theCurRenderItem];
        if (aItem == null) {
            aItem = new RenderItem();
            theRenderList[theCurRenderItem] = aItem;
        }
        aItem.mRenderObjectType = RenderObjectType.RENDER_ITEM_BOSS_PART;
        aItem.mZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_BOSS, aFrontLegRow, 2);
        aItem.mAllocId = theBossZombie.mAllocId;
        aItem.mBossPart = BossPart.BOSS_PART_FRONT_LEG;
        theCurRenderItem++;

        aItem = theRenderList[theCurRenderItem];
        if (aItem == null) {
            aItem = new RenderItem();
            theRenderList[theCurRenderItem] = aItem;
        }
        aItem.mRenderObjectType = RenderObjectType.RENDER_ITEM_BOSS_PART;
        aItem.mZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_BOSS, 4, 2);
        aItem.mAllocId = theBossZombie.mAllocId;
        aItem.mBossPart = BossPart.BOSS_PART_MAIN;
        theCurRenderItem++;

        aItem = theRenderList[theCurRenderItem];
        if (aItem == null) {
            aItem = new RenderItem();
            theRenderList[theCurRenderItem] = aItem;
        }
        aItem.mRenderObjectType = RenderObjectType.RENDER_ITEM_BOSS_PART;
        aItem.mZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_BOSS, aBackArmRow, 3);
        aItem.mAllocId = theBossZombie.mAllocId;
        aItem.mBossPart = BossPart.BOSS_PART_BACK_ARM;
        theCurRenderItem++;

        Reanimation aBallReanim = b.mApp.reanimationTryToGet(theBossZombie.mBossFireBallReanimID);
        if (aBallReanim != null) {
            aItem = theRenderList[theCurRenderItem];
            if (aItem == null) {
                aItem = new RenderItem();
                theRenderList[theCurRenderItem] = aItem;
            }
            aItem.mRenderObjectType = RenderObjectType.RENDER_ITEM_BOSS_PART;
            aItem.mZPos = aBallReanim.mRenderOrder;
            aItem.mAllocId = theBossZombie.mAllocId;
            aItem.mBossPart = BossPart.BOSS_PART_FIREBALL;
            theCurRenderItem++;
        }
        return theCurRenderItem;
    }

    /** Ported from src/Lawn/Board.cpp:6074 (Board::DrawGameObjects). */
    static void drawGameObjects(Board b, Graphics g) {
        RenderItem[] aRenderList = new RenderItem[Board.MAX_RENDER_ITEMS];
        int aRenderItemCount = 0;

        for (Plant aPlant : b.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mOnBungeeState == PlantOnBungeeState.NOT_ON_BUNGEE) {
                aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                        RenderObjectType.RENDER_ITEM_PLANT, aPlant.mRenderOrder, aPlant.mAllocId);
                aRenderList[aRenderItemCount - 1].mPlant = aPlant;

                if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN && aPlant.mPottedPlantIndex != -1) {
                    aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                            RenderObjectType.RENDER_ITEM_PLANT_OVERLAY,
                            Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, 0, b.mY), aPlant.mAllocId);
                    aRenderList[aRenderItemCount - 1].mPlant = aPlant;
                }

                if ((aPlant.mSeedType == SeedType.SEED_MAGNETSHROOM || aPlant.mSeedType == SeedType.SEED_GOLD_MAGNET)
                        && aPlant.drawMagnetItemsOnTop()) {
                    aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                            RenderObjectType.RENDER_ITEM_PLANT_MAGNET_ITEMS,
                            Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, -1), aPlant.mAllocId);
                    aRenderList[aRenderItemCount - 1].mPlant = aPlant;
                }
            }
        }

        for (Coin aCoin : b.mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                    RenderObjectType.RENDER_ITEM_COIN, aCoin.mRenderOrder, aCoin.mAllocId);
            aRenderList[aRenderItemCount - 1].mCoin = aCoin;
        }

        for (Zombie aZombie : b.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                aRenderItemCount = addBossRenderItem(b, aRenderList, aRenderItemCount, aZombie);
            } else {
                aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                        RenderObjectType.RENDER_ITEM_ZOMBIE, aZombie.mRenderOrder, aZombie.mAllocId);
                aRenderList[aRenderItemCount - 1].mZombie = aZombie;

                if (aZombie.hasShadow()) {
                    aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                            RenderObjectType.RENDER_ITEM_ZOMBIE_SHADOW,
                            Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, aZombie.mRow, 3), aZombie.mAllocId);
                    aRenderList[aRenderItemCount - 1].mZombie = aZombie;
                }

                if (aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
                    aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                            RenderObjectType.RENDER_ITEM_ZOMBIE_BUNGEE_TARGET,
                            Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PROJECTILE, aZombie.mRow, 1), aZombie.mAllocId);
                    aRenderList[aRenderItemCount - 1].mZombie = aZombie;
                }
            }
        }

        for (Projectile aProjectile : b.mProjectiles) {
            if (aProjectile.mDead) {
                continue;
            }
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                    RenderObjectType.RENDER_ITEM_PROJECTILE, aProjectile.mRenderOrder, aProjectile.mAllocId);
            aRenderList[aRenderItemCount - 1].mProjectile = aProjectile;

            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                    RenderObjectType.RENDER_ITEM_PROJECTILE_SHADOW,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GROUND, aProjectile.mRow, 3), aProjectile.mAllocId);
            aRenderList[aRenderItemCount - 1].mProjectile = aProjectile;
        }

        for (LawnMower aLawnMower : b.mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                    RenderObjectType.RENDER_ITEM_MOWER, aLawnMower.mRenderOrder, aLawnMower.mAllocId);
            aRenderList[aRenderItemCount - 1].mMower = aLawnMower;
        }

        for (PvzpParticleSystem aParticle : b.mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            if (aParticle.mDead) {
                continue;
            }
            if (!aParticle.mIsAttachment) {
                aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                        RenderObjectType.RENDER_ITEM_PARTICLE, aParticle.mRenderOrder, aRenderItemCount);
                aRenderList[aRenderItemCount - 1].mParticleSytem = aParticle;
            }
        }

        for (Reanimation aReanimation : b.mApp.mEffectSystem.mReanimationHolder.mReanimations) {
            if (aReanimation.mDead) {
                continue;
            }
            if (!aReanimation.mIsAttachment) {
                aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                        RenderObjectType.RENDER_ITEM_REANIMATION, aReanimation.mRenderOrder, aRenderItemCount);
                aRenderList[aRenderItemCount - 1].mReanimation = aReanimation;
            }
        }

        for (GridItem aGridItem : b.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                    RenderObjectType.RENDER_ITEM_GRID_ITEM, aGridItem.mRenderOrder, aGridItem.mAllocId);
            aRenderList[aRenderItemCount - 1].mGridItem = aGridItem;

            if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                    && aGridItem.mGridItemType == GridItemType.GRIDITEM_STINKY) {
                aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                        RenderObjectType.RENDER_ITEM_GRID_ITEM_OVERLAY,
                        Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, 0, (int) (aGridItem.mPosY - 30.0f)),
                        aGridItem.mAllocId);
                aRenderList[aRenderItemCount - 1].mGridItem = aGridItem;
            }
        }

        for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
            if (b.mIceTimer[i] != 0) {
                aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                        RenderObjectType.RENDER_ITEM_ICE, getIceZPos(b, i), aRenderItemCount);
                aRenderList[aRenderItemCount - 1].mBoardGridY = i;
            }
        }

        {
            int aZPos;
            if (b.mTimeStopCounter > 0) {
                aZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
            } else if (b.mApp.mGameScene == GameScenes.SCENE_PLAYING || b.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
                aZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_UI_BOTTOM, 0, 1);
            } else if (b.mCutScene.isAfterSeedChooser() || b.mCutScene.isInShovelTutorial()
                    || b.mHelpIndex == AdviceType.ADVICE_CLICK_TO_CONTINUE) {
                aZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_UI_BOTTOM, 0, 1);
            } else {
                aZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
            }

            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount, RenderObjectType.RENDER_ITEM_BACKDROP,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_UI_BOTTOM, 0, 0), aRenderItemCount);
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount, RenderObjectType.RENDER_ITEM_BOTTOM_UI,
                    aZPos, aRenderItemCount);
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount, RenderObjectType.RENDER_ITEM_COIN_BANK,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_COIN_BANK, 0, 0), aRenderItemCount);
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount, RenderObjectType.RENDER_ITEM_TOP_UI,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_UI_TOP, 0, 0), aRenderItemCount);
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount, RenderObjectType.RENDER_ITEM_SCREEN_FADE,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_SCREEN_FADE, 0, 0), aRenderItemCount);
        }
        if (b.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            int aZPos;
            if (b.stageHasRoof()) {
                aZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, 0, 4);
            } else {
                aZPos = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, 3, 2);
            }
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                    RenderObjectType.RENDER_ITEM_DOOR_MASK, aZPos, aRenderItemCount);
        }
        if (b.stageHasFog()) {
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount, RenderObjectType.RENDER_ITEM_FOG,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_FOG, 0, 0), aRenderItemCount);
        }
        if (b.mApp.isStormyNightLevel() || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS) {
            aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount, RenderObjectType.RENDER_ITEM_STORM,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_FOG, 0, 3), aRenderItemCount);
        }
        aRenderItemCount = addObjectRenderItem(aRenderList, aRenderItemCount,
                RenderObjectType.RENDER_ITEM_CURSOR_PREVIEW, b.mCursorPreview.mRenderOrder, aRenderItemCount);
        aRenderList[aRenderItemCount - 1].mCursorPreview = b.mCursorPreview;

        Arrays.sort(aRenderList, 0, aRenderItemCount, BoardRender::compareRenderItems);

        for (int i = 0; i < aRenderItemCount; i++) {
            RenderItem aRenderItem = aRenderList[i];
            switch (aRenderItem.mRenderObjectType) {
                case RenderObjectType.RENDER_ITEM_PLANT: {
                    Plant aPlant = aRenderItem.mPlant;
                    if (aPlant.beginDraw(g)) {
                        aPlant.draw(g);
                        aPlant.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_PLANT_OVERLAY: {
                    Plant aPlant = aRenderItem.mPlant;
                    if (aPlant.beginDraw(g)) {
                        b.mApp.mZenGarden.drawPlantOverlay(g, aPlant);
                        aPlant.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_PLANT_MAGNET_ITEMS: {
                    Plant aPlant = aRenderItem.mPlant;
                    if (aPlant.beginDraw(g)) {
                        aPlant.drawMagnetItems(g);
                        aPlant.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_MOWER: {
                    LawnMower aLawnMower = aRenderItem.mMower;
                    aLawnMower.draw(g);
                    break;
                }
                case RenderObjectType.RENDER_ITEM_ZOMBIE: {
                    Zombie aZombie = aRenderItem.mZombie;
                    if (aZombie.beginDraw(g)) {
                        aZombie.draw(g);
                        aZombie.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_ZOMBIE_SHADOW: {
                    Zombie aZombie = aRenderItem.mZombie;
                    if (aZombie.beginDraw(g)) {
                        aZombie.drawShadow(g);
                        aZombie.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_ZOMBIE_BUNGEE_TARGET: {
                    Zombie aZombie = aRenderItem.mZombie;
                    aZombie.drawBungeeTarget(g);
                    break;
                }
                case RenderObjectType.RENDER_ITEM_BOSS_PART: {
                    Zombie aBossZombie = b.getBossZombie();
                    if (aBossZombie != null && aBossZombie.beginDraw(g)) {
                        aBossZombie.drawBossPart(g, aRenderItem.mBossPart);
                        aBossZombie.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_COIN: {
                    Coin aCoin = aRenderItem.mCoin;
                    if (aCoin.beginDraw(g)) {
                        aCoin.draw(g);
                        aCoin.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_PROJECTILE: {
                    Projectile aProjectile = aRenderItem.mProjectile;
                    if (aProjectile.beginDraw(g)) {
                        aProjectile.draw(g);
                        aProjectile.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_PROJECTILE_SHADOW: {
                    Projectile aProjectile = aRenderItem.mProjectile;
                    if (aProjectile.beginDraw(g)) {
                        aProjectile.drawShadow(g);
                        aProjectile.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_CURSOR_PREVIEW: {
                    CursorPreview aCursorPreview = aRenderItem.mCursorPreview;
                    if (aCursorPreview.beginDraw(g)) {
                        aCursorPreview.draw(g);
                        aCursorPreview.endDraw(g);
                    }
                    break;
                }
                case RenderObjectType.RENDER_ITEM_GRID_ITEM: {
                    GridItem aGridItem = aRenderItem.mGridItem;
                    aGridItem.drawGridItem(g);
                    break;
                }
                case RenderObjectType.RENDER_ITEM_GRID_ITEM_OVERLAY: {
                    GridItem aGridItem = aRenderItem.mGridItem;
                    aGridItem.drawGridItemOverlay(g);
                    break;
                }
                case RenderObjectType.RENDER_ITEM_ICE:
                    drawIce(b, g, aRenderItem.mBoardGridY);
                    break;
                case RenderObjectType.RENDER_ITEM_PARTICLE: {
                    PvzpParticleSystem aParticle = (PvzpParticleSystem) aRenderItem.mParticleSytem;
                    aParticle.draw(g);
                    break;
                }
                case RenderObjectType.RENDER_ITEM_REANIMATION: {
                    Reanimation aReanimation = aRenderItem.mReanimation;
                    aReanimation.draw(g);
                    break;
                }
                case RenderObjectType.RENDER_ITEM_COIN_BANK:
                    drawUICoinBank(b, g);
                    break;
                case RenderObjectType.RENDER_ITEM_BACKDROP:
                    drawBackdrop(b, g);
                    break;
                case RenderObjectType.RENDER_ITEM_DOOR_MASK:
                    drawHouseDoorTop(b, g);
                    break;
                case RenderObjectType.RENDER_ITEM_BOTTOM_UI:
                    drawUIBottom(b, g);
                    break;
                case RenderObjectType.RENDER_ITEM_TOP_UI:
                    drawUITop(b, g);
                    break;
                case RenderObjectType.RENDER_ITEM_FOG:
                    drawFog(b, g);
                    break;
                case RenderObjectType.RENDER_ITEM_STORM:
                    b.mChallenge.drawWeather(g);
                    break;
                case RenderObjectType.RENDER_ITEM_SCREEN_FADE:
                    drawFadeOut(b, g);
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/Lawn/Board.cpp:6488 (Board::HasProgressMeter). */
    static boolean hasProgressMeter(Board b) {
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || b.mApp.isFinalBossLevel()
                || b.mApp.isSlotMachineLevel()
                || b.mApp.isSquirrelLevel()
                || b.mApp.isIZombieLevel()) {
            return true;
        }

        if (b.mProgressMeterWidth == 0) {
            return false;
        }

        if (b.mApp.isContinuousChallenge()
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || b.mApp.isScaryPotterLevel()) {
            return false;
        }

        return true;
    }

    /** Ported from src/Lawn/Board.cpp:6510 (Board::ProgressMeterHasFlags). */
    static boolean progressMeterHasFlags(Board b) {
        if (b.mApp.isFirstTimeAdventureMode() && b.mLevel == 1) {
            return false;
        }

        if (b.mApp.isWhackAZombieLevel()
                || b.mApp.isFinalBossLevel()
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || b.mApp.isSlotMachineLevel()
                || b.mApp.isSquirrelLevel()
                || b.mApp.isIZombieLevel()) {
            return false;
        }

        return true;
    }

    /** Ported from src/Lawn/Board.cpp:6527 (Board::DrawProgressMeter). */
    static void drawProgressMeter(Board b, Graphics g) {
        if (!hasProgressMeter(b)) {
            return;
        }

        Image aFlagMeter = Resources.getImageById(ImageID.IMAGE_FLAGMETER);
        g.drawImageCel(aFlagMeter, 600, 575, 0);
        int aCelWidth = aFlagMeter.getCelWidth();
        int aCelHeight = aFlagMeter.getCelHeight();
        int aClipWidth = PvzpCommon.pvzpAnimateCurve(0, Board.PROGRESS_METER_COUNTER, b.mProgressMeterWidth, 0, 143,
                PvzpCurves.CURVE_LINEAR);
        Rect aSrcRect = new Rect(aCelWidth - aClipWidth - 7, aCelHeight, aClipWidth, aCelHeight);
        Rect aDstRect = new Rect(aCelWidth - aClipWidth + 593, 575, aClipWidth, aCelHeight);
        g.drawImage(aFlagMeter, aDstRect, aSrcRect);

        int aPosX = aCelWidth / 2 + 600;
        Color aColor = new Color(224, 187, 98);
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            String aMatchStr = b.mChallenge.mChallengeScore + "/75 " + PvzpStringFile.translate("[MATCHES]");
            PvzpCommon.pvzpDrawString(g, aMatchStr, aPosX, 589, Resources.getFontById(Resources.FONT_DWARVENTODCRAFT12_ID),
                    aColor, DrawStringJustification.DS_ALIGN_CENTER);
        } else if (b.mApp.isSquirrelLevel()) {
            String aMatchStr = b.mChallenge.mChallengeScore + "/7 " + PvzpStringFile.translate("[SQUIRRELS]");
            PvzpCommon.pvzpDrawString(g, aMatchStr, aPosX, 589, Resources.getFontById(Resources.FONT_DWARVENTODCRAFT12_ID),
                    aColor, DrawStringJustification.DS_ALIGN_CENTER);
        } else if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE) {
            int aSunMoney = Math.min(Math.max(b.mSunMoney, 0), 2000);
            String aMatchStr = aSunMoney + "/2000 " + PvzpStringFile.translate("[SUN]");
            PvzpCommon.pvzpDrawString(g, aMatchStr, aPosX, 589, Resources.getFontById(Resources.FONT_DWARVENTODCRAFT12_ID),
                    aColor, DrawStringJustification.DS_ALIGN_CENTER);
        } else if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            int aSunMoney = Math.min(Math.max(b.mSunMoney, 0), 1000);
            String aMatchStr = aSunMoney + "/1000 " + PvzpStringFile.translate("[SUN]");
            PvzpCommon.pvzpDrawString(g, aMatchStr, aPosX, 589, Resources.getFontById(Resources.FONT_DWARVENTODCRAFT12_ID),
                    aColor, DrawStringJustification.DS_ALIGN_CENTER);
        } else if (b.mApp.isIZombieLevel()) {
            String aMatchStr = b.mChallenge.mChallengeScore + "/5 " + PvzpStringFile.translate("[BRAINS]");
            PvzpCommon.pvzpDrawString(g, aMatchStr, aPosX, 589, Resources.getFontById(Resources.FONT_DWARVENTODCRAFT12_ID),
                    aColor, DrawStringJustification.DS_ALIGN_CENTER);
        } else if (progressMeterHasFlags(b)) {
            int aNumWavesPerFlag = b.getNumWavesPerFlag();
            int aNumFlagWaves = b.mNumWaves / aNumWavesPerFlag;
            int aFlagsPosEnd = 590 + aCelWidth;
            for (int aFlagWave = 1; aFlagWave <= aNumFlagWaves; aFlagWave++) {
                int aHeight = 0;
                int aTotalWavesAtFlag = aFlagWave * aNumWavesPerFlag;
                if (aTotalWavesAtFlag < b.mCurrentWave) {
                    aHeight = 14;
                } else if (aTotalWavesAtFlag == b.mCurrentWave) {
                    aHeight = PvzpCommon.pvzpAnimateCurve(100, 0, b.mFlagRaiseCounter, 0, 14, PvzpCurves.CURVE_LINEAR);
                }
                int aFlagPosX = PvzpCommon.pvzpAnimateCurve(0, b.mNumWaves, aTotalWavesAtFlag, aFlagsPosEnd, 606,
                        PvzpCurves.CURVE_LINEAR);
                g.drawImageCel(Resources.getImageById(ImageID.IMAGE_FLAGMETERPARTS), aFlagPosX, 571, 1, 0);
                g.drawImageCel(Resources.getImageById(ImageID.IMAGE_FLAGMETERPARTS), aFlagPosX, 572 - aHeight, 2, 0);
            }
        }

        g.drawImage(Resources.getImageById(ImageID.IMAGE_FLAGMETERLEVELPROGRESS), 638, 589);
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM
                || b.mApp.isSquirrelLevel()
                || b.mApp.isSlotMachineLevel()
                || b.mApp.isIZombieLevel()
                || b.mApp.isFinalBossLevel()) {
            return;
        }
        int aHeadProgress = PvzpCommon.pvzpAnimateCurve(0, 150, b.mProgressMeterWidth, 0, 135, PvzpCurves.CURVE_LINEAR);
        g.drawImageCel(Resources.getImageById(ImageID.IMAGE_FLAGMETERPARTS), aCelWidth - aHeadProgress + 580, 572, 0, 0);
    }

    /** Ported from src/Lawn/Board.cpp:6606 (Board::DrawHouseDoorBottom). */
    static void drawHouseDoorBottom(Board b, Graphics g) {
        switch (b.mBackground) {
            case BackgroundType.BACKGROUND_1_DAY:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1_GAMEOVER_INTERIOR_OVERLAY), -126, 225);
                break;
            case BackgroundType.BACKGROUND_2_NIGHT:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND2_GAMEOVER_INTERIOR_OVERLAY), -125, 196);
                break;
            case BackgroundType.BACKGROUND_3_POOL:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND3_GAMEOVER_INTERIOR_OVERLAY), -171, 241);
                break;
            case BackgroundType.BACKGROUND_4_FOG:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND4_GAMEOVER_INTERIOR_OVERLAY), -172, 246);
                break;
            default:
                break;
        }
    }

    /** Ported from src/Lawn/Board.cpp:6618 (Board::DrawHouseDoorTop). */
    static void drawHouseDoorTop(Board b, Graphics g) {
        switch (b.mBackground) {
            case BackgroundType.BACKGROUND_1_DAY:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND1_GAMEOVER_MASK), -130, 202);
                break;
            case BackgroundType.BACKGROUND_2_NIGHT:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND2_GAMEOVER_MASK), -128, 207);
                break;
            case BackgroundType.BACKGROUND_3_POOL:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND3_GAMEOVER_MASK), -172, 234);
                break;
            case BackgroundType.BACKGROUND_4_FOG:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND4_GAMEOVER_MASK), -173, 133);
                break;
            case BackgroundType.BACKGROUND_5_ROOF:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND5_GAMEOVER_MASK), -220, 81);
                break;
            case BackgroundType.BACKGROUND_6_BOSS:
                g.drawImage(Resources.getImageById(ImageID.IMAGE_BACKGROUND6_GAMEOVER_MASK), -220, 81);
                break;
            default:
                break;
        }
    }

    /** Ported from src/Lawn/Board.cpp:6632 (Board::DrawLevel). */
    static void drawLevel(Board b, Graphics g) {
        String aLevelStr;
        if (b.mApp.isAdventureMode()) {
            aLevelStr = PvzpStringFile.translate("[LEVEL]") + " " + LawnApp.getStageString(b.mLevel);
        } else {
            aLevelStr = b.mApp.getCurrentChallengeDef().mChallengeName;
            if (b.mApp.isSurvivalMode() || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
                int aFlags = b.getSurvivalFlagsCompleted();
                if (aFlags > 0) {
                    String aFlagStr = LawnApp.pluralize(aFlags, "[ONE_FLAG]", "[COUNT_FLAGS]");
                    String aCompletedStr = PvzpCommon.pvzpReplaceString("[FLAGS_COMPLETED]", "{FLAGS}", aFlagStr);
                    aLevelStr = PvzpStringFile.translate(aLevelStr) + " - " + aCompletedStr;
                }
            } else if (LawnApp.isEndlessIZombie(b.mApp.mGameMode)
                    || LawnApp.isEndlessScaryPotter(b.mApp.mGameMode)) {
                int aStreak = b.mChallenge.mSurvivalStage;
                if (b.mNextSurvivalStageCounter > 0) {
                    aStreak++;
                }
                if (aStreak > 0) {
                    String aStreakStr = PvzpCommon.pvzpReplaceNumberString("[ENDLESS_STREAK]", "{STREAK}", aStreak);
                    aLevelStr = PvzpStringFile.translate(aLevelStr) + " - " + aStreakStr;
                }
            }
        }

        int aPosX = 780;
        int aPosY = 595;
        if (hasProgressMeter(b)) {
            aPosX = 593;
        }
        if (b.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_ZEN_FADING) {
            aPosY += PvzpCommon.pvzpAnimateCurve(50, 0, b.mChallenge.mChallengeStateCounter, 0, 50,
                    PvzpCurves.CURVE_EASE_IN_OUT);
        }
        PvzpCommon.pvzpDrawString(g, aLevelStr, aPosX, aPosY, Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID),
                new Color(224, 187, 98), DrawStringJustification.DS_ALIGN_RIGHT);
    }

    /** Ported from src/Lawn/Board.cpp:6680 (Board::DrawZenWheelBarrowButton). */
    static void drawZenWheelBarrowButton(Board b, Graphics g, int theOffsetY) {
        Rect aButtonRect = b.getShovelButtonRect();
        b.getZenButtonRect(GameObjectType.OBJECT_TYPE_WHEELBARROW, aButtonRect);
        PottedPlant aPlant = b.mApp.mZenGarden.getPottedPlantInWheelbarrow();
        if (aPlant != null && b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW) {
            if (b.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_ZEN_FADING) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_WHEELBARROW), aButtonRect.mX - 7,
                        aButtonRect.mY + theOffsetY - 3);
            } else {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_WHEELBARROW), aButtonRect.mX - 7,
                        aButtonRect.mY + theOffsetY + 4);
            }

            if (aPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
                b.mApp.mZenGarden.drawPottedPlant(g, aButtonRect.mX + 23, aButtonRect.mY + theOffsetY - 8,
                        aPlant, 0.6f, true);
            } else if (aPlant.mPlantAge == PottedPlantAge.PLANTAGE_MEDIUM) {
                b.mApp.mZenGarden.drawPottedPlant(g, aButtonRect.mX + 28, aButtonRect.mY + theOffsetY + 2,
                        aPlant, 0.5f, true);
            } else {
                b.mApp.mZenGarden.drawPottedPlant(g, aButtonRect.mX + 34, aButtonRect.mY + theOffsetY + 12,
                        aPlant, 0.4f, true);
            }
        } else {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_WHEELBARROW), aButtonRect.mX - 7,
                    aButtonRect.mY + theOffsetY - 3);
        }
    }

    /** Ported from src/Lawn/Board.cpp:6715 (Board::DrawZenButtons). */
    static void drawZenButtons(Board b, Graphics g) {
        int aOffsetY = 0;
        if (b.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_ZEN_FADING) {
            aOffsetY = PvzpCommon.pvzpAnimateCurve(50, 0, b.mChallenge.mChallengeStateCounter, 0, -72,
                    PvzpCurves.CURVE_EASE_IN_OUT);
        }

        for (int aTool = GameObjectType.OBJECT_TYPE_WATERING_CAN; aTool <= GameObjectType.OBJECT_TYPE_NEXT_GARDEN;
                aTool++) {
            if (!b.canUseGameObject(aTool)) {
                continue;
            }

            Rect aButtonRect = b.getShovelButtonRect();
            if (aTool == GameObjectType.OBJECT_TYPE_NEXT_GARDEN) {
                aButtonRect.mX = 564;
                if (!b.mMenuButton.mBtnNoDraw) {
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_NEXTGARDEN), aButtonRect.mX + 2,
                            aButtonRect.mY + aOffsetY);
                }
            } else {
                b.getZenButtonRect(aTool, aButtonRect);
                g.drawImage(Resources.getImageById(ImageID.IMAGE_SHOVELBANK), aButtonRect.mX, aButtonRect.mY + aOffsetY);
                if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WATERING_CAN + aTool - 6) {
                    continue; // skip drawing a tool currently held by the cursor
                }

                switch (aTool) {
                    case GameObjectType.OBJECT_TYPE_WATERING_CAN:
                        if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_GOLD_WATERINGCAN] != 0) {
                            g.drawImage(Resources.getImageById(ImageID.IMAGE_WATERINGCANGOLD), aButtonRect.mX - 2,
                                    aButtonRect.mY + aOffsetY - 6);
                        } else {
                            g.drawImage(Resources.getImageById(ImageID.IMAGE_WATERINGCAN), aButtonRect.mX - 2,
                                    aButtonRect.mY + aOffsetY - 6);
                        }
                        break;
                    case GameObjectType.OBJECT_TYPE_FERTILIZER: {
                        int aPurchase = b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER];
                        int aCharges = aPurchase > PlayerInfo.PURCHASE_COUNT_OFFSET
                                ? aPurchase - PlayerInfo.PURCHASE_COUNT_OFFSET
                                : 0;
                        if (aCharges == 0) {
                            g.setColorizeImages(true);
                            g.setColor(new Color(96, 96, 96));
                        } else if (b.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_FERTILIZE_PLANTS) {
                            g.setColorizeImages(true);
                            g.setColor(PvzpCommon.getFlashingColor(b.mMainCounter, 75));
                        }
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_FERTILIZER), aButtonRect.mX - 6,
                                aButtonRect.mY + aOffsetY - 7);
                        g.setColorizeImages(false);

                        String aChargeString = "x" + aCharges;
                        PvzpCommon.pvzpDrawString(g, aChargeString, aButtonRect.mX + 64, aButtonRect.mY + aOffsetY + 65,
                                Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), new Color(255, 255, 255),
                                DrawStringJustification.DS_ALIGN_RIGHT);
                        break;
                    }
                    case GameObjectType.OBJECT_TYPE_BUG_SPRAY: {
                        int aPurchase = b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY];
                        int aCharges = aPurchase > PlayerInfo.PURCHASE_COUNT_OFFSET
                                ? aPurchase - PlayerInfo.PURCHASE_COUNT_OFFSET
                                : 0;
                        if (aCharges == 0) {
                            g.setColorizeImages(true);
                            g.setColor(new Color(128, 128, 128));
                        }
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_BUG_SPRAY), aButtonRect.mX,
                                aButtonRect.mY + aOffsetY - 1);
                        g.setColorizeImages(false);

                        String aChargeString = "x" + aCharges;
                        PvzpCommon.pvzpDrawString(g, aChargeString, aButtonRect.mX + 64, aButtonRect.mY + aOffsetY + 65,
                                Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), new Color(255, 255, 255),
                                DrawStringJustification.DS_ALIGN_RIGHT);
                        break;
                    }
                    case GameObjectType.OBJECT_TYPE_PHONOGRAPH:
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_PHONOGRAPH), aButtonRect.mX + 2,
                                aButtonRect.mY + aOffsetY + 2);
                        break;
                    case GameObjectType.OBJECT_TYPE_CHOCOLATE: {
                        int aPurchase = b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE];
                        int aCharges = aPurchase > PlayerInfo.PURCHASE_COUNT_OFFSET
                                ? aPurchase - PlayerInfo.PURCHASE_COUNT_OFFSET
                                : 0;
                        if (aCharges == 0) {
                            g.setColorizeImages(true);
                            g.setColor(new Color(128, 128, 128));
                        }
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_CHOCOLATE), aButtonRect.mX + 6,
                                aButtonRect.mY + aOffsetY + 4);
                        g.setColorizeImages(false);

                        String aChargeString = "x" + aCharges;
                        PvzpCommon.pvzpDrawString(g, aChargeString, aButtonRect.mX + 64, aButtonRect.mY + aOffsetY + 65,
                                Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), new Color(255, 255, 255),
                                DrawStringJustification.DS_ALIGN_RIGHT);
                        break;
                    }
                    case GameObjectType.OBJECT_TYPE_GLOVE:
                        if (b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE
                                && b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW) {
                            g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_GARDENGLOVE), aButtonRect.mX - 6,
                                    aButtonRect.mY + aOffsetY - 4);
                        }
                        break;
                    case GameObjectType.OBJECT_TYPE_MONEY_SIGN:
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_ZEN_MONEYSIGN), aButtonRect.mX - 5,
                                aButtonRect.mY + aOffsetY - 4);
                        break;
                    case GameObjectType.OBJECT_TYPE_WHEELBARROW:
                        drawZenWheelBarrowButton(b, g, aOffsetY);
                        break;
                    case GameObjectType.OBJECT_TYPE_TREE_FOOD: {
                        int aPurchase = b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD];
                        int aCharges = aPurchase > PlayerInfo.PURCHASE_COUNT_OFFSET
                                ? aPurchase - PlayerInfo.PURCHASE_COUNT_OFFSET
                                : 0;
                        if (aCharges == 0) {
                            g.setColorizeImages(true);
                            g.setColor(new Color(128, 128, 128));
                        }
                        if (b.mChallenge.treeOfWisdomCanFeed() == 0) {
                            g.setColorizeImages(true);
                            g.setColor(new Color(128, 128, 128));
                        }
                        g.drawImage(Resources.getImageById(ImageID.IMAGE_TREEFOOD), aButtonRect.mX - 6,
                                aButtonRect.mY + aOffsetY - 7);
                        g.setColorizeImages(false);

                        String aChargeString = "x" + aCharges;
                        PvzpCommon.pvzpDrawString(g, aChargeString, aButtonRect.mX + 64, aButtonRect.mY + aOffsetY + 65,
                                Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), new Color(255, 255, 255),
                                DrawStringJustification.DS_ALIGN_RIGHT);
                        break;
                    }
                    default:
                        break;
                }
            }
        }
    }

    /** Ported from src/Lawn/Board.cpp:6855 (Board::DrawShovel). */
    static void drawShovel(Board b, Graphics g) {
        if (b.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && b.mApp.mGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM) {
            if (b.mShowShovel) {
                Rect aShovelRect = b.getShovelButtonRect();
                g.drawImage(Resources.getImageById(ImageID.IMAGE_SHOVELBANK), aShovelRect.mX, aShovelRect.mY);

                if (b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_SHOVEL) {
                    if (b.mChallenge.mChallengeState == 15) { // (ChallengeState)15
                        g.setColorizeImages(true);
                        g.setColor(PvzpCommon.getFlashingColor(b.mMainCounter, 75));
                    }
                    g.drawImage(Resources.getImageById(ImageID.IMAGE_SHOVEL), aShovelRect.mX - 7, aShovelRect.mY - 3);
                    g.setColorizeImages(false);
                }
            }
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            drawZenButtons(b, g);
        }
    }

    /** Ported from src/Lawn/Board.cpp:6883 (Board::DrawDebugText). */
    static void drawDebugText(Board b, Graphics g) {
        String aText = "";

        switch (b.mDebugTextMode) {
            case DebugTextMode.DEBUG_TEXT_NONE:
                break;

            case DebugTextMode.DEBUG_TEXT_ZOMBIE_SPAWN: {
                int aTime = b.mZombieCountDownStart - b.mZombieCountDown;
                float aCountDownFraction = (float) aTime / (float) b.mZombieCountDownStart;

                aText += String.format("ZOMBIE SPAWNING DEBUG\n");
                aText += String.format("CurrentWave: %d of %d\n", b.mCurrentWave, b.mNumWaves);
                aText += String.format("TimeSinseLastSpawn: %d %s\n", aTime, aTime > 400 ? "" : "(too soon)");
                aText += String.format("ZombieCountDown: %d/%d (%.0f%%)\n", b.mZombieCountDown, b.mZombieCountDownStart,
                        aCountDownFraction);

                if (b.mZombieHealthToNextWave != -1) {
                    int aTotalHealth = b.totalZombiesHealthInWave(b.mCurrentWave - 1);
                    int aHealthRange = Math.max(b.mZombieHealthWaveStart - b.mZombieHealthToNextWave, 1);
                    float aHealthFraction = (float) (b.mZombieHealthToNextWave - aTotalHealth + aHealthRange)
                            / (float) aHealthRange;
                    aText += String.format("ZombieHealth: CurZombieHealth %d trigger %d (%.0f%%)\n", aTotalHealth,
                            b.mZombieHealthToNextWave, aHealthFraction * 100);
                } else {
                    aText += String.format("ZombieHealth: before first wave\n");
                }

                if (b.mHugeWaveCountDown > 0) {
                    aText += String.format("HugeWaveCountDown: %d\n", b.mHugeWaveCountDown);
                }

                Zombie aBossZombie = b.getBossZombie();
                if (aBossZombie != null) {
                    aText += String.format("\nSpawn: %d\n", aBossZombie.mSummonCounter);
                    aText += String.format("Stomp: %d\n", aBossZombie.mBossStompCounter);
                    aText += String.format("Bungee: %d\n", aBossZombie.mBossBungeeCounter);
                    aText += String.format("Head: %d\n", aBossZombie.mBossHeadCounter);
                    aText += String.format("Health: %d of %d\n", aBossZombie.mBodyHealth, aBossZombie.mBodyMaxHealth);
                }
                break;
            }

            case DebugTextMode.DEBUG_TEXT_MUSIC: {
                aText += String.format("MUSIC DEBUG\n");
                aText += String.format("CurrentWave: %d of %d\n", b.mCurrentWave, b.mNumWaves);

                if (b.mApp.mMusic.mCurMusicFileMain == MusicFile.MUSIC_FILE_NONE) {
                    aText += String.format("No music");
                } else {
                    aText += String.format("Music Burst: ");

                    if (b.mApp.mMusic.mMusicBurstState == MusicBurstState.MUSIC_BURST_OFF) {
                        aText += String.format("Off");
                    } else if (b.mApp.mMusic.mMusicBurstState == MusicBurstState.MUSIC_BURST_STARTING) {
                        aText += String.format("Starting %d/%d", b.mApp.mMusic.mBurstStateCounter, 400);
                    } else if (b.mApp.mMusic.mMusicBurstState == MusicBurstState.MUSIC_BURST_ON) {
                        aText += String.format("On at least until %d/%d", b.mApp.mMusic.mBurstStateCounter, 800);
                    } else if (b.mApp.mMusic.mMusicBurstState == MusicBurstState.MUSIC_BURST_FINISHING) {
                        aText += String.format("Finishing %d/%d", b.mApp.mMusic.mBurstStateCounter, 400);
                    }

                    if (b.mApp.mMusic.mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_OFF) {
                        aText += String.format(", Drums off");
                    } else if (b.mApp.mMusic.mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_ON_QUEUED) {
                        aText += String.format(", Drums queued on");
                    } else if (b.mApp.mMusic.mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_ON) {
                        aText += String.format(", Drums on");
                    } else if (b.mApp.mMusic.mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_OFF_QUEUED) {
                        aText += String.format(", Drums queued off");
                    } else if (b.mApp.mMusic.mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_FADING) {
                        aText += String.format(", Drums fading off %d/%d", b.mApp.mMusic.mDrumsStateCounter, 50);
                    }
                    aText += String.format("\n");
                }
                break;
            }

            case DebugTextMode.DEBUG_TEXT_MEMORY:
                aText += String.format("MEMORY DEBUG\n");
                aText += String.format("attachments %d\n", b.mApp.mEffectSystem.mAttachmentHolder.mAttachments.mSize);
                aText += String.format("emitters %d\n", b.mApp.mEffectSystem.mParticleHolder.mEmitters.mSize);
                aText += String.format("particles %d\n", b.mApp.mEffectSystem.mParticleHolder.mParticles.mSize);
                aText += String.format("particle systems %d\n", b.mApp.mEffectSystem.mParticleHolder.mParticleSystems.mSize);
                aText += String.format("trails %d\n", b.mApp.mEffectSystem.mTrailHolder.mTrails.mSize);
                aText += String.format("reanimation %d\n", b.mApp.mEffectSystem.mReanimationHolder.mReanimations.mSize);
                aText += String.format("zombies %d\n", b.mZombies.mSize);
                aText += String.format("plants %d\n", b.mPlants.mSize);
                aText += String.format("projectiles %d\n", b.mProjectiles.mSize);
                aText += String.format("coins %d\n", b.mCoins.mSize);
                aText += String.format("lawn mowers %d\n", b.mLawnMowers.mSize);
                aText += String.format("grid items %d\n", b.mGridItems.mSize);
                break;

            case DebugTextMode.DEBUG_TEXT_COLLISION:
                aText += String.format("COLLISION DEBUG\n");
                break;

            default:
                break;
        }

        g.setFont(Resources.getFontById(Resources.FONT_PICO129_ID));
        if (!aText.isEmpty()) {
            g.setColor(new Color(0, 0, 0));
            g.drawStringWordWrapped(aText, 10, 89);
            g.drawStringWordWrapped(aText, 11, 91);
            g.drawStringWordWrapped(aText, 9, 90);
            g.drawStringWordWrapped(aText, 11, 90);
            g.setColor(new Color(255, 255, 255));
            g.drawStringWordWrapped(aText, 10, 90);
        }
    }

    /** Ported from src/Lawn/Board.cpp:7026 (Board::DrawDebugObjectRects). */
    static void drawDebugObjectRects(Board b, Graphics g) {
        if (b.mDebugTextMode != DebugTextMode.DEBUG_TEXT_COLLISION) {
            return;
        }

        for (Plant aPlant : b.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            Rect aRect = aPlant.getPlantRect();
            g.setColor(new Color(0, 255, 0));
            g.drawRect(aRect);

            Rect aAttackRect = aPlant.getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);
            if (aAttackRect.mWidth < GameConstants.BOARD_WIDTH) {
                g.setColor(new Color(255, 0, 0));
                g.drawRect(aAttackRect);
            }

            Rect aSecondaryRect = aPlant.getPlantAttackRect(PlantWeapon.WEAPON_SECONDARY);
            if (aSecondaryRect.mWidth < GameConstants.BOARD_WIDTH) {
                g.setColor(new Color(255, 0, 128));
                g.drawRect(aSecondaryRect);
            }
        }

        for (Zombie aZombie : b.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying()) {
                Rect aRect = aZombie.getZombieRect();
                g.setColor(new Color(0, 255, 0));
                g.drawRect(aRect);

                Rect aAttackRect = aZombie.getZombieAttackRect();
                g.setColor(new Color(255, 0, 0));
                g.drawRect(aAttackRect);
            }
        }

        for (LawnMower aLawnMower : b.mLawnMowers) {
            if (aLawnMower.mDead) {
                continue;
            }
            Rect aAttackRect = aLawnMower.getLawnMowerAttackRect();
            g.setColor(new Color(255, 0, 0));
            g.drawRect(aAttackRect);
        }

        for (Projectile aProjectile : b.mProjectiles) {
            if (aProjectile.mDead) {
                continue;
            }
            g.setColor(new Color(255, 0, 0));
            Rect aDamageRect = aProjectile.getProjectileRect();
            g.drawRect(aDamageRect);
        }
    }

    /** Ported from src/Lawn/Board.cpp:7094 (Board::DrawFadeOut). */
    static void drawFadeOut(Board b, Graphics g) {
        if (b.mBoardFadeOutCounter < 0 || b.isSurvivalStageWithRepick()) {
            return;
        }

        int anAlpha = PvzpCommon.pvzpAnimateCurve(200, 0, b.mBoardFadeOutCounter, 0, 255, PvzpCurves.CURVE_LINEAR);
        if (b.mLevel == 9 || b.mLevel == 19 || b.mLevel == 29 || b.mLevel == 39 || b.mLevel == 49) {
            g.setColor(new Color(0, 0, 0, anAlpha));
        } else {
            g.setColor(new Color(255, 255, 255, anAlpha));
        }
        g.fillRect(0, 0, b.mWidth, b.mHeight);
    }

    /** Ported from src/Lawn/Board.cpp:7111 (Board::DrawTopRightUI). */
    static void drawTopRightUI(Board b, Graphics g) {
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            if (b.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_ZEN_FADING) {
                b.mMenuButton.mY = PvzpCommon.pvzpAnimateCurve(50, 0, b.mChallenge.mChallengeStateCounter, -10, -50,
                        PvzpCurves.CURVE_EASE_IN_OUT);
                b.mStoreButton.mX = PvzpCommon.pvzpAnimateCurve(50, 0, b.mChallenge.mChallengeStateCounter, 678, 800,
                        PvzpCurves.CURVE_EASE_IN_OUT);
            } else {
                b.mMenuButton.mY = -10;
                b.mStoreButton.mX = 678;
            }
        }

        if (b.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_COMPLETED) {
            g.setColorizeImages(true);
            g.setColor(PvzpCommon.getFlashingColor(b.mMainCounter, 75));
        }
        b.mMenuButton.draw(g);
        g.setColorizeImages(false);

        if (b.mStoreButton != null && b.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            if (b.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_VISIT_STORE) {
                g.setColorizeImages(true);
                g.setColor(PvzpCommon.getFlashingColor(b.mMainCounter, 75));
            }
            b.mStoreButton.draw(g);
            g.setColorizeImages(false);
        }
    }

    /** Ported from src/Lawn/Board.cpp:7147 (Board::DrawUIBottom). */
    static void drawUIBottom(Board b, Graphics g) {
        if (b.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
            int aWaveTime = Math.abs((b.mMainCounter / 8) % 22 - 11);
            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.drawImageCel(Resources.getImageById(ImageID.IMAGE_WAVESIDE), 0, 40, aWaveTime);
            g.drawImageCel(Resources.getImageById(ImageID.IMAGE_WAVECENTER), 160, 40, aWaveTime);
            g.drawImageCel(Resources.getImageById(ImageID.IMAGE_WAVECENTER), 320, 40, aWaveTime);
            g.drawImageCel(Resources.getImageById(ImageID.IMAGE_WAVECENTER), 480, 40, aWaveTime);
            Image aWaveSide = Resources.getImageById(ImageID.IMAGE_WAVESIDE);
            PvzpCommon.pvzpDrawImageCelScaled(g, aWaveSide, 800, 40, aWaveTime % aWaveSide.mNumCols,
                    aWaveTime / aWaveSide.mNumCols, -1.0f, 1.0f);
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        }

        if (b.mBackground == BackgroundType.BACKGROUND_GREENHOUSE
                || b.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            Image aOverlay = Resources.getImageById(ImageID.IMAGE_BACKGROUND_GREENHOUSE_OVERLAY);
            g.drawImage(aOverlay, new Rect(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT),
                    new Rect(0, 0, aOverlay.mWidth, aOverlay.mHeight));
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        }

        if (b.mApp.mGameScene != GameScenes.SCENE_ZOMBIES_WON) {
            if (b.mSeedBank.beginDraw(g)) {
                b.mSeedBank.draw(g);
                b.mSeedBank.endDraw(g);
            }

            if (b.mAdvice.mMessageStyle == MessageStyle.MESSAGE_STYLE_SLOT_MACHINE) {
                b.mAdvice.draw(g);
            }
        }

        drawShovel(b, g);
        if (!b.stageHasFog()) {
            drawTopRightUI(b, g);
        }
    }

    /** Ported from src/Lawn/Board.cpp:7197 (Board::DrawUICoinBank). */
    static void drawUICoinBank(Board b, Graphics g) {
        if (b.mApp.mGameScene != GameScenes.SCENE_PLAYING
                && b.mApp.mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_OFF) {
            return;
        }

        if (b.mCoinBankFadeCount <= 0) {
            return;
        }

        Image aCoinBank = Resources.getImageById(ImageID.IMAGE_COINBANK);
        int aPosX = 57;
        int aPosY = 599 - aCoinBank.getHeight();
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || b.mApp.mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_OFF) {
            aPosX = 450 - b.mX;
        }

        g.setColorizeImages(true);
        int anAlpha = Math.min(Math.max(255 * b.mCoinBankFadeCount / 15, 0), 255);
        g.setColor(new Color(255, 255, 255, anAlpha));
        g.drawImage(aCoinBank, aPosX, aPosY);

        g.setColor(new Color(180, 255, 90, anAlpha));
        g.setFont(Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID));
        String aCoinLabel = b.mApp.getMoneyString(b.mApp.mPlayerInfo.mCoins);
        g.drawString(aCoinLabel, aPosX + 116 - Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID).stringWidth(aCoinLabel),
                aPosY + 24);
        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/Board.cpp:7320 (Board::DrawFog). */
    static void drawFog(Board b, Graphics g) {
        boolean aIs3DAccelerated = b.mApp.is3DAccelerated();
        Image aImageFog = aIs3DAccelerated
                ? Resources.getImageById(ImageID.IMAGE_FOG)
                : Resources.getImageById(ImageID.IMAGE_FOG_SOFTWARE);
        // fog animation uses 900- and 500-frame periods; mod by their lcm (4500) to avoid float precision loss
        float aTime = (float) (b.mMainCounter % 4500) * (float) Math.PI * 2.0f;
        g.setColorizeImages(true);
        for (int x = 0; x < Board.MAX_GRID_SIZE_X; x++) {
            for (int y = 0; y < Board.MAX_GRID_SIZE_Y + 1; y++) {
                int aFadeAmount = b.mGridCelFog[x][y];
                if (aFadeAmount == 0) {
                    continue;
                }

                int aCelLook = b.mGridCelLook[x][y % Board.MAX_GRID_SIZE_Y];
                int aCelCol = aCelLook % 8;
                float aPosX = x * 80 + b.mFogOffset - 15;
                float aPosY = y * 85 + 20;
                float aPhaseX = (float) (6.0 * Math.PI * x / Board.MAX_GRID_SIZE_X);
                float aPhaseY = (float) (6.0 * Math.PI * y / (Board.MAX_GRID_SIZE_Y + 1));
                float aMotion = (float) (13.0 + 4.0 * Math.sin(aTime / 900.0f + aPhaseY)
                        + 8.0 * Math.sin(aTime / 500.0f + aPhaseX));

                int aColorVariant = (int) (255 - aCelLook * 1.5 - aMotion * 1.5);
                int aLightnessVariant = (int) (255 - aCelLook - aMotion);
                if (!aIs3DAccelerated) {
                    aPosX += 10;
                    aPosY += 3;
                    aCelCol = aCelLook % Resources.getImageById(ImageID.IMAGE_FOG_SOFTWARE).mNumCols;
                    aColorVariant = 255;
                    aLightnessVariant = 255;
                }

                g.setColor(new Color(aColorVariant, aColorVariant, aLightnessVariant, aFadeAmount));
                g.drawImageCel(aImageFog, (int) aPosX, (int) aPosY, aCelCol, 0);

                if (x == Board.MAX_GRID_SIZE_X - 1) {
                    g.drawImageCel(aImageFog, (int) aPosX + 80, (int) aPosY, aCelCol, 0);
                }
            }
        }
        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/Board.cpp:7373 (Board::DrawUITop). */
    static void drawUITop(Board b, Graphics g) {
        if (b.stageHasFog()) {
            drawTopRightUI(b, g);
        }

        if (b.mTimeStopCounter > 0) {
            g.setColor(new Color(200, 200, 200, 210));
            g.fillRect(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        }

        if (b.mApp.mGameScene == GameScenes.SCENE_PLAYING
                || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            drawProgressMeter(b, g);
            drawLevel(b, g);
        }
        if (b.mStoreButton != null && b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            b.mStoreButton.draw(g);
        }

        if ((b.mApp.mGameMode == GameMode.GAMEMODE_UPSELL || b.mApp.mGameMode == GameMode.GAMEMODE_INTRO)
                && b.mCutScene.mUpsellHideBoard) {
            g.setColor(new Color(0, 0, 0));
            g.fillRect(0, 0, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
            b.mCutScene.drawUpsell(g);
        }
        if (b.mApp.mGameMode == GameMode.GAMEMODE_INTRO) {
            b.mCutScene.drawIntro(g);
        }

        if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || b.isScaryPotterDaveTalking()) {
            Graphics aScreenSpace = new Graphics(g);
            aScreenSpace.mTransX -= b.mX;
            aScreenSpace.mTransY -= b.mY;
            b.mApp.drawCrazyDave(aScreenSpace);
        }

        if (b.mAdvice.mMessageStyle != MessageStyle.MESSAGE_STYLE_SLOT_MACHINE) {
            b.mAdvice.draw(g);
        }

        if (b.mTimeStopCounter == 0 && b.mCursorObject.beginDraw(g)) {
            b.mCursorObject.draw(g);
            b.mCursorObject.endDraw(g);
        }

        b.mToolTip.draw(g);
        drawDebugText(b, g);
        drawDebugObjectRects(b, g);
    }

    /** Ported from src/Lawn/Board.cpp:7438 (Board::Draw). */
    static void draw(Board b, Graphics g) {
        if (b.mApp.getDialog(Dialogs.DIALOG_STORE) != null || b.mApp.getDialog(Dialogs.DIALOG_ALMANAC) != null) {
            return;
        }

        g.setLinearBlend(true);

        if (b.mDrawCount != 0 && b.mCutScene.mPreloaded) {
            long aTickCount = getTicks();
            long aIntervalDraws = (long) b.mDrawCount - b.mIntervalDrawCountStart;
            long aInterval = aTickCount - b.mIntervalDrawTime;
            if (aInterval > 10000) {
                float aIntervalFPS = (float) ((aIntervalDraws * 1000 + 500) / aInterval);
                if (b.mMinFPS > aIntervalFPS) {
                    b.mMinFPS = aIntervalFPS;
                }
                b.mIntervalDrawCountStart = b.mDrawCount;
                b.mIntervalDrawTime = aTickCount;
            }
        } else {
            long aTickCount = getTicks();
            b.mStartDrawTime = aTickCount;
            b.mIntervalDrawTime = aTickCount;
            b.mDrawCount = 1;
            b.mIntervalDrawCountStart = 1;
        }

        b.mDrawCount++;
        LawnHooksRegistry.dispatcher().onPreDraw(b, g);
        drawGameObjects(b, g);
        LawnHooksRegistry.dispatcher().onPostDraw(b, g);
    }
}
