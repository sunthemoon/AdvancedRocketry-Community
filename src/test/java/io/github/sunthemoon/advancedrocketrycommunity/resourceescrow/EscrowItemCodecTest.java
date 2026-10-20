package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void bothDirectionsShareRocketAuthorityBindingsFloorsGuardsAndCompositePrefix() {
        for (var kind : KINDS) {
            var op = guarded(operation(kind, 1, 99));
            assertEquals(ledger(op), EscrowCodec.decode(EscrowCodec.encode(ledger(op))));
            assertEquals(kind.cargoLoad() ? op.destination() : op.source(), op.rocket());
            assertEquals(op.rocket().identity(), op.nativeBinding().physical());
            assertEquals(EscrowEndpoint.Unit.PRIMARY, op.rocket().key().unit());
            assertEquals(EscrowSignatureKind.ROCKET_COMPOSITE_V1, EscrowSignatureKind.endpoint(op.rocket()));
            assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, EscrowSignatureKind.settlement(op.rocket()));
            assertEquals(EscrowSignatureKind.ITEM_BUFFER_V1, EscrowSignatureKind.endpoint(op.device()));
            assertEquals(RocketNativeResources.signature(op.payload()), op.payloadHash());
            var chain = EscrowPrefix.chains(List.of(op)).get(op.rocket().key());
            assertEquals(kind.cargoLoad() ? "DESTINATION" : "SOURCE", chain.get(0).role());
            var state = new EscrowPrefix.State(id(500), 1, op.rocket().afterSignature(), List.of(chain.get(0).marker()));
            assertEquals(EscrowPrefix.Classification.AFTER, EscrowPrefix.classify(id(500), chain, state));
            var retired = EscrowFloors.retire(ledger(op), Set.of(op.id()), Map.of(op.rocket().key(), state));
            var floor = retired.floors().get(0);
            assertEquals(op.rocket().identity(), floor.getUUID("entity_id"));
            assertEquals(op.nativeBinding().invariant(), floor.getString("invariant_digest"));
            assertEquals("ROCKET_COMPOSITE_V1", floor.getString("signature_kind"));
            assertEquals(retired, EscrowCodec.decode(EscrowCodec.encode(retired)));
            assertTrue(EscrowReconciliationValidation.floorMatches(floor, op.rocket(), state, op.nativeBinding().invariant()));
            floor.putString("signature_kind", "FUEL_VECTOR_V1");
            assertFalse(EscrowReconciliationValidation.floorMatches(floor, op.rocket(), state, op.nativeBinding().invariant()));
        }
    }

    @Test void payloadIsStrictDefensivelyCopiedAndDirectionChecksBothSlotEdges() {
        var stack = stack(-1); var delta = new EscrowItemCargoPayload(cursor(), stack, 3, 26);
        assertEquals(delta, EscrowItemCargoPayload.decode(delta.encode()));
        stack.putByte("Count", (byte) 0); delta.stack().putByte("Count", (byte) 0);
        assertEquals(64, delta.stack().getByte("Count"));
        assertDoesNotThrow(() -> delta.requireDirection(true)); assertThrows(IllegalArgumentException.class, () -> delta.requireDirection(false));
        for (int[] slots : List.of(new int[] {-1, 0}, new int[] {0, -1}, new int[] {27, 0}, new int[] {0, 27}, new int[] {4, 4})) {
            assertThrows(IllegalArgumentException.class, () -> new EscrowItemCargoPayload(cursor(), stack(-1), slots[0], slots[1]));
        }
        assertDoesNotThrow(() -> new EscrowItemCargoPayload(cursor(), stack(-1), 26, 3).requireDirection(false));
        for (String key : List.of("v", "cursor", "stack", "from", "to")) {
            var bad = delta.encode(); bad.remove(key); assertThrows(IllegalArgumentException.class, () -> EscrowItemCargoPayload.decode(bad));
        }
        var future = delta.encode(); future.putInt("v", 2); assertThrows(IllegalArgumentException.class, () -> EscrowItemCargoPayload.decode(future));
        var wrong = delta.encode(); wrong.putInt("from", 3); assertThrows(IllegalArgumentException.class, () -> EscrowItemCargoPayload.decode(wrong));
    }

    @Test void localLimitsDoNotOverrideTheFramedDepthAndNodeLimits() {
        var bytes = EscrowItemStackCodecTest.bytes(4_096);
        assertDoesNotThrow(() -> new EscrowItemCargoPayload(cursor(), bytes, 0, 0));
        // One outer compound adds one level to the local stack, and nine nodes to this frame.
        assertDoesNotThrow(() -> new EscrowItemCargoPayload(cursor(), EscrowItemStackCodecTest.depth(11), 0, 0));
        assertDoesNotThrow(() -> EscrowItemStackCodec.decode(EscrowItemStackCodecTest.depth(12)));
        EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE,
                () -> new EscrowItemCargoPayload(cursor(), EscrowItemStackCodecTest.depth(12), 0, 0));
        int extra = BoundedNbt.measure(new EscrowItemCargoPayload(cursor(), stack(-1), 0, 0).encode(), 8_192, 12, 384).nodes()
                - BoundedNbt.measure(stack(-1), 4_096, 16, 512).nodes();
        assertDoesNotThrow(() -> new EscrowItemCargoPayload(cursor(), EscrowItemStackCodecTest.nodes(384 - extra), 0, 0));
        var tooMany = EscrowItemStackCodecTest.nodes(385 - extra); assertDoesNotThrow(() -> EscrowItemStackCodec.decode(tooMany));
        EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE,
                () -> new EscrowItemCargoPayload(cursor(), tooMany, 0, 0));
    }

    @Test void directionUnitBindingGuardAndMixedChainSubstitutionsFailClosed() {
        for (var kind : KINDS) {
            var op = operation(kind, 1, 99);
            var wrongDirection = EscrowCodec.operation(op); wrongDirection.putString("kind", kind.cargoLoad() ? "ITEM_CARGO_UNLOAD" : "ITEM_CARGO_LOAD");
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(wrongDirection));
            for (String side : List.of("source", "destination")) {
                var bad = EscrowCodec.operation(op); bad.getCompound(side).putString("unit", "TANK");
                assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(bad));
            }
            var guard = EscrowCodec.operation(op); guard.getCompound("transfer_guard").putUUID("rocket_id", id(42));
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(guard));
            var binding = EscrowCodec.operation(op); binding.getCompound("native_binding").putBoolean("complete", false);
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(binding));
            for (var next : KINDS) { assertThrows(IllegalArgumentException.class, () -> ledger(op, operation(next, 2, 99))); }
            for (var next : EscrowCargoFixtures.KINDS) { assertThrows(IllegalArgumentException.class, () -> ledger(op, EscrowCargoFixtures.operation(next, 2, 99))); }
            assertThrows(IllegalArgumentException.class, () -> ledger(op, EscrowSettlementCapacityTest.operation(2, 99, 1, true)));
        }
    }
}
