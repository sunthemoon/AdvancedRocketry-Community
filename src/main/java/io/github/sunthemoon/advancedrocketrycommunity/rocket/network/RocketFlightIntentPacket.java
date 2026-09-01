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
        RocketFlightAction action = RocketFlightAction.fromNetworkId(buffer.readUnsignedByte());
        int entityId = buffer.readVarInt();
        RocketDestination destination = RocketDestination.fromNetworkId(buffer.readUnsignedByte());
        UUID stationId = destination == RocketDestination.SPACE_STATION ? buffer.readUUID() : null;
        return new RocketFlightIntentPacket(action, entityId, destination, stationId, buffer.readUUID());
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
