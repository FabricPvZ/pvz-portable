package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MTRand}. Expected values come from the canonical MT19937 sequence for
 * seed 5489 (the well-known reference output begins 3499211612, 581869302, 3890346734, ...),
 * masked with upstream's {@code y &amp;= 0x7FFFFFFF} so that {@code Next()} stays in
 * {@code [0, SEXY_RAND_MAX]}. The reference generator below is an independent MT19937
 * implementation used to cross-check the whole sequence rather than a single constant.
 */
class MTRandTest {

    private static long[] rawMt19937(int seed, int count) {
        final int N = 624;
        final int M = 397;
        final long MATRIX_A = 0x9908b0dfL;
        final long UPPER_MASK = 0x80000000L;
        final long LOWER_MASK = 0x7fffffffL;

        long[] mt = new long[N];
        mt[0] = seed & 0xffffffffL;
        for (int i = 1; i < N; i++) {
            mt[i] = (1812433253L * ((mt[i - 1] ^ (mt[i - 1] >>> 30)) & 0xffffffffL) + i) & 0xffffffffL;
        }

        long[] out = new long[count];
        int idx = N;
        for (int n = 0; n < count; n++) {
            if (idx >= N) {
                for (int kk = 0; kk < N - M; kk++) {
                    long y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = (mt[kk + M] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0)) & 0xffffffffL;
                }
                for (int kk = N - M; kk < N - 1; kk++) {
                    long y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = (mt[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0)) & 0xffffffffL;
                }
                long y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                mt[N - 1] = (mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0)) & 0xffffffffL;
                idx = 0;
            }
            long y = mt[idx++];
            y ^= y >>> 11;
            y ^= (y << 7) & 0x9d2c5680L;
            y ^= (y << 15) & 0xefc60000L;
            y ^= y >>> 18;
            out[n] = y;
        }
        return out;
    }

    @Test
    void referenceMt19937MatchesKnownSeed5489Sequence() {
        long[] raw = rawMt19937(5489, 5);
        assertEquals(3499211612L, raw[0]);
        assertEquals(581869302L, raw[1]);
        assertEquals(3890346734L, raw[2]);
        assertEquals(3586334585L, raw[3]);
        assertEquals(545404204L, raw[4]);
    }

    @Test
    void nextIsMaskedToThirtyOneBits() {
        MTRand rand = new MTRand(5489);
        long[] raw = rawMt19937(5489, 8);
        for (int i = 0; i < raw.length; i++) {
            assertEquals(raw[i] & 0x7FFFFFFFL, rand.next(), "index " + i);
        }
    }

    @Test
    void seed5489FirstValues() {
        MTRand rand = new MTRand(5489);
        assertEquals(1351727964L, rand.next());
        assertEquals(581869302L, rand.next());
        assertEquals(1742863086L, rand.next());
        assertEquals(1438850937L, rand.next());
        assertEquals(545404204L, rand.next());
    }

    @Test
    void defaultSeedIs4357() {
        MTRand rand = new MTRand();
        assertEquals(2146374468L, rand.next());
        assertEquals(699692587L, rand.next());
    }

    @Test
    void seedZeroIsRemappedTo4357() {
        MTRand rand = new MTRand(0);
        assertEquals(2146374468L, rand.next());
    }

    @Test
    void nextWithRangeTakesUnsignedModulo() {
        MTRand rand = new MTRand(5489);
        assertEquals(1351727964L % 7L, rand.next(7L));
        // 581869302 (the second masked draw)
        assertEquals(581869302L % 11L, rand.next(11L));
    }

    @Test
    void nextWithFloatRange() {
        MTRand rand = new MTRand(5489);
        float v = rand.next(10.0f);
        assertEquals(6.2944736f, v, 1e-5f);
        assertEquals((float) ((double) 1351727964L / (double) 0x7FFFFFFF * 10.0), v, 0.0f);
    }

    @Test
    void serializeRoundTripsStateAndContinuesSequence() {
        MTRand rand = new MTRand(5489);
        rand.next();
        rand.next();

        byte[] serialized = rand.serialize();
        assertEquals(624 * 4 + 4, serialized.length);

        MTRand restored = new MTRand(serialized);
        long[] raw = rawMt19937(5489, 6);
        for (int i = 2; i < 6; i++) {
            assertEquals(raw[i] & 0x7FFFFFFFL, restored.next(), "index " + i);
        }
    }

    @Test
    void serializeStringMatchesSerializeBytes() {
        MTRand rand = new MTRand(99);
        assertArrayEquals(rand.serialize(),
                rand.serializeString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    @Test
    void seededArrayOfWrongLengthFallsBackToDefaultSeed() {
        MTRand rand = new MTRand(new byte[10]);
        assertEquals(2146374468L, rand.next());
    }

    @Test
    void seededArrayWithOutOfRangeIndexFallsBackToDefaultSeed() {
        byte[] data = new MTRand(5489).serialize();
        // Force mti = 0 in the little-endian trailer, which upstream rejects.
        data[624 * 4] = 0;
        data[624 * 4 + 1] = 0;
        data[624 * 4 + 2] = 0;
        data[624 * 4 + 3] = 0;
        MTRand rand = new MTRand(data);
        assertEquals(2146374468L, rand.next());
    }

    @Test
    void randAllowedGuardRejectsUnguardedNext() {
        try (MTRand.MTAutoDisallowRand ignored = new MTRand.MTAutoDisallowRand()) {
            MTRand rand = new MTRand(5489);
            assertThrows(AssertionError.class, rand::next);
            // NextNoAssert still works while random numbers are disallowed.
            assertEquals(1351727964L, rand.nextNoAssert());
        }
        // Guard restored by close().
        assertEquals(2146374468L, new MTRand().next());
    }
}
