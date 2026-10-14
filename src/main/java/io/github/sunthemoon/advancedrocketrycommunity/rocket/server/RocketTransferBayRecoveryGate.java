package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Internal, server-lifetime bay seam. Queries read coordinator RAM only; they perform no world or file IO. */
public interface RocketTransferBayRecoveryGate {
    enum Kind { UNCLAIMED, HELD, RELEASED_FLIGHT, READY_SOURCE, RELEASED_TO_ORDINARY }
    enum State { OWNED_WAIT, REPAIR }
    enum Action { RECOVER_AUTO, RECOVER_BY_ID, ELECT_DUPLICATES, RECONNECT, SETTLE_SOURCE,
        REMOVE_JOURNAL, TICK_SOURCE, SPAWN_DESTINATION }
    enum BoundaryKind { UNCLAIMED, HELD, PERMIT }

    record Claim(Kind kind, State state, String reason, UUID sourceUuid) {
        public Claim {
            Objects.requireNonNull(kind);
            if (kind == Kind.HELD) {
                Objects.requireNonNull(state); Objects.requireNonNull(reason);
                if (reason.isEmpty() || reason.length() > 64 || !reason.matches("[A-Z0-9_]+")) {
                    throw new IllegalArgumentException("Invalid bay claim reason");
                }
            } else if (state != null || reason != null) { throw new IllegalArgumentException("Unexpected claim detail"); }
            if ((kind == Kind.READY_SOURCE) != (sourceUuid != null)) { throw new IllegalArgumentException("Invalid source handoff"); }
        }
        public static Claim unclaimed() { return new Claim(Kind.UNCLAIMED, null, null, null); }
        public static Claim held(State state, String reason) { return new Claim(Kind.HELD, state, reason, null); }
        public static Claim releasedFlight() { return new Claim(Kind.RELEASED_FLIGHT, null, null, null); }
        public static Claim readySource(UUID source) { return new Claim(Kind.READY_SOURCE, null, null, source); }
        public static Claim ordinary() { return new Claim(Kind.RELEASED_TO_ORDINARY, null, null, null); }
    }

    /** The coordinator validates and consumes its exact session/action/authority token, never an operation UUID. */
    @FunctionalInterface interface Permit {
        boolean valid(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record, Action action);
    }
    record Boundary(BoundaryKind kind, Permit token) {
        public Boundary {
            Objects.requireNonNull(kind);
            if ((kind == BoundaryKind.PERMIT) != (token != null)) { throw new IllegalArgumentException("Invalid bay permit"); }
        }
        public static Boundary unclaimed() { return new Boundary(BoundaryKind.UNCLAIMED, null); }
        public static Boundary held() { return new Boundary(BoundaryKind.HELD, null); }
        public static Boundary permit(Permit token) { return new Boundary(BoundaryKind.PERMIT, token); }
    }

    Claim claim(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record);
    Boundary beforeAction(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record, Action action);

    /** Join and leave both invalidate the group's physical proofs before another action may be authorized. */
    default void endpointChanged(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) { }
    /** A bay permit never overrides an escrow conflict. W27d persists/owns the resulting REPAIR state. */
    default void escrowConflict(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) { }

    RocketTransferBayRecoveryGate UNCLAIMED = new RocketTransferBayRecoveryGate() {
        @Override public Claim claim(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
            return Claim.unclaimed();
        }
        @Override public Boundary beforeAction(MinecraftServer server, RocketTransferSavedData journal,
                RocketTransferRecord record, Action action) { return Boundary.unclaimed(); }
    };
}
