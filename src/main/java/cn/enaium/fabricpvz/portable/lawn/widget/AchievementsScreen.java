/*
 * Ported from src/Lawn/Widget/AchievementsScreen.h:57-81 and AchievementsScreen.cpp:41-220.
 *
 * The upstream translation unit also declares the static reporting helper class ReportAchievement
 * (AchievementsScreen.h:86); that lives in {@link ReportAchievement}.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.AchievementId;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/AchievementsScreen.h:57-81 and AchievementsScreen.cpp:41-220.
 *
 * <p>The tall scrolling achievements poster shown when the player opens the Achievements screen from
 * the main menu. Constructed by {@link GameSelector} and slid into view.
 */
public class AchievementsScreen extends Widget {

    /**
     * Ported from src/Lawn/Widget/AchievementsScreen.h:57-61.
     *
     * <p>Achievement name/description pair; the English strings double as the localization keys.
     */
    public static class AchievementItem {
        public String name;
        public String description;

        /** Ported from the aggregate initialiser of gAchievementList (AchievementsScreen.cpp:43-63). */
        public AchievementItem(String theName, String theDescription) {
            name = theName;
            description = theDescription;
        }
    }

    /** Ported from src/Lawn/Widget/AchievementsScreen.cpp:42-63 (gAchievementList[MAX_ACHIEVEMENTS]). */
    public static final AchievementItem[] gAchievementList = {
        new AchievementItem("Home Lawn Security", "Complete Adventure Mode."),
        new AchievementItem("Nobel Peas Prize", "Get the golden sunflower trophy."),
        new AchievementItem("Better Off Dead", "Get to a streak of 10 in I, Zombie Endless"),
        new AchievementItem("China Shop", "Get to a streak of 15 in Vasebreaker Endless"),
        new AchievementItem("SPUDOW!", "Blow up a zombie using a Potato Mine."),
        new AchievementItem("Explodonator", "Take out 10 full-sized zombies with a single Cherry Bomb."),
        new AchievementItem("Morticulturalist", "Collect all 49 plants (including plants from Crazy Dave's shop)."),
        new AchievementItem("Don't Pea in the Pool", "Complete a daytime pool level without using pea shooters of any kind."),
        new AchievementItem("Roll Some Heads", "Bowl over 5 zombies with a single Wall-Nut."),
        new AchievementItem("Grounded", "Defeat a normal roof level without using any catapult plants."),
        new AchievementItem("Zombologist", "Discover the Yeti zombie."),
        new AchievementItem("Penny Pincher", "Pick up 30 coins in a row on a single level without letting any disappear."),
        new AchievementItem("Sunny Days", "Get 8000 sun during a single level."),
        new AchievementItem("Popcorn Party", "Defeat 2 Gargantuars with Corn Cob missiles in a single level."),
        new AchievementItem("Good Morning", "Complete a daytime level by planting only Mushrooms and Coffee Beans."),
        new AchievementItem("No Fungus Among Us", "Complete a nighttime level without planting any Mushrooms."),
        new AchievementItem("Beyond the Grave", "Beat all 20 mini games."),
        new AchievementItem("Immortal", "Survive 20 waves of pure zombie ferocity."),
        new AchievementItem("Towering Wisdom", "Grow the Tree of Wisdom to 100 feet."),
        new AchievementItem("Mustache Mode", "Enable Mustache Mode")
    };

    /** Ported from src/Lawn/Widget/AchievementsScreen.cpp:41 (aBackButtonRect). */
    public Rect aBackButtonRect = new Rect(120, 35, 130, 80);

    public LawnApp mApp;
    public int mScrollDirection;
    public Rect mMoreRockRect;
    public int mScrollValue;
    public int mScrollDecay;
    public int mDefaultScrollValue;
    public boolean mDidPressMoreButton;

    public AchievementsScreen(LawnApp theApp) {
        mApp = theApp;
        mWidth = 800;
        mHeight = imageHeight(Resources.IMAGE_ACHEESEMENTS_CHINA_ID)
                + imageHeight(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_BG_ID) + 15700;
        mScrollDirection = -1;
        mScrollValue = 0;
        mDefaultScrollValue = 30;
        mScrollDecay = 1;
        mDidPressMoreButton = false;
        Image aMoreRock = theApp.getImage(Resources.IMAGE_ACHEESEMENTS_MORE_ROCK_ID);
        mMoreRockRect = new Rect(710, 470,
                (aMoreRock == null ? 0 : aMoreRock.mWidth) - 25,
                (aMoreRock == null ? 0 : aMoreRock.mHeight) - 50);
    }

    private int imageHeight(int theId) {
        Image anImage = mApp.getImage(theId);
        return anImage == null ? 0 : anImage.mHeight;
    }

    // AchievementsWidget::Update (AchievementsScreen.cpp:79-106)
    @Override
    public void update() {
        markDirty();
        if (mScrollValue <= 0) {
            return;
        }

        mScrollValue = Math.min(mScrollValue, mDefaultScrollValue);

        mScrollValue -= mScrollDecay;

        int aNewY = mY + mScrollValue * mScrollDirection;
        aNewY = Math.min(aNewY, -1);

        int aMaxScroll = 2 * mApp.mHeight + 50 - mHeight;
        aNewY = Math.max(aNewY, aMaxScroll);

        mY = aNewY;

        // Faithful to upstream: mY was already assigned, so aDelta is always 0 (dead code kept).
        int aDelta = aNewY - mY;
        mMoreRockRect.mY += aDelta;
        aBackButtonRect.mY += aDelta;

        mScrollValue = Math.max(mScrollValue, 0);
    }

    // AchievementsWidget::Draw (AchievementsScreen.cpp:108-175)
    @Override
    public void draw(Graphics g) {
        g.drawImage(mApp.getImage(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_BG_ID), 0, 0);

        int aHeight = imageHeight(Resources.IMAGE_SELECTORSCREEN_ACHIEVEMENTS_BG_ID);
        for (int i = 1; i <= 70; i++) {
            g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_HOLE_TILE_ID), 0, aHeight * i);
        }

        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_BOOKWORM_ID), 0, 1125);
        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_BEJEWELED_ID), 0, 2250);
        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_CHUZZLE_ID), 0, 4500);
        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_PEGGLE_ID), 0, 6750);
        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_PIPE_ID), 0, 9000);
        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_ZUMA_ID), 0, 11250);

        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_CHINA_ID), 0,
                mHeight - imageHeight(Resources.IMAGE_ACHEESEMENTS_CHINA_ID) - 650);

        if (aBackButtonRect.contains(mWidgetManager.mLastMouseX - mX, mWidgetManager.mLastMouseY - mY)) {
            g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_BACK_HIGHLIGHT_ID), 128, 55);
        }

        for (int i = 0; i < AchievementId.MAX_ACHIEVEMENTS; i++) {
            boolean aHasAchievement;
            if (mApp.mPlayerInfo != null) {
                aHasAchievement = mApp.mPlayerInfo.mEarnedAchievements[i];
            } else {
                aHasAchievement = false;
            }

            int aCurrAchievementOff = 57 * (i / 2);
            int aImageXPos = i % 2 == 0 ? 120 : 410;
            int aImageYPos = 178 + aCurrAchievementOff;
            int aTextXPos = aImageXPos + 70;
            int aTextYPos = aImageYPos + 16;

            Rect aSrcRect = new Rect(70 * (i % 7), 70 * (i / 7), 70, 70);
            Rect aDestRect = new Rect(aImageXPos, aImageYPos, 56, 56);

            g.setColorizeImages(true);
            g.setColor(aHasAchievement ? new Color(255, 255, 255) : new Color(255, 255, 255, 32));

            g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_ICONS_ID), aDestRect, aSrcRect);
            g.setColorizeImages(false);

            g.setFont(mApp.getFont(Resources.FONT_DWARVENTODCRAFT15_ID));
            g.setColor(new Color(21, 175, 0));

            String aName = mApp.getString(gAchievementList[i].name, gAchievementList[i].name);
            g.drawString(aName, aTextXPos, aTextYPos);

            Rect aPos = new Rect(aTextXPos, aTextYPos + 3, 212, 60);

            g.setFont(mApp.getFont(Resources.FONT_DWARVENTODCRAFT12_ID));
            g.setColor(new Color(255, 255, 255));

            String aDesc = mApp.getString(gAchievementList[i].description, gAchievementList[i].description);
            g.writeWordWrapped(aPos, aDesc, 12, -1, null, -1, null);
        }

        g.drawImage(mApp.getImage(Resources.IMAGE_ACHEESEMENTS_MORE_ROCK_ID), 700, 450);

        boolean aIsHighlight = mMoreRockRect.contains(mWidgetManager.mLastMouseX - mX, mWidgetManager.mLastMouseY - mY);
        if (mDidPressMoreButton) {
            g.drawImage(mApp.getImage(aIsHighlight ? Resources.IMAGE_ACHEESEMENTS_TOP_BUTTON_HIGHLIGHT_ID
                    : Resources.IMAGE_ACHEESEMENTS_TOP_BUTTON_ID), 700, 450);
        } else {
            g.drawImage(mApp.getImage(aIsHighlight ? Resources.IMAGE_ACHEESEMENTS_MORE_BUTTON_HIGHLIGHT_ID
                    : Resources.IMAGE_ACHEESEMENTS_MORE_BUTTON_ID), 700, 450);
        }
    }

    // AchievementsWidget::KeyDown (AchievementsScreen.cpp:177-190)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_UP) {
            mScrollValue = mDefaultScrollValue;
            mScrollDirection = 1;
        } else if (theKey == KeyCode.KEYCODE_DOWN) {
            mScrollValue = mDefaultScrollValue;
            mScrollDirection = -1;
        } else if (theKey == KeyCode.KEYCODE_ESCAPE) {
            mApp.mGameSelector.slideTo(0, 0);
            mApp.mGameSelector.mWidgetManager.setFocus(mApp.mGameSelector);
        }
    }

    // AchievementsWidget::MouseDown (AchievementsScreen.cpp:192-199)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        if (aBackButtonRect.contains(x, y)) {
            mApp.playSample(Resources.SOUND_GRAVEBUTTON_ID);
        }

        if (mMoreRockRect.contains(x, y)) {
            mApp.playSample(Resources.SOUND_GRAVEBUTTON_ID);
        }
    }

    // AchievementsWidget::MouseUp (AchievementsScreen.cpp:201-214)
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
        Point aPos = new Point(x, y);
        if (aBackButtonRect.contains(aPos)) {
            mApp.mGameSelector.slideTo(0, 0);
            mApp.mGameSelector.mWidgetManager.setFocus(mApp.mGameSelector);
        }

        if (mMoreRockRect.contains(aPos)) {
            mDidPressMoreButton = !mDidPressMoreButton;
            mScrollDirection = mDidPressMoreButton ? -1 : 1;
            mScrollValue = 20;
        }
    }

    // AchievementsWidget::MouseWheel (AchievementsScreen.cpp:216-220)
    @Override
    public void mouseWheel(int theDelta) {
        mScrollValue = mDefaultScrollValue;

        if (theDelta > 0) {
            mScrollDirection = 1;
        } else if (theDelta < 0) {
            mScrollDirection = -1;
        }
    }
}
