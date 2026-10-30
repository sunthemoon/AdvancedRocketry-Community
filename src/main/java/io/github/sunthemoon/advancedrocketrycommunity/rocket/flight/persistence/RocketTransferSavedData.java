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
    /** Derived encodability only, never durable authority. Validated mutations preserve this witness. */
    private boolean canonicalResidents = true;
    private final RocketCheckedInsertion checkedInsertion;
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
    private volatile boolean stopping;
    /** W44 section 2.1: the monotonic RAM slot; authority is only the latest writer confirmation. */
    private FirstEventOutbox firstMoonLanding = FirstEventOutbox.empty();
    private long outboxSequence = 1;
    /** W44 section 3.6: how this root was first obtained; a detached root behaves as loaded. */
    private ManagedRootPresence presence = ManagedRootPresence.LOADED;
    private final RocketJournalFirstWrite firstWrite = new RocketJournalFirstWrite();
    private final RocketJournalLeases leases = new RocketJournalLeases();
    private final RocketJournalSchedule schedule;
    private long lastWriterNanos;
    private long completedWriteCount;
    private RocketJournalNativeProbe nativeProbe;
    private RocketJournalHold nativeHold;
    private CheckedSavedDataPolicy nativeI6Policy;

    CheckedSavedDataPolicy nativeI6Policy(String mode, MinecraftServer server) {
        if (!mode.equals(RocketI6NativeCut.selection(server))) { throw new IllegalStateException("I6 cut is not armed"); }
        if (nativeI6Policy == null) { nativeI6Policy = RocketI6NativeCut.live(mode, server); }
        return nativeI6Policy;
    }

    public RocketJournalNativeProbe nativeProbe() { return nativeProbe; }

    public void startNativeProbe(MinecraftServer server) {
        RocketJournalNativeProbe.requireGate(server);
        if (nativeProbe != null) { throw new IllegalStateException("Probe already started in this session"); }
        nativeProbe = new RocketJournalNativeProbe();
        if (writer != null) { writer.observe(nativeProbe); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_JOURNAL_PROBE_STARTED");
    }

    void observeForTests(RocketJournalNativeProbe probe) {
        if (ownerServer != null) { throw new IllegalStateException("Detached probe only"); }
        nativeProbe = probe;
        if (writer != null) { writer.observe(probe); }
    }

    public void dumpNativeProbe(MinecraftServer server) throws java.io.IOException {
        RocketJournalNativeProbe.requireGate(server);
        if (nativeProbe != null) { nativeProbe.write(server.getServerDirectory().toPath()); }
    }

    RocketJournalHold nativeHold(MinecraftServer server) {
        RocketJournalNativeProbe.requireGate(server);
        if (!"HOLD".equals(System.getProperty(RocketJournalFailurePolicy.PROPERTY))) {
            throw new IllegalStateException("Selected-record hold requires HOLD mode");
        }
        if (nativeHold == null) {
            nativeHold = new RocketJournalHold(Long.getLong(RocketJournalHold.BOUND_PROPERTY, RocketJournalHold.MAX_MILLIS));
        }
        return nativeHold;
    }

    public void selectNativeHold(MinecraftServer server, UUID logical) { nativeHold(server).select(logical); }
    public void releaseNativeHold(MinecraftServer server) { nativeHold(server).release(); }
    public boolean nativeHoldWaiting(MinecraftServer server) { return nativeHold(server).waiting(); }

    public RocketTransferSavedData() { this(RocketJournalSchedule.LIMIT); }

    /** A smaller detached-journal bound exercises capacity refusals without enlarging production limits. */
    public RocketTransferSavedData(int reservationLimit) { this(reservationLimit, new RocketCheckedInsertion()); }
    RocketTransferSavedData(int reservationLimit, RocketCheckedInsertion checkedInsertion) {
        schedule = new RocketJournalSchedule(this, reservationLimit); this.checkedInsertion = checkedInsertion;
    }

    /** Single-use owner-thread token. Closing a consumed or invalidated token cannot release another reservation. */
    public static final class Reservation implements AutoCloseable {
        private final RocketJournalSchedule owner;
        private final UUID logical;
        Reservation(RocketJournalSchedule owner, UUID logical) { this.owner = owner; this.logical = logical; }
        public UUID logical() { return logical; }
        @Override public void close() { owner.close(this); }
    }

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
        return load(source, RocketJournalSchedule.LIMIT);
    }

    static RocketTransferSavedData load(CompoundTag source, int reservationLimit) {
        Objects.requireNonNull(source, "source");
        RocketTransferSavedData data = new RocketTransferSavedData(reservationLimit);
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
            data.canonicalResidents = transfers.isEmpty(); // Decoding bounds input, not its canonical re-encoding.
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
            data.leases.loaded(data.mutationSequence);
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
        if (nativeProbe != null) { nativeProbe.event(null, "mutation", mutationSequence, "MOON_ELECTED", 0); }
        outboxSequence = mutationSequence;
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
        if (nativeProbe != null) { nativeProbe.event(null, "mutation", mutationSequence, "MOON_DELIVERED", 0); }
        outboxSequence = mutationSequence;
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

    /** Named-pad absence only: a validated load grants no writer checkpoint or other dependent effects. */
    public boolean transferAbsenceAcknowledged(UUID transferId) {
        return schedulingAvailable() && !failedWrite && find(transferId).isEmpty()
                && leases.absenceAcknowledged(transferId, writeOutcome == WriteOutcome.UNACKNOWLEDGED);
    }

    public Optional<RocketTransferRecord> findByLogicalRocket(UUID logicalRocketId) {
        Objects.requireNonNull(logicalRocketId, "logicalRocketId");
        return entries.values().stream()
                .filter(record -> record.logicalRocketId().equals(logicalRocketId))
                .findFirst();
    }

    /** Read-only F1 reservation check; neither a candidate nor this check acknowledges PREPARED. */
    public void validateNewRecord(RocketTransferRecord record) { validateInsert(record, true, "New"); }

    /**
     * W85 M3: the insert checks of {@link #validateNewRecord} without its {@code stopping} refusal, so that a stop can
     * still carry a written-back landed record into its final capture. Read-only.
     */
    public void validateWriteBack(RocketTransferRecord record) { validateInsert(record, false, "Written-back"); }

    private void validateInsert(RocketTransferRecord record, boolean refuseStopping, String kind) {
        checkedInsertion.clear();
        Objects.requireNonNull(record, "record");
        if (!operational() || refuseStopping && stopping || entries.containsKey(record.transferId())
                || entries.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            throw new IllegalArgumentException(kind + " transfer authority is unavailable");
        }
        ensureNoAuthorityConflict(record);
        try {
            RocketJournalInsertionBudget.validate(entries(), record, firstMoonLanding, canonicalResidents, checkedInsertion::encode);
            canonicalResidents = true;
            checkedInsertion.validated(record, mutationSequence);
        }
        catch (IllegalStateException invalid) { throw new IllegalArgumentException(kind + " transfer does not fit the journal", invalid); }
    }

    public void put(RocketTransferRecord record) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        Objects.requireNonNull(record, "record");
        checkedInsertion.check(record, mutationSequence, canonicalResidents);
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
        changed(record, existing, record.phase().name());
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
        changed(migrated, legacy, migrated.phase().name());
    }

    public void remove(UUID transferId) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        var removed = entries.remove(Objects.requireNonNull(transferId, "transferId"));
        if (removed != null) {
            changed(removed, removed, "REMOVED");
        }
    }

    /** J1 only: a valid S1 capability, native proof and exact recorded version; ordinary put remains unchanged. */
    public boolean releasePassengers(io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleasePermit permit,
                                     boolean remainingCrewReady, java.util.function.BooleanSupplier nativeProof) {
        if (ownerServer != null && !ownerServer.isSameThread()) { throw new IllegalStateException("Release requires owner thread"); }
        var mutation = RocketLegacyRelease.prepare(this, permit, remainingCrewReady, nativeProof);
        if (mutation == null) { return false; }
        if (mutation.before() == mutation.after()) { return true; }
        var before = mutation.before(); var after = mutation.after();
        if (after == null) { entries.remove(before.transferId()); } else { entries.put(after.transferId(), after); }
        changed(after == null ? before : after, before, after == null ? "REMOVED" : after.phase().name());
        return true;
    }

    /** W170 D5: {@link RocketTransferConsumption} rewrites live PREPARED T into T' after its checks; nothing else calls it. */
    void consumed(RocketTransferRecord before, RocketTransferRecord after) {
        if (!operational() || stopping || entries.get(before.transferId()) != before) { throw new IllegalStateException("Bay consumption needs live T"); }
        entries.put(after.transferId(), after); changed(after, before, "BAY_CONSUMED");
    }

    private void changed(RocketTransferRecord record, RocketTransferRecord previous, String event) {
        checkedInsertion.clear();
        mutationSequence++; leases.changed(record.logicalRocketId(), record.transferId(), mutationSequence, previous);
        if (nativeProbe != null) { nativeProbe.event(record.logicalRocketId(), "mutation", mutationSequence, event, 0); }
        setDirty(); publishLeases();
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
        changed(replacement, previous, replacement.phase().name());
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
        if (acknowledgedCurrent()) { return clean(); }
        if (failedWrite && attempted && (long) server.getTickCount() - lastAttemptTick < 200) { return false; }
        flushChecked(server); return clean();
    }

    public boolean acknowledgedCurrent() {
        return operational() && !isDirty() && checkpoint.current(mutationSequence);
    }

    /** Whole-root consumers must also wait for reserved and budget-deferred owner-thread work. */
    public boolean clean() { return acknowledgedCurrent() && !schedule.outstanding(); }

    public boolean actionable(UUID logical) { return slotAcknowledged(logical) && !schedule.busy(logical); }

    public boolean actionable(UUID logical, Reservation reservation) {
        return slotAcknowledged(logical) && !schedule.busy(logical, reservation);
    }

    /** Owner-thread scheduling lease layered on the unchanged R17 IO-safe logical lease. */
    public java.util.function.BooleanSupplier actionableLease(UUID logical) {
        if (!actionable(logical)) { return () -> false; }
        var lease = logicalLease(logical, null, 0);
        return () -> actionable(logical) && lease.getAsBoolean();
    }

    public Optional<Reservation> reserve(UUID logical) { return schedule.reserve(logical); }
    public int outstandingEffectCount() { return schedule.size(); }
    public Optional<Reservation> canQueueEffect(UUID logical) {
        return failedWrite ? Optional.empty() : reserve(logical);
    }

    boolean schedulingAvailable() { return operational() && !stopping; }
    boolean effectWriteFailed() { return failedWrite; }
    boolean logicalUnchangedSince(UUID logical, long sequence) { return leases.unchangedSince(logical, sequence); }
    boolean slotAcknowledged(UUID logical) {
        if (!logicalAcknowledged(logical)) { return false; }
        var acknowledged = checkpoint.acknowledged().orElseThrow();
        var record = findByLogicalRocket(logical).orElse(null);
        return record == null ? !acknowledged.logicalRockets().contains(logical)
                : acknowledged.records().containsKey(record.transferId())
                        && RocketJournalCheckpoint.RecordVersion.of(record).equals(acknowledged.versions().get(logical));
    }
    boolean outboxAcknowledged(FirstEventOutbox expected) {
        return checkpoint.acknowledged().filter(value -> value.sequence() >= outboxSequence
                && value.firstMoonLanding().equals(expected)).isPresent();
    }

    /** Owner-thread effects need current authority and a live journal, not just an unchanged IO lease. */
    public boolean actionAuthorityAvailable() {
        return !stopping && !failedWrite && clean();
    }

    public long mutationSequence() { return mutationSequence; }

    /** Owner-thread acquisition; the ledger writer reads only volatile lifecycle, work and mutation flags. */
    public java.util.function.BooleanSupplier acknowledgedLease() {
        if (ownerServer != null && !ownerServer.isSameThread()) { throw new IllegalStateException("Journal lease requires the owner thread"); }
        if (!clean() || stopping) { return () -> false; }
        long captured = mutationSequence;
        return () -> !stopping && !schedule.outstanding() && mutationSequence == captured && leases.rootAcknowledged(captured);
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
     * them refuses it in RAM or the acknowledged generation; later L/T mutations permanently refuse this lease (R103).
     * Owner-thread acquisition; the returned lease reads only an immutable published view for writer replacement checks.
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
        // Preserve the pre-baseline exception only for an untouched absent identity, never after failure or stop.
        if (schedulingAvailable() && !failedWrite && checkpoint.acknowledged().isEmpty()
                && findByLogicalRocket(logical).isEmpty() && leases.untouched(logical) && !schedule.busy(logical)) { return false; }
        return !actionable(logical);
    }

    public CheckedWriteResult checkedWriteResult() {
        return new CheckedWriteResult(writeOutcome, mutationSequence,
                checkpoint.acknowledged().map(RocketJournalCheckpoint.Acknowledgement::sequence).orElse(null),
                acknowledgedCurrent());
    }

    public record JournalGuard(UUID logical, UUID transfer, String recordDigest, String rootDigest, long sequence) { }

    public Optional<JournalGuard> acknowledgedGuard(UUID logical) {
        if (!clean()) { return Optional.empty(); }
        var acknowledged = checkpoint.acknowledged().orElseThrow();
        var record = findByLogicalRocket(logical).orElse(null);
        return Optional.of(new JournalGuard(logical, record == null ? null : record.transferId(),
                record == null ? null : acknowledged.records().get(record.transferId()),
                acknowledged.digest(), acknowledged.sequence()));
    }

    /** One continuation or reservation per logical rocket, bounded independently of writer generations. */
    public boolean afterAcknowledged(MinecraftServer server, UUID logical, Runnable effect) {
        return afterAcknowledged(server, logical, () -> { effect.run(); return true; }, () -> { });
    }

    public boolean afterAcknowledged(MinecraftServer server, UUID logical,
            java.util.function.BooleanSupplier effect, Runnable discarded) {
        if (!server.isSameThread()) { return false; }
        try (var token = reserve(logical).orElse(null)) {
            return token != null && afterAcknowledged(server, token, effect, discarded);
        }
    }

    public boolean afterAcknowledged(MinecraftServer server, Reservation token, Runnable effect) {
        return afterAcknowledged(server, token, () -> { effect.run(); return true; }, () -> { });
    }

    public boolean afterAcknowledged(MinecraftServer server, Reservation token,
                                    java.util.function.BooleanSupplier effect, Runnable discarded) {
        return afterAcknowledged(server, token, false, false, effect, discarded);
    }

    public boolean afterAcknowledged(MinecraftServer server, Reservation token, boolean outbox, boolean duringFailure,
                                    java.util.function.BooleanSupplier effect, Runnable discarded) {
        if (!server.isSameThread() || !schedule.submit(token, outbox, duringFailure, effect, discarded, server.getTickCount())) {
            return false;
        }
        flushChecked(server); return true;
    }

    boolean queueEffect(UUID logical, Runnable effect) {
        return queueEffect(logical, () -> { effect.run(); return true; }, () -> { });
    }

    boolean queueEffect(UUID logical, java.util.function.BooleanSupplier effect, Runnable discarded) {
        try (var token = reserve(logical).orElse(null)) {
            return token != null && queueEffect(token, false, false, effect, discarded);
        }
    }

    boolean queueEffect(Reservation token, boolean outbox, boolean duringFailure,
                        java.util.function.BooleanSupplier effect, Runnable discarded) {
        return schedule.submit(token, outbox, duringFailure, effect, discarded, schedule.tick());
    }

    public void pollWriter(long tick) {
        if (!operational()) { return; }
        if (writer != null) { writer.tick(tick, this::publish); }
        schedule.visit(tick);
    }

    private void publish(RocketJournalWriter.Completion completion) {
        if (nativeProbe != null) { nativeProbe.event(null, "completion", completion.sequence(), completion.outcome().name(), completion.writerNanos()); }
        completedWriteCount++;
        firstWrite.completed(completion.outcome());
        if (firstWrite.appeared() && presence == ManagedRootPresence.PROVEN_ABSENT) {
            becomeUnreadable();
            return;
        }
        checkpoint.publish(completion); lastWriterNanos = completion.writerNanos();
        if (completion.outcome() == RocketJournalCheckpoint.Outcome.COMMITTED
                && completion.sequence() == mutationSequence) { canonicalResidents = true; }
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
        schedule.discard();
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
            writer.observe(nativeProbe);
        }
        if (!writerFile.equals(normalized)) { throw new IllegalArgumentException("Journal writer cannot rebind its target"); }
        policy = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoCutProbe.journalPolicy(ownerServer,
                io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTerminalProbe.journalPolicy(ownerServer, policy));
        policy = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCutProbe.journalPolicy(ownerServer, policy);
        policy = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLoaderS6JournalProbe.journalPolicy(ownerServer, policy);
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
        CompoundTag encoded = encode(entries(), firstMoonLanding, target);
        canonicalResidents = true;
        return encoded;
    }

    static CompoundTag encode(List<RocketTransferRecord> records, FirstEventOutbox firstMoonLanding,
                              CompoundTag target) {
        return RocketTransferJournalCodec.encodeRoot(records, firstMoonLanding, target);
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
        RocketJournalInsertionBudget.ensureNoAuthorityConflict(entries.values(), candidate);
    }

}
