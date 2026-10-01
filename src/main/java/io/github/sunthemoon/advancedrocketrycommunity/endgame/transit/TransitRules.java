package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * ADR-054 section 11 as pure decisions: the source reconciliation rows, the destination rows of ADR-051 section 7
 * with the incoming gate, pruning, removal settlement (section 9.1), retirement without live state, redirects, the
 * registration freeze and an operator resolve. The ledger, the endpoints and the reference-model tests all ask these
 * functions, so the tests that enumerate the C10 reference models' crash cuts describe the production decisions.
 */
public final class TransitRules {
    private TransitRules() {
    }

    /** What one record looks like from one endpoint, at one moment. */
    public record RecordFacts(TransitRecord.State state, boolean destinedHere, boolean paidHere, boolean durable,
                              boolean acknowledged, boolean ackDurable) {
        public RecordFacts {
            Objects.requireNonNull(state, "state");
            if (acknowledged && state != TransitRecord.State.CLAIMED) {
                throw new IllegalArgumentException("Only a claimed record is acknowledged");
            }
            if (ackDurable && !acknowledged) {
                throw new IllegalArgumentException("An acknowledgement is durable only once it exists");
            }
        }

        /** Claimed at this endpoint and not yet acknowledged. */
        public boolean claimedHere() {
            return state == TransitRecord.State.CLAIMED && paidHere && !acknowledged;
        }
    }

    // ---- Source rows ----------------------------------------------------------------------------------------

    public enum SourceStep {
        /** No record and {@code seq <= dispatched_through}: delivered and pruned, or purged; audit the drop. */
        STALE_DROP,
        /** Persisted, aged and the source's lowest entry, above {@code dispatched_through}: create the record. */
        REGISTER,
        /** The entry's record is durable: drop the entry. */
        RELEASE,
        /** Unpersisted, not aged, not the lowest entry, or a record that is not yet durable. */
        WAIT
    }

    /**
     * One outbox entry at a live source. {@code lowestSeq} is the lowest seq in the outbox, as in the reference model:
     * an entry that still waits for its record to become durable keeps later entries from registering.
     */
    public static SourceStep sourceStep(long seq, long lowestSeq, boolean persistedAndAged, long dispatchedThrough,
                                        boolean recordPresent, boolean recordDurable) {
        if (!recordPresent && seq <= dispatchedThrough) {
            return SourceStep.STALE_DROP;
        }
        if (!recordPresent && persistedAndAged && seq == lowestSeq && seq > dispatchedThrough) {
            return SourceStep.REGISTER;
        }
        if (recordPresent && recordDurable) {
            return SourceStep.RELEASE;
        }
        return SourceStep.WAIT;
    }

    /** A registration above {@code dispatched_through + 1}: the ledger lost registrations ({@code SEQUENCE_GAP}). */
    public static boolean sequenceGap(long seq, long dispatchedThrough) {
        return seq > dispatchedThrough + 1;
    }

    /** The source's chunk is older than the ledger ({@code SOURCE_ROLLBACK}): {@code next_seq} must move past it. */
    public static boolean rollback(long nextSeq, long dispatchedThrough) {
        return nextSeq <= dispatchedThrough;
    }

    /** Escrow at a source: no pending rollback, a free outbox slot and a durable registration (review R3-H1). */
    public static boolean mayEscrow(boolean rollbackPending, boolean outboxFree, boolean registrationDurable) {
        return !rollbackPending && outboxFree && registrationDurable;
    }

    // ---- Destination rows -----------------------------------------------------------------------------------

    /** Claim: an arrived, durable record for this endpoint, no receipt for it here, a durable registration. */
    public static boolean claims(RecordFacts record, boolean receiptHere, boolean registrationDurable) {
        return record.state() == TransitRecord.State.ARRIVED && record.destinedHere() && record.durable()
                && !receiptHere && registrationDurable;
    }

    /** The ledger is behind a claim this endpoint made ({@code CLAIM_RECOVERED}): claimed here with no items. */
    public static boolean recovers(RecordFacts record, boolean receiptHere) {
        return record.destinedHere() && receiptHere && (record.state() == TransitRecord.State.IN_TRANSIT
                || record.state() == TransitRecord.State.ARRIVED);
    }

    /** A persisted, aged receipt with nothing incoming acknowledges the claim. */
    public static boolean acknowledges(RecordFacts record, boolean receiptPersistedAndAged, boolean incomingHere) {
        return record.claimedHere() && receiptPersistedAndAged && !incomingHere;
    }

    /** An unacknowledged claim here without a receipt: the payload comes back into incoming, once. */
    public static boolean rematerializes(RecordFacts record, boolean receiptHere) {
        return record.claimedHere() && !receiptHere;
    }

    /**
     * The incoming gate: a persisted, aged incoming payload moves into the receive buffer when its record is claimed
     * here (and is acknowledged in the same tick), acknowledged here, or gone (the chunk is behind its own move).
     */
    public static boolean moves(boolean incomingPersistedAndAged, @Nullable RecordFacts record) {
        return incomingPersistedAndAged && (record == null || record.claimedHere()
                || record.acknowledged() && record.paidHere());
    }

    /** A receipt goes once its record is gone or acknowledged durably. */
    public static boolean dropsReceipt(@Nullable RecordFacts record) {
        return record == null || record.acknowledged() && record.ackDurable();
    }

    /**
     * Pruning (review R3-L6): a durably acknowledged record goes once its paid endpoint's chunk, saved at least 40
     * ticks after the acknowledgement, no longer holds the payload as incoming; until then it is a stub.
     */
    public static boolean prunes(RecordFacts record, boolean paidEndpointChunkHoldsIncoming) {
        return record.acknowledged() && record.ackDurable() && !paidEndpointChunkHoldsIncoming;
    }

    // ---- Removal, retirement, redirect and resolve -----------------------------------------------------------

    public enum Settlement {
        /** The payload moved to the receive buffer: the record becomes claimed and acknowledged. */
        MOVED,
        /** The payload is still incoming here, or was never materialized: the record returns to arrived. */
        INCOMING,
        /** Nothing of this record is settled here. */
        UNCHANGED
    }

    /** Section 9.1: an unacknowledged record for this endpoint, settled from its live state at removal. */
    public static Settlement settleRemoval(RecordFacts record, boolean receiptHere, boolean incomingHere) {
        if (!record.destinedHere() || record.acknowledged()) {
            return Settlement.UNCHANGED;
        }
        return receiptHere && !incomingHere ? Settlement.MOVED : Settlement.INCOMING;
    }

    /** Retirement without live state ({@code MISSING}, {@code endpoint retire}): unacknowledged claims return. */
    public static boolean returnsOnRetirement(RecordFacts record) {
        return record.destinedHere() && !record.acknowledged();
    }

    /** A redirect needs an in-transit or arrived record whose destination's index removal is durable. */
    public static boolean redirectable(RecordFacts record, boolean destinationRemovalDurable) {
        return (record.state() == TransitRecord.State.IN_TRANSIT || record.state() == TransitRecord.State.ARRIVED)
                && destinationRemovalDurable;
    }

    /**
     * Registration from a persisted tag (review R3-H1): a retired ID never registers, and an unknown ID whose tag
     * holds outbox entries, incoming payloads or receipts is frozen instead.
     */
    public static boolean registers(boolean retired, boolean tagHoldsContents) {
        return !retired && !tagHoldsContents;
    }

    public enum Resolve {
        /** The payload is this endpoint's own: it joins the receive buffer (and the claim is acknowledged). */
        TO_RECEIVE_BUFFER,
        /** Nothing proves it is this endpoint's: destroyed with an audit line. */
        DESTROY
    }

    /** An operator or owner resolve of a frozen incoming payload (section 9). */
    public static Resolve resolveIncoming(@Nullable RecordFacts record) {
        return record != null && record.state() == TransitRecord.State.CLAIMED && record.paidHere()
                ? Resolve.TO_RECEIVE_BUFFER : Resolve.DESTROY;
    }

    /** A frozen outbox entry goes back to the input buffer only if its tombstone proves it was never registered. */
    public static boolean resolveOutboxToInput(boolean tombstonePresent, long seq, long tombstoneDispatchedThrough) {
        return tombstonePresent && seq > tombstoneDispatchedThrough;
    }
}
