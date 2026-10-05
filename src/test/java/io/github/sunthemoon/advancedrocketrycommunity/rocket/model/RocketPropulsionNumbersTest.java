package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class RocketPropulsionNumbersTest {
    private static final long DENOMINATOR = 1L << 56;
    private static final long MIN_UNITS = 7_205_759_403_792_794L;
    private static final long MAX_UNITS = 288_230_376_151_711_744L;
    private static final long MAX_BASE = (long) RocketLimits.MAX_BLOCKS * 5_000L;
    private static final long MAX_NOMINAL = (long) RocketLimits.MAX_BLOCKS * 3_500L;
    private static final long MAX_SUPPORT = (long) RocketLimits.MAX_BLOCKS * 400_000L;

    @Test
    void capturesDefaultAndInclusiveEndpointsExactly() {
        assertEquals(MIN_UNITS, RocketPropulsionNumbers.captureMultiplierUnits(0.1));
        assertEquals(DENOMINATOR, RocketPropulsionNumbers.captureMultiplierUnits(1.0));
        assertEquals(MAX_UNITS, RocketPropulsionNumbers.captureMultiplierUnits(4.0));
    }

    @Test
    void capturesTheOriginalBinary64PointThree() {
        assertEquals(21_617_278_211_378_380L, RocketPropulsionNumbers.captureMultiplierUnits(0.3));
        assertEquals(Double.doubleToRawLongBits(0.3),
                Double.doubleToRawLongBits(21_617_278_211_378_380L / 0x1.0p56));
    }

    @Test
    void capturePreservesExponentBoundaryAndAdjacentBits() {
        double[] values = {0.1, Math.nextUp(0.1), 0.3, Math.nextDown(0.5), 0.5,
                Math.nextUp(0.5), Math.nextDown(1.0), 1.0, Math.nextUp(1.0),
                Math.nextDown(2.0), 2.0, Math.nextUp(2.0), Math.nextDown(4.0), 4.0};
        for (double value : values) {
            assertCapturedBits(value);
        }
    }

    @Test
    void captureDoesNotSubstituteADecimalLattice() {
        double value = 1.23456789;
        long units = RocketPropulsionNumbers.captureMultiplierUnits(value);
        assertEquals(Double.doubleToRawLongBits(value), Double.doubleToRawLongBits(units / 0x1.0p56));
        assertFalse(units == RocketPropulsionNumbers.captureMultiplierUnits(1.235));
    }

    @Test
    void deterministicRuntimeSamplesRoundTripWithoutQuantizing() {
        Random random = new Random(0xC17A);
        for (int index = 0; index < 512; index++) {
            assertCapturedBits(randomMultiplier(random));
        }
    }

    @Test
    void invalidRuntimeInputsHaveOnlyTheFixedMultiplierFailure() {
        double[] invalid = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                0.0, -0.0, -1.0, Math.nextDown(0.1), Math.nextUp(4.0), Double.MAX_VALUE};
        for (double value : invalid) {
            assertFixedError("MULTIPLIER", () -> RocketPropulsionNumbers.captureMultiplierUnits(value));
        }
    }

    @Test
    void scaleRejectsOutOfRangeAndOffGridUnits() {
        long[] invalid = {Long.MIN_VALUE, -1L, 0L, MIN_UNITS - 1L, DENOMINATOR - 1L,
                DENOMINATOR + 1L, MAX_UNITS - 1L, MAX_UNITS + 1L, Long.MAX_VALUE};
        for (long units : invalid) {
            assertFixedError("MULTIPLIER", () -> RocketPropulsionNumbers.scaleHostRounded(1L, units));
        }
    }

    @Test
    void invalidBaseTakesPriorityOverInvalidUnits() {
        long[] invalid = {Long.MIN_VALUE, -1L, MAX_BASE + 1L, Long.MAX_VALUE};
        for (long base : invalid) {
            assertFixedError("BASE", () -> RocketPropulsionNumbers.scaleHostRounded(base, 0L));
            assertFixedError("BASE", () -> RocketPropulsionNumbers.scaleHostRounded(base, DENOMINATOR));
        }
    }

    @Test
    void zeroBaseStillValidatesTheMultiplier() {
        assertEquals(0L, RocketPropulsionNumbers.scaleHostRounded(0L, MIN_UNITS));
        assertEquals(0L, RocketPropulsionNumbers.scaleHostRounded(0L, MAX_UNITS));
        assertFixedError("MULTIPLIER", () -> RocketPropulsionNumbers.scaleHostRounded(0L, 0L));
        assertFixedError("MULTIPLIER", () -> RocketPropulsionNumbers.scaleHostRounded(0L, DENOMINATOR + 1L));
    }

    @Test
    void defaultMultiplierLeavesEverySelectedBaseUnchanged() {
        long[] bases = {0L, 1L, 3L, 1_000L, 3_500L, 100_000L, MAX_BASE};
        for (long base : bases) {
            assertEquals(base, RocketPropulsionNumbers.scaleHostRounded(base, DENOMINATOR));
        }
    }

    @Test
    void roundedProductIsNotExactRationalFloor() {
        long units = RocketPropulsionNumbers.captureMultiplierUnits(0.3);
        long exactFloor = BigInteger.valueOf(1_000L).multiply(BigInteger.valueOf(units))
                .shiftRight(56).longValueExact();
        assertEquals(299L, exactFloor);
        assertEquals(300L, RocketPropulsionNumbers.scaleHostRounded(1_000L, units));
        assertEquals((long) (1_000.0 * 0.3), RocketPropulsionNumbers.scaleHostRounded(1_000L, units));
    }

    @Test
    void adjacentPointThreeValuesDoNotCollapseAtAnIntegerBoundary() {
        assertEquals(299L, RocketPropulsionNumbers.scaleHostRounded(1_000L,
                RocketPropulsionNumbers.captureMultiplierUnits(Math.nextDown(0.3))));
        assertEquals(300L, RocketPropulsionNumbers.scaleHostRounded(1_000L,
                RocketPropulsionNumbers.captureMultiplierUnits(Math.nextUp(0.3))));
    }

    @Test
    void nearestEvenTieAndExponentCarryInputsHaveLiteralControls() {
        double carry = 0x1.5555555555555p0;
        long carryUnits = RocketPropulsionNumbers.captureMultiplierUnits(carry);
        assertEquals(96_076_792_050_570_576L, carryUnits);
        assertTie(3L, carryUnits, true);
        assertEquals(3L, BigInteger.valueOf(3L).multiply(BigInteger.valueOf(carryUnits))
                .shiftRight(56).longValueExact());
        assertEquals(4L, RocketPropulsionNumbers.scaleHostRounded(3L, carryUnits));

        long evenUnits = DENOMINATOR + 48L;
        long oddUnits = DENOMINATOR + 16L;
        assertTie(3L, evenUnits, false);
        assertTie(3L, oddUnits, true);
        assertEquals(3L, RocketPropulsionNumbers.scaleHostRounded(3L, evenUnits));
        assertEquals(3L, RocketPropulsionNumbers.scaleHostRounded(3L, oddUnits));
    }

    @Test
    void scalingUsesABoundedProductLargerThanLong() {
        BigInteger product = BigInteger.valueOf(MAX_BASE).multiply(BigInteger.valueOf(MAX_UNITS));
        assertEquals(82, product.bitLength());
        assertTrue(product.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertEquals(40_960_000L, RocketPropulsionNumbers.scaleHostRounded(MAX_BASE, MAX_UNITS));
        assertEquals((long) (MAX_BASE * 0.1), RocketPropulsionNumbers.scaleHostRounded(MAX_BASE, MIN_UNITS));
    }

    @Test
    void deterministicProductsMatchIndependentJava17Multiplication() {
        Random random = new Random(0xC17B);
        long[] bases = {0L, 1L, 3L, 1_000L, 3_500L, 100_000L, MAX_BASE};
        for (int index = 0; index < 512; index++) {
            double multiplier = randomMultiplier(random);
            long units = RocketPropulsionNumbers.captureMultiplierUnits(multiplier);
            for (long base : bases) {
                assertEquals((long) ((double) base * multiplier),
                        RocketPropulsionNumbers.scaleHostRounded(base, units));
            }
        }
    }

    @Test
    void aggregateAndPerTermScalingRemainDifferentComputations() {
        long units = RocketPropulsionNumbers.captureMultiplierUnits(1.5);
        assertEquals(3L, RocketPropulsionNumbers.scaleHostRounded(2L, units));
        assertEquals(2L, RocketPropulsionNumbers.scaleHostRounded(1L, units)
                + RocketPropulsionNumbers.scaleHostRounded(1L, units));
    }

    @Test
    void partialSupportReturnsExactMinAndProportionalWorkingCoefficient() {
        assertSupport(RocketPropulsionNumbers.nuclearSupport(140_000L, 100_000L),
                100_000L, 5L, 7L, 10L, 49L);
    }

    @Test
    void fullSupportReturnsCanonicalOneAndTwoSevenths() {
        assertSupport(RocketPropulsionNumbers.nuclearSupport(3_500L, 3_500L),
                3_500L, 1L, 1L, 2L, 7L);
        assertSupport(RocketPropulsionNumbers.nuclearSupport(3_500L, 100_000L),
                3_500L, 1L, 1L, 2L, 7L);
    }

    @Test
    void supportDomainEndpointsHaveExactReducedValues() {
        assertSupport(RocketPropulsionNumbers.nuclearSupport(1L, 1L), 1L, 1L, 1L, 2L, 7L);
        assertSupport(RocketPropulsionNumbers.nuclearSupport(MAX_NOMINAL, MAX_SUPPORT),
                MAX_NOMINAL, 1L, 1L, 2L, 7L);
        assertSupport(RocketPropulsionNumbers.nuclearSupport(MAX_NOMINAL, 1L),
                1L, 1L, 7_168_000L, 1L, 25_088_000L);
    }

    @Test
    void zeroNegativeAndOverBoundSupportInputsRefuseWithoutNormalization() {
        long[][] invalid = {{0L, 1L}, {-1L, 1L}, {MAX_NOMINAL + 1L, 1L},
                {Long.MAX_VALUE, 1L}, {1L, 0L}, {1L, -1L}, {1L, MAX_SUPPORT + 1L},
                {1L, Long.MAX_VALUE}, {Long.MIN_VALUE, Long.MIN_VALUE}};
        for (long[] values : invalid) {
            assertFixedError("SUPPORT", () -> RocketPropulsionNumbers.nuclearSupport(values[0], values[1]));
        }
    }

    @Test
    void supportSamplesMatchIndependentCanonicalIntegerRatios() {
        Random random = new Random(0xC17C);
        for (int index = 0; index < 512; index++) {
            long nominal = 1L + Math.floorMod(random.nextLong(), MAX_NOMINAL);
            long core = 1L + Math.floorMod(random.nextLong(), MAX_SUPPORT);
            long effective = Math.min(nominal, core);
            BigInteger numerator = BigInteger.valueOf(effective);
            BigInteger denominator = BigInteger.valueOf(nominal);
            BigInteger supportGcd = numerator.gcd(denominator);
            BigInteger workingNumerator = numerator.multiply(BigInteger.TWO);
            BigInteger workingDenominator = denominator.multiply(BigInteger.valueOf(7L));
            BigInteger workingGcd = workingNumerator.gcd(workingDenominator);
            assertSupport(RocketPropulsionNumbers.nuclearSupport(nominal, core), effective,
                    numerator.divide(supportGcd).longValueExact(), denominator.divide(supportGcd).longValueExact(),
                    workingNumerator.divide(workingGcd).longValueExact(),
                    workingDenominator.divide(workingGcd).longValueExact());
        }
    }

    @Test
    void numericalSurfaceAndConstructionRemainPackagePrivate() {
        assertTrue(Modifier.isFinal(RocketPropulsionNumbers.class.getModifiers()));
        assertFalse(Modifier.isPublic(RocketPropulsionNumbers.class.getModifiers()));
        for (Constructor<?> constructor : RocketPropulsionNumbers.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
        for (Method method : RocketPropulsionNumbers.class.getDeclaredMethods()) {
            assertFalse(Modifier.isPublic(method.getModifiers()));
            assertFalse(Modifier.isProtected(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
        }
    }

    @Test
    void supportResultHasOnlyPrivateFinalPrimitiveState() {
        Class<?> type = RocketPropulsionNumbers.NuclearSupport.class;
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertTrue(Modifier.isStatic(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        Field[] fields = type.getDeclaredFields();
        assertEquals(5, fields.length);
        for (Field field : fields) {
            assertEquals(long.class, field.getType());
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(Modifier.isStatic(field.getModifiers()));
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
        assertSupport(RocketPropulsionNumbers.nuclearSupport(140_000L, 100_000L),
                100_000L, 5L, 7L, 10L, 49L);
        assertSupport(RocketPropulsionNumbers.nuclearSupport(140_000L, 100_000L),
                100_000L, 5L, 7L, 10L, 49L);
    }

    private static void assertCapturedBits(double multiplier) {
        long units = RocketPropulsionNumbers.captureMultiplierUnits(multiplier);
        assertEquals(Double.doubleToRawLongBits(multiplier), Double.doubleToRawLongBits(units / 0x1.0p56));
        assertEquals((long) (1_000.0 * multiplier), RocketPropulsionNumbers.scaleHostRounded(1_000L, units));
    }

    private static double randomMultiplier(Random random) {
        long minimumBits = Double.doubleToRawLongBits(0.1);
        long maximumBits = Double.doubleToRawLongBits(4.0);
        long bits = minimumBits + Math.floorMod(random.nextLong(), maximumBits - minimumBits + 1L);
        return Double.longBitsToDouble(bits);
    }

    private static void assertTie(long base, long units, boolean oddRetained) {
        BigInteger product = BigInteger.valueOf(base).multiply(BigInteger.valueOf(units));
        int discardedBits = product.bitLength() - 53;
        assertTrue(discardedBits > 0);
        BigInteger retained = product.shiftRight(discardedBits);
        assertEquals(BigInteger.ONE.shiftLeft(discardedBits - 1),
                product.subtract(retained.shiftLeft(discardedBits)));
        assertEquals(oddRetained, retained.testBit(0));
    }

    private static void assertSupport(RocketPropulsionNumbers.NuclearSupport result, long effective,
                                      long supportNumerator, long supportDenominator,
                                      long workingNumerator, long workingDenominator) {
        assertEquals(effective, result.effectiveThrust());
        assertEquals(supportNumerator, result.supportNumerator());
        assertEquals(supportDenominator, result.supportDenominator());
        assertEquals(workingNumerator, result.workingNumerator());
        assertEquals(workingDenominator, result.workingDenominator());
    }

    private static void assertFixedError(String reason, Runnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(IllegalArgumentException.class, exception.getClass());
        assertEquals("Rocket propulsion numbers: " + reason, exception.getMessage());
        assertNull(exception.getCause());
    }
}
