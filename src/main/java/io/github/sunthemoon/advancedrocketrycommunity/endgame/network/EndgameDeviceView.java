package io.github.sunthemoon.advancedrocketrycommunity.endgame.network;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-054 section 4: the device view, the only message of the S2C {@code endgame} channel. It carries the device
 * status and codes (by name), and bounded labelled lines: settings, the current selection only (label, position in
 * the list and list size), counts and buffer summaries. Per-tick numbers stay in menu data slots. A view for a
 * viewer without detail carries no target, coordinate, owner name or other device's ID; the server builds it so.
 * The encoded view is at most 8 KiB, and the client shows it only for the open menu it names.
 */
public record EndgameDeviceView(int containerId, EndgameSystem system, EndgameCode status, EndgameCode lastStop,
                                boolean detail, List<Line> lines) {
    public static final int MAX_BYTES = 8 * 1024;
    public static final int MAX_LINES = 16;
    public static final int MAX_LABEL_CHARS = 64;
    public static final int MAX_VALUE_BYTES = 384;
    private static final int MAX_NAME_CHARS = 64;

    public EndgameDeviceView {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(lastStop, "lastStop");
        lines = List.copyOf(lines);
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("A device view has at most " + MAX_LINES + " lines");
        }
    }

    /**
     * One labelled value. The label is a translation key; a translated value is a translation key too, otherwise
     * it is shown as text.
     */
    public record Line(String label, String value, boolean translated) {
        public Line {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
            if (label.isEmpty() || label.length() > MAX_LABEL_CHARS || !label.matches("[a-z0-9_.]+")) {
                throw new IllegalArgumentException("A view label is a translation key of 1.." + MAX_LABEL_CHARS
                        + " characters");
            }
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
                throw new IllegalArgumentException("A view value exceeds " + MAX_VALUE_BYTES + " bytes");
            }
        }

        /** A text value, cut to the byte bound so no server-side value can fail the view (review C11R-L3). */
        public static Line text(String label, String value) {
            return new Line(label, fit(value), false);
        }

        private static String fit(String value) {
            if (value.getBytes(StandardCharsets.UTF_8).length <= MAX_VALUE_BYTES) {
                return value;
            }
            int end = Math.min(value.length(), MAX_VALUE_BYTES);
            while (end > 0 && value.substring(0, end).getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES - 3) {
                end--;
            }
            if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
                end--;
            }
            return value.substring(0, end) + "...";
        }

        public static Line key(String label, String translationKey) {
            return new Line(label, translationKey, true);
        }
    }

    public static void encode(EndgameDeviceView view, FriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        buffer.writeVarInt(view.containerId());
        buffer.writeUtf(view.system().id(), MAX_NAME_CHARS);
        buffer.writeUtf(view.status().name(), MAX_NAME_CHARS);
        buffer.writeUtf(view.lastStop().name(), MAX_NAME_CHARS);
        buffer.writeBoolean(view.detail());
        buffer.writeVarInt(view.lines().size());
        for (Line line : view.lines()) {
            buffer.writeUtf(line.label(), MAX_LABEL_CHARS);
            buffer.writeUtf(line.value(), MAX_VALUE_BYTES);
            buffer.writeBoolean(line.translated());
        }
        if (buffer.writerIndex() - start > MAX_BYTES) {
            throw new EncoderException("An endgame device view exceeds " + MAX_BYTES + " bytes");
        }
    }

    public static EndgameDeviceView decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("An endgame device view exceeds " + MAX_BYTES + " bytes");
        }
        try {
            int containerId = buffer.readVarInt();
            EndgameSystem system = EndgameSystem.byId(buffer.readUtf(MAX_NAME_CHARS))
                    .orElseThrow(() -> new DecoderException("Unknown endgame system"));
            EndgameCode status = code(buffer.readUtf(MAX_NAME_CHARS));
            EndgameCode lastStop = code(buffer.readUtf(MAX_NAME_CHARS));
            boolean detail = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_LINES) {
                throw new DecoderException("Too many device view lines");
            }
            List<Line> lines = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                lines.add(new Line(buffer.readUtf(MAX_LABEL_CHARS), buffer.readUtf(MAX_VALUE_BYTES),
                        buffer.readBoolean()));
            }
            if (buffer.isReadable()) {
                throw new DecoderException("Trailing bytes after the endgame device view");
            }
            return new EndgameDeviceView(containerId, system, status, lastStop, detail, lines);
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw new DecoderException("Malformed endgame device view", exception);
        }
    }

    public static void handle(EndgameDeviceView view, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            EndgameDeviceViewCache.accept(view);
        }
        context.setPacketHandled(true);
    }

    public Optional<Line> line(String label) {
        return lines.stream().filter(line -> line.label().equals(label)).findFirst();
    }

    private static EndgameCode code(String name) {
        return EndgameCode.byName(name).orElseThrow(() -> new DecoderException("Unknown endgame code " + name));
    }
}
