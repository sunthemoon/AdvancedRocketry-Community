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
                           List<CompoundTag> tombstones, long issuedLaunchSerial,
                           List<EscrowFlightTables.Head> rocketAuthorities, List<EscrowFlightTables.Armed> armedFlights) {
    public EscrowLedger(UUID id, long issuedThrough, long writerGeneration, List<EscrowOperation> records,
            List<CompoundTag> floors, List<CompoundTag> tombstones) {
        this(id, issuedThrough, writerGeneration, records, floors, tombstones, 0, List.of(), List.of());
    }
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
            EscrowTags.require(!op.kind().transfer()
                    || devices.add(op.device().identity()), "Multiple operations for a device");
            int count = rockets.merge(op.rocket().logicalRocket(), 1, Integer::sum);
            EscrowTags.require(count <= EscrowLimits.PER_ROCKET, "Rocket pending limit exceeded");
        }
        for (EscrowOperation op : records) {
            EscrowTags.require(!op.kind().cargo() || rockets.get(op.rocket().logicalRocket()) == 1,
                    "Cargo cannot chain or share a rocket with another operation");
        }
        EscrowPrefix.validateChains(records);
        EscrowSettlementIntent.validateComponents(records);
        floors = copySections(floors, false); tombstones = copySections(tombstones, true);
        EscrowTags.require(issuedLaunchSerial >= 0 && rocketAuthorities.size() <= 64 && armedFlights.size() <= 64, "Flight authority capacity exceeded");
        rocketAuthorities = List.copyOf(rocketAuthorities); armedFlights = List.copyOf(armedFlights);
        var checkedReceipts = tombstones; var checkedHeads = rocketAuthorities;
        var logicalHeads = new HashSet<UUID>(); var logicalArms = new HashSet<UUID>(); var serials = new HashSet<Long>(); var transfers = new HashSet<UUID>();
        for (var head : rocketAuthorities) {
            head.encode();
            EscrowTags.require(logicalHeads.add(head.logical()) && head.issued() <= issuedLaunchSerial
                    && checkedReceipts.stream().anyMatch(tag -> tag.getUUID("op_id").equals(head.anchor()) && tag.getUUID("anchor_op").equals(head.anchor())
                    && tag.getBoolean("released") && tag.getLong("release_generation") == head.releaseGeneration()
                    && checkedReceipts.stream().filter(member -> member.getString("decision_digest").equals(tag.getString("decision_digest"))
                    && member.getUUID("anchor_op").equals(head.anchor()) && member.getBoolean("released") && member.getLong("release_generation") == head.releaseGeneration())
                    .flatMap(member -> EscrowTags.list(member, "guards", 1).stream()).anyMatch(raw -> EscrowCodec.guard((CompoundTag) raw).rocket().equals(head.logical()))),
                    "Unbacked or duplicate READY head");
        }
        for (var arm : armedFlights) {
            arm.encode(); EscrowTags.require(id.equals(arm.ledger()) && arm.serial() <= issuedLaunchSerial && logicalArms.add(arm.logical())
                    && serials.add(arm.serial()) && transfers.add(arm.transfer()), "Duplicate or foreign flight authority");
            EscrowTags.require(arm.anchor() == null || checkedHeads.stream().anyMatch(head -> head.logical().equals(arm.logical())
                    && head.owner().equals(arm.owner()) && head.anchor().equals(arm.anchor()) && head.releaseGeneration() == arm.releaseGeneration()), "ARMED head reference mismatch");
        }
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
        return new EscrowLedger(id, issuedThrough, generation, records, floors, tombstones, issuedLaunchSerial, rocketAuthorities, armedFlights);
    }
    public EscrowLedger append(List<EscrowOperation> additions) {
        var all = new ArrayList<>(records); all.addAll(additions);
        long watermark = additions.stream().mapToLong(EscrowOperation::sequence).max().orElse(issuedThrough);
        return new EscrowLedger(id, Math.max(issuedThrough, watermark), writerGeneration, all, floors, tombstones, issuedLaunchSerial, rocketAuthorities, armedFlights);
    }
    public EscrowLedger retire(java.util.Set<UUID> retired) {
        EscrowPrefix.requireRetirementPrefix(records, retired);
        return new EscrowLedger(id, issuedThrough, writerGeneration,
                records.stream().filter(op -> !retired.contains(op.id())).toList(), floors, tombstones, issuedLaunchSerial, rocketAuthorities, armedFlights);
    }
    EscrowLedger replace(List<EscrowOperation> nextRecords, List<CompoundTag> nextFloors, List<CompoundTag> nextReceipts) {
        return new EscrowLedger(id, issuedThrough, writerGeneration, nextRecords, nextFloors, nextReceipts, issuedLaunchSerial, rocketAuthorities, armedFlights);
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
