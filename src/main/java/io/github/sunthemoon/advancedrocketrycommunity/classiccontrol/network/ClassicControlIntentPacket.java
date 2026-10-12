package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import io.netty.handler.codec.DecoderException;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-065 sections 11.1 and 11.2, {@code classic_controls} index 0, client to server: one action on the open C17
 * control menu. It names the menu by its container ID and kind and carries the action, a bounded value and a
 * quote token, never NBT, coordinates, amounts or a cost. Schema byte 1, then the container ID (1..255), the kind
 * byte, the action, the value and the token long: exactly {@link #ENCODED_BYTES}, at most {@link #MAX_BYTES}.
 * The server checks everything again: the open menu, its holder and every station rule.
 */
public record ClassicControlIntentPacket(int containerId, ClassicControlKind kind, int action, int value, long token) {
    public static final int SCHEMA = 1;
    public static final int ENCODED_BYTES = 1 + 1 + 1 + 1 + 1 + Long.BYTES;
    /** The frozen ADR-065 bound; a larger frame is a decoding error before anything is read. */
    public static final int MAX_BYTES = 512;

    public ClassicControlIntentPacket {
        Objects.requireNonNull(kind, "kind");
        if (containerId < 1 || containerId > 255) {
            throw new IllegalArgumentException("A control intent names a container 1..255");
        }
        if (action < 0 || action >= kind.actions()) {
            throw new IllegalArgumentException("Unknown " + kind + " action " + action);
        }
        int valueMaximum = action == kind.valueAction() ? kind.valueMaximum() : 0;
        if (value < 0 || value > valueMaximum) {
            throw new IllegalArgumentException("A " + kind + " action " + action + " value is outside 0.." + valueMaximum);
        }
        if (action == kind.tokenAction() ? token <= 0L : token != 0L) {
            throw new IllegalArgumentException("Only " + kind + " action " + kind.tokenAction()
                    + " carries a positive quote token");
        }
    }

    public static void encode(ClassicControlIntentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(SCHEMA);
        buffer.writeByte(packet.containerId());
        buffer.writeByte(packet.kind().wireId());
        buffer.writeByte(packet.action());
        buffer.writeByte(packet.value());
        buffer.writeLong(packet.token());
    }

    public static ClassicControlIntentPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("A classic control intent exceeds " + MAX_BYTES + " bytes");
        }
        try {
            if (buffer.readUnsignedByte() != SCHEMA) {
                throw new DecoderException("Unsupported classic control intent schema");
            }
            int containerId = buffer.readUnsignedByte();
            int wireId = buffer.readUnsignedByte();
            ClassicControlKind kind = ClassicControlKind.byWireId(wireId)
                    .orElseThrow(() -> new DecoderException("Unknown classic control kind " + wireId));
            int action = buffer.readUnsignedByte();
            int value = buffer.readUnsignedByte();
            long token = buffer.readLong();
            if (buffer.isReadable()) {
                throw new DecoderException("Trailing bytes after the classic control intent");
            }
            return new ClassicControlIntentPacket(containerId, kind, action, value, token);
        } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
            throw new DecoderException("Malformed classic control intent", malformed);
        }
    }

    /** Main thread, server side only: hands the intent to the sender's open menu of the same container and kind. */
    public static void handle(ClassicControlIntentPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER && sender != null
                && sender.containerMenu != null && sender.containerMenu.containerId == packet.containerId()
                && sender.containerMenu instanceof ClassicControlTarget target
                && target.classicControlKind() == packet.kind()) {
            target.acceptClassicControl(sender, packet);
        }
        context.setPacketHandled(true);
    }
}
