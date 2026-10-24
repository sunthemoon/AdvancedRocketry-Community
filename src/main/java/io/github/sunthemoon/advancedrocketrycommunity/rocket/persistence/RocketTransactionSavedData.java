package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Path;
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
    // Snapshot depth 32 plus transaction/list/root framing, as in the transfer journal.
    private static final int DIGEST_DEPTH = 35;

    private final Map<UUID, RocketPersistedTransaction> entries = new LinkedHashMap<>();
    private CompoundTag preservedBlockedData;
    private final Thread owner = Thread.currentThread();
    private final CheckedJournalCheckpoint checkpoint;
    private long mutationSequence = 1;
    private Path writerFile;
    private MinecraftServer ownerServer;
    private boolean unreadable;

    public RocketTransactionSavedData() { this(CheckedSavedDataPolicy.DirectoryForce.PLATFORM); }

    RocketTransactionSavedData(CheckedSavedDataPolicy.DirectoryForce force) {
        checkpoint = new CheckedJournalCheckpoint(force, DIGEST_DEPTH);
    }

    public static RocketTransactionSavedData get(MinecraftServer server) {
        RocketTransactionSavedData data = ManagedRootSupplier.obtain(server, TYPE,
                RocketTransactionSavedData::load, RocketTransactionSavedData::created);
        data.ownerServer = server;
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
    public CheckedJournalCheckpoint.Outcome writeOutcome() { return checkpoint.outcome(); }
    public Optional<CheckedJournalCheckpoint.Acknowledgement> acknowledgement() { return checkpoint.acknowledged(); }
    public boolean acknowledgedCurrent() {
        return operational() && checkpoint.outcome() == CheckedJournalCheckpoint.Outcome.COMMITTED
                && checkpoint.current(mutationSequence);
    }

    /** Computed once per desired entry version, not once per terrain member or polling tick. */
    public static final class ExpectedEntry {
        private final UUID transaction;
        private final String digest;
        private ExpectedEntry(UUID transaction, String digest) { this.transaction = transaction; this.digest = digest; }
    }

    public static ExpectedEntry expectedEntry(RocketPersistedTransaction entry) {
        Objects.requireNonNull(entry);
        return new ExpectedEntry(entry.record().transactionId(), CanonicalNbtDigest.of(encodeEntry(entry),
                Math.toIntExact(TYPE.maxUncompressedBytes()), DIGEST_DEPTH));
    }

    /** Exact W123 presence: current entries, current sequence and the acknowledged full entry digest must agree. */
    public boolean acknowledgedEntry(ExpectedEntry expected) {
        requireOwner(); Objects.requireNonNull(expected);
        return acknowledgedCurrent() && entries.containsKey(expected.transaction)
                && expected.digest.equals(checkpoint.acknowledged().orElseThrow().records().get(expected.transaction));
    }

    /** Journal facts only; the caller must additionally exclude live owners/removals and overlapping operations. */
    public boolean acknowledgedAbsent(UUID transaction, UUID snapshot, UUID entity) {
        return absenceStatus(transaction, snapshot, entity) == AbsenceStatus.PROVED
                && !checkpoint.acknowledged().orElseThrow().records().containsKey(transaction);
    }

    /** W27d owns waiting and all-bay rejection; this read-only query never writes or removes a record. */
    public enum AbsenceStatus {
        PROVED, WRITE_REQUIRED, ASSEMBLY_JOURNAL_PENDING, ASSEMBLY_JOURNAL_UNPROVED
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
        return checkpoint.outcome() == CheckedJournalCheckpoint.Outcome.NOT_COMMITTED
                || checkpoint.outcome() == CheckedJournalCheckpoint.Outcome.UNKNOWN
                ? AbsenceStatus.ASSEMBLY_JOURNAL_UNPROVED : AbsenceStatus.WRITE_REQUIRED;
    }

    /** One synchronous checked capture. The deployment caller must wait and re-query, not infer absence from success. */
    public CheckedJournalCheckpoint.Outcome checkpoint(MinecraftServer server) {
        if (ownerServer != server || !server.isSameThread()) {
            throw new IllegalStateException("Transaction checkpoint requires its owning server thread");
        }
        return saveChecked(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve(TYPE.fileName()), CheckedSavedDataPolicy.atomic(TYPE));
    }

    @Override
    public void save(java.io.File file) {
        requireOwner();
        if (isDirty()) { saveChecked(file.toPath(), CheckedSavedDataPolicy.atomic(TYPE)); }
    }

    CheckedJournalCheckpoint.Outcome saveChecked(Path file, CheckedSavedDataPolicy policy) {
        requireOwner();
        if (policy.type() != TYPE) { throw new IllegalArgumentException("Wrong transaction journal policy"); }
        Path normalized = file.toAbsolutePath().normalize();
        if (writerFile != null && !writerFile.equals(normalized)) {
            throw new IllegalArgumentException("Transaction writer cannot rebind its target");
        }
        writerFile = normalized;
        long sequence = mutationSequence;
        boolean wasUnknown = checkpoint.outcome() == CheckedJournalCheckpoint.Outcome.UNKNOWN;
        try {
            if (!operational()) { return checkpoint.refused(); }
            CompoundTag payload = save(new CompoundTag());
            Map<UUID, String> digests = new LinkedHashMap<>();
            for (Tag raw : payload.getList("transactions", Tag.TAG_COMPOUND)) {
                CompoundTag record = (CompoundTag) raw;
                digests.put(record.getUUID("transaction_id"), CanonicalNbtDigest.of(record,
                        Math.toIntExact(TYPE.maxUncompressedBytes()), DIGEST_DEPTH));
            }
            checkpoint.write(normalized, policy, sequence, payload, digests);
        } catch (RuntimeException beforeReplacement) {
            checkpoint.refused();
        } catch (Error ambiguous) {
            checkpoint.unknown();
            throw ambiguous;
        } finally {
            // Unlike the transfer scheduler's per-attempt result, this D0 view retains unresolved authority.
            if (wasUnknown && checkpoint.outcome() != CheckedJournalCheckpoint.Outcome.COMMITTED) {
                checkpoint.unknown();
            }
            super.setDirty(!acknowledgedCurrent());
        }
        return checkpoint.outcome();
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Transaction journal requires its owner thread");
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
                RocketPersistedTransaction entry = decodeEntry(entryTag);
                if (data.entries.put(entry.record().transactionId(), entry) != null) {
                    throw new IllegalArgumentException("Duplicate rocket transaction ID");
                }
            }
            if (migration.changed()) {
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.entries.clear();
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
        entries().forEach(entry -> transactions.add(encodeEntry(entry)));
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
        entries.put(record.transactionId(), new RocketPersistedTransaction(record, snapshot, ownerId));
        setDirty();
    }

    private void remove(UUID transactionId) {
        requireOwner();
        if (entries.remove(transactionId) != null) {
            setDirty();
        }
    }

    private static CompoundTag encodeEntry(RocketPersistedTransaction entry) {
        RocketTransactionRecord record = entry.record();
        CompoundTag data = new CompoundTag();
        data.putUUID("transaction_id", record.transactionId());
        data.putString("type", record.type().name());
        data.putString("phase", record.phase().name());
        data.putUUID("snapshot_id", record.snapshotId());
        data.putString("content_hash", record.contentHash());
        data.putString("dimension", record.region().dimension().toString());
        data.putIntArray("minimum", positionArray(record.region().minimum()));
        data.putIntArray("maximum", positionArray(record.region().maximum()));
        data.putInt("progress", record.progress());
        record.rocketEntityIdOptional().ifPresent(id -> data.putUUID("rocket_entity_id", id));
        data.putUUID("owner_id", entry.ownerId());
        data.put("snapshot", RocketSnapshotNbtCodec.encode(entry.snapshot()));
        return data;
    }

    private static RocketPersistedTransaction decodeEntry(CompoundTag data) {
        UUID transactionId = requireUuid(data, "transaction_id");
        RocketTransactionType type = RocketTransactionType.valueOf(requireString(data, "type"));
        RocketTransactionPhase phase = RocketTransactionPhase.valueOf(requireString(data, "phase"));
        UUID snapshotId = requireUuid(data, "snapshot_id");
        String contentHash = requireString(data, "content_hash");
        if (!contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Transaction content hash is invalid");
        }
        ResourceLocation dimension = ResourceLocation.tryParse(requireString(data, "dimension"));
        if (dimension == null) {
            throw new IllegalArgumentException("Transaction dimension is invalid");
        }
        RocketRegion region = new RocketRegion(
                dimension,
                requirePosition(data, "minimum"),
                requirePosition(data, "maximum")
        );
        int progress = requireInt(data, "progress");
        UUID rocketEntityId = data.hasUUID("rocket_entity_id")
                ? data.getUUID("rocket_entity_id")
                : null;
        UUID ownerId = requireUuid(data, "owner_id");
        if (!data.contains("snapshot", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Transaction snapshot is missing");
        }
        RocketSnapshotDecodeResult decoded = RocketSnapshotNbtCodec.decode(data.getCompound("snapshot"));
        if (decoded.status() != RocketSnapshotDecodeResult.Status.VALID) {
            throw new IllegalArgumentException("Transaction snapshot is not valid: " + decoded.message());
        }
        RocketStructureSnapshot snapshot = decoded.snapshot().orElseThrow();
        RocketTransactionRecord record = new RocketTransactionRecord(
                transactionId,
                type,
                phase,
                snapshotId,
                contentHash,
                region,
                progress,
                rocketEntityId
        );
        if (!RocketRegion.fromSnapshot(snapshot).equals(region)) {
            throw new IllegalArgumentException("Transaction region does not match snapshot bounds");
        }
        return new RocketPersistedTransaction(record, snapshot, ownerId);
    }

    private static UUID requireUuid(CompoundTag data, String key) {
        if (!data.hasUUID(key)) {
            throw new IllegalArgumentException("Missing transaction UUID " + key);
        }
        return data.getUUID(key);
    }

    private static String requireString(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing transaction string " + key);
        }
        return data.getString(key);
    }

    private static int requireInt(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing transaction integer " + key);
        }
        int value = data.getInt(key);
        if (value < 0) {
            throw new IllegalArgumentException("Negative transaction integer " + key);
        }
        return value;
    }

    private static RocketPosition requirePosition(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("Missing transaction position " + key);
        }
        int[] position = data.getIntArray(key);
        if (position.length != 3) {
            throw new IllegalArgumentException("Transaction position has the wrong length");
        }
        return new RocketPosition(position[0], position[1], position[2]);
    }

    private static int[] positionArray(RocketPosition position) {
        return new int[]{position.x(), position.y(), position.z()};
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
