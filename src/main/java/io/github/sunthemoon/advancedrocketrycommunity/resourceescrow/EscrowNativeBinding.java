package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Immutable schema-1 binding inside operation 2; full envelopes remain runtime captures. */
public record EscrowNativeBinding(EscrowSignatureKind kind, UUID physical, UUID logical, UUID owner, UUID snapshot,
        String snapshotHash, String invariant, String sourceReference, String destinationReference,
        String beforeWrapper, String afterWrapper, String beforeFrontier, String afterFrontier,
        String sourceBefore, String sourceAfter, String destinationBefore, String destinationAfter, boolean complete) {
    public EscrowNativeBinding {
        java.util.Objects.requireNonNull(kind); java.util.Objects.requireNonNull(physical);
        java.util.Objects.requireNonNull(logical); java.util.Objects.requireNonNull(owner);
        EscrowTags.require(kind != EscrowSignatureKind.ROCKET_COMPOSITE_V1 && (snapshot != null || !complete
                || kind == EscrowSignatureKind.FIXTURE_RESOURCE_V1), "Missing native snapshot binding");
        for (String digest : List.of(snapshotHash, invariant, sourceReference, destinationReference, beforeWrapper, afterWrapper,
                beforeFrontier, afterFrontier, sourceBefore, sourceAfter, destinationBefore, destinationAfter)) { EscrowTags.digest(digest); }
    }
    public CompoundTag encode() {
        var t = new CompoundTag(); t.putInt("v", 1); t.putString("kind", kind.name()); t.putUUID("physical", physical);
        t.putUUID("logical", logical); t.putUUID("owner", owner); if (snapshot != null) { t.putUUID("snapshot", snapshot); }
        String[] keys = {"snapshot_hash", "invariant", "src_ref", "dst_ref", "before_wrapper", "after_wrapper", "before_frontier", "after_frontier",
                "source_before", "source_after", "destination_before", "destination_after"};
        var hashes = List.of(snapshotHash, invariant, sourceReference, destinationReference, beforeWrapper, afterWrapper,
                beforeFrontier, afterFrontier, sourceBefore, sourceAfter, destinationBefore, destinationAfter);
        for (int i = 0; i < keys.length; i++) { EscrowFlightTables.digest(t, keys[i], hashes.get(i)); }
        t.putBoolean("complete", complete); EscrowTags.bound(t, 2_048, 8, 64); return t;
    }
    public static EscrowNativeBinding decode(CompoundTag t) {
        EscrowTags.bound(t, 2_048, 8, 64);
        EscrowTags.fields(t, "v", "kind", "physical", "logical", "owner", "snapshot", "snapshot_hash", "invariant", "src_ref", "dst_ref",
                "before_wrapper", "after_wrapper", "before_frontier", "after_frontier", "source_before", "source_after", "destination_before", "destination_after", "complete");
        EscrowTags.require(EscrowTags.integer(t, "v") == 1 && t.contains("complete", net.minecraft.nbt.Tag.TAG_BYTE)
                && (t.getByte("complete") == 0 || t.getByte("complete") == 1), "Invalid native binding version/flag");
        return new EscrowNativeBinding(EscrowTags.enumeration(EscrowSignatureKind.class, t, "kind"), EscrowTags.uuid(t, "physical"),
                EscrowTags.uuid(t, "logical"), EscrowTags.uuid(t, "owner"), t.contains("snapshot") ? EscrowTags.uuid(t, "snapshot") : null,
                EscrowFlightTables.digest(t, "snapshot_hash"), EscrowFlightTables.digest(t, "invariant"), EscrowFlightTables.digest(t, "src_ref"),
                EscrowFlightTables.digest(t, "dst_ref"), EscrowFlightTables.digest(t, "before_wrapper"), EscrowFlightTables.digest(t, "after_wrapper"),
                EscrowFlightTables.digest(t, "before_frontier"), EscrowFlightTables.digest(t, "after_frontier"), EscrowFlightTables.digest(t, "source_before"),
                EscrowFlightTables.digest(t, "source_after"), EscrowFlightTables.digest(t, "destination_before"), EscrowFlightTables.digest(t, "destination_after"), t.getBoolean("complete"));
    }
    void requireOperation(UUID expectedOwner, EscrowEndpoint source, EscrowEndpoint destination) {
        EscrowTags.require(owner.equals(expectedOwner) && physical.equals(destination.identity()) && logical.equals(destination.logicalRocket())
                && kind == EscrowSignatureKind.endpoint(destination) && sourceReference.equals(reference(source))
                && destinationReference.equals(reference(destination)) && beforeFrontier.equals(destination.beforeSignature())
                && afterFrontier.equals(destination.afterSignature()), "Native binding does not match operation");
    }
    static String reference(EscrowEndpoint endpoint) { return RocketNativeResources.signature(EscrowCodec.endpoint(endpoint)); }
    /** Pure model fixtures cannot produce a native publication permit or pass native L1 admission. */
    static EscrowNativeBinding model(UUID owner, EscrowEndpoint source, EscrowEndpoint destination) {
        String s = reference(source), d = reference(destination);
        return new EscrowNativeBinding(EscrowSignatureKind.endpoint(destination), destination.identity(), destination.logicalRocket(), owner, null,
                d, EscrowInvariant.reference(destination), s, d, d, d, destination.beforeSignature(), destination.afterSignature(),
                source.beforeSignature(), source.afterSignature(), destination.beforeSignature(), destination.afterSignature(), false);
    }
    record Admission(EscrowOperation operation, RocketEscrowSection before) {
        Admission {
            EscrowTags.require(EscrowNativeEnvelope.digest(before.encode()).equals(operation.nativeBinding().beforeWrapper()),
                    "Admission capture has a different marker section");
        }
    }
    /** C0 uses the actual native capture. Historical projection fixtures below are not admission authority. */
    static Admission captureCurrentTest(EscrowOperation op,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket,
            net.minecraft.world.level.block.entity.BlockEntity source, EscrowLedger root) {
        EscrowTestEndpoints.requireEnabled(rocket.getServer());
        EscrowTags.require(EscrowReconciliationValidation.canBind(root, op.destination(), rocket.escrowSection().markers())
                && EscrowReconciliationValidation.canBind(root, op.source(),
                EscrowTestEndpoints.state(source.getPersistentData(), root.id(), false).markers()), "C0 waits for stale marker pruning");
        var before = EscrowNativeEnvelope.rocket(rocket);
        var after = EscrowReconciliationChange.rocket(root.id(), op, before);
        var next = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec
                .decode(after.getCompound("flight")).data().orElseThrow().fuel();
        return captureTest(op, rocket, source, rocket.flightData().orElseThrow().fuel(), next, root);
    }
    static Admission captureTest(EscrowOperation op, io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket,
            net.minecraft.world.level.block.entity.BlockEntity source,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState beforeFuel,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState afterFuel, EscrowLedger root) {
        EscrowTestEndpoints.requireEnabled(rocket.getServer());
        EscrowTags.require(op.owner().equals(rocket.ownerId().orElseThrow()) && beforeFuel.resourceSignature().equals(op.destination().beforeSignature())
                && afterFuel.resourceSignature().equals(op.destination().afterSignature()), "Capture changed native frontiers");
        var beforeWrapper = captureSection(op, rocket.escrowSection(), root);
        var markers = new java.util.ArrayList<>(beforeWrapper.markers());
        markers.add(new EscrowPrefix.Marker(op.id(), op.sequence(), "DESTINATION", op.destination().afterEpoch()));
        var afterWrapper = new RocketEscrowSection(root.id(), op.destination().afterEpoch(), markers);
        var current = rocket.flightData().orElseThrow();
        var before = EscrowNativeEnvelope.rocket(rocket, EscrowNativeEnvelope.fuelOnly(current, beforeFuel), beforeWrapper);
        var after = EscrowNativeEnvelope.rocket(rocket, EscrowNativeEnvelope.fuelOnly(current, afterFuel), afterWrapper);
        var sourceBefore = source.saveWithFullMetadata(); var sourceAfter = sourceBefore.copy();
        var state = EscrowTestEndpoints.state(source.getPersistentData(), root.id(), false);
        var sourceMarkers = new java.util.ArrayList<>(state.markers()); sourceMarkers.add(new EscrowPrefix.Marker(op.id(), op.sequence(), "SOURCE", op.source().afterEpoch()));
        var persistent = sourceAfter.getCompound("ForgeData");
        persistent.getCompound("arce_escrow_fixture").putInt("amount", EscrowTestEndpoints.amount(source.getPersistentData()) - op.payload().getInt("Amount"));
        EscrowTestEndpoints.putState(persistent, new EscrowPrefix.State(root.id(), op.source().afterEpoch(), op.source().afterSignature(), sourceMarkers), false);
        var snapshot = rocket.snapshot().orElseThrow();
        var binding = new EscrowNativeBinding(EscrowSignatureKind.FUEL_VECTOR_V1, rocket.getUUID(), op.guard().rocket(), op.owner(), snapshot.snapshotId(),
                snapshot.contentHash(), EscrowInvariant.rocket(rocket), reference(op.source()), reference(op.destination()),
                EscrowNativeEnvelope.digest(beforeWrapper.encode()), EscrowNativeEnvelope.digest(afterWrapper.encode()),
                beforeFuel.resourceSignature(), afterFuel.resourceSignature(), EscrowNativeEnvelope.digest(sourceBefore), EscrowNativeEnvelope.digest(sourceAfter),
                EscrowNativeEnvelope.digest(before), EscrowNativeEnvelope.digest(after), true);
        return new Admission(new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), binding), beforeWrapper);
    }
    /** Test-only future-chain projection starts with the captured native suffix, including retired markers. */
    static RocketEscrowSection captureSection(EscrowOperation op, RocketEscrowSection observed, EscrowLedger root) {
        EscrowTags.require((observed.ledger() == null || observed.ledger().equals(root.id()))
                && observed.epoch() <= op.destination().beforeEpoch(), "Native capture has a foreign or later wrapper");
        if (observed.epoch() == op.destination().beforeEpoch()) { return observed; }
        var markers = new java.util.ArrayList<>(observed.markers()); long epoch = observed.epoch();
        var pending = root.records().stream().filter(member -> member.destination().key().equals(op.destination().key())
                && member.destination().afterEpoch() > observed.epoch() && member.destination().afterEpoch() <= op.destination().beforeEpoch())
                .sorted(java.util.Comparator.comparingLong(member -> member.destination().afterEpoch())).toList();
        for (var member : pending) {
            EscrowTags.require(member.destination().beforeEpoch() == epoch, "Native capture has a gap before the modeled fill");
            markers.add(new EscrowPrefix.Marker(member.id(), member.sequence(), "DESTINATION", member.destination().afterEpoch()));
            epoch = member.destination().afterEpoch();
        }
        EscrowTags.require(epoch == op.destination().beforeEpoch(), "Native capture lacks the modeled prefix");
        return new RocketEscrowSection(root.id(), epoch, markers);
    }
}
