package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketStructureSnapshotTest {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final UUID SNAPSHOT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void canonicalOrderingAndHashDoNotDependOnScanOrPropertyInsertionOrder() {
        Map<String, String> firstProperties = new HashMap<>();
        firstProperties.put("waterlogged", "false");
        firstProperties.put("facing", "north");
        Map<String, String> secondProperties = new HashMap<>();
        secondProperties.put("facing", "north");
        secondProperties.put("waterlogged", "false");

        CompoundTag firstPayload = new CompoundTag();
        firstPayload.putString("CustomName", "Rocket supplies");
        firstPayload.putInt("Count", 7);
        CompoundTag secondPayload = new CompoundTag();
        secondPayload.putInt("Count", 7);
        secondPayload.putString("CustomName", "Rocket supplies");

        RocketBlock firstA = block(1, 0, 0, "test:hull", Map.of());
        RocketBlock secondA = blockWithPayload(0, 0, 0, firstProperties, firstPayload);
        RocketBlock firstB = blockWithPayload(0, 0, 0, secondProperties, secondPayload);
        RocketBlock secondB = block(1, 0, 0, "test:hull", Map.of());

        RocketStats stats = new RocketStats(2, 20, 40, 0, 1, 0, 0, 1);
        RocketStructureSnapshot left = snapshot(List.of(firstA, secondA), List.of(), stats);
        RocketStructureSnapshot right = snapshot(List.of(firstB, secondB), List.of(), stats);

        assertEquals(left.blocks(), right.blocks());
        assertEquals(left.contentHash(), right.contentHash());
        assertEquals(new RocketPosition(0, 0, 0), left.bounds().minimum());
        assertEquals(new RocketPosition(1, 0, 0), left.bounds().maximum());
    }

    @Test
    void mutableInputsAndReturnedNbtCannotChangeSnapshot() {
        Map<String, String> properties = new HashMap<>();
        properties.put("facing", "north");
        CompoundTag data = new CompoundTag();
        data.putInt("Count", 3);
        RocketBlockEntityPayload payload = new RocketBlockEntityPayload(
                new ResourceLocation("advancedrocketrycommunity", "vanilla_container"),
                data
        );
        RocketBlockState state = new RocketBlockState(new ResourceLocation("minecraft", "chest"), properties);
        RocketBlock block = new RocketBlock(new RocketPosition(0, 0, 0), state, payload);
        RocketStructureSnapshot snapshot = snapshot(
                List.of(block),
                List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 1)
        );
        String hash = snapshot.contentHash();

        properties.put("type", "left");
        data.putInt("Count", 99);
        payload.data().putInt("Count", 100);

        assertEquals(Map.of("facing", "north"), state.properties());
        assertEquals(3, snapshot.blocks().get(0).blockEntityPayload().orElseThrow().data().getInt("Count"));
        assertEquals(hash, snapshot.contentHash());
        assertThrows(UnsupportedOperationException.class, () -> state.properties().put("type", "right"));
    }

    @Test
    void duplicatePositionsFailWithExactDiagnostic() {
        RocketBlock block = block(0, 0, 0, "test:hull", Map.of());
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> snapshot(
                        List.of(block, block),
                        List.of(),
                        new RocketStats(2, 20, 0, 0, 0, 0, 0, 0)
                )
        );
        assertEquals(RocketValidationCode.DUPLICATE_BLOCK_POSITION, failure.code());
        assertEquals(new RocketPosition(0, 0, 0), failure.position().orElseThrow());
    }

    @Test
    void blockCountLimitStopsAtFirstInvalidSnapshot() {
        List<RocketBlock> blocks = new ArrayList<>();
        for (int index = 0; index <= RocketLimits.MAX_BLOCKS; index++) {
            blocks.add(block(index, 0, 0, "test:hull", Map.of()));
        }
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> snapshot(
                        blocks,
                        List.of(),
                        new RocketStats(blocks.size(), blocks.size(), 0, 0, 0, 0, 0, 0)
                )
        );
        assertEquals(RocketValidationCode.TOO_MANY_BLOCKS, failure.code());
    }

    @Test
    void boundingVolumeLimitUsesLongArithmetic() {
        List<RocketBlock> blocks = List.of(
                block(0, 0, 0, "test:hull", Map.of()),
                block(32_768, 0, 0, "test:hull", Map.of())
        );
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> snapshot(blocks, List.of(), new RocketStats(2, 20, 0, 0, 0, 0, 0, 0))
        );
        assertEquals(RocketValidationCode.BOUNDING_VOLUME_EXCEEDED, failure.code());
    }

    @Test
    void absolutePositionOverflowFailsBeforeWorldAccess() {
        RocketBlock block = block(1, 0, 0, "test:hull", Map.of());
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> RocketStructureSnapshot.create(
                        SNAPSHOT_ID,
                        OVERWORLD,
                        new RocketPosition(Integer.MAX_VALUE, 64, 0),
                        List.of(block),
                        List.of(),
                        new RocketStats(1, 10, 0, 0, 0, 0, 0, 0),
                        1L
                )
        );
        assertEquals(RocketValidationCode.POSITION_OVERFLOW, failure.code());
    }

    @Test
    void paletteLimitIsIndependentFromBlockLimit() {
        List<RocketBlock> blocks = new ArrayList<>();
        for (int index = 0; index <= RocketLimits.MAX_PALETTE_ENTRIES; index++) {
            blocks.add(block(index, 0, 0, "test:block_" + index, Map.of()));
        }
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> snapshot(
                        blocks,
                        List.of(),
                        new RocketStats(blocks.size(), blocks.size(), 0, 0, 0, 0, 0, 0)
                )
        );
        assertEquals(RocketValidationCode.TOO_MANY_PALETTE_ENTRIES, failure.code());
    }

    @Test
    void blockEntityCountIsBounded() {
        CompoundTag data = new CompoundTag();
        data.putInt("slot", 1);
        List<RocketBlock> blocks = new ArrayList<>();
        for (int index = 0; index <= RocketLimits.MAX_BLOCK_ENTITIES; index++) {
            blocks.add(blockWithPayload(index, 0, 0, Map.of(), data));
        }
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> snapshot(
                        blocks,
                        List.of(),
                        new RocketStats(blocks.size(), blocks.size(), 0, 0, 0, 0, 0, blocks.size())
                )
        );
        assertEquals(RocketValidationCode.TOO_MANY_BLOCK_ENTITIES, failure.code());
    }

    @Test
    void blockEntityPayloadRejectsIdentityFieldsAndOversizeData() {
        CompoundTag identity = new CompoundTag();
        identity.putInt("x", 4);
        RocketSnapshotException identityFailure = assertThrows(
                RocketSnapshotException.class,
                () -> payload(identity)
        );
        assertEquals(RocketValidationCode.INVALID_BLOCK_ENTITY_DATA, identityFailure.code());

        CompoundTag oversized = new CompoundTag();
        oversized.putByteArray("bytes", new byte[RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES]);
        RocketSnapshotException sizeFailure = assertThrows(
                RocketSnapshotException.class,
                () -> payload(oversized)
        );
        assertEquals(RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE, sizeFailure.code());
    }

    @Test
    void statsAndPassengerAnchorsMustMatchCapturedBlocks() {
        RocketBlock block = block(0, 0, 0, "test:seat", Map.of());
        RocketSnapshotException missingAnchor = assertThrows(
                RocketSnapshotException.class,
                () -> snapshot(
                        List.of(block),
                        List.of(),
                        new RocketStats(1, 10, 0, 0, 0, 1, 0, 0)
                )
        );
        assertEquals(RocketValidationCode.STATS_MISMATCH, missingAnchor.code());

        RocketSnapshotException outside = assertThrows(
                RocketSnapshotException.class,
                () -> snapshot(
                        List.of(block),
                        List.of(new RocketPosition(1, 0, 0)),
                        new RocketStats(1, 10, 0, 0, 0, 1, 0, 0)
                )
        );
        assertEquals(RocketValidationCode.STATS_MISMATCH, outside.code());
    }

    @Test
    void hashCoversDimensionOriginStatePayloadAnchorsAndStatsButNotUuidOrGameTime() {
        RocketBlock seat = block(0, 0, 0, "test:seat", Map.of("facing", "north"));
        RocketStats stats = new RocketStats(1, 10, 0, 0, 0, 1, 0, 0);
        RocketStructureSnapshot baseline = snapshot(List.of(seat), List.of(seat.position()), stats);
        RocketStructureSnapshot differentIdAndTime = RocketStructureSnapshot.create(
                UUID.randomUUID(),
                OVERWORLD,
                new RocketPosition(10, 64, 10),
                List.of(seat),
                List.of(seat.position()),
                stats,
                99L
        );
        assertEquals(baseline.contentHash(), differentIdAndTime.contentHash());

        RocketStructureSnapshot differentOrigin = RocketStructureSnapshot.create(
                SNAPSHOT_ID,
                OVERWORLD,
                new RocketPosition(11, 64, 10),
                List.of(seat),
                List.of(seat.position()),
                stats,
                1L
        );
        assertNotEquals(baseline.contentHash(), differentOrigin.contentHash());

        RocketBlock turned = block(0, 0, 0, "test:seat", Map.of("facing", "south"));
        assertNotEquals(
                baseline.contentHash(),
                snapshot(List.of(turned), List.of(turned.position()), stats).contentHash()
        );
    }

    @Test
    void translationKeysAreStableAndNamespaced() {
        assertEquals(
                "validation.advancedrocketrycommunity.rocket.too_many_blocks",
                RocketValidationCode.TOO_MANY_BLOCKS.translationKey()
        );
        assertTrue(RocketValidationCode.values().length > 20);
    }

    private static RocketStructureSnapshot snapshot(
            List<RocketBlock> blocks,
            List<RocketPosition> anchors,
            RocketStats stats
    ) {
        return RocketStructureSnapshot.create(
                SNAPSHOT_ID,
                OVERWORLD,
                new RocketPosition(10, 64, 10),
                blocks,
                anchors,
                stats,
                1L
        );
    }

    private static RocketBlock block(
            int x,
            int y,
            int z,
            String id,
            Map<String, String> properties
    ) {
        return new RocketBlock(
                new RocketPosition(x, y, z),
                new RocketBlockState(new ResourceLocation(id), properties)
        );
    }

    private static RocketBlock blockWithPayload(
            int x,
            int y,
            int z,
            Map<String, String> properties,
            CompoundTag data
    ) {
        return new RocketBlock(
                new RocketPosition(x, y, z),
                new RocketBlockState(new ResourceLocation("minecraft", "chest"), properties),
                payload(data)
        );
    }

    private static RocketBlockEntityPayload payload(CompoundTag data) {
        return new RocketBlockEntityPayload(
                new ResourceLocation("advancedrocketrycommunity", "vanilla_container"),
                data
        );
    }
}
