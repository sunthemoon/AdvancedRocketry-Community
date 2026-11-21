package io.github.sunthemoon.arceintenttest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CaseBuilderTest {
    private static final UUID REQUEST = UUID.fromString("12345678-1234-5678-abcd-0123456789ab");

    private static CaseBuilder.Input input(int container, int kind, int entity, String body) {
        return new CaseBuilder.Input(container, kind, entity, body, REQUEST);
    }

    @ParameterizedTest @EnumSource(IntentCase.class)
    void everyCaseHasTheExactPinnedFrameAndValues(IntentCase which) throws Exception {
        boolean station = which == IntentCase.FLIGHT_PRIVATE_STATION;
        var packet = CaseBuilder.build(which, input(255, 1, 300, station ? REQUEST.toString() : "advancedrocketrycommunity:moon"));
        var in = new DataInputStream(new ByteArrayInputStream(packet.payload()));
        assertEquals(0, in.readUnsignedByte(), "SimpleChannel message discriminator");
        if (which.flight()) {
            assertEquals("advancedrocketrycommunity:rocket_flight", which.channel());
            assertEquals(10, which.protocol());
            assertEquals(which == IntentCase.FLIGHT_FORBIDDEN_SURFACE || station ? 0 : 1, in.readUnsignedByte());
            assertEquals(0xac, in.readUnsignedByte());
            assertEquals(0x02, in.readUnsignedByte());
            assertEquals(1, in.readUnsignedByte(), "Travel target schema");
            assertEquals(station ? 2 : 0, in.readUnsignedByte(), "Travel target kind");
            if (station) {
                assertEquals(REQUEST, new UUID(in.readLong(), in.readLong()));
            } else {
                int length = in.readUnsignedByte();
                assertEquals("advancedrocketrycommunity:moon", new String(in.readNBytes(length), java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals(REQUEST, new UUID(in.readLong(), in.readLong()));
            assertEquals(0, in.readUnsignedByte(), "No named pad");
            assertTrue(packet.values().contains(REQUEST.toString()));
            assertTrue(packet.values().contains("entity=300"));
        } else if (which.classic()) {
            assertEquals("advancedrocketrycommunity:classic_controls", which.channel());
            assertEquals(3, which.protocol());
            assertEquals(14, packet.payload().length);
            assertEquals(1, in.readUnsignedByte());
            assertEquals(255, in.readUnsignedByte());
            assertEquals(1, in.readUnsignedByte());
            assertEquals(0, in.readUnsignedByte());
            assertEquals(0, in.readUnsignedByte());
            assertEquals(0, in.readLong());
        } else {
            assertEquals("advancedrocketrycommunity:suit_motion", which.channel());
            assertEquals(2, which.protocol());
            assertEquals(3, packet.payload().length);
            assertEquals(which == IntentCase.MOTION_NO_HOVER ? 1 : which == IntentCase.MOTION_DISABLED ? 2 : 0,
                    in.readUnsignedByte());
            assertEquals(which == IntentCase.MOTION_DISABLED ? 1 : 0, in.readUnsignedByte());
        }
        assertEquals(-1, in.read(), "No trailing fields");
        assertTrue(packet.payload().length <= CaseBuilder.MAX_PAYLOAD_BYTES);
        assertEquals(which, IntentCase.parse(which.id()));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void everyClassicKindUsesAValidMutationWithoutAQuote(int kind) {
        var bytes = CaseBuilder.build(IntentCase.CLASSIC_LATE, input(1, kind, 0, null)).payload();
        assertEquals(kind, bytes[3]);
        assertEquals(kind == 0 ? 4 : kind == 4 ? 5 : 0, bytes[4]);
        for (int i = 5; i < 14; i++) { assertEquals(0, bytes[i]); }
    }

    @Test void largestFlightIsBoundedAndUsesCanonicalVarInts() {
        String body = "a:" + "b".repeat(126);
        byte[] bytes = CaseBuilder.build(IntentCase.FLIGHT_STALE, input(1, 0, Integer.MAX_VALUE, body)).payload();
        assertEquals(156, bytes.length);
        assertArrayEquals(new byte[] {-1, -1, -1, -1, 7}, java.util.Arrays.copyOfRange(bytes, 2, 7));
        assertEquals(0x80, bytes[9] & 255);
        assertEquals(1, bytes[10]);
        byte[] smallest = CaseBuilder.build(IntentCase.FLIGHT_LATE, input(1, 0, 0, "a:b")).payload();
        assertEquals(0, smallest[2]);
        assertEquals(26, smallest.length);
    }

    @Test void refusesOutOfProtocolValuesRatherThanClamping() {
        for (int container : new int[] {0, -1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CaseBuilder.build(IntentCase.CLASSIC_STALE, input(container, 1, 0, null)));
        }
        for (int kind : new int[] {-1, 5, 255}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CaseBuilder.build(IntentCase.CLASSIC_STALE, input(1, kind, 0, null)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CaseBuilder.build(IntentCase.FLIGHT_STALE, input(1, 0, -1, "a:b")));
        for (String body : new String[] {"", "moon", "A:b", "a:", "a:b c", "a:\n", "a:" + "b".repeat(127)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CaseBuilder.build(IntentCase.FLIGHT_STALE, input(1, 0, 1, body)), body);
        }
        assertThrows(NullPointerException.class,
                () -> CaseBuilder.build(IntentCase.FLIGHT_STALE, new CaseBuilder.Input(1, 0, 1, "a:b", null)));
        assertThrows(IllegalArgumentException.class,
                () -> CaseBuilder.build(IntentCase.FLIGHT_PRIVATE_STATION, input(1, 0, 1, "a:b")));
        assertThrows(IllegalArgumentException.class,
                () -> CaseBuilder.build(IntentCase.FLIGHT_PRIVATE_STATION, input(1, 0, 1, "1-1-1-1-1")));
    }

    @Test void packetsAreImmutableAndRejectOversizedFrames() {
        var packet = CaseBuilder.build(IntentCase.MOTION_DISABLED, input(0, 0, 0, null));
        packet.payload()[0] = 9;
        assertEquals(0, packet.payload()[0]);
        byte[] source = {0, 2, 1};
        var copy = new CaseBuilder.Packet(IntentCase.MOTION_DISABLED, source, "held=1");
        source[0] = 7;
        assertEquals(0, copy.payload()[0]);
        assertThrows(IllegalArgumentException.class,
                () -> new CaseBuilder.Packet(IntentCase.MOTION_DISABLED, new byte[157], ""));
        assertThrows(IllegalArgumentException.class,
                () -> new CaseBuilder.Packet(IntentCase.MOTION_DISABLED, new byte[2], ""));
    }
}
