package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class RocketFlightIntentPacketTest {
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000661");
    private static final UUID INSTANCE = UUID.fromString("123e4567-e89b-42d3-a456-426614174700");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyTargetKindRoundTripsWithoutCoordinatesOrClientRouteCost() {
        List<TravelTarget> targets = List.of(
                new TravelTarget.BodySurface(CelestialIds.MOON_ID),
                new TravelTarget.Orbit(CelestialIds.EARTH_ID),
                new TravelTarget.Station(INSTANCE),
                new TravelTarget.Mission(INSTANCE)
        );
        for (TravelTarget target : targets) {
            RocketFlightIntentPacket original = new RocketFlightIntentPacket(
                    RocketFlightAction.LAUNCH, 42, target, REQUEST
            );
            withBuffer(buffer -> {
                original.encode(buffer);
                assertEquals(original, RocketFlightIntentPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            });
        }
    }

    @Test
    void negativeEntityAndUnknownFixedIdsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                -1,
                new TravelTarget.BodySurface(CelestialIds.MOON_ID),
                REQUEST
        ));

        withBuffer(invalidAction -> {
            invalidAction.writeByte(255);
            invalidAction.writeVarInt(1);
            writeSurfaceTarget(invalidAction, CelestialIds.MOON_ID);
            invalidAction.writeUUID(REQUEST);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(invalidAction));
        });

        withBuffer(invalidTarget -> {
            invalidTarget.writeByte(RocketFlightAction.LAUNCH.networkId());
            invalidTarget.writeVarInt(1);
            invalidTarget.writeByte(TravelTarget.SCHEMA_VERSION);
            invalidTarget.writeByte(255);
            invalidTarget.writeUUID(REQUEST);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(invalidTarget));
        });
    }

    @Test
    void exactMaximumBodyFrameRoundTrips() {
        ResourceLocation longest = ResourceLocation.tryBuild("a", "a".repeat(126));
        assertEquals(TravelTarget.MAX_RESOURCE_LOCATION_CHARS, longest.toString().length());
        RocketFlightIntentPacket original = new RocketFlightIntentPacket(
                RocketFlightAction.LAUNCH,
                Integer.MAX_VALUE,
                new TravelTarget.BodySurface(longest),
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
                new TravelTarget.Station(INSTANCE),
                REQUEST
        ));

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
                new TravelTarget.BodySurface(CelestialIds.MOON_ID),
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
        });
    }

    @Test
    void nonCanonicalAndOverflowingVarIntsFailClosed() {
        withBuffer(overlongZero -> {
            overlongZero.writeByte(RocketFlightAction.LAUNCH.networkId());
            overlongZero.writeByte(0x80);
            overlongZero.writeByte(0x00);
            writeSurfaceTarget(overlongZero, CelestialIds.MOON_ID);
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
            writeSurfaceTarget(overflow, CelestialIds.MOON_ID);
            overflow.writeUUID(REQUEST);
            assertThrows(RuntimeException.class, () -> RocketFlightIntentPacket.decode(overflow));
        });
    }

    @Test
    void invalidSchemaNegativeEntityAndMissingTargetPayloadFailClosed() {
        withBuffer(invalidSchema -> {
            invalidSchema.writeByte(RocketFlightAction.LAUNCH.networkId());
            invalidSchema.writeVarInt(1);
            invalidSchema.writeByte(TravelTarget.SCHEMA_VERSION + 1);
            invalidSchema.writeByte(0);
            invalidSchema.writeUtf(CelestialIds.MOON_ID.toString());
            invalidSchema.writeUUID(REQUEST);
            assertThrows(IllegalArgumentException.class,
                    () -> RocketFlightIntentPacket.decode(invalidSchema));
        });

        withBuffer(negativeEntity -> {
            negativeEntity.writeByte(RocketFlightAction.LAUNCH.networkId());
            negativeEntity.writeVarInt(-1);
            writeSurfaceTarget(negativeEntity, CelestialIds.MOON_ID);
            negativeEntity.writeUUID(REQUEST);
            assertThrows(IllegalArgumentException.class,
                    () -> RocketFlightIntentPacket.decode(negativeEntity));
        });

        withBuffer(missingStation -> {
            missingStation.writeByte(RocketFlightAction.LAUNCH.networkId());
            missingStation.writeVarInt(1);
            missingStation.writeByte(TravelTarget.SCHEMA_VERSION);
            missingStation.writeByte(2);
            assertThrows(RuntimeException.class,
                    () -> RocketFlightIntentPacket.decode(missingStation));
        });
    }

    private static void writeSurfaceTarget(FriendlyByteBuf buffer, ResourceLocation bodyId) {
        buffer.writeByte(TravelTarget.SCHEMA_VERSION);
        buffer.writeByte(0);
        buffer.writeUtf(bodyId.toString());
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
