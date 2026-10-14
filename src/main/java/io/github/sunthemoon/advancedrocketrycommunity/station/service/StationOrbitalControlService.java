package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * ADR-065 D2-A: the owner or an operator sets a station's logical orbital distance and its three orientation
 * rates or fixed phases. Every value is checked on the server, the clock is sampled once per change, and each
 * change is one checked candidate write that compares the observed station before committing. An unchanged
 * value is a no-op; a changed one is refused while an elevator pins the station.
 */
public final class StationOrbitalControlService {
    private final StationAccessService access;
    private final StationWriteBudget budget;
    private final StationWriteCooldown cooldown;

    /** {@code cooldown} is the per-station settings cooldown shared with gravity; the budget is server-wide. */
    public StationOrbitalControlService(StationAccessService access, StationWriteBudget budget,
                                        StationWriteCooldown cooldown) {
        this.access = Objects.requireNonNull(access, "access");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    public StationManagementResult setAltitude(ServerPlayer player, StationActorContext context,
                                               Optional<StationState> expected, int distance) {
        if (!StationOrbitalControls.validDistance(distance)) {
            throw new IllegalArgumentException("Station orbital distance is outside 4..190");
        }
        return write(player, context, expected, Kind.ALTITUDE, (observed, sample, exhausted) ->
                Change.of(observed.withDistance(distance)));
    }

    /** New signed rates; changing them samples the current phases into a new epoch. */
    public StationManagementResult setRates(ServerPlayer player, StationActorContext context,
                                            Optional<StationState> expected, int east, int up, int north) {
        if (!StationOrbitalControls.validRate(east) || !StationOrbitalControls.validRate(up)
                || !StationOrbitalControls.validRate(north)) {
            throw new IllegalArgumentException("Station orbital rates are outside -60..60");
        }
        return write(player, context, expected, Kind.ORIENTATION, (observed, sample, exhausted) -> {
            if (observed.eastRate() == east && observed.upRate() == up && observed.northRate() == north) {
                return Change.of(observed);
            }
            if (exhausted && (east != 0 || up != 0 || north != 0)) {
                return Change.refused(StationManagementCode.ORBITAL_CLOCK_EXHAUSTED);
            }
            return Change.of(observed.withRates(east, up, north, sample));
        });
    }

    /** STOP: samples the exact current phases and sets every rate to zero, even on an exhausted clock. */
    public StationManagementResult stop(ServerPlayer player, StationActorContext context,
                                        Optional<StationState> expected) {
        return write(player, context, expected, Kind.ORIENTATION, (observed, sample, exhausted) ->
                Change.of(observed.rotating() ? observed.stoppedAt(sample) : observed));
    }

    /** A fixed orientation, chosen only while every rate is zero. */
    public StationManagementResult setPhases(ServerPlayer player, StationActorContext context,
                                             Optional<StationState> expected, int east, int up, int north) {
        if (!StationOrbitalControls.validPhase(east) || !StationOrbitalControls.validPhase(up)
                || !StationOrbitalControls.validPhase(north)) {
            throw new IllegalArgumentException("Station orbital phases are outside 0..359,999");
        }
        return write(player, context, expected, Kind.ORIENTATION, (observed, sample, exhausted) -> {
            if (observed.rotating()) {
                return Change.refused(StationManagementCode.ORIENTATION_ROTATING);
            }
            if (observed.eastPhase() == east && observed.upPhase() == up && observed.northPhase() == north) {
                return Change.of(observed);
            }
            return Change.of(observed.withPhases(east, up, north, sample));
        });
    }

    private StationManagementResult write(ServerPlayer player, StationActorContext context,
                                          Optional<StationState> expected, Kind kind, Candidate candidate) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(expected, "expected");
        StationLocalActor.Located located = StationLocalActor.locate(access, player, context);
        if (located.code() != null) {
            return audit(player, kind, StationManagementResult.of(located.code(), located.station()));
        }
        MinecraftServer server = player.getServer();
        StationState observed = located.station();
        StationRegistrySavedData data = located.authority();
        if (expected.isPresent() && !expected.orElseThrow().equals(observed)) {
            return audit(player, kind, StationManagementResult.of(StationManagementCode.STATION_CHANGED, observed));
        }
        // The one clock sample of this change (ADR-065 section 5.2.1).
        long sample = data.orbitalTick();
        Change change = candidate.change(observed.orbitalControls(), sample, data.orbitalClockExhausted());
        if (change.refusal() != null) {
            return audit(player, kind, StationManagementResult.of(change.refusal(), observed));
        }
        if (change.controls().equals(observed.orbitalControls())) {
            // No write, no cooldown and no elevator question.
            return audit(player, kind, StationManagementResult.of(kind.unchanged, observed));
        }
        switch (ElevatorStationGuard.Installed.current().orbitalControls(server, observed)) {
            case BOUND -> {
                return audit(player, kind, StationManagementResult.of(StationManagementCode.ELEVATOR_BOUND, observed));
            }
            case UNAVAILABLE -> {
                return audit(player, kind, StationManagementResult.of(StationManagementCode.ENDGAME_UNAVAILABLE,
                        observed));
            }
            default -> {
            }
        }
        if (!cooldown.ready(observed.stationId(), server.getTickCount())) {
            return audit(player, kind, StationManagementResult.of(StationManagementCode.ORBITAL_COOLDOWN, observed));
        }
        if (!budget.ready(server.getTickCount(), data.recordCount())) {
            return audit(player, kind, StationManagementResult.of(StationManagementCode.REGISTRY_BUSY, observed));
        }
        StationRegistrySavedData.CheckedUpdate written;
        try {
            written = kind == Kind.ALTITUDE
                    ? data.checkedSetAltitude(server, observed, change.controls().distance())
                    : data.checkedSetOrientation(server, observed, change.controls(), sample);
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error("ARCE_STATION_ORBIT_WRITE_FAILED station={} kind={} stage=candidate",
                    observed.stationId(), kind, exception);
            written = StationRegistrySavedData.CheckedUpdate.WRITE_FAILED;
        }
        StationManagementCode code = switch (written) {
            case COMMITTED -> kind.set;
            case UNCHANGED -> kind.unchanged;
            case STALE -> StationManagementCode.STATION_CHANGED;
            case UNAVAILABLE, INSUFFICIENT_ENERGY, FIRST_EVENT_OVER_BUDGET, FIRST_EVENT_UNAVAILABLE -> StationManagementCode.AUTHORITY_UNAVAILABLE;
            case WRITE_FAILED -> StationManagementCode.WRITE_FAILED;
            case OUTCOME_UNKNOWN -> StationManagementCode.OUTCOME_UNKNOWN;
        };
        if (code == kind.set || code == StationManagementCode.WRITE_FAILED
                || code == StationManagementCode.OUTCOME_UNKNOWN) {
            // Committed and failed writes both start the cooldown, as for gravity.
            cooldown.record(observed.stationId(), server.getTickCount());
            budget.record(server.getTickCount());
        }
        return audit(player, kind, StationManagementResult.of(code, code == kind.set
                ? data.find(observed.stationId()).orElse(null) : observed));
    }

    private static StationManagementResult audit(ServerPlayer player, Kind kind, StationManagementResult result) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_ORBIT action={} code={} station={} actor={} distance={} rates={}",
                kind.name().toLowerCase(java.util.Locale.ROOT), result.code(),
                result.station().map(StationState::stationId).orElse(null), player.getUUID(),
                result.station().map(station -> station.orbitalControls().distance()).orElse(null),
                result.station().map(station -> station.orbitalControls().eastRate() + ","
                        + station.orbitalControls().upRate() + "," + station.orbitalControls().northRate()).orElse(null));
        return result;
    }

    private enum Kind {
        ALTITUDE(StationManagementCode.ALTITUDE_SET, StationManagementCode.ALTITUDE_UNCHANGED),
        ORIENTATION(StationManagementCode.ORIENTATION_SET, StationManagementCode.ORIENTATION_UNCHANGED);

        private final StationManagementCode set;
        private final StationManagementCode unchanged;

        Kind(StationManagementCode set, StationManagementCode unchanged) {
            this.set = set;
            this.unchanged = unchanged;
        }
    }

    @FunctionalInterface
    private interface Candidate {
        Change change(StationOrbitalControls observed, long sample, boolean clockExhausted);
    }

    private record Change(StationOrbitalControls controls, StationManagementCode refusal) {
        static Change of(StationOrbitalControls controls) {
            return new Change(controls, null);
        }

        static Change refused(StationManagementCode refusal) {
            return new Change(null, refusal);
        }
    }
}
