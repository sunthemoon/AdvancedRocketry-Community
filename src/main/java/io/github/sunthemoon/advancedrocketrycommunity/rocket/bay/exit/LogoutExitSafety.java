package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Position;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Bounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Cell;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Hazard;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.LoadedState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Snapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Verdict;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Window;
import java.util.OptionalDouble;

/**
 * W312F (R375): the logout save's own proof, built on {@link BayExitSafety}. It answers one question: would a player who
 * is saved at the exact dismount location be hurt there after logging in? It never chooses or moves a placement, and the
 * bay exit selector does not use it.
 *
 * <p>SAFE means that the exact standing box is SAFE under the shared proof, or that the player settles at most
 * {@link #MAX_DROP} whole blocks straight down onto the first stable support: every cell of the fall column's shared
 * window (body, support row and one-block collar) is free of collision with the falling body, fluid and hazards, the
 * landing box is SAFE under the shared proof, and the landing deals no fall damage. Vanilla deals none up to
 * {@link #DAMAGE_FREE_DISTANCE}, but this mod scales a landing distance by the gravity multiplier first (W312F2, R378), so
 * the fall times the largest multiplier from the exit down to the landing must stay within that distance. A fall whose
 * multiplier cannot be known is not accepted.
 *
 * <p>All world reads come from one window that the caller's shared probe pays for once, before any read. The landing
 * proofs then evaluate only that snapshot; their local allowance pays for no world access.
 */
public final class LogoutExitSafety {
    public static final int MAX_DROP = 3;
    /** {@code LivingEntity.calculateFallDamage}: no damage while the landing distance stays at or below three blocks. */
    public static final double DAMAGE_FREE_DISTANCE = 3.0D;

    private LogoutExitSafety() { }

    /** {@code drop} is the number of whole blocks the player settles after logging in, or -1 when not SAFE. */
    public record Proof(Verdict verdict, int drop) {
        static Proof refused(Verdict verdict) { return new Proof(verdict, -1); }
    }

    /** A loaded state that reads one region once; its readWindow then serves only windows inside that region. */
    public interface Region extends LoadedState {
        /** One raw state read per cell of an already reserved window; false if any column became unavailable. */
        boolean read(Window window);

        /**
         * W312F2 (R378): the largest gravity multiplier that can scale the player's landing anywhere from the exact feet
         * down to the feet {@code drop} blocks below, by the rules that scale it; empty when it cannot be known.
         */
        OptionalDouble fallMultiplier(Box exact, int drop);
    }

    public static Proof inspect(Region state, Box exact, BayExitBudget.Probe probe) {
        probe.checkOpen();
        if (!exact.valid() || !exact.feet().level().equals(state.level())) { return Proof.refused(Verdict.UNSAFE); }
        double limit = Integer.MAX_VALUE - 4.0 - MAX_DROP;
        if (Math.abs(exact.minX()) > limit || Math.abs(exact.maxX()) > limit || Math.abs(exact.minZ()) > limit
                || Math.abs(exact.maxZ()) > limit || Math.abs(exact.minY()) > limit || Math.abs(exact.maxY()) > limit) {
            return Proof.refused(Verdict.UNSAFE);
        }
        Bounds bounds = state.bounds();
        if (bounds == null) { return Proof.refused(Verdict.UNSAFE); }
        // The deepest fall whose window fits the build height, the world border and one candidate's read allowance.
        int deepest = -1;
        Window region = null;
        for (int drop = MAX_DROP; drop >= 0 && region == null; drop--) {
            var window = window(exact, drop);
            if (bounds.contains(window) && window.volume() <= BayExitBudget.READS_PER_CANDIDATE) { deepest = drop; region = window; }
        }
        if (region == null) { return Proof.refused(Verdict.UNSAFE); }
        for (int x = Math.floorDiv(region.minX(), 16); x <= Math.floorDiv(region.maxX() - 1, 16); x++) {
            for (int z = Math.floorDiv(region.minZ(), 16); z <= Math.floorDiv(region.maxZ() - 1, 16); z++) {
                if (!state.loadedColumn(x, z)) { return Proof.refused(Verdict.UNPROVED); }
            }
        }
        if (!probe.reserveReads(region.volume()) || !state.read(region)) { return Proof.refused(Verdict.UNPROVED); }
        // Each fall's column contains every shallower one, so the first SAFE landing is the first stable support, and
        // an obstructed or hazardous column refuses every deeper fall too.
        var evaluation = new BayExitBudget(0);
        for (int drop = 0; drop <= deepest; drop++) {
            if (drop > 0) {
                Verdict column = column(state, exact, drop, probe);
                if (column != Verdict.SAFE) { return Proof.refused(column); }
            }
            Verdict landing;
            try (var local = evaluation.openProbe().orElseThrow()) {
                landing = BayExitSafety.loaded(state).inspect(shifted(exact, drop), local, (box, work) -> true);
            }
            if (probe.incomplete() || landing == Verdict.UNPROVED) { return Proof.refused(Verdict.UNPROVED); }
            if (landing == Verdict.SAFE) { return drop == 0 ? new Proof(Verdict.SAFE, 0) : landed(state, exact, drop); }
        }
        return Proof.refused(Verdict.UNSAFE);
    }

    /**
     * W312F2: the first stable support is where the player lands, so a damaging landing refuses the fall outright. The
     * shared proof accepts a landing only with its feet on the support's whole-block top (its body may not reach into the
     * support), so the fall is at most {@code drop} blocks.
     */
    private static Proof landed(Region state, Box exact, int drop) {
        OptionalDouble multiplier = state.fallMultiplier(exact, drop);
        if (multiplier == null || multiplier.isEmpty() || !Double.isFinite(multiplier.getAsDouble())
                || multiplier.getAsDouble() < 0.0D) { return Proof.refused(Verdict.UNPROVED); }
        return drop * multiplier.getAsDouble() <= DAMAGE_FREE_DISTANCE
                ? new Proof(Verdict.SAFE, drop) : Proof.refused(Verdict.UNSAFE);
    }

    /** The fall from the exact feet down to the landing feet: the swept body and its whole shared window. */
    private static Verdict column(Region state, Box exact, int drop, BayExitBudget.Probe probe) {
        var fall = new Box(new Position(exact.feet().level(), exact.feet().x(), exact.feet().y() - drop, exact.feet().z()),
                exact.width(), exact.height() + drop);
        var window = window(exact, drop);
        Snapshot snapshot = state.readWindow(fall, window);
        if (snapshot == null) { return Verdict.UNPROVED; }
        boolean clear = true;
        for (int x = window.minX(); x < window.maxX(); x++) {
            for (int y = window.minY(); y < window.maxY(); y++) {
                for (int z = window.minZ(); z < window.maxZ(); z++) {
                    Cell cell = snapshot.cell(x, y, z);
                    if (cell == null || cell.support() == null || cell.hazard() == null) { return Verdict.UNPROVED; }
                    if (cell.fluid() || cell.hazard() != Hazard.NONE || cell.intersectsBody()) { clear = false; }
                }
            }
        }
        if (!clear) { return Verdict.UNSAFE; }
        if (!state.entitiesClear(fall, probe)) { return probe.incomplete() ? Verdict.UNPROVED : Verdict.UNSAFE; }
        return Verdict.SAFE;
    }

    static Box shifted(Box exact, int drop) {
        return new Box(new Position(exact.feet().level(), exact.feet().x(), exact.feet().y() - drop, exact.feet().z()),
                exact.width(), exact.height());
    }

    /**
     * The shared proof's window for a box whose feet are {@code drop} blocks below the exact feet and whose head stays at
     * the exact head. With {@code drop == 0} it is exactly the shared window of the exact box; otherwise it is the union
     * of the exact box's and the landing box's shared windows.
     */
    static Window window(Box exact, int drop) {
        int supportY = (int) Math.floor(Math.nextDown(exact.minY() - drop));
        return new Window((int) Math.floor(exact.minX()) - 1, supportY - 1, (int) Math.floor(exact.minZ()) - 1,
                (int) Math.ceil(exact.maxX()) + 1, (int) Math.ceil(exact.maxY()) + 1, (int) Math.ceil(exact.maxZ()) + 1);
    }
}
