package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.netty.handler.codec.DecoderException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-047: the body the station the player stands in orbits, or none. It carries no station
 * identity, and the client uses it for presentation only.
 */
public record StationSkyContextPacket(Optional<ResourceLocation> orbitBody) {
    /**
     * One strict boolean byte, a VarInt length and at most 128 ASCII identifier characters: 131 bytes
     * after the message index. A malformed message is a decoding error, which closes the connection as
     * for every other ARCE message.
     */
    public static final int MAX_BYTES = 1 + 2 + BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS;
    public static final StationSkyContextPacket NONE = new StationSkyContextPacket(Optional.empty());

    public StationSkyContextPacket {
        Objects.requireNonNull(orbitBody, "orbitBody");
        orbitBody.ifPresent(id -> BoundedCelestialCodecs.requireId(id, "orbit body"));
    }

    public static void encode(StationSkyContextPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.orbitBody().isPresent());
        packet.orbitBody().ifPresent(id -> buffer.writeUtf(id.toString(),
                BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS));
    }

    public static StationSkyContextPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("Station sky context exceeds " + MAX_BYTES + " bytes");
        }
        Optional<ResourceLocation> body = Optional.empty();
        byte present = buffer.readByte();
        if (present != 0 && present != 1) {
            throw new DecoderException("Station sky context presence must be 0 or 1");
        }
        if (present == 1) {
            ResourceLocation id = ResourceLocation.tryParse(
                    buffer.readUtf(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS));
            if (id == null) {
                throw new DecoderException("Invalid station sky orbit body");
            }
            body = Optional.of(id);
        }
        if (buffer.isReadable()) {
            throw new DecoderException("Trailing bytes after the station sky context");
        }
        return new StationSkyContextPacket(body);
    }

    public static void handle(StationSkyContextPacket packet,
                              java.util.function.Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        StationSkyContextCache.accept(packet);
        context.setPacketHandled(true);
    }
}
