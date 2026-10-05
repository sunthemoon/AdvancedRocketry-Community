package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.math.BigInteger;

/** Private scalar arithmetic; results do not grant flight or resource authority. */
final class RocketPropulsionNumbers {
    private static final int DENOMINATOR_BITS = 56;
    private static final long MIN_MULTIPLIER_UNITS = 7_205_759_403_792_794L;
    private static final long MAX_MULTIPLIER_UNITS = 4L << DENOMINATOR_BITS;
    private static final long MAX_HOST_BASE = (long) RocketLimits.MAX_BLOCKS * 5_000L;
    private static final long MAX_NUCLEAR_NOMINAL = (long) RocketLimits.MAX_BLOCKS * 3_500L;
    private static final long MAX_CORE_SUPPORT = (long) RocketLimits.MAX_BLOCKS * 100_000L * 4L;
    private static final long SIGNIFICAND_BIT = 1L << 52;
    private static final long FRACTION_MASK = SIGNIFICAND_BIT - 1L;
    private static final String ERROR_PREFIX = "Rocket propulsion numbers: ";

    private RocketPropulsionNumbers() {
    }

    static long captureMultiplierUnits(double runtimeValue) {
        require(Double.isFinite(runtimeValue) && runtimeValue >= 0.1 && runtimeValue <= 4.0,
                "MULTIPLIER");
        long bits = Double.doubleToRawLongBits(runtimeValue);
        int exponent = (int) ((bits >>> 52) & 0x7ffL);
        long significand = SIGNIFICAND_BIT | (bits & FRACTION_MASK);
        return significand << (exponent - 1019);
    }

    static long scaleHostRounded(long base, long capturedUnits) {
        require(base >= 0L && base <= MAX_HOST_BASE, "BASE");
        validateMultiplierUnits(capturedUnits);

        // Validated inputs bound the product to 82 bits, including at base zero.
        BigInteger product = BigInteger.valueOf(base).multiply(BigInteger.valueOf(capturedUnits));
        int discardedBits = Math.max(0, product.bitLength() - 53);
        if (discardedBits > 0) {
            BigInteger retained = product.shiftRight(discardedBits);
            BigInteger remainder = product.subtract(retained.shiftLeft(discardedBits));
            BigInteger halfway = BigInteger.ONE.shiftLeft(discardedBits - 1);
            int comparison = remainder.compareTo(halfway);
            if (comparison > 0 || comparison == 0 && retained.testBit(0)) {
                retained = retained.add(BigInteger.ONE);
            }
            // Keep a carry into the next binary64 exponent before taking the floor.
            product = retained.shiftLeft(discardedBits);
        }
        return product.shiftRight(DENOMINATOR_BITS).longValueExact();
    }

    static NuclearSupport nuclearSupport(long nominal, long coreSupport) {
        require(nominal > 0L && nominal <= MAX_NUCLEAR_NOMINAL
                && coreSupport > 0L && coreSupport <= MAX_CORE_SUPPORT, "SUPPORT");
        long effective = Math.min(nominal, coreSupport);
        long supportDivisor = gcd(effective, nominal);
        long workingNumerator = 2L * effective;
        long workingDenominator = 7L * nominal;
        long workingDivisor = gcd(workingNumerator, workingDenominator);
        return new NuclearSupport(effective, effective / supportDivisor, nominal / supportDivisor,
                workingNumerator / workingDivisor, workingDenominator / workingDivisor);
    }

    private static void validateMultiplierUnits(long units) {
        require(units >= MIN_MULTIPLIER_UNITS && units <= MAX_MULTIPLIER_UNITS, "MULTIPLIER");
        int discardedBits = Math.max(0, Long.SIZE - Long.numberOfLeadingZeros(units) - 53);
        long mask = (1L << discardedBits) - 1L;
        require((units & mask) == 0L, "MULTIPLIER");
    }

    private static long gcd(long first, long second) {
        while (second != 0L) {
            long remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalArgumentException(ERROR_PREFIX + reason);
        }
    }

    static final class NuclearSupport {
        private final long effectiveThrust;
        private final long supportNumerator;
        private final long supportDenominator;
        private final long workingNumerator;
        private final long workingDenominator;

        private NuclearSupport(long effectiveThrust, long supportNumerator, long supportDenominator,
                               long workingNumerator, long workingDenominator) {
            this.effectiveThrust = effectiveThrust;
            this.supportNumerator = supportNumerator;
            this.supportDenominator = supportDenominator;
            this.workingNumerator = workingNumerator;
            this.workingDenominator = workingDenominator;
        }

        long effectiveThrust() {
            return effectiveThrust;
        }

        long supportNumerator() {
            return supportNumerator;
        }

        long supportDenominator() {
            return supportDenominator;
        }

        long workingNumerator() {
            return workingNumerator;
        }

        long workingDenominator() {
            return workingDenominator;
        }
    }
}
