package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketTransactionsTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");
    private static final RocketPosition ORIGIN = new RocketPosition(20, 70, -20);
    private static final UUID ROCKET_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");

    @Test
    void successfulAssemblyAndDisassemblyPreserveEveryBlockAndContainerByte() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        String before = world.materialSignature();
        InMemoryJournal journal = new InMemoryJournal();
        RocketRegionLockManager locks = new RocketRegionLockManager();
        RocketOperationLedger ledger = new RocketOperationLedger();

        RocketTransactionResult assembly = new RocketAssemblyTransaction(
                world,
                locks,
                ledger,
                journal
        ).execute(operation(1), snapshot);

        assertTrue(assembly.success());
        assertEquals(ROCKET_ID, assembly.rocketEntityId().orElseThrow());
        assertEquals(snapshot.blocks().size(), assembly.changedBlocks());
        assertTrue(world.blocks.isEmpty());
        assertTrue(world.rocketMatches(ROCKET_ID, snapshot.snapshotId(), snapshot.contentHash()));
        assertEquals(0, locks.activeCount());
        assertEquals(RocketOperationLedger.Outcome.SUCCEEDED, ledger.outcome(operation(1)));
        assertEquals(
                List.of(
                        RocketTransactionPhase.SNAPSHOT_VALIDATED,
                        RocketTransactionPhase.LOCKED,
                        RocketTransactionPhase.EXTRACTING,
                        RocketTransactionPhase.EXTRACTING,
                        RocketTransactionPhase.EXTRACTING,
                        RocketTransactionPhase.EXTRACTING,
                        RocketTransactionPhase.EXTRACTED,
                        RocketTransactionPhase.SPAWNED,
                        RocketTransactionPhase.COMMITTED
                ),
                journal.history.stream().map(RocketTransactionRecord::phase).toList()
        );
        assertTrue(journal.active.isEmpty());

        RocketTransactionResult disassembly = new RocketDisassemblyTransaction(
                world,
                locks,
                ledger,
                journal
        ).execute(operation(2), ROCKET_ID, snapshot);

        assertTrue(disassembly.success());
        assertEquals(before, world.materialSignature());
        assertFalse(world.rockets.containsKey(ROCKET_ID));
        assertEquals(snapshot.blocks().size(), world.blocks.size());
        assertEquals(0, locks.activeCount());
    }

    @Test
    void everyRequiredAssemblyFailurePointRollsBackWithoutDuplicationOrLoss() {
        for (RocketFailurePoint point : List.of(
                RocketFailurePoint.AFTER_SNAPSHOT,
                RocketFailurePoint.DURING_EXTRACTION,
                RocketFailurePoint.AFTER_SPAWN,
                RocketFailurePoint.BEFORE_COMMIT
        )) {
            RocketStructureSnapshot snapshot = snapshot();
            FakeWorld world = FakeWorld.withStructure(snapshot);
            String before = world.materialSignature();
            RocketFailureInjector injector = (candidate, progress) -> {
                if (candidate == point && (point != RocketFailurePoint.DURING_EXTRACTION || progress == 2)) {
                    throw new RocketTransactionAbortException(
                            point == RocketFailurePoint.AFTER_SPAWN
                                    ? RocketValidationCode.SPAWN_FAILED
                                    : RocketValidationCode.EXTRACTION_FAILED,
                            "injected " + point
                    );
                }
            };
            RocketRegionLockManager locks = new RocketRegionLockManager();
            InMemoryJournal journal = new InMemoryJournal();

            RocketTransactionResult result = new RocketAssemblyTransaction(
                    world,
                    locks,
                    new RocketOperationLedger(),
                    journal,
                    injector
            ).execute(UUID.randomUUID(), snapshot);

            assertFalse(result.success(), point.toString());
            assertEquals(before, world.materialSignature(), point.toString());
            assertTrue(world.rockets.isEmpty(), point.toString());
            assertEquals(result.changedBlocks(), result.rolledBackBlocks(), point.toString());
            assertEquals(0, locks.activeCount(), point.toString());
            assertTrue(journal.active.isEmpty(), point.toString());
            assertTrue(
                    journal.history.stream().anyMatch(record -> record.phase() == RocketTransactionPhase.ROLLED_BACK),
                    point.toString()
            );
        }
    }

    @Test
    void spawnAndMidExtractionWorldFailuresRestoreTheOriginalStructure() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld spawnFailure = FakeWorld.withStructure(snapshot);
        spawnFailure.spawnAllowed = false;
        String before = spawnFailure.materialSignature();
        RocketTransactionResult spawn = assemble(spawnFailure, snapshot, UUID.randomUUID());
        assertEquals(RocketValidationCode.SPAWN_FAILED, spawn.code());
        assertEquals(before, spawnFailure.materialSignature());
        assertTrue(spawnFailure.rockets.isEmpty());

        FakeWorld extractionFailure = FakeWorld.withStructure(snapshot);
        extractionFailure.failRemoveAt = absolute(snapshot, snapshot.blocks().get(2));
        before = extractionFailure.materialSignature();
        RocketTransactionResult extraction = assemble(extractionFailure, snapshot, UUID.randomUUID());
        assertEquals(RocketValidationCode.EXTRACTION_FAILED, extraction.code());
        assertEquals(before, extractionFailure.materialSignature());
        assertEquals(2, extraction.changedBlocks());
        assertEquals(2, extraction.rolledBackBlocks());
    }

    @Test
    void rollbackFailureRemainsDurablyVisibleAndNeverClaimsSuccess() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        RocketPosition cannotRestore = absolute(snapshot, snapshot.blocks().get(0));
        world.failPlaceAt = cannotRestore;
        InMemoryJournal journal = new InMemoryJournal();
        RocketFailureInjector injector = (point, progress) -> {
            if (point == RocketFailurePoint.DURING_EXTRACTION && progress == 2) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.EXTRACTION_FAILED,
                        "force rollback"
                );
            }
        };

        RocketTransactionResult result = new RocketAssemblyTransaction(
                world,
                new RocketRegionLockManager(),
                new RocketOperationLedger(),
                journal,
                injector
        ).execute(operation(3), snapshot);

        assertFalse(result.success());
        assertEquals(RocketValidationCode.ROLLBACK_FAILED, result.code());
        assertEquals(2, result.changedBlocks());
        assertEquals(1, result.rolledBackBlocks());
        assertEquals(RocketTransactionPhase.FAILED, journal.active.get(operation(3)).phase());
        assertFalse(world.rockets.containsKey(ROCKET_ID));
    }

    @Test
    void occupiedOrUnloadedDisassemblyNeverOverwritesTheWorld() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld occupied = assembledWorld(snapshot);
        RocketPosition target = absolute(snapshot, snapshot.blocks().get(1));
        occupied.blocks.put(target, new RocketWorldBlock(state("minecraft:diamond_block"), null));
        String before = occupied.materialSignature();

        RocketTransactionResult occupiedResult = disassemble(
                occupied,
                snapshot,
                UUID.randomUUID(),
                RocketFailureInjector.NONE
        );
        assertEquals(RocketValidationCode.TARGET_OCCUPIED, occupiedResult.code());
        assertEquals(target, occupiedResult.issue().orElseThrow().position().orElseThrow());
        assertEquals(before, occupied.materialSignature());
        assertTrue(occupied.rockets.containsKey(ROCKET_ID));

        FakeWorld unloaded = assembledWorld(snapshot);
        unloaded.loaded = false;
        RocketTransactionResult unloadedResult = disassemble(
                unloaded,
                snapshot,
                UUID.randomUUID(),
                RocketFailureInjector.NONE
        );
        assertEquals(RocketValidationCode.UNLOADED_CHUNK, unloadedResult.code());
        assertTrue(unloaded.blocks.isEmpty());
        assertTrue(unloaded.rockets.containsKey(ROCKET_ID));
    }

    @Test
    void interruptedDisassemblyRemovesPartialBlocksAndKeepsTheEntity() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = assembledWorld(snapshot);
        RocketFailureInjector injector = (point, progress) -> {
            if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.EXTRACTION_FAILED,
                        "interrupt restoration"
                );
            }
        };

        RocketTransactionResult result = disassemble(world, snapshot, operation(4), injector);

        assertFalse(result.success());
        assertEquals(RocketValidationCode.EXTRACTION_FAILED, result.code());
        assertTrue(world.blocks.isEmpty());
        assertTrue(world.rocketMatches(ROCKET_ID, snapshot.snapshotId(), snapshot.contentHash()));
        assertEquals(2, result.changedBlocks());
        assertEquals(2, result.rolledBackBlocks());
    }

    @Test
    void entityRemovalFailureRollsBackDisassembly() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = assembledWorld(snapshot);
        world.removeRocketAllowed = false;

        RocketTransactionResult result = disassemble(
                world,
                snapshot,
                UUID.randomUUID(),
                RocketFailureInjector.NONE
        );

        assertEquals(RocketValidationCode.ENTITY_STATE_INVALID, result.code());
        assertTrue(world.blocks.isEmpty());
        assertTrue(world.rockets.containsKey(ROCKET_ID));
        assertEquals(snapshot.blocks().size(), result.rolledBackBlocks());
    }

    @Test
    void overlappingRegionLockAllowsOnlyOneTransaction() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        RocketRegionLockManager locks = new RocketRegionLockManager();
        UUID foreign = operation(50);
        RocketRegionLockManager.LockToken held = locks.tryAcquire(
                foreign,
                RocketRegion.fromSnapshot(snapshot)
        ).orElseThrow();
        try {
            RocketTransactionResult result = new RocketAssemblyTransaction(
                    world,
                    locks,
                    new RocketOperationLedger(),
                    RocketTransactionJournal.NO_OP
            ).execute(operation(51), snapshot);
            assertEquals(RocketValidationCode.REGION_BUSY, result.code());
            assertEquals(snapshot.blocks().size(), world.blocks.size());
            assertTrue(world.rockets.isEmpty());
            assertEquals(1, locks.activeCount());
        } finally {
            held.close();
        }
        assertEquals(0, locks.activeCount());
    }

    @Test
    void replayAndSecondFreshRequestCannotCreateASecondRocket() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        RocketOperationLedger ledger = new RocketOperationLedger();
        UUID operation = operation(60);
        RocketAssemblyTransaction transaction = new RocketAssemblyTransaction(
                world,
                new RocketRegionLockManager(),
                ledger,
                RocketTransactionJournal.NO_OP
        );

        assertTrue(transaction.execute(operation, snapshot).success());
        RocketTransactionResult replay = transaction.execute(operation, snapshot);
        assertEquals(RocketValidationCode.REQUEST_REPLAYED, replay.code());
        RocketTransactionResult fresh = transaction.execute(operation(61), snapshot);
        assertEquals(RocketValidationCode.WORLD_CHANGED, fresh.code());
        assertEquals(1, world.rockets.size());
    }

    @Test
    void activeLedgerCapacityFailsClosedWithoutEvictingInflightWork() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        RocketOperationLedger ledger = new RocketOperationLedger(1);
        assertEquals(RocketOperationLedger.BeginResult.STARTED, ledger.begin(operation(70)));

        RocketTransactionResult result = new RocketAssemblyTransaction(
                world,
                new RocketRegionLockManager(),
                ledger,
                RocketTransactionJournal.NO_OP
        ).execute(operation(71), snapshot);

        assertEquals(RocketValidationCode.OPERATION_LEDGER_FULL, result.code());
        assertEquals(RocketOperationLedger.Outcome.ACTIVE, ledger.outcome(operation(70)));
        assertNull(ledger.outcome(operation(71)));
        assertEquals(snapshot.blocks().size(), world.blocks.size());
    }

    @Test
    void journalWriteFailureAfterMutationTriggersFullRollback() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        String before = world.materialSignature();
        InMemoryJournal journal = new InMemoryJournal();
        journal.failWhen = record -> record.phase() == RocketTransactionPhase.EXTRACTING
                && record.progress() == 2;

        RocketTransactionResult result = new RocketAssemblyTransaction(
                world,
                new RocketRegionLockManager(),
                new RocketOperationLedger(),
                journal
        ).execute(operation(80), snapshot);

        assertFalse(result.success());
        assertEquals(before, world.materialSignature());
        assertTrue(world.rockets.isEmpty());
        assertEquals(2, result.changedBlocks());
        assertEquals(2, result.rolledBackBlocks());
    }

    @Test
    void terminalJournalCleanupFailureDoesNotUndoACommittedWorld() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        InMemoryJournal journal = new InMemoryJournal();
        journal.failRemove = true;

        RocketTransactionResult result = new RocketAssemblyTransaction(
                world,
                new RocketRegionLockManager(),
                new RocketOperationLedger(),
                journal
        ).execute(operation(90), snapshot);

        assertTrue(result.success());
        assertTrue(world.blocks.isEmpty());
        assertTrue(world.rockets.containsKey(ROCKET_ID));
        assertEquals(RocketTransactionPhase.COMMITTED, journal.active.get(operation(90)).phase());
    }

    @Test
    void oneHundredCyclesMaintainAnExactMaterialAndInventoryLedger() {
        RocketStructureSnapshot snapshot = snapshot();
        FakeWorld world = FakeWorld.withStructure(snapshot);
        String expected = world.materialSignature();
        RocketRegionLockManager locks = new RocketRegionLockManager();
        RocketOperationLedger ledger = new RocketOperationLedger(512);
        InMemoryJournal journal = new InMemoryJournal();

        for (int cycle = 0; cycle < 100; cycle++) {
            RocketTransactionResult assembly = new RocketAssemblyTransaction(
                    world,
                    locks,
                    ledger,
                    journal
            ).execute(operation(1_000 + cycle * 2), snapshot);
            assertTrue(assembly.success(), "assembly cycle " + cycle);
            assertTrue(world.blocks.isEmpty(), "assembly blocks " + cycle);
            assertEquals(1, world.rockets.size(), "assembly entity " + cycle);

            RocketTransactionResult disassembly = new RocketDisassemblyTransaction(
                    world,
                    locks,
                    ledger,
                    journal
            ).execute(operation(1_001 + cycle * 2), ROCKET_ID, snapshot);
            assertTrue(disassembly.success(), "disassembly cycle " + cycle);
            assertEquals(expected, world.materialSignature(), "ledger cycle " + cycle);
            assertTrue(world.rockets.isEmpty(), "disassembly entity " + cycle);
            assertEquals(0, locks.activeCount(), "locks cycle " + cycle);
        }
    }

    @Test
    void regionsUseInclusiveDimensionScopedOverlap() {
        RocketRegion a = new RocketRegion(
                OVERWORLD,
                new RocketPosition(0, 0, 0),
                new RocketPosition(2, 2, 2)
        );
        RocketRegion touching = new RocketRegion(
                OVERWORLD,
                new RocketPosition(2, 2, 2),
                new RocketPosition(3, 3, 3)
        );
        RocketRegion separate = new RocketRegion(
                OVERWORLD,
                new RocketPosition(3, 0, 0),
                new RocketPosition(4, 1, 1)
        );
        RocketRegion moon = new RocketRegion(
                ResourceLocation.tryParse("advancedrocketrycommunity:moon"),
                new RocketPosition(0, 0, 0),
                new RocketPosition(2, 2, 2)
        );
        assertTrue(a.overlaps(touching));
        assertFalse(a.overlaps(separate));
        assertFalse(a.overlaps(moon));
        assertTrue(a.contains(new RocketPosition(1, 1, 1)));
    }

    private static RocketTransactionResult assemble(
            FakeWorld world,
            RocketStructureSnapshot snapshot,
            UUID operationId
    ) {
        return new RocketAssemblyTransaction(
                world,
                new RocketRegionLockManager(),
                new RocketOperationLedger(),
                RocketTransactionJournal.NO_OP
        ).execute(operationId, snapshot);
    }

    private static RocketTransactionResult disassemble(
            FakeWorld world,
            RocketStructureSnapshot snapshot,
            UUID operationId,
            RocketFailureInjector injector
    ) {
        return new RocketDisassemblyTransaction(
                world,
                new RocketRegionLockManager(),
                new RocketOperationLedger(),
                RocketTransactionJournal.NO_OP,
                injector
        ).execute(operationId, ROCKET_ID, snapshot);
    }

    private static FakeWorld assembledWorld(RocketStructureSnapshot snapshot) {
        FakeWorld world = new FakeWorld();
        world.rockets.put(ROCKET_ID, new RocketRecord(snapshot.snapshotId(), snapshot.contentHash()));
        return world;
    }

    private static RocketStructureSnapshot snapshot() {
        CompoundTag chest = new CompoundTag();
        chest.putInt("slot_count", 27);
        chest.putString("slot_0", "minecraft:diamond*17");
        chest.putString("custom_name", "Exact cargo");
        List<RocketBlock> blocks = List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), state("test:engine")),
                new RocketBlock(
                        new RocketPosition(0, 1, 0),
                        state("minecraft:chest"),
                        new RocketBlockEntityPayload(
                                ResourceLocation.tryParse("advancedrocketrycommunity:vanilla_container"),
                                chest
                        )
                ),
                new RocketBlock(new RocketPosition(0, 2, 0), state("test:seat")),
                new RocketBlock(new RocketPosition(0, 3, 0), state("test:guidance"))
        );
        return RocketStructureSnapshot.create(
                UUID.fromString("80000000-0000-0000-0000-000000000001"),
                OVERWORLD,
                ORIGIN,
                blocks,
                List.of(new RocketPosition(0, 2, 0)),
                new RocketStats(4, 200, 1_000, 500, 1, 1, 1, 1),
                500L
        );
    }

    private static RocketBlockState state(String id) {
        return new RocketBlockState(ResourceLocation.tryParse(id), Map.of());
    }

    private static RocketPosition absolute(RocketStructureSnapshot snapshot, RocketBlock block) {
        return snapshot.sourceOrigin().add(block.position());
    }

    private static UUID operation(int value) {
        return new UUID(0x1234L, value);
    }

    private record RocketRecord(UUID snapshotId, String contentHash) {
    }

    private static final class FakeWorld implements RocketTransactionWorld {
        private final Map<RocketPosition, RocketWorldBlock> blocks = new LinkedHashMap<>();
        private final Map<UUID, RocketRecord> rockets = new HashMap<>();
        private boolean loaded = true;
        private boolean spawnAllowed = true;
        private boolean removeRocketAllowed = true;
        private RocketPosition failRemoveAt;
        private RocketPosition failPlaceAt;

        static FakeWorld withStructure(RocketStructureSnapshot snapshot) {
            FakeWorld world = new FakeWorld();
            for (RocketBlock block : snapshot.blocks()) {
                world.blocks.put(absolute(snapshot, block), RocketWorldBlock.fromSnapshotBlock(block));
            }
            return world;
        }

        @Override
        public ResourceLocation dimension() {
            return OVERWORLD;
        }

        @Override
        public boolean isRegionLoaded(RocketRegion region) {
            return loaded && region.dimension().equals(OVERWORLD);
        }

        @Override
        public Optional<RocketWorldBlock> readBlock(RocketPosition absolutePosition) {
            return Optional.ofNullable(blocks.get(absolutePosition));
        }

        @Override
        public boolean removeBlockNoDrops(RocketPosition absolutePosition, RocketWorldBlock expected) {
            if (absolutePosition.equals(failRemoveAt) || !expected.equals(blocks.get(absolutePosition))) {
                return false;
            }
            blocks.remove(absolutePosition);
            return true;
        }

        @Override
        public boolean placeBlockIfEmpty(RocketPosition absolutePosition, RocketWorldBlock block) {
            if (absolutePosition.equals(failPlaceAt) || blocks.containsKey(absolutePosition)) {
                return false;
            }
            blocks.put(absolutePosition, block);
            return true;
        }

        @Override
        public Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId) {
            if (!spawnAllowed || rockets.containsKey(ROCKET_ID)) {
                return Optional.empty();
            }
            rockets.put(ROCKET_ID, new RocketRecord(snapshot.snapshotId(), snapshot.contentHash()));
            return Optional.of(ROCKET_ID);
        }

        @Override
        public boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash) {
            return new RocketRecord(snapshotId, contentHash).equals(rockets.get(rocketId));
        }

        @Override
        public boolean removeRocket(UUID rocketId, UUID snapshotId) {
            if (!removeRocketAllowed) {
                return false;
            }
            RocketRecord record = rockets.get(rocketId);
            if (record == null || !record.snapshotId().equals(snapshotId)) {
                return false;
            }
            rockets.remove(rocketId);
            return true;
        }

        String materialSignature() {
            StringBuilder signature = new StringBuilder();
            blocks.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        signature.append(entry.getKey()).append('=')
                                .append(entry.getValue().state().canonicalKey());
                        entry.getValue().payload().ifPresent(payload -> signature
                                .append('@').append(payload.adapterId())
                                .append(':').append(payload.data()));
                        signature.append(';');
                    });
            signature.append("rockets=").append(rockets.size());
            return signature.toString();
        }
    }

    private static final class InMemoryJournal implements RocketTransactionJournal {
        private final List<RocketTransactionRecord> history = new ArrayList<>();
        private final Map<UUID, RocketTransactionRecord> active = new HashMap<>();
        private Predicate<RocketTransactionRecord> failWhen = ignored -> false;
        private boolean failRemove;

        @Override
        public void write(RocketTransactionRecord record) {
            if (failWhen.test(record)) {
                failWhen = ignored -> false;
                throw new IllegalStateException("injected journal write failure");
            }
            history.add(record);
            active.put(record.transactionId(), record);
        }

        @Override
        public void remove(UUID transactionId) {
            if (failRemove) {
                throw new IllegalStateException("injected journal cleanup failure");
            }
            active.remove(transactionId);
        }
    }
}
