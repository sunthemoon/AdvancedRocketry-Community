package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** R15: persistent prefix observations are distinct from the one-use C2 action capture. */
final class EscrowReconciliationValidation {
    private EscrowReconciliationValidation() { }

    /** C0/C3/C6 use the acknowledged chain, not historical complete-envelope commitments. */
    static EscrowPrefix.Classification rocket(UUID ledger, List<EscrowPrefix.Link> chain, CompoundTag envelope) {
        if (chain == null || chain.isEmpty()) { return EscrowPrefix.Classification.OTHER; }
        try {
            var snapshot = RocketSnapshotNbtCodec.decode(EscrowTags.compound(envelope, "snapshot")).snapshot().orElseThrow();
            var flight = RocketFlightNbtCodec.decode(EscrowTags.compound(envelope, "flight")).data().orElseThrow();
            var wrapper = RocketEscrowSection.decode(EscrowTags.compound(envelope, "wrapper"));
            // The first acknowledged fill may still find the pristine, not-yet-bound native wrapper.
            // C2 binds it atomically with epoch 1 and the first marker; no other neutral state is valid.
            if (wrapper.ledger() != null && !ledger.equals(wrapper.ledger()) || flight.fuel().propulsion().isEmpty()
                    || !snapshot.snapshotId().equals(EscrowTags.uuid(envelope, "assembly_snapshot"))
                    || !snapshot.contentHash().equals(envelope.getString("content_hash"))) { return EscrowPrefix.Classification.OTHER; }
            for (var link : chain) {
                var op = link.operation(); var endpoint = link.endpoint(); var binding = op.nativeBinding();
                binding.requireOperation(op.owner(), op.source(), op.destination());
                var chunk = envelope.getIntArray("chunk");
                if (!link.role().equals("DESTINATION") || !binding.complete() || binding.kind() != EscrowSignatureKind.FUEL_VECTOR_V1
                        || !endpoint.type().equals(envelope.getString("type")) || !endpoint.level().equals(envelope.getString("level"))
                        || chunk.length != 2 || endpoint.x() != chunk[0] || endpoint.z() != chunk[1]
                        || !binding.physical().equals(EscrowTags.uuid(envelope, "physical"))
                        || !binding.logical().equals(EscrowTags.uuid(envelope, "logical"))
                        || !binding.logical().equals(flight.logicalRocketId())
                        || !binding.owner().equals(EscrowTags.uuid(envelope, "owner"))
                        || !binding.snapshot().equals(snapshot.snapshotId()) || !binding.snapshotHash().equals(snapshot.contentHash())
                        || !binding.invariant().equals(EscrowInvariant.rocket(endpoint, binding.owner(), snapshot))) {
                    return EscrowPrefix.Classification.OTHER;
                }
            }
            return EscrowPrefix.classify(ledger, chain, new EscrowPrefix.State(ledger, wrapper.epoch(),
                    flight.fuel().resourceSignature(), wrapper.markers()));
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            return EscrowPrefix.Classification.OTHER;
        }
    }

    /** No C0 binding may incorporate a marker absent from this acknowledged endpoint chain. */
    static boolean canBind(EscrowLedger root, EscrowEndpoint endpoint, List<EscrowPrefix.Marker> markers) {
        var chain = EscrowPrefix.chains(root.records()).getOrDefault(endpoint.key(), List.of());
        return markers.stream().allMatch(marker -> chain.stream().anyMatch(link -> link.marker().equals(marker)));
    }

    /** A retained floor for the exact checked T remains a lower bound during ordinary fills. */
    static boolean floorMatches(CompoundTag floor, EscrowEndpoint endpoint, EscrowPrefix.State state, String invariant) {
        return floor.getInt("schema_version") == 2 && endpoint.identity().equals(floor.getUUID("entity_id"))
                && EscrowSignatureKind.FUEL_VECTOR_V1.name().equals(floor.getString("signature_kind"))
                && invariant.equals(floor.getString("invariant_digest")) && state.epoch() >= floor.getLong("resource_epoch")
                && (state.epoch() != floor.getLong("resource_epoch") || state.signature().equals(floor.getString("signature")));
    }

    /** A later L1 member still BEFORE/PREFIX silently delays retirement on every touched end. */
    static boolean retirementReady(EscrowLedger root, Set<UUID> members, Map<EscrowEndpoint.Key, EscrowPrefix.State> states) {
        EscrowPrefix.requireRetirementPrefix(root.records(), members);
        for (var chain : EscrowPrefix.chains(root.records()).entrySet()) {
            if (chain.getValue().stream().noneMatch(link -> members.contains(link.operation().id()))) { continue; }
            var state = states.get(chain.getKey());
            if (state == null || EscrowPrefix.classify(root.id(), chain.getValue(), state) != EscrowPrefix.Classification.AFTER) { return false; }
        }
        return true;
    }

    /** C2 only. E' is computed by the fixed adapter, never supplied as publication authority. */
    static EscrowReason action(EscrowLedger root, EscrowOperation op, CompoundTag before, CompoundTag after, boolean destination) {
        try {
            var binding = op.nativeBinding();
            if (!binding.complete() || op.kind() != EscrowOperation.Kind.FLUID_PROPULSION_FILL
                    || !(destination ? EscrowReconciliationChange.rocket(root.id(), op, before)
                    : EscrowReconciliationChange.source(root.id(), op, before)).equals(after)) { return EscrowReason.OTHER_STATE; }
            if (destination) {
                var chain = EscrowPrefix.chains(root.records()).get(op.destination().key());
                var classified = rocket(root.id(), chain, before);
                var wrapper = RocketEscrowSection.decode(before.getCompound("wrapper"));
                if ((classified != EscrowPrefix.Classification.BEFORE && classified != EscrowPrefix.Classification.PREFIX)
                        || !chain.get(wrapper.markers().size()).operation().equals(op)
                        || !binding.beforeWrapper().equals(EscrowNativeEnvelope.digest(wrapper.encode()))
                        || !binding.afterWrapper().equals(EscrowNativeEnvelope.digest(after.getCompound("wrapper")))
                        || !binding.beforeFrontier().equals(fuelSignature(before)) || !binding.afterFrontier().equals(fuelSignature(after))
                        || rocket(root.id(), chain, after) == EscrowPrefix.Classification.OTHER) { return EscrowReason.OTHER_STATE; }
            } else if (!binding.sourceBefore().equals(EscrowNativeEnvelope.digest(before))
                    || !binding.sourceAfter().equals(EscrowNativeEnvelope.digest(after))) { return EscrowReason.OTHER_STATE; }
            return EscrowReason.NONE;
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            return EscrowReason.OTHER_STATE;
        }
    }

    private static String fuelSignature(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow().fuel().resourceSignature();
    }
}
