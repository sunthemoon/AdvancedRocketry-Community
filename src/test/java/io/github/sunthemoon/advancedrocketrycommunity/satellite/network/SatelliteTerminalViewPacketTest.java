package io.github.sunthemoon.advancedrocketrycommunity.satellite.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket.InstanceView;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket.MissionSummary;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket.Selection;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-049 section 10: the terminal view round-trips, stays within 12 KiB at its maximum and decodes strictly. */
final class SatelliteTerminalViewPacketTest {
    @Test
    void theMaximumViewRoundTripsWithinItsBound() {
        SatelliteTerminalViewPacket maximum = maximum();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SatelliteTerminalViewPacket.encode(maximum, buffer);
            int bytes = buffer.readableBytes();
            assertTrue(bytes <= SatelliteTerminalViewPacket.MAX_BYTES, "the maximum view is " + bytes + " bytes");
            System.out.println("ARCE_TERMINAL_VIEW_MAX_BYTES=" + bytes);
            assertEquals(8_405, bytes, "the measured maximum recorded in ADR-049 revision 4");
            assertEquals(maximum, SatelliteTerminalViewPacket.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void theEmptyViewRoundTrips() {
        SatelliteTerminalViewPacket empty = SatelliteTerminalViewPacket.empty(7);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SatelliteTerminalViewPacket.encode(empty, buffer);
            assertEquals(empty, SatelliteTerminalViewPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void malformedViewsAreDecodingErrors() {
        byte[] valid = encoded(maximum());
        assertRejected(buffer -> {
            buffer.writeBytes(valid);
            buffer.writeByte(0);
        });
        assertRejected(buffer -> buffer.writeBytes(valid, 0, valid.length - 1));
        assertRejected(buffer -> buffer.writeBytes(new byte[SatelliteTerminalViewPacket.MAX_BYTES + 1]));
        byte[] empty = encoded(SatelliteTerminalViewPacket.empty(0));
        byte[] badKind = empty.clone();
        badKind[1] = (byte) (SatelliteKind.values().length + 1);
        assertRejected(buffer -> buffer.writeBytes(badKind));
        byte[] badPresence = empty.clone();
        badPresence[2] = 2;
        assertRejected(buffer -> buffer.writeBytes(badPresence));
        // A selection position outside its list.
        assertRejected(buffer -> {
            buffer.writeVarInt(0);
            buffer.writeByte(0);
            buffer.writeBoolean(false);
            buffer.writeVarInt(3);
            buffer.writeVarInt(2);
        });
        // Nine mission summaries on one page.
        assertRejected(buffer -> {
            buffer.writeVarInt(0);
            buffer.writeByte(0);
            for (int index = 0; index < 2; index++) {
                buffer.writeBoolean(false);
                buffer.writeVarInt(0);
                buffer.writeVarInt(0);
            }
            buffer.writeBoolean(false);
            for (int index = 0; index < 2; index++) {
                buffer.writeBoolean(false);
                buffer.writeVarInt(0);
                buffer.writeVarInt(0);
            }
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeVarInt(SatelliteTerminalViewPacket.MAX_MISSIONS_PER_PAGE + 1);
        });
    }

    @Test
    void constructorBoundsHoldOnTheServer() {
        Selection none = Selection.NONE;
        assertThrows(IllegalArgumentException.class, () -> new Selection(Optional.of(id("a")), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Selection(Optional.empty(), 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Selection(Optional.empty(), 0, SatelliteTerminalViewPacket.MAX_SELECTION_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> new Selection(
                Optional.of(ResourceLocation.tryParse("f:" + "x".repeat(127))), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteTerminalViewPacket(0, Optional.empty(),
                none, none, Optional.empty(), new Selection(Optional.of(id("i")), 0, 1), Optional.empty(), none,
                0, 0, List.of(), List.of()));
        List<RewardEntry> tooMany = new ArrayList<>();
        for (int index = 0; index <= SatelliteTerminalViewPacket.MAX_BUFFER_ENTRIES; index++) {
            tooMany.add(new RewardEntry(id("b" + index), 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new SatelliteTerminalViewPacket(0, Optional.empty(),
                none, none, Optional.empty(), none, Optional.empty(), none, 0, 0, List.of(), tooMany));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteTerminalViewPacket(0, Optional.empty(),
                none, none, Optional.empty(), none, Optional.empty(), none, 1, 1, List.of(), List.of()));
        assertEquals(new Selection(Optional.of(id("b")), 1, 3), Selection.of(List.of(id("a"), id("b"), id("c")), 4));
        assertEquals(Selection.NONE, Selection.of(List.of(), 2));
    }

    private static SatelliteTerminalViewPacket maximum() {
        int size = SatelliteTerminalViewPacket.MAX_SELECTION_SIZE;
        List<RewardEntry> yield = new ArrayList<>();
        for (int index = 0; index < SatelliteLimits.MAX_REWARD_ENTRIES; index++) {
            yield.add(new RewardEntry(id("yield" + index), 240));
        }
        List<MissionSummary> missions = new ArrayList<>();
        for (int index = 0; index < SatelliteTerminalViewPacket.MAX_MISSIONS_PER_PAGE; index++) {
            missions.add(new MissionSummary(UUID.randomUUID(), MissionKind.ASTEROID, MissionStatus.QUARANTINED,
                    id("target" + index), Integer.MAX_VALUE, Integer.MAX_VALUE));
        }
        List<RewardEntry> buffer = new ArrayList<>();
        for (int index = 0; index < SatelliteTerminalViewPacket.MAX_BUFFER_ENTRIES; index++) {
            buffer.add(new RewardEntry(id("buffer" + index), 108));
        }
        return new SatelliteTerminalViewPacket(
                Integer.MAX_VALUE,
                Optional.of(SatelliteKind.GAS_HARVESTER),
                new Selection(Optional.of(id("definition")), size - 1, size),
                new Selection(Optional.of(id("target")), size - 1, size),
                Optional.of(id("orbit")),
                new Selection(Optional.of(id("asteroid")), size - 1, size),
                Optional.of(new InstanceView(UUID.randomUUID(), Integer.MAX_VALUE, yield)),
                new Selection(Optional.of(id("product")), size - 1, size),
                size - 1,
                size,
                missions,
                buffer
        );
    }

    private static byte[] encoded(SatelliteTerminalViewPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SatelliteTerminalViewPacket.encode(packet, buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void assertRejected(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            assertThrows(DecoderException.class, () -> SatelliteTerminalViewPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /** A 128-character identifier. */
    private static ResourceLocation id(String prefix) {
        return ResourceLocation.tryParse("f:" + prefix + "x".repeat(126 - prefix.length()));
    }
}
