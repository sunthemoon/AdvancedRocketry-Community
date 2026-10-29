package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** R168-R2: a release flag and an earlier registry reason cannot erase observed physical authority holds. */
final class BayReleasedHoldTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(strings = {"UNEXPECTED_COPY", "PROTECTED_DUPLICATE", "DUPLICATE_LIMIT", "BAY_SOURCE_PROTECTED",
            "SOURCE_AUTHORITY_CONFLICT", "TRANSFER_AUTHORITY_CONFLICT", "ESCROW_AUTHORITY_CONFLICT",
            "SOURCE_REBUILD_REFUSED", "CONSUMPTION_MISMATCH", "DUPLICATE_PASSENGERS", "BAY_CONFLICT"})
    void physicalRepairIsNotReleaseAuthorityEvenWithTheFlag(String reason) {
        var record = record(1);
        var receipt = intent(record, 0).withPhase(BayReceipt.Phase.REPAIR, reason, 1);
        assertTrue(BayReleasedAuthority.capture(record, List.of(receipt), true).isEmpty());
        assertTrue(BayReleasedAuthority.capture(record, List.of(receipt.withReleased()), true).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNEXPECTED_COPY", "PROTECTED_DUPLICATE", "DUPLICATE_LIMIT", "BAY_SOURCE_PROTECTED",
            "SOURCE_AUTHORITY_CONFLICT", "TRANSFER_AUTHORITY_CONFLICT", "ESCROW_AUTHORITY_CONFLICT",
            "SOURCE_REBUILD_REFUSED", "CONSUMPTION_MISMATCH", "DUPLICATE_PASSENGERS", "BAY_CONFLICT"})
    void earlyPhysicalHoldStopsBeforeAnyReceiptJournalOrWorldAccess(String reason) {
        for (boolean released : new boolean[] {false, true}) {
            var record = record(1);
            var receipt = intent(record, 0).withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 1);
            var claim = BayRestartRows.claims(List.of(released ? receipt.withReleased() : receipt)).get(0);
            claim.repair("REGISTRY_BLOCKED");
            claim.repair(reason);
            claim.repair("REGISTRY_SIGNATURE_CONFLICT");
            var flight = UnstartedServer.allocate(BayReleasedFlight.class);
            UnstartedServer.set(BayReleasedFlight.class, flight, "states", new HashMap<>());
            // Dependencies deliberately absent: a latched physical conflict has no recovery effects to perform.
            assertEquals(BayStepQueue.Result.IDLE, assertDoesNotThrow(() -> flight.step(claim)));
            assertFalse(flight.delegated(record));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNEXPECTED_COPY", "ESCROW_AUTHORITY_CONFLICT"})
    void aLaterConflictRevokesAnExistingDelegationBeforeTheNextQueuedStep(String reason) throws Exception {
        var record = record(1);
        var receipts = List.of(intent(record, 0).withReleased());
        var claim = BayRestartRows.claims(receipts).get(0);
        var stateType = Class.forName(BayReleasedFlight.class.getName() + "$State");
        var state = UnstartedServer.allocate(stateType);
        UnstartedServer.set(stateType, state, "claim", claim);
        UnstartedServer.set(stateType, state, "authority", BayReleasedAuthority.capture(record, receipts, false).orElseThrow());
        UnstartedServer.set(stateType, state, "delegated", true);
        var flight = UnstartedServer.allocate(BayReleasedFlight.class);
        var states = new HashMap<UUID, Object>(); states.put(record.logicalRocketId(), state);
        UnstartedServer.set(BayReleasedFlight.class, flight, "states", states);
        assertTrue(flight.delegated(record));
        claim.repair("REGISTRY_BLOCKED");
        assertTrue(flight.delegated(record));
        claim.repair(reason);
        assertFalse(flight.delegated(record));
        assertFalse(flight.active(record.logicalRocketId()));
        assertEquals(reason, claim.releaseRefusal().orElseThrow());
        assertEquals(BayStepQueue.Result.IDLE, flight.step(claim));
    }

    @Test void recoveryHelpersCheckTheParentHoldBeforeReadingOrMutatingAnything() {
        assertEquals(BayStepQueue.Result.IDLE, UnstartedServer.allocate(BaySourceResume.class).resume(null, () -> false));
        assertEquals(BayStepQueue.Result.IDLE, UnstartedServer.allocate(BayConsumptionSteps.class).consume(null, () -> false));
        assertEquals(BayStepQueue.Result.IDLE, UnstartedServer.allocate(BayCopySteps.class).normalize(null, null, () -> false));
        assertFalse(UnstartedServer.allocate(BayPassengerRouting.class).emptyCopy(null, null, null, null, () -> false));
        var resume = UnstartedServer.allocate(BayReleasedResume.class);
        assertEquals("SOURCE_AUTHORITY_CONFLICT", resume.resume(null, true, List.of(), () -> false));
        assertFalse(resume.move(null, null, null, () -> false));
    }
}
