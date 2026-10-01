package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import java.util.function.IntSupplier;

/**
 * ADR-057 section 4, steps 1 and 2 of one tick, without a world: an empty burn takes one item only while the
 * buffer is not full; a burn adds its fixed rate only when the whole rate fits, otherwise it pauses with nothing
 * consumed and nothing wasted. Energy is created only from consumed fuel.
 */
public final class BlackHoleBurn {
    private BlackHoleBurn() {
    }

    /** The burn state saved with the generator. */
    public record State(int energy, int remaining, int rate) {
        public State {
            if (energy < 0 || remaining < 0 || rate < 0) {
                throw new IllegalArgumentException("A burn state is not negative");
            }
        }
    }

    /** One tick's result; {@code consumed} tells the caller to remove exactly one item from the first non-empty slot. */
    public record Step(State state, boolean consumed, boolean generated) {
    }

    /**
     * @param fuelAvailable whether the fuel buffer holds an item
     * @param nextBurnTicks the burn ticks of the item that would be consumed; asked only when one is
     * @param output        the singularity's {@code output_fe_per_tick}
     * @param percent       {@code endgame.blackHoleGenerator.energyPercent} (10..400)
     */
    public static Step tick(State state, boolean fuelAvailable, IntSupplier nextBurnTicks, int capacity, int output,
                            int percent) {
        int energy = state.energy();
        int remaining = state.remaining();
        int rate = state.rate();
        boolean consumed = false;
        if (remaining == 0 && energy < capacity && fuelAvailable) {
            remaining = nextBurnTicks.getAsInt();
            rate = output * percent / 100;
            consumed = true;
        }
        boolean generated = false;
        if (remaining > 0 && (long) energy + rate <= capacity) {
            energy += rate;
            remaining--;
            generated = true;
        }
        return new Step(new State(energy, remaining, rate), consumed, generated);
    }
}
