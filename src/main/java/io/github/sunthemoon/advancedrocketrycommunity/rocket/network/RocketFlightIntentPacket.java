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
        UUID requestId
) {
    public RocketFlightIntentPacket {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(requestId, "requestId");
        if (rocketEntityId < 0) {
            throw new IllegalArgumentException("Rocket entity id cannot be negative");
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action.networkId());
        buffer.writeVarInt(rocketEntityId);
        buffer.writeByte(destination.networkId());
        buffer.writeUUID(requestId);
    }

    public static RocketFlightIntentPacket decode(FriendlyByteBuf buffer) {
        return new RocketFlightIntentPacket(
                RocketFlightAction.fromNetworkId(buffer.readUnsignedByte()),
                buffer.readVarInt(),
                RocketDestination.fromNetworkId(buffer.readUnsignedByte()),
                buffer.readUUID()
        );
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
                    requestId
            ));
        }
        context.setPacketHandled(true);
    }
}
