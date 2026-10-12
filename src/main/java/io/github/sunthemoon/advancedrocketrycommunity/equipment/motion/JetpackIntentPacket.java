package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-066 section 5.2 C2S intent: an exact two-byte frame {@code [action][value]}. Only {@code THRUST} carries a
 * value, the held boolean. It never carries velocity, height, position, resources or NBT.
 */
public record JetpackIntentPacket(Action action, boolean held) {
    public static final int ENCODED_BYTES = 2;

    public enum Action { TOGGLE, CYCLE_MODE, THRUST }

    public JetpackIntentPacket {
        Objects.requireNonNull(action, "action");
        if (held && action != Action.THRUST) { throw new IllegalArgumentException("Only THRUST carries a value"); }
    }

    public static JetpackIntentPacket toggle() { return new JetpackIntentPacket(Action.TOGGLE, false); }
    public static JetpackIntentPacket cycleMode() { return new JetpackIntentPacket(Action.CYCLE_MODE, false); }
    public static JetpackIntentPacket thrust(boolean held) { return new JetpackIntentPacket(Action.THRUST, held); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action.ordinal());
        buffer.writeByte(held ? 1 : 0);
    }

    /** Rejects any other length, unknown action, non-boolean value or trailing byte without side effects. */
    public static JetpackIntentPacket decode(FriendlyByteBuf buffer) {
        int frame = buffer.readableBytes();
        if (frame != ENCODED_BYTES) {
            throw new IllegalArgumentException("Jetpack intent frame must be exactly 2 bytes, not " + frame);
        }
        int action = buffer.readUnsignedByte();
        int value = buffer.readUnsignedByte();
        if (action >= Action.values().length || value > 1) {
            throw new IllegalArgumentException("Unknown jetpack intent " + action + "/" + value);
        }
        return new JetpackIntentPacket(Action.values()[action], value == 1);
    }

    /** Registered with {@code consumerMainThread}: this already runs on the server thread. */
    public static void handle(JetpackIntentPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            SuitMotionEvents.service().ifPresent(service -> service.receive(sender, packet, sender.server.getTickCount()));
        }
        context.get().setPacketHandled(true);
    }
}
