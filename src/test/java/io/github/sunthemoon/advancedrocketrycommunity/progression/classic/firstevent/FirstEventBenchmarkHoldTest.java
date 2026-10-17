package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FirstEventBenchmarkHoldTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap.initialize();
    }
    @Test void repeatedActualVisitsKeepDispositionDeferredForTwentyTicksThenRelease() {
        try (var hold = new FirstEventBenchmarkHold(90)) {
            for (int tick = 100; tick < 120; tick++) {
                hold.deferred(tick); hold.deferred(tick); hold.tick(tick);
                assertFalse(hold.released());
                assertEquals(FirstEventDisposition.DEFER, hold.disposition(FirstEventKind.FIRST_MOON_LANDING, FirstEventDisposition.ALLOW));
            }
            assertEquals(40, hold.deferredVisits()); assertEquals(20, hold.deferredTicks());
            hold.tick(120);
            assertTrue(hold.released());
            hold.deferred(121); assertEquals(40, hold.deferredVisits());
            assertEquals(FirstEventDisposition.ALLOW, hold.disposition(FirstEventKind.FIRST_MOON_LANDING, FirstEventDisposition.ALLOW));
        }
    }

    @Test void closeReleasesOnFailureOrShutdown() {
        var hold = new FirstEventBenchmarkHold(0); hold.close();
        assertTrue(hold.released()); assertEquals(0, hold.deferredVisits());
    }

    @Test void noVisitsNeverSilentlyReleaseAndOtherDecisionsAreUnchanged() {
        try (var hold = new FirstEventBenchmarkHold(0)) {
            assertEquals(FirstEventDisposition.NOT_FIRST, hold.disposition(FirstEventKind.FIRST_MOON_LANDING, FirstEventDisposition.NOT_FIRST));
            assertEquals(FirstEventDisposition.DEFER, hold.disposition(FirstEventKind.FIRST_MOON_LANDING, FirstEventDisposition.DEFER));
            assertEquals(FirstEventDisposition.ALLOW, hold.disposition(FirstEventKind.FIRST_STATION_WARP, FirstEventDisposition.ALLOW));
            hold.tick(119); assertFalse(hold.released());
            assertThrows(IllegalStateException.class, () -> hold.tick(120));
        }
    }

    @Test void benchmarkCannotArmWithoutExplicitReleaseProperties() {
        assertThrows(IllegalStateException.class, () -> FirstEventCutSeams.holdBenchmarkDisposition(null));
    }
}
