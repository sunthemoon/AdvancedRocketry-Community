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

/** Same-dimension validate/snapshot/lock/extract/spawn/commit transaction. */
public final class RocketAssemblyTransaction {
    private final RocketTransactionWorld world;
    private final RocketRegionLockManager locks;
    private final RocketOperationLedger ledger;
    private final RocketTransactionJournal journal;
    private final RocketFailureInjector failureInjector;

    public RocketAssemblyTransaction(
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

    public RocketAssemblyTransaction(
            RocketTransactionWorld world,
            RocketRegionLockManager locks,
            RocketOperationLedger ledger,
            RocketTransactionJournal journal
    ) {
        this(world, locks, ledger, journal, RocketFailureInjector.NONE);
    }

    public RocketTransactionResult execute(UUID transactionId, RocketStructureSnapshot snapshot) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(snapshot, "snapshot");
        RocketOperationLedger.BeginResult begin = ledger.begin(transactionId);
        if (begin != RocketOperationLedger.BeginResult.STARTED) {
            RocketValidationCode code = begin == RocketOperationLedger.BeginResult.REPLAYED
                    ? RocketValidationCode.REQUEST_REPLAYED
                    : RocketValidationCode.OPERATION_LEDGER_FULL;
            return RocketTransactionResult.failure(
                    transactionId,
                    issue(code, snapshot.sourceOrigin(), "operation rejected before mutation"),
                    null,
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
                    RocketValidationCode.POSITION_OVERFLOW,
                    snapshot.sourceOrigin(),
                    exception.getMessage()
            );
        }
        Optional<RocketPosition> changed;
        try {
            if (!world.dimension().equals(snapshot.sourceDimension())) {
                return failWithoutMutation(
                        transactionId,
                        RocketValidationCode.ENTITY_STATE_INVALID,
                        snapshot.sourceOrigin(),
                        "snapshot dimension does not match transaction world"
                );
            }
            if (!world.isRegionLoaded(region)) {
                return failWithoutMutation(
                        transactionId,
                        RocketValidationCode.UNLOADED_CHUNK,
                        snapshot.sourceOrigin(),
                        "rocket region is not fully loaded"
                );
            }
            changed = firstMismatchedBlock(snapshot);
            if (changed.isPresent()) {
                return failWithoutMutation(
                        transactionId,
                        RocketValidationCode.WORLD_CHANGED,
                        changed.orElseThrow(),
                        "world block does not match the validated snapshot"
                );
            }
        } catch (RuntimeException exception) {
            return failWithoutMutation(
                    transactionId,
                    RocketValidationCode.WORLD_CHANGED,
                    snapshot.sourceOrigin(),
                    safeMessage(exception)
            );
        }

        ArrayList<PlacedBlock> extracted = new ArrayList<>();
        UUID rocketId = null;
        RocketRegionLockManager.LockToken lock = null;
        try {
            write(record(
                    transactionId,
                    snapshot,
                    region,
                    RocketTransactionPhase.SNAPSHOT_VALIDATED,
                    0,
                    null
            ));
            failureInjector.check(RocketFailurePoint.AFTER_SNAPSHOT, 0);

            lock = locks.tryAcquire(transactionId, region).orElseThrow(() -> new RocketTransactionAbortException(
                    RocketValidationCode.REGION_BUSY,
                    snapshot.sourceOrigin(),
                    "another rocket transaction holds an overlapping region"
            ));
            write(record(transactionId, snapshot, region, RocketTransactionPhase.LOCKED, 0, null));
            if (!world.isRegionLoaded(region)) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.UNLOADED_CHUNK,
                        snapshot.sourceOrigin(),
                        "rocket region unloaded before extraction"
                );
            }
            changed = firstMismatchedBlock(snapshot);
            if (changed.isPresent()) {
                throw new RocketTransactionAbortException(
                        RocketValidationCode.WORLD_CHANGED,
                        changed.orElseThrow(),
                        "world changed while acquiring the transaction lock"
                );
            }

            for (RocketBlock block : snapshot.blocks()) {
                RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
                RocketWorldBlock expected = RocketWorldBlock.fromSnapshotBlock(block);
                if (!world.removeBlockNoDrops(absolute, expected)) {
                    throw new RocketTransactionAbortException(
                            RocketValidationCode.EXTRACTION_FAILED,
                            absolute,
                            "world rejected no-drop block extraction"
                    );
                }
                extracted.add(new PlacedBlock(absolute, expected));
                write(record(
                        transactionId,
                        snapshot,
                        region,
                        RocketTransactionPhase.EXTRACTING,
                        extracted.size(),
                        null
                ));
                failureInjector.check(RocketFailurePoint.DURING_EXTRACTION, extracted.size());
            }
            write(record(
                    transactionId,
                    snapshot,
                    region,
                    RocketTransactionPhase.EXTRACTED,
                    extracted.size(),
                    null
            ));

            rocketId = world.spawnRocket(snapshot, transactionId).orElseThrow(
                    () -> new RocketTransactionAbortException(
                            RocketValidationCode.SPAWN_FAILED,
                            snapshot.sourceOrigin(),
                            "world rejected RocketEntity spawn"
                    )
            );
            write(record(
                    transactionId,
                    snapshot,
                    region,
                    RocketTransactionPhase.SPAWNED,
                    extracted.size(),
                    rocketId
            ));
            failureInjector.check(RocketFailurePoint.AFTER_SPAWN, extracted.size());
            failureInjector.check(RocketFailurePoint.BEFORE_COMMIT, extracted.size());
            write(record(
                    transactionId,
                    snapshot,
                    region,
                    RocketTransactionPhase.COMMITTED,
                    extracted.size(),
                    rocketId
            ));
            ledger.finish(transactionId, true);
            removeJournalBestEffort(transactionId);
            return RocketTransactionResult.success(transactionId, rocketId, extracted.size());
        } catch (RocketTransactionAbortException exception) {
            return rollback(
                    transactionId,
                    snapshot,
                    region,
                    extracted,
                    rocketId,
                    exception.code(),
                    exception.position().orElse(snapshot.sourceOrigin()),
                    exception.getMessage()
            );
        } catch (RuntimeException exception) {
            return rollback(
                    transactionId,
                    snapshot,
                    region,
                    extracted,
                    rocketId,
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

    private Optional<RocketPosition> firstMismatchedBlock(RocketStructureSnapshot snapshot) {
        for (RocketBlock block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            Optional<RocketWorldBlock> current = world.readBlock(absolute);
            if (current.isEmpty() || !current.orElseThrow().equals(RocketWorldBlock.fromSnapshotBlock(block))) {
                return Optional.of(absolute);
            }
        }
        return Optional.empty();
    }

    private RocketTransactionResult rollback(
            UUID transactionId,
            RocketStructureSnapshot snapshot,
            RocketRegion region,
            List<PlacedBlock> extracted,
            UUID rocketId,
            RocketValidationCode originalCode,
            RocketPosition failurePosition,
            String detail
    ) {
        writeBestEffort(record(
                transactionId,
                snapshot,
                region,
                RocketTransactionPhase.ROLLING_BACK,
                extracted.size(),
                rocketId
        ));
        boolean rollbackComplete = true;
        if (rocketId != null && !world.removeRocket(rocketId, snapshot.snapshotId())) {
            rollbackComplete = false;
        }
        int restored = 0;
        for (int index = extracted.size() - 1; index >= 0; index--) {
            PlacedBlock block = extracted.get(index);
            if (world.placeBlockIfEmpty(block.position(), block.block())) {
                restored++;
            } else {
                rollbackComplete = false;
            }
        }

        ledger.finish(transactionId, false);
        if (rollbackComplete && restored == extracted.size()) {
            writeBestEffort(record(
                    transactionId,
                    snapshot,
                    region,
                    RocketTransactionPhase.ROLLED_BACK,
                    restored,
                    null
            ));
            removeJournalBestEffort(transactionId);
            return RocketTransactionResult.failure(
                    transactionId,
                    issue(originalCode, failurePosition, detail),
                    rocketId,
                    extracted.size(),
                    restored
            );
        }

        writeBestEffort(record(
                transactionId,
                snapshot,
                region,
                RocketTransactionPhase.FAILED,
                restored,
                rocketId
        ));
        return RocketTransactionResult.failure(
                transactionId,
                issue(
                        RocketValidationCode.ROLLBACK_FAILED,
                        failurePosition,
                        "original=" + originalCode + "; " + detail
                ),
                rocketId,
                extracted.size(),
                restored
        );
    }

    private RocketTransactionResult failWithoutMutation(
            UUID transactionId,
            RocketValidationCode code,
            RocketPosition position,
            String detail
    ) {
        ledger.finish(transactionId, false);
        return RocketTransactionResult.failure(
                transactionId,
                issue(code, position, detail),
                null,
                0,
                0
        );
    }

    private void write(RocketTransactionRecord record) {
        journal.write(record);
    }

    private void writeBestEffort(RocketTransactionRecord record) {
        try {
            journal.write(record);
        } catch (RuntimeException ignored) {
            // The earlier durable phase remains for restart recovery.
        }
    }

    private void removeJournalBestEffort(UUID transactionId) {
        try {
            journal.remove(transactionId);
        } catch (RuntimeException ignored) {
            // A terminal durable record is safe for idempotent recovery cleanup.
        }
    }

    private static RocketTransactionRecord record(
            UUID transactionId,
            RocketStructureSnapshot snapshot,
            RocketRegion region,
            RocketTransactionPhase phase,
            int progress,
            UUID rocketId
    ) {
        return new RocketTransactionRecord(
                transactionId,
                RocketTransactionType.ASSEMBLY,
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
