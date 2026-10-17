package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedDestinationDiskTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void provesExactSourceAndAnActuallyEmptyDestinationSlot() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var source = EscrowGovernedDestinationActionTest.transit(fixture);
        var from = EscrowGovernedDestinationDisk.endpoint(record, false); var to = EscrowGovernedDestinationDisk.endpoint(record, true);
        var sourceSlot = slot(from, nativeEntity(source)); var empty = slot(to);
        assertTrue(EscrowGovernedDestinationDisk.validator(record, from, source).test(sourceSlot));
        assertTrue(EscrowGovernedDestinationDisk.validator(record, to, source).test(empty));
        assertFalse(EscrowGovernedDestinationDisk.validator(record, from, source).test(slot(from)));
        var changed = sourceSlot.copy(); changed.getList("Entities", 10).getCompound(0).getCompound("RocketEntityData")
                .getCompound("resource_escrow").putLong("resource_epoch", 8);
        assertFalse(EscrowGovernedDestinationDisk.validator(record, from, source).test(changed));
        assertFalse(EscrowGovernedDestinationDisk.validator(record, to, source).test(sourceSlot));
    }

    @Test void predeterminedUuidForeignLogicalCopiesAndNestedCopiesRefuseAbsence() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record(); var source = fixture.source();
        var from = EscrowGovernedDestinationDisk.endpoint(record, false); var to = EscrowGovernedDestinationDisk.endpoint(record, true);
        var collision = new CompoundTag(); collision.putUUID("UUID", record.nativeTransfer().orElseThrow().armed().destination());
        collision.putString("id", "minecraft:pig");
        assertFalse(EscrowGovernedDestinationDisk.validator(record, from, source).test(slot(from, nativeEntity(source), collision)));
        assertFalse(EscrowGovernedDestinationDisk.validator(record, to, source).test(slot(to, collision)));
        var copy = nativeEntity(source); copy.putUUID("UUID", GovernedTransferFixture.id(999));
        assertFalse(EscrowGovernedDestinationDisk.validator(record, to, source).test(slot(to, copy)));
        var parent = new CompoundTag(); var passengers = new ListTag(); passengers.add(copy); parent.put("Passengers", passengers);
        assertFalse(EscrowGovernedDestinationDisk.validator(record, to, source).test(slot(to, parent)));
        assertFalse(EscrowGovernedDestinationDisk.validator(record, to, source).test(slot(to, nativeEntity(source))));
    }

    @Test void censusAndPassengerDepthAcceptTheirExactLimitsAndRefuseOneMore() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var endpoint = EscrowGovernedDestinationDisk.endpoint(record, true);
        var validator = EscrowGovernedDestinationDisk.validator(record, endpoint, fixture.source());
        var root = slot(endpoint); var entities = root.getList("Entities", 10);
        for (int i = 0; i < EscrowSettlementObserver.ENTITIES_PER_SLOT; i++) { entities.add(new CompoundTag()); }
        assertTrue(validator.test(root)); entities.add(new CompoundTag()); assertFalse(validator.test(root));
        root = slot(endpoint); var branch = new CompoundTag(); root.getList("Entities", 10).add(branch);
        for (int depth = 1; depth <= 16; depth++) {
            var child = new CompoundTag(); var children = new ListTag(); children.add(child); branch.put("Passengers", children); branch = child;
        }
        assertTrue(validator.test(root)); var extra = new ListTag(); extra.add(new CompoundTag()); branch.put("Passengers", extra);
        assertFalse(validator.test(root));
    }

    static CompoundTag slot(EscrowEndpoint endpoint, CompoundTag... objects) {
        var root = new CompoundTag(); root.putIntArray("Position", new int[] {endpoint.x(), endpoint.z()});
        var entities = new ListTag(); for (var object : objects) { entities.add(object); } root.put("Entities", entities); return root;
    }
    static CompoundTag nativeEntity(CompoundTag envelope) {
        var entity = new CompoundTag(); entity.putUUID("UUID", envelope.getUUID("physical")); entity.putString("id", envelope.getString("type"));
        var data = new CompoundTag(); data.putInt("schema_version", 3); data.putUUID("assembly_transaction_id", envelope.getUUID("logical"));
        data.putUUID("owner_id", envelope.getUUID("owner")); data.put("snapshot", envelope.getCompound("snapshot").copy());
        data.put("flight_data", envelope.getCompound("flight").copy()); data.put("resource_escrow", envelope.getCompound("wrapper").copy());
        entity.put("RocketEntityData", data); entity.put("Pos", envelope.get("pos").copy()); entity.put("Motion", envelope.get("motion").copy());
        var rotation = new ListTag(); rotation.add(FloatTag.valueOf(envelope.getFloat("yaw"))); rotation.add(FloatTag.valueOf(envelope.getFloat("pitch")));
        entity.put("Rotation", rotation); entity.putBoolean("NoGravity", envelope.getBoolean("no_gravity"));
        entity.putBoolean("OnGround", envelope.getBoolean("on_ground")); entity.putBoolean("Invulnerable", envelope.getBoolean("invulnerable")); return entity;
    }
}
