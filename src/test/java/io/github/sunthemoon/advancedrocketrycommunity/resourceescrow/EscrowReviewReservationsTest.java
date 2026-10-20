package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowReviewReservationsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void severalNativeEndpointsReserveRetiredMarkersBeforeAcceptingTheirConnectedComponent() {
        var operations = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 4; i++) {
            var model = EscrowSettlementCapacityTest.operation(i, 100 + i, 0, true);
            // The conservative component graph joins a cross-kind identity collision. Each device
            // still has just one operation, and the native candidates have distinct logical IDs.
            var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld",
                    i == 1 ? EscrowTestFixtures.id(900) : operations.get(i - 2).destination().identity(), null,
                    "minecraft:chest", i, 64, 0, EscrowEndpoint.Unit.TANK, 0, EscrowTestFixtures.hash(0), EscrowTestFixtures.hash(1));
            operations.add(EscrowTestFixtures.nativeModel(new EscrowOperation(model.id(), i, model.owner(), 1,
                    model.phase(), model.reason(), model.payload(), model.payloadHash(), source, model.destination(), model.guard())));
        }
        var root = EscrowTestFixtures.ledger(operations.toArray(EscrowOperation[]::new));
        var held = root.replace(operations.stream().map(op -> op.withReason(EscrowReason.OTHER_STATE)).toList(), List.of(), List.of());
        var observed = new ArrayList<EscrowSettlementIntent.Observation>();
        for (var chain : EscrowPrefix.chains(root.records()).values()) {
            var last = chain.get(chain.size() - 1); var endpoint = last.endpoint();
            observed.add(new EscrowSettlementIntent.Observation(endpoint,
                    new EscrowPrefix.State(root.id(), endpoint.afterEpoch(), endpoint.afterSignature(),
                            chain.stream().map(EscrowPrefix.Link::marker).toList()), true, Long.MAX_VALUE));
        }
        var preview = EscrowSettlementIntent.preview(held, operations.get(0).id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "~".repeat(16), 0, observed);
        var unpadded = preview.confirm(preview.actor(), preview.digest(), 0, observed);
        assertDoesNotThrow(unpadded::encode);
        for (var op : held.records()) {
            assertDoesNotThrow(() -> EscrowCodec.operation(op.settle(unpadded)));
            assertDoesNotThrow(() -> EscrowSettlementTombstones.create(op, unpadded));
        }
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root, operations.get(3).id()));
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
    }

    @Test void cursorUsesCoordinatorBusyForFrozenSettlementsAndRevisitsAfterTheyClear() {
        var a = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var root = EscrowTestFixtures.ledger(a); var intent = intent(root);
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
        var b = new EscrowOperation(EscrowTestFixtures.id(2), 2, a.owner(), 1, a.phase(), a.reason(),
                a.payload(), a.payloadHash(), a.source(), a.destination(), a.guard());
        var next = released.append(List.of(b)); var frozen = intent(next).freeze(next);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(frozen));
        try (var writer = new EscrowLedgerWriter(directory.resolve("unused.dat"), 0);
                var coordinator = new EscrowCoordinator(data, writer)) {
            var cursor = new EscrowSettlementPruning.Cursor();
            assertTrue(coordinator.pruningBusy(a.source().key())); assertTrue(coordinator.pruningBusy(a.destination().key()));
            // No server is available: touching either endpoint rather than skipping the busy visit fails.
            for (int i = 0; i < 4; i++) { assertDoesNotThrow(() -> cursor.tick(null, coordinator)); }
            assertEquals(frozen, coordinator.ledger());
            data.acknowledged(released.generation(1), false);
            assertFalse(coordinator.pruningBusy(a.source().key())); assertFalse(coordinator.pruningBusy(a.destination().key()));
            var seen = new java.util.HashSet<EscrowEndpoint.Key>();
            for (int i = 0; i < 2; i++) {
                var visit = cursor.next(coordinator.ledger(), Set.of(), coordinator::pruningBusy);
                assertNotNull(visit); seen.add(visit.endpoint().reference().key());
            }
            assertEquals(Set.of(a.source().key(), a.destination().key()), seen);
        }
    }
    private static EscrowSettlementIntent intent(EscrowLedger root) {
        var observations = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, root.records().get(0).id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 10, observations);
        return preview.confirm(preview.actor(), preview.digest(), 11, observations);
    }
}
