package io.github.sunthemoon.advancedrocketrycommunity.rocket.testing;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Per-transaction fault injection; never substitutes a world or a journal record. */
public final class RocketTransactionFaultProbe {
    private final RocketTransactionCheckpoint checkpoint;
    private final Consumer<RocketTransactionRecord> observer;
    private boolean failureInjected;
    private boolean rollingBack;
    private boolean rollbackRejected;

    public RocketTransactionFaultProbe(
            RocketTransactionCheckpoint checkpoint, Consumer<RocketTransactionRecord> observer
    ) {
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    public RocketTransactionJournal journal(RocketTransactionJournal delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new RocketTransactionJournal() {
            @Override
            public void write(RocketTransactionRecord record) {
                delegate.write(record);
                rollingBack |= record.phase() == RocketTransactionPhase.ROLLING_BACK;
                if (checkpoint.reached(record)) {
                    observer.accept(record);
                }
                if (checkpoint.requiresRollback() && !failureInjected && record.progress() == 2
                        && (record.phase() == RocketTransactionPhase.EXTRACTING
                            || record.phase() == RocketTransactionPhase.RESTORING)) {
                    failureInjected = true;
                    throw new IllegalStateException("Release-test transaction fault after mutation 2");
                }
            }

            @Override
            public void remove(UUID transactionId) {
                delegate.remove(transactionId);
            }
        };
    }

    public RocketTransactionWorld world(RocketTransactionWorld delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new RocketTransactionWorld() {
            @Override
            public ResourceLocation dimension() {
                return delegate.dimension();
            }

            @Override
            public boolean isRegionLoaded(RocketRegion region) {
                return delegate.isRegionLoaded(region);
            }

            @Override
            public boolean canRestoreSnapshot(RocketStructureSnapshot snapshot) {
                return delegate.canRestoreSnapshot(snapshot);
            }

            @Override
            public Optional<RocketWorldBlock> readBlock(RocketPosition position) {
                return delegate.readBlock(position);
            }

            @Override
            public boolean removeBlockNoDrops(RocketPosition position, RocketWorldBlock expected) {
                return !(checkpoint.type() == RocketTransactionType.DISASSEMBLY && rejectOneRollback())
                        && delegate.removeBlockNoDrops(position, expected);
            }

            @Override
            public boolean placeBlockIfEmpty(RocketPosition position, RocketWorldBlock block) {
                return !(checkpoint.type() == RocketTransactionType.ASSEMBLY && rejectOneRollback())
                        && delegate.placeBlockIfEmpty(position, block);
            }

            @Override
            public Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId) {
                return delegate.spawnRocket(snapshot, transactionId);
            }

            @Override
            public boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash) {
                return delegate.rocketMatches(rocketId, snapshotId, contentHash);
            }

            @Override
            public boolean removeRocket(UUID rocketId, UUID snapshotId) {
                return delegate.removeRocket(rocketId, snapshotId);
            }
        };
    }

    private boolean rejectOneRollback() {
        if (checkpoint.phase() != RocketTransactionPhase.FAILED || !rollingBack || rollbackRejected) {
            return false;
        }
        rollbackRejected = true;
        return true;
    }
}
