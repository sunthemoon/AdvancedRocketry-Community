package io.github.sunthemoon.advancedrocketrycommunity.satellite.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-049 sections 8 and 10: the scan result stays within 8 KiB at its maximum and decodes strictly. */
final class SurveyScanResultPacketTest {
    @Test
    void theMaximumResultRoundTripsWithinItsBound() {
        SurveyScanResultPacket maximum = maximum();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SurveyScanResultPacket.encode(maximum, buffer);
            int bytes = buffer.readableBytes();
            System.out.println("ARCE_SCAN_RESULT_MAX_BYTES=" + bytes);
            assertEquals(3_819, bytes, "576 cells of 3 bytes, 16 palette IDs of 130 bytes and an 11-byte header");
            assertEquals(maximum, SurveyScanResultPacket.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void malformedResultsAreDecodingErrors() {
        byte[] valid = encoded(maximum());
        assertRejected(buffer -> {
            buffer.writeBytes(valid);
            buffer.writeByte(0);
        });
        assertRejected(buffer -> buffer.writeBytes(valid, 0, valid.length - 1));
        assertRejected(buffer -> buffer.writeBytes(new byte[SurveyScanResultPacket.MAX_BYTES + 1]));
        for (int[] geometry : new int[][] {{15, 4}, {50, 4}, {20, 8}, {16, 2}}) {
            assertRejected(buffer -> {
                buffer.writeInt(0);
                buffer.writeInt(0);
                buffer.writeByte(geometry[0]);
                buffer.writeByte(geometry[1]);
                buffer.writeByte(0);
            });
        }
        assertRejected(buffer -> {
            header(buffer, 17);
        });
        // A cell naming a palette entry that does not exist, and an UNKNOWN cell with a ratio.
        for (int[] cell : new int[][] {{0, 0}, {1, 0xFF}}) {
            assertRejected(buffer -> {
                header(buffer, 0);
                for (int index = 0; index < 4; index++) {
                    buffer.writeShort(cell[0]);
                    buffer.writeByte(cell[1]);
                }
            });
        }
    }

    @Test
    void constructorBoundsHold() {
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanResultPacket.Cell(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanResultPacket.Cell(65_536, 0));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanResultPacket.Cell(0, 16));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanResultPacket.Cell(5, 0xFF));
        List<SurveyScanResultPacket.Cell> four = List.of(SurveyScanResultPacket.Cell.UNKNOWN_CELL,
                SurveyScanResultPacket.Cell.UNKNOWN_CELL, SurveyScanResultPacket.Cell.UNKNOWN_CELL,
                SurveyScanResultPacket.Cell.UNKNOWN_CELL);
        assertThrows(IllegalArgumentException.class,
                () -> new SurveyScanResultPacket(0, 0, 16, 8, List.of(), four));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanResultPacket(0, 0, 16, 16,
                List.of(id("a"), id("a")), four));
    }

    private static void header(FriendlyByteBuf buffer, int palette) {
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeByte(16);
        buffer.writeByte(16);
        buffer.writeByte(palette);
    }

    private static SurveyScanResultPacket maximum() {
        List<ResourceLocation> palette = new ArrayList<>();
        for (int index = 0; index < SurveyScanResultPacket.MAX_PALETTE; index++) {
            palette.add(id("biome" + index));
        }
        List<SurveyScanResultPacket.Cell> cells = new ArrayList<>();
        for (int index = 0; index < 576; index++) {
            cells.add(index % 17 == 16 ? SurveyScanResultPacket.Cell.UNKNOWN_CELL
                    : new SurveyScanResultPacket.Cell(65_535 - index, index % 17 == 15 ? 0xFE : index % 16));
        }
        return new SurveyScanResultPacket(Integer.MIN_VALUE, Integer.MAX_VALUE, 48, 4, palette, cells);
    }

    private static byte[] encoded(SurveyScanResultPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SurveyScanResultPacket.encode(packet, buffer);
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
            assertThrows(DecoderException.class, () -> SurveyScanResultPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /** A 128-character identifier. */
    private static ResourceLocation id(String prefix) {
        return ResourceLocation.tryParse("f:" + prefix + "x".repeat(126 - prefix.length()));
    }
}
