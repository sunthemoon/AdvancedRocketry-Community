package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Bounded C2S intent: no coordinates, fuel, stats, passenger list, or snapshot. */
public record RocketFlightIntentPacket(
        RocketFlightAction action,
        int rocketEntityId,
        RocketDestination destination,
        UUID destinationStationId,
        UUID requestId
) {
    static final int MAX_ENCODED_BYTES = Byte.BYTES
            + 5
            + Byte.BYTES
            + (Long.BYTES * 2)
            + (Long.BYTES * 2);

    public RocketFlightIntentPacket {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(requestId, "requestId");
        if ((destination == RocketDestination.SPACE_STATION) != (destinationStationId != null)) {
            throw new IllegalArgumentException("Station destination must bind exactly one station UUID");
        }
        if (rocketEntityId < 0) {
            throw new IllegalArgumentException("Rocket entity id cannot be negative");
        }
    }

    public RocketFlightIntentPacket(
            RocketFlightAction action,
            int rocketEntityId,
            RocketDestination destination,
            UUID requestId
    ) {
        this(action, rocketEntityId, destination, null, requestId);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action.networkId());
        buffer.writeVarInt(rocketEntityId);
        buffer.writeByte(destination.networkId());
        if (destination == RocketDestination.SPACE_STATION) {
            buffer.writeUUID(destinationStationId);
        }
        buffer.writeUUID(requestId);
    }

    public static RocketFlightIntentPacket decode(FriendlyByteBuf buffer) {
        int frameBytes = buffer.readableBytes();
        if (frameBytes <= 0 || frameBytes > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Rocket flight intent frame length is outside the bounded protocol: " + frameBytes
            );
        }
        RocketFlightAction action = RocketFlightAction.fromNetworkId(buffer.readUnsignedByte());
        int entityStart = buffer.readerIndex();
        int entityId = buffer.readVarInt();
        int entityBytes = buffer.readerIndex() - entityStart;
        if (entityBytes != FriendlyByteBuf.getVarIntSize(entityId)) {
            throw new IllegalArgumentException("Rocket entity id uses a non-canonical VarInt encoding");
        }
        RocketDestination destination = RocketDestination.fromNetworkId(buffer.readUnsignedByte());
        UUID stationId = destination == RocketDestination.SPACE_STATION ? buffer.readUUID() : null;
        RocketFlightIntentPacket packet = new RocketFlightIntentPacket(
                action,
                entityId,
                destination,
                stationId,
                buffer.readUUID()
        );
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Rocket flight intent frame contains " + buffer.readableBytes() + " trailing bytes"
            );
        }
        return packet;
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> RocketRuntime.requestFlightIntent(
                    sender,
                    rocketEntityId,
                    action,
                    destination,
                    destinationStationId,
                    requestId
            ));
        }
        context.setPacketHandled(true);
    }
}
