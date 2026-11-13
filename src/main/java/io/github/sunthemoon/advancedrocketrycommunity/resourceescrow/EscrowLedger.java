package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Immutable server-owned authority, including same-generation account references and permanent receipts. */
public record EscrowLedger(UUID id, long issuedThrough, long writerGeneration,
                           List<EscrowOperation> records, List<CompoundTag> floors,
                           List<CompoundTag> tombstones, long issuedLaunchSerial,
                           List<EscrowFlightTables.Head> rocketAuthorities, List<EscrowFlightTables.Armed> armedFlights,
                           int schema, List<EscrowFluidAccount.Row> fluidAccounts) {
    public EscrowLedger(UUID id, long issuedThrough, long writerGeneration, List<EscrowOperation> records,
            List<CompoundTag> floors, List<CompoundTag> tombstones, long issuedLaunchSerial,
            List<EscrowFlightTables.Head> rocketAuthorities, List<EscrowFlightTables.Armed> armedFlights) {
        this(id, issuedThrough, writerGeneration, records, floors, tombstones, issuedLaunchSerial, rocketAuthorities, armedFlights,
                EscrowAccountingFormat.ENABLED ? 3 : 2, List.of());
    }
    public EscrowLedger(UUID id, long issuedThrough, long writerGeneration, List<EscrowOperation> records,
            List<CompoundTag> floors, List<CompoundTag> tombstones) {
        this(id, issuedThrough, writerGeneration, records, floors, tombstones, 0, List.of(), List.of());
    }
    public EscrowLedger {
        Objects.requireNonNull(id);
        EscrowTags.require(schema == 2 || schema == 3, "Unsupported ledger schema");
        fluidAccounts = List.copyOf(fluidAccounts);
        EscrowTags.require(issuedThrough >= 0 && writerGeneration >= 0, "Invalid ledger counters");
        if (records.size() > EscrowLimits.OPERATIONS || floors.size() > EscrowLimits.FLOORS
                || tombstones.size() > EscrowLimits.TOMBSTONES) {
            throw new EscrowSettlementCapacity.ReservationFull("Escrow capacity exceeded");
        }
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
        validateAccounts(schema, records, tombstones, fluidAccounts);
    }

    public static EscrowLedger empty(UUID id) { return new EscrowLedger(id, 0, 0, List.of(), List.of(), List.of()); }
    @Override public List<CompoundTag> floors() { return floors.stream().map(CompoundTag::copy).toList(); }
    @Override public List<CompoundTag> tombstones() { return tombstones.stream().map(CompoundTag::copy).toList(); }
    public EscrowLedger generation(long generation) {
        EscrowTags.require(generation > writerGeneration, "Writer generation must advance");
        return new EscrowLedger(id, issuedThrough, generation, records, floors, tombstones, issuedLaunchSerial, rocketAuthorities, armedFlights, schema, fluidAccounts);
    }
    public EscrowLedger append(List<EscrowOperation> additions) {
        var all = new ArrayList<>(records); all.addAll(additions);
        long watermark = additions.stream().mapToLong(EscrowOperation::sequence).max().orElse(issuedThrough);
        return new EscrowLedger(id, Math.max(issuedThrough, watermark), writerGeneration, all, floors, tombstones, issuedLaunchSerial, rocketAuthorities, armedFlights, schema, fluidAccounts);
    }
    public EscrowLedger retire(java.util.Set<UUID> retired) {
        EscrowPrefix.requireRetirementPrefix(records, retired);
        return new EscrowLedger(id, issuedThrough, writerGeneration,
                records.stream().filter(op -> !retired.contains(op.id())).toList(), floors, tombstones, issuedLaunchSerial, rocketAuthorities, armedFlights, schema,
                fluidAccounts.stream().filter(row -> !retired.contains(row.operation()) || tombstones.stream().anyMatch(t -> t.getUUID("op_id").equals(row.operation()))).toList());
    }
    EscrowLedger replace(List<EscrowOperation> nextRecords, List<CompoundTag> nextFloors, List<CompoundTag> nextReceipts) {
        return new EscrowLedger(id, issuedThrough, writerGeneration, nextRecords, nextFloors, nextReceipts, issuedLaunchSerial, rocketAuthorities, armedFlights, schema, fluidAccounts);
    }
    public EscrowLedger accounting(List<EscrowOperation> operations, List<EscrowFluidAccount.Row> rows) {
        return new EscrowLedger(id, issuedThrough, writerGeneration, operations, floors, tombstones, issuedLaunchSerial,
                rocketAuthorities, armedFlights, 3, rows);
    }

    private static void validateAccounts(int schema, List<EscrowOperation> records, List<CompoundTag> receipts,
            List<EscrowFluidAccount.Row> rows) {
        var encoded = EscrowFluidAccountCodec.encodeRows(rows, EscrowFluidAccountCodec.SAVED);
        EscrowTags.require(encoded.ok(), "Invalid account rows: " + encoded.status());
        if (schema == 2) {
            EscrowTags.require(rows.isEmpty() && records.stream().allMatch(op -> op.accountingBaseline() == null
                    && (op.settlement().isEmpty() || !op.settlement().contains("account_commitment")))
                    && receipts.stream().allMatch(t -> t.getInt("schema_version") == 3), "Accounting in a root-2 ledger");
            return;
        }
        var byId = new HashMap<UUID, EscrowFluidAccount.Row>(); rows.forEach(row -> byId.put(row.operation(), row));
        var expected = new HashSet<UUID>();
        for (var op : records) {
            var row = byId.get(op.id());
            EscrowTags.require((row != null) == (op.accountingBaseline() != null), "Missing or legacy operation row");
            if (row != null) {
                expected.add(op.id());
                EscrowTags.require(op.accountingBaseline().equals(EscrowFluidAccountCodec.baselineDigest(row.baseline()).value())
                        && row.baseline().scope() == (op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL
                        ? EscrowFluidAccount.Scope.FUEL : EscrowFluidAccount.Scope.CARGO), "Baseline reference mismatch");
                var fluid = op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? op.payload() : op.payload().getCompound("fluid");
                EscrowTags.require(row.baseline().moved().equals(EscrowFluidAccountCodec.key(fluid).value())
                        && row.baseline().quantity() == fluid.getInt("Amount"), "Baseline payload mismatch");
                EscrowTags.require(op.settlement().isEmpty() == (row.frozen() == null), "Operation freeze mismatch");
            }
        }
        for (var receipt : receipts) {
            var live = records.stream().filter(op -> op.id().equals(receipt.getUUID("op_id"))).findFirst().orElse(null);
            if (live != null) {
                EscrowTags.require((live.accountingBaseline() != null) == (receipt.getInt("schema_version") == 4), "O3 receipt version mismatch");
            }
            if (receipt.getInt("schema_version") != 4) { continue; }
            var row = byId.get(receipt.getUUID("op_id")); expected.add(receipt.getUUID("op_id"));
            EscrowTags.require(row != null && row.frozen() != null
                    && receipt.getString("accounting_digest").equals(EscrowFluidAccountCodec.digest(row, EscrowFluidAccountCodec.SAVED).value())
                    && receipt.getString("accounting_status").equals(row.knowledge() == EscrowFluidAccount.Knowledge.KNOWN ? "KNOWN" : "UNKNOWN"),
                    "Receipt account mismatch");
        }
        EscrowTags.require(expected.equals(byId.keySet()), "Orphan account row");
        var checked = new HashSet<UUID>();
        for (var row : rows) {
            if (row.frozen() == null || !checked.add(row.frozen().groupOwner())) { continue; }
            var owner = byId.get(row.frozen().groupOwner());
            EscrowTags.require(owner != null && owner.frozen() != null, "Missing account group owner");
            var group = owner.frozen().members().stream().map(byId::get).toList();
            EscrowTags.require(group.stream().allMatch(Objects::nonNull)
                    && rows.stream().filter(r -> r.frozen() != null && r.frozen().groupOwner().equals(owner.operation())).count() == group.size()
                    && EscrowFluidAccount.verifyGroup(group) == EscrowFluidAccount.Status.OK, "Invalid account group");
            var decisions = new HashSet<String>();
            for (var member : group) {
                records.stream().filter(op -> op.id().equals(member.operation()) && !op.settlement().isEmpty())
                        .forEach(op -> decisions.add(EscrowSettlementIntent.decode(op.settlement()).digest()));
                receipts.stream().filter(t -> t.getUUID("op_id").equals(member.operation())).forEach(t -> decisions.add(t.getString("decision_digest")));
            }
            EscrowTags.require(decisions.size() == 1, "Split account group decision");
        }
        var checkedIntents = new HashSet<String>();
        for (var op : records) {
            if (op.settlement().isEmpty()) { continue; }
            var intent = EscrowSettlementIntent.decode(op.settlement());
            if (!checkedIntents.add(intent.digest())) { continue; }
            var component = EscrowSettlementIntent.component(records, op.id());
            var group = component.stream().filter(member -> member.accountingBaseline() != null).map(member -> byId.get(member.id())).toList();
            EscrowTags.require(group.isEmpty() ? intent.accountCommitment() == null
                    : EscrowFluidAccountCodec.commitment(group).equals(intent.accountCommitment()), "Intent account commitment mismatch");
            if (!group.isEmpty()) {
                EscrowTags.require(group.get(0).frozen().members().equals(group.stream().map(EscrowFluidAccount.Row::operation).toList()), "Component row membership mismatch");
                EscrowTags.require(component.stream().noneMatch(EscrowOperation::legacyFluid)
                        || group.stream().allMatch(r -> r.knowledge() == EscrowFluidAccount.Knowledge.UNKNOWN_BASELINE), "Legacy component is not UNKNOWN");
                validateNumericGroup(component, byId, group);
            }
        }
    }

    /** Recompute shared adjustments with the actual typed endpoints, not the per-row diagnostic sums. */
    private static void validateNumericGroup(List<EscrowOperation> component, java.util.Map<UUID, EscrowFluidAccount.Row> rows,
            List<EscrowFluidAccount.Row> group) {
        var members = new ArrayList<EscrowFluidAccount.Member>();
        var finals = new HashMap<EscrowFluidAccount.UnitRef, java.util.Optional<EscrowFluidAccount.Vector>>();
        for (var op : component) {
            String scope = op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? "fuel"
                    : op.kind().fluid() ? EscrowCargoPayload.validateStructure(op.payload()).toString() : "item";
            var source = new EscrowFluidAccount.UnitRef(op.source().key(), op.source().kind() == EscrowEndpoint.Kind.BLOCK_ENTITY ? "tank" : scope);
            var destination = new EscrowFluidAccount.UnitRef(op.destination().key(), op.destination().kind() == EscrowEndpoint.Kind.BLOCK_ENTITY ? "tank" : scope);
            var row = rows.get(op.id());
            members.add(new EscrowFluidAccount.Member(op.id(), op.sequence(), source, destination, row == null ? null : row.baseline(), op.legacyFluid()));
            if (row == null) { continue; }
            for (boolean isSource : List.of(true, false)) {
                var values = row.frozen().entries().stream().map(e -> new EscrowFluidAccount.Unit(e.key(),
                        (isSource ? e.source() : e.destination()).orElse(0))).toList();
                var observed = (isSource ? row.frozen().entries().get(0).source() : row.frozen().entries().get(0).destination()).isPresent()
                        ? java.util.Optional.of(EscrowFluidAccount.Vector.aggregate(values).value()) : java.util.Optional.<EscrowFluidAccount.Vector>empty();
                var previous = finals.putIfAbsent(isSource ? source : destination, observed);
                EscrowTags.require(previous == null || previous.equals(observed), "Split numeric frontier");
            }
        }
        var recomputed = EscrowFluidAccount.settle(members, finals);
        EscrowTags.require(recomputed.ok() && group.stream().allMatch(row -> row.equals(recomputed.value().rows().get(row.operation()))), "Incorrect component balance");
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
