package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-065 section 11.1: the three {@code classic_controls} messages. Every frame stays inside its frozen bound, a
 * deliberately malformed frame is a {@link DecoderException}, and only protocol 1 is accepted.
 */
final class ClassicControlPacketsTest {
    private static final ClassicControlKind WARP = ClassicControlKind.WARP_CONTROLLER;
    private static final ClassicControlKind ORIENTATION = ClassicControlKind.STATION_ORIENTATION_CONTROLLER;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void onlyTheExactProtocolIsAccepted() {
        assertEquals("1", ClassicControlsNetwork.protocolVersion());
        assertTrue(ClassicControlsNetwork.acceptsProtocol("1"));
        for (String version : new String[] {null, "", "0", "2", "01", "1 ", "1.0",
                NetworkRegistry.ABSENT.version(), NetworkRegistry.ACCEPTVANILLA}) {
            assertFalse(ClassicControlsNetwork.acceptsProtocol(version), "version " + version);
        }
    }

    @Test
    void everyIntentOfEveryKindRoundTripsInItsFixedSize() {
        for (ClassicControlKind kind : ClassicControlKind.values()) {
            for (int action = 0; action < kind.actions(); action++) {
                int value = action == kind.valueAction() ? kind.valueMaximum() : 0;
                long token = action == kind.tokenAction() ? Long.MAX_VALUE : 0L;
                ClassicControlIntentPacket intent = new ClassicControlIntentPacket(255, kind, action, value, token);
                FriendlyByteBuf buffer = encode(intent, ClassicControlIntentPacket::encode);
                assertEquals(ClassicControlIntentPacket.ENCODED_BYTES, buffer.readableBytes());
                assertTrue(buffer.readableBytes() <= ClassicControlIntentPacket.MAX_BYTES);
                assertEquals(intent, ClassicControlIntentPacket.decode(buffer));
            }
        }
    }

    @Test
    void intentConstructionRefusesValuesOutsideTheKindsTable() {
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(0, WARP, 0, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(256, WARP, 0, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, WARP, 5, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, WARP, -1, 0, 0L));
        // Only the request carries a value, at most 31; only the confirmation carries a positive token.
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, WARP, 2, 32, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, WARP, 1, 1, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, WARP, 3, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, WARP, 3, 0, -1L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, WARP, 4, 0, 7L));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlIntentPacket(1, ORIENTATION, 19, 0, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClassicControlIntentPacket(1, ClassicControlKind.STATION_GRAVITY_CONTROLLER, 0, 0, 1L));
    }

    @Test
    void malformedIntentFramesAreDecoderExceptions() {
        List<byte[]> frames = List.of(
                intent(2, 1, 0, 1, 0, 0L),   // schema 2
                intent(1, 1, 4, 0, 0, 0L),   // unknown kind
                intent(1, 0, 0, 0, 0, 0L),   // container 0
                intent(1, 1, 0, 5, 0, 0L),   // warp action 5
                intent(1, 1, 0, 2, 32, 0L),  // request value 32
                intent(1, 1, 0, 0, 1, 0L),   // a value on PREVIOUS
                intent(1, 1, 0, 3, 0, 0L),   // CONFIRM without a token
                intent(1, 1, 0, 3, 0, -5L),  // a negative token
                intent(1, 1, 0, 4, 0, 9L),   // a token on CANCEL
                intent(1, 1, 3, 19, 0, 0L)); // orientation action 19
        for (int index = 0; index < frames.size(); index++) {
            FriendlyByteBuf buffer = wrap(frames.get(index));
            assertThrows(DecoderException.class, () -> ClassicControlIntentPacket.decode(buffer), "frame " + index);
        }
        byte[] valid = intent(1, 1, 0, 3, 0, 42L);
        assertEquals(new ClassicControlIntentPacket(1, WARP, 3, 0, 42L), ClassicControlIntentPacket.decode(wrap(valid)));
        assertThrows(DecoderException.class, () -> ClassicControlIntentPacket.decode(wrap(append(valid, 0))));
        for (int length = 0; length < valid.length; length++) {
            FriendlyByteBuf truncated = wrap(Arrays.copyOf(valid, length));
            assertThrows(DecoderException.class, () -> ClassicControlIntentPacket.decode(truncated), "prefix " + length);
        }
        FriendlyByteBuf oversized = wrap(Arrays.copyOf(valid, ClassicControlIntentPacket.MAX_BYTES + 1));
        assertThrows(DecoderException.class, () -> ClassicControlIntentPacket.decode(oversized));
        assertEquals(0, oversized.readerIndex(), "An oversized intent was read before its bound was checked");
    }

    @Test
    void theLargestViewOfEveryKindStaysInsideItsBound() {
        StationManagementCode longest = Collections.max(Arrays.asList(StationManagementCode.values()),
                (left, right) -> Integer.compare(left.name().length(), right.name().length()));
        int largest = 0;
        for (ClassicControlKind kind : ClassicControlKind.values()) {
            List<Integer> values = new ArrayList<>();
            for (int index = 0; index < kind.valueCount(); index++) {
                values.add(kind.maximum(index));
            }
            ClassicControlViewPacket view = new ClassicControlViewPacket(255, kind, Optional.of(longest),
                    kind.tokenAction() >= 0 ? Long.MAX_VALUE : 0L, values);
            FriendlyByteBuf buffer = encode(view, ClassicControlViewPacket::encode);
            largest = Math.max(largest, buffer.readableBytes());
            assertEquals(view, ClassicControlViewPacket.decode(buffer));
            List<Integer> minimums = new ArrayList<>();
            for (int index = 0; index < kind.valueCount(); index++) {
                minimums.add(kind.minimum(index));
            }
            ClassicControlViewPacket empty = new ClassicControlViewPacket(1, kind, Optional.empty(), 0L, minimums);
            assertEquals(empty, ClassicControlViewPacket.decode(encode(empty, ClassicControlViewPacket::encode)));
        }
        assertEquals(ClassicControlViewPacket.maximumEncodedBytes(), largest);
        assertTrue(largest <= ClassicControlViewPacket.MAX_BYTES, "largest view " + largest);
    }

    @Test
    void viewConstructionRefusesValuesOutsideTheKindsLayout() {
        List<Integer> warp = List.of(0, 0, 0, 0, 0, 0, -1);
        new ClassicControlViewPacket(1, WARP, Optional.empty(), 5L, warp);
        assertThrows(IllegalArgumentException.class,
                () -> new ClassicControlViewPacket(1, WARP, Optional.empty(), -1L, warp));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1, WARP, Optional.empty(), 0L,
                List.of(0, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1, WARP, Optional.empty(), 0L,
                List.of(32, 0, 0, 0, 0, 0, -1)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1, WARP, Optional.empty(), 0L,
                List.of(0, 0, -1, 0, 0, 0, -1)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1, WARP, Optional.empty(), 0L,
                List.of(0, 0, 0, 0, 0, 3, -1)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1, ORIENTATION,
                Optional.empty(), 0L, List.of(0, 61, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1, ORIENTATION,
                Optional.empty(), 0L, List.of(0, 0, 0, 0, 360, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1,
                ClassicControlKind.STATION_ALTITUDE_CONTROLLER, Optional.empty(), 0L, List.of(3)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(1,
                ClassicControlKind.STATION_GRAVITY_CONTROLLER, Optional.empty(), 1L, List.of(50)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControlViewPacket(0,
                ClassicControlKind.STATION_GRAVITY_CONTROLLER, Optional.empty(), 0L, List.of(50)));
    }

    @Test
    void malformedViewFramesAreDecoderExceptions() {
        String code = StationManagementCode.WARP_ISSUED.name();
        List<byte[]> frames = List.of(
                view(2, 1, 0, 0, null, 0L, 7, warpValues()),        // schema 2
                view(1, 1, 9, 0, null, 0L, 7, warpValues()),        // unknown kind
                view(1, 1, 0, 2, null, 0L, 7, warpValues()),        // status flag 2
                view(1, 1, 0, 1, "NOT_A_CODE", 0L, 7, warpValues()), // unknown code
                view(1, 1, 0, 0, null, 0L, 6, warpValues()),        // a count that is not the kind's
                view(1, 1, 0, 0, null, -1L, 7, warpValues()),       // a negative token
                view(1, 1, 1, 0, null, 3L, 1, List.of(50)),         // a token on gravity
                view(1, 1, 1, 0, null, 0L, 1, List.of(101)),        // gravity 101
                view(1, 0, 1, 0, null, 0L, 1, List.of(50)));        // container 0
        for (int index = 0; index < frames.size(); index++) {
            FriendlyByteBuf buffer = wrap(frames.get(index));
            assertThrows(DecoderException.class, () -> ClassicControlViewPacket.decode(buffer), "frame " + index);
        }
        byte[] valid = view(1, 1, 0, 1, code, 77L, 7, warpValues());
        assertEquals(new ClassicControlViewPacket(1, WARP, Optional.of(StationManagementCode.WARP_ISSUED), 77L,
                warpValues()), ClassicControlViewPacket.decode(wrap(valid)));
        assertThrows(DecoderException.class, () -> ClassicControlViewPacket.decode(wrap(append(valid, 0))));
        for (int length = 0; length < valid.length; length++) {
            FriendlyByteBuf truncated = wrap(Arrays.copyOf(valid, length));
            assertThrows(DecoderException.class, () -> ClassicControlViewPacket.decode(truncated), "prefix " + length);
        }
        // The same code name behind an overlong two-byte VarInt length is not the canonical frame.
        FriendlyByteBuf overlong = new FriendlyByteBuf(Unpooled.buffer());
        overlong.writeByte(1).writeByte(1).writeByte(0).writeByte(1);
        overlong.writeByte(0x80 | code.length()).writeByte(0);
        overlong.writeBytes(code.getBytes(StandardCharsets.US_ASCII));
        overlong.writeLong(77L).writeByte(7);
        warpValues().forEach(overlong::writeInt);
        assertThrows(DecoderException.class, () -> ClassicControlViewPacket.decode(overlong));
        FriendlyByteBuf oversized = wrap(Arrays.copyOf(valid, ClassicControlViewPacket.MAX_BYTES + 1));
        assertThrows(DecoderException.class, () -> ClassicControlViewPacket.decode(oversized));
        assertEquals(0, oversized.readerIndex(), "An oversized view was read before its bound was checked");
    }

    @Test
    void theLargestBeaconSnapshotStaysInsideOneKibibyte() {
        List<BeaconMarkerSnapshotPacket.Marker> markers = new ArrayList<>();
        for (int index = 0; index < BeaconMarkerSnapshotPacket.MAX_MARKERS; index++) {
            markers.add(new BeaconMarkerSnapshotPacket.Marker(new UUID(index, -index), -30_000_000, -2032,
                    30_000_000, index % 2 == 0, "中".repeat(BeaconMarkerSnapshotPacket.MAX_NAME_CHARS)));
        }
        BeaconMarkerSnapshotPacket largest = new BeaconMarkerSnapshotPacket(markers);
        FriendlyByteBuf buffer = encode(largest, BeaconMarkerSnapshotPacket::encode);
        assertEquals(BeaconMarkerSnapshotPacket.ENCODED_MAXIMUM, buffer.readableBytes());
        assertTrue(buffer.readableBytes() <= BeaconMarkerSnapshotPacket.MAX_BYTES, "largest " + buffer.readableBytes());
        assertEquals(largest, BeaconMarkerSnapshotPacket.decode(buffer));
        FriendlyByteBuf empty = encode(BeaconMarkerSnapshotPacket.EMPTY, BeaconMarkerSnapshotPacket::encode);
        assertEquals(2, empty.readableBytes());
        assertEquals(BeaconMarkerSnapshotPacket.EMPTY, BeaconMarkerSnapshotPacket.decode(empty));
    }

    @Test
    void beaconSnapshotConstructionRefusesItsBounds() {
        UUID id = UUID.randomUUID();
        List<BeaconMarkerSnapshotPacket.Marker> nine = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            nine.add(new BeaconMarkerSnapshotPacket.Marker(new UUID(0L, index), 0, 64, 0, true, "b" + index));
        }
        assertThrows(IllegalArgumentException.class, () -> new BeaconMarkerSnapshotPacket(nine));
        BeaconMarkerSnapshotPacket.Marker marker = new BeaconMarkerSnapshotPacket.Marker(id, 0, 64, 0, true, "a");
        assertThrows(IllegalArgumentException.class, () -> new BeaconMarkerSnapshotPacket(List.of(marker, marker)));
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconMarkerSnapshotPacket.Marker(id, 30_000_001, 64, 0, true, "a"));
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconMarkerSnapshotPacket.Marker(id, 0, 64, -30_000_001, true, "a"));
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconMarkerSnapshotPacket.Marker(id, 0, 2032, 0, true, "a"));
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconMarkerSnapshotPacket.Marker(id, 0, -2033, 0, true, "a"));
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconMarkerSnapshotPacket.Marker(id, 0, 64, 0, true, "x".repeat(33)));
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconMarkerSnapshotPacket.Marker(id, 0, 64, 0, true, "line\nbreak"));
        // Four-byte characters count twice towards the 32, so a name never exceeds 96 encoded bytes.
        BeaconMarkerSnapshotPacket supplementary = new BeaconMarkerSnapshotPacket(List.of(
                new BeaconMarkerSnapshotPacket.Marker(id, 0, 64, 0, true, "🚀".repeat(16))));
        FriendlyByteBuf encoded = encode(supplementary, BeaconMarkerSnapshotPacket::encode);
        assertTrue(encoded.readableBytes() <= 2 + BeaconMarkerSnapshotPacket.MAX_MARKER_BYTES);
        assertEquals(supplementary, BeaconMarkerSnapshotPacket.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> new BeaconMarkerSnapshotPacket.Marker(id, 0, 64, 0, true,
                "🚀".repeat(17)));
    }

    @Test
    void malformedBeaconFramesAreDecoderExceptions() {
        UUID id = new UUID(1L, 2L);
        List<byte[]> frames = List.of(
                beacon(2, 0, buffer -> { }),                                   // schema 2
                beacon(1, 9, buffer -> { }),                                   // nine markers
                beacon(1, 1, buffer -> marker(buffer, id, 0, 64, 0, 2, "a")),  // live flag 2
                beacon(1, 1, buffer -> marker(buffer, id, 0, 4000, 0, 1, "a")), // outside the height range
                beacon(1, 1, buffer -> marker(buffer, id, 0, 64, 0, 1, "x".repeat(33))), // a long name
                beacon(1, 1, buffer -> marker(buffer, id, 0, 64, 0, 1, "\u0007")),      // a control character
                beacon(1, 2, buffer -> {                                       // the same beacon twice
                    marker(buffer, id, 0, 64, 0, 1, "a");
                    marker(buffer, id, 1, 64, 0, 1, "b");
                }));
        for (int index = 0; index < frames.size(); index++) {
            FriendlyByteBuf buffer = wrap(frames.get(index));
            assertThrows(DecoderException.class, () -> BeaconMarkerSnapshotPacket.decode(buffer), "frame " + index);
        }
        byte[] valid = beacon(1, 1, buffer -> marker(buffer, id, -5, 70, 9, 0, "home"));
        assertEquals(new BeaconMarkerSnapshotPacket(List.of(new BeaconMarkerSnapshotPacket.Marker(id, -5, 70, 9, false,
                "home"))), BeaconMarkerSnapshotPacket.decode(wrap(valid)));
        assertThrows(DecoderException.class, () -> BeaconMarkerSnapshotPacket.decode(wrap(append(valid, 0))));
        for (int length = 0; length < valid.length; length++) {
            FriendlyByteBuf truncated = wrap(Arrays.copyOf(valid, length));
            assertThrows(DecoderException.class, () -> BeaconMarkerSnapshotPacket.decode(truncated), "prefix " + length);
        }
        FriendlyByteBuf overlong = new FriendlyByteBuf(Unpooled.buffer());
        overlong.writeByte(1).writeByte(1);
        overlong.writeUUID(id);
        overlong.writeInt(-5).writeInt(70).writeInt(9).writeByte(0);
        overlong.writeByte(0x80 | 4).writeByte(0).writeBytes("home".getBytes(StandardCharsets.US_ASCII));
        assertThrows(DecoderException.class, () -> BeaconMarkerSnapshotPacket.decode(overlong));
        FriendlyByteBuf oversized = wrap(Arrays.copyOf(valid, BeaconMarkerSnapshotPacket.MAX_BYTES + 1));
        assertThrows(DecoderException.class, () -> BeaconMarkerSnapshotPacket.decode(oversized));
        assertEquals(0, oversized.readerIndex(), "An oversized snapshot was read before its bound was checked");
    }

    private static List<Integer> warpValues() {
        return List.of(3, 0, 2_500_000, 1_000, 2_000_000, 1, 10);
    }

    private static byte[] intent(int schema, int container, int kind, int action, int value, long token) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(schema).writeByte(container).writeByte(kind).writeByte(action).writeByte(value);
        buffer.writeLong(token);
        return bytes(buffer);
    }

    private static byte[] view(int schema, int container, int kind, int flag, String code, long token, int count,
                               List<Integer> values) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(schema).writeByte(container).writeByte(kind).writeByte(flag);
        if (code != null) {
            buffer.writeUtf(code);
        }
        buffer.writeLong(token).writeByte(count);
        values.forEach(buffer::writeInt);
        return bytes(buffer);
    }

    private static byte[] beacon(int schema, int count, Consumer<FriendlyByteBuf> markers) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(schema).writeByte(count);
        markers.accept(buffer);
        return bytes(buffer);
    }

    private static void marker(FriendlyByteBuf buffer, UUID id, int x, int y, int z, int flag, String name) {
        buffer.writeUUID(id).writeInt(x).writeInt(y).writeInt(z).writeByte(flag);
        buffer.writeUtf(name);
    }

    private static <T> FriendlyByteBuf encode(T packet, BiConsumer<T, FriendlyByteBuf> encoder) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(packet, buffer);
        return buffer;
    }

    private static byte[] append(byte[] frame, int extra) {
        byte[] longer = Arrays.copyOf(frame, frame.length + 1);
        longer[frame.length] = (byte) extra;
        return longer;
    }

    private static byte[] bytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static FriendlyByteBuf wrap(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }
}
