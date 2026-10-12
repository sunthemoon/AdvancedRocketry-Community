package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class EquipmentEnabledPacketTest {
    @Test void bothServerValuesRoundTripInOneByte() {
        for (boolean enabled : new boolean[] {false, true}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var packet = new EquipmentEnabledPacket(enabled);
                packet.encode(buffer);
                assertEquals(EquipmentEnabledPacket.ENCODED_BYTES, buffer.readableBytes());
                assertEquals(packet, EquipmentEnabledPacket.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
        assertEquals("2", MotionNetwork.protocolVersion());
    }

    @Test void truncatedTrailingAndNonBooleanFramesAreRefused() {
        for (byte[] bytes : new byte[][] {{}, {0, 1}, {2}, {(byte) 255}}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try { assertThrows(IllegalArgumentException.class, () -> EquipmentEnabledPacket.decode(buffer)); }
            finally { buffer.release(); }
        }
    }
}
