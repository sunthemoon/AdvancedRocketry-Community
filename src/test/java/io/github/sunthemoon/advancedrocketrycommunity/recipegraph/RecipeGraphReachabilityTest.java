package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.DEFAULT_LIMITS;
import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.evaluate;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Synthetic fixtures only; they make no real recipe, item, access or full-graph coverage claim. */
class RecipeGraphReachabilityTest {
    private static final Available ROOT = new Available(0, null, List.of());
    private static final long[] CEILINGS = {16_384, 32_768, 64, 1_024, 1_048_576, 1_048_576, 8_388_608};

    @Test
    void defaultLimitsMatchTheContractAndConstructionIsRangeChecked() {
        assertEquals(new Limits(16_384, 32_768, 64, 1_024, 1_048_576L, 1_048_576L, 8_388_608L), DEFAULT_LIMITS);
        assertEquals(DEFAULT_LIMITS, limits(CEILINGS));
        assertEquals(0, new Limits(0, 0, 0, 0, 0, 0, 0).workUnits());
        for (int field = 0; field < CEILINGS.length; field++) {
            for (long bad : new long[] {-1, CEILINGS[field] + 1}) {
                long[] values = CEILINGS.clone();
                values[field] = bad;
                assertThrows(IllegalArgumentException.class, () -> limits(values));
            }
        }
    }

    @Test
    void emptyAndRootsOnlyGraphsUseRoundZeroFacts() {
        Limits zero = new Limits(0, 0, 0, 0, 0, 0, 0);
        assertEquals(new Success(new TreeMap<>(), new TreeSet<>(), 0, 0), evaluate(set(), set(), List.of(), zero));
        Success rootsOnly = success(evaluate(set("b", "a", "c"), set("c", "a"), List.of(), DEFAULT_LIMITS));
        assertEquals(Map.of("a", ROOT, "c", ROOT), rootsOnly.available());
        assertEquals(Set.of("b"), rootsOnly.unavailable());
        assertEquals(2, rootsOnly.workUnits());
        assertEquals(0, rootsOnly.rounds());
        failure(Code.LIMIT_WORK, evaluate(set("a"), set("a"), List.of(), new Limits(1, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void chainUsesPriorRoundRanksAndChargesNineUnits() {
        Set<String> nodes = set("a", "b", "c");
        List<Producer> producers = List.of(p("make_c", "b", "c"), p("make_b", "a", "b"));
        Success chain = success(evaluate(nodes, set("a"), producers, DEFAULT_LIMITS));
        assertEquals(Map.of("a", ROOT, "b", new Available(1, "make_b", List.of("a")),
                "c", new Available(2, "make_c", List.of("b"))), chain.available());
        assertTrue(chain.unavailable().isEmpty());
        assertEquals(9, chain.workUnits());
        assertEquals(2, chain.rounds());
        assertEquals(chain, evaluate(nodes, set("a"), producers, work(9)));
        failure(Code.LIMIT_WORK, evaluate(nodes, set("a"), producers, work(8)));
    }

    @Test
    void orderedSlotsAreAndRequirementsAndSlotMembersAreOrAlternatives() {
        Set<String> nodes = set("a", "b", "c", "x");
        List<Producer> producers = List.of(p("make_x", "a|b,c", "x"));
        Success both = success(evaluate(nodes, set("c", "b"), producers, DEFAULT_LIMITS));
        assertEquals(new Available(1, "make_x", List.of("b", "c")), both.available().get("x"));
        assertEquals(Set.of("a"), both.unavailable());
        assertEquals(7, both.workUnits());
        for (Set<String> roots : List.of(set("a", "b"), set("c"), set())) {
            assertTrue(success(evaluate(nodes, roots, producers, DEFAULT_LIMITS)).unavailable().contains("x"));
        }
    }

    @Test
    void repeatedSlotsStaySeparateAndRepeatedMembersAreChargedEachTime() {
        Set<String> nodes = set("a", "b", "x", "y", "z");
        List<Producer> producers = List.of(p("twice", "a,a", "x"), p("dup", "a|a", "y"), p("never", "a,b", "z"));
        Success result = success(evaluate(nodes, set("a"), producers, DEFAULT_LIMITS));
        assertEquals(new Available(1, "twice", List.of("a", "a")), result.available().get("x"));
        assertEquals(new Available(1, "dup", List.of("a")), result.available().get("y"));
        assertEquals(Set.of("b", "z"), result.unavailable());
        // Pop a, five raw occurrences of a, two applications, two outputs, pops of x and y.
        assertEquals(12, result.workUnits());
        assertEquals(result, evaluate(nodes, set("a"), producers, work(12)));
        failure(Code.LIMIT_WORK, evaluate(nodes, set("a"), producers, work(11)));
    }

    @Test
    void witnessesPreferEarlierRankThenStringOrderAndKeepOriginalSlotOrder() {
        Set<String> nodes = set("a", "b", "m", "z", "w", "x", "y");
        List<Producer> producers = List.of(p("make_a", "m", "a"), p("rank", "a|z", "x"),
                p("lexical", "m|b", "y"), p("ordered", "z,b", "w"));
        Success result = success(evaluate(nodes, set("z", "m", "b"), producers, DEFAULT_LIMITS));
        assertEquals(new Available(1, "make_a", List.of("m")), result.available().get("a"));
        assertEquals(new Available(1, "rank", List.of("z")), result.available().get("x"));
        assertEquals(new Available(1, "lexical", List.of("b")), result.available().get("y"));
        assertEquals(new Available(1, "ordered", List.of("z", "b")), result.available().get("w"));
    }

    @Test
    void creditPrefersEarliestRoundThenProducerId() {
        Set<String> nodes = set("a", "b", "x", "y");
        List<Producer> producers = List.of(p("a_late", "b", "x"), p("p2", "a", "y"), p("z_early", "a", "x"),
                p("make_b", "a", "b"), p("p1", "a", "y"));
        Success result = success(evaluate(nodes, set("a"), producers, DEFAULT_LIMITS));
        assertEquals(new Available(1, "z_early", List.of("a")), result.available().get("x"));
        assertEquals(new Available(1, "p1", List.of("a")), result.available().get("y"));
        assertEquals(1, result.rounds());
        List<Producer> reversed = new ArrayList<>(producers);
        Collections.reverse(reversed);
        assertEquals(result, evaluate(nodes, set("a"), reversed, DEFAULT_LIMITS));
    }

    @Test
    void multipleOutputsShareOneCreditAndAlreadyAvailableOutputsStillCost() {
        Set<String> nodes = set("a", "x", "y", "z");
        List<Producer> producers = List.of(p("split", "a", "x,y,z"), p("again", "x", "y,a"));
        Success result = success(evaluate(nodes, set("a"), producers, DEFAULT_LIMITS));
        Available split = new Available(1, "split", List.of("a"));
        assertEquals(Map.of("a", ROOT, "x", split, "y", split, "z", split), result.available());
        assertEquals(1, result.rounds());
        assertEquals(13, result.workUnits());
        assertEquals(result, evaluate(nodes, set("a"), producers, work(13)));
        failure(Code.LIMIT_WORK, evaluate(nodes, set("a"), producers, work(12)));
    }

    @Test
    void unseededCyclesStayUnavailableAndSeededCyclesBecomeReachable() {
        List<Producer> cycle = List.of(p("xy", "x", "y"), p("yx", "y", "x"));
        Success unseeded = success(evaluate(set("x", "y"), set(), cycle, work(0)));
        assertEquals(new Success(new TreeMap<>(), new TreeSet<>(Set.of("x", "y")), 0, 0), unseeded);
        List<Producer> seeded = new ArrayList<>(cycle);
        seeded.add(p("seed", "s", "x"));
        Success result = success(evaluate(set("s", "x", "y"), set("s"), seeded, DEFAULT_LIMITS));
        assertEquals(Map.of("s", ROOT, "x", new Available(1, "seed", List.of("s")),
                "y", new Available(2, "xy", List.of("x"))), result.available());
        assertEquals(12, result.workUnits());
        assertEquals(2, result.rounds());
    }

    @Test
    void equivalentOrEncodingsReachTheSameNodesAtTheSameRounds() {
        Set<String> nodes = set("a", "b", "x");
        List<Producer> selector = List.of(p("make_x", "a|b", "x"));
        List<Producer> reversed = List.of(p("make_x", "b|a", "x"));
        List<Producer> split = List.of(p("from_a", "a", "x"), p("from_b", "b", "x"));
        for (Set<String> roots : List.of(set("a"), set("b"), set("a", "b"), set())) {
            Success one = success(evaluate(nodes, roots, selector, DEFAULT_LIMITS));
            assertEquals(one, evaluate(nodes, roots, reversed, DEFAULT_LIMITS));
            Success two = success(evaluate(nodes, roots, split, DEFAULT_LIMITS));
            assertEquals(one.unavailable(), two.unavailable());
            assertEquals(rounds(one), rounds(two));
        }
    }

    @Test
    void randomGraphsMatchTheSynchronousReferenceUnderPermutationsAndExactWork() {
        Random random = new Random(0xC16DL);
        int multiRound = 0;
        for (int trial = 0; trial < 300; trial++) {
            Graph graph = randomGraph(random);
            Success expected = reference(graph);
            assertEquals(expected, evaluate(graph.nodes(), graph.roots(), graph.producers(), DEFAULT_LIMITS));
            Graph shuffled = permuted(graph, random);
            assertEquals(expected, evaluate(shuffled.nodes(), shuffled.roots(), shuffled.producers(), DEFAULT_LIMITS));
            assertEquals(expected, evaluate(shuffled.nodes(), shuffled.roots(), shuffled.producers(),
                    work(expected.workUnits())));
            if (expected.workUnits() > 0) {
                failure(Code.LIMIT_WORK, evaluate(graph.nodes(), graph.roots(), graph.producers(),
                        work(expected.workUnits() - 1)));
            }
            multiRound += expected.rounds() >= 2 ? 1 : 0;
        }
        assertTrue(multiRound > 0, "fixtures must include multi-round derivations");
    }

    @Test
    void resultsAreImmutableAndDetachedFromLaterInputMutation() {
        Set<String> nodes = new HashSet<>(List.of("a", "b", "x"));
        Set<String> roots = new HashSet<>(List.of("a"));
        List<String> slot = new ArrayList<>(List.of("a", "b"));
        List<List<String>> slots = new ArrayList<>(List.of(slot));
        List<String> outputs = new ArrayList<>(List.of("x"));
        Producer producer = new Producer("make_x", slots, outputs);
        assertSame(slots, producer.slots());
        assertSame(outputs, producer.outputs());
        assertEquals(new Producer(null, null, null), new Producer(null, null, null));
        List<Producer> producers = new ArrayList<>(List.of(producer));
        Success result = success(evaluate(nodes, roots, producers, DEFAULT_LIMITS));
        Success before = success(evaluate(set("a", "b", "x"), set("a"), List.of(p("make_x", "a|b", "x")),
                DEFAULT_LIMITS));
        nodes.clear();
        roots.add("b");
        slot.set(0, "b");
        slots.clear();
        outputs.add("y");
        producers.clear();
        assertEquals(before, result);
        assertNull(result.available().get("a").producer());
        assertThrows(UnsupportedOperationException.class, () -> result.available().put("y", ROOT));
        assertThrows(UnsupportedOperationException.class, () -> result.available().headMap("x").clear());
        assertThrows(UnsupportedOperationException.class, () -> result.unavailable().add("y"));
        assertThrows(UnsupportedOperationException.class, () -> result.available().get("x").slotMembers().add("b"));
        assertThrows(UnsupportedOperationException.class, () -> result.available().get("a").slotMembers().add("b"));
    }

    @Test
    void nullInputsFailWithTypedLocationsInsteadOfExceptions() {
        Set<String> nodes = set("a", "x");
        List<Producer> ok = List.of(p("make_x", "a", "x"));
        assertEquals("nodes", failure(Code.NULL_INPUT, evaluate(null, set(), ok, DEFAULT_LIMITS)).subject());
        assertEquals("roots", failure(Code.NULL_INPUT, evaluate(nodes, null, ok, DEFAULT_LIMITS)).subject());
        assertEquals("producers", failure(Code.NULL_INPUT, evaluate(nodes, set(), null, DEFAULT_LIMITS)).subject());
        assertEquals("limits", failure(Code.NULL_INPUT, evaluate(nodes, set(), ok, null)).subject());
        failure(Code.NULL_INPUT, evaluate(withNull(nodes), set(), ok, DEFAULT_LIMITS));
        failure(Code.NULL_INPUT, evaluate(nodes, withNull(set("a")), ok, DEFAULT_LIMITS));
        assertEquals("producers[1]", failure(Code.NULL_INPUT,
                evaluate(nodes, set(), Arrays.asList(ok.get(0), null), DEFAULT_LIMITS)).subject());
        List<Producer> broken = List.of(new Producer(null, List.of(List.of("a")), List.of("x")),
                new Producer("p", null, List.of("x")),
                new Producer("p", Arrays.asList(List.of("a"), null), List.of("x")),
                new Producer("p", List.of(Arrays.asList("a", null)), List.of("x")),
                new Producer("p", List.of(List.of("a")), null),
                new Producer("p", List.of(List.of("a")), Arrays.asList("x", null)));
        List<String> subjects = new ArrayList<>();
        for (Producer producer : broken) {
            subjects.add(failure(Code.NULL_INPUT, evaluate(nodes, set(), List.of(producer), DEFAULT_LIMITS)).subject());
        }
        assertEquals(List.of("producers[0].id", "producers[0].slots", "producers[0].slots[1]",
                "producers[0].slots[0][1]", "producers[0].outputs", "producers[0].outputs[1]"), subjects);
    }

    @Test
    void invalidIdsAreRejectedWithoutEchoingTheRejectedText() {
        for (String bad : List.of("", " ", "a b", "tab\t", "nul\u0000", "del\u007f", "e" + (char) 0xe9, "x".repeat(257))) {
            Failure node = failure(Code.INVALID_ID, evaluate(set("a", bad), set(), List.of(), DEFAULT_LIMITS));
            assertFalse(!bad.isEmpty() && node.subject().contains(bad));
            Set<String> nodes = set("a", "x");
            failure(Code.INVALID_ID, evaluate(nodes, set(bad), List.of(), DEFAULT_LIMITS));
            failure(Code.INVALID_ID, evaluate(nodes, set(), List.of(
                    new Producer(bad, List.of(List.of("a")), List.of("x"))), DEFAULT_LIMITS));
            failure(Code.INVALID_ID, evaluate(nodes, set(), List.of(
                    new Producer("p", List.of(List.of(bad)), List.of("x"))), DEFAULT_LIMITS));
            failure(Code.INVALID_ID, evaluate(nodes, set(), List.of(
                    new Producer("p", List.of(List.of("a")), List.of(bad))), DEFAULT_LIMITS));
        }
        String edge = "!" + "x".repeat(254) + "~";
        assertEquals(Set.of(edge), success(evaluate(set(edge), set(), List.of(), DEFAULT_LIMITS)).unavailable());
    }

    @Test
    void undeclaredDuplicateAndEmptyStructuresUseTheirOwnCodes() {
        Set<String> nodes = set("a", "x");
        failure(Code.UNKNOWN_REFERENCE, evaluate(nodes, set("b"), List.of(), DEFAULT_LIMITS));
        failure(Code.UNKNOWN_REFERENCE, evaluate(nodes, set(), List.of(p("p", "a|b", "x")), DEFAULT_LIMITS));
        failure(Code.UNKNOWN_REFERENCE, evaluate(nodes, set(), List.of(p("p", "a", "b")), DEFAULT_LIMITS));
        failure(Code.DUPLICATE_PRODUCER, evaluate(nodes, set(), List.of(p("p", "a", "x"), p("p", "x", "a")),
                DEFAULT_LIMITS));
        failure(Code.DUPLICATE_OUTPUT, evaluate(nodes, set(), List.of(p("p", "a", "x,x")), DEFAULT_LIMITS));
        failure(Code.EMPTY_SLOTS, evaluate(nodes, set(), List.of(
                new Producer("p", List.of(), List.of("x"))), DEFAULT_LIMITS));
        failure(Code.EMPTY_SELECTOR, evaluate(nodes, set(), List.of(
                new Producer("p", List.of(List.of("a"), List.of()), List.of("x"))), DEFAULT_LIMITS));
        failure(Code.EMPTY_OUTPUTS, evaluate(nodes, set(), List.of(
                new Producer("p", List.of(List.of("a")), List.of())), DEFAULT_LIMITS));
        Success shared = success(evaluate(nodes, set("a"), List.of(p("q", "a", "x"), p("p", "a", "x")),
                DEFAULT_LIMITS));
        assertEquals(new Available(1, "p", List.of("a")), shared.available().get("x"));
    }

    @Test
    void smallStructuralLimitsAcceptExactCountsAndRejectOneMore() {
        Set<String> nodes = set("a", "b", "c");
        List<Producer> producers = List.of(p("p1", "a|b,c", "b,c"), p("p2", "a", "c"));
        long[] exact = {3, 2, 2, 2, 4, 3, 8_388_608};
        Code[] codes = {Code.LIMIT_NODES, Code.LIMIT_PRODUCERS, Code.LIMIT_SLOTS, Code.LIMIT_MEMBERS,
            Code.LIMIT_MEMBER_OCCURRENCES, Code.LIMIT_OUTPUT_OCCURRENCES};
        success(evaluate(nodes, set("a"), producers, limits(exact)));
        for (int field = 0; field < codes.length; field++) {
            long[] values = exact.clone();
            values[field]--;
            failure(codes[field], evaluate(nodes, set("a"), producers, limits(values)));
        }
        assertEquals("roots", failure(Code.LIMIT_NODES,
                evaluate(set("a"), set("a", "b"), List.of(), new Limits(1, 0, 0, 0, 0, 0, 0))).subject());
    }

    @Test
    void defaultStructuralCeilingsAcceptExactCountsAndRejectOneMore() {
        Set<String> many = new LinkedHashSet<>(names("n", 16_384));
        assertEquals(16_384, success(evaluate(many, set(), List.of(), DEFAULT_LIMITS)).unavailable().size());
        many.add("extra");
        failure(Code.LIMIT_NODES, evaluate(many, set(), List.of(), DEFAULT_LIMITS));

        success(evaluate(set("a"), set("a"), List.of(single("p", List.of(Collections.nCopies(1_024, "a")))),
                DEFAULT_LIMITS));
        failure(Code.LIMIT_MEMBERS, evaluate(set("a"), set("a"),
                List.of(single("p", List.of(Collections.nCopies(1_025, "a")))), DEFAULT_LIMITS));
        success(evaluate(set("a"), set("a"), List.of(single("p", Collections.nCopies(64, List.of("a")))),
                DEFAULT_LIMITS));
        failure(Code.LIMIT_SLOTS, evaluate(set("a"), set("a"),
                List.of(single("p", Collections.nCopies(65, List.of("a")))), DEFAULT_LIMITS));

        List<Producer> producers = new ArrayList<>();
        for (int i = 0; i < 32_768; i++) {
            producers.add(single("p" + i, List.of(List.of("a"))));
        }
        Success full = success(evaluate(set("a"), set("a"), producers, DEFAULT_LIMITS));
        assertEquals(1 + 3L * 32_768, full.workUnits());
        assertEquals(0, full.rounds());
        producers.add(single("p_over", List.of(List.of("a"))));
        failure(Code.LIMIT_PRODUCERS, evaluate(set("a"), set("a"), producers, DEFAULT_LIMITS));

        List<Producer> members = new ArrayList<>();
        for (int i = 0; i < 1_024; i++) {
            members.add(single("m" + i, List.of(Collections.nCopies(1_024, "a"))));
        }
        assertEquals(1 + 1_048_576L + 2 * 1_024,
                success(evaluate(set("a"), set("a"), members, DEFAULT_LIMITS)).workUnits());
        members.add(single("m_over", List.of(List.of("a"))));
        failure(Code.LIMIT_MEMBER_OCCURRENCES, evaluate(set("a"), set("a"), members, DEFAULT_LIMITS));

        List<String> outputs = names("o", 1_024);
        Set<String> outputNodes = new LinkedHashSet<>(outputs);
        outputNodes.add("a");
        List<Producer> wide = new ArrayList<>();
        for (int i = 0; i < 1_024; i++) {
            wide.add(new Producer("w" + i, List.of(List.of("a")), outputs));
        }
        assertEquals(1 + 3 * 1_024L + 1_048_576L,
                success(evaluate(outputNodes, set("a"), wide, DEFAULT_LIMITS)).workUnits());
        wide.add(single("w_over", List.of(List.of("a"))));
        failure(Code.LIMIT_OUTPUT_OCCURRENCES, evaluate(outputNodes, set("a"), wide, DEFAULT_LIMITS));
    }

    private record Graph(Set<String> nodes, Set<String> roots, List<Producer> producers) {
    }

    /** Independent synchronous-round reference; every round recomputes all producers from prior ranks. */
    private static Success reference(Graph graph) {
        Map<String, Integer> rank = new HashMap<>();
        SortedMap<String, Available> available = new TreeMap<>();
        for (String root : graph.roots()) {
            rank.put(root, 0);
            available.put(root, ROOT);
        }
        Map<String, Producer> byId = new HashMap<>();
        graph.producers().forEach(producer -> byId.put(producer.id(), producer));
        Set<String> applied = new HashSet<>();
        int rounds = 0;
        for (int round = 1; ; round++) {
            SortedMap<String, List<String>> ready = new TreeMap<>();
            for (Producer producer : graph.producers()) {
                List<String> witnesses = applied.contains(producer.id()) ? null : witnesses(producer, rank);
                if (witnesses != null) { ready.put(producer.id(), witnesses); }
            }
            if (ready.isEmpty()) { break; }
            for (Map.Entry<String, List<String>> entry : ready.entrySet()) {
                applied.add(entry.getKey());
                for (String output : byId.get(entry.getKey()).outputs()) {
                    if (rank.putIfAbsent(output, round) == null) {
                        available.put(output, new Available(round, entry.getKey(), entry.getValue()));
                        rounds = round;
                    }
                }
            }
        }
        // Work follows the four counted events directly, not the optimized traversal order.
        long work = rank.size();
        for (Producer producer : graph.producers()) {
            for (List<String> slot : producer.slots()) {
                for (String member : slot) { work += rank.containsKey(member) ? 1 : 0; }
            }
            work += applied.contains(producer.id()) ? 1 + producer.outputs().size() : 0;
        }
        SortedSet<String> unavailable = new TreeSet<>(graph.nodes());
        unavailable.removeAll(rank.keySet());
        return new Success(available, unavailable, work, rounds);
    }

    private static List<String> witnesses(Producer producer, Map<String, Integer> rank) {
        List<String> witnesses = new ArrayList<>();
        for (List<String> slot : producer.slots()) {
            String best = null;
            for (String member : slot) {
                Integer r = rank.get(member);
                if (r != null && (best == null || r < rank.get(best)
                        || r.equals(rank.get(best)) && member.compareTo(best) < 0)) {
                    best = member;
                }
            }
            if (best == null) { return null; }
            witnesses.add(best);
        }
        return witnesses;
    }

    private static Graph randomGraph(Random random) {
        List<String> nodes = randomNames(random, "", 1 + random.nextInt(16));
        Set<String> roots = new LinkedHashSet<>();
        for (String node : nodes) {
            if (random.nextInt(4) == 0) { roots.add(node); }
        }
        List<Producer> producers = new ArrayList<>();
        for (String id : randomNames(random, "p", random.nextInt(20))) {
            List<List<String>> slots = new ArrayList<>();
            for (int s = 1 + random.nextInt(3); s > 0; s--) {
                List<String> members = new ArrayList<>();
                for (int m = 1 + random.nextInt(3); m > 0; m--) { members.add(nodes.get(random.nextInt(nodes.size()))); }
                slots.add(members);
            }
            List<String> outputs = shuffled(nodes, random);
            producers.add(new Producer(id, slots,
                    new ArrayList<>(outputs.subList(0, 1 + random.nextInt(Math.min(3, outputs.size()))))));
        }
        return new Graph(new LinkedHashSet<>(nodes), roots, producers);
    }

    /** Shuffles node, root, producer, member and output order; slot order is semantic and kept. */
    private static Graph permuted(Graph graph, Random random) {
        List<Producer> producers = new ArrayList<>();
        for (Producer producer : graph.producers()) {
            List<List<String>> slots = new ArrayList<>();
            for (List<String> slot : producer.slots()) {
                slots.add(shuffled(slot, random));
            }
            producers.add(new Producer(producer.id(), slots, shuffled(producer.outputs(), random)));
        }
        Collections.shuffle(producers, random);
        return new Graph(new LinkedHashSet<>(shuffled(graph.nodes(), random)),
                new LinkedHashSet<>(shuffled(graph.roots(), random)), producers);
    }

    private static List<String> randomNames(Random random, String prefix, int count) {
        Set<String> names = new LinkedHashSet<>();
        while (names.size() < count) {
            String name = prefix + (char) ('a' + random.nextInt(26));
            names.add(random.nextBoolean() ? name : name + (char) ('a' + random.nextInt(26)));
        }
        return new ArrayList<>(names);
    }

    private static List<String> names(String prefix, int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) { names.add(prefix + i); }
        return names;
    }

    private static List<String> shuffled(Collection<String> values, Random random) {
        List<String> copy = new ArrayList<>(values); Collections.shuffle(copy, random); return copy;
    }

    private static Map<String, Integer> rounds(Success success) {
        Map<String, Integer> rounds = new HashMap<>();
        success.available().forEach((id, available) -> rounds.put(id, available.round()));
        return rounds;
    }

    /** Compact fixture: slots "a|b,c" means [[a, b], [c]] and outputs "x,y" means [x, y]. */
    private static Producer p(String id, String slots, String outputs) {
        List<List<String>> parsed = new ArrayList<>();
        for (String slot : slots.split(",")) {
            parsed.add(List.of(slot.split("\\|")));
        }
        return new Producer(id, parsed, List.of(outputs.split(",")));
    }

    private static Producer single(String id, List<List<String>> slots) { return new Producer(id, slots, List.of("a")); }
    private static Set<String> set(String... ids) { return new LinkedHashSet<>(Arrays.asList(ids)); }
    private static Success success(Evaluation evaluation) { return assertInstanceOf(Success.class, evaluation); }

    private static Set<String> withNull(Set<String> ids) {
        Set<String> copy = new HashSet<>(ids); copy.add(null); return copy;
    }

    private static Limits work(long units) {
        return new Limits(16_384, 32_768, 64, 1_024, 1_048_576L, 1_048_576L, units);
    }

    private static Limits limits(long[] values) {
        return new Limits((int) values[0], (int) values[1], (int) values[2], (int) values[3],
                values[4], values[5], values[6]);
    }

    private static Failure failure(Code code, Evaluation evaluation) {
        Failure failure = assertInstanceOf(Failure.class, evaluation, () -> "expected " + code);
        assertEquals(code, failure.code(), failure.subject());
        assertTrue(failure.subject().length() <= 256 && failure.subject().chars().allMatch(c -> c >= 32 && c < 127));
        return failure;
    }
}
