package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestinationDiskTest.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedTerminalDiskTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void handoffRequiresLandedDestinationAndSourceAbsentInBothSlots() {
        var record = EscrowGovernedArrivalStateTest.record(); var survivor = landed(record);
        var slots = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.HANDED_OFF); assertEquals(2, slots.size());
        var source = slots.get(0); var destination = slots.get(1);
        var sourceCheck = EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.HANDED_OFF, source, survivor);
        var destinationCheck = EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.HANDED_OFF, destination, survivor);
        assertTrue(sourceCheck.test(slot(source))); assertTrue(destinationCheck.test(slot(destination, nativeEntity(survivor))));
        assertFalse(destinationCheck.test(slot(destination)));
        var old = nativeEntity(record.nativeTransfer().orElseThrow().sourceEnvelope(record));
        assertFalse(sourceCheck.test(slot(source, old)));
        assertFalse(destinationCheck.test(slot(destination, nativeEntity(survivor), old)));
        assertFalse(sourceCheck.test(slot(source, nativeEntity(survivor))));
        assertFalse(sourceCheck.test(slot(destination)));
    }

    @Test void cancellationRequiresReturnedSourceAndNeverAcceptsTheInitialCheckpointOrDestination() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var survivor = returned(fixture); var slots = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.CANCELLED);
        assertFalse(EscrowGovernedTerminalDisk.survivor(record, EscrowFlightTables.Outcome.CANCELLED, fixture.source()));
        assertTrue(EscrowGovernedTerminalDisk.survivor(record, EscrowFlightTables.Outcome.CANCELLED, survivor));
        for (var slot : slots) {
            var validator = EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.CANCELLED, slot, survivor);
            var root = slot.equals(slots.get(0)) ? slot(slot, nativeEntity(survivor)) : slot(slot);
            assertTrue(validator.test(root));
            root.getList("Entities", Tag.TAG_COMPOUND).add(nativeEntity(fixture.destination()));
            assertFalse(validator.test(root));
        }
        assertFalse(EscrowGovernedTerminalDisk.survivor(record, EscrowFlightTables.Outcome.ARMED, survivor));
        assertFalse(EscrowGovernedTerminalDisk.survivor(record, EscrowFlightTables.Outcome.HANDED_OFF, survivor));
        var later = EscrowGovernedDispatchTest.committed(record);
        assertFalse(EscrowGovernedTerminalDisk.survivor(later, EscrowFlightTables.Outcome.CANCELLED, survivor));
    }

    @Test void completeSuccessorAndFrozenPredicateRejectAnyChangedNativeEnvelope() {
        var record = EscrowGovernedArrivalStateTest.record(); var survivor = landed(record);
        var endpoint = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.HANDED_OFF).get(1);
        var validator = EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.HANDED_OFF, endpoint, survivor);
        for (var key : survivor.getAllKeys()) {
            var changed = survivor.copy(); changed.remove(key);
            assertFalse(EscrowGovernedTerminalDisk.survivor(record, EscrowFlightTables.Outcome.HANDED_OFF, changed), key);
        }
        var changed = nativeEntity(survivor); changed.getCompound("RocketEntityData").getCompound("resource_escrow").putLong("resource_epoch", 1);
        assertFalse(validator.test(slot(endpoint, changed)));
        var original = nativeEntity(survivor);
        survivor.putUUID("owner", GovernedTransferFixture.id(999));
        assertTrue(validator.test(slot(endpoint, original)), "Validator retained caller-owned mutable input");
        var descent = EscrowGovernedArrivalStateTest.descent(record, 500, RocketFlightLimits.DESCENT_TICKS);
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedTerminalDisk.validator(record,
                EscrowFlightTables.Outcome.HANDED_OFF, endpoint, descent));
        var foreign = new EscrowEndpoint(endpoint.kind(), endpoint.level(), endpoint.identity(), endpoint.logicalRocket(),
                endpoint.type(), endpoint.x() + 1, endpoint.y(), endpoint.z(), endpoint.unit(), endpoint.beforeEpoch(),
                endpoint.beforeSignature(), endpoint.afterSignature());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedTerminalDisk.validator(record,
                EscrowFlightTables.Outcome.HANDED_OFF, foreign, landed(record)));
    }

    @Test void physicalCollisionLogicalCopyAndNestedCopiesPreventAbsence() {
        var record = EscrowGovernedArrivalStateTest.record(); var survivor = landed(record);
        var endpoints = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.HANDED_OFF);
        for (var endpoint : endpoints) {
            var check = EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.HANDED_OFF, endpoint, survivor);
            for (boolean logical : List.of(false, true)) {
                var collision = logical ? nativeEntity(survivor) : new CompoundTag();
                collision.putUUID("UUID", logical ? GovernedTransferFixture.id(999) : record.sourceEntityId());
                var root = endpoint.equals(endpoints.get(1)) ? slot(endpoint, nativeEntity(survivor)) : slot(endpoint);
                var parent = new CompoundTag(); var children = new ListTag(); children.add(collision); parent.put("Passengers", children);
                root.getList("Entities", 10).add(parent); assertFalse(check.test(root));
            }
        }
    }

    @Test void absenceCensusAndDepthKeepTheirExactBounds() {
        var record = EscrowGovernedArrivalStateTest.record();
        var endpoint = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.HANDED_OFF).get(0);
        var check = EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.HANDED_OFF, endpoint, landed(record));
        var root = slot(endpoint); var entities = root.getList("Entities", 10);
        for (int i = 0; i < EscrowSettlementObserver.ENTITIES_PER_SLOT; i++) { entities.add(new CompoundTag()); }
        assertTrue(check.test(root)); entities.add(new CompoundTag()); assertFalse(check.test(root));
        root = slot(endpoint); var branch = new CompoundTag(); root.getList("Entities", 10).add(branch);
        for (int depth = 1; depth <= 16; depth++) {
            var child = new CompoundTag(); var children = new ListTag(); children.add(child); branch.put("Passengers", children); branch = child;
        }
        assertTrue(check.test(root)); var extra = new ListTag(); extra.add(new CompoundTag()); branch.put("Passengers", extra);
        assertFalse(check.test(root));
    }

    static CompoundTag landed(RocketTransferRecord record) {
        var envelope = EscrowGovernedArrivalStateTest.descent(record, 500, RocketFlightLimits.DESCENT_TICKS);
        envelope.put("flight", RocketFlightNbtCodec.encode(EscrowGovernedArrivalState.flight(envelope).land(600))); return envelope;
    }
    static CompoundTag returned(GovernedTransferFixture.Fixture fixture) {
        var envelope = fixture.source().copy(); var initial = EscrowGovernedReturnAction.flight(envelope);
        envelope.put("flight", RocketFlightNbtCodec.encode(initial.markFailed(200).recover(initial.fuel().availableForLaunch(), 200)));
        return envelope;
    }
}
