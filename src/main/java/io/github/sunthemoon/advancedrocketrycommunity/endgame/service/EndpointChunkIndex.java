package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.Tombstone;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * ADR-054 sections 9 and 11: which ACTIVE endpoints and tombstones lie in which chunk, so the service scans a chunk
 * tag only when it can show one of them. Each chunk entry is an immutable set in a concurrent map, so chunk-load
 * threads read single entries; only the server thread changes it, moving just the IDs a root mutation touched
 * (review C11R-M5). Split out of {@link EndgameService} for the AGENTS §3.4 class-size check (review C11R2-L2).
 */
final class EndpointChunkIndex {
    private final Map<ChunkKey, Set<UUID>> index = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkKey> indexedAt = new HashMap<>();

    /**
     * A chunk of one Level. Live records and young tombstones are keyed by the full Level key, so two Levels whose
     * keys share a hash never see each other's chunks (review C11R-L4); a settled tombstone keeps only the hash and is
     * keyed by it ({@code level} null), which only housekeeping reads.
     */
    record ChunkKey(@Nullable ResourceLocation level, int levelHash, long chunk) {
        static ChunkKey exact(ResourceLocation level, long chunk) {
            return new ChunkKey(level, Tombstone.hash(level), chunk);
        }

        ChunkKey hashed() {
            return level == null ? this : new ChunkKey(null, levelHash, chunk);
        }
    }

    void clear() {
        index.clear();
        indexedAt.clear();
    }

    /** The whole index, built when the root is loaded: ACTIVE records and every tombstone, by chunk. */
    void rebuild(EndgameRoot root) {
        clear();
        root.drainTouched();
        Map<ChunkKey, Set<UUID>> next = new HashMap<>();
        for (EndpointRecord record : root.endpoints()) {
            keyOf(root, record.id()).ifPresent(key -> place(next, key, record.id()));
        }
        root.youngTombstones().forEach(tombstone -> keyOf(root, tombstone.id())
                .ifPresent(key -> place(next, key, tombstone.id())));
        root.settledTombstones().forEach(tombstone -> keyOf(root, tombstone.id())
                .ifPresent(key -> place(next, key, tombstone.id())));
        next.forEach((key, ids) -> index.put(key, Set.copyOf(ids)));
    }

    private void place(Map<ChunkKey, Set<UUID>> next, ChunkKey key, UUID id) {
        next.computeIfAbsent(key, ignored -> new HashSet<>()).add(id);
        indexedAt.put(id, key);
    }

    /** After a mutation only the IDs it touched move between chunk entries. */
    void update(EndgameRoot root) {
        for (UUID id : root.drainTouched()) {
            ChunkKey now = keyOf(root, id).orElse(null);
            ChunkKey before = now == null ? indexedAt.remove(id) : indexedAt.put(id, now);
            if (Objects.equals(before, now)) {
                continue;
            }
            if (before != null) {
                index.computeIfPresent(before, (key, ids) -> {
                    Set<UUID> rest = new HashSet<>(ids);
                    rest.remove(id);
                    return rest.isEmpty() ? null : Set.copyOf(rest);
                });
            }
            if (now != null) {
                index.merge(now, Set.of(id), (ids, added) -> {
                    Set<UUID> all = new HashSet<>(ids);
                    all.addAll(added);
                    return Set.copyOf(all);
                });
            }
        }
    }

    /** Any thread: whether a tag of this chunk can show an indexed ID. */
    boolean watches(ChunkKey key) {
        return index.containsKey(key) || index.containsKey(key.hashed());
    }

    /** The IDs a tag of this chunk can show: exact entries and settled tombstones by hash. */
    Set<UUID> idsAt(ChunkKey key) {
        Set<UUID> ids = new HashSet<>(index.getOrDefault(key, Set.of()));
        ids.addAll(index.getOrDefault(key.hashed(), Set.of()));
        return ids;
    }

    Map<ChunkKey, Set<UUID>> snapshot() {
        return Map.copyOf(index);
    }

    /** The chunk an ID is indexed under; none for MISSING or unknown IDs. */
    static Optional<ChunkKey> keyOf(EndgameRoot root, UUID id) {
        Optional<EndpointRecord> record = root.endpoint(id);
        if (record.isPresent()) {
            return record.get().state() == EndpointRecord.State.ACTIVE
                    ? Optional.of(ChunkKey.exact(record.get().level(), chunkOf(record.get().pos()))) : Optional.empty();
        }
        return root.tombstone(id).map(tombstone -> tombstone instanceof Tombstone.Young young
                ? ChunkKey.exact(young.level(), chunkOf(young.pos()))
                : new ChunkKey(null, tombstone.levelHash(), chunkOf(tombstone.pos())));
    }

    static long chunkOf(long pos) {
        BlockPos block = BlockPos.of(pos);
        return ChunkPos.asLong(block.getX() >> 4, block.getZ() >> 4);
    }
}
