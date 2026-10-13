package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import java.util.ArrayList;
import java.util.List;

/** Pure bounded voxel DDA. Distances are in blocks, including zero-length boundary cells. */
public final class LaserRay {
    public static final double RANGE = 50;
    public static final int MAX_CELLS = 160;

    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Cell(int x, int y, int z, double enter, double exit) {
        public int chunkX() { return Math.floorDiv(x, 16); }
        public int chunkZ() { return Math.floorDiv(z, 16); }
    }

    private LaserRay() { }

    public static List<Cell> cells(Point start, Point direction, double range) {
        if (!start.finite() || !direction.finite() || !Double.isFinite(range) || range <= 0 || range > RANGE
                || Math.abs(start.x()) > 30_000_000 || Math.abs(start.y()) > 30_000_000
                || Math.abs(start.z()) > 30_000_000) { return List.of(); }
        double norm = Math.hypot(Math.hypot(direction.x(), direction.y()), direction.z());
        if (!Double.isFinite(norm) || norm < 1.0E-12) { return List.of(); }
        double[] origin = {start.x(), start.y(), start.z()};
        double[] ray = {direction.x() / norm, direction.y() / norm, direction.z() / norm};
        int[] cell = {(int) Math.floor(start.x()), (int) Math.floor(start.y()), (int) Math.floor(start.z())};
        int[] step = new int[3];
        double[] next = new double[3];
        double[] stride = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            step[axis] = ray[axis] > 0 ? 1 : ray[axis] < 0 ? -1 : 0;
            stride[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / ray[axis]);
            next[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY
                    : ((step[axis] > 0 ? cell[axis] + 1 : cell[axis]) - origin[axis]) / ray[axis];
        }
        List<Cell> result = new ArrayList<>();
        double enter = 0;
        for (int count = 0; count < MAX_CELLS; count++) {
            int axis = next[0] <= next[1] && next[0] <= next[2] ? 0 : next[1] <= next[2] ? 1 : 2;
            double exit = Math.max(0, Math.min(range, next[axis]));
            result.add(new Cell(cell[0], cell[1], cell[2], enter, exit));
            if (exit >= range) { return List.copyOf(result); }
            enter = exit;
            cell[axis] += step[axis];
            next[axis] += stride[axis];
        }
        // Never return a truncated ray as an apparently complete successful trace.
        return List.of();
    }
}
