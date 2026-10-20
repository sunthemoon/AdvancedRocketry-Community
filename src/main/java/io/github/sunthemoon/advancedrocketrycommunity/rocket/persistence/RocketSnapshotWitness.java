package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Verifies an encoded round trip against already hashed immutable input, without hashing it again. */
public final class RocketSnapshotWitness {
    private RocketSnapshotWitness() { }

    public static boolean verifies(CompoundTag source, RocketStructureSnapshot expected) {
        java.util.Objects.requireNonNull(expected);
        return RocketSnapshotNbtCodec.decode(source, expected).snapshot().filter(value -> value == expected).isPresent()
                && canonicalEncoding(source, expected);
    }

    /** Fields normalized by ordinary decode must still match an encode/decode/encode round trip exactly. */
    private static boolean canonicalEncoding(CompoundTag root, RocketStructureSnapshot expected) {
        if (!root.getString("snapshot_id").equals(expected.snapshotId().toString())
                || !root.getString("source_dimension").equals(expected.sourceDimension().toString())
                || !root.getCompound("mass_inputs").getAllKeys().equals(Set.of("block_count", "mass", "thrust", "fuel_capacity",
                        "engine_count", "seat_count", "guidance_count", "block_entity_count"))) { return false; }
        if (expected.propulsion().isPresent()
                && !RocketPropulsionCodec.encode(expected.propulsion().orElseThrow()).equals(root.getCompound("propulsion"))) { return false; }
        var palette = root.getList("block_palette", Tag.TAG_COMPOUND);
        var used = new java.util.BitSet(palette.size());
        for (var raw : palette) {
            var entry = (CompoundTag) raw;
            if (!entry.getAllKeys().equals(Set.of("id", "properties")) || !canonicalId(entry.getString("id"))) { return false; }
        }
        for (var raw : root.getList("relative_blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) raw; used.set(block.getInt("palette"));
            if (!block.getAllKeys().equals(block.contains("block_entity") ? Set.of("position", "palette", "block_entity")
                    : Set.of("position", "palette"))) { return false; }
            if (block.contains("block_entity")) {
                var payload = block.getCompound("block_entity");
                if (!payload.getAllKeys().equals(Set.of("adapter", "data")) || !canonicalId(payload.getString("adapter"))) { return false; }
            }
        }
        return used.cardinality() == palette.size();
    }

    private static boolean canonicalId(String text) {
        var id = ResourceLocation.tryParse(text); return id != null && id.toString().equals(text);
    }
}
