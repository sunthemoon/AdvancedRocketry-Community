package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class EscrowGovernedTerminalPassengersTest {
    @Test void unresolvedPollingDoesNotCaptureAndReconnectRepeatsTheFullCheck() {
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var manifest = RocketPassengerManifest.empty(2).assign(first).orElseThrow().assign(second).orElseThrow();
        var online = new HashSet<UUID>(); online.add(first);
        var captures = new AtomicInteger(); var valid = new AtomicBoolean(false);
        java.util.function.BooleanSupplier observe = () -> EscrowGovernedTerminalPassengers.afterPassengers(
                () -> EscrowGovernedTerminalPassengers.resolved(manifest, online::contains),
                () -> { captures.incrementAndGet(); return valid.get(); });
        for (int tick = 0; tick < 2_000; tick++) { assertFalse(observe.getAsBoolean()); }
        assertEquals(0, captures.get(), "Offline polling rebuilt the expensive survivor state");
        online.add(second);
        assertFalse(observe.getAsBoolean(), "Reconnection is not a survivor proof");
        valid.set(true); assertTrue(observe.getAsBoolean());
        assertEquals(2, captures.get(), "Ready passengers skipped final validation");
        online.remove(first);
        for (int tick = 0; tick < 2_000; tick++) { assertFalse(observe.getAsBoolean()); }
        assertEquals(2, captures.get(), "A later departure reused or rebuilt a capture");
    }

    @Test void offlineOrUnresolvedAssignmentRetainsTheHandoffWait() {
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var manifest = RocketPassengerManifest.empty(2).assign(first).orElseThrow().assign(second).orElseThrow();
        var resolved = new HashSet<UUID>();
        assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
        resolved.add(first); assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
        resolved.add(second); assertTrue(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
        resolved.remove(first); assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
    }

    @Test void allSixteenAssignmentsAreCheckedAndAnEmptyManifestNeedsNoPlayer() {
        assertTrue(EscrowGovernedTerminalPassengers.resolved(RocketPassengerManifest.empty(0), id -> {
            fail("Empty manifest performed a player lookup"); return false;
        }));
        var manifest = RocketPassengerManifest.empty(16); var passengers = new HashSet<UUID>();
        for (int i = 0; i < 16; i++) { var id = new UUID(30, i); passengers.add(id); manifest = manifest.assign(id).orElseThrow(); }
        var observed = new HashSet<UUID>();
        assertTrue(EscrowGovernedTerminalPassengers.resolved(manifest, id -> { observed.add(id); return true; }));
        assertEquals(passengers, observed);
        for (var missing : passengers) {
            assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, id -> !id.equals(missing)));
        }
    }
}
