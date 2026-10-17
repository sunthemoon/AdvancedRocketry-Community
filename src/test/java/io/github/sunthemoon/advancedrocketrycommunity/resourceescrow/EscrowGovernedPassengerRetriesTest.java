package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedPassengerRetriesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void eachFailedPassengerRouteUsesTheCommonProgressionWithoutPerTickLogging() {
        for (var reason : List.of("PASSENGER_INELIGIBLE", "PASSENGER_RIDING_OTHER", "PASSENGER_MOVE_REFUSED")) {
            var events = new ArrayList<EscrowSuccessorRetry.Hold>(); var phases = new EscrowGovernedPhases(events::add);
            var record = GovernedTransferFixture.create().record(); var logical = record.logicalRocketId();
            long now = 0;
            for (int delay : new int[] {100, 400, 1_200, 1_200}) {
                assertTrue(phases.passengerAttemptDue(logical, now));
                assertEquals(EscrowGovernedPhases.Result.HELD, phases.passengerFailed(record, reason, now));
                for (long tick = now; tick < now + delay; tick++) { assertFalse(phases.passengerAttemptDue(logical, tick)); }
                now += delay;
            }
            assertEquals(List.of(100, 400, 1_200), events.stream().map(EscrowSuccessorRetry.Hold::backoff).toList());
            assertTrue(events.stream().allMatch(event -> event.reason().equals(reason) && event.phase().equals("F5_PASSENGER")));
            phases.clear(); assertTrue(phases.passengerAttemptDue(logical, 0));
        }
    }

    @Test void phaseReplacementDoesNotResetTheLogicalPassengerHistoryAndHealthyPollingIsSilent() {
        var events = new ArrayList<EscrowSuccessorRetry.Hold>(); var phases = new EscrowGovernedPhases(events::add);
        var record = GovernedTransferFixture.create().record();
        for (long tick = 0; tick < 2_000; tick++) { assertTrue(phases.passengerAttemptDue(record.logicalRocketId(), tick)); }
        assertTrue(events.isEmpty());
        phases.passengerFailed(record, "PASSENGER_INELIGIBLE", 0);
        var replaced = record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination());
        assertFalse(phases.passengerAttemptDue(replaced.logicalRocketId(), 99));
        phases.passengerFailed(replaced, "PASSENGER_RIDING_OTHER", 100);
        assertFalse(phases.passengerAttemptDue(record.logicalRocketId(), 499));
        assertTrue(phases.passengerAttemptDue(record.logicalRocketId(), 500));
        assertEquals(List.of(100, 400), events.stream().map(EscrowSuccessorRetry.Hold::backoff).toList());
    }
}
