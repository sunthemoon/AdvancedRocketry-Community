package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.network.TravelTargetWireCodec;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class RocketFlightPlanPacketTest {
    private static final UUID STATION = UUID.fromString("123e4567-e89b-42d3-a456-426614174730");
    private static final TravelTarget EARTH = new TravelTarget.BodySurface(ModIdentity.id("earth"));
    private static final TravelTarget MOON = new TravelTarget.BodySurface(ModIdentity.id("moon"));
    private static final TravelTarget STATION_TARGET = new TravelTarget.Station(STATION);
    private static final RocketFlightQuotes QUOTES = new RocketFlightQuotes(List.of(
            quote(EARTH, 0, false),
            quote(MOON, 372, false),
            quote(STATION_TARGET, 330, true)
    ));

    @Test
    void emptyAndTypedPlansRoundTripWithBoundedQuoteLists() {
        roundTrip(RocketFlightPlanSnapshot.empty());
        roundTrip(new RocketFlightPlanSnapshot(EARTH));
        roundTrip(new RocketFlightPlanSnapshot(MOON));
        roundTrip(new RocketFlightPlanSnapshot(STATION_TARGET));
        roundTrip(new RocketFlightPlanSnapshot(
                new TravelTarget.Mission(UUID.fromString("123e4567-e89b-42d3-a456-426614174731"))
        ));
    }

    @Test
    void negativeIdentifiersAndDuplicateQuoteTargetsFail() {
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightPlanPacket(-1, 1, RocketFlightPlanSnapshot.empty(), QUOTES));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightPlanPacket(1, -1, RocketFlightPlanSnapshot.empty(), QUOTES));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightQuotes(List.of(
                quote(EARTH, 10, false), quote(EARTH, 11, false)
        )));
    }

    @Test
    void everyTruncatedFrameIsRejected() {
        withBuffer(buffer -> {
            RocketFlightPlanPacket.encode(new RocketFlightPlanPacket(
                    7, 42, new RocketFlightPlanSnapshot(STATION_TARGET), QUOTES
            ), buffer);
            int encoded = buffer.writerIndex();
            for (int length = 0; length < encoded; length++) {
                FriendlyByteBuf truncated = new FriendlyByteBuf(buffer.copy(0, length));
                try {
                    assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(truncated));
                } finally {
                    truncated.release();
                }
            }
        });
    }

    @Test
    void oversizeCountsFlagsAndTrailingBytesAreRejected() {
        withBuffer(buffer -> {
            buffer.writeInt(7).writeInt(42).writeBoolean(false);
            buffer.writeVarInt(RocketFlightQuotes.MAX_QUOTES + 1);
            buffer.writeLong(1);
            buffer.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
        withBuffer(buffer -> {
            buffer.writeInt(7).writeInt(42).writeBoolean(false);
            buffer.writeVarInt(1);
            TravelTargetWireCodec.encode(buffer, EARTH);
            buffer.writeInt(10).writeByte(255);
            buffer.writeLong(1);
            buffer.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
        withBuffer(buffer -> {
            RocketFlightPlanPacket.encode(new RocketFlightPlanPacket(
                    7, 42, RocketFlightPlanSnapshot.empty(), QUOTES
            ), buffer);
            buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
        withBuffer(buffer -> {
            buffer.writeZero(RocketFlightPlanPacket.MAX_ENCODED_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
    }

    @Test
    void latePacketsMustMatchBothTheOpenContainerAndRocket() {
        var packet = new RocketFlightPlanPacket(7, 42, RocketFlightPlanSnapshot.empty(), QUOTES);
        assertTrue(packet.targets(7, 42));
        assertFalse(packet.targets(8, 42));
        assertFalse(packet.targets(7, 43));
        assertFalse(packet.targets(0, -1));
    }

    @Test
    void protocolRevisionCarriesTypedTargetsAndVariableQuoteCounts() {
        assertEquals("7", RocketFlightNetwork.protocolVersion());
        withBuffer(buffer -> {
            var empty = new RocketFlightPlanPacket(
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    RocketFlightPlanSnapshot.empty(),
                    RocketFlightQuotes.empty()
            );
            RocketFlightPlanPacket.encode(empty, buffer);
            assertEquals(RocketFlightPlanPacket.MIN_ENCODED_BYTES, buffer.readableBytes());
            assertEquals(empty, RocketFlightPlanPacket.decode(buffer));
        });
    }

    private static RocketFlightQuotes.TargetQuote quote(
            TravelTarget target,
            int fuel,
            boolean launchable
    ) {
        return new RocketFlightQuotes.TargetQuote(
                target,
                new RocketFlightQuotes.Quote(fuel, launchable)
        );
    }

    @Test
    void navigationRejectsNegativeGenerationNoncanonicalCountsDuplicatesAndInvalidStations() {
        for (int count : List.of(-1, 33)) {
            withBuffer(buffer -> {
                emptyNavigationHeader(buffer, 1);
                buffer.writeVarInt(count);
                assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
        withBuffer(buffer -> {
            emptyNavigationHeader(buffer, -1);
            buffer.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
        withBuffer(buffer -> {
            emptyNavigationHeader(buffer, 1);
            buffer.writeByte(0x80).writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
        for (String name : List.of("", " trimmed ", "x".repeat(49))) {
            withBuffer(buffer -> {
                emptyNavigationHeader(buffer, 1);
                buffer.writeVarInt(1);
                station(buffer, name, "test:body");
                assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
        for (String body : List.of("body", "Bad:body", "test:" + "x".repeat(124))) {
            withBuffer(buffer -> {
                emptyNavigationHeader(buffer, 1);
                buffer.writeVarInt(1);
                station(buffer, "Name", body);
                assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
        withBuffer(buffer -> {
            emptyNavigationHeader(buffer, 1);
            buffer.writeVarInt(2);
            station(buffer, "First", "test:body");
            station(buffer, "Second", "test:body");
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
    }

    @Test
    void everyTruncationIncludingStationStringsAndStrictPlanFlagsRejects() {
        withBuffer(buffer -> {
            emptyNavigationHeader(buffer, 1);
            buffer.writeVarInt(1);
            station(buffer, "Station", "test:body");
            int size = buffer.writerIndex();
            for (int length = 0; length < size; length++) {
                var truncated = new FriendlyByteBuf(buffer.copy(0, length));
                try { assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(truncated)); }
                finally { truncated.release(); }
            }
            buffer.setByte(8, 2);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
        });
    }

    private static void emptyNavigationHeader(FriendlyByteBuf buffer, long generation) {
        buffer.writeInt(7).writeInt(42).writeByte(0);
        buffer.writeVarInt(0);
        buffer.writeLong(generation);
    }

    private static void station(FriendlyByteBuf buffer, String name, String body) {
        buffer.writeUUID(STATION);
        buffer.writeUtf(name);
        buffer.writeUtf(body);
    }

    private static void roundTrip(RocketFlightPlanSnapshot snapshot) {
        withBuffer(buffer -> {
            var packet = new RocketFlightPlanPacket(Integer.MAX_VALUE, Integer.MAX_VALUE, snapshot, QUOTES);
            RocketFlightPlanPacket.encode(packet, buffer);
            assertTrue(buffer.readableBytes() >= RocketFlightPlanPacket.MIN_ENCODED_BYTES);
            assertTrue(buffer.readableBytes() <= RocketFlightPlanPacket.MAX_ENCODED_BYTES);
            assertEquals(packet, RocketFlightPlanPacket.decode(buffer));
            assertFalse(buffer.isReadable());
        });
    }

    private static void withBuffer(Consumer<FriendlyByteBuf> action) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            action.accept(buffer);
        } finally {
            buffer.release();
        }
    }
}
