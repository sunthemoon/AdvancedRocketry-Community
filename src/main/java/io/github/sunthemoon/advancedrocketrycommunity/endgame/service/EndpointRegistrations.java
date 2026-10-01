package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.Tombstone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * ADR-054 section 9 registration: a loaded endpoint without an index record waits as {@code AWAITING_WORLD_SAVE}
 * until a chunk-save or chunk-load tag of its chunk shows its ID at its position; then at most 32 registrations per
 * tick run against the root, reading the tag's freeze flag (review R3-H1, R4-L3). Limits and a full root are retried
 * at the next save; a registered, retired or conflicting ID stops waiting. Candidates are an immutable snapshot, so
 * chunk-load threads read them safely; only the server thread changes them.
 */
final class EndpointRegistrations {
    private volatile Map<EndgameService.ChunkKey, Map<UUID, Candidate>> byChunk = Map.of();
    private final Queue<Request> requests = new ConcurrentLinkedQueue<>();
    private final Map<UUID, EndgameCode> results = new HashMap<>();

    /** A loaded endpoint block entity that has no index record yet. */
    record Candidate(UUID id, ResourceLocation kind, UUID owner, ResourceLocation level, long pos) {
        Candidate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(level, "level");
        }

        EndgameService.ChunkKey chunk() {
            BlockPos block = BlockPos.of(pos);
            return new EndgameService.ChunkKey(Tombstone.hash(level), ChunkPos.asLong(block.getX() >> 4,
                    block.getZ() >> 4));
        }
    }

    record Request(Candidate candidate, boolean frozen) {
    }

    record Result(Candidate candidate, EndgameCode code) {
    }

    void await(Candidate candidate) {
        Map<UUID, Candidate> current = byChunk.getOrDefault(candidate.chunk(), Map.of());
        if (candidate.equals(current.get(candidate.id()))) {
            return;
        }
        Map<EndgameService.ChunkKey, Map<UUID, Candidate>> next = new HashMap<>(byChunk);
        Map<UUID, Candidate> chunk = new HashMap<>(current);
        chunk.put(candidate.id(), candidate);
        next.put(candidate.chunk(), Map.copyOf(chunk));
        byChunk = Map.copyOf(next);
    }

    void forget(UUID id) {
        Map<EndgameService.ChunkKey, Map<UUID, Candidate>> next = new HashMap<>();
        boolean changed = false;
        for (Map.Entry<EndgameService.ChunkKey, Map<UUID, Candidate>> entry : byChunk.entrySet()) {
            if (entry.getValue().containsKey(id)) {
                changed = true;
                Map<UUID, Candidate> chunk = new HashMap<>(entry.getValue());
                chunk.remove(id);
                if (!chunk.isEmpty()) {
                    next.put(entry.getKey(), Map.copyOf(chunk));
                }
            } else {
                next.put(entry.getKey(), entry.getValue());
            }
        }
        if (changed) {
            byChunk = Map.copyOf(next);
        }
    }

    boolean watches(EndgameService.ChunkKey key) {
        return byChunk.containsKey(key);
    }

    /** Any thread: queues the candidates this chunk tag shows persisted, with the tag's freeze flag. */
    void observe(EndgameService.ChunkKey key, CompoundTag tag, Set<String> endgameTypes) {
        Map<UUID, Candidate> candidates = byChunk.get(key);
        if (candidates == null) {
            return;
        }
        for (Candidate candidate : candidates.values()) {
            EndpointObservations.persisted(tag, endgameTypes, candidate.id(), candidate.pos())
                    .ifPresent(frozen -> requests.add(new Request(candidate, frozen)));
        }
    }

    /** Server thread: registers at most 32 queued candidates that still wait. */
    List<Result> drain(BiFunction<Candidate, Boolean, EndgameCode> register) {
        List<Result> done = new ArrayList<>();
        for (Request request; done.size() < EndgameLimits.REGISTRATIONS_PER_TICK && (request = requests.poll()) != null; ) {
            Candidate candidate = request.candidate();
            if (!candidate.equals(byChunk.getOrDefault(candidate.chunk(), Map.of()).get(candidate.id()))) {
                continue;
            }
            EndgameCode code = register.apply(candidate, request.frozen());
            results.put(candidate.id(), code);
            if (code != EndgameCode.ENDPOINT_LIMIT && code != EndgameCode.ROOT_FULL) {
                forget(candidate.id());
            }
            done.add(new Result(candidate, code));
        }
        return done;
    }

    Optional<EndgameCode> result(UUID id) {
        return Optional.ofNullable(results.get(id));
    }

    int waiting() {
        int count = 0;
        for (Map<UUID, Candidate> chunk : byChunk.values()) {
            count += chunk.size();
        }
        return count;
    }

    void clear() {
        byChunk = Map.of();
        requests.clear();
        results.clear();
    }
}
