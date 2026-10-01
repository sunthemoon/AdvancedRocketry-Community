package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitTags;
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

    /** The transit sections (ADR-054 section 11) of the readable endgame block entities a chunk tag holds. */
    public static Map<UUID, TransitTags.Shown> transit(CompoundTag chunk, Set<String> endgameTypes) {
        Map<UUID, TransitTags.Shown> found = new HashMap<>();
        if (!(chunk.get(BLOCK_ENTITIES) instanceof ListTag list) || list.getElementType() != Tag.TAG_COMPOUND) {
            return found;
        }
        for (int index = 0; index < list.size(); index++) {
            CompoundTag blockEntity = list.getCompound(index);
            if (endgameTypes.contains(blockEntity.getString("id"))
                    && blockEntity.contains(EndgameDeviceTags.ROOT, Tag.TAG_COMPOUND)) {
                CompoundTag root = blockEntity.getCompound(EndgameDeviceTags.ROOT);
                if (root.contains(TransitTags.SECTION, Tag.TAG_COMPOUND)) {
                    readId(blockEntity).ifPresent(id -> found.put(id, TransitTags.scan(root)));
                }
            }
        }
        return found;
    }

    /**
     * Whether the tag shows the block entity with this ID at this position; when it does, whether it must be frozen
     * instead of registered: its root records a freeze (review R4-L3), or it carries outbox entries, incoming payloads
     * or receipts (review R3-H1). Empty when the tag does not show it.
     */
    public static Optional<Boolean> persisted(CompoundTag chunk, Set<String> endgameTypes, UUID id, long pos) {
        if (!(chunk.get(BLOCK_ENTITIES) instanceof ListTag list) || list.getElementType() != Tag.TAG_COMPOUND) {
            return Optional.empty();
        }
        BlockPos block = BlockPos.of(pos);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag blockEntity = list.getCompound(index);
            if (endgameTypes.contains(blockEntity.getString("id")) && blockEntity.getInt("x") == block.getX()
                    && blockEntity.getInt("y") == block.getY() && blockEntity.getInt("z") == block.getZ()
                    && readId(blockEntity).filter(id::equals).isPresent()) {
                CompoundTag root = blockEntity.getCompound(EndgameDeviceTags.ROOT);
                return Optional.of(root.getBoolean(EndgameDeviceTags.FROZEN)
                        || TransitTags.scan(root).holdsContents());
            }
        }
        return Optional.empty();
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
