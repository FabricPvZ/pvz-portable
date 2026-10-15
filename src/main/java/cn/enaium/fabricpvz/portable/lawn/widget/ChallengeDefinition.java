/*
 * Ported from src/Lawn/Widget/ChallengeScreen.h:96-104.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

/**
 * Ported from src/Lawn/Widget/ChallengeScreen.h:96-104.
 *
 * <p>One row of the {@code gChallengeDefs} table: the game mode, its thumbnail cel, the page it is
 * shown on, its grid cell and the (translatable) display name. Public fields keep the upstream
 * {@code m} prefix; the C++ aggregate initialisation becomes a plain constructor.
 */
public class ChallengeDefinition {
    public int mChallengeMode;
    public int mChallengeIconIndex;
    public int mPage;
    public int mRow;
    public int mCol;
    public String mChallengeName;

    public ChallengeDefinition(int theChallengeMode, int theChallengeIconIndex, int thePage, int theRow,
            int theCol, String theChallengeName) {
        mChallengeMode = theChallengeMode;
        mChallengeIconIndex = theChallengeIconIndex;
        mPage = thePage;
        mRow = theRow;
        mCol = theCol;
        mChallengeName = theChallengeName;
    }
}
