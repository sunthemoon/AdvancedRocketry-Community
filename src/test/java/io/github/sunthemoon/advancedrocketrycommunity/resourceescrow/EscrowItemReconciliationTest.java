package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemReconciliationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void liveAdmissionProjectsTheItemEndpointContract() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load); var endpoint = f.op().rocket();
            var fuel = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec
                    .decode(f.before().getCompound("flight")).data().orElseThrow().fuel().resourceSignature();
            assertEquals(endpoint, EscrowItemCargoProjection.rocketEndpoint(endpoint.level(), endpoint.identity(),
                    endpoint.logicalRocket(), endpoint.x(), endpoint.z(), endpoint.beforeEpoch(),
                    f.before().getString("content_hash"), f.after().getString("content_hash"), fuel));
        }
    }

    @Test void completeItemCapturesUseOrdinaryReconciliationInBothDirections() {
        for (boolean load : List.of(true, false)) {
            var c = EscrowItemCargoEligibilityTest.sample(load, 5, EscrowItemCargoEligibilityTest.stone(-1));
            var capture = EscrowItemCargoEligibility.check(EscrowTestFixtures.ledger(), List.of(), c.op(),
                    c.before(), c.after(), c.deviceBefore(), c.deviceAfter()).capture();
            assertNotNull(capture);
            assertTrue(EscrowCargoReconciliation.supported(capture.operation()));
        }
    }

    @Test void nativeClassificationAndAtomicFourSlotPermitPreserveTypedPayloads() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load); var op = f.op();
            var chain = EscrowPrefix.chains(f.ledger().records()).get(op.rocket().key());
            assertEquals(EscrowPrefix.Classification.BEFORE, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, f.before()));
            assertEquals(EscrowPrefix.Classification.AFTER, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, f.after()));
            var permit = EscrowItemCargoPublication.issue(f.ledger(), op, f.deviceBefore(), 10, () -> true);
            var root = permit.consume(10, f::deviceBefore);
            assertEquals(f.deviceAfter().getCompound("arce_rocket_item_cargo"), root.encode());
            assertEquals(4, root.buffer().slots().size());
            assertEquals(load ? 59 : 5, root.buffer().slots().get(0).getByte("Count"));
            assertEquals(net.minecraft.nbt.Tag.TAG_BYTE, root.buffer().slots().get(0).getCompound("tag").get("b").getId());
            assertThrows(IllegalArgumentException.class, () -> permit.consume(10, f::deviceBefore));
            assertThrows(IllegalArgumentException.class, () -> EscrowItemCargoPublication.issue(f.ledger(), op, f.deviceAfter(), 10, () -> true));
            var stale = EscrowItemCargoPublication.issue(f.ledger(), op, f.deviceBefore(), 10, () -> true);
            assertThrows(IllegalArgumentException.class, () -> stale.consume(11, f::deviceBefore));
            var other = f.deviceBefore().copy(); other.putInt("x", 1);
            assertThrows(IllegalArgumentException.class, () -> EscrowItemCargoPublication.issue(f.ledger(), op, other, 10, () -> true));
            assertEquals(new RocketEscrowSection(f.ledger().id(), 1, List.of()), EscrowItemCargoEndpoint.pruned(root,
                    new EscrowPrefix.State(f.ledger().id(), 1, root.signature(), List.of())));
            assertThrows(IllegalArgumentException.class, () -> EscrowItemCargoEndpoint.pruned(root,
                    new EscrowPrefix.State(f.ledger().id(), 0, root.signature(), List.of())));
            try (var checkpoint = EscrowItemReadbackTest.checkpoint(f)) {
                assertFalse(checkpoint.proved(), "Captures are not joint disk proof");
            }
        }
    }

    @Test void namedItemCargoRemainsHeldAndR39IsNativeRatherThanDurableDecoding() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load); var op = f.op();
            var encoded = EscrowCodec.operation(op); encoded.getCompound("transfer_guard").putUUID("transfer_id", java.util.UUID.randomUUID());
            var named = EscrowCodec.operation(encoded);
            assertFalse(EscrowCargoReconciliation.supported(named));
            var namedLedger = f.ledger().replace(List.of(named), List.of(), List.of());
            assertThrows(IllegalArgumentException.class, () -> EscrowItemCargoPublication.issue(namedLedger, named, f.deviceBefore(), 10, () -> true));
            for (boolean missing : List.of(true, false)) {
                var invalid = EscrowCodec.operation(op); var stack = invalid.getCompound("payload").getCompound("stack");
                stack.putString("id", missing ? "missing:item" : "minecraft:ender_pearl");
                if (!missing) { stack.putByte("Count", (byte) 17); }
                invalid.putString("payload_hash", io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources
                        .signature(invalid.getCompound("payload")));
                var held = EscrowCodec.operation(invalid);
                assertEquals(held, EscrowCodec.operation(EscrowCodec.operation(held)));
                assertEquals(EscrowReason.OTHER_STATE, assertThrows(EscrowReconciliationView.Refusal.class,
                        () -> EscrowReconciliationAction.requireNativeResource(held)).reason);
                var ledger = f.ledger().replace(List.of(held), List.of(), List.of());
                assertThrows(EscrowReconciliationView.Refusal.class, () -> EscrowItemCargoPublication.issue(ledger, held, f.deviceBefore(), 10, () -> true));
            }
        }
    }

    @Test void censusRejectsDuplicateRelocationAndTypedNbtMutation() {
        var f = EscrowItemReconciliationFixture.create(true); var op = f.op(); var before = f.deviceBefore();
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.ordinary(
                EscrowItemReconciliationFixture.slot(before, before), op.device(), f.ledger().id()));
        var moved = before.copy(); moved.putInt("x", 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.ordinary(
                EscrowItemReconciliationFixture.slot(moved), op.device(), f.ledger().id()));
        var after = f.deviceAfter(); var slot = EscrowItemReconciliationFixture.slot(after);
        var expected = EscrowSettlementDisk.ordinary(slot, op.device(), f.ledger().id());
        var reader = new EscrowCargoReadback(f.ledger().id(), expected, null);
        var altered = after.copy();
        altered.getCompound("arce_rocket_item_cargo").getCompound("buffer").getList("slots", 10)
                .getCompound(0).getCompound("tag").putInt("b", 7);
        assertFalse(reader.test(EscrowItemReconciliationFixture.slot(altered)));
        assertTrue(java.util.concurrent.CompletableFuture.supplyAsync(
                () -> reader.test(EscrowItemReconciliationFixture.slot(altered))).join());
        assertFalse(reader.confirmed(), "Equal numeric values with a different NBT type must not confirm");
    }
}
