package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidatesTest.pos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.*;

final class BayExitSafetyTest {
    private static final Cell SAFE = new Cell(false, Support.FULL_STABLE, Hazard.NONE, false);
    private static final Box BOX = new Box(pos(.5, 64, .5), .6, 1.8);

    static final class World implements LoadedState {
        Bounds bounds = new Bounds(-64, 320, -1000, 1000, -1000, 1000);
        Map<String, Cell> cells = new HashMap<>();
        Set<String> missing = new HashSet<>();
        Set<String> checked = new HashSet<>();
        int reads;
        int entities = 1;
        boolean incompleteEntities;
        public String level() { return "test:level"; }
        public Bounds bounds() { return bounds; }
        public boolean loadedColumn(int x, int z) { checked.add(x + ":" + z); return !missing.contains(x + ":" + z); }
        public Snapshot readWindow(Box box, Window window) {
            return (x, y, z) -> {
                assertTrue(window.contains(x, y, z));
                assertTrue(checked.contains(Math.floorDiv(x, 16) + ":" + Math.floorDiv(z, 16)));
                reads++;
                return cells.getOrDefault(x + ":" + y + ":" + z, SAFE);
            };
        }
        public boolean entitiesClear(Box box, BayExitBudget.Probe probe) {
            for (int n = 0; n < entities; n++) { if (!probe.visit()) { return false; } }
            return !incompleteEntities;
        }
    }

    private Verdict inspect(World world, Box box, BayExitBudget budget) {
        try (var probe = budget.openProbe().orElseThrow()) {
            return BayExitSafety.loaded(world).inspect(box, probe, (b, p) -> true);
        }
    }

    @Test void safeBoxUsesTheWholeFiniteLoadedWindow() {
        var world = new World(); var budget = new BayExitBudget(0);
        assertEquals(Verdict.SAFE, inspect(world, BOX, budget));
        assertEquals(45, world.reads);
        assertEquals(new BayExitBudget.Usage(1, 45, 1), budget.usage());
        var maximum = new World(); var maximumBudget = new BayExitBudget(0);
        assertEquals(Verdict.SAFE, inspect(maximum, new Box(pos(1, 64.2, 1), 1, 2), maximumBudget));
        assertEquals(4 * 5 * 4, maximum.reads, "x/z [-1,3), y [63,68): enclosing window is 80, below the 96 ceiling");
        assertEquals(new BayExitBudget.Usage(1, 80, 1), maximumBudget.usage());
    }

    @ParameterizedTest @EnumSource(value = Hazard.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void everyHazardRefusesInBodySupportAndCollar(Hazard hazard) {
        for (String position : new String[]{"0:64:0", "0:63:0", "-1:62:-1"}) {
            var world = new World();
            world.cells.put(position, new Cell(false, Support.FULL_STABLE, hazard, false));
            assertEquals(Verdict.UNSAFE, inspect(world, BOX, new BayExitBudget(0)), position + " " + hazard);
        }
    }

    @ParameterizedTest @EnumSource(value = Support.class, names = "FULL_STABLE", mode = EnumSource.Mode.EXCLUDE)
    void everyUnstableOrUncertainSupportRefuses(Support support) {
        var world = new World();
        world.cells.put("0:63:0", new Cell(false, support, Hazard.NONE, false));
        assertEquals(Verdict.UNSAFE, inspect(world, BOX, new BayExitBudget(0)));
    }

    @Test void fluidsCollisionAndUnknownObservationsRefuse() {
        for (String position : new String[]{"0:64:0", "0:63:0", "-1:62:-1"}) {
            var world = new World();
            world.cells.put(position, new Cell(false, Support.FULL_STABLE, Hazard.NONE, true));
            assertEquals(Verdict.UNSAFE, inspect(world, BOX, new BayExitBudget(0)));
        }
        var collision = new World(); collision.cells.put("0:65:0", new Cell(true, Support.FULL_STABLE, Hazard.NONE, false));
        assertEquals(Verdict.UNSAFE, inspect(collision, BOX, new BayExitBudget(0)));
        var unknown = new World(); unknown.cells.put("0:64:0", null);
        assertEquals(Verdict.UNPROVED, inspect(unknown, BOX, new BayExitBudget(0)));
        var footprint = new World(); footprint.cells.put("1:63:0", new Cell(false, Support.PARTIAL, Hazard.NONE, false));
        assertEquals(Verdict.UNSAFE, inspect(footprint, new Box(pos(1, 64, .5), 1, 2), new BayExitBudget(0)));
    }

    @Test void fullWindowMustFitBuildHeightAndBorderBeforeAnyRead() {
        for (Bounds bounds : new Bounds[]{new Bounds(63, 320, -1000, 1000, -1000, 1000),
                new Bounds(-64, 66, -1000, 1000, -1000, 1000),
                new Bounds(-64, 320, 0, 1000, -1000, 1000), new Bounds(-64, 320, -1000, 1, -1000, 1000),
                new Bounds(-64, 320, -1000, 1000, 0, 1000), new Bounds(-64, 320, -1000, 1000, -1000, 1)}) {
            var world = new World(); world.bounds = bounds;
            assertEquals(Verdict.UNSAFE, inspect(world, BOX, new BayExitBudget(0)));
            assertEquals(0, world.reads); assertTrue(world.checked.isEmpty());
        }
    }

    @Test void unloadedCollarNeighborRefusesBeforeAnyStateReadEvenAtNegativeChunkEdges() {
        for (double x : new double[]{15.5, -.5}) {
            var world = new World(); world.missing.add(x > 0 ? "1:0" : "0:0");
            assertEquals(Verdict.UNPROVED, inspect(world, new Box(pos(x, 64, 8), .6, 1.8), new BayExitBudget(0)));
            assertEquals(0, world.reads);
        }
    }

    @Test void largerNonfiniteAndForeignLevelBoxesNeverRead() {
        for (Box box : new Box[]{new Box(pos(0, 64, 0), 1.01, 2), new Box(pos(0, 64, 0), 1, 2.01),
                new Box(pos(0, 64, 0), 0, 2), new Box(pos(0, 64, 0), .6, Double.NaN),
                new Box(pos(Double.NaN, 64, 0), .6, 1.8), new Box(pos(Double.MAX_VALUE, 64, 0), .6, 1.8),
                new Box(new Position("test:other", 0, 64, 0), .6, 1.8)}) {
            var world = new World();
            assertEquals(Verdict.UNSAFE, inspect(world, box, new BayExitBudget(0)));
            assertEquals(0, world.reads);
        }
    }

    @Test void insufficientWindowAllowanceAndIncompleteEntityTraversalCannotProveSafety() {
        var budget = new BayExitBudget(0);
        for (int n = 0; n < 42; n++) {
            try (var probe = budget.openProbe().orElseThrow()) { assertTrue(probe.reserveReads(96)); }
        }
        var world = new World();
        assertEquals(Verdict.UNPROVED, inspect(world, new Box(pos(1, 64.2, 1), 1, 2), budget));
        assertEquals(0, world.reads); assertEquals(4032, budget.usage().reads());
        var crowded = new World(); crowded.entities = 17; var entityBudget = new BayExitBudget(0);
        assertEquals(Verdict.UNPROVED, inspect(crowded, BOX, entityBudget));
        assertEquals(16, entityBudget.usage().visits());
        var incomplete = new World(); incomplete.incompleteEntities = true;
        assertNotEquals(Verdict.SAFE, inspect(incomplete, BOX, new BayExitBudget(0)));
    }

    @Test void offThreadAndClosedProbesRefuseBeforeAnyLoadedColumnLookup() throws InterruptedException {
        var world = new World(); var budget = new BayExitBudget(0); var probe = budget.openProbe().orElseThrow();
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try { BayExitSafety.loaded(world).inspect(BOX, probe, (box, p) -> true); }
            catch (Throwable error) { failure.set(error); }
        });
        thread.start(); thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        probe.close();
        assertThrows(IllegalStateException.class, () -> BayExitSafety.loaded(world).inspect(BOX, probe, (box, p) -> true));
        assertEquals(0, world.reads); assertTrue(world.checked.isEmpty());
    }
}
