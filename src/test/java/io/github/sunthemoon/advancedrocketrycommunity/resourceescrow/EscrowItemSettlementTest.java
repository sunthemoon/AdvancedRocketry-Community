package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemSettlementTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void acceptAndEitherAbandonSurvivorDeriveO4FromFrozenFuelObservationsWithoutLiveInputs() {
        for (var kind : KINDS) {
            for (int missing = 0; missing <= 2; missing++) {
                var op = operation(kind, 1, 99).withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
                var root = ledger(op); var intent = intent(root, op, 0, missing, false);
                var frozen = intent.freeze(root);
                var installed = EscrowSettlementTombstones.install(frozen, intent);
                // O3 survives replacement without callback: only the encoded root and O1 are inputs to O4.
                var durable = EscrowCodec.decode(EscrowCodec.encode(installed));
                var choice = EscrowSettlementIntent.decode(durable.records().get(0).settlement());
                var released = EscrowSettlementTombstones.release(durable, choice, 7).generation(7);
                assertEquals(released, EscrowCodec.decode(EscrowCodec.encode(released)));
                assertTrue(released.records().isEmpty());
                var receipt = released.tombstones().get(0);
                assertEquals("UNKNOWN", receipt.getString("accounting_status")); assertEquals(0, receipt.getInt("payload_amount"));
                assertEquals(op.payloadHash(), receipt.getString("payload_hash")); assertFalse(receipt.contains("payload_kind"));
                assertEquals(receipt, EscrowReceiptCodec.decode(EscrowReceiptCodec.encode(receipt)));
                assertEquals(missing == 0 ? 2 : 1, receipt.getList("survivors", 10).size());
                var deviceObservation = choice.observations().stream().filter(value -> value.endpoint().equals(op.device())).findFirst().orElseThrow();
                assertEquals(0, deviceObservation.quantity());
                assertEquals(missing == 2, deviceObservation.missing());
                assertEquals(EscrowSignatureKind.ITEM_BUFFER_V1, deviceObservation.signatureKind());
                if (missing != 2) {
                    assertEquals(buffer(63).signature(op.device().identity()), deviceObservation.state().signature());
                    assertNotEquals(new EscrowItemBuffer(java.util.Collections.nCopies(4, new net.minecraft.nbt.CompoundTag()))
                            .signature(op.device().identity()), deviceObservation.state().signature());
                    var survivor = receipt.getList("survivors", 10).stream().map(raw -> (net.minecraft.nbt.CompoundTag) raw)
                            .filter(raw -> raw.getCompound("endpoint").getUUID("identity").equals(op.device().identity())).findFirst().orElseThrow();
                    assertEquals(0, survivor.getLong("quantity")); assertEquals("ITEM_BUFFER_V1", survivor.getString("signature_kind"));
                    assertEquals(deviceObservation.state().signature(), survivor.getString("signature"));
                }
                assertEquals(missing == 1 ? 0 : 1, released.rocketAuthorities().size());
                if (missing == 1) {
                    assertTrue(EscrowSettlementTombstones.identityLocked(released, op.guard().rocket(), op.rocket().identity()));
                    continue;
                }
                var observation = choice.checkpointed().stream().filter(value -> value.endpoint().equals(op.rocket())).findFirst().orElseThrow();
                var head = released.rocketAuthorities().get(0);
                assertEquals(op.rocket().logicalRocket(), head.logical()); assertEquals(op.rocket().identity(), head.physical());
                assertEquals(op.owner(), head.owner()); assertEquals(op.id(), head.anchor()); assertEquals(7, head.releaseGeneration());
                assertEquals(observation.state().epoch(), head.epoch()); assertEquals(0, head.issued()); assertEquals(0, head.completed());
                assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, head.kind());
                assertEquals(hash(777), head.signature()); assertNotEquals(op.rocket().afterSignature(), head.signature());
                assertEquals(observation.invariantDigest(), head.invariant()); assertEquals(EscrowFlightTables.Outcome.READY, head.outcome());
                assertNotEquals(op.nativeBinding().invariant(), head.invariant(), "O4 keeps O1's chosen OTHER content, not the operation's after snapshot");
                // Pruning uses released group authority, not a new cargo projection or an after-snapshot lookup.
                var groups = EscrowSettlementPruning.groups(released); assertEquals(1, groups.size());
                var cursor = new EscrowSettlementPruning.Cursor();
                cursor.enqueue(released, new EscrowCoordinator.SettlementAcknowledgement(released.id(), choice.digest(), Set.of(op.id()), 7, true));
                assertNotNull(cursor.next(released, Set.of()));
                var markers = observation.state().markers();
                assertEquals(markers, EscrowSettlementPruning.prune(markers, Set.of(op.id()), Set.of(op.id())));
                var later = new EscrowPrefix.Marker(id(2), 2, "DESTINATION", 2);
                var withLater = new java.util.ArrayList<>(markers); withLater.add(later);
                assertEquals(List.of(later), EscrowSettlementPruning.prune(withLater, Set.of(op.id()), Set.of(later.operation())));
                var pruned = new RocketEscrowSection(released.id(), head.epoch(), EscrowSettlementPruning.prune(markers, Set.of(op.id()), Set.of()));
                var launch = new EscrowFlightAuthority.Launch(head.logical(), head.owner(), head.physical(), pruned, head.signature(), head.invariant(),
                        id(400), id(401), hash(4), hash(5), hash(6), hash(7), new EscrowGuard(head.logical(), null, 1, hash(8)), Set.of());
                var armed = EscrowFlightAuthority.arm(released, launch, 8);
                assertEquals(1, armed.armedFlights().size()); assertEquals(head.anchor(), armed.armedFlights().get(0).anchor());
                var following = operation(kind, 2, 99);
                assertEquals(java.util.Optional.of(EscrowCoordinator.Transfer.RECEIPT_BACKED), EscrowTransferAdmission.refusal(released, List.of(),
                        new EscrowNativeBinding.Admission(following, wrapper(0))));
            }
        }
    }

    @Test void neutralNormalizationKeepsTheFrozenFuelProjectionAndDoesNotTreatItAsComposite() {
        for (var kind : KINDS) {
            var op = operation(kind, 1, 99).withReason(EscrowReason.OTHER_STATE); var root = ledger(op);
            var intent = intent(root, op, 0, 0, true);
            var observed = intent.observations().stream().filter(value -> value.endpoint().equals(op.rocket())).findFirst().orElseThrow();
            assertFalse(observed.bound()); assertTrue(observed.checkpointed().bound());
            assertEquals(new RocketEscrowSection(root.id(), 0, List.of()), observed.normalizedWrapper());
            assertEquals(observed.state(), observed.checkpointed().state());
            assertEquals(observed.quantity(), observed.checkpointed().quantity());
            assertEquals(observed.invariantDigest(), observed.checkpointed().invariantDigest());
            assertEquals(intent, EscrowSettlementIntent.decode(intent.encode()));
            assertThrows(IllegalArgumentException.class, () -> new EscrowSettlementIntent.Observation(op.rocket(), observed.state(), false,
                    observed.quantity(), EscrowSignatureKind.ROCKET_COMPOSITE_V1, observed.invariantDigest()));
            var stale = EscrowSettlementIntent.encodeObservation(observed); stale.getCompound("normalized_wrapper").putLong("resource_epoch", 1);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decodeObservation(stale));
            var o4 = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
            assertEquals(0, o4.rocketAuthorities().get(0).epoch());
            assertEquals(observed.state().signature(), o4.rocketAuthorities().get(0).signature());
        }
    }

    @Test void aZeroDiagnosticCannotProveAbsenceAndTypedBufferChangesInvalidateConfirmation() {
        for (var kind : KINDS) {
            var op = operation(kind, 1, 99).withReason(EscrowReason.OTHER_STATE); var root = ledger(op);
            var observed = observations(root, op, 0, 0, false);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.preview(root, op.id(),
                    EscrowSettlementIntent.Choice.ABANDON, id(700), "operator", 10, observed));
            var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "operator", 10, observed);
            var changed = observed.stream().map(value -> value.endpoint().equals(op.device())
                    ? new EscrowSettlementIntent.Observation(value.endpoint(), new EscrowPrefix.State(root.id(), value.state().epoch(),
                            buffer(62).signature(op.device().identity()), value.state().markers()), true, 0, value.signatureKind(), value.invariantDigest()) : value).toList();
            assertThrows(IllegalArgumentException.class, () -> preview.confirm(preview.actor(), preview.digest(), 11, changed));
            var device = observed.stream().filter(value -> value.endpoint().equals(op.device())).findFirst().orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> new EscrowSettlementIntent.Observation(device.endpoint(), device.state(), true, 1));
        }
    }
}
