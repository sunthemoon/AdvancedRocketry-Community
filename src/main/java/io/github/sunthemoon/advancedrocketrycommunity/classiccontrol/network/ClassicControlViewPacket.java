package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-065 sections 11.1 and 11.2, {@code classic_controls} index 1, server to client: the view of one open C17
 * control menu, sent only to that menu's validated holder. It carries the last action's stable code, the token of
 * the quote the menu displays (0 for none) and the kind's exact scalar values, nothing else: no inventory, no
 * native tag, no station identity. Schema byte 1, container ID, kind byte, a strict 0/1 status flag and code
 * name, the token long, the value count and the values as ints; at most {@link #MAX_BYTES}, and only in its
 * canonical encoding.
 */
public record ClassicControlViewPacket(int containerId, ClassicControlKind kind, Optional<StationManagementCode> status,
                                       long token, List<Integer> values) {
    public static final int SCHEMA = 1;
    public static final int MAX_CODE_CHARS = 64;
    /** The frozen ADR-065 bound. */
    public static final int MAX_BYTES = 8 * 1024;

    public ClassicControlViewPacket {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        values = List.copyOf(values);
        if (containerId < 1 || containerId > 255) {
            throw new IllegalArgumentException("A control view names a container 1..255");
        }
        if (token < 0L || token != 0L && kind.tokenAction() < 0) {
            throw new IllegalArgumentException("Only a kind with a confirm action shows a positive quote token");
        }
        if (!kind.validValues(values)) {
            throw new IllegalArgumentException("A " + kind + " view needs its " + kind.valueCount()
                    + " values inside their ranges: " + values);
        }
    }

    /** The largest encoded view of any kind: the longest code name and the most values. */
    public static int maximumEncodedBytes() {
        int values = Arrays.stream(ClassicControlKind.values()).mapToInt(ClassicControlKind::valueCount).max().orElse(0);
        int code = Arrays.stream(StationManagementCode.values()).mapToInt(value -> value.name().length()).max()
                .orElse(0);
        return 1 + 1 + 1 + 1 + 1 + code + Long.BYTES + 1 + values * Integer.BYTES;
    }

    public static void encode(ClassicControlViewPacket packet, FriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        buffer.writeByte(SCHEMA);
        buffer.writeByte(packet.containerId());
        buffer.writeByte(packet.kind().wireId());
        buffer.writeBoolean(packet.status().isPresent());
        packet.status().ifPresent(code -> buffer.writeUtf(code.name(), MAX_CODE_CHARS));
        buffer.writeLong(packet.token());
        buffer.writeByte(packet.values().size());
        packet.values().forEach(buffer::writeInt);
        if (buffer.writerIndex() - start > MAX_BYTES) {
            throw new EncoderException("A classic control view exceeds " + MAX_BYTES + " bytes");
        }
    }

    public static ClassicControlViewPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("A classic control view exceeds " + MAX_BYTES + " bytes");
        }
        int start = buffer.readerIndex();
        try {
            if (buffer.readUnsignedByte() != SCHEMA) {
                throw new DecoderException("Unsupported classic control view schema");
            }
            int containerId = buffer.readUnsignedByte();
            int wireId = buffer.readUnsignedByte();
            ClassicControlKind kind = ClassicControlKind.byWireId(wireId)
                    .orElseThrow(() -> new DecoderException("Unknown classic control kind " + wireId));
            byte flag = buffer.readByte();
            if (flag != 0 && flag != 1) {
                throw new DecoderException("The classic control view status flag must be 0 or 1");
            }
            Optional<StationManagementCode> status = Optional.empty();
            if (flag == 1) {
                String name = buffer.readUtf(MAX_CODE_CHARS);
                status = Optional.of(Arrays.stream(StationManagementCode.values())
                        .filter(code -> code.name().equals(name)).findFirst()
                        .orElseThrow(() -> new DecoderException("Unknown station code " + name)));
            }
            long token = buffer.readLong();
            int count = buffer.readUnsignedByte();
            if (count != kind.valueCount()) {
                throw new DecoderException("A " + kind + " view has " + kind.valueCount() + " values, not " + count);
            }
            List<Integer> values = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                values.add(buffer.readInt());
            }
            if (buffer.isReadable()) {
                throw new DecoderException("Trailing bytes after the classic control view");
            }
            ClassicControlViewPacket view = new ClassicControlViewPacket(containerId, kind, status, token, values);
            ClassicCodecs.requireCanonical(buffer, start, view, ClassicControlViewPacket::encode, "classic control view");
            return view;
        } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
            throw new DecoderException("Malformed classic control view", malformed);
        }
    }

    /** Client only: the view is kept for the screen of the container it names. */
    public static void handle(ClassicControlViewPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            ClassicControlViewCache.accept(packet);
        }
        context.setPacketHandled(true);
    }

    public int value(int index) {
        return values.get(index);
    }
}
