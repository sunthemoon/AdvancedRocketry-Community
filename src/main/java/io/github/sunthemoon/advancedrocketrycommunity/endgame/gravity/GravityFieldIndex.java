package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * ADR-058 section 4: the runtime index of active fields, never persisted. Per Level, a field is listed in every chunk
 * its box touches (at most 3 × 3); a lookup reads only the player's chunk bucket (at most 16 candidates). Admission
 * checks the active caps ({@code ACTIVE_LIMIT}) and then the chunk density ({@code FIELD_DENSITY}).
 */
public final class GravityFieldIndex {
    private final Map<UUID, GravityField> byId = new HashMap<>();
    private final Map<ResourceLocation, Map<Long, List<GravityField>>> buckets = new HashMap<>();

    /** Adds or replaces a field; a refusal leaves the index without it. */
    public EndgameCode add(GravityField field, GravityFieldLimits limits) {
        Objects.requireNonNull(field, "field");
        remove(field.id());
        if (byId.size() >= limits.activeGlobal() || count(field.level()) >= limits.activePerLevel()
                || ownerCount(field.owner()) >= limits.activePerOwner()) {
            return EndgameCode.ACTIVE_LIMIT;
        }
        Map<Long, List<GravityField>> level = buckets.getOrDefault(field.level(), Map.of());
        for (long chunk : chunks(field.box())) {
            List<GravityField> bucket = level.getOrDefault(chunk, List.of());
            long sameOwner = bucket.stream().filter(other -> other.owner().equals(field.owner())).count();
            if (bucket.size() >= limits.perChunk() || sameOwner >= limits.perOwnerPerChunk()) {
                return EndgameCode.FIELD_DENSITY;
            }
        }
        byId.put(field.id(), field);
        Map<Long, List<GravityField>> target = buckets.computeIfAbsent(field.level(), ignored -> new HashMap<>());
        for (long chunk : chunks(field.box())) {
            target.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(field);
        }
        return EndgameCode.OK;
    }

    public void remove(UUID id) {
        GravityField field = byId.remove(id);
        if (field == null) {
            return;
        }
        Map<Long, List<GravityField>> level = buckets.get(field.level());
        for (long chunk : chunks(field.box())) {
            List<GravityField> bucket = level.get(chunk);
            bucket.removeIf(other -> other.id().equals(id));
            if (bucket.isEmpty()) {
                level.remove(chunk);
            }
        }
        if (level.isEmpty()) {
            buckets.remove(field.level());
        }
    }

    public boolean contains(UUID id) {
        return byId.containsKey(id);
    }

    public Optional<GravityField> field(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** The winning field's value for this player, as a multiplier; empty when no field applies. */
    public OptionalDouble at(ResourceLocation level, int x, int y, int z, UUID player, Set<UUID> trusted) {
        Map<Long, List<GravityField>> levelBuckets = buckets.get(level);
        if (levelBuckets == null) {
            return OptionalDouble.empty();
        }
        List<GravityField> bucket = levelBuckets.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (bucket == null) {
            return OptionalDouble.empty();
        }
        return GravityField.winner(bucket, x, y, z, player, trusted)
                .map(field -> OptionalDouble.of(field.effective() / 100.0D)).orElse(OptionalDouble.empty());
    }

    public int size() {
        return byId.size();
    }

    public int count(ResourceLocation level) {
        int count = 0;
        for (GravityField field : byId.values()) {
            count += field.level().equals(level) ? 1 : 0;
        }
        return count;
    }

    public int ownerCount(UUID owner) {
        int count = 0;
        for (GravityField field : byId.values()) {
            count += field.owner().equals(owner) ? 1 : 0;
        }
        return count;
    }

    /** Level unload: every field of that Level leaves the index. */
    public List<UUID> clearLevel(ResourceLocation level) {
        List<UUID> removed = new ArrayList<>();
        for (GravityField field : List.copyOf(byId.values())) {
            if (field.level().equals(level)) {
                remove(field.id());
                removed.add(field.id());
            }
        }
        return removed;
    }

    public void clear() {
        byId.clear();
        buckets.clear();
    }

    private static List<Long> chunks(GravityField.Box box) {
        List<Long> chunks = new ArrayList<>(9);
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return chunks;
    }
}
