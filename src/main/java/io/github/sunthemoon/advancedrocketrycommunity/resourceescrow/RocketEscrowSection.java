package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Schema-1 rocket envelope. Absence of ledger_id is canonical only before the first operation. */
public record RocketEscrowSection(UUID ledger, long epoch, List<EscrowPrefix.Marker> markers) {
    public RocketEscrowSection {
        markers = List.copyOf(markers);
        EscrowTags.require(epoch >= 0 && markers.size() <= EscrowLimits.PER_ROCKET, "Invalid rocket escrow counters");
        EscrowTags.require(ledger != null || epoch == 0 && markers.isEmpty(), "Only a pristine endpoint may be unbound");
        var ids = new HashSet<UUID>();
        long previousSequence = 0;
        long previousEpoch = epoch - markers.size();
        for (EscrowPrefix.Marker marker : markers) {
            EscrowTags.require(ids.add(marker.operation()) && marker.sequence() > previousSequence
                    && marker.epochAfter() == previousEpoch + 1 && marker.role().equals("DESTINATION"),
                    "Invalid rocket marker prefix");
            previousSequence = marker.sequence();
            previousEpoch = marker.epochAfter();
        }
    }

    public static RocketEscrowSection neutral() { return new RocketEscrowSection(null, 0, List.of()); }

    /** Binding is part of first acknowledged L1 registration; it does not create a ledger. */
    public RocketEscrowSection bind(UUID actualLedger) {
        java.util.Objects.requireNonNull(actualLedger);
        EscrowTags.require(ledger == null || ledger.equals(actualLedger), "Foreign bound rocket ledger");
        return new RocketEscrowSection(actualLedger, epoch, markers);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", 1);
        if (ledger != null) { tag.putUUID("ledger_id", ledger); }
        tag.putLong("resource_epoch", epoch);
        ListTag list = new ListTag();
        for (EscrowPrefix.Marker marker : markers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("op_id", marker.operation()); entry.putLong("seq", marker.sequence());
            entry.putString("role", marker.role()); entry.putLong("epoch_after", marker.epochAfter());
            list.add(entry);
        }
        tag.put("markers", list);
        EscrowTags.bound(tag, EscrowLimits.ENTITY_MARKER_BYTES, 8, 128);
        return tag;
    }

    public static RocketEscrowSection decode(CompoundTag tag) {
        EscrowTags.bound(tag, EscrowLimits.ENTITY_MARKER_BYTES, 8, 128);
        EscrowTags.fields(tag, "schema_version", "ledger_id", "resource_epoch", "markers");
        EscrowTags.require(EscrowTags.integer(tag, "schema_version") == 1, "Unsupported rocket escrow schema");
        var markers = new ArrayList<EscrowPrefix.Marker>();
        for (var raw : EscrowTags.list(tag, "markers", EscrowLimits.PER_ROCKET)) {
            CompoundTag entry = (CompoundTag) raw;
            EscrowTags.fields(entry, "op_id", "seq", "role", "epoch_after");
            markers.add(new EscrowPrefix.Marker(EscrowTags.uuid(entry, "op_id"), EscrowTags.number(entry, "seq"),
                    EscrowTags.string(entry, "role"), EscrowTags.number(entry, "epoch_after")));
        }
        return new RocketEscrowSection(tag.contains("ledger_id") ? EscrowTags.uuid(tag, "ledger_id") : null,
                EscrowTags.number(tag, "resource_epoch"), markers);
    }
}
