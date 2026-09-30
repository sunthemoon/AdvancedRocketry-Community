package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SatelliteTerminalTargetsTest {
    @Test void maximumCatalogRoundTripsUnderActualForgeExtraDataBudget() {
        List<ResourceLocation> targets = java.util.stream.IntStream.range(0, 128).mapToObj(i -> id("body" + i)).toList();
        var entries = new ArrayList<SatelliteTerminalTargets.Entry>();
        for (int i = 0; i < 16; i++) {
            entries.add(new SatelliteTerminalTargets.Entry(id("definition" + i), targets.subList(i % 8 * 16, i % 8 * 16 + 16)));
        }
        var original = new SatelliteTerminalTargets(Long.MAX_VALUE, entries);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBlockPos(BlockPos.ZERO); original.write(buffer);
            buffer.writeBoolean(true); // Format 2: the terminal-view flag follows the format-1 content.
            // NetworkHooks prefixes the complete additional buffer with its VarInt length.
            assertTrue(buffer.readableBytes() + FriendlyByteBuf.getVarIntSize(buffer.readableBytes()) <= 32600);
            assertEquals(BlockPos.ZERO, buffer.readBlockPos());
            assertEquals(original, SatelliteTerminalTargets.read(buffer));
            assertTrue(buffer.readBoolean()); assertFalse(buffer.isReadable());
            assertEquals(15, original.indexOf(id("definition15")));
            assertEquals(-1, original.indexOf(id("absent")));
            assertTrue(original.targets(-1).isEmpty()); assertTrue(original.targets(16).isEmpty());
        } finally { buffer.release(); }
    }

    @Test void malformedCountsVersionsIndicesDuplicatesAndIdsAreRejected() {
        var entry = new SatelliteTerminalTargets.Entry(id("def"), List.of(id("target")));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteTerminalTargets(1, List.of(entry, entry)));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteTerminalTargets(-1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteTerminalTargets.Entry(id("def"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteTerminalTargets.Entry(id("def"), List.of(id("x"), id("x"))));
        for (int bad : new int[]{-2, 0, 1, 16, 129}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { buffer.writeVarInt(bad); assertThrows(IllegalArgumentException.class, () -> SatelliteTerminalTargets.read(buffer)); }
            finally { buffer.release(); }
        }
        for (int count : new int[]{-1, 129, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(-1); buffer.writeVarInt(2); buffer.writeLong(1); buffer.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> SatelliteTerminalTargets.read(buffer));
            } finally { buffer.release(); }
        }
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(-1); buffer.writeVarInt(2); buffer.writeLong(1); buffer.writeVarInt(1); buffer.writeUtf("a:b");
            buffer.writeVarInt(1); buffer.writeUtf("a:def"); buffer.writeVarInt(1); buffer.writeVarInt(1);
            assertThrows(IllegalArgumentException.class, () -> SatelliteTerminalTargets.read(buffer));
        } finally { buffer.release(); }
    }

    @Test void formatOneMenuDataIsRejected() {
        var entry = new SatelliteTerminalTargets.Entry(id("def"), List.of(id("target")));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SatelliteTerminalTargets(3, List.of(entry)).write(buffer);
            assertEquals(-1, buffer.readVarInt());
            assertEquals(2, buffer.readVarInt());
            buffer.readerIndex(0);
            var legacy = new FriendlyByteBuf(Unpooled.buffer());
            try {
                legacy.writeVarInt(-1); legacy.writeVarInt(1);
                legacy.writeBytes(buffer, 2, buffer.readableBytes() - 2);
                assertThrows(IllegalArgumentException.class, () -> SatelliteTerminalTargets.read(legacy));
            } finally { legacy.release(); }
        } finally { buffer.release(); }
    }

    private static ResourceLocation id(String prefix) {
        return ResourceLocation.tryParse("f:" + prefix + "x".repeat(126 - prefix.length()));
    }
}
