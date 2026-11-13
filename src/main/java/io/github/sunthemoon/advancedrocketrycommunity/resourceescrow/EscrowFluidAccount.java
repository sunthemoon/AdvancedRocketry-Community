package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * W357 sections 2 and 3: the pure signed per-fluid account of one resource transfer. It uses no Minecraft type, and in
 * Slice A (W361) no ledger, operation, intent, receipt, writer, migration or admission path calls it. The saved form is
 * {@link EscrowFluidAccountCodec}.
 *
 * <p>Every ordinary refusal is a {@link Status} or a {@link Result}, never an exception. All arithmetic is checked
 * {@code long} arithmetic, and an unknown contribution is an empty {@link OptionalLong}, never a zero.
 */
public final class EscrowFluidAccount {
    public static final int SCHEMA = 1;
    /** One operation moves 1 to 1,000 mB of one key (section 2.1). */
    public static final long MAX_TRANSFER = 1_000;
    public static final int MAX_MEMBERS = 64;
    public static final int MAX_UNITS = 128;
    public static final int MAX_MEMBER_KEYS = 320;
    public static final String KEY_DOMAIN = "arce-escrow-fluid-key-1:";
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final java.util.regex.Pattern DIGEST = java.util.regex.Pattern.compile("[0-9a-f]{64}");

    private EscrowFluidAccount() { }

    public enum Status {
        OK, INVALID_KEY, MALFORMED_DIGEST, DUPLICATE_KEY, UNSORTED_KEYS, CARDINALITY, NEGATIVE_AMOUNT, INVALID_PLAN,
        OVERFLOW, INVALID_KNOWLEDGE, INVALID_MASK, INVALID_OWNER, INVALID_MEMBERS, INCORRECT_BALANCE, BROKEN_CHAIN,
        COMPONENT_LIMIT, MISSING_OBSERVATION,
        // Saved-form refusals of the codec.
        TOO_LARGE, UNKNOWN_FIELD, INVALID_FIELD, UNSUPPORTED_SCHEMA, NONCANONICAL, DUPLICATE_ROW
    }

    /** A bounded outcome: {@code value} is non-null exactly when {@code status} is OK. */
    public record Result<T>(Status status, T value) {
        public Result { Objects.requireNonNull(status); if ((status == Status.OK) != (value != null)) { throw new IllegalStateException("Result"); } }
        public static <T> Result<T> ok(T value) { return new Result<>(Status.OK, value); }
        public static <T> Result<T> refuse(Status status) { return new Result<>(status, null); }
        public boolean ok() { return status == Status.OK; }
    }

    /** The resource scope fixes the key cardinalities of section 2.1. */
    public enum Scope {
        /** The loader tank and both native banks: two baseline keys, five in the union. */
        FUEL(2, 5),
        /** The device tank and the one addressed rocket tank: one baseline key, three in the union. */
        CARGO(1, 3);
        private final int baselineKeys, unionKeys;
        Scope(int baselineKeys, int unionKeys) { this.baselineKeys = baselineKeys; this.unionKeys = unionKeys; }
        public int baselineKeys() { return baselineKeys; }
        public int unionKeys() { return unionKeys; }
    }

    /** Section 2.2. PLANNED is a row without a frozen part; NOT_APPLICABLE is a member without a row. */
    public enum Knowledge { PLANNED, KNOWN, UNKNOWN_BASELINE, UNKNOWN_ENDPOINT, NOT_APPLICABLE }

    /** A fluid registry ID and the digest of its amount-free native envelope. Ordered by ID, then by digest. */
    public record Key(String fluid, String tagDigest) implements Comparable<Key> {
        public Key { Objects.requireNonNull(fluid); Objects.requireNonNull(tagDigest); }
        /**
         * {@code canonicalTagDigest} is the canonical digest of the native Tag, or null when the stack has no Tag. An
         * absent Tag and an empty compound give different keys.
         */
        public static Result<Key> derive(String fluid, String canonicalTagDigest) {
            if (!validId(fluid)) { return Result.refuse(Status.INVALID_KEY); }
            if (canonicalTagDigest != null && !DIGEST.matcher(canonicalTagDigest).matches()) { return Result.refuse(Status.MALFORMED_DIGEST); }
            String envelope = KEY_DOMAIN + fluid + "\n" + (canonicalTagDigest == null ? "absent" : "tag:" + canonicalTagDigest);
            try {
                return Result.ok(new Key(fluid, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(envelope.getBytes(StandardCharsets.UTF_8)))));
            } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        public Status status() {
            return !validId(fluid) ? Status.INVALID_KEY : !DIGEST.matcher(tagDigest).matches() ? Status.MALFORMED_DIGEST : Status.OK;
        }
        @Override public int compareTo(Key other) {
            int order = fluid.compareTo(other.fluid);
            return order != 0 ? order : tagDigest.compareTo(other.tagDigest);
        }
    }
    private static boolean validId(String value) {
        return value != null && value.length() <= 128 && ID.matcher(value).matches();
    }

    /** One native unit of an endpoint: a tank or a bank. An empty unit is {@code (null, 0)}. */
    public record Unit(Key key, long amount) {
        public static final Unit EMPTY = new Unit(null, 0);
    }

    /** The amounts of one endpoint, aggregated per key. A key that is not listed is a known zero. */
    public static final class Vector {
        public static final Vector EMPTY = new Vector(new TreeMap<>());
        private final TreeMap<Key, Long> amounts;
        private Vector(TreeMap<Key, Long> amounts) { this.amounts = amounts; }
        /** Sums the units of one endpoint per key, so the same key in both banks is one amount. */
        public static Result<Vector> aggregate(List<Unit> units) {
            var amounts = new TreeMap<Key, Long>();
            for (Unit unit : units) {
                if (unit.amount() < 0) { return Result.refuse(Status.NEGATIVE_AMOUNT); }
                if (unit.key() == null) {
                    if (unit.amount() != 0) { return Result.refuse(Status.INVALID_KEY); }
                    continue;
                }
                if (unit.key().status() != Status.OK) { return Result.refuse(unit.key().status()); }
                if (unit.amount() == 0) { continue; }
                long sum;
                try { sum = Math.addExact(amounts.getOrDefault(unit.key(), 0L), unit.amount()); }
                catch (ArithmeticException overflow) { return Result.refuse(Status.OVERFLOW); }
                amounts.put(unit.key(), sum);
            }
            return Result.ok(new Vector(amounts));
        }
        public static Result<Vector> of(Key key, long amount) { return aggregate(List.of(new Unit(key, amount))); }
        public long amount(Key key) { return amounts.getOrDefault(key, 0L); }
        public Set<Key> keys() { return Collections.unmodifiableSet(amounts.keySet()); }
        /** Adds a signed delta. A result below zero is a broken chain, never a clamped zero. */
        public Result<Vector> plus(Key key, long delta) {
            long next;
            try { next = Math.addExact(amount(key), delta); }
            catch (ArithmeticException overflow) { return Result.refuse(Status.OVERFLOW); }
            if (next < 0) { return Result.refuse(Status.BROKEN_CHAIN); }
            var copy = new TreeMap<>(amounts);
            if (next == 0) { copy.remove(key); } else { copy.put(key, next); }
            return Result.ok(new Vector(copy));
        }
        @Override public boolean equals(Object other) { return other instanceof Vector vector && amounts.equals(vector.amounts); }
        @Override public int hashCode() { return amounts.hashCode(); }
        @Override public String toString() { return amounts.toString(); }
    }

    /** A signed planned change of one key at one endpoint: positive is a gain there. */
    public record Delta(Key key, long amount) { }

    /** A baseline entry: the two captured amounts and the two planned signed deltas of one key (four longs). */
    public record Entry(Key key, long source, long destination, long sourceDelta, long destinationDelta) {
        public Entry { Objects.requireNonNull(key); }
    }

    /** The typed baseline committed at L1. It is never rewritten by an application, a mismatch or a settlement. */
    public record Baseline(Scope scope, List<Entry> entries) {
        public Baseline { Objects.requireNonNull(scope); entries = List.copyOf(entries); }

        /**
         * Captures the baseline of a transfer of {@code quantity} mB of {@code moved} from the projected source vector to
         * the projected destination vector.
         */
        public static Result<Baseline> capture(Scope scope, Vector source, Vector destination, Key moved, long quantity) {
            if (moved == null || moved.status() != Status.OK) { return Result.refuse(Status.INVALID_KEY); }
            if (quantity < 1 || quantity > MAX_TRANSFER || source.amount(moved) < quantity) { return Result.refuse(Status.INVALID_PLAN); }
            var keys = new TreeSet<Key>(source.keys()); keys.addAll(destination.keys()); keys.add(moved);
            var entries = new ArrayList<Entry>();
            for (Key key : keys) {
                boolean transfer = key.equals(moved);
                entries.add(new Entry(key, source.amount(key), destination.amount(key), transfer ? -quantity : 0, transfer ? quantity : 0));
            }
            var baseline = new Baseline(scope, entries);
            return baseline.status() == Status.OK ? Result.ok(baseline) : Result.refuse(baseline.status());
        }

        public Status status() {
            if (entries.isEmpty() || entries.size() > scope.baselineKeys()) { return Status.CARDINALITY; }
            Status order = ordered(entries.stream().map(Entry::key).toList());
            if (order != Status.OK) { return order; }
            int moved = 0;
            for (Entry entry : entries) {
                if (entry.source() < 0 || entry.destination() < 0) { return Status.NEGATIVE_AMOUNT; }
                long sum;
                try { sum = Math.addExact(entry.sourceDelta(), entry.destinationDelta()); }
                catch (ArithmeticException overflow) { return Status.OVERFLOW; }
                // The planned identity: what leaves the source arrives at the destination, for every key.
                if (sum != 0) { return Status.INCORRECT_BALANCE; }
                if (entry.sourceDelta() == 0) {
                    // A collateral key is listed only because one of its amounts is not zero.
                    if (entry.source() == 0 && entry.destination() == 0) { return Status.INVALID_PLAN; }
                    continue;
                }
                long quantity = entry.destinationDelta();
                if (quantity < 1 || quantity > MAX_TRANSFER || entry.source() < quantity) { return Status.INVALID_PLAN; }
                if (entry.destination() > Long.MAX_VALUE - quantity) { return Status.OVERFLOW; }
                moved++;
            }
            return moved == 1 ? Status.OK : Status.INVALID_PLAN;
        }

        private Entry movedEntry() { return entries.stream().filter(entry -> entry.sourceDelta() != 0).findFirst().orElseThrow(); }
        public Key moved() { return movedEntry().key(); }
        public long quantity() { return movedEntry().destinationDelta(); }
        public Delta sourceDelta() { return new Delta(moved(), -quantity()); }
        public Delta destinationDelta() { return new Delta(moved(), quantity()); }
        public Set<Key> keys() { var keys = new TreeSet<Key>(); entries.forEach(entry -> keys.add(entry.key())); return keys; }
        public long source(Key key) { return entries.stream().filter(e -> e.key().equals(key)).mapToLong(Entry::source).findFirst().orElse(0); }
        public long destination(Key key) { return entries.stream().filter(e -> e.key().equals(key)).mapToLong(Entry::destination).findFirst().orElse(0); }
        public Vector sourceBefore() { return side(true, false); }
        public Vector destinationBefore() { return side(false, false); }
        public Vector sourceAfter() { return side(true, true); }
        public Vector destinationAfter() { return side(false, true); }
        private Vector side(boolean source, boolean after) {
            var units = new ArrayList<Unit>();
            for (Entry entry : entries) {
                long amount = (source ? entry.source() : entry.destination()) + (after ? source ? entry.sourceDelta() : entry.destinationDelta() : 0);
                units.add(new Unit(entry.key(), amount));
            }
            return Vector.aggregate(units).value();
        }
        /**
         * Preparation check of section 3: the native AFTER captures equal the plan for every key, and every collateral
         * unit is unchanged.
         */
        public Status verifyPlan(Vector plannedSourceAfter, Vector plannedDestinationAfter) {
            return status() != Status.OK ? status() : sourceAfter().equals(plannedSourceAfter)
                    && destinationAfter().equals(plannedDestinationAfter) ? Status.OK : Status.INVALID_PLAN;
        }
    }

    /** The exact prefix combinations of one member: {@code aS} and {@code aD} of section 2.2. */
    public enum Application {
        NONE(0, 0), SOURCE_ONLY(1, 0), DESTINATION_ONLY(0, 1), BOTH(1, 1);
        private final int source, destination;
        Application(int source, int destination) { this.source = source; this.destination = destination; }
        public boolean halfApplied() { return source != destination; }
    }

    /** {@code observed - baseline}; positive is a gain at that endpoint. */
    public static Result<Long> delta(long observed, long baseline) {
        if (observed < 0 || baseline < 0) { return Result.refuse(Status.NEGATIVE_AMOUNT); }
        return Result.ok(observed - baseline); // Two non-negative longs cannot overflow a difference.
    }

    /** {@code r[f] = dS[f] + dD[f]} against this member's own baseline. */
    public static Result<Long> residual(Baseline baseline, Key key, Vector source, Vector destination) {
        try {
            return Result.ok(Math.addExact(source.amount(key) - baseline.source(key), destination.amount(key) - baseline.destination(key)));
        } catch (ArithmeticException overflow) { return Result.refuse(Status.OVERFLOW); }
    }

    /**
     * Which exact partial application the two observed vectors show, for every key in scope. Empty when they match none
     * of the four combinations, which is a numeric mismatch and not a new baseline.
     */
    public static Optional<Application> classify(Baseline baseline, Vector source, Vector destination) {
        var keys = new TreeSet<>(baseline.keys()); keys.addAll(source.keys()); keys.addAll(destination.keys());
        for (Application application : Application.values()) {
            boolean matches = true;
            for (Key key : keys) {
                long quantity = key.equals(baseline.moved()) ? baseline.quantity() : 0;
                matches &= source.amount(key) - baseline.source(key) == -quantity * application.source
                        && destination.amount(key) - baseline.destination(key) == quantity * application.destination;
            }
            if (matches) { return Optional.of(application); }
        }
        return Optional.empty();
    }

    /**
     * {@code outstanding = q * (aS - aD)}: a signed transfer obligation, not stored fluid and not proof of conservation.
     * Source-only gives {@code +q}; destination-only gives {@code -q}.
     */
    public static long outstanding(Baseline baseline, Application application) {
        return baseline.quantity() * (application.source - application.destination);
    }

    /**
     * Admission (section 2.3): the projected baseline of a new member is the observed frontier plus the planned deltas
     * of the unresolved earlier members of the same endpoint that are not applied yet.
     */
    public static Result<Vector> project(Vector frontier, List<Delta> unappliedEarlier) { return shift(frontier, unappliedEarlier, 1); }

    /**
     * C6 (section 2.3): subtract the recorded deltas of the proved later prefix members from the observed frontier
     * before comparing it with this member's expected AFTER, so a later fill is not charged to an earlier member.
     */
    public static Result<Vector> withoutLater(Vector frontier, List<Delta> appliedLater) { return shift(frontier, appliedLater, -1); }

    private static Result<Vector> shift(Vector vector, List<Delta> deltas, int sign) {
        var current = Result.ok(vector);
        for (Delta delta : deltas) {
            if (delta.amount() == Long.MIN_VALUE) { return Result.refuse(Status.OVERFLOW); }
            current = current.value().plus(delta.key(), sign * delta.amount());
            if (!current.ok()) { return current; }
        }
        return current;
    }

    /** A frozen entry: up to two observed amounts, an optional known adjustment, and the member that owns it. */
    public record FrozenEntry(Key key, OptionalLong source, OptionalLong destination, OptionalLong adjustment, UUID owner) {
        public FrozenEntry {
            Objects.requireNonNull(key); Objects.requireNonNull(source); Objects.requireNonNull(destination);
            Objects.requireNonNull(adjustment); Objects.requireNonNull(owner);
        }
        /** A shared adjustment is stored once, in its owner's row. It is a reference here, never an addend. */
        public boolean shared(UUID row) { return !owner.equals(row); }
    }

    /**
     * The frozen settlement part. {@code members} is not empty only in the row of {@code groupOwner}, the
     * lowest-sequence member that has a row.
     */
    public record Frozen(Knowledge knowledge, UUID groupOwner, List<UUID> members, List<FrozenEntry> entries) {
        public Frozen {
            Objects.requireNonNull(knowledge); Objects.requireNonNull(groupOwner);
            members = List.copyOf(members); entries = List.copyOf(entries);
        }
    }

    /** One account row, keyed by its operation. */
    public record Row(UUID operation, Baseline baseline, Frozen frozen) {
        public Row { Objects.requireNonNull(operation); Objects.requireNonNull(baseline); }
        public Knowledge knowledge() { return frozen == null ? Knowledge.PLANNED : frozen.knowledge(); }

        /** Everything one row can prove alone. A group of rows is checked by {@link #verifyGroup}. */
        public Status status() {
            Status base = baseline.status();
            if (base != Status.OK || frozen == null) { return base; }
            Knowledge knowledge = frozen.knowledge();
            if (knowledge != Knowledge.KNOWN && knowledge != Knowledge.UNKNOWN_BASELINE && knowledge != Knowledge.UNKNOWN_ENDPOINT) {
                return Status.INVALID_KNOWLEDGE;
            }
            boolean owner = frozen.groupOwner().equals(operation);
            if (owner) {
                if (frozen.members().isEmpty() || frozen.members().size() > MAX_MEMBERS || !frozen.members().get(0).equals(operation)
                        || new HashSet<>(frozen.members()).size() != frozen.members().size()) { return Status.INVALID_MEMBERS; }
            } else if (!frozen.members().isEmpty()) { return Status.INVALID_MEMBERS; }
            var entries = frozen.entries();
            if (entries.isEmpty() || entries.size() > baseline.scope().unionKeys()) { return Status.CARDINALITY; }
            Status order = ordered(entries.stream().map(FrozenEntry::key).toList());
            if (order != Status.OK) { return order; }
            var keys = new TreeSet<Key>(); entries.forEach(entry -> keys.add(entry.key()));
            if (!keys.containsAll(baseline.keys())) { return Status.CARDINALITY; }
            boolean source = entries.get(0).source().isPresent(), destination = entries.get(0).destination().isPresent();
            boolean single = owner && frozen.members().size() == 1;
            if (knowledge == Knowledge.KNOWN && !(source && destination)) { return Status.INVALID_KNOWLEDGE; }
            for (FrozenEntry entry : entries) {
                // One endpoint is observed for every key or for none.
                if (entry.source().isPresent() != source || entry.destination().isPresent() != destination) { return Status.INVALID_MASK; }
                if (source && entry.source().getAsLong() < 0 || destination && entry.destination().getAsLong() < 0) { return Status.NEGATIVE_AMOUNT; }
                boolean listedForNothing = !baseline.keys().contains(entry.key())
                        && (!source || entry.source().getAsLong() == 0) && (!destination || entry.destination().getAsLong() == 0);
                if (listedForNothing) { return Status.CARDINALITY; }
                if (owner && !frozen.members().contains(entry.owner())) { return Status.INVALID_OWNER; }
                if (single && !entry.owner().equals(operation)) { return Status.INVALID_OWNER; }
                boolean own = entry.owner().equals(operation);
                // An adjustment exists exactly where it is known, and only in its owner's row.
                if (entry.adjustment().isPresent() != (knowledge == Knowledge.KNOWN && own)) {
                    return knowledge == Knowledge.KNOWN ? Status.INVALID_OWNER : Status.INVALID_KNOWLEDGE;
                }
            }
            if (knowledge == Knowledge.UNKNOWN_ENDPOINT && source && destination && single) { return Status.INVALID_KNOWLEDGE; }
            if (knowledge == Knowledge.KNOWN && single) {
                // A component of one member: dS + dD + loss = 0 for every key, against this row's own baseline.
                for (FrozenEntry entry : entries) {
                    try {
                        long residual = Math.addExact(entry.source().getAsLong() - baseline.source(entry.key()),
                                entry.destination().getAsLong() - baseline.destination(entry.key()));
                        if (Math.addExact(residual, entry.adjustment().getAsLong()) != 0) { return Status.INCORRECT_BALANCE; }
                    } catch (ArithmeticException overflow) { return Status.OVERFLOW; }
                }
            }
            return Status.OK;
        }
    }

    /** One physical resource unit: the full typed endpoint key and its resource scope, never a UUID alone. */
    public record UnitRef(EscrowEndpoint.Key endpoint, String scope) {
        public UnitRef { Objects.requireNonNull(endpoint); Objects.requireNonNull(scope); }
    }

    /**
     * One member of a connected component. {@code baseline} is null for a member without a row: an item member, or a
     * legacy fluid member when {@code legacyFluid} is true.
     */
    public record Member(UUID operation, long sequence, UnitRef source, UnitRef destination, Baseline baseline, boolean legacyFluid) {
        public Member { Objects.requireNonNull(operation); Objects.requireNonNull(source); Objects.requireNonNull(destination); }
        public static Member item(UUID operation, long sequence, UnitRef source, UnitRef destination) {
            return new Member(operation, sequence, source, destination, null, false);
        }
        public static Member legacy(UUID operation, long sequence, UnitRef source, UnitRef destination) {
            return new Member(operation, sequence, source, destination, null, true);
        }
    }

    /**
     * The frozen account of a whole component. {@code discrepancy} is the signed loss per key (positive is a loss,
     * negative is a gain) and is empty unless the knowledge is KNOWN. {@code rows} holds one row per member that has one.
     */
    public record Settlement(Knowledge knowledge, UUID groupOwner, List<UUID> members, SortedMap<Key, Long> discrepancy,
                             Map<UUID, Row> rows) {
        /** Automatic completion needs KNOWN and a zero residual for every key. */
        public boolean balanced() {
            return knowledge == Knowledge.KNOWN && discrepancy.values().stream().allMatch(value -> value == 0);
        }
    }

    /**
     * Freezes a connected component (section 2.3). Each physical unit is counted once, from its earliest baseline to its
     * final observation; each key's discrepancy is stored once, in the row of the lowest-sequence member whose own keys
     * include it. {@code finals} maps every unit of a row-bearing member to its final vector, or to empty when that
     * endpoint is missing or not proved.
     */
    public static Result<Settlement> settle(List<Member> component, Map<UnitRef, Optional<Vector>> finals) {
        if (component.isEmpty() || component.size() > MAX_MEMBERS) { return Result.refuse(Status.COMPONENT_LIMIT); }
        var members = component.stream().sorted(Comparator.comparingLong(Member::sequence)).toList();
        var ids = new HashSet<UUID>(); var sequences = new HashSet<Long>();
        boolean legacy = false;
        for (Member member : members) {
            if (!ids.add(member.operation()) || !sequences.add(member.sequence())
                    || member.legacyFluid() && member.baseline() != null) { return Result.refuse(Status.INVALID_MEMBERS); }
            legacy |= member.legacyFluid();
            if (member.baseline() != null && member.baseline().status() != Status.OK) { return Result.refuse(member.baseline().status()); }
        }
        var owners = members.stream().filter(member -> member.baseline() != null).toList();
        if (owners.isEmpty()) {
            // No fluid account at all: an item-only component is not applicable; a legacy one has no baseline.
            return Result.ok(new Settlement(legacy ? Knowledge.UNKNOWN_BASELINE : Knowledge.NOT_APPLICABLE, null, List.of(),
                    Collections.emptySortedMap(), Map.of()));
        }
        // The chain of every unit, in sequence order, with each link's own baseline vector and planned delta.
        var chains = new LinkedHashMap<UnitRef, List<Link>>();
        for (Member member : owners) {
            chains.computeIfAbsent(member.source(), unit -> new ArrayList<>())
                    .add(new Link(member.baseline().sourceBefore(), member.baseline().sourceDelta()));
            chains.computeIfAbsent(member.destination(), unit -> new ArrayList<>())
                    .add(new Link(member.baseline().destinationBefore(), member.baseline().destinationDelta()));
        }
        if (chains.size() > MAX_UNITS) { return Result.refuse(Status.COMPONENT_LIMIT); }
        boolean missing = false;
        for (UnitRef unit : chains.keySet()) {
            var observed = finals.get(unit);
            if (observed == null) { return Result.refuse(Status.MISSING_OBSERVATION); }
            missing |= observed.isEmpty();
        }
        Knowledge knowledge = legacy ? Knowledge.UNKNOWN_BASELINE : missing ? Knowledge.UNKNOWN_ENDPOINT : Knowledge.KNOWN;
        if (!legacy) {
            // Numeric continuity: each later member's projected baseline is its predecessor's baseline plus its delta.
            for (var chain : chains.values()) {
                for (int index = 1; index < chain.size(); index++) {
                    Link before = chain.get(index - 1);
                    var expected = before.baseline().plus(before.delta().key(), before.delta().amount());
                    if (!expected.ok()) { return Result.refuse(expected.status()); }
                    if (!expected.value().equals(chain.get(index).baseline())) { return Result.refuse(Status.BROKEN_CHAIN); }
                }
            }
        }
        // Each member's own keys: its baseline keys and the keys observed on its two units.
        var unions = new LinkedHashMap<UUID, TreeSet<Key>>(); int memberKeys = 0;
        for (Member member : owners) {
            var keys = new TreeSet<>(member.baseline().keys());
            for (UnitRef unit : List.of(member.source(), member.destination())) { finals.get(unit).ifPresent(vector -> keys.addAll(vector.keys())); }
            if (keys.size() > member.baseline().scope().unionKeys()) { return Result.refuse(Status.CARDINALITY); }
            unions.put(member.operation(), keys); memberKeys += keys.size();
        }
        if (memberKeys > MAX_MEMBER_KEYS) { return Result.refuse(Status.COMPONENT_LIMIT); }
        var discrepancy = new TreeMap<Key, Long>();
        if (knowledge == Knowledge.KNOWN) {
            try {
                for (var chain : chains.entrySet()) {
                    Vector earliest = chain.getValue().get(0).baseline(), last = finals.get(chain.getKey()).orElseThrow();
                    var keys = new TreeSet<>(earliest.keys()); keys.addAll(last.keys());
                    for (Key key : keys) {
                        // loss = -(sum of deltas): positive when the component holds less of this key than before.
                        discrepancy.merge(key, Math.subtractExact(earliest.amount(key), last.amount(key)), Math::addExact);
                    }
                }
            } catch (ArithmeticException overflow) { return Result.refuse(Status.OVERFLOW); }
            unions.values().forEach(keys -> keys.forEach(key -> discrepancy.putIfAbsent(key, 0L)));
        }
        var keyOwner = new TreeMap<Key, UUID>();
        for (Member member : owners) { unions.get(member.operation()).forEach(key -> keyOwner.putIfAbsent(key, member.operation())); }
        UUID groupOwner = owners.get(0).operation();
        var memberIds = owners.stream().map(Member::operation).toList();
        var rows = new LinkedHashMap<UUID, Row>();
        for (Member member : owners) {
            var source = finals.get(member.source()); var destination = finals.get(member.destination());
            var entries = new ArrayList<FrozenEntry>();
            for (Key key : unions.get(member.operation())) {
                UUID owner = keyOwner.get(key);
                entries.add(new FrozenEntry(key,
                        source.map(vector -> OptionalLong.of(vector.amount(key))).orElse(OptionalLong.empty()),
                        destination.map(vector -> OptionalLong.of(vector.amount(key))).orElse(OptionalLong.empty()),
                        knowledge == Knowledge.KNOWN && owner.equals(member.operation())
                                ? OptionalLong.of(discrepancy.get(key)) : OptionalLong.empty(), owner));
            }
            var row = new Row(member.operation(), member.baseline(), new Frozen(knowledge, groupOwner,
                    member.operation().equals(groupOwner) ? memberIds : List.of(), entries));
            if (row.status() != Status.OK) { return Result.refuse(row.status()); }
            rows.put(member.operation(), row);
        }
        return Result.ok(new Settlement(knowledge, groupOwner, memberIds, Collections.unmodifiableSortedMap(discrepancy),
                Collections.unmodifiableMap(rows)));
    }
    private record Link(Vector baseline, Delta delta) { }

    /**
     * What a set of frozen rows proves without the operations: one group owner, one member set, the same knowledge, and
     * for every key one owner whose row is present and, when KNOWN, stores the one adjustment. A group with a missing
     * key-adjustment owner or a different member set cannot release.
     */
    public static Status verifyGroup(List<Row> rows) {
        if (rows.isEmpty() || rows.size() > MAX_MEMBERS) { return Status.COMPONENT_LIMIT; }
        var byId = new LinkedHashMap<UUID, Row>();
        for (Row row : rows) {
            if (row.status() != Status.OK) { return row.status(); }
            if (row.frozen() == null) { return Status.INVALID_KNOWLEDGE; }
            if (byId.put(row.operation(), row) != null) { return Status.DUPLICATE_ROW; }
        }
        UUID groupOwner = rows.get(0).frozen().groupOwner(); Knowledge knowledge = rows.get(0).frozen().knowledge();
        Row ownerRow = byId.get(groupOwner);
        if (ownerRow == null || !new HashSet<>(ownerRow.frozen().members()).equals(byId.keySet())) { return Status.INVALID_MEMBERS; }
        int memberKeys = 0;
        for (Row row : rows) {
            if (!row.frozen().groupOwner().equals(groupOwner)) { return Status.INVALID_MEMBERS; }
            if (row.frozen().knowledge() != knowledge) { return Status.INVALID_KNOWLEDGE; }
            memberKeys += row.frozen().entries().size();
            for (FrozenEntry entry : row.frozen().entries()) {
                Row keyOwner = byId.get(entry.owner());
                if (keyOwner == null) { return Status.INVALID_OWNER; }
                var owned = keyOwner.frozen().entries().stream().filter(other -> other.key().equals(entry.key())).findFirst();
                // The owner's own entry names itself, so exactly one row can hold this key's adjustment.
                if (owned.isEmpty() || !owned.get().owner().equals(entry.owner())) { return Status.INVALID_OWNER; }
            }
        }
        return memberKeys > MAX_MEMBER_KEYS ? Status.COMPONENT_LIMIT : Status.OK;
    }

    private static Status ordered(List<Key> keys) {
        for (int index = 0; index < keys.size(); index++) {
            if (keys.get(index).status() != Status.OK) { return keys.get(index).status(); }
            if (index > 0) {
                int order = keys.get(index - 1).compareTo(keys.get(index));
                if (order == 0) { return Status.DUPLICATE_KEY; }
                if (order > 0) { return Status.UNSORTED_KEYS; }
            }
        }
        return Status.OK;
    }
}
