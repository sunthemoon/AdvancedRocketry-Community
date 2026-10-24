package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Set;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketReleaseProofTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private final RocketTransferRecord t = legacy();
    private final RocketFlightData flight = t.sourceFlightData().markFailed(10).recover(true, 10);
    private final RocketReleasePlan plan = RocketReleasePlan.create(RocketRejoinRoot.empty(), GovernedTransferFixture.id(900),
            t, RocketRejoinRoot.Hold.H1, t.sourceEntityId(), t.sourceSnapshot(), flight,
            id -> id.equals(GovernedTransferFixture.id(100)), Set.of());
    private static RocketTransferRecord legacy() {
        var g = GovernedTransferFixture.create(1, 3, false).record();
        return RocketTransferRecord.create(g.transferId(), g.logicalRocketId(), g.ownerId(), g.sourceEntityId(),
                g.sourceSnapshot(), g.destinationSnapshot(), g.sourceFlightData(), g.destinationFlightData(), g.requiredFuel(), g.createdAtGameTime());
    }
    private CompoundTag entity(RocketFlightData data) {
        var tag = new CompoundTag(); tag.putString("id", "advancedrocketrycommunity:rocket"); tag.putUUID("UUID", t.sourceEntityId());
        var p = t.sourceSnapshot().sourceOrigin(); tag.put("Pos", GovernedTransferFixture.vector(p.x() + .5, p.y(), p.z() + .5));
        var payload = new CompoundTag(); payload.putInt("schema_version", 3); payload.putUUID("assembly_transaction_id", t.logicalRocketId());
        payload.putUUID("owner_id", t.ownerId()); payload.put("snapshot", RocketSnapshotNbtCodec.encode(t.sourceSnapshot()));
        payload.put("flight_data", RocketFlightNbtCodec.encode(data)); payload.put("resource_escrow", RocketEscrowSection.neutral().encode());
        tag.put("RocketEntityData", payload); return tag;
    }
    private CompoundTag slot(CompoundTag... entities) {
        var root = new CompoundTag(); var list = new ListTag(); for (var e : entities) { list.add(e); }
        var s = plan.batch().slots().get(0); root.putIntArray("Position", new int[] {s.x(), s.z()}); root.put("Entities", list); return root;
    }
    private int count(CompoundTag root, RocketPassengerManifest b0) {
        return RocketReleaseProof.count(root, plan.batch(), plan.batch().slots().get(0), b0);
    }
    @Test void b0RequiresTheLiveManifestAndB1AllowsOtherPassengersToLeaveWithoutRestoringThem() {
        var before = entity(flight); assertEquals(1, count(slot(before), flight.passengers()));
        assertEquals(-1, count(slot(before), null));
        var clean = flight.withPassengers(RocketReleasePlan.filter(flight.passengers(), plan.batch().released()));
        assertEquals(1, count(slot(entity(clean)), null));
        assertEquals(1, count(slot(entity(clean.withPassengers(clean.passengers().remove(GovernedTransferFixture.id(100))))), null));
        assertEquals(-1, count(slot(entity(clean)), flight.passengers()));
    }
    @Test void goneIsOnlyAWellFormedEmptyRecordedSlotAndDuplicateOrNestedCopiesFail() {
        assertEquals(0, count(slot(), null)); assertEquals(-1, count(slot(), flight.passengers()));
        var clean = entity(flight.withPassengers(RocketPassengerManifest.empty(3)));
        assertEquals(-1, count(slot(clean, clean.copy()), null));
        var parent = new CompoundTag(); var nested = new ListTag(); nested.add(clean); parent.put("Passengers", nested);
        assertEquals(-1, count(slot(parent), null));
        var malformed = slot(); var strings = new ListTag(); strings.add(StringTag.valueOf("not an entity")); malformed.put("Entities", strings);
        assertEquals(-1, count(malformed, null));
        malformed = slot(); malformed.putIntArray("Position", new int[] {99, 99}); assertEquals(-1, count(malformed, null));
    }
    @Test void physicalLogicalResourceLocationAndPayloadChangesCannotProveTheAuthority() {
        var clean = entity(flight.withPassengers(RocketPassengerManifest.empty(3)));
        var bad = clean.copy(); bad.putUUID("UUID", GovernedTransferFixture.id(999)); assertEquals(-1, count(slot(bad), null));
        bad = clean.copy(); bad.put("Pos", GovernedTransferFixture.vector(99, 80, 8)); assertEquals(-1, count(slot(bad), null));
        bad = clean.copy(); bad.getCompound("RocketEntityData").getCompound("flight_data").getCompound("fuel").putInt("tamper", 1);
        assertEquals(-1, count(slot(bad), null));
        bad = clean.copy(); bad.getCompound("RocketEntityData").putInt("schema_version", 99); assertEquals(-1, count(slot(bad), null));
        bad = clean.copy(); bad.getCompound("RocketEntityData").putUUID("assembly_transaction_id", GovernedTransferFixture.id(999));
        assertEquals(-1, count(slot(bad), null));
    }
}
