package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Finite W230 candidate geometry. Endpoint authority and hint identity belong to the caller. */
public final class BayExitCandidates {
    private BayExitCandidates() { }

    public record Position(String level, double x, double y, double z) {
        public Position { x = x == 0 ? 0 : x; y = y == 0 ? 0 : y; z = z == 0 ? 0 : z; }
        public boolean finite() {
            return level != null && !level.isBlank() && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
        Position offset(double dx, double dy, double dz) { return new Position(level, x + dx, y + dy, z + dz); }
    }
    public record Anchor(String level, int x, int y, int z) { }

    public record Box(Position feet, double width, double height) {
        public boolean valid() {
            return feet.finite() && Double.isFinite(width) && width > 0 && width <= 1
                    && Double.isFinite(height) && height > 0 && height <= 2
                    && Double.isFinite(minX()) && Double.isFinite(maxX()) && Double.isFinite(maxY())
                    && Double.isFinite(minZ()) && Double.isFinite(maxZ())
                    && minX() < maxX() && minY() < maxY() && minZ() < maxZ();
        }
        public double minX() { return feet.x - width / 2; }
        public double maxX() { return feet.x + width / 2; }
        public double minY() { return feet.y; }
        public double maxY() { return feet.y + height; }
        public double minZ() { return feet.z - width / 2; }
        public double maxZ() { return feet.z + width / 2; }
        public boolean overlaps(Box other) {
            return feet.level.equals(other.feet.level) && minX() < other.maxX() && maxX() > other.minX()
                    && minY() < other.maxY() && maxY() > other.minY()
                    && minZ() < other.maxZ() && maxZ() > other.minZ();
        }
    }

    /** An absent anchor means unproved authority. Hints are never a safety proof and are never stored here. */
    public static List<Position> ordered(Position rocket, Optional<Position> hint, Optional<Anchor> anchor) {
        if (!rocket.finite()) { return List.of(); }
        var positions = new LinkedHashSet<Position>();
        hint.filter(p -> p.finite() && rocket.level.equals(p.level)).ifPresent(positions::add);
        positions.add(rocket.offset(2, 0, 0)); positions.add(rocket.offset(0, 0, 2));
        positions.add(rocket.offset(-2, 0, 0)); positions.add(rocket.offset(0, 0, -2));
        anchor.filter(a -> rocket.level.equals(a.level)).ifPresent(a -> {
            int[][] columns = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
            for (int[] column : columns) {
                for (int dy : new int[]{0, 1, -1}) {
                    positions.add(new Position(a.level, a.x + .5 + column[0], (double) a.y + dy, a.z + .5 + column[1]));
                }
            }
        });
        return List.copyOf(positions);
    }
}
