package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketVisualSnapshotCodecTest {
    @Test
    void canonicalVisualPayloadRoundTripsDeterministically() {
        RocketVisualSnapshot source = visual(12);
        byte[] first = RocketVisualSnapshotCodec.encode(source);
        byte[] second = RocketVisualSnapshotCodec.encode(source);
        RocketVisualSnapshot restored = RocketVisualSnapshotCodec.decode(first);

        assertArrayEquals(first, second);
        assertEquals(source.snapshotId(), restored.snapshotId());
        assertEquals(source.structureContentHash(), restored.structureContentHash());
        assertEquals(source.blocks(), restored.blocks());
        assertEquals(source.bounds(), restored.bounds());
        assertEquals(64, RocketVisualSnapshotCodec.sha256(first).length());
    }

    @Test
    void serverProjectionContainsNoBlockEntityPayload() {
        CompoundTag inventory = new CompoundTag();
        inventory.putString("secret_payload_marker", "must-not-cross-network");
        RocketStructureSnapshot server = RocketStructureSnapshot.create(
                UUID.randomUUID(),
                ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(0, 64, 0),
                List.of(new RocketBlock(
                        new RocketPosition(0, 0, 0),
                        state(),
                        new RocketBlockEntityPayload(
                                ResourceLocation.tryParse("test:container"),
                                inventory
                        )
                )),
                List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 1),
                1L
        );

        RocketVisualSnapshot visual = RocketVisualSnapshot.fromServerSnapshot(server);
        String encodedText = new String(RocketVisualSnapshotCodec.encode(visual), StandardCharsets.ISO_8859_1);
        assertEquals(1, visual.blocks().size());
        assertTrue(!encodedText.contains("secret_payload_marker"));
        assertTrue(!encodedText.contains("must-not-cross-network"));
    }

    @Test
    void malformedSchemaTruncationAndTrailingBytesAreRejected() {
        byte[] valid = RocketVisualSnapshotCodec.encode(visual(4));

        byte[] future = valid.clone();
        ByteBuffer.wrap(future).putInt(RocketVisualSnapshotCodec.SCHEMA_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> RocketVisualSnapshotCodec.decode(future));

        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class, () -> RocketVisualSnapshotCodec.decode(truncated));

        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> RocketVisualSnapshotCodec.decode(trailing));
    }

    @Test
    void maximumBlockProjectionUsesBoundedMultipleChunks() {
        RocketVisualSnapshot maximum = visual(2_048);
        long started = System.nanoTime();
        List<RocketVisualChunkPacket> chunks = RocketVisualChunker.chunk(UUID.randomUUID(), maximum);
        byte[] encoded = RocketVisualSnapshotCodec.encode(maximum);
        RocketVisualSnapshot decoded = RocketVisualSnapshotCodec.decode(encoded);
        long elapsedNanos = System.nanoTime() - started;

        assertEquals(2, chunks.size());
        assertTrue(chunks.stream().allMatch(packet -> packet.chunk().length <= 32_768));
        assertEquals(chunks.size(), chunks.get(0).chunkCount());
        assertEquals(
                encoded.length,
                chunks.stream().mapToInt(packet -> packet.chunk().length).sum()
        );
        assertEquals(maximum.blocks(), decoded.blocks());
        System.out.printf(
                "ARCE_ROCKET_VISUAL_PERF blocks=%d payload_bytes=%d chunks=%d elapsed_nanos=%d%n",
                maximum.blocks().size(),
                encoded.length,
                chunks.size(),
                elapsedNanos
        );
    }

    static RocketVisualSnapshot visual(int blocks) {
        ArrayList<RocketVisualBlock> visualBlocks = new ArrayList<>(blocks);
        for (int index = 0; index < blocks; index++) {
            visualBlocks.add(new RocketVisualBlock(new RocketPosition(index, 0, 0), state()));
        }
        return new RocketVisualSnapshot(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "0123456789abcdef".repeat(4),
                visualBlocks
        );
    }

    private static RocketBlockState state() {
        return new RocketBlockState(
                ResourceLocation.tryParse("minecraft:iron_block"),
                Map.of()
        );
    }
}
