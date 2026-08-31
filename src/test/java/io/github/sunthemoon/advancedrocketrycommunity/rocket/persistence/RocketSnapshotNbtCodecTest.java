package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketSnapshotNbtCodecTest {
    @Test
    void schemaOneRoundTripsCanonicallyInExpandedAndCompressedForms() {
        RocketStructureSnapshot source = legalSnapshot();
        CompoundTag encoded = RocketSnapshotNbtCodec.encode(source);
        RocketSnapshotDecodeResult decoded = RocketSnapshotNbtCodec.decode(encoded);

        assertEquals(RocketSnapshotDecodeResult.Status.VALID, decoded.status());
        RocketStructureSnapshot restored = decoded.snapshot().orElseThrow();
        assertEquals(source.snapshotId(), restored.snapshotId());
        assertEquals(source.sourceDimension(), restored.sourceDimension());
        assertEquals(source.sourceOrigin(), restored.sourceOrigin());
        assertEquals(source.bounds(), restored.bounds());
        assertEquals(source.blocks(), restored.blocks());
        assertEquals(source.passengerAnchors(), restored.passengerAnchors());
        assertEquals(source.stats(), restored.stats());
        assertEquals(source.createdAtGameTime(), restored.createdAtGameTime());
        assertEquals(source.contentHash(), restored.contentHash());
        assertEquals(encoded, RocketSnapshotNbtCodec.encode(restored));

        byte[] compressed = RocketSnapshotNbtCodec.encodeCompressed(source);
        RocketSnapshotDecodeResult decompressed = RocketSnapshotNbtCodec.decodeCompressed(compressed);
        assertEquals(RocketSnapshotDecodeResult.Status.VALID, decompressed.status());
        assertEquals(
                source.contentHash(),
                decompressed.snapshot().orElseThrow().contentHash()
        );
    }

    @Test
    void futureSchemaIsPreservedAndNeverDecodedAsCurrent() {
        CompoundTag future = RocketSnapshotNbtCodec.encode(legalSnapshot());
        future.putInt("schema_version", RocketLimits.SNAPSHOT_SCHEMA_VERSION + 1);
        future.putString("future_field", "opaque");

        RocketSnapshotDecodeResult result = RocketSnapshotNbtCodec.decode(future);
        assertEquals(RocketSnapshotDecodeResult.Status.FUTURE_SCHEMA, result.status());
        assertEquals(RocketValidationCode.UNSUPPORTED_SCHEMA, result.code());
        assertTrue(result.snapshot().isEmpty());
        CompoundTag preserved = result.preservedPayload().orElseThrow();
        assertEquals(future, preserved);
        preserved.putString("future_field", "mutated copy");
        assertEquals("opaque", result.preservedPayload().orElseThrow().getString("future_field"));
    }

    @Test
    void malformedAndWronglyTypedFieldsFailClosed() {
        CompoundTag missingSchema = RocketSnapshotNbtCodec.encode(legalSnapshot());
        missingSchema.remove("schema_version");
        assertInvalid(missingSchema, RocketValidationCode.MALFORMED_SNAPSHOT);

        CompoundTag wrongBlocks = RocketSnapshotNbtCodec.encode(legalSnapshot());
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not-a-block"));
        wrongBlocks.put("relative_blocks", strings);
        assertInvalid(wrongBlocks, RocketValidationCode.MALFORMED_SNAPSHOT);

        CompoundTag badUuid = RocketSnapshotNbtCodec.encode(legalSnapshot());
        badUuid.putString("snapshot_id", "not-a-uuid");
        assertInvalid(badUuid, RocketValidationCode.MALFORMED_SNAPSHOT);

        CompoundTag badDimension = RocketSnapshotNbtCodec.encode(legalSnapshot());
        badDimension.putString("source_dimension", "Bad Dimension");
        assertInvalid(badDimension, RocketValidationCode.MALFORMED_SNAPSHOT);
    }

    @Test
    void contentHashDetectsAnyStateOrPayloadModification() {
        CompoundTag changedPalette = RocketSnapshotNbtCodec.encode(legalSnapshot());
        changedPalette.getList("block_palette", CompoundTag.TAG_COMPOUND)
                .getCompound(0)
                .putString("id", "minecraft:diamond_block");
        assertInvalid(changedPalette, RocketValidationCode.HASH_MISMATCH);

        CompoundTag changedPayload = RocketSnapshotNbtCodec.encode(legalSnapshot());
        CompoundTag block = changedPayload.getList("relative_blocks", CompoundTag.TAG_COMPOUND)
                .getCompound(0);
        if (!block.contains("block_entity")) {
            block = changedPayload.getList("relative_blocks", CompoundTag.TAG_COMPOUND)
                    .getCompound(1);
        }
        block.getCompound("block_entity").getCompound("data").putInt("slot_count", 99);
        assertInvalid(changedPayload, RocketValidationCode.HASH_MISMATCH);
    }

    @Test
    void encodedBoundsStatsAndCanonicalOrderAreVerified() {
        CompoundTag wrongBounds = RocketSnapshotNbtCodec.encode(legalSnapshot());
        wrongBounds.putIntArray("bounding_box", new int[]{0, 0, 0, 99, 99, 99});
        assertInvalid(wrongBounds, RocketValidationCode.BOUNDING_VOLUME_EXCEEDED);

        CompoundTag wrongStats = RocketSnapshotNbtCodec.encode(legalSnapshot());
        wrongStats.getCompound("mass_inputs").putInt("block_count", 1);
        assertInvalid(wrongStats, RocketValidationCode.STATS_MISMATCH);

        CompoundTag reversedBlocks = RocketSnapshotNbtCodec.encode(legalSnapshot());
        ListTag original = reversedBlocks.getList("relative_blocks", CompoundTag.TAG_COMPOUND);
        ListTag reversed = new ListTag();
        for (int index = original.size() - 1; index >= 0; index--) {
            reversed.add(original.getCompound(index).copy());
        }
        reversedBlocks.put("relative_blocks", reversed);
        assertInvalid(reversedBlocks, RocketValidationCode.MALFORMED_SNAPSHOT);

        CompoundTag reversedPalette = RocketSnapshotNbtCodec.encode(legalSnapshot());
        ListTag palette = reversedPalette.getList("block_palette", CompoundTag.TAG_COMPOUND);
        ListTag badPalette = new ListTag();
        for (int index = palette.size() - 1; index >= 0; index--) {
            badPalette.add(palette.getCompound(index).copy());
        }
        reversedPalette.put("block_palette", badPalette);
        assertInvalid(reversedPalette, RocketValidationCode.MALFORMED_SNAPSHOT);
    }

    @Test
    void totalExpandedNbtLimitIsEnforcedBeforePersistence() {
        List<RocketBlock> blocks = new ArrayList<>();
        int payloadBytes = 220_000;
        for (int index = 0; index < 5; index++) {
            CompoundTag data = new CompoundTag();
            data.putByteArray("bytes", new byte[payloadBytes]);
            blocks.add(new RocketBlock(
                    new RocketPosition(index, 0, 0),
                    state("minecraft:chest"),
                    new RocketBlockEntityPayload(adapter(), data)
            ));
        }
        RocketStructureSnapshot snapshot = RocketStructureSnapshot.create(
                UUID.randomUUID(),
                ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(0, 64, 0),
                blocks,
                List.of(),
                new RocketStats(5, 50, 0, 0, 0, 0, 0, 5),
                10L
        );
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> RocketSnapshotNbtCodec.encode(snapshot)
        );
        assertEquals(RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE, failure.code());
    }

    @Test
    void compressedInputRejectsEmptyTruncatedOversizedAndExpansionBombs() throws IOException {
        RocketSnapshotDecodeResult empty = RocketSnapshotNbtCodec.decodeCompressed(new byte[0]);
        assertEquals(RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE, empty.code());

        byte[] valid = RocketSnapshotNbtCodec.encodeCompressed(legalSnapshot());
        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length / 2);
        assertEquals(
                RocketSnapshotDecodeResult.Status.INVALID,
                RocketSnapshotNbtCodec.decodeCompressed(truncated).status()
        );

        byte[] oversized = new byte[RocketLimits.MAX_TOTAL_NBT_BYTES + 1];
        assertEquals(
                RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                RocketSnapshotNbtCodec.decodeCompressed(oversized).code()
        );

        CompoundTag bomb = new CompoundTag();
        bomb.putInt("schema_version", 1);
        bomb.putByteArray("compressed_repetition", new byte[RocketLimits.MAX_TOTAL_NBT_BYTES + 32]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(bomb, bytes);
        assertTrue(bytes.size() < RocketLimits.MAX_TOTAL_NBT_BYTES);
        assertEquals(
                RocketSnapshotDecodeResult.Status.INVALID,
                RocketSnapshotNbtCodec.decodeCompressed(bytes.toByteArray()).status()
        );
    }

    @Test
    void compressedEncodingIsDeterministicForTheSameSnapshot() {
        byte[] first = RocketSnapshotNbtCodec.encodeCompressed(legalSnapshot());
        byte[] second = RocketSnapshotNbtCodec.encodeCompressed(legalSnapshot());
        assertArrayEquals(first, second);
    }

    private static RocketStructureSnapshot legalSnapshot() {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("slot_count", 27);
        inventory.putString("item", "minecraft:diamond");
        List<RocketBlock> blocks = List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), state("test:engine")),
                new RocketBlock(
                        new RocketPosition(0, 1, 0),
                        state("minecraft:chest"),
                        new RocketBlockEntityPayload(adapter(), inventory)
                ),
                new RocketBlock(new RocketPosition(0, 2, 0), state("test:seat")),
                new RocketBlock(new RocketPosition(0, 3, 0), state("test:guidance"))
        );
        return RocketStructureSnapshot.create(
                UUID.fromString("10000000-2000-3000-4000-500000000000"),
                ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(8, 70, -4),
                blocks,
                List.of(new RocketPosition(0, 2, 0)),
                new RocketStats(4, 200, 1_000, 0, 1, 1, 1, 1),
                1_234L
        );
    }

    private static RocketBlockState state(String id) {
        return new RocketBlockState(ResourceLocation.tryParse(id), Map.of());
    }

    private static ResourceLocation adapter() {
        return ResourceLocation.tryParse("advancedrocketrycommunity:vanilla_container");
    }

    private static void assertInvalid(CompoundTag tag, RocketValidationCode code) {
        RocketSnapshotDecodeResult result = RocketSnapshotNbtCodec.decode(tag);
        assertEquals(RocketSnapshotDecodeResult.Status.INVALID, result.status(), result.message());
        assertEquals(code, result.code(), result.message());
        assertTrue(result.snapshot().isEmpty());
        assertFalse(result.preservedPayload().isEmpty());
    }
}
