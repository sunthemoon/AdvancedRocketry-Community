package io.github.sunthemoon.arceintenttest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class CaseGuardTest {
    @ParameterizedTest @EnumSource(IntentCase.class)
    void eachCaseHasAnExplicitLocalPrerequisite(IntentCase which) {
        boolean same = which == IntentCase.FLIGHT_NON_OWNER || which == IntentCase.CLASSIC_NON_OWNER
                || which == IntentCase.FLIGHT_FORBIDDEN_SURFACE || which == IntentCase.FLIGHT_PRIVATE_STATION;
        boolean inventory = which == IntentCase.FLIGHT_LATE || which == IntentCase.CLASSIC_LATE;
        boolean entity = which != IntentCase.FLIGHT_STALE;
        double distance = which == IntentCase.FLIGHT_RANGE || which == IntentCase.CLASSIC_RANGE ? 65 : 64;
        var observed = new CaseGuard.Observation(same, inventory, entity, distance,
                which == IntentCase.MOTION_SPECTATOR, false);
        assertDoesNotThrow(() -> CaseGuard.check(which, 1, observed));
    }

    @Test void rangeIsStrictlyBeyondEightBlocks() {
        for (var which : new IntentCase[] {IntentCase.FLIGHT_RANGE, IntentCase.CLASSIC_RANGE}) {
            assertThrows(IllegalStateException.class, () -> CaseGuard.check(which, 1,
                    new CaseGuard.Observation(false, true, true, 64, false, false)));
            assertDoesNotThrow(() -> CaseGuard.check(which, 1,
                    new CaseGuard.Observation(false, true, true, Math.nextUp(64.0), false, false)));
        }
    }

    @Test void localStateCannotStandInForTheNegativeCondition() {
        assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.FLIGHT_STALE, 0,
                new CaseGuard.Observation(false, true, true, 64, false, false)));
        assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.CLASSIC_STALE, 1,
                new CaseGuard.Observation(true, false, true, 64, false, false)));
        assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.CLASSIC_STALE, 1,
                new CaseGuard.Observation(false, true, true, 64, false, false)));
        for (var which : new IntentCase[] {IntentCase.FLIGHT_LATE, IntentCase.CLASSIC_LATE}) {
            assertThrows(IllegalStateException.class, () -> CaseGuard.check(which, 1,
                    new CaseGuard.Observation(false, false, true, 64, false, false)));
        }
        assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.FLIGHT_NON_OWNER, 1,
                new CaseGuard.Observation(true, false, true, 64, false, true)));
        assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.CLASSIC_NON_OWNER, 4,
                new CaseGuard.Observation(true, false, true, 64, false, false)));
        assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.MOTION_SPECTATOR, 1,
                new CaseGuard.Observation(false, true, true, 64, false, false)));
    }

    @Test void badCoordinatesAndMissingLiveRangeTargetAreLocalRefusals() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.FLIGHT_RANGE, 1,
                    new CaseGuard.Observation(false, true, true, value, false, false)));
        }
        assertThrows(IllegalStateException.class, () -> CaseGuard.check(IntentCase.FLIGHT_RANGE, 1,
                new CaseGuard.Observation(false, true, false, 65, false, false)));
    }
}
