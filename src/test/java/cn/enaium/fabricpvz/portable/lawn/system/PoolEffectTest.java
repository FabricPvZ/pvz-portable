/*
 * Headless, asset-free tests for cn.enaium.fabricpvz.portable.lawn.system.PoolEffect.
 *
 * PoolEffect.poolEffectInitialize pulls IMAGE_POOL_CAUSTIC_EFFECT, so these tests construct the
 * object directly and fill the public mCausticGrayscaleImage buffer — the only input of
 * bilinearLookupFixedPoint (PoolEffect.cpp:69-87). The expected values are hand-computed from the
 * upstream 16.16 integer formula: 32-bit unsigned products with {@code / 65536} (i.e. {@code >>> 16},
 * which wraps) and one low-byte grayscale sample per corner.
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PoolEffectTest {

    private static PoolEffect withPattern(int theIndex, int theValue) {
        PoolEffect aPoolEffect = new PoolEffect();
        aPoolEffect.mCausticGrayscaleImage = new byte[256 * 256];
        aPoolEffect.mCausticGrayscaleImage[theIndex] = (byte) theValue;
        return aPoolEffect;
    }

    @Test
    void bilinearLookupAtIntegerBoundaryWeightsOnlyTheU0V0Corner() {
        // u = v = 0 -> factorU1 = factorV1 = 1, factorU0 = factorV0 = 65535, all indices 0/1.
        // The other three terms have a /65536-ed factor of 0, so the result is
        // ((65535 * 65535) >>> 16) * gray[0] >>> 16 = (65534 * gray[0]) >>> 16.
        assertEquals(99, withPattern(0, 100).bilinearLookupFixedPoint(0, 0));
        assertEquals(254, withPattern(0, 255).bilinearLookupFixedPoint(0, 0));
        assertEquals(0, withPattern(0, 1).bilinearLookupFixedPoint(0, 0));
    }

    @Test
    void bilinearLookupInterpolatesTheHalfFraction() {
        // u = 0x8000, v = 0 -> factorU1 = 32769 / factorU0 = 32767, factorV1 = 1 / factorV0 = 65535.
        // ((32767 * 65535) >>> 16) = 32766 -> (32766 * 200) >>> 16 = 99; the other terms are 0.
        assertEquals(99, withPattern(0, 200).bilinearLookupFixedPoint(0x8000, 0));
    }

    @Test
    void bilinearLookupWrapsTheLastTexelBackToZero() {
        // u = v = 0xFFFFFFFF -> timeU/timeV = 0xFFFF0000, so indexU0 = indexV0 = 255 and the +1
        // indices wrap to 0; factorU1 = factorV1 = 65535 and factorU0 = factorV0 = 1, i.e. the same
        // weights as (0, 0), so the single weighted corner is indexV1 * 256 + indexU0 = 0.
        assertEquals(254, withPattern(0, 255).bilinearLookupFixedPoint(0xFFFFFFFF, 0xFFFFFFFF));
        assertEquals(254, withPattern(0, 255).bilinearLookupFixedPoint(-1, -1));
    }

    @Test
    void bilinearLookupAddressesTheInteriorTexel() {
        // u = v = 0x00018000 -> timeU/timeV = 0x00010000: indexU0/V0 = 1, indexU1/V1 = 2, and
        // factorU1 = factorV1 = 32769 (factorU0 = factorV0 = 32767). The dominant term is
        // ((32769 * 32769) >>> 16) = 16385 at indexV1 * 256 + indexU1 = 2 * 256 + 2 = 514:
        // (16385 * 255) >>> 16 = 63.
        assertEquals(63, withPattern(514, 255).bilinearLookupFixedPoint(0x00018000, 0x00018000));
    }

    @Test
    void poolEffectUpdateAdvancesTheCounter() {
        PoolEffect aPoolEffect = new PoolEffect();
        assertEquals(0, aPoolEffect.mPoolCounter);
        aPoolEffect.poolEffectUpdate();
        aPoolEffect.poolEffectUpdate();
        assertEquals(2, aPoolEffect.mPoolCounter);
    }
}
