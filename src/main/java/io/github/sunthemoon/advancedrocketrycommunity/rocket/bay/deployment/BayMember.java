package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One bay of a claim and its state machine (W27A-DESIGN section 4, D0 to D2 for W27d1). A completion only marks state;
 * the coordinator's queued steps do the writing. Pure Java, owner-thread only; it is never persisted.
 */
public final class BayMember {
    public enum State {
        /** D0 has not reached this bay yet. */
        CANDIDATE,
        /** D1: the INTENT or re-armed receipt is queued or captured; its exact acknowledgement follows. */
        INTENT_WRITE,
        /** INTENT is exactly acknowledged (or loaded at restart); the outcome step is queued. */
        INTENT,
        /** The REJECTED outcome is queued or captured. */
        REJECT_WRITE,
        /** The REPAIR outcome (a restart row) is queued or captured. */
        REPAIR_WRITE,
        /** REJECTED is exactly acknowledged. */
        REJECTED,
        /** Refused at runtime (D0, or a definite D1 failure); nothing durable changed. */
        REFUSED,
        /** A state this slice never resolves online; it waits unchanged for a later slice or offline repair. */
        HELD
    }

    private final RocketPosition bay;
    @Nullable private final UUID satellite;
    private State state;
    private String reason = "NONE";
    @Nullable private BayReceipt receipt;

    private BayMember(RocketPosition bay, @Nullable UUID satellite, State state, @Nullable BayReceipt receipt) {
        this.bay = Objects.requireNonNull(bay, "bay"); this.satellite = satellite; this.state = state; this.receipt = receipt;
    }

    /** A bay holding a package at the checkpoint; {@code satellite} is null when the package has no readable identity. */
    public static BayMember candidate(RocketPosition bay, @Nullable UUID satellite) {
        return new BayMember(bay, satellite, State.CANDIDATE, null);
    }

    /** A durable INTENT found at restart, before its row is decided. */
    public static BayMember restartIntent(BayReceipt intent) {
        if (intent.phase() != BayReceipt.Phase.INTENT || intent.released()) {
            throw new IllegalArgumentException("A restart member starts from a non-released INTENT");
        }
        return new BayMember(intent.bay(), intent.satellite(), State.INTENT, intent);
    }

    /** A durable receipt that waits unchanged in this session (REGISTERED, REPAIR or released). */
    public static BayMember restartHeld(BayReceipt receipt, String reason) {
        var member = new BayMember(receipt.bay(), receipt.satellite(), State.HELD, receipt);
        member.reason = reason;
        return member;
    }

    public RocketPosition bay() { return bay; }
    public Optional<UUID> satellite() { return Optional.ofNullable(satellite); }
    public State state() { return state; }
    public String reason() { return reason; }
    public Optional<BayReceipt> receipt() { return Optional.ofNullable(receipt); }
    /** The member's last durable outcome is known and nothing about it is pending. */
    public boolean resolved() { return state == State.REJECTED || state == State.REFUSED; }
    /** A receipt write of this member is queued, captured or unknown, or its outcome step is still to run. */
    public boolean awaiting() {
        return state == State.INTENT_WRITE || state == State.INTENT || state == State.REJECT_WRITE || state == State.REPAIR_WRITE;
    }

    /** D0 refused it, or its INTENT write failed definitely (the store restored the prior receipt). */
    public void refuse(String nextReason) {
        require(state == State.CANDIDATE || state == State.INTENT_WRITE, "refuse");
        state = State.REFUSED; reason = Objects.requireNonNull(nextReason);
    }

    /** D1: the INTENT (or re-armed) receipt was queued through the store's admission. */
    public void admitted(BayReceipt intent) {
        require(state == State.CANDIDATE && intent.phase() == BayReceipt.Phase.INTENT, "admit");
        state = State.INTENT_WRITE; receipt = intent; reason = intent.reason();
    }

    public void intentAcknowledged() { require(state == State.INTENT_WRITE, "acknowledge INTENT"); state = State.INTENT; }

    /** The outcome step queued REJECTED or, at restart, REPAIR, replacing this member's INTENT. */
    public void outcomeQueued(BayReceipt outcome) {
        require(state == State.INTENT && receipt != null && outcome.op().equals(receipt.op()), "queue an outcome");
        if (outcome.phase() != BayReceipt.Phase.REJECTED && outcome.phase() != BayReceipt.Phase.REPAIR) {
            throw new IllegalArgumentException("An outcome is REJECTED or REPAIR");
        }
        state = outcome.phase() == BayReceipt.Phase.REJECTED ? State.REJECT_WRITE : State.REPAIR_WRITE;
        receipt = outcome; reason = outcome.reason();
    }

    public void outcomeAcknowledged() {
        require(state == State.REJECT_WRITE || state == State.REPAIR_WRITE, "acknowledge an outcome");
        state = state == State.REJECT_WRITE ? State.REJECTED : State.HELD;
    }

    /** A definitely failed outcome write: the store restored INTENT, and the outcome step runs again. */
    public void outcomeFailed(BayReceipt restored) {
        require(state == State.REJECT_WRITE || state == State.REPAIR_WRITE, "fail an outcome");
        state = State.INTENT; receipt = restored; reason = restored.reason();
    }

    /** A restart row that waits unchanged at runtime with its reason, writing nothing. */
    public void hold(String nextReason) {
        require(state == State.INTENT || state == State.CANDIDATE, "hold");
        state = State.HELD; reason = Objects.requireNonNull(nextReason);
    }

    private void require(boolean valid, String action) {
        if (!valid) { throw new IllegalStateException("Bay member cannot " + action + " from " + state); }
    }
}
