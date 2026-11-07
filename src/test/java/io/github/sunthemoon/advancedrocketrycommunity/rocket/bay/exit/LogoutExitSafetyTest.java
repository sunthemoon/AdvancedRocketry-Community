package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidatesTest.pos;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.LogoutExitSafety.Proof;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** W312F (R375): the logout save's own proof over a small block grid that models collision, support and hazards. */
final class LogoutExitSafetyTest {
    /** The exit of a rocket standing at (0.5, 64, 0.5): its x is on a block edge, its z is centred, its feet are whole. */
    private static final Box EXIT = new Box(pos(2.0, 64, .5), .6, 1.8);

    enum Kind { AIR, SOLID, SLAB, MAGMA, WATER, UNKNOWN }

    static final class Grid implements LogoutExitSafety.Region {
        Bounds bounds = new Bounds(-64, 320, -1000, 1000, -1000, 1000);
        final Map<String, Kind> kinds = new HashMap<>();
        final Set<String> missing = new HashSet<>();
        final List<double[]> entities = new ArrayList<>();
        Window region;
        int regions, regionReads;
        boolean readFails;
        /** W312F2: the multiplier that scales a landing; 1 g unless a test sets another (or none). */
        OptionalDouble gravity = OptionalDouble.of(1.0);
        final List<Query> queries = new ArrayList<>();
        record Query(Box exact, int drop) { }

        public String level() { return "test:level"; }
        public OptionalDouble fallMultiplier(Box exact, int drop) { queries.add(new Query(exact, drop)); return gravity; }
        public Bounds bounds() { return bounds; }
        public boolean loadedColumn(int x, int z) { return !missing.contains(x + ":" + z); }
        public boolean read(Window window) {
            regions++; regionReads += window.volume(); region = window;
            return !readFails;
        }
        public Snapshot readWindow(Box box, Window window) {
            assertNotNull(region, "A window was read before the paid region");
            assertTrue(window.minX() >= region.minX() && window.minY() >= region.minY() && window.minZ() >= region.minZ()
                    && window.maxX() <= region.maxX() && window.maxY() <= region.maxY() && window.maxZ() <= region.maxZ(),
                    "A window outside the paid region: " + window + " of " + region);
            return (x, y, z) -> window.contains(x, y, z) ? cell(box, x, y, z) : null;
        }
        public boolean entitiesClear(Box box, BayExitBudget.Probe probe) {
            for (double[] e : entities) {
                if (!probe.visit()) { return false; }
                if (e[0] > box.minX() && e[0] < box.maxX() && e[1] >= box.minY() && e[1] < box.maxY()
                        && e[2] > box.minZ() && e[2] < box.maxZ()) { return false; }
            }
            return true;
        }
        Grid fill(int y, Kind kind) {
            for (int x = -2; x <= 5; x++) { for (int z = -2; z <= 3; z++) { set(x, y, z, kind); } }
            return this;
        }
        Grid floor(int y) { return fill(y, Kind.SOLID); }
        Grid set(int x, int y, int z, Kind kind) { kinds.put(x + ":" + y + ":" + z, kind); return this; }
        /** The adapter's per-cell rules: shape collision with the body, flush support only, hazard and fluid. */
        Cell cell(Box box, int x, int y, int z) {
            Kind kind = kinds.getOrDefault(x + ":" + y + ":" + z, Kind.AIR);
            boolean shaped = kind == Kind.SOLID || kind == Kind.SLAB || kind == Kind.MAGMA;
            double top = kind == Kind.SLAB ? y + .5 : y + 1;
            boolean collision = shaped && x < box.maxX() && x + 1 > box.minX() && z < box.maxZ() && z + 1 > box.minZ()
                    && y < box.maxY() - 1e-7 && top > box.minY() + 1e-7;
            Support support = !shaped ? Support.NONE : kind == Kind.SLAB ? Support.PARTIAL : Support.FULL_STABLE;
            if (Math.abs(box.minY() - (y + 1.0)) > .01) { support = Support.NONE; }
            return switch (kind) {
                case UNKNOWN -> new Cell(true, Support.UNKNOWN, Hazard.UNKNOWN, false);
                case MAGMA -> new Cell(collision, support, Hazard.MAGMA, false);
                case WATER -> new Cell(false, Support.NONE, Hazard.NONE, true);
                default -> new Cell(collision, support, Hazard.NONE, false);
            };
        }
    }

    private static Proof inspect(Grid grid, Box box, BayExitBudget budget) {
        try (var probe = budget.openProbe().orElseThrow()) { return LogoutExitSafety.inspect(grid, box, probe); }
    }
    private static Proof inspect(Grid grid) { return inspect(grid, EXIT, new BayExitBudget(0)); }
    /** The bay exit selector's proof of one box, over the region the logout proof already read. */
    private static Verdict shared(Grid grid, Box box) {
        try (var probe = new BayExitBudget(0).openProbe().orElseThrow()) {
            return BayExitSafety.loaded(grid).inspect(box, probe, (b, p) -> true);
        }
    }
    private static Proof unsafe() { return new Proof(Verdict.UNSAFE, -1); }

    @Test void supportedExitNeedsNoFallAndThePaidRegionIsReadOnce() {
        var grid = new Grid().floor(63); var budget = new BayExitBudget(0);
        assertEquals(new Proof(Verdict.SAFE, 0), inspect(grid, EXIT, budget));
        assertEquals(1, grid.regions);
        assertEquals(4 * 8 * 3, grid.regionReads, "x [0,4), y [59,67), z [-1,2): the three-block fall's window");
        assertEquals(new BayExitBudget.Usage(1, 96, 0), budget.usage(), "One shared candidate pays for every world read");
        assertEquals(Verdict.SAFE, shared(grid, EXIT));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void theExitSettlesUpToThreeBlocksOntoTheFirstStableSupport(int drop) {
        var grid = new Grid().floor(63 - drop);
        assertEquals(new Proof(Verdict.SAFE, drop), inspect(grid));
        assertEquals(Verdict.UNSAFE, shared(grid, EXIT), "Only the fall makes this exact exit safe");
        assertEquals(Verdict.SAFE, shared(grid, LogoutExitSafety.shifted(EXIT, drop)));
    }

    @Test void aFourBlockFallOrNoSupportIsUnsafe() {
        assertEquals(unsafe(), inspect(new Grid().floor(59)));
        assertEquals(unsafe(), inspect(new Grid()));
    }

    @Test void theFirstSupportWinsOverALowerFloor() {
        assertEquals(new Proof(Verdict.SAFE, 1), inspect(new Grid().floor(62).floor(60)));
    }

    @Test void anythingSolidOnTheWayDownRefusesEveryDeeperLanding() {
        var half = new Grid().floor(60).set(1, 63, 0, Kind.SOLID);
        assertEquals(unsafe(), inspect(half), "A half-supported exit is not proved, and the fall cannot pass the block");
        var slab = new Grid().floor(60).set(2, 62, 0, Kind.SLAB);
        assertEquals(unsafe(), inspect(slab), "A slab in the fall is neither passable nor stable support");
        assertEquals(unsafe(), inspect(new Grid().fill(62, Kind.SLAB)), "A slab landing is not stable support");
        var ceiling = new Grid().floor(60).set(3, 65, 0, Kind.SOLID);
        assertEquals(new Proof(Verdict.SAFE, 3), inspect(ceiling), "A block in the collar beside the head is no collision");
    }

    @Test void hazardsFluidsAndUnknownBlocksAnywhereInTheFallWindowRefuse() {
        // In the exact body, in the collar beside the fall, and below the landing support (the shared window's last row).
        for (int[] at : new int[][]{{2, 64, 0}, {0, 62, -1}, {3, 59, 1}}) {
            for (Kind kind : new Kind[]{Kind.MAGMA, Kind.WATER, Kind.UNKNOWN}) {
                var grid = new Grid().floor(60).set(at[0], at[1], at[2], kind);
                assertEquals(Verdict.UNSAFE, inspect(grid).verdict(), kind + " at " + at[0] + ":" + at[1] + ":" + at[2]);
            }
        }
        assertEquals(new Proof(Verdict.SAFE, 3), inspect(new Grid().floor(60)), "Control without the hazard");
        assertEquals(unsafe(), inspect(new Grid().fill(61, Kind.MAGMA)), "A hazardous landing support");
    }

    @Test void anEntityInTheFallOrOnTheLandingRefusesAndACrowdIsUnproved() {
        var fall = new Grid().floor(61); fall.entities.add(new double[]{2.0, 64.5, .5});
        assertEquals(unsafe(), inspect(fall), "An entity in the exact box, above the landing box");
        var landing = new Grid().floor(61); landing.entities.add(new double[]{2.0, 62.5, .5});
        assertEquals(unsafe(), inspect(landing));
        var crowd = new Grid().floor(61);
        for (int n = 0; n < BayExitBudget.VISITS_PER_CANDIDATE + 1; n++) { crowd.entities.add(new double[]{40, 64, 40}); }
        var budget = new BayExitBudget(0);
        assertEquals(new Proof(Verdict.UNPROVED, -1), inspect(crowd, EXIT, budget));
        assertEquals(BayExitBudget.VISITS_PER_CANDIDATE, budget.usage().visits(), "The shared probe caps the visits");
    }

    @Test void deepFallsThatDoNotFitOneCandidateStayUnprovedButShallowOnesAreProved() {
        // A box over both an x and a z block edge needs 4 x 4 columns: only a one-block fall fits 96 reads.
        var corner = new Box(pos(2.0, 64, 1.0), .6, 1.8);
        var shallow = new Grid().floor(62); var budget = new BayExitBudget(0);
        assertEquals(new Proof(Verdict.SAFE, 1), inspect(shallow, corner, budget));
        assertEquals(4 * 6 * 4, shallow.regionReads);
        assertEquals(unsafe(), inspect(new Grid().floor(61), corner, new BayExitBudget(0)));
    }

    @Test void theBuildFloorLimitsTheDeepestFallBeforeAnyRead() {
        var shallow = new Grid().floor(62); shallow.bounds = new Bounds(61, 320, -1000, 1000, -1000, 1000);
        assertEquals(new Proof(Verdict.SAFE, 1), inspect(shallow));
        var deep = new Grid().floor(61); deep.bounds = shallow.bounds;
        assertEquals(unsafe(), inspect(deep));
        var none = new Grid().floor(63); none.bounds = new Bounds(63, 320, -1000, 1000, -1000, 1000);
        assertEquals(unsafe(), inspect(none), "The shared window itself leaves the world, as in the shared proof");
        assertEquals(0, none.regions);
        var border = new Grid().floor(63); border.bounds = new Bounds(-64, 320, -1000, 3.5, -1000, 1000);
        assertEquals(unsafe(), inspect(border)); assertEquals(0, border.regions);
    }

    @Test void unloadedColumnsExhaustedReadsAndFailedReadsAreUnprovedWithoutStateReads() {
        var unloaded = new Grid().floor(63); unloaded.missing.add("0:-1");
        assertEquals(new Proof(Verdict.UNPROVED, -1), inspect(unloaded)); assertEquals(0, unloaded.regions);
        var budget = new BayExitBudget(0);
        for (int n = 0; n < 42; n++) {
            try (var probe = budget.openProbe().orElseThrow()) { assertTrue(probe.reserveReads(96)); }
        }
        var poor = new Grid().floor(63);
        assertEquals(new Proof(Verdict.UNPROVED, -1), inspect(poor, EXIT, budget)); assertEquals(0, poor.regions);
        var failing = new Grid().floor(63); failing.readFails = true;
        assertEquals(new Proof(Verdict.UNPROVED, -1), inspect(failing));
    }

    @Test void invalidForeignAndClosedInputsNeverRead() {
        for (Box box : new Box[]{new Box(pos(2, 64, .5), 1.01, 1.8), new Box(pos(2, 64, .5), .6, 2.01),
                new Box(pos(Double.NaN, 64, .5), .6, 1.8), new Box(pos(Double.MAX_VALUE, 64, .5), .6, 1.8),
                new Box(pos(Integer.MAX_VALUE - 5.0, 64, .5), .6, 1.8),
                new Box(new Position("test:other", 2, 64, .5), .6, 1.8)}) {
            var grid = new Grid().floor(63);
            assertEquals(unsafe(), inspect(grid, box, new BayExitBudget(0)), box.toString());
            assertEquals(0, grid.regions);
        }
        var probe = new BayExitBudget(0).openProbe().orElseThrow(); probe.close();
        assertThrows(IllegalStateException.class, () -> LogoutExitSafety.inspect(new Grid().floor(63), EXIT, probe));
    }

    // W312F2 (R378, review W312FR F1): the mod scales a landing distance by gravity before vanilla's damage-free three
    // blocks apply, so a fall is accepted only while drop x multiplier stays within them.

    @ParameterizedTest
    @CsvSource({"1, 3.0, true", "1, 3.05, false", "1, 4.0, false", "2, 1.5, true", "2, 1.55, false", "3, 1.0, true",
            "3, 1.000000001, false", "3, 0.165, true", "3, 0.0, true"})
    void aFallIsAcceptedOnlyWhileTheScaledLandingStaysDamageFree(int drop, double multiplier, boolean safe) {
        var grid = new Grid().floor(63 - drop); grid.gravity = OptionalDouble.of(multiplier);
        assertEquals(safe ? new Proof(Verdict.SAFE, drop) : unsafe(), inspect(grid));
        assertEquals(List.of(new Grid.Query(EXIT, drop)), grid.queries, "One question, about the fall to the first stable support");
    }

    @Test void twoGravityRefusesTheReviewProbesThreeBlockFallButKeepsAOneBlockFall() {
        // W312FR's probe: in a 2 g field a three-block fall lands at distance 6 and deals 3 health.
        var deep = new Grid().floor(60); deep.gravity = OptionalDouble.of(2.0);
        assertEquals(unsafe(), inspect(deep));
        var lower = new Grid().floor(61).floor(59); lower.gravity = OptionalDouble.of(2.0);
        assertEquals(unsafe(), inspect(lower), "A damaging first support is where the player lands; no deeper floor helps");
        var shallow = new Grid().floor(62); shallow.gravity = OptionalDouble.of(2.0);
        assertEquals(new Proof(Verdict.SAFE, 1), inspect(shallow));
    }

    @Test void lowGravityKeepsTheThreeBlockCeiling() {
        for (double multiplier : new double[]{0.0, 0.1, 0.5}) {
            var grid = new Grid().floor(59); grid.gravity = OptionalDouble.of(multiplier);
            assertEquals(unsafe(), inspect(grid), "A four-block fall at " + multiplier + " g");
            assertTrue(grid.queries.isEmpty(), "A fall beyond the ceiling never asks for gravity");
        }
    }

    @Test void anUnknownOrMalformedMultiplierAcceptsNoFallAndLeavesTheSupportedExitAlone() {
        var unknown = new ArrayList<OptionalDouble>(List.of(OptionalDouble.empty(), OptionalDouble.of(Double.NaN),
                OptionalDouble.of(-0.5), OptionalDouble.of(Double.POSITIVE_INFINITY)));
        unknown.add(null);
        for (var multiplier : unknown) {
            var fall = new Grid().floor(62); fall.gravity = multiplier;
            assertEquals(new Proof(Verdict.UNPROVED, -1), inspect(fall), String.valueOf(multiplier));
            var supported = new Grid().floor(63); supported.gravity = multiplier;
            assertEquals(new Proof(Verdict.SAFE, 0), inspect(supported), String.valueOf(multiplier));
            assertTrue(supported.queries.isEmpty(), "The shared SAFE case asks for no gravity");
        }
    }

    @Test void refusedColumnsAndLandingsAskForNoGravity() {
        var magma = new Grid().fill(61, Kind.MAGMA); var slab = new Grid().fill(62, Kind.SLAB);
        var unloaded = new Grid().floor(62); unloaded.missing.add("0:-1");
        for (var grid : List.of(magma, slab, unloaded)) {
            assertNotEquals(Verdict.SAFE, inspect(grid).verdict());
            assertTrue(grid.queries.isEmpty());
        }
    }

    @Test void withoutAFallTheVerdictIsTheSharedProofsVerdict() {
        var random = new Random(312L);
        Kind[] kinds = Kind.values();
        int falls = 0, exact = 0;
        for (int trial = 0; trial < 2_000; trial++) {
            var grid = new Grid();
            for (int x = 0; x < 4; x++) { for (int y = 59; y < 67; y++) { for (int z = -1; z < 2; z++) {
                if (random.nextInt(24) == 0) { grid.set(x, y, z, kinds[random.nextInt(kinds.length)]); }
            } } }
            if (random.nextBoolean()) { grid.floor(63 - random.nextInt(4)); }
            var proof = inspect(grid);
            var exactVerdict = shared(grid, EXIT);
            assertEquals(exactVerdict == Verdict.SAFE, proof.verdict() == Verdict.SAFE && proof.drop() == 0, "trial " + trial);
            if (proof.verdict() == Verdict.SAFE && proof.drop() > 0) {
                falls++;
                assertEquals(Verdict.SAFE, shared(grid, LogoutExitSafety.shifted(EXIT, proof.drop())), "trial " + trial);
                for (int shallower = 0; shallower < proof.drop(); shallower++) {
                    assertNotEquals(Verdict.SAFE, shared(grid, LogoutExitSafety.shifted(EXIT, shallower)), "trial " + trial);
                }
            }
            if (exactVerdict == Verdict.SAFE) { exact++; }
        }
        assertTrue(falls > 20 && exact > 20, "The seeded grids exercise both outcomes: falls=" + falls + " exact=" + exact);
    }
}
