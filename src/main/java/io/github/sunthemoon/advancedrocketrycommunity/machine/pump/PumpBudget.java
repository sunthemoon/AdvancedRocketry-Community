package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

/** ADR-064 section 7 constants and integer-only resource planning. */
public final class PumpBudget {
    public static final int TANK = 16_000;
    public static final int ENERGY = 10_000;
    public static final int COST = 100;
    public static final int SOURCE = 1_000;
    public static final int OUTPUT = 1_000;
    public static final int COOLDOWN = 5;
    public static final int HORIZONTAL = 32;
    public static final int DOWN = 64;
    public static final int SEARCH = 4_096;
    public static final int PER_TICK = 64;

    public record State(int energy, int amount, int cooldown) {
        public State {
            if (energy < 0 || energy > ENERGY || amount < 0 || amount > TANK
                    || cooldown < 0 || cooldown > COOLDOWN) {
                throw new IllegalArgumentException("Pump resource bounds exceeded");
            }
        }
        public State tick() { return new State(energy, amount, Math.max(0, cooldown - 1)); }
    }
    public record Plan(State state, PumpCode code) { }

    public static Plan drain(State state, boolean compatible) {
        if (state.cooldown() > 0) { return new Plan(state, PumpCode.COOLDOWN); }
        if (state.energy() < COST) { return new Plan(state, PumpCode.NO_ENERGY); }
        if (state.amount() > TANK - SOURCE) { return new Plan(state, PumpCode.TANK_FULL); }
        if (!compatible) { return new Plan(state, PumpCode.TANK_INCOMPATIBLE); }
        return new Plan(new State(state.energy() - COST, state.amount() + SOURCE, COOLDOWN), PumpCode.DRAINED);
    }
    private PumpBudget() { }
}
