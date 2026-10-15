package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/** Ported from src/Lawn/Challenge.cpp:4923-5335 (Challenge::TreeOfWisdom*). */
public class ChallengeTreeOfWisdom {
    public final Challenge c;

    public ChallengeTreeOfWisdom(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:4923-4928 ({@code Challenge::TreeOfWisdomMouseOn}). */
    public int treeOfWisdomMouseOn(int theX, int theY) {
        HitResult aHitResult = new HitResult();
        c.mBoard.mouseHitTest(theX, theY, aHitResult);
        return (aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_TREE_OF_WISDOM
                && c.mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_TREE_FOOD) ? 1 : 0;
    }

    /** Ported from src/Lawn/Challenge.cpp:4930-4933 ({@code Challenge::TreeOfWisdomGetSize}). */
    public int treeOfWisdomGetSize() {
        return c.mApp.mPlayerInfo.mChallengeRecords[c.mApp.getCurrentChallengeIndex()];
    }

    /** Ported from src/Lawn/Challenge.cpp:4935-5018 ({@code Challenge::TreeOfWisdomDraw}). */
    public void treeOfWisdomDraw(Graphics g) {
        int aMouseOn = treeOfWisdomMouseOn(c.mApp.mWidgetManager.mLastMouseX - c.mBoard.mX,
                c.mApp.mWidgetManager.mLastMouseY - c.mBoard.mY);

        Reanimation aReanimTree = c.mApp.reanimationGet(c.mReanimChallenge);
        aReanimTree.mEnableExtraOverlayDraw = false;
        aReanimTree.drawRenderGroup(g, 0); // draw the background
        for (int i = 0; i < 6; i++) {
            c.mApp.reanimationGet(c.mReanimClouds[i]).drawRenderGroup(g, 0);
        }

        int aHeight = treeOfWisdomGetSize();
        if (aMouseOn != 0) {
            aReanimTree.mExtraOverlayColor = new Color(255, 255, 255, aHeight < 18 ? 128 : 48);
            aReanimTree.mEnableExtraOverlayDraw = true;
        } else {
            aReanimTree.mEnableExtraOverlayDraw = false;
        }
        aReanimTree.drawRenderGroup(g, 2); // draw the trunk

        aReanimTree.mEnableExtraOverlayDraw = false;
        aReanimTree.drawRenderGroup(g, 3); // draw the ground

        if (aMouseOn != 0) {
            aReanimTree.mExtraOverlayColor = new Color(255, 255, 255, 32);
            aReanimTree.mEnableExtraOverlayDraw = true;
        } else {
            aReanimTree.mEnableExtraOverlayDraw = false;
        }
        aReanimTree.drawRenderGroup(g, 4); // draw the roots

        if (c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_GIVE_WISDOM
                || c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_BABBLING) {
            int aPosX;
            int aPosY;
            if (aHeight < 7) {
                aPosX = 400;
                aPosY = 152;
            } else if (aHeight < 12) {
                aPosX = 395;
                aPosY = 60;
            } else {
                aPosX = 390;
                aPosY = 52;
            }

            g.drawImage(Resources.getImageById(Resources.IMAGE_STORE_SPEECHBUBBLE2_ID), aPosX, aPosY);
            String aText = "[TREE_OF_WISDOM_" + c.mTreeOfWisdomTalkIndex + "]";
            int aWrapEnum = c.mApp.getInteger("TREE_OF_WISDOM_TEXT_WRAP_ENUM",
                    DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
            PvzpStringFile.pvzpDrawStringWrapped(g, aText, new Rect(aPosX + 25, aPosY + 6, 233, 144),
                    Resources.getFontById(Resources.FONT_BRIANNETOD16_ID), Color.Black, aWrapEnum);
        }

        int aCurSize = aHeight;
        float aScale = 1.0f;
        if (c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_JUST_GREW) {
            if (c.mChallengeStateCounter > 30) {
                aCurSize--;
            }
            aScale = PvzpCommon.pvzpAnimateCurveFloat(55, 20, c.mChallengeStateCounter, 1.0f, 1.2f,
                    PvzpCurves.CURVE_BOUNCE);
        }
        if (aCurSize >= 50) {
            String aSizeStr = PvzpCommon.pvzpReplaceNumberString("[TREE_OF_WISDOM_HIEGHT]", "{HEIGHT}", aCurSize);
            Font aFont = Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID);
            float aStrWidth = aFont.stringWidth(aSizeStr) * aScale;
            float aStrHeight = aFont.mAscent * aScale;

            SexyTransform2D aMatrix = new SexyTransform2D();
            PvzpCommon.pvzpScaleTransformMatrix(aMatrix, 400.0f - aStrWidth * 0.5f, 20.0f + aStrHeight * 0.5f,
                    aScale, aScale);
            PvzpCommon.pvzpDrawStringMatrix(g, aFont, aMatrix, aSizeStr, new Color(255, 255, 255));
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5020-5068 ({@code Challenge::TreeOfWisdomInit}). */
    public void treeOfWisdomInit() {
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_TREEOFWISDOM, true);
        Reanimation aReanimTree = c.mApp.addReanimation(0.5f, 0.5f, 0, ReanimationType.REANIM_TREEOFWISDOM);
        aReanimTree.mIsAttachment = true;
        aReanimTree.assignRenderGroupToPrefix("bg", 0);
        aReanimTree.assignRenderGroupToPrefix("tree", 2);
        aReanimTree.assignRenderGroupToPrefix("grass", 3);
        aReanimTree.assignRenderGroupToPrefix("overlay", 4);
        aReanimTree.assignRenderGroupToPrefix("leaf", 4);
        aReanimTree.assignRenderGroupToPrefix("bunch", 4);
        aReanimTree.setTruncateDisappearingFrames(null, false);
        c.mReanimChallenge = c.mApp.reanimationGetID(aReanimTree);

        int aTreeSize = Math.max(1, Math.min(50, treeOfWisdomGetSize()));
        aReanimTree.playReanim("anim_grow" + aTreeSize, ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 18.0f);
        if (aTreeSize == 0 && c.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD]
                < PlayerInfo.PURCHASE_COUNT_OFFSET) {
            aReanimTree.mFrameCount += aReanimTree.mFrameStart;
            aReanimTree.mFrameStart = 0;
        } else {
            aReanimTree.mAnimTime = 0.99f;
        }

        for (int i = 0; i < 6; i++) {
            Reanimation aReanimCloud = c.mApp.addReanimation(0, 0, 0, ReanimationType.REANIM_TREEOFWISDOM_CLOUDS);
            aReanimCloud.playReanim("Cloud" + (i + 1), ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 0);
            c.mReanimClouds[i] = c.mApp.reanimationGetID(aReanimCloud);

            int aCloudCounter = PvzpCommon.randRangeInt(-6000, 2000);
            c.mCloudsCounter[i] = aCloudCounter;
            if (aCloudCounter < 0) {
                aReanimCloud.mAnimTime = -aCloudCounter / 6000.0f;
                aReanimCloud.mAnimRate = 0.2f;
                c.mCloudsCounter[i] = 0;
            } else {
                aReanimCloud.mAnimRate = 0;
            }
        }

        c.mChallengeState = ChallengeState.STATECHALLENGE_TREE_WAITING_TO_BABBLE;
        c.mChallengeStateCounter = PvzpCommon.randRangeInt(700, 1500);
    }

    /** Ported from src/Lawn/Challenge.cpp:5070-5089 ({@code Challenge::TreeOfWisdomGrow}). */
    public void treeOfWisdomGrow() {
        c.mApp.mPlayerInfo.mChallengeRecords[c.mApp.getCurrentChallengeIndex()]++;
        int aTreeSize = treeOfWisdomGetSize();
        c.mApp.reanimationGet(c.mReanimChallenge).playReanim("anim_grow" + Math.max(1, Math.min(51, aTreeSize)),
                ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 8.0f);
        c.mApp.playFoley(FoleyType.FOLEY_PLANTGROW);

        if (aTreeSize > 1) {
            c.mChallengeState = ChallengeState.STATECHALLENGE_TREE_JUST_GREW;
            c.mChallengeStateCounter = 120;
        } else {
            c.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        }

        if (aTreeSize == 100) {
            ReportAchievement.giveAchievement(c.mApp, AchievementId.ToweringWisdom, true);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5091-5109 ({@code Challenge::TreeOfWisdomFertilize}). */
    public void treeOfWisdomFertilize() {
        GridItem aTreeFood = c.mBoard.mGridItems.dataArrayAlloc();
        aTreeFood.mPosX = 340.0f;
        aTreeFood.mPosY = 300.0f;
        aTreeFood.mGridItemType = GridItemType.GRIDITEM_ZEN_TOOL;
        aTreeFood.mGridX = 0;
        aTreeFood.mGridY = 0;
        aTreeFood.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        Reanimation aReanim = c.mApp.addReanimation(340.0f, 300.0f, 0, ReanimationType.REANIM_TREEOFWISDOM_TREEFOOD);
        aReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        aTreeFood.mGridItemReanimID = c.mApp.reanimationGetID(aReanim);
        aTreeFood.mGridItemState = GridItemState.GRIDITEM_STATE_ZEN_TOOL_FERTILIZER;

        c.mApp.playFoley(FoleyType.FOLEY_FERTILIZER);
        c.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD]--;
        c.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        c.mBoard.clearCursor();
    }

    /** Ported from src/Lawn/Challenge.cpp:5111-5134 ({@code Challenge::TreeOfWisdomBabble}). */
    public void treeOfWisdomBabble() {
        c.mChallengeState = ChallengeState.STATECHALLENGE_TREE_BABBLING;
        c.mChallengeStateCounter = 400;

        int aTreeSize = treeOfWisdomGetSize();
        int aBabbleHit = PvzpCommon.randRangeInt(0, 2);
        if (aTreeSize <= 1) {
            c.mTreeOfWisdomTalkIndex = 600;
        } else if (aBabbleHit == 0 && aTreeSize >= 5) {
            c.mTreeOfWisdomTalkIndex = 500;
        } else if (aBabbleHit == 1) {
            c.mTreeOfWisdomTalkIndex = PvzpCommon.randRangeInt(101, 110);
        } else {
            c.mTreeOfWisdomTalkIndex = Common.rand(4) + (aTreeSize < 12 ? 201 : aTreeSize < 50 ? 301 : 401);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5136-5162 ({@code Challenge::TreeOfWisdomGiveWisdom}). */
    public void treeOfWisdomGiveWisdom() {
        c.mChallengeState = ChallengeState.STATECHALLENGE_TREE_GIVE_WISDOM;
        c.mChallengeStateCounter = 1000;

        int aTreeSize = treeOfWisdomGetSize();
        if (aTreeSize == 100) {
            c.mTreeOfWisdomTalkIndex = 800;
        } else if (aTreeSize == 500) {
            c.mTreeOfWisdomTalkIndex = 900;
        } else if (aTreeSize == 1000) {
            c.mTreeOfWisdomTalkIndex = 1000;
        } else if (aTreeSize > 1000) {
            c.mTreeOfWisdomTalkIndex = 1100;
        } else {
            c.mTreeOfWisdomTalkIndex = Math.max(1, Math.min(49, aTreeSize - 1));
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5164-5185 ({@code Challenge::TreeOfWisdomSayRepeat}). */
    public void treeOfWisdomSayRepeat() {
        int aTreeSize = treeOfWisdomGetSize();
        if (aTreeSize >= 100 && Common.rand(47) == 0) {
            c.mTreeOfWisdomTalkIndex = 800;
        } else if (aTreeSize >= 500 && Common.rand(47) == 0) {
            c.mTreeOfWisdomTalkIndex = 900;
        } else if (aTreeSize >= 1000 && Common.rand(47) == 0) {
            c.mTreeOfWisdomTalkIndex = 1000;
        } else {
            c.mTreeOfWisdomTalkIndex = PvzpCommon.randRangeInt(2, Math.max(3, Math.min(49, aTreeSize)));
        }

        c.mChallengeStateCounter = 600;
    }

    /** Ported from src/Lawn/Challenge.cpp:5187-5195 ({@code Challenge::TreeOfWisdomToolUpdate}). */
    public void treeOfWisdomToolUpdate(GridItem theZenTool) {
        Reanimation aReanim = c.mApp.reanimationTryToGet(theZenTool.mGridItemReanimID);
        if (aReanim != null && aReanim.mLoopCount > 0) {
            treeOfWisdomGrow();
            theZenTool.gridItemDie();
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5197-5254 ({@code Challenge::TreeOfWisdomUpdate}). */
    public void treeOfWisdomUpdate() {
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            treeOfWisdomToolUpdate(aGridItem);
        }

        if (c.mApp.mGameScene == GameScenes.SCENE_PLAYING && c.mChallengeStateCounter > 0) {
            c.mChallengeStateCounter--;
        }
        if (c.mChallengeStateCounter == 0) {
            if (c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_JUST_GREW) {
                treeOfWisdomGiveWisdom();
            } else if (c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_WAITING_TO_BABBLE) {
                treeOfWisdomBabble();
            } else if (c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_BABBLING
                    || c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_GIVE_WISDOM) {
                if (c.mTreeOfWisdomTalkIndex == 500) {
                    treeOfWisdomSayRepeat();
                } else {
                    c.mChallengeState = ChallengeState.STATECHALLENGE_TREE_WAITING_TO_BABBLE;
                    c.mChallengeStateCounter = PvzpCommon.randRangeInt(700, 1000);
                }
            }
        }

        for (int i = 5; i >= 0; i--) { // Off by one error!
            Reanimation aReanimCloud = c.mApp.reanimationGet(c.mReanimClouds[i]);
            if (c.mCloudsCounter[i] > 0) {
                c.mCloudsCounter[i]--;
                if (c.mCloudsCounter[i] == 0) {
                    aReanimCloud.mLoopCount = 0;
                    aReanimCloud.mAnimTime = 0.0f;
                    aReanimCloud.mAnimRate = 0.2f;
                }
            } else if (aReanimCloud.mLoopCount > 0) {
                c.mCloudsCounter[i] = PvzpCommon.randRangeInt(2000, 4000);
            }

            aReanimCloud.update();
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5256-5268 ({@code Challenge::TreeOfWisdomLeave}). */
    public void treeOfWisdomLeave() {
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                treeOfWisdomGrow();
                aGridItem.gridItemDie();
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5270-5275 ({@code Challenge::TreeOfWisdomNextGarden}). */
    public void treeOfWisdomNextGarden() {
        treeOfWisdomLeave();
        c.mApp.killBoard();
        c.mApp.preNewGame(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, false);
    }

    /** Ported from src/Lawn/Challenge.cpp:5277-5285 ({@code Challenge::TreeOfWisdomOpenStore}). */
    public void treeOfWisdomOpenStore() {
        treeOfWisdomLeave();
        StoreScreen aStore = c.mApp.showStoreScreen();
        aStore.mBackButton.setLabel("[STORE_BACK_TO_GAME]");
        aStore.mPage = StorePages.STORE_PAGE_ZEN2;
        // PORT-NOTE: Challenge.cpp:5280-5284 — upstream restores the garden music after
        // WaitForResult(true) returned, i.e. after autoKill removed the store (Dialog.cpp:410-441);
        // this port's wait does not pump (sexy/Dialog.java:506), so the restore runs from the
        // screen's close path instead.
        aStore.onStoreClosed(store -> c.mApp.mMusic.makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_ZEN_GARDEN));
    }

    /** Ported from src/Lawn/Challenge.cpp:5287-5295 ({@code Challenge::TreeOfWisdomTool}). */
    public void treeOfWisdomTool(int theMouseX, int theMouseY) {
        if (treeOfWisdomMouseOn(theMouseX, theMouseY) != 0) {
            treeOfWisdomFertilize();
        }

        c.mBoard.clearCursor();
    }

    /** Ported from src/Lawn/Challenge.cpp:5297-5318 ({@code Challenge::TreeOfWisdomHitTest}). */
    public int treeOfWisdomHitTest(int theX, int theY, HitResult theHitResult) {
        Rect aTreeRect;
        int aTreeSize = treeOfWisdomGetSize();
        if (aTreeSize <= 1) {
            aTreeRect = new Rect(310, 275, 175, 175);
        } else if (aTreeSize < 7) {
            aTreeRect = new Rect(290, 255, 205, 195);
        } else if (aTreeSize < 12) {
            aTreeRect = new Rect(290, 215, 205, 225);
        } else {
            aTreeRect = new Rect(280, 155, 225, 305);
        }

        if (aTreeRect.contains(theX, theY)) {
            theHitResult.mObject = null;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_TREE_OF_WISDOM;
            return 1;
        } else {
            theHitResult.mObject = null;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
            return 0;
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:5320-5335 ({@code Challenge::TreeOfWisdomCanFeed}). */
    public int treeOfWisdomCanFeed() {
        if (c.mChallengeState == ChallengeState.STATECHALLENGE_TREE_JUST_GREW) {
            return 0;
        }

        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_ZEN_TOOL) {
                return 0;
            }
        }

        return 1;
    }
}
