package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RocketVisualChunker {
    private RocketVisualChunker() {
    }

    public static List<RocketVisualChunkPacket> chunk(
            UUID entityId,
            RocketVisualSnapshot snapshot
    ) {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(snapshot, "snapshot");
        byte[] payload = RocketVisualSnapshotCodec.encode(snapshot);
        String payloadHash = RocketVisualSnapshotCodec.sha256(payload);
        int chunkCount = (payload.length + RocketLimits.MAX_VISUAL_CHUNK_BYTES - 1)
                / RocketLimits.MAX_VISUAL_CHUNK_BYTES;
        ArrayList<RocketVisualChunkPacket> packets = new ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) {
            int start = index * RocketLimits.MAX_VISUAL_CHUNK_BYTES;
            int end = Math.min(payload.length, start + RocketLimits.MAX_VISUAL_CHUNK_BYTES);
            packets.add(new RocketVisualChunkPacket(
                    RocketVisualChunkPacket.SCHEMA_VERSION,
                    entityId,
                    snapshot.snapshotId(),
                    snapshot.structureContentHash(),
                    payloadHash,
                    payload.length,
                    chunkCount,
                    index,
                    Arrays.copyOfRange(payload, start, end)
            ));
        }
        return List.copyOf(packets);
    }
}
