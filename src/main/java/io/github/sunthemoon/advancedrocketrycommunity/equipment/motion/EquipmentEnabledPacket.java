package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.client.equipment.SuitVisorClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** ADR-066 section 5.2: one server-authorized boolean, on suit_motion protocol 2. */
public record EquipmentEnabledPacket(boolean enabled) {
    public static final int ENCODED_BYTES = 1;

    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(enabled ? 1 : 0); }

    public static EquipmentEnabledPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() != ENCODED_BYTES) {
            throw new IllegalArgumentException("Equipment enabled frame must be exactly one byte");
        }
        int value = buffer.readUnsignedByte();
        if (value > 1) { throw new IllegalArgumentException("Equipment enabled value must be boolean"); }
        return new EquipmentEnabledPacket(value == 1);
    }

    public static void handle(EquipmentEnabledPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SuitVisorClient.authorize(packet.enabled()));
        context.get().setPacketHandled(true);
    }
}
