package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

/** Pure bounded fuel-credit accounting; the adapter owns fuel and energy transfers. */
public final class CombustionBurn {
    public static final int RATE = 40;
    public static final int CAPACITY = 20_000;
    public static final int OUTPUT_PER_TICK = 1_000;
    public static final int MAX_BURN_TICKS = 1_000_000;

    public enum Status {
        NO_FUEL, GENERATING, BUFFER_FULL, DISABLED, FUEL_TOO_LONG, CONTAINER_BLOCKED, REPAIR_REQUIRED
    }

    public record State(int energy, int duration, int remaining) {
        public State {
            if (energy < 0 || energy > CAPACITY || duration < 0 || duration > MAX_BURN_TICKS
                    || remaining < 0 || remaining > duration) {
                throw new IllegalArgumentException("Invalid combustion resource state");
            }
        }

        public State withEnergy(int value) {
            return new State(value, duration, remaining);
        }
    }

    public record Step(State state, boolean consumeFuel, Status status) { }

    public static Step tick(State before, int fuelTicks) {
        if (before.energy() > CAPACITY - RATE) {
            return new Step(before, false, Status.BUFFER_FULL);
        }
        boolean consume = before.remaining() == 0;
        if (consume && (fuelTicks <= 0 || fuelTicks > MAX_BURN_TICKS)) {
            return new Step(before, false, fuelTicks > MAX_BURN_TICKS ? Status.FUEL_TOO_LONG : Status.NO_FUEL);
        }
        int duration = consume ? fuelTicks : before.duration();
        int remaining = consume ? fuelTicks : before.remaining();
        return new Step(new State(before.energy() + RATE, duration, remaining - 1), consume, Status.GENERATING);
    }

    private CombustionBurn() { }
}
