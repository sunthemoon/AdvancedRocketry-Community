package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.SimpleContainerData;
import org.junit.jupiter.api.Test;

class SolarMenuWireTest {
    @Test void openFormatIsExactlySixBytesAndSevenScalarSlots() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SolarGeneratorMenu.writeOpenData(buffer);
            assertEquals(6, buffer.readableBytes());
            assertEquals(7, SolarGeneratorMenu.readOpenData(buffer).getCount());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
    @Test void futureTruncatedOverlongAndTrailingInputsFailClosed() {
        for (int[] pair : new int[][]{{0, 1}, {-1, 0}, {-1, 2}, {Integer.MAX_VALUE, 1}}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { buffer.writeVarInt(pair[0]).writeVarInt(pair[1]);
                assertThrows(IllegalArgumentException.class, () -> SolarGeneratorMenu.readOpenData(buffer));
            } finally { buffer.release(); }
        }
        for (int length = 0; length < 6; length++) {
            FriendlyByteBuf whole = new FriendlyByteBuf(Unpooled.buffer()), partial = new FriendlyByteBuf(Unpooled.buffer());
            try { SolarGeneratorMenu.writeOpenData(whole); partial.writeBytes(whole, length);
                assertThrows(RuntimeException.class, () -> SolarGeneratorMenu.readOpenData(partial));
            } finally { whole.release(); partial.release(); }
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SolarGeneratorMenu.writeOpenData(buffer); buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> SolarGeneratorMenu.readOpenData(buffer));
            buffer.clear(); for (int index = 0; index < 6; index++) { buffer.writeByte(0x80); }
            assertThrows(RuntimeException.class, () -> SolarGeneratorMenu.readOpenData(buffer));
        } finally { buffer.release(); }
    }
    @Test void allSevenMaximaFitSignedShortTransportWithoutTruncation() {
        SimpleContainerData data = data(new int[]{10_000, 128, 0, 1, 2, 3, 1_000});
        var view = SolarGeneratorMenu.project(data);
        assertEquals(10_000, view.energy()); assertEquals(128, view.credit());
        assertEquals(SolarGeneration.Reason.GENERATING, view.reason());
        assertTrue(view.sky()); assertEquals(2, view.day()); assertEquals(3, view.context()); assertEquals(1_000, view.weather());
    }
    @Test void malformedScalarsAndUnknownCodesAreNotPlausibleFeedback() {
        int[] valid = {777, 2, 0, 1, 1, 1, 1_000};
        int[] maxima = {10_000, 128, 7, 1, 2, 3, 1_000};
        for (int slot = 0; slot < 7; slot++) {
            for (int invalid : new int[]{-1, maxima[slot] + 1, Integer.MAX_VALUE}) {
                int[] changed = valid.clone(); changed[slot] = invalid;
                var view = SolarGeneratorMenu.project(data(changed));
                assertEquals(SolarGeneration.Reason.REPAIR_REQUIRED, view.reason());
                assertEquals(0, view.credit()); assertEquals(0, view.energy());
            }
        }
        int[] changed = valid.clone(); changed[6] = 249;
        assertEquals(SolarGeneration.Reason.REPAIR_REQUIRED, SolarGeneratorMenu.project(data(changed)).reason());
        assertEquals(SolarGeneration.Reason.REPAIR_REQUIRED, SolarGeneration.Reason.fromCode(8));
    }
    private static SimpleContainerData data(int[] values) {
        SimpleContainerData data = new SimpleContainerData(7);
        for (int slot = 0; slot < values.length; slot++) { data.set(slot, (short) values[slot]); }
        return data;
    }
}
