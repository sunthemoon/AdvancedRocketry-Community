package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.network.TravelTargetWireCodec;
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
        TravelTarget target,
        UUID requestId,
        io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference landingPad
) {
    static final int MAX_ENCODED_BYTES = Byte.BYTES
            + 5
            + TravelTargetWireCodec.MAX_ENCODED_BYTES
            + (Long.BYTES * 2)
            + 1; // Longest body ID + absent pad exceeds a station UUID + present 25-byte pad.

    public RocketFlightIntentPacket(RocketFlightAction action, int rocketEntityId, TravelTarget target, UUID requestId) {
        this(action, rocketEntityId, target, requestId, null);
    }

    public RocketFlightIntentPacket {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requestId, "requestId");
        if (landingPad != null && !(target instanceof TravelTarget.Station)) {
            throw new IllegalArgumentException("A named pad requires a station target");
        }
        if (rocketEntityId < 0) {
            throw new IllegalArgumentException("Rocket entity id cannot be negative");
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action.networkId());
        buffer.writeVarInt(rocketEntityId);
        TravelTargetWireCodec.encode(buffer, target);
        buffer.writeUUID(requestId);
        io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference.writeOptional(buffer, landingPad);
    }

    public static int maximumEncodedBytes() {
        return MAX_ENCODED_BYTES;
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
        if (entityId < 0) { throw new IllegalArgumentException("Rocket entity id cannot be negative"); }
        RocketFlightIntentPacket packet = new RocketFlightIntentPacket(
                action,
                entityId,
                TravelTargetWireCodec.decode(buffer),
                buffer.readUUID(),
                io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference.readOptional(buffer)
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
                    target,
                    requestId,
                    landingPad
            ));
        }
        context.setPacketHandled(true);
    }
}
