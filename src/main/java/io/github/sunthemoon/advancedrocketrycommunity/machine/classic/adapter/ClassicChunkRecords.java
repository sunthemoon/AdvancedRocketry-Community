package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Bounded raw metadata only; a record is not FULL installation or resource authority. */
final class ClassicChunkRecords {
    private static final Set<String> TYPES = Set.of("advancedrocketrycommunity:classic_hatch",
            "advancedrocketrycommunity:lathe");
    private static final Set<String> ROOTS = Set.of("arce_classic_hatch", "arce_classic_resources",
            "arce_classic_machine");

    /** Exact signed coordinates, without the aliases of a packed BlockPos long. */
    record Position(int x, int y, int z) { }

    record Scan(boolean refused, Map<Position, String> records) {
        Scan { records = Map.copyOf(records); }
        private static Scan refusal() { return new Scan(true, Map.of()); }
    }

    static Scan read(CompoundTag parent, int chunkX, int chunkZ, int minimumY, int height) {
        long maximumY = (long) minimumY + height;
        long maximumRecords = (long) height * 256;
        if (parent == null || parent.getClass() != CompoundTag.class || height <= 0
                || maximumY > Integer.MAX_VALUE || maximumRecords > Integer.MAX_VALUE) {
            return Scan.refusal();
        }
        Tag raw = parent.get("block_entities");
        if (raw == null || raw.getClass() != ListTag.class) { return Scan.refusal(); }
        ListTag list = (ListTag) raw;
        int count = list.size();
        if (count > maximumRecords || (count != 0 && list.getElementType() != Tag.TAG_COMPOUND)
                || (count == 0 && list.getElementType() != Tag.TAG_END && list.getElementType() != Tag.TAG_COMPOUND)) {
            return Scan.refusal();
        }
        Map<Position, String> records = new HashMap<>();
        Set<Position> positions = new HashSet<>();
        for (int index = 0; index < count; index++) {
            Tag element = list.get(index);
            if (element == null || element.getClass() != CompoundTag.class) { return Scan.refusal(); }
            CompoundTag entry = (CompoundTag) element;
            Tag id = entry.get("id");
            Tag x = entry.get("x"), y = entry.get("y"), z = entry.get("z");
            if (id == null || id.getClass() != StringTag.class || x == null || x.getClass() != IntTag.class
                    || y == null || y.getClass() != IntTag.class || z == null || z.getClass() != IntTag.class) {
                return Scan.refusal();
            }
            Position position = new Position(((IntTag) x).getAsInt(), ((IntTag) y).getAsInt(), ((IntTag) z).getAsInt());
            if ((position.x() >> 4) != chunkX || (position.z() >> 4) != chunkZ
                    || position.y() < minimumY || position.y() >= maximumY || !positions.add(position)) {
                return Scan.refusal();
            }
            String type = ((StringTag) id).getAsString();
            if (TYPES.contains(type)) { records.put(position, type); }
            else {
                // Presence under the wrong BE ID is protected, regardless of the root value/type.
                for (String root : ROOTS) { if (entry.contains(root)) { return Scan.refusal(); } }
            }
        }
        // Only scalars survive. No NBT reference, world query, decoder, normalization or partial result.
        return new Scan(false, records);
    }

    private ClassicChunkRecords() { }
}
