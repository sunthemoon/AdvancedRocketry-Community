package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoReconciliationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void compositeBeforeAndAfterAreRecognizedInBothRocketRoles() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowCargoReconciliationFixture.create(load);
            var chain = EscrowPrefix.chains(f.ledger().records()).get(f.op().rocket().key());
            assertEquals(EscrowPrefix.Classification.BEFORE, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, f.before()));
            assertEquals(EscrowPrefix.Classification.AFTER, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, f.after()));
            var forged = f.after().copy(); forged.put("wrapper", f.before().getCompound("wrapper").copy());
            assertEquals(EscrowPrefix.Classification.OTHER, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, forged),
                    "An AFTER snapshot with BEFORE epoch/markers must not be replayed");
        }
    }

    @Test void identityWholeSnapshotFuelAndMarkerSubstitutionsRemainOther() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowCargoReconciliationFixture.create(load);
            var chain = EscrowPrefix.chains(f.ledger().records()).get(f.op().rocket().key());
            for (String field : List.of("physical", "logical", "owner", "assembly_snapshot")) {
                var wrong = f.after().copy(); wrong.putUUID(field, java.util.UUID.randomUUID());
                assertEquals(EscrowPrefix.Classification.OTHER, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, wrong), field);
            }
            var oldPayload = f.after().copy(); oldPayload.put("snapshot", f.before().getCompound("snapshot"));
            oldPayload.putString("content_hash", f.before().getString("content_hash"));
            assertEquals(EscrowPrefix.Classification.OTHER, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, oldPayload));
            var wrongRole = f.after().copy(); wrongRole.getCompound("wrapper").getList("markers", 10).getCompound(0)
                    .putString("role", load ? "SOURCE" : "DESTINATION");
            assertEquals(EscrowPrefix.Classification.OTHER, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, wrongRole));
            var moved = f.after().copy(); moved.putFloat("yaw", 90);
            assertEquals(EscrowPrefix.Classification.AFTER, EscrowReconciliationValidation.rocket(f.ledger().id(), chain, moved));
            assertEquals(f.ledger(), EscrowCodec.decode(EscrowCodec.encode(f.ledger())));
        }
    }

    @Test void checkpointRequiresBothCompositeAfterFrontiersAndKeepsSettlementFuelSeparate() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowCargoReconciliationFixture.create(load); var op = f.op();
            var rocketSlot = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.after());
            var deviceSlot = EscrowFluidCargoEndpointTest.slot(f.deviceAfter());
            var rocket = EscrowSettlementDisk.ordinary(rocketSlot, op.rocket(), f.ledger().id());
            var device = EscrowSettlementDisk.ordinary(deviceSlot, op.device(), f.ledger().id());
            assertNotEquals(rocket.state().signature(), rocket.settlement().state().signature());
            assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, rocket.settlement().signatureKind());
            var frontiers = Map.of(op.rocket().key(), rocket.state(), op.device().key(), device.state());
            var observations = List.of(rocket.settlement(), device.settlement());
            try (var checkpoint = new EscrowReconciliationCheckpoint(f.ledger(), Set.of(op.id()), observations, () -> true, frontiers)) {
                assertTrue(checkpoint.validator(op.rocket().key()).test(rocketSlot));
                assertTrue(checkpoint.validator(op.device().key()).test(deviceSlot));
                assertFalse(checkpoint.validator(op.rocket().key()).test(EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.before())));
                assertFalse(checkpoint.validator(op.device().key()).test(EscrowFluidCargoEndpointTest.slot(f.deviceBefore())));
                assertEquals(op.rocket().afterSignature(), checkpoint.targets(op.rocket()).get(0).frontier().signature());
                assertFalse(checkpoint.proved(), "A classification alone is not native proof");
            }
            assertThrows(IllegalArgumentException.class, () -> new EscrowReconciliationCheckpoint(f.ledger(), Set.of(op.id()),
                    observations, () -> true), "Fuel signatures cannot retire a composite operation");
            var before = EscrowSettlementDisk.ordinary(EscrowFluidCargoEndpointTest.slot(f.deviceBefore()), op.device(), f.ledger().id());
            assertFalse(EscrowReconciliationValidation.retirementReady(f.ledger(), Set.of(op.id()),
                    Map.of(op.rocket().key(), rocket.state(), op.device().key(), before.state())));
        }
    }

    @Test void retirementPrunesBothRolesWithoutLosingEpochOrAcceptingALiveMarker() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowCargoReconciliationFixture.create(load); var op = f.op(); var retired = f.ledger().retire(Set.of(op.id()));
            for (var endpoint : List.of(op.source(), op.destination())) {
                String role = endpoint.equals(op.source()) ? "SOURCE" : "DESTINATION";
                var state = new EscrowPrefix.State(f.ledger().id(), endpoint.afterEpoch(), endpoint.afterSignature(),
                        List.of(new EscrowPrefix.Link(op, endpoint, role).marker()));
                assertTrue(EscrowReconciliationPruning.retired(f.ledger(), endpoint, state, Set.of()).isEmpty());
                assertEquals(Set.of(op.id()), EscrowReconciliationPruning.retired(retired, endpoint, state, Set.of()));
                assertTrue(EscrowReconciliationPruning.retired(retired, endpoint, state, Set.of(op.id())).isEmpty());
                assertEquals(1, state.epoch()); assertEquals(1, state.markers().size());
            }
        }
    }
}
