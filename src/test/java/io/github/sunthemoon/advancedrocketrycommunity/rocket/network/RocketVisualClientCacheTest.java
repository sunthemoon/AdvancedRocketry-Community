package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RocketVisualClientCacheTest {
    @Test
    void outOfOrderChunksCompleteOnlyAfterHashAndSnapshotVerification() {
        UUID entityId = UUID.randomUUID();
        RocketVisualSnapshot expected = RocketVisualSnapshotCodecTest.visual(2_048);
        List<RocketVisualChunkPacket> packets = new ArrayList<>(RocketVisualChunker.chunk(entityId, expected));
        Collections.reverse(packets);
        RocketVisualClientCache cache = new RocketVisualClientCache();

        for (int index = 0; index < packets.size(); index++) {
            RocketVisualClientCache.AcceptResult result = cache.accept(packets.get(index), 100L + index);
            assertEquals(
                    index == packets.size() - 1
                            ? RocketVisualClientCache.AcceptResult.COMPLETED
                            : RocketVisualClientCache.AcceptResult.PARTIAL,
                    result
            );
        }
        RocketVisualSnapshot restored = cache.get(entityId).orElseThrow();
        assertEquals(expected.snapshotId(), restored.snapshotId());
        assertEquals(expected.structureContentHash(), restored.structureContentHash());
        assertEquals(expected.blocks(), restored.blocks());
        assertEquals(0, cache.pendingCount());
    }

    @Test
    void identicalDuplicateIsIdempotentButConflictingDuplicateDropsAssembly() {
        UUID entityId = UUID.randomUUID();
        List<RocketVisualChunkPacket> packets = RocketVisualChunker.chunk(
                entityId,
                RocketVisualSnapshotCodecTest.visual(2_048)
        );
        RocketVisualClientCache cache = new RocketVisualClientCache();
        RocketVisualChunkPacket first = packets.get(0);

        assertEquals(RocketVisualClientCache.AcceptResult.PARTIAL, cache.accept(first, 1L));
        assertEquals(RocketVisualClientCache.AcceptResult.DUPLICATE, cache.accept(first, 2L));

        byte[] changed = first.chunk();
        changed[0] ^= 0x01;
        RocketVisualChunkPacket conflicting = copyWithChunk(first, changed);
        assertEquals(RocketVisualClientCache.AcceptResult.REJECTED, cache.accept(conflicting, 3L));
        assertEquals(0, cache.pendingCount());
        assertFalse(cache.get(entityId).isPresent());
    }

    @Test
    void tamperedPayloadFailsWholeHashAndNeverReplacesValidCache() {
        UUID entityId = UUID.randomUUID();
        List<RocketVisualChunkPacket> valid = RocketVisualChunker.chunk(
                entityId,
                RocketVisualSnapshotCodecTest.visual(2_048)
        );
        RocketVisualClientCache cache = new RocketVisualClientCache();
        for (int index = 0; index < valid.size(); index++) {
            cache.accept(valid.get(index), index);
        }
        assertTrue(cache.get(entityId).isPresent());

        List<RocketVisualChunkPacket> tampered = new ArrayList<>(valid);
        byte[] changed = tampered.get(0).chunk();
        changed[changed.length - 1] ^= 0x01;
        tampered.set(0, copyWithChunk(tampered.get(0), changed));
        RocketVisualClientCache.AcceptResult terminal = null;
        for (int index = 0; index < tampered.size(); index++) {
            terminal = cache.accept(tampered.get(index), 1_000L + index);
        }
        assertEquals(RocketVisualClientCache.AcceptResult.REJECTED, terminal);
        assertTrue(cache.get(entityId).isPresent(), "Last valid visual snapshot must survive malformed replacement");
    }

    @Test
    void concurrentReassembliesAreCappedAndExpired() {
        RocketVisualClientCache cache = new RocketVisualClientCache();
        for (int index = 0; index < RocketLimits.MAX_VISUAL_REASSEMBLIES + 1; index++) {
            UUID entityId = new UUID(0L, index + 1L);
            RocketVisualChunkPacket first = RocketVisualChunker.chunk(
                    entityId,
                    RocketVisualSnapshotCodecTest.visual(2_048)
            ).get(0);
            cache.accept(first, index);
        }
        assertEquals(RocketLimits.MAX_VISUAL_REASSEMBLIES, cache.pendingCount());

        cache.evictExpired(RocketVisualClientCache.REASSEMBLY_EXPIRY_MILLIS + 100L);
        assertEquals(0, cache.pendingCount());
    }

    @Test
    void packetManifestAndChunkArraysAreDefensive() {
        RocketVisualChunkPacket packet = RocketVisualChunker.chunk(
                UUID.randomUUID(),
                RocketVisualSnapshotCodecTest.visual(4)
        ).get(0);
        byte[] copy = packet.chunk();
        byte original = packet.chunk()[0];
        copy[0] ^= 0x01;
        assertEquals(original, packet.chunk()[0]);

        assertThrows(IllegalArgumentException.class, () -> new RocketVisualChunkPacket(
                packet.schemaVersion(),
                packet.entityId(),
                packet.snapshotId(),
                packet.structureContentHash(),
                packet.payloadHash(),
                packet.totalBytes(),
                packet.chunkCount() + 1,
                packet.chunkIndex(),
                packet.chunk()
        ));
    }

    private static RocketVisualChunkPacket copyWithChunk(
            RocketVisualChunkPacket source,
            byte[] chunk
    ) {
        return new RocketVisualChunkPacket(
                source.schemaVersion(),
                source.entityId(),
                source.snapshotId(),
                source.structureContentHash(),
                source.payloadHash(),
                source.totalBytes(),
                source.chunkCount(),
                source.chunkIndex(),
                chunk
        );
    }
}
