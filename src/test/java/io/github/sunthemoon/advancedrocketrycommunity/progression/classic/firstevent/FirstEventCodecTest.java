package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W44 A0: the shared event codec, its exact shape, bounds and fingerprint (sections 1.1 and 2.1). */
class FirstEventCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void bothKindsRoundTripWithExactKeysAndTheFingerprintFormula() throws Exception {
        var warp = warp(1, List.of(id(30), id(10), id(20)));
        assertEquals(List.of(id(10), id(20), id(30)), warp.participants(), "create sorts ascending");
        CompoundTag encoded = FirstEventCodec.encode(warp);
        assertEquals(FirstEventCodec.WARP_KEYS, encoded.getAllKeys());
        assertEquals(12, encoded.getIntArray("participants").length);
        assertEquals(warp, FirstEventCodec.decode(encoded, FirstEventKind.FIRST_STATION_WARP));
        assertEquals(expectedFingerprint("first_station_warp", warp.eventId(), warp.participants()), warp.fingerprint());

        var moon = moon(2, List.of(id(11), id(12)));
        CompoundTag moonTag = FirstEventCodec.encode(moon);
        assertEquals(FirstEventCodec.MOON_KEYS, moonTag.getAllKeys());
        assertArrayEquals(new int[] {12, 65, -4}, moonTag.getIntArray("terminal_origin"));
        assertEquals("advancedrocketrycommunity:moon", moonTag.getString("level"));
        assertEquals(moon, FirstEventCodec.decode(moonTag, FirstEventKind.FIRST_MOON_LANDING));
        assertEquals(expectedFingerprint("first_moon_landing", moon.eventId(), moon.participants()), moon.fingerprint());
        assertTrue(moon.participant(id(12)));
        assertFalse(moon.participant(id(13)), "a later observer is never a participant");
        // The empty set is a valid event with no recipients (W44 section 5).
        var empty = warp(3, List.of());
        assertEquals(empty, FirstEventCodec.decode(FirstEventCodec.encode(empty), FirstEventKind.FIRST_STATION_WARP));
    }

    @Test void everyDeviationIsRefused() {
        var warp = warp(1, List.of(id(10), id(20)));
        assertRefused(warp, FirstEventKind.FIRST_MOON_LANDING, tag -> { }); // kind does not match its slot
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putInt("extra", 1));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.remove("orbital_tick"));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.put("cost", LongTag.valueOf(5)));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putInt("cost", 0));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putLong("orbital_tick", -1L));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.put("station_id", StringTag.valueOf("x")));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putString("to_body", "a".repeat(129)));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putString("fingerprint", "0".repeat(64)));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putString("fingerprint", "A".repeat(64)));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putUUID("event_id", id(99)));
        // Participants: a partial UUID, a descending pair and a duplicate (which also keeps the fingerprint honest).
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> tag.putIntArray("participants", new int[] {1, 2, 3}));
        assertRefused(warp, FirstEventKind.FIRST_STATION_WARP, tag -> {
            int[] values = tag.getIntArray("participants");
            int[] swapped = new int[8];
            System.arraycopy(values, 4, swapped, 0, 4);
            System.arraycopy(values, 0, swapped, 4, 4);
            tag.putIntArray("participants", swapped);
        });
        var moon = moon(2, List.of(id(11)));
        assertRefused(moon, FirstEventKind.FIRST_MOON_LANDING, tag -> tag.putString("level", "minecraft:overworld"));
        assertRefused(moon, FirstEventKind.FIRST_MOON_LANDING, tag -> tag.putIntArray("terminal_origin", new int[] {1, 2}));
        assertRefused(moon, FirstEventKind.FIRST_MOON_LANDING, tag -> tag.putString("record_checksum", "F".repeat(64)));
        assertRefused(moon, FirstEventKind.FIRST_MOON_LANDING, tag -> tag.put("completed_at_game_time", IntTag.valueOf(1)));
        assertThrows(IllegalArgumentException.class, () -> new FirstEvent.StationWarp(id(1), id(2), id(3),
                warp.fromBody(), warp.toBody(), 5, 0L, List.of(id(20), id(10)), warp.fingerprint()));
        assertThrows(IllegalArgumentException.class, () -> warp(4, List.of(id(10), id(10))));
    }

    @Test void participantBoundsAreRefusedNeverTruncatedAndTheMaximumEncodingsFit() {
        assertThrows(IllegalArgumentException.class, () -> moon(1, participants(17, 100)));
        assertThrows(IllegalArgumentException.class, () -> warp(1, participants(4_097, 100)));
        var moon = moon(1, participants(16, 100));
        var warp = warp(2, participants(4_096, 100));
        CompoundTag moonOutbox = FirstEventOutbox.elected(moon).encode(FirstEventKind.FIRST_MOON_LANDING);
        CompoundTag warpOutbox = FirstEventOutbox.elected(warp).encode(FirstEventKind.FIRST_STATION_WARP);
        BoundedNbt.Size moonSize = BoundedNbt.measure(moonOutbox, 4 * 1_024, 16, 8_192);
        BoundedNbt.Size warpSize = BoundedNbt.measure(warpOutbox, 128 * 1_024, 16, 8_192);
        assertNotNull(moonSize);
        assertNotNull(warpSize);
        assertEquals(65_536, warpOutbox.getCompound("event").getIntArray("participants").length * 4);
        assertTrue(warpSize.nodes() < 20, "participants are one flat array: " + warpSize.nodes());
        var oversized = warpOutbox.copy();
        List<UUID> more = new ArrayList<>(warp.participants());
        more.add(id(1));
        oversized.getCompound("event").put("participants", FirstEventCodec.encodeParticipants(more.stream().sorted().toList()));
        assertThrows(IllegalArgumentException.class, () -> FirstEventOutbox.decode(oversized, FirstEventKind.FIRST_STATION_WARP));
    }

    private static void assertRefused(FirstEvent event, FirstEventKind kind, java.util.function.Consumer<CompoundTag> mutation) {
        CompoundTag tag = FirstEventCodec.encode(event);
        mutation.accept(tag);
        assertThrows(IllegalArgumentException.class, () -> FirstEventCodec.decode(tag, kind));
    }

    /** Independent statement of the W44 section 1.1 formula. */
    private static String expectedFingerprint(String kind, UUID event, List<UUID> participants) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(kind.getBytes(StandardCharsets.UTF_8));
        digest.update(new byte[] {0});
        digest.update(ByteBuffer.allocate(16).putLong(event.getMostSignificantBits())
                .putLong(event.getLeastSignificantBits()).array());
        digest.update(ByteBuffer.allocate(4).putInt(participants.size()).array());
        for (UUID participant : participants) {
            digest.update(ByteBuffer.allocate(16).putLong(participant.getMostSignificantBits())
                    .putLong(participant.getLeastSignificantBits()).array());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
