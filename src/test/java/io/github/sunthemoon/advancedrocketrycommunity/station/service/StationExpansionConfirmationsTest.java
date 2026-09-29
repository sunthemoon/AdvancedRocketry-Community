package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationExpansionConfirmations.Status;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class StationExpansionConfirmationsTest {
    private final Object authority = new Object();
    private final UUID actor = UUID.randomUUID();
    private final StationState station = station(0);

    @Test
    void confirmationIsOneShotAndReturnsTheObservedState() {
        var confirmations = new StationExpansionConfirmations();
        assertTrue(confirmations.issue(actor, station, authority, 10));
        var outcome = confirmations.take(actor, station.stationId(), authority, 10);
        assertEquals(Status.READY, outcome.status());
        assertSame(station, outcome.observed());
        assertEquals(Status.MISSING, confirmations.take(actor, station.stationId(), authority, 10).status());
        assertEquals(0, confirmations.size());
    }

    @Test
    void confirmationExpiresAfterTwoHundredTicks() {
        var confirmations = new StationExpansionConfirmations();
        confirmations.issue(actor, station, authority, 100);
        assertEquals(Status.READY, confirmations.take(actor, station.stationId(), authority,
                100 + StationLimits.EXPANSION_CONFIRMATION_TICKS).status());
        confirmations.issue(actor, station, authority, 100);
        assertEquals(Status.EXPIRED, confirmations.take(actor, station.stationId(), authority,
                101 + StationLimits.EXPANSION_CONFIRMATION_TICKS).status());
        confirmations.issue(actor, station, authority, 100);
        assertEquals(Status.EXPIRED, confirmations.take(actor, station.stationId(), authority, 99).status());
    }

    @Test
    void confirmationCannotTransferToAnotherPlayerStationOrAuthority() {
        var confirmations = new StationExpansionConfirmations();
        confirmations.issue(actor, station, authority, 0);
        assertEquals(Status.MISSING, confirmations.take(UUID.randomUUID(), station.stationId(), authority, 0).status());
        assertEquals(Status.MISMATCH, confirmations.take(actor, UUID.randomUUID(), authority, 0).status());
        confirmations.issue(actor, station, authority, 0);
        assertEquals(Status.MISMATCH, confirmations.take(actor, station.stationId(), new Object(), 0).status());
        // A mismatch still consumes the confirmation.
        assertEquals(Status.MISSING, confirmations.take(actor, station.stationId(), authority, 0).status());
    }

    @Test
    void eachPlayerHoldsOnlyTheLatestConfirmation() {
        var confirmations = new StationExpansionConfirmations();
        StationState other = station(1);
        confirmations.issue(actor, station, authority, 0);
        confirmations.issue(actor, other, authority, 1);
        assertEquals(1, confirmations.size());
        assertEquals(Status.MISMATCH, confirmations.take(actor, station.stationId(), authority, 2).status());
        confirmations.issue(actor, station, authority, 3);
        confirmations.issue(actor, other, authority, 4);
        assertSame(other, confirmations.take(actor, other.stationId(), authority, 5).observed());
    }

    @Test
    void capacityRejectsNewPlayersUntilEntriesExpireOrClear() {
        var confirmations = new StationExpansionConfirmations();
        UUID first = UUID.randomUUID();
        confirmations.issue(first, station, authority, 0);
        for (int index = 1; index < StationLimits.MAX_PENDING_EXPANSIONS; index++) {
            assertTrue(confirmations.issue(UUID.randomUUID(), station, authority, 50));
        }
        assertEquals(StationLimits.MAX_PENDING_EXPANSIONS, confirmations.size());
        assertFalse(confirmations.issue(UUID.randomUUID(), station, authority, 60));
        assertTrue(confirmations.issue(first, station, authority, 60), "Existing holder may replace at capacity");
        assertEquals(StationLimits.MAX_PENDING_EXPANSIONS, confirmations.size());

        // Entries issued at tick 50 expire after tick 250, freeing capacity with bounded purging.
        assertTrue(confirmations.issue(UUID.randomUUID(), station, authority, 251));
        assertEquals(2, confirmations.size());
        confirmations.clear(first);
        assertEquals(1, confirmations.size());
        confirmations.clear();
        assertEquals(0, confirmations.size());
    }

    private static StationState station(int cellX) {
        return StationState.fromReservation(new StationReservation(UUID.randomUUID(), UUID.randomUUID(),
                "Expansion " + cellX, new StationGridCell(cellX, 0), ModIdentity.id("earth"), 0));
    }
}
