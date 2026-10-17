package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferJournalCodec.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventSourceView;
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
    /** W44 section 2.2: root 3 carries the Moon landing outbox; each record keeps its own schema 2. */
    public static final int ROOT_SCHEMA_VERSION = ManagedSavedDataType.FIRST_MOON_LANDING_JOURNAL_SCHEMA;
    public static final String FIRST_MOON_LANDING = ManagedSavedDataType.FIRST_MOON_LANDING_KEY;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;

    /** W44 section 2.2: the EMPTY Moon landing outbox that the root 1 and 2 to root 3 migration adds. */
    public static CompoundTag emptyFirstMoonLanding() {
        return FirstEventOutbox.empty().encode(FirstEventKind.FIRST_MOON_LANDING);
    }

    private final Map<UUID, RocketTransferRecord> entries = new LinkedHashMap<>();
    private CompoundTag preservedBlockedData;
    private final RocketJournalCheckpoint checkpoint = new RocketJournalCheckpoint();
    private volatile long mutationSequence = 1;
    private MinecraftServer ownerServer;
    private boolean saveFailureReported;
    private long lastAttemptTick;
    private boolean attempted;
    private boolean failedWrite;
    private WriteOutcome writeOutcome = WriteOutcome.UNACKNOWLEDGED;
    private RocketJournalWriter writer;
    private java.nio.file.Path writerFile;
    private boolean stopping;
    /** W44 section 2.1: the monotonic RAM slot; authority is only the latest writer confirmation. */
    private FirstEventOutbox firstMoonLanding = FirstEventOutbox.empty();
    /** W44 section 3.6: how this root was first obtained; a detached root behaves as loaded. */
    private ManagedRootPresence presence = ManagedRootPresence.LOADED;
    private final RocketJournalFirstWrite firstWrite = new RocketJournalFirstWrite();
    private final RocketJournalLeases leases = new RocketJournalLeases();
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
    private long completedWriteCount;

    public enum WriteOutcome { UNACKNOWLEDGED, COMMITTED, NOT_COMMITTED, UNKNOWN }

    /** The attempt's outcome does not imply acknowledgement of a later RAM mutation. */
    public record CheckedWriteResult(WriteOutcome outcome, long mutationSequence,
                                     Long acknowledgedSequence, boolean acknowledgedCurrent) { }

    public static RocketTransferSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        RocketTransferSavedData data = ManagedRootSupplier.obtain(server, TYPE, storageLoad(server),
                RocketTransferSavedData::created);
        data.ownerServer = server;
        return data;
    }

    /** The cached root, or null; reads through the same load function so the load-fault seam applies. */
    public static RocketTransferSavedData existing(MinecraftServer server) {
        return server.overworld() == null ? null : server.overworld().getDataStorage().get(storageLoad(server), DATA_NAME);
    }

    /** The load function given to vanilla's storage; the static {@link #load} stays the validator's decoder. */
    private static java.util.function.Function<CompoundTag, RocketTransferSavedData> storageLoad(MinecraftServer server) {
        return tag -> {
            FirstEventCutSeams.beforeLoad(server, TYPE);
            return load(tag);
        };
    }

    /** The root-presence supplier's root: PROVEN_ABSENT or UNREADABLE (W44 section 3.6). */
    static RocketTransferSavedData created(ManagedRootPresence presence) {
        RocketTransferSavedData data = new RocketTransferSavedData();
        data.presence = presence;
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
            data.firstMoonLanding = FirstEventOutbox.decode(payload.get(FIRST_MOON_LANDING),
                    FirstEventKind.FIRST_MOON_LANDING);
            if (migration.changed()) {
                data.mutationSequence++;
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.entries.clear();
            data.firstMoonLanding = FirstEventOutbox.empty();
            data.preservedBlockedData = preserved;
        }
        return data;
    }

    /** Not blocked and not unreadable: an unreadable root is never written and holds every transfer (W44 3.6). */
    public boolean operational() {
        return preservedBlockedData == null && presence != ManagedRootPresence.UNREADABLE;
    }

    public ManagedRootPresence presence() {
        return preservedBlockedData != null ? ManagedRootPresence.BLOCKED : presence;
    }

    /** The RAM slot; never proof. */
    public FirstEventOutbox firstMoonLandingRam() {
        return firstMoonLanding;
    }

    /**
     * W44 section 2.4: the tagged durable view. Authority is the outbox read back from the latest exact writer
     * confirmation; a missing acknowledgement is SOURCE_UNCONFIRMED, never EMPTY.
     */
    public FirstEventSourceView durableFirstMoonLanding() {
        if (presence == ManagedRootPresence.UNREADABLE) {
            return FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_UNREADABLE);
        }
        if (preservedBlockedData != null) {
            return FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_BLOCKED);
        }
        return checkpoint.acknowledged().map(value -> FirstEventSourceView.known(value.firstMoonLanding()))
                .orElseGet(FirstEventSourceView::unconfirmed);
    }

    /**
     * W44 section 2.3, step 6: EMPTY to ELECTED in RAM only; the existing writer persists it and dependent effects wait
     * for its exact confirmation. The slot never moves backwards, so a second election is refused.
     */
    public boolean electFirstMoonLanding(FirstEvent.MoonLanding event) {
        Objects.requireNonNull(event, "event");
        if (!operational() || stopping || !firstMoonLanding.isEmpty()) { return false; }
        firstMoonLanding = FirstEventOutbox.elected(event);
        mutationSequence++;
        setDirty();
        return true;
    }

    /** W44 section 3.3: ELECTED(E) to DELIVERED(E) in RAM, after the receipt is durable; idempotent for DELIVERED(E). */
    public boolean deliverFirstMoonLanding(FirstEvent event) {
        Objects.requireNonNull(event, "event");
        if (!operational() || stopping || !event.equals(firstMoonLanding.event())) { return false; }
        if (firstMoonLanding.state() == FirstEventOutbox.State.DELIVERED) { return true; }
        firstMoonLanding = firstMoonLanding.delivered();
        mutationSequence++;
        setDirty();
        return true;
    }

    /**
     * W44 section 2.4: every valid or proven-absent load requests one baseline capture through the same writer at the
     * first END tick, whatever the transfer count, and again after an unknown outcome. The first request is
     * immediate; later attempts follow the existing 200-tick failure spacing. An unreadable or blocked root never
     * requests one.
     */
    public void tickBaseline(MinecraftServer server) {
        if (!operational() || stopping || checkpoint.acknowledged().isPresent()) { return; }
        retryChecked(server);
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

    /** Read-only F1 reservation check; neither a candidate nor this check acknowledges PREPARED. */
    public void validateNewRecord(RocketTransferRecord record) {
        Objects.requireNonNull(record, "record");
        if (!operational() || stopping || entries.containsKey(record.transferId())
                || entries.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            throw new IllegalArgumentException("New transfer authority is unavailable");
        }
        ensureNoAuthorityConflict(record);
        var candidate = new java.util.ArrayList<>(entries()); candidate.add(record);
        try { encode(candidate, firstMoonLanding, new CompoundTag()); }
        catch (IllegalStateException invalid) { throw new IllegalArgumentException("New transfer does not fit the journal", invalid); }
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
        leases.changed(record.logicalRocketId(), mutationSequence, existing);
        setDirty();
        publishLeases();
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
        leases.changed(migrated.logicalRocketId(), mutationSequence, legacy);
        setDirty();
        publishLeases();
    }

    public void remove(UUID transferId) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        var removed = entries.remove(Objects.requireNonNull(transferId, "transferId"));
        if (removed != null) {
            mutationSequence++;
            leases.changed(removed.logicalRocketId(), mutationSequence, removed);
            setDirty();
            publishLeases();
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
        leases.changed(replacement.logicalRocketId(), mutationSequence, previous);
        setDirty();
        publishLeases();
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

    /** Owner-thread effects need current authority and a live journal, not just an unchanged IO lease. */
    public boolean actionAuthorityAvailable() {
        return !stopping && !failedWrite && acknowledgedCurrent();
    }

    public long mutationSequence() { return mutationSequence; }

    /** Owner-thread acquisition; the returned lease reads only a volatile sequence off-thread. */
    public java.util.function.BooleanSupplier acknowledgedLease() {
        if (ownerServer != null && !ownerServer.isSameThread()) { throw new IllegalStateException("Journal lease requires the owner thread"); }
        if (!acknowledgedCurrent()) { return () -> false; }
        long captured = mutationSequence; return () -> mutationSequence == captured;
    }

    /** R17 item 3: this rocket's latest change is in the exact acknowledged generation; other rockets may be pending. */
    public boolean logicalAcknowledged(UUID logical) {
        return operational() && !stopping && leases.acknowledged(logical);
    }

    /** {@link #actionAuthorityAvailable()} for one logical rocket: another rocket's pending change does not refuse it. */
    public boolean actionAuthorityAvailable(UUID logical) {
        return !stopping && !failedWrite && logicalAcknowledged(logical);
    }

    /** {@link #acknowledgedGuard} for one logical rocket, read from the latest acknowledged generation. */
    public Optional<JournalGuard> logicalGuard(UUID logical) {
        if (!logicalAcknowledged(logical)) { return Optional.empty(); }
        var acknowledged = checkpoint.acknowledged().orElseThrow();
        var record = findByLogicalRocket(logical).orElse(null);
        return Optional.of(new JournalGuard(logical, record == null ? null : record.transferId(),
                record == null ? null : acknowledged.records().get(record.transferId()),
                acknowledged.digest(), acknowledged.sequence()));
    }

    /**
     * R17 item 3: the checked lease of logical rocket L for F1, F7, F8, the pre-F2 cancellation, settlement O3/O4 and
     * reconciliation C3 to C7. Null {@code transfer} or a serial below 1 checks no T or serial; another record that names
     * them refuses it in RAM or in the last acknowledged generation (LR1). Owner-thread acquisition; the returned lease
     * reads only an immutable published view, so the ledger writer may check it before replacement.
     */
    public java.util.function.BooleanSupplier logicalLease(UUID logical, UUID transfer, long serial) {
        if (ownerServer != null && !ownerServer.isSameThread()) { throw new IllegalStateException("Journal lease requires the owner thread"); }
        return leases.lease(logical, transfer, serial, mutationSequence);
    }

    private void publishLeases() {
        leases.publish(operational() && !stopping, checkpoint.acknowledged().orElse(null), entries.values());
    }

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
        if (!operational()) { return; }
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
        completedWriteCount++;
        firstWrite.completed(completion.outcome());
        if (firstWrite.appeared() && presence == ManagedRootPresence.PROVEN_ABSENT) {
            becomeUnreadable();
            return;
        }
        checkpoint.publish(completion); lastWriterNanos = completion.writerNanos();
        Effect effect = effects.get(completion.sequence());
        if (effect != null && completion.outcome() == RocketJournalCheckpoint.Outcome.COMMITTED) { effect.committed = true; }
        writeOutcome = WriteOutcome.valueOf(completion.outcome().name());
        failedWrite = completion.outcome() != RocketJournalCheckpoint.Outcome.COMMITTED;
        if (writeOutcome == WriteOutcome.UNKNOWN && ownerServer != null
                && firstMoonLanding.state() == FirstEventOutbox.State.ELECTED) {
            FirstEventCutSeams.boundary(ownerServer, FirstEventCutSeams.JOURNAL_UNKNOWN, FirstEventKind.FIRST_MOON_LANDING,
                    firstMoonLanding.event());
        }
        setDirty(!checkpoint.current(mutationSequence) || failedWrite);
        publishLeases();
        if (failedWrite && !saveFailureReported) {
            com.mojang.logging.LogUtils.getLogger().error("ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority; dependent effects are blocked");
            saveFailureReported = true;
        } else if (!failedWrite) { saveFailureReported = false; }
    }

    /** Nothing was overwritten; the root holds every transfer until a plain restart re-reads the file. */
    private void becomeUnreadable() {
        presence = ManagedRootPresence.UNREADABLE;
        // This completion has finished its IO. Cancel the retained request and successor before tick can retry.
        if (writer != null) { writer.abort(); }
        checkpoint.unknown();
        entries.clear();
        firstMoonLanding = FirstEventOutbox.empty();
        discardEffects();
        setDirty(false);
        publishLeases();
        AdvancedRocketryCommunity.LOGGER.error("ARCE_MANAGED_ROOT_UNREADABLE type={} file={} stage=first_write",
                DATA_NAME, writerFile);
    }

    public void stop() {
        if (writer == null && operational() && isDirty() && ownerServer != null) {
            requestWrite(ownerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()),
                    RocketJournalFailurePolicy.checked(ownerServer));
        }
        stopping = true; publishLeases(); discardEffects();
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
    public void abort() { stopping = true; publishLeases(); discardEffects(); if (writer != null) { writer.abort(); } }

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
        if (writer == null) {
            writerFile = normalized;
            writer = new RocketJournalWriter(normalized, FirstEventCutSeams.journalDirectoryForce(ownerServer));
        }
        if (!writerFile.equals(normalized)) { throw new IllegalArgumentException("Journal writer cannot rebind its target"); }
        policy = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTerminalProbe.journalPolicy(ownerServer, policy);
        return writer.request(capture(), presence == ManagedRootPresence.PROVEN_ABSENT ? firstWriteChecked(policy) : policy);
    }

    /**
     * W44 section 3.6: the first write of a proven-absent root re-checks the target on the writer thread just before
     * the atomic move. An entry that appeared is never replaced; the owner switches the root to UNREADABLE.
     */
    private CheckedSavedDataPolicy firstWriteChecked(CheckedSavedDataPolicy policy) {
        return policy.withCommitter((staged, target) -> {
            firstWrite.beforeMove(staged, target, policy);
            policy.committer().commit(staged, target);
        });
    }
    RocketJournalWriter.Snapshot capture() {
        return new RocketJournalWriter.Snapshot(mutationSequence, entries(), firstMoonLanding);
    }
    long lastWriterNanos() { return lastWriterNanos; }
    long completedWriteCount() { return completedWriteCount; }
    void drainForTests() { if (writer != null) { writer.drain(this::publish); } }
    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        if (presence == ManagedRootPresence.UNREADABLE) {
            throw new IllegalStateException("An unreadable transfer journal is never encoded");
        }
        return encode(entries(), firstMoonLanding, target);
    }

    static CompoundTag encode(List<RocketTransferRecord> records, FirstEventOutbox firstMoonLanding,
                              CompoundTag target) {
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
        target.put(FIRST_MOON_LANDING, firstMoonLanding.encode(FirstEventKind.FIRST_MOON_LANDING));
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
