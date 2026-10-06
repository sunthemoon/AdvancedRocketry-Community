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

    void prepared(UUID transferId) {
        latestPrepared = transferId;
        dispatched = false;
        branch = transferId == null ? Branch.UNOBSERVED : Branch.PREPARED;
    }

    void enterTick(int tick) {
        entered = true;
        enteredTick = tick;
    }

    void completeTick() {
        if (entered) {
            completed = true;
            completedTick = enteredTick;
        }
    }

    void dispatch(UUID transferId) {
        if (tracks(transferId) && entered) {
            dispatched = true;
            dispatchTick = enteredTick;
            branch = Branch.LIVE_DISPATCH;
        }
    }

    void branch(UUID transferId, Branch observed) {
        if (tracks(transferId)) {
            branch = observed == null ? Branch.UNOBSERVED : observed;
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
                + " target_branch=" + (tracked ? branch.name() : "UNOBSERVED");
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
    }

    private boolean tracks(UUID transferId) {
        return transferId != null && transferId.equals(latestPrepared);
    }

    private static String tick(boolean observed, int value) {
        return observed ? Integer.toString(value) : "UNOBSERVED";
    }
}
