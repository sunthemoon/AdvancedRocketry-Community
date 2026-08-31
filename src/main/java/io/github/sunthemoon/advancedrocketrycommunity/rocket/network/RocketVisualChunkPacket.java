package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** One strictly bounded S2C fragment of a BlockEntity-free visual snapshot. */
public record RocketVisualChunkPacket(
        int schemaVersion,
        UUID entityId,
        UUID snapshotId,
        String structureContentHash,
        String payloadHash,
        int totalBytes,
        int chunkCount,
        int chunkIndex,
        byte[] chunk
) {
    public static final int SCHEMA_VERSION = 1;

    public RocketVisualChunkPacket {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported rocket visual packet schema");
        }
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(snapshotId, "snapshotId");
        requireHash(structureContentHash, "structureContentHash");
        requireHash(payloadHash, "payloadHash");
        if (totalBytes <= 0 || totalBytes > RocketLimits.MAX_VISUAL_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Rocket visual total byte count is invalid");
        }
        int expectedChunkCount = (totalBytes + RocketLimits.MAX_VISUAL_CHUNK_BYTES - 1)
                / RocketLimits.MAX_VISUAL_CHUNK_BYTES;
        if (chunkCount != expectedChunkCount || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("Rocket visual chunk manifest is invalid");
        }
        Objects.requireNonNull(chunk, "chunk");
        int expectedLength = chunkIndex == chunkCount - 1
                ? totalBytes - chunkIndex * RocketLimits.MAX_VISUAL_CHUNK_BYTES
                : RocketLimits.MAX_VISUAL_CHUNK_BYTES;
        if (chunk.length != expectedLength || chunk.length > RocketLimits.MAX_VISUAL_CHUNK_BYTES) {
            throw new IllegalArgumentException("Rocket visual chunk length does not match its manifest");
        }
        chunk = Arrays.copyOf(chunk, chunk.length);
    }

    @Override
    public byte[] chunk() {
        return Arrays.copyOf(chunk, chunk.length);
    }

    public static void encode(RocketVisualChunkPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.schemaVersion());
        buffer.writeUUID(packet.entityId());
        buffer.writeUUID(packet.snapshotId());
        buffer.writeUtf(packet.structureContentHash(), 64);
        buffer.writeUtf(packet.payloadHash(), 64);
        buffer.writeVarInt(packet.totalBytes());
        buffer.writeVarInt(packet.chunkCount());
        buffer.writeVarInt(packet.chunkIndex());
        buffer.writeByteArray(packet.chunk);
    }

    public static RocketVisualChunkPacket decode(FriendlyByteBuf buffer) {
        return new RocketVisualChunkPacket(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(RocketLimits.MAX_VISUAL_CHUNK_BYTES)
        );
    }

    public static void handle(
            RocketVisualChunkPacket packet,
            java.util.function.Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        RocketVisualClientCache.accept(packet);
        context.setPacketHandled(true);
    }

    private static void requireHash(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
    }
}
