package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;

/** Exact U versions and idempotent RAM mutations. Only the manager's aggregate requester invokes the checked writer. */
final class SealedJournalEntry {
    final RocketPersistedTransaction initial;
    private final RocketTransactionSavedData data;
    private final RocketTransactionSavedData.ExpectedEntry initialProof;
    private RocketPersistedTransaction desired, observed;
    private RocketTransactionSavedData.ExpectedEntry desiredProof;
    private boolean observedSame, removed, retained;

    SealedJournalEntry(RocketTransactionSavedData data, RocketPersistedTransaction initial) {
        if (initial.record().type() != RocketTransactionType.SEALED_DISASSEMBLY
                || initial.record().rocketEntityIdOptional().isEmpty()) { throw new IllegalArgumentException("Not a sealed U entry"); }
        this.data = data; this.initial = initial; desired = initial;
        initialProof = RocketTransactionSavedData.expectedEntry(initial); desiredProof = initialProof;
        retained = find() != null;
    }
    boolean elected() {
        return desired.record().phase() == RocketTransactionPhase.RESTORED || desired.record().phase() == RocketTransactionPhase.COMMITTED;
    }
    boolean owned() {
        var found = find();
        if (found == null) { return false; }
        if (found != observed) {
            observed = found;
            observedSame = found.record().equals(desired.record()) && found.ownerId().equals(desired.ownerId())
                    && (found.snapshot() == desired.snapshot()
                    || RocketSnapshotNbtCodec.encode(found.snapshot()).equals(RocketSnapshotNbtCodec.encode(desired.snapshot())));
        }
        return observedSame;
    }
    boolean compatible() { return data.operational() && (find() == null || owned()); }
    boolean retainedAcknowledged() { return data.acknowledgedEntry(initialProof); }
    boolean electedAcknowledged() { return elected() && data.acknowledgedEntry(desiredProof); }
    boolean absentAcknowledged() {
        var record = initial.record();
        return data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId());
    }
    void retain() {
        if (removed) { throw new IllegalStateException("Cannot reinsert retired U"); }
        if (find() != null) { requireOwned(); return; }
        if (retained) { throw new IllegalStateException("Retained U unexpectedly disappeared"); }
        // Account for the longest phase this entry may write, not only LOCKED's shorter string.
        if (!data.canAppend(withPhase(RocketTransactionPhase.COMMITTED))) { return; }
        retained = true;
        data.journalFor(initial.snapshot(), initial.ownerId()).write(initial.record());
    }
    void elect() {
        if (removed) { throw new IllegalStateException("Cannot elect retired U"); }
        requireOwned();
        if (elected()) { return; }
        var next = withPhase(RocketTransactionPhase.RESTORED);
        var proof = RocketTransactionSavedData.expectedEntry(next);
        data.journalFor(initial.snapshot(), initial.ownerId()).write(next.record());
        desired = next; desiredProof = proof; observed = null;
    }
    void remove() {
        if (find() != null) {
            requireOwned(); removed = true;
            data.journalFor(initial.snapshot(), initial.ownerId()).remove(initial.record().transactionId());
        } else { removed = true; }
    }
    private RocketPersistedTransaction find() {
        return data.entries().stream().filter(entry -> entry.record().transactionId().equals(initial.record().transactionId())).findFirst().orElse(null);
    }
    private void requireOwned() { if (!owned()) { throw new IllegalStateException("Sealed U authority changed"); } }
    private RocketPersistedTransaction withPhase(RocketTransactionPhase phase) {
        var before = initial.record();
        return new RocketPersistedTransaction(new RocketTransactionRecord(before.transactionId(), before.type(), phase,
                before.snapshotId(), before.contentHash(), before.region(), initial.snapshot().blocks().size(), before.rocketEntityId()),
                initial.snapshot(), initial.ownerId());
    }
}
