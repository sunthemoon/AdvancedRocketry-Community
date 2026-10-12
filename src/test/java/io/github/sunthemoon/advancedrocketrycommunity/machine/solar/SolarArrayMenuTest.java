package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.SimpleContainerData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SolarArrayMenuTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void arrayOpenFormatIsSixBytesNineScalarsAndDistinctFromSingle() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SolarArrayMenu.writeOpenData(buffer); assertEquals(6, buffer.readableBytes());
            assertEquals(9, SolarArrayMenu.readOpenData(buffer).getCount());
            buffer.clear(); SolarGeneratorMenu.writeOpenData(buffer);
            assertThrows(IllegalArgumentException.class, () -> SolarArrayMenu.readOpenData(buffer));
            buffer.clear(); SolarArrayMenu.writeOpenData(buffer); buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> SolarArrayMenu.readOpenData(buffer));
        } finally { buffer.release(); }
    }
    @Test void maximaFitSignedShortTransportAndMalformedScalarsFailClosed() {
        int[] maxima = {20_000, 8_064, 7, 1, 2, 3, 1_000, 6, 63};
        var data = new SimpleContainerData(9);
        for (int i = 0; i < 9; i++) { data.set(i, (short) maxima[i]); }
        assertEquals(20_000, SolarArrayMenu.project(data).energy()); assertEquals(8_064, SolarArrayMenu.project(data).credit());
        assertEquals(63, SolarArrayMenu.project(data).collectors());
        for (int i = 0; i < 9; i++) {
            for (int bad : new int[]{-1, maxima[i] + 1}) {
                int previous = data.get(i); data.set(i, bad);
                var view = SolarArrayMenu.project(data);
                assertEquals(0, view.credit()); assertEquals(SolarArrayRules.Structure.UNAVAILABLE, view.structure()); data.set(i, previous);
            }
        }
    }
}
