package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointCode;
import java.util.Objects;
import java.util.Optional;

/**
 * ADR-059 sections 3 to 8 as pure decisions: the bind order, validity at use, who may ship, ride and unbind, the ride
 * request and commit orders, the costs, and the station guard's warp and deletion decisions. The services and the
 * reference tests ask these functions, so the tests describe the production decisions.
 */
public final class ElevatorRules {
    public static final int MIN_PERCENT = 10;
    public static final int MAX_PERCENT = 1000;
    public static final int CARGO_FE = 20_000;
    public static final int RIDE_FE = 50_000;
    public static final int CARGO_TRAVEL_TICKS = 200;
    public static final int RIDE_COUNTDOWN_TICKS = 100;
    public static final int ARRIVAL_WAIT_TICKS = 100;
    public static final int ARRIVAL_TICKET_TICKS = 300;
    public static final int RIDE_COOLDOWN_TICKS = 200;
    public static final int CANCEL_COOLDOWN_TICKS = 100;
    public static final int PENDING_RIDES_PER_ENDPOINT = 4;
    public static final int PENDING_RIDES = 64;
    public static final int TERMINAL_PAD_CLEARANCE = 2;
    public static final int MAX_TETHER_BLOCKS = 384;

    private ElevatorRules() {
    }

    /** A decision with the ADR-045 rule that failed, when one did ({@code ELEVATOR_RULE}). */
    public record Check(EndgameCode code, Optional<ElevatorEndpointCode> rule) {
        public static final Check OK = new Check(EndgameCode.OK, Optional.empty());

        public Check {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(rule, "rule");
        }

        public static Check of(EndgameCode code) {
            return code == EndgameCode.OK ? OK : new Check(code, Optional.empty());
        }

        public static Check rule(ElevatorEndpointCode failed) {
            return new Check(EndgameCode.ELEVATOR_RULE, Optional.of(failed));
        }

        public boolean ok() {
            return code == EndgameCode.OK;
        }

        /** The code, with the failed rule's name after it: {@code ELEVATOR_RULE NOT_CURRENT_ORBIT}. */
        public String describe() {
            return rule.map(failed -> code.name() + " " + failed.name()).orElse(code.name());
        }
    }

    // ---- Bind (section 3) -------------------------------------------------------------------------------------

    /**
     * What a bind found, in its order.
     *
     * @param rule              the ADR-045 result for (station, anchor body, x, z), rule 2 included
     * @param terminalUsable    the terminal is ACTIVE and inside this station's committed region
     * @param anchorActive      the anchor is ACTIVE
     * @param anchorOwnedByActor the anchor is the actor's own
     * @param cardinality       {@code OK} or the first of {@code STATION_BOUND}, {@code ANCHOR_BOUND},
     *                          {@code TERMINAL_BOUND}, {@code COLUMN_BOUND}
     * @param admission         the root's admission for one more pair
     */
    public record BindFacts(ElevatorEndpointCode rule, boolean terminalUsable, boolean anchorActive,
                            boolean anchorOwnedByActor, boolean operator, EndgameCode cardinality,
                            boolean warpPending, EndgameCode admission) {
        public BindFacts {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(cardinality, "cardinality");
            Objects.requireNonNull(admission, "admission");
        }
    }

    public static Check bind(BindFacts facts) {
        if (facts.rule() != ElevatorEndpointCode.VALID) {
            return Check.rule(facts.rule());
        }
        if (!facts.terminalUsable()) {
            return Check.of(EndgameCode.TERMINAL_UNAVAILABLE);
        }
        if (!facts.anchorActive()) {
            return Check.of(EndgameCode.ANCHOR_UNAVAILABLE);
        }
        if (!facts.operator() && !facts.anchorOwnedByActor()) {
            return Check.of(EndgameCode.ANCHOR_FOREIGN);
        }
        if (facts.cardinality() != EndgameCode.OK) {
            return Check.of(facts.cardinality());
        }
        if (facts.warpPending()) {
            return Check.of(EndgameCode.WARP_PENDING);
        }
        return Check.of(facts.admission());
    }

    // ---- Validity at use (section 3) ------------------------------------------------------------------------

    /**
     * Re-derived at every use, never cached: ADR-045 rules 1, 3, 4 and 5 ({@code rule}, checked as an operator so rule
     * 2 is skipped), the stored Level key still the body's Level, and both endpoints ACTIVE. A failure changes nothing.
     */
    public static Check validity(ElevatorEndpointCode rule, boolean levelKeyCurrent, boolean terminalActive,
                                 boolean anchorActive) {
        if (rule != ElevatorEndpointCode.VALID) {
            return Check.rule(rule);
        }
        if (!levelKeyCurrent) {
            return Check.of(EndgameCode.PAIR_LEVEL_CHANGED);
        }
        if (!terminalActive) {
            return Check.of(EndgameCode.TERMINAL_UNAVAILABLE);
        }
        return Check.of(anchorActive ? EndgameCode.OK : EndgameCode.ANCHOR_UNAVAILABLE);
    }

    // ---- Who may (section 7, ADR-054 section 3 per-action overrides) -------------------------------------------

    /**
     * Riding and shipping: station {@code VISIT} (owner, members, operators), and the anchor owner is the station's
     * current owner or a member; operators are exempt from both.
     */
    public static EndgameCode access(boolean operator, boolean stationVisit, boolean anchorOwnerInStation) {
        if (operator) {
            return EndgameCode.OK;
        }
        if (!stationVisit) {
            return EndgameCode.UNAUTHORIZED;
        }
        return anchorOwnerInStation ? EndgameCode.OK : EndgameCode.ANCHOR_OWNER_NOT_MEMBER;
    }

    /** Unbinding: the station owner, the anchor owner or an operator, whatever the pair's validity. */
    public static boolean mayUnbind(boolean operator, boolean stationOwner, boolean anchorOwner) {
        return operator || stationOwner || anchorOwner;
    }

    // ---- Rides (section 8) ------------------------------------------------------------------------------------

    /**
     * What a ride request found, in its order.
     *
     * @param passengerFree     not riding anything and carrying no passengers
     * @param otherPending      the player has another pending ride
     * @param cooledDown        200 ticks after the player's last commit and 100 after a cancelled ride
     */
    public record RideFacts(boolean enabled, boolean paired, boolean onPlatform, EndgameCode access,
                            boolean passengerFree, boolean otherPending, boolean cooledDown, int pendingHere,
                            int pendingOnServer, Check validity) {
        public RideFacts {
            Objects.requireNonNull(access, "access");
            Objects.requireNonNull(validity, "validity");
        }
    }

    public static Check rideRequest(RideFacts facts) {
        if (!facts.enabled()) {
            return Check.of(EndgameCode.SYSTEM_DISABLED);
        }
        if (!facts.paired()) {
            return Check.of(EndgameCode.NOT_BOUND);
        }
        if (!facts.onPlatform()) {
            return Check.of(EndgameCode.NOT_ON_PLATFORM);
        }
        if (facts.access() != EndgameCode.OK) {
            return Check.of(facts.access());
        }
        if (!facts.passengerFree()) {
            return Check.of(EndgameCode.DISMOUNT_FIRST);
        }
        if (facts.otherPending()) {
            return Check.of(EndgameCode.RIDE_PENDING);
        }
        if (!facts.cooledDown()) {
            return Check.of(EndgameCode.RATE_LIMITED);
        }
        if (facts.pendingHere() >= PENDING_RIDES_PER_ENDPOINT || facts.pendingOnServer() >= PENDING_RIDES) {
            return Check.of(EndgameCode.RIDE_LIMIT);
        }
        return facts.validity();
    }

    /** What a ride's commit does this tick. */
    public enum Commit {
        /** Teleport now and debit once the rider stands at the arrival. */
        TELEPORT,
        /** The arrival chunk is not loaded yet: check again next tick. */
        WAIT,
        /** Cancel with the code; nothing changes but the ticket is released. */
        CANCEL
    }

    public record CommitDecision(Commit commit, Check check) {
        public CommitDecision {
            Objects.requireNonNull(commit, "commit");
            Objects.requireNonNull(check, "check");
        }
    }

    /**
     * The commit order, after the countdown: validity and access re-derived, the departing endpoint's energy, the
     * arrival chunk {@code FULL} (waiting at most 100 more ticks), the arrival endpoint present with two free blocks
     * above its platform centre, then the protection chain for the arrival box.
     */
    public static CommitDecision commit(Check validity, EndgameCode access, boolean energy, boolean arrivalLoaded,
                                        boolean arrivalWaitOver, boolean arrivalClear, EndgameCode protection) {
        if (!validity.ok()) {
            return new CommitDecision(Commit.CANCEL, validity);
        }
        if (access != EndgameCode.OK) {
            return new CommitDecision(Commit.CANCEL, Check.of(access));
        }
        if (!energy) {
            return new CommitDecision(Commit.CANCEL, Check.of(EndgameCode.INSUFFICIENT_ENERGY));
        }
        if (!arrivalLoaded) {
            return arrivalWaitOver ? new CommitDecision(Commit.CANCEL, Check.of(EndgameCode.ARRIVAL_UNLOADED))
                    : new CommitDecision(Commit.WAIT, Check.OK);
        }
        if (!arrivalClear) {
            return new CommitDecision(Commit.CANCEL, Check.of(EndgameCode.ARRIVAL_OBSTRUCTED));
        }
        if (protection != EndgameCode.OK) {
            return new CommitDecision(Commit.CANCEL, Check.of(protection));
        }
        return new CommitDecision(Commit.TELEPORT, Check.OK);
    }

    // ---- Costs (sections 6 and 8) -----------------------------------------------------------------------------

    public static int cargoCost(int percent) {
        return CARGO_FE * requirePercent(percent) / 100;
    }

    public static int rideCost(int percent) {
        return RIDE_FE * requirePercent(percent) / 100;
    }

    private static int requirePercent(int percent) {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT) {
            throw new IllegalArgumentException("The elevator energy percent is outside 10..1000");
        }
        return percent;
    }

    // ---- The station guard (section 5) ------------------------------------------------------------------------

    public enum Guard {
        ALLOWED,
        /** A pair names the station ({@code ELEVATOR_BOUND}, or {@code ELEVATOR_REFERENCES} for a deletion). */
        BOUND,
        /** The endgame root is not operational: fail closed ({@code ENDGAME_UNAVAILABLE}). */
        UNAVAILABLE
    }

    /** Warp request, confirmation and commit: refused while a pair names the station, and while the root is down. */
    public static Guard warp(boolean rootOperational, boolean stationBound) {
        if (!rootOperational) {
            return Guard.UNAVAILABLE;
        }
        return stationBound ? Guard.BOUND : Guard.ALLOWED;
    }

    /** Station deletion: also refused while a transit record's source or destination stands in the station's region. */
    public static Guard delete(boolean rootOperational, boolean stationBound, boolean transitInRegion) {
        if (!rootOperational) {
            return Guard.UNAVAILABLE;
        }
        return stationBound || transitInRegion ? Guard.BOUND : Guard.ALLOWED;
    }
}
