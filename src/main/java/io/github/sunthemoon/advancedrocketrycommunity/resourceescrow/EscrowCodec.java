package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Schema 1, no legacy fallback or unknown-field normalization. */
public final class EscrowCodec {
    private EscrowCodec() { }
    public static CompoundTag encode(EscrowLedger ledger) {
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1);
        root.putString("format_epoch", EscrowLimits.EPOCH); root.putUUID("ledger_id", ledger.id());
        root.putLong("issued_through", ledger.issuedThrough()); root.putLong("writer_generation", ledger.writerGeneration());
        ListTag records = new ListTag(); ledger.records().forEach(op -> records.add(operation(op))); root.put("records", records);
        root.put("floors", list(ledger.floors())); root.put("tombstones", list(ledger.tombstones()));
        EscrowTags.bound(root, EscrowLimits.ROOT_BYTES, 24, 131_072); return root;
    }
    public static EscrowLedger decode(CompoundTag root) {
        EscrowTags.bound(root, EscrowLimits.ROOT_BYTES, 24, 131_072);
        EscrowTags.fields(root, "schema_version", "format_epoch", "ledger_id", "issued_through", "writer_generation", "records", "floors", "tombstones");
        EscrowTags.require(EscrowTags.integer(root, "schema_version") == 1
                && EscrowLimits.EPOCH.equals(EscrowTags.string(root, "format_epoch")), "Unsupported escrow root");
        var records = new ArrayList<EscrowOperation>();
        for (var tag : EscrowTags.list(root, "records", EscrowLimits.OPERATIONS)) { records.add(operation((CompoundTag) tag)); }
        var result = new EscrowLedger(EscrowTags.uuid(root, "ledger_id"), EscrowTags.number(root, "issued_through"),
                EscrowTags.number(root, "writer_generation"), records,
                sections(root, "floors", EscrowLimits.FLOORS), sections(root, "tombstones", EscrowLimits.TOMBSTONES));
        EscrowSettlementIntent.reserve(result); return result;
    }
    public static CompoundTag operation(EscrowOperation op) {
        CompoundTag tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.putUUID("op_id", op.id());
        tag.putLong("seq", op.sequence()); tag.putUUID("owner", op.owner()); tag.putLong("created_tick", op.createdTick());
        tag.putString("kind", op.kind().name()); tag.putString("phase", op.phase().name());
        tag.putString("reason", op.reason().name()); tag.put("payload", op.payload()); tag.putString("payload_hash", op.payloadHash());
        tag.put("source", endpoint(op.source())); tag.put("destination", endpoint(op.destination())); tag.put("transfer_guard", guard(op.guard()));
        if (!op.settlement().isEmpty()) { tag.put("settlement", op.settlement()); }
        EscrowTags.bound(tag, EscrowLimits.OP_BYTES, 16, 512); return tag;
    }
    public static EscrowOperation operation(CompoundTag tag) {
        EscrowTags.bound(tag, EscrowLimits.OP_BYTES, 16, 512);
        EscrowTags.fields(tag, "schema_version", "op_id", "seq", "owner", "created_tick", "kind", "phase", "reason", "payload", "payload_hash", "source", "destination", "transfer_guard", "settlement");
        EscrowTags.require(EscrowTags.integer(tag, "schema_version") == 1, "Unsupported escrow operation");
        return new EscrowOperation(EscrowTags.uuid(tag, "op_id"), EscrowTags.number(tag, "seq"), EscrowTags.uuid(tag, "owner"),
                EscrowTags.number(tag, "created_tick"), EscrowTags.enumeration(EscrowOperation.Phase.class, tag, "phase"),
                EscrowTags.enumeration(EscrowReason.class, tag, "reason"), EscrowTags.compound(tag, "payload"),
                EscrowTags.string(tag, "payload_hash"), endpoint(EscrowTags.compound(tag, "source")),
                endpoint(EscrowTags.compound(tag, "destination")), guard(EscrowTags.compound(tag, "transfer_guard")),
                EscrowTags.enumeration(EscrowOperation.Kind.class, tag, "kind"),
                tag.contains("settlement") ? EscrowTags.compound(tag, "settlement") : new CompoundTag());
    }
    public static CompoundTag endpoint(EscrowEndpoint endpoint) {
        CompoundTag tag = new CompoundTag(); tag.putString("kind", endpoint.kind().name()); tag.putString("level", endpoint.level());
        tag.putUUID("identity", endpoint.identity()); if (endpoint.logicalRocket() != null) { tag.putUUID("logical_rocket", endpoint.logicalRocket()); }
        tag.putString("type", endpoint.type()); tag.putInt("x", endpoint.x()); tag.putInt("y", endpoint.y()); tag.putInt("z", endpoint.z());
        tag.putString("unit", endpoint.unit().name()); tag.putLong("before_epoch", endpoint.beforeEpoch()); tag.putLong("after_epoch", endpoint.afterEpoch());
        tag.putString("before_signature", endpoint.beforeSignature()); tag.putString("after_signature", endpoint.afterSignature());
        EscrowTags.bound(tag, EscrowLimits.REFERENCE_BYTES, 8, 32); return tag;
    }
    public static EscrowEndpoint endpoint(CompoundTag tag) {
        EscrowTags.bound(tag, EscrowLimits.REFERENCE_BYTES, 8, 32);
        EscrowTags.fields(tag, "kind", "level", "identity", "logical_rocket", "type", "x", "y", "z", "unit", "before_epoch", "after_epoch", "before_signature", "after_signature");
        var endpoint = new EscrowEndpoint(EscrowTags.enumeration(EscrowEndpoint.Kind.class, tag, "kind"), EscrowTags.string(tag, "level"),
                EscrowTags.uuid(tag, "identity"), tag.contains("logical_rocket") ? EscrowTags.uuid(tag, "logical_rocket") : null,
                EscrowTags.string(tag, "type"), EscrowTags.integer(tag, "x"), EscrowTags.integer(tag, "y"), EscrowTags.integer(tag, "z"),
                EscrowTags.enumeration(EscrowEndpoint.Unit.class, tag, "unit"), EscrowTags.number(tag, "before_epoch"),
                EscrowTags.string(tag, "before_signature"), EscrowTags.string(tag, "after_signature"));
        EscrowTags.require(EscrowTags.number(tag, "after_epoch") == endpoint.afterEpoch(), "Noncontiguous reference epoch"); return endpoint;
    }
    static CompoundTag guard(EscrowGuard guard) {
        CompoundTag tag = new CompoundTag(); tag.putUUID("rocket_id", guard.rocket());
        if (guard.transfer() != null) { tag.putUUID("transfer_id", guard.transfer()); }
        tag.putLong("journal_generation", guard.journalGeneration()); tag.putString("journal_digest", guard.journalDigest()); return tag;
    }
    static EscrowGuard guard(CompoundTag tag) {
        EscrowTags.fields(tag, "rocket_id", "transfer_id", "journal_generation", "journal_digest");
        return new EscrowGuard(EscrowTags.uuid(tag, "rocket_id"), tag.contains("transfer_id") ? EscrowTags.uuid(tag, "transfer_id") : null,
                EscrowTags.number(tag, "journal_generation"), EscrowTags.string(tag, "journal_digest"));
    }
    static EscrowGuard floorGuard(CompoundTag floor) {
        CompoundTag tag = new CompoundTag();
        for (String field : List.of("rocket_id", "transfer_id", "journal_generation", "journal_digest")) {
            if (floor.contains(field)) { tag.put(field, floor.get(field).copy()); }
        }
        return guard(tag);
    }
    static void validateSection(CompoundTag tag, boolean tombstone) {
        EscrowTags.bound(tag, tombstone ? 2_048 : 1_024, 16, 128);
        if (tombstone && EscrowTags.integer(tag, "schema_version") == 2) {
            validateSettlementTombstone(tag); return;
        }
        if (!tombstone) {
            EscrowTags.fields(tag, "schema_version", "rocket_id", "transfer_id", "journal_generation", "journal_digest", "entity_id", "resource_epoch", "signature", "highest_seq");
            CompoundTag journal = new CompoundTag();
            for (String field : List.of("rocket_id", "transfer_id", "journal_generation", "journal_digest")) {
                if (tag.contains(field)) { journal.put(field, tag.get(field)); }
            }
            guard(journal); EscrowTags.uuid(tag, "entity_id"); EscrowTags.digest(EscrowTags.string(tag, "signature"));
            EscrowTags.require(EscrowTags.number(tag, "resource_epoch") >= 0 && EscrowTags.number(tag, "highest_seq") > 0, "Invalid floor counters");
        } else {
            EscrowTags.fields(tag, "schema_version", "op_id", "retired", "missing", "guards", "survivors", "amount", "payload_hash", "audited_loss");
            EscrowTags.uuid(tag, "op_id"); EscrowTags.digest(EscrowTags.string(tag, "payload_hash"));
            EscrowTags.require(EscrowTags.integer(tag, "amount") >= 1 && EscrowTags.integer(tag, "amount") <= 1_000
                    && EscrowTags.number(tag, "audited_loss") >= 0, "Invalid tombstone accounting");
            for (var entry : EscrowTags.list(tag, "retired", 64)) { var item = (CompoundTag) entry; EscrowTags.fields(item, "op_id"); EscrowTags.uuid(item, "op_id"); }
            for (var entry : EscrowTags.list(tag, "missing", 2)) { endpoint((CompoundTag) entry); }
            for (var entry : EscrowTags.list(tag, "guards", 64)) { guard((CompoundTag) entry); }
            for (var entry : EscrowTags.list(tag, "survivors", 2)) {
                var item = (CompoundTag) entry; EscrowTags.fields(item, "identity", "digest"); EscrowTags.uuid(item, "identity"); EscrowTags.digest(EscrowTags.string(item, "digest"));
            }
        }
        EscrowTags.require(EscrowTags.integer(tag, "schema_version") == 1, "Invalid escrow section schema");
    }
    private static void validateSettlementTombstone(CompoundTag tag) {
        EscrowTags.fields(tag, "schema_version", "op_id", "retired", "missing", "guards", "survivors", "amount", "payload_hash", "audited_loss",
                "loss_known", "decision_digest", "actor", "actor_name", "choice", "confirmed_tick", "released");
        EscrowTags.uuid(tag, "op_id"); EscrowTags.uuid(tag, "actor"); EscrowTags.digest(EscrowTags.string(tag, "payload_hash"));
        EscrowTags.digest(EscrowTags.string(tag, "decision_digest")); EscrowTags.enumeration(EscrowSettlementIntent.Choice.class, tag, "choice");
        String name = EscrowTags.string(tag, "actor_name");
        EscrowTags.require(!name.isBlank() && name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 128
                && EscrowTags.number(tag, "confirmed_tick") >= 0 && EscrowTags.integer(tag, "amount") >= 0
                && tag.getInt("amount") <= 1_000 && EscrowTags.number(tag, "audited_loss") >= 0, "Invalid settlement accounting");
        for (String flag : List.of("released", "loss_known")) {
            EscrowTags.require(tag.contains(flag, net.minecraft.nbt.Tag.TAG_BYTE) && (tag.getByte(flag) == 0 || tag.getByte(flag) == 1), "Invalid audit flag");
        }
        var retired = EscrowTags.list(tag, "retired", 1); var guards = EscrowTags.list(tag, "guards", 1);
        EscrowTags.require(retired.size() == 1 && guards.size() == 1, "Receipt needs one retired operation and guard");
        var entry = (CompoundTag) retired.get(0); EscrowTags.fields(entry, "op_id");
        EscrowTags.require(tag.getUUID("op_id").equals(EscrowTags.uuid(entry, "op_id")), "Receipt operation mismatch");
        guard((CompoundTag) guards.get(0));
        var identities = new java.util.HashSet<EscrowEndpoint.Key>();
        for (var raw : EscrowTags.list(tag, "missing", 2)) {
            EscrowTags.require(identities.add(endpoint((CompoundTag) raw).key()), "Duplicate receipt endpoint");
        }
        for (var raw : EscrowTags.list(tag, "survivors", 2)) {
            var survivor = (CompoundTag) raw; EscrowTags.fields(survivor, "endpoint", "digest", "epoch", "signature", "quantity", "bound");
            EscrowTags.require(identities.add(endpoint(EscrowTags.compound(survivor, "endpoint")).key())
                    && EscrowTags.number(survivor, "epoch") >= 0 && EscrowTags.number(survivor, "quantity") >= 0
                    && survivor.contains("bound", net.minecraft.nbt.Tag.TAG_BYTE) && (survivor.getByte("bound") == 0 || survivor.getByte("bound") == 1),
                    "Invalid chosen survivor");
            EscrowTags.digest(EscrowTags.string(survivor, "digest")); EscrowTags.digest(EscrowTags.string(survivor, "signature"));
        }
        EscrowTags.require(!identities.isEmpty(), "Empty settlement receipt");
    }
    private static ListTag list(List<CompoundTag> values) { var list = new ListTag(); values.forEach(list::add); return list; }
    private static List<CompoundTag> sections(CompoundTag tag, String key, int max) {
        return EscrowTags.list(tag, key, max).stream().map(value -> (CompoundTag) value).toList();
    }
}
