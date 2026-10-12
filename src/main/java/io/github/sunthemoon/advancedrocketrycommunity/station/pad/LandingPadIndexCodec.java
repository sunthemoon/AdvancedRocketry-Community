package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Independent schema 1, native preflight before allocation/copy, canonical fields and bounded buckets. */
public final class LandingPadIndexCodec {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_STATIONS = 4096;
    public static final int MAX_PADS = 8;
    private static final Set<String> FIELDS = Set.of("station", "pad", "physical", "owner", "name",
            "x", "y", "z", "epoch", "claim");

    private LandingPadIndexCodec() { }

    public record Decoded(long nextEpoch, List<LandingPadEntry> entries) { }

    public static CompoundTag encode(long nextEpoch, List<LandingPadEntry> entries, boolean reserveClaims) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 1); root.putLong("next_epoch", nextEpoch);
        ListTag pads = new ListTag();
        for (LandingPadEntry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("station", entry.stationId()); tag.putUUID("pad", entry.padId());
            tag.putUUID("physical", entry.physicalId()); tag.putUUID("owner", entry.ownerId());
            tag.putString("name", entry.name()); tag.putInt("x", entry.localPosition().x());
            tag.putInt("y", entry.localPosition().y()); tag.putInt("z", entry.localPosition().z());
            tag.putLong("epoch", entry.epoch());
            if (entry.claim() != null || reserveClaims) {
                tag.putUUID("claim", entry.claim() == null ? new UUID(0, 0) : entry.claim());
            }
            pads.add(tag);
        }
        root.put("pads", pads);
        return root;
    }

    public static boolean fits(CompoundTag tag) { return BoundedNbt.fits(tag, MAX_BYTES, 5, 500_000); }

    public static Decoded decode(CompoundTag root) {
        if (!fits(root) || !root.getAllKeys().equals(Set.of("schema", "next_epoch", "pads"))
                || !root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != 1
                || !root.contains("next_epoch", Tag.TAG_LONG) || root.getLong("next_epoch") < 0
                || !(root.get("pads") instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > MAX_STATIONS * MAX_PADS) {
            throw new IllegalArgumentException("Unsupported or oversized landing-pad root");
        }
        List<LandingPadEntry> entries = new ArrayList<>(list.size());
        var counts = new HashMap<UUID, Integer>();
        var ids = new HashSet<UUID>();
        var physicalIds = new HashSet<UUID>();
        var positions = new HashSet<String>();
        long next = root.getLong("next_epoch");
        for (Tag value : list) {
            CompoundTag tag = (CompoundTag) value;
            if (!FIELDS.containsAll(tag.getAllKeys()) || tag.size() != (tag.contains("claim") ? 10 : 9)
                    || !tag.contains("name", Tag.TAG_STRING) || !tag.contains("epoch", Tag.TAG_LONG)
                    || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT)
                    || !tag.contains("z", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Noncanonical landing-pad record");
            }
            LandingPadEntry entry = new LandingPadEntry(uuid(tag, "station"), uuid(tag, "pad"),
                    uuid(tag, "physical"), uuid(tag, "owner"), tag.getString("name"),
                    new StationPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                    tag.getLong("epoch"), tag.contains("claim") ? uuid(tag, "claim") : null);
            if (entry.epoch() >= next || !ids.add(entry.padId()) || !physicalIds.add(entry.physicalId())
                    || !positions.add(entry.stationId() + ":" + entry.localPosition())
                    || counts.merge(entry.stationId(), 1, Integer::sum) > MAX_PADS || counts.size() > MAX_STATIONS) {
                throw new IllegalArgumentException("Duplicate, stale or over-capacity pad index");
            }
            entries.add(entry);
        }
        if (!fits(encode(next, entries, true))) {
            throw new IllegalArgumentException("Landing-pad root lacks room for bounded claims");
        }
        return new Decoded(next, List.copyOf(entries));
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) { throw new IllegalArgumentException("Invalid pad UUID: " + key); }
        return tag.getUUID(key);
    }
}
