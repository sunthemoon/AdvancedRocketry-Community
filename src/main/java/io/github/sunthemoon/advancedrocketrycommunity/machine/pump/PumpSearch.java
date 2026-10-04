package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Transient, breadth-first, loaded-only search. No Minecraft or registry dependency. */
public final class PumpSearch {
    public record Point(int x, int y, int z) {
        public Point offset(int dx, int dy, int dz) {
            return new Point(Math.addExact(x, dx), Math.addExact(y, dy), Math.addExact(z, dz));
        }
    }
    public enum Kind { AIR, OTHER, FLUID, UNSUPPORTED, UNLOADED, OUT_OF_BOUNDS }
    public record Cell(Kind kind, String fluid, boolean source) {
        public Cell {
            Objects.requireNonNull(kind);
            if (kind == Kind.FLUID) {
                if (fluid == null) { throw new IllegalArgumentException("A fluid cell requires a canonical ID"); }
                // The framework's pure value codec freezes both canonical grammar and the 128-character bound.
                new ProcessResourceKey(ProcessResourceKind.FLUID, "pump", fluid);
            }
        }
        public static Cell of(Kind kind) { return new Cell(kind, null, false); }
    }
    @FunctionalInterface public interface View { Cell inspect(Point position); }
    public record Result(PumpCode code, Point candidate, int inspected) { }
    private static final int[][] NEIGHBOURS = {{0,-1,0}, {0,0,-1}, {1,0,0}, {0,0,1}, {-1,0,0}, {0,1,0}};
    private final Point origin;
    private final ArrayDeque<Point> queue = new ArrayDeque<>();
    private final Set<Point> seen = new HashSet<>();
    private int down = 1;
    private boolean seeded;
    private boolean limited;
    private String fluid;
    private Point candidate;
    private PumpCode stopped;
    private int totalInspected;

    public PumpSearch(Point origin) { this.origin = Objects.requireNonNull(origin); }

    public Result advance(View view) {
        Objects.requireNonNull(view);
        if (stopped != null) { return new Result(stopped, null, 0); }
        if (candidate != null) { return new Result(PumpCode.SOURCE_READY, candidate, 0); }
        int inspected = 0;
        while (inspected < PumpBudget.PER_TICK) {
            Point point;
            if (!seeded) {
                if (down > PumpBudget.DOWN) { return stop(PumpCode.NO_FLUID, inspected); }
                try { point = origin.offset(0, -down++, 0); }
                catch (ArithmeticException outsideIntegerWorld) { return stop(PumpCode.TARGET_OUT_OF_BOUNDS, inspected); }
                seen.add(point);
            } else {
                if (queue.isEmpty()) { return stop(limited ? PumpCode.SEARCH_LIMIT : PumpCode.SEARCH_EXHAUSTED, inspected); }
                point = queue.removeFirst();
            }
            Cell cell = Objects.requireNonNull(view.inspect(point));
            inspected++;
            totalInspected++;
            if (cell.kind() == Kind.UNLOADED) { return stop(PumpCode.TARGET_UNLOADED, inspected); }
            if (cell.kind() == Kind.OUT_OF_BOUNDS) { return stop(PumpCode.TARGET_OUT_OF_BOUNDS, inspected); }
            if (!seeded) {
                if (cell.kind() == Kind.AIR) { continue; }
                if (cell.kind() != Kind.FLUID) {
                    return stop(cell.kind() == Kind.UNSUPPORTED ? PumpCode.SOURCE_UNSUPPORTED : PumpCode.NO_FLUID, inspected);
                }
                seeded = true;
                fluid = cell.fluid();
            }
            if (cell.kind() == Kind.FLUID && fluid.equals(cell.fluid())) {
                enqueueNeighbours(point);
                if (cell.source()) {
                    candidate = point;
                    return new Result(PumpCode.SOURCE_READY, candidate, inspected);
                }
            }
        }
        return new Result(PumpCode.SEARCHING, null, inspected);
    }

    /** Called only after successful authoritative source removal and resource publication. */
    public void drained() {
        if (candidate == null) { throw new IllegalStateException("No pump candidate"); }
        candidate = null;
    }
    public String fluid() { return fluid; }
    public int totalInspected() { return totalInspected; }
    public int reserved() { return seen.size(); }
    public boolean inside(Point point) {
        return Math.abs((long) point.x() - origin.x()) <= PumpBudget.HORIZONTAL
                && Math.abs((long) point.z() - origin.z()) <= PumpBudget.HORIZONTAL
                && (long) point.y() >= (long) origin.y() - PumpBudget.DOWN && point.y() < origin.y();
    }
    private Result stop(PumpCode code, int inspected) {
        stopped = code;
        queue.clear();
        return new Result(code, null, inspected);
    }
    private void enqueueNeighbours(Point point) {
        for (int[] delta : NEIGHBOURS) {
            Point next;
            try { next = point.offset(delta[0], delta[1], delta[2]); }
            catch (ArithmeticException outsideIntegerWorld) { continue; }
            if (!inside(next) || seen.contains(next)) { continue; }
            if (seen.size() >= PumpBudget.SEARCH) { limited = true; continue; }
            seen.add(next);
            queue.addLast(next);
        }
    }
}
