/*
 * Ported from src/Lawn/Widget/ChallengeScreen.h:96-104 (class ChallengeDefinition).
 */
package cn.enaium.fabricpvz.portable.lawn;

/**
 * Ported from src/Lawn/Widget/ChallengeScreen.h:96-104.
 *
 * <p>One row of {@code gChallengeDefs} ({@code ChallengeScreen.cpp:39-215}): the game mode, its
 * challenge-book icon index and page placement. {@code mChallengeMode} is a {@link GameMode}
 * constant, {@code mPage} a {@link ChallengePage} constant, and {@code mChallengeName} the raw
 * upstream string literal (translated by the UI layer, never here).
 */
public class ChallengeDefinition {

    /** Upstream {@code mChallengeMode} ({@code GameMode}). */
    public int mChallengeMode;

    /** Upstream {@code mChallengeIconIndex} ({@code int}). */
    public int mChallengeIconIndex;

    /** Upstream {@code mPage} ({@code ChallengePage}). */
    public int mPage;

    /** Upstream {@code mRow} ({@code int}). */
    public int mRow;

    /** Upstream {@code mCol} ({@code int}). */
    public int mCol;

    /** Upstream {@code mChallengeName} ({@code const char*}). */
    public String mChallengeName;

    public ChallengeDefinition() {
    }

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
