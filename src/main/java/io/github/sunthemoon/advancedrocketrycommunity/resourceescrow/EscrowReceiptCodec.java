package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Schema-3 receipts store exact digest bytes and one canonical copy of each audit observation. */
final class EscrowReceiptCodec {
    private static final Set<String> DIGESTS = Set.of("payload_hash", "decision_digest", "digest", "signature",
            "before_signature", "after_signature", "journal_digest", "invariant_digest");
    // Schema-3 wire names are a closed, lossless dictionary, not omitted audit fields.
    private static final List<String> FIELDS = List.of("schema_version", "op_id", "retired", "guards", "before_choice",
            "payload_amount", "payload_hash", "accounting_status", "decision_digest", "actor", "actor_name", "choice",
            "confirmed_tick", "released", "release_generation", "anchor_op", "rocket_id", "transfer_id", "journal_generation",
            "journal_digest", "endpoint", "missing", "bound", "quantity", "signature_kind", "invariant_digest", "state",
            "kind", "level", "identity", "logical_rocket", "type", "x", "y", "z", "unit", "before_epoch", "after_epoch",
            "before_signature", "after_signature", "ledger_id", "epoch", "signature", "markers", "seq", "role", "epoch_after");
    private EscrowReceiptCodec() { }
    static CompoundTag encode(CompoundTag internal) {
        var projected = internal.copy(); projections(projected);
        EscrowTags.require(projected.get("survivors").equals(internal.get("survivors")) && projected.get("missing").equals(internal.get("missing")),
                "Receipt projections do not match frozen observations");
        var encoded = internal.copy();
        // Survivors/missing are canonical projections of before_choice, never separate mutable authority.
        encoded.remove("survivors"); encoded.remove("missing");
        for (var raw : EscrowTags.list(encoded, "before_choice", 2)) {
            // O1 prescribes this exact projection. Do not store a second marker list as authority.
            ((CompoundTag) raw).remove("normalized_wrapper");
        }
        convert(encoded, false);
        names(encoded, false);
        EscrowTags.bound(encoded, 2_048, 16, 128);
        return encoded;
    }
    static CompoundTag decode(CompoundTag encoded) {
        EscrowTags.bound(encoded, 2_048, 16, 128);
        var internal = encoded.copy(); names(internal, true); convert(internal, true);
        for (var raw : EscrowTags.list(internal, "before_choice", 2)) {
            var observation = (CompoundTag) raw;
            if (!observation.getBoolean("missing") && observation.getString("signature_kind").equals(EscrowSignatureKind.FUEL_VECTOR_V1.name())) {
                var state = EscrowTags.compound(observation, "state");
                var wrapper = new CompoundTag(); wrapper.putInt("schema_version", 1);
                wrapper.putUUID("ledger_id", EscrowTags.uuid(state, "ledger_id")); wrapper.putLong("resource_epoch", EscrowTags.number(state, "epoch"));
                wrapper.put("markers", state.get("markers").copy()); observation.put("normalized_wrapper", wrapper);
            }
        }
        projections(internal);
        return internal;
    }
    private static void names(CompoundTag tag, boolean read) {
        for (var key : List.copyOf(tag.getAllKeys())) {
            int index = read ? wireIndex(key) : FIELDS.indexOf(key);
            EscrowTags.require(index >= 0 && index < FIELDS.size(), "Unknown receipt wire field");
            var value = tag.get(key); tag.remove(key);
            if (value instanceof CompoundTag child) { names(child, read); }
            else if (value instanceof ListTag list) {
                for (var item : list) { if (item instanceof CompoundTag child) { names(child, read); } }
            }
            tag.put(read ? FIELDS.get(index) : "f" + Integer.toString(index, 36), value);
        }
    }
    private static int wireIndex(String key) {
        if (!key.matches("f[0-9a-z]{1,2}")) { return -1; }
        int index = Integer.parseInt(key.substring(1), 36);
        return key.equals("f" + Integer.toString(index, 36)) ? index : -1;
    }
    static void projections(CompoundTag tag) {
        var missing = new ListTag(); var survivors = new ListTag();
        for (var raw : EscrowTags.list(tag, "before_choice", 2)) {
            var before = EscrowSettlementIntent.decodeObservation((CompoundTag) raw);
            var chosen = before.checkpointed();
            if (chosen.missing()) { missing.add(EscrowCodec.endpoint(chosen.endpoint())); }
            else {
                var survivor = new CompoundTag(); survivor.put("endpoint", EscrowCodec.endpoint(chosen.endpoint()));
                survivor.putString("digest", EscrowSettlementTombstones.observationDigest(chosen));
                survivor.putLong("epoch", chosen.state().epoch()); survivor.putString("signature", chosen.state().signature());
                survivor.putLong("quantity", chosen.quantity()); survivor.putBoolean("bound", chosen.bound());
                survivor.putString("signature_kind", chosen.signatureKind().name()); survivor.putString("invariant_digest", chosen.invariantDigest());
                survivor.put("markers", EscrowSettlementIntent.encodeObservation(chosen).getCompound("state").get("markers").copy());
                survivors.add(survivor);
            }
        }
        tag.put("missing", missing); tag.put("survivors", survivors);
    }
    private static void convert(CompoundTag tag, boolean read) {
        for (var key : List.copyOf(tag.getAllKeys())) {
            if (DIGESTS.contains(key)) {
                if (read) {
                    EscrowTags.require(tag.contains(key, Tag.TAG_BYTE_ARRAY) && tag.getByteArray(key).length == 32, "Invalid receipt digest bytes");
                    tag.putString(key, HexFormat.of().formatHex(tag.getByteArray(key)));
                } else {
                    var value = EscrowTags.string(tag, key); EscrowTags.digest(value);
                    tag.putByteArray(key, HexFormat.of().parseHex(value));
                }
            } else if (tag.get(key) instanceof CompoundTag child) { convert(child, read); }
            else if (tag.get(key) instanceof ListTag list) {
                for (var child : list) { if (child instanceof CompoundTag compound) { convert(compound, read); } }
            }
        }
    }
}
