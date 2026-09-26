package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** World callbacks may fail after mutating; the journal must remain authoritative. */
final class RocketTransactionExceptionTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.tryParse("minecraft:overworld");
    private static final RocketPosition ORIGIN = new RocketPosition(10, 70, 10);
    private static final UUID TRANSACTION = new UUID(12, 1);
    private static final UUID ROCKET = new UUID(12, 2);
    private static final RocketStructureSnapshot SNAPSHOT = RocketStructureSnapshot.create(
            new UUID(12, 3), DIMENSION, ORIGIN,
            List.of(block(0), block(1)), List.of(),
            new RocketStats(2, 2, 0, 0, 0, 0, 0, 0), 0);

    @Test
    void extractionExceptionAfterMutationRetainsRecoveryRecordForUntrackedBlock() {
        Fixture fixture = new Fixture(true);
        fixture.world.throwRemoveAt = ORIGIN;
        RocketTransactionResult result = fixture.assemble(RocketFailureInjector.NONE);
        fixture.assertRetainedFailure(result);
        assertEquals(0, result.changedBlocks());
        assertEquals(1, fixture.world.blocks.size());
        assertFalse(fixture.world.entity);
    }

    @Test
    void placementExceptionAfterMutationRetainsEntityAndRecoveryRecord() {
        Fixture fixture = new Fixture(false);
        fixture.world.throwPlaceAt = ORIGIN;
        RocketTransactionResult result = fixture.disassemble(RocketFailureInjector.NONE);
        fixture.assertRetainedFailure(result);
        assertEquals(0, result.changedBlocks());
        assertEquals(1, fixture.world.blocks.size());
        assertTrue(fixture.world.entity);
    }

    @Test
    void assemblyRollbackExceptionDoesNotEscapeOrSkipRemainingCleanup() {
        Fixture fixture = new Fixture(true);
        fixture.world.throwPlaceAt = ORIGIN.add(new RocketPosition(0, 1, 0));
        RocketTransactionResult result = fixture.assemble(failAt(RocketFailurePoint.BEFORE_COMMIT));
        fixture.assertRetainedFailure(result);
        assertEquals(2, result.changedBlocks());
        assertEquals(1, result.rolledBackBlocks());
        assertEquals(2, fixture.world.blocks.size());
        assertFalse(fixture.world.entity);
    }

    @Test
    void disassemblyRollbackExceptionDoesNotEscapeOrSkipRemainingCleanup() {
        Fixture fixture = new Fixture(false);
        fixture.world.throwRemoveAt = ORIGIN.add(new RocketPosition(0, 1, 0));
        RocketTransactionResult result = fixture.disassemble(failAt(RocketFailurePoint.BEFORE_COMMIT));
        fixture.assertRetainedFailure(result);
        assertEquals(2, result.changedBlocks());
        assertEquals(1, result.rolledBackBlocks());
        assertTrue(fixture.world.blocks.isEmpty());
        assertTrue(fixture.world.entity);
    }

    @Test
    void assemblyCannotRestoreBlocksUntilEntityRemovalIsConfirmed() {
        for (int failure = 1; failure <= 3; failure++) {
            Fixture fixture = new Fixture(true);
            fixture.world.entityRemovalFailure = failure;
            RocketTransactionResult result = fixture.assemble(failAt(RocketFailurePoint.BEFORE_COMMIT));
            fixture.assertRetainedFailure(result);
            assertEquals(0, result.rolledBackBlocks());
            assertTrue(fixture.world.blocks.isEmpty());
            assertEquals(failure != 3, fixture.world.entity);
        }
    }

    @Test
    void missingRestorationDependenciesRejectBeforeWorldOrJournalMutation() {
        Fixture fixture = new Fixture(false);
        fixture.world.restorable = false;
        RocketTransactionResult result = fixture.disassemble(RocketFailureInjector.NONE);
        assertEquals(RocketValidationCode.UNSUPPORTED_BLOCK_ENTITY, result.code());
        assertTrue(fixture.world.blocks.isEmpty());
        assertTrue(fixture.world.entity);
        assertTrue(fixture.journal.records.isEmpty());
        assertEquals(0, fixture.locks.activeCount());
        assertEquals(RocketOperationLedger.Outcome.FAILED, fixture.ledger.outcome(TRANSACTION));
    }

    private static RocketBlock block(int y) {
        return new RocketBlock(new RocketPosition(0, y, 0),
                new RocketBlockState(ResourceLocation.tryParse("minecraft:stone"), Map.of()));
    }

    private static RocketFailureInjector failAt(RocketFailurePoint point) {
        return (actual, progress) -> {
            if (actual == point) {
                throw new IllegalStateException("injected failure");
            }
        };
    }

    private static final class Fixture {
        private final World world = new World();
        private final Journal journal = new Journal();
        private final RocketRegionLockManager locks = new RocketRegionLockManager();
        private final RocketOperationLedger ledger = new RocketOperationLedger();

        private Fixture(boolean assembledBlocks) {
            world.entity = !assembledBlocks;
            if (assembledBlocks) {
                SNAPSHOT.blocks().forEach(block -> world.blocks.put(ORIGIN.add(block.position()),
                        RocketWorldBlock.fromSnapshotBlock(block)));
            }
        }

        private RocketTransactionResult assemble(RocketFailureInjector injector) {
            return new RocketAssemblyTransaction(world, locks, ledger, journal, injector)
                    .execute(TRANSACTION, SNAPSHOT);
        }

        private RocketTransactionResult disassemble(RocketFailureInjector injector) {
            return new RocketDisassemblyTransaction(world, locks, ledger, journal, injector)
                    .execute(TRANSACTION, ROCKET, SNAPSHOT);
        }

        private void assertRetainedFailure(RocketTransactionResult result) {
            assertEquals(RocketValidationCode.ROLLBACK_FAILED, result.code());
            assertEquals(RocketTransactionPhase.FAILED, journal.records.get(TRANSACTION).phase());
            assertEquals(SNAPSHOT.contentHash(), journal.records.get(TRANSACTION).contentHash());
            assertEquals(0, locks.activeCount());
            assertEquals(RocketOperationLedger.Outcome.FAILED, ledger.outcome(TRANSACTION));
        }
    }

    private static final class Journal implements RocketTransactionJournal {
        private final Map<UUID, RocketTransactionRecord> records = new HashMap<>();

        @Override
        public void write(RocketTransactionRecord record) {
            records.put(record.transactionId(), record);
        }

        @Override
        public void remove(UUID transactionId) {
            records.remove(transactionId);
        }
    }

    private static final class World implements RocketTransactionWorld {
        private final Map<RocketPosition, RocketWorldBlock> blocks = new HashMap<>();
        private boolean entity;
        private boolean restorable = true;
        private RocketPosition throwRemoveAt;
        private RocketPosition throwPlaceAt;
        private int entityRemovalFailure;

        @Override
        public ResourceLocation dimension() {
            return DIMENSION;
        }

        @Override
        public boolean isRegionLoaded(RocketRegion region) {
            return true;
        }

        @Override
        public boolean canRestoreSnapshot(RocketStructureSnapshot snapshot) {
            return restorable;
        }

        @Override
        public Optional<RocketWorldBlock> readBlock(RocketPosition position) {
            return Optional.ofNullable(blocks.get(position));
        }

        @Override
        public boolean removeBlockNoDrops(RocketPosition position, RocketWorldBlock expected) {
            boolean removed = blocks.remove(position, expected);
            if (position.equals(throwRemoveAt)) {
                throw new IllegalStateException("injected after removal");
            }
            return removed;
        }

        @Override
        public boolean placeBlockIfEmpty(RocketPosition position, RocketWorldBlock block) {
            boolean placed = blocks.putIfAbsent(position, block) == null;
            if (position.equals(throwPlaceAt)) {
                throw new IllegalStateException("injected after placement");
            }
            return placed;
        }

        @Override
        public Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId) {
            entity = true;
            return Optional.of(ROCKET);
        }

        @Override
        public boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash) {
            return entity && rocketId.equals(ROCKET) && snapshotId.equals(SNAPSHOT.snapshotId())
                    && contentHash.equals(SNAPSHOT.contentHash());
        }

        @Override
        public boolean removeRocket(UUID rocketId, UUID snapshotId) {
            if (entityRemovalFailure == 1) {
                return false;
            }
            if (entityRemovalFailure == 2) {
                throw new IllegalStateException("before entity discard");
            }
            boolean removed = entity;
            entity = false;
            if (entityRemovalFailure == 3) {
                throw new IllegalStateException("after entity discard");
            }
            return removed;
        }
    }
}
