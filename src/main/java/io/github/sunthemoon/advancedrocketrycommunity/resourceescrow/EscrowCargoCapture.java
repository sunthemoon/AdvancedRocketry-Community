package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Pure C-K capture binding. C-P supplies the deterministic mutation; this grants no apply or proof authority. */
final class EscrowCargoCapture {
    private EscrowCargoCapture() { }
    static EscrowNativeBinding.Admission capture(UUID ledger, EscrowOperation op, CompoundTag rocketBefore,
            CompoundTag rocketAfter, CompoundTag deviceBefore, CompoundTag deviceAfter) {
        EscrowTags.require(op.kind().cargo(), "Not a cargo transfer");
        for (var envelope : java.util.List.of(rocketBefore, rocketAfter, deviceBefore, deviceAfter)) {
            EscrowTags.bound(envelope, EscrowNativeEnvelope.MAX_BYTES, 24, 131_072);
        }
        var before = snapshot(op, rocketBefore); var after = snapshot(op, rocketAfter);
        var cursor = op.kind().itemCargo() ? EscrowItemCargoPayload.decode(op.payload()).cursor()
                : EscrowCargoPayload.decode(op.payload()).cursor();
        cursor.requirePayload(before); cursor.requirePayload(after);
        EscrowTags.require(before.snapshotId().equals(after.snapshotId()) && before.snapshotId().equals(cursor.snapshot()),
                "Cargo capture changed snapshot identity");
        var beforeFuel = RocketFlightNbtCodec.decode(EscrowTags.compound(rocketBefore, "flight")).data().orElseThrow();
        var afterFuel = RocketFlightNbtCodec.decode(EscrowTags.compound(rocketAfter, "flight")).data().orElseThrow();
        EscrowTags.require(beforeFuel.fuel().propulsion().isPresent() && afterFuel.fuel().propulsion().isPresent()
                && op.rocket().logicalRocket().equals(beforeFuel.logicalRocketId())
                && op.rocket().logicalRocket().equals(afterFuel.logicalRocketId())
                && beforeFuel.fuel().resourceSignature().equals(afterFuel.fuel().resourceSignature()), "Cargo changed propulsion or is legacy");
        var endpoint = op.rocket();
        EscrowTags.require(endpoint.beforeSignature().equals(EscrowSignatureKind.composite(endpoint.logicalRocket(), before.contentHash(), beforeFuel.fuel().resourceSignature()))
                && endpoint.afterSignature().equals(EscrowSignatureKind.composite(endpoint.logicalRocket(), after.contentHash(), afterFuel.fuel().resourceSignature())),
                "Cargo composite frontier changed");
        var beforeWrapper = RocketEscrowSection.decode(EscrowTags.compound(rocketBefore, "wrapper"));
        var afterWrapper = RocketEscrowSection.decode(EscrowTags.compound(rocketAfter, "wrapper"));
        EscrowTags.require((beforeWrapper.ledger() == null || ledger.equals(beforeWrapper.ledger()))
                && beforeWrapper.epoch() == endpoint.beforeEpoch(), "Cargo wrapper changed");
        var markers = new ArrayList<>(beforeWrapper.markers());
        markers.add(new EscrowPrefix.Link(op, endpoint, op.source().equals(endpoint) ? "SOURCE" : "DESTINATION").marker());
        EscrowTags.require(afterWrapper.equals(new RocketEscrowSection(ledger, endpoint.afterEpoch(), markers)), "Cargo after markers changed");
        var beforeFrame = rocketBefore.copy(); var afterFrame = rocketAfter.copy();
        for (String field : java.util.List.of("snapshot", "content_hash", "wrapper")) { beforeFrame.remove(field); afterFrame.remove(field); }
        EscrowTags.require(beforeFrame.equals(afterFrame), "Cargo changed the native non-cargo envelope");
        boolean load = op.kind().cargoLoad();
        var binding = new EscrowNativeBinding(EscrowSignatureKind.ROCKET_COMPOSITE_V1, endpoint.identity(), endpoint.logicalRocket(), op.owner(),
                after.snapshotId(), after.contentHash(), EscrowInvariant.rocket(endpoint, op.owner(), after),
                EscrowNativeBinding.reference(op.source()), EscrowNativeBinding.reference(op.destination()),
                EscrowNativeEnvelope.digest(beforeWrapper.encode()), EscrowNativeEnvelope.digest(afterWrapper.encode()),
                endpoint.beforeSignature(), endpoint.afterSignature(),
                EscrowNativeEnvelope.digest(load ? deviceBefore : rocketBefore), EscrowNativeEnvelope.digest(load ? deviceAfter : rocketAfter),
                EscrowNativeEnvelope.digest(load ? rocketBefore : deviceBefore), EscrowNativeEnvelope.digest(load ? rocketAfter : deviceAfter), true);
        var captured = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), binding);
        EscrowCodec.operation(captured);
        return new EscrowNativeBinding.Admission(captured, beforeWrapper);
    }
    private static RocketStructureSnapshot snapshot(EscrowOperation op, CompoundTag envelope) {
        var endpoint = op.rocket(); var chunk = envelope.getIntArray("chunk");
        EscrowTags.require(endpoint.identity().equals(EscrowTags.uuid(envelope, "physical"))
                && endpoint.logicalRocket().equals(EscrowTags.uuid(envelope, "logical")) && op.owner().equals(EscrowTags.uuid(envelope, "owner"))
                && endpoint.type().equals(EscrowTags.string(envelope, "type")) && endpoint.level().equals(EscrowTags.string(envelope, "level"))
                && chunk.length == 2 && endpoint.x() == chunk[0] && endpoint.z() == chunk[1], "Cargo capture identity or placement changed");
        var tag = EscrowTags.compound(envelope, "snapshot");
        var snapshot = RocketSnapshotNbtCodec.decode(tag).snapshot().orElseThrow();
        EscrowTags.require(RocketSnapshotNbtCodec.encode(snapshot).equals(tag)
                && snapshot.snapshotId().equals(EscrowTags.uuid(envelope, "assembly_snapshot"))
                && snapshot.contentHash().equals(EscrowTags.string(envelope, "content_hash")), "Cargo snapshot envelope changed");
        return snapshot;
    }
}
