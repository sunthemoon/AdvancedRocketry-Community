package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowReconciliationActionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactAcknowledgedNextLinkProducesOnlyItsFrozenDeltaAndCannotReplay() {
        var f = EscrowReconciliationBindingBoundaryTest.sample();
        var permit = EscrowReconciliationAction.issue(f.ledger(), f.a(), f.before(), true, () -> true);
        assertEquals(f.first(), permit.consume(f::before));
        assertThrows(IllegalArgumentException.class, () -> permit.consume(f::before));
        assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationAction.issue(f.ledger(), f.b(), f.before(), true, () -> true));
        assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationAction.issue(f.ledger(), f.a(), f.first(), true, () -> true));
    }

    @Test void missingL1RepairAndLostJournalLeaseCannotMintOrUseAPermit() {
        var f = EscrowReconciliationBindingBoundaryTest.sample();
        assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationAction.issue(EscrowLedger.empty(f.ledger().id()),
                f.a(), f.before(), true, () -> true));
        var repair = f.a().withReason(EscrowReason.OTHER_STATE);
        var root = f.ledger().replace(List.of(repair, f.b()), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationAction.issue(root, repair, f.before(), true, () -> true));
        var live = new AtomicBoolean(true);
        var permit = EscrowReconciliationAction.issue(f.ledger(), f.a(), f.before(), true, live::get);
        live.set(false);
        assertThrows(IllegalArgumentException.class, () -> permit.consume(f::before));
        live.set(true);
        assertThrows(IllegalArgumentException.class, () -> permit.consume(f::before));
    }

    @Test void actionRechecksTheCompleteCaptureIncludingNonResourceFields() {
        var f = EscrowReconciliationBindingBoundaryTest.sample();
        var permit = EscrowReconciliationAction.issue(f.ledger(), f.a(), f.before(), true, () -> true);
        var changed = f.before().copy(); changed.putFloat("yaw", 80);
        assertThrows(IllegalArgumentException.class, () -> permit.consume(() -> changed));
        // A fresh action may capture an ordinary frame change, without changing persisted L1 envelopes.
        var fresh = EscrowReconciliationAction.issue(f.ledger(), f.a(), changed, true, () -> true);
        var after = fresh.consume(() -> changed);
        assertEquals(80, after.getFloat("yaw"));
        assertEquals(f.first().getCompound("flight"), after.getCompound("flight"));
        assertEquals(f.first().getCompound("wrapper"), after.getCompound("wrapper"));
    }

    @Test void permitsRejectAnotherThread() {
        var f = EscrowReconciliationBindingBoundaryTest.sample();
        var permit = EscrowReconciliationAction.issue(f.ledger(), f.a(), f.before(), true, () -> true);
        CompletableFuture.runAsync(() -> assertThrows(IllegalArgumentException.class, () -> permit.consume(f::before))).join();
        assertEquals(f.first(), permit.consume(f::before));
    }

    @Test void nativeAdmissionRefusesAnEarlierSourceMarkerUntilValidatedPruningRemovesIt() {
        var f = EscrowReconciliationBindingBoundaryTest.sample();
        var marker = new EscrowPrefix.Link(f.a(), f.a().source(), "SOURCE").marker();
        var state = new EscrowPrefix.State(f.ledger().id(), 1, f.a().source().afterSignature(), List.of(marker));
        assertThrows(IllegalArgumentException.class, () -> EscrowCoordinator.requireNativeSourceAvailable(state));
        assertTrue(EscrowReconciliationPruning.retired(f.ledger(), f.a().source(), state, java.util.Set.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> EscrowCoordinator.requireNativeSourceAvailable(state));
        var retired = EscrowReconciliationPruning.retired(f.ledger().retire(java.util.Set.of(f.a().id())),
                f.a().source(), state, java.util.Set.of());
        var markers = EscrowSettlementPruning.prune(state.markers(), retired, java.util.Set.of());
        assertDoesNotThrow(() -> EscrowCoordinator.requireNativeSourceAvailable(
                new EscrowPrefix.State(state.ledger(), state.epoch(), state.signature(), markers)));
        assertEquals(List.of(marker), state.markers(), "Admission refusal must not remove the source marker");
    }

    @Test void exactNamedFloorRetainsIdentityTypedInvariantEpochAndSignatureBounds() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var endpoint = f.a().destination();
        var state = new EscrowPrefix.State(f.ledger().id(), 1, endpoint.afterSignature(), List.of());
        var floor = new net.minecraft.nbt.CompoundTag(); floor.putInt("schema_version", 2);
        floor.putUUID("entity_id", endpoint.identity()); floor.putString("signature_kind", EscrowSignatureKind.FUEL_VECTOR_V1.name());
        floor.putString("invariant_digest", f.a().nativeBinding().invariant()); floor.putLong("resource_epoch", 1);
        floor.putString("signature", state.signature());
        assertTrue(EscrowReconciliationValidation.floorMatches(floor, endpoint, state, f.a().nativeBinding().invariant()));
        floor.putLong("resource_epoch", 2);
        assertFalse(EscrowReconciliationValidation.floorMatches(floor, endpoint, state, f.a().nativeBinding().invariant()));
        floor.putLong("resource_epoch", 1); floor.putString("signature", f.b().destination().afterSignature());
        assertFalse(EscrowReconciliationValidation.floorMatches(floor, endpoint, state, f.a().nativeBinding().invariant()));
        floor.putString("signature", state.signature());
        for (var field : List.of("signature_kind", "invariant_digest")) {
            var changed = floor.copy(); changed.putString(field, "different");
            assertFalse(EscrowReconciliationValidation.floorMatches(changed, endpoint, state, f.a().nativeBinding().invariant()));
        }
        floor.putUUID("entity_id", java.util.UUID.randomUUID());
        assertFalse(EscrowReconciliationValidation.floorMatches(floor, endpoint, state, f.a().nativeBinding().invariant()));
    }
}
