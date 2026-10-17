package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowOperatorDiagnosticsTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void inspectShowsOnlyFourMembersAndMetadataWithoutChangingTheRoot() {
        var ops = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 8; i++) { ops.add(EscrowTestFixtures.operation(i, 99, i - 1).withReason(EscrowReason.OTHER_STATE)); }
        var root = EscrowTestFixtures.ledger(ops.toArray(EscrowOperation[]::new)); var before = EscrowCodec.encode(root);
        var lines = EscrowOperatorDiagnostics.inspect(root, ops.get(0).id(), intent -> fail(),
                id -> new EscrowReconciliationScheduler.View("WAITING", "BARRIER_IO", 100, 2, false), "ACKNOWLEDGED");
        assertEquals(5, lines.size()); assertTrue(lines.get(0).contains("members=8 omitted=4"));
        assertTrue(lines.get(1).contains("persisted=REPAIR persisted_reason=OTHER_STATE"));
        assertTrue(lines.get(1).contains("failures=2")); assertTrue(lines.get(1).contains("accounting=UNKNOWN"));
        assertFalse(String.join("", lines).contains("FluidName")); assertFalse(String.join("", lines).contains("minecraft:overworld"));
        assertEquals(before, EscrowCodec.encode(root));
    }
    @Test void settlementO2CountsPhysicalSlotsAndNeverCallsAProofAttempt() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var observations = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 0, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 1, observations);
        var frozen = intent.freeze(root); var before = EscrowCodec.encode(frozen);
        try (var recovery = new EscrowSettlementRecovery()) {
            var lines = EscrowOperatorDiagnostics.inspect(frozen, op.id(), recovery::view, id -> fail(), "UNKNOWN");
            assertEquals(2, lines.size()); assertTrue(lines.get(1).contains("O2_proved_slots=0 O2_waiting_slots=2"));
            assertTrue(lines.get(0).contains("journal=UNKNOWN"));
            assertEquals(EscrowOperatorRetry.Outcome.PENDING, recovery.accelerate(intent, 2));
            assertEquals(before, EscrowCodec.encode(frozen));
        }
        assertTrue(EscrowOperatorDiagnostics.preview(preview).contains("permanent_receipts_consumed=1"));
        assertTrue(EscrowOperatorDiagnostics.preview(preview).contains("zero loss"));
    }
    @Test void releasedMembersShowThePruningCursorsStateWithoutChangingTheRoot() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var observations = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 0, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 1, observations);
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
        var before = EscrowCodec.encode(released); var cursor = new EscrowSettlementPruning.Cursor();
        java.util.function.Supplier<List<String>> inspect = () -> EscrowOperatorDiagnostics.inspect(released, op.id(),
                ignored -> fail(), id -> fail(), "ACKNOWLEDGED", cursor::summary);
        var lines = inspect.get();
        assertEquals(2, lines.size()); assertTrue(lines.get(0).contains("pruning=UNVISITED"));
        assertTrue(lines.get(1).contains("O2=not-retained pruning=UNVISITED"), lines.get(1));
        var first = cursor.next(released, java.util.Set.of()); var second = cursor.next(released, java.util.Set.of());
        cursor.observe(first, EscrowSettlementPruning.State.WAITING_EARLIER_GROUP); cursor.observe(second, EscrowSettlementPruning.State.DONE);
        assertTrue(inspect.get().stream().allMatch(line -> line.contains("pruning=WAITING_EARLIER_GROUP")));
        cursor.observe(first, EscrowSettlementPruning.State.refused("frontier: live state differs"));
        lines = inspect.get();
        assertTrue(lines.stream().allMatch(line -> line.contains("pruning=REFUSED(frontier: live state differs)") && line.length() < 1200));
        cursor.observe(first, EscrowSettlementPruning.State.DONE);
        assertTrue(inspect.get().stream().allMatch(line -> line.contains("pruning=DONE")));
        assertTrue(EscrowOperatorDiagnostics.inspect(released, op.id(), ignored -> fail(), id -> fail(), "ACKNOWLEDGED")
                .stream().allMatch(line -> line.contains("pruning=UNVISITED")));
        assertEquals(before, EscrowCodec.encode(released));
    }
    @Test void statusBoundsReasonsAndIncludesPermanentAndReservedMemberCounts() {
        var ops = new ArrayList<EscrowOperation>(); for (int i = 1; i <= 64; i++) { ops.add(EscrowTestFixtures.operation(i)); }
        var root = EscrowTestFixtures.ledger(ops.toArray(EscrowOperation[]::new));
        var lines = EscrowOperatorDiagnostics.status(root, op -> "reason_" + op.sequence() + "x".repeat(200));
        assertEquals(3, lines.size()); assertTrue(lines.get(0).contains("held=64"));
        assertTrue(lines.get(2).contains("other_reason_kinds=60"));
        assertTrue(lines.stream().allMatch(line -> line.length() < 1200));
        assertTrue(lines.get(1).contains("accept_current_members=0 abandon_members=0 floor_members=0"));
        assertTrue(lines.get(2).contains("all choices refuse"));
    }
    @Test void readOnlyStatusRetainsQueuedFloorReservationsWithoutOpeningOrStartingAWriter() {
        var data = ResourceEscrowSavedData.create();
        assertEquals(new EscrowFloors.Usage(0, 0), data.floorUsage());
        var file = temp.resolve("ledger.dat");
        try (var writer = new EscrowLedgerWriter(file, 0); var coordinator = new EscrowCoordinator(data, writer)) {
            data.attach(coordinator, () -> true);
            var op = EscrowTestFixtures.operation(1);
            var guarded = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                    op.payload(), op.payloadHash(), op.source(), op.destination(),
                    new EscrowGuard(op.guard().rocket(), EscrowTestFixtures.id(900), 1, EscrowTestFixtures.hash(900)));
            coordinator.prepareTest(guarded, ignored -> fail("Diagnostics must not submit a write"));
            var before = EscrowCodec.encode(data.ledger());
            var lines = EscrowOperatorDiagnostics.status(data.ledger(), value -> value.reason().name(), data.floorUsage());
            assertTrue(lines.get(1).contains("floors_used=0 floors_reserved=1/"));
            assertTrue(data.ledger().records().isEmpty()); assertEquals(before, EscrowCodec.encode(data.ledger()));
            assertTrue(writer.idle()); assertFalse(java.nio.file.Files.exists(file));
        }
    }
}
