package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        withBuffer(buffer -> {
            original.encode(buffer);
            RocketFlightIntentPacket decoded = RocketFlightIntentPacket.decode(buffer);

            assertEquals(original, decoded);
            assertEquals(0, buffer.readableBytes());
        });
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
        withBuffer(buffer -> {
            original.encode(buffer);
            assertEquals(original, RocketFlightIntentPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        });
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

        withBuffer(invalidAction -> {
            invalidAction.writeByte(255);
            invalidAction.writeVarInt(1);
            invalidAction.writeByte(RocketDestination.MOON.networkId());
            invalidAction.writeUUID(REQUEST);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(invalidAction));
        });

        withBuffer(invalidDestination -> {
            invalidDestination.writeByte(RocketFlightAction.LAUNCH.networkId());
            invalidDestination.writeVarInt(1);
            invalidDestination.writeByte(255);
            invalidDestination.writeUUID(REQUEST);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RocketFlightIntentPacket.decode(invalidDestination)
            );
        });
    }

    @Test
    void exactMaximumStationFrameRoundTrips() {
        UUID stationId = UUID.fromString("00000000-0000-0000-0000-000000000700");
        RocketFlightIntentPacket original = new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                Integer.MAX_VALUE,
                RocketDestination.SPACE_STATION,
                stationId,
                REQUEST
        );

        withBuffer(buffer -> {
            original.encode(buffer);
            assertEquals(RocketFlightIntentPacket.MAX_ENCODED_BYTES, buffer.readableBytes());
            assertEquals(original, RocketFlightIntentPacket.decode(buffer));
        });
    }

    @Test
    void everyTruncatedStationFrameFailsClosed() {
        byte[] canonical = encode(new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                Integer.MAX_VALUE,
                RocketDestination.SPACE_STATION,
                UUID.fromString("00000000-0000-0000-0000-000000000700"),
                REQUEST
        ));
        assertEquals(RocketFlightIntentPacket.MAX_ENCODED_BYTES, canonical.length);

        for (int length = 0; length < canonical.length; length++) {
            int truncatedLength = length;
            withBuffer(buffer -> {
                buffer.writeBytes(canonical, 0, truncatedLength);
                assertThrows(
                        RuntimeException.class,
                        () -> RocketFlightIntentPacket.decode(buffer),
                        () -> "accepted truncated frame length " + truncatedLength
                );
            });
        }
    }

    @Test
    void oversizedAndTrailingFramesFailBeforeTheyCanBeSmuggled() {
        byte[] canonical = encode(new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                42,
                RocketDestination.MOON,
                REQUEST
        ));

        withBuffer(trailing -> {
            trailing.writeBytes(canonical);
            trailing.writeByte(0x5A);
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> RocketFlightIntentPacket.decode(trailing)
            );
            assertTrue(error.getMessage().contains("trailing bytes"));
        });

        withBuffer(oversized -> {
            oversized.writeZero(RocketFlightIntentPacket.MAX_ENCODED_BYTES + 1);
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> RocketFlightIntentPacket.decode(oversized)
            );
            assertTrue(error.getMessage().contains("outside the bounded protocol"));
            assertEquals(RocketFlightIntentPacket.MAX_ENCODED_BYTES + 1, oversized.readerIndex(0).readableBytes());
        });
    }

    @Test
    void nonCanonicalAndOverflowingVarIntsFailClosed() {
        withBuffer(overlongZero -> {
            overlongZero.writeByte(RocketFlightAction.LAUNCH.networkId());
            overlongZero.writeByte(0x80);
            overlongZero.writeByte(0x00);
            overlongZero.writeByte(RocketDestination.MOON.networkId());
            overlongZero.writeUUID(REQUEST);
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> RocketFlightIntentPacket.decode(overlongZero)
            );
            assertTrue(error.getMessage().contains("non-canonical"));
        });

        withBuffer(overflow -> {
            overflow.writeByte(RocketFlightAction.LAUNCH.networkId());
            for (int index = 0; index < 6; index++) {
                overflow.writeByte(0x80);
            }
            overflow.writeByte(RocketDestination.MOON.networkId());
            overflow.writeUUID(REQUEST);
            assertThrows(RuntimeException.class, () -> RocketFlightIntentPacket.decode(overflow));
        });
    }

    @Test
    void negativeEntityAndMissingStationPayloadFailClosedOnTheWire() {
        withBuffer(negativeEntity -> {
            negativeEntity.writeByte(RocketFlightAction.LAUNCH.networkId());
            negativeEntity.writeVarInt(-1);
            negativeEntity.writeByte(RocketDestination.MOON.networkId());
            negativeEntity.writeUUID(REQUEST);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(negativeEntity));
        });

        withBuffer(missingStation -> {
            missingStation.writeByte(RocketFlightAction.LAUNCH.networkId());
            missingStation.writeVarInt(1);
            missingStation.writeByte(RocketDestination.SPACE_STATION.networkId());
            missingStation.writeUUID(REQUEST);
            assertThrows(RuntimeException.class, () -> RocketFlightIntentPacket.decode(missingStation));
        });
    }

    private static byte[] encode(RocketFlightIntentPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    private static void withBuffer(BufferAction action) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            action.run(buffer);
        } finally {
            buffer.release();
        }
    }

    @FunctionalInterface
    private interface BufferAction {
        void run(FriendlyByteBuf buffer);
    }
}
