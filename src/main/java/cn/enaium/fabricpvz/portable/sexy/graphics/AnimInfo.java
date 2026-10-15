package cn.enaium.fabricpvz.portable.sexy.graphics;

import java.util.Arrays;

/**
 * Ported from src/SexyAppFramework/graphics/Image.h:52 (implementation src/SexyAppFramework/graphics/Image.cpp:84-185).
 *
 * <p>Faithful port of {@code Sexy::AnimInfo}: the animation timing of an image strip. Time is
 * measured in hundredths of a second, exactly as upstream ({@code mFrameDelay} is documented as
 * "1/100s" and the game passes update counts scaled to that unit).
 *
 * <p>PORT-NOTE: upstream keeps {@code mPerFrameDelay} and {@code mFrameMap} as
 * {@code std::vector<int>} and tests them with {@code empty()}. Java has no cheap "empty but
 * resizable" container, so both are {@code int[]} where {@code null} means "empty vector":
 * every upstream {@code .empty()}/{@code !.empty()} maps to {@code == null}/{@code != null} and
 * {@code resize(n)} maps to {@link Arrays#copyOf(int[], int)} (which zero-fills when growing,
 * matching {@code std::vector::resize}).
 */
public class AnimInfo {
    public AnimType mAnimType;
    /** Delay per frame in 1/100 s. */
    public int mFrameDelay;
    public int mNumCels;
    /** Optional per-frame delays ({@code null} = upstream empty vector). */
    public int[] mPerFrameDelay;
    /** Optional cel remap table, used by ping-pong ({@code null} = upstream empty vector). */
    public int[] mFrameMap;
    public int mTotalAnimTime;

    public AnimInfo() {
        mAnimType = AnimType.AnimType_None;
        mFrameDelay = 1;
        mNumCels = 1;
    }

    /** Replaces upstream's implicit copy constructor ({@code make_unique<AnimInfo>(*from)}). */
    public AnimInfo(AnimInfo from) {
        mAnimType = from.mAnimType;
        mFrameDelay = from.mFrameDelay;
        mNumCels = from.mNumCels;
        mPerFrameDelay = from.mPerFrameDelay == null ? null : from.mPerFrameDelay.clone();
        mFrameMap = from.mFrameMap == null ? null : from.mFrameMap.clone();
        mTotalAnimTime = from.mTotalAnimTime;
    }

    // SetPerFrameDelay
    public void setPerFrameDelay(int theFrame, int theTime) {
        if (mPerFrameDelay == null) {
            mPerFrameDelay = new int[0];
        }
        if (mPerFrameDelay.length <= theFrame) {
            mPerFrameDelay = Arrays.copyOf(mPerFrameDelay, theFrame + 1);
        }

        mPerFrameDelay[theFrame] = theTime;
    }

    // Compute(int theNumCels, int theBeginFrameTime = 0, int theEndFrameTime = 0)
    public void compute(int theNumCels, int theBeginFrameTime, int theEndFrameTime) {
        mNumCels = theNumCels;
        if (mNumCels <= 0) {
            mNumCels = 1;
        }

        if (mFrameDelay <= 0) {
            mFrameDelay = 1;
        }

        if (mAnimType == AnimType.AnimType_PingPong && mNumCels > 1) {
            mFrameMap = new int[theNumCels * 2 - 2];
            int index = 0;
            for (int i = 0; i < theNumCels; i++) {
                mFrameMap[index++] = i;
            }
            for (int i = theNumCels - 2; i >= 1; i--) {
                mFrameMap[index++] = i;
            }
        }

        if (mFrameMap != null) {
            mNumCels = mFrameMap.length;
        }

        if (theBeginFrameTime > 0) {
            setPerFrameDelay(0, theBeginFrameTime);
        }

        if (theEndFrameTime > 0) {
            setPerFrameDelay(mNumCels - 1, theEndFrameTime);
        }

        if (mPerFrameDelay != null) {
            mTotalAnimTime = 0;
            mPerFrameDelay = Arrays.copyOf(mPerFrameDelay, mNumCels);

            for (int i = 0; i < mNumCels; i++) {
                if (mPerFrameDelay[i] <= 0) {
                    mPerFrameDelay[i] = mFrameDelay;
                }

                mTotalAnimTime += mPerFrameDelay[i];
            }
        } else {
            mTotalAnimTime = mFrameDelay * mNumCels;
        }

        if (mFrameMap != null) {
            mFrameMap = Arrays.copyOf(mFrameMap, mNumCels);
        }
    }

    // Compute(int theNumCels)
    public void compute(int theNumCels) {
        compute(theNumCels, 0, 0);
    }

    public int getPerFrameCel(int theTime) {
        for (int i = 0; i < mNumCels; i++) {
            theTime -= mPerFrameDelay[i];
            if (theTime < 0) {
                return i;
            }
        }

        return mNumCels - 1;
    }

    public int getCel(int theTime) {
        if (mAnimType == AnimType.AnimType_Once && theTime >= mTotalAnimTime) {
            if (mFrameMap != null) {
                return mFrameMap[mFrameMap.length - 1];
            } else {
                return mNumCels - 1;
            }
        }

        theTime = theTime % mTotalAnimTime;

        int aFrame;
        if (mPerFrameDelay != null) {
            aFrame = getPerFrameCel(theTime);
        } else {
            aFrame = (theTime / mFrameDelay) % mNumCels;
        }

        if (mFrameMap == null) {
            return aFrame;
        } else {
            return mFrameMap[aFrame];
        }
    }
}
