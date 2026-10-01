package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ADR-054 section 2.1: the pattern boxes of loaded endgame controllers, indexed by chunk, so a block change inside a
 * box marks that controller for re-validation without scanning other controllers. Controllers register while loaded
 * and unregister when unloaded or removed.
 */
public final class EndgameStructureTracker {
    private final Map<Key, BoundingBox> boxes = new HashMap<>();
    private final Map<ChunkKey, Set<Key>> byChunk = new HashMap<>();
    private final Set<Key> dirty = new HashSet<>();

    public void track(ResourceKey<Level> level, BlockPos controller, BoundingBox box) {
        Key key = new Key(level, controller.immutable());
        untrack(level, controller);
        boxes.put(key, Objects.requireNonNull(box, "box"));
        forEachChunk(level, box, chunk -> byChunk.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(key));
        dirty.add(key);
    }

    public void untrack(ResourceKey<Level> level, BlockPos controller) {
        Key key = new Key(level, controller);
        BoundingBox box = boxes.remove(key);
        dirty.remove(key);
        if (box != null) {
            forEachChunk(level, box, chunk -> {
                Set<Key> keys = byChunk.get(chunk);
                if (keys != null) {
                    keys.remove(key);
                    if (keys.isEmpty()) {
                        byChunk.remove(chunk);
                    }
                }
            });
        }
    }

    /** A block inside some controllers' boxes changed. */
    public void changed(ResourceKey<Level> level, BlockPos position) {
        Set<Key> keys = byChunk.get(new ChunkKey(level, ChunkPos.asLong(SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()))));
        if (keys == null) {
            return;
        }
        for (Key key : keys) {
            if (boxes.get(key).isInside(position)) {
                dirty.add(key);
            }
        }
    }

    public boolean consumeDirty(ResourceKey<Level> level, BlockPos controller) {
        return dirty.remove(new Key(level, controller));
    }

    public int tracked() {
        return boxes.size();
    }

    public void clear() {
        boxes.clear();
        byChunk.clear();
        dirty.clear();
    }

    private static void forEachChunk(ResourceKey<Level> level, BoundingBox box, java.util.function.Consumer<ChunkKey> action) {
        for (int x = SectionPos.blockToSectionCoord(box.minX()); x <= SectionPos.blockToSectionCoord(box.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ()); z <= SectionPos.blockToSectionCoord(box.maxZ()); z++) {
                action.accept(new ChunkKey(level, ChunkPos.asLong(x, z)));
            }
        }
    }

    private record Key(ResourceKey<Level> level, BlockPos controller) {
    }

    private record ChunkKey(ResourceKey<Level> level, long chunk) {
    }
}
