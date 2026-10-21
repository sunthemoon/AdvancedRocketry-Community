package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketFillReconciliationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyCommittedStationaryNonliveRecordsWithTheThreeBoundaryFencesQualify() {
        var prepared = RocketStationMotionRuleTest.record(new RocketPosition(80, 80, 80));
        var committed = prepared.destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        assertTrue(RocketFillReconciliation.guardedRecordAllowed(committed, false, true, null));
        for (var reason : List.of("ESCROW_PENDING_OR_TOMBSTONE", "ENDPOINT_JOIN_PROOF_REQUIRED", "ENDPOINT_PROOF_REQUIRED")) {
            assertTrue(RocketFillReconciliation.guardedRecordAllowed(committed, false, true, reason));
            assertFalse(RocketFillReconciliation.guardedRecordAllowed(committed, true, true, reason));
            assertFalse(RocketFillReconciliation.guardedRecordAllowed(committed, false, false, reason));
            assertFalse(RocketFillReconciliation.guardedRecordAllowed(prepared, false, true, reason));
        }
        for (var reason : List.of("FLOOR_CONFLICT", "FLOOR_PROOF_REQUIRED", "WAITING_UNLOADED", "INVALID_ENDPOINT", "MATCH_LIMIT", "BAY_HELD")) {
            assertFalse(RocketFillReconciliation.guardedRecordAllowed(committed, false, true, reason), reason);
        }
        assertFalse(RocketTransferService.reconciliationRecordAllowed(committed, false, false, true, false),
                "The old boundary stays strict");
        assertFalse(RocketFillReconciliation.guardedRecordAllowed(
                io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture.create().record(), false, true, null));
    }

    @Test void noRevokeOnlyPreservesExistingSettlementAndNeverRemovesAJoinOrConflictFence() {
        var fences = new RocketTransferFences(); var id = UUID.randomUUID();
        var settled = new HashSet<>(Set.of(id)); var live = new HashSet<UUID>();
        for (int tick = 0; tick < 1_200; tick++) {
            assertTrue(fences.keepSettlement(id, live, settled, () -> true));
            assertEquals(Set.of(id), settled); assertTrue(live.isEmpty()); assertTrue(fences.ids().isEmpty());
            assertFalse(fences.keepSettlement(id, live, settled, () -> false), "Foreign/orphan work cannot preserve settlement");
        }
        live.add(id); assertFalse(fences.keepSettlement(id, live, settled, () -> true)); live.clear();
        settled.clear(); assertFalse(fences.keepSettlement(id, live, settled, () -> true));
        for (var reason : List.of("ENDPOINT_JOIN_PROOF_REQUIRED", "FLOOR_CONFLICT")) {
            fences.reevaluate(id, reason.equals("FLOOR_CONFLICT") ? 1_200 : 0, () -> reason, live, settled);
            settled.add(id);
            assertFalse(fences.keepSettlement(id, live, settled, () -> true));
            assertEquals(reason, fences.reason(id));
        }
    }
}
