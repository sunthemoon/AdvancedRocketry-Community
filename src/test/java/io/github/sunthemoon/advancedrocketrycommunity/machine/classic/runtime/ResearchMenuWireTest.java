package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.inventory.SimpleContainerData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResearchMenuWireTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void actualSignedShortPacketsRoundTripEveryWideBoundary() {
        for (int value : new int[]{32_767, 32_768, 65_535, 65_536, 72_000, ResearchControllerMenu.MAX_FE}) {
            for (int low : new int[]{2, 4, 6}) {
                var source = new SimpleContainerData(16); ResearchControllerMenu.setWide(source, low, value);
                var target = new SimpleContainerData(16);
                for (int index = low; index <= low + 1; index++) {
                    var buffer = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        new ClientboundContainerSetDataPacket(1, index, source.get(index)).write(buffer);
                        var packet = new ClientboundContainerSetDataPacket(buffer); target.set(index, packet.getValue());
                    } finally { buffer.release(); }
                }
                assertEquals(value, ResearchControllerMenu.wide(target, low, ResearchControllerMenu.MAX_FE));
            }
        }
        var malformed = new SimpleContainerData(16); malformed.set(3, -1);
        assertEquals(0, ResearchControllerMenu.wide(malformed, 2, 72_000));
        assertEquals(16, ResearchControllerMenu.DATA_COUNT);
    }
    @Test void openingFrameIsFixedAndStrict() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ResearchControllerMenu.writeOpening(buffer, BlockPos.ZERO, true); assertEquals(11, buffer.readableBytes());
            assertEquals(1, ResearchControllerMenu.readOpening(buffer).get(15));
            buffer.clear(); buffer.writeZero(12); assertThrows(IllegalArgumentException.class, () -> ResearchControllerMenu.readOpening(buffer));
        } finally { buffer.release(); }
        var roles = java.util.Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
        var actual = new java.util.HashSet<Integer>();
        for (boolean processor : new boolean[]{false, true}) {
            io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns.definition(processor)
                    .cells().values().forEach(matcher -> actual.add(ResearchControllerMenu.expectedRole(matcher.summary())));
        }
        assertEquals(roles, actual);
    }
}
