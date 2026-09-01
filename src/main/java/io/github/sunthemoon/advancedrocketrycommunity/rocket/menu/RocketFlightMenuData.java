package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import java.util.UUID;
import net.minecraft.world.inventory.ContainerData;

/** Server-computed fixed fields; the client never supplies stats, fuel, time, or coordinates. */
final class RocketFlightMenuData implements ContainerData {
    static final int COUNT = 9;

    private final RocketEntity rocket;

    RocketFlightMenuData(RocketEntity rocket) {
        this.rocket = java.util.Objects.requireNonNull(rocket, "rocket");
    }

    @Override
    public int get(int index) {
        RocketFlightData data = rocket.flightData().orElse(null);
        if (data == null) {
            return index == 0 ? RocketFlightState.FAILED_RECOVERABLE.networkId() : 0;
        }
        RocketDestination current = current(data);
        RocketDestination destination = destination(data, current);
        RocketFlightPlanResult quote = quote(data, current, destination);
        return switch (index) {
            case 0 -> data.state().networkId();
            case 1 -> boundedInt(data.fuel().amount());
            case 2 -> boundedInt(data.fuel().capacity());
            case 3 -> boundedInt(data.plan().map(plan -> plan.requiredFuel()).orElse(quote.requiredFuel()));
            case 4 -> current == null ? -1 : current.networkId();
            case 5 -> destination == null ? -1 : destination.networkId();
            case 6 -> (data.state() == RocketFlightState.FUELED
                    || (data.state() == RocketFlightState.LANDED && data.fuel().amount() > 0L))
                    && quote.success() ? 1 : 0;
            case 7 -> countdownRemaining(data);
            case 8 -> data.passengers().assignments().size();
            default -> throw new IndexOutOfBoundsException("Unknown rocket flight menu field " + index);
        };
    }

    @Override
    public void set(int index, int value) {
        // Client mirrors are read-only; server state changes only through validated intents.
    }

    @Override
    public int getCount() {
        return COUNT;
    }

    private RocketFlightPlanResult quote(
            RocketFlightData data,
            RocketDestination current,
            RocketDestination destination
    ) {
        if (current == null || destination == null || rocket.snapshot().isEmpty()) {
            return RocketFlightPlanResult.failure(
                    RocketFlightPlanCode.UNSUPPORTED_ROUTE,
                    0L
            );
        }
        return RocketFlightPlanner.plan(
                rocket.snapshot().orElseThrow().stats(),
                data.fuel(),
                current.profile(),
                destination.profile(),
                destination == RocketDestination.SPACE_STATION
                        ? data.plan().flatMap(plan -> plan.destinationStation())
                        .orElse(new UUID(0L, 0L))
                        : null,
                new UUID(0L, 0L),
                Math.max(0L, rocket.level().getGameTime())
        );
    }

    private static RocketDestination current(RocketFlightData data) {
        try {
            return RocketDestination.fromDimension(data.currentDimension());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static RocketDestination destination(
            RocketFlightData data,
            RocketDestination current
    ) {
        if (data.plan().isPresent()) {
            try {
                return RocketDestination.fromBody(data.plan().orElseThrow().destinationBody());
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return current == null ? null : current.opposite();
    }

    private int countdownRemaining(RocketFlightData data) {
        if (data.state() != RocketFlightState.COUNTDOWN) {
            return 0;
        }
        long elapsed = Math.max(0L, rocket.level().getGameTime() - data.stateStartedGameTime());
        return (int) Math.max(0L, RocketFlightLimits.COUNTDOWN_TICKS - elapsed);
    }

    private static int boundedInt(long value) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Rocket menu value is outside the fixed integer bound");
        }
        return (int) value;
    }
}
