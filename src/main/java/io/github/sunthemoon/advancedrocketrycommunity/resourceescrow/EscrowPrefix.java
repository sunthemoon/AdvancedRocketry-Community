package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Prefixes are per endpoint, not global sequence adjacency or an obsolete individual after-hash. */
public final class EscrowPrefix {
    public record Marker(UUID operation, long sequence, String role, long epochAfter) {
        public Marker {
            java.util.Objects.requireNonNull(operation);
            EscrowTags.require(sequence > 0 && epochAfter > 0 && (role.equals("SOURCE") || role.equals("DESTINATION")), "Invalid marker");
        }
    }
    public record State(UUID ledger, long epoch, String signature, List<Marker> markers) {
        public State {
            java.util.Objects.requireNonNull(ledger); EscrowTags.require(epoch >= 0, "Invalid resource epoch");
            EscrowTags.digest(signature); markers = List.copyOf(markers);
            EscrowTags.require(markers.size() <= EscrowLimits.PER_ROCKET, "Marker count exceeded");
        }
    }
    public enum Classification { BEFORE, PREFIX, AFTER, OTHER }
    private EscrowPrefix() { }

    public static Map<EscrowEndpoint.Key, List<Link>> chains(List<EscrowOperation> records) {
        var chains = new HashMap<EscrowEndpoint.Key, List<Link>>();
        records.stream().sorted(Comparator.comparingLong(EscrowOperation::sequence)).forEach(op -> {
            if (op.kind() == EscrowOperation.Kind.TRANSFER_FLOOR_RETIRE) { return; }
            chains.computeIfAbsent(op.source().key(), key -> new ArrayList<>()).add(new Link(op, op.source(), "SOURCE"));
            chains.computeIfAbsent(op.destination().key(), key -> new ArrayList<>()).add(new Link(op, op.destination(), "DESTINATION"));
        });
        return chains;
    }
    public record Link(EscrowOperation operation, EscrowEndpoint endpoint, String role) {
        public Marker marker() { return new Marker(operation.id(), operation.sequence(), role, endpoint.afterEpoch()); }
    }
    static void validateChains(List<EscrowOperation> records) {
        for (List<Link> chain : chains(records).values()) {
            for (int i = 1; i < chain.size(); i++) {
                EscrowEndpoint before = chain.get(i - 1).endpoint(), after = chain.get(i).endpoint();
                EscrowTags.require(before.afterEpoch() == after.beforeEpoch()
                        && before.afterSignature().equals(after.beforeSignature()), "Noncontiguous endpoint chain");
            }
        }
    }
    public static Classification classify(UUID ledger, List<Link> chain, State state) {
        if (chain.isEmpty() || !ledger.equals(state.ledger())) { return Classification.OTHER; }
        EscrowEndpoint first = chain.get(0).endpoint();
        if (state.markers().isEmpty() && state.epoch() == first.beforeEpoch()
                && state.signature().equals(first.beforeSignature())) { return Classification.BEFORE; }
        int applied = state.markers().size();
        if (applied == 0 || applied > chain.size()) { return Classification.OTHER; }
        for (int i = 0; i < applied; i++) {
            if (!chain.get(i).marker().equals(state.markers().get(i))) { return Classification.OTHER; }
        }
        EscrowEndpoint frontier = chain.get(applied - 1).endpoint();
        if (state.epoch() != frontier.afterEpoch() || !state.signature().equals(frontier.afterSignature())) {
            return Classification.OTHER;
        }
        return applied == chain.size() ? Classification.AFTER : Classification.PREFIX;
    }
    public static void requireRetirementPrefix(List<EscrowOperation> records, Set<UUID> retired) {
        EscrowTags.require(records.stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet())
                .containsAll(retired), "Unknown retirement operation");
        for (List<Link> chain : chains(records).values()) {
            boolean retained = false;
            for (Link link : chain) {
                if (!retired.contains(link.operation().id())) { retained = true; }
                else { EscrowTags.require(!retained, "Retirement would leave a hole in an endpoint prefix"); }
            }
        }
    }
}
