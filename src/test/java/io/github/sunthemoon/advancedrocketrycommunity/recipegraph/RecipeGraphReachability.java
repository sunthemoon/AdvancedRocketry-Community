package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Test-side bounded forward evaluator over opaque IDs (C16d forward CONTRACT-01).
 *
 * <p>Ordered slots are AND requirements and the members of one slot are OR alternatives. Results are
 * synthetic reachability facts only: they do not assert that a real item, recipe, dimension, capability
 * or access path exists, and no main-source code calls this helper.
 */
final class RecipeGraphReachability {
    private static final int MAX_NODES = 16_384;
    private static final int MAX_PRODUCERS = 32_768;
    private static final int MAX_SLOTS_PER_PRODUCER = 64;
    private static final int MAX_MEMBERS_PER_SLOT = 1_024;
    private static final long MAX_MEMBER_OCCURRENCES = 1_048_576L;
    private static final long MAX_OUTPUT_OCCURRENCES = 1_048_576L;
    private static final long MAX_WORK_UNITS = 8_388_608L;
    private static final int MAX_ID_LENGTH = 256;

    static final Limits DEFAULT_LIMITS = new Limits(MAX_NODES, MAX_PRODUCERS, MAX_SLOTS_PER_PRODUCER,
            MAX_MEMBERS_PER_SLOT, MAX_MEMBER_OCCURRENCES, MAX_OUTPUT_OCCURRENCES, MAX_WORK_UNITS);

    private RecipeGraphReachability() {
    }

    /** Raw producer; the constructor neither copies nor traverses its lists. */
    record Producer(String id, List<List<String>> slots, List<String> outputs) {
    }

    /** Private structural and forward-work ceilings; each field ranges from zero to its default. */
    record Limits(int nodes, int producers, int slotsPerProducer, int membersPerSlot,
            long memberOccurrences, long outputOccurrences, long workUnits) {
        Limits {
            if (nodes < 0 || nodes > MAX_NODES || producers < 0 || producers > MAX_PRODUCERS
                    || slotsPerProducer < 0 || slotsPerProducer > MAX_SLOTS_PER_PRODUCER
                    || membersPerSlot < 0 || membersPerSlot > MAX_MEMBERS_PER_SLOT
                    || memberOccurrences < 0 || memberOccurrences > MAX_MEMBER_OCCURRENCES
                    || outputOccurrences < 0 || outputOccurrences > MAX_OUTPUT_OCCURRENCES
                    || workUnits < 0 || workUnits > MAX_WORK_UNITS) {
                throw new IllegalArgumentException("Recipe graph limits must be within 0 and the defaults");
            }
        }
    }

    enum Code {
        NULL_INPUT,
        INVALID_ID,
        UNKNOWN_REFERENCE,
        DUPLICATE_PRODUCER,
        DUPLICATE_OUTPUT,
        EMPTY_SLOTS,
        EMPTY_SELECTOR,
        EMPTY_OUTPUTS,
        LIMIT_NODES,
        LIMIT_PRODUCERS,
        LIMIT_SLOTS,
        LIMIT_MEMBERS,
        LIMIT_MEMBER_OCCURRENCES,
        LIMIT_OUTPUT_OCCURRENCES,
        LIMIT_WORK
    }

    sealed interface Evaluation permits Success, Failure {
    }

    /** Roots use round 0, a null producer and no members; witnesses otherwise follow original slot order. */
    record Available(int round, String producer, List<String> slotMembers) {
    }

    record Success(SortedMap<String, Available> available, SortedSet<String> unavailable, long workUnits,
            int rounds) implements Evaluation {
    }

    /** The subject is a bounded location such as producers[3].slots[1][0], never the rejected ID text. */
    record Failure(Code code, String subject) implements Evaluation {
    }

    /** Owned producer copy holding node indices; never exposed. */
    private record Owned(String id, int[][] slots, int[] outputs) {
    }

    static Evaluation evaluate(Set<String> nodes, Set<String> roots, List<Producer> producers, Limits limits) {
        if (nodes == null) {
            return new Failure(Code.NULL_INPUT, "nodes");
        }
        if (roots == null) {
            return new Failure(Code.NULL_INPUT, "roots");
        }
        if (producers == null) {
            return new Failure(Code.NULL_INPUT, "producers");
        }
        if (limits == null) {
            return new Failure(Code.NULL_INPUT, "limits");
        }
        // Container sizes are checked before any element is traversed or copied.
        if (nodes.size() > limits.nodes()) {
            return new Failure(Code.LIMIT_NODES, "nodes");
        }
        if (roots.size() > limits.nodes()) {
            return new Failure(Code.LIMIT_NODES, "roots");
        }
        if (producers.size() > limits.producers()) {
            return new Failure(Code.LIMIT_PRODUCERS, "producers");
        }
        int position = 0;
        for (String node : nodes) {
            Code problem = idProblem(node);
            if (problem != null) {
                return new Failure(problem, "nodes[" + position + "]");
            }
            position++;
        }
        // Node indices follow String.compareTo order, so ascending index is ascending node ID.
        String[] ids = new TreeSet<>(nodes).toArray(new String[0]);
        Map<String, Integer> indexOf = new HashMap<>(ids.length * 2);
        for (int i = 0; i < ids.length; i++) {
            indexOf.put(ids[i], i);
        }
        int[] rootIndices = new int[roots.size()];
        position = 0;
        for (String root : roots) {
            Code problem = referenceProblem(root, indexOf);
            if (problem != null) {
                return new Failure(problem, "roots[" + position + "]");
            }
            rootIndices[position++] = indexOf.get(root);
        }
        Owner owner = new Owner(indexOf, limits);
        position = 0;
        for (Producer producer : producers) {
            Failure failure = owner.add(producer, position++);
            if (failure != null) {
                return failure;
            }
        }
        Owned[] owned = owner.owned.toArray(new Owned[0]);
        Arrays.sort(owned, Comparator.comparing(Owned::id));
        return forward(ids, rootIndices, owned, (int) owner.memberTotal, limits.workUnits());
    }

    /** Validates producers in input order and builds owned copies only after each guarding bound. */
    private static final class Owner {
        private final Map<String, Integer> indexOf;
        private final Limits limits;
        private final Set<String> producerIds = new HashSet<>();
        private final int[] outputStamp;
        private final List<Owned> owned = new ArrayList<>();
        private long memberTotal;
        private long outputTotal;

        Owner(Map<String, Integer> indexOf, Limits limits) {
            this.indexOf = indexOf;
            this.limits = limits;
            this.outputStamp = new int[indexOf.size()];
        }

        Failure add(Producer producer, int position) {
            String at = "producers[" + position + "]";
            if (producer == null) {
                return new Failure(Code.NULL_INPUT, at);
            }
            Code problem = idProblem(producer.id());
            if (problem != null) {
                return new Failure(problem, at + ".id");
            }
            if (!producerIds.add(producer.id())) {
                return new Failure(Code.DUPLICATE_PRODUCER, at + ".id");
            }
            List<List<String>> slots = producer.slots();
            if (slots == null) {
                return new Failure(Code.NULL_INPUT, at + ".slots");
            }
            if (slots.isEmpty()) {
                return new Failure(Code.EMPTY_SLOTS, at + ".slots");
            }
            if (slots.size() > limits.slotsPerProducer()) {
                return new Failure(Code.LIMIT_SLOTS, at + ".slots");
            }
            int[][] ownedSlots = new int[slots.size()][];
            int slotPosition = 0;
            for (List<String> slot : slots) {
                if (slot == null) {
                    return new Failure(Code.NULL_INPUT, at + ".slots[" + slotPosition + "]");
                }
                if (slot.isEmpty()) {
                    return new Failure(Code.EMPTY_SELECTOR, at + ".slots[" + slotPosition + "]");
                }
                if (slot.size() > limits.membersPerSlot()) {
                    return new Failure(Code.LIMIT_MEMBERS, at + ".slots[" + slotPosition + "]");
                }
                memberTotal += slot.size();
                if (memberTotal > limits.memberOccurrences()) {
                    return new Failure(Code.LIMIT_MEMBER_OCCURRENCES, at + ".slots[" + slotPosition + "]");
                }
                int[] members = new int[slot.size()];
                int memberPosition = 0;
                for (String member : slot) {
                    problem = referenceProblem(member, indexOf);
                    if (problem != null) {
                        return new Failure(problem, at + ".slots[" + slotPosition + "][" + memberPosition + "]");
                    }
                    members[memberPosition++] = indexOf.get(member);
                }
                ownedSlots[slotPosition++] = members;
            }
            List<String> outputs = producer.outputs();
            if (outputs == null) {
                return new Failure(Code.NULL_INPUT, at + ".outputs");
            }
            if (outputs.isEmpty()) {
                return new Failure(Code.EMPTY_OUTPUTS, at + ".outputs");
            }
            outputTotal += outputs.size();
            if (outputTotal > limits.outputOccurrences()) {
                return new Failure(Code.LIMIT_OUTPUT_OCCURRENCES, at + ".outputs");
            }
            int[] ownedOutputs = new int[outputs.size()];
            int outputPosition = 0;
            for (String output : outputs) {
                problem = referenceProblem(output, indexOf);
                if (problem != null) {
                    return new Failure(problem, at + ".outputs[" + outputPosition + "]");
                }
                int node = indexOf.get(output);
                if (outputStamp[node] == position + 1) {
                    return new Failure(Code.DUPLICATE_OUTPUT, at + ".outputs[" + outputPosition + "]");
                }
                outputStamp[node] = position + 1;
                ownedOutputs[outputPosition++] = node;
            }
            owned.add(new Owned(producer.id(), ownedSlots, ownedOutputs));
            return null;
        }
    }

    /**
     * Occurrence-index worklist. Each newly available node is popped once, in ascending ID within its
     * round; producers whose last slot is satisfied apply once, in ascending ID, in the next round.
     */
    private static Evaluation forward(String[] ids, int[] roots, Owned[] producers, int memberTotal,
            long workLimit) {
        int nodeCount = ids.length;
        int[] slotBase = new int[producers.length + 1];
        for (int p = 0; p < producers.length; p++) {
            slotBase[p + 1] = slotBase[p] + producers[p].slots().length;
        }
        int[] slotOwner = new int[slotBase[producers.length]];
        int[] missing = new int[producers.length];
        int[] occurrenceStart = new int[nodeCount + 1];
        for (int p = 0; p < producers.length; p++) {
            int[][] slots = producers[p].slots();
            missing[p] = slots.length;
            for (int s = 0; s < slots.length; s++) {
                slotOwner[slotBase[p] + s] = p;
                for (int member : slots[s]) {
                    occurrenceStart[member + 1]++;
                }
            }
        }
        for (int n = 0; n < nodeCount; n++) {
            occurrenceStart[n + 1] += occurrenceStart[n];
        }
        int[] occurrenceSlot = new int[memberTotal];
        int[] cursor = Arrays.copyOf(occurrenceStart, nodeCount);
        for (int p = 0; p < producers.length; p++) {
            int[][] slots = producers[p].slots();
            for (int s = 0; s < slots.length; s++) {
                for (int member : slots[s]) {
                    occurrenceSlot[cursor[member]++] = slotBase[p] + s;
                }
            }
        }
        int[] witness = new int[slotOwner.length];
        int[] rank = new int[nodeCount];
        int[] credit = new int[nodeCount];
        Arrays.fill(witness, -1);
        Arrays.fill(rank, -1);
        Arrays.fill(credit, -1);
        int[] frontier = new int[nodeCount];
        int[] next = new int[nodeCount];
        int[] ready = new int[producers.length];
        int frontierSize = 0;
        for (int root : roots) {
            if (rank[root] < 0) {
                rank[root] = 0;
                frontier[frontierSize++] = root;
            }
        }
        Arrays.sort(frontier, 0, frontierSize);
        Failure overflow = new Failure(Code.LIMIT_WORK, "workUnits");
        long work = 0;
        int round = 0;
        int rounds = 0;
        while (frontierSize > 0) {
            int readySize = 0;
            for (int i = 0; i < frontierSize; i++) {
                int node = frontier[i];
                if (++work > workLimit) {
                    return overflow;
                }
                for (int o = occurrenceStart[node]; o < occurrenceStart[node + 1]; o++) {
                    if (++work > workLimit) {
                        return overflow;
                    }
                    int slot = occurrenceSlot[o];
                    if (witness[slot] < 0) {
                        // First pop wins: the smallest rank, then the smallest ID within that rank.
                        witness[slot] = node;
                        if (--missing[slotOwner[slot]] == 0) {
                            ready[readySize++] = slotOwner[slot];
                        }
                    }
                }
            }
            Arrays.sort(ready, 0, readySize);
            round++;
            int nextSize = 0;
            for (int r = 0; r < readySize; r++) {
                if (++work > workLimit) {
                    return overflow;
                }
                for (int output : producers[ready[r]].outputs()) {
                    if (++work > workLimit) {
                        return overflow;
                    }
                    if (rank[output] < 0) {
                        rank[output] = round;
                        credit[output] = ready[r];
                        next[nextSize++] = output;
                    }
                }
            }
            Arrays.sort(next, 0, nextSize);
            int[] swap = frontier;
            frontier = next;
            next = swap;
            frontierSize = nextSize;
            if (nextSize > 0) {
                rounds = round;
            }
        }
        return success(ids, producers, slotBase, witness, rank, credit, work, rounds);
    }

    private static Success success(String[] ids, Owned[] producers, int[] slotBase, int[] witness, int[] rank,
            int[] credit, long work, int rounds) {
        Available root = new Available(0, null, List.of());
        List<List<String>> witnessLists = new ArrayList<>(Collections.nCopies(producers.length, null));
        TreeMap<String, Available> available = new TreeMap<>();
        TreeSet<String> unavailable = new TreeSet<>();
        for (int n = 0; n < ids.length; n++) {
            if (rank[n] < 0) {
                unavailable.add(ids[n]);
            } else if (credit[n] < 0) {
                available.put(ids[n], root);
            } else {
                int p = credit[n];
                if (witnessLists.get(p) == null) {
                    String[] members = new String[producers[p].slots().length];
                    for (int s = 0; s < members.length; s++) {
                        members[s] = ids[witness[slotBase[p] + s]];
                    }
                    witnessLists.set(p, List.of(members));
                }
                available.put(ids[n], new Available(rank[n], producers[p].id(), witnessLists.get(p)));
            }
        }
        return new Success(Collections.unmodifiableSortedMap(available),
                Collections.unmodifiableSortedSet(unavailable), work, rounds);
    }

    /** Nonempty, at most 256 characters, each in 33..126 (printable ASCII without space). */
    private static Code idProblem(String id) {
        if (id == null) {
            return Code.NULL_INPUT;
        }
        if (id.isEmpty() || id.length() > MAX_ID_LENGTH) {
            return Code.INVALID_ID;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < '!' || c > '~') {
                return Code.INVALID_ID;
            }
        }
        return null;
    }

    private static Code referenceProblem(String id, Map<String, Integer> indexOf) {
        Code problem = idProblem(id);
        if (problem == null && !indexOf.containsKey(id)) {
            return Code.UNKNOWN_REFERENCE;
        }
        return problem;
    }
}
