package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternObservation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternWorldView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ADR-065 section 9: the one-layer 3-wide, 22-deep array. The front row is plug, controller, plug; the 63 cells behind
 * it accept a panel, AIR or a block in {@link #CELL_TAG}. The v1.2 pattern schema caps every axis at 16, so this fixed
 * layout is evaluated here with the kernel's own matchers, transform and loaded-only world view instead of a datapack
 * definition. One evaluation inspects exactly {@link #CELL_COUNT} cells and never loads a chunk.
 */
public final class SolarArrayLayout {
    public static final int WIDTH = 3;
    public static final int DEPTH = 22;
    public static final int CELL_COUNT = WIDTH * DEPTH;
    public static final String OUTPUT_CHANNEL = "energy_output";
    public static final String CELL_TAG = "advancedrocketrycommunity:solar_array_cells";
    public static final String PANEL = "advancedrocketrycommunity:solar_array_panel";
    /** The controller's local cell; the layout extends toward local +Z, behind the controller's front face. */
    public static final PatternPosition ANCHOR = new PatternPosition(1, 0, 0);
    private static final List<Cell> CELLS = buildCells();

    public record Cell(PatternPosition local, PatternMatcher matcher) {
        public Cell { Objects.requireNonNull(local, "local"); Objects.requireNonNull(matcher, "matcher"); }
        public boolean collector() { return local.z() > 0; }
    }

    public enum Status { FORMED, UNLOADED, MISMATCH, NO_COLLECTORS }

    /** Outputs are the two plug positions in layout order; collectors counts panels only. */
    public record Evaluation(Status status, int inspected, int collectors, List<PatternPosition> outputs) {
        public Evaluation {
            Objects.requireNonNull(status, "status");
            outputs = List.copyOf(outputs);
            if (inspected < 0 || inspected > CELL_COUNT || collectors < 0 || collectors > SolarGeneration.MAX_COLLECTORS
                    || outputs.size() > 2 || status == Status.FORMED && (collectors < 1 || outputs.size() != 2)) {
                throw new IllegalArgumentException("Invalid solar array evaluation");
            }
        }
        public boolean formed() { return status == Status.FORMED; }
    }

    public static List<Cell> cells() { return CELLS; }

    /** Four horizontal rotations only; the array has no mirrored form. */
    public static List<PatternPosition> footprint(PatternTransform transform, PatternPosition controller) {
        requireUnmirrored(transform);
        List<PatternPosition> positions = new ArrayList<>(CELL_COUNT);
        for (Cell cell : CELLS) { positions.add(transform.localToWorld(cell.local(), ANCHOR, controller)); }
        return List.copyOf(positions);
    }

    /** Unloaded takes precedence over a mismatch, as in the kernel validator: nothing is known about an unloaded cell. */
    public static Evaluation evaluate(PatternTransform transform, PatternPosition controller, PatternWorldView world) {
        requireUnmirrored(transform);
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(world, "world");
        boolean unloaded = false, mismatch = false;
        int inspected = 0, collectors = 0;
        List<PatternPosition> outputs = new ArrayList<>(2);
        for (Cell cell : CELLS) {
            PatternPosition position;
            try { position = transform.localToWorld(cell.local(), ANCHOR, controller); }
            catch (ArithmeticException overflow) { return new Evaluation(Status.MISMATCH, inspected, 0, List.of()); }
            PatternObservation observation = world.observe(position);
            inspected++;
            if (!observation.loaded()) { unloaded = true; continue; }
            PatternBlock block = observation.block().orElseThrow();
            if (!cell.matcher().matches(block)) { mismatch = true; continue; }
            if (cell.matcher() instanceof PatternMatcher.Port) { outputs.add(position); }
            else if (cell.collector() && block.blockId().equals(PANEL)) { collectors++; }
        }
        if (unloaded) { return new Evaluation(Status.UNLOADED, inspected, 0, List.of()); }
        if (mismatch) { return new Evaluation(Status.MISMATCH, inspected, 0, List.of()); }
        if (collectors == 0) { return new Evaluation(Status.NO_COLLECTORS, inspected, 0, List.of()); }
        return new Evaluation(Status.FORMED, inspected, collectors, outputs);
    }

    private static void requireUnmirrored(PatternTransform transform) {
        if (Objects.requireNonNull(transform, "transform").mirroredLocalX()) {
            throw new IllegalArgumentException("The solar array has no mirrored form");
        }
    }

    private static List<Cell> buildCells() {
        List<Cell> cells = new ArrayList<>(CELL_COUNT);
        cells.add(new Cell(new PatternPosition(0, 0, 0), new PatternMatcher.Port(OUTPUT_CHANNEL)));
        cells.add(new Cell(ANCHOR, new PatternMatcher.Controller()));
        cells.add(new Cell(new PatternPosition(2, 0, 0), new PatternMatcher.Port(OUTPUT_CHANNEL)));
        PatternMatcher collector = new PatternMatcher.OptionalCell(new PatternMatcher.BlockTag(CELL_TAG));
        for (int z = 1; z < DEPTH; z++) {
            for (int x = 0; x < WIDTH; x++) { cells.add(new Cell(new PatternPosition(x, 0, z), collector)); }
        }
        return List.copyOf(cells);
    }

    private SolarArrayLayout() { }
}
