package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** BlockEntity-free client display projection; never used for server authority. */
public final class RocketVisualSnapshot {
    private final UUID snapshotId;
    private final String structureContentHash;
    private final List<RocketVisualBlock> blocks;
    private final RocketBounds bounds;

    public RocketVisualSnapshot(
            UUID snapshotId,
            String structureContentHash,
            List<RocketVisualBlock> blocks
    ) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.structureContentHash = Objects.requireNonNull(structureContentHash, "structureContentHash");
        if (!structureContentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Visual snapshot structure hash must be lowercase SHA-256");
        }
        Objects.requireNonNull(blocks, "blocks");
        if (blocks.isEmpty() || blocks.size() > RocketLimits.MAX_BLOCKS) {
            throw new IllegalArgumentException("Visual snapshot block count is outside the fixed limit");
        }
        ArrayList<RocketVisualBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(RocketVisualBlock::compareTo);
        Set<RocketPosition> positions = new HashSet<>();
        for (RocketVisualBlock block : sorted) {
            if (!positions.add(Objects.requireNonNull(block, "block").position())) {
                throw new IllegalArgumentException("Visual snapshot contains duplicate block positions");
            }
        }
        this.blocks = List.copyOf(sorted);
        bounds = RocketBounds.enclosing(positions);
    }

    public static RocketVisualSnapshot fromServerSnapshot(RocketStructureSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new RocketVisualSnapshot(
                snapshot.snapshotId(),
                snapshot.contentHash(),
                snapshot.blocks().stream()
                        .map(RocketVisualSnapshot::visualBlock)
                        .toList()
        );
    }

    public UUID snapshotId() {
        return snapshotId;
    }

    public String structureContentHash() {
        return structureContentHash;
    }

    public List<RocketVisualBlock> blocks() {
        return blocks;
    }

    public RocketBounds bounds() {
        return bounds;
    }

    private static RocketVisualBlock visualBlock(RocketBlock block) {
        return new RocketVisualBlock(block.position(), block.state());
    }
}
