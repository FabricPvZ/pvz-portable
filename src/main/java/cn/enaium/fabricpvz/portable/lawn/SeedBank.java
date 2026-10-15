/*
 * Ported from src/Lawn/SeedPacket.h:69-92 and src/Lawn/SeedPacket.cpp:919-1218.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/SeedPacket.h:69-90 and src/Lawn/SeedPacket.cpp:892-1194.
 */
public class SeedBank extends GameObject {

    public int mNumPackets;
    public SeedPacket[] mSeedPackets = new SeedPacket[GameConstants.SEEDBANK_MAX];
    public int mCutSceneDarken;
    public int mConveyorBeltCounter;

    /** Ported from src/Lawn/SeedPacket.cpp:919-928 (SeedBank::SeedBank). */
    public SeedBank() {
        mWidth = Resources.getImageById(ImageID.IMAGE_SEEDBANK).getWidth();
        mHeight = Resources.getImageById(ImageID.IMAGE_SEEDBANK).getHeight();

        for (int i = 0; i < GameConstants.SEEDBANK_MAX; i++) {
            mSeedPackets[i] = new SeedPacket();
        }

        mNumPackets = 0;
        mConveyorBeltCounter = 0;
        mCutSceneDarken = 255;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:929-993 (SeedBank::Draw). */
    public void draw(Graphics g) {
        if (mBoard.mCutScene != null && mBoard.mCutScene.isBeforePreloading()) {
            return;
        }

        if (mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            g.mTransX -= mBoard.mX;
            g.mTransY -= mBoard.mY;
        }

        Image seedBank = Resources.getImageById(ImageID.IMAGE_SEEDBANK);
        if (mApp.isSlotMachineLevel()) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_SUNBANK), 0, 0);
        } else if (mBoard.hasConveyorBeltSeedBank()) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_CONVEYORBELT_BACKDROP), 83, 0);
            g.drawImageCel(Resources.getImageById(ImageID.IMAGE_CONVEYORBELT), 90, 63, mConveyorBeltCounter / 4 % 6);
            g.setClipRect(90, 0, 501, GameConstants.BOARD_HEIGHT);
        } else {
            int aExtraWidth = mBoard.getSeedBankExtraWidth();
            Rect theSrcRect = new Rect(seedBank.mWidth - aExtraWidth - 12, 0, aExtraWidth + 12, seedBank.mHeight);
            g.drawImage(seedBank, 0, 0);
            g.drawImage(seedBank, seedBank.mWidth - 12, 0, theSrcRect);
        }

        for (int i = 0; i < mNumPackets; i++) {
            SeedPacket aSeedPacket = mSeedPackets[i];
            if (aSeedPacket.mPacketType != SeedType.SEED_NONE && aSeedPacket.beginDraw(g)) {
                aSeedPacket.draw(g);
                aSeedPacket.endDraw(g);
            }
        }

        g.clearClipRect();
        if (mApp.isSlotMachineLevel() && mY > -Resources.getImageById(ImageID.IMAGE_SEEDBANK).getHeight()) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_SLOTMACHINE_OVERLAY), 189, -2);
        }

        if (!mBoard.hasConveyorBeltSeedBank()) {
            String aMoneyLabel = Integer.toString(Math.max(mBoard.mSunMoney, 0));
            Color aMoneyColor = new Color(0, 0, 0);
            if (mBoard.mOutOfMoneyCounter > 0 && mBoard.mOutOfMoneyCounter % 20 < 10) {
                aMoneyColor = new Color(255, 0, 0);
            }

            PvzpCommon.pvzpDrawString(g, aMoneyLabel, 34, 78, Resources.getFontById(Resources.FONT_CONTINUUMBOLD14_ID),
                    aMoneyColor, DrawStringJustification.DS_ALIGN_CENTER);
        }

        if (mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            g.mTransX += mBoard.mX;
            g.mTransY += mBoard.mY;
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:995-1011 (SeedBank::MouseHitTest). */
    public boolean mouseHitTest(int x, int y, HitResult theHitResult) {
        if (x - mX <= mWidth - 5 && mNumPackets > 0) {
            for (int i = 0; i < GameConstants.SEEDBANK_MAX; i++) {
                SeedPacket aSeedPacket = mSeedPackets[i];
                if (aSeedPacket.mouseHitTest(x - mX, y - mY, theHitResult)) {
                    return true;
                }
            }
        }

        theHitResult.mObject = null;
        theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
        return false;
    }

    /** Ported from src/Lawn/SeedPacket.cpp inline (SeedBank::ContainsPoint). */
    public boolean containsPoint(int theX, int theY) {
        return theX >= mX && theX < mX + mWidth && theY >= mY && theY < mY + mHeight;
    }

    /** Ported from src/Lawn/SeedPacket.h:83 inline (SeedBank::Move). */
    public void move(int x, int y) {
        mX = x;
        mY = y;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1018-1048 (SeedBank::AddSeed). */
    public void addSeed(int theSeedType, boolean thePlaceOnLeft) {
        assert mBoard.hasConveyorBeltSeedBank();
        assert theSeedType != SeedType.SEED_NONE;

        int aNumSeeds = getNumSeedsOnConveyorBelt();
        if (aNumSeeds == mNumPackets) {
            return;
        }

        SeedPacket aSeedPacket = mSeedPackets[aNumSeeds];
        aSeedPacket.mPacketType = theSeedType;
        aSeedPacket.mRefreshCounter = 0;
        aSeedPacket.mRefreshTime = 0;
        aSeedPacket.mRefreshing = false;
        aSeedPacket.mActive = true;
        aSeedPacket.mOffsetX = 515 - (GameConstants.SEED_PACKET_WIDTH + 1) * aNumSeeds;
        if (thePlaceOnLeft) {
            aSeedPacket.mOffsetX = 0;
        }
        if (aNumSeeds > 0) {
            SeedPacket aPrevPacket = mSeedPackets[aNumSeeds - 1];
            if (aSeedPacket.mOffsetX < aPrevPacket.mOffsetX) {
                aSeedPacket.mOffsetX = aPrevPacket.mOffsetX + 40;
            }
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1050-1080 (SeedBank::RemoveSeed). */
    public void removeSeed(int theIndex) {
        assert mBoard.hasConveyorBeltSeedBank();
        assert theIndex >= 0 && theIndex < getNumSeedsOnConveyorBelt();

        for (int i = theIndex; i < mNumPackets; i++) {
            SeedPacket aSeedPacket = mSeedPackets[i];
            if (aSeedPacket.mPacketType == SeedType.SEED_NONE) {
                break;
            }

            if (i == mNumPackets - 1) {
                aSeedPacket.mPacketType = SeedType.SEED_NONE;
                aSeedPacket.mOffsetX = 0;
            } else {
                SeedPacket aNextPacket = mSeedPackets[i + 1];
                aSeedPacket.mPacketType = aNextPacket.mPacketType;
                aSeedPacket.mOffsetX = aNextPacket.mOffsetX + GameConstants.SEED_PACKET_WIDTH + 1;
            }

            aSeedPacket.mRefreshCounter = 0;
            aSeedPacket.mRefreshTime = 0;
            aSeedPacket.mRefreshing = false;
            aSeedPacket.mActive = true;
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1082-1092 (SeedBank::GetNumSeedsOnConveyorBelt). */
    public int getNumSeedsOnConveyorBelt() {
        for (int i = 0; i < mNumPackets; i++) {
            if (mSeedPackets[i].mPacketType == SeedType.SEED_NONE) {
                return i;
            }
        }
        return mNumPackets;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1094-1105 (SeedBank::CountOfTypeOnConveyorBelt). */
    public int countOfTypeOnConveyorBelt(int theSeedType) {
        int aCount = 0;
        for (int i = 0; i < mNumPackets; i++) {
            if (mSeedPackets[i].mPacketType == theSeedType) {
                aCount++;
            }
        }
        return aCount;
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1147-1163 (SeedBank::UpdateConveyorBelt). */
    public void updateConveyorBelt() {
        mConveyorBeltCounter++;
        if (mConveyorBeltCounter % SeedPacket.CONVEYOR_SPEED == 0) {
            for (int i = 0; i < mNumPackets; i++) {
                SeedPacket aSeedPacket = mSeedPackets[i];
                if (aSeedPacket.mOffsetX > 0) {
                    aSeedPacket.mOffsetX = Math.max(aSeedPacket.mOffsetX - 1, 0);
                }
            }

            mBoard.updateToolTip();
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1165-1174 (SeedBank::UpdateWidth). */
    public void updateWidth() {
        mNumPackets = mBoard.getNumSeedsInBank();
        mWidth = Resources.getImageById(ImageID.IMAGE_SEEDBANK).getWidth() + mBoard.getSeedBankExtraWidth();
        for (int i = 0; i < mNumPackets; i++) {
            mSeedPackets[i].mX = mBoard.getSeedPacketPositionX(i);
        }
    }

    /** Ported from src/Lawn/SeedPacket.cpp:1176-1193 (SeedBank::RefreshAllPackets). */
    public void refreshAllPackets() {
        for (int i = 0; i < mNumPackets; i++) {
            SeedPacket aSeedPacket = mSeedPackets[i];
            if (aSeedPacket.mPacketType == SeedType.SEED_NONE) {
                break;
            }

            if (aSeedPacket.mRefreshing) {
                aSeedPacket.mRefreshCounter = 0;
                aSeedPacket.mRefreshing = false;
                aSeedPacket.mActive = true;
                aSeedPacket.flashIfReady();
            }
        }
    }
}
