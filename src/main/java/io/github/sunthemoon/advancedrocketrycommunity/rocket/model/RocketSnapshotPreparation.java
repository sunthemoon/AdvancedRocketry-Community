package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Bounded payload-only reconstruction. Each step is reserved before it reads/copies a block payload. */
public final class RocketSnapshotPreparation {
    public record Work(long bytes, int visits) { }
    private final RocketStructureSnapshot before;
    /** Block index to its replacement; empty when the input is only rehashed. */
    private final TreeMap<Integer, RocketBlock> replacements = new TreeMap<>();
    private final RocketSnapshotHasher.Incremental hashes = new RocketSnapshotHasher.Incremental();
    private final Thread owner = Thread.currentThread();
    private int index = -1;
    private long payloadBytes;
    private RocketSnapshotHasher.Hashes completed;
    private RocketStructureSnapshot result;

    /** Index -1 rehashes unchanged input, including snapshots without payloads. */
    public RocketSnapshotPreparation(RocketStructureSnapshot before, int index, RocketBlockEntityPayload payload) {
        this(before, index == -1 && payload == null ? Map.of() : single(before, index, payload));
    }

    /**
     * W170 D4: several payload-only replacements in one pass (a bay group's consumed roots). Every index names an
     * existing block entity, and no replacement may change its adapter; the snapshot ID, blocks and states stay.
     */
    public RocketSnapshotPreparation(RocketStructureSnapshot before, Map<Integer, RocketBlockEntityPayload> payloads) {
        this.before = Objects.requireNonNull(before);
        for (var entry : payloads.entrySet()) {
            int at = entry.getKey(); RocketBlockEntityPayload payload = Objects.requireNonNull(entry.getValue());
            if (at < 0 || at >= before.blocks().size()) { throw new IllegalArgumentException("Invalid replacement index"); }
            var block = before.blocks().get(at);
            if (!block.blockEntityPayload().orElseThrow().adapterId().equals(payload.adapterId())) {
                throw new IllegalArgumentException("Payload preparation cannot replace the adapter");
            }
            replacements.put(at, new RocketBlock(block.position(), block.state(), payload));
        }
    }

    private static Map<Integer, RocketBlockEntityPayload> single(RocketStructureSnapshot before, int index, RocketBlockEntityPayload payload) {
        if (index < 0 || index >= before.blocks().size() || payload == null) { throw new IllegalArgumentException("Invalid replacement index"); }
        return Map.of(index, payload);
    }

    public int blockIndex() { return Math.max(0, Math.min(index, before.blocks().size())); }
    /** New payload bytes owned by this reconstruction; all other payloads are immutable shared references. */
    public int additionalPayloadBytes() {
        int bytes = 0;
        for (RocketBlock replacement : replacements.values()) { bytes += replacement.blockEntityPayload().orElseThrow().uncompressedBytes(); }
        return bytes;
    }
    public long totalPayloadBytes() {
        if (!done()) { throw new IllegalStateException("Payload validation is incomplete"); }
        return payloadBytes;
    }
    public boolean done() { return result != null; }
    public RocketStructureSnapshot result() {
        if (!done()) { throw new IllegalStateException("Snapshot preparation is incomplete"); }
        return result;
    }
    public int nextVisits() {
        return index >= 0 && index < before.blocks().size() ? 3
                : index == before.blocks().size() + 1 ? 2 * before.blocks().size() : 0;
    }
    public long nextByteBound() {
        if (done()) { return 0; }
        if (index < 0 || index >= before.blocks().size()) { return 65_536; }
        var block = block(index);
        // UTF-8 is at most three bytes per UTF-16 unit. Numeric array SNBT is at most
        // eight times its native size. Include both hashes, copies and the NBT walk.
        return 1_024L + 16L * block.state().canonicalKey().length()
                + block.blockEntityPayload().map(p -> 16L * p.uncompressedBytes()).orElse(0L);
    }
    public Work step() {
        if (Thread.currentThread() != owner || done()) { throw new IllegalStateException("Inactive preparation thread/state"); }
        long old = hashes.bytes(), extra = 0;
        int visits = nextVisits();
        try {
            if (index < 0) { hashes.header(before); }
            else if (index < before.blocks().size()) {
                var block = block(index);
                var payload = block.blockEntityPayload().orElse(null);
                if (payload != null) {
                    payloadBytes += payload.uncompressedBytes();
                    if (payloadBytes > RocketLimits.MAX_TOTAL_NBT_BYTES || !BoundedNbt.fits(payload.data(),
                            RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES, 32, 65_536)) {
                        throw new RocketSnapshotException(
                                io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE,
                                "Cargo payload envelope exceeded");
                    }
                    // Validation copy/walk and the hasher's defensive copy. Hash writes are counted below.
                    extra = 3L * payload.uncompressedBytes();
                }
                hashes.block(block);
            } else if (index == before.blocks().size()) {
                completed = hashes.finish(before);
                extra = before.propulsionSignature() == null ? 0 : 65L + before.propulsionSignature().length();
            } else {
                var blocks = new ArrayList<>(before.blocks());
                replacements.forEach(blocks::set);
                result = new RocketStructureSnapshot(before, blocks, completed);
                extra = 16L * blocks.size(); // Two bounded reference-array copies, no payload copies.
            }
            index++;
            return new Work(hashes.bytes() - old + extra, visits);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private RocketBlock block(int at) { return replacements.getOrDefault(at, before.blocks().get(at)); }
}
