package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * W85 Design M (P1-F8), the decisions that need no Minecraft state: M0's admission and status branch, M2's restore
 * condition and result classification, M3's write-back refusal reason, and P's one-way stages. See
 * {@code docs/work/v1.8.0-root-queue/records/W85-DESIGN.md} section 4.
 */
final class RocketLandedDisassemblyRules {
    private RocketLandedDisassemblyRules() { }

    enum Admission { PLAIN, RELEASE, REFUSE }

    record Decision(Admission admission, RocketValidationCode code) {
        Decision {
            Objects.requireNonNull(admission, "admission");
            Objects.requireNonNull(code, "code");
        }
    }

    /**
     * M0 after {@code validateRocket} passed and no P exists. Without T the plain one-tick path runs, as today. With T,
     * every check must hold, otherwise the request answers the failing code and nothing changes. Only RELEASE mutates.
     */
    static Decision admit(boolean recordPresent, boolean unproved, BooleanSupplier legacyLandedAuthority,
                          BooleanSupplier bayAllowsRemoval, boolean canQueueEffect, boolean pendingCapacity,
                          Supplier<Optional<RocketValidationCode>> preflight) {
        if (!recordPresent) {
            return unproved ? refuse(RocketValidationCode.REGION_BUSY)
                    : new Decision(Admission.PLAIN, RocketValidationCode.SUCCESS);
        }
        if (!legacyLandedAuthority.getAsBoolean() || !bayAllowsRemoval.getAsBoolean() || !canQueueEffect
                || !pendingCapacity) {
            return refuse(RocketValidationCode.REGION_BUSY);
        }
        Optional<RocketValidationCode> refused = preflight.get();
        return refused.map(RocketLandedDisassemblyRules::refuse)
                .orElseGet(() -> new Decision(Admission.RELEASE, RocketValidationCode.DISASSEMBLY_PENDING));
    }

    private static Decision refuse(RocketValidationCode code) {
        return new Decision(Admission.REFUSE, code);
    }

    /**
     * M0's record clause: a record without a native transfer that M3 can put back unchanged. Besides schema 2 it admits
     * a settled schema-1 record whose target migration is blocked; its write-back is the identical schema-1 record, so no
     * format changes (W92-R4, Root's decision in TASK W92F). Governed record 3 stays excluded (G9).
     */
    static boolean landedRecordAdmitted(int schemaVersion, boolean nativeTransfer) {
        return !nativeTransfer
                && (schemaVersion == 1 || schemaVersion == RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION);
    }

    /**
     * N6: with a P, a request runs the actor, range, identity and owner-or-permission checks in that order and then
     * only reports DISASSEMBLY_PENDING. It mutates nothing, issues no offer and starts nothing.
     */
    static RocketValidationCode status(boolean actorValid, boolean inRange, RocketValidationCode identity,
                                       boolean authorized) {
        if (!actorValid) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        if (!inRange) {
            return RocketValidationCode.OUT_OF_RANGE;
        }
        if (identity != RocketValidationCode.SUCCESS) {
            return identity;
        }
        return authorized ? RocketValidationCode.DISASSEMBLY_PENDING : RocketValidationCode.UNAUTHORIZED;
    }

    /**
     * M2 step 2, checked when the removal's continuation runs: at most the confirmation lifetime after the request, E
     * still equal to S, an operational v0.5 transaction journal with no pending recovery overlapping E (W92-R2), and
     * the bay gate still allowing the removal. It is not a timer and bounds nothing.
     */
    static boolean restore(long requestedAt, long now, boolean entityMatches, boolean transactionsClear,
                           boolean bayAllowsRemoval) {
        return now >= requestedAt && now - requestedAt <= RocketDisassemblyConfirmations.LIFETIME_TICKS
                && entityMatches && transactionsClear && bayAllowsRemoval;
    }

    enum Outcome { SUCCESS, CLEAN_ROLLBACK, UNCERTAIN }

    /**
     * M2 step 4. The v0.5 rollback reports the original failure code with its counts, so a certified clean rollback is
     * a failure other than ROLLBACK_FAILED whose rolled-back count equals its changed count, with E still equal to S.
     */
    static Outcome classify(boolean success, RocketValidationCode code, int changedBlocks, int rolledBackBlocks,
                            boolean entityMatches) {
        if (success) {
            return Outcome.SUCCESS;
        }
        return code != RocketValidationCode.ROLLBACK_FAILED && changedBlocks == rolledBackBlocks && entityMatches
                ? Outcome.CLEAN_ROLLBACK : Outcome.UNCERTAIN;
    }

    /** M3's view of E: loaded and still T's landed authority, its entity section unloaded, or destroyed or altered. */
    enum Presence { MATCHES, UNLOADED, CHANGED }

    /** M3's result; every value other than INSERT is a refused insert whose WARN names it in lower case. */
    enum WriteBack { INSERT, JOURNAL, FULL, OVERLAP, MISMATCH }

    static WriteBack writeBack(boolean logicalInRam, Presence presence, boolean operational, boolean transferInRam,
                               boolean full, boolean overlap) {
        if (!operational) {
            return WriteBack.JOURNAL;
        }
        if (logicalInRam || transferInRam || presence == Presence.CHANGED) {
            return WriteBack.MISMATCH;
        }
        if (full) {
            return WriteBack.FULL;
        }
        return overlap ? WriteBack.OVERLAP : WriteBack.INSERT;
    }

    enum Stage { RELEASED, RESTORING, WRITE_BACK, ENDED }

    /**
     * P's stages only move forward. The restoration latch is set before {@code execute} is entered and is never
     * undone; once it is set, only a certified clean rollback may still write T back.
     */
    static final class Progress {
        private Stage stage = Stage.RELEASED;
        private boolean restorationStarted;
        private boolean certified;
        private final EnumSet<Stage> warned = EnumSet.noneOf(Stage.class);

        Stage stage() {
            return stage;
        }

        boolean restorationStarted() {
            return restorationStarted;
        }

        boolean certified() {
            return certified;
        }

        /** RELEASED to RESTORING, setting the one-way latch. */
        boolean startRestoration() {
            if (stage != Stage.RELEASED) {
                return false;
            }
            restorationStarted = true;
            stage = Stage.RESTORING;
            return true;
        }

        void certifyCleanRollback() {
            if (stage == Stage.RESTORING) {
                certified = true;
            }
        }

        boolean mayWriteBack() {
            return (stage == Stage.RELEASED || stage == Stage.RESTORING) && (!restorationStarted || certified);
        }

        boolean beginWriteBack() {
            if (!mayWriteBack()) {
                return false;
            }
            stage = Stage.WRITE_BACK;
            return true;
        }

        boolean end() {
            if (stage == Stage.ENDED) {
                return false;
            }
            stage = Stage.ENDED;
            return true;
        }

        /** P holds T's reservation and slot only while T is absent from RAM, never a second time in WRITE_BACK. */
        boolean holdsReservation() {
            return stage == Stage.RELEASED || stage == Stage.RESTORING;
        }

        /** At most one unacknowledged-write WARN per stage, and only for the two stages that wait for a write. */
        boolean warnOnce() {
            return (stage == Stage.RELEASED || stage == Stage.WRITE_BACK) && warned.add(stage);
        }
    }

    /**
     * S, P's snapshot of E (N5): the fields of the fueled confirmation's quote, but zero fuel is accepted. Consent stays
     * separate from S.
     */
    record Snapshot(UUID entityId, UUID ownerId, UUID snapshotId, String snapshotHash, RocketFlightData flight,
                    double x, double y, double z, String catalogSignature) {
        Snapshot {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(snapshotId, "snapshotId");
            Objects.requireNonNull(snapshotHash, "snapshotHash");
            Objects.requireNonNull(flight, "flight");
            if (catalogSignature == null || !catalogSignature.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid catalog signature");
            }
            if (!snapshotHash.matches("[0-9a-f]{64}") || flight.fuel().totalAmount() < 0L
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Invalid pending disassembly snapshot");
            }
        }

        /** A confirmed fueled request's taken quote must describe exactly this snapshot. */
        boolean consented(RocketDisassemblyConfirmations.Quote quote) {
            return quote.entityId().equals(entityId) && quote.ownerId().equals(ownerId)
                    && quote.snapshotId().equals(snapshotId) && quote.snapshotHash().equals(snapshotHash)
                    && quote.flight().equals(flight) && Double.compare(quote.x(), x) == 0
                    && Double.compare(quote.y(), y) == 0 && Double.compare(quote.z(), z) == 0
                    && quote.catalogSignature().equals(catalogSignature);
        }
    }
}
