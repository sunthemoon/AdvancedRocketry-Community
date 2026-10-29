package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** R168-R1: the review's unstarted-object probe, with the reconciliation queue obligation. */
final class BayReleasedEffectTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void invalidatedPassengerEffectSuspendsDelegationAndQueuesReconciliation() throws Exception {
        var prepared = record(1);
        var passengers = prepared.destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        var authority = BayReleasedAuthority.capture(prepared, List.of(intent(prepared, 0).withReleased()), false).orElseThrow();
        var stateType = Class.forName(BayReleasedFlight.class.getName() + "$State");
        var state = UnstartedServer.allocate(stateType);
        UnstartedServer.set(stateType, state, "authority", authority);
        UnstartedServer.set(stateType, state, "claim", BayRestartRows.claims(authority.receipts()).get(0));
        UnstartedServer.set(stateType, state, "delegated", true);
        UnstartedServer.set(stateType, state, "proposed", passengers);
        UnstartedServer.set(stateType, state, "oldDigest", "checked-old-record");
        UnstartedServer.set(stateType, state, "proposedObservation", 8L);
        UnstartedServer.set(stateType, state, "observation", 8L);
        var flight = UnstartedServer.allocate(BayReleasedFlight.class);
        var states = new HashMap<UUID, Object>();
        states.put(prepared.logicalRocketId(), state);
        UnstartedServer.set(BayReleasedFlight.class, flight, "states", states);
        var queued = new ArrayList<UUID>();
        UnstartedServer.set(BayReleasedFlight.class, flight, "offer", (Consumer<UUID>) queued::add);
        assertTrue(flight.delegated(passengers));

        flight.changed(prepared.logicalRocketId());
        var ran = new AtomicBoolean();
        // Stale observation must be refused before any journal access.
        flight.effect(null, passengers, RocketTransferBayRecoveryGate.Action.TICK_SOURCE, () -> ran.set(true));
        assertFalse(ran.get(), "An invalidated effect must not execute");
        assertFalse(flight.delegated(passengers), "The checked phase must reconcile before source removal");
        assertEquals(List.of(prepared.logicalRocketId()), queued);
    }
}
