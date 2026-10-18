package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void bothDirectionsRoundTripWithOneWholeRocketKeyAndCompositeFloors() {
        for (var kind : KINDS) {
            var op = guarded(operation(kind, 1, 99));
            assertEquals(op, EscrowCodec.operation(EscrowCodec.operation(op)));
            assertEquals(ledger(op), EscrowCodec.decode(EscrowCodec.encode(ledger(op))));
            assertEquals(kind == EscrowOperation.Kind.FLUID_CARGO_LOAD ? op.destination() : op.source(), op.rocket());
            assertEquals(op.rocket().identity(), op.nativeBinding().physical());
            assertEquals(EscrowEndpoint.Unit.PRIMARY, op.rocket().key().unit());
            assertEquals(EscrowSignatureKind.ROCKET_COMPOSITE_V1, EscrowSignatureKind.endpoint(op.rocket()));
            assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, EscrowSignatureKind.settlement(op.rocket()));
            assertEquals(EscrowSignatureKind.FLUID_TANK_V1, EscrowSignatureKind.endpoint(op.device()));
            var chain = EscrowPrefix.chains(List.of(op)).get(op.rocket().key());
            assertEquals(kind == EscrowOperation.Kind.FLUID_CARGO_LOAD ? "DESTINATION" : "SOURCE", chain.get(0).role());
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

    @Test void cursorAndFluidAreCanonicalBoundedAndDefensivelyCopied() {
        var fluid = fluid(-1); var cargo = new EscrowCargoPayload(cursor(), fluid);
        assertEquals(cargo, EscrowCargoPayload.decode(cargo.encode()));
        fluid.putInt("Amount", 0); cargo.fluid().putInt("Amount", 0);
        assertEquals(1_000, cargo.fluid().getInt("Amount"));
        for (int amount : List.of(0, -1, 1_001)) {
            var bad = fluid(-1); bad.putInt("Amount", amount);
            assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload(cursor(), bad));
        }
        var missing = fluid(-1); missing.putString("FluidName", "missing:fluid");
        assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload(cursor(), missing));
        for (String field : List.of("v", "cursor", "fluid")) {
            var bad = cargo.encode(); bad.remove(field); assertThrows(IllegalArgumentException.class, () -> EscrowCargoPayload.decode(bad));
        }
        var future = cargo.encode(); future.putInt("v", 2);
        assertThrows(IllegalArgumentException.class, () -> EscrowCargoPayload.decode(future));
        var badCursor = cursor().encode(); badCursor.putIntArray("p", new int[] {1, 2});
        assertThrows(IllegalArgumentException.class, () -> EscrowCargoPayload.Cursor.decode(badCursor));
        assertTrue(BoundedNbt.fits(cursor().encode(), 256, 2, 8));
    }

    @Test void anInnerFluidThatFitsCannotExceedFramedBytesDepthOrNodes() {
        var large = fluid(8_100);
        assertTrue(BoundedNbt.fits(large, 8_192, 12, 384));
        assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload(cursor(), large));
        var deep = fluid(-1); var child = new CompoundTag(); deep.put("Tag", child);
        for (int i = 0; i < 10; i++) { var next = new CompoundTag(); child.put("c", next); child = next; }
        assertTrue(BoundedNbt.fits(deep, 8_192, 12, 384));
        assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload(cursor(), deep));
        var nodes = fluid(-1); var tag = new CompoundTag(); nodes.put("Tag", tag);
        for (int i = 0; i < 378; i++) { tag.putByte("n" + i, (byte) 1); }
        assertTrue(BoundedNbt.fits(nodes, 8_192, 12, 384));
        assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload(cursor(), nodes));
    }

    @Test void badDirectionKindBindingAndMixedChainsFailClosed() {
        for (var kind : KINDS) {
            var op = operation(kind, 1, 99); var encoded = EscrowCodec.operation(op);
            encoded.putString("kind", kind == EscrowOperation.Kind.FLUID_CARGO_LOAD ? "FLUID_CARGO_UNLOAD" : "FLUID_CARGO_LOAD");
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(encoded));
            for (String reserved : List.of("ITEM_CARGO_LOAD", "ITEM_CARGO_UNLOAD")) {
                var item = EscrowCodec.operation(op); item.putString("kind", reserved);
                assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(item));
            }
            var incomplete = EscrowCodec.operation(op); incomplete.getCompound("native_binding").putBoolean("complete", false);
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(incomplete));
            var foreign = EscrowCodec.operation(op); foreign.getCompound("native_binding").putUUID("physical", id(404));
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(foreign));
            for (var next : KINDS) { assertThrows(IllegalArgumentException.class, () -> ledger(op, operation(next, 2, 99))); }
            assertThrows(IllegalArgumentException.class, () -> ledger(op, EscrowSettlementCapacityTest.operation(2, 99, 1, true)));
            var wrongGuard = EscrowCodec.operation(op); wrongGuard.getCompound("transfer_guard").putUUID("rocket_id", id(42));
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(wrongGuard));
        }
    }
}
