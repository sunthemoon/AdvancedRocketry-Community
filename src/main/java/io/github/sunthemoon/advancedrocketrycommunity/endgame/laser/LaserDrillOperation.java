package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * ADR-055 section 2: whether one logical operation happens now, and with which stack. The first failing condition
 * wins, in the contract's order after the device's own preconditions: owned, system enabled, station, structure
 * formed, lens, running, redstone, active admission, table, energy, and room for the whole stack. On {@code OK} the
 * caller, in the same tick at the controller, takes {@code cost}, raises the index by one and adds the stack; on
 * {@code OUTPUT_FULL} nothing changes, so the same draw is retried and nothing is discarded.
 */
public final class LaserDrillOperation {
    private LaserDrillOperation() {
    }

    public static Decision decide(Inputs in, Predicate<LaserDrillTable.Entry> outputAccepts) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(outputAccepts, "outputAccepts");
        EndgameCode common = common(in);
        if (common != EndgameCode.OK) {
            return Decision.stop(common);
        }
        if (in.orbitBody().isEmpty()) {
            return Decision.stop(EndgameCode.ORBIT_BODY_UNAVAILABLE);
        }
        Optional<LaserDrillTable> table = in.tables().forBody(in.orbitBody().get());
        if (table.isEmpty()) {
            return Decision.stop(EndgameCode.NO_TABLE);
        }
        if (in.energy() < in.cost()) {
            return new Decision(EndgameCode.INSUFFICIENT_ENERGY, table, Optional.empty());
        }
        LaserDrillTable.Entry stack = LaserDraw.draw(table.get(), in.seed(), in.index());
        if (!outputAccepts.test(stack)) {
            return new Decision(EndgameCode.OUTPUT_FULL, table, Optional.of(stack));
        }
        return new Decision(EndgameCode.OK, table, Optional.of(stack));
    }

    /**
     * The conditions both modes share, in contract order: owned, system enabled, station, structure formed, lens,
     * running, redstone and active admission; {@code OK} when all hold.
     */
    public static EndgameCode common(Inputs in) {
        if (!in.owned()) {
            return EndgameCode.UNOWNED;
        }
        if (!in.systemEnabled()) {
            return EndgameCode.SYSTEM_DISABLED;
        }
        if (in.station() != EndgameCode.OK) {
            return in.station();
        }
        if (in.structure() != EndgameCode.OK) {
            return in.structure();
        }
        if (!in.lens()) {
            return EndgameCode.NO_LENS;
        }
        if (!in.running()) {
            return EndgameCode.STOPPED;
        }
        if (!in.redstoneSatisfied()) {
            return EndgameCode.REDSTONE_BLOCKED;
        }
        return in.admitted() ? EndgameCode.OK : EndgameCode.ACTIVE_LIMIT;
    }

    /**
     * @param station   {@code OK} or the code that keeps the drill from operating at its position
     * @param structure {@code OK}, {@code UNFORMED} or {@code STRUCTURE_UNLOADED}
     * @param orbitBody the station's live orbit body, re-read for every operation; empty when it is unavailable
     */
    public record Inputs(boolean owned, boolean systemEnabled, EndgameCode station, EndgameCode structure, boolean lens,
                         boolean running, boolean redstoneSatisfied, boolean admitted,
                         Optional<CelestialBodyDefinition> orbitBody, LaserDrillTables tables, long energy, long cost,
                         long seed, long index) {
        public Inputs {
            Objects.requireNonNull(station, "station");
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(orbitBody, "orbitBody");
            Objects.requireNonNull(tables, "tables");
            if (cost < 0 || energy < 0 || index < 0) {
                throw new IllegalArgumentException("Energy, cost and the operation index are not negative");
            }
        }
    }

    public record Decision(EndgameCode code, Optional<LaserDrillTable> table, Optional<LaserDrillTable.Entry> stack) {
        public Decision {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(table, "table");
            Objects.requireNonNull(stack, "stack");
            if (code == EndgameCode.OK && (table.isEmpty() || stack.isEmpty())) {
                throw new IllegalArgumentException("An operation has a table and a stack");
            }
        }

        static Decision stop(EndgameCode code) {
            return new Decision(code, Optional.empty(), Optional.empty());
        }

        public boolean operates() {
            return code == EndgameCode.OK;
        }
    }
}
