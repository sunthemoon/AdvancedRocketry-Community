package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Permanent settlement receipts. Only the chosen physical survivors can be released. */
final class EscrowSettlementTombstones {
    private EscrowSettlementTombstones() { }

    static CompoundTag create(EscrowOperation op, EscrowSettlementIntent intent) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 2); tag.putUUID("op_id", op.id());
        var retired = new ListTag(); var id = new CompoundTag(); id.putUUID("op_id", op.id()); retired.add(id); tag.put("retired", retired);
        var guards = new ListTag(); guards.add(EscrowCodec.guard(op.guard())); tag.put("guards", guards);
        var missing = new ListTag(); var survivors = new ListTag();
        intent.observations().stream().filter(observation -> op.kind() == EscrowOperation.Kind.TRANSFER_FLOOR_RETIRE || observation.endpoint().key().equals(op.source().key())
                || observation.endpoint().key().equals(op.destination().key())).forEach(observation -> {
            if (observation.missing()) { missing.add(EscrowCodec.endpoint(observation.endpoint())); }
            else {
                var survivor = new CompoundTag(); survivor.put("endpoint", EscrowCodec.endpoint(observation.endpoint()));
                survivor.putString("digest", observationDigest(observation)); survivor.putLong("epoch", observation.state().epoch());
                survivor.putString("signature", observation.state().signature()); survivor.putLong("quantity", observation.quantity());
                survivor.putBoolean("bound", observation.bound()); survivors.add(survivor);
            }
        });
        tag.put("missing", missing); tag.put("survivors", survivors);
        int amount = op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? op.payload().getInt("Amount") : 0;
        tag.putInt("amount", amount); tag.putString("payload_hash", op.payloadHash());
        // Payload liability is known; total vanished endpoint contents are not reconstructible from O1.
        tag.putLong("audited_loss", 0); tag.putBoolean("loss_known", missing.isEmpty());
        tag.putString("decision_digest", intent.digest()); tag.putUUID("actor", intent.actor()); tag.putString("actor_name", intent.actorName());
        tag.putString("choice", intent.choice().name()); tag.putLong("confirmed_tick", intent.confirmedTick()); tag.putBoolean("released", false);
        EscrowCodec.validateSection(tag, true); return tag;
    }

    static String observationDigest(EscrowSettlementIntent.Observation observation) {
        var tag = EscrowCodec.endpoint(observation.endpoint()); tag.putBoolean("missing", observation.missing());
        tag.putBoolean("bound", observation.bound()); tag.putLong("quantity", observation.quantity());
        if (!observation.missing()) {
            tag.putUUID("ledger", observation.state().ledger()); tag.putLong("epoch", observation.state().epoch());
            tag.putString("signature", observation.state().signature()); var markers = new ListTag();
            for (var marker : observation.state().markers()) {
                var entry = new CompoundTag(); entry.putUUID("op_id", marker.operation()); entry.putLong("seq", marker.sequence());
                entry.putString("role", marker.role()); entry.putLong("epoch_after", marker.epochAfter()); markers.add(entry);
            }
            tag.put("markers", markers);
        }
        return RocketNativeResources.signature(tag);
    }

    static boolean installed(EscrowLedger root, EscrowSettlementIntent intent) {
        return intent.ids().stream().allMatch(id -> root.tombstones().stream().anyMatch(tag -> tag.getInt("schema_version") == 2
                && tag.getUUID("op_id").equals(id) && tag.getString("decision_digest").equals(intent.digest()) && !tag.getBoolean("released")));
    }

    static EscrowLedger install(EscrowLedger root, EscrowSettlementIntent intent) {
        var tags = new ArrayList<>(root.tombstones());
        for (var op : root.records()) {
            if (!intent.ids().contains(op.id())) { continue; }
            var expected = create(op, intent);
            var existing = tags.stream().filter(tag -> tag.getUUID("op_id").equals(op.id())).findFirst().orElse(null);
            EscrowTags.require(existing == null || existing.equals(expected), "Settlement tombstone changed");
            if (existing == null) { tags.add(expected); }
        }
        return new EscrowLedger(root.id(), root.issuedThrough(), root.writerGeneration(), root.records(), root.floors(), tags);
    }

    static EscrowLedger release(EscrowLedger root, EscrowSettlementIntent intent) {
        EscrowTags.require(installed(root, intent), "Settlement tombstones are not acknowledged");
        var tags = root.tombstones().stream().map(tag -> {
            if (intent.ids().contains(tag.getUUID("op_id"))) { tag.putBoolean("released", true); } return tag;
        }).toList();
        var floorKeys = root.records().stream().filter(op -> intent.ids().contains(op.id())).map(op -> EscrowFloors.key(op.guard()))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return new EscrowLedger(root.id(), root.issuedThrough(), root.writerGeneration(),
                root.records().stream().filter(op -> !intent.ids().contains(op.id())).toList(),
                root.floors().stream().filter(floor -> !floorKeys.contains(EscrowFloors.key(floor))).toList(), tags);
    }

    static boolean logicalLocked(EscrowLedger root, UUID logical) {
        return root.tombstones().stream().anyMatch(tag -> {
            if (tag.getInt("schema_version") != 2 || !tag.getBoolean("released")) { return guarded(tag, logical); }
            return EscrowTags.list(tag, "missing", 2).stream().map(raw -> EscrowCodec.endpoint((CompoundTag) raw))
                    .anyMatch(endpoint -> logical.equals(endpoint.logicalRocket()));
        });
    }
    private static boolean guarded(CompoundTag tag, UUID logical) {
        return EscrowTags.list(tag, "guards", 64).stream().anyMatch(raw -> logical.equals(EscrowCodec.guard((CompoundTag) raw).rocket()))
                || EscrowTags.list(tag, "missing", 2).stream().anyMatch(raw -> logical.equals(EscrowCodec.endpoint((CompoundTag) raw).logicalRocket()));
    }
    static boolean identityLocked(EscrowLedger root, UUID logical, UUID identity) {
        if (logicalLocked(root, logical)) { return true; }
        for (var tag : root.tombstones()) {
            if (tag.getInt("schema_version") != 2 || !guarded(tag, logical)) { continue; }
            boolean survivor = EscrowTags.list(tag, "survivors", 2).stream().map(raw -> EscrowCodec.endpoint(((CompoundTag) raw).getCompound("endpoint")))
                    .anyMatch(endpoint -> endpoint.kind() == EscrowEndpoint.Kind.ENTITY
                            && logical.equals(endpoint.logicalRocket()) && identity.equals(endpoint.identity()));
            if (!survivor) { return true; }
        }
        return false;
    }
    static boolean retiredTransfer(EscrowLedger root, UUID logical, UUID transfer) {
        return root.tombstones().stream().flatMap(tag -> EscrowTags.list(tag, "guards", 64).stream())
                .map(raw -> EscrowCodec.guard((CompoundTag) raw)).anyMatch(guard -> logical.equals(guard.rocket()) && transfer.equals(guard.transfer()));
    }
    static long minimumEpoch(EscrowLedger root, UUID logical, UUID identity) {
        return chosen(root, logical, identity).stream().mapToLong(tag -> tag.getLong("epoch")).max().orElse(0);
    }
    static boolean stateLocked(EscrowLedger root, UUID logical, UUID identity, RocketEscrowSection section, String signature, long quantity) {
        if (identityLocked(root, logical, identity) || section.ledger() != null && !root.id().equals(section.ledger())) { return true; }
        for (var survivor : chosen(root, logical, identity)) {
            long floor = survivor.getLong("epoch");
            if (section.epoch() < floor || survivor.getBoolean("bound") && !root.id().equals(section.ledger())) { return true; }
            if (section.epoch() == floor && (survivor.getBoolean("bound") != (section.ledger() != null)
                    || !signature.equals(survivor.getString("signature")) || quantity != survivor.getLong("quantity"))) { return true; }
        }
        return false;
    }
    private static List<CompoundTag> chosen(EscrowLedger root, UUID logical, UUID identity) {
        return root.tombstones().stream().filter(tag -> tag.getInt("schema_version") == 2 && tag.getBoolean("released"))
                .flatMap(tag -> EscrowTags.list(tag, "survivors", 2).stream()).map(raw -> (CompoundTag) raw).filter(survivor -> {
                    var endpoint = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
                    return endpoint.kind() == EscrowEndpoint.Kind.ENTITY && logical.equals(endpoint.logicalRocket()) && identity.equals(endpoint.identity());
                }).toList();
    }
    static boolean endpointLocked(EscrowLedger root, EscrowEndpoint.Key endpoint) {
        if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) { return identityLocked(root, endpoint.logicalRocket(), endpoint.identity()); }
        for (var tag : root.tombstones()) {
            if (EscrowTags.list(tag, "missing", 2).stream().anyMatch(raw -> endpoint.identity().equals(EscrowCodec.endpoint((CompoundTag) raw).identity()))) { return true; }
            if (tag.getInt("schema_version") != 2) { continue; }
            for (var raw : EscrowTags.list(tag, "survivors", 2)) {
                var chosen = EscrowCodec.endpoint(((CompoundTag) raw).getCompound("endpoint"));
                if (endpoint.identity().equals(chosen.identity()) && (!tag.getBoolean("released") || !endpoint.equals(chosen.key()))) { return true; }
            }
        }
        return false;
    }
    static Set<UUID> retiredIds(List<CompoundTag> tags) {
        return tags.stream().flatMap(tag -> EscrowTags.list(tag, "retired", 64).stream()).map(raw -> ((CompoundTag) raw).getUUID("op_id"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
