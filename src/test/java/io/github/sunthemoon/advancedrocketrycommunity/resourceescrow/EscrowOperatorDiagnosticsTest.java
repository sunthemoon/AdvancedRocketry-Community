package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowOperatorDiagnosticsTest {
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
}
