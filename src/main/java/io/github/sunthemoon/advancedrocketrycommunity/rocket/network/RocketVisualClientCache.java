package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded client-only display cache. It carries no gameplay authority. */
public final class RocketVisualClientCache {
    public static final long REASSEMBLY_EXPIRY_MILLIS = 10_000L;
    public static final int MAX_COMPLETE_SNAPSHOTS = 256;
    private static final RocketVisualClientCache GLOBAL = new RocketVisualClientCache();

    private final Map<UUID, Reassembly> pending = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, RocketVisualSnapshot> complete = new LinkedHashMap<>(16, 0.75F, true);

    public enum AcceptResult {
        PARTIAL,
        COMPLETED,
        DUPLICATE,
        REJECTED
    }

    public static AcceptResult accept(RocketVisualChunkPacket packet) {
        return GLOBAL.accept(packet, System.currentTimeMillis());
    }

    public static Optional<RocketVisualSnapshot> snapshot(UUID entityId) {
        return GLOBAL.get(entityId);
    }

    public static void discardGlobal(UUID entityId) {
        GLOBAL.discard(entityId);
    }

    public static void clearGlobal() {
        GLOBAL.clear();
    }

    public synchronized AcceptResult accept(RocketVisualChunkPacket packet, long nowMillis) {
        Objects.requireNonNull(packet, "packet");
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("nowMillis must not be negative");
        }
        evictExpired(nowMillis);
        Reassembly assembly = pending.get(packet.entityId());
        if (assembly != null && !assembly.matches(packet)) {
            pending.remove(packet.entityId());
            assembly = null;
        }
        if (assembly == null) {
            evictOldestPendingIfFull();
            assembly = new Reassembly(packet, nowMillis);
            pending.put(packet.entityId(), assembly);
        }

        byte[] previous = assembly.chunks[packet.chunkIndex()];
        if (previous != null) {
            if (Arrays.equals(previous, packet.chunk())) {
                assembly.lastUpdatedMillis = nowMillis;
                return AcceptResult.DUPLICATE;
            }
            pending.remove(packet.entityId());
            return AcceptResult.REJECTED;
        }
        byte[] acceptedChunk = packet.chunk();
        assembly.chunks[packet.chunkIndex()] = acceptedChunk;
        assembly.receivedChunks++;
        assembly.receivedBytes += acceptedChunk.length;
        assembly.lastUpdatedMillis = nowMillis;
        if (assembly.receivedChunks != assembly.chunks.length) {
            return AcceptResult.PARTIAL;
        }

        pending.remove(packet.entityId());
        if (assembly.receivedBytes != assembly.totalBytes) {
            return AcceptResult.REJECTED;
        }
        byte[] payload;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(assembly.totalBytes);
            for (byte[] chunk : assembly.chunks) {
                output.writeBytes(chunk);
            }
            payload = output.toByteArray();
        } catch (RuntimeException exception) {
            return AcceptResult.REJECTED;
        }
        if (!RocketVisualSnapshotCodec.sha256(payload).equals(assembly.payloadHash)) {
            return AcceptResult.REJECTED;
        }
        RocketVisualSnapshot snapshot;
        try {
            snapshot = RocketVisualSnapshotCodec.decode(payload);
        } catch (RuntimeException exception) {
            return AcceptResult.REJECTED;
        }
        if (!snapshot.snapshotId().equals(assembly.snapshotId)
                || !snapshot.structureContentHash().equals(assembly.structureContentHash)) {
            return AcceptResult.REJECTED;
        }
        complete.put(packet.entityId(), snapshot);
        while (complete.size() > MAX_COMPLETE_SNAPSHOTS) {
            UUID oldest = complete.keySet().iterator().next();
            complete.remove(oldest);
        }
        return AcceptResult.COMPLETED;
    }

    public synchronized Optional<RocketVisualSnapshot> get(UUID entityId) {
        return Optional.ofNullable(complete.get(Objects.requireNonNull(entityId, "entityId")));
    }

    public synchronized void discard(UUID entityId) {
        pending.remove(Objects.requireNonNull(entityId, "entityId"));
        complete.remove(entityId);
    }

    public synchronized void clear() {
        pending.clear();
        complete.clear();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    public synchronized int completeCount() {
        return complete.size();
    }

    public synchronized void evictExpired(long nowMillis) {
        pending.entrySet().removeIf(entry -> nowMillis >= entry.getValue().lastUpdatedMillis
                && nowMillis - entry.getValue().lastUpdatedMillis > REASSEMBLY_EXPIRY_MILLIS);
    }

    private void evictOldestPendingIfFull() {
        if (pending.size() < RocketLimits.MAX_VISUAL_REASSEMBLIES) {
            return;
        }
        UUID oldest = pending.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().lastUpdatedMillis))
                .map(Map.Entry::getKey)
                .orElseThrow();
        pending.remove(oldest);
    }

    private static final class Reassembly {
        private final UUID snapshotId;
        private final String structureContentHash;
        private final String payloadHash;
        private final int totalBytes;
        private final byte[][] chunks;
        private int receivedChunks;
        private int receivedBytes;
        private long lastUpdatedMillis;

        private Reassembly(RocketVisualChunkPacket first, long nowMillis) {
            snapshotId = first.snapshotId();
            structureContentHash = first.structureContentHash();
            payloadHash = first.payloadHash();
            totalBytes = first.totalBytes();
            chunks = new byte[first.chunkCount()][];
            lastUpdatedMillis = nowMillis;
        }

        private boolean matches(RocketVisualChunkPacket packet) {
            return snapshotId.equals(packet.snapshotId())
                    && structureContentHash.equals(packet.structureContentHash())
                    && payloadHash.equals(packet.payloadHash())
                    && totalBytes == packet.totalBytes()
                    && chunks.length == packet.chunkCount();
        }
    }
}
