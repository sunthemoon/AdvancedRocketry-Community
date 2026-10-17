package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Presence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Progress;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Stage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.WriteBack;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
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
        /** E is alive and loaded and still equals S. */
        boolean matches();

        /** M3's view of E for the record that would be written back. */
        Presence presence(RocketTransferRecord record);

        /** Today's one-tick v0.5 transaction. Called at most once, with the latch already set. */
        RocketTransactionResult restore();

        /** Called at most once per P: the result's code, or REGION_BUSY when M2 did not restore (result null). */
        void finished(RocketValidationCode code, RocketTransactionResult result);
    }

    /** The service side: clock, bay gate, the journal continuation, the session sets, audits and the native probe. */
    interface Port {
        long now();

        boolean bayAllowsRemoval(RocketTransferRecord record);

        boolean queue(UUID logical, BooleanSupplier action, Runnable discarded);

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
        private boolean reported;

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
        if (!journal.operational()) {
            return RocketValidationCode.REGION_BUSY;
        }
        UUID logical = rocket.assemblyTransactionId().orElseThrow();
        if (pending.containsKey(logical)) {
            return RocketValidationCode.DISASSEMBLY_PENDING;
        }
        RocketTransferRecord record = journal.findByLogicalRocket(logical).orElse(null);
        Port port = ports.apply(level.getServer(), journal);
        var decision = RocketLandedDisassemblyRules.admit(record != null, journal.resourceMutationUnproved(logical),
                () -> record.schemaVersion() == RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION
                        && record.nativeTransfer().isEmpty()
                        && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record),
                () -> port.bayAllowsRemoval(record), journal.canQueueEffect(), hasCapacity(), preflight);
        return decision.admission() == RocketLandedDisassemblyRules.Admission.RELEASE
                ? release(journal, record, rocket.getUUID(), request, port) : decision.code();
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
            Port port = ports.apply(level.getServer(), journal);
            if (!port.bayAllowsRemoval(record)) { return false; }
            journal.remove(record.transferId());
            port.queue(record.logicalRocketId(), () -> {
                if (port.bayAllowsRemoval(record)) { port.released(record, rocket.getUUID()); }
                return true;
            }, () -> { });
            return false;
        }
        return !journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow());
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

    boolean hasCapacity() {
        return pending.size() < MAX_PENDING;
    }

    /**
     * M1, after M0 admitted the release: record P, remove T, then queue the removal's continuation. M0 checked that the
     * continuation can be queued, so the refusal branch is unreachable; it restores the identical T in RAM before any
     * capture can have seen its absence.
     */
    RocketValidationCode release(RocketTransferSavedData journal, RocketTransferRecord record, UUID entity,
                                 Request request, Port port) {
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(record, "record");
        if (pending.containsKey(record.logicalRocketId()) || !hasCapacity()) {
            return RocketValidationCode.REGION_BUSY;
        }
        Pending entry = new Pending(record, Objects.requireNonNull(entity, "entity"), port.now(),
                Objects.requireNonNull(request, "request"), Objects.requireNonNull(port, "port"), journal);
        pending.put(entry.logical(), entry);
        journal.remove(record.transferId());
        if (!port.queue(entry.logical(), () -> removalAcknowledged(entry), () -> removalDiscarded(entry))) {
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

    /** M2, inside the removal's exact-acknowledgement continuation. Every handled case returns true (N1). */
    private boolean removalAcknowledged(Pending entry) {
        if (!current(entry) || entry.stage() != Stage.RELEASED) {
            return true;
        }
        try {
            entry.port.probe("K2", entry.record);
            entry.port.released(entry.record, entry.entity);
            if (!RocketLandedDisassemblyRules.restore(entry.requestedAt, entry.port.now(), entry.request.matches(),
                    entry.port.bayAllowsRemoval(entry.record))) {
                writeBack(entry);
                return true;
            }
            entry.progress.startRestoration();
            RocketTransactionResult result = entry.request.restore();
            entry.code = result.code();
            entry.result = result;
            Outcome outcome = RocketLandedDisassemblyRules.classify(result.success(), result.code(),
                    result.changedBlocks(), result.rolledBackBlocks(), !result.success() && entry.request.matches());
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
        return true;
    }

    /**
     * The removal's discarded callback (N1's four cases). Once the latch is set without a certified clean rollback it
     * only logs and ends P, which is all it does while an {@code Error} unwinds; otherwise it runs M3.
     */
    private void removalDiscarded(Pending entry) {
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
        report(entry);
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
        try {
            journal.put(record);
        } catch (RuntimeException refused) {
            notRestored(entry, "journal");
            end(entry);
            return;
        }
        entry.progress.beginWriteBack();
        // A stop or an abort refuses the continuation; T then rides the final capture, and P ends here.
        if (!entry.port.queue(entry.logical(), () -> writeBackAcknowledged(entry), () -> end(entry))) {
            end(entry);
        }
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
        entry.request.finished(entry.code, entry.result);
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
