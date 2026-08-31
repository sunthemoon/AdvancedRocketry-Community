package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Fixed-field, versioned, S2C-only life-support display packet. */
public record LifeSupportStatusPacket(
        int schemaVersion,
        PlayerLifeSupportSnapshot snapshot
) {
    public static final int SCHEMA_VERSION = 1;

    public LifeSupportStatusPacket {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported life-support packet schema");
        }
        Objects.requireNonNull(snapshot, "snapshot");
    }

    public static LifeSupportStatusPacket current(PlayerLifeSupportSnapshot snapshot) {
        return new LifeSupportStatusPacket(SCHEMA_VERSION, snapshot);
    }

    public static void encode(LifeSupportStatusPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.schemaVersion());
        buffer.writeVarInt(packet.snapshot().status().ordinal());
        buffer.writeVarInt(packet.snapshot().breathability().ordinal());
        buffer.writeVarInt(packet.snapshot().equippedSuitPieces());
        buffer.writeVarInt(packet.snapshot().oxygenUnits());
    }

    public static LifeSupportStatusPacket decode(FriendlyByteBuf buffer) {
        int schema = buffer.readVarInt();
        PlayerProtectionStatus status = decodeEnum(
                buffer.readVarInt(),
                PlayerProtectionStatus.values(),
                "player protection status"
        );
        BreathabilityState breathability = decodeEnum(
                buffer.readVarInt(),
                BreathabilityState.values(),
                "breathability"
        );
        int suitPieces = buffer.readVarInt();
        int oxygenUnits = buffer.readVarInt();
        return new LifeSupportStatusPacket(
                schema,
                new PlayerLifeSupportSnapshot(status, breathability, suitPieces, oxygenUnits)
        );
    }

    public static void handle(
            LifeSupportStatusPacket packet,
            java.util.function.Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        LifeSupportClientCache.accept(packet.snapshot());
        context.setPacketHandled(true);
    }

    private static <T> T decodeEnum(int ordinal, T[] values, String label) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown " + label + " ordinal");
        }
        return values[ordinal];
    }
}
