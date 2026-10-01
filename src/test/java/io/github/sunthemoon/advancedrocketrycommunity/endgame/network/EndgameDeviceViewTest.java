package io.github.sunthemoon.advancedrocketrycommunity.endgame.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** ADR-054 section 4: the device view stays within 8 KiB at its maximum and decodes strictly. */
final class EndgameDeviceViewTest {
    @Test
    void theLargestViewRoundTripsWithinEightKibibytes() {
        List<EndgameDeviceView.Line> lines = new ArrayList<>();
        for (int i = 0; i < EndgameDeviceView.MAX_LINES; i++) {
            String label = ("advancedrocketrycommunity.endgame.view.label_" + i + "_").repeat(2)
                    .substring(0, EndgameDeviceView.MAX_LABEL_CHARS);
            // 128 three-byte characters: the 384-byte value maximum.
            lines.add(new EndgameDeviceView.Line(label, "界".repeat(128), i % 2 == 0));
        }
        EndgameDeviceView view = new EndgameDeviceView(Integer.MAX_VALUE, EndgameSystem.BLACK_HOLE_GENERATOR,
                EndgameCode.ENDPOINT_POSITION_CONFLICT, EndgameCode.ENDPOINT_CHUNK_LOADED, true, lines);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        EndgameDeviceView.encode(view, buffer);
        assertTrue(buffer.readableBytes() <= EndgameDeviceView.MAX_BYTES, "encoded " + buffer.readableBytes() + " B");
        assertTrue(buffer.readableBytes() > 7 * 1024, "the test is at the maximum");
        assertEquals(view, EndgameDeviceView.decode(buffer));
    }

    @Test
    void codesAndSystemsTravelByName() {
        EndgameDeviceView view = new EndgameDeviceView(3, EndgameSystem.LASER_DRILL, EndgameCode.OUTPUT_FULL,
                EndgameCode.OK, false, List.of(EndgameDeviceView.Line.text("advancedrocketrycommunity.view.ops", "12")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        EndgameDeviceView.encode(view, buffer);
        buffer.readVarInt();
        assertEquals("laser_drill", buffer.readUtf());
        assertEquals("OUTPUT_FULL", buffer.readUtf());
    }

    @Test
    void malformedViewsAreRejected() {
        assertThrows(DecoderException.class, () -> EndgameDeviceView.decode(raw(buffer -> {
            buffer.writeVarInt(1);
            buffer.writeUtf("not_a_system");
        })));
        assertThrows(DecoderException.class, () -> EndgameDeviceView.decode(raw(buffer -> {
            header(buffer, "NOT_A_CODE");
        })));
        assertThrows(DecoderException.class, () -> EndgameDeviceView.decode(raw(buffer -> {
            header(buffer, "OK");
            buffer.writeVarInt(EndgameDeviceView.MAX_LINES + 1);
        })));
        assertThrows(DecoderException.class, () -> EndgameDeviceView.decode(raw(buffer -> {
            header(buffer, "OK");
            buffer.writeVarInt(1);
            buffer.writeUtf("Upper Case Label");
            buffer.writeUtf("v");
            buffer.writeBoolean(false);
        })));
        assertThrows(DecoderException.class, () -> EndgameDeviceView.decode(raw(buffer -> {
            header(buffer, "OK");
            buffer.writeVarInt(1);
            buffer.writeUtf("a.label");
            buffer.writeUtf("界".repeat(129));
            buffer.writeBoolean(false);
        })), "a value over 384 bytes");
        assertThrows(DecoderException.class, () -> EndgameDeviceView.decode(raw(buffer -> {
            header(buffer, "OK");
            buffer.writeVarInt(0);
            buffer.writeByte(0);
        })), "trailing bytes");
        assertThrows(DecoderException.class, () -> EndgameDeviceView.decode(raw(buffer -> buffer.writeBytes(
                new byte[EndgameDeviceView.MAX_BYTES + 1]))), "oversized");
        assertThrows(IllegalArgumentException.class, () -> new EndgameDeviceView.Line("a.b", "x".repeat(385), false));
    }

    @Test
    void theChannelIsProtocolOne() {
        assertEquals("1", EndgameNetwork.protocolVersion());
    }

    private static void header(FriendlyByteBuf buffer, String status) {
        buffer.writeVarInt(1);
        buffer.writeUtf("laser_drill");
        buffer.writeUtf(status);
        buffer.writeUtf("OK");
        buffer.writeBoolean(true);
    }

    private static FriendlyByteBuf raw(java.util.function.Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buffer);
        return buffer;
    }
}
