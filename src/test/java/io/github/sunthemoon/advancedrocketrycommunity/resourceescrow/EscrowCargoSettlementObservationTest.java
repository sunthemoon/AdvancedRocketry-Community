package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** C-E3 item 1: the device's own O1 projection beside the rocket's unchanged fuel-vector settlement projection. */
class EscrowCargoSettlementObservationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static EscrowSettlementIntent.Observation rocket(EscrowCargoReconciliationFixture f, CompoundTag envelope) {
        return EscrowSettlementDisk.observe(EscrowReconciliationCheckpointTest.rocketSlot(f.op().rocket(), envelope), f.op().rocket(), f.ledger().id());
    }
    static EscrowSettlementIntent.Observation device(EscrowCargoReconciliationFixture f, CompoundTag envelope) {
        return EscrowSettlementDisk.observe(EscrowFluidCargoEndpointTest.slot(envelope), f.op().device(), f.ledger().id());
    }

    @Test void deviceObservesItsNativeTankAndTheRocketKeepsTheFuelVectorAndInvariant() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowCargoReconciliationFixture.create(load); var op = f.op();
            for (boolean after : List.of(false, true)) {
                var envelope = after ? f.deviceAfter() : f.deviceBefore(); var root = EscrowFluidCargoEndpoint.root(envelope);
                var device = device(f, envelope);
                assertEquals(EscrowSignatureKind.FLUID_TANK_V1, device.signatureKind());
                assertEquals(root.signature(), device.state().signature());
                assertEquals(after ? op.device().afterSignature() : op.device().beforeSignature(), device.state().signature());
                assertEquals(root.tank().amount(), device.quantity());
                assertEquals(after ? (load ? 4_000 : 1_000) : (load ? 5_000 : 0), device.quantity());
                assertEquals(EscrowInvariant.reference(op.device()), device.invariantDigest());
                assertEquals(after, device.bound()); assertEquals(after ? 1 : 0, device.state().epoch());
                assertEquals(after ? List.of(new EscrowPrefix.Link(op, op.device(), load ? "SOURCE" : "DESTINATION").marker()) : List.of(),
                        device.state().markers());
                assertSame(device, device.checkpointed(), "A device survivor is never normalized");
                assertThrows(IllegalArgumentException.class, device::normalizedWrapper);
                assertFalse(EscrowSettlementIntent.encodeObservation(device).contains("normalized_wrapper"));

                var rocketEnvelope = after ? f.after() : f.before(); var rocket = rocket(f, rocketEnvelope);
                var snapshot = RocketSnapshotNbtCodec.decode(rocketEnvelope.getCompound("snapshot")).snapshot().orElseThrow();
                var fuel = RocketFlightNbtCodec.decode(rocketEnvelope.getCompound("flight")).data().orElseThrow().fuel();
                assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, rocket.signatureKind());
                assertEquals(fuel.resourceSignature(), rocket.state().signature());
                assertEquals(fuel.totalAmount(), rocket.quantity(), "The rocket quantity is propulsion, never cargo mB");
                assertEquals(EscrowInvariant.rocket(op.rocket(), op.owner(), snapshot), rocket.invariantDigest());
                assertEquals(after, rocket.bound());
                // The endpoint reference keeps the operation's composite frontiers; the observation never uses them.
                var composite = EscrowSignatureKind.composite(op.rocket().logicalRocket(), snapshot.contentHash(), fuel.resourceSignature());
                assertEquals(composite, after ? op.rocket().afterSignature() : op.rocket().beforeSignature());
                assertNotEquals(composite, rocket.state().signature());
                assertThrows(IllegalArgumentException.class, () -> new EscrowSettlementIntent.Observation(op.rocket(), rocket.state(),
                        rocket.bound(), rocket.quantity(), EscrowSignatureKind.ROCKET_COMPOSITE_V1, rocket.invariantDigest()));
            }
            assertNotEquals(rocket(f, f.before()).invariantDigest(), rocket(f, f.after()).invariantDigest(),
                    "The invariant envelope binds the cargo-sensitive content hash");
        }
    }

    @Test void acceptCurrentFreezesTheDeviceTankAndO4UsesOnlyTheRocketFuelProjection() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowCargoReconciliationFixture.create(load); var op = f.op().withReason(EscrowReason.OTHER_STATE);
            var root = f.ledger().replace(List.of(op), f.ledger().floors(), f.ledger().tombstones());
            // A load held after its device published (rocket still neutral); an unload held after both published.
            var rocket = rocket(f, load ? f.before() : f.after()); var device = device(f, f.deviceAfter());
            var actor = EscrowTestFixtures.id(700);
            var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, actor, "CargoO1", 10,
                    List.of(rocket, device));
            var intent = preview.confirm(actor, preview.digest(), 11, List.of(device, rocket));
            assertEquals(intent, EscrowSettlementIntent.decode(intent.encode()));
            var frozen = intent.observations().stream().filter(value -> value.endpoint().equals(op.rocket())).findFirst().orElseThrow();
            assertEquals(!load, frozen.bound());
            assertEquals(new RocketEscrowSection(root.id(), load ? 0 : 1, load ? List.of()
                    : List.of(new EscrowPrefix.Link(op, op.rocket(), "SOURCE").marker())), frozen.normalizedWrapper());
            assertTrue(frozen.checkpointed().bound());
            var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent, 7);
            assertTrue(released.records().isEmpty());
            var receipt = released.tombstones().get(0);
            assertEquals(0, receipt.getInt("payload_amount")); assertEquals("UNKNOWN", receipt.getString("accounting_status"));
            assertEquals(op.payloadHash(), receipt.getString("payload_hash"));
            assertEquals(2, receipt.getList("before_choice", 10).size()); assertTrue(receipt.getList("missing", 10).isEmpty());
            assertEquals(receipt, EscrowReceiptCodec.decode(EscrowReceiptCodec.encode(receipt)));
            var survivors = receipt.getList("survivors", 10);
            assertEquals(2, survivors.size());
            for (int i = 0; i < survivors.size(); i++) {
                var survivor = survivors.getCompound(i); var reference = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
                boolean isDevice = reference.equals(op.device());
                assertEquals((isDevice ? EscrowSignatureKind.FLUID_TANK_V1 : EscrowSignatureKind.FUEL_VECTOR_V1).name(), survivor.getString("signature_kind"));
                assertEquals(isDevice ? device.quantity() : rocket.quantity(), survivor.getLong("quantity"));
                assertEquals(isDevice ? device.state().signature() : rocket.state().signature(), survivor.getString("signature"));
                assertTrue(survivor.getBoolean("bound"), "The neutral rocket is released only in its normalized form");
            }
            var head = released.rocketAuthorities().get(0);
            assertEquals(op.rocket().identity(), head.physical()); assertEquals(op.owner(), head.owner());
            assertEquals(op.id(), head.anchor()); assertEquals(7, head.releaseGeneration());
            assertEquals(load ? 0 : 1, head.epoch()); assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, head.kind());
            assertEquals(rocket.state().signature(), head.signature()); assertEquals(rocket.invariantDigest(), head.invariant());
            assertEquals(EscrowFlightTables.Outcome.READY, head.outcome());
            var groups = EscrowSettlementPruning.groups(released);
            assertEquals(1, groups.size()); assertEquals(2, groups.get(0).endpoints().size());
        }
    }
}
