package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconMarkerSelection;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The finder's selection through W07F's frozen {@code classic_controls} index 2 codec: every valid record maps to a
 * marker, the largest possible selection fits the 1 KiB bound and decodes to itself, and a snapshot carries no owner,
 * body or Level.
 */
class BeaconMarkerSnapshotTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static BeaconMarkerSnapshotPacket snapshot(List<BeaconRecord> records) {
        List<BeaconMarkerSnapshotPacket.Marker> markers = new ArrayList<>();
        for (BeaconRecord record : records) {
            markers.add(new BeaconMarkerSnapshotPacket.Marker(record.beaconId(), record.x(), record.y(), record.z(),
                    record.generation() % 2 == 0, record.name()));
        }
        return new BeaconMarkerSnapshotPacket(markers);
    }

    private static BeaconMarkerSnapshotPacket roundTrip(BeaconMarkerSnapshotPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BeaconMarkerSnapshotPacket.encode(packet, buffer);
            assertTrue(buffer.readableBytes() <= BeaconMarkerSnapshotPacket.MAX_BYTES, "frame " + buffer.readableBytes());
            return BeaconMarkerSnapshotPacket.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    void theLargestSelectionFitsTheFrozenCodec() {
        UUID owner = UUID.randomUUID();
        String level = "advancedrocketrycommunity:" + "l".repeat(BeaconLimits.MAX_ID_CHARS - 26);
        String body = "advancedrocketrycommunity:" + "b".repeat(BeaconLimits.MAX_ID_CHARS - 26);
        List<BeaconRecord> owned = new ArrayList<>();
        for (int i = 0; i < BeaconLimits.MAX_PER_OWNER; i++) {
            owned.add(new BeaconRecord(UUID.randomUUID(), owner, body, level, i % 2 == 0 ? 30_000_000 : -30_000_000,
                    i % 3 == 0 ? BeaconLimits.MAX_Y : BeaconLimits.MIN_Y, -30_000_000 + i, i, true,
                    "中".repeat(BeaconLimits.MAX_NAME_CHARS)));
        }
        List<BeaconRecord> selected = BeaconMarkerSelection.select(owned,
                new BeaconMarkerSelection.Viewer(owner, body, level, 0, 0, 0));
        assertEquals(BeaconLimits.MAX_MARKERS, selected.size());
        assertEquals(BeaconMarkerSnapshotPacket.MAX_MARKERS, BeaconLimits.MAX_MARKERS);
        assertEquals(BeaconMarkerSnapshotPacket.MAX_NAME_CHARS, BeaconLimits.MAX_NAME_CHARS);
        BeaconMarkerSnapshotPacket packet = snapshot(selected);
        assertEquals(packet, roundTrip(packet));
    }

    @Test
    void theEmptySnapshotIsTheClearingMessage() {
        assertEquals(BeaconMarkerSnapshotPacket.EMPTY, roundTrip(snapshot(List.of())));
        assertEquals(BeaconMarkerSnapshotPacket.EMPTY, roundTrip(BeaconMarkerSnapshotPacket.EMPTY));
    }

    @Test
    void aMarkerCarriesNoOwnerBodyOrLevel() {
        UUID owner = new UUID(0x1234_5678_9abc_def0L, 0x0fed_cba9_8765_4321L);
        BeaconRecord record = new BeaconRecord(UUID.randomUUID(), owner, "advancedrocketrycommunity:secret_body",
                "advancedrocketrycommunity:secret_level", 10, 64, 20, 4, true, "Home");
        BeaconMarkerSnapshotPacket packet = snapshot(List.of(record));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BeaconMarkerSnapshotPacket.encode(packet, buffer);
            byte[] frame = new byte[buffer.readableBytes()];
            buffer.getBytes(0, frame);
            String text = new String(frame, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(!text.contains("secret_body") && !text.contains("secret_level"));
            FriendlyByteBuf ownerBytes = new FriendlyByteBuf(Unpooled.buffer());
            ownerBytes.writeUUID(owner);
            byte[] ownerFrame = new byte[16];
            ownerBytes.getBytes(0, ownerFrame);
            ownerBytes.release();
            assertTrue(!text.contains(new String(ownerFrame, java.nio.charset.StandardCharsets.ISO_8859_1)));
            assertEquals(1 + 1 + 16 + 12 + 1 + 1 + 4, frame.length);
        } finally {
            buffer.release();
        }
    }
}
