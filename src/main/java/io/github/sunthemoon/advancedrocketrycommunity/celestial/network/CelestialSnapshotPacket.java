package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Outer versioned packet envelope; future schema payloads remain opaque and bounded. */
public record CelestialSnapshotPacket(
        int schemaVersion,
        long catalogGeneration,
        byte[] payload
) {
    public CelestialSnapshotPacket {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Snapshot schema must be positive");
        }
        if (catalogGeneration < 0L) {
            throw new IllegalArgumentException("Catalog generation cannot be negative");
        }
        if (payload.length > CelestialSnapshotCodec.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Snapshot payload exceeds the packet limit");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public static DataResult<CelestialSnapshotPacket> fromCatalog(
            CelestialCatalog catalog,
            long generation
    ) {
        return CelestialSnapshotCodec.encode(catalog)
                .map(payload -> new CelestialSnapshotPacket(
                        CelestialSnapshotCodec.SCHEMA_VERSION,
                        generation,
                        payload
                ));
    }

    public static void encode(CelestialSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.schemaVersion());
        buffer.writeVarLong(packet.catalogGeneration());
        buffer.writeByteArray(packet.payload);
    }

    public static CelestialSnapshotPacket decode(FriendlyByteBuf buffer) {
        int schema = buffer.readVarInt();
        long generation = buffer.readVarLong();
        byte[] payload = buffer.readByteArray(CelestialSnapshotCodec.MAX_PACKET_BYTES);
        return new CelestialSnapshotPacket(schema, generation, payload);
    }

    public static void handle(
            CelestialSnapshotPacket packet,
            java.util.function.Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        CelestialClientCache.accept(packet);
        context.setPacketHandled(true);
    }
}
