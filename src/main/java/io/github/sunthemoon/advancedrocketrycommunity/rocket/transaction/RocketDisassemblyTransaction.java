package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Restores an assembled rocket only to its captured, fully empty, loaded origin. */
public final class RocketDisassemblyTransaction {
    private final RocketTransactionWorld world;
    private final RocketRegionLockManager locks;
    private final RocketOperationLedger ledger;
    private final RocketTransactionJournal journal;
    private final RocketFailureInjector failureInjector;

    public RocketDisassemblyTransaction(
            RocketTransactionWorld world,
            RocketRegionLockManager locks,
            RocketOperationLedger ledger,
            RocketTransactionJournal journal,
            RocketFailureInjector failureInjector
    ) {
        this.world = Objects.requireNonNull(world, "world");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
    }

    public RocketDisassemblyTransaction(
            RocketTransactionWorld world,
            RocketRegionLockManager locks,
            RocketOperationLedger ledger,
            RocketTransactionJournal journal
    ) {
        this(world, locks, ledger, journal, RocketFailureInjector.NONE);
    }

    public RocketTransactionResult execute(
            UUID transactionId,
            UUID rocketId,
            RocketStructureSnapshot snapshot
    ) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(rocketId, "rocketId");
        Objects.requireNonNull(snapshot, "snapshot");
        RocketOperationLedger.BeginResult begin = ledger.begin(transactionId);
        if (begin != RocketOperationLedger.BeginResult.STARTED) {
            RocketValidationCode code = begin == RocketOperationLedger.BeginResult.REPLAYED
                    ? RocketValidationCode.REQUEST_REPLAYED
                    : RocketValidationCode.OPERATION_LEDGER_FULL;
            return RocketTransactionResult.failure(
                    transactionId,
                    issue(code, snapshot.sourceOrigin(), "operation rejected before mutation"),
                    rocketId,
                    0,
                    0
            );
        }

        RocketRegion region;
        try {
            region = RocketRegion.fromSnapshot(snapshot);
        } catch (RuntimeException exception) {
            return failWithoutMutation(
                    transactionId,
                    rocketId,
                    RocketValidationCode.POSITION_OVERFLOW,
                    snapshot.sourceOrigin(),
                    exception.getMessage()
            );
        }
        Optional<RocketPosition> occupied;
        try {
            if (!world.dimension().equals(snapshot.sourceDimension())) {
                return failWithoutMutation(
                        transactionId,
                        rocketId,
                        RocketValidationCode.ENTITY_STATE_INVALID,
                        snapshot.sourceOrigin(),
                        "snapshot dimension does not match transaction world"
                );
            }
            if (!world.isRegionLoaded(region)) {
                return failWithoutMutation(
                        transactionId,
                        rocketId,
                        RocketValidationCode.UNLOADED_CHUNK,
                        snapshot.sourceOrigin(),
                        "disassembly region is not fully loaded"
                );
            }
            if (!world.rocketMatches(rocketId, snapshot.snapshotId(), snapshot.contentHash())) {
                return failWithoutMutation(
                        transactionId,
                        rocketId,
                        RocketValidationCode.ENTITY_STATE_INVALID,
                        snapshot.sourceOrigin(),
                        "RocketEntity identity or snapshot hash does not match"
                );
            }
            occupied = firstOccupiedPosition(snapshot);
            if (occupied.isPresent()) {
                return failWithoutMutation(
                        transactionId,
                        rocketId,
                        RocketValidationCode.TARGET_OCCUPIED,
                        occupied.orElseThrow(),
                        "disassembly target contains a block"
                );
            }
        } catch (RuntimeException exception) {
            return failWithoutMutation(
                    transactionId,
                    rocketId,
                    RocketValidationCode.WORLD_CHANGED,
                    snapshot.sourceOrigin(),
                    safeMessage(exception)
            );
        }

        ArrayList<PlacedBlock> restored = new ArrayList<>();
        RocketRegionLockManager.LockToken lock = null;
        try {
            write(record(
                    transactionId,
                    rocketId,
                    snapshot,
                    region,
                    RocketTransactionPhase.SNAPSHOT_VALIDATED,
                    0
            ));
            failureInjector.check(RocketFailurePoint.AFTER_SNAPSHOT, 0);
            lock = locks.tryAcquire(transactionId, region).orElseThrow(() -> new RocketTransactionAbortException(
                    RocketValidationCode.REGION_BUSY,
                    snapshot.sourceOrigin(),
                    "another rocket transaction holds an overlapping region"
            ));
            write(record(
                    transactionId,
                    rocketId,
                    snapshot,
                    region,
                    RocketTransactionPhase.LOCKED,
                    0
            ));

            if (!world.isRegionLoaded(region)) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.UNLOADED_CHUNK,
                        snapshot.sourceOrigin(),
                        "disassembly region unloaded while acquiring the lock"
                );
            }
            if (!world.rocketMatches(rocketId, snapshot.snapshotId(), snapshot.contentHash())) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.ENTITY_STATE_INVALID,
                        snapshot.sourceOrigin(),
                        "RocketEntity changed while acquiring the lock"
                );
            }
            occupied = firstOccupiedPosition(snapshot);
            if (occupied.isPresent()) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.TARGET_OCCUPIED,
                        occupied.orElseThrow(),
                        "disassembly target changed while acquiring the lock"
                );
            }

            for (RocketBlock block : snapshot.blocks()) {
                RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
                RocketWorldBlock restoredBlock = RocketWorldBlock.fromSnapshotBlock(block);
                if (!world.placeBlockIfEmpty(absolute, restoredBlock)) {
                    throw new RocketTransactionAbortException(
                            RocketValidationCode.TARGET_OCCUPIED,
                            absolute,
                            "world rejected transactional block restoration"
                    );
                }
                restored.add(new PlacedBlock(absolute, restoredBlock));
                write(record(
                        transactionId,
                        rocketId,
                        snapshot,
                        region,
                        RocketTransactionPhase.RESTORING,
                        restored.size()
                ));
                failureInjector.check(RocketFailurePoint.DURING_RESTORATION, restored.size());
            }
            write(record(
                    transactionId,
                    rocketId,
                    snapshot,
                    region,
                    RocketTransactionPhase.RESTORED,
                    restored.size()
            ));
            failureInjector.check(RocketFailurePoint.BEFORE_COMMIT, restored.size());
            if (!world.removeRocket(rocketId, snapshot.snapshotId())) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.ENTITY_STATE_INVALID,
                        snapshot.sourceOrigin(),
                        "world rejected authoritative RocketEntity removal"
                );
            }

            boolean committedRecordWritten = writeBestEffort(record(
                    transactionId,
                    rocketId,
                    snapshot,
                    region,
                    RocketTransactionPhase.COMMITTED,
                    restored.size()
            ));
            ledger.finish(transactionId, true);
            if (committedRecordWritten) {
                removeJournalBestEffort(transactionId);
            }
            return RocketTransactionResult.success(transactionId, rocketId, restored.size());
        } catch (RocketTransactionAbortException exception) {
            return rollback(
                    transactionId,
                    rocketId,
                    snapshot,
                    region,
                    restored,
                    exception.code(),
                    exception.position().orElse(snapshot.sourceOrigin()),
                    exception.getMessage()
            );
        } catch (RuntimeException exception) {
            return rollback(
                    transactionId,
                    rocketId,
                    snapshot,
                    region,
                    restored,
                    RocketValidationCode.EXTRACTION_FAILED,
                    snapshot.sourceOrigin(),
                    safeMessage(exception)
            );
        } finally {
            if (lock != null) {
                lock.close();
            }
        }
    }

    private Optional<RocketPosition> firstOccupiedPosition(RocketStructureSnapshot snapshot) {
        for (RocketBlock block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            if (world.readBlock(absolute).isPresent()) {
                return Optional.of(absolute);
            }
        }
        return Optional.empty();
    }

    private RocketTransactionResult rollback(
            UUID transactionId,
            UUID rocketId,
            RocketStructureSnapshot snapshot,
            RocketRegion region,
            List<PlacedBlock> restored,
            RocketValidationCode originalCode,
            RocketPosition failurePosition,
            String detail
    ) {
        writeBestEffort(record(
                transactionId,
                rocketId,
                snapshot,
                region,
                RocketTransactionPhase.ROLLING_BACK,
                restored.size()
        ));
        int removed = 0;
        boolean rollbackComplete = true;
        for (int index = restored.size() - 1; index >= 0; index--) {
            PlacedBlock block = restored.get(index);
            if (world.removeBlockNoDrops(block.position(), block.block())) {
                removed++;
            } else {
                rollbackComplete = false;
            }
        }
        if (!world.rocketMatches(rocketId, snapshot.snapshotId(), snapshot.contentHash())) {
            rollbackComplete = false;
        }
        ledger.finish(transactionId, false);
        if (rollbackComplete && removed == restored.size()) {
            writeBestEffort(record(
                    transactionId,
                    rocketId,
                    snapshot,
                    region,
                    RocketTransactionPhase.ROLLED_BACK,
                    removed
            ));
            removeJournalBestEffort(transactionId);
            return RocketTransactionResult.failure(
                    transactionId,
                    issue(originalCode, failurePosition, detail),
                    rocketId,
                    restored.size(),
                    removed
            );
        }

        writeBestEffort(record(
                transactionId,
                rocketId,
                snapshot,
                region,
                RocketTransactionPhase.FAILED,
                removed
        ));
        return RocketTransactionResult.failure(
                transactionId,
                issue(
                        RocketValidationCode.ROLLBACK_FAILED,
                        failurePosition,
                        "original=" + originalCode + "; " + detail
                ),
                rocketId,
                restored.size(),
                removed
        );
    }

    private RocketTransactionResult failWithoutMutation(
            UUID transactionId,
            UUID rocketId,
            RocketValidationCode code,
            RocketPosition position,
            String detail
    ) {
        ledger.finish(transactionId, false);
        return RocketTransactionResult.failure(
                transactionId,
                issue(code, position, detail),
                rocketId,
                0,
                0
        );
    }

    private void write(RocketTransactionRecord record) {
        journal.write(record);
    }

    private boolean writeBestEffort(RocketTransactionRecord record) {
        try {
            journal.write(record);
            return true;
        } catch (RuntimeException ignored) {
            // The preceding durable phase remains available for recovery.
            return false;
        }
    }

    private void removeJournalBestEffort(UUID transactionId) {
        try {
            journal.remove(transactionId);
        } catch (RuntimeException ignored) {
            // Terminal records are idempotently cleaned during recovery.
        }
    }

    private static RocketTransactionRecord record(
            UUID transactionId,
            UUID rocketId,
            RocketStructureSnapshot snapshot,
            RocketRegion region,
            RocketTransactionPhase phase,
            int progress
    ) {
        return new RocketTransactionRecord(
                transactionId,
                RocketTransactionType.DISASSEMBLY,
                phase,
                snapshot.snapshotId(),
                snapshot.contentHash(),
                region,
                progress,
                rocketId
        );
    }

    private static RocketValidationIssue issue(
            RocketValidationCode code,
            RocketPosition position,
            String detail
    ) {
        Map<String, String> parameters = detail == null || detail.isBlank()
                ? Map.of("coordinate_space", "absolute")
                : Map.of("coordinate_space", "absolute", "detail", detail);
        return new RocketValidationIssue(code, position, parameters);
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record PlacedBlock(RocketPosition position, RocketWorldBlock block) {
    }
}
