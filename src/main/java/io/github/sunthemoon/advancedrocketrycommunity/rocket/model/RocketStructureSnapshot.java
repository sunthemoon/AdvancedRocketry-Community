package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable schema-1 server snapshot. Block positions are relative to sourceOrigin. */
public final class RocketStructureSnapshot {
    private final int schemaVersion;
    private final UUID snapshotId;
    private final ResourceLocation sourceDimension;
    private final RocketPosition sourceOrigin;
    private final RocketBounds bounds;
    private final List<RocketBlock> blocks;
    private final List<RocketPosition> passengerAnchors;
    private final RocketStats stats;
    private final long createdAtGameTime;
    private final String contentHash;

    private RocketStructureSnapshot(
            UUID snapshotId,
            ResourceLocation sourceDimension,
            RocketPosition sourceOrigin,
            List<RocketBlock> blocks,
            List<RocketPosition> passengerAnchors,
            RocketStats stats,
            long createdAtGameTime
    ) {
        schemaVersion = RocketLimits.SNAPSHOT_SCHEMA_VERSION;
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.sourceDimension = Objects.requireNonNull(sourceDimension, "sourceDimension");
        this.sourceOrigin = Objects.requireNonNull(sourceOrigin, "sourceOrigin");
        this.stats = Objects.requireNonNull(stats, "stats");
        if (sourceDimension.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw new RocketSnapshotException(
                    RocketValidationCode.MALFORMED_SNAPSHOT,
                    "Source dimension identifier exceeds the fixed length limit"
            );
        }
        if (createdAtGameTime < 0L) {
            throw new RocketSnapshotException(
                    RocketValidationCode.MALFORMED_SNAPSHOT,
                    "Snapshot game time must not be negative"
            );
        }
        this.createdAtGameTime = createdAtGameTime;

        Objects.requireNonNull(blocks, "blocks");
        if (blocks.isEmpty()) {
            throw new RocketSnapshotException(
                    RocketValidationCode.EMPTY_STRUCTURE,
                    "A rocket snapshot must contain at least one block"
            );
        }
        if (blocks.size() > RocketLimits.MAX_BLOCKS) {
            throw new RocketSnapshotException(
                    RocketValidationCode.TOO_MANY_BLOCKS,
                    "Rocket contains " + blocks.size() + " blocks; limit is " + RocketLimits.MAX_BLOCKS
            );
        }

        ArrayList<RocketBlock> sortedBlocks = new ArrayList<>(blocks);
        sortedBlocks.sort(RocketBlock::compareTo);
        Set<RocketPosition> positions = new HashSet<>();
        Set<RocketBlockState> palette = new HashSet<>();
        int blockEntities = 0;
        for (RocketBlock block : sortedBlocks) {
            Objects.requireNonNull(block, "block");
            if (!positions.add(block.position())) {
                throw new RocketSnapshotException(
                        RocketValidationCode.DUPLICATE_BLOCK_POSITION,
                        block.position(),
                        "Rocket snapshot contains a duplicate relative position"
                );
            }
            sourceOrigin.add(block.position());
            palette.add(block.state());
            blockEntities += block.blockEntityPayload().isPresent() ? 1 : 0;
        }
        if (palette.size() > RocketLimits.MAX_PALETTE_ENTRIES) {
            throw new RocketSnapshotException(
                    RocketValidationCode.TOO_MANY_PALETTE_ENTRIES,
                    "Rocket palette exceeds " + RocketLimits.MAX_PALETTE_ENTRIES
            );
        }
        if (blockEntities > RocketLimits.MAX_BLOCK_ENTITIES) {
            throw new RocketSnapshotException(
                    RocketValidationCode.TOO_MANY_BLOCK_ENTITIES,
                    "Rocket contains too many BlockEntities"
            );
        }
        this.blocks = List.copyOf(sortedBlocks);
        bounds = RocketBounds.enclosing(positions);

        Objects.requireNonNull(passengerAnchors, "passengerAnchors");
        ArrayList<RocketPosition> sortedAnchors = new ArrayList<>(passengerAnchors);
        sortedAnchors.sort(RocketPosition::compareTo);
        Set<RocketPosition> uniqueAnchors = new HashSet<>();
        for (RocketPosition anchor : sortedAnchors) {
            Objects.requireNonNull(anchor, "passengerAnchor");
            if (!positions.contains(anchor) || !uniqueAnchors.add(anchor)) {
                throw new RocketSnapshotException(
                        RocketValidationCode.STATS_MISMATCH,
                        anchor,
                        "Passenger anchors must be unique rocket block positions"
                );
            }
        }
        this.passengerAnchors = List.copyOf(sortedAnchors);

        if (stats.blockCount() != this.blocks.size()
                || stats.blockEntityCount() != blockEntities
                || stats.seatCount() != this.passengerAnchors.size()) {
            throw new RocketSnapshotException(
                    RocketValidationCode.STATS_MISMATCH,
                    "Snapshot statistics do not match captured structure counts"
            );
        }
        contentHash = RocketSnapshotHasher.hash(
                schemaVersion,
                sourceDimension,
                sourceOrigin,
                bounds,
                this.blocks,
                this.passengerAnchors,
                stats
        );
    }

    public static RocketStructureSnapshot create(
            UUID snapshotId,
            ResourceLocation sourceDimension,
            RocketPosition sourceOrigin,
            List<RocketBlock> blocks,
            List<RocketPosition> passengerAnchors,
            RocketStats stats,
            long createdAtGameTime
    ) {
        return new RocketStructureSnapshot(
                snapshotId,
                sourceDimension,
                sourceOrigin,
                blocks,
                passengerAnchors,
                stats,
                createdAtGameTime
        );
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public UUID snapshotId() {
        return snapshotId;
    }

    public ResourceLocation sourceDimension() {
        return sourceDimension;
    }

    public RocketPosition sourceOrigin() {
        return sourceOrigin;
    }

    public RocketBounds bounds() {
        return bounds;
    }

    public List<RocketBlock> blocks() {
        return blocks;
    }

    public List<RocketPosition> passengerAnchors() {
        return passengerAnchors;
    }

    public RocketStats stats() {
        return stats;
    }

    public long createdAtGameTime() {
        return createdAtGameTime;
    }

    public String contentHash() {
        return contentHash;
    }

    /** Creates a location-bound identity for the same immutable block payload. */
    public RocketStructureSnapshot relocated(
            UUID relocatedSnapshotId,
            ResourceLocation destinationDimension,
            RocketPosition destinationOrigin,
            long relocatedAtGameTime
    ) {
        return create(
                Objects.requireNonNull(relocatedSnapshotId, "relocatedSnapshotId"),
                Objects.requireNonNull(destinationDimension, "destinationDimension"),
                Objects.requireNonNull(destinationOrigin, "destinationOrigin"),
                blocks,
                passengerAnchors,
                stats,
                relocatedAtGameTime
        );
    }
}
