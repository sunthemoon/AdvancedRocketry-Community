package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable schema-1 entity flight state; world mutation remains a server service. */
public final class RocketFlightData {
    private final int schemaVersion;
    private final UUID logicalRocketId;
    private final RocketFlightState state;
    private final RocketFuelState fuel;
    private final RocketFlightPlan plan;
    private final RocketPassengerManifest passengers;
    private final ResourceLocation currentBody;
    private final ResourceLocation currentDimension;
    private final RocketPosition currentOrigin;
    private final long stateStartedGameTime;
    private final UUID activeTransferId;

    private RocketFlightData(
            int schemaVersion,
            UUID logicalRocketId,
            RocketFlightState state,
            RocketFuelState fuel,
            RocketFlightPlan plan,
            RocketPassengerManifest passengers,
            ResourceLocation currentBody,
            ResourceLocation currentDimension,
            RocketPosition currentOrigin,
            long stateStartedGameTime,
            UUID activeTransferId
    ) {
        if (schemaVersion != RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported rocket flight data schema");
        }
        this.schemaVersion = schemaVersion;
        this.logicalRocketId = Objects.requireNonNull(logicalRocketId, "logicalRocketId");
        this.state = Objects.requireNonNull(state, "state");
        this.fuel = Objects.requireNonNull(fuel, "fuel");
        this.plan = plan;
        this.passengers = Objects.requireNonNull(passengers, "passengers");
        this.currentBody = requireIdentifier(currentBody, "currentBody");
        this.currentDimension = requireIdentifier(currentDimension, "currentDimension");
        this.currentOrigin = Objects.requireNonNull(currentOrigin, "currentOrigin");
        if (stateStartedGameTime < 0L) {
            throw new IllegalArgumentException("Flight state start time cannot be negative");
        }
        this.stateStartedGameTime = stateStartedGameTime;
        this.activeTransferId = activeTransferId;
        validateStateShape();
    }

    public static RocketFlightData initial(
            UUID logicalRocketId,
            long fuelCapacity,
            int declaredSeats,
            ResourceLocation body,
            ResourceLocation dimension,
            RocketPosition origin,
            long gameTime
    ) {
        return restore(
                RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION,
                logicalRocketId,
                RocketFlightState.ASSEMBLED,
                RocketFuelState.empty(fuelCapacity),
                null,
                RocketPassengerManifest.empty(declaredSeats),
                body,
                dimension,
                origin,
                gameTime,
                null
        );
    }

    public static RocketFlightData restore(
            int schemaVersion,
            UUID logicalRocketId,
            RocketFlightState state,
            RocketFuelState fuel,
            RocketFlightPlan plan,
            RocketPassengerManifest passengers,
            ResourceLocation currentBody,
            ResourceLocation currentDimension,
            RocketPosition currentOrigin,
            long stateStartedGameTime,
            UUID activeTransferId
    ) {
        return new RocketFlightData(
                schemaVersion,
                logicalRocketId,
                state,
                fuel,
                plan,
                passengers,
                currentBody,
                currentDimension,
                currentOrigin,
                stateStartedGameTime,
                activeTransferId
        );
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public UUID logicalRocketId() {
        return logicalRocketId;
    }

    public RocketFlightState state() {
        return state;
    }

    public RocketFuelState fuel() {
        return fuel;
    }

    public Optional<RocketFlightPlan> plan() {
        return Optional.ofNullable(plan);
    }

    public RocketPassengerManifest passengers() {
        return passengers;
    }

    public ResourceLocation currentBody() {
        return currentBody;
    }

    public ResourceLocation currentDimension() {
        return currentDimension;
    }

    public RocketPosition currentOrigin() {
        return currentOrigin;
    }

    public long stateStartedGameTime() {
        return stateStartedGameTime;
    }

    public Optional<UUID> activeTransferId() {
        return Optional.ofNullable(activeTransferId);
    }

    public RocketFlightData withFuel(RocketFuelState updatedFuel, long gameTime) {
        Objects.requireNonNull(updatedFuel, "updatedFuel");
        if (updatedFuel.capacity() != fuel.capacity()) {
            throw new IllegalArgumentException("Fuel update cannot change rocket capacity");
        }
        RocketFlightState updatedState = state;
        long updatedStartedAt = stateStartedGameTime;
        if (updatedFuel.amount() > 0L && state.acceptsFuel()) {
            RocketFlightTransition transition = RocketFlightStateMachine.apply(
                    state,
                    RocketFlightEvent.FUEL_AVAILABLE
            );
            if (!transition.applied()) {
                throw new IllegalStateException("Fuel transition table rejected an accepting state");
            }
            updatedState = transition.next();
            if (updatedState != state) {
                updatedStartedAt = requireGameTime(gameTime);
            }
        }
        return copy(updatedState, updatedFuel, plan, passengers, currentBody, currentDimension,
                currentOrigin, updatedStartedAt, activeTransferId);
    }

    public RocketFlightData withPlan(RocketFlightPlan updatedPlan) {
        Objects.requireNonNull(updatedPlan, "updatedPlan");
        if (state != RocketFlightState.FUELED) {
            throw new IllegalStateException("Only a fueled rocket may accept a flight plan");
        }
        if (!updatedPlan.sourceBody().equals(currentBody)
                || !updatedPlan.sourceDimension().equals(currentDimension)) {
            throw new IllegalArgumentException("Flight plan source does not match the rocket location");
        }
        return copy(state, fuel, updatedPlan, passengers, currentBody, currentDimension,
                currentOrigin, stateStartedGameTime, null);
    }

    public RocketFlightData withPassengers(RocketPassengerManifest updatedPassengers) {
        Objects.requireNonNull(updatedPassengers, "updatedPassengers");
        if (updatedPassengers.seatCapacity() != passengers.seatCapacity()) {
            throw new IllegalArgumentException("Passenger update cannot change seat capacity");
        }
        return copy(state, fuel, plan, updatedPassengers, currentBody, currentDimension,
                currentOrigin, stateStartedGameTime, activeTransferId);
    }

    public RocketFlightData startCountdown(long gameTime) {
        if (plan == null) {
            throw new IllegalStateException("Countdown requires a server flight plan");
        }
        return transition(RocketFlightEvent.START_COUNTDOWN, gameTime, plan, activeTransferId);
    }

    public RocketFlightData cancelCountdown(long gameTime) {
        return transition(RocketFlightEvent.CANCEL_COUNTDOWN, gameTime, plan, null);
    }

    public RocketFlightData completeCountdown(long gameTime) {
        return transition(RocketFlightEvent.COUNTDOWN_COMPLETE, gameTime, plan, null);
    }

    public RocketFlightData beginTransit(UUID transferId, long gameTime) {
        Objects.requireNonNull(transferId, "transferId");
        return transition(RocketFlightEvent.ASCENT_COMPLETE, gameTime, plan, transferId);
    }

    public RocketFlightData arriveAtDestination(
            RocketFuelState debitedFuel,
            ResourceLocation body,
            ResourceLocation dimension,
            RocketPosition origin,
            long gameTime
    ) {
        if (plan == null || activeTransferId == null) {
            throw new IllegalStateException("Destination arrival requires an active transfer plan");
        }
        if (!plan.destinationBody().equals(body) || !plan.destinationDimension().equals(dimension)) {
            throw new IllegalArgumentException("Destination location does not match the flight plan");
        }
        if (debitedFuel.capacity() != fuel.capacity()) {
            throw new IllegalArgumentException("Destination fuel capacity changed during transfer");
        }
        RocketFlightTransition transition = requireTransition(
                RocketFlightEvent.DESTINATION_AUTHORITY_ACQUIRED
        );
        return copy(
                transition.next(),
                debitedFuel,
                plan,
                passengers,
                body,
                dimension,
                Objects.requireNonNull(origin, "origin"),
                requireGameTime(gameTime),
                activeTransferId
        );
    }

    public RocketFlightData land(long gameTime) {
        RocketFlightTransition transition = requireTransition(RocketFlightEvent.LANDING_COMPLETE);
        return copy(
                transition.next(),
                fuel,
                null,
                passengers,
                currentBody,
                currentDimension,
                currentOrigin,
                requireGameTime(gameTime),
                null
        );
    }

    public RocketFlightData markFailed(long gameTime) {
        return transition(RocketFlightEvent.MARK_FAILED, gameTime, plan, activeTransferId);
    }

    public RocketFlightData recover(boolean fueled, long gameTime) {
        RocketFlightEvent event = fueled
                ? RocketFlightEvent.RECOVER_FUELED
                : RocketFlightEvent.RECOVER_ASSEMBLED;
        return transition(event, gameTime, null, null);
    }

    private RocketFlightData transition(
            RocketFlightEvent event,
            long gameTime,
            RocketFlightPlan updatedPlan,
            UUID updatedTransferId
    ) {
        RocketFlightTransition transition = requireTransition(event);
        return copy(
                transition.next(),
                fuel,
                updatedPlan,
                passengers,
                currentBody,
                currentDimension,
                currentOrigin,
                requireGameTime(gameTime),
                updatedTransferId
        );
    }

    private RocketFlightTransition requireTransition(RocketFlightEvent event) {
        RocketFlightTransition transition = RocketFlightStateMachine.apply(state, event);
        if (!transition.applied()) {
            throw new IllegalStateException("Illegal rocket transition " + state + " + " + event);
        }
        return transition;
    }

    private RocketFlightData copy(
            RocketFlightState updatedState,
            RocketFuelState updatedFuel,
            RocketFlightPlan updatedPlan,
            RocketPassengerManifest updatedPassengers,
            ResourceLocation updatedBody,
            ResourceLocation updatedDimension,
            RocketPosition updatedOrigin,
            long updatedStartedAt,
            UUID updatedTransferId
    ) {
        return restore(
                schemaVersion,
                logicalRocketId,
                updatedState,
                updatedFuel,
                updatedPlan,
                updatedPassengers,
                updatedBody,
                updatedDimension,
                updatedOrigin,
                updatedStartedAt,
                updatedTransferId
        );
    }

    private void validateStateShape() {
        if (state == RocketFlightState.DISASSEMBLED) {
            throw new IllegalArgumentException("A removed entity cannot persist DISASSEMBLED flight data");
        }
        boolean planRequired = state == RocketFlightState.COUNTDOWN
                || state == RocketFlightState.ASCENT
                || state == RocketFlightState.TRANSIT
                || state == RocketFlightState.DESCENT;
        if (planRequired && plan == null) {
            throw new IllegalArgumentException("Active flight state requires a plan");
        }
        if ((state == RocketFlightState.ASSEMBLED || state == RocketFlightState.LANDED)
                && (plan != null || activeTransferId != null)) {
            throw new IllegalArgumentException("Stationary unplanned state cannot retain a plan or transfer");
        }
        if (state == RocketFlightState.FUELED && fuel.amount() <= 0L) {
            throw new IllegalArgumentException("FUELED state requires positive fuel");
        }
        boolean transferRequired = state == RocketFlightState.TRANSIT
                || state == RocketFlightState.DESCENT;
        if (transferRequired && activeTransferId == null) {
            throw new IllegalArgumentException("Transfer/descent state requires a transaction id");
        }
        if (!transferRequired
                && state != RocketFlightState.FAILED_RECOVERABLE
                && activeTransferId != null) {
            throw new IllegalArgumentException("Non-transfer state cannot retain a transaction id");
        }
        if (plan != null) {
            boolean atSource = plan.sourceBody().equals(currentBody)
                    && plan.sourceDimension().equals(currentDimension);
            boolean atDestination = plan.destinationBody().equals(currentBody)
                    && plan.destinationDimension().equals(currentDimension);
            if (!atSource && !atDestination) {
                throw new IllegalArgumentException("Flight data location is outside its plan");
            }
            if ((state == RocketFlightState.FUELED
                    || state == RocketFlightState.COUNTDOWN
                    || state == RocketFlightState.ASCENT) && !atSource) {
                throw new IllegalArgumentException("Pre-transfer flight state must remain at the source");
            }
            if (state == RocketFlightState.DESCENT && !atDestination) {
                throw new IllegalArgumentException("Descent flight state must be at the destination");
            }
        }
    }

    private static ResourceLocation requireIdentifier(ResourceLocation value, String name) {
        Objects.requireNonNull(value, name);
        if (value.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(name + " exceeds the fixed identifier limit");
        }
        return value;
    }

    private static long requireGameTime(long gameTime) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Flight state game time cannot be negative");
        }
        return gameTime;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketFlightData other
                && schemaVersion == other.schemaVersion
                && logicalRocketId.equals(other.logicalRocketId)
                && state == other.state
                && fuel.equals(other.fuel)
                && Objects.equals(plan, other.plan)
                && passengers.equals(other.passengers)
                && currentBody.equals(other.currentBody)
                && currentDimension.equals(other.currentDimension)
                && currentOrigin.equals(other.currentOrigin)
                && stateStartedGameTime == other.stateStartedGameTime
                && Objects.equals(activeTransferId, other.activeTransferId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                schemaVersion,
                logicalRocketId,
                state,
                fuel,
                plan,
                passengers,
                currentBody,
                currentDimension,
                currentOrigin,
                stateStartedGameTime,
                activeTransferId
        );
    }
}
