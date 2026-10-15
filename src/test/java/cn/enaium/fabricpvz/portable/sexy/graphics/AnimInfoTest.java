package cn.enaium.fabricpvz.portable.sexy.graphics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AnimInfo} (ported from src/SexyAppFramework/graphics/Image.h:52 and
 * Image.cpp:84-185). Every expected value is hand-derived below from the upstream formulas:
 *
 * <pre>
 * compute(n, b, e):
 *   mNumCels = max(n, 1)
 *   mFrameDelay = max(mFrameDelay, 1)
 *   if PingPong && n &gt; 1: mFrameMap = [0..n-1, n-2..1]        (length n*2-2)
 *   if mFrameMap != null: mNumCels = mFrameMap.length
 *   if b &gt; 0: mPerFrameDelay[0] = b
 *   if e &gt; 0: mPerFrameDelay[mNumCels-1] = e
 *   if mPerFrameDelay != null:
 *       resize to mNumCels (zero-fill); each entry &lt;= 0 becomes mFrameDelay; mTotalAnimTime = sum
 *   else: mTotalAnimTime = mFrameDelay * mNumCels
 *   if mFrameMap != null: resize to mNumCels
 *
 * getCel(t):
 *   if Once && t &gt;= mTotalAnimTime: return last(mFrameMap) or mNumCels-1
 *   t %= mTotalAnimTime
 *   aFrame = (mPerFrameDelay != null) ? getPerFrameCel(t) : (t / mFrameDelay) % mNumCels
 *   return mFrameMap == null ? aFrame : mFrameMap[aFrame]
 *
 * getPerFrameCel(t): walk frames subtracting mPerFrameDelay[i]; first i where t &lt; 0 wins; else mNumCels-1
 * </pre>
 */
class AnimInfoTest {

    /** 1. Constructor defaults. */
    @Test
    void constructorDefaults() {
        AnimInfo info = new AnimInfo();

        assertEquals(AnimType.AnimType_None, info.mAnimType);
        assertEquals(1, info.mFrameDelay);
        assertEquals(1, info.mNumCels);
        assertNull(info.mPerFrameDelay); // upstream empty std::vector
        assertNull(info.mFrameMap);      // upstream empty std::vector
        assertEquals(0, info.mTotalAnimTime);
    }

    /** 2a. compute(n) with no per-frame delays takes the {@code mFrameDelay*mNumCels} path. */
    @Test
    void computeWithoutPerFrameDelays() {
        AnimInfo info = new AnimInfo();
        info.compute(4);

        assertEquals(4, info.mNumCels);
        // no per-frame delays: mTotalAnimTime = mFrameDelay(1) * mNumCels(4) = 4
        assertEquals(4, info.mTotalAnimTime);

        // getCel(t) = (t / mFrameDelay) % mNumCels = (t / 1) % 4
        int[] expected = {0, 1, 2, 3, 0, 1}; // t = 0,1,2,3,4,5
        for (int t = 0; t <= 5; t++) {
            assertEquals(expected[t], info.getCel(t), "getCel(" + t + ")");
        }
        // t=4: 4 % 4 = 0 -> wraps to frame 0
        assertEquals(0, info.getCel(4));
    }

    /** 2b. Same but mFrameDelay = 10 set before compute(4). */
    @Test
    void computeWithoutPerFrameDelaysWithFrameDelay() {
        AnimInfo info = new AnimInfo();
        info.mFrameDelay = 10;
        info.compute(4);

        // mTotalAnimTime = mFrameDelay(10) * mNumCels(4) = 40
        assertEquals(40, info.mTotalAnimTime);

        // getCel(t) = (t / 10) % 4
        assertEquals(0, info.getCel(0));   // 0/10 = 0
        assertEquals(1, info.getCel(10));  // 10/10 = 1
        assertEquals(2, info.getCel(20));  // 20/10 = 2
        assertEquals(3, info.getCel(30));  // 30/10 = 3
        assertEquals(0, info.getCel(40));  // 40/10 = 4 % 4 = 0
        // getCel(39): 39/10 = 3; 3 % 4 = 3
        assertEquals(3, info.getCel(39));
    }

    /** 3. compute(0) clamps mNumCels to 1; compute with mFrameDelay = 0 clamps it to 1. */
    @Test
    void computeClampsNumCelsAndFrameDelay() {
        AnimInfo info = new AnimInfo();
        info.compute(0);
        assertEquals(1, info.mNumCels);      // max(0, 1)
        assertEquals(1, info.mTotalAnimTime); // 1 * 1

        AnimInfo zeroDelay = new AnimInfo();
        zeroDelay.mFrameDelay = 0;
        zeroDelay.compute(3);
        assertEquals(1, zeroDelay.mFrameDelay); // max(0, 1)
        assertEquals(3, zeroDelay.mNumCels);
        // mTotalAnimTime = mFrameDelay(1) * mNumCels(3) = 3
        assertEquals(3, zeroDelay.mTotalAnimTime);
    }

    /** 4. AnimType_Once holds on the last cel instead of wrapping. */
    @Test
    void onceTypeClampsToLastCel() {
        AnimInfo info = new AnimInfo();
        info.mAnimType = AnimType.AnimType_Once;
        info.mFrameDelay = 10;
        info.compute(3);

        // mTotalAnimTime = 10 * 3 = 30
        assertEquals(30, info.mTotalAnimTime);

        // t in [0,10): 0; [10,20): 1; [20,30): 2  (t/10 % 3)
        for (int t = 0; t <= 9; t++) {
            assertEquals(0, info.getCel(t), "getCel(" + t + ")");
        }
        for (int t = 10; t <= 19; t++) {
            assertEquals(1, info.getCel(t), "getCel(" + t + ")");
        }
        for (int t = 20; t <= 29; t++) {
            assertEquals(2, info.getCel(t), "getCel(" + t + ")");
        }
        // t >= mTotalAnimTime -> Once branch returns mNumCels-1 = 2 (no map)
        assertEquals(2, info.getCel(30));
        assertEquals(2, info.getCel(35));
    }

    /** 5a. PingPong with 4 cels builds the {0,1,2,3,2,1} map. */
    @Test
    void pingPongBuildsFrameMap() {
        AnimInfo info = new AnimInfo();
        info.mAnimType = AnimType.AnimType_PingPong;
        info.compute(4);

        assertArrayEquals(new int[] {0, 1, 2, 3, 2, 1}, info.mFrameMap);
        assertEquals(6, info.mNumCels);       // map length
        // no per-frame delays: mTotalAnimTime = mFrameDelay(1) * 6 = 6
        assertEquals(6, info.mTotalAnimTime);

        // getCel(t) = mFrameMap[t % 6] for t = 0..11
        int[] expected = {0, 1, 2, 3, 2, 1, 0, 1, 2, 3, 2, 1};
        for (int t = 0; t <= 11; t++) {
            assertEquals(expected[t], info.getCel(t), "getCel(" + t + ")");
        }
        // t=6: 6 % 6 = 0 -> mFrameMap[0] = 0
        assertEquals(0, info.getCel(6));
    }

    /** 5b. PingPong with a single cel leaves mFrameMap null (n &gt; 1 guard). */
    @Test
    void pingPongSingleCelKeepsNullFrameMap() {
        AnimInfo info = new AnimInfo();
        info.mAnimType = AnimType.AnimType_PingPong;
        info.compute(1);

        assertNull(info.mFrameMap);
        assertEquals(1, info.mNumCels);
    }

    /** 6. Per-frame delays with a zero-filled middle entry falling back to mFrameDelay. */
    @Test
    void perFrameDelays() {
        AnimInfo info = new AnimInfo();
        info.setPerFrameDelay(0, 5); // array grows to [5]
        info.setPerFrameDelay(2, 7); // grows to [5, 0, 7]
        info.compute(3, 0, 0);

        // entry 1 was 0 <= 0 -> mFrameDelay = 1; total = 5 + 1 + 7 = 13
        assertArrayEquals(new int[] {5, 1, 7}, info.mPerFrameDelay);
        assertEquals(13, info.mTotalAnimTime);

        // getPerFrameCel: t-5 >= 0 then t-1 >= 0 then t-7 < 0 -> cel 2 window is [6,12]
        for (int t = 0; t <= 4; t++) {
            assertEquals(0, info.getPerFrameCel(t), "getPerFrameCel(" + t + ")");
        }
        assertEquals(1, info.getPerFrameCel(5));   // 5-5 = 0, 0-1 < 0
        for (int t = 6; t <= 12; t++) {
            assertEquals(2, info.getPerFrameCel(t), "getPerFrameCel(" + t + ")");
        }

        assertEquals(2, info.getCel(12)); // 12 % 13 = 12 -> cel 2
        assertEquals(0, info.getCel(13)); // 13 % 13 = 0 -> cel 0 (wrap)
        assertEquals(0, info.getCel(4));  // 4 % 13 = 4 -> cel 0
    }

    /** 7. begin/end frame times are injected into mPerFrameDelay[0] / [mNumCels-1]. */
    @Test
    void beginAndEndFrameTimes() {
        AnimInfo info = new AnimInfo();
        info.compute(4, 10, 20);

        // setPerFrameDelay(0,10) -> [10]; setPerFrameDelay(3,20) -> [10,0,0,20];
        // 0 entries become mFrameDelay(1): [10,1,1,20]; total = 10+1+1+20 = 32
        assertArrayEquals(new int[] {10, 1, 1, 20}, info.mPerFrameDelay);
        assertEquals(32, info.mTotalAnimTime);

        assertEquals(0, info.getCel(0));  // 0-10 < 0
        assertEquals(0, info.getCel(9));  // 9-10 < 0
        assertEquals(1, info.getCel(10)); // 10-10=0, 0-1<0
        assertEquals(2, info.getCel(11)); // 11-10=1, 1-1=0, 0-1<0
        assertEquals(3, info.getCel(12)); // 12-10=2, 2-1=1, 1-1=0, 0-20<0
        assertEquals(3, info.getCel(31)); // 31-10=21,20,19, 19-20<0
        assertEquals(0, info.getCel(32)); // 32 % 32 = 0 (wrap) -> cel 0
    }

    /** 8. PingPong combined with per-frame delays. */
    @Test
    void pingPongWithPerFrameDelays() {
        AnimInfo info = new AnimInfo();
        info.mAnimType = AnimType.AnimType_PingPong;
        info.setPerFrameDelay(0, 3); // [3]
        info.setPerFrameDelay(3, 3); // [3, 0, 0, 3]
        info.compute(3);

        // map = [0,1,2, 1] (length 4); mNumCels = 4;
        // mPerFrameDelay resized to 4 = [3,0,0,3] -> zeros become 1 -> [3,1,1,3]; total = 8
        assertArrayEquals(new int[] {0, 1, 2, 1}, info.mFrameMap);
        assertEquals(4, info.mNumCels);
        assertArrayEquals(new int[] {3, 1, 1, 3}, info.mPerFrameDelay);
        assertEquals(8, info.mTotalAnimTime);

        // getPerFrameCel: [0,1,2] windows 0-2 / 3 / 4 / 5-7 -> aFrame 0,0,0,1,2,3,3,3
        // then mapped through mFrameMap {0,1,2,1} -> 0,0,0,1,2,1,1,1
        int[] expected = {0, 0, 0, 1, 2, 1, 1, 1};
        for (int t = 0; t <= 7; t++) {
            assertEquals(expected[t], info.getCel(t), "getCel(" + t + ")");
        }
        // t=8: 8 % 8 = 0 -> aFrame 0 -> mFrameMap[0] = 0
        assertEquals(0, info.getCel(8));
    }

    /** 9. AnimInfo(AnimInfo) deep-copies both arrays. */
    @Test
    void copyConstructorDeepCopiesArrays() {
        AnimInfo source = new AnimInfo();
        source.mAnimType = AnimType.AnimType_PingPong;
        source.setPerFrameDelay(0, 3);
        source.setPerFrameDelay(3, 3);
        source.compute(3);

        AnimInfo copy = new AnimInfo(source);
        assertNotNull(copy.mPerFrameDelay);
        assertNotNull(copy.mFrameMap);

        // Mutating the source's arrays (and scalars) must not affect the copy.
        source.mPerFrameDelay[0] = 99;
        source.mFrameMap[0] = 7;
        source.mFrameDelay = 42;
        source.mTotalAnimTime = -1;

        assertArrayEquals(new int[] {3, 1, 1, 3}, copy.mPerFrameDelay);
        assertArrayEquals(new int[] {0, 1, 2, 1}, copy.mFrameMap);
        assertEquals(1, copy.mFrameDelay);
        assertEquals(8, copy.mTotalAnimTime);
    }
}
