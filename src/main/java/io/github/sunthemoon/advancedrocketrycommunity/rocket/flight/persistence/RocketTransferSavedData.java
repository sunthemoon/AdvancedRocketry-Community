package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferJournalCodec.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-owned, strict versioned Earth/Moon transfer journal. */
public final class RocketTransferSavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_rocket_transfers";
    public static final int ROOT_SCHEMA_VERSION = 2;

    private final Map<UUID, RocketTransferRecord> entries = new LinkedHashMap<>();
    private CompoundTag preservedBlockedData;
    private final RocketJournalCheckpoint checkpoint = new RocketJournalCheckpoint();
    private long mutationSequence = 1;
    private MinecraftServer ownerServer;
    private boolean saveFailureReported;
    private long lastAttemptTick;
    private boolean attempted;
    private boolean failedWrite;
    private WriteOutcome writeOutcome = WriteOutcome.UNACKNOWLEDGED;
    private RocketJournalWriter writer;
    private java.nio.file.Path writerFile;
    private boolean stopping;
    private static final class Effect {
        final UUID logical;
        final RocketTransferRecord expected;
        final java.util.function.BooleanSupplier action;
        final Runnable discarded;
        boolean committed;
        Effect(UUID logical, RocketTransferRecord expected, java.util.function.BooleanSupplier action, Runnable discarded) {
            this.logical = logical; this.expected = expected; this.action = action; this.discarded = discarded;
        }
        void run() {
            boolean accepted = false;
            try { accepted = action.getAsBoolean(); }
            finally { if (!accepted) { discarded.run(); } }
        }
    }
    private final Map<Long, Effect> effects = new LinkedHashMap<>();
    private long lastWriterNanos;

    public enum WriteOutcome { UNACKNOWLEDGED, COMMITTED, NOT_COMMITTED, UNKNOWN }

    /** The attempt's outcome does not imply acknowledgement of a later RAM mutation. */
    public record CheckedWriteResult(WriteOutcome outcome, long mutationSequence,
                                     Long acknowledgedSequence, boolean acknowledgedCurrent) { }

    public static RocketTransferSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        RocketTransferSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                RocketTransferSavedData::load,
                RocketTransferSavedData::new,
                DATA_NAME
        );
        data.ownerServer = server;
        return data;
    }

    public static RocketTransferSavedData load(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        RocketTransferSavedData data = new RocketTransferSavedData();
        CompoundTag preserved = source.copy();
        try {
            if (RocketNbtSize.uncompressedBytes(source)
                    > RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES) {
                throw new IllegalArgumentException("Transfer journal exceeds the fixed NBT limit");
            }
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    ManagedSavedDataType.ROCKET_TRANSFERS,
                    source
            );
            if (migration.status() == SavedDataSchemaMigrator.MigrationStatus.FUTURE) {
                throw new IllegalArgumentException("Transfer journal uses a future root schema");
            }
            CompoundTag payload = migration.payload();
            ListTag transfers = requireList(payload, "transfers", Tag.TAG_COMPOUND);
            if (transfers.size() > RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
                throw new IllegalArgumentException("Transfer journal exceeds the active-record bound");
            }
            for (Tag raw : transfers) {
                RocketTransferRecord record = decodeRecord((CompoundTag) raw);
                data.insertValidated(record);
            }
            if (migration.changed()) {
                data.mutationSequence++;
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.entries.clear();
            data.preservedBlockedData = preserved;
        }
        return data;
    }

    public boolean operational() {
        return preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

    public List<RocketTransferRecord> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(RocketTransferRecord::transferId))
                .toList();
    }

    public Optional<RocketTransferRecord> find(UUID transferId) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(transferId, "transferId")));
    }

    public Optional<RocketTransferRecord> findByLogicalRocket(UUID logicalRocketId) {
        Objects.requireNonNull(logicalRocketId, "logicalRocketId");
        return entries.values().stream()
                .filter(record -> record.logicalRocketId().equals(logicalRocketId))
                .findFirst();
    }

    public void put(RocketTransferRecord record) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        Objects.requireNonNull(record, "record");
        CompoundTag encoded = encodeRecord(record);
        if (RocketNbtSize.uncompressedBytes(encoded) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Transfer record exceeds the fixed NBT limit");
        }
        RocketTransferRecord existing = entries.get(record.transferId());
        if (existing == null && entries.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            throw new IllegalStateException("Transfer journal is full");
        }
        if (existing != null) {
            if (!existing.logicalRocketId().equals(record.logicalRocketId())
                    || !existing.checksum().equals(record.checksum())
                    || record.phase().ordinal() < existing.phase().ordinal()) {
                throw new IllegalArgumentException("Transfer ID was rebound or moved backwards");
            }
        } else {
            ensureNoAuthorityConflict(record);
        }
        entries.put(record.transferId(), record);
        mutationSequence++;
        setDirty();
    }

    /** Replaces a committed schema-1 record with its checksum-rebound schema-2 form. */
    public void migrateCommitted(RocketTransferRecord migrated) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        Objects.requireNonNull(migrated, "migrated");
        RocketTransferRecord legacy = entries.get(migrated.transferId());
        if (legacy == null
                || legacy.schemaVersion() != 1
                || migrated.schemaVersion() != RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION
                || legacy.phase() != RocketTransferPhase.COMMITTED
                || migrated.phase() != legacy.phase()
                || !legacy.logicalRocketId().equals(migrated.logicalRocketId())
                || !legacy.ownerId().equals(migrated.ownerId())
                || !legacy.sourceEntityId().equals(migrated.sourceEntityId())
                || !legacy.destinationEntityId().equals(migrated.destinationEntityId())
                || !legacy.sourceSnapshot().equals(migrated.sourceSnapshot())
                || !legacy.destinationSnapshot().equals(migrated.destinationSnapshot())
                || legacy.requiredFuel() != migrated.requiredFuel()
                || legacy.createdAtGameTime() != migrated.createdAtGameTime()) {
            throw new IllegalArgumentException("Transfer migration changed immutable authority data");
        }
        CompoundTag encoded = encodeRecord(migrated);
        if (RocketNbtSize.uncompressedBytes(encoded) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Migrated transfer record exceeds the fixed NBT limit");
        }
        entries.put(migrated.transferId(), migrated);
        mutationSequence++;
        setDirty();
    }

    public void remove(UUID transferId) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        if (entries.remove(Objects.requireNonNull(transferId, "transferId")) != null) {
            mutationSequence++;
            setDirty();
        }
    }

    /** Atomically replaces a committed landed reservation with its return flight. */
    public void replace(UUID previousTransferId, RocketTransferRecord replacement) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        Objects.requireNonNull(previousTransferId, "previousTransferId");
        Objects.requireNonNull(replacement, "replacement");
        RocketTransferRecord previous = entries.get(previousTransferId);
        if (previous == null
                || previous.phase() != RocketTransferPhase.COMMITTED
                || !previous.logicalRocketId().equals(replacement.logicalRocketId())) {
            throw new IllegalArgumentException("Only a committed landed reservation may be replaced");
        }
        entries.remove(previousTransferId);
        try {
            CompoundTag encoded = encodeRecord(replacement);
            if (RocketNbtSize.uncompressedBytes(encoded) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
                throw new IllegalArgumentException("Replacement transfer record exceeds the fixed NBT limit");
            }
            insertValidated(replacement);
        } catch (RuntimeException exception) {
            entries.put(previousTransferId, previous);
            throw exception;
        }
        mutationSequence++;
        setDirty();
    }

    /** Enqueues only this journal, never an unrelated world-wide SavedData save. */
    public void flush(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) { throw new IllegalStateException("Journal capture requires the server thread"); }
        // Detached GameTest journals retain their already-bound isolated file. get(server) binds live roots.
        if (isDirty()) { save((ownerServer == null && writerFile != null ? writerFile
                : server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName())).toFile()); }
    }

    /** Non-throwing exact-snapshot barrier; callers must inspect the result before dependent effects. */
    public boolean flushChecked(MinecraftServer server) {
        if (!server.isSameThread() || !operational()) { return false; }
        if (!acknowledgedCurrent()) { setDirty(); }
        flush(server);
        return acknowledgedCurrent();
    }

    /** A persistent failure cannot turn a per-tick retry into a synchronous write storm. */
    public boolean retryChecked(MinecraftServer server) {
        pollWriter(server.getTickCount());
        if (acknowledgedCurrent()) { return true; }
        if (failedWrite && attempted && (long) server.getTickCount() - lastAttemptTick < 200) { return false; }
        return flushChecked(server);
    }

    public boolean acknowledgedCurrent() {
        return operational() && !isDirty() && checkpoint.current(mutationSequence);
    }

    public long mutationSequence() { return mutationSequence; }

    /** Current and last acknowledged authority protect removal failures without locking new identities. */
    public boolean resourceMutationUnproved(UUID logical) {
        if (!operational()) { return true; }
        var acknowledged = checkpoint.acknowledged();
        // No retained disk generation after an observed failure means the affected identities are unknown.
        if (failedWrite && acknowledged.isEmpty()) { return true; }
        var record = findByLogicalRocket(logical).orElse(null);
        if (record == null) { return acknowledged.map(value -> value.logicalRockets().contains(logical)).orElse(false); }
        return acknowledged.map(value -> !RocketJournalCheckpoint.RecordVersion.of(record)
                .equals(value.versions().get(logical))).orElse(true);
    }

    public CheckedWriteResult checkedWriteResult() {
        return new CheckedWriteResult(writeOutcome, mutationSequence,
                checkpoint.acknowledged().map(RocketJournalCheckpoint.Acknowledgement::sequence).orElse(null),
                acknowledgedCurrent());
    }

    public record JournalGuard(UUID logical, UUID transfer, String recordDigest, String rootDigest, long sequence) { }

    public Optional<JournalGuard> acknowledgedGuard(UUID logical) {
        if (!acknowledgedCurrent()) { return Optional.empty(); }
        var acknowledged = checkpoint.acknowledged().orElseThrow();
        var record = findByLogicalRocket(logical).orElse(null);
        return Optional.of(new JournalGuard(logical, record == null ? null : record.transferId(),
                record == null ? null : acknowledged.records().get(record.transferId()),
                acknowledged.digest(), acknowledged.sequence()));
    }

    /** At most one dependent continuation per captured generation, and at most two generations. */
    public boolean afterAcknowledged(MinecraftServer server, UUID logical, Runnable effect) {
        return afterAcknowledged(server, logical, () -> { effect.run(); return true; }, () -> { });
    }

    public boolean afterAcknowledged(MinecraftServer server, UUID logical,
            java.util.function.BooleanSupplier effect, Runnable discarded) {
        if (!server.isSameThread() || !operational() || stopping) { return false; }
        if (!queueEffect(logical, effect, discarded)) { return false; }
        flushChecked(server); return true;
    }

    boolean queueEffect(UUID logical, Runnable effect) {
        return queueEffect(logical, () -> { effect.run(); return true; }, () -> { });
    }

    boolean queueEffect(UUID logical, java.util.function.BooleanSupplier effect, Runnable discarded) {
        if (!operational() || stopping) { return false; }
        var continuation = new Effect(logical, findByLogicalRocket(logical).orElse(null),
                Objects.requireNonNull(effect), Objects.requireNonNull(discarded));
        if (acknowledgedCurrent()) { continuation.run(); return true; }
        if (effects.containsKey(mutationSequence) || effects.size() == 2) { return false; }
        effects.put(mutationSequence, continuation); return true;
    }

    public boolean canQueueEffect() { return !stopping && !failedWrite && effects.size() < 2; }

    public void pollWriter(long tick) {
        if (writer != null) { writer.tick(tick, this::publish); }
        if (!stopping && acknowledgedCurrent()) {
            // A later disjoint generation may already be in RAM when the earlier exact write completes.
            // Wait for a clean acknowledgement, then require that it still contains the same authority.
            for (var entry : List.copyOf(effects.entrySet())) {
                if (!acknowledgedCurrent()) { break; }
                Effect effect = entry.getValue();
                if (!effect.committed) {
                    if (entry.getKey() < mutationSequence) { effects.remove(entry.getKey()); effect.discarded.run(); }
                    continue;
                }
                effects.remove(entry.getKey());
                if (findByLogicalRocket(effect.logical).orElse(null) == effect.expected) {
                    try { effect.run(); }
                    catch (RuntimeException failure) {
                        com.mojang.logging.LogUtils.getLogger().error("ARCE_TRANSFER_EFFECT_FAILED logical={} generation={}; retain journal authority for recovery",
                                effect.logical, entry.getKey(), failure);
                    }
                } else { effect.discarded.run(); }
            }
        }
    }

    private void publish(RocketJournalWriter.Completion completion) {
        checkpoint.publish(completion); lastWriterNanos = completion.writerNanos();
        Effect effect = effects.get(completion.sequence());
        if (effect != null && completion.outcome() == RocketJournalCheckpoint.Outcome.COMMITTED) { effect.committed = true; }
        writeOutcome = WriteOutcome.valueOf(completion.outcome().name());
        failedWrite = completion.outcome() != RocketJournalCheckpoint.Outcome.COMMITTED;
        setDirty(!checkpoint.current(mutationSequence) || failedWrite);
        if (failedWrite && !saveFailureReported) {
            com.mojang.logging.LogUtils.getLogger().error("ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority; dependent effects are blocked");
            saveFailureReported = true;
        } else if (!failedWrite) { saveFailureReported = false; }
    }

    public void stop() {
        if (writer == null && operational() && isDirty() && ownerServer != null) {
            requestWrite(ownerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()),
                    RocketJournalFailurePolicy.checked(ownerServer));
        }
        stopping = true; discardEffects();
        if (writer != null) {
            writer.drain(this::publish);
            if (operational() && isDirty() && !failedWrite) {
                writer.request(capture(), RocketJournalFailurePolicy.checked(ownerServer));
            }
            writer.close(this::publish);
        }
    }
    private void discardEffects() {
        var pending = List.copyOf(effects.values()); effects.clear();
        pending.forEach(effect -> effect.discarded.run());
    }
    public void abort() { stopping = true; discardEffects(); if (writer != null) { writer.abort(); } }

    @Override
    public void save(java.io.File file) {
        if (!isDirty() || !operational() || stopping
                || ownerServer != null && (!ownerServer.isRunning() || ownerServer.isStopped())) { return; }
        if (ownerServer != null && !ownerServer.isSameThread()) {
            throw new IllegalStateException("Transfer journal writes require the server thread");
        }
        if (ownerServer != null) { lastAttemptTick = ownerServer.getTickCount(); attempted = true; }
        try {
            requestWrite(file.toPath(), RocketJournalFailurePolicy.checked(ownerServer));
        } catch (RuntimeException failure) {
            setDirty();
            failedWrite = true;
            writeOutcome = WriteOutcome.NOT_COMMITTED;
        }
    }

    // Detached unit diagnostics await IO, but production never joins from flush/autosave.
    void saveChecked(java.nio.file.Path file, CheckedSavedDataPolicy policy) {
        if (!operational()) { return; }
        if (ownerServer != null) { throw new IllegalStateException("A live journal cannot synchronously await IO"); }
        requestWrite(file, policy); writer.drain(this::publish);
    }

    boolean requestWrite(java.nio.file.Path file, CheckedSavedDataPolicy policy) {
        if (!operational() || stopping) { return false; }
        java.nio.file.Path normalized = file.toAbsolutePath().normalize();
        if (writer == null) { writerFile = normalized; writer = new RocketJournalWriter(normalized); }
        if (!writerFile.equals(normalized)) { throw new IllegalArgumentException("Journal writer cannot rebind its target"); }
        return writer.request(capture(), policy);
    }
    RocketJournalWriter.Snapshot capture() { return new RocketJournalWriter.Snapshot(mutationSequence, entries()); }
    long lastWriterNanos() { return lastWriterNanos; }
    void drainForTests() { if (writer != null) { writer.drain(this::publish); } }
    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        return encode(entries(), target);
    }

    static CompoundTag encode(List<RocketTransferRecord> records, CompoundTag target) {
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.ROCKET_TRANSFERS, target);
        ListTag transfers = new ListTag();
        records.stream().sorted(Comparator.comparing(RocketTransferRecord::transferId)).forEach(record -> {
            CompoundTag encoded = encodeRecord(record);
            if (RocketNbtSize.uncompressedBytes(encoded) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
                throw new IllegalStateException("Encoded transfer record exceeds the fixed NBT limit");
            }
            transfers.add(encoded);
        });
        target.put("transfers", transfers);
        if (RocketNbtSize.uncompressedBytes(target) > RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES) {
            throw new IllegalStateException("Encoded transfer journal exceeds the fixed NBT limit");
        }
        return target;
    }

    private void insertValidated(RocketTransferRecord record) {
        if (entries.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            throw new IllegalArgumentException("Transfer journal exceeds the active-record bound");
        }
        if (entries.put(record.transferId(), record) != null) {
            throw new IllegalArgumentException("Transfer journal contains a duplicate transaction ID");
        }
        try {
            ensureNoAuthorityConflict(record);
        } catch (RuntimeException exception) {
            entries.remove(record.transferId());
            throw exception;
        }
    }

    private void ensureNoAuthorityConflict(RocketTransferRecord candidate) {
        RocketRegion destination = RocketRegion.fromSnapshot(candidate.destinationSnapshot());
        for (RocketTransferRecord existing : entries.values()) {
            if (existing.transferId().equals(candidate.transferId())) {
                continue;
            }
            if (existing.logicalRocketId().equals(candidate.logicalRocketId())) {
                throw new IllegalArgumentException("Logical rocket already has an active transfer");
            }
            if (RocketRegion.fromSnapshot(existing.destinationSnapshot()).overlaps(destination)) {
                throw new IllegalArgumentException("Transfer destination overlaps an active reservation");
            }
        }
    }

}
