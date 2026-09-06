package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.testing.RocketTransactionCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.testing.RocketTransactionFaultProbe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RocketTransactionCheckpointTest {
    private static final UUID ENTITY = new UUID(0L, 9L);
    private static final RocketStructureSnapshot SNAPSHOT = snapshot();

    @ParameterizedTest
    @CsvSource({
            "ASSEMBLY:SNAPSHOT_VALIDATED:0, 5, false", "ASSEMBLY:LOCKED:0, 5, false",
            "ASSEMBLY:EXTRACTING:1, 4, false", "ASSEMBLY:EXTRACTING:2, 3, false",
            "ASSEMBLY:EXTRACTING:3, 2, false", "ASSEMBLY:EXTRACTING:4, 1, false",
            "ASSEMBLY:EXTRACTING:5, 0, false", "ASSEMBLY:EXTRACTED:5, 0, false",
            "ASSEMBLY:SPAWNED:5, 0, true", "ASSEMBLY:COMMITTED:5, 0, true",
            "ASSEMBLY:ROLLING_BACK:2, 3, false", "ASSEMBLY:ROLLED_BACK:2, 5, false",
            "ASSEMBLY:FAILED:1, 4, false",
            "DISASSEMBLY:SNAPSHOT_VALIDATED:0, 0, true", "DISASSEMBLY:LOCKED:0, 0, true",
            "DISASSEMBLY:RESTORING:1, 1, true", "DISASSEMBLY:RESTORING:2, 2, true",
            "DISASSEMBLY:RESTORING:3, 3, true", "DISASSEMBLY:RESTORING:4, 4, true",
            "DISASSEMBLY:RESTORING:5, 5, true", "DISASSEMBLY:RESTORED:5, 5, true",
            "DISASSEMBLY:COMMITTED:5, 5, false", "DISASSEMBLY:ROLLING_BACK:2, 2, true",
            "DISASSEMBLY:ROLLED_BACK:2, 0, true", "DISASSEMBLY:FAILED:1, 1, true"
    })
    void observesActualTransactionWithoutReplacingItsWorldOrRecord(String selection, int blocks, boolean entity) {
        RocketTransactionCheckpoint checkpoint = RocketTransactionCheckpoint.parse(selection);
        checkpoint.validateBlockCount(5);
        TestWorld world = new TestWorld(checkpoint.type() == RocketTransactionType.ASSEMBLY);
        Journal journal = new Journal();
        List<RocketTransactionRecord> observed = new ArrayList<>();
        RocketTransactionFaultProbe probe = new RocketTransactionFaultProbe(checkpoint, record -> {
            assertEquals(record, journal.latest);
            observed.add(record);
            throw new ObservedStop();
        });
        var locks = new RocketRegionLockManager();
        assertThrows(ObservedStop.class, () -> execute(checkpoint.type(), probe.world(world),
                probe.journal(journal), locks));
        assertEquals(1, observed.size());
        assertTrue(checkpoint.reached(observed.get(0)));
        assertEquals(blocks, world.blocks.size());
        assertEquals(entity, world.entity);
        assertEquals(0, locks.activeCount());
        assertFalse(journal.removed);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ASSEMBLY:UNKNOWN:0", "OTHER:LOCKED:0", "ASSEMBLY:RESTORING:1",
            "DISASSEMBLY:EXTRACTED:5", "DISASSEMBLY:SPAWNED:5", "ASSEMBLY:LOCKED:1",
            "ASSEMBLY:EXTRACTING:0", "ASSEMBLY:EXTRACTING:-1", "ASSEMBLY:EXTRACTING:2049",
            "ASSEMBLY:EXTRACTING:01", "ASSEMBLY:LOCKED:0:EXTRA", "ASSEMBLY:FAILED:2",
            "DISASSEMBLY:ROLLING_BACK:1", "ASSEMBLY:ROLLED_BACK:0", "ASSEMBLY:LOCKED:+0"})
    void rejectsInvalidCheckpointBeforeMutation(String selection) {
        assertThrows(IllegalArgumentException.class, () -> RocketTransactionCheckpoint.parse(selection));
    }

    @Test
    void rejectsUnreachableStructureProgressAndOversizedInput() {
        assertThrows(IllegalArgumentException.class, () -> RocketTransactionCheckpoint.parse("x".repeat(97)));
        assertThrows(IllegalArgumentException.class,
                () -> RocketTransactionCheckpoint.parse("ASSEMBLY:COMMITTED:4").validateBlockCount(5));
        assertThrows(IllegalArgumentException.class,
                () -> RocketTransactionCheckpoint.parse("ASSEMBLY:EXTRACTING:6").validateBlockCount(5));
    }

    @Test
    void returningObserverPreservesNormalTransactionAndJournalCleanup() {
        TestWorld world = new TestWorld(true);
        Map<RocketPosition, RocketWorldBlock> before = Map.copyOf(world.blocks);
        for (RocketTransactionType type : RocketTransactionType.values()) {
            Journal journal = new Journal();
            List<RocketTransactionRecord> observed = new ArrayList<>();
            var probe = new RocketTransactionFaultProbe(
                    RocketTransactionCheckpoint.parse(type + ":COMMITTED:5"), observed::add);
            assertTrue(execute(type, probe.world(world), probe.journal(journal),
                    new RocketRegionLockManager()).success());
            assertEquals(1, observed.size());
            assertTrue(journal.removed);
        }
        assertEquals(before, world.blocks);
        assertFalse(world.entity);
    }

    private static RocketTransactionResult execute(RocketTransactionType type, RocketTransactionWorld world,
                                                   RocketTransactionJournal journal, RocketRegionLockManager locks) {
        return type == RocketTransactionType.ASSEMBLY
                ? new RocketAssemblyTransaction(world, locks, new RocketOperationLedger(), journal)
                        .execute(new UUID(1L, 1L), SNAPSHOT)
                : new RocketDisassemblyTransaction(world, locks, new RocketOperationLedger(), journal)
                        .execute(new UUID(1L, 2L), ENTITY, SNAPSHOT);
    }

    private static RocketStructureSnapshot snapshot() {
        List<RocketBlock> blocks = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            blocks.add(new RocketBlock(new RocketPosition(0, index, 0),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
        }
        return RocketStructureSnapshot.create(new UUID(2L, 1L), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(20, 70, 20), blocks, List.of(new RocketPosition(0, 2, 0)),
                new RocketStats(5, 250, 1000, 1000, 1, 1, 1, 0), 0L);
    }

    private static final class ObservedStop extends Error {
    }

    private static final class Journal implements RocketTransactionJournal {
        private RocketTransactionRecord latest;
        private boolean removed;

        @Override
        public void write(RocketTransactionRecord record) {
            latest = record;
        }

        @Override
        public void remove(UUID transactionId) {
            removed = true;
        }
    }

    private static final class TestWorld implements RocketTransactionWorld {
        private final Map<RocketPosition, RocketWorldBlock> blocks = new LinkedHashMap<>();
        private boolean entity;

        TestWorld(boolean assembledBlocks) {
            entity = !assembledBlocks;
            if (assembledBlocks) {
                SNAPSHOT.blocks().forEach(block -> blocks.put(SNAPSHOT.sourceOrigin().add(block.position()),
                        RocketWorldBlock.fromSnapshotBlock(block)));
            }
        }

        @Override
        public ResourceLocation dimension() { return SNAPSHOT.sourceDimension(); }

        @Override
        public boolean isRegionLoaded(RocketRegion region) { return true; }

        @Override
        public Optional<RocketWorldBlock> readBlock(RocketPosition position) {
            return Optional.ofNullable(blocks.get(position));
        }

        @Override
        public boolean removeBlockNoDrops(RocketPosition position, RocketWorldBlock expected) {
            return blocks.remove(position, expected);
        }

        @Override
        public boolean placeBlockIfEmpty(RocketPosition position, RocketWorldBlock block) {
            return blocks.putIfAbsent(position, block) == null;
        }

        @Override
        public Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transaction) {
            entity = true;
            return Optional.of(ENTITY);
        }

        @Override
        public boolean rocketMatches(UUID rocket, UUID snapshot, String hash) {
            return entity && rocket.equals(ENTITY) && snapshot.equals(SNAPSHOT.snapshotId())
                    && hash.equals(SNAPSHOT.contentHash());
        }

        @Override
        public boolean removeRocket(UUID rocket, UUID snapshot) {
            boolean present = entity;
            entity = false;
            return present;
        }
    }
}
