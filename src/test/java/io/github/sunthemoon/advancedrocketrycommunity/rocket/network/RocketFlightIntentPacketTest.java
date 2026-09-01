package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class RocketFlightIntentPacketTest {
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000661");

    @Test
    void boundedIntentRoundTripsWithoutCoordinatesOrClientStats() {
        RocketFlightIntentPacket original = new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                42,
                RocketDestination.MOON,
                REQUEST
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        original.encode(buffer);
        RocketFlightIntentPacket decoded = RocketFlightIntentPacket.decode(buffer);

        assertEquals(original, decoded);
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void stationIntentRoundTripsOnlyTheBoundedStationUuid() {
        UUID stationId = UUID.fromString("00000000-0000-0000-0000-000000000700");
        RocketFlightIntentPacket original = new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                7,
                RocketDestination.SPACE_STATION,
                stationId,
                REQUEST
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buffer);

        assertEquals(original, RocketFlightIntentPacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                7,
                RocketDestination.SPACE_STATION,
                null,
                REQUEST
        ));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                7,
                RocketDestination.EARTH,
                stationId,
                REQUEST
        ));
    }

    @Test
    void negativeEntityAndUnknownFixedIdsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                -1,
                RocketDestination.MOON,
                REQUEST
        ));

        FriendlyByteBuf invalidAction = new FriendlyByteBuf(Unpooled.buffer());
        invalidAction.writeByte(255);
        invalidAction.writeVarInt(1);
        invalidAction.writeByte(RocketDestination.MOON.networkId());
        invalidAction.writeUUID(REQUEST);
        assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(invalidAction));

        FriendlyByteBuf invalidDestination = new FriendlyByteBuf(Unpooled.buffer());
        invalidDestination.writeByte(RocketFlightAction.LAUNCH.networkId());
        invalidDestination.writeVarInt(1);
        invalidDestination.writeByte(255);
        invalidDestination.writeUUID(REQUEST);
        assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(invalidDestination));
    }
}
