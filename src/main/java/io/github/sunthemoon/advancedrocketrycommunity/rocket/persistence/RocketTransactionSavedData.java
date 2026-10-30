package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-attached versioned journal for same- and cross-level rocket recovery. */
public final class RocketTransactionSavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_rocket_transactions";
    public static final int SCHEMA_VERSION = 2;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    private static final CheckedJournalCheckpoint.Outcome COMMITTED = CheckedJournalCheckpoint.Outcome.COMMITTED;
    private static final CheckedJournalCheckpoint.Outcome NOT_COMMITTED = CheckedJournalCheckpoint.Outcome.NOT_COMMITTED;
    private static final CheckedJournalCheckpoint.Outcome UNKNOWN = CheckedJournalCheckpoint.Outcome.UNKNOWN;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private final Map<UUID, RocketPersistedTransaction> entries = new LinkedHashMap<>();
    private final RocketTransactionAdmission admission = new RocketTransactionAdmission();
    private CompoundTag preservedBlockedData;
    private final Thread owner = Thread.currentThread();
    private final long createdNanos = System.nanoTime();
    private final CheckedSavedDataPolicy.DirectoryForce directoryForce;
    // ADR-074 item 8: W123's session-local acknowledgement and outcome, changed only by owner-thread publication.
    private CheckedJournalCheckpoint.Acknowledgement acknowledged;
    private CheckedJournalCheckpoint.Outcome outcome = CheckedJournalCheckpoint.Outcome.UNACKNOWLEDGED;
    private CheckedRootWriter<RocketTransactionWriter.Capture, CheckedSavedDataPolicy, RocketTransactionWriter.Result> writer;
    private long mutationSequence = 1;
    private Path writerFile;
    private MinecraftServer ownerServer;
    private boolean unreadable;
    private long completedWriteCount, lastWriterNanos, lastWriterCpuNanos;
    private long awaitedMillis; // Release-only awaits advance the writer while the bound server tick stands still.
    private boolean failureReported;
    private RocketTransactionWriterCuts cuts; // the release-test native cut seam; null in production

    public RocketTransactionSavedData() { this(CheckedSavedDataPolicy.DirectoryForce.PLATFORM); }

    RocketTransactionSavedData(CheckedSavedDataPolicy.DirectoryForce force) {
        directoryForce = Objects.requireNonNull(force, "force");
    }

    public static RocketTransactionSavedData get(MinecraftServer server) {
        RocketTransactionSavedData data = ManagedRootSupplier.obtain(server, TYPE,
                RocketTransactionSavedData::load, RocketTransactionSavedData::created);
        data.ownerServer = server;
        return data;
    }

    /** The lifecycle hooks' view: the cached root, or the one vanilla reads from its file, never a new empty root. */
    public static RocketTransactionSavedData existing(MinecraftServer server) {
        if (server.overworld() == null) { return null; }
        var data = server.overworld().getDataStorage().get(RocketTransactionSavedData::load, DATA_NAME);
        if (data != null) { data.ownerServer = server; }
        return data;
    }

    /** Vanilla's fallback for a failed read must not turn an unreadable existing file into an empty authority. */
    static RocketTransactionSavedData created(ManagedRootPresence presence) {
        var data = new RocketTransactionSavedData();
        data.unreadable = presence == ManagedRootPresence.UNREADABLE;
        return data;
    }

    @Override
    public void setDirty(boolean dirty) {
        requireOwner();
        if (dirty) { mutationSequence = Math.incrementExact(mutationSequence); }
        super.setDirty(dirty);
    }

    public long mutationSequence() { return mutationSequence; }
    public CheckedJournalCheckpoint.Outcome writeOutcome() { return outcome; }
    public Optional<CheckedJournalCheckpoint.Acknowledgement> acknowledgement() { return Optional.ofNullable(acknowledged); }
    public boolean acknowledgedCurrent() {
        return operational() && outcome == COMMITTED && acknowledged != null && acknowledged.sequence() == mutationSequence;
    }

    /** Computed once per desired entry version, not once per terrain member or polling tick. */
    public static final class ExpectedEntry {
        private final UUID transaction;
        private final String digest;
        private ExpectedEntry(UUID transaction, String digest) { this.transaction = transaction; this.digest = digest; }
    }

    public static ExpectedEntry expectedEntry(RocketPersistedTransaction entry) {
        Objects.requireNonNull(entry);
        return new ExpectedEntry(entry.record().transactionId(), CanonicalNbtDigest.of(RocketTransactionCodec.encodeEntry(entry),
                Math.toIntExact(TYPE.maxUncompressedBytes()), RocketTransactionWriter.DIGEST_DEPTH));
    }

    /** Read-only admission sizing; never introduce a new over-bound root or replace an existing transaction ID. */
    public boolean canAppend(RocketPersistedTransaction candidate) {
        requireOwner(); Objects.requireNonNull(candidate);
        if (!operational() || entries.containsKey(candidate.record().transactionId())
                || entries.size() >= RocketLimits.MAX_ACTIVE_TRANSACTIONS) { return false; }
        return admission.canAppend(entries.values(), candidate);
    }

    /** Exact W123 presence: current entries, current sequence and the acknowledged full entry digest must agree. */
    public boolean acknowledgedEntry(ExpectedEntry expected) {
        requireOwner(); Objects.requireNonNull(expected);
        return acknowledgedCurrent() && entries.containsKey(expected.transaction)
                && expected.digest.equals(acknowledged.records().get(expected.transaction));
    }

    /** Journal facts only; the caller must additionally exclude live owners/removals and overlapping operations. */
    public boolean acknowledgedAbsent(UUID transaction, UUID snapshot, UUID entity) {
        return absenceStatus(transaction, snapshot, entity) == AbsenceStatus.PROVED
                && !acknowledged.records().containsKey(transaction);
    }

    /**
     * W27d owns waiting and all-bay rejection; this read-only query never writes or removes a record. ADR-074 item 8
     * appends WRITE_PENDING: a request is pending or a capture is in flight, so a stale outcome is not the answer.
     */
    public enum AbsenceStatus {
        PROVED, WRITE_REQUIRED, ASSEMBLY_JOURNAL_PENDING, ASSEMBLY_JOURNAL_UNPROVED, WRITE_PENDING
    }

    /** Logical ID names the assembly; snapshot/entity IDs also match independent disassembly transactions. */
    public AbsenceStatus absenceStatus(UUID logicalRocket, UUID snapshot, UUID entity) {
        requireOwner();
        Objects.requireNonNull(logicalRocket); Objects.requireNonNull(snapshot); Objects.requireNonNull(entity);
        if (!operational()) { return AbsenceStatus.ASSEMBLY_JOURNAL_UNPROVED; }
        for (var entry : entries.values()) {
            var record = entry.record();
            if (record.transactionId().equals(logicalRocket) || record.snapshotId().equals(snapshot)
                    || record.rocketEntityIdOptional().filter(entity::equals).isPresent()) {
                return AbsenceStatus.ASSEMBLY_JOURNAL_PENDING;
            }
        }
        if (acknowledgedCurrent()) { return AbsenceStatus.PROVED; }
        // The writer is sequential: after a request, the first other answer reflects a capture taken at or after it.
        if (writer != null && writer.busy()) { return AbsenceStatus.WRITE_PENDING; }
        return outcome == NOT_COMMITTED || outcome == UNKNOWN
                ? AbsenceStatus.ASSEMBLY_JOURNAL_UNPROVED : AbsenceStatus.WRITE_REQUIRED;
    }

    /** ADR-074 item 8: D0's checkpoint is a request; its answer is published on a later tick and B14 is asked again. */
    public enum CheckpointRequest { REQUESTED, REFUSED }

    public CheckpointRequest requestCheckpoint(MinecraftServer server) {
        if (ownerServer != server || !server.isSameThread()) {
            throw new IllegalStateException("Transaction checkpoint requires its owning server thread");
        }
        return requestWrite(ManagedRootSupplier.dataFile(server, TYPE), ATOMIC)
                ? CheckpointRequest.REQUESTED : CheckpointRequest.REFUSED;
    }

    /** Tick START: publish at most one completion, then start a pending request when the cadence allows. */
    public void pollWriter() {
        requireOwner();
        if (writer == null) { return; }
        if (shuttingDown()) { writer.latchShutdown(); return; }
        writer.poll(tick());
    }

    /** A capture is in flight, published or not, or a request is pending. */
    public boolean writerBusy() { requireOwner(); return writer != null && writer.busy(); }

    /** ADR-074 item 10, {@code ServerStoppingEvent}: refuse requests and starts; the predicate covers a tick exception. */
    public void latchShutdown() {
        requireOwner();
        if (writer != null) { writer.latchShutdown(); }
    }

    /** Overworld unload, before storage closes: resume the same drain (or start a skipped one), then always join. */
    public void close() {
        requireOwner();
        if (writer == null) {
            if (!operational() || !isDirty() || ownerServer == null && writerFile == null) { return; }
            writer(bind(writerFile != null ? writerFile : ManagedRootSupplier.dataFile(ownerServer, TYPE), ATOMIC));
        }
        writer.close(drainPolicy(), System::nanoTime, TimeUnit.NANOSECONDS::sleep);
    }

    /** {@code ServerStoppedEvent} and exceptional cleanup: join without publishing; it never reports success. */
    public void abort() {
        requireOwner();
        if (writer != null) { writer.abort(); }
    }

    /** The last stop-path report (drain, close or abort), for diagnostics and tests. */
    Optional<CheckedRootWriter.DrainReport> drainReport() {
        requireOwner();
        return writer == null ? Optional.empty() : Optional.ofNullable(writer.lastReport());
    }

    /** ADR-074 item 9: a running server's autosave and {@code /save-all} only request; they never wait. */
    @Override
    public void save(java.io.File file) {
        requireOwner();
        if (shuttingDown()) { stopSave(file.toPath()); }
        else if (isDirty()) { requestWrite(file.toPath(), ATOMIC); }
    }

    /** Detached journals only (unit tests and diagnostics): request one write and await its publication. */
    CheckedJournalCheckpoint.Outcome saveChecked(Path file, CheckedSavedDataPolicy policy) {
        requireOwner();
        if (ownerServer != null) { throw new IllegalStateException("A live transaction journal never awaits IO"); }
        if (requestWrite(file, policy)) { awaitWriter(); }
        return outcome;
    }

    /** Detached journals only: pump publication until the writer is idle. */
    void awaitWriter() {
        requireOwner();
        if (ownerServer != null && !RocketTransactionWriterCuts.enabled(ownerServer)) {
            throw new IllegalStateException("A live transaction journal never awaits IO");
        }
        if (writer == null) { return; }
        long started = System.nanoTime();
        try { writer.await(System::nanoTime, TimeUnit.NANOSECONDS::sleep); }
        finally { awaitedMillis += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + 1; }
    }

    boolean writerCompletionReady() { requireOwner(); return writer != null && writer.completionReady(); }

    /** The first request binds the target; a blocked or unreadable journal refuses without staging anything. */
    boolean requestWrite(Path file, CheckedSavedDataPolicy policy) {
        requireOwner();
        Path normalized = bind(file, policy);
        if (!operational()) { refuse(); return false; }
        if (shuttingDown()) {
            if (writer != null) { writer.latchShutdown(); }
            return false;
        }
        return writer(normalized).request(cuts == null ? policy : cuts.policy(policy), tick());
    }

    /** ADR-074 item 10, at the shutdown save: drain until the current RAM is covered, within the one budget. */
    private void stopSave(Path file) {
        Path normalized = bind(file, ATOMIC);
        if (!operational()) {
            if (isDirty()) { refuse(); }
            return;
        }
        if (writer == null && !isDirty()) { return; }
        writer(normalized).drain(drainPolicy(), System::nanoTime, TimeUnit.NANOSECONDS::sleep);
    }

    private CheckedSavedDataPolicy drainPolicy() { return cuts == null ? ATOMIC : cuts.policy(ATOMIC); }

    /** Release-test commands only (RocketTransactionWriterCuts#enabled): this journal's native cut seam. */
    RocketTransactionWriterCuts cuts() { if (cuts == null) { cuts = new RocketTransactionWriterCuts(); } return cuts; }
    RocketTransactionWriterCuts releaseTestCuts() { return cuts; }

    private Path bind(Path file, CheckedSavedDataPolicy policy) {
        if (policy.type() != TYPE) { throw new IllegalArgumentException("Wrong transaction journal policy"); }
        Path normalized = file.toAbsolutePath().normalize();
        if (writerFile != null && !writerFile.equals(normalized)) {
            throw new IllegalArgumentException("Transaction writer cannot rebind its target");
        }
        writerFile = normalized;
        return normalized;
    }

    private CheckedRootWriter<RocketTransactionWriter.Capture, CheckedSavedDataPolicy, RocketTransactionWriter.Result>
            writer(Path file) {
        if (writer == null) {
            writer = new CheckedRootWriter<>(RocketTransactionWriter.LABEL, RocketTransactionWriter.THREAD,
                    new Journal(file, directoryForce));
        }
        return writer;
    }

    /** W123's outcome rules at publication; dirty is recomputed against the current sequence. */
    private void publish(CheckedRootWriter.Completion<RocketTransactionWriter.Result> completion) {
        completedWriteCount++; lastWriterNanos = completion.writerNanos(); lastWriterCpuNanos = completion.writerCpuNanos();
        switch (completion.status()) {
            case COMMITTED -> {
                acknowledged = Objects.requireNonNull(completion.payload(), "payload").acknowledgement();
                outcome = COMMITTED;
            }
            // The old disk proof stays, without authority; unresolved authority stays UNKNOWN until a commit.
            case FAILED -> outcome = outcome == UNKNOWN ? UNKNOWN : NOT_COMMITTED;
            case UNKNOWN -> { acknowledged = null; outcome = UNKNOWN; }
        }
        super.setDirty(!acknowledgedCurrent());
        // R-040: one ERROR at the first failed or unknown write of a streak and one INFO at the commit that ends it.
        if (completion.status() == CheckedRootWriter.Status.COMMITTED) {
            if (failureReported) {
                failureReported = false;
                LOGGER.info("ARCE_TRANSACTION_JOURNAL_ACKNOWLEDGED sequence={} after an unacknowledged write", completion.sequence());
            }
        } else if (!failureReported) {
            failureReported = true;
            LOGGER.error("ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome={} sequence={} acknowledged={}", outcome,
                    completion.sequence(), acknowledged == null ? "none" : String.valueOf(acknowledged.sequence()));
        }
    }

    /** A refused request on the owner thread: NOT_COMMITTED with W123's sticky UNKNOWN, nothing staged. */
    private void refuse() {
        outcome = outcome == UNKNOWN ? UNKNOWN : NOT_COMMITTED;
        super.setDirty(!acknowledgedCurrent());
    }

    private boolean shuttingDown() {
        return ownerServer != null && (ownerServer.isStopped() || !ownerServer.isRunning());
    }

    /** The server tick; a detached journal counts one tick per monotonic millisecond, as the drain clock does. */
    private long tick() {
        return ownerServer == null ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - createdNanos)
                : ownerServer.getTickCount() + awaitedMillis;
    }

    /** Unit tests and benchmarks: a real but unstarted server supplies the tick and both shutdown flags. */
    void bindServerForTests(MinecraftServer server) { requireOwner(); ownerServer = server; }
    long completedWriteCount() { return completedWriteCount; }
    long lastWriterNanos() { return lastWriterNanos; }
    long lastWriterCpuNanos() { return lastWriterCpuNanos; }
    boolean writerInFlight() { requireOwner(); return writer != null && writer.inFlight(); }

    RocketTransactionWriter.Capture captureForTests() { requireOwner(); return new Journal(null, directoryForce).capture(mutationSequence); }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Transaction journal requires its owner thread");
        }
    }

    /** The writer's view of this root. Only {@code write} runs on IO, and it reads the capture and its own fields. */
    private final class Journal implements CheckedRootWriter.Root<RocketTransactionWriter.Capture, CheckedSavedDataPolicy,
            RocketTransactionWriter.Result> {
        private final Path file;
        private final CheckedSavedDataPolicy.DirectoryForce force;

        private Journal(Path file, CheckedSavedDataPolicy.DirectoryForce force) { this.file = file; this.force = force; }

        @Override public long mutationSequence() { return mutationSequence; }
        @Override public RocketTransactionWriter.Capture capture(long sequence) {
            return new RocketTransactionWriter.Capture(sequence, List.copyOf(entries.values()), acknowledged, outcome,
                    cuts == null ? null : cuts.beforeEncode(sequence));
        }
        @Override public CheckedRootWriter.Attempt<RocketTransactionWriter.Result> write(
                RocketTransactionWriter.Capture capture, CheckedSavedDataPolicy policy) {
            return RocketTransactionWriter.write(capture, policy, file, force);
        }
        @Override public void publish(CheckedRootWriter.Completion<RocketTransactionWriter.Result> completion) {
            RocketTransactionSavedData.this.publish(completion);
        }
        @Override public boolean needsWrite() { return isDirty(); }
        @Override public CheckedSavedDataPolicy merge(CheckedSavedDataPolicy pending, CheckedSavedDataPolicy later) {
            return later;
        }
        @Override public boolean serviced(CheckedSavedDataPolicy attempted, CheckedSavedDataPolicy requested) {
            return attempted.equals(requested);
        }
    }

    public static RocketTransactionSavedData load(CompoundTag source) {
        RocketTransactionSavedData data = new RocketTransactionSavedData();
        try {
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    ManagedSavedDataType.ROCKET_TRANSACTIONS,
                    source
            );
            if (migration.status() == SavedDataSchemaMigrator.MigrationStatus.FUTURE) {
                data.preservedBlockedData = source.copy();
                return data;
            }
            CompoundTag payload = migration.payload();
            Tag rawTransactions = payload.get("transactions");
            if (!(rawTransactions instanceof ListTag transactions)
                    || (!transactions.isEmpty() && transactions.getElementType() != Tag.TAG_COMPOUND)
                    || transactions.size() > RocketLimits.MAX_ACTIVE_TRANSACTIONS) {
                throw new IllegalArgumentException("Rocket transaction list is invalid or oversized");
            }
            for (Tag raw : transactions) {
                if (!(raw instanceof CompoundTag entryTag)) {
                    throw new IllegalArgumentException("Transaction entry has the wrong type");
                }
                RocketPersistedTransaction entry = RocketTransactionCodec.decodeEntry(entryTag);
                if (data.entries.put(entry.record().transactionId(), entry) != null) {
                    throw new IllegalArgumentException("Duplicate rocket transaction ID");
                }
                data.admission.replace(entry);
            }
            if (migration.changed()) {
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.entries.clear();
            data.admission.clear();
            data.preservedBlockedData = source.copy();
        }
        return data;
    }

    public boolean operational() {
        return preservedBlockedData == null && !unreadable;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

    public List<RocketPersistedTransaction> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.record().transactionId()))
                .toList();
    }

    /**
     * Whether an assembly or disassembly record claims the position: its region contains it in that dimension. A
     * blocked journal claims every position, since its records cannot be read. At most 256 records are checked.
     */
    public boolean claims(ResourceLocation dimension, RocketPosition position) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        if (!operational()) {
            return true;
        }
        for (RocketPersistedTransaction entry : entries.values()) {
            RocketRegion region = entry.record().region();
            if (region.dimension().equals(dimension) && region.contains(position)) {
                return true;
            }
        }
        return false;
    }

    public RocketTransactionJournal journalFor(
            RocketStructureSnapshot snapshot,
            UUID ownerId
    ) {
        requireOwner();
        if (!operational()) {
            throw new IllegalStateException("Rocket transaction journal contains unsupported or invalid data");
        }
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ownerId, "ownerId");
        return new BoundJournal(snapshot, ownerId);
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.ROCKET_TRANSACTIONS, target);
        ListTag transactions = new ListTag();
        entries().forEach(entry -> transactions.add(RocketTransactionCodec.encodeEntry(entry)));
        target.put("transactions", transactions);
        return target;
    }

    private void write(
            RocketTransactionRecord record,
            RocketStructureSnapshot snapshot,
            UUID ownerId
    ) {
        requireOwner();
        if (!record.snapshotId().equals(snapshot.snapshotId())
                || !record.contentHash().equals(snapshot.contentHash())) {
            throw new IllegalArgumentException("Journal record does not match bound snapshot");
        }
        RocketPersistedTransaction existing = entries.get(record.transactionId());
        if (existing == null && entries.size() >= RocketLimits.MAX_ACTIVE_TRANSACTIONS) {
            throw new IllegalStateException("Rocket transaction journal is full");
        }
        if (existing != null
                && (!existing.snapshot().snapshotId().equals(snapshot.snapshotId())
                || !existing.ownerId().equals(ownerId)
                || existing.record().type() != record.type())) {
            throw new IllegalArgumentException("Transaction ID was rebound to different authority data");
        }
        var entry = new RocketPersistedTransaction(record, snapshot, ownerId);
        entries.put(record.transactionId(), entry);
        admission.replace(entry);
        setDirty();
    }

    private void remove(UUID transactionId) {
        requireOwner();
        if (entries.remove(transactionId) != null) {
            admission.remove(transactionId);
            setDirty();
        }
    }

    private final class BoundJournal implements RocketTransactionJournal {
        private final RocketStructureSnapshot snapshot;
        private final UUID ownerId;

        private BoundJournal(RocketStructureSnapshot snapshot, UUID ownerId) {
            this.snapshot = snapshot;
            this.ownerId = ownerId;
        }

        @Override
        public void write(RocketTransactionRecord record) {
            RocketTransactionSavedData.this.write(record, snapshot, ownerId);
        }

        @Override
        public void remove(UUID transactionId) {
            RocketTransactionSavedData.this.remove(transactionId);
        }
    }
}
