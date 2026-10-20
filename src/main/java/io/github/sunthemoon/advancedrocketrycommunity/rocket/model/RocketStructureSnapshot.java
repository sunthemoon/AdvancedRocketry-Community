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
    private final RocketPropulsion propulsion;
    private final String propulsionSignature;
    private final String structureDigest;
    private final int structureDigestBytes;

    /** Package-private completion of the payload-only cursor; no public digest injection path. */
    RocketStructureSnapshot(RocketStructureSnapshot source, List<RocketBlock> blocks, RocketSnapshotHasher.Hashes hashes) {
        schemaVersion = source.schemaVersion;
        snapshotId = source.snapshotId;
        sourceDimension = source.sourceDimension;
        sourceOrigin = source.sourceOrigin;
        bounds = source.bounds;
        this.blocks = List.copyOf(blocks);
        passengerAnchors = source.passengerAnchors;
        stats = source.stats;
        createdAtGameTime = source.createdAtGameTime;
        propulsion = source.propulsion;
        propulsionSignature = source.propulsionSignature;
        contentHash = hashes.content();
        structureDigest = hashes.structure();
        structureDigestBytes = hashes.structureBytes();
        if (!source.structureDigest.equals(structureDigest)) {
            throw new IllegalArgumentException("Payload preparation changed the rocket structure");
        }
    }

    private RocketStructureSnapshot(
            UUID snapshotId,
            ResourceLocation sourceDimension,
            RocketPosition sourceOrigin,
            List<RocketBlock> blocks,
            List<RocketPosition> passengerAnchors,
            RocketStats stats,
            long createdAtGameTime,
            RocketPropulsion propulsion
    ) {
        schemaVersion = propulsion == null ? 1 : 2;
        this.propulsion = propulsion;
        propulsionSignature = propulsion == null ? null : propulsion.signature();
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
        String structureHash = RocketSnapshotHasher.hash(
                schemaVersion,
                sourceDimension,
                sourceOrigin,
                bounds,
                this.blocks,
                this.passengerAnchors,
                stats
        );
        if (propulsion != null && propulsion.primaryCapacity() != stats.fuelCapacity()) {
            throw new RocketSnapshotException(RocketValidationCode.STATS_MISMATCH, "Propulsion capacity differs from stats");
        }
        contentHash = propulsion == null ? structureHash : RocketNativeResources.hash(structureHash + ":" + propulsionSignature);
        // C-I: one pass per constructed snapshot, so recognition never hashes; the codec never writes it.
        var structure = RocketSnapshotHasher.structure(schemaVersion, sourceDimension, sourceOrigin, bounds, this.blocks,
                this.passengerAnchors, stats, propulsion);
        structureDigest = structure.digest();
        structureDigestBytes = structure.bytes();
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
                createdAtGameTime,
                null
        );
    }

    public static RocketStructureSnapshot create(UUID snapshotId, ResourceLocation sourceDimension,
            RocketPosition sourceOrigin, List<RocketBlock> blocks, List<RocketPosition> anchors,
            RocketStats stats, long createdAtGameTime, RocketPropulsion propulsion) {
        return new RocketStructureSnapshot(snapshotId, sourceDimension, sourceOrigin, blocks, anchors, stats,
                createdAtGameTime, propulsion);
    }

    public java.util.Optional<RocketPropulsion> propulsion() { return java.util.Optional.ofNullable(propulsion); }
    String propulsionSignature() { return propulsionSignature; }

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

    /**
     * Derived, never persisted: SHA-256 with prefix {@code arce-rocket-structure-1:} over the schema, dimension, origin,
     * bounds, each block's position, state and payload adapter ID (not payload data), the anchors, the stats and the
     * propulsion signature. Two snapshots with one snapshot ID and equal digests differ at most in payload data and in
     * the creation time, which no hash covers (W11CD-DESIGN section 2.5).
     */
    public String structureDigest() {
        return structureDigest;
    }

    /**
     * The byte work of the digest's one pass, for ADR-065 section 4.1.1's reconstruction budget; the pass visits each
     * of {@link #blocks()} once. It never includes payload data.
     */
    public int structureDigestBytes() {
        return structureDigestBytes;
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
                relocatedAtGameTime,
                propulsion
        );
    }
}
