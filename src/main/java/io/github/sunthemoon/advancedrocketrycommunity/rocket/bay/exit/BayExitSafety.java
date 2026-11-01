package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box;

/** Loaded-state proof boundary. Unknown/incomplete observations are never a safe placement. */
public final class BayExitSafety {
    private BayExitSafety() { }

    public enum Verdict { SAFE, UNSAFE, UNPROVED }
    public enum Hazard {
        NONE, FIRE, SOUL_FIRE, CAMPFIRE, LAVA, CACTUS, MAGMA, BERRY_BUSH, POWDER_SNOW,
        WITHER_ROSE, POINTED_DRIPSTONE, PORTAL, GATEWAY, UNKNOWN
    }
    public enum Support { FULL_STABLE, NONE, PARTIAL, FALLING, MOVING_PISTON, OPEN_TRAPDOOR, UNKNOWN }

    /** Geometry proofs are relative to the exact standing box, not a cube/air approximation. */
    public record Cell(boolean intersectsBody, Support support, Hazard hazard, boolean fluid) { }
    public record Bounds(int minY, int maxY, double minX, double maxX, double minZ, double maxZ) {
        boolean contains(Window w) {
            return w.minY >= minY && w.maxY <= maxY && w.minX >= minX && w.maxX <= maxX
                    && w.minZ >= minZ && w.maxZ <= maxZ;
        }
    }

    /** Half-open integer window: body + support + a one-block collar, at most 96 cells. */
    public record Window(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
        int volume() { return (maxX - minX) * (maxY - minY) * (maxZ - minZ); }
    }

    @FunctionalInterface public interface Reservations {
        boolean clear(Box box, BayExitBudget.Probe probe);
    }
    @FunctionalInterface public interface Port {
        /** SAFE requires a successful reservation check for this exact box and probe. */
        Verdict inspect(Box box, BayExitBudget.Probe probe, Reservations reservations);
    }
    @FunctionalInterface public interface Snapshot {
        Cell cell(int x, int y, int z);
    }

    /**
     * S2 implements this with non-creating FULL/entity-loaded/ticking lookups, never a loading accessor.
     * readWindow takes at most one raw state read per cell in the already reserved window, then returns a
     * read-only snapshot. Shape/support proofs and neighbor lookups use ONLY that snapshot, with no further
     * world access; an unavailable/out-of-window dependency returns null, not air. Collision/support fields
     * must prove the exact box/footprint, not merely a block's default shape or the rocket's visual terrain.
     * entitiesClear must use a capped loaded-section visitor, calling probe.visit BEFORE every visited entity.
     * It returns false if traversal is incomplete. No unbounded list may be materialized and then truncated.
     */
    public interface LoadedState {
        String level();
        Bounds bounds();
        boolean loadedColumn(int chunkX, int chunkZ);
        Snapshot readWindow(Box box, Window window);
        boolean entitiesClear(Box box, BayExitBudget.Probe probe);
    }

    public static Port loaded(LoadedState state) {
        return (box, probe, reservations) -> inspect(state, box, probe, reservations);
    }

    private static Verdict inspect(LoadedState state, Box box, BayExitBudget.Probe probe, Reservations reservations) {
        probe.checkOpen();
        if (!box.valid() || !box.feet().level().equals(state.level())) { return Verdict.UNSAFE; }
        // Coordinates must leave room for the collar and int block positions, before any narrowing conversion.
        if (Math.abs(box.minX()) > Integer.MAX_VALUE - 4.0 || Math.abs(box.maxX()) > Integer.MAX_VALUE - 4.0
                || Math.abs(box.minZ()) > Integer.MAX_VALUE - 4.0 || Math.abs(box.maxZ()) > Integer.MAX_VALUE - 4.0
                || Math.abs(box.minY()) > Integer.MAX_VALUE - 4.0 || Math.abs(box.maxY()) > Integer.MAX_VALUE - 4.0) {
            return Verdict.UNSAFE;
        }
        int supportY = (int) Math.floor(Math.nextDown(box.minY()));
        var window = new Window((int) Math.floor(box.minX()) - 1, supportY - 1,
                (int) Math.floor(box.minZ()) - 1, (int) Math.ceil(box.maxX()) + 1,
                (int) Math.ceil(box.maxY()) + 1, (int) Math.ceil(box.maxZ()) + 1);
        Bounds bounds = state.bounds();
        if (bounds == null || !bounds.contains(window)) { return Verdict.UNSAFE; }
        for (int x = Math.floorDiv(window.minX, 16); x <= Math.floorDiv(window.maxX - 1, 16); x++) {
            for (int z = Math.floorDiv(window.minZ, 16); z <= Math.floorDiv(window.maxZ - 1, 16); z++) {
                if (!state.loadedColumn(x, z)) { return Verdict.UNPROVED; }
            }
        }
        if (!probe.reserveReads(window.volume())) { return Verdict.UNPROVED; }
        Snapshot snapshot = state.readWindow(box, window);
        if (snapshot == null) { return Verdict.UNPROVED; }
        // Inspect the entire reserved window even after finding a hazard. No hidden neighbor/world reads.
        boolean safe = true;
        for (int x = window.minX; x < window.maxX; x++) {
            for (int y = window.minY; y < window.maxY; y++) {
                for (int z = window.minZ; z < window.maxZ; z++) {
                    Cell cell = snapshot.cell(x, y, z);
                    if (cell == null || cell.support == null || cell.hazard == null) { return Verdict.UNPROVED; }
                    if (cell.fluid || cell.hazard != Hazard.NONE || cell.intersectsBody) { safe = false; }
                    boolean footprint = x < box.maxX() && x + 1 > box.minX()
                            && z < box.maxZ() && z + 1 > box.minZ();
                    if (y == supportY && footprint && cell.support != Support.FULL_STABLE) { safe = false; }
                }
            }
        }
        if (!safe) { return Verdict.UNSAFE; }
        if (!reservations.clear(box, probe) || !state.entitiesClear(box, probe)) {
            return probe.incomplete() ? Verdict.UNPROVED : Verdict.UNSAFE;
        }
        return probe.incomplete() ? Verdict.UNPROVED : Verdict.SAFE;
    }
}
