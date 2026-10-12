package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class JetpackPacketCodecTest {
    private static FriendlyByteBuf bytes(int... values) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        for (int value : values) { buffer.writeByte(value); }
        return buffer;
    }

    @Test void everyIntentRoundTripsInExactlyTwoBytes() {
        for (JetpackIntentPacket intent : List.of(JetpackIntentPacket.toggle(), JetpackIntentPacket.cycleMode(),
                JetpackIntentPacket.thrust(true), JetpackIntentPacket.thrust(false))) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            intent.encode(buffer);
            assertEquals(JetpackIntentPacket.ENCODED_BYTES, buffer.readableBytes());
            assertEquals(intent, JetpackIntentPacket.decode(buffer));
            assertFalse(buffer.isReadable());
        }
        assertTrue(JetpackIntentPacket.ENCODED_BYTES <= 64, "ADR-066 section 2 ordinary intent bound");
    }

    @Test void truncatedTrailingUnknownAndNonBooleanFramesAreRefused() {
        for (FriendlyByteBuf frame : List.of(bytes(), bytes(2), bytes(2, 1, 0), bytes(3, 0), bytes(255, 0),
                bytes(2, 2), bytes(2, 255), bytes(0, 1), bytes(1, 1))) {
            assertThrows(IllegalArgumentException.class, () -> JetpackIntentPacket.decode(frame));
        }
        assertThrows(IllegalArgumentException.class, () -> new JetpackIntentPacket(JetpackIntentPacket.Action.TOGGLE, true));
        assertThrows(NullPointerException.class, () -> new JetpackIntentPacket(null, false));
    }

    @Test void impulsesRoundTripAndTheClientRefusesValuesOutsideTheServerBounds() {
        JetpackImpulsePacket packet = JetpackImpulsePacket.of(new JetpackPhysics.Impulse(0.012, 0.104, -0.016));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buffer);
        assertEquals(JetpackImpulsePacket.ENCODED_BYTES, buffer.readableBytes());
        assertEquals(packet, JetpackImpulsePacket.decode(buffer));
        assertEquals(0.104, packet.impulse().y(), 1.0E-7);
        float[][] refused = {{0, 0.33F, 0}, {0, -0.01F, 0}, {0.07F, 0.1F, 0}, {0.05F, 0, 0.05F},
                {Float.NaN, 0.1F, 0}, {0, Float.POSITIVE_INFINITY, 0}};
        for (float[] values : refused) {
            FriendlyByteBuf frame = new FriendlyByteBuf(Unpooled.buffer());
            frame.writeFloat(values[0]).writeFloat(values[1]).writeFloat(values[2]);
            assertThrows(IllegalArgumentException.class, () -> JetpackImpulsePacket.decode(frame));
        }
        FriendlyByteBuf trailing = new FriendlyByteBuf(Unpooled.buffer());
        trailing.writeFloat(0).writeFloat(0.1F).writeFloat(0).writeByte(0);
        assertThrows(IllegalArgumentException.class, () -> JetpackImpulsePacket.decode(trailing));
        assertThrows(IllegalArgumentException.class, () -> JetpackImpulsePacket.decode(bytes(0, 0, 0, 0)));
    }
}
