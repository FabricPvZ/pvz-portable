package cn.enaium.fabricpvz.portable.sexy;

import java.nio.charset.StandardCharsets;

/**
 * Ported from src/SexyAppFramework/misc/MTRand.h:12 and src/SexyAppFramework/misc/MTRand.cpp:62.
 *
 * <p>Faithful port of {@code Sexy::MTRand}, the MT19937 Mersenne Twister used by the game's
 * simulation and replay (.dmo) logic. The generated sequence is bit-exact with upstream:
 * the standard MT19937 init (Knuth line 25, seed 0 remapped to 4357), the standard twist,
 * the standard tempering, and finally upstream's {@code y &= 0x7FFFFFFF} so {@code Next()}
 * returns a value in {@code [0, SEXY_RAND_MAX]}.
 *
 * <p>PORT-NOTE: upstream stores the state as {@code uint32_t} and shifts with logical
 * (unsigned) semantics; this port keeps the same 32-bit patterns in {@code int} and uses
 * {@code >>>} where upstream relies on unsigned shifts. {@code Next(unsigned long)} is a
 * 64-bit {@code long} parameter on LP64 platforms and is ported as {@code long}.
 */
public class MTRand {
    public static final int MTRAND_N = 624;

    private static final int MTRAND_M = 397;
    private static final int MATRIX_A = 0x9908b0df;   // constant vector a
    private static final int UPPER_MASK = 0x80000000; // most significant w-r bits
    private static final int LOWER_MASK = 0x7fffffff; // least significant r bits

    // Tempering parameters
    private static final int TEMPERING_MASK_B = 0x9d2c5680;
    private static final int TEMPERING_MASK_C = 0xefc60000;

    /** the array for the state vector */
    private final int[] mt = new int[MTRAND_N];
    private int mti;

    private static int gRandAllowed = 0;

    public MTRand(String theSerialData) {
        sRand(theSerialData);
    }

    public MTRand(byte[] theSerialData) {
        sRand(theSerialData);
    }

    public MTRand(long seed) {
        sRand(seed);
    }

    public MTRand() {
        sRand(4357);
    }

    // SetRandAllowed
    public static void setRandAllowed(boolean allowed) {
        if (allowed) {
            if (gRandAllowed > 0) {
                gRandAllowed--;
            } else {
                Debug.dbgAssert(false); // mismatched calls
            }
        } else {
            gRandAllowed++;
        }
    }

    // SRand(const std::string&)
    public void sRand(byte[] theSerialData) {
        if (theSerialData.length == MTRAND_N * 4 + 4) {
            // little-endian, 4 bytes per word: independent of host byte order
            int p = 0;
            for (int i = 0; i < MTRAND_N; i++) {
                mt[i] = (theSerialData[p] & 0xFF)
                        | ((theSerialData[p + 1] & 0xFF) << 8)
                        | ((theSerialData[p + 2] & 0xFF) << 16)
                        | ((theSerialData[p + 3] & 0xFF) << 24);
                p += 4;
            }
            mti = (theSerialData[p] & 0xFF)
                    | ((theSerialData[p + 1] & 0xFF) << 8)
                    | ((theSerialData[p + 2] & 0xFF) << 16)
                    | ((theSerialData[p + 3] & 0xFF) << 24);
            if (mti < 1 || mti > MTRAND_N) { // reject out-of-range index before it can drive a negative read
                sRand(4357);
            }
        } else {
            sRand(4357);
        }
    }

    /** Convenience Latin-1 view of {@link #sRand(byte[])} for upstream's {@code std::string} API. */
    public void sRand(String theSerialData) {
        sRand(theSerialData.getBytes(StandardCharsets.ISO_8859_1));
    }

    // SRand(unsigned long)
    public void sRand(long seed) {
        if (seed == 0) {
            seed = 4357;
        }

        /* setting initial seeds to mt[MTRAND_N] using         */
        /* the generator Line 25 of Table 1 in          */
        /* [KNUTH 1981, The Art of Computer Programming */
        /*    Vol. 2 (2nd Ed.), pp102]                  */
        mt[0] = (int) (seed & 0xffffffffL);
        for (mti = 1; mti < MTRAND_N; mti++) {
            mt[mti] = (int) (1812433253L * ((mt[mti - 1] ^ (mt[mti - 1] >>> 30)) & 0xffffffffL) + mti);
            /* See Knuth TAOCP Vol2. 3rd Ed. P.106 for multiplier. */
        }
    }

    // Next
    public long next() {
        Debug.dbgAssert(gRandAllowed == 0);
        return nextNoAssert();
    }

    // NextNoAssert
    public long nextNoAssert() {
        int y;
        int[] mag01 = {0x0, MATRIX_A};
        /* mag01[x] = x * MATRIX_A  for x=0,1 */

        if (mti >= MTRAND_N) { // generate MTRAND_N words at one time
            int kk;

            for (kk = 0; kk < MTRAND_N - MTRAND_M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + MTRAND_M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }
            for (; kk < MTRAND_N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (MTRAND_M - MTRAND_N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }
            y = (mt[MTRAND_N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[MTRAND_N - 1] = mt[MTRAND_M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];

            mti = 0;
        }

        y = mt[mti++];
        y ^= (y >>> 11);
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        y &= 0x7FFFFFFF;

        return y & 0xFFFFFFFFL;
    }

    // NextNoAssert(unsigned long)
    // PORT-NOTE: C++ unsigned modulo by zero is undefined; Java throws ArithmeticException.
    public long nextNoAssert(long range) {
        return nextNoAssert() % range;
    }

    // Next(unsigned long)
    public long next(long range) {
        Debug.dbgAssert(gRandAllowed == 0);
        return nextNoAssert(range);
    }

    // NextNoAssert(float)
    public float nextNoAssert(float range) {
        return (float) ((double) nextNoAssert() / (double) 0x7FFFFFFF * range);
    }

    // Next(float)
    public float next(float range) {
        Debug.dbgAssert(gRandAllowed == 0);
        return nextNoAssert(range);
    }

    // Serialize
    public byte[] serialize() {
        byte[] aString = new byte[MTRAND_N * 4 + 4];

        // little-endian, 4 bytes per word: independent of host byte order
        int p = 0;
        for (int i = 0; i < MTRAND_N; i++) {
            int aValue = mt[i];
            aString[p++] = (byte) (aValue & 0xFF);
            aString[p++] = (byte) ((aValue >>> 8) & 0xFF);
            aString[p++] = (byte) ((aValue >>> 16) & 0xFF);
            aString[p++] = (byte) ((aValue >>> 24) & 0xFF);
        }
        int aMti = mti;
        aString[p++] = (byte) (aMti & 0xFF);
        aString[p++] = (byte) ((aMti >>> 8) & 0xFF);
        aString[p++] = (byte) ((aMti >>> 16) & 0xFF);
        aString[p] = (byte) ((aMti >>> 24) & 0xFF);

        return aString;
    }

    /** Convenience Latin-1 view of {@link #serialize()} for upstream's {@code std::string} API. */
    public String serializeString() {
        return new String(serialize(), StandardCharsets.ISO_8859_1);
    }

    /**
     * Ported from src/SexyAppFramework/misc/MTRand.h:36. Upstream disallows random numbers for
     * the {}-scoped lifetime (destructor re-enables them); close() mirrors that destructor.
     */
    public static class MTAutoDisallowRand implements AutoCloseable {
        public MTAutoDisallowRand() {
            MTRand.setRandAllowed(false);
        }

        @Override
        public void close() {
            MTRand.setRandAllowed(true);
        }
    }
}
