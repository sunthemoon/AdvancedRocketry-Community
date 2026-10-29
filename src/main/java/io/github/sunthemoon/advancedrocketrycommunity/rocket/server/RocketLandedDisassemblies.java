package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.Reservation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Presence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Progress;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Stage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.WriteBack;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * W85 Design M (P1-F8): session-only pending landed disassemblies P, at most one per logical rocket and at most 64.
 * The checked removal of the landed record T is acknowledged before any block is restored. The identical T is written
 * back only while restoration never started or after a certified clean rollback, and a written-back T keeps P and every
 * interlock until its own write is acknowledged, with no time limit. Nothing here is persisted.
 */
final class RocketLandedDisassemblies {
    static final int MAX_PENDING = RocketFlightLimits.MAX_ACTIVE_TRANSFERS;

    /** The request's world side: E, the v0.5 transaction and the requester's or the release-test hook's report. */
    interface Request {
        /** Original M0 time; the sentinel is only for legacy direct callers that have no proof stage. */
        default long requestedAt() { return Long.MIN_VALUE; }
        /** The installed ordinary caller uses B2; legacy synchronous adapters are isolated test fixtures. */
        default boolean retainedExecutor() { return false; }
        /** False means no executor was admitted; completion must not run inline before admission returns. */
        default boolean beginRetained(java.util.function.Consumer<SealedDisassembly.Result> completion) {
            throw new UnsupportedOperationException("No retained executor");
        }
        default RocketValidationCode admissionRefusal() { return RocketValidationCode.REGION_BUSY; }
        default void finishedRetained(SealedDisassembly.Result result) { }
        default boolean authorized() { return matches(); }
        /** E is alive and loaded and still equals S. */
        boolean matches();

        /**
         * W92-R2, read-only: the v0.5 transaction journal is operational and has no pending recovery overlapping E's
         * region, as {@code validateRocket} checked before today's one-tick {@code execute}.
         */
        boolean restorable();

        /** M3's view of E for the record that would be written back. */
        Presence presence(RocketTransferRecord record);

        /** Today's one-tick v0.5 transaction. Called at most once, with the latch already set. */
        RocketTransactionResult restore();

        /** Resumable completion. A synchronous adapter is not a native rollback certificate. */
        default void restore(java.util.function.Consumer<RocketTransactionResult> completion) {
            completion.accept(restore());
        }

        /** True only after the executor proves native rollback and checked U absence. */
        default boolean rollbackCertified() { return false; }

        /** Legacy direct execution remains unchanged until its caller is replaced by the retained executor. */
        default boolean requiresNativeRollbackProof() { return false; }

        /** Called at most once per P: the result's code, or REGION_BUSY when M2 did not restore (result null). */
        void finished(RocketValidationCode code, RocketTransactionResult result);
    }

    /** The service side: clock, bay gate, the journal continuation, the session sets, audits and the native probe. */
    interface Port {
        long now();

        boolean bayAllowsRemoval(RocketTransferRecord record);

        /** Queues L's continuation with the reservation that the caller took before its mutation, consuming it. */
        boolean queue(Reservation reservation, BooleanSupplier action, Runnable discarded);

        /**
         * {@link #queue} for M1's removal continuation (M2), which opts in to run during another record's failed write
         * (W86 section 3, Root decision R30). No other continuation opts in.
         */
        boolean queueRemoval(Reservation reservation, BooleanSupplier action, Runnable discarded);

        /** M2 step 1: audit {@code landed_reservation_released} and drop T from the session sets. */
        void released(RocketTransferRecord record, UUID entity);

        /** M3 acknowledged: settle T when E matches, then audit {@code landed_reservation_restored}. */
        void restored(RocketTransferRecord record, UUID entity, boolean settle);

        /** A gated release-test pause; a no-op on every ordinary server. */
        void probe(String boundary, RocketTransferRecord record);
    }

    static final class Pending {
        private final RocketTransferRecord record;
        private final UUID entity;
        private final long requestedAt;
        private final Request request;
        private final Port port;
        private final RocketTransferSavedData journal;
        private final Progress progress = new Progress();
        private RocketValidationCode code = RocketValidationCode.REGION_BUSY;
        private RocketTransactionResult result;
        private SealedDisassembly.Result retainedResult;
        private boolean reported;
        private boolean retainedReady;

        private Pending(RocketTransferRecord record, UUID entity, long requestedAt, Request request, Port port,
                        RocketTransferSavedData journal) {
            this.record = record;
            this.entity = entity;
            this.requestedAt = requestedAt;
            this.request = request;
            this.port = port;
            this.journal = journal;
        }

        UUID logical() {
            return record.logicalRocketId();
        }

        Stage stage() {
            return progress.stage();
        }

        boolean restorationStarted() {
            return progress.restorationStarted();
        }
    }

    private final Map<UUID, Pending> pending = new LinkedHashMap<>();

    /**
     * M0 then M1 for a request whose {@code validateRocket} passed and that has no P. SUCCESS means that L has no landed
     * record T, so the caller runs today's one-tick path; DISASSEMBLY_PENDING means that T was released into a new P.
     * Every other code changed nothing and kept T.
     */
    RocketValidationCode begin(RocketEntity rocket, Supplier<Optional<RocketValidationCode>> preflight, Request request,
                               BiFunction<MinecraftServer, RocketTransferSavedData, Port> ports) {
        if (!(rocket.level() instanceof ServerLevel level) || !rocket.operational()) {
            return RocketValidationCode.REGION_BUSY;
        }
        RocketTransferSavedData journal = RocketTransferSavedData.get(level.getServer());
        return begin(rocket, journal, ports.apply(level.getServer(), journal), preflight, request);
    }

    /** {@link #begin} against a given journal; the W85 A1 GameTests use an isolated checked journal file. */
    RocketValidationCode begin(RocketEntity rocket, RocketTransferSavedData journal, Port port,
                               Supplier<Optional<RocketValidationCode>> preflight, Request request) {
        if (!journal.operational() || !rocket.operational()) {
            return RocketValidationCode.REGION_BUSY;
        }
        UUID logical = rocket.assemblyTransactionId().orElseThrow();
        if (pending.containsKey(logical)) {
            return RocketValidationCode.DISASSEMBLY_PENDING;
        }
        RocketTransferRecord record = journal.findByLogicalRocket(logical).orElse(null);
        // N2: L's own reservation is taken before any change and held through M1's removal and its queueing.
        try (var reservation = reservation(journal, record)) {
            var decision = admission(rocket, record, journal, port, preflight, true, reservation);
            return decision.admission() == RocketLandedDisassemblyRules.Admission.RELEASE
                    ? release(journal, record, rocket.getUUID(), request, port, reservation) : decision.code();
        }
    }

    /**
     * M0's read-only decision for L's record. Production always applies the record clause; W92-R1's G9 also evaluates
     * it without the clause to show that the clause alone refuses a governed record. L's reservation is taken and
     * closed here, so nothing changes.
     */
    RocketLandedDisassemblyRules.Decision admission(RocketEntity rocket, RocketTransferRecord record,
                                                    RocketTransferSavedData journal, Port port,
                                                    Supplier<Optional<RocketValidationCode>> preflight,
                                                    boolean recordClause) {
        try (var reservation = reservation(journal, record)) {
            return admission(rocket, record, journal, port, preflight, recordClause, reservation);
        }
    }

    private RocketLandedDisassemblyRules.Decision admission(RocketEntity rocket, RocketTransferRecord record,
                                                            RocketTransferSavedData journal, Port port,
                                                            Supplier<Optional<RocketValidationCode>> preflight,
                                                            boolean recordClause, Reservation reservation) {
        return RocketLandedDisassemblyRules.admit(record != null,
                journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow()),
                () -> (!recordClause || RocketLandedDisassemblyRules.landedRecordAdmitted(record.schemaVersion(),
                        record.nativeTransfer().isPresent()))
                        && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record),
                () -> port.bayAllowsRemoval(record), reservation != null, hasCapacity(), preflight);
    }

    /**
     * {@code canQueueEffect(L)} for a present T: null while L has a continuation or a reservation, the bound is
     * reached, the journal stops or a write has failed. Without T nothing is removed, so none is taken.
     */
    private static Reservation reservation(RocketTransferSavedData journal, RocketTransferRecord record) {
        return record == null ? null : journal.canQueueEffect(record.logicalRocketId()).orElse(null);
    }

    /**
     * W44's FM12a hook keeps today's plain removal: no preflight, no P, an unchecked continuation and REGION_BUSY.
     * True only when L has no landed record and its last change is proved.
     */
    static boolean releasePlain(RocketEntity rocket, BiFunction<MinecraftServer, RocketTransferSavedData, Port> ports) {
        Objects.requireNonNull(rocket, "rocket");
        if (!(rocket.level() instanceof ServerLevel level) || !rocket.operational()) {
            return false;
        }
        RocketTransferSavedData journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.operational()) {
            return false;
        }
        RocketTransferRecord record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow())
                .orElse(null);
        if (record != null && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record)) {
            removePlain(journal, record, rocket.getUUID(), ports.apply(level.getServer(), journal));
            return false;
        }
        return !journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow());
    }

    /**
     * The plain removal of T. W91 (R23): L must be actionable, and its reservation is taken before the removal and
     * consumed by the continuation; otherwise nothing changes. True when T was removed.
     */
    static boolean removePlain(RocketTransferSavedData journal, RocketTransferRecord record, UUID entity, Port port) {
        if (!journal.actionable(record.logicalRocketId()) || !port.bayAllowsRemoval(record)) { return false; }
        try (var reservation = journal.reserve(record.logicalRocketId()).orElse(null)) {
            if (reservation == null) { return false; }
            journal.remove(record.transferId());
            port.queue(reservation, () -> {
                if (port.bayAllowsRemoval(record)) { port.released(record, entity); }
                return true;
            }, () -> { });
            return true;
        }
    }

    boolean pending(UUID logical) {
        return logical != null && pending.containsKey(logical);
    }

    Pending find(UUID logical) {
        return pending.get(logical);
    }

    int size() {
        return pending.size();
    }
    java.util.Set<UUID> logicalIds() { return java.util.Set.copyOf(pending.keySet()); }

    boolean hasCapacity() {
        return pending.size() < MAX_PENDING;
    }

    /**
     * M1, after M0 admitted the release: record P, remove T, then queue the removal's continuation with L's reservation,
     * which M0 took and which is held through the removal. Without it nothing changes. The queue refusal branch is
     * therefore unreachable; it restores the identical T in RAM before any capture can have seen its absence.
     */
    RocketValidationCode release(RocketTransferSavedData journal, RocketTransferRecord record, UUID entity,
                                 Request request, Port port, Reservation reservation) {
        Pending entry = prepareRelease(journal, record, entity, request, port, reservation);
        if (entry == null) { return RocketValidationCode.REGION_BUSY; }
        if (!port.queueRemoval(reservation, () -> removalAcknowledged(entry), () -> removalDiscarded(entry))) {
            end(entry);
            try {
                journal.put(record);
            } catch (RuntimeException refused) {
                notRestored(entry, "journal");
            }
            return RocketValidationCode.REGION_BUSY;
        }
        return RocketValidationCode.DISASSEMBLY_PENDING;
    }

    /** M1 under the proof coordinator's token; that coordinator alone queues the removal continuation. */
    Pending prepareRelease(RocketTransferSavedData journal, RocketTransferRecord record, UUID entity,
                           Request request, Port port, Reservation reservation) {
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(record, "record");
        if (pending.containsKey(record.logicalRocketId()) || !hasCapacity() || reservation == null
                || !reservation.logical().equals(record.logicalRocketId())) {
            return null;
        }
        long requestedAt = request.requestedAt() == Long.MIN_VALUE ? port.now() : request.requestedAt();
        Pending entry = new Pending(record, Objects.requireNonNull(entity, "entity"), requestedAt,
                Objects.requireNonNull(request, "request"), Objects.requireNonNull(port, "port"), journal);
        pending.put(entry.logical(), entry);
        journal.remove(record.transferId());
        return entry;
    }

    /**
     * M2, inside the removal's exact-acknowledgement continuation; it also runs while another record's write has failed
     * definitely, because the acknowledgement that it needs is already retained. Every handled case returns true (N1).
     */
    boolean removalAcknowledged(Pending entry) {
        if (!current(entry) || entry.stage() != Stage.RELEASED) {
            return true;
        }
        try {
            entry.port.probe("K2", entry.record);
            entry.port.released(entry.record, entry.entity);
            if (!RocketLandedDisassemblyRules.restore(entry.requestedAt, entry.port.now(), entry.request.matches(),
                    entry.request.restorable(), entry.port.bayAllowsRemoval(entry.record))) {
                writeBack(entry);
                return true;
            }
            if (entry.request.retainedExecutor()) {
                // The journal callback runs after B2's END-tick work. P retains its capacity/region until the next
                // manager turn, before the shared inspection allowance is consumed. No U or executor exists yet.
                entry.retainedReady = true;
            } else {
                entry.progress.startRestoration();
                entry.request.restore(result -> restorationCompleted(entry, result));
            }
        } catch (RuntimeException failure) {
            restorationFailed(entry);
        }
        return true;
    }

    /** Service acknowledged handoffs before B2 spends this server tick's existing inspection budget. */
    void tickRetained() {
        for (var entry : List.copyOf(pending.values())) {
            if (!entry.retainedReady || entry.stage() != Stage.RELEASED) { continue; }
            entry.retainedReady = false;
            try {
                if (!entry.request.authorized() || !RocketLandedDisassemblyRules.restore(entry.requestedAt,
                        entry.port.now(), entry.request.matches(), entry.request.restorable(), entry.port.bayAllowsRemoval(entry.record))) {
                    writeBack(entry); continue;
                }
                boolean admitted;
                try { admitted = entry.request.beginRetained(result -> retainedCompleted(entry, result)); }
                catch (RuntimeException | Error uncertain) { entry.progress.startRestoration(); throw uncertain; }
                if (!admitted) { entry.code = entry.request.admissionRefusal(); writeBack(entry); continue; }
                entry.progress.startRestoration();
            } catch (RuntimeException failure) { restorationFailed(entry); }
        }
    }

    /** Before the transfer writer stops, M3 still owns a handoff that has entered no executor and made no terrain change. */
    void cancelRetainedHandoffs() {
        for (var entry : List.copyOf(pending.values())) {
            if (entry.retainedReady && entry.stage() == Stage.RELEASED) {
                entry.retainedReady = false; writeBack(entry);
            }
        }
    }

    private void retainedCompleted(Pending entry, SealedDisassembly.Result result) {
        if (!current(entry) || entry.stage() != Stage.RESTORING) { return; }
        try {
            entry.retainedResult = Objects.requireNonNull(result);
            if (result == SealedDisassembly.Result.ROLLBACK_PROVED && entry.request.matches()) {
                entry.progress.certifyCleanRollback(); writeBack(entry);
            } else {
                // REJECTED/PROOF_PLAN_LIMIT are not native rollback certificates. No invented block counters.
                end(entry); report(entry);
            }
        } catch (RuntimeException failure) { restorationFailed(entry); }
        catch (Error fatal) { end(entry); throw fatal; }
    }

    private void restorationCompleted(Pending entry, RocketTransactionResult result) {
        if (!current(entry) || entry.stage() != Stage.RESTORING) { return; }
        try {
            entry.code = result.code();
            entry.result = result;
            Outcome outcome = RocketLandedDisassemblyRules.classify(result.success(), result.code(),
                    result.changedBlocks(), result.rolledBackBlocks(), !result.success()
                            && (!entry.request.requiresNativeRollbackProof() || entry.request.rollbackCertified())
                            && entry.request.matches());
            switch (outcome) {
                case SUCCESS -> {
                    report(entry);
                    end(entry);
                    entry.port.probe("K5", entry.record);
                }
                case CLEAN_ROLLBACK -> {
                    entry.progress.certifyCleanRollback();
                    writeBack(entry);
                }
                case UNCERTAIN -> {
                    notRestored(entry, "rollback_failed");
                    end(entry);
                    report(entry);
                }
            }
        } catch (RuntimeException failure) {
            restorationFailed(entry);
        } catch (Error fatal) {
            // A late callback cannot leave a RAM-only owner alive after its executor releases the claim.
            end(entry);
            throw fatal;
        }
    }

    private void restorationFailed(Pending entry) {
        if (!entry.restorationStarted() || entry.progress.certified()) {
            writeBack(entry);
        } else if (current(entry) && entry.stage() == Stage.RESTORING) {
            // Mutation is uncertain, also after a SUCCESS whose notification threw: never write T back.
            notRestored(entry, "uncertain");
            end(entry);
            if (entry.result == null) {
                entry.code = RocketValidationCode.ROLLBACK_FAILED;
            }
            report(entry);
        }
    }

    /**
     * The removal's discarded callback (N1's four cases). Once the latch is set without a certified clean rollback it
     * only logs and ends P, which is all it does while an {@code Error} unwinds; otherwise it runs M3.
     */
    void removalDiscarded(Pending entry) {
        if (!current(entry) || entry.stage() == Stage.WRITE_BACK || entry.stage() == Stage.ENDED) {
            return;
        }
        if (entry.restorationStarted() && !entry.progress.certified()) {
            notRestored(entry, "uncertain");
            end(entry);
            return;
        }
        writeBack(entry);
    }

    /** M3, the only step that puts T back: a refused insert, a queued write-back, or a stop's final capture. */
    private void writeBack(Pending entry) {
        if (!current(entry) || !entry.progress.mayWriteBack()) {
            return;
        }
        // The failure code of a certified clean rollback, or REGION_BUSY when restoration never started.
        reportContained(entry);
        RocketTransferSavedData journal = entry.journal;
        RocketTransferRecord record = entry.record;
        Presence presence;
        try {
            presence = entry.request.presence(record);
        } catch (RuntimeException unavailable) {
            presence = Presence.CHANGED;
        }
        WriteBack decision;
        try {
            boolean operational = journal.operational();
            decision = RocketLandedDisassemblyRules.writeBack(
                    operational && journal.findByLogicalRocket(entry.logical()).isPresent(),
                    presence, operational,
                    operational && journal.find(record.transferId()).isPresent(),
                    operational && journal.entries().size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                    operational && overlaps(journal, record));
            if (decision == WriteBack.INSERT) {
                journal.validateWriteBack(record);
            }
        } catch (RuntimeException refused) {
            decision = journal.operational() ? WriteBack.FULL : WriteBack.JOURNAL;
        }
        if (decision != WriteBack.INSERT) {
            notRestored(entry, decision.name().toLowerCase(Locale.ROOT));
            end(entry);
            return;
        }
        // The write-back's reservation is taken before its put. The removal's own continuation was consumed before
        // this callback ran, so it is absent only while the journal refuses all work (a stop or an abort): T then
        // rides the final capture, and P ends here, as when the continuation is refused.
        try (var reservation = journal.reserve(entry.logical()).orElse(null)) {
            try {
                journal.put(record);
            } catch (RuntimeException refused) {
                notRestored(entry, "journal");
                end(entry);
                return;
            }
            entry.progress.beginWriteBack();
            if (reservation == null || !entry.port.queue(reservation, () -> writeBackAcknowledged(entry),
                    () -> writeBackDiscarded(entry))) {
                end(entry);
            }
        }
    }

    /**
     * The write-back's discarded callback. T is already in RAM, so ordinary recovery or the final capture owns it; P
     * only ends. It reaches no notifier, so nothing in it can throw (W92-R3).
     */
    private void writeBackDiscarded(Pending entry) {
        end(entry);
    }

    private boolean writeBackAcknowledged(Pending entry) {
        if (!current(entry) || entry.stage() != Stage.WRITE_BACK) {
            return true;
        }
        entry.port.probe("K4", entry.record);
        entry.port.restored(entry.record, entry.entity, entry.request.presence(entry.record) == Presence.MATCHES);
        end(entry);
        return true;
    }

    private static boolean overlaps(RocketTransferSavedData journal, RocketTransferRecord record) {
        RocketRegion region = RocketRegion.fromSnapshot(record.destinationSnapshot());
        return journal.entries().stream().anyMatch(other -> !other.transferId().equals(record.transferId())
                && RocketRegion.fromSnapshot(other.destinationSnapshot()).overlaps(region));
    }

    private static void report(Pending entry) {
        if (entry.reported) {
            return;
        }
        entry.reported = true;
        if (entry.retainedResult != null) { entry.request.finishedRetained(entry.retainedResult); }
        else { entry.request.finished(entry.code, entry.result); }
    }

    /**
     * M3's report. The journal runs discarded callbacks outside any handler (in {@code pollWriter} and while it stops or
     * aborts), so a notifier failure is logged once, as {@link #report} runs once per P, and M3 goes on (W92-R3).
     */
    private static void reportContained(Pending entry) {
        try {
            report(entry);
        } catch (RuntimeException failure) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_LANDED_DISASSEMBLY_REPORT_FAILED transfer={} logical={} entity={} code={} error={}",
                    entry.record.transferId(), entry.logical(), entry.entity, entry.code, failure.toString());
        }
    }

    private static void notRestored(Pending entry, String reason) {
        AdvancedRocketryCommunity.LOGGER.warn(
                "ARCE_LANDED_RESERVATION_NOT_RESTORED transfer={} logical={} entity={} reason={}",
                entry.record.transferId(), entry.logical(), entry.entity, reason);
    }

    private boolean current(Pending entry) {
        return pending.get(entry.logical()) == entry;
    }

    private void end(Pending entry) {
        entry.progress.end();
        pending.remove(entry.logical(), entry);
    }

    /**
     * Before the transfer tick's early return: at most one WARN per P and stage, the first time a failed or unknown
     * write is seen while P waits for its removal or its write-back. No line repeats.
     */
    void reportUnacknowledged() {
        for (Pending entry : List.copyOf(pending.values())) {
            var outcome = entry.journal.checkedWriteResult().outcome();
            if ((outcome == RocketTransferSavedData.WriteOutcome.NOT_COMMITTED
                    || outcome == RocketTransferSavedData.WriteOutcome.UNKNOWN) && entry.progress.warnOnce()) {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_LANDED_DISASSEMBLY_UNACKNOWLEDGED logical={} stage={}",
                        entry.logical(), entry.stage() == Stage.RELEASED ? "removal" : "write_back");
            }
        }
    }

    /** The destination regions that P still reserves for new flights while T is absent from RAM (W85-R6, N2). */
    List<RocketRegion> reservations(RocketTransferSavedData journal) {
        var regions = new ArrayList<RocketRegion>();
        for (Pending entry : pending.values()) {
            if (holds(entry, journal)) {
                regions.add(RocketRegion.fromSnapshot(entry.record.destinationSnapshot()));
            }
        }
        return List.copyOf(regions);
    }

    /** The journal slots that P still holds while T is absent from RAM. */
    int heldSlots(RocketTransferSavedData journal) {
        return (int) pending.values().stream().filter(entry -> holds(entry, journal)).count();
    }

    private static boolean holds(Pending entry, RocketTransferSavedData journal) {
        return entry.progress.holdsReservation() && journal.find(entry.record.transferId()).isEmpty();
    }

    void clear() {
        pending.values().forEach(entry -> entry.progress.end());
        pending.clear();
    }
}
