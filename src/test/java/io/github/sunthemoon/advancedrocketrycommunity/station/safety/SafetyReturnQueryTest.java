package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-066 section 3.3: the fair resumable query's budgets, cursor, tie-break, restarts and outcomes. */
final class SafetyReturnQueryTest {
    private static final UUID PLAYER = new UUID(0L, 1L);

    @Test
    void theNearestSafeAuthorizedArrivalWinsNotTheFirstSafeOneNorTheFarthest() {
        FakeWorld world = new FakeWorld();
        UUID first = world.station(id(1), 4_000, 0, PLAYER);
        UUID farthest = world.station(id(2), 9_000, 0, PLAYER);
        UUID nearest = world.station(id(3), 600, 0, PLAYER);
        world.station(id(4), 2_000, 0, PLAYER);
        SafetyReturnQuery query = new SafetyReturnQuery();
        assertTrue(query.admit(PLAYER, 0.0D, 0.0D));
        SafetyReturnQuery.Result result = single(query.tick(world));
        SafetyReturnQuery.Station station = assertInstanceOf(SafetyReturnQuery.Station.class, result);
        assertEquals(nearest, station.target().stationId());
        assertTrue(world.probed.contains(first), "The first safe candidate was probed and then beaten");
        assertFalse(world.probed.contains(farthest), "A farther candidate is never probed");
        assertEquals(1, station.passes());
    }

    @Test
    void equalDistancesGoToTheLowerStationUuid() {
        FakeWorld world = new FakeWorld();
        UUID higher = world.station(id(9), 1_000, 0, PLAYER);
        UUID lower = world.station(id(2), -1_000, 0, PLAYER);
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(PLAYER, 0.5D, 0.5D);
        assertEquals(lower, station(single(query.tick(world))));
        assertTrue(higher.compareTo(lower) > 0);
        assertFalse(world.probed.contains(higher), "An equally distant higher UUID never replaces the best");
    }

    @Test
    void unauthorizedUnsafeAndUnloadedNearerArrivalsAreSkipped() {
        FakeWorld world = new FakeWorld();
        UUID stranger = world.station(id(1), 100, 0, new UUID(9L, 9L));
        UUID unloaded = world.station(id(2), 200, 0, PLAYER);
        world.unloaded.add(unloaded);
        UUID unsafe = world.station(id(3), 300, 0, PLAYER);
        world.unsafeStep.put(unsafe, 1);
        UUID allowed = world.station(id(4), 900, 0, PLAYER);
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(PLAYER, 0.0D, 0.0D);
        assertEquals(allowed, station(single(query.tick(world))));
        assertFalse(world.inspected.contains(stranger), "Another player's station is not a candidate");
        assertEquals(1, world.probeCalls(unloaded), "An unloaded arrival costs one probe and reads nothing");
        assertEquals(2, world.probeCalls(unsafe), "An unsafe feet cell stops the probe");
    }

    @Test
    void noSafeAuthorizedArrivalIsTheOverworldAndAnUnknownAuthorityIsARefusal() {
        FakeWorld empty = new FakeWorld();
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(PLAYER, 0.0D, 0.0D);
        assertInstanceOf(SafetyReturnQuery.Overworld.class, single(query.tick(empty)));

        FakeWorld unsafe = new FakeWorld();
        unsafe.unloaded.add(unsafe.station(id(1), 100, 0, PLAYER));
        query.admit(PLAYER, 0.0D, 0.0D);
        assertInstanceOf(SafetyReturnQuery.Overworld.class, single(query.tick(unsafe)));

        FakeWorld blocked = new FakeWorld();
        blocked.station(id(1), 100, 0, PLAYER);
        blocked.authority = false;
        query.admit(PLAYER, 0.0D, 0.0D);
        assertInstanceOf(SafetyReturnQuery.Refused.class, single(query.tick(blocked)),
                "A blocked registry is not an empty successful query");
        assertEquals(0, blocked.inspected.size());
    }

    @Test
    void oneJobInspectsAtMostThirtyTwoCandidatesPerTickWithAStableCursor() {
        FakeWorld world = new FakeWorld();
        for (int index = 0; index < 100; index++) {
            world.unloaded.add(world.station(id(100 + index), 10_000 - index * 10, 0, PLAYER));
        }
        UUID best = world.station(id(500), 20_000, 0, PLAYER);
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(PLAYER, 0.0D, 0.0D);
        int ticks = 0;
        List<SafetyReturnQuery.Result> results = List.of();
        while (results.isEmpty()) {
            results = query.tick(world);
            ticks++;
            assertTrue(query.lastTick().inspections() <= SafetyReturnQuery.INSPECTIONS_PER_JOB);
            assertTrue(ticks < 10, "The query never finished");
        }
        assertEquals(best, station(single(results)));
        assertEquals(4, ticks, "101 candidates, the end of the pass and the commit take four turns of 32");
        world.inspectCounts.forEach((station, count) -> {
            if (!station.equals(best)) {
                assertEquals(1, count, "Each candidate is inspected once in a pass");
            }
        });
    }

    @Test
    void manyJobsShareTheGlobalBudgetsFairly() {
        FakeWorld world = new FakeWorld();
        List<UUID> players = new ArrayList<>();
        for (int player = 0; player < 10; player++) {
            players.add(new UUID(1L, player));
        }
        // Every candidate is nearer than the last and safe, so each inspection also starts three probes.
        for (int index = 0; index < 60; index++) {
            world.station(id(1_000 + index), 60_000 - index * 100, 0, players.toArray(UUID[]::new));
        }
        SafetyReturnQuery query = new SafetyReturnQuery();
        players.forEach(player -> query.admit(player, 0.0D, 0.0D));
        Set<UUID> finished = new HashSet<>();
        int maxProbes = 0;
        for (int tick = 0; tick < 50 && finished.size() < players.size(); tick++) {
            world.tick = tick;
            for (SafetyReturnQuery.Result result : query.tick(world)) {
                assertTrue(finished.add(result.player()));
                assertEquals(id(1_059), station(result), "Every player finds the nearest");
            }
            SafetyReturnQuery.TickUse use = query.lastTick();
            assertTrue(use.inspections() <= SafetyReturnQuery.INSPECTIONS_PER_TICK, "inspections " + use);
            assertTrue(use.probes() <= SafetyReturnQuery.PROBES_PER_TICK, "probes " + use);
            assertTrue(use.maxJobInspections() <= SafetyReturnQuery.INSPECTIONS_PER_JOB, "job " + use);
            assertTrue(use.jobs() <= SafetyReturnQuery.MAX_JOBS);
            maxProbes = Math.max(maxProbes, use.probes());
        }
        assertEquals(players.size(), finished.size(), "Every job finished");
        assertTrue(maxProbes > SafetyReturnQuery.PROBES_PER_TICK - SafetyReturnQuery.PROBE_STEPS,
                "The probe budget was the limit in at least one tick: " + maxProbes);
        for (UUID player : players) {
            Set<Integer> commitTicks = new HashSet<>();
            for (int step = 0; step < SafetyReturnQuery.PROBE_STEPS; step++) {
                commitTicks.add(world.probeTicks.get(player + "/" + id(1_059) + "/" + step + "#2"));
            }
            assertEquals(1, commitTicks.size(), "The commit probes ran in one tick: " + commitTicks);
            assertFalse(commitTicks.contains(null));
        }
    }

    @Test
    void anInterruptedProbeResumesAtItsStep() {
        FakeWorld world = new FakeWorld();
        List<UUID> players = new ArrayList<>();
        for (int player = 0; player < 64; player++) {
            players.add(new UUID(2L, player));
        }
        for (int index = 0; index < 6; index++) {
            world.station(id(2_000 + index), 6_000 - index * 100, 0, players.toArray(UUID[]::new));
        }
        SafetyReturnQuery query = new SafetyReturnQuery();
        players.forEach(player -> assertTrue(query.admit(player, 0.0D, 0.0D)));
        int finished = 0;
        for (int tick = 0; tick < 50 && finished < players.size(); tick++) {
            finished += query.tick(world).size();
            assertTrue(query.lastTick().probes() <= SafetyReturnQuery.PROBES_PER_TICK);
        }
        assertEquals(players.size(), finished);
        for (UUID player : players) {
            for (int index = 0; index < 6; index++) {
                UUID station = id(2_000 + index);
                for (int step = 0; step < SafetyReturnQuery.PROBE_STEPS; step++) {
                    int expected = index == 5 ? 2 : 1;
                    assertEquals(expected, world.probeCalls(player, station, step),
                            "Each step runs once per pass (the winner once more at the commit)");
                }
            }
        }
    }

    @Test
    void aGenerationOrAccessChangeStartsAFreshPass() {
        FakeWorld world = new FakeWorld();
        for (int index = 0; index < 80; index++) {
            world.unloaded.add(world.station(id(3_000 + index), 50_000, 0, PLAYER));
        }
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(PLAYER, 0.0D, 0.0D);
        assertTrue(query.tick(world).isEmpty());
        UUID late = world.station(id(2_999), 700, 0, PLAYER);
        world.generation++;
        assertTrue(query.tick(world).isEmpty());
        assertEquals(2, world.inspectCounts.get(id(3_000)), "The new pass started at the first candidate again");
        List<SafetyReturnQuery.Result> results = List.of();
        for (int tick = 0; tick < 10 && results.isEmpty(); tick++) {
            results = query.tick(world);
        }
        assertEquals(late, station(single(results)));

        FakeWorld access = new FakeWorld();
        UUID foreign = access.station(id(10), 100, 0, new UUID(7L, 7L));
        access.station(id(11), 5_000, 0, PLAYER);
        for (int index = 0; index < 40; index++) {
            access.unloaded.add(access.station(id(100 + index), 9_000, 0, PLAYER));
        }
        SafetyReturnQuery operatorQuery = new SafetyReturnQuery();
        operatorQuery.admit(PLAYER, 0.0D, 0.0D);
        assertTrue(operatorQuery.tick(access).isEmpty());
        access.operators.add(PLAYER);
        List<SafetyReturnQuery.Result> operatorResults = List.of();
        for (int tick = 0; tick < 10 && operatorResults.isEmpty(); tick++) {
            operatorResults = operatorQuery.tick(access);
        }
        assertEquals(foreign, station(single(operatorResults)), "An operator uses the all-station index");
    }

    @Test
    void aBestThatChangesBeforeTheCommitIsReplacedByAFreshPass() {
        FakeWorld world = new FakeWorld();
        UUID nearest = world.station(id(1), 300, 0, PLAYER);
        UUID second = world.station(id(2), 800, 0, PLAYER);
        world.failCommitProbeOf = nearest;
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(PLAYER, 0.0D, 0.0D);
        assertEquals(second, station(single(query.tick(world))),
                "The nearest unloaded at the commit, so a fresh pass chose the next one");
    }

    @Test
    void jobsAreBoundedOnePerPlayerAndSixtyFourInTotal() {
        SafetyReturnQuery query = new SafetyReturnQuery();
        for (int player = 0; player < SafetyReturnQuery.MAX_JOBS; player++) {
            assertTrue(query.admit(new UUID(3L, player), 0.0D, 0.0D));
        }
        assertFalse(query.admit(new UUID(3L, 0L), 0.0D, 0.0D), "One job per player");
        assertFalse(query.admit(new UUID(4L, 0L), 0.0D, 0.0D), "At most 64 jobs");
        query.cancel(new UUID(3L, 5L));
        assertFalse(query.pending(new UUID(3L, 5L)));
        assertTrue(query.admit(new UUID(4L, 0L), 0.0D, 0.0D));
        assertEquals(SafetyReturnQuery.MAX_JOBS, query.size());
    }

    @Test
    void aJobTheBudgetStoppedIsServedFirstNextTick() {
        FakeWorld world = new FakeWorld();
        List<UUID> players = new ArrayList<>();
        for (int player = 0; player < 5; player++) {
            players.add(new UUID(5L, player));
        }
        for (int index = 0; index < 40; index++) {
            world.unloaded.add(world.station(id(4_000 + index), 1_000, 0, players.toArray(UUID[]::new)));
        }
        SafetyReturnQuery query = new SafetyReturnQuery();
        players.forEach(player -> query.admit(player, 0.0D, 0.0D));
        query.tick(world);
        assertEquals(128, query.lastTick().inspections());
        assertFalse(world.inspectedBy.containsKey(players.get(4)), "The fifth job got nothing in the first tick");
        world.inspectedBy.clear();
        query.tick(world);
        assertEquals(32, world.inspectedBy.get(players.get(4)), "It goes first in the next tick");
    }

    private static SafetyReturnQuery.Result single(List<SafetyReturnQuery.Result> results) {
        assertEquals(1, results.size(), "results " + results);
        return results.get(0);
    }

    private static UUID station(SafetyReturnQuery.Result result) {
        return assertInstanceOf(SafetyReturnQuery.Station.class, result).target().stationId();
    }

    private static UUID id(long low) {
        return new UUID(0x5afeL, low);
    }

    /** Stations with arrivals at y 128, members, loaded and safe unless listed; counts every read. */
    private static final class FakeWorld implements SafetyReturnQuery.World {
        final Map<UUID, SafetyReturnQuery.Target> stations = new HashMap<>();
        final Map<UUID, Set<UUID>> teams = new HashMap<>();
        final Set<UUID> unloaded = new HashSet<>();
        final Map<UUID, Integer> unsafeStep = new HashMap<>();
        final Set<UUID> operators = new HashSet<>();
        final Set<UUID> inspected = new HashSet<>();
        final Set<UUID> probed = new HashSet<>();
        final Map<UUID, Integer> inspectCounts = new HashMap<>();
        final Map<UUID, Integer> inspectedBy = new HashMap<>();
        final Map<String, Integer> probeCounts = new HashMap<>();
        /** The test tick of each probe call, keyed by player/station/step#call. */
        final Map<String, Integer> probeTicks = new HashMap<>();
        UUID failCommitProbeOf;
        boolean authority = true;
        long generation;
        int tick;
        private UUID current;

        UUID station(UUID id, int x, int z, UUID... members) {
            stations.put(id, new SafetyReturnQuery.Target(id, x, 128, z));
            teams.put(id, Set.of(members));
            generation++;
            return id;
        }

        int probeCalls(UUID station) {
            return probeCalls(PLAYER, station, -1);
        }

        int probeCalls(UUID player, UUID station, int step) {
            if (step < 0) {
                int total = 0;
                for (int each = 0; each < SafetyReturnQuery.PROBE_STEPS; each++) {
                    total += probeCounts.getOrDefault(player + "/" + station + "/" + each, 0);
                }
                return total;
            }
            return probeCounts.getOrDefault(player + "/" + station + "/" + step, 0);
        }

        @Override
        public boolean authorityAvailable() {
            return authority;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public boolean operator(UUID player) {
            current = player;
            return operators.contains(player);
        }

        @Override
        public Optional<UUID> next(UUID player, boolean operator, UUID after) {
            TreeSet<UUID> candidates = new TreeSet<>();
            stations.keySet().stream().filter(id -> operator || teams.get(id).contains(player))
                    .forEach(candidates::add);
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(after == null ? candidates.first() : candidates.higher(after));
        }

        @Override
        public Optional<SafetyReturnQuery.Target> inspect(UUID player, boolean operator, UUID station) {
            inspected.add(station);
            inspectCounts.merge(station, 1, Integer::sum);
            inspectedBy.merge(player, 1, Integer::sum);
            SafetyReturnQuery.Target target = stations.get(station);
            if (target == null || !(operator || teams.get(station).contains(player))) {
                return Optional.empty();
            }
            return Optional.of(target);
        }

        @Override
        public boolean probe(SafetyReturnQuery.Target target, int step) {
            probed.add(target.stationId());
            String key = current + "/" + target.stationId() + "/" + step;
            int calls = probeCounts.merge(key, 1, Integer::sum);
            probeTicks.put(key + "#" + calls, tick);
            if (target.stationId().equals(failCommitProbeOf) && calls > 1) {
                return false;
            }
            if (unloaded.contains(target.stationId())) {
                return false;
            }
            return !Integer.valueOf(step).equals(unsafeStep.get(target.stationId()));
        }
    }
}
