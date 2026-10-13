package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Immutable server-owned authority. Floors/tombstones have codecs but no online mutation in W11b1. */
public record EscrowLedger(UUID id, long issuedThrough, long writerGeneration,
                           List<EscrowOperation> records, List<CompoundTag> floors,
                           List<CompoundTag> tombstones) {
    public EscrowLedger {
        Objects.requireNonNull(id);
        EscrowTags.require(issuedThrough >= 0 && writerGeneration >= 0, "Invalid ledger counters");
        EscrowTags.require(records.size() <= EscrowLimits.OPERATIONS && floors.size() <= EscrowLimits.FLOORS
                && tombstones.size() <= EscrowLimits.TOMBSTONES, "Escrow capacity exceeded");
        records = List.copyOf(records);
        var ids = new HashSet<UUID>(); var sequences = new HashSet<Long>();
        var devices = new HashSet<UUID>(); var rockets = new HashMap<UUID, Integer>();
        for (EscrowOperation op : records) {
            EscrowTags.require(ids.add(op.id()) && sequences.add(op.sequence()) && op.sequence() <= issuedThrough,
                    "Duplicate or unissued operation");
            EscrowTags.require(op.kind() != EscrowOperation.Kind.FLUID_PROPULSION_FILL
                    || devices.add(op.source().identity()), "Multiple operations for a device");
            int count = rockets.merge(op.destination().logicalRocket(), 1, Integer::sum);
            EscrowTags.require(count <= EscrowLimits.PER_ROCKET, "Rocket pending limit exceeded");
        }
        EscrowPrefix.validateChains(records);
        EscrowSettlementIntent.validateComponents(records);
        floors = copySections(floors, false); tombstones = copySections(tombstones, true);
        for (CompoundTag floor : floors) {
            EscrowTags.require(floor.getLong("highest_seq") <= issuedThrough, "Floor is beyond the issued watermark");
        }
        for (EscrowOperation op : records) {
            if (!op.settlement().isEmpty()) {
                EscrowTags.require(id.equals(EscrowSettlementIntent.decode(op.settlement()).ledger()), "Foreign settlement ledger");
            }
            if (op.kind() == EscrowOperation.Kind.TRANSFER_FLOOR_RETIRE) {
                EscrowTags.require(floors.stream().anyMatch(op.payload()::equals), "Floor intent lost its authoritative floor");
            }
        }
    }

    public static EscrowLedger empty(UUID id) { return new EscrowLedger(id, 0, 0, List.of(), List.of(), List.of()); }
    @Override public List<CompoundTag> floors() { return floors.stream().map(CompoundTag::copy).toList(); }
    @Override public List<CompoundTag> tombstones() { return tombstones.stream().map(CompoundTag::copy).toList(); }
    public EscrowLedger generation(long generation) {
        EscrowTags.require(generation > writerGeneration, "Writer generation must advance");
        return new EscrowLedger(id, issuedThrough, generation, records, floors, tombstones);
    }
    public EscrowLedger append(List<EscrowOperation> additions) {
        var all = new ArrayList<>(records); all.addAll(additions);
        long watermark = additions.stream().mapToLong(EscrowOperation::sequence).max().orElse(issuedThrough);
        return new EscrowLedger(id, Math.max(issuedThrough, watermark), writerGeneration, all, floors, tombstones);
    }
    public EscrowLedger retire(java.util.Set<UUID> retired) {
        EscrowPrefix.requireRetirementPrefix(records, retired);
        return new EscrowLedger(id, issuedThrough, writerGeneration,
                records.stream().filter(op -> !retired.contains(op.id())).toList(), floors, tombstones);
    }
    private static List<CompoundTag> copySections(List<CompoundTag> sections, boolean tombstone) {
        var result = new ArrayList<CompoundTag>(); var ids = new HashSet<String>();
        for (CompoundTag section : sections) {
            EscrowCodec.validateSection(section, tombstone);
            String key = EscrowTags.uuid(section, tombstone ? "op_id" : "rocket_id").toString();
            if (!tombstone && section.contains("transfer_id")) { key += ":" + EscrowTags.uuid(section, "transfer_id"); }
            EscrowTags.require(ids.add(key), "Duplicate escrow section");
            result.add(section.copy());
        }
        return List.copyOf(result);
    }
}
