package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class LifeSupportStatusPacketTest {
    @Test
    void fixedVersionedFieldsRoundTrip() {
        LifeSupportStatusPacket expected = LifeSupportStatusPacket.current(
                new PlayerLifeSupportSnapshot(
                        PlayerProtectionStatus.SUIT_OXYGEN,
                        BreathabilityState.VACUUM,
                        4,
                        1_234
                )
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LifeSupportStatusPacket.encode(expected, buffer);
            assertEquals(expected, LifeSupportStatusPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void unknownEnumsSchemaAndOutOfRangeValuesFailClosed() {
        FriendlyByteBuf unknownStatus = packetBuffer(1, 10_000, 0, 0, 0);
        try {
            assertThrows(IllegalArgumentException.class, () -> LifeSupportStatusPacket.decode(unknownStatus));
        } finally {
            unknownStatus.release();
        }

        FriendlyByteBuf futureSchema = packetBuffer(2, 0, 0, 0, 0);
        try {
            assertThrows(IllegalArgumentException.class, () -> LifeSupportStatusPacket.decode(futureSchema));
        } finally {
            futureSchema.release();
        }

        assertThrows(IllegalArgumentException.class, () -> new PlayerLifeSupportSnapshot(
                PlayerProtectionStatus.EXPOSED,
                BreathabilityState.VACUUM,
                5,
                AtmosphereLimits.SUIT_OXYGEN_CAPACITY + 1
        ));
    }

    private static FriendlyByteBuf packetBuffer(
            int schema,
            int status,
            int breathability,
            int pieces,
            int oxygen
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(schema);
        buffer.writeVarInt(status);
        buffer.writeVarInt(breathability);
        buffer.writeVarInt(pieces);
        buffer.writeVarInt(oxygen);
        return buffer;
    }
}
