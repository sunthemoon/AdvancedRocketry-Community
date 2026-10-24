package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.Objects;

/** C0-C5 control flow. This object never exposes resources, performs IO, or interprets native payloads. */
public final class SealedDisassembly {
    public enum Cell { EMPTY, OWNED, OTHER, UNLOADED }
    public enum Entity { MATCHING, ABSENT, OTHER, UNLOADED }
    public enum Gate { READY, UNLOADED, SAVE_OFF, CONFLICT }
    public enum Goal { PREPARE, SEALED, COMPLETE, ROLLBACK }
    public enum Phase { PREFLIGHT, PREPARE, RETAIN, STAGE, MATCH, PROVE, ELECT, RETIRE_ENTITY, COMPLETE,
        SELECT_RECOVERY, ROLLBACK, PROVE_ROLLBACK, REMOVE_U, DONE }
    /** REJECTED grants no M3 permission; only ROLLBACK_PROVED certifies native rollback plus checked U absence. */
    public enum Result { SEALED, ROLLBACK_PROVED, REJECTED }
    public enum Reason { UNLOADED, SAVE_OFF, CONFLICT, MEMBER_CONFLICT, ENTITY_CONFLICT, PREDECESSOR_UNPROVED,
        JOURNAL_UNPROVED, PROOF_PENDING, PLACEMENT_FAILED, REMOVAL_FAILED }

    public interface Port {
        long now();
        Gate gate();
        /** Original actor/consent/snapshot check; never a refreshed request or a new timeout. */
        boolean requestCurrent();
        /** Actual predecessor/no-T journal proof, not a fabricated transfer record. */
        boolean predecessorAbsent();
        Cell inspect(int index);
        boolean stage(int index);
        boolean erase(int index);
        Entity entity();
        boolean discard();
        boolean soleSurvivor();
        /** Remove one exact redundant copy, never the deterministic survivor or a conflicting payload. */
        boolean discardDuplicate();
        /** Idempotent exact desired entry writes; conflicting authority must be refused, never overwritten. */
        void retain();
        boolean retainedAcknowledged();
        void elect();
        boolean electedAcknowledged();
        void removeRecord();
        boolean absentAcknowledged();
        void checkpoint();
        /** Current full-slot native proofs, including identity/census checks, not merely RAM contents. */
        boolean proofCurrent(Goal goal);
        /** Enqueue bounded proof work in the shared ordinary quota; no hidden synchronous world scan. */
        void requestProof(Goal goal);
        void held(Phase phase, Reason reason);
    }

    private final Thread owner = Thread.currentThread();
    private final int members;
    private final Port port;
    private final long requestedAt;
    private Phase phase = Phase.PREFLIGHT;
    private Result result;
    private int cursor;
    private boolean recovering, retentionSubmitted, restorationStarted, electionSubmitted, removalSubmitted, rollback, capacityPrepared;
    private boolean stepping;
    private long notBefore = Long.MIN_VALUE;

    public SealedDisassembly(int members, Port port) {
        if (members < 1 || members > RocketLimits.MAX_BLOCKS) { throw new IllegalArgumentException("Invalid sealed footprint"); }
        this.members = members; this.port = Objects.requireNonNull(port); requestedAt = port.now();
    }
    public static SealedDisassembly recover(int members, Port port, boolean elected) {
        var work = new SealedDisassembly(members, port);
        work.recovering = true; work.retentionSubmitted = true; work.restorationStarted = true;
        work.electionSubmitted = elected;
        work.phase = elected ? Phase.STAGE : Phase.SELECT_RECOVERY;
        return work;
    }
    public Result result() { return result; }
    public Phase phase() { return phase; }
    public boolean restorationStarted() { return restorationStarted; }
    public boolean electionSubmitted() { return electionSubmitted; }
    public boolean removalSubmitted() { return removalSubmitted; }

    /** One bounded step. A manager may interleave entries, but every call spends the same server-tick allowance. */
    public boolean step(SealedWorkBudget budget) {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed disassembly requires its owner thread"); }
        if (stepping || result != null || !budget.take()) { return false; }
        stepping = true;
        try {
            if (!recovering && !restorationStarted && !electionSubmitted
                    && (!port.requestCurrent() || port.now() - requestedAt > 200)) {
                if (!retentionSubmitted) { finish(Result.REJECTED); return true; }
                recovering = true;
                phase = Phase.SELECT_RECOVERY; cursor = 0;
            }
            if (port.now() < notBefore) { return false; }
            Gate gate = port.gate();
            if (gate != Gate.READY) { return hold(Reason.valueOf(gate.name())); }
            return switch (phase) {
                case PREFLIGHT -> preflight();
                case PREPARE -> prepare();
                case RETAIN -> retain();
                case STAGE -> stage(budget);
                case MATCH -> match();
                case PROVE -> prove();
                case ELECT -> elect();
                case RETIRE_ENTITY -> retire(budget);
                case COMPLETE -> complete();
                case SELECT_RECOVERY -> select();
                case ROLLBACK -> rollback(budget);
                case PROVE_ROLLBACK -> proveRollback(budget);
                case REMOVE_U -> remove();
                case DONE -> false;
            };
        } finally { stepping = false; }
    }

    private boolean preflight() {
        if (cursor == members) { phase = Phase.PREPARE; cursor = 0; return true; }
        Cell cell = port.inspect(cursor);
        if (cell == Cell.UNLOADED) { return hold(Reason.UNLOADED); }
        if (cell != Cell.EMPTY) { finish(Result.REJECTED); return true; }
        cursor++; return true;
    }
    private boolean prepare() {
        if (!port.predecessorAbsent()) { return hold(Reason.PREDECESSOR_UNPROVED); }
        if (!proof(Goal.PREPARE)) { return false; }
        capacityPrepared = true; phase = Phase.RETAIN; return true;
    }
    private boolean retain() {
        retentionSubmitted = true;
        port.retain();
        if (!presence()) { return false; }
        phase = Phase.STAGE; return true;
    }
    private boolean stage(SealedWorkBudget budget) {
        if (!presence()) { return false; }
        if (!port.predecessorAbsent()) { return hold(Reason.PREDECESSOR_UNPROVED); }
        if (!capacityPrepared || !restorationStarted) {
            if (!proof(Goal.PREPARE)) { return false; }
            capacityPrepared = true;
        }
        if (cursor == members) { phase = Phase.MATCH; cursor = 0; return true; }
        Cell cell = port.inspect(cursor);
        if (cell == Cell.UNLOADED) { return hold(Reason.UNLOADED); }
        if (cell == Cell.OTHER) { return hold(Reason.MEMBER_CONFLICT); }
        if (cell == Cell.EMPTY) {
            if (!budget.take()) { return false; }
            restorationStarted = true;
            if (!port.stage(cursor)) {
                if (!electionSubmitted) { phase = Phase.SELECT_RECOVERY; cursor = 0; }
                return hold(Reason.PLACEMENT_FAILED);
            }
        }
        cursor++; return true;
    }
    private boolean match() {
        if (!presence()) { return false; }
        if (cursor == members) { phase = Phase.PROVE; cursor = 0; return true; }
        Cell cell = port.inspect(cursor);
        if (cell == Cell.UNLOADED) { return hold(Reason.UNLOADED); }
        if (cell == Cell.OTHER) { return hold(Reason.MEMBER_CONFLICT); }
        if (cell == Cell.EMPTY) { phase = Phase.STAGE; cursor = 0; return true; }
        cursor++; return true;
    }
    private boolean prove() {
        if (!presence() || !proof(Goal.SEALED)) { return false; }
        phase = electionSubmitted ? Phase.RETIRE_ENTITY : Phase.ELECT; return true;
    }
    private boolean elect() {
        if (!proof(Goal.SEALED)) { return false; }
        Entity entity = port.entity();
        if (entity == Entity.UNLOADED) { return hold(Reason.UNLOADED); }
        if (entity == Entity.OTHER) { return hold(Reason.ENTITY_CONFLICT); }
        // Submission, not its acknowledgement, is the irreversible live-process boundary.
        electionSubmitted = true;
        port.elect();
        if (!presence()) { return false; }
        phase = Phase.RETIRE_ENTITY; return true;
    }
    private boolean retire(SealedWorkBudget budget) {
        // Once checked removal is submitted, the retained continuation still owns the elected C proof.
        // Never reinsert U merely to retire an exact copy that returned during its acknowledgement wait.
        if ((!removalSubmitted && !presence()) || !proof(Goal.SEALED)) { return false; }
        Entity entity = port.entity();
        if (entity == Entity.UNLOADED) { return hold(Reason.UNLOADED); }
        if (entity == Entity.OTHER) { return hold(Reason.ENTITY_CONFLICT); }
        if (entity == Entity.MATCHING) {
            if (!budget.take()) { return false; }
            if (!port.discard()) { return hold(Reason.REMOVAL_FAILED); }
            return true;
        }
        phase = Phase.COMPLETE; return true;
    }
    private boolean complete() {
        if (port.entity() == Entity.MATCHING) { phase = Phase.RETIRE_ENTITY; return true; }
        if (!proof(Goal.COMPLETE)) { return false; }
        phase = Phase.REMOVE_U; return true;
    }
    private boolean select() {
        if (electionSubmitted) { phase = Phase.STAGE; cursor = 0; return true; }
        Entity entity = port.entity();
        if (entity == Entity.UNLOADED) { return hold(Reason.UNLOADED); }
        if (entity == Entity.OTHER) { return hold(Reason.ENTITY_CONFLICT); }
        rollback = entity == Entity.MATCHING;
        cursor = 0; phase = rollback ? Phase.ROLLBACK : Phase.STAGE;
        return true;
    }
    private boolean rollback(SealedWorkBudget budget) {
        // A refused initial retention may leave no U at all. No C was placed in that case;
        // checked absence plus the native rollback proof is still required before releasing D.
        if (!(!restorationStarted && port.absentAcknowledged()) && !presence()) { return false; }
        Entity entity = port.entity();
        if (entity == Entity.UNLOADED) { return hold(Reason.UNLOADED); }
        if (entity == Entity.OTHER) { return hold(Reason.ENTITY_CONFLICT); }
        if (entity == Entity.ABSENT) { rollback = false; phase = Phase.STAGE; cursor = 0; return true; }
        if (cursor == members) { phase = Phase.PROVE_ROLLBACK; cursor = 0; return true; }
        if (!budget.take()) { return false; }
        Cell cell = port.inspect(cursor);
        if (cell == Cell.UNLOADED) { return hold(Reason.UNLOADED); }
        if (cell == Cell.OTHER) { return hold(Reason.MEMBER_CONFLICT); }
        if (cell == Cell.OWNED) {
            if (!budget.take()) { return false; }
            if (!port.erase(cursor)) { return hold(Reason.REMOVAL_FAILED); }
        }
        cursor++; return true;
    }
    private boolean proveRollback(SealedWorkBudget budget) {
        if (!port.soleSurvivor()) {
            if (!budget.take()) { return false; }
            if (port.entity() == Entity.MATCHING && port.discardDuplicate()) { return true; }
            return hold(Reason.ENTITY_CONFLICT);
        }
        if (!proof(Goal.ROLLBACK)) { return false; }
        phase = Phase.REMOVE_U; return true;
    }
    private boolean remove() {
        if (rollback && !port.soleSurvivor()) { phase = Phase.PROVE_ROLLBACK; return true; }
        if (!rollback && port.entity() == Entity.MATCHING) { phase = Phase.RETIRE_ENTITY; return true; }
        if (!proof(rollback ? Goal.ROLLBACK : Goal.COMPLETE)) { return false; }
        removalSubmitted = true;
        port.removeRecord();
        if (!port.absentAcknowledged()) {
            port.checkpoint(); return hold(Reason.JOURNAL_UNPROVED);
        }
        finish(rollback ? Result.ROLLBACK_PROVED : Result.SEALED); return true;
    }
    private boolean presence() {
        if (electionSubmitted ? port.electedAcknowledged() : port.retainedAcknowledged()) { return true; }
        port.checkpoint(); return hold(Reason.JOURNAL_UNPROVED);
    }
    private boolean proof(Goal goal) {
        if (port.proofCurrent(goal)) { return true; }
        port.requestProof(goal); return hold(Reason.PROOF_PENDING);
    }
    private boolean hold(Reason reason) {
        port.held(phase, reason);
        if (reason == Reason.UNLOADED || reason == Reason.SAVE_OFF || reason == Reason.PREDECESSOR_UNPROVED) {
            notBefore = port.now() + 20;
        }
        else if (reason != Reason.JOURNAL_UNPROVED && reason != Reason.PROOF_PENDING) { notBefore = port.now() + 200; }
        return false;
    }
    private void finish(Result result) { this.result = result; phase = Phase.DONE; }
}
