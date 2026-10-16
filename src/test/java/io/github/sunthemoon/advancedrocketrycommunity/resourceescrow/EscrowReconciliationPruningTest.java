package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowReconciliationPruningTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyAbsentLeadingOrdinaryMarkersBelowTheWatermarkAreRemoved() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var root = f.ledger().retire(Set.of(f.a().id()));
        var state = state(f.ledger().id(), f.a(), f.b());
        var retired = EscrowReconciliationPruning.retired(root, f.a().destination(), state, Set.of(f.b().id()));
        assertEquals(Set.of(f.a().id()), retired);
        var markers = EscrowSettlementPruning.prune(state.markers(), retired, Set.of(f.b().id()));
        var pruned = new EscrowPrefix.State(root.id(), state.epoch(), state.signature(), markers);
        assertEquals(2, pruned.epoch()); assertEquals(state.signature(), pruned.signature());
        assertEquals(List.of(new EscrowPrefix.Link(f.b(), f.b().destination(), "DESTINATION").marker()), pruned.markers());
        assertEquals(EscrowPrefix.Classification.AFTER, EscrowPrefix.classify(root.id(), EscrowPrefix.chains(root.records()).get(f.b().destination().key()), pruned));
        assertTrue(EscrowReconciliationPruning.retired(root, f.b().destination(), pruned, Set.of(f.b().id())).isEmpty());
        assertEquals(Set.of(f.a().id(), f.b().id()), EscrowReconciliationPruning.retired(root.retire(Set.of(f.b().id())),
                f.a().destination(), state, Set.of()));
    }

    @Test void missingForeignOrBehindLedgerNeverAuthorizesPruning() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var root = f.ledger().retire(Set.of(f.a().id(), f.b().id()));
        var state = state(root.id(), f.a(), f.b());
        var foreign = new EscrowPrefix.State(EscrowTestFixtures.id(999), state.epoch(), state.signature(), state.markers());
        assertEquals(EscrowReason.LEDGER_REPLACED, assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationPruning.retired(root, f.a().destination(), foreign, Set.of())).reason);
        var behind = new EscrowLedger(root.id(), 1, 0, List.of(), List.of(), List.of());
        assertEquals(EscrowReason.LEDGER_BEHIND, assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationPruning.retired(behind, f.a().destination(), state, Set.of())).reason);
    }

    @Test void foreignRolesIdentityAndHolesCannotBeConvertedIntoAValidPrefix() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var root = f.ledger(); var state = state(root.id(), f.a(), f.b());
        assertEquals(EscrowReason.FOREIGN_MARKER, assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationPruning.retired(root, f.a().source(), state, Set.of())).reason);
        var wrongId = new EscrowPrefix.Marker(EscrowTestFixtures.id(999), f.a().sequence(), "DESTINATION", 1);
        var forged = new EscrowPrefix.State(root.id(), 1, f.a().destination().afterSignature(), List.of(wrongId));
        assertEquals(EscrowReason.FOREIGN_MARKER, assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationPruning.retired(root, f.a().destination(), forged, Set.of())).reason);
        var tailRetired = new EscrowLedger(root.id(), root.issuedThrough(), 0, List.of(f.a()), List.of(), List.of());
        assertEquals(EscrowReason.FOREIGN_MARKER, assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationPruning.retired(tailRetired, f.a().destination(), state, Set.of())).reason);
    }

    @Test void unresolvedAndReceiptMembersKeepTheirOwnReleaseAuthority() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var root = f.ledger().retire(Set.of(f.a().id(), f.b().id()));
        var state = new EscrowPrefix.State(root.id(), 1, f.a().destination().afterSignature(),
                List.of(new EscrowPrefix.Link(f.a(), f.a().destination(), "DESTINATION").marker()));
        assertTrue(EscrowReconciliationPruning.retired(root, f.a().destination(), state, Set.of(f.a().id())).isEmpty());
        var receipt = EscrowTestFixtures.receipt(1); var receiptId = receipt.getUUID("op_id");
        var seq = EscrowTestFixtures.operation(10_001).sequence();
        var withReceipt = new EscrowLedger(root.id(), Math.max(seq, root.issuedThrough()), 1, List.of(), List.of(), List.of(receipt));
        var receiptState = new EscrowPrefix.State(root.id(), 1, f.a().destination().afterSignature(),
                List.of(new EscrowPrefix.Marker(receiptId, seq, "DESTINATION", 1)));
        assertTrue(EscrowReconciliationPruning.retired(withReceipt, f.a().destination(), receiptState, Set.of()).isEmpty());
    }

    private static EscrowPrefix.State state(java.util.UUID ledger, EscrowOperation a, EscrowOperation b) {
        return new EscrowPrefix.State(ledger, 2, b.destination().afterSignature(), List.of(
                new EscrowPrefix.Link(a, a.destination(), "DESTINATION").marker(), new EscrowPrefix.Link(b, b.destination(), "DESTINATION").marker()));
    }
}
