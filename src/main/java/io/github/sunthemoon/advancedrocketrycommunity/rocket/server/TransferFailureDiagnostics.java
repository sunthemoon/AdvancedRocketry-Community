package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.UUID;

/** Server-thread-only scalar observations; never participates in flight decisions. */
final class TransferFailureDiagnostics {
    enum Branch {
        UNOBSERVED,
        PREPARED,
        LIVE_DISPATCH,
        SOURCE_UNAVAILABLE,
        SOURCE_STATE_UNEXPECTED,
        TRANSIT_NOT_DUE,
        DESTINATION_CHECK,
        WAIT_ENTITY_READY,
        DESTINATION_UNAVAILABLE,
        SPAWN_ATTEMPT,
        TICK_EXCEPTION
    }

    private UUID latestPrepared;
    private boolean entered;
    private boolean completed;
    private boolean dispatched;
    private int enteredTick;
    private int completedTick;
    private int dispatchTick;
    private Branch branch = Branch.UNOBSERVED;
    private int firstWaitTick;
    private int lastWaitTick;
    private int waitAttempts;
    private boolean waitDispatchCurrent;

    void prepared(UUID transferId) {
        latestPrepared = transferId;
        dispatched = false;
        branch = transferId == null ? Branch.UNOBSERVED : Branch.PREPARED;
        clearWaitHistory();
    }

    void enterTick(int tick) {
        entered = true;
        enteredTick = tick;
        waitDispatchCurrent = false;
    }

    void completeTick() {
        if (entered) {
            completed = true;
            completedTick = enteredTick;
        }
        waitDispatchCurrent = false;
    }

    void dispatch(UUID transferId) {
        if (tracks(transferId) && entered) {
            dispatched = true;
            dispatchTick = enteredTick;
            branch = Branch.LIVE_DISPATCH;
            waitDispatchCurrent = true;
        }
    }

    void branch(UUID transferId, Branch observed) {
        if (tracks(transferId)) {
            branch = observed == null ? Branch.UNOBSERVED : observed;
        }
    }

    void readinessWait(UUID transferId) {
        if (!tracks(transferId) || !entered || !dispatched || !waitDispatchCurrent
                || dispatchTick != enteredTick) {
            return;
        }
        branch = Branch.WAIT_ENTITY_READY;
        if (waitAttempts == 0) {
            firstWaitTick = enteredTick;
        }
        lastWaitTick = enteredTick;
        if (waitAttempts < Integer.MAX_VALUE) {
            waitAttempts++;
        }
    }

    String snapshot(UUID transferId, boolean active, boolean live, boolean settled) {
        boolean tracked = tracks(transferId);
        String classification = transferId == null ? "UNOBSERVED"
                : live ? settled ? "OVERLAP" : "LIVE" : settled ? "SETTLED" : "UNCLASSIFIED";
        return "installed=MANAGER flight_active=" + (active ? "YES" : "NO")
                + " classification=" + classification
                + " service_entered_tick=" + tick(entered, enteredTick)
                + " service_completed_tick=" + tick(completed, completedTick)
                + " tracked=" + (tracked ? "YES" : "NO")
                + " target_last_tick=" + tick(tracked && dispatched, dispatchTick)
                + " target_branch=" + (tracked ? branch.name() : "UNOBSERVED")
                + " target_wait_first_tick=" + tick(tracked && waitAttempts > 0, firstWaitTick)
                + " target_wait_last_tick=" + tick(tracked && waitAttempts > 0, lastWaitTick)
                + " target_wait_attempts=" + (tracked && waitAttempts > 0
                        ? Integer.toString(waitAttempts) : "UNOBSERVED");
    }

    void clear() {
        latestPrepared = null;
        entered = false;
        completed = false;
        dispatched = false;
        enteredTick = 0;
        completedTick = 0;
        dispatchTick = 0;
        branch = Branch.UNOBSERVED;
        clearWaitHistory();
    }

    private void clearWaitHistory() {
        firstWaitTick = 0;
        lastWaitTick = 0;
        waitAttempts = 0;
        waitDispatchCurrent = false;
    }

    private boolean tracks(UUID transferId) {
        return transferId != null && transferId.equals(latestPrepared);
    }

    private static String tick(boolean observed, int value) {
        return observed ? Integer.toString(value) : "UNOBSERVED";
    }
}
