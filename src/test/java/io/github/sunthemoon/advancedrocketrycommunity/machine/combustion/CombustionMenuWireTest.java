package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class CombustionMenuWireTest {
    @Test void supportedOpenPayloadIsExactlyTwoBoundedVarints() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CombustionGeneratorMenu.writeOpenData(buffer);
            assertEquals(6, buffer.readableBytes());
            assertEquals(CombustionGeneratorMenu.DATA_COUNT, CombustionGeneratorMenu.readOpenData(buffer).getCount());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void wrongFutureTruncatedAndTrailingPayloadsAreRefused() {
        int[][] forms = {{0, 1}, {-1, 2}, {-1, 0}};
        for (int[] form : forms) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(form[0]).writeVarInt(form[1]);
                assertThrows(IllegalArgumentException.class, () -> CombustionGeneratorMenu.readOpenData(buffer));
            } finally { buffer.release(); }
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(RuntimeException.class, () -> CombustionGeneratorMenu.readOpenData(buffer));
            CombustionGeneratorMenu.writeOpenData(buffer);
            buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> CombustionGeneratorMenu.readOpenData(buffer));
        } finally { buffer.release(); }
    }
}
