package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class RocketFlightPlanPacketTest {
    private static final RocketFlightQuotes QUOTES = new RocketFlightQuotes(
            new RocketFlightQuotes.Quote(0, false), new RocketFlightQuotes.Quote(372, false),
            new RocketFlightQuotes.Quote(330, true));
    @Test
    void emptyAndAllDestinationsRoundTripAsAtomicFixedSizeFrames() {
        roundTrip(RocketFlightPlanSnapshot.empty(), 22);
        roundTrip(new RocketFlightPlanSnapshot(RocketDestination.EARTH, null), 22);
        roundTrip(new RocketFlightPlanSnapshot(RocketDestination.MOON, null), 22);
        roundTrip(new RocketFlightPlanSnapshot(RocketDestination.SPACE_STATION,
                new UUID(Long.MIN_VALUE, Long.MAX_VALUE)), 38);
    }

    @Test
    void negativeIdentifiersAndInvalidStationBindingsFail() {
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightPlanPacket(-1, 1, RocketFlightPlanSnapshot.empty(), QUOTES));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightPlanPacket(1, -1, RocketFlightPlanSnapshot.empty(), QUOTES));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightPlanSnapshot(RocketDestination.SPACE_STATION, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightPlanSnapshot(RocketDestination.MOON, new UUID(0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightPlanSnapshot(null, new UUID(0, 1)));
    }

    @Test
    void everyTruncatedStationFrameIsRejected() {
        for (int length = 0; length < 38; length++) {
            int truncated = length;
            withBuffer(buffer -> {
                RocketFlightPlanPacket.encode(new RocketFlightPlanPacket(7, 42,
                        new RocketFlightPlanSnapshot(RocketDestination.SPACE_STATION, new UUID(1, 2)), QUOTES), buffer);
                buffer.writerIndex(truncated);
                assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
    }

    @Test
    void oversizeAndTrailingBytesAreRejected() {
        for (int length : new int[]{23, 38, 39, 1000}) {
            withBuffer(buffer -> {
                RocketFlightPlanPacket.encode(new RocketFlightPlanPacket(7, 42,
                        RocketFlightPlanSnapshot.empty(), QUOTES), buffer);
                buffer.writeZero(length - 22);
                assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
    }

    @Test
    void unknownDestinationIdsAreRejected() {
        for (int id : new int[]{-128, -2, 3, 127}) {
            withBuffer(buffer -> {
                buffer.writeInt(7).writeInt(42).writeByte(id);
                buffer.writeZero(13);
                assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
    }

    @Test
    void decodedNegativeIdentifiersAreRejected() {
        withBuffer(buffer -> {
            buffer.writeInt(-1).writeInt(42).writeByte(-1);
            buffer.writeZero(13);
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
    void flightChannelRevisionAccountsForSynchronizedPassengerSeats() {
        assertEquals("5", RocketFlightNetwork.protocolVersion());
    }

    private static void roundTrip(RocketFlightPlanSnapshot snapshot, int size) {
        withBuffer(buffer -> {
            var packet = new RocketFlightPlanPacket(Integer.MAX_VALUE, Integer.MAX_VALUE, snapshot, QUOTES);
            RocketFlightPlanPacket.encode(packet, buffer);
            assertEquals(size, buffer.readableBytes());
            assertEquals(packet, RocketFlightPlanPacket.decode(buffer));
            assertFalse(buffer.isReadable());
        });
    }

    @Test
    void malformedQuoteValuesAndFlagsAreRejected() {
        for (int value : new int[]{-1, 2_048_001, Integer.MAX_VALUE}) {
            withBuffer(buffer -> {
                buffer.writeInt(7).writeInt(42).writeByte(-1);
                buffer.writeInt(value).writeInt(372).writeInt(330).writeByte(4);
                assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
        for (int flags : new int[]{1, 8, 128, 255}) {
            withBuffer(buffer -> {
                buffer.writeInt(7).writeInt(42).writeByte(-1);
                buffer.writeInt(0).writeInt(372).writeInt(330).writeByte(flags);
                assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanPacket.decode(buffer));
            });
        }
    }

    @Test
    void fullWidthQuotesSurviveBeyondSignedContainerShortRange() {
        withBuffer(buffer -> {
            var quote = new RocketFlightQuotes.Quote(2_048_000, true);
            var packet = new RocketFlightPlanPacket(7, 42, RocketFlightPlanSnapshot.empty(),
                    new RocketFlightQuotes(quote, quote, quote));
            RocketFlightPlanPacket.encode(packet, buffer);
            assertEquals(packet, RocketFlightPlanPacket.decode(buffer));
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
