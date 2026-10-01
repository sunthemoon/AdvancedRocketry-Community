package io.github.sunthemoon.advancedrocketrycommunity.endgame.authority;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ADR-054 section 3: {@code allowed(actor, device, action)} as a pure function over a snapshot. The caller has
 * already established that the actor is a connected, non-fake player (FakePlayers, command blocks, functions and
 * the console never pass an intent) and computed the operator flag only for such a player.
 */
public final class EndgameAuthority {
    private EndgameAuthority() {
    }

    public static Decision decide(Request request) {
        Objects.requireNonNull(request, "request");
        UUID actor = request.actorId();
        boolean deviceOwner = request.deviceOwner().filter(actor::equals).isPresent();
        StationContext station = request.station();
        EndgameAction action = request.action();

        if (station.kind() == StationKind.UNAVAILABLE) {
            // A device in the Space Level outside every committed region, or in a blocked or quarantined registry.
            return action == EndgameAction.WITHDRAW && (request.operator() || deviceOwner)
                    ? Decision.full() : Decision.refused(EndgameCode.STATION_UNAVAILABLE);
        }
        if (request.operator()) {
            return Decision.full();
        }
        if (station.kind() == StationKind.OUTSIDE_STATIONS) {
            if (deviceOwner) {
                return Decision.full();
            }
            return action == EndgameAction.VIEW ? Decision.publicView() : Decision.refused(EndgameCode.UNAUTHORIZED);
        }

        boolean stationOwner = station.ownerId().filter(actor::equals).isPresent();
        boolean member = station.members().contains(actor);
        boolean changesDevice = action == EndgameAction.CONFIGURE || action == EndgameAction.OPERATE;
        if (request.stationManaged() && changesDevice && !stationOwner) {
            // ADR-058: inside a station a gravity field needs MANAGE_STATION (the station owner or an operator).
            return Decision.refused(EndgameCode.UNAUTHORIZED);
        }
        if (deviceOwner) {
            if (stationOwner || member) {
                return Decision.full();
            }
            // The owner lost station BUILD access: VIEW only (of their own device, so with detail).
            return action == EndgameAction.VIEW ? Decision.full() : Decision.refused(EndgameCode.UNAUTHORIZED);
        }
        if (stationOwner) {
            return Decision.full();
        }
        if (member) {
            return action == EndgameAction.VIEW ? Decision.publicView() : Decision.refused(EndgameCode.UNAUTHORIZED);
        }
        return Decision.refused(EndgameCode.UNAUTHORIZED);
    }

    public enum StationKind {
        /** Not in the Space Level: no station rules apply. */
        OUTSIDE_STATIONS,
        /** Inside a committed station region of an operational registry. */
        COMMITTED,
        /** In the Space Level outside every committed region, or the station registry is blocked or quarantined. */
        UNAVAILABLE
    }

    /** The station at the device position, as the caller observed it on the server thread. */
    public record StationContext(StationKind kind, Optional<UUID> ownerId, Set<UUID> members) {
        public StationContext {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(ownerId, "ownerId");
            members = Set.copyOf(members);
            if ((kind == StationKind.COMMITTED) != ownerId.isPresent()) {
                throw new IllegalArgumentException("Only a committed station has an owner");
            }
            if (kind != StationKind.COMMITTED && !members.isEmpty()) {
                throw new IllegalArgumentException("Only a committed station has members");
            }
        }

        public static StationContext outside() {
            return new StationContext(StationKind.OUTSIDE_STATIONS, Optional.empty(), Set.of());
        }

        public static StationContext unavailable() {
            return new StationContext(StationKind.UNAVAILABLE, Optional.empty(), Set.of());
        }

        public static StationContext committed(UUID ownerId, Set<UUID> members) {
            return new StationContext(StationKind.COMMITTED, Optional.of(ownerId), members);
        }
    }

    /**
     * @param deviceOwner    empty for an unowned device (ADR-054 section 2)
     * @param stationManaged true for systems whose CONFIGURE/OPERATE inside stations need MANAGE_STATION (ADR-058)
     */
    public record Request(UUID actorId, boolean operator, Optional<UUID> deviceOwner, StationContext station,
                          EndgameAction action, boolean stationManaged) {
        public Request {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(deviceOwner, "deviceOwner");
            Objects.requireNonNull(station, "station");
            Objects.requireNonNull(action, "action");
        }
    }

    /**
     * @param detail true when the actor may see targets, coordinates and other device IDs; a public view carries
     *               none of them (ADR-054 section 3)
     */
    public record Decision(boolean allowed, boolean detail, EndgameCode refusal) {
        public Decision {
            Objects.requireNonNull(refusal, "refusal");
            if (allowed != (refusal == EndgameCode.OK)) {
                throw new IllegalArgumentException("An allowed decision has no refusal code and only it");
            }
            if (!allowed && detail) {
                throw new IllegalArgumentException("A refusal grants no detail");
            }
        }

        static Decision full() {
            return new Decision(true, true, EndgameCode.OK);
        }

        static Decision publicView() {
            return new Decision(true, false, EndgameCode.OK);
        }

        static Decision refused(EndgameCode code) {
            return new Decision(false, false, code);
        }
    }
}
