package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * ADR-054 sections 2 and 9: presence and absence of endgame block entities as a chunk-save or chunk-load tag shows
 * them. A block entity of an endgame type at a position counts as present for an ID unless its root names a
 * different, readable device ID; a quarantined or unreadable root is present (review R3-L1).
 */
public final class EndpointObservations {
    private static final String BLOCK_ENTITIES = "block_entities";

    private EndpointObservations() {
    }

    /** The endgame block entities of a chunk tag by position; an unreadable device ID maps to empty. */
    public static Map<Long, Optional<UUID>> scan(CompoundTag chunk, Set<String> endgameTypes) {
        Map<Long, Optional<UUID>> found = new HashMap<>();
        if (!(chunk.get(BLOCK_ENTITIES) instanceof ListTag list) || list.getElementType() != Tag.TAG_COMPOUND) {
            return found;
        }
        for (int index = 0; index < list.size(); index++) {
            CompoundTag blockEntity = list.getCompound(index);
            if (!endgameTypes.contains(blockEntity.getString("id"))) {
                continue;
            }
            long pos = new BlockPos(blockEntity.getInt("x"), blockEntity.getInt("y"), blockEntity.getInt("z")).asLong();
            found.put(pos, readId(blockEntity));
        }
        return found;
    }

    public static boolean present(Map<Long, Optional<UUID>> scan, UUID id, long pos) {
        Optional<UUID> atPosition = scan.get(pos);
        return atPosition != null && (atPosition.isEmpty() || atPosition.get().equals(id));
    }

    private static Optional<UUID> readId(CompoundTag blockEntity) {
        if (!blockEntity.contains(EndgameDeviceTags.ROOT, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag root = blockEntity.getCompound(EndgameDeviceTags.ROOT);
        if (!root.contains(EndgameDeviceTags.DEVICE_ID, Tag.TAG_INT_ARRAY)
                || root.getIntArray(EndgameDeviceTags.DEVICE_ID).length != 4) {
            return Optional.empty();
        }
        return Optional.of(NbtUtils.loadUUID(root.get(EndgameDeviceTags.DEVICE_ID)));
    }
}
